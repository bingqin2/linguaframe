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
DUBBED_VIDEO_PATH="${LINGUAFRAME_DEMO_DUBBED_VIDEO_PATH:-/tmp/linguaframe-demo/dubbed-video.mp4}"
ARCHIVE_PATH="${LINGUAFRAME_DEMO_ARCHIVE_PATH:-/tmp/linguaframe-demo/artifacts.zip}"
DIAGNOSTICS_PATH="${LINGUAFRAME_DEMO_DIAGNOSTICS_PATH:-/tmp/linguaframe-demo/job-diagnostics.json}"
EVIDENCE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_EVIDENCE_MARKDOWN_PATH:-/tmp/linguaframe-demo/job-evidence.md}"
QUALITY_EVIDENCE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_QUALITY_EVIDENCE_MARKDOWN_PATH:-/tmp/linguaframe-demo/quality-evidence.md}"
EVIDENCE_BUNDLE_PATH="${LINGUAFRAME_DEMO_EVIDENCE_BUNDLE_PATH:-/tmp/linguaframe-demo/job-evidence.zip}"
HANDOFF_PACKAGE_PATH="${LINGUAFRAME_DEMO_HANDOFF_PACKAGE_PATH:-/tmp/linguaframe-demo/handoff-package.zip}"
DEMO_RUN_PACKAGE_PATH="${LINGUAFRAME_DEMO_RUN_PACKAGE_PATH:-/tmp/linguaframe-demo/demo-run-package.zip}"
AI_AUDIT_PACKAGE_PATH="${LINGUAFRAME_DEMO_AI_AUDIT_PACKAGE_PATH:-/tmp/linguaframe-demo/ai-audit-package.zip}"
DEMO_REVIEWER_WORKSPACE_JSON_PATH="${LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_JSON_PATH:-/tmp/linguaframe-demo/demo-reviewer-workspace.json}"
DEMO_REVIEWER_WORKSPACE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_MARKDOWN_PATH:-/tmp/linguaframe-demo/demo-reviewer-workspace.md}"
DEMO_REVIEWER_WORKSPACE_ZIP_PATH="${LINGUAFRAME_DEMO_REVIEWER_WORKSPACE_ZIP_PATH:-/tmp/linguaframe-demo/demo-reviewer-workspace.zip}"
DEMO_HANDOFF_PORTAL_JSON_PATH="${LINGUAFRAME_DEMO_HANDOFF_PORTAL_JSON_PATH:-/tmp/linguaframe-demo/demo-handoff-portal.json}"
DEMO_HANDOFF_PORTAL_MARKDOWN_PATH="${LINGUAFRAME_DEMO_HANDOFF_PORTAL_MARKDOWN_PATH:-/tmp/linguaframe-demo/demo-handoff-portal.md}"
DEMO_HANDOFF_PORTAL_ZIP_PATH="${LINGUAFRAME_DEMO_HANDOFF_PORTAL_ZIP_PATH:-/tmp/linguaframe-demo/demo-handoff-portal.zip}"
SUBTITLE_REVIEW_EVIDENCE_JSON_PATH="${LINGUAFRAME_DEMO_SUBTITLE_REVIEW_EVIDENCE_JSON_PATH:-/tmp/linguaframe-demo/subtitle-review-evidence.json}"
SUBTITLE_REVIEW_EVIDENCE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_SUBTITLE_REVIEW_EVIDENCE_MARKDOWN_PATH:-/tmp/linguaframe-demo/subtitle-review-evidence.md}"
SUBTITLE_REVIEW_EVIDENCE_ZIP_PATH="${LINGUAFRAME_DEMO_SUBTITLE_REVIEW_EVIDENCE_ZIP_PATH:-/tmp/linguaframe-demo/subtitle-review-evidence.zip}"
NARRATION_EVIDENCE_JSON_PATH="${LINGUAFRAME_DEMO_NARRATION_EVIDENCE_JSON_PATH:-/tmp/linguaframe-demo/narration-evidence.json}"
NARRATION_EVIDENCE_MARKDOWN_PATH="${LINGUAFRAME_DEMO_NARRATION_EVIDENCE_MARKDOWN_PATH:-/tmp/linguaframe-demo/narration-evidence.md}"
NARRATION_EVIDENCE_ZIP_PATH="${LINGUAFRAME_DEMO_NARRATION_EVIDENCE_ZIP_PATH:-/tmp/linguaframe-demo/narration-evidence.zip}"
SOURCE_MEDIA_METADATA_PATH="${LINGUAFRAME_DEMO_SOURCE_MEDIA_METADATA_PATH:-/tmp/linguaframe-demo/source-media.json}"
SOURCE_MEDIA_PATH="${LINGUAFRAME_DEMO_SOURCE_MEDIA_PATH:-/tmp/linguaframe-demo/source-video.mp4}"
DELIVERY_MANIFEST_MARKDOWN_PATH="${LINGUAFRAME_DEMO_DELIVERY_MANIFEST_MARKDOWN_PATH:-/tmp/linguaframe-demo/delivery-manifest.md}"
DELIVERY_MANIFEST_JSON_PATH="${LINGUAFRAME_DEMO_DELIVERY_MANIFEST_JSON_PATH:-/tmp/linguaframe-demo/delivery-manifest.json}"
ARTIFACTS_JSON_PATH="${LINGUAFRAME_DEMO_ARTIFACTS_JSON_PATH:-/tmp/linguaframe-demo/artifacts.json}"
JOB_DETAIL_PATH="${LINGUAFRAME_DEMO_JOB_DETAIL_PATH:-/tmp/linguaframe-demo/job-detail.json}"
DEMO_SESSION_REPORT_PATH="${LINGUAFRAME_DEMO_SESSION_REPORT_PATH:-/tmp/linguaframe-demo/demo-session-report.md}"

