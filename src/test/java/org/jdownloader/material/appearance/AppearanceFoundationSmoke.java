package org.jdownloader.material.appearance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;
import org.jdownloader.material.search.RegexFlag;
import org.jdownloader.material.search.SafeSearchEvaluator;
import org.jdownloader.material.search.SearchMode;
import org.jdownloader.material.search.SearchSpec;

/** Headless coverage for color translation, reset semantics and bounded profile persistence. */
public final class AppearanceFoundationSmoke {

    private int assertions;

    public static void main(String[] args) throws Exception {
        AppearanceFoundationSmoke smoke = new AppearanceFoundationSmoke();
        smoke.colorsTranslateBidirectionally();
        smoke.colorsReportContrastAndClipping();
        smoke.profileResetsAndPresetsAreSparse();
        smoke.profileRoundTripPreservesFutureData();
        smoke.importRejectsMalformedAndOversizedInput();
        smoke.appearanceSearchSpecsAreIndependentAndBounded();
        smoke.settingsPayloadIsDeterministicAndNoOpSafe();
        System.out.println("AppearanceFoundationSmoke: " + smoke.assertions + " assertions passed");
    }

    private void colorsTranslateBidirectionally() {
        ColorValue transparentPurple = ColorTranslator.fromHex("#66339980");
        equal("#66339980", transparentPurple.toHex8(), "HEX8 retains alpha");
        near(128.0 / 255.0, transparentPurple.alpha(), 1.0e-12, "HEX8 alpha channel");
        equal("rebeccapurple", ColorTranslator.namedColor(ColorTranslator.fromNamed("RebeccaPurple")).orElseThrow(),
                "named colors are case-insensitive and reversible");

        ColorValue source = ColorTranslator.fromHex("#3A7BC8B3");
        Map<ColorSpace, String> representations = ColorTranslator.allRepresentations(source);
        equal(ColorSpace.values().length, representations.size(), "every advertised color space has an output");
        for (ColorSpace space : ColorSpace.values()) {
            if (space == ColorSpace.NAMED) continue;
            ColorValue roundTrip = ColorTranslator.parse(space, representations.get(space));
            near(source.red(), roundTrip.red(), 0.012, space + " red round trip");
            near(source.green(), roundTrip.green(), 0.012, space + " green round trip");
            near(source.blue(), roundTrip.blue(), 0.012, space + " blue round trip");
            if (space != ColorSpace.HEX && space != ColorSpace.RGB && space != ColorSpace.HSL) {
                near(source.alpha(), roundTrip.alpha(), 0.006, space + " alpha round trip");
            }
        }

        ColorValue hslRed = ColorTranslator.parse(ColorSpace.HSL, "hsl(0 100% 50%)");
        equal("#FF0000", hslRed.toHex(), "HSL parser");
        equal("#00FF00", ColorTranslator.fromHsv(120, 100, 100, 1).toHex(), "HSV parser");
        equal("#808080", ColorTranslator.fromHwb(42, 50, 50, 1).toHex(), "HWB gray normalization");
        equal("#FF0000", ColorTranslator.fromCmyk(0, 100, 100, 0, 1).toHex(), "CMYK conversion");

        ColorValue restored = ColorValue.fromStorageString(source.withActiveSpace(ColorSpace.OKLCH).toStorageString());
        equal(ColorSpace.OKLCH, restored.activeSpace(), "storage retains active color space");
        near(source.alpha(), restored.alpha(), 0.006, "storage retains alpha");
    }

