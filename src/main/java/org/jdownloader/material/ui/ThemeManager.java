package org.jdownloader.material.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Owns a scene's stylesheet stack: one theme-token file (light or dark) plus
 * the shared component stylesheet. Toggling {@link #darkProperty()} swaps only
 * the token file, so every {@code -md-*} lookup re-resolves and the whole UI
 * re-themes instantly.
 */
public final class ThemeManager {

    private static final String BASE = res("/css/material.css");
    private static final String LIGHT = res("/css/theme-light.css");
    private static final String DARK = res("/css/theme-dark.css");

    private final BooleanProperty dark = new SimpleBooleanProperty(false);
    private Scene scene;

    public ThemeManager() {
        dark.addListener((o, was, is) -> refresh());
    }

    public void install(Scene scene) {
        this.scene = scene;
        refresh();
    }

    private void refresh() {
        if (scene == null) return;
        scene.getStylesheets().removeAll(LIGHT, DARK, BASE);
        scene.getStylesheets().add(dark.get() ? DARK : LIGHT);
        scene.getStylesheets().add(BASE); // component rules load after tokens
    }

    public BooleanProperty darkProperty() {
        return dark;
    }

    public boolean isDark() {
        return dark.get();
    }

    public void toggle() {
        dark.set(!dark.get());
    }

    private static String res(String path) {
        return Objects.requireNonNull(ThemeManager.class.getResource(path),
                "Missing stylesheet: " + path).toExternalForm();
    }
}
