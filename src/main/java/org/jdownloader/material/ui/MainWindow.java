package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.ActivityStatus;
import org.jdownloader.material.ui.component.ClipboardMonitor;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.component.ThroughputMeter;
import org.jdownloader.material.ui.view.AddLinksView;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.HistoryView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.SettingsView;

/**
 * Production application shell based on the supplied JDownloader design
 * handoff. The shell deliberately keeps navigation, global transfer controls,
 * search, status, and the Add Links drawer in stable positions while views
 * continue to own their engine-specific behavior.
 */
public final class MainWindow extends StackPane {

    private enum Page {
        DOWNLOADS,
        LINKGRABBER,
        HISTORY,
        SETTINGS
    }

    private static final double EXPANDED_RAIL_WIDTH = 208;
    private static final double COMPACT_RAIL_WIDTH = 72;
    private static final double EXPANDED_BRAND_WIDTH = 194;
    private static final double COMPACT_BRAND_WIDTH = 48;

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final Stage stage;
    private final I18n i18n;
    private final ActivityStatus activity = new ActivityStatus();
    private final ClipboardMonitor clipboardMonitor;
    private final StringProperty applicationName =
            new SimpleStringProperty(this, "applicationName", "JDownloader Material");
    private final Map<Page, ToggleButton> navButtons = new EnumMap<>(Page.class);
    private final List<Label> responsiveLabels = new ArrayList<>();
    private final List<Runnable> shellDisposers = new ArrayList<>();
    private final ChangeListener<LanguageMode> languageListener =
            (observable, previous, current) -> rebuildShell();

