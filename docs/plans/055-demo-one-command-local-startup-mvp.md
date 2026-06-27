# Demo One-Command Local Startup MVP

**Goal:** Provide one reliable command that prepares the backend, starts the browser demo, and runs preflight so contributors can open LinguaFrame locally without remembering the full command sequence.

**Architecture:** Add an orchestration script that composes existing scripts rather than duplicating their logic. The script packages the backend jar, recreates the backend container with the chosen env file, ensures the frontend is reachable by using the local Vite fallback when needed, and runs private-demo preflight. It prints the final browser URL and next demo commands, while leaving long-running frontend work in the foreground when it has to start Vite locally.

## Scope

- Add `scripts/demo/start-local-demo.sh`.
- Default to `.env.example`, with `LINGUAFRAME_ENV_FILE` override.
- Package backend with Maven before Docker backend recreate.
- Run `docker compose --env-file <env> up -d --build linguaframe-backend`.
- Check `http://localhost:5173`; if unavailable, start `scripts/demo/frontend-local-dev.sh` in the background for the current script session and wait until it responds.
- Run `scripts/demo/private-demo-preflight.sh`.
- Print final URLs and suggested validation commands:
  - `http://localhost:5173`
  - `scripts/demo/docker-e2e-success.sh`
  - `scripts/demo/docker-e2e-cache-hit.sh`
- Stop only the frontend process that this script started when the script exits; do not stop Docker services.

## Non-Goals

- Do not replace Docker Compose or existing specialized demo scripts.
- Do not run upload/e2e scripts automatically.
- Do not commit generated artifacts, logs, `node_modules`, or local `.env`.
- Do not push, deploy, or expose the demo publicly.

## Implementation Steps

1. **Startup orchestration script**
   - Create `scripts/demo/start-local-demo.sh`.
   - Add `--help` usage.
   - Require `mvn`, `docker`, `curl`, `python3`, `node`, and `npm`.
   - Resolve backend/frontend ports from the env file using the same simple parser pattern as `private-demo-preflight.sh`.
   - Package the backend jar.
   - Recreate `linguaframe-backend`.
   - If the frontend already responds, reuse it.
   - If the frontend does not respond, start `scripts/demo/frontend-local-dev.sh` in the background and wait for readiness.
   - Run `LINGUAFRAME_ENV_FILE=<env> scripts/demo/private-demo-preflight.sh`.

2. **Documentation**
   - Add README quick-start instructions for the one-command local demo.
   - Add `docs/agent/docker-e2e-demo.md` startup section that explains when to use the orchestrator versus individual scripts.
   - Add smoke-test checklist expectations for `start-local-demo.sh`.
   - Record validation in `docs/progress/execution-log.md`.

3. **Validation**
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh`.
   - Run `scripts/demo/start-local-demo.sh --help`.
   - Run `cd frontend && npm run test:run -- App`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - If local Docker backend is available, run `scripts/demo/start-local-demo.sh` and confirm preflight passes.
   - Run `git diff --check`.

## Acceptance Criteria

- A contributor can run one command to package/recreate backend, make the frontend reachable, and verify preflight.
- The script does not leave unmanaged foreground/background processes beyond the frontend process it explicitly starts for the session.
- Existing specialized scripts remain usable independently.
- Documentation clearly shows the one-command path and the lower-level fallback commands.
