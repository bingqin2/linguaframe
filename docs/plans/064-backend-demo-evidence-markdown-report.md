# Backend Demo Evidence Markdown Report

**Goal:** Provide a backend-generated Markdown evidence report for each localization job so demos can share a readable, reproducible, sanitized proof of execution without relying only on browser-local state.

**Branch:** `codex-backend-demo-evidence-markdown-report`

## Scope

- Add a downloadable backend Markdown report derived from the existing sanitized diagnostics model.
- Expose the report in the React demo next to the current browser evidence and diagnostics actions.
- Extend the successful Docker demo script so terminal runs download and validate the Markdown report.
- Document the report in README, the Docker demo runbook, the smoke checklist, roadmap, decisions, and execution log.

## Non-Goals

- Do not store Markdown reports as durable artifacts.
- Do not include raw transcript text, raw subtitle text, uploaded media bytes, object storage keys, local file paths, provider payloads, credentials, API tokens, or raw user media paths.
- Do not replace the existing diagnostics JSON endpoint; keep JSON as the machine-readable source.

## Implementation Steps

1. **Backend report generation** - Completed
   - Add a job evidence report service that builds deterministic Markdown from `JobDiagnosticsReportVo`.
   - Include job id, video id, target language, status, retry/failure data, timeline stages, usage/cost, cache counts, quality evaluation summary, artifact filenames, artifact types, sizes, short SHA-256 evidence, cache state, and safe download routes.
   - Add `GET /api/jobs/{jobId}/evidence/markdown/download` returning `text/markdown` with an attachment filename like `linguaframe-job-<jobId>-evidence.md`.

2. **Backend tests and OpenAPI** - Completed
   - Add controller/service tests proving the endpoint returns expected Markdown for completed and failed jobs.
   - Add safety assertions that forbidden values never appear in the report.
   - Update OpenAPI contract tests to include the new route.

3. **Frontend integration** - Completed
   - Add `jobEvidenceMarkdownDownloadUrl(jobId)` to `frontend/src/api/linguaframeApi.ts`.
   - Add a `Download backend evidence` link in the `Demo evidence` panel.
   - Extend frontend tests to assert the link points to the new endpoint and does not affect the existing copy/JSON export actions.

4. **Docker demo evidence** - Completed
   - Extend `scripts/demo/lib/linguaframe-demo.sh` with a helper to download job evidence Markdown.
   - Extend `scripts/demo/docker-e2e-success.sh` to save `/tmp/linguaframe-demo/job-evidence.md`.
   - Validate the file contains key execution markers and excludes unsafe markers.

5. **Documentation** - Completed
   - Update README demo sections to explain when to use browser evidence, backend Markdown evidence, and diagnostics JSON.
   - Update `docs/agent/docker-e2e-demo.md` and `docs/agent/smoke-test-checklist.md` with the command output and expected file.
   - Record the architecture decision and validation evidence in `docs/progress/`.
   - Update `docs/product/roadmap.md` after implementation.

## Verification - Completed

- [x] `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobQueryServiceTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- [x] `mvn -pl LinguaFrame test`
- [x] `cd frontend && npm run test:run -- App linguaFrameApi`
- [x] `cd frontend && npm run build`
- [x] `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh`
- [x] `docker compose --env-file .env.example config --quiet`
- [x] `git diff --check`

## Done Criteria - Completed

- [x] A reviewer can download safe backend-authored Markdown evidence for any existing job.
- [x] The browser exposes the backend Markdown report without removing existing local evidence export.
- [x] The Docker success script downloads and validates `job-evidence.md`.
- [x] Tests and docs cover the new report contract, safety rules, and demo workflow.
- [x] The feature branch is committed, verified, and merged back to `main`.
