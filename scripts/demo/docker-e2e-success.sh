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
TRANSCRIPT_PATH="${LINGUAFRAME_DEMO_TRANSCRIPT_PATH:-/tmp/linguaframe-demo/transcript.json}"
SRT_PATH="${LINGUAFRAME_DEMO_SRT_PATH:-/tmp/linguaframe-demo/subtitles.srt}"
VTT_PATH="${LINGUAFRAME_DEMO_VTT_PATH:-/tmp/linguaframe-demo/subtitles.vtt}"
TARGET_SUBTITLE_JSON_PATH="${LINGUAFRAME_DEMO_TARGET_SUBTITLE_JSON_PATH:-/tmp/linguaframe-demo/target-subtitles.json}"
TARGET_SRT_PATH="${LINGUAFRAME_DEMO_TARGET_SRT_PATH:-/tmp/linguaframe-demo/target-subtitles.srt}"
TARGET_VTT_PATH="${LINGUAFRAME_DEMO_TARGET_VTT_PATH:-/tmp/linguaframe-demo/target-subtitles.vtt}"
DUBBING_AUDIO_PATH="${LINGUAFRAME_DEMO_DUBBING_AUDIO_PATH:-/tmp/linguaframe-demo/dubbing-audio.mp3}"
BURNED_VIDEO_PATH="${LINGUAFRAME_DEMO_BURNED_VIDEO_PATH:-/tmp/linguaframe-demo/burned-video.mp4}"
ARCHIVE_PATH="${LINGUAFRAME_DEMO_ARCHIVE_PATH:-/tmp/linguaframe-demo/artifacts.zip}"
DIAGNOSTICS_PATH="${LINGUAFRAME_DEMO_DIAGNOSTICS_PATH:-/tmp/linguaframe-demo/job-diagnostics.json}"
EVIDENCE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_EVIDENCE_MARKDOWN_PATH:-/tmp/linguaframe-demo/job-evidence.md}"
EVIDENCE_BUNDLE_PATH="${LINGUAFRAME_DEMO_EVIDENCE_BUNDLE_PATH:-/tmp/linguaframe-demo/job-evidence.zip}"
DELIVERY_MANIFEST_MARKDOWN_PATH="${LINGUAFRAME_DEMO_DELIVERY_MANIFEST_MARKDOWN_PATH:-/tmp/linguaframe-demo/delivery-manifest.md}"

wait_for_backend "$BASE_URL"
ensure_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$job_response" | print_job_summary

echo "Subtitle draft summary for job $job_id:"
get_job_subtitle_draft "$BASE_URL" "$job_id" "zh-CN" | print_subtitle_draft_summary
echo "Publishing reviewed subtitle artifacts for job $job_id:"
publish_reviewed_subtitles "$BASE_URL" "$job_id" "zh-CN" "false" | print_reviewed_publish_summary
echo "Delivery manifest summary for job $job_id:"
get_delivery_manifest "$BASE_URL" "$job_id" | print_delivery_manifest_summary
echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
printf '%s' "$artifacts_response" | print_artifact_summary
echo "Transcript preview for job $job_id:"
get_job_transcript "$BASE_URL" "$job_id" | python3 -m json.tool
echo "Target subtitle preview for job $job_id:"
get_job_subtitles "$BASE_URL" "$job_id" "zh-CN" | python3 -m json.tool
echo "Subtitle review summary for job $job_id:"
get_job_subtitle_review "$BASE_URL" "$job_id" "zh-CN" | print_subtitle_review_summary
download_artifact_by_type "$BASE_URL" "$job_id" EXTRACTED_AUDIO "$AUDIO_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TRANSCRIPT_JSON "$TRANSCRIPT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_SRT "$SRT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_VTT "$VTT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_JSON "$TARGET_SUBTITLE_JSON_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_SRT "$TARGET_SRT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_VTT "$TARGET_VTT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" BURNED_VIDEO "$BURNED_VIDEO_PATH"
download_artifact_archive "$BASE_URL" "$job_id" "$ARCHIVE_PATH"
print_zip_entries "$ARCHIVE_PATH"
download_job_diagnostics "$BASE_URL" "$job_id" "$DIAGNOSTICS_PATH"
print_diagnostics_summary "$DIAGNOSTICS_PATH"
download_job_evidence_markdown "$BASE_URL" "$job_id" "$EVIDENCE_MARKDOWN_PATH"
print_evidence_markdown_summary "$EVIDENCE_MARKDOWN_PATH" "$job_id"
download_job_evidence_bundle "$BASE_URL" "$job_id" "$EVIDENCE_BUNDLE_PATH"
print_evidence_bundle_summary "$EVIDENCE_BUNDLE_PATH" "$job_id"
download_delivery_manifest_markdown "$BASE_URL" "$job_id" "$DELIVERY_MANIFEST_MARKDOWN_PATH"
if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBING_AUDIO "$DUBBING_AUDIO_PATH"; then
  echo "Downloaded dubbing audio to $DUBBING_AUDIO_PATH"
else
  echo "No dubbing audio artifact found; TTS may be disabled"
fi
download_artifact_by_type "$BASE_URL" "$job_id" WORKER_SUMMARY "$ARTIFACT_PATH"
echo "Downloaded extracted audio to $AUDIO_PATH"
echo "Downloaded transcript JSON to $TRANSCRIPT_PATH"
echo "Downloaded SRT subtitles to $SRT_PATH"
echo "Downloaded VTT subtitles to $VTT_PATH"
echo "Downloaded target subtitle JSON to $TARGET_SUBTITLE_JSON_PATH"
echo "Downloaded target SRT subtitles to $TARGET_SRT_PATH"
echo "Downloaded target VTT subtitles to $TARGET_VTT_PATH"
echo "Downloaded burned video to $BURNED_VIDEO_PATH"
echo "Downloaded artifact archive to $ARCHIVE_PATH"
echo "Downloaded diagnostics report to $DIAGNOSTICS_PATH"
echo "Downloaded evidence markdown to $EVIDENCE_MARKDOWN_PATH"
echo "Downloaded evidence bundle to $EVIDENCE_BUNDLE_PATH"
echo "Downloaded delivery manifest markdown to $DELIVERY_MANIFEST_MARKDOWN_PATH"
echo "Downloaded worker summary to $ARTIFACT_PATH"
