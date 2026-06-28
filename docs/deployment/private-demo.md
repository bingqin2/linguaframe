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
- Operations readiness: `https://<LINGUAFRAME_PUBLIC_DOMAIN>/api/operator/private-demo/operations`

When a token is configured, `/api/**` requires the demo token. Enter it in the browser `Demo access token` field before uploads, downloads, previews, or SSE.

## Operations Readiness Report

Use the browser `Private demo operations` panel before uploading media or running provider-backed demos. It is a read-only aggregate of the access gate, runtime contract, live dependency checks, provider readiness, budget controls, storage/recovery guidance, retention cleanup preview, and demo evidence.

Generate the same metadata-only report from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-operations-report.sh
```

The script writes `/tmp/linguaframe-demo/private-demo-operations-report.md` unless `LINGUAFRAME_PRIVATE_DEMO_OPERATIONS_REPORT_PATH` is set. `READY` and `ATTENTION` exit with status 0; `BLOCKED` exits non-zero so deployment scripts can stop before upload or provider spend.

## Launch Rehearsal

Use the browser `Private demo launch rehearsal` panel as the ordered go/no-go checklist for a real presentation. It sits above operations readiness: operations explains current health, while launch rehearsal explains the next manual step and expected evidence.

Generate the same launch packet from the terminal:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-launch-rehearsal.sh
```

The script writes `launch-rehearsal.json` and `launch-rehearsal.md` under `/tmp/linguaframe-demo/private-demo-launch-rehearsal/` by default. It is read-only: it does not start Docker, upload media, call OpenAI, create backups, restore data, or run cleanup. `READY` and `ATTENTION` exit with status 0; `BLOCKED` exits non-zero.

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
