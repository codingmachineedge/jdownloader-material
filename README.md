# JDownloader Material

A ground-up rewrite of [JDownloader 2](https://jdownloader.org/)'s desktop GUI in
**JavaFX + [MaterialFX](https://github.com/palexdev/MaterialFX)**, styled entirely with
**Material Design 3**. Every screen — Downloads, LinkGrabber, Settings, dialogs — is Material,
in both a light and a dark theme.

The goal is a feature-faithful reimplementation of JDownloader's *front end* that reuses the
proven JDownloader core (link crawler, hoster/plugin system, download controller) behind a
clean [engine boundary](docs/ENGINE_API.md), rather than reimplementing the download engine.

![Downloads — light](docs/screenshots/downloads-light.png)
![Downloads — dark](docs/screenshots/downloads-dark.png)

- **Language:** Java 25 (Temurin), JavaFX 25
- **UI:** JavaFX 25, MaterialFX components, hand-authored Material 3 stylesheet
- **Themes:** Material light + dark, switchable at runtime from the app bar
- **Engine:** a swappable [`DownloadEngine`](src/main/java/org/jdownloader/material/engine/DownloadEngine.java)
  interface. Ships with a fully interactive in-memory `SimulatedEngine`; the real
  JDownloader-core adapter drops in behind the same interface.

## Visual tour

The gallery below is captured from the running application. See the [UI guide](docs/UI_GUIDE.md)
for how the shell, views, controls, and screenshot capture mode fit together.

| Downloads light | Downloads dark |
| --- | --- |
| ![Downloads in the light theme](docs/screenshots/downloads-light.png) | ![Downloads in the dark theme](docs/screenshots/downloads-dark.png) |

| LinkGrabber light | LinkGrabber dark |
| --- | --- |
| ![LinkGrabber in the light theme](docs/screenshots/linkgrabber-light.png) | ![LinkGrabber in the dark theme](docs/screenshots/linkgrabber-dark.png) |

| Settings light | Settings dark |
| --- | --- |
| ![Settings in the light theme](docs/screenshots/settings-light.png) | ![Settings in the dark theme](docs/screenshots/settings-dark.png) |

| In-app snackbar | Add Links panel |
| --- | --- |
| ![Dark Downloads view with snackbar feedback](docs/screenshots/snackbar-dark.png) | ![Dark Downloads view with the Add Links in-app panel](docs/screenshots/add-links-panel-dark.png) |

## Status

Front-end scaffold and core screens are in place and interactive. See
[`docs/PARITY.md`](docs/PARITY.md) for the feature-by-feature checklist against the upstream
GUI (inventoried in [`docs/FEATURE_SPEC.md`](docs/FEATURE_SPEC.md)).

Implemented so far:

- **Downloads** view — package → file tree-table (Name, Size, Host, Status, Progress, Speed,
  ETA), Material status chips and progress bars, toolbar (Add Links · Start · Pause · Stop ·
  move top/up/down/bottom · Remove), live search, right-click context menu.
- **LinkGrabber** view — staging tree-table with online-availability checking, Paste, and
  Confirm-to-Downloads.
- **In-app notifications instead of dialogs** — a non-modal overlay renders Material snackbars
  (bottom) and notification cards/panels (top-right). The **Add Links** form is an in-app
  panel, not a modal window, and every action (start/pause/stop/remove/paste/confirm/reconnect)
  reports via a snackbar.
- **Settings** — General, Connection, Reconnect, LinkGrabber, Appearance, Accounts, About
  pages with live-bound controls.
- **App bar** — clipboard-monitor and auto-reconnect toggles, reconnect-now, light/dark switch.
- **Status bar** — global speed, running count, remaining bytes, reconnect indicator.

![Add Links as an in-app panel](docs/screenshots/add-links-panel-dark.png)
![Snackbar notifications](docs/screenshots/snackbar-dark.png)
- A **simulated engine** that schedules queued links up to the concurrency limit, advances
  downloads live, honors the global speed limit, and simulates availability checks/reconnects.

## Installer releases

Every push to `main` builds and publishes a new GitHub release with self-contained native
installers for Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. The installers
include a Java 25 runtime, so users do not need to install Java or Maven separately.

- [Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest)
- [Windows x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-windows-x64.exe)

The release tag and About page include the generated build version. Windows and macOS packages
are currently unsigned, so SmartScreen or Gatekeeper may display a security warning.

## Building & running

**Zero-setup (recommended)** — no Java, no Maven required; the script provisions everything:

```sh
run.cmd        # Windows
./run.sh       # Linux / macOS
```

The script finds a JDK 25+ (`JAVA_HOME`, `PATH`, or a previously provisioned `.jdk/`); if none
exists it downloads Eclipse Temurin 25 from the Adoptium API for your OS/architecture into a
project-local `.jdk/` folder (no admin rights, no system changes), then builds and launches
through the bundled Maven Wrapper, which self-downloads Maven the same way. First run needs an
internet connection; supported platforms are Windows, macOS, and Linux on x64/arm64.

**With your own toolchain** (JDK 25+, Maven 3.9+):

```sh
mvn javafx:run       # build and launch
mvn compile          # compile only
mvn package          # build a jar
```

MaterialFX and JavaFX are resolved from Maven Central automatically.

## Settings backup (encrypted, secrets included)

Settings → **Backup** exports every setting — including secrets such as the My.JDownloader
password — to a single `.jdmbackup` file, and imports restore the full configuration on any
machine. The entire file is encrypted with **AES-256-GCM** under a key derived from your
passphrase (PBKDF2-HmacSHA256, 210k iterations, random salt); nothing is written in plaintext
and a wrong passphrase or tampered file fails authentication cleanly.

## Project layout

```
src/main/java/org/jdownloader/material/
  app/       Application entry point (JDMaterialApp, Launcher)
  model/     DownloadItem/Link/Package, CrawledLink/Package, states
  engine/    DownloadEngine interface, SimulatedEngine, Settings
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, SettingsView
  ui/component/ Mat, DownloadCells, StatusBar, NotificationCenter
  ui/dialog/ AddLinksPanel (in-app notification panel)
src/main/resources/css/
  theme-light.css / theme-dark.css   Material 3 color tokens
  material.css                       component stylesheet
docs/        UI_GUIDE, FEATURE_SPEC, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
```

## Relationship to JDownloader

This project reimplements only the **graphical front end**. It is designed to sit on top of
the existing JDownloader core, which remains the source of truth for crawling, hosting
plugins, and the download pipeline. See [`docs/ENGINE_API.md`](docs/ENGINE_API.md) for the
mapping between the `DownloadEngine` interface and the JD core classes.

## License

Intended to track upstream JDownloader licensing. The JDownloader name and core are the work
of AppWork GmbH; this is an independent front-end experiment.
