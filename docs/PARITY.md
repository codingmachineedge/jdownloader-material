# Parity checklist

Tracks the Material application against the upstream reference inventory in
[FEATURE_SPEC.md](FEATURE_SPEC.md). It distinguishes the shipped direct HTTP(S) engine from a
future JDownloader-core adapter: normal releases use DirectHttpEngine, while SimulatedEngine is
only for deterministic screenshots and demos.

Legend: **Done**; **Partial**; **Not started**

## Shell

- **Done** Main window: app bar, navigation rail, content, and status bar
- **Done** Light/dark Material themes with runtime switch
- **Done** App-bar controls: clipboard monitoring, automatic-reconnect setting, reconnect-now,
  theme, and custom window controls
- **Done** Status bar: global speed, running count, remaining bytes, reconnect indicator
- **Done** Window-title speed setting
- **Done** Inline Add Links workflow and compact nonblocking feedback for undo/navigation; no
  workflow-blocking action cards or floating form panels
- **Partial** Navigation rail: Downloads, LinkGrabber, and Settings are present; Add Links opens
  from the transfer views, but a My.JDownloader device view is not implemented
- **Not started** System tray and OS bubble notifications
- **Not started** Full menu bar, customizable/importable toolbar layouts

## Downloads view

- **Done** Package -> file tree table
- **Done** Columns: Name, Size, Host, Status, Progress, Speed, ETA, and Details
- **Done** Material status chips: Queued, Downloading, Paused, Finished, Error, Disabled
- **Done** Material state-colored progress bars
- **Done** Toolbar: Add Links, Start, Pause, Stop, move top/up/down/bottom, Remove
- **Done** Live search filter
- **Done** Right-click context menu: start/force/stop/expand/remove
- **Done** DirectHttpEngine streams normal direct HTTP(S) files to disk and reports live
  progress, speed, pause state, errors, and completion in the same rows
- **Done** Global simultaneous-download and per-host connection limits are enforced by the
  direct scheduler; the global speed cap is applied by its transfer workers
- **Done** Transfers write .part files, attempt HTTP Range resume, and finalize by atomic move
  where supported by the filesystem
- **Done** File-exists policy is nonblocking: the default Ask policy auto-renames safely rather
  than showing a prompt
- **Partial** Aggregate package rows: size/loaded/speed/state roll-up is complete; ETA/priority
  columns are not
- **Not started** Extra columns: connections, account, dates, comment, checksum, priority,
  stop-sign
- **Not started** Overview strip; drag/drop reorder of links; merge/split packages
- **Not started** Inline rename, per-item priority, download-folder editing

## LinkGrabber view

- **Done** Staging tree table: Name, Availability, Host, Size, URL
- **Done** DirectHttpEngine probes staged HTTP(S) URLs asynchronously with HEAD and ranged-GET
  fallback, then exposes online/offline state, filename, and known size
- **Done** Add Links, Paste, Confirm-to-Downloads, Add-all, Remove
- **Done** Auto-confirm continues newly submitted or clipboard direct links after availability
  work; Auto-start begins those auto-confirmed results without a dialog
- **Done** LinkGrabber packages and links are included in the local state journal
- **Partial** Inline availability filter cycles **All links**, **Checking**, **Online**, and
  **Offline**; hoster/type quick-filter sidebars are not implemented
- **Not started** Variant selector, inline editing, cleanup submenu
- **Not started** Full JDownloader crawler, DLC/CCF/RSDF or other container support, plugin
  discovery, and hoster-specific link handling

## Add Links (inline composer, not a dialog)

- **Done** Inline URL composer with optional package name and editable destination
- **Done** Direct HTTP(S) validation; unsupported input is ignored with inline explanatory status
- **Done** Queue in LinkGrabber and Queue & Start return immediately while probing runs; Queue &
  Start confirms online results and starts them automatically
- **Done** Inline workflow status; no acknowledgement prompt is needed for the normal path
- **Not started** Priority, comment, extraction options, download password, variable insertion,
  Add Container

## Settings

- **Done** Page rail with General, Connection, Reconnect, LinkGrabber, Appearance, Accounts,
  Backup, and About
- **Partial** Live-bound controls: DirectHttpEngine applies the direct download folder,
  simultaneous-download limit, per-host limit, speed limit, collision policy, and LinkGrabber
  flow settings; not every JDownloader setting has a corresponding backend feature
- **Done** Full settings export/import including optional credentials, encrypted as AES-256-GCM
  .jdmbackup files; the page performs file work asynchronously
- **Done** Non-secret settings, Download queue, and LinkGrabber staging state are journaled
  locally; running/paused links recover as queued after restart and can reuse .part files
- **Partial** Optional My.JDownloader credentials can be entered and encrypted in backups, but
  there is no remote-control service or real Account Manager
- **Not started** Plugins, CAPTCHA, filters, Packagizer, advanced searchable settings
- **Not started** Extension manager

## Tooling

- **Done** Zero-setup build/run scripts (run.cmd / run.sh): provision a JDK via Adoptium and Maven
  through the bundled wrapper
- **Done** GitHub Actions release pipeline: self-contained Windows, Linux, and macOS installers
  on every push
- **Done** Deterministic screenshot capture uses SimulatedEngine so documentation does not depend
  on a live network

## Engine

- **Done** DownloadEngine interface: control, model, statistics, and settings
- **Done** DirectHttpEngine: real direct HTTP(S) files, asynchronous probing, nonblocking
  auto-confirm/auto-start, queued scheduling, per-host/global limits, resumable .part files,
  atomic completion, collision policies, and state persistence
- **Done** SimulatedEngine: deterministic fake progress and demo rows for screenshot/demo work,
  not the normal user downloader
- **Partial** Reconnect control exposes UI state only; it is not a router or host reconnect
- **Not started** Real JDownloader-core adapter; this repository does not contain JDownloader
  plugins, containers, accounts, CAPTCHA, or full upstream compatibility (see
  [ENGINE_API.md](ENGINE_API.md))
