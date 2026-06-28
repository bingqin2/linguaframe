#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

fail() {
  echo "[fail] $1" >&2
  exit 1
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  local label="$3"

  if [[ "$expected" != "$actual" ]]; then
    fail "$label: expected '$expected', got '$actual'"
  fi
}

fake_curl_bin() {
  local bin_path="$TMPDIR/fake-curl"
  cat >"$bin_path" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$@"
SH
  chmod +x "$bin_path"
  printf '%s' "$bin_path"
}

test_demo_curl_adds_token_header_when_configured() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_ACCESS_TOKEN="private-token" \
  LINGUAFRAME_DEMO_ACCESS_HEADER_NAME="X-Test-Demo-Token" \
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    demo_curl -fsS http://example.test/api/jobs >"$TMPDIR/demo-curl.out"

  local output
  output="$(cat "$TMPDIR/demo-curl.out")"

  [[ "$output" == *"-H"* ]] || fail "demo_curl did not add -H"
  [[ "$output" == *"X-Test-Demo-Token: private-token"* ]] || fail "demo_curl did not add configured token header"
  [[ "$output" == *"http://example.test/api/jobs"* ]] || fail "demo_curl did not preserve URL"
}

test_demo_curl_omits_token_header_when_not_configured() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_ACCESS_TOKEN="" \
  LINGUAFRAME_DEMO_ACCESS_HEADER_NAME="X-Test-Demo-Token" \
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    demo_curl -fsS http://example.test/api/jobs >"$TMPDIR/demo-curl-no-token.out"

  local output
  output="$(cat "$TMPDIR/demo-curl-no-token.out")"

  [[ "$output" != *"X-Test-Demo-Token"* ]] || fail "demo_curl added a token header without token"
}

test_demo_base_url_uses_backend_port_from_env_file() {
  local env_file="$TMPDIR/linguaframe-demo.env"
  printf 'LINGUAFRAME_BACKEND_PORT=18080\n' >"$env_file"

  local url
  url="$(LINGUAFRAME_ENV_FILE="$env_file" demo_base_url)"

  assert_equals "http://localhost:18080" "$url" "demo_base_url"
}

test_print_job_summary_includes_failure_triage() {
  cat >"$TMPDIR/job-triage.json" <<'JSON'
{
  "jobId": "job-triage",
  "videoId": "video-triage",
  "status": "FAILED",
  "retryCount": 0,
  "failureStage": "TARGET_SUBTITLE_EXPORT",
  "failureReason": "OpenAI request failed with status 401",
  "failureTriage": {
    "category": "OPENAI_AUTH_OR_MODEL",
    "summary": "OpenAI rejected the configured credentials or model.",
    "recommendedAction": "Run the OpenAI preflight before retrying.",
    "retryable": false,
    "runbookCommand": "scripts/demo/openai-demo-preflight.sh",
    "safeDetails": ["failureStage=TARGET_SUBTITLE_EXPORT"]
  },
  "pipelineProgress": {
    "totalStageCount": 10,
    "completedStageCount": 2,
    "failedStageCount": 1,
    "skippedStageCount": 0,
    "cacheHitStageCount": 0,
    "currentStage": "TARGET_SUBTITLE_EXPORT",
    "terminal": true,
    "totalMeasuredDurationMs": 1700,
    "slowestStage": "TARGET_SUBTITLE_EXPORT",
    "slowestStageDurationMs": 1500,
    "stages": []
  },
  "usageSummary": {
    "modelCallCount": 1,
    "failedModelCallCount": 1,
    "estimatedCostUsd": 0
  },
  "modelCalls": [],
  "timelineEvents": []
}
JSON

  print_job_summary <"$TMPDIR/job-triage.json" >"$TMPDIR/job-triage.out"
  local output
  output="$(cat "$TMPDIR/job-triage.out")"

  [[ "$output" == *"failureTriageCategory=OPENAI_AUTH_OR_MODEL"* ]] || fail "print_job_summary missed triage category"
  [[ "$output" == *"failureTriageRetryable=false"* ]] || fail "print_job_summary missed triage retryability"
  [[ "$output" == *"failureTriageRunbookCommand=scripts/demo/openai-demo-preflight.sh"* ]] || fail "print_job_summary missed triage runbook"
  [[ "$output" == *"pipelineCurrentStage=TARGET_SUBTITLE_EXPORT"* ]] || fail "print_job_summary missed pipeline current stage"
  [[ "$output" == *"pipelineTerminal=true"* ]] || fail "print_job_summary missed pipeline terminal state"
  [[ "$output" == *"pipelineCompletedStageCount=2"* ]] || fail "print_job_summary missed pipeline completed count"
  [[ "$output" == *"pipelineTotalMeasuredDurationMs=1700"* ]] || fail "print_job_summary missed pipeline measured duration"
  [[ "$output" == *"pipelineSlowestStage=TARGET_SUBTITLE_EXPORT"* ]] || fail "print_job_summary missed pipeline slowest stage"
}

