TICKET-016 — Add synchronous click analytics baseline and expose link traffic totals
title[]

Add synchronous click analytics baseline and expose link traffic totals

technical_detail[]

Expand the Link Platform from a CRUD-style control plane into a system that records real redirect usage. This ticket should capture a basic synchronous click analytics baseline on the redirect path and expose simple traffic totals through the existing control-plane read surfaces.

At minimum, the implementation should:

record one click event for each successful redirect
persist basic click metadata for each event
maintain or derive a simple per-link total click count
expose click totals on:
single-link read responses
recent/search/filter list responses

The click capture should happen only for successful redirects of active links. Missing links and expired links must not create click records.

Keep this intentionally small and practical. A good baseline event shape includes:

slug
clicked_at timestamp
request metadata that is directly available and useful now, such as:
user-agent
referrer
remote address or a lightweight IP representation

Do not overbuild the analytics model in this ticket. Do not add rollups, dashboards, Kafka, async workers, materialized views, deduplication, bot detection, or tenant analytics yet. This is the first baseline only.

The implementation may use either:

a separate link_clicks table plus a derived count query, or
a separate link_clicks table plus a simple stored counter update

Choose the smaller clean option that fits the current JDBC style and keeps the redirect path implementation understandable.

The existing create/update/delete/read/list/search/filter/expiration/Problem Details behavior should remain unchanged except for the addition of click totals in read/list responses and the fact that successful redirects now record clicks.

feature_delivered_by_end[]

Successful redirects create a basic persisted click trail, and the control plane can show simple traffic totals for links.

how_this_unlocks_next_feature[]

This creates the real analytics baseline that later rollups, reporting, trending views, async event pipelines, and realtime features can build on.

acceptance_criteria[]
A successful redirect records one click event
Missing-link redirects do not record clicks
Expired-link redirects do not record clicks
The system persists basic click data for recorded redirects
Single-link read responses include a total click count
Recent/search/filter list responses include a total click count
Existing redirect behavior remains unchanged apart from recording the click
Existing create/update/delete/read/list/search/filter/expiration behavior remains unchanged
Existing Problem Details behavior remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover click recording and click-count exposure
Only the minimum schema and persistence changes needed for this analytics baseline are introduced
code_target[]
apps/api
proof[]
successful redirects create persisted click records
click totals appear in single-link read responses
click totals appear in list responses
missing and expired redirects do not create clicks
passing automated tests
delivery_note[]

Deliberately postponed: rollups, reporting views, trending logic, dashboards, Kafka, async consumers, bot filtering, deduplication, tenant analytics, and realtime updates.