# Local Docker Runtime Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a root Docker Compose runtime skeleton for LinguaFrame backend, MySQL, Redis, RabbitMQ, and MinIO.

**Architecture:** Keep the Spring Boot backend as one deployable container and run supporting infrastructure as separate Compose services. Use `.env.example` for local defaults, `application-docker.yaml` for Docker profile placeholders, and a multi-stage backend Dockerfile that builds the Maven module from the repository root.

**Tech Stack:** Docker Compose v5, Java 21, Spring Boot 3.5.15, Maven, MySQL 8.4, Redis 7.4 Alpine, RabbitMQ 4 Management, MinIO `RELEASE.2025-09-07T16-13-09Z`.

## Global Constraints

- Run Maven and Docker Compose commands from the repository root.
- Keep backend packages under `com.linguaframe`.
- Do not add upload, Flyway migrations, database access code, Redis access code, RabbitMQ consumers, MinIO client code, OpenAI, frontend, worker, or FFmpeg behavior in this slice.
- Do not commit real secrets. `.env.example` may contain local development placeholders only; `.env` remains ignored.
- Do not put agent workflow preferences in `AGENTS.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## File Structure

- Create: `.env.example`
  - Responsibility: provide local development defaults for Compose without storing real secrets.
- Create: `.dockerignore`
  - Responsibility: keep Docker build context small and prevent local secrets/build outputs from entering image builds.
- Create: `docker-compose.yml`
  - Responsibility: define local backend, MySQL, Redis, RabbitMQ, and MinIO services.
- Create: `LinguaFrame/Dockerfile`
  - Responsibility: build and run the backend container from the Maven module.
- Create: `LinguaFrame/src/main/resources/application-docker.yaml`
  - Responsibility: define Docker profile placeholders for backend runtime.
- Modify: `README.md`
  - Responsibility: document Docker Compose validation and run commands.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record this feature slice and validation commands.

## Task 1: Verify Docker Runtime Is Missing

**Files:**
- Verify: `docker-compose.yml`

**Interfaces:**
- Consumes: current repository state.
- Produces: a failing Docker Compose command proving the runtime skeleton is absent.

- [ ] **Step 1: Run Docker Compose config before adding files**

Run:

```bash
docker compose config
```

Expected: command fails because no Compose file exists at the repository root. Acceptable error text includes `no configuration file provided` or `no compose file found`.

## Task 2: Add Environment Template And Compose Services

**Files:**
- Create: `.env.example`
- Create: `docker-compose.yml`

**Interfaces:**
- Consumes: locally pulled images `mysql:8.4`, `redis:7.4-alpine`, `rabbitmq:4-management`, and `minio/minio:RELEASE.2025-09-07T16-13-09Z`.
- Produces: Compose services named `mysql`, `redis`, `rabbitmq`, `minio`, and `linguaframe-backend`.

- [ ] **Step 1: Create `.env.example`**

Create `.env.example`:

```dotenv
COMPOSE_PROJECT_NAME=linguaframe

LINGUAFRAME_BACKEND_PORT=8080

MYSQL_DATABASE=linguaframe
MYSQL_USER=linguaframe
MYSQL_PASSWORD=linguaframe_dev_password
MYSQL_ROOT_PASSWORD=linguaframe_root_password
MYSQL_PORT=3306

REDIS_PORT=6379

RABBITMQ_USER=linguaframe
RABBITMQ_PASSWORD=linguaframe_dev_password
RABBITMQ_AMQP_PORT=5672
RABBITMQ_MANAGEMENT_PORT=15672

MINIO_ROOT_USER=linguaframe
MINIO_ROOT_PASSWORD=linguaframe_minio_password
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_BUCKET=linguaframe-artifacts
```

- [ ] **Step 2: Create `docker-compose.yml`**

Create `docker-compose.yml`:

```yaml
name: ${COMPOSE_PROJECT_NAME:-linguaframe}

services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE:-linguaframe}
      MYSQL_USER: ${MYSQL_USER:-linguaframe}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-linguaframe_dev_password}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-linguaframe_root_password}
    ports:
      - "${MYSQL_PORT:-3306}:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u$${MYSQL_USER} -p$${MYSQL_PASSWORD} --silent"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  redis:
    image: redis:7.4-alpine
    ports:
      - "${REDIS_PORT:-6379}:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  rabbitmq:
    image: rabbitmq:4-management
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-linguaframe}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-linguaframe_dev_password}
    ports:
      - "${RABBITMQ_AMQP_PORT:-5672}:5672"
      - "${RABBITMQ_MANAGEMENT_PORT:-15672}:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s

  minio:
    image: minio/minio:RELEASE.2025-09-07T16-13-09Z
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-linguaframe}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-linguaframe_minio_password}
    ports:
      - "${MINIO_API_PORT:-9000}:9000"
      - "${MINIO_CONSOLE_PORT:-9001}:9001"
    volumes:
      - minio-data:/data

  linguaframe-backend:
    build:
      context: .
      dockerfile: LinguaFrame/Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8080
      MYSQL_HOST: mysql
      MYSQL_DATABASE: ${MYSQL_DATABASE:-linguaframe}
      MYSQL_USER: ${MYSQL_USER:-linguaframe}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-linguaframe_dev_password}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USER: ${RABBITMQ_USER:-linguaframe}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-linguaframe_dev_password}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_BUCKET: ${MINIO_BUCKET:-linguaframe-artifacts}
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-linguaframe}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-linguaframe_minio_password}
    ports:
      - "${LINGUAFRAME_BACKEND_PORT:-8080}:8080"
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
      minio:
        condition: service_started

