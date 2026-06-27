#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/sample.mp4}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_CACHE_HIT_OUTPUT_DIR:-/tmp/linguaframe-demo/cache-hit}"
FIRST_JOB_DETAIL_PATH="$OUTPUT_DIR/first-job.json"
SECOND_JOB_DETAIL_PATH="$OUTPUT_DIR/second-job.json"
FIRST_DIAGNOSTICS_PATH="$OUTPUT_DIR/first-diagnostics.json"
SECOND_DIAGNOSTICS_PATH="$OUTPUT_DIR/second-diagnostics.json"

wait_for_backend "$BASE_URL"
ensure_demo_sample "$SAMPLE_PATH"
mkdir -p "$OUTPUT_DIR"

echo "Uploading first demo video for cache warm-up..."
first_upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
first_job_id="$(printf '%s' "$first_upload_response" | extract_json_field jobId)"
echo "Waiting for first job $first_job_id to complete..."
wait_for_job_status "$BASE_URL" "$first_job_id" COMPLETED >/dev/null
download_job_detail "$BASE_URL" "$first_job_id" "$FIRST_JOB_DETAIL_PATH"
download_job_diagnostics "$BASE_URL" "$first_job_id" "$FIRST_DIAGNOSTICS_PATH"

echo "First job summary:"
print_job_cache_summary_file "$FIRST_JOB_DETAIL_PATH"
echo "First job provider cache-hit events:"
print_provider_cache_hit_events_file "$FIRST_JOB_DETAIL_PATH"
echo "First job diagnostics summary:"
print_diagnostics_summary "$FIRST_DIAGNOSTICS_PATH"

echo "Uploading second compatible demo video for cache verification..."
second_upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
second_job_id="$(printf '%s' "$second_upload_response" | extract_json_field jobId)"
echo "Waiting for second job $second_job_id to complete..."
wait_for_job_status "$BASE_URL" "$second_job_id" COMPLETED >/dev/null
download_job_detail "$BASE_URL" "$second_job_id" "$SECOND_JOB_DETAIL_PATH"
download_job_diagnostics "$BASE_URL" "$second_job_id" "$SECOND_DIAGNOSTICS_PATH"

echo "Second job summary:"
print_job_cache_summary_file "$SECOND_JOB_DETAIL_PATH"
echo "Second job provider cache-hit events:"
print_provider_cache_hit_events_file "$SECOND_JOB_DETAIL_PATH"
echo "Second job diagnostics summary:"
print_diagnostics_summary "$SECOND_DIAGNOSTICS_PATH"

assert_provider_cache_hit_file "$SECOND_JOB_DETAIL_PATH"

echo "Cache-hit comparison:"
print_cache_hit_comparison "$FIRST_JOB_DETAIL_PATH" "$SECOND_JOB_DETAIL_PATH"
echo "Downloaded cache-hit evidence to $OUTPUT_DIR"
