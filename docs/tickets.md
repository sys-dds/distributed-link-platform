TICKET-015 - Add link search and lifecycle-aware control-plane filtering
title[]

Add link search and lifecycle-aware control-plane filtering

technical_detail[]

Expand the control-plane read surface so clients can find links more practically instead of only fetching one slug or listing recent active links.

This ticket should extend the existing link list endpoint with a small, useful search and filtering capability backed by PostgreSQL. Keep the current control-plane style and current lifecycle rules intact.

At minimum, the list API should support:

searching by slug
searching by original URL
filtering by lifecycle state:
active
expired
all

The default behavior should remain safe and practical for the current stage:

default lifecycle filter should continue to represent currently active links
ordering should remain deterministic
the current limit handling should continue to apply

Keep the implementation intentionally small. This is not a full search platform. Do not add full-text search, tags, hostname extraction, ranking, advanced pagination, fuzzy matching, or indexing beyond what is directly useful for this ticket. Use simple explicit query parameters and a direct JDBC-backed implementation that matches the current code style.

The single-link read, create, update, delete, redirect, expiration, validation, Problem Details handling, and observability behavior should remain unchanged outside of the new list-search/filter capability.

feature_delivered_by_end[]

The control plane can search and filter links by basic fields and lifecycle state, making the system meaningfully easier to inspect and use as a real admin surface.

how_this_unlocks_next_feature[]

This creates the discovery surface that later ownership, search refinement, feeds, analytics views, and admin workflows can build on without inventing basic filtering from scratch.

acceptance_criteria[]
The existing list endpoint supports searching by slug
The existing list endpoint supports searching by original URL
The existing list endpoint supports lifecycle filtering for active, expired, and all
Default list behavior remains active-only
List ordering remains deterministic
Existing limit behavior remains in place
Invalid filter values return a clear client error
Existing create/update/delete/read/redirect behavior remains unchanged
Existing Problem Details handling style remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover search and lifecycle filtering behavior
No unnecessary schema or infrastructure changes are introduced unless a very small persistence change is directly justified
code_target[]
apps/api
proof[]
searching by slug works
searching by original URL works
filtering active/expired/all works
default list still returns active links only
invalid filter values return a clear client error
passing automated tests
delivery_note[]

Deliberately postponed: full-text search, fuzzy matching, ranking, hostname/tag search, advanced pagination, ownership/auth, quotas, analytics, caching, and UI/admin work.