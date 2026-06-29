#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-reviewer-workspace.sh

Exports the metadata-only reviewer workspace for an existing job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                              Required job id.
  LINGUAFRAME_DEMO_BASE_URL                            Default: http://localhost:8080
  LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_OUTPUT_DIR       Default: /tmp/linguaframe-demo/demo-reviewer-workspace
  LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_REPORT_ONLY      Default: false. true allows BLOCKED status with exit 0.
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-reviewer-workspace}"
REPORT_ONLY="${LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_REPORT_ONLY:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Missing LINGUAFRAME_DEMO_JOB_ID." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/demo-reviewer-workspace.json"
MARKDOWN_PATH="$OUTPUT_DIR/demo-reviewer-workspace.md"
ZIP_PATH="$OUTPUT_DIR/demo-reviewer-workspace.zip"

download_demo_reviewer_workspace_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_demo_reviewer_workspace_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
download_demo_reviewer_workspace_zip "$BASE_URL" "$JOB_ID" "$ZIP_PATH"

print_demo_reviewer_workspace_summary_file "$JSON_PATH" "$MARKDOWN_PATH" "$ZIP_PATH"

status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys

workspace = json.load(open(sys.argv[1], encoding="utf-8"))
print(workspace.get("overallStatus"))
PY
)"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Demo reviewer workspace is BLOCKED. Set LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
