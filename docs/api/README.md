# HTTP/API applicability

## Status: not applicable

The Windows desktop application exposes **no inbound HTTP API**. `DirectHttpEngine` is an HTTP(S)
client that probes and downloads user-supplied direct URLs. The optional
[installed-JDownloader bridge](../integrations/installed-jdownloader-bridge.md) is also an outbound
client, constrained to the local machine's loopback addresses. Neither is a web service, and the app
does not open a REST, GraphQL, WebSocket or webhook listener.

Consequently:

- no category-level Postman collection is appropriate;
- no master Postman collection is appropriate;
- no OpenAPI document is implied; and
- inventing request examples would misrepresent the product and expand its attack surface.

If an inbound API is deliberately added later, this index must be revised alongside an
authentication model, bind-address and transport policy, failure behavior, rate and size bounds,
security review, category collection, and master Postman collection. Until then, the internal Java
engine contract is documented in [Engine API](../ENGINE_API.md).
