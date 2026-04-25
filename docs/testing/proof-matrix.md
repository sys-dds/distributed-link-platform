# Proof Matrix

| Capability | Proof target |
|---|---|
| API correctness foundation | Reserved-route protection, self-redirect prevention, RFC 7807 Problem Details, and contract tests. |
| Operational baseline | Health, readiness, metrics, and structured request logging before complexity grows. |
| Control-plane link lifecycle | Owner-facing reads, lifecycle mutation APIs, expiration, search, filtering, metadata, and discovery. |
| Analytics evolution | Synchronous analytics baseline evolved into rollups, activity feed, trending links, freshness metadata, and deterministic proof. |
| Async analytics pipeline | Kafka, transactional outbox, ordered processing, dedicated worker runtime, retry, parking, leases, backpressure, and poison-message handling. |
| Projection rebuild and replay | Projection jobs, analytics replay, lifecycle event backbone, catalog projection, progress fields, reconciliation, and workspace visibility. |
| Idempotency and write safety | Idempotent link mutations, optimistic concurrency, duplicate-safe create flows, lifecycle event safety, and exact quota-race hardening. |
| Ownership identity and governance | Owners, workspaces, hashed API keys, scopes, permissions, service accounts, invitations, lifecycle, enterprise policy, and global governance. |
| Plans entitlements quota and usage | Workspace plans, usage ledger, entitlements, subscription lifecycle, over-quota reporting, and race-safe active-link enforcement. |
| Owner query acceleration and discovery | Redis-backed owner read acceleration, invalidation, graceful degradation, cache coherence, search/index projection, and frontend-shaped query flows. |
| Read scaling and SRE hardening | Query datasource routing, replica-aware reads, query health, fallback to primary, lag policy, role-aware health, and performance/degradation proof. |
| Recovery backup retention and rollout safety | Backup/restore scripts, retention policies, purge runner, workspace exports/imports, recovery drills, contract compatibility, and rollout posture. |
| Regional redirect resilience | Multi-region redirect posture, region-specific runtimes, failover base URLs, no-failover fail-closed proof, and redirect runtime health. |
| Security abuse and enterprise posture | Security/config hygiene, host rules, redirect rate limits, abuse intelligence, global abuse risk, security events, and enterprise policies. |
| Webhook platform maturity | Webhook subscriptions, delivery records, signing, replay, callback validation, verification, health, testing, global risk, and end-to-end proof. |
| Local platform proof and CI | Multi-runtime Docker Compose, proof profiles, platform smoke scripts, standalone verification, Postman assets, and CI proof posture. |

Back to [README](../../README.md).
