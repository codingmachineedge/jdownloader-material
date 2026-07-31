# CLAUDE.md

Guidance for Claude Code and other coding agents working in this repository.

## Scope

JDownloader Material is an independently implemented JavaFX Windows desktop app with its own direct
HTTP(S) engine. It does not embed upstream JDownloader core or use My.JDownloader cloud. Its
optional strict-loopback bridge can drive documented features of an installed local JDownloader
process.

The current product scope is the Windows desktop application. Do not edit, test, document, or run
the repository's TUI unless the user explicitly reopens that scope.

- Runtime: Java 25 (Temurin) and JavaFX 25
- Build: Maven Wrapper; a system Maven install is unnecessary
- UI: JavaFX, MaterialFX, and project-owned Material 3 styles
- Persistence: restart journal plus append-only local JGit history

## Build and launch

The zero-setup Windows launcher can provision a project-local JDK:

```powershell
.\run.cmd
```

With JDK 25 already available:

```powershell
.\mvnw.cmd compile
.\mvnw.cmd package
.\mvnw.cmd javafx:run
```

`mvn test` only compiles the desktop smoke classes. They are standalone `main` programs rather than
Surefire/JUnit tests. Compile and discover every current and future desktop smoke explicitly:

```powershell
.\mvnw.cmd test-compile
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
node .\site\smoke-check.mjs
```

JavaFX-backed checks need a graphical session. CI provides one with Xvfb and software rendering.

## Architecture boundaries

`Launcher` starts `JDMaterialApp`. Normal launches construct `DirectHttpEngine`; documentation
capture uses `SimulatedEngine`. Views depend on `DownloadEngine`, not a concrete implementation.

```text
app/             entry point and engine selection
appearance/      persisted global/per-target appearance profiles and color translation
changelog/       bundled factual release records and export
dimsum/          startup selection policy and dish catalog
model/           download and LinkGrabber data
engine/          direct-transfer contract, scheduler, settings, restart journal
engine/history/  append-only local history
i18n/            English, Cantonese, and bilingual resource lookup
integration/     external editor and installed-JDownloader loopback client
notification/    active toasts and bounded local history
search/          bounded RE2/J evaluation model
ui/              application shell and shared UI
ui/appearance/   target registry, anchored editor, font and color pickers
ui/search/       independent search field and anchored regex builder
ui/view/         downloads, LinkGrabber, history, settings, notifications, changelog
ui/workspace/    browser-style workspace strip and controls
workspace/       append-only JGit-backed tab/group structure
```

- Keep network, disk, backup, and Git work off the JavaFX Application Thread.
- Return observable-model changes through the existing JavaFX thread boundary.
- Preserve append-only restore semantics: restoring creates a new revision.
- Never put credentials, downloaded bytes, or `.part` contents in history.
- Treat retained direct-link URLs as private local data.
- Keep the installed-JDownloader client strict-loopback, redirect-free, bounded and free of stored
  passwords; destructive endpoints require scoped one-use confirmation.
- Keep plain text as the default search mode, preserve each field's independent builder state and
  route regex evaluation only through bounded RE2/J.
- Register new rendered controls with the appearance system and retain unsupported imported values
  instead of silently dropping them.
- Keep routine feedback non-blocking; reserve modal dialogs for a decision the user must make.
- Keep light/dark role tokens in the theme stylesheets and consume them from component styles.
- Add localized UI keys to both English and Cantonese resources; bilingual strings are composed at
  lookup time.

Read `docs/ARCHITECTURE.md`, `docs/ENGINE_API.md`, and `docs/README.md` before non-trivial work.

## Documentation and delivery

Keep these surfaces factual and synchronized:

- `README.md` for the project overview;
- `docs/` for categorized feature contracts and verification;
- `wiki/` for tracked GitHub wiki source;
- `site/` for GitHub Pages;
- `ROADMAP.md` and `HANDOFF.md` for remaining evidence and handoff state.

Keep implementation and release evidence distinct. Regenerate and review screenshots when a
visible desktop change lands.

`.github/workflows/release.yml` discovers and executes every non-TUI desktop `*Smoke.java` main under
Ubuntu/Xvfb, runs the Pages guard, validates the bundled dim-sum catalog, and only then stages
exactly one Windows x64 EXE plus one dim-sum photo on an immutable unique release tag.
`.github/workflows/pages.yml` runs the Pages guard before deployment. Both workflows support manual
dispatch; the release workflow also runs for every branch push while ignoring release-created tag pushes.

Git and GitHub operations use `git` and `gh`. A repository-changing task is not complete until the
intended work is committed, pushed to the default branch, remotely verified, and safely cleaned up.
