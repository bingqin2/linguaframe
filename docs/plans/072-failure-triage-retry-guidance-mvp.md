# Failure Triage And Retry Guidance MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make failed LinguaFrame jobs actionable in the browser, diagnostics exports, evidence reports, and terminal demo scripts without exposing sensitive data.

**Architecture:** Add a backend failure-triage service that derives safe categories and retry guidance from existing job status, failure metadata, timeline events, and model-call summaries. Attach the resulting structure to `LocalizationJobVo` so the same advice flows through job detail, SSE snapshots, diagnostics JSON, backend Markdown evidence, browser evidence export, and shell demo output. Keep retry behavior unchanged; this slice only explains what likely failed and what the operator should do next.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React, TypeScript, Vitest, Bash, existing LinguaFrame demo scripts.

## Global Constraints

- Do not change provider request payloads, provider parsing, retry state transitions, dispatch behavior, or budget accounting.
- Do not expose API keys, bearer/demo tokens, object storage keys, local media paths, raw provider payloads, raw transcript text, raw subtitle text, or media bytes.
- Triage is advisory and must fall back to `UNKNOWN` when evidence is ambiguous.
- `failureTriage` should be omitted or `null` for non-terminal successful/in-progress jobs.
- Existing deterministic demo and OpenAI demo scripts must keep working.

---

## Task 1: Backend Failure Triage Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/FailureTriageCategory.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/FailureTriageVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/FailureTriageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/FailureTriageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/FailureTriageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobQueryServiceTests.java`

**Steps:**

- [x] Define categories: `CONFIGURATION`, `OPENAI_AUTH_OR_MODEL`, `OPENAI_TIMEOUT_OR_NETWORK`, `BUDGET_GUARD`, `MEDIA_PROCESSING`, `STORAGE_OR_ARTIFACT`, `WORKER_OR_QUEUE`, `USER_CANCELLED`, and `UNKNOWN`.
- [x] Add `FailureTriageVo` fields: `category`, `summary`, `recommendedAction`, `retryable`, `runbookCommand`, and `safeDetails`.
- [x] Implement `FailureTriageService.triage(LocalizationJobRecord record, List<JobTimelineEventRecord> timelineEvents, List<ModelCallVo> modelCalls)`.
- [x] Return `null` unless the job status is `FAILED` or `CANCELLED`.
- [x] Map cancelled jobs to `USER_CANCELLED` and `retryable=false`.
- [x] Map budget-exceeded failures to `BUDGET_GUARD` with guidance to adjust budget env values or use the deterministic demo profile.
- [x] Map OpenAI `401`, `403`, `invalid_api_key`, missing model, unknown model, or model-not-found evidence to `OPENAI_AUTH_OR_MODEL`.
- [x] Map OpenAI timeout, connection reset, DNS, rate limit, or 5xx evidence to `OPENAI_TIMEOUT_OR_NETWORK`.
- [x] Map FFmpeg, unsupported media, unreadable media, duration, audio extraction, and subtitle burn-in evidence to `MEDIA_PROCESSING`.
- [x] Map artifact, MinIO, object storage, bundle, archive, or download evidence to `STORAGE_OR_ARTIFACT`.
- [x] Map worker smoke, dispatch, queue, and worker-received failures to `WORKER_OR_QUEUE`.
- [x] Fall back to `CONFIGURATION` for missing required config evidence, otherwise `UNKNOWN`.
- [x] Attach the triage result to `LocalizationJobVo` in `LocalizationJobQueryServiceImpl`.
- [x] Add focused tests for each category using safe failure strings and failed model-call summaries.
- [x] Add a query-service test proving failed job detail includes `failureTriage` and completed job detail does not.

## Task 2: Diagnostics And Evidence Propagation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobDiagnosticsReportVo.java` only if an explicit report-level shortcut is useful; otherwise rely on `report.job.failureTriage`.
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Test: existing diagnostics/evidence tests if present, otherwise add coverage in `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`

**Steps:**

