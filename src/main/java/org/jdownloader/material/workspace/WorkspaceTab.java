package org.jdownloader.material.workspace;

import java.util.Objects;
import java.util.UUID;

/** One named, styled page in the user's locally persisted workspace. */
public record WorkspaceTab(UUID id, WorkspacePage page, String title, WorkspaceStyle style) {

    public WorkspaceTab {
        id = Objects.requireNonNull(id, "id");
        page = Objects.requireNonNull(page, "page");
        title = normalizedTitle(title);
        style = style == null ? WorkspaceStyle.DEFAULT : style;
    }

    public WorkspaceTab withTitle(String value) {
        return new WorkspaceTab(id, page, value, style);
    }

    public WorkspaceTab withStyle(WorkspaceStyle value) {
        return new WorkspaceTab(id, page, title, value);
    }

    public static String normalizedTitle(String value) {
        if (value == null || value.isBlank()) return "Workspace";
        String trimmed = value.trim();
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }
}
