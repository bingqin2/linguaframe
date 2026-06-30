#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_SESSION_RECOVERY_BOARD_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-session-recovery-board}"
LIMIT="${LINGUAFRAME_DEMO_SESSION_RECOVERY_LIMIT:-20}"
JSON_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_RECOVERY_BOARD_JSON_PATH:-$OUTPUT_DIR/demo-session-recovery-board.json}"
MARKDOWN_OUTPUT_PATH="${LINGUAFRAME_DEMO_SESSION_RECOVERY_BOARD_MARKDOWN_PATH:-$OUTPUT_DIR/demo-session-recovery-board.md}"
REPORT_ONLY="${LINGUAFRAME_DEMO_SESSION_RECOVERY_BOARD_REPORT_ONLY:-false}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$MARKDOWN_OUTPUT_PATH")"

download_demo_session_recovery_board_json "$BASE_URL" "$LIMIT" "$JSON_OUTPUT_PATH"
download_demo_session_recovery_board_markdown "$BASE_URL" "$LIMIT" "$MARKDOWN_OUTPUT_PATH"
print_demo_session_recovery_board_summary_file "$JSON_OUTPUT_PATH"
printf 'demoSessionRecoveryBoardMarkdownPath=%s\n' "$MARKDOWN_OUTPUT_PATH"

RECOVER_NOW_COUNT="$(python3 - "$JSON_OUTPUT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("recoverNowCount", 0))
PY
)"

if [[ "$RECOVER_NOW_COUNT" != "0" && "$REPORT_ONLY" != "true" ]]; then
  echo "Demo session recovery board has recover-now rows. Set LINGUAFRAME_DEMO_SESSION_RECOVERY_BOARD_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
