package org.jdownloader.material.ui.appearance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.Scene;
import org.jdownloader.material.appearance.AppearancePreset;
import org.jdownloader.material.appearance.AppearancePresets;
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProfileStore;
import org.jdownloader.material.appearance.AppearanceProperty;
import org.jdownloader.material.appearance.AppearanceState;
import org.jdownloader.material.appearance.AppearanceStyle;
import org.jdownloader.material.appearance.AppearanceTargetId;
import org.jdownloader.material.appearance.GlobalAppearanceProperty;

/** Clean load/install/apply API for wiring the appearance subsystem into the desktop shell. */
public final class AppearanceService implements AutoCloseable {

    private final AppearanceProfileStore store;
    private AppearanceProfile profile;
    private AppearanceRegistry registry;
    private Consumer<Exception> persistenceFailureHandler = error -> { };
    private Consumer<AppearanceProfile> globalChangeHandler = ignored -> { };
    private Consumer<AppearanceProfile> profileChangeHandler = ignored -> { };

    public AppearanceService(AppearanceProfileStore store, AppearanceProfile profile) {
        this.store = Objects.requireNonNull(store, "store");
        this.profile = Objects.requireNonNull(profile, "profile").copy();
    }

    public static AppearanceService loadDefault() throws IOException {
        AppearanceProfileStore store = AppearanceProfileStore.defaultStore();
        return new AppearanceService(store, store.load());
    }

    public synchronized AppearanceProfile profile() { return profile.copy(); }

    public synchronized void setPersistenceFailureHandler(Consumer<Exception> handler) {
        persistenceFailureHandler = Objects.requireNonNull(handler, "handler");
    }

    /** Bridges profile-wide changes to host concerns such as the existing scene theme manager. */
    public synchronized void setGlobalChangeHandler(Consumer<AppearanceProfile> handler) {
        globalChangeHandler = Objects.requireNonNull(handler, "handler");
        notifyGlobalChange();
    }

    /** Publishes every semantic profile mutation for settings/history synchronization. */
    public synchronized void setProfileChangeHandler(Consumer<AppearanceProfile> handler) {
        profileChangeHandler = Objects.requireNonNull(handler, "handler");
        notifyProfileChange();
    }

    public synchronized AppearanceRegistry install(Scene scene) {
        return install(scene, Function.identity());
    }

    public synchronized AppearanceRegistry install(Scene scene, Function<String, String> text) {
        Objects.requireNonNull(scene, "scene");
        if (registry != null) registry.close();
        String css = Objects.requireNonNull(AppearanceService.class.getResource("/css/appearance.css"),
                "Missing appearance.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) scene.getStylesheets().add(css);
        AppearanceCss.applyGlobal(scene.getRoot(), profile);
        registry = new AppearanceRegistry(scene, this, text == null ? Function.identity() : text);
        return registry;
    }

    public synchronized AppearanceRegistry registry() { return registry; }

    public synchronized AppearanceStyle styleFor(AppearanceTargetId targetId, AppearanceState state) {
        return profile.targetIfPresent(targetId).map(target -> {
            AppearanceStyle normal = target.styleOrEmpty(AppearanceState.NORMAL);
            return state == AppearanceState.NORMAL ? normal : target.styleOrEmpty(state).over(normal);
        }).orElseGet(AppearanceStyle::new);
    }

    /** Returns only values explicitly stored for this state, without inherited NORMAL values. */
    public synchronized AppearanceStyle explicitStyleFor(AppearanceTargetId targetId, AppearanceState state) {
        return profile.targetIfPresent(targetId).map(target -> target.styleOrEmpty(state))
                .orElseGet(AppearanceStyle::new);
    }

    public synchronized void apply(Node node, AppearanceTargetId targetId, AppearanceState state) {
        AppearanceCss.apply(node, profile, styleFor(targetId, state));
    }

    public synchronized void applyGlobals(Scene scene) {
        AppearanceCss.applyGlobal(scene.getRoot(), profile);
        if (registry != null) registry.refreshAll();
    }

    public synchronized void update(AppearanceTargetId targetId, AppearanceState state,
                                    Consumer<AppearanceStyle> mutation) {
        AppearanceStyle style = profile.target(targetId).style(state);
        mutation.accept(style);
        persistAndRefresh(targetId);
    }

    public synchronized void updateGlobal(Consumer<AppearanceProfile> mutation) {
        mutation.accept(profile);
        persistAndRefresh(null);
    }

    public synchronized void resetProperty(AppearanceTargetId targetId, AppearanceState state,
                                           AppearanceProperty property) {
        profile.resetProperty(targetId, state, property);
        persistAndRefresh(targetId);
    }

    public synchronized void resetTarget(AppearanceTargetId targetId) {
        profile.resetTarget(targetId);
        persistAndRefresh(targetId);
    }

    public synchronized void resetGlobal(GlobalAppearanceProperty property) {
        profile.resetGlobal(property);
        persistAndRefresh(null);
    }

    public synchronized void resetGlobalAppearance() {
        profile.resetGlobalAppearance();
        persistAndRefresh(null);
    }

    public synchronized void applyPreset(AppearancePreset preset, AppearanceTargetId targetId) {
        profile.applyPreset(preset, targetId);
        // Presets always carry global fields, even when they also target one element.
        persistAndRefresh(null);
    }

    public synchronized void addUserPreset(AppearancePreset preset) {
        profile.addUserPreset(preset);
        persist();
        notifyProfileChange();
    }

    public synchronized void removeUserPreset(String id) {
        profile.removeUserPreset(id);
        persist();
        notifyProfileChange();
    }

    public synchronized List<AppearancePreset> presets() {
        List<AppearancePreset> presets = new ArrayList<>(AppearancePresets.builtIns());
        presets.addAll(profile.userPresets().values());
        return List.copyOf(presets);
    }

    public synchronized void exportTo(Path destination) throws IOException {
        store.exportTo(destination, profile);
    }

    public synchronized void importFrom(Path source) throws IOException {
        AppearanceProfile imported = store.importAndSave(source);
        profile = imported;
        notifyGlobalChange();
        notifyProfileChange();
        if (registry != null) registry.refreshAll();
    }

    /** Applies a Settings/history restore as a new live profile without rewriting prior history. */
    public synchronized void replaceProfile(AppearanceProfile replacement) {
        profile = Objects.requireNonNull(replacement, "replacement").copy();
        persist();
        notifyGlobalChange();
        notifyProfileChange();
        if (registry != null) registry.refreshAll();
    }

    public synchronized void saveNow() throws IOException { store.save(profile); }

    private void persistAndRefresh(AppearanceTargetId targetId) {
        persist();
        notifyProfileChange();
        if (targetId == null) notifyGlobalChange();
        if (registry != null) {
            if (targetId == null) registry.refreshAll();
            else registry.refresh(targetId);
        }
    }

    private void persist() {
        try { store.save(profile); }
        catch (Exception error) { persistenceFailureHandler.accept(error); }
    }

    private void notifyGlobalChange() {
        try { globalChangeHandler.accept(profile.copy()); }
        catch (RuntimeException error) { persistenceFailureHandler.accept(error); }
    }

    private void notifyProfileChange() {
        try { profileChangeHandler.accept(profile.copy()); }
        catch (RuntimeException error) { persistenceFailureHandler.accept(error); }
    }

    @Override
    public synchronized void close() {
        if (registry != null) registry.close();
        registry = null;
    }
}
