# Worker Execution MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn queued localization jobs into an observable worker execution lifecycle with durable status transitions, timeline events, RabbitMQ consumption, and retry back into the outbox.

**Architecture:** Keep the backend as a modular monolith. A RabbitMQ listener receives `QueuedLocalizationJobMessage` and delegates to a transactional execution service. The service claims a job, records timeline events, runs a deterministic smoke pipeline stage, marks terminal state, and supports retry by creating a new dispatch event. This slice intentionally builds the worker lifecycle boundary before real FFmpeg/OpenAI stages.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AMQP, Spring JDBC `JdbcClient`, Flyway, H2 test profile, MySQL runtime, RabbitMQ, Maven, JUnit 5, MockMvc, AssertJ.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `worker-execution-mvp`.
- Keep tests external-service-free; no test may require a live RabbitMQ, MySQL, Redis, MinIO, FFmpeg, or OpenAI service.
- Do not add real FFmpeg execution, OpenAI calls, subtitle generation, TTS, frontend UI, authentication, or Redis usage in this slice.
- Keep upload success independent from RabbitMQ availability.
- Keep worker execution disabled by default for `local` and `test`; enable it for Docker runtime through environment-backed config.
- Persist only safe error summaries; do not store raw stack traces, secrets, provider responses, or raw media paths from users.
- Record verification evidence in `docs/progress/execution-log.md`.

## Feature Boundary

This feature produces the following behavior:

- RabbitMQ worker runtime
  - Can consume `QueuedLocalizationJobMessage` from the configured job queue.
  - Delegates message handling to a testable execution service.
  - Is disabled in local/test profiles unless explicitly enabled.
- Job execution lifecycle
  - Claims only `QUEUED` or `RETRYING` jobs for execution.
  - Skips duplicate or stale messages when the job is already terminal or already running.
  - Records durable timeline events for worker receive, smoke stage start/success/failure, completion, and failure.
  - Marks successful smoke execution as `COMPLETED`.
  - Marks execution failures as `FAILED` with `failureStage`, `failureReason`, and `failedAt`.
- Job read API
  - Returns status metadata and timeline events in `GET /api/jobs/{jobId}`.
- Retry API
  - `POST /api/jobs/{jobId}/retry` works for failed jobs only.
  - Retry increments `retryCount`, clears failure metadata, moves the job back to `RETRYING`, and creates a new pending dispatch event.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/resources/db/migration/V3__create_job_execution_state.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobTimelineEventStatus.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/LocalizationJobRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobTimelineEventRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobTimelineEventVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobExecutionResultVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/LocalizationJobExecutionContextBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/exception/JobStateConflictException.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobTimelineEventRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobExecutionService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobRetryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationPipelineStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobRetryServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerSmokePipelineStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/worker/LocalizationJobWorker.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify tests under `LinguaFrame/src/test/java/com/linguaframe/job/...`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Worker Execution Configuration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify runtime YAML files, `.env.example`, and `docker-compose.yml`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- `LinguaFrameProperties.Worker#isExecutionEnabled()`
- `LinguaFrameProperties.Worker#getSmokeStageDurationMs()`

- [x] **Step 1: Confirm branch and baseline**

Run:

```bash
git status --short --branch
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: branch is `worker-execution-mvp`; baseline tests pass before implementation. If Maven tests need localhost socket binding, rerun with the same command outside the sandbox and record why.

- [x] **Step 2: Write failing property tests**

Extend `LinguaFramePropertiesTests` to assert:

```java
assertThat(properties.getWorker().isExecutionEnabled()).isFalse();
assertThat(properties.getWorker().getSmokeStageDurationMs()).isEqualTo(0L);
```

Also assert invalid negative smoke durations fail binding.

- [x] **Step 3: Add worker execution properties**

Add properties under `Worker`:

```java
private boolean executionEnabled = false;

@Min(0)
@Max(60000)
private long smokeStageDurationMs = 0L;
```

Add getters and setters named exactly `isExecutionEnabled`, `setExecutionEnabled`, `getSmokeStageDurationMs`, and `setSmokeStageDurationMs`.

- [x] **Step 4: Wire runtime config**

Add YAML:

```yaml
linguaframe:
  worker:
    execution-enabled: ${LINGUAFRAME_WORKER_EXECUTION_ENABLED:false}
    smoke-stage-duration-ms: ${LINGUAFRAME_WORKER_SMOKE_STAGE_DURATION_MS:0}
