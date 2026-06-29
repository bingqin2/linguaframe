# Upload Decision Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single pre-upload decision package that exports the full safe evidence needed to decide whether to upload, reuse an existing run, or fix readiness before any storage, queue dispatch, FFmpeg, or OpenAI work.

**Architecture:** Build on the existing upload readiness, owner quota, upload execution plan, source reuse decision, and Markdown execution-plan report. Add a backend aggregate service plus Markdown/ZIP endpoints, expose browser download controls in the execution-plan area, and add a terminal script that writes a package under `/tmp/linguaframe-demo/upload-decision-package/`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, shell demo scripts, ZIP output through Java standard library.

## Global Constraints

- The package is read-only and must not upload media, create jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- The package may include safe metadata only: filename, content type, size, duration, validation status, owner quota state, upload readiness, cost/time estimate, source SHA-256, source reuse decision, safe links, gates, stages, commands, and safety notes.
- The package must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, provider payloads, API keys, demo tokens, bearer tokens, and credentials.
- The package must work for valid uploads, invalid uploads, no source reuse candidates, active duplicate candidates, and completed duplicate candidates.
- Existing upload validation, cost estimate, execution-plan JSON, and execution-plan Markdown behavior must remain backward compatible.

---

## Task 1: Backend Decision Package Model And Renderer

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadDecisionPackageVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/UploadDecisionPackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadDecisionPackageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadDecisionPackageServiceTests.java`

**Interfaces:**
- `UploadDecisionPackageVo build(MultipartFile file, UploadCostEstimateOptionsBo options)` returns the safe aggregate.
- `String renderMarkdown(UploadDecisionPackageVo value)` returns `# Upload Decision Package`.

- [x] Compose `UploadExecutionPlanService.plan(...)`, `OwnerQuotaPreflightService.getPreflight()`, `DemoUploadReadinessService.getReadiness(options.demoProfileId())`, and `UploadExecutionPlanReportService.renderMarkdown(...)`.
- [x] Add package fields: generatedAt, overallStatus, recommendedDecision, executionPlan, ownerQuotaPreflight, uploadReadiness, executionPlanMarkdown, safetyNotes.
- [x] Decide `recommendedDecision`: `BLOCKED` when execution plan, owner quota, or readiness is blocked; `REUSE_COMPLETED_RUN` when source reuse decision recommends a completed run; `WAIT_FOR_ACTIVE_RUN` for active duplicate runs; otherwise `UPLOAD_NEW_SOURCE`.
- [x] Render Markdown sections: summary, owner quota, upload readiness, execution plan summary, source reuse decision, commands, package contents, and safety notes.
- [x] Add tests for ready upload, blocked invalid file, and completed duplicate reuse decision.
- [x] Run `mvn -pl LinguaFrame -Dtest=UploadDecisionPackageServiceTests test`.

## Task 2: Backend Markdown And ZIP Endpoints

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Interfaces:**
- `POST /api/media/uploads/decision-package/markdown/download` consumes the same multipart fields as `/execution-plan`.
- `POST /api/media/uploads/decision-package/download` consumes the same multipart fields and returns `upload-decision-package.zip`.

- [x] Add controller methods using the same request params as `/execution-plan`.
- [x] Markdown response content type is `text/markdown` with attachment filename `upload-decision-package.md`.
- [x] ZIP response content type is `application/zip` with attachment filename `upload-decision-package.zip`.
- [x] ZIP entries must include `manifest.json`, `upload-decision-package.md`, and `upload-execution-plan.md`.
- [x] Controller tests must assert content headers, Markdown headings, ZIP entry names, source reuse fields, and no local path/object-key/token leakage.
- [x] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests#downloadsUploadDecisionPackageMarkdown+downloadsUploadDecisionPackageZip+downloadsUploadExecutionPlanMarkdown test`.

## Task 3: Frontend API And Upload Panel Controls

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- `downloadUploadDecisionPackageMarkdown(...)` returns a `Blob`.
- `downloadUploadDecisionPackageZip(...)` returns a `Blob`.

- [x] Add typed `UploadDecisionPackage` only if the browser renders package metadata; otherwise keep downloads as blob-only functions.
- [x] Add API functions that reuse the same multipart builder as execution plan.
- [x] Add API tests proving target language, profile, style, glossary, subtitle style, and polishing fields are sent.
- [x] Add `Download decision report` and `Download decision ZIP` controls to the execution-plan panel.
- [x] Keep controls disabled while planning/downloading or when no file/plan exists.
- [x] Show download status without blocking validation, upload readiness, or cost estimate panels.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [x] Run `npm run build`.

## Task 4: Terminal Script And Documentation

**Files:**
- Create: `scripts/demo/upload-decision-package.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/upload-decision-package/`.
- Print `uploadDecisionPackageMarkdownPath`, `uploadDecisionPackageZipPath`, `uploadDecisionPackageZipBytes`, and `uploadDecisionPackageStatus=written`.

- [x] Script posts the selected sample/options to both package endpoints.
- [x] Write `upload-decision-package.md` and `upload-decision-package.zip`.
- [x] Document when to use the decision package versus the JSON execution plan, Markdown execution plan, source reuse decision, and full E2E scripts.
- [x] Document that the package is safe for demo handoff and contains no media bytes or secrets.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/upload-decision-package.sh scripts/demo/upload-execution-plan-report.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification And Merge

**Files:**
- Modify: `docs/plans/121-upload-decision-package.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=UploadDecisionPackageServiceTests,MediaUploadControllerTests#downloadsUploadDecisionPackageMarkdown+downloadsUploadDecisionPackageZip+downloadsUploadExecutionPlanMarkdown test`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/upload-decision-package.sh scripts/demo/upload-execution-plan-report.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit as `Add upload decision package`.
- [x] Merge the feature branch back to `main` after validation passes.
