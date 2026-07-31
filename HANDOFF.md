# Handoff

## Current state

- Baseline commit at the start of this delivery stream: `49d970f671e501ef8ec90d703afbb4595f1dda2a`.
- Scope is the Windows desktop application only. TUI code, tests, documentation and runtime remain
  out of scope.
- The implementation and documentation described here are in the shared working tree. This
  workstream has not committed, pushed or claimed remote verification.
- Concurrent agents own production Java, tests, Maven metadata, localization and raster/package
  icons. This documentation/delivery pass preserves those edits.

## Implemented desktop behavior represented by the docs

- Material 3 appearance profiles, stable per-element/state targets, context/keyboard access,
  anchored editing, presets, import/export/reset, installed-font controls and translated continuous
  color editing.
- Persistent browser-style tabs with overflow, reordering, pinning, groups, four independent tab
  searches and guarded containing/inverse bulk close.
- Plain-first, bounded RE2/J search builders wired across global/content, Settings, properties,
  notification, changelog, tab and installed-JDownloader surfaces.
- Three language modes, two independent persisted funny sliders and the first-use/Settings
  disclosure that humour styles all messages without changing facts.
- Bottom-right non-blocking notifications with severity timeouts and bounded searchable local
  history; an offline all-version changelog with date/search composition, copy and Markdown export;
  and a first-run-safe, exactly-once 1% local dim-sum card with opt-out.
- Append-only local Git history for Downloads, LinkGrabber, non-secret Settings and workspace
  structure. Undo, redo and restore append rather than rewrite.
- Windows external-editor integration and a strict-loopback installed-JDownloader bridge with
  bounded/cancellable transport, stock feature pages and confirmation-gated destructive requests.

## Delivery and documentation work

- The release workflow discovers every desktop smoke main, excludes TUI paths, runs the suite under
  Ubuntu/Xvfb, runs the Pages guard and validates matching runtime/release dim-sum catalogs before a
  release draft can exist.
- A qualifying run uses an immutable unique tag, builds exactly one real Windows x64 EXE with the
  bundled Java runtime and package icon, uploads it directly without retained Actions artifacts,
  adds one named local dim-sum photograph, and publishes only after exact asset validation.
- Four original local photographs are catalogued: Shrimp dumpling · 蝦餃, Siu mai · 燒賣,
  Char siu bao · 叉燒包 and Egg tart · 蛋撻.
- `README.md`, categorized `docs/`, tracked `wiki/`, the tabbed Pages source, `ROADMAP.md`,
  `CLAUDE.md` and this handoff describe the implemented Windows behavior without treating an
  unpushed workflow or remote surface as verified.
- The local Pages implementation now has eight discrete tabs, full tab management/four discovery
  searches, independent bounded builders, persisted language/tone/notification/appearance tools,
  local dim sum and eleven substantive in-site feature articles. Its disclosed browser limitation
  is installed-font enumeration: users receive free entry, known choices and CJK fallback.
- The HTTP/Postman category remains not applicable because the app exposes no inbound HTTP API; the
  installed-JDownloader integration is an outbound, loopback-only client.

## Local verification record

Local checks after concurrent source edits settled:

- [x] `release.yml` parses and its static invariants pass: every branch push/manual dispatch, no
  tag/PR loop, Ubuntu/Xvfb verification, one Windows x64 EXE plus one image, token fallback, ICO and
  embedded version/date/commit metadata.
- [x] Site script syntax passes. `node site/smoke-check.mjs` passes 155 assertions, eleven feature
  articles and four decoded/hash-equal local dim-sum images.
- [x] Both runtime/release dim-sum catalogs parse and every pair matches by SHA-256.
- [x] All four image pairs are unique, decode at 1254×1254 and exceed the required minimum.
- [x] Categorized documentation indexes and 128 local Markdown targets across 47 files pass
  validation.
- [x] All 12 discovered non-TUI desktop `*Smoke.java` main programs execute successfully.
- [x] The Windows packaging path builds one 77,971,456-byte EXE locally with the bundled runtime
  and `app.ico` (`SHA-256 C805FBC02CE1939AC5030171432E68DEF6B5F9BB8C195939594E58571A0582D5`).
- [x] All 26 current-build captures were SHA-256 matched into `docs/screenshots` and visually
  reviewed; the 1,939,999-byte set includes the 880×560 bilingual layout, Changelog, bridge,
  notification history and dim-sum overlay.
- [x] Off-screen Chrome verifies Settings, bilingual persistence/disclosure, notifications,
  appearance, independent/adversarial regex handling, tab management and the 560 px Pages layout.

## Remote evidence still required

- The final default-branch commit must be pushed and proven on `origin/main`.
- The GitHub Actions release and Pages runs must complete; their exact run URLs and outcomes belong
  here once known.
- The release must contain exactly the real Windows x64 EXE plus one named dim-sum image.
- Tracked wiki source must be synchronized to the wiki remote and its rendered pages checked.
- GitHub Project inspection is blocked until the active `gh` credential has `read:project`; do not
  infer its state.
- The remote `origin/claude/claude-md-docs-uhkcdj` branch still requires repository-wide ancestry
  and preservation review before any cleanup.

## Issue scans

At both the start and the final local-validation checkpoint, `gh issue list` returned zero open
issues for `Ding-Ding-Projects/jdownloader-material` and
`Ding-Ding-Projects/agent-global-memory`.
