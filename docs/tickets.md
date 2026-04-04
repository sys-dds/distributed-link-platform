
### 🎯 TICKET-029

#### title[]

Build idempotent link mutations, optimistic concurrency, and version-aware projection safety

#### technical_detail[]

The platform now has real async lifecycle-driven read models, which makes **write safety** the next best move.

This ticket should harden the mutation side of the system so retries, duplicate submissions, concurrent edits, and stale async events cannot corrupt link state or projections.

Bundle these capabilities into one coherent slice:

* add a **monotonic `version`** to the link write model
* include that version in **lifecycle events**
* add a **version** column to `link_catalog_projection`
* make catalog projection application **ignore stale or duplicate lifecycle events**
* add **idempotency key** support for create/update/delete style link mutations
* add **optimistic concurrency** for update/delete style mutations
* expose link version in control-plane responses where it matters

Implementation should stay practical and link-specific. Do **not** build a generic command bus, generic middleware, or abstract framework.

Use a small, explicit design:

* `Idempotency-Key` request header for mutation endpoints
* plain integer `If-Match` header for mutation preconditions on update/delete-style operations
* a small JDBC-backed idempotency store scoped to link mutations
* SQL update patterns that enforce version matching atomically

#### feature_delivered_by_end[]

The platform can safely handle:

* client retries without duplicate mutation effects
* concurrent edits without silent overwrite
* duplicate or out-of-order lifecycle event delivery without stale projection state winning

#### how_this_unlocks_next_feature[]

This unlocks the next commercial/control layer cleanly:

* ownership
* API keys
* plans
* quotas
* abuse controls

It also makes later worker hardening and service-boundary work much safer because mutation correctness is no longer the weak link.

#### acceptance_criteria[]

* creating a link with the same `Idempotency-Key` and same payload does **not** create duplicates
* reusing the same `Idempotency-Key` with a different payload returns a **409 Problem Details** response
* update/delete-style mutations require a valid current version via `If-Match`
* stale `If-Match` values return a **409 Problem Details** response
* link version increments on every successful mutation
* lifecycle events include the new version
* `link_catalog_projection` stores version and only applies newer events
* replay/rebuild still reconstructs correct final projection state
* control-plane reads expose version where needed for future safe clients
* no repo ticket-tracking/doc churn

#### code_target[]

* link API mutation endpoints and request handling
* link application service mutation flows
* `LinkStore` and `PostgresLinkStore`
* lifecycle event model / serialization / outbox write path
* `link_catalog_projection` schema and projection write logic
* new small idempotency persistence component under the existing JDBC style
* API and projection tests
* **do not touch** `docs/tickets.md`, README, or Postman files

#### proof[]

* integration test proving duplicate create retry returns one logical result
* integration test proving same key + different payload returns 409
* integration test proving concurrent/stale update is rejected
* projection test proving stale lifecycle event does not overwrite newer projection state
* rebuild/replay test proving final projection state is still correct after versioned events
* targeted test output showing green results

#### delivery_note[]

Keep this as one coherent correctness slice.
Do not split idempotency, versioning, and projection safety into separate mini-tickets.
Do not add ownership, API keys, quotas, or rate limiting yet.
