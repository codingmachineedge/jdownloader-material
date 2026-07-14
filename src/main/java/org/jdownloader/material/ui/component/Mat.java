package org.jdownloader.material.ui.component;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.jdownloader.material.ui.Icons;

/** Factory helpers for the recurring Material widgets used across the views. */
public final class Mat {
    private Mat() {
    }

    private static MFXButton base(String text, String styleClass) {
        MFXButton b = new MFXButton(text);
        b.getStyleClass().add(styleClass);
        b.setButtonType(io.github.palexdev.materialfx.enums.ButtonType.FLAT);
        return b;
    }

    public static MFXButton filled(String text, String icon) {
        MFXButton b = base(text, "filled-button");
        if (icon != null) withIcon(b, icon);
        return b;
    }

    public static MFXButton tonal(String text, String icon) {
        MFXButton b = base(text, "tonal-button");
        if (icon != null) withIcon(b, icon);
        return b;
    }

    public static MFXButton outlined(String text, String icon) {
        MFXButton b = base(text, "outlined-button");
        if (icon != null) withIcon(b, icon);
        return b;
    }

    public static MFXButton text(String text, String icon) {
        MFXButton b = base(text, "text-button");
        if (icon != null) withIcon(b, icon);
        return b;
    }

    /** A 40dp circular icon-only button with a tooltip. */
    public static MFXButton icon(String icon, String tooltip) {
        MFXButton b = base("", "icon-button");
        b.setGraphic(Icons.of(icon, 20));
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        if (tooltip != null && !tooltip.isBlank()) {
            tip(b, tooltip);
            // Icon-only controls have no visible text for assistive technology
            // to derive a name from. Keep the spoken name in step with the
            // user-facing tooltip supplied by the caller.
            b.setAccessibleText(tooltip);
            b.setAccessibleHelp(tooltip);
        }
        return b;
    }

    private static void withIcon(MFXButton b, String icon) {
        b.setGraphic(Icons.of(icon, 18));
        b.setContentDisplay(ContentDisplay.LEFT);
        b.setGraphicTextGap(8);
    }

    public static void tip(javafx.scene.control.Control node, String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(Duration.millis(400));
        node.setTooltip(t);
    }

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    /** A pill-shaped status chip whose color follows the given state style class. */
    public static Label chip(String text, String stateStyleClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("status-chip", stateStyleClass);
        return l;
    }

    public static Region hSpacer() {
        Region r = new Region();
        javafx.scene.layout.HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    public static Region vSep() {
        Region r = new Region();
        r.getStyleClass().add("toolbar-sep");
        return r;
    }

    public static Insets insets(double v) {
        return new Insets(v);
    }
}
