# Narration Draft History Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add undo, redo, revert-to-saved, and unsaved-change summary support to the narration editor so operators can safely shape timed explanatory voiceover drafts before saving.

**Architecture:** Keep this as a frontend draft-state feature over the existing narration workspace contract. The browser owns local history snapshots until `Save narration`; backend APIs, persistence schema, TTS generation, evidence, preflight, and render contracts remain unchanged. History wraps existing row edits, timeline edits, and editing commands so the table, timeline, waveform, preview, inspector, validation, and save payload all read from the same current draft.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration regression tests, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: helper module, UI integration, focused tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-draft-history-workbench`; do not include `/` in the user-facing branch title.
- Do not add backend routes, database fields, browser localStorage persistence, OpenAI calls, TTS provider changes, object-storage writes, decoded-audio waveform rendering, voice cloning, uploaded reference audio, voice preview playback, or multitrack automation curves in this slice.
- Draft history is in-memory only and resets when a different workspace is loaded or when the backend returns a saved workspace.
- Undo, redo, and revert must never call save, evidence refresh, preflight, TTS generation, mix saving, render, artifact APIs, or provider APIs.
- UI style should stay compact and consistent with the existing LinguaFrame workbench: dense desktop layout, restrained borders, stable dimensions, no marketing/card-heavy redesign.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Narration Draft History Helper

**Files:**
- Create: `frontend/src/domain/narrationDraftHistory.ts`
- Test: `frontend/src/domain/narrationDraftHistory.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/146-narration-draft-history-workbench.md`

**Interfaces:**
- Produces:
  - `export interface NarrationDraftHistoryState { saved: NarrationWorkspace['segments']; past: NarrationWorkspace['segments'][]; present: NarrationWorkspace['segments']; future: NarrationWorkspace['segments'][]; lastActionLabel: string | null; }`
  - `export interface NarrationDraftChangeSummary { dirty: boolean; addedCount: number; removedCount: number; timingChangedCount: number; textChangedCount: number; voiceChangedCount: number; changedRowLabels: string[]; }`
  - `export function createNarrationDraftHistory(segments: NarrationWorkspace['segments']): NarrationDraftHistoryState`
  - `export function applyNarrationDraftChange(state: NarrationDraftHistoryState, nextSegments: NarrationWorkspace['segments'], actionLabel: string): NarrationDraftHistoryState`
  - `export function undoNarrationDraftChange(state: NarrationDraftHistoryState): NarrationDraftHistoryState`
  - `export function redoNarrationDraftChange(state: NarrationDraftHistoryState): NarrationDraftHistoryState`
  - `export function resetNarrationDraftToSaved(state: NarrationDraftHistoryState): NarrationDraftHistoryState`
  - `export function markNarrationDraftSaved(state: NarrationDraftHistoryState, savedSegments: NarrationWorkspace['segments']): NarrationDraftHistoryState`
  - `export function summarizeNarrationDraftChanges(saved: NarrationWorkspace['segments'], present: NarrationWorkspace['segments']): NarrationDraftChangeSummary`
- Consumes: `NarrationWorkspace` from `frontend/src/domain/jobTypes.ts`.

- [ ] Write failing tests proving `createNarrationDraftHistory` copies saved/present arrays and starts with empty past/future stacks.
- [ ] Write failing tests proving `applyNarrationDraftChange` pushes the previous present snapshot to `past`, replaces `present`, clears `future`, stores the action label, and never mutates caller-owned segment objects.
- [ ] Write failing tests proving `undoNarrationDraftChange` moves one snapshot from `past` to `present`, pushes the previous present into `future`, and leaves state unchanged when no undo is available.
- [ ] Write failing tests proving `redoNarrationDraftChange` restores the next future snapshot, pushes the previous present into `past`, and leaves state unchanged when no redo is available.
- [ ] Write failing tests proving `resetNarrationDraftToSaved` returns to the saved baseline, clears undo/redo stacks, and marks the last action as `Reverted to saved narration.`
- [ ] Write failing tests proving `markNarrationDraftSaved` replaces the saved baseline with backend-returned segments and clears history stacks.
- [ ] Write failing tests proving `summarizeNarrationDraftChanges` reports added, removed, timing, text, and voice changes with stable row labels.
- [ ] Run `npm test -- --run src/domain/narrationDraftHistory.test.ts` and verify it fails because the helper module does not exist.
- [ ] Implement immutable snapshot helpers with copied arrays only.
- [ ] Keep comparisons deterministic by matching rows by `index` first and falling back to array position for added/removed rows.
- [ ] Run `npm test -- --run src/domain/narrationDraftHistory.test.ts` and verify it passes.
- [ ] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration draft history helpers`.

