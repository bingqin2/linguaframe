# Frontend Local Fallback Demo Runner MVP

**Goal:** Keep the browser demo reachable at `http://localhost:5173` even when Docker cannot build the frontend image because the Node base image registry or mirror is unavailable.

**Architecture:** Add a small local frontend runner script that validates dependencies, installs packages only when needed, and starts Vite on the expected demo port. Update private-demo preflight so a missing frontend points contributors to either Docker frontend startup or the local fallback. Keep Docker Compose unchanged; this slice adds a reliable fallback path rather than replacing the container path.

## Scope

- Add `scripts/demo/frontend-local-dev.sh`.
- The script runs from the repo root, checks `node` and `npm`, verifies `frontend/package.json`, installs dependencies when `frontend/node_modules` is missing, and starts `npm run dev -- --host 0.0.0.0 --port 5173`.
- If port 5173 is already in use, print a clear message instead of silently choosing another port.
- Update `scripts/demo/private-demo-preflight.sh` frontend failure text to include the local fallback command.
- Update README, Docker E2E runbook, smoke-test checklist, and execution log.
- Do not change frontend application behavior or Docker Compose service definitions.

## Non-Goals

- Do not vendor `node_modules`.
- Do not change the Docker frontend base image in this slice.
- Do not start the frontend automatically from preflight.
- Do not add a new package manager; continue using npm and the existing `package-lock.json`.

## Implementation Steps

1. **Local frontend runner**
   - Create `scripts/demo/frontend-local-dev.sh`.
   - Require `node` and `npm`.
   - Run `npm ci` inside `frontend/` only when `node_modules` is absent.
   - Check whether `http://localhost:5173` already responds; if it does, print that the frontend is already reachable and exit 0.
   - Check whether port 5173 is occupied but not responding as HTTP; fail with a message asking the contributor to free the port or set `LINGUAFRAME_FRONTEND_PORT`.
   - Start Vite on `LINGUAFRAME_FRONTEND_PORT`, default `5173`.

2. **Preflight guidance**
   - Update `check_frontend` in `scripts/demo/private-demo-preflight.sh` so failure output includes:
     - Docker path: `docker compose --env-file <env> up -d --build linguaframe-frontend`
     - Local fallback path: `scripts/demo/frontend-local-dev.sh`
   - Keep the preflight as a checker only; it must not launch long-running processes.

3. **Documentation**
   - Add README instructions for the local fallback when Docker frontend build fails.
   - Add the fallback to `docs/agent/docker-e2e-demo.md`.
   - Add smoke-test checklist expectations for both Docker frontend and local Vite fallback.
   - Record validation in `docs/progress/execution-log.md`.

4. **Validation**
   - Run `bash -n scripts/demo/private-demo-preflight.sh scripts/demo/frontend-local-dev.sh`.
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- A contributor can recover from Docker frontend image pull/build failure with one documented command.
- The fallback serves the existing Vite app on `http://localhost:5173`.
- Preflight explains both startup paths when the frontend is missing.
- No generated dependencies or local runtime artifacts are committed.
