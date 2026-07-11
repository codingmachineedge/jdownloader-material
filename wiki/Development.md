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

`mvn test` compiles the manual smoke classes but they are plain `main` programs, not Surefire/JUnit tests. Run the current download, persistence, history, and localization checks explicitly from PowerShell:

```powershell
.\mvnw.cmd test-compile
$smokes = @(
  "org.jdownloader.material.i18n.LocalizationSmoke",
  "org.jdownloader.material.engine.history.GitHistoryServiceSmoke",
  "org.jdownloader.material.engine.DirectHistorySmoke",
  "org.jdownloader.material.engine.DirectHttpEngineSmoke"
)
foreach ($smoke in $smokes) {
  .\mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
    "-Dexec.mainClass=$smoke" "-Dexec.classpathScope=test"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

The JavaFX-backed smoke checks need a graphical desktop session.

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
