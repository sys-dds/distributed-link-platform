TICKET-012 - Add link read and list APIs for the control plane

title[]

Add link read and list APIs for the control plane

technical_detail[]

Expand the Link Platform from a pure create-and-redirect service into a minimal control-plane API by adding read endpoints for stored links. This ticket should expose a single-link read endpoint and a list endpoint backed by the existing PostgreSQL persistence model, while keeping the current create-link, redirect, validation, and observability behavior unchanged.

At minimum, the implementation should add:

a read endpoint for one link by slug
a list endpoint for recent links

The single-link read endpoint should return the current persisted link details for an existing slug and return the existing RFC 7807 not-found shape for a missing slug.

The list endpoint should return a stable, deterministic list of persisted links using the existing schema only. Keep the list response intentionally small and practical for the current stage. It should support a simple limit query parameter with a sensible default and maximum so the endpoint does not become unbounded. Do not add full search, filtering, or advanced pagination yet.

The response shape should be clean and focused on the current data model. Use the fields that already exist and are useful now, such as slug, original URL, and created timestamp. Keep the implementation minimal and avoid overbuilding repository layers, query abstractions, or admin frameworks.

Do not broaden this ticket into update/delete behavior, search, ownership, tenant logic, auth, quotas, analytics, caching, or UI work. Keep it focused on control-plane read access only.

feature_delivered_by_end[]

The platform supports reading one link and listing recent links through stable control-plane endpoints, making the system easier to inspect and setting up future ownership, search, and admin features.

how_this_unlocks_next_feature[]

This creates the missing read surface that later identity, ownership, search, feed, and admin features can build on without forcing those later tickets to invent basic link retrieval from scratch.

acceptance_criteria[]
A client can fetch one existing link by slug through an API endpoint
A missing slug on the single-link read endpoint returns the current RFC 7807 not-found response style
A client can list recent links through an API endpoint
The list endpoint returns deterministic ordering
The list endpoint supports a simple limit query parameter with a sensible default and maximum
Invalid limit values return a clear client error
Existing create-link behavior remains unchanged
Existing redirect behavior remains unchanged
Existing validation rules remain unchanged
Existing tests still pass or are updated appropriately
New focused tests cover the new read and list endpoints
No unnecessary schema or infrastructure changes are introduced
code_target[]
apps/api
proof[]
fetching an existing link by slug works
fetching a missing slug returns the current Problem Details 404 shape
listing recent links works with deterministic ordering
limit handling works
passing automated tests
delivery_note[]

Deliberately postponed: update/delete endpoints, search, filtering, pagination beyond a simple limit, ownership, quotas, analytics, caching, and UI/admin work.