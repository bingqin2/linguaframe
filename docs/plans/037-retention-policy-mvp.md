# Retention Policy MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a configurable, default-off retention policy that removes old terminal demo jobs, source videos, and generated artifacts so private demo deployments do not accumulate media forever.

**Architecture:** Add retention settings under `linguaframe.retention`, a service that finds terminal jobs older than the retention window, deletes object-storage blobs first, then deletes dependent database rows in a safe order. Expose a manual operator endpoint for dry-run and execute modes, and add an optional scheduler for deployments that opt in.

**Tech Stack:** Java 21, Spring Boot MVC, Spring scheduling, JDBC repositories, MinIO object storage, JUnit 5, MockMvc, Docker Compose env config.

## Global Constraints

- This feature must be a complete feature slice: backend cleanup service, manual operator API, optional scheduler, Docker/env docs, tests, validation, commit, and merge back to `main`.
- Retention must be disabled by default.
- Retention must only target terminal jobs: `COMPLETED`, `FAILED`, and `CANCELLED`.
- Retention must never delete `QUEUED`, `RETRYING`, or `PROCESSING` jobs.
- Dry-run mode must report what would be deleted without deleting database rows or object storage.
- Never log or render object storage credentials, API keys, raw transcript text, or local media paths.

---

## Task 1: Retention Configuration And Candidate Query

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`

**Interfaces:**

- `LinguaFrameProperties.Retention` fields:
  - `enabled`, default `false`
  - `dryRun`, default `true`
  - `completedJobTtlDays`, default `7`
  - `failedJobTtlDays`, default `3`
  - `cancelledJobTtlDays`, default `3`
  - `cleanupBatchSize`, default `25`
  - `schedulerEnabled`, default `false`
  - `schedulerIntervalMs`, default `3600000`
- `LocalizationJobRepository.findRetentionCandidates(Set<LocalizationJobStatus> statuses, Instant cutoff, int limit)` returns oldest matching terminal jobs with video ids.

- [x] **Step 1: Add failing configuration and repository tests**
  - Assert default retention config is disabled and dry-run.
  - Assert env-style properties bind all retention fields.
  - Insert terminal and non-terminal jobs with different `updated_at` values.
  - Assert only terminal jobs older than the cutoff are returned.
  - Assert `QUEUED`, `RETRYING`, and `PROCESSING` are not returned even when old.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,LocalizationJobRepositoryTests test
```

Expected: compilation failure because retention properties and candidate query do not exist.

- [x] **Step 3: Implement config and candidate query**
  - Add nested `Retention` properties with validation ranges.
  - Add YAML placeholders:
    - `LINGUAFRAME_RETENTION_ENABLED`
    - `LINGUAFRAME_RETENTION_DRY_RUN`
    - `LINGUAFRAME_RETENTION_COMPLETED_JOB_TTL_DAYS`
    - `LINGUAFRAME_RETENTION_FAILED_JOB_TTL_DAYS`
    - `LINGUAFRAME_RETENTION_CANCELLED_JOB_TTL_DAYS`
    - `LINGUAFRAME_RETENTION_CLEANUP_BATCH_SIZE`
    - `LINGUAFRAME_RETENTION_SCHEDULER_ENABLED`
    - `LINGUAFRAME_RETENTION_SCHEDULER_INTERVAL_MS`
  - Add a small `RetentionJobCandidateVo` or record under `job/domain/vo`.
  - Query by `status IN (...)`, `updated_at < :cutoff`, oldest first, capped by limit.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,LocalizationJobRepositoryTests test
```

Expected: selected tests pass.

## Task 2: Object Storage Delete Boundary

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/ObjectStorageService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/impl/MinioObjectStorageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/storage/service/MinioObjectStorageServiceTests.java`
- Update existing test doubles under job/media tests as needed.

**Interfaces:**

- `ObjectStorageService.delete(String objectKey)` deletes one object.
- Delete is idempotent from the retention service perspective: missing objects are reported as skipped, not as fatal cleanup failures.

- [x] **Step 1: Add failing storage delete tests**
  - Assert `MinioObjectStorageServiceImpl.delete("objects/demo.mp4")` calls MinIO remove-object for the configured bucket/key.
  - Assert storage exceptions are wrapped as safe `IllegalStateException("Object storage delete failed.")`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=MinioObjectStorageServiceTests test
