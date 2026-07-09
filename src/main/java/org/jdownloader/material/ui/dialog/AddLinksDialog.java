package org.jdownloader.material.ui.dialog;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.component.Mat;

import java.io.File;

/** Material "Add Links" dialog — crawls pasted URLs into the LinkGrabber. */
public final class AddLinksDialog {

    private final DownloadEngine engine;
    private final Window owner;
    private double dragX, dragY;

    public AddLinksDialog(DownloadEngine engine, Window owner) {
        this.engine = engine;
        this.owner = owner;
    }

    public void show() {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        var titleRow = new HBox(8, Mat.label("Add Links", "dialog-title"), Mat.hSpacer());
        var close = Mat.icon("close", "Close");
        titleRow.getChildren().add(close);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        TextArea links = new TextArea();
        links.setPromptText("Paste one or more URLs — one per line");
        links.setPrefRowCount(7);
        links.setWrapText(true);
        links.getStyleClass().add("links-area");
        VBox.setVgrow(links, Priority.ALWAYS);
        // pre-fill from clipboard if it looks like a URL
        String clip = safeClipboard();
        if (clip != null && (clip.startsWith("http://") || clip.startsWith("https://"))) {
            links.setText(clip);
        }

        MFXTextField pkg = new MFXTextField();
        pkg.setFloatingText("Package name (optional)");
        pkg.setPrefWidth(Double.MAX_VALUE);

        MFXTextField dest = new MFXTextField();
        dest.setFloatingText("Save to");
        dest.setText(engine.settings().downloadFolderProperty().get());
        HBox.setHgrow(dest, Priority.ALWAYS);
        var browse = Mat.icon("folder", "Choose folder");
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(stage);
            if (dir != null) dest.setText(dir.getAbsolutePath());
        });
        HBox destRow = new HBox(8, dest, browse);
        destRow.setAlignment(Pos.CENTER_LEFT);

        MFXCheckbox autoExtract = new MFXCheckbox("Auto-extract archives");
        MFXCheckbox overwriteRules = new MFXCheckbox("Overwrite Packagizer rules");

        var cancel = Mat.text("Cancel", null);
        cancel.setOnAction(e -> stage.close());
        var add = Mat.outlined("Add", "add");
        add.setOnAction(e -> { submit(links.getText(), pkg.getText(), false); stage.close(); });
        var addStart = Mat.filled("Add & Start", "play");
        addStart.setOnAction(e -> { submit(links.getText(), pkg.getText(), true); stage.close(); });
        close.setOnAction(e -> stage.close());

        HBox actions = new HBox(8, Mat.hSpacer(), cancel, add, addStart);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(16, titleRow, links,
                Mat.label("Package", "label-md"), pkg,
                Mat.label("Destination", "label-md"), destRow,
                new HBox(24, autoExtract, overwriteRules),
                actions);
        card.getStyleClass().add("md-dialog");
        card.setPrefWidth(560);
        card.setMaxWidth(560);
        enableDrag(titleRow, stage);

        StackPane rootPane = new StackPane(card);
        rootPane.setPadding(new Insets(24)); // room for the drop shadow
        rootPane.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(rootPane);
        scene.setFill(Color.TRANSPARENT);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.setTitle("Add Links");
        stage.centerOnScreen();
        stage.show();
    }

    private void submit(String text, String pkgName, boolean start) {
        if (text == null || text.isBlank()) return;
        if (start) engine.settings().autoStartProperty().set(true);
        engine.addLinks(text, pkgName, start);
    }

    private void enableDrag(HBox handle, Stage stage) {
        handle.setOnMousePressed(e -> { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
        handle.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); });
    }

    private static String safeClipboard() {
        try {
            return javafx.scene.input.Clipboard.getSystemClipboard().getString();
        } catch (Exception e) {
            return null;
        }
    }
}
