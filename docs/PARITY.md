# Parity checklist

Tracks the Material application against the upstream reference inventory in
[FEATURE_SPEC.md](FEATURE_SPEC.md). It distinguishes the shipped direct HTTP(S) engine from a
future JDownloader-core adapter: normal releases use DirectHttpEngine, while SimulatedEngine is
only for deterministic screenshot capture.

Legend: **Done**; **Partial**; **Not started**

## Shell

- **Done** Main window: app bar, navigation rail, content, and status bar
- **Done** Light/dark Material themes with runtime switch
- **Done** App-bar controls: clipboard monitoring, automatic transient-failure retry, theme,
  and custom window controls
- **Done** Status bar: global speed, running count, remaining bytes, pending-retry indicator
- **Done** Window-title speed setting
- **Done** Live English, 香港粵語（得意版）, and bilingual English · 香港粵語 interface modes
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
- **Done** Right-click context menu: start/force/stop, enable/disable, priority, open completed
  file, show in folder, expand, and remove
- **Done** DirectHttpEngine streams normal direct HTTP(S) files to disk and reports live
  progress, speed, pause state, errors, and completion in the same rows
- **Done** Global simultaneous-download and per-host connection limits are enforced by the
  direct scheduler; the global speed cap is applied by its transfer workers
- **Done** Transfers write .part files, attempt HTTP Range resume, and finalize by atomic move
  where supported by the filesystem
- **Done** File-exists policy is nonblocking: the default Ask policy auto-renames safely rather
  than showing a prompt
- **Done** Completed links retain their resolved output path, including collision-safe renames
- **Done** Bounded background retry of transient direct HTTP/network failures with an inline
  countdown; permanent failures remain visible as Error rows
- **Done** Per-link durable priority orders normal scheduler admission without overriding Force Start
- **Done** Inline queued-item rename and destination editing below the table; a package is editable
  only when every child is queued/error/disabled, while active and completed transfers stay read-only
  so an existing stream or finalized file is never silently retargeted
- **Partial** Aggregate package rows: size/loaded/speed/state roll-up is complete; ETA/priority
  columns are not
- **Not started** Extra columns: connections, account, dates, comment, checksum, stop-sign
- **Not started** Overview strip; drag/drop reorder of links; merge/split packages

## LinkGrabber view

- **Done** Staging tree table: Name, Availability, Host, Size, URL
- **Done** DirectHttpEngine probes staged HTTP(S) URLs asynchronously with HEAD and ranged-GET
  fallback, then exposes online/offline state, filename, and known size
- **Done** Add Links, Paste, Add to Downloads, Add-all, Remove
- **Done** Auto-confirm continues newly submitted or clipboard direct links after availability
  work; Auto-start begins those auto-confirmed results without a dialog
- **Done** LinkGrabber packages and links are included in the local state journal
- **Done** Background availability updates preserve existing table selection and package expansion
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
  simultaneous-download limit, per-host limit, speed limit, collision policy, transient-failure
  retry, LinkGrabber flow settings, and persisted presentation language. Multi-connection
  segmentation is disabled because direct mode currently uses one safe stream per file.
- **Done** Full settings export/import including optional credentials, encrypted as AES-256-GCM
  .jdmbackup files; the page performs file work asynchronously
- **Done** Non-secret settings, Download queue, and LinkGrabber staging state are journaled
  locally; running/paused links recover as queued after restart and can reuse .part files
- **Partial** Optional My.JDownloader values are retained only for encrypted backup compatibility;
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
  atomic completion, collision policies, bounded transient retry, resolved output paths, and state persistence
- **Done** SimulatedEngine: deterministic fake progress and sample rows for screenshot capture,
  not the normal user downloader
- **Partial** Direct mode retries transient HTTP/network failures; it does not reconnect a router
  or host connection
- **Not started** Real JDownloader-core adapter; this repository does not contain JDownloader
  plugins, containers, accounts, CAPTCHA, or full upstream compatibility (see
  [ENGINE_API.md](ENGINE_API.md))
