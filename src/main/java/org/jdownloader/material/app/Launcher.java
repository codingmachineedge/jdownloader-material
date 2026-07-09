package org.jdownloader.material.app;

/**
 * Plain (non-Application) entry point.
 * <p>
 * Launching JavaFX from a class that does <em>not</em> extend {@code Application}
 * avoids the "JavaFX runtime components are missing" error when the app is run
 * from a shaded/fat jar without the module path.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        JDMaterialApp.main(args);
    }
}
