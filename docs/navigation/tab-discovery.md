# Tab discovery searches

## Status

Implemented in the workspace toolbar and group headers.

## Behavior contract

The desktop provides four independent search scopes: current strip, one field inside every group,
group names, and a master result menu over every tab owned by the single application window. Each
search is plain text by default and owns its adjacent anchored full regex builder and state.

Master results identify app window, strip, group, pinned state and visible label. Activating a result
selects its tab and moves focus into the content. Selecting a result from a collapsed group does not
change the saved collapsed state. The overflow menu always retains every tab even when a strip
filter hides it.

## Configuration and persistence

Query, pattern, flags, validation and mode synchronize only within their originating field; one
search never shares hidden mutable state with another. Search state is session-local; the durable
workspace snapshot contains structure, not sample text or regex queries.

## Failure modes

Invalid regex stays editable and runs no search. Closed or moved results disappear safely. Large
sets remain bounded and responsive.

## Security and verification

Evaluation is local, bounded by the shared RE2/J limits and inspects visible labels only.
`DesktopCompletenessSmoke` verifies all four fields exist and remain independent; evaluator tests
cover Unicode, flags, invalid and adversarial patterns. Manual checks cover collapsed groups,
keyboard activation, location labels and return focus.
