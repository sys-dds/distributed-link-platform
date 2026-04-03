TICKET-019 - Add platform activity feed and trending link views
title[]

Add platform activity feed and trending link views

technical_detail[]

Expand the control plane beyond CRUD/search/reporting by adding two higher-level discovery surfaces:

a recent platform activity feed
a trending-links view

This ticket should introduce a lightweight activity-event model for link lifecycle mutations and expose a feed endpoint that lets clients see recent create, update, and delete activity in deterministic order. The feed should be backed by persisted events, not reconstructed loosely from current link state, so delete events and historical updates can still appear even after the underlying link changes.

This ticket should also expose a trending-links endpoint that ranks links by recent traffic growth rather than only raw top-click totals. The implementation should reuse the existing click capture and daily rollup baseline where practical. A good first trending view compares one recent window against the immediately preceding window of the same size and ranks links by positive growth. Keep the supported windows intentionally small and explicit, such as 24h and 7d.

At minimum, the implementation should provide:

persisted activity events for:
link created
link updated
link deleted
an activity feed endpoint for recent events
a trending-links endpoint based on recent traffic growth
compact, useful response shapes for both endpoints

The activity feed should store enough snapshot data to remain useful even if the underlying link later changes or is deleted. A practical snapshot includes:

slug
event type
occurred-at timestamp
title if available
hostname if available

The trending endpoint should return compact link discovery information together with recent-window traffic numbers. A practical response includes:

slug
title
hostname
clicks in current window
clicks in previous comparable window
absolute growth

Keep the implementation intentionally small and JDBC/PostgreSQL-friendly. Do not broaden this ticket into auth/team scoping, notifications, realtime push, caching, Kafka, outbox, anomaly detection, recommendation logic, or dashboard UI work. This is the synchronous discovery/feed phase only.

The existing create/read/list/update/delete/search/suggestions/expiration/redirect/analytics-reporting behavior should remain unchanged outside the addition of these new endpoints and supporting persistence.

feature_delivered_by_end[]

The control plane exposes a recent platform activity feed and a trending-links view, making the platform feel more like a real operational product instead of just a set of CRUD/reporting endpoints.

how_this_unlocks_next_feature[]

This creates natural consumers for later async events, realtime updates, notification jobs, feeds, and richer home/dashboard experiences without forcing those later tickets to invent basic event and trending surfaces from scratch.

acceptance_criteria[]
The system persists activity events for link create, update, and delete operations
A client can request a recent activity feed
Activity feed ordering is deterministic
Delete events remain visible in the feed even after the link itself is gone
A client can request trending links for a supported recent window
Trending ranking is based on recent growth versus the previous comparable window, not only raw total clicks
Supported windows are explicit and limited, such as 24h and 7d
Invalid window values return a clear client error
Existing create/read/list/update/delete/search/suggestions/redirect behavior remains unchanged
Existing analytics reporting behavior remains unchanged
Existing Problem Details handling style remains unchanged
Existing tests still pass or are updated appropriately
New focused tests cover activity-event capture, feed behavior, trending behavior, deterministic ordering, and invalid-window handling
Only the minimum schema and persistence changes needed for feed/trending are introduced
code_target[]
apps/api
proof[]
create/update/delete operations persist activity events
activity feed returns recent events in deterministic order
delete events remain visible after link deletion
trending endpoint returns links ranked by recent growth
invalid window values return a clear client error
passing automated tests
delivery_note[]

Deliberately postponed: auth/team scoping, notifications, realtime streaming, Kafka/outbox, caching, anomaly detection, recommendation logic, dashboards, and UI/admin work.