- [x] Keep diagnostics JSON metadata-only and include triage through the embedded safe `LocalizationJobVo`.
- [x] Add a `Failure triage` line to backend Markdown evidence with category, retryable flag, summary, and recommended action.
- [x] Include `runbookCommand` only when it is a safe static command such as `scripts/demo/openai-demo-preflight.sh` or `scripts/demo/docker-e2e-success.sh`.
- [x] Verify evidence output does not contain object keys, local paths, tokens, provider payloads, or raw transcript/subtitle content.
- [x] Add tests for Markdown evidence with and without triage.

## Task 3: Browser Failure Triage Panel And Evidence Export

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/App.css` if layout needs a small panel style adjustment.

**Steps:**

- [x] Add TypeScript types for `FailureTriageCategory` and `FailureTriage`.
- [x] Add `failureTriage: FailureTriage | null` to `LocalizationJob`.
- [x] Render a `Failure triage` panel in the selected job view when triage exists.
- [x] Show category, summary, recommended action, retryability, safe details, and runbook command.
- [x] Keep the existing `Retry` button behavior unchanged; do not auto-retry based on triage.
- [x] Add triage to browser-generated demo evidence Markdown and JSON export.
- [x] Add Vitest coverage proving a failed OpenAI auth/model job shows actionable triage and a completed job does not show the panel.

## Task 4: Terminal Demo Script Triage Output

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-budget-guard.sh`
- Modify: `scripts/demo/docker-e2e-daily-budget-guard.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh` only if failed-job output needs a clearer handoff.

**Steps:**

- [x] Print `failureTriageCategory`, `failureTriageRetryable`, `failureTriageSummary`, and `failureTriageRecommendedAction` in `print_job_summary`.
- [x] Print triage fields in `print_diagnostics_summary` when present.
- [x] Keep script forbidden-string checks strict for keys, tokens, object keys, provider payloads, and local raw paths.
- [x] Update the demo client test fixture to include triage and assert the new summary lines.
- [x] Ensure budget-guard scripts can assert the `BUDGET_GUARD` category after controlled failures.

## Task 5: Documentation, Progress Notes, And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/072-failure-triage-retry-guidance-mvp.md`

**Steps:**

- [x] Document that failed job detail now includes safe triage guidance for OpenAI, budget, media, storage, worker, queue, cancellation, and unknown failures.
- [x] Add demo-run instructions for using triage after a failed OpenAI smoke run.
- [x] Add smoke checklist items for browser triage panel, diagnostics JSON, backend evidence Markdown, and terminal output.
- [x] Record the decision that LinguaFrame keeps triage advisory instead of changing retry semantics.
- [x] Mark plan checkboxes as tasks complete.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=FailureTriageServiceTests,LocalizationJobQueryServiceTests,JobEvidenceReportServiceTests test`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-budget-guard.sh scripts/demo/docker-e2e-daily-budget-guard.sh scripts/demo/docker-e2e-openai-smoke.sh`.
- [x] Run `cd frontend && npm run test:run -- App`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `git diff --check`.
- [ ] Optional live validation with local Docker and configured env:

```bash
docker compose --env-file .env up -d --force-recreate linguaframe-backend
scripts/demo/docker-e2e-budget-guard.sh
LINGUAFRAME_ENV_FILE=.env.openai-demo \
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 \
scripts/demo/docker-e2e-openai-smoke.sh
```

## Done Criteria

- [x] Failed job detail returns a safe `failureTriage` object with category, summary, action, retryability, and optional runbook command.
- [x] Browser users can understand why a job failed and what to try next without reading logs.
- [x] Diagnostics JSON, backend evidence Markdown, browser evidence export, and shell scripts show the same safe triage.
- [x] Controlled budget failures produce `BUDGET_GUARD`; OpenAI auth/model failures produce `OPENAI_AUTH_OR_MODEL`; ambiguous failures produce `UNKNOWN`.
- [x] Retry behavior remains backend-controlled and unchanged.
- [x] The feature branch is committed, verified, and merged back to `main`.
