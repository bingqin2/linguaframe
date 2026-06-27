# Job-Scoped Structured Logging MVP

**Goal:** Make worker and job execution logs searchable by job, video, stage, and worker role without exposing secrets or raw media paths.

**Architecture:** Add a small MDC helper for LinguaFrame log context and wire it into the worker execution path. Configure log patterns to print stable context keys when present. Keep durable job state, timeline events, API payloads, and provider behavior unchanged.

## Scope

- Add a backend logging context helper for safe MDC keys:
  - `jobId`
  - `videoId`
  - `stage`
  - `workerRole`
- Wrap job execution and each pipeline stage with context-scoped MDC.
- Add concise worker execution logs for:
  - job message received
  - stale message skipped
  - worker role/stage plan selected
  - stage started
  - stage succeeded
  - stage failed
  - job completed
  - worker handoff
  - job canceled
- Configure console log patterns in default, local, test, and Docker profiles so MDC appears as `jobId=... videoId=... stage=... workerRole=...` when present.
- Add tests that prove:
  - MDC helper sets and restores keys.
  - execution clears MDC after successful and failed jobs.
  - stage-level context includes stage and worker role while the stage runs.
- Update README, Docker E2E guide, smoke-test checklist, roadmap, and execution log.

## Non-Goals

- Do not add log aggregation, tracing systems, OpenTelemetry, JSON log encoders, or external dependencies.
- Do not log OpenAI keys, demo tokens, object storage credentials, source object keys, local filesystem paths, raw transcript text, raw subtitle text, provider payloads, or media bytes.
- Do not change job status transitions, timeline event semantics, provider calls, cache behavior, queue routing, or API responses.
- Do not add frontend UI for logs in this slice.

## Implementation Steps

1. **Logging context helper**
   - Create `LinguaFrame/src/main/java/com/linguaframe/common/logging/LinguaFrameLogContext.java`.
   - Provide scoped helpers such as `withJob(String jobId, String videoId)` and `withStage(String stage, String workerRole)`.
   - Use `org.slf4j.MDC` and return `AutoCloseable` scopes that restore previous values on close.
   - Ignore blank values instead of writing empty MDC entries.

2. **Logging context tests**
   - Create `LinguaFrame/src/test/java/com/linguaframe/common/logging/LinguaFrameLogContextTests.java`.
   - Verify job context sets `jobId` and `videoId`.
   - Verify nested stage context sets `stage` and `workerRole`.
   - Verify closing nested scopes restores previous values.
   - Verify all keys are removed after outer scope closes.

3. **Worker execution logging**
   - Modify `LocalizationJobExecutionServiceImpl`.
   - Add a `Logger`.
   - Wrap `execute(...)` in job context as soon as the queued message is available.
   - Add stage context around each `LocalizationPipelineStage.execute(...)` call.
   - Add logs only with safe IDs and enum values; do not log `sourceObjectKey`.
   - Ensure try-with-resources clears MDC on every success, failure, skip, cancellation, and handoff path.

4. **Execution MDC tests**
   - Extend `LocalizationJobExecutionServiceTests`.
   - Add a recording stage that asserts `MDC.get("jobId")`, `MDC.get("videoId")`, `MDC.get("stage")`, and `MDC.get("workerRole")` while executing.
   - Assert MDC keys are absent after a successful execution.
   - Add a failing stage case and assert MDC keys are absent after the failure result.

5. **Log pattern configuration**
   - Update `LinguaFrame/src/main/resources/application.yaml`, `application-local.yaml`, `application-test.yaml`, and `application-docker.yaml`.
   - Add `logging.pattern.console` with timestamp, level, thread, logger, message, and the four MDC keys.
   - Keep the format plain text for local and Docker readability.

6. **Documentation**
   - Update README with how to grep Docker/backend logs by `jobId`, `stage`, and `workerRole`.
   - Update `docs/agent/docker-e2e-demo.md` with a post-run log inspection command.
   - Update `docs/agent/smoke-test-checklist.md` with structured log checks.
   - Mark roadmap structured logs as implemented.
   - Record red/green validation in `docs/progress/execution-log.md`.

7. **Validation**
   - Run `mvn -pl LinguaFrame -Dtest=LinguaFrameLogContextTests,LocalizationJobExecutionServiceTests test`.
   - Run `mvn -pl LinguaFrame test`.
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh scripts/demo/docker-e2e-success.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- Worker logs produced during a job can be searched by `jobId`, `videoId`, `stage`, and `workerRole`.
- MDC context is restored or cleared after success, failure, cancellation, stale-message skip, and handoff paths.
- Logs include safe operational identifiers and enum values only.
- Existing job execution, timeline, cache, retry, artifacts, diagnostics, frontend demo, and Docker scripts remain valid.
- The feature is verified, committed on a feature branch, and merged back to `main`.
