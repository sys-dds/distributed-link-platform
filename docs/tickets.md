

## 🎯 TICKET-026

#### title[]

Add link lifecycle event backbone and move activity feed to async projection

#### technical_detail[]

Broaden the event-driven architecture beyond redirect-click analytics by introducing a durable lifecycle event pipeline for link create, update, delete, and expiration-state changes, then move activity-feed persistence off the synchronous control-plane write path into an asynchronous worker-maintained projection.

The project already has a real async analytics backbone with outbox delivery, Kafka publishing, worker/runtime separation, claim/lease safety, retry scheduling, parked failures, and operator diagnostics. The next step should reuse those lessons for a second domain that matters to the product: link lifecycle events.

Today the activity feed is product-useful, but it is still conceptually tied to synchronous control-plane mutations. This ticket should make lifecycle changes durable as events first, then let the worker build the feed projection asynchronously. Keep the existing activity feed API behavior stable from the client point of view, including snapshot usefulness after deletion.

At minimum, this ticket should:

* introduce durable lifecycle events for:

  * link created
  * link updated
  * link deleted
  * expiration updated
* ensure lifecycle event creation happens transactionally with the control-plane write
* publish lifecycle events to Kafka with stable per-link keying
* run lifecycle publishing in worker-compatible async flow, not as direct synchronous side-effect fanout
* build the existing activity feed asynchronously from lifecycle events
* remove direct synchronous activity-feed persistence from control-plane write paths
* keep existing feed endpoint behavior and response shape unchanged
* preserve feed usefulness after deletion by keeping snapshot-style event payloads
* make lifecycle consumer idempotent
* add minimal diagnostics/metrics for the lifecycle pipeline, reusing the current style
* keep implementation intentionally small and practical; do not build a generic event platform

Keep this focused on lifecycle events + activity projection. Do not broaden into notifications, websocket push, search indexing, or service extraction yet.

#### feature_delivered_by_end[]

Link writes now emit durable lifecycle events, and the activity feed is maintained asynchronously by a worker-driven projection instead of synchronous write-path side effects.

#### how_this_unlocks_next_feature[]

This gives the platform its second real event domain and first non-analytics async projection. Later notifications, audit trails, search projection, realtime updates, and broader service decomposition can build on an event backbone that is no longer analytics-only.

#### acceptance_criteria[]

* create/update/delete/expiration-update operations still behave the same from the client point of view
* lifecycle events are written durably in the same transaction as the control-plane mutation
* lifecycle events are published with stable per-link keying
* activity-feed persistence is removed from the synchronous control-plane write path
* activity feed is rebuilt asynchronously from lifecycle events
* activity feed endpoint behavior remains unchanged from the client point of view
* deleted links still appear meaningfully in the feed because lifecycle event payloads snapshot enough data
* lifecycle consumer is idempotent under replay/redelivery
* worker/runtime split continues to work
* focused diagnostics/metrics exist for lifecycle backlog / parked failures / projection processing
* tests cover:

  * lifecycle event creation on create/update/delete/expiration update
  * no direct synchronous activity-feed persistence on write path
  * async consumer writing feed projection
  * idempotent lifecycle consumer behavior
  * deleted-link feed snapshot correctness
  * existing feed endpoint still returning expected data
* no README, Postman, or ticket-tracking repo changes

#### code_target[]

* `apps/api`
* Flyway migration(s) for lifecycle outbox / projection support
* `application.yml` for small lifecycle topic/retry settings only
* `infra/docker-compose` only if a tiny config addition is directly required

#### proof[]

* link mutations write lifecycle outbox records transactionally
* lifecycle events publish through Kafka keyed by link
* worker asynchronously writes activity feed records
* control-plane write path no longer persists feed entries directly
* existing activity feed still works correctly
* automated tests pass

#### delivery_note[]

Deliberately postponed: notifications, websocket/SSE updates, search indexing projections, dead-letter topics for consumer failures, service extraction, auth, quotas, caching, and broader event-platform abstractions.

---