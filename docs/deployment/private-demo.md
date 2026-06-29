# Private Demo Deployment

This guide prepares LinguaFrame for a single-owner private demo on one server. It is not a public multi-user launch path.

## Shape

```text
Internet
  -> Caddy reverse proxy on 80/443
  -> React frontend on the internal Docker network
  -> Spring Boot backend on the internal Docker network
  -> MySQL, Redis, RabbitMQ, and MinIO on the internal Docker network
```

The proxy routes browser pages to `linguaframe-frontend:5173` and routes `/api/*`, `/actuator/*`, `/swagger-ui/*`, `/v3/api-docs*`, and `/swagger-ui.html` to `linguaframe-backend:8080`.

## Setup

```bash
cp .env.private-demo.example .env.private-demo
```

Edit `.env.private-demo`:

- Set `LINGUAFRAME_PUBLIC_DOMAIN` to the demo domain that points at the server.
- Set `LINGUAFRAME_DEMO_ACCESS_TOKEN` to a long random value.
- Set `LINGUAFRAME_DEMO_OWNER_ID` to a stable non-secret owner label such as `demo-owner`; uploads, jobs, and owner-facing media/job APIs are scoped to this value.
- Optional: set `LINGUAFRAME_AUTH_ENABLED=true`, `LINGUAFRAME_AUTH_USERNAME`, `LINGUAFRAME_AUTH_PASSWORD`, and a `LINGUAFRAME_AUTH_JWT_SECRET` of at least 32 characters to test bearer-token access. Keep these values local and uncommitted.
- Keep `LINGUAFRAME_OWNER_QUOTA_ENABLED=true` for hosted demos and set conservative `LINGUAFRAME_OWNER_QUOTA_MAX_ACTIVE_JOBS`, `LINGUAFRAME_OWNER_QUOTA_MAX_QUEUED_JOBS`, and `LINGUAFRAME_OWNER_QUOTA_MAX_DAILY_COST_USD` values.
- Replace database, RabbitMQ, and MinIO placeholder passwords.
- Keep upload limits conservative, for example `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS=300`.
- Enable OpenAI providers only when you are ready to spend API credits.

Run deployment preflight:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh
```

Build the backend jar and start the proxy-fronted stack:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.private-demo \
  -f docker-compose.yml \
  -f deploy/private-demo/docker-compose.private-demo.yml \
  up -d --build
```

