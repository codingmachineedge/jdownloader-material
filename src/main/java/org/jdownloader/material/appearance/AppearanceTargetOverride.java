package org.jdownloader.material.appearance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Per-element appearance state, separated by hover/focus/pressed/etc. */
public final class AppearanceTargetOverride {

    private final AppearanceTargetId targetId;
    private final EnumMap<AppearanceState, AppearanceStyle> states = new EnumMap<>(AppearanceState.class);

    public AppearanceTargetOverride(AppearanceTargetId targetId) {
        this.targetId = Objects.requireNonNull(targetId, "targetId");
    }

    private AppearanceTargetOverride(AppearanceTargetOverride source) {
        this.targetId = source.targetId;
        source.states.forEach((state, style) -> states.put(state, style.copy()));
    }

    public AppearanceTargetId targetId() { return targetId; }

    public AppearanceStyle style(AppearanceState state) {
        return states.computeIfAbsent(Objects.requireNonNull(state, "state"), ignored -> new AppearanceStyle());
    }

    public AppearanceStyle styleOrEmpty(AppearanceState state) {
        AppearanceStyle style = states.get(state);
        return style == null ? new AppearanceStyle() : style.copy();
    }

    public void resetProperty(AppearanceState state, AppearanceProperty property) {
        AppearanceStyle style = states.get(state);
        if (style == null) return;
        style.reset(property);
        if (style.isEmpty()) states.remove(state);
    }

    public void resetState(AppearanceState state) {
        states.remove(state);
    }

    public void clear() {
        states.clear();
    }

    public boolean isEmpty() {
        return states.values().stream().allMatch(AppearanceStyle::isEmpty);
    }

    public Map<AppearanceState, AppearanceStyle> states() {
        EnumMap<AppearanceState, AppearanceStyle> copy = new EnumMap<>(AppearanceState.class);
        states.forEach((state, style) -> copy.put(state, style.copy()));
        return Collections.unmodifiableMap(copy);
    }

    public AppearanceTargetOverride copy() {
        return new AppearanceTargetOverride(this);
    }
}
