# Pipeline Progress And Stage Timing Demo MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make long-running local and OpenAI demo jobs easier to monitor by exposing safe pipeline progress, per-stage timing, and slow-stage evidence across the API, browser, operator dashboard, diagnostics, evidence reports, and terminal scripts.

**Architecture:** Reuse existing durable `job_timeline_events` as the source of truth. Add a backend progress-summary builder that derives ordered stage states, completed/failed/skipped counts, total measured duration, current stage, and slowest stage without changing worker execution semantics. Attach the summary to job detail and diagnostics, aggregate stage timing in the operator dashboard, then render the same information in React and demo scripts.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, MySQL/JdbcClient, React, TypeScript, Vitest, Bash, existing LinguaFrame demo scripts.

## Global Constraints

- Do not change worker stage ordering, queue routing, retry transitions, cancellation semantics, provider calls, FFmpeg calls, or artifact generation.
- Do not add a new persistence table; derive progress and timing from existing job, timeline, and model-call records.
- Do not expose object storage keys, local media paths, raw transcript text, raw subtitle text, provider payloads, API keys, demo tokens, or media bytes.
- Progress is advisory and must tolerate missing or duplicate timeline events.
- Stage timing must use safe enum names, counts, timestamps, and durations only.
- Existing deterministic, cache-hit, budget-guard, full-video, and OpenAI smoke scripts must keep working.

---

## Task 1: Job Pipeline Progress Summary Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobStageProgressVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobPipelineProgressVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobPipelineProgressService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobPipelineProgressServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobPipelineProgressServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobQueryServiceTests.java`

**Steps:**

- [x] Define `JobStageProgressVo` fields: `stage`, `status`, `startedAt`, `finishedAt`, `durationMs`, and `message`.
- [x] Define `JobPipelineProgressVo` fields: `totalStageCount`, `completedStageCount`, `failedStageCount`, `skippedStageCount`, `cacheHitStageCount`, `currentStage`, `terminal`, `totalMeasuredDurationMs`, `slowestStage`, `slowestStageDurationMs`, and `stages`.
- [x] Implement `JobPipelineProgressService.summarize(LocalizationJobRecord record, List<JobTimelineEventRecord> timelineEvents)`.
- [x] Derive one ordered progress row per `LocalizationJobStage`.
- [x] Treat `FAILED`, `SUCCEEDED`, `SKIPPED`, and `CACHE_HIT` as terminal stage statuses for that stage, with the latest event winning when duplicates exist.
- [x] Use `durationMs` from terminal timeline events when present; otherwise keep duration `null`.
- [x] Mark `terminal=true` when the job status is `COMPLETED`, `FAILED`, or `CANCELLED`.
- [x] Attach `pipelineProgress` to `LocalizationJobVo` and therefore to SSE snapshots, diagnostics JSON, evidence bundle diagnostics, and cache snapshots.
- [x] Add tests for completed, failed, processing, skipped, cache-hit, duplicate-event, and empty-timeline jobs.

## Task 2: Operator Stage Timing Aggregates

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorStageTimingVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorDashboardVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/repository/OperatorDashboardRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/repository/OperatorDashboardRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Steps:**

- [x] Add `stageTimings: List<OperatorStageTimingVo>` to `OperatorDashboardVo`.
- [x] Define `OperatorStageTimingVo` fields: `stage`, `completedEventCount`, `failedEventCount`, `averageDurationMs`, `maxDurationMs`, and `latestDurationMs`.
- [x] Query `job_timeline_events` grouped by `stage` for events where `duration_ms IS NOT NULL`.
- [x] Count `SUCCEEDED` and `FAILED` events separately; do not count `STARTED` durations.
- [x] Sort the output by `maxDurationMs DESC`, then `stage ASC`.
- [x] Limit to the top 6 stages so the dashboard remains compact.
- [x] Extend repository/controller tests so operator dashboard JSON includes stage timings without raw data.

## Task 3: Browser Progress And Timing Panels

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add TypeScript types for `JobStageProgress`, `JobPipelineProgress`, and `OperatorStageTiming`.
- [x] Add `pipelineProgress` to `LocalizationJob` and `stageTimings` to `OperatorDashboard`.
- [x] Render a selected-job `Pipeline progress` panel near the current timeline panel.
- [x] Show completed/failed/skipped/cache-hit counts, current stage, terminal state, total measured duration, and slowest stage.
- [x] Render an ordered compact stage list with status and duration per stage.
- [x] Keep the existing raw `Timeline` panel visible for detailed event evidence.
- [x] Add operator dashboard stage timing rows for slowest recent stages.
- [x] Add Vitest coverage for a processing job with current stage timing, a completed job with slowest stage, and operator dashboard stage timing output.

## Task 4: Evidence, Diagnostics, And Demo Script Output

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `frontend/src/App.tsx`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh` only if summary output needs a clearer long-run handoff.

**Steps:**

- [x] Add pipeline progress lines to backend Markdown evidence: current stage, terminal flag, total measured duration, slowest stage, and stage counts.
- [x] Add pipeline progress to browser-generated Markdown and JSON evidence export.
- [x] Print `pipelineCurrentStage`, `pipelineTerminal`, `pipelineCompletedStageCount`, `pipelineFailedStageCount`, `pipelineTotalMeasuredDurationMs`, and `pipelineSlowestStage` in `print_job_summary`.
- [x] Print pipeline progress in `print_diagnostics_summary` when present.
- [x] Extend the demo client test fixture to assert pipeline summary output.
- [x] Ensure forbidden-string checks still reject local paths, object keys, tokens, provider payloads, raw transcript text, raw subtitle text, and media bytes.

## Task 5: Documentation, Progress Notes, And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/073-pipeline-progress-stage-timing-demo-mvp.md`

**Steps:**

- [x] Document how to inspect pipeline progress and stage timing in the browser after short, full-video, cache-hit, and OpenAI smoke runs.
- [x] Document that progress and timing are derived from existing timeline events and do not change execution semantics.
- [x] Add smoke checklist items for selected-job progress, operator stage timing, diagnostics JSON, evidence Markdown, and terminal script summaries.
- [x] Record the decision to keep progress advisory and timeline-derived instead of adding a new progress table.
- [x] Mark plan checkboxes as tasks complete.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=JobPipelineProgressServiceTests,LocalizationJobQueryServiceTests,JobEvidenceReportServiceTests,OperatorDashboardRepositoryTests,OperatorDashboardControllerTests test`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh`.
- [x] Run `cd frontend && npm run test:run -- App`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `git diff --check`.
- [x] Optional live validation with local Docker and sample media:

```bash
scripts/demo/start-local-demo.sh
scripts/demo/docker-e2e-success.sh
LINGUAFRAME_DEMO_SAMPLE_PATH=/tmp/linguaframe-demo/tears-demo.mp4 \
  scripts/demo/docker-e2e-success.sh
```

## Done Criteria

- [x] Job detail includes safe `pipelineProgress` derived from timeline events.
- [x] Browser users can see current stage, completed stage count, slowest stage, and per-stage durations without reading raw logs.
- [x] Operator dashboard highlights slow or failing pipeline stages.
- [x] Diagnostics JSON, backend evidence Markdown, browser evidence export, and terminal scripts include the same safe progress/timing summary.
- [x] Existing worker execution, retry, cancellation, cache, OpenAI smoke, and artifact generation behavior is unchanged.
- [x] The feature branch is committed, verified, and merged back to `main`.
