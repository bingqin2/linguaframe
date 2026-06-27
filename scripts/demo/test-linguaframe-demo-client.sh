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
}

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file
test_print_job_summary_includes_failure_triage
test_print_diagnostics_summary_includes_failure_triage

echo "linguaframe-demo client tests passed"
