# Design system

JDownloader Material uses Material Design 3 roles with a focused direct-download workspace shell.
Color is expressed through semantic roles rather than raw component colors, allowing light and
dark themes to stay coherent across tables, tabs, status feedback, and inline editors.

## Brand mark

`src/main/resources/icons/app.png` is the square application logo: an indigo-violet rounded badge
with a white download arrow and link detail. It is used for the desktop stage icon and the app-bar
mark. The README displays the same source asset so release pages and source browsing use the same
identity.

## Color tokens

`src/main/resources/css/theme-light.css` and `theme-dark.css` define JavaFX looked-up colors.
`material.css` consumes these roles, keeping brand and component colors centralized.

| Token | Role |
| --- | --- |
| `-md-primary` / `-md-on-primary` | Primary actions, active states, and brand emphasis |
| `-md-primary-container` / `-md-on-primary-container` | Tonal primary surfaces |
| `-md-secondary-container` / `-md-on-secondary-container` | Selected navigation, tabs, chips, and selection |
| `-md-error` / `-md-error-container` | Error state and destructive actions |
| `-md-success` / `-md-warning` (+ containers) | Finished and paused state treatment |
| `-md-background`, `-md-surface` | Base surfaces |
| `-md-surface-container[-low/high/highest]` | Elevation tiers for app bar, cards, and inputs |
| `-md-outline`, `-md-outline-variant` | Borders and dividers |
| `-md-*-overlay` | Hover, focus, and pressed state layers |

## Type and tab styling

The base type scale is `.display` 32, `.headline` 24, `.title` 18, `.subtitle` 15, `.body` 14,
`.label-md` 13, and `.caption` 12. The default UI font is Segoe UI / Roboto.

Workspace tabs support personal label typography without changing the page's semantic content:
each tab can save a title, font family, font size, bold, italic, and a full-color picker value.
The selected style renders in the tab label and persists through the local workspace Git store.

## Shape and elevation

- Corner radii: buttons 20, cards 16, inputs 8, chips/small controls 8–12.
- Soft `dropshadow` keeps cards distinct while surface-container tiers define the main elevation.
- Browser-style tabs use the same compact, rounded Material language as navigation pills and
  preserve room for title, close, and right-click editing affordances.

## Components

- **Buttons** — `.filled-button`, `.tonal-button`, `.outlined-button`, `.text-button`, and
  `.icon-button`; application actions use visible labels beside their icons.
- **Navigation rail** — `.nav-rail` / `.nav-item` with a pill `.nav-glyph` selected surface.
- **Workspace tabs** — tab labels carry persisted user typography and expose a right-click
  editor for the app name and tab settings.
- **Data tables** — `.data-table` styles tree-table headers, rows, selection, in-cell
  `.cell-progress` bars, and `.status-chip` state colors.
- **Inputs** — filled text fields, combos, sliders, toggles, and the native color picker use
  the primary theme role.
- **Status feedback** — `.status-bar .status-message` keeps recent nonblocking activity in the
  layout; error messages use the error role without adding an overlay.

## Light and dark

Both themes are hand-tuned Material 3 role sets. The app bar switch and Settings → Appearance
apply the active set immediately, including the logo mark, browser tabs, data tables, and status
line.
