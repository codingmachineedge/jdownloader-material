# UI guide

JDownloader Material uses one Material 3 shell around every primary screen. The desktop window
does not show a second native title bar: its app bar owns window movement and window controls,
keeping the application visually consistent.

Normal launches use DirectHttpEngine. That means the ordinary flow can download direct HTTP and
HTTPS file URLs end to end; it does not mean the app includes JDownloader's hoster plugins,
containers, accounts, CAPTCHA handling, or full crawler.

## Shell

- **Top app bar** - identifies the application and holds clipboard monitoring, the
  automatic transient-failure retry setting, theme, minimize, maximize/restore, and close
  controls. Drag an unused portion of the bar to move the window; double-click it to maximize or
  restore it.
- **Navigation rail** - switches between Downloads, LinkGrabber, and Settings while preserving
  the shell and status bar. Add Links opens as its own inline view from the transfer screens.
- **Status bar** - reports global transfer speed, active download count, remaining bytes, and
  a pending automatic-retry indicator.
- **Light and dark themes** - switch from the app bar. Every surface, table, chip, and inline
  form resolves from the same theme tokens.

## Downloads

Downloads is a package-to-file tree with Name, Size, Host, Status, Progress, Speed, ETA, and
inline Details for a direct-transfer error or collision result.
Package rows aggregate their children. The toolbar holds the transfer and ordering actions, and
search filters the tree live. Start, pause, stop, and state changes appear directly in the table
and status bar rather than requiring acknowledgement.

For direct HTTP(S) files, the engine streams bytes in background workers. The scheduler honors the
global simultaneous-download setting and the per-host connection setting. Pause holds active
transfers without dequeuing them; Stop returns them to the queue and preserves any partial file
for a later start.

The row menu can enable or disable queued work and set a durable priority, and completed links
retain the exact resolved file path. **Open completed file** and **Show in folder** run outside the
UI thread, so a desktop file-manager action never blocks the queue.

Selecting exactly one queued, error, or disabled link reveals an inline properties strip below the
table. It can rename that item and set its next destination without opening a dialog. A package is
editable only when every child is in one of those safe states, then its changes apply to all of
them. Running, paused, and completed transfers remain read-only there, because their stream or
final filesystem location is already in use.

| Selected item light | Selected item dark |
| --- | --- |
| ![Queued item properties in light mode](screenshots/downloads-properties-light.png) | ![Queued item properties in dark mode](screenshots/downloads-properties-dark.png) |

| Light | Dark |
| --- | --- |
| ![Downloads light](screenshots/downloads-light.png) | ![Downloads dark](screenshots/downloads-dark.png) |

## LinkGrabber

LinkGrabber stages discovered direct URLs. After URLs are queued, DirectHttpEngine checks their
metadata asynchronously: it uses HEAD first and a ranged GET fallback, follows redirects, and
updates availability, filename, and known size when the server provides them. It supports adding
or pasting links, removing staged entries, and confirming online results to Downloads.
Its compact availability control cycles through **All links**, **Checking**, **Online**, and
**Offline** without opening a filter dialog.

The Auto-confirm setting lets a staged direct URL continue through confirmation, and Auto-start
begins those auto-confirmed results without additional user interaction. URLs that are offline or unsupported stay
out of the actual download queue; this release accepts only direct HTTP(S) URLs, not JDownloader
container files or plugin-managed hoster links.

| Light | Dark |
| --- | --- |
| ![LinkGrabber light](screenshots/linkgrabber-light.png) | ![LinkGrabber dark](screenshots/linkgrabber-dark.png) |

## Add Links: queue to download without a blocking prompt

**Add Links** is a normal content view, not a floating panel or modal dialog. Paste one or more
direct HTTP(S) URLs, optionally set a package name and destination, then choose one of two paths:

1. **Queue in LinkGrabber** adds the URLs with the submitted destination and returns immediately. Availability checking happens
   after submission while the user can navigate elsewhere.
2. **Queue & Start** queues the URLs, waits for the deferred availability check, then confirms
   the online results and starts downloading automatically.

The inline status confirms immediate background submission; the engine accepts only supported
direct HTTP(S) URLs. Clipboard monitoring follows the same nonblocking path, and the LinkGrabber
Auto-confirm and Auto-start settings can continue it without a confirmation prompt.
If background validation finds no supported URL, the pasted text stays in the composer for editing;
accepted submissions clear only the unchanged input snapshot, so a newer edit is never lost.

| Light | Dark |
| --- | --- |
| ![Add Links light](screenshots/add-links-light.png) | ![Add Links dark](screenshots/add-links-dark.png) |

## Transfers, partial files, and collisions

A real transfer writes to a final-name .part file. When a file is started again, the engine asks the
server for the remaining range; if the server supports it, the partial bytes are reused. If it
does not, the partial stream restarts safely. On success, the file is moved to its final name
atomically where the filesystem supports atomic moves.

There is no file-collision dialog in the path. The default **Ask** setting deliberately means
safe auto-rename in this app. **Rename**, **Skip**, and **Overwrite** are all applied by the
background worker, so an existing filename cannot interrupt a batch with a prompt.

When **Retry transient HTTP failures** is enabled, direct transfers automatically retry network,
408, 429, and 5xx failures with a bounded exponential backoff. The Details column shows the next
retry countdown; an unavailable file, filesystem problem, or exhausted retry budget remains an
inline Error row for an explicit Start or Force Start.

## Recovery after restart

DirectHttpEngine saves non-secret settings, Downloads packages/links, and LinkGrabber
packages/links in a local journal under ~/.jdownloader-material/. A running or paused transfer
is restored as queued after a restart because its live HTTP stream cannot survive process exit.
Starting it again can reuse its .part file. Optional My.JDownloader credentials are omitted
from this normal local journal; use the encrypted Backup page to move those credentials between
machines.

## Feedback and settings work

Normal actions do not open workflow-blocking overlays or acknowledgement dialogs. Visible state in
the table, inline composer, and status bar is the primary feedback. A compact transient message
may appear for a reversible or navigable result such as **Undo** or **View**; it does not block input.

The Backup page keeps export/import inputs inline. Encryption and disk work run asynchronously,
and completion or failure is shown in the page, so backup activity does not interrupt the
download workflow.

The recovery setting retries transient direct HTTP/network failures only. It does not attempt a
router, modem, or IP reconnect.

## Settings

Settings groups General, Connection, Network recovery, LinkGrabber, Appearance, backup-compatible
My.JDownloader fields, Backup, and About behind its own page rail. The
default download folder is an editable path field using the platform-native separator. No account
is required for direct HTTP(S) downloading. Router reconnect, remote control, and multi-connection
segmentation are labelled unavailable rather than silently pretending to run.

| Light | Dark |
| --- | --- |
| ![Settings light](screenshots/settings-light.png) | ![Settings dark](screenshots/settings-dark.png) |

## Regenerating the gallery

The application has an opt-in documentation capture mode. It selects SimulatedEngine, seeds
deterministic sample rows, renders ten scene snapshots, and exits when they are written.
Ordinary launches are unaffected and continue to select DirectHttpEngine.

From PowerShell with JDK 25 available:

~~~powershell
$env:JD_SCREENSHOT_DIR = (Resolve-Path "docs/screenshots").Path
.\mvnw.cmd javafx:run
~~~

This refreshes Downloads, LinkGrabber, Settings, and Add Links in both light and dark themes.
