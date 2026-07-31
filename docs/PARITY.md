# Shipped feature inventory

JDownloader Material is a direct HTTP(S) download app. This inventory describes behavior that ships
in the application rather than comparing it with another product's complete feature list. Normal
launches use `DirectHttpEngine`; `SimulatedEngine` is limited to repeatable documentation capture.

## Shell and navigation

- Compact Material 3 desktop shell with a mint-teal light/dark system, 52 px global toolbar,
  responsive 208/72 px navigation rail, content area, and 30 px fixed status bar.
- Persistent Downloads, LinkGrabber, History, and Settings destinations; Add Links appears as a
  440 px right-hand drawer instead of a primary page.
- Browser-style workspace tabs with separate pinned/regular regions, horizontal overflow, drag and
  keyboard reorder, grouping, collapse, structural persistence and appearance targets.
- Global Add Links, Start, Pause/Resume, Stop, contextual search, aggregate throughput, theme,
  clipboard-monitoring, and window controls.
- Plain-first/regex search delegates to the active workspace content. Settings adds global and
  per-section searches. At compact width, navigation labels, search and throughput hide while
  navigation tooltips remain.
- English, playful Hong Kong Cantonese, and bilingual English / Hong Kong Cantonese presentation,
  switched immediately and saved with local settings.
- Fixed in-layout activity plus severity-aware bottom-right notifications and searchable bounded
  history. Normal work continues without an acknowledgement prompt.
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
- Append-only workspace JGit history stores tab order, pinning, groups, decorations and selection
  separately from the Downloads/LinkGrabber/Settings timeline.

## Appearance, search and experience

- Material 3 appearance profiles with global theme/density/seed/font controls, stable targets and
  states, context/keyboard access, anchored editing, presets, reset and bounded import/export.
- Installed/bundled font search, CJK fallback, deep typography schema and continuous color editing
  with alpha, gamut/clipping, contrast and bidirectional named/HEX/RGB/HSL/HSV/HWB/Lab/LCH/OKLab/
  OKLCH/CMYK translation.
- RE2/J 1.8 plain/regex evaluation with hard input/result bounds, flags, guided construction, sample
  matches/captures and copy/export. Every search field owns its builder state.
- Three persisted language modes and independent 1–5 English/Cantonese funny levels, with an
  explicit disclosure that voice affects every message while facts remain fixed.
- Offline all-version changelog with composable date/search filters, clipboard copy and Markdown
  export; exactly-once eligible 1% local dim-sum startup card with opt-out and reduced-motion
  handling.

## Windows integrations

- External editor discovery and persisted structured launch template for an owned folder/selected
  file; paths are passed to `ProcessBuilder` without a command shell.
- Strict-loopback installed-JDownloader client with typed stock operations, bounded/cancellable
  transport, independent operation/response/settings searches, sanitized failures and scoped,
  expiring, single-use confirmation tokens for destructive endpoints.

## Delivery and validation

- The zero-setup Windows `run.cmd` launcher provisions a supported JDK through the Maven Wrapper
  when needed.
- Deterministic capture defines 26 light/dark/localized screenshots without a live network
  dependency.
- GitHub Pages publishes the project overview and an
  [interactive UI demo](https://ding-ding-projects.github.io/jdownloader-material/).
- Every branch push and manual dispatch runs all discovered desktop smoke mains under Xvfb, the Pages guard,
  and dim-sum image validation before creating a release draft.
- Qualifying runs create uniquely tagged releases with exactly one Windows x64 EXE plus one named
  project-bundled dim-sum photograph.
- Assets upload straight to the draft with no retained Actions artifacts. Incomplete drafts are
  removed, while already-published releases are immutable refusal points.

## Explicit non-goals in the shipped build

The application does not embed JDownloader core or connect to My.JDownloader cloud. Its own direct
engine remains intentionally focused. Host accounts, captcha, plugins/extensions, extraction,
updates and system operations are available only as optional loopback controls for an already-
installed JDownloader instance.
