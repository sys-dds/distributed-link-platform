TICKET-010 - Add dedicated liveness and readiness health probes

Status: Ready

title[]

Add dedicated liveness and readiness health probes

technical_detail[]

Improve service operability by exposing dedicated liveness and readiness health probe endpoints through Spring Boot Actuator health groups. The application already exposes a general health endpoint, but this ticket should make the service more production-usable by separating “process is alive” from “service is ready to handle traffic”.

The implementation should use the smallest clean Spring Boot / Actuator-native approach. Prefer built-in health groups and existing health contributors over custom probe frameworks. The resulting endpoints should be explicit, stable, and suitable for local verification and future container/orchestrator use.

At minimum, the repo should expose distinct liveness and readiness endpoints under the Actuator health surface. The readiness behavior should reflect whether the application is actually ready to serve traffic with its current dependencies. The liveness behavior should represent whether the application process is alive. Keep the implementation minimal and aligned with current architecture.

Do not broaden this ticket into structured logging, tracing, metrics dashboards, Kubernetes manifests, auth, caching, analytics, persistence redesign, or a general observability overhaul. Do not change business behavior for create-link or redirect flows. Keep the scope focused on health probe operability only.

feature_delivered_by_end[]

The service exposes dedicated liveness and readiness health endpoints, making the runtime easier to operate and easier to integrate into future deployment environments.

how_this_unlocks_next_feature[]

This creates a cleaner operational foundation for future observability, deployment, and resilience work without mixing in unrelated infrastructure complexity.

acceptance_criteria[]
Dedicated liveness and readiness health endpoints are exposed through Spring Boot Actuator
The existing general health endpoint continues to work
The implementation uses the framework’s smallest clean health-group approach
The readiness endpoint reflects actual service readiness with current dependencies
The liveness endpoint reflects process liveness
Existing create-link and redirect behavior remain unchanged
Existing tests still pass or are updated appropriately
New focused tests cover the new probe endpoints, if adding them is practical and maintainable
README/manual verification guidance is updated only where needed
No unnecessary infrastructure or business-logic changes are introduced
code_target[]
apps/api
README.md only if required for startup or manual verification clarification
postman only if adding the probe requests clearly improves manual verification
proof[]
/actuator/health still works
dedicated liveness and readiness probe endpoints work
create-link and redirect behavior remain unaffected
passing automated tests
delivery_note[]

Deliberately postponed: structured logs, metrics, tracing, deployment manifests, advanced health contributors, and broader observability/platform work.