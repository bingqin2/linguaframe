# Subtitle Burn-In Style Preset Workspace

## Goal

Add an end-to-end subtitle burn-in style preset so a demo operator can choose how subtitles appear in the generated video before a job starts. The selected preset must become part of the job contract, FFmpeg burn-in command, artifact cache identity, UI, demo scripts, and evidence surfaces.

This feature makes the output video visibly configurable while keeping scope to production-safe presets instead of a full subtitle style editor.

## User-Facing Behavior

- Upload form exposes a subtitle style preset selector with:
  - `STANDARD`: default readable white subtitles.
  - `LARGE`: larger subtitles for presentations and screen recordings.
  - `HIGH_CONTRAST`: stronger outline/background contrast for busy footage.
- Existing jobs and old database rows resolve to `STANDARD`.
- Job list, job detail, recent-job history, demo evidence, diagnostics, worker summary, and delivery metadata show the selected preset.
- Demo upload scripts accept `LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET` and default to `STANDARD`.
- Invalid preset values are rejected before creating a job.

## Backend Scope

- Add a `SubtitleStylePreset` enum under the job domain with safe FFmpeg/libass style metadata.
- Add `subtitle_style_preset` to `localization_jobs` through a new Flyway migration.
- Thread the value through:
  - upload controller and upload service,
  - `LocalizationJobRecord`,
  - repository save/find/list mappings,
  - `LocalizationJobVo`, `LocalizationJobSummaryVo`, and upload response VOs,
  - queued job message and dispatch outbox,
  - diagnostics, evidence, handoff, worker summary, delivery metadata, and demo run package output.
- Extend `BurnInSubtitlesCommand` and `SubtitleBurnInPipelineStage` so FFmpeg receives the selected preset for generated burned videos.
- Update `FfmpegSubtitleBurnInServiceImpl` to generate a `subtitles=...:force_style=...` filter from preset metadata, with path/style escaping covered by tests.
- Include subtitle style preset in artifact cache lookup identity for `BURNED_VIDEO` so repeat jobs with different presets cannot reuse the wrong output video.
- Keep extracted audio and dubbing audio cache behavior unchanged.

## Frontend Scope

- Add a compact subtitle style preset selector near target language, translation style, and TTS voice.
- Pass the preset through `uploadMedia`.
- Preserve selected preset in local recent jobs.
- Display the preset in:
  - job history rows,
  - recent job rows,
  - selected job metadata,
  - media delivery context where generated burned video is shown,
  - browser-generated demo evidence JSON and Markdown,
  - cache replay evidence where job inputs are compared.
- Keep current dashboard density and avoid a full style-editor workflow.

## Documentation & Demo Scripts

- Document `LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET` in README demo/runtime sections.
- Update demo script helpers so quick, full-video, and OpenAI smoke demo runs can pass the preset.
- Update `docs/product/target-state.md` and `docs/product/roadmap.md` to mark preset-based subtitle burn-in styling as implemented.
- Record implementation decisions in `docs/progress/decisions.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

## Tests

- Backend:
  - upload accepts default and explicit presets,
  - invalid presets fail without creating jobs,
  - repository persists and reads the preset,
  - job list/detail APIs expose the preset,
  - FFmpeg command includes the expected `force_style` for each preset,
  - burn-in pipeline passes the job preset into `BurnInSubtitlesCommand`,
  - `BURNED_VIDEO` artifact cache reuse is preset-sensitive,
  - evidence, diagnostics, handoff, and worker summary output include the preset.
- Frontend:
  - API sends the preset in multipart upload,
  - upload form calls API with the selected preset,
  - recent/history/detail/media/evidence views render the preset.
- Demo scripts:
  - shell syntax checks pass,
  - demo upload request includes `LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET` when set.

## Validation Commands

Run these before committing:

```bash
mvn -pl LinguaFrame test
cd frontend && npm test -- --run
cd frontend && npm run build
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh
git diff --check
```

If Docker is already running locally, also run:

```bash
docker compose --env-file .env up -d --force-recreate linguaframe-backend
LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET=HIGH_CONTRAST scripts/demo/docker-e2e-success.sh
```

## Acceptance Criteria

- A new upload can choose a subtitle burn-in preset and the choice is visible throughout the job lifecycle.
- Generated burned videos use the selected preset in the FFmpeg subtitle filter.
- `BURNED_VIDEO` artifact cache reuse is safe across different subtitle presets.
- Existing jobs remain readable with `STANDARD` as the default.
- Frontend and demo scripts support the feature without requiring manual API calls.
- The feature branch is merged back to `main` after validation passes.
