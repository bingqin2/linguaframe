# Demo Replay Card Workspace Implementation Plan

**Goal:** Build a metadata-only replay card for any selected demo job so a presenter can reproduce the same settings, rerun the same source with a chosen profile, or compare the completed job against a new run without searching through separate panels.

**Architecture:** Add a read-only backend aggregate under the job API, render it in the selected-job frontend workspace, and expose the same payload through a terminal script. The card must reuse existing job detail, demo profile, source media, run matrix, comparison, snapshot, and presenter-pack facts instead of introducing a new persistence table.

## Scope

- Add `GET /api/jobs/{jobId}/demo-replay-card`.
- Include selected job identity, source/video metadata, target language, demo profile, style/glossary/subtitle/voice settings, cache/cost/quality summary, and safe package links.
- Generate recommended commands for browser replay, full Tears of Steel replay when applicable, launcher-based replay, and same-source comparison.
- Add a React `Demo replay card` panel on the selected job detail surface.
- Add `scripts/demo/demo-replay-card.sh` for terminal export and summary printing.
- Extend docs, progress records, and tests.

## Constraints

- Do not store raw OpenAI prompts, API keys, object storage credentials, or local absolute media paths in the replay card.
- Keep replay commands advisory when the original local upload path is unknown.
- Reuse existing package/download routes instead of creating duplicate artifacts.
- Keep this feature read-only; it must not enqueue or mutate jobs.

## Implementation Steps

### 1. Backend Replay Aggregate

- Add replay-card VO classes under the job API model package.
- Implement `DemoReplayCardService` and `DemoReplayCardServiceImpl`.
- Compose the aggregate from existing job detail, demo profile, run matrix, source media evidence, quality/cost/cache summaries, snapshot/presenter package routes, and comparison route hints.
- Add controller route `GET /api/jobs/{jobId}/demo-replay-card`.
- Add OpenAPI coverage and focused service/controller tests for completed, failed, queued, and missing-profile jobs.

### 2. Frontend Workspace Panel

- Add typed API client support for the replay-card endpoint.
- Render a `Demo replay card` panel in the selected job detail workflow.
- Show stable metadata, setting chips, readiness status, recommended replay commands, comparison guidance, and safe download links.
- Add copy buttons for commands and route links where existing UI patterns support it.
- Add focused React tests for loading, completed-job content, unavailable metadata, and command rendering.

### 3. Terminal Script Export

- Add `scripts/demo/demo-replay-card.sh`.
- Accept `LINGUAFRAME_DEMO_JOB_ID`, `LINGUAFRAME_API_BASE_URL`, and optional output directory.
- Download `demo-replay-card.json`, print a safe summary, and write files under `/tmp/linguaframe-demo/demo-replay-card/`.
- Add shell/client tests or syntax validation consistent with existing demo scripts.

### 4. Documentation And Progress

- Update `docs/agent/docker-e2e-demo.md` with when to use the replay card after a successful demo run.
- Update `README.md` demo testing notes if this becomes part of the recommended demo loop.
- Record implementation evidence in `docs/progress/execution-log.md`.
- Record any durable design decision in `docs/progress/decisions.md`.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=DemoReplayCardServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests test`
- Frontend focused tests for the selected-job replay-card panel.
- `npm --prefix frontend run build`
- `bash -n scripts/demo/demo-replay-card.sh`
- Existing full backend and frontend suites before merge.
- Post-merge rerun focused backend/frontend/script validation on `main`.

## Completion Criteria

- A completed demo job has one browser panel and one terminal command that explain how to reproduce or compare the run.
- The replay card contains only safe metadata and links.
- The same endpoint powers browser and terminal output.
- The branch is verified, merged back to `main`, and the post-merge validation is recorded.
