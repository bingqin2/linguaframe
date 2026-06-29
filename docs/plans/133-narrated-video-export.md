# Narrated Video Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a standalone playable narrated video from saved time-coded narration audio without replacing existing subtitle, dubbing, burned-video, or reviewed handoff artifacts.

**Architecture:** Reuse the existing narration workspace and `NARRATION_AUDIO` artifact, then add a manual backend export service that selects the best available video base and uses the existing FFmpeg audio replacement boundary to create a separate `NARRATED_VIDEO`. Keep narrated-video generation outside the automatic worker pipeline for this slice so the browser and terminal demo can trigger and validate it deliberately.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5 + MockMvc, existing FFmpeg audio replacement service, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This is one complete feature slice: backend service/API, artifact type, FFmpeg integration, tests, frontend UI, terminal script/docs, validation, commit, and merge back to `main`.
- Use branch title `narrated-video-export` if a new branch is needed; do not include `/` in the user-facing branch title.
- Generate `NARRATED_VIDEO` as a separate artifact and never overwrite `DUBBING_AUDIO`, `NARRATION_AUDIO`, `DUBBED_VIDEO`, `BURNED_VIDEO`, `REVIEWED_BURNED_VIDEO`, generated subtitles, reviewed subtitles, or existing handoff artifacts.
- Use existing media and artifact boundaries. Do not introduce a new video-processing framework, waveform editor, timeline drag/drop editor, voice cloning, lip sync, multi-track mixer, or background-audio ducking in this slice.
- Evidence and terminal summaries must remain metadata-only and exclude raw transcript text, subtitle text, narration script bodies, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Prefer the best available base video in this order: `REVIEWED_BURNED_VIDEO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, then source video from job media. Record the selected base type in the generation response and evidence.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Add Narrated Video Artifact And FFmpeg Output Naming

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ReplaceVideoAudioCommand.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegAudioReplacementServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegAudioReplacementServiceTests.java`

**Interfaces:**
- Add `JobArtifactType.NARRATED_VIDEO`.
- Extend `ReplaceVideoAudioCommand` to include `String outputFilename`.
- Keep existing `FfmpegAudioReplacementService.replaceAudio(ReplaceVideoAudioCommand)` signature.

- [x] Add enum value `NARRATED_VIDEO` next to other playable video artifacts.
- [x] Add `outputFilename` to `ReplaceVideoAudioCommand` so callers can request `dubbed-video.mp4` or `narrated-video.mp4`.
- [x] Update `DubbedVideoPipelineStage` construction sites to pass `dubbed-video.mp4`.
- [x] Update `FfmpegAudioReplacementServiceImpl` to return `command.outputFilename()` instead of hardcoded `dubbed-video.mp4`.
- [x] Preserve current FFmpeg command behavior: copy video stream, encode AAC audio, use `-shortest`, and `+faststart`.
- [x] Add/update tests proving existing dubbed-video output still returns `dubbed-video.mp4` and custom output returns `narrated-video.mp4`.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegAudioReplacementServiceTests,DubbedVideoPipelineStageTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 2: Backend Narrated Video Generation API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarratedVideoGenerationVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarratedVideoService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarratedVideoServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarratedVideoServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-workspace/generate-video`
- `NarratedVideoGenerationVo(String jobId, String artifactId, String filename, String contentType, long sizeBytes, String baseVideoType, String narrationAudioArtifactId, String status)`

- [x] Implement `NarratedVideoService.generateVideo(String jobId)`.
- [x] Require an existing `NARRATION_AUDIO`; reject with `IllegalArgumentException("Narration audio is required before generating narrated video.")` when missing.
- [x] Select base video in order: `REVIEWED_BURNED_VIDEO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, then source media video from the job.
- [x] Reject when no base video is available with `IllegalArgumentException("A source or generated video is required before generating narrated video.")`.
- [x] Use `MediaWorkDirectoryService` to create a job workspace, copy selected base video and narration audio into it, invoke `FfmpegAudioReplacementService.replaceAudio`, and always delete the workspace in `finally`.
- [x] Store the result as `narrated-video.mp4` with artifact type `NARRATED_VIDEO`.
- [x] Do not call TTS, do not mutate narration segments, and do not replace any existing artifacts.
- [x] Add tests for successful generation from `BURNED_VIDEO`, preference for `REVIEWED_BURNED_VIDEO`, source-video fallback, missing narration-audio rejection, missing-base-video rejection, and artifact isolation.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarratedVideoServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 3: Evidence, Runtime Routes, And Handoff Integration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunPackageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- Add narrated-video readiness fields to `NarrationEvidenceVo`: `boolean narratedVideoReady`, `int narratedVideoArtifactCount`.
- Add runtime required route for `POST /api/jobs/{jobId}/narration-workspace/generate-video`.

