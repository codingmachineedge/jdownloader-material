package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXTextField;
import javafx.application.Platform;
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
        // A submission-specific destination must not silently overwrite the global default.
        engine.settings().downloadFolderProperty().addListener((o, previous, current) -> {
            // Keep an untouched composer aligned with Settings while preserving
            // a path the user deliberately entered for this submission.
            if (samePath(destination.getText(), previous)) destination.setText(current);
        });
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
        if (text == null || text.isBlank()) {
            status.setText("Paste at least one direct HTTP or HTTPS URL to queue a download.");
            return;
        }
        String submittedPackage = packageName.getText();
        String submittedDestination = destination.getText();
        status.setText("Validating links in the background…");
        engine.addLinks(text, submittedPackage, submittedDestination, start, start)
                .whenComplete((summary, error) -> Platform.runLater(() ->
                        showSubmissionResult(text, submittedPackage, submittedDestination, start, summary, error)));
    }

    private void showSubmissionResult(String submittedText, String submittedPackage, String submittedDestination,
                                      boolean start, org.jdownloader.material.engine.DownloadEngine.AddLinksResult summary,
                                      Throwable error) {
        if (error != null) {
            status.setText("Could not submit links; your input is still available to edit.");
            return;
        }
        if (summary == null || summary.acceptedLinks() == 0) {
            status.setText("No direct HTTP or HTTPS URLs were found; your input is still available to edit.");
            return;
        }
        int accepted = summary.acceptedLinks();
        String message = accepted + (accepted == 1 ? " link is" : " links are")
                + (start ? " being checked and will start automatically." : " being checked in LinkGrabber.");
        if (summary.ignoredLines() > 0) {
            message += " " + summary.ignoredLines()
                    + (summary.ignoredLines() == 1 ? " unsupported line was kept out of the queue."
                    : " unsupported lines were kept out of the queue.");
        }
        status.setText(message);

        // Never erase a newer edit while an earlier submission was validating.
        if (java.util.Objects.equals(links.getText(), submittedText)) links.clear();
        if (java.util.Objects.equals(packageName.getText(), submittedPackage)) packageName.clear();
        if (samePath(destination.getText(), submittedDestination)) {
            destination.setText(engine.settings().downloadFolderProperty().get());
        }
    }

    private static boolean samePath(String value, String other) {
        return java.util.Objects.equals(value == null ? "" : value.trim(), other == null ? "" : other.trim());
    }
}
