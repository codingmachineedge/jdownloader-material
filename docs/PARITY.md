# Parity checklist

Tracks the Material rewrite against the upstream GUI in [`FEATURE_SPEC.md`](FEATURE_SPEC.md).

Legend: ✅ done · 🟡 partial · ⬜ not started

## Shell
- ✅ Main window (app bar + navigation rail + content + status bar)
- ✅ Light / dark Material themes, runtime switch
- ✅ App-bar toggles: clipboard monitoring, auto-reconnect, reconnect-now
- ✅ Status bar: global speed, running count, remaining, reconnect indicator
- ✅ In-app notifications (snackbars + cards) replacing modal dialogs — non-modal overlay,
  used for all action feedback and the Add Links form
- 🟡 Navigation rail (Downloads, LinkGrabber, Settings) — My.JDownloader view ⬜
- ⬜ System tray, bubble notifications, window-title speed toggle (title done, tray ⬜)
- ⬜ Menu bar (File/Settings/Extensions/Help) — actions live on the toolbar for now
- ⬜ Customizable/importable toolbar & menu layouts

## Downloads view
- ✅ Package → file tree-table
- ✅ Columns: Name, Size, Host, Status, Progress, Speed, ETA
- ✅ Material status chips (Queued/Downloading/Paused/Finished/Error/Disabled)
- ✅ Material progress bars, state-colored
- ✅ Toolbar: Add Links, Start, Pause, Stop, move top/up/down/bottom, Remove
- ✅ Live search filter
- ✅ Right-click context menu (start/force/stop/expand/remove)
- 🟡 Aggregate package rows (size/loaded/speed/state roll-up) — done; ETA/priority columns ⬜
- ⬜ Extra columns (connections, account, dates, comment, checksum, priority, stop-sign)
- ⬜ Overview strip; drag-drop reorder of links; merge/split packages
- ⬜ Inline rename, per-item priority, download-folder editing

## LinkGrabber view
- ✅ Staging tree-table (Name, Availability, Host, Size, URL)
- ✅ Online-availability check (simulated), colored availability dots
- ✅ Add Links, Paste, Confirm-to-Downloads, Add-all, Remove
- ⬜ Quick-filter sidebar (by hoster / type / status)
- ⬜ Variant selector, inline editing, cleanup submenu

## Add Links (in-app panel, not a modal dialog)
- ✅ In-app notification panel: URL box (clipboard auto-fill), package name, destination chooser
- ✅ Add / Add & Start actions, with snackbar confirmation + "View" navigation
- 🟡 Extras present as controls (auto-extract) — not yet wired
- ⬜ Priority, comment, download password, variable-insertion menu, Add Container

## Settings
- ✅ Page rail + pages: General, Connection, Reconnect, LinkGrabber, Appearance, Accounts, About
- ✅ Live-bound controls (folder, sliders, toggles, combos)
- ⬜ Account Manager (real accounts), Plugins, Captcha, Filters, Packagizer, Advanced (searchable)
- ⬜ Extension manager

## Engine
- ✅ `DownloadEngine` interface (control, model, stats, settings)
- ✅ `SimulatedEngine`: scheduling to concurrency limit, live progress, speed cap, availability,
  reconnect
- ⬜ Real JDownloader-core adapter (see [`ENGINE_API.md`](ENGINE_API.md))
