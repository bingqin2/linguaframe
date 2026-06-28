# Demo Profile Comparison Workspace

## Goal

Add a reviewer-facing comparison workspace so LinguaFrame can explain how two completed demo runs differ when they use different profiles or manual upload settings.

This should make the demo more credible after adding reusable profiles: one run can be the baseline, another can be `tears-showcase` or `concise-review`, and the browser/terminal evidence can compare configuration, quality, cost, cache reuse, and delivery readiness without reading logs.

## Scope

- Add a backend comparison endpoint for two jobs.
- Compare safe job metadata only: source video id, target language, demo profile id, voice, translation style, glossary count/hash, subtitle style, polishing mode, status, quality score, cost/model-call summary, cache summary, and reviewed delivery readiness.
- Add a React selected-job comparison panel that lets the user choose another known job from server history/recent jobs.
- Add copy/download comparison evidence in JSON and Markdown.
- Add terminal script helpers for comparing two job ids after demo runs.
- Update README, Docker E2E guide, roadmap, target state, decisions, and execution log.

## Backend Plan

1. Create comparison VOs:
   - `JobComparisonVo`
   - `JobComparisonJobVo`
   - `JobComparisonDeltaVo`
   - `JobComparisonSettingDiffVo`
2. Add `JobComparisonService` and implementation that loads both jobs through existing query services and artifact/manifest services.
3. Add `GET /api/jobs/{jobId}/comparison/{comparisonJobId}` returning JSON.
4. Add `GET /api/jobs/{jobId}/comparison/{comparisonJobId}/markdown/download` returning metadata-only Markdown.
5. Keep comparison read-only and safe: no raw transcript text, raw subtitles, object keys, local paths, provider payloads, credentials, or media bytes.

## Frontend Plan

1. Add API/domain types for job comparison.
2. Add a `Demo comparison` panel near cache replay / evidence surfaces.
3. Let the user choose a comparison job from known server history plus browser recent jobs.
4. Show:
   - profile/settings differences,
   - status and delivery readiness,
   - quality score/verdict delta,
   - model-call and cost delta,
   - artifact/provider cache comparison.
5. Add copy Markdown and download JSON/Markdown actions.

## Demo Script Plan

1. Add reusable shell helpers:
   - `download_job_comparison_json BASE_JOB_ID COMPARISON_JOB_ID OUTPUT_PATH`
   - `download_job_comparison_markdown BASE_JOB_ID COMPARISON_JOB_ID OUTPUT_PATH`
   - `print_job_comparison_summary COMPARISON_JSON_PATH`
2. Extend script tests with fixture JSON proving summary output includes profile, quality, model-call, cost, and cache deltas.
3. Document manual usage instead of forcing every E2E script to create two jobs.

## Testing

- Backend service tests for same/different profile comparisons and safe Markdown output.
- Controller tests for JSON and Markdown endpoints.
- Runtime/OpenAPI route tests for the new endpoints.
- Frontend API tests for comparison fetch/download URLs.
- React tests for selecting a comparison job and rendering deltas.
- Script client tests for comparison download URL construction and summary output.
- Full validation: `mvn -pl LinguaFrame test`, `cd frontend && npm test -- --run`, `cd frontend && npm run build`, `bash scripts/demo/test-linguaframe-demo-client.sh`, `bash -n ...`, and `git diff --check`.

## Acceptance Criteria

- A user can open one job in the browser, select another job, and understand how the two runs differ.
- Comparison evidence is downloadable and safe for reviewer handoff.
- The comparison uses current job/profile metadata but does not change provider cache identity or mutate either job.
- Terminal users can compare two known job ids with documented commands.
- The feature is merged back to `main` after verification.
