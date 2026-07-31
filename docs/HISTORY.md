# History Manager

The History Manager keeps a local, append-only timeline for the data that shapes direct-download
work: Downloads, LinkGrabber and non-secret Settings. A separate append-only workspace repository
records tabs and groups. Together they make everyday model/settings/navigation changes reversible
without interrupting active work.

## Interface

History uses the same global toolbar and primary navigation as the rest of the application. Global
search matches timeline operation, summary, scope, status, identifiers, and related entries. The
page heading shows local storage size plus Undo and Redo; a scope selector filters all changes,
Downloads, LinkGrabber, or Settings.

The bordered work panel is split roughly 44/56. The left timeline presents semantic scope/status
chips and timestamps. The right preview shows the selected event's summary, operation, scope, time,
status, related revision, storage message, and Restore action. Ready, busy, and error feedback stays
inline.

## Local-only storage

History lives below `~/.jdownloader-material/history/` in separate private Git repositories:

- **Download lists** holds Downloads and LinkGrabber together, so moving a link from staging into
  the queue is one coherent historical operation.
- **Settings** holds non-secret preferences such as destinations, connection limits, language, both
  funny levels, disclosure, reduced motion, dim-sum/quiet/notification controls, external-editor
  command, installed-JDownloader loopback URL, retry behavior and LinkGrabber flow.
- **Manifest** is a local coordinator. It first commits a durable prepare record with canonical
  copies of every snapshot file, then records completion after matching Download lists and Settings
  commits exist. On the next launch it can finish a prepared-but-interrupted change.

These repositories stay on the device and have no configured remote. Credential fields stay out of
history. Downloaded files, `.part` contents, and their bytes also stay out: history versions the
app's lists and settings, not user files. Direct-link URLs remain exactly as entered so a restored
list remains useful, including signed or authenticated URLs. Treat the history directory as private
device data.

`~/.jdownloader-material/workspace/` is another private JGit repository. It records the complete
current tab/group snapshot—page identities, titles, order, pinning, selected tab, group names/order/
membership/collapse/pinning/decorations and application name—after each structural action. The
appearance profile and notification records use bounded atomic files rather than Git; their
Settings switches still participate in the Settings timeline.

## Timeline, undo, redo, and restore

Each settled, user-meaningful operation creates a new entry. Entries cover link submission,
resolved LinkGrabber metadata, confirmation, removal, queued-item name/destination changes, package
ordering, terminal transfer outcomes, and settings changes.

Workspace commits cover open, close, select, move, pin, group create/update/remove, import and bulk
close. An unchanged structural snapshot writes no commit. Workspace import/export carries the
validated current structure, not the private Git object database.

Undo, Redo, and Restore apply a snapshot and append a new event. Earlier entries remain intact, so
an undo can itself be undone and a restored point stays visible alongside the newer path. Restore
changes only application model state. It safely stops active transfers before replacing
list/settings state and leaves completed downloads, `.part` files, and other user files intact.

## Granularity and responsiveness

The manager records settled durable transactions instead of transfer telemetry. One package
reorder, Apply action, LinkGrabber confirmation, or Settings import produces one entry even when it
changes several properties. Speed, byte-progress ticks, retry countdowns, slider drag ticks, and
individual text-field keystrokes are coalesced. A pending Settings revision is flushed during
normal close.

Snapshots are captured before background storage begins, and repository writes run asynchronously.
The History page reports saving and restore status inline while downloads and primary navigation
remain usable. The window closes without a prompt; a non-daemon background flusher finishes every
already accepted local revision.

## Storage and archive guidance

Append-only history is a local convenience and recovery layer. It consumes disk space as entries
accumulate and relies on the device's ordinary storage/backup care. Keep separate backups of
important downloaded files and use encrypted Settings backup plus workspace export for portable
configuration. The app retains its timeline records; ensure the device has adequate storage and a
suitable backup policy.
