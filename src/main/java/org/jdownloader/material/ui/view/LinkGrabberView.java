package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
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
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.LinkAvailability;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.util.Formats;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The LinkGrabber staging area: crawled packages awaiting confirmation into Downloads. */
public final class LinkGrabberView extends BorderPane {

    private final DownloadEngine engine;
    private final NotificationCenter notifier;
    private final Runnable openAddLinks;
    private final TreeTableView<Object> tree = new TreeTableView<>();
    private final TreeItem<Object> root = new TreeItem<>(null);
    private final javafx.beans.InvalidationListener refresh = o -> rebuild();
    private String filter = "";

    public LinkGrabberView(DownloadEngine engine, NotificationCenter notifier, Runnable openAddLinks) {
        this.engine = engine;
        this.notifier = notifier;
        this.openAddLinks = openAddLinks;
        getStyleClass().add("content-area");
        setTop(buildHeaderAndToolbar());
        setCenter(buildTree());
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
        HBox header = new HBox(12, title, Mat.hSpacer(), org.jdownloader.material.ui.Icons.of("search", 20), search);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var addLinks = Mat.filled("Add Links", "add");
        addLinks.setOnAction(e -> openAddLinks.run());
        var paste = Mat.tonal("Paste", "paste");
        paste.setOnAction(e -> pasteFromClipboard());

        var confirm = Mat.filled("Add to Downloads", "check");
        confirm.setOnAction(e -> confirmSelected());
        var addAll = Mat.outlined("Add all", null);
        addAll.setOnAction(e -> {
            int n = engine.crawledPackages().stream().mapToInt(p -> p.links().size()).sum();
            engine.confirmAll(false);
            if (n > 0) notifier.snack("Added " + n + (n == 1 ? " link" : " links") + " to Downloads");
        });

        var remove = Mat.icon("delete", "Remove selected");
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> {
            int n = selectedPackages().size();
            engine.removeCrawled(selectedPackages());
            if (n > 0) notifier.snack("Removed " + n + (n == 1 ? " package" : " packages"));
        });

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
                setText(empty || v == null || v.longValue() <= 0 ? "—" : Formats.bytes(v.longValue()));
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
                box.getChildren().setAll(dot, new javafx.scene.control.Label(av.label()));
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
            rebuild();
        });
        for (CrawledPackage p : engine.crawledPackages()) attach(p);
    }

    private void attach(CrawledPackage p) {
        p.links().addListener(refresh);
        for (CrawledLink l : p.links()) l.availabilityProperty().addListener(refresh);
    }

    private void detach(CrawledPackage p) {
        p.links().removeListener(refresh);
        for (CrawledLink l : p.links()) l.availabilityProperty().removeListener(refresh);
    }

    private void rebuild() {
        root.getChildren().clear();
        for (CrawledPackage pkg : engine.crawledPackages()) {
            boolean pkgMatch = filter.isEmpty() || pkg.name().toLowerCase().contains(filter);
            TreeItem<Object> pi = new TreeItem<>(pkg);
            pi.setExpanded(pkg.expandedProperty().get());
            for (CrawledLink l : pkg.links()) {
                if (filter.isEmpty() || pkgMatch
                        || l.name().toLowerCase().contains(filter)
                        || l.host().toLowerCase().contains(filter)) {
                    l.availabilityProperty().removeListener(refresh);
                    l.availabilityProperty().addListener(refresh);
                    pi.getChildren().add(new TreeItem<>(l));
                }
            }
            if (pkgMatch || !pi.getChildren().isEmpty()) root.getChildren().add(pi);
        }
    }

    private Set<CrawledPackage> selectedPackages() {
        Set<CrawledPackage> out = new LinkedHashSet<>();
        for (TreeItem<Object> item : tree.getSelectionModel().getSelectedItems()) {
            if (item == null || item.getValue() == null) continue;
            Object o = item.getValue();
            if (o instanceof CrawledPackage cp) out.add(cp);
            else if (o instanceof CrawledLink cl) parentOf(cl).ifPresent(out::add);
        }
        return out;
    }

    private java.util.Optional<CrawledPackage> parentOf(CrawledLink link) {
        return engine.crawledPackages().stream().filter(p -> p.links().contains(link)).findFirst();
    }

    private void confirmSelected() {
        Set<CrawledPackage> sel = selectedPackages();
        int n;
        if (sel.isEmpty()) {
            n = engine.crawledPackages().stream().mapToInt(p -> p.links().size()).sum();
            engine.confirmAll(false);
        } else {
            n = sel.stream().mapToInt(p -> p.links().size()).sum();
            engine.confirmToDownloads(sel, false);
        }
        if (n > 0) notifier.snack("Added " + n + (n == 1 ? " link" : " links") + " to Downloads");
    }

    private void pasteFromClipboard() {
        try {
            String s = javafx.scene.input.Clipboard.getSystemClipboard().getString();
            if (s != null && !s.isBlank()) {
                long n = s.lines().map(String::trim).filter(x -> !x.isEmpty()).count();
                engine.addLinks(s, null, false);
                notifier.snack(n + (n == 1 ? " link" : " links") + " added to LinkGrabber");
            } else {
                notifier.snack("Clipboard has no links to paste");
            }
        } catch (Exception ignored) {
        }
    }
}
