# Docker E2E Demo

This guide verifies the current LinguaFrame backend demo path: upload a small sample file, create a job, dispatch through RabbitMQ, execute the smoke worker stage, and inspect the job timeline.

## Start The Stack

Package the backend jar from the repository root:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
```

Start Docker Compose:

```bash
docker compose --env-file .env.example up --build
```

Wait until the backend health endpoint is up:

```bash
curl http://localhost:8080/actuator/health
```

## Successful Job Demo

In another terminal, run:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected output includes:

```text
status=COMPLETED
- WORKER_RECEIVED STARTED
- WORKER_SMOKE STARTED
- WORKER_SMOKE SUCCEEDED
- COMPLETED SUCCEEDED
```

## Failure And Retry Demo

Start the stack with forced smoke-stage failure:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true docker compose --env-file .env.example up --build
```

In another terminal, run:

```bash
scripts/demo/docker-e2e-retry.sh
```

When prompted, stop the backend, restart it with failure disabled, then press Enter in the script terminal:

```bash
docker compose --env-file .env.example up --build linguaframe-backend
```

Expected output first includes `status=FAILED`, then `status=COMPLETED`.

## Cleanup

```bash
docker compose --env-file .env.example down
```

Add `-v` only when you intentionally want to delete local MySQL, RabbitMQ, Redis, and MinIO volumes.
