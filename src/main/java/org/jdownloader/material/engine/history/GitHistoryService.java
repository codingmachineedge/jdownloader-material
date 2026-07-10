package org.jdownloader.material.engine.history;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Embedded JGit-backed history storage. It owns three private working-tree
 * repositories below its root:
 *
 * <pre>
 * root/settings
 * root/download-lists
 * root/manifest
 * </pre>
 *
 * Every revision is a small append-only three-repository transaction. A
 * canonical snapshot is first committed to the manifest as a durable prepare
 * record. The two snapshot repositories are then committed, followed by a
 * completion event in the manifest. On startup, any prepared event without a
 * completion is finished from its manifest copy. No reset, rebase, removal, or
 * garbage collection is used.
 */
public final class GitHistoryService extends AbstractHistoryService {

    private static final String EVENTS_DIRECTORY = "events";
    private static final String PREPARES_DIRECTORY = "prepares";
    private static final String MANIFEST_SCHEMA = "2";
    private static final String PREPARE_KIND = "prepare";
    private static final String COMPLETE_KIND = "complete";
    private static final int MAX_OBJECT_BYTES = 16 * 1024 * 1024;

    private final Path root;
    private final Path settingsRepository;
    private final Path downloadListsRepository;
    private final Path manifestRepository;
    private final PrepareHook prepareHook;

    /** Worker-thread-only next durable sequence. */
    private long nextSequence = 1;

    public GitHistoryService(Path root, Supplier<HistorySnapshot> snapshotSupplier,
                             Consumer<HistorySnapshot> snapshotApplier) {
        this(root, snapshotSupplier, snapshotApplier, (entry, sequence) -> { });
    }

    /** Package-private fault point used only by the storage smoke check. */
    GitHistoryService(Path root, Supplier<HistorySnapshot> snapshotSupplier,
                      Consumer<HistorySnapshot> snapshotApplier, PrepareHook prepareHook) {
        super(snapshotSupplier, snapshotApplier, "history-git-writer");
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.settingsRepository = this.root.resolve("settings");
        this.downloadListsRepository = this.root.resolve("download-lists");
        this.manifestRepository = this.root.resolve("manifest");
        this.prepareHook = Objects.requireNonNull(prepareHook, "prepareHook");
        initialize();
    }

    public Path root() {
        return root;
    }

    @Override
    protected List<Checkpoint> loadPersistentTimeline() throws Exception {
        Files.createDirectories(root);
        // Ensure all three repositories exist even before the first mutation.
        try (Git settings = openOrCreate(settingsRepository);
             Git lists = openOrCreate(downloadListsRepository);
             Git manifest = openOrCreate(manifestRepository)) {
            ManifestState beforeRepair = readManifest(manifest);
            repairPreparedTransactions(settings, lists, manifest, beforeRepair);
            ManifestState repaired = readManifest(manifest);
            nextSequence = nextSequenceAfter(repaired.highestSequence());
            return repaired.completed();
        }
    }

    @Override
    protected Checkpoint persist(HistoryEntry entry, HistorySnapshot snapshot) throws Exception {
        long sequence = allocateSequence();
        try (Git settings = openOrCreate(settingsRepository);
             Git lists = openOrCreate(downloadListsRepository);
             Git manifest = openOrCreate(manifestRepository)) {
            PreparedTransaction prepared = writePrepare(manifest, entry, snapshot, sequence);
            // The test-only hook deliberately runs after the durable prepare
            // commit so startup recovery exercises the real crash window.
            prepareHook.afterDurablePrepare(entry, sequence);
            return completePreparedTransaction(settings, lists, manifest, prepared, snapshot);
        }
    }

    @Override
    protected HistorySnapshot readSnapshot(Checkpoint checkpoint) throws Exception {
        if (checkpoint.settingsCommit() == null || checkpoint.downloadListsCommit() == null) {
            throw new IllegalStateException("History event " + checkpoint.entry().id() + " has no snapshot commits");
        }
        try (Git settings = openOrCreate(settingsRepository);
             Git lists = openOrCreate(downloadListsRepository)) {
            byte[] settingsBytes = readFileAtCommit(settings.getRepository(), checkpoint.settingsCommit(),
                    HistorySnapshot.SETTINGS_FILE);
            byte[] downloadsBytes = readFileAtCommit(lists.getRepository(), checkpoint.downloadListsCommit(),
                    HistorySnapshot.DOWNLOADS_FILE);
            byte[] linkGrabberBytes = readFileAtCommit(lists.getRepository(), checkpoint.downloadListsCommit(),
                    HistorySnapshot.LINKGRABBER_FILE);
            return HistorySnapshot.fromCanonicalBytes(settingsBytes, downloadsBytes, linkGrabberBytes);
        }
    }

