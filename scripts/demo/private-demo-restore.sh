#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.private-demo}"
LOCAL_COMPOSE_FILE="docker-compose.yml"
PRIVATE_COMPOSE_FILE="deploy/private-demo/docker-compose.private-demo.yml"
BACKUP_VERSION="1"

BACKUP_DIR=""
DRY_RUN=false
YES=false
INCLUDE_VOLATILE=false

usage() {
  cat <<'TXT'
Usage: scripts/demo/private-demo-restore.sh --backup-dir PATH [--dry-run] [--yes] [--include-volatile]

Validates and restores a private-demo backup. Non-dry-run restore requires --yes.
Redis and RabbitMQ volume snapshots are restored only with --include-volatile.
TXT
}

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup-dir)
      [[ $# -ge 2 ]] || fail "--backup-dir requires a path"
      BACKUP_DIR="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --yes)
      YES=true
      shift
      ;;
    --include-volatile)
      INCLUDE_VOLATILE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

compose_json() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$LOCAL_COMPOSE_FILE" \
    -f "$PRIVATE_COMPOSE_FILE" \
    config \
    --format json
}

validate_files() {
  [[ -n "$BACKUP_DIR" ]] || fail "--backup-dir is required"
  [[ -d "$BACKUP_DIR" ]] || fail "Backup directory does not exist: $BACKUP_DIR"
  [[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE. Create it with: cp .env.private-demo.example $ENV_FILE"
  [[ -f "$LOCAL_COMPOSE_FILE" ]] || fail "Missing $LOCAL_COMPOSE_FILE"
  [[ -f "$PRIVATE_COMPOSE_FILE" ]] || fail "Missing $PRIVATE_COMPOSE_FILE"
  [[ -f "$BACKUP_DIR/manifest.json" ]] || fail "Backup is missing manifest.json"
  [[ -f "$BACKUP_DIR/mysql.sql" ]] || fail "Backup is missing mysql.sql"
  [[ -d "$BACKUP_DIR/minio" ]] || fail "Backup is missing minio/"
  [[ -f "$BACKUP_DIR/caddy-data.tar" ]] || fail "Backup is missing caddy-data.tar"
  [[ -f "$BACKUP_DIR/caddy-config.tar" ]] || fail "Backup is missing caddy-config.tar"
}

validate_manifest() {
  python3 - "$BACKUP_DIR/manifest.json" "$BACKUP_VERSION" "$INCLUDE_VOLATILE" <<'PY'
import json
import pathlib
import sys

manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
expected_version = sys.argv[2]
include_volatile = sys.argv[3] == "true"
if str(manifest.get("backupVersion")) != expected_version:
    raise SystemExit("Unsupported backupVersion")
components = set(manifest.get("components") or [])
required = {"mysql", "minio", "caddy-data", "caddy-config"}
missing = sorted(required - components)
if missing:
    raise SystemExit("Manifest missing components: " + ", ".join(missing))
allowed = required | {"redis-data", "rabbitmq-data"}
unknown = sorted(components - allowed)
if unknown:
    raise SystemExit("Manifest has unknown components: " + ", ".join(unknown))
if include_volatile:
    volatile_missing = sorted({"redis-data", "rabbitmq-data"} - components)
    if volatile_missing:
        raise SystemExit("Volatile restore requested but manifest is missing: " + ", ".join(volatile_missing))
services = set(manifest.get("services") or [])
required_services = {"mysql", "minio", "linguaframe-proxy", "linguaframe-backend", "linguaframe-frontend"}
missing_services = sorted(required_services - services)
if missing_services:
    raise SystemExit("Manifest missing services: " + ", ".join(missing_services))
PY
}

render_and_validate_compose() {
  local config_file="$1"
  if ! compose_json > "$config_file"; then
    fail "Private-demo Compose rendering failed"
  fi

  python3 - "$config_file" <<'PY'
import json
import pathlib
import sys

body = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
services = body.get("services") or {}
required = {"mysql", "minio", "linguaframe-proxy", "linguaframe-backend", "linguaframe-frontend"}
missing = sorted(required - set(services))
if missing:
    raise SystemExit("Missing services: " + ", ".join(missing))
if not body.get("name"):
    raise SystemExit("Compose project name is missing")
PY
}

compose_project_name() {
  python3 - "$1" <<'PY'
import json
import pathlib
import sys

body = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
print(body["name"])
PY
}

restore_tar_volume() {
  local volume_name="$1"
  local tar_file="$2"
  docker run --rm \
    -v "${volume_name}:/volume" \
    -v "$(dirname "$tar_file"):/backup:ro" \
    alpine:3.20 \
    sh -c 'find /volume -mindepth 1 -maxdepth 1 -exec rm -rf {} + && tar -xf "/backup/'"$(basename "$tar_file")"'" -C /volume'
}

restore_mysql() {
  docker compose --env-file "$ENV_FILE" -f "$LOCAL_COMPOSE_FILE" -f "$PRIVATE_COMPOSE_FILE" \
    exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    < "$BACKUP_DIR/mysql.sql"
}

restore_minio() {
  local project_name="$1"
  docker run --rm \
    --network "${project_name}_default" \
    --env-file "$ENV_FILE" \
    -v "$BACKUP_DIR/minio:/backup:ro" \
    minio/mc:RELEASE.2025-08-13T08-35-41Z \
    sh -c 'mc alias set local http://minio:9000 "${MINIO_ROOT_USER:-linguaframe}" "${MINIO_ROOT_PASSWORD:-linguaframe_minio_password}" >/dev/null && mc mb --ignore-existing "local/${MINIO_BUCKET:-linguaframe-artifacts}" >/dev/null && mc mirror --quiet --overwrite /backup "local/${MINIO_BUCKET:-linguaframe-artifacts}"'
}

echo "LinguaFrame private demo restore"
echo "Env file: $ENV_FILE"

require_command docker
require_command python3
require_command tar
validate_files
validate_manifest

CONFIG_FILE="$(mktemp)"
trap 'rm -f "$CONFIG_FILE"' EXIT
render_and_validate_compose "$CONFIG_FILE"
PROJECT_NAME="$(compose_project_name "$CONFIG_FILE")"

echo "composeProject=$PROJECT_NAME"
echo "backupDir=$BACKUP_DIR"
echo "components=mysql,minio,caddy-data,caddy-config"
if [[ "$INCLUDE_VOLATILE" == "true" ]]; then
  [[ -f "$BACKUP_DIR/redis-data.tar" ]] || fail "Volatile restore requested but backup is missing redis-data.tar"
  [[ -f "$BACKUP_DIR/rabbitmq-data.tar" ]] || fail "Volatile restore requested but backup is missing rabbitmq-data.tar"
  echo "volatileComponents=redis-data,rabbitmq-data"
else
  echo "volatileComponents=skipped"
fi

if [[ "$DRY_RUN" == "true" ]]; then
  pass "Restore dry run validated backup shape without writing data"
  exit 0
fi

if [[ "$YES" != "true" ]]; then
  fail "Non-dry-run restore requires --yes"
fi

docker compose --env-file "$ENV_FILE" -f "$LOCAL_COMPOSE_FILE" -f "$PRIVATE_COMPOSE_FILE" \
  stop linguaframe-proxy linguaframe-frontend linguaframe-backend

restore_mysql
restore_minio "$PROJECT_NAME"
restore_tar_volume "${PROJECT_NAME}_caddy-data" "$BACKUP_DIR/caddy-data.tar"
restore_tar_volume "${PROJECT_NAME}_caddy-config" "$BACKUP_DIR/caddy-config.tar"

if [[ "$INCLUDE_VOLATILE" == "true" ]]; then
  docker compose --env-file "$ENV_FILE" -f "$LOCAL_COMPOSE_FILE" -f "$PRIVATE_COMPOSE_FILE" \
    stop redis rabbitmq
  restore_tar_volume "${PROJECT_NAME}_redis-data" "$BACKUP_DIR/redis-data.tar"
  restore_tar_volume "${PROJECT_NAME}_rabbitmq-data" "$BACKUP_DIR/rabbitmq-data.tar"
fi

pass "Private demo restore completed"
echo "Post-restore verification:"
echo "  LINGUAFRAME_ENV_FILE=$ENV_FILE scripts/demo/private-demo-preflight.sh"
echo "  scripts/demo/docker-e2e-cache-hit.sh"
