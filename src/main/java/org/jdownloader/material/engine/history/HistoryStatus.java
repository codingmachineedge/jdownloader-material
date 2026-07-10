package org.jdownloader.material.engine.history;

/**
 * Lifecycle status shared by the service and its immutable timeline entries.
 * Service values are normally {@link #IDLE}, {@link #RECORDING},
 * {@link #RESTORING}, {@link #FAILED}, or {@link #CLOSED}; successful entries
 * use {@link #COMMITTED} or {@link #RESTORED}.
 */
public enum HistoryStatus {
    IDLE,
    PENDING,
    RECORDING,
    COMMITTED,
    RESTORING,
    RESTORED,
    FAILED,
    CLOSED
}
