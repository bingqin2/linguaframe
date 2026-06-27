# Job Diagnostics Report MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a one-click sanitized diagnostics report for each localization job so demo failures and completed runs can be shared or inspected without manually copying multiple API responses.

**Architecture:** Build a backend diagnostics service over existing read models: job detail, timeline/model-call/usage data, quality evaluation, and artifact metadata. Expose it as a downloadable JSON attachment and add a React link plus demo-script helper. The report is metadata-only and must not include transcript text, subtitle text, object keys, source media paths, provider payloads, credentials, or uploaded bytes.

**Tech Stack:** Spring Boot, Java 21, JUnit 5, MockMvc, React, TypeScript, Vitest, Bash, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- The diagnostics report must be safe to share: no secrets, raw media paths, object storage keys, prompt/request bodies, transcript text, subtitle text, or uploaded media bytes.
- Reuse existing sanitized VOs where possible; do not introduce new persistence tables.
- Keep report generation read-only and on demand.
- Keep repository docs and UI copy in English; discussion can stay Chinese.

---

## Task 1: Backend Diagnostics Report Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobDiagnosticsReportVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobDiagnosticsArtifactVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobQueryService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobQueryServiceTests.java`

**Interfaces:**
- Consumes: `LocalizationJobVo` and `JobArtifactService.listArtifacts(String jobId)`
- Produces: `JobDiagnosticsReportVo` from `LocalizationJobQueryService.getDiagnosticsReport(String jobId)`

- [x] Add `JobDiagnosticsArtifactVo` with artifact id, type, filename, content type, size bytes, content SHA-256, cache-hit flag, source artifact id, and created time.
- [x] Add `JobDiagnosticsReportVo` with `generatedAt`, `job`, `artifacts`, and `artifactCount`.
- [x] Inject `JobArtifactService` into `LocalizationJobQueryServiceImpl` or use the existing repository boundary if it is already available there.
- [x] Implement `getDiagnosticsReport(String jobId)` using existing sanitized job detail plus artifact metadata.
- [x] Add a service test that a completed job report includes job status, timeline events, usage summary, model calls, quality evaluation, artifact count, and artifact hashes.
- [x] Add a service test that the serialized report does not contain source object keys, local media paths, raw transcript text, raw subtitle text, or provider request payloads.
- [x] Run: `mvn -pl LinguaFrame -Dtest=LocalizationJobQueryServiceTests test`.

## Task 2: Backend Diagnostics Download Endpoint

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Consumes: `LocalizationJobQueryService.getDiagnosticsReport(String jobId)`
- Produces: `GET /api/jobs/{jobId}/diagnostics/download`

- [x] Add controller test `downloadsJobDiagnosticsReport`.
- [x] Assert HTTP 200, content type `application/json`, and attachment filename `linguaframe-job-<jobId>-diagnostics.json`.
- [x] Assert JSON includes `job.jobId`, `job.status`, `artifactCount`, `artifacts`, `timelineEvents`, and `modelCalls`.
- [x] Assert JSON excludes raw source object keys, demo access tokens, API keys, and local media paths.
- [x] Implement `downloadDiagnosticsReport` using Jackson serialization and `InputStreamResource` or `byte[]`.
- [x] Run: `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,DemoAccessInterceptorTests' test`.

## Task 3: Frontend Diagnostics Link

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /api/jobs/{jobId}/diagnostics/download`
- Produces: visible `Download diagnostics` link in the selected job detail area

- [x] Add `jobDiagnosticsDownloadUrl(jobId: string): string`.
- [x] Add API test asserting URL encoding for job ids containing spaces or slashes.
- [x] Add a `Download diagnostics` link near the job detail header/actions for any selected job.
- [x] Keep the existing artifact bundle link in the artifacts panel unchanged.
- [x] Add an App test that a failed selected job shows the diagnostics link with the expected href.
- [x] Add compact styling if needed so job actions wrap cleanly on narrow screens.
- [x] Run: `cd frontend && npm run test:run -- linguaframeApi App`.

## Task 4: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-retry.sh`
- Modify: `scripts/demo/docker-e2e-budget-guard.sh`
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: repeatable script evidence that diagnostics JSON is generated and safe

- [x] Add `download_job_diagnostics "$base_url" "$job_id" "$output_path"` to the demo helper library.
- [x] Update success, retry, and budget-guard scripts to download diagnostics JSON for their relevant job.
- [x] Add a Python JSON check that prints report job id, status, artifact count, model-call count, and timeline event count.
- [x] Document the browser link and terminal diagnostics output in README.
- [x] Add smoke-test checklist expectations for diagnostics JSON contents and forbidden sensitive strings.
- [x] Update product docs to list diagnostics export as a demo observability feature.
- [x] Record the decision that diagnostics reports are read-only, generated on demand, and metadata-only.
- [x] Run: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-retry.sh scripts/demo/docker-e2e-budget-guard.sh`.

## Task 5: Verification And Merge

**Files:**
- Modify: `docs/plans/049-job-diagnostics-report-mvp.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='LocalizationJobQueryServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run script syntax validation: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-retry.sh scripts/demo/docker-e2e-budget-guard.sh`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add job diagnostics report`.
- [ ] Merge `job-diagnostics-report-mvp` back to `main`.
- [ ] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- linguaframeApi App`.
