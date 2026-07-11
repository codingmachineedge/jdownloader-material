package org.jdownloader.material.workspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete current workspace state, separate from its immutable Git timeline. */
public record WorkspaceSnapshot(String applicationName, List<WorkspaceTab> tabs, UUID selectedTabId) {

    public WorkspaceSnapshot {
        applicationName = normalizedApplicationName(applicationName);
        tabs = List.copyOf(tabs == null ? List.of() : tabs);
        Set<UUID> ids = new LinkedHashSet<>();
        for (WorkspaceTab tab : tabs) {
            Objects.requireNonNull(tab, "tab");
            if (!ids.add(tab.id())) throw new IllegalArgumentException("Workspace tab ids must be unique");
        }
        if (tabs.size() > 64) throw new IllegalArgumentException("A workspace supports up to 64 open tabs");
        if (selectedTabId != null && !ids.contains(selectedTabId)) selectedTabId = tabs.isEmpty() ? null : tabs.getFirst().id();
        if (selectedTabId == null && !tabs.isEmpty()) selectedTabId = tabs.getFirst().id();
    }

    public static WorkspaceSnapshot fresh() {
        WorkspaceTab downloads = new WorkspaceTab(UUID.randomUUID(), WorkspacePage.DOWNLOADS,
                "Downloads", WorkspaceStyle.DEFAULT);
        return new WorkspaceSnapshot("JDownloader Material", List.of(downloads), downloads.id());
    }

    public static String normalizedApplicationName(String value) {
        if (value == null || value.isBlank()) return "JDownloader Material";
        String trimmed = value.trim();
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    public WorkspaceSnapshot withTabs(List<WorkspaceTab> replacement, UUID selected) {
        return new WorkspaceSnapshot(applicationName, replacement, selected);
    }

    public WorkspaceSnapshot withApplicationName(String replacement) {
        return new WorkspaceSnapshot(replacement, tabs, selectedTabId);
    }

    public WorkspaceSnapshot withSelectedTab(UUID selected) {
        return new WorkspaceSnapshot(applicationName, tabs, selected);
    }

    public WorkspaceTab tab(UUID id) {
        return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst().orElse(null);
    }

    public WorkspaceSnapshot replacing(WorkspaceTab replacement) {
        List<WorkspaceTab> next = new ArrayList<>(tabs);
        int index = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id().equals(replacement.id())) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException("Unknown workspace tab " + replacement.id());
        next.set(index, replacement);
        return withTabs(next, selectedTabId);
    }
}
