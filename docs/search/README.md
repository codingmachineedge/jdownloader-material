# Search

| Feature | Contract | Status |
| --- | --- | --- |
| Safe evaluator and builder | [Regex builder](regex-builder.md) | Implemented |
| Desktop search surfaces | [Search integration](search-integration.md) | Implemented |

All desktop search fields use RE2/J 1.8 through the same bounded evaluator. Plain-text matching is
the default; each field owns its own adjacent anchored builder, expression, flags, validation and
mode.
