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

test_upload_demo_video_includes_subtitle_polishing_mode() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"
  local sample_path="$TMPDIR/sample.mp4"
  printf 'demo' >"$sample_path"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE="BALANCED" \
    upload_demo_video "http://example.test" "$sample_path" >"$TMPDIR/upload-demo.out"

  local output
  output="$(cat "$TMPDIR/upload-demo.out")"
  [[ "$output" == *"subtitlePolishingMode=BALANCED"* ]] || fail "upload_demo_video missed subtitle polishing mode"
}

test_upload_demo_video_applies_tears_showcase_profile() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"
  local sample_path="$TMPDIR/sample.mp4"
  printf 'demo' >"$sample_path"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_PROFILE_ID="tears-showcase" \
    upload_demo_video "http://example.test" "$sample_path" >"$TMPDIR/upload-demo-profile.out"

  local output
  output="$(cat "$TMPDIR/upload-demo-profile.out")"
  [[ "$output" == *"demoProfileId=tears-showcase"* ]] || fail "upload_demo_video missed demo profile id"
  [[ "$output" == *"translationStyle=FORMAL"* ]] || fail "upload_demo_video missed profile translation style"
  [[ "$output" == *"subtitleStylePreset=HIGH_CONTRAST"* ]] || fail "upload_demo_video missed profile subtitle style"
  [[ "$output" == *"subtitlePolishingMode=BALANCED"* ]] || fail "upload_demo_video missed profile polishing mode"
  [[ "$output" == *"Tears of Steel"* ]] || fail "upload_demo_video missed profile glossary"
}

test_upload_demo_video_explicit_env_overrides_profile() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"
  local sample_path="$TMPDIR/sample.mp4"
  printf 'demo' >"$sample_path"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_PROFILE_ID="tears-showcase" \
  LINGUAFRAME_DEMO_TRANSLATION_STYLE="CONCISE" \
    upload_demo_video "http://example.test" "$sample_path" >"$TMPDIR/upload-demo-profile-override.out"

  local output
  output="$(cat "$TMPDIR/upload-demo-profile-override.out")"
  [[ "$output" == *"translationStyle=CONCISE"* ]] || fail "upload_demo_video did not let explicit style override profile"
}

test_download_job_comparison_helpers_use_backend_routes() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_job_comparison_json "http://example.test" "baseline job" "comparison/job" "$TMPDIR/comparison.json" >"$TMPDIR/comparison-json-curl.out"
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_job_comparison_markdown "http://example.test" "baseline job" "comparison/job" "$TMPDIR/comparison.md" >"$TMPDIR/comparison-markdown-curl.out"

  local json_output markdown_output
  json_output="$(cat "$TMPDIR/comparison-json-curl.out")"
  markdown_output="$(cat "$TMPDIR/comparison-markdown-curl.out")"

  [[ "$json_output" == *"http://example.test/api/jobs/baseline%20job/comparison/comparison%2Fjob"* ]] || fail "comparison JSON helper used wrong route"
  [[ "$markdown_output" == *"http://example.test/api/jobs/baseline%20job/comparison/comparison%2Fjob/markdown/download"* ]] || fail "comparison Markdown helper used wrong route"
}

test_print_job_comparison_summary_is_metadata_only() {
  cat >"$TMPDIR/job-comparison.json" <<'JSON'
{
  "baselineJobId": "baseline-job",
  "comparisonJobId": "showcase-job",
  "sameSourceVideo": true,
  "baseline": {
    "jobId": "baseline-job",
    "videoId": "video-demo",
    "targetLanguage": "zh-CN",
    "demoProfileId": "quick-baseline",
    "status": "COMPLETED",
    "qualityScore": 80,
    "modelCallCount": 2,
    "estimatedCostUsd": 0.000012,
    "artifactCacheHitCount": 0,
    "providerCacheHitCount": 0,
    "generatedArtifactCount": 8,
    "handoffReady": true
  },
  "comparison": {
    "jobId": "showcase-job",
    "videoId": "video-demo",
    "targetLanguage": "zh-CN",
    "demoProfileId": "tears-showcase",
    "status": "COMPLETED",
    "qualityScore": 91,
    "modelCallCount": 3,
    "estimatedCostUsd": 0.000090,
    "artifactCacheHitCount": 2,
    "providerCacheHitCount": 1,
    "generatedArtifactCount": 9,
    "handoffReady": true
  },
  "delta": {
    "qualityScore": 11,
    "modelCallCount": 1,
    "estimatedCostUsd": 0.000078,
    "artifactCacheHitCount": 2,
    "providerCacheHitCount": 1,
    "generatedArtifactCount": 1
  },
  "settingDiffs": [
    {
      "field": "demoProfileId",
      "baselineValue": "quick-baseline",
      "comparisonValue": "tears-showcase"
    },
    {
      "field": "translationGlossary",
      "baselineValue": "",
      "comparisonValue": "raw glossary text /Users/example/private.mov sk-test"
    }
  ]
}
JSON

  print_job_comparison_summary_file "$TMPDIR/job-comparison.json" >"$TMPDIR/job-comparison.out"
  local output
  output="$(cat "$TMPDIR/job-comparison.out")"

  [[ "$output" == *"comparisonBaselineJobId=baseline-job"* ]] || fail "comparison summary missed baseline job"
  [[ "$output" == *"comparisonJobId=showcase-job"* ]] || fail "comparison summary missed comparison job"
  [[ "$output" == *"comparisonSameSourceVideo=true"* ]] || fail "comparison summary missed source match"
  [[ "$output" == *"comparisonBaselineProfile=quick-baseline"* ]] || fail "comparison summary missed baseline profile"
  [[ "$output" == *"comparisonProfile=tears-showcase"* ]] || fail "comparison summary missed comparison profile"
  [[ "$output" == *"comparisonQualityDelta=11"* ]] || fail "comparison summary missed quality delta"
  [[ "$output" == *"comparisonModelCallDelta=1"* ]] || fail "comparison summary missed model-call delta"
  [[ "$output" == *"comparisonEstimatedCostDeltaUsd=0.000078"* ]] || fail "comparison summary missed cost delta"
  [[ "$output" == *"comparisonSettingDiff=demoProfileId:quick-baseline->tears-showcase"* ]] || fail "comparison summary missed setting diff"
  [[ "$output" == *"comparisonSettingDiff=translationGlossary:[empty]->[present]"* ]] || fail "comparison summary did not redact glossary values"
  [[ "$output" != *"raw glossary text"* ]] || fail "comparison summary exposed glossary text"
  [[ "$output" != *"/Users/example"* ]] || fail "comparison summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "comparison summary exposed token"
}

test_download_demo_run_matrix_helper_uses_backend_route() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_matrix_json "http://example.test" "matrix job/slash" "$TMPDIR/demo-run-matrix.json" >"$TMPDIR/matrix-curl.out"

  local output
  output="$(cat "$TMPDIR/matrix-curl.out")"
  [[ "$output" == *"http://example.test/api/jobs/matrix%20job%2Fslash/demo-run-matrix"* ]] || fail "demo run matrix helper used wrong route"
}

test_print_demo_run_matrix_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-run-matrix.json" <<'JSON'
{
  "anchorJobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-28T12:00:00Z",
  "recommendedBaselineJobId": "baseline-job",
  "bestQualityJobId": "showcase-job",
  "lowestCostJobId": "baseline-job",
  "jobs": [
    {
      "jobId": "showcase-job",
      "videoId": "video-demo",
      "filename": "tears.mp4",
      "targetLanguage": "zh-CN",
      "demoProfileId": "tears-showcase",
      "translationStyle": "FORMAL",
      "subtitleStylePreset": "HIGH_CONTRAST",
      "translationGlossaryEntryCount": 3,
      "translationGlossaryHash": "abc123",
      "subtitlePolishingMode": "BALANCED",
      "status": "COMPLETED",
      "qualityScore": 91,
      "qualityVerdict": "GOOD",
      "modelCallCount": 3,
      "failedModelCallCount": 0,
      "estimatedCostUsd": 0.000090,
      "artifactCacheHitCount": 2,
      "generatedArtifactCount": 9,
      "providerCacheHitCount": 1,
      "handoffReady": true,
      "failureReason": "raw transcript text /Users/example/private.mov sk-test provider payload"
    },
    {
      "jobId": "baseline-job",
      "videoId": "video-demo",
      "filename": "tears.mp4",
      "targetLanguage": "zh-CN",
      "demoProfileId": "quick-baseline",
      "translationStyle": "NATURAL",
      "subtitleStylePreset": "STANDARD",
      "translationGlossaryEntryCount": 0,
      "translationGlossaryHash": "",
      "subtitlePolishingMode": "OFF",
      "status": "COMPLETED",
      "qualityScore": 80,
      "qualityVerdict": "GOOD",
      "modelCallCount": 2,
      "failedModelCallCount": 0,
      "estimatedCostUsd": 0.000012,
      "artifactCacheHitCount": 0,
      "generatedArtifactCount": 8,
      "providerCacheHitCount": 0,
      "handoffReady": true
    }
  ]
}
JSON

  print_demo_run_matrix_summary_file "$TMPDIR/demo-run-matrix.json" >"$TMPDIR/demo-run-matrix.out"
  local output
  output="$(cat "$TMPDIR/demo-run-matrix.out")"

  [[ "$output" == *"demoRunMatrixAnchorJobId=showcase-job"* ]] || fail "matrix summary missed anchor"
  [[ "$output" == *"demoRunMatrixVideoId=video-demo"* ]] || fail "matrix summary missed video"
  [[ "$output" == *"demoRunMatrixRunCount=2"* ]] || fail "matrix summary missed run count"
  [[ "$output" == *"demoRunMatrixRecommendedBaselineJobId=baseline-job"* ]] || fail "matrix summary missed baseline"
  [[ "$output" == *"demoRunMatrixBestQualityJobId=showcase-job"* ]] || fail "matrix summary missed best quality"
  [[ "$output" == *"demoRunMatrixLowestCostJobId=baseline-job"* ]] || fail "matrix summary missed lowest cost"
  [[ "$output" == *"demoRunMatrixRun=showcase-job:tears-showcase:COMPLETED:quality=91:cost=0.000090:modelCalls=3:providerCacheHits=1:handoffReady=true"* ]] || fail "matrix summary missed showcase row"
  [[ "$output" != *"raw transcript text"* ]] || fail "matrix summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "matrix summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "matrix summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "matrix summary exposed provider payload"
}

