# Architecture

JDownloader Material keeps the UI dependent on the DownloadEngine interface rather than on a
particular transfer backend. In a normal launch, the application selects DirectHttpEngine for real
direct HTTP(S) transfers. SimulatedEngine is selected only for deterministic screenshot/demo
paths, where reaching the network would make documentation unstable.

~~~text
app
  JDMaterialApp
    normal launch ------------------------> DirectHttpEngine
    screenshot/demo capture -------------> SimulatedEngine
                                             |
ui                                           v
  MainWindow ----------------------> DownloadEngine (interface)
    DownloadsView                      Settings / SettingsIO
    LinkGrabberView                          |
    AddLinksView                             v
    SettingsView                    AppStateStore (local journal)
    StatusBar / NotificationCenter            |
          |                                   v
          +-------------------------------> model
                                      DownloadItem -> DownloadPackage -> DownloadLink
                                      CrawledPackage -> CrawledLink
                                      DownloadState, LinkAvailability
~~~

The UI consumes observable packages, link properties, settings, and global statistics through
the interface. It does not depend on DirectHttpEngine, SimulatedEngine, or JDownloader-core
classes directly.

## Normal direct-download path

For the normal application path, DirectHttpEngine provides a small but real direct-file pipeline:

1. Add Links and clipboard input submit direct HTTP(S) URLs without blocking the UI.
2. Background crawl workers parse the text, create LinkGrabber rows, and probe metadata using
   HEAD with a ranged GET fallback.
3. The UI receives availability, filename, and size updates on the JavaFX Application Thread.
   Auto-confirm occurs after the probe if requested; auto-start applies to those auto-confirmed
   results.
4. An AnimationTimer scheduler admits queued links according to the global
   simultaneous-download and per-host connection limits.
5. Transfer workers stream HTTP responses to target-name .part files, attempt Range resumption,
   and atomically move completed files into place when the filesystem supports atomic moves.
6. The scheduler and worker state update observable model properties; tree-table cells and the
   status bar react through bindings instead of polling or modal dialogs.

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

## Nonblocking work and UI state

The model uses JavaFX observable properties. Views bind table cells and labels directly, so
progress, speed, availability, and state changes do not need a manual refresh.

- Network probes, HTTP streaming, state writes, backup encryption, and backup file I/O run on
  background workers.
- Model mutations that JavaFX observes are marshalled back with Platform.runLater.
- Link submission is deferred: the inline Add Links composer returns immediately while probing
  proceeds, then auto-confirm / auto-start can continue without a dialog.
- Transfers expose progress, failure detail, and pause state in their rows and the status bar.
  A compact snackbar is reserved for a navigable or reversible UI result, not normal workflow.
- Settings backup snapshots/applies JavaFX properties on the UI thread while encryption and disk
  work stay in a JavaFX Task.

## SimulatedEngine and future JDownloader integration

SimulatedEngine is retained to seed reliable screenshots and demos with no live network traffic.
It is not the engine normal users run and it is not evidence of a JDownloader-core integration.

DirectHttpEngine also is not JDownloader core. It handles only ordinary direct HTTP(S) files.
JDownloader plugins, hoster-specific logic, container formats, accounts, CAPTCHA, remote
control, and full upstream parity remain future work. A future adapter can map the same
[DownloadEngine](ENGINE_API.md) contract onto JDownloader core classes, provided it preserves
the JavaFX-thread boundary described above.
