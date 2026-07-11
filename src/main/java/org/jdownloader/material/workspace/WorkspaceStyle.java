package org.jdownloader.material.workspace;

import java.util.Locale;

/** User-selected typography for one workspace tab. */
public record WorkspaceStyle(String fontFamily, double fontSize, boolean bold, boolean italic, String color) {

    public static final WorkspaceStyle DEFAULT = new WorkspaceStyle("System", 13, false, false, "#1D1B20");

    public WorkspaceStyle {
        fontFamily = normalizedFamily(fontFamily);
        fontSize = Math.max(6, Math.min(160, fontSize));
        color = normalizedColor(color);
    }

    public static String normalizedFamily(String value) {
        if (value == null || value.isBlank()) return "System";
        String trimmed = value.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    public static String normalizedColor(String value) {
        if (value == null || value.isBlank()) return DEFAULT.color;
        String trimmed = value.trim();
        if (!trimmed.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return DEFAULT.color;
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
