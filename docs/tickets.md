🚀 TICKET-034
title[]

Extract the redirect runtime, harden worker/projection execution, and isolate owner query reads with replica-aware posture

technical_detail[]

From TICKET-034 onward, tickets should be larger and more destination-oriented.

This ticket intentionally combines the old “dedicated redirect runtime,” “worker/projection hardening,” and “read/query isolation” trajectory into one major platform jump.

The platform now has:

safe writes
async relays/projections
owner-authenticated control-plane APIs
owner discovery/search read models
Redis read caching
rate limiting/security events

The next best move is to turn this into a more production-shaped multi-runtime architecture inside one codebase.

Bundle all of this together:

Part 1 — dedicated redirect runtime boundary

Create a true redirect runtime role that serves only the hot public redirect path and its minimal supporting concerns.

It should:

expose public redirect endpoints
resolve slug -> destination quickly
record click intent / analytics event emission as already designed
expose minimal health/metrics needed for the redirect service
exclude owner control-plane endpoints, rebuild APIs, heavy query APIs, and worker duties

Keep this in the same repository/application codebase, but with a clean runtime boundary and startup role.

Part 2 — stronger runtime separation

Formalize runtime roles so the code can run cleanly as:

all
control-plane-api
redirect
worker

Make sure:

scheduled outbox relay / projection rebuild / background work runs only where it should
control-plane API hosts owner-authenticated CRUD/discovery/query endpoints
redirect runtime remains slim
worker runtime owns async relays, consumers, projection rebuilds, and background jobs
Part 3 — worker/projection hardening

Strengthen the worker runtime so it looks production-shaped:

role-aware startup validation
clearer ownership of scheduled jobs
stronger projection/rebuild diagnostics
lag/lease/failure metrics for relay/projection paths
clearer degraded/parked/retry visibility
guardrails so redirect/control-plane roles do not accidentally run worker logic
Part 4 — query/read isolation with replica-aware posture

Split the owner-facing query workload more intentionally from the write path.

Add a practical read-isolation layer for:

owner discovery queries
owner analytics queries
owner detail/list reads where sensible

This does not need real infra-level replicas yet, but it should:

introduce a clear query/read datasource posture that can target primary now
support a future read-replica route cleanly
separate heavy query code paths from mutation code paths
allow role-aware routing for query workloads

If the existing app already has multiple datasource wiring patterns, extend them cleanly. Do not invent a giant abstraction.

Part 5 — carry forward the unfinished 033 hardening

Also finish the still-under-proven parts from the previous slice:

deterministic analytics cache freshness after click/rollup changes
clear rebuild/proof for discovery projection recovery
ensure cache invalidation and rebuild behavior remain correct after runtime separation
feature_delivered_by_end[]

The platform runs with a much more serious production shape:

dedicated redirect runtime for the hot path
clean control-plane vs worker vs redirect runtime boundaries
stronger worker/projection execution posture
query/read isolation shaped for future replica use
carried-forward cache/rebuild hardening from the prior slice
how_this_unlocks_next_feature[]

This unlocks the remaining big destination slices faster:

backup/restore + retention + recovery drills
multi-region redirect architecture
performance/observability/SRE packaging
stronger deployment/release posture
final portfolio/staff-level packaging
acceptance_criteria[]
runtime roles exist and are intentionally shaped: all, control-plane-api, redirect, worker
redirect runtime exposes the public redirect path and excludes control-plane/worker-only surfaces
control-plane runtime exposes owner APIs and excludes worker-only jobs
worker runtime owns background jobs, relays, consumers, and projection rebuild execution
startup/runtime guards prevent the wrong jobs/endpoints from running in the wrong role
owner discovery/analytics query paths run through a clearly isolated query/read path
query/read isolation is designed so a future replica can be introduced without redesign
analytics caches are invalidated or refreshed deterministically after click/rollup changes
discovery projection rebuild/recovery is proven
public redirect behavior remains correct and fast
no repo churn in docs/tickets.md, README, or Postman
code_target[]
runtime role configuration / boot wiring
redirect controller/runtime composition
control-plane API runtime wiring
worker scheduling / consumer / relay wiring
projection job / relay diagnostics
query/read datasource or repository routing layer
analytics click/rollup invalidation path
discovery rebuild/projection job path
focused runtime integration tests
do not touch repo ticket-tracking/docs files
proof[]
targeted tests proving redirect runtime serves public redirect and excludes owner control-plane endpoints
targeted tests proving control-plane runtime excludes worker jobs
targeted tests proving worker runtime excludes public/control-plane endpoint surface where appropriate
targeted tests proving query/read isolation paths behave correctly
targeted tests proving analytics cache freshness after click/rollup changes
targeted tests proving discovery projection rebuild converges correctly
targeted tests proving public redirect behavior remains unchanged
actual compile/test command output with passing results
delivery_note[]

This is intentionally a very large ticket.

It folds the old 034 + 035 + much of 036 direction into one coherent slice:

redirect runtime boundary
worker/projection hardening
query/read isolation
carried-forward missing hardening from 033

Do not split this into several mini-tickets unless the repo forces a truly separate architectural cut.