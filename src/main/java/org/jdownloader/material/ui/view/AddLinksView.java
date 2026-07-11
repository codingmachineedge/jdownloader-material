package org.jdownloader.material.ui.view;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.Mat;

/**
 * Non-blocking Add Links drawer. The shell supplies the scrim and slide
 * transition; this view owns validation and submission state.
 */
public final class AddLinksView extends VBox {

    /** User-entered composer values retained when the language shell is rebuilt. */
    public record Draft(String urls, String packageName, String destination) {
    }

    private final DownloadEngine engine;
    private final I18n i18n;
    private final TextArea links = new TextArea();
    private final TextField packageName = new TextField();
    private final TextField destination;
    private final Label status = Mat.label("", "row-desc");
    private final ChangeListener<String> downloadFolderListener;
    private final Runnable closeDrawer;
    private final Runnable showLinkGrabber;
    private final ButtonBase addButton;
    private final ButtonBase addStartButton;
    private long submissionGeneration;
    private boolean submissionInFlight;
    private volatile boolean disposed;

    public AddLinksView(DownloadEngine engine, Runnable closeDrawer, Runnable showLinkGrabber, I18n i18n) {
        this.engine = engine;
        this.i18n = i18n;
        this.closeDrawer = closeDrawer;
        this.showLinkGrabber = showLinkGrabber;
        this.destination = new TextField(engine.settings().downloadFolderProperty().get());
        this.downloadFolderListener = (o, previous, current) -> {
            if (!disposed && samePath(destination.getText(), previous)) destination.setText(current);
        };
        setStatus("status.addlinks.initial");
        // A submission-specific destination must not silently overwrite the global default.
        // Keep an untouched composer aligned with Settings while preserving a
        // path the user deliberately entered for this submission.
        engine.settings().downloadFolderProperty().addListener(downloadFolderListener);
        getStyleClass().add("drawer-body");
        setFillWidth(true);

        var title = Mat.label(i18n.text("addlinks.title"), "drawer-title");
        var close = Mat.icon("close", i18n.text("window.close"));
        close.setOnAction(e -> closeDrawer.run());
        HBox header = new HBox(12, title, Mat.hSpacer(), close);
        header.getStyleClass().add("drawer-header");
        header.setAlignment(Pos.CENTER_LEFT);

        links.setPromptText(i18n.text("addlinks.urls_prompt"));
        links.setPrefRowCount(7);
        links.setWrapText(true);
        links.getStyleClass().add("links-area");

        packageName.setPromptText(i18n.text("addlinks.package_prompt"));
        packageName.setMaxWidth(Double.MAX_VALUE);

        HBox.setHgrow(destination, Priority.ALWAYS);
        HBox destinationRow = new HBox(destination);
        destinationRow.setAlignment(Pos.CENTER_LEFT);
        destinationRow.setMaxWidth(Double.MAX_VALUE);

        addButton = Mat.outlined(i18n.text("addlinks.queue"), null);
        addButton.setOnAction(e -> submit(false));
        addStartButton = Mat.filled(i18n.text("addlinks.queue_start"), null);
        addStartButton.setOnAction(e -> submit(true));
        var cancel = Mat.text(i18n.text("addlinks.cancel"), null);
        cancel.setOnAction(e -> closeDrawer.run());
        HBox actions = new HBox(8, Mat.hSpacer(), cancel, addButton, addStartButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("drawer-actions");

        VBox composer = new VBox(12,
                Mat.label(i18n.text("addlinks.urls"), "label-md"), links,
                Mat.label(i18n.text("addlinks.package"), "label-md"), packageName,
                Mat.label(i18n.text("addlinks.destination"), "label-md"), destinationRow,
                status);
        composer.getStyleClass().add("drawer-form");
        VBox.setVgrow(composer, Priority.ALWAYS);

        getChildren().setAll(header, composer, actions);
        setPadding(new Insets(20));
    }

    private void submit(boolean start) {
        if (disposed || submissionInFlight) return;
        String text = links.getText();
        if (text == null || text.isBlank()) {
            setStatus("status.addlinks.empty");
            return;
        }
        String submittedPackage = packageName.getText();
        String submittedDestination = destination.getText();
        long generation = ++submissionGeneration;
        setSubmissionInFlight(true);
        setStatus("status.addlinks.validating");
        engine.addLinks(text, submittedPackage, submittedDestination, start, start)
                .whenComplete((summary, error) -> {
                    if (disposed) return;
                    Platform.runLater(() -> {
                        if (!disposed && generation == submissionGeneration) {
                            setSubmissionInFlight(false);
                            showSubmissionResult(text, submittedPackage, submittedDestination, start, summary, error);
                        }
                    });
                });
    }

    private void showSubmissionResult(String submittedText, String submittedPackage, String submittedDestination,
                                      boolean start, org.jdownloader.material.engine.DownloadEngine.AddLinksResult summary,
                                      Throwable error) {
        if (disposed) return;
        if (error != null) {
            setStatus("status.addlinks.failed");
            return;
        }
        if (summary == null || summary.acceptedLinks() == 0) {
            setStatus("status.addlinks.none");
            return;
        }
        int accepted = summary.acceptedLinks();
        String message = i18n.text(start ? "status.addlinks.accepted.start" : "status.addlinks.accepted.queue", accepted);
        if (summary.ignoredLines() > 0) {
            message += " " + i18n.text(summary.ignoredLines() == 1
                    ? "status.addlinks.ignored.one" : "status.addlinks.ignored.many", summary.ignoredLines());
        }
        status.setText(message);

        // Never erase a newer edit while an earlier submission was validating.
        if (java.util.Objects.equals(links.getText(), submittedText)) links.clear();
        if (java.util.Objects.equals(packageName.getText(), submittedPackage)) packageName.clear();
        if (samePath(destination.getText(), submittedDestination)) {
            destination.setText(engine.settings().downloadFolderProperty().get());
        }
        if (start) closeDrawer.run();
        else showLinkGrabber.run();
    }

    private static boolean samePath(String value, String other) {
        return java.util.Objects.equals(value == null ? "" : value.trim(), other == null ? "" : other.trim());
    }

    private void setStatus(String key, Object... arguments) {
        status.setText(i18n.text(key, arguments));
    }

    public Draft draft() {
        return new Draft(links.getText(), packageName.getText(), destination.getText());
    }

    public void restoreDraft(Draft draft) {
        if (draft == null) return;
        links.setText(draft.urls());
        packageName.setText(draft.packageName());
        destination.setText(draft.destination());
    }

    /** Moves keyboard focus into the first field after the drawer transition. */
    public void requestInitialFocus() {
        Platform.runLater(links::requestFocus);
    }

    /** Relinquishes completion callbacks when the current drawer session is dismissed. */
    public void releaseSubmissionOwnership() {
        if (disposed) return;
        submissionGeneration++;
        setSubmissionInFlight(false);
        setStatus("status.addlinks.initial");
    }

    private void setSubmissionInFlight(boolean inFlight) {
        submissionInFlight = inFlight;
        addButton.setDisable(inFlight);
        addStartButton.setDisable(inFlight);
    }

    /**
     * Detaches the Settings listener registered for the composer. In-flight
     * submissions still finish in the engine, but their UI callback becomes a
     * no-op once this view has been replaced.
     */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        submissionGeneration++;
        engine.settings().downloadFolderProperty().removeListener(downloadFolderListener);
    }
}
