package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.ActivityStatus;
import org.jdownloader.material.ui.component.ClipboardMonitor;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.view.AddLinksView;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.HistoryView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.SettingsView;
import org.jdownloader.material.util.Formats;
import org.jdownloader.material.workspace.GitWorkspaceStore;
import org.jdownloader.material.workspace.WorkspacePage;
import org.jdownloader.material.workspace.WorkspaceSnapshot;
import org.jdownloader.material.workspace.WorkspaceStyle;
import org.jdownloader.material.workspace.WorkspaceTab;

/**
 * Assembles the application shell around a durable browser-style workspace.
 * Static labels rebuild when the language changes; tab descriptors live in a
 * private local Git repository, so no view node is the source of truth.
 */
public final class MainWindow extends StackPane {

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final Stage stage;
    private final I18n i18n;
    private final ActivityStatus activity = new ActivityStatus();
    private final ClipboardMonitor clipboardMonitor;
    private final GitWorkspaceStore workspace;
    private final StringProperty applicationName = new SimpleStringProperty(this, "applicationName", "JDownloader Material");
    private final List<Runnable> shellDisposers = new ArrayList<>();
    private final Map<UUID, ViewBundle> viewsByTab = new LinkedHashMap<>();
    private final Map<UUID, Tab> tabsById = new LinkedHashMap<>();
    private final Map<WorkspacePage, ToggleButton> navTabs = new HashMap<>();
    private final ChangeListener<LanguageMode> languageListener = (observable, previous, current) -> rebuildShell();

