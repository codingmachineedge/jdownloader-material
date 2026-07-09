package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.dialog.AddLinksPanel;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.SettingsView;

/**
 * Assembles the whole window: top app bar, navigation rail, content and status
 * bar in a shell, with a {@link NotificationCenter} overlay on top so all
 * feedback and former dialogs render as in-app notifications.
 */
public final class MainWindow extends StackPane {

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final NotificationCenter notifier = new NotificationCenter();
    private final StackPane content = new StackPane();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final Runnable openAddLinks;

    public MainWindow(DownloadEngine engine, ThemeManager theme) {
        this.engine = engine;
        this.theme = theme;

        // "View" snack actions navigate to the LinkGrabber; resolved after nav is built.
        Runnable[] navToLinkGrabber = {() -> { }};
        this.openAddLinks = () -> AddLinksPanel.open(notifier, engine, () -> navToLinkGrabber[0].run());

        Node downloads = new DownloadsView(engine, notifier, openAddLinks);
        Node linkgrabber = new LinkGrabberView(engine, notifier, openAddLinks);
        Node settings = new SettingsView(engine.settings(), notifier);

        ToggleButton downloadsTab = navItem("download", "Downloads", downloads);
        ToggleButton linkgrabberTab = navItem("link", "LinkGrabber", linkgrabber);
        ToggleButton settingsTab = navItem("settings", "Settings", settings);
        navToLinkGrabber[0] = () -> { linkgrabberTab.setSelected(true); content.getChildren().setAll(linkgrabber); };

        VBox rail = new VBox(6, downloadsTab, linkgrabberTab, Mat.hSpacer(), settingsTab);
        rail.getStyleClass().add("nav-rail");
        VBox.setVgrow(rail.getChildren().get(2), Priority.ALWAYS);

        BorderPane shell = new BorderPane();
        shell.setTop(buildTopAppBar());
        shell.setLeft(rail);
        shell.setCenter(content);
        shell.setBottom(new StatusBar(engine));

        content.getChildren().setAll(downloads);
        downloadsTab.setSelected(true);

        getChildren().addAll(shell, notifier);
    }

    /** Opens the in-app Add Links panel programmatically (also used for demos/tests). */
    public void openAddLinks() {
        openAddLinks.run();
    }

    /** Fires a representative snackbar (used by the demo hook and future tests). */
    public void demoSnack() {
        notifier.snack("3 links added to LinkGrabber", "View", () -> { });
    }

    // ------------------------------------------------------------- App bar
    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(Icons.of("download", 18, "icon-on-primary"));
        mark.getStyleClass().add("app-mark");
        Label title = Mat.label("JDownloader", "app-title");
        VBox titleBox = new VBox(-2, title);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        var clipboard = toggleIcon("paste", "Clipboard monitoring", engine.settings().clipboardMonitoringProperty());
        var autoReconnect = toggleIcon("reconnect", "Automatic reconnect", engine.settings().autoReconnectProperty());
        var reconnectNow = Mat.icon("cloud", "Reconnect now");
        reconnectNow.setOnAction(e -> {
            engine.reconnect();
            notifier.info("Reconnect", "Requesting a new IP address…");
        });

        var themeToggle = Mat.icon(theme.isDark() ? "sun" : "moon",
                theme.isDark() ? "Switch to light theme" : "Switch to dark theme");
        themeToggle.setOnAction(e -> {
            theme.toggle();
            themeToggle.setGraphic(Icons.of(theme.isDark() ? "sun" : "moon", 20));
            Mat.tip(themeToggle, theme.isDark() ? "Switch to light theme" : "Switch to dark theme");
        });

        HBox bar = new HBox(12, mark, titleBox, Mat.hSpacer(),
                clipboard, autoReconnect, reconnectNow, Mat.vSep(), themeToggle);
        bar.getStyleClass().add("top-app-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
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
            notifier.snack((prop.get() ? "Enabled" : "Disabled") + " " + tip.toLowerCase());
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
