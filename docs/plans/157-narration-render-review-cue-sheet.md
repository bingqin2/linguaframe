# Narration Render Review Cue Sheet

## Goal

Add a read-only narration render review surface that turns saved narration segments, mix settings/keyframes, generated artifacts, waveform evidence, and render status into a safe cue sheet for demo review. This should help an operator explain a full-video narration render without opening every panel separately.

## Feature Scope

- Add a backend review endpoint for completed or in-progress narration jobs.
- Summarize narration windows, gaps, overlap readiness, voice usage, mix settings, keyframe coverage, waveform artifact status, narration audio status, narrated video status, and recommended next action.
- Keep the response metadata-only: no narration text, transcript text, subtitle text, object keys, local paths, provider payloads, secrets, or media bytes.
- Add a compact browser `Render review` panel inside the narration workspace.
- Add terminal JSON/Markdown export for full-video demo review evidence.

## Implementation Tasks

1. Backend cue-sheet domain and service
   - Create `NarrationRenderReviewVo` and focused row/check/link records.
   - Add `NarrationRenderReviewService` that reads existing workspace, artifacts, evidence, and waveform metadata.
   - Compute segment count, total narration duration, covered span, gap count, overlap count, voice summary, mix override/keyframe summaries, artifact availability, and next action.
   - Do not call TTS, FFmpeg render, OpenAI, or any provider.

2. Backend API, Markdown, and tests
   - Add `GET /api/jobs/{jobId}/narration-render-review`.
   - Add `GET /api/jobs/{jobId}/narration-render-review/markdown/download`.
   - Cover no-segment, audio-only, full audio+video, waveform-ready, and blocked-overlap cases.
   - Keep markdown safe and useful for reviewer handoff.

3. Frontend review panel
   - Extend TypeScript API/types.
   - Add `Render review` panel near `Render narration demo` in the narration workspace.
   - Show readiness, next action, artifact state, timing/gap/overlap metrics, mix/keyframe summary, and safe download/copy actions.
   - Add focused API and App tests.

4. Terminal script and full-demo integration
   - Add `scripts/demo/narration-render-review.sh`.
   - Extend shared demo helpers to print `narrationRenderReview*` status lines.
   - Optionally include review JSON/Markdown in the full Tears demo output after narration render/evidence when a job id exists.

5. Docs, validation, commit, and merge
   - Update README, demo runbook, smoke checklist, roadmap, and target-state docs.
   - Record validation in `docs/progress/execution-log.md`.
   - Run focused backend/frontend tests, full backend/frontend tests, frontend build, script syntax checks, and `git diff --check`.
   - Commit the feature branch and merge back to `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationRenderReviewServiceTests,LocalizationJobControllerTests`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration render review"`
- `bash -n scripts/demo/narration-render-review.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Out of Scope

- Generating or regenerating narration audio/video.
- Editing narration rows, mix keyframes, or waveform buckets.
- Voice cloning, uploaded reference audio, lip sync, or multitrack editing.
