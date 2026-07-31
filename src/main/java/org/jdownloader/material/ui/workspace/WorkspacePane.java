package org.jdownloader.material.ui.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.ui.appearance.AppearanceRegistry;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.M3Dialogs;
import org.jdownloader.material.ui.search.SearchField;
import org.jdownloader.material.workspace.GitWorkspaceStore;
import org.jdownloader.material.workspace.WorkspaceGroup;
import org.jdownloader.material.workspace.WorkspacePage;
import org.jdownloader.material.workspace.WorkspaceSnapshot;
import org.jdownloader.material.workspace.WorkspaceStyle;
import org.jdownloader.material.workspace.WorkspaceTab;

/**
 * Browser-style, Git-persisted desktop workspace.
 *
 * <p>Pinned tabs live outside the scrolling region. Groups, search state and
 * bulk-close previews are owned by this strip rather than hidden in a skin.</p>
 */
public final class WorkspacePane extends BorderPane implements AutoCloseable {

    @FunctionalInterface
    public interface PageFactory { WorkspaceContent create(WorkspaceTab descriptor); }

    private enum BulkScope {
        CURRENT_GROUP("workspace.scope.current_group"),
        SELECTED_GROUPS("workspace.scope.selected_groups"),
        ALL_GROUPS("workspace.scope.all_groups");
        private final String key;
        BulkScope(String key) { this.key = key; }
    }

    private final I18n i18n;
    private final NotificationService notifications;
    private final PageFactory factory;
    private final GitWorkspaceStore store;
    private final Map<UUID, WorkspaceContent> contents = new LinkedHashMap<>();
    private final Map<UUID, SearchField> groupSearches = new HashMap<>();
    private final Map<UUID, ToggleButton> tabButtons = new LinkedHashMap<>();
    private final Set<UUID> selectedGroups = new HashSet<>();
    private final ReadOnlyObjectWrapper<WorkspacePage> activePage = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper applicationName = new ReadOnlyStringWrapper(this,
            "applicationName", "JDownloader Material");
    private final SearchField stripSearch;
    private final SearchField masterSearch;
    private final SearchField groupNameSearch;
    private final StackPane contentHost = new StackPane();
    private final HBox pinnedStrip = new HBox(4);
    private final ScrollPane pinnedScroll = new ScrollPane(pinnedStrip);
    private final HBox regularStrip = new HBox(8);
    private final ScrollPane regularScroll = new ScrollPane(regularStrip);
    private final MenuButton overflow = new MenuButton();
    private final MenuButton groupsButton = new MenuButton();
    private final MenuButton bulkButton = new MenuButton();
    private final MenuButton newTabButton = new MenuButton();
    private final Map<WorkspacePage, String> pageKeys = new EnumMap<>(WorkspacePage.class);
    private WorkspaceSnapshot snapshot = WorkspaceSnapshot.fresh();
    private boolean disposed;

    public WorkspacePane(Path root, I18n i18n, NotificationService notifications, PageFactory factory) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.store = new GitWorkspaceStore(Objects.requireNonNull(root, "root"));
        configurePageKeys();
        getStyleClass().add("workspace-pane");

