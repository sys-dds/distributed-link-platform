

# Distributed Link Platform

Distributed Link Platform is a backend-first link infrastructure project built to explore how a simple short-link service evolves into a more production-shaped backend system.

At the smallest level, the platform creates a durable short link and serves public redirects.

What makes the project interesting is everything that comes after that baseline:

- separating owner-facing control-plane traffic from public redirect traffic
- treating redirects as a cache-sensitive hot path
- moving analytics through asynchronous worker-driven flow
- rebuilding derived analytics views instead of trusting them forever
- making degraded behavior explicit and inspectable
- proving runtime behavior locally instead of only describing it

This repository started from a deliberately small foundation and has evolved into a stronger backend platform story. It is still one backend codebase, but it runs in different runtime roles to make traffic shape, caching, analytics flow, and degraded behavior easier to reason about.

### What this project is

This is not just a URL shortener demo.

It is a backend engineering project that uses a familiar domain to explore:

- runtime separation
- hot-path optimization
- cache-aware reads
- asynchronous analytics
- rebuildable projections
- degraded dependency behavior
- operational visibility

The domain is intentionally simple so the engineering choices are easier to see.

### What the platform does today

The platform currently supports:

- owner-facing link creation through a control-plane API
- public redirect serving through regional redirect runtimes
- cache-aware redirect and owner-query paths
- worker-driven click analytics
- owner analytics such as:
  - traffic summary
  - top links
  - trending links
  - recent activity
- projection-job rebuilds for derived analytics views
- explicit degraded and failover proof flows

### Why this project exists

Many portfolio backends stop too early.

They prove that a row can be inserted, looked up later, and returned through an endpoint. That shows framework familiarity, but it does not show much backend judgment.

This project exists to explore the questions that start to matter after the first version already works:

- which traffic should be treated as a hot path
- which responsibilities belong on the control plane versus the public redirect path
- when caching is justified and how to prove it is helping
- how analytics should evolve once request-time updates stop being the right shape
- how derived views should be rebuilt when they drift
- how the system should behave when a dependency is degraded
- how operators should understand that behavior without guessing

### Architecture overview

The project keeps a single backend application codebase, but runs it in different runtime roles.

That is a deliberate middle ground:

- simpler than splitting into many deployables too early
- more realistic than forcing every workload into one generic runtime shape

The main runtime roles are:

#### 1. Control-plane API

The control plane handles owner-facing concerns such as:

- link creation
- discovery-style reads
- analytics reads
- projection job control
- health, readiness, and metrics surfaces

#### 2. Regional redirect runtimes

The redirect runtimes serve public traffic.

These runtimes are shaped around:

- redirect correctness
- cache-aware slug resolution
- failover posture
- degraded dependency behavior

The proof flows currently use:

- `eu-west-1`
- `us-east-1`

There is also a no-failover redirect runtime used to prove explicit fail-closed behavior.

#### 3. Worker runtime

The worker runtime relays and consumes click events so analytics can be processed asynchronously instead of forcing all work onto the redirect request path.

That gives the project room to discuss:

- freshness
- eventual convergence
- replay and rebuild thinking
- worker-driven analytics flow

### Architecture sketch

