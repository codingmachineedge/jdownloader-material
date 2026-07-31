package org.jdownloader.material.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * A private, append-only JGit repository for workspace tabs.
 *
 * <p>The current workspace is represented by {@code workspace.properties};
 * every tab also owns a durable {@code tabs/<id>.properties} record. Closing a
 * tab never deletes its descriptor: it records a closed event and leaves its
 * descriptor in the repository, so the complete timeline remains exportable.
 * All filesystem and Git work happens on a one-thread worker.</p>
 */
public final class GitWorkspaceStore implements AutoCloseable {

    private static final String SCHEMA = "2";
    private static final String LEGACY_SCHEMA = "1";
    private static final long MAX_IMPORT_BYTES = 1024L * 1024L;
    private static final String WORKSPACE_FILE = "workspace.properties";
    private static final String TABS_DIRECTORY = "tabs";
    private static final String EVENTS_DIRECTORY = "events";

    private final Path root;
    private final ExecutorService worker;
    private WorkspaceSnapshot current;
    private long nextSequence = 1;
    private boolean initialized;

    public GitWorkspaceStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workspace-git-writer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Path root() {
        return root;
    }

    /** Loads (or seeds) the current workspace without blocking the caller. */
    public CompletableFuture<WorkspaceSnapshot> load() {
        return submit(() -> {
            ensureInitialized();
            return current;
        });
    }

    /** Opens and selects a new tab, making one append-only local commit. */
    public CompletableFuture<WorkspaceSnapshot> open(WorkspacePage page, String title) {
        return open(new WorkspaceTab(UUID.randomUUID(), page, title, WorkspaceStyle.DEFAULT));
    }

    /** Opens a caller-created descriptor so the interface can update immediately without waiting for I/O. */
    public CompletableFuture<WorkspaceSnapshot> open(WorkspaceTab tab) {
        Objects.requireNonNull(tab, "tab");
        return mutate("Opened " + tab.title(), "open", tab.id(), snapshot -> {
            if (snapshot.tab(tab.id()) != null) throw new IllegalArgumentException("Workspace tab id already exists");
            List<WorkspaceTab> tabs = new ArrayList<>(snapshot.tabs());
            tabs.add(tab);
            return new Mutation(new WorkspaceSnapshot(snapshot.applicationName(), tabs, tab.id(), snapshot.groups()),
                    List.of(), false);
        });
    }

    /** Saves a renamed or restyled tab and records exactly one commit. */
    public CompletableFuture<WorkspaceSnapshot> update(WorkspaceTab updated) {
        Objects.requireNonNull(updated, "updated");
        return mutate("Updated " + updated.title(), "update", updated.id(), snapshot -> {
            if (snapshot.tab(updated.id()) == null) throw new IllegalArgumentException("The tab is no longer open");
            return new Mutation(snapshot.replacing(updated), List.of(), false);
        });
    }

    /** Reorders a tab without changing its pinned/group metadata. */
    public CompletableFuture<WorkspaceSnapshot> moveTab(UUID tabId, int requestedIndex) {
        return mutate("Moved workspace tab", "move", tabId, snapshot -> {
            WorkspaceTab moved = snapshot.tab(tabId);
            if (moved == null) throw new IllegalArgumentException("The tab is no longer open");
            List<WorkspaceTab> tabs = new ArrayList<>(snapshot.tabs());
            tabs.remove(moved);
            int index = Math.max(0, Math.min(requestedIndex, tabs.size()));
            tabs.add(index, moved);
            return new Mutation(snapshot.withTabs(tabs, snapshot.selectedTabId()), List.of(), false);
        });
    }

    /** Pins/unpins a tab; order remains explicit and independently reorderable. */
    public CompletableFuture<WorkspaceSnapshot> setPinned(UUID tabId, boolean pinned) {
        return mutate((pinned ? "Pinned " : "Unpinned ") + "workspace tab", "pin", tabId, snapshot -> {
            WorkspaceTab tab = snapshot.tab(tabId);
            if (tab == null) throw new IllegalArgumentException("The tab is no longer open");
            return new Mutation(snapshot.replacing(tab.withPinned(pinned)), List.of(), false);
        });
    }

