# Narration Quick Script Import Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators paste a multi-line timed narration script and turn it into editable narration rows in one browser workflow.

**Architecture:** Keep this as a frontend-first authoring slice over the existing narration workspace draft state. Add a pure parser/validator for a compact line format, then add an import panel that previews parsed rows, reports row-level errors, and applies valid rows to the local draft by replacing or appending. Existing save, segment TTS preview, script package export/import, narration generation, evidence, and render flows remain the source of truth after import.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration regression tests, existing Markdown docs.

## Global Constraints

- This is one complete feature slice: parser, frontend workbench, tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-quick-script-import-workbench`; do not include `/` in the user-facing branch title.
- Do not add backend routes, database columns, provider calls, object-storage writes, artifact generation, voice cloning, uploaded reference audio, or decoded waveform rendering.
- Import is local-only until the user clicks the existing `Save narration` action.
- The parser must support multiple rows and reject invalid rows without partially applying them.
- Supported line format: `START-END | VOICE | TEXT`, where `START` and `END` accept `SS`, `MM:SS`, or `HH:MM:SS`; `VOICE` may be blank to inherit the job/default voice.
- Imported rows must use the current voice catalog for validation and the same timing/text constraints as manual rows.
- UI copy must stay compact and consistent with the existing LinguaFrame narration workbench.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Pure Quick Script Parser

**Files:**
- Create: `frontend/src/domain/narrationQuickScriptImport.ts`
- Test: `frontend/src/domain/narrationQuickScriptImport.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/148-narration-quick-script-import-workbench.md`

**Interfaces:**
- Consumes:
  - `NarrationWorkspace['segments']`
  - `NarrationWorkspace['voiceCatalog']`
- Produces:
  - `NarrationQuickScriptImportMode = 'replace' | 'append'`
  - `NarrationQuickScriptImportRow`
  - `NarrationQuickScriptImportIssue`
  - `NarrationQuickScriptImportResult`
  - `parseNarrationQuickScript(input: string, options: { existingSegments: NarrationWorkspace['segments']; voiceCatalog: NarrationWorkspace['voiceCatalog'] | null; mode: NarrationQuickScriptImportMode; }): NarrationQuickScriptImportResult`

- [x] Write failing parser tests for `00:15-00:28 | alloy | Explain the first scene.` and `55-70.5 || Inherit default voice.`.
- [x] Write failing parser tests for `MM:SS`, `HH:MM:SS`, decimal seconds, whitespace trimming, and Unicode narration text.
- [x] Write failing parser tests for malformed lines, blank text, end-before-start, text longer than 1000 characters, voice longer than 64 characters, and unknown voice presets.
- [x] Write failing parser tests proving overlapping imported rows are rejected and append mode reindexes after existing rows.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts` and verify it fails because the parser module does not exist.
- [x] Implement `parseTimestampSeconds(value: string): number | null` with `SS`, `MM:SS`, `HH:MM:SS`, and decimal support.
- [x] Implement line parsing with exact `|` separators and row-level issue messages such as `Line 2: expected START-END | VOICE | TEXT.`
- [x] Implement validation with existing narration limits and voice catalog checks.
- [x] Implement replace and append indexing without mutating caller-owned segment objects.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts` and verify it passes.
- [x] Update execution log with RED/GREEN parser evidence.
- [x] Commit with message `Add narration quick script parser`.

## Task 2: Browser Import Workbench

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/148-narration-quick-script-import-workbench.md`

**Interfaces:**
- Consumes:
  - `parseNarrationQuickScript(...)`
  - Current `NarrationWorkspacePanel` draft `segments`, `selectedIndex`, `voiceCatalog`, `commitDraftChange`
- Produces:
  - A compact `Quick script import` panel in the narration workspace.
  - Local `Replace draft` and `Append to draft` import actions.

- [x] Write failing App tests proving `Quick script import` renders for a completed job with example placeholder text.
- [x] Write failing App tests proving valid pasted rows show parsed row count, total duration, and a preview list before apply.
- [x] Write failing App tests proving `Replace draft` replaces the local draft rows, updates table/timeline/inspector, and does not call `saveNarrationWorkspace`.
- [x] Write failing App tests proving `Append to draft` preserves existing rows, appends parsed rows with reindexed indices, and keeps save payload aligned.
- [x] Write failing App tests proving invalid input displays row-level errors and disables both apply buttons.
- [x] Write failing App tests proving unknown voice presets are blocked using the current voice catalog.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script import"` and verify App tests fail because the panel does not exist.
- [x] Add `NarrationQuickScriptImportPanel` after `NarrationTtsPreviewPanel` and before `NarrationPreviewPanel`.
- [x] Add local state for pasted script text, mode, parse result, and status.
- [x] Recompute parse result with `useMemo` from text, mode, current draft rows, and voice catalog.
- [x] Disable apply when parse result has errors or no parsed rows.
- [x] On apply, call `commitDraftChange(parsedSegments, 'Imported narration quick script.', nextSelectedIndex)` and keep changes local until existing `Save narration`.
- [x] Show concise guidance: `Format: 00:15-00:28 | alloy | Explain this moment. Leave voice blank to inherit default.`
- [x] Style the panel using existing compact narration panel patterns; do not add landing-page or marketing copy.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script import"` and verify it passes.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [x] Update execution log with RED/GREEN UI evidence.
- [ ] Commit with message `Add narration quick script import workbench`.

## Task 3: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/148-narration-quick-script-import-workbench.md`

- [ ] Document browser order: paste quick script, inspect parsed rows/errors, replace or append local draft, preview selected TTS, save when ready, then generate narration audio/video.
- [ ] Document supported line format and timestamp examples.
- [ ] State that quick import is local-only and does not save rows, call providers, create artifacts, update evidence, or write object storage.
- [ ] Add a decision record explaining why this slice uses a compact text importer before full timeline templates, file upload import, voice cloning, or provider voice browsing.
- [ ] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationSegmentPreviewServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification evidence.
- [ ] Commit with message `Document narration quick script import`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts`
- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "quick script import"`
- `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/domain/narrationDraftHistory.test.ts src/domain/narrationEditingCommands.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationSegmentPreviewServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-segment-preview.sh scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
