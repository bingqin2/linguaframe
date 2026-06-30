#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/stuck-job-recovery.sh
       scripts/demo/stuck-job-recovery.sh <job-id>

Downloads metadata-only stuck job recovery JSON and Markdown for one job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                         Required unless job id is first argument
  LINGUAFRAME_DEMO_BASE_URL                       Default: http://localhost:8080
  LINGUAFRAME_STUCK_JOB_RECOVERY_OUTPUT_DIR       Default: /tmp/linguaframe-demo/stuck-job-recovery
  LINGUAFRAME_STUCK_JOB_RECOVERY_ACTION           Optional: REQUEUE_DISPATCH, CANCEL_JOB, RETRY_FAILED_JOB
  LINGUAFRAME_STUCK_JOB_RECOVERY_CONFIRM          Must match action id when action is set
  LINGUAFRAME_STUCK_JOB_RECOVERY_REPORT_ONLY      Default: false
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_STUCK_JOB_RECOVERY_OUTPUT_DIR:-/tmp/linguaframe-demo/stuck-job-recovery}"
JSON_PATH="${LINGUAFRAME_STUCK_JOB_RECOVERY_JSON_PATH:-$OUTPUT_DIR/stuck-job-recovery.json}"
MARKDOWN_PATH="${LINGUAFRAME_STUCK_JOB_RECOVERY_MARKDOWN_PATH:-$OUTPUT_DIR/stuck-job-recovery.md}"
ACTION="${LINGUAFRAME_STUCK_JOB_RECOVERY_ACTION:-}"
CONFIRMATION="${LINGUAFRAME_STUCK_JOB_RECOVERY_CONFIRM:-}"
REPORT_ONLY="${LINGUAFRAME_STUCK_JOB_RECOVERY_REPORT_ONLY:-false}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$MARKDOWN_PATH")"

download_stuck_job_recovery_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
download_stuck_job_recovery_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"

if [[ -n "$ACTION" ]]; then
  if [[ "$CONFIRMATION" != "$ACTION" ]]; then
    echo "LINGUAFRAME_STUCK_JOB_RECOVERY_CONFIRM must match LINGUAFRAME_STUCK_JOB_RECOVERY_ACTION." >&2
    exit 2
  fi
  run_stuck_job_recovery_action_json "$BASE_URL" "$JOB_ID" "$ACTION" "$CONFIRMATION" "$JSON_PATH"
  download_stuck_job_recovery_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
fi

print_stuck_job_recovery_summary_file "$JSON_PATH"

classification="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
print(body.get("classification", "UNKNOWN"))
PY
)"
status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
print(body.get("status", "UNKNOWN"))
PY
)"

echo "Wrote stuck job recovery JSON to $JSON_PATH"
echo "Wrote stuck job recovery Markdown to $MARKDOWN_PATH"

if [[ "$REPORT_ONLY" != "true" ]]; then
  case "$classification:$status" in
    QUEUED_STALE_DISPATCH:*|PROCESSING_STALE_STAGE:*|*:BLOCKED)
      echo "Stuck job recovery requires attention for job $JOB_ID. Set LINGUAFRAME_STUCK_JOB_RECOVERY_REPORT_ONLY=true to report without failing." >&2
      exit 1
      ;;
  esac
fi
