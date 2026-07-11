# Design system

The rewrite is a compact desktop interpretation of Material 3, anchored by mint-teal primary roles and shared semantic tokens rather than page-specific colors.

## Foundations

- `theme-light.css` and `theme-dark.css` define the canonical `-md-*` color roles.
- `material.css` applies those roles to the shell, tables, settings rows, drawer, progress, focus, and state feedback.
- `Segoe UI Variable`, `Segoe UI`, and a system sans-serif fallback form the interface stack; numeric telemetry uses `Cascadia Mono`, `Consolas`, and monospace.
- Major work panels use a one-pixel semantic border and 16 px corner radius. Fields and chips use 9 px, navigation and timeline entries use 12 px, and progress/file markers use 4 px.
- The canonical spacing scale is 2, 4, 6, 8, 10, 12, 14, 16, 18, 22, 24, 30, and 32 px, with a 4 px icon rhythm and 8–12 px control gaps.

## Semantic roles

Primary and primary-container roles drive selected navigation, primary actions, focus, and branded progress. Surface roles separate the toolbar, rail, work panels, and elevated drawer. Outline roles define structural borders and dividers. Error, warning, success, and informational roles always appear with a label, icon, chip, progress value, or supporting copy rather than color alone.

## Density and motion

Desktop scanning density is intentional: the toolbar is 52 px, action rows 48 px, table headers 34 px, data rows 48 px, status bar 30 px, and Settings rows about 62 px. The Add Links drawer uses an interruption-safe 260 ms fade/translate transition; routine feedback stays fixed in the page or status bar instead of appearing as blocking dialogs.

The responsive breakpoint is 980 px. Below it, the rail narrows from 208 to 72 px and hides labels, global search, and the throughput trace while retaining their core actions or tooltips.

The source handoff illustrates My.JDownloader as its third navigation destination. The production
app uses that visual slot for its real History surface because it has no My.JDownloader adapter;
inert credential and Connect controls are intentionally not presented as a shipped capability.

## Source and verification

The implementation lives in [`src/main/resources/css`](https://github.com/codingmachineedge/jdownloader-material/tree/main/src/main/resources/css), with layout ownership in [`MainWindow.java`](https://github.com/codingmachineedge/jdownloader-material/blob/main/src/main/java/org/jdownloader/material/ui/MainWindow.java). Use the [21-scene gallery](Home#screenshot-gallery) to compare light, dark, localized, status, properties, Settings, and drawer states. Capture instructions are in [Development](Development#refresh-the-gallery).
