#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_RENDER_REVIEW_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-render-review}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_RENDER_REVIEW_REPORT_ONLY:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/narration-render-review.json"
MARKDOWN_PATH="$OUTPUT_DIR/narration-render-review.md"

download_narration_render_review_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"

download_narration_render_review_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
print_narration_render_review_summary_file "$JSON_PATH" "$MARKDOWN_PATH"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Narration render review is BLOCKED for job $JOB_ID. Set LINGUAFRAME_NARRATION_RENDER_REVIEW_REPORT_ONLY=true to export the blocked report without failing." >&2
  exit 1
fi
