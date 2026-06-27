# Job Result Bundle Download MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-click ZIP download for all generated artifacts of a localization job so the browser demo can export complete results without clicking each artifact row.

**Architecture:** Extend the existing job artifact boundary with an archive download operation. The backend will stream a ZIP assembled from existing artifact object storage entries plus a small `manifest.json`; the frontend will add a download link in the artifacts panel. Demo scripts and docs will verify the archive without changing the core pipeline.

**Tech Stack:** Spring Boot, Java 21, JUnit 5, MockMvc, React, TypeScript, Vitest, Bash, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Do not include source videos, raw local media paths, secrets, demo tokens, OpenAI keys, or storage credentials in the ZIP.
- Keep existing individual artifact download URLs unchanged.
- Use sanitized ZIP entry paths derived from artifact type, artifact id, and filename; never trust stored filenames as raw paths.
- Keep the archive read-only and generated on demand; do not persist ZIP files in object storage.
- Plan and discussion can be Chinese, but repository docs and UI copy stay English.

---

## Task 1: Backend Artifact Archive Service

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobArtifactService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredArtifactArchiveBo.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobArtifactServiceTests.java`

**Interfaces:**
- Consumes: `JobArtifactRepository.findByJobId(String jobId)` and `ObjectStorageService.open(String objectKey)`
- Produces: `StoredArtifactArchiveBo(String filename, String contentType, long sizeBytes, InputStream inputStream)` and `JobArtifactService.openArtifactArchive(String jobId)`

- [x] Add a failing service test `opensArchiveWithManifestAndArtifactsForJob`.
- [x] Assert the ZIP contains `manifest.json` plus entries shaped like `artifacts/WORKER_SUMMARY/<artifactId>-worker-summary.json`.
- [x] Assert `manifest.json` includes job id, artifact count, type, filename, size, content SHA-256, and cache-hit fields.
- [x] Add a test `sanitizesArchiveEntryFilenames` for filenames such as `../evil.mp4` and `nested/path.srt`.
- [x] Implement `StoredArtifactArchiveBo`.
- [x] Add `openArtifactArchive(String jobId)` to `JobArtifactService`.
- [x] Implement ZIP assembly in `JobArtifactServiceImpl` with `ZipOutputStream` and `ByteArrayOutputStream`.
- [x] Return an empty archive with `artifactCount: 0` for jobs with no artifacts.
- [x] Run: `mvn -pl LinguaFrame -Dtest=JobArtifactServiceTests test`.

## Task 2: Backend Archive Download Endpoint

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Consumes: `JobArtifactService.openArtifactArchive(String jobId)`
- Produces: `GET /api/jobs/{jobId}/artifacts/archive/download`

- [x] Add a failing controller test `downloadsArtifactArchiveForLocalizationJob`.
- [x] Seed at least two artifact records for one job and mock object storage bytes.
- [x] Assert status `200`, content type `application/zip`, and attachment filename `linguaframe-job-<jobId>-artifacts.zip`.
- [x] Read the response ZIP in the test and assert it contains `manifest.json` and both artifact entries.
- [x] Add controller method `downloadArtifactArchive`.
- [x] Return `InputStreamResource` with content length from `StoredArtifactArchiveBo`.
- [x] Run: `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,DemoAccessInterceptorTests' test`.

## Task 3: Frontend Archive Download Action

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /api/jobs/{jobId}/artifacts/archive/download`
- Produces: a visible `Download result bundle` link in the `Artifacts` panel

- [x] Add `artifactArchiveDownloadUrl(jobId: string): string` to `linguaframeApi`.
- [x] Add an API test asserting archive URLs are encoded and point at `/api/jobs/{jobId}/artifacts/archive/download`.
- [x] Add a `Download result bundle` link near the `Artifacts` heading when a job is selected.
- [x] Keep the link visible even when artifact list loading fails, but hide it when no job is selected.
- [x] Add an App test that a completed job with artifacts shows the bundle link with the expected href.
- [x] Add an App test that an empty artifact list still renders the bundle link and the existing empty-state copy.
- [x] Add compact styling for artifact panel header actions.
- [x] Run: `cd frontend && npm run test:run -- linguaframeApi App`.

## Task 4: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: repeatable script evidence that the ZIP bundle downloads and is inspectable

- [x] Add a demo helper `download_artifact_archive "$base_url" "$job_id" "$output_path"`.
- [x] Update success and full Tears of Steel scripts to download `/tmp/linguaframe-demo/artifacts.zip` by default.
- [x] Add a Python ZIP listing check in the success script that prints archive entries.
- [x] Document the browser bundle link and script output in README.
- [x] Add archive verification to the smoke-test checklist.
- [x] Update product docs to include one-click result bundle download as part of artifact export.
- [x] Record the decision that ZIP archives are generated on demand and not persisted.
- [x] Run: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`.
- [x] Run: `docker compose --env-file .env.example config --quiet`.
- [x] Run: `git diff --check`.

## Task 5: Full Verification And Merge

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/046-job-result-bundle-download-mvp.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='JobArtifactServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run script syntax validation: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [x] Commit as `Add job result bundle download`.
- [x] Merge `job-result-bundle-download-mvp` back to `main`.
- [x] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- linguaframeApi App`.
