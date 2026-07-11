# Application feature reference

This reference describes the shipped JDownloader Material surface: a direct HTTP(S) download app
with a mint-teal high-density desktop shell, append-only local history, and nonblocking flows. For
an implementation-oriented inventory, see [PARITY.md](PARITY.md). The
[GitHub Pages demo](https://codingmachineedge.github.io/jdownloader-material/) provides an
interactive presentation of the layout.

## Window layout

| Region | Role |
| --- | --- |
| 52 px global toolbar | Project mark, Add Links, Start, Pause/Resume, Stop, contextual search, aggregate throughput, theme, clipboard monitoring, and window controls. |
| 208/72 px primary rail | Persistent Downloads, LinkGrabber, History, and Settings destinations; collapses to icons below 980 px. |
| Content area | 62 px page heading above one dense bordered panel; settings and history provide nested split layouts. |
| 30 px status bar | Global speed, active count, remaining bytes, retry indicator, and fixed activity feedback. |
| Add Links drawer | Right-hand 440 px task drawer with scrim, close/cancel, initial focus, and Escape dismissal. |

The global search targets Downloads, LinkGrabber, or History according to the active destination
and is disabled in Settings. Search and the throughput trace hide in the compact rail state.

## Downloads

- Package-to-file tree with name, size, host, status, details, progress, speed, and ETA.
- Global text search plus local All / Running / Finished state filters.
- Move and Remove toolbar actions, with row-menu controls for state, durable priority,
  completed-file actions, expansion, and removal.
- Live package aggregation, semantic state chips/progress, and inline queue-safe rename/destination
  editing.
- Direct HTTP(S) scheduling with global/per-host limits, speed cap, partial-file resumption,
  nonmodal collision policy, bounded retry, and restart recovery.

## LinkGrabber

- Background HEAD plus ranged-GET metadata probes with redirect following.
- Staged package-to-link tree carrying filename, size, host, URL, and availability.
- Global text search plus local All / Checking / Online / Offline availability filtering.
- Add Links, Paste, Remove, Add selected to Downloads, Add all, auto-confirm, and auto-start.

## Add Links

- Right-hand drawer for one or more direct HTTP(S) URLs, optional package name, and destination.
- Add and Add & start submit immediately, then let probe/confirmation/start work
  finish on background workers.
- Inline validation/acceptance status, preservation of newer edits during an in-flight submission,
  and dismissal by close, cancel, scrim, or Escape.

## History

- Split timeline/preview surface for append-only Downloads, LinkGrabber, and non-secret Settings
  events.
- Global search, scope filtering, local storage size, busy/error status, and detailed selected-event
  metadata.
- Undo, redo, and selected restore append new events and preserve every prior revision.
- Restore changes model state only; completed files and `.part` contents stay untouched.

## Settings and presentation

- 220 px section list with General, Connection, Recovery, LinkGrabber, Appearance, Backup, and
  About pages.
- Concise 62 px setting rows pair a title/supporting sentence with one direct control.
- Encrypted AES-256-GCM settings backup runs with asynchronous local file work.
- English, playful Hong Kong Cantonese, and bilingual presentation apply immediately.
- Light and dark themes use the canonical mint-teal semantic roles in [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

## Feedback and accessibility

- Persistent table/page/status feedback rather than blocking dialogs for routine actions.
- Visible primary focus outlines, normal JavaFX keyboard traversal, labeled primary actions, and
  tooltips for compact icon controls.
- State color always reinforced by text, icons, chips, or values.
- Throughput exposes an accessible text value; Add Links receives initial focus and supports Escape.
- Complete screen-reader announcement and shortcut coverage is not yet claimed.

## Direct transfer engine

- Redirect-aware HTTP(S) probing and streaming on background workers.
- Global simultaneous-download, per-host connection, and global speed limits.
- Safe `.part` range resumption, collision-safe naming/skip/overwrite behavior, and atomic final
  move where supported.
- Bounded automatic recovery for network failures and HTTP 408, 429, and 5xx responses.
- Downloads and LinkGrabber journal recovery across restarts.

## Capability boundary

Normal launches use `DirectHttpEngine`; `SimulatedEngine` is only for deterministic documentation
capture. The release does not claim My.JDownloader, a general JDownloader-core adapter, host
accounts, captcha solving, proxy management, plugins, extraction, or an updater.

## Release delivery

- Every push to `main` stages and validates a GitHub release with Windows x64, Linux x64, macOS Apple Silicon,
  and macOS Intel installers.
- Installers upload directly as release assets; GitHub Actions artifacts are not retained.
- The workflow verifies the expected asset set and removes an incomplete release.
- GitHub Pages publishes the project overview and interactive interface demo.
