### 🚀 TICKET-036

#### title[]

Build the resilience and recovery proof pack: deterministic analytics freshness, backup/restore and retention, failure drills, and safer rollout/contract posture

#### technical_detail[]

PR 30 improved runtime/read-scaling posture, but it stopped short of the next truly valuable end-state.

TICKET-036 should be a **large resilience slice** that combines:

### Part 1 — finish the missing 035 hardening

* make analytics freshness deterministic after click and rollup changes
* ensure traffic summary, top links, trending links, and recent activity do not serve stale cached data after new traffic
* add focused proof for redirect hot path and owner query paths so “performance/degradation proof” becomes real instead of implied

### Part 2 — backup / restore / retention / archival

Add real operational data protection posture:

* backup and restore flows for PostgreSQL data that matter to the platform
* retention and archival rules for analytics/history where appropriate
* documented and testable recovery path inside the code/integration surface
* keep it practical and local-dev/prod-shaped, not enterprise theater

### Part 3 — resilience drills

Add failure-mode proof for the critical runtime dependencies:

* Redis unavailable
* dedicated query datasource unavailable
* worker restart / replay / projection rebuild recovery
* outbox backlog / parked / retry posture under failure
* restore + rebuild convergence after recovery

### Part 4 — safer rollout and contract posture

Add the first real release-safety layer:

* explicit versioned API / event contract posture where needed
* compatibility checks for important serialized event shapes
* rollout-safe startup validation for runtime modes / datasource expectations
* keep contracts friendly for the future Next.js frontend
* do not add a giant release system; keep it practical

This is the right next ticket because it combines:

* the missing hardening from 035
* real operational recovery posture
* resilience proof
* safer evolution posture

That gets you closer to the polished end-state much faster than another narrow ticket.

#### feature_delivered_by_end[]

The platform has:

* deterministic analytics freshness after new click traffic
* real backup/restore and retention posture
* recovery drills proving replay/rebuild/degradation behavior
* safer rollout and contract-evolution posture
* stronger evidence that the platform is production-shaped, not just feature-rich

#### how_this_unlocks_next_feature[]

This unlocks the remaining destination slices cleanly:

* multi-region redirect architecture
* stronger security hardening
* final performance/observability/cost packaging
* final runbooks/portfolio/interview packaging

#### acceptance_criteria[]

* analytics caches are invalidated or refreshed deterministically after click and rollup changes
* owner-facing traffic summary / top links / trending / recent activity do not serve stale results after new traffic
* focused redirect and owner-query performance proof exists and is reproducible
* backup flow exists and restore flow is proven against a realistic dataset
* retention / archival behavior is explicit for data that should not grow unbounded
* worker replay / projection rebuild after restore converges correctly
* Redis outage behavior remains correct
* dedicated query datasource outage behavior remains correct
* runtime startup validation is safer and clearer
* important API / event contract changes are guarded with compatibility-oriented tests
* no repo churn in `docs/tickets.md`, README, or Postman

#### code_target[]

* analytics click / rollup consumer path
* Redis invalidation / cache adapter paths
* owner analytics/query service/store paths
* runtime health / startup validation wiring
* backup/restore/retention support in infra/scripts/test harness where appropriate
* projection rebuild / replay paths
* compatibility tests for important API/event contracts
* focused resilience/performance tests
* do **not** touch repo ticket-tracking/docs files

#### proof[]

* targeted tests proving analytics freshness after click changes
* targeted tests proving analytics freshness after rollup updates
* targeted tests proving restore + replay + rebuild convergence
* targeted tests proving Redis outage fallback
* targeted tests proving query datasource outage fallback
* targeted tests proving runtime startup validation
* focused performance evidence for redirect and owner query hot paths
* actual compile/test command output with passing results

#### delivery_note[]

This is intentionally a **huge** ticket.

It folds together:

* the missing hardening from 035
* backup/restore + retention
* resilience drills
* rollout/contract safety

Do **not** split those into several small tickets unless the repo forces a clearly separate architectural cut.
