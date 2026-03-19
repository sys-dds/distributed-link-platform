Implement TICKET-003 from docs/tickets.md for the Link Platform repo.

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

Implement only TICKET-003.

### Objective of TICKET-003

The objective of this ticket is to expose the first real create short-link feature through HTTP, backed by in-memory storage only.

By the end of this ticket, the repo should provide:
- a `POST /api/v1/links` endpoint
- request validation using the existing link domain/application code
- in-memory storage for created links
- duplicate slug rejection
- automated tests for the create-link flow
- updated Postman/manual testing assets

This ticket is successful only if:
- a client can create a link through HTTP
- valid requests return a clean JSON response
- duplicate slugs are rejected
- invalid input is rejected
- created links are stored in memory for the app lifecycle
- the project still builds successfully
- existing tests still pass
- new API tests pass

The purpose of this ticket is to create the first end-to-end feature with temporary in-memory persistence only.
It must not add database persistence yet.

Required scope:
- add `POST /api/v1/links`
- add request/response DTOs only if directly useful
- reuse the existing link business area
- add a simple in-memory storage mechanism for links
- reject duplicate slugs in memory
- add focused automated tests for the endpoint and in-memory flow
- update Postman collection
- update README usage examples
- update docs only where required by this ticket

Suggested API shape:
- request JSON:
    - `slug`
    - `originalUrl`
- response JSON should be simple and useful
- use HTTP 201 for successful creation
- keep the response minimal and production-friendly

A good result will likely include some combination of:
- `LinkController`
- request/response DTOs
- an in-memory link store such as `InMemoryLinkStore` or equivalent
- small application changes to support storing created links
- duplicate slug handling

But do not add unnecessary layers if a smaller design is better.

Out of scope:
- no redirect endpoint yet
- no repository backed by PostgreSQL
- no JPA entity
- no database schema change
- no Flyway migration for links yet
- no Redis, Kafka, RabbitMQ, Kubernetes, auth, analytics, or frontend
- no advanced error framework unless directly needed

Validation and error-handling guidance:
- keep error handling simple and useful
- duplicate slug should produce a clear client error
- invalid slug or URL input should produce a clear client error
- do not over-engineer the error model yet

Testing expectations:
- add API tests for `POST /api/v1/links`
- test successful creation
- test duplicate slug rejection
- test invalid input rejection
- keep tests fast and low-maintenance
- keep existing tests green
- explain briefly what is intentionally not tested yet

Manual testing requirements:
- update Postman collection with the new create-link request
- update any local environment variables only if needed
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
- if TICKET-003 is completed, move it to Completed tickets and mark it Done
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
- prefer the simplest clean design that sets up TICKET-004 well

Return in this order:
1. file tree
2. brief design notes
3. execution report
4. full file contents
5. run commands
6. test commands
7. manual verification steps