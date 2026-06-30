#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-session-command-center}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
JSON_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_JSON_PATH:-$OUTPUT_DIR/demo-session-command-center.json}"
MARKDOWN_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_MARKDOWN_PATH:-$OUTPUT_DIR/demo-session-command-center.md}"
REPORT_ONLY="${LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_REPORT_ONLY:-false}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$MARKDOWN_OUTPUT_PATH")"

QUERY=""
if [[ -n "${JOB_ID//[[:space:]]/}" ]]; then
  QUERY="$(python3 - "$JOB_ID" <<'PY'
import sys
from urllib.parse import urlencode

print("?" + urlencode({"jobId": sys.argv[1].strip()}))
PY
)"
fi

demo_curl -fsS \
  "$BASE_URL/api/operator/demo-session-command-center$QUERY" \
  -o "$JSON_OUTPUT_PATH"

demo_curl -fsS \
  "$BASE_URL/api/operator/demo-session-command-center/markdown/download$QUERY" \
  -o "$MARKDOWN_OUTPUT_PATH"

print_demo_session_command_center_summary_file "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH"

STATUS="$(python3 - "$JSON_OUTPUT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("overallStatus"))
PY
)"

if [[ "$STATUS" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Demo session command center is BLOCKED. Set LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
