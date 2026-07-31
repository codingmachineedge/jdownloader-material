# Design system

The Windows desktop uses a compact Material 3 system anchored by mint/deep-teal roles, semantic
state colors and stable component geometry. `theme-light.css` and `theme-dark.css` define canonical
roles; `material.css` and `appearance.css` style the shell, workspace, views, notifications,
builders and appearance controls.

## Runtime appearance

The persisted profile controls theme, density, seed/accent colors, installed/bundled font family,
size scale, weight and CJK fallback. Stable per-element and per-state targets add typography,
foreground/background/border/icon colors, geometry, spacing and icon treatment. Right-click adds
**Edit appearance…**; Shift+right-click opens directly; <kbd>Ctrl</kbd>+<kbd>Shift</kbd>+<kbd>A</kbd>
targets the focused element. Tabs and groups preserve their complete management menus.

The color control combines a continuous field, alpha, recents/swatches, gamut/clipping and contrast
with named, HEX/HEX8, RGB/A, HSL/A, HSV/HSB, HWB, Lab/LCH, OKLab/OKLCH and CMYK translation. The font
control searches and previews installed/bundled faces and exposes deep typography. Unsupported
JavaFX properties stay visible and persist rather than disappearing.

## Foundations

- Primary teal marks the current action/focus/progress; success, warning and error always include
  text, icon or value.
- Interface fonts use Segoe UI Variable/Segoe UI with CJK-safe fallback; telemetry uses Cascadia
  Mono/Consolas.
- The canonical spacing scale is 2–32 px, with one-pixel structural borders, 9 px fields, 12 px
  navigation/timeline elements and 16 px major panels.
- The shell keeps a 52 px toolbar, 208/72 px rail, browser-style workspace and 30 px status bar.
  At narrow width, rail labels/global search/throughput hide while the workspace keeps overflow and
  accessible tab names.
- Routine feedback uses named focusable corner notifications. Modal dialogs are reserved for a
  decision, including confirmation of destructive installed-JDownloader requests or bulk close.

The project mark is a mint download arrow entering a deep-teal M-shaped tray. It is a project-owned
derived asset, not an official JDownloader trademark.

See the repository [design-system reference](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/DESIGN_SYSTEM.md)
and the [appearance category](https://github.com/Ding-Ding-Projects/jdownloader-material/blob/main/docs/appearance/README.md).
