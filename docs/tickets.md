🎯 TICKET-022
title[]

Finish async analytics cutover and add async pipeline operability

technical_detail[]

Complete the redirect-click analytics migration so the redirect hot path no longer performs any synchronous analytics persistence, and add the minimum observability needed for this async pipeline to be trustworthy in production.

The previous two tickets introduced Kafka, the analytics outbox, the relay, consumer-side idempotency, and producer-ordering hardening, but the redirect application service still persists clicks directly on the request path before also writing the outbox event. This ticket must finish the cutover so successful active-link redirects write only a durable analytics outbox record on the request path. Raw click persistence and daily rollup maintenance must happen only in the asynchronous consumer path.

At the same time, now that redirect analytics depends on an async pipeline, add a small operability baseline so the system can expose whether the pipeline is healthy and keeping up. This should stay intentionally small and use the existing Spring Boot / Actuator / Micrometer style already present in the project.

At minimum, this ticket should:

remove direct click persistence from the redirect hot path
keep redirect HTTP behavior unchanged
keep durable outbox event creation for successful active-link redirects
ensure missing-link and expired-link redirects do not create analytics events
keep Kafka publish keying stable per link
keep consumer-side raw click persistence asynchronous
keep consumer-side daily rollup maintenance asynchronous
preserve existing analytics reporting behavior from the client point of view
add minimal async-pipeline metrics such as:
unpublished outbox count gauge
relay publish success/failure counters
consumer processed/duplicate counters, or equivalent small metrics
make async-pipeline expectations explicit in code/tests

Keep the implementation intentionally small and practical. Do not broaden this ticket into dead-letter queues, schema registries, retries across many domains, dashboards, or generic event-platform abstractions.

feature_delivered_by_end[]

Redirect requests become truly outbox-only for analytics, and the analytics event pipeline has a small but real operability baseline so async failures or lag are visible.

how_this_unlocks_next_feature[]

This gives the project its first believable event-driven backbone: the hot path is actually decoupled, ordering/idempotency remain explicit, and the pipeline has enough visibility to support later consumers, notifications, feeds, or further service decomposition without hand-wavy observability gaps.

acceptance_criteria[]
Successful redirects still return the same redirect behavior as before
Direct click persistence is removed from the synchronous redirect hot path
Successful active-link redirects write a durable analytics outbox record
Successful active-link redirects do not write raw click analytics synchronously
Missing-link redirects do not create analytics events
Expired-link redirects do not create analytics events
Kafka publish continues to use a stable per-link key
Consumer-side click persistence remains idempotent
Consumer-side daily rollup maintenance remains correct
Existing traffic-summary, top-links, and trending endpoints still work correctly from asynchronously maintained data
Actuator/Micrometer exposes small async-pipeline metrics for backlog and processing outcome
Existing useful TICKET-021 tests remain or are improved
New focused tests cover:
redirect path no longer writing clicks synchronously
successful redirect writes outbox only
missing-link redirect writes no outbox record
expired-link redirect writes no outbox record
reporting correctness after async processing
async-pipeline metrics behavior at a focused level
No ticket-tracking docs are modified
No unnecessary new infrastructure or broad refactors are introduced
code_target[]
apps/api
infra/docker-compose only if a very small Kafka adjustment is directly required
proof[]
successful redirects write only outbox analytics records on the request path
raw clicks and rollups are persisted only from the async consumer path
reporting still works correctly
missing and expired redirects generate no analytics events
per-link Kafka keying remains stable
async-pipeline metrics are exposed
passing automated tests
delivery_note[]

Deliberately postponed: dead-letter queues, schema/versioning systems, retry backoff frameworks, dashboards, alerts, more event types, notifications, realtime streaming, service extraction, and broader event-platform work.