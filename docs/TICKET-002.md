Implement TICKET-002 from docs/tickets.md for the Link Platform repo.

Source of truth:
- docs/**

Locked decisions:
- Java version: 21
- Spring Boot version: 3.5.11
- Maven version: 3.9.14
- Build tool: Maven with Maven Wrapper pinned to 3.9.14
- Database: PostgreSQL
- Local infrastructure: Docker Compose via Docker Desktop on Windows
- Architecture: single backend application with modular-friendly package structure
- Manual testing support: Postman collection, Postman environment, and curl examples
- Use pinned versions for core tooling and avoid floating/latest for important runtime/build dependencies

Implement only TICKET-002.

### Objective of TICKET-002

The objective of this ticket is to introduce the first real link business area inside the backend application, with a clean domain model and a minimal application/service boundary that future tickets can build on.

By the end of this ticket, the repo should provide:
- a dedicated `link` package or module area inside `apps/api`
- core link domain types with clean naming
- a minimal application/service boundary for future link operations
- automated tests for the new domain code
- no HTTP endpoint, no persistence implementation, and no database changes yet

This ticket is successful only if:
- the `link` business area has a clear home in the codebase
- the new domain code is small, coherent, and easy to extend
- the project still builds successfully
- existing tests still pass
- new domain-focused tests pass
- no unnecessary placeholders or premature abstractions are introduced

The purpose of this ticket is internal business structure only.
It must not expose new API routes and must not implement persistence yet.

Required scope:
- create a dedicated `link` business area in `apps/api`
- introduce the minimum useful domain model for a short-link concept
- introduce a minimal application/service boundary for future link operations
- add small automated tests for the new domain code
- update docs only where required by this ticket

Domain modeling guidance:
- prefer a small and clean domain model
- introduce only types that are directly useful now
- value objects are allowed if they improve clarity and validation
- keep naming production-friendly and easy to teach later
- avoid speculative complexity

A good result will likely include some combination of:
- `Link`
- `LinkSlug`
- `OriginalUrl`
- a minimal application/service interface such as `LinkApplicationService`
- small request/command objects only if they are directly useful

But do not force all of these if a smaller design is better.

Out of scope:
- no HTTP controller
- no `/api/v1/links` endpoint yet
- no redirect endpoint
- no repository implementation
- no JPA entity
- no database schema change
- no Flyway migration for links yet
- no Redis, Kafka, RabbitMQ, Kubernetes, auth, analytics, or frontend

Testing expectations:
- add focused unit tests for the new domain code
- test domain invariants and validation where relevant
- keep tests fast and low-maintenance
- keep existing tests green
- explain briefly what is intentionally not tested yet

Execution reporting requirements:
- do not expose hidden chain-of-thought
- instead, provide a concise execution report

The execution report must include:
1. Summary of what was implemented
2. Files created
3. Files modified
4. Commands run
5. Tests run
6. Test results
7. Assumptions made
8. Anything intentionally not done
9. Issues, risks, or follow-up suggestions

Also update repo docs to reflect the completed work:
- update `docs/tickets.md`
- if TICKET-002 is completed, move it to Completed tickets and mark it Done
- add a short delivery note under that ticket summarizing:
    - files changed
    - domain types introduced
    - tests added
    - what was deliberately postponed

Rules:
- do not suggest alternative stacks or expand scope
- implement the locked decisions exactly
- keep the package structure modular-friendly but minimal
- do not create placeholder classes, packages, or modules unless they are directly used by this ticket
- do not add infrastructure or persistence code unless required by this ticket
- prefer the simplest clean design that sets up TICKET-003 well

Return in this order:
1. file tree
2. brief design notes
3. execution report
4. full file contents
5. run commands
6. test commands
7. manual verification steps