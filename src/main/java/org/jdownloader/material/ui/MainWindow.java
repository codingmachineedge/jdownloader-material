package org.jdownloader.material.ui;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
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
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.ClipboardMonitor;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.ui.component.StatusBar;
import org.jdownloader.material.ui.view.AddLinksView;
import org.jdownloader.material.ui.view.DownloadsView;
import org.jdownloader.material.ui.view.LinkGrabberView;
import org.jdownloader.material.ui.view.SettingsView;
import org.jdownloader.material.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the whole window: top app bar, navigation rail, content and status
 * bar in a shell. Rebuilding the shell when the language changes keeps every
 * static label in sync without a restart or a modal language prompt.
 */
public final class MainWindow extends StackPane {

    private enum Page { DOWNLOADS, LINKGRABBER, SETTINGS, ADD_LINKS }

    private final DownloadEngine engine;
    private final ThemeManager theme;
    private final Stage stage;
    private final I18n i18n;
    private final NotificationCenter notifier = new NotificationCenter();
    private final ClipboardMonitor clipboardMonitor;
    private final List<Runnable> shellDisposers = new ArrayList<>();
    private final ChangeListener<LanguageMode> languageListener = (observable, previous, current) -> rebuildShell();

    private StackPane content;
    private ToggleGroup navGroup;
    private DownloadsView downloadsView;
    private LinkGrabberView linkgrabberView;
    private SettingsView settingsView;
    private AddLinksView addLinksView;
    private ToggleButton downloadsTab;
    private ToggleButton linkgrabberTab;
    private ToggleButton settingsTab;
    private Page currentPage = Page.DOWNLOADS;
    private double dragOffsetX;
    private double dragOffsetY;

    public MainWindow(DownloadEngine engine, ThemeManager theme, Stage stage) {
        this.engine = engine;
        this.theme = theme;
        this.stage = stage;
        this.i18n = new I18n(engine.settings().languageProperty());
        setFocusTraversable(true);

        i18n.modeProperty().addListener(languageListener);
        rebuildShell();

        // Clipboard capture offers one optional View action; no work opens a dialog.
        clipboardMonitor = new ClipboardMonitor(engine, notifier, this::showLinkGrabber, i18n);
        clipboardMonitor.start();
    }

    private void rebuildShell() {
        AddLinksView.Draft addLinksDraft = addLinksView == null ? null : addLinksView.draft();
        String selectedSettingsTab = settingsView == null ? null : settingsView.selectedTabKey();
        disposeShell();
        content = new StackPane();
        navGroup = new ToggleGroup();

        downloadsView = new DownloadsView(engine, notifier, this::openAddLinks, i18n);
        linkgrabberView = new LinkGrabberView(engine, notifier, this::openAddLinks, i18n);
        settingsView = new SettingsView(engine.settings(), i18n, selectedSettingsTab);
        addLinksView = new AddLinksView(engine, this::showDownloads, this::showLinkGrabber, i18n);
        addLinksView.restoreDraft(addLinksDraft);

        downloadsTab = navItem("download", "nav.downloads", downloadsView);
        linkgrabberTab = navItem("link", "nav.linkgrabber", linkgrabberView);
        settingsTab = navItem("settings", "nav.settings", settingsView);

        VBox rail = new VBox(6, downloadsTab, linkgrabberTab, Mat.hSpacer(), settingsTab);
        rail.getStyleClass().add("nav-rail");
        VBox.setVgrow(rail.getChildren().get(2), Priority.ALWAYS);

        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("app-shell");
        shell.setTop(buildTopAppBar());
        shell.setLeft(rail);
        shell.setCenter(content);
        shell.setBottom(new StatusBar(engine, i18n));

        getChildren().setAll(shell, notifier);
        show(currentPage);
    }

    /** Stops timers owned by the window (called on application shutdown). */
    public void dispose() {
        clipboardMonitor.stop();
        i18n.modeProperty().removeListener(languageListener);
        disposeShell();
    }

    /** Removes listeners attached by the language-specific shell before replacing it. */
    private void disposeShell() {
        if (downloadsView != null) downloadsView.dispose();
        if (linkgrabberView != null) linkgrabberView.dispose();
        if (addLinksView != null) addLinksView.dispose();
        downloadsView = null;
        linkgrabberView = null;
        settingsView = null;
        addLinksView = null;
        shellDisposers.forEach(Runnable::run);
        shellDisposers.clear();
    }

    /** Moves focus off transient form controls before deterministic scene capture. */
    public void clearTransientFocus() {
        requestFocus();
    }

    /** Opens the inline Add Links composer programmatically (also used for demos/tests). */
    public void openAddLinks() {
        show(Page.ADD_LINKS);
    }

    /** Switches to the LinkGrabber view programmatically (also used for demos/tests). */
    public void showLinkGrabber() {
        show(Page.LINKGRABBER);
    }

    /** Switches to Downloads programmatically (also used for demos/tests). */
    public void showDownloads() {
        show(Page.DOWNLOADS);
    }

    /** Shows the standard unselected Downloads view for documentation capture. */
    public void showDownloadsForCapture() {
        show(Page.DOWNLOADS);
        downloadsView.clearSelectionForCapture();
    }

    /** Shows Downloads with an editable sample row selected for documentation capture. */
    public void showDownloadsWithEditableSelection() {
        show(Page.DOWNLOADS);
        downloadsView.selectFirstEditableForCapture();
    }

