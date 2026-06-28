# Translation Glossary Control Workspace

## Goal

Add an end-to-end translation glossary control so a demo operator can provide a small terminology guide before a job starts. The glossary must become part of the job contract, OpenAI translation payload, translation cache identity, browser UI, demo scripts, and safe evidence surfaces.

This feature makes translation behavior more controllable for real demo material with names, product terms, and sci-fi vocabulary without turning LinguaFrame into a full terminology-management system.

## User-Facing Behavior

- Upload form exposes an optional glossary textarea near target language and translation style.
- Operators enter one mapping per line:
  - `source term => target term`
  - `source term = target term`
- Blank glossary values default to no glossary.
- Existing jobs and old database rows resolve to no glossary.
- Invalid entries are rejected before storage:
  - missing separator,
  - blank source term,
  - blank target term,
  - too many entries,
  - entry or total text too long.
- Job list, job detail, recent-job history, browser evidence, backend evidence, diagnostics, worker summary, delivery metadata, and demo run package metadata show safe glossary metadata, not raw transcript text.
- Demo upload scripts accept `LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY` as inline text and `LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE` as a local file path. The inline value wins when both are set.

## Backend Scope

- Add a `TranslationGlossaryEntryBo` record with `sourceTerm` and `targetTerm`.
- Add a `TranslationGlossaryParser` service or utility with strict limits:
  - maximum 20 entries,
  - maximum 80 characters per source or target term,
  - maximum 2000 characters total raw input.
- Add `translation_glossary_json` and `translation_glossary_hash` to `localization_jobs` through a new Flyway migration.
- Thread glossary values through:
  - upload controller and upload service,
  - `LocalizationJobRecord`,
  - repository save/find/list mappings,
  - `LocalizationJobVo`, `LocalizationJobSummaryVo`, and upload response VOs,
  - queued job message and dispatch outbox,
  - diagnostics, evidence, handoff, worker summary, delivery metadata, demo run package, and AI audit package manifest.
- Store safe metadata surfaces as:
  - `translationGlossaryEntryCount`,
  - `translationGlossaryHash`,
  - optional compact preview limited to glossary terms only when already user-supplied.
- Update `TranslationProvider` to receive glossary entries.
- Update demo and OpenAI translation providers:
  - include glossary metadata in safe input summaries,
  - include glossary entries in the OpenAI user payload under `glossary`,
  - instruct the model to preserve glossary mappings when translating,
  - keep raw transcript text out of logs and public evidence.
- Include glossary hash in translation cache identity so jobs with different glossary mappings cannot reuse each other's translated subtitle provider cache result.

## Frontend Scope

- Add a compact glossary textarea under translation style.
- Show short helper copy through label/placeholder only, not a marketing explanation.
- Pass glossary text through `uploadMedia`.
- Preserve glossary metadata in local recent jobs:
  - entry count,
  - hash,
  - no raw glossary text in local evidence exports except the active upload form state.
- Display glossary count/hash in:
  - job history rows,
  - recent job rows,
  - selected job metadata,
  - browser-generated demo evidence JSON and Markdown,
  - cache replay evidence where job inputs are compared.
- Keep current dense dashboard layout; do not add a separate glossary management page.

## Documentation & Demo Scripts

- Document the upload glossary format in README.
- Document `LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY` and `LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE` in demo sections.
- Update demo script helpers so quick, full-video, and OpenAI smoke demo runs can pass glossary text.
- Update `docs/product/target-state.md` and `docs/product/roadmap.md` to mark translation glossary control as implemented.
- Record the implementation decision in `docs/progress/decisions.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

## Tests

- Backend:
  - parser accepts `=>` and `=` mappings,
  - parser rejects malformed, blank, too-long, and too-many entries,
  - upload accepts default empty and explicit glossary values,
  - invalid glossary fails without storing media,
  - repository persists and reads glossary JSON/hash,
  - job list/detail APIs expose glossary count/hash,
  - dispatch payload carries glossary JSON/hash,
  - translation cache key changes when glossary changes,
  - OpenAI request payload includes glossary entries and style,
  - demo provider safe audit input includes glossary count/hash,
  - evidence, diagnostics, handoff, and worker summary output include safe glossary metadata.
- Frontend:
  - API sends glossary in multipart upload only when non-blank,
  - upload form calls API with glossary text,
  - recent/history/detail/evidence/cache-replay views render glossary metadata.
- Demo scripts:
  - shell syntax checks pass,
  - demo upload request includes inline or file glossary when configured.

## Validation Commands

Run these before committing:

```bash
mvn -pl LinguaFrame test
cd frontend && npm test -- --run
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh
git diff --check
```

If Docker is already running locally, also run:

```bash
docker compose --env-file .env up -d --force-recreate linguaframe-backend
LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY=$'Maya => 玛雅\nTears of Steel => 钢铁之泪' scripts/demo/docker-e2e-success.sh
```

## Acceptance Criteria

- A new upload can include glossary mappings and the choice is visible as safe metadata throughout the job lifecycle.
- The OpenAI translation provider receives glossary entries in the request payload.
- Translation provider cache keys are glossary-sensitive.
- Existing jobs remain readable with an empty glossary.
- Frontend and demo scripts support the feature without requiring manual API calls.
- The feature branch is merged back to `main` after validation passes.
