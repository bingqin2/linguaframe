#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="$(demo_base_url)"
OUTPUT_DIR="${LINGUAFRAME_LOCAL_AUTH_OUTPUT_DIR:-/tmp/linguaframe-demo/local-auth}"
SESSION_JSON="${LINGUAFRAME_LOCAL_AUTH_SESSION_JSON_PATH:-$OUTPUT_DIR/session.json}"
LOGIN_JSON="${LINGUAFRAME_LOCAL_AUTH_LOGIN_JSON_PATH:-$OUTPUT_DIR/login.json}"
RUNTIME_JSON="${LINGUAFRAME_LOCAL_AUTH_RUNTIME_JSON_PATH:-$OUTPUT_DIR/runtime-dependencies.json}"
USERNAME="${LINGUAFRAME_AUTH_USERNAME:-$(env_value LINGUAFRAME_AUTH_USERNAME owner)}"
PASSWORD="${LINGUAFRAME_AUTH_PASSWORD:-$(env_value LINGUAFRAME_AUTH_PASSWORD)}"

mkdir -p "$OUTPUT_DIR"

download_local_auth_session_json "$BASE_URL" "$SESSION_JSON"
print_local_auth_summary_file "$SESSION_JSON"

configured="$(python3 - "$SESSION_JSON" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    body = json.load(handle)
print(str(bool(body.get("configured"))).lower())
PY
)"

if [[ "$configured" != "true" ]]; then
  echo "Local auth is disabled or unconfigured; bearer smoke skipped."
  exit 0
fi

if [[ -z "$PASSWORD" ]]; then
  echo "Local auth is configured but LINGUAFRAME_AUTH_PASSWORD is not available." >&2
  exit 2
fi

login_local_auth_json "$BASE_URL" "$USERNAME" "$PASSWORD" "$LOGIN_JSON"
print_local_auth_summary_file "$LOGIN_JSON"

token="$(python3 - "$LOGIN_JSON" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    body = json.load(handle)
print(body.get("token", ""))
PY
)"

if [[ -z "$token" ]]; then
  echo "Local auth login did not return a bearer token." >&2
  exit 1
fi

auth_curl "$token" -fsS "$BASE_URL/api/runtime/dependencies" -o "$RUNTIME_JSON"
echo "localAuthRuntimeDependencies=$RUNTIME_JSON"
