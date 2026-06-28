# Delivery Handoff Manifest MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Provide a safe, downloadable delivery manifest that explains a completed job's final handoff artifacts, reviewed outputs, evidence links, hashes, and demo verification status.

**Architecture:** Add a backend manifest service that derives safe metadata from existing job detail, artifacts, diagnostics, and reviewed subtitle artifacts without reading media bytes or subtitle text. Expose the manifest as JSON and Markdown downloads, render it in the React selected-job view, and include it in terminal demo output so a demo reviewer can understand the handoff package from browser or CLI.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, MockMvc, React, TypeScript, Vitest, existing job query/artifact/diagnostics/evidence services, Bash demo helpers.

## Global Constraints

- This is one complete user-visible feature slice: backend API, browser panel, scripts, docs, tests, validation, commit, and merge.
- Do not add new provider calls, TTS regeneration, burn-in regeneration, retries, or job status transitions.
- Do not expose raw transcript text, generated subtitle text, corrected draft text, object keys, local paths, tokens, credentials, provider payloads, or media bytes.
- The manifest must work before reviewed artifacts exist, but it should clearly mark reviewed delivery as incomplete.
- The manifest should prefer reviewed artifacts as handoff outputs when present, while preserving generated artifacts as audit evidence.
- Keep generated artifacts, reviewed artifacts, diagnostics, evidence bundles, and result archives as separate concepts.

---

## Task 1: Backend Delivery Manifest Model And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DeliveryManifestVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DeliveryManifestArtifactVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DeliveryManifestLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DeliveryManifestService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DeliveryManifestServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DeliveryManifestServiceTests.java`

**Interfaces:**
- Consumes: `LocalizationJobQueryService.getJob(jobId)`, `JobArtifactService.listArtifacts(jobId)`.
- Produces: `DeliveryManifestService.buildManifest(String jobId)` returning `DeliveryManifestVo`.

**Steps:**

- [x] Add `DeliveryManifestArtifactVo` with `artifactId`, `type`, `filename`, `contentType`, `sizeBytes`, `shortSha256`, `cacheState`, `role`, and `downloadUrl`.
- [x] Add `DeliveryManifestLinkVo` with `label`, `kind`, and `url`.
- [x] Add `DeliveryManifestVo` with `jobId`, `videoId`, `targetLanguage`, `status`, `generatedAt`, `handoffReady`, `reviewedSubtitleArtifactCount`, `reviewedBurnedVideoAvailable`, `generatedArtifactCount`, `reviewedArtifacts`, `auditArtifacts`, and `links`.
- [x] Classify `REVIEWED_SUBTITLE_JSON`, `REVIEWED_SUBTITLE_SRT`, `REVIEWED_SUBTITLE_VTT`, and `REVIEWED_BURNED_VIDEO` as reviewed handoff artifacts.
- [x] Classify generated transcript/subtitle/audio/video/worker artifacts as audit artifacts.
- [x] Set `handoffReady=true` only when reviewed JSON, SRT, and VTT are all present.
- [x] Include safe links for result bundle, diagnostics JSON, backend evidence Markdown, evidence bundle, and each artifact download.
- [x] Test completed job with reviewed artifacts returns `handoffReady=true`.
- [x] Test completed job without reviewed artifacts returns `handoffReady=false` and still includes audit artifacts.
- [x] Test no raw subtitle or object-storage fields are present in serialized manifest JSON.

