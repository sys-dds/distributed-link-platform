Implement TICKET-005 from docs/tickets.md for the Link Platform repo.

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

Implement only TICKET-005.

### Objective of TICKET-005

The objective of this ticket is to replace the temporary in-memory runtime link storage with PostgreSQL-backed persistence while keeping the existing create-link and redirect behavior working.

By the end of this ticket, the repo should provide:
- a first `links` table migration
- PostgreSQL-backed runtime storage for links
- create-link persisting to PostgreSQL
- redirect-by-slug resolving from PostgreSQL
- duplicate slug rejection still working
- focused persistence tests
- updated README/manual testing guidance where needed

This ticket is successful only if:
- a link created through the current API is persisted to PostgreSQL
- a redirect lookup resolves from PostgreSQL rather than in-memory storage
- duplicate slug rejection still works
- the project still builds successfully
- existing tests still pass or are updated appropriately
- new persistence-focused tests pass

The purpose of this ticket is durable storage only.
It must not add analytics, caching, or broader schema complexity yet.

Required scope:
- add the first Flyway migration for a `links` table
- replace the current runtime link storage implementation with a PostgreSQL-backed implementation
- keep the existing create and redirect API behavior working
- preserve duplicate slug handling
- add focused persistence tests
- update README usage notes only where required
- update docs only where required by this ticket

Schema guidance:
- keep the first schema minimal
- a good first table will likely include:
    - `slug`
    - `original_url`
    - `created_at`
- prefer `slug` as the primary key or unique identifier for now if that keeps the design simpler
- do not add extra columns unless directly useful for current behavior

Persistence design guidance:
- prefer the simplest clear Postgres-backed implementation
- keep the existing `LinkStore` abstraction if it still helps
- a Spring JDBC / JdbcTemplate-style adapter is preferred if it keeps the persistence explicit and small
- do not add unnecessary repository/entity layers if a smaller design is better
- runtime should have one clear storage implementation
- if the in-memory store is kept, it must not remain the default runtime store

A good result will likely include some combination of:
- `PostgresLinkStore` or similar
- SQL migration for `links`
- small row-mapping logic
- duplicate-slug handling translated into the existing application exception

But do not add unnecessary layers if a smaller design is better.

Out of scope:
- no analytics
- no click tracking
- no caching
- no Redis, Kafka, RabbitMQ, Kubernetes, auth, or frontend
- no expiration/TTL yet
- no advanced error framework unless directly needed
- no reserved-route hardening work in this ticket

Testing expectations:
- add at least one real persistence-focused integration test
- verify create/save and resolve/read behavior against PostgreSQL
- verify duplicate slug handling against PostgreSQL
- prefer Testcontainers for Postgres-backed persistence tests if that keeps the tests honest
- keep tests as small and maintainable as possible
- explain briefly what is intentionally not tested yet

Manual testing requirements:
- update README if runtime behavior or startup steps changed
- update Postman collection only if request flow changed
- include create + redirect verification guidance for the PostgreSQL-backed runtime

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
- if TICKET-005 is completed, move it to Completed tickets and mark it Done
- add a short delivery note under that ticket summarizing:
    - files changed
    - storage implementation used
    - migration added
    - tests added
    - what was deliberately postponed

Rules:
- do not suggest alternative stacks or expand scope
- implement the locked decisions exactly
- keep the package structure modular-friendly but minimal
- do not create placeholder classes, packages, or modules unless they are directly used by this ticket
- do not add extra infrastructure beyond what is required for PostgreSQL-backed persistence
- prefer the simplest clean design that sets up future persistence evolution without overbuilding it

Return in this order:
1. file tree
2. brief design notes
3. execution report
4. full file contents
5. run commands
6. test commands
7. manual verification steps