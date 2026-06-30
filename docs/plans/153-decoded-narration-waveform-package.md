# Decoded Narration Waveform Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real decoded-audio waveform package for narration review while preserving the current metadata-derived waveform as a safe fallback.

**Architecture:** Introduce a backend FFmpeg-powered waveform analyzer that reads an existing safe media source for a job, emits bounded numeric peak buckets, and exposes them through a read-only narration waveform API. The React narration workbench will load this waveform, show whether it came from decoded audio or metadata fallback, and keep all scrubbing local to the browser preview player.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, existing artifact/object-storage services, FFmpeg/ffprobe command execution, JUnit 5 + MockMvc, React + TypeScript + Vitest, Bash demo scripts.

## Global Constraints

- This is one complete feature slice: backend analyzer, source selection, API, React integration, terminal export, docs, tests, validation, commit, and merge back to `main`.
- Do not call OpenAI, TTS providers, translation providers, quality evaluation providers, or any paid provider.
- Do not create new media artifacts, upload objects, mutate narration rows, update evidence, or save object-storage keys from waveform reads.
- The waveform response must contain only metadata and numeric buckets: no transcript text, subtitle text, narration text, provider payloads, object keys, local paths, tokens, or API keys.
- Prefer decoded sources in this order: `NARRATION_AUDIO`, `NARRATED_VIDEO`, `BURNED_VIDEO`, then uploaded source media.
- If decoded waveform generation is unavailable or fails safely, the browser must keep using the existing metadata-derived waveform overview.
- Keep bucket output bounded and deterministic: default 96 buckets, each bucket has `index`, `startSeconds`, `endSeconds`, `peak`, and `rms` in `0.0-1.0`.
- UI style should stay compact and workbench-like, consistent with the existing narration editor.

---

### Task 1: Backend Waveform Domain And FFmpeg Analyzer

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/AudioWaveformAnalyzeCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/AudioWaveformBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/AudioWaveformBucketBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegAudioWaveformService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegAudioWaveformServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegAudioWaveformServiceTests.java`

**Interfaces:**
- `AudioWaveformAnalyzeCommand(Path inputMediaPath, int bucketCount, double durationSeconds)`
- `AudioWaveformBucketBo(int index, BigDecimal startSeconds, BigDecimal endSeconds, BigDecimal peak, BigDecimal rms)`
- `AudioWaveformBo(int bucketCount, BigDecimal durationSeconds, List<AudioWaveformBucketBo> buckets)`
- `FfmpegAudioWaveformService.analyze(AudioWaveformAnalyzeCommand command): AudioWaveformBo`

- [x] Add tests for parsing deterministic FFmpeg `astats`/raw PCM command output into 96 bounded buckets.
- [x] Add tests for clamping invalid amplitudes to `0.0-1.0`.
- [x] Add tests for timeout and non-zero FFmpeg exit producing safe `IllegalStateException` messages without local paths.
- [x] Implement the analyzer with injectable `CommandRunner` following existing FFmpeg service patterns.
- [x] Keep temporary decoded audio files under `MediaWorkDirectoryService`-managed directories when needed.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegAudioWaveformServiceTests`.

### Task 2: Job-Level Waveform Source Selection And API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/NarrationWaveformSourceType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWaveformBucketVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWaveformVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationWaveformService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWaveformServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWaveformServiceTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/narration-waveform?bucketCount=96`
- `NarrationWaveformVo(String jobId, String status, String sourceType, int bucketCount, BigDecimal durationSeconds, List<NarrationWaveformBucketVo> buckets, String fallbackReason)`
- `status` is `READY`, `UNAVAILABLE`, or `FAILED_SAFE`.

- [x] Add tests proving source selection prefers `NARRATION_AUDIO`, then `NARRATED_VIDEO`, then `BURNED_VIDEO`, then source media.
- [x] Add tests proving unavailable jobs return `UNAVAILABLE` with empty buckets and no exception.
- [x] Add tests proving analyzer failures return `FAILED_SAFE` with safe fallback reason and no local path/object key.
- [x] Add controller tests for the JSON route, bucket count bounds, and private-demo access compatibility.
- [x] Implement source download/open logic using existing artifact/source services without creating new artifacts.
- [x] Cap requested bucket count to `24-192`; default to `96`.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWaveformServiceTests,LocalizationJobControllerTests`.

### Task 3: Frontend Waveform Loading And Fallback

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `NarrationWaveform`, `NarrationWaveformBucket`, and `getNarrationWaveform(jobId, bucketCount?)`.
- Existing `NarrationWaveformOverviewPanel` consumes decoded buckets when `status === "READY"`; otherwise it uses `buildNarrationWaveformOverview(...)`.

- [x] Add API tests for `GET /api/jobs/{jobId}/narration-waveform?bucketCount=96`.
- [x] Add App tests proving decoded waveform buckets render when the API returns `READY`.
- [x] Add App tests proving the panel shows fallback mode when the API returns `UNAVAILABLE` or request fails.
- [x] Add App tests proving decoded waveform scrubbing still only seeks preview state and does not save/generate.
- [x] Add compact UI labels: `Decoded waveform`, `Metadata fallback`, source type, bucket count, and safe fallback reason.
- [x] Keep the existing 48-bucket metadata overview as fallback; decoded API can render 96 narrow buckets with stable dimensions.
- [x] Run `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration waveform"`.
- [x] Run `npm --prefix frontend run build`.

### Task 4: Terminal Export And Documentation

**Files:**
- Create: `scripts/demo/narration-waveform.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/153-decoded-narration-waveform-package.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-waveform.sh`
- Terminal output prints only status, source type, bucket count, duration, peak max, RMS average, and fallback reason.

- [x] Add script tests through `bash -n`.
- [x] Document browser order: open completed job, load narration waveform, inspect decoded source/fallback mode, scrub locally, then save/render only through explicit actions.
- [x] Document that decoded waveform reads existing media only and does not call providers or create artifacts.
- [x] Record a decision explaining why decoded waveform buckets come before multitrack automation curves.
- [x] Append focused and final validation results to `docs/progress/execution-log.md`.

### Task 5: Full Validation, Commit, And Merge

**Files:**
- No new files beyond Tasks 1-4.

- [x] Run focused backend validations from Tasks 1-2.
- [x] Run focused frontend validations from Task 3.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `npm --prefix frontend test -- --run`.
- [x] Run `npm --prefix frontend run build`.
- [x] Run `bash -n scripts/demo/narration-waveform.sh scripts/demo/narration-timing-assistant.sh scripts/demo/narration-evidence.sh scripts/demo/narration-script-package.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Commit with message `Add decoded narration waveform package`.
- [x] Switch to `main`, merge `decoded-narration-waveform-package`, and confirm `git status --short --branch` is clean.

## Self-Review

- Spec coverage: backend decoded waveform generation, safe API, frontend fallback behavior, terminal export, docs, validation, and merge are covered.
- Placeholder scan: no deferred implementation placeholders remain.
- Scope check: this slice intentionally excludes waveform editing, automation curves, voice cloning, uploaded reference audio, and persistent waveform artifacts.
