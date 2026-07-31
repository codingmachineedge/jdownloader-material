# Architecture

JDownloader Material separates direct HTTP(S) transfer work, strict-loopback installed-JDownloader
requests, persistent local state, append-only histories, bounded search, appearance/localization and
JavaFX presentation. Normal direct downloads select `DirectHttpEngine`; deterministic documentation
capture selects `SimulatedEngine` so the gallery does not depend on live network traffic.

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
    208 or 72 px primary navigation -----> WorkspacePane
      pinned strip / grouped regular strip / overflow
      four tab searches / guarded bulk close
      Downloads / LinkGrabber / History / Settings
      Notifications / Changelog / installed-JDownloader pages
    AddLinksView -------------------------> right drawer + scrim
    StatusBar / ActivityStatus / NotificationOverlay / DimSumSurpriseOverlay
    I18n / ThemeManager / AppearanceService
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

  SearchField -> SafeSearchEvaluator (RE2/J, bounded, local)
  WorkspacePane -> GitWorkspaceStore (private append-only JGit)
  AppearanceRegistry -> AppearanceProfileStore (atomic local profile)
  StockFeatureView -> JDownloaderRemoteClient -> strict loopback only
  NotificationOverlay/Center -> NotificationService (bounded local history)
~~~

Views consume observable packages, links, settings, history, and global statistics through
`DownloadEngine`. They do not poll transfer workers. `I18n` renders English, playful Hong Kong
Cantonese, or both. A language change rebuilds translated shell/view controls while the engine
continues running; the selected Settings section and current Add Links draft are retained.
`ThemeManager` switches the canonical light/dark token sheet without rebuilding engine state.

## Shell and view composition

`MainWindow` owns one `WorkspacePane`. A rail action opens or focuses its page, while the New Tab
menu can create another supported page. The workspace owns a stable pinned region, scrolling
regular/group region, overflow list, structural persistence and active content. The width listener
applies compact state below 980 px: navigation changes from 208 to 72 px, labels are removed from
layout, and the global search/throughput components hide.

The toolbar owns scheduler-wide Start, Pause/Resume and Stop controls. Its independent `SearchField`
delegates one `SearchSpec` to the active workspace content; Settings then composes it with global and
per-section fields. The `ThroughputMeter` listens to the engine's global speed property and
publishes both a visual trace and an accessible text value.

`AddLinksView` is composed as a 440 px right drawer above the shell. A managed scrim intercepts
outside clicks. Interruption-safe fade/translate transitions reveal or hide the drawer; focus moves
to the URLs field when opening, and Escape dismisses it. The drawer is not a modal window, so its
submission hands asynchronous work to the engine and then routes to LinkGrabber or closes.

The bottom `StatusBar` binds global speed, running count, remaining bytes and scheduled retry.
`ActivityStatus` retains page-local feedback. `NotificationOverlay` renders routine app-wide
information/success/errors in a bottom-right stack backed by searchable bounded history;
`DimSumSurpriseOverlay` independently owns the eligible startup delight at bottom-left. Neither
blocks content or requests an acknowledgement.

## Workspace and search

`GitWorkspaceStore` keeps the complete tab/group snapshot below
`~/.jdownloader-material/workspace/`. Each open, close, select, move, pin, group, rename, import or
bulk-close operation commits the resulting snapshot; no-op state writes no commit. Layout load/save
runs on a dedicated executor and failures return through persistent notifications.

Every `SearchField` owns expression, plain/regex mode, flags and validation. Its adjacent
`RegexBuilderPopover` binds bidirectionally to that exact field and tracks the anchor. The shared
`SafeSearchEvaluator` uses RE2/J with hard pattern/input/match/capture/result budgets and never falls
back to a backtracking engine. Pattern/sample data remains local and sample text is not persisted.

## Appearance and experience services

`AppearanceRegistry` registers the scene graph with stable target ids, installs right-click,
Shift+right-click and focused-node keyboard access, observes pseudo-state changes and drives one
anchored `AppearanceEditorPopover`. `AppearanceService` applies live global/per-state overrides and
persists them atomically through `AppearanceProfileStore`. Picker/editor nodes register themselves,
so customization also reaches the customization chrome.

`I18n` observes language plus two funny-level properties; factual resource strings are selected
before voice suffixes are applied. `NotificationService`, `ChangelogService` and
`DimSumSurpriseService` remain separate from views so their persistence, limits and selection policy
can be tested without a running external service.

## Installed-JDownloader bridge

`JDownloaderRemoteClient` is an outbound client for an installed local JDownloader Remote API. Base
URL validation permits only `http`/`https` on `localhost`, `127.0.0.0/8` or `::1`, refuses redirects,
credentials/query/fragment/traversal aliases and caps both request and streamed response bytes.
`StockFeatureView` provides typed operations and a bounded Advanced surface on background tasks.
Unknown/mutating/destructive requests require a short-lived, endpoint-scoped, one-use confirmation
token. Password arrays are cleared after request assembly and are never Settings.

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

The separate workspace JGit repository records tab/group structure. Appearance and notification
history use atomic bounded files; their enablement/configuration remains part of the normal Settings
snapshot so the setting itself is reversible.

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
environment is enabled. The capture path applies the same shell, workspace, themes, localization
and views, defines 26 scenes including notifications, changelog, the loopback-bridge UI, narrow
bilingual layout and dim sum, then exits. Normal direct-download behavior is unchanged.