    private WorkspaceSnapshot workspaceSnapshot = WorkspaceSnapshot.fresh();
    private TabPane workspaceTabs;
    private ToggleGroup navGroup;
    private boolean applyingWorkspace;
    private boolean workspaceTouchedBeforeLoad;
    private boolean disposed;
    private double dragOffsetX;
    private double dragOffsetY;

    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage) {
        this(engine, theme, stage, Path.of(System.getProperty("user.home", "."), ".jdownloader-material", "workspace"));
    }

    /** Supplies an isolated workspace location for documentation capture and tests. */
    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage, Path workspaceRoot) {
        this.engine = engine;
        this.theme = theme;
        this.stage = stage;
        this.workspace = new GitWorkspaceStore(workspaceRoot);
        this.i18n = new I18n(engine.settings().languageProperty());
        setFocusTraversable(true);

        i18n.modeProperty().addListener(languageListener);
        rebuildShell();
        loadWorkspace();

        // Clipboard capture reports through the fixed status bar, never a
        // floating notification or forced page switch.
        clipboardMonitor = new ClipboardMonitor(engine, activity, i18n);
        clipboardMonitor.start();
    }

    /** The saved display name used by both the app bar and native stage title. */
    public StringProperty applicationNameProperty() {
        return applicationName;
    }

    private void loadWorkspace() {
        onFx(workspace.load(), snapshot -> {
            if (workspaceTouchedBeforeLoad) return;
            applyWorkspaceSnapshot(snapshot);
            activity.info(i18n.text("workspace.status.ready"));
        }, error -> activity.error(i18n.text("workspace.status.load_failed", message(error))));
    }

    private void rebuildShell() {
        Map<UUID, AddLinksView.Draft> drafts = new HashMap<>();
        Map<UUID, String> settingsSections = new HashMap<>();
        for (Map.Entry<UUID, ViewBundle> entry : viewsByTab.entrySet()) {
            if (entry.getValue().addLinks != null) drafts.put(entry.getKey(), entry.getValue().addLinks.draft());
            if (entry.getValue().settings != null) settingsSections.put(entry.getKey(), entry.getValue().settings.selectedTabKey());
        }
        disposeShell();
        navGroup = new ToggleGroup();
        navTabs.clear();

        ToggleButton downloads = navItem("download", "nav.downloads", WorkspacePage.DOWNLOADS);
        ToggleButton linkGrabber = navItem("link", "nav.linkgrabber", WorkspacePage.LINKGRABBER);
        ToggleButton history = navItem("history", "nav.history", WorkspacePage.HISTORY);
        ToggleButton settings = navItem("settings", "nav.settings", WorkspacePage.SETTINGS);
        Region railSpacer = Mat.hSpacer();
        VBox.setVgrow(railSpacer, Priority.ALWAYS);
        VBox rail = new VBox(6, downloads, linkGrabber, history, railSpacer, settings);
        rail.getStyleClass().add("nav-rail");

        workspaceTabs = new TabPane();
        workspaceTabs.getStyleClass().add("workspace-tabs");
        workspaceTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        workspaceTabs.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            if (applyingWorkspace || selected == null || !(selected.getUserData() instanceof WorkspaceTab tab)) return;
            selectWorkspaceTab(tab.id());
        });
        VBox workspaceArea = new VBox(buildWorkspaceToolbar(), workspaceTabs);
        workspaceArea.getStyleClass().add("workspace-area");
        VBox.setVgrow(workspaceTabs, Priority.ALWAYS);

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTopAppBar());
        shell.setLeft(rail);
        shell.setCenter(workspaceArea);
        shell.setBottom(new StatusBar(engine, i18n, activity));
        getChildren().setAll(shell);
        rebuildTabs(drafts, settingsSections);
    }

    private HBox buildWorkspaceToolbar() {
        Label title = Mat.label(i18n.text("workspace.title"), "workspace-title");
        MFXButton newTab = Mat.text(i18n.text("workspace.new_tab"), "add");
        newTab.setOnAction(event -> showPagePicker(newTab));
        MFXButton tools = Mat.text(i18n.text("workspace.tools"), "settings");
        tools.setOnAction(event -> showWorkspaceTools(tools));
        HBox toolbar = new HBox(10, title, Mat.hSpacer(), newTab, tools);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("workspace-toolbar");
        return toolbar;
    }

    private void rebuildTabs(Map<UUID, AddLinksView.Draft> drafts, Map<UUID, String> settingsSections) {
        if (workspaceTabs == null) return;
        applyingWorkspace = true;
        try {
            workspaceTabs.getTabs().clear();
            tabsById.clear();
            for (WorkspaceTab descriptor : workspaceSnapshot.tabs()) {
                ViewBundle view = createView(descriptor, drafts.get(descriptor.id()), settingsSections.get(descriptor.id()));
                viewsByTab.put(descriptor.id(), view);
                StackPane page = new StackPane(view.node);
                page.getStyleClass().add("workspace-page");
                Label header = new Label(descriptor.title(), Icons.of(iconFor(descriptor.page()), 16));
                header.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                header.setGraphicTextGap(7);
                header.getStyleClass().add("workspace-tab-label");
                applyAppearance(descriptor, header, page);

                Tab tab = new Tab();
                tab.setGraphic(header);
                tab.setContent(page);
                tab.setClosable(true);
                tab.setUserData(descriptor);
                tab.setOnCloseRequest(event -> {
                    event.consume();
                    closeWorkspaceTab(descriptor.id());
                });
                header.setOnContextMenuRequested(event -> showTabEditor(event, descriptor, header, page, tab));
                workspaceTabs.getTabs().add(tab);
                tabsById.put(descriptor.id(), tab);
            }
            Tab selected = tabsById.get(workspaceSnapshot.selectedTabId());
            if (selected != null) workspaceTabs.getSelectionModel().select(selected);
            else if (!workspaceTabs.getTabs().isEmpty()) workspaceTabs.getSelectionModel().selectFirst();
        } finally {
            applyingWorkspace = false;
        }
        syncNavSelection();
    }

    private ViewBundle createView(WorkspaceTab descriptor, AddLinksView.Draft draft, String settingsSection) {
        return switch (descriptor.page()) {
            case DOWNLOADS -> {
                DownloadsView view = new DownloadsView(engine, activity, this::openAddLinks, i18n);
                yield new ViewBundle(view, view, null, null, null, null);
            }
            case LINKGRABBER -> {
                LinkGrabberView view = new LinkGrabberView(engine, activity, this::openAddLinks, i18n);
                yield new ViewBundle(view, null, view, null, null, null);
            }
            case HISTORY -> {
                HistoryView view = new HistoryView(engine.history(), i18n);
                yield new ViewBundle(view, null, null, view, null, null);
            }
            case SETTINGS -> {
                SettingsView view = new SettingsView(engine.settings(), i18n, settingsSection);
                yield new ViewBundle(view, null, null, null, view, null);
            }
            case ADD_LINKS -> {
                AddLinksView view = new AddLinksView(engine, this::showDownloads, this::showLinkGrabber, i18n);
                if (draft != null) view.restoreDraft(draft);
                yield new ViewBundle(view, null, null, null, null, view);
            }
        };
    }

    /** Stops timers and flushes the private workspace repository on application shutdown. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        clipboardMonitor.stop();
        i18n.modeProperty().removeListener(languageListener);
        disposeShell();
        workspace.close();
    }

    private void disposeShell() {
        disposeViews();
        shellDisposers.forEach(Runnable::run);
        shellDisposers.clear();
    }

    private void disposeViews() {
        for (ViewBundle view : viewsByTab.values()) view.dispose();
        viewsByTab.clear();
        tabsById.clear();
    }

    /** Moves focus off transient form controls before deterministic scene capture. */
    public void clearTransientFocus() {
        requestFocus();
    }

    /** Clears the fixed activity line before a deterministic documentation frame. */
    public void clearActivityStatus() {
        activity.clear();
    }

    /** Opens the inline Add Links composer programmatically (also used for demos/tests). */
    public void openAddLinks() {
        openOrSelect(WorkspacePage.ADD_LINKS);
    }

    /** Switches to LinkGrabber programmatically (also used for demos/tests). */
    public void showLinkGrabber() {
        openOrSelect(WorkspacePage.LINKGRABBER);
    }

    /** Switches to Downloads programmatically (also used for demos/tests). */
    public void showDownloads() {
        openOrSelect(WorkspacePage.DOWNLOADS);
    }

    /** Switches to the local, append-only history manager. */
    public void showHistory() {
        openOrSelect(WorkspacePage.HISTORY);
    }

    /** Shows the standard unselected Downloads view for documentation capture. */
    public void showDownloadsForCapture() {
        openOrSelect(WorkspacePage.DOWNLOADS);
        downloadsForCurrentOrFirst().clearSelectionForCapture();
    }

    /** Shows fixed status-bar feedback without placing a popup over content. */
    public void showDownloadsWithActivityForCapture() {
        showDownloadsForCapture();
        activity.info(i18n.text("status.activity_capture"));
    }

    /** Shows Downloads with an editable sample row selected for documentation capture. */
    public void showDownloadsWithEditableSelection() {
        openOrSelect(WorkspacePage.DOWNLOADS);
        downloadsForCurrentOrFirst().selectFirstEditableForCapture();
    }

    /** Switches to Settings programmatically (also used for demos/tests). */
    public void showSettings() {
        openOrSelect(WorkspacePage.SETTINGS);
    }

    /** Shows Appearance for a deterministic capture of the language picker. */
    public void showSettingsAppearanceForCapture() {
        openOrSelect(WorkspacePage.SETTINGS);
        settingsForCurrentOrFirst().showAppearanceForCapture();
    }

    /** Shows a tab strip with the editor open for documentation capture. */
    public void showTabEditorForCapture() {
        openOrSelect(WorkspacePage.DOWNLOADS);
        Tab tab = tabsById.get(workspaceSnapshot.selectedTabId());
        if (tab == null || !(tab.getUserData() instanceof WorkspaceTab descriptor)
                || !(tab.getGraphic() instanceof Label header) || !(tab.getContent() instanceof StackPane page)) return;
        ContextMenu menu = tabEditor(descriptor, header, page, tab);
        Platform.runLater(() -> menu.show(header, Side.BOTTOM, 0, 0));
    }

    private DownloadsView downloadsForCurrentOrFirst() {
        ViewBundle current = viewsByTab.get(workspaceSnapshot.selectedTabId());
        if (current != null && current.downloads != null) return current.downloads;
        return viewsByTab.values().stream().map(bundle -> bundle.downloads).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
    }

    private SettingsView settingsForCurrentOrFirst() {
        ViewBundle current = viewsByTab.get(workspaceSnapshot.selectedTabId());
        if (current != null && current.settings != null) return current.settings;
        return viewsByTab.values().stream().map(bundle -> bundle.settings).filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow();
    }

    // --------------------------------------------------------- Workspace tabs
    private void showPagePicker(Node anchor) {
        ContextMenu picker = new ContextMenu();
        for (WorkspacePage page : WorkspacePage.values()) {
            MenuItem item = new MenuItem(i18n.text("workspace.open_page", pageTitle(page)), Icons.of(iconFor(page), 16));
            item.setOnAction(event -> openOrSelect(page));
            picker.getItems().add(item);
        }
        picker.show(anchor, Side.BOTTOM, 0, 0);
    }

    private void openOrSelect(WorkspacePage page) {
        WorkspaceTab existing = workspaceSnapshot.tabs().stream()
                .filter(tab -> tab.page() == page).findFirst().orElse(null);
        if (existing != null) {
            selectWorkspaceTab(existing.id());
            return;
        }
        WorkspaceTab opened = new WorkspaceTab(UUID.randomUUID(), page, pageTitle(page), WorkspaceStyle.DEFAULT);
        List<WorkspaceTab> tabs = new ArrayList<>(workspaceSnapshot.tabs());
        tabs.add(opened);
        workspaceTouchedBeforeLoad = true;
        applyWorkspaceSnapshot(new WorkspaceSnapshot(workspaceSnapshot.applicationName(), tabs, opened.id()));
        activity.info(i18n.text("workspace.status.opening", opened.title()));
        onFx(workspace.open(opened), this::applyWorkspaceSnapshot,
                error -> refreshWorkspaceAfterFailure(error));
    }

    private void selectWorkspaceTab(UUID id) {
        Tab tab = tabsById.get(id);
        if (tab != null && workspaceTabs.getSelectionModel().getSelectedItem() != tab) {
            applyingWorkspace = true;
            try {
                workspaceTabs.getSelectionModel().select(tab);
            } finally {
                applyingWorkspace = false;
            }
        }
        if (workspaceSnapshot.tab(id) == null || id.equals(workspaceSnapshot.selectedTabId())) {
            syncNavSelection();
            return;
        }
        workspaceTouchedBeforeLoad = true;
        workspaceSnapshot = workspaceSnapshot.withSelectedTab(id);
        syncNavSelection();
        onFx(workspace.select(id), snapshot -> { }, error -> activity.error(i18n.text("workspace.status.save_failed", message(error))));
    }

    private void closeWorkspaceTab(UUID id) {
        activity.info(i18n.text("workspace.status.closing"));
        workspaceTouchedBeforeLoad = true;
        onFx(workspace.closeTab(id), snapshot -> {
            applyWorkspaceSnapshot(snapshot);
            activity.info(i18n.text("workspace.status.closed"));
        }, this::refreshWorkspaceAfterFailure);
    }

    private void applyWorkspaceSnapshot(WorkspaceSnapshot snapshot) {
        if (disposed) return;
        workspaceSnapshot = snapshot.tabs().isEmpty() ? WorkspaceSnapshot.fresh() : snapshot;
        applicationName.set(workspaceSnapshot.applicationName());
        Map<UUID, AddLinksView.Draft> drafts = new HashMap<>();
        Map<UUID, String> settingsSections = new HashMap<>();
        for (Map.Entry<UUID, ViewBundle> entry : viewsByTab.entrySet()) {
            if (entry.getValue().addLinks != null) drafts.put(entry.getKey(), entry.getValue().addLinks.draft());
            if (entry.getValue().settings != null) settingsSections.put(entry.getKey(), entry.getValue().settings.selectedTabKey());
        }
        disposeViews();
        rebuildTabs(drafts, settingsSections);
    }

    private void refreshWorkspaceAfterFailure(Throwable error) {
        activity.error(i18n.text("workspace.status.save_failed", message(error)));
        onFx(workspace.load(), this::applyWorkspaceSnapshot, ignored -> { });
    }

    private void syncNavSelection() {
        WorkspaceTab selected = workspaceSnapshot.tab(workspaceSnapshot.selectedTabId());
        ToggleButton nav = selected == null ? null : navTabs.get(selected.page());
        if (nav != null) nav.setSelected(true);
        else if (navGroup != null) navGroup.selectToggle(null);
    }

    private void showTabEditor(ContextMenuEvent event, WorkspaceTab descriptor, Label header, StackPane page, Tab tab) {
        ContextMenu menu = tabEditor(descriptor, header, page, tab);
        menu.show(header, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private ContextMenu tabEditor(WorkspaceTab descriptor, Label header, StackPane page, Tab tab) {
        WorkspaceStyle initial = descriptor.style();
        TextField name = new TextField(descriptor.title());
        name.setPromptText(i18n.text("workspace.tab_name"));
        ComboBox<String> family = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        family.setEditable(true);
        family.setValue(initial.fontFamily());
        TextField size = new TextField(formatFontSize(initial.fontSize()));
        size.setPrefColumnCount(5);
        Slider sizeSlider = new Slider(6, 96, initial.fontSize());
        sizeSlider.setPrefWidth(170);
        sizeSlider.setBlockIncrement(1);
        sizeSlider.valueProperty().addListener((observable, previous, value) -> size.setText(formatFontSize(value.doubleValue())));
        CheckBox bold = new CheckBox(i18n.text("workspace.bold"));
        bold.setSelected(initial.bold());
        CheckBox italic = new CheckBox(i18n.text("workspace.italic"));
        italic.setSelected(initial.italic());
        ColorPicker colorPicker = new ColorPicker(color(initial.color()));
        TextField hex = new TextField(initial.color());
        hex.setPrefColumnCount(10);
        colorPicker.valueProperty().addListener((observable, previous, value) -> hex.setText(hex(value)));

        Label status = Mat.label(i18n.text("workspace.tab_options"), "row-desc");
        ContextMenu menu = new ContextMenu();
        Runnable save = () -> {
            String rawColor = hex.getText() == null ? "" : hex.getText().trim();
            if (!rawColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                status.setText(i18n.text("workspace.status.invalid_color"));
                return;
            }
            double fontSize = parseFontSize(size.getText(), initial.fontSize());
            WorkspaceStyle style = new WorkspaceStyle(family.getEditor().getText(), fontSize,
                    bold.isSelected(), italic.isSelected(), rawColor);
            WorkspaceTab updated = descriptor.withTitle(name.getText()).withStyle(style);
            applyAppearance(updated, header, page);
            tab.setUserData(updated);
            workspaceTouchedBeforeLoad = true;
            onFx(workspace.update(updated), this::applyWorkspaceSnapshot,
                    error -> activity.error(i18n.text("workspace.status.save_failed", message(error))));
            activity.info(i18n.text("workspace.status.tab_saved"));
            menu.hide();
        };
        MFXButton saveButton = Mat.filled(i18n.text("workspace.save_tab"), "check");
        saveButton.setOnAction(event -> save.run());
        MFXButton reset = Mat.outlined(i18n.text("workspace.reset_style"), "undo");
        reset.setOnAction(event -> {
            family.setValue(WorkspaceStyle.DEFAULT.fontFamily());
            sizeSlider.setValue(WorkspaceStyle.DEFAULT.fontSize());
            bold.setSelected(false);
            italic.setSelected(false);
            hex.setText(WorkspaceStyle.DEFAULT.color());
            colorPicker.setValue(color(WorkspaceStyle.DEFAULT.color()));
        });
        MFXButton close = Mat.text(i18n.text("workspace.close_tab"), "close");
        close.setOnAction(event -> {
            menu.hide();
            closeWorkspaceTab(descriptor.id());
        });
        name.setOnAction(event -> save.run());

        VBox editor = new VBox(8,
                Mat.label(i18n.text("workspace.tab_options"), "subtitle"),
                editorRow(i18n.text("workspace.tab_name"), name),
                editorRow(i18n.text("workspace.font"), family),
                editorRow(i18n.text("workspace.font_size"), new HBox(8, sizeSlider, size)),
                new HBox(12, bold, italic),
                editorRow(i18n.text("workspace.color"), new HBox(8, colorPicker, hex)),
                new HBox(8, saveButton, reset, close), status);
        editor.setPadding(new Insets(12));
        editor.setPrefWidth(410);
        CustomMenuItem item = new CustomMenuItem(editor, false);
        item.setHideOnClick(false);
        menu.getItems().add(item);
        return menu;
    }

    private HBox editorRow(String label, Node control) {
        Label title = Mat.label(label, "row-title");
        HBox row = new HBox(12, title, Mat.hSpacer(), control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showWorkspaceTools(Node anchor) {
        ContextMenu menu = new ContextMenu();
        TextField appName = new TextField(applicationName.get());
        TextField tabsPath = new TextField(Path.of(System.getProperty("user.home", "."),
                "jdownloader-material-tabs.jdmtabs").toString());
        TextField importPath = new TextField();
        importPath.setPromptText(i18n.text("workspace.path_tabs"));
        TextField repositoryPath = new TextField(Path.of(System.getProperty("user.home", "."),
                "jdownloader-material-tabs-history.zip").toString());
        Label status = Mat.label(i18n.text("workspace.status.ready"), "row-desc");

        MFXButton saveName = Mat.filled(i18n.text("workspace.save_name"), "check");
        saveName.setOnAction(event -> renameApplication(appName.getText(), status));
        MFXButton exportTabs = Mat.outlined(i18n.text("workspace.export_tabs"), "download");
        exportTabs.setOnAction(event -> exportTabs(tabsPath, status));
        MFXButton importTabs = Mat.outlined(i18n.text("workspace.import_tabs"), "folder");
        importTabs.setOnAction(event -> importTabs(importPath, status, menu));
        MFXButton exportRepository = Mat.outlined(i18n.text("workspace.export_repository"), "history");
        exportRepository.setOnAction(event -> exportRepository(repositoryPath, status));

        VBox form = new VBox(9,
                Mat.label(i18n.text("workspace.tools"), "subtitle"),
                editorRow(i18n.text("workspace.app_name"), appName), saveName,
                Mat.label(i18n.text("workspace.export_tabs"), "row-title"), tabsPath, exportTabs,
                Mat.label(i18n.text("workspace.import_tabs"), "row-title"), importPath, importTabs,
                Mat.label(i18n.text("workspace.export_repository"), "row-title"), repositoryPath, exportRepository,
                status);
        form.setPadding(new Insets(12));
        form.setPrefWidth(500);
        CustomMenuItem item = new CustomMenuItem(form, false);
        item.setHideOnClick(false);
        menu.getItems().add(item);
        menu.show(anchor, Side.BOTTOM, 0, 0);
    }

    private void renameApplication(String name, Label status) {
        String normalized = WorkspaceSnapshot.normalizedApplicationName(name);
        applicationName.set(normalized);
        workspaceSnapshot = workspaceSnapshot.withApplicationName(normalized);
        workspaceTouchedBeforeLoad = true;
        status.setText(i18n.text("workspace.status.saving"));
        onFx(workspace.renameApplication(normalized), snapshot -> {
            applyWorkspaceSnapshot(snapshot);
            status.setText(i18n.text("workspace.status.name_saved"));
            activity.info(i18n.text("workspace.status.name_saved"));
        }, error -> {
            status.setText(i18n.text("workspace.status.save_failed", message(error)));
            refreshWorkspaceAfterFailure(error);
        });
    }

    private void exportTabs(TextField path, Label status) {
        Path output = path(path.getText(), status);
        if (output == null) return;
        status.setText(i18n.text("workspace.status.exporting"));
        onFx(workspace.exportSnapshot(output), saved -> {
            status.setText(i18n.text("workspace.status.exported", saved));
            activity.info(i18n.text("workspace.status.exported", saved));
        }, error -> status.setText(i18n.text("workspace.status.export_failed", message(error))));
    }

    private void importTabs(TextField path, Label status, ContextMenu menu) {
        Path input = path(path.getText(), status);
        if (input == null) return;
        status.setText(i18n.text("workspace.status.importing"));
        onFx(workspace.importSnapshot(input), snapshot -> {
            applyWorkspaceSnapshot(snapshot);
            status.setText(i18n.text("workspace.status.imported", input));
            activity.info(i18n.text("workspace.status.imported", input));
            menu.hide();
        }, error -> status.setText(i18n.text("workspace.status.import_failed", message(error))));
    }

    private void exportRepository(TextField path, Label status) {
        Path output = path(path.getText(), status);
        if (output == null) return;
        status.setText(i18n.text("workspace.status.exporting"));
        onFx(workspace.exportRepository(output), saved -> {
            status.setText(i18n.text("workspace.status.repository_exported", saved));
            activity.info(i18n.text("workspace.status.repository_exported", saved));
        }, error -> status.setText(i18n.text("workspace.status.export_failed", message(error))));
    }

    private Path path(String raw, Label status) {
        try {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException();
            return Path.of(raw.trim());
        } catch (RuntimeException invalid) {
            status.setText(i18n.text("workspace.status.path_required"));
            return null;
        }
    }

    // ------------------------------------------------------------- App bar
    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(appLogo());
        mark.getStyleClass().add("app-mark");
        Label title = Mat.label(applicationName.get(), "app-title");
        var titleBinding = Bindings.createStringBinding(() -> {
            if (engine.settings().speedInTitleProperty().get() && engine.globalSpeedProperty().get() > 0) {
                return applicationName.get() + " - ▼ " + Formats.speed(engine.globalSpeedProperty().get());
            }
            return applicationName.get();
        }, applicationName, engine.globalSpeedProperty(), engine.settings().speedInTitleProperty());
        title.textProperty().bind(titleBinding);
        shellDisposers.add(() -> title.textProperty().unbind());
        VBox titleBox = new VBox(-2, title);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        var clipboard = toggleAction("paste", "app.clipboard", "tooltip.clipboard",
                engine.settings().clipboardMonitoringProperty());
        var autoReconnect = toggleAction("reconnect", "app.auto_retry", "tooltip.auto_retry",
                engine.settings().autoReconnectProperty());

        var themeToggle = Mat.text(theme.isDark() ? i18n.text("app.theme.light") : i18n.text("app.theme.dark"),
                theme.isDark() ? "sun" : "moon");
        Runnable updateThemeToggle = () -> {
            boolean dark = theme.isDark();
            themeToggle.setText(i18n.text(dark ? "app.theme.light" : "app.theme.dark"));
            themeToggle.setGraphic(Icons.of(dark ? "sun" : "moon", 20));
            Mat.tip(themeToggle, i18n.text(dark ? "tooltip.light_theme" : "tooltip.dark_theme"));
        };
        updateThemeToggle.run();
        themeToggle.setOnAction(event -> theme.toggle());
        ChangeListener<Boolean> themeListener = (o, wasDark, isDark) -> updateThemeToggle.run();
        theme.darkProperty().addListener(themeListener);
        shellDisposers.add(() -> theme.darkProperty().removeListener(themeListener));

        var minimize = Mat.icon("minimize", i18n.text("window.minimize"));
        minimize.getStyleClass().add("window-control");
        minimize.setOnAction(event -> stage.setIconified(true));
        var maximize = Mat.icon("maximize", i18n.text("window.maximize"));
        maximize.getStyleClass().add("window-control");
        maximize.setOnAction(event -> stage.setMaximized(!stage.isMaximized()));
        ChangeListener<Boolean> maximizedListener = (o, wasMaximized, isMaximized) ->
                updateMaximizeControl(maximize, isMaximized);
        stage.maximizedProperty().addListener(maximizedListener);
        shellDisposers.add(() -> stage.maximizedProperty().removeListener(maximizedListener));
        updateMaximizeControl(maximize, stage.isMaximized());
        var close = Mat.icon("close", i18n.text("window.close"));
        close.getStyleClass().addAll("window-control", "window-close");
        close.setOnAction(event -> stage.close());

        HBox bar = new HBox(12, mark, titleBox, Mat.hSpacer(), clipboard, autoReconnect, Mat.vSep(), themeToggle,
                Mat.vSep(), minimize, maximize, close);
        bar.getStyleClass().add("top-app-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        installWindowDragging(bar);
        return bar;
    }

    private Node appLogo() {
        var resource = getClass().getResource("/icons/app.png");
        if (resource == null) return Icons.of("download", 18, "icon-on-primary");
        ImageView logo = new ImageView(new Image(resource.toExternalForm(), 32, 32, true, true, true));
        logo.setSmooth(true);
        return logo;
    }

    private void updateMaximizeControl(MFXButton button, boolean maximized) {
        button.setGraphic(Icons.of(maximized ? "restore" : "maximize", 20));
        Mat.tip(button, i18n.text(maximized ? "window.restore" : "window.maximize"));
    }

    private void installWindowDragging(HBox bar) {
        bar.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || targetsAControl(event.getTarget())) return;
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });
        bar.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown() || stage.isMaximized() || targetsAControl(event.getTarget())) return;
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
        bar.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && !targetsAControl(event.getTarget())) stage.setMaximized(!stage.isMaximized());
        });
    }

    private boolean targetsAControl(Object target) {
        for (Node node = target instanceof Node n ? n : null; node != null; node = node.getParent()) {
            if (node instanceof ButtonBase || node instanceof MFXButton) return true;
        }
        return false;
    }

    private MFXButton toggleAction(String icon, String textKey, String tipKey, BooleanProperty property) {
        MFXButton button = Mat.text(i18n.text(textKey), icon);
        Mat.tip(button, i18n.text(tipKey));
        Runnable restyle = () -> {
            button.getStyleClass().remove("active");
            if (property.get()) button.getStyleClass().add("active");
        };
        restyle.run();
        button.setOnAction(event -> {
            property.set(!property.get());
            restyle.run();
        });
        ChangeListener<Boolean> listener = (o, oldValue, newValue) -> restyle.run();
        property.addListener(listener);
        shellDisposers.add(() -> property.removeListener(listener));
        return button;
    }

    // -------------------------------------------------------------- Nav rail
    private ToggleButton navItem(String icon, String textKey, WorkspacePage page) {
        StackPane glyph = new StackPane(Icons.of(icon, 22));
        glyph.getStyleClass().add("nav-glyph");
        Label label = new Label(i18n.text(textKey));
        label.getStyleClass().add("nav-label");
        label.setWrapText(true);
        label.setMaxWidth(84);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        VBox body = new VBox(4, glyph, label);
        body.setAlignment(Pos.CENTER);
        ToggleButton tab = new ToggleButton();
        tab.setGraphic(body);
        tab.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        tab.getStyleClass().add("nav-item");
        tab.setToggleGroup(navGroup);
        tab.setOnAction(event -> openOrSelect(page));
        navTabs.put(page, tab);
        return tab;
    }

    private static String iconFor(WorkspacePage page) {
        return switch (page) {
            case DOWNLOADS -> "download";
            case LINKGRABBER -> "link";
            case HISTORY -> "history";
            case SETTINGS -> "settings";
            case ADD_LINKS -> "add";
        };
    }

    private String pageTitle(WorkspacePage page) {
        return switch (page) {
            case DOWNLOADS -> i18n.text("nav.downloads");
            case LINKGRABBER -> i18n.text("nav.linkgrabber");
            case HISTORY -> i18n.text("nav.history");
            case SETTINGS -> i18n.text("settings.title");
            case ADD_LINKS -> i18n.text("addlinks.title");
        };
    }

    private static void applyAppearance(WorkspaceTab tab, Label header, StackPane page) {
        WorkspaceStyle style = tab.style();
        header.setText(tab.title());
        header.setFont(Font.font(style.fontFamily(), style.bold() ? FontWeight.BOLD : FontWeight.NORMAL,
                style.italic() ? FontPosture.ITALIC : FontPosture.REGULAR, style.fontSize()));
        header.setTextFill(color(style.color()));
        String family = style.fontFamily().replace("\\", "\\\\").replace("\"", "\\\"");
        page.setStyle("-fx-font-family: \"" + family + "\"; -fx-font-size: " + style.fontSize()
                + "px; -fx-text-fill: " + style.color() + ";");
    }

    private static Color color(String value) {
        try {
            return Color.web(value);
        } catch (RuntimeException invalid) {
            return Color.web(WorkspaceStyle.DEFAULT.color());
        }
    }

    private static String hex(Color value) {
        return String.format("#%02X%02X%02X%02X", Math.round(value.getRed() * 255), Math.round(value.getGreen() * 255),
                Math.round(value.getBlue() * 255), Math.round(value.getOpacity() * 255));
    }

    private static String formatFontSize(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static double parseFontSize(String value, double fallback) {
        try {
            return Math.max(6, Math.min(160, Double.parseDouble(value)));
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private <T> void onFx(CompletableFuture<T> future, Consumer<T> success, Consumer<Throwable> failure) {
        future.whenComplete((value, error) -> Platform.runLater(() -> {
            if (disposed) return;
            if (error == null) success.accept(value);
            else failure.accept(error);
        }));
    }

    private static String message(Throwable error) {
        Throwable cursor = error;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        String text = cursor.getMessage();
        return text == null || text.isBlank() ? cursor.getClass().getSimpleName() : text;
    }

    private static final class ViewBundle {
        private final Node node;
        private final DownloadsView downloads;
        private final LinkGrabberView linkGrabber;
        private final HistoryView history;
        private final SettingsView settings;
        private final AddLinksView addLinks;

        private ViewBundle(Node node, DownloadsView downloads, LinkGrabberView linkGrabber, HistoryView history,
                           SettingsView settings, AddLinksView addLinks) {
            this.node = node;
            this.downloads = downloads;
            this.linkGrabber = linkGrabber;
            this.history = history;
            this.settings = settings;
            this.addLinks = addLinks;
        }

        private void dispose() {
            if (downloads != null) downloads.dispose();
            if (linkGrabber != null) linkGrabber.dispose();
            if (history != null) history.dispose();
            if (addLinks != null) addLinks.dispose();
        }
    }
}
