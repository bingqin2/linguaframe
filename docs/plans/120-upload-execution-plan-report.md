# Upload Execution Plan Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a safe, shareable upload execution plan report so the operator can copy or download the full pre-upload decision before storing media, dispatching queues, running FFmpeg, or calling OpenAI.

**Architecture:** Build on the existing `POST /api/media/uploads/execution-plan` response. Add a backend Markdown renderer and download endpoint that produce a metadata-only report from the same validation, cost, readiness, quota, source reuse decision, gates, stages, and commands. Surface the report in the React execution-plan panel and terminal scripts without changing upload creation behavior.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, shell demo scripts.

## Global Constraints

- Keep the report read-only; it must not upload media, create jobs, dispatch queues, run FFmpeg, call providers, or mutate storage.
- Never include raw transcript text, raw subtitle text, media bytes, local media paths, object keys, provider payloads, tokens, or credentials.
- The report may include safe metadata: filename, content type, size, duration, source fingerprint, validation status, cost estimates, source reuse decision, safe API routes, gates, stages, and commands.
- The report must tolerate invalid uploads and missing source reuse candidates.
- Existing JSON execution-plan behavior must remain backward compatible.

---

## Task 1: Backend Markdown Report Renderer

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/UploadExecutionPlanReportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadExecutionPlanReportServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadExecutionPlanReportServiceTests.java`

**Interfaces:**
- `String UploadExecutionPlanReportService.renderMarkdown(UploadExecutionPlanVo plan)` returns a metadata-only Markdown report.

- [x] Render sections: summary, source metadata, validation, cost/time estimate, source reuse decision, gates, stages, commands, and safety notes.
- [x] Include source reuse decision links when present.
- [x] Redact or omit unsafe fields by design; never render object keys or local paths.
- [x] Add tests for a ready plan with reuse links and an invalid plan with no stages.
- [x] Run `mvn -pl LinguaFrame -Dtest=UploadExecutionPlanReportServiceTests test`.

## Task 2: Backend Report Endpoint

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Interfaces:**
- `POST /api/media/uploads/execution-plan/markdown/download` consumes the same multipart form fields as `/execution-plan`.
- Response content type is `text/markdown` and attachment filename is `upload-execution-plan.md`.

- [x] Add controller method that calls `UploadExecutionPlanService.plan(...)` and renders the Markdown with `UploadExecutionPlanReportService`.
- [x] Keep request parameters identical to the JSON execution-plan endpoint.
- [x] Add MockMvc tests that assert content type, attachment header, Markdown headings, source reuse decision, and no raw object-storage/local-path leakage.
- [x] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests#downloadsUploadExecutionPlanMarkdown+estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test`.

## Task 3: Frontend Copy And Download Controls

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `downloadUploadExecutionPlanMarkdown(...)` returns a browser download URL or triggers a fetch-based blob download using the same form data.
- `renderUploadExecutionPlanMarkdown(plan: UploadExecutionPlan)` creates the same safe browser-side copy text for clipboard use.

- [x] Add an API function for the backend Markdown endpoint with the same upload option parameters.
- [x] Add API tests proving multipart fields match JSON execution-plan fields.
- [x] Add `Copy plan` and `Download Markdown` controls to the execution-plan panel.
- [x] Copy text must be generated from loaded safe plan state and include source reuse decision, gates, paid stages, and commands.
- [x] Keep controls disabled while planning or when no plan exists.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [x] Run `npm run build`.

## Task 4: Terminal Script And README

**Files:**
- Modify: `scripts/demo/upload-execution-plan.sh`
- Create: `scripts/demo/upload-execution-plan-report.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `scripts/demo/upload-execution-plan-report.sh` writes `/tmp/linguaframe-demo/upload-execution-plan.md` by default.
- Existing `upload-execution-plan.sh` prints the Markdown report path when `LINGUAFRAME_UPLOAD_EXECUTION_PLAN_MARKDOWN_PATH` is set.

- [x] Add a focused script that posts the selected sample/options to the Markdown endpoint and writes the report.
- [x] Print `uploadExecutionPlanReportPath`, `uploadExecutionPlanReportBytes`, and `uploadExecutionPlanReportStatus=written`.
- [x] Document browser and terminal usage, including that the report is read-only and safe for demo handoff.
- [x] Record validation commands and any failures in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/upload-execution-plan-report.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification And Merge

**Files:**
- Modify: `docs/plans/120-upload-execution-plan-report.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=UploadExecutionPlanReportServiceTests,MediaUploadControllerTests#downloadsUploadExecutionPlanMarkdown+estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/upload-execution-plan-report.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit as `Add upload execution plan report`.
- [x] Merge the feature branch back to `main` after validation passes.
