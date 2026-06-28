#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_PRIVATE_DEMO_EVIDENCE_GALLERY_OUTPUT_DIR:-/tmp/linguaframe-demo/private-demo-evidence-gallery}"
JSON_PATH="${LINGUAFRAME_PRIVATE_DEMO_EVIDENCE_GALLERY_JSON_PATH:-$OUTPUT_DIR/evidence-gallery.json}"
REPORT_PATH="${LINGUAFRAME_PRIVATE_DEMO_EVIDENCE_GALLERY_REPORT_PATH:-$OUTPUT_DIR/evidence-gallery.md}"
LIMIT="${LINGUAFRAME_PRIVATE_DEMO_EVIDENCE_GALLERY_LIMIT:-20}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$REPORT_PATH")"

download_private_demo_evidence_gallery_json "$BASE_URL" "$JSON_PATH" "$LIMIT"
print_private_demo_evidence_gallery_summary_file "$JSON_PATH"
write_private_demo_evidence_gallery_report "$JSON_PATH" "$REPORT_PATH"

echo "Wrote private demo evidence gallery JSON to $JSON_PATH"
echo "Wrote private demo evidence gallery report to $REPORT_PATH"
