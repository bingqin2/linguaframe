#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_PREVIEW_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-segment-preview}"
TEXT_FILE="${LINGUAFRAME_NARRATION_PREVIEW_TEXT_FILE:-}"
TEXT="${LINGUAFRAME_NARRATION_PREVIEW_TEXT:-}"
VOICE="${LINGUAFRAME_NARRATION_PREVIEW_VOICE:-}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

if [[ -n "$TEXT_FILE" ]]; then
  if [[ ! -r "$TEXT_FILE" ]]; then
    echo "LINGUAFRAME_NARRATION_PREVIEW_TEXT_FILE is not readable." >&2
    exit 2
  fi
  TEXT="$(<"$TEXT_FILE")"
fi

if [[ -z "${TEXT//[[:space:]]/}" ]]; then
  echo "Set LINGUAFRAME_NARRATION_PREVIEW_TEXT or LINGUAFRAME_NARRATION_PREVIEW_TEXT_FILE." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

REQUEST_PATH="$OUTPUT_DIR/narration-segment-preview-request.json"
OUTPUT_PATH="$OUTPUT_DIR/narration-segment-preview.mp3"
TMP_OUTPUT_PATH="$OUTPUT_PATH.tmp"

python3 - "$TEXT" "$VOICE" "$REQUEST_PATH" <<'PY'
import json
import pathlib
import sys

text = sys.argv[1].strip()
voice = sys.argv[2].strip() or None
path = pathlib.Path(sys.argv[3])
path.write_text(
    json.dumps({"text": text, "voice": voice}, ensure_ascii=False, separators=(",", ":")),
    encoding="utf-8",
)
PY

rm -f "$TMP_OUTPUT_PATH"

if ! download_narration_segment_preview_audio "$BASE_URL" "$JOB_ID" "$REQUEST_PATH" "$TMP_OUTPUT_PATH"; then
  rm -f "$TMP_OUTPUT_PATH"
  echo "Narration segment preview request failed for job $JOB_ID." >&2
  exit 1
fi

mv "$TMP_OUTPUT_PATH" "$OUTPUT_PATH"

characters="$(python3 - "$REQUEST_PATH" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
print(len(payload["text"]))
PY
)"
resolved_voice="$(python3 - "$REQUEST_PATH" <<'PY'
import json
import sys

payload = json.load(open(sys.argv[1], encoding="utf-8"))
print(payload.get("voice") or "inherit job/default voice")
PY
)"

echo "narrationSegmentPreviewJobId=$JOB_ID"
echo "narrationSegmentPreviewVoice=$resolved_voice"
echo "narrationSegmentPreviewCharacters=$characters"
echo "narrationSegmentPreviewRequestPath=$REQUEST_PATH"
echo "narrationSegmentPreviewOutputPath=$OUTPUT_PATH"
echo "narrationSegmentPreviewProviderCostWarning=Preview calls the configured TTS provider and may consume credits; it does not save rows or create artifacts."
