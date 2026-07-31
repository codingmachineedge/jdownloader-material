# Font and color controls

## Status

Implemented by `InfiniteColorPicker`, `FontPicker`, the appearance property grid and the persisted
profile model.

## Behavior contract

The color picker combines JavaFX's continuous color field with palette and recent swatches, alpha,
live preview, a selectable notation field and bidirectional entry for named colors, HEX/HEX8,
RGB/A, HSL/A, HSV/HSB, HWB, CIELAB/LCH, OKLab/OKLCH and CMYK. It preserves alpha, identifies gamut,
retains clipping diagnostics, reports WCAG contrast against a chosen background and copies any
translated representation.

The font picker searches installed and bundled families, previews each family in its own face, and
provides free-entry plus stepped size, weight, posture, underline, strike, variable axes, character
spacing, line height and CJK fallback. The property grid exposes the wider schema—bold, overline,
capitalization/small caps, super/subscript, text/highlight/outline, shadow/glow, word spacing,
baseline, direction, alignment, geometry, spacing and icon properties. A visible capability message
marks values JavaFX cannot render directly; those values remain stored instead of being discarded.

## Configuration and persistence

Values are stored in a representation that does not silently discard unsupported properties.
Recent colors and fonts are bounded. CJK-safe fallback is explicit and previewed.

## Failure modes

Out-of-gamut conversion previews the clipped result and retains a clipping warning. Invalid numeric
input remains editable with inline feedback. Missing fonts fall back without overwriting the
user's saved choice.

## Security and accessibility

No remote font or palette is fetched implicitly. Keyboard users can reach the full continuous color
space and numeric fields. Contrast output is screen-reader readable and never relies on color alone.

## Verification

`AppearanceFoundationSmoke` round-trips every supported color family, alpha, gamut warnings,
contrast, presets and import/export. `AppearanceUiSmoke` exercises the picker/editor integration,
live CSS application and resets. Manual verification still checks installed-font enumeration, CJK
fallback, keyboard operation, narrow bilingual layouts and Windows display scaling.
