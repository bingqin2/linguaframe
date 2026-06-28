# Private Demo Evidence Gallery Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one private-demo evidence gallery that lets the owner review, select, and export the best completed demo runs from browser and terminal without opening every job manually.

**Architecture:** Add a read-only operator aggregate that lists recent completed localization jobs, derives handoff and evidence readiness from existing job detail/package services, and returns safe download routes plus presenter notes. Render the same gallery in React and expose a terminal report script so the hosted private demo has repeatable input/output evidence after jobs complete. Keep this as a metadata index; do not persist curation state or copy media bytes.

**Tech Stack:** Spring Boot MVC, existing job/operator services, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend API, frontend workspace, terminal gallery script, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep all gallery output metadata-only. Do not expose API keys, demo tokens, passwords, object keys, raw local media paths, provider payloads, raw transcript/subtitle text, corrected subtitle text, uploaded media bytes, generated media bytes, or backup contents.
- The backend API is read-only. It must not upload media, call OpenAI, run backups/restores, mutate jobs, publish reviewed subtitles, delete retention candidates, or run Docker.
- Prefer existing job detail, diagnostics, delivery manifest, presenter pack, demo run package, AI audit package, handoff package, and evidence routes before adding new package behavior.
- The gallery should advance Stage 2 private demo repeatability: completed demo runs must be easier to present, compare, and hand off from one workspace.

---

## Current Context

- Private demo deployment now has reverse proxy config, owner token gate, operations readiness, backup/restore paths, and a launch rehearsal checklist.
- Single-job evidence is already strong: delivery manifest, handoff package, demo run package, AI audit package, presenter pack, quality evidence, diagnostics, and browser evidence panels.
- The remaining Stage 2 gap is cross-run selection after demos have completed. The owner needs one place to see which completed runs are handoff-ready, which run is recommended, and which safe downloads should be shared.

## Task 1: Backend Evidence Gallery API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoEvidenceGalleryDownloadVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoEvidenceGalleryJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoEvidenceGalleryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/PrivateDemoEvidenceGalleryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/PrivateDemoEvidenceGalleryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/PrivateDemoEvidenceGalleryServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`

**Interfaces:**
- Produces: `GET /api/operator/private-demo/evidence-gallery?limit=20`.
- Produces: `PrivateDemoEvidenceGalleryVo(generatedAt, overallStatus, completedJobCount, handoffReadyCount, recommendedJobId, jobs, galleryDownloads, galleryNotesMarkdown)`.
- Produces job rows with `jobId`, `videoId`, `targetLanguage`, `demoProfileId`, `status`, `createdAt`, `completedAt`, `qualityScore`, `qualityVerdict`, `estimatedCostUsd`, `modelCallCount`, `providerCacheHitCount`, `handoffReady`, `presenterPackReady`, `recommended`, `attentionReasons`, and safe `downloads`.
- Produces download rows with `label`, `href`, `contentType`, and `description`.
- Status values: `READY`, `ATTENTION`, `EMPTY`.

- [x] Write failing service tests for empty completed-job history, completed jobs with safe downloads, handoff-ready counting, recommended-job selection, and redaction of unsafe strings.
- [x] Implement VO records and `PrivateDemoEvidenceGalleryService`.
- [x] Implement `PrivateDemoEvidenceGalleryServiceImpl` using `LocalizationJobQueryService.listJobs(LocalizationJobStatus.COMPLETED, limit, 0)` and `LocalizationJobQueryService.getJob(jobId)`.
- [x] Derive readiness without mutation: handoff-ready when the delivery manifest reports a ready handoff; presenter-pack-ready when the existing presenter pack service can build a pack for the job.
- [x] Select the recommended job by highest quality score, then lowest estimated cost, then newest completion time; if no quality exists, choose the newest handoff-ready completed job.
- [x] Add safe routes for job detail, diagnostics, evidence Markdown, evidence bundle, quality evidence, delivery manifest, handoff package, demo run package, AI audit package, presenter pack, and source media metadata when derivable.
- [x] Add controller endpoint under the operator/private-demo boundary and OpenAPI route coverage.
- [x] Run `mvn -pl LinguaFrame -Dtest=PrivateDemoEvidenceGalleryServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests test`.

## Task 2: Browser Evidence Gallery Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `PrivateDemoEvidenceGallery` from `/api/operator/private-demo/evidence-gallery`.
- Produces: a `Private demo evidence gallery` workspace near the private-demo operations and launch rehearsal panels.

- [x] Write failing API tests for `getPrivateDemoEvidenceGallery()` including owner token header behavior and encoded query parameters.
- [x] Add TypeScript types for gallery aggregate, gallery job rows, download links, and status values.
- [x] Load the gallery in the app alongside existing private-demo operations data without blocking upload or job-detail screens when unavailable.
- [x] Write failing App tests for empty state, recommended run display, handoff-ready counts, quality/cost/cache metadata, safe download links, copy notes, and Markdown download.
- [x] Render a dense operational panel with summary counters, recommended run, recent completed runs, attention reasons, and grouped safe links.
- [x] Add copy/download actions for `galleryNotesMarkdown` using the existing evidence/report clipboard pattern.
- [x] Style the panel consistently with existing operations/rehearsal surfaces; avoid hero layout, nested cards, and raw media text.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "private demo evidence gallery"`.

## Task 3: Terminal Evidence Gallery Report

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/private-demo-evidence-gallery.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces: `download_private_demo_evidence_gallery_json BASE_URL OUTPUT_PATH LIMIT`.
- Produces: `print_private_demo_evidence_gallery_summary_file GALLERY_JSON_PATH`.
- Produces: `scripts/demo/private-demo-evidence-gallery.sh` that writes JSON and Markdown under `/tmp/linguaframe-demo/private-demo-evidence-gallery/`.

- [x] Write failing script tests for the encoded gallery route, owner-token header flow, empty-state output, recommended job summary, download-link rendering, and unsafe-marker redaction.
- [x] Implement the gallery JSON download helper with the same env/token handling as other private-demo scripts.
- [x] Implement a Python summary writer that prints overall status, completed count, handoff-ready count, recommended job id, top attention reasons, and safe download links.
- [x] Implement `private-demo-evidence-gallery.sh` so it fetches the API, writes `evidence-gallery.json`, writes `evidence-gallery.md`, prints a concise summary, and exits zero for `READY`, `ATTENTION`, and `EMPTY`.
- [x] Ensure the script does not call providers, upload media, create backups, restore data, clean storage, or run Docker.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/private-demo-evidence-gallery.sh`.

## Task 4: Documentation, Verification, Commit, Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/097-private-demo-evidence-gallery-workspace.md`

**Interfaces:**
- Documents when to use operations readiness, launch rehearsal, and evidence gallery.
- Documents browser and terminal gallery workflows, expected output files, and the metadata-only safety boundary.

- [x] Update private-demo docs with `scripts/demo/private-demo-evidence-gallery.sh` and explain it is for post-run evidence selection.
- [x] Update demo docs to explain that operations readiness checks health, launch rehearsal orders go/no-go steps, and evidence gallery selects completed demo outputs.
- [x] Update target state and roadmap to mark private-demo cross-run evidence gallery as implemented after validation.
- [x] Record the decision to keep the gallery read-only and derived from existing job/package evidence instead of adding persistent curation state.
- [x] Update execution log with implementation and validation evidence.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on `private-demo-evidence-gallery`, merge back to `main`, and record the merge in the execution log.
