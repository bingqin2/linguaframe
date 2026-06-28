# Demo Run Matrix Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a backend-backed demo run matrix that summarizes multiple completed profile runs for the same source video and exposes it in the browser, terminal scripts, and docs.

**Architecture:** Add a read-only backend service under the job API that selects recent jobs for one source video, enriches them with existing job detail and delivery manifest data, and returns a metadata-only matrix. Reuse the React job history and selected job context to render a `Demo run matrix` panel, and extend demo scripts with JSON/Markdown download plus safe summary printing.

**Tech Stack:** Java 21, Spring Boot, JdbcClient repositories, JUnit 5/Spring Boot Test, React + Vite + TypeScript, Vitest, Bash + Python helper scripts.

## Global Constraints

- Keep this as one complete feature slice: backend API, tests, frontend UI, scripts, docs, and validation.
- Do not store new matrix state; derive it from existing jobs, model calls, quality evaluation, cache summaries, delivery manifest, and profile metadata.
- Keep all matrix outputs metadata-only: no transcript text, subtitle text, provider payloads, object keys, local file paths, or secrets.
- Use the selected job as the matrix anchor and group by `videoId`.
- Merge the verified feature branch back to `main` after completion.

---

## Task 1: Backend Matrix API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunMatrixJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunMatrixVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoRunMatrixService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunMatrixServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoRunMatrixServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Produces: `GET /api/jobs/{jobId}/demo-run-matrix?limit=8`.
- Produces: `DemoRunMatrixVo(anchorJobId, videoId, generatedAt, jobs, recommendedBaselineJobId, bestQualityJobId, lowestCostJobId)`.
- Produces repository method `findSummariesByVideoId(String videoId, int limit)`.

- [x] Write failing service tests for same-video grouping, completed-job preference, quality/cost/cache fields, and matrix recommendation ids.
- [x] Add VO records and `DemoRunMatrixService`.
- [x] Add repository query ordered by `created_at DESC, id DESC`, capped by normalized limit.
- [x] Implement service using `LocalizationJobQueryService.getJob`, `DeliveryManifestService.getManifest`, and `Clock`.
- [x] Add controller endpoint and runtime/OpenAPI route assertions.
- [x] Run `mvn -pl LinguaFrame -Dtest=DemoRunMatrixServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.

## Task 2: Frontend Matrix Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `DemoRunMatrix` from `/api/jobs/{jobId}/demo-run-matrix`.
- Produces: a selected-job `Demo run matrix` region with profile, status, quality, cost, model calls, cache hits, handoff readiness, and best/lowest-cost markers.

- [x] Write failing API test for `getDemoRunMatrix(jobId, limit)` URL encoding and query params.
- [x] Add TypeScript `DemoRunMatrix` and `DemoRunMatrixJob` interfaces.
- [x] Implement `linguaFrameApi.getDemoRunMatrix`.
- [x] Write failing App test that opens a selected job, waits for `Demo run matrix`, and verifies profile rows plus recommended markers.
- [x] Add matrix state, loading/error handling, reload button, and selected-job reset behavior.
- [x] Render `DemoRunMatrixPanel` before the two-job comparison panel so demo viewers see the broader evidence first.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "demo run matrix"`.

## Task 3: Terminal Demo Matrix Evidence

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces: `download_demo_run_matrix_json BASE_URL JOB_ID OUTPUT_PATH`.
- Produces: `print_demo_run_matrix_summary_file MATRIX_JSON_PATH`.
- Full Tears script downloads `demo-run-matrix.json` for the completed job and prints a safe summary.

- [x] Write failing script tests for encoded matrix route and metadata-only summary redaction.
- [x] Implement JSON download helper and safe Python summary printer.
- [x] Extend full Tears script to download and summarize the matrix after job completion.
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
- Documents how to use the browser matrix and terminal matrix output to present several demo profile runs.

- [x] Document the browser `Demo run matrix` workflow and the terminal `demo-run-matrix.json` output.
- [x] Record the decision to derive the matrix on demand instead of storing a demo-session table.
- [x] Update execution log with implementation and validation evidence.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on `demo-run-matrix-workspace`, then merge back to `main` after validation.
