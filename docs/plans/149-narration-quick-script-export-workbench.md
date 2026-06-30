# Narration Quick Script Export Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators copy or download the current local narration draft as quick script text that can be pasted back into the existing quick script importer.

**Architecture:** Keep export frontend-first and local-only over the existing narration draft state. Add a pure formatter that converts `NarrationWorkspace['segments']` into `START-END | VOICE | TEXT` lines, then add a compact export panel beside quick import with copy and text-download actions. Existing save, script package export/import, TTS preview, audio/video generation, evidence, and render flows remain unchanged.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration regression tests, existing Markdown docs.

## Global Constraints

- This is one complete feature slice: formatter, browser workbench, tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-quick-script-export-workbench`; do not include `/` in the user-facing branch title.
- Do not add backend routes, database columns, provider calls, object-storage writes, artifact generation, voice cloning, uploaded reference audio, or decoded waveform rendering.
- Export must use the current local draft, including unsaved quick-import/manual/timeline edits.
- Export is local-only and must not save rows, call providers, create artifacts, update evidence, generate video, or write object storage.
- Exported text must round-trip through the existing quick script parser.
- Voice inheritance must export as a blank voice field: `START-END || TEXT`.
- UI copy must stay compact and consistent with the existing LinguaFrame narration workbench.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Pure Quick Script Formatter

**Files:**
- Modify: `frontend/src/domain/narrationQuickScriptImport.ts`
- Modify: `frontend/src/domain/narrationQuickScriptImport.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/149-narration-quick-script-export-workbench.md`

**Interfaces:**
- Consumes:
  - `NarrationWorkspace['segments']`
- Produces:
  - `formatNarrationQuickScript(segments: NarrationWorkspace['segments']): string`
  - `formatQuickScriptTimestamp(seconds: number): string`

- [ ] Write failing formatter tests proving saved rows export as `00:15-00:28 | alloy | Explain the first scene.` and inherited voice rows export as `00:55-01:10 || Inherit default voice.`
- [ ] Write failing formatter tests proving decimals are preserved compactly, invalid negative/non-finite values are clamped to `00:00`, and caller-owned segment objects are not mutated.
- [ ] Write failing round-trip tests proving `parseNarrationQuickScript(formatNarrationQuickScript(segments), { mode: 'replace' })` recreates the same timing, text, and voice values.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts` and verify formatter tests fail because the formatter is missing.
- [ ] Implement `formatQuickScriptTimestamp(seconds)` with `HH:MM:SS`, `MM:SS`, or `SS` output, preserving up to three decimal places only when needed.
- [ ] Implement `formatNarrationQuickScript(segments)` in index order without mutating segments.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts` and verify it passes.
- [ ] Update execution log with RED/GREEN formatter evidence.
- [ ] Commit with message `Add narration quick script formatter`.

## Task 2: Browser Export Workbench

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/149-narration-quick-script-export-workbench.md`

**Interfaces:**
- Consumes:
  - `formatNarrationQuickScript(...)`
  - Current `NarrationWorkspacePanel` draft `segments`
- Produces:
  - A compact `Quick script export` panel in the narration workspace.
  - `Copy quick script` and `Download quick script` local actions.

- [ ] Write failing App tests proving `Quick script export` renders with the current draft text.
- [ ] Write failing App tests proving local unsaved edits immediately update the exported quick script.
- [ ] Write failing App tests proving inherited/default voice exports as a blank voice field.
- [ ] Write failing App tests proving `Copy quick script` writes the current script to `navigator.clipboard.writeText` and does not call `saveNarrationWorkspace`.
- [ ] Write failing App tests proving `Download quick script` creates a text blob link with a `.txt` filename and does not call providers or save rows.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script export"` and verify App tests fail because the panel does not exist.
- [ ] Add `NarrationQuickScriptExportPanel` after `NarrationQuickScriptImportPanel` and before `NarrationPreviewPanel`.
- [ ] Compute exported script with `useMemo` from current draft `segments`.
- [ ] Add copy status state and clear it when draft rows change.
- [ ] Implement download with `Blob`, `URL.createObjectURL`, an ephemeral anchor, and immediate URL revocation.
- [ ] Disable copy/download when the current draft has no rows.
- [ ] Style the panel using existing compact narration panel patterns; do not add marketing copy.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script export"` and verify it passes.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [ ] Update execution log with RED/GREEN UI evidence.
- [ ] Commit with message `Add narration quick script export workbench`.

## Task 3: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/149-narration-quick-script-export-workbench.md`

- [ ] Document browser order: edit/import narration rows, inspect quick script export, copy/download the local draft, paste it back into quick import when needed, save only when ready.
- [ ] State that quick script export is local-only and does not save rows, call providers, create artifacts, update evidence, generate video, or write object storage.
- [ ] Add a decision record explaining why this slice uses local text copy/download before backend quick-script files or timeline templates.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationSegmentPreviewServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification evidence.
- [ ] Commit with message `Document narration quick script export`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts`
- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script export"`
- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationSegmentPreviewServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
