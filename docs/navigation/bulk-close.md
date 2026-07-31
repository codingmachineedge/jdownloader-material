# Tab bulk close

## Status

Implemented from the workspace Bulk Close menu.

## Behavior contract

The workspace provides **Close tabs containing text** and **Close tabs not containing text**. Both
match visible titles, default to plain text and use an adjacent anchored regex builder and the same
predicate. Empty queries, invalid patterns and zero-result previews never enable Close.

Before closing, the popover shows match mode, scope, affected count and up to eight matching titles.
Pinned tabs and pinned-group members are excluded by default. Explicitly including them recomputes
the preview. Content that reports unsaved work is removed from the close set and reported through a
persistent warning. The remaining count is confirmed in a blocking decision dialog immediately
before the structural change.

## Configuration, failure and security

Scope is explicit: current group, selected groups or all groups. Matching is local, RE2/J-bounded
and never examines page contents. The close set is recomputed when the user acts; if a tab becomes
dirty after preview, the final guard skips it and reports the exclusion.

## Verification

`DesktopCompletenessSmoke` covers containing/inverse availability, empty/invalid gating, pinned
exclusion and scope wiring. `SafeSearchSmoke` covers the shared predicate across plain/regex modes,
flags and Unicode. Manual verification covers unsaved changes, moving tabs, localization and
keyboard operation.
