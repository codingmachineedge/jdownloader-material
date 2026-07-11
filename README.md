# JDownloader Material

<p align="center"><img src="src/main/resources/icons/app.png" alt="JDownloader Material indigo download-and-link logo" width="132"></p>

JDownloader Material is a purpose-built JavaFX download workspace for direct HTTP(S) files. It
combines a responsive Material Design 3 desktop interface with a real background transfer engine:
paste URLs, inspect them in LinkGrabber, queue downloads, and keep working while probes, disk I/O,
retry scheduling, and history writes run in the background.

The indigo download-and-link mark above is the application icon used by the desktop app. Normal
launches use `DirectHttpEngine`; `SimulatedEngine` is used only by the repeatable documentation
capture mode.

- **Runtime:** Java 25 (Temurin) and JavaFX 25
- **UI:** JavaFX, MaterialFX, and a hand-authored Material 3 stylesheet
- **Languages:** English, playful Hong Kong Cantonese, or bilingual English / Hong Kong Cantonese
- **Themes:** light and dark, switched immediately from the app bar
- **Download scope:** direct HTTP and HTTPS URLs, including resumable `.part` transfers

## Visual tour

The gallery is captured from the running application. [UI guide](docs/UI_GUIDE.md) explains the
workspace shell, direct-transfer flow, and nonblocking interaction pattern; [History Manager](docs/HISTORY.md)
documents the local append-only timeline.

| Downloads light | Downloads dark |
| --- | --- |
| ![Downloads in the light theme](docs/screenshots/downloads-light.png) | ![Downloads in the dark theme](docs/screenshots/downloads-dark.png) |

| Fixed status feedback |
| --- |
| ![Downloads showing fixed in-layout activity feedback](docs/screenshots/downloads-status-light.png) |

| Hong Kong Cantonese | Bilingual English / Hong Kong Cantonese |
| --- | --- |
| ![Downloads in playful Hong Kong Cantonese](docs/screenshots/downloads-cantonese.png) | ![Downloads in bilingual English and Hong Kong Cantonese](docs/screenshots/downloads-bilingual.png) |

| Hong Kong Cantonese LinkGrabber | Bilingual Add Links |
| --- | --- |
| ![LinkGrabber in playful Hong Kong Cantonese](docs/screenshots/linkgrabber-cantonese.png) | ![Add Links in bilingual English and Hong Kong Cantonese](docs/screenshots/add-links-bilingual.png) |

| English language setting | Bilingual language setting |
| --- | --- |
| ![Appearance settings showing the English selector](docs/screenshots/settings-appearance-light.png) | ![Appearance settings showing the bilingual selector](docs/screenshots/settings-appearance-bilingual.png) |

| Selected item light | Selected item dark |
| --- | --- |
| ![Inline queued-download properties in the light theme](docs/screenshots/downloads-properties-light.png) | ![Inline queued-download properties in the dark theme](docs/screenshots/downloads-properties-dark.png) |

| LinkGrabber light | LinkGrabber dark |
| --- | --- |
| ![LinkGrabber in the light theme](docs/screenshots/linkgrabber-light.png) | ![LinkGrabber in the dark theme](docs/screenshots/linkgrabber-dark.png) |

| History light | History dark | Bilingual History |
| --- | --- | --- |
| ![History Manager in the light theme](docs/screenshots/history-light.png) | ![History Manager in the dark theme](docs/screenshots/history-dark.png) | ![History Manager in bilingual English and Hong Kong Cantonese](docs/screenshots/history-bilingual.png) |

| Settings light | Settings dark |
| --- | --- |
| ![Settings in the light theme](docs/screenshots/settings-light.png) | ![Settings in the dark theme](docs/screenshots/settings-dark.png) |

| Add Links light | Add Links dark |
| --- | --- |
| ![Inline Add Links composer in the light theme](docs/screenshots/add-links-light.png) | ![Inline Add Links composer in the dark theme](docs/screenshots/add-links-dark.png) |

| Workspace tabs light | Workspace tabs dark | Bilingual workspace tabs |
| --- | --- | --- |
| ![Browser-style workspace tabs in the light theme](docs/screenshots/workspace-tabs-light.png) | ![Browser-style workspace tabs in the dark theme](docs/screenshots/workspace-tabs-dark.png) | ![Browser-style workspace tabs in bilingual English and Hong Kong Cantonese](docs/screenshots/workspace-tabs-bilingual.png) |

## Workspace tabs

The shell behaves like a browser workspace: every open page has its own tab, and each tab owns a
single page instance. Open Downloads, LinkGrabber, History, Settings, or Add Links in another tab
when you want two independent places to work. A right-click on a tab opens its editor, where you
can:

- rename the tab;
- choose the tab-label font family and size;
- set bold, italic, and any color from the color picker; and
- close the tab or open a new page tab.

