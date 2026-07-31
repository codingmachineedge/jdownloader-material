package org.jdownloader.material.ui.component;

import java.io.InputStream;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jdownloader.material.dimsum.DimSumDish;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.i18n.I18n;

/** Non-modal, focus-preserving startup delight with a local image and alt text. */
public final class DimSumSurpriseOverlay extends StackPane {
    private final Settings settings;
    private final I18n i18n;
    private PauseTransition timeout;

    public DimSumSurpriseOverlay(Settings settings, I18n i18n) {
        this.settings = settings;
        this.i18n = i18n;
        getStyleClass().add("dim-sum-overlay");
        setVisible(false);
        setManaged(false);
        setPickOnBounds(false);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(this, Pos.BOTTOM_LEFT);
    }

    public void show(DimSumDish dish) {
        Image image;
        try (InputStream input = getClass().getResourceAsStream(dish.resourcePath())) {
            if (input == null) return;
            image = new Image(input, 176, 132, true, true);
        } catch (Exception missing) {
            return;
        }
        ImageView picture = new ImageView(image);
        picture.setPreserveRatio(true);
        picture.setFitWidth(176);
        picture.setFitHeight(132);
        picture.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        picture.setAccessibleText(dish.bilingualName());

        Label title = Mat.label(i18n.text("dimsum.title"), "subtitle");
        Label name = Mat.label(dish.name(i18n.modeProperty().get()), "row-title");
        Label body = Mat.label(i18n.text("dimsum.body"), "row-desc");
        body.setWrapText(true);
        VBox copy = new VBox(4, title, name, body);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Button close = new Button("×");
        close.getStyleClass().add("notification-dismiss");
        close.setAccessibleText(i18n.text("dimsum.dismiss"));
        close.setOnAction(event -> hide());
        HBox card = new HBox(12, picture, copy, close);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12));
        card.getStyleClass().add("dim-sum-card");
        card.setAccessibleRole(AccessibleRole.PARENT);
        card.setAccessibleText(dish.bilingualName() + ". " + i18n.text("dimsum.body"));
        getChildren().setAll(card);
        setOpacity(1);
        setVisible(true);
        setManaged(true);
        if (timeout != null) timeout.stop();
        timeout = new PauseTransition(Duration.seconds(8));
        timeout.setOnFinished(event -> hide());
        timeout.play();
    }

    public void hide() {
        if (!isVisible()) return;
        if (timeout != null) timeout.stop();
        if (settings.reducedMotionProperty().get()) {
            finishHide();
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(180), this);
        fade.setToValue(0);
        fade.setOnFinished(event -> finishHide());
        fade.play();
    }

    /** Removes the transient card synchronously for deterministic capture state. */
    public void hideImmediately() {
        if (timeout != null) timeout.stop();
        finishHide();
    }

    private void finishHide() {
        setVisible(false);
        setManaged(false);
        setOpacity(1);
        getChildren().clear();
    }
}
