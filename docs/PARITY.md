# Parity checklist

Tracks the Material front end against the upstream reference inventory in
[FEATURE_SPEC.md](FEATURE_SPEC.md). It does not imply that a real JDownloader-core adapter is
included; current releases use SimulatedEngine.

Legend: ✅ done · 🟡 partial · ⬜ not started

## Shell

- ✅ Main window: app bar, navigation rail, content, and status bar
- ✅ Light/dark Material themes with runtime switch
- ✅ App-bar toggles: clipboard monitoring, auto-reconnect, reconnect-now
- ✅ Status bar: global speed, running count, remaining bytes, reconnect indicator
- ✅ Inline Add Links workflow and compact nonblocking feedback for undo/navigation; no
  workflow-blocking action cards or floating form panels
- 🟡 Navigation rail: Downloads, LinkGrabber, and Settings; Add Links opens from the transfer
  views, and a My.JDownloader device view is not implemented
- ⬜ System tray, OS bubble notifications, window-title speed toggle
- ⬜ Menu bar: File/Settings/Extensions/Help actions are represented in the current UI where
  applicable, but no full menu bar exists
- ⬜ Customizable/importable toolbar and menu layouts

## Downloads view

- ✅ Package -> file tree table
- ✅ Columns: Name, Size, Host, Status, Progress, Speed, ETA
- ✅ Material status chips: Queued, Downloading, Paused, Finished, Error, Disabled
- ✅ Material state-colored progress bars
- ✅ Toolbar: Add Links, Start, Pause, Stop, move top/up/down/bottom, Remove
- ✅ Live search filter
- ✅ Right-click context menu: start/force/stop/expand/remove
- 🟡 Aggregate package rows: size/loaded/speed/state roll-up is complete; ETA/priority columns
  are not
- ⬜ Extra columns: connections, account, dates, comment, checksum, priority, stop-sign
- ⬜ Overview strip; drag/drop reorder of links; merge/split packages
- ⬜ Inline rename, per-item priority, download-folder editing

## LinkGrabber view

- ✅ Staging tree table: Name, Availability, Host, Size, URL
- ✅ Deferred availability check in the simulated backend, with colored availability dots
- ✅ Add Links, Paste, Confirm-to-Downloads, Add-all, Remove
- ✅ Auto-confirm and Auto-start continue newly submitted or clipboard links after availability
  work without a dialog
- ⬜ Quick-filter sidebar by hoster/type/status
- ⬜ Variant selector, inline editing, cleanup submenu

## Add Links (inline composer, not a dialog)

- ✅ Inline URL composer with optional package name and editable destination
- ✅ Queue in LinkGrabber and Queue & Start; both return immediately while availability is
  checked, and Queue & Start continues automatically into downloads
- ✅ Inline workflow status; no acknowledgement prompt is needed for the normal path
- ⬜ Priority, comment, extraction options, download password, variable insertion, Add Container

## Settings

- ✅ Page rail with General, Connection, Reconnect, LinkGrabber, Appearance, Accounts, Backup,
  and About
- ✅ Live-bound controls for folder, sliders, toggles, and combos
- ✅ Optional My.JDownloader remote-control credentials
- ✅ Full settings export/import including optional credentials, encrypted as AES-256-GCM
  .jdmbackup files; the page performs file work asynchronously
- ⬜ Real Account Manager, plugins, CAPTCHA, filters, Packagizer, advanced searchable settings
- ⬜ Extension manager

## Tooling

- ✅ Zero-setup build/run scripts (run.cmd / run.sh): provision a JDK via Adoptium and Maven
  through the bundled wrapper
- ✅ GitHub Actions release pipeline: self-contained Windows, Linux, and macOS installers on
  every push to main

## Engine

- ✅ DownloadEngine interface: control, model, statistics, and settings
- ✅ SimulatedEngine: concurrency scheduling, live progress, speed cap, paused states,
  deferred availability, auto-confirm/auto-start behavior, and reconnect simulation
- ⬜ Real JDownloader-core adapter; this repository does not yet contain one (see
  [ENGINE_API.md](ENGINE_API.md))
