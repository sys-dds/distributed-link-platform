# Link Api

Link APIs handle creation, read/list, lifecycle mutations, expiration, metadata, discovery, and search.

## Design questions

- Is this owner/workspace-scoped?
- Is it hot path or control plane?
- Is data source truth, cache, or projection?
- Does it emit/consume events?
- Is it idempotent?
- What happens under degradation?

Back to [README](../../README.md).
