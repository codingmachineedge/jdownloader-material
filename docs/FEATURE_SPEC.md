# Application feature reference

This reference describes the shipped JDownloader Material surface: a direct HTTP(S) download app
with a mint-teal high-density desktop shell, append-only local history, and nonblocking flows. For
an implementation-oriented inventory, see [PARITY.md](PARITY.md). The
[GitHub Pages demo](https://ding-ding-projects.github.io/jdownloader-material/) provides an
interactive presentation of the layout.

## Window layout

| Region | Role |
| --- | --- |
| 52 px global toolbar | Project mark, Add Links, Start, Pause/Resume, Stop, contextual search, aggregate throughput, theme, clipboard monitoring, and window controls. |
| 208/72 px primary rail | Persistent Downloads, LinkGrabber, History, and Settings destinations; collapses to icons below 980 px. |
| Workspace | Persistent pinned/regular tab strips, groups, overflow and one active page panel. |
| Content area | Dense page panel; Settings, History and stock bridge pages provide nested/tabbed layouts. |
| 30 px status bar | Global speed, active count, remaining bytes, retry indicator, and fixed activity feedback. |
| Add Links drawer | Right-hand 440 px task drawer with scrim, close/cancel, initial focus, and Escape dismissal. |

The global plain-first/regex search targets the active workspace page. Settings also supplies a
global settings search and an independent search inside every section. Search and the throughput
trace hide in the compact rail state.

## Workspace navigation

- Tabs can be opened, selected, dragged/reordered, pinned, closed and moved by keyboard or context
  menu. Pinned tabs remain in a protected stable region with accessible full names.
- Groups support create/rename/color/reorder/pin/collapse/remove and tab membership. The complete
  structure persists in an append-only local Git repository.
- Current-strip, per-group, group-name and master-tab searches each own an adjacent bounded RE2/J
  builder. Overflow lists every tab regardless of filter state.
- Containing and inverse bulk close show scope/mode/count/title preview, exclude pinned tabs by
  default and skip content with unsaved work.

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

- General, Connection, Recovery, LinkGrabber, Appearance, Backup and About pages with global and
  per-page search.
- Concise 62 px setting rows pair a title/supporting sentence with one direct control.
- Encrypted AES-256-GCM settings backup runs with asynchronous local file work.
- English, playful Hong Kong Cantonese and bilingual presentation apply immediately. Independent
  English/Cantonese 1–5 funny levels persist and style all messages without changing facts.
- Material 3 appearance covers light/dark, density, seed/accent colors, fonts and stable
  per-element/state overrides through an anchored context/keyboard editor.

## Search and feedback

- Every desktop search field is plain text by default and has an independent adjacent full RE2/J
  builder with guided fragments, flags, bounded sample text, live matches/captures and copy/export.
- Routine information, success and non-decision errors use a bottom-right stack. Information and
  success auto-dismiss; warning/error cards persist. A searchable local Notification Center retains
  bounded history.
- The bundled all-version changelog composes plain/regex search with locale/ISO date ranges and
  presets, then copies or exports the filtered Markdown view.
- On exactly 1% of eligible post-first-run launches, a local dim-sum image may appear for eight
  seconds in a non-blocking bottom-left card. The user can disable it.

## Desktop integrations

- Windows editor detection and a persisted structured command open owned folders or selected files
  without routing arguments through a shell.
- The optional installed-JDownloader bridge accepts only strict loopback base URLs, caps and cancels
  requests/responses, exposes stock feature pages and confirmation-gates destructive operations.
  Passwords are transient and never stored.

## Feedback and accessibility

- Persistent table/page/status feedback and non-blocking notifications rather than dialogs for
  routine actions.
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

Normal direct downloads use `DirectHttpEngine`; `SimulatedEngine` is only for deterministic
documentation capture. The app does not embed JDownloader core or connect to My.JDownloader cloud.
Accounts, plugins, captcha, extraction, update and other stock pages are outbound controls for an
installed local JDownloader instance and are unavailable when that loopback API is not running.

## Release delivery

- Every branch push and manual dispatch runs every discovered desktop smoke main under Xvfb, the static
  Pages guard, and bundled dim-sum validation before a draft release exists.
- A qualifying run uses a unique non-reused tag and builds exactly one Windows x64 EXE.
- Installers upload directly as release assets; GitHub Actions artifacts are not retained.
- The workflow requires the Windows EXE plus one named, bundled dim-sum photograph. It
  removes an incomplete draft and refuses to mutate an already-published release.
- GitHub Pages runs its static guard before publishing the overview and interactive interface demo.
