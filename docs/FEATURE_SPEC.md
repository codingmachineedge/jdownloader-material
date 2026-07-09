# JDownloader GUI — Feature Spec

The user-facing GUI surface of JDownloader 2 that this project reimplements in Material Design.
Derived from the upstream Swing source (`src/jd/gui/swing/jdgui`, `src/org/jdownloader/gui`).
Use together with [`PARITY.md`](PARITY.md), which tracks implementation status.

> **Architectural note (upstream):** nearly every toolbar, menu, table context menu and tab
> "bottom bar" in JDownloader is *data-driven and user-customizable* — built at runtime from a
> `MenuContainerRoot` tree and rendered by `MenuBuilder`, with import/export of layouts. The
> lists below are the **default** structure; a faithful rewrite treats them as defaults and,
> ideally, preserves the customizability concept.

## 1. Window layout

| Region | Upstream class | Notes |
|---|---|---|
| Main frame | `JDownloaderMainFrame` | Remembered geometry, min size, tray/close behavior |
| Menu bar | `JDMenuBar` / `MenuManagerMainmenu` | File · Settings · Extensions · Help |
| Toolbar | `MainToolBar` / `MenuManagerMainToolbar` | Actions + embedded speedmeter graph |
| Central tabs | `MainTabbedPane` | Downloads, LinkGrabber, Settings, My.JDownloader |
| Status bar | `StatusBarImpl` | Premium bars, activity indicators, reconnect progress |
| System tray | `TrayExtension` | Minimize/close-to-tray, tray popup |
| Toasts | `BubbleNotify` | Captcha, start/stop, updates, crawler |

## 2. Primary views

- **Downloads** (`DownloadsView`) — active/finished download list (default tab).
- **LinkGrabber** (`LinkGrabberView`) — staging area for crawled links before confirmation.
- **Settings** (`ConfigurationView`) — full preferences (closable tab).
- **My.JDownloader** (`MyJDownloaderView`) — remote-control account & devices.

## 3. The two tables (packages → children)

Both are `ExtTable`s over a `PackageController` tree of **packages → links/files**, with
show/hide/reorder/lock columns persisted per user.

**Downloads columns:** Name (+tree, enable checkbox, status icon) · Size · Host · Connections ·
Account · **Status/Task** · Remaining · Added/Modified/Finished dates · Duration · **Speed** ·
**ETA** · Loaded · **Progress bar** · Priority · Availability · Download folder · Comment ·
Checksum · … Row context menu: Add, Settings submenu (rename, folder, password, priority,
chunks), open file/dir, enable/disable, force download, stop-sign, resume/reset, merge/split
packages, delete submenu, properties.

**LinkGrabber columns:** Name · **Variant** selector · Parts · URL (editable) · Download folder ·
Password · Enable · Size · Host · **Availability** (default visible) · Priority · Comment · …
Row context menu: Add/Paste/Container · **Confirm** (add selected/all) · settings submenu ·
check status · merge/split · cleanup submenu (remove disabled/dupes/offline) · properties.

## 4. Toolbar (default)

Start · Pause · Stop · | · Move top/up/down/bottom · | · Clipboard-monitor toggle ·
Auto-reconnect toggle · Global-premium toggle · Silent-mode toggle · (Speed-limiter toggle) · |
· Reconnect now · Check for updates · [speedmeter graph]. Many optional actions are hideable
and addable (settings, proxy, quick-settings submenus, captcha submenu, delete submenu…).

## 5. Menu bar

- **File:** Add Links · Add Container · Backup (create/restore) · Restart · Exit
- **Settings:** Settings · My.JDownloader · quick editors (chunks, parallel downloads, speed)
- **Extensions:** dynamic list of enabled extensions + their windows
- **Help:** Knowledge base · Send log · Check for updates · Latest changes · Donate · About

## 6. Dialogs

- **Add Links** (`AddLinksDialog`) — links text area (clipboard parse), destination chooser
  with variable insertion, package name (history), comment, extract password, auto-extract,
  download password, priority, overwrite-Packagizer toggle, Continue dropdown (add / add+start
  / add+force).
- **Add Container** — file chooser for DLC/CCF/RSDF.
- **Settings / Preferences** pages, in order: General · Reconnect · Connection/Proxy ·
  Account Manager · Basic Authentication · Plugins · Captcha/Anti-Captcha · Appearance/GUI ·
  Notifications · My.JDownloader · LinkGrabber Filter · Packagizer · Archive Extractor ·
  Tray icon · Advanced Settings (searchable) · Extension Manager · one page per extension.
- **About**, **Captcha** dialogs (image/click/multi-click), **Properties** panels (link/package),
  **Account** add/edit, **Proxy**, credential prompts, **Confirm delete**, **New package**.

## 7. Status bar

Premium/service traffic bars per host · status text · reconnect progress (indeterminate) ·
dynamic activity indicators (crawler running, availability checking, captcha pending,
auto-confirm) · skipped-links marker (click to un-skip). Global speed also shown in the
speedmeter and optionally the window title.

## 8. Side panels

- **LinkGrabber sidebar** — quick filters by hoster / file type / online status, with counts.
- **Settings sidebar** — the page list, with inline extension enable/disable.
- **Overview strips** — Downloads: packages, size, loaded, remaining, running, links, speed,
  ETA, connections, finished, skipped, failed. LinkGrabber: analogous.
- **Dockable bottom bars** per tab (customizable), **properties panel** (inline dockable).

## 9. Notable behaviors

Clipboard monitoring · auto-confirm / auto-start · add-at-top · reconnect (auto + manual) ·
speed limiter · pause-as-throttle · silent mode · per-row & aggregate progress/speed/ETA ·
package expand/collapse, merge/split, auto-hide single-child packages · live search + quick
filters · media variants · priority & stop-sign · skipped-links bulk un-skip · tray behaviors ·
bubble notifications · remembered window state · in-app updater.
