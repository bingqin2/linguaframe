#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/daily-budget-guard-sample.mp4}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_DAILY_BUDGET_OUTPUT_DIR:-/tmp/linguaframe-demo/daily-budget-guard}"
FIRST_JOB_DETAIL_PATH="$OUTPUT_DIR/first-job.json"
SECOND_JOB_DETAIL_PATH="$OUTPUT_DIR/second-job.json"
SECOND_DIAGNOSTICS_PATH="$OUTPUT_DIR/second-diagnostics.json"

echo "Daily budget guard demo expects the backend to be started with:"
echo "- LINGUAFRAME_COST_ENABLED=true"
echo "- LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true"
echo "- LINGUAFRAME_COST_MAX_JOB_COST_USD high enough for the first job"
echo "- LINGUAFRAME_COST_DAILY_BUDGET_GUARD_ENABLED=true"
echo "- LINGUAFRAME_COST_MAX_DAILY_COST_USD set to a tiny positive value"
echo "- LINGUAFRAME_COST_BUDGET_IDENTITY=demo-owner"
echo "- at least one non-zero model-call cost rate"
echo
echo "Example:"
echo "  LINGUAFRAME_COST_ENABLED=true \\"
echo "  LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true \\"
echo "  LINGUAFRAME_COST_MAX_JOB_COST_USD=1 \\"
echo "  LINGUAFRAME_COST_DAILY_BUDGET_GUARD_ENABLED=true \\"
echo "  LINGUAFRAME_COST_MAX_DAILY_COST_USD=0.000001 \\"
echo "  LINGUAFRAME_COST_BUDGET_IDENTITY=demo-owner \\"
echo "  LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE=1 \\"
echo "  docker compose --env-file .env up -d --force-recreate linguaframe-backend"
echo

wait_for_backend "$BASE_URL"
ensure_demo_sample "$SAMPLE_PATH"
mkdir -p "$OUTPUT_DIR"

echo "Uploading first demo video to create same-day budget usage..."
first_upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
first_job_id="$(printf '%s' "$first_upload_response" | extract_json_field jobId)"
echo "Waiting for first job $first_job_id to complete..."
wait_for_job_status "$BASE_URL" "$first_job_id" COMPLETED >/dev/null
download_job_detail "$BASE_URL" "$first_job_id" "$FIRST_JOB_DETAIL_PATH"
echo "First job summary:"
print_job_cache_summary_file "$FIRST_JOB_DETAIL_PATH"

echo "Uploading second demo video; daily budget guard should block a guarded AI stage..."
second_upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
second_job_id="$(printf '%s' "$second_upload_response" | extract_json_field jobId)"
echo "Waiting for second job $second_job_id to fail on daily budget..."
failed_response="$(wait_for_job_status "$BASE_URL" "$second_job_id" FAILED)"
printf '%s' "$failed_response" >"$SECOND_JOB_DETAIL_PATH"
printf '%s' "$failed_response" | print_budget_guard_failure "Daily cost budget exceeded"
download_job_diagnostics "$BASE_URL" "$second_job_id" "$SECOND_DIAGNOSTICS_PATH"
print_diagnostics_summary "$SECOND_DIAGNOSTICS_PATH"
echo "Downloaded daily budget guard evidence to $OUTPUT_DIR"
