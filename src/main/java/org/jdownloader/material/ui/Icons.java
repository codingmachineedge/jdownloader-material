package org.jdownloader.material.ui;

import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.Map;

/**
 * Material Design icon factory. Each icon is a 24dp SVG path rendered as a
 * {@link Region} whose {@code -fx-background-color} fills the shape, so icons
 * recolor from CSS tokens and scale crisply to any size.
 */
public final class Icons {
    private Icons() {
    }

    private static final Map<String, String> PATHS = Map.ofEntries(
            Map.entry("download", "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"),
            Map.entry("add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"),
            Map.entry("play", "M8 5v14l11-7z"),
            Map.entry("pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z"),
            Map.entry("stop", "M6 6h12v12H6z"),
            Map.entry("delete", "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"),
            Map.entry("settings", "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"),
            Map.entry("search", "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"),
            Map.entry("link", "M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z"),
            Map.entry("folder", "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"),
            Map.entry("paste", "M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm2 18H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V8h10v2z"),
            Map.entry("moon", "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"),
            Map.entry("sun", "M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06z"),
            Map.entry("close", "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"),
            Map.entry("minimize", "M6 19h12v2H6z"),
            Map.entry("maximize", "M5 5v14h14V5H5zm2 2h10v10H7V7z"),
            Map.entry("restore", "M7 7h10v10H7V7zm-4 2v12h12v-2H5V9h10V3H9v2h4v2H7z"),
            Map.entry("more", "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"),
            Map.entry("check", "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"),
            Map.entry("up", "M7.41 15.41L12 10.83l4.59 4.58L18 14l-6-6-6 6z"),
            Map.entry("down", "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z"),
            Map.entry("top", "M4 4h16v2H4zm4 6h8v10h-8zM8 8l4-4 4 4z"),
            Map.entry("bottom", "M4 18h16v2H4zm4-4h8V4H8zm0 0l4 4 4-4z"),
            Map.entry("reconnect", "M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"),
            Map.entry("tune", "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"),
            Map.entry("info", "M11 7h2v2h-2zm0 4h2v6h-2zm1-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"),
            Map.entry("cloud", "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z"),
            Map.entry("speed", "M20.38 8.57l-1.23 1.85a8 8 0 0 1-.22 7.58H5.07A8 8 0 0 1 15.58 6.85l1.85-1.23A10 10 0 0 0 3.35 19a2 2 0 0 0 1.72 1h13.85a2 2 0 0 0 1.74-1 10 10 0 0 0-.27-10.44zm-9.79 6.84a2 2 0 0 0 2.83 0l5.66-8.49-8.49 5.66a2 2 0 0 0 0 2.83z"),
            Map.entry("account", "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"),
            Map.entry("shield", "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"),
            Map.entry("palette", "M12 2C6.49 2 2 6.49 2 12s4.49 10 10 10c1.38 0 2.5-1.12 2.5-2.5 0-.61-.23-1.2-.64-1.67a.528.528 0 0 1-.13-.33c0-.28.22-.5.5-.5H16c3.31 0 6-2.69 6-6 0-4.96-4.49-9-10-9zm5.5 11c-.83 0-1.5-.67-1.5-1.5S16.67 10 17.5 10s1.5.67 1.5 1.5-.67 1.5-1.5 1.5zm-3-4C13.67 9 13 8.33 13 7.5S13.67 6 14.5 6s1.5.67 1.5 1.5S15.33 9 14.5 9zM5 11.5C5 10.67 5.67 10 6.5 10S8 10.67 8 11.5 7.33 13 6.5 13 5 12.33 5 11.5zm4-4C9 6.67 9.67 6 10.5 6s1.5.67 1.5 1.5S11.33 9 10.5 9 9 8.33 9 7.5z")
    );

    /** An icon region sized to {@code size} dp, colored by the {@code mat-icon} style class. */
    public static Region of(String name, double size) {
        String content = PATHS.getOrDefault(name, PATHS.get("info"));
        SVGPath shape = new SVGPath();
        shape.setContent(content);
        Region r = new Region();
        r.setShape(shape);
        r.setMinSize(size, size);
        r.setPrefSize(size, size);
        r.setMaxSize(size, size);
        r.getStyleClass().add("mat-icon");
        r.setScaleShape(true);
        return r;
    }

    /** An icon with an extra style class used to override its color. */
    public static Region of(String name, double size, String colorClass) {
        Region r = of(name, size);
        r.getStyleClass().add(colorClass);
        return r;
    }
}
