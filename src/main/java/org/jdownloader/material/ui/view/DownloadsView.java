package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadPriority;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.CompletedFileActions;
import org.jdownloader.material.ui.component.DownloadCells;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The Downloads list: package/file tree-table with toolbar, search and context menu. */
public final class DownloadsView extends BorderPane {

    private final DownloadEngine engine;
    private final NotificationCenter notifier;
    private final Runnable openAddLinks;
    private final I18n i18n;
    private final TreeTableView<DownloadItem> tree = new TreeTableView<>();
    private final TreeItem<DownloadItem> root = new TreeItem<>(null);
    private final ListChangeListener<DownloadLink> rebuildListener = c -> rebuild();
    private final ListChangeListener<DownloadPackage> downloadPackagesListener = c -> {
        while (c.next()) {
            for (DownloadPackage p : c.getAddedSubList()) attachPackage(p);
            for (DownloadPackage p : c.getRemoved()) detachPackage(p);
        }
        rebuild();
    };
    private final ListChangeListener<TreeItem<DownloadItem>> selectionListener = change -> refreshProperties();
    private final Set<DownloadPackage> observedPackages =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final VBox properties = new VBox(8);
    private final TextField editName = new TextField();
    private final TextField editDestination = new TextField();
    private final ButtonBase applyProperties = Mat.tonal("Apply changes", "check");
    private final javafx.scene.control.Label propertiesHint = Mat.label("", "row-desc");
    private final ChangeListener<DownloadState> propertiesStateListener = (o, was, is) -> refreshProperties();
    private DownloadItem propertiesItem;
    private DownloadItem observedPropertiesItem;
    private ChangeListener<Boolean> pausedListener;
    private volatile boolean disposed;
    private String filter = "";

    public DownloadsView(DownloadEngine engine, NotificationCenter notifier, Runnable openAddLinks, I18n i18n) {
        this.engine = engine;
        this.notifier = notifier;
        this.openAddLinks = openAddLinks;
        this.i18n = i18n;
        getStyleClass().add("content-area");
        setTop(buildHeaderAndToolbar());
        setCenter(buildTree());
        setBottom(buildProperties());
        wireModel();
        rebuild();
    }

    // --------------------------------------------------------------- Toolbar
    private VBox buildHeaderAndToolbar() {
        var title = Mat.label(i18n.text("downloads.title"), "headline");
        var search = new MFXTextField();
        search.setPromptText(i18n.text("downloads.search"));
        search.getStyleClass().add("search-field");
        search.setPrefWidth(260);
        search.textProperty().addListener((o, a, b) -> { filter = b == null ? "" : b.toLowerCase(); rebuild(); });

        HBox header = new HBox(12, title, Mat.hSpacer(), Icons0.search(), search);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);

        var addLinks = Mat.filled(i18n.text("downloads.add_links"), "add");
        addLinks.setOnAction(e -> openAddLinks.run());

        var start = Mat.text(i18n.text("toolbar.start"), "play");
        Mat.tip(start, i18n.text("tooltip.start"));
        start.setOnAction(e -> engine.start());
        var pause = Mat.text(i18n.text("toolbar.pause"), "pause");
        pause.setOnAction(e -> engine.pause(!engine.pausedProperty().get()));
        Runnable updatePause = () -> {
            boolean isPaused = engine.pausedProperty().get();
            pause.setText(i18n.text(isPaused ? "toolbar.resume" : "toolbar.pause"));
            pause.setGraphic(org.jdownloader.material.ui.Icons.of(isPaused ? "play" : "pause", 20));
            Mat.tip(pause, i18n.text(isPaused ? "tooltip.resume" : "tooltip.pause"));
        };
        updatePause.run();
        pausedListener = (o, wasPaused, isPaused) -> {
            if (!disposed) updatePause.run();
        };
        engine.pausedProperty().addListener(pausedListener);
        var stop = Mat.text(i18n.text("toolbar.stop"), "stop");
        Mat.tip(stop, i18n.text("tooltip.stop"));
        stop.setOnAction(e -> engine.stop());

