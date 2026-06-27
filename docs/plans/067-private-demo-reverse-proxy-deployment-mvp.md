# Private Demo Reverse Proxy Deployment MVP Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a private-demo deployment overlay with HTTPS reverse proxy routing so LinguaFrame can be prepared for a single-owner server demo without changing the default local Docker workflow.

**Architecture:** Keep `docker-compose.yml` as the local development stack. Add a separate deployment overlay and proxy config that route one public origin to the React frontend and `/api`, `/actuator`, `/swagger-ui`, and `/v3/api-docs` to the backend. Add a deployment preflight script that validates Compose rendering, required secrets, reverse-proxy routes, backend/frontend service shape, and safe defaults before any media upload or provider call.

**Tech Stack:** Docker Compose, Caddy reverse proxy, Spring Boot, React/Vite, Bash, Markdown.

## Global Constraints

- Do not change the default local `docker compose --env-file .env.example up` behavior.
- Keep the private deployment owner-only; do not add public user accounts, JWT, billing, or enterprise permissions.
- Keep secrets out of git. Add example env files only with placeholders.
- Preserve current private demo token behavior for `/api/**`; proxy config must not bypass backend auth.
- Do not expose MySQL, Redis, RabbitMQ, MinIO, or backend ports publicly in the private-demo overlay unless explicitly documented as local-only.
- The feature must include deployment config, preflight, docs, tests/validation, roadmap/progress updates, and merge tracking.

---

## Task 1: Compose Overlay And Proxy Config

**Files:**
- Create: `deploy/private-demo/docker-compose.private-demo.yml`
- Create: `deploy/private-demo/Caddyfile`
- Create: `.env.private-demo.example`
- Modify: `.gitignore`
- Modify: `.dockerignore`

**Steps:**

- [x] Add a Compose overlay that introduces a `linguaframe-proxy` service using Caddy.
- [x] Configure the proxy to publish `${LINGUAFRAME_PUBLIC_HTTP_PORT:-80}:80` and `${LINGUAFRAME_PUBLIC_HTTPS_PORT:-443}:443`.
- [x] Route `/api/*`, `/actuator/*`, `/swagger-ui/*`, `/v3/api-docs*`, and `/swagger-ui.html` to `linguaframe-backend:8080`.
- [x] Route all other requests to `linguaframe-frontend:5173`.
- [x] In the private overlay, remove host port publication for backend/frontend where possible and rely on the proxy service.
- [x] Add Caddy data/config volumes so certificates and proxy state can persist across restarts.
- [x] Add `.env.private-demo.example` with placeholders for domain, demo token, public ports, conservative media limits, retention flags, OpenAI placeholders, and existing service credentials.
- [x] Update ignore rules so `.env.private-demo.example` is tracked while real `.env.private-demo` remains ignored.

## Task 2: Private Deployment Preflight

**Files:**
- Create: `scripts/demo/private-demo-deploy-preflight.sh`
- Modify if useful: `scripts/demo/private-demo-preflight.sh`

**Steps:**

- [x] Add a script that accepts `LINGUAFRAME_ENV_FILE` defaulting to `.env.private-demo`.
- [x] Fail with a clear message when the env file is missing.
- [x] Require `LINGUAFRAME_PUBLIC_DOMAIN` and a non-empty `LINGUAFRAME_DEMO_ACCESS_TOKEN`.
- [x] Render Compose with `docker compose --env-file "$ENV_FILE" -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config`.
- [x] Verify rendered config includes `linguaframe-proxy`, does not publish backend/frontend host ports in the private overlay, and includes proxy routes to backend and frontend.
- [x] Reuse the existing runtime freshness/live-check expectations by printing the command sequence operators should run after starting the stack.
- [x] Ensure script output never prints the demo token, OpenAI key, database password, MinIO secret, or raw `.env` contents.

## Task 3: Documentation And Runbook

**Files:**
- Create: `docs/deployment/private-demo.md`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/067-private-demo-reverse-proxy-deployment-mvp.md`

**Steps:**

- [x] Document the private-demo deployment shape: public Caddy proxy, internal frontend/backend, internal MySQL/Redis/RabbitMQ/MinIO.
- [x] Document setup commands: copy env example, fill domain/token/secrets, run deployment preflight, package backend jar, start compose with both files.
- [x] Document expected URLs and token behavior for browser, Swagger, `/api/runtime/dependencies`, and downloads/SSE.
- [x] Document safe defaults: conservative upload duration, demo token required, retention default-off or dry-run, OpenAI connectivity check optional.
- [x] Document non-goals: no public multi-user launch, no JWT users, no billing, no production SRE automation.
- [x] Update roadmap Phase 9 status for production Compose/simple server deployment and HTTPS reverse proxy.

## Task 4: Validation

**Files:**
- Modify as needed: `docs/progress/execution-log.md`
- Modify as needed: `docs/plans/067-private-demo-reverse-proxy-deployment-mvp.md`

**Steps:**

- [x] Run `bash -n scripts/demo/private-demo-deploy-preflight.sh scripts/demo/private-demo-preflight.sh`.
- [x] Run `docker compose --env-file .env.example config --quiet` to prove local default Compose still renders.
- [x] Run `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet`.
- [x] Run `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-deploy-preflight.sh`.
- [x] Run `git diff --check`.
- [x] If code paths are not touched, document why Maven/frontend tests are not required; otherwise run focused tests for touched code.

## Done Criteria

- [x] Local Compose behavior remains unchanged.
- [x] Private-demo Compose overlay renders a proxy-fronted stack.
- [x] Private deployment preflight catches missing domain/token and validates the overlay shape without exposing secrets.
- [x] Documentation gives a repeatable path from repo checkout to private demo URL readiness.
- [x] Roadmap, decisions, execution log, and this plan are updated.
- [ ] The feature branch is committed, verified, and merged back to `main`.
