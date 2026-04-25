# Failure Drill: Outbox Lease Lost

## Premise

A worker losing a lease should allow safe lease recovery.

## What to prove

- behavior is explicit
- source truth remains safe
- owner/workspace boundaries hold where relevant
- degradation/failure is visible
- retry/recovery is controlled
- final state is explainable

## Drill steps

1. Create baseline state.
2. Inject the failure or edge case.
3. Observe API behavior.
4. Check runtime health/metrics/logs where available.
5. Check source truth and derived views.
6. Run recovery/rebuild/replay if needed.
7. Confirm expected invariant.

Back to [README](../../../README.md).
