package org.jdownloader.material.ui.component;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.util.Formats;

/** Bottom status bar mirroring JDownloader's global speed / activity indicators. */
public final class StatusBar extends HBox {

    public StatusBar(DownloadEngine engine, I18n i18n, ActivityStatus activity) {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);

        Label speed = new Label();
        speed.getStyleClass().add("status-strong");
        speed.textProperty().bind(Bindings.createStringBinding(
                () -> "▼ " + Formats.speed(engine.globalSpeedProperty().get()),
                engine.globalSpeedProperty()));

        Label running = metric(i18n, "status.running",
                Bindings.createStringBinding(
                        () -> String.valueOf(engine.runningCountProperty().get()),
                        engine.runningCountProperty()));

        Label remaining = metric(i18n, "status.remaining",
                Bindings.createStringBinding(
                        () -> Formats.bytes(engine.totalRemainingProperty().get()),
                        engine.totalRemainingProperty()));

        HBox retry = new HBox(6, Icons.of("reconnect", 14, "icon-primary"),
                new Label(i18n.text("status.retry_scheduled")));
        retry.setAlignment(Pos.CENTER_LEFT);
        retry.visibleProperty().bind(engine.retryScheduledProperty());
        retry.managedProperty().bind(engine.retryScheduledProperty());

        Label activityMessage = new Label();
        activityMessage.getStyleClass().add("status-message");
        activityMessage.setTextOverrun(OverrunStyle.ELLIPSIS);
        activityMessage.setMaxWidth(420);
        activityMessage.textProperty().bind(activity.messageProperty());
        activityMessage.visibleProperty().bind(activity.messageProperty().isNotEmpty());
        activityMessage.managedProperty().bind(activity.messageProperty().isNotEmpty());
        activity.errorProperty().addListener((observable, wasError, isError) -> {
            activityMessage.getStyleClass().remove("error");
            if (isError) activityMessage.getStyleClass().add("error");
        });
        if (activity.errorProperty().get()) activityMessage.getStyleClass().add("error");
        HBox.setHgrow(activityMessage, Priority.NEVER);

        getChildren().addAll(speed, sep(), running, sep(), remaining, activityMessage, Mat.hSpacer(), retry);
    }

    private Label metric(I18n i18n, String nameKey, javafx.beans.binding.StringBinding valueBinding) {
        Label l = new Label();
        l.getStyleClass().add("status-metric");
        l.textProperty().bind(Bindings.createStringBinding(() -> i18n.text(nameKey) + ": ",
                i18n.modeProperty()));
        Label value = new Label();
        value.getStyleClass().addAll("status-metric", "value");
        value.textProperty().bind(valueBinding);
        // combine into one label-like node via a container is overkill; return a compound label
        HBox box = new HBox(4, l, value);
        box.setAlignment(Pos.CENTER_LEFT);
        Label wrapper = new Label();
        wrapper.setGraphic(box);
        return wrapper;
    }

    private Label sep() {
        Label l = new Label("•");
        l.getStyleClass().add("status-metric");
        return l;
    }
}
