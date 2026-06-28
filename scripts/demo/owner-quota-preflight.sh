#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_PATH="${LINGUAFRAME_OWNER_QUOTA_PREFLIGHT_JSON_PATH:-/tmp/linguaframe-demo/owner-quota-preflight.json}"
REPORT_ONLY="${LINGUAFRAME_OWNER_QUOTA_REPORT_ONLY:-false}"

mkdir -p "$(dirname "$OUTPUT_PATH")"

download_owner_quota_preflight_json "$BASE_URL" "$OUTPUT_PATH"
print_owner_quota_preflight_summary_file "$OUTPUT_PATH"

allowed="$(extract_json_field allowed <"$OUTPUT_PATH")"
echo "Wrote owner quota preflight JSON to $OUTPUT_PATH"

if [[ "$allowed" != "True" && "$allowed" != "true" && "$REPORT_ONLY" != "true" ]]; then
  exit 1
fi
