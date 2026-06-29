#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-session-evidence-package}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
JSON_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_JSON_PATH:-$OUTPUT_DIR/command-center.json}"
ZIP_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_ZIP_PATH:-$OUTPUT_DIR/demo-session-evidence-package.zip}"
REPORT_ONLY="${LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_REPORT_ONLY:-false}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$ZIP_OUTPUT_PATH")"

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
  "$BASE_URL/api/operator/demo-session-evidence-package/download$QUERY" \
  -o "$ZIP_OUTPUT_PATH"

python3 - "$JSON_OUTPUT_PATH" "$ZIP_OUTPUT_PATH" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

json_path = Path(sys.argv[1])
zip_path = Path(sys.argv[2])
command_center = json.loads(json_path.read_text(encoding="utf-8"))
focus_run = command_center.get("focusRun") or command_center.get("activeRun") or command_center.get("recommendedCompletedRun") or {}

with zipfile.ZipFile(zip_path) as package:
    entries = package.namelist()

print(f"demoSessionEvidencePackageStatus={command_center.get('overallStatus')}")
print(f"demoSessionEvidencePackagePhase={command_center.get('phase')}")
print(f"demoSessionEvidencePackageNextAction={command_center.get('recommendedNextAction')}")
print(f"demoSessionEvidencePackageFocusJobId={focus_run.get('jobId')}")
print(f"demoSessionEvidencePackageJsonPath={json_path}")
print(f"demoSessionEvidencePackageZipPath={zip_path}")
print(f"demoSessionEvidencePackageEntries={','.join(entries)}")
PY

STATUS="$(python3 - "$JSON_OUTPUT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("overallStatus"))
PY
)"

if [[ "$STATUS" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Demo session evidence package is BLOCKED. Set LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
