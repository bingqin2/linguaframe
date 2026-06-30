# Narration Playback Review Resolution Loop

## Goal

Turn saved playback review decisions into an actionable revision and handoff gate so operators can quickly find unresolved narration segments, revise affected rows, and prove whether the narrated-video review is ready for demo handoff.

## Why This Slice

The current playback review workspace stores decisions and safe evidence, but `NEEDS_EDIT` and `NEEDS_RERENDER` remain passive metadata. This slice closes the loop: review issues should guide the narration editor, block handoff when unresolved, and produce safe terminal/browser evidence for what still needs work.

## Feature Scope

- Add a backend resolution summary derived from narration segments, playback review rows, render review readiness, and generated narration artifacts.
- Classify each segment as `READY`, `UNREVIEWED`, `TEXT_REVISION_REQUIRED`, `RERENDER_REQUIRED`, or `BLOCKED`.
- Add safe action guidance per unresolved segment without exposing narration text or reviewer note bodies.
- Add an API endpoint and Markdown export for the resolution gate.
- Add browser UI in the narration workspace that filters unresolved playback-review segments and lets an operator jump to the matching narration row.
- Add local draft helpers to focus unresolved rows and make it clear that editing still requires `Save narration` plus explicit audio/video generation.
- Add terminal export script and full Tears integration after playback review export.
- Update docs and smoke tests with the complete review-resolution workflow.

## Implementation Tasks

1. Backend resolution service and API
   - Add `NarrationPlaybackReviewResolutionService`.
   - Add VOs for summary status, unresolved counts, segment action rows, safe links, and safety notes.
   - Add `GET /api/jobs/{jobId}/narration-playback-review/resolution`.
   - Add `GET /api/jobs/{jobId}/narration-playback-review/resolution/markdown/download`.
   - Compute status as `READY` only when all segments are accepted and narrated-video evidence is available, `ATTENTION` when edits/rerenders/unreviewed rows remain, and `BLOCKED` when narration segments are missing.
   - Keep exports metadata-only: segment index, timing, decision, issue categories, artifact readiness, and next action only.

2. Backend tests
   - Add service tests for blocked, attention, and ready cases.
   - Extend controller tests for JSON and Markdown routes.
   - Assert Markdown excludes narration text, reviewer note bodies, object keys, local paths, provider payloads, tokens, API keys, and media bytes.

3. Frontend API and types
   - Extend `frontend/src/domain/jobTypes.ts` with resolution summary types.
   - Add `getNarrationPlaybackReviewResolution` and `downloadNarrationPlaybackReviewResolutionMarkdown` to `linguaFrameApi`.
   - Add API tests for encoded job ids and Markdown download.

4. Frontend resolution panel
   - Add a compact `Playback resolution` panel near `Playback review`.
   - Show status, unresolved counts, rerender-required count, unreviewed count, and generated audio/video readiness.
   - Render unresolved segment rows with timing, decision, issue categories, and safe next action.
   - Add `Focus row` controls that select the matching narration segment in the editor and scroll the workbench locally.
   - Add Markdown download.
   - Do not display narration text or reviewer note bodies in the resolution panel.

5. Terminal and full-demo integration
   - Add `scripts/demo/narration-playback-review-resolution.sh`.
   - Extend shared demo helpers to download JSON/Markdown, print `narrationPlaybackResolution*` summary lines, and scan forbidden strings.
   - Extend `scripts/demo/docker-e2e-tears-of-steel-full.sh` to export resolution evidence after playback review, defaulting to report-only so blocked resolution does not fail a full demo run.

6. Docs, validation, commit, and merge
   - Update README, Docker demo guide, smoke checklist, roadmap, target-state, and execution log.
   - Run focused backend/frontend tests, script syntax checks, full backend/frontend tests, frontend build, and `git diff --check`.
   - Commit the feature branch and merge back to `main`.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=NarrationPlaybackReviewResolutionServiceTests,LocalizationJobControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "playback resolution"`
- `bash -n scripts/demo/narration-playback-review-resolution.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Out of Scope

- Automatically rewriting narration text from reviewer notes.
- Automatically regenerating audio/video after a resolution action.
- Exposing reviewer note bodies in safe evidence.
- Voice cloning, uploaded reference audio, lip sync, or nonlinear video editing.
