# Demo Run Evidence Bundle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-click safe ZIP bundle that packages all non-media demo evidence for a localization job.

**Architecture:** Reuse the existing sanitized job diagnostics model and backend Markdown evidence generator. Add a dedicated evidence bundle service that creates an on-demand ZIP containing `evidence.md`, `diagnostics.json`, `manifest.json`, and optional lightweight artifact metadata, without reading object storage bytes or embedding generated media. Expose it through the API, React demo, Docker success script, OpenAPI/runtime route contracts, and docs.

**Tech Stack:** Java 21, Spring Boot MVC, Jackson, `ZipOutputStream`, JUnit 5, React + TypeScript, Vitest, Bash demo scripts.

## Global Constraints

- Do not store evidence bundles as durable artifacts.
- Do not include uploaded media bytes, generated artifact bytes, raw transcript text, raw subtitle text, object storage keys, local paths, provider payloads, credentials, API keys, demo tokens, or raw user media paths.
- Keep the existing artifact ZIP for generated deliverables and diagnostics JSON for machine-readable debugging.
- Keep the evidence bundle deterministic enough for controller and script assertions.
- Complete this slice on `codex-demo-run-evidence-bundle-mvp`, commit, verify, and merge back to `main`.

---

## Approach Decision

Recommended approach: create a separate `GET /api/jobs/{jobId}/evidence/bundle/download` endpoint for a metadata-only ZIP. This keeps the existing artifact archive focused on generated deliverables and gives demos a single shareable proof bundle.

Rejected alternative: add Markdown/diagnostics files into the existing artifact archive. That mixes user-facing media deliverables with audit evidence and would make the artifact archive less clear.

Rejected alternative: only enhance the browser JSON export. That remains browser-state-dependent and does not give scripts or API clients a reproducible backend-authored package.

## Task 1: Backend Evidence Bundle Service - Completed

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredEvidenceBundleBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobEvidenceBundleService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceBundleServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Consumes: `LocalizationJobQueryService#getDiagnosticsReport(String jobId)`
- Consumes: `JobEvidenceReportService#buildMarkdownReport(String jobId)`
- Produces: `StoredEvidenceBundleBo openEvidenceBundle(String jobId)`
- Produces: `GET /api/jobs/{jobId}/evidence/bundle/download`

- [x] Write a failing controller test `downloadsJobEvidenceBundle`.
- [x] Fixture a completed job with one artifact, one timeline event, one model call, and one quality evaluation.
- [x] Assert the response is `application/zip` with filename `linguaframe-job-<jobId>-evidence.zip`.
- [x] Open the ZIP and assert entries:
  - `manifest.json`
  - `evidence.md`
  - `diagnostics.json`
- [x] Assert `manifest.json` includes `jobId`, `videoId`, `status`, `artifactCount`, and entry names.
- [x] Assert ZIP contents exclude `/Users/`, `source-videos/`, `job-artifacts/`, `objectKey`, `demo-access-token`, `sk-`, raw transcript markers, raw subtitle markers, and provider payload markers.
- [x] Implement `StoredEvidenceBundleBo`.
- [x] Implement `JobEvidenceBundleServiceImpl` using `ZipOutputStream` and `ObjectMapper`.
- [x] Add the controller method returning `InputStreamResource`.
- [x] Run the targeted controller test and fix until green.

## Task 2: API Contract and Runtime Freshness - Completed

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Interfaces:**
- Produces required route: `/api/jobs/{jobId}/evidence/bundle/download`

- [x] Add a failing OpenAPI path assertion for the evidence bundle endpoint.
- [x] Add the new route to backend runtime required routes.
- [x] Update runtime dependency controller tests.
- [x] Update private-demo preflight required routes.
- [x] Run targeted backend route-contract tests and fix until green.

## Task 3: React Demo Download Entry - Completed

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces: `jobEvidenceBundleDownloadUrl(jobId: string): string`
- Consumes: `DemoEvidence.links.evidenceBundle`

- [x] Add a failing API helper test for URL encoding.
- [x] Add a failing App test asserting `Download evidence bundle` appears in the `Demo evidence` panel and points to `/api/jobs/<jobId>/evidence/bundle/download`.
- [x] Implement `jobEvidenceBundleDownloadUrl`.
- [x] Add `evidenceBundle` to `DemoEvidence.links`.
- [x] Add the link in `DemoEvidencePanel`.
- [x] Include `Backend evidence bundle` in the browser Markdown preview.
- [x] Run frontend targeted tests and fix until green.

## Task 4: Docker Demo Script Evidence - Completed

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`

**Interfaces:**
- Produces: `/tmp/linguaframe-demo/job-evidence.zip`

- [x] Add `download_job_evidence_bundle`.
- [x] Add `print_evidence_bundle_summary` that checks ZIP entries and forbidden strings.
- [x] Extend `docker-e2e-success.sh` to download and validate `job-evidence.zip`.
- [x] Print the bundle path and entry count.
- [x] Run `bash -n` on modified scripts.

## Task 5: Documentation and Progress - Completed

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/065-demo-run-evidence-bundle-mvp.md`

- [x] Document the difference between result bundle, diagnostics JSON, backend Markdown evidence, and evidence bundle ZIP.
- [x] Update Docker demo output docs to include `/tmp/linguaframe-demo/job-evidence.zip`.
- [x] Add smoke checklist assertions for the browser link and script output.
- [x] Record the design decision: evidence bundle is generated on demand and metadata-only.
- [x] Record RED/GREEN and final validation evidence.
- [x] Mark plan tasks and done criteria as completed after implementation.

## Verification - Completed

- [x] `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- [x] `mvn -pl LinguaFrame test`
- [x] `cd frontend && npm run test:run -- App linguaFrameApi`
- [x] `cd frontend && npm run build`
- [x] `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh`
- [x] `docker compose --env-file .env.example config --quiet`
- [x] `git diff --check`

## Done Criteria

- [x] API clients can download a metadata-only evidence ZIP for an existing job.
- [x] The ZIP contains Markdown evidence, diagnostics JSON, and a safe manifest.
- [x] The React demo exposes one-click evidence bundle download.
- [x] Docker success demo downloads and validates `job-evidence.zip`.
- [x] Tests and docs cover safety rules and usage.
- [ ] The feature branch is committed, verified, and merged back to `main`.
