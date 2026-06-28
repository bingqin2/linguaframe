# Subtitle Polishing Control Workspace Implementation Plan

**Goal:** Add an upload-time subtitle polishing option that improves translated subtitle readability before downstream quality evaluation, TTS, burn-in, review, and handoff evidence.

**Feature value:** This moves LinguaFrame closer to a credible localization demo: the user can decide whether generated subtitles should remain literal or be polished for natural viewing, and the system can prove which mode was used without exposing raw media text.

**Branch:** `subtitle-polishing-control-workspace`

## Scope

- Add a job-level `subtitlePolishingMode` with safe values:
  - `OFF`: keep current translated subtitle output unchanged.
  - `BALANCED`: polish for natural subtitle readability while preserving meaning and timing.
  - `STRICT`: only make minimal grammar, punctuation, and fluency fixes.
- Persist mode on upload and carry it through dispatch, worker context, job detail, job list, diagnostics, delivery manifest, demo reports, evidence bundles, and recent-job browser storage.
- Add a polishing provider boundary and prompt template so OpenAI-backed runs can audit the subtitle polishing model call separately from translation.
- Run polishing after target subtitle export and before quality evaluation, TTS, subtitle burn-in, and review/export stages.
- Isolate provider cache identity by mode, provider, model, prompt version, target language, and input subtitle text hash.
- Add React upload controls and selected-job metadata display.
- Add demo script support through `LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE`.

## Out Of Scope

- Browser editing of prompt templates.
- Automatic prompt optimization.
- Segment retiming or subtitle line wrapping algorithms.
- Regenerating already completed jobs.
- Multi-provider routing beyond the existing demo/OpenAI pattern.

## Backend Design

1. Add enum `SubtitlePolishingMode` under `com.linguaframe.job.domain.enums`.
2. Add migration `V23__add_subtitle_polishing_mode.sql` with a non-null `subtitle_polishing_mode` column defaulting to `OFF`.
3. Extend `LocalizationJobRecord`, repository insert/list/detail mapping, upload service/controller, dispatch messages, and job VO types.
4. Add `SUBTITLE_POLISHING` to prompt template purpose and model-call operation enums.
5. Add `SubtitlePolishingProvider` with demo and OpenAI implementations:
   - Demo provider returns input unchanged for `OFF` and deterministic readable cleanup for enabled modes.
   - OpenAI provider sends safe structured JSON `{mode, targetLanguage, segments}` and expects `segments[{index,text}]`.
6. Add `SubtitlePolishingPipelineStage` after `TARGET_SUBTITLE_EXPORT` and before `TRANSLATION_QUALITY_EVALUATION`.
7. Add `SubtitlePolishingCacheKeyService` and repository/cache service so repeated polishing inputs can be reused safely.
8. Store polished subtitles by replacing the target-language subtitle rows, then rewrite target subtitle JSON/SRT/VTT artifacts so downstream artifacts use polished text.

## Frontend Design

- Add a compact upload control next to translation style and subtitle burn-in style.
- Default to `OFF` to preserve current behavior and avoid surprise paid model calls.
- Show mode metadata in recent jobs, history rows, selected job detail, evidence/cache replay sections, and session/handoff panels.
- Never store raw polished subtitle text in recent-job local storage beyond existing subtitle APIs.

## Tests

- Backend unit tests:
  - enum parsing/defaulting rejects invalid upload values.
  - upload persists `subtitlePolishingMode`.
  - disabled polishing skips provider calls.
  - enabled polishing rewrites target subtitle artifacts and records model-call/cache evidence.
  - OpenAI provider sends mode and safe prompt-version metadata.
  - cache keys differ between `OFF`, `BALANCED`, and `STRICT`.
- Frontend tests:
  - upload sends `subtitlePolishingMode` only when non-default or always with the selected value, matching API design.
  - recent jobs preserve mode safely across old stored entries.
  - job detail renders mode metadata.
- Demo script tests:
  - `LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE=BALANCED` is included in upload form data.

## Documentation

- Update `README.md` with the upload form/curl/demo env examples.
- Update `docs/product/target-state.md` and `docs/product/roadmap.md` to mark subtitle polishing control as implemented after coding.
- Add a decision note in `docs/progress/decisions.md` explaining why polishing is a separate audited stage rather than hidden inside translation.
- Add validation evidence to `docs/progress/execution-log.md`.

## Validation

Run before merge:

```bash
mvn -pl LinguaFrame test
cd frontend && npm test -- --run
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh
git diff --check
```

## Acceptance Criteria

- A user can upload a video with `OFF`, `BALANCED`, or `STRICT` subtitle polishing mode.
- Completed jobs clearly show which mode was used.
- Downstream quality evaluation, TTS, burned video, reviewed handoff, and demo packages use the polished subtitle rows when polishing is enabled.
- OpenAI-backed polishing is audited as its own model-call operation with prompt version, usage/cost metadata when available, and safe summaries.
- Repeated compatible polishing inputs can hit provider cache without another paid model call.
- The feature branch is verified, committed, and merged back to `main`.
