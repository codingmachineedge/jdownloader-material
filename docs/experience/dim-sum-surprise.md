# Dim-sum surprise

## Status

Implemented. Runtime images live in `src/main/resources/dimsum/`; matching release-safe copies live
in `release-assets/dimsum/`. Local and remote verification evidence remain distinct in
[the handoff](../../HANDOFF.md).

## Behavior contract

After first run, each ordinary launch evaluates the policy exactly once and makes one fresh random
draw. When enabled, exactly 1% of eligible launches select one randomly chosen catalog dish for a
bottom-left, non-blocking surface that never requests focus and auto-dismisses after eight seconds.
The policy has explicit first-run, update, startup-error, quiet-hours and task-in-progress guards; a
second call in the same launch never draws or fires again.

The dish name is factual in both languages. Surrounding copy follows the active language and funny
levels. Reduced-motion users receive no animated entrance, and alt text names the dish.

## Configuration and persistence

A persisted setting disables the surprise completely. The catalog schema is versioned and contains
stable ids, English and Cantonese names, bilingual alt text, and local filenames.

## Failure modes

A missing, corrupt or undecodable image suppresses the surface and startup continues. The release
workflow independently rejects unsafe catalog filenames, mismatched copies and undecodable images.
Randomness or persistence failure must never increase the probability beyond 1%.

## Security and privacy

Images are bundled project assets. The feature performs no network request, tracking, analytics or
remote selection. Catalog paths are constrained to safe local PNG filenames.

## Verification

`DesktopCompletenessSmoke` injects deterministic random sources for the non-trigger, exact trigger,
first-run/quiet/opt-out and single-fire paths. JavaFX smoke captures the real overlay and accessible
name. Delivery validation parses both catalogs, proves byte-identical runtime/release copies,
decodes every image and enforces a minimum 1024 × 1024 size.