test_print_diagnostics_summary_includes_failure_triage() {
  cat >"$TMPDIR/diagnostics-triage.json" <<'JSON'
{
  "generatedAt": "2026-06-28T08:00:00Z",
  "job": {
    "jobId": "job-triage",
    "status": "FAILED",
    "modelCalls": [],
    "timelineEvents": [],
    "failureTriage": {
      "category": "BUDGET_GUARD",
      "summary": "The configured cost budget stopped the job.",
      "recommendedAction": "Raise the relevant budget environment value.",
      "retryable": false,
      "runbookCommand": "scripts/demo/docker-e2e-success.sh",
      "safeDetails": []
    },
    "pipelineProgress": {
      "totalStageCount": 10,
      "completedStageCount": 3,
      "failedStageCount": 1,
      "skippedStageCount": 0,
      "cacheHitStageCount": 0,
      "currentStage": "DUBBING_AUDIO_GENERATION",
      "terminal": true,
      "totalMeasuredDurationMs": 2400,
      "slowestStage": "DUBBING_AUDIO_GENERATION",
      "slowestStageDurationMs": 1800,
      "stages": []
    }
  },
  "artifacts": [],
  "artifactCount": 0
}
JSON

  print_diagnostics_summary "$TMPDIR/diagnostics-triage.json" >"$TMPDIR/diagnostics-triage.out"
  local output
  output="$(cat "$TMPDIR/diagnostics-triage.out")"

  [[ "$output" == *"diagnosticsFailureTriageCategory=BUDGET_GUARD"* ]] || fail "diagnostics summary missed triage category"
  [[ "$output" == *"diagnosticsFailureTriageRetryable=false"* ]] || fail "diagnostics summary missed triage retryability"
  [[ "$output" == *"diagnosticsPipelineCurrentStage=DUBBING_AUDIO_GENERATION"* ]] || fail "diagnostics summary missed pipeline current stage"
  [[ "$output" == *"diagnosticsPipelineCompletedStageCount=3"* ]] || fail "diagnostics summary missed pipeline completed count"
  [[ "$output" == *"diagnosticsPipelineTotalMeasuredDurationMs=2400"* ]] || fail "diagnostics summary missed pipeline measured duration"
}

test_print_subtitle_review_summary_is_metadata_only() {
  cat >"$TMPDIR/subtitle-review.json" <<'JSON'
{
  "jobId": "job-review",
  "targetLanguage": "zh-CN",
  "segmentCount": 2,
  "missingTargetCount": 1,
  "timingMismatchCount": 1,
  "averageDurationMs": 1000,
  "maxDurationMs": 1400,
  "qualityScore": 88,
  "qualityVerdict": "NEEDS_REVIEW",
  "qualityIssueCount": 1,
  "qualitySuggestedFixCount": 1,
  "downloadableSubtitleArtifactCount": 3,
  "segments": [
    {
      "index": 0,
      "startMs": 0,
      "endMs": 1000,
      "sourceText": "raw transcript text",
      "targetText": "raw subtitle text",
      "durationMs": 1000,
      "timingDeltaMs": 0,
      "status": "ALIGNED"
    }
  ]
}
JSON

  print_subtitle_review_summary <"$TMPDIR/subtitle-review.json" >"$TMPDIR/subtitle-review.out"
  local output
  output="$(cat "$TMPDIR/subtitle-review.out")"

  [[ "$output" == *"subtitleReviewJobId=job-review"* ]] || fail "subtitle review summary missed job id"
  [[ "$output" == *"subtitleReviewLanguage=zh-CN"* ]] || fail "subtitle review summary missed language"
  [[ "$output" == *"subtitleReviewSegmentCount=2"* ]] || fail "subtitle review summary missed segment count"
  [[ "$output" == *"subtitleReviewMissingTargetCount=1"* ]] || fail "subtitle review summary missed missing count"
  [[ "$output" == *"subtitleReviewTimingMismatchCount=1"* ]] || fail "subtitle review summary missed timing count"
  [[ "$output" == *"subtitleReviewQuality=88 / 100, NEEDS_REVIEW"* ]] || fail "subtitle review summary missed quality"
  [[ "$output" == *"subtitleReviewSubtitleArtifactCount=3"* ]] || fail "subtitle review summary missed artifact count"
  [[ "$output" != *"raw transcript text"* ]] || fail "subtitle review summary exposed raw transcript text"
  [[ "$output" != *"raw subtitle text"* ]] || fail "subtitle review summary exposed raw subtitle text"
}