    private void colorsReportContrastAndClipping() {
        ColorValue black = ColorTranslator.fromHex("#000000");
        ColorValue white = ColorTranslator.fromHex("#FFFFFF");
        near(21, black.contrastRatio(white), 1.0e-9, "WCAG black/white contrast");
        equal("#000000", white.recommendedTextColor().toHex(), "white recommends black text");

        ColorValue clipped = ColorTranslator.fromOklab(0.85, 0.8, 0.8, 1);
        check(clipped.clipped(), "out-of-gamut OKLab conversion is marked clipped");
        check(!clipped.clippingWarning().isBlank(), "clipped color explains the gamut conversion");
        ColorValue clippedRgb = ColorTranslator.fromRgb(400, -20, 0, 1);
        check(clippedRgb.clipped(), "out-of-range RGB is marked clipped");
        check(!clippedRgb.clippingWarning().isBlank(), "direct RGB clipping also has a warning");
        ColorValue clippedHsl = ColorTranslator.fromHsl(30, 140, 50, 1);
        check(clippedHsl.clipped(), "out-of-range HSL percentages are marked clipped");
        check(!clippedHsl.clippingWarning().isBlank(), "HSL component clipping has a warning");
        ColorValue clippedCmyk = ColorTranslator.fromCmyk(-10, 20, 20, 0, 1);
        check(clippedCmyk.clipped(), "out-of-range CMYK percentages are marked clipped");
        check(!clippedCmyk.clippingWarning().isBlank(), "CMYK component clipping has a warning");
        ColorValue restored = ColorValue.fromStorageString(clipped.toStorageString());
        check(restored.clipped(), "storage retains the clipping flag");
        equal(clipped.clippingWarning(), restored.clippingWarning(), "storage retains clipping diagnostics");
    }

    private void profileResetsAndPresetsAreSparse() {
        AppearanceTargetId target = AppearanceTargetId.of("downloads.toolbar.add-links");
        AppearanceProfile profile = new AppearanceProfile();
        profile.setTheme(ThemeMode.DARK);
        profile.setDensity(Density.COMFORTABLE);
        profile.setFontWeight(650);
        profile.target(target).style(AppearanceState.NORMAL)
                .set(AppearanceProperty.FONT_SIZE, 18)
                .set(AppearanceProperty.TEXT_COLOR, ColorTranslator.fromHex("#112233"));
        profile.target(target).style(AppearanceState.HOVER)
                .set(AppearanceProperty.BACKGROUND_COLOR, ColorTranslator.fromHex("#DDEEFF"));

        profile.resetProperty(target, AppearanceState.NORMAL, AppearanceProperty.FONT_SIZE);
        check(profile.targetIfPresent(target).orElseThrow().styleOrEmpty(AppearanceState.NORMAL)
                .get(AppearanceProperty.FONT_SIZE).isEmpty(), "per-property reset removes only one value");
        check(profile.targetIfPresent(target).orElseThrow().styleOrEmpty(AppearanceState.NORMAL)
                .get(AppearanceProperty.TEXT_COLOR).isPresent(), "per-property reset preserves siblings");

        AppearanceStyle presetStyle = new AppearanceStyle().set(AppearanceProperty.CORNER_RADIUS, 24);
        AppearancePreset preset = profile.snapshotPreset("rounded", "Rounded", presetStyle);
        profile.addUserPreset(preset);
        profile.resetGlobalAppearance();
        equal(ThemeMode.LIGHT, profile.theme(), "global reset restores theme default");
        equal(Density.STANDARD, profile.density(), "global reset restores density default");
        check(profile.targets().isEmpty(), "global reset clears per-target overrides");
        check(profile.userPresets().containsKey("rounded"), "global reset preserves named user presets");

        profile.applyPreset(preset, target);
        equal(ThemeMode.DARK, profile.theme(), "preset restores global theme");
        near(24, profile.targetIfPresent(target).orElseThrow().styleOrEmpty(AppearanceState.NORMAL)
                .number(AppearanceProperty.CORNER_RADIUS).orElseThrow(), 0, "preset applies target style");
        profile.resetTarget(target);
        check(profile.targetIfPresent(target).isEmpty(), "per-target reset removes the target override");

        rejects(() -> AppearanceTargetId.of("contains whitespace"), "target ids reject unstable whitespace");
    }

