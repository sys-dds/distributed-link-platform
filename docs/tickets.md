TICKET-013 - Add basic link lifecycle mutation APIs
title[]

Add basic link lifecycle mutation APIs

technical_detail[]

Expand the control-plane API by adding the first mutation endpoints for existing links. This ticket should let clients update a link’s destination URL and delete an existing link, while keeping the current create-link, redirect, read/list, validation, Problem Details, persistence, and observability behavior intact.

At minimum, the implementation should add:

an endpoint to update the originalUrl of an existing link identified by slug
an endpoint to delete an existing link identified by slug

The slug should remain immutable. This ticket must not introduce slug renaming.

The update behavior must reuse the current validation rules that already apply to target URLs, including invalid URL rejection and self-target rejection. The update flow should return a clean success response for the updated link and should return the existing RFC 7807 not-found response shape when the slug does not exist.

The delete behavior should remove the link from the current runtime source of truth so that:

future control-plane reads for that slug return not found
future redirects for that slug return not found

Keep the implementation intentionally small. Prefer extending the current application service and JDBC persistence style rather than adding new abstraction layers. Do not broaden this ticket into search, auth, ownership, quotas, analytics, caching, soft-delete lifecycle, audit history, or optimistic locking yet.

feature_delivered_by_end[]

The platform supports basic lifecycle management for links through update and delete APIs, making the control plane meaningfully useful beyond create/read/list.

how_this_unlocks_next_feature[]

This completes the first practical link-management surface that later ownership, search, feeds, quotas, and concurrency-control work can build on.

acceptance_criteria[]
A client can update the destination URL of an existing link by slug
Updating a missing slug returns the current RFC 7807 not-found response style
Updating a link reuses the current URL validation rules, including self-target rejection
A client can delete an existing link by slug
Deleting a missing slug returns the current RFC 7807 not-found response style
After deletion, single-link reads for that slug return not found
After deletion, redirects for that slug return not found
Existing create-link behavior remains unchanged
Existing read/list behavior remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover update and delete behavior
No unnecessary schema or infrastructure changes are introduced unless a very small persistence change is directly justified
code_target[]
apps/api
proof[]
updating an existing link works
updating a missing link returns the current Problem Details 404
deleting an existing link works
deleted links no longer resolve through control-plane read or redirect
passing automated tests
delivery_note[]

Deliberately postponed: slug renaming, soft delete, audit history, ownership, auth, quotas, search, analytics, caching, and optimistic locking.