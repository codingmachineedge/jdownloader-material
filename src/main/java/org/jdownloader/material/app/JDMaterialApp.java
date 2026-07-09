package org.jdownloader.material.app;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.engine.SimulatedEngine;
import org.jdownloader.material.ui.MainWindow;
import org.jdownloader.material.ui.ThemeManager;
import org.jdownloader.material.util.Formats;

/** JavaFX application entry point for JDownloader Material. */
public class JDMaterialApp extends Application {

    private DownloadEngine engine;

    @Override
    public void start(Stage stage) {
        SimulatedEngine sim = new SimulatedEngine();
        sim.seedDemoData();
        this.engine = sim;

        ThemeManager theme = new ThemeManager();
        // Keep the Appearance toggle and the app-bar toggle in lock-step.
        theme.darkProperty().bindBidirectional(engine.settings().darkThemeProperty());

        MainWindow window = new MainWindow(engine, theme);
        Scene scene = new Scene(window, 1180, 720);
        theme.install(scene);

        stage.setScene(scene);
        stage.setMinWidth(880);
        stage.setMinHeight(560);
        loadIcon(stage);

        stage.titleProperty().bind(Bindings.createStringBinding(() -> {
            if (engine.settings().speedInTitleProperty().get() && engine.globalSpeedProperty().get() > 0) {
                return "JDownloader Material — ▼ " + Formats.speed(engine.globalSpeedProperty().get());
            }
            return "JDownloader Material";
        }, engine.globalSpeedProperty(), engine.settings().speedInTitleProperty()));

        stage.setOnCloseRequest(e -> {
            window.dispose();
            engine.shutdown();
        });
        stage.show();

        // Optional demo hook. JD_DEMO=panel opens the Add Links panel; JD_DEMO=snack
        // re-shows a snackbar on a timer so it can be observed/screenshotted.
        String demo = System.getenv("JD_DEMO");
        if (demo != null) {
            javafx.application.Platform.runLater(() -> {
                if ("snack".equalsIgnoreCase(demo)) {
                    var timeline = new javafx.animation.Timeline(
                            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> window.demoSnack()),
                            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), e -> window.demoSnack()));
                    timeline.setCycleCount(6);
                    timeline.play();
                } else if ("linkgrabber".equalsIgnoreCase(demo)) {
                    window.showLinkGrabber();
                } else {
                    window.openAddLinks();
                }
            });
        }
    }

    private void loadIcon(Stage stage) {
        try (var in = getClass().getResourceAsStream("/icons/app.png")) {
            if (in != null) stage.getIcons().add(new Image(in));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        if (engine != null) engine.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
