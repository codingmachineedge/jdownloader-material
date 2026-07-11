# Getting started

## Install a release

The [latest GitHub release](https://github.com/codingmachineedge/jdownloader-material/releases/latest) contains self-contained installers for Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. Java is bundled. Windows and macOS packages are currently unsigned, so the operating system may show its normal security warning.

## Run from source

The bootstrap scripts locate JDK 25 through `JAVA_HOME`, `PATH`, or the project-local `.jdk/` directory and provision Temurin 25 when necessary:

```text
run.cmd        # Windows
./run.sh       # Linux or macOS
```

The first provisioned run needs internet access. With JDK 25 and Maven 3.9 already installed, run `mvn javafx:run`.

## Add a download

1. Choose **Add Links** in the global toolbar.
2. Paste one or more direct `http://` or `https://` file URLs.
3. Optionally choose a package name and destination.
4. Choose **Add** to stage the URLs in LinkGrabber, or **Add & start** to probe, confirm, and start them in the background.

The engine follows redirects, probes metadata, writes an in-progress transfer to a `.part` file, and resumes it when the server supports byte ranges. Start, Pause/Resume, and Stop always control the scheduler.

## Scope and local data

The app is deliberately focused on direct HTTP(S) downloads. A web page, host account, captcha flow, plugin URL, or My.JDownloader connection is not a supported input path. There is no user-facing workspace-tab workflow; Downloads, LinkGrabber, History, and Settings are the fixed destinations.

Restart state and append-only history live below `~/.jdownloader-material/`. History retains direct URLs—including signed query parameters—so treat that directory as private. Downloaded contents and `.part` contents are not committed to history.

Next: [Interface](Interface) · [Architecture](Architecture)
