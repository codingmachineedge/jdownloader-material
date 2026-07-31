package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jdownloader.material.changelog.ChangelogService;
import org.jdownloader.material.dimsum.DimSumSurpriseService;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.integration.ExternalEditorActions;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.component.ActivityStatus;
import org.jdownloader.material.ui.component.ClipboardMonitor;
import org.jdownloader.material.ui.component.DimSumSurpriseOverlay;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationOverlay;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.component.ThroughputMeter;
import org.jdownloader.material.ui.search.SearchField;
import org.jdownloader.material.ui.stock.StockFeatureView;
import org.jdownloader.material.ui.view.AddLinksView;
import org.jdownloader.material.ui.view.ChangelogView;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.HistoryView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.NotificationCenterView;
import org.jdownloader.material.ui.view.SettingsView;
import org.jdownloader.material.ui.workspace.WorkspaceContent;
import org.jdownloader.material.ui.workspace.WorkspacePane;
import org.jdownloader.material.workspace.WorkspacePage;
import org.jdownloader.material.workspace.WorkspaceTab;

/** M3 desktop shell with persistent browser-style workspaces and non-blocking feedback. */
public final class MainWindow extends StackPane {
    private static final double EXPANDED_RAIL_WIDTH = 208;
    private static final double COMPACT_RAIL_WIDTH = 72;
    private static final double EXPANDED_BRAND_WIDTH = 194;
    private static final double COMPACT_BRAND_WIDTH = 48;

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final Stage stage;
    private final Path workspaceRoot;
    private final I18n i18n;
    private final ActivityStatus activity = new ActivityStatus();
    private final NotificationService notifications;
    private final ExternalEditorActions externalEditors;
    private final ChangelogService changelog = new ChangelogService();
    private final DimSumSurpriseService dimSumSurprise;
    private final ClipboardMonitor clipboardMonitor;
    private final StringProperty applicationName =
            new SimpleStringProperty(this, "applicationName", "JDownloader Material");
    private final Map<WorkspacePage, ToggleButton> navButtons = new EnumMap<>(WorkspacePage.class);
    private final List<Label> responsiveLabels = new ArrayList<>();
    private final List<Runnable> shellDisposers = new ArrayList<>();
    private final PauseTransition copyRefresh = new PauseTransition(Duration.millis(180));
    private final PauseTransition activityNotificationDelay = new PauseTransition(Duration.millis(100));
    private final PauseTransition captureOverlayDelay = new PauseTransition(Duration.millis(240));
    private final ChangeListener<LanguageMode> languageListener = (observable, previous, current) -> scheduleCopyRefresh();
    private final ChangeListener<Number> funnyListener = (observable, previous, current) -> scheduleCopyRefresh();

