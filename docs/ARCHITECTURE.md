# Architecture

JDownloader Material has a strict dependency direction: **UI -> engine interface -> model**.
Views never depend on a concrete engine implementation.

~~~text
ui
  JDMaterialApp -> MainWindow
    view/DownloadsView   view/LinkGrabberView   view/AddLinksView   view/SettingsView
    component/{Mat, DownloadCells, StatusBar, NotificationCenter}
    ThemeManager, Icons
              |
              v
engine
  DownloadEngine (interface)   Settings   SettingsIO
  SimulatedEngine (current implementation)
              |
              v
model
  DownloadItem -> DownloadPackage -> DownloadLink
  CrawledPackage -> CrawledLink
  DownloadState, LinkAvailability
~~~

SimulatedEngine is the only bundled engine implementation. It makes the UI demonstrable but
does not contain the JDownloader crawler, plugin system, or production download controller. A
JDownloader-core adapter is a future integration point, not code that is present in this
repository.

## Data flow and nonblocking work

The model uses JavaFX observable properties. The engine changes bytes loaded, speed, and state;
views bind table cells and labels directly to those properties, so updates do not require a
manual refresh.

- DownloadPackage observes its children and re-aggregates size, loaded bytes, speed, state,
  and progress when a child or child list changes.
- SimulatedEngine uses an AnimationTimer on the JavaFX pulse (roughly a 150 ms cadence) to
  admit queued links to the configured concurrency limit, advance active work, honor the speed
  cap, and publish global speed, running count, and remaining bytes.
- Link submission is deliberately deferred: addLinks immediately puts work in LinkGrabber,
  availability checking completes later, and auto-confirm/auto-start applies after that check.
  The inline Add Links composer therefore never waits on a confirmation dialog.
- The views keep their tree tables synchronized with ListChangeListeners on the engine's
  observable package lists; row values come from property-bound cell value factories.
- Settings backup takes a settings snapshot on the JavaFX thread, then performs encryption and
  file I/O in a JavaFX Task; imported properties are applied back on the JavaFX thread. The
  Backup page reports progress inline rather than opening a blocking form.

## Theming

ThemeManager installs a token stylesheet (theme-light.css or theme-dark.css) and the shared
material.css stylesheet. The token file defines the -md-* Material 3 color roles; switching
themes swaps that token file, so controls re-resolve their colors together. See
[DESIGN_SYSTEM.md](DESIGN_SYSTEM.md).

## Why an engine interface

The engine boundary makes UI development independent of download-backend integration. The
current SimulatedEngine supplies predictable interactive behavior for the application and the
documentation screenshots. A future adapter can map the same
[DownloadEngine](ENGINE_API.md) contract onto JDownloader core classes, provided it marshals
backend updates to the JavaFX Application Thread. Until that adapter exists, releases should be
understood as a simulated front-end experience rather than a full JDownloader client.
