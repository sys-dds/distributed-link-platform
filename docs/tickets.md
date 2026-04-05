TICKET-032
title[]

Finish the owner-scoped query surface and add Redis-backed read acceleration, invalidation, and graceful degradation

technical_detail[]

TICKET-031 recovered the ownership/auth foundation and secured the main control-plane reads and writes, but the owner-facing query surface is still inconsistent because analytics/query methods remain global-shaped while get/list/suggestions are owner-scoped.

TICKET-032 should close that gap and deliver the next major platform slice in one coherent package:

finish the owner-facing control plane by making the remaining query/analytics reads owner-aware where they belong
shape those query responses so they are clean for the future Next.js UI
add Redis-backed read acceleration for hot paths
add explicit invalidation rules tied to link mutations and projection updates
preserve correctness when Redis is unavailable by falling back to the database and recording degradation signals
keep redirect public and fast
keep implementation explicit and JDBC/Spring-shaped; do not build a generic caching framework

Bundle these capabilities together:

owner-scoped query/auth boundary for remaining control-plane analytics/detail reads:
recent activity
traffic summary
top links
trending links
any equivalent owner-facing analytics endpoints already present
extend read-model / query behavior only as needed to support owner-scoped analytics cleanly
add Redis integration for:
public redirect resolution cache
owner-scoped link detail cache
owner-scoped list/search/suggestions cache where practical
/api/v1/me summary cache if it is cheap and clearly useful
owner-scoped analytics/query cache where practical
add deterministic cache keys that include owner scope where needed
add mutation-triggered and projection-triggered invalidation:
create/update/delete invalidates owner-scoped control-plane caches
lifecycle/catalog projection updates invalidate affected owner-scoped read-model caches
click/rollup changes invalidate affected analytics caches
add graceful degradation behavior:
Redis unavailable must not fail reads or writes
fallback to DB/query path
record metrics/logging when cache is unavailable or bypassed
keep contract responses stable and frontend-friendly
feature_delivered_by_end[]

The backend has a real owner-facing query surface and the first serious read-performance layer:

owner-scoped analytics/query reads
Redis acceleration for hot read paths
explicit invalidation tied to mutations and projections
graceful fallback when Redis is unavailable
control-plane APIs shaped better for the future Next.js app
how_this_unlocks_next_feature[]

This unlocks the next big slices cleanly:

richer search/index projection
cleaner frontend integration and faster UI screens
safer runtime separation later
better SRE/performance work because cache behavior is now real
stronger abuse/rate-limit posture because the read side is less DB-heavy
acceptance_criteria[]
remaining owner-facing analytics/query endpoints are owner-authenticated and owner-scoped
cross-owner query reads do not leak data
public redirect behavior remains anonymous and unchanged
Redis is used for configured hot read paths with deterministic keys
owner-scoped cache keys include owner identity where needed
mutations invalidate the relevant owner-scoped control-plane caches
projection/catalog updates invalidate the relevant query caches
click/rollup changes invalidate the relevant analytics caches
if Redis is unavailable, requests still succeed via DB fallback
cache degradation is observable via logs/metrics
owner-facing responses remain stable and frontend-friendly
no repo churn in docs/tickets.md, README, or Postman
code_target[]
current analytics/query controller(s) and owner-facing read endpoints
LinkApplicationService
DefaultLinkApplicationService
LinkStore
PostgresLinkStore
owner auth/query boundary wiring
lifecycle/catalog projection invalidation hooks
click/rollup invalidation hooks
Redis configuration + small explicit cache adapter(s)
integration/controller/projection/cache tests
do not touch repo ticket-tracking/docs files
proof[]
targeted tests proving owner-scoped recent activity / traffic summary / top links / trending links
targeted tests proving cross-owner analytics/query reads do not leak data
targeted tests proving redirect remains public with cache hit + miss behavior
targeted tests proving owner-scoped list/detail/search/suggestions cache keys do not collide across owners
targeted tests proving create/update/delete invalidate relevant caches
targeted tests proving projection rebuild/update invalidates affected caches
targeted tests proving click/rollup changes invalidate analytics caches
targeted tests proving Redis failure falls back cleanly without breaking responses
actual compile/test command output with passing results
delivery_note[]

This is intentionally a large ticket.

It must finish the missing owner-scoped query work left by TICKET-031 and land the Redis caching/invalidation/degradation slice in one coherent delivery.

Do not split “finish owner analytics” and “add caching” into separate mini-tickets.