- [x] Update narration evidence status: `READY` only when segments, narration audio, and narrated video all exist; `ATTENTION` when segments exist but audio or narrated video is missing; `BLOCKED` when segments are missing.
- [x] Add `NARRATED_VIDEO` check, safe link metadata, package entry, and Markdown/JSON summary counts.
- [x] Ensure evidence package remains metadata-only and still excludes narration script bodies and media bytes.
- [x] Include `NARRATED_VIDEO` in job evidence and demo handoff portal safe artifact/package links.
- [x] Include `NARRATED_VIDEO` in demo run package media outputs where existing playable media artifacts are collected.
- [x] Add runtime route coverage for the generate-video endpoint and artifact type visibility.
- [x] Add tests for READY/ATTENTION behavior, ZIP summary content, forbidden marker absence, runtime route coverage, and handoff links.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,RuntimeDependencyControllerTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 4: React Narrated Video Controls And Media Delivery

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `NarratedVideoGeneration` type.
- Add `generateNarratedVideo(jobId: string): Promise<NarratedVideoGeneration>`.
- Extend `NarrationEvidence` with `narratedVideoReady` and `narratedVideoArtifactCount`.

- [ ] Add API helper and tests for `POST /api/jobs/{jobId}/narration-workspace/generate-video`.
- [ ] Add `Generate narrated video` action to the existing `Narration workspace` panel.
- [ ] Disable or clearly surface blocked state when narration audio is not ready.
- [ ] Show generated result filename, base video type, and status after generation.
- [ ] Render `NARRATED_VIDEO` as a playable video card in media delivery with the label `Narrated video`.
- [ ] Extend evidence rendering to show narrated-video readiness without exposing narration text.
- [ ] Add Vitest coverage for the generate-video action, evidence rendering, blocked state, and `NARRATED_VIDEO` media card.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.
- [ ] Update `docs/progress/execution-log.md`.

## Task 5: Terminal Demo Scripts And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/narration-evidence.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Optional env: `LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO=true` for scripts that already know a job id and have narration audio.

- [ ] Add Bash helper `generate_narrated_video_json(base_url, job_id, output_path)`.
- [ ] Extend narration evidence summary to print narrated-video readiness and artifact count.
- [ ] Extend deterministic, OpenAI smoke, and full Tears scripts to optionally call generate-video after narration audio exists; skip cleanly when missing prerequisites.
- [ ] Document browser workflow: save narration rows, generate narration audio, generate narrated video, verify `NARRATED_VIDEO` media card.
- [ ] Document terminal workflow and expected metadata-only evidence output.
- [ ] Update product docs to say narrated-video export exists, while ducking/mixing/waveform editing remain future work.
- [ ] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Update `docs/progress/execution-log.md`.

## Task 6: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/133-narrated-video-export.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Mark completed plan tasks.
- [ ] Run focused backend validation from Tasks 1-3.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run script syntax validation from Task 5.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Add narrated video export`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=FfmpegAudioReplacementServiceTests,DubbedVideoPipelineStageTests`
- `mvn -pl LinguaFrame test -Dtest=NarratedVideoServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,RuntimeDependencyControllerTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
