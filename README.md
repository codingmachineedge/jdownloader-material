# JDownloader Material

<p align="center"><img src="src/main/resources/icons/app.png" alt="JDownloader Material mint-teal download mark" width="132"></p>

JDownloader Material is a purpose-built JavaFX desktop app for direct HTTP(S) files. Its mint-teal,
high-density interface combines Downloads, LinkGrabber, History, Settings, and a nonblocking Add
Links drawer with a real background transfer engine. Paste URLs, inspect metadata, queue files, and
keep working while probes, disk I/O, retry scheduling, and append-only history writes continue.

Normal launches use `DirectHttpEngine`; `SimulatedEngine` is limited to deterministic documentation
capture. The project does not expose My.JDownloader, host accounts, captcha solving, plugins, or a
general JDownloader-core adapter.

- **Runtime:** Java 25 (Temurin) and JavaFX 25
- **UI:** JavaFX, MaterialFX, and a hand-authored compact Material 3 system
- **Languages:** English, playful Hong Kong Cantonese, or bilingual English / Hong Kong Cantonese
- **Themes:** light and dark, switched immediately from the global toolbar or Settings
- **Download scope:** direct HTTP and HTTPS URLs, including resumable `.part` transfers
- **Interactive demo:** [GitHub Pages](https://codingmachineedge.github.io/jdownloader-material/)

## Interface

The desktop shell keeps global work in stable positions:

- a **52 px global toolbar** with Add Links, Start, Pause/Resume, Stop, contextual search, aggregate
  throughput, theme, clipboard monitoring, and window controls;
- a **responsive navigation rail** that is 208 px wide normally and collapses to a 72 px icon rail
  below 980 px, where the global search and throughput trace also hide;
- dense page panels with 62 px headings, 48 px action rows, 34 px table headers, and 48 px data rows;
- a **30 px status bar** for aggregate speed, running count, remaining bytes, retry state, and the
  latest activity message; and
- a **440 px Add Links drawer** with a scrim, explicit close/cancel actions, initial keyboard focus,
  and Escape dismissal.

Downloads, LinkGrabber, History, and Settings are persistent destinations in the rail. Search in
the global toolbar follows the active Downloads, LinkGrabber, or History page; Settings disables
it. [Read the UI guide](docs/UI_GUIDE.md), explore the
[design system](docs/DESIGN_SYSTEM.md), or try the
[interactive GitHub Pages demo](https://codingmachineedge.github.io/jdownloader-material/).

## Visual tour

The gallery is captured from the running JavaFX application.

### Downloads

| Light | Dark |
| --- | --- |
| ![Downloads in the light theme](docs/screenshots/downloads-light.png) | ![Downloads in the dark theme](docs/screenshots/downloads-dark.png) |

| Status feedback | Selected item, light | Selected item, dark |
| --- | --- | --- |
| ![Downloads with fixed activity feedback](docs/screenshots/downloads-status-light.png) | ![Queued-download properties in the light theme](docs/screenshots/downloads-properties-light.png) | ![Queued-download properties in the dark theme](docs/screenshots/downloads-properties-dark.png) |

| Hong Kong Cantonese | Bilingual English / Hong Kong Cantonese |
| --- | --- |
| ![Downloads in playful Hong Kong Cantonese](docs/screenshots/downloads-cantonese.png) | ![Downloads in bilingual English and Hong Kong Cantonese](docs/screenshots/downloads-bilingual.png) |

### LinkGrabber

| Light | Dark | Hong Kong Cantonese |
| --- | --- | --- |
| ![LinkGrabber in the light theme](docs/screenshots/linkgrabber-light.png) | ![LinkGrabber in the dark theme](docs/screenshots/linkgrabber-dark.png) | ![LinkGrabber in playful Hong Kong Cantonese](docs/screenshots/linkgrabber-cantonese.png) |

### History

| Light | Dark | Bilingual English / Hong Kong Cantonese |
| --- | --- | --- |
| ![History in the light theme](docs/screenshots/history-light.png) | ![History in the dark theme](docs/screenshots/history-dark.png) | ![History in bilingual English and Hong Kong Cantonese](docs/screenshots/history-bilingual.png) |

### Settings

| General, light | General, dark |
| --- | --- |
| ![Settings general section in the light theme](docs/screenshots/settings-light.png) | ![Settings general section in the dark theme](docs/screenshots/settings-dark.png) |

| Appearance, light | Appearance, dark | Appearance, bilingual |
| --- | --- | --- |
| ![Appearance settings in the light theme](docs/screenshots/settings-appearance-light.png) | ![Appearance settings in the dark theme](docs/screenshots/settings-appearance-dark.png) | ![Appearance settings in bilingual English and Hong Kong Cantonese](docs/screenshots/settings-appearance-bilingual.png) |

### Add Links drawer

| Light | Dark | Bilingual English / Hong Kong Cantonese |
| --- | --- | --- |
| ![Add Links drawer in the light theme](docs/screenshots/add-links-light.png) | ![Add Links drawer in the dark theme](docs/screenshots/add-links-dark.png) | ![Add Links drawer in bilingual English and Hong Kong Cantonese](docs/screenshots/add-links-bilingual.png) |

## Current capabilities

- **Downloads** — package-to-file tree with name, size, host, status, details, progress, speed, and
  ETA. All / Running / Finished filters, Move, Remove, row context actions, and queue-safe inline
  name/destination editing complement the global transfer controls and search.
- **LinkGrabber** — staged direct URLs with asynchronous metadata probes, availability filtering,
  Add Links, Paste, Remove, Add to Downloads, and Add all.
- **Add Links drawer** — submits one or more direct HTTP(S) URLs with an optional package name and
  destination. Add queues them in LinkGrabber; Add & start begins confirmed work without blocking
  navigation.
- **Direct transfers** — redirect-aware probing, background streaming, resumable `.part` files,
  atomic finalization where supported, global/per-host concurrency, speed limiting, nonmodal
  collision policies, and bounded automatic retry for transient failures.
- **History** — a split timeline/preview view over append-only local Git history for Downloads,
  LinkGrabber, and non-secret Settings. Undo, redo, and restore append new events.
- **Settings** — a 220 px section list beside concise setting rows for General, Connection,
  Recovery, LinkGrabber, Appearance, Backup, and About. Encrypted backup file work is asynchronous.
- **Localization** — English, playful Hong Kong Cantonese, and bilingual copy apply immediately and
  persist across restarts.
- **Accessible compact UI** — named rail and icon actions, linked Settings labels, readable bilingual
  state chips, and a focus-managed Add Links dialog keep dense workflows usable at compact width.

## Installer releases

Every push to `main` stages a draft GitHub release and builds self-contained native installers for
Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. Each package includes a Java 25
runtime, so users do not need Java or Maven installed. GitHub Actions uploads installers directly
to the draft, with no retained workflow artifacts. The final check publishes only after it finds
exactly the four expected installer assets; a failed platform build removes the incomplete draft.

- [Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest)
- [Windows x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-windows-x64.exe)
- [Linux x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-linux-x64.deb)
- [macOS Apple Silicon installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-arm64.dmg)
- [macOS Intel installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-x64.dmg)

The release tag and About page include the generated build version. Windows and macOS packages are
currently unsigned, so SmartScreen or Gatekeeper can display the platform's normal security notice.

## Building and running

**Zero-setup (recommended)** — no Java or Maven required:

~~~sh
run.cmd        # Windows
./run.sh       # Linux / macOS
~~~

The scripts find a JDK 25+ through `JAVA_HOME`, `PATH`, or a project-local `.jdk/` directory. When
needed, they provision Eclipse Temurin 25 through the Adoptium API, then build and launch through
the Maven Wrapper. First run needs an internet connection; Windows, macOS, and Linux on x64/arm64
are supported.

With an existing JDK 25+ and Maven 3.9+:

~~~sh
mvn javafx:run       # build and launch
mvn compile          # compile only
mvn package          # build a jar
~~~

## Verification

The maintained [UI smoke handoff](docs/UI_SMOKE.md) records the route, accessibility, compact-layout,
and gallery checks used for this revision. It also lists reproducible manual-smoke and screenshot
commands for future interface changes.

## Local data and privacy

The restart journal and append-only history repositories live below
`~/.jdownloader-material/` and have no configured remote. History retains direct-link URLs so a
restored list remains useful, including signed parameters; treat the directory as private data.
Credential fields, completed file contents, and `.part` contents do not enter history. Settings
backup files are written only to the path selected in Settings.

## Project layout

~~~text
src/main/java/org/jdownloader/material/
  app/       Application entry point (JDMaterialApp, Launcher)
  model/     DownloadItem/Link/Package, CrawledLink/Package, states
  engine/    DownloadEngine, DirectHttpEngine, SimulatedEngine, state and settings
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, HistoryView, AddLinksView, SettingsView
  ui/component/ Mat, DownloadCells, StatusBar, ThroughputMeter, ActivityStatus
src/main/resources/
  icons/app.png                        application mark
  css/theme-light.css / theme-dark.css canonical light/dark color roles
  css/material.css                     compact desktop component system
docs/        UI_GUIDE, HISTORY, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
site/        GitHub Pages overview and interactive demo
~~~

## Project identity

JDownloader Material is an independently implemented direct HTTP(S) download app. The JDownloader
name and core are the work of AppWork GmbH; this project has its own focused engine and local data
model. Its mint-teal download mark is a project-specific derived asset, not an official JDownloader
trademark asset.

## License

Intended to track upstream JDownloader licensing.