    /** Creates an ordered tab group. */
    public CompletableFuture<WorkspaceSnapshot> createGroup(WorkspaceGroup group) {
        Objects.requireNonNull(group, "group");
        return mutate("Created tab group " + group.name(), "create-group", group.id(), snapshot -> {
            if (snapshot.group(group.id()) != null) throw new IllegalArgumentException("Workspace group id already exists");
            List<WorkspaceGroup> groups = new ArrayList<>(snapshot.groups());
            groups.add(group);
            return new Mutation(snapshot.withGroups(groups), List.of(), false);
        });
    }

    public CompletableFuture<WorkspaceSnapshot> updateGroup(WorkspaceGroup group) {
        Objects.requireNonNull(group, "group");
        return mutate("Updated tab group " + group.name(), "update-group", group.id(), snapshot -> {
            if (snapshot.group(group.id()) == null) throw new IllegalArgumentException("The tab group no longer exists");
            return new Mutation(snapshot.replacing(group), List.of(), false);
        });
    }

    public CompletableFuture<WorkspaceSnapshot> moveGroup(UUID groupId, int requestedIndex) {
        return mutate("Moved tab group", "move-group", groupId, snapshot -> {
            WorkspaceGroup moved = snapshot.group(groupId);
            if (moved == null) throw new IllegalArgumentException("The tab group no longer exists");
            List<WorkspaceGroup> groups = new ArrayList<>(snapshot.groups());
            groups.remove(moved);
            groups.add(Math.max(0, Math.min(requestedIndex, groups.size())), moved);
            return new Mutation(snapshot.withGroups(groups), List.of(), false);
        });
    }

    /** Moves a tab into/out of a group and places it at an explicit global order index. */
    public CompletableFuture<WorkspaceSnapshot> moveToGroup(UUID tabId, UUID groupId, int requestedIndex) {
        return mutate("Moved workspace tab between groups", "move-to-group", tabId, snapshot -> {
            WorkspaceTab moved = snapshot.tab(tabId);
            if (moved == null) throw new IllegalArgumentException("The tab is no longer open");
            if (groupId != null && snapshot.group(groupId) == null) {
                throw new IllegalArgumentException("The destination group no longer exists");
            }
            List<WorkspaceTab> tabs = new ArrayList<>(snapshot.tabs());
            tabs.remove(moved);
            tabs.add(Math.max(0, Math.min(requestedIndex, tabs.size())), moved.withGroup(groupId));
            return new Mutation(snapshot.withTabs(tabs, snapshot.selectedTabId()), List.of(), false);
        });
    }

    /** Removes a group without deleting its member tabs. */
    public CompletableFuture<WorkspaceSnapshot> removeGroup(UUID groupId) {
        return mutate("Removed tab group", "remove-group", groupId, snapshot -> {
            if (snapshot.group(groupId) == null) throw new IllegalArgumentException("The tab group no longer exists");
            List<WorkspaceGroup> groups = new ArrayList<>(snapshot.groups());
            groups.removeIf(group -> group.id().equals(groupId));
            List<WorkspaceTab> tabs = snapshot.tabs().stream()
                    .map(tab -> groupId.equals(tab.groupId()) ? tab.withGroup(null) : tab)
                    .toList();
            return new Mutation(new WorkspaceSnapshot(snapshot.applicationName(), tabs,
                    snapshot.selectedTabId(), groups), List.of(), false);
        });
    }

    /** Persists selection changes as part of the workspace timeline. */
    public CompletableFuture<WorkspaceSnapshot> select(UUID tabId) {
        return mutate("Selected workspace tab", "select", tabId, snapshot -> {
            if (snapshot.tab(tabId) == null) throw new IllegalArgumentException("The tab is no longer open");
            return new Mutation(snapshot.withSelectedTab(tabId), List.of(), false);
        });
    }

    /** Closes a tab while retaining its descriptor and an immutable close event. */
    public CompletableFuture<WorkspaceSnapshot> closeTab(UUID tabId) {
        return mutate("Closed workspace tab", "close", tabId, snapshot -> {
            return closeMutation(snapshot, List.of(tabId));
        });
    }

    /** Closes a reviewed set atomically, preserving closed descriptors and pinned protection at the caller. */
    public CompletableFuture<WorkspaceSnapshot> closeTabs(Collection<UUID> tabIds) {
        List<UUID> requested = List.copyOf(Objects.requireNonNull(tabIds, "tabIds"));
        if (requested.isEmpty()) return load();
        return mutate("Closed " + requested.size() + " workspace tabs", "bulk-close", null,
                snapshot -> closeMutation(snapshot, requested));
    }

