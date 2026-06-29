# Narration Timeline Drag Editing Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a drag-editing narration timeline workbench so operators can move and resize time-coded narration windows visually while keeping the existing table, inspector, save, TTS, render, and evidence flow intact.

**Architecture:** Keep the backend persistence contract unchanged and implement drag editing as a frontend workbench over the existing `NarrationWorkspace.segments` state. Extract small pure helpers for seconds/pixel conversion, snapping, clamping, duration calculation, and overlap-aware validation so the large `App.tsx` UI remains testable. The timeline bars update local unsaved segment state; existing `Save narration` persists through `PUT /api/jobs/{jobId}/narration-workspace`.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration workspace validation tests, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: frontend drag editing, focused tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-timeline-drag-editing-workbench`; do not include `/` in the user-facing branch title.
- Do not add waveform rendering, audio decoding, multitrack automation curves, voice cloning, uploaded reference audio, or provider voice preview playback in this slice.
- Do not add a new backend table or route unless an existing save contract bug is discovered; the feature should save through the existing narration workspace API.
- Drag editing must not generate narration audio, generate narrated video, call OpenAI, create artifacts, mutate object storage, or run one-click render.
- Drag editing must keep text, voice, mix settings, script package export/import, narration evidence, and render preflight semantics unchanged.
- UI style should follow the existing LinguaFrame workbench and the remembered `fish-tts-desktop` cues: dense desktop layout, segment-list/table plus timeline, right inspector, restrained borders, compact controls, no marketing/card-heavy redesign.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Timeline Editing Helper Module

**Files:**
- Create: `frontend/src/domain/narrationTimelineEditing.ts`
- Test: `frontend/src/domain/narrationTimelineEditing.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/142-narration-timeline-drag-editing-workbench.md`

**Interfaces:**
- Produces:
  - `export type NarrationTimelineEditMode = 'move' | 'resize-start' | 'resize-end';`
  - `export interface NarrationTimelineEditInput { segment: NarrationWorkspace['segments'][number]; mode: NarrationTimelineEditMode; pointerDeltaPx: number; trackWidthPx: number; timelineStartSeconds: number; timelineEndSeconds: number; minDurationSeconds?: number; snapSeconds?: number; }`
  - `export function editNarrationTimelineSegment(input: NarrationTimelineEditInput): NarrationWorkspace['segments'][number]`
  - `export function normalizeNarrationSegmentTiming(segment: NarrationWorkspace['segments'][number]): NarrationWorkspace['segments'][number]`
  - `export function buildLocalNarrationTimeline(segments: NarrationWorkspace['segments']): NarrationWorkspace['timeline']`
- Consumes: `NarrationWorkspace` types from `frontend/src/domain/jobTypes.ts`.

- [x] Write failing tests for moving a segment by pixels, resizing start, resizing end, clamping at timeline start/end, enforcing a minimum `0.25` second duration, snapping to `0.25` seconds, preserving text/voice/index fields, and recomputing local timeline percentages.
- [x] Run `npm test -- --run src/domain/narrationTimelineEditing.test.ts` and verify it fails because `narrationTimelineEditing.ts` does not exist.
- [x] Implement `editNarrationTimelineSegment` with deterministic math:
  - `secondsPerPixel = (timelineEndSeconds - timelineStartSeconds) / trackWidthPx`
  - default `minDurationSeconds = 0.25`
  - default `snapSeconds = 0.25`
  - `move` shifts start/end together and clamps the full window into the timeline range.
  - `resize-start` changes only start, clamped to `timelineStartSeconds` and `endSeconds - minDurationSeconds`.
  - `resize-end` changes only end, clamped to `startSeconds + minDurationSeconds` and `timelineEndSeconds`.
  - `durationSeconds` is recalculated from rounded start/end.
- [x] Implement `buildLocalNarrationTimeline` by sorting a copy of segments by `startSeconds`, computing start/end/span/covered/gaps/overlap, and assigning `leftPercent`/`widthPercent` for each segment.
- [x] Run `npm test -- --run src/domain/narrationTimelineEditing.test.ts` and verify it passes.
- [x] Update execution log with RED/GREEN evidence.
- [x] Commit with message `Add narration timeline editing helpers`.

## Task 2: Interactive Timeline Workbench UI

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/142-narration-timeline-drag-editing-workbench.md`

**Interfaces:**
- Consumes:
  - `editNarrationTimelineSegment(...)`
  - `buildLocalNarrationTimeline(segments)`
  - Existing `updateSegment(index, patch)` and `setSelectedIndex(index)` inside `NarrationWorkspacePanel`.
