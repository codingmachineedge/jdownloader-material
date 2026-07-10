package org.jdownloader.material.engine.history;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Shared append-only undo/redo mechanics for persistent and in-memory stores. */
abstract class AbstractHistoryService implements HistoryService {

    protected static final class Checkpoint {
        private final long sequence;
        private final HistoryEntry entry;
        private final HistorySnapshot snapshot;
        private final String settingsCommit;
        private final String downloadListsCommit;

        protected Checkpoint(long sequence, HistoryEntry entry, HistorySnapshot snapshot,
                             String settingsCommit, String downloadListsCommit) {
            if (sequence < 1) throw new IllegalArgumentException("History sequence must be positive");
            this.sequence = sequence;
            this.entry = Objects.requireNonNull(entry, "entry");
            this.snapshot = snapshot;
            this.settingsCommit = settingsCommit;
            this.downloadListsCommit = downloadListsCommit;
        }

        protected long sequence() { return sequence; }
        protected HistoryEntry entry() { return entry; }
        protected HistorySnapshot snapshot() { return snapshot; }
        protected String settingsCommit() { return settingsCommit; }
        protected String downloadListsCommit() { return downloadListsCommit; }
    }

    private final Supplier<HistorySnapshot> snapshotSupplier;
    private final Consumer<HistorySnapshot> snapshotApplier;
    private final ExecutorService worker;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final AtomicBoolean taskFailed = new AtomicBoolean(false);
    /** FX restore callbacks awaiting a worker thread; close cancels each one explicitly. */
    private final Set<CompletableFuture<Void>> pendingApplies = ConcurrentHashMap.newKeySet();

    private final ObservableList<HistoryEntry> entries = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<HistoryStatus> status =
            new ReadOnlyObjectWrapper<>(this, "status", HistoryStatus.PENDING);
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", true);
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo", false);
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo", false);
    private final ReadOnlyLongWrapper storageBytes = new ReadOnlyLongWrapper(this, "storageBytes", 0);

    /** Worker-thread-only persistent timeline in chronological order. */
    private final List<Checkpoint> timeline = new ArrayList<>();
    /** Worker-thread-only failures that cannot be written to storage. */
    private final List<HistoryEntry> transientFailures = new ArrayList<>();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private boolean initialized;

