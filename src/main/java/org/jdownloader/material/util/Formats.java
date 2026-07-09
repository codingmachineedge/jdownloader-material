package org.jdownloader.material.util;

/** Human-readable formatting for byte sizes, speeds and durations. */
public final class Formats {
    private Formats() {
    }

    private static final String[] UNITS = {"B", "KiB", "MiB", "GiB", "TiB"};

    /** e.g. {@code 1536 -> "1.5 KiB"}. */
    public static String bytes(long v) {
        if (v <= 0) return "0 B";
        double val = v;
        int u = 0;
        while (val >= 1024 && u < UNITS.length - 1) {
            val /= 1024;
            u++;
        }
        return (u == 0 ? String.format("%.0f %s", val, UNITS[u])
                       : String.format("%.1f %s", val, UNITS[u]));
    }

    /** e.g. {@code 1048576 -> "1.0 MiB/s"}; zero renders as an em dash. */
    public static String speed(long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "—";
        return bytes(bytesPerSecond) + "/s";
    }

    /** Seconds to a compact {@code 1h 02m}, {@code 3m 04s}, {@code 12s} form. */
    public static String eta(long seconds) {
        if (seconds < 0) return "—";
        if (seconds == 0) return "0s";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    /** {@code 0.42 -> "42%"}; negatives (unknown) render as an em dash. */
    public static String percent(double fraction) {
        if (fraction < 0) return "—";
        return Math.round(fraction * 100) + "%";
    }
}
