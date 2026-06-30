#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
VOICE="${LINGUAFRAME_NARRATION_AUDITION_VOICE:-${2:-}}"
TEXT_FILE="${LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE:-}"
TEXT="${LINGUAFRAME_NARRATION_AUDITION_TEXT:-}"
OUTPUT_PATH="${LINGUAFRAME_NARRATION_AUDITION_OUTPUT_PATH:-/tmp/linguaframe-demo/narration-voice-audition.mp3}"
OUTPUT_DIR="$(dirname "$OUTPUT_PATH")"
REQUEST_PATH="$OUTPUT_DIR/narration-voice-audition-request.json"
TMP_OUTPUT_PATH="$OUTPUT_PATH.tmp"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

if [[ -z "${VOICE//[[:space:]]/}" ]]; then
  echo "Set LINGUAFRAME_NARRATION_AUDITION_VOICE or pass a voice as the second argument." >&2
  exit 2
fi

if [[ -n "$TEXT_FILE" ]]; then
  if [[ ! -r "$TEXT_FILE" ]]; then
    echo "LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE is not readable." >&2
    exit 2
  fi
  TEXT="$(<"$TEXT_FILE")"
fi

if [[ -z "${TEXT//[[:space:]]/}" ]]; then
  echo "Set LINGUAFRAME_NARRATION_AUDITION_TEXT or LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

python3 - "$TEXT" "$VOICE" "$REQUEST_PATH" <<'PY'
import json
import pathlib
import sys

text = sys.argv[1].strip()
voice = sys.argv[2].strip()
path = pathlib.Path(sys.argv[3])
path.write_text(
    json.dumps({"text": text, "voice": voice}, ensure_ascii=False, separators=(",", ":")),
    encoding="utf-8",
)
PY

rm -f "$TMP_OUTPUT_PATH"

if ! download_narration_segment_preview_audio "$BASE_URL" "$JOB_ID" "$REQUEST_PATH" "$TMP_OUTPUT_PATH"; then
  rm -f "$TMP_OUTPUT_PATH"
  echo "Narration voice audition request failed for job $JOB_ID." >&2
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

bytes="$(wc -c <"$OUTPUT_PATH" | tr -d ' ')"
content_type="$(file -b --mime-type "$OUTPUT_PATH" 2>/dev/null || printf 'audio/mpeg')"

echo "narrationVoiceAuditionJobId=$JOB_ID"
echo "narrationVoiceAuditionVoice=$VOICE"
echo "narrationVoiceAuditionCharacters=$characters"
echo "narrationVoiceAuditionContentType=$content_type"
echo "narrationVoiceAuditionBytes=$bytes"
echo "narrationVoiceAuditionRequestPath=$REQUEST_PATH"
echo "narrationVoiceAuditionOutputPath=$OUTPUT_PATH"
echo "narrationVoiceAuditionProviderCostWarning=Voice audition calls the configured TTS provider and may consume credits; it does not save rows or create artifacts."
