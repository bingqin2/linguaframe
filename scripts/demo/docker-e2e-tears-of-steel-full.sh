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
  LINGUAFRAME_RENDER_NARRATION_DEMO         Default: false
  LINGUAFRAME_RENDER_CUSTOM_NARRATION       Default: false
  LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET   Default: false
  LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED
                                              Default for full render: true
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
APPLY_NARRATION_DEMO_PRESET="${LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET:-false}"
RENDER_NARRATION_DEMO="${LINGUAFRAME_RENDER_NARRATION_DEMO:-false}"
RENDER_CUSTOM_NARRATION="${LINGUAFRAME_RENDER_CUSTOM_NARRATION:-false}"

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

echo "Final proof bundle:"
download_openai_smoke_proof_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/openai-smoke-proof.json"
download_openai_smoke_proof_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/openai-smoke-proof.md"
download_ai_audit_package "$BASE_URL" "$job_id" "$OUTPUT_DIR/ai-audit-package.zip"
write_demo_evidence_closure_request "$OUTPUT_DIR/demo-evidence-closure-request.json"
download_demo_evidence_closure_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-evidence-closure-request.json" "$OUTPUT_DIR/demo-evidence-closure.json"
download_demo_evidence_closure_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-evidence-closure-request.json" "$OUTPUT_DIR/demo-evidence-closure.md"
download_demo_evidence_closure_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-evidence-closure-request.json" "$OUTPUT_DIR/demo-evidence-closure.zip"
print_ai_audit_package_summary "$OUTPUT_DIR/ai-audit-package.zip" "$job_id"
echo "Downloaded final proof bundle to $OUTPUT_DIR/openai-smoke-proof.json, $OUTPUT_DIR/openai-smoke-proof.md, $OUTPUT_DIR/ai-audit-package.zip, $OUTPUT_DIR/demo-evidence-closure.json, $OUTPUT_DIR/demo-evidence-closure.md, and $OUTPUT_DIR/demo-evidence-closure.zip"

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

if [[ "$RENDER_NARRATION_DEMO" == "true" ]]; then
  echo "Narration demo render:"
  LINGUAFRAME_DEMO_JOB_ID="$job_id" \
    LINGUAFRAME_NARRATION_DEMO_PRESET_PROFILE_ID="${LINGUAFRAME_NARRATION_DEMO_PRESET_PROFILE_ID:-${LINGUAFRAME_DEMO_PROFILE_ID:-tears-showcase}}" \
    LINGUAFRAME_NARRATION_DEMO_RENDER_OUTPUT_DIR="$OUTPUT_DIR/narration-demo-render" \
    LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED="${LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED:-true}" \
    "$SCRIPT_DIR/narration-demo-render.sh"
elif [[ "$APPLY_NARRATION_DEMO_PRESET" == "true" ]]; then
  echo "Narration demo preset:"
  LINGUAFRAME_DEMO_JOB_ID="$job_id" \
    LINGUAFRAME_NARRATION_DEMO_PRESET_PROFILE_ID="${LINGUAFRAME_NARRATION_DEMO_PRESET_PROFILE_ID:-${LINGUAFRAME_DEMO_PROFILE_ID:-tears-showcase}}" \
    LINGUAFRAME_NARRATION_DEMO_PRESET_OUTPUT_DIR="$OUTPUT_DIR/narration-demo-preset" \
    "$SCRIPT_DIR/narration-demo-preset.sh"
else
  echo "Skipping narration demo render. Set LINGUAFRAME_RENDER_NARRATION_DEMO=true to apply the preset, generate narration audio/video, and export refreshed evidence."
  echo "Run scripts/demo/narration-demo-render-preflight.sh with LINGUAFRAME_DEMO_JOB_ID=$job_id to inspect the read-only preflight first."
  echo "Set LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true for the apply-only workflow."
fi

echo "Narration studio:"
download_narration_studio_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-studio.json"
print_narration_studio_summary_file "$OUTPUT_DIR/narration-studio.json"
echo "Downloaded narration studio to $OUTPUT_DIR/narration-studio.json"

if [[ "$RENDER_CUSTOM_NARRATION" == "true" ]]; then
  echo "Custom narration render:"
  LINGUAFRAME_DEMO_JOB_ID="$job_id" \
    LINGUAFRAME_CUSTOM_NARRATION_RENDER_OUTPUT_DIR="$OUTPUT_DIR/custom-narration-render" \
    "$SCRIPT_DIR/custom-narration-render.sh"
  cp "$OUTPUT_DIR/custom-narration-render/custom-narration-render.md" "$OUTPUT_DIR/custom-narration-render.md"
  echo "Custom narration render report exported to $OUTPUT_DIR/custom-narration-render.md"
  download_narration_studio_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-studio.json"
  print_narration_studio_summary_file "$OUTPUT_DIR/narration-studio.json"
  echo "Refreshed narration studio after custom render at $OUTPUT_DIR/narration-studio.json"
else
  echo "Skipping custom narration render. Set LINGUAFRAME_RENDER_CUSTOM_NARRATION=true to render saved upload-seeded or manually edited narration rows."
  echo "Run scripts/demo/custom-narration-render.sh with LINGUAFRAME_DEMO_JOB_ID=$job_id to preflight and render the saved custom workspace."
