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

echo "Demo replay card:"
download_demo_replay_card_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-replay-card.json"
print_demo_replay_card_summary_file "$OUTPUT_DIR/demo-replay-card.json"
echo "Downloaded demo replay card to $OUTPUT_DIR/demo-replay-card.json"

echo "Demo completion certificate:"
download_demo_completion_certificate_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-completion-certificate.json"
print_demo_completion_certificate_summary_file "$OUTPUT_DIR/demo-completion-certificate.json"
echo "Downloaded demo completion certificate to $OUTPUT_DIR/demo-completion-certificate.json"

echo "Demo acceptance gate:"
download_demo_acceptance_gate_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-acceptance-gate.json"
print_demo_acceptance_gate_summary_file "$OUTPUT_DIR/demo-acceptance-gate.json"
echo "Downloaded demo acceptance gate to $OUTPUT_DIR/demo-acceptance-gate.json"

echo "Demo run monitor:"
download_demo_run_monitor_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-monitor.json"
download_demo_run_monitor_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-monitor.md"
print_demo_run_monitor_summary_file "$OUTPUT_DIR/demo-run-monitor.json"
echo "Downloaded demo run monitor to $OUTPUT_DIR/demo-run-monitor.json and $OUTPUT_DIR/demo-run-monitor.md"

echo "Demo share sheet:"
download_demo_share_sheet_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-share-sheet.json"
download_demo_share_sheet_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-share-sheet.md"
print_demo_share_sheet_summary_file "$OUTPUT_DIR/demo-share-sheet.json"
echo "Downloaded demo share sheet to $OUTPUT_DIR/demo-share-sheet.json and $OUTPUT_DIR/demo-share-sheet.md"

echo "Demo run snapshot:"
download_demo_run_snapshot_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-snapshot.json"
download_demo_run_snapshot_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-snapshot.zip"
print_demo_run_snapshot_summary_file "$OUTPUT_DIR/demo-run-snapshot.json"
print_demo_run_snapshot_package_summary "$OUTPUT_DIR/demo-run-snapshot.zip" "$job_id"
echo "Downloaded demo run snapshot to $OUTPUT_DIR/demo-run-snapshot.json and $OUTPUT_DIR/demo-run-snapshot.zip"

echo "Demo reviewer workspace:"
download_demo_reviewer_workspace_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.json"
download_demo_reviewer_workspace_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.md"
download_demo_reviewer_workspace_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
print_demo_reviewer_workspace_summary_file "$OUTPUT_DIR/demo-reviewer-workspace.json" "$OUTPUT_DIR/demo-reviewer-workspace.md" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
echo "Downloaded demo reviewer workspace to $OUTPUT_DIR/demo-reviewer-workspace.json, $OUTPUT_DIR/demo-reviewer-workspace.md, and $OUTPUT_DIR/demo-reviewer-workspace.zip"

echo "Demo handoff portal:"
download_demo_handoff_portal_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.json"
download_demo_handoff_portal_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.md"
download_demo_handoff_portal_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.zip"
print_demo_handoff_portal_summary_file "$OUTPUT_DIR/demo-handoff-portal.json" "$OUTPUT_DIR/demo-handoff-portal.md" "$OUTPUT_DIR/demo-handoff-portal.zip"
echo "Downloaded demo handoff portal to $OUTPUT_DIR/demo-handoff-portal.json, $OUTPUT_DIR/demo-handoff-portal.md, and $OUTPUT_DIR/demo-handoff-portal.zip"

echo "Subtitle review evidence:"
download_subtitle_review_evidence_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/subtitle-review-evidence.json"
download_subtitle_review_evidence_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/subtitle-review-evidence.md"
download_subtitle_review_evidence_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/subtitle-review-evidence.zip"
print_subtitle_review_evidence_summary_file "$OUTPUT_DIR/subtitle-review-evidence.json" "$OUTPUT_DIR/subtitle-review-evidence.md" "$OUTPUT_DIR/subtitle-review-evidence.zip"
echo "Downloaded subtitle review evidence to $OUTPUT_DIR/subtitle-review-evidence.json, $OUTPUT_DIR/subtitle-review-evidence.md, and $OUTPUT_DIR/subtitle-review-evidence.zip"

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

if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBED_VIDEO "$OUTPUT_DIR/dubbed-video.mp4"; then
  echo "Downloaded dubbed video to $OUTPUT_DIR/dubbed-video.mp4"
else
  echo "No dubbed video artifact found; TTS or subtitle burn-in may be disabled."
fi

echo "Downloaded artifact archive to $OUTPUT_DIR/artifacts.zip"
echo "Downloaded full-video demo artifacts to $OUTPUT_DIR"
