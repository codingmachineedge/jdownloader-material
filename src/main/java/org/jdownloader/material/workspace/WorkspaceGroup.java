package org.jdownloader.material.workspace;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A persisted, ordered group in the browser-style workspace tab strip. */
public record WorkspaceGroup(UUID id, String name, String color, String icon, String badge,
                             boolean collapsed, boolean pinned) {

    public static final String DEFAULT_COLOR = "#5C6F69FF";

    public WorkspaceGroup {
        id = Objects.requireNonNull(id, "id");
        name = normalize(name, "Group", 64);
        color = normalizeColor(color);
        icon = normalize(icon, "folder", 48);
        badge = normalize(badge, "", 24);
    }

    public static WorkspaceGroup create(String name) {
        return new WorkspaceGroup(UUID.randomUUID(), name, DEFAULT_COLOR, "folder", "", false, false);
    }

    public WorkspaceGroup withName(String value) {
        return new WorkspaceGroup(id, value, color, icon, badge, collapsed, pinned);
    }

    public WorkspaceGroup withColor(String value) {
        return new WorkspaceGroup(id, name, value, icon, badge, collapsed, pinned);
    }

    public WorkspaceGroup withDecoration(String nextIcon, String nextBadge) {
        return new WorkspaceGroup(id, name, color, nextIcon, nextBadge, collapsed, pinned);
    }

    public WorkspaceGroup withCollapsed(boolean value) {
        return new WorkspaceGroup(id, name, color, icon, badge, value, pinned);
    }

    public WorkspaceGroup withPinned(boolean value) {
        return new WorkspaceGroup(id, name, color, icon, badge, collapsed, value);
    }

    private static String normalize(String value, String fallback, int max) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private static String normalizeColor(String value) {
        if (value == null || !value.trim().matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            return DEFAULT_COLOR;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 7 ? normalized + "FF" : normalized;
    }
}