test_download_demo_presenter_pack_helper_uses_backend_route() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_presenter_pack_json "http://example.test" "presenter job/slash" "$TMPDIR/demo-presenter-pack.json" >"$TMPDIR/presenter-pack-curl.out"

  local output
  output="$(cat "$TMPDIR/presenter-pack-curl.out")"
  [[ "$output" == *"http://example.test/api/jobs/presenter%20job%2Fslash/demo-presenter-pack"* ]] || fail "demo presenter pack helper used wrong route"
}

test_print_demo_presenter_pack_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-presenter-pack.json" <<'JSON'
{
  "anchorJobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-28T12:00:00Z",
  "headline": "tears-showcase demo to zh-CN",
  "readinessStatus": "READY",
  "recommendedBaselineJobId": "baseline-job",
  "bestQualityJobId": "showcase-job",
  "lowestCostJobId": "baseline-job",
  "runs": [
    {
      "jobId": "showcase-job",
      "demoProfileId": "tears-showcase",
      "status": "COMPLETED",
      "completedAt": "2026-06-28T11:03:00Z",
      "qualityScore": 91,
      "estimatedCostUsd": 0.000090,
      "modelCallCount": 3,
      "providerCacheHitCount": 1,
      "handoffReady": true,
      "roles": ["ANCHOR", "BEST_QUALITY"]
    },
    {
      "jobId": "baseline-job",
      "demoProfileId": "quick-baseline",
      "status": "COMPLETED",
      "completedAt": "2026-06-28T10:03:00Z",
      "qualityScore": 80,
      "estimatedCostUsd": 0.000012,
      "modelCallCount": 2,
      "providerCacheHitCount": 0,
      "handoffReady": true,
      "roles": ["RECOMMENDED_BASELINE", "LOWEST_COST"]
    }
  ],
  "downloads": [
    {
      "kind": "DEMO_RUN_PACKAGE",
      "label": "Demo run package",
      "url": "/api/jobs/showcase-job/demo-run-package/download"
    }
  ],
  "presenterNotesMarkdown": "raw transcript text /Users/example/private.mov sk-test provider payload"
}
JSON

  print_demo_presenter_pack_summary_file "$TMPDIR/demo-presenter-pack.json" >"$TMPDIR/demo-presenter-pack.out"
  local output
  output="$(cat "$TMPDIR/demo-presenter-pack.out")"

  [[ "$output" == *"demoPresenterPackAnchorJobId=showcase-job"* ]] || fail "presenter pack summary missed anchor"
  [[ "$output" == *"demoPresenterPackVideoId=video-demo"* ]] || fail "presenter pack summary missed video"
  [[ "$output" == *"demoPresenterPackReadiness=READY"* ]] || fail "presenter pack summary missed readiness"
  [[ "$output" == *"demoPresenterPackRunCount=2"* ]] || fail "presenter pack summary missed run count"
  [[ "$output" == *"demoPresenterPackRecommendedBaselineJobId=baseline-job"* ]] || fail "presenter pack summary missed baseline"
  [[ "$output" == *"demoPresenterPackBestQualityJobId=showcase-job"* ]] || fail "presenter pack summary missed best quality"
  [[ "$output" == *"demoPresenterPackLowestCostJobId=baseline-job"* ]] || fail "presenter pack summary missed lowest cost"
  [[ "$output" == *"demoPresenterPackRun=showcase-job:tears-showcase:COMPLETED:roles=ANCHOR,BEST_QUALITY:quality=91:cost=0.000090:modelCalls=3:providerCacheHits=1:handoffReady=true"* ]] || fail "presenter pack summary missed showcase row"
  [[ "$output" == *"demoPresenterPackDownload=DEMO_RUN_PACKAGE:/api/jobs/showcase-job/demo-run-package/download"* ]] || fail "presenter pack summary missed download"
  [[ "$output" != *"raw transcript text"* ]] || fail "presenter pack summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "presenter pack summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "presenter pack summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "presenter pack summary exposed provider payload"
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