```

Expected: compilation failure because `delete` is not on the interface.

- [x] **Step 3: Implement storage delete**
  - Add `delete(String objectKey)` to `ObjectStorageService`.
  - Use MinIO `RemoveObjectArgs`.
  - Keep error messages generic and credential-free.
  - Update in-memory/recording test implementations to track deleted object keys.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=MinioObjectStorageServiceTests,JobArtifactServiceTests,MediaUploadServiceTests test
```

Expected: selected tests pass.

## Task 3: Retention Cleanup Service And Database Deletion

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/RetentionCleanupResultVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/RetentionCleanupService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/RetentionCleanupServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobArtifactRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobDispatchEventRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobTimelineEventRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/RetentionCleanupServiceTests.java`

**Interfaces:**

- `RetentionCleanupService.previewCleanup()` returns `RetentionCleanupResultVo`.
- `RetentionCleanupService.runCleanup()` deletes when retention is enabled and not dry-run.
- `RetentionCleanupResultVo` includes `dryRun`, `candidateJobCount`, `deletedJobCount`, `deletedVideoCount`, `deletedObjectCount`, `skippedObjectCount`, and `failureCount`.

- [x] **Step 1: Add failing cleanup service tests**
  - Dry-run reports eligible job/source/artifact counts and deletes nothing.
  - Disabled retention returns an empty dry-run result.
  - Execute mode deletes artifact objects, source video object, timeline rows, dispatch rows, job rows, and orphaned videos.
  - Execute mode does not delete a shared video if another job for the same video remains.
  - Object delete failure increments `failureCount` and leaves database rows for that job.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests test
```

Expected: compilation failure because retention service and deletion repository methods do not exist.

- [x] **Step 3: Implement cleanup service**
  - Compute separate cutoffs per terminal status from `Clock`.
  - Fetch candidates up to `cleanupBatchSize`.
  - Load artifacts by job id and source video by video id.
  - In execute mode, delete storage objects before database rows.
  - Delete database rows in this order: dispatch events, timeline events, job row; `job_artifacts`, model calls, transcript segments, subtitles, and quality evaluations rely on existing cascade where available.
  - Delete the video row only if no remaining jobs reference it.
  - Return a count-based result without object keys.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests,LocalizationJobRepositoryTests,JobArtifactRepositoryTests test
```

Expected: selected tests pass.

## Task 4: Operator API And Optional Scheduler

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/controller/RetentionCleanupController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/scheduling/RetentionCleanupScheduler.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/RetentionCleanupControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/scheduling/RetentionCleanupSchedulerTests.java`

**Interfaces:**

- `GET /api/retention/cleanup/preview` returns dry-run counts.
- `POST /api/retention/cleanup/run` runs cleanup only when enabled and not dry-run.
- Scheduler calls `runCleanup()` only when both `retention.enabled` and `retention.scheduler-enabled` are true.

- [x] **Step 1: Add failing API and scheduler tests**
  - Preview endpoint returns cleanup counts.
  - Run endpoint returns cleanup counts.
  - Scheduler is inert by default.
  - Scheduler invokes cleanup when enabled.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=RetentionCleanupControllerTests,RetentionCleanupSchedulerTests test
```

Expected: compilation failure because controller and scheduler do not exist.

- [x] **Step 3: Implement API and scheduler**
  - Add controller under `/api/retention/cleanup`.
  - Keep responses count-only.
  - Add scheduler with fixed delay from `linguaframe.retention.scheduler-interval-ms`.
  - Log only aggregate counts and failure count.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=RetentionCleanupControllerTests,RetentionCleanupSchedulerTests test
```

Expected: selected tests pass.

## Task 5: Runtime Docs, Validation, Commit, And Merge

**Files:**

- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/037-retention-policy-mvp.md`

- [x] **Step 1: Update docs and runtime env**
  - Document default-off retention behavior.
  - Document dry-run preview before execute mode.
  - Add Compose env variables for all retention settings.
  - Mark Phase 9 file retention policy as implemented.
  - Record the decision to use default-off, count-only retention cleanup before full user auth/admin permissions.

- [x] **Step 2: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
git diff --check
```

Expected: all commands exit 0.

- [x] **Step 3: Commit feature branch**

```bash
git add README.md .env.example docker-compose.yml docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md docs/plans/037-retention-policy-mvp.md LinguaFrame/src
git commit -m "Add retention policy cleanup"
```

- [x] **Step 4: Merge back to main**

```bash
git switch main
git merge --no-ff retention-policy-mvp
```

- [x] **Step 5: Run post-merge smoke validation**

```bash
mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests,RetentionCleanupControllerTests test
docker compose --env-file .env.example config --quiet
```

Expected: post-merge checks pass on `main`.
