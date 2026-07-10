package org.jdownloader.material.model;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;

import java.util.UUID;

/** A single downloadable file — the leaf row of the Downloads tree. */
public final class DownloadLink extends DownloadItem {

    private final String id = UUID.randomUUID().toString();
    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final StringProperty host = new SimpleStringProperty(this, "host", "");
    private final StringProperty url = new SimpleStringProperty(this, "url", "");
    private final StringProperty destination = new SimpleStringProperty(this, "destination", "");
    private final StringProperty outputPath = new SimpleStringProperty(this, "outputPath", "");
    private final StringProperty detail = new SimpleStringProperty(this, "detail", "");
    private final StringProperty retryReason = new SimpleStringProperty(this, "retryReason", "");
    private final LongProperty bytesTotal = new SimpleLongProperty(this, "bytesTotal", 0);
    private final LongProperty bytesLoaded = new SimpleLongProperty(this, "bytesLoaded", 0);
    private final LongProperty speed = new SimpleLongProperty(this, "speed", 0);
    private final ObjectProperty<DownloadState> state =
            new SimpleObjectProperty<>(this, "state", DownloadState.QUEUED);
    private final ObjectProperty<DownloadPriority> priority =
            new SimpleObjectProperty<>(this, "priority", DownloadPriority.NORMAL);
    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", true);
    /** Number of automatic transient-failure retries already scheduled. */
    private final IntegerProperty retryAttempt = new SimpleIntegerProperty(this, "retryAttempt", 0);
    /** Epoch-millis deadline for the next automatic retry, or zero when none is pending. */
    private final LongProperty retryAtEpochMillis = new SimpleLongProperty(this, "retryAtEpochMillis", 0);
    private final ObservableValue<Number> progress;

    public DownloadLink(String name, String host, long bytesTotal) {
        this.name.set(name);
        this.host.set(host);
        this.bytesTotal.set(bytesTotal);
        this.progress = Bindings.createDoubleBinding(() -> {
            long tot = this.bytesTotal.get();
            if (tot <= 0) return -1.0;
            return Math.min(1.0, this.bytesLoaded.get() / (double) tot);
        }, this.bytesLoaded, this.bytesTotal);
    }

    public String id() {
        return id;
    }

    /** Writable name used for queued-item inline rename. */
    public StringProperty nameProp() { return name; }

    @Override public ObservableValue<String> nameProperty()   { return name; }
    @Override public ObservableValue<String> hostProperty()   { return host; }
    @Override public ObservableValue<Number> bytesTotalProperty()  { return bytesTotal; }
    @Override public ObservableValue<Number> bytesLoadedProperty() { return bytesLoaded; }
    @Override public ObservableValue<Number> speedProperty()  { return speed; }
    @Override public ObservableValue<DownloadState> stateProperty() { return state; }
    @Override public ObservableValue<Number> progressProperty() { return progress; }
    @Override public boolean isPackage() { return false; }

    // Writable accessors used by the engine --------------------------------
    public LongProperty loadedProp()   { return bytesLoaded; }
    public LongProperty totalProp()    { return bytesTotal; }
    public LongProperty speedProp()    { return speed; }
    public ObjectProperty<DownloadState> stateProp() { return state; }
    public ObjectProperty<DownloadPriority> priorityProperty() { return priority; }
    public BooleanProperty enabled()   { return enabled; }
    public StringProperty url()        { return url; }
    /** Per-link destination captured from its package at confirmation time. */
    public StringProperty destinationProperty() { return destination; }
    /** Resolved completed-file path, including any collision-safe rename. */
    public StringProperty outputPathProperty() { return outputPath; }
    /** Inline status detail such as an HTTP or filesystem failure. */
    public StringProperty detailProperty() { return detail; }
    /** Stable cause text used to render a changing automatic-retry countdown. */
    public StringProperty retryReasonProperty() { return retryReason; }
    public IntegerProperty retryAttemptProperty() { return retryAttempt; }
    public LongProperty retryAtEpochMillisProperty() { return retryAtEpochMillis; }
    public long total()                { return bytesTotal.get(); }

    public void setState(DownloadState s) { state.set(s); }
}
