#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.openai-demo}"

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
Usage: scripts/demo/openai-demo-preflight.sh

Checks that a local OpenAI demo profile is configured and reachable before
running a paid provider-backed media demo.

Environment overrides:
  LINGUAFRAME_ENV_FILE        Default: .env.openai-demo
  LINGUAFRAME_BASE_URL        Default: http://localhost:8080
  LINGUAFRAME_FRONTEND_URL    Default: http://localhost:5173
  LINGUAFRAME_DEMO_SAMPLE_PATH Optional short speech MP4 path to print
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

pass() {
  echo "[ok] $1"
}

fail() {
  echo "[fail] $1" >&2
  exit 1
}

require_env_file() {
  if [[ ! -f "$ENV_FILE" ]]; then
    fail "Missing $ENV_FILE. Create it with: cp .env.openai-demo.example $ENV_FILE"
  fi
  pass "Found env file: $ENV_FILE"
}

require_value() {
  local key="$1"
  local value
  value="$(env_value "$key")"

  if [[ -z "$value" || "$value" == \<* || "$value" == *placeholder* ]]; then
    fail "$key is not configured in $ENV_FILE"
  fi
  pass "$key is configured"
}

check_static_openai_profile() {
  require_value OPENAI_API_KEY
  require_value OPENAI_BASE_URL
  require_value OPENAI_TRANSCRIPTION_MODEL
  require_value OPENAI_TRANSLATION_MODEL
  require_value OPENAI_EVALUATION_MODEL
  require_value LINGUAFRAME_OPENAI_CONNECTIVITY_MODEL

  [[ "$(env_value LINGUAFRAME_TRANSCRIPTION_ENABLED)" == "true" ]] || fail "LINGUAFRAME_TRANSCRIPTION_ENABLED must be true"
  [[ "$(env_value LINGUAFRAME_TRANSCRIPTION_PROVIDER)" == "openai" ]] || fail "LINGUAFRAME_TRANSCRIPTION_PROVIDER must be openai"
  [[ "$(env_value LINGUAFRAME_TRANSLATION_ENABLED)" == "true" ]] || fail "LINGUAFRAME_TRANSLATION_ENABLED must be true"
  [[ "$(env_value LINGUAFRAME_TRANSLATION_PROVIDER)" == "openai" ]] || fail "LINGUAFRAME_TRANSLATION_PROVIDER must be openai"
  [[ "$(env_value LINGUAFRAME_EVALUATION_ENABLED)" == "true" ]] || fail "LINGUAFRAME_EVALUATION_ENABLED must be true"
  [[ "$(env_value LINGUAFRAME_EVALUATION_PROVIDER)" == "openai" ]] || fail "LINGUAFRAME_EVALUATION_PROVIDER must be openai"
  [[ "$(env_value LINGUAFRAME_OPENAI_CONNECTIVITY_CHECK_ENABLED)" == "true" ]] || fail "LINGUAFRAME_OPENAI_CONNECTIVITY_CHECK_ENABLED must be true"

  pass "OpenAI demo provider profile is configured"
}

demo_curl() {
  if [[ -n "$DEMO_TOKEN" ]]; then
    curl -fsS -H "$DEMO_HEADER_NAME: $DEMO_TOKEN" "$@"
    return 0
  fi

  curl -fsS "$@"
}

check_runtime_readiness() {
  local response_file
  response_file="$(mktemp)"
  if ! demo_curl "$BASE_URL/api/runtime/dependencies" >"$response_file"; then
    rm -f "$response_file"
    fail "Runtime dependency summary failed at $BASE_URL/api/runtime/dependencies"
  fi

  python3 - "$response_file" <<'PY'
import json
import sys

body = json.load(open(sys.argv[1], encoding="utf-8"))
providers = ((body.get("readiness") or {}).get("providers") or {})
required = ["transcription", "translation", "evaluation"]
for name in required:
    provider = providers.get(name)
    if not isinstance(provider, dict):
        raise SystemExit(f"Missing provider readiness for {name}")
    if provider.get("provider") != "openai":
        raise SystemExit(f"{name} provider is not openai")
    if provider.get("enabled") is not True:
        raise SystemExit(f"{name} provider is not enabled")
    if provider.get("credentialsConfigured") is not True:
        raise SystemExit(f"{name} OpenAI credentials are not configured")
    print(f"{name}=openai model={provider.get('model')}")
tts = providers.get("tts") or {}
print(f"tts={tts.get('provider')} enabled={tts.get('enabled')}")
PY
  rm -f "$response_file"
  pass "Runtime readiness reports OpenAI provider configuration"
}

check_openai_live_check() {
  local response_file
  response_file="$(mktemp)"
  if ! demo_curl "$BASE_URL/api/runtime/live-checks" >"$response_file"; then
    rm -f "$response_file"
    fail "Runtime live checks failed at $BASE_URL/api/runtime/live-checks"
  fi

  python3 - "$response_file" <<'PY'
import json
import sys

body = json.load(open(sys.argv[1], encoding="utf-8"))
openai = ((body.get("checks") or {}).get("openai") or {})
status = openai.get("status")
message = openai.get("message")
latency = openai.get("latencyMs")
print(f"openai={status} latencyMs={latency} message={message}")
if status != "UP":
    raise SystemExit("OpenAI live check is not UP")
PY
  rm -f "$response_file"
  pass "OpenAI live check is UP"
}

main() {
  echo "LinguaFrame OpenAI demo preflight"
  echo "Backend: $BASE_URL"
  echo "Frontend: $FRONTEND_URL"
  echo "Env file: $ENV_FILE"
  if [[ -n "${LINGUAFRAME_DEMO_SAMPLE_PATH:-}" ]]; then
    echo "Sample: $LINGUAFRAME_DEMO_SAMPLE_PATH"
  fi

  require_env_file
  check_static_openai_profile

  (cd "$REPO_ROOT" && LINGUAFRAME_ENV_FILE="$ENV_FILE" scripts/demo/private-demo-preflight.sh)

  check_runtime_readiness
  check_openai_live_check

  pass "OpenAI demo preflight passed"
}

main "$@"
