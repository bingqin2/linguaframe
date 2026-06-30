#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_OUTPUT_DIR:-/tmp/linguaframe-demo/private-demo-delivery-receipt}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
JSON_PATH="${LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_JSON_PATH:-$OUTPUT_DIR/private-demo-delivery-receipt.json}"
MARKDOWN_PATH="${LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_MARKDOWN_PATH:-$OUTPUT_DIR/private-demo-delivery-receipt.md}"
ZIP_PATH="${LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_ZIP_PATH:-$OUTPUT_DIR/private-demo-delivery-receipt.zip}"
REPORT_ONLY="${LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_REPORT_ONLY:-false}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$MARKDOWN_PATH")" "$(dirname "$ZIP_PATH")"

download_private_demo_delivery_receipt_json "$BASE_URL" "$JSON_PATH" "$JOB_ID"
download_private_demo_delivery_receipt_markdown "$BASE_URL" "$MARKDOWN_PATH" "$JOB_ID"
download_private_demo_delivery_receipt_zip "$BASE_URL" "$ZIP_PATH" "$JOB_ID"
print_private_demo_delivery_receipt_summary_file "$JSON_PATH"

status="$(extract_json_field overallStatus <"$JSON_PATH")"
echo "Wrote private demo delivery receipt JSON to $JSON_PATH"
echo "Wrote private demo delivery receipt Markdown to $MARKDOWN_PATH"
echo "Wrote private demo delivery receipt ZIP to $ZIP_PATH"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Private demo delivery receipt is BLOCKED. Set LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
