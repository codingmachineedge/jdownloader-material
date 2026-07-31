package org.jdownloader.material.ui.appearance;

import java.util.Locale;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProperty;
import org.jdownloader.material.appearance.AppearanceStyle;
import org.jdownloader.material.appearance.ColorValue;

/** Applies the JavaFX-supported subset while preserving every unsupported value in the profile. */
final class AppearanceCss {

    static final String BASE_STYLE_KEY = "jdm.appearance.baseStyle";
    static final String GLOBAL_STYLE_KEY = "jdm.appearance.globalStyle";
    static final String DIRECT_BASELINE_KEY = "jdm.appearance.directBaseline";

    private AppearanceCss() {
    }

    static void applyGlobal(Node root, AppearanceProfile profile) {
        String base = baseStyle(root);
        String family = cssString(profile.fontFamily() + ", " + profile.cjkFallback());
        ColorValue accent = profile.accentColor();
        ColorValue seed = profile.seedColor();
        ColorValue container = mix(seed, profile.theme() == org.jdownloader.material.appearance.ThemeMode.DARK
                ? ColorValue.srgb(0, 0, 0, 1) : ColorValue.srgb(1, 1, 1, 1), 0.72);
        boolean dark = profile.theme() == org.jdownloader.material.appearance.ThemeMode.DARK;
        String additions = "-fx-font-family: " + family + ";"
                + "-fx-font-size: " + decimal(14 * profile.fontSizeScale()) + "px;"
                + "-fx-font-weight: " + profile.fontWeight() + ";"
                + "-md-primary: " + accent.toCssRgba() + ";"
                + "-md-on-primary: " + accent.recommendedTextColor().toCssRgba() + ";"
                + "-md-primary-container: " + container.toCssRgba() + ";"
                + "-md-on-primary-container: " + container.recommendedTextColor().toCssRgba() + ";"
                + "-md-selection: " + container.toCssRgba() + ";"
                + "-appearance-surface: " + (dark ? "#141218" : "#FFFBFE") + ";"
                + "-appearance-surface-container: " + (dark ? "#211F26" : "#F3EDF7") + ";"
                + "-appearance-on-surface: " + (dark ? "#E6E0E9" : "#1D1B20") + ";"
                + "-appearance-on-surface-variant: " + (dark ? "#CAC4D0" : "#49454F") + ";"
                + "-appearance-outline: " + (dark ? "#938F99" : "#79747E") + ";"
                + "-appearance-error: " + (dark ? "#FFB4AB" : "#BA1A1A") + ";"
                + "-appearance-on-error: " + (dark ? "#690005" : "#FFFFFF") + ";"
                + "-fx-accent: " + accent.toCssRgba() + ";";
        root.getProperties().put(GLOBAL_STYLE_KEY, additions);
        root.setStyle(join(base, additions));
        root.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("appearance-dark"),
                profile.theme() == org.jdownloader.material.appearance.ThemeMode.DARK);
        for (String density : new String[]{"compact", "standard", "comfortable"}) {
            root.getStyleClass().remove("appearance-density-" + density);
        }
        root.getStyleClass().add("appearance-density-" + profile.density().name().toLowerCase(Locale.ROOT));
    }

    static void apply(Node node, AppearanceProfile profile, AppearanceStyle style) {
        DirectBaseline baseline = directBaseline(node);
        baseline.restore(node);
        StringBuilder css = new StringBuilder();
        css(css, "-fx-font-family", style.get(AppearanceProperty.FONT_FAMILY)
                .map(value -> cssString(value + ", " + profile.cjkFallback())).orElse(null));
        css(css, "-fx-font-size", pixels(style, AppearanceProperty.FONT_SIZE));
        int weight = style.get(AppearanceProperty.FONT_WEIGHT).map(Integer::parseInt).orElse(profile.fontWeight());
        if (style.booleanValue(AppearanceProperty.BOLD, false)) weight = Math.max(700, weight);
        if (style.get(AppearanceProperty.FONT_WEIGHT).isPresent() || style.get(AppearanceProperty.BOLD).isPresent()) {
            css(css, "-fx-font-weight", Integer.toString(weight));
        }
        style.get(AppearanceProperty.FONT_POSTURE).ifPresent(value ->
                css(css, "-fx-font-style", "NORMAL".equals(value) ? "normal" : "italic"));
        style.color(AppearanceProperty.TEXT_COLOR).ifPresent(value -> css(css, "-fx-text-fill", value.toCssRgba()));
        style.color(AppearanceProperty.FOREGROUND_COLOR).ifPresent(value -> css(css, "-fx-text-fill", value.toCssRgba()));
        if (node instanceof Text) {
            style.color(AppearanceProperty.TEXT_COLOR).ifPresent(value -> css(css, "-fx-fill", value.toCssRgba()));
            style.color(AppearanceProperty.FOREGROUND_COLOR).ifPresent(value -> css(css, "-fx-fill", value.toCssRgba()));
        }
        style.color(AppearanceProperty.BACKGROUND_COLOR).ifPresent(value -> css(css, "-fx-background-color", value.toCssRgba()));
        style.color(AppearanceProperty.HIGHLIGHT_COLOR).ifPresent(value -> css(css, "-fx-highlight-fill", value.toCssRgba()));
        style.color(AppearanceProperty.BORDER_COLOR).ifPresent(value -> css(css, "-fx-border-color", value.toCssRgba()));
        css(css, "-fx-border-width", pixels(style, AppearanceProperty.BORDER_WIDTH));
        css(css, "-fx-background-radius", pixels(style, AppearanceProperty.CORNER_RADIUS));
        css(css, "-fx-border-radius", pixels(style, AppearanceProperty.CORNER_RADIUS));
        style.number(AppearanceProperty.OPACITY).ifPresent(value ->
                css(css, "-fx-opacity", decimal(clamp(value, 0, 1))));
        style.get(AppearanceProperty.UNDERLINE_STYLE).ifPresent(value ->
                css(css, "-fx-underline", Boolean.toString(!"NONE".equals(value))));
        style.get(AppearanceProperty.STRIKETHROUGH_STYLE).ifPresent(value ->
                css(css, "-fx-strikethrough", Boolean.toString(!"NONE".equals(value))));
        style.get(AppearanceProperty.TEXT_DIRECTION).ifPresent(value ->
                css(css, "-fx-node-orientation", switch (value) {
                    case "RIGHT_TO_LEFT" -> "right-to-left";
                    case "LEFT_TO_RIGHT" -> "left-to-right";
                    default -> "inherit";
                }));
        style.number(AppearanceProperty.BASELINE_OFFSET).ifPresent(value ->
                css(css, "-fx-translate-y", decimal(value) + "px"));
        if (node instanceof Region) {
            style.color(AppearanceProperty.ICON_COLOR).ifPresent(value -> {
                css(css, "-fx-background-color", value.toCssRgba());
                css(css, "-fx-fill", value.toCssRgba());
            });
        }
        if (node instanceof Labeled labeled) appendLabeledCss(css, labeled, style);
        appendInsets(css, style);
        node.setStyle(join(join(baseStyle(node), globalStyle(node)), css.toString()));

        if (node instanceof Region region) applyGeometry(region, style);
        if (node instanceof Labeled labeled) applyGraphic(labeled, style);
        applyEffect(node, style, baseline.effect());
    }

    static void clear(Node node) {
        Object base = node.getProperties().get(BASE_STYLE_KEY);
        node.setStyle(join(base instanceof String value ? value : "", globalStyle(node)));
        directBaseline(node).restore(node);
    }

    private static void applyGeometry(Region region, AppearanceStyle style) {
        style.number(AppearanceProperty.MIN_WIDTH).ifPresent(region::setMinWidth);
        style.number(AppearanceProperty.MIN_HEIGHT).ifPresent(region::setMinHeight);
        style.number(AppearanceProperty.MAX_WIDTH).ifPresent(region::setMaxWidth);
        style.number(AppearanceProperty.MAX_HEIGHT).ifPresent(region::setMaxHeight);
        style.number(AppearanceProperty.WIDTH).ifPresent(region::setPrefWidth);
        style.number(AppearanceProperty.HEIGHT).ifPresent(region::setPrefHeight);
        style.number(AppearanceProperty.ICON_SIZE).ifPresent(value -> {
            region.setMinSize(value, value);
            region.setPrefSize(value, value);
            region.setMaxSize(value, value);
        });
    }

    private static void appendLabeledCss(StringBuilder css, Labeled labeled, AppearanceStyle style) {
        style.number(AppearanceProperty.LINE_HEIGHT).ifPresent(value -> {
            double size = style.number(AppearanceProperty.FONT_SIZE).orElse(labeled.getFont().getSize());
            css(css, "-fx-line-spacing", decimal(Math.max(0, value - 1) * size) + "px");
        });
        style.get(AppearanceProperty.ICON_POSITION).ifPresent(value ->
                css(css, "-fx-content-display", value.toLowerCase(Locale.ROOT).replace('_', '-')));
        style.number(AppearanceProperty.ICON_TEXT_GAP).ifPresent(value ->
                css(css, "-fx-graphic-text-gap", decimal(value) + "px"));
        style.get(AppearanceProperty.TEXT_ALIGNMENT).ifPresent(value ->
                {
                    css(css, "-fx-text-alignment", value.toLowerCase(Locale.ROOT).replace('_', '-'));
                    css(css, "-fx-alignment", switch (value) {
                        case "CENTER" -> "center";
                        case "RIGHT" -> "center-right";
                        default -> "center-left";
                    });
                });
    }

    private static void applyGraphic(Labeled labeled, AppearanceStyle style) {
        Node graphic = labeled.getGraphic();
        if (graphic == null) return;
        StringBuilder css = new StringBuilder();
        style.color(AppearanceProperty.ICON_COLOR).ifPresent(value -> {
            css(css, "-fx-background-color", value.toCssRgba());
            css(css, "-fx-fill", value.toCssRgba());
        });
        graphic.setStyle(join(baseStyle(graphic), css.toString()));
        if (graphic instanceof Region region) {
            DirectBaseline baseline = directBaseline(graphic);
            baseline.restore(graphic);
            style.number(AppearanceProperty.ICON_SIZE).ifPresent(value -> {
                region.setMinSize(value, value);
                region.setPrefSize(value, value);
                region.setMaxSize(value, value);
            });
        }
    }

    private static void applyEffect(Node node, AppearanceStyle style, Effect inheritedEffect) {
        Optional<ColorValue> shadowColor = style.color(AppearanceProperty.SHADOW_COLOR);
        Optional<ColorValue> glowColor = style.color(AppearanceProperty.GLOW_COLOR);
        if (shadowColor.isPresent()) {
            DropShadow shadow = new DropShadow();
            shadow.setColor(toFx(shadowColor.get()));
            style.number(AppearanceProperty.SHADOW_RADIUS).ifPresent(shadow::setRadius);
            style.number(AppearanceProperty.SHADOW_OFFSET_X).ifPresent(shadow::setOffsetX);
            style.number(AppearanceProperty.SHADOW_OFFSET_Y).ifPresent(shadow::setOffsetY);
            if (glowColor.isPresent()) {
                DropShadow glow = coloredGlow(glowColor.get(), style.number(AppearanceProperty.GLOW_RADIUS).orElse(5));
                shadow.setInput(glow);
            }
            node.setEffect(shadow);
        } else if (glowColor.isPresent()) {
            node.setEffect(coloredGlow(glowColor.get(), style.number(AppearanceProperty.GLOW_RADIUS).orElse(5)));
        } else if (style.number(AppearanceProperty.ELEVATION).isPresent()) {
            double elevation = Math.max(0, style.number(AppearanceProperty.ELEVATION).orElse(0));
            DropShadow shadow = new DropShadow();
            shadow.setColor(Color.rgb(0, 0, 0, Math.min(0.36, 0.12 + elevation * 0.015)));
            shadow.setRadius(2 + elevation * 1.5);
            shadow.setOffsetY(Math.max(1, elevation * 0.6));
            node.setEffect(shadow);
        } else {
            node.setEffect(inheritedEffect);
        }
    }

    private static DropShadow coloredGlow(ColorValue color, double radius) {
        DropShadow glow = new DropShadow();
        glow.setColor(toFx(color));
        glow.setRadius(Math.max(0, radius));
        glow.setSpread(0.2);
        glow.setOffsetX(0);
        glow.setOffsetY(0);
        return glow;
    }

    private static void appendInsets(StringBuilder css, AppearanceStyle style) {
        if (style.get(AppearanceProperty.PADDING_TOP).isEmpty()
                && style.get(AppearanceProperty.PADDING_RIGHT).isEmpty()
                && style.get(AppearanceProperty.PADDING_BOTTOM).isEmpty()
                && style.get(AppearanceProperty.PADDING_LEFT).isEmpty()) return;
        double top = style.number(AppearanceProperty.PADDING_TOP).orElse(0);
        double right = style.number(AppearanceProperty.PADDING_RIGHT).orElse(0);
        double bottom = style.number(AppearanceProperty.PADDING_BOTTOM).orElse(0);
        double left = style.number(AppearanceProperty.PADDING_LEFT).orElse(0);
        css(css, "-fx-padding", decimal(top) + " " + decimal(right) + " " + decimal(bottom) + " " + decimal(left));
    }

    private static String pixels(AppearanceStyle style, AppearanceProperty property) {
        return style.number(property).isPresent() ? decimal(style.number(property).getAsDouble()) + "px" : null;
    }

    private static Color toFx(ColorValue value) {
        return new Color(value.red(), value.green(), value.blue(), value.alpha());
    }

    private static DirectBaseline directBaseline(Node node) {
        Object saved = node.getProperties().get(DIRECT_BASELINE_KEY);
        if (saved instanceof DirectBaseline baseline) return baseline;
        DirectBaseline baseline = DirectBaseline.capture(node);
        node.getProperties().put(DIRECT_BASELINE_KEY, baseline);
        return baseline;
    }

    private static String baseStyle(Node node) {
        Object existing = node.getProperties().get(BASE_STYLE_KEY);
        if (existing instanceof String value) return value;
        String captured = node.getStyle() == null ? "" : node.getStyle();
        node.getProperties().put(BASE_STYLE_KEY, captured);
        return captured;
    }

    private static String globalStyle(Node node) {
        Object value = node.getProperties().get(GLOBAL_STYLE_KEY);
        return value instanceof String style ? style : "";
    }

    private static void css(StringBuilder target, String property, String value) {
        if (value != null && !value.isBlank()) target.append(property).append(':').append(value).append(';');
    }

    private static String cssString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String join(String base, String additions) {
        String prefix = base == null ? "" : base.trim();
        if (!prefix.isEmpty() && !prefix.endsWith(";")) prefix += ";";
        return prefix + additions;
    }

    private static ColorValue mix(ColorValue color, ColorValue destination, double destinationAmount) {
        double amount = clamp(destinationAmount, 0, 1);
        double keep = 1 - amount;
        return ColorValue.srgb(color.red() * keep + destination.red() * amount,
                color.green() * keep + destination.green() * amount,
                color.blue() * keep + destination.blue() * amount,
                color.alpha() * keep + destination.alpha() * amount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record DirectBaseline(Effect effect, double minWidth, double minHeight,
                                  double prefWidth, double prefHeight, double maxWidth, double maxHeight) {
        static DirectBaseline capture(Node node) {
            if (node instanceof Region region) {
                return new DirectBaseline(node.getEffect(), region.getMinWidth(), region.getMinHeight(),
                        region.getPrefWidth(), region.getPrefHeight(), region.getMaxWidth(), region.getMaxHeight());
            }
            return new DirectBaseline(node.getEffect(), 0, 0, 0, 0, 0, 0);
        }

        void restore(Node node) {
            node.setEffect(effect);
            if (node instanceof Region region) {
                region.setMinWidth(minWidth);
                region.setMinHeight(minHeight);
                region.setPrefWidth(prefWidth);
                region.setPrefHeight(prefHeight);
                region.setMaxWidth(maxWidth);
                region.setMaxHeight(maxHeight);
            }
        }
    }
}
