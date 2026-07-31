package org.jdownloader.material.appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bidirectional color translator used by every appearance color control. */
public final class ColorTranslator {

    private static final double XN = 0.95047;
    private static final double YN = 1.00000;
    private static final double ZN = 1.08883;
    private static final String GAMUT_WARNING = "Converted color was clipped to the sRGB gamut.";
    private static final String COMPONENT_WARNING = "Color components were clipped to their supported range.";

    private static final Map<String, String> NAMED = namedColors();
    private static final Map<String, String> REVERSE_NAMED = reverseNamedColors();

    private ColorTranslator() {
    }

    public static ColorValue parse(String text) {
        String value = Objects.requireNonNull(text, "text").trim();
        if (value.startsWith("#")) return fromHex(value);
        int parenthesis = value.indexOf('(');
        if (parenthesis > 0) {
            String function = value.substring(0, parenthesis).trim().toUpperCase(Locale.ROOT);
            ColorSpace space = switch (function) {
                case "RGB" -> ColorSpace.RGB;
                case "RGBA" -> ColorSpace.RGBA;
                case "HSL" -> ColorSpace.HSL;
                case "HSLA" -> ColorSpace.HSLA;
                case "HSV" -> ColorSpace.HSV;
                case "HSB" -> ColorSpace.HSB;
                case "HWB" -> ColorSpace.HWB;
                case "LAB" -> ColorSpace.CIELAB;
                case "LCH" -> ColorSpace.LCH;
                case "OKLAB" -> ColorSpace.OKLAB;
                case "OKLCH" -> ColorSpace.OKLCH;
                case "CMYK" -> ColorSpace.CMYK;
                default -> throw new IllegalArgumentException("Unknown color function: " + function);
            };
            return parse(space, value);
        }
        return fromNamed(value);
    }

    public static ColorValue parse(ColorSpace space, String text) {
        Objects.requireNonNull(space, "space");
        return switch (space) {
            case NAMED -> fromNamed(text);
            case HEX, HEX8 -> fromHex(text).withActiveSpace(space);
            case RGB, RGBA -> parseRgb(space, text);
            case HSL, HSLA -> parseHsl(space, text);
            case HSV, HSB -> parseHsv(space, text);
            case HWB -> parseHwb(text);
            case CIELAB -> parseLab(text);
            case LCH -> parseLch(text);
            case OKLAB -> parseOklab(text);
            case OKLCH -> parseOklch(text);
            case CMYK -> parseCmyk(text);
        };
    }

    public static ColorValue fromNamed(String name) {
        String normalized = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
        String hex = NAMED.get(normalized);
        if (hex == null) throw new IllegalArgumentException("Unknown named color: " + name);
        return fromHex(hex).withActiveSpace(ColorSpace.NAMED);
    }

    public static Optional<String> namedColor(ColorValue color) {
        return Optional.ofNullable(REVERSE_NAMED.get(opaqueKey(color)));
    }

