# Engine API

The GUI depends only on
[`DownloadEngine`](../src/main/java/org/jdownloader/material/engine/DownloadEngine.java). The
bundled `SimulatedEngine` implements it in memory; a real adapter over the JDownloader core
implements the same interface. This document maps each interface member to the JD-core concept
it corresponds to, so the adapter is a mechanical bridge rather than a redesign.

## Model exposure

| Interface | JD core equivalent | Notes |
|---|---|---|
| `downloadPackages() : ObservableList<DownloadPackage>` | `DownloadController` → `FilePackage` list | Wrap FilePackages; push updates on the FX thread |
| `crawledPackages() : ObservableList<CrawledPackage>` | `LinkCollector` → `CrawledPackage` list | LinkGrabber staging tree |

`DownloadLink` / `CrawledLink` mirror JD's `DownloadLink` / `CrawledLink`. The adapter listens
to JD's `DownloadControllerListener` / `LinkCollectorListener` and copies bytes-loaded, speed,
and state into the observable model properties (marshalled onto the JavaFX thread).

## LinkGrabber

| Interface | JD core equivalent |
|---|---|
| `addLinks(text, packageName, autoConfirm)` | `LinkCollector.addCrawlerJob(...)` / `LinkCrawler` |
| `confirmToDownloads(packages, autoStart)` | `LinkCollector.moveLinksToDownloadList(...)` |
| `confirmAll(autoStart)` | confirm the whole collector |
| `removeCrawled(packages)` | `LinkCollector.removePackage(...)` |

## Download control

| Interface | JD core equivalent |
|---|---|
| `start()` | `DownloadWatchDog.startDownloads()` |
| `pause(boolean)` | `DownloadWatchDog.pauseDownloadWatchDog(true/false)` |
| `stop()` | `DownloadWatchDog.stopDownloads()` |
| `forceStart(links)` | `DownloadWatchDog.forceDownload(links)` |
| `removeDownloads(items)` | `DownloadController.removePackage/removeChildren(...)` |
| `reconnect()` | `Reconnecter.forceReconnect()` |

## Global state (observable)

| Interface | JD core equivalent |
|---|---|
| `runningProperty()` / `pausedProperty()` | `DownloadWatchDog` state machine |
| `globalSpeedProperty()` | `DownloadWatchDog.getDownloadSpeedManager()` |
| `runningCountProperty()` | active `SingleDownloadController` count |
| `totalRemainingProperty()` | sum of unfinished bytes |
| `reconnectingProperty()` | `Reconnecter` progress |

## Settings

`Settings` maps to JD's config interfaces (`GeneralSettings`, `InternetConnectionSettings`,
`GraphicalUserInterfaceSettings`, `LinkgrabberSettings`, `ReconnectConfig`). The adapter binds
each property to the corresponding `org.appwork.storage.config` key.

## Threading contract

JD-core callbacks arrive on background threads; all model mutations the GUI observes **must** be
applied on the JavaFX Application Thread (`Platform.runLater`). The adapter is the single place
that crosses that boundary — the views assume every observable changes on the FX thread.
