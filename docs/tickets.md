TICKET-014 - Add link expiration and lifecycle-aware reads
title[]

Add link expiration and lifecycle-aware reads

technical_detail[]

Expand the Link Platform lifecycle model by adding optional expiration support to links and enforcing expiration in both the control-plane read APIs and the redirect path.

This ticket should introduce an optional expiration timestamp for links. A link may either have no expiration or have a concrete expiration time. The platform must allow expiration to be set at create time and updated later through the existing control-plane mutation flow.

Once a link is expired, it must no longer behave like an active link:

redirects for expired links must return not found using the current RFC 7807 response style
single-link control-plane reads for expired links must return not found using the current RFC 7807 response style
list results should exclude expired links by default so the control plane shows currently active links unless later tickets add historical views

Keep the implementation intentionally small and lifecycle-focused. Reuse the existing PostgreSQL-backed design and current application service style. Do not add a scheduler, background cleanup worker, soft-delete system, restore flow, or archival system in this ticket. Expiration should be enforced at read/resolve time using the current clock.

The list endpoint should continue to work with deterministic ordering for active links. The update API should support changing the expiration timestamp along with the current destination URL mutation capability, but the slug must remain immutable.

Do not broaden this ticket into quotas, plans, analytics, auth, ownership, caching, or advanced search/filtering. Keep the scope focused on expiration as a first-class lifecycle rule.

feature_delivered_by_end[]

Links can optionally expire, expired links no longer resolve or appear in active reads, and the control plane can create and update links with expiration data.

how_this_unlocks_next_feature[]

This adds a real lifecycle rule that later plans/quotas, notifications, analytics, and admin features can build on, while making the link model more realistic and production-shaped.

acceptance_criteria[]
A link can be created with no expiration or with an expiration timestamp
A link can be updated to change its expiration timestamp
Expired links do not redirect
Expired links are not returned by single-link reads
Expired links are excluded from the default recent-links list
Active links continue to behave normally
Existing target URL validation rules remain unchanged
Existing Problem Details error style remains unchanged
Existing create/update/delete/read/list behavior for non-expired links remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover expiration behavior
Only the minimum schema and persistence changes required for expiration are introduced
code_target[]
apps/api
proof[]
creating a link with expiration works
updating expiration works
expired links return not found on read and redirect
active links still read and redirect correctly
expired links are excluded from default list results
passing automated tests
delivery_note[]

Deliberately postponed: background cleanup, archival/history views, restore/undelete flows, quotas/plans, notifications, auth, ownership, caching, and advanced search/filtering.