# Link Platform

Link Platform is a backend-first URL shortener being built incrementally. `TICKET-001` delivers the smallest production-shaped foundation: a Spring Boot API, PostgreSQL via Docker Compose, Flyway migrations, health verification, a system ping API, automated tests, and manual verification assets.

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

### curl examples

```powershell
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/ping
```

Expected responses:

- `GET /actuator/health` returns HTTP 200 with a JSON payload containing `"status":"UP"`
- `GET /api/v1/system/ping` returns HTTP 200 with a JSON payload containing `"status":"ok"` and `"service":"link-platform-api"`

### Postman

Import:

- [`postman/Link-Platform.postman_collection.json`](postman/Link-Platform.postman_collection.json)
- [`postman/Link-Platform.local.postman_environment.json`](postman/Link-Platform.local.postman_environment.json)

Then select the `Link Platform Local` environment and run the `Health` and `System Ping` requests.

## What is intentionally not tested yet

- No PostgreSQL container integration test yet
- No persistence behavior test yet
- No business-domain endpoint tests yet

Those are deferred until the project has real link behavior to validate.
