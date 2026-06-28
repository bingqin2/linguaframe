#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
DEMO_PROFILE_ID="${LINGUAFRAME_DEMO_PROFILE_ID:-quick-baseline}"
OUTPUT_PATH="${LINGUAFRAME_UPLOAD_READINESS_JSON_PATH:-/tmp/linguaframe-demo/upload-readiness.json}"
REPORT_ONLY="${LINGUAFRAME_UPLOAD_READINESS_REPORT_ONLY:-false}"

mkdir -p "$(dirname "$OUTPUT_PATH")"

download_upload_readiness_json "$BASE_URL" "$DEMO_PROFILE_ID" "$OUTPUT_PATH"
print_upload_readiness_summary_file "$OUTPUT_PATH"

status="$(extract_json_field overallStatus <"$OUTPUT_PATH")"
echo "Wrote upload readiness JSON to $OUTPUT_PATH"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  exit 1
fi
