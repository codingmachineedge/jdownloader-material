package org.jdownloader.material.model;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
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
    private final LongProperty bytesTotal = new SimpleLongProperty(this, "bytesTotal", 0);
    private final LongProperty bytesLoaded = new SimpleLongProperty(this, "bytesLoaded", 0);
    private final LongProperty speed = new SimpleLongProperty(this, "speed", 0);
    private final ObjectProperty<DownloadState> state =
            new SimpleObjectProperty<>(this, "state", DownloadState.QUEUED);
    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", true);
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
    public LongProperty speedProp()    { return speed; }
    public ObjectProperty<DownloadState> stateProp() { return state; }
    public BooleanProperty enabled()   { return enabled; }
    public StringProperty url()        { return url; }
    public long total()                { return bytesTotal.get(); }

    public void setState(DownloadState s) { state.set(s); }
}
