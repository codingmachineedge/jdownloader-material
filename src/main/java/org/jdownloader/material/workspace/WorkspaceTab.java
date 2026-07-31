package org.jdownloader.material.workspace;

import java.util.Objects;
import java.util.UUID;

/** One named, styled page in the user's locally persisted workspace. */
public record WorkspaceTab(UUID id, WorkspacePage page, String title, WorkspaceStyle style,
                           boolean pinned, UUID groupId) {

    public WorkspaceTab {
        id = Objects.requireNonNull(id, "id");
        page = Objects.requireNonNull(page, "page");
        title = normalizedTitle(title);
        style = style == null ? WorkspaceStyle.DEFAULT : style;
    }

    /** Backward-compatible constructor for schema-v1 callers and imports. */
    public WorkspaceTab(UUID id, WorkspacePage page, String title, WorkspaceStyle style) {
        this(id, page, title, style, false, null);
    }

    public WorkspaceTab withTitle(String value) {
        return new WorkspaceTab(id, page, value, style, pinned, groupId);
    }

    public WorkspaceTab withStyle(WorkspaceStyle value) {
        return new WorkspaceTab(id, page, title, value, pinned, groupId);
    }

    public WorkspaceTab withPinned(boolean value) {
        return new WorkspaceTab(id, page, title, style, value, groupId);
    }

    public WorkspaceTab withGroup(UUID value) {
        return new WorkspaceTab(id, page, title, style, pinned, value);
    }

    public static String normalizedTitle(String value) {
        if (value == null || value.isBlank()) return "Workspace";
        String trimmed = value.trim();
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }
}
