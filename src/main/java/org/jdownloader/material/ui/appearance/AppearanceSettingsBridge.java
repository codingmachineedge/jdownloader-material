package org.jdownloader.material.ui.appearance;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.value.ChangeListener;
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProfileStore;
import org.jdownloader.material.engine.Settings;

/**
 * Synchronizes the non-secret appearance profile with Settings so encrypted
 * backups and append-only local history capture and restore the same revision.
 */
public final class AppearanceSettingsBridge implements AutoCloseable {

    private final AppearanceService service;
    private final Settings settings;
    private final Consumer<Exception> failureHandler;
    private final ChangeListener<String> settingsListener = this::settingsChanged;
    private boolean synchronizing;
    private boolean closed;
    private String lastPayload = "";

    public AppearanceSettingsBridge(AppearanceService service, Settings settings,
                                    Consumer<Exception> failureHandler) {
        this.service = Objects.requireNonNull(service, "service");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.failureHandler = failureHandler == null ? ignored -> { } : failureHandler;

        String existing = Objects.requireNonNullElse(settings.appearanceProfilePayloadProperty().get(), "");
        if (!existing.isBlank()) applySettingsPayload(existing);
        settings.appearanceProfilePayloadProperty().addListener(settingsListener);
        service.setProfileChangeHandler(this::profileChanged);
    }

    private void profileChanged(AppearanceProfile profile) {
        if (closed || synchronizing) return;
        try {
            String payload = AppearanceProfileStore.serialize(profile);
            lastPayload = payload;
            if (payload.equals(settings.appearanceProfilePayloadProperty().get())) return;
            synchronizing = true;
            try { settings.setAppearanceProfilePayload(payload); }
            finally { synchronizing = false; }
        } catch (IOException | RuntimeException failure) {
            failureHandler.accept(asException(failure));
        }
    }

    private void settingsChanged(javafx.beans.value.ObservableValue<? extends String> observable,
                                 String previous, String current) {
        if (closed || synchronizing) return;
        applySettingsPayload(current);
    }

    private void applySettingsPayload(String payload) {
        try {
            AppearanceProfile replacement = AppearanceProfileStore.deserialize(payload);
            String normalized = AppearanceProfileStore.serialize(replacement);
            if (!normalized.equals(lastPayload)) {
                lastPayload = normalized;
                synchronizing = true;
                try { service.replaceProfile(replacement); }
                finally { synchronizing = false; }
            }
            if (!normalized.equals(settings.appearanceProfilePayloadProperty().get())) {
                synchronizing = true;
                try { settings.setAppearanceProfilePayload(normalized); }
                finally { synchronizing = false; }
            }
        } catch (IOException | RuntimeException failure) {
            failureHandler.accept(asException(failure));
            restoreLastValidPayload();
        }
    }

    private void restoreLastValidPayload() {
        if (lastPayload.isEmpty() || lastPayload.equals(settings.appearanceProfilePayloadProperty().get())) return;
        synchronizing = true;
        try { settings.setAppearanceProfilePayload(lastPayload); }
        finally { synchronizing = false; }
    }

    private static Exception asException(Throwable failure) {
        return failure instanceof Exception exception ? exception : new IllegalStateException(failure);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        settings.appearanceProfilePayloadProperty().removeListener(settingsListener);
        service.setProfileChangeHandler(ignored -> { });
    }
}