    private Page currentPage = Page.DOWNLOADS;
    private DownloadsView downloads;
    private LinkGrabberView linkGrabber;
    private HistoryView history;
    private SettingsView settings;
    private AddLinksView addLinks;
    private ThroughputMeter throughput;
    private StackPane contentHost;
    private Region drawerScrim;
    private StackPane drawerHost;
    private TextField globalSearch;
    private MFXButton addLinksButton;
    private VBox navigation;
    private HBox brand;
    private ParallelTransition drawerTransition;
    private boolean drawerOpen;
    private boolean disposed;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean windowDragArmed;
    private Node focusBeforeDrawer;

    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage) {
        this(engine, theme, stage, null);
    }

    /**
     * The path argument is retained for source compatibility with the prior
     * workspace-tab shell and isolated screenshot mode. The new navigation
     * model no longer stores presentation state in a private Git repository.
     */
    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage, Path ignoredWorkspaceRoot) {
        this.engine = engine;
        this.theme = theme;
        this.stage = stage;
        this.i18n = new I18n(engine.settings().languageProperty());
        getStyleClass().add("app-frame");
        setFocusTraversable(true);
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleDrawerKeyPressed);
        widthProperty().addListener((observable, previous, current) -> updateResponsiveState());
        i18n.modeProperty().addListener(languageListener);
        rebuildShell();

        clipboardMonitor = new ClipboardMonitor(engine, activity, i18n);
        clipboardMonitor.start();
    }

    public StringProperty applicationNameProperty() {
        return applicationName;
    }

    private void rebuildShell() {
        if (disposed) return;
        if (drawerTransition != null) {
            drawerTransition.stop();
            drawerTransition = null;
        }
        AddLinksView.Draft draft = addLinks == null ? null : addLinks.draft();
        String settingsSection = settings == null ? null : settings.selectedTabKey();
        boolean restoreDrawer = drawerOpen;
        disposeViews();
        shellDisposers.forEach(Runnable::run);
        shellDisposers.clear();
        navButtons.clear();
        responsiveLabels.clear();

        downloads = new DownloadsView(engine, activity, i18n);
        linkGrabber = new LinkGrabberView(engine, activity, this::openAddLinks, i18n);
        history = new HistoryView(engine.history(), i18n);
        settings = new SettingsView(engine.settings(), i18n, settingsSection);
        addLinks = new AddLinksView(engine, this::hideAddLinks, () -> {
            hideAddLinks();
            showLinkGrabber();
        }, i18n);
        if (draft != null) addLinks.restoreDraft(draft);

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTopAppBar());
        shell.setLeft(buildNavigation());
        contentHost = new StackPane();
        contentHost.getStyleClass().add("app-content");
        shell.setCenter(contentHost);
        StatusBar statusBar = new StatusBar(engine, i18n, activity);
        shell.setBottom(statusBar);
        shellDisposers.add(statusBar::dispose);

        drawerScrim = new Region();
        drawerScrim.getStyleClass().add("drawer-scrim");
        drawerScrim.setVisible(false);
        drawerScrim.setManaged(false);
        drawerScrim.setOnMouseClicked(event -> hideAddLinks());

        drawerHost = new StackPane(addLinks);
        drawerHost.getStyleClass().add("add-links-drawer");
        drawerHost.setAccessibleRole(AccessibleRole.DIALOG);
        drawerHost.setAccessibleText(i18n.text("addlinks.title"));
        drawerHost.setMaxWidth(440);
        drawerHost.setPrefWidth(440);
        StackPane.setAlignment(drawerHost, Pos.CENTER_RIGHT);
        drawerHost.setVisible(false);
        drawerHost.setManaged(false);

        getChildren().setAll(shell, drawerScrim, drawerHost);
        drawerOpen = false;
        showPage(currentPage, false);
        updateResponsiveState();
        if (restoreDrawer) Platform.runLater(this::openAddLinks);
    }

    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(Icons.of("download", 18));
        mark.getStyleClass().add("brand-mark");
        Label brandName = new Label("JDownloader");
        brandName.getStyleClass().add("brand-name");
        responsiveLabels.add(brandName);
        brand = new HBox(10, mark, brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("app-brand");
        brand.setMinWidth(EXPANDED_BRAND_WIDTH);
        brand.setPrefWidth(EXPANDED_BRAND_WIDTH);

        addLinksButton = Mat.filled(i18n.text("downloads.add_links"), "add");
        addLinksButton.getStyleClass().add("top-add-links");
        addLinksButton.setOnAction(event -> openAddLinks());

        MFXButton start = Mat.icon("play", i18n.text("tooltip.start"));
        start.setOnAction(event -> engine.start());
        MFXButton pause = Mat.icon(engine.pausedProperty().get() ? "play" : "pause",
                i18n.text(engine.pausedProperty().get() ? "tooltip.resume" : "tooltip.pause"));
        pause.setOnAction(event -> engine.pause(!engine.pausedProperty().get()));
        ChangeListener<Boolean> pauseListener = (observable, wasPaused, isPaused) -> {
            pause.setGraphic(Icons.of(isPaused ? "play" : "pause", 20));
            String label = i18n.text(isPaused ? "tooltip.resume" : "tooltip.pause");
            Mat.tip(pause, label);
            pause.setAccessibleText(label);
            pause.setAccessibleHelp(label);
        };
        engine.pausedProperty().addListener(pauseListener);
        shellDisposers.add(() -> engine.pausedProperty().removeListener(pauseListener));

        MFXButton stop = Mat.icon("stop", i18n.text("tooltip.stop"));
        stop.setOnAction(event -> engine.stop());

        globalSearch = new TextField();
        globalSearch.getStyleClass().add("top-search");
        globalSearch.setPromptText(i18n.text("downloads.search"));
        HBox.setHgrow(globalSearch, Priority.ALWAYS);
        globalSearch.setMaxWidth(300);
        globalSearch.textProperty().addListener((observable, previous, value) -> applyGlobalSearch(value));

        throughput = new ThroughputMeter(engine.globalSpeedProperty(), i18n);

        MFXButton themeButton = Mat.icon(theme.isDark() ? "sun" : "moon",
                i18n.text(theme.isDark() ? "tooltip.light_theme" : "tooltip.dark_theme"));
        themeButton.setOnAction(event -> theme.toggle());
        ChangeListener<Boolean> themeListener = (observable, wasDark, isDark) -> {
            themeButton.setGraphic(Icons.of(isDark ? "sun" : "moon", 20));
            String label = i18n.text(isDark ? "tooltip.light_theme" : "tooltip.dark_theme");
            Mat.tip(themeButton, label);
            themeButton.setAccessibleText(label);
            themeButton.setAccessibleHelp(label);
        };
        theme.darkProperty().addListener(themeListener);
        shellDisposers.add(() -> theme.darkProperty().removeListener(themeListener));

        MFXButton clipboard = Mat.icon("paste", i18n.text("tooltip.clipboard"));
        clipboard.getStyleClass().add("clipboard-button");
        clipboard.setOnAction(event -> engine.settings().clipboardMonitoringProperty().set(
                !engine.settings().clipboardMonitoringProperty().get()));
        clipboard.opacityProperty().bind(Bindings.when(engine.settings().clipboardMonitoringProperty())
                .then(1.0).otherwise(0.48));
        shellDisposers.add(() -> clipboard.opacityProperty().unbind());

        HBox windowControls = buildWindowControls();
        HBox toolbar = new HBox(4, addLinksButton, start, pause, stop, Mat.hSpacer(), globalSearch,
                throughput, themeButton, clipboard, windowControls);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("top-toolbar");
        HBox.setHgrow(toolbar, Priority.ALWAYS);

        HBox topBar = new HBox(0, brand, toolbar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-app-bar");
        installWindowDragging(topBar);
        return topBar;
    }

    private HBox buildWindowControls() {
        MFXButton minimize = Mat.icon("minimize", i18n.text("window.minimize"));
        minimize.getStyleClass().add("window-control");
        minimize.setOnAction(event -> stage.setIconified(true));

        MFXButton maximize = Mat.icon(stage.isMaximized() ? "restore" : "maximize",
                i18n.text(stage.isMaximized() ? "window.restore" : "window.maximize"));
        maximize.getStyleClass().add("window-control");
        maximize.setOnAction(event -> toggleMaximize());
        ChangeListener<Boolean> maximizeListener = (observable, wasMaximized, isMaximized) -> {
            maximize.setGraphic(Icons.of(isMaximized ? "restore" : "maximize", 18));
            String label = i18n.text(isMaximized ? "window.restore" : "window.maximize");
            Mat.tip(maximize, label);
            maximize.setAccessibleText(label);
            maximize.setAccessibleHelp(label);
        };
        stage.maximizedProperty().addListener(maximizeListener);
        shellDisposers.add(() -> stage.maximizedProperty().removeListener(maximizeListener));

        MFXButton close = Mat.icon("close", i18n.text("window.close"));
        close.getStyleClass().addAll("window-control", "close-window");
        close.setOnAction(event -> stage.fireEvent(
                new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));

        HBox controls = new HBox(0, minimize, maximize, close);
        controls.getStyleClass().add("window-controls");
        controls.setAlignment(Pos.CENTER_RIGHT);
        return controls;
    }

    private VBox buildNavigation() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton downloadsItem = navItem(Page.DOWNLOADS, "download", "nav.downloads", group);
        ToggleButton linksItem = navItem(Page.LINKGRABBER, "link", "nav.linkgrabber", group);
        ToggleButton historyItem = navItem(Page.HISTORY, "history", "nav.history", group);
        Label system = new Label(i18n.text("nav.system"));
        system.getStyleClass().add("nav-section-label");
        responsiveLabels.add(system);
        ToggleButton settingsItem = navItem(Page.SETTINGS, "settings", "nav.settings", group);
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        navigation = new VBox(4, downloadsItem, linksItem, historyItem, system, settingsItem, spacer);
        navigation.getStyleClass().add("primary-nav");
        navigation.setMinWidth(EXPANDED_RAIL_WIDTH);
        navigation.setPrefWidth(EXPANDED_RAIL_WIDTH);
        return navigation;
    }

    private ToggleButton navItem(Page page, String icon, String key, ToggleGroup group) {
        String accessibleLabel = i18n.text(key);
        Label label = new Label(accessibleLabel);
        label.getStyleClass().add("nav-label");
        responsiveLabels.add(label);
        Region glyph = Icons.of(icon, 20);
        glyph.getStyleClass().add("nav-icon");
        HBox graphic = new HBox(12, glyph, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        ToggleButton button = new ToggleButton();
        button.setGraphic(graphic);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setOnAction(event -> {
            if (button.isSelected()) showPage(page, true);
            else button.setSelected(true);
        });
        Mat.tip(button, accessibleLabel);
        button.setAccessibleText(accessibleLabel);
        button.setAccessibleHelp(accessibleLabel);
        navButtons.put(page, button);
        return button;
    }

    private void showPage(Page page, boolean focusHeading) {
        if (contentHost == null) return;
        currentPage = page;
        hideAddLinks();
        Node view = switch (page) {
            case DOWNLOADS -> downloads;
            case LINKGRABBER -> linkGrabber;
            case HISTORY -> history;
            case SETTINGS -> settings;
        };
        contentHost.getChildren().setAll(view);
        ToggleButton selected = navButtons.get(page);
        if (selected != null) selected.setSelected(true);
        if (globalSearch != null) {
            globalSearch.setDisable(page == Page.SETTINGS);
            globalSearch.setPromptText(i18n.text(switch (page) {
                case DOWNLOADS -> "downloads.search";
                case LINKGRABBER -> "linkgrabber.search";
                case HISTORY -> "history.search";
                case SETTINGS -> "downloads.search";
            }));
            applyGlobalSearch(globalSearch.getText());
        }
        if (focusHeading) Platform.runLater(view::requestFocus);
    }

    private void applyGlobalSearch(String value) {
        String filter = value == null ? "" : value;
        if (currentPage == Page.DOWNLOADS && downloads != null) downloads.setFilter(filter);
        if (currentPage == Page.LINKGRABBER && linkGrabber != null) linkGrabber.setFilter(filter);
        if (currentPage == Page.HISTORY && history != null) history.setFilter(filter);
    }

    private void updateResponsiveState() {
        if (navigation == null || brand == null) return;
        boolean compact = getWidth() > 0 && getWidth() < 980;
        double width = compact ? COMPACT_RAIL_WIDTH : EXPANDED_RAIL_WIDTH;
        navigation.setMinWidth(width);
        navigation.setPrefWidth(width);
        double brandWidth = compact ? COMPACT_BRAND_WIDTH : EXPANDED_BRAND_WIDTH;
        brand.setMinWidth(brandWidth);
        brand.setPrefWidth(brandWidth);
        navigation.getStyleClass().remove("compact-nav");
        if (compact) navigation.getStyleClass().add("compact-nav");
        responsiveLabels.forEach(label -> {
            label.setVisible(!compact);
            label.setManaged(!compact);
        });
        if (globalSearch != null) {
            globalSearch.setVisible(!compact);
            globalSearch.setManaged(!compact);
        }
        if (throughput != null) {
            throughput.setVisible(!compact);
            throughput.setManaged(!compact);
        }
    }

    private void installWindowDragging(HBox topBar) {
        topBar.setOnMousePressed(event -> {
            windowDragArmed = event.getButton() == MouseButton.PRIMARY
                    && !isInteractiveTarget(event.getTarget());
            if (!windowDragArmed) return;
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });
        topBar.setOnMouseDragged(event -> {
            if (!windowDragArmed || !event.isPrimaryButtonDown()) return;
            if (stage.isMaximized()) stage.setMaximized(false);
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
        topBar.setOnMouseReleased(event -> windowDragArmed = false);
        topBar.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && !isInteractiveTarget(event.getTarget())) {
                toggleMaximize();
            }
        });
    }

    private boolean isInteractiveTarget(Object target) {
        Node node = target instanceof Node candidate ? candidate : null;
        while (node != null) {
            if (node instanceof javafx.scene.control.ButtonBase || node instanceof TextInputControl) return true;
            node = node.getParent();
        }
        return false;
    }

    private void toggleMaximize() {
        stage.setMaximized(!stage.isMaximized());
    }

    public void openAddLinks() {
        if (disposed || drawerHost == null || drawerOpen) return;
        focusBeforeDrawer = getScene() == null ? null : getScene().getFocusOwner();
        drawerOpen = true;
        drawerScrim.setManaged(true);
        drawerScrim.setVisible(true);
        drawerHost.setManaged(true);
        drawerHost.setVisible(true);
        drawerScrim.setOpacity(0);
        drawerHost.setTranslateX(440);
        animateDrawer(0, 1, 440, 0, false);
    }

    private void hideAddLinks() {
        if (drawerHost == null || !drawerOpen) return;
        drawerOpen = false;
        addLinks.releaseSubmissionOwnership();
        animateDrawer(drawerScrim.getOpacity(), 0, drawerHost.getTranslateX(), 440, true);
    }

    private void animateDrawer(double scrimFrom, double scrimTo, double drawerFrom,
                               double drawerTo, boolean hideAfter) {
        if (drawerTransition != null) drawerTransition.stop();
        FadeTransition fade = new FadeTransition(Duration.millis(hideAfter ? 180 : 220), drawerScrim);
        fade.setFromValue(scrimFrom);
        fade.setToValue(scrimTo);
        TranslateTransition slide = new TranslateTransition(Duration.millis(260), drawerHost);
        slide.setFromX(drawerFrom);
        slide.setToX(drawerTo);
        drawerTransition = new ParallelTransition(fade, slide);
        if (hideAfter) {
            drawerTransition.setOnFinished(event -> {
                if (drawerOpen) return;
                drawerScrim.setVisible(false);
                drawerScrim.setManaged(false);
                drawerHost.setVisible(false);
                drawerHost.setManaged(false);
                restoreFocusAfterDrawer();
            });
        } else {
            drawerTransition.setOnFinished(event -> addLinks.requestInitialFocus());
        }
        drawerTransition.play();
    }

    private void handleDrawerKeyPressed(KeyEvent event) {
        if (!drawerOpen) return;
        if (event.getCode() == KeyCode.ESCAPE) {
            hideAddLinks();
            event.consume();
            return;
        }
        if (event.getCode() != KeyCode.TAB || drawerHost == null) return;
        List<Control> focusable = new ArrayList<>();
        collectFocusable(drawerHost, focusable);
        if (focusable.isEmpty()) return;
        Node focused = getScene() == null ? null : getScene().getFocusOwner();
        int index = focusable.indexOf(focused);
        Control target = null;
        if (index < 0) target = event.isShiftDown() ? focusable.getLast() : focusable.getFirst();
        else if (event.isShiftDown() && index == 0) target = focusable.getLast();
        else if (!event.isShiftDown() && index == focusable.size() - 1) target = focusable.getFirst();
        if (target != null) {
            target.requestFocus();
            event.consume();
        }
    }

    private void collectFocusable(Parent parent, List<Control> controls) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Control control) {
                if (control.isVisible() && !control.isDisabled() && control.isFocusTraversable()) {
                    controls.add(control);
                }
                continue;
            }
            if (child instanceof Parent nested) collectFocusable(nested, controls);
        }
    }

    private void restoreFocusAfterDrawer() {
        Node candidate = focusBeforeDrawer;
        focusBeforeDrawer = null;
        if (candidate != null && candidate.getScene() == getScene() && candidate.isVisible()) {
            candidate.requestFocus();
        } else if (addLinksButton != null && addLinksButton.getScene() == getScene()) {
            addLinksButton.requestFocus();
        }
    }

    public void showDownloads() {
        showPage(Page.DOWNLOADS, false);
    }

    public void showLinkGrabber() {
        showPage(Page.LINKGRABBER, false);
    }

    public void showHistory() {
        showPage(Page.HISTORY, false);
    }

    public void showSettings() {
        showPage(Page.SETTINGS, false);
    }

    public void showDownloadsForCapture() {
        showDownloads();
        downloads.clearSelectionForCapture();
    }

    public void showDownloadsWithActivityForCapture() {
        showDownloadsForCapture();
        activity.info(i18n.text("status.activity_capture"));
    }

    public void showDownloadsWithEditableSelection() {
        showDownloads();
        downloads.selectFirstEditableForCapture();
    }

    public void showSettingsAppearanceForCapture() {
        showSettings();
        settings.showAppearanceForCapture();
    }

    public void showSettingsGeneralForCapture() {
        showSettings();
        settings.showGeneralForCapture();
    }

    public void showAddLinksForCapture() {
        showDownloadsForCapture();
        openAddLinks();
    }

    /** Compatibility hook retained for older capture scripts. */
    public void showTabEditorForCapture() {
        showDownloadsForCapture();
    }

    public void clearTransientFocus() {
        requestFocus();
    }

    public void clearActivityStatus() {
        activity.clear();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        if (drawerTransition != null) drawerTransition.stop();
        clipboardMonitor.stop();
        i18n.modeProperty().removeListener(languageListener);
        shellDisposers.forEach(Runnable::run);
        shellDisposers.clear();
        disposeViews();
    }

    private void disposeViews() {
        if (downloads != null) downloads.dispose();
        if (linkGrabber != null) linkGrabber.dispose();
        if (history != null) history.dispose();
        if (settings != null) settings.dispose();
        if (addLinks != null) addLinks.dispose();
        if (throughput != null) throughput.dispose();
        downloads = null;
        linkGrabber = null;
        history = null;
        settings = null;
        addLinks = null;
        throughput = null;
    }
}