fi

echo "Narration evidence:"
download_narration_evidence_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-evidence.json"
narration_status="$(python3 - "$OUTPUT_DIR/narration-evidence.json" <<'PY'
import json
import sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", ""))
PY
)"
if [[ "$narration_status" == "BLOCKED" ]]; then
  echo "Narration evidence is BLOCKED; no narration segments have been saved for this run."
else
  if [[ "${LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO:-false}" == "true" ]]; then
    narration_audio_ready="$(python3 - "$OUTPUT_DIR/narration-evidence.json" <<'PY'
import json
import sys
print(str(json.load(open(sys.argv[1], encoding="utf-8")).get("narrationAudioReady", False)).lower())
PY
)"
    if [[ "$narration_audio_ready" == "true" ]]; then
      generate_narrated_video_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/narrated-video-generation.json"
      print_narrated_video_generation_summary_file "$OUTPUT_DIR/narrated-video-generation.json"
      download_narration_evidence_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-evidence.json"
    else
      echo "Skipping narrated video generation because narration audio is not ready."
    fi
  fi
  download_narration_evidence_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-evidence.md"
  download_narration_evidence_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/narration-evidence.zip"
  print_narration_evidence_summary_file "$OUTPUT_DIR/narration-evidence.json" "$OUTPUT_DIR/narration-evidence.md" "$OUTPUT_DIR/narration-evidence.zip"
  echo "Downloaded narration evidence to $OUTPUT_DIR/narration-evidence.json, $OUTPUT_DIR/narration-evidence.md, and $OUTPUT_DIR/narration-evidence.zip"
fi

echo "Narration render review:"
LINGUAFRAME_DEMO_JOB_ID="$job_id" \
  LINGUAFRAME_NARRATION_RENDER_REVIEW_OUTPUT_DIR="$OUTPUT_DIR/narration-render-review" \
  LINGUAFRAME_NARRATION_RENDER_REVIEW_REPORT_ONLY="${LINGUAFRAME_NARRATION_RENDER_REVIEW_REPORT_ONLY:-true}" \
  "$SCRIPT_DIR/narration-render-review.sh"

echo "Narration playback review:"
LINGUAFRAME_DEMO_JOB_ID="$job_id" \
  LINGUAFRAME_NARRATION_PLAYBACK_REVIEW_OUTPUT_DIR="$OUTPUT_DIR/narration-playback-review" \
  LINGUAFRAME_NARRATION_PLAYBACK_REVIEW_REPORT_ONLY="${LINGUAFRAME_NARRATION_PLAYBACK_REVIEW_REPORT_ONLY:-true}" \
  "$SCRIPT_DIR/narration-playback-review.sh"

echo "Narration playback resolution:"
LINGUAFRAME_DEMO_JOB_ID="$job_id" \
  LINGUAFRAME_NARRATION_PLAYBACK_RESOLUTION_OUTPUT_DIR="$OUTPUT_DIR/narration-playback-resolution" \
  LINGUAFRAME_NARRATION_PLAYBACK_RESOLUTION_REPORT_ONLY="${LINGUAFRAME_NARRATION_PLAYBACK_RESOLUTION_REPORT_ONLY:-true}" \
  "$SCRIPT_DIR/narration-playback-review-resolution.sh"

echo "Narration recovery handoff:"
LINGUAFRAME_DEMO_JOB_ID="$job_id" \
  LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_OUTPUT_DIR="$OUTPUT_DIR/narration-recovery-handoff" \
  LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY="${LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY:-true}" \
  "$SCRIPT_DIR/narration-recovery-handoff.sh"

echo "Narration delivery package:"
LINGUAFRAME_DEMO_JOB_ID="$job_id" \
  LINGUAFRAME_NARRATION_DELIVERY_PACKAGE_OUTPUT_DIR="$OUTPUT_DIR/narration-delivery-package" \
  LINGUAFRAME_NARRATION_DELIVERY_PACKAGE_REPORT_ONLY="${LINGUAFRAME_NARRATION_DELIVERY_PACKAGE_REPORT_ONLY:-true}" \
  "$SCRIPT_DIR/narration-delivery-package.sh"

echo "Refreshing final demo handoff packages with narration delivery status:"
download_demo_acceptance_gate_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-acceptance-gate.json"
print_demo_acceptance_gate_summary_file "$OUTPUT_DIR/demo-acceptance-gate.json"
download_demo_reviewer_workspace_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.json"
download_demo_reviewer_workspace_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.md"
download_demo_reviewer_workspace_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
print_demo_reviewer_workspace_summary_file "$OUTPUT_DIR/demo-reviewer-workspace.json" "$OUTPUT_DIR/demo-reviewer-workspace.md" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
download_demo_handoff_portal_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.json"
download_demo_handoff_portal_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.md"
download_demo_handoff_portal_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.zip"
print_demo_handoff_portal_summary_file "$OUTPUT_DIR/demo-handoff-portal.json" "$OUTPUT_DIR/demo-handoff-portal.md" "$OUTPUT_DIR/demo-handoff-portal.zip"

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
