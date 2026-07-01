#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_STUDIO_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-studio}"
STUDIO_JSON_PATH="${LINGUAFRAME_NARRATION_STUDIO_JSON_PATH:-$OUTPUT_DIR/narration-studio.json}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID to fetch narration studio state." >&2
  exit 2
fi

wait_for_backend "$BASE_URL"
download_narration_studio_json "$BASE_URL" "$JOB_ID" "$STUDIO_JSON_PATH"
print_narration_studio_summary_file "$STUDIO_JSON_PATH"
echo "Downloaded narration studio JSON to $STUDIO_JSON_PATH"
