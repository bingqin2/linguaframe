# Live Demo Run Monitor Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live, metadata-only demo run monitor so the owner can watch a selected localization job from browser or terminal, see current stage timing, identify slow/stuck work, and know the next safe action without reading backend logs.

**Architecture:** Build on existing job detail, timeline events, pipeline progress, diagnostics links, and SSE refresh behavior. The monitor should derive state from persisted job metadata and existing safe routes; it must not persist new provider payloads or expose transcript text, subtitle text, object keys, raw media paths, credentials, or demo tokens.

**Tech Stack:** Spring Boot services/controllers, JUnit 5 tests, React + TypeScript + Vitest, Bash demo helpers, Markdown docs.

## Global Constraints

- This slice must be a complete feature: backend API, Markdown download, browser panel, terminal watch/export script, docs, validation, commit, and merge back to `main`.
- Keep existing job detail, pipeline progress, presenter pack, share sheet, and full-video demo behavior backward compatible.
- Require the same owner-scoped access boundary as other job detail and evidence endpoints.
- Treat monitor output as observability only. It must never retry, cancel, dispatch, upload, or call providers.
- Use deterministic clock-aware tests for elapsed/stuck calculations.

---

## Current Context

- Job detail already returns timeline events and timeline-derived pipeline progress.
- The browser selected-job view already refreshes selected jobs through SSE and exposes share/evidence panels.
- Terminal demo helpers already download safe job evidence, matrices, presenter packs, and share sheets, but there is no live monitor command for an in-flight job.
- The target state explicitly calls for current stage, slowest stage, and timing evidence without reading backend logs.

## Task 1: Backend Run Monitor Contract

**Files:**
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunMonitorVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunMonitorStageVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunMonitorLinkVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoRunMonitorService.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunMonitorServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Add/modify focused backend tests under `LinguaFrame/src/test/java/com/linguaframe/job/`

**Interfaces:**
- Add `GET /api/jobs/{jobId}/demo-run-monitor`.
- Add `GET /api/jobs/{jobId}/demo-run-monitor/markdown/download`.
- Return safe fields: job id, video id, status, dispatch status, generated time, elapsed time, current stage, completed/total stage counts, slowest completed stage, attention level, headline summary, recommended next action, stage rows, and curated safe links.
- Mark attention for failed/cancelled jobs, terminal completed jobs, jobs with no progress after dispatch, and active stages that exceed a conservative stuck threshold.

- [x] Write failing service/controller tests for queued, processing, completed, failed, and stale in-flight monitor states.
- [x] Implement VO records, service derivation, controller routes, and Markdown rendering.
- [x] Run focused backend tests for run monitor and owner access.

## Task 2: Browser Run Monitor Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify/add focused frontend API tests if needed.

**Interfaces:**
- Add a selected-job `Demo run monitor` panel near status/timeline/progress.
- Show attention level, current stage, elapsed time, completed stage count, slowest stage, next action, and compact stage rows.
- Add explicit refresh and backend Markdown download actions.
- Refresh monitor data when a job is opened, uploaded, retried, cancelled, refreshed manually, and when SSE delivers job updates.

- [x] Write failing Vitest coverage for monitor rendering, attention state, refresh, and download link.
- [x] Implement frontend API types and selected-job panel.
- [x] Run focused frontend tests.

## Task 3: Terminal Watch And Export Script

**Files:**
- Add: `scripts/demo/demo-run-monitor.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-monitor.sh` downloads JSON and Markdown to `/tmp/linguaframe-demo/demo-run-monitor/`.
- `LINGUAFRAME_DEMO_RUN_MONITOR_WATCH=true` polls until terminal status or attempt limit, printing metadata-only monitor summary lines.
- The full Tears demo should export the final monitor artifacts after completion.
- Script output must fail clearly when the job id is missing or access is denied.

- [x] Add failing shell helper tests for JSON download, Markdown download, summary printing, and watch termination.
- [x] Implement shared helpers and the monitor script.
- [x] Integrate final monitor export into the full Tears demo path.

## Task 4: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/107-live-demo-run-monitor-workspace.md`

**Validation Commands:**
- `mvn -pl LinguaFrame -Dtest=DemoRunMonitorServiceTests,LocalizationJobControllerTests,AuthenticatedOwnerJobAccessTests test`
- `cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/demo-run-monitor.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `git diff --check`

- [x] Document browser and terminal monitor usage.
- [x] Record validation evidence in the execution log.
- [ ] Commit the completed feature branch, merge back to `main`, run post-merge focused validation, and record the merge.
