#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
PROFILE_ID="${LINGUAFRAME_NARRATION_DEMO_PRESET_PROFILE_ID:-${LINGUAFRAME_DEMO_PROFILE_ID:-tears-showcase}}"
PRESET_ID="${LINGUAFRAME_NARRATION_DEMO_PRESET_ID:-}"
REPLACE_EXISTING="${LINGUAFRAME_NARRATION_DEMO_RENDER_REPLACE_EXISTING:-true}"
GENERATE_VIDEO="${LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO:-true}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-demo-render-preflight}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REPORT_ONLY:-false}"

case "$REPLACE_EXISTING" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_NARRATION_DEMO_RENDER_REPLACE_EXISTING must be true or false." >&2
    exit 2
    ;;
esac

case "$GENERATE_VIDEO" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO must be true or false." >&2
    exit 2
    ;;
esac

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument to preflight narration demo render." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

PRESET_PATH="$OUTPUT_DIR/narration-demo-preset.json"
PREFLIGHT_PATH="$OUTPUT_DIR/narration-demo-render-preflight.json"

download_narration_demo_preset_json "$BASE_URL" "$PROFILE_ID" "$PRESET_PATH"

resolved_preset_id="$(python3 - "$PRESET_PATH" "$PRESET_ID" <<'PY'
import json
import sys

preset = json.load(open(sys.argv[1], encoding="utf-8"))
override = sys.argv[2].strip()
print(override or preset.get("presetId", ""))
PY
)"

if [[ -z "$resolved_preset_id" ]]; then
  echo "No narration demo preset is configured for profile $PROFILE_ID." >&2
  exit 1
fi

preflight_narration_demo_render_json "$BASE_URL" "$JOB_ID" "$resolved_preset_id" "$REPLACE_EXISTING" "$GENERATE_VIDEO" "$PREFLIGHT_PATH"
print_narration_demo_render_preflight_summary_file "$PREFLIGHT_PATH"

preflight_status="$(python3 - "$PREFLIGHT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"

echo "Downloaded narration demo render preflight to $PREFLIGHT_PATH"

if [[ "$preflight_status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Narration demo render preflight is BLOCKED. Set LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REPORT_ONLY=true to collect the report without failing." >&2
  exit 1
fi
