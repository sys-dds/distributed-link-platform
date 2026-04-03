
## TICKET-020 - Move analytics off the redirect path with Kafka and transactional outbox

#### title[]

Move analytics off the redirect path with Kafka and transactional outbox

#### technical_detail[]

Evolve the Link Platform analytics flow from synchronous redirect-path persistence into a reliable event-driven pipeline.

Today, successful redirects synchronously persist click data and update analytics-related storage inside the request path. This ticket should remove analytics persistence work from the redirect hot path and replace it with transactional event capture plus asynchronous processing.

At minimum, the implementation should:

* keep redirect behavior unchanged for clients
* stop doing analytics persistence work directly on the redirect request path
* write a durable analytics outbox record transactionally with the redirect success event
* relay outbox records to Kafka
* consume Kafka click events in a dedicated background analytics component
* update raw click storage and daily rollups asynchronously from the consumer side

The redirect path should still resolve active links and return the same redirect response as today, but analytics capture must no longer depend on synchronous click persistence work completing inside the HTTP request.

The outbox design should be reliable and explicit:

* the application must durably record an analytics event in the same database transaction as the redirect-side mutation that makes the event valid to publish
* a relay process inside the current application is acceptable for this stage
* published events should be marked/managed so they are not blindly re-sent forever

The consumer side should stay intentionally small:

* consume click analytics events from Kafka
* persist raw click records
* maintain the current daily rollups
* preserve existing reporting behavior for traffic summary, top links, and trending views

Keep the implementation practical and constrained. This is not yet a general event platform. Do not add notifications, RabbitMQ, realtime push, dashboards, distributed sagas, or complex schema/versioning machinery unless directly required.

The current create/read/list/update/delete/search/suggestions/expiration/activity-feed/trending/Problem Details behavior should remain unchanged outside the analytics-pipeline internals.

#### feature_delivered_by_end[]

Successful redirects no longer perform analytics persistence synchronously on the hot path. Analytics events are captured reliably through a transactional outbox, published to Kafka, consumed asynchronously, and still power the existing analytics/reporting surfaces.

#### how_this_unlocks_next_feature[]

This creates the first real event-driven backbone for the platform and provides the reliable publishing foundation that later notifications, feed propagation, and async platform capabilities can build on.

#### acceptance_criteria[]

* Successful redirects still return the same redirect behavior as before
* Analytics persistence work is removed from the synchronous redirect hot path
* A durable outbox record is written transactionally for successful redirect analytics events
* Outbox records are relayed to Kafka
* A Kafka consumer processes click analytics events asynchronously
* Raw click storage is still maintained
* Daily rollups are still maintained
* Existing traffic summary, top-links, and trending endpoints still work from asynchronously maintained data
* Missing-link and expired-link redirects do not produce analytics events
* Event publishing is implemented with explicit outbox state management, not fire-and-forget best effort
* Existing tests still pass or are updated appropriately
* New focused tests cover outbox writing, relay behavior, consumer behavior, and preserved reporting correctness
* Only the minimum schema, infrastructure, and code changes needed for this event-driven analytics pipeline are introduced

#### code_target[]

* `apps/api`
* `infra/docker-compose`

#### proof[]

* successful redirects enqueue durable analytics outbox records
* outbox records are published to Kafka
* consumer updates raw click storage and rollups
* analytics/reporting endpoints still return correct data after asynchronous processing
* missing and expired redirects do not enqueue analytics events
* passing automated tests

#### delivery_note[]

Deliberately postponed: RabbitMQ, notifications, realtime push, dashboards, advanced event schema/versioning, bot filtering, deduplication beyond what is directly needed for reliability, and broader multi-consumer event-platform work.
