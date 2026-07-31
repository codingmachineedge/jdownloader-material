# Tabs, pinning and grouping

## Status

Implemented by `WorkspacePane` and the append-only `GitWorkspaceStore`.

## Behavior contract

Content opens in discrete tabs on a persistent strip. A horizontal scroller and complete overflow
menu prevent silent clipping. Users can drag to reorder or use keyboard/context commands to move,
close, pin and unpin. Pinned tabs occupy a stable dedicated region, use a compact icon while
retaining their full accessible name, and are excluded from bulk close by default.

Groups can be created, named, renamed, colored, reordered, pinned, collapsed, expanded and removed.
Tabs move into, out of and between groups through drag, keyboard and context commands. Empty groups
remain explicit records until removed. Groups and tabs are appearance targets with normal
management menus, **Edit tab/group appearance…**, Shift+right-click and the global appearance
keyboard path.

## Configuration and persistence

Tab order, pinned state/order, group order, membership, group names/colors/icons/badges, collapsed
state, page identity, title, selected tab and app name persist below
`~/.jdownloader-material/workspace/`. Each structural change creates a new local Git commit. The
appearance profile separately retains the stable per-tab/per-group target overrides.

## Failure modes

A missing workspace is seeded with a safe default strip. Import and persistence are schema- and
size-bounded; a failure produces a persistent non-blocking notification and does not freeze the
active content. A result inside a collapsed group can be activated without changing the saved
collapsed preference. Overflow and compact labels remain keyboard accessible.

## Security and accessibility

Bulk actions inspect visible tab titles only, never page content or hidden data. JavaFX tab-region
and tab-item accessibility roles, accessible full names, visible focus and keyboard activation are
applied. Appearance and structural state stay local; the workspace Git repository has no remote.

## Verification

`DesktopCompletenessSmoke` and the workspace store smokes exercise tabs, pinning, grouping, searches,
bulk close and persistence. Manual verification covers drag/reorder, overflow, unsaved protection,
context/keyboard commands, bilingual labels, narrow widths and 100–200% Windows display scaling.
