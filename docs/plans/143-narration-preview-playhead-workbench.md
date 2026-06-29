# Narration Preview Playhead Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a narration preview workbench that links time-coded narration windows to playable source/output media with a visible playhead and segment jump controls.

**Architecture:** Keep this as a frontend-first feature over existing source-media and artifact download URLs. Reuse the current `NarrationWorkspacePanel`, `NarrationTimelineWorkbench`, source video route, and media-delivery artifacts; do not add backend routes unless an existing URL contract bug is discovered. Store preview state locally in React so seeking and playhead updates never save narration rows, call OpenAI, generate artifacts, or mutate storage.

**Tech Stack:** React + Vite + TypeScript, HTML media elements, Vitest/jsdom, Testing Library, existing Spring Boot source/artifact download APIs, existing docs and demo checklist.

## Global Constraints

- This is one complete feature slice: preview UI, playhead behavior, focused tests, docs/progress updates, validation, commits, and merge back to `main`.
- Use branch title `narration-preview-playhead-workbench`; do not include `/` in the user-facing branch title.
- Do not add waveform rendering, audio decoding, multitrack automation, voice cloning, uploaded reference audio, or provider voice preview playback in this slice.
- Do not add a backend table or route unless an existing safe download URL is insufficient.
- Preview controls must not call OpenAI, generate narration audio/video, create artifacts, save narration rows, or mutate object storage.
- The selected media source should prefer `NARRATED_VIDEO`, then `BURNED_VIDEO`, then source video; if no video is available, show a clear unavailable state.
- The workbench must stay compact and consistent with the existing dense LinguaFrame UI.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Preview Source Selection Helper

**Files:**
- Create: `frontend/src/domain/narrationPreview.ts`
- Test: `frontend/src/domain/narrationPreview.test.ts`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/143-narration-preview-playhead-workbench.md`

**Interfaces:**
- Produces:
  - `export type NarrationPreviewSourceKind = 'narrated-video' | 'burned-video' | 'source-video';`
  - `export interface NarrationPreviewSource { kind: NarrationPreviewSourceKind; label: string; url: string; artifactId: string | null; available: boolean; }`
  - `export function selectNarrationPreviewSource(input: { jobId: string; videoId: string; artifacts: JobArtifact[]; artifactDownloadUrl: (jobId: string, artifactId: string) => string; sourceMediaDownloadUrl: (videoId: string) => string; }): NarrationPreviewSource`
  - `export function calculateNarrationPlayheadPercent(currentSeconds: number, startSeconds: number, endSeconds: number): number`
- Consumes: `JobArtifact` from `frontend/src/domain/jobTypes.ts`.

- [x] Write failing helper tests proving source priority is `NARRATED_VIDEO` over `BURNED_VIDEO` over source video, URLs are generated through existing API URL builders, unavailable artifacts are ignored, and playhead percent clamps between `0` and `100`.
- [x] Run `npm test -- --run src/domain/narrationPreview.test.ts` and verify it fails because the helper module does not exist.
- [x] Implement `selectNarrationPreviewSource` and `calculateNarrationPlayheadPercent` with no DOM dependencies.
- [x] Run `npm test -- --run src/domain/narrationPreview.test.ts` and verify it passes.
- [x] Update execution log with RED/GREEN evidence.
- [x] Commit with message `Add narration preview source helpers`.

## Task 2: Narration Preview Panel And Timeline Playhead

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/143-narration-preview-playhead-workbench.md`

**Interfaces:**
- Consumes:
  - `selectNarrationPreviewSource(...)`
  - `calculateNarrationPlayheadPercent(...)`
  - Existing `NarrationWorkspacePanel` state: `segments`, `selectedIndex`, `localTimeline`
  - Existing API URL builders: `linguaFrameApi.artifactDownloadUrl`, `linguaFrameApi.sourceMediaDownloadUrl`
- Produces:
  - `NarrationPreviewPanel` rendered inside `NarrationWorkspacePanel`
  - `NarrationTimelineWorkbench` optional `playheadSeconds` prop
  - Timeline playhead marker positioned by local timeline span

- [x] Write failing App tests proving a completed job with `NARRATED_VIDEO` renders a `Narration preview` panel using the narrated-video URL.
- [x] Write failing App tests proving the panel falls back to source video when no generated video artifacts are available.
- [x] Write failing App tests proving clicking `Jump to narration 1` sets the media current time to the selected segment start and updates visible current time/playhead state.
- [x] Write failing App tests proving clicking `Play window` seeks to the selected segment start, calls `play()`, and shows the selected window end.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration preview"` and verify the new tests fail.
- [x] Add preview state to `NarrationWorkspacePanel`: `previewCurrentSeconds`, `previewIsPlayingWindow`, and selected media metadata.
- [x] Add `NarrationPreviewPanel` with:
  - media source label;
  - `<video aria-label="Narration preview player" controls ...>`;
  - `Jump to narration N` button;
  - `Play window` button;
  - selected window start/end/current time metrics;
  - unavailable message when no source video id exists.
- [x] Wire media events: `onTimeUpdate` updates current seconds; `onEnded` clears window playback; if playback passes selected segment `endSeconds`, pause the media and clear window playback.
- [x] Pass `previewCurrentSeconds` to `NarrationTimelineWorkbench` and render one stable `.narration-timeline-playhead` marker when the current time falls within the timeline span.
- [x] Add compact CSS for preview panel, video dimensions, control row, and playhead marker without causing timeline layout shift.
- [x] Run `npm test -- --run src/App.test.tsx -t "narration preview"` and verify it passes.
- [x] Run `npm test -- --run src/domain/narrationPreview.test.ts src/App.test.tsx`.
- [x] Update execution log with validation details.
- [x] Commit with message `Add narration preview playhead UI`.

## Task 3: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/143-narration-preview-playhead-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [x] Document the browser order: open completed job, edit/select narration window, preview against narrated/burned/source video, jump to window, play window, then save/generate/render only through explicit actions.
- [x] State that preview playback is local-only and does not call providers or create artifacts.
- [x] Add a decision record explaining why this slice adds source/output preview and playhead before waveform rendering.
- [x] Run `npm test -- --run src/domain/narrationPreview.test.ts src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Update execution log with final verification.
- [x] Commit with message `Document narration preview playhead workbench`.
- [x] Merge feature branch back to `main`.
- [x] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `npm test -- --run src/domain/narrationPreview.test.ts`
- `npm test -- --run src/App.test.tsx -t "narration preview"`
- `npm test -- --run src/domain/narrationPreview.test.ts src/domain/narrationTimelineEditing.test.ts src/App.test.tsx`
- `npm test -- --run`
- `npm run build`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- `mvn -pl LinguaFrame test`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `git diff --check`
