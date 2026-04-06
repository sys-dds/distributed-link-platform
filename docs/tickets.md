TICKET-037
title[]

Build the global redirect resilience slice: multi-region redirect posture, deterministic analytics freshness, security/config hardening, and rollout-safe evolution

technical_detail[]

PR 31 added useful resilience groundwork, but the project now needs a bigger destination-oriented jump, not another narrow ticket.

This ticket should intentionally combine four adjacent endgame themes into one coherent slice:

Part 1 — finish the still-open 036 hardening

close the analytics freshness gap properly
make click-driven and rollup-driven cache invalidation deterministic
ensure traffic summary, top links, trending links, and recent activity do not serve stale data after new traffic
preserve replay/rebuild convergence after cache invalidation

Part 2 — multi-region redirect posture
Build the first serious multi-region redirect architecture slice inside the current single codebase/runtime model.

That should include:

region-aware redirect runtime configuration
region identity in redirect/runtime configuration
health-aware redirect failover posture
region-aware redirect metrics and diagnostics
clear behavior for slug resolution/read path under region failure or degraded query/cache dependencies
practical local/dev simulation support for at least two redirect runtimes
keep control-plane and worker roles consistent with this evolution

Do not turn this into full distributed infra theater.
This is about creating a believable architecture seam and proving the main failure/consistency behavior.

Part 3 — security and config hardening
Bundle the security/config layer here instead of making it a separate tiny ticket:

tighten secret/config hygiene for runtime roles and datasource/cache settings
add stronger startup validation for unsafe config combinations
harden API-key and auth-related operational behavior where needed
improve audit/security-event usefulness for operator investigation
ensure internal/operational surfaces are appropriately gated by runtime role

Part 4 — rollout-safe evolution
Strengthen safe change posture for a system that now has multiple runtimes and evolving event/API contracts:

compatibility-oriented tests for key serialized contracts
rollout-safe validation for region/runtime/config combinations
safer behavior when one runtime is upgraded before another
keep frontend-facing contract shapes stable for the future Next.js app

This is the right next ticket because it:

finishes the still-open correctness gap from 036
adds the first real global/failover story
hardens runtime safety and security posture
gets you closer to the final showcase version faster than splitting these into three smaller tickets
feature_delivered_by_end[]

The platform has:

deterministic analytics freshness after new click traffic
a real multi-region redirect posture
region-aware redirect/runtime diagnostics
stronger config/secret/runtime safety
safer rollout and compatibility posture for multiple runtimes
a much more believable staff-level architecture story
how_this_unlocks_next_feature[]

This unlocks the final destination slices cleanly:

performance / observability / capacity / cost proof pack
final security/release polish if anything remains
final runbooks / architecture diagrams / interview packaging
stronger frontend/backend showcase story
acceptance_criteria[]
analytics caches invalidate or refresh deterministically after click and rollup changes
owner-facing traffic summary / top links / trending / recent activity do not serve stale results after new traffic
redirect runtime supports region-aware configuration
at least two redirect runtime instances can be simulated locally with distinct region identities
redirect failover/degradation behavior is explicit and tested
runtime/config validation catches unsafe region/query/cache combinations
security/audit signals are improved for relevant auth/runtime failure cases
key API/event contracts remain compatibility-tested
public redirect behavior remains correct and fast
control-plane and worker behavior remain correctly separated
no repo churn in docs/tickets.md, README, or Postman
code_target[]
click analytics / rollup consumer and analytics-cache invalidation paths
redirect runtime configuration and health/metrics wiring
runtime/config validation
query/cache fallback behavior where region/failover posture needs it
security/audit event handling where runtime/config/auth failures matter
compatibility tests for key contracts
local infra/runtime wiring needed to simulate region-aware redirect posture
focused runtime/resilience tests
do not touch repo ticket-tracking/docs files
proof[]
targeted tests proving analytics freshness after click changes
targeted tests proving analytics freshness after rollup updates
targeted tests proving region-aware redirect runtimes start and behave correctly
targeted tests proving failover/degraded redirect behavior is explicit and safe
targeted tests proving unsafe runtime/config combinations fail fast
targeted tests proving key API/event contracts remain compatible
targeted tests proving public redirect behavior remains unchanged
actual compile/test command output with passing results
delivery_note[]

This is intentionally a huge ticket.

It folds together:

the unfinished correctness gap from 036
multi-region redirect posture
security/config hardening
rollout-safe evolution

Do not split those into several smaller tickets unless the repo forces a clearly separate architectural cut.