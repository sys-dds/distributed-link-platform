# Link Platform

Link Platform is a backend-first URL shortener being built incrementally. The current repo foundation includes the first durable short-link loop: a Spring Boot API, PostgreSQL via Docker Compose for local infrastructure, Flyway migrations, health verification, a system ping API, a `POST /api/v1/links` endpoint persisted to PostgreSQL, redirect-by-slug via `GET /{slug}`, automated tests, and manual verification assets.

## Version choices

- Java 21 is the locked runtime version for the project.
- Spring Boot 3.5.11 is pinned in [`apps/api/pom.xml`](apps/api/pom.xml) to keep framework behavior stable.
- Maven Wrapper is pinned to Apache Maven 3.9.14 in [`apps/api/.mvn/wrapper/maven-wrapper.properties`](apps/api/.mvn/wrapper/maven-wrapper.properties).
- PostgreSQL uses the concrete image tag `postgres:16.8` in [`infra/docker-compose/docker-compose.yml`](infra/docker-compose/docker-compose.yml).
- Flyway was chosen as the migration tool because it is lightweight, SQL-first, and enough for the current foundation ticket without adding XML/YAML changelog overhead.

## Local prerequisites

### Required

- Java 21
- Docker Desktop with Docker Compose support

### Optional

- Postman, for importing the provided collection and environment
- A global Maven install, only if you intentionally do not want to use the included Maven Wrapper
- WSL/Linux tooling, if you prefer running `./mvnw` instead of `mvnw.cmd`

## Prerequisite verification commands

Run these from the repo root unless noted otherwise.

### Java

```powershell
java -version
```

Expected outcome: output starts with `openjdk version "21"` or another Java 21 distribution string.

### Docker

```powershell
docker --version
```

Expected outcome: output starts with `Docker version`.

### Docker Compose

```powershell
docker compose version
```

Expected outcome: output starts with `Docker Compose version`.

If your Docker Desktop install exposes the legacy shim instead, this also works:

```powershell
docker-compose --version
```

### Maven Wrapper

Run this from [`apps/api`](apps/api):

```powershell
.\mvnw.cmd -version
```

Expected outcome: output shows `Apache Maven 3.9.14`.

## Runtime configuration

- `LINK_PLATFORM_PUBLIC_BASE_URL`
  Default: `http://localhost:8080`
  Purpose: defines the platform's public base URL used to reject self-targeting `originalUrl` values before persistence.

## Start PostgreSQL locally

From [`infra/docker-compose`](infra/docker-compose):

```powershell
docker compose up -d
```

If your environment only exposes the legacy command:

```powershell
docker-compose up -d
```

The database starts on `localhost:5432` with:

- database: `link_platform`
- username: `link_platform`
- password: `link_platform`

## Start the API locally

From [`apps/api`](apps/api):

```powershell
.\mvnw.cmd spring-boot:run
```

Optional Linux/WSL path:

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

## Run tests

From [`apps/api`](apps/api):

```powershell
.\mvnw.cmd test
```

The regular API and domain tests use H2 in PostgreSQL compatibility mode so they stay small and reliable. The suite also includes a focused PostgreSQL-backed persistence integration test using Testcontainers, so Docker Desktop should be running for the full test suite.

## Manual verification

### Browser or HTTP client

- Health: `http://localhost:8080/actuator/health`
- Liveness probe: `http://localhost:8080/actuator/health/liveness`
- Readiness probe: `http://localhost:8080/actuator/health/readiness`
- Metrics: `http://localhost:8080/actuator/metrics`
- Ping: `http://localhost:8080/api/v1/system/ping`
- Create link: `http://localhost:8080/api/v1/links`
- Redirect by slug: `http://localhost:8080/launch-page`

### curl examples

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/api/v1/system/ping
curl -X POST http://localhost:8080/api/v1/links `
  -H "Content-Type: application/json" `
  -d '{"slug":"launch-page","originalUrl":"https://example.com/launch"}'
curl -i http://localhost:8080/launch-page
```

Expected responses:

- `GET /actuator/health` returns HTTP 200 with a JSON payload containing `"status":"UP"`
- `GET /actuator/health/liveness` returns HTTP 200 with a JSON payload containing `"status":"UP"`
- `GET /actuator/health/readiness` returns HTTP 200 with a JSON payload containing `"status":"UP"`
- `GET /actuator/metrics` returns HTTP 200 with an Actuator metrics payload containing available meter names
- `GET /api/v1/system/ping` returns HTTP 200 with a JSON payload containing `"status":"ok"` and `"service":"link-platform-api"`
- `POST /api/v1/links` returns HTTP 201 with a JSON payload containing the created `slug` and `originalUrl`
- `GET /{slug}` returns HTTP 307 with a `Location` header pointing to the stored original URL

Duplicate or invalid create-link requests return a clear client error:

- duplicate slug: HTTP 409
- reserved slug (`api`, `actuator`, `error`, case-insensitive): HTTP 400
- self-target URL matching `LINK_PLATFORM_PUBLIC_BASE_URL` origin: HTTP 400
- invalid slug or invalid URL: HTTP 400

Current handled API errors use RFC 7807 Problem Details with fields such as:

- `type`
- `title`
- `status`
- `detail`

### Postman

Import:

- [`postman/Link-Platform.postman_collection.json`](postman/Link-Platform.postman_collection.json)
- [`postman/Link-Platform.local.postman_environment.json`](postman/Link-Platform.local.postman_environment.json)

The local environment includes:

- `baseUrl`
- `createLinkSlug`
- `createLinkOriginalUrl`

Then select the `Link Platform Local` environment and run the `Health`, `Liveness`, `Readiness`, `Metrics`, `System Ping`, `Create Link`, and `Redirect Link` requests.

After creating a link, run the `Redirect Link` request to verify the temporary redirect response.

For a quick persistence check against PostgreSQL:

1. Start PostgreSQL with Docker Compose.
2. Start the API.
3. Confirm `LINK_PLATFORM_PUBLIC_BASE_URL` is set correctly for the local app, or rely on the default `http://localhost:8080`.
4. Try creating a valid external link with `POST /api/v1/links` using a slug like `launch-page`.
5. Try creating a self-target URL such as `http://localhost/about` and confirm it is rejected with HTTP 400 before persistence.
6. Try creating a reserved slug such as `api` or `Actuator` and confirm it is rejected with HTTP 400 before persistence.
7. Call `GET /{slug}` for the valid slug and confirm the redirect works.
8. Stop and start the API again.
9. Call `GET /{slug}` again and confirm the redirect still works from PostgreSQL-backed storage.

Representative error checks:

1. `400 Bad Request`
   Example: reserved slug or self-target URL
   Confirm the response body is RFC 7807 Problem Details JSON
2. `409 Conflict`
   Example: create the same valid slug twice
   Confirm the response body is RFC 7807 Problem Details JSON
3. `404 Not Found`
   Example: `GET /missing-link`
   Confirm the response body is RFC 7807 Problem Details JSON

Representative probe checks:

1. `GET /actuator/health`
   Confirm the response is HTTP 200 with `"status":"UP"`
2. `GET /actuator/health/liveness`
   Confirm the response is HTTP 200 with `"status":"UP"`
3. `GET /actuator/health/readiness`
   Confirm the response is HTTP 200 with `"status":"UP"`
4. `GET /actuator/metrics`
   Confirm the response is HTTP 200 and lists available meter names

Representative request-log checks:

1. `POST /api/v1/links`
   Confirm the app logs a structured line like `http_request method=POST path=/api/v1/links status=201 duration_ms=...`
2. `GET /{slug}`
   Confirm the app logs a structured line like `http_request method=GET path=/launch-page status=307 duration_ms=...`

## What is intentionally not tested yet

- No concurrency stress test for duplicate slug creation yet
- No broader reserved-route matrix beyond the current top-level conflicts yet
- No broader self-origin canonicalization policy beyond host casing and default-port equivalence yet
- No end-to-end restart verification is automated yet

Those are deferred until the project moves beyond the current PostgreSQL-backed foundation.
