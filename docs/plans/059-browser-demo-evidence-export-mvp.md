# Browser Demo Evidence Export MVP

**Goal:** Let a reviewer export safe, browser-visible evidence for a selected job without using terminal commands.

**Architecture:** Extend the existing React selected-job view. Reuse loaded job detail, artifacts, transcript preview, subtitle preview, quality evaluation, result delivery, diagnostics URL, and artifact archive URL. Do not add backend endpoints in this slice.

## Scope

- Add a `Demo evidence` panel to the selected job detail view.
- Generate a safe evidence summary from already-loaded frontend state:
  - job id, video id, target language, status, retry count
  - failure stage/reason when present
  - timeline stage/status list
  - usage summary, model-call count, estimated cost, cache counts
  - quality evaluation score/verdict when present
  - artifact count, artifact type/filename/size/short SHA-256/cache state
  - transcript/subtitle preview segment counts only, not raw text
  - result bundle URL and diagnostics URL
- Provide two browser actions:
  - `Copy evidence` copies a Markdown summary to the clipboard when available.
  - `Download evidence JSON` downloads a local JSON file with the same safe metadata.
- Show copy/download status and a concise fallback message when Clipboard API is unavailable.
- Keep secrets and sensitive data out of exported evidence:
  - no API keys, demo tokens, object storage keys, local paths, raw transcript text, raw subtitle text, provider payloads, or uploaded media bytes.
- Update frontend tests, README, Docker E2E guide, smoke-test checklist, and execution log.

## Non-Goals

- Do not add backend evidence endpoints.
- Do not download or embed artifact bytes in the evidence file.
- Do not include raw transcript or subtitle text.
- Do not implement share links, cloud publishing, email, PDF generation, or screenshots.
- Do not redesign the selected-job layout beyond adding the evidence panel.

## Implementation Steps

1. **Evidence model**
   - Add frontend helper functions in `frontend/src/App.tsx` to build a safe evidence object and Markdown summary from `LocalizationJob`, `JobArtifact[]`, transcript count, subtitle count, and selected language.
   - Keep generated evidence deterministic so tests can assert exact lines.

2. **Evidence UI**
   - Add a `Demo evidence` panel near `Result delivery`.
   - Render the Markdown summary in a compact read-only preview.
   - Add `Copy evidence` and `Download evidence JSON` actions.
   - Disable copy gracefully when `navigator.clipboard.writeText` is unavailable.

3. **Frontend tests**
   - Extend `frontend/src/App.test.tsx` to verify:
     - the panel renders safe job evidence for a completed job
     - raw transcript/subtitle text is not included in evidence
     - copy calls `navigator.clipboard.writeText` with Markdown
     - JSON download creates a same-page object URL and safe filename
     - unavailable clipboard state is visible and does not break the panel

4. **Documentation**
   - Update README with the browser evidence export behavior.
   - Update `docs/agent/docker-e2e-demo.md` with how to use evidence export after opening a job.
   - Update `docs/agent/smoke-test-checklist.md` with browser evidence checks.
   - Record red/green validation in `docs/progress/execution-log.md`.

5. **Validation**
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- A completed or failed selected job has a `Demo evidence` panel in the browser.
- A reviewer can copy Markdown evidence or download JSON evidence without terminal access.
- The evidence includes enough metadata to prove pipeline execution, artifacts, usage, cache state, and safe download routes.
- The evidence does not expose secrets, local paths, raw transcript/subtitle text, provider payloads, object keys, or media bytes.
- Existing upload, job detail, result delivery, artifact table, media previews, diagnostics, runbook, and retry behavior continue to work.
- The feature is verified, committed on a feature branch, and merged back to `main`.
