# TTS Dubbed Video Delivery Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a downloadable and browser-playable video that combines target subtitles with generated TTS dubbing audio when TTS is enabled.

**Architecture:** Extend the existing media pipeline after `DUBBING_AUDIO_GENERATION` and subtitle burn-in by adding a safe FFmpeg mux/replacement service and a new delivery artifact type for dubbed video. Reuse existing artifact storage, cache, diagnostics, delivery, media-preview, evidence-package, and demo-script surfaces so the feature is visible end to end without adding accounts, billing, or public publishing.

**Tech Stack:** Spring Boot, existing localization pipeline stages, FFmpeg command runner pattern, MinIO-backed artifact storage, JUnit 5, React + TypeScript + Vitest, Bash/Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend media service, pipeline artifact creation, API/detail visibility, browser playback/download, terminal evidence, docs, validation, commit, and merge back to `main`.
- Do not change public auth, billing, multi-tenant boundaries, or provider routing.
- Do not expose object keys, local paths, raw transcript/subtitle text, provider payloads, demo tokens, OpenAI keys, or media bytes in metadata outputs.
- Keep TTS disabled-by-default behavior intact; dubbed video is skipped when TTS audio is absent or TTS is disabled.
- Keep the existing `BURNED_VIDEO` and `REVIEWED_BURNED_VIDEO` artifacts unchanged; dubbed video must be a separate artifact.
- Use existing artifact download routes instead of adding media-specific byte routes unless tests prove a new route is necessary.

---

## Current Context

- The pipeline already produces `DUBBING_AUDIO` when TTS is enabled.
- The pipeline already produces `BURNED_VIDEO` when subtitle burn-in is enabled.
- The browser `Media delivery`, `Result delivery`, handoff, evidence, demo package, and terminal scripts already understand playable media artifacts.
- Roadmap Phase 6 explicitly leaves "Audio replacement with TTS" as not built yet, so this feature moves the demo closer to a complete localized media output.

## Task 1: Backend Dubbed Video Artifact Foundation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ReplaceVideoAudioCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/DubbedVideoBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegAudioReplacementService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegAudioReplacementServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegAudioReplacementServiceTests.java`

**Interfaces:**
- Add `JobArtifactType.DUBBED_VIDEO`.
- Add `ReplaceVideoAudioCommand(jobId, inputVideoPath, inputAudioPath, outputVideoPath)`.
- Add `FfmpegAudioReplacementService.replaceAudio(ReplaceVideoAudioCommand): DubbedVideoBo`.
- FFmpeg command should map video from the input video and audio from the TTS artifact, encode or copy safely, and write `dubbed-video.mp4`.
- Errors must use bounded safe summaries like existing FFmpeg services.

- [x] Write failing tests for FFmpeg command shape, success output metadata, non-zero exit safe failure, timeout failure, and interrupted command cleanup.
- [x] Implement command/BO types and service.
- [x] Run `mvn -pl LinguaFrame -Dtest=FfmpegAudioReplacementServiceTests test`.

## Task 2: Pipeline Stage And Artifact Visibility

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbedVideoPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerStageRouterImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/RabbitJobQueuePublisher.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbedVideoPipelineStageTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/WorkerStageRouterTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Add a stage after `SUBTITLE_BURN_IN` and before `ARTIFACT_SUMMARY`, for example `DUBBED_VIDEO_DELIVERY`.
- The stage should skip when TTS is disabled, `DUBBING_AUDIO` is absent, or burn-in is disabled and no video base artifact exists.
- Prefer `BURNED_VIDEO` as the video base so the final output has both subtitles and dubbing audio.
- Create `DUBBED_VIDEO` artifact with filename `dubbed-video.mp4`, content type `video/mp4`, and normal artifact hash/cache metadata.
- Reuse artifact cache only when existing cache identity is already safe for this artifact type; otherwise do not cache in this slice.

- [x] Write failing stage tests for skip cases, successful `DUBBING_AUDIO` + `BURNED_VIDEO` mux, safe work-directory cleanup, and failure propagation.
- [x] Add pipeline stage enum, routing, execution order, and Rabbit role routing.
- [x] Run focused backend tests for stage/router/execution behavior.

## Task 3: Browser Delivery And Evidence Integration

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DeliveryManifestServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Test: relevant backend service/controller tests already covering delivery/evidence/package metadata.

**Interfaces:**
- Add `DUBBED_VIDEO` to frontend artifact type unions and media delivery output ordering.
- Browser `Media delivery` should show `Dubbed video` as a playable video with download link, content type, size, hash, and generated/reused state.
- `Result delivery`, `Delivery handoff`, `Demo handoff checklist`, `Demo session report`, `Demo evidence`, and demo run package summaries should count and label `DUBBED_VIDEO` as a media output without embedding bytes.
- Delivery manifest should keep reviewed artifacts separate; `DUBBED_VIDEO` is generated audit/delivery media unless a later reviewed workflow promotes it.

- [x] Write failing frontend tests for rendering and downloading `DUBBED_VIDEO`.
- [x] Write or update backend tests proving delivery/evidence/package metadata includes `DUBBED_VIDEO` safely.
- [x] Implement UI/domain/backend metadata changes.
- [x] Run `cd frontend && npm test -- --run src/App.test.tsx`.
- [x] Run focused backend tests for delivery/evidence/package services.

## Task 4: Terminal Demo Evidence

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Extend media delivery summary helpers to include `DUBBED_VIDEO`.
- Extend deterministic/OpenAI/full-video scripts to attempt optional `DUBBED_VIDEO` download to `/tmp/linguaframe-demo/dubbed-video.mp4` or the script-specific output directory.
- Keep scripts optional-friendly: no failure when TTS is disabled and the artifact is absent.
- Add tests proving terminal summaries do not print local paths, object keys, tokens, provider payloads, or raw text.

- [x] Write failing script tests for `DUBBED_VIDEO` summary and redaction.
- [x] Implement helper and script updates.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`.

## Task 5: Documentation, Validation, Commit, Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/101-tts-dubbed-video-delivery-workspace.md`

**Interfaces:**
- Document that dubbed video is generated only when TTS audio and a video base artifact are available.
- Document that existing burned-video and reviewed-burned-video artifacts remain separate.
- Record all validation commands and post-merge verification.

- [x] Update docs and execution log.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on a feature branch, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
