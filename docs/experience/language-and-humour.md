# Language modes and funny levels

## Status

Implemented. English, playful Hong Kong-style Cantonese and compact bilingual modes switch live.
Two independent persisted funny-level sliders cover levels 1–5, and first use plus the Appearance
settings page disclose that humour also styles errors and warnings.

## Behavior contract

Language changes apply without restart. Bilingual layouts keep English primary and Cantonese
legible without clipping. Funny levels style every category of copy, including errors and warnings,
but never change the named action, affected data, irreversibility, cause, or recovery options.
Level 1 is fully professional; level 5 is maximally playful and respectful.

## Configuration and persistence

Language mode and the two funny levels are separate settings in the restart journal and append-only
Settings history. Both languages default to professional level 1, so playfulness is an explicit
opt-in. The one-time disclosure flag
also persists. Localization resources remain separate from product logic; a missing key falls back
to the factual key rather than inventing copy.

## Failure modes

Missing translations display the stable resource key, keeping the failure diagnosable. Values read
from disk are clamped to 1–5. Long bilingual strings wrap or reflow rather than overlapping or
pushing controls off-screen.

## Security and accessibility

Humour never mocks the user, loss, money, disability, or security state. Screen-reader names expose
the same facts as visual copy, and language fragments identify their language.

## Verification

`DesktopCompletenessSmoke` verifies three modes, the two independent controls and disclosure
wiring. Settings persistence tests cover clamping and restart round-trips. Manual verification
exercises levels 1–5 for normal, warning, error and destructive copy at 100%, 125%, 150% and 200%
Windows scale and the narrow supported width.
