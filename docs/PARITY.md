# Shipped feature inventory

JDownloader Material is a direct HTTP(S) download workspace. This inventory describes the
behaviors that ship in the application rather than comparing it with another product's feature
list. Normal launches use `DirectHttpEngine`; `SimulatedEngine` serves only the reproducible
documentation gallery.

## Shell and workspace

- Material 3 shell with a custom app bar, navigation rail, browser-style workspace tabs, content
  area, fixed status line, and light/dark themes.
- Application name saved in the local workspace and reflected in the window and app bar.
- One page instance per workspace tab. Downloads, LinkGrabber, History, Settings, and Add Links
  can each be opened in their own tabs.
- Right-click tab editor for tab title, font family, font size, bold, italic, and any chosen color.
- Private local Git workspace repository that remembers open tabs, close events, selection,
  application name, and per-tab typography. Each workspace mutation adds an append-only commit.
- Portable `.jdmtabs` workspace export/import plus ZIP export of the complete local workspace
  repository.
- App-bar controls for clipboard monitoring, retry behavior, theme, and window management. Text
  labels accompany application actions so their purpose stays visible.
- English, playful Hong Kong Cantonese, and bilingual English / Hong Kong Cantonese presentation
  modes, switched immediately and saved with local settings.
- Fixed in-layout activity feedback in the status line. Normal work flows continue without a
  floating notification or acknowledgement prompt.

## Downloads

- Package-to-file tree with name, size, host, status, progress, speed, ETA, and inline detail.
- State-colored progress and status treatment for queued, downloading, paused, finished, error,
  and disabled links.
- Labeled controls for Add Links, Start, Pause, Stop, ordering, and Remove; live search filters
  the table.
- Right-click commands for transfer state, priority, completed-file actions, expansion, and
  removal.
- Inline rename and next-destination editor for safe queued/error/disabled items. Package edits
  apply coherently to eligible children.
- Live package aggregation, speed reporting, retry countdowns, resolved output paths, and
  nonblocking Open completed file / Show in folder actions.

## LinkGrabber and Add Links

- Background direct-URL metadata probing with HEAD and ranged-GET fallback, redirect following,
  filename/size discovery, and online/offline state.
- Staging tree with Add Links, Paste, Remove, Add to Downloads, Add all, and inline availability
  filtering.
- Inline Add Links page for one or more direct HTTP(S) URLs, optional package name, and chosen
  destination.
- Queue in LinkGrabber and Queue & Start submit immediately; probes, confirmation, and optional
  start continue asynchronously.
- Clipboard capture, auto-confirm, and auto-start follow the same deferred direct-download path.

## Direct transfer engine

- Redirect-aware HTTP(S) transfers stream on background workers.
- Global simultaneous-download, per-host, and global speed limits are applied by the scheduler.
- `.part` files support safe range resumption when the server accepts it; completion uses an
  atomic move where the filesystem supports it.
- Collision policies run on the transfer worker. The default Ask policy chooses a safe auto-name,
  while Rename, Skip, and Overwrite are applied directly.
- Bounded automatic recovery handles transient network failures and HTTP 408, 429, and 5xx
  responses while retaining the queued item and partial data.
- Download queue and LinkGrabber state survive restart; in-progress transfers return as queued
  items and can reuse retained partial data.

## History, settings, and backup

- Append-only local Git History Manager for Downloads, LinkGrabber, and non-secret Settings.
- Undo, redo, and selected restore append new timeline events, preserving every earlier state.
- Crash-recoverable manifest coordinates Settings and download-list history repositories.
- History writes, snapshot reads, export/import, and restore work run asynchronously while the
  JavaFX interface remains responsive.
- Settings page groups live direct-download behavior, recovery behavior, LinkGrabber flow,
  presentation, encrypted backup, and About information.
- AES-256-GCM settings backup with passphrase-derived keys and asynchronous local file work.

## Delivery and validation

- Zero-setup `run.cmd` and `run.sh` launchers provision a supported JDK through the Maven Wrapper
  when needed.
- Deterministic screenshot capture supplies a stable gallery without a live network dependency.
- Every Git push creates a published GitHub release with Windows x64, Linux x64, macOS Apple
  Silicon, and macOS Intel installers.
- Installers upload straight to the release as the only release assets; the workflow retains no
  Actions artifacts and removes an incomplete release.
