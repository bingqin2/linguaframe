# Narration Demo Preset Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reusable narration demo presets that connect an existing demo run profile to a ready-to-apply time-coded narration script package, so a completed demo job can be turned into a narrated showcase without manually retyping segments.

**Architecture:** Keep demo run profiles as upload-time localization presets and keep narration workspace rows as the source of truth after upload. Add a read-only narration preset catalog plus an apply endpoint that validates the preset against the selected job duration, imports its script through the existing narration script package path, saves mix settings, and returns refreshed workspace/package/evidence summaries. The browser and terminal surfaces expose this as a guided post-processing step, not as a new TTS provider or media editor.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: backend preset catalog, apply endpoint, frontend workbench controls, terminal script, docs, focused tests, full validation, commits, and merge back to `main`.
- Use branch title `narration-demo-preset-package`; do not include `/` in the user-facing branch title.
- Presets may include operator-authored narration text because applying them restores a script, but safe summaries, evidence reports, and terminal metadata must not expose transcript text, subtitle text, object keys, local paths, provider payloads, tokens, API keys, or media bytes.
- Do not implement voice cloning, uploaded reference audio, voice preview playback, waveform editing, drag/drop timeline editing, or automatic generation during upload.
- Applying a preset must validate duration bounds, overlaps, text length, voice presets, and `replaceExisting=true` before replacing the current narration workspace.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Narration Preset Catalog

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/demo/domain/vo/NarrationDemoPresetVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/demo/domain/vo/NarrationDemoPresetSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/demo/domain/vo/NarrationDemoPresetMixSettingsVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/demo/service/NarrationDemoPresetService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/demo/service/impl/InMemoryNarrationDemoPresetService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/demo/controller/DemoRunProfileController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/demo/service/NarrationDemoPresetServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/demo/controller/DemoRunProfileControllerTests.java`

**Interfaces:**
- `GET /api/demo-run-profiles/narration-presets`
- `GET /api/demo-run-profiles/{profileId}/narration-preset`

- [ ] Add a built-in `tears-showcase-narration` preset linked to `tears-showcase`, with 3-5 time-coded explanatory segments within the 5-minute upload limit and conservative mix settings.
- [ ] Include labels, profile id, sample id hint, target language, voice summary, segment count, total characters, time span, mix settings, safety notes, and segment rows.
- [ ] Return empty optional behavior for profiles without narration presets instead of failing.
- [ ] Add service tests for listing presets, profile lookup, unknown profile handling, duration ordering, and no unsafe local paths or keys in catalog metadata.
- [ ] Add controller tests for both routes.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoPresetServiceTests,DemoRunProfileControllerTests`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration demo preset catalog`.

## Task 2: Apply Preset To Job Through Script Package Import

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ApplyNarrationDemoPresetDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationDemoPresetApplyVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationDemoPresetApplyService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationDemoPresetApplyServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationDemoPresetApplyServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-demo-preset/apply`
- Request: `ApplyNarrationDemoPresetDto(String presetId, boolean replaceExisting)`
- Response: `NarrationDemoPresetApplyVo` with preset id, imported segment count, voice summary, refreshed workspace, refreshed script package checks, and narration evidence status.

- [ ] Write failing tests for successful apply, rejected missing `replaceExisting=true`, unknown preset, too-short source duration, invalid voice, and preservation of existing workspace on failure.
- [ ] Build an `ImportNarrationScriptPackageDto` from the selected preset and reuse `NarrationScriptPackageService.importPackage` for validation and replacement.
- [ ] Refresh and return workspace, script package, and narration evidence metadata after successful apply.
- [ ] Keep preset application separate from `generate-audio` and `generate-video`; no provider calls happen in this endpoint.
- [ ] Add controller tests for route encoding and validation error bodies.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoPresetApplyServiceTests,LocalizationJobControllerTests,NarrationScriptPackageServiceTests`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Apply narration demo presets to jobs`.

## Task 3: React Preset Workbench Integration

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `listNarrationDemoPresets(): Promise<NarrationDemoPreset[]>`
- `getNarrationDemoPreset(profileId: string): Promise<NarrationDemoPreset | null>`
- `applyNarrationDemoPreset(jobId: string, request: ApplyNarrationDemoPresetRequest): Promise<NarrationDemoPresetApplyResult>`

- [ ] Add TypeScript types and API helper tests for listing, profile lookup, and apply.
- [ ] Add a compact `Demo narration preset` panel inside the selected job narration workspace.
- [ ] Show linked profile, sample hint, segment count, total duration, voice summary, mix settings, and safe apply checks without exposing unrelated evidence text.
- [ ] Require explicit replace acknowledgement before applying a preset over an existing workspace.
- [ ] On success, refresh narration workspace, script package, narration evidence, and artifacts without page reload.
- [ ] Keep generate audio/video as separate user actions after the preset is applied.
- [ ] Add Vitest coverage for preset rendering, disabled apply states, successful apply refresh, validation error display, and preserved manual script package import behavior.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration demo preset UI`.

## Task 4: Terminal Demo Script, Docs, And Final Verification

**Files:**
- Create: `scripts/demo/narration-demo-preset.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/139-narration-demo-preset-package.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Add `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-preset.sh` to list presets, apply the recommended preset with explicit replace mode, and download refreshed narration script/evidence summaries.
- [ ] Add dry-run/report-only mode that lists recommended preset metadata without mutating a job.
- [ ] Extend the full Tears script to optionally apply the preset before narration evidence export when `LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true`.
- [ ] Document the run order: upload full demo, apply narration preset, generate narration audio/video, export narration evidence, then run acceptance/completion/handoff packages.
- [ ] Update smoke checklist with browser and terminal expectations for preset apply, replace confirmation, separate generation, and metadata safety exclusions.
- [ ] Run focused backend validations from Tasks 1-2.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Document narration demo preset package`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationDemoPresetServiceTests,DemoRunProfileControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationDemoPresetApplyServiceTests,LocalizationJobControllerTests,NarrationScriptPackageServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