    /** Switches to Settings programmatically (also used for demos/tests). */
    public void showSettings() {
        show(Page.SETTINGS);
    }

    /** Shows Appearance for a deterministic capture of the language picker. */
    public void showSettingsAppearanceForCapture() {
        show(Page.SETTINGS);
        settingsView.showAppearanceForCapture();
    }

    /** Fires a representative snackbar (used by the demo hook and future tests). */
    public void demoSnack() {
        notifier.snack(i18n.text("demo.snack"), i18n.text("action.view"), () -> { });
    }

    /** Clears transient overlay content before a deterministic visual capture. */
    public void clearNotifications() {
        notifier.clear();
    }

    private void show(Page page) {
        currentPage = page;
        switch (page) {
            case DOWNLOADS -> {
                downloadsTab.setSelected(true);
                content.getChildren().setAll(downloadsView);
            }
            case LINKGRABBER -> {
                linkgrabberTab.setSelected(true);
                content.getChildren().setAll(linkgrabberView);
            }
            case SETTINGS -> {
                settingsTab.setSelected(true);
                content.getChildren().setAll(settingsView);
            }
            case ADD_LINKS -> content.getChildren().setAll(addLinksView);
        }
    }

    // ------------------------------------------------------------- App bar
    private HBox buildTopAppBar() {
        StackPane mark = new StackPane(Icons.of("download", 18, "icon-on-primary"));
        mark.getStyleClass().add("app-mark");
        Label title = Mat.label(i18n.text("app.title"), "app-title");
        var titleBinding = Bindings.createStringBinding(() -> {
            if (engine.settings().speedInTitleProperty().get() && engine.globalSpeedProperty().get() > 0) {
                return i18n.text("app.title") + "  —  ▼ " + Formats.speed(engine.globalSpeedProperty().get());
            }
            return i18n.text("app.title");
        }, engine.globalSpeedProperty(), engine.settings().speedInTitleProperty(), i18n.modeProperty());
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
        themeToggle.setOnAction(e -> theme.toggle());
        ChangeListener<Boolean> themeListener = (o, wasDark, isDark) -> updateThemeToggle.run();
        theme.darkProperty().addListener(themeListener);
        shellDisposers.add(() -> theme.darkProperty().removeListener(themeListener));

        var minimize = Mat.icon("minimize", i18n.text("window.minimize"));
        minimize.getStyleClass().add("window-control");
        minimize.setOnAction(e -> stage.setIconified(true));

        var maximize = Mat.icon("maximize", i18n.text("window.maximize"));
        maximize.getStyleClass().add("window-control");
        maximize.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        ChangeListener<Boolean> maximizedListener = (o, wasMaximized, isMaximized) ->
                updateMaximizeControl(maximize, isMaximized);
        stage.maximizedProperty().addListener(maximizedListener);
        shellDisposers.add(() -> stage.maximizedProperty().removeListener(maximizedListener));
        updateMaximizeControl(maximize, stage.isMaximized());

        var close = Mat.icon("close", i18n.text("window.close"));
        close.getStyleClass().addAll("window-control", "window-close");
        close.setOnAction(e -> stage.close());

        HBox bar = new HBox(12, mark, titleBox, Mat.hSpacer(),
                clipboard, autoReconnect, Mat.vSep(), themeToggle, Mat.vSep(),
                minimize, maximize, close);
        bar.getStyleClass().add("top-app-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        installWindowDragging(bar);
        return bar;
    }

    private void updateMaximizeControl(MFXButton button, boolean maximized) {
        button.setGraphic(Icons.of(maximized ? "restore" : "maximize", 20));
        Mat.tip(button, i18n.text(maximized ? "window.restore" : "window.maximize"));
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

    /** A labelled app-bar action that reflects and toggles a boolean setting. */
    private MFXButton toggleAction(String icon, String textKey, String tipKey, BooleanProperty prop) {
        MFXButton button = Mat.text(i18n.text(textKey), icon);
        Mat.tip(button, i18n.text(tipKey));
        Runnable restyle = () -> {
            button.getStyleClass().remove("active");
            if (prop.get()) button.getStyleClass().add("active");
        };
        restyle.run();
        button.setOnAction(e -> {
            prop.set(!prop.get());
            restyle.run();
        });
        ChangeListener<Boolean> propertyListener = (o, a, v) -> restyle.run();
        prop.addListener(propertyListener);
        shellDisposers.add(() -> prop.removeListener(propertyListener));
        return button;
    }

    // -------------------------------------------------------------- Nav rail
    private ToggleButton navItem(String icon, String textKey, Node page) {
        StackPane glyph = new StackPane(Icons.of(icon, 22));
        glyph.getStyleClass().add("nav-glyph");
        Label label = new Label(i18n.text(textKey));
        label.getStyleClass().add("nav-label");
        label.setWrapText(true);
        label.setMaxWidth(84);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        VBox v = new VBox(4, glyph, label);
        v.setAlignment(Pos.CENTER);

        ToggleButton tab = new ToggleButton();
        tab.setGraphic(v);
        tab.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        tab.getStyleClass().add("nav-item");
        tab.setToggleGroup(navGroup);
        tab.setUserData(page);
        tab.setOnAction(e -> {
            tab.setSelected(true); // never allow deselect-to-empty
            if (page == downloadsView) show(Page.DOWNLOADS);
            else if (page == linkgrabberView) show(Page.LINKGRABBER);
            else show(Page.SETTINGS);
        });
        return tab;
    }
}
