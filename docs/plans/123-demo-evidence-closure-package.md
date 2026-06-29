# Demo Evidence Closure Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a final read-only demo evidence closure package that ties pre-upload decision evidence, post-run variance, acceptance, completion, and safe delivery links into one reviewer-facing JSON/Markdown/ZIP artifact.

**Architecture:** Build a job-scoped backend aggregate that accepts an optional pre-upload baseline JSON, reuses existing safe services for variance, acceptance gate, completion certificate, presenter/share/snapshot links, and returns a sanitized closure manifest. Add JSON, Markdown, and ZIP endpoints, surface the package in the selected-job frontend, and add a terminal script that writes the closure evidence under `/tmp/linguaframe-demo/demo-evidence-closure/`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, shell demo scripts, `ZipOutputStream`.

## Global Constraints

- The closure package is read-only and must not upload media, create jobs, retry/cancel jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- The package may include only safe metadata: job ids, video id, target language, demo profile/options, statuses, timestamps, cost/time summaries, model-call counts, cache counts, quality score, readiness decisions, safe links, and operator next actions.
- The package must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, corrected subtitle text, provider payloads, API keys, bearer tokens, demo tokens, and credentials.
- The package must work without a pre-upload baseline by embedding an actual-only variance report and an explicit missing-baseline note.
- Existing variance, acceptance gate, completion certificate, share sheet, snapshot, presenter pack, and demo run package behavior must remain backward compatible.

---

## Task 1: Backend Closure Domain And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoEvidenceClosureSectionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoEvidenceClosurePackageVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredDemoEvidenceClosurePackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoEvidenceClosurePackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoEvidenceClosurePackageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoEvidenceClosurePackageServiceTests.java`

**Interfaces:**
- `DemoEvidenceClosurePackageVo buildClosure(String jobId, String preUploadJson)` returns safe closure metadata.
- `String renderMarkdown(DemoEvidenceClosurePackageVo closure)` returns Markdown beginning with `# Demo Evidence Closure Package`.
- `StoredDemoEvidenceClosurePackageBo openClosurePackage(String jobId, String preUploadJson)` returns a ZIP containing `manifest.json`, `demo-evidence-closure.md`, and `demo-run-variance.md`.

- [ ] Add VO records with stable fields: `jobId`, `videoId`, `generatedAt`, `closureStatus`, `baselineMode`, `jobStatus`, `targetLanguage`, `demoProfileId`, `recommendedNextAction`, `varianceReport`, `sections`, `safeLinks`, and `safetyNotes`.
- [ ] Inject existing safe services: `DemoRunVarianceReportService`, `DemoAcceptanceGateService`, `DemoCompletionCertificateService`, and `ObjectMapper`.
- [ ] Build closure sections for `PRE_UPLOAD_BASELINE`, `POST_RUN_VARIANCE`, `ACCEPTANCE_GATE`, `COMPLETION_CERTIFICATE`, `DELIVERY_PACKAGE`, and `REVIEWER_HANDOFF`.
- [ ] Classify closure status as `READY` only when the variance report is `READY`, acceptance gate is `READY`, and completion certificate is `READY`; use `BLOCKED` if any source reports `BLOCKED`; otherwise use `ATTENTION`.
- [ ] Render Markdown sections: `Summary`, `Baseline`, `Post-Run Variance`, `Acceptance`, `Completion`, `Safe Links`, and `Safety Notes`.
- [ ] Build ZIP entries without touching object storage: `manifest.json`, `demo-evidence-closure.md`, `demo-run-variance.md`, and `README.md`.
- [ ] Add service tests for completed READY closure with execution-plan baseline, actual-only closure without baseline, and attention closure when variance has invalid baseline JSON.
- [ ] Run `mvn -pl LinguaFrame -Dtest=DemoEvidenceClosurePackageServiceTests test`.

