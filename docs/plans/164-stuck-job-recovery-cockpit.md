# Stuck Job Recovery Cockpit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe recovery cockpit for queued, retrying, or processing jobs that appear stuck, so demo operators can diagnose queue/worker issues and choose a controlled recovery action.

**Architecture:** Reuse existing durable job, dispatch outbox, timeline, retry, cancel, live-check, and run-monitor data. Add one backend aggregate plus a controlled recovery command endpoint; expose the same cockpit in React and terminal scripts without auto-running FFmpeg, OpenAI, Docker, or destructive cleanup.

**Tech Stack:** Spring Boot MVC/JDBC/JUnit 5, existing job repositories/services, React + TypeScript + Vitest/jsdom, Bash demo helpers, Markdown docs.

## Global Constraints

- This feature must be a complete slice: backend API, frontend UI, terminal script, docs, tests, and validation evidence.
- The cockpit must not call OpenAI, TTS providers, FFmpeg, Docker, object storage mutations, uploads, or retention cleanup.
- The cockpit must not print secrets, bearer tokens, demo tokens, object keys, local paths, provider payloads, raw transcript text, raw subtitle text, or narration text.
- Recovery actions must be explicit and limited to safe existing state transitions: retry a failed job, cancel an active job, or requeue a stale dispatch event when a job has not been picked up.
- No automatic background recovery loop in this slice.

---

## Scope

Build `Stuck job recovery` as the next operational reliability surface:

- `GET /api/jobs/{jobId}/stuck-job-recovery`
- `GET /api/jobs/{jobId}/stuck-job-recovery/markdown/download`
- `POST /api/jobs/{jobId}/stuck-job-recovery/actions`
- React selected-job panel near `Demo run monitor`.
- Terminal script `scripts/demo/stuck-job-recovery.sh`.
- Full-demo docs and smoke checklist updates.

## Backend Model

Create VOs:

- `StuckJobRecoveryVo`
  - `jobId`, `videoId`, `generatedAt`, `status`, `attentionLevel`, `classification`, `headline`, `recommendedNextAction`
  - `jobStatus`, `dispatchStatus`, `dispatchAttempts`, `dispatchedAt`, `lastTimelineAt`, `ageSeconds`, `staleSeconds`
  - `checks`, `actions`, `safeLinks`, `safetyNotes`, `markdown`
- `StuckJobRecoveryCheckVo`
  - `key`, `label`, `status`, `detail`, `nextAction`, `blocking`
- `StuckJobRecoveryActionVo`
  - `id`, `label`, `method`, `href`, `enabled`, `requiresConfirmation`, `description`
- `StuckJobRecoveryActionRequest`
  - `actionId`, `confirmation`

Classifications:

- `READY`: terminal completed job, no recovery needed.
- `FAILED_RETRYABLE`: failed job can use existing retry endpoint.
- `FAILED_BLOCKED`: failed job reached retry limit or non-retryable state.
- `QUEUED_WAITING`: queued/retrying job has a pending dispatch event that is not stale.
- `QUEUED_STALE_DISPATCH`: queued/retrying job has no dispatch pickup after the configured threshold.
- `PROCESSING_STALE_STAGE`: processing job has a running timeline stage beyond the threshold.
- `CANCELLED`: job is already cancelled.
- `UNKNOWN`: inconsistent metadata that needs manual inspection.

Thresholds:

- Use a property default such as `linguaframe.worker.stuck-job-threshold-seconds=900`.
- Use this threshold only for classification and recommendations. It must not mutate jobs.

## Recovery Actions

Action endpoint accepts:

- `REQUEUE_DISPATCH`: allowed only when classification is `QUEUED_STALE_DISPATCH`; creates a fresh dispatch outbox event from the current job/video metadata and writes a safe timeline event such as `Stale dispatch requeued by operator.`
- `CANCEL_JOB`: delegates to existing cancellation service for queued/retrying/processing jobs.
- `RETRY_FAILED_JOB`: delegates to existing retry service for failed jobs.

All actions:

- Require confirmation text equal to the action id.
- Evict job status cache after mutation.
- Return refreshed `StuckJobRecoveryVo`.
- Reject invalid state with `409 CONFLICT`.

## Frontend Shape

Add a selected-job panel labeled `Stuck job recovery` near `Demo run monitor`.

Show:

- Attention level and classification.
- Current job/dispatch/timeline age metrics.
- Check rows for dispatch outbox, timeline freshness, live dependency hint, retry/cancel eligibility, and safety.
- Safe actions as buttons:
  - `Requeue dispatch`
  - `Cancel job`
  - `Retry failed job`
- Confirmation prompt before each action.
- `Download recovery Markdown`.

Keep the rest of the selected job usable when this panel fails to load.

## Terminal Shape

Add `scripts/demo/stuck-job-recovery.sh`.

Behavior:

- Requires `LINGUAFRAME_DEMO_JOB_ID` or first positional job id.
- Downloads JSON and Markdown under `/tmp/linguaframe-demo/stuck-job-recovery/`.
- Prints metadata-only lines:
  - `stuckJobRecoveryStatus`
  - `stuckJobRecoveryClassification`
  - `stuckJobRecoveryAttention`
  - `stuckJobRecoveryRecommendedAction`
  - `stuckJobRecoveryAction=<id>:<enabled>`
  - `stuckJobRecoveryCheck=<status>:<key>:<label>`
- Optional action execution:
  - `LINGUAFRAME_STUCK_JOB_RECOVERY_ACTION=REQUEUE_DISPATCH|CANCEL_JOB|RETRY_FAILED_JOB`
  - Requires `LINGUAFRAME_STUCK_JOB_RECOVERY_CONFIRM=<same action id>`.
- Exits non-zero for `BLOCKED` or stale classifications unless `LINGUAFRAME_STUCK_JOB_RECOVERY_REPORT_ONLY=true`.

## Tests

Backend:

- Service tests for each classification above.
- Action tests proving:
  - stale dispatch requeue creates a new outbox event and timeline event.
  - cancel delegates to cancellation semantics.
  - retry delegates to retry semantics.
  - invalid confirmation or invalid state returns conflict.
  - JSON/Markdown omit local paths, object keys, tokens, provider payloads, and raw media text markers.
- Controller tests for JSON, Markdown, and action endpoint content/status.
- Runtime/OpenAPI endpoint lists include the new routes.

Frontend:

- API tests for `getStuckJobRecovery`, `downloadStuckJobRecoveryMarkdown`, and `runStuckJobRecoveryAction`.
- App test renders the panel, check rows, disabled/enabled actions, and Markdown download.
- App test confirms action execution requires confirmation and refreshes selected job/recovery data.
- App test proves upload/job detail remain usable when the recovery endpoint fails.

Scripts:

- Demo helper tests verify summary output, redaction guards, report-only behavior, and action confirmation behavior.
- `bash -n scripts/demo/stuck-job-recovery.sh scripts/demo/lib/linguaframe-demo.sh`.

Docs:

- README: when to use stuck-job recovery versus demo run monitor, retry, cancel, and private-demo preflight.
- `docs/agent/docker-e2e-demo.md`: add stuck-job recovery to failed/stale run guidance.
- `docs/agent/smoke-test-checklist.md`: add focused checks.
- `docs/product/roadmap.md` and `docs/product/target-state.md`: mark queue/worker recovery cockpit as implemented after validation.
- `docs/progress/execution-log.md`: record RED/GREEN and full validation.

## Validation

- `mvn -pl LinguaFrame -Dtest=StuckJobRecoveryServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "stuck job recovery"`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/stuck-job-recovery.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A stale queued/retrying job can be diagnosed and safely requeued without manually editing the database.
- A stale processing job gives clear diagnosis and safe cancel guidance without unsafe automatic retry.
- A failed job surfaces retry eligibility in the same recovery cockpit.
- Browser, backend, terminal, and docs expose the same recovery state.
- The feature is metadata-only, secret-safe, and integrated into final validation evidence.
