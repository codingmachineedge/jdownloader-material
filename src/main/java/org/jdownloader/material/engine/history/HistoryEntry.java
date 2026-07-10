package org.jdownloader.material.engine.history;

import java.time.Instant;
import java.util.Objects;

/** A durable, append-only item in the user-visible history timeline. */
public record HistoryEntry(
        String id,
        Instant timestamp,
        HistoryScope scope,
        HistoryOperation operation,
        String summary,
        HistoryStatus status,
        String targetId,
        String error) {

    public HistoryEntry {
        id = requireText(id, "id");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        scope = Objects.requireNonNull(scope, "scope");
        operation = Objects.requireNonNull(operation, "operation");
        summary = requireText(summary, "summary");
        status = Objects.requireNonNull(status, "status");
        targetId = blankToNull(targetId);
        error = blankToNull(error);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Returns the same immutable event with a new lifecycle status. */
    public HistoryEntry withStatus(HistoryStatus nextStatus) {
        return new HistoryEntry(id, timestamp, scope, operation, summary, nextStatus, targetId, error);
    }
}
