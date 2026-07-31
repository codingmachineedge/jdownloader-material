package org.jdownloader.material.workspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete current workspace state, separate from its immutable Git timeline. */
public record WorkspaceSnapshot(String applicationName, List<WorkspaceTab> tabs, UUID selectedTabId,
                                List<WorkspaceGroup> groups) {

    public WorkspaceSnapshot {
        applicationName = normalizedApplicationName(applicationName);
        tabs = List.copyOf(tabs == null ? List.of() : tabs);
        groups = List.copyOf(groups == null ? List.of() : groups);
        Set<UUID> ids = new LinkedHashSet<>();
        for (WorkspaceTab tab : tabs) {
            Objects.requireNonNull(tab, "tab");
            if (!ids.add(tab.id())) throw new IllegalArgumentException("Workspace tab ids must be unique");
        }
        if (tabs.size() > 64) throw new IllegalArgumentException("A workspace supports up to 64 open tabs");
        Set<UUID> groupIds = new LinkedHashSet<>();
        for (WorkspaceGroup group : groups) {
            Objects.requireNonNull(group, "group");
            if (!groupIds.add(group.id())) throw new IllegalArgumentException("Workspace group ids must be unique");
        }
        if (groups.size() > 32) throw new IllegalArgumentException("A workspace supports up to 32 tab groups");
        List<WorkspaceTab> sanitized = new ArrayList<>(tabs.size());
        for (WorkspaceTab tab : tabs) {
            sanitized.add(tab.groupId() != null && !groupIds.contains(tab.groupId()) ? tab.withGroup(null) : tab);
        }
        tabs = List.copyOf(sanitized);
        if (selectedTabId != null && !ids.contains(selectedTabId)) selectedTabId = tabs.isEmpty() ? null : tabs.getFirst().id();
        if (selectedTabId == null && !tabs.isEmpty()) selectedTabId = tabs.getFirst().id();
    }

    /** Backward-compatible constructor for schema-v1 callers. */
    public WorkspaceSnapshot(String applicationName, List<WorkspaceTab> tabs, UUID selectedTabId) {
        this(applicationName, tabs, selectedTabId, List.of());
    }

    public static WorkspaceSnapshot fresh() {
        WorkspaceTab downloads = new WorkspaceTab(UUID.randomUUID(), WorkspacePage.DOWNLOADS,
                "Downloads", WorkspaceStyle.DEFAULT);
        return new WorkspaceSnapshot("JDownloader Material", List.of(downloads), downloads.id(), List.of());
    }

    public static String normalizedApplicationName(String value) {
        if (value == null || value.isBlank()) return "JDownloader Material";
        String trimmed = value.trim();
        return trimmed.length() > 96 ? trimmed.substring(0, 96) : trimmed;
    }

    public WorkspaceSnapshot withTabs(List<WorkspaceTab> replacement, UUID selected) {
        return new WorkspaceSnapshot(applicationName, replacement, selected, groups);
    }

    public WorkspaceSnapshot withApplicationName(String replacement) {
        return new WorkspaceSnapshot(replacement, tabs, selectedTabId, groups);
    }

    public WorkspaceSnapshot withSelectedTab(UUID selected) {
        return new WorkspaceSnapshot(applicationName, tabs, selected, groups);
    }

    public WorkspaceSnapshot withGroups(List<WorkspaceGroup> replacement) {
        return new WorkspaceSnapshot(applicationName, tabs, selectedTabId, replacement);
    }

    public WorkspaceTab tab(UUID id) {
        return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst().orElse(null);
    }

    public WorkspaceGroup group(UUID id) {
        return id == null ? null : groups.stream().filter(group -> group.id().equals(id)).findFirst().orElse(null);
    }

    public WorkspaceSnapshot replacing(WorkspaceTab replacement) {
        List<WorkspaceTab> next = new ArrayList<>(tabs);
        int index = -1;
        for (int i = 0; i < next.size(); i++) if (next.get(i).id().equals(replacement.id())) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException("Unknown workspace tab " + replacement.id());
        next.set(index, replacement);
        return withTabs(next, selectedTabId);
    }

    public WorkspaceSnapshot replacing(WorkspaceGroup replacement) {
        List<WorkspaceGroup> next = new ArrayList<>(groups);
        int index = -1;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(replacement.id())) { index = i; break; }
        }
        if (index < 0) throw new IllegalArgumentException("Unknown workspace group " + replacement.id());
        next.set(index, replacement);
        return withGroups(next);
    }
}
