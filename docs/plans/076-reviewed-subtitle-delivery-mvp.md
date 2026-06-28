# Reviewed Subtitle Delivery MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Let a demo reviewer publish saved subtitle draft corrections into downloadable reviewed subtitle artifacts and an optional reviewed subtitle-burned video.

**Architecture:** Keep generated target subtitles and draft rows unchanged. Add a publish service that reads the saved draft overlay, creates reviewed subtitle artifacts on demand, and optionally burns the reviewed SRT into the original source video using the existing FFmpeg burn-in boundary. Reviewed artifacts are explicit job artifacts, so they appear in artifact lists, bundles, browser delivery panels, diagnostics, and terminal demos.

**Tech Stack:** Java 21, Spring Boot, Flyway-compatible enum/storage changes, JUnit 5, MockMvc, React, TypeScript, Vitest, existing `SubtitleDraftService`, `SubtitleExportService`, `JobArtifactService`, `FfmpegSubtitleBurnInService`, and object storage services.

## Global Constraints

- This is one complete user-visible feature slice: backend publish API, artifact persistence, optional reviewed burn-in, frontend controls, demo scripts, tests, docs, validation, commit, and merge.
- Do not mutate original generated `subtitle_segments` rows.
- Do not delete or rewrite existing generated artifacts.
- Do not run OpenAI calls, TTS regeneration, quality re-evaluation, job retry, or job status transitions in this feature.
- Reviewed evidence may include artifact counts, hashes, filenames, and timestamps, but must not include raw transcript text, generated subtitle text, or corrected draft text.
- Reviewed burn-in must be explicit and best-effort through a publish option; a subtitle-only publish must work when FFmpeg burn-in is disabled.

---

## Task 1: Reviewed Artifact Types And Publish Service

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/PublishReviewedSubtitlesRequest.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ReviewedSubtitlePublishVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ReviewedSubtitleDeliveryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ReviewedSubtitleDeliveryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ReviewedSubtitleDeliveryServiceTests.java`

**Interfaces:**
- Consumes: `SubtitleDraftService.exportDraft(jobId, language, format)`, `JobArtifactService.createArtifact(CreateJobArtifactCommand)`.
- Produces: `ReviewedSubtitleDeliveryService.publish(jobId, request)` returning `ReviewedSubtitlePublishVo`.

**Steps:**

- [x] Add artifact enum values `REVIEWED_SUBTITLE_JSON`, `REVIEWED_SUBTITLE_SRT`, `REVIEWED_SUBTITLE_VTT`, and `REVIEWED_BURNED_VIDEO`.
- [x] Add `PublishReviewedSubtitlesRequest(String language, boolean includeBurnedVideo)`.
- [x] Add `ReviewedSubtitlePublishVo(String jobId, String targetLanguage, boolean burnedVideoRequested, boolean burnedVideoCreated, List<JobArtifactVo> artifacts)`.
- [x] Implement subtitle-only publish by creating reviewed JSON/SRT/VTT artifacts from the current draft overlay.
- [x] Use stable filenames `reviewed-subtitles.<language>.json`, `.srt`, and `.vtt`.
- [x] Reject publish when generated target subtitles are missing, reusing the current draft service not-found behavior.
- [x] Test that reviewed artifacts contain corrected draft text while generated target subtitle artifacts remain unchanged.
- [x] Test repeated publish creates a new explicit reviewed artifact set instead of mutating old artifact rows.

## Task 2: Optional Reviewed Burn-In

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ReviewedSubtitleDeliveryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ReviewedSubtitleDeliveryServiceTests.java`

**Interfaces:**
- Consumes: `FfmpegSubtitleBurnInService.burnInSubtitles(BurnInSubtitlesCommand)`, `MediaWorkDirectoryService`, `ObjectStorageService`.
- Produces: optional `REVIEWED_BURNED_VIDEO` artifact in the publish result.

**Steps:**