```

Use Docker defaults:

```yaml
LINGUAFRAME_WORKER_EXECUTION_ENABLED: ${LINGUAFRAME_WORKER_EXECUTION_ENABLED:-true}
LINGUAFRAME_WORKER_SMOKE_STAGE_DURATION_MS: ${LINGUAFRAME_WORKER_SMOKE_STAGE_DURATION_MS:-0}
```

Keep `application-test.yaml` explicit:

```yaml
linguaframe:
  worker:
    execution-enabled: false
```

- [x] **Step 5: Verify configuration**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
docker compose --env-file .env.example config
```

Expected: property tests pass and Compose renders execution env vars.

## Task 2: Durable Job Execution State

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V3__create_job_execution_state.sql`
- Modify: `LocalizationJobStatus`
- Create: `LocalizationJobStage`
- Create: `JobTimelineEventStatus`
- Modify: `LocalizationJobRecord`
- Create: `JobTimelineEventRecord`
- Modify: `LocalizationJobRepository`
- Create: `JobTimelineEventRepository`
- Modify/add repository and schema tests

**Interfaces:**
- `LocalizationJobRepository#claimForExecution(String jobId, Instant now)`
- `LocalizationJobRepository#markCompleted(String jobId, Instant completedAt)`
- `LocalizationJobRepository#markFailed(String jobId, LocalizationJobStage stage, String failureReason, Instant failedAt)`
- `LocalizationJobRepository#markRetrying(String jobId, Instant now)`
- `JobTimelineEventRepository#save(JobTimelineEventRecord record)`
- `JobTimelineEventRepository#findByJobId(String jobId)`

- [x] **Step 1: Write failing schema and repository tests**

Add tests proving:

- Flyway creates `job_timeline_events`.
- `localization_jobs` includes `started_at`, `completed_at`, `failed_at`, `failure_stage`, `failure_reason`, `retry_count`, and `updated_at`.
- `claimForExecution` changes only `QUEUED` or `RETRYING` jobs to `PROCESSING`.
- `markCompleted` sets `COMPLETED`, `completed_at`, and clears failure fields.
- `markFailed` sets `FAILED`, `failure_stage`, `failure_reason`, and `failed_at`.
- `markRetrying` works only for `FAILED` jobs and increments `retry_count`.
- Timeline events are returned ordered by `occurred_at ASC, id ASC`.

- [x] **Step 2: Add migration**

Create V3 migration:

```sql
ALTER TABLE localization_jobs
    ADD COLUMN started_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN completed_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failed_at TIMESTAMP NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failure_stage VARCHAR(64) NULL;

ALTER TABLE localization_jobs
    ADD COLUMN failure_reason VARCHAR(512) NULL;

ALTER TABLE localization_jobs
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE localization_jobs
    ADD COLUMN updated_at TIMESTAMP NULL;

UPDATE localization_jobs
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE localization_jobs
    MODIFY COLUMN updated_at TIMESTAMP NOT NULL;

CREATE TABLE job_timeline_events (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message VARCHAR(512) NOT NULL,
    duration_ms BIGINT NULL,
    error_summary VARCHAR(512) NULL,
    occurred_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_timeline_events_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
);

CREATE INDEX idx_job_timeline_events_job_id_occurred
    ON job_timeline_events(job_id, occurred_at);
```

- [x] **Step 3: Expand domain records and enums**

Use enum values:

```java
public enum LocalizationJobStatus {
    QUEUED,
    RETRYING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

```java
public enum LocalizationJobStage {
    WORKER_RECEIVED,
    WORKER_SMOKE,
    COMPLETED
}
```

```java
public enum JobTimelineEventStatus {
    STARTED,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
```

Extend `LocalizationJobRecord` with nullable execution fields and `retryCount`.

- [x] **Step 4: Implement repositories**

`claimForExecution` must use a guarded update:

```sql
UPDATE localization_jobs
SET status = 'PROCESSING',
    started_at = COALESCE(started_at, :now),
    completed_at = NULL,
    failed_at = NULL,
    failure_stage = NULL,
    failure_reason = NULL,
    updated_at = :now
WHERE id = :id
  AND status IN ('QUEUED', 'RETRYING')
```

Return `true` only when the update count is `1`.

`markRetrying` must use a guarded update:

```sql
UPDATE localization_jobs
SET status = 'RETRYING',
    retry_count = retry_count + 1,
    started_at = NULL,
    completed_at = NULL,
    failed_at = NULL,
    failure_stage = NULL,
    failure_reason = NULL,
    updated_at = :now
WHERE id = :id
  AND status = 'FAILED'
```

Return `true` only when the update count is `1`.

`save` must write `updated_at` equal to `created_at` for newly created queued jobs. `markFailed` must truncate `failureReason` to 512 characters.

- [x] **Step 5: Verify persistence**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests,LocalizationJobRepositoryTests,JobTimelineEventRepositoryTests
```

Expected: schema and repository tests pass in H2 MySQL mode.

## Task 3: Execution Service And Smoke Pipeline Stage

**Files:**
- Create: `LocalizationJobExecutionResultVo`
- Create: `LocalizationJobExecutionContextBo`
- Create: `LocalizationPipelineStage`
- Create: `LocalizationJobExecutionService`
- Create: `LocalizationJobExecutionServiceImpl`
- Create: `WorkerSmokePipelineStage`
- Add service tests

**Interfaces:**
- `LocalizationJobExecutionService#execute(QueuedLocalizationJobMessage message)`
- `LocalizationPipelineStage#stage()`
- `LocalizationPipelineStage#execute(LocalizationJobExecutionContextBo context)`
- `LocalizationJobExecutionResultVo(String jobId, boolean executed, LocalizationJobStatus status)`

- [x] **Step 1: Write failing execution service tests**

Use fake stages to prove:

- A queued job is claimed, stage events are recorded, and job becomes `COMPLETED`.
- A stale duplicate message for a completed job is skipped and records a `SKIPPED` event.
- A message whose `videoId` does not match the stored job fails safely.
- A stage exception marks the job `FAILED` and records a failed event.

- [x] **Step 2: Implement execution context**

Create:

```java
public record LocalizationJobExecutionContextBo(
        LocalizationJobRecord job,
        QueuedLocalizationJobMessage message,
        Instant startedAt
) {
}
```

- [x] **Step 3: Implement pipeline stage contract**

Create:

```java
public interface LocalizationPipelineStage {

    LocalizationJobStage stage();

    void execute(LocalizationJobExecutionContextBo context);
}
```

`WorkerSmokePipelineStage` should sleep only when `smokeStageDurationMs > 0`; otherwise it is a no-op. It must not touch files, object storage, FFmpeg, or OpenAI.

- [x] **Step 4: Implement execution service**

The service flow:

1. Load job by `message.jobId()`.
2. Reject missing jobs with a safe `NoSuchElementException`.
3. Call `claimForExecution`; if false, save a `SKIPPED` timeline event and return `executed=false`.
4. Save `WORKER_RECEIVED STARTED`.
5. Verify `record.videoId().equals(message.videoId())`; fail the claimed job with stage `WORKER_RECEIVED` if it does not match.
6. Execute stages in sorted injection order.
7. Save per-stage `STARTED` and `SUCCEEDED` events.
8. Mark job `COMPLETED` and save `COMPLETED SUCCEEDED`.
9. On runtime exception after claiming, mark job `FAILED` and save the failed stage event.

- [x] **Step 5: Verify execution service**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: execution service behavior is covered without RabbitMQ.

## Task 4: RabbitMQ Worker Listener

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/worker/LocalizationJobWorker.java`
- Add worker tests

**Interfaces:**
- `LocalizationJobWorker#handle(QueuedLocalizationJobMessage message)`

- [x] **Step 1: Write failing worker tests**

Instantiate the worker with a fake `LocalizationJobExecutionService` and assert:

- `handle` delegates the exact message.
- A runtime exception from the execution service is not swallowed.

Also add a Spring context test that verifies the listener bean exists while execution is disabled in the test profile.

- [x] **Step 2: Implement listener**

Create:

```java
@Component
public class LocalizationJobWorker {

    private final LocalizationJobExecutionService executionService;

    public LocalizationJobWorker(LocalizationJobExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(
            queues = "${linguaframe.rabbitmq.job-queue}",
            autoStartup = "${linguaframe.worker.execution-enabled:false}"
    )
    public void handle(QueuedLocalizationJobMessage message) {
        executionService.execute(message);
    }
}
```

Do not catch execution exceptions in this method; RabbitMQ retry/dead-letter policy is a later slice.

- [x] **Step 3: Verify worker listener**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobWorkerTests
```

Expected: worker tests pass without live RabbitMQ.

## Task 5: Job Detail Timeline And Retry API

**Files:**
- Create: `JobTimelineEventVo`
- Modify: `LocalizationJobVo`
- Create: `JobStateConflictException`
- Modify: `LocalizationJobQueryServiceImpl`
- Create: `LocalizationJobRetryService`
- Create: `LocalizationJobRetryServiceImpl`
- Modify: `LocalizationJobController`
- Modify: `LocalizationJobControllerTests`

**Interfaces:**
- `LocalizationJobRetryService#retryFailedJob(String jobId)`
- `GET /api/jobs/{jobId}` includes `retryCount`, failure metadata, timestamps, and `timelineEvents`
- `POST /api/jobs/{jobId}/retry` returns updated `LocalizationJobVo`

- [x] **Step 1: Write failing controller tests**

Add tests proving:

- Job detail includes timeline events ordered by occurrence.
- Failed job detail includes `failureStage`, `failureReason`, `failedAt`, and `retryCount`.
- `POST /api/jobs/{jobId}/retry` on a failed job returns status `RETRYING`.
- Retrying creates one new `PENDING` dispatch event.
- Retrying a non-failed job returns `409 CONFLICT`.

- [x] **Step 2: Extend response VOs**

Add:

```java
public record JobTimelineEventVo(
        String id,
        LocalizationJobStage stage,
        JobTimelineEventStatus status,
        String message,
        Long durationMs,
        String errorSummary,
        Instant occurredAt
) {
}
```

Extend `LocalizationJobVo` with:

```java
Instant startedAt,
Instant completedAt,
Instant failedAt,
LocalizationJobStage failureStage,
String failureReason,
int retryCount,
List<JobTimelineEventVo> timelineEvents
```

- [x] **Step 3: Implement query enrichment**

`LocalizationJobQueryServiceImpl` should load timeline events through `JobTimelineEventRepository#findByJobId` and map them into `JobTimelineEventVo`.

- [x] **Step 4: Implement retry service**

Flow:

1. Load job or throw `NoSuchElementException`.
2. If status is not `FAILED`, throw `new JobStateConflictException("Only failed localization jobs can be retried.")`.
3. Load the source video by `job.videoId()`.
4. Call `LocalizationJobRepository#markRetrying`; if it returns `false`, throw the same `JobStateConflictException`.
5. Save a `WORKER_RECEIVED STARTED` timeline event with message `Retry requested.`
6. Call `JobDispatchOutboxService#enqueueLocalizationJobQueued(video, updatedJob)`.
7. Return the refreshed `LocalizationJobVo`.

- [x] **Step 5: Add conflict mapping**

Create:

```java
public class JobStateConflictException extends RuntimeException {

    public JobStateConflictException(String message) {
        super(message);
    }
}
```

Update `ApiExceptionHandler` so `JobStateConflictException` returns `409 CONFLICT` and code `CONFLICT`. Do not change existing upload/storage safe failure mapping.

- [x] **Step 6: Verify API behavior**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

Expected: job detail and retry behavior are covered.

## Task 6: Documentation And Full Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

- [x] **Step 1: Update docs**

Document:

- `LINGUAFRAME_WORKER_EXECUTION_ENABLED`
- `LINGUAFRAME_WORKER_SMOKE_STAGE_DURATION_MS`
- Worker lifecycle behavior.
- The fact that this slice uses a smoke stage and does not run FFmpeg/OpenAI yet.
- Retry behavior and conflict rule.

- [x] **Step 2: Run full verification**

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

- [x] **Step 3: Record evidence**

Append a dated entry to `docs/progress/execution-log.md` with:

- Failing tests observed before implementation.
- Final focused test results.
- Full `mvn test` result count.
- Compose config and Docker build results.
- Notes that real FFmpeg/OpenAI execution remains a follow-up.

## Completion Criteria

- Worker execution can be enabled through Docker config and remains disabled in tests.
- Rabbit listener delegates queued job messages to a testable execution service.
- Jobs have durable execution timestamps, failure metadata, retry count, and timeline events.
- Duplicate/stale worker messages do not corrupt terminal jobs.
- Failed jobs can be retried through API and re-enter the existing dispatch outbox.
- Job detail returns timeline and failure state for operator visibility.
- README and execution log explain the worker lifecycle smoke mode.
- Feature branch can be merged back into `main` after verification.

## Follow-Up Slice

After this feature, the next slice should replace `WorkerSmokePipelineStage` with a real FFmpeg audio extraction stage:

- Install or verify FFmpeg in the backend/worker container.
- Download the source object from MinIO to a controlled workspace path.
- Extract audio into a normalized format.
- Store the audio artifact in object storage.
- Record artifact metadata and FFmpeg duration/error details.
