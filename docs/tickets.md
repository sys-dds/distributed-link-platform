

## 🎯 TICKET-027

#### title[]

Add projection rebuild and replay operations for analytics and lifecycle

#### technical_detail[]

Operationalize the new event-driven read models by introducing explicit rebuild/replay jobs for the projections the platform now depends on.

The project now has two real async projection paths:

* analytics event processing that produces raw clicks and daily rollups
* lifecycle event processing that produces the activity feed projection

That is strong progress, but it is still missing a safe operator-grade recovery model. If the lifecycle consumer logic changes, if analytics rollups drift, or if a worker failure leaves projections incomplete, the current system depends too much on ad hoc repair. This ticket should add a small but real projection-operations capability so projections can be rebuilt or replayed intentionally and safely.

This ticket should add a dedicated projection-operations flow for:

* **activity feed replay** from durable lifecycle outbox history
* **daily click rollup rebuild** from durable click data

The implementation should stay practical and specific to existing projections. Do not build a generic workflow engine or broad platform abstraction.

At minimum, this ticket should:

* introduce a small projection-job model with durable job records
* support creating and tracking jobs such as:

  * `ACTIVITY_FEED_REPLAY`
  * `CLICK_ROLLUP_REBUILD`
* execute jobs in worker/all runtime modes only
* make job claiming safe for multiple workers
* provide progress and status tracking for jobs
* replay lifecycle history into `link_activity_events` idempotently
* rebuild daily click rollups from `link_clicks` deterministically
* expose minimal operator APIs for:

  * create rebuild/replay job
  * inspect job status
  * list recent jobs
* add focused metrics for:

  * job started/completed/failed counts
  * job duration
  * active job count or queued job count
* keep current API behavior unchanged from the client point of view
* keep implementation intentionally small and interview-explainable

Do not broaden this into generic backfill platforms, service extraction, dashboards, or auth yet.

#### feature_delivered_by_end[]

The platform gains first-class rebuild/replay operations for its async projections, so activity feed and analytics rollups can be repaired or regenerated intentionally instead of relying on manual DB fixes.

#### how_this_unlocks_next_feature[]

This makes the event-driven backbone operationally trustworthy. Later projection domains, service extraction, search/index projections, notifications, and replay-heavy distributed workflows can build on a recovery model that already exists.

#### acceptance_criteria[]

* activity feed replay jobs can rebuild the feed projection from durable lifecycle history
* replay is idempotent and does not create duplicate feed rows
* click rollup rebuild jobs can recompute daily rollups from durable click data
* rebuild output is deterministic and correct
* projection jobs are stored durably with status/progress/error summary
* jobs can be claimed and executed safely by worker instances
* worker/all runtime modes can execute jobs
* api-only mode does not execute jobs
* operator APIs exist to:

  * create a job
  * get a job by id
  * list recent jobs
* focused metrics exist for job counts and duration
* current feed/reporting endpoints still behave correctly after rebuild/replay
* focused tests cover:

  * activity feed replay correctness
  * replay idempotency
  * click rollup rebuild correctness
  * multi-worker-safe job claiming
  * job status/progress transitions
  * api mode not executing jobs
* no README, Postman, or ticket-tracking repo changes

#### code_target[]

* `apps/api`
* Flyway migration(s) for projection job state
* `application.yml` only for small job polling / batch settings
* `infra/docker-compose` only if a tiny worker config adjustment is directly required

#### proof[]

* activity feed can be replayed from lifecycle history
* daily rollups can be rebuilt from click history
* jobs are durable, observable, and multi-worker safe
* rebuilt projections remain correct
* automated tests pass

#### delivery_note[]

Deliberately postponed: generic workflow engines, dashboards, alerts, search index replay, service extraction, auth, quotas, caching, and broader event-platform abstractions.
