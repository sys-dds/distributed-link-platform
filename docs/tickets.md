
## 🚀 TICKET-017

This should be the next bigger ticket:

## TICKET-017 — Add analytics rollups and link traffic reporting endpoints

#### title[]

Add analytics rollups and link traffic reporting endpoints

#### technical_detail[]

Build the first reporting layer on top of the click analytics baseline. This ticket should aggregate raw click events into small reporting views that the control plane can query efficiently, without changing the redirect contract or introducing async/event-driven processing yet.

At minimum, the implementation should:

* add a simple daily rollup for clicks per link
* expose a traffic-summary endpoint for one link
* expose a top-links endpoint based on recent click volume

The link traffic summary should provide a practical control-plane view for a single slug using data already captured by the system. A good first summary includes:

* total clicks
* clicks for the last 24 hours
* clicks for the last 7 days
* daily buckets for a recent window such as the last 7 days

The top-links endpoint should return a deterministic ranked list of the most-clicked links over a recent window such as the last 24 hours or last 7 days. Keep it intentionally small and explicit. A simple `window` query parameter is enough.

Prefer a small rollup/reporting design over repeated heavy scans of raw click events. A good result is some combination of:

* a daily rollup table
* simple aggregation queries
* small reporting DTOs
* a dedicated analytics read controller or a small extension of the current control-plane API

Keep the implementation intentionally practical. Do not add Kafka, async consumers, dashboards, realtime streaming, materialized-view orchestration, anomaly detection, bot filtering, tenant analytics, or advanced ranking logic yet. This is still the synchronous analytics phase, just with a real reporting surface now.

The existing create/update/delete/read/list/search/filter/expiration/redirect behavior should remain unchanged outside the new reporting endpoints and any supporting rollup persistence.

#### feature_delivered_by_end[]

The platform can report basic traffic summaries for a link and show top-clicked links over a recent window, using small analytics rollups instead of only raw click capture.

#### how_this_unlocks_next_feature[]

This creates the first useful analytics read surface that later trending views, feeds, async event pipelines, dashboards, and realtime features can build on.

#### acceptance_criteria[]

* The system maintains a basic daily click rollup per link
* A client can request a traffic summary for one existing link
* A missing slug on the traffic-summary endpoint returns the current RFC 7807 not-found style
* The traffic summary includes total clicks and recent-window information
* A client can request top links by click volume over a supported recent window
* Ranking is deterministic
* Invalid window values return a clear client error
* Existing redirect behavior remains unchanged
* Existing click capture behavior remains unchanged
* Existing create/update/delete/read/list/search/filter/expiration behavior remains unchanged
* Existing Problem Details behavior remains unchanged
* Existing tests still pass or are updated appropriately
* New focused tests cover rollup/reporting behavior
* Only the minimum schema and persistence changes needed for rollups/reporting are introduced

#### code_target[]

* `apps/api`

#### proof[]

* link traffic summary works
* top-links reporting works
* rollup data is maintained correctly for covered scenarios
* invalid reporting inputs return clear client errors
* passing automated tests

#### delivery_note[]

Deliberately postponed: Kafka, async analytics workers, dashboards, realtime updates, tenant analytics, bot filtering, anomaly detection, advanced ranking, and broader reporting infrastructure.