## Task 2: Draft History UI Integration

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/146-narration-draft-history-workbench.md`

**Interfaces:**
- Consumes:
  - `createNarrationDraftHistory(...)`
  - `applyNarrationDraftChange(...)`
  - `undoNarrationDraftChange(...)`
  - `redoNarrationDraftChange(...)`
  - `resetNarrationDraftToSaved(...)`
  - `markNarrationDraftSaved(...)`
  - `summarizeNarrationDraftChanges(...)`
  - Existing narration row update, add, delete, timeline edit, editing command, validation, save, timeline, waveform, preview, and inspector state in `NarrationWorkspacePanel`.
- Produces:
  - A compact `Narration draft history` region near the editing commands.
  - Buttons for `Undo`, `Redo`, and `Revert to saved`.
  - Unsaved-change metrics for added, removed, timing, text, and voice changes.
  - Local status text showing the last draft action.

- [ ] Write failing App tests proving `Narration draft history` renders for a completed job and starts with clean draft status and disabled undo/redo/revert.
- [ ] Write failing App tests proving duplicate/editing-command changes make the draft dirty, enable undo/revert, update added-row metrics, and keep save payload aligned with the current draft.
- [ ] Write failing App tests proving `Undo` restores the previous row list, enables `Redo`, and updates timeline/table labels.
- [ ] Write failing App tests proving `Redo` reapplies the undone change and keeps the selected row in a valid visible range.
- [ ] Write failing App tests proving table text edits and timeline timing edits are tracked as text/timing changes in the history summary.
- [ ] Write failing App tests proving `Revert to saved` restores the backend-loaded rows, clears validation caused by inserted blank rows, and does not call save or provider APIs.
- [ ] Run `npm test -- --run src/App.test.tsx -t "narration draft history"` and verify the new tests fail.
- [ ] Replace direct `segments` state in `NarrationWorkspacePanel` with `draftHistory.present` while keeping existing props and derived values stable.
- [ ] Initialize history from `workspace?.segments ?? []` whenever the workspace prop changes.
- [ ] Route `addSegment`, `deleteSelectedSegment`, `updateSegment`, timeline edits, and narration editing command results through `applyNarrationDraftChange`.
- [ ] Ensure selected row index is clamped after undo, redo, revert, delete, merge, and workspace reload.
- [ ] Add `NarrationDraftHistoryPanel` with compact metrics and undo/redo/revert controls.
- [ ] Keep undo/redo/revert local-only; do not call save, evidence, preflight, generation, mix, render, artifact, or provider APIs.
- [ ] Add compact CSS for the history panel, metric grid, and status text with stable height and no layout shift.
- [ ] Run `npm test -- --run src/App.test.tsx -t "narration draft history"` and verify it passes.
- [ ] Run `npm test -- --run src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`.
- [ ] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration draft history UI`.

## Task 3: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/146-narration-draft-history-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document the browser order: open completed job, edit narration locally, use undo/redo/revert while previewing, save only when the draft is ready, then generate audio/video only through explicit actions.
- [ ] State that draft history is in-memory only and resets on workspace reload or successful save response.
- [ ] State that undo, redo, and revert are local draft controls and never call providers or create artifacts.
- [ ] Add a decision record explaining why this slice adds local draft history before decoded waveform rendering, persisted editor sessions, or multitrack automation.
- [ ] Run `npm test -- --run src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification.
- [ ] Commit with message `Document narration draft history workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationDraftHistory.test.ts`
- `npm test -- --run src/App.test.tsx -t "narration draft history"`
- `npm test -- --run src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/domain/narrationTimelineEditing.test.ts src/domain/narrationWaveformOverview.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
