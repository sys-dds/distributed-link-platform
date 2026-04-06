
### 🚀 TICKET-038

#### title[]

Finish global redirect failover for real, close live analytics freshness, and land the final production-hardening pack: security/config hygiene, rollout safety, observability/performance/cost proof

#### technical_detail[]

PR 32 added the right seam, but not the full end-state.

TICKET-038 should be a **very large endgame slice** that folds together the unfinished parts of 037 and the next major polish layers, instead of spreading them across multiple small tickets.

This ticket should combine:

##### 1. Finish the still-open 037 correctness gap

* close the **live** analytics freshness gap, not just rebuild-time invalidation
* make click-driven and rollup-driven cache invalidation deterministic
* ensure traffic summary, top links, trending, and recent activity do not serve stale data after new traffic
* preserve replay/rebuild convergence

##### 2. Turn the multi-region seam into real failover behavior

* keep region-aware redirect config
* add explicit redirect failover behavior for degraded primary lookup / degraded dependencies
* make local multi-region simulation more realistic
* define and enforce clear behavior for:

    * cache hit / cache miss
    * primary lookup failure
    * failover-region posture
    * degraded analytics-write posture
* keep redirect hot-path correctness first

##### 3. Bundle security/config hardening

* tighten secret/config hygiene for runtime, datasource, Redis, and auth settings
* add stricter startup validation for unsafe production-shaped combinations
* improve security/audit events for auth/runtime/config failures
* make runtime-role exposure safer and more explicit

##### 4. Bundle rollout-safe evolution

* extend compatibility tests for key API and event contracts
* add mixed-runtime / partial-rollout safety checks where practical
* ensure frontend-facing contracts stay stable for the future Next.js app
* validate risky upgrade/config combinations early

##### 5. Add the final serious observability/performance/cost proof layer

* add focused hot-path performance proof for redirect
* add focused query-path performance proof for owner discovery/analytics
* add stronger operational signals for:

    * redirect failover activation
    * analytics freshness invalidation
    * cache degradation/fallback
    * query datasource fallback
    * worker backlog / reclaim / retry posture where useful
* add capacity/cost notes in code/test proof shape where practical, not fluff

This should be treated as the **final major backend hardening pack**, not a narrow follow-up.

#### feature_delivered_by_end[]

The platform has:

* deterministic live analytics freshness
* real redirect failover behavior, not just config placeholders
* stronger security/config/runtime safety
* rollout-safe evolution posture
* meaningful observability/performance/cost proof
* a much more polished senior/staff-level production story

#### how_this_unlocks_next_feature[]

This unlocks the final backend finish cleanly:

* last-mile repo polish
* architecture/runbook/interview packaging
* clean handoff into the Next.js frontend build

#### acceptance_criteria[]

* analytics caches invalidate or refresh deterministically after live click changes
* analytics caches invalidate or refresh deterministically after rollup changes
* traffic summary / top links / trending / recent activity do not serve stale data after new traffic
* redirect runtime supports explicit failover behavior under degraded primary lookup and cache states
* local multi-region simulation is stronger and tested
* unsafe runtime/config combinations fail fast
* security/audit events are improved for auth/runtime/config failure cases
* key API/event contracts remain compatibility-tested
* redirect hot path and owner query hot paths have focused reproducible performance proof
* public redirect behavior remains correct and fast
* control-plane and worker separation remains intact
* no repo churn in `docs/tickets.md`, README, or Postman

#### code_target[]

* live click / rollup consumer paths
* analytics cache invalidation paths
* redirect runtime configuration / failover behavior
* runtime/config/startup validators
* security/audit event paths
* compatibility tests for key contracts
* health/metrics/observability wiring
* focused performance/failover tests
* local runtime/compose wiring only where needed
* do **not** touch repo ticket-tracking/docs files

#### proof[]

* targeted tests proving live analytics freshness after click changes
* targeted tests proving analytics freshness after rollup updates
* targeted tests proving redirect failover behavior under degraded primary lookup
* targeted tests proving unsafe runtime/config combinations fail fast
* targeted tests proving security/audit events for auth/runtime/config failures
* targeted tests proving key API/event contracts remain compatible
* focused reproducible performance evidence for redirect and owner query paths
* actual compile/test command output with passing results

#### delivery_note[]

This is intentionally a **huge** ticket.

It folds together:

* the unfinished correctness from 037
* real redirect failover behavior
* security/config hardening
* rollout-safe evolution
* observability/performance/cost proof

Do **not** split these into several smaller tickets unless the repo forces a clearly separate architectural cut.