    private void profileRoundTripPreservesFutureData() throws Exception {
        Path directory = Files.createTempDirectory("appearance-smoke-");
        try {
            Path file = directory.resolve("appearance.properties");
            AppearanceProfile profile = new AppearanceProfile();
            profile.setSourceSchemaVersion(9);
            profile.setTheme(ThemeMode.DARK);
            profile.setDensity(Density.COMPACT);
            profile.setFontFamily("Noto Sans CJK TC", FontSource.BUNDLED);
            profile.setFontSizeScale(1.25);
            profile.setFontWeight(575);
            profile.retainUnsupportedRootField("future.root.mode", "turbo-but-bounded");
            String encodedPreset = Base64.getUrlEncoder().withoutPadding().encodeToString("night-tea".getBytes());
            profile.retainUnsupportedRootField("preset." + encodedPreset + ".future.mode", "sparkly");
            AppearanceTargetId target = AppearanceTargetId.of("settings.appearance.preview");
            profile.target(target).style(AppearanceState.FOCUSED)
                    .set(AppearanceProperty.BORDER_WIDTH, 3)
                    .retainUnsupported("future.sparkleIntensity", "42");
            AppearancePreset preset = profile.snapshotPreset("night-tea", "Night tea",
                    new AppearanceStyle().set(AppearanceProperty.BACKGROUND_COLOR,
                            ColorTranslator.fromHex("#221B2E")));
            profile.addUserPreset(preset);

            AppearanceProfileStore store = new AppearanceProfileStore(file);
            store.save(profile);
            AppearanceProfile loaded = store.load();
            equal(9, loaded.sourceSchemaVersion(), "future schema number survives a round trip");
            equal(ThemeMode.DARK, loaded.theme(), "theme round trip");
            equal(Density.COMPACT, loaded.density(), "density round trip");
            equal(FontSource.BUNDLED, loaded.fontSource(), "font source round trip");
            near(1.25, loaded.fontSizeScale(), 0, "font scale round trip");
            equal("turbo-but-bounded", loaded.unsupportedRootFields().get("future.root.mode"),
                    "unknown root fields survive");
            equal("sparkly", loaded.unsupportedRootFields().get("preset." + encodedPreset + ".future.mode"),
                    "future preset metadata survives without being silently discarded");
            AppearanceStyle focused = loaded.targetIfPresent(target).orElseThrow()
                    .styleOrEmpty(AppearanceState.FOCUSED);
            equal("42", focused.unsupportedValues().get("future.sparkleIntensity"),
                    "unknown target properties survive");
            near(3, focused.number(AppearanceProperty.BORDER_WIDTH).orElseThrow(), 0,
                    "typed target property round trip");
            check(loaded.userPresets().containsKey("night-tea"), "user preset round trip");

            Path exported = directory.resolve("export.jdmappearance");
            store.exportTo(exported, loaded);
            AppearanceProfile imported = store.importFrom(exported);
            equal(loaded.sourceSchemaVersion(), imported.sourceSchemaVersion(), "export can be imported");

            String payload = AppearanceProfileStore.serialize(loaded);
            AppearanceProfile payloadRoundTrip = AppearanceProfileStore.deserialize(payload);
            equal(payload, AppearanceProfileStore.serialize(payloadRoundTrip),
                    "settings/history payload is deterministic after a round trip");
            equal("sparkly", payloadRoundTrip.unsupportedRootFields()
                    .get("preset." + encodedPreset + ".future.mode"),
                    "settings/history payload retains forward preset metadata");
        } finally {
            deleteTree(directory);
        }
    }

    private void appearanceSearchSpecsAreIndependentAndBounded() {
        SafeSearchEvaluator evaluator = new SafeSearchEvaluator();
        SearchSpec editor = SearchSpec.regex("(?i)font|顏色", RegexFlag.CASE_INSENSITIVE);
        SearchSpec font = SearchSpec.plain("Segoe UI");
        SearchSpec color = SearchSpec.plain("OKLCH");
        check(editor != font && font != color, "appearance surfaces keep independent SearchSpec instances");
        equal(SearchMode.REGEX, editor.mode(), "editor can opt into regex independently");
        equal(SearchMode.PLAIN_TEXT, font.mode(), "font search remains plain text by default");
        check(evaluator.matches(editor, "Font family · 字體"), "editor regex matches localized labels");
        check(evaluator.matches(font, "Font family · Segoe UI Variable"), "font plain search matches current values");
        check(evaluator.matches(color, "Translations · OKLCH · oklch(0.5 0.2 90)"),
                "color search matches translated representations");
        check(!evaluator.validate(SearchSpec.regex("(")).valid(), "invalid appearance regex is reported inline-safe");
    }

