package org.jdownloader.material.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, persistable notification facts. Runtime actions are registered separately. */
public record AppNotification(UUID id, Instant timestamp, NotificationSeverity severity,
                              String title, String body, String actionLabel, boolean read) {
    public AppNotification {
        id = Objects.requireNonNull(id, "id");
        timestamp = timestamp == null ? Instant.now() : timestamp;
        severity = severity == null ? NotificationSeverity.INFO : severity;
        title = bounded(title, 160);
        body = bounded(body, 2_000);
        actionLabel = bounded(actionLabel, 80);
    }

    public AppNotification withRead(boolean value) {
        return new AppNotification(id, timestamp, severity, title, body, actionLabel, value);
    }

    private static String bounded(String value, int max) {
        String safe = value == null ? "" : value.strip();
        return safe.length() > max ? safe.substring(0, max) : safe;
    }
}
