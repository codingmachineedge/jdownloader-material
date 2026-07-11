# Shipped feature inventory

JDownloader Material is a direct HTTP(S) download app. This inventory describes behavior that ships
in the application rather than comparing it with another product's complete feature list. Normal
launches use `DirectHttpEngine`; `SimulatedEngine` is limited to repeatable documentation capture.

## Shell and navigation

- Compact Material 3 desktop shell with a mint-teal light/dark system, 52 px global toolbar,
  responsive 208/72 px navigation rail, content area, and 30 px fixed status bar.
- Persistent Downloads, LinkGrabber, History, and Settings destinations; Add Links appears as a
  440 px right-hand drawer instead of a primary page.
- Global Add Links, Start, Pause/Resume, Stop, contextual search, aggregate throughput, theme,
  clipboard-monitoring, and window controls.
- Search delegates to the active Downloads, LinkGrabber, or History view and disables in Settings.
  At compact width, navigation labels, search, and throughput hide while navigation tooltips remain.
- English, playful Hong Kong Cantonese, and bilingual English / Hong Kong Cantonese presentation,
  switched immediately and saved with local settings.
- Fixed in-layout activity feedback. Normal work continues without an acknowledgement prompt.
- Visible keyboard focus, labeled primary actions, tooltips on compact icon controls, accessible
  throughput text, text/icon reinforcement for semantic colors, and Escape-close drawer behavior.

## Downloads

- Package-to-file tree with name, size, host, status, details, progress, speed, and ETA.
- State-colored progress and labeled chips for queued, downloading, paused, finished, error, and
  disabled links.
- Global text search plus All / Running / Finished filters; local Move and Remove controls.
- Row-menu commands for transfer state, priority, completed-file actions, expansion, and removal.
- Inline rename and next-destination editing for safe queued/error/disabled items. Package edits
  apply coherently when every child is eligible.
- Live package aggregation, retry countdowns, resolved output paths, and nonblocking Open file /
  Show in folder actions.

## LinkGrabber and Add Links

- Background direct-URL metadata probing with HEAD and ranged-GET fallback, redirect following,
  filename/size discovery, and online/offline state.
- Staging tree with global search, All / Checking / Online / Offline filtering, Add Links, Paste,
  Remove, Add selected to Downloads, and Add all.
- Right drawer for one or more direct HTTP(S) URLs, optional package name, and destination.
- Add and Add & start return after acceptance; probes, confirmation, and optional
  start continue asynchronously.
- Drawer focus transfer, Close/Cancel/scrim/Escape dismissal, inline validation, and protection of
  edits made while an earlier submission completes.
- Clipboard capture, auto-confirm, auto-start, and add-at-top follow the same deferred path.

## Direct transfer engine

- Redirect-aware HTTP(S) transfers stream on background workers.
- Global simultaneous-download, per-host connection, and global speed limits are applied by the
  scheduler.
- `.part` files support safe range resumption; completion uses an atomic move where supported.
- Collision behavior runs on transfer workers. The default safely auto-renames without a prompt;
  Skip, Overwrite, and Auto-rename are also nonmodal.
- Bounded automatic recovery handles transient network failures and HTTP 408, 429, and 5xx while
  retaining the queued item and partial data.
- Downloads and LinkGrabber survive restart; in-progress transfers return as queued and can reuse
  retained partial bytes.

## History, settings, and backup

- Split timeline/preview History view over append-only local Git state for Downloads, LinkGrabber,
  and non-secret Settings.
- Global search, scope filtering, storage-size reporting, detailed event metadata, and inline
  ready/busy/error status.
- Undo, redo, and selected restore append new events, preserving every earlier state.
- Crash-recoverable manifest coordinates Settings and download-list history repositories.
- History writes, reads, and restore work run asynchronously while the interface stays responsive.
- Two-pane Settings view with a 220 px list for General, Connection, Recovery, LinkGrabber,
  Appearance, Backup, and About.
- AES-256-GCM settings backup with passphrase-derived keys and asynchronous local file work.

## Delivery and validation

- Zero-setup `run.cmd` and `run.sh` launchers provision a supported JDK through the Maven Wrapper
  when needed.
- Deterministic capture supplies 21 light/dark/localized screenshots without a live network
  dependency.
- GitHub Pages publishes the project overview and an
  [interactive UI demo](https://codingmachineedge.github.io/jdownloader-material/).
- Every push to `main` creates a GitHub release with Windows x64, Linux x64, macOS Apple Silicon, and macOS
  Intel installers.
- Installers upload straight to the release as its only assets; no Actions artifacts are retained,
  and an incomplete release is removed.

## Explicit non-goals in the shipped build

The application does not claim My.JDownloader, a general JDownloader-core adapter, host accounts,
captcha workflows, proxy management, a plugin/extension system, archive extraction, or an updater.
