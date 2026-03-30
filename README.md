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
- reserved slug (`api`, `actuator`, `error`, case-insensitive): HTTP 400
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

For a quick persistence check against PostgreSQL:

1. Start PostgreSQL with Docker Compose.
2. Start the API.
3. Try creating a valid link with `POST /api/v1/links` using a slug like `launch-page`.
4. Try creating a reserved slug such as `api` or `Actuator` and confirm it is rejected with HTTP 400 before persistence.
5. Call `GET /{slug}` for the valid slug and confirm the redirect works.
6. Stop and start the API again.
7. Call `GET /{slug}` again and confirm the redirect still works from PostgreSQL-backed storage.

## What is intentionally not tested yet

- No concurrency stress test for duplicate slug creation yet
- No broader reserved-route matrix beyond the current top-level conflicts yet
- No end-to-end restart verification is automated yet

Those are deferred until the project moves beyond the current PostgreSQL-backed foundation.
