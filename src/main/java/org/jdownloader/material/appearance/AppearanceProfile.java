package org.jdownloader.material.appearance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Schema-versioned global appearance profile with sparse per-target overrides.
 * The model deliberately has no JavaFX dependency, so import and conversion can
 * be verified headlessly and the UI layer can be replaced without data loss.
 */
public final class AppearanceProfile {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String DEFAULT_FONT_FAMILY = "Segoe UI Variable";
    public static final String DEFAULT_CJK_FALLBACK =
            "Microsoft JhengHei UI, Noto Sans CJK TC, Segoe UI, sans-serif";

    private int sourceSchemaVersion = CURRENT_SCHEMA_VERSION;
    private ThemeMode theme = ThemeMode.LIGHT;
    private Density density = Density.STANDARD;
    private ColorValue seedColor = ColorTranslator.fromHex("#006B5C");
    private ColorValue accentColor = ColorTranslator.fromHex("#006B5C");
    private String fontFamily = DEFAULT_FONT_FAMILY;
    private FontSource fontSource = FontSource.INSTALLED;
    private double fontSizeScale = 1.0;
    private int fontWeight = 400;
    private String cjkFallback = DEFAULT_CJK_FALLBACK;
    private final LinkedHashMap<AppearanceTargetId, AppearanceTargetOverride> targets = new LinkedHashMap<>();
    private final LinkedHashMap<String, AppearancePreset> userPresets = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> unsupportedRootFields = new LinkedHashMap<>();

    public AppearanceProfile() {
    }

    private AppearanceProfile(AppearanceProfile source) {
        sourceSchemaVersion = source.sourceSchemaVersion;
        theme = source.theme;
        density = source.density;
        seedColor = source.seedColor;
        accentColor = source.accentColor;
        fontFamily = source.fontFamily;
        fontSource = source.fontSource;
        fontSizeScale = source.fontSizeScale;
        fontWeight = source.fontWeight;
        cjkFallback = source.cjkFallback;
        source.targets.forEach((id, value) -> targets.put(id, value.copy()));
        userPresets.putAll(source.userPresets);
        unsupportedRootFields.putAll(source.unsupportedRootFields);
    }

    public synchronized AppearanceProfile copy() { return new AppearanceProfile(this); }

    public synchronized int sourceSchemaVersion() { return sourceSchemaVersion; }
    public synchronized ThemeMode theme() { return theme; }
    public synchronized Density density() { return density; }
    public synchronized ColorValue seedColor() { return seedColor; }
    public synchronized ColorValue accentColor() { return accentColor; }
    public synchronized String fontFamily() { return fontFamily; }
    public synchronized FontSource fontSource() { return fontSource; }
    public synchronized double fontSizeScale() { return fontSizeScale; }
    public synchronized int fontWeight() { return fontWeight; }
    public synchronized String cjkFallback() { return cjkFallback; }

    public synchronized void setSourceSchemaVersion(int value) {
        if (value < 1 || value > 100_000) throw new IllegalArgumentException("Unsupported appearance schema version");
        sourceSchemaVersion = value;
    }

    public synchronized void setTheme(ThemeMode value) { theme = Objects.requireNonNull(value, "theme"); }
    public synchronized void setDensity(Density value) { density = Objects.requireNonNull(value, "density"); }
    public synchronized void setSeedColor(ColorValue value) { seedColor = Objects.requireNonNull(value, "seedColor"); }
    public synchronized void setAccentColor(ColorValue value) { accentColor = Objects.requireNonNull(value, "accentColor"); }

    public synchronized void setFontFamily(String value, FontSource source) {
        fontFamily = boundedText(value, "fontFamily", 240);
        fontSource = Objects.requireNonNull(source, "fontSource");
    }

    public synchronized void setFontSizeScale(double value) {
        if (!Double.isFinite(value) || value < 0.5 || value > 3.0) {
            throw new IllegalArgumentException("Font size scale must be between 0.5 and 3.0");
        }
        fontSizeScale = value;
    }

    public synchronized void setFontWeight(int value) {
        if (value < 100 || value > 1_000) throw new IllegalArgumentException("Font weight must be between 100 and 1000");
        fontWeight = value;
    }

    public synchronized void setCjkFallback(String value) {
        cjkFallback = boundedText(value, "cjkFallback", 1_024);
    }

    public synchronized AppearanceTargetOverride target(AppearanceTargetId id) {
        return targets.computeIfAbsent(Objects.requireNonNull(id, "id"), AppearanceTargetOverride::new);
    }

