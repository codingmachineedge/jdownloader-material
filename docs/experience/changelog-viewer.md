# In-app changelog viewer

## Status

Implemented as the Changelog workspace page backed by the bundled `changelog.tsv` release record.

## Behavior contract

The viewer lists every recorded release with exact version, release date, category, source commit
and factual changes. Search and the inclusive From/To range compose. JavaFX date pickers provide
calendar navigation and accept either the current locale's short date or ISO `YYYY-MM-DD`. Presets
cover all entries, the last 30 days and the current year. Users can copy or export the complete
filtered view as Markdown with the exported range recorded.

Search is plain text by default and opens the full anchored regex builder. Empty results say that no
entries match. Language and funny levels may style the narration but never alter versions, dates,
security facts or breaking changes.

## Configuration and persistence

Filters are session-local. Release entries come from the bundled, reviewable TSV source; the viewer
does not invent gaps or require a network connection. The default export target is the current
user's Downloads folder and remains editable.

## Failure modes

Partial or invalid typed dates stay visible and receive inline feedback; the previous filtered list
is retained until the range is valid. A From date after To is rejected inline. Missing or malformed
TSV records are skipped rather than fabricated. Export failure leaves the filtered view intact and
reports a persistent non-blocking error.

## Security and accessibility

Search and date parsing are local and bounded. Calendar, search, copy and export are keyboard and
screen-reader operable with visible focus.

## Verification

`DesktopCompletenessSmoke` and changelog service coverage verify bundled entries, date/search
composition, copy-ready Markdown and export range metadata. Manual verification covers locale/ISO
typing, partial/invalid dates, calendar navigation, presets, no-match, clipboard, localization and
keyboard focus.