wait_for_backend "$BASE_URL"
ensure_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
video_id="$(printf '%s' "$job_response" | extract_json_field videoId)"
mkdir -p "$(dirname "$JOB_DETAIL_PATH")"
printf '%s' "$job_response" >"$JOB_DETAIL_PATH"
printf '%s' "$job_response" | print_job_summary
fetch_source_media_metadata "$BASE_URL" "$video_id" >"$SOURCE_MEDIA_METADATA_PATH"
print_source_media_summary "$SOURCE_MEDIA_METADATA_PATH" "$job_id"
download_source_media "$BASE_URL" "$video_id" "$SOURCE_MEDIA_PATH"
echo "Quality evaluation summary for job $job_id:"
print_quality_evaluation_summary_file "$JOB_DETAIL_PATH"

echo "Subtitle draft summary for job $job_id:"
get_job_subtitle_draft "$BASE_URL" "$job_id" "zh-CN" | print_subtitle_draft_summary
echo "Publishing reviewed subtitle artifacts for job $job_id:"
publish_reviewed_subtitles "$BASE_URL" "$job_id" "zh-CN" "false" | print_reviewed_publish_summary
echo "Delivery manifest summary for job $job_id:"
delivery_manifest_response="$(get_delivery_manifest "$BASE_URL" "$job_id")"
mkdir -p "$(dirname "$DELIVERY_MANIFEST_JSON_PATH")"
printf '%s' "$delivery_manifest_response" >"$DELIVERY_MANIFEST_JSON_PATH"
printf '%s' "$delivery_manifest_response" | print_delivery_manifest_summary
echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
mkdir -p "$(dirname "$ARTIFACTS_JSON_PATH")"
printf '%s' "$artifacts_response" >"$ARTIFACTS_JSON_PATH"
printf '%s' "$artifacts_response" | print_artifact_summary
echo "Media delivery summary for job $job_id:"
printf '%s' "$artifacts_response" | print_media_delivery_summary
echo "Demo handoff checklist for job $job_id:"
print_demo_handoff_checklist_summary "$JOB_DETAIL_PATH" "$DELIVERY_MANIFEST_JSON_PATH" "$ARTIFACTS_JSON_PATH"
write_demo_session_report_markdown \
  "$JOB_DETAIL_PATH" \
  "$DELIVERY_MANIFEST_JSON_PATH" \
  "$ARTIFACTS_JSON_PATH" \
  "$DEMO_SESSION_REPORT_PATH"
