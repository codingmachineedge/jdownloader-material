# Getting started

## Install on Windows

The [latest GitHub release](https://github.com/Ding-Ding-Projects/jdownloader-material/releases/latest)
contains the self-contained `JDownloader-Material-windows-x64.exe`. Java 25 is bundled. The package
is currently unsigned, so Windows SmartScreen may show its normal warning.

## Run from source

`run.cmd` locates JDK 25 through `JAVA_HOME`, `PATH` or the project-local `.jdk` directory and
provisions Temurin 25 when necessary. The first provisioned run needs internet access. With JDK 25
already available, run `.\mvnw.cmd javafx:run`.

## Add a direct download

1. Choose **Add Links** in the global toolbar.
2. Paste one or more direct `http://` or `https://` file URLs.
3. Optionally choose a package name and destination.
4. Choose **Add** to stage the URLs in LinkGrabber, or **Add & start** to probe, confirm and start
   them in the background.

The engine follows redirects, probes metadata, writes an active transfer to a `.part` file and
resumes it when the server supports byte ranges. Start, Pause/Resume and Stop always control the
direct scheduler.

## Navigate and search

Rail destinations open or focus browser-style workspace tabs. Use New Tab for Notifications,
Changelog and optional installed-JDownloader pages. Tabs can be reordered, pinned and grouped; the
overflow menu never silently drops one. Current-strip, per-group, group-name and master searches
all have adjacent bounded RE2/J builders. Bulk close previews titles and protects pinned/unsaved
tabs.

## Optional integrations

General Settings can detect a Windows editor and configure direct folder/file launch. Connection
Settings holds the installed-JDownloader loopback URL, defaulting to `http://127.0.0.1:3128`.
Only strict local loopback hosts are accepted; the app does not use My.JDownloader cloud. If no
local API is listening, stock pages report a non-blocking failure while direct downloads keep
working.

## Local data

Restart state, appearance, notification records and append-only transfer/settings/workspace Git
history live below `~/.jdownloader-material/`. History retains direct URLs—including signed query
parameters—so treat that directory as private. Credentials, downloaded contents and `.part`
contents are excluded.

Next: [Interface](Interface) · [Architecture](Architecture)
