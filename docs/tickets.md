
### 🚀 TICKET-023

The best next move is:

### **TICKET-023 — make the analytics outbox relay safe for multiple workers**

This is the right distributed-systems step now.

Why this is the best next ticket:

* you already have async cutover
* you already have ordering/idempotency basics
* you already have minimal pipeline metrics
* the next real weakness is **duplicate relay work when more than one instance polls the same outbox rows**

That is exactly the kind of thing that makes the project feel more genuinely distributed.

### 🎯 TICKET-023

#### title[]

Make analytics outbox relay safe for multiple workers

#### technical_detail[]

Strengthen the async analytics backbone so the outbox relay can run safely across multiple application instances without multiple workers repeatedly publishing the same unpublished outbox rows at the same time.

The current relay polls unpublished outbox rows and publishes them directly. That is acceptable for a single worker, and the consumer-side idempotency protects correctness, but it is not a strong multi-instance design because multiple relay workers can still compete over the same backlog and produce unnecessary duplicate Kafka publishes. This ticket should make outbox processing explicitly claim-based and safe for horizontal scaling.

Introduce a small, practical outbox-claim mechanism for analytics relay work. The store should atomically claim a batch of unpublished rows for one relay worker, and only claimed rows should be published by that worker. Claims should expire so stuck work can be recovered if a process dies after claiming but before publishing.

At minimum, this ticket should:

* add small claim/lease fields to `analytics_outbox`
* atomically claim the next batch of eligible unpublished rows
* ensure two relay workers do not claim the same rows concurrently
* preserve ordering of claimed rows by created time/id within a batch
* keep Kafka publish keyed stably per link
* keep consumer-side idempotency unchanged
* allow stale claims to become reclaimable after a lease timeout
* keep implementation small and Postgres/JDBC-shaped

Keep this intentionally focused. Do not add dead-letter queues, generic work schedulers, retries across multiple domains, schema registries, or service extraction.

#### feature_delivered_by_end[]

The analytics outbox relay can run safely with multiple workers because outbox rows are claimed atomically before publish, and stuck claims can be recovered after lease expiry.

#### how_this_unlocks_next_feature[]

This makes the async backbone genuinely safer to scale horizontally. Later work like additional consumers, more outbox-driven event types, or splitting background processing away from the main app can build on a believable relay model instead of a single-worker assumption.

#### acceptance_criteria[]

* relay no longer processes work by simply reading all unpublished rows without claiming
* outbox rows are claimed atomically before publish
* concurrent relay workers do not claim the same rows in the same lease window
* stale claimed rows become eligible again after lease expiry
* successful publish still marks rows published
* failed publish does not mark rows published
* existing Kafka keying by link remains stable
* existing consumer idempotency remains unchanged
* existing analytics reporting behavior remains correct
* focused tests cover:

    * claim batch behavior
    * no duplicate claims across two relay workers
    * stale-claim recovery
    * successful publish marks published
    * failed publish leaves row recoverable
* no README/Postman/ticket-tracking updates
* no unnecessary new infrastructure

#### code_target[]

* `apps/api`
* Flyway migration for `analytics_outbox`
* `application.yml` only for minimal lease settings if needed

#### proof[]

* relay uses claimed work rather than naive unpublished polling
* two workers do not publish the same row concurrently
* stale claims can be recovered
* reporting still works
* automated tests pass

#### delivery_note[]

Deliberately postponed: dead-letter queues, retry backoff frameworks, schema/versioning systems, separate worker services, additional event domains, and broader event-platform abstractions.
