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

_None yet._

---

## Completed tickets

### TICKET-004 - Add redirect endpoint backed by in-memory lookup
Status: Done

#### title[]
Add redirect endpoint backed by in-memory lookup

#### technical_detail[]
Expose the first redirect flow for short links. Add a `GET /{slug}` endpoint that looks up the slug from the existing in-memory link store and responds with an HTTP redirect to the stored original URL. Return a clear 404 response when the slug does not exist. Do not add database persistence yet.

#### feature_delivered_by_end[]
A client can resolve a previously created short link and be redirected to its original URL using the current in-memory store.

#### how_this_unlocks_next_feature[]
Completes the first end-to-end shortener flow and creates the base behavior that later persistence, analytics, and caching will build on.

#### acceptance_criteria[]
- `GET /{slug}` exists
- Existing in-memory created links can be resolved by slug
- Successful lookup returns an HTTP redirect with a `Location` header
- Missing slugs return HTTP 404
- Existing app behavior still works
- Existing tests still pass
- New redirect tests pass
- Postman collection and README are updated for the new endpoint

#### code_target[]
- `apps/api`
- `postman`
- `README.md`
- `docs/tickets.md`

#### proof[]
- successful redirect request in local testing
- missing slug returns 404
- passing automated tests
- updated Postman collection

#### delivery_note[]
- Files changed: `docs/tickets.md`, `README.md`, `postman/Link-Platform.postman_collection.json`, `apps/api/src/main/java/com/linkplatform/api/link/**`, `apps/api/src/test/java/com/linkplatform/api/link/**`
- Endpoint added: `GET /{slug}`
- Tests added: redirect success and missing-slug API tests, plus application-service resolve tests
- Deliberately postponed: database persistence, analytics, click tracking, reserved-route hardening, and schema changes

### TICKET-003 - Add create short-link endpoint with in-memory storage
Status: Done

#### title[]
Add create short-link endpoint with in-memory storage

#### technical_detail[]
Expose the first real link creation API through HTTP. Add a `POST /api/v1/links` endpoint that accepts a slug and original URL, validates the request through the existing link domain/application code, stores the created link in an in-memory store, and returns a clean JSON response. Reject duplicate slugs. Do not add database persistence yet.

#### feature_delivered_by_end[]
A client can create a short link through a real HTTP API and receive a usable response, with created links stored in memory for the current app lifecycle.

#### how_this_unlocks_next_feature[]
Creates the first real end-to-end business feature and sets up the redirect ticket to resolve links from the in-memory store before database persistence is introduced later.

#### acceptance_criteria[]
- `POST /api/v1/links` exists
- Request accepts `slug` and `originalUrl`
- Valid requests return a successful JSON response
- Duplicate slugs are rejected
- Invalid inputs are rejected
- Created links are stored in memory
- Existing app behavior still works
- Existing tests still pass
- New API tests pass
- Postman collection and README are updated for the new endpoint

#### code_target[]
- `apps/api`
- `postman`
- `README.md`
- `docs/tickets.md`

#### proof[]
- successful create-link request in local testing
- passing automated tests
- updated Postman collection
- clean file structure

#### delivery_note[]
- Files changed: `docs/tickets.md`, `README.md`, `postman/Link-Platform.postman_collection.json`, `apps/api/src/main/java/com/linkplatform/api/link/**`, `apps/api/src/test/java/com/linkplatform/api/link/**`
- Endpoint added: `POST /api/v1/links`
- Tests added: API tests for successful creation, duplicate slug rejection, and invalid input rejection, plus updated application-service duplicate slug test
- Deliberately postponed: redirect endpoint, database persistence, repository implementation, entities, and schema changes

### TICKET-002 - Introduce link domain model and application boundary
Status: Done

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

#### delivery_note[]
- Files changed: `docs/tickets.md`, `apps/api/src/main/java/com/linkplatform/api/link/**`, `apps/api/src/test/java/com/linkplatform/api/link/**`
- Domain types introduced: `Link`, `LinkSlug`, `OriginalUrl`, `CreateLinkCommand`, `LinkApplicationService`
- Tests added: focused unit tests for `LinkSlug`, `OriginalUrl`, and `DefaultLinkApplicationService`
- Deliberately postponed: HTTP endpoints, persistence, repositories, entities, and database schema changes

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