## Task 2: Backend JSON, Markdown, And ZIP Endpoints

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/demo-evidence-closure` accepts optional `{ "preUploadJson": "..." }` and returns `DemoEvidenceClosurePackageVo`.
- `POST /api/jobs/{jobId}/demo-evidence-closure/markdown/download` returns `demo-evidence-closure.md`.
- `POST /api/jobs/{jobId}/demo-evidence-closure/download` returns `linguaframe-job-{jobId}-demo-evidence-closure.zip`.

- [ ] Add controller request record for closure baseline JSON; reuse the variance request shape where practical without exposing it publicly.
- [ ] Add the JSON endpoint with demo-token behavior inherited from existing `/api/jobs/**` endpoints.
- [ ] Add Markdown response with `text/markdown;charset=UTF-8` and attachment filename `demo-evidence-closure.md`.
- [ ] Add ZIP response with service-provided content type, length, and filename.
- [ ] Add controller tests: `returnsDemoEvidenceClosurePackage`, `downloadsDemoEvidenceClosureMarkdown`, and `downloadsDemoEvidenceClosureZip`.
- [ ] Assert tests cover status, baseline mode, embedded variance fields, acceptance/completion sections, ZIP entries, safe links, and absence of raw object keys, local paths, provider payloads, and token-like strings.
- [ ] Run `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#returnsDemoEvidenceClosurePackage+downloadsDemoEvidenceClosureMarkdown+downloadsDemoEvidenceClosureZip test`.

## Task 3: Frontend API And Selected-Job Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- `getDemoEvidenceClosure(jobId: string, preUploadJson?: string): Promise<DemoEvidenceClosurePackage>`.
- `downloadDemoEvidenceClosureMarkdown(jobId: string, preUploadJson?: string): Promise<Blob>`.
- `downloadDemoEvidenceClosureZip(jobId: string, preUploadJson?: string): Promise<Blob>`.

- [ ] Add TypeScript types for closure package and closure sections.
- [ ] Add API functions with POST JSON bodies and encoded job ids.
- [ ] Add API tests for JSON, Markdown, and ZIP requests, including demo-token header propagation.
- [ ] Add a `Demo evidence closure` panel near the variance, acceptance, and completion panels.
- [ ] Let the operator paste pre-upload baseline JSON or leave it blank, then build JSON, download Markdown, or download ZIP using the same baseline text.
- [ ] Render closure status, baseline mode, recommended next action, section statuses, embedded variance status, safe links, and safety notes.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [ ] Run `npm run build`.

## Task 4: Terminal Script, README, And Progress Log

**Files:**
- Create: `scripts/demo/demo-evidence-closure.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/demo-evidence-closure/`.
- Optional baseline input: `LINGUAFRAME_PRE_UPLOAD_JSON_PATH` or `LINGUAFRAME_PRE_UPLOAD_JSON_INLINE`.
- Required job id: `LINGUAFRAME_DEMO_JOB_ID`.
- Script output keys: `demoEvidenceClosureStatus`, `demoEvidenceClosureBaselineMode`, `demoEvidenceClosureJsonPath`, `demoEvidenceClosureMarkdownPath`, `demoEvidenceClosureZipPath`, and `demoEvidenceClosureSectionCount`.

- [ ] Implement the script with `demo_curl`, `python3`, safe request JSON generation, and separate JSON/Markdown/ZIP downloads.
- [ ] Document when to use closure package versus variance report, acceptance gate, completion certificate, snapshot, demo run package, and upload decision package.
- [ ] Document actual-only testing and baseline-path testing using a saved upload decision package manifest.
- [ ] Record focused validation commands and outcomes in `docs/progress/execution-log.md`.
- [ ] Run `bash -n scripts/demo/demo-evidence-closure.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/123-demo-evidence-closure-package.md`

- [ ] Mark this plan checklist complete after implementation.
- [ ] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=DemoEvidenceClosurePackageServiceTests,LocalizationJobControllerTests#returnsDemoEvidenceClosurePackage+downloadsDemoEvidenceClosureMarkdown+downloadsDemoEvidenceClosureZip test`
- [ ] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/demo-evidence-closure.sh scripts/demo/lib/linguaframe-demo.sh`
- [ ] Run `git diff --check`.
- [ ] Commit as `Add demo evidence closure package`.
- [ ] Merge the feature branch back to `main` after validation passes.
