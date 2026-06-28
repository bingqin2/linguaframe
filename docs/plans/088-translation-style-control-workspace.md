# Translation Style Control Workspace

## Goal

Add an end-to-end translation style control so a demo operator can choose how target subtitles are localized before a job starts. The style must become part of the job contract, provider prompt, cache identity, UI, demo scripts, and evidence surfaces.

This feature moves the product beyond a fixed translation path toward configurable localization and subtitle polishing behavior.

## User-Facing Behavior

- Upload form exposes a translation style selector with:
  - `NATURAL`: default, idiomatic subtitle translation.
  - `FORMAL`: more polished and presentation-safe wording.
  - `CONCISE`: shorter subtitle text for readability.
- Existing jobs and old database rows resolve to `NATURAL`.
- Job list, job detail, source evidence, handoff/evidence exports, and recent-job history show the selected style.
- Demo upload scripts accept `LINGUAFRAME_DEMO_TRANSLATION_STYLE` and default to `NATURAL`.
- Invalid style values are rejected before creating a job.

## Backend Scope

- Add a `TranslationStyle` enum under the job domain with display-safe metadata or prompt instructions.
- Add `translation_style` to `localization_jobs` through a new Flyway migration.
- Thread the value through:
  - upload controller and upload service,
  - `LocalizationJobRecord`,
  - repository save/find/list mappings,
  - `LocalizationJobVo`, `LocalizationJobSummaryVo`, and upload response VOs,
  - queued job message and dispatch outbox where job identity is serialized,
  - diagnostics, evidence, handoff, worker summary, and delivery metadata.
- Update `TranslationProvider` to receive the style.
- Update demo and OpenAI translation providers:
  - include style in safe model-call input summaries,
  - include style instructions in the OpenAI request payload,
  - keep raw source text out of logs and public evidence.
- Include style in translation cache lookup identity so `NATURAL`, `FORMAL`, and `CONCISE` jobs cannot reuse each other's provider cache result.

## Frontend Scope

- Add a stable style selector near target language and TTS voice.
- Pass style through `uploadMedia`.
- Preserve selected style in local recent jobs.
- Display style in:
  - job history rows,
  - recent job rows,
  - selected job metadata,
  - demo evidence JSON/Markdown generation,
  - cache replay evidence where job inputs are compared.
- Keep labels compact so current demo dashboard layout does not become a marketing page.

## Documentation & Demo Scripts

- Document `LINGUAFRAME_DEMO_TRANSLATION_STYLE` in the README demo/runtime section.
- Update demo script helpers so full-video and quick demo runs can pass style.
- Update `docs/product/target-state.md` and `docs/product/roadmap.md` to mark translation style control as implemented.
- Record the implementation decision in `docs/progress/decisions.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

## Tests

- Backend:
  - upload accepts default and explicit styles,
  - invalid styles fail without creating jobs,
  - repository persists and reads style,
  - job list/detail APIs expose style,
  - translation cache key changes when style changes,
  - OpenAI request payload includes style without leaking raw source in summaries,
  - demo provider records style in safe audit input.
- Frontend:
  - API sends style in multipart upload,
  - upload form calls API with selected style,
  - recent/history/detail views render style.

## Validation Commands

Run these before committing:

```bash
mvn test
cd frontend && npm test -- --run
cd frontend && npm run build
```

If Docker is already running locally, also run:

```bash
docker compose --env-file .env up -d --force-recreate linguaframe-backend
LINGUAFRAME_DEMO_TRANSLATION_STYLE=FORMAL scripts/demo/docker-e2e-success.sh
```

## Acceptance Criteria

- A new upload can choose translation style and the choice is visible throughout the job lifecycle.
- The translation provider receives the style and OpenAI requests include a style instruction.
- Translation provider cache keys are style-sensitive.
- Existing jobs remain readable with `NATURAL` as the default.
- Frontend and demo scripts support the feature without requiring manual API calls.
- The feature branch is merged back to `main` after tests pass.
