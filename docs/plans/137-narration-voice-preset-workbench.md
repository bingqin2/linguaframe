# Narration Voice Preset Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make narration voice selection demo-ready by replacing free-form voice entry with a validated voice preset catalog, frontend voice controls, evidence summaries, and docs.

**Architecture:** Keep the persisted `voice` string on narration segments so no migration is needed. Add a backend catalog service that exposes provider-aware voice presets and default inheritance rules, then reuse that catalog for workspace responses, save validation, audio generation summaries, frontend controls, demo evidence, and documentation. This is a complete narration/TTS workflow slice; it does not add voice cloning, voice samples, waveform editing, or provider-specific advanced tuning.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing demo Bash scripts.

## Global Constraints

- This is one complete feature slice: backend voice catalog, save validation, generation/evidence summaries, frontend workbench controls, docs, tests, validation, commit, and merge back to `main`.
- Use branch title `narration-voice-preset-workbench`; do not include `/` in the user-facing branch title.
- Do not change the persisted narration segment schema unless tests prove the current `voice` string cannot support the workbench.
- Do not implement voice cloning, custom uploaded reference audio, voice preview playback, provider-specific fine-tuning controls, waveform editing, drag/drop timeline editing, or multitrack automation curves.
- Evidence and demo summaries must remain metadata-only and exclude narration script bodies, transcript text, subtitle text, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Keep the UI dense and workbench-like: compact select controls, right inspector summary, clear default-vs-custom voice state, and no marketing layout.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Voice Catalog And Workspace Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationVoicePresetVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationVoiceCatalogVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationVoiceCatalogService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationVoiceCatalogServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWorkspaceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationVoiceCatalogServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`

**Interfaces:**
- `NarrationVoicePresetVo(String voice, String label, String provider, boolean defaultPreset, String description)`
- `NarrationVoiceCatalogVo(String provider, String defaultVoice, List<NarrationVoicePresetVo> presets, List<String> safetyNotes)`
- Extend `NarrationWorkspaceVo` with `NarrationVoiceCatalogVo voiceCatalog`.

- [ ] Write failing service tests asserting demo/OpenAI catalog presets, configured default voice inheritance, and workspace JSON shape through `NarrationWorkspaceVo.voiceCatalog()`.
- [ ] Verify the tests fail because `NarrationVoiceCatalogService` and `NarrationWorkspaceVo.voiceCatalog()` do not exist.
- [ ] Implement `NarrationVoiceCatalogServiceImpl` using current `LinguaFrameProperties.tts.provider` and `linguaframe.tts.openai.voice`.
- [ ] Include a conservative OpenAI preset list such as `alloy`, `ash`, `ballad`, `coral`, `echo`, `fable`, `nova`, `onyx`, `sage`, `shimmer`, and `verse`, plus `demo-voice` for demo provider.
- [ ] Add `voiceCatalog` to workspace responses without changing saved segment schema.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationVoiceCatalogServiceTests,NarrationWorkspaceServiceTests`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration voice catalog`.

## Task 2: Voice Validation, Generation Summary, And Evidence

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationAudioServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationAudioServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`

**Interfaces:**
- `NarrationEvidenceVo` adds `voicePresetCount`, `voiceSummary`, and `defaultVoice`.
- Generation summary distinguishes `DEFAULT`, one named preset, and `MIXED`.

- [ ] Write failing tests for rejected unknown preset voices while allowing blank voice to inherit the job/config default.
- [ ] Write failing tests for generation `voiceSummary` when all rows inherit default, all rows use the same preset, and rows mix presets.
- [ ] Write failing evidence tests for voice metadata in JSON, Markdown, ZIP manifest, summary JSON, and terminal demo summaries.
- [ ] Implement validation against the catalog instead of accepting arbitrary non-empty voice strings.
- [ ] Update generation/evidence summaries without printing narration text.
- [ ] Extend demo shell output with metadata-only `narrationEvidenceVoiceSummary`, `narrationEvidenceDefaultVoice`, and `narrationEvidenceVoicePresetCount`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,NarrationAudioServiceTests,NarrationEvidenceServiceTests`.
- [ ] Run `bash -n scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Validate narration voice presets`.

## Task 3: React Voice Preset Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add frontend types `NarrationVoiceCatalog` and `NarrationVoicePreset`.
- `NarrationWorkspace.voiceCatalog` drives per-row voice selects and inspector voice metadata.

- [ ] Write failing Vitest coverage for visible voice catalog, default voice label, per-row voice select, selected segment voice summary, and blocked unknown voice display.
- [ ] Replace free-form row voice inputs with compact `<select>` controls populated by `voiceCatalog.presets`, including a default/inherit option.
- [ ] Show selected segment voice state in the right inspector: inherited default, explicit preset, or unknown saved value.
- [ ] Keep keyboard-friendly row editing and existing text inspector; do not add voice preview playback.
- [ ] Disable save/generate controls when an unknown saved voice is selected, while still allowing evidence refresh/downloads.
- [ ] Run `npm test -- --run src/App.test.tsx`.
- [ ] Run `npm run build`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration voice preset UI`.

## Task 4: Documentation And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/137-narration-voice-preset-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document the browser workflow: open narration workspace, choose inherited/default or explicit voice preset per segment, save narration, generate audio/video, verify voice metadata in evidence.
- [ ] Document that voice presets are provider-aware identifiers, not voice cloning or uploaded reference audio.
- [ ] Record the decision that this slice adds preset selection before voice preview/clone features.
- [ ] Run focused backend validations from Tasks 1-2.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Document narration voice preset workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationVoiceCatalogServiceTests,NarrationWorkspaceServiceTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,NarrationAudioServiceTests,NarrationEvidenceServiceTests`
- `npm test -- --run src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
