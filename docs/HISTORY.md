# History Manager

The History Manager keeps a local, append-only timeline for the data that shapes the direct
download workspace: Downloads, LinkGrabber, and non-secret settings. It makes everyday
experimentation reversible without interrupting the active workflow.

## Local-only storage

History lives below `~/.jdownloader-material/history/` in separate private Git repositories:

- **Download lists** holds the Downloads queue and LinkGrabber staging list together, so moving
  a link from LinkGrabber into Downloads is one coherent historical operation.
- **Settings** holds non-secret preferences such as destinations, connection limits, appearance,
  language, retry behavior, and LinkGrabber flow.
- **Manifest** is a local coordinator repository. It first commits a durable prepare record with
  canonical copies of every snapshot file, then records completion after the matching Download
  lists and Settings commits exist. On the next launch it completes a prepared-but-interrupted
  change, so the visible timeline remains coherent.

These repositories stay on the device and have no configured remote. Credential fields stay out
of history. Downloaded files, `.part` contents, and their bytes also stay out: history versions
the app's lists and settings, rather than user files. Direct-link URLs remain exactly as entered
so restoring a list keeps it usable, including signed or authenticated URLs. Treat the history
directory as private device data.

## Timeline, undo, redo, and restore

Each settled, user-meaningful operation creates a new entry. Entries cover link submission,
resolved LinkGrabber metadata, confirmation, removal, queued-item name/destination changes,
package ordering, a completed/skip/final-failure state, and settings changes.

**Undo**, **Redo**, and **Restore** apply a selected snapshot and append a new event. They retain
the earlier entries, so an undo can be undone and a restored point remains visible alongside a
newer path. Restoring changes only application model state. It safely stops active transfers
before replacing list/settings state and leaves completed downloads, `.part` files, and other
user files intact.

## Workspace tab timeline

The browser-style workspace owns a second private repository at
`~/.jdownloader-material/workspace/`. It records the saved application name, open tabs, selected
tab, and each tab's title styling. Every tab has a durable `tabs/<id>.properties` descriptor, and
each open, select, edit, import, or close action adds an append-only commit. A closed tab's
descriptor remains with its close event, so no workspace event is lost from the repository.

Workspace export has two forms: a portable `.jdmtabs` snapshot for importing current tabs and a
ZIP of the complete local Git repository for preserving the entire workspace timeline. The
workspace timeline remains separate from the Downloads/Settings History Manager, allowing tab
state and download-model state to evolve independently.

## Granularity and responsiveness

The manager records settled durable transactions instead of transfer telemetry. A package
reorder, one Apply action, LinkGrabber confirmation, or Settings import produces one entry even
when it changes several model properties. Transfer speed, byte-progress ticks, retry countdowns,
slider drag ticks, and individual text-field keystrokes are coalesced. A pending Settings
revision is flushed during normal close.

Snapshots are captured before background storage begins, and repository writes run
asynchronously. The History page reports saving and restore status inline while downloads,
workspace navigation, and tab edits continue. The window closes without a prompt; a non-daemon
background flusher finishes every already accepted local revision.

## Storage and archive guidance

Append-only history is a local convenience and recovery layer. It consumes disk space as entries
accumulate and relies on the device's normal storage and backup care. Keep ordinary backups of
important downloaded files and use encrypted Settings backup for portable configuration. The app
retains its timeline records; ensure the device has adequate storage and a suitable backup policy.
