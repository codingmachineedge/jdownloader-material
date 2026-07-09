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

- **Language:** Java 21+ (built and tested on Temurin 25)
- **UI:** JavaFX 25, MaterialFX components, hand-authored Material 3 stylesheet
- **Themes:** Material light + dark, switchable at runtime from the app bar
- **Engine:** a swappable [`DownloadEngine`](src/main/java/org/jdownloader/material/engine/DownloadEngine.java)
  interface. Ships with a fully interactive in-memory `SimulatedEngine`; the real
  JDownloader-core adapter drops in behind the same interface.

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
- **Add Links** dialog — Material modal with URL box (clipboard auto-fill), package name,
  destination chooser, and Add / Add&Start actions.
- **Settings** — General, Connection, Reconnect, LinkGrabber, Appearance, Accounts, About
  pages with live-bound controls.
- **App bar** — clipboard-monitor and auto-reconnect toggles, reconnect-now, light/dark switch.
- **Status bar** — global speed, running count, remaining bytes, reconnect indicator.
- A **simulated engine** that schedules queued links up to the concurrency limit, advances
  downloads live, honors the global speed limit, and simulates availability checks/reconnects.

## Building & running

Requires a JDK 21+ and Maven 3.9+.

```sh
mvn javafx:run       # build and launch
mvn compile          # compile only
mvn package          # build a jar
```

MaterialFX and JavaFX are resolved from Maven Central automatically.

## Project layout

```
src/main/java/org/jdownloader/material/
  app/       Application entry point (JDMaterialApp, Launcher)
  model/     DownloadItem/Link/Package, CrawledLink/Package, states
  engine/    DownloadEngine interface, SimulatedEngine, Settings
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, SettingsView
  ui/component/ Mat (widget factory), DownloadCells, StatusBar
  ui/dialog/ AddLinksDialog
src/main/resources/css/
  theme-light.css / theme-dark.css   Material 3 color tokens
  material.css                       component stylesheet
docs/        FEATURE_SPEC, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
```

## Relationship to JDownloader

This project reimplements only the **graphical front end**. It is designed to sit on top of
the existing JDownloader core, which remains the source of truth for crawling, hosting
plugins, and the download pipeline. See [`docs/ENGINE_API.md`](docs/ENGINE_API.md) for the
mapping between the `DownloadEngine` interface and the JD core classes.

## License

Intended to track upstream JDownloader licensing. The JDownloader name and core are the work
of AppWork GmbH; this is an independent front-end experiment.
