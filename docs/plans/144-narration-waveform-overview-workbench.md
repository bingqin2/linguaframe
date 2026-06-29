# Narration Waveform Overview Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a waveform-style narration overview that makes narration density, gaps, selected windows, and preview scrub position visible before generating audio or video.

**Architecture:** Keep this as a frontend-only feature over existing narration segment state and preview playback state. Generate deterministic waveform-style buckets from narration timing/text metadata rather than decoding audio; this keeps the editor useful before `NARRATION_AUDIO` exists and avoids adding a waveform library or backend route. The overview updates local React state only and reuses the existing `Narration preview` player, timeline workbench, table, inspector, save, preflight, and render flows.

**Tech Stack:** React + Vite + TypeScript, Vitest/jsdom, Testing Library, existing Spring Boot narration regression tests, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: helper module, UI, focused tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-waveform-overview-workbench`; do not include `/` in the user-facing branch title.
- Do not add audio decoding, Web Audio, third-party waveform packages, backend tables, backend routes, provider calls, object-storage writes, voice cloning, uploaded reference audio, or multitrack automation curves in this slice.
- The waveform overview must be metadata-derived and deterministic from local narration windows, text length, timeline span, gaps, and preview current time.
- Scrubbing must only seek the browser preview player and update local preview state; it must not save narration rows, synthesize audio, generate video, refresh evidence, or call OpenAI.
- UI style should stay compact and consistent with the existing LinguaFrame workbench: dense desktop layout, restrained borders, stable dimensions, no marketing/card-heavy redesign.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Waveform Overview Helper Module

**Files:**
- Create: `frontend/src/domain/narrationWaveformOverview.ts`
- Test: `frontend/src/domain/narrationWaveformOverview.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/144-narration-waveform-overview-workbench.md`

**Interfaces:**
- Produces:
  - `export interface NarrationWaveformBucket { index: number; startSeconds: number; endSeconds: number; heightPercent: number; active: boolean; gap: boolean; selected: boolean; }`
  - `export interface NarrationWaveformOverview { startSeconds: number; endSeconds: number; durationSeconds: number; bucketCount: number; buckets: NarrationWaveformBucket[]; selectedStartPercent: number | null; selectedEndPercent: number | null; playheadPercent: number | null; gapBucketCount: number; activeBucketCount: number; }`
  - `export function buildNarrationWaveformOverview(input: { segments: NarrationWorkspace['segments']; selectedIndex: number; currentSeconds: number; bucketCount?: number; }): NarrationWaveformOverview`
  - `export function secondsFromWaveformPercent(input: { percent: number; startSeconds: number; endSeconds: number; }): number`
- Consumes: `NarrationWorkspace` from `frontend/src/domain/jobTypes.ts`.

- [x] Write failing tests proving an empty segment list returns `bucketCount` silent gap buckets, zero duration, and null selected/playhead percentages.
- [x] Write failing tests proving two separated narration windows produce active buckets for covered time and gap buckets for silence.
- [x] Write failing tests proving selected-window start/end percentages and playhead percentage are clamped between `0` and `100`.
- [x] Write failing tests proving bucket heights are deterministic from overlap coverage plus text density and stay between `8` and `100`.
- [x] Write failing tests proving `secondsFromWaveformPercent` maps `0`, `50`, and `100` percent onto the timeline span.
- [x] Run `npm test -- --run src/domain/narrationWaveformOverview.test.ts` and verify it fails because the helper module does not exist.
- [x] Implement `buildNarrationWaveformOverview` with default `bucketCount = 48`, sorted copied segments, timeline start/end from segment bounds, and per-bucket overlap math.
- [x] Implement `secondsFromWaveformPercent` with percent clamping and rounded `0.001` second precision.
- [x] Run `npm test -- --run src/domain/narrationWaveformOverview.test.ts` and verify it passes.
- [x] Update execution log with RED/GREEN evidence.
- [x] Commit with message `Add narration waveform overview helpers`.

## Task 2: Waveform Overview UI And Preview Scrubber

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/144-narration-waveform-overview-workbench.md`

