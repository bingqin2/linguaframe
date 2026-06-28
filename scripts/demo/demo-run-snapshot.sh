#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-snapshot.sh

Downloads the metadata-only static demo snapshot JSON preview and ZIP package for one job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                    Required selected job id
  LINGUAFRAME_DEMO_BASE_URL                  Default: http://localhost:8080
  LINGUAFRAME_DEMO_RUN_SNAPSHOT_OUTPUT_DIR   Default: /tmp/linguaframe-demo/demo-run-snapshot
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_RUN_SNAPSHOT_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-run-snapshot}"
JSON_PATH="${LINGUAFRAME_DEMO_RUN_SNAPSHOT_JSON_PATH:-$OUTPUT_DIR/demo-run-snapshot.json}"
ZIP_PATH="${LINGUAFRAME_DEMO_RUN_SNAPSHOT_ZIP_PATH:-$OUTPUT_DIR/demo-run-snapshot.zip}"

if [[ -z "$JOB_ID" ]]; then
  echo "Missing LINGUAFRAME_DEMO_JOB_ID." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$ZIP_PATH")"

download_demo_run_snapshot_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_demo_run_snapshot_zip "$BASE_URL" "$JOB_ID" "$ZIP_PATH"
print_demo_run_snapshot_summary_file "$JSON_PATH"
print_demo_run_snapshot_package_summary "$ZIP_PATH" "$JOB_ID"

echo "Wrote demo run snapshot JSON to $JSON_PATH"
echo "Wrote demo run snapshot ZIP to $ZIP_PATH"
