Do not suggest alternative stacks or expand scope; implement the locked decisions exactly.

# Link Platform Tickets

## Ticket status meanings

- Draft
- Ready
- In Progress
- Done
- Dropped

## Current ticket

_None. See completed tickets._

---

## Next likely tickets

### TICKET-002 - Add link domain skeleton
Status: Draft

#### title[]
Add initial link domain skeleton

#### technical_detail[]
Create the first business-area structure for link handling without implementing full short-link behavior yet. Introduce the minimum domain package layout, placeholder service boundaries, and shared naming conventions needed for upcoming link creation and redirect work.

#### feature_delivered_by_end[]
A clean business-area skeleton exists for link-related code so future endpoint and persistence work has a stable place to live.

#### how_this_unlocks_next_feature[]
Creates a safe place to add the create short-link API and redirect flow without mixing business code into bootstrap or system packages.

#### acceptance_criteria[]
- A dedicated link business-area package exists
- Naming is clean and consistent with current project structure
- No fake future complexity is introduced
- Existing app behavior still works

#### code_target[]
- `apps/api`

#### proof[]
- clean file structure
- project still builds
- existing tests still pass

### TICKET-003 - Add create short-link endpoint
Status: Draft

### TICKET-004 - Add redirect endpoint
Status: Draft

---

## Completed tickets

### TICKET-001 - Bootstrap backend foundation
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
