Implement TICKET-004 from docs/tickets.md for the Link Platform repo.

Source of truth:
- docs/**

If any docs conflict, prefer:
1. docs/tickets.md
2. docs/master-plan.md
3. docs/decision-log.md

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

Implement only TICKET-004.

### Objective of TICKET-004

The objective of this ticket is to complete the first real short-link loop by adding redirect-by-slug behavior backed by the existing in-memory store.

By the end of this ticket, the repo should provide:
- a `GET /{slug}` endpoint
- lookup of existing links from the current in-memory store
- redirect behavior to the stored original URL
- a clear 404 response for missing slugs
- automated tests for redirect success and missing-slug behavior
- updated Postman/manual testing assets

This ticket is successful only if:
- a link created through the current in-memory create flow can be resolved by slug
- successful resolution returns an HTTP redirect with the correct `Location` header
- missing slugs return HTTP 404
- the project still builds successfully
- existing tests still pass
- new redirect tests pass

The purpose of this ticket is redirect behavior only, still backed by temporary in-memory storage.
It must not add database persistence yet.

Required scope:
- add `GET /{slug}`
- reuse the existing link business area and in-memory store
- add the minimum read/lookup support needed for redirect
- return a redirect response for existing slugs
- return a clear 404 response for unknown slugs
- add focused automated tests for the redirect flow
- update Postman collection
- update README usage examples
- update docs only where required by this ticket

Redirect behavior guidance:
- use a temporary redirect status for now
- include the `Location` header with the original URL
- keep the implementation minimal and production-friendly
- avoid over-engineering reserved-route handling unless directly required by current routes

A good result will likely include some combination of:
- a redirect controller or route in the existing link API area
- a lookup method on the in-memory store
- a small application-service read/resolve method
- a not-found exception or equivalent handling

But do not add unnecessary layers if a smaller design is better.

Out of scope:
- no PostgreSQL-backed persistence
- no JPA entity
- no database schema change
- no Flyway migration for links yet
- no analytics
- no click tracking
- no Redis, Kafka, RabbitMQ, Kubernetes, auth, or frontend
- no advanced global error framework unless directly needed

Validation and error-handling guidance:
- keep error handling simple and useful
- missing slug should produce a clear 404 response
- do not over-engineer the error model yet

Testing expectations:
- add API tests for redirect success
- assert redirect status and `Location` header
- test missing slug returns 404
- keep tests fast and low-maintenance
- keep existing tests green
- explain briefly what is intentionally not tested yet

Manual testing requirements:
- update Postman collection with the new redirect request
- add curl example(s) to README

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
7. Manual verification steps performed
8. Assumptions made
9. Anything intentionally not done
10. Issues, risks, or follow-up suggestions

Also update repo docs to reflect the completed work:
- update `docs/tickets.md`
- if TICKET-004 is completed, move it to Completed tickets and mark it Done
- add a short delivery note under that ticket summarizing:
    - files changed
    - endpoint added
    - tests added
    - what was deliberately postponed

Rules:
- do not suggest alternative stacks or expand scope
- implement the locked decisions exactly
- keep the package structure modular-friendly but minimal
- do not create placeholder classes, packages, or modules unless they are directly used by this ticket
- do not add infrastructure or database persistence code unless required by this ticket
- prefer the simplest clean design that sets up persistence later without forcing it now

Return in this order:
1. file tree
2. brief design notes
3. execution report
4. full file contents
5. run commands
6. test commands
7. manual verification steps