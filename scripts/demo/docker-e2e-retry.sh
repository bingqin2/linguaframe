#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/retry-sample.mp4}"
MAX_RETRIES="${LINGUAFRAME_WORKER_MAX_RETRIES:-2}"
FAILED_DIAGNOSTICS_PATH="${LINGUAFRAME_DEMO_FAILED_DIAGNOSTICS_PATH:-/tmp/linguaframe-demo/retry-failed-diagnostics.json}"
COMPLETED_DIAGNOSTICS_PATH="${LINGUAFRAME_DEMO_COMPLETED_DIAGNOSTICS_PATH:-/tmp/linguaframe-demo/retry-completed-diagnostics.json}"

wait_for_backend "$BASE_URL"
create_demo_sample "$SAMPLE_PATH"

echo "Retry demo uses LINGUAFRAME_WORKER_MAX_RETRIES=$MAX_RETRIES."
echo "Start this run with LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true to force the first failure."

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for forced failure on job $job_id..."
failed_response="$(wait_for_job_status "$BASE_URL" "$job_id" FAILED)"
printf '%s' "$failed_response" | print_job_summary
download_job_diagnostics "$BASE_URL" "$job_id" "$FAILED_DIAGNOSTICS_PATH"
print_diagnostics_summary "$FAILED_DIAGNOSTICS_PATH"
echo "Downloaded failed-attempt diagnostics report to $FAILED_DIAGNOSTICS_PATH"

echo
echo "Disable LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED, restart linguaframe-backend, then press Enter to retry once."
read -r _

retry_response="$(curl -fsS -X POST "$BASE_URL/api/jobs/$job_id/retry")"
retry_status="$(printf '%s' "$retry_response" | extract_json_field status)"
if [[ "$retry_status" != "RETRYING" ]]; then
  echo "Expected retry API to return RETRYING, got $retry_status" >&2
  exit 1
fi

completed_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$completed_response" | print_job_summary
download_job_diagnostics "$BASE_URL" "$job_id" "$COMPLETED_DIAGNOSTICS_PATH"
print_diagnostics_summary "$COMPLETED_DIAGNOSTICS_PATH"
echo "Downloaded completed retry diagnostics report to $COMPLETED_DIAGNOSTICS_PATH"
