# Engine API

The GUI depends only on
[DownloadEngine](../src/main/java/org/jdownloader/material/engine/DownloadEngine.java).
The bundled implementation is the in-memory SimulatedEngine. No real JDownloader-core adapter
is included in this repository; the mappings below describe the intended target for a future
adapter, not a currently active integration.

## Model exposure

| Interface | Future JDownloader-core concept | Notes |
|---|---|---|
| downloadPackages() : ObservableList<DownloadPackage> | DownloadController -> FilePackage list | Wrap file packages and publish updates on the FX thread. |
| crawledPackages() : ObservableList<CrawledPackage> | LinkCollector -> CrawledPackage list | Exposes the LinkGrabber staging tree. |

DownloadLink and CrawledLink mirror the UI-relevant state of JD's DownloadLink and CrawledLink.
A future adapter would listen to DownloadControllerListener and LinkCollectorListener, then copy
loaded bytes, speed, and state into the observable model properties on the JavaFX Application
Thread.

## LinkGrabber

| Interface | Future JDownloader-core concept |
|---|---|
| addLinks(text, packageName, autoConfirm, autoStart) | LinkCollector.addCrawlerJob(...) / LinkCrawler |
| confirmToDownloads(packages, autoStart) | LinkCollector.moveLinksToDownloadList(...) |
| confirmAll(autoStart) | Confirm the collector contents. |
| removeCrawled(packages) | LinkCollector.removePackage(...) |

addLinks is a nonblocking workflow contract. It adds submitted URLs to the staging model and
returns without waiting for a confirmation prompt. Availability work completes later; if either
the explicit arguments or the corresponding settings request it, the engine then confirms the
new package and starts it. SimulatedEngine demonstrates that deferred behavior. A future adapter
must preserve it while delegating actual crawling to the JD core.

## Download control

| Interface | Future JDownloader-core concept |
|---|---|
| start() | DownloadWatchDog.startDownloads() |
| pause(boolean) | DownloadWatchDog.pauseDownloadWatchDog(true/false) |
| stop() | DownloadWatchDog.stopDownloads() |
| forceStart(links) | DownloadWatchDog.forceDownload(links) |
| removeDownloads(items) | DownloadController.removePackage/removeChildren(...) |
| reconnect() | Reconnecter.forceReconnect() |

## Global state (observable)

| Interface | Future JDownloader-core concept |
|---|---|
| runningProperty() / pausedProperty() | DownloadWatchDog state machine |
| globalSpeedProperty() | DownloadWatchDog.getDownloadSpeedManager() |
| runningCountProperty() | Active SingleDownloadController count |
| totalRemainingProperty() | Sum of unfinished bytes |
| reconnectingProperty() | Reconnecter progress |

## Settings

Settings has properties corresponding to portions of JD configuration such as general,
connection, GUI, LinkGrabber, and reconnect settings. In a future adapter, each property would
bind to the appropriate org.appwork.storage.config key. The current settings object belongs to
the simulated front end and is not a live view of a JDownloader installation.

## Threading contract

Backend callbacks can arrive on background threads; all mutations observed by the JavaFX UI
must be made on the JavaFX Application Thread (Platform.runLater). The adapter is the single
place that crosses that boundary. Likewise, expensive backup encryption and file I/O belong in a
background task, while UI properties are snapshotted and applied on the JavaFX thread.
