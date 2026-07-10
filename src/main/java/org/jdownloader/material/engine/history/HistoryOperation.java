package org.jdownloader.material.engine.history;

/** The append-only operation represented by one history entry. */
public enum HistoryOperation {
    /** The first durable snapshot for an otherwise empty profile. */
    SEED,
    /** A normal user- or engine-originated state change. */
    CHANGE,
    /** A new revision that restores the state before an earlier action. */
    UNDO,
    /** A new revision that restores an action which was previously undone. */
    REDO,
    /** A new revision restored from a user-selected point in the timeline. */
    RESTORE
}