        stripSearch = new SearchField(i18n, "workspace.search.strip");
        masterSearch = new SearchField(i18n, "workspace.search.master");
        groupNameSearch = new SearchField(i18n, "workspace.search.groups");
        stripSearch.setMaxWidth(260);
        masterSearch.setMaxWidth(260);
        stripSearch.searchSpecProperty().addListener((observable, previous, current) -> rebuildStrip());
        stripSearch.searchSpecProperty().addListener((observable, previous, current) -> applyActiveSearch(current));
        masterSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshMasterResults());
        groupNameSearch.searchSpecProperty().addListener((observable, previous, current) -> rebuildGroupMenu());

        buildChrome();
        applySnapshot(snapshot);
        onFx(store.load(), loaded -> {
            applySnapshot(loaded);
            notifications.success(i18n.text("workspace.ready.title"), i18n.text("workspace.status.ready"));
        }, error -> notifications.error(i18n.text("workspace.failed.title"),
                i18n.text("workspace.status.load_failed", message(error))));
    }

    public ReadOnlyObjectProperty<WorkspacePage> activePageProperty() { return activePage.getReadOnlyProperty(); }
    public ReadOnlyStringProperty applicationNameProperty() { return applicationName.getReadOnlyProperty(); }
    public WorkspacePage activePage() { return activePage.get(); }
    public WorkspaceSnapshot snapshot() { return snapshot; }
    public Node activeNode() {
        WorkspaceContent active = contents.get(snapshot.selectedTabId());
        return active == null ? null : active.node();
    }

    /** Navigation-rail behavior: focus an existing page or open a new tab. */
    public void openOrSelect(WorkspacePage page) {
        WorkspaceTab existing = snapshot.tabs().stream().filter(tab -> tab.page() == page).findFirst().orElse(null);
        if (existing != null) select(existing.id());
        else open(page);
    }

    public void open(WorkspacePage page) {
        WorkspaceTab descriptor = new WorkspaceTab(UUID.randomUUID(), page, pageTitle(page), WorkspaceStyle.DEFAULT);
        List<WorkspaceTab> optimisticTabs = new ArrayList<>(snapshot.tabs());
        optimisticTabs.add(descriptor);
        applySnapshot(new WorkspaceSnapshot(snapshot.applicationName(), optimisticTabs, descriptor.id(),
                snapshot.groups()));
        requestActiveContentFocus();
        onFx(store.open(descriptor), this::applySnapshot, error -> {
            reportSaveFailure(error);
            onFx(store.load(), this::applySnapshot, this::reportSaveFailure);
        });
    }

    public void applyGlobalSearch(SearchSpec spec) {
        stripSearch.setSearchSpec(spec == null ? SearchSpec.empty() : spec);
    }

    private void buildChrome() {
        newTabButton.setGraphic(Icons.of("add", 18));
        newTabButton.getStyleClass().add("workspace-menu-button");
        newTabButton.setAccessibleText(i18n.text("workspace.new_tab"));
        for (WorkspacePage page : WorkspacePage.values()) {
            MenuItem item = new MenuItem(pageTitle(page), Icons.of(iconFor(page), 16));
            item.setOnAction(event -> open(page));
            newTabButton.getItems().add(item);
        }

        overflow.setGraphic(Icons.of("more", 18));
        overflow.getStyleClass().add("workspace-menu-button");
        overflow.setAccessibleText(i18n.text("workspace.overflow"));
        groupsButton.setGraphic(Icons.of("folder", 18));
        groupsButton.getStyleClass().add("workspace-menu-button");
        groupsButton.setAccessibleText(i18n.text("workspace.groups"));
        groupsButton.setOnShowing(event -> rebuildGroupMenu());
        bulkButton.setGraphic(Icons.of("close", 18));
        bulkButton.getStyleClass().add("workspace-menu-button");
        bulkButton.setAccessibleText(i18n.text("workspace.bulk_close"));
        MenuItem containing = new MenuItem(i18n.text("workspace.close_containing"));
        containing.setOnAction(event -> showBulkClose(bulkButton, false));
        MenuItem inverse = new MenuItem(i18n.text("workspace.close_not_containing"));
        inverse.setOnAction(event -> showBulkClose(bulkButton, true));
        bulkButton.getItems().setAll(containing, inverse);

        Label stripLabel = Mat.label(i18n.text("workspace.current_strip"), "caption");
        HBox toolbar = new HBox(8, stripLabel, stripSearch, masterSearch, Mat.hSpacer(),
                newTabButton, groupsButton, bulkButton, overflow);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("workspace-toolbar");

        pinnedStrip.getStyleClass().add("workspace-pinned-strip");
        pinnedStrip.setAlignment(Pos.CENTER_LEFT);
        pinnedStrip.setAccessibleRole(AccessibleRole.TAB_PANE);
        pinnedStrip.setAccessibleText(i18n.text("workspace.pinned_region"));
        pinnedScroll.setFitToHeight(true);
        pinnedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pinnedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pinnedScroll.setMaxWidth(320);
        pinnedScroll.getStyleClass().addAll("workspace-tab-scroll", "workspace-pinned-scroll");
        regularStrip.getStyleClass().add("workspace-regular-strip");
        regularStrip.setAlignment(Pos.CENTER_LEFT);
        regularStrip.setAccessibleRole(AccessibleRole.TAB_PANE);
        regularStrip.setAccessibleText(i18n.text("workspace.regular_region"));
        regularScroll.setFitToHeight(true);
        regularScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        regularScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        regularScroll.getStyleClass().add("workspace-tab-scroll");
        HBox.setHgrow(regularScroll, Priority.ALWAYS);
        HBox strips = new HBox(8, pinnedScroll, regularScroll);
        strips.setAlignment(Pos.CENTER_LEFT);
        strips.getStyleClass().add("workspace-strips");

        VBox top = new VBox(toolbar, strips);
        top.getStyleClass().add("workspace-chrome");
        setTop(top);
        contentHost.getStyleClass().add("workspace-content-host");
        setCenter(contentHost);
    }

    private void applySnapshot(WorkspaceSnapshot next) {
        if (disposed) return;
        snapshot = next.tabs().isEmpty() ? WorkspaceSnapshot.fresh() : next;
        applicationName.set(snapshot.applicationName());
        Set<UUID> liveIds = snapshot.tabs().stream().map(WorkspaceTab::id).collect(java.util.stream.Collectors.toSet());
        List<UUID> removed = contents.keySet().stream().filter(id -> !liveIds.contains(id)).toList();
        for (UUID id : removed) {
            WorkspaceContent content = contents.remove(id);
            if (content != null) content.dispose().run();
        }
        for (WorkspaceTab tab : snapshot.tabs()) contents.computeIfAbsent(tab.id(), ignored -> factory.create(tab));
        rebuildStrip();
        selectLocally(snapshot.selectedTabId());
    }

    private void rebuildStrip() {
        if (disposed) return;
        pinnedStrip.getChildren().clear();
        regularStrip.getChildren().clear();
        tabButtons.clear();
        SearchSpec stripSpec = stripSearch.searchSpec();

        List<WorkspaceTab> pinned = snapshot.tabs().stream().filter(tab -> tab.pinned()
                || (tab.groupId() != null && snapshot.group(tab.groupId()) != null
                    && snapshot.group(tab.groupId()).pinned())).toList();
        for (WorkspaceTab tab : pinned) if (matches(stripSearch, stripSpec, tab.title())) {
            pinnedStrip.getChildren().add(tabButton(tab, true));
        }
        pinnedScroll.setVisible(!pinnedStrip.getChildren().isEmpty());
        pinnedScroll.setManaged(!pinnedStrip.getChildren().isEmpty());

        List<WorkspaceTab> ungrouped = snapshot.tabs().stream()
                .filter(tab -> !pinned.contains(tab) && tab.groupId() == null).toList();
        if (!ungrouped.isEmpty()) {
            HBox tabs = new HBox(4);
            tabs.getStyleClass().add("workspace-ungrouped-tabs");
            for (WorkspaceTab tab : ungrouped) if (matches(stripSearch, stripSpec, tab.title())) {
                tabs.getChildren().add(tabButton(tab, false));
            }
            if (!tabs.getChildren().isEmpty()) regularStrip.getChildren().add(tabs);
        }
        for (WorkspaceGroup group : snapshot.groups()) {
            if (group.pinned()) continue;
            Node segment = groupSegment(group, stripSpec);
            if (segment != null) regularStrip.getChildren().add(segment);
        }
        rebuildOverflowMenu();
    }

    private Node groupSegment(WorkspaceGroup group, SearchSpec stripSpec) {
        List<WorkspaceTab> groupTabs = snapshot.tabs().stream()
                .filter(tab -> !tab.pinned() && group.id().equals(tab.groupId())).toList();
        SearchField localSearch = groupSearches.computeIfAbsent(group.id(), id -> {
            SearchField field = new SearchField(i18n, "workspace.search.group");
            field.setMaxWidth(180);
            field.searchSpecProperty().addListener((observable, previous, current) -> rebuildStrip());
            return field;
        });
        HBox header = new HBox(6);
        header.getStyleClass().add("workspace-group-header");
        Label label = Mat.label((group.icon().isBlank() ? "" : group.icon() + " ") + group.name(),
                "workspace-group-name");
        label.setStyle("-fx-text-fill: " + group.color() + ";");
        Button collapse = new Button(group.collapsed() ? "▸" : "▾");
        collapse.getStyleClass().add("workspace-group-toggle");
        collapse.setAccessibleText(i18n.text(group.collapsed() ? "workspace.expand_group" : "workspace.collapse_group"));
        collapse.setOnAction(event -> updateGroup(group.withCollapsed(!group.collapsed())));
        header.getChildren().setAll(collapse, label, localSearch);
        installGroupContextMenu(header, group);

        HBox tabs = new HBox(4);
        tabs.getStyleClass().add("workspace-group-tabs");
        if (!group.collapsed()) {
            for (WorkspaceTab tab : groupTabs) {
                if (matches(stripSearch, stripSpec, tab.title())
                        && matches(localSearch, localSearch.searchSpec(), tab.title())) {
                    tabs.getChildren().add(tabButton(tab, false));
                }
            }
        }
        VBox segment = new VBox(2, header, tabs);
        segment.getStyleClass().add("workspace-group-segment");
        boolean visible = !group.collapsed() ? !tabs.getChildren().isEmpty() : matches(stripSearch, stripSpec, group.name());
        return visible ? segment : null;
    }

    private ToggleButton tabButton(WorkspaceTab tab, boolean pinnedRegion) {
        ToggleButton button = new ToggleButton(pinnedRegion ? "" : tab.title(), Icons.of(iconFor(tab.page()), 16));
        button.getStyleClass().addAll("workspace-tab", pinnedRegion ? "pinned-tab" : "regular-tab");
        button.setAccessibleRole(AccessibleRole.TAB_ITEM);
        button.setAccessibleText(tab.title() + (isPinned(tab) ? ", " + i18n.text("workspace.pinned") : ""));
        button.setAccessibleHelp(i18n.text("workspace.tab_help"));
        button.setFocusTraversable(true);
        button.setSelected(tab.id().equals(snapshot.selectedTabId()));
        button.setOnAction(event -> select(tab.id()));
        button.setOnKeyPressed(event -> handleTabKey(event, tab));
        installTabContextMenu(button, tab);
        installDragReorder(button, tab);
        tabButtons.put(tab.id(), button);
        return button;
    }

    private void handleTabKey(KeyEvent event, WorkspaceTab tab) {
        if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
            select(tab.id()); event.consume(); return;
        }
        if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.P) {
            onFx(store.setPinned(tab.id(), !tab.pinned()), this::applySnapshot, this::reportSaveFailure);
            event.consume(); return;
        }
        if (event.isControlDown() && event.isShiftDown()
                && (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT)) {
            int current = snapshot.tabs().indexOf(tab);
            int next = event.getCode() == KeyCode.LEFT ? current - 1 : current + 1;
            onFx(store.moveTab(tab.id(), next), this::applySnapshot, this::reportSaveFailure);
            event.consume(); return;
        }
        if (event.getCode() == KeyCode.LEFT || event.getCode() == KeyCode.RIGHT
                || event.getCode() == KeyCode.HOME || event.getCode() == KeyCode.END) {
            List<ToggleButton> buttons = new ArrayList<>(tabButtons.values());
            int current = buttons.indexOf(event.getSource());
            int next = switch (event.getCode()) {
                case LEFT -> Math.max(0, current - 1);
                case RIGHT -> Math.min(buttons.size() - 1, current + 1);
                case HOME -> 0;
                case END -> buttons.size() - 1;
                default -> current;
            };
            if (!buttons.isEmpty()) buttons.get(next).requestFocus();
            event.consume();
        }
    }

    private void installDragReorder(ToggleButton button, WorkspaceTab tab) {
        button.setOnDragDetected(event -> {
            Dragboard drag = button.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(tab.id().toString());
            drag.setContent(content);
            event.consume();
        });
        button.setOnDragOver(event -> {
            if (event.getGestureSource() != button && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        button.setOnDragDropped(event -> {
            try {
                UUID source = UUID.fromString(event.getDragboard().getString());
                int index = snapshot.tabs().indexOf(tab);
                onFx(store.moveToGroup(source, tab.groupId(), index), this::applySnapshot, this::reportSaveFailure);
                event.setDropCompleted(true);
            } catch (RuntimeException invalid) {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private void installTabContextMenu(ToggleButton button, WorkspaceTab tab) {
        ContextMenu menu = new ContextMenu();
        MenuItem pin = new MenuItem(i18n.text(tab.pinned() ? "workspace.unpin" : "workspace.pin"));
        pin.setOnAction(event -> onFx(store.setPinned(tab.id(), !tab.pinned()), this::applySnapshot,
                this::reportSaveFailure));
        Menu move = new Menu(i18n.text("workspace.move_to_group"));
        MenuItem ungrouped = new MenuItem(i18n.text("workspace.ungrouped"));
        ungrouped.setOnAction(event -> moveToGroup(tab, null));
        move.getItems().add(ungrouped);
        for (WorkspaceGroup group : snapshot.groups()) {
            MenuItem target = new MenuItem(group.name());
            target.setOnAction(event -> moveToGroup(tab, group.id()));
            move.getItems().add(target);
        }
        MenuItem close = new MenuItem(i18n.text("workspace.close_tab"));
        close.setOnAction(event -> closeWithGuard(List.of(tab.id())));
        MenuItem closeOthers = new MenuItem(i18n.text("workspace.close_others"));
        closeOthers.setOnAction(event -> closeWithGuard(snapshot.tabs().stream()
                .filter(candidate -> !candidate.id().equals(tab.id()) && !isPinned(candidate))
                .map(WorkspaceTab::id).toList()));
        MenuItem closeLeft = new MenuItem(i18n.text("workspace.close_left"));
        closeLeft.setOnAction(event -> closeWithGuard(tabsToEdge(tab, true)));
        MenuItem closeRight = new MenuItem(i18n.text("workspace.close_right"));
        closeRight.setOnAction(event -> closeWithGuard(tabsToEdge(tab, false)));
        MenuItem appearance = new MenuItem(i18n.text("workspace.edit_tab_appearance"));
        appearance.getProperties().put(AppearanceRegistry.TARGET_PROPERTY, Boolean.TRUE);
        appearance.setOnAction(event -> AppearanceRegistry.openEditorFor(button));
        menu.getItems().setAll(pin, move, new SeparatorMenuItem(), close, closeOthers, closeLeft, closeRight,
                new SeparatorMenuItem(), appearance);
        button.setContextMenu(menu);
    }

    private List<UUID> tabsToEdge(WorkspaceTab anchor, boolean left) {
        int index = snapshot.tabs().indexOf(anchor);
        if (index < 0) return List.of();
        return snapshot.tabs().stream().filter(tab -> !isPinned(tab))
                .filter(tab -> left ? snapshot.tabs().indexOf(tab) < index : snapshot.tabs().indexOf(tab) > index)
                .map(WorkspaceTab::id).toList();
    }

    private void installGroupContextMenu(Node target, WorkspaceGroup group) {
        ContextMenu menu = new ContextMenu();
        MenuItem rename = new MenuItem(i18n.text("workspace.rename_group"));
        rename.setOnAction(event -> renameGroup(group));
        MenuItem color = new MenuItem(i18n.text("workspace.group_color"));
        color.setOnAction(event -> editGroupColor(target, group));
        MenuItem collapse = new MenuItem(i18n.text(group.collapsed() ? "workspace.expand_group" : "workspace.collapse_group"));
        collapse.setOnAction(event -> updateGroup(group.withCollapsed(!group.collapsed())));
        MenuItem pin = new MenuItem(i18n.text(group.pinned() ? "workspace.unpin_group" : "workspace.pin_group"));
        pin.setOnAction(event -> updateGroup(group.withPinned(!group.pinned())));
        MenuItem moveLeft = new MenuItem(i18n.text("workspace.move_group_left"));
        moveLeft.setOnAction(event -> moveGroup(group, -1));
        MenuItem moveRight = new MenuItem(i18n.text("workspace.move_group_right"));
        moveRight.setOnAction(event -> moveGroup(group, 1));
        MenuItem remove = new MenuItem(i18n.text("workspace.remove_group"));
        remove.setOnAction(event -> onFx(store.removeGroup(group.id()), this::applySnapshot, this::reportSaveFailure));
        MenuItem appearance = new MenuItem(i18n.text("workspace.edit_group_appearance"));
        appearance.getProperties().put(AppearanceRegistry.TARGET_PROPERTY, Boolean.TRUE);
        appearance.setOnAction(event -> AppearanceRegistry.openEditorFor(target));
        menu.getItems().setAll(rename, color, collapse, pin, moveLeft, moveRight,
                new SeparatorMenuItem(), remove, appearance);
        AppearanceRegistry.installContextMenu(target, menu);
        target.setOnContextMenuRequested(event -> {
            menu.show(target, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void renameGroup(WorkspaceGroup group) {
        M3Dialogs.prompt(this, i18n.text("workspace.rename_group"),
                i18n.text("workspace.rename_group_help"), group.name(),
                i18n.text("stock.remote.confirm_cancel"), i18n.text("workspace.apply"))
                .ifPresent(name -> updateGroup(group.withName(name)));
    }

    private void editGroupColor(Node anchor, WorkspaceGroup group) {
        ColorPicker picker = new ColorPicker(Color.web(group.color()));
        picker.setAccessibleText(i18n.text("workspace.group_color"));
        Button apply = Mat.filled(i18n.text("workspace.apply"), "check");
        ContextMenu popover = new ContextMenu();
        apply.setOnAction(event -> {
            updateGroup(group.withColor(toHex(picker.getValue())));
            popover.hide();
        });
        VBox box = new VBox(8, Mat.label(i18n.text("workspace.group_color"), "subtitle"), picker, apply);
        box.setPadding(new Insets(12));
        popover.getItems().add(new CustomMenuItem(box, false));
        popover.show(anchor, Side.BOTTOM, 0, 4);
    }

    private void moveGroup(WorkspaceGroup group, int delta) {
        int index = snapshot.groups().indexOf(group);
        onFx(store.moveGroup(group.id(), index + delta), this::applySnapshot, this::reportSaveFailure);
    }

    private void updateGroup(WorkspaceGroup group) {
        onFx(store.updateGroup(group), this::applySnapshot, this::reportSaveFailure);
    }

    private void moveToGroup(WorkspaceTab tab, UUID groupId) {
        onFx(store.moveToGroup(tab.id(), groupId, snapshot.tabs().indexOf(tab)), this::applySnapshot,
                this::reportSaveFailure);
    }

    private void rebuildGroupMenu() {
        if (groupsButton == null) return;
        groupsButton.getItems().clear();
        VBox searchBox = new VBox(4, Mat.label(i18n.text("workspace.search.groups"), "caption"), groupNameSearch);
        searchBox.setPadding(new Insets(8));
        groupsButton.getItems().add(new CustomMenuItem(searchBox, false));
        MenuItem create = new MenuItem(i18n.text("workspace.create_group"));
        create.setOnAction(event -> createGroup());
        groupsButton.getItems().addAll(create, new SeparatorMenuItem());
        for (WorkspaceGroup group : snapshot.groups()) {
            if (!matches(groupNameSearch, groupNameSearch.searchSpec(), group.name())) continue;
            CheckMenuItem selected = new CheckMenuItem(group.name());
            selected.setSelected(selectedGroups.contains(group.id()));
            selected.setOnAction(event -> {
                if (selected.isSelected()) selectedGroups.add(group.id()); else selectedGroups.remove(group.id());
            });
            groupsButton.getItems().add(selected);
        }
    }

    private void createGroup() {
        M3Dialogs.prompt(this, i18n.text("workspace.create_group"),
                i18n.text("workspace.create_group_help"), i18n.text("workspace.default_group"),
                i18n.text("stock.remote.confirm_cancel"), i18n.text("workspace.apply"))
                .ifPresent(name -> onFx(store.createGroup(WorkspaceGroup.create(name)),
                        this::applySnapshot, this::reportSaveFailure));
    }

    private void rebuildOverflowMenu() {
        overflow.getItems().clear();
        for (WorkspaceTab tab : snapshot.tabs()) {
            WorkspaceGroup group = snapshot.group(tab.groupId());
            String metadata = (group == null ? i18n.text("workspace.ungrouped") : group.name())
                    + " · " + (isPinned(tab) ? i18n.text("workspace.pinned") : i18n.text("workspace.regular"));
            MenuItem item = new MenuItem(tab.title() + " — " + metadata, Icons.of(iconFor(tab.page()), 16));
            item.setOnAction(event -> select(tab.id()));
            overflow.getItems().add(item);
        }
    }

    private void refreshMasterResults() {
        if (!masterSearch.input().isFocused()) return;
        ContextMenu results = masterSearch.input().getProperties().get("master-results") instanceof ContextMenu existing
                ? existing : new ContextMenu();
        masterSearch.input().getProperties().put("master-results", results);
        results.getItems().clear();
        SearchSpec spec = masterSearch.searchSpec();
        if (!spec.expression().isBlank() && masterSearch.validation().valid()) {
            for (WorkspaceTab tab : snapshot.tabs()) {
                if (!matches(masterSearch, spec, tab.title())) continue;
                WorkspaceGroup group = snapshot.group(tab.groupId());
                String location = i18n.text("workspace.master_location", "Main", "Primary",
                        group == null ? i18n.text("workspace.ungrouped") : group.name(),
                        isPinned(tab) ? i18n.text("workspace.pinned") : i18n.text("workspace.regular"));
                Menu item = new Menu(tab.title() + " — " + location, Icons.of(iconFor(tab.page()), 16));
                MenuItem activate = new MenuItem(i18n.text("workspace.open_page", tab.title()));
                activate.setOnAction(event -> select(tab.id()));
                MenuItem pin = new MenuItem(i18n.text(tab.pinned() ? "workspace.unpin" : "workspace.pin"));
                pin.setOnAction(event -> onFx(store.setPinned(tab.id(), !tab.pinned()),
                        this::applySnapshot, this::reportSaveFailure));
                Menu move = new Menu(i18n.text("workspace.move_to_group"));
                MenuItem ungrouped = new MenuItem(i18n.text("workspace.ungrouped"));
                ungrouped.setOnAction(event -> moveToGroup(tab, null));
                move.getItems().add(ungrouped);
                for (WorkspaceGroup candidate : snapshot.groups()) {
                    MenuItem groupTarget = new MenuItem(candidate.name());
                    groupTarget.setOnAction(event -> moveToGroup(tab, candidate.id()));
                    move.getItems().add(groupTarget);
                }
                MenuItem close = new MenuItem(i18n.text("workspace.close_tab"));
                close.setOnAction(event -> closeWithGuard(List.of(tab.id())));
                item.getItems().setAll(activate, pin, move, new SeparatorMenuItem(), close);
                results.getItems().add(item);
            }
        }
        if (results.getItems().isEmpty()) results.hide();
        else if (!results.isShowing()) results.show(masterSearch.input(), Side.BOTTOM, 0, 2);
    }

    private void showBulkClose(Node anchor, boolean inverse) {
        SearchField query = new SearchField(i18n, inverse ? "workspace.close_not_containing" : "workspace.close_containing");
        CheckBox includePinned = new CheckBox(i18n.text("workspace.include_pinned"));
        ComboBox<BulkScope> scope = new ComboBox<>();
        scope.getItems().setAll(BulkScope.values());
        scope.setValue(BulkScope.CURRENT_GROUP);
        scope.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(BulkScope value) { return value == null ? "" : i18n.text(value.key); }
            @Override public BulkScope fromString(String value) { return scope.getValue(); }
        });
        Label preview = Mat.label(i18n.text("workspace.bulk_empty"), "row-desc");
        Button close = Mat.filled(i18n.text("workspace.review_close"), "close");
        close.setDisable(true);
        Runnable update = () -> {
            List<WorkspaceTab> candidates = bulkCandidates(query.searchSpec(), inverse, scope.getValue(),
                    includePinned.isSelected());
            boolean ready = !query.searchSpec().expression().isBlank() && query.validation().valid()
                    && !candidates.isEmpty();
            close.setDisable(!ready);
            preview.setText(query.searchSpec().expression().isBlank() ? i18n.text("workspace.bulk_empty")
                    : !query.validation().valid() ? i18n.text("workspace.bulk_invalid")
                    : i18n.text("workspace.bulk_preview", candidates.size(),
                        query.searchSpec().mode().name(), candidates.stream().map(WorkspaceTab::title).limit(8)
                                .collect(java.util.stream.Collectors.joining(", "))));
        };
        query.searchSpecProperty().addListener((observable, previous, current) -> update.run());
        includePinned.selectedProperty().addListener((observable, previous, current) -> update.run());
        scope.valueProperty().addListener((observable, previous, current) -> update.run());
        ContextMenu popover = new ContextMenu();
        close.setOnAction(event -> {
            List<WorkspaceTab> candidates = bulkCandidates(query.searchSpec(), inverse, scope.getValue(),
                    includePinned.isSelected());
            List<WorkspaceTab> protectedTabs = candidates.stream().filter(tab -> {
                WorkspaceContent content = contents.get(tab.id());
                return content != null && content.hasUnsavedWork().getAsBoolean();
            }).toList();
            List<UUID> closable = candidates.stream().filter(tab -> !protectedTabs.contains(tab))
                    .map(WorkspaceTab::id).toList();
            if (closable.isEmpty()) {
                notifications.warning(i18n.text("workspace.close_blocked_title"),
                        i18n.text("workspace.close_blocked_body", protectedTabs.size()));
                return;
            }
            boolean confirmed = M3Dialogs.confirm(this,
                    i18n.text("workspace.confirm_close_title"),
                    i18n.text("workspace.confirm_close_header",
                            inverse ? i18n.text("workspace.close_not_containing")
                                    : i18n.text("workspace.close_containing")),
                    i18n.text("workspace.confirm_close_body", closable.size(), protectedTabs.size()),
                    i18n.text("stock.remote.confirm_cancel"), i18n.text("workspace.review_close"));
            if (confirmed) {
                closeWithGuard(closable);
                popover.hide();
            }
        });
        VBox pane = new VBox(8, Mat.label(inverse ? i18n.text("workspace.close_not_containing")
                        : i18n.text("workspace.close_containing"), "subtitle"),
                query, scope, includePinned, preview, close);
        pane.getStyleClass().add("bulk-close-popover");
        pane.setPadding(new Insets(12));
        pane.setPrefWidth(460);
        popover.getItems().add(new CustomMenuItem(pane, false));
        popover.setOnHidden(event -> query.dispose());
        update.run();
        popover.show(anchor, Side.BOTTOM, 0, 4);
        Platform.runLater(query.input()::requestFocus);
    }

    private List<WorkspaceTab> bulkCandidates(SearchSpec spec, boolean inverse, BulkScope scope,
                                               boolean includePinned) {
        WorkspaceTab selected = snapshot.tab(snapshot.selectedTabId());
        UUID currentGroup = selected == null ? null : selected.groupId();
        return snapshot.tabs().stream()
                .filter(tab -> includePinned || !isPinned(tab))
                .filter(tab -> switch (scope == null ? BulkScope.CURRENT_GROUP : scope) {
                    case CURRENT_GROUP -> Objects.equals(currentGroup, tab.groupId());
                    case SELECTED_GROUPS -> selectedGroups.contains(tab.groupId());
                    case ALL_GROUPS -> true;
                })
                .filter(tab -> {
                    boolean matched = spec.expression().isBlank()
                            || (stripSearch.evaluator().validate(spec).valid()
                            && stripSearch.evaluator().matches(spec, tab.title()));
                    return inverse ? !matched : matched;
                }).toList();
    }

    private boolean isPinned(WorkspaceTab tab) {
        WorkspaceGroup group = snapshot.group(tab.groupId());
        return tab.pinned() || (group != null && group.pinned());
    }

    private void closeWithGuard(Collection<UUID> requested) {
        List<UUID> ids = requested.stream().distinct().filter(id -> {
            WorkspaceContent content = contents.get(id);
            return content == null || !content.hasUnsavedWork().getAsBoolean();
        }).toList();
        int blocked = (int) requested.stream().distinct().count() - ids.size();
        if (blocked > 0) notifications.warning(i18n.text("workspace.close_blocked_title"),
                i18n.text("workspace.close_blocked_body", blocked));
        if (!ids.isEmpty()) onFx(store.closeTabs(ids), this::applySnapshot, this::reportSaveFailure);
    }

    private void select(UUID id) {
        selectLocally(id);
        requestActiveContentFocus();
        onFx(store.select(id), ignored -> { }, this::reportSaveFailure);
    }

    private void selectLocally(UUID id) {
        WorkspaceTab selected = snapshot.tab(id);
        WorkspaceContent content = contents.get(id);
        if (selected == null || content == null) return;
        snapshot = snapshot.withSelectedTab(id);
        activePage.set(selected.page());
        contentHost.getChildren().setAll(content.node());
        tabButtons.forEach((tabId, button) -> button.setSelected(tabId.equals(id)));
        applyActiveSearch(stripSearch.searchSpec());
    }

    private void requestActiveContentFocus() {
        WorkspaceContent active = contents.get(snapshot.selectedTabId());
        if (active != null) Platform.runLater(active.node()::requestFocus);
    }

    private void applyActiveSearch(SearchSpec spec) {
        WorkspaceContent active = contents.get(snapshot.selectedTabId());
        if (active != null) active.search().accept(spec == null ? SearchSpec.empty() : spec);
    }

    private boolean matches(SearchField field, SearchSpec spec, String value) {
        return spec.expression().isBlank() || (field.validation().valid() && field.evaluator().matches(spec, value));
    }

    private void reportSaveFailure(Throwable error) {
        notifications.error(i18n.text("workspace.failed.title"),
                i18n.text("workspace.status.save_failed", message(error)));
    }

    private <T> void onFx(CompletableFuture<T> future, Consumer<T> success, Consumer<Throwable> failure) {
        future.whenComplete((value, error) -> Platform.runLater(() -> {
            if (error == null) success.accept(value); else failure.accept(unwrap(error));
        }));
    }

    private String pageTitle(WorkspacePage page) { return i18n.text(pageKeys.get(page)); }

    private void configurePageKeys() {
        pageKeys.put(WorkspacePage.DOWNLOADS, "nav.downloads");
        pageKeys.put(WorkspacePage.LINKGRABBER, "nav.linkgrabber");
        pageKeys.put(WorkspacePage.HISTORY, "nav.history");
        pageKeys.put(WorkspacePage.SETTINGS, "nav.settings");
        pageKeys.put(WorkspacePage.ADD_LINKS, "addlinks.title");
        pageKeys.put(WorkspacePage.ACCOUNTS, "stock.accounts");
        pageKeys.put(WorkspacePage.PLUGINS, "stock.plugins");
        pageKeys.put(WorkspacePage.CAPTCHA, "stock.captcha");
        pageKeys.put(WorkspacePage.EXTRACTION, "stock.extraction");
        pageKeys.put(WorkspacePage.SCHEDULER, "stock.scheduler");
        pageKeys.put(WorkspacePage.CONNECTIONS, "stock.connections");
        pageKeys.put(WorkspacePage.REMOTE_CONTROL, "stock.remote");
        pageKeys.put(WorkspacePage.AUTOMATION, "stock.automation");
        pageKeys.put(WorkspacePage.LOGS, "stock.logs");
        pageKeys.put(WorkspacePage.NOTIFICATIONS, "notifications.title");
        pageKeys.put(WorkspacePage.CHANGELOG, "changelog.title");
    }

    private static String iconFor(WorkspacePage page) {
        return switch (page) {
            case DOWNLOADS -> "download";
            case LINKGRABBER -> "link";
            case HISTORY, CHANGELOG -> "history";
            case SETTINGS -> "settings";
            case ADD_LINKS -> "add";
            case ACCOUNTS -> "account";
            case PLUGINS -> "extension";
            case CAPTCHA -> "shield";
            case EXTRACTION -> "folder";
            case SCHEDULER, AUTOMATION -> "clock";
            case CONNECTIONS, REMOTE_CONTROL -> "reconnect";
            case LOGS -> "info";
            case NOTIFICATIONS -> "notification";
        };
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X%02X", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255),
                Math.round(color.getOpacity() * 255));
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) current = current.getCause();
        return current;
    }

    private static String message(Throwable error) {
        Throwable unwrapped = unwrap(error);
        return unwrapped.getMessage() == null || unwrapped.getMessage().isBlank()
                ? unwrapped.getClass().getSimpleName() : unwrapped.getMessage();
    }

    @Override public void close() {
        if (disposed) return;
        disposed = true;
        stripSearch.dispose();
        masterSearch.dispose();
        groupNameSearch.dispose();
        groupSearches.values().forEach(SearchField::dispose);
        groupSearches.clear();
        contents.values().forEach(content -> content.dispose().run());
        contents.clear();
        store.close();
    }
}
