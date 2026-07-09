package org.jdownloader.material.model;

import javafx.beans.value.ObservableValue;

/**
 * Common row abstraction for the Downloads tree-table. Both a
 * {@link DownloadPackage} (parent row) and a {@link DownloadLink} (child row)
 * present the same observable columns, so a single set of cell factories
 * renders either. Package rows aggregate their children.
 */
public abstract class DownloadItem {

    public abstract ObservableValue<String> nameProperty();

    public abstract ObservableValue<String> hostProperty();

    public abstract ObservableValue<Number> bytesTotalProperty();

    public abstract ObservableValue<Number> bytesLoadedProperty();

    public abstract ObservableValue<Number> speedProperty();

    public abstract ObservableValue<DownloadState> stateProperty();

    /** Fraction in {@code [0,1]}, or a negative value when the total is unknown. */
    public abstract ObservableValue<Number> progressProperty();

    public abstract boolean isPackage();

    public double progress() {
        return progressProperty().getValue().doubleValue();
    }

    public long bytesTotal() {
        return bytesTotalProperty().getValue().longValue();
    }

    public long bytesLoaded() {
        return bytesLoadedProperty().getValue().longValue();
    }

    public long speed() {
        return speedProperty().getValue().longValue();
    }

    public DownloadState state() {
        return stateProperty().getValue();
    }

    /** Estimated seconds to completion, or {@code -1} when not computable. */
    public long etaSeconds() {
        long spd = speed();
        if (spd <= 0 || state() != DownloadState.RUNNING) return -1;
        long remaining = bytesTotal() - bytesLoaded();
        if (remaining <= 0) return 0;
        return remaining / spd;
    }
}