test_print_subtitle_draft_summary_is_metadata_only() {
  cat >"$TMPDIR/subtitle-draft.json" <<'JSON'
{
  "jobId": "job-draft",
  "targetLanguage": "zh-CN",
  "segmentCount": 2,
  "editedSegmentCount": 1,
  "lastUpdatedAt": "2026-06-28T09:15:00Z",
  "segments": [
    {
      "index": 0,
      "startMs": 0,
      "endMs": 1000,
      "sourceText": "raw transcript text",
      "generatedText": "raw generated subtitle",
      "draftText": "raw corrected subtitle",
      "edited": true,
      "updatedAt": "2026-06-28T09:15:00Z"
    }
  ]
}
JSON

  print_subtitle_draft_summary <"$TMPDIR/subtitle-draft.json" >"$TMPDIR/subtitle-draft.out"
  local output
  output="$(cat "$TMPDIR/subtitle-draft.out")"

  [[ "$output" == *"subtitleDraftJobId=job-draft"* ]] || fail "subtitle draft summary missed job id"
  [[ "$output" == *"subtitleDraftLanguage=zh-CN"* ]] || fail "subtitle draft summary missed language"
  [[ "$output" == *"subtitleDraftSegmentCount=2"* ]] || fail "subtitle draft summary missed segment count"
  [[ "$output" == *"subtitleDraftEditedSegmentCount=1"* ]] || fail "subtitle draft summary missed edited count"
  [[ "$output" == *"subtitleDraftLastUpdated=2026-06-28T09:15:00Z"* ]] || fail "subtitle draft summary missed last updated"
  [[ "$output" != *"raw transcript text"* ]] || fail "subtitle draft summary exposed raw transcript text"
  [[ "$output" != *"raw generated subtitle"* ]] || fail "subtitle draft summary exposed generated text"
  [[ "$output" != *"raw corrected subtitle"* ]] || fail "subtitle draft summary exposed corrected text"
}

test_print_reviewed_publish_summary_is_metadata_only() {
  cat >"$TMPDIR/reviewed-publish.json" <<'JSON'
{
  "jobId": "job-reviewed",
  "targetLanguage": "zh-CN",
  "burnedVideoRequested": true,
  "burnedVideoCreated": false,
  "artifacts": [
    {
      "artifactId": "reviewed-json",
      "jobId": "job-reviewed",
      "type": "REVIEWED_SUBTITLE_JSON",
      "filename": "reviewed-subtitles.zh-CN.json",
      "contentType": "application/json",
      "sizeBytes": 512,
      "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "cacheHit": false,
      "sourceArtifactId": null,
      "createdAt": "2026-06-28T09:15:00Z",
      "unsafeText": "raw corrected subtitle"
    },
    {
      "artifactId": "reviewed-srt",
      "jobId": "job-reviewed",
      "type": "REVIEWED_SUBTITLE_SRT",
      "filename": "reviewed-subtitles.zh-CN.srt",
      "contentType": "application/x-subrip",
      "sizeBytes": 256,
      "contentSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      "cacheHit": false,
      "sourceArtifactId": null,
      "createdAt": "2026-06-28T09:15:00Z"
    }
  ]
}
JSON

  print_reviewed_publish_summary <"$TMPDIR/reviewed-publish.json" >"$TMPDIR/reviewed-publish.out"
  local output
  output="$(cat "$TMPDIR/reviewed-publish.out")"

  [[ "$output" == *"reviewedPublishJobId=job-reviewed"* ]] || fail "reviewed publish summary missed job id"
  [[ "$output" == *"reviewedPublishLanguage=zh-CN"* ]] || fail "reviewed publish summary missed language"
  [[ "$output" == *"reviewedPublishArtifactCount=2"* ]] || fail "reviewed publish summary missed artifact count"
  [[ "$output" == *"reviewedPublishBurnedVideoRequested=true"* ]] || fail "reviewed publish summary missed burn request"
  [[ "$output" == *"reviewedPublishBurnedVideoCreated=false"* ]] || fail "reviewed publish summary missed burn result"
  [[ "$output" == *"reviewedPublishSubtitleArtifactCount=2"* ]] || fail "reviewed publish summary missed subtitle count"
  [[ "$output" == *"reviewedPublishBurnedVideoArtifactCount=0"* ]] || fail "reviewed publish summary missed burned video count"
  [[ "$output" == *"reviewedPublishArtifact=REVIEWED_SUBTITLE_SRT:reviewed-subtitles.zh-CN.srt"* ]] || fail "reviewed publish summary missed reviewed artifact"
  [[ "$output" != *"raw corrected subtitle"* ]] || fail "reviewed publish summary exposed corrected text"
}

