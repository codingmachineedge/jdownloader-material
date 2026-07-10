package org.jdownloader.material.ui.component;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.util.Formats;

/** Bottom status bar mirroring JDownloader's global speed / activity indicators. */
public final class StatusBar extends HBox {

    public StatusBar(DownloadEngine engine) {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);

        Label speed = new Label();
        speed.getStyleClass().add("status-strong");
        speed.textProperty().bind(Bindings.createStringBinding(
                () -> "▼ " + Formats.speed(engine.globalSpeedProperty().get()),
                engine.globalSpeedProperty()));

        Label running = metric("Running",
                Bindings.createStringBinding(
                        () -> String.valueOf(engine.runningCountProperty().get()),
                        engine.runningCountProperty()));

        Label remaining = metric("Remaining",
                Bindings.createStringBinding(
                        () -> Formats.bytes(engine.totalRemainingProperty().get()),
                        engine.totalRemainingProperty()));

        // Direct HTTP mode uses this legacy-named property for a real retry
        // countdown, not a pretend router reconnect.
        HBox reconnect = new HBox(6, Icons.of("reconnect", 14, "icon-primary"), new Label("Retry scheduled"));
        reconnect.setAlignment(Pos.CENTER_LEFT);
        reconnect.visibleProperty().bind(engine.reconnectingProperty());
        reconnect.managedProperty().bind(engine.reconnectingProperty());

        getChildren().addAll(speed, sep(), running, sep(), remaining, Mat.hSpacer(), reconnect);
    }

    private Label metric(String name, javafx.beans.binding.StringBinding valueBinding) {
        Label l = new Label();
        l.getStyleClass().add("status-metric");
        l.textProperty().bind(Bindings.concat(name + ": "));
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
