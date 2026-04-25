# Health Metrics Api

Health and metrics APIs expose liveness, readiness, runtime role health, query datasource health, and operational metrics.

## Design questions

- Is this owner/workspace-scoped?
- Is it hot path or control plane?
- Is data source truth, cache, or projection?
- Does it emit/consume events?
- Is it idempotent?
- What happens under degradation?

Back to [README](../../README.md).
