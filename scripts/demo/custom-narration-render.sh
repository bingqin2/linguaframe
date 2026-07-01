#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
GENERATE_VIDEO="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_GENERATE_VIDEO:-${LINGUAFRAME_CUSTOM_NARRATION_GENERATE_VIDEO:-true}}"
ACKNOWLEDGE_PROVIDER_COST="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_ACK_PROVIDER_COST:-${LINGUAFRAME_CUSTOM_NARRATION_ACK_PROVIDER_COST:-true}}"
ACKNOWLEDGE_VIDEO_RENDER="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_ACK_VIDEO:-${LINGUAFRAME_CUSTOM_NARRATION_ACK_VIDEO_RENDER:-true}}"
OUTPUT_DIR="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_OUTPUT_DIR:-/tmp/linguaframe-demo/custom-narration-render}"
PREFLIGHT_REQUIRED="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_PREFLIGHT_REQUIRED:-true}"
REPORT_ONLY="${LINGUAFRAME_CUSTOM_NARRATION_RENDER_REPORT_ONLY:-false}"

case "$GENERATE_VIDEO" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_CUSTOM_NARRATION_GENERATE_VIDEO must be true or false." >&2
    exit 2
    ;;
esac

case "$ACKNOWLEDGE_PROVIDER_COST" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_CUSTOM_NARRATION_ACK_PROVIDER_COST must be true or false." >&2
    exit 2
    ;;
esac

case "$ACKNOWLEDGE_VIDEO_RENDER" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_CUSTOM_NARRATION_ACK_VIDEO_RENDER must be true or false." >&2
    exit 2
    ;;
esac

case "$PREFLIGHT_REQUIRED" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_CUSTOM_NARRATION_RENDER_PREFLIGHT_REQUIRED must be true or false." >&2
    exit 2
    ;;
esac

case "$REPORT_ONLY" in
  true|false)
    ;;
  *)
    echo "LINGUAFRAME_CUSTOM_NARRATION_RENDER_REPORT_ONLY must be true or false." >&2
    exit 2
    ;;
esac

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument to render saved custom narration rows." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

PREFLIGHT_PATH="$OUTPUT_DIR/custom-narration-render-preflight.json"
RENDER_PATH="$OUTPUT_DIR/custom-narration-render.json"
REPORT_PATH="$OUTPUT_DIR/custom-narration-render.md"
EVIDENCE_JSON_PATH="$OUTPUT_DIR/narration-evidence.json"
EVIDENCE_MARKDOWN_PATH="$OUTPUT_DIR/narration-evidence.md"
EVIDENCE_ZIP_PATH="$OUTPUT_DIR/narration-evidence.zip"
DELIVERY_JSON_PATH="$OUTPUT_DIR/narration-delivery-package.json"
DELIVERY_MARKDOWN_PATH="$OUTPUT_DIR/narration-delivery-package.md"
DELIVERY_ZIP_PATH="$OUTPUT_DIR/narration-delivery-package.zip"

preflight_custom_narration_render_json \
  "$BASE_URL" \
  "$JOB_ID" \
  "$GENERATE_VIDEO" \
  "$ACKNOWLEDGE_PROVIDER_COST" \
  "$ACKNOWLEDGE_VIDEO_RENDER" \
  "$PREFLIGHT_PATH"
print_custom_narration_render_preflight_summary_file "$PREFLIGHT_PATH"

preflight_status="$(python3 - "$PREFLIGHT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"

download_custom_narration_render_markdown "$BASE_URL" "$JOB_ID" "$REPORT_PATH"
echo "Downloaded custom narration render report to $REPORT_PATH"

if [[ "$preflight_status" == "BLOCKED" ]]; then
  echo "Custom narration render preflight is BLOCKED." >&2
  if [[ "$REPORT_ONLY" == "true" ]]; then
    exit 0
  fi
  exit 1
fi

if [[ "$REPORT_ONLY" == "true" ]]; then
  echo "Report-only mode: preflight and Markdown report collected without rendering media."
  exit 0
fi

if [[ "$PREFLIGHT_REQUIRED" != "true" ]]; then
  echo "Warning: preflight requirement disabled; rendering continues using current acknowledgements." >&2
fi

render_custom_narration_json \
  "$BASE_URL" \
  "$JOB_ID" \
  "$GENERATE_VIDEO" \
  "$ACKNOWLEDGE_PROVIDER_COST" \
  "$ACKNOWLEDGE_VIDEO_RENDER" \
  "$RENDER_PATH"
print_custom_narration_render_summary_file "$RENDER_PATH"

download_custom_narration_render_markdown "$BASE_URL" "$JOB_ID" "$REPORT_PATH"
download_narration_evidence_json "$BASE_URL" "$JOB_ID" "$EVIDENCE_JSON_PATH"
download_narration_evidence_markdown "$BASE_URL" "$JOB_ID" "$EVIDENCE_MARKDOWN_PATH"
download_narration_evidence_zip "$BASE_URL" "$JOB_ID" "$EVIDENCE_ZIP_PATH"
download_narration_delivery_package_json "$BASE_URL" "$JOB_ID" "$DELIVERY_JSON_PATH"
download_narration_delivery_package_markdown "$BASE_URL" "$JOB_ID" "$DELIVERY_MARKDOWN_PATH"
download_narration_delivery_package_zip "$BASE_URL" "$JOB_ID" "$DELIVERY_ZIP_PATH"
print_narration_evidence_summary_file "$EVIDENCE_JSON_PATH" "$EVIDENCE_MARKDOWN_PATH" "$EVIDENCE_ZIP_PATH"
print_narration_delivery_package_summary_file "$DELIVERY_JSON_PATH" "$DELIVERY_MARKDOWN_PATH" "$DELIVERY_ZIP_PATH"

echo "Rendered custom narration for job $JOB_ID."
echo "Downloaded custom render result, report, evidence, and delivery package to $OUTPUT_DIR"
