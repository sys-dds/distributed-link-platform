TICKET-011 - Add metrics and structured request logging baseline

Status: Ready

title[]

Add metrics and structured request logging baseline

technical_detail[]

Improve service observability by exposing a basic Actuator metrics surface and introducing structured request logging for the main HTTP flows. The application already exposes general health, liveness, and readiness probes. This ticket should extend operability by making runtime measurements and request-level diagnostics easier to inspect during local development and future deployment.

The implementation should use the smallest clean Spring Boot / Actuator-native approach possible. Prefer framework-native metrics exposure and simple structured logging configuration over custom observability frameworks. The metrics work in this ticket should expose the existing baseline metrics surface only; it must not introduce custom business metrics yet.

For logging, add a minimal structured request logging baseline that makes incoming HTTP requests easier to trace in logs without overbuilding. The logging should remain lightweight and practical for the current monolith. Keep the implementation simple and local to the current runtime.

At minimum, the repo should provide:

a basic Actuator metrics endpoint
a lightweight structured request logging baseline for HTTP requests
focused tests only where practical and maintainable
README/manual verification guidance updated only where required

Do not broaden this ticket into tracing, Prometheus integration, dashboards, custom business metrics, distributed correlation systems, auth, caching, analytics, persistence redesign, or a wider observability overhaul. Do not change create-link or redirect business behavior. Keep the scope focused on observability baseline only.

feature_delivered_by_end[]

The service exposes a basic metrics surface and emits more structured HTTP request logs, making local debugging and future operations easier without changing business behavior.

how_this_unlocks_next_feature[]

This strengthens the observability baseline so later performance, resilience, and deployment work can build on real runtime signals instead of ad hoc debugging.

acceptance_criteria[]
A basic Actuator metrics endpoint is exposed
Existing health, liveness, and readiness endpoints continue to work
A lightweight structured request logging baseline is added for HTTP requests
Existing create-link and redirect behavior remain unchanged
Existing tests still pass or are updated appropriately
Focused tests are added where practical and maintainable
README/manual verification guidance is updated only where needed
Postman is updated only if it materially improves manual verification
No unnecessary business-logic, schema, or infrastructure changes are introduced
No custom business metrics, tracing, or dashboard work is introduced
code_target[]
apps/api
README.md only if required for manual verification or logging/metrics clarification
postman only if adding metrics verification requests clearly improves manual testing
proof[]
the Actuator metrics endpoint works
health, liveness, and readiness endpoints still work
request logs are more structured and useful during local verification
create-link and redirect behavior remain unaffected
passing automated tests
delivery_note[]

Deliberately postponed: custom business metrics, tracing, Prometheus integration, dashboards, advanced correlation systems, deployment manifests, and broader observability/platform work.