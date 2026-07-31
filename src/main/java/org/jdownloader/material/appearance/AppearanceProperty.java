package org.jdownloader.material.appearance;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Typed schema for every property an element/state override can store.
 * Unsupported JavaFX properties remain first-class schema members so an import
 * never silently loses them and the editor can explain the platform limit.
 */
public enum AppearanceProperty {
    FONT_FAMILY("typography.fontFamily", Category.TYPOGRAPHY, Kind.TEXT, true),
    FONT_SIZE("typography.fontSize", Category.TYPOGRAPHY, Kind.NUMBER, true),
    VARIABLE_FONT_AXES("typography.variableAxes", Category.TYPOGRAPHY, Kind.TEXT, false),
    FONT_WEIGHT("typography.weight", Category.TYPOGRAPHY, Kind.INTEGER, true),
    BOLD("typography.bold", Category.TYPOGRAPHY, Kind.BOOLEAN, true),
    FONT_POSTURE("typography.posture", Category.TYPOGRAPHY, Kind.CHOICE, true,
            "NORMAL", "ITALIC", "OBLIQUE"),
    UNDERLINE_STYLE("typography.underlineStyle", Category.TYPOGRAPHY, Kind.CHOICE, false,
            "NONE", "SINGLE", "DOUBLE", "DOTTED", "DASHED", "WAVY"),
    UNDERLINE_COLOR("typography.underlineColor", Category.TYPOGRAPHY, Kind.COLOR, false),
    STRIKETHROUGH_STYLE("typography.strikethroughStyle", Category.TYPOGRAPHY, Kind.CHOICE, false,
            "NONE", "SINGLE", "DOUBLE"),
    OVERLINE("typography.overline", Category.TYPOGRAPHY, Kind.BOOLEAN, false),
    CAPITALIZATION("typography.capitalization", Category.TYPOGRAPHY, Kind.CHOICE, false,
            "NONE", "UPPERCASE", "LOWERCASE", "TITLE_CASE"),
    SMALL_CAPS("typography.smallCaps", Category.TYPOGRAPHY, Kind.BOOLEAN, false),
    VERTICAL_POSITION("typography.verticalPosition", Category.TYPOGRAPHY, Kind.CHOICE, false,
            "NORMAL", "SUPERSCRIPT", "SUBSCRIPT"),
    TEXT_COLOR("typography.textColor", Category.COLORS, Kind.COLOR, true),
    HIGHLIGHT_COLOR("typography.highlightColor", Category.COLORS, Kind.COLOR, true),
    OUTLINE_COLOR("typography.outlineColor", Category.COLORS, Kind.COLOR, false),
    OUTLINE_WIDTH("typography.outlineWidth", Category.TYPOGRAPHY, Kind.NUMBER, false),
    SHADOW_COLOR("typography.shadowColor", Category.COLORS, Kind.COLOR, true),
    SHADOW_RADIUS("typography.shadowRadius", Category.TYPOGRAPHY, Kind.NUMBER, true),
    SHADOW_OFFSET_X("typography.shadowOffsetX", Category.TYPOGRAPHY, Kind.NUMBER, true),
    SHADOW_OFFSET_Y("typography.shadowOffsetY", Category.TYPOGRAPHY, Kind.NUMBER, true),
    GLOW_COLOR("typography.glowColor", Category.COLORS, Kind.COLOR, true),
    GLOW_RADIUS("typography.glowRadius", Category.TYPOGRAPHY, Kind.NUMBER, true),
    CHARACTER_SPACING("typography.characterSpacing", Category.TYPOGRAPHY, Kind.NUMBER, false),
    WORD_SPACING("typography.wordSpacing", Category.TYPOGRAPHY, Kind.NUMBER, false),
    LINE_HEIGHT("typography.lineHeight", Category.TYPOGRAPHY, Kind.NUMBER, true),
    BASELINE_OFFSET("typography.baselineOffset", Category.TYPOGRAPHY, Kind.NUMBER, true),
    TEXT_DIRECTION("typography.direction", Category.TYPOGRAPHY, Kind.CHOICE, true,
            "AUTO", "LEFT_TO_RIGHT", "RIGHT_TO_LEFT"),
    TEXT_ALIGNMENT("typography.alignment", Category.TYPOGRAPHY, Kind.CHOICE, true,
            "LEFT", "CENTER", "RIGHT", "JUSTIFY"),

    FOREGROUND_COLOR("colors.foreground", Category.COLORS, Kind.COLOR, true),
    BACKGROUND_COLOR("colors.background", Category.COLORS, Kind.COLOR, true),
    BORDER_COLOR("colors.border", Category.COLORS, Kind.COLOR, true),
    ICON_COLOR("colors.icon", Category.COLORS, Kind.COLOR, true),