test_print_delivery_manifest_summary_is_metadata_only() {
  cat >"$TMPDIR/delivery-manifest.json" <<'JSON'
{
  "jobId": "job-manifest",
  "videoId": "video-manifest",
  "targetLanguage": "zh-CN",
  "status": "COMPLETED",
  "generatedAt": "2026-06-28T11:00:00Z",
  "handoffReady": true,
  "reviewedSubtitleArtifactCount": 3,
  "reviewedBurnedVideoAvailable": false,
  "generatedArtifactCount": 9,
  "reviewedArtifacts": [
    {
      "artifactId": "reviewed-srt",
      "type": "REVIEWED_SUBTITLE_SRT",
      "filename": "reviewed-subtitles.zh-CN.srt",
      "contentType": "application/x-subrip",
      "sizeBytes": 256,
      "shortSha256": "abcdef012345",
      "cacheState": "Generated",
      "role": "REVIEWED_HANDOFF",
      "downloadUrl": "/api/jobs/job-manifest/artifacts/reviewed-srt/download",
      "unsafeText": "raw corrected subtitle"
    }
  ],
  "auditArtifacts": [],
  "links": [
    {
      "label": "Result bundle",
      "kind": "RESULT_BUNDLE",
      "url": "/api/jobs/job-manifest/artifacts/archive/download"
    },
    {
      "label": "Evidence bundle",
      "kind": "EVIDENCE_BUNDLE",
      "url": "/api/jobs/job-manifest/evidence/bundle/download"
    }
  ],
  "unsafeTranscript": "raw transcript text",
  "unsafeSubtitle": "raw generated subtitle",
  "unsafeObjectKey": "job-artifacts/job-manifest/reviewed-srt",
  "unsafePath": "/Users/example/private.mov",
  "unsafeProviderPayload": "provider payload",
  "unsafeToken": "OPENAI_API_KEY"
}
JSON

  print_delivery_manifest_summary <"$TMPDIR/delivery-manifest.json" >"$TMPDIR/delivery-manifest.out"
  local output
  output="$(cat "$TMPDIR/delivery-manifest.out")"

  [[ "$output" == *"deliveryManifestJobId=job-manifest"* ]] || fail "delivery manifest summary missed job id"
  [[ "$output" == *"deliveryManifestHandoffReady=true"* ]] || fail "delivery manifest summary missed ready state"
  [[ "$output" == *"deliveryManifestReviewedSubtitleArtifactCount=3"* ]] || fail "delivery manifest summary missed reviewed subtitle count"
  [[ "$output" == *"deliveryManifestReviewedBurnedVideoAvailable=false"* ]] || fail "delivery manifest summary missed reviewed video availability"
  [[ "$output" == *"deliveryManifestGeneratedArtifactCount=9"* ]] || fail "delivery manifest summary missed generated artifact count"
  [[ "$output" == *"deliveryManifestLinkCount=2"* ]] || fail "delivery manifest summary missed link count"
  [[ "$output" == *"deliveryManifestReviewedArtifact=REVIEWED_SUBTITLE_SRT:reviewed-subtitles.zh-CN.srt"* ]] || fail "delivery manifest summary missed reviewed artifact"
  [[ "$output" != *"raw transcript text"* ]] || fail "delivery manifest summary exposed transcript"
  [[ "$output" != *"raw generated subtitle"* ]] || fail "delivery manifest summary exposed generated subtitle"
  [[ "$output" != *"raw corrected subtitle"* ]] || fail "delivery manifest summary exposed corrected subtitle"
  [[ "$output" != *"job-artifacts/job-manifest"* ]] || fail "delivery manifest summary exposed object key"
  [[ "$output" != *"/Users/example/private.mov"* ]] || fail "delivery manifest summary exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "delivery manifest summary exposed provider payload"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "delivery manifest summary exposed token"
}

