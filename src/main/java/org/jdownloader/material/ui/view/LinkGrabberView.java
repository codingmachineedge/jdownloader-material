package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.LinkAvailability;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.util.Formats;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The LinkGrabber staging area: crawled packages awaiting confirmation into Downloads. */
public final class LinkGrabberView extends BorderPane {

    private final DownloadEngine engine;
    private final NotificationCenter notifier;
    private final Runnable openAddLinks;
    private final TreeTableView<Object> tree = new TreeTableView<>();
    private final TreeItem<Object> root = new TreeItem<>(null);
    private final javafx.beans.InvalidationListener refresh = o -> requestRebuild();
    private final PauseTransition rebuildDelay = new PauseTransition(Duration.millis(80));
    private final Map<CrawledPackage, ListChangeListener<CrawledLink>> packageLinkListeners = new HashMap<>();
    private boolean rebuildScheduled;
    private String filter = "";
    private AvailabilityFilter availabilityFilter = AvailabilityFilter.ALL;

    public LinkGrabberView(DownloadEngine engine, NotificationCenter notifier, Runnable openAddLinks) {
        this.engine = engine;
        this.notifier = notifier;
        this.openAddLinks = openAddLinks;
        getStyleClass().add("content-area");
        setTop(buildHeaderAndToolbar());
        setCenter(buildTree());
        rebuildDelay.setOnFinished(event -> {
            rebuildScheduled = false;
            rebuild();
        });
        wireModel();
        rebuild();
    }

    private VBox buildHeaderAndToolbar() {
        var title = Mat.label("LinkGrabber", "headline");
        var search = new MFXTextField();
        search.setPromptText("Search links");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(240);
        search.textProperty().addListener((o, a, b) -> { filter = b == null ? "" : b.toLowerCase(); rebuild(); });
        var availability = Mat.outlined(availabilityFilter.label, null);
        availability.setOnAction(e -> {
            availabilityFilter = availabilityFilter.next();
            availability.setText(availabilityFilter.label);
            requestRebuild();
        });
        HBox header = new HBox(12, title, Mat.hSpacer(), availability,
                org.jdownloader.material.ui.Icons.of("search", 20), search);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var addLinks = Mat.filled("Add Links", "add");
        addLinks.setOnAction(e -> openAddLinks.run());
        var paste = Mat.tonal("Paste", "paste");
        paste.setOnAction(e -> pasteFromClipboard());

        var confirm = Mat.filled("Add to Downloads", "check");
        confirm.setOnAction(e -> confirmSelected());
        confirm.disableProperty().bind(Bindings.isEmpty(tree.getSelectionModel().getSelectedItems()));
        var addAll = Mat.outlined("Add all to Downloads", null);
        addAll.setOnAction(e -> {
            engine.confirmAll(engine.settings().autoStartProperty().get());
        });

        var remove = Mat.text("Remove", "delete");
        Mat.tip(remove, "Remove selected");
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> removeSelected());

        HBox bar = new HBox(6, addLinks, paste, Mat.vSep(), confirm, addAll, Mat.hSpacer(), remove);
        bar.getStyleClass().add("action-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return new VBox(header, bar);
    }

