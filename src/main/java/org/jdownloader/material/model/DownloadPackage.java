package org.jdownloader.material.model;

import javafx.beans.InvalidationListener;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A parent row grouping several {@link DownloadLink}s, mirroring JDownloader's
 * FilePackage. Size / loaded / speed / state / progress are aggregated from the
 * children and kept live: the package re-computes whenever a child property or
 * the child list changes.
 */
public final class DownloadPackage extends DownloadItem {

    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final StringProperty destination = new SimpleStringProperty(this, "destination", "");
    private final ObservableList<DownloadLink> links = FXCollections.observableArrayList();
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", true);

    private final LongProperty bytesTotal = new SimpleLongProperty(this, "bytesTotal", 0);
    private final LongProperty bytesLoaded = new SimpleLongProperty(this, "bytesLoaded", 0);
    private final LongProperty speed = new SimpleLongProperty(this, "speed", 0);
    private final ObjectProperty<DownloadState> state =
            new SimpleObjectProperty<>(this, "state", DownloadState.QUEUED);
    private final StringProperty host = new SimpleStringProperty(this, "host", "");
    private final ObservableValue<Number> progress;

    private final InvalidationListener childListener = o -> recompute();

    public DownloadPackage(String name) {
        this(name, "");
    }

    public DownloadPackage(String name, String destination) {
        this.name.set(name);
        this.destination.set(destination == null ? "" : destination);
        this.progress = Bindings.createDoubleBinding(() -> {
            long tot = bytesTotal.get();
            if (tot <= 0) return -1.0;
            return Math.min(1.0, bytesLoaded.get() / (double) tot);
        }, bytesLoaded, bytesTotal);

        links.addListener((javafx.collections.ListChangeListener<DownloadLink>) c -> {
            while (c.next()) {
                for (DownloadLink l : c.getAddedSubList()) attach(l);
                for (DownloadLink l : c.getRemoved()) detach(l);
            }
            recompute();
        });
    }

    private void attach(DownloadLink l) {
        l.bytesLoadedProperty().addListener(childListener);
        l.bytesTotalProperty().addListener(childListener);
        l.speedProperty().addListener(childListener);
        l.stateProperty().addListener(childListener);
    }

    private void detach(DownloadLink l) {
        l.bytesLoadedProperty().removeListener(childListener);
        l.bytesTotalProperty().removeListener(childListener);
        l.speedProperty().removeListener(childListener);
        l.stateProperty().removeListener(childListener);
    }

    /** Recomputes all aggregate columns from the current children. */
    public void recompute() {
        long total = 0, loaded = 0, spd = 0;
        int running = 0, finished = 0, error = 0, paused = 0, disabled = 0;
        String firstHost = "";
        for (DownloadLink l : links) {
            total += Math.max(0, l.bytesTotal());
            loaded += Math.max(0, l.bytesLoaded());
            if (l.state() == DownloadState.RUNNING) spd += l.speed();
            switch (l.state()) {
                case RUNNING -> running++;
                case FINISHED -> finished++;
                case ERROR -> error++;
                case PAUSED -> paused++;
                case DISABLED -> disabled++;
                default -> { }
            }
            if (firstHost.isEmpty()) firstHost = l.hostProperty().getValue();
        }
        bytesTotal.set(total);
        bytesLoaded.set(loaded);
        speed.set(spd);

        int n = links.size();
        DownloadState agg;
        if (n == 0) agg = DownloadState.QUEUED;
        else if (running > 0) agg = DownloadState.RUNNING;
        else if (error > 0) agg = DownloadState.ERROR;
        else if (finished == n) agg = DownloadState.FINISHED;
        else if (paused > 0) agg = DownloadState.PAUSED;
        else if (disabled == n) agg = DownloadState.DISABLED;
        else agg = DownloadState.QUEUED;
        state.set(agg);

        boolean sameHost = links.stream().map(l -> l.hostProperty().getValue()).distinct().count() == 1;
        host.set(links.isEmpty() ? "" : (sameHost ? firstHost : links.size() + " hosts"));
    }

    public ObservableList<DownloadLink> links() { return links; }
    public StringProperty nameProp() { return name; }
    /** Destination captured when this package was confirmed. */
    public StringProperty destinationProperty() { return destination; }
    public BooleanProperty expandedProperty() { return expanded; }
    public int childCount() { return links.size(); }

    @Override public ObservableValue<String> nameProperty() { return name; }
    @Override public ObservableValue<String> hostProperty() { return host; }
    @Override public ObservableValue<Number> bytesTotalProperty() { return bytesTotal; }
    @Override public ObservableValue<Number> bytesLoadedProperty() { return bytesLoaded; }
    @Override public ObservableValue<Number> speedProperty() { return speed; }
    @Override public ObservableValue<DownloadState> stateProperty() { return state; }
    @Override public ObservableValue<Number> progressProperty() { return progress; }
    @Override public boolean isPackage() { return true; }
}
