#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_EVIDENCE_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-evidence}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_EVIDENCE_REPORT_ONLY:-false}"
GENERATE_NARRATED_VIDEO="${LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/narration-evidence.json"
MARKDOWN_PATH="$OUTPUT_DIR/narration-evidence.md"
ZIP_PATH="$OUTPUT_DIR/narration-evidence.zip"
NARRATED_VIDEO_JSON_PATH="$OUTPUT_DIR/narrated-video-generation.json"

download_narration_evidence_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Narration evidence is BLOCKED for job $JOB_ID. Set LINGUAFRAME_NARRATION_EVIDENCE_REPORT_ONLY=true to export the blocked report." >&2
  exit 1
fi

if [[ "$GENERATE_NARRATED_VIDEO" == "true" && "$status" != "BLOCKED" ]]; then
  audio_ready="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
print(str(json.load(open(sys.argv[1], encoding="utf-8")).get("narrationAudioReady", False)).lower())
PY
)"
  if [[ "$audio_ready" == "true" ]]; then
    generate_narrated_video_json "$BASE_URL" "$JOB_ID" "$NARRATED_VIDEO_JSON_PATH"
    print_narrated_video_generation_summary_file "$NARRATED_VIDEO_JSON_PATH"
    download_narration_evidence_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
  else
    echo "Skipping narrated video generation because narration audio is not ready."
  fi
fi

download_narration_evidence_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
download_narration_evidence_zip "$BASE_URL" "$JOB_ID" "$ZIP_PATH"
print_narration_evidence_summary_file "$JSON_PATH" "$MARKDOWN_PATH" "$ZIP_PATH"
