# Design system

JDownloader Material follows **Material Design 3**. Color is expressed as a small set of
semantic *roles* (not raw hex), defined once per theme and consumed everywhere.

## Color tokens

Defined in `src/main/resources/css/theme-light.css` and `theme-dark.css` as JavaFX looked-up
colors. Component styles in `material.css` reference only these — never literal colors — so a
theme is one file swap.

| Token | Role |
|---|---|
| `-md-primary` / `-md-on-primary` | Primary actions (filled buttons, active states) |
| `-md-primary-container` / `-md-on-primary-container` | Tonal primary surfaces |
| `-md-secondary-container` / `-md-on-secondary-container` | Selected nav, chips, selection |
| `-md-error` / `-md-error-container` | Error state, destructive actions |
| `-md-success` / `-md-warning` (+containers) | Finished / paused states (extended roles) |
| `-md-background`, `-md-surface` | Base surfaces |
| `-md-surface-container[-low/high/highest]` | Elevation tiers (app bar, cards, inputs) |
| `-md-outline`, `-md-outline-variant` | Borders, dividers |
| `-md-*-overlay` | Hover / focus / pressed state layers |

The baseline palette is Material 3's default (primary `#6750A4` light / `#D0BCFF` dark). Swap the
token values to rebrand.

## Type scale

`.display` 32 · `.headline` 24 · `.title` 18 · `.subtitle` 15 · `.body` 14 · `.label-md` 13 ·
`.caption` 12. Default UI font is Segoe UI / Roboto.

## Shape & elevation

- Corner radii: buttons 20, cards 16, dialogs 28, inputs 8, chips/small 8–12.
- Elevation via soft `dropshadow` on cards/dialogs; surfaces step up through the
  `surface-container` tiers rather than heavy shadows.

## Components

- **Buttons** — `.filled-button`, `.tonal-button`, `.outlined-button`, `.text-button`,
  `.icon-button` (40dp circular), applied on `MFXButton` for the ripple.
- **Navigation rail** — `.nav-rail` / `.nav-item` with a pill `.nav-glyph` that fills with
  `secondary-container` when selected.
- **Data tables** — `.data-table` styles `TreeTableView` headers, rows (hover/selected/zebra),
  in-cell `.cell-progress` bars and `.status-chip`s colored by state.
- **Icons** — `Icons.of(name, size)` renders 24dp Material SVG paths as CSS-colorable regions
  (`-fx-background-color` fills the shape), so icons recolor with the theme.
- **Inputs** — filled text fields, combos, sliders, and MaterialFX toggles, all tinted from
  `-md-primary`.

## Light + dark

Both themes are first-class and hand-tuned (not an auto-derived inversion): dark uses the M3
dark role set with lighter primaries and darker surface tiers. The active theme is chosen from
the app bar and mirrored in Settings → Appearance.
