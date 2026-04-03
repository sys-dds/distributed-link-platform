TICKET-018 - Add link metadata and richer discovery search
title[]

Add link metadata and richer discovery search

technical_detail[]

Expand the Link Platform control plane so links can carry simple metadata and can be discovered through richer search behavior.

This ticket should introduce optional link metadata fields and extend the existing list/search surface so clients can find links more practically than only searching slug and original URL. The implementation should stay focused on discovery and metadata only.

At minimum, the system should support:

optional title on a link
optional tags on a link
hostname-aware discovery based on the original URL
richer search across:
slug
original URL
title
tags
hostname
lightweight autocomplete suggestions for the control plane

The metadata should be supported on both create and update flows. Existing behavior should remain unchanged for clients that do not provide metadata.

The existing list/search endpoint should continue to support:

current lifecycle filtering
current limit behavior
deterministic ordering

Autocomplete should stay intentionally small. A good result is a dedicated suggestions endpoint or a minimal extension of the existing control-plane search surface that returns compact suggestion items based on current link data. It does not need ranking sophistication, typo tolerance, or full-text search.

Keep the implementation intentionally practical and PostgreSQL/JDBC-friendly. Do not add a separate search engine, fuzzy search system, full-text infrastructure, advanced ranking, synonyms, stemming, or recommendation logic. Do not broaden into ownership, auth, quotas, feeds, analytics redesign, or UI work.

feature_delivered_by_end[]

Links can store lightweight metadata and the control plane can search and autocomplete across more useful discovery fields, making the platform meaningfully easier to browse and manage.

how_this_unlocks_next_feature[]

This creates the richer discovery surface that later admin workflows, feeds, trending views, ownership, and UX layers can build on without inventing metadata and search basics later.

acceptance_criteria[]
A link can be created with optional metadata fields
A link can be updated to change optional metadata fields
Existing create/update behavior still works when metadata is absent
The list/search surface supports searching by:
slug
original URL
title
tags
hostname
Existing lifecycle filtering continues to work
Existing limit handling continues to work
Ordering remains deterministic
A lightweight autocomplete/suggestions API is exposed
Suggestions return compact useful results and remain deterministic
Existing create/read/list/update/delete/redirect/expiration/analytics behavior remains unchanged outside the new metadata/search capability
Existing Problem Details handling style remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover metadata persistence, richer search, and autocomplete behavior
Only the minimum schema and persistence changes needed for metadata/discovery are introduced
code_target[]
apps/api
proof[]
links can be created and updated with metadata
richer search works across metadata and hostname
autocomplete suggestions work
lifecycle filtering still works
passing automated tests
delivery_note[]

Deliberately postponed: full-text search, fuzzy matching, advanced ranking, separate search infrastructure, ownership/auth, quotas, feeds, caching, and UI/admin work.