# Project Narrative

Distributed Link Platform uses a simple link domain to explore real backend architecture.

The product is familiar:

- create a short link
- redirect users
- collect analytics

The engineering is deeper:

- redirects are hot paths
- analytics should not overload redirect requests
- derived views can drift
- caches can fail
- workers can lag
- owners need scoped governance
- quotas can race
- webhooks need delivery truth
- recovery needs proof

Back to [README](../README.md).
