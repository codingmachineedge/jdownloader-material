# Search integration

## Status

Implemented across the Windows desktop. The global search passes one `SearchSpec` to Downloads,
LinkGrabber, History, Settings and the active workspace page. Dedicated fields cover Downloads
properties, Settings globally and per tab, notifications, changelog, installed-JDownloader
operation/response/settings surfaces, current strip, every tab group, group names, master tabs and
both bulk-close predicates.

## Behavior contract

Every search bar has its own adjacent builder and independent state. Settings search covers option
labels, descriptions and current values; the global field reports matching Settings sections while
every section also retains its own local query. No field borrows a hidden “last active” builder.

## Failure, security and verification

Invalid regex blocks only its originating search and remains editable. Evaluation stays local and
bounded, and search text/sample text is not transmitted. `SafeSearchSmoke` verifies evaluator
behavior; desktop UI smokes and manual narrow/bilingual checks verify that regex mode affects the
real result set and the adjacent affordance remains reachable.
