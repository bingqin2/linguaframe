# Narration Mix Automation Curves Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a compact narration mix automation workbench that visualizes effective ducking, narration volume, and fade values across the current narration timeline and lets operators safely apply or reset segment-level mix overrides.

**Architecture:** Reuse the existing persisted job-level mix settings and nullable per-segment overrides; do not add a new automation-curve schema. Add a pure frontend helper that derives effective automation points from the local draft, a React panel that renders curve rows and batch actions, and a terminal metadata report that summarizes effective mix windows from the existing narration workspace API.

**Tech Stack:** React + TypeScript + Vitest, existing Spring Boot narration workspace APIs, Bash demo scripts, Python JSON parsing in `scripts/demo/lib/linguaframe-demo.sh`.

## Global Constraints

- This is one complete feature slice: frontend domain helper, workbench UI, local batch actions, tests, terminal report, docs, validation, commit, and merge back to `main`.
- Do not call OpenAI, TTS providers, translation providers, quality evaluation providers, or any paid provider.
- Do not generate audio/video artifacts, save narration rows automatically, update evidence, or mutate object storage from automation preview actions.
- Persist changes only through the existing `Save narration` button and existing save payload.
- Keep automation derived from existing values: job-level `duckingVolume`, `narrationVolume`, `fadeDurationMs`, and per-segment nullable overrides.
- UI style must stay dense and workbench-like, with restrained panels inspired by the remembered `fish-tts-desktop` layout direction.
- Terminal output must be metadata-only: no narration text, raw transcript/subtitle text, object keys, local paths, provider payloads, tokens, or API keys.

---

### Task 1: Frontend Automation Curve Domain Helper

**Files:**
- Create: `frontend/src/domain/narrationMixAutomation.ts`
- Create: `frontend/src/domain/narrationMixAutomation.test.ts`

**Interfaces:**
- `buildNarrationMixAutomation(input): NarrationMixAutomation`
- `NarrationMixAutomationPoint`: `{ index, startSeconds, endSeconds, duckingVolume, narrationVolume, fadeDurationMs, hasOverride, selected }`
- `NarrationMixAutomation`: `{ points, overrideCount, inheritedCount, minDuckingVolume, maxNarrationVolume, maxFadeDurationMs }`

- [x] Add failing tests proving inherited values come from job-level mix settings when segment overrides are blank.
- [x] Add failing tests proving explicit segment overrides replace only their own effective values.
- [x] Add failing tests proving summary counts and min/max metrics are deterministic for empty, inherited, and mixed override timelines.
- [x] Implement the pure helper with no React, DOM, API, provider, storage, or clock dependencies.
- [x] Run `npm --prefix frontend test -- --run src/domain/narrationMixAutomation.test.ts`.

### Task 2: Browser Automation Curves Workbench

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `NarrationMixAutomationPanel` inside `NarrationWorkspacePanel` near `Segment mix overrides`.
- Panel consumes local `segments`, `mixSettings`, `selectedIndex`, and `onUpdateSegment`.
- Local actions:
  - `Apply selected mix to all rows`
  - `Clear selected row overrides`
  - `Clear all row overrides`

- [x] Add App tests proving the panel renders effective ducking, narration, fade, inherited/override state, and selected row state.
- [x] Add App tests proving `Apply selected mix to all rows` updates local draft rows only and does not call `saveNarrationWorkspace`, `generateNarrationAudio`, or `generateNarratedVideo`.
- [x] Add App tests proving `Clear selected row overrides` and `Clear all row overrides` keep job-level mix settings intact.
- [x] Implement the panel with compact curve bars for ducking, narration, and fade using stable dimensions and accessible labels.
- [x] Wire actions through the existing draft-history update path so undo/revert still works.
- [x] Run `npm --prefix frontend test -- --run src/domain/narrationMixAutomation.test.ts src/App.test.tsx -t "mix automation"`.

### Task 3: Terminal Metadata Report

**Files:**
- Create: `scripts/demo/narration-mix-automation.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/README.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-mix-automation.sh`
- Output keys: `narrationMixAutomationSegmentCount`, `narrationMixAutomationOverrideCount`, `narrationMixAutomationInheritedCount`, `narrationMixAutomationMinDuckingVolume`, `narrationMixAutomationMaxNarrationVolume`, `narrationMixAutomationMaxFadeDurationMs`.

- [x] Add script helper to download narration workspace JSON using existing demo auth/header handling.
- [x] Add Python summary logic that computes effective values without printing text bodies or paths.
- [x] Add the executable demo script and document safe usage.
- [x] Run `bash -n scripts/demo/narration-mix-automation.sh scripts/demo/lib/linguaframe-demo.sh`.

### Task 4: Documentation And Decisions

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/154-narration-mix-automation-curves-workbench.md`

- [x] Document browser order: open completed job, inspect decoded/fallback waveform, inspect mix automation curves, adjust selected overrides or batch actions locally, save narration only when ready, then explicitly generate/render.
- [x] Document that automation curves are derived from existing settings and segment overrides, not a new persisted curve editor.
- [x] Record a decision explaining why this adds automation visualization and batch local actions before persistent curve keyframes.
- [x] Append focused and final validation results to `docs/progress/execution-log.md`.

### Task 5: Full Validation, Commit, And Merge

**Files:**
- No new files beyond Tasks 1-4.

- [x] Run focused frontend validations from Tasks 1-2.
- [x] Run `npm --prefix frontend test -- --run`.
- [x] Run `npm --prefix frontend run build`.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `bash -n scripts/demo/narration-mix-automation.sh scripts/demo/narration-waveform.sh scripts/demo/narration-timing-assistant.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Commit with message `Add narration mix automation curves workbench`.
- [x] Switch to `main`, merge `narration-mix-automation-curves-workbench`, and confirm `git status --short --branch` is clean.

## Self-Review

- Spec coverage: frontend automation derivation, browser workbench, local batch actions, terminal metadata report, docs, validation, commit, and merge are covered.
- Placeholder scan: no deferred implementation placeholders remain.
- Scope check: this slice intentionally excludes persistent keyframes, waveform editing, audio re-render-on-drag, voice cloning, uploaded reference audio, and full multitrack editing.
