# Demo Run Variance Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a post-run variance report that compares the upload decision package or execution-plan estimate with the completed job's actual safe evidence, closing the loop from pre-upload decision to final demo proof.

**Architecture:** Build a read-only job-scoped aggregate that accepts a completed job id plus optional pre-upload decision package JSON or execution-plan JSON, normalizes estimated cost/time/stages, compares them with job detail usage/model-call/cache/delivery facts, and exports Markdown plus JSON. Surface the report in the selected-job browser workspace and provide a terminal script that writes the evidence under `/tmp/linguaframe-demo/demo-run-variance/`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, shell demo scripts.

## Global Constraints

- The report is read-only and must not upload media, create jobs, retry/cancel jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- The report may include safe metadata only: job id, video id, filename, target language, profile/options, job status, timestamps, estimated cost/time, actual model-call count/cost/latency, cache counts, quality score, delivery readiness, safe links, and variance notes.
- The report must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, corrected subtitle text, provider payloads, API keys, demo tokens, bearer tokens, and credentials.
- The feature must work when no pre-upload estimate is supplied by producing an actual-only report with an explicit missing-baseline note.
- Existing job detail, decision package, execution plan, demo run package, and comparison behavior must remain backward compatible.

---

## Task 1: Backend Variance Domain And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunVarianceReportVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunVarianceMetricVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoRunVarianceReportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunVarianceReportServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoRunVarianceReportServiceTests.java`

**Interfaces:**
- `DemoRunVarianceReportVo build(String jobId, String preUploadJson)` returns safe variance evidence.
- `String renderMarkdown(DemoRunVarianceReportVo report)` returns `# Demo Run Variance Report`.

- [x] Parse optional `preUploadJson` as either `UploadDecisionPackageVo`, `UploadExecutionPlanVo`, or a minimal safe map; invalid JSON produces an `ATTENTION` report note instead of failing.
- [x] Load actual evidence from existing query services: job detail, diagnostics, delivery manifest, acceptance gate, and demo run package links where available.
- [x] Produce metrics for estimated vs actual cost, estimated vs actual model-call count, estimated vs actual runtime seconds, status, source reuse decision, cache hits, quality score, and delivery readiness.
- [x] Classify each metric as `MATCH`, `LOWER_THAN_ESTIMATE`, `HIGHER_THAN_ESTIMATE`, `ACTUAL_ONLY`, or `BASELINE_MISSING`.
- [x] Render Markdown sections: summary, baseline, actual run, variance metrics, delivery evidence, safe links, and safety notes.
- [x] Add service tests for completed job with execution-plan baseline, completed job with no baseline, and invalid baseline JSON.
- [x] Run `mvn -pl LinguaFrame -Dtest=DemoRunVarianceReportServiceTests test`.

## Task 2: Backend JSON And Markdown Endpoints

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/demo-run-variance` accepts optional JSON body `{ "preUploadJson": "..." }` and returns `DemoRunVarianceReportVo`.
- `POST /api/jobs/{jobId}/demo-run-variance/markdown/download` accepts the same body and returns `demo-run-variance.md`.

- [x] Add controller endpoints with auth/demo access behavior matching existing job evidence endpoints.
- [x] Markdown response content type is `text/markdown` and attachment filename is `demo-run-variance.md`.
- [x] Controller tests must assert JSON status, Markdown headings, actual-only missing-baseline note, parsed baseline fields, safe links, and no raw object/local path/token leakage.
- [x] Run `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#returnsDemoRunVarianceReport+downloadsDemoRunVarianceMarkdown+returnsActualOnlyDemoRunVarianceWithoutBaseline test`.

## Task 3: Frontend API And Selected-Job Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- `getDemoRunVariance(jobId: string, preUploadJson?: string): Promise<DemoRunVarianceReport>`.
- `demoRunVarianceMarkdownDownloadUrl(jobId: string)` is not enough because the endpoint is POST; add `downloadDemoRunVarianceMarkdown(jobId, preUploadJson?)`.

- [x] Add TypeScript types for report and metric rows.
- [x] Add API functions and tests for JSON and Markdown POST bodies.
- [x] Add a selected-job `Demo run variance` panel near demo acceptance/completion evidence.
- [x] Let the operator paste pre-upload JSON or leave it blank for actual-only mode.
- [x] Render summary status, metric rows, missing-baseline notes, and safe package links.
- [x] Add `Download variance Markdown` button using the same pasted baseline text.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [x] Run `npm run build`.

## Task 4: Terminal Script And Demo Docs

**Files:**
- Create: `scripts/demo/demo-run-variance.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/demo-run-variance/`.
- Optional baseline input: `LINGUAFRAME_PRE_UPLOAD_JSON_PATH=/tmp/linguaframe-demo/upload-decision-package/manifest.json` or `LINGUAFRAME_PRE_UPLOAD_JSON_INLINE`.
- Print `demoRunVarianceStatus`, `demoRunVarianceMarkdownPath`, `demoRunVarianceJsonPath`, `demoRunVarianceMetricCount`, and `demoRunVarianceBaselineMode`.

- [x] Script posts optional baseline JSON to both endpoints for `LINGUAFRAME_DEMO_JOB_ID`.
- [x] Write `demo-run-variance.json` and `demo-run-variance.md`.
- [x] Document when to use variance report versus acceptance gate, completion certificate, demo run package, job comparison, and upload decision package.
- [x] Document testing with actual-only mode and with a saved pre-upload JSON baseline.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/demo-run-variance.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification And Merge

**Files:**
- Modify: `docs/plans/122-demo-run-variance-report.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=DemoRunVarianceReportServiceTests,LocalizationJobControllerTests#returnsDemoRunVarianceReport+downloadsDemoRunVarianceMarkdown+returnsActualOnlyDemoRunVarianceWithoutBaseline test`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/demo-run-variance.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit as `Add demo run variance report`.
- [x] Merge the feature branch back to `main` after validation passes.