    private TreeTableView<Object> buildTree() {
        tree.getStyleClass().add("data-table");
        tree.setShowRoot(false);
        tree.setRoot(root);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setPlaceholder(Mat.label("No links staged — add links to check and organize them here.", "empty-table-hint"));

        TreeTableColumn<Object, String> name = new TreeTableColumn<>("Name");
        name.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledPackage cp) return cp.nameProperty();
            if (o instanceof CrawledLink cl) return cl.nameProperty();
            return new ReadOnlyStringWrapper("");
        });
        name.setPrefWidth(320);
        name.setMinWidth(180);

        TreeTableColumn<Object, LinkAvailability> avail = new TreeTableColumn<>("Availability");
        avail.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.availabilityProperty();
            return new ReadOnlyObjectWrapper<>(null);
        });
        avail.setCellFactory(availabilityCell());
        avail.setPrefWidth(140);

        TreeTableColumn<Object, String> host = new TreeTableColumn<>("Host");
        host.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.hostProperty();
            return new ReadOnlyStringWrapper("");
        });
        host.setPrefWidth(150);

        TreeTableColumn<Object, Number> size = new TreeTableColumn<>("Size");
        size.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.sizeProperty();
            if (o instanceof CrawledPackage cp) return new SimpleLongProperty(cp.totalSize());
            return new SimpleLongProperty(0);
        });
        size.setCellFactory(col -> new TreeTableCell<>() {
            { setAlignment(Pos.CENTER_RIGHT); }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null || v.longValue() <= 0 ? "" : Formats.bytes(v.longValue()));
            }
        });
        size.setPrefWidth(96);

        TreeTableColumn<Object, String> url = new TreeTableColumn<>("URL");
        url.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.urlProperty();
            return new ReadOnlyStringWrapper("");
        });
        url.setPrefWidth(260);

        tree.getColumns().setAll(List.of(name, avail, host, size, url));
        return tree;
    }

    private javafx.util.Callback<TreeTableColumn<Object, LinkAvailability>, TreeTableCell<Object, LinkAvailability>> availabilityCell() {
        return col -> new TreeTableCell<>() {
            private final Circle dot = new Circle(5);
            private final HBox box = new HBox(8, dot);
            @Override protected void updateItem(LinkAvailability a, boolean empty) {
                super.updateItem(a, empty);
                Object row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) { setGraphic(null); setText(null); return; }
                if (row instanceof CrawledPackage cp) {
                    setGraphic(null);
                    setText(cp.onlineCount() + " / " + cp.links().size() + " online");
                    return;
                }
                LinkAvailability av = a == null ? LinkAvailability.UNKNOWN : a;
                dot.setFill(switch (av) {
                    case ONLINE -> Color.web("#2e9b4f");
                    case OFFLINE -> Color.web("#c5352c");
                    case UNKNOWN -> Color.web("#8b8792");
                });
                var label = new javafx.scene.control.Label(av.label());
                label.getStyleClass().add("table-content-label");
                box.getChildren().setAll(dot, label);
                setGraphic(box);
                setText(null);
            }
        };
    }

    private void wireModel() {
        engine.crawledPackages().addListener((ListChangeListener<CrawledPackage>) c -> {
            while (c.next()) {
                for (CrawledPackage p : c.getAddedSubList()) attach(p);
                for (CrawledPackage p : c.getRemoved()) detach(p);
            }
            requestRebuild();
        });
        for (CrawledPackage p : engine.crawledPackages()) attach(p);
    }

    private void attach(CrawledPackage p) {
        if (packageLinkListeners.containsKey(p)) return;
        ListChangeListener<CrawledLink> listener = change -> {
            while (change.next()) {
                for (CrawledLink link : change.getAddedSubList()) attachLink(link);
                for (CrawledLink link : change.getRemoved()) detachLink(link);
            }
            requestRebuild();
        };
        packageLinkListeners.put(p, listener);
        p.links().addListener(listener);
        for (CrawledLink link : p.links()) attachLink(link);
    }

    private void detach(CrawledPackage p) {
        ListChangeListener<CrawledLink> listener = packageLinkListeners.remove(p);
        if (listener != null) p.links().removeListener(listener);
        for (CrawledLink link : p.links()) detachLink(link);
    }

    private void attachLink(CrawledLink link) {
        link.availabilityProperty().addListener(refresh);
        link.sizeProperty().addListener(refresh);
    }

    private void detachLink(CrawledLink link) {
        link.availabilityProperty().removeListener(refresh);
        link.sizeProperty().removeListener(refresh);
    }

    /**
     * Probes can complete in dense bursts. Rebuild at most once per short
     * interval so a large paste does not repeatedly clear and restore the
     * entire tree on the JavaFX Application Thread.
     */
    private void requestRebuild() {
        if (rebuildScheduled) return;
        rebuildScheduled = true;
        rebuildDelay.playFromStart();
    }

    private void rebuild() {
        Set<Object> selectedBefore = new LinkedHashSet<>();
        for (TreeItem<Object> item : tree.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null) selectedBefore.add(item.getValue());
        }
        tree.getSelectionModel().clearSelection();
        root.getChildren().clear();
        for (CrawledPackage pkg : engine.crawledPackages()) {
            boolean pkgMatch = filter.isEmpty() || pkg.name().toLowerCase().contains(filter);
            TreeItem<Object> pi = new TreeItem<>(pkg);
            pi.setExpanded(pkg.expandedProperty().get());
            pi.expandedProperty().addListener((o, wasExpanded, isExpanded) -> pkg.expandedProperty().set(isExpanded));
            for (CrawledLink l : pkg.links()) {
                boolean textMatch = filter.isEmpty() || pkgMatch
                        || l.name().toLowerCase().contains(filter)
                        || l.host().toLowerCase().contains(filter);
                if (textMatch && availabilityFilter.matches(l.availability())) {
                    pi.getChildren().add(new TreeItem<>(l));
                }
            }
            if (!pi.getChildren().isEmpty() || (availabilityFilter == AvailabilityFilter.ALL && pkgMatch)) {
                root.getChildren().add(pi);
            }
        }
        if (!selectedBefore.isEmpty()) restoreSelection(root, selectedBefore);
        // Package rows derive text (online counts, sizes) at render time from
        // non-observable values; force visible cells to re-render.
        tree.refresh();
    }

    private void restoreSelection(TreeItem<Object> parent, Set<Object> selected) {
        for (TreeItem<Object> child : parent.getChildren()) {
            if (selected.contains(child.getValue())) tree.getSelectionModel().select(child);
            restoreSelection(child, selected);
        }
    }

    private Set<CrawledPackage> selectedPackageRows() {
        Set<CrawledPackage> out = new LinkedHashSet<>();
        for (TreeItem<Object> item : tree.getSelectionModel().getSelectedItems()) {
            if (item == null || item.getValue() == null) continue;
            Object o = item.getValue();
            if (o instanceof CrawledPackage cp) out.add(cp);
        }
        return out;
    }

    private Set<CrawledLink> selectedLinkRows(Set<CrawledPackage> selectedPackages) {
        Set<CrawledLink> out = new LinkedHashSet<>();
        for (TreeItem<Object> item : tree.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() instanceof CrawledLink link
                    && selectedPackages.stream().noneMatch(pkg -> pkg.links().contains(link))) {
                out.add(link);
            }
        }
        return out;
    }

    /** Immediate remove with additive Undo — no confirm dialog. */
    private void removeSelected() {
        Set<CrawledPackage> selectedPackages = selectedPackageRows();
        Set<CrawledLink> selectedLinks = selectedLinkRows(selectedPackages);
        if (selectedPackages.isEmpty() && selectedLinks.isEmpty()) return;
        var packages = engine.crawledPackages();
        Set<CrawledPackage> affectedPackages = new LinkedHashSet<>(selectedPackages);
        for (CrawledLink link : selectedLinks) {
            packages.stream().filter(pkg -> pkg.links().contains(link)).findFirst().ifPresent(affectedPackages::add);
        }
        var indices = new java.util.LinkedHashMap<CrawledPackage, Integer>();
        var linkOrder = new java.util.LinkedHashMap<CrawledPackage, List<CrawledLink>>();
        for (CrawledPackage pkg : affectedPackages) {
            indices.put(pkg, packages.indexOf(pkg));
            linkOrder.put(pkg, new java.util.ArrayList<>(pkg.links()));
        }

        engine.removeCrawled(selectedPackages);
        engine.removeCrawledLinks(selectedLinks);

        int n = selectedPackages.size() + selectedLinks.size();
        notifier.snack("Removed " + n + (n == 1 ? " item" : " items"), "Undo", () -> {
            for (var entry : indices.entrySet()) {
                if (!packages.contains(entry.getKey())) {
                    packages.add(Math.min(entry.getValue(), packages.size()), entry.getKey());
                }
                List<CrawledLink> wanted = linkOrder.get(entry.getKey());
                for (int i = 0; i < wanted.size(); i++) {
                    CrawledLink link = wanted.get(i);
                    if (!entry.getKey().links().contains(link)) {
                        entry.getKey().links().add(Math.min(i, entry.getKey().links().size()), link);
                    }
                }
            }
        });
    }

    private void confirmSelected() {
        Set<CrawledPackage> selectedPackages = selectedPackageRows();
        Set<CrawledLink> selectedLinks = selectedLinkRows(selectedPackages);
        boolean autoStart = engine.settings().autoStartProperty().get();
        if (!selectedPackages.isEmpty()) engine.confirmToDownloads(selectedPackages, autoStart);
        if (!selectedLinks.isEmpty()) engine.confirmLinksToDownloads(selectedLinks, autoStart);
    }

    private void pasteFromClipboard() {
        try {
            String s = javafx.scene.input.Clipboard.getSystemClipboard().getString();
            if (s != null && !s.isBlank()) {
                engine.addLinks(s, null, engine.settings().downloadFolderProperty().get(), false, false);
            }
        } catch (Exception ignored) {
        }
    }

    private enum AvailabilityFilter {
        ALL("All links", null),
        CHECKING("Checking", LinkAvailability.UNKNOWN),
        ONLINE("Online", LinkAvailability.ONLINE),
        OFFLINE("Offline", LinkAvailability.OFFLINE);

        private final String label;
        private final LinkAvailability availability;

        AvailabilityFilter(String label, LinkAvailability availability) {
            this.label = label;
            this.availability = availability;
        }

        boolean matches(LinkAvailability value) {
            return availability == null || value == availability;
        }

        AvailabilityFilter next() {
            AvailabilityFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        @Override public String toString() { return label; }
    }
}
