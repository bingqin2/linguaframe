# Narration Segment TTS Preview Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators preview TTS audio for one narration segment in the browser and terminal before saving, generating the full narration bed, or creating artifacts.

**Architecture:** Add a backend preview service and controller route that accepts one transient narration segment payload, validates it against the selected job and voice catalog, calls the existing `TtsProvider`, and streams the returned audio bytes directly. The frontend requests that blob from the selected row, creates a browser object URL, and plays it in a compact `Narration TTS preview` panel near the existing narration preview/history controls. A terminal script uses the same route to save a local preview MP3 for demo evidence without creating backend artifacts. No database rows, artifacts, object storage files, script packages, evidence files, or narrated videos are created by preview.

**Tech Stack:** Java 21, Spring Boot MVC, existing `TtsProvider` boundary, React + Vite + TypeScript, Vitest/jsdom, JUnit 5, existing narration demo scripts.

## Global Constraints

- This is one complete feature slice: backend preview contract, frontend API/client, UI, terminal demo script, tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-segment-tts-preview-workbench`; do not include `/` in the user-facing branch title.
- Preview is transient and must not write narration rows, create `NARRATION_AUDIO`, create `NARRATED_VIDEO`, create artifacts, write object storage, update evidence, update script packages, or dispatch worker jobs.
- Preview may call the configured TTS provider and can consume OpenAI credits when `LINGUAFRAME_TTS_PROVIDER=openai`; UI copy must make this explicit.
- Terminal preview may call the configured TTS provider and can consume OpenAI credits; script output must make this explicit before writing the MP3 path.
- Preview uses the selected draft row values, including unsaved edits, explicit voice preset, or inherited job/default voice.
- Preview validates blank text, invalid voice presets, excessive voice length, and missing jobs before calling the provider.
- Preview does not require start/end timing to be valid because it only synthesizes selected text; full save/generation validation continues to enforce timing.
- Do not add voice cloning, uploaded reference audio, provider voice browser, persistent preview cache, waveform decoding, or multitrack automation in this slice.
- Keep UI compact and consistent with the existing LinguaFrame narration workbench.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Segment Preview Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/PreviewNarrationSegmentRequestDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationSegmentPreviewVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationSegmentPreviewService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationSegmentPreviewServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationSegmentPreviewServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/147-narration-segment-tts-preview-workbench.md`

**Interfaces:**
- Consumes:
  - `TtsProvider.synthesize(TtsRequestBo request)`
  - `LocalizationJobQueryService.getJob(String jobId)`
  - `NarrationVoiceCatalogService.defaultVoice()`
  - `NarrationVoiceCatalogService.voiceCatalog()`
- Produces:
  - `PreviewNarrationSegmentRequestDto(String text, String voice)`
  - `NarrationSegmentPreviewVo(byte[] audioContent, String filename, String contentType, String voice, int characterCount, String safetyNote)`
  - `NarrationSegmentPreviewService.previewSegment(String jobId, PreviewNarrationSegmentRequestDto request)`
  - `POST /api/jobs/{jobId}/narration-workspace/segment-preview` returning audio bytes with `Content-Type` and `Content-Disposition`.

