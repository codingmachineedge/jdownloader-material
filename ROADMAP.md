# Roadmap

This roadmap covers only the Windows desktop application. The implementation inventory below is
present in the current desktop source. “Remote evidence pending” means exactly that: it does not
undo the local implementation, and it does not predict a release before the final commit is pushed
and GitHub reports the result.

## Implemented desktop baseline

- Direct HTTP(S) downloads, LinkGrabber staging, bounded retry/resume and restart recovery.
- Browser-style workspace tabs with overflow, drag/keyboard reordering, pinning, grouping,
  persistent structure, four independent discovery searches and previewed containing/inverse bulk
  close with unsaved-work guards.
- Plain-text-first RE2/J search with independent anchored builders, guided construction, flags,
  validation, live matches/captures, export and hard resource bounds across desktop search fields.
- Material 3 appearance profiles with live global controls, stable per-element and per-state targets,
  context/keyboard access, anchored editor, presets, import/export, reset, installed-font browsing,
  deep typography properties and continuous translated color controls.
- English, playful Hong Kong-style Cantonese and compact bilingual modes, plus independent persisted
  English/Cantonese funny levels 1–5 and the all-message disclosure.
- Severity-aware bottom-corner notifications, bounded searchable notification history, an all-
  version searchable/date-filterable/copyable/exportable changelog and the exactly-once 1% local
  dim-sum startup surprise with persisted opt-out.
- Append-only private JGit history for Downloads, LinkGrabber, non-secret Settings and workspace
  structure; restore appends a new revision.
- Windows external-editor discovery/configuration and structured launches without a command shell.
- A bounded, cancellable, strict-loopback bridge to an installed JDownloader Remote API, including
  stock feature pages and one-use confirmation tokens for destructive requests.
- Test-first delivery workflow, Windows x64 EXE packaging, immutable unique release tags, one bundled
  dim-sum release image, guarded GitHub Pages and tracked wiki source.

## Completion gates for this delivery

1. Let all concurrent desktop source edits settle; compile and run every discovered non-TUI
   `*Smoke.java` main, then capture and inspect the changed visible surfaces.
2. Validate workflow YAML, Pages behavior, documentation links, both dim-sum catalogs and every
   release asset locally.
3. Commit the complete intended tree to `main`, push once, and prove the pushed `origin/main`
   contains the commit without discarding unrelated work.
4. Inspect the resulting GitHub Actions release and Pages runs. A successful release must contain
   exactly one real Windows x64 EXE plus the named, decodable dim-sum image; a failed verification
   run must publish no release.
5. Synchronize the tracked `wiki/` source, verify the live Pages/wiki content, record exact remote
   links and complete safe branch/worktree/stash cleanup.

## Evidence policy

Executable behavior, persistence, localization, accessibility, failure paths and relevant visual
layouts require local evidence. Delivery additionally requires pushed-commit ancestry, a completed
Actions run, the real installer and photo assets, Pages deployment and wiki synchronization. A
running or not-yet-started remote check is recorded as such, never as verified.

The repository's TUI, its tests, its documentation and its runtime remain out of scope until the
user explicitly reopens that scope.
