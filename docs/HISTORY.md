# History Manager

The History Manager keeps a local, append-only timeline for the data that shapes the
application: the Downloads/LinkGrabber lists and non-secret settings. It is designed to
make ordinary experimentation reversible without putting a dialog in the way.

## Local-only storage

History lives below `~/.jdownloader-material/history/` in separate local Git repositories:

- **Download lists** holds the Downloads queue and LinkGrabber staging list together, so moving
  a link from LinkGrabber into Downloads is one coherent historical operation.
- **Settings** holds non-secret preferences such as destinations, connection limits, appearance,
  language, and LinkGrabber behavior.
- **Manifest** is a private coordinator repository. It first commits a durable prepare record with
  canonical copies of all three snapshot files, then records completion only after the matching
  Download lists and Settings commits exist. On the next launch it idempotently finishes any
  prepared-but-incomplete change, so the visible timeline never presents a half-written
  cross-repository change.

The repositories are local to the device. They are not pushed to GitHub or any remote service.
My.JDownloader credentials are excluded from history; use the encrypted Settings backup when
credentials must be moved between machines. Downloaded files, `.part` files, and their contents
are also excluded: history versions the app's lists and settings, not the files on disk.
Direct-link URLs are retained exactly so restoring a list remains usable, including signed or
authenticated URLs. That makes the history directory part of the device's private data: it has no
remote configured and is never uploaded, but do not copy it to an untrusted location.

## Timeline, undo, redo, and restore

Each completed semantic operation creates a new entry. Examples include adding links, resolved
LinkGrabber probe metadata, confirming or removing list entries, changing a queued item's name or
destination, reordering packages, the completion/skip/final failure of a download, and applying a
settings change.

**Undo**, **Redo**, and **Restore** do not rewrite or erase prior entries. They apply the requested
snapshot and append a new entry that records that action. That means an undo can itself be undone,
and a restored point remains visible even after a newer path is created. The timeline can show
that newer path as an alternate branch rather than discarding it.

Restoring history changes the application model only. It never deletes, overwrites, or rolls back
a completed download, a `.part` file, or any other user file. A finished row retains its final
byte count, resolved path, and outcome detail; an error row retains its final reason. Active
transfers are safely stopped before their list state is replaced, and a restored queued item
follows the normal download rules.

## Granularity and responsiveness

The manager records every settled, user-meaningful durable transaction rather than noisy
telemetry. A package reorder, one Apply action, a LinkGrabber confirmation, or a Settings import
is one entry—even when it changes several model properties. Transfer speed, byte-progress ticks,
retry countdowns, slider drag ticks, and individual text-field keystrokes are coalesced instead of
generating a stream of nearly identical commits. A pending Settings revision is flushed on normal
app close. The window closes without a prompt while a non-daemon background flusher lets every
already accepted local revision finish before the process exits.

Snapshots are captured before background storage begins, and repository writes run asynchronously.
The History view reports saving or restore status inline, so browsing, downloads, and navigation do
not wait for a history write or a confirmation dialog.

## Storage and archive caveat

Append-only history is a local convenience and recovery layer, not a disaster-recovery archive.
It uses disk space as entries accumulate and is not protected against drive failure, operating-system
cleanup, manual deletion, or another program with access to the history folder. Keep normal backups
of important downloaded files and use the encrypted Settings backup for portable, credential-bearing
configuration. No history entry is automatically deleted by the application, but users should still
ensure the device has adequate storage and its own backup policy.
