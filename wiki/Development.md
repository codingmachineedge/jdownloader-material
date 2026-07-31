# Development

## Prerequisites and launch

Use JDK 25 and the Maven Wrapper. The zero-setup launchers can provision Temurin 25 automatically.

```powershell
.\run.cmd
.\mvnw.cmd clean package
.\mvnw.cmd javafx:run
```

The application entry point is `org.jdownloader.material.app.Launcher`. Normal direct downloads
select `DirectHttpEngine`; optional installed-JDownloader pages use their own strict-loopback client.

## Compile and run smoke checks

`mvn test` compiles the manual smoke classes but they are plain `main` programs, not Surefire/JUnit tests. Discover and run every desktop smoke explicitly so a new feature check cannot be omitted from a maintained list:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile
$testRoot = (Resolve-Path "src/test/java").Path
$smokes = Get-ChildItem -LiteralPath $testRoot -Recurse -File -Filter "*Smoke.java" |
  Where-Object { $_.FullName -notmatch "[\\/]tui[\\/]" } |
  ForEach-Object {
    [IO.Path]::GetRelativePath($testRoot, $_.FullName) `
      -replace "\.java$", "" -replace "[\\/]", "."
  } | Sort-Object -Unique
foreach ($smoke in $smokes) {
  .\mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
    "-Dexec.mainClass=$smoke" "-Dexec.classpathScope=test"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
```

JavaFX-backed smokes need a graphical desktop session. Together they cover the primary views,
workspace tabs/groups/search/bulk-close, anchored regex and appearance surfaces, notification/
changelog/dim-sum UI, named actions and bilingual compact Add Links. The release workflow runs the
discovered non-TUI set under Ubuntu/Xvfb with software rendering before it creates a release draft.

## UI smoke checklist

Before handing off an interface change, run the manual-smoke set, capture the gallery to a temporary
directory, and review at least Downloads light/dark/bilingual/narrow, Add Links bilingual,
Appearance bilingual, Notifications, Changelog, installed-JDownloader Plugins and Dim Sum. Confirm
that bilingual state chips show both lines, `ETA` remains compact, and no drawer/popover text is cut
off. The static Pages site must retain its eight discrete tabs, complete tab manager and four
independent discovery searches; bounded builders; persisted language/tone, notifications and local
dim sum; the M3 appearance/profile workbench; eleven feature articles; keyboard roles; 560 px
layout; reduced motion and iframe focus handoff. See the
[UI smoke handoff](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/UI_SMOKE.md)
for the current evidence and exact scope.

Run the Pages guard with any Node.js 20+ runtime:

```powershell
node .\site\smoke-check.mjs
```

## Refresh the gallery

Documentation capture opts into `SimulatedEngine`, seeds deterministic data, defines all 26 scenes
and exits. From PowerShell:

```powershell
$env:JD_SCREENSHOT_DIR = (Resolve-Path "docs/screenshots").Path
.\mvnw.cmd javafx:run
Remove-Item Env:JD_SCREENSHOT_DIR
```

Review every generated image in [`docs/screenshots`](https://github.com/Ding-Ding-Projects/jdownloader-material/tree/main/docs/screenshots),
then update the README, detailed docs, wiki source and Pages content together when interface behavior
or visuals change.

## Preview GitHub Pages

Pages publishes the static `site/` directory. A simple local preview is:

```powershell
python -m http.server 8000 --directory site
```

Open `http://localhost:8000/` and verify the overview and interactive demo. See [Releases](Releases) for the `main`-only publication flow.
