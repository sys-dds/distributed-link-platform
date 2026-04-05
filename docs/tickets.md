
### 🚀 TICKET-031

#### title[]

Secure the owner-facing control plane with owner-scoped reads, projection ownership, API-key hygiene, and plan-aware rate limiting / abuse controls

#### technical_detail[]

TICKET-030 established authenticated ownership for mutations, but the control plane is still inconsistent:

* writes are owner-authenticated
* reads are still mostly global/public
* the catalog projection is not yet clearly owner-shaped for UI use
* API-key hygiene still needs stronger proof and redaction
* rate limiting / abuse controls have not been added yet

This ticket should close that gap in one coherent slice.

Bundle all of this together:

* require authenticated owner context for **control-plane reads and writes**
* keep **public redirect** behavior unchanged
* add `owner_id` to the lifecycle projection path where needed so owner-scoped catalog reads are first-class
* make the main control-plane read endpoints owner-scoped:

    * get one link
    * list links
    * search/filter links
    * suggestions/autocomplete
    * owner-relevant analytics/detail reads where practical
* add a frontend-friendly **`/api/v1/me`** or equivalent owner summary endpoint with:

    * owner key / display info
    * plan
    * active link count
    * active link limit
* redact `X-API-Key` from request logging and add explicit proof that raw keys do not appear in logs or DB persistence
* add pragmatic **plan-aware control-plane rate limiting**

    * separate read and mutation limits
    * scope by authenticated owner
    * optionally also rate-limit repeated invalid API-key attempts by remote source
* add durable **security / abuse event logging** for:

    * invalid API key attempts
    * rate-limit rejections
    * quota rejections
* return clean RFC 7807 responses for auth/rate-limit/abuse outcomes
* keep implementation explicit and JDBC-based; do not introduce Redis yet

This is the right next ticket because it finishes the real ownership boundary and makes the backend much more usable for the future Next.js control-plane UI.

#### feature_delivered_by_end[]

The platform has a **real private owner-facing control plane**:

* owner-authenticated reads and writes
* owner-shaped catalog/read behavior
* frontend-friendly owner summary endpoint
* safe API-key handling with redaction proof
* plan-aware rate limiting and basic abuse/security event capture

#### how_this_unlocks_next_feature[]

This unlocks the next big slices cleanly:

* Redis caching + invalidation
* richer search/index projection
* cleaner frontend integration
* stronger admin/operator controls
* safer runtime separation later without awkward anonymous control-plane behavior

#### acceptance_criteria[]

* `GET /api/v1/links/{slug}` is owner-authenticated and only returns the caller’s link
* `GET /api/v1/links` is owner-authenticated and only lists the caller’s links
* suggestions/search are owner-authenticated and owner-scoped
* public redirect remains anonymous and unchanged
* lifecycle/projection path carries enough ownership data for owner-scoped catalog reads
* projection rebuild still converges correctly with ownership present
* `X-API-Key` is not logged in plaintext
* tests prove raw API keys are not persisted in plaintext
* a frontend-friendly owner summary endpoint returns plan + quota info
* read and mutation rate limits are enforced by owner/plan
* repeated invalid API-key attempts are captured as abuse/security events
* rate-limit failures return RFC 7807 with `429`
* no repo churn in `docs/tickets.md`, README, or Postman

#### code_target[]

* `LinkController`
* control-plane read/query paths in `LinkApplicationService` / `DefaultLinkApplicationService`
* `link_catalog_projection` schema + projection write/rebuild path
* lifecycle event payload/model if owner data is missing
* owner/auth components
* request logging / redaction path
* rate-limit store/service (JDBC-backed)
* abuse/security event store
* integration/controller/projection tests
* **do not touch** repo ticket-tracking/docs files

#### proof[]

* targeted tests proving owner-scoped get/list/search/suggestions
* targeted tests proving cross-owner reads do not leak data
* targeted tests proving redirect stays public
* targeted tests proving rate limits fire correctly for free vs pro plans
* targeted tests proving invalid API-key abuse events are recorded
* targeted tests proving `X-API-Key` is redacted from logs
* targeted tests proving only key hashes are stored in DB
* targeted projection/rebuild tests proving owner-aware projection convergence
* actual test command output with passing results

#### delivery_note[]

This is intentionally a **large protection slice**.

It must finish the missing TICKET-030 hardening **and** add the next coherent capability.
Do not split owner-scoped reads, secret hygiene, and rate limiting into separate mini-tickets.
