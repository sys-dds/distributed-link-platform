# Link Platform Master Plan

## 1. Project summary

Link Platform starts as a backend-first URL shortener.

The project is intentionally being built in small, controlled steps so it can later become:
- a strong engineering portfolio project
- interview material with clear architecture stories
- optional educational material if the project grows in that direction

The project will not be fully designed up front.
It will evolve ticket by ticket.

## 2. Why this project exists

### Learning goals
- Build a real system incrementally instead of jumping into fake complexity
- Learn architectural evolution through actual implementation decisions
- Practice making trade-offs deliberately rather than copying trends

### Portfolio goals
- Produce a project that looks credible and explainable
- Preserve decision history and architecture reasoning
- Build something that can later support demos, write-ups, and interview discussion

### Productization goals later
- Keep the codebase and docs clean enough to support future teaching material
- Preserve useful artifacts from day one
- Avoid early decisions that reduce flexibility

## 3. Current scope

The current scope is intentionally small.

The first version is:
- a backend service
- with a real HTTP API
- with a real database
- with clean project structure
- with tests
- with manual API testing support

The first product capability is:
- health verification
- system ping verification
- then short-link creation and redirect in later tickets

## 4. Non-goals for now

These are explicitly out of scope right now:
- frontend or admin UI
- authentication and authorization
- analytics
- rate limiting
- Redis
- Kafka
- RabbitMQ
- Kubernetes
- multi-service decomposition
- multi-region design
- advanced scaling work
- educational packaging work beyond keeping docs clean

These are postponed because they do not help prove the first small version.

## 5. Chosen decisions

The following are currently locked in:

- Project name: Link Platform
- Initial product direction: backend-first URL shortener
- Build style: incremental, ticket-by-ticket
- Language: Java 21
- Framework: Spring Boot
- Build tool: Maven
- Database: PostgreSQL
- Local infrastructure: Docker Compose
- Architecture style for now: single backend application with modular-friendly package structure
- Manual API testing must be supported with:
  - Postman collection
  - Postman environment
  - curl examples
- Every relevant ticket must include automated tests
- Codex will be used for execution only, not project planning

## 6. Open decisions

These are not decided yet:

- Flyway vs Liquibase
- exact package layout beyond the first minimal structure
- exact first short-link API contract
- slug generation strategy
- link expiration rules
- custom alias support
- API error format
- testing depth beyond the first foundation tests
- observability stack beyond basic health checks
- future modular boundaries once business logic grows

## 7. Current architecture

Current architecture target:

- one Spring Boot backend service
- one PostgreSQL database
- local startup through Docker Compose
- HTTP API exposed locally for manual testing
- code organized so business areas can evolve cleanly

Initial architectural principle:
- keep a single deployable unit
- organize code to be modular-friendly without pretending it is already a distributed system

## 8. Current repository shape

The repo should begin simple.

Target early shape:

- `apps/api` for the Spring Boot backend
- `infra/docker-compose` for local infrastructure
- `docs/` for project docs
- `postman/` for manual API testing assets

Core docs for now:
- `docs/master-plan.md`
- `docs/decision-log.md`
- `docs/tickets.md`

This may evolve later, but should not be overdesigned now.

## 9. API conventions

Current API conventions:

- JSON over HTTP
- versioned routes under `/api/v1`
- health endpoint exposed for runtime verification
- one lightweight application ping endpoint for app-level verification
- future business endpoints should stay simple and explicit

Initial endpoints expected:
- `GET /actuator/health`
- `GET /api/v1/system/ping`

Business endpoints for link creation and redirect will come in later tickets.

## 10. Testing strategy

Current testing strategy:

- start with small, high-confidence tests
- prove the application starts
- prove the main verification endpoint returns 200
- keep tests low-maintenance
- add more test depth only when business logic appears

Initial test types:
- context load test
- HTTP integration or slice test for `/api/v1/system/ping`

Not required yet:
- performance testing
- contract testing
- heavy container-based testing unless justified
- resilience/failure testing

## 11. Manual testing assets

Every relevant HTTP ticket should update:

- Postman collection JSON
- Postman environment JSON
- curl examples in README or ticket docs

Goal:
- easy local verification without guessing requests

## 12. Current ticket

Current active ticket is tracked in:

- `docs/tickets.md`

### Active ticket
- TICKET-001 — Bootstrap backend repo and runnable foundation

## 13. Next likely tickets

These are likely next, but not locked until explicitly approved:

1. Implement first link domain skeleton
2. Add create short-link endpoint
3. Add redirect endpoint
4. Add persistence for links
5. Add validation and error handling

This list is guidance, not a strict backlog.

## 14. Update rules

How this file should be maintained:

- Keep stable sections stable:
  - Project summary
  - Why this project exists
  - Non-goals for now
  - Update rules

- Update evolving sections as decisions are made:
  - Chosen decisions
  - Open decisions
  - Current architecture
  - Current ticket
  - Next likely tickets

- Do not rewrite the whole document for small changes
- Move decisions from open to chosen only when explicitly agreed
- Keep the plan concrete and lightweight
- Do not turn this file into a giant backlog

## 15. Change log

### 2026-03-19
- Created initial master plan
- Locked initial stack: Java 21, Spring Boot, Maven, PostgreSQL, Docker Compose
- Locked incremental workflow: plan here, execute in Codex
- Locked Ticket 001 as backend foundation setup