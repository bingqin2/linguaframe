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

python3 - "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
command_center = json.loads(json_path.read_text(encoding="utf-8"))
focus_run = command_center.get("focusRun") or command_center.get("activeRun") or command_center.get("recommendedCompletedRun") or {}

print(f"demoSessionCommandCenterStatus={command_center.get('overallStatus')}")
print(f"demoSessionCommandCenterPhase={command_center.get('phase')}")
print(f"demoSessionCommandCenterNextAction={command_center.get('recommendedNextAction')}")
print(f"demoSessionCommandCenterFocusJobId={focus_run.get('jobId')}")
print(f"demoSessionCommandCenterModelCallCount={command_center.get('modelCallCount')}")
print(f"demoSessionCommandCenterFailedModelCallCount={command_center.get('failedModelCallCount')}")
print(f"demoSessionCommandCenterEstimatedCostUsd={command_center.get('estimatedCostUsd')}")
print(f"demoSessionCommandCenterPrimaryCommand={command_center.get('primaryCommand')}")
print(f"demoSessionCommandCenterJsonPath={json_path}")
print(f"demoSessionCommandCenterMarkdownPath={markdown_path}")
PY

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
