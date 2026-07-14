package org.jdownloader.material.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.engine.SimulatedEngine;
import org.jdownloader.material.i18n.I18n;

/**
 * Headful scene-graph smoke for route coverage, accessibility, and compact-layout guarantees.
 * It deliberately uses the deterministic simulated engine and never starts a
 * network transfer or touches a person's profile data.
 */
public final class UiAccessibilitySmoke {

    private UiAccessibilitySmoke() {
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<MainWindow> windowRef = new AtomicReference<>();
        AtomicReference<SimulatedEngine> engineRef = new AtomicReference<>();
        AtomicReference<Scene> sceneRef = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                SimulatedEngine engine = new SimulatedEngine();
                engine.seedDemoData();
                Stage stage = new Stage(StageStyle.UNDECORATED);
                ThemeManager theme = new ThemeManager();
                MainWindow window = new MainWindow(engine, theme, stage);
                Scene scene = new Scene(window, 1440, 900);
                theme.install(scene);
                stage.setScene(scene);
                stage.show();
                stageRef.set(stage);
                windowRef.set(window);
                engineRef.set(engine);
                sceneRef.set(scene);
            } finally {
                ready.countDown();
            }
        });

        try {
            require(ready.await(8, TimeUnit.SECONDS), "JavaFX did not start");
            MainWindow window = windowRef.get();
            SimulatedEngine engine = engineRef.get();
            Scene scene = sceneRef.get();
            Stage stage = stageRef.get();
            require(window != null && engine != null && scene != null && stage != null,
                    "Smoke UI was not constructed");

            onFx(() -> {
                assertNamedIcons(scene);
                assertNamedNavigation(scene);
                I18n i18n = new I18n(engine.settings().languageProperty());
                assertRoutes(window, scene, i18n);
                window.showSettings();
                layout(scene);
                assertSettingsLabels(scene);

                engine.settings().languageProperty().set(LanguageMode.BILINGUAL);
                require(i18n.text("state.QUEUED").contains("\n"),
                        "Bilingual status chips must show both languages on separate lines");
                require("ETA".equals(i18n.text("column.eta")),
                        "The compact ETA table header should not duplicate an international abbreviation");

                stage.setWidth(880);
                stage.setHeight(560);
                stage.requestFocus();
                window.openAddLinks();
                return null;
            });

            // The drawer deliberately waits for its slide transition before moving focus.
            Thread.sleep(500);
            onFx(() -> {
                layout(scene);
                StackPane drawer = only(scene.getRoot(), ".add-links-drawer", StackPane.class);
                require(drawer.isVisible(), "Add Links drawer did not open at the minimum supported size");
                require(drawer.getAccessibleRole() == AccessibleRole.DIALOG,
                        "Add Links drawer is not exposed as a dialog");
                require(!blank(drawer.getAccessibleText()), "Add Links dialog has no accessible name");

                TextArea urls = only(drawer, ".links-area", TextArea.class);
                require(scene.getFocusOwner() == urls, "Add Links drawer did not move initial focus to URLs");
                Label status = only(drawer, ".row-desc", Label.class);
                require(status.isWrapText(), "Add Links status does not wrap bilingual feedback");
                require(status.getText().contains("\n"), "Bilingual Add Links status did not retain both languages");
                double parentWidth = status.getParent().getLayoutBounds().getWidth();
                require(status.getLayoutBounds().getWidth() <= parentWidth + 0.5,
                        "Add Links status overflows the compact drawer");

                KeyEvent escape = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false);
                urls.fireEvent(escape);
                return null;
            });

            Thread.sleep(350);
            onFx(() -> {
                StackPane drawer = only(scene.getRoot(), ".add-links-drawer", StackPane.class);
                require(!drawer.isVisible(), "Escape did not dismiss the Add Links dialog");
                return null;
            });

            System.out.println("UI accessibility and compact-layout smoke check passed");
        } finally {
            MainWindow window = windowRef.get();
            SimulatedEngine engine = engineRef.get();
            Stage stage = stageRef.get();
            if (window != null || engine != null || stage != null) {
                onFx(() -> {
                    if (window != null) window.dispose();
                    if (engine != null) engine.shutdown();
                    if (stage != null) stage.close();
                    return null;
                });
            }
            Platform.exit();
        }
    }

    private static void assertNamedIcons(Scene scene) {
        for (Node node : scene.getRoot().lookupAll(".icon-button")) {
            require(node instanceof Control, "Icon button is not a control");
            require(!blank(node.getAccessibleText()), "Icon button has no accessible name");
        }
    }

    private static void assertNamedNavigation(Scene scene) {
        int count = 0;
        for (Node node : scene.getRoot().lookupAll(".nav-item")) {
            require(node instanceof ToggleButton, "Primary navigation item is not a toggle button");
            require(!blank(node.getAccessibleText()), "Primary navigation item has no accessible name");
            count++;
        }
        require(count == 4, "Primary navigation did not expose every route");
    }

    private static void assertRoutes(MainWindow window, Scene scene, I18n i18n) {
        assertRoute(window::showDownloads, scene, i18n.text("downloads.title"));
        assertRoute(window::showLinkGrabber, scene, i18n.text("linkgrabber.title"));
        assertRoute(window::showHistory, scene, i18n.text("history.title"));
        assertRoute(window::showSettings, scene, i18n.text("settings.title"));
    }

    private static void assertRoute(Runnable showRoute, Scene scene, String expectedTitle) {
        showRoute.run();
        layout(scene);
        Label title = only(scene.getRoot(), ".page-title", Label.class);
        require(expectedTitle.equals(title.getText()), "Route title did not render: " + expectedTitle);
        double right = title.localToScene(title.getLayoutBounds()).getMaxX();
        require(right <= scene.getWidth() + 0.5, "Route title is clipped horizontally: " + expectedTitle);
    }

    private static void assertSettingsLabels(Scene scene) {
        int count = 0;
        for (Node node : scene.getRoot().lookupAll(".row-title")) {
            require(node instanceof Label, "Settings title is not a label");
            Label label = (Label) node;
            Node control = label.getLabelFor();
            require(control instanceof Control, "Settings label is not associated with a control: " + label.getText());
            require(!blank(control.getAccessibleText()), "Settings control has no accessible name: " + label.getText());
            require(!blank(control.getAccessibleHelp()), "Settings control has no accessible help: " + label.getText());
            count++;
        }
        require(count > 0, "No settings controls were found for accessibility verification");
    }

    private static <T extends Node> T only(Node root, String selector, Class<T> type) {
        return root.lookupAll(selector).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find " + selector));
    }

    private static void layout(Scene scene) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch complete = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                complete.countDown();
            }
        });
        if (!complete.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX action timed out");
        if (error.get() != null) {
            if (error.get() instanceof Exception exception) throw exception;
            if (error.get() instanceof Error fatal) throw fatal;
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
