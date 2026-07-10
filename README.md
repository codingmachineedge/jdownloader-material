# JDownloader Material

JDownloader Material is a JavaFX + [MaterialFX](https://github.com/palexdev/MaterialFX)
desktop downloader inspired by [JDownloader 2](https://jdownloader.org/), styled with
Material Design 3 in both light and dark themes.

Normal launches use `DirectHttpEngine` to download **direct HTTP and HTTPS files**. URLs are
probed in the background, streamed to resumable `.part` files, retried after transient network or
server failures when recovery is enabled, and finalized into their target names without a blocking
confirmation dialog. It is **not** a distribution of, or adapter to,
the JDownloader core: JDownloader plugins, containers, accounts, CAPTCHA handling, and the full
hoster ecosystem are not bundled. The UI talks to a small [engine boundary](docs/ENGINE_API.md)
so a future core integration can be added without rewriting the views.

- **Language:** Java 25 (Temurin), JavaFX 25
- **UI:** JavaFX 25, MaterialFX components, hand-authored Material 3 stylesheet
- **Themes:** Material light + dark, switchable at runtime from the app bar
- **Engine:** a swappable [DownloadEngine](src/main/java/org/jdownloader/material/engine/DownloadEngine.java)
  interface; normal launches use `DirectHttpEngine`, while `SimulatedEngine` is reserved for
  deterministic screenshot capture

## Visual tour

The gallery is captured from the running application. The [UI guide](docs/UI_GUIDE.md)
explains the shell, views, nonblocking flows, and repeatable capture mode.

| Downloads light | Downloads dark |
| --- | --- |
| ![Downloads in the light theme](docs/screenshots/downloads-light.png) | ![Downloads in the dark theme](docs/screenshots/downloads-dark.png) |

| Selected item light | Selected item dark |
| --- | --- |
| ![Inline queued-download properties in the light theme](docs/screenshots/downloads-properties-light.png) | ![Inline queued-download properties in the dark theme](docs/screenshots/downloads-properties-dark.png) |

| LinkGrabber light | LinkGrabber dark |
| --- | --- |
| ![LinkGrabber in the light theme](docs/screenshots/linkgrabber-light.png) | ![LinkGrabber in the dark theme](docs/screenshots/linkgrabber-dark.png) |

| Settings light | Settings dark |
| --- | --- |
| ![Settings in the light theme](docs/screenshots/settings-light.png) | ![Settings in the dark theme](docs/screenshots/settings-dark.png) |

| Add Links light | Add Links dark |
| --- | --- |
| ![Inline Add Links composer in the light theme](docs/screenshots/add-links-light.png) | ![Inline Add Links composer in the dark theme](docs/screenshots/add-links-dark.png) |

## Current capabilities

The front-end screens are interactive. [docs/PARITY.md](docs/PARITY.md) records the
implemented surface and remaining upstream work; [docs/FEATURE_SPEC.md](docs/FEATURE_SPEC.md)
is the upstream reference inventory, not a claim of full core compatibility.

- **Downloads** - package-to-file tree table with Name, Size, Host, Status, Progress, Speed,
  ETA, and inline Details; Material status chips and progress bars; Add Links, Start, Pause, Stop, ordering,
  Remove, live search, and a context menu. A single queued/error/disabled link—or a package only
  when every child is in one of those safe states—can be renamed or retargeted in an inline strip;
  active and completed output paths are intentionally read-only.
  Direct HTTP(S) files stream to disk for normal runs.
- **LinkGrabber** - staging tree table with asynchronous direct-URL metadata probes, Paste,
  Remove, Add to Downloads, and an inline availability filter (All, Checking, Online, Offline).
- **Inline Add Links composer** - paste one or more direct HTTP(S) URLs, optionally name the
  package and set a destination, then choose **Queue in LinkGrabber** or **Queue & Start**.
  Parsing, availability checks, automatic confirmation, and starting are deferred so the user
  can keep navigating. Unsupported-only input stays in the composer with an inline result rather
  than being discarded.
- **Direct HTTP(S) transfers** - redirect-aware metadata probes; background streaming;
  resumable `.part` files when the server supports byte ranges; atomic finalization where the
  filesystem supports it; global and per-host transfer limits; a global speed cap; and bounded,
  inline automatic retry for transient HTTP/network failures.
- **Nonblocking file collisions** - the default "Ask" policy safely auto-renames instead of
  showing a prompt. Skip, overwrite, and rename policies are selected in Settings and applied by
  the worker, not by a dialog. Completed rows retain their resolved file path and offer
  nonblocking **Open completed file** and **Show in folder** actions.
- **Local state recovery** - settings (excluding remote-control credentials), the Downloads
  queue, and LinkGrabber staging data are journaled under `~/.jdownloader-material/`. In-progress
  transfers recover as queued on restart and reuse their `.part` file when started again.
- **Nonblocking feedback** - ordinary transfer actions expose their state in the view and status
  bar. A compact transient message is reserved for navigable or reversible results such as
  **View** and **Undo**; it never asks the user to dismiss a workflow-blocking prompt.
- **Settings** - General, Connection, network-recovery retry, LinkGrabber, Appearance,
  backup compatibility fields, Backup, and About. Router reconnect and remote control remain
  clearly unavailable in direct HTTP mode; an account is not required to download.
- **Encrypted settings backup** - inline export/import fields run file and cryptographic work
  asynchronously, preserving access to the rest of the application.
- **App bar and status bar** - clipboard monitoring, automatic transient-failure retry,
  light/dark switch, window controls, global speed, running count, remaining bytes, and a real
  pending-retry indicator.
- **Screenshot capture engine** - `SimulatedEngine` supplies deterministic sample rows and fake
  progress only when the opt-in documentation screenshot path is used. It is not the normal downloader.

## Installer releases

Every push builds and publishes a new GitHub release with self-contained native
installers for Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. The installers
include a Java 25 runtime, so users do not need to install Java or Maven separately.
Each matrix build uploads its installer directly to that published release—no GitHub Actions
artifacts are created or retained. The final workflow check keeps exactly those four installer
uploads as release assets and removes an incomplete release if a platform build fails.

- [Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest)
- [Windows x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-windows-x64.exe)
- [Linux x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-linux-x64.deb)
- [macOS Apple Silicon installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-arm64.dmg)
- [macOS Intel installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-x64.dmg)

The release tag and About page include the generated build version. Windows and macOS packages
are currently unsigned, so SmartScreen or Gatekeeper may display a security warning.

## Building and running

**Zero-setup (recommended)** - no Java or Maven required:

~~~sh
run.cmd        # Windows
./run.sh       # Linux / macOS
~~~

The scripts find a JDK 25+ (JAVA_HOME, PATH, or a previously provisioned .jdk/). If none
exists, they download Eclipse Temurin 25 from the Adoptium API into a project-local .jdk/
folder, then build and launch through the Maven Wrapper. First run needs an internet connection;
supported platforms are Windows, macOS, and Linux on x64/arm64.

**With your own toolchain** (JDK 25+, Maven 3.9+):

~~~sh
mvn javafx:run       # build and launch
mvn compile          # compile only
mvn package          # build a jar
~~~

MaterialFX and JavaFX are resolved from Maven Central automatically.

## Settings backup (encrypted, secrets included)

Settings -> **Backup** exports every setting, including optional My.JDownloader credentials,
to a .jdmbackup file and restores the full configuration on another machine. The file is
encrypted with **AES-256-GCM** under a key derived from the chosen passphrase
(PBKDF2-HmacSHA256, 210k iterations, random salt). The backup page keeps path and passphrase
inputs inline and performs export/import work asynchronously; nothing is written in plaintext.

## Project layout

~~~text
src/main/java/org/jdownloader/material/
  app/       Application entry point (JDMaterialApp, Launcher)
  model/     DownloadItem/Link/Package, CrawledLink/Package, states
  engine/    DownloadEngine interface, DirectHttpEngine, SimulatedEngine, AppStateStore, Settings
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, AddLinksView, SettingsView
  ui/component/ Mat, DownloadCells, StatusBar, NotificationCenter
src/main/resources/css/
  theme-light.css / theme-dark.css   Material 3 color tokens
  material.css                       component stylesheet
docs/        UI_GUIDE, FEATURE_SPEC, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
~~~

## Relationship to JDownloader

This project has its own small direct-file downloader, not the JDownloader core. It can retrieve
ordinary direct HTTP(S) files, but it does not ship JDownloader's crawler, hoster/plugin
ecosystem, container formats, Account Manager, CAPTCHA flow, or remote-control backend. No
JDownloader-core adapter exists in this repository yet. [docs/ENGINE_API.md](docs/ENGINE_API.md)
documents both the shipped direct engine and the intended future core integration boundary.

## License

Intended to track upstream JDownloader licensing. The JDownloader name and core are the work
of AppWork GmbH; this is an independent front-end experiment.
