package org.jdownloader.material.notification;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Corner-toast source plus bounded, reviewable local history.
 *
 * <p>Persistence is best effort: a history-write failure is reported to stderr
 * and never turns the user's original operation into a failure.</p>
 */
public final class NotificationService implements AutoCloseable {
    private static final String SCHEMA = "1";
    private static final int MAX_HISTORY = 500;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final ObservableList<AppNotification> history = FXCollections.observableArrayList();
    private final ObservableList<AppNotification> active = FXCollections.observableArrayList();
    private final Map<UUID, Runnable> actions = new HashMap<>();
    private final Path file;
    private final BooleanSupplier historyEnabled;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "notification-history-writer");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationService(Path directory) {
        this(directory, () -> true);
    }

    public NotificationService(Path directory, BooleanSupplier historyEnabled) {
        file = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize()
                .resolve("notifications.properties");
        this.historyEnabled = Objects.requireNonNull(historyEnabled, "historyEnabled");
        load();
    }

    public static NotificationService createDefault() {
        return new NotificationService(Path.of(System.getProperty("user.home", "."), ".jdownloader-material"));
    }

    public ObservableList<AppNotification> history() { return FXCollections.unmodifiableObservableList(history); }
    public ObservableList<AppNotification> active() { return FXCollections.unmodifiableObservableList(active); }

    public UUID info(String title, String body) { return show(NotificationSeverity.INFO, title, body, "", null); }
    public UUID success(String title, String body) { return show(NotificationSeverity.SUCCESS, title, body, "", null); }
    public UUID warning(String title, String body) { return show(NotificationSeverity.WARNING, title, body, "", null); }
    public UUID error(String title, String body) { return show(NotificationSeverity.ERROR, title, body, "", null); }

    public UUID show(NotificationSeverity severity, String title, String body,
                     String actionLabel, Runnable action) {
        AppNotification notification = new AppNotification(UUID.randomUUID(), Instant.now(), severity,
                title, body, actionLabel, false);
        runOnFx(() -> {
            active.add(notification);
            if (action != null && !notification.actionLabel().isBlank()) actions.put(notification.id(), action);
            if (historyEnabled.getAsBoolean()) {
                history.addFirst(notification);
                while (history.size() > MAX_HISTORY) history.removeLast();
                persistAsync();
            }
        });
        return notification.id();
    }

    public void invokeAction(UUID id) {
        Runnable action = actions.get(id);
        if (action != null) action.run();
    }

    public void dismiss(UUID id) {
        runOnFx(() -> {
            active.removeIf(item -> item.id().equals(id));
            actions.remove(id);
            markReadInternal(id);
        });
    }

    public void markRead(UUID id) { runOnFx(() -> markReadInternal(id)); }

    public void clearHistory() {
        runOnFx(() -> {
            history.clear();
            persistAsync();
        });
    }

    private void markReadInternal(UUID id) {
        for (int index = 0; index < history.size(); index++) {
            AppNotification item = history.get(index);
            if (item.id().equals(id) && !item.read()) {
                history.set(index, item.withRead(true));
                persistAsync();
                break;
            }
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Notification history is too large");
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
            if (!SCHEMA.equals(properties.getProperty("schema"))) return;
            int count = Math.min(MAX_HISTORY, integer(properties.getProperty("count"), 0));
            List<AppNotification> loaded = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String prefix = "item." + index + ".";
                try {
                    loaded.add(new AppNotification(
                            UUID.fromString(properties.getProperty(prefix + "id")),
                            Instant.parse(properties.getProperty(prefix + "timestamp")),
                            NotificationSeverity.valueOf(properties.getProperty(prefix + "severity")),
                            properties.getProperty(prefix + "title", ""),
                            properties.getProperty(prefix + "body", ""),
                            properties.getProperty(prefix + "actionLabel", ""),
                            Boolean.parseBoolean(properties.getProperty(prefix + "read", "false"))));
                } catch (RuntimeException ignored) {
                    // One damaged record never hides valid history around it.
                }
            }
            loaded.sort(Comparator.comparing(AppNotification::timestamp).reversed());
            history.setAll(loaded);
        } catch (IOException error) {
            System.err.println("Notification history could not be loaded: " + error.getMessage());
        }
    }

    private void persistAsync() {
        List<AppNotification> snapshot = List.copyOf(history);
        writer.execute(() -> {
            try { persist(snapshot); }
            catch (IOException error) {
                System.err.println("Notification history could not be saved: " + error.getMessage());
            }
        });
    }

    private void persist(List<AppNotification> snapshot) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("schema", SCHEMA);
        properties.setProperty("count", Integer.toString(snapshot.size()));
        for (int index = 0; index < snapshot.size(); index++) {
            AppNotification item = snapshot.get(index);
            String prefix = "item." + index + ".";
            properties.setProperty(prefix + "id", item.id().toString());
            properties.setProperty(prefix + "timestamp", item.timestamp().toString());
            properties.setProperty(prefix + "severity", item.severity().name());
            properties.setProperty(prefix + "title", item.title());
            properties.setProperty(prefix + "body", item.body());
            properties.setProperty(prefix + "actionLabel", item.actionLabel());
            properties.setProperty(prefix + "read", Boolean.toString(item.read()));
        }
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "notifications-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "JDownloader Material notification history");
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static int integer(String value, int fallback) {
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static void runOnFx(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) action.run();
            else Platform.runLater(action);
        } catch (IllegalStateException toolkitNotStarted) {
            action.run();
        }
    }

    @Override public void close() {
        writer.shutdown();
        Runnable flush = () -> {
            try {
                if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow();
            } catch (InterruptedException interrupted) {
                writer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        };
        if (Platform.isFxApplicationThread()) {
            Thread thread = new Thread(flush, "notification-history-close");
            thread.setDaemon(false);
            thread.start();
        } else {
            flush.run();
        }
    }
}