```text
Owner / Operator
    |
    v
Control-Plane API
    |
    +--------------------+
    |                    |
    v                    v
PostgreSQL            Redis
    ^                    ^
    |                    |
    +---------+----------+
              |
              v
        Worker Runtime
              |
              v
            Kafka

Redirect Runtime (eu-west-1)
Redirect Runtime (us-east-1)
Redirect Runtime (no failover proof)
````

### Main engineering themes

#### Redirect hot path

Redirects are treated as a real hot path.

That matters because redirect traffic is:

* public
* repetitive
* latency-sensitive
* a natural place to explore cache effectiveness and degraded lookup behavior

The proof surface does not just say redirects are cached. It proves cache miss and cache hit behavior through repeated requests and metrics.

#### Owner-query hot paths

Owner-facing reads are also treated as cache-sensitive, but differently from public redirects.

This makes it possible to discuss:

* different traffic shapes
* area-specific caching
* query-path fallback behavior
* analytics reads versus public redirect reads

#### Event-driven analytics

Analytics are intentionally pushed through an asynchronous path.

Instead of treating every redirect request as the place where all analytics work must happen, click activity moves through a worker-driven event flow. That creates room to discuss freshness, eventual consistency, worker processing, and operational proof.

#### Projection rebuilds

The system does not rely only on forward-processing.

It also supports projection-job rebuilds for derived analytics views. That is valuable because real systems eventually need recovery paths when derived views drift or logic changes.

#### Explicit degraded behavior

One of the strongest design choices in the project is that degraded behavior is made visible.

The backend proof covers:

* configured regional failover posture
* explicit no-failover posture
* dedicated query-datasource fallback to primary
* cache degradation fallback to primary storage

That is much stronger than just saying the system is resilient.

### What is proven locally

The repo includes a backend proof flow so the most interesting behavior can be demonstrated locally.

#### Redirect hot-path proof

You can:

* create a link through the control plane
* hit the redirect twice
* verify:

    * both responses return `307`
    * the `Location` header is correct
    * the first request records a cache miss
    * the second request records a cache hit

#### Owner-query cache proof

You can warm repeated owner reads and verify cache hits increase for:

* discovery
* traffic summary

#### Live analytics freshness proof

You can drive public redirect traffic and then verify that owner analytics update through the worker pipeline:

* traffic summary increases
* top links reflects the new count
* trending reflects the current window
* recent activity shows click events

#### Rollup rebuild proof

You can create a projection rebuild job, poll it, and confirm that rebuilt rollups remain aligned with owner-facing analytics reads after completion.

#### Failover and degraded-mode proof

You can verify:

* configured failover on the regional redirect runtime
* explicit no-failover behavior on the proof runtime
* dedicated query-path fallback to primary
* cache degradation fallback when Redis is unavailable

### Runtime proof services

The local proof flow uses these services:

* `http://localhost:8080` — control-plane API
* `http://localhost:8081` — redirect runtime `eu-west-1`
* `http://localhost:8082` — redirect runtime `us-east-1`
* `http://localhost:8083` — control-plane API with broken dedicated query datasource to prove primary fallback
* `http://localhost:8084` — redirect runtime without failover to prove explicit degraded behavior

### API keys used in local proof

The proof flows use these default owner keys:

* `free-owner-api-key`
* `pro-owner-api-key`

### Technology choices

The stack is intentionally practical rather than flashy.

#### Backend

* Java 21
* Spring Boot 3.5.11
* Spring Web
* Spring JDBC
* Spring Actuator

#### Data and runtime

* PostgreSQL
* Flyway
* Redis
* Kafka
* Docker Compose

#### Testing

* Spring Boot test support
* H2 for smaller/faster test paths
* Testcontainers for focused PostgreSQL-backed integration tests

### Why these choices

These choices support the goal of exploring backend behavior and trade-offs:

* Java 21 gives a modern JVM baseline
* Spring Boot keeps runtime wiring practical
* JDBC keeps persistence explicit and close to SQL
* Flyway keeps schema evolution simple and durable
* PostgreSQL gives a real relational persistence layer
* Redis supports cache-aware hot paths
* Kafka supports asynchronous analytics flow
* Actuator exposes health, readiness, and metrics truth
* Docker Compose keeps local proof reproducible

### Version choices

The repo intentionally pins key versions:

* Java 21
* Spring Boot 3.5.11
* Maven Wrapper pinned to Apache Maven 3.9.14
* PostgreSQL image tag `postgres:16.8`

### Repository layout

