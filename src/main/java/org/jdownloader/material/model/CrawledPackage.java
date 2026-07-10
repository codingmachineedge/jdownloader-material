package org.jdownloader.material.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** A package of crawled links in the LinkGrabber staging area. */
public final class CrawledPackage {

    private final StringProperty name = new SimpleStringProperty(this, "name", "");
    private final StringProperty destination = new SimpleStringProperty(this, "destination", "");
    private final ObservableList<CrawledLink> links = FXCollections.observableArrayList();
    private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", true);

    public CrawledPackage(String name) {
        this(name, "");
    }

    public CrawledPackage(String name, String destination) {
        this.name.set(name);
        this.destination.set(destination == null ? "" : destination);
    }

    public StringProperty nameProperty() { return name; }
    /** Destination captured when this package was submitted. */
    public StringProperty destinationProperty() { return destination; }
    public ObservableList<CrawledLink> links() { return links; }
    public BooleanProperty expandedProperty() { return expanded; }

    public String name() { return name.get(); }

    public long totalSize() {
        return links.stream().mapToLong(CrawledLink::size).sum();
    }

    public int onlineCount() {
        return (int) links.stream().filter(l -> l.availability() == LinkAvailability.ONLINE).count();
    }
}
