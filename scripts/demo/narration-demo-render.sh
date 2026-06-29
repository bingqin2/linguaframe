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
GENERATE_VIDEO="${LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO:-true}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_DEMO_RENDER_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-demo-render}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_DEMO_RENDER_REPORT_ONLY:-false}"
PREFLIGHT_REQUIRED="${LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED:-false}"

case "$GENERATE_VIDEO" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO must be true or false." >&2
    exit 2
    ;;
esac

mkdir -p "$OUTPUT_DIR"

CATALOG_PATH="$OUTPUT_DIR/narration-demo-presets.json"
PRESET_PATH="$OUTPUT_DIR/narration-demo-preset.json"
RENDER_PATH="$OUTPUT_DIR/narration-demo-render.json"
PREFLIGHT_PATH="$OUTPUT_DIR/narration-demo-render-preflight.json"
SCRIPT_PACKAGE_JSON_PATH="$OUTPUT_DIR/narration-script-package.json"
SCRIPT_PACKAGE_MARKDOWN_PATH="$OUTPUT_DIR/narration-script-package.md"
SCRIPT_PACKAGE_ZIP_PATH="$OUTPUT_DIR/narration-script-package.zip"
EVIDENCE_JSON_PATH="$OUTPUT_DIR/narration-evidence.json"
EVIDENCE_MARKDOWN_PATH="$OUTPUT_DIR/narration-evidence.md"
EVIDENCE_ZIP_PATH="$OUTPUT_DIR/narration-evidence.zip"

download_narration_demo_presets_json "$BASE_URL" "$CATALOG_PATH"
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

print_narration_demo_preset_summary_file "$PRESET_PATH"

if [[ "$REPORT_ONLY" == "true" ]]; then
  echo "Report-only mode: downloaded narration demo preset catalog and recommended preset without rendering a job."
  echo "narrationDemoPresetCatalogPath=$CATALOG_PATH"
  exit 0
fi

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument to render narration demo media." >&2
  echo "Use LINGUAFRAME_NARRATION_DEMO_RENDER_REPORT_ONLY=true to inspect preset metadata without a job." >&2
  exit 2
fi

if [[ "$PREFLIGHT_REQUIRED" == "true" ]]; then
  preflight_narration_demo_render_json "$BASE_URL" "$JOB_ID" "$resolved_preset_id" true "$GENERATE_VIDEO" "$PREFLIGHT_PATH"
  print_narration_demo_render_preflight_summary_file "$PREFLIGHT_PATH"
  preflight_status="$(python3 - "$PREFLIGHT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"
  if [[ "$preflight_status" == "BLOCKED" ]]; then
    echo "Narration demo render preflight is BLOCKED; refusing to render." >&2
    exit 1
  fi
fi

render_narration_demo_json "$BASE_URL" "$JOB_ID" "$resolved_preset_id" "$GENERATE_VIDEO" "$RENDER_PATH"
print_narration_demo_render_summary_file "$RENDER_PATH"

download_narration_script_package_json "$BASE_URL" "$JOB_ID" "$SCRIPT_PACKAGE_JSON_PATH"
download_narration_script_package_markdown "$BASE_URL" "$JOB_ID" "$SCRIPT_PACKAGE_MARKDOWN_PATH"
download_narration_script_package_zip "$BASE_URL" "$JOB_ID" "$SCRIPT_PACKAGE_ZIP_PATH"
print_narration_script_package_summary_file "$SCRIPT_PACKAGE_JSON_PATH" "$SCRIPT_PACKAGE_MARKDOWN_PATH" "$SCRIPT_PACKAGE_ZIP_PATH"

download_narration_evidence_json "$BASE_URL" "$JOB_ID" "$EVIDENCE_JSON_PATH"
download_narration_evidence_markdown "$BASE_URL" "$JOB_ID" "$EVIDENCE_MARKDOWN_PATH"
download_narration_evidence_zip "$BASE_URL" "$JOB_ID" "$EVIDENCE_ZIP_PATH"
print_narration_evidence_summary_file "$EVIDENCE_JSON_PATH" "$EVIDENCE_MARKDOWN_PATH" "$EVIDENCE_ZIP_PATH"

echo "Rendered narration demo preset $resolved_preset_id for job $JOB_ID."
echo "Downloaded render result, script package, and narration evidence to $OUTPUT_DIR"
