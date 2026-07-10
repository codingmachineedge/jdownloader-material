package org.jdownloader.material.ui.component;

import io.github.palexdev.materialfx.controls.MFXButton;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * A single, unobtrusive snackbar lane for optional feedback and Undo actions.
 * Workflows use inline views and status text; this layer never opens a dialog
 * or piles cards over the active screen.
 */
public final class NotificationCenter extends StackPane {

    private final VBox snackStack = new VBox(8);

    public NotificationCenter() {
        getStyleClass().add("notification-layer");
        setPickOnBounds(false);
        snackStack.setPickOnBounds(false);
        snackStack.setFillWidth(false);
        snackStack.setMaxWidth(Region.USE_PREF_SIZE);
        snackStack.setMaxHeight(Region.USE_PREF_SIZE);
        snackStack.setAlignment(Pos.BOTTOM_CENTER);
        StackPane.setAlignment(snackStack, Pos.BOTTOM_CENTER);
        StackPane.setMargin(snackStack, new Insets(0, 0, 24, 0));
        getChildren().add(snackStack);
    }

    public void snack(String message) {
        snack(message, null, null);
    }

    /** Clears the optional feedback lane, used by deterministic visual capture. */
    public void clear() {
        snackStack.getChildren().clear();
    }

    /** Shows one optional, self-dismissing message with an optional action. */
    public void snack(String message, String actionLabel, Runnable action) {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("snackbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label text = new Label(message);
        text.getStyleClass().add("snackbar-text");
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);
        bar.getChildren().add(text);

        if (actionLabel != null && !actionLabel.isBlank()) {
            MFXButton act = Mat.text(actionLabel, null);
            act.getStyleClass().setAll("snackbar-action");
            act.setOnAction(e -> {
                dismiss(bar);
                if (action != null) action.run();
            });
            bar.getChildren().add(act);
        }

        // A new result replaces stale feedback instead of covering the UI.
        snackStack.getChildren().setAll(bar);
        animateIn(bar);
        autoDismissLater(bar, 4000);
    }

    /** Compatibility aliases for concise, nonblocking background status. */
    public void info(String title, String body) {
        snack(summary(title, body));
    }

    public void success(String title, String body) {
        snack(summary(title, body));
    }

    public void error(String title, String body) {
        snack(summary(title, body));
    }

    private static String summary(String title, String body) {
        if (body == null || body.isBlank()) return title;
        return title + " - " + body;
    }

    private void animateIn(Node node) {
        node.setOpacity(0);
        node.setTranslateY(12);
        FadeTransition fade = new FadeTransition(Duration.millis(160), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(180), node);
        slide.setToY(0);
        fade.play();
        slide.play();
    }

    private void autoDismissLater(Node node, long ms) {
        PauseTransition wait = new PauseTransition(Duration.millis(ms));
        wait.setOnFinished(e -> dismiss(node));
        wait.play();
    }

    private void dismiss(Node node) {
        if (!snackStack.getChildren().contains(node)) return;
        FadeTransition fade = new FadeTransition(Duration.millis(140), node);
        fade.setToValue(0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(140), node);
        slide.setToY(8);
        fade.setOnFinished(e -> snackStack.getChildren().remove(node));
        fade.play();
        slide.play();
    }
}
