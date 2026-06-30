# Narration Voice Audition Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators audition configured narration voice presets with custom sample text, then apply the chosen voice to selected or all narration rows before saving.

**Architecture:** Reuse the existing transient narration segment preview API so auditioning can call the configured TTS provider without creating artifacts, saving rows, updating evidence, generating video, or writing object storage. Add a compact React voice audition panel driven by the existing `voiceCatalog`, plus a terminal demo script that saves a local MP3 preview for one preset. Keep persisted narration rows unchanged until the operator explicitly saves the workspace.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, existing `previewNarrationSegment` API client, existing Spring Boot narration preview service tests, Bash demo helpers, Markdown docs.

## Global Constraints

- This is one complete feature slice: plan, browser voice audition workbench, terminal voice-audition script, tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-voice-audition-workbench`; do not include `/` in the user-facing branch title.
- Do not add backend routes, database columns, artifact records, object-storage writes, evidence updates, generated video, voice cloning, uploaded reference audio, or provider-specific tuning controls.
- Voice audition may call the configured TTS provider and may consume OpenAI credits.
- Voice audition must remain local-only until `Save narration`; applying a voice only changes browser draft rows.
- The audition panel must be compact and fit the existing workbench style; no landing-page or marketing layout.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Browser Voice Audition Workbench

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/150-narration-voice-audition-workbench.md`

**Interfaces:**
- Consumes:
  - `NarrationWorkspace['voiceCatalog']`
  - Existing `linguaFrameApi.previewNarrationSegment(jobId, { text, voice })`
  - Current local narration draft `segments`
- Produces:
  - A `Voice audition` panel in the narration workspace.
  - Local actions: preview selected preset, apply to selected row, apply to all rows.

- [x] Write failing App tests proving `Voice audition` renders configured presets and default sample text when a narration workspace is open.
- [x] Write failing App tests proving `Preview voice` calls `previewNarrationSegment` with the selected preset and custom audition text, renders an audio player, and shows the provider-credit warning.
- [x] Write failing App tests proving preview failure shows an error while leaving save/generate/evidence actions usable.
- [x] Write failing App tests proving `Apply to selected row` updates only the selected local draft row voice without calling `saveNarrationWorkspace`.
- [x] Write failing App tests proving `Apply to all rows` updates all local draft row voices without saving, generating audio, generating video, or refreshing evidence.
- [x] Run `npm test -- --run src/App.test.tsx -t "voice audition"` and verify tests fail because the panel does not exist.
- [x] Add local state for audition text, selected voice, preview status, preview error, and preview object URL.
- [x] Add `NarrationVoiceAuditionPanel` after the voice preset strip / row editor and before the existing preview/history panels.
- [x] Populate the voice select from `workspace.voiceCatalog.presets`, defaulting to `workspace.voiceCatalog.defaultVoice`.
- [x] Use a concise default audition text such as `This is a LinguaFrame narration voice preview.`
- [x] Implement preview by calling `previewNarrationSegment(jobId, { text: auditionText, voice: selectedVoice })`, revoking previous object URLs when replaced or unmounted.
- [x] Implement `Apply to selected row` through the existing local draft update path and keep draft history/validation/export in sync.
- [x] Implement `Apply to all rows` through the existing local draft update path and keep draft history/validation/export in sync.
- [x] Disable preview when no preset is selected or audition text is blank.
- [x] Disable apply buttons when there are no narration rows.
- [x] Style the panel with existing compact narration controls; avoid nested cards.
- [x] Run `npm test -- --run src/App.test.tsx -t "voice audition"` and verify it passes.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [x] Update execution log with RED/GREEN UI evidence.
- [ ] Commit with message `Add narration voice audition workbench`.

## Task 2: Terminal Voice Audition Script

**Files:**
- Create: `scripts/demo/narration-voice-audition.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/150-narration-voice-audition-workbench.md`

**Interfaces:**
- Consumes:
  - `LINGUAFRAME_DEMO_JOB_ID`
  - `LINGUAFRAME_NARRATION_AUDITION_VOICE`
  - `LINGUAFRAME_NARRATION_AUDITION_TEXT` or `LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE`
  - Existing demo base URL and demo-token helpers.
- Produces:
  - A local MP3 preview at `LINGUAFRAME_NARRATION_AUDITION_OUTPUT_PATH` or `/tmp/linguaframe-demo/narration-voice-audition.mp3`.
  - Safe terminal metadata: job id, voice, content type, bytes, output path, and provider-credit warning.

- [ ] Write the script skeleton with usage comments and strict shell settings.
- [ ] Add or reuse a helper that posts to `/api/jobs/{jobId}/narration-workspace/segment-preview` with text and explicit voice, downloads the response atomically, and prints safe metadata only.
- [ ] Reject missing job id, missing voice, and missing audition text before calling the API.
- [ ] Support text from `LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE` so longer samples do not need shell escaping.
- [ ] Keep raw API keys, provider payloads, object keys, local input media paths, transcript text, subtitle text, and narration script bodies out of terminal output.
- [ ] Run `bash -n scripts/demo/narration-voice-audition.sh scripts/demo/lib/linguaframe-demo.sh` and verify it passes.
- [ ] Run `LINGUAFRAME_DEMO_JOB_ID=job-demo scripts/demo/narration-voice-audition.sh` and verify it exits before the API call with a missing voice or missing text message.
- [ ] Update execution log with terminal script validation evidence.
- [ ] Commit with message `Add narration voice audition script`.

## Task 3: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/150-narration-voice-audition-workbench.md`

- [ ] Document browser order: open narration workspace, audition a preset with sample text, apply it to selected/all local rows, save only when ready, then generate audio/video explicitly.
- [ ] Document terminal order for `scripts/demo/narration-voice-audition.sh`, including required env vars, provider-credit warning, and local MP3 output.
- [ ] State that audition preview can call the configured provider, but it does not save rows, create artifacts, update evidence, generate video, or write object storage.
- [ ] Add a decision record explaining why this slice reuses transient segment preview before adding voice cloning, uploaded reference audio, or provider-specific voice tuning.
- [ ] Run `npm test -- --run src/App.test.tsx -t "voice audition"`.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests,NarrationVoiceCatalogServiceTests,NarrationWorkspaceServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-voice-audition.sh scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification evidence.
- [ ] Commit with message `Document narration voice audition workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/App.test.tsx -t "voice audition"`
- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationSegmentPreviewServiceTests,LocalizationJobControllerTests,NarrationVoiceCatalogServiceTests,NarrationWorkspaceServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-voice-audition.sh scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
