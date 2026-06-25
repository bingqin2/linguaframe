# ExecPlan: Project Foundation

## Purpose

Create the first runnable LinguaFrame foundation: a documented repository, a Spring Boot backend baseline, local configuration, and a Docker Compose runtime skeleton for the services required by the video localization pipeline.

This plan does not build the full upload or OpenAI workflow. It prepares the project so feature work can proceed in small, verifiable steps.

## Context

Current state:

- The Spring Boot backend exists under `LinguaFrame/`.
- Package name is `com.linguaframe`.
- Java version is 21.
- The current `pom.xml` contains only the Spring Boot starter and test starter.
- Product documentation has been created under `docs/`.

Target foundation:

- Backend dependencies match the first implementation phases.
- Configuration uses `application.yaml`.
- Local and Docker profiles exist.
- Health endpoint works through Actuator or a small custom endpoint.
- Docker Compose validates service wiring for MySQL, Redis, RabbitMQ, MinIO, and backend.

## Scope

Included:

- Backend dependency baseline.
- Basic configuration properties.
- Health endpoint or Actuator health.
- `.env.example`.
- Backend Dockerfile.
- Docker Compose skeleton.
- Root README alignment with runnable commands.
- Basic test proving application context or health behavior.

Not included:

- React frontend implementation.
- Video upload.
- FFmpeg execution.
- OpenAI API calls.
- MySQL schema for video jobs.
- RabbitMQ worker logic.
- MinIO object storage implementation.

## Implementation Steps

1. Normalize Maven metadata.
   - Ensure artifact id is lowercase if practical.
   - Keep group and package as `com.linguaframe`.
   - Keep Java 21.

2. Add backend dependencies.
   - Spring Web.
   - Spring Validation.
   - Spring Boot Actuator.
   - MySQL driver.
   - Flyway.
   - Redis.
   - RabbitMQ.
   - OpenAPI.
   - Lombok only if the project accepts annotation-based boilerplate reduction.

3. Add configuration.
   - `application.yaml` with `linguaframe` prefix.
   - `application-local.yaml` for local development.
   - `application-docker.yaml` for Docker Compose.
   - Environment variable placeholders for OpenAI, MySQL, Redis, RabbitMQ, and MinIO.

4. Add a health check.
   - Prefer Actuator `/actuator/health`.
   - Add a custom `/health` endpoint only if useful for simpler demos.

5. Add Docker runtime skeleton.
   - Backend Dockerfile.
   - Root `docker-compose.yml`.
   - MySQL service.
   - Redis service.
   - RabbitMQ service with management UI.
   - MinIO service with console.
   - Backend service configured through environment variables.

6. Add `.env.example`.
   - Document all required local variables.
   - Use placeholder values only.
   - Do not commit real API keys.

7. Update README commands.
   - Backend test command.
   - Backend run command.
   - Docker Compose validation command.
   - Expected local URLs.

## Validation

Run from backend directory:

```bash
cd LinguaFrame
./mvnw test
```

Run from repository root after Docker Compose is added:

```bash
docker compose config
```

Optional after Docker runtime is wired:

```bash
docker compose up --build
curl -i http://localhost:8080/actuator/health
```

## Recovery

If dependency downloads fail, check network access before changing versions.

If Docker Compose fails because images cannot be pulled, inspect Docker Desktop networking or registry mirror configuration before rewriting Dockerfiles.

If the backend fails to start because external services are unavailable, verify the active Spring profile and avoid making local development require all services before the feature phase needs them.

## Artifacts

Expected files after completion:

- `LinguaFrame/pom.xml`
- `LinguaFrame/src/main/resources/application.yaml`
- `LinguaFrame/src/main/resources/application-local.yaml`
- `LinguaFrame/src/main/resources/application-docker.yaml`
- `LinguaFrame/Dockerfile`
- `docker-compose.yml`
- `.env.example`
- `README.md`
- `docs/progress/execution-log.md`
