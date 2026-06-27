# Retry Guard And Evidence MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make failed-job retry demo-ready by enforcing the configured retry limit, exposing retry evidence clearly, and documenting a repeatable validation path.

**Architecture:** Reuse the existing `POST /api/jobs/{jobId}/retry` API, `retry_count` field, timeline events, job detail UI, and Docker retry script. The backend will enforce `linguaframe.worker.max-retries` before transitioning a failed job back to `RETRYING`; the frontend and docs will explain why a retry is unavailable once the limit is reached.

**Tech Stack:** Spring Boot, Java 21, JdbcClient, JUnit 5, Mockito, React, TypeScript, Vitest, Bash demo scripts, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Do not add user accounts, a full admin dashboard, or a new retry history table in this slice.
- Keep retry evidence based on existing durable data: `retry_count`, `failure_stage`, `failure_reason`, dispatch events, and timeline events.
- Do not log secrets, raw OpenAI payloads, or raw media paths.
- Plan and discussion can be Chinese, but repository docs and UI copy stay English.

---

## Task 1: Backend Retry Limit Enforcement

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobRetryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobRetryServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- Consumes: `LinguaFrameProperties.getWorker().getMaxRetries()`
- Produces: `retryFailedJob(String jobId)` rejects failed jobs whose `retryCount >= maxRetries`

- [x] Add failing tests that configure `maxRetries=1` and verify a failed job with `retryCount=1` throws `JobStateConflictException` without calling `markRetrying`, `enqueueLocalizationJobQueued`, or cache eviction.
- [x] Add a passing-path test showing `retryCount=0` with `maxRetries=1` still transitions to `RETRYING`.
- [x] Wire `LinguaFrameProperties` into `LocalizationJobRetryServiceImpl`.
- [x] Enforce the retry limit before loading the source video or mutating job state.
- [x] Keep the existing conflict message style, using: `Retry limit reached for this localization job.`
- [x] Run: `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LinguaFramePropertiesTests' test`.

## Task 2: API And UI Retry Evidence

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: existing `LocalizationJob.retryCount`, `failureStage`, `failureReason`, and API error responses
- Produces: visible retry evidence in the selected job panel and a clear error when retry is rejected

- [x] Add a controller test that verifies retry-limit conflicts return a structured conflict response through the existing exception handler.
- [x] Add a frontend test where retry rejects with `Retry limit reached for this localization job.` and the selected job remains visible with the error shown.
- [x] Update the selected job metadata copy so `Retries` is easy to find next to stage/status evidence.
- [x] Keep the retry button visible only for `FAILED` jobs; do not add a disabled speculative limit check in the browser because the backend is the source of truth.
- [x] Run: `cd frontend && npm run test:run -- App`.
- [x] Run: `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,LocalizationJobRetryServiceTests' test`.

## Task 3: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/docker-e2e-retry.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: `LINGUAFRAME_WORKER_MAX_RETRIES`
- Produces: documented and script-visible retry limit behavior

- [x] Add `LINGUAFRAME_WORKER_MAX_RETRIES=2` to `.env.example`.
- [x] Pass `LINGUAFRAME_WORKER_MAX_RETRIES` through the backend and worker service environment in `docker-compose.yml`.
- [x] Enhance `print_job_summary` to show failure stage and failure reason when present.
- [x] Update `scripts/demo/docker-e2e-retry.sh` to print the configured retry limit and explain that the script demonstrates one successful retry after disabling forced smoke failure.
- [x] Document the retry demo command, required forced-failure env, and retry-limit behavior in `README.md`.
- [x] Update product docs to say retry is bounded by `linguaframe.worker.max-retries` and visible through retry count plus timeline events.
- [x] Record the implementation decision that retry limit enforcement stays in the backend service, not the frontend.
- [x] Run: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-retry.sh`.
- [x] Run: `docker compose --env-file .env.example config --quiet`.

## Task 4: Full Verification And Merge

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/044-retry-guard-and-evidence-mvp.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [x] Commit as `Add retry guard and evidence`.
- [x] Merge `retry-guard-and-evidence-mvp` back to `main`.
- [x] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- App`.
