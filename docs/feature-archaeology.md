# Feature Archaeology

## API correctness foundation

Reserved-route protection, self-redirect prevention, RFC 7807 Problem Details, and contract tests.

### Tickets / PR themes

- TICKET-006
- TICKET-007
- TICKET-008
- TICKET-009

### Features

- reserved top-level slugs: api, actuator, error
- case-insensitive checks before persistence
- self-redirect prevention
- RFC 7807 Problem Details
- create-link/redirect contract tests

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Operational baseline

Health, readiness, metrics, and structured request logging before complexity grows.

### Tickets / PR themes

- TICKET-010
- TICKET-011

### Features

- Actuator health/liveness/readiness
- metrics baseline
- structured request logging
- system ping
- health and metrics proof surfaces

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Control-plane link lifecycle

Owner-facing reads, lifecycle mutation APIs, expiration, search, filtering, metadata, and discovery.

### Tickets / PR themes

- TICKET-012
- TICKET-013
- TICKET-014
- TICKET-015
- TICKET-018

### Features

- link read/list APIs
- lifecycle mutation APIs
- expiration
- lifecycle-aware reads
- search/filtering
- metadata
- richer discovery

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Analytics evolution

Synchronous analytics baseline evolved into rollups, activity feed, trending links, freshness metadata, and deterministic proof.

### Tickets / PR themes

- TICKET-016
- TICKET-017
- TICKET-019
- TICKET-047

### Features

- sync click analytics baseline
- traffic totals
- rollups
- activity feed
- trending links
- advanced analytics query surface
- freshness metadata
- live/deterministic freshness proof

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Async analytics pipeline

Kafka, transactional outbox, ordered processing, dedicated worker runtime, retry, parking, leases, backpressure, and poison-message handling.

### Tickets / PR themes

- TICKET-020
- TICKET-021
- TICKET-022
- TICKET-023
- TICKET-024
- TICKET-025
- TICKET-065

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Projection rebuild and replay

Projection jobs, analytics replay, lifecycle event backbone, catalog projection, progress fields, reconciliation, and workspace visibility.

### Tickets / PR themes

- TICKET-026
- TICKET-027
- TICKET-028
- TICKET-053
- TICKET-057
- TICKET-065

### Features

- projection jobs
- analytics replay
- link lifecycle event backbone
- activity feed via lifecycle events
- link catalog projection
- progress fields
- reconciliation
- workspace visibility protection

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Idempotency and write safety

Idempotent link mutations, optimistic concurrency, duplicate-safe create flows, lifecycle event safety, and exact quota-race hardening.

### Tickets / PR themes

- TICKET-029
- TICKET-065

### Features

- link mutation idempotency store
- duplicate create safety
- optimistic concurrency
- concurrent duplicate create proof
- owner-scoped idempotency
- out-of-order lifecycle event handling
- exact quota-race hardening

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Ownership identity and governance

Owners, workspaces, hashed API keys, scopes, permissions, service accounts, invitations, lifecycle, enterprise policy, and global governance.

### Tickets / PR themes

- TICKET-030
- TICKET-031
- TICKET-057
- TICKET-063

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Plans entitlements quota and usage

Workspace plans, usage ledger, entitlements, subscription lifecycle, over-quota reporting, and race-safe active-link enforcement.

### Tickets / PR themes

- TICKET-030
- TICKET-054
- TICKET-057
- TICKET-065

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Owner query acceleration and discovery

Redis-backed owner read acceleration, invalidation, graceful degradation, cache coherence, search/index projection, and frontend-shaped query flows.

### Tickets / PR themes

- TICKET-032
- TICKET-033
- TICKET-034

### Features

- owner query surface
- Redis read acceleration
- cache invalidation
- graceful degradation
- owner-scoped search/index projection
- discovery read model
- frontend-shaped query flows
- analytics cache coherence

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Read scaling and SRE hardening

Query datasource routing, replica-aware reads, query health, fallback to primary, lag policy, role-aware health, and performance/degradation proof.

### Tickets / PR themes

- TICKET-035
- TICKET-063

### Features

- query datasource routing
- query datasource health indicator
- query routing datasource
- fallback to primary
- query replica runtime state
- fallback records
- query replica lag policy
- role-aware health/metrics

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Recovery backup retention and rollout safety

Backup/restore scripts, retention policies, purge runner, workspace exports/imports, recovery drills, contract compatibility, and rollout posture.

### Tickets / PR themes

- TICKET-036
- TICKET-054
- TICKET-057
- TICKET-063

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Regional redirect resilience

Multi-region redirect posture, region-specific runtimes, failover base URLs, no-failover fail-closed proof, and redirect runtime health.

### Tickets / PR themes

- TICKET-037
- TICKET-038
- TICKET-039

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Security abuse and enterprise posture

Security/config hygiene, host rules, redirect rate limits, abuse intelligence, global abuse risk, security events, and enterprise policies.

### Tickets / PR themes

- TICKET-038
- TICKET-039
- TICKET-057
- TICKET-063

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Webhook platform maturity

Webhook subscriptions, delivery records, signing, replay, callback validation, verification, health, testing, global risk, and end-to-end proof.

### Tickets / PR themes

- TICKET-054
- TICKET-057
- TICKET-063

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.

## Local platform proof and CI

Multi-runtime Docker Compose, proof profiles, platform smoke scripts, standalone verification, Postman assets, and CI proof posture.

### Tickets / PR themes

- TICKET-039
- TICKET-054
- TICKET-056
- TICKET-059

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

### Why it matters

This capability moves DLP further away from a toy URL shortener and closer to a production-shaped backend platform.


Back to [README](../README.md).
