# Narration Timing Assistant Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete timing assistant for narration drafts so operators can detect, review, and locally fix gaps/overlaps before saving or generating narrated video.

**Architecture:** Add a pure frontend timing-assistant domain module that computes issues and returns non-mutating fixed segment arrays. Surface it in the React narration workspace as a local-only panel next to timeline editing, and add a terminal script that downloads workspace JSON and prints a safe timing preflight report. Existing backend save/generate APIs remain the persistence boundary.

**Tech Stack:** React + TypeScript + Vitest for the workbench, Bash + Python 3 for the demo script, existing Spring Boot APIs for workspace retrieval, existing docs/progress logs for evidence.

## Global Constraints

- This slice must be a complete feature: tested domain logic, browser UI, terminal preflight, docs, execution log, and merge-ready validation.
- Timing assistant actions are local-only until the user clicks the existing `Save narration` button.
- Do not call OpenAI/TTS providers, generate audio/video, create artifacts, or write object storage from assistant actions.
- Preserve segment text, voice, and durations unless an action explicitly resolves an overlap by shifting later rows.
- Never log raw user media paths, secrets, or OpenAI keys.

---

### Task 1: Timing Assistant Domain Model

**Files:**
- Create: `frontend/src/domain/narrationTimingAssistant.ts`
- Create: `frontend/src/domain/narrationTimingAssistant.test.ts`

**Interfaces:**
- Produces: `buildNarrationTimingAssistantReport(segments, options)` returning counts, issue rows, and action readiness.
- Produces: `closeNarrationDraftGaps(segments, options)`, `resolveNarrationDraftOverlaps(segments, options)`, and `normalizeNarrationDraftOrder(segments, options)`.
- Consumes: `NarrationWorkspace['segments']` from `frontend/src/domain/jobTypes.ts`.

- [ ] Write failing Vitest coverage for gap detection, overlap detection, preserving text/voice/duration, non-mutating input arrays, and row reindexing.
- [ ] Implement the helper with deterministic sorting by `startSeconds` then original `index`.
- [ ] Make gap closing pack rows with a configurable `targetGapSeconds` defaulting to `0.25`.
- [ ] Make overlap resolution shift the later row to `previousEnd + targetGapSeconds` while preserving its original duration.
- [ ] Run `npm test -- --run src/domain/narrationTimingAssistant.test.ts`.

### Task 2: React Timing Assistant Panel

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes Task 1 helpers.
- Produces: `NarrationTimingAssistantPanel` with issue summary, threshold controls, and local fix buttons.

- [ ] Add App tests that render the narration workspace, show assistant gap/overlap counts, click `Close gaps`, click `Resolve overlaps`, and verify the local draft/table/timeline update without saving.
- [ ] Import the timing assistant helpers into `App.tsx`.
- [ ] Add panel state for `targetGapSeconds` and `minimumReportGapSeconds` with bounded numeric inputs.
- [ ] Wire `Close gaps`, `Resolve overlaps`, and `Normalize order` through the existing `commitDraftChange` path so draft history and unsaved-change counts stay consistent.
- [ ] Style the panel with the existing compact narration workbench pattern; keep it dense and consistent with current narration controls.
- [ ] Run the focused App tests with `npm test -- --run src/App.test.tsx -t "narration timing assistant"`.

### Task 3: Terminal Timing Preflight

**Files:**
- Create: `scripts/demo/narration-timing-assistant.sh`
- Modify: `scripts/demo/README.md`

**Interfaces:**
- Consumes: `download_narration_workspace_json` from `scripts/demo/lib/linguaframe-demo.sh`.
- Produces: a read-only timing report saved under `/tmp/linguaframe-demo/narration-timing-assistant/`.

- [ ] Add a Bash script that accepts `LINGUAFRAME_DEMO_JOB_ID` or a first positional job id.
- [ ] Download narration workspace JSON and run embedded Python to print metadata only: segment count, gap count, total gap seconds, overlap count, longest gap, generation readiness, and suggested next action.
- [ ] Exit `2` before network calls when job id is missing.
- [ ] Keep report output free of narration text and raw local media paths.
- [ ] Document the script command and expected usage in `scripts/demo/README.md`.
- [ ] Run `bash -n scripts/demo/narration-timing-assistant.sh` and the missing-job-id validation.

### Task 4: Product Docs And Evidence

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes implementation behavior from Tasks 1-3.
- Produces updated contributor-visible demo and product documentation.

- [ ] Document where the timing assistant fits in the narration demo workflow.
- [ ] Update roadmap and target-state language to include local timing fixes and terminal timing preflight.
- [ ] Record the decision that timing fixes remain local draft edits until save.
- [ ] Record validation commands and outcomes in the execution log.

### Task 5: Full Validation And Merge

**Files:**
- No new files beyond Tasks 1-4.

**Interfaces:**
- Consumes all previous tasks.
- Produces a verified feature branch ready to merge back into `main`.

- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-timing-assistant.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit the complete feature slice.
- [ ] Switch to `main`, merge `narration-timing-assistant-workbench`, and confirm `git status --short --branch` is clean.

## Self-Review

- Spec coverage: domain logic, browser workflow, terminal preflight, docs, validation, and merge are covered.
- Placeholder scan: no deferred implementation placeholders remain.
- Scope check: the slice intentionally excludes provider calls, video generation, decoded waveform rendering, automation curves, uploaded reference audio, and backend persistence changes.