**Interfaces:**
- Consumes:
  - `buildNarrationWaveformOverview(...)`
  - `secondsFromWaveformPercent(...)`
  - Existing `previewCurrentSeconds`, `previewSource`, `selectedIndex`, `selectedSegment`, and `setPreviewCurrentSeconds` in `NarrationWorkspacePanel`.
- Produces:
  - `NarrationWaveformOverviewPanel` rendered between `NarrationTimelineWorkbench` and `NarrationPreviewPanel`.
  - A stable `.narration-waveform-overview` region with bucket bars, selected-window overlay, playhead marker, gap/active metrics, and scrub buttons.
  - Optional `seekSeconds` callback on `NarrationPreviewPanel` so waveform scrub can seek the same preview video.

- [x] Write failing App tests proving the narration workspace renders a `Narration waveform overview` region with active/gap bucket metrics.
- [x] Write failing App tests proving the selected narration window is visible in the waveform overview and updates when selecting narration row 2.
- [x] Write failing App tests proving clicking `Scrub to midpoint` seeks the preview player to the midpoint of the local timeline and updates both waveform and timeline playheads.
- [x] Write failing App tests proving scrubbing is disabled when no preview media is available.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration waveform"` and verify the new tests fail.
- [x] Add a `previewVideoRef = useRef<HTMLVideoElement | null>(null)` in `NarrationWorkspacePanel` and pass it to `NarrationPreviewPanel`.
- [x] Add `seekPreviewTo(seconds)` in `NarrationWorkspacePanel` that sets `previewVideoRef.current.currentTime`, updates `previewCurrentSeconds`, and clears `previewWindowEndSeconds`.
- [x] Add `NarrationWaveformOverviewPanel` with:
  - `aria-label="Narration waveform overview"`;
  - 48 fixed-width bucket bars;
  - selected window overlay from `selectedStartPercent` to `selectedEndPercent`;
  - playhead marker from `playheadPercent`;
  - metrics for active buckets, gap buckets, timeline span, and current time;
  - `Scrub to start`, `Scrub to midpoint`, and `Scrub to selected` buttons.
- [x] Wire `Scrub to start` to overview start seconds, `Scrub to midpoint` to 50% of overview span, and `Scrub to selected` to selected segment start.
- [x] Keep waveform scrubbing local-only; do not call save, evidence, preflight, render, audio generation, or video generation handlers.
- [x] Add compact CSS for waveform buckets, selected overlay, playhead marker, metrics, and controls with stable height and no layout shift.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration waveform"` and verify it passes.
- [x] Run `npm test -- --run src/domain/narrationWaveformOverview.test.ts src/domain/narrationPreview.test.ts src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`.
- [x] Update execution log with RED/GREEN evidence.
- [x] Commit with message `Add narration waveform overview UI`.

## Task 3: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/144-narration-waveform-overview-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [x] Document the browser order: open completed job, inspect timeline, inspect waveform overview, select/scrub narration windows, preview locally, then save/generate/render only through explicit actions.
- [x] State that this is a metadata-derived waveform-style overview, not decoded audio waveform rendering.
- [x] State that scrubbing is local-only and does not call providers or create artifacts.
- [x] Add a decision record explaining why this slice adds deterministic waveform-style overview before real audio waveform decoding.
- [x] Run `npm test -- --run src/domain/narrationWaveformOverview.test.ts src/domain/narrationPreview.test.ts src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Update execution log with final verification.
- [x] Commit with message `Document narration waveform overview workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationWaveformOverview.test.ts`
- `npm test -- --run src/App.test.tsx -t "narration waveform"`
- `npm test -- --run src/domain/narrationWaveformOverview.test.ts src/domain/narrationPreview.test.ts src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
