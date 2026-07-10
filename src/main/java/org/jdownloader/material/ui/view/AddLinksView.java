package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.component.Mat;

/**
 * Inline link composer. It replaces the former floating Add Links panel so
 * users can queue, validate, and start work without a blocking dialog.
 */
public final class AddLinksView extends BorderPane {

    private final DownloadEngine engine;
    private final TextArea links = new TextArea();
    private final MFXTextField packageName = new MFXTextField();
    private final TextField destination;
    private final Label status = Mat.label(
            "Links are checked in the background. You can keep navigating while they are queued.",
            "row-desc");

    public AddLinksView(DownloadEngine engine, Runnable showDownloads, Runnable showLinkGrabber) {
        this.engine = engine;
        this.destination = new TextField(engine.settings().downloadFolderProperty().get());
        this.destination.textProperty().bindBidirectional(engine.settings().downloadFolderProperty());
        getStyleClass().add("content-area");

        var title = Mat.label("Add Links", "headline");
        var downloads = Mat.text("Downloads", "download");
        downloads.setOnAction(e -> showDownloads.run());
        var linkGrabber = Mat.outlined("LinkGrabber", "link");
        linkGrabber.setOnAction(e -> showLinkGrabber.run());
        HBox header = new HBox(12, title, Mat.hSpacer(), downloads, linkGrabber);
        header.getStyleClass().add("view-header");
        header.setAlignment(Pos.CENTER_LEFT);
        setTop(header);

        links.setPromptText("Paste one or more URLs — one per line");
        links.setPrefRowCount(8);
        links.setWrapText(true);
        links.getStyleClass().add("links-area");

        packageName.setFloatingText("Package name (optional)");
        packageName.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(destination, Priority.ALWAYS);
        HBox destinationRow = new HBox(destination);
        destinationRow.setAlignment(Pos.CENTER_LEFT);
        destinationRow.setMaxWidth(620);

        var add = Mat.outlined("Queue in LinkGrabber", "add");
        add.setOnAction(e -> submit(false));
        var addStart = Mat.filled("Queue & Start", "play");
        addStart.setOnAction(e -> submit(true));
        HBox actions = new HBox(8, add, addStart);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox composer = new VBox(12,
                Mat.label("URLs", "label-md"), links,
                Mat.label("Package", "label-md"), packageName,
                Mat.label("Destination", "label-md"), destinationRow,
                actions, status);
        composer.setPadding(new Insets(20));
        composer.setMaxWidth(760);
        composer.getStyleClass().add("md-card-flat");

        VBox center = new VBox(20, composer);
        center.setPadding(new Insets(24, 28, 32, 28));
        setCenter(center);
    }

    private void submit(boolean start) {
        String text = links.getText();
        long count = text == null ? 0 : text.lines().map(String::trim).filter(line -> !line.isEmpty()).count();
        if (count == 0) {
            status.setText("Paste at least one URL to queue a download.");
            return;
        }
        engine.addLinks(text, packageName.getText(), start, start);
        status.setText(start
                ? count + (count == 1 ? " link is" : " links are")
                        + " being checked and will start automatically."
                : count + (count == 1 ? " link is" : " links are")
                        + " being checked in LinkGrabber.");
        links.clear();
        packageName.clear();
    }
}
