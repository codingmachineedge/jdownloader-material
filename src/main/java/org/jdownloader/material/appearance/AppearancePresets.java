package org.jdownloader.material.appearance;

import java.util.List;

/** Built-in named Material presets. They are immutable and never overwrite user presets. */
public final class AppearancePresets {

    private static final String CJK = "Microsoft JhengHei UI, Noto Sans CJK TC, Segoe UI, sans-serif";

    private static final List<AppearancePreset> BUILT_INS = List.of(
            new AppearancePreset("material-light", "Material Light", true,
                    ThemeMode.LIGHT, Density.STANDARD,
                    ColorTranslator.fromHex("#006B5C"), ColorTranslator.fromHex("#006B5C"),
                    "Segoe UI Variable", FontSource.INSTALLED, 1.0, 400, CJK,
                    new AppearanceStyle()),
            new AppearancePreset("material-dark", "Material Dark", true,
                    ThemeMode.DARK, Density.STANDARD,
                    ColorTranslator.fromHex("#8BD8C5"), ColorTranslator.fromHex("#8BD8C5"),
                    "Segoe UI Variable", FontSource.INSTALLED, 1.0, 400, CJK,
                    new AppearanceStyle()),
            new AppearancePreset("comfortable-cjk", "Comfortable CJK", true,
                    ThemeMode.LIGHT, Density.COMFORTABLE,
                    ColorTranslator.fromHex("#315F9F"), ColorTranslator.fromHex("#315F9F"),
                    "Microsoft JhengHei UI", FontSource.INSTALLED, 1.08, 450, CJK,
                    new AppearanceStyle().set(AppearanceProperty.LINE_HEIGHT, 1.35)
                            .set(AppearanceProperty.CHARACTER_SPACING, 0.15)));

    private AppearancePresets() {
    }

    public static List<AppearancePreset> builtIns() {
        return BUILT_INS;
    }
}
