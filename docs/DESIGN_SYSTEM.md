# Design system

JDownloader Material uses a compact Material 3 language adapted to a high-density Windows desktop
utility. State is the hierarchy: queue status, availability, progress, speed, selection, focus, and
activity take priority over decoration. The visual posture is cool and operational—neutral
blue-charcoal surfaces, one mint-teal primary, cool-blue secondary signals, thin borders, and
restrained elevation.

This document describes the JavaFX implementation in `theme-light.css`, `theme-dark.css`, and
`material.css`. It does not imply unimplemented My.JDownloader, account, captcha, plugin, proxy, or
updater surfaces.

## Brand mark

`src/main/resources/icons/app.png` is the project-specific mint-teal download mark used by the
desktop stage and source documentation. The toolbar renders the same download motif as a compact
30 px mark. This derived project asset is not an official JDownloader trademark asset.

## Canonical color roles

The two theme files are the source of truth. Components consume looked-up `-md-*` roles; component
CSS must not introduce local theme colors.

| Role | JavaFX token | Light | Dark | Use |
| --- | --- | --- | --- | --- |
| Background | `-md-background` | `#f7f9fc` | `#111318` | Application canvas |
| Foreground | `-md-on-background`, `-md-on-surface` | `#191c20` | `#e7e9f0` | Primary text and icons |
| Surface | `-md-surface` | `#ffffff` | `#191c22` | Toolbar, rail, panels, status bar |
| Surface 2 | `-md-surface-2` | `#eef1f6` | `#22262e` | Inputs, hover, grouped controls |
| Surface 3 | `-md-surface-3` | `#e4e8ef` | `#2c313b` | Pressed states, tracks, toggles |
| Muted | `-md-muted` | `#565e69` | `#aeb4c0` | Metadata, hints, inactive icons |
| Border | `-md-border` | `#c5cbd4` | `#3b414c` | One-pixel structure |
| Primary | `-md-primary` | `#006b5c` | `#8bd8c5` | Primary action, focus, progress |
| On primary | `-md-on-primary` | `#ffffff` | `#00382f` | Content on primary |
| Secondary | `-md-secondary` | `#315f9f` | `#a9c7ff` | File markers and secondary signals |
| Success | `-md-success` | `#176b45` | `#78dba9` | Online and finished |
| Warning | `-md-warning` | `#795900` | `#f0c36a` | Checking and paused |
| Error | `-md-error` | `#ba1a1a` | `#ffb4ab` | Failed and destructive |

Supporting containers keep semantic content readable without filling large areas with saturated
color.

| Role | Light | Dark |
| --- | --- | --- |
| `-md-primary-container` | `#d1e4e2` | `#2e3e3f` |
| `-md-secondary-container` | `#dbe7ff` | `#28374f` |
| `-md-success-container` | `#d4f3e2` | `#203d31` |
| `-md-warning-container` | `#f8e7b4` | `#44391f` |
| `-md-error-container` | `#ffdad6` | `#4a2525` |

Selection and interaction use derived state layers:

- `-md-selection` is `#d1e4e2` in light and `#2e3e3f` in dark;
- hover and press use translucent foreground overlays;
- `-md-primary-soft`, `-md-secondary-soft`, `-md-success-soft`, `-md-warning-soft`, and
  `-md-error-soft` provide restrained semantic tints;
- `-md-divider-soft` separates dense rows; and
- `-md-scrim` plus `-md-shadow-overlay` are reserved for the Add Links drawer and other overlays.

Material-compatible aliases such as `-md-surface-container-high`, `-md-outline-variant`, and
`-md-on-surface-variant` map back to the canonical palette so legacy controls resolve through the
same themes.

Primary teal is reserved for the current action, keyboard focus, active progress, and selected
emphasis. It is not a page background. Success, warning, and error always appear with text, an icon,
a status chip, or a progress value rather than color alone.

## Typography

The UI family is `Segoe UI Variable`, then `Segoe UI`, then a system sans-serif fallback. Numeric
telemetry uses `Cascadia Mono`, then `Consolas`, then monospace. The scale implemented in
`material.css` is:

| Style | Size | Weight | Typical use |
| --- | --- | --- | --- |
| Display | 32 px | 400 | Product identity in About |
| Page heading | 24 px | 600 | Downloads, LinkGrabber, History, Settings, drawer heading |
| Title | 18 px | 600 | Cards and grouped content |
| Subtitle | 15 px | 600 | Section and timeline headings |
| Body | 14 px | 400 | Primary interface copy |
| Label | 12 px | 500 | Table headers, fields, metadata |
| Caption | 12 px | 400 | Supporting descriptions and status text |

Navigation item text is 14 px; the System section label is 11 px/600. Table body text is 13 px and
tabular diagnostics use the monospace stack. Copy stays sentence case and operational.