    private void settingsPayloadIsDeterministicAndNoOpSafe() throws Exception {
        AppearanceProfile profile = new AppearanceProfile();
        AppearanceTargetId target = AppearanceTargetId.of("history.appearance.sample");
        profile.target(target).style(AppearanceState.NORMAL).set(AppearanceProperty.FONT_SIZE, 19);
        String payload = AppearanceProfileStore.serialize(profile);

        Settings settings = new Settings();
        AtomicInteger changes = new AtomicInteger();
        settings.appearanceProfilePayloadProperty().addListener((observable, previous, current) ->
                changes.incrementAndGet());
        settings.setAppearanceProfilePayload(payload);
        settings.setAppearanceProfilePayload(payload);
        equal(1, changes.get(), "identical serialized appearance payload does not emit a second settings change");

        java.util.Properties snapshot = SettingsIO.snapshot(settings);
        equal(payload, snapshot.getProperty("appearanceProfilePayload"),
                "encrypted backup/settings snapshot includes the appearance payload");
        Settings restored = new Settings();
        SettingsIO.apply(snapshot, restored);
        equal(payload, restored.appearanceProfilePayloadProperty().get(),
                "settings restore carries the appearance payload unchanged");
        equal(payload, AppearanceProfileStore.serialize(AppearanceProfileStore.deserialize(payload)),
                "restored appearance payload canonicalizes byte-for-byte");
    }

    private void importRejectsMalformedAndOversizedInput() throws Exception {
        Path directory = Files.createTempDirectory("appearance-invalid-");
        try {
            Path malformed = directory.resolve("malformed.properties");
            Files.writeString(malformed, "schema.version=0\nglobal.theme=LIGHT\n");
            rejectsIo(() -> AppearanceProfileStore.read(malformed), "invalid schema is rejected");

            Path badColor = directory.resolve("bad-color.properties");
            Files.writeString(badColor, "schema.version=1\nglobal.seed=definitely-not-a-color\n");
            rejectsIo(() -> AppearanceProfileStore.read(badColor), "invalid color is rejected before import");

            Path oversized = directory.resolve("oversized.properties");
            Files.write(oversized, new byte[(int) AppearanceProfileStore.MAX_IMPORT_BYTES + 1]);
            rejectsIo(() -> AppearanceProfileStore.read(oversized), "oversized profile is rejected");

            String longToken = Base64.getUrlEncoder().withoutPadding().encodeToString("x".repeat(1_025).getBytes());
            Path longId = directory.resolve("long-token.properties");
            Files.writeString(longId, "schema.version=1\ntarget." + longToken + ".NORMAL."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString("geometry.width".getBytes()) + "=1\n");
            rejectsIo(() -> AppearanceProfileStore.read(longId),
                    "oversized encoded target identifiers are rejected before instantiation");
        } finally {
            deleteTree(directory);
        }
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void equal(Object expected, Object actual, String message) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private void near(double expected, double actual, double tolerance, String message) {
        assertions++;
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual
                    + ", tolerance=" + tolerance);
        }
    }

    private void rejects(ThrowingRunnable action, String message) {
        assertions++;
        try {
            action.run();
            throw new AssertionError(message + ": no exception was thrown");
        } catch (IllegalArgumentException expected) {
            // Expected.
        } catch (Exception unexpected) {
            throw new AssertionError(message + ": wrong exception", unexpected);
        }
    }

    private void rejectsIo(ThrowingRunnable action, String message) {
        assertions++;
        try {
            action.run();
            throw new AssertionError(message + ": no exception was thrown");
        } catch (IOException expected) {
            // Expected.
        } catch (Exception unexpected) {
            throw new AssertionError(message + ": wrong exception", unexpected);
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
