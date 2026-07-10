package org.jdownloader.material.engine.history;

import java.util.concurrent.CompletableFuture;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ObservableList;

/**
 * An append-only local state history. Implementations must never rewrite or
 * delete revisions: Undo, redo, and restore are new revisions of their own.
 */
public interface HistoryService {

    ObservableList<HistoryEntry> entries();

    ReadOnlyObjectProperty<HistoryStatus> statusProperty();

    ReadOnlyBooleanProperty busyProperty();

    ReadOnlyBooleanProperty canUndoProperty();

    ReadOnlyBooleanProperty canRedoProperty();

    ReadOnlyLongProperty storageBytesProperty();

    CompletableFuture<Void> undo();

    CompletableFuture<Void> redo();

    CompletableFuture<Void> restore(String id);

    void record(HistoryScope scope, String summary, HistorySnapshot snapshot);

    void seedIfEmpty(String summary, HistorySnapshot snapshot);

    void shutdown();
}
