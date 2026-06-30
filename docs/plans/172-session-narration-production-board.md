# Session Narration Production Board Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a session-level narration production board so a demo operator can see which recent jobs have narration ready, blocked, missing, or needing review without opening each job one by one.

**Architecture:** Reuse the existing operator dashboard/session command center patterns and the per-job narration scene board, render review, playback resolution, delivery package, acceptance gate, and artifact metadata. Add one read-only operator aggregate that classifies recent jobs, exposes safe per-job narration links, renders a dense React workbench, and exports a metadata-only terminal/Markdown report.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, CLI script, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, upload APIs, recovery actions, retry/cancel APIs, object-storage write paths, or database mutation paths from this board.
- Do not expose raw transcript text, subtitle text, narration text, reviewer notes, provider payloads, object keys, local paths, tokens, secrets, API keys, or media bytes.
- Keep the UI dense and workbench-like: grouped job rows, compact readiness metrics, status chips, and right-side selected-job details inspired by the existing narration editor direction.
- The board must make progress toward the final demo goal by turning per-job narration tooling into a run-day production overview.

---

## Feature Scope

Build `Session narration production board` as a read-only operator aggregate:

- `GET /api/operator/session-narration-production-board?limit=25`
- `GET /api/operator/session-narration-production-board/markdown/download?limit=25`
- Browser panel in the run-day command center/operator area.
- Terminal script `scripts/demo/session-narration-production-board.sh`.
- Docs and smoke-test checklist updates.

The board classifies recent jobs into:

- `READY_TO_DELIVER`: narration rows, audio/video/render review/playback resolution/delivery evidence are ready.
- `NEEDS_REVIEW`: narration exists but playback review or resolution is incomplete.
- `NEEDS_RENDER`: narration rows exist but audio, narrated video, render review, or delivery package is missing.
- `NEEDS_AUTHORING`: completed localization job exists but narration rows are absent.
- `BLOCKED`: job failed, acceptance gate is blocked, scene board is blocked, or narration evidence is unsafe to deliver.
- `NOT_APPLICABLE`: cancelled, queued, or non-completed jobs that cannot be narrated yet.

Out of scope:

- New narration editing actions.
- New provider calls.
- Automatic rendering or recovery.
- Changing final acceptance semantics.
- Persisting a separate production-board table.

## Backend Design

Create VOs under `com.linguaframe.operator.domain.vo`:

- `SessionNarrationProductionBoardVo`
- `SessionNarrationProductionJobVo`
- `SessionNarrationProductionCheckVo`
- `SessionNarrationProductionActionVo`
- `SessionNarrationProductionLinkVo`

Create `SessionNarrationProductionBoardService` and implementation under `com.linguaframe.operator.service`.

The implementation should:

- Read recent jobs from the existing job summary/query path used by operator dashboard/session recovery surfaces.
- For each recent job, safely compose existing read-only services:
  - `NarrationSceneBoardService#getSceneBoard(jobId)`
  - narration render review service
  - narration playback resolution service
  - narration delivery package service
  - demo acceptance gate service when available
  - artifact metadata for `NARRATION_AUDIO` and `NARRATED_VIDEO`
- Catch per-job read failures and classify that row as `BLOCKED` with a safe error summary instead of failing the entire board.
- Return board-level counts, `overallStatus` (`READY`, `ATTENTION`, `BLOCKED`, `EMPTY`), `recommendedNextAction`, generated time, limit, rows, checks, actions, links, and safety notes.

Per job row fields:

- `jobId`, `videoId`, `jobStatus`, `classification`, `attentionLevel`, `targetLanguage`, `createdAt`, `completedAt`.
- narration counts: segment count, coverage percent, gap count, overlap flag, voice count, mix keyframe count.
- readiness booleans: `sceneBoardReady`, `audioReady`, `videoReady`, `renderReviewReady`, `playbackResolved`, `deliveryReady`, `acceptanceReady`.
- `primaryBlocker`, `recommendedNextAction`, safe links to scene board, workspace, render review, playback review, playback resolution, delivery package, acceptance gate, and stuck recovery when useful.

Markdown export must be metadata-only and include:

- Overall status and counts.
- Grouped job rows by classification.
- Blocker/action summary.
- Safe route inventory.
- Safety notes stating that the board is read-only and metadata-only.

Add endpoints to the existing operator controller:

- `GET /api/operator/session-narration-production-board`
- `GET /api/operator/session-narration-production-board/markdown/download`

## Frontend Design

Extend `frontend/src/domain/jobTypes.ts` with the board types.