volumes:
  mysql-data:
  redis-data:
  rabbitmq-data:
  minio-data:
```

- [ ] **Step 3: Verify Compose service parsing**

Run:

```bash
docker compose --env-file .env.example config --services
```

Expected:

```text
mysql
redis
rabbitmq
minio
linguaframe-backend
```

## Task 3: Add Backend Container Build Files

**Files:**
- Create: `.dockerignore`
- Create: `LinguaFrame/Dockerfile`
- Create: `LinguaFrame/src/main/resources/application-docker.yaml`

**Interfaces:**
- Consumes: root `pom.xml`, `LinguaFrame/pom.xml`, and backend source tree.
- Produces: a buildable `linguaframe-backend` container image.

- [ ] **Step 1: Create `.dockerignore`**

Create `.dockerignore`:

```dockerignore
.git
.idea
.vscode
.DS_Store
**/target
**/build
**/out
logs
tmp
.env
.env.*
!.env.example
```

- [ ] **Step 2: Create `LinguaFrame/Dockerfile`**

Create `LinguaFrame/Dockerfile`:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml pom.xml
COPY LinguaFrame/pom.xml LinguaFrame/pom.xml

RUN mvn -pl LinguaFrame -am dependency:go-offline -DskipTests

COPY LinguaFrame/src LinguaFrame/src

RUN mvn -pl LinguaFrame -am package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Create Docker Spring profile**

Create `LinguaFrame/src/main/resources/application-docker.yaml`:

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: LinguaFrame

linguaframe:
  storage:
    endpoint: ${MINIO_ENDPOINT:http://minio:9000}
    bucket: ${MINIO_BUCKET:linguaframe-artifacts}
    access-key: ${MINIO_ROOT_USER:linguaframe}
    secret-key: ${MINIO_ROOT_PASSWORD:}
```

- [ ] **Step 4: Verify Compose config after build files exist**

Run:

```bash
docker compose --env-file .env.example config
```

Expected: command exits `0` and renders the Compose model with `linguaframe-backend`, `mysql`, `redis`, `rabbitmq`, `minio`, and four named volumes.

- [ ] **Step 5: Verify backend image build**

Run:

```bash
docker compose --env-file .env.example build linguaframe-backend
```

Expected: command exits `0` and builds the backend image. If Docker reports missing base images, pull `maven:3.9.9-eclipse-temurin-21` and `eclipse-temurin:21-jre`, then rerun the same build command.

## Task 4: Document Docker Runtime Commands

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: root Compose file, `.env.example`, and backend Dockerfile.
- Produces: contributor-facing Docker commands and execution evidence.

- [ ] **Step 1: Update README Docker commands**

Replace the current text under `After the root runtime is added, expected commands should become:` in `README.md` with:

```markdown
Local Docker runtime commands:

```bash
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up
```

The Compose stack includes MySQL, Redis, RabbitMQ, MinIO, and the Spring Boot backend. Use a local `.env` file for machine-specific overrides; `.env` is ignored by git.
```

- [ ] **Step 2: Add execution log entry**

Append this entry to `docs/progress/execution-log.md` after successful verification:

```markdown
## 2026-06-25

Work:

- Added root Docker Compose runtime skeleton for MySQL, Redis, RabbitMQ, MinIO, and the backend.
- Added `.env.example` with local development placeholders.
- Added backend multi-stage Dockerfile using Java 21.
- Added Docker Spring profile placeholders for container runtime.
- Documented local Docker runtime commands in `README.md`.

Validation:

- `docker compose config` failed before implementation because no root Compose file existed.
- `docker compose --env-file .env.example config --services` returned `mysql`, `redis`, `rabbitmq`, `minio`, and `linguaframe-backend`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 6, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add database migrations, storage client code, queue consumers, upload APIs, or worker behavior.
- Real local secrets should live in `.env`, which is ignored by git.
```

## Task 5: Final Verification And Commit

**Files:**
- Verify: all files touched by Tasks 1-4.

**Interfaces:**
- Consumes: completed Docker runtime skeleton.
- Produces: one complete feature commit that will be merged back to `main` after verification.

- [ ] **Step 1: Run full validation**

Run:

```bash
docker compose --env-file .env.example config --services
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: Docker Compose commands exit `0`, backend image build exits `0`, and Maven reports `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 2: Review changed files**

Run:

```bash
git status --short
git diff -- .env.example .dockerignore docker-compose.yml LinguaFrame/Dockerfile LinguaFrame/src/main/resources/application-docker.yaml README.md docs/progress/execution-log.md docs/plans/006-local-docker-runtime-skeleton.md
git diff --check
```

Expected: only this feature slice's files are modified or created, and `git diff --check` reports no whitespace errors.

- [ ] **Step 3: Commit the feature**

Run:

```bash
git add .env.example \
  .dockerignore \
  docker-compose.yml \
  LinguaFrame/Dockerfile \
  LinguaFrame/src/main/resources/application-docker.yaml \
  README.md \
  docs/progress/execution-log.md \
  docs/plans/006-local-docker-runtime-skeleton.md
git commit -m "Add local Docker runtime skeleton"
```

Expected: one commit containing the complete local Docker runtime skeleton feature.

## Self-Review

- Spec coverage: This plan advances the basic version by adding `.env.example`, Docker Compose infrastructure, backend Dockerfile, Docker profile, README commands, and validation steps.
- Placeholder scan: No placeholder tasks or undefined file paths remain.
- Type consistency: Service names, file paths, environment keys, and validation commands match across tasks.
