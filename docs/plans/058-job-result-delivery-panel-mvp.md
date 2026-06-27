# Job Result Delivery Panel MVP

**Goal:** Turn the selected job detail view into a complete result-delivery surface that shows what LinguaFrame produced, what is still missing, and which download/preview/evidence actions are available for a demo reviewer.

**Architecture:** Extend the existing React job detail view and reuse current job detail, artifact, transcript, subtitle, diagnostics, and archive APIs. Do not add backend endpoints. Build a result-delivery summary above the existing artifact table that derives expected deliverables from known artifact types and preview data, then keeps the detailed artifact table and media previews available below.

## Scope

- Add a `Result delivery` panel to the selected job detail view.
- Summarize expected deliverables:
  - transcript JSON
  - source SRT
  - source VTT
  - target subtitle JSON
  - target SRT
  - target VTT
  - dubbing audio
  - burned video
  - worker summary
- Mark each deliverable as:
  - `Ready` when an artifact exists
  - `Preview only` when transcript/subtitle preview data exists but the corresponding artifact is absent
  - `Missing` when neither artifact nor preview exists
- Show top-level delivery counts:
  - generated artifact count
  - reused artifact/cache-hit count
  - missing deliverable count
  - model-call count and estimated cost
- Add prominent safe action links:
  - `Download result bundle`
  - `Download diagnostics`
  - direct links for each ready deliverable
- Surface evidence without secrets:
  - artifact SHA-256 short hash
  - generated versus reused cache state
  - source artifact id only as a `title` attribute, matching existing behavior
- Preserve existing artifact table and media preview sections.
- Update tests, README, Docker E2E guide, smoke-test checklist, and execution log.

## Non-Goals

- Do not add backend endpoints or change artifact persistence.
- Do not include uploaded source video in the result bundle.
- Do not expose object storage keys, local file paths, raw transcript text in diagnostics, demo tokens, API keys, or provider request payloads.
- Do not implement artifact regeneration or selective rerun controls in this slice.
- Do not redesign the whole app layout.

## Implementation Steps

1. **Result delivery model**
   - Add helper functions in `frontend/src/App.tsx` to map `JobArtifact[]`, transcript preview rows, subtitle preview rows, and `LocalizationJob` usage/cache summary into delivery rows and counts.
   - Keep the mapping local to the frontend because artifact types already exist in job artifact responses.

2. **Result delivery UI**
   - Render a `Result delivery` panel near the top of `JobDetail`, after usage summary and before quality/timeline detail.
   - Add compact status cards for counts and a dense deliverable list with status, artifact filename, hash, cache state, and download link.
   - Keep text operational and short.

3. **Frontend tests**
   - Extend `frontend/src/App.test.tsx` to assert:
     - complete artifact set shows ready deliverables and safe download links
     - transcript/subtitle preview without artifacts shows `Preview only`
     - missing deliverables are counted and shown as `Missing`
     - cache-hit artifacts show `Reused` and short SHA-256 hash
     - diagnostics and result bundle links remain visible

4. **Documentation**
   - Update README browser demo section to mention result delivery.
   - Update `docs/agent/docker-e2e-demo.md` with expected result-delivery browser behavior.
   - Update `docs/agent/smoke-test-checklist.md` with result-delivery checks.
   - Record red/green validation and final verification in `docs/progress/execution-log.md`.

5. **Validation**
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- The selected job view has a `Result delivery` panel that explains completed, preview-only, and missing outputs without reading the artifact table.
- A reviewer can download the result bundle, diagnostics, and individual ready deliverables from the panel.
- Cache reuse and content hashes are visible without exposing secrets or object keys.
- Existing artifact table, transcript/subtitle previews, media previews, retry, cancel, runbook, readiness, and upload preflight behavior continue to work.
- The feature is verified, committed on a feature branch, and merged back to `main`.
