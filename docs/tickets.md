
#### title[]

Build ownership, hashed API keys, plans, and create-mutation quota enforcement

#### technical_detail[]

The next best move is to add the first real **commercial/control boundary** to the platform.

Bundle these capabilities into one coherent slice:

* introduce a small internal **owner/account model**
* require **API key authentication** for control-plane mutation endpoints
* store API keys in **hashed form only**
* associate each link with an **owner**
* add a small **plan model** with explicit limits
* enforce **quota checks** for link creation and active-link ownership limits
* surface plan/quota failures as clean RFC 7807 responses
* scope idempotency records to the authenticated owner so future tenant collisions are impossible
* keep redirect public and unauthenticated
* keep read/query endpoints simple unless they need ownership filtering for correctness

Use a practical in-repo model, not a full auth platform:

* local owner records
* seeded dev owner + API key support
* explicit JDBC repositories
* request authentication from `X-API-Key` header for protected control-plane mutations

Do **not** build user signup, OAuth, JWT infrastructure, or a generic IAM framework.

#### feature_delivered_by_end[]

The platform has a real ownership boundary:

* links belong to owners
* control-plane writes are authenticated by API key
* API keys are stored safely
* plans and quotas are enforced on create flows
* idempotency is owner-scoped instead of global

#### how_this_unlocks_next_feature[]

This unlocks the next protection/performance slices cleanly:

* rate limiting and abuse controls
* owner-aware search/reporting
* cached quota reads
* safer operator/admin controls
* future service/runtime separation without anonymous write access

#### acceptance_criteria[]

* mutation endpoints require a valid `X-API-Key`
* invalid or missing API key returns RFC 7807 problem details with `401` or `403` semantics chosen consistently
* create/update/delete flows record and use the authenticated owner
* new links persist `owner_id`
* API keys are never stored in plaintext; only hash + metadata are stored
* at least two plans exist, such as `FREE` and `PRO`, with explicit limits
* create-link quota is enforced from plan rules
* active owned-link limit is enforced from plan rules
* quota violations return clean `409` or `403` style problem details consistently
* idempotency storage is owner-scoped
* existing redirect behavior remains public and unchanged
* existing async lifecycle/analytics behavior still works
* no repo ticket-tracking/doc churn

#### code_target[]

* link mutation controller/auth entry points
* link application service mutation paths
* owner/account repository + model
* API key repository + hashing logic
* plan/quota model + enforcement logic
* `links` schema for `owner_id`
* idempotency schema/store updated to include owner scope
* Flyway migrations
* mutation integration tests
* do **not** touch `docs/tickets.md`, README, or Postman files

#### proof[]

* integration test: create link with valid API key succeeds and stores owner
* integration test: missing API key fails cleanly
* integration test: invalid API key fails cleanly
* integration test: free-plan owner hits active link quota and gets problem-details failure
* integration test: pro-plan owner can exceed free-plan limit
* integration test: idempotency key reuse is isolated per owner
* integration test: redirect endpoint remains public
* test evidence that raw API keys are not persisted

#### delivery_note[]

Keep this as one commercial/control slice.
Do not split ownership, API keys, and quotas into separate mini-tickets.
Do not add rate limiting yet.
Do not build a generic auth framework.
