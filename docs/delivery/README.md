# Delivery documentation

| Feature | Contract | Status |
| --- | --- | --- |
| Windows releases | [Release pipeline](release-pipeline.md) | Implemented locally; remote proof pending |
| Pages and wiki | [Pages and wiki](pages-and-wiki.md) | Tabbed source implemented locally; remote proof pending |
| Test evidence | [Verification](verification.md) | Dynamic smoke gate implemented locally; remote proof pending |

Delivery is successful only when tests pass before publication, one new immutable release is
published for the qualifying run, the release contains exactly one real Windows x64 EXE and one
bundled dim-sum image, Pages deploys verified tabbed source, the wiki is synchronized, and the
pushed default branch contains the intended commit.

No HTTP/Postman artifacts belong here. See [API applicability](../api/README.md).
