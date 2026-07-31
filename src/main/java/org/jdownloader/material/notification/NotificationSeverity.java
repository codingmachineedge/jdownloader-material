package org.jdownloader.material.notification;

/** Material notification priority and dismissal policy. */
public enum NotificationSeverity {
    INFO(false, 5_000),
    SUCCESS(false, 4_000),
    WARNING(true, 0),
    ERROR(true, 0);

    private final boolean persistent;
    private final long timeoutMillis;

    NotificationSeverity(boolean persistent, long timeoutMillis) {
        this.persistent = persistent;
        this.timeoutMillis = timeoutMillis;
    }

    public boolean persistent() { return persistent; }
    public long timeoutMillis() { return timeoutMillis; }
}
