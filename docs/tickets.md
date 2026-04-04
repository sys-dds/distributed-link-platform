
---

### 🎯 TICKET-025

#### title[]

Add retryable outbox delivery, dead-letter parking, and pipeline diagnostics

#### technical_detail[]

Strengthen the async analytics backbone so outbox delivery failures are handled explicitly instead of relying only on lease expiry and repeated blind retries.

The current analytics worker can claim outbox rows safely and publish them to Kafka, but publish failures still just throw, leaving the row to become reclaimable again after lease expiry. That is acceptable as a first cut, but it is not enough for a believable production-shaped distributed pipeline because there is no retry scheduling, no capped failure handling, and no operator-friendly visibility into stuck or permanently failing work. The next step should make the relay resilient and observable without overbuilding a general event platform.

This ticket should extend the existing `analytics_outbox` flow with explicit delivery retry state and a simple parked/dead-letter state for rows that exceed a configured retry budget. It should also expose small control-plane diagnostics so an operator can see backlog, retrying work, and parked failures, and requeue a parked outbox row when needed.

At minimum, this ticket should:

* extend `analytics_outbox` with small delivery-state fields such as:

    * `attempt_count`
    * `next_attempt_at`
    * `last_error`
    * `dead_lettered_at` or equivalent parked-state marker
* make relay claiming consider only rows that are:

    * unpublished
    * not dead-lettered
    * eligible by `next_attempt_at`
    * unclaimed or lease-expired
* on Kafka publish failure:

    * increment attempts
    * store a compact error message
    * clear the claim
    * schedule the next retry using a small capped backoff
* after a configured max-attempt threshold:

    * stop retrying normally
    * park/dead-letter the outbox row
* keep successful publish behavior unchanged:

    * mark published
    * clear claim state
* preserve stable per-link Kafka keying
* preserve consumer-side idempotency and reporting correctness
* add minimal diagnostics APIs for the async pipeline, for example:

    * pipeline status summary
    * list parked outbox rows
    * requeue one parked outbox row
* add focused metrics for:

    * retry attempts
    * parked/dead-letter count
    * eligible backlog count
    * oldest eligible outbox age, or smallest equivalent useful signal

Keep this intentionally focused on the redirect-click analytics outbox only. Do not build a generic workflow engine, generic retry framework, schema registry, or broad event-platform abstraction.

#### feature_delivered_by_end[]

The analytics worker can retry transient outbox publish failures with explicit backoff, permanently failing rows are parked instead of retrying forever, and operators can inspect and requeue failed pipeline work.

#### how_this_unlocks_next_feature[]

This makes the async backbone operationally trustworthy. Later event domains, additional consumers, service extraction, and more advanced platform behavior can build on a failure-handling model that is explicit, explainable, and observable instead of relying on blind retries and hope.

#### acceptance_criteria[]

* successful redirects still behave the same
* redirect path remains outbox-only for analytics
* relay only claims rows eligible for processing
* relay publish failures no longer rely only on lease expiry for retry
* failed publish increments attempt count and stores failure state
* retry scheduling uses a bounded backoff policy
* rows that exceed the configured retry budget are parked/dead-lettered
* parked rows are excluded from normal relay claims
* successful publish still marks rows published
* stable Kafka keying by link remains unchanged
* consumer-side idempotency remains unchanged
* analytics reporting remains correct
* diagnostics APIs exist for:

    * pipeline status summary
    * parked/dead-letter rows
    * requeueing one parked row
* focused tests cover:

    * retry state transition after publish failure
    * next-attempt scheduling/backoff
    * parking after max attempts
    * parked rows excluded from normal claims
    * requeue making a parked row eligible again
    * reporting still correct after successful async processing
* no README, Postman, or ticket-tracking repo updates

#### code_target[]

* `apps/api`
* Flyway migration for `analytics_outbox`
* `application.yml` for small retry/backoff settings only
* `infra/docker-compose` only if a tiny env/config addition is directly needed

#### proof[]

* relay retries transient publish failures with explicit state
* permanently failing rows move to parked/dead-letter state
* parked rows are visible through diagnostics
* a parked row can be requeued
* normal analytics reporting still works
* automated tests pass

#### delivery_note[]

Deliberately postponed: Kafka dead-letter topics for consumer failures, schema/versioning systems, broader replay tooling, service extraction, dashboards, alerts, autoscaling, caching, auth, quotas, and additional event domains.

---