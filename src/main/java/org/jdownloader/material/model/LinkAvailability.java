package org.jdownloader.material.model;

/** Online-check result for a crawled link in the LinkGrabber. */
public enum LinkAvailability {
    ONLINE("Online", "avail-online"),
    OFFLINE("Offline", "avail-offline"),
    UNKNOWN("Unchecked", "avail-unknown");

    private final String label;
    private final String styleClass;

    LinkAvailability(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    public String label() {
        return label;
    }

    public String styleClass() {
        return styleClass;
    }
}