    /** Renames the running application and keeps the setting in the workspace Git history. */
    public CompletableFuture<WorkspaceSnapshot> renameApplication(String name) {
        return mutate("Renamed application", "rename-application", null,
                snapshot -> new Mutation(snapshot.withApplicationName(name), List.of(), false));
    }

    /** Exports a validated portable workspace snapshot, not the Git history. */
    public CompletableFuture<Path> exportSnapshot(Path target) {
        return submit(() -> {
            ensureInitialized();
            Path output = validatedTarget(target, ".jdmtabs");
            writePropertiesAtomically(output, snapshotProperties(current));
            return output;
        });
    }

    /** Imports a portable snapshot as new tab descriptors and one append-only commit. */
    public CompletableFuture<WorkspaceSnapshot> importSnapshot(Path source) {
        return submit(() -> {
            ensureInitialized();
            Path input = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
            if (!Files.isRegularFile(input)) throw new IOException("Workspace import file does not exist");
            if (Files.size(input) > MAX_IMPORT_BYTES) throw new IOException("Workspace import file is too large");
            Properties properties = readProperties(input);
            WorkspaceSnapshot imported = parseSnapshot(properties, false);
            Map<UUID, UUID> groupIds = new HashMap<>();
            List<WorkspaceGroup> freshGroups = new ArrayList<>();
            for (WorkspaceGroup group : imported.groups()) {
                UUID freshId = UUID.randomUUID();
                groupIds.put(group.id(), freshId);
                freshGroups.add(new WorkspaceGroup(freshId, group.name(), group.color(), group.icon(), group.badge(),
                        group.collapsed(), group.pinned()));
            }
            List<WorkspaceTab> freshTabs = new ArrayList<>();
            for (WorkspaceTab tab : imported.tabs()) {
                freshTabs.add(new WorkspaceTab(UUID.randomUUID(), tab.page(), tab.title(), tab.style(), tab.pinned(),
                        groupIds.get(tab.groupId())));
            }
            WorkspaceSnapshot replacement = new WorkspaceSnapshot(imported.applicationName(), freshTabs,
                    freshTabs.isEmpty() ? null : freshTabs.getFirst().id(), freshGroups);
            persist("Imported workspace", "import", null, replacement, List.of(), false);
            current = replacement;
            return current;
        });
    }