    protected AbstractHistoryService(Supplier<HistorySnapshot> snapshotSupplier,
                                     Consumer<HistorySnapshot> snapshotApplier,
                                     String workerName) {
        this.snapshotSupplier = Objects.requireNonNull(snapshotSupplier, "snapshotSupplier");
        this.snapshotApplier = Objects.requireNonNull(snapshotApplier, "snapshotApplier");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, workerName);
            thread.setDaemon(true);
            return thread;
        };
        this.worker = Executors.newSingleThreadExecutor(factory);
    }

    /** Must be invoked by a concrete constructor after its fields have been set. */
    protected final void initialize() {
        submitTask(HistoryStatus.PENDING, () -> {
            timeline.clear();
            timeline.addAll(loadPersistentTimeline());
            requireStrictlyIncreasingSequences(timeline);
            initialized = true;
            rebuildStacks();
            publishTimeline();
        }, null);
    }

    @Override
    public final ObservableList<HistoryEntry> entries() {
        return entries;
    }

    @Override
    public final ReadOnlyObjectProperty<HistoryStatus> statusProperty() {
        return status.getReadOnlyProperty();
    }

    @Override
    public final ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    @Override
    public final ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    @Override
    public final ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    @Override
    public final ReadOnlyLongProperty storageBytesProperty() {
        return storageBytes.getReadOnlyProperty();
    }

    @Override
    public final void record(HistoryScope scope, String summary, HistorySnapshot snapshot) {
        HistoryEntry pending = newEntry(scope, HistoryOperation.CHANGE, summary, HistoryStatus.PENDING, null, null);
        HistorySnapshot captured = Objects.requireNonNull(snapshot, "snapshot");
        submitTask(HistoryStatus.RECORDING, () -> {
            ensureInitialized();
            append(pending.withStatus(HistoryStatus.COMMITTED), captured, true);
        }, error -> addFailure(pending, error));
    }

    /** Convenience for integrations that prefer this service to capture on the FX thread. */
    public final void recordCurrent(HistoryScope scope, String summary) {
        captureCurrent().whenComplete((snapshot, error) -> {
            if (error != null) addCaptureFailure(scope, summary, error);
            else record(scope, summary, snapshot);
        });
    }

    @Override
    public final void seedIfEmpty(String summary, HistorySnapshot snapshot) {
        HistoryEntry pending = newEntry(HistoryScope.SETTINGS, HistoryOperation.SEED, summary,
                HistoryStatus.PENDING, null, null);
        HistorySnapshot captured = Objects.requireNonNull(snapshot, "snapshot");
        submitTask(HistoryStatus.RECORDING, () -> {
            ensureInitialized();
            if (!timeline.isEmpty()) return;
            append(pending.withStatus(HistoryStatus.COMMITTED), captured, false);
        }, error -> addFailure(pending, error));
    }

    /** Convenience for a first-run seed captured on the JavaFX thread. */
    public final void seedCurrentIfEmpty(String summary) {
        captureCurrent().whenComplete((snapshot, error) -> {
            if (error != null) addCaptureFailure(HistoryScope.SETTINGS, summary, error);
            else seedIfEmpty(summary, snapshot);
        });
    }

    @Override
    public final CompletableFuture<Void> undo() {
        return submitTask(HistoryStatus.RESTORING, () -> {
            ensureInitialized();
            String targetId = undoStack.peek();
            if (targetId == null) return;
            Checkpoint target = find(targetId);
            HistorySnapshot before = snapshotBefore(target);
            applyOnFx(before).join();
            HistoryEntry restored = newEntry(target.entry().scope(), HistoryOperation.UNDO,
                    target.entry().summary(), HistoryStatus.RESTORED, targetId, null);
            append(restored, before, false);
            undoStack.pop();
            redoStack.push(targetId);
            publishTimeline();
        }, null);
    }

    @Override
    public final CompletableFuture<Void> redo() {
        return submitTask(HistoryStatus.RESTORING, () -> {
            ensureInitialized();
            String targetId = redoStack.peek();
            if (targetId == null) return;
            Checkpoint target = find(targetId);
            HistorySnapshot after = snapshotFor(target);
            applyOnFx(after).join();
            HistoryEntry restored = newEntry(target.entry().scope(), HistoryOperation.REDO,
                    target.entry().summary(), HistoryStatus.RESTORED, targetId, null);
            append(restored, after, false);
            redoStack.pop();
            undoStack.push(targetId);
            publishTimeline();
        }, null);
    }

    @Override
    public final CompletableFuture<Void> restore(String id) {
        if (id == null || id.isBlank()) return failedFuture(new IllegalArgumentException("History id must not be blank"));
        return submitTask(HistoryStatus.RESTORING, () -> {
            ensureInitialized();
            Checkpoint target = find(id);
            HistorySnapshot selected = snapshotFor(target);
            applyOnFx(selected).join();
            HistoryEntry restored = newEntry(target.entry().scope(), HistoryOperation.RESTORE,
                    target.entry().summary(), HistoryStatus.RESTORED, id, null);
            append(restored, selected, true);
        }, null);
    }

    @Override
    public final void shutdown() {
        if (!closed.compareAndSet(false, true)) return;
        for (CompletableFuture<Void> pending : pendingApplies) {
            pending.completeExceptionally(new IllegalStateException("History restore cancelled while closing"));
        }
        worker.shutdown();
        if (Platform.isFxApplicationThread()) {
            // A restore worker may be waiting to apply a snapshot on this same
            // FX thread. Keep process shutdown alive briefly without blocking
            // the UI thread and creating a close-time deadlock.
            Thread flush = new Thread(this::awaitCloseFlush, "history-close-flush");
            flush.setDaemon(false);
            flush.start();
        } else {
            awaitCloseFlush();
        }
        runOnFx(() -> {
            busy.set(false);
            status.set(HistoryStatus.CLOSED);
        });
    }

    protected abstract List<Checkpoint> loadPersistentTimeline() throws Exception;

    /** Persists one state revision without resetting, deleting, or garbage-collecting prior revisions. */
    protected abstract Checkpoint persist(HistoryEntry entry, HistorySnapshot snapshot) throws Exception;

    protected abstract HistorySnapshot readSnapshot(Checkpoint checkpoint) throws Exception;

    protected abstract long measureStorageBytes() throws Exception;

    private void append(HistoryEntry entry, HistorySnapshot snapshot, boolean clearRedo) throws Exception {
        Checkpoint checkpoint = persist(entry, snapshot);
        if (!timeline.isEmpty() && checkpoint.sequence() <= timeline.getLast().sequence()) {
            throw new IllegalStateException("History writer returned a non-monotonic sequence");
        }
        timeline.add(checkpoint);
        if (entry.operation() == HistoryOperation.CHANGE || entry.operation() == HistoryOperation.RESTORE) {
            undoStack.push(entry.id());
            if (clearRedo) redoStack.clear();
        }
        publishTimeline();
    }

    private void rebuildStacks() {
        undoStack.clear();
        redoStack.clear();
        for (Checkpoint checkpoint : timeline) {
            HistoryEntry entry = checkpoint.entry();
            if (entry.status() != HistoryStatus.COMMITTED && entry.status() != HistoryStatus.RESTORED) continue;
            switch (entry.operation()) {
                case CHANGE, RESTORE -> {
                    undoStack.push(entry.id());
                    redoStack.clear();
                }
                case UNDO -> {
                    if (entry.targetId() != null && remove(undoStack, entry.targetId())) {
                        redoStack.push(entry.targetId());
                    }
                }
                case REDO -> {
                    if (entry.targetId() != null && remove(redoStack, entry.targetId())) {
                        undoStack.push(entry.targetId());
                    }
                }
                case SEED -> {
                    // A seed establishes the initial durable state and is not undoable.
                }
            }
        }
    }

    private static boolean remove(Deque<String> values, String value) {
        return values.removeFirstOccurrence(value);
    }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("History service did not finish initialization");
    }

    private Checkpoint find(String id) {
        return timeline.stream()
                .filter(checkpoint -> checkpoint.entry().id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown history entry: " + id));
    }

    private HistorySnapshot snapshotBefore(Checkpoint target) throws Exception {
        int index = timeline.indexOf(target);
        for (int previous = index - 1; previous >= 0; previous--) {
            HistoryEntry entry = timeline.get(previous).entry();
            if (entry.status() == HistoryStatus.COMMITTED || entry.status() == HistoryStatus.RESTORED) {
                return snapshotFor(timeline.get(previous));
            }
        }
        throw new IllegalStateException("The initial history checkpoint cannot be undone");
    }

    private HistorySnapshot snapshotFor(Checkpoint checkpoint) throws Exception {
        HistorySnapshot inMemory = checkpoint.snapshot();
        return inMemory == null ? readSnapshot(checkpoint) : inMemory;
    }

    private CompletableFuture<HistorySnapshot> captureCurrent() {
        CompletableFuture<HistorySnapshot> future = new CompletableFuture<>();
        runOnFx(() -> {
            try {
                future.complete(Objects.requireNonNull(snapshotSupplier.get(), "snapshotSupplier returned null"));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private CompletableFuture<Void> applyOnFx(HistorySnapshot snapshot) {
        if (closed.get()) return failedFuture(new IllegalStateException("History service is closing"));
        CompletableFuture<Void> future = new CompletableFuture<>();
        pendingApplies.add(future);
        future.whenComplete((ignored, error) -> pendingApplies.remove(future));
        // The close path may race this worker just after the initial check.
        // Register first, then cancel immediately if it already won.
        if (closed.get()) {
            future.completeExceptionally(new IllegalStateException("History restore cancelled while closing"));
            return future;
        }
        runOnFx(() -> {
            // A close can happen after this restore was queued but before the
            // JavaFX thread gets to it. Never mutate a closing engine and then
            // falsely append an undo/redo/restore revision for that mutation.
            if (closed.get() || future.isDone()) {
                future.completeExceptionally(new IllegalStateException("History restore cancelled while closing"));
                return;
            }
            try {
                snapshotApplier.accept(snapshot);
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future;
    }

    private HistoryEntry newEntry(HistoryScope scope, HistoryOperation operation, String summary,
                                  HistoryStatus entryStatus, String targetId, String error) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(operation, "operation");
        String cleanSummary = summary == null || summary.isBlank()
                ? operation.name().charAt(0) + operation.name().substring(1).toLowerCase() + " " + scope.storageKey()
                : summary.trim();
        return new HistoryEntry(UUID.randomUUID().toString(), Instant.now(), scope, operation,
                cleanSummary, entryStatus, targetId, error);
    }

    private void addFailure(HistoryEntry pending, Throwable error) {
        HistoryEntry failed = new HistoryEntry(pending.id(), pending.timestamp(), pending.scope(),
                pending.operation(), pending.summary(), HistoryStatus.FAILED, pending.targetId(), errorMessage(error));
        transientFailures.add(failed);
        publishTimeline();
    }

    private void addCaptureFailure(HistoryScope scope, String summary, Throwable error) {
        HistoryEntry pending = newEntry(scope, HistoryOperation.CHANGE, summary, HistoryStatus.PENDING, null, null);
        submitTask(HistoryStatus.RECORDING, () -> {
            transientFailures.add(new HistoryEntry(pending.id(), pending.timestamp(), pending.scope(),
                    pending.operation(), pending.summary(), HistoryStatus.FAILED, null, errorMessage(error)));
            publishTimeline();
        }, null);
    }

    private CompletableFuture<Void> submitTask(HistoryStatus taskStatus, ThrowingRunnable task,
                                                Consumer<Throwable> failureHandler) {
        if (closed.get()) return failedFuture(new IllegalStateException("History service is closed"));
        CompletableFuture<Void> future = new CompletableFuture<>();
        queuedTasks.incrementAndGet();
        runOnFx(() -> {
            busy.set(true);
            status.set(taskStatus);
        });
        try {
            worker.execute(() -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable error) {
                    taskFailed.set(true);
                    if (failureHandler != null) failureHandler.accept(error);
                    future.completeExceptionally(error);
                } finally {
                    int remaining = queuedTasks.decrementAndGet();
                    runOnFx(() -> {
                        if (remaining == 0 && !closed.get()) {
                            busy.set(false);
                            status.set(taskFailed.getAndSet(false) ? HistoryStatus.FAILED : HistoryStatus.IDLE);
                        }
                    });
                }
            });
        } catch (Throwable error) {
            queuedTasks.decrementAndGet();
            future.completeExceptionally(error);
            runOnFx(() -> {
                busy.set(false);
                status.set(HistoryStatus.FAILED);
            });
        }
        return future;
    }

    private void publishTimeline() {
        long bytes;
        try {
            bytes = Math.max(0, measureStorageBytes());
        } catch (Exception ignored) {
            bytes = 0;
        }
        long measuredBytes = bytes;
        List<HistoryEntry> visible = new ArrayList<>();
        // The worker's persisted sequence is the authoritative timeline order.
        // Never use wall-clock timestamps or random IDs to reconstruct it.
        for (int index = transientFailures.size() - 1; index >= 0; index--) visible.add(transientFailures.get(index));
        for (int index = timeline.size() - 1; index >= 0; index--) visible.add(timeline.get(index).entry());
        List<HistoryEntry> immutableView = List.copyOf(visible);
        boolean undoAvailable = !undoStack.isEmpty();
        boolean redoAvailable = !redoStack.isEmpty();
        runOnFx(() -> {
            entries.setAll(immutableView);
            storageBytes.set(measuredBytes);
            canUndo.set(undoAvailable);
            canRedo.set(redoAvailable);
        });
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static CompletableFuture<Void> failedFuture(Throwable error) {
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(error);
        return failed;
    }

    protected static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitUnavailable) {
            // This fallback makes headless smoke tests usable. A running app always
            // routes mutations through JavaFX's event thread above.
            action.run();
        }
    }

    private void awaitCloseFlush() {
        try {
            // Existing queued commits are allowed to finish. When shutdown is
            // initiated on JavaFX, this work runs on a non-daemon helper so
            // the window closes immediately while the process keeps the
            // append-only revisions alive until they are durable. We never
            // interrupt, reset, or discard a revision merely to exit faster.
            while (!worker.awaitTermination(1, TimeUnit.SECONDS)) {
                // Keep waiting without tying up the JavaFX Application Thread.
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void requireStrictlyIncreasingSequences(List<Checkpoint> checkpoints) {
        long previous = 0;
        for (Checkpoint checkpoint : checkpoints) {
            if (checkpoint.sequence() <= previous) {
                throw new IllegalStateException("Persisted history sequence is not strictly increasing");
            }
            previous = checkpoint.sequence();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