    public synchronized Optional<AppearanceTargetOverride> targetIfPresent(AppearanceTargetId id) {
        AppearanceTargetOverride value = targets.get(id);
        return value == null ? Optional.empty() : Optional.of(value.copy());
    }

    public synchronized Map<AppearanceTargetId, AppearanceTargetOverride> targets() {
        LinkedHashMap<AppearanceTargetId, AppearanceTargetOverride> copy = new LinkedHashMap<>();
        targets.forEach((id, value) -> copy.put(id, value.copy()));
        return Collections.unmodifiableMap(copy);
    }

    public synchronized void resetProperty(AppearanceTargetId targetId, AppearanceState state,
                                           AppearanceProperty property) {
        AppearanceTargetOverride target = targets.get(targetId);
        if (target == null) return;
        target.resetProperty(state, property);
        if (target.isEmpty()) targets.remove(targetId);
    }

    public synchronized void resetTarget(AppearanceTargetId targetId) {
        targets.remove(targetId);
    }

    public synchronized void resetGlobal(GlobalAppearanceProperty property) {
        Objects.requireNonNull(property, "property");
        switch (property) {
            case THEME -> theme = ThemeMode.LIGHT;
            case DENSITY -> density = Density.STANDARD;
            case SEED_COLOR -> seedColor = ColorTranslator.fromHex("#006B5C");
            case ACCENT_COLOR -> accentColor = ColorTranslator.fromHex("#006B5C");
            case FONT_FAMILY -> fontFamily = DEFAULT_FONT_FAMILY;
            case FONT_SOURCE -> fontSource = FontSource.INSTALLED;
            case FONT_SIZE_SCALE -> fontSizeScale = 1.0;
            case FONT_WEIGHT -> fontWeight = 400;
            case CJK_FALLBACK -> cjkFallback = DEFAULT_CJK_FALLBACK;
        }
    }

    /** Resets current appearance while preserving named user presets and future imported fields. */
    public synchronized void resetGlobalAppearance() {
        theme = ThemeMode.LIGHT;
        density = Density.STANDARD;
        seedColor = ColorTranslator.fromHex("#006B5C");
        accentColor = ColorTranslator.fromHex("#006B5C");
        fontFamily = DEFAULT_FONT_FAMILY;
        fontSource = FontSource.INSTALLED;
        fontSizeScale = 1.0;
        fontWeight = 400;
        cjkFallback = DEFAULT_CJK_FALLBACK;
        targets.clear();
    }

    public synchronized void addUserPreset(AppearancePreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (preset.builtIn()) throw new IllegalArgumentException("Built-in presets are not stored as user presets");
        if (userPresets.size() >= 500 && !userPresets.containsKey(preset.id())) {
            throw new IllegalStateException("Too many user appearance presets");
        }
        userPresets.put(preset.id(), preset);
    }

    public synchronized void removeUserPreset(String id) { userPresets.remove(id); }

    public synchronized Map<String, AppearancePreset> userPresets() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(userPresets));
    }

    public synchronized AppearancePreset snapshotPreset(String id, String name, AppearanceStyle style) {
        return new AppearancePreset(id, name, false, theme, density, seedColor, accentColor,
                fontFamily, fontSource, fontSizeScale, fontWeight, cjkFallback, style);
    }

    public synchronized void applyPreset(AppearancePreset preset, AppearanceTargetId targetId) {
        Objects.requireNonNull(preset, "preset");
        theme = preset.theme();
        density = preset.density();
        seedColor = preset.seedColor();
        accentColor = preset.accentColor();
        fontFamily = preset.fontFamily();
        fontSource = preset.fontSource();
        fontSizeScale = preset.fontSizeScale();
        fontWeight = preset.fontWeight();
        cjkFallback = preset.cjkFallback();
        if (targetId != null) {
            AppearanceStyle normal = target(targetId).style(AppearanceState.NORMAL);
            normal.clear();
            AppearanceStyle presetStyle = preset.style();
            presetStyle.values().forEach(normal::set);
            presetStyle.unsupportedValues().forEach(normal::retainUnsupported);
        }
    }

    public synchronized void retainUnsupportedRootField(String key, String value) {
        String boundedKey = boundedText(key, "key", 1_024);
        String boundedValue = Objects.requireNonNullElse(value, "");
        if (boundedValue.length() > 8_192) throw new IllegalArgumentException("Unsupported appearance value is too long");
        unsupportedRootFields.put(boundedKey, boundedValue);
    }

    public synchronized Map<String, String> unsupportedRootFields() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(unsupportedRootFields));
    }

    private static String boundedText(String value, String name, int maxLength) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1-" + maxLength + " characters");
        }
        return text;
    }
}
