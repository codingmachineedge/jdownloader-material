package org.jdownloader.material.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.UUID;

/** A link staged in the LinkGrabber, not yet confirmed into the Downloads list. */
public final class CrawledLink {

    private final String id = UUID.randomUUID().toString();
    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final StringProperty host = new SimpleStringProperty(this, "host", "");
    private final StringProperty url = new SimpleStringProperty(this, "url", "");
    private final LongProperty size = new SimpleLongProperty(this, "size", 0);
    private final ObjectProperty<LinkAvailability> availability =
            new SimpleObjectProperty<>(this, "availability", LinkAvailability.UNKNOWN);
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", true);

    public CrawledLink(String name, String host, String url, long size) {
        this.name.set(name);
        this.host.set(host);
        this.url.set(url);
        this.size.set(size);
    }

    public String id() { return id; }
    public StringProperty nameProperty() { return name; }
    public StringProperty hostProperty() { return host; }
    public StringProperty urlProperty() { return url; }
    public LongProperty sizeProperty() { return size; }
    public ObjectProperty<LinkAvailability> availabilityProperty() { return availability; }
    public BooleanProperty selectedProperty() { return selected; }

    public String name() { return name.get(); }
    public String host() { return host.get(); }
    public long size() { return size.get(); }
    public LinkAvailability availability() { return availability.get(); }
    public boolean isSelected() { return selected.get(); }

    /** Promotes this crawled link into a concrete download link. */
    public DownloadLink toDownloadLink() {
        return new DownloadLink(name.get(), host.get(), size.get());
    }
}
