package org.jdownloader.material.engine.history;

/**
 * The part of the application that caused a history revision. Snapshots always
 * carry all three durable state files, while this value keeps the timeline
 * useful for filtering and human-readable audit messages.
 */
public enum HistoryScope {
    SETTINGS("settings"),
    DOWNLOADS("downloads"),
    LINKGRABBER("linkgrabber"),
    /** A combined queue/LinkGrabber operation, retained for import and bulk actions. */
    DOWNLOAD_LISTS("download-lists");

    private final String storageKey;

    HistoryScope(String storageKey) {
        this.storageKey = storageKey;
    }

    public String storageKey() {
        return storageKey;
    }
}
