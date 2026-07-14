# UI smoke handoff — 2026-07-13

This handoff records the deterministic UI verification completed for the current revision. It uses
`SimulatedEngine` for scene construction and screenshot capture; it does not submit real downloads,
write to a person's normal application profile, or call external transfer endpoints.

## Scope and coverage

| Surface | Evidence |
| --- | --- |
| Downloads | Light, dark, status feedback, queued-item properties, Cantonese, and bilingual gallery scenes; bilingual state-chip and compact `ETA` checks. |
| LinkGrabber | Light, dark, and Cantonese gallery scenes; mounted by the JavaFX route smoke. |
| History | Light, dark, and bilingual gallery scenes; mounted by the JavaFX route smoke. |
| Settings | General light/dark and Appearance light/dark/bilingual gallery scenes; every visible Settings row is checked for a label/control relationship. |
| Add Links | Light, dark, and bilingual gallery scenes; at 880 × 560 px, verifies dialog role/name, initial URL focus, wrapping status copy, bounded layout, and Escape dismissal. |
| Pages preview | Static responsive/a11y guard verifies the compact native menu, visible mobile state words, status-column room, `lang="yue"` fragments, and heading focus after parent-driven screen changes. |

## Checks run

| Check | Result |
| --- | --- |
| `./mvnw.cmd -B test` with the project JDK 25 | Passed. |
| `LocalizationSmoke`, `GitWorkspaceStoreSmoke`, `GitHistoryServiceSmoke`, `DirectHistorySmoke`, and `DirectHttpEngineSmoke` | Passed. |
| `UiAccessibilitySmoke` with software JavaFX rendering | Passed. It mounts all four primary destinations and verifies navigation names, Settings labels, bilingual state formatting, and the compact Add Links flow. |
| Static Pages responsive/a11y source guards | Passed. |
| Documentation gallery capture | Passed: 21 PNG files at 1440 × 900. |

The in-app browser could not navigate to the local preview because its URL policy blocks localhost in
this environment. The Pages checks therefore use static semantic guards; the JavaFX visual verdict is
based on deterministic `Scene` snapshots, not OS-level `PrintWindow` output.

## Defects fixed and verified

- Bilingual download state chips now use two lines instead of truncating in a compact cell.
- The `ETA` header remains one compact, international abbreviation in bilingual mode.
- Add Links status copy wraps and its fields, drawer, and dismissal behavior have explicit accessible
  semantics.
- Rail and icon-only controls have accessible names; Settings titles label their related controls and
  expose their supporting help.
- The static Pages header supplies a native compact navigation menu below 1080 px; mobile state labels
  remain textual; compact demo status cells retain space; and external screen switching transfers
  focus to the new iframe page heading.

## Gallery

The refreshed canonical assets live in [`docs/screenshots`](screenshots). The set has 21 scenes:
Downloads (7), LinkGrabber (3), History (3), Settings (5), and Add Links (3). Review the bilingual
Downloads and Add Links captures first when changing localization, table, or drawer layout.

## Reproduction

```powershell
$env:JAVA_HOME = (Resolve-Path ".\.jdk\jdk-25.0.3+9").Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -B test

.\mvnw.cmd org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
  "-Dexec.mainClass=org.jdownloader.material.ui.UiAccessibilitySmoke" `
  "-Dexec.classpathScope=test"

node .\site\smoke-check.mjs

$env:JD_SCREENSHOT_DIR = (Resolve-Path "docs/screenshots").Path
.\mvnw.cmd javafx:run
Remove-Item Env:JD_SCREENSHOT_DIR
```

The UI smoke intentionally leaves normal transfer behavior to the direct-engine smoke classes. It is
safe to rerun before future UI handoffs.
