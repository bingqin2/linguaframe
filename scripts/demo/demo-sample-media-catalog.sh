#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_PATH="${LINGUAFRAME_DEMO_SAMPLE_CATALOG_JSON_PATH:-/tmp/linguaframe-demo/demo-sample-media-catalog.json}"
REPORT_ONLY="${LINGUAFRAME_DEMO_SAMPLE_CATALOG_REPORT_ONLY:-false}"

mkdir -p "$(dirname "$OUTPUT_PATH")"

download_demo_sample_media_catalog_json "$BASE_URL" "$OUTPUT_PATH"
print_demo_sample_media_catalog_summary_file "$OUTPUT_PATH"

status="$(extract_json_field overallStatus <"$OUTPUT_PATH")"
echo "Wrote demo sample media catalog JSON to $OUTPUT_PATH"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  exit 1
fi
