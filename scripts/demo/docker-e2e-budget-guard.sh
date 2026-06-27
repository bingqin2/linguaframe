#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/budget-guard-sample.mp4}"

echo "Budget guard demo expects the backend to be started with:"
echo "- LINGUAFRAME_COST_ENABLED=true"
echo "- LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true"
echo "- LINGUAFRAME_COST_MAX_JOB_COST_USD set to a tiny positive value"
echo "- at least one non-zero model-call cost rate"
echo
echo "Example:"
echo "  LINGUAFRAME_COST_ENABLED=true \\"
echo "  LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true \\"
echo "  LINGUAFRAME_COST_MAX_JOB_COST_USD=0.000001 \\"
echo "  LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE=1 \\"
echo "  docker compose --env-file .env up -d --force-recreate linguaframe-backend"
echo

wait_for_backend "$BASE_URL"
ensure_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for budget guard failure on job $job_id..."
failed_response="$(wait_for_job_status "$BASE_URL" "$job_id" FAILED)"
printf '%s' "$failed_response" | print_budget_guard_failure
echo "Budget guard blocked the job as expected."