The application name is editable in the same workspace controls and updates the window/app-bar
identity. Open tabs, the selected tab, application name, and tab-label styling are kept in a
private local Git repository at `~/.jdownloader-material/workspace/`. Every workspace change adds
an append-only local commit. Closing a tab records its event while retaining its descriptor, so the
timeline remains complete.

Workspace controls also export a portable `.jdmtabs` snapshot, import that snapshot, and export a
ZIP of the complete local workspace repository. The repository export includes the append-only
tab history as well as the current workspace.

## Current capabilities

- **Downloads** — package-to-file tree with name, size, host, status, progress, speed, ETA, and
  inline details. Toolbar and right-click actions cover adding, starting, pausing, stopping,
  ordering, removing, enabling, priority, and completed-file actions. A selected safe queued item
  can be renamed or retargeted in an inline properties strip.
- **LinkGrabber** — staged direct URLs with asynchronous metadata probes, Paste, Remove,
  availability filtering, and direct confirmation into Downloads.
- **Inline Add Links** — submit one or more direct HTTP(S) URLs with a package name and
  destination. Queue in LinkGrabber and Queue & Start return immediately while background checks
  continue.
- **Direct transfers** — redirect-aware probing, background streaming, resumable `.part` files,
  atomic finalization where supported, global and per-host limits, speed limiting, collision
  policies, and bounded automatic retry for transient failures.
- **Fixed feedback** — recent clipboard, validation, and removal messages remain in the status
  line; work continues beneath them. Durable Undo and Redo live in History rather than a floating
  popup.
- **Local history** — Downloads, LinkGrabber, and non-secret settings are stored in separate
  append-only local Git timelines. Undo, redo, and restore add a new event instead of removing an
  older revision.
- **Settings and backup** — every displayed setting controls a live direct-download behavior or
  persisted presentation preference. Encrypted settings export/import performs cryptographic and
  file work asynchronously.
- **Localization** — English, playful Hong Kong Cantonese, and bilingual copy switch immediately
  and persist across restarts.

## Installer releases

Every push creates a published GitHub release containing self-contained native installers for
Windows x64, Linux x64, macOS Apple Silicon, and macOS Intel. Each package includes a Java 25
runtime, so users do not need Java or Maven installed. GitHub Actions uploads installers directly
to the release; the release contains installer assets only, with no retained workflow artifacts.
The final release check accepts exactly the four installer assets and removes an incomplete release
when a platform build fails.

- [Latest release](https://github.com/codingmachineedge/jdownloader-material/releases/latest)
- [Windows x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-windows-x64.exe)
- [Linux x64 installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-linux-x64.deb)
- [macOS Apple Silicon installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-arm64.dmg)
- [macOS Intel installer](https://github.com/codingmachineedge/jdownloader-material/releases/latest/download/JDownloader-Material-macos-x64.dmg)

The release tag and About page include the generated build version. Windows and macOS packages are
currently unsigned, so SmartScreen or Gatekeeper can display the platform's normal security notice.

## Building and running

**Zero-setup (recommended)** — no Java or Maven required:

~~~sh
run.cmd        # Windows
./run.sh       # Linux / macOS
~~~

The scripts find a JDK 25+ through `JAVA_HOME`, `PATH`, or a project-local `.jdk/` directory. When
needed, they provision Eclipse Temurin 25 through the Adoptium API, then build and launch through
the Maven Wrapper. First run needs an internet connection; Windows, macOS, and Linux on x64/arm64
are supported.

With an existing JDK 25+ and Maven 3.9+:

~~~sh
mvn javafx:run       # build and launch
mvn compile          # compile only
mvn package          # build a jar
~~~

## Local data and privacy

The app keeps its restart journal, local history repositories, and workspace repository under
`~/.jdownloader-material/`. They stay on the device and have no configured remote. History keeps
direct-link URLs so a restored list remains useful; treat the directory as private data. Completed
files and `.part` file contents are not copied into the history repositories.

## Project layout

~~~text
src/main/java/org/jdownloader/material/
  app/       Application entry point (JDMaterialApp, Launcher)
  model/     DownloadItem/Link/Package, CrawledLink/Package, states
  engine/    DownloadEngine, DirectHttpEngine, SimulatedEngine, state and settings
  workspace/ private append-only Git workspace tabs and portable export/import
  ui/        MainWindow, ThemeManager, Icons
  ui/view/   DownloadsView, LinkGrabberView, HistoryView, AddLinksView, SettingsView
  ui/component/ Mat, DownloadCells, StatusBar, ActivityStatus
src/main/resources/
  icons/app.png                       application logo
  css/theme-light.css / theme-dark.css Material 3 color tokens
  css/material.css                    component stylesheet
docs/        UI_GUIDE, HISTORY, PARITY, ARCHITECTURE, DESIGN_SYSTEM, ENGINE_API
~~~

## Project identity

JDownloader Material is an independently implemented direct HTTP(S) download workspace. The
JDownloader name and core are the work of AppWork GmbH; this project uses a focused desktop
experience and has its own engine and local data model.

## License

Intended to track upstream JDownloader licensing.
