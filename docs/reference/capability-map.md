# Capability Map

## API correctness foundation

Reserved-route protection, self-redirect prevention, RFC 7807 Problem Details, and contract tests.

### Features

- reserved top-level slugs: api, actuator, error
- case-insensitive checks before persistence
- self-redirect prevention
- RFC 7807 Problem Details
- create-link/redirect contract tests

## Operational baseline

Health, readiness, metrics, and structured request logging before complexity grows.

### Features

- Actuator health/liveness/readiness
- metrics baseline
- structured request logging
- system ping
- health and metrics proof surfaces

## Control-plane link lifecycle

Owner-facing reads, lifecycle mutation APIs, expiration, search, filtering, metadata, and discovery.

### Features

- link read/list APIs
- lifecycle mutation APIs
- expiration
- lifecycle-aware reads
- search/filtering
- metadata
- richer discovery

## Analytics evolution

Synchronous analytics baseline evolved into rollups, activity feed, trending links, freshness metadata, and deterministic proof.

### Features

- sync click analytics baseline
- traffic totals
- rollups
- activity feed
- trending links
- advanced analytics query surface
- freshness metadata
- live/deterministic freshness proof

## Async analytics pipeline

Kafka, transactional outbox, ordered processing, dedicated worker runtime, retry, parking, leases, backpressure, and poison-message handling.

### Features

- Kafka analytics events
- transactional outbox
- ordered processing
- worker runtime
- safe relay for multiple workers
- retry state
- dead-letter parking
- leases
- lease recovery
- backpressure
- poison-message parking

## Projection rebuild and replay

Projection jobs, analytics replay, lifecycle event backbone, catalog projection, progress fields, reconciliation, and workspace visibility.

### Features

- projection jobs
- analytics replay
- link lifecycle event backbone
- activity feed via lifecycle events
- link catalog projection
- progress fields
- reconciliation
- workspace visibility protection

## Idempotency and write safety

Idempotent link mutations, optimistic concurrency, duplicate-safe create flows, lifecycle event safety, and exact quota-race hardening.

### Features

- link mutation idempotency store
- duplicate create safety
- optimistic concurrency
- concurrent duplicate create proof
- owner-scoped idempotency
- out-of-order lifecycle event handling
- exact quota-race hardening

## Ownership identity and governance

Owners, workspaces, hashed API keys, scopes, permissions, service accounts, invitations, lifecycle, enterprise policy, and global governance.

### Features

- ownership model
- workspaces
- hashed API keys
- API key scopes
- workspace permissions
- service accounts
- workspace invitations
- member lifecycle
- ownership transfer
- enterprise policy
- global governance rollups
- operator action logs
- security event store

## Plans entitlements quota and usage

Workspace plans, usage ledger, entitlements, subscription lifecycle, over-quota reporting, and race-safe active-link enforcement.

### Features

- workspace plans
- active-link quota
- workspace usage ledger
- usage summary
- entitlement service
- workspace subscription lifecycle
- over-quota reporting
- quota end-to-end tests
- quota concurrency tests

## Owner query acceleration and discovery

Redis-backed owner read acceleration, invalidation, graceful degradation, cache coherence, search/index projection, and frontend-shaped query flows.

### Features

- owner query surface
- Redis read acceleration
- cache invalidation
- graceful degradation
- owner-scoped search/index projection
- discovery read model
- frontend-shaped query flows
- analytics cache coherence

## Read scaling and SRE hardening

Query datasource routing, replica-aware reads, query health, fallback to primary, lag policy, role-aware health, and performance/degradation proof.

### Features

- query datasource routing
- query datasource health indicator
- query routing datasource
- fallback to primary
- query replica runtime state
- fallback records
- query replica lag policy
- role-aware health/metrics

## Recovery backup retention and rollout safety

Backup/restore scripts, retention policies, purge runner, workspace exports/imports, recovery drills, contract compatibility, and rollout posture.

### Features

- PostgreSQL backup script
- PostgreSQL restore script
- workspace retention policies
- retention purge runner
- workspace exports
- workspace imports
- import restore proof
- recovery drills
- contract compatibility tests

## Regional redirect resilience

Multi-region redirect posture, region-specific runtimes, failover base URLs, no-failover fail-closed proof, and redirect runtime health.

### Features

- redirect runtime eu-west-1
- redirect runtime us-east-1
- configured failover region
- failover base URL
- no-failover proof runtime
- redirect runtime state
- redirect runtime health indicator
- global failover proof
- fail-closed no-failover behavior

## Security abuse and enterprise posture

Security/config hygiene, host rules, redirect rate limits, abuse intelligence, global abuse risk, security events, and enterprise policies.

### Features

- security/config hygiene
- redirect rate-limit service
- target policy service
- workspace host rules
- workspace abuse policy
- abuse trends
- abuse review
- global abuse risk
- enterprise identity policy
- security events

## Webhook platform maturity

Webhook subscriptions, delivery records, signing, replay, callback validation, verification, health, testing, global risk, and end-to-end proof.

### Features

- webhook subscriptions
- delivery records
- dispatcher
- signing service
- event publisher
- replay
- callback validation
- verification
- subscription health
- test delivery
- global webhook risk

## Local platform proof and CI

Multi-runtime Docker Compose, proof profiles, platform smoke scripts, standalone verification, Postman assets, and CI proof posture.

### Features

- multi-runtime Docker Compose
- control-plane runtime
- redirect runtimes
- worker runtime
- query fallback proof runtime
- single-region no-failover proof runtime
- platform smoke scripts
- Postman collection/environment
- CI proof baseline

Back to [README](../../README.md).
