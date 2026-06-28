#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="$(demo_base_url)"
OUTPUT_DIR="${LINGUAFRAME_OWNER_WORKSPACE_OUTPUT_DIR:-/tmp/linguaframe-demo/owner-workspace}"
SESSION_JSON="${LINGUAFRAME_OWNER_WORKSPACE_SESSION_JSON_PATH:-$OUTPUT_DIR/session.json}"
LOGIN_JSON="${LINGUAFRAME_OWNER_WORKSPACE_LOGIN_JSON_PATH:-$OUTPUT_DIR/login.json}"
JOBS_JSON="${LINGUAFRAME_OWNER_WORKSPACE_JOBS_JSON_PATH:-$OUTPUT_DIR/jobs.json}"
READINESS_JSON="${LINGUAFRAME_OWNER_WORKSPACE_READINESS_JSON_PATH:-$OUTPUT_DIR/upload-readiness.json}"
RUNTIME_JSON="${LINGUAFRAME_OWNER_WORKSPACE_RUNTIME_JSON_PATH:-$OUTPUT_DIR/runtime-dependencies.json}"
USERNAME="${LINGUAFRAME_AUTH_USERNAME:-$(env_value LINGUAFRAME_AUTH_USERNAME owner)}"
PASSWORD="${LINGUAFRAME_AUTH_PASSWORD:-$(env_value LINGUAFRAME_AUTH_PASSWORD)}"
DEMO_PROFILE_ID="${LINGUAFRAME_DEMO_PROFILE_ID:-quick-baseline}"

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
  python3 - "$SESSION_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    session = json.load(handle)

def text(value):
    return "" if value is None else str(value)

print("ownerWorkspaceAuthMode=" + text(session.get("authMode")))
print("ownerWorkspaceOwnerId=" + text(session.get("ownerId")))
print("ownerWorkspaceOwnershipScope=" + text(session.get("ownershipScope")))
print("ownerWorkspaceJobCount=0")
print("ownerWorkspaceUploadReadiness=SKIPPED")
PY
  echo "Local auth is disabled or unconfigured; bearer workspace smoke skipped."
  exit 0
fi

if [[ -z "$PASSWORD" ]]; then
  echo "Local auth is configured but LINGUAFRAME_AUTH_PASSWORD is not available." >&2
  exit 2
fi

login_local_auth_json "$BASE_URL" "$USERNAME" "$PASSWORD" "$LOGIN_JSON"

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

download_owner_workspace_jobs_json "$BASE_URL" "$token" "$JOBS_JSON"
download_owner_workspace_upload_readiness_json "$BASE_URL" "$token" "$DEMO_PROFILE_ID" "$READINESS_JSON"
download_owner_workspace_runtime_dependencies_json "$BASE_URL" "$token" "$RUNTIME_JSON"
print_owner_workspace_summary_files "$LOGIN_JSON" "$JOBS_JSON" "$READINESS_JSON"
echo "ownerWorkspaceRuntimeDependencies=$RUNTIME_JSON"
