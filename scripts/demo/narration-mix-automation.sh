#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_MIX_AUTOMATION_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-mix-automation}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

WORKSPACE_PATH="$OUTPUT_DIR/narration-workspace.json"

download_narration_workspace_json "$BASE_URL" "$JOB_ID" "$WORKSPACE_PATH"
print_narration_mix_automation_summary_file "$WORKSPACE_PATH"
echo "narrationMixAutomationWorkspacePath=$WORKSPACE_PATH"
echo "narrationMixAutomationSafety=Report output is metadata-only; narration text and media bytes are not printed."
