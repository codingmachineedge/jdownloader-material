# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

JDownloader Material is a JavaFX desktop app for direct HTTP(S) downloads: Downloads, LinkGrabber,
History, Settings, and a nonblocking Add Links drawer over a real background transfer engine. It is
an independently implemented app (not the upstream JDownloader core) with its own engine and local
data model — no My.JDownloader, host accounts, captcha solving, or plugin system.

- Runtime: Java 25 (Temurin), JavaFX 25, built with Maven (wrapper committed, no system Maven needed)
- UI: JavaFX + MaterialFX, with a hand-authored compact Material 3 system (`material.css`,
  `theme-light.css`, `theme-dark.css`)
- Persistence: embedded JGit (no external Git binary) for append-only history; a properties-file
  restart journal for queue/settings recovery

## Build, run, and test

Zero-setup scripts provision a JDK 25 toolchain into `.jdk/` if none is found, then delegate to the
Maven Wrapper:

```sh
./run.sh          # Linux/macOS: build and launch
run.cmd           # Windows: build and launch
```

With an existing JDK 25+ / Maven 3.9+:

```sh
mvn javafx:run    # build and launch
mvn compile       # compile only
mvn package       # build a jar
```

**There is no Surefire/JUnit test suite.** `src/test/java` contains manual smoke checks — plain
classes with a `main` method, not JUnit tests. `mvn test` only compiles them; it does not execute
their checks. Run a smoke check explicitly after `test-compile`:

```sh
./mvnw test-compile
./mvnw org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=org.jdownloader.material.engine.DirectHttpEngineSmoke -Dexec.classpathScope=test
```

Other smoke classes: `org.jdownloader.material.i18n.LocalizationSmoke`,
`org.jdownloader.material.engine.history.GitHistoryServiceSmoke`,
`org.jdownloader.material.engine.DirectHistorySmoke`. The JavaFX-backed ones need a graphical
desktop session (they call `Platform.startup`). When changing engine, history, or i18n behavior,
run the relevant smoke class instead of assuming `mvn test` verified anything.

Refreshing the documentation gallery uses the same app with a different engine, driven by an env var:

```sh
JD_SCREENSHOT_DIR="$(pwd)/docs/screenshots" ./mvnw javafx:run
```

This makes `JDMaterialApp` select `SimulatedEngine` instead of `DirectHttpEngine`, seed deterministic
sample data, write all 21 gallery scenes, and exit.

## Architecture

Entry point: `org.jdownloader.material.app.Launcher` → `JDMaterialApp`. Normal launches construct
`DirectHttpEngine`; setting `JD_SCREENSHOT_DIR` switches to `SimulatedEngine` for deterministic
documentation capture. Views and shell code depend only on the `DownloadEngine` interface, never on
a concrete engine — see `docs/ENGINE_API.md` for the full contract.

```
app/       JDMaterialApp, Launcher — entry point, engine selection
model/     DownloadItem/Link/Package, CrawledLink/Package, DownloadState/Priority, LinkAvailability
engine/    DownloadEngine (contract), DirectHttpEngine, SimulatedEngine, Settings, AppStateStore
engine/history/  HistoryService, GitHistoryService (JGit-backed), HistoryEntry/Scope/Status/Snapshot
i18n/      I18n — resource-bundle facade for English/Cantonese/bilingual
ui/        MainWindow (shell), ThemeManager, Icons
ui/view/   DownloadsView, LinkGrabberView, HistoryView, AddLinksView, SettingsView
ui/component/  Mat (shared controls), DownloadCells, StatusBar, ThroughputMeter, ActivityStatus,
               ClipboardMonitor, CompletedFileActions
workspace/ GitWorkspaceStore, WorkspaceSnapshot/Tab/Page/Style — generic Git-backed persistence used
           by the history layer
util/      Formats
```

Full narrative architecture, the direct-download path, and threading model live in
`docs/ARCHITECTURE.md`; the engine method-by-method contract is in `docs/ENGINE_API.md`. Read those
before making non-trivial changes to the engine, history, or scheduler — they encode invariants
(retry/backoff, collision handling, restart recovery) that span multiple files.

Key structural points worth knowing before editing:

- **Engine boundary**: `DownloadEngine` is the only thing the UI talks to. `downloadPackages()` and
  `crawledPackages()` are observable JavaFX lists views bind to directly — nothing polls workers.
  Any new engine capability needs a method on this interface, implemented by both
  `DirectHttpEngine` and (usually as a no-op or simulated behavior) `SimulatedEngine`.
- **Threading**: network probes, HTTP streaming, restart-journal writes, encrypted backups, and
  History/JGit I/O all run off the JavaFX Application Thread. They report back via
  `Platform.runLater` or existing observable property bindings — never mutate JavaFX-observed state
  from a background thread directly.
- **History is append-only and privacy-sensitive**: `GitHistoryService` keeps three private JGit
  repos under `~/.jdownloader-material/history/` (`settings`, `download-lists`, `manifest`).
  Credential fields must never enter a snapshot; completed file bytes and `.part` contents never do
  either. Direct-link URLs are intentionally retained so a restored list stays usable — treat that
  directory as private local data, not something to sync or upload.
- **Restart journal** (`AppStateStore`, debounced writes to `~/.jdownloader-material/state.properties`)
  is separate from History — it's for process-restart recovery of queue/settings state, not an
  undo/redo timeline.
- **Localization**: `I18n` composes English/Cantonese/bilingual strings from
  `i18n/messages_en.properties` and `i18n/messages_yue.properties` at lookup time — bilingual copy
  is never pre-baked into resource files. Add new keys to both properties files.
- **Theming**: `theme-light.css`/`theme-dark.css` are the sole source of truth for `-md-*` color
  role tokens; `material.css` must consume those roles rather than hardcoding colors. See
  `docs/DESIGN_SYSTEM.md` for the full role table and typography scale.

## Documentation stays in sync across four places

This repo intentionally duplicates narrative docs in several locations, and they're expected to be
updated together when behavior or visuals change:

- `docs/*.md` — canonical detailed docs (`ARCHITECTURE.md`, `ENGINE_API.md`, `DESIGN_SYSTEM.md`,
  `HISTORY.md`, `PARITY.md`, `FEATURE_SPEC.md`, `UI_GUIDE.md`) plus `docs/screenshots/`.
- `wiki/*.md` — GitHub wiki source mirrored from `docs/` (`Architecture.md`, `Design-System.md`,
  `Development.md`, `Getting-Started.md`, `Home.md`, `Interface.md`, `Releases.md`, `_Sidebar.md`).
- `site/` — the static GitHub Pages overview/demo, deployed by `.github/workflows/pages.yml` on
  every push to `main`.
- `README.md` at the repo root.

If you change UI behavior, the engine contract, or visuals, update the relevant `docs/` file first,
then port the change into `wiki/` and `site/`/`README.md` as applicable — don't leave them
inconsistent. Regenerate `docs/screenshots/*` via the `JD_SCREENSHOT_DIR` flow above when the visual
gallery is affected.

## CI

- `.github/workflows/release.yml` runs on every push to `main`: builds native installers (Windows,
  Linux, macOS arm64/x64) with `jpackage` and stages them on a draft GitHub release, publishing only
  once all four expected assets exist.
- `.github/workflows/pages.yml` deploys the static `site/` directory to GitHub Pages on push to
  `main`.
- Neither workflow runs the smoke checks — there is no automated test gate. Verify engine/history/
  i18n changes locally with the smoke classes above before pushing.
