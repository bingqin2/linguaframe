#!/usr/bin/env bash

set -euo pipefail

ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env}"

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
DEMO_HEADER_NAME="${LINGUAFRAME_DEMO_ACCESS_HEADER_NAME:-$(env_value LINGUAFRAME_DEMO_ACCESS_HEADER_NAME X-LinguaFrame-Demo-Token)}"
DEMO_TOKEN="${LINGUAFRAME_DEMO_ACCESS_TOKEN:-$(env_value LINGUAFRAME_DEMO_ACCESS_TOKEN)}"

usage() {
  cat <<'EOF'
Usage: scripts/demo/private-demo-preflight.sh

Checks whether the local LinguaFrame private demo is ready before uploading media.

Environment overrides:
  LINGUAFRAME_ENV_FILE                    Default: .env
  LINGUAFRAME_BASE_URL                    Default: http://localhost:8080
  LINGUAFRAME_FRONTEND_URL                Default: http://localhost:5173
  LINGUAFRAME_DEMO_ACCESS_TOKEN           Optional token for gated /api/** checks
  LINGUAFRAME_DEMO_ACCESS_HEADER_NAME     Default: X-LinguaFrame-Demo-Token
  LINGUAFRAME_DEMO_SAMPLE_PATH            Optional short demo MP4 path to verify
  LINGUAFRAME_TEARS_SAMPLE_PATH           Optional Tears of Steel MP4 path to verify
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

pass() {
  echo "[ok] $1"
}

warn() {
  echo "[warn] $1" >&2
}

fail() {
  echo "[fail] $1" >&2
  exit 1
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    fail "Missing required command: $name"
  fi
  pass "Found command: $name"
}

check_optional_command() {
  local name="$1"
  local message="$2"
  if command -v "$name" >/dev/null 2>&1; then
    pass "Found optional command: $name"
  else
    warn "$message"
  fi
}

check_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    fail "Missing $ENV_FILE. Create it with: cp .env.example $ENV_FILE"
  fi
  pass "Found env file: $ENV_FILE"
}

check_compose_config() {
  docker compose --env-file "$ENV_FILE" config --quiet
  pass "Docker Compose config renders"

  docker compose --env-file "$ENV_FILE" --profile split-workers config --quiet
  pass "Docker Compose split-workers config renders"
}

check_backend_health() {
  local response
  if ! response="$(curl -fsS "$BASE_URL/actuator/health")"; then
    fail "Backend health check failed at $BASE_URL/actuator/health. Start the stack with: docker compose --env-file $ENV_FILE up -d --build"
  fi
  if [[ "$response" != *'"status":"UP"'* ]]; then
    fail "Backend health check did not return UP: $response"
  fi
  pass "Backend health is UP at $BASE_URL"
}

check_frontend() {
  if ! curl -fsSI "$FRONTEND_URL" >/dev/null; then
    fail "Frontend did not respond at $FRONTEND_URL. Start the frontend with: docker compose --env-file $ENV_FILE up -d --build linguaframe-frontend"
  fi
  pass "Frontend responds at $FRONTEND_URL"
}

http_status() {
  curl -sS -o /dev/null -w "%{http_code}" "$@"
}

check_demo_token_gate() {
  local endpoint="$BASE_URL/api/runtime/dependencies"
  local anonymous_status
  local token_status

  if [[ -z "$DEMO_TOKEN" ]]; then
    pass "Demo access token is not configured; local API is open"
    return 0
  fi

  anonymous_status="$(http_status "$endpoint")"
  if [[ "$anonymous_status" != "401" ]]; then
    fail "Expected protected API without token to return 401, got $anonymous_status from $endpoint"
  fi

  token_status="$(http_status -H "$DEMO_HEADER_NAME: $DEMO_TOKEN" "$endpoint")"
  if [[ "$token_status" == "401" || "$token_status" == "000" ]]; then
    fail "Expected protected API with $DEMO_HEADER_NAME to pass, got $token_status from $endpoint"
  fi

  pass "Demo access token gate works with header $DEMO_HEADER_NAME"
}

check_media_path() {
  local variable_name="$1"
  local value="${!variable_name:-}"

  if [[ -z "$value" ]]; then
    return 0
  fi
  if [[ ! -r "$value" || ! -s "$value" ]]; then
    fail "$variable_name points to an unreadable or empty file: $value"
  fi
  pass "$variable_name is readable: $value"
}

check_sample_paths() {
  check_media_path LINGUAFRAME_DEMO_SAMPLE_PATH
  check_media_path LINGUAFRAME_TEARS_SAMPLE_PATH

  if [[ -z "${LINGUAFRAME_DEMO_SAMPLE_PATH:-}" && -z "${LINGUAFRAME_TEARS_SAMPLE_PATH:-}" ]]; then
    warn "No sample path configured. The short demo can generate /tmp/linguaframe-demo/sample.mp4 when ffmpeg exists."
    warn "For the full demo, set LINGUAFRAME_TEARS_SAMPLE_PATH=/absolute/path/to/tos_casting-720p.mp4."
  fi
}

main() {
  echo "LinguaFrame private demo preflight"
  echo "Backend: $BASE_URL"
  echo "Frontend: $FRONTEND_URL"
  echo "Env file: $ENV_FILE"

  require_command docker
  require_command curl
  require_command mvn
  check_optional_command ffmpeg "ffmpeg not found. Existing MP4 demos can still run, but the short demo cannot generate a sample automatically."
  check_env_file
  check_compose_config
  check_backend_health
  check_frontend
  check_demo_token_gate
  check_sample_paths

  pass "Private demo preflight passed"
}

main "$@"
