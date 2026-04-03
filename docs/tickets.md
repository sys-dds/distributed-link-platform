TICKET-024
title[]

Introduce dedicated async worker runtime mode

technical_detail[]

Operationalize the async analytics pipeline by allowing the same Spring Boot application to run in distinct runtime roles instead of always running everything everywhere.

The project should remain a single backend application and a single codebase, but it should now support deployment as:

all: current default behavior, runs public HTTP API plus async analytics pipeline
api: runs public HTTP API only, with no outbox relay and no Kafka analytics consumer
worker: runs async analytics pipeline only, with no public API role

This is the right next step after the async cutover and multi-worker-safe outbox relay. It makes the current event-driven backbone operationally believable without forcing premature service extraction. API instances should be able to focus on request latency, while worker instances handle outbox relay and Kafka consumption separately.

At minimum, this ticket should:

introduce a small runtime-role configuration model
keep all as the default mode for local simplicity
disable the scheduled outbox relay in api mode
disable the Kafka analytics consumer in api mode
enable relay and consumer in worker mode
ensure worker mode does not run the public API role
keep redirect, control-plane, and reporting behavior unchanged in all mode
keep Kafka keying, outbox claiming, and consumer idempotency unchanged
add a minimal Docker Compose split showing separate api and analytics-worker services using the same artifact/config style
keep the implementation intentionally small and configuration-driven

Do not broaden this into service extraction, separate repos, Kubernetes, auth, quotas, caching, or additional event domains.

feature_delivered_by_end[]

The same backend application can run as API-only, worker-only, or combined mode, making the async analytics pipeline deployable as a distinct worker role without splitting the codebase into services yet.

how_this_unlocks_next_feature[]

This gives the project its first real distributed deployment topology. Later service extraction, scaling API and worker fleets independently, failure isolation, and deeper async workflows can build on a believable runtime separation instead of one monolithic process doing everything.

acceptance_criteria[]
default all mode preserves current behavior
api mode does not run the outbox relay
api mode does not run the Kafka analytics consumer
worker mode runs the outbox relay and Kafka analytics consumer
worker mode does not serve the public API role
existing redirect/control-plane/reporting behavior remains correct in all mode
existing Kafka keying, outbox claim flow, and consumer idempotency remain unchanged
minimal Docker Compose support exists for separate api and analytics-worker services
focused tests cover:
default combined mode behavior
relay disabled in api mode
consumer disabled in api mode
worker-mode async beans enabled
no unnecessary README/Postman/ticket-tracking changes
code_target[]
apps/api
infra/docker-compose for a minimal split-runtime example only
proof[]
the same artifact can run in combined, API-only, and worker-only modes
API-only instances do not process async analytics work
worker-only instances do process async analytics work
combined mode still works as before
automated tests pass
delivery_note[]

Deliberately postponed: service extraction, separate worker codebases, Kubernetes deployment, autoscaling policies, additional event domains, dead-letter queues, retries beyond current behavior, caching, auth, quotas, and broader platform concerns.