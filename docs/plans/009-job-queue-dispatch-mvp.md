# Job Queue Dispatch MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Turn the current upload intake into a reliable queue handoff: every accepted upload creates a durable dispatch event, Docker runtime can publish pending jobs to RabbitMQ, and job reads expose whether the queued job has been dispatched.

**Architecture:** Use a transactional outbox table as the boundary between upload intake and RabbitMQ. Upload stays independent from RabbitMQ availability: the database commit records the video, localization job, and pending dispatch event together. A separate dispatcher publishes ready events to RabbitMQ and marks each event as dispatched or failed with attempt metadata.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring JDBC, Flyway, H2 test profile, MySQL runtime, Spring AMQP, RabbitMQ, Jackson, Maven, MockMvc, AssertJ.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `job-queue-dispatch-mvp`.
- Keep upload request success independent from RabbitMQ availability.
- Do not add worker execution, FFmpeg processing, OpenAI calls, subtitles, TTS, frontend UI, authentication, or Redis usage in this slice.
- Keep persistence in the existing `JdbcClient` repository style.
- Keep test runs external-service-free: no unit or integration test may require a live RabbitMQ server.
- Keep RabbitMQ dispatch disabled by default for `local` and `test`; enable it for Docker runtime through environment-backed config.
- Store dispatch payloads as stable JSON and avoid secrets or raw exception traces in persisted errors.
- Record verification evidence in `docs/progress/execution-log.md`.

## Feature Boundary

This feature produces the following behavior:

- `POST /api/media/uploads`
  - Validates and stores the source video as today.
  - Inserts the `videos` row and `localization_jobs` row as today.
  - Inserts one `job_dispatch_events` row with status `PENDING` in the same transaction.
  - Returns the existing upload response without waiting for RabbitMQ.
- Docker runtime
  - Declares a direct exchange, durable queue, and binding for localization jobs.
  - Enables a scheduled dispatcher that publishes pending outbox events to RabbitMQ.
  - Marks successfully published events as `DISPATCHED`.
  - Marks failed publishes as `FAILED`, increments attempts, and stores a safe error summary.
- `GET /api/jobs/{jobId}`
  - Returns the current job fields.
  - Adds dispatch visibility: `dispatchStatus`, `dispatchAttempts`, and `dispatchedAt`.

## File Structure

- Modify: `LinguaFrame/pom.xml`
  - Add `spring-boot-starter-amqp`.
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
  - Add worker dispatch settings and RabbitMQ job routing settings.
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/resources/db/migration/V2__create_job_dispatch_events.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobDispatchEventStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobDispatchEventType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobDispatchEventRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/message/QueuedLocalizationJobMessage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobDispatchResultVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobDispatchEventRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobDispatchOutboxService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobDispatchService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobQueuePublisher.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobDispatchOutboxServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobDispatchServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/RabbitJobQueuePublisher.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/scheduling/JobDispatchScheduler.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/config/RabbitJobQueueConfiguration.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Add tests under `LinguaFrame/src/test/java/com/linguaframe/job/...`
- Modify existing upload and job controller tests.
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: AMQP Dependency And Dispatch Configuration

**Files:**
- Modify: `LinguaFrame/pom.xml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify runtime YAML files and `docker-compose.yml`
- Add focused configuration tests

**Interfaces:**
- `LinguaFrameProperties.Worker#isDispatchEnabled()`
- `LinguaFrameProperties.Worker#getDispatchBatchSize()`
- `LinguaFrameProperties.Worker#getDispatchIntervalMs()`
- `LinguaFrameProperties.Rabbitmq#getJobExchange()`
- `LinguaFrameProperties.Rabbitmq#getJobQueue()`
- `LinguaFrameProperties.Rabbitmq#getJobRoutingKey()`

- [x] **Step 1: Confirm branch and run baseline**

Run:

```bash
git status --short --branch
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: branch is `job-queue-dispatch-mvp`; baseline tests pass before implementation.

- [x] **Step 2: Add failing property tests**

Extend `LinguaFramePropertiesTests` to assert defaults:

```java
assertThat(properties.getWorker().isDispatchEnabled()).isFalse();
assertThat(properties.getWorker().getDispatchBatchSize()).isEqualTo(10);
assertThat(properties.getWorker().getDispatchIntervalMs()).isEqualTo(5000L);
assertThat(properties.getRabbitmq().getJobExchange()).isEqualTo("linguaframe.jobs");
assertThat(properties.getRabbitmq().getJobQueue()).isEqualTo("linguaframe.localization.jobs");
assertThat(properties.getRabbitmq().getJobRoutingKey()).isEqualTo("localization.queued");
```

Also add invalid binding checks for zero or blank values.

- [x] **Step 3: Add AMQP dependency and config bindings**

Add `spring-boot-starter-amqp`, then add validated properties:

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:linguaframe}
    password: ${RABBITMQ_PASSWORD:linguaframe_dev_password}

linguaframe:
  worker:
    dispatch-enabled: ${LINGUAFRAME_WORKER_DISPATCH_ENABLED:false}
    dispatch-batch-size: ${LINGUAFRAME_WORKER_DISPATCH_BATCH_SIZE:10}
    dispatch-interval-ms: ${LINGUAFRAME_WORKER_DISPATCH_INTERVAL_MS:5000}
  rabbitmq:
    job-exchange: ${RABBITMQ_JOB_EXCHANGE:linguaframe.jobs}
    job-queue: ${RABBITMQ_JOB_QUEUE:linguaframe.localization.jobs}
    job-routing-key: ${RABBITMQ_JOB_ROUTING_KEY:localization.queued}
```

In `application-test.yaml`, explicitly keep `linguaframe.worker.dispatch-enabled: false`.

- [x] **Step 4: Enable Docker dispatch**

Update `application-docker.yaml` and `docker-compose.yml` so Docker sets:

```yaml
LINGUAFRAME_WORKER_DISPATCH_ENABLED: ${LINGUAFRAME_WORKER_DISPATCH_ENABLED:-true}
RABBITMQ_JOB_EXCHANGE: ${RABBITMQ_JOB_EXCHANGE:-linguaframe.jobs}
RABBITMQ_JOB_QUEUE: ${RABBITMQ_JOB_QUEUE:-linguaframe.localization.jobs}
RABBITMQ_JOB_ROUTING_KEY: ${RABBITMQ_JOB_ROUTING_KEY:-localization.queued}
```

- [x] **Step 5: Verify config**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
docker compose --env-file .env.example config
```

Expected: property tests pass and Compose config renders.

## Task 2: Durable Dispatch Outbox

**Files:**
- Create: `V2__create_job_dispatch_events.sql`
- Create dispatch event enums and record
- Create `JobDispatchEventRepository`
- Add repository/schema tests

**Interfaces:**
- `JobDispatchEventRepository#save(JobDispatchEventRecord record)`
- `JobDispatchEventRepository#findLatestByJobId(String jobId)`
- `JobDispatchEventRepository#findReadyToDispatch(Instant now, int limit)`
- `JobDispatchEventRepository#markDispatched(String eventId, Instant dispatchedAt)`
- `JobDispatchEventRepository#markFailed(String eventId, int attempts, Instant nextAttemptAt, String lastError, Instant updatedAt)`

- [x] **Step 1: Write failing schema and repository tests**

Add tests proving:

- Flyway creates `job_dispatch_events`.
- Saving and loading preserves `eventType`, `status`, `attempts`, `payloadJson`, and timestamps.
- `findReadyToDispatch` returns only `PENDING` or `FAILED` events whose `nextAttemptAt <= now`, ordered by `createdAt`, capped by `limit`.
- `markDispatched` sets status `DISPATCHED` and `dispatchedAt`.
- `markFailed` increments attempts safely and truncates long errors.

- [x] **Step 2: Add migration**

Create:

```sql
CREATE TABLE job_dispatch_events (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error VARCHAR(512),
    dispatched_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_dispatch_events_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
);

CREATE INDEX idx_job_dispatch_events_ready
    ON job_dispatch_events(status, next_attempt_at, created_at);

CREATE INDEX idx_job_dispatch_events_job_id
    ON job_dispatch_events(job_id);
```

- [x] **Step 3: Implement repository**

Map nullable `dispatched_at` and `last_error` carefully. Keep enum values stored as `name()`.

- [x] **Step 4: Verify outbox persistence**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests,JobDispatchEventRepositoryTests
```

Expected: schema and repository tests pass against H2 MySQL mode.

## Task 3: Upload Creates Dispatch Event

**Files:**
- Create `QueuedLocalizationJobMessage`
- Create and implement `JobDispatchOutboxService`
- Modify `MediaUploadServiceImpl`
- Modify `MediaUploadServiceTests`

**Interfaces:**
- `JobDispatchOutboxService#enqueueLocalizationJobQueued(VideoRecord video, LocalizationJobRecord job)`

- [x] **Step 1: Write failing upload orchestration test**

Extend `MediaUploadServiceTests#createsDurableVideoAndQueuedJob` to assert that one outbox event exists for the returned `jobId`:

```java
assertThat(dispatchEventRepository.findLatestByJobId(result.jobId()))
        .isPresent()
        .get()
        .satisfies(event -> {
            assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
            assertThat(event.eventType()).isEqualTo(JobDispatchEventType.LOCALIZATION_JOB_QUEUED);
            assertThat(event.payloadJson()).contains(result.jobId(), result.videoId(), result.sourceObjectKey());
        });
```

- [x] **Step 2: Implement outbox service**

Create JSON payload with stable fields:

```json
{
  "jobId": "...",
  "videoId": "...",
  "sourceObjectKey": "...",
  "targetLanguage": "zh-CN",
  "createdAt": "2026-06-26T..."
}
```

Use Jackson `ObjectMapper` and fail upload with a safe `IllegalStateException("Failed to enqueue localization job.")` only if JSON creation or database insert fails.

- [x] **Step 3: Wire upload service transactionally**

Add `JobDispatchOutboxService` to `MediaUploadServiceImpl` and call it after saving `VideoRecord` and `LocalizationJobRecord`, inside the existing `@Transactional` method.

Update existing tests to construct the service with the outbox dependency.

- [x] **Step 4: Verify upload-to-outbox path**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=MediaUploadServiceTests,MediaUploadControllerTests
```

Expected: uploads still return `201`; the durable dispatch event is created without RabbitMQ.

## Task 4: RabbitMQ Publisher And Dispatcher

**Files:**
- Create `JobQueuePublisher`
- Create `RabbitJobQueuePublisher`
- Create `RabbitJobQueueConfiguration`
- Create `JobDispatchService` and implementation
- Create `JobDispatchScheduler`
- Add service/config tests

**Interfaces:**
- `JobQueuePublisher#publish(QueuedLocalizationJobMessage message)`
- `JobDispatchService#dispatchReadyEvents(int limit)`
- `JobDispatchResultVo(int scanned, int dispatched, int failed)`

- [x] **Step 1: Write failing dispatcher service tests**

Use a fake `JobQueuePublisher` to prove:

- A ready pending event is published and marked `DISPATCHED`.
- A publisher exception marks the event `FAILED`, increments attempts, and stores a safe error summary.
- A malformed payload marks the event `FAILED` without publishing.
- The service respects the provided `limit`.

- [x] **Step 2: Implement publisher boundary**

`RabbitJobQueuePublisher` should call:

```java
rabbitTemplate.convertAndSend(
        properties.getRabbitmq().getJobExchange(),
        properties.getRabbitmq().getJobRoutingKey(),
        message
);
```

Do not catch exceptions in the publisher; the dispatch service owns failure accounting.

- [x] **Step 3: Declare Rabbit topology**

Create durable topology beans:

```java
@Bean
DirectExchange localizationJobExchange(LinguaFrameProperties properties)

@Bean
Queue localizationJobQueue(LinguaFrameProperties properties)

@Bean
Binding localizationJobBinding(Queue queue, DirectExchange exchange, LinguaFrameProperties properties)
```

- [x] **Step 4: Implement dispatcher and scheduler**

The dispatcher should:

- Load ready events through `findReadyToDispatch`.
- Deserialize payloads into `QueuedLocalizationJobMessage`.
- Publish each message.
- Mark success as `DISPATCHED`.
- Mark failures as `FAILED`, set `nextAttemptAt` using a short retry delay, and truncate `lastError` to 512 characters.

The scheduler should be conditional:

```java
@ConditionalOnProperty(prefix = "linguaframe.worker", name = "dispatch-enabled", havingValue = "true")
@Scheduled(fixedDelayString = "${linguaframe.worker.dispatch-interval-ms}")
```

- [x] **Step 5: Verify dispatcher**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobDispatchServiceTests,RabbitJobQueueConfigurationTests
```

Expected: dispatch behavior is covered without a live RabbitMQ server.

## Task 5: Job Read Visibility And Documentation

**Files:**
- Modify `LocalizationJobVo`
- Modify `LocalizationJobQueryServiceImpl`
- Modify `LocalizationJobControllerTests`
- Modify `README.md`
- Modify `docs/progress/execution-log.md`

**Interfaces:**
- `GET /api/jobs/{jobId}` adds:

```json
{
  "dispatchStatus": "PENDING",
  "dispatchAttempts": 0,
  "dispatchedAt": null
}
```

- [x] **Step 1: Write failing controller/query tests**

Update the job controller test to create a dispatch event and assert the new JSON fields.

Also test that a legacy or manually inserted job with no event returns `dispatchStatus: null`, `dispatchAttempts: 0`, and `dispatchedAt: null`.

- [x] **Step 2: Implement query enrichment**

Inject `JobDispatchEventRepository` into `LocalizationJobQueryServiceImpl` and enrich the VO with the latest event for that job.

- [x] **Step 3: Document runtime behavior**

Update `README.md` with:

- Queue exchange, queue, and routing key env vars.
- `LINGUAFRAME_WORKER_DISPATCH_ENABLED`.
- The fact that upload success writes outbox state first and Docker dispatch publishes asynchronously.

Update `docs/progress/execution-log.md` with this feature's branch, behavior, and verification commands.

- [x] **Step 4: Run full verification**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
git diff --check
```

Expected:

- All Maven tests pass.
- Compose config renders.
- Backend Docker image builds.
- Whitespace check passes.

## Completion Criteria

- Upload intake creates video, job, and dispatch outbox state in one transaction.
- Docker runtime has RabbitMQ topology config and a scheduled dispatcher.
- Dispatch service success and failure behavior is test-covered without RabbitMQ.
- Job read API exposes dispatch status for operator visibility.
- README and execution log explain the new queue handoff behavior.
- Feature branch can be merged back into `main` after verification.