    /** Exports the complete private repository, including every immutable event, as a ZIP. */
    public CompletableFuture<Path> exportRepository(Path target) {
        return submit(() -> {
            ensureInitialized();
            Path output = validatedTarget(target, ".zip");
            if (output.startsWith(root)) throw new IOException("Workspace repository export must be outside its repository");
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tempParent = parent == null ? Path.of(System.getProperty("java.io.tmpdir")) : parent;
            Path temp = Files.createTempFile(tempParent, "jdm-workspace-", ".zip");
            try {
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                    try (var paths = Files.walk(root)) {
                        for (Path path : paths.filter(Files::isRegularFile).toList()) {
                            String name = "jdownloader-material-workspace/"
                                    + root.relativize(path).toString().replace('\\', '/');
                            zip.putNextEntry(new ZipEntry(name));
                            Files.copy(path, zip);
                            zip.closeEntry();
                        }
                    }
                }
                moveAtomically(temp, output);
            } finally {
                Files.deleteIfExists(temp);
            }
            return output;
        });
    }

    /** Completes after all accepted changes have been durably committed. */
    public CompletableFuture<Void> flush() {
        return submit(() -> null);
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(8, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException interrupted) {
            worker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private CompletableFuture<WorkspaceSnapshot> mutate(String summary, String action, UUID tabId,
                                                          Mutator mutator) {
        return submit(() -> {
            ensureInitialized();
            Mutation mutation = mutator.apply(current);
            if (mutation.snapshot().equals(current)) return current;
            persist(summary, action, tabId, mutation.snapshot(), mutation.closedDescriptors(), mutation.closed());
            current = mutation.snapshot();
            return current;
        });
    }

    private static Mutation closeMutation(WorkspaceSnapshot snapshot, Collection<UUID> requestedIds) {
        List<UUID> ids = requestedIds.stream().distinct().toList();
        List<WorkspaceTab> closed = snapshot.tabs().stream().filter(tab -> ids.contains(tab.id())).toList();
        if (closed.size() != ids.size()) throw new IllegalArgumentException("One or more tabs are no longer open");
        List<WorkspaceTab> tabs = new ArrayList<>(snapshot.tabs());
        tabs.removeIf(tab -> ids.contains(tab.id()));
        if (tabs.isEmpty()) {
            tabs.add(new WorkspaceTab(UUID.randomUUID(), WorkspacePage.DOWNLOADS,
                    "Downloads", WorkspaceStyle.DEFAULT));
        }
        UUID selected = ids.contains(snapshot.selectedTabId()) ? tabs.getFirst().id() : snapshot.selectedTabId();
        return new Mutation(new WorkspaceSnapshot(snapshot.applicationName(), tabs, selected, snapshot.groups()),
                closed, true);
    }

    private void ensureInitialized() throws Exception {
        if (initialized) return;
        Files.createDirectories(root);
        try (Git git = openOrCreate()) {
            Path state = root.resolve(WORKSPACE_FILE);
            if (Files.isRegularFile(state)) {
                current = parseSnapshot(readProperties(state), true);
                nextSequence = highestEventSequence() + 1;
            } else {
                current = WorkspaceSnapshot.fresh();
                persistWithGit(git, "Seed workspace", "seed", null, current, List.of(), false);
            }
            initialized = true;
        }
    }

    private void persist(String summary, String action, UUID tabId, WorkspaceSnapshot snapshot,
                         List<WorkspaceTab> closedDescriptors, boolean closed)
            throws Exception {
        try (Git git = openOrCreate()) {
            persistWithGit(git, summary, action, tabId, snapshot, closedDescriptors, closed);
        }
    }

    private void persistWithGit(Git git, String summary, String action, UUID tabId, WorkspaceSnapshot snapshot,
                                 List<WorkspaceTab> closedDescriptors, boolean closed)
            throws Exception {
        List<String> changed = new ArrayList<>();
        writePropertiesAtomically(root.resolve(WORKSPACE_FILE), snapshotProperties(snapshot));
        changed.add(WORKSPACE_FILE);
        for (WorkspaceTab closedDescriptor : closedDescriptors) {
            writePropertiesAtomically(tabPath(closedDescriptor.id()), tabProperties(closedDescriptor, false));
            changed.add(relative(tabPath(closedDescriptor.id())));
        }
        // A descriptor is never removed. Rewriting the current descriptors
        // keeps their working-tree state authoritative while prior commits
        // retain every historical version.
        for (WorkspaceTab tab : snapshot.tabs()) {
            writePropertiesAtomically(tabPath(tab.id()), tabProperties(tab, true));
            changed.add(relative(tabPath(tab.id())));
        }
        long sequence = nextSequence++;
        Path eventPath = root.resolve(EVENTS_DIRECTORY).resolve(String.format("%020d-%s.properties", sequence,
                UUID.randomUUID()));
        writePropertiesAtomically(eventPath, eventProperties(sequence, action, summary, tabId, snapshot,
                closedDescriptors.size(), closed));
        changed.add(relative(eventPath));
        for (String path : changed) git.add().addFilepattern(path).call();
        git.commit().setMessage(summary).setAuthor(person()).setCommitter(person()).call();
    }

    private Git openOrCreate() throws IOException, GitAPIException {
        if (Files.isDirectory(root.resolve(".git"))) return Git.open(root.toFile());
        return Git.init().setDirectory(root.toFile()).call();
    }

    private Properties snapshotProperties(WorkspaceSnapshot snapshot) {
        Properties properties = new Properties();
        properties.setProperty("schema", SCHEMA);
        properties.setProperty("applicationName", snapshot.applicationName());
        properties.setProperty("selectedTabId", snapshot.selectedTabId() == null ? "" : snapshot.selectedTabId().toString());
        properties.setProperty("tabCount", Integer.toString(snapshot.tabs().size()));
        for (int index = 0; index < snapshot.tabs().size(); index++) {
            WorkspaceTab tab = snapshot.tabs().get(index);
            String prefix = "tab." + index + ".";
            properties.setProperty(prefix + "id", tab.id().toString());
            properties.setProperty(prefix + "page", tab.page().name());
            properties.setProperty(prefix + "title", tab.title());
            properties.setProperty(prefix + "fontFamily", tab.style().fontFamily());
            properties.setProperty(prefix + "fontSize", Double.toString(tab.style().fontSize()));
            properties.setProperty(prefix + "bold", Boolean.toString(tab.style().bold()));
            properties.setProperty(prefix + "italic", Boolean.toString(tab.style().italic()));
            properties.setProperty(prefix + "color", tab.style().color());
            properties.setProperty(prefix + "pinned", Boolean.toString(tab.pinned()));
            properties.setProperty(prefix + "groupId", tab.groupId() == null ? "" : tab.groupId().toString());
        }
        properties.setProperty("groupCount", Integer.toString(snapshot.groups().size()));
        for (int index = 0; index < snapshot.groups().size(); index++) {
            WorkspaceGroup group = snapshot.groups().get(index);
            String prefix = "group." + index + ".";
            properties.setProperty(prefix + "id", group.id().toString());
            properties.setProperty(prefix + "name", group.name());
            properties.setProperty(prefix + "color", group.color());
            properties.setProperty(prefix + "icon", group.icon());
            properties.setProperty(prefix + "badge", group.badge());
            properties.setProperty(prefix + "collapsed", Boolean.toString(group.collapsed()));
            properties.setProperty(prefix + "pinned", Boolean.toString(group.pinned()));
        }
        return properties;
    }

    private static Properties tabProperties(WorkspaceTab tab, boolean open) {
        Properties properties = new Properties();
        properties.setProperty("schema", SCHEMA);
        properties.setProperty("id", tab.id().toString());
        properties.setProperty("page", tab.page().name());
        properties.setProperty("title", tab.title());
        properties.setProperty("fontFamily", tab.style().fontFamily());
        properties.setProperty("fontSize", Double.toString(tab.style().fontSize()));
        properties.setProperty("bold", Boolean.toString(tab.style().bold()));
        properties.setProperty("italic", Boolean.toString(tab.style().italic()));
        properties.setProperty("color", tab.style().color());
        properties.setProperty("pinned", Boolean.toString(tab.pinned()));
        properties.setProperty("groupId", tab.groupId() == null ? "" : tab.groupId().toString());
        properties.setProperty("open", Boolean.toString(open));
        return properties;
    }

    private static Properties eventProperties(long sequence, String action, String summary, UUID tabId,
                                               WorkspaceSnapshot snapshot, int closedCount, boolean closed) {
        Properties properties = new Properties();
        properties.setProperty("schema", SCHEMA);
        properties.setProperty("sequence", Long.toString(sequence));
        properties.setProperty("timestamp", Instant.now().toString());
        properties.setProperty("action", action);
        properties.setProperty("summary", summary);
        if (tabId != null) properties.setProperty("tabId", tabId.toString());
        properties.setProperty("selectedTabId", snapshot.selectedTabId() == null ? "" : snapshot.selectedTabId().toString());
        properties.setProperty("applicationName", snapshot.applicationName());
        properties.setProperty("closed", Boolean.toString(closed));
        properties.setProperty("closedCount", Integer.toString(closedCount));
        return properties;
    }

    private WorkspaceSnapshot parseSnapshot(Properties properties, boolean loadDescriptorFiles) throws IOException {
        String schema = properties.getProperty("schema");
        if (!SCHEMA.equals(schema) && !LEGACY_SCHEMA.equals(schema)) {
            throw new IOException("Unsupported workspace snapshot");
        }
        List<WorkspaceGroup> groups = new ArrayList<>();
        if (SCHEMA.equals(schema)) {
            int groupCount = boundedInt(properties.getProperty("groupCount"), 0, 32);
            for (int index = 0; index < groupCount; index++) {
                String prefix = "group." + index + ".";
                groups.add(new WorkspaceGroup(parseUuid(required(properties, prefix + "id"), "group id"),
                        properties.getProperty(prefix + "name", "Group"),
                        properties.getProperty(prefix + "color", WorkspaceGroup.DEFAULT_COLOR),
                        properties.getProperty(prefix + "icon", "folder"),
                        properties.getProperty(prefix + "badge", ""),
                        Boolean.parseBoolean(properties.getProperty(prefix + "collapsed", "false")),
                        Boolean.parseBoolean(properties.getProperty(prefix + "pinned", "false"))));
            }
        }
        int count = boundedInt(properties.getProperty("tabCount"), 0, 64);
        List<WorkspaceTab> tabs = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String idValue = properties.getProperty("tab." + index + ".id");
            if (idValue == null) throw new IOException("Workspace snapshot is missing a tab id");
            UUID id = parseUuid(idValue, "tab id");
            Path tabFile = tabPath(id);
            Properties tab = loadDescriptorFiles && Files.isRegularFile(tabFile) ? readProperties(tabFile) : properties;
            tabs.add(parseTab(tab, id, index, SCHEMA.equals(schema)));
        }
        UUID selected = null;
        String selectedValue = properties.getProperty("selectedTabId", "").trim();
        if (!selectedValue.isEmpty()) selected = parseUuid(selectedValue, "selected tab id");
        return new WorkspaceSnapshot(properties.getProperty("applicationName", "JDownloader Material"),
                tabs, selected, groups);
    }

    private static WorkspaceTab parseTab(Properties properties, UUID expectedId, int fallbackIndex,
                                         boolean schemaTwo) throws IOException {
        String prefix = properties.containsKey("page") ? "" : "tab." + fallbackIndex + ".";
        UUID id = expectedId;
        String explicit = properties.getProperty(prefix + "id");
        if (explicit != null && !explicit.isBlank()) id = parseUuid(explicit, "tab id");
        try {
            WorkspacePage page = WorkspacePage.valueOf(required(properties, prefix + "page"));
            WorkspaceStyle style = new WorkspaceStyle(properties.getProperty(prefix + "fontFamily", "System"),
                    doubleValue(properties.getProperty(prefix + "fontSize"), 13),
                    Boolean.parseBoolean(properties.getProperty(prefix + "bold", "false")),
                    Boolean.parseBoolean(properties.getProperty(prefix + "italic", "false")),
                    properties.getProperty(prefix + "color", "#1D1B20"));
            UUID groupId = null;
            String groupValue = properties.getProperty(prefix + "groupId", "").trim();
            if (schemaTwo && !groupValue.isEmpty()) groupId = parseUuid(groupValue, "group id");
            return new WorkspaceTab(id, page, properties.getProperty(prefix + "title", page.name()), style,
                    schemaTwo && Boolean.parseBoolean(properties.getProperty(prefix + "pinned", "false")), groupId);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Workspace tab is invalid", invalid);
        }
    }

    private long highestEventSequence() throws IOException {
        Path events = root.resolve(EVENTS_DIRECTORY);
        if (!Files.isDirectory(events)) return 0;
        long highest = 0;
        try (var paths = Files.list(events)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                try {
                    highest = Math.max(highest, Long.parseLong(readProperties(path).getProperty("sequence", "0")));
                } catch (NumberFormatException ignored) {
                    // Damaged event records do not erase valid prior history.
                }
            }
        }
        return highest;
    }

    private Path tabPath(UUID id) {
        return root.resolve(TABS_DIRECTORY).resolve(id + ".properties");
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static Path validatedTarget(Path target, String suffix) throws IOException {
        Path output = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        if (output.getFileName() == null) throw new IOException("Workspace export target is invalid");
        String name = output.getFileName().toString().toLowerCase();
        if (!name.endsWith(suffix)) output = output.resolveSibling(output.getFileName() + suffix);
        return output;
    }

    private static Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void writePropertiesAtomically(Path path, Properties properties) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tempParent = parent == null ? Path.of(System.getProperty("java.io.tmpdir")) : parent;
        Path temp = Files.createTempFile(tempParent, "workspace-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(output, "JDownloader Material local workspace");
            }
            moveAtomically(temp, path);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int boundedInt(String value, int min, int max) throws IOException {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException invalid) {
            throw new IOException("Workspace tab count is invalid", invalid);
        }
    }

    private static UUID parseUuid(String value, String label) throws IOException {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException invalid) {
            throw new IOException("Workspace " + label + " is invalid", invalid);
        }
    }

    private static double doubleValue(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Workspace snapshot is missing " + key);
        return value;
    }

    private static PersonIdent person() {
        return new PersonIdent("JDownloader Material", "workspace@jdownloader-material.local");
    }

    private <T> CompletableFuture<T> submit(ThrowingSupplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    result.complete(task.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface Mutator {
        Mutation apply(WorkspaceSnapshot snapshot) throws Exception;
    }

    private record Mutation(WorkspaceSnapshot snapshot, List<WorkspaceTab> closedDescriptors, boolean closed) {
        private Mutation {
            closedDescriptors = List.copyOf(closedDescriptors == null ? List.of() : closedDescriptors);
        }
    }
}
