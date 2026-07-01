#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_UPLOAD_NARRATION_LAUNCHPAD_OUTPUT_DIR:-/tmp/linguaframe-demo/upload-narration-launchpad}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/upload-narration-launchpad.json"
MARKDOWN_PATH="$OUTPUT_DIR/upload-narration-launchpad.md"

download_upload_narration_launchpad_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_upload_narration_launchpad_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
print_upload_narration_launchpad_summary_file "$JSON_PATH" "$MARKDOWN_PATH"
