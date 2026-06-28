# Demo Presenter Pack Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a presenter-facing demo pack workspace that turns one selected job plus its same-source run matrix into a safe checklist of recommended runs, evidence downloads, and presentation-ready export metadata.

**Architecture:** Add a read-only backend aggregate under the job API that combines the selected job, demo run matrix, delivery manifests, package routes, quality/cost/cache evidence, and optional same-source baseline comparison links. Render the aggregate in the React selected-job surface, then extend terminal scripts so full-video demos download the same metadata JSON and print a safe summary.

**Tech Stack:** Java 21, Spring Boot, JdbcClient-backed services, JUnit 5/Spring Boot Test, React + Vite + TypeScript, Vitest, Bash + Python helper scripts.

## Global Constraints

- Keep this as one complete feature slice: backend API, tests, frontend UI, scripts, docs, validation, commit, and merge back to `main`.
- Derive presenter pack data from existing job detail, demo run matrix, delivery manifest, comparison routes, and package URLs; do not persist new presenter-session state.
- Keep all presenter pack outputs metadata-only: no transcript text, subtitle text, corrected draft text, provider payloads, object keys, local file paths, source media bytes, or secrets.
- The selected job is the anchor; same-source recommendations come from `DemoRunMatrixService`.
- This feature should make a real demo easier to run and explain, not add marketing-page content.

---

## Task 1: Backend Presenter Pack API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoPresenterPackVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoPresenterPackRunVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoPresenterPackDownloadVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoPresenterPackService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoPresenterPackServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoPresenterPackServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Produces: `GET /api/jobs/{jobId}/demo-presenter-pack`.
- Produces: `DemoPresenterPackVo(anchorJobId, videoId, generatedAt, headline, readinessStatus, recommendedBaselineJobId, bestQualityJobId, lowestCostJobId, runs, downloads, presenterNotesMarkdown)`.
- Produces run rows with role labels: `ANCHOR`, `RECOMMENDED_BASELINE`, `BEST_QUALITY`, `LOWEST_COST`.

- [x] Write failing service tests for pack readiness, role labels, same-source run rows, package/download routes, and metadata redaction.
- [x] Add VO records and `DemoPresenterPackService`.
- [x] Implement service by composing `LocalizationJobQueryService.getJob`, `DemoRunMatrixService.buildMatrix`, and `DeliveryManifestService.getManifest`.
- [x] Generate safe download rows for diagnostics, evidence Markdown, evidence bundle, quality evidence, delivery manifest Markdown, handoff package, demo run package, AI audit package, artifact archive, and source media route when `videoId` is present.
- [x] Add controller endpoint and runtime/OpenAPI route assertions.
- [x] Run `mvn -pl LinguaFrame -Dtest=DemoPresenterPackServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.

## Task 2: Frontend Presenter Pack Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `DemoPresenterPack` from `/api/jobs/{jobId}/demo-presenter-pack`.
- Produces: a selected-job `Demo presenter pack` region with readiness, recommended runs, safe download links, and copy/download presenter notes actions.

- [x] Write failing API test for `getDemoPresenterPack(jobId)` URL encoding.
- [x] Add TypeScript interfaces for `DemoPresenterPack`, `DemoPresenterPackRun`, and `DemoPresenterPackDownload`.
- [x] Implement `linguaFrameApi.getDemoPresenterPack`.
- [x] Write failing App test that opens a selected completed job and verifies presenter pack readiness, baseline/best/lowest roles, and safe evidence links.
- [x] Add presenter pack state, loading/error handling, refresh behavior, and selected-job reset behavior.
- [x] Render `DemoPresenterPackPanel` near the existing demo handoff/session panels so presenters see the export-oriented summary before lower-level artifacts.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "demo presenter pack"`.

## Task 3: Terminal Presenter Pack Evidence

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces: `download_demo_presenter_pack_json BASE_URL JOB_ID OUTPUT_PATH`.
- Produces: `print_demo_presenter_pack_summary_file PACK_JSON_PATH`.
- Full Tears script downloads `demo-presenter-pack.json` for the completed job and prints a metadata-only summary.

- [x] Write failing script tests for encoded presenter-pack route and metadata-only summary redaction.
- [x] Implement JSON download helper and safe Python summary printer.
- [x] Extend full Tears script to download and summarize the presenter pack after the matrix output.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`.

## Task 4: Documentation And Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Documents how to use the browser presenter pack and terminal `demo-presenter-pack.json` to present a complete demo run.

- [x] Document the browser `Demo presenter pack` workflow and terminal `demo-presenter-pack.json` output.
- [x] Record the decision to derive presenter packs on demand from existing evidence APIs instead of storing curated presentation sessions.
- [x] Update execution log with implementation and validation evidence.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on `demo-presenter-pack-workspace`, then merge back to `main` after validation.
