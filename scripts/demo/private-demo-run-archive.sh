#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_PRIVATE_DEMO_RUN_ARCHIVE_OUTPUT_DIR:-/tmp/linguaframe-demo/private-demo-run-archive}"
JSON_PATH="${LINGUAFRAME_PRIVATE_DEMO_RUN_ARCHIVE_JSON_PATH:-$OUTPUT_DIR/run-archive.json}"
REPORT_PATH="${LINGUAFRAME_PRIVATE_DEMO_RUN_ARCHIVE_REPORT_PATH:-$OUTPUT_DIR/run-archive.md}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$REPORT_PATH")"

download_private_demo_run_archive_json "$BASE_URL" "$JSON_PATH"
print_private_demo_run_archive_summary_file "$JSON_PATH"
write_private_demo_run_archive_report "$JSON_PATH" "$REPORT_PATH"

status="$(extract_json_field overallStatus <"$JSON_PATH")"
echo "Wrote private demo run archive JSON to $JSON_PATH"
echo "Wrote private demo run archive report to $REPORT_PATH"

if [[ "$status" == "BLOCKED" ]]; then
  exit 1
fi
