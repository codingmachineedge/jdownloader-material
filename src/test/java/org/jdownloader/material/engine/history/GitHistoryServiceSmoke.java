package org.jdownloader.material.engine.history;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.StringReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Manual smoke check for append-only JGit history without a system Git executable. */
public final class GitHistoryServiceSmoke {

    private GitHistoryServiceSmoke() {
    }

    public static void main(String[] args) throws Exception {
        verifyVolatileQueueFieldsAreExcluded();
        verifyTerminalMetadataIsRetained();
        Path root = Files.createTempDirectory("jdm-history-smoke-");
        AtomicReference<HistorySnapshot> current = new AtomicReference<>();
        HistorySnapshot initial = snapshot("initial", "queue-0", "grabber-0");
        HistorySnapshot changed = snapshot("changed", "queue-1", "grabber-1");
        current.set(initial);

        GitHistoryService history = new GitHistoryService(root, current::get, current::set);
        awaitIdle(history);
        history.seedIfEmpty("Initial state", initial);
        awaitIdle(history);
        history.record(HistoryScope.SETTINGS, "Changed a setting", changed);
        awaitIdle(history);
        require(history.entries().size() == 2, "Two append-only entries were not recorded");
        require(history.canUndoProperty().get(), "A normal change was not undoable");
        require(Files.isDirectory(root.resolve("settings/.git")), "Settings repository was not created");
        require(Files.isDirectory(root.resolve("download-lists/.git")), "List repository was not created");
        require(Files.isDirectory(root.resolve("manifest/.git")), "Manifest repository was not created");
        String storedSettings = Files.readString(root.resolve("settings").resolve(HistorySnapshot.SETTINGS_FILE),
                StandardCharsets.UTF_8);
        require(!storedSettings.contains("myjdEmail") && !storedSettings.contains("myjdPassword"),
                "My.JDownloader credentials entered history storage");
        String storedDownloads = Files.readString(root.resolve("download-lists")
                .resolve(HistorySnapshot.DOWNLOADS_FILE), StandardCharsets.UTF_8);
        Properties restoredDownloads = new Properties();
        restoredDownloads.load(new StringReader(storedDownloads));
        require("https://alice:secret@example.test/file.bin?token=temporary-token#private-fragment".equals(
                        restoredDownloads.getProperty("queue.package.0.link.0.url")),
                "Direct-link URL was not retained exactly for history restore");

        history.undo().get(10, TimeUnit.SECONDS);
        awaitIdle(history);
        require(current.get().downloadsText().contains("queue-0"), "Undo did not apply the prior snapshot");
        require(history.canRedoProperty().get(), "Undo did not make redo available");

        history.redo().get(10, TimeUnit.SECONDS);
        awaitIdle(history);
        require(current.get().downloadsText().contains("queue-1"), "Redo did not restore the target snapshot");
        String seedId = history.entries().stream()
                .filter(entry -> entry.operation() == HistoryOperation.SEED)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed entry is missing"))
                .id();
        history.restore(seedId).get(10, TimeUnit.SECONDS);
        awaitIdle(history);
        require(current.get().downloadsText().contains("queue-0"), "Restore did not apply a selected revision");
        require(history.entries().stream().anyMatch(entry -> entry.operation() == HistoryOperation.UNDO),
                "Undo was not itself recorded as a durable revision");
        require(history.entries().stream().anyMatch(entry -> entry.operation() == HistoryOperation.REDO),
                "Redo was not itself recorded as a durable revision");
        List<ManifestEvent> originalEvents = manifestEvents(root);
        assertStrictSequences(originalEvents);
        assertTimelineOrder(history, originalEvents);
        history.shutdown();

        // Re-opening reconstructs a standard redo/undo stack from the immutable manifest.
        GitHistoryService reopened = new GitHistoryService(root, current::get, current::set);
        awaitIdle(reopened);
        require(reopened.entries().size() >= 5, "Manifest events were not restored after restart");
        assertTimelineOrder(reopened, manifestEvents(root));
        reopened.shutdown();
        verifyPreparedTransactionRecovery();
        verifyShutdownFlushesQueuedRecord();
        System.out.println("Git history smoke check passed: " + root);
    }