- [ ] Write failing service tests proving blank text is rejected before `TtsProvider` is called.
- [ ] Write failing service tests proving explicit valid voice is passed to `TtsProvider` with job target language and trimmed text.
- [ ] Write failing service tests proving blank voice inherits the job `ttsVoice`, then falls back to the voice catalog default.
- [ ] Write failing service tests proving unknown voice presets and voices longer than 64 characters are rejected before provider calls.
- [ ] Write failing service tests proving the returned preview uses provider audio bytes, content type, filename `narration-segment-preview.mp3`, character count, and a cost warning safety note.
- [ ] Write failing controller tests proving `POST /api/jobs/{jobId}/narration-workspace/segment-preview` returns `audio/mpeg`, `Content-Disposition: inline; filename="narration-segment-preview.mp3"`, and provider bytes.
- [ ] Write failing controller tests proving validation failures return `400` and do not create job artifacts.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests` and verify the new tests fail because preview classes/routes do not exist.
- [ ] Implement `PreviewNarrationSegmentRequestDto` with `text` and `voice` fields.
- [ ] Implement `NarrationSegmentPreviewVo` as a record carrying preview bytes and safe metadata.
- [ ] Implement `NarrationSegmentPreviewServiceImpl` validation:
  - Trim `text`; reject blank with `Narration preview text is required.`
  - Trim `voice`; reject length greater than 64 with `Narration preview voice must be 64 characters or fewer.`
  - Reject unknown explicit voice when not present in `voiceCatalog().presets()`.
  - Resolve effective voice from explicit voice, job `ttsVoice`, or `defaultVoice()`.
  - Call `ttsProvider.synthesize(new TtsRequestBo(jobId, job.targetLanguage(), effectiveVoice, trimmedText))`.
  - Return provider bytes, provider content type or `audio/mpeg`, filename `narration-segment-preview.mp3`, effective voice, text length, and safety note `Preview calls the configured TTS provider but does not create artifacts.`
- [ ] Add controller route with OpenAPI summary `Preview TTS audio for one unsaved narration segment`.
- [ ] Return `ResponseEntity<byte[]>` with inline content disposition, content type from preview result, and no persistence side effects.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests` and verify it passes.
- [ ] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration segment TTS preview API`.

## Task 2: Frontend Preview Client And Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/147-narration-segment-tts-preview-workbench.md`

**Interfaces:**
- Consumes:
  - `previewNarrationSegment(jobId: string, request: PreviewNarrationSegmentRequest): Promise<Blob>`
  - Current `NarrationWorkspacePanel` `segments`, `selectedIndex`, `selectedSegment`, `voiceCatalog`, and draft validation.
- Produces:
  - `export interface PreviewNarrationSegmentRequest { text: string; voice: string | null; }`
  - A compact `Narration TTS preview` panel with `Preview selected TTS`, effective voice copy, provider cost warning, status/error text, and an `<audio controls>` player.

