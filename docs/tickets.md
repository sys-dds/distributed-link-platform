
### 🚀 TICKET-035

#### title[]

Build real read-scaling and SRE hardening: actual query-datasource routing, deterministic analytics freshness, role-aware health/metrics, and performance/degradation proof

#### technical_detail[]

PR 29 created the right seam, but it stopped at **runtime separation + query groundwork**.

TICKET-035 should turn that into a much more production-shaped slice by combining:

### Part 1 — finish the missing TICKET-034 hardening

* close the still-open analytics freshness gap
* make click/rollup-driven analytics cache invalidation deterministic
* prove discovery/search/analytics rebuild and cache behavior still converge correctly

### Part 2 — promote the query seam into real read-scaling posture

* replace the “same-DataSource query template” setup with a **real query-datasource configuration**
* keep primary write datasource and query datasource explicitly separate in configuration
* default query datasource to primary when a dedicated query source is not configured
* route owner discovery/search/analytics/list/detail reads through the query path intentionally
* keep mutation/write paths on the primary write datasource

### Part 3 — role-aware operational/SRE hardening

Add a stronger operations surface for the split runtimes:

* role-aware readiness/health behavior
* clearer metrics for:

    * cache hit/miss/fallback
    * analytics outbox backlog / parked / retry posture
    * lifecycle outbox backlog / parked / retry posture
    * projection job lag/failure/reclaim posture
    * rate-limit/security-event counters where useful
* make runtime-specific degradation more visible without overbuilding dashboards inside the repo

### Part 4 — performance and degradation proof

Add focused proof that the new architecture actually behaves well:

* redirect runtime hot-path verification
* owner control-plane read-path verification through query datasource
* query-datasource outage / degradation behavior
* Redis degradation still behaving correctly
* targeted performance evidence for hot paths and owner query screens

Keep this explicit and production-shaped.
Do **not** introduce full replica infrastructure or multi-region yet.

#### feature_delivered_by_end[]

The platform has:

* real query-datasource routing posture instead of just a placeholder seam
* deterministic analytics freshness after click/rollup changes
* stronger runtime-specific health/metrics visibility
* better proof that redirect, control-plane, and worker runtimes behave correctly under load and degradation

#### how_this_unlocks_next_feature[]

This unlocks the next major destination slices cleanly:

* backup/restore + retention + recovery drills
* multi-region redirect architecture
* stronger security/release/resilience proof packs
* final performance/observability/staff-level packaging

#### acceptance_criteria[]

* query datasource is separately configurable from the write datasource
* query reads default safely to primary when a dedicated query datasource is not configured
* owner discovery/search/analytics/list/detail reads go through the query path intentionally
* mutations remain on the primary/write path
* analytics caches are invalidated or refreshed deterministically after click/rollup changes
* redirect runtime still behaves correctly and remains slim
* control-plane and worker runtime behavior remains correctly separated
* runtime-specific health/metrics expose useful operational signals
* degraded query datasource behavior is explicit and proven
* Redis degradation still does not break correctness
* no repo churn in `docs/tickets.md`, README, or Postman

#### code_target[]

* runtime datasource configuration
* `PostgresLinkStore`
* owner-facing query/read paths in `LinkApplicationService` / `DefaultLinkApplicationService`
* analytics click/rollup consumer path
* cache invalidation hooks
* runtime health/metrics wiring
* focused runtime/query/cache tests
* focused performance/degradation proof
* do **not** touch repo ticket-tracking/docs files

#### proof[]

* targeted tests proving query reads route through the query datasource path
* targeted tests proving writes remain on the primary path
* targeted tests proving analytics freshness after click/rollup changes
* targeted tests proving redirect runtime still behaves correctly
* targeted tests proving worker/control-plane separation still holds
* targeted tests proving query-datasource fallback/degradation behavior
* targeted tests proving Redis degradation still works
* focused performance evidence for redirect and owner query paths
* actual compile/test command output with passing results

#### delivery_note[]

This is intentionally a **large** ticket.

It must:

* finish the missing hardening from PR 29
* turn the query seam into real read-scaling posture
* add the next serious SRE/operational proof layer

Do **not** split this into separate tickets for:

* cache freshness
* query datasource
* metrics/health
* performance proof

Do it as one coherent production-hardening jump.