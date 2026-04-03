
## TICKET-021 - Complete async analytics cutover with ordered Kafka event processing

#### title[]

Complete async analytics cutover with ordered Kafka event processing

#### technical_detail[]

Finish the analytics migration so redirect requests no longer perform synchronous analytics persistence work, and make the Kafka event flow explicitly ordered and safe for continued growth.

The previous ticket introduced Kafka, an analytics outbox, a relay, and a consumer, but the redirect path still performs direct click persistence work. This ticket must complete the cutover so the redirect hot path only writes the durable outbox event and no longer writes raw click analytics or rollup data synchronously.

At the same time, formalize the event-processing model for redirect analytics by defining and enforcing a clear partitioning and ordering strategy. Redirect-click events for the same link should be published with a stable partition key so per-link ordering is preserved in Kafka. The consumer side should continue to be idempotent and should preserve reporting correctness when events are replayed or redelivered.

At minimum, this ticket should:

* remove direct analytics persistence from the redirect hot path
* keep the redirect HTTP behavior unchanged
* keep durable outbox event creation for successful active-link redirects
* publish redirect-click events to Kafka using a stable per-link event key
* keep consumer-side click persistence and rollup maintenance asynchronous
* make ordering assumptions explicit in code/config/tests
* preserve existing analytics reporting behavior from the client’s point of view

The consumer should continue to persist raw click records and maintain current daily rollups. Existing traffic-summary, top-links, and trending endpoints should continue to work, but now from a fully asynchronous analytics pipeline.

Keep the implementation intentionally small and practical. Do not broaden this ticket into new event types, notifications, RabbitMQ, realtime streaming, dashboards, or a general event-platform abstraction. This ticket is specifically about finishing the redirect-click analytics cutover and making ordered event processing explicit and reliable.

#### feature_delivered_by_end[]

Redirect requests no longer persist analytics synchronously, and redirect-click events are processed asynchronously through Kafka with a clear per-link ordering model.

#### how_this_unlocks_next_feature[]

This completes the first real event-driven backbone properly, so later notifications, feed propagation, additional consumers, and richer async workflows can build on a clean, believable foundation instead of a half-sync/half-async compromise.

#### acceptance_criteria[]

* Successful redirects still return the same redirect behavior as before
* Direct analytics persistence is removed from the synchronous redirect hot path
* Successful active-link redirects still create a durable analytics outbox record
* Missing-link and expired-link redirects do not create analytics events
* Redirect-click events are published to Kafka with a stable per-link event key
* Consumer-side click persistence remains idempotent
* Consumer-side daily rollup maintenance remains correct
* Existing traffic-summary, top-links, and trending endpoints still work correctly from asynchronously maintained data
* Ordering expectations are explicit and covered for per-link event processing
* Existing tests still pass or are updated appropriately
* New focused tests cover:

    * no synchronous click persistence on redirect
    * outbox-only redirect behavior
    * stable Kafka event key usage
    * idempotent consumer behavior
    * preserved reporting correctness after async processing
* No unnecessary new infrastructure or broad refactors are introduced

#### code_target[]

* `apps/api`
* `infra/docker-compose` only if a very small Kafka config adjustment is directly required

#### proof[]

* successful redirects write only outbox analytics records on the request path
* Kafka publishes redirect-click events keyed by slug
* consumer persists clicks and rollups asynchronously
* analytics reporting still works correctly
* missing and expired redirects do not generate analytics events
* passing automated tests

#### delivery_note[]

Deliberately postponed: additional event types, RabbitMQ, notifications, realtime streaming, dashboards, consumer groups for other domains, advanced schema/versioning systems, and broader event-platform work.
