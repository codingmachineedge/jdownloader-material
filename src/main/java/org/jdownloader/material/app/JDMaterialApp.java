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
import javafx.beans.value.ChangeListener;
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
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProfileStore;
import org.jdownloader.material.appearance.ThemeMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.MainWindow;
import org.jdownloader.material.ui.ThemeManager;
import org.jdownloader.material.ui.appearance.AppearanceService;
import org.jdownloader.material.ui.appearance.AppearanceSettingsBridge;
import org.jdownloader.material.util.Formats;

/** JavaFX application entry point for JDownloader Material. */
public class JDMaterialApp extends Application {

    private DownloadEngine engine;
    private boolean engineShutdown;
    private AppearanceService appearance;
    private AppearanceSettingsBridge appearanceSettingsBridge;
    private ChangeListener<Boolean> appearanceThemeListener;
    private boolean syncingAppearanceTheme;

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

        MainWindow window;
        if (screenshotDir != null && !screenshotDir.isBlank()) {
            try {
                // Documentation capture never touches a person's real workspace repository.
                window = new MainWindow(engine, theme, stage, Files.createTempDirectory("jdm-workspace-capture-"));
            } catch (IOException e) {
                throw new IllegalStateException("Could not create an isolated capture workspace", e);
            }
        } else {
            window = new MainWindow(engine, theme, stage);
        }
        boolean documentationCapture = screenshotDir != null && !screenshotDir.isBlank();
        Scene scene = new Scene(window, documentationCapture ? 1440 : 1280,
                documentationCapture ? 900 : 800);
        theme.install(scene);
        installAppearance(scene, window, theme, appearanceStore(documentationCapture));

        stage.setScene(scene);
        stage.setMinWidth(880);
        stage.setMinHeight(560);
        loadIcon(stage);

        // Workspace tools can rename the display name without renaming the installed binary.
        stage.titleProperty().bind(Bindings.createStringBinding(() -> {
            if (engine.settings().speedInTitleProperty().get() && engine.globalSpeedProperty().get() > 0) {
                return window.applicationNameProperty().get() + " - ▼ "
                        + Formats.speed(engine.globalSpeedProperty().get());
            }
            return window.applicationNameProperty().get();
        }, window.applicationNameProperty(), engine.globalSpeedProperty(), engine.settings().speedInTitleProperty()));

        stage.setOnCloseRequest(e -> {
            closeAppearance(theme);
            window.dispose();
            shutdownEngine();
        });
        stage.show();

        if (screenshotDir != null && !screenshotDir.isBlank()) {
            captureDocumentation(stage, scene, window, theme, Path.of(screenshotDir));
            return;
        }

        // First-run disclosure and the opt-in 1% local dim-sum delight never
        // delay or take focus from the usable stage.
        javafx.application.Platform.runLater(window::showStartupSurprise);

