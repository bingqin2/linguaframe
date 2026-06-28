#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_PRIVATE_DEMO_LAUNCH_REHEARSAL_OUTPUT_DIR:-/tmp/linguaframe-demo/private-demo-launch-rehearsal}"
JSON_PATH="${LINGUAFRAME_PRIVATE_DEMO_LAUNCH_REHEARSAL_JSON_PATH:-$OUTPUT_DIR/launch-rehearsal.json}"
REPORT_PATH="${LINGUAFRAME_PRIVATE_DEMO_LAUNCH_REHEARSAL_REPORT_PATH:-$OUTPUT_DIR/launch-rehearsal.md}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$REPORT_PATH")"

download_private_demo_launch_rehearsal_json "$BASE_URL" "$JSON_PATH"
print_private_demo_launch_rehearsal_summary_file "$JSON_PATH"
write_private_demo_launch_rehearsal_report "$JSON_PATH" "$REPORT_PATH"

status="$(extract_json_field overallStatus <"$JSON_PATH")"
echo "Wrote private demo launch rehearsal JSON to $JSON_PATH"
echo "Wrote private demo launch rehearsal report to $REPORT_PATH"

if [[ "$status" == "BLOCKED" ]]; then
  exit 1
fi
