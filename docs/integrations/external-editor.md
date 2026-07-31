# Open in external editor

## Status

Implemented in the Windows desktop app.

## Behavior contract

The General settings page detects Visual Studio Code, VS Code Insiders, Notepad++, IntelliJ IDEA and
Eclipse from `PATH` and common Windows install locations. The chooser supports automatic selection,
a specific detected editor, or a custom command. Re-detection runs away from the JavaFX thread and
reports its state accessibly.

**Open folder in editor** sits beside the default download folder in Settings and in the Downloads
toolbar menu. **Open selected file in editor** is available from the Downloads toolbar and row context
menu when the selected link has a completed file. Launch success, missing configuration, invalid
paths and process-start failures are reported as non-blocking localized notifications.

## Configuration and persistence

`externalEditorSelection` and `externalEditorCommand` are persisted by `SettingsIO` and included in
settings-history snapshots. Choosing **Automatic** is the reset path; the refresh button repeats
detection. The custom command field is editable only while **Custom command** is selected.

Custom templates accept `%file%` and `%folder%`. If neither placeholder occurs, the selected absolute
path is appended as the final argument. Double quotes preserve paths containing spaces; an unmatched
quote is rejected. The parser produces a structured argument list for `ProcessBuilder` and never
passes the command through a shell, so command metacharacters remain ordinary argument text.

## Failure modes

No detected editor leaves **Automatic** available but unable to launch until the user refreshes or
configures a custom command. A removed editor, unknown persisted selection, missing target, malformed
quote or denied process launch leaves the app usable and produces a factual error notification. The
stored custom command is preserved when another editor is selected, so returning to it does not lose
the user's template.

## Security and verification

Detection and launch are local-only. The app does not download editors, invoke a command shell,
elevate privileges or transmit the selected path. The chosen editor is visible in Settings and a
custom executable/template remains visible in its text field.

`DesktopCompletenessSmoke` exercises quoted and non-ASCII paths, placeholder substitution, direct
argument-list launching, automatic/detected/custom selection, folder and file targets, missing
configuration, launch failure notifications and settings persistence. `SettingsView` and
`DownloadsView` provide the localized, keyboard-operable UI paths.
