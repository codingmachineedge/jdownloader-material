# JDownloader Material

JDownloader Material is a JavaFX + [MaterialFX](https://github.com/palexdev/MaterialFX)
front-end experiment inspired by [JDownloader 2](https://jdownloader.org/), styled with
Material Design 3 in both light and dark themes.

The repository currently ships a fully interactive in-memory SimulatedEngine. It is **not**
a distribution of, or adapter to, the JDownloader core. The UI talks to a small
[engine boundary](docs/ENGINE_API.md) so a future core integration can be added without
rewriting the views; that adapter is not included in this release.

![Downloads - light](docs/screenshots/downloads-light.png)
![Downloads - dark](docs/screenshots/downloads-dark.png)

- **Language:** Java 25 (Temurin), JavaFX 25
- **UI:** JavaFX 25, MaterialFX components, hand-authored Material 3 stylesheet
- **Themes:** Material light + dark, switchable at runtime from the app bar
- **Engine:** a swappable [DownloadEngine](src/main/java/org/jdownloader/material/engine/DownloadEngine.java)
  interface, currently backed by the interactive SimulatedEngine

## Visual tour

The gallery is captured from the running application. The [UI guide](docs/UI_GUIDE.md)
explains the shell, views, nonblocking flows, and repeatable capture mode.

| Downloads light | Downloads dark |
| --- | --- |
| ![Downloads in the light theme](docs/screenshots/downloads-light.png) | ![Downloads in the dark theme](docs/screenshots/downloads-dark.png) |

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
  and ETA; Material status chips and progress bars; Add Links, Start, Pause, Stop, ordering,
  Remove, live search, and a context menu.
- **LinkGrabber** - staging tree table with deferred availability checks, Paste, Remove, and
  Confirm-to-Downloads.
- **Inline Add Links composer** - paste one or more URLs, optionally name the package and set
  a destination, then choose **Queue in LinkGrabber** or **Queue & Start**. Availability checks,
  automatic confirmation, and starting are deferred so the user can keep navigating.
- **Nonblocking feedback** - ordinary transfer actions expose their state in the view and status
  bar. A compact transient message is reserved for navigable or reversible results such as
  **View** and **Undo**; it never asks the user to dismiss a workflow-blocking prompt.
- **Settings** - General, Connection, Reconnect, LinkGrabber, Appearance, optional
  My.JDownloader remote-control credentials, Backup, and About. An account is not required to
  download.
- **Encrypted settings backup** - inline export/import fields run file and cryptographic work
  asynchronously, preserving access to the rest of the application.
- **App bar and status bar** - clipboard monitoring, automatic reconnect, reconnect-now,
  light/dark switch, window controls, global speed, running count, remaining bytes, and
  reconnect state.
- **Simulated engine** - schedules queued links up to the concurrency limit, advances progress,
  honors the global speed limit, supports real paused states, and simulates availability checks
  and reconnects. It is a UI/demo backend, not the JDownloader download pipeline.

## Installer releases

Every push to main builds and publishes a new GitHub release with self-contained native
installers for Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. The installers
include a Java 25 runtime, so users do not need to install Java or Maven separately.

- [Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest)
- [Windows x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-windows-x64.exe)

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
  engine/    DownloadEngine interface, SimulatedEngine, Settings
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, AddLinksView, SettingsView
  ui/component/ Mat, DownloadCells, StatusBar, NotificationCenter
src/main/resources/css/
  theme-light.css / theme-dark.css   Material 3 color tokens
  material.css                       component stylesheet
docs/        UI_GUIDE, FEATURE_SPEC, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
~~~

## Relationship to JDownloader

This project currently reimplements a portion of the **graphical front end** only. It does not
ship the JDownloader crawler, hoster/plugin ecosystem, or real download controller, and no
JDownloader-core adapter exists in this repository yet. [docs/ENGINE_API.md](docs/ENGINE_API.md)
documents the intended integration boundary and class mapping for future work.

## License

Intended to track upstream JDownloader licensing. The JDownloader name and core are the work
of AppWork GmbH; this is an independent front-end experiment.
