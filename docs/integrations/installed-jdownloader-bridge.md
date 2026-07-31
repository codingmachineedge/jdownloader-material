# Installed-JDownloader loopback bridge

## Status

Implemented by `JDownloaderRemoteClient` and the stock feature workspace pages. It complements the
project's direct-download engine; it does not embed JDownloader core or use My.JDownloader cloud.

## Behavior

The New Tab menu exposes Accounts, Plugins, Captcha, Extraction, Scheduler, Connections, Remote
Control, Automation and Logs pages backed by a typed catalog of the installed JDownloader Remote
API. Each page has Operations, Response and Connection tabs. Operations and bounded response chunks
are searchable with independent plain-first RE2/J builders; every connection/settings surface has
its own search as well.

Typed operations cover device/system information, accounts, extensions/plugins, captcha/dialogs,
download and LinkGrabber queries/actions, extraction, reconnect, logs, update and system/session
commands. A validated Advanced request surface accepts a simple absolute endpoint plus GET/POST,
positional JSON and a bounded body. Requests run asynchronously, can be cancelled, never follow
redirects and never block the JavaFX thread.

## Configuration and persistence

Settings > Connection stores the base URL; the default is `http://127.0.0.1:3128`. Only `http` or
`https` URLs whose host resolves strictly to `localhost`, `127.0.0.0/8` or `::1` are accepted.
Credentials, query strings, fragments, traversal components and ambiguous numeric host aliases are
rejected. The base URL is part of normal non-secret Settings history. Account/session passwords are
not persisted.

Default transport bounds are a 2-second connect timeout, 8-second request timeout, 128-KiB request
limit and 2-MiB response limit. UI response input is capped at 128 KiB and rendered in 16-KiB
chunks. Positional parameters use UTF-8 percent encoding in the published order.

## Failure modes

An absent or stopped installed JDownloader instance produces a sanitized timeout/transport error
and a persistent notification. Non-loopback URLs, unsafe paths, oversized request/response data,
invalid parameter counts and bodies on GET are rejected before or during the request. Closing a
page cancels its active call; closing the client cancels every remaining call and shuts down its
daemon executor.

Mutating advanced requests, unknown endpoints and catalogued destructive operations require an
explicit user confirmation. The resulting token is scoped to one endpoint, expires after two
minutes and can be consumed only once. A missing, expired, reused or wrong-endpoint token fails
closed.

## Security and privacy

The client never follows redirects, never logs request URLs/bodies, never stores credentials and
retains only bounded response text. Password fields are copied into a temporary character array and
cleared synchronously after request assembly. Error messages name only the validated endpoint, not
secret parameters. Running an operation still has the same effect and permissions as the installed
JDownloader process, so destructive commands remain confirmation-gated.

## Verification

`JDownloaderRemoteClientSmoke` uses an isolated `127.0.0.1` HTTP server to verify the typed catalog,
UTF-8 encoding, loopback validation, traversal/credential rejection, request and streamed-response
bounds, timeouts, cancellation, confirmation tokens, password clearing, advanced requests and
stock-page coverage. It makes no request to a real user profile or public network. Manual Windows
verification connects to an installed JDownloader instance, exercises each stock page and confirms
that failures remain non-blocking.

## Related articles

- [Search integration](../search/search-integration.md)
- [Notifications](../experience/notifications.md)
- [HTTP/API applicability](../api/README.md)
