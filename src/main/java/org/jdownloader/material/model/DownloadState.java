package org.jdownloader.material.model;

/**
 * Lifecycle state of a download link or aggregated package, mirroring the
 * states JDownloader exposes in its Downloads list.
 */
public enum DownloadState {
    QUEUED("Queued", "state-queued"),
    RUNNING("Downloading", "state-running"),
    PAUSED("Paused", "state-paused"),
    FINISHED("Finished", "state-finished"),
    ERROR("Error", "state-error"),
    DISABLED("Disabled", "state-disabled");

    private final String label;
    private final String styleClass;

    DownloadState(String label, String styleClass) {
        this.label = label;
        this.styleClass = styleClass;
    }

    public String label() {
        return label;
    }

    /** CSS style class used to color chips and progress bars for this state. */
    public String styleClass() {
        return styleClass;
    }

    public boolean isActive() {
        return this == RUNNING;
    }

    public boolean isTerminal() {
        return this == FINISHED;
    }
}
