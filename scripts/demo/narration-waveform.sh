#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
BUCKET_COUNT="${LINGUAFRAME_NARRATION_WAVEFORM_BUCKET_COUNT:-96}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_WAVEFORM_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-waveform}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/narration-waveform.json"

download_narration_waveform_json "$BASE_URL" "$JOB_ID" "$BUCKET_COUNT" "$JSON_PATH"
print_narration_waveform_summary_file "$JSON_PATH"
echo "narrationWaveformJson=$JSON_PATH"
