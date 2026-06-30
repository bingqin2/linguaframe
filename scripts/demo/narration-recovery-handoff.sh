#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-recovery-handoff.sh

Exports the metadata-only narration recovery handoff package for an existing job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                              Required job id, unless passed as the first argument.
  LINGUAFRAME_DEMO_BASE_URL                            Default: http://localhost:8080
  LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_OUTPUT_DIR    Default: /tmp/linguaframe-demo/narration-recovery-handoff
  LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY   Default: false. true allows BLOCKED status with exit 0.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-recovery-handoff}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/narration-recovery-handoff.json"
MARKDOWN_PATH="$OUTPUT_DIR/narration-recovery-handoff.md"
ZIP_PATH="$OUTPUT_DIR/narration-recovery-handoff.zip"

download_narration_recovery_handoff_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_narration_recovery_handoff_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
download_narration_recovery_handoff_zip "$BASE_URL" "$JOB_ID" "$ZIP_PATH"

print_narration_recovery_handoff_summary_file "$JSON_PATH" "$MARKDOWN_PATH" "$ZIP_PATH"

status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys

handoff = json.load(open(sys.argv[1], encoding="utf-8"))
print(handoff.get("status"))
PY
)"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Narration recovery handoff is BLOCKED for job $JOB_ID. Set LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