test_quality_evaluation_evidence_helpers_are_metadata_only() {
  cat >"$TMPDIR/job-quality-evidence.json" <<'JSON'
{
  "jobId": "job-quality",
  "videoId": "video-quality",
  "targetLanguage": "zh-CN",
  "status": "COMPLETED",
  "createdAt": "2026-06-28T10:00:00Z",
  "failureReason": "provider request payload raw transcript text sk-test /Users/example/job-artifacts/raw.json",
  "qualityEvaluation": {
    "evaluationId": "quality-1",
    "jobId": "job-quality",
    "language": "zh-CN",
    "score": 92,
    "verdict": "GOOD",
    "completeness": 95,
    "readability": 92,
    "timingPreservation": 94,
    "naturalness": 88,
    "issues": ["One subtitle line is slightly literal."],
    "suggestedFixes": ["Review terminology."],
    "status": "SUCCEEDED",
    "safeErrorSummary": null,
    "createdAt": "2026-06-28T10:01:00Z"
  }
}
JSON

  print_quality_evaluation_summary_file "$TMPDIR/job-quality-evidence.json" >"$TMPDIR/job-quality-summary.out"
  local summary
  summary="$(cat "$TMPDIR/job-quality-summary.out")"
  [[ "$summary" == *"qualityEvaluationJobId=job-quality"* ]] || fail "quality summary missed job id"
  [[ "$summary" == *"qualityEvaluationStatus=SUCCEEDED"* ]] || fail "quality summary missed status"
  [[ "$summary" == *"qualityEvaluationScore=92"* ]] || fail "quality summary missed score"
  [[ "$summary" == *"qualityEvaluationIssueCount=1"* ]] || fail "quality summary missed issue count"

  write_quality_evaluation_evidence_markdown \
    "$TMPDIR/job-quality-evidence.json" \
    "$TMPDIR/quality-evidence.md"
  print_quality_evidence_markdown_summary "$TMPDIR/quality-evidence.md" "job-quality" >"$TMPDIR/quality-evidence-summary.out"
  local output
  output="$(cat "$TMPDIR/quality-evidence.md")"
  [[ "$output" == *"# LinguaFrame Quality Evaluation Evidence"* ]] || fail "quality evidence missed title"
  [[ "$output" == *"- Score: 92 / 100"* ]] || fail "quality evidence missed score"
  [[ "$output" == *"/api/jobs/job-quality/quality-evaluation/evidence/markdown/download"* ]] || fail "quality evidence missed backend route"
  [[ "$output" != *"raw transcript text"* ]] || fail "quality evidence exposed raw transcript text"
  [[ "$output" != *"provider request payload"* ]] || fail "quality evidence exposed provider payload"
  [[ "$output" != *"/Users/example"* ]] || fail "quality evidence exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "quality evidence exposed token"
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

test_print_demo_handoff_checklist_summary_is_metadata_only() {
  cat >"$TMPDIR/handoff-job.json" <<'JSON'
{
  "jobId": "job-handoff",
  "videoId": "video-handoff",
  "status": "COMPLETED",
  "failureStage": null,
  "failureReason": null,
  "pipelineProgress": {
    "terminal": true
  },
  "usageSummary": {
    "modelCallCount": 2,
    "failedModelCallCount": 0,
    "estimatedCostUsd": 0
  },
  "cacheSummary": {
    "cacheHitCount": 1,
    "providerCacheHitCount": 1
  },
  "failureTriage": null,
  "unsafeTranscript": "raw transcript text",
  "unsafeProviderPayload": "provider payload"
}
JSON
  cat >"$TMPDIR/handoff-manifest.json" <<'JSON'
{
  "jobId": "job-handoff",
  "handoffReady": true,
  "reviewedSubtitleArtifactCount": 3,
  "links": [
    {
      "label": "Evidence bundle",
      "kind": "EVIDENCE_BUNDLE",
      "url": "/api/jobs/job-handoff/evidence/bundle/download"
    }
  ],
  "unsafeSubtitle": "raw subtitle text",
  "unsafeObjectKey": "job-artifacts/job-handoff/private"
}
JSON
  cat >"$TMPDIR/handoff-artifacts.json" <<'JSON'
[
  {
    "type": "REVIEWED_SUBTITLE_JSON",
    "filename": "reviewed-subtitles.zh-CN.json",
    "contentType": "application/json",
    "sourceArtifactId": null
  },
  {
    "type": "REVIEWED_SUBTITLE_SRT",
    "filename": "reviewed-subtitles.zh-CN.srt",
    "contentType": "application/x-subrip",
    "sourceArtifactId": null
  },
  {
    "type": "REVIEWED_SUBTITLE_VTT",
    "filename": "reviewed-subtitles.zh-CN.vtt",
    "contentType": "text/vtt",
    "sourceArtifactId": null
  },
  {
    "type": "BURNED_VIDEO",
    "filename": "burned-video.mp4",
    "contentType": "video/mp4",
    "sourceArtifactId": "job-artifacts/private/video.mp4",
    "unsafePath": "/Users/example/private.mov",
    "unsafeToken": "OPENAI_API_KEY",
    "unsafeText": "raw corrected subtitle"
  }
]
JSON

  print_demo_handoff_checklist_summary \
    "$TMPDIR/handoff-job.json" \
    "$TMPDIR/handoff-manifest.json" \
    "$TMPDIR/handoff-artifacts.json" >"$TMPDIR/handoff-checklist.out"
  local output
  output="$(cat "$TMPDIR/handoff-checklist.out")"

  [[ "$output" == *"demoHandoffOverall=READY"* ]] || fail "demo handoff checklist missed ready overall"
  [[ "$output" == *"demoHandoffItem=PASS:Job completed"* ]] || fail "demo handoff checklist missed job completion"
  [[ "$output" == *"demoHandoffItem=PASS:Reviewed subtitles ready"* ]] || fail "demo handoff checklist missed reviewed subtitles"
  [[ "$output" == *"demoHandoffItem=PASS:Media outputs available"* ]] || fail "demo handoff checklist missed media output"
  [[ "$output" == *"demoHandoffItem=PASS:Evidence downloads ready"* ]] || fail "demo handoff checklist missed evidence output"
  [[ "$output" != *"raw transcript text"* ]] || fail "demo handoff checklist exposed transcript"
  [[ "$output" != *"raw subtitle text"* ]] || fail "demo handoff checklist exposed subtitle"
  [[ "$output" != *"raw corrected subtitle"* ]] || fail "demo handoff checklist exposed corrected subtitle"
  [[ "$output" != *"job-artifacts/"* ]] || fail "demo handoff checklist exposed object key"
  [[ "$output" != *"/Users/example/private.mov"* ]] || fail "demo handoff checklist exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "demo handoff checklist exposed provider payload"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "demo handoff checklist exposed token"
}

test_print_handoff_package_summary_validates_zip_and_secrets() {
  python3 - "$TMPDIR/handoff-package.zip" "$TMPDIR/unsafe-handoff-package.zip" <<'PY'
import json
import sys
import zipfile

safe_path = sys.argv[1]
unsafe_path = sys.argv[2]
manifest = {
    "jobId": "job-handoff-package",
    "videoId": "video-handoff-package",
    "targetLanguage": "zh-CN",
    "status": "COMPLETED",
    "handoffReady": True,
    "reviewedArtifactCount": 3,
}
with zipfile.ZipFile(safe_path, "w") as archive:
    archive.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
    archive.writestr("delivery-manifest.md", "# Delivery manifest\n- Job: job-handoff-package\n")
    archive.writestr("evidence.md", "# LinguaFrame Demo Evidence\n- Job: job-handoff-package\n")
    archive.writestr("diagnostics.json", json.dumps({"job": {"jobId": "job-handoff-package"}}))
    archive.writestr("reviewed/REVIEWED_SUBTITLE_JSON/reviewed-subtitles.zh-CN.json", "{}")
    archive.writestr("reviewed/REVIEWED_SUBTITLE_SRT/reviewed-subtitles.zh-CN.srt", "1\n00:00:00,000 --> 00:00:01,000\nDemo")
    archive.writestr("reviewed/REVIEWED_SUBTITLE_VTT/reviewed-subtitles.zh-CN.vtt", "WEBVTT\n")

with zipfile.ZipFile(unsafe_path, "w") as archive:
    archive.writestr("manifest.json", json.dumps(manifest, separators=(",", ":")))
    archive.writestr("delivery-manifest.md", "# Delivery manifest\n")
    archive.writestr("evidence.md", "OPENAI_API_KEY should never appear")
    archive.writestr("diagnostics.json", "{}")
    archive.writestr("reviewed/REVIEWED_SUBTITLE_SRT/reviewed-subtitles.zh-CN.srt", "safe")
PY

  print_handoff_package_summary "$TMPDIR/handoff-package.zip" "job-handoff-package" >"$TMPDIR/handoff-package.out"
  local output
  output="$(cat "$TMPDIR/handoff-package.out")"

  [[ "$output" == *"handoffPackageJobId=job-handoff-package"* ]] || fail "handoff package summary missed job id"
  [[ "$output" == *"handoffPackageEntryCount=7"* ]] || fail "handoff package summary missed entry count"
  [[ "$output" == *"handoffPackageReviewedArtifactCount=3"* ]] || fail "handoff package summary missed reviewed count"

  if print_handoff_package_summary "$TMPDIR/unsafe-handoff-package.zip" "job-handoff-package" >"$TMPDIR/unsafe-handoff-package.out" 2>&1; then
    fail "handoff package summary accepted unsafe ZIP"
  fi
  [[ "$(cat "$TMPDIR/unsafe-handoff-package.out")" == *"forbidden sensitive string"* ]] || fail "handoff package unsafe failure was not explicit"
}

test_print_demo_run_package_summary_validates_zip_and_secrets() {
  python3 - "$TMPDIR/demo-run-package.zip" "$TMPDIR/unsafe-demo-run-package.zip" <<'PY'
import json
import sys
import zipfile

safe_path = sys.argv[1]
unsafe_path = sys.argv[2]
manifest = {
    "jobId": "job-demo-run-package",
    "videoId": "video-demo-run-package",
    "targetLanguage": "zh-CN",
    "status": "COMPLETED",
}
entries = {
    "manifest.json": json.dumps(manifest, separators=(",", ":")),
    "README.md": "# LinguaFrame Demo Run Package\n- Job: job-demo-run-package\n",
    "job-detail.json": json.dumps({"jobId": "job-demo-run-package"}),
    "diagnostics.json": json.dumps({"job": {"jobId": "job-demo-run-package"}}),
    "evidence.md": "# Evidence\n- Job: job-demo-run-package\n",
    "quality-evidence.md": "# Quality evidence\n- Job: job-demo-run-package\n",
    "delivery-manifest.md": "# Delivery manifest\n- Job: job-demo-run-package\n",
    "demo-handoff-checklist.md": "# Checklist\n- Job: job-demo-run-package\n",
    "demo-session-report.md": "# Session report\n- Job: job-demo-run-package\n",
}
with zipfile.ZipFile(safe_path, "w") as archive:
    for name, content in entries.items():
        archive.writestr(name, content)

unsafe_entries = dict(entries)
unsafe_entries["evidence.md"] = "provider request payload sk-test /Users/example/job-artifacts/raw.json"
with zipfile.ZipFile(unsafe_path, "w") as archive:
    for name, content in unsafe_entries.items():
        archive.writestr(name, content)
PY

  print_demo_run_package_summary "$TMPDIR/demo-run-package.zip" "job-demo-run-package" >"$TMPDIR/demo-run-package.out"
  local output
  output="$(cat "$TMPDIR/demo-run-package.out")"

  [[ "$output" == *"demoRunPackageJobId=job-demo-run-package"* ]] || fail "demo run package summary missed job id"
  [[ "$output" == *"demoRunPackageEntryCount=9"* ]] || fail "demo run package summary missed entry count"

  if print_demo_run_package_summary "$TMPDIR/unsafe-demo-run-package.zip" "job-demo-run-package" >"$TMPDIR/unsafe-demo-run-package.out" 2>&1; then
    fail "demo run package summary accepted unsafe ZIP"
  fi
  [[ "$(cat "$TMPDIR/unsafe-demo-run-package.out")" == *"forbidden sensitive string"* ]] || fail "demo run package unsafe failure was not explicit"
}

test_print_ai_audit_package_summary_validates_zip_and_secrets() {
  python3 - "$TMPDIR/ai-audit-package.zip" "$TMPDIR/unsafe-ai-audit-package.zip" <<'PY'
import json
import sys
import zipfile

safe_path = sys.argv[1]
unsafe_path = sys.argv[2]
manifest = {
    "jobId": "job-ai-audit-package",
    "videoId": "video-ai-audit-package",
    "targetLanguage": "zh-CN",
    "status": "COMPLETED",
    "modelCallCount": 3,
    "promptTemplateCount": 2,
}
entries = {
    "manifest.json": json.dumps(manifest, separators=(",", ":")),
    "README.md": "# LinguaFrame AI Audit Package\n- Job: job-ai-audit-package\n",
    "model-calls.json": json.dumps([
        {"operation": "TRANSCRIPTION", "promptVersion": "openai-audio-transcriptions-v1"},
        {"operation": "TRANSLATION", "promptVersion": "openai-subtitle-translation-v1"},
        {"operation": "EVALUATION", "promptVersion": "openai-translation-quality-evaluation-v1"},
    ]),
    "prompt-templates.json": json.dumps([
        {"purpose": "SUBTITLE_TRANSLATION", "version": "openai-subtitle-translation-v1"},
        {"purpose": "TRANSLATION_QUALITY_EVALUATION", "version": "openai-translation-quality-evaluation-v1"},
    ]),
    "ai-usage-summary.json": json.dumps({"modelCallCount": 3, "failedModelCallCount": 1}),
    "ai-audit-report.md": "# AI audit\n- Job: job-ai-audit-package\n",
}
with zipfile.ZipFile(safe_path, "w") as archive:
    for name, content in entries.items():
        archive.writestr(name, content)

unsafe_entries = dict(entries)
unsafe_entries["model-calls.json"] = "provider request payload sk-test /Users/example/job-artifacts/raw.json"
with zipfile.ZipFile(unsafe_path, "w") as archive:
    for name, content in unsafe_entries.items():
        archive.writestr(name, content)
PY

  print_ai_audit_package_summary "$TMPDIR/ai-audit-package.zip" "job-ai-audit-package" >"$TMPDIR/ai-audit-package.out"
  local output
  output="$(cat "$TMPDIR/ai-audit-package.out")"

  [[ "$output" == *"aiAuditPackageJobId=job-ai-audit-package"* ]] || fail "AI audit package summary missed job id"
  [[ "$output" == *"aiAuditPackageEntryCount=6"* ]] || fail "AI audit package summary missed entry count"
  [[ "$output" == *"aiAuditPackageModelCallCount=3"* ]] || fail "AI audit package summary missed model-call count"

  if print_ai_audit_package_summary "$TMPDIR/unsafe-ai-audit-package.zip" "job-ai-audit-package" >"$TMPDIR/unsafe-ai-audit-package.out" 2>&1; then
    fail "AI audit package summary accepted unsafe ZIP"
  fi
  [[ "$(cat "$TMPDIR/unsafe-ai-audit-package.out")" == *"forbidden sensitive string"* ]] || fail "AI audit package unsafe failure was not explicit"
}

test_print_source_media_summary_is_metadata_only() {
  cat >"$TMPDIR/source-media.json" <<'JSON'
{
  "videoId": "video-source",
  "filename": "sample.mp4",
  "contentType": "video/mp4",
  "fileSizeBytes": 4096,
  "durationSeconds": 45,
  "sourceObjectKey": "source-videos/video-source/sample.mp4",
  "status": "UPLOADED",
  "createdAt": "2026-06-28T10:00:00Z",
  "unsafePath": "/Users/example/source.mp4",
  "unsafeToken": "demo-access-token",
  "unsafeKey": "OPENAI_API_KEY"
}
JSON

  print_source_media_summary "$TMPDIR/source-media.json" "job-source" >"$TMPDIR/source-media.out"
  local output
  output="$(cat "$TMPDIR/source-media.out")"

  [[ "$output" == *"sourceMediaJobId=job-source"* ]] || fail "source media summary missed job id"
  [[ "$output" == *"sourceMediaVideoId=video-source"* ]] || fail "source media summary missed video id"
  [[ "$output" == *"sourceMediaFilename=sample.mp4"* ]] || fail "source media summary missed filename"
  [[ "$output" == *"sourceMediaContentType=video/mp4"* ]] || fail "source media summary missed content type"
  [[ "$output" == *"sourceMediaDurationSeconds=45"* ]] || fail "source media summary missed duration"
  [[ "$output" == *"sourceMediaDownloadRoute=/api/media/uploads/video-source/source/download"* ]] || fail "source media summary missed download route"
  [[ "$output" != *"source-videos/"* ]] || fail "source media summary exposed object key"
  [[ "$output" != *"/Users/example"* ]] || fail "source media summary exposed local path"
  [[ "$output" != *"demo-access-token"* ]] || fail "source media summary exposed demo token"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "source media summary exposed API key"
}

test_write_demo_session_report_markdown_is_metadata_only() {
  cat >"$TMPDIR/session-job.json" <<'JSON'
{
  "jobId": "job-session",
  "videoId": "video-session",
  "targetLanguage": "zh-CN",
  "status": "COMPLETED",
  "retryCount": 0,
  "pipelineProgress": {
    "terminal": true
  },
  "usageSummary": {
    "modelCallCount": 3,
    "failedModelCallCount": 0,
    "estimatedCostUsd": 0.0012
  },
  "cacheSummary": {
    "cacheHitCount": 1,
    "providerCacheHitCount": 2
  },
  "failureTriage": null,
  "unsafeTranscript": "raw transcript text",
  "unsafeProviderPayload": "provider payload"
}
JSON
  cat >"$TMPDIR/session-manifest.json" <<'JSON'
{
  "jobId": "job-session",
  "handoffReady": true,
  "reviewedSubtitleArtifactCount": 3,
  "reviewedBurnedVideoAvailable": true,
  "unsafeSubtitle": "raw subtitle text",
  "unsafeObjectKey": "job-artifacts/job-session/private"
}
JSON
  cat >"$TMPDIR/session-artifacts.json" <<'JSON'
[
  {
    "type": "REVIEWED_SUBTITLE_JSON",
    "filename": "reviewed-subtitles.zh-CN.json",
    "unsafeText": "raw corrected subtitle"
  },
  {
    "type": "REVIEWED_SUBTITLE_SRT",
    "filename": "reviewed-subtitles.zh-CN.srt"
  },
  {
    "type": "REVIEWED_SUBTITLE_VTT",
    "filename": "reviewed-subtitles.zh-CN.vtt"
  },
  {
    "type": "DUBBING_AUDIO",
    "filename": "dubbing-audio.mp3",
    "sourceArtifactId": "job-artifacts/private/audio.mp3",
    "unsafePath": "/Users/example/private.mov",
    "unsafeToken": "OPENAI_API_KEY",
    "unsafeDemoToken": "private-demo-token"
  }
]
JSON

  write_demo_session_report_markdown \
    "$TMPDIR/session-job.json" \
    "$TMPDIR/session-manifest.json" \
    "$TMPDIR/session-artifacts.json" \
    "$TMPDIR/demo-session-report.md"
  local output
  output="$(cat "$TMPDIR/demo-session-report.md")"

  [[ "$output" == *"# LinguaFrame Demo Session Report"* ]] || fail "demo session report missed title"
  [[ "$output" == *"- Overall: READY"* ]] || fail "demo session report missed ready overall"
  [[ "$output" == *"## Input and job"* ]] || fail "demo session report missed input section"
  [[ "$output" == *"## Generated outputs"* ]] || fail "demo session report missed outputs section"
  [[ "$output" == *"## Handoff evidence"* ]] || fail "demo session report missed handoff section"
  [[ "$output" == *"## Cost and cache"* ]] || fail "demo session report missed cost section"
  [[ "$output" != *"raw transcript text"* ]] || fail "demo session report exposed transcript"
  [[ "$output" != *"raw subtitle text"* ]] || fail "demo session report exposed subtitle"
  [[ "$output" != *"raw corrected subtitle"* ]] || fail "demo session report exposed corrected subtitle"
  [[ "$output" != *"job-artifacts/"* ]] || fail "demo session report exposed object key"
  [[ "$output" != *"/Users/example/private.mov"* ]] || fail "demo session report exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "demo session report exposed provider payload"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "demo session report exposed API key"
  [[ "$output" != *"private-demo-token"* ]] || fail "demo session report exposed demo token"
}

test_write_private_demo_operations_report_is_metadata_only() {
  cat >"$TMPDIR/private-demo-operations.json" <<'JSON'
{
  "generatedAt": "2026-06-28T08:00:00Z",
  "overallStatus": "ATTENTION",
  "readyCount": 7,
  "attentionCount": 2,
  "blockedCount": 0,
  "sections": [
    {
      "title": "Access gate",
      "status": "READY",
      "checks": [
        {
          "label": "Owner access gate",
          "status": "READY",
          "detail": "Private demo API access requires the configured owner token.",
          "nextAction": "Use the browser owner-session login or demo token header for API calls.",
          "unsafeToken": "private-demo-token"
        }
      ]
    },
    {
      "title": "Live dependencies",
      "status": "ATTENTION",
      "checks": [
        {
          "label": "openai",
          "status": "ATTENTION",
          "detail": "OpenAI connectivity check is disabled.",
          "nextAction": "Enable the check before provider-backed demos.",
          "unsafeApiKey": "OPENAI_API_KEY",
          "unsafePayload": "provider payload"
        }
      ]
    }
  ],
  "commands": [
    {
      "label": "Private demo preflight",
      "command": "scripts/demo/private-demo-preflight.sh",
      "detail": "Checks local env and dependency reachability."
    },
    {
      "label": "Restore dry-run",
      "command": "scripts/demo/private-demo-restore.sh --dry-run --backup-dir <backup-dir>",
      "detail": "Validates a selected backup before any guarded restore.",
      "unsafePath": "/Users/example/backups/private"
    }
  ],
  "documentationLinks": [
    {
      "label": "Private demo deployment",
      "path": "docs/deployment/private-demo.md",
      "detail": "Reverse proxy, env, backup, and restore runbook.",
      "unsafeObjectKey": "job-artifacts/private"
    }
  ]
}
JSON

  print_private_demo_operations_summary <"$TMPDIR/private-demo-operations.json" >"$TMPDIR/private-demo-operations.out"
  local summary
  summary="$(cat "$TMPDIR/private-demo-operations.out")"
  [[ "$summary" == *"privateDemoOperationsOverall=ATTENTION"* ]] || fail "operations summary missed overall"
  [[ "$summary" == *"privateDemoOperationsReadyCount=7"* ]] || fail "operations summary missed ready count"
  [[ "$summary" == *"privateDemoOperationsSection=ATTENTION:Live dependencies"* ]] || fail "operations summary missed section"

  write_private_demo_operations_report \
    "$TMPDIR/private-demo-operations.json" \
    "$TMPDIR/private-demo-operations-report.md"
  local output
  output="$(cat "$TMPDIR/private-demo-operations-report.md")"

  [[ "$output" == *"# LinguaFrame Private Demo Operations Report"* ]] || fail "operations report missed title"
  [[ "$output" == *"- Overall: ATTENTION"* ]] || fail "operations report missed overall"
  [[ "$output" == *"## Checks"* ]] || fail "operations report missed checks section"
  [[ "$output" == *"ATTENTION Live dependencies / openai: OpenAI connectivity check is disabled."* ]] || fail "operations report missed openai check"
  [[ "$output" == *"scripts/demo/private-demo-preflight.sh"* ]] || fail "operations report missed preflight command"
  [[ "$output" == *"docs/deployment/private-demo.md"* ]] || fail "operations report missed doc link"
  [[ "$output" != *"private-demo-token"* ]] || fail "operations report exposed demo token"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "operations report exposed API key"
  [[ "$output" != *"job-artifacts/"* ]] || fail "operations report exposed object key"
  [[ "$output" != *"/Users/example"* ]] || fail "operations report exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "operations report exposed provider payload"
}

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file
test_upload_demo_video_includes_subtitle_polishing_mode
test_upload_demo_video_applies_tears_showcase_profile
test_upload_demo_video_explicit_env_overrides_profile
test_download_job_comparison_helpers_use_backend_routes
test_print_job_comparison_summary_is_metadata_only
test_download_demo_run_matrix_helper_uses_backend_route
test_print_demo_run_matrix_summary_is_metadata_only
test_download_demo_presenter_pack_helper_uses_backend_route
test_print_demo_presenter_pack_summary_is_metadata_only
test_print_job_summary_includes_failure_triage
test_print_diagnostics_summary_includes_failure_triage
test_quality_evaluation_evidence_helpers_are_metadata_only
test_print_subtitle_review_summary_is_metadata_only
test_print_subtitle_draft_summary_is_metadata_only
test_print_reviewed_publish_summary_is_metadata_only
test_print_delivery_manifest_summary_is_metadata_only
test_print_media_delivery_summary_is_metadata_only
test_print_demo_handoff_checklist_summary_is_metadata_only
test_print_handoff_package_summary_validates_zip_and_secrets
test_print_demo_run_package_summary_validates_zip_and_secrets
test_print_ai_audit_package_summary_validates_zip_and_secrets
test_print_source_media_summary_is_metadata_only
test_write_demo_session_report_markdown_is_metadata_only
test_write_private_demo_operations_report_is_metadata_only

echo "linguaframe-demo client tests passed"
