package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.ui.component.DownloadCells;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** The Downloads list: package/file tree-table with toolbar, search and context menu. */
public final class DownloadsView extends BorderPane {

    private final DownloadEngine engine;
    private final NotificationCenter notifier;
    private final Runnable openAddLinks;
    private final TreeTableView<DownloadItem> tree = new TreeTableView<>();
    private final TreeItem<DownloadItem> root = new TreeItem<>(null);
    private final ListChangeListener<Object> rebuildListener = c -> rebuild();
    private String filter = "";

    public DownloadsView(DownloadEngine engine, NotificationCenter notifier, Runnable openAddLinks) {
        this.engine = engine;
        this.notifier = notifier;
        this.openAddLinks = openAddLinks;
        getStyleClass().add("content-area");
        setTop(buildHeaderAndToolbar());
        setCenter(buildTree());
        wireModel();
        rebuild();
    }

    // --------------------------------------------------------------- Toolbar
    private VBox buildHeaderAndToolbar() {
        var title = Mat.label("Downloads", "headline");
        var search = new MFXTextField();
        search.setPromptText("Search downloads");
        search.getStyleClass().add("search-field");
        search.setPrefWidth(260);
        search.textProperty().addListener((o, a, b) -> { filter = b == null ? "" : b.toLowerCase(); rebuild(); });

        HBox header = new HBox(12, title, Mat.hSpacer(), Icons0.search(), search);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var addLinks = Mat.filled("Add Links", "add");
        addLinks.setOnAction(e -> openAddLinks.run());

        var start = Mat.icon("play", "Start downloads");
        start.setOnAction(e -> { engine.start(); notifier.snack("Downloads started"); });
        var pause = Mat.icon("pause", "Pause");
        pause.setOnAction(e -> {
            boolean willPause = !engine.pausedProperty().get();
            engine.pause(willPause);
            notifier.snack(willPause ? "Downloads paused" : "Downloads resumed");
        });
        var stop = Mat.icon("stop", "Stop all");
        stop.setOnAction(e -> { engine.stop(); notifier.snack("Downloads stopped"); });

        var top = Mat.icon("top", "Move to top");
        top.setOnAction(e -> move(Move.TOP));
        var up = Mat.icon("up", "Move up");
        up.setOnAction(e -> move(Move.UP));
        var down = Mat.icon("down", "Move down");
        down.setOnAction(e -> move(Move.DOWN));
        var bottom = Mat.icon("bottom", "Move to bottom");
        bottom.setOnAction(e -> move(Move.BOTTOM));

        var remove = Mat.icon("delete", "Remove selected");
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> {
            int n = selectedItems().size();
            engine.removeDownloads(selectedItems());
            if (n > 0) notifier.snack("Removed " + n + (n == 1 ? " item" : " items"));
        });

        HBox bar = new HBox(6, addLinks, Mat.vSep(), start, pause, stop, Mat.vSep(),
                top, up, down, bottom, Mat.vSep(), remove);
        bar.getStyleClass().add("action-toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        return new VBox(header, bar);
    }

    // ----------------------------------------------------------------- Tree
    private TreeTableView<DownloadItem> buildTree() {
        tree.getStyleClass().add("data-table");
        tree.setShowRoot(false);
        tree.setRoot(root);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setPlaceholder(Mat.label("No downloads yet — click “Add Links” to get started.", "empty-table-hint"));

        TreeTableColumn<DownloadItem, String> name = new TreeTableColumn<>("Name");
        name.setCellValueFactory(p -> p.getValue().getValue().nameProperty());
        name.setCellFactory(DownloadCells.name());
        name.setPrefWidth(340);
        name.setMinWidth(200);

        TreeTableColumn<DownloadItem, Number> size = new TreeTableColumn<>("Size");
        size.setCellValueFactory(p -> p.getValue().getValue().bytesTotalProperty());
        size.setCellFactory(DownloadCells.bytes());
        size.setPrefWidth(96);

        TreeTableColumn<DownloadItem, String> host = new TreeTableColumn<>("Host");
        host.setCellValueFactory(p -> p.getValue().getValue().hostProperty());
        host.setPrefWidth(150);

        TreeTableColumn<DownloadItem, DownloadState> status = new TreeTableColumn<>("Status");
        status.setCellValueFactory(p -> p.getValue().getValue().stateProperty());
        status.setCellFactory(DownloadCells.status());
        status.setPrefWidth(130);

        TreeTableColumn<DownloadItem, Number> progress = new TreeTableColumn<>("Progress");
        progress.setCellValueFactory(p -> p.getValue().getValue().progressProperty());
        progress.setCellFactory(DownloadCells.progress());
        progress.setPrefWidth(220);
        progress.setMinWidth(140);

        TreeTableColumn<DownloadItem, Number> speed = new TreeTableColumn<>("Speed");
        speed.setCellValueFactory(p -> p.getValue().getValue().speedProperty());
        speed.setCellFactory(DownloadCells.speed());
        speed.setPrefWidth(104);

        TreeTableColumn<DownloadItem, Number> eta = new TreeTableColumn<>("ETA");
        eta.setCellValueFactory(p -> p.getValue().getValue().speedProperty());
        eta.setCellFactory(DownloadCells.eta());
        eta.setPrefWidth(90);

        tree.getColumns().setAll(List.of(name, size, host, status, progress, speed, eta));
        tree.setContextMenu(buildContextMenu());
        return tree;
    }