- [ ] Write failing API tests proving `previewNarrationSegment('job-narration', { text: 'Hello', voice: 'alloy' })` posts JSON to `/api/jobs/job-narration/narration-workspace/segment-preview` and returns a blob.
- [ ] Write failing App tests proving the narration workspace renders `Narration TTS preview` for a completed job.
- [ ] Write failing App tests proving `Preview selected TTS` sends the selected draft row text and explicit voice, then renders an audio player using a blob URL.
- [ ] Write failing App tests proving unsaved text edits are included in the preview request without calling `saveNarrationWorkspace`.
- [ ] Write failing App tests proving blank selected text disables preview and shows the same validation guidance without calling the API.
- [ ] Write failing App tests proving a rejected preview shows an error message and keeps save/generate controls usable.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration TTS preview"` and verify the new tests fail.
- [ ] Add `PreviewNarrationSegmentRequest` to `frontend/src/domain/jobTypes.ts`.
- [ ] Add `previewNarrationSegment(...)` to `frontend/src/api/linguaframeApi.ts` using existing `requestBlob`.
- [ ] Export `previewNarrationSegment` from `linguaFrameApi`.
- [ ] Add `NarrationTtsPreviewPanel` inside `NarrationWorkspacePanel`, placed after `NarrationDraftHistoryPanel` and before `NarrationPreviewPanel`.
- [ ] Keep panel state local: `isPreviewingSegment`, `segmentPreviewUrl`, `segmentPreviewStatus`, `segmentPreviewError`.
- [ ] Revoke old object URLs with `URL.revokeObjectURL` when replacing preview audio and when workspace/selected row changes.
- [ ] Disable preview when no selected row, blank selected text, unknown selected voice, or a preview request is in flight.
- [ ] On preview click, call `previewNarrationSegment(jobId, { text: selectedSegment.text, voice: selectedSegment.voice ?? null })`, create an object URL, and render `<audio controls src={segmentPreviewUrl} aria-label="Narration TTS preview player" />`.
- [ ] Display cost warning text: `Preview calls the configured TTS provider and may consume credits; it does not save rows or create artifacts.`
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration TTS preview"` and verify it passes.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/domain/narrationDraftHistory.test.ts src/App.test.tsx`.
- [ ] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration TTS preview workbench`.

## Task 3: Terminal Segment Preview Script

**Files:**
- Create: `scripts/demo/narration-segment-preview.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/147-narration-segment-tts-preview-workbench.md`

**Interfaces:**
- Consumes:
  - `POST /api/jobs/{jobId}/narration-workspace/segment-preview`
  - Existing demo env conventions: `LINGUAFRAME_API_BASE_URL`, `LINGUAFRAME_DEMO_TOKEN`, `LINGUAFRAME_DEMO_JOB_ID`
- Produces:
  - `/tmp/linguaframe-demo/narration-segment-preview/narration-segment-preview.mp3`
  - `/tmp/linguaframe-demo/narration-segment-preview/narration-segment-preview-request.json`
  - Safe terminal summary lines: `narrationSegmentPreviewJobId`, `narrationSegmentPreviewVoice`, `narrationSegmentPreviewCharacters`, `narrationSegmentPreviewOutputPath`, `narrationSegmentPreviewProviderCostWarning`.

- [ ] Write failing shell syntax validation entry by adding `scripts/demo/narration-segment-preview.sh` to the planned `bash -n` command before the file exists.
- [ ] Implement script arguments and env:
  - `LINGUAFRAME_DEMO_JOB_ID` is required.
  - `LINGUAFRAME_NARRATION_PREVIEW_TEXT` is required unless `LINGUAFRAME_NARRATION_PREVIEW_TEXT_FILE` points to a readable file.
  - `LINGUAFRAME_NARRATION_PREVIEW_VOICE` is optional and may be blank to inherit default/job voice.
  - `LINGUAFRAME_NARRATION_PREVIEW_OUTPUT_DIR` defaults to `/tmp/linguaframe-demo/narration-segment-preview`.
- [ ] Build request JSON with structured escaping rather than shell string concatenation.
- [ ] Call the preview route with existing demo-token header support from `linguaframe-demo.sh`.
- [ ] Save MP3 output and request JSON under the output directory.
- [ ] Print safe metadata only; do not print raw local env values, API keys, bearer tokens, provider payloads, or object keys.
- [ ] Make failed HTTP responses print a concise error and exit non-zero without leaving partial MP3 output as success.
- [ ] Run `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/lib/linguaframe-demo.sh` and verify it passes.
- [ ] Update execution log with script validation evidence.
- [ ] Commit with message `Add narration segment preview demo script`.

## Task 4: Docs, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/147-narration-segment-tts-preview-workbench.md`

- [ ] Document the browser order: edit/select a narration row, preview TTS for that row, adjust text/voice locally, save only when ready, then generate full narration audio/video through explicit actions.
- [ ] Document the terminal order: set `LINGUAFRAME_DEMO_JOB_ID`, provide preview text or text file, optionally provide voice, run `scripts/demo/narration-segment-preview.sh`, then listen to the local MP3 before saving/generating full narration.
- [ ] State that segment preview calls the configured TTS provider and can consume OpenAI credits.
- [ ] State that segment preview is transient and never creates artifacts, evidence packages, object-storage files, saved rows, or narrated videos.
- [ ] Add a decision record explaining why this slice adds segment-level TTS preview before voice cloning, uploaded reference audio, persistent preview cache, or provider voice browsing.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests,NarrationAudioServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/domain/narrationDraftHistory.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification.
- [ ] Commit with message `Document narration TTS preview workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests,NarrationAudioServiceTests`
- `mvn -pl LinguaFrame test`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration TTS preview"`
- `npm test -- --run src/api/linguaframeApi.test.ts src/domain/narrationDraftHistory.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
