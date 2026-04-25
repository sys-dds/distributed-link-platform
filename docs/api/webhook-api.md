# Webhook Api

Webhook APIs cover subscriptions, verification, health, delivery history, replay, signing, and callback validation.

## Design questions

- Is this owner/workspace-scoped?
- Is it hot path or control plane?
- Is data source truth, cache, or projection?
- Does it emit/consume events?
- Is it idempotent?
- What happens under degradation?

Back to [README](../../README.md).