    @Override
    protected long measureStorageBytes() throws Exception {
        if (!Files.exists(root)) return 0;
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).mapToLong(GitHistoryService::fileSize).sum();
        }
    }

    private long allocateSequence() {
        if (nextSequence < 1 || nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("History sequence space is exhausted");
        }
        return nextSequence++;
    }

    private static long nextSequenceAfter(long highestSequence) {
        if (highestSequence < 0 || highestSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("History sequence space is exhausted");
        }
        return highestSequence + 1;
    }

    private void repairPreparedTransactions(Git settings, Git lists, Git manifest, ManifestState state)
            throws Exception {
        for (PreparedTransaction prepared : state.incompletePrepares()) {
            HistorySnapshot snapshot = readPrepareSnapshot(manifest.getRepository(), prepared);
            completePreparedTransaction(settings, lists, manifest, prepared, snapshot);
        }
    }

    private Checkpoint completePreparedTransaction(Git settings, Git lists, Git manifest,
                                                    PreparedTransaction prepared,
                                                    HistorySnapshot snapshot) throws Exception {
        HistoryEntry entry = prepared.entry();
        String settingsCommit = findSnapshotCommit(settings, entry.id());
        if (settingsCommit == null) {
            settingsCommit = commitSnapshot(settings, settingsRepository,
                    List.of(new SnapshotFile(HistorySnapshot.SETTINGS_FILE, snapshot.settingsBytes())),
                    entry, prepared.sequence());
        }
        String listsCommit = findSnapshotCommit(lists, entry.id());
        if (listsCommit == null) {
            listsCommit = commitSnapshot(lists, downloadListsRepository,
                    List.of(
                            new SnapshotFile(HistorySnapshot.DOWNLOADS_FILE, snapshot.downloadsBytes()),
                            new SnapshotFile(HistorySnapshot.LINKGRABBER_FILE, snapshot.linkGrabberBytes())),
                    entry, prepared.sequence());
        }
        writeCompletion(manifest, prepared, settingsCommit, listsCommit);
        return new Checkpoint(prepared.sequence(), entry, null, settingsCommit, listsCommit);
    }

    private PreparedTransaction writePrepare(Git manifest, HistoryEntry entry, HistorySnapshot snapshot,
                                             long sequence) throws Exception {
        String folder = prepareFolder(entry.id());
        String metadataPath = prepareMetadataPath(entry.id());
        Properties prepare = eventProperties(entry, sequence, PREPARE_KIND);
        prepare.setProperty("settingsSnapshot", folder + "/" + HistorySnapshot.SETTINGS_FILE);
        prepare.setProperty("downloadsSnapshot", folder + "/" + HistorySnapshot.DOWNLOADS_FILE);
        prepare.setProperty("linkGrabberSnapshot", folder + "/" + HistorySnapshot.LINKGRABBER_FILE);
        List<SnapshotFile> files = List.of(
                new SnapshotFile(metadataPath, HistorySnapshot.canonicalProperties(prepare, false)),
                new SnapshotFile(folder + "/" + HistorySnapshot.SETTINGS_FILE, snapshot.settingsBytes()),
                new SnapshotFile(folder + "/" + HistorySnapshot.DOWNLOADS_FILE, snapshot.downloadsBytes()),
                new SnapshotFile(folder + "/" + HistorySnapshot.LINKGRABBER_FILE, snapshot.linkGrabberBytes()));
        List<String> paths = new ArrayList<>(files.size());
        for (SnapshotFile file : files) {
            writeAtomically(manifestRepository.resolve(file.path()), file.bytes());
            paths.add(file.path());
        }
        commitFiles(manifest, paths, messageFor(entry, "prepare", sequence));
        return new PreparedTransaction(sequence, entry, null);
    }

    private void writeCompletion(Git manifest, PreparedTransaction prepared,
                                 String settingsCommit, String downloadListsCommit) throws Exception {
        String eventPath = eventPath(prepared.entry().id());
        Properties event = eventProperties(prepared.entry(), prepared.sequence(), COMPLETE_KIND);
        event.setProperty("prepareId", prepared.entry().id());
        event.setProperty("settingsCommit", settingsCommit);
        event.setProperty("downloadListsCommit", downloadListsCommit);
        writeAtomically(manifestRepository.resolve(eventPath), HistorySnapshot.canonicalProperties(event, false));
        commitFiles(manifest, List.of(eventPath), messageFor(prepared.entry(), "complete", prepared.sequence()));
    }

    private static Properties eventProperties(HistoryEntry entry, long sequence, String kind) {
        Properties event = new Properties();
        event.setProperty("schema", MANIFEST_SCHEMA);
        event.setProperty("kind", kind);
        event.setProperty("sequence", Long.toString(sequence));
        event.setProperty("id", entry.id());
        event.setProperty("timestamp", entry.timestamp().toString());
        event.setProperty("scope", entry.scope().name());
        event.setProperty("operation", entry.operation().name());
        event.setProperty("summary", entry.summary());
        event.setProperty("status", entry.status().name());
        if (entry.targetId() != null) event.setProperty("targetId", entry.targetId());
        if (entry.error() != null) event.setProperty("error", entry.error());
        return event;
    }

    private ManifestState readManifest(Git manifest) throws Exception {
        Repository repository = manifest.getRepository();
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) return ManifestState.empty();
        Map<String, PreparedTransaction> prepares = new HashMap<>();
        Map<String, Checkpoint> completions = new HashMap<>();
        long highestSequence = 0;
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(head);
            ObjectId treeId = commit.getTree().getId();
            try (TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(commit.getTree());
                tree.setRecursive(true);
                while (tree.next()) {
                    String path = tree.getPathString();
                    if (!isMetadataPath(path, PREPARES_DIRECTORY) && !isMetadataPath(path, EVENTS_DIRECTORY)) continue;
                    try {
                        byte[] bytes = repository.open(tree.getObjectId(0)).getBytes(MAX_OBJECT_BYTES);
                        if (isMetadataPath(path, PREPARES_DIRECTORY)) {
                            PreparedTransaction prepared = parsePrepare(bytes, treeId);
                            if (prepared != null) {
                                PreparedTransaction previous = prepares.putIfAbsent(prepared.entry().id(), prepared);
                                if (previous != null) throw new IOException("Duplicate manifest prepare id "
                                        + prepared.entry().id());
                                highestSequence = Math.max(highestSequence, prepared.sequence());
                            }
                        } else {
                            Checkpoint completion = parseCompletion(bytes);
                            if (completion != null) {
                                Checkpoint previous = completions.putIfAbsent(completion.entry().id(), completion);
                                if (previous != null) throw new IOException("Duplicate manifest completion id "
                                        + completion.entry().id());
                                highestSequence = Math.max(highestSequence, completion.sequence());
                            }
                        }
                    } catch (IOException consistencyFailure) {
                        throw consistencyFailure;
                    } catch (Exception ignored) {
                        // One externally damaged record must not prevent recovery
                        // of older valid append-only revisions.
                    }
                }
            }
        }
        verifySequenceOwnership(prepares, completions);
        List<Checkpoint> ordered = new ArrayList<>(completions.values());
        ordered.sort(Comparator.comparingLong(Checkpoint::sequence));
        return new ManifestState(List.copyOf(ordered), Map.copyOf(prepares), Set.copyOf(completions.keySet()),
                highestSequence);
    }

    private static boolean isMetadataPath(String path, String directory) {
        if (!path.startsWith(directory + "/") || !path.endsWith(".properties")) return false;
        String relative = path.substring(directory.length() + 1);
        return !relative.contains("/");
    }

    private static void verifySequenceOwnership(Map<String, PreparedTransaction> prepares,
                                                Map<String, Checkpoint> completions) throws IOException {
        Map<Long, String> owners = new HashMap<>();
        for (PreparedTransaction prepared : prepares.values()) {
            putSequenceOwner(owners, prepared.sequence(), prepared.entry().id());
        }
        for (Checkpoint completion : completions.values()) {
            PreparedTransaction prepared = prepares.get(completion.entry().id());
            if (prepared != null && (prepared.sequence() != completion.sequence()
                    || !prepared.entry().equals(completion.entry()))) {
                throw new IOException("Manifest prepare and completion disagree for " + completion.entry().id());
            }
            putSequenceOwner(owners, completion.sequence(), completion.entry().id());
        }
    }

    private static void putSequenceOwner(Map<Long, String> owners, long sequence, String id) throws IOException {
        if (sequence < 1) throw new IOException("Manifest sequence must be positive");
        String existing = owners.putIfAbsent(sequence, id);
        if (existing != null && !existing.equals(id)) {
            throw new IOException("Manifest sequence " + sequence + " belongs to multiple transactions");
        }
    }

    private static PreparedTransaction parsePrepare(byte[] bytes, ObjectId treeId) throws IOException {
        Properties event = readProperties(bytes);
        if (!MANIFEST_SCHEMA.equals(event.getProperty("schema")) || !PREPARE_KIND.equals(event.getProperty("kind"))) {
            return null;
        }
        HistoryEntry entry = entryFrom(event);
        return new PreparedTransaction(sequenceFrom(event), entry, treeId);
    }

    private static Checkpoint parseCompletion(byte[] bytes) throws IOException {
        Properties event = readProperties(bytes);
        if (!MANIFEST_SCHEMA.equals(event.getProperty("schema")) || !COMPLETE_KIND.equals(event.getProperty("kind"))) {
            return null;
        }
        HistoryEntry entry = entryFrom(event);
        return new Checkpoint(sequenceFrom(event), entry, null, required(event, "settingsCommit"),
                required(event, "downloadListsCommit"));
    }

    private static Properties readProperties(byte[] bytes) throws IOException {
        Properties event = new Properties();
        event.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        return event;
    }

    private static HistoryEntry entryFrom(Properties event) {
        return new HistoryEntry(
                required(event, "id"),
                Instant.parse(required(event, "timestamp")),
                HistoryScope.valueOf(required(event, "scope")),
                HistoryOperation.valueOf(required(event, "operation")),
                required(event, "summary"),
                HistoryStatus.valueOf(required(event, "status")),
                event.getProperty("targetId"),
                event.getProperty("error"));
    }

    private static long sequenceFrom(Properties event) {
        try {
            long sequence = Long.parseLong(required(event, "sequence"));
            if (sequence < 1) throw new IllegalArgumentException("Manifest sequence must be positive");
            return sequence;
        } catch (NumberFormatException invalidSequence) {
            throw new IllegalArgumentException("Manifest sequence is invalid", invalidSequence);
        }
    }

    private static String required(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Manifest event is missing " + key);
        return value;
    }

    private static HistorySnapshot readPrepareSnapshot(Repository repository, PreparedTransaction prepared)
            throws Exception {
        String folder = prepareFolder(prepared.entry().id());
        byte[] settings = readFileAtTree(repository, prepared.manifestTree(),
                folder + "/" + HistorySnapshot.SETTINGS_FILE);
        byte[] downloads = readFileAtTree(repository, prepared.manifestTree(),
                folder + "/" + HistorySnapshot.DOWNLOADS_FILE);
        byte[] linkGrabber = readFileAtTree(repository, prepared.manifestTree(),
                folder + "/" + HistorySnapshot.LINKGRABBER_FILE);
        return HistorySnapshot.fromCanonicalBytes(settings, downloads, linkGrabber);
    }

    private static Git openOrCreate(Path workTree) throws IOException, GitAPIException {
        Files.createDirectories(workTree);
        Git git = Files.isDirectory(workTree.resolve(".git"))
                ? Git.open(workTree.toFile())
                : Git.init().setDirectory(workTree.toFile()).setInitialBranch("main").call();
        configureAppendOnly(git.getRepository());
        return git;
    }

    private static void configureAppendOnly(Repository repository) throws IOException {
        StoredConfig config = repository.getConfig();
        config.setInt("gc", null, "auto", 0);
        config.setString("gc", null, "pruneExpire", "never");
        config.setString("gc", null, "reflogExpire", "never");
        config.setString("gc", null, "reflogExpireUnreachable", "never");
        config.setBoolean("core", null, "autocrlf", false);
        config.setBoolean("core", null, "logAllRefUpdates", true);
        config.save();
    }

    private static String findSnapshotCommit(Git git, String historyId) throws Exception {
        try {
            for (RevCommit commit : git.log().all().call()) {
                if (commit.getFullMessage().contains("JDM-History-Id: " + historyId)) return commit.getName();
            }
        } catch (NoHeadException noHead) {
            return null;
        }
        return null;
    }

    private static String commitSnapshot(Git git, Path workTree, List<SnapshotFile> files,
                                         HistoryEntry entry, long sequence) throws Exception {
        List<String> paths = new ArrayList<>(files.size());
        for (SnapshotFile file : files) {
            writeAtomically(workTree.resolve(file.path()), file.bytes());
            paths.add(file.path());
        }
        return commitFiles(git, paths, messageFor(entry, "snapshot", sequence));
    }

    private static String commitFiles(Git git, List<String> paths, String message) throws GitAPIException {
        for (String path : paths) git.add().addFilepattern(path.replace('\\', '/')).call();
        PersonIdent author = new PersonIdent("JDownloader Material History", "history@localhost");
        return git.commit()
                .setMessage(message)
                .setAuthor(author)
                .setCommitter(author)
                // A revision represents a user-visible action even when its
                // canonical content is byte-identical to the preceding state.
                .setAllowEmpty(true)
                .call()
                .getName();
    }

    private static String messageFor(HistoryEntry entry, String kind, long sequence) {
        String summary = entry.summary().replace('\r', ' ').replace('\n', ' ').trim();
        return "JDM " + kind + " " + entry.operation() + ": " + summary
                + "\n\nJDM-History-Id: " + entry.id()
                + "\nJDM-Sequence: " + sequence
                + "\nJDM-Operation: " + entry.operation()
                + (entry.targetId() == null ? "" : "\nJDM-Target: " + entry.targetId());
    }

    private static byte[] readFileAtCommit(Repository repository, String commitId, String path) throws Exception {
        ObjectId objectId = repository.resolve(commitId + "^{commit}");
        if (objectId == null) throw new IOException("Missing history commit " + commitId);
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(objectId);
            return readFileAtTree(repository, commit.getTree().getId(), path);
        }
    }

    private static byte[] readFileAtTree(Repository repository, ObjectId treeId, String path) throws Exception {
        try (TreeWalk tree = TreeWalk.forPath(repository, path, treeId)) {
            if (tree == null) throw new IOException("History tree has no " + path);
            return repository.open(tree.getObjectId(0)).getBytes(MAX_OBJECT_BYTES);
        }
    }

    private static void writeAtomically(Path target, byte[] data) throws IOException {
        Path parent = target.getParent();
        if (parent == null) throw new IOException("History file has no parent: " + target);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, data, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException atomicMoveUnavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String prepareMetadataPath(String id) {
        return PREPARES_DIRECTORY + "/" + id + ".properties";
    }

    private static String prepareFolder(String id) {
        return PREPARES_DIRECTORY + "/" + id;
    }

    private static String eventPath(String id) {
        return EVENTS_DIRECTORY + "/" + id + ".properties";
    }

    @FunctionalInterface
    interface PrepareHook {
        void afterDurablePrepare(HistoryEntry entry, long sequence) throws Exception;
    }

    private record SnapshotFile(String path, byte[] bytes) {
    }

    private record PreparedTransaction(long sequence, HistoryEntry entry, ObjectId manifestTree) {
    }

    private record ManifestState(List<Checkpoint> completed,
                                 Map<String, PreparedTransaction> prepares,
                                 Set<String> completedIds,
                                 long highestSequence) {
        private static ManifestState empty() {
            return new ManifestState(List.of(), Map.of(), Set.of(), 0);
        }

        private List<PreparedTransaction> incompletePrepares() {
            List<PreparedTransaction> incomplete = new ArrayList<>();
            for (PreparedTransaction prepared : prepares.values()) {
                if (!completedIds.contains(prepared.entry().id())) incomplete.add(prepared);
            }
            incomplete.sort(Comparator.comparingLong(PreparedTransaction::sequence));
            return incomplete;
        }
    }
}
