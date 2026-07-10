# JDownloader GUI - Feature Spec

This is an upstream-reference inventory of the JDownloader 2 GUI surface, derived from the
Swing source (src/jd/gui/swing/jdgui, src/org/jdownloader/gui). It is not a claim that the
Material front end or its simulated backend implements every item. Use it with
[PARITY.md](PARITY.md), which records the actual shipped scope.

> **Architectural note (upstream):** Nearly every toolbar, menu, table context menu, and tab
> bottom bar is data-driven and user-customizable. It is built at runtime from a
> MenuContainerRoot tree and rendered by MenuBuilder, with layout import/export. The lists
> below describe default structure; a faithful rewrite would preserve the customizability model.

## 1. Window layout

| Region | Upstream class | Notes |
|---|---|---|
| Main frame | JDownloaderMainFrame | Remembered geometry, minimum size, tray/close behavior |
| Menu bar | JDMenuBar / MenuManagerMainmenu | File, Settings, Extensions, Help |
| Toolbar | MainToolBar / MenuManagerMainToolbar | Actions and embedded speedmeter graph |
| Central tabs | MainTabbedPane | Downloads, LinkGrabber, Settings, My.JDownloader |
| Status bar | StatusBarImpl | Per-service traffic, activity indicators, reconnect progress |
| System tray | TrayExtension | Minimize/close-to-tray and tray menu |
| Transient feedback | BubbleNotify | Captcha, transfer, update, and crawler status |

## 2. Primary views

- **Downloads** (DownloadsView) - active and finished download list (default tab).
- **LinkGrabber** (LinkGrabberView) - staging area for crawled links before confirmation.
- **Settings** (ConfigurationView) - full preferences (closable tab).
- **My.JDownloader** (MyJDownloaderView) - optional remote-control account and devices.

## 3. The two tables (packages -> children)

Both are ExtTables over a PackageController tree of **packages -> links/files**, with
show/hide/reorder/lock columns persisted per user.

**Downloads columns:** Name (tree, enable checkbox, status icon), Size, Host, Connections,
Account, **Status/Task**, Remaining, Added/Modified/Finished dates, Duration, **Speed**, **ETA**,
Loaded, **Progress bar**, Priority, Availability, Download folder, Comment, Checksum, and more.
The row context menu includes add, settings (rename, folder, password, priority, chunks), open
file/folder, enable/disable, force download, stop-sign, resume/reset, merge/split packages,
delete, and properties.

**LinkGrabber columns:** Name, **Variant** selector, Parts, editable URL, Download folder,
Password, Enable, Size, Host, **Availability**, Priority, Comment, and more. The row context
menu includes Add/Paste/Container, **Confirm** selected/all, settings, check status, merge/split,
cleanup, and properties.

## 4. Toolbar (default)

Start, Pause, Stop, move top/up/down/bottom, clipboard-monitor toggle, auto-reconnect toggle,
account/service controls, silent mode, speed limiter, reconnect now, update check, and speedmeter
graph. Many actions are hideable or addable, including settings, proxy, quick settings, captcha,
and delete submenus.

## 5. Menu bar

- **File:** Add Links, Add Container, Backup (create/restore), Restart, Exit
- **Settings:** Settings, My.JDownloader, quick editors (chunks, parallel downloads, speed)
- **Extensions:** Dynamic list of enabled extensions and their windows
- **Help:** Knowledge base, Send log, Check for updates, Latest changes, About

## 6. Panels and prompts

- **Add Links** (AddLinksDialog upstream) - URL text area with clipboard parsing, destination
  selection and variable insertion, package name/history, comment, extract password, extraction
  option, download password, priority, Packagizer override, and add/add-and-start/add-and-force
  choices. The Material implementation should present this as an inline, nonblocking composer.
- **Add Container** - file selection for DLC/CCF/RSDF.
- **Settings / Preferences** pages: General, Reconnect, Connection/Proxy, Account Manager,
  Basic Authentication, Plugins, Captcha/Anti-Captcha, Appearance/GUI, Notifications,
  My.JDownloader, LinkGrabber Filter, Packagizer, Archive Extractor, Tray icon, Advanced
  Settings (searchable), Extension Manager, and extension pages.
- **About, Captcha, Properties, Account, Proxy, credential, delete, and new-package prompts** -
  reference surfaces in the upstream GUI. A Material rewrite should favor inline or transient
  nonblocking interaction wherever a prompt is not required for safety.

## 7. Status bar

Per-service traffic bars, status text, reconnect progress (indeterminate), dynamic activity
indicators (crawler running, availability checking, captcha pending, auto-confirm), skipped-link
marker, global speed, and optionally window-title speed.

## 8. Side panels

- **LinkGrabber sidebar** - quick filters by hoster, file type, or online status, with counts.
- **Settings sidebar** - page list, with inline extension enable/disable.
- **Overview strips** - Downloads: packages, size, loaded, remaining, running, links, speed,
  ETA, connections, finished, skipped, failed. LinkGrabber: analogous.
- **Dockable bottom bars** per tab (customizable), and an inline dockable properties panel.

## 9. Notable behaviors

Clipboard monitoring, auto-confirm/auto-start, add-at-top, automatic and manual reconnect,
speed limiter, pause behavior, per-row and aggregate progress/speed/ETA, package
expand/collapse, merge/split, auto-hide single-child packages, live search and quick filters,
media variants, priority and stop-sign, skipped-link recovery, tray behaviors, remembered window
state, nonblocking status/undo feedback, and in-app updates.
