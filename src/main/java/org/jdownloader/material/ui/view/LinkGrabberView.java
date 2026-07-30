package org.jdownloader.material.ui.view;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.LinkAvailability;
import org.jdownloader.material.ui.component.ActivityStatus;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.util.Formats;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The LinkGrabber staging area: crawled packages awaiting confirmation into Downloads. */
public final class LinkGrabberView extends BorderPane {

    private final DownloadEngine engine;
    private final ActivityStatus activity;
    private final Runnable openAddLinks;
    private final I18n i18n;
    private final TreeTableView<Object> tree = new TreeTableView<>();
    private final TreeItem<Object> root = new TreeItem<>(null);
    private final javafx.beans.InvalidationListener refresh = o -> requestRebuild();
    private final PauseTransition rebuildDelay = new PauseTransition(Duration.millis(80));
    private final Map<CrawledPackage, ListChangeListener<CrawledLink>> packageLinkListeners = new HashMap<>();
    private final Map<CrawledLink, Integer> linkListenerReferences = new IdentityHashMap<>();
    private final ListChangeListener<CrawledPackage> crawledPackagesListener = c -> {
        while (c.next()) {
            for (CrawledPackage p : c.getAddedSubList()) attach(p);
            for (CrawledPackage p : c.getRemoved()) detach(p);
        }
        requestRebuild();
    };
    private volatile boolean disposed;
    private boolean rebuildScheduled;
    private String filter = "";
    private AvailabilityFilter availabilityFilter = AvailabilityFilter.ALL;

    public LinkGrabberView(DownloadEngine engine, ActivityStatus activity, Runnable openAddLinks, I18n i18n) {
        this.engine = engine;
        this.activity = activity;
        this.openAddLinks = openAddLinks;
        this.i18n = i18n;
        getStyleClass().addAll("content-area", "page-view");
        TreeTableView<Object> table = buildTree();
        setTop(buildPageHeader(table));
        VBox panel = new VBox(buildTableTools(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        panel.getStyleClass().add("content-panel");
        setCenter(panel);
        rebuildDelay.setOnFinished(event -> {
            rebuildScheduled = false;
            if (!disposed) rebuild();
        });
        wireModel();
        rebuild();
    }

    private HBox buildPageHeader(TreeTableView<Object> table) {
        var title = Mat.label(i18n.text("linkgrabber.title"), "headline", "page-title");
        MenuButton columns = columnMenu(table);
        HBox header = new HBox(12, title, Mat.hSpacer(), columns);
        header.getStyleClass().addAll("view-header", "page-head");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private MenuButton columnMenu(TreeTableView<Object> table) {
        MenuButton menu = new MenuButton(i18n.text("downloads.columns"),
                org.jdownloader.material.ui.Icons.of("more", 16));
        menu.getStyleClass().addAll("page-actions", "move-menu");
        for (int index = 1; index < table.getColumns().size(); index++) {
            TreeTableColumn<Object, ?> column = table.getColumns().get(index);
            CheckMenuItem item = new CheckMenuItem(column.getText());
            item.selectedProperty().bindBidirectional(column.visibleProperty());
            menu.getItems().add(item);
        }
        return menu;
    }

    private HBox buildTableTools() {
        var availability = Mat.outlined(availabilityFilter.label(i18n), null);
        availability.getStyleClass().add("filter-chip");
        availability.setOnAction(e -> {
            availabilityFilter = availabilityFilter.next();
            availability.setText(availabilityFilter.label(i18n));
            requestRebuild();
        });

        var addLinks = Mat.text(i18n.text("linkgrabber.add_links"), "add");
        addLinks.setOnAction(e -> openAddLinks.run());
        var paste = Mat.text(i18n.text("linkgrabber.paste"), "paste");
        paste.setOnAction(e -> pasteFromClipboard());

        var confirm = Mat.tonal(i18n.text("linkgrabber.add_to_downloads"), "check");
        confirm.setOnAction(e -> confirmSelected());
        confirm.disableProperty().bind(Bindings.isEmpty(tree.getSelectionModel().getSelectedItems()));
        var addAll = Mat.text(i18n.text("linkgrabber.add_all"), null);
        addAll.setOnAction(e -> {
            engine.confirmAll(engine.settings().autoStartProperty().get());
        });

        var remove = Mat.text(i18n.text("linkgrabber.remove"), "delete");
        Mat.tip(remove, i18n.text("tooltip.remove"));
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> removeSelected());

        HBox bar = new HBox(8, availability, addLinks, paste, Mat.hSpacer(), confirm, addAll, remove);
        bar.getStyleClass().addAll("action-toolbar", "table-tools");
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(bar, Priority.ALWAYS);
        return bar;
    }

    private TreeTableView<Object> buildTree() {
        tree.getStyleClass().add("data-table");
        tree.setShowRoot(false);
        tree.setRoot(root);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setPlaceholder(Mat.label(i18n.text("empty.linkgrabber"), "empty-table-hint"));

        TreeTableColumn<Object, String> name = new TreeTableColumn<>(i18n.text("column.name"));
        name.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledPackage cp) return cp.nameProperty();
            if (o instanceof CrawledLink cl) return cl.nameProperty();
            return new ReadOnlyStringWrapper("");
        });
        name.setPrefWidth(320);
        name.setMinWidth(180);

