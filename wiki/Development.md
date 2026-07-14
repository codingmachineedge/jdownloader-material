# Development

## Prerequisites and launch

Use JDK 25 and the Maven Wrapper. The zero-setup launchers can provision Temurin 25 automatically.

```powershell
# Windows
.\run.cmd
.\mvnw.cmd clean package
.\mvnw.cmd javafx:run
```

```sh
# Linux or macOS
./run.sh
./mvnw clean package
./mvnw javafx:run
```

The application entry point is `org.jdownloader.material.app.Launcher`. Normal launches select `DirectHttpEngine`.

## Compile and run smoke checks

`mvn test` compiles the manual smoke classes but they are plain `main` programs, not Surefire/JUnit tests. Run the current download, persistence, history, localization, and JavaFX accessibility checks explicitly from PowerShell:

```powershell
.\mvnw.cmd test-compile
$smokes = @(
  "org.jdownloader.material.i18n.LocalizationSmoke",
  "org.jdownloader.material.workspace.GitWorkspaceStoreSmoke",
  "org.jdownloader.material.engine.history.GitHistoryServiceSmoke",
  "org.jdownloader.material.engine.DirectHistorySmoke",
  "org.jdownloader.material.engine.DirectHttpEngineSmoke",
  "org.jdownloader.material.ui.UiAccessibilitySmoke"
)
foreach ($smoke in $smokes) {
  .\mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
    "-Dexec.mainClass=$smoke" "-Dexec.classpathScope=test"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

`UiAccessibilitySmoke` needs a graphical desktop session. It mounts Downloads, LinkGrabber, History,
and Settings, checks named rail/icon actions and linked Settings labels, then verifies the bilingual
Add Links dialog at 880 × 560 px for focus, wrapping, dialog semantics, and Escape dismissal.

## UI smoke checklist

Before handing off an interface change, run the manual-smoke set, capture the gallery to a temporary
directory, and review at least Downloads light/dark/bilingual, Add Links bilingual, and Appearance
bilingual. Confirm that bilingual state chips show both lines, `ETA` remains a single compact header,
and no drawer text is cut off. The static Pages preview must keep a native compact menu below 1080 px,
retain visible state words below 640 px, preserve room for demo status text, and move focus to the
iframe page heading after a parent screen switch. See the
[UI smoke handoff](https://github.com/codingmachineedge/jdownloader-material/blob/main/docs/UI_SMOKE.md)
for the current evidence and exact scope.

Run the Pages guard with any Node.js 20+ runtime:

```powershell
node .\site\smoke-check.mjs
```

## Refresh the gallery

Documentation capture opts into `SimulatedEngine`, seeds deterministic data, writes all 21 scenes, and exits. From PowerShell:

```powershell
$env:JD_SCREENSHOT_DIR = (Resolve-Path "docs/screenshots").Path
.\mvnw.cmd javafx:run
Remove-Item Env:JD_SCREENSHOT_DIR
```

Review every image in [`docs/screenshots`](https://github.com/codingmachineedge/jdownloader-material/tree/main/docs/screenshots), then update the README, detailed docs, wiki source, and Pages content together when interface behavior or visuals change.

## Preview GitHub Pages

Pages publishes the static `site/` directory. A simple local preview is:

```powershell
python -m http.server 8000 --directory site
```

Open `http://localhost:8000/` and verify the overview and interactive demo. See [Releases](Releases) for the `main`-only publication flow.