```text
distributed-link-platform/
├─ apps/
│  └─ api/
├─ docs/
│  ├─ backend-proof.md
│  ├─ decision-log.md
│  ├─ master-plan.md
│  └─ tickets.md
├─ infra/
│  ├─ docker-compose/
│  └─ scripts/
├─ postman/
└─ .github/workflows/
```

#### `apps/api`

The main Spring Boot backend application.

#### `docs`

Proof docs, decision history, project planning, and active hardening tickets.

#### `infra/docker-compose`

Local infrastructure and proof stack definitions.

#### `infra/scripts`

Operational and local support scripts.

#### `postman`

Manual verification assets.

#### `.github/workflows`

Targeted backend validation and platform checks.

### Getting started

#### Prerequisites

Required:

* Java 21
* Docker Desktop with Docker Compose support

Optional:

* Postman
* Linux/WSL tooling if you prefer `./mvnw`
* a global Maven install only if you intentionally do not want to use the wrapper

#### Verify prerequisites

From the repo root:

```powershell
java -version
docker --version
docker compose version
```

From `apps/api`:

```powershell
.\mvnw.cmd -version
```

#### Start the local stack

From `infra/docker-compose`:

```powershell
docker compose up -d
```

#### Start the backend

From `apps/api`:

```powershell
.\mvnw.cmd spring-boot:run
```

Optional Linux/WSL path:

```bash
./mvnw spring-boot:run
```

#### Run tests

From `apps/api`:

```powershell
.\mvnw.cmd test
```

### Representative endpoints

#### Runtime and health

* `GET /actuator/health`
* `GET /actuator/health/liveness`
* `GET /actuator/health/readiness`
* `GET /actuator/metrics`
* `GET /api/v1/system/ping`

#### Link management

* `POST /api/v1/links`
* `GET /{slug}`

#### Owner discovery and analytics

* `GET /api/v1/links/discovery`
* `GET /api/v1/links/{slug}/traffic-summary`
* `GET /api/v1/links/traffic/top`
* `GET /api/v1/links/traffic/trending`
* `GET /api/v1/links/activity`

#### Projection jobs

* `POST /api/v1/projection-jobs`
* `GET /api/v1/projection-jobs/{id}`

### Testing strategy

The test strategy is layered rather than pretending every test should be the same.

#### Fast test paths

Smaller tests use H2 in PostgreSQL compatibility mode to keep feedback loops practical.

#### Focused integration paths

Where PostgreSQL behavior matters, the suite includes focused Testcontainers-backed persistence tests.

That balance keeps the project practical to work on while still proving important persistence behavior against a real engine.

### Why this project is interesting

What makes this project interesting is not the domain alone.

A short-link service is easy to understand. That is exactly why it is useful here. The domain is simple enough that a reviewer can understand the product quickly, but still rich enough to expose meaningful backend trade-offs once the system grows beyond the first durable create-and-redirect loop.

The interesting part starts after the obvious version already works.

A basic implementation can create a row, generate a slug, and issue a redirect. That proves framework familiarity. It does not say much about backend judgment. This project becomes more interesting because it moves past that baseline and starts asking better questions:

* what traffic should be treated as a hot path
* which responsibilities belong on the control plane versus the public redirect path
* when caching is justified and how to prove it is actually helping
* how analytics should evolve once request-time updates stop being the right shape
* how derived views should be repaired or rebuilt when they drift
* how runtime behavior should change when dependencies become slow, unavailable, or degraded
* how operators should be able to understand those behaviors without guessing

This repository uses a familiar product surface to explore non-trivial backend concerns in a way that is concrete, testable, and explainable. Instead of depending on product novelty, it depends on engineering choices.

The complexity is also placed deliberately. It is not complexity everywhere. It is complexity where it is justified:

* redirect serving as a cache-sensitive hot path
* owner-facing reads as a different class of traffic from public redirects
* analytics as asynchronous pipeline work rather than only request-path work
* derived analytics views as rebuildable instead of permanently trusted
* degraded behavior as an explicit design concern rather than an afterthought
* proof and observability as first-class parts of the backend story

