package org.jdownloader.material.ui.component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;
import org.jdownloader.material.ui.appearance.AppearanceRegistry;

/** Material 3 blocking decisions with localized actions and appearance-editor participation. */
public final class M3Dialogs {

    private M3Dialogs() { }

    public static boolean confirm(Node owner, String title, String header, String body,
                                  String cancelLabel, String confirmLabel) {
        Objects.requireNonNull(owner, "owner");
        ButtonType cancel = new ButtonType(Objects.requireNonNullElse(cancelLabel, "Cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType(Objects.requireNonNullElse(confirmLabel, "Confirm"),
                ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        dialog.setTitle(Objects.requireNonNullElse(title, ""));
        dialog.setHeaderText(Objects.requireNonNullElse(header, ""));
        dialog.setContentText(Objects.requireNonNullElse(body, ""));
        dialog.setGraphic(null);
        dialog.getButtonTypes().setAll(cancel, confirm);
        dialog.getDialogPane().getStyleClass().add("m3-dialog");
        dialog.getDialogPane().setAccessibleText(String.join(". ",
                Objects.requireNonNullElse(title, ""), Objects.requireNonNullElse(header, ""),
                Objects.requireNonNullElse(body, "")));

        AtomicReference<Scene> attached = new AtomicReference<>();
        dialog.setOnShown(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null && AppearanceRegistry.attachSceneFor(owner, scene)) attached.set(scene);
        });
        try {
            return dialog.showAndWait().filter(confirm::equals).isPresent();
        } finally {
            Scene scene = attached.get();
            if (scene != null) AppearanceRegistry.detachSceneFor(owner, scene);
        }
    }

    public static Optional<String> prompt(Node owner, String title, String header, String initialValue,
                                          String cancelLabel, String applyLabel) {
        Objects.requireNonNull(owner, "owner");
        ButtonType cancel = new ButtonType(Objects.requireNonNullElse(cancelLabel, "Cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType apply = new ButtonType(Objects.requireNonNullElse(applyLabel, "Apply"),
                ButtonBar.ButtonData.OK_DONE);
        TextInputDialog dialog = new TextInputDialog(Objects.requireNonNullElse(initialValue, ""));
        dialog.initModality(Modality.WINDOW_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
            dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        dialog.setTitle(Objects.requireNonNullElse(title, ""));
        dialog.setHeaderText(Objects.requireNonNullElse(header, ""));
        dialog.setGraphic(null);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, apply);
        dialog.getDialogPane().getStyleClass().add("m3-dialog");
        dialog.getDialogPane().setAccessibleText(String.join(". ",
                Objects.requireNonNullElse(title, ""), Objects.requireNonNullElse(header, "")));
        dialog.getEditor().setAccessibleText(Objects.requireNonNullElse(header, title));

        AtomicReference<Scene> attached = new AtomicReference<>();
        dialog.setOnShown(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null && AppearanceRegistry.attachSceneFor(owner, scene)) attached.set(scene);
        });
        try {
            Optional<ButtonType> result = dialog.showAndWait().map(ignored -> apply);
            return result.filter(apply::equals).map(ignored -> dialog.getEditor().getText());
        } finally {
            Scene scene = attached.get();
            if (scene != null) AppearanceRegistry.detachSceneFor(owner, scene);
        }
    }
}
