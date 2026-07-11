# Engine API

The desktop views use
[DownloadEngine](../src/main/java/org/jdownloader/material/engine/DownloadEngine.java) as their
direct-download contract. Normal application launches create `DirectHttpEngine`, which handles
real HTTP(S) URLs. `SimulatedEngine` supplies deterministic in-memory sample data only for the
documentation capture path.

| Implementation | Used by | Behavior |
| --- | --- | --- |
| `DirectHttpEngine` | Normal launch | Direct URL probing, queueing, streaming, resume, bounded retry, restart recovery, and embedded local-Git history. |
| `SimulatedEngine` | Documentation capture | Stable sample rows, fake progress, and in-memory history. |

## Observable model

`downloadPackages()` exposes the direct-download queue as an observable package-to-link tree.
`crawledPackages()` exposes staged direct URLs and their asynchronous probe results. Views bind to
these lists and their JavaFX properties; they do not poll transfer workers.

A `DownloadLink` includes queued name, host, URL, destination, resolved output path,
byte/progress state, priority, and retry data. A `CrawledLink` includes staged name, host, URL,
size, and availability; its shared destination belongs to `CrawledPackage`.

## LinkGrabber operations

| API | Behavior |
| --- | --- |
| `addLinks(text, packageName, destination, autoConfirm, autoStart)` | Filters direct HTTP(S) URLs outside the UI thread, stages them, and begins background metadata probes. The returned result resolves with the accepted-input summary while later probe/confirmation work continues. |
| `confirmToDownloads(packages, autoStart)` | Moves online staged package links into the Downloads queue. |
| `confirmLinksToDownloads(links, autoStart)` | Moves selected online staged links while retaining their package siblings. |
| `confirmAll(autoStart)` | Moves every online staged link to Downloads. |
| `removeCrawled(...)` / `removeCrawledLinks(...)` | Removes staging packages or selected staged links. |

Probing tries HEAD first and falls back to a ranged GET when metadata requires it. Redirects are
followed. A successful response can update availability, filename, and size. Submission returns
immediately; auto-confirm and auto-start continue after the asynchronous probe result.

## Download operations

| API | Behavior |
| --- | --- |
| `start()` / `pause(boolean)` / `stop()` | Starts, pauses/resumes, or stops the direct queue while preserving queued work and partial files. |
| `startLinks(links)` / `stopLinks(links)` | Starts or stops only the selected direct links. |
| `forceStart(links)` | Requeues eligible links and starts the scheduler. |
| `setEnabled(links, enabled)` | Enables or disables selected queue links without a confirmation prompt. |
| `setPriority(links, priority)` | Saves a direct-link queue priority; higher priorities enter the scheduler first. |
| `removeDownloads(items)` | Cancels matching workers and removes package/link rows. |

The scheduler runs on JavaFX pulses and admits queued links under the configured global
simultaneous-download and per-host limits. Transfer I/O runs on background workers and honors the
global speed cap. Each direct link uses one safe HTTP stream.

For each transfer, a worker:

1. reserves a target name and writes to a `.part` file;
2. records a URL fingerprint and any available remote validator beside the partial;
3. asks for the remaining range when partial bytes exist, restarting safely when the server
   declines or invalidates resumption; and
4. moves the completed partial to the final name atomically where supported, with a normal move
   fallback, then retains the resolved path for file actions.

When Network recovery is enabled, direct engine workers retry network failures and HTTP 408, 429,
and 5xx responses with capped 2/4/8/16-second backoff. The queue item and its partial data remain
in place while Details shows the countdown. Other transfer failures remain visible as Error rows.

File-exists behavior is resolved by the worker rather than a modal prompt. The internal default
`ASK` value is presented as **Auto-rename (no prompt)** and chooses a safe name; Auto-rename, Skip,
and Overwrite continue without interrupting the batch.

For one queued, error, or disabled link, the Downloads page presents inline name and destination
fields. A package editor applies only when all of its children are in those safe states. Running,
paused, and finished output locations remain stable while a worker owns a stream or a completed
file.

## Restart journal

`DirectHttpEngine` writes a debounced local journal to
`~/.jdownloader-material/state.properties` (or a supplied portable/test directory). The journal
contains non-secret settings, Downloads packages/links, and LinkGrabber packages/links. A link
that was running or paused at exit returns as queued after restart; a later start can reuse
existing `.part` bytes when the server supports ranges. Priority, retry state, resolved output
path, and queued inline name/destination changes persist too.

## Local append-only History API

`history()` returns the observable `HistoryService`. `recordHistory(scope, summary)` captures one
completed semantic model change on the JavaFX thread and commits it asynchronously. History uses
asynchronous operations for `undo()`, `redo()`, and `restore(entryId)`; each operation
applies a full model snapshot on the JavaFX thread and appends a new `UNDO`, `REDO`, or `RESTORE`
event.

`DirectHttpEngine` uses bundled JGit to maintain private repositories under
`~/.jdownloader-material/history/`:

- `settings` contains canonical non-secret `settings.properties` snapshots;
- `download-lists` contains canonical `downloads.properties` and `linkgrabber.properties`;
- `manifest` holds durable prepare records and completion metadata linked to the matching commits.

The repositories retain an append-only timeline. Credential fields never enter a history snapshot.
Direct-link URLs remain for a faithful restore, so these local-only repositories are private device
data with no configured remote. Completed files and `.part` contents stay out of history. Active
telemetry is omitted while final byte/path/outcome metadata stays with completed or failed rows.
The manifest sequence can finish a durable prepared entry after a process interruption between
repositories.

## Global state

| Property | Meaning |
| --- | --- |
| `runningProperty()` / `pausedProperty()` | Direct queue scheduler state. |
| `globalSpeedProperty()` | Sum of active direct-transfer speeds. |
| `runningCountProperty()` | Number of running direct links. |
| `totalRemainingProperty()` | Sum of known remaining direct bytes. |
| `retryScheduledProperty()` | True while one or more transient direct-download retries are scheduled. |

## Settings and threading

The direct engine applies download folder, simultaneous-download limit, per-host limit, global
speed limit, file-exists policy, transient retry, LinkGrabber auto-confirm/auto-start behavior,
ordering, and presentation settings. Presentation offers English, playful Hong Kong Cantonese, and
bilingual English / Hong Kong Cantonese.

Network probes, HTTP streaming, journal writes, backup encryption, History Git work, and disk I/O
run outside the JavaFX Application Thread. JavaFX observable-model updates and history snapshot
capture/restoration return through `Platform.runLater`. This keeps URL submission, queue controls,
primary navigation, History, and backup work responsive without a blocking dialog.
