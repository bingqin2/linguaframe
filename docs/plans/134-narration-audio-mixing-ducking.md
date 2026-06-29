# Narration Audio Mixing And Ducking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate time-aligned narration audio and a narrated video that preserves original/base video audio while ducking it under narration segments.

**Architecture:** Convert saved narration rows into a timed narration audio bed instead of one continuous speech file, then mix that bed with the selected base video's existing audio when creating `NARRATED_VIDEO`. Keep the existing manual browser/script trigger, existing artifact types, and metadata-only evidence, but add explicit mix-mode and ducking evidence so the demo can prove the narration is aligned to operator-entered time ranges.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, existing `TtsProvider`, FFmpeg filter graphs, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This is one complete feature slice: backend timed audio bed generation, FFmpeg mixing/ducking, API response evidence, frontend controls/status, terminal scripts/docs, validation, commit, and merge back to `main`.
- Use branch title `narration-audio-mixing-ducking`; do not include `/` in the user-facing branch title.
- Preserve existing artifact boundaries: `NARRATION_AUDIO` remains the narration preview artifact and `NARRATED_VIDEO` remains the final mixed video artifact.
- Do not overwrite or replace `DUBBING_AUDIO`, `DUBBED_VIDEO`, `BURNED_VIDEO`, `REVIEWED_BURNED_VIDEO`, generated subtitles, reviewed subtitles, or handoff artifacts.
- Do not add waveform editing, drag/drop timeline editing, voice cloning, lip sync, public multi-user editing, or a generic multitrack editor in this slice.
- Evidence and terminal summaries must remain metadata-only and exclude raw transcript text, subtitle text, narration script bodies, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Default mix behavior: narration volume `1.0`, original/base audio volume `0.35` during narration windows, original/base audio volume `1.0` outside narration windows.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: FFmpeg Timed Narration Audio Bed

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/CreateTimedAudioBedCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/TimedAudioSegmentBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegTimedAudioBedService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegTimedAudioBedServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegTimedAudioBedServiceTests.java`

**Interfaces:**
- `TimedAudioSegmentBo(Path inputAudioPath, BigDecimal startSeconds, BigDecimal endSeconds)`
- `CreateTimedAudioBedCommand(String jobId, List<TimedAudioSegmentBo> segments, Path outputAudioPath, String outputFilename)`
- `FfmpegTimedAudioBedService.createAudioBed(CreateTimedAudioBedCommand command): TtsResultBo`

- [x] Add command/value objects for timed audio bed inputs.
- [x] Implement an FFmpeg filter graph that delays each segment by `startSeconds`, mixes delayed inputs with `amix`, encodes MP3, and writes `narration-audio.mp3`.
- [x] Keep output deterministic: sort segments by `startSeconds`, use millisecond delays, and reject empty segment lists.
- [x] Convert FFmpeg failures, timeouts, and IO errors into safe `IllegalStateException` messages without leaking file paths.
- [x] Add tests that assert the generated FFmpeg command contains `adelay`, `amix`, expected millisecond offsets, MP3 output, timeout handling, and safe stderr truncation.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegTimedAudioBedServiceTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 2: Timed Narration Audio Generation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationGenerationVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationAudioServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationAudioServiceTests.java`

**Interfaces:**
- Extend `NarrationGenerationVo` with `String audioLayout`, `boolean timeAligned`, and `int ttsCallCount`.
- `audioLayout` values: `TIMED_AUDIO_BED` for the new behavior, `CONTINUOUS_SCRIPT` only for legacy test fixtures if needed.

