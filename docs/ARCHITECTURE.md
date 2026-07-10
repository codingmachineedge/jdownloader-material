# Architecture

JDownloader Material keeps the UI dependent on the DownloadEngine interface rather than on a
particular transfer backend. In a normal launch, the application selects DirectHttpEngine for real
direct HTTP(S) transfers. SimulatedEngine is selected only for deterministic screenshot-capture
paths, where reaching the network would make documentation unstable.

~~~text
app
  JDMaterialApp
    normal launch ------------------------> DirectHttpEngine
    screenshot capture ------------------> SimulatedEngine
                                             |
ui                                           v
  MainWindow / I18n ---------------> DownloadEngine (interface)
    DownloadsView                      Settings / SettingsIO
    LinkGrabberView                          |
    HistoryView                              +---------------------> AppStateStore (local journal)
    AddLinksView                             |                         GitHistoryService (embedded JGit)
    SettingsView                             |                           settings / download-lists / manifest
    StatusBar / NotificationCenter            v
          |                               model
          +---------------------------> DownloadItem -> DownloadPackage -> DownloadLink
                                          CrawledPackage -> CrawledLink
                                          DownloadState, LinkAvailability
~~~

The UI consumes observable packages, link properties, settings, and global statistics through
the interface. `I18n` reads the persisted presentation mode and renders English, playful Hong
Kong Cantonese, or both; the shell rebuilds its static labels immediately without restarting the
engine. The UI does not depend on DirectHttpEngine, SimulatedEngine, or JDownloader-core classes
directly.

## Normal direct-download path

For the normal application path, DirectHttpEngine provides a small but real direct-file pipeline:

1. Add Links and clipboard input submit direct HTTP(S) URLs without blocking the UI.
2. Background crawl workers parse the text and probe metadata using HEAD with a ranged GET
   fallback; LinkGrabber rows are created in small batches on the JavaFX Application Thread.
3. The UI receives availability, filename, and size updates on the JavaFX Application Thread.
   Auto-confirm occurs after the probe if requested; auto-start applies to those auto-confirmed
   results.
4. An AnimationTimer scheduler admits queued links according to the global
   simultaneous-download and per-host connection limits.
5. Transfer workers stream HTTP responses to target-name .part files, attempt Range resumption,
   and atomically move completed files into place when the filesystem supports atomic moves. The
   resolved path is retained for nonblocking file-manager actions.
6. The scheduler and worker state update observable model properties; tree-table cells and the
   status bar react through bindings instead of polling or modal dialogs.

When Network recovery is enabled, transient network/HTTP failures move the link back to Queue with
a bounded exponential retry deadline. The scheduler renders that countdown inline and reuses the
same partial file; permanent failures remain visible for an explicit user retry.

If the server does not honor a range request, the engine restarts the partial stream. File
collisions are resolved by the configured policy on the worker. In particular, the default Ask
policy safely auto-renames instead of placing an acknowledgement dialog in the download path.

## Local state recovery

DirectHttpEngine owns AppStateStore, a debounced best-effort journal at
~/.jdownloader-material/state.properties. The state writer snapshots non-secret settings, the
Downloads queue, and LinkGrabber packages on the JavaFX thread, then writes it on a background
worker using a temporary file and atomic replacement where available.

At startup the journal is read asynchronously and applied on the JavaFX thread. Formerly running
or paused links are restored as queued because a live HTTP stream cannot survive process exit;
their remaining .part file is available for the next direct transfer to resume. My.JDownloader
credentials are deliberately omitted from this ordinary journal. The encrypted Backup page is
the route for carrying those optional credentials between machines.

## Local append-only history

Alongside the recovery journal, DirectHttpEngine owns GitHistoryService. It uses bundled JGit,
not a system Git executable, and creates three private repositories below
`~/.jdownloader-material/history/`:

- `settings` stores the canonical non-secret settings snapshot;
- `download-lists` stores separate canonical Downloads and LinkGrabber snapshots; and
- `manifest` stores immutable prepare and completion records that link each visible timeline entry
  to the matching commits in the first two repositories.

Every semantic history event snapshots all three state files, even though its scope identifies
whether Settings, Downloads, LinkGrabber, or a combined list operation caused it. The manifest is
the cross-repository timeline: it commits canonical snapshot copies in a durable prepare record,
then records the event ID, worker-assigned sequence, timestamp, operation, summary, and both
snapshot commit IDs as completion. Startup idempotently completes a prepared record after a crash
between repositories. The service configures its repositories for append-only use and never
invokes reset, rebase, remove, pruning, or garbage collection.

Undo, redo, and restore load a selected immutable snapshot, apply it to the JavaFX model, and
append a new history event. They never rewrite or delete the older event. A newer change after a
restore therefore remains an alternate path in the timeline rather than erasing the restored one.
Restoring stops active transfers and replaces only in-memory list/settings state; completed files
and resumable `.part` data are not deleted or versioned.

History snapshots remove My.JDownloader credentials before a Git object is created. Direct-link
URLs remain intact so a restored list stays usable, so the local-only history directory must be
treated as private device data. History snapshots omit active-transfer telemetry such as byte
progress, speed, retry timing, and live details, while retaining final byte/path/outcome metadata
for finished rows and final error details for error rows. The persisted queue intent, LinkGrabber
content, and non-secret settings remain sufficient for reversible user-facing list/settings
operations without recording a commit for every transfer tick.

## Nonblocking work and UI state

The model uses JavaFX observable properties. Views bind table cells and labels directly, so
progress, speed, availability, and state changes do not need a manual refresh.

- Network probes, HTTP streaming, state writes, backup encryption, and backup file I/O run on
  background workers.
- Model mutations that JavaFX observes are marshalled back with Platform.runLater.
- Link submission is deferred: the inline Add Links composer returns immediately while probing
  proceeds, then auto-confirm / auto-start can continue without a dialog.
- A single queued/error/disabled Downloads link can be renamed or retargeted in an inline strip;
  a package needs every child in those safe states. Active and finalized rows stay read-only rather
  than risking a live stream or existing file.
- Transfers expose progress, failure detail, and pause state in their rows and the status bar.
  A compact snackbar is reserved for a navigable or reversible UI result, not normal workflow.
- Settings backup snapshots/applies JavaFX properties on the UI thread while encryption and disk
  work stay in a JavaFX Task.
- History snapshots are captured and restored on the JavaFX Application Thread. JGit repository
  loading, commit creation, storage measurement, and snapshot reads run through one dedicated
  history worker; HistoryView observes its entries and asynchronous status without blocking input.
  On normal close, a non-daemon flusher lets already accepted append-only writes finish without
  keeping the JavaFX window open.

## SimulatedEngine and future JDownloader integration

SimulatedEngine is retained to seed reliable documentation screenshots with no live network
traffic. It is not the engine normal users run and it is not evidence of a JDownloader-core integration.

DirectHttpEngine also is not JDownloader core. It handles only ordinary direct HTTP(S) files.
JDownloader plugins, hoster-specific logic, container formats, accounts, CAPTCHA, remote
control, and full upstream parity remain future work. A future adapter can map the same
[DownloadEngine](ENGINE_API.md) contract onto JDownloader core classes, provided it preserves
the JavaFX-thread boundary described above.
