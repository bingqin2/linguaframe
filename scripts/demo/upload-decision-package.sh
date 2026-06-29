#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_UPLOAD_PLAN_SAMPLE_PATH:-${LINGUAFRAME_TEARS_SAMPLE_PATH:-${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/sample.mp4}}}"
OUTPUT_DIR="${LINGUAFRAME_UPLOAD_DECISION_PACKAGE_DIR:-/tmp/linguaframe-demo/upload-decision-package}"
MARKDOWN_PATH="${LINGUAFRAME_UPLOAD_DECISION_PACKAGE_MARKDOWN_PATH:-$OUTPUT_DIR/upload-decision-package.md}"
ZIP_PATH="${LINGUAFRAME_UPLOAD_DECISION_PACKAGE_ZIP_PATH:-$OUTPUT_DIR/upload-decision-package.zip}"
DEMO_PROFILE_ID="${LINGUAFRAME_DEMO_PROFILE_ID:-tears-showcase}"
TARGET_LANGUAGE="${LINGUAFRAME_DEMO_TARGET_LANGUAGE:-zh-CN}"
TRANSLATION_STYLE="${LINGUAFRAME_DEMO_TRANSLATION_STYLE:-FORMAL}"
SUBTITLE_STYLE_PRESET="${LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET:-HIGH_CONTRAST}"
SUBTITLE_POLISHING_MODE="${LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE:-BALANCED}"
TRANSLATION_GLOSSARY="${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY:-$'Maya => 玛雅\nTears of Steel => 钢铁之泪\nThom => 汤姆'}"

if [[ -z "$TRANSLATION_GLOSSARY" && -n "${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE:-}" ]]; then
  TRANSLATION_GLOSSARY="$(<"$LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE")"
fi

if [[ ! -s "$SAMPLE_PATH" ]]; then
  echo "Sample video does not exist or is empty. Set LINGUAFRAME_UPLOAD_PLAN_SAMPLE_PATH or LINGUAFRAME_TEARS_SAMPLE_PATH." >&2
  exit 1
fi

wait_for_backend "$BASE_URL"
mkdir -p "$OUTPUT_DIR" "$(dirname "$MARKDOWN_PATH")" "$(dirname "$ZIP_PATH")"

form_args=(
  -F "file=@${SAMPLE_PATH};type=video/mp4"
  -F "targetLanguage=${TARGET_LANGUAGE}"
  -F "demoProfileId=${DEMO_PROFILE_ID}"
  -F "translationStyle=${TRANSLATION_STYLE}"
  -F "subtitleStylePreset=${SUBTITLE_STYLE_PRESET}"
  -F "subtitlePolishingMode=${SUBTITLE_POLISHING_MODE}"
)
if [[ -n "${TRANSLATION_GLOSSARY//[[:space:]]/}" ]]; then
  form_args+=(-F "translationGlossary=${TRANSLATION_GLOSSARY}")
fi

demo_curl -fsS "${form_args[@]}" \
  "$BASE_URL/api/media/uploads/decision-package/markdown/download" \
  >"$MARKDOWN_PATH"

demo_curl -fsS "${form_args[@]}" \
  "$BASE_URL/api/media/uploads/decision-package/download" \
  >"$ZIP_PATH"

echo "uploadDecisionPackageMarkdownPath=$MARKDOWN_PATH"
echo "uploadDecisionPackageZipPath=$ZIP_PATH"
echo "uploadDecisionPackageZipBytes=$(wc -c <"$ZIP_PATH" | tr -d ' ')"
echo "uploadDecisionPackageStatus=written"