echo "Wrote demo session report to $DEMO_SESSION_REPORT_PATH"
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
if download_quality_evaluation_evidence_markdown "$BASE_URL" "$job_id" "$QUALITY_EVIDENCE_MARKDOWN_PATH"; then
  print_quality_evidence_markdown_summary "$QUALITY_EVIDENCE_MARKDOWN_PATH" "$job_id"
else
  write_quality_evaluation_evidence_markdown "$JOB_DETAIL_PATH" "$QUALITY_EVIDENCE_MARKDOWN_PATH"
  print_quality_evidence_markdown_summary "$QUALITY_EVIDENCE_MARKDOWN_PATH" "$job_id"
fi
download_job_evidence_bundle "$BASE_URL" "$job_id" "$EVIDENCE_BUNDLE_PATH"
print_evidence_bundle_summary "$EVIDENCE_BUNDLE_PATH" "$job_id"
download_delivery_manifest_markdown "$BASE_URL" "$job_id" "$DELIVERY_MANIFEST_MARKDOWN_PATH"
download_handoff_package "$BASE_URL" "$job_id" "$HANDOFF_PACKAGE_PATH"
print_handoff_package_summary "$HANDOFF_PACKAGE_PATH" "$job_id"
download_demo_run_package "$BASE_URL" "$job_id" "$DEMO_RUN_PACKAGE_PATH"
print_demo_run_package_summary "$DEMO_RUN_PACKAGE_PATH" "$job_id"
download_ai_audit_package "$BASE_URL" "$job_id" "$AI_AUDIT_PACKAGE_PATH"
print_ai_audit_package_summary "$AI_AUDIT_PACKAGE_PATH" "$job_id"
download_demo_reviewer_workspace_json "$BASE_URL" "$job_id" "$DEMO_REVIEWER_WORKSPACE_JSON_PATH"
download_demo_reviewer_workspace_markdown "$BASE_URL" "$job_id" "$DEMO_REVIEWER_WORKSPACE_MARKDOWN_PATH"
download_demo_reviewer_workspace_zip "$BASE_URL" "$job_id" "$DEMO_REVIEWER_WORKSPACE_ZIP_PATH"
print_demo_reviewer_workspace_summary_file "$DEMO_REVIEWER_WORKSPACE_JSON_PATH" "$DEMO_REVIEWER_WORKSPACE_MARKDOWN_PATH" "$DEMO_REVIEWER_WORKSPACE_ZIP_PATH"
download_demo_handoff_portal_json "$BASE_URL" "$job_id" "$DEMO_HANDOFF_PORTAL_JSON_PATH"
download_demo_handoff_portal_markdown "$BASE_URL" "$job_id" "$DEMO_HANDOFF_PORTAL_MARKDOWN_PATH"
download_demo_handoff_portal_zip "$BASE_URL" "$job_id" "$DEMO_HANDOFF_PORTAL_ZIP_PATH"
print_demo_handoff_portal_summary_file "$DEMO_HANDOFF_PORTAL_JSON_PATH" "$DEMO_HANDOFF_PORTAL_MARKDOWN_PATH" "$DEMO_HANDOFF_PORTAL_ZIP_PATH"
download_subtitle_review_evidence_json "$BASE_URL" "$job_id" "$SUBTITLE_REVIEW_EVIDENCE_JSON_PATH"
download_subtitle_review_evidence_markdown "$BASE_URL" "$job_id" "$SUBTITLE_REVIEW_EVIDENCE_MARKDOWN_PATH"
download_subtitle_review_evidence_zip "$BASE_URL" "$job_id" "$SUBTITLE_REVIEW_EVIDENCE_ZIP_PATH"
print_subtitle_review_evidence_summary_file "$SUBTITLE_REVIEW_EVIDENCE_JSON_PATH" "$SUBTITLE_REVIEW_EVIDENCE_MARKDOWN_PATH" "$SUBTITLE_REVIEW_EVIDENCE_ZIP_PATH"
download_narration_evidence_json "$BASE_URL" "$job_id" "$NARRATION_EVIDENCE_JSON_PATH"
narration_status="$(printf '%s' "$(cat "$NARRATION_EVIDENCE_JSON_PATH")" | extract_json_field status)"
if [[ "$narration_status" == "BLOCKED" ]]; then
  echo "Narration evidence is BLOCKED; no narration segments have been saved for this run."
