package org.jdownloader.material.app;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.SnapshotResult;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.jdownloader.material.engine.DirectHttpEngine;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.engine.SimulatedEngine;
import org.jdownloader.material.ui.MainWindow;
import org.jdownloader.material.ui.ThemeManager;
import org.jdownloader.material.util.Formats;

/** JavaFX application entry point for JDownloader Material. */
public class JDMaterialApp extends Application {

    private DownloadEngine engine;
    private boolean engineShutdown;

    @Override
    public void start(Stage stage) {
        // The Material app bar supplies its own window controls. Suppress the
        // native title bar so it does not render as a duplicate strip above it.
        stage.initStyle(StageStyle.UNDECORATED);

        String screenshotDir = System.getenv("JD_SCREENSHOT_DIR");
        if (screenshotDir != null && !screenshotDir.isBlank()) {
            // Documentation needs stable, local sample rows and must never reach the network.
            SimulatedEngine sim = new SimulatedEngine();
            sim.seedDemoData();
            this.engine = sim;
        } else {
            this.engine = new DirectHttpEngine();
        }

        ThemeManager theme = new ThemeManager();
        // Keep the Appearance toggle and the app-bar toggle in lock-step.
        theme.darkProperty().bindBidirectional(engine.settings().darkThemeProperty());

        MainWindow window = new MainWindow(engine, theme, stage);
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
            shutdownEngine();
        });
        stage.show();

        if (screenshotDir != null && !screenshotDir.isBlank()) {
            captureDocumentation(scene, window, theme, Path.of(screenshotDir));
            return;
        }

        // Optional demo hook. JD_DEMO=panel opens the Add Links composer; JD_DEMO=snack
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
                } else if ("settings".equalsIgnoreCase(demo)) {
                    window.showSettings();
                } else {
                    window.openAddLinks();
                }
            });
        }
    }

    /**
     * Renders a repeatable documentation gallery when JD_SCREENSHOT_DIR is set.
     * This mode is intentionally opt-in so normal launches retain their interactive behavior.
     */
    private void captureDocumentation(Scene scene, MainWindow window, ThemeManager theme, Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create screenshot directory: " + directory, e);
        }
        // Keep public documentation images independent of the machine that generated them.
        engine.settings().downloadFolderProperty().set("C:\\Downloads");
        engine.settings().languageProperty().set(LanguageMode.ENGLISH);

        List<ScreenshotStep> steps = List.of(
                new ScreenshotStep("downloads-light.png", () -> {
                    if (theme.isDark()) theme.toggle();
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("downloads-properties-light.png", window::showDownloadsWithEditableSelection),
                new ScreenshotStep("linkgrabber-light.png", window::showLinkGrabber),
                new ScreenshotStep("settings-light.png", window::showSettings),
                new ScreenshotStep("settings-appearance-light.png", window::showSettingsAppearanceForCapture),
                new ScreenshotStep("add-links-light.png", window::openAddLinks),
                new ScreenshotStep("downloads-dark.png", () -> {
                    if (!theme.isDark()) theme.toggle();
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("downloads-properties-dark.png", window::showDownloadsWithEditableSelection),
                new ScreenshotStep("linkgrabber-dark.png", window::showLinkGrabber),
                new ScreenshotStep("settings-dark.png", window::showSettings),
                new ScreenshotStep("add-links-dark.png", window::openAddLinks),
                new ScreenshotStep("downloads-cantonese.png", () -> {
                    if (theme.isDark()) theme.toggle();
                    engine.settings().languageProperty().set(LanguageMode.HONG_KONG_CANTONESE);
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("linkgrabber-cantonese.png", window::showLinkGrabber),
                new ScreenshotStep("downloads-bilingual.png", () -> {
                    engine.settings().languageProperty().set(LanguageMode.BILINGUAL);
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("add-links-bilingual.png", window::openAddLinks),
                new ScreenshotStep("settings-appearance-bilingual.png", window::showSettingsAppearanceForCapture));
        captureStep(scene, window, steps, directory, 0);
    }

    private void captureStep(Scene scene, MainWindow window, List<ScreenshotStep> steps, Path directory, int index) {
        ScreenshotStep step = steps.get(index);
        step.prepare().run();
        window.clearTransientFocus();
        // A content or language switch can replace much of the scene graph.
        // Force CSS/layout now and start the next step on a fresh UI turn so
        // snapshots never catch a partially repainted custom app bar.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        PauseTransition delay = new PauseTransition(Duration.millis(350));
        delay.setOnFinished(event -> scene.snapshot(result -> {
            try {
                writePng(result, directory.resolve(step.fileName()));
                if (index + 1 < steps.size()) {
                    javafx.application.Platform.runLater(() ->
                            captureStep(scene, window, steps, directory, index + 1));
                } else {
                    System.out.println("Wrote " + steps.size() + " documentation screenshots to " + directory);
                    window.dispose();
                    javafx.application.Platform.exit();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Could not write documentation screenshot", e);
            }
            return null;
        }, null));
        delay.play();
    }

    private void writePng(SnapshotResult result, Path output) throws IOException {
        WritableImage image = result.getImage();
        int width = (int) Math.ceil(image.getWidth());
        int height = (int) Math.ceil(image.getHeight());
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(buffered, "png", output.toFile());
    }

    private record ScreenshotStep(String fileName, Runnable prepare) {
    }

    private void loadIcon(Stage stage) {
        try (var in = getClass().getResourceAsStream("/icons/app.png")) {
            if (in != null) stage.getIcons().add(new Image(in));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void stop() {
        shutdownEngine();
    }

    private void shutdownEngine() {
        if (engineShutdown || engine == null) return;
        engineShutdown = true;
        engine.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
