#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/retry-sample.mp4}"

wait_for_backend "$BASE_URL"
create_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for forced failure on job $job_id..."
failed_response="$(wait_for_job_status "$BASE_URL" "$job_id" FAILED)"
printf '%s' "$failed_response" | print_job_summary

echo
echo "Disable LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED, restart linguaframe-backend, then press Enter to retry."
read -r _

retry_response="$(curl -fsS -X POST "$BASE_URL/api/jobs/$job_id/retry")"
retry_status="$(printf '%s' "$retry_response" | extract_json_field status)"
if [[ "$retry_status" != "RETRYING" ]]; then
  echo "Expected retry API to return RETRYING, got $retry_status" >&2
  exit 1
fi

completed_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$completed_response" | print_job_summary
