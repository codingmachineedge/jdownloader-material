# Application feature reference

This reference describes the JDownloader Material release surface: a direct HTTP(S) download
workspace with append-only local history, browser-style tabs, and nonblocking desktop flows.
For an implementation-oriented inventory, see [PARITY.md](PARITY.md).

## Window layout

| Region | Role |
| --- | --- |
| App bar | Saved application name, logo, clipboard monitor, retry behavior, theme, and window controls. |
| Workspace tab strip | One page instance per browser-style tab; right-click editor for title and label typography. |
| Navigation rail | Opens or focuses Downloads, LinkGrabber, History, and Settings. |
| Content area | Direct-download pages and inline editors. |
| Status line | Global speed, active count, remaining bytes, retry indicator, and fixed activity feedback. |

## Workspace tabs

- Tabs host Downloads, LinkGrabber, History, Settings, or Add Links pages.
- Each tab stores title, font family, size, bold, italic, and color.
- Application name, tab selection, opens, edits, and closes persist in a private local Git
  repository under `~/.jdownloader-material/workspace/`.
- Portable `.jdmtabs` export/import moves the current workspace; repository ZIP export preserves
  the full append-only workspace record.

## Downloads

- Package-to-file tree with name, size, host, status, progress, speed, ETA, and Details.
- Labeled toolbar for Add Links, Start, Pause, Stop, ordering, and Remove.
- Right-click controls for state, durable priority, completed-file actions, expansion, and
  removal.
- Live search, package aggregation, state-colored progress, and inline queue-safe rename and
  destination editing.
- Direct HTTP(S) transfer scheduling with global/per-host limits, speed cap, partial-file
  resumption, collision policy, bounded retry, and restart recovery.

## LinkGrabber

- Background HEAD plus ranged-GET metadata probes with redirect following.
- Staged package-to-link tree carrying filename, size, host, URL, and availability.
- Add Links, Paste, Remove, Add to Downloads, Add all, auto-confirm, auto-start, and inline
  availability filtering.

## Add Links

- Inline workspace page for one or more direct HTTP(S) URLs.
- Optional package name and selected destination.
- Queue in LinkGrabber and Queue & Start submit immediately, then let probe/confirmation/start
  work finish on background workers.

## History, settings, and presentation

- Downloads, LinkGrabber, and non-secret Settings live in append-only local Git timelines.
- Undo, redo, and selected restore append a new event and preserve every prior revision.
- Encrypted AES-256-GCM settings backup runs with asynchronous local file work.
- Settings cover direct-download behavior, retry, LinkGrabber flow, appearance, backup, and
  About.
- English, playful Hong Kong Cantonese, and bilingual presentation modes apply immediately.
- Light and dark Material themes use shared semantic color tokens.

## Release delivery

- Every push creates a published GitHub release with Windows x64, Linux x64, macOS Apple Silicon,
  and macOS Intel installers.
- Installers upload directly as the release assets; GitHub Actions artifacts are not retained.
- The workflow verifies the expected asset set and removes an incomplete release.
