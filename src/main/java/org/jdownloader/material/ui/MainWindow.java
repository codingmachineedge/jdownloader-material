package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.component.ClipboardMonitor;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.view.AddLinksView;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.SettingsView;
import org.jdownloader.material.util.Formats;

/**
 * Assembles the whole window: top app bar, navigation rail, content and status
 * bar in a shell, with one optional {@link NotificationCenter} snackbar lane
 * for Undo and navigation actions. Forms stay in normal content views.
 */
public final class MainWindow extends StackPane {

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final Stage stage;
    private final NotificationCenter notifier = new NotificationCenter();
    private final StackPane content = new StackPane();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Runnable openAddLinks;
    private final Runnable showDownloads;
    private final Runnable showLinkGrabber;
    private final Runnable showSettings;
    private final ClipboardMonitor clipboardMonitor;
    private double dragOffsetX;
    private double dragOffsetY;

    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage) {
        this.engine = engine;
        this.theme = theme;
        this.stage = stage;

        // "View" snack actions navigate to the LinkGrabber; resolved after nav is built.
        Runnable[] navToLinkGrabber = {() -> { }};
        Runnable[] openComposer = {() -> { }};

        Node downloads = new DownloadsView(engine, notifier, () -> openComposer[0].run());
        Node linkgrabber = new LinkGrabberView(engine, notifier, () -> openComposer[0].run());
        Node settings = new SettingsView(engine.settings());

        ToggleButton downloadsTab = navItem("download", "Downloads", downloads);
        ToggleButton linkgrabberTab = navItem("link", "LinkGrabber", linkgrabber);
        ToggleButton settingsTab = navItem("settings", "Settings", settings);
        this.showDownloads = () -> { downloadsTab.setSelected(true); content.getChildren().setAll(downloads); };
        navToLinkGrabber[0] = () -> { linkgrabberTab.setSelected(true); content.getChildren().setAll(linkgrabber); };
        this.showLinkGrabber = navToLinkGrabber[0];
        this.showSettings = () -> { settingsTab.setSelected(true); content.getChildren().setAll(settings); };
        Node addLinks = new AddLinksView(engine, showDownloads, showLinkGrabber);
        this.openAddLinks = () -> content.getChildren().setAll(addLinks);
        openComposer[0] = this.openAddLinks;

        VBox rail = new VBox(6, downloadsTab, linkgrabberTab, Mat.hSpacer(), settingsTab);
        rail.getStyleClass().add("nav-rail");
        VBox.setVgrow(rail.getChildren().get(2), Priority.ALWAYS);

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTopAppBar());
        shell.setLeft(rail);
        shell.setCenter(content);
        shell.setBottom(new StatusBar(engine));

        showDownloads.run();

        getChildren().addAll(shell, notifier);

        // Clipboard capture offers one optional View action; no work opens a dialog.
        clipboardMonitor = new ClipboardMonitor(engine, notifier, () -> navToLinkGrabber[0].run());
        clipboardMonitor.start();
    }

    /** Stops timers owned by the window (called on application shutdown). */
    public void dispose() {
        clipboardMonitor.stop();
    }

    /** Opens the inline Add Links composer programmatically (also used for demos/tests). */
    public void openAddLinks() {
        openAddLinks.run();
    }

    /** Switches to the LinkGrabber view programmatically (also used for demos/tests). */
    public void showLinkGrabber() {
        showLinkGrabber.run();
    }

    /** Switches to Downloads programmatically (also used for demos/tests). */
    public void showDownloads() {
        showDownloads.run();
    }

    /** Switches to Settings programmatically (also used for demos/tests). */
    public void showSettings() {
        showSettings.run();
    }

    /** Fires a representative snackbar (used by the demo hook and future tests). */
    public void demoSnack() {
        notifier.snack("3 links added to LinkGrabber", "View", () -> { });
    }

    /** Clears transient overlay content before a deterministic visual capture. */
    public void clearNotifications() {
        notifier.clear();
    }

    // ------------------------------------------------------------- App bar
    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(Icons.of("download", 18, "icon-on-primary"));
        mark.getStyleClass().add("app-mark");
        Label title = Mat.label("JDownloader", "app-title");
        title.textProperty().bind(Bindings.createStringBinding(() -> {
            if (engine.settings().speedInTitleProperty().get() && engine.globalSpeedProperty().get() > 0) {
                return "JDownloader  -  " + Formats.speed(engine.globalSpeedProperty().get());
            }
            return "JDownloader";
        }, engine.globalSpeedProperty(), engine.settings().speedInTitleProperty()));
        VBox titleBox = new VBox(-2, title);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        var clipboard = toggleIcon("paste", "Clipboard monitoring", engine.settings().clipboardMonitoringProperty());
        var autoReconnect = toggleIcon("reconnect", "Automatic reconnect", engine.settings().autoReconnectProperty());
        var reconnectNow = Mat.icon("cloud", "Reconnect now");
        reconnectNow.setOnAction(e -> engine.reconnect());

        var themeToggle = Mat.icon(theme.isDark() ? "sun" : "moon",
                theme.isDark() ? "Switch to light theme" : "Switch to dark theme");
        Runnable updateThemeToggle = () -> {
            themeToggle.setGraphic(Icons.of(theme.isDark() ? "sun" : "moon", 20));
            Mat.tip(themeToggle, theme.isDark() ? "Switch to light theme" : "Switch to dark theme");
        };
        themeToggle.setOnAction(e -> theme.toggle());
        theme.darkProperty().addListener((o, wasDark, isDark) -> updateThemeToggle.run());

        var minimize = Mat.icon("minimize", "Minimize");
        minimize.getStyleClass().add("window-control");
        minimize.setOnAction(e -> stage.setIconified(true));

        var maximize = Mat.icon("maximize", "Maximize");
        maximize.getStyleClass().add("window-control");
        maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        stage.maximizedProperty().addListener((o, wasMaximized, isMaximized) ->
                updateMaximizeControl(maximize, isMaximized));

        var close = Mat.icon("close", "Close");
        close.getStyleClass().addAll("window-control", "window-close");
        close.setOnAction(e -> stage.close());

        HBox bar = new HBox(12, mark, titleBox, Mat.hSpacer(),
                clipboard, autoReconnect, reconnectNow, Mat.vSep(), themeToggle, Mat.vSep(),
                minimize, maximize, close);
        bar.getStyleClass().add("top-app-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        installWindowDragging(bar);
        return bar;
    }

    private void updateMaximizeControl(MFXButton button, boolean maximized) {
        button.setGraphic(Icons.of(maximized ? "restore" : "maximize", 20));
        Mat.tip(button, maximized ? "Restore" : "Maximize");
    }

    /** Lets the app bar replace the native title bar without losing window movement. */
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
                    && !targetsAControl(event.getTarget())) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    private boolean targetsAControl(Object target) {
        for (Node node = target instanceof Node n ? n : null; node != null; node = node.getParent()) {
            if (node instanceof ButtonBase || node instanceof MFXButton) return true;
        }
        return false;
    }

    /** A 40dp icon button that reflects and toggles a boolean setting. */
    private MFXButton toggleIcon(String icon, String tip, BooleanProperty prop) {
        MFXButton b = Mat.icon(icon, tip);
        Runnable restyle = () -> {
            b.getStyleClass().remove("active");
            if (prop.get()) b.getStyleClass().add("active");
        };
        restyle.run();
        b.setOnAction(e -> {
            prop.set(!prop.get());
            restyle.run();
        });
        prop.addListener((o, a, v) -> restyle.run());
        return b;
    }

    // -------------------------------------------------------------- Nav rail
    private ToggleButton navItem(String icon, String text, Node page) {
        StackPane glyph = new StackPane(Icons.of(icon, 22));
        glyph.getStyleClass().add("nav-glyph");
        Label label = new Label(text);
        label.getStyleClass().add("nav-label");
        VBox v = new VBox(4, glyph, label);
        v.setAlignment(Pos.CENTER);

        ToggleButton tb = new ToggleButton();
        tb.setGraphic(v);
        tb.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        tb.getStyleClass().add("nav-item");
        tb.setToggleGroup(navGroup);
        tb.setUserData(page);
        tb.setOnAction(e -> {
            tb.setSelected(true); // never allow deselect-to-empty
            content.getChildren().setAll(page);
        });
        return tb;
    }
}