        // Optional demo hook for opening a content page without a modal or
        // overlay. Documentation capture uses its own deterministic path.
        String demo = System.getenv("JD_DEMO");
        if (demo != null) {
            javafx.application.Platform.runLater(() -> {
                if ("linkgrabber".equalsIgnoreCase(demo)) {
                    window.showLinkGrabber();
                } else if ("settings".equalsIgnoreCase(demo)) {
                    window.showSettings();
                } else if ("history".equalsIgnoreCase(demo)) {
                    window.showHistory();
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
    private void captureDocumentation(Stage stage, Scene scene, MainWindow window,
                                      ThemeManager theme, Path directory) {
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
                new ScreenshotStep("downloads-status-light.png", window::showDownloadsWithActivityForCapture),
                new ScreenshotStep("downloads-properties-light.png", window::showDownloadsWithEditableSelection),
                new ScreenshotStep("linkgrabber-light.png", window::showLinkGrabber),
                new ScreenshotStep("history-light.png", window::showHistory),
                new ScreenshotStep("settings-light.png", window::showSettingsGeneralForCapture),
                new ScreenshotStep("settings-appearance-light.png", window::showSettingsAppearanceForCapture),
                new ScreenshotStep("changelog-light.png", window::showChangelogForCapture),
                new ScreenshotStep("plugins-bridge-light.png", window::showPluginsForCapture),
                new ScreenshotStep("add-links-light.png", window::showAddLinksForCapture),
                new ScreenshotStep("downloads-dark.png", () -> {
                    if (!theme.isDark()) theme.toggle();
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("downloads-properties-dark.png", window::showDownloadsWithEditableSelection),
                new ScreenshotStep("linkgrabber-dark.png", window::showLinkGrabber),
                new ScreenshotStep("history-dark.png", window::showHistory),
                new ScreenshotStep("settings-dark.png", window::showSettingsGeneralForCapture),
                new ScreenshotStep("add-links-dark.png", window::showAddLinksForCapture),
                new ScreenshotStep("settings-appearance-dark.png", window::showSettingsAppearanceForCapture),
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
                new ScreenshotStep("history-bilingual.png", window::showHistory),
                new ScreenshotStep("add-links-bilingual.png", window::showAddLinksForCapture),
                new ScreenshotStep("settings-appearance-bilingual.png", window::showSettingsAppearanceForCapture),
                new ScreenshotStep("notifications-bilingual.png", window::showNotificationsForCapture),
                new ScreenshotStep("downloads-bilingual-narrow.png", () -> {
                    stage.setWidth(880);
                    stage.setHeight(560);
                    window.showDownloadsForCapture();
                }),
                new ScreenshotStep("dim-sum-light.png", () -> {
                    stage.setWidth(1440);
                    stage.setHeight(900);
                    engine.settings().languageProperty().set(LanguageMode.ENGLISH);
                    window.showDimSumForCapture();
                }));
        captureStep(scene, window, steps, directory, 0);
    }

    private void captureStep(Scene scene, MainWindow window, List<ScreenshotStep> steps, Path directory, int index) {
        ScreenshotStep step = steps.get(index);
        window.prepareDocumentationCapture();
        window.clearActivityStatus();
        step.prepare().run();
        window.clearTransientFocus();
        // A content or language switch can replace much of the scene graph.
        // Force CSS/layout now and start the next step on a fresh UI turn so
        // snapshots never catch a partially repainted custom app bar.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        PauseTransition delay = new PauseTransition(Duration.millis(650));
        delay.setOnFinished(event -> {
            // Language changes rebuild the workspace asynchronously and may
            // produce fresh setup toasts after the initial reset. The
            // Notifications scene captures durable history rows, not a live
            // toast stack obscuring those rows, so every scene is purged.
            window.clearNotificationsForCapture();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            scene.snapshot(result -> {
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
            }, null);
        });
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
        // The close-request hook does not run for every programmatic JavaFX exit.
        closeAppearance(null);
        shutdownEngine();
    }

    private AppearanceProfileStore appearanceStore(boolean documentationCapture) {
        if (!documentationCapture) return AppearanceProfileStore.defaultStore();
        try {
            return new AppearanceProfileStore(Files.createTempDirectory("jdm-appearance-capture-")
                    .resolve("appearance.properties"));
        } catch (IOException error) {
            throw new IllegalStateException("Could not create an isolated capture appearance profile", error);
        }
    }

    private void installAppearance(Scene scene, MainWindow window, ThemeManager theme,
                                   AppearanceProfileStore profileStore) {
        I18n copy = new I18n(engine.settings().languageProperty(),
                engine.settings().englishFunnyLevelProperty(), engine.settings().cantoneseFunnyLevelProperty());
        AppearanceProfile initialProfile;
        try {
            boolean savedProfileExists = Files.isRegularFile(profileStore.file());
            initialProfile = profileStore.load();
            // Before the dedicated profile exists, preserve the app's already-persisted theme choice.
            if (!savedProfileExists) initialProfile.setTheme(theme.isDark() ? ThemeMode.DARK : ThemeMode.LIGHT);
        } catch (IOException invalidProfile) {
            window.notificationService().error(copy.text("appearance.load_failed"), safeMessage(invalidProfile));
            initialProfile = new AppearanceProfile();
            initialProfile.setTheme(theme.isDark() ? ThemeMode.DARK : ThemeMode.LIGHT);
        }
        appearance = new AppearanceService(profileStore, initialProfile);
        appearance.setPersistenceFailureHandler(error -> javafx.application.Platform.runLater(() ->
                window.notificationService().error(copy.text("appearance.persistence_failed"), safeMessage(error))));
        appearance.setGlobalChangeHandler(profile -> {
            syncingAppearanceTheme = true;
            try { theme.darkProperty().set(profile.theme() == ThemeMode.DARK); }
            finally { syncingAppearanceTheme = false; }
        });
        appearanceSettingsBridge = new AppearanceSettingsBridge(appearance, engine.settings(), error ->
                javafx.application.Platform.runLater(() -> window.notificationService().error(
                        copy.text("appearance.persistence_failed"), safeMessage(error))));
        appearance.install(scene, copy::text);
        appearanceThemeListener = (observable, previous, dark) -> {
            if (syncingAppearanceTheme || appearance == null) return;
            appearance.updateGlobal(profile -> profile.setTheme(dark ? ThemeMode.DARK : ThemeMode.LIGHT));
        };
        theme.darkProperty().addListener(appearanceThemeListener);
    }

    private void closeAppearance(ThemeManager theme) {
        if (theme != null && appearanceThemeListener != null) {
            theme.darkProperty().removeListener(appearanceThemeListener);
        }
        appearanceThemeListener = null;
        if (appearanceSettingsBridge != null) appearanceSettingsBridge.close();
        appearanceSettingsBridge = null;
        if (appearance != null) appearance.close();
        appearance = null;
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank() ? "Appearance profile operation failed." : message;
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
