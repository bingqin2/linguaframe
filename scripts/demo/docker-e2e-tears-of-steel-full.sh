#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/demo/docker-e2e-tears-of-steel-full.sh

Runs a full-video LinguaFrame demo using the local Tears of Steel sample.

Environment:
  LINGUAFRAME_TEARS_SAMPLE_PATH             Default: /Users/wangbingqin/Downloads/tos_casting-720p.mp4
  LINGUAFRAME_DEMO_BASE_URL                 Default: http://localhost:8080
  LINGUAFRAME_FULL_DEMO_OUTPUT_DIR          Default: /tmp/linguaframe-demo/tears-of-steel-full
  LINGUAFRAME_FULL_DEMO_WAIT_ATTEMPTS       Default: 240
  LINGUAFRAME_FULL_DEMO_WAIT_DELAY_SECONDS  Default: 5
  LINGUAFRAME_COMPARISON_BASELINE_JOB_ID    Optional completed baseline job to compare against
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_TEARS_SAMPLE_PATH:-/Users/wangbingqin/Downloads/tos_casting-720p.mp4}"
OUTPUT_DIR="${LINGUAFRAME_FULL_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo/tears-of-steel-full}"
WAIT_ATTEMPTS="${LINGUAFRAME_FULL_DEMO_WAIT_ATTEMPTS:-240}"
WAIT_DELAY_SECONDS="${LINGUAFRAME_FULL_DEMO_WAIT_DELAY_SECONDS:-5}"
COMPARISON_BASELINE_JOB_ID="${LINGUAFRAME_COMPARISON_BASELINE_JOB_ID:-}"

if [[ ! -s "$SAMPLE_PATH" ]]; then
  echo "Missing Tears of Steel sample: $SAMPLE_PATH" >&2
  echo "Download the sample locally, then rerun with LINGUAFRAME_TEARS_SAMPLE_PATH=/absolute/path/to/video.mp4 if needed." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "Demo media: Tears of Steel / Casting the Actors"
echo "Source: https://studio.blender.org/films/tears-of-steel/"
echo "License reference: https://mango.blender.org/about/"
echo "Input: $SAMPLE_PATH"
echo "Output directory: $OUTPUT_DIR"

wait_for_backend "$BASE_URL"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded full demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED "$WAIT_ATTEMPTS" "$WAIT_DELAY_SECONDS")"
printf '%s' "$job_response" | print_job_summary

echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
printf '%s' "$artifacts_response" | print_artifact_summary

download_artifact_by_type "$BASE_URL" "$job_id" EXTRACTED_AUDIO "$OUTPUT_DIR/audio.wav"
download_artifact_by_type "$BASE_URL" "$job_id" TRANSCRIPT_JSON "$OUTPUT_DIR/transcript.json"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_SRT "$OUTPUT_DIR/subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_VTT "$OUTPUT_DIR/subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_JSON "$OUTPUT_DIR/target-subtitles.json"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_SRT "$OUTPUT_DIR/target-subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_VTT "$OUTPUT_DIR/target-subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" WORKER_SUMMARY "$OUTPUT_DIR/worker-summary.json"
download_artifact_archive "$BASE_URL" "$job_id" "$OUTPUT_DIR/artifacts.zip"
print_zip_entries "$OUTPUT_DIR/artifacts.zip"

echo "Demo run matrix for source video:"
download_demo_run_matrix_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-matrix.json"
print_demo_run_matrix_summary_file "$OUTPUT_DIR/demo-run-matrix.json"
echo "Downloaded demo run matrix to $OUTPUT_DIR/demo-run-matrix.json"

echo "Demo presenter pack:"
download_demo_presenter_pack_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-presenter-pack.json"
print_demo_presenter_pack_summary_file "$OUTPUT_DIR/demo-presenter-pack.json"
echo "Downloaded demo presenter pack to $OUTPUT_DIR/demo-presenter-pack.json"

if [[ -n "$COMPARISON_BASELINE_JOB_ID" ]]; then
  echo "Demo profile comparison against baseline $COMPARISON_BASELINE_JOB_ID:"
  download_job_comparison_json "$BASE_URL" "$COMPARISON_BASELINE_JOB_ID" "$job_id" "$OUTPUT_DIR/job-comparison.json"
  download_job_comparison_markdown "$BASE_URL" "$COMPARISON_BASELINE_JOB_ID" "$job_id" "$OUTPUT_DIR/job-comparison.md"
  print_job_comparison_summary_file "$OUTPUT_DIR/job-comparison.json"
  echo "Downloaded comparison evidence to $OUTPUT_DIR/job-comparison.json and $OUTPUT_DIR/job-comparison.md"
fi

if download_optional_artifact_by_type "$BASE_URL" "$job_id" BURNED_VIDEO "$OUTPUT_DIR/burned-video.mp4"; then
  echo "Downloaded burned video to $OUTPUT_DIR/burned-video.mp4"
else
  echo "No burned video artifact found; full-video burn-in may be disabled or skipped."
fi

if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBING_AUDIO "$OUTPUT_DIR/dubbing-audio.mp3"; then
  echo "Downloaded dubbing audio to $OUTPUT_DIR/dubbing-audio.mp3"
else
  echo "No dubbing audio artifact found; TTS may be disabled."
fi

echo "Downloaded artifact archive to $OUTPUT_DIR/artifacts.zip"
echo "Downloaded full-video demo artifacts to $OUTPUT_DIR"
