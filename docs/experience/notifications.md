# Notifications and history

## Status

Implemented by `NotificationService`, the bottom-right `NotificationOverlay` and the searchable
Notification Center workspace page.

## Behavior contract

Informational, success and non-decision errors appear in a bottom-right stack without blocking work.
At most the newest five are rendered at once. Information auto-dismisses after five seconds,
success after four seconds, and warnings/errors remain until dismissed. Optional actions are kept
outside the persisted record and run only while still valid. Only decisions that must be resolved
before continuing use a modal dialog.

A notification center retains up to 500 dismissed and active items with timestamp, severity,
factual title/body and read state. It provides an independent plain-first/regex search and Clear
History action. Runtime actions are deliberately not resurrected after restart.

## Configuration and persistence

History is stored atomically in `~/.jdownloader-material/notifications.properties`, capped at 2 MiB.
The notification-history preference and quiet-hours preference are persisted with Settings.
Language and per-language funny levels style notification copy, and notification nodes participate
in the same appearance registry as the rest of the scene.

## Failure modes

One damaged persisted item is skipped without hiding valid history around it. Load/save failures are
reported to stderr and never fail the original operation. More than five active items remain in the
service/history even though only the newest five cards render. Dismissed or restarted action
callbacks are unavailable by design.

## Security and accessibility

Callers must pass bounded factual copy: titles are capped at 160 characters, bodies at 2,000 and
action labels at 80. The notification store must not receive credentials or signed URLs. Each card
has a combined accessible name, is keyboard-focusable and provides a named dismiss control.

## Verification

`DesktopCompletenessSmoke` covers service history, severity policy and Notification Center wiring.
JavaFX UI smoke covers the visible stack and accessible controls. Manual verification covers five-
card stacking, exact timeouts, persistent warnings, keyboard operation, funny levels and narrow
bilingual layouts.