    /** A normal close must preserve a record that was queued just before it. */
    private static void verifyShutdownFlushesQueuedRecord() throws Exception {
        Path root = Files.createTempDirectory("jdm-history-close-");
        AtomicReference<HistorySnapshot> current = new AtomicReference<>();
        HistorySnapshot initial = snapshot("close-initial", "queue-close-0", "grabber-close-0");
        HistorySnapshot changed = snapshot("close-changed", "queue-close-1", "grabber-close-1");
        current.set(initial);

        GitHistoryService closing = new GitHistoryService(root, current::get, current::set);
        awaitIdle(closing);
        closing.seedIfEmpty("Close seed", initial);
        awaitIdle(closing);
        closing.record(HistoryScope.SETTINGS, "Queued just before close", changed);
        // Do not await idle: shutdown itself must finish the already accepted
        // append-only write before this local profile can disappear.
        closing.shutdown();

        GitHistoryService reopened = new GitHistoryService(root, current::get, current::set);
        awaitIdle(reopened);
        require(reopened.entries().stream().anyMatch(entry -> "Queued just before close".equals(entry.summary())),
                "Shutdown did not flush the queued history revision");
        reopened.shutdown();
    }

    /** Simulates a process crash after the manifest prepare commit and verifies startup repair. */
    private static void verifyPreparedTransactionRecovery() throws Exception {
        Path root = Files.createTempDirectory("jdm-history-recovery-");
        AtomicReference<HistorySnapshot> current = new AtomicReference<>();
        HistorySnapshot initial = snapshot("initial", "queue-initial", "grabber-initial");
        HistorySnapshot recoveredSnapshot = snapshot("recovered", "queue-recovered", "grabber-recovered");
        current.set(initial);

        GitHistoryService seed = new GitHistoryService(root, current::get, current::set);
        awaitIdle(seed);
        seed.seedIfEmpty("Recovery seed", initial);
        awaitIdle(seed);
        seed.shutdown();

        GitHistoryService interrupted = new GitHistoryService(root, current::get, current::set,
                (entry, sequence) -> {
                    throw new IOException("Simulated process loss after durable prepare");
                });
        awaitIdle(interrupted);
        interrupted.record(HistoryScope.DOWNLOADS, "Crash-recoverable queue change", recoveredSnapshot);
        awaitSettled(interrupted);
        require(interrupted.statusProperty().get() == HistoryStatus.FAILED,
                "Prepare hook did not fail the interrupted transaction");
        require(manifestEvents(root).size() == 1,
                "A completion event was written after the simulated crash point");
        require(Files.isDirectory(root.resolve("manifest/prepares")), "Durable prepare directory is missing");
        interrupted.shutdown();

        GitHistoryService repaired = new GitHistoryService(root, current::get, current::set);
        awaitIdle(repaired);
        List<ManifestEvent> repairedEvents = manifestEvents(root);
        require(repairedEvents.size() == 2, "Startup did not complete the prepared transaction");
        assertStrictSequences(repairedEvents);
        assertTimelineOrder(repaired, repairedEvents);
        ManifestEvent repairedEvent = repairedEvents.stream()
                .filter(event -> "Crash-recoverable queue change".equals(event.summary()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Repaired completion event is missing"));
        repaired.restore(repairedEvent.id()).get(10, TimeUnit.SECONDS);
        awaitIdle(repaired);
        require(current.get().downloadsText().contains("queue-recovered"),
                "Recovered event did not preserve its prepared snapshot data");
        repaired.shutdown();

        // Repair is idempotent: a second open must not append another completion.
        GitHistoryService reopened = new GitHistoryService(root, current::get, current::set);
        awaitIdle(reopened);
        require(manifestEvents(root).size() == 3,
                "Reopening after a completed repair changed the persisted event count unexpectedly");
        // The third event is the explicit restore above; no duplicate repair was appended.
        assertTimelineOrder(reopened, manifestEvents(root));
        reopened.shutdown();
    }

    private static void verifyVolatileQueueFieldsAreExcluded() {
        Properties state = new Properties();
        state.setProperty("queue.package.0.link.0.name", "example.bin");
        state.setProperty("queue.package.0.link.0.url", "https://example.test/example.bin");
        state.setProperty("queue.package.0.link.0.loaded", "123456");
        state.setProperty("queue.package.0.link.0.outputPath", "C:/old/example.bin");
        state.setProperty("queue.package.0.link.0.detail", "Old transfer detail");
        state.setProperty("queue.package.0.link.0.retryAt", "999999999");
        HistorySnapshot snapshot = HistorySnapshot.fromState(state);
        String queue = snapshot.downloadsText();
        require(queue.contains(".name=example.bin") && queue.contains(".url=https\\://example.test/example.bin"),
                "Structural queue fields were removed from history");
        require(!queue.contains(".loaded") && !queue.contains(".outputPath")
                        && !queue.contains(".detail") && !queue.contains(".retryAt"),
                "Volatile transfer telemetry entered history storage");
    }

    private static void verifyTerminalMetadataIsRetained() {
        Properties state = new Properties();
        String prefix = "queue.package.0.link.0.";
        state.setProperty(prefix + "state", "FINISHED");
        state.setProperty(prefix + "loaded", "8192");
        state.setProperty(prefix + "outputPath", "C:/Downloads/finished.bin");
        state.setProperty(prefix + "detail", "Kept the existing file");
        HistorySnapshot snapshot = HistorySnapshot.fromState(state);
        Properties queue = snapshot.downloadsProperties();
        require("8192".equals(queue.getProperty(prefix + "loaded"))
                        && "C:/Downloads/finished.bin".equals(queue.getProperty(prefix + "outputPath"))
                        && "Kept the existing file".equals(queue.getProperty(prefix + "detail")),
                "Finished-row metadata was not retained for a usable history restore");

        Properties failedState = new Properties();
        failedState.setProperty(prefix + "state", "ERROR");
        failedState.setProperty(prefix + "detail", "The server rejected this direct link");
        HistorySnapshot failedSnapshot = HistorySnapshot.fromState(failedState);
        require("The server rejected this direct link".equals(
                        failedSnapshot.downloadsProperties().getProperty(prefix + "detail")),
                "Final error detail was not retained for a useful history restore");
    }

    private static HistorySnapshot snapshot(String value, String queue, String grabber) {
        Properties settings = new Properties();
        settings.setProperty("appearance", value);
        settings.setProperty("myjdEmail", "private@example.test");
        settings.setProperty("myjdPassword", "do-not-store");
        Properties downloads = new Properties();
        downloads.setProperty("queue.item", queue);
        downloads.setProperty("queue.package.0.link.0.url",
                "https://alice:secret@example.test/file.bin?token=temporary-token#private-fragment");
        Properties linkGrabber = new Properties();
        linkGrabber.setProperty("linkgrabber.item", grabber);
        return HistorySnapshot.fromProperties(settings, downloads, linkGrabber);
    }

    private static void awaitIdle(HistoryService history) throws InterruptedException {
        awaitSettled(history);
        if (history.statusProperty().get() == HistoryStatus.FAILED) {
            throw new IllegalStateException("History worker failed");
        }
    }

    private static void awaitSettled(HistoryService history) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (history.busyProperty().get() && System.nanoTime() < deadline) Thread.sleep(10);
        if (history.busyProperty().get()) throw new IllegalStateException("History worker did not become idle");
    }

    private static List<ManifestEvent> manifestEvents(Path root) throws IOException {
        Path eventsDirectory = root.resolve("manifest/events");
        if (!Files.isDirectory(eventsDirectory)) return List.of();
        List<ManifestEvent> events = new ArrayList<>();
        try (var paths = Files.list(eventsDirectory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".properties")).toList()) {
                Properties values = new Properties();
                try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    values.load(reader);
                }
                events.add(new ManifestEvent(values.getProperty("id"),
                        Long.parseLong(values.getProperty("sequence")), values.getProperty("summary")));
            }
        }
        events.sort(Comparator.comparingLong(ManifestEvent::sequence));
        return List.copyOf(events);
    }

    private static void assertStrictSequences(List<ManifestEvent> events) {
        require(!events.isEmpty(), "Manifest has no completion events");
        long expected = 1;
        for (ManifestEvent event : events) {
            require(event.sequence() == expected++, "Manifest sequences are not monotonic and contiguous");
        }
    }

    private static void assertTimelineOrder(HistoryService history, List<ManifestEvent> events) {
        Map<String, Long> sequences = events.stream().collect(java.util.stream.Collectors.toMap(
                ManifestEvent::id, ManifestEvent::sequence));
        List<String> expectedNewestFirst = events.stream()
                .sorted(Comparator.comparingLong(ManifestEvent::sequence).reversed())
                .map(ManifestEvent::id)
                .toList();
        List<String> actual = history.entries().stream()
                .map(HistoryEntry::id)
                .filter(sequences::containsKey)
                .toList();
        require(expectedNewestFirst.equals(actual),
                "Restarted timeline was not reconstructed from persisted sequence order");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private record ManifestEvent(String id, long sequence, String summary) {
    }
}
