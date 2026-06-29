# Narration Editing Command Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete local editing commands to the time-coded narration editor so a user can duplicate, split, merge, and insert narration rows before saving or rendering.

**Architecture:** Keep this as a frontend editing feature over existing narration workspace state. Commands mutate only local draft rows until the user clicks the existing save action. The backend save, preflight, TTS, mix, and render contracts stay unchanged; this slice makes the editor closer to a usable narration workbench without adding provider calls or new persistence tables.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration regression tests, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: helper module, UI, focused tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-editing-command-workbench`; do not include `/` in the user-facing branch title.
- Do not add backend routes, database fields, OpenAI calls, TTS provider changes, object-storage writes, decoded-audio waveform rendering, voice cloning, uploaded reference audio, keyboard-shortcut help text, or multitrack automation curves in this slice.
- All edit commands must be deterministic, local-only, and reversible by reloading the workspace until the user explicitly saves.
- Keep row validation strict: inserted blank rows may exist locally, but saving must remain blocked until required text, voice, and timing fields are valid.
- UI style should stay compact and consistent with the existing LinguaFrame workbench: dense desktop layout, restrained borders, stable dimensions, no marketing/card-heavy redesign.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Narration Editing Command Helpers

**Files:**
- Create: `frontend/src/domain/narrationEditingCommands.ts`
- Test: `frontend/src/domain/narrationEditingCommands.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/145-narration-editing-command-workbench.md`

**Interfaces:**
- Produces:
  - `export interface NarrationEditCommandResult { segments: NarrationWorkspace['segments']; selectedIndex: number; blockedReason: string | null; }`
  - `export function duplicateNarrationSegment(segments: NarrationWorkspace['segments'], selectedIndex: number): NarrationEditCommandResult`
  - `export function splitNarrationSegmentAtTime(segments: NarrationWorkspace['segments'], selectedIndex: number, splitSeconds: number): NarrationEditCommandResult`
  - `export function mergeNarrationSegmentWithNext(segments: NarrationWorkspace['segments'], selectedIndex: number): NarrationEditCommandResult`
  - `export function insertNarrationSegmentAfter(segments: NarrationWorkspace['segments'], selectedIndex: number): NarrationEditCommandResult`
  - `export function reindexNarrationSegments(segments: NarrationWorkspace['segments']): NarrationWorkspace['segments']`
- Consumes: `NarrationWorkspace` from `frontend/src/domain/jobTypes.ts`.

- [x] Write failing tests proving duplicate inserts a copied row immediately after the selected row, preserves text/voice, shifts the copy after the original window, reindexes rows, and selects the copy.
- [x] Write failing tests proving split requires at least `0.25` seconds on both sides of the split point, splits the row into two windows, preserves voice, splits text near the midpoint, reindexes rows, and selects the second half.
- [x] Write failing tests proving merge combines the selected row with the next row, joins text with a blank line, preserves the selected row voice, spans from selected start to next end, reindexes rows, and keeps selection on the merged row.
- [x] Write failing tests proving insert creates a new blank local row after the selected row, starts at the selected row end, defaults to a five-second duration, reindexes rows, and selects the new row.
- [x] Write failing tests proving blocked commands return the original rows with a `blockedReason` instead of throwing.
- [x] Run `npm test -- --run src/domain/narrationEditingCommands.test.ts` and verify it fails because the helper module does not exist.
- [x] Implement command helpers with copied arrays only; do not mutate caller-owned segment objects.
- [x] Round generated timing values to `0.001` second precision and keep duration fields synchronized with start/end.
- [x] Run `npm test -- --run src/domain/narrationEditingCommands.test.ts` and verify it passes.
- [x] Update execution log with RED/GREEN evidence.
- [x] Commit with message `Add narration editing command helpers`.

## Task 2: Editing Command Bar UI

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/145-narration-editing-command-workbench.md`

**Interfaces:**
- Consumes:
  - `duplicateNarrationSegment(...)`
  - `splitNarrationSegmentAtTime(...)`
  - `mergeNarrationSegmentWithNext(...)`
  - `insertNarrationSegmentAfter(...)`
  - Existing `segments`, `selectedIndex`, `setSelectedIndex`, `previewCurrentSeconds`, validation, save, timeline, waveform, and preview state in `NarrationWorkspacePanel`.
- Produces:
  - A compact `Narration editing commands` region near the narration timeline/workspace controls.
  - Buttons for `Duplicate`, `Split at playhead`, `Merge next`, and `Insert after`.
  - A local command status message for successful commands and blocked-command reasons.

- [ ] Write failing App tests proving the command bar renders for a completed job with narration workspace data.
- [ ] Write failing App tests proving `Duplicate` adds a copied row after the selected row, selects it, and the existing save action sends the updated row list.
- [ ] Write failing App tests proving `Split at playhead` is disabled or blocked outside the selected window and succeeds when the preview playhead is inside the selected row.
- [ ] Write failing App tests proving `Merge next` combines adjacent rows and disables or blocks on the final row.
- [ ] Write failing App tests proving `Insert after` creates a blank local row and existing validation blocks saving until the user fills required fields.
- [ ] Run `npm test -- --run src/App.test.tsx -t "narration editing commands"` and verify the new tests fail.
- [ ] Add a small command panel that calls the helper module, updates local narration segment draft state, updates selected row state, and shows command status.
- [ ] Keep all commands local-only; do not call save, evidence refresh, preflight, TTS generation, mix, render, or artifact APIs.
- [ ] Disable clearly impossible commands from current selection state while still preserving helper-level blocked reasons for test coverage.
- [ ] Add compact CSS for command buttons and status text with stable height and no layout shift.
- [ ] Run `npm test -- --run src/App.test.tsx -t "narration editing commands"` and verify it passes.
- [ ] Run `npm test -- --run src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`.
- [ ] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration editing command UI`.

## Task 3: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/145-narration-editing-command-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document the browser order: open completed job, inspect narration rows, use editing commands locally, preview/scrub, save workspace, then generate audio/video only through explicit actions.
- [ ] State that duplicate, split, merge, and insert are local draft commands until save.
- [ ] State that inserted blank rows intentionally block save until filled, preserving backend validation.
- [ ] Add a decision record explaining why this slice adds deterministic editor commands before decoded waveform rendering or multitrack automation.
- [ ] Run `npm test -- --run src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification.
- [ ] Commit with message `Document narration editing command workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationEditingCommands.test.ts`
- `npm test -- --run src/App.test.tsx -t "narration editing commands"`
- `npm test -- --run src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
