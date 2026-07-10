# Engine API

The GUI depends only on
[DownloadEngine](../src/main/java/org/jdownloader/material/engine/DownloadEngine.java). That
boundary is real in the shipped application: normal launches create **DirectHttpEngine**, which
downloads ordinary direct HTTP and HTTPS files. It is also the seam where a future
JDownloader-core adapter could be added without making views depend on JDownloader classes.

| Implementation | Used by | Scope |
|---|---|---|
| DirectHttpEngine | Normal application launch | Real direct HTTP(S) probing, queueing, streaming, resume, and local state recovery. |
| SimulatedEngine | Deterministic screenshot and demo code paths | In-memory sample data and fake progress; it never performs normal-user downloads. |

DirectHttpEngine is deliberately not a JDownloader-core adapter. It has no hoster plugins,
container crawler, Account Manager, CAPTCHA solver, My.JDownloader backend, or full upstream
compatibility.

## Model exposure

| Interface | Shipped behavior | Future JDownloader-core concept |
|---|---|---|
| downloadPackages() : ObservableList<DownloadPackage> | Exposes the real direct-download queue and its live transfer state. | DownloadController -> FilePackage list |
| crawledPackages() : ObservableList<CrawledPackage> | Exposes staged direct URLs and asynchronous probe results. | LinkCollector -> CrawledPackage list |

DownloadLink and CrawledLink carry the UI-relevant name, host, URL, destination, size,
loaded-byte, availability, and state data. A future adapter would listen to JDownloader
controller events and copy those values into these observable model properties on the JavaFX
Application Thread.

## Shipped direct HTTP(S) pipeline

### LinkGrabber

| Interface | Current direct-engine behavior | Future JDownloader-core concept |
|---|---|---|
| addLinks(text, packageName, destination, autoConfirm, autoStart) | Filters direct HTTP(S) URLs off the UI thread, puts them in a LinkGrabber package, then probes them in background workers. | LinkCollector.addCrawlerJob(...) / LinkCrawler |
| confirmToDownloads(packages, autoStart) | Moves online staged links into the real Downloads queue. | LinkCollector.moveLinksToDownloadList(...) |
| confirmAll(autoStart) | Confirms every online staged link. | Confirm the collector contents. |
| removeCrawled(...) / removeCrawledLinks(...) | Removes staged packages or individual staged links. | LinkCollector.removePackage(...) |

Probing first tries a HEAD request and falls back to a ranged GET when metadata is not
available. Redirects are followed; a successful response can update the availability, filename,
and size shown in LinkGrabber. Submission returns immediately. When explicit arguments or the
LinkGrabber settings request it, confirmation and starting happen only after the asynchronous
probe completes, with no confirmation dialog.

### Downloads

start() schedules queued links on the JavaFX pulse; file I/O itself runs on daemon workers.
The scheduler enforces the configured global simultaneous-download limit and the configured
per-host connection limit. The direct engine also honors the global speed cap. Each direct link
uses one HTTP stream; the persisted **Connections per download** / `maxChunksPerDownload` setting
does not segment a file yet, so it does **not** implement JDownloader's plugin-specific
multi-chunk behavior.

For each real transfer, the worker:

1. reserves a target name and writes to its `.part` file;
2. records a URL fingerprint and, when provided, a remote validator beside the partial;
3. sends a Range request from the existing partial length when possible and restarts safely if
   the server declines or invalidates range resumption; and
4. moves the completed partial file to the final name atomically where the filesystem supports
   atomic moves (with a normal move fallback where it does not).

File-exists behavior is resolved by the worker, not a modal prompt. The default Ask setting is
intentionally mapped to safe auto-rename; Rename, Skip, and Overwrite also proceed without
asking the user to dismiss anything.

| Interface | Current direct-engine behavior | Future JDownloader-core concept |
|---|---|---|
| start() / pause(boolean) / stop() | Starts, pauses/resumes, or cancels direct transfer workers while preserving queued work and partial files. | DownloadWatchDog controls |
| forceStart(links) | Requeues selected non-finished direct links and starts the scheduler. | DownloadWatchDog.forceDownload(...) |
| removeDownloads(items) | Cancels matching workers and removes package/link rows. | DownloadController.removePackage/removeChildren(...) |
| reconnect() | Updates the current UI reconnect state only; it does not reconnect a router or host connection. | Reconnecter.forceReconnect() |

## Local state recovery

DirectHttpEngine writes a debounced, best-effort local journal to
~/.jdownloader-material/state.properties (or to the directory supplied to its test/portable
constructor). The journal includes non-secret settings, Downloads packages/links, and
LinkGrabber packages/links. A link that was running or paused when the process ended is restored
as queued; when it is started again, its existing .part file can be resumed if the server
supports byte ranges.

The ordinary local journal deliberately excludes My.JDownloader email and password. Those
optional credentials are preserved only through the separately encrypted .jdmbackup
export/import flow.

## Global state (observable)

| Interface | Shipped direct-engine behavior | Future JDownloader-core concept |
|---|---|---|
| runningProperty() / pausedProperty() | Direct queue scheduler state. | DownloadWatchDog state machine |
| globalSpeedProperty() | Sum of active direct-transfer speeds. | DownloadWatchDog speed manager |
| runningCountProperty() | Number of running direct links. | Active SingleDownloadController count |
| totalRemainingProperty() | Sum of known remaining direct bytes. | Sum of unfinished bytes |
| reconnectingProperty() | UI reconnect indicator; not an actual network reconnect. | Reconnecter progress |

## Settings

DirectHttpEngine currently applies the direct-download folder, simultaneous-download limit,
per-host connection limit, global speed limit, file-exists policy, LinkGrabber auto-confirm /
auto-start behavior, ordering, and appearance settings. Other Settings pages are UI/configuration
surfaces rather than proof of JDownloader-core parity. In particular, optional My.JDownloader
credentials do not connect to a remote-control service in this release.

## Threading contract

Network probing, HTTP streaming, state-file writing, backup encryption, and disk I/O run outside
the JavaFX Application Thread. JavaFX observable-model updates are marshalled back to that thread
with Platform.runLater; views bind directly to those properties. This keeps URL submission,
queue controls, backup fields, and collision handling responsive without a blocking dialog.

A future JDownloader adapter must preserve the same boundary: backend callbacks may arrive on
background threads, but every mutation observed by the JavaFX UI must arrive on the JavaFX
Application Thread.
