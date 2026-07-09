package org.jdownloader.material.ui.dialog;

import io.github.palexdev.materialfx.controls.MFXCheckbox;
import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;

import java.io.File;

/**
 * The "Add Links" form, rendered as an in-app notification panel instead of a
 * modal dialog window. Submitting it closes the panel and reports the result
 * with a snackbar.
 */
public final class AddLinksPanel {
    private AddLinksPanel() {
    }

    public static void open(NotificationCenter notifier, DownloadEngine engine, Runnable onViewLinkGrabber) {
        notifier.panel("Add Links", "add", close -> build(notifier, engine, onViewLinkGrabber, close));
    }

    private static Node build(NotificationCenter notifier, DownloadEngine engine,
                              Runnable onViewLinkGrabber, Runnable close) {
        TextArea links = new TextArea();
        links.setPromptText("Paste one or more URLs — one per line");
        links.setPrefRowCount(5);
        links.setWrapText(true);
        links.getStyleClass().add("links-area");
        String clip = safeClipboard();
        if (clip != null && (clip.startsWith("http://") || clip.startsWith("https://"))) {
            links.setText(clip);
        }

        MFXTextField pkg = new MFXTextField();
        pkg.setFloatingText("Package name (optional)");
        pkg.setMaxWidth(Double.MAX_VALUE);

        TextField dest = new TextField(engine.settings().downloadFolderProperty().get());
        HBox.setHgrow(dest, Priority.ALWAYS);
        var browse = Mat.icon("folder", "Choose folder");
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            var window = notifier.getScene() == null ? null : notifier.getScene().getWindow();
            File dir = dc.showDialog(window);
            if (dir != null) dest.setText(dir.getAbsolutePath());
        });
        HBox destRow = new HBox(8, dest, browse);
        destRow.setAlignment(Pos.CENTER_LEFT);

        MFXCheckbox autoExtract = new MFXCheckbox("Auto-extract archives");

        var cancel = Mat.text("Cancel", null);
        cancel.setOnAction(e -> close.run());
        var add = Mat.outlined("Add", "add");
        add.setOnAction(e -> {
            int n = submit(engine, links.getText(), pkg.getText(), dest.getText(), false);
            close.run();
            if (n > 0) notifier.snack(n + (n == 1 ? " link" : " links") + " added to LinkGrabber",
                    "View", onViewLinkGrabber);
        });
        var addStart = Mat.filled("Add & Start", "play");
        addStart.setOnAction(e -> {
            int n = submit(engine, links.getText(), pkg.getText(), dest.getText(), true);
            close.run();
            if (n > 0) notifier.snack("Added " + n + (n == 1 ? " link" : " links") + " — downloading");
        });
        HBox actions = new HBox(8, Mat.hSpacer(), cancel, add, addStart);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox body = new VBox(12, links,
                Mat.label("Package", "label-md"), pkg,
                Mat.label("Destination", "label-md"), destRow,
                autoExtract, actions);
        body.setFillWidth(true);
        return body;
    }

    private static int submit(DownloadEngine engine, String text, String pkg, String dest, boolean start) {
        if (text == null || text.isBlank()) return 0;
        long count = text.lines().map(String::trim).filter(s -> !s.isEmpty()).count();
        if (dest != null && !dest.isBlank()) engine.settings().downloadFolderProperty().set(dest.trim());
        if (start) engine.settings().autoStartProperty().set(true);
        engine.addLinks(text, pkg, start);
        return (int) count;
    }

    private static String safeClipboard() {
        try {
            return javafx.scene.input.Clipboard.getSystemClipboard().getString();
        } catch (Exception e) {
            return null;
        }
    }
}
