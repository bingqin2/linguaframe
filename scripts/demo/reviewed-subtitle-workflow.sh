#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID to an existing localization job id." >&2
  exit 2
fi

require_command curl
require_command python3
wait_for_backend "$BASE_URL"

mkdir -p "$OUTPUT_DIR"
WORKFLOW_JSON="$OUTPUT_DIR/reviewed-subtitle-workflow-$JOB_ID.json"

download_reviewed_subtitle_workflow_json "$BASE_URL" "$JOB_ID" "$WORKFLOW_JSON"
echo "Downloaded reviewed subtitle workflow: $WORKFLOW_JSON"
print_reviewed_subtitle_workflow_summary_file "$WORKFLOW_JSON"
