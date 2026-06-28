#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-monitor.sh

Downloads the metadata-only live demo run monitor JSON and Markdown for one job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                    Required selected job id
  LINGUAFRAME_DEMO_BASE_URL                  Default: http://localhost:8080
  LINGUAFRAME_DEMO_RUN_MONITOR_OUTPUT_DIR    Default: /tmp/linguaframe-demo/demo-run-monitor
  LINGUAFRAME_DEMO_RUN_MONITOR_WATCH         Default: false
  LINGUAFRAME_DEMO_RUN_MONITOR_ATTEMPTS      Default: 60
  LINGUAFRAME_DEMO_RUN_MONITOR_INTERVAL      Default: 5
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_RUN_MONITOR_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-run-monitor}"
JSON_PATH="${LINGUAFRAME_DEMO_RUN_MONITOR_JSON_PATH:-$OUTPUT_DIR/demo-run-monitor.json}"
MARKDOWN_PATH="${LINGUAFRAME_DEMO_RUN_MONITOR_MARKDOWN_PATH:-$OUTPUT_DIR/demo-run-monitor.md}"
WATCH="${LINGUAFRAME_DEMO_RUN_MONITOR_WATCH:-false}"
WATCH_ATTEMPTS="${LINGUAFRAME_DEMO_RUN_MONITOR_ATTEMPTS:-60}"
WATCH_INTERVAL="${LINGUAFRAME_DEMO_RUN_MONITOR_INTERVAL:-5}"

if [[ -z "$JOB_ID" ]]; then
  echo "Missing LINGUAFRAME_DEMO_JOB_ID." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")" "$(dirname "$MARKDOWN_PATH")"

attempt=1
while :; do
  download_demo_run_monitor_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
  download_demo_run_monitor_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_PATH"
  print_demo_run_monitor_summary_file "$JSON_PATH"

  if [[ "$WATCH" != "true" ]]; then
    break
  fi

  status="$(python3 - "$JSON_PATH" <<'PY'
import json
import sys
body = json.load(open(sys.argv[1], encoding="utf-8"))
print(body.get("status", "UNKNOWN"))
PY
)"
  if [[ "$status" == "COMPLETED" || "$status" == "FAILED" || "$status" == "CANCELLED" ]]; then
    break
  fi
  if (( attempt >= WATCH_ATTEMPTS )); then
    echo "Demo run monitor watch reached attempt limit at status $status." >&2
    break
  fi
  attempt=$((attempt + 1))
  sleep "$WATCH_INTERVAL"
done

echo "Wrote demo run monitor JSON to $JSON_PATH"
echo "Wrote demo run monitor Markdown to $MARKDOWN_PATH"