- Produces:
  - `NarrationTimelineWorkbench` props become `{ timeline, selectedIndex, onSelectSegment, onEditSegment }`.
  - Timeline segment buttons expose resize handles with labels `Resize narration N start` and `Resize narration N end`.
  - Keyboard controls on selected bars:
    - ArrowLeft/ArrowRight move by `0.25` seconds.
    - Shift+ArrowLeft/Shift+ArrowRight resize the end by `0.25` seconds.
    - Alt+ArrowLeft/Alt+ArrowRight resize the start by `0.25` seconds.

- [x] Write failing App tests proving:
  - clicking a timeline segment selects the same row and updates inspector selection;
  - pressing ArrowRight on a selected timeline segment updates `Narration 1 start` and `Narration 1 end` values by `0.25`;
  - pressing Shift+ArrowRight updates only the selected segment end;
  - the Save narration button sends the edited start/end through `saveNarrationWorkspace`.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration timeline"` and verify the new tests fail against the static timeline.
- [x] In `NarrationWorkspacePanel`, compute `localTimeline = useMemo(() => buildLocalNarrationTimeline(segments), [segments])` and pass it to `NarrationTimelineWorkbench` instead of `workspace.timeline` so unsaved edits are visible immediately.
- [x] Update `NarrationTimelineWorkbench` to:
  - render bars as buttons with selected state;
  - call `onSelectSegment(segment.index)` on click/focus;
  - support `onKeyDown` for move/resize keyboard controls;
  - support pointer drag for move and pointer drag on start/end handles for resize;
  - call `onEditSegment(index, patch)` with updated `startSeconds`, `endSeconds`, and `durationSeconds`.
- [x] Add pointer event state with `useState<{ index: number; mode: NarrationTimelineEditMode; startClientX: number; original: NarrationWorkspace['segments'][number]; trackWidthPx: number; } | null>(null)` and use `setPointerCapture`/`releasePointerCapture` on the track.
- [x] Keep the table inputs authoritative: direct number edits still update the timeline immediately through shared `segments` state.
- [x] Add CSS for selected bars and handles:
  - stable track height;
  - visible left/right handles;
  - focused selected segment outline;
  - no layout shift on hover/focus.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration timeline"` and verify it passes.
- [x] Run `npm test -- --run src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`.
- [x] Update execution log with RED/GREEN evidence.
- [ ] Commit with message `Add narration timeline drag editing UI`.

## Task 3: Validation, Save Contract, And Evidence Consistency

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/142-narration-timeline-drag-editing-workbench.md`

**Interfaces:**
- Consumes: Existing `saveNarrationWorkspace(jobId, { segments })` frontend API and backend `PUT /api/jobs/{jobId}/narration-workspace`.
- Produces: Regression evidence that drag-edited times round-trip through the existing API and keep timeline/evidence metadata correct.

- [ ] Add App regression tests proving drag/keyboard edits that create overlap show `Row N: time range overlaps the previous row.`, disable `Save narration`, disable `Generate narration audio`, but leave `Refresh evidence` enabled.
- [ ] Add App regression tests proving deleting a selected segment after a drag reindexes rows and timeline bars without stale selected index errors.
- [ ] Add backend service/controller tests, only if missing, that saving changed start/end values returns recomputed `timeline.startSeconds`, `timeline.endSeconds`, `coveredSeconds`, `gapCount`, `hasOverlap`, and segment percentages.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests` and verify backend contract tests pass.
- [ ] Run `npm test -- --run src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`.
- [ ] Update execution log with validation details.
- [ ] Commit with message `Stabilize narration timeline save validation`.

## Task 4: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/142-narration-timeline-drag-editing-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document the browser editing order: open completed job, use timeline bars or table inputs, drag/move/resize narration windows, inspect validation, save narration, generate audio/video or run render preflight.
- [ ] Document keyboard shortcuts for selected bars in the smoke checklist, not as visible in-app instructional text.
- [ ] State that drag editing is local-only until `Save narration` and does not call OpenAI or create artifacts.
- [ ] Add a decision record explaining why this slice adds timeline drag editing before waveform rendering or multitrack automation.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Update execution log with final verification.
- [ ] Commit with message `Document narration timeline drag editing`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationTimelineEditing.test.ts`
- `npm test -- --run src/App.test.tsx -t "narration timeline"`
- `npm test -- --run src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `npm run build`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
