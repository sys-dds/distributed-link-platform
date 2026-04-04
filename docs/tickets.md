
## 🎯 TICKET-028

#### title[]

Build link catalog projection and upgrade projection jobs to chunked resumable execution

#### technical_detail[]

Broaden the event-driven architecture from analytics and activity feed into a true control-plane read-model layer by introducing a worker-maintained **link catalog projection**, and at the same time mature the new projection-job system so rebuild/replay work is **chunked, resumable, and progress-aware** instead of one-shot full-table processing.

The project now has:

* durable lifecycle history
* async lifecycle consumer
* async analytics pipeline
* projection jobs for replay/rebuild

That is a strong base, but the current projection jobs are still full-scan/full-rebuild operations, and core control-plane reads such as list/search/suggestions are still not clearly treated as a dedicated projection. The next step should fix both together.

This ticket should introduce a **`link_catalog_projection`** table maintained asynchronously from lifecycle events, then move the main control-plane read APIs onto that projection while preserving current response shapes and behavior from the client point of view.

At the same time, upgrade projection jobs so they can process in chunks with durable checkpoint state. That should apply to:

* existing `ACTIVITY_FEED_REPLAY`
* existing `CLICK_ROLLUP_REBUILD`
* new `LINK_CATALOG_REBUILD`

Keep this intentionally practical. Do not build a generic event platform or workflow engine.

#### feature_delivered_by_end[]

The platform has a real async-maintained control-plane catalog projection, and projection rebuild/replay jobs are chunked and resumable instead of one-shot operations.

#### how_this_unlocks_next_feature[]

This creates the first believable **read-model separation** for the control plane and gives you projection infrastructure that can support later search/index projections, service extraction, and larger rebuilds without manual DB surgery or fragile full-table jobs.

#### acceptance_criteria[]

* a new durable `link_catalog_projection` exists and is maintained asynchronously from lifecycle events
* lifecycle consumer updates the catalog projection for:

  * create
  * update
  * delete
  * expiration update
* catalog projection keeps enough snapshot data to serve current control-plane read behavior
* list/search/suggestions read from the catalog projection instead of rebuilding directly from the write model
* single-link control-plane read also uses the projection if it can be done cleanly without changing client behavior
* redirect behavior remains unchanged and continues to use the current runtime path
* current client-facing response shapes and semantics stay stable
* projection jobs support durable checkpoint/progress state
* projection jobs run in chunks instead of one-shot full-table operations
* failed chunked jobs can resume from saved checkpoint state
* existing `ACTIVITY_FEED_REPLAY` is converted to chunked replay
* existing `CLICK_ROLLUP_REBUILD` is converted to chunked rebuild
* new `LINK_CATALOG_REBUILD` can rebuild the catalog projection from lifecycle history
* worker/all runtime modes execute projection jobs
* api-only mode does not execute projection jobs
* metrics expose at minimum:

  * queued jobs
  * active jobs
  * started/completed/failed counts
  * job duration
  * job progress/checkpoint visibility, or the smallest equivalent useful signal
* focused tests cover:

  * lifecycle consumer maintaining catalog projection correctly
  * list/search/suggestions correctness from catalog projection
  * deleted/expired behavior preserved
  * chunked job progress and resume behavior
  * link catalog rebuild correctness
  * activity feed replay still idempotent after chunking
  * click rollup rebuild still deterministic after chunking
  * multi-worker-safe job claiming still works
* no README, Postman, or ticket-tracking repo changes

#### code_target[]

* `apps/api`
* Flyway migration(s) for:

  * `link_catalog_projection`
  * projection-job checkpoint/progress state
* `application.yml` for small chunk-size / poll settings only
* `infra/docker-compose` only if a tiny worker config addition is directly required

#### proof[]

* control-plane reads are served from the catalog projection
* lifecycle events maintain the projection asynchronously
* projection jobs are chunked and resumable
* catalog rebuild works correctly
* existing activity feed replay and click rollup rebuild still work correctly after migration to chunked execution
* automated tests pass

#### delivery_note[]