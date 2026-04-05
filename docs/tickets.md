### 🚀 TICKET-033

#### title[]

Build the owner-scoped search/index projection and discovery read model, while finishing analytics cache-coherence hardening and frontend-shaped query flows

#### technical_detail[]

The current platform has already gained most of the owner-facing control-plane boundary:

* authenticated owner context
* owner-scoped mutations
* owner-scoped main reads
* `/api/v1/me`
* Redis-backed read acceleration on important paths
* lifecycle-driven invalidation
* rate limiting and security events

But there is still unfinished work from the previous slice:

* analytics cache freshness after click/rollup changes is not proven strongly enough
* the discovery/query side still needs a more intentional owner-scoped read model
* the future Next.js control-plane UI needs richer, stable query/filter/sort behavior

This ticket should combine both needs into one coherent slice.

### Part 1 — finish the missing hardening from the previous slice

* make analytics cache invalidation deterministic for click-driven and rollup-driven freshness
* ensure traffic summary, top links, trending links, and recent activity do not serve stale results after click/rollup changes
* verify async consumers, replay, rebuild, and cache invalidation cooperate correctly

### Part 2 — deliver the next major platform capability

Build a dedicated **owner-scoped search/index projection** and richer discovery read model for the control plane.

Bundle these capabilities together:

* add a dedicated search/index projection for owner-scoped discovery reads
* project the fields the UI actually needs, such as:

    * owner_id
    * slug
    * original_url
    * title
    * hostname
    * tags
    * lifecycle state
    * created_at
    * updated_at / deleted_at where useful
    * expiration timestamp / expiration state
    * latest activity marker if cheap and useful
    * click-derived sort helpers if practical
* move owner-facing discovery/search/filter/sort reads onto that projection where clean
* support richer query behavior for UI screens:

    * search text
    * hostname filter
    * tag filter
    * lifecycle filter
    * expiration filter
    * sort options
    * pagination inputs that will work well for a future Next.js UI
* keep responses stable and frontend-friendly
* add rebuild support for the new search/index projection
* add deterministic cache invalidation for the new discovery/query read paths
* keep everything explicit, JDBC-based, and production-shaped
* do not introduce Elasticsearch/OpenSearch yet
* do not split “finish analytics freshness” and “build search projection” into separate tickets

#### feature_delivered_by_end[]

The platform has:

* deterministic analytics cache freshness after click/rollup changes
* a real owner-scoped search/index projection
* richer discovery/filter/sort capabilities for the control plane
* rebuildable discovery read models
* stable frontend-friendly query surfaces for the future Next.js app

#### how_this_unlocks_next_feature[]

This unlocks the next big slices cleanly:

* dedicated redirect/runtime boundary extraction
* worker/projection runtime hardening
* read scaling and reporting/query isolation
* faster and cleaner frontend screens
* stronger performance/SRE packaging because the read side is now intentionally shaped

#### acceptance_criteria[]

* analytics caches are invalidated or refreshed deterministically after click/rollup changes
* owner-scoped traffic summary, top links, trending links, and recent activity do not remain stale after new clicks
* owner-scoped discovery/search/filter/sort reads are served from a dedicated projection where appropriate
* cross-owner discovery/query reads do not leak data
* search supports practical UI-facing filters and sort options
* query responses support stable pagination behavior suitable for a future Next.js app
* projection rebuild reconstructs the new search/index read model correctly
* cache invalidation for discovery reads is deterministic and owner-safe
* public redirect behavior remains anonymous and unchanged
* async lifecycle and analytics pipelines continue to work
* no repo churn in `docs/tickets.md`, README, or Postman

#### code_target[]

* owner-facing discovery/search controller and query endpoints
* analytics/query endpoints where cache freshness must be completed
* `LinkApplicationService`
* `DefaultLinkApplicationService`
* `LinkStore`
* `PostgresLinkStore`
* lifecycle consumer and analytics/click consumer paths where cache invalidation must be finished
* new search/index projection schema + projector
* projection rebuild/job path
* Redis cache adapter / invalidation hooks
* focused controller/service/projection/cache tests
* do **not** touch repo ticket-tracking/docs files

#### proof[]

* targeted tests proving analytics caches refresh or invalidate correctly after click/rollup changes
* targeted tests proving owner-scoped traffic summary / top / trending / recent activity do not serve stale results after new clicks
* targeted tests proving owner-scoped search/filter/sort results are correct
* targeted tests proving cross-owner discovery isolation
* targeted tests proving projection rebuild reconstructs the search/index read model correctly
* targeted tests proving cache keys and invalidation do not collide across owners
* targeted tests proving public redirect behavior remains unchanged
* actual compile/test command output with passing results

#### delivery_note[]

This is intentionally a **huge** ticket.

It must:

* finish the missing cache-freshness hardening left from the prior slice
* and deliver the next major owner-scoped discovery/search projection in the same pass

Do **not** split this into:

* analytics freshness cleanup
* search projection later
* frontend shaping later

Do it as one coherent read-model evolution step.
