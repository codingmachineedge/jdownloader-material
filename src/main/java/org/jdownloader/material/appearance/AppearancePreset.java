package org.jdownloader.material.appearance;

import java.util.Objects;

/** Named global/target appearance values that can be applied without losing the current profile. */
public final class AppearancePreset {

    private final String id;
    private final String name;
    private final boolean builtIn;
    private final ThemeMode theme;
    private final Density density;
    private final ColorValue seedColor;
    private final ColorValue accentColor;
    private final String fontFamily;
    private final FontSource fontSource;
    private final double fontSizeScale;
    private final int fontWeight;
    private final String cjkFallback;
    private final AppearanceStyle style;

    public AppearancePreset(String id, String name, boolean builtIn, ThemeMode theme, Density density,
                            ColorValue seedColor, ColorValue accentColor, String fontFamily,
                            FontSource fontSource, double fontSizeScale, int fontWeight,
                            String cjkFallback, AppearanceStyle style) {
        this.id = identifier(id);
        this.name = required(name, "name", 120);
        this.builtIn = builtIn;
        this.theme = Objects.requireNonNull(theme, "theme");
        this.density = Objects.requireNonNull(density, "density");
        this.seedColor = Objects.requireNonNull(seedColor, "seedColor");
        this.accentColor = Objects.requireNonNull(accentColor, "accentColor");
        this.fontFamily = required(fontFamily, "fontFamily", 240);
        this.fontSource = Objects.requireNonNull(fontSource, "fontSource");
        if (!Double.isFinite(fontSizeScale) || fontSizeScale < 0.5 || fontSizeScale > 3.0) {
            throw new IllegalArgumentException("Font size scale must be between 0.5 and 3.0");
        }
        if (fontWeight < 100 || fontWeight > 1_000) {
            throw new IllegalArgumentException("Font weight must be between 100 and 1000");
        }
        this.fontSizeScale = fontSizeScale;
        this.fontWeight = fontWeight;
        this.cjkFallback = required(cjkFallback, "cjkFallback", 1_024);
        this.style = style == null ? new AppearanceStyle() : style.copy();
    }

    public String id() { return id; }
    public String name() { return name; }
    public boolean builtIn() { return builtIn; }
    public ThemeMode theme() { return theme; }
    public Density density() { return density; }
    public ColorValue seedColor() { return seedColor; }
    public ColorValue accentColor() { return accentColor; }
    public String fontFamily() { return fontFamily; }
    public FontSource fontSource() { return fontSource; }
    public double fontSizeScale() { return fontSizeScale; }
    public int fontWeight() { return fontWeight; }
    public String cjkFallback() { return cjkFallback; }
    public AppearanceStyle style() { return style.copy(); }

    public AppearancePreset asUserPreset(String nextId, String nextName) {
        return new AppearancePreset(nextId, nextName, false, theme, density, seedColor, accentColor,
                fontFamily, fontSource, fontSizeScale, fontWeight, cjkFallback, style);
    }

    private static String identifier(String value) {
        String id = required(value, "id", 120);
        if (!id.matches("[\\p{L}\\p{N}._-]+")) throw new IllegalArgumentException("Preset id contains unsupported characters");
        return id;
    }

    private static String required(String value, String name, int maxLength) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " must contain 1-" + maxLength + " characters");
        }
        return normalized;
    }
}