    public static ColorValue fromHex(String text) {
        String hex = Objects.requireNonNull(text, "text").trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 3 || hex.length() == 4) {
            StringBuilder expanded = new StringBuilder(hex.length() * 2);
            for (char character : hex.toCharArray()) expanded.append(character).append(character);
            hex = expanded.toString();
        }
        if (hex.length() != 6 && hex.length() != 8) throw new IllegalArgumentException("Use #RGB, #RGBA, #RRGGBB, or #RRGGBBAA");
        try {
            int red = Integer.parseInt(hex.substring(0, 2), 16);
            int green = Integer.parseInt(hex.substring(2, 4), 16);
            int blue = Integer.parseInt(hex.substring(4, 6), 16);
            int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;
            return ColorValue.converted(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0,
                    hex.length() == 8 ? ColorSpace.HEX8 : ColorSpace.HEX, false, "");
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid hexadecimal color", invalid);
        }
    }

    public static ColorValue fromRgb(double red, double green, double blue, double alpha) {
        boolean clipped = outside(red / 255.0) || outside(green / 255.0) || outside(blue / 255.0)
                || outside(alpha);
        return ColorValue.converted(red / 255.0, green / 255.0, blue / 255.0, alpha,
                alpha < 1 ? ColorSpace.RGBA : ColorSpace.RGB, clipped, clipped ? COMPONENT_WARNING : "");
    }

    public static ColorValue fromHsl(double hue, double saturation, double lightness, double alpha) {
        boolean clipped = outsidePercent(saturation) || outsidePercent(lightness) || outside(alpha);
        double h = wrapHue(hue) / 360.0;
        double s = fraction(saturation);
        double l = fraction(lightness);
        double red;
        double green;
        double blue;
        if (s == 0) {
            red = green = blue = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            red = hueToRgb(p, q, h + 1.0 / 3.0);
            green = hueToRgb(p, q, h);
            blue = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        return ColorValue.converted(red, green, blue, alpha,
                alpha < 1 ? ColorSpace.HSLA : ColorSpace.HSL, clipped, clipped ? COMPONENT_WARNING : "");
    }

    public static ColorValue fromHsv(double hue, double saturation, double value, double alpha) {
        boolean clipped = outsidePercent(saturation) || outsidePercent(value) || outside(alpha);
        double h = wrapHue(hue) / 60.0;
        double s = fraction(saturation);
        double v = fraction(value);
        int sector = (int) Math.floor(h) % 6;
        double fraction = h - Math.floor(h);
        double p = v * (1 - s);
        double q = v * (1 - fraction * s);
        double t = v * (1 - (1 - fraction) * s);
        double[] rgb = switch (sector) {
            case 0 -> new double[]{v, t, p};
            case 1 -> new double[]{q, v, p};
            case 2 -> new double[]{p, v, t};
            case 3 -> new double[]{p, q, v};
            case 4 -> new double[]{t, p, v};
            default -> new double[]{v, p, q};
        };
        return ColorValue.converted(rgb[0], rgb[1], rgb[2], alpha, ColorSpace.HSV,
                clipped, clipped ? COMPONENT_WARNING : "");
    }

    public static ColorValue fromHwb(double hue, double whiteness, double blackness, double alpha) {
        boolean clipped = outsidePercent(whiteness) || outsidePercent(blackness) || outside(alpha);
        double white = fraction(whiteness);
        double black = fraction(blackness);
        if (white + black >= 1) {
            double gray = white / (white + black);
            return ColorValue.converted(gray, gray, gray, alpha, ColorSpace.HWB,
                    clipped, clipped ? COMPONENT_WARNING : "");
        }
        ColorValue base = fromHsv(hue, 100, 100, alpha);
        double factor = 1 - white - black;
        return ColorValue.converted(base.red() * factor + white, base.green() * factor + white,
                base.blue() * factor + white, alpha, ColorSpace.HWB,
                clipped, clipped ? COMPONENT_WARNING : "");
    }

    public static ColorValue fromLab(double lightness, double a, double b, double alpha) {
        double fy = (lightness + 16) / 116.0;
        double fx = fy + a / 500.0;
        double fz = fy - b / 200.0;
        double x = XN * inverseLabPivot(fx);
        double y = YN * inverseLabPivot(fy);
        double z = ZN * inverseLabPivot(fz);
        return fromXyz(x, y, z, alpha, ColorSpace.CIELAB);
    }

    public static ColorValue fromLch(double lightness, double chroma, double hue, double alpha) {
        double radians = Math.toRadians(wrapHue(hue));
        ColorValue value = fromLab(lightness, chroma * Math.cos(radians), chroma * Math.sin(radians), alpha);
        return value.withActiveSpace(ColorSpace.LCH);
    }

    public static ColorValue fromOklab(double lightness, double a, double b, double alpha) {
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        double red = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        double green = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        double blue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
        return fromLinearRgb(red, green, blue, alpha, ColorSpace.OKLAB);
    }

    public static ColorValue fromOklch(double lightness, double chroma, double hue, double alpha) {
        double radians = Math.toRadians(wrapHue(hue));
        ColorValue value = fromOklab(lightness, chroma * Math.cos(radians), chroma * Math.sin(radians), alpha);
        return value.withActiveSpace(ColorSpace.OKLCH);
    }

    public static ColorValue fromCmyk(double cyan, double magenta, double yellow, double black, double alpha) {
        boolean clipped = outsidePercent(cyan) || outsidePercent(magenta) || outsidePercent(yellow)
                || outsidePercent(black) || outside(alpha);
        double c = fraction(cyan);
        double m = fraction(magenta);
        double y = fraction(yellow);
        double k = fraction(black);
        return ColorValue.converted((1 - c) * (1 - k), (1 - m) * (1 - k),
                (1 - y) * (1 - k), alpha, ColorSpace.CMYK,
                clipped, clipped ? COMPONENT_WARNING : "");
    }

    public static String format(ColorValue color, ColorSpace space) {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(space, "space");
        return switch (space) {
            case NAMED -> namedColor(color).orElse("not-defined");
            case HEX -> color.toHex();
            case HEX8 -> color.toHex8();
            case RGB -> String.format(Locale.ROOT, "rgb(%d, %d, %d)",
                    channel(color.red()), channel(color.green()), channel(color.blue()));
            case RGBA -> String.format(Locale.ROOT, "rgba(%d, %d, %d, %.4f)",
                    channel(color.red()), channel(color.green()), channel(color.blue()), color.alpha());
            case HSL, HSLA -> formatHsl(color, space == ColorSpace.HSLA);
            case HSV, HSB -> formatHsv(color, space);
            case HWB -> formatHwb(color);
            case CIELAB -> formatLab(color);
            case LCH -> formatLch(color);
            case OKLAB -> formatOklab(color);
            case OKLCH -> formatOklch(color);
            case CMYK -> formatCmyk(color);
        };
    }

    public static Map<ColorSpace, String> allRepresentations(ColorValue color) {
        EnumMap<ColorSpace, String> values = new EnumMap<>(ColorSpace.class);
        for (ColorSpace space : ColorSpace.values()) values.put(space, format(color, space));
        return Collections.unmodifiableMap(values);
    }

    public static Map<String, String> namedColors() {
        LinkedHashMap<String, String> colors = new LinkedHashMap<>();
        colors.put("transparent", "#00000000");
        colors.put("black", "#000000FF");
        colors.put("white", "#FFFFFFFF");
        colors.put("red", "#FF0000FF");
        colors.put("lime", "#00FF00FF");
        colors.put("blue", "#0000FFFF");
        colors.put("yellow", "#FFFF00FF");
        colors.put("cyan", "#00FFFFFF");
        colors.put("aqua", "#00FFFFFF");
        colors.put("magenta", "#FF00FFFF");
        colors.put("fuchsia", "#FF00FFFF");
        colors.put("gray", "#808080FF");
        colors.put("grey", "#808080FF");
        colors.put("silver", "#C0C0C0FF");
        colors.put("maroon", "#800000FF");
        colors.put("olive", "#808000FF");
        colors.put("green", "#008000FF");
        colors.put("purple", "#800080FF");
        colors.put("teal", "#008080FF");
        colors.put("navy", "#000080FF");
        colors.put("orange", "#FFA500FF");
        colors.put("pink", "#FFC0CBFF");
        colors.put("brown", "#A52A2AFF");
        colors.put("rebeccapurple", "#663399FF");
        return Collections.unmodifiableMap(colors);
    }

    private static Map<String, String> reverseNamedColors() {
        LinkedHashMap<String, String> reverse = new LinkedHashMap<>();
        NAMED.forEach((name, hex) -> {
            if (!"transparent".equals(name)) reverse.putIfAbsent(hex.toUpperCase(Locale.ROOT), name);
        });
        reverse.put("#00000000", "transparent");
        return Collections.unmodifiableMap(reverse);
    }

    private static String opaqueKey(ColorValue color) {
        return color.toHex8().toUpperCase(Locale.ROOT);
    }

    private static ColorValue parseRgb(ColorSpace space, String text) {
        String[] values = components(text);
        requireCount(values, space == ColorSpace.RGBA ? 4 : 3, "RGB");
        double alpha = values.length == 4 ? alpha(values[3]) : 1;
        return fromRgb(rgb(values[0]), rgb(values[1]), rgb(values[2]), alpha).withActiveSpace(space);
    }

    private static ColorValue parseHsl(ColorSpace space, String text) {
        String[] values = components(text);
        requireCount(values, space == ColorSpace.HSLA ? 4 : 3, "HSL");
        return fromHsl(number(values[0]), percent(values[1]), percent(values[2]),
                values.length == 4 ? alpha(values[3]) : 1).withActiveSpace(space);
    }

    private static ColorValue parseHsv(ColorSpace space, String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("HSV/HSB needs 3 or 4 values");
        return fromHsv(number(values[0]), percent(values[1]), percent(values[2]),
                values.length == 4 ? alpha(values[3]) : 1).withActiveSpace(space);
    }

    private static ColorValue parseHwb(String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("HWB needs 3 or 4 values");
        return fromHwb(number(values[0]), percent(values[1]), percent(values[2]),
                values.length == 4 ? alpha(values[3]) : 1);
    }

    private static ColorValue parseLab(String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("Lab needs 3 or 4 values");
        return fromLab(percentOrNumber(values[0], 100), number(values[1]), number(values[2]),
                values.length == 4 ? alpha(values[3]) : 1);
    }

    private static ColorValue parseLch(String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("LCH needs 3 or 4 values");
        return fromLch(percentOrNumber(values[0], 100), number(values[1]), number(values[2]),
                values.length == 4 ? alpha(values[3]) : 1);
    }

    private static ColorValue parseOklab(String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("OKLab needs 3 or 4 values");
        return fromOklab(percentOrNumber(values[0], 1), number(values[1]), number(values[2]),
                values.length == 4 ? alpha(values[3]) : 1);
    }

    private static ColorValue parseOklch(String text) {
        String[] values = components(text);
        if (values.length != 3 && values.length != 4) throw new IllegalArgumentException("OKLCH needs 3 or 4 values");
        return fromOklch(percentOrNumber(values[0], 1), number(values[1]), number(values[2]),
                values.length == 4 ? alpha(values[3]) : 1);
    }

    private static ColorValue parseCmyk(String text) {
        String[] values = components(text);
        if (values.length != 4 && values.length != 5) throw new IllegalArgumentException("CMYK needs 4 or 5 values");
        return fromCmyk(percent(values[0]), percent(values[1]), percent(values[2]), percent(values[3]),
                values.length == 5 ? alpha(values[4]) : 1);
    }

    private static String formatHsl(ColorValue color, boolean includeAlpha) {
        double max = Math.max(color.red(), Math.max(color.green(), color.blue()));
        double min = Math.min(color.red(), Math.min(color.green(), color.blue()));
        double lightness = (max + min) / 2;
        double saturation;
        double hue;
        if (max == min) {
            hue = saturation = 0;
        } else {
            double delta = max - min;
            saturation = lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
            if (max == color.red()) hue = ((color.green() - color.blue()) / delta + (color.green() < color.blue() ? 6 : 0));
            else if (max == color.green()) hue = (color.blue() - color.red()) / delta + 2;
            else hue = (color.red() - color.green()) / delta + 4;
            hue *= 60;
        }
        return includeAlpha
                ? String.format(Locale.ROOT, "hsla(%.2f, %.2f%%, %.2f%%, %.4f)", hue, saturation * 100, lightness * 100, color.alpha())
                : String.format(Locale.ROOT, "hsl(%.2f, %.2f%%, %.2f%%)", hue, saturation * 100, lightness * 100);
    }

    private static String formatHsv(ColorValue color, ColorSpace space) {
        double max = Math.max(color.red(), Math.max(color.green(), color.blue()));
        double min = Math.min(color.red(), Math.min(color.green(), color.blue()));
        double delta = max - min;
        double hue;
        if (delta == 0) hue = 0;
        else if (max == color.red()) hue = 60 * (((color.green() - color.blue()) / delta) % 6);
        else if (max == color.green()) hue = 60 * ((color.blue() - color.red()) / delta + 2);
        else hue = 60 * ((color.red() - color.green()) / delta + 4);
        if (hue < 0) hue += 360;
        double saturation = max == 0 ? 0 : delta / max;
        return String.format(Locale.ROOT, "%s(%.2f, %.2f%%, %.2f%%, %.4f)",
                space == ColorSpace.HSB ? "hsb" : "hsv", hue, saturation * 100, max * 100, color.alpha());
    }

    private static String formatHwb(ColorValue color) {
        double[] hsv = hsv(color);
        double white = Math.min(color.red(), Math.min(color.green(), color.blue()));
        double black = 1 - Math.max(color.red(), Math.max(color.green(), color.blue()));
        return String.format(Locale.ROOT, "hwb(%.2f, %.2f%%, %.2f%%, %.4f)",
                hsv[0], white * 100, black * 100, color.alpha());
    }

    private static String formatLab(ColorValue color) {
        double[] lab = lab(color);
        return String.format(Locale.ROOT, "lab(%.4f, %.4f, %.4f, %.4f)", lab[0], lab[1], lab[2], color.alpha());
    }

    private static String formatLch(ColorValue color) {
        double[] lab = lab(color);
        double chroma = Math.hypot(lab[1], lab[2]);
        double hue = wrapHue(Math.toDegrees(Math.atan2(lab[2], lab[1])));
        return String.format(Locale.ROOT, "lch(%.4f, %.4f, %.4f, %.4f)", lab[0], chroma, hue, color.alpha());
    }

    private static String formatOklab(ColorValue color) {
        double[] lab = oklab(color);
        return String.format(Locale.ROOT, "oklab(%.6f, %.6f, %.6f, %.4f)", lab[0], lab[1], lab[2], color.alpha());
    }

    private static String formatOklch(ColorValue color) {
        double[] lab = oklab(color);
        double chroma = Math.hypot(lab[1], lab[2]);
        double hue = wrapHue(Math.toDegrees(Math.atan2(lab[2], lab[1])));
        return String.format(Locale.ROOT, "oklch(%.6f, %.6f, %.4f, %.4f)", lab[0], chroma, hue, color.alpha());
    }

    private static String formatCmyk(ColorValue color) {
        double key = 1 - Math.max(color.red(), Math.max(color.green(), color.blue()));
        double cyan = key >= 1 ? 0 : (1 - color.red() - key) / (1 - key);
        double magenta = key >= 1 ? 0 : (1 - color.green() - key) / (1 - key);
        double yellow = key >= 1 ? 0 : (1 - color.blue() - key) / (1 - key);
        return String.format(Locale.ROOT, "cmyk(%.2f%%, %.2f%%, %.2f%%, %.2f%%, %.4f)",
                cyan * 100, magenta * 100, yellow * 100, key * 100, color.alpha());
    }

    private static ColorValue fromXyz(double x, double y, double z, double alpha, ColorSpace space) {
        double red = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
        double green = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
        double blue = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
        return fromLinearRgb(red, green, blue, alpha, space);
    }

    private static ColorValue fromLinearRgb(double red, double green, double blue, double alpha, ColorSpace space) {
        double encodedRed = gamma(red);
        double encodedGreen = gamma(green);
        double encodedBlue = gamma(blue);
        boolean clipped = outside(encodedRed) || outside(encodedGreen) || outside(encodedBlue);
        return ColorValue.converted(encodedRed, encodedGreen, encodedBlue, alpha, space, clipped,
                clipped ? GAMUT_WARNING : "");
    }

    private static double[] lab(ColorValue color) {
        double red = linear(color.red());
        double green = linear(color.green());
        double blue = linear(color.blue());
        double x = (0.4124564 * red + 0.3575761 * green + 0.1804375 * blue) / XN;
        double y = (0.2126729 * red + 0.7151522 * green + 0.0721750 * blue) / YN;
        double z = (0.0193339 * red + 0.1191920 * green + 0.9503041 * blue) / ZN;
        double fx = labPivot(x);
        double fy = labPivot(y);
        double fz = labPivot(z);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double[] oklab(ColorValue color) {
        double red = linear(color.red());
        double green = linear(color.green());
        double blue = linear(color.blue());
        double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
        double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
        double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    private static double[] hsv(ColorValue color) {
        double max = Math.max(color.red(), Math.max(color.green(), color.blue()));
        double min = Math.min(color.red(), Math.min(color.green(), color.blue()));
        double delta = max - min;
        double hue;
        if (delta == 0) hue = 0;
        else if (max == color.red()) hue = 60 * (((color.green() - color.blue()) / delta) % 6);
        else if (max == color.green()) hue = 60 * ((color.blue() - color.red()) / delta + 2);
        else hue = 60 * ((color.red() - color.green()) / delta + 4);
        if (hue < 0) hue += 360;
        return new double[]{hue, max == 0 ? 0 : delta / max, max};
    }

    private static String[] components(String text) {
        String value = Objects.requireNonNull(text, "text").trim();
        int open = value.indexOf('(');
        int close = value.lastIndexOf(')');
        if (open >= 0) {
            if (close <= open) throw new IllegalArgumentException("Color function is missing a closing parenthesis");
            value = value.substring(open + 1, close);
        }
        value = value.replace(',', ' ').replace('/', ' ').trim();
        return value.isEmpty() ? new String[0] : value.split("\\s+");
    }

    private static void requireCount(String[] values, int count, String name) {
        if (values.length != count) throw new IllegalArgumentException(name + " needs " + count + " values");
    }

    private static double rgb(String value) {
        return value.endsWith("%") ? percent(value) * 2.55 : number(value);
    }

    private static double alpha(String value) {
        return value.endsWith("%") ? percent(value) / 100.0 : number(value);
    }

    private static double percent(String value) {
        return value.endsWith("%") ? number(value.substring(0, value.length() - 1)) : number(value);
    }

    private static double percentOrNumber(String value, double percentScale) {
        return value.endsWith("%") ? number(value.substring(0, value.length() - 1)) / 100.0 * percentScale : number(value);
    }

    private static double number(String value) {
        double parsed = Double.parseDouble(value.trim());
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Color component must be finite");
        return parsed;
    }

    private static double fraction(double percent) {
        return Math.max(0, Math.min(100, percent)) / 100.0;
    }

    private static boolean outsidePercent(double value) {
        return !Double.isFinite(value) || value < 0 || value > 100;
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }

    private static double wrapHue(double hue) {
        double wrapped = hue % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }

    private static double labPivot(double value) {
        double delta = 6.0 / 29.0;
        return value > Math.pow(delta, 3) ? Math.cbrt(value) : value / (3 * delta * delta) + 4.0 / 29.0;
    }

    private static double inverseLabPivot(double value) {
        double delta = 6.0 / 29.0;
        return value > delta ? value * value * value : 3 * delta * delta * (value - 4.0 / 29.0);
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double gamma(double value) {
        return value <= 0.0031308 ? 12.92 * value : 1.055 * Math.pow(value, 1 / 2.4) - 0.055;
    }

    private static boolean outside(double value) {
        return !Double.isFinite(value) || value < -1.0e-9 || value > 1.0 + 1.0e-9;
    }

    private static int channel(double value) {
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
