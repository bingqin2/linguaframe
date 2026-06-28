#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-share-sheet.sh

Downloads the metadata-only demo share sheet JSON and Markdown for one job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                  Required selected job id
  LINGUAFRAME_DEMO_BASE_URL                Default: http://localhost:8080
  LINGUAFRAME_DEMO_SHARE_SHEET_OUTPUT_DIR  Default: /tmp/linguaframe-demo/demo-share-sheet
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
OUTPUT_DIR="${LINGUAFRAME_DEMO_SHARE_SHEET_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-share-sheet}"
JSON_PATH="${LINGUAFRAME_DEMO_SHARE_SHEET_JSON_PATH:-$OUTPUT_DIR/demo-share-sheet.json}"
MARKDOWN_PATH="${LINGUAFRAME_DEMO_SHARE_SHEET_MARKDOWN_PATH:-$OUTPUT_DIR/demo-share-sheet.md}"

if [[ -z "$JOB_ID" ]]; then
  echo "Missing LINGUAFRAME_DEMO_JOB_ID." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$MARKDOWN_PATH")"

download_demo_share_sheet_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_demo_share_sheet_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
print_demo_share_sheet_summary_file "$JSON_PATH"

echo "Wrote demo share sheet JSON to $JSON_PATH"
echo "Wrote demo share sheet Markdown to $MARKDOWN_PATH"
