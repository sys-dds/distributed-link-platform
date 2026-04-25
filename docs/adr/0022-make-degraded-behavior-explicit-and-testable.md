# ADR-0022: Make degraded behavior explicit and testable

## Status

Accepted

## Context

DLP uses a simple link domain to explore production-shaped backend behavior: hot paths, cache, async analytics, projection rebuilds, governance, webhooks, recovery, degraded behavior, and proof.

## Decision

Make degraded behavior explicit and testable.

## Consequences

### Benefits

- improves explainability
- makes runtime behavior clearer
- supports better tests and proof flows
- strengthens the public architecture story
- avoids hiding important backend trade-offs

### Costs

- adds more documentation
- adds more operational concepts
- makes the system more complex than a CRUD URL shortener
- requires proof flows rather than claims

## Alternatives considered

- one generic runtime for every workload
- direct synchronous analytics in redirect path
- trusting cache/projections as source truth
- hiding degraded behavior
- raw API key storage
- manual recovery without audit
- splitting into microservices too early

Back to [README](../../README.md).