    private WorkspacePane workspace;
    private AddLinksView drawerAddLinks;
    private ThroughputMeter throughput;
    private StatusBar statusBar;
    private NotificationOverlay notificationOverlay;
    private DimSumSurpriseOverlay dimSumOverlay;
    private SearchField globalSearch;
    private MenuButton featuresButton;
    private Region drawerScrim;
    private StackPane drawerHost;
    private MFXButton addLinksButton;
    private VBox navigation;
    private HBox brand;
    private ParallelTransition drawerTransition;
    private boolean drawerOpen;
    private boolean disposed;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean windowDragArmed;
    private boolean suppressLiveNotificationsForCapture;
    private Node focusBeforeDrawer;
    private String lastActivityNotification = "";

    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage) {
        this(engine, theme, stage,
                Path.of(System.getProperty("user.home", "."), ".jdownloader-material", "workspace"));
    }

    /** Supplies isolated workspace/history roots for capture and JavaFX smoke tests. */
    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage, Path workspaceRoot) {
        this.engine = engine;
        this.theme = theme;
        this.stage = stage;
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.i18n = new I18n(engine.settings().languageProperty(),
                engine.settings().englishFunnyLevelProperty(), engine.settings().cantoneseFunnyLevelProperty());
        Path notificationRoot = this.workspaceRoot.getParent() == null ? this.workspaceRoot : this.workspaceRoot.getParent();
        this.notifications = new NotificationService(notificationRoot,
                () -> engine.settings().notificationHistoryEnabledProperty().get());
        this.externalEditors = new ExternalEditorActions(engine.settings(), i18n, notifications);
        this.dimSumSurprise = new DimSumSurpriseService(engine.settings());
        getStyleClass().add("app-frame");
        setFocusTraversable(true);
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleDrawerKeyPressed);
        widthProperty().addListener((observable, previous, current) -> updateResponsiveState());
        i18n.modeProperty().addListener(languageListener);
        engine.settings().englishFunnyLevelProperty().addListener(funnyListener);
        engine.settings().cantoneseFunnyLevelProperty().addListener(funnyListener);
        copyRefresh.setOnFinished(event -> rebuildShell());
        installActivityNotificationBridge();
        rebuildShell();

        clipboardMonitor = new ClipboardMonitor(engine, activity, i18n);
        clipboardMonitor.start();
    }

    public StringProperty applicationNameProperty() { return applicationName; }
    public NotificationService notificationService() { return notifications; }

    private void scheduleCopyRefresh() {
        if (!disposed) copyRefresh.playFromStart();
    }

    private void rebuildShell() {
        if (disposed) return;
        AddLinksView.Draft draft = drawerAddLinks == null ? null : drawerAddLinks.draft();
        boolean restoreDrawer = drawerOpen;
        disposeShell();
        navButtons.clear();
        responsiveLabels.clear();

        workspace = new WorkspacePane(workspaceRoot, i18n, notifications, this::createWorkspaceContent);
        applicationName.unbind();
        applicationName.bind(workspace.applicationNameProperty());
        workspace.activePageProperty().addListener((observable, previous, current) -> syncNavigation(current));

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTopAppBar());
        shell.setLeft(buildNavigation());
        shell.setCenter(workspace);
        statusBar = new StatusBar(engine, i18n, activity);
        shell.setBottom(statusBar);

        drawerAddLinks = new AddLinksView(engine, this::hideAddLinks, () -> {
            hideAddLinks();
            showLinkGrabber();
        }, i18n);
        if (draft != null) drawerAddLinks.restoreDraft(draft);
        drawerScrim = new Region();
        drawerScrim.getStyleClass().add("drawer-scrim");
        drawerScrim.setVisible(false);
        drawerScrim.setManaged(false);
        drawerScrim.setOnMouseClicked(event -> hideAddLinks());
        drawerHost = new StackPane(drawerAddLinks);
        drawerHost.getStyleClass().add("add-links-drawer");
        drawerHost.setAccessibleRole(AccessibleRole.DIALOG);
        drawerHost.setAccessibleText(i18n.text("addlinks.title"));
        drawerHost.setMaxWidth(440);
        drawerHost.setPrefWidth(440);
        StackPane.setAlignment(drawerHost, Pos.CENTER_RIGHT);
        drawerHost.setVisible(false);
        drawerHost.setManaged(false);

        notificationOverlay = new NotificationOverlay(notifications, i18n);
        notificationOverlay.setSuppressed(suppressLiveNotificationsForCapture);
        StackPane.setAlignment(notificationOverlay, Pos.BOTTOM_RIGHT);
        dimSumOverlay = new DimSumSurpriseOverlay(engine.settings(), i18n);
        StackPane.setAlignment(dimSumOverlay, Pos.BOTTOM_LEFT);
        getChildren().setAll(shell, drawerScrim, drawerHost, notificationOverlay, dimSumOverlay);
        drawerOpen = false;
        updateResponsiveState();
        if (restoreDrawer) Platform.runLater(this::restoreOpenDrawerAfterShellRefresh);
    }

    private void restoreOpenDrawerAfterShellRefresh() {
        if (disposed || drawerHost == null) return;
        drawerOpen = true;
        drawerScrim.setManaged(true);
        drawerScrim.setVisible(true);
        drawerScrim.setOpacity(1);
        drawerHost.setManaged(true);
        drawerHost.setVisible(true);
        drawerHost.setTranslateX(0);
        drawerAddLinks.requestInitialFocus();
    }

    private WorkspaceContent createWorkspaceContent(WorkspaceTab descriptor) {
        return switch (descriptor.page()) {
            case DOWNLOADS -> {
                DownloadsView view = new DownloadsView(engine, activity, i18n, externalEditors);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case LINKGRABBER -> {
                LinkGrabberView view = new LinkGrabberView(engine, activity, this::openAddLinks, i18n);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case HISTORY -> {
                HistoryView view = new HistoryView(engine.history(), i18n);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case SETTINGS -> {
                SettingsView view = new SettingsView(engine.settings(), i18n, externalEditors, notifications);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case ADD_LINKS -> {
                AddLinksView view = new AddLinksView(engine, this::showDownloads, this::showLinkGrabber, i18n);
                yield new WorkspaceContent(view, ignored -> { }, view::dispose,
                        () -> !view.draft().urls().isBlank() || !view.draft().packageName().isBlank(),
                        i18n.text("workspace.unsaved_addlinks"));
            }
            case NOTIFICATIONS -> {
                NotificationCenterView view = new NotificationCenterView(notifications, i18n);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case CHANGELOG -> {
                ChangelogView view = new ChangelogView(changelog, notifications, i18n);
                yield WorkspaceContent.simple(view, view::setSearchSpec, view::dispose);
            }
            case ACCOUNTS, PLUGINS, CAPTCHA, EXTRACTION, SCHEDULER, CONNECTIONS,
                    REMOTE_CONTROL, AUTOMATION, LOGS ->
                    StockFeatureView.create(descriptor.page(), engine.settings(), i18n, notifications)
                            .asWorkspaceContent();
        };
    }

    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(brandLogo());
        mark.getStyleClass().add("brand-mark");
        Label brandName = new Label("JDownloader Material");
        brandName.getStyleClass().add("brand-name");
        responsiveLabels.add(brandName);
        brand = new HBox(10, mark, brandName);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("app-brand");
        brand.setMinWidth(EXPANDED_BRAND_WIDTH);
        brand.setPrefWidth(EXPANDED_BRAND_WIDTH);

        addLinksButton = Mat.filled(i18n.text("downloads.add_links"), "add");
        addLinksButton.getStyleClass().add("top-add-links");
        addLinksButton.setAccessibleText(i18n.text("downloads.add_links"));
        Mat.tip(addLinksButton, i18n.text("downloads.add_links"));
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
        };
        engine.pausedProperty().addListener(pauseListener);
        shellDisposers.add(() -> engine.pausedProperty().removeListener(pauseListener));
        MFXButton stop = Mat.icon("stop", i18n.text("tooltip.stop"));
        stop.setOnAction(event -> engine.stop());

        globalSearch = new SearchField(i18n, "search.field.prompt");
        globalSearch.setMaxWidth(360);
        HBox.setHgrow(globalSearch, Priority.ALWAYS);
        globalSearch.searchSpecProperty().addListener((observable, previous, current) ->
                workspace.applyGlobalSearch(current));
        throughput = new ThroughputMeter(engine.globalSpeedProperty(), i18n);

        MFXButton themeButton = Mat.icon(theme.isDark() ? "sun" : "moon",
                i18n.text(theme.isDark() ? "tooltip.light_theme" : "tooltip.dark_theme"));
        themeButton.setOnAction(event -> theme.toggle());
        ChangeListener<Boolean> themeListener = (observable, wasDark, isDark) -> {
            themeButton.setGraphic(Icons.of(isDark ? "sun" : "moon", 20));
            String label = i18n.text(isDark ? "tooltip.light_theme" : "tooltip.dark_theme");
            Mat.tip(themeButton, label);
            themeButton.setAccessibleText(label);
        };
        theme.darkProperty().addListener(themeListener);
        shellDisposers.add(() -> theme.darkProperty().removeListener(themeListener));
        MFXButton clipboard = Mat.icon("paste", i18n.text("tooltip.clipboard"));
        clipboard.setOnAction(event -> engine.settings().clipboardMonitoringProperty().set(
                !engine.settings().clipboardMonitoringProperty().get()));
        clipboard.opacityProperty().bind(Bindings.when(engine.settings().clipboardMonitoringProperty())
                .then(1.0).otherwise(0.48));
        shellDisposers.add(() -> clipboard.opacityProperty().unbind());

        HBox toolbar = new HBox(4, addLinksButton, start, pause, stop, Mat.hSpacer(), globalSearch,
                throughput, themeButton, clipboard, buildWindowControls());
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("top-toolbar");
        HBox.setHgrow(toolbar, Priority.ALWAYS);
        HBox topBar = new HBox(0, brand, toolbar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-app-bar");
        installWindowDragging(topBar);
        return topBar;
    }

    private Node brandLogo() {
        try (var stream = getClass().getResourceAsStream("/icons/app.png")) {
            if (stream == null) return Icons.of("download", 18);
            ImageView logo = new ImageView(new Image(stream, 24, 24, true, true));
            logo.setFitWidth(24);
            logo.setFitHeight(24);
            logo.setPreserveRatio(true);
            logo.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
            logo.setAccessibleText("JDownloader Material");
            return logo;
        } catch (Exception missing) {
            return Icons.of("download", 18);
        }
    }

    private HBox buildWindowControls() {
        MFXButton minimize = Mat.icon("minimize", i18n.text("window.minimize"));
        minimize.getStyleClass().add("window-control");
        minimize.setOnAction(event -> stage.setIconified(true));
        MFXButton maximize = Mat.icon(stage.isMaximized() ? "restore" : "maximize",
                i18n.text(stage.isMaximized() ? "window.restore" : "window.maximize"));
        maximize.getStyleClass().add("window-control");
        maximize.setOnAction(event -> toggleMaximize());
        ChangeListener<Boolean> listener = (observable, previous, current) -> {
            maximize.setGraphic(Icons.of(current ? "restore" : "maximize", 18));
            maximize.setAccessibleText(i18n.text(current ? "window.restore" : "window.maximize"));
        };
        stage.maximizedProperty().addListener(listener);
        shellDisposers.add(() -> stage.maximizedProperty().removeListener(listener));
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
        VBox items = new VBox(4,
                navItem(WorkspacePage.DOWNLOADS, "download", "nav.downloads", group),
                navItem(WorkspacePage.LINKGRABBER, "link", "nav.linkgrabber", group),
                navItem(WorkspacePage.HISTORY, "history", "nav.history", group));
        Label featuresLabel = Mat.label(i18n.text("nav.features"), "nav-section-label");
        responsiveLabels.add(featuresLabel);
        MenuButton features = new MenuButton(i18n.text("nav.features"), Icons.of("tune", 20));
        features.getStyleClass().add("nav-menu");
        features.setAccessibleText(i18n.text("nav.features"));
        Mat.tip(features, i18n.text("nav.features"));
        featuresButton = features;
        for (WorkspacePage page : List.of(WorkspacePage.ACCOUNTS, WorkspacePage.PLUGINS,
                WorkspacePage.CAPTCHA, WorkspacePage.EXTRACTION, WorkspacePage.SCHEDULER,
                WorkspacePage.CONNECTIONS, WorkspacePage.REMOTE_CONTROL, WorkspacePage.AUTOMATION,
                WorkspacePage.LOGS, WorkspacePage.NOTIFICATIONS, WorkspacePage.CHANGELOG)) {
            MenuItem item = new MenuItem(pageTitle(page), Icons.of(iconFor(page), 16));
            item.setOnAction(event -> workspace.openOrSelect(page));
            features.getItems().add(item);
        }
        ToggleButton settings = navItem(WorkspacePage.SETTINGS, "settings", "nav.settings", group);
        items.getChildren().addAll(featuresLabel, features, settings);
        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("nav-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        navigation = new VBox(scroll);
        navigation.getStyleClass().add("primary-nav");
        navigation.setMinWidth(EXPANDED_RAIL_WIDTH);
        navigation.setPrefWidth(EXPANDED_RAIL_WIDTH);
        return navigation;
    }

    private ToggleButton navItem(WorkspacePage page, String icon, String key, ToggleGroup group) {
        String accessibleLabel = i18n.text(key);
        Label label = new Label(accessibleLabel);
        label.getStyleClass().add("nav-label");
        responsiveLabels.add(label);
        HBox graphic = new HBox(12, Icons.of(icon, 20), label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        ToggleButton button = new ToggleButton();
        button.setGraphic(graphic);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setOnAction(event -> {
            if (button.isSelected()) workspace.openOrSelect(page); else button.setSelected(true);
        });
        Mat.tip(button, accessibleLabel);
        button.setAccessibleText(accessibleLabel);
        navButtons.put(page, button);
        return button;
    }

    private void syncNavigation(WorkspacePage page) {
        ToggleButton selected = navButtons.get(page);
        if (selected != null) selected.setSelected(true);
    }

    private String pageTitle(WorkspacePage page) {
        return i18n.text(switch (page) {
            case DOWNLOADS -> "nav.downloads";
            case LINKGRABBER -> "nav.linkgrabber";
            case HISTORY -> "nav.history";
            case SETTINGS -> "nav.settings";
            case ADD_LINKS -> "addlinks.title";
            case ACCOUNTS -> "stock.accounts";
            case PLUGINS -> "stock.plugins";
            case CAPTCHA -> "stock.captcha";
            case EXTRACTION -> "stock.extraction";
            case SCHEDULER -> "stock.scheduler";
            case CONNECTIONS -> "stock.connections";
            case REMOTE_CONTROL -> "stock.remote";
            case AUTOMATION -> "stock.automation";
            case LOGS -> "stock.logs";
            case NOTIFICATIONS -> "notifications.title";
            case CHANGELOG -> "changelog.title";
        });
    }

    private static String iconFor(WorkspacePage page) {
        return switch (page) {
            case DOWNLOADS -> "download";
            case LINKGRABBER -> "link";
            case HISTORY, CHANGELOG -> "history";
            case SETTINGS -> "settings";
            case ADD_LINKS -> "add";
            case ACCOUNTS -> "account";
            case PLUGINS -> "tune";
            case CAPTCHA -> "shield";
            case EXTRACTION -> "folder";
            case SCHEDULER, AUTOMATION -> "history";
            case CONNECTIONS, REMOTE_CONTROL -> "reconnect";
            case LOGS -> "info";
            case NOTIFICATIONS -> "info";
        };
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
        responsiveLabels.forEach(label -> { label.setVisible(!compact); label.setManaged(!compact); });
        if (addLinksButton != null) {
            addLinksButton.setText(compact ? "" : i18n.text("downloads.add_links"));
            addLinksButton.setContentDisplay(compact
                    ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                    : javafx.scene.control.ContentDisplay.LEFT);
            addLinksButton.setMinWidth(compact ? 48 : Region.USE_COMPUTED_SIZE);
            addLinksButton.setPrefWidth(compact ? 48 : Region.USE_COMPUTED_SIZE);
            addLinksButton.setMaxWidth(compact ? 48 : Region.USE_COMPUTED_SIZE);
        }
        if (featuresButton != null) {
            featuresButton.setText(compact ? "" : i18n.text("nav.features"));
            featuresButton.setContentDisplay(compact
                    ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                    : javafx.scene.control.ContentDisplay.LEFT);
            double featureWidth = compact ? 56 : 176;
            featuresButton.setMinWidth(featureWidth);
            featuresButton.setPrefWidth(featureWidth);
            featuresButton.setMaxWidth(featureWidth);
        }
        if (globalSearch != null) globalSearch.setMaxWidth(compact ? 180 : 360);
        if (throughput != null) {
            throughput.setVisible(!compact);
            throughput.setManaged(!compact);
        }
    }

    private void installActivityNotificationBridge() {
        Runnable schedule = () -> activityNotificationDelay.playFromStart();
        activity.messageProperty().addListener((observable, previous, current) -> schedule.run());
        activity.errorProperty().addListener((observable, previous, current) -> schedule.run());
        activityNotificationDelay.setOnFinished(event -> {
            String message = activity.messageProperty().get();
            if (message == null || message.isBlank()) return;
            String key = activity.errorProperty().get() + "\n" + message;
            if (key.equals(lastActivityNotification)) return;
            lastActivityNotification = key;
            if (activity.errorProperty().get()) notifications.error(i18n.text("notifications.activity_error"), message);
            else notifications.info(i18n.text("notifications.activity"), message);
        });
    }

    public void showStartupSurprise() {
        boolean startupError = activity.errorProperty().get()
                && activity.messageProperty().get() != null && !activity.messageProperty().get().isBlank();
        boolean taskInProgress = engine.runningProperty().get() || engine.runningCountProperty().get() > 0
                || engine.retryScheduledProperty().get();
        dimSumSurprise.choose(startupError, false, taskInProgress).ifPresent(dish -> dimSumOverlay.show(dish));
        if (!engine.settings().funnyLevelDisclosedProperty().get()) {
            notifications.info(i18n.text("settings.funny_disclosure_title"), i18n.text("settings.funny_disclosure"));
            engine.settings().funnyLevelDisclosedProperty().set(true);
        }
    }

    private void installWindowDragging(HBox topBar) {
        topBar.setOnMousePressed(event -> {
            windowDragArmed = event.getButton() == MouseButton.PRIMARY && !isInteractiveTarget(event.getTarget());
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
                    && !isInteractiveTarget(event.getTarget())) toggleMaximize();
        });
    }

    private static boolean isInteractiveTarget(Object target) {
        Node node = target instanceof Node candidate ? candidate : null;
        while (node != null) {
            if (node instanceof javafx.scene.control.ButtonBase || node instanceof TextInputControl) return true;
            node = node.getParent();
        }
        return false;
    }

    private void toggleMaximize() { stage.setMaximized(!stage.isMaximized()); }

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
        drawerAddLinks.releaseSubmissionOwnership();
        animateDrawer(drawerScrim.getOpacity(), 0, drawerHost.getTranslateX(), 440, true);
    }

    private void animateDrawer(double scrimFrom, double scrimTo, double drawerFrom,
                               double drawerTo, boolean hideAfter) {
        if (drawerTransition != null) drawerTransition.stop();
        if (engine.settings().reducedMotionProperty().get()) {
            drawerScrim.setOpacity(scrimTo);
            drawerHost.setTranslateX(drawerTo);
            if (hideAfter) finishDrawerHide(); else drawerAddLinks.requestInitialFocus();
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(hideAfter ? 180 : 220), drawerScrim);
        fade.setFromValue(scrimFrom);
        fade.setToValue(scrimTo);
        TranslateTransition slide = new TranslateTransition(Duration.millis(260), drawerHost);
        slide.setFromX(drawerFrom);
        slide.setToX(drawerTo);
        drawerTransition = new ParallelTransition(fade, slide);
        drawerTransition.setOnFinished(event -> {
            if (hideAfter) finishDrawerHide(); else drawerAddLinks.requestInitialFocus();
        });
        drawerTransition.play();
    }

    private void finishDrawerHide() {
        if (drawerOpen) return;
        drawerScrim.setVisible(false);
        drawerScrim.setManaged(false);
        drawerHost.setVisible(false);
        drawerHost.setManaged(false);
        restoreFocusAfterDrawer();
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
        if (target != null) { target.requestFocus(); event.consume(); }
    }

    private static void collectFocusable(Parent parent, List<Control> controls) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Control control) {
                if (control.isVisible() && !control.isDisabled() && control.isFocusTraversable()) controls.add(control);
            } else if (child instanceof Parent nested) collectFocusable(nested, controls);
        }
    }

    private void restoreFocusAfterDrawer() {
        Node candidate = focusBeforeDrawer;
        focusBeforeDrawer = null;
        if (candidate != null && candidate.getScene() == getScene() && candidate.isVisible()) candidate.requestFocus();
        else if (addLinksButton != null) addLinksButton.requestFocus();
    }

    public void showDownloads() { workspace.openOrSelect(WorkspacePage.DOWNLOADS); }
    public void showLinkGrabber() { workspace.openOrSelect(WorkspacePage.LINKGRABBER); }
    public void showHistory() { workspace.openOrSelect(WorkspacePage.HISTORY); }
    public void showSettings() { workspace.openOrSelect(WorkspacePage.SETTINGS); }

    public void showDownloadsForCapture() {
        showDownloads();
        afterNavigation(DownloadsView.class, DownloadsView::clearSelectionForCapture);
    }

    public void showDownloadsWithActivityForCapture() {
        showDownloadsForCapture();
        activity.info(i18n.text("status.activity_capture"));
    }

    public void showDownloadsWithEditableSelection() {
        showDownloads();
        afterNavigation(DownloadsView.class, DownloadsView::selectFirstEditableForCapture);
    }

    public void showSettingsAppearanceForCapture() {
        showSettings();
        afterNavigation(SettingsView.class, SettingsView::showAppearanceForCapture);
    }

    public void showSettingsGeneralForCapture() {
        showSettings();
        afterNavigation(SettingsView.class, SettingsView::showGeneralForCapture);
    }

    public void showAddLinksForCapture() {
        showDownloadsForCapture();
        showAddLinksImmediatelyForCapture();
    }
    public void showChangelogForCapture() { workspace.openOrSelect(WorkspacePage.CHANGELOG); }
    public void showPluginsForCapture() { workspace.openOrSelect(WorkspacePage.PLUGINS); }
    public void showNotificationsForCapture() {
        workspace.openOrSelect(WorkspacePage.NOTIFICATIONS);
        notifications.warning(i18n.text("notifications.activity"),
                i18n.text("workspace.status.ready"));
    }
    public void showDimSumForCapture() {
        showDownloadsForCapture();
        // Language capture rebuilds the whole shell after 180 ms. Show the
        // genuine bundled dish on the replacement overlay, never the stale
        // node that is about to be detached.
        captureOverlayDelay.stop();
        captureOverlayDelay.setOnFinished(event -> {
            if (!disposed && dimSumOverlay != null) {
                showDownloadsForCapture();
                dimSumOverlay.show(DimSumSurpriseService.catalog().getFirst());
            }
        });
        captureOverlayDelay.playFromStart();
    }
    public void showTabEditorForCapture() { showDownloadsForCapture(); }
    public void clearTransientFocus() { requestFocus(); }
    public void clearActivityStatus() { activity.clear(); }

    /** Starts every documentation scene from a synchronous, overlay-free state. */
    public void prepareDocumentationCapture() {
        suppressLiveNotificationsForCapture = true;
        if (notificationOverlay != null) notificationOverlay.setSuppressed(true);
        captureOverlayDelay.stop();
        forceHideAddLinksForCapture();
        if (dimSumOverlay != null) dimSumOverlay.hideImmediately();
        clearNotificationsForCapture();
    }

    /** Clears async informational cards without disturbing the captured page. */
    public void clearNotificationsForCapture() {
        for (var item : List.copyOf(notifications.active())) notifications.dismiss(item.id());
    }

    private void showAddLinksImmediatelyForCapture() {
        if (disposed || drawerHost == null) return;
        if (drawerTransition != null) {
            drawerTransition.stop();
            drawerTransition = null;
        }
        drawerOpen = true;
        drawerScrim.setManaged(true);
        drawerScrim.setVisible(true);
        drawerScrim.setOpacity(1);
        drawerHost.setManaged(true);
        drawerHost.setVisible(true);
        drawerHost.setTranslateX(0);
        drawerAddLinks.requestInitialFocus();
    }

    private void forceHideAddLinksForCapture() {
        if (drawerTransition != null) {
            drawerTransition.stop();
            drawerTransition = null;
        }
        drawerOpen = false;
        if (drawerAddLinks != null) drawerAddLinks.releaseSubmissionOwnership();
        if (drawerScrim != null) {
            drawerScrim.setOpacity(0);
            drawerScrim.setVisible(false);
            drawerScrim.setManaged(false);
        }
        if (drawerHost != null) {
            drawerHost.setTranslateX(440);
            drawerHost.setVisible(false);
            drawerHost.setManaged(false);
        }
        focusBeforeDrawer = null;
    }

    private <T extends Node> void afterNavigation(Class<T> type, Consumer<T> action) {
        PauseTransition wait = new PauseTransition(Duration.millis(120));
        wait.setOnFinished(event -> {
            Node active = workspace.activeNode();
            if (type.isInstance(active)) action.accept(type.cast(active));
        });
        wait.play();
    }

    private void disposeShell() {
        if (drawerTransition != null) { drawerTransition.stop(); drawerTransition = null; }
        if (workspace != null) { workspace.close(); workspace = null; }
        if (drawerAddLinks != null) { drawerAddLinks.dispose(); drawerAddLinks = null; }
        if (throughput != null) { throughput.dispose(); throughput = null; }
        if (statusBar != null) { statusBar.dispose(); statusBar = null; }
        if (notificationOverlay != null) { notificationOverlay.dispose(); notificationOverlay = null; }
        if (globalSearch != null) { globalSearch.dispose(); globalSearch = null; }
        applicationName.unbind();
        shellDisposers.forEach(Runnable::run);
        shellDisposers.clear();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        copyRefresh.stop();
        activityNotificationDelay.stop();
        captureOverlayDelay.stop();
        if (drawerTransition != null) drawerTransition.stop();
        clipboardMonitor.stop();
        i18n.modeProperty().removeListener(languageListener);
        engine.settings().englishFunnyLevelProperty().removeListener(funnyListener);
        engine.settings().cantoneseFunnyLevelProperty().removeListener(funnyListener);
        disposeShell();
        externalEditors.close();
        notifications.close();
    }

    private static String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current instanceof java.util.concurrent.CompletionException) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName() : current.getMessage();
    }
}