- [x] When `includeBurnedVideo=false`, publish reviewed subtitle files only.
- [x] When `includeBurnedVideo=true` and `linguaframe.ffmpeg.burn-in-enabled=false`, return subtitle artifacts with `burnedVideoRequested=true` and `burnedVideoCreated=false`.
- [x] When `includeBurnedVideo=true` and burn-in is enabled, copy the source video into a job work directory, write reviewed SRT to disk, run `FfmpegSubtitleBurnInService`, and store `reviewed-burned-video.mp4`.
- [x] Always delete the media work directory in a `finally` block.
- [x] Test disabled burn-in does not call FFmpeg and still returns reviewed subtitle artifacts.
- [x] Test enabled burn-in stores `REVIEWED_BURNED_VIDEO`.
- [x] Test FFmpeg failure does not create a partial reviewed burned video artifact and surfaces a clear client error.

## Task 3: Publish API And Evidence Surface

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`

**Interfaces:**
- Produces: `POST /api/jobs/{jobId}/subtitle-draft/publish`
- Request body: `{"language":"zh-CN","includeBurnedVideo":false}`
- Response body: `ReviewedSubtitlePublishVo`.

**Steps:**

- [x] Add controller endpoint `POST /api/jobs/{jobId}/subtitle-draft/publish`.
- [x] Default missing language to `zh-CN` and missing `includeBurnedVideo` to `false`.
- [x] Return reviewed artifact metadata in the response.
- [x] Ensure artifact list and artifact archive automatically include reviewed artifacts through existing artifact APIs.
- [x] Add backend evidence lines for reviewed subtitle artifact count and reviewed burned video availability.
- [x] Test endpoint response, artifact list visibility, artifact download, and archive manifest entries.
- [x] Test evidence includes reviewed artifact metadata but excludes raw corrected subtitle text.

## Task 4: Browser Reviewed Delivery Workflow

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `publishReviewedSubtitles(jobId, request)`.
- Produces: browser controls in `Subtitle draft editor` and reviewed delivery metadata in `Result delivery` / evidence.

**Steps:**

- [x] Add TypeScript types for publish request and response.
- [x] Add `linguaFrameApi.publishReviewedSubtitles(jobId, request)`.
- [x] Add `Publish reviewed subtitles` button beside draft save/clear controls.
- [x] Add `Include reviewed burned video` checkbox that is off by default.
- [x] After publish succeeds, refresh artifacts and show created reviewed artifact count.
- [x] Add reviewed subtitle and reviewed burned video rows to result delivery when artifacts exist.
- [x] Add browser evidence metadata for reviewed artifact count and reviewed burned video availability only.
- [x] Test publish request shape, success state, artifact refresh, reviewed rows, disabled/error state, and evidence redaction.

## Task 5: Demo Scripts And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/076-reviewed-subtitle-delivery-mvp.md`

**Steps:**

- [x] Add demo helper `publish_reviewed_subtitles(baseUrl, jobId, language, includeBurnedVideo)`.
- [x] Add metadata-only printer for reviewed publish response.
- [x] Update the deterministic E2E script to publish reviewed subtitle files after draft summary is available, without requesting reviewed burn-in by default.
- [x] Add script test proving publish summary output excludes raw corrected subtitle text.
- [x] Document reviewed subtitle artifacts as the handoff-ready result of human subtitle correction.
- [x] Document that reviewed burn-in is explicit and does not regenerate TTS or replace original burned video artifacts.
- [x] Mark roadmap Phase 8 reviewed subtitle delivery as implemented.
- [x] Record the decision to publish reviewed artifacts explicitly instead of mutating generated artifacts.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=ReviewedSubtitleDeliveryServiceTests,LocalizationJobControllerTests,JobEvidenceReportServiceTests,OpenApiDocumentationTests test`.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh`.
- [x] Run `git diff --check`.
- [x] Post-merge: rerun backend focused tests, frontend focused tests, frontend build, demo client tests, and `git diff --check` on `main`.

## Done Criteria

- [x] A browser user can publish saved draft corrections into reviewed JSON/SRT/VTT artifacts.
- [x] Reviewed artifacts appear in artifact list, downloads, result bundle/archive, browser result delivery, diagnostics/evidence metadata, and terminal demo summaries.
- [x] A browser user can optionally request a reviewed subtitle-burned video when FFmpeg burn-in is enabled.
- [x] Generated subtitle artifacts and generated burned video artifacts remain unchanged.
- [x] No evidence or terminal summary leaks raw transcript, generated subtitle, or corrected draft subtitle text.
- [x] Tests, docs, validation, commit, and merge back to `main` are completed as part of this feature slice.
