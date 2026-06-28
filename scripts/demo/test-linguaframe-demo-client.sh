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

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file
test_print_job_summary_includes_failure_triage
test_print_diagnostics_summary_includes_failure_triage
test_print_subtitle_review_summary_is_metadata_only
test_print_subtitle_draft_summary_is_metadata_only

echo "linguaframe-demo client tests passed"
