# Testing Strategy

DLP tests should prove behavior and invariants.

## Test groups

- API correctness
- reserved slug/self-redirect
- Problem Details
- redirect hot path
- cache hit/miss
- async analytics
- outbox relay
- lease recovery
- poison-message parking
- projection rebuild
- owner/workspace boundary
- API key lifecycle
- service account lifecycle
- quota race
- webhook validation/replay/health
- export/import/retention
- query fallback
- redirect failover/no-failover
- abuse intelligence
- ops status

Back to [README](../../README.md).