        TreeTableColumn<Object, LinkAvailability> avail = new TreeTableColumn<>(i18n.text("column.availability"));
        avail.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.availabilityProperty();
            return new ReadOnlyObjectWrapper<>(null);
        });
        avail.setCellFactory(availabilityCell());
        avail.setPrefWidth(140);

        TreeTableColumn<Object, String> host = new TreeTableColumn<>(i18n.text("column.host"));
        host.setCellValueFactory(p -> {
            Object o = p.getValue().getValue();
            if (o instanceof CrawledLink cl) return cl.hostProperty();
            return new ReadOnlyStringWrapper("");
        });
        host.setCellFactory(col -> new TreeTableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : "unknown".equalsIgnoreCase(value) ? i18n.text("host.unknown") : value);
            }
        });
        host.setPrefWidth(150);

        TreeTableColumn<Object, Number> size = new TreeTableColumn<>(i18n.text("column.size"));
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

        TreeTableColumn<Object, String> url = new TreeTableColumn<>(i18n.text("column.url"));
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
            { dot.getStyleClass().add("status-dot"); }
            @Override protected void updateItem(LinkAvailability a, boolean empty) {
                super.updateItem(a, empty);
                Object row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) { setGraphic(null); setText(null); return; }
                if (row instanceof CrawledPackage cp) {
                    setGraphic(null);
                    setText(i18n.text("availability.count", cp.onlineCount(), cp.links().size()));
                    return;
                }
                LinkAvailability av = a == null ? LinkAvailability.UNKNOWN : a;
                dot.getStyleClass().removeAll("state-finished", "state-offline", "state-checking");
                dot.getStyleClass().add(switch (av) {
                    case ONLINE -> "state-finished";
                    case OFFLINE -> "state-offline";
                    case UNKNOWN -> "state-checking";
                });
                var label = new javafx.scene.control.Label(i18n.text("availability." + av.name()));
                label.getStyleClass().add("table-content-label");
                box.getChildren().setAll(dot, label);
                setGraphic(box);
                setText(null);
            }
        };
    }

    private void wireModel() {
        engine.crawledPackages().addListener(crawledPackagesListener);
        for (CrawledPackage p : engine.crawledPackages()) attach(p);
    }

    private void attach(CrawledPackage p) {
        if (disposed) return;
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
        int references = linkListenerReferences.getOrDefault(link, 0);
        linkListenerReferences.put(link, references + 1);
        if (references > 0) return;
        link.availabilityProperty().addListener(refresh);
        link.sizeProperty().addListener(refresh);
    }

    private void detachLink(CrawledLink link) {
        Integer references = linkListenerReferences.get(link);
        if (references == null) return;
        if (references > 1) {
            linkListenerReferences.put(link, references - 1);
            return;
        }
        linkListenerReferences.remove(link);
        link.availabilityProperty().removeListener(refresh);
        link.sizeProperty().removeListener(refresh);
    }

    /**
     * Probes can complete in dense bursts. Rebuild at most once per short
     * interval so a large paste does not repeatedly clear and restore the
     * entire tree on the JavaFX Application Thread.
     */
    private void requestRebuild() {
        if (disposed || rebuildScheduled) return;
        rebuildScheduled = true;
        rebuildDelay.playFromStart();
    }

    private void rebuild() {
        if (disposed) return;
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

    /** Applies the search field hosted by the global application toolbar. */
    public void setFilter(String value) {
        String next = value == null ? "" : value.trim().toLowerCase();
        if (next.equals(filter)) return;
        filter = next;
        requestRebuild();
    }

    /**
     * Stops deferred work and removes every listener this view registered on
     * the long-lived crawler model before a language shell rebuild replaces it.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        rebuildDelay.stop();
        rebuildDelay.setOnFinished(null);
        rebuildScheduled = false;
        engine.crawledPackages().removeListener(crawledPackagesListener);
        for (CrawledPackage p : new java.util.ArrayList<>(packageLinkListeners.keySet())) detach(p);
        // The package map should have removed every link registration. Keep a
        // defensive final pass so an inconsistent model change cannot retain
        // this discarded view through a link property listener.
        for (CrawledLink link : new java.util.ArrayList<>(linkListenerReferences.keySet())) {
            link.availabilityProperty().removeListener(refresh);
            link.sizeProperty().removeListener(refresh);
        }
        linkListenerReferences.clear();
        packageLinkListeners.clear();
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

    /** Immediate remove; the append-only History page provides durable undo. */
    private void removeSelected() {
        Set<CrawledPackage> selectedPackages = selectedPackageRows();
        Set<CrawledLink> selectedLinks = selectedLinkRows(selectedPackages);
        if (selectedPackages.isEmpty() && selectedLinks.isEmpty()) return;
        engine.removeCrawled(selectedPackages);
        engine.removeCrawledLinks(selectedLinks);

        int n = selectedPackages.size() + selectedLinks.size();
        activity.info(i18n.text(n == 1 ? "activity.removed.one" : "activity.removed.many", n));
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
        ALL("linkgrabber.filter.all", null),
        CHECKING("linkgrabber.filter.checking", LinkAvailability.UNKNOWN),
        ONLINE("linkgrabber.filter.online", LinkAvailability.ONLINE),
        OFFLINE("linkgrabber.filter.offline", LinkAvailability.OFFLINE);

        private final String key;
        private final LinkAvailability availability;

        AvailabilityFilter(String key, LinkAvailability availability) {
            this.key = key;
            this.availability = availability;
        }

        String label(I18n i18n) {
            return i18n.text(key);
        }

        boolean matches(LinkAvailability value) {
            return availability == null || value == availability;
        }

        AvailabilityFilter next() {
            AvailabilityFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }
}
