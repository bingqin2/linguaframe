#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
REPORT_PATH="${LINGUAFRAME_PRIVATE_DEMO_OPERATIONS_REPORT_PATH:-/tmp/linguaframe-demo/private-demo-operations-report.md}"
JSON_PATH="${LINGUAFRAME_PRIVATE_DEMO_OPERATIONS_JSON_PATH:-/tmp/linguaframe-demo/private-demo-operations.json}"

mkdir -p "$(dirname "$REPORT_PATH")" "$(dirname "$JSON_PATH")"

get_private_demo_operations "$BASE_URL" >"$JSON_PATH"
print_private_demo_operations_summary <"$JSON_PATH"
write_private_demo_operations_report "$JSON_PATH" "$REPORT_PATH"

status="$(extract_json_field overallStatus <"$JSON_PATH")"
echo "Wrote private demo operations report to $REPORT_PATH"

if [[ "$status" == "BLOCKED" ]]; then
  exit 1
fi
