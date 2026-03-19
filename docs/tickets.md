# Link Platform Tickets

## Ticket status meanings

- Draft
- Ready
- In Progress
- Done
- Dropped

## Current ticket

_None._

---

## Next likely tickets

### TICKET-002 — Introduce link domain model and application boundary
Status: Ready

#### title[]
Introduce link domain model and application boundary

#### technical_detail[]
Create the first real business-area structure for link handling inside the backend application. Add the minimum domain model and application/service boundary needed for upcoming short-link creation work. Do not add HTTP endpoints or persistence yet.

#### feature_delivered_by_end[]
A dedicated link business area exists with clean internal structure and naming, giving future create-link and redirect work a stable home.

#### how_this_unlocks_next_feature[]
Creates the internal business foundation needed before exposing create short-link APIs or wiring persistence.

#### acceptance_criteria[]
- A dedicated `link` package or module exists in `apps/api`
- Core link domain types exist with clean naming
- A minimal application/service boundary exists for future link operations
- No HTTP endpoint is added yet
- No persistence implementation is added yet
- Existing app behavior still works
- Existing tests still pass

#### code_target[]
- `apps/api`

#### proof[]
- clean file structure
- project still builds
- existing tests still pass
- link domain code has a clear home

### TICKET-003 — Add create short-link endpoint
Status: Draft

### TICKET-004 — Add redirect endpoint
Status: Draft

---

## Completed tickets

### TICKET-001 — Bootstrap backend foundation
Status: Done

#### title[]
Bootstrap backend repo and runnable foundation

#### technical_detail[]
Create the initial backend application for Link Platform using Java 21, Spring Boot, Maven, PostgreSQL, and Docker Compose. The application must expose a health endpoint and a lightweight application ping endpoint, include database migration setup, provide minimal automated tests, and include manual API testing artifacts for Postman and curl.

#### feature_delivered_by_end[]
A runnable backend foundation exists locally with:
- Spring Boot app
- PostgreSQL container
- migrations wired in
- health verification
- app-level ping endpoint
- first test foundation
- Postman artifacts
- README usage instructions

#### how_this_unlocks_next_feature[]
This creates the minimal real environment needed before adding link domain logic, create-link endpoints, redirect handling, and persistence rules.

#### acceptance_criteria[]
- Project builds successfully with Maven
- App starts locally
- PostgreSQL runs via Docker Compose
- Migration tooling is configured and runs on startup
- `GET /actuator/health` responds successfully
- `GET /api/v1/system/ping` responds with HTTP 200 and JSON
- At least one automated test proves app startup or endpoint behavior
- Postman collection JSON exists in the repo
- Postman environment JSON exists in the repo
- README explains how to run and test locally

#### code_target[]
- `apps/api`
- `infra/docker-compose`
- `docs`
- `postman`

#### proof[]
- successful local startup
- successful endpoint responses
- passing test output
- importable Postman collection

#### delivery_note[]
- Files changed: `README.md`, `.gitignore`, `docs/tickets.md`, `apps/api/**`, `infra/docker-compose/docker-compose.yml`, `postman/**`
- Endpoints exposed: `GET /actuator/health`, `GET /api/v1/system/ping`
- Tests added: Spring Boot context load test and MockMvc ping endpoint test
- Migration/tooling choice: Flyway with SQL migration scripts, Maven Wrapper pinned to 3.9.14