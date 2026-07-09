# Architecture

JDownloader Material is a JavaFX application split into three layers with a strict dependency
direction: **UI → engine interface → model**. The UI never depends on a concrete engine.

```
        ┌────────────────────────── ui ──────────────────────────┐
        │  JDMaterialApp → MainWindow                            │
        │    ├─ view/DownloadsView   ├─ view/LinkGrabberView     │
        │    ├─ view/SettingsView    ├─ dialog/AddLinksDialog    │
        │    ├─ component/{Mat, DownloadCells, StatusBar}        │
        │    └─ ThemeManager, Icons                              │
        └───────────────┬───────────────────────────────────────┘
                        │ depends only on
        ┌───────────────▼──────────── engine ───────────────────┐
        │  DownloadEngine (interface)   Settings                 │
        │  SimulatedEngine (impl)  ◄── swap for JD-core adapter  │
        └───────────────┬───────────────────────────────────────┘
                        │ observes
        ┌───────────────▼──────────── model ────────────────────┐
        │  DownloadItem ─┬─ DownloadPackage (aggregates)         │
        │                └─ DownloadLink                         │
        │  CrawledPackage → CrawledLink                          │
        │  DownloadState, LinkAvailability                       │
        └────────────────────────────────────────────────────────┘
```

## Data flow

The model is built from JavaFX **observable properties**. The engine mutates model properties
(bytes loaded, speed, state); the views bind table cells and labels directly to those
properties, so the UI updates reactively with no manual refresh calls.

- `DownloadPackage` listens to its children and re-aggregates size / loaded / speed / state /
  progress whenever a child or the child list changes.
- `SimulatedEngine` runs an `AnimationTimer` on the JavaFX pulse (~150 ms cadence): it promotes
  queued links up to the concurrency limit, advances running links, applies the global speed
  cap, and republishes `globalSpeed` / `runningCount` / `totalRemaining`.
- Views keep their tree-tables in sync via `ListChangeListener`s on the engine's observable
  package lists; per-row live values come from cell value factories bound to item properties.

## Theming

`ThemeManager` installs two stylesheets on the scene: a **token file**
(`theme-light.css` or `theme-dark.css`) that defines the `-md-*` Material 3 color roles, and the
shared `material.css` that styles every component using only those tokens. Toggling the theme
swaps the token file, so all lookups re-resolve and the whole UI re-themes instantly. See
[`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).

## Why an engine interface

JDownloader's real value is its core: the link crawler, the hoster/plugin ecosystem, and the
download controller. Reimplementing those would be a decade of work and a maintenance sink.
Instead the GUI talks to a small [`DownloadEngine`](ENGINE_API.md) contract. `SimulatedEngine`
makes the UI fully demonstrable today; a thin adapter over the JD core replaces it without any
change to the views.
