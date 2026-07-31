package org.jdownloader.material.ui.appearance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProfileStore;
import org.jdownloader.material.appearance.AppearanceProperty;
import org.jdownloader.material.appearance.AppearanceState;
import org.jdownloader.material.appearance.AppearanceTargetId;
import org.jdownloader.material.appearance.Density;
import org.jdownloader.material.appearance.ThemeMode;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.search.SearchMode;
import org.jdownloader.material.ui.search.SearchField;

/** Headful smoke for live CSS application, true reset behavior and the anchored non-modal editor. */
public final class AppearanceUiSmoke {

    private AppearanceUiSmoke() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger assertions = new AtomicInteger();
        AtomicReference<Path> temporary = new AtomicReference<>();

        Platform.startup(() -> { });
        Platform.runLater(() -> {
            AppearanceService service = null;
            AppearanceSettingsBridge settingsBridge = null;
            Stage host = null;
            try {
                Path directory = Files.createTempDirectory("appearance-ui-smoke-");
                temporary.set(directory);
                Label label = new Label("Appearance target");
                label.setId("sample-label");
                TextField field = new TextField("Editable");
                Button button = new Button("Action");
                VBox root = new VBox(12, label, field, button);
                root.setId("appearance-smoke-root");
                Scene scene = new Scene(root, 640, 420);
                host = new Stage();
                host.setScene(scene);
                host.show();

                service = new AppearanceService(
                        new AppearanceProfileStore(directory.resolve("appearance.properties")),
                        new AppearanceProfile());
                Settings settings = new Settings();
                AtomicInteger payloadChanges = new AtomicInteger();
                settings.appearanceProfilePayloadProperty().addListener((observable, previous, current) ->
                        payloadChanges.incrementAndGet());
                settingsBridge = new AppearanceSettingsBridge(service, settings,
                        error -> { throw new AssertionError("appearance settings bridge failed", error); });
                AtomicInteger globalChanges = new AtomicInteger();
                service.setGlobalChangeHandler(profile -> globalChanges.incrementAndGet());
                AppearanceRegistry registry = service.install(scene);
                root.applyCss();
                root.layout();

                check(scene.getStylesheets().stream().anyMatch(value -> value.endsWith("appearance.css")),
                        "appearance stylesheet was not installed", assertions);
                AppearanceTargetId target = registry.registerTarget(label,
                        AppearanceTargetId.of("smoke.sample-label"));
                check(target.equals(registry.targetId(label)), "explicit target id is not stable", assertions);
                check(label.getProperties().get(AppearanceRegistry.TARGET_PROPERTY).equals(target),
                        "target id was not exposed on the node", assertions);

                double inheritedSize = label.getFont().getSize();
                service.update(target, AppearanceState.NORMAL,
                        style -> style.set(AppearanceProperty.FONT_SIZE, 22));
                root.applyCss();
                near(22, label.getFont().getSize(), 0.1, "target font size did not render", assertions);
                service.resetProperty(target, AppearanceState.NORMAL, AppearanceProperty.FONT_SIZE);
                root.applyCss();
                near(inheritedSize, label.getFont().getSize(), 0.1,
                        "property reset did not restore the inherited font size", assertions);

                service.update(target, AppearanceState.NORMAL,
                        style -> style.set(AppearanceProperty.WIDTH, 260));
                near(260, label.getPrefWidth(), 0, "target width did not render", assertions);
                service.resetProperty(target, AppearanceState.NORMAL, AppearanceProperty.WIDTH);
                check(label.getPrefWidth() == javafx.scene.layout.Region.USE_COMPUTED_SIZE,
                        "geometry reset did not restore the original computed width", assertions);

                service.updateGlobal(profile -> {
                    profile.setTheme(ThemeMode.DARK);
                    profile.setDensity(Density.COMFORTABLE);
                    profile.setFontSizeScale(1.15);
                });
                root.applyCss();
                check(root.getStyleClass().contains("appearance-density-comfortable"),
                        "density class did not update live", assertions);
                check(root.getPseudoClassStates().stream().anyMatch(value -> "appearance-dark".equals(value.getPseudoClassName())),
                        "dark appearance pseudo-class did not update live", assertions);
                check(globalChanges.get() >= 2, "global-change bridge was not notified", assertions);
                check(root.getStyle().contains("-md-primary"),
                        "target refresh erased global Material color roles", assertions);

                VBox utilityRoot = new VBox(new Label("Utility decision"));
                Scene utilityScene = new Scene(utilityRoot, 320, 180);
                check(AppearanceRegistry.attachSceneFor(label, utilityScene),
                        "owner-aware utility Scene did not attach", assertions);
                check(utilityRoot.getStyle().contains("-md-primary"),
                        "attached utility Scene did not inherit global appearance", assertions);
                check(AppearanceRegistry.detachSceneFor(label, utilityScene),
                        "owner-aware utility Scene did not detach", assertions);
                check(!AppearanceRegistry.openEditorFor(utilityRoot),
                        "detached utility Scene retained appearance editing hooks", assertions);

                int changesBeforeBridgeCheck = payloadChanges.get();
                service.update(target, AppearanceState.NORMAL,
                        style -> style.set(AppearanceProperty.FONT_SIZE, 21));
                check(payloadChanges.get() == changesBeforeBridgeCheck + 1,
                        "appearance mutation was not bridged into Settings", assertions);
                String historicalPayload = settings.appearanceProfilePayloadProperty().get();
                int afterFirstPayload = payloadChanges.get();
                service.update(target, AppearanceState.NORMAL,
                        style -> style.set(AppearanceProperty.FONT_SIZE, 21));
                check(payloadChanges.get() == afterFirstPayload,
                        "identical appearance payload emitted a no-op Settings revision", assertions);
                service.update(target, AppearanceState.NORMAL,
                        style -> style.set(AppearanceProperty.FONT_SIZE, 23));
                check(payloadChanges.get() == afterFirstPayload + 1,
                        "second semantic appearance mutation was not bridged", assertions);
                settings.setAppearanceProfilePayload(historicalPayload);
                near(21, service.explicitStyleFor(target, AppearanceState.NORMAL)
                                .number(AppearanceProperty.FONT_SIZE).orElseThrow(), 0,
                        "Settings restore did not reapply the historical appearance profile", assertions);

                registry.openEditor(label);
                Stage hostStage = host;
                Window editor = Window.getWindows().stream()
                        .filter(window -> window != hostStage && window.isShowing() && window.getScene() != null)
                        .filter(window -> window.getScene().getRoot().getStyleClass().contains("appearance-editor"))
                        .findFirst().orElseThrow(() -> new AssertionError("appearance editor did not open"));
                editor.getScene().getRoot().applyCss();
                editor.getScene().getRoot().layout();
                check(editor instanceof Stage stage && stage.getModality() == Modality.NONE,
                        "appearance editor is blocking instead of non-modal", assertions);
                check(((Stage) editor).getOwner() == host,
                        "appearance editor is not anchored to the host window", assertions);
                check(editor.getScene().getRoot().getAccessibleRole() == AccessibleRole.DIALOG,
                        "appearance editor lacks its dialog accessibility role", assertions);
                check(editor.getScene().lookup(".infinite-color-picker") != null,
                        "infinite color picker is missing from the editor", assertions);
                check(editor.getScene().lookup(".font-picker") != null,
                        "font picker is missing from the editor", assertions);
                check(editor.getScene().getRoot().getProperties().containsKey(AppearanceRegistry.TARGET_PROPERTY),
                        "appearance editor cannot target its own chrome", assertions);

                Set<Node> searchSurfaces = editor.getScene().getRoot().lookupAll(".appearance-surface-search");
                check(searchSurfaces.size() == 4,
                        "editor, font picker, and both color pickers need four independent searches", assertions);
                AppearanceSurfaceSearch editorSearch = (AppearanceSurfaceSearch) editor.getScene()
                        .lookup(".appearance-editor-surface-search");
                AppearanceSurfaceSearch fontSearch = (AppearanceSurfaceSearch) editor.getScene()
                        .lookup(".appearance-font-surface-search");
                List<AppearanceSurfaceSearch> colorSearches = editor.getScene().getRoot()
                        .lookupAll(".appearance-color-surface-search").stream()
                        .map(AppearanceSurfaceSearch.class::cast).toList();
                check(editorSearch != null && fontSearch != null && colorSearches.size() == 2,
                        "appearance search surfaces were not mounted", assertions);

                editorSearch.searchField().expressionProperty().set("font");
                check(!editorSearch.resultDisplays().isEmpty(),
                        "overall editor search did not index labels and current values", assertions);
                check(fontSearch.searchField().expressionProperty().get().isEmpty()
                                && colorSearches.stream().allMatch(search ->
                                search.searchField().expressionProperty().get().isEmpty()),
                        "appearance searches leaked hidden query state between surfaces", assertions);

                fontSearch.searchField().expressionProperty().set("Segoe");
                check(fontSearch.resultDisplays().stream().anyMatch(value -> value.contains("Segoe")),
                        "font search did not filter installed/current font values", assertions);
                colorSearches.getFirst().searchField().expressionProperty().set("OKLCH");
                check(colorSearches.getFirst().resultDisplays().stream().anyMatch(value -> value.contains("OKLCH")),
                        "color search did not index translated color-space values", assertions);

                editorSearch.searchField().modeProperty().set(SearchMode.REGEX);
                editorSearch.searchField().expressionProperty().set("(");
                check(!editorSearch.searchField().validation().valid(),
                        "invalid editor regex was not retained as invalid", assertions);
                Label inlineValidation = (Label) editorSearch.lookup(".appearance-search-status");
                check(inlineValidation != null && inlineValidation.getStyleClass().contains("invalid")
                                && !inlineValidation.getText().isBlank(),
                        "invalid appearance regex did not remain visible inline", assertions);

                editorSearch.searchField().expressionProperty().set("font|color");
                editorSearch.searchField().showBuilder();
                check(editorSearch.searchField().builderPopover().isShowing(),
                        "overall appearance search did not open its anchored full regex builder", assertions);
                editorSearch.searchField().hideBuilder();
                FontPicker mountedFontPicker = (FontPicker) editor.getScene().lookup(".font-picker");
                mountedFontPicker.searchField().expressionProperty().set("no-such-font-value");
                check(mountedFontPicker.lookup(".font-live-sample").isVisible(),
                        "font search hid the user's live input/preview surface", assertions);

                Button resetAll = editor.getScene().getRoot().lookupAll(".button").stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(candidate -> "Reset all appearance".equals(candidate.getText()))
                        .findFirst().orElseThrow(() -> new AssertionError("reset-all action is missing"));
                resetAll.fire();
                Window confirmation = Window.getWindows().stream()
                        .filter(window -> window.isShowing() && window.getScene() != null)
                        .filter(window -> window.getScene().getRoot().getStyleClass()
                                .contains("appearance-confirmation"))
                        .findFirst().orElseThrow(() -> new AssertionError("reset-all confirmation did not open"));
                check(confirmation instanceof Stage decision && decision.getModality() == Modality.WINDOW_MODAL,
                        "destructive appearance reset is not a blocking owner decision", assertions);
                check(((Stage) confirmation).getOwner() == editor,
                        "appearance confirmation is not owned by the editor", assertions);
                check(confirmation.getScene().getRoot().getStyle().contains("-md-primary"),
                        "appearance confirmation did not inherit the live profile", assertions);
                Button cancelDecision = confirmation.getScene().getRoot().lookupAll(".button").stream()
                        .filter(Button.class::isInstance).map(Button.class::cast)
                        .filter(candidate -> "Cancel".equals(candidate.getText()))
                        .findFirst().orElseThrow(() -> new AssertionError("confirmation cancel action is missing"));
                cancelDecision.fire();
                check(!confirmation.isShowing(), "cancel did not dismiss the destructive confirmation", assertions);

                List<SearchField> mountedSearchFields = searchSurfaces.stream()
                        .map(AppearanceSurfaceSearch.class::cast)
                        .map(AppearanceSurfaceSearch::searchField).toList();

                settingsBridge.close();
                settingsBridge = null;
                service.close();
                service = null;
                check(!editor.isShowing(), "closing the service left the editor window open", assertions);
                check(mountedSearchFields.stream().noneMatch(search -> search.builderPopover().isShowing()),
                        "closing the editor left an anchored regex builder open", assertions);
                host.close();
                host = null;
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                if (settingsBridge != null) settingsBridge.close();
                if (service != null) service.close();
                if (host != null) host.close();
                finished.countDown();
            }
        });

        check(finished.await(20, TimeUnit.SECONDS), "JavaFX appearance smoke timed out", assertions);
        Platform.exit();
        deleteTree(temporary.get());
        if (failure.get() != null) {
            if (failure.get() instanceof Exception exception) throw exception;
            if (failure.get() instanceof Error error) throw error;
            throw new RuntimeException(failure.get());
        }
        System.out.println("AppearanceUiSmoke: " + assertions.get() + " assertions passed");
    }

    private static void check(boolean condition, String message, AtomicInteger assertions) {
        assertions.incrementAndGet();
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double expected, double actual, double tolerance, String message,
                             AtomicInteger assertions) {
        assertions.incrementAndGet();
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
