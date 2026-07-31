# Windows desktop verification matrix — 2026-07-30

This document defines the current deterministic UI verification surface. `SimulatedEngine` supplies
scene data and screenshot capture; it does not submit real downloads, modify a person's normal app
profile or contact an installed JDownloader instance. Exact final run results belong in
[HANDOFF.md](../HANDOFF.md), so this matrix never predicts a check that has not completed.

## Scope and coverage

| Surface | Automated/current capture coverage |
| --- | --- |
| Downloads and LinkGrabber | Direct-engine smokes plus light/dark/localized captures; properties search and global `SearchSpec` integration. |
| Workspace | Open/select/close/reorder/pin/group persistence, four independent tab searches, overflow and guarded containing/inverse bulk close. |
| Search | RE2/J valid/invalid/Unicode/multiline/dotall/zero-width/capture/adversarial/limit cases plus anchored builder UI. |
| Appearance | Profile/schema/import/export/preset/color/contrast tests and headful live-target/editor/picker/context/keyboard checks. |
| History and Settings | Append-only transfer/settings history, workspace history, all Settings searches and persisted language/funny/integration preferences. |
| Experience | Notification stack/history, changelog filtering/copy/export, exact 1% dim-sum policy and real overlay capture. |
| Integrations | External-editor command parsing/detection/launch surfaces and isolated-loopback installed-JDownloader client/stock-page coverage. |
| Add Links | Drawer role/name, initial focus, wrapping, bounded layout, draft preservation and Escape dismissal. |
| Pages | Eight discrete browser-style tabs; reorder/pin/group/collapse/manager/bulk close; four independent tab searches plus Settings/editor/article searches; bounded ECMAScript builders; three language modes and 1/1 funny defaults; notifications, local dim sum, M3 appearance/profile tools, eleven feature articles, reduced motion and canonical links. |

## Required local commands

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

node .\site\smoke-check.mjs

$env:JD_SCREENSHOT_DIR = (Resolve-Path "docs/screenshots").Path
.\mvnw.cmd javafx:run
Remove-Item Env:JD_SCREENSHOT_DIR
```

JavaFX-backed smokes require a graphical session. CI uses Ubuntu/Xvfb and software rendering only as
the verification host; the released product and installer are Windows x64.

## Gallery contract

The capture harness defines 26 deterministic scenes:

- Downloads: light, status, properties-light, dark, properties-dark, Cantonese, bilingual and
  bilingual-narrow;
- LinkGrabber: light, dark and Cantonese;
- History: light, dark and bilingual;
- Settings: General light/dark and Appearance light/dark/bilingual;
- Add Links: light, dark and bilingual;
- Changelog light, installed-JDownloader Plugins light, Notifications bilingual and Dim Sum light.

Before handoff, refresh the checked-in assets and inspect the exact new surfaces. A source-defined
capture name is not visual proof until the PNG is generated from the current built app and reviewed.
For every visible fixed issue, use that issue's own precisely framed capture rather than reusing a
whole-window gallery image.

The final 2026-07-30 run generated all 26 scenes in `target/m3-captures-4`; each checked-in copy in
`docs/screenshots` was matched to its source by SHA-256. The reviewed set totals 1,939,999 bytes.
Visual inspection confirmed consistent Material 3 controls, a clean 880×560 bilingual layout, the
complete bilingual Add Links actions, separated Changelog heading/filter rows, an isolated
bottom-left dim-sum card and unobscured bilingual notification history.

The Pages guard passed 155 assertions with eleven substantive in-site feature articles and four
decoded, release-catalog-identical local dim-sum images. Script syntax checks passed. Off-screen
Chrome exercised Settings, persisted bilingual mode, first-run disclosure/dismissal, notification
toast/count, the appearance workbench, independent regex builders with adversarial rejection, the
tab manager and a 560 px responsive layout. Browser font enumeration is not exposed by the
platform; the workbench discloses that limitation and supplies free-entry local families,
searchable known choices and CJK-safe fallback.

## Manual visual/accessibility pass

- Exercise the workspace and every anchored popover at 100%, 125%, 150% and 200% Windows display
  scale, at the narrow supported size and in bilingual mode.
- Confirm pinned/regular overflow, collapsed groups, tab/group context menus, keyboard activation,
  focus return and unsaved-work close protection.
- Confirm search/builders remain adjacent and independent, invalid input stays visible, samples do
  not persist and zero-width matches cannot hang the UI.
- Confirm all appearance controls apply live, pickers customize themselves, unsupported properties
  remain visible, continuous colors retain alpha/gamut warnings and reset/import/export are safe.
- Confirm toast stacking/timeouts/persistence, changelog date typing/calendar/presets/export, dim-sum
  focus preservation/reduced motion and bridge/editor failure notifications.
- Confirm the tabbed Pages site never falls back to a single long-scroll document and remains
  keyboard-operable at narrow widths with reduced motion.

## Evidence rules

A compile-only result is not a passing smoke. `mvn test` alone is insufficient because these checks
are standalone `main` classes. A mockup, old screenshot, predicted Actions result or installer build
without the discovered smoke gate is not verification. Record every command, exit code, smoke class,
assertion count and current-build capture in the final handoff.
