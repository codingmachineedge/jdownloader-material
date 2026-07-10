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
import org.jdownloader.material.ui.Icons;

import java.util.function.Function;

/**
 * A transparent overlay that hosts all in-app notifications, replacing modal
 * dialogs. Snackbars stack at the bottom center; notification cards and rich
 * panels stack at the top right. Empty areas are click-through, so the app
 * behind the layer stays usable (notifications are non-modal).
 */
public final class NotificationCenter extends StackPane {

    private final VBox cardStack = new VBox(12);
    private final VBox snackStack = new VBox(8);

    public NotificationCenter() {
        getStyleClass().add("notification-layer");
        setPickOnBounds(false);

        configureStack(cardStack, Pos.TOP_RIGHT, new Insets(16));
        configureStack(snackStack, Pos.BOTTOM_CENTER, new Insets(0, 0, 24, 0));
        snackStack.setAlignment(Pos.BOTTOM_CENTER);

        getChildren().addAll(cardStack, snackStack);
    }

    private void configureStack(VBox stack, Pos pos, Insets margin) {
        stack.setPickOnBounds(false);
        stack.setFillWidth(false);
        stack.setMaxWidth(Region.USE_PREF_SIZE);
        stack.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(stack, pos);
        StackPane.setMargin(stack, margin);
    }

    // ------------------------------------------------------------- Snackbars
    public void snack(String message) {
        snack(message, null, null);
    }

    /** Clears transient notifications; used by visual documentation capture between states. */
    public void clear() {
        cardStack.getChildren().clear();
        snackStack.getChildren().clear();
    }

    /** A snackbar with an optional trailing action (e.g. "View", "Undo"). */
    public void snack(String message, String actionLabel, Runnable action) {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("snackbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        Label text = new Label(message);
        text.getStyleClass().add("snackbar-text");
        HBox.setHgrow(text, Priority.ALWAYS);
        bar.getChildren().add(text);
        if (actionLabel != null) {
            MFXButton act = Mat.text(actionLabel, null);
            act.getStyleClass().setAll("snackbar-action");
            act.setOnAction(e -> {
                dismiss(bar, snackStack);
                if (action != null) action.run();
            });
            bar.getChildren().add(act);
        }
        // keep at most a few snackbars visible
        while (snackStack.getChildren().size() >= 3) snackStack.getChildren().remove(0);
        present(bar, snackStack, true, 4000);
    }

    // --------------------------------------------------------- Toast cards
    public void info(String title, String body)    { card(title, body, "type-info", "info", true); }
    public void success(String title, String body) { card(title, body, "type-success", "check", true); }
    public void error(String title, String body)   { card(title, body, "type-error", "info", false); }

    private void card(String title, String body, String typeClass, String icon, boolean autoDismiss) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("notif-card", typeClass);

        Region accent = new Region();
        accent.getStyleClass().add("accent-bar");

        Region ic = Icons.of(icon, 20);
        ic.getStyleClass().add("notif-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("notif-title");
        MFXButton close = Mat.icon("close", "Dismiss");
        HBox header = new HBox(10, ic, titleLabel, Mat.hSpacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        Label bodyLabel = new Label(body);
        bodyLabel.getStyleClass().add("notif-body");
        bodyLabel.setWrapText(true);

        VBox texts = new VBox(4, header, bodyLabel);
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox content = new HBox(12, accent, texts);
        card.getChildren().add(content);

        close.setOnAction(e -> dismiss(card, cardStack));
        cardStack.getChildren().add(0, card);
        animateIn(card, false);
        if (autoDismiss) autoDismissLater(card, cardStack, 5000);
    }

    // ---------------------------------------------------------- Rich panels
    /**
     * Shows a persistent in-app panel (used for forms that used to be modal
     * dialogs). The builder receives a {@code close} handle so its own buttons
     * can dismiss the panel.
     */
    public void panel(String title, String icon, Function<Runnable, Node> bodyBuilder) {
        VBox card = new VBox(14);
        card.getStyleClass().addAll("notif-card", "panel-card");

        Runnable close = () -> dismiss(card, cardStack);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("panel-title");
        MFXButton closeBtn = Mat.icon("close", "Close");
        closeBtn.setOnAction(e -> close.run());
        HBox header = new HBox(10, Icons.of(icon, 22, "icon-primary"), titleLabel, Mat.hSpacer(), closeBtn);
        header.setAlignment(Pos.CENTER_LEFT);

        Node body = bodyBuilder.apply(close);
        card.getChildren().addAll(header, body);

        cardStack.getChildren().add(0, card);
        animateIn(card, false);
    }

    // ----------------------------------------------------------- Animation
    private void present(Node node, VBox stack, boolean fromBottom, long autoMs) {
        stack.getChildren().add(node);
        animateIn(node, fromBottom);
        if (autoMs > 0) autoDismissLater(node, stack, autoMs);
    }

    private void animateIn(Node node, boolean fromBottom) {
        node.setOpacity(0);
        if (fromBottom) node.setTranslateY(16);
        else node.setTranslateX(24);
        FadeTransition fade = new FadeTransition(Duration.millis(180), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), node);
        slide.setToX(0);
        slide.setToY(0);
        fade.play();
        slide.play();
    }

    private void autoDismissLater(Node node, VBox stack, long ms) {
        PauseTransition wait = new PauseTransition(Duration.millis(ms));
        wait.setOnFinished(e -> dismiss(node, stack));
        wait.play();
    }

    private void dismiss(Node node, VBox stack) {
        if (!stack.getChildren().contains(node)) return;
        FadeTransition fade = new FadeTransition(Duration.millis(160), node);
        fade.setToValue(0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(160), node);
        slide.setToX(16);
        fade.setOnFinished(e -> stack.getChildren().remove(node));
        fade.play();
        slide.play();
    }
}
