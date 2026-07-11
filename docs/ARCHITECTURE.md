# Architecture

JDownloader Material separates direct HTTP(S) transfer work, persistent local state, append-only
history, localization/theme state, and JavaFX presentation. Normal launches select
`DirectHttpEngine`; deterministic documentation capture selects `SimulatedEngine` so the gallery
does not depend on live network traffic.

~~~text
app
  JDMaterialApp
    normal launch ------------------------> DirectHttpEngine
    documentation capture ---------------> SimulatedEngine

ui
  MainWindow
    52 px global toolbar
      Add Links / Start / Pause / Stop
      contextual search / throughput / theme / clipboard / window controls
    208 or 72 px primary navigation
      DownloadsView
      LinkGrabberView
      HistoryView
      SettingsView
    AddLinksView -------------------------> right drawer + scrim
    StatusBar / ActivityStatus
    I18n / ThemeManager
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

Views consume observable packages, links, settings, history, and global statistics through
`DownloadEngine`. They do not poll transfer workers. `I18n` renders English, playful Hong Kong
Cantonese, or both. A language change rebuilds translated shell/view controls while the engine
continues running; the selected Settings section and current Add Links draft are retained.
`ThemeManager` switches the canonical light/dark token sheet without rebuilding engine state.

## Shell and view composition

`MainWindow` owns one active primary destination selected from Downloads, LinkGrabber, History, and
Settings. A toggle group in the persistent rail swaps the corresponding view into `contentHost`.
The width listener applies the compact state below 980 px: navigation changes from 208 to 72 px,
labels are removed from layout, and the global search/throughput components hide.

The toolbar owns scheduler-wide Start, Pause/Resume, and Stop controls. Its search field delegates
to `DownloadsView.setFilter`, `LinkGrabberView.setFilter`, or `HistoryView.setFilter` according to
the active destination. Settings disables the field. The `ThroughputMeter` listens to the engine's
global speed property and publishes both a visual trace and an accessible text value.

`AddLinksView` is composed as a 440 px right drawer above the shell. A managed scrim intercepts
outside clicks. Interruption-safe fade/translate transitions reveal or hide the drawer; focus moves
to the URLs field when opening, and Escape dismisses it. The drawer is not a modal window, so its
submission hands asynchronous work to the engine and then routes to LinkGrabber or closes.

The bottom `StatusBar` binds global speed, running count, remaining bytes, and scheduled retry.
`ActivityStatus` contributes the latest validation, clipboard, or removal message. These nodes stay
in the layout instead of creating a blocking notification.

## Direct-download path

1. Add Links and clipboard input submit direct HTTP(S) URLs without blocking the JavaFX thread.
2. Background crawl workers parse text and probe metadata with HEAD plus ranged-GET fallback.
   LinkGrabber rows are created in small batches on the JavaFX Application Thread.
3. Observable properties publish availability, filename, and size. Auto-confirm and auto-start
   continue after probe completion when enabled.
4. An `AnimationTimer` scheduler admits queued links according to global simultaneous-download and
   per-host limits.
5. Transfer workers stream HTTP responses to target-name `.part` files, attempt Range resumption,
   and move completed data into place atomically where supported. The resolved path is retained for
   file-manager actions.
6. Scheduler and worker state return through observable model properties. Tables, progress cells,
   the throughput component, and the status bar react through bindings.

With transient recovery enabled, network/HTTP 408, 429, and 5xx failures return a link to Queue with
a bounded 2/4/8/16-second retry deadline. The scheduler renders the countdown and reuses the same
partial file. A server that declines or invalidates a range request causes a safe partial-stream
restart.

Collision behavior runs on the transfer worker. The default `ASK` enum is presented as
**Auto-rename (no prompt)** and chooses a collision-safe name; Skip, Overwrite, and Auto-rename also
continue without interrupting a batch.

## Restart state

`DirectHttpEngine` owns `AppStateStore`, a debounced local journal at
`~/.jdownloader-material/state.properties`. The writer captures supported settings, the Downloads
queue, and LinkGrabber packages on the JavaFX thread, then writes a temporary file and replaces the
journal atomically where possible.

Startup reads the journal asynchronously and applies it on the JavaFX thread. Links that were
running or paused at exit return as queued because their HTTP streams ended with the process;
retained `.part` files remain available to the next transfer.

## Append-only History Manager

`GitHistoryService` keeps three private embedded-Git repositories below
`~/.jdownloader-material/history/`:

- `settings` stores canonical non-secret settings snapshots;
- `download-lists` stores Downloads and LinkGrabber snapshots; and
- `manifest` stores immutable prepare/completion records linking matching commits.

Every settled semantic history event snapshots durable list/settings state. The manifest writes a
prepare record before completing its cross-repository record, allowing startup to finish an
interrupted save coherently. Undo, redo, and restore load an immutable snapshot into the JavaFX
model and append a corresponding history event; earlier records remain intact.

History rejects credential fields before any Git object is created. Direct-link URLs remain so a
restored list continues to work, making the local history folder private device data. Completed
files and `.part` contents do not enter history. Active telemetry such as progress ticks, speed,
retry timing, and live details is omitted; final outcome metadata remains with finished/error rows.

## Presentation architecture

The light and dark token sheets expose the same `-md-*` names. `material.css` references those
roles for the toolbar, rail, panels, tables, forms, status states, History split view, Settings
rows, and drawer. The layout constants in `MainWindow` match the stylesheet: 52 px toolbar,
208/72 px rail, 30 px status, and 440 px drawer. See [Design system](DESIGN_SYSTEM.md).

The dense views keep their models separate from presentation filtering:

- Downloads rebuilds a filtered tree while preserving package/link selection across asynchronous
  state changes.
- LinkGrabber batches rebuild requests while probes update observable metadata.
- History filters its observable timeline and binds a selected entry into the preview.
- Settings binds direct controls to live `Settings` properties; encrypted file work runs in tasks.

## Responsive and background work

- Network probes, HTTP streaming, restart-journal writes, encrypted backup, History Git storage,
  and completed-file actions run on background workers.
- JavaFX-observed mutations return through `Platform.runLater` or existing observable bindings.
- Add Links returns after input acceptance while probe/confirmation/start work continues.
- Download name/destination fields appear only for queue-safe states; active streams and completed
  paths remain stable.
- Clipboard, validation, and removal results use fixed `ActivityStatus`; durable reversal lives in
  History.
- A non-daemon close flusher completes accepted append-only history writes without keeping the
  window open for an interaction prompt.

## Documentation capture

`SimulatedEngine` seeds stable package/link/history rows and progress only when the screenshot
environment is enabled. The capture path applies the same shell, themes, localization, and views,
writes the 21-scene gallery, and exits. Normal direct-download behavior is unchanged.
