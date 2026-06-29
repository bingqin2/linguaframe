#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

export LINGUAFRAME_ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.openai-demo}"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/openai-smoke.mp4}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo/openai-smoke}"

fail() {
  echo "[fail] $1" >&2
  exit 1
}

if [[ ! -r "$SAMPLE_PATH" || ! -s "$SAMPLE_PATH" ]]; then
  fail "Set LINGUAFRAME_DEMO_SAMPLE_PATH to a readable short speech MP4. OpenAI smoke does not generate a tone-only sample."
fi

"$SCRIPT_DIR/openai-demo-preflight.sh"

mkdir -p "$OUTPUT_DIR"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded OpenAI smoke video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED 120 2)"
job_detail_path="$OUTPUT_DIR/job-detail.json"
printf '%s' "$job_response" >"$job_detail_path"
printf '%s' "$job_response" | print_job_summary
video_id="$(printf '%s' "$job_response" | extract_json_field videoId)"
fetch_source_media_metadata "$BASE_URL" "$video_id" >"$OUTPUT_DIR/source-media.json"
print_source_media_summary "$OUTPUT_DIR/source-media.json" "$job_id"
download_source_media "$BASE_URL" "$video_id" "$OUTPUT_DIR/source-video.mp4"
print_quality_evaluation_summary_file "$job_detail_path"

python3 - "$job_detail_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
calls = job.get("modelCalls") or []
required = {"TRANSCRIPTION", "TRANSLATION"}
seen = {
    call.get("operation")
    for call in calls
    if call.get("provider") == "OPENAI" and call.get("status") == "SUCCEEDED"
}
missing = sorted(required - seen)
if missing:
    raise SystemExit("Missing successful OpenAI model calls: " + ", ".join(missing))

evaluation = job.get("qualityEvaluation")
if evaluation:
    print("qualityScore=" + str(evaluation.get("score")))
    print("qualityVerdict=" + str(evaluation.get("verdict")))
    print("qualityStatus=" + str(evaluation.get("status")))
else:
    print("qualityEvaluation=none")
PY

echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
printf '%s' "$artifacts_response" >"$OUTPUT_DIR/artifacts.json"
printf '%s' "$artifacts_response" | print_artifact_summary

get_job_transcript "$BASE_URL" "$job_id" >"$OUTPUT_DIR/transcript-preview.json"
get_job_subtitles "$BASE_URL" "$job_id" "zh-CN" >"$OUTPUT_DIR/target-subtitle-preview.json"

download_artifact_by_type "$BASE_URL" "$job_id" EXTRACTED_AUDIO "$OUTPUT_DIR/audio.wav"
download_artifact_by_type "$BASE_URL" "$job_id" TRANSCRIPT_JSON "$OUTPUT_DIR/transcript.json"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_SRT "$OUTPUT_DIR/subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_VTT "$OUTPUT_DIR/subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_JSON "$OUTPUT_DIR/target-subtitles.json"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_SRT "$OUTPUT_DIR/target-subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_VTT "$OUTPUT_DIR/target-subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" BURNED_VIDEO "$OUTPUT_DIR/burned-video.mp4"
download_artifact_archive "$BASE_URL" "$job_id" "$OUTPUT_DIR/artifacts.zip"
download_job_diagnostics "$BASE_URL" "$job_id" "$OUTPUT_DIR/job-diagnostics.json"
download_job_evidence_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/job-evidence.md"
if ! download_quality_evaluation_evidence_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/quality-evidence.md"; then
  write_quality_evaluation_evidence_markdown "$job_detail_path" "$OUTPUT_DIR/quality-evidence.md"
fi
download_job_evidence_bundle "$BASE_URL" "$job_id" "$OUTPUT_DIR/job-evidence.zip"
download_demo_run_package "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-run-package.zip"
download_ai_audit_package "$BASE_URL" "$job_id" "$OUTPUT_DIR/ai-audit-package.zip"
download_openai_smoke_proof_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/openai-smoke-proof.json"
download_openai_smoke_proof_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/openai-smoke-proof.md"
download_demo_reviewer_workspace_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.json"
download_demo_reviewer_workspace_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.md"
download_demo_reviewer_workspace_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
download_demo_handoff_portal_json "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.json"
download_demo_handoff_portal_markdown "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.md"
download_demo_handoff_portal_zip "$BASE_URL" "$job_id" "$OUTPUT_DIR/demo-handoff-portal.zip"
download_artifact_by_type "$BASE_URL" "$job_id" WORKER_SUMMARY "$OUTPUT_DIR/worker-summary.json"

if [[ "$(env_value LINGUAFRAME_TTS_ENABLED false)" == "true" ]]; then
  download_artifact_by_type "$BASE_URL" "$job_id" DUBBING_AUDIO "$OUTPUT_DIR/dubbing-audio.mp3"
  echo "Downloaded OpenAI TTS dubbing audio"
  if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBED_VIDEO "$OUTPUT_DIR/dubbed-video.mp4"; then
    echo "Downloaded OpenAI dubbed video"
  else
    echo "No dubbed video artifact found; subtitle burn-in or audio replacement may be disabled"
  fi
else
  echo "TTS is disabled by the OpenAI smoke profile; no dubbing audio or dubbed video expected"
fi

print_zip_entries "$OUTPUT_DIR/artifacts.zip"
print_diagnostics_summary "$OUTPUT_DIR/job-diagnostics.json"
print_evidence_markdown_summary "$OUTPUT_DIR/job-evidence.md" "$job_id"
print_quality_evidence_markdown_summary "$OUTPUT_DIR/quality-evidence.md" "$job_id"
print_evidence_bundle_summary "$OUTPUT_DIR/job-evidence.zip" "$job_id"
print_demo_run_package_summary "$OUTPUT_DIR/demo-run-package.zip" "$job_id"
print_ai_audit_package_summary "$OUTPUT_DIR/ai-audit-package.zip" "$job_id"
print_demo_reviewer_workspace_summary_file "$OUTPUT_DIR/demo-reviewer-workspace.json" "$OUTPUT_DIR/demo-reviewer-workspace.md" "$OUTPUT_DIR/demo-reviewer-workspace.zip"
print_demo_handoff_portal_summary_file "$OUTPUT_DIR/demo-handoff-portal.json" "$OUTPUT_DIR/demo-handoff-portal.md" "$OUTPUT_DIR/demo-handoff-portal.zip"
python3 - "$OUTPUT_DIR/openai-smoke-proof.json" "$OUTPUT_DIR/openai-smoke-proof.md" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
proof = json.loads(json_path.read_text(encoding="utf-8"))
required = proof.get("requiredChecks") or []

print("openAiSmokeProofStatus=" + str(proof.get("overallStatus")))
print("openAiSmokeProofPhase=" + str(proof.get("phase")))
print("openAiSmokeProofRequiredBlockedCount=" + str(sum(1 for check in required if check.get("status") == "BLOCKED")))
print("openAiSmokeProofJsonPath=" + str(json_path))
print("openAiSmokeProofMarkdownPath=" + str(markdown_path))
PY
print_source_media_summary "$OUTPUT_DIR/source-media.json" "$job_id"

cat <<EOF

OpenAI smoke demo completed.

Job:
  $job_id

Evidence directory:
  $OUTPUT_DIR

Browser follow-up:
  1. Open the frontend.
  2. Start owner session if the private demo gate is enabled.
  3. Open job id $job_id.
  4. Inspect Model calls, Quality evaluation, Result delivery, and Demo evidence.
EOF
