# Architecture

JDownloader Material separates direct HTTP(S) transfer work, persistent local state, workspace
tabs, and JavaFX presentation. Normal launches select `DirectHttpEngine` for real transfers.
`SimulatedEngine` is selected only by documentation capture so the gallery is repeatable without
network traffic.

~~~text
app
  JDMaterialApp
    normal launch ------------------------> DirectHttpEngine
    documentation capture ---------------> SimulatedEngine

ui
  MainWindow / I18n / ThemeManager
    workspace tabs ----------------------> GitWorkspaceStore (embedded JGit)
      DownloadsView                         workspace.properties / tabs / events
      LinkGrabberView
      HistoryView
      AddLinksView
      SettingsView
      StatusBar / ActivityStatus
          |
          +------------------------------> DownloadEngine
                                             |
                                             +--> AppStateStore (restart journal)
                                             +--> GitHistoryService (embedded JGit)
                                             |      settings / download-lists / manifest
                                             +--> model
                                                    DownloadPackage -> DownloadLink
                                                    CrawledPackage -> CrawledLink
                                                    DownloadState / LinkAvailability
~~~

Views consume observable packages, links, settings, and global statistics through
`DownloadEngine`. `I18n` renders English, playful Hong Kong Cantonese, or both. Changing language
rebuilds static shell labels immediately while the engine remains running.

## Direct-download path

1. Add Links and clipboard input submit direct HTTP(S) URLs without blocking the workspace.
2. Background crawl workers parse text and probe metadata with HEAD plus ranged-GET fallback.
   LinkGrabber rows are created in small batches on the JavaFX Application Thread.
3. The UI receives availability, filename, and size updates through observable properties.
   Auto-confirm and auto-start continue after probe completion when selected.
4. An `AnimationTimer` scheduler admits queued links according to global simultaneous-download
   and per-host limits.
5. Transfer workers stream HTTP responses to target-name `.part` files, attempt Range resumption,
   and move completed data into place atomically where supported. The resolved path is retained
   for file-manager actions.
6. Scheduler and worker state update observable model properties. Tree-table cells and the status
   line react through bindings rather than polling or modal dialogs.

With Network recovery enabled, transient network/HTTP failures return a link to Queue with a
bounded exponential retry deadline. The scheduler renders the countdown inline and reuses the
same partial file. A server that declines a range request causes a safe partial-stream restart.
Collision policy also runs on the worker: the default Ask policy chooses a collision-safe name
instead of interrupting a batch.

## Restart state

`DirectHttpEngine` owns `AppStateStore`, a debounced local journal at
`~/.jdownloader-material/state.properties`. The writer captures supported settings, the Downloads
queue, and LinkGrabber packages on the JavaFX thread, then writes a temporary file and replaces
the journal atomically where possible.

Startup reads the journal asynchronously and applies it on the JavaFX thread. Links that were
running or paused at exit return as queued because an HTTP stream ends with the process; retained
`.part` files are available for the next direct transfer.

## Append-only History Manager

`GitHistoryService` keeps three private embedded-Git repositories under
`~/.jdownloader-material/history/`:

- `settings` stores canonical non-secret settings snapshots;
- `download-lists` stores Downloads and LinkGrabber snapshots; and
- `manifest` stores immutable prepare and completion records that link matching commits.

Every settled semantic history event snapshots all durable list/settings state. The manifest
writes the durable prepare before completing its cross-repository record, allowing startup to
finish an interrupted save coherently. Undo, redo, and restore load an immutable snapshot into the
JavaFX model and append a corresponding history event. Earlier records remain intact, including
the state before an undo.

History snapshots reject credential fields before any Git object is made. Direct-link URLs remain
so a restored list continues to work, making the local history folder private device data. The
repositories do not contain completed download contents or `.part` contents. Active telemetry
such as byte progress, speed, retry timing, and live details stays outside snapshots; final
outcome metadata remains with finished and error rows.

## Browser-style workspace storage

`GitWorkspaceStore` owns a separate private repository at
`~/.jdownloader-material/workspace/`. It stores the saved application name, current open tabs,
selected tab, and title styling in `workspace.properties`; each tab also owns
`tabs/<id>.properties`. Immutable event records describe opening, selection, editing, import, and
closing. A close event retains the tab descriptor, so the exported repository includes every tab
that was part of the workspace timeline.

The store runs one embedded-Git writer. Every workspace mutation creates a commit. Portable
snapshot export/import uses `.jdmtabs`, while repository export writes a ZIP of the full private
repository. These operations run off the JavaFX thread and publish a new snapshot back to the
shell when complete.

## Responsive UI work

- Network probes, HTTP streaming, journal writes, encrypted backup work, History Git storage,
  workspace Git storage, and file I/O run on background workers.
- Model mutations observed by JavaFX return through `Platform.runLater`.
- Add Links submits first and lets background probe/confirmation/start work continue while the
  user moves to another tab.
- Download item edits appear as inline fields only in safe queue states; active streams and
  completed paths stay stable.
- Clipboard capture, validation, and removal results use `ActivityStatus` in the fixed status
  line. Durable reversal lives in the History page.
- A non-daemon close flusher completes already accepted append-only writes without keeping the
  window open for an interaction prompt.

## Documentation capture

`SimulatedEngine` seeds stable sample rows and progress only when the capture environment is
enabled. It writes the visual gallery and exits, leaving the normal direct-download path unchanged.