else
  if [[ "${LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO:-false}" == "true" ]]; then
    narration_audio_ready="$(printf '%s' "$(cat "$NARRATION_EVIDENCE_JSON_PATH")" | extract_json_field narrationAudioReady)"
    if [[ "$narration_audio_ready" == "true" ]]; then
      generate_narrated_video_json "$BASE_URL" "$job_id" "/tmp/linguaframe-demo/narrated-video-generation.json"
      download_narration_evidence_json "$BASE_URL" "$job_id" "$NARRATION_EVIDENCE_JSON_PATH"
    else
      echo "Skipping narrated video generation because narration audio is not ready."
    fi
  fi
  download_narration_evidence_markdown "$BASE_URL" "$job_id" "$NARRATION_EVIDENCE_MARKDOWN_PATH"
  download_narration_evidence_zip "$BASE_URL" "$job_id" "$NARRATION_EVIDENCE_ZIP_PATH"
  print_narration_evidence_summary_file "$NARRATION_EVIDENCE_JSON_PATH" "$NARRATION_EVIDENCE_MARKDOWN_PATH" "$NARRATION_EVIDENCE_ZIP_PATH"
fi
if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBING_AUDIO "$DUBBING_AUDIO_PATH"; then
  echo "Downloaded dubbing audio to $DUBBING_AUDIO_PATH"
else
  echo "No dubbing audio artifact found; TTS may be disabled"
fi
if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBED_VIDEO "$DUBBED_VIDEO_PATH"; then
  echo "Downloaded dubbed video to $DUBBED_VIDEO_PATH"
else
  echo "No dubbed video artifact found; TTS or subtitle burn-in may be disabled"
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
echo "Downloaded quality evidence markdown to $QUALITY_EVIDENCE_MARKDOWN_PATH"
echo "Downloaded evidence bundle to $EVIDENCE_BUNDLE_PATH"
echo "Downloaded delivery manifest markdown to $DELIVERY_MANIFEST_MARKDOWN_PATH"
echo "Downloaded handoff package to $HANDOFF_PACKAGE_PATH"
echo "Downloaded demo run package to $DEMO_RUN_PACKAGE_PATH"
echo "Downloaded AI audit package to $AI_AUDIT_PACKAGE_PATH"
echo "Downloaded demo reviewer workspace JSON to $DEMO_REVIEWER_WORKSPACE_JSON_PATH"
echo "Downloaded demo reviewer workspace Markdown to $DEMO_REVIEWER_WORKSPACE_MARKDOWN_PATH"
echo "Downloaded demo reviewer workspace ZIP to $DEMO_REVIEWER_WORKSPACE_ZIP_PATH"
echo "Downloaded demo handoff portal JSON to $DEMO_HANDOFF_PORTAL_JSON_PATH"
echo "Downloaded demo handoff portal Markdown to $DEMO_HANDOFF_PORTAL_MARKDOWN_PATH"
echo "Downloaded demo handoff portal ZIP to $DEMO_HANDOFF_PORTAL_ZIP_PATH"
echo "Downloaded subtitle review evidence JSON to $SUBTITLE_REVIEW_EVIDENCE_JSON_PATH"
echo "Downloaded subtitle review evidence Markdown to $SUBTITLE_REVIEW_EVIDENCE_MARKDOWN_PATH"
echo "Downloaded subtitle review evidence ZIP to $SUBTITLE_REVIEW_EVIDENCE_ZIP_PATH"
echo "Checked narration evidence JSON at $NARRATION_EVIDENCE_JSON_PATH"
echo "Downloaded source video to $SOURCE_MEDIA_PATH"
echo "Downloaded worker summary to $ARTIFACT_PATH"
