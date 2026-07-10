package org.jdownloader.material.engine.history;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lightweight append-only history for demo/screenshot engines. It has the same
 * undo, redo, restore, and JavaFX-callback contract as {@link GitHistoryService}
 * without creating a profile directory or depending on a persistent Git store.
 */
public final class MemoryHistoryService extends AbstractHistoryService {

    private long bytes;
    private long nextSequence = 1;

    public MemoryHistoryService(Supplier<HistorySnapshot> snapshotSupplier,
                                Consumer<HistorySnapshot> snapshotApplier) {
        super(Objects.requireNonNull(snapshotSupplier, "snapshotSupplier"),
                Objects.requireNonNull(snapshotApplier, "snapshotApplier"), "history-memory-writer");
        initialize();
    }

    @Override
    protected List<Checkpoint> loadPersistentTimeline() {
        bytes = 0;
        nextSequence = 1;
        return List.of();
    }

    @Override
    protected Checkpoint persist(HistoryEntry entry, HistorySnapshot snapshot) {
        bytes += snapshot.byteCount();
        return new Checkpoint(nextSequence++, entry, snapshot, null, null);
    }

    @Override
    protected HistorySnapshot readSnapshot(Checkpoint checkpoint) {
        HistorySnapshot snapshot = checkpoint.snapshot();
        if (snapshot == null) throw new IllegalStateException("In-memory history snapshot is unavailable");
        return snapshot;
    }

    @Override
    protected long measureStorageBytes() {
        return bytes;
    }
}
