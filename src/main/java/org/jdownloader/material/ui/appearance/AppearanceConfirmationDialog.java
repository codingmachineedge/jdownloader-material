package org.jdownloader.material.ui.appearance;

import java.util.Objects;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Appearance-attached M3 decision dialog reserved for destructive profile operations. */
final class AppearanceConfirmationDialog {

    private AppearanceConfirmationDialog() {
    }

    static void show(Node owner, Function<String, String> text, String titleKey, String titleFallback,
                     String bodyKey, String bodyFallback, String factualDetail, Runnable confirmed) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(confirmed, "confirmed");
        Function<String, String> copy = text == null ? Function.identity() : text;

        Stage stage = new Stage(StageStyle.UNDECORATED);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            stage.initOwner(owner.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
        } else {
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        Label title = new Label(label(copy, titleKey, titleFallback));
        title.getStyleClass().add("appearance-confirmation-title");
        Label body = new Label(label(copy, bodyKey, bodyFallback)
                + (factualDetail == null || factualDetail.isBlank() ? "" : System.lineSeparator() + factualDetail));
        body.getStyleClass().add("appearance-confirmation-body");
        body.setWrapText(true);

        Button cancel = new Button(label(copy, "appearance.confirm.cancel", "Cancel"));
        cancel.setCancelButton(true);
        Button confirm = new Button(label(copy, "appearance.confirm.proceed", "Continue"));
        confirm.setDefaultButton(true);
        confirm.getStyleClass().add("appearance-confirmation-proceed");
        HBox actions = new HBox(8, cancel, confirm);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(14, title, body, actions);
        root.getStyleClass().add("appearance-confirmation");
        root.setAccessibleRole(AccessibleRole.DIALOG);
        root.setAccessibleText(title.getText());
        root.setPadding(new Insets(22));
        Scene scene = new Scene(root, 460, 220);
        if (owner.getScene() != null) scene.getStylesheets().setAll(owner.getScene().getStylesheets());
        stage.setScene(scene);

        AppearanceRegistry.attachSceneFor(owner, scene);
        cancel.setOnAction(event -> stage.close());
        confirm.setOnAction(event -> {
            stage.close();
            confirmed.run();
        });
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setOnHidden(event -> {
            AppearanceRegistry.detachSceneFor(owner, scene);
            if (owner.getScene() != null) owner.requestFocus();
        });
        stage.show();
        cancel.requestFocus();
    }

    private static String label(Function<String, String> text, String key, String fallback) {
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }
}
