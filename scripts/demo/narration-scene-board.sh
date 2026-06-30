#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_SCENE_BOARD_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-scene-board}"
REPORT_ONLY="${LINGUAFRAME_NARRATION_SCENE_BOARD_REPORT_ONLY:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

JSON_PATH="$OUTPUT_DIR/narration-scene-board.json"
MARKDOWN_PATH="$OUTPUT_DIR/narration-scene-board.md"

demo_curl -fsS "$BASE_URL/api/jobs/$JOB_ID/narration-scene-board" -o "$JSON_PATH"
demo_curl -fsS "$BASE_URL/api/jobs/$JOB_ID/narration-scene-board/markdown/download" -o "$MARKDOWN_PATH"

status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys

board = json.load(open(sys.argv[1], encoding="utf-8"))
print(board.get("status", ""))
PY
)"

python3 - "$JSON_PATH" "$MARKDOWN_PATH" <<'PY'
import json
import sys

json_path, markdown_path = sys.argv[1], sys.argv[2]
board = json.load(open(json_path, encoding="utf-8"))
blocked = [
    check.get("label", check.get("key", "unknown"))
    for check in board.get("checks", [])
    if check.get("status") == "BLOCKED"
]
actions = board.get("recommendedActions", [])
next_action = actions[0].get("label", "Review narration workspace") if actions else "Review narration workspace"

print(f"narrationSceneBoardJobId={board.get('jobId', '')}")
print(f"narrationSceneBoardStatus={board.get('status', '')}")
print(f"narrationSceneBoardSegmentCount={board.get('segmentCount', 0)}")
print(f"narrationSceneBoardCoveragePercent={board.get('coveragePercent', 0)}")
print(f"narrationSceneBoardGapCount={board.get('gapCount', 0)}")
print(f"narrationSceneBoardHasOverlap={str(board.get('hasOverlap', False)).lower()}")
print(f"narrationSceneBoardVoiceCount={board.get('voiceCount', 0)}")
print(f"narrationSceneBoardMixKeyframeCount={board.get('mixKeyframeCount', 0)}")
print(f"narrationSceneBoardAudioReady={str(board.get('audioReady', False)).lower()}")
print(f"narrationSceneBoardVideoReady={str(board.get('videoReady', False)).lower()}")
print(f"narrationSceneBoardBlockedChecks={','.join(blocked)}")
print(f"narrationSceneBoardNextAction={next_action}")
print(f"narrationSceneBoardJsonPath={json_path}")
print(f"narrationSceneBoardMarkdownPath={markdown_path}")
print("narrationSceneBoardSafety=Metadata-only report; narration text, object keys, local paths, provider payloads, tokens, secrets, and media bytes are not printed.")
PY

if [[ "$status" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "Narration scene board is BLOCKED for job $JOB_ID. Set LINGUAFRAME_NARRATION_SCENE_BOARD_REPORT_ONLY=true to export the blocked report without failing." >&2
  exit 1
fi
