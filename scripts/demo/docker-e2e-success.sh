#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/sample.mp4}"
ARTIFACT_PATH="${LINGUAFRAME_DEMO_ARTIFACT_PATH:-/tmp/linguaframe-demo/worker-summary.json}"
AUDIO_PATH="${LINGUAFRAME_DEMO_AUDIO_PATH:-/tmp/linguaframe-demo/audio.wav}"

wait_for_backend "$BASE_URL"
create_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$job_response" | print_job_summary

echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
printf '%s' "$artifacts_response" | print_artifact_summary
download_artifact_by_type "$BASE_URL" "$job_id" EXTRACTED_AUDIO "$AUDIO_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" WORKER_SUMMARY "$ARTIFACT_PATH"
echo "Downloaded extracted audio to $AUDIO_PATH"
echo "Downloaded worker summary to $ARTIFACT_PATH"
