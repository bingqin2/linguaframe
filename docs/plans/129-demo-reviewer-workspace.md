# Demo Reviewer Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a single reviewer-facing workspace for a completed demo job that summarizes whether the run is ready to share and exports one safe review package.

**Architecture:** Compose a read-only job aggregate from existing evidence surfaces instead of duplicating media or raw content. The backend exposes JSON, Markdown, and ZIP downloads; the React selected-job view shows one consolidated review panel; terminal scripts export the same reviewer package for local demos.

**Tech Stack:** Java 21, Spring Boot MVC, existing job evidence services, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This feature must be read-only: it must not upload media, create jobs, retry jobs, dispatch queues, call OpenAI, edit `.env`, run Docker, or download generated media bytes.
- Output must exclude API keys, bearer tokens, demo tokens, object keys, local media paths, raw transcript text, raw subtitle text, provider payloads, and media bytes.
- The reviewer workspace should use existing evidence sources where available: acceptance gate, completion certificate, delivery manifest, OpenAI smoke proof, model usage summaries, demo run package links, AI audit package links, handoff links, and diagnostics/evidence links.
- The feature should be useful for both deterministic demo runs and OpenAI-backed runs; missing OpenAI evidence should be `ATTENTION`, not `BLOCKED`, unless failed OpenAI calls exist.
- Every implementation task must include focused backend/frontend/script validation and update the progress log.

---

## Task 1: Backend Reviewer Workspace Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoReviewerWorkspaceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoReviewerWorkspaceSectionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoReviewerWorkspaceCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoReviewerWorkspaceLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoReviewerWorkspaceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoReviewerWorkspaceServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoReviewerWorkspaceServiceTests.java`

**Interfaces:**
- `DemoReviewerWorkspaceVo getWorkspace(String jobId)` returns `jobId`, `overallStatus`, `phase`, `recommendedNextAction`, `completedAt`, `targetLanguage`, sections, checks, safe links, package entries, and safety notes.
- `String renderMarkdown(String jobId)` returns the same workspace as metadata-only Markdown.

- [x] Compose workspace from existing job detail, acceptance gate, completion certificate, delivery manifest, and OpenAI smoke proof services.
- [x] Add required checks for terminal completed status, transcript/subtitle artifacts, delivery links, diagnostics/evidence links, and package availability.
- [x] Add optional checks for quality evaluation, TTS/dubbed media, reviewed subtitle handoff, OpenAI smoke proof, AI audit package, and demo run package.
- [x] Derive `READY`, `ATTENTION`, or `BLOCKED` from required/optional checks with deterministic rules.
- [x] Render Markdown with a reviewer summary, checklist, safe links, package inventory, and safety notes.
- [x] Add tests for `READY`, `ATTENTION`, `BLOCKED`, missing job propagation, and unsafe marker exclusion.
- [x] Run `mvn -pl LinguaFrame test -Dtest=DemoReviewerWorkspaceServiceTests`.

## Task 2: Job API Endpoints And Runtime Contract

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/demo-reviewer-workspace`
- `GET /api/jobs/{jobId}/demo-reviewer-workspace/markdown/download`
- `GET /api/jobs/{jobId}/demo-reviewer-workspace/download`

- [x] Add JSON, Markdown, and ZIP endpoints beside existing selected-job evidence routes.
- [x] ZIP should include `manifest.json`, `reviewer-workspace.md`, and safety README only; it should link to existing packages instead of embedding media artifacts.
- [x] Add the new routes to the runtime required-route contract.
- [x] Add MockMvc tests for JSON status, Markdown headers, ZIP entries, auth behavior, route contract, and unsafe marker exclusion.
- [x] Run `mvn -pl LinguaFrame test -Dtest=DemoReviewerWorkspaceServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`.

## Task 3: React Selected-Job Reviewer Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getDemoReviewerWorkspace(jobId: string): Promise<DemoReviewerWorkspace>`
- `downloadDemoReviewerWorkspaceMarkdown(jobId: string): Promise<Blob>`
- `downloadDemoReviewerWorkspaceZip(jobId: string): Promise<Blob>`

- [x] Add TypeScript types matching the backend reviewer workspace.
- [x] Add API helpers using the existing owner-session token and bearer-header behavior.
- [x] Load the workspace whenever a selected job is loaded, refreshed, retried, cancelled, selected from history, or updated through SSE/polling.
- [x] Add a `Demo reviewer workspace` panel near the top of selected-job evidence with status, phase, required checks, optional checks, safe package links, and Markdown/ZIP download actions.
- [x] Keep selected-job details usable if reviewer workspace loading fails.
- [x] Add Vitest coverage for API paths, panel rendering, failure state, Markdown/ZIP downloads, and unsafe marker absence.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.

## Task 4: Terminal Script, Docker Export, And Docs

**Files:**
- Create: `scripts/demo/demo-reviewer-workspace.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-reviewer-workspace.sh`
- Default output directory: `/tmp/linguaframe-demo/demo-reviewer-workspace/`.

- [x] Add helper functions for reviewer workspace JSON, Markdown, and ZIP downloads.
- [x] Implement a script that writes `demo-reviewer-workspace.json`, `.md`, and `.zip`, prints metadata-only summary keys, and exits non-zero on `BLOCKED` unless `LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_REPORT_ONLY=true`.
- [x] Extend success/OpenAI/full-video E2E scripts to export reviewer workspace files after job completion.
- [x] Document when to use reviewer workspace versus acceptance gate, completion certificate, OpenAI smoke proof, closure package, and session evidence package.
- [x] Update smoke checklist with expected browser panel, terminal script, ZIP entries, and safety exclusions.
- [x] Run `bash -n scripts/demo/demo-reviewer-workspace.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/129-demo-reviewer-workspace.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=DemoReviewerWorkspaceServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/demo-reviewer-workspace.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run full safety checks:
  `mvn -pl LinguaFrame test`
  `npm test -- --run`
  `git diff --check`
- [x] Commit as `Add demo reviewer workspace`.
- [x] Merge the feature branch back to `main` after validation passes.
