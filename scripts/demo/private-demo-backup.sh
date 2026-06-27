#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.private-demo}"
LOCAL_COMPOSE_FILE="docker-compose.yml"
PRIVATE_COMPOSE_FILE="deploy/private-demo/docker-compose.private-demo.yml"
DEFAULT_OUTPUT_DIR="/tmp/linguaframe-private-demo-backups"
BACKUP_VERSION="1"

OUTPUT_DIR="$DEFAULT_OUTPUT_DIR"
DRY_RUN=false
INCLUDE_VOLATILE=false

usage() {
  cat <<'TXT'
Usage: scripts/demo/private-demo-backup.sh [--output-dir PATH] [--dry-run] [--include-volatile]

Creates a private-demo backup containing MySQL, MinIO objects, and Caddy state.
Redis and RabbitMQ volume snapshots are included only with --include-volatile.
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
    --output-dir)
      [[ $# -ge 2 ]] || fail "--output-dir requires a path"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=true
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
  [[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE. Create it with: cp .env.private-demo.example $ENV_FILE"
  [[ -f "$LOCAL_COMPOSE_FILE" ]] || fail "Missing $LOCAL_COMPOSE_FILE"
  [[ -f "$PRIVATE_COMPOSE_FILE" ]] || fail "Missing $PRIVATE_COMPOSE_FILE"
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

write_manifest() {
  local backup_dir="$1"
  local project_name="$2"
  python3 - "$backup_dir" "$project_name" "$BACKUP_VERSION" "$INCLUDE_VOLATILE" <<'PY'
import datetime
import json
import pathlib
import sys

backup_dir = pathlib.Path(sys.argv[1])
project_name = sys.argv[2]
version = sys.argv[3]
include_volatile = sys.argv[4] == "true"
components = ["mysql", "minio", "caddy-data", "caddy-config"]
if include_volatile:
    components.extend(["redis-data", "rabbitmq-data"])
manifest = {
    "backupVersion": version,
    "createdAt": datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "composeProject": project_name,
    "components": components,
    "services": ["mysql", "minio", "linguaframe-proxy", "linguaframe-backend", "linguaframe-frontend"],
}
(backup_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PY
}

volume_tar() {
  local volume_name="$1"
  local output_file="$2"
  docker run --rm \
    -v "${volume_name}:/volume:ro" \
    -v "$(dirname "$output_file"):/backup" \
    alpine:3.20 \
    tar -cf "/backup/$(basename "$output_file")" -C /volume .
}

backup_mysql() {
  local output_file="$1"
  docker compose --env-file "$ENV_FILE" -f "$LOCAL_COMPOSE_FILE" -f "$PRIVATE_COMPOSE_FILE" \
    exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --quick -u"$MYSQL_USER" "$MYSQL_DATABASE"' \
    > "$output_file"
}

backup_minio() {
  local backup_dir="$1"
  local project_name="$2"
  mkdir -p "$backup_dir/minio"
  docker run --rm \
    --network "${project_name}_default" \
    --env-file "$ENV_FILE" \
    -v "$backup_dir/minio:/backup" \
    minio/mc:RELEASE.2025-08-13T08-35-41Z \
    sh -c 'mc alias set local http://minio:9000 "${MINIO_ROOT_USER:-linguaframe}" "${MINIO_ROOT_PASSWORD:-linguaframe_minio_password}" >/dev/null && mc mirror --quiet --overwrite "local/${MINIO_BUCKET:-linguaframe-artifacts}" /backup'
}

timestamp() {
  date -u +"%Y%m%dT%H%M%SZ"
}

echo "LinguaFrame private demo backup"
echo "Env file: $ENV_FILE"

require_command docker
require_command python3
require_command tar
require_command date
validate_files

CONFIG_FILE="$(mktemp)"
trap 'rm -f "$CONFIG_FILE"' EXIT
render_and_validate_compose "$CONFIG_FILE"
PROJECT_NAME="$(compose_project_name "$CONFIG_FILE")"
TARGET_DIR="$OUTPUT_DIR/$(timestamp).linguaframe-backup"

echo "composeProject=$PROJECT_NAME"
echo "targetDir=$TARGET_DIR"
echo "components=mysql,minio,caddy-data,caddy-config"
if [[ "$INCLUDE_VOLATILE" == "true" ]]; then
  echo "volatileComponents=redis-data,rabbitmq-data"
else
  echo "volatileComponents=skipped"
fi

if [[ "$DRY_RUN" == "true" ]]; then
  pass "Dry run completed without reading or writing service data"
  exit 0
fi

mkdir -p "$TARGET_DIR"
write_manifest "$TARGET_DIR" "$PROJECT_NAME"
backup_mysql "$TARGET_DIR/mysql.sql"
backup_minio "$TARGET_DIR" "$PROJECT_NAME"
volume_tar "${PROJECT_NAME}_caddy-data" "$TARGET_DIR/caddy-data.tar"
volume_tar "${PROJECT_NAME}_caddy-config" "$TARGET_DIR/caddy-config.tar"

if [[ "$INCLUDE_VOLATILE" == "true" ]]; then
  volume_tar "${PROJECT_NAME}_redis-data" "$TARGET_DIR/redis-data.tar"
  volume_tar "${PROJECT_NAME}_rabbitmq-data" "$TARGET_DIR/rabbitmq-data.tar"
fi

pass "Private demo backup created at $TARGET_DIR"
echo "Next restore dry-run:"
echo "  LINGUAFRAME_ENV_FILE=$ENV_FILE scripts/demo/private-demo-restore.sh --dry-run --backup-dir $TARGET_DIR"
