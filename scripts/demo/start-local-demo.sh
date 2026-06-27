#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.example}"

env_value() {
  local key="$1"
  local fallback="${2:-}"

  if [[ ! -f "$ENV_FILE" ]]; then
    printf '%s' "$fallback"
    return 0
  fi

  local line
  line="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    printf '%s' "$fallback"
    return 0
  fi

  local value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf '%s' "$value"
}

BACKEND_PORT="$(env_value LINGUAFRAME_BACKEND_PORT 8080)"
FRONTEND_PORT="$(env_value LINGUAFRAME_FRONTEND_PORT 5173)"
BASE_URL="${LINGUAFRAME_BASE_URL:-${LINGUAFRAME_DEMO_BASE_URL:-http://localhost:${BACKEND_PORT}}}"
FRONTEND_URL="${LINGUAFRAME_FRONTEND_URL:-http://localhost:${FRONTEND_PORT}}"
STARTED_FRONTEND_PID=""

usage() {
  cat <<'EOF'
Usage: scripts/demo/start-local-demo.sh

Packages the backend, recreates the Docker backend container, ensures the
frontend is reachable, and runs private-demo preflight.

Environment overrides:
  LINGUAFRAME_ENV_FILE       Default: .env.example
  LINGUAFRAME_BASE_URL       Default: http://localhost:8080
  LINGUAFRAME_FRONTEND_URL   Default: http://localhost:5173
  LINGUAFRAME_FRONTEND_PORT  Default: 5173
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

fail() {
  echo "[fail] $1" >&2
  exit 1
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    fail "Missing required command: $name"
  fi
}

cleanup() {
  if [[ -n "$STARTED_FRONTEND_PID" ]]; then
    if kill -0 "$STARTED_FRONTEND_PID" >/dev/null 2>&1; then
      echo "Stopping local frontend process $STARTED_FRONTEND_PID"
      kill "$STARTED_FRONTEND_PID" >/dev/null 2>&1 || true
      wait "$STARTED_FRONTEND_PID" 2>/dev/null || true
    fi
  fi
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  local delay_seconds="${4:-2}"

  for ((i = 1; i <= attempts; i++)); do
    if curl -fsSI "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  fail "$label did not become reachable at $url"
}

main() {
  trap cleanup EXIT INT TERM

  require_command mvn
  require_command docker
  require_command curl
  require_command python3
  require_command node
  require_command npm

  if [[ ! -f "$ENV_FILE" ]]; then
    fail "Missing env file: $ENV_FILE"
  fi

  echo "LinguaFrame local demo startup"
  echo "Env file: $ENV_FILE"
  echo "Backend: $BASE_URL"
  echo "Frontend: $FRONTEND_URL"

  echo "Packaging backend jar..."
  (cd "$REPO_ROOT" && mvn -pl LinguaFrame -am package -DskipTests)

  echo "Recreating backend container..."
  (cd "$REPO_ROOT" && docker compose --env-file "$ENV_FILE" up -d --build linguaframe-backend)
  wait_for_url "$BASE_URL/actuator/health" "Backend health"

  if curl -fsSI "$FRONTEND_URL" >/dev/null 2>&1; then
    echo "Frontend already responds at $FRONTEND_URL"
  else
    echo "Starting local frontend fallback..."
    (
      cd "$REPO_ROOT"
      LINGUAFRAME_FRONTEND_PORT="$FRONTEND_PORT" \
      LINGUAFRAME_FRONTEND_URL="$FRONTEND_URL" \
        scripts/demo/frontend-local-dev.sh
    ) &
    STARTED_FRONTEND_PID="$!"
    wait_for_url "$FRONTEND_URL" "Frontend"
  fi

  echo "Running private demo preflight..."
  (cd "$REPO_ROOT" && LINGUAFRAME_ENV_FILE="$ENV_FILE" scripts/demo/private-demo-preflight.sh)

  cat <<EOF

Local demo is ready.

Open:
  $FRONTEND_URL

Next commands:
  scripts/demo/docker-e2e-success.sh
  scripts/demo/docker-e2e-cache-hit.sh
EOF

  if [[ -n "$STARTED_FRONTEND_PID" ]]; then
    echo
    echo "The local frontend was started for this startup session and will stop when this script exits."
  fi
}

main "$@"
