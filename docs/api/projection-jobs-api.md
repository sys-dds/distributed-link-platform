# Projection Jobs Api

Projection job APIs create and inspect rebuild/replay jobs for derived analytics and catalog views.

## Design questions

- Is this owner/workspace-scoped?
- Is it hot path or control plane?
- Is data source truth, cache, or projection?
- Does it emit/consume events?
- Is it idempotent?
- What happens under degradation?

Back to [README](../../README.md).