## Task 2: Manifest API And Markdown Download

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`

**Interfaces:**
- Produces: `GET /api/jobs/{jobId}/delivery-manifest`
- Produces: `GET /api/jobs/{jobId}/delivery-manifest/markdown/download`

**Steps:**

- [x] Add `GET /api/jobs/{jobId}/delivery-manifest` returning `DeliveryManifestVo`.
- [x] Add `GET /api/jobs/{jobId}/delivery-manifest/markdown/download` returning `text/markdown`.
- [x] Generate Markdown from the same manifest data with sections for handoff readiness, reviewed outputs, audit artifacts, and verification links.
- [x] Add OpenAPI path assertions for both endpoints.
- [x] Add both routes to runtime required route metadata so private-demo preflight catches stale backend containers.
- [x] Test JSON endpoint includes reviewed artifact download links.
- [x] Test Markdown endpoint includes reviewed artifact filenames and evidence links.
- [x] Test Markdown output excludes raw transcript, raw subtitles, object keys, local paths, tokens, and provider payloads.

## Task 3: Browser Delivery Handoff Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `linguaFrameApi.getDeliveryManifest(jobId)`.
- Produces: selected-job `Delivery handoff` panel.

**Steps:**

- [x] Add TypeScript types matching `DeliveryManifestVo`, `DeliveryManifestArtifactVo`, and `DeliveryManifestLinkVo`.
- [x] Add API helpers `getDeliveryManifest(jobId)` and `deliveryManifestMarkdownDownloadUrl(jobId)`.
- [x] Load the manifest when a selected job is opened and after reviewed subtitle publish succeeds.
- [x] Render a `Delivery handoff` panel near `Result delivery`.
- [x] Show `Ready for handoff` when `handoffReady=true`; otherwise show `Needs reviewed subtitle publish`.
- [x] List reviewed handoff artifacts first, then audit artifacts.
- [x] Add direct links for manifest Markdown, result bundle, diagnostics, evidence Markdown, evidence bundle, and artifact downloads.
- [x] Show a panel-level error if manifest loading fails without breaking transcript, artifacts, or draft editing.
- [x] Test the API helper encodes job IDs and sends the demo access token header.
- [x] Test the panel shows ready/incomplete states and safe download links.
- [x] Test publishing reviewed subtitles refreshes the handoff panel.

## Task 4: Terminal Demo Manifest Evidence

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Interfaces:**
- Consumes: `/api/jobs/{jobId}/delivery-manifest`.
- Produces: metadata-only terminal summary and downloaded Markdown manifest.

**Steps:**

- [x] Add `get_delivery_manifest(baseUrl, jobId)`.
- [x] Add `download_delivery_manifest_markdown(baseUrl, jobId, outputPath)`.
- [x] Add `print_delivery_manifest_summary` that prints job id, handoff readiness, reviewed artifact count, reviewed burned video availability, generated artifact count, and link count.
- [x] Update `docker-e2e-success.sh` to print the manifest summary after reviewed subtitle publish.
- [x] Download Markdown manifest to `/tmp/linguaframe-demo/delivery-manifest.md`.
- [x] Add client tests proving manifest summary excludes raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- [x] Add the new required routes to private-demo preflight route checks.

## Task 5: Documentation, Plan Tracking, And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/077-delivery-handoff-manifest-mvp.md`

**Steps:**

- [x] Document the delivery manifest as the single safe handoff checklist for a completed job.
- [x] Clarify that the delivery manifest references artifacts and evidence; it does not embed media bytes or raw subtitle text.
- [x] Update the deterministic Docker E2E expected output with delivery manifest summary lines and downloaded Markdown path.
- [x] Mark the roadmap React demo experience with delivery handoff manifest status.
- [x] Record the decision that handoff manifests are derived on demand from existing durable job/artifact state.
- [x] Mark all plan tasks complete after implementation and validation.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=DeliveryManifestServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh`.
- [x] Run `git diff --check`.
- [x] Post-merge: rerun backend focused tests, frontend focused tests, frontend build, demo client tests, and `git diff --check` on `main`.

## Done Criteria

- [x] A browser user can open a selected job and see whether it is ready for handoff.
- [x] Reviewed artifacts are clearly separated from generated audit artifacts.
- [x] JSON and Markdown delivery manifests are downloadable from the backend.
- [x] Terminal E2E output prints delivery manifest metadata and downloads a Markdown manifest.
- [x] Private-demo preflight catches stale backend containers missing the manifest routes.
- [x] Manifest surfaces no raw transcript, generated subtitle, corrected subtitle, object key, local path, token, credential, provider payload, or media byte content.
- [x] Tests, docs, validation, commit, and merge back to `main` are completed as part of this feature slice.
