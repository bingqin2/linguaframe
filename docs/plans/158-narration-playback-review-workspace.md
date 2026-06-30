# Narration Playback Review Workspace

## Goal

Add a complete review workflow for generated narration outputs so an operator can inspect each narration window, record review decisions, and export safe playback-review evidence after generating `NARRATION_AUDIO` or `NARRATED_VIDEO`.

## Why This Slice

The current narration flow can author, render, and summarize narration, but it does not let an operator mark whether the generated voiceover is acceptable per segment. This slice closes that demo loop by turning generated narration playback into a reviewable artifact with decisions, issue categories, summary status, browser UI, terminal export, and documentation.

## Feature Scope

- Persist per-segment narration review metadata keyed by job id and narration segment index.
- Support decisions such as `UNREVIEWED`, `ACCEPTED`, `NEEDS_EDIT`, and `NEEDS_RERENDER`.
- Support issue categories such as `TIMING`, `VOICE`, `TEXT`, `MIX`, `VIDEO`, and `OTHER`.
- Keep author notes optional, bounded, and separate from exported safe evidence.
- Add backend JSON and Markdown evidence endpoints for playback review.
- Add a compact browser `Playback review` panel in the narration workspace.
- Add terminal export through `scripts/demo/narration-playback-review.sh`.
- Integrate safe review evidence into the full Tears demo output when narration exists.

## Implementation Tasks

1. Backend persistence and domain model
   - Add a Flyway migration for `narration_segment_reviews`.
   - Add `NarrationSegmentReviewRecord`, repository, DTOs, and VOs.
   - Enforce one row per `(job_id, segment_index)`.
   - Bound note length and normalize blank notes to empty strings.

2. Backend service, API, and evidence
   - Add `NarrationPlaybackReviewService`.
   - Add `GET /api/jobs/{jobId}/narration-playback-review`.
   - Add `PUT /api/jobs/{jobId}/narration-playback-review/segments/{segmentIndex}`.
   - Add `GET /api/jobs/{jobId}/narration-playback-review/markdown/download`.
   - Compute overall status: `READY` when all saved narration segments are accepted, `ATTENTION` when any are unreviewed or need edits, and `BLOCKED` when no narration segments exist.
   - Keep evidence metadata-only: include counts, segment indexes, timings, decisions, issue categories, artifact readiness, and safe links, but exclude narration text bodies, transcript text, subtitle text, local paths, object keys, provider payloads, secrets, and media bytes.

3. Frontend API and review panel
   - Extend TypeScript job types and `linguaFrameApi`.
   - Add a `Playback review` panel near `Render review`.
   - Show each narration window with timing, current decision, issue category chips, and a bounded reviewer note field.
   - Allow saving one segment review at a time.
   - Show overall playback-review status, accepted count, needs-edit count, needs-rerender count, and unreviewed count.
   - Add Markdown download action.

4. Terminal script and demo integration
   - Add `scripts/demo/narration-playback-review.sh`.
   - Extend shared demo helper functions to download JSON/Markdown and print `narrationPlaybackReview*` summary lines.
   - Add full Tears demo export after narration render review when narration exists, defaulting to report-only so blocked review does not fail the full demo.

5. Docs, validation, commit, and merge
   - Update README, `scripts/demo/README.md`, Docker E2E demo guide, smoke checklist, roadmap, target-state, and `docs/progress/execution-log.md`.
   - Run focused backend/frontend tests, full backend/frontend tests, frontend build, script syntax checks, and `git diff --check`.
   - Commit the feature branch and merge back to `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationPlaybackReviewServiceTests,LocalizationJobControllerTests`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration playback review"`
- `bash -n scripts/demo/narration-playback-review.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Out of Scope

- Regenerating audio or video from review decisions.
- Automatic quality scoring of narration audio.
- Voice cloning, uploaded reference audio, lip sync, or full nonlinear video editing.
- Exporting raw narration script text in playback-review evidence.