After startup:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-preflight.sh
```

## Expected URLs

- Frontend: `https://<LINGUAFRAME_PUBLIC_DOMAIN>`
- Swagger UI: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/swagger-ui/index.html`
- Runtime readiness: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/api/runtime/dependencies`
- Live checks: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/api/runtime/live-checks`
- Upload readiness: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/api/media/uploads/readiness`
- Operations readiness: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/api/operator/private-demo/operations`

When a token is configured, `/api/**` requires the demo token. Enter it in the browser `Demo access token` field before uploads, downloads, previews, or SSE.

When local account auth is configured, the browser `Local account` form can sign in and use `Authorization: Bearer ...` for protected APIs. Demo-token header and cookie compatibility remain available for scripts, Swagger, downloads, previews, and SSE.

Validate the bearer owner workspace from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/owner-workspace-smoke.sh
```

The script logs in with `LINGUAFRAME_AUTH_USERNAME` and `LINGUAFRAME_AUTH_PASSWORD`, then fetches job history, upload readiness, and runtime dependencies with bearer auth. It prints only owner workspace metadata and never prints bearer tokens, passwords, JWT secrets, demo tokens, object keys, local paths, provider payloads, transcript text, subtitle text, or media bytes. If local auth is disabled or unconfigured, it exits 0 and reports that bearer validation was skipped.

## Operations Readiness Report

Use the browser `Private demo operations` panel before uploading media or running provider-backed demos. It is a read-only aggregate of the access gate, runtime contract, live dependency checks, provider readiness, budget controls, storage/recovery guidance, retention cleanup preview, and demo evidence.

Generate the same metadata-only report from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-operations-report.sh
```

The script writes `/tmp/linguaframe-demo/private-demo-operations-report.md` unless `LINGUAFRAME_PRIVATE_DEMO_OPERATIONS_REPORT_PATH` is set. `READY` and `ATTENTION` exit with status 0; `BLOCKED` exits non-zero so deployment scripts can stop before upload or provider spend.

## Owner Quota Preflight

Use owner quota preflight immediately before uploading a real demo video or enabling paid providers:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/owner-quota-preflight.sh
```

The script calls `GET /api/media/uploads/preflight`, writes `/tmp/linguaframe-demo/owner-quota-preflight.json` by default, and exits non-zero when the configured owner is blocked. Set `LINGUAFRAME_OWNER_QUOTA_REPORT_ONLY=true` to collect the same metadata without blocking a shell workflow.

This guard rejects new uploads before object storage writes, job rows, dispatch events, FFmpeg stages, or OpenAI calls. It is a private-demo abuse and cost guard tied to `LINGUAFRAME_DEMO_OWNER_ID`; it is not public billing, JWT auth, or multi-tenant account management.

## Upload Readiness

Use upload readiness as the final pre-upload go/no-go check for the selected demo profile:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo \
  LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase \
  scripts/demo/upload-readiness.sh
```

The script calls `GET /api/media/uploads/readiness`, writes `/tmp/linguaframe-demo/upload-readiness.json` by default, and prints metadata-only readiness rows. `READY` and `ATTENTION` exit with status 0; `BLOCKED` exits non-zero unless `LINGUAFRAME_UPLOAD_READINESS_REPORT_ONLY=true`.

This check combines access-gated API reachability, runtime contract, live dependencies, owner quota, selected demo profile, and paid-provider warnings. It does not upload media, create jobs, call OpenAI, copy local files, or expose demo tokens, object keys, local paths, provider payloads, transcript text, subtitle text, or media bytes.

## Launch Rehearsal

Use the browser `Private demo launch rehearsal` panel as the ordered go/no-go checklist for a real presentation. It sits above operations readiness: operations explains current health, while launch rehearsal explains the next manual step and expected evidence.

Generate the same launch packet from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-launch-rehearsal.sh
```

The script writes `launch-rehearsal.json` and `launch-rehearsal.md` under `/tmp/linguaframe-demo/private-demo-launch-rehearsal/` by default. It is read-only: it does not start Docker, upload media, call OpenAI, create backups, restore data, or run cleanup. `READY` and `ATTENTION` exit with status 0; `BLOCKED` exits non-zero.

## Presentation Cockpit

Use the browser `Demo presentation cockpit` panel as the first run-day command center. It calls `GET /api/operator/demo-presentation-cockpit` and combines launcher readiness, upload readiness, live checks, private-demo operations, active run status, recommended completed run, acceptance gate, package links, and safety notes into one next-action view.

Generate the same cockpit from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/demo-presentation-cockpit.sh
LINGUAFRAME_ENV_FILE=.env.private-demo LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-presentation-cockpit.sh
```

The script writes `demo-presentation-cockpit.json` under `/tmp/linguaframe-demo/demo-presentation-cockpit/` by default. It is read-only and metadata-only: it does not upload media, start Docker, call OpenAI, retry or cancel jobs, create artifacts, run cleanup, copy media bytes, or print secrets, local paths, provider payloads, transcript text, or subtitle text.

## OpenAI Readiness Evidence

Use the browser `OpenAI readiness evidence` panel or terminal script before a provider-backed smoke/full demo:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-readiness-evidence.sh
```

The script writes `openai-readiness-evidence.json` and `openai-readiness-evidence.md` under `/tmp/linguaframe-demo/openai-readiness-evidence/`. It aggregates provider mode, OpenAI live-check status, upload readiness, budget posture, and recent model-call failure evidence without uploading media, creating jobs, running provider stages, printing API keys, exposing tokens, or including provider payloads. Use it as a shareable readiness report; use `openai-demo-preflight.sh` for strict env/live-check validation and `docker-e2e-openai-smoke.sh` only when you are ready for a paid proof run.

## Demo Session Evidence Package

Use the browser `Demo session command center` panel or the terminal script to download one ZIP for the whole private demo session:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/demo-session-evidence-package.sh
LINGUAFRAME_ENV_FILE=.env.private-demo LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-session-evidence-package.sh
```

The script writes `command-center.json` and `demo-session-evidence-package.zip` under `/tmp/linguaframe-demo/demo-session-evidence-package/`. The ZIP is metadata-only and read-only: it aggregates command center, operations, launch rehearsal, model usage, presentation cockpit, evidence gallery, and run archive evidence without media bytes, object keys, local paths, provider payloads, transcript text, subtitle text, or secrets. Use it as the session-level handoff bundle after a rehearsal or live demo.

## Evidence Gallery

Use the browser `Private demo evidence gallery` panel after demo jobs complete. It is the post-run selection workspace: operations readiness explains current health, launch rehearsal orders the go/no-go path, and evidence gallery picks completed outputs for presentation or handoff.

Generate the same gallery from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-evidence-gallery.sh
```

The script writes `evidence-gallery.json` and `evidence-gallery.md` under `/tmp/linguaframe-demo/private-demo-evidence-gallery/` by default. It is read-only and metadata-only: it does not upload media, call OpenAI, start Docker, publish subtitles, create backups, restore data, run cleanup, or copy media bytes.

## Safety Defaults

- Backend and frontend host ports are removed in the private overlay; traffic enters through Caddy.
- MySQL, Redis, RabbitMQ, and MinIO are internal Docker services, not public internet endpoints.
- Retention cleanup remains disabled and dry-run by default.
- Owner quota is enabled in `.env.private-demo.example` with conservative active, queued, and same-day cost limits.
- OpenAI connectivity checks are disabled by default.
- Deployment preflight validates shape and required env values without printing secrets.

## Backup And Restore

Create backups outside the repository by default:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-backup.sh \
  --output-dir /tmp/linguaframe-private-demo-backups
```

The backup includes:

- MySQL job history and audit tables as `mysql.sql`.
- MinIO artifact bucket contents under `minio/`.
- Caddy certificate and config state as `caddy-data.tar` and `caddy-config.tar`.
- A safe `manifest.json` with component names and Compose project metadata.

Redis and RabbitMQ are treated as volatile runtime state. Include them only when you intentionally want a point-in-time service-volume snapshot:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-backup.sh \
  --output-dir /tmp/linguaframe-private-demo-backups \
  --include-volatile
```

Validate a restore without writing data:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-restore.sh \
  --dry-run \
  --backup-dir /tmp/linguaframe-private-demo-backups/<timestamp>.linguaframe-backup
```

Run a guarded restore only after the dry-run passes:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-restore.sh \
  --backup-dir /tmp/linguaframe-private-demo-backups/<timestamp>.linguaframe-backup \
  --yes
```

Restore stops the proxy, frontend, and backend before importing MySQL, MinIO, and Caddy state. After restore, run:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-preflight.sh
scripts/demo/docker-e2e-cache-hit.sh
```

Backups do not include OpenAI secrets, demo token values, local source videos outside object storage, browser local storage, DNS records, or external provider state. Store backup directories securely because they contain job metadata and generated artifacts.

## Non-Goals

No public registration, JWT users, per-user storage isolation, billing, autoscaling, managed operations beyond Caddy default behavior, or production SRE automation.