test_print_media_delivery_summary_is_metadata_only() {
  cat >"$TMPDIR/media-artifacts.json" <<'JSON'
[
  {
    "artifactId": "dubbing-audio",
    "jobId": "job-media",
    "type": "DUBBING_AUDIO",
    "filename": "dubbing-audio.mp3",
    "contentType": "audio/mpeg",
    "sizeBytes": 4200,
    "contentSha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
    "cacheHit": false,
    "sourceArtifactId": null,
    "createdAt": "2026-06-28T11:20:00Z",
    "unsafeText": "raw transcript text"
  },
  {
    "artifactId": "burned-video",
    "jobId": "job-media",
    "type": "BURNED_VIDEO",
    "filename": "burned-video.mp4",
    "contentType": "video/mp4",
    "sizeBytes": 42000,
    "contentSha256": "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
    "cacheHit": true,
    "sourceArtifactId": "job-artifacts/private/burned-video.mp4",
    "createdAt": "2026-06-28T11:21:00Z",
    "unsafePath": "/Users/example/private.mov"
  },
  {
    "artifactId": "reviewed-video",
    "jobId": "job-media",
    "type": "REVIEWED_BURNED_VIDEO",
    "filename": "reviewed-burned-video.mp4",
    "contentType": "video/mp4",
    "sizeBytes": 41000,
    "contentSha256": "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
    "cacheHit": false,
    "sourceArtifactId": null,
    "createdAt": "2026-06-28T11:22:00Z",
    "unsafeToken": "OPENAI_API_KEY"
  },
  {
    "artifactId": "subtitle",
    "jobId": "job-media",
    "type": "TARGET_SUBTITLE_SRT",
    "filename": "target-subtitles.srt",
    "contentType": "application/x-subrip",
    "sizeBytes": 256,
    "contentSha256": "0000000000000000000000000000000000000000000000000000000000000000",
    "cacheHit": false,
    "sourceArtifactId": null,
    "createdAt": "2026-06-28T11:23:00Z",
    "unsafeText": "raw subtitle text"
  }
]
JSON

  print_media_delivery_summary <"$TMPDIR/media-artifacts.json" >"$TMPDIR/media-artifacts.out"
  local output
  output="$(cat "$TMPDIR/media-artifacts.out")"

  [[ "$output" == *"mediaDeliveryReadyCount=3"* ]] || fail "media delivery summary missed ready count"
  [[ "$output" == *"mediaDeliveryArtifact=DUBBING_AUDIO:dubbing-audio.mp3:audio/mpeg:Generated"* ]] || fail "media delivery summary missed audio artifact"
  [[ "$output" == *"mediaDeliveryArtifact=BURNED_VIDEO:burned-video.mp4:video/mp4:Reused"* ]] || fail "media delivery summary missed generated video artifact"
  [[ "$output" == *"mediaDeliveryArtifact=REVIEWED_BURNED_VIDEO:reviewed-burned-video.mp4:video/mp4:Generated"* ]] || fail "media delivery summary missed reviewed video artifact"
  [[ "$output" != *"raw transcript text"* ]] || fail "media delivery summary exposed transcript"
  [[ "$output" != *"raw subtitle text"* ]] || fail "media delivery summary exposed subtitle"
  [[ "$output" != *"job-artifacts/private"* ]] || fail "media delivery summary exposed object key"
  [[ "$output" != *"/Users/example/private.mov"* ]] || fail "media delivery summary exposed local path"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "media delivery summary exposed token"
}

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file
test_print_job_summary_includes_failure_triage
test_print_diagnostics_summary_includes_failure_triage
test_print_subtitle_review_summary_is_metadata_only
test_print_subtitle_draft_summary_is_metadata_only
test_print_reviewed_publish_summary_is_metadata_only
test_print_delivery_manifest_summary_is_metadata_only
test_print_media_delivery_summary_is_metadata_only

echo "linguaframe-demo client tests passed"
