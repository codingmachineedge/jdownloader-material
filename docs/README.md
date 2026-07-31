# Documentation index

This is the canonical documentation map for the Windows desktop application. **Implemented** means
the named behavior exists in the current desktop source. It does not predict a GitHub release:
local and remote evidence are recorded separately in [HANDOFF.md](../HANDOFF.md) and the
[delivery verification guide](delivery/verification.md).

| Category | Scope | Current status |
| --- | --- | --- |
| [Desktop experience](experience/README.md) | language and humour, notifications, changelog, dim-sum surprise | Implemented |
| [Appearance](appearance/README.md) | Material 3 tokens, per-element editing, fonts, colors | Implemented |
| [Navigation](navigation/README.md) | browser-style tabs, pinning, grouping, tab discovery and bulk close | Implemented |
| [Search](search/README.md) | plain search and bounded RE2/J regex builder | Implemented |
| [Data and history](data/README.md) | restart recovery and append-only local revisions | Implemented |
| [Integrations](integrations/README.md) | external editor and installed-JDownloader loopback bridge | Implemented |
| [Delivery](delivery/README.md) | CI, Windows installer, release photo, Pages, wiki and verification | Implemented locally; remote proof pending |
| [API](api/README.md) | inbound HTTP/API and Postman applicability | Not applicable |

Existing detailed references remain authoritative for shipped transfer behavior:

- [Architecture](ARCHITECTURE.md)
- [Engine API](ENGINE_API.md)
- [History](HISTORY.md)
- [Design system](DESIGN_SYSTEM.md)
- [UI guide](UI_GUIDE.md)
- [Feature reference](FEATURE_SPEC.md)
- [Verification handoff](UI_SMOKE.md)

Every feature document states behavior, configuration and persistence, failure modes, security and
privacy, verification, and an honest implementation status. Update the relevant category index,
`README.md`, tracked `wiki/`, Pages source, `ROADMAP.md`, and `HANDOFF.md` with each desktop change.