## Geometry and density

The canonical spacing scale is `2, 4, 6, 8, 10, 12, 14, 16, 18, 22, 24, 30, 32` px. Prefer a 4 px
internal icon rhythm and 8–12 px control gaps.

| Element | Measurement |
| --- | --- |
| Global toolbar | 52 px high |
| Expanded / compact primary rail | 208 px / 72 px; compact below 980 px |
| Status bar | 30 px high |
| Page heading | 62 px high |
| Settings section list | 220 px wide |
| Table action bar / data row | 48 px high |
| Table header | 34 px high |
| Navigation item / field | 42 px high |
| Toolbar icon target | 38 px |
| Add Links drawer | 440 px preferred/max, 360 px minimum |

Radii are 4 px for progress/file markers, 9 px for fields and chips, 12 px for navigation and
timeline entries, and 16 px for major panels/cards. Search and icon buttons use pill/circular
geometry only where their compact role benefits from it. Structural borders are one pixel.
Panels are usually border-defined; the deep overlay shadow is reserved for the drawer.

## Layout

The shell places the 52 px toolbar across the top, persistent navigation on the left, one active
content destination in the center, and the 30 px status bar across the bottom. The global toolbar
owns transfer controls and page-aware search. The navigation rail swaps Downloads, LinkGrabber,
History, and Settings without creating duplicate view instances.

At widths below 980 px, the rail collapses to 72 px, text labels hide, and global search and the
throughput trace are removed from layout. This is a compact desktop fallback, not a mobile card
conversion. Tables retain their own constrained column behavior.

Settings is a nested split composition: a 220 px section list and scroll-managed rows. History is a
roughly 44/56 timeline/preview split. Add Links is a right-hand overlay rather than another primary
destination.

## Components

- **Global toolbar** — brand mark, Add Links, Start, Pause/Resume, Stop, contextual search,
  throughput, theme, clipboard monitoring, and window controls.
- **Primary navigation** — 42 px destinations with recognizable 20 px icons, visible selected
  fill, hover feedback, focus outline, and compact tooltips.
- **Buttons** — filled, tonal, outlined, text, and 38 px icon variants. Destructive controls use
  the error role and an explicit text verb where space permits.
- **Data tables** — package/file hierarchy, 34 px headers, 48 px rows, subtle separators, hover and
  selection layers, status chips/dots, 5 px progress, and monospace telemetry.
- **Inputs and filters** — 42 px surface-2 fields with 9 px radius; app-bar search is the only pill
  field. Filter chips are 30 px high.
- **Status bar** — global speed, running count, remaining bytes, retry state, and fixed activity
  feedback.
- **History** — bordered split view with timeline entries, semantic chips, metadata rows, and a
  restore card.
- **Settings** — 62 px label/support/control rows with a bottom divider, avoiding decorative cards
  except where Backup needs a grouped form.
- **Add Links drawer** — scrim, close control, 62 px header/footer, URL/package/destination fields,
  inline outcome copy, and grouped actions.

## State and interaction

Hover uses Surface 2; pressed uses Surface 3 or the press overlay; selection uses the primary-tint
selection role. Focus is a two-pixel primary outline on controls and a one-pixel primary boundary on
table rows. Disabled controls use 46 percent opacity.

The drawer cancels an in-flight transition before starting another. Its slide lasts 260 ms; the
scrim fades for 220 ms on open and 180 ms on close. Focus moves to the URL field when opening, and
Escape, the scrim, Cancel, or Close dismisses it. Other feedback remains inline in tables, pages,
and the fixed status bar.

## Accessibility contract

- Keep controls as native JavaFX buttons, toggles, fields, lists, and tables.
- Preserve visible keyboard focus in both themes.
- Give compact icon controls meaningful tooltips and keep primary actions visibly labeled.
- Pair status color with readable text, an icon, or a value.
- Keep the throughput value available as accessible text.
- Move focus into drawers and support Escape dismissal.
- Do not claim complete screen-reader announcements or shortcut coverage until those dynamic paths
  are implemented and tested.

## Voice and boundaries

Use terse, factual terms: Downloads, LinkGrabber, History, Settings, Add Links, Package, Host,
Availability, Progress, Speed, ETA, clipboard monitoring, and transient retry. Buttons start with a
direct verb. Supporting copy explains consequence rather than marketing benefit.

The current product is a direct HTTP(S) download app. New designs must not imply My.JDownloader,
host accounts, captcha solving, proxy management, plugin management, extraction, or update systems
until both the engine capability and the corresponding UI are implemented.

The handoff's third primary-navigation example is My.JDownloader. This implementation deliberately
uses the same visual slot for the real History surface instead: the engine has no My.JDownloader
adapter, and reproducing credential or Connect controls as inert UI would misrepresent capability.