        var top = Mat.text(i18n.text("toolbar.top"), "top");
        Mat.tip(top, i18n.text("tooltip.top"));
        top.setOnAction(e -> move(Move.TOP));
        var up = Mat.text(i18n.text("toolbar.up"), "up");
        Mat.tip(up, i18n.text("tooltip.up"));
        up.setOnAction(e -> move(Move.UP));
        var down = Mat.text(i18n.text("toolbar.down"), "down");
        Mat.tip(down, i18n.text("tooltip.down"));
        down.setOnAction(e -> move(Move.DOWN));
        var bottom = Mat.text(i18n.text("toolbar.bottom"), "bottom");
        Mat.tip(bottom, i18n.text("tooltip.bottom"));
        bottom.setOnAction(e -> move(Move.BOTTOM));

        var remove = Mat.text(i18n.text("toolbar.remove"), "delete");
        Mat.tip(remove, i18n.text("tooltip.remove"));
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> removeSelected());

        FlowPane bar = new FlowPane(6, 4, addLinks, Mat.vSep(), start, pause, stop, Mat.vSep(),
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
        tree.getSelectionModel().getSelectedItems().addListener(selectionListener);
        tree.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tree.setPlaceholder(Mat.label(i18n.text("empty.downloads"), "empty-table-hint"));

        TreeTableColumn<DownloadItem, String> name = new TreeTableColumn<>(i18n.text("column.name"));
        name.setCellValueFactory(p -> p.getValue().getValue().nameProperty());
        name.setCellFactory(DownloadCells.name());
        name.setPrefWidth(340);
        name.setMinWidth(200);

        TreeTableColumn<DownloadItem, Number> size = new TreeTableColumn<>(i18n.text("column.size"));
        size.setCellValueFactory(p -> p.getValue().getValue().bytesTotalProperty());
        size.setCellFactory(DownloadCells.bytes());
        size.setPrefWidth(96);

        TreeTableColumn<DownloadItem, String> host = new TreeTableColumn<>(i18n.text("column.host"));
        host.setCellValueFactory(p -> p.getValue().getValue().hostProperty());
        host.setCellFactory(DownloadCells.host(i18n));
        host.setPrefWidth(150);

        TreeTableColumn<DownloadItem, DownloadState> status = new TreeTableColumn<>(i18n.text("column.status"));
        status.setCellValueFactory(p -> p.getValue().getValue().stateProperty());
        status.setCellFactory(DownloadCells.status(i18n));
        status.setPrefWidth(130);

        TreeTableColumn<DownloadItem, String> details = new TreeTableColumn<>(i18n.text("column.details"));
        details.setCellValueFactory(p -> {
            DownloadItem item = p.getValue().getValue();
            return item instanceof DownloadLink link ? link.detailProperty() : new ReadOnlyStringWrapper("");
        });
        details.setPrefWidth(180);

        TreeTableColumn<DownloadItem, Number> progress = new TreeTableColumn<>(i18n.text("column.progress"));
        progress.setCellValueFactory(p -> p.getValue().getValue().progressProperty());
        progress.setCellFactory(DownloadCells.progress());
        progress.setPrefWidth(220);
        progress.setMinWidth(140);

        TreeTableColumn<DownloadItem, Number> speed = new TreeTableColumn<>(i18n.text("column.speed"));
        speed.setCellValueFactory(p -> p.getValue().getValue().speedProperty());
        speed.setCellFactory(DownloadCells.speed());
        speed.setPrefWidth(104);

        TreeTableColumn<DownloadItem, Number> eta = new TreeTableColumn<>(i18n.text("column.eta"));
        eta.setCellValueFactory(p -> p.getValue().getValue().speedProperty());
        eta.setCellFactory(DownloadCells.eta());
        eta.setPrefWidth(90);

        tree.getColumns().setAll(List.of(name, size, host, status, details, progress, speed, eta));
        tree.setContextMenu(buildContextMenu());
        return tree;
    }

    /**
     * Queued work can be adjusted directly beneath the table instead of
     * opening a blocking properties window. Active and completed transfers
     * intentionally remain read-only: their output path has already been
     * captured by the transfer or finalized on disk.
     */
    private VBox buildProperties() {
        var title = Mat.label(i18n.text("properties.selected"), "label-md");
        editName.setPromptText(i18n.text("properties.name"));
        editDestination.setPromptText(i18n.text("properties.destination"));
        applyProperties.setText(i18n.text("properties.apply"));
        HBox.setHgrow(editName, Priority.ALWAYS);
        HBox.setHgrow(editDestination, Priority.ALWAYS);
        HBox fields = new HBox(10, editName, editDestination, applyProperties);
        fields.setAlignment(Pos.CENTER_LEFT);

        applyProperties.setOnAction(e -> applyProperties());
        editName.setOnAction(e -> applyProperties());
        editDestination.setOnAction(e -> applyProperties());

        properties.getChildren().setAll(title, fields, propertiesHint);
        properties.setPadding(new Insets(12, 28, 16, 28));
        properties.getStyleClass().add("inline-properties");
        properties.setVisible(false);
        properties.setManaged(false);
        return properties;
    }

    private void refreshProperties() {
        if (disposed) return;
        List<DownloadItem> selection = selectedItems();
        if (selection.size() != 1) {
            propertiesItem = null;
            observePropertiesItem(null);
            properties.setVisible(false);
            properties.setManaged(false);
            return;
        }

        propertiesItem = selection.getFirst();
        observePropertiesItem(propertiesItem);
        boolean editable = isEditable(propertiesItem);
        if (propertiesItem instanceof DownloadLink link) {
            editName.setText(link.nameProp().get());
            editDestination.setText(link.destinationProperty().get());
        } else if (propertiesItem instanceof DownloadPackage pkg) {
            editName.setText(pkg.nameProp().get());
            editDestination.setText(pkg.destinationProperty().get());
        }
        editName.setDisable(!editable);
        editDestination.setDisable(!editable);
        applyProperties.setDisable(!editable);
        propertiesHint.setText(editable
                ? i18n.text("properties.hint.editable")
                : i18n.text("properties.hint.locked"));
        properties.setManaged(true);
        properties.setVisible(true);
    }

    private void observePropertiesItem(DownloadItem next) {
        if (observedPropertiesItem == next) return;
        if (observedPropertiesItem != null) {
            observedPropertiesItem.stateProperty().removeListener(propertiesStateListener);
        }
        observedPropertiesItem = next;
        if (observedPropertiesItem != null) {
            observedPropertiesItem.stateProperty().addListener(propertiesStateListener);
        }
    }

    private boolean isEditable(DownloadItem item) {
        if (item instanceof DownloadLink link) return isEditable(link.state());
        if (item instanceof DownloadPackage pkg) {
            return pkg.links().stream().allMatch(link -> isEditable(link.state()));
        }
        return false;
    }

    private static boolean isEditable(DownloadState state) {
        return state == DownloadState.QUEUED || state == DownloadState.ERROR || state == DownloadState.DISABLED;
    }

    private void applyProperties() {
        if (propertiesItem == null || !isEditable(propertiesItem)) return;
        String name = editName.getText() == null ? "" : editName.getText().trim();
        if (name.isBlank()) {
            notifier.snack(i18n.text("properties.name_required"));
            return;
        }
        String destination = editDestination.getText() == null ? "" : editDestination.getText().trim();
        if (propertiesItem instanceof DownloadLink link) {
            link.nameProp().set(name);
            link.destinationProperty().set(destination);
        } else if (propertiesItem instanceof DownloadPackage pkg) {
            pkg.nameProp().set(name);
            pkg.destinationProperty().set(destination);
            pkg.links().forEach(link -> link.destinationProperty().set(destination));
        }
        notifier.snack(i18n.text("properties.updated"));
        rebuild();
    }

    private ContextMenu buildContextMenu() {
        MenuItem start = new MenuItem(i18n.text("context.start"));
        start.setOnAction(e -> engine.startLinks(selectedLinks()));
        MenuItem force = new MenuItem(i18n.text("context.force_start"));
        force.setOnAction(e -> engine.forceStart(selectedLinks()));
        MenuItem stop = new MenuItem(i18n.text("context.stop"));
        stop.setOnAction(e -> engine.stopLinks(selectedLinks()));
        MenuItem enable = new MenuItem(i18n.text("context.enable"));
        enable.setOnAction(e -> engine.setEnabled(selectedLinks(), true));
        MenuItem disable = new MenuItem(i18n.text("context.disable"));
        disable.setOnAction(e -> engine.setEnabled(selectedLinks(), false));
        Menu priority = new Menu(i18n.text("context.priority"));
        ToggleGroup priorityGroup = new ToggleGroup();
        java.util.Map<DownloadPriority, RadioMenuItem> priorityOptions =
                new java.util.EnumMap<>(DownloadPriority.class);
        for (DownloadPriority value : DownloadPriority.values()) {
            RadioMenuItem option = new RadioMenuItem(i18n.text("priority." + value.name()));
            option.setToggleGroup(priorityGroup);
            option.setOnAction(e -> engine.setPriority(selectedLinks(), value));
            priority.getItems().add(option);
            priorityOptions.put(value, option);
        }
        MenuItem open = new MenuItem(i18n.text("context.open_file"));
        open.setOnAction(e -> firstCompletedLink().ifPresent(link -> CompletedFileActions.openFile(link, i18n)));
        MenuItem folder = new MenuItem(i18n.text("context.show_folder"));
        folder.setOnAction(e -> firstCompletedLink().ifPresent(link -> CompletedFileActions.showInFolder(link, i18n)));
        MenuItem expand = new MenuItem(i18n.text("context.expand"));
        expand.setOnAction(e -> toggleExpandSelected());
        MenuItem remove = new MenuItem(i18n.text("context.remove"));
        remove.setOnAction(e -> removeSelected());
        ContextMenu menu = new ContextMenu(start, force, stop, new SeparatorMenuItem(), enable, disable, priority,
                new SeparatorMenuItem(), open, folder, new SeparatorMenuItem(), expand,
                new SeparatorMenuItem(), remove);
        menu.setOnShowing(e -> {
            List<DownloadLink> links = selectedLinks();
            boolean hasLinks = !links.isEmpty();
            start.setDisable(!hasLinks);
            force.setDisable(!hasLinks);
            stop.setDisable(!hasLinks);
            enable.setDisable(!hasLinks);
            disable.setDisable(!hasLinks);
            priority.setDisable(!hasLinks);
            remove.setDisable(selectedItems().isEmpty());
            expand.setDisable(selectedItems().stream().noneMatch(DownloadItem::isPackage));
            boolean hasCompletedFile = firstCompletedLink().isPresent();
            open.setDisable(!hasCompletedFile);
            folder.setDisable(!hasCompletedFile);

            priorityGroup.selectToggle(null);
            DownloadPriority shared = sharedPriority(links);
            if (shared != null) priorityOptions.get(shared).setSelected(true);
        });
        return menu;
    }

    // ----------------------------------------------------------- Model sync
    private void wireModel() {
        engine.downloadPackages().addListener(downloadPackagesListener);
        for (DownloadPackage p : engine.downloadPackages()) attachPackage(p);
    }

    private void attachPackage(DownloadPackage p) {
        if (observedPackages.add(p)) p.links().addListener(rebuildListener);
    }

    private void detachPackage(DownloadPackage p) {
        if (observedPackages.remove(p)) p.links().removeListener(rebuildListener);
    }

    private void rebuild() {
        if (disposed) return;
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
        // Preserve both package and link selection while asynchronous state
        // changes refresh the tree, so a user can keep editing the same row.
        if (!selectedBefore.isEmpty()) {
            for (TreeItem<DownloadItem> pi : root.getChildren()) restoreSelection(pi, selectedBefore);
        }
        refreshProperties();
    }

    /**
     * Releases listeners held by the engine and download models before the
     * language shell replaces this view. The engine outlives individual views,
     * so leaving these registrations in place would keep discarded controls
     * alive and duplicate rebuild work after each language change.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        engine.downloadPackages().removeListener(downloadPackagesListener);
        for (DownloadPackage p : new ArrayList<>(observedPackages)) detachPackage(p);
        if (pausedListener != null) engine.pausedProperty().removeListener(pausedListener);
        tree.getSelectionModel().getSelectedItems().removeListener(selectionListener);
        observePropertiesItem(null);
    }

    private void restoreSelection(TreeItem<DownloadItem> item, List<DownloadItem> selectedBefore) {
        if (selectedBefore.contains(item.getValue())) tree.getSelectionModel().select(item);
        for (TreeItem<DownloadItem> child : item.getChildren()) restoreSelection(child, selectedBefore);
    }

    /** Selects a safely editable leaf for deterministic documentation capture. */
    public void selectFirstEditableForCapture() {
        TreeItem<DownloadItem> item = findEditableLeaf(root, true);
        if (item == null) item = findEditableLeaf(root, false);
        if (item == null) return;
        tree.getSelectionModel().clearSelection();
        tree.getSelectionModel().select(item);
        int row = tree.getRow(item);
        if (row >= 0) tree.scrollTo(row);
    }

    /** Clears table selection for the standard, unselected documentation view. */
    public void clearSelectionForCapture() {
        tree.getSelectionModel().clearSelection();
    }

    private TreeItem<DownloadItem> findEditableLeaf(TreeItem<DownloadItem> parent, boolean errorsFirst) {
        for (TreeItem<DownloadItem> child : parent.getChildren()) {
            if (child.getValue() instanceof DownloadLink link && isEditable(link)
                    && (!errorsFirst || link.state() == DownloadState.ERROR)) {
                return child;
            }
            TreeItem<DownloadItem> nested = findEditableLeaf(child, errorsFirst);
            if (nested != null) return nested;
        }
        return null;
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

    // ---------------------------------------------------------------- Remove
    /**
     * Removes the selection immediately and offers Undo via snackbar — the
     * Material pattern replacing a confirm-delete dialog. Undo is additive:
     * it re-inserts what was removed without disturbing anything added since.
     */
    private void removeSelected() {
        List<DownloadItem> selection = selectedItems();
        if (selection.isEmpty()) return;
        // Snapshot the full structure so Undo can restore packages emptied
        // (and auto-dropped) as a side effect of removing their last links.
        var packages = engine.downloadPackages();
        List<DownloadPackage> pkgOrder = new ArrayList<>(packages);
        var linkOrder = new java.util.HashMap<DownloadPackage, List<DownloadLink>>();
        for (DownloadPackage p : pkgOrder) linkOrder.put(p, new ArrayList<>(p.links()));

        int n = selection.size();
        engine.removeDownloads(selection);

        notifier.snack(i18n.text(n == 1 ? "snack.removed.one" : "snack.removed.many", n),
                i18n.text("action.undo"), () -> {
            for (int i = 0; i < pkgOrder.size(); i++) {
                DownloadPackage p = pkgOrder.get(i);
                if (!packages.contains(p)) {
                    packages.add(Math.min(i, packages.size()), p);
                }
                List<DownloadLink> wanted = linkOrder.get(p);
                for (int j = 0; j < wanted.size(); j++) {
                    DownloadLink l = wanted.get(j);
                    if (!p.links().contains(l)) {
                        p.links().add(Math.min(j, p.links().size()), l);
                    }
                }
            }
        });
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
        return out;
    }

    private java.util.Optional<DownloadLink> firstCompletedLink() {
        return selectedLinks().stream()
                .filter(link -> link.state() == DownloadState.FINISHED)
                .filter(link -> !link.outputPathProperty().get().isBlank())
                .findFirst();
    }

    private static DownloadPriority sharedPriority(List<DownloadLink> links) {
        if (links.isEmpty()) return null;
        DownloadPriority first = links.getFirst().priorityProperty().get();
        return links.stream().allMatch(link -> link.priorityProperty().get() == first) ? first : null;
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
