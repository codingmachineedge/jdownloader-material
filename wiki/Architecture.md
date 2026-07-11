# Architecture

JDownloader Material separates JavaFX presentation, observable download state, direct HTTP(S) work, restart persistence, and append-only history.

```text
JDMaterialApp
  normal launch --------> DirectHttpEngine ----> HTTP client / .part files
  gallery capture ------> SimulatedEngine
                               |
MainWindow + views <----- DownloadEngine
  Downloads                   |-- AppStateStore
  LinkGrabber                 |-- GitHistoryService
  History                     `-- observable model
  Settings
```

`MainWindow` owns one active fixed destination and the global toolbar, navigation rail, Add Links drawer, and status bar. Views consume observable packages, links, settings, history, and aggregate statistics through the `DownloadEngine` contract; they do not poll workers.

## Direct HTTP(S) path

1. Add Links or clipboard input validates direct HTTP(S) URLs off the JavaFX thread.
2. Crawl workers follow redirects and probe with HEAD plus ranged-GET fallback.
3. The scheduler admits queued links under global and per-host concurrency limits.
4. Transfer workers stream to `.part` files, attempt Range resumption, and move completed data to the final name atomically where supported.
5. Observable properties update tables, progress cells, throughput, and fixed status feedback.

Transient network errors and HTTP 408, 429, and 5xx responses can use bounded 2/4/8/16-second retry. Collision policies remain nonmodal. The engine does not provide a general JDownloader-core adapter, My.JDownloader session, host accounts, captcha solving, or plugin execution.

## Persistence and history

`AppStateStore` writes a debounced journal at `~/.jdownloader-material/state.properties`. A running or paused transfer returns as queued after restart; its retained `.part` file can still be resumed.

`GitHistoryService` maintains private embedded-JGit repositories below `~/.jdownloader-material/history/` for non-secret Settings, download lists, and a manifest tying coherent events together. Undo, redo, and restore load model snapshots and append new events. Credentials, downloaded contents, partial-file contents, and volatile transfer telemetry are excluded; final outcome metadata and direct URLs are retained.

`SimulatedEngine` is restricted to deterministic documentation capture. It does not alter the capability claims of normal releases.

For implementation details, see the repository’s [architecture document](https://github.com/codingmachineedge/jdownloader-material/blob/main/docs/ARCHITECTURE.md) and [engine API](https://github.com/codingmachineedge/jdownloader-material/blob/main/docs/ENGINE_API.md).
