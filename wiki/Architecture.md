# Architecture

JDownloader Material separates JavaFX presentation, direct HTTP(S) state, optional installed-
JDownloader loopback requests, local persistence, bounded search, appearance and append-only
history.

```text
JDMaterialApp
  normal launch ------> DirectHttpEngine ------> HTTP client / .part files
  gallery capture ----> SimulatedEngine

MainWindow
  toolbar / rail / drawer / status
  WorkspacePane ------> GitWorkspaceStore (private append-only JGit)
    Downloads / LinkGrabber / History / Settings
    Notifications / Changelog / installed-JDownloader pages
  SearchField --------> SafeSearchEvaluator (bounded RE2/J)
  AppearanceRegistry -> AppearanceProfileStore
  StockFeatureView ---> JDownloaderRemoteClient ---> strict loopback only
  Notification stack -> bounded local notification history

DownloadEngine
  AppStateStore / GitHistoryService / observable model
```

`WorkspacePane` owns pinned and grouped regular tabs, overflow, four tab-search scopes and guarded
bulk close. Structural changes commit complete snapshots below `~/.jdownloader-material/workspace/`.
The main History service separately records Downloads, LinkGrabber and non-secret Settings; restore
always appends a new event.

Every desktop search field owns its adjacent builder and state. RE2/J evaluation is local and hard-
bounded. `AppearanceRegistry` assigns stable target ids and opens a tracked non-modal editor from
context, modifier-click or keyboard paths; profiles persist atomically.

The installed-JDownloader client accepts only `http`/`https` loopback URLs, never follows redirects,
bounds request/response data, clears transient passwords and confirmation-gates destructive or
unknown requests. It is not My.JDownloader cloud and never exposes an inbound API.

Direct downloads still follow the established path: background probe, scheduler admission, `.part`
stream/range resume, atomic final move where supported and observable UI updates. Restart state and
the private histories retain model intent but exclude credentials and downloaded/partial contents.

For implementation details, see the repository's [architecture document](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/ARCHITECTURE.md),
[loopback bridge](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/integrations/installed-jdownloader-bridge.md)
and [engine API](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/ENGINE_API.md).
