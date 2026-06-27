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

When a token is configured, `/api/**` requires the demo token. Enter it in the browser `Demo access token` field before uploads, downloads, previews, or SSE.

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