- [x] Change narration audio generation to synthesize each saved narration segment separately through `TtsProvider`.
- [x] Preserve per-segment voice behavior: use a segment voice when present; otherwise use the job default voice.
- [x] Run the budget guard before the first provider call and keep the existing stage identity.
- [x] Write each segment audio into a job work directory and call `FfmpegTimedAudioBedService.createAudioBed`.
- [x] Store the mixed bed as `NARRATION_AUDIO` with filename `narration-audio.mp3`.
- [x] Return `audioLayout=TIMED_AUDIO_BED`, `timeAligned=true`, and `ttsCallCount=segmentCount`.
- [x] Ensure provider or FFmpeg failure does not create a `NARRATION_AUDIO` artifact and always cleans the work directory.
- [x] Update tests for segment-level TTS calls, per-segment voice selection, time-aligned response fields, empty workspace rejection, provider failure, and FFmpeg failure.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationAudioServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 3: FFmpeg Original-Audio Mixing And Ducking

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MixNarratedVideoCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegNarratedVideoMixService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegNarratedVideoMixServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarratedVideoServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegNarratedVideoMixServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarratedVideoServiceTests.java`

**Interfaces:**
- `MixNarratedVideoCommand(String jobId, Path inputVideoPath, Path narrationAudioPath, Path outputVideoPath, String outputFilename, BigDecimal duckingVolume, List<NarrationWindow> narrationWindows)`
- `FfmpegNarratedVideoMixService.mixNarration(MixNarratedVideoCommand command): DubbedVideoBo`
- Extend `NarratedVideoGenerationVo` with `String mixMode`, `BigDecimal duckingVolume`, and `int narrationWindowCount`.

- [x] Implement an FFmpeg mix command that maps video from input 0, mixes base audio with narration audio, lowers base audio to `0.35` during narration windows, and keeps original volume outside those windows.
- [x] Use existing base video preference order: `REVIEWED_BURNED_VIDEO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, source video.
- [x] Read narration windows from saved narration segments instead of deriving them from raw narration text.
- [x] Replace the current `FfmpegAudioReplacementService` call in `NarratedVideoServiceImpl` with `FfmpegNarratedVideoMixService`.
- [x] Preserve fallback behavior for base videos without an audio track by outputting video plus narration audio without failing the whole generation.
- [x] Store output as `NARRATED_VIDEO` filename `narrated-video.mp4`.
- [x] Return `mixMode=DUCKED_ORIGINAL_AUDIO`, `duckingVolume=0.35`, and `narrationWindowCount=segmentCount`.
- [x] Add tests for command filter graph, ducking windows, missing narration audio rejection, missing base video rejection, source-video fallback, reviewed-video preference, artifact isolation, and work-directory cleanup.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegNarratedVideoMixServiceTests,NarratedVideoServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 4: Evidence, Handoff, And Runtime Metadata

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- Add metadata-only fields to narration evidence: `String audioLayout`, `boolean timeAligned`, `String mixMode`, `BigDecimal duckingVolume`.

- [x] Add evidence fields showing timed audio bed readiness and ducked mix mode.
- [x] Keep `READY` criteria as: segments exist, `NARRATION_AUDIO` exists, and `NARRATED_VIDEO` exists.
- [x] Add checks for `TIMED_AUDIO_BED` and `DUCKED_ORIGINAL_AUDIO` without exposing narration script text.
- [x] Update job evidence and handoff portal copy to say the final video preserves base/original audio with ducking.
- [x] Add tests for READY/ATTENTION behavior, Markdown fields, ZIP summaries, and forbidden marker absence.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 5: React Narration Mixing Controls And Status

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Extend `NarrationGeneration`, `NarratedVideoGeneration`, and `NarrationEvidence` with timed-audio and mix metadata.

- [x] Show `Timed audio bed` status after generating narration audio.
- [x] Show `Ducked original audio` status after generating narrated video.
- [x] Add compact evidence metrics for layout, mix mode, ducking volume, and narration windows.
- [x] Keep controls simple: no manual slider in this slice; ducking volume is fixed at `0.35` and shown as read-only evidence.
- [x] Keep UI consistent with the existing dense media-workflow style and avoid adding a landing page or decorative layout.
- [x] Add Vitest coverage for response parsing, visible timed-audio status, visible ducking status, and existing disabled states.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.
- [x] Update `docs/progress/execution-log.md`.

## Task 6: Demo Scripts And Documentation

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
- Preserve existing env: `LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO=true`.

- [ ] Extend narration evidence terminal summaries with `audioLayout`, `timeAligned`, `mixMode`, and `duckingVolume`.
- [ ] Update deterministic, OpenAI smoke, and full Tears scripts to print the mixed/ducked video result when generated.
- [ ] Document the browser workflow: save rows, generate timed narration audio, generate ducked narrated video, verify `NARRATION_AUDIO` and `NARRATED_VIDEO`.
- [ ] Update product docs to move audio ducking/mixing from future work to implemented status while keeping waveform and drag/drop timeline editing future.
- [ ] Add a decision record explaining why fixed ducking volume is used before adding manual audio controls.
- [ ] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Update `docs/progress/execution-log.md`.

## Task 7: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/134-narration-audio-mixing-ducking.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Mark completed plan tasks.
- [ ] Run focused backend validation from Tasks 1-4.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run script syntax validation from Task 6.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Add narration audio mixing and ducking`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=FfmpegTimedAudioBedServiceTests,NarrationAudioServiceTests`
- `mvn -pl LinguaFrame test -Dtest=FfmpegNarratedVideoMixServiceTests,NarratedVideoServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
