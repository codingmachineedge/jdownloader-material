# Regex builder

## Status

Implemented. `org.jdownloader.material.search` supplies the bounded RE2/J evaluator and
`org.jdownloader.material.ui.search` supplies the independent plain-first field plus anchored
builder.

## Behavior contract

The builder stays anchored to its originating field, tracks collisions and returns focus on close.
It supports raw editing; case-insensitive, multiline and dotall flags; guided literals, character
classes, anchors, groups, alternation and quantifiers; bounded sample text; syntax feedback; live
matches and capture groups; and copy/export. Plain search remains the default. Query, pattern, flags,
validation and mode synchronize bidirectionally with only that field.

The engine is RE2/J 1.8. It does not silently fall back to Java's backtracking regex engine.

## Configuration and persistence

Each field owns independent session state; sample text is never persisted. The default bounds are
4,096 expression characters, 65,536 input characters, 1,000 matches, 100 capture groups and 262,144
captured-result characters.

## Failure modes

Invalid syntax, unsupported constructs and exceeded limits return inline validation without running
or discarding input. Zero-width matches advance safely. No-match is a normal result.

## Security and privacy

Evaluation is local, RE2/J-bounded and size-limited. Patterns and samples are not transmitted.
Exports contain only the user-selected pattern/flags, never unrelated app data.

## Verification

`SafeSearchSmoke` covers valid, invalid, unsupported, no-match, Unicode, multiline, dotall,
case-insensitive, zero-width, capture-group, adversarial, limit, result-budget and
plain-versus-regex cases. JavaFX smoke coverage exercises the anchored field/builder integration;
manual checks cover focus return, copy/export and narrow bilingual layouts.