    private ContextMenu buildContextMenu() {
        MenuItem start = new MenuItem("Start");
        start.setOnAction(e -> engine.forceStart(selectedLinks()));
        MenuItem force = new MenuItem("Force start");
        force.setOnAction(e -> engine.forceStart(selectedLinks()));
        MenuItem stop = new MenuItem("Stop");
        stop.setOnAction(e -> engine.stop());
        MenuItem expand = new MenuItem("Expand / collapse");
        expand.setOnAction(e -> toggleExpandSelected());
        MenuItem remove = new MenuItem("Remove");
        remove.setOnAction(e -> engine.removeDownloads(selectedItems()));
        return new ContextMenu(start, force, stop, new SeparatorMenuItem(), expand,
                new SeparatorMenuItem(), remove);
    }

    // ----------------------------------------------------------- Model sync
    private void wireModel() {
        engine.downloadPackages().addListener((ListChangeListener<DownloadPackage>) c -> {
            while (c.next()) {
                for (DownloadPackage p : c.getAddedSubList()) p.links().addListener(rebuildListener);
                for (DownloadPackage p : c.getRemoved()) p.links().removeListener(rebuildListener);
            }
            rebuild();
        });
        for (DownloadPackage p : engine.downloadPackages()) p.links().addListener(rebuildListener);
    }

    private void rebuild() {
        var selectedBefore = selectedItems();
        root.getChildren().clear();
        for (DownloadPackage pkg : engine.downloadPackages()) {
            if (!matchesPackage(pkg)) continue;
            TreeItem<DownloadItem> pi = new TreeItem<>(pkg);
            pi.setExpanded(pkg.expandedProperty().get());
            pi.expandedProperty().addListener((o, a, b) -> pkg.expandedProperty().set(b));
            for (DownloadLink l : pkg.links()) {
                if (matchesLink(l)) pi.getChildren().add(new TreeItem<>(l));
            }
            root.getChildren().add(pi);
        }
        // best-effort selection restore
        if (!selectedBefore.isEmpty()) {
            for (TreeItem<DownloadItem> pi : root.getChildren()) {
                if (selectedBefore.contains(pi.getValue())) tree.getSelectionModel().select(pi);
            }
        }
    }

    private boolean matchesPackage(DownloadPackage p) {
        if (filter.isEmpty()) return true;
        if (p.nameProp().get().toLowerCase().contains(filter)) return true;
        return p.links().stream().anyMatch(this::matchesLink);
    }

    private boolean matchesLink(DownloadLink l) {
        return filter.isEmpty()
                || l.nameProperty().getValue().toLowerCase().contains(filter)
                || l.hostProperty().getValue().toLowerCase().contains(filter);
    }

    // ------------------------------------------------------------- Selection
    private List<DownloadItem> selectedItems() {
        return tree.getSelectionModel().getSelectedItems().stream()
                .filter(i -> i != null && i.getValue() != null)
                .map(TreeItem::getValue)
                .collect(Collectors.toList());
    }

    private List<DownloadLink> selectedLinks() {
        List<DownloadLink> out = new ArrayList<>();
        for (DownloadItem it : selectedItems()) {
            if (it instanceof DownloadLink l) out.add(l);
            else if (it instanceof DownloadPackage p) out.addAll(p.links());
        }
        if (out.isEmpty()) engine.downloadPackages().forEach(p -> out.addAll(p.links()));
        return out;
    }

    private enum Move { TOP, UP, DOWN, BOTTOM }

    private void toggleExpandSelected() {
        for (TreeItem<DownloadItem> i : tree.getSelectionModel().getSelectedItems()) {
            if (i != null && i.getValue() != null && i.getValue().isPackage()) i.setExpanded(!i.isExpanded());
        }
    }

    private void move(Move dir) {
        var packages = engine.downloadPackages();
        DownloadPackage sel = selectedItems().stream()
                .map(i -> i instanceof DownloadPackage p ? p : parentOf(i))
                .filter(p -> p != null).findFirst().orElse(null);
        if (sel == null) return;
        int i = packages.indexOf(sel);
        if (i < 0) return;
        switch (dir) {
            case TOP -> { if (i > 0) { packages.remove(i); packages.add(0, sel); } }
            case UP -> { if (i > 0) { packages.remove(i); packages.add(i - 1, sel); } }
            case DOWN -> { if (i < packages.size() - 1) { packages.remove(i); packages.add(i + 1, sel); } }
            case BOTTOM -> { if (i < packages.size() - 1) { packages.remove(i); packages.add(sel); } }
        }
    }

    private DownloadPackage parentOf(DownloadItem link) {
        for (DownloadPackage p : engine.downloadPackages()) {
            if (p.links().contains(link)) return p;
        }
        return null;
    }

    /** Tiny leading search glyph for the header. */
    private static final class Icons0 {
        static javafx.scene.Node search() {
            return org.jdownloader.material.ui.Icons.of("search", 20);
        }
    }
}
