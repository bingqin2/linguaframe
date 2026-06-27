# Private Demo Persistence Backup Restore MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an operator-visible private demo backup and restore workflow so job history, artifacts, and proxy state can survive server rebuilds or migration.

**Architecture:** Keep the runtime stack unchanged. Add shell tooling under `scripts/demo/` that exports MySQL, MinIO objects, and Caddy state from the private-demo Compose project into a timestamped local backup directory, plus a restore preflight/restore path that validates archive shape before writing back into Docker volumes and services. Document the workflow as an operator runbook and add smoke validation that uses synthetic metadata by default, not real media uploads or provider calls.

**Tech Stack:** Docker Compose, Bash, MySQL CLI inside the MySQL container, MinIO `mc` client container, tar, Markdown.

## Global Constraints

- Do not change the default local `docker compose --env-file .env.example up` behavior.
- Do not add public user accounts, JWT migration, billing, or multi-tenant storage isolation.
- Do not print database passwords, MinIO secrets, demo tokens, OpenAI keys, object keys from user uploads, or raw transcript/subtitle contents.
- Default commands must use `.env.private-demo` through `LINGUAFRAME_ENV_FILE`, while validation can use `.env.private-demo.example`.
- Backup output must stay outside git by default and be ignored if stored under the repository.
- Restore must require explicit confirmation unless running in dry-run mode.
- The feature must include scripts, docs, validation, roadmap/progress updates, this plan file, and merge tracking.

---

## Task 1: Backup Archive Tooling

**Files:**
- Create: `scripts/demo/private-demo-backup.sh`
- Modify: `.gitignore`
- Modify: `.dockerignore`

**Steps:**

- [x] Add `scripts/demo/private-demo-backup.sh` with `set -euo pipefail`, repo-root detection, and `LINGUAFRAME_ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.private-demo}"`.
- [x] Add options: `--output-dir PATH`, `--dry-run`, and `--include-volatile` where volatile means Redis and RabbitMQ volume tarballs.
- [x] Validate required commands: `docker`, `python3`, `tar`, and `date`.
- [x] Fail clearly if the env file is missing or Compose cannot render with `docker-compose.yml` plus `deploy/private-demo/docker-compose.private-demo.yml`.
- [x] Resolve Compose project name from rendered Compose JSON, not by echoing env values.
- [x] Create a timestamped backup directory such as `/tmp/linguaframe-private-demo-backups/20260628T120000Z`.
- [x] Write `manifest.json` with backup version, creation time, Compose project name, included components, and safe service names only.
- [x] Export MySQL through the running `mysql` container using `mysqldump` and write `mysql.sql`.
- [x] Export MinIO objects through a temporary `minio/mc` container on the Compose network and write `minio/`.
- [x] Export Caddy data/config volumes using a temporary tar container and write `caddy-data.tar` and `caddy-config.tar`.
- [x] When `--include-volatile` is set, export Redis and RabbitMQ volumes as `redis-data.tar` and `rabbitmq-data.tar`.
- [x] In `--dry-run`, print planned safe components and target paths without reading or writing service data.
- [x] Add `private-demo-backups/` and `*.linguaframe-backup/` ignore rules while keeping scripts and docs tracked.

## Task 2: Restore Preflight And Restore Tooling

**Files:**
- Create: `scripts/demo/private-demo-restore.sh`
- Modify if useful: `scripts/demo/private-demo-backup.sh`

**Steps:**

- [x] Add `scripts/demo/private-demo-restore.sh` with `set -euo pipefail`, repo-root detection, and `LINGUAFRAME_ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.private-demo}"`.
- [x] Add options: `--backup-dir PATH`, `--dry-run`, `--yes`, and `--include-volatile`.
- [x] Validate backup directory contains `manifest.json`, `mysql.sql`, `minio/`, `caddy-data.tar`, and `caddy-config.tar`.
- [x] Validate `manifest.json` has the expected backup version and safe component names.
- [x] Render private-demo Compose config and verify required services exist before restore.
- [x] In dry-run mode, print the restore plan without writing to containers or volumes.
- [x] Require `--yes` for non-dry-run restore and refuse to continue without it.
- [x] Stop backend/frontend/proxy before restore and leave dependency services available for import where needed.
- [x] Restore MySQL by piping `mysql.sql` into the running MySQL container without printing credentials.
- [x] Restore MinIO objects through a temporary `minio/mc` container.
- [x] Restore Caddy tarballs into the Compose volumes through a temporary tar container.
- [x] Restore Redis/RabbitMQ tarballs only when both the backup contains them and `--include-volatile` is set.
- [x] Print post-restore verification commands: `scripts/demo/private-demo-preflight.sh` and `scripts/demo/docker-e2e-cache-hit.sh`.

## Task 3: Script Tests And Static Validation

**Files:**
- Modify: `scripts/demo/private-demo-backup.sh`
- Modify: `scripts/demo/private-demo-restore.sh`
- Modify: `docs/agent/smoke-test-checklist.md`

**Steps:**

- [x] Run `bash -n scripts/demo/private-demo-backup.sh scripts/demo/private-demo-restore.sh`.
- [x] Run `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-backup.sh --dry-run --output-dir /tmp/linguaframe-private-demo-backups`.
- [x] Create a synthetic backup directory under `/tmp/linguaframe-private-demo-restore-smoke` with `manifest.json`, empty `mysql.sql`, empty `minio/`, and small placeholder Caddy tar files.
- [x] Run `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-restore.sh --dry-run --backup-dir /tmp/linguaframe-private-demo-restore-smoke`.
- [x] Run `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet`.
- [x] Run `git diff --check`.
- [x] Document that full non-dry-run backup/restore requires a running private-demo stack and is not executed during static validation.

## Task 4: Operator Documentation And Product Tracking

**Files:**
- Modify: `docs/deployment/private-demo.md`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/068-private-demo-persistence-backup-restore-mvp.md`

**Steps:**

- [x] Document backup commands, restore dry-run commands, and guarded restore commands.
- [x] Document what is included by default: MySQL job history, MinIO artifacts, Caddy certificate/config state.
- [x] Document what is optional: Redis/RabbitMQ volatile state.
- [x] Document what is not included: OpenAI secrets, demo token values, local source videos outside object storage, browser local storage, and external DNS records.
- [x] Update Phase 9 roadmap status for persistent object storage and restart/migration survivability.
- [x] Record the decision that private-demo durability is handled through operator-run backups before adding managed cloud storage or public multi-user account isolation.
- [x] Update execution log with validation commands and any skipped live restore reason.
- [x] Mark completed checklist items in this plan as tasks finish.

## Done Criteria

- [x] A private-demo operator can run a dry-run backup plan without starting a provider call or uploading media.
- [x] A running private-demo stack has a documented command path to export MySQL, MinIO, and Caddy state.
- [x] A restore script validates backup shape and requires explicit confirmation before writing data.
- [x] Backup artifacts are ignored by git when stored under the repository.
- [x] Smoke checklist and deployment docs explain backup, restore dry-run, guarded restore, and post-restore verification.
- [x] Roadmap, decisions, execution log, and this plan are updated.
- [ ] The feature branch is committed, verified, and merged back to `main`.