    WIDTH("geometry.width", Category.GEOMETRY, Kind.NUMBER, true),
    HEIGHT("geometry.height", Category.GEOMETRY, Kind.NUMBER, true),
    MIN_WIDTH("geometry.minWidth", Category.GEOMETRY, Kind.NUMBER, true),
    MIN_HEIGHT("geometry.minHeight", Category.GEOMETRY, Kind.NUMBER, true),
    MAX_WIDTH("geometry.maxWidth", Category.GEOMETRY, Kind.NUMBER, true),
    MAX_HEIGHT("geometry.maxHeight", Category.GEOMETRY, Kind.NUMBER, true),
    PADDING_TOP("spacing.paddingTop", Category.SPACING, Kind.NUMBER, true),
    PADDING_RIGHT("spacing.paddingRight", Category.SPACING, Kind.NUMBER, true),
    PADDING_BOTTOM("spacing.paddingBottom", Category.SPACING, Kind.NUMBER, true),
    PADDING_LEFT("spacing.paddingLeft", Category.SPACING, Kind.NUMBER, true),
    MARGIN_TOP("spacing.marginTop", Category.SPACING, Kind.NUMBER, false),
    MARGIN_RIGHT("spacing.marginRight", Category.SPACING, Kind.NUMBER, false),
    MARGIN_BOTTOM("spacing.marginBottom", Category.SPACING, Kind.NUMBER, false),
    MARGIN_LEFT("spacing.marginLeft", Category.SPACING, Kind.NUMBER, false),
    GAP("spacing.gap", Category.SPACING, Kind.NUMBER, false),
    CORNER_RADIUS("geometry.cornerRadius", Category.GEOMETRY, Kind.NUMBER, true),
    BORDER_WIDTH("geometry.borderWidth", Category.GEOMETRY, Kind.NUMBER, true),
    ELEVATION("geometry.elevation", Category.GEOMETRY, Kind.NUMBER, true),
    OPACITY("geometry.opacity", Category.GEOMETRY, Kind.NUMBER, true),
    SHAPE("geometry.shape", Category.GEOMETRY, Kind.CHOICE, false,
            "ROUNDED_RECTANGLE", "RECTANGLE", "PILL", "CIRCLE", "CUSTOM"),

    ICON_NAME("icon.name", Category.ICON, Kind.TEXT, false),
    ICON_SIZE("icon.size", Category.ICON, Kind.NUMBER, true),
    ICON_POSITION("icon.position", Category.ICON, Kind.CHOICE, true,
            "LEFT", "RIGHT", "TOP", "BOTTOM", "GRAPHIC_ONLY", "TEXT_ONLY"),
    ICON_TEXT_GAP("icon.textGap", Category.ICON, Kind.NUMBER, true);

    public enum Category { TYPOGRAPHY, COLORS, GEOMETRY, SPACING, ICON }
    public enum Kind { TEXT, BOOLEAN, NUMBER, INTEGER, COLOR, CHOICE }

    private final String id;
    private final Category category;
    private final Kind kind;
    private final boolean javaFxSupported;
    private final List<String> choices;

    AppearanceProperty(String id, Category category, Kind kind, boolean javaFxSupported, String... choices) {
        this.id = id;
        this.category = category;
        this.kind = kind;
        this.javaFxSupported = javaFxSupported;
        this.choices = List.copyOf(Arrays.asList(choices));
    }

    public String id() { return id; }
    public Category category() { return category; }
    public Kind kind() { return kind; }
    public boolean javaFxSupported() { return javaFxSupported; }
    public List<String> choices() { return choices; }

    public String normalize(String raw) {
        String value = Objects.requireNonNull(raw, "value").trim();
        if (value.length() > 8_192) throw new IllegalArgumentException(id + " is too long");
        return switch (kind) {
            case TEXT, COLOR -> value;
            case BOOLEAN -> Boolean.toString(parseBoolean(value));
            case NUMBER -> Double.toString(finite(value));
            case INTEGER -> Integer.toString(Integer.parseInt(value));
            case CHOICE -> normalizeChoice(value);
        };
    }

    private String normalizeChoice(String value) {
        String upper = value.toUpperCase(Locale.ROOT);
        if (!choices.contains(upper)) throw new IllegalArgumentException("Unsupported " + id + " value: " + value);
        return upper;
    }

    private static boolean parseBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Expected true or false: " + value);
        }
        return Boolean.parseBoolean(value);
    }

    private static double finite(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Expected a finite number: " + value);
        return parsed;
    }

    public static AppearanceProperty byId(String id) {
        for (AppearanceProperty property : values()) {
            if (property.id.equals(id)) return property;
        }
        return null;
    }
}
