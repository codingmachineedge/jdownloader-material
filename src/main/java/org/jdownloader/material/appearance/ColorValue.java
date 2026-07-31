package org.jdownloader.material.appearance;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable sRGB color plus the notation that was active when it was edited.
 * Values are render-safe; {@link #clipped()} records whether conversion had to
 * clip an out-of-gamut component rather than quietly pretending it fitted.
 */
public final class ColorValue {

    private static final double EPSILON = 1.0e-12;

    private final double red;
    private final double green;
    private final double blue;
    private final double alpha;
    private final ColorSpace activeSpace;
    private final ColorGamut gamut;
    private final boolean clipped;
    private final String clippingWarning;

    private ColorValue(double red, double green, double blue, double alpha, ColorSpace activeSpace,
                       boolean clipped, String clippingWarning) {
        boolean outside = outside(red) || outside(green) || outside(blue) || outside(alpha);
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
        this.activeSpace = Objects.requireNonNull(activeSpace, "activeSpace");
        this.gamut = ColorGamut.SRGB;
        this.clipped = clipped || outside;
        String warning = Objects.requireNonNullElse(clippingWarning, "").trim();
        this.clippingWarning = this.clipped
                ? (warning.isEmpty() ? "Converted color was clipped to the sRGB gamut." : warning)
                : "";
    }

    public static ColorValue srgb(double red, double green, double blue, double alpha) {
        return converted(red, green, blue, alpha, ColorSpace.RGBA, false, "");
    }

    /** Advanced factory used by translated UI input while retaining clipping diagnostics. */
    public static ColorValue converted(double red, double green, double blue, double alpha, ColorSpace activeSpace,
                                       boolean clipped, String warning) {
        return new ColorValue(red, green, blue, alpha, activeSpace, clipped, warning);
    }

    public double red() { return red; }
    public double green() { return green; }
    public double blue() { return blue; }
    public double alpha() { return alpha; }
    public ColorSpace activeSpace() { return activeSpace; }
    public ColorGamut gamut() { return gamut; }
    public boolean clipped() { return clipped; }
    public boolean inGamut() { return !clipped; }
    public String clippingWarning() { return clippingWarning; }

    public ColorValue withActiveSpace(ColorSpace space) {
        return converted(red, green, blue, alpha, space, clipped, clippingWarning);
    }

    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", channel(red), channel(green), channel(blue));
    }

    public String toHex8() {
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
                channel(red), channel(green), channel(blue), channel(alpha));
    }

    public String toCssRgba() {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.4f)",
                channel(red), channel(green), channel(blue), alpha);
    }

    public double relativeLuminance() {
        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
    }

    public double contrastRatio(ColorValue other) {
        Objects.requireNonNull(other, "other");
        double lighter = Math.max(relativeLuminance(), other.relativeLuminance());
        double darker = Math.min(relativeLuminance(), other.relativeLuminance());
        return (lighter + 0.05) / (darker + 0.05);
    }

    public ColorValue recommendedTextColor() {
        ColorValue black = srgb(0, 0, 0, 1);
        ColorValue white = srgb(1, 1, 1, 1);
        return contrastRatio(black) >= contrastRatio(white) ? black : white;
    }

    /** Stable profile representation retaining active notation and clipping diagnostics. */
    public String toStorageString() {
        String warning = clippingWarning.isEmpty() ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(clippingWarning.getBytes(StandardCharsets.UTF_8));
        return "v1|" + activeSpace.name() + "|" + toHex8() + "|" + (clipped ? "1" : "0") + "|" + warning;
    }

    public static ColorValue fromStorageString(String stored) {
        String value = Objects.requireNonNull(stored, "stored").trim();
        if (!value.startsWith("v1|")) return ColorTranslator.parse(value);
        String[] parts = value.split("\\|", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Invalid persisted color value");
        ColorSpace space = ColorSpace.valueOf(parts[1]);
        ColorValue parsed = ColorTranslator.fromHex(parts[2]).withActiveSpace(space);
        boolean clipped = switch (parts[3]) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalArgumentException("Invalid persisted clipping flag");
        };
        String warning = parts[4].isEmpty() ? "" : new String(
                Base64.getUrlDecoder().decode(parts[4]), StandardCharsets.UTF_8);
        if (warning.length() > 1_024) throw new IllegalArgumentException("Persisted color warning is too long");
        return converted(parsed.red, parsed.green, parsed.blue, parsed.alpha, space, clipped, warning);
    }

    private static int channel(double value) {
        return (int) Math.round(clamp(value) * 255.0);
    }

    private static boolean outside(double value) {
        return !Double.isFinite(value) || value < -EPSILON || value > 1.0 + EPSILON;
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Color components must be finite");
        return Math.max(0, Math.min(1, value));
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ColorValue value)) return false;
        return Double.compare(red, value.red) == 0
                && Double.compare(green, value.green) == 0
                && Double.compare(blue, value.blue) == 0
                && Double.compare(alpha, value.alpha) == 0
                && activeSpace == value.activeSpace
                && gamut == value.gamut
                && clipped == value.clipped
                && clippingWarning.equals(value.clippingWarning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(red, green, blue, alpha, activeSpace, gamut, clipped, clippingWarning);
    }

    @Override
    public String toString() {
        return toHex8() + " (" + activeSpace + ", " + gamut + (clipped ? ", clipped" : "") + ")";
    }
}
