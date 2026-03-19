# Link Platform

Link Platform is a backend-first URL shortener being built incrementally. The current repo foundation includes the first short-link loop: a Spring Boot API, PostgreSQL via Docker Compose for local infrastructure, Flyway migrations, health verification, a system ping API, a `POST /api/v1/links` endpoint backed by in-memory storage, redirect-by-slug via `GET /{slug}`, automated tests, and manual verification assets.

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

The tests use H2 in PostgreSQL compatibility mode so they stay small and reliable without requiring Docker for every test run.

## Manual verification

### Browser or HTTP client

- Health: `http://localhost:8080/actuator/health`
- Ping: `http://localhost:8080/api/v1/system/ping`
- Create link: `http://localhost:8080/api/v1/links`
- Redirect by slug: `http://localhost:8080/launch-page`

### curl examples

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/ping
curl -X POST http://localhost:8080/api/v1/links `
  -H "Content-Type: application/json" `
  -d '{"slug":"launch-page","originalUrl":"https://example.com/launch"}'
curl -i http://localhost:8080/launch-page
```

Expected responses:

- `GET /actuator/health` returns HTTP 200 with a JSON payload containing `"status":"UP"`
- `GET /api/v1/system/ping` returns HTTP 200 with a JSON payload containing `"status":"ok"` and `"service":"link-platform-api"`
- `POST /api/v1/links` returns HTTP 201 with a JSON payload containing the created `slug` and `originalUrl`
- `GET /{slug}` returns HTTP 307 with a `Location` header pointing to the stored original URL

Duplicate or invalid create-link requests return a clear client error:

- duplicate slug: HTTP 409
- invalid slug or invalid URL: HTTP 400

### Postman

Import:

- [`postman/Link-Platform.postman_collection.json`](postman/Link-Platform.postman_collection.json)
- [`postman/Link-Platform.local.postman_environment.json`](postman/Link-Platform.local.postman_environment.json)

The local environment includes:

- `baseUrl`
- `createLinkSlug`
- `createLinkOriginalUrl`

Then select the `Link Platform Local` environment and run the `Health`, `System Ping`, `Create Link`, and `Redirect Link` requests.

After creating a link, run the `Redirect Link` request to verify the temporary redirect response.

## What is intentionally not tested yet

- No PostgreSQL container integration test yet
- No database-backed persistence test yet
- No concurrency stress test for duplicate slug creation yet
- No reserved-route collision test yet

Those are deferred until the project moves beyond the current in-memory create-link implementation.
