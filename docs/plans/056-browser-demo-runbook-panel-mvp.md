# Browser Demo Runbook Panel MVP

**Goal:** Make the React demo explain the complete local demo path from inside the browser, so a reviewer can see startup status, readiness risks, sample-media guidance, and next commands without switching back to the README.

**Architecture:** Extend the existing React demo experience only. Reuse the current `GET /api/runtime/dependencies` readiness contract and existing frontend state instead of adding backend endpoints. Add a compact runbook panel beside the existing readiness/operator controls, with deterministic copy derived from runtime fields and static repo commands.

## Scope

- Add a browser-visible `Demo runbook` panel to the React app.
- Show the primary startup command:
  - `scripts/demo/start-local-demo.sh`
- Show next validation commands:
  - `scripts/demo/docker-e2e-success.sh`
  - `scripts/demo/docker-e2e-cache-hit.sh`
  - `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Show demo URLs:
  - `http://localhost:5173`
  - `http://localhost:8080/actuator/health`
- Convert runtime readiness data into human-readable demo guidance:
  - demo token gate configured or open
  - max upload duration and max file size
  - transcription, translation, TTS, evaluation provider modes
  - budget guard enabled or disabled
  - FFmpeg burn-in enabled or disabled
- Include sample-media guidance for:
  - quick generated sample used by `docker-e2e-success.sh`
  - full Tears of Steel local path override using `LINGUAFRAME_TEARS_SAMPLE_PATH`
- Add frontend tests that assert the runbook renders commands and reacts to readiness data.
- Update README, smoke-test checklist, Docker E2E guide, and execution log.

## Non-Goals

- Do not add copy-to-clipboard behavior in this slice.
- Do not execute shell commands from the browser.
- Do not expose secrets, raw `.env` values, absolute user media paths, or provider credentials.
- Do not add a backend endpoint unless the existing runtime readiness contract is insufficient.
- Do not create a marketing landing page.

## Implementation Steps

1. **Frontend runbook model and rendering**
   - Add small helper functions in `frontend/src/App.tsx` or a focused `frontend/src/domain/demoRunbook.ts` if the logic becomes bulky.
   - Render a `Demo runbook` panel near the current `Demo readiness` area.
   - Keep the panel dense and operational, not instructional marketing copy.

2. **Frontend tests**
   - Extend `frontend/src/App.test.tsx` to mock runtime readiness and assert:
     - startup and E2E commands render
     - provider modes render
     - upload duration/file-size limits render
     - budget guard and burn-in state render
     - readiness load failure still leaves the static command runbook visible

3. **Documentation**
   - Update README browser demo section to mention the in-app runbook panel.
   - Update `docs/agent/docker-e2e-demo.md` to describe using the panel before uploads.
   - Update `docs/agent/smoke-test-checklist.md` with expected runbook panel behavior.
   - Record implementation and validation in `docs/progress/execution-log.md`.

4. **Validation**
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- The browser demo includes a clear operational runbook panel.
- The panel remains useful even if runtime readiness loading fails.
- The panel uses existing sanitized runtime data and exposes no secrets.
- A reviewer can identify the one-command startup path, quick E2E path, cache-hit path, full-video path, and current runtime constraints from the UI.
- The feature is merged back to `main` after validation.