Extend `frontend/src/api/linguaframeApi.ts`:

- `getSessionNarrationProductionBoard(limit?: number)`
- `downloadSessionNarrationProductionBoardMarkdown(limit?: number)`

Add the panel near the existing command-center/recovery-board area in `frontend/src/App.tsx`.

The `SessionNarrationProductionBoardPanel` should show:

- Top counters for ready, needs review, needs render, needs authoring, blocked, and not applicable.
- Grouped compact table rows with job id, classification, segment count, coverage, audio/video, playback resolution, delivery, and next action.
- A selected-job inspector with blocker, safe links, and narration readiness checklist.
- Refresh and Markdown download controls.
- Empty state that links the operator to the demo run launcher and per-job narration workspace once a job exists.

The UI must not display narration text. It may show job ids, status labels, counts, percentages, and route labels.

## Terminal Script Design

Add `scripts/demo/session-narration-production-board.sh`.

The script should:

- Accept `LINGUAFRAME_SESSION_NARRATION_BOARD_LIMIT`, default `25`.
- Download JSON and Markdown to `/tmp/linguaframe-demo/session-narration-production-board/`.
- Print stable summary lines:
  - `sessionNarrationProductionStatus`
  - `sessionNarrationProductionReadyCount`
  - `sessionNarrationProductionNeedsReviewCount`
  - `sessionNarrationProductionNeedsRenderCount`
  - `sessionNarrationProductionNeedsAuthoringCount`
  - `sessionNarrationProductionBlockedCount`
  - `sessionNarrationProductionFirstBlockedJobId`
  - `sessionNarrationProductionPrimaryAction`
- Exit non-zero when blocked rows exist unless `LINGUAFRAME_SESSION_NARRATION_BOARD_REPORT_ONLY=true`.
- Never print narration text, reviewer notes, object keys, local paths, provider payloads, tokens, secrets, or media bytes.

Extend `scripts/demo/lib/linguaframe-demo.sh` only for narrow route helpers if existing helpers do not fit.

## Testing

Backend focused tests:

- Add `SessionNarrationProductionBoardServiceTests`.
- Cover mixed recent jobs: ready delivery, needs review, needs render, needs authoring, blocked scene board, failed/non-applicable.
- Assert per-job service failure becomes a blocked row without failing the board.
- Assert Markdown excludes narration text, reviewer notes, object keys, local paths, provider payloads, and tokens.
- Extend operator controller tests for JSON and Markdown routes.
- Extend OpenAPI/runtime route tests if they enumerate operator endpoints.

Frontend focused tests:

- Extend `frontend/src/api/linguaframeApi.test.ts` for JSON and Markdown route encoding plus demo-token header behavior.
- Extend `frontend/src/App.test.tsx` to assert the panel renders counters, grouped rows, selected-job inspector, safe links, refresh, Markdown download, empty state, and error fallback.
- Assert no narration text fixture appears in the board.

Script tests:

- Extend `scripts/demo/test-linguaframe-demo-client.sh` with route helper, summary, failure/report-only, and redaction tests.
- Run shell syntax checks on the new script and touched helper/test scripts.

## Documentation

Update:

- `README.md` run-day narration section.
- `scripts/demo/README.md` with the new command.
- `docs/agent/smoke-test-checklist.md` with browser and terminal checks.
- `docs/product/roadmap.md` Phase 5/8 status.
- `docs/product/target-state.md` Stage 1/user-experience/backend target text.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=SessionNarrationProductionBoardServiceTests,OperatorDashboardControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "session narration production board|session narration"`
- `bash -n scripts/demo/session-narration-production-board.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A demo operator can see narration production readiness across recent jobs without manually opening each job.
- The board clearly distinguishes authoring, render, review, delivery, blocked, and ready states.
- Every recommended action links to an existing safe per-job surface rather than executing work.
- Browser, backend, CLI, Markdown, docs, and tests all expose the same metadata-only feature.
- The feature is committed on its branch and merged back to `main` after verification.

## Implementation Status

- [x] Backend service, VOs, JSON endpoint, and Markdown download endpoint.
- [x] Backend service and operator controller tests with fail-first coverage.
- [x] Frontend API types/helpers and run-day board panel.
- [x] Frontend API and App tests with fail-first coverage.
- [x] Terminal helper functions, metadata-only summary, redaction checks, and `scripts/demo/session-narration-production-board.sh`.
- [x] README, demo README, smoke checklist, roadmap, target-state, and execution-log updates.
- [x] Final full validation.
- [ ] Commit and merge back to `main`.
