# Source Media Evidence Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a safe source-media workspace so a demo reviewer can verify the exact uploaded input video, its limits, and its relationship to the localization job without exposing object keys or local paths.

**Architecture:** Reuse the existing media upload metadata and object storage abstraction. Add a controlled source-video download endpoint plus a sanitized job-facing source media summary, render it in the React selected-job view, and include the same metadata in terminal demo summaries and safe evidence docs.

**Tech Stack:** Java 21, Spring Boot, MockMvc, React + TypeScript + Vitest, Bash demo scripts, Markdown docs.

## Global Constraints

- Implement one complete feature slice: backend, frontend, scripts, docs, tests, validation, commit, and merge back to `main`.
- Do not expose source object keys, local file paths, storage credentials, demo tokens, OpenAI keys, provider payloads, raw transcript text, raw subtitles, or media bytes inside metadata evidence exports.
- Source download may stream the uploaded media bytes only through an explicit authenticated API route.
- Accepted uploads are processed in full; do not add clipping or trimming.
- Keep AGENTS.md unchanged.

---

## Feature Design

Recommended approach: add a **Source media** workspace connected to selected job detail. It should show filename, content type, size, duration, upload status, created time, video id, job id, target language, upload limit evidence, and links to controlled source metadata/download routes. This closes an input-side demo gap: the project already has output packages, AI audit, handoff, and quality evidence, but the browser and terminal proof trail still treat the original input mostly as a `videoId`.

Alternative 1: only add a source download link. This is smaller, but it does not help a reviewer understand whether duration and upload limits were applied correctly.

Alternative 2: add source media into every evidence ZIP. This is too heavy and weakens the safe metadata-only boundary. Keep media bytes behind an explicit download route.

## Files

- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadDetailVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Backend Source Media Contract

**Interfaces:**

- Produce: `MediaUploadDetailVo` without `sourceObjectKey`.
- Produce: `StoredObjectResourceBo openSourceMedia(String videoId)`.
- Produce: `GET /api/media/uploads/{videoId}/source/download`.

- [x] Write a failing MockMvc test in `MediaUploadControllerTests` that uploads `sample.mp4`, fetches `GET /api/media/uploads/{videoId}`, and asserts `$.sourceObjectKey` does not exist.
- [x] Write a failing MockMvc test that stubs `objectStorageService.open(...)`, calls `GET /api/media/uploads/{videoId}/source/download`, and asserts status 200, attachment filename `sample.mp4`, content type `video/mp4`, content length, and original bytes.
- [x] Add `openSourceMedia(String videoId)` to `MediaUploadService` and implement it in `MediaUploadServiceImpl` by loading `VideoRecord`, opening its stored object internally, and returning `StoredObjectResourceBo(filename, contentType, fileSizeBytes, inputStream)`.
- [x] Remove `sourceObjectKey` from `MediaUploadDetailVo` and update call sites/tests to use only safe metadata.
- [x] Add the controller endpoint with `Content-Disposition: attachment`.
- [x] Add `/api/media/uploads/{videoId}/source/download` to required runtime and OpenAPI contract tests.
- [x] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.

## Task 2: Frontend Source Media Workspace

**Interfaces:**

- Consume: `GET /api/media/uploads/{videoId}` returns safe source metadata.
- Consume: `sourceMediaDownloadUrl(videoId)` returns `/api/media/uploads/{videoId}/source/download`.
- Produce: selected-job `Source media` panel.

- [x] Add API helper and tests in `frontend/src/api/linguaframeApi.ts` and `frontend/src/api/linguaframeApi.test.ts` for `getMediaUpload(videoId)` and `sourceMediaDownloadUrl(videoId)`.
- [x] Add selected-job state to load source media metadata whenever a job is selected or refreshed.
- [x] Render a `Source media` panel near the selected job summary showing filename, content type, size, duration, upload status, created time, job id, video id, target language, and source download link.
- [x] Show a clear inline message if source metadata fails to load, without breaking upload or job detail rendering.
- [x] Add App tests for successful rendering, source download link, no object-key text, and metadata-load failure fallback.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi` and `cd frontend && npm run build`.

## Task 3: Demo Script Evidence

**Interfaces:**

- Produce: safe source media terminal summary.
- Produce: optional source media download path `/tmp/linguaframe-demo/source-video.<ext>` for deterministic and OpenAI smoke scripts.

- [x] Add `fetch_source_media_metadata`, `download_source_media`, and `print_source_media_summary` helpers in `scripts/demo/lib/linguaframe-demo.sh`.
- [x] Ensure summaries print only `videoId`, filename, content type, size, duration, status, created time, and safe route presence; do not print object keys.
- [x] Extend `scripts/demo/docker-e2e-success.sh` to fetch source metadata, download the source video to `/tmp/linguaframe-demo/source-video.<safe extension>`, and print source media summary before output artifact downloads.
- [x] Extend `scripts/demo/docker-e2e-openai-smoke.sh` with the same source media evidence under its output directory.
- [x] Add shell helper tests that validate metadata parsing and reject forbidden markers such as `source-videos/`, `/Users/`, `objectKey`, `demo-access-token`, and `OPENAI_API_KEY`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh` and `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh`.

## Task 4: Evidence And Product Documentation

**Interfaces:**

- Produce: README and agent docs that explain source-media evidence and the safe download boundary.
- Produce: roadmap/target-state updates showing this input-side workspace is implemented.

- [x] Update `README.md` to describe the `Source media` browser panel and the source download endpoint.
- [x] Update `docs/agent/docker-e2e-demo.md` and `docs/agent/smoke-test-checklist.md` with expected source media browser and terminal checks.
- [x] Update `docs/product/roadmap.md` and `docs/product/target-state.md` to mark source input evidence as implemented in the React demo experience.
- [x] Add a decision in `docs/progress/decisions.md`: source media metadata is safe to show, object keys are not, and source bytes only move through explicit download.
- [x] Add a validation entry in `docs/progress/execution-log.md`.

## Task 5: Final Verification, Commit, And Merge

- [x] Run backend verification: `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.
- [x] Run frontend verification: `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run script verification: `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run syntax and whitespace checks: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh` and `git diff --check`.
- [x] Commit on `source-media-evidence-workspace` with message `Add source media evidence workspace`.
- [x] Merge back to `main` with `git merge --no-ff source-media-evidence-workspace`.
- [x] Re-run the focused backend, frontend, script, and diff verification on `main`.

## Acceptance Criteria

- A selected job has a browser-visible `Source media` panel with safe source input metadata and a source download link.
- `GET /api/media/uploads/{videoId}` no longer exposes `sourceObjectKey`.
- `GET /api/media/uploads/{videoId}/source/download` streams source bytes as an explicit attachment.
- Demo scripts print source media evidence and can download the source video for local inspection.
- Evidence text and summaries do not contain object keys, local paths, secrets, raw media text, or provider payloads.
- All listed validations pass before commit and again after merge to `main`.
