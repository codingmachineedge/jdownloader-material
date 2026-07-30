package org.jdownloader.material.ui.component;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.util.Duration;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.util.Formats;

/** Compact aggregate-throughput trace used by the global app toolbar. */
public final class ThroughputMeter extends HBox {

    private final ReadOnlyLongProperty speed;
    private final I18n i18n;
    private final long[] samples = new long[20];
    private final Pane plot = new Pane();
    private final Polygon area = new Polygon();
    private final Polyline line = new Polyline();
    private final Label value = new Label();
    private final ChangeListener<Number> speedListener = (observable, previous, current) -> refresh();
    private final Timeline sampler = new Timeline(new KeyFrame(Duration.seconds(1), event -> sample()));

    public ThroughputMeter(ReadOnlyLongProperty speed, I18n i18n) {
        this.speed = speed;
        this.i18n = i18n;
        getStyleClass().add("throughput-meter");
        setAlignment(Pos.CENTER_LEFT);
        setAccessibleRole(AccessibleRole.TEXT);

        plot.getStyleClass().add("throughput-plot");
        plot.setMinSize(76, 28);
        plot.setPrefSize(76, 28);
        plot.setMaxSize(76, 28);
        area.getStyleClass().add("throughput-area");
        line.getStyleClass().add("throughput-line");
        plot.getChildren().addAll(area, line);

        value.getStyleClass().add("throughput-value");
        value.setMaxWidth(120);
        getChildren().addAll(plot, value);
        speed.addListener(speedListener);
        sampler.setCycleCount(Animation.INDEFINITE);
        sampler.play();
        refresh();
    }

    private void sample() {
        System.arraycopy(samples, 1, samples, 0, samples.length - 1);
        samples[samples.length - 1] = Math.max(0, speed.get());
        refresh();
    }

    private void refresh() {
        long bytesPerSecond = Math.max(0, speed.get());
        value.setText(bytesPerSecond == 0 ? "—" : Formats.speed(bytesPerSecond));
        setAccessibleText(bytesPerSecond == 0
                ? i18n.text("throughput.none")
                : i18n.text("throughput.current", Formats.speed(bytesPerSecond)));

        line.getPoints().clear();
        area.getPoints().clear();
        area.getPoints().addAll(0.0, 28.0);
        long peak = 1;
        for (long sample : samples) peak = Math.max(peak, sample);
        for (int index = 0; index < samples.length; index++) {
            double x = index * (76.0 / (samples.length - 1));
            double normalized = samples[index] / (double) peak;
            double y = 26 - normalized * 23;
            line.getPoints().addAll(x, y);
            area.getPoints().addAll(x, y);
        }
        area.getPoints().addAll(76.0, 28.0);
    }

    public void dispose() {
        sampler.stop();
        speed.removeListener(speedListener);
    }
}