That is what makes the project feel more like a backend platform and less like a CRUD app.

### What this project is good for in interviews

This project gives strong material for discussing:

* control-plane versus public-path separation
* hot-path optimization and proof
* cache hit/miss behavior
* asynchronous event-driven analytics
* worker-driven convergence of read models
* projection rebuild and replay thinking
* failover posture and degraded-mode behavior
* dependency fallback strategies
* operational visibility and runtime proof
* incremental architecture evolution

The strongest framing is not:

> I built a URL shortener.

The stronger framing is:

> I used a simple link domain to explore hot paths, async analytics, rebuildability, and explicit degraded behavior in a more production-shaped backend.

### What this project is not trying to be

This repository is intentionally not trying to claim all of the following at once:

* a globally deployed commercial SaaS product
* an exactly-once event platform
* a complete frontend product
* an identity platform
* a payments system
* a workflow engine
* a giant microservices estate for the sake of saying “microservices”

Its strength is that it stays grounded in one manageable domain and then goes deeper on runtime behavior, proof, and architecture.

### Current status

The backend is already rich enough to tell a strong senior-backend story.

At the same time, the hardening docs still describe a final backend-finish slice around analytics freshness, redirect failover drills, and final release/security/observability/cost posture before frontend handoff.

So the honest way to describe the repo today is:

* strong
* credible
* already interview-worthy
* still being intentionally hardened

### Suggested reading order

If you are new to the repository, read it in this order:

1. `README.md`
2. `docs/backend-proof.md`
3. `docs/tickets.md`
4. `docs/decision-log.md`
5. `infra/docker-compose/`
6. `apps/api/`

That gives the clearest path from:

* what the platform is
* to what it proves
* to what is still being hardened
* to how it runs
* to how it is implemented

# Important Commands

```powershell
# Start the full local multi-runtime stack
docker compose -f infra/docker-compose/docker-compose.yml up -d --build

# Stop the full local multi-runtime stack and remove volumes
docker compose -f infra/docker-compose/docker-compose.yml down -v

# Start proof-profile services for degraded-runtime scenarios
docker compose -f infra/docker-compose/docker-compose.yml --profile proof up -d --build

# Start the standalone platform smoke stack used by CI compose validation
docker compose -f infra/docker-compose/docker-compose.platform.yml up -d --build

# Stop the standalone platform smoke stack and remove volumes
docker compose -f infra/docker-compose/docker-compose.platform.yml down -v

# Validate the full local compose file
docker compose -f infra/docker-compose/docker-compose.yml config

# Validate the standalone platform compose file
docker compose -f infra/docker-compose/docker-compose.platform.yml config

# Compile backend
cd apps/api
.\mvnw.cmd -DskipTests compile

# Run backend application locally from source
cd apps/api
.\mvnw.cmd spring-boot:run

# Run backend in a specific runtime mode from source
cd apps/api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--link-platform.runtime.mode=control-plane-api"

# Run backend tests
cd apps/api
.\mvnw.cmd test

# Run a targeted backend test class
cd apps/api
.\mvnw.cmd "-Dtest=ProjectionJobsControllerIntegrationTest" test

# Run the GitHub Actions backend hardening test baseline
cd apps/api
.\mvnw.cmd "-Dtest=WebhookCallbackValidationIntegrationTest,WebhookApiPathEndToEndIntegrationTest,WebhooksControllerIntegrationTest,WebhookDeliveryRelayIntegrationTest,WorkspacePlanControllerIntegrationTest,ProjectionJobWorkspaceVisibilityIntegrationTest,ProjectionJobsControllerIntegrationTest,WorkspaceQuotaEndToEndIntegrationTest" test

# Build the backend Docker image
docker build -f apps/api/Dockerfile -t link-platform-api:local .

# Import Postman collection and local environment
postman\Link-Platform.postman_collection.json
postman\Link-Platform.local.postman_environment.json
```