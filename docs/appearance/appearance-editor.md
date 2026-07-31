# Per-element appearance editor

## Status

Implemented in `org.jdownloader.material.appearance` and
`org.jdownloader.material.ui.appearance`. The registry covers the live scene graph, including the
editor and picker controls themselves. Remote release evidence is tracked separately in
[the handoff](../../HANDOFF.md).

## Behavior contract

Registered rendered elements expose **Edit appearance…** through the normal context menu. The
global <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>A</kbd> path targets the focused node, and
Shift+right-click opens the editor directly. Tab and group menus retain their management commands
and add specific appearance commands. The non-modal editor tracks its anchor, flips at viewport
edges, and returns focus when it closes.

Every editable property supports inheritance, live preview, per-property reset, per-element reset
and global reset. Named presets and user themes can be imported/exported. Unsupported platform
properties remain visible with an explanation and retain saved values.

## Configuration and persistence

Overrides use stable target identifiers rather than ephemeral node identities. Theme, density,
seed/accent colors, font family/scale/weight, CJK fallback, presets, state-specific overrides and
unsupported values persist atomically in `~/.jdownloader-material/appearance.properties`. The
editor and its own pickers are appearance targets too. Profiles import and export as bounded
`.jdmappearance` files, with per-property, per-target and global reset.

## Failure modes

Invalid or larger-than-4-MiB imports are rejected without replacing the active profile. A missing
font uses the stored CJK-safe fallback. Persistence failure produces a non-blocking notification;
the current UI remains usable. A vanished anchor closes safely rather than leaving a detached
editor.

## Security and accessibility

Theme import is local data, size-bounded and strictly parsed; it never executes code or loads remote
fonts. Every control has a name, value, visible focus and sufficient contrast, including hover,
focus, selected, disabled and error states.

## Verification

`AppearanceFoundationSmoke` covers profile persistence, import/export bounds, presets, resets,
unsupported-value retention and color translation. `AppearanceUiSmoke` covers live application,
target discovery, keyboard/context paths, anchor behavior, geometry reset and self-registration.
The release workflow discovers and runs both with every other desktop `*Smoke.java` main.
