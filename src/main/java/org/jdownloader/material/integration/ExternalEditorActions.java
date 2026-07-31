package org.jdownloader.material.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.notification.NotificationService;

/** Non-blocking, localized application workflow around direct external-editor launches. */
public final class ExternalEditorActions implements AutoCloseable {

    private final Settings settings;
    private final I18n i18n;
    private final NotificationService notifications;
    private final ExternalEditorService service;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private volatile boolean closed;

    public ExternalEditorActions(Settings settings, I18n i18n, NotificationService notifications) {
        this(settings, i18n, notifications, new ExternalEditorService(), createExecutor(), true);
    }

    /** Deterministic test boundary; the supplied executor and service remain caller-owned. */
    public ExternalEditorActions(Settings settings, I18n i18n, NotificationService notifications,
                                 ExternalEditorService service, Executor executor) {
        this(settings, i18n, notifications, service, executor, false);
    }

    private ExternalEditorActions(Settings settings, I18n i18n, NotificationService notifications,
                                  ExternalEditorService service, Executor executor, boolean ownsExecutor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.service = Objects.requireNonNull(service, "service");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownedExecutor = ownsExecutor ? (ExecutorService) executor : null;
    }

    public List<ExternalEditorService.Editor> detectedEditors() {
        return service.detectedEditors();
    }

    public CompletableFuture<List<ExternalEditorService.Editor>> refreshDetectedEditors() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("External editor actions are closed"));
        return CompletableFuture.supplyAsync(service::detect, executor);
    }

    public CompletableFuture<Boolean> openDownloadFolder() {
        return openPath(settings.downloadFolderProperty().get());
    }

    public CompletableFuture<Boolean> openPath(String value) {
        String path = Objects.requireNonNullElse(value, "").strip();
        if (path.isBlank()) {
            notifications.error(i18n.text("external_editor.error_title"),
                    i18n.text("external_editor.invalid_path", path));
            return CompletableFuture.completedFuture(false);
        }
        try {
            return openPath(Path.of(path));
        } catch (IllegalArgumentException invalid) {
            notifications.error(i18n.text("external_editor.error_title"),
                    i18n.text("external_editor.invalid_path", path));
            return CompletableFuture.completedFuture(false);
        }
    }

    public CompletableFuture<Boolean> openPath(Path target) {
        if (closed) return CompletableFuture.completedFuture(false);
        Path absolute = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        String selection = settings.externalEditorSelectionProperty().get();
        String customCommand = settings.externalEditorCommandProperty().get();
        return CompletableFuture.supplyAsync(() -> launch(absolute, selection, customCommand), executor)
                .handle((opened, failure) -> {
                    if (failure == null) {
                        notifications.success(i18n.text("external_editor.success_title"),
                                i18n.text("external_editor.success_body", opened));
                        return true;
                    }
                    Throwable cause = unwrap(failure);
                    if (cause instanceof NoEditorConfiguredException) {
                        notifications.error(i18n.text("external_editor.none_title"),
                                i18n.text("external_editor.none_body"));
                    } else {
                        notifications.error(i18n.text("external_editor.error_title"),
                                i18n.text("external_editor.error_body", message(cause)));
                    }
                    return false;
                });
    }

    private Path launch(Path target, String selection, String customCommand) {
        try {
            if (!Files.exists(target)) throw new IOException("Path does not exist: " + target);
            boolean custom = ExternalEditorService.CUSTOM_SELECTION.equalsIgnoreCase(selection);
            if (!custom && (service.detectedEditors().isEmpty()
                    || service.commandTemplate(selection, customCommand).isEmpty())) {
                service.detect();
            }
            String command = service.commandTemplate(selection, customCommand)
                    .orElseThrow(NoEditorConfiguredException::new);
            service.open(target, command);
            return target;
        } catch (IOException error) {
            throw new CompletionException(error);
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "external-editor-actions");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    @Override
    public void close() {
        closed = true;
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    private static final class NoEditorConfiguredException extends IOException {
        private NoEditorConfiguredException() {
            super("No external editor is configured");
        }
    }
}
