package org.jdownloader.material.ui.component;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.util.Formats;

/** Stable 30px operational status line from the design handoff. */
public final class StatusBar extends HBox {

    private final ActivityStatus activity;
    private final ChangeListener<Boolean> errorListener;
    private final ChangeListener<Number> widthListener;
    private final Label clipboardCopy;
    private final Label retryCopy;

    public StatusBar(DownloadEngine engine, I18n i18n, ActivityStatus activity) {
        this.activity = activity;
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);

        clipboardCopy = new Label();
        clipboardCopy.getStyleClass().add("status-metric");
        clipboardCopy.textProperty().bind(Bindings.createStringBinding(
                () -> i18n.text(engine.settings().clipboardMonitoringProperty().get()
                        ? "status.clipboard_on" : "status.clipboard_off"),
                engine.settings().clipboardMonitoringProperty(), i18n.modeProperty()));
        Label clipboardIcon = compactMetric("▣", clipboardCopy);
        HBox clipboard = new HBox(5, clipboardIcon, clipboardCopy);
        clipboard.setAlignment(Pos.CENTER_LEFT);

        retryCopy = new Label();
        retryCopy.getStyleClass().add("status-metric");
        retryCopy.textProperty().bind(Bindings.createStringBinding(
                () -> i18n.text(engine.retryScheduledProperty().get()
                        ? "status.retry_scheduled" : "status.auto_retry_ready"),
                engine.retryScheduledProperty(), i18n.modeProperty()));
        Label retryIcon = compactMetric("↻", retryCopy);
        HBox retry = new HBox(5, retryIcon, retryCopy);
        retry.setAlignment(Pos.CENTER_LEFT);

        Label activityMessage = new Label();
        activityMessage.getStyleClass().add("status-message");
        activityMessage.setTextOverrun(OverrunStyle.ELLIPSIS);
        // maxWidth removed — text overrun ellipsis handles long strings
        activityMessage.textProperty().bind(activity.messageProperty());
        activityMessage.visibleProperty().bind(activity.messageProperty().isNotEmpty());
        activityMessage.managedProperty().bind(activity.messageProperty().isNotEmpty());
        errorListener = (observable, wasError, isError) -> {
            activityMessage.getStyleClass().remove("error");
            if (isError) activityMessage.getStyleClass().add("error");
        };
        activity.errorProperty().addListener(errorListener);
        if (activity.errorProperty().get()) activityMessage.getStyleClass().add("error");

        Label speed = new Label();
        speed.getStyleClass().add("status-strong");
        speed.setMaxWidth(180);
        speed.textProperty().bind(Bindings.createStringBinding(
                () -> "\u2193 " + Formats.speed(engine.globalSpeedProperty().get()),
                engine.globalSpeedProperty()));

        Label running = new Label();
        running.getStyleClass().add("status-metric");
        running.textProperty().bind(Bindings.createStringBinding(
                () -> i18n.text("status.active", engine.runningCountProperty().get()),
                engine.runningCountProperty(), i18n.modeProperty()));

        Label remaining = new Label();
        remaining.getStyleClass().add("status-metric");
        remaining.textProperty().bind(Bindings.createStringBinding(
                () -> i18n.text("status.remaining_value",
                        Formats.bytes(engine.totalRemainingProperty().get())),
                engine.totalRemainingProperty(), i18n.modeProperty()));

        // Each metric keeps its preferred size; none grows infinitely
        HBox.setHgrow(speed, Priority.NEVER);
        HBox.setHgrow(running, Priority.NEVER);
        HBox.setHgrow(remaining, Priority.NEVER);
        running.setMaxWidth(200);
        remaining.setMaxWidth(240);

        HBox right = new HBox(8, speed, sep(), running, sep(), remaining);
        right.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(activityMessage, Priority.ALWAYS);
        getChildren().addAll(clipboard, retry, activityMessage, Mat.hSpacer(), right);
        widthListener = (observable, previous, current) -> updateResponsiveState(current.doubleValue());
        widthProperty().addListener(widthListener);
        updateResponsiveState(getWidth());
    }

    private Label compactMetric(String glyph, Label fullCopy) {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("status-metric-icon");
        icon.accessibleTextProperty().bind(fullCopy.textProperty());
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(fullCopy.textProperty());
        Tooltip.install(icon, tooltip);
        return icon;
    }

    private void updateResponsiveState(double width) {
        boolean compact = width > 0 && width < 1000;
        clipboardCopy.setVisible(!compact);
        clipboardCopy.setManaged(!compact);
        retryCopy.setVisible(!compact);
        retryCopy.setManaged(!compact);
    }

    private Label sep() {
        Label label = new Label("\u00b7");
        label.getStyleClass().add("status-metric");
        return label;
    }

    public void dispose() {
        activity.errorProperty().removeListener(errorListener);
        widthProperty().removeListener(widthListener);
        clipboardCopy.textProperty().unbind();
        retryCopy.textProperty().unbind();
    }
}
