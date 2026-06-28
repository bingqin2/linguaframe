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

test_demo_session_owner_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-session.json" <<'JSON'
{
  "accessGateEnabled": true,
  "authenticated": true,
  "headerName": "X-LinguaFrame-Demo-Token",
  "mode": "OWNER_SESSION_ACTIVE",
  "ownerId": "owner-alpha",
  "ownershipScope": "CONFIGURED_DEMO_OWNER",
  "token": "sk-test-secret",
  "localPath": "/Users/example/private.mov"
}
JSON

  print_demo_session_owner_summary_file "$TMPDIR/demo-session.json" >"$TMPDIR/demo-session.out"
  local output
  output="$(cat "$TMPDIR/demo-session.out")"

  [[ "$output" == *"demoSessionMode=OWNER_SESSION_ACTIVE"* ]] || fail "demo session summary missed mode"
  [[ "$output" == *"demoSessionAuthenticated=true"* ]] || fail "demo session summary missed authenticated state"
  [[ "$output" == *"demoOwnerId=owner-alpha"* ]] || fail "demo session summary missed owner id"
  [[ "$output" == *"demoOwnershipScope=CONFIGURED_DEMO_OWNER"* ]] || fail "demo session summary missed ownership scope"
  [[ "$output" != *"sk-test-secret"* ]] || fail "demo session summary exposed token"
  [[ "$output" != *"/Users/example"* ]] || fail "demo session summary exposed local path"
}

test_local_auth_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_local_auth_session_json "http://example.test" "$TMPDIR/local-auth-session.json" >"$TMPDIR/local-auth-session-curl.out"

  local session_curl_output
  session_curl_output="$(cat "$TMPDIR/local-auth-session-curl.out")"
  [[ "$session_curl_output" == *"http://example.test/api/auth/session"* ]] || fail "local auth session helper used wrong route"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    login_local_auth_json "http://example.test" "owner" "owner-password" "$TMPDIR/local-auth-login.json" >"$TMPDIR/local-auth-login-curl.out"

  local login_curl_output
  login_curl_output="$(cat "$TMPDIR/local-auth-login-curl.out")"
  [[ "$login_curl_output" == *"http://example.test/api/auth/login"* ]] || fail "local auth login helper used wrong route"
  [[ "$login_curl_output" == *"{\"username\":\"owner\",\"password\":\"owner-password\"}"* ]] || fail "local auth login helper missed credentials body"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    auth_curl "jwt-token" -fsS http://example.test/api/runtime/dependencies >"$TMPDIR/local-auth-bearer-curl.out"
  local bearer_curl_output
  bearer_curl_output="$(cat "$TMPDIR/local-auth-bearer-curl.out")"
  [[ "$bearer_curl_output" == *"Authorization: Bearer jwt-token"* ]] || fail "auth_curl did not add bearer token"
  [[ "$bearer_curl_output" == *"http://example.test/api/runtime/dependencies"* ]] || fail "auth_curl did not preserve URL"

  cat >"$TMPDIR/local-auth-login.json" <<'JSON'
{
  "token": "jwt-token",
  "tokenType": "Bearer",
  "expiresAt": "2026-06-28T13:00:00Z",
  "session": {
    "enabled": true,
    "configured": true,
    "authenticated": true,
    "ownerId": "owner-alpha",
    "username": "owner",
    "authMode": "LOCAL_AUTH_ACTIVE"
  },
  "password": "owner-password",
  "jwtSecret": "0123456789abcdef0123456789abcdef",
  "demoToken": "private-demo-token"
}
JSON

  print_local_auth_summary_file "$TMPDIR/local-auth-login.json" >"$TMPDIR/local-auth-login.out"
  local output
  output="$(cat "$TMPDIR/local-auth-login.out")"
  [[ "$output" == *"localAuthEnabled=true"* ]] || fail "local auth summary missed enabled state"
  [[ "$output" == *"localAuthConfigured=true"* ]] || fail "local auth summary missed configured state"
  [[ "$output" == *"localAuthAuthenticated=true"* ]] || fail "local auth summary missed authenticated state"
  [[ "$output" == *"localAuthOwnerId=owner-alpha"* ]] || fail "local auth summary missed owner"
  [[ "$output" == *"localAuthUsername=owner"* ]] || fail "local auth summary missed username"
  [[ "$output" == *"localAuthTokenExpiresAt=2026-06-28T13:00:00Z"* ]] || fail "local auth summary missed expiry"
  [[ "$output" != *"jwt-token"* ]] || fail "local auth summary exposed bearer token"
  [[ "$output" != *"owner-password"* ]] || fail "local auth summary exposed password"
  [[ "$output" != *"0123456789abcdef"* ]] || fail "local auth summary exposed signing secret"
  [[ "$output" != *"private-demo-token"* ]] || fail "local auth summary exposed demo token"
}

test_owner_workspace_smoke_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_owner_workspace_jobs_json "http://example.test" "jwt-token" "$TMPDIR/owner-workspace-jobs.json" >"$TMPDIR/owner-workspace-jobs-curl.out"
  local jobs_curl_output
  jobs_curl_output="$(cat "$TMPDIR/owner-workspace-jobs-curl.out")"
  [[ "$jobs_curl_output" == *"Authorization: Bearer jwt-token"* ]] || fail "owner workspace jobs helper missed bearer header"
  [[ "$jobs_curl_output" == *"http://example.test/api/jobs?limit=20&offset=0"* ]] || fail "owner workspace jobs helper used wrong route"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_owner_workspace_upload_readiness_json "http://example.test" "jwt-token" "tears-showcase" "$TMPDIR/owner-workspace-readiness.json" >"$TMPDIR/owner-workspace-readiness-curl.out"
  local readiness_curl_output
  readiness_curl_output="$(cat "$TMPDIR/owner-workspace-readiness-curl.out")"
  [[ "$readiness_curl_output" == *"Authorization: Bearer jwt-token"* ]] || fail "owner workspace readiness helper missed bearer header"
  [[ "$readiness_curl_output" == *"http://example.test/api/media/uploads/readiness?demoProfileId=tears-showcase"* ]] || fail "owner workspace readiness helper used wrong route"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_owner_workspace_runtime_dependencies_json "http://example.test" "jwt-token" "$TMPDIR/owner-workspace-runtime.json" >"$TMPDIR/owner-workspace-runtime-curl.out"
  local runtime_curl_output
  runtime_curl_output="$(cat "$TMPDIR/owner-workspace-runtime-curl.out")"
  [[ "$runtime_curl_output" == *"Authorization: Bearer jwt-token"* ]] || fail "owner workspace runtime helper missed bearer header"
  [[ "$runtime_curl_output" == *"http://example.test/api/runtime/dependencies"* ]] || fail "owner workspace runtime helper used wrong route"

  cat >"$TMPDIR/owner-workspace-session.json" <<'JSON'
{
  "enabled": true,
  "configured": true,
  "authenticated": true,
  "ownerId": "owner-alpha",
  "username": "owner",
  "ownershipScope": "LOCAL_AUTH_OWNER",
  "authMode": "LOCAL_AUTH_ACTIVE",
  "token": "jwt-token",
  "password": "owner-password"
}
JSON
  cat >"$TMPDIR/owner-workspace-jobs.json" <<'JSON'
{
  "jobs": [
    { "jobId": "job-alpha", "filename": "alpha.mp4", "ownerId": "owner-alpha" }
  ],
  "total": 1,
  "limit": 20,
  "offset": 0,
  "sourceObjectKey": "uploads/video-1/source.mp4"
}
JSON
  cat >"$TMPDIR/owner-workspace-readiness.json" <<'JSON'
{
  "overallStatus": "READY",
  "ownerId": "owner-alpha",
  "demoProfileId": "tears-showcase",
  "generatedAt": "2026-06-28T08:00:00Z",
  "checks": [],
  "requiredActions": [],
  "evidenceRoutes": [],
  "localPath": "/Users/example/private.mov"
}
JSON

  print_owner_workspace_summary_files \
    "$TMPDIR/owner-workspace-session.json" \
    "$TMPDIR/owner-workspace-jobs.json" \
    "$TMPDIR/owner-workspace-readiness.json" \
    >"$TMPDIR/owner-workspace.out"
  local output
  output="$(cat "$TMPDIR/owner-workspace.out")"
  [[ "$output" == *"ownerWorkspaceAuthMode=LOCAL_AUTH_ACTIVE"* ]] || fail "owner workspace summary missed auth mode"
  [[ "$output" == *"ownerWorkspaceOwnerId=owner-alpha"* ]] || fail "owner workspace summary missed owner id"
  [[ "$output" == *"ownerWorkspaceOwnershipScope=LOCAL_AUTH_OWNER"* ]] || fail "owner workspace summary missed ownership scope"
  [[ "$output" == *"ownerWorkspaceJobCount=1"* ]] || fail "owner workspace summary missed job count"
  [[ "$output" == *"ownerWorkspaceUploadReadiness=READY"* ]] || fail "owner workspace summary missed upload readiness"
  [[ "$output" != *"jwt-token"* ]] || fail "owner workspace summary exposed bearer token"
  [[ "$output" != *"owner-password"* ]] || fail "owner workspace summary exposed password"
  [[ "$output" != *"uploads/video-1/source.mp4"* ]] || fail "owner workspace summary exposed object key"
  [[ "$output" != *"/Users/example"* ]] || fail "owner workspace summary exposed local path"
  [[ "$output" != *"alpha.mp4"* ]] || fail "owner workspace summary exposed filename"
}

test_owner_workspace_smoke_script_skips_when_auth_unconfigured() {
  local fake_curl="$TMPDIR/fake-owner-workspace-curl"
  cat >"$fake_curl" <<'SH'
#!/usr/bin/env bash
output_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
cat >"$output_path" <<'JSON'
{
  "enabled": false,
  "configured": false,
  "authenticated": false,
  "ownerId": "demo-owner",
  "username": "owner",
  "ownershipScope": "CONFIGURED_DEMO_OWNER",
  "authMode": "LOCAL_AUTH_DISABLED"
}
JSON
SH
  chmod +x "$fake_curl"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_OWNER_WORKSPACE_OUTPUT_DIR="$TMPDIR/owner-workspace-skip" \
    "$SCRIPT_DIR/owner-workspace-smoke.sh" >"$TMPDIR/owner-workspace-skip.out"

  local output
  output="$(cat "$TMPDIR/owner-workspace-skip.out")"
  [[ "$output" == *"ownerWorkspaceAuthMode=LOCAL_AUTH_DISABLED"* ]] || fail "owner workspace skip missed auth mode"
  [[ "$output" == *"Local auth is disabled or unconfigured; bearer workspace smoke skipped."* ]] || fail "owner workspace script did not skip unconfigured auth"
}

test_owner_quota_preflight_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_owner_quota_preflight_json "http://example.test" "$TMPDIR/owner-quota.json" >"$TMPDIR/owner-quota-curl.out"

  local curl_output
  curl_output="$(cat "$TMPDIR/owner-quota-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/media/uploads/preflight"* ]] || fail "owner quota helper used wrong route"

  cat >"$TMPDIR/owner-quota.json" <<'JSON'
{
  "ownerId": "demo-owner",
  "enabled": true,
  "allowed": false,
  "activeJobs": 2,
  "queuedJobs": 1,
  "dailyEstimatedCostUsd": 0.25,
  "dailyBudgetDate": "2026-06-28",
  "limits": [
    { "name": "activeJobs", "enabled": true, "current": 2, "limit": 2 },
    { "name": "queuedJobs", "enabled": true, "current": 1, "limit": 1 },
    { "name": "dailyCostUsd", "enabled": true, "current": 0.25, "limit": 0.25 }
  ],
  "blockingReasons": [
    "Owner active job limit reached: 2 / 2"
  ],
  "demoToken": "private-demo-token",
  "sourceObjectKey": "uploads/video-1/source.mp4",
  "localPath": "/Users/example/private.mov",
  "providerPayload": "raw provider payload"
}
JSON

  print_owner_quota_preflight_summary_file "$TMPDIR/owner-quota.json" >"$TMPDIR/owner-quota.out"
  local output
  output="$(cat "$TMPDIR/owner-quota.out")"
  [[ "$output" == *"ownerQuotaOwnerId=demo-owner"* ]] || fail "owner quota summary missed owner"
  [[ "$output" == *"ownerQuotaEnabled=true"* ]] || fail "owner quota summary missed enabled state"
  [[ "$output" == *"ownerQuotaAllowed=false"* ]] || fail "owner quota summary missed allowed state"
  [[ "$output" == *"ownerQuotaActiveJobs=2"* ]] || fail "owner quota summary missed active jobs"
  [[ "$output" == *"ownerQuotaQueuedJobs=1"* ]] || fail "owner quota summary missed queued jobs"
  [[ "$output" == *"ownerQuotaDailyEstimatedCostUsd=0.25"* ]] || fail "owner quota summary missed daily cost"
  [[ "$output" == *"ownerQuotaLimit=activeJobs:enabled=true:current=2:limit=2"* ]] || fail "owner quota summary missed active limit"
  [[ "$output" == *"ownerQuotaBlockingReason=Owner active job limit reached: 2 / 2"* ]] || fail "owner quota summary missed blocking reason"
  [[ "$output" != *"private-demo-token"* ]] || fail "owner quota summary exposed demo token"
  [[ "$output" != *"uploads/video-1/source.mp4"* ]] || fail "owner quota summary exposed object key"
  [[ "$output" != *"/Users/example"* ]] || fail "owner quota summary exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "owner quota summary exposed provider payload"
}

test_owner_quota_preflight_script_exits_on_blocked_state() {
  local fake_curl="$TMPDIR/fake-owner-quota-curl"
  cat >"$fake_curl" <<'SH'
#!/usr/bin/env bash
output_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
cat >"$output_path" <<'JSON'
{
  "ownerId": "demo-owner",
  "enabled": true,
  "allowed": false,
  "activeJobs": 2,
  "queuedJobs": 0,
  "dailyEstimatedCostUsd": 0,
  "dailyBudgetDate": "2026-06-28",
  "limits": [
    { "name": "activeJobs", "enabled": true, "current": 2, "limit": 2 }
  ],
  "blockingReasons": ["Owner active job limit reached: 2 / 2"]
}
JSON
SH
  chmod +x "$fake_curl"

  local status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_OWNER_QUOTA_PREFLIGHT_JSON_PATH="$TMPDIR/script-owner-quota.json" \
    "$SCRIPT_DIR/owner-quota-preflight.sh" >"$TMPDIR/script-owner-quota.out" || status=$?

  [[ "$status" -ne 0 ]] || fail "owner quota preflight script did not fail on blocked quota"

  status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_OWNER_QUOTA_REPORT_ONLY=true \
  LINGUAFRAME_OWNER_QUOTA_PREFLIGHT_JSON_PATH="$TMPDIR/script-owner-quota-report-only.json" \
    "$SCRIPT_DIR/owner-quota-preflight.sh" >"$TMPDIR/script-owner-quota-report-only.out" || status=$?

  [[ "$status" -eq 0 ]] || fail "owner quota preflight script failed in report-only mode"
}

test_upload_readiness_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_upload_readiness_json "http://example.test" "tears-showcase" "$TMPDIR/upload-readiness.json" >"$TMPDIR/upload-readiness-curl.out"

  local curl_output
  curl_output="$(cat "$TMPDIR/upload-readiness-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/media/uploads/readiness?demoProfileId=tears-showcase"* ]] || fail "upload readiness helper used wrong route"

  cat >"$TMPDIR/upload-readiness.json" <<'JSON'
{
  "overallStatus": "BLOCKED",
  "ownerId": "demo-owner",
  "demoProfileId": "tears-showcase",
  "generatedAt": "2026-06-28T08:00:00Z",
  "checks": [
    {
      "id": "owner-quota",
      "label": "Owner quota",
      "status": "BLOCKED",
      "detail": "Owner active job limit reached.",
      "nextAction": "Wait for active jobs to finish.",
      "blocking": true
    }
  ],
  "requiredActions": [
    "Resolve blocking upload readiness checks before uploading media."
  ],
  "evidenceRoutes": [
    "/api/media/uploads/readiness",
    "/api/media/uploads/preflight"
  ],
  "demoToken": "private-demo-token",
  "sourceObjectKey": "uploads/video-1/source.mp4",
  "localPath": "/Users/example/private.mov",
  "providerPayload": "raw provider payload"
}
JSON

  print_upload_readiness_summary_file "$TMPDIR/upload-readiness.json" >"$TMPDIR/upload-readiness.out"
  local output
  output="$(cat "$TMPDIR/upload-readiness.out")"
  [[ "$output" == *"uploadReadinessOverall=BLOCKED"* ]] || fail "upload readiness summary missed overall"
  [[ "$output" == *"uploadReadinessOwnerId=demo-owner"* ]] || fail "upload readiness summary missed owner"
  [[ "$output" == *"uploadReadinessDemoProfileId=tears-showcase"* ]] || fail "upload readiness summary missed profile"
  [[ "$output" == *"uploadReadinessCheck=BLOCKED:owner-quota:Owner quota"* ]] || fail "upload readiness summary missed check"
  [[ "$output" == *"uploadReadinessRequiredAction=Resolve blocking upload readiness checks before uploading media."* ]] || fail "upload readiness summary missed action"
  [[ "$output" != *"private-demo-token"* ]] || fail "upload readiness summary exposed demo token"
  [[ "$output" != *"uploads/video-1/source.mp4"* ]] || fail "upload readiness summary exposed object key"
  [[ "$output" != *"/Users/example"* ]] || fail "upload readiness summary exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "upload readiness summary exposed provider payload"
}

test_worker_topology_summary_is_metadata_only() {
  cat >"$TMPDIR/runtime-dependencies.json" <<'JSON'
{
  "readiness": {
    "worker": {
      "role": "OPENAI",
      "listenerQueue": "linguaframe.localization.openai.jobs",
      "ffmpegJobQueue": "linguaframe.localization.jobs",
      "ffmpegRoutingKey": "localization.queued",
      "openaiJobQueue": "linguaframe.localization.openai.jobs",
      "openaiRoutingKey": "localization.openai",
      "recommendedCommands": [
        "LINGUAFRAME_WORKER_ROLE=COMBINED docker compose --env-file .env up -d linguaframe-backend",
        "LINGUAFRAME_WORKER_ROLE=OPENAI LINGUAFRAME_RABBITMQ_LISTENER_QUEUE=linguaframe.localization.openai.jobs docker compose --env-file .env up -d linguaframe-backend"
      ],
      "unsafePassword": "linguaframe_dev_password",
      "unsafeToken": "private-demo-token",
      "unsafePath": "/Users/example/private.mov"
    }
  }
}
JSON

  print_worker_topology_summary_file "$TMPDIR/runtime-dependencies.json" >"$TMPDIR/worker-topology.out"
  local output
  output="$(cat "$TMPDIR/worker-topology.out")"
  [[ "$output" == *"workerTopologyRole=OPENAI"* ]] || fail "worker topology summary missed role"
  [[ "$output" == *"workerTopologyListenerQueue=linguaframe.localization.openai.jobs"* ]] || fail "worker topology summary missed listener queue"
  [[ "$output" == *"workerTopologyFfmpegRoute=linguaframe.localization.jobs:localization.queued"* ]] || fail "worker topology summary missed ffmpeg route"
  [[ "$output" == *"workerTopologyOpenaiRoute=linguaframe.localization.openai.jobs:localization.openai"* ]] || fail "worker topology summary missed openai route"
  [[ "$output" == *"workerTopologyCommand=LINGUAFRAME_WORKER_ROLE=OPENAI"* ]] || fail "worker topology summary missed command"
  [[ "$output" != *"linguaframe_dev_password"* ]] || fail "worker topology summary exposed password"
  [[ "$output" != *"private-demo-token"* ]] || fail "worker topology summary exposed token"
  [[ "$output" != *"/Users/example"* ]] || fail "worker topology summary exposed local path"
}

test_upload_readiness_script_exits_on_blocked_state() {
  local fake_curl="$TMPDIR/fake-upload-readiness-curl"
  cat >"$fake_curl" <<'SH'
#!/usr/bin/env bash
output_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
cat >"$output_path" <<'JSON'
{
  "overallStatus": "BLOCKED",
  "ownerId": "demo-owner",
  "demoProfileId": "quick-baseline",
  "generatedAt": "2026-06-28T08:00:00Z",
  "checks": [
    {
      "id": "owner-quota",
      "label": "Owner quota",
      "status": "BLOCKED",
      "detail": "Owner quota blocked upload.",
      "nextAction": "Wait for active jobs to finish.",
      "blocking": true
    }
  ],
  "requiredActions": ["Resolve blocking upload readiness checks before uploading media."],
  "evidenceRoutes": ["/api/media/uploads/readiness"]
}
JSON
SH
  chmod +x "$fake_curl"

  local status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_UPLOAD_READINESS_JSON_PATH="$TMPDIR/script-upload-readiness.json" \
    "$SCRIPT_DIR/upload-readiness.sh" >"$TMPDIR/script-upload-readiness.out" || status=$?

  [[ "$status" -ne 0 ]] || fail "upload readiness script did not fail on blocked status"

  status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_UPLOAD_READINESS_REPORT_ONLY=true \
  LINGUAFRAME_UPLOAD_READINESS_JSON_PATH="$TMPDIR/script-upload-readiness-report-only.json" \
    "$SCRIPT_DIR/upload-readiness.sh" >"$TMPDIR/script-upload-readiness-report-only.out" || status=$?

  [[ "$status" -eq 0 ]] || fail "upload readiness script failed in report-only mode"
}

test_demo_sample_media_catalog_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_sample_media_catalog_json "http://example.test" "$TMPDIR/demo-sample-media-catalog.json" >"$TMPDIR/demo-sample-media-catalog-curl.out"

  local curl_output
  curl_output="$(cat "$TMPDIR/demo-sample-media-catalog-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/operator/demo-sample-media-catalog"* ]] || fail "sample media catalog helper used wrong route"

  cat >"$TMPDIR/demo-sample-media-catalog.json" <<'JSON'
{
  "generatedAt": "2026-06-29T08:00:00Z",
  "overallStatus": "READY",
  "uploadDurationLimitSeconds": 300,
  "recommendedSampleId": "tears-of-steel-casting",
  "items": [
    {
      "id": "tears-of-steel-casting",
      "title": "Tears of Steel casting clip",
      "source": "Blender Studio",
      "sourceUrl": "https://studio.blender.org/films/tears-of-steel/",
      "attribution": "Credit Blender Studio.",
      "licenseGuidance": "Check license.",
      "recommendedUse": "Full local demo.",
      "durationGuidance": "Under 300 seconds.",
      "command": "scripts/demo/docker-e2e-tears-of-steel-full.sh",
      "tags": ["recommended"]
    }
  ],
  "configuredPaths": [
    {
      "envVar": "LINGUAFRAME_TEARS_SAMPLE_PATH",
      "status": "CONFIGURED",
      "filename": "tos_casting-720p.mp4",
      "extension": "mp4",
      "sizeBytes": 1024,
      "message": "Configured sample exists.",
      "fullPathExposed": false
    }
  ],
  "commands": [
    {
      "label": "Run full Tears sample",
      "command": "scripts/demo/docker-e2e-tears-of-steel-full.sh",
      "description": "Run the full demo."
    }
  ],
  "notesMarkdown": "# Catalog",
  "documentationLinks": [],
  "localPath": "/Users/example/Downloads/tos_casting-720p.mp4",
  "demoToken": "private-demo-token",
  "providerPayload": "raw provider payload"
}
JSON

  print_demo_sample_media_catalog_summary_file "$TMPDIR/demo-sample-media-catalog.json" >"$TMPDIR/demo-sample-media-catalog.out"
  local output
  output="$(cat "$TMPDIR/demo-sample-media-catalog.out")"
  [[ "$output" == *"sampleCatalogOverall=READY"* ]] || fail "sample catalog summary missed overall"
  [[ "$output" == *"sampleCatalogRecommended=tears-of-steel-casting"* ]] || fail "sample catalog summary missed recommendation"
  [[ "$output" == *"sampleCatalogDurationLimitSeconds=300"* ]] || fail "sample catalog summary missed duration limit"
  [[ "$output" == *"sampleCatalogPath=LINGUAFRAME_TEARS_SAMPLE_PATH:CONFIGURED:tos_casting-720p.mp4:1024"* ]] || fail "sample catalog summary missed configured path"
  [[ "$output" == *"sampleCatalogCommand=scripts/demo/docker-e2e-tears-of-steel-full.sh"* ]] || fail "sample catalog summary missed command"
  [[ "$output" != *"/Users/example"* ]] || fail "sample catalog summary exposed local path"
  [[ "$output" != *"private-demo-token"* ]] || fail "sample catalog summary exposed demo token"
  [[ "$output" != *"provider payload"* ]] || fail "sample catalog summary exposed provider payload"
}

test_demo_sample_media_catalog_script_exits_on_blocked_state() {
  local fake_curl="$TMPDIR/fake-sample-catalog-curl"
  cat >"$fake_curl" <<'SH'
#!/usr/bin/env bash
output_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
cat >"$output_path" <<'JSON'
{
  "overallStatus": "BLOCKED",
  "uploadDurationLimitSeconds": 300,
  "recommendedSampleId": "tears-of-steel-casting",
  "items": [],
  "configuredPaths": [],
  "commands": [],
  "notesMarkdown": "# Catalog",
  "documentationLinks": []
}
JSON
SH
  chmod +x "$fake_curl"

  local status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_DEMO_SAMPLE_CATALOG_JSON_PATH="$TMPDIR/script-sample-catalog.json" \
    "$SCRIPT_DIR/demo-sample-media-catalog.sh" >"$TMPDIR/script-sample-catalog.out" || status=$?

  [[ "$status" -ne 0 ]] || fail "sample catalog script did not fail on blocked state"

  status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_DEMO_SAMPLE_CATALOG_REPORT_ONLY=true \
  LINGUAFRAME_DEMO_SAMPLE_CATALOG_JSON_PATH="$TMPDIR/script-sample-catalog-report-only.json" \
    "$SCRIPT_DIR/demo-sample-media-catalog.sh" >"$TMPDIR/script-sample-catalog-report-only.out" || status=$?

  [[ "$status" -eq 0 ]] || fail "sample catalog script failed in report-only mode"
}

test_demo_run_launcher_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_launcher_json "http://example.test" "$TMPDIR/demo-run-launcher.json" >"$TMPDIR/demo-run-launcher-curl.out"

  local curl_output
  curl_output="$(cat "$TMPDIR/demo-run-launcher-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/operator/demo-run-launcher"* ]] || fail "launcher helper used wrong route"

  cat >"$TMPDIR/demo-run-launcher.json" <<'JSON'
{
  "generatedAt": "2026-06-29T08:05:00Z",
  "overallStatus": "ATTENTION",
  "recommendedSampleId": "tears-of-steel-casting",
  "recommendedProfileId": "tears-showcase",
  "recommendedNextCommand": "scripts/demo/docker-e2e-tears-of-steel-full.sh",
  "gates": [
    {
      "id": "sample-media",
      "label": "Sample media",
      "status": "READY",
      "detail": "Recommended Tears sample is configured as tos_casting-720p.mp4.",
      "nextAction": "No sample-media action required.",
      "blocking": false
    },
    {
      "id": "paid-provider-check",
      "label": "Paid provider check",
      "status": "ATTENTION",
      "detail": "OpenAI provider mode is enabled, but the live OpenAI connectivity check is skipped.",
      "nextAction": "Run the OpenAI preflight before provider-backed uploads.",
      "blocking": false
    }
  ],
  "commands": [
    {
      "label": "Inspect launcher",
      "command": "scripts/demo/demo-run-launcher.sh",
      "description": "Download this read-only launcher contract."
    },
    {
      "label": "Run full Tears demo",
      "command": "scripts/demo/docker-e2e-tears-of-steel-full.sh",
      "description": "Process the configured complete Tears sample."
    }
  ],
  "expectedEvidence": [
    {
      "label": "Demo presenter pack",
      "path": "/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json",
      "description": "Presenter-facing metadata."
    },
    {
      "label": "Demo run snapshot ZIP",
      "path": "/tmp/linguaframe-demo/full-tears/demo-run-snapshot.zip",
      "description": "Safe reviewer package."
    }
  ],
  "notesMarkdown": "# Launcher",
  "localPath": "/Users/example/Downloads/tos_casting-720p.mp4",
  "demoToken": "private-demo-token",
  "providerPayload": "raw provider payload"
}
JSON

  print_demo_run_launcher_summary_file "$TMPDIR/demo-run-launcher.json" >"$TMPDIR/demo-run-launcher.out"
  local output
  output="$(cat "$TMPDIR/demo-run-launcher.out")"
  [[ "$output" == *"demoRunLauncherOverall=ATTENTION"* ]] || fail "launcher summary missed overall"
  [[ "$output" == *"demoRunLauncherRecommendedSample=tears-of-steel-casting"* ]] || fail "launcher summary missed sample"
  [[ "$output" == *"demoRunLauncherRecommendedProfile=tears-showcase"* ]] || fail "launcher summary missed profile"
  [[ "$output" == *"demoRunLauncherNextCommand=scripts/demo/docker-e2e-tears-of-steel-full.sh"* ]] || fail "launcher summary missed next command"
  [[ "$output" == *"demoRunLauncherGate=ATTENTION:paid-provider-check:Paid provider check:blocking=false"* ]] || fail "launcher summary missed gate"
  [[ "$output" == *"demoRunLauncherCommand=scripts/demo/docker-e2e-tears-of-steel-full.sh"* ]] || fail "launcher summary missed command"
  [[ "$output" == *"demoRunLauncherEvidence=Demo presenter pack:/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json"* ]] || fail "launcher summary missed evidence"
  [[ "$output" != *"/Users/example"* ]] || fail "launcher summary exposed local path"
  [[ "$output" != *"private-demo-token"* ]] || fail "launcher summary exposed demo token"
  [[ "$output" != *"provider payload"* ]] || fail "launcher summary exposed provider payload"
}

test_demo_run_launcher_script_exits_on_blocked_state() {
  local fake_curl="$TMPDIR/fake-demo-run-launcher-curl"
  cat >"$fake_curl" <<'SH'
#!/usr/bin/env bash
output_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
cat >"$output_path" <<'JSON'
{
  "overallStatus": "BLOCKED",
  "recommendedSampleId": "tears-of-steel-casting",
  "recommendedProfileId": "tears-showcase",
  "recommendedNextCommand": "scripts/demo/upload-readiness.sh",
  "gates": [],
  "commands": [],
  "expectedEvidence": [],
  "notesMarkdown": "# Launcher"
}
JSON
SH
  chmod +x "$fake_curl"

  local status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_DEMO_RUN_LAUNCHER_JSON_PATH="$TMPDIR/script-demo-run-launcher.json" \
    "$SCRIPT_DIR/demo-run-launcher.sh" >"$TMPDIR/script-demo-run-launcher.out" || status=$?

  [[ "$status" -ne 0 ]] || fail "launcher script did not fail on blocked state"

  status=0
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_DEMO_RUN_LAUNCHER_REPORT_ONLY=true \
  LINGUAFRAME_DEMO_RUN_LAUNCHER_JSON_PATH="$TMPDIR/script-demo-run-launcher-report-only.json" \
    "$SCRIPT_DIR/demo-run-launcher.sh" >"$TMPDIR/script-demo-run-launcher-report-only.out" || status=$?

  [[ "$status" -eq 0 ]] || fail "launcher script failed in report-only mode"
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

test_download_demo_replay_card_helper_uses_backend_route() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_replay_card_json "http://example.test" "replay job/slash" "$TMPDIR/demo-replay-card.json" >"$TMPDIR/replay-card-curl.out"

  local output
  output="$(cat "$TMPDIR/replay-card-curl.out")"
  [[ "$output" == *"http://example.test/api/jobs/replay%20job%2Fslash/demo-replay-card"* ]] || fail "demo replay card helper used wrong route"
}

test_download_demo_completion_certificate_helper_uses_backend_route() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_completion_certificate_json "http://example.test" "certificate job/slash" "$TMPDIR/demo-completion-certificate.json" >"$TMPDIR/completion-certificate-curl.out"

  local output
  output="$(cat "$TMPDIR/completion-certificate-curl.out")"
  [[ "$output" == *"http://example.test/api/jobs/certificate%20job%2Fslash/demo-completion-certificate"* ]] || fail "demo completion certificate helper used wrong route"
}

test_download_demo_acceptance_gate_helper_uses_backend_route() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_acceptance_gate_json "http://example.test" "acceptance job/slash" "$TMPDIR/demo-acceptance-gate.json" >"$TMPDIR/acceptance-gate-curl.out"

  local output
  output="$(cat "$TMPDIR/acceptance-gate-curl.out")"
  [[ "$output" == *"http://example.test/api/jobs/acceptance%20job%2Fslash/demo-acceptance-gate"* ]] || fail "demo acceptance gate helper used wrong route"
}

test_download_demo_share_sheet_helpers_use_backend_routes() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_share_sheet_json "http://example.test" "share job/slash" "$TMPDIR/demo-share-sheet.json" >"$TMPDIR/share-sheet-json-curl.out"
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_share_sheet_markdown "http://example.test" "share job/slash" "$TMPDIR/demo-share-sheet.md" >"$TMPDIR/share-sheet-md-curl.out"

  local json_output
  json_output="$(cat "$TMPDIR/share-sheet-json-curl.out")"
  [[ "$json_output" == *"http://example.test/api/jobs/share%20job%2Fslash/demo-share-sheet"* ]] || fail "demo share sheet json helper used wrong route"

  local markdown_output
  markdown_output="$(cat "$TMPDIR/share-sheet-md-curl.out")"
  [[ "$markdown_output" == *"http://example.test/api/jobs/share%20job%2Fslash/demo-share-sheet/markdown/download"* ]] || fail "demo share sheet markdown helper used wrong route"
}

test_download_demo_run_monitor_helpers_use_backend_routes() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_monitor_json "http://example.test" "monitor job/slash" "$TMPDIR/demo-run-monitor.json" >"$TMPDIR/run-monitor-json-curl.out"
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_monitor_markdown "http://example.test" "monitor job/slash" "$TMPDIR/demo-run-monitor.md" >"$TMPDIR/run-monitor-md-curl.out"

  local json_output
  json_output="$(cat "$TMPDIR/run-monitor-json-curl.out")"
  [[ "$json_output" == *"http://example.test/api/jobs/monitor%20job%2Fslash/demo-run-monitor"* ]] || fail "demo run monitor json helper used wrong route"

  local markdown_output
  markdown_output="$(cat "$TMPDIR/run-monitor-md-curl.out")"
  [[ "$markdown_output" == *"http://example.test/api/jobs/monitor%20job%2Fslash/demo-run-monitor/markdown/download"* ]] || fail "demo run monitor markdown helper used wrong route"
}

test_download_demo_run_snapshot_helpers_use_backend_routes() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_snapshot_json "http://example.test" "snapshot job/slash" "$TMPDIR/demo-run-snapshot.json" >"$TMPDIR/run-snapshot-json-curl.out"
  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_demo_run_snapshot_zip "http://example.test" "snapshot job/slash" "$TMPDIR/demo-run-snapshot.zip" >"$TMPDIR/run-snapshot-zip-curl.out"

  local json_output
  json_output="$(cat "$TMPDIR/run-snapshot-json-curl.out")"
  [[ "$json_output" == *"http://example.test/api/jobs/snapshot%20job%2Fslash/demo-run-snapshot"* ]] || fail "demo run snapshot json helper used wrong route"

  local zip_output
  zip_output="$(cat "$TMPDIR/run-snapshot-zip-curl.out")"
  [[ "$zip_output" == *"http://example.test/api/jobs/snapshot%20job%2Fslash/demo-run-snapshot/download"* ]] || fail "demo run snapshot zip helper used wrong route"
}

test_print_demo_run_monitor_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-run-monitor.json" <<'JSON'
{
  "jobId": "monitor-job",
  "videoId": "video-demo",
  "status": "PROCESSING",
  "dispatchStatus": "DISPATCHED",
  "generatedAt": "2026-06-29T10:00:00Z",
  "elapsedMs": 540000,
  "currentStage": "TARGET_SUBTITLE_EXPORT",
  "completedStageCount": 2,
  "totalStageCount": 12,
  "failedStageCount": 0,
  "slowestStage": "TRANSCRIPT_SUBTITLE_EXPORT",
  "slowestStageDurationMs": 90000,
  "attentionLevel": "RUNNING",
  "summary": "Localization job is running at TARGET_SUBTITLE_EXPORT.",
  "recommendedNextAction": "Keep watching this monitor.",
  "stages": [
    {
      "stage": "WORKER_RECEIVED",
      "status": "SUCCEEDED",
      "durationMs": 1000,
      "runningForMs": null,
      "attention": "OK",
      "message": "Stage completed."
    },
    {
      "stage": "TARGET_SUBTITLE_EXPORT",
      "status": "STARTED",
      "durationMs": null,
      "runningForMs": 540000,
      "attention": "RUNNING",
      "message": "raw transcript text /Users/example/private.mov sk-test provider payload"
    }
  ],
  "links": [
    {
      "kind": "JOB_DETAIL",
      "label": "Job detail",
      "url": "/api/jobs/monitor-job"
    }
  ],
  "markdown": "raw transcript text /Users/example/private.mov sk-test provider payload"
}
JSON

  print_demo_run_monitor_summary_file "$TMPDIR/demo-run-monitor.json" >"$TMPDIR/demo-run-monitor.out"
  local output
  output="$(cat "$TMPDIR/demo-run-monitor.out")"

  [[ "$output" == *"demoRunMonitorJobId=monitor-job"* ]] || fail "run monitor summary missed job"
  [[ "$output" == *"demoRunMonitorVideoId=video-demo"* ]] || fail "run monitor summary missed video"
  [[ "$output" == *"demoRunMonitorStatus=PROCESSING"* ]] || fail "run monitor summary missed status"
  [[ "$output" == *"demoRunMonitorAttentionLevel=RUNNING"* ]] || fail "run monitor summary missed attention"
  [[ "$output" == *"demoRunMonitorCurrentStage=TARGET_SUBTITLE_EXPORT"* ]] || fail "run monitor summary missed current stage"
  [[ "$output" == *"demoRunMonitorCompletedStageCount=2"* ]] || fail "run monitor summary missed completed count"
  [[ "$output" == *"demoRunMonitorSlowestStage=TRANSCRIPT_SUBTITLE_EXPORT"* ]] || fail "run monitor summary missed slowest stage"
  [[ "$output" == *"demoRunMonitorStage=TARGET_SUBTITLE_EXPORT:STARTED:attention=RUNNING:durationMs=N/A:runningForMs=540000"* ]] || fail "run monitor summary missed running stage"
  [[ "$output" == *"demoRunMonitorTerminal=false"* ]] || fail "run monitor summary missed terminal false"
  [[ "$output" != *"raw transcript text"* ]] || fail "run monitor summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "run monitor summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "run monitor summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "run monitor summary exposed provider payload"
}

test_print_demo_run_snapshot_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-run-snapshot.json" <<'JSON'
{
  "jobId": "snapshot-job",
  "videoId": "video-demo",
  "targetLanguage": "zh-CN",
  "demoProfileId": "tears-showcase",
  "generatedAt": "2026-06-29T12:00:00Z",
  "readiness": "READY",
  "headline": "tears-showcase demo to zh-CN",
  "summary": "Offline reviewer snapshot.",
  "sections": [
    {
      "kind": "INDEX_HTML",
      "title": "Offline index",
      "status": "READY",
      "filename": "index.html",
      "summary": "Self-contained index."
    },
    {
      "kind": "SHARE_SHEET",
      "title": "Share sheet",
      "status": "READY",
      "filename": "demo-share-sheet.md",
      "summary": "raw transcript text /Users/example/private.mov sk-test provider payload"
    }
  ],
  "packageEntries": [
    "index.html",
    "manifest.json",
    "README.md",
    "demo-share-sheet.md"
  ],
  "links": [
    {
      "kind": "DEMO_RUN_SNAPSHOT_DOWNLOAD",
      "label": "Static demo snapshot ZIP",
      "url": "/api/jobs/snapshot-job/demo-run-snapshot/download"
    }
  ],
  "exclusionPolicy": [
    "media bytes",
    "transcript content"
  ],
  "markdown": "raw transcript text /Users/example/private.mov sk-test provider payload"
}
JSON

  print_demo_run_snapshot_summary_file "$TMPDIR/demo-run-snapshot.json" >"$TMPDIR/demo-run-snapshot.out"
  local output
  output="$(cat "$TMPDIR/demo-run-snapshot.out")"

  [[ "$output" == *"demoRunSnapshotJobId=snapshot-job"* ]] || fail "snapshot summary missed job"
  [[ "$output" == *"demoRunSnapshotVideoId=video-demo"* ]] || fail "snapshot summary missed video"
  [[ "$output" == *"demoRunSnapshotReadiness=READY"* ]] || fail "snapshot summary missed readiness"
  [[ "$output" == *"demoRunSnapshotHeadline=tears-showcase demo to zh-CN"* ]] || fail "snapshot summary missed headline"
  [[ "$output" == *"demoRunSnapshotSectionCount=2"* ]] || fail "snapshot summary missed section count"
  [[ "$output" == *"demoRunSnapshotPackageEntryCount=4"* ]] || fail "snapshot summary missed entry count"
  [[ "$output" == *"demoRunSnapshotSection=INDEX_HTML:index.html:READY"* ]] || fail "snapshot summary missed index section"
  [[ "$output" == *"demoRunSnapshotEntry=demo-share-sheet.md"* ]] || fail "snapshot summary missed package entry"
  [[ "$output" == *"demoRunSnapshotLink=DEMO_RUN_SNAPSHOT_DOWNLOAD:/api/jobs/snapshot-job/demo-run-snapshot/download"* ]] || fail "snapshot summary missed download link"
  [[ "$output" != *"raw transcript text"* ]] || fail "snapshot summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "snapshot summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "snapshot summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "snapshot summary exposed provider payload"
}

test_print_demo_share_sheet_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-share-sheet.json" <<'JSON'
{
  "jobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-28T12:00:00Z",
  "readiness": "READY",
  "headline": "tears-showcase demo to zh-CN",
  "summary": "Completed job with safe metadata.",
  "outcomeBullets": [
    "Status: COMPLETED",
    "Quality score: 91 (GOOD)",
    "raw transcript text /Users/example/private.mov sk-test provider payload"
  ],
  "recommendedNextAction": "Open the demo run package.",
  "links": [
    {
      "kind": "DEMO_RUN_PACKAGE",
      "label": "Demo run package",
      "url": "/api/jobs/showcase-job/demo-run-package/download"
    },
    {
      "kind": "EVIDENCE_BUNDLE",
      "label": "Evidence bundle",
      "url": "/api/jobs/showcase-job/evidence/bundle/download"
    }
  ],
  "markdown": "raw transcript text /Users/example/private.mov sk-test provider payload"
}
JSON

  print_demo_share_sheet_summary_file "$TMPDIR/demo-share-sheet.json" >"$TMPDIR/demo-share-sheet.out"
  local output
  output="$(cat "$TMPDIR/demo-share-sheet.out")"

  [[ "$output" == *"demoShareSheetJobId=showcase-job"* ]] || fail "share sheet summary missed job"
  [[ "$output" == *"demoShareSheetVideoId=video-demo"* ]] || fail "share sheet summary missed video"
  [[ "$output" == *"demoShareSheetReadiness=READY"* ]] || fail "share sheet summary missed readiness"
  [[ "$output" == *"demoShareSheetHeadline=tears-showcase demo to zh-CN"* ]] || fail "share sheet summary missed headline"
  [[ "$output" == *"demoShareSheetRecommendedNextAction=Open the demo run package."* ]] || fail "share sheet summary missed next action"
  [[ "$output" == *"demoShareSheetOutcome=Status: COMPLETED"* ]] || fail "share sheet summary missed status outcome"
  [[ "$output" == *"demoShareSheetLink=DEMO_RUN_PACKAGE:/api/jobs/showcase-job/demo-run-package/download"* ]] || fail "share sheet summary missed package link"
  [[ "$output" != *"raw transcript text"* ]] || fail "share sheet summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "share sheet summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "share sheet summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "share sheet summary exposed provider payload"
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

test_print_demo_replay_card_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-replay-card.json" <<'JSON'
{
  "jobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-29T10:15:30Z",
  "headline": "tears-showcase replay card to zh-CN",
  "readiness": "READY",
  "status": "COMPLETED",
  "targetLanguage": "zh-CN",
  "demoProfileId": "tears-showcase",
  "qualityScore": 91,
  "qualityVerdict": "GOOD",
  "modelCallCount": 3,
  "providerCacheHitCount": 1,
  "artifactCacheHitCount": 0,
  "estimatedCostUsd": 0.000090,
  "recommendedBaselineJobId": "baseline-job",
  "bestQualityJobId": "showcase-job",
  "lowestCostJobId": "baseline-job",
  "settings": [
    {
      "key": "targetLanguage",
      "label": "Target language",
      "value": "zh-CN"
    },
    {
      "key": "unsafe",
      "label": "Unsafe detail",
      "value": "raw transcript text /Users/example/private.mov sk-test provider payload"
    }
  ],
  "commands": [
    {
      "kind": "EXPORT_REPLAY_CARD",
      "label": "Export this replay card",
      "command": "LINGUAFRAME_DEMO_JOB_ID=showcase-job scripts/demo/demo-replay-card.sh",
      "note": "Writes JSON."
    }
  ],
  "links": [
    {
      "kind": "DEMO_RUN_PACKAGE",
      "label": "Demo run package",
      "url": "/api/jobs/showcase-job/demo-run-package/download"
    }
  ],
  "safetyNotes": [
    "Metadata only: no API keys, object storage credentials, raw prompts, or media bytes are included."
  ]
}
JSON

  print_demo_replay_card_summary_file "$TMPDIR/demo-replay-card.json" >"$TMPDIR/demo-replay-card.out"
  local output
  output="$(cat "$TMPDIR/demo-replay-card.out")"

  [[ "$output" == *"demoReplayCardJobId=showcase-job"* ]] || fail "replay card summary missed job"
  [[ "$output" == *"demoReplayCardVideoId=video-demo"* ]] || fail "replay card summary missed video"
  [[ "$output" == *"demoReplayCardReadiness=READY"* ]] || fail "replay card summary missed readiness"
  [[ "$output" == *"demoReplayCardRecommendedBaselineJobId=baseline-job"* ]] || fail "replay card summary missed baseline"
  [[ "$output" == *"demoReplayCardCommand=EXPORT_REPLAY_CARD:LINGUAFRAME_DEMO_JOB_ID=showcase-job scripts/demo/demo-replay-card.sh"* ]] || fail "replay card summary missed command"
  [[ "$output" == *"demoReplayCardLink=DEMO_RUN_PACKAGE:/api/jobs/showcase-job/demo-run-package/download"* ]] || fail "replay card summary missed link"
  [[ "$output" != *"raw transcript text"* ]] || fail "replay card summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "replay card summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "replay card summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "replay card summary exposed provider payload"
}

test_print_demo_completion_certificate_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-completion-certificate.json" <<'JSON'
{
  "jobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-29T10:45:00Z",
  "certificateStatus": "READY",
  "jobStatus": "COMPLETED",
  "targetLanguage": "zh-CN",
  "demoProfileId": "tears-showcase",
  "headline": "tears-showcase completion certificate",
  "summary": "raw transcript text /Users/example/private.mov sk-test provider payload",
  "recommendedNextAction": "Use the completion certificate and demo run package as final demo handoff evidence.",
  "recommendedBaselineJobId": "baseline-job",
  "bestQualityJobId": "showcase-job",
  "lowestCostJobId": "baseline-job",
  "checks": [
    {
      "key": "JOB_COMPLETED",
      "label": "Job completed",
      "status": "PASS",
      "detail": "Job status is COMPLETED.",
      "blocking": false
    },
    {
      "key": "UNSAFE_FIXTURE",
      "label": "Unsafe fixture",
      "status": "WARN",
      "detail": "raw transcript text /Users/example/private.mov sk-test provider payload",
      "blocking": false
    }
  ],
  "sections": [
    {
      "key": "REPRODUCIBILITY",
      "title": "Reproducibility",
      "status": "READY",
      "facts": [
        "raw transcript text /Users/example/private.mov sk-test provider payload"
      ]
    }
  ],
  "links": [
    {
      "kind": "CERTIFICATE_JSON",
      "label": "Completion certificate JSON",
      "url": "/api/jobs/showcase-job/demo-completion-certificate"
    },
    {
      "kind": "UNSAFE_LINK",
      "label": "Unsafe link",
      "url": "/Users/example/private.mov?token=sk-test&payload=provider payload"
    }
  ],
  "safetyNotes": [
    "Metadata-only certificate: only IDs, status, readiness, costs, counts, safe routes, and replay commands are included."
  ]
}
JSON

  print_demo_completion_certificate_summary_file "$TMPDIR/demo-completion-certificate.json" >"$TMPDIR/demo-completion-certificate.out"
  local output
  output="$(cat "$TMPDIR/demo-completion-certificate.out")"

  [[ "$output" == *"demoCompletionCertificateJobId=showcase-job"* ]] || fail "completion certificate summary missed job"
  [[ "$output" == *"demoCompletionCertificateVideoId=video-demo"* ]] || fail "completion certificate summary missed video"
  [[ "$output" == *"demoCompletionCertificateStatus=READY"* ]] || fail "completion certificate summary missed status"
  [[ "$output" == *"demoCompletionCertificateCheck=JOB_COMPLETED:PASS:blocking=false"* ]] || fail "completion certificate summary missed check"
  [[ "$output" == *"demoCompletionCertificateSection=REPRODUCIBILITY:READY"* ]] || fail "completion certificate summary missed section"
  [[ "$output" == *"demoCompletionCertificateLink=CERTIFICATE_JSON:/api/jobs/showcase-job/demo-completion-certificate"* ]] || fail "completion certificate summary missed link"
  [[ "$output" != *"raw transcript text"* ]] || fail "completion certificate summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "completion certificate summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "completion certificate summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "completion certificate summary exposed provider payload"
}

test_print_demo_acceptance_gate_summary_is_metadata_only() {
  cat >"$TMPDIR/demo-acceptance-gate.json" <<'JSON'
{
  "jobId": "showcase-job",
  "videoId": "video-demo",
  "generatedAt": "2026-06-29T11:15:00Z",
  "gateStatus": "READY",
  "jobStatus": "COMPLETED",
  "targetLanguage": "zh-CN",
  "demoProfileId": "tears-showcase",
  "headline": "tears-showcase acceptance gate",
  "summary": "raw transcript text /Users/example/private.mov sk-test provider payload",
  "recommendedNextAction": "Present this run using the completion certificate, demo run package, and snapshot.",
  "checks": [
    {
      "key": "JOB_COMPLETED",
      "label": "Job completed",
      "status": "PASS",
      "detail": "Job status is COMPLETED.",
      "required": true
    },
    {
      "key": "BASELINE_RECOMMENDED",
      "label": "Recommended baseline",
      "status": "WARN",
      "detail": "No same-source baseline was found.",
      "required": false
    },
    {
      "key": "UNSAFE_FIXTURE",
      "label": "Unsafe fixture",
      "status": "WARN",
      "detail": "raw transcript text /Users/example/private.mov sk-test provider payload",
      "required": false
    }
  ],
  "evidence": [
    {
      "key": "MEDIA_OUTPUT_COUNT",
      "label": "Playable media outputs",
      "value": "1",
      "status": "READY"
    },
    {
      "key": "UNSAFE_EVIDENCE",
      "label": "Unsafe evidence",
      "value": "raw transcript text /Users/example/private.mov sk-test provider payload",
      "status": "ATTENTION"
    }
  ],
  "links": [
    {
      "kind": "ACCEPTANCE_GATE_JSON",
      "label": "Demo acceptance gate JSON",
      "url": "/api/jobs/showcase-job/demo-acceptance-gate"
    },
    {
      "kind": "UNSAFE_LINK",
      "label": "Unsafe link",
      "url": "/Users/example/private.mov?token=sk-test&payload=provider payload"
    }
  ],
  "safetyNotes": [
    "Metadata-only gate: only IDs, status, counts, scores, costs, safe routes, and readiness labels are included."
  ]
}
JSON

  print_demo_acceptance_gate_summary_file "$TMPDIR/demo-acceptance-gate.json" >"$TMPDIR/demo-acceptance-gate.out"
  local output
  output="$(cat "$TMPDIR/demo-acceptance-gate.out")"

  [[ "$output" == *"demoAcceptanceGateJobId=showcase-job"* ]] || fail "acceptance gate summary missed job"
  [[ "$output" == *"demoAcceptanceGateVideoId=video-demo"* ]] || fail "acceptance gate summary missed video"
  [[ "$output" == *"demoAcceptanceGateStatus=READY"* ]] || fail "acceptance gate summary missed status"
  [[ "$output" == *"demoAcceptanceGateJobStatus=COMPLETED"* ]] || fail "acceptance gate summary missed job status"
  [[ "$output" == *"demoAcceptanceGateProfile=tears-showcase"* ]] || fail "acceptance gate summary missed profile"
  [[ "$output" == *"demoAcceptanceGateNextAction=Present this run using the completion certificate, demo run package, and snapshot."* ]] || fail "acceptance gate summary missed next action"
  [[ "$output" == *"demoAcceptanceGateCheck=JOB_COMPLETED:PASS:required=true"* ]] || fail "acceptance gate summary missed required check"
  [[ "$output" == *"demoAcceptanceGateCheck=BASELINE_RECOMMENDED:WARN:required=false"* ]] || fail "acceptance gate summary missed warning check"
  [[ "$output" == *"demoAcceptanceGateEvidence=MEDIA_OUTPUT_COUNT:1:READY"* ]] || fail "acceptance gate summary missed evidence"
  [[ "$output" == *"demoAcceptanceGateLink=ACCEPTANCE_GATE_JSON:/api/jobs/showcase-job/demo-acceptance-gate"* ]] || fail "acceptance gate summary missed link"
  [[ "$output" == *"demoAcceptanceGateSafetyNote=Metadata-only gate: only IDs, status, counts, scores, costs, safe routes, and readiness labels are included."* ]] || fail "acceptance gate summary missed safety note"
  [[ "$output" != *"raw transcript text"* ]] || fail "acceptance gate summary exposed transcript"
  [[ "$output" != *"/Users/example"* ]] || fail "acceptance gate summary exposed local path"
  [[ "$output" != *"sk-test"* ]] || fail "acceptance gate summary exposed token"
  [[ "$output" != *"provider payload"* ]] || fail "acceptance gate summary exposed provider payload"
}

test_demo_acceptance_gate_script_requires_job_id() {
  local status=0
  LINGUAFRAME_DEMO_BASE_URL="http://example.test" \
  LINGUAFRAME_DEMO_ACCEPTANCE_GATE_OUTPUT_DIR="$TMPDIR/demo-acceptance-gate-missing-job" \
    bash "$SCRIPT_DIR/demo-acceptance-gate.sh" >"$TMPDIR/demo-acceptance-gate-missing-job.out" 2>"$TMPDIR/demo-acceptance-gate-missing-job.err" || status=$?

  [[ "$status" -eq 2 ]] || fail "demo acceptance gate script did not exit 2 when job id is missing"
  local error_output
  error_output="$(cat "$TMPDIR/demo-acceptance-gate-missing-job.err")"
  [[ "$error_output" == *"Missing LINGUAFRAME_DEMO_JOB_ID."* ]] || fail "demo acceptance gate script missed missing job message"
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
    "artifactId": "dubbed-video",
    "jobId": "job-media",
    "type": "DUBBED_VIDEO",
    "filename": "dubbed-video.mp4",
    "contentType": "video/mp4",
    "sizeBytes": 84000,
    "contentSha256": "789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456",
    "cacheHit": false,
    "sourceArtifactId": null,
    "createdAt": "2026-06-28T11:21:30Z",
    "unsafePayload": "provider request payload sk-test"
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

  [[ "$output" == *"mediaDeliveryReadyCount=4"* ]] || fail "media delivery summary missed ready count"
  [[ "$output" == *"mediaDeliveryArtifact=DUBBING_AUDIO:dubbing-audio.mp3:audio/mpeg:Generated"* ]] || fail "media delivery summary missed audio artifact"
  [[ "$output" == *"mediaDeliveryArtifact=BURNED_VIDEO:burned-video.mp4:video/mp4:Reused"* ]] || fail "media delivery summary missed generated video artifact"
  [[ "$output" == *"mediaDeliveryArtifact=DUBBED_VIDEO:dubbed-video.mp4:video/mp4:Generated"* ]] || fail "media delivery summary missed dubbed video artifact"
  [[ "$output" == *"mediaDeliveryArtifact=REVIEWED_BURNED_VIDEO:reviewed-burned-video.mp4:video/mp4:Generated"* ]] || fail "media delivery summary missed reviewed video artifact"
  [[ "$output" != *"raw transcript text"* ]] || fail "media delivery summary exposed transcript"
  [[ "$output" != *"raw subtitle text"* ]] || fail "media delivery summary exposed subtitle"
  [[ "$output" != *"job-artifacts/private"* ]] || fail "media delivery summary exposed object key"
  [[ "$output" != *"/Users/example/private.mov"* ]] || fail "media delivery summary exposed local path"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "media delivery summary exposed token"
  [[ "$output" != *"provider request payload"* ]] || fail "media delivery summary exposed provider payload"
  [[ "$output" != *"sk-test"* ]] || fail "media delivery summary exposed API key marker"
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

test_print_demo_run_snapshot_package_summary_validates_zip_and_secrets() {
  python3 - "$TMPDIR/demo-run-snapshot.zip" "$TMPDIR/unsafe-demo-run-snapshot.zip" <<'PY'
import json
import sys
import zipfile

safe_path = sys.argv[1]
unsafe_path = sys.argv[2]
manifest = {
    "jobId": "job-demo-run-snapshot",
    "videoId": "video-demo-run-snapshot",
    "targetLanguage": "zh-CN",
    "readiness": "READY",
}
entries = {
    "index.html": "<!doctype html><title>LinguaFrame Demo Snapshot</title>",
    "manifest.json": json.dumps(manifest, separators=(",", ":")),
    "README.md": "# LinguaFrame Demo Snapshot\n- Job: job-demo-run-snapshot\n",
    "demo-share-sheet.md": "# Share sheet\n- Job: job-demo-run-snapshot\n",
    "demo-share-sheet.json": json.dumps({"jobId": "job-demo-run-snapshot"}),
    "demo-run-monitor.md": "# Monitor\n- Job: job-demo-run-snapshot\n",
    "demo-run-monitor.json": json.dumps({"jobId": "job-demo-run-snapshot"}),
    "presenter-pack.json": json.dumps({"anchorJobId": "job-demo-run-snapshot"}),
    "delivery-manifest.md": "# Delivery manifest\n- Job: job-demo-run-snapshot\n",
    "diagnostics.json": json.dumps({"jobId": "job-demo-run-snapshot"}),
    "evidence.md": "# Evidence\n- Job: job-demo-run-snapshot\n",
}
with zipfile.ZipFile(safe_path, "w") as archive:
    for name, content in entries.items():
        archive.writestr(name, content)

unsafe_entries = dict(entries)
unsafe_entries["index.html"] = "provider request payload sk-test /Users/example/job-artifacts/raw.json"
with zipfile.ZipFile(unsafe_path, "w") as archive:
    for name, content in unsafe_entries.items():
        archive.writestr(name, content)
PY

  print_demo_run_snapshot_package_summary "$TMPDIR/demo-run-snapshot.zip" "job-demo-run-snapshot" >"$TMPDIR/demo-run-snapshot-package.out"
  local output
  output="$(cat "$TMPDIR/demo-run-snapshot-package.out")"

  [[ "$output" == *"demoRunSnapshotPackageJobId=job-demo-run-snapshot"* ]] || fail "demo run snapshot package summary missed job id"
  [[ "$output" == *"demoRunSnapshotPackageEntryCount=11"* ]] || fail "demo run snapshot package summary missed entry count"

  if print_demo_run_snapshot_package_summary "$TMPDIR/unsafe-demo-run-snapshot.zip" "job-demo-run-snapshot" >"$TMPDIR/unsafe-demo-run-snapshot.out" 2>&1; then
    fail "demo run snapshot package summary accepted unsafe ZIP"
  fi
  [[ "$(cat "$TMPDIR/unsafe-demo-run-snapshot.out")" == *"forbidden sensitive string"* ]] || fail "demo run snapshot package unsafe failure was not explicit"
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

test_private_demo_launch_rehearsal_helpers_are_metadata_only() {
  cat >"$TMPDIR/private-demo-launch-rehearsal.json" <<'JSON'
{
  "generatedAt": "2026-06-28T08:30:00Z",
  "overallStatus": "ATTENTION",
  "readyCount": 8,
  "attentionCount": 2,
  "blockedCount": 0,
  "recommendedNextStepId": "openai-preflight",
  "steps": [
    {
      "id": "deploy-preflight",
      "title": "Deployment preflight",
      "status": "READY",
      "detail": "The backend runtime contract includes launch rehearsal.",
      "command": "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh",
      "evidencePath": "/api/runtime/dependencies",
      "nextAction": "Run before starting the stack.",
      "blocking": false
    },
    {
      "id": "openai-preflight",
      "title": "OpenAI provider preflight",
      "status": "ATTENTION",
      "detail": "Provider readiness needs manual confirmation.",
      "command": "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-demo-preflight.sh",
      "evidencePath": "/api/runtime/live-checks",
      "nextAction": "Run only when paid provider access should be proven.",
      "blocking": false,
      "unsafeToken": "OPENAI_API_KEY",
      "unsafePath": "/Users/example/private.mov",
      "unsafePayload": "provider payload"
    }
  ],
  "evidenceDownloads": [
    "/api/operator/private-demo/operations",
    "/api/jobs/{jobId}/demo-presenter-pack"
  ],
  "rehearsalNotesMarkdown": "# LinguaFrame Private Demo Launch Rehearsal\nraw transcript text /Users/example/private.mov private-demo-token provider payload"
}
JSON

  print_private_demo_launch_rehearsal_summary_file \
    "$TMPDIR/private-demo-launch-rehearsal.json" \
    >"$TMPDIR/private-demo-launch-rehearsal.out"
  local summary
  summary="$(cat "$TMPDIR/private-demo-launch-rehearsal.out")"
  [[ "$summary" == *"privateDemoLaunchOverall=ATTENTION"* ]] || fail "launch summary missed overall"
  [[ "$summary" == *"privateDemoLaunchRecommendedNextStepId=openai-preflight"* ]] || fail "launch summary missed next step"
  [[ "$summary" == *"privateDemoLaunchStep=ATTENTION:openai-preflight:OpenAI provider preflight:blocking=false"* ]] || fail "launch summary missed step"
  [[ "$summary" != *"OPENAI_API_KEY"* ]] || fail "launch summary exposed API key"
  [[ "$summary" != *"/Users/example"* ]] || fail "launch summary exposed local path"
  [[ "$summary" != *"provider payload"* ]] || fail "launch summary exposed provider payload"

  write_private_demo_launch_rehearsal_report \
    "$TMPDIR/private-demo-launch-rehearsal.json" \
    "$TMPDIR/private-demo-launch-rehearsal.md"
  local output
  output="$(cat "$TMPDIR/private-demo-launch-rehearsal.md")"
  [[ "$output" == *"# LinguaFrame Private Demo Launch Rehearsal"* ]] || fail "launch report missed title"
  [[ "$output" == *"- Overall: ATTENTION"* ]] || fail "launch report missed overall"
  [[ "$output" == *"- Recommended next step: openai-preflight"* ]] || fail "launch report missed next step"
  [[ "$output" == *"LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-demo-preflight.sh"* ]] || fail "launch report missed command"
  [[ "$output" == *"/api/jobs/{jobId}/demo-presenter-pack"* ]] || fail "launch report missed evidence route"
  [[ "$output" != *"private-demo-token"* ]] || fail "launch report exposed demo token"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "launch report exposed API key"
  [[ "$output" != *"/Users/example"* ]] || fail "launch report exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "launch report exposed provider payload"
  [[ "$output" != *"raw transcript text"* ]] || fail "launch report exposed transcript"
}

test_private_demo_evidence_gallery_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_private_demo_evidence_gallery_json "http://example.test" "$TMPDIR/private-demo-evidence-gallery-route.json" 7 \
    >"$TMPDIR/private-demo-evidence-gallery-curl.out"
  local curl_output
  curl_output="$(cat "$TMPDIR/private-demo-evidence-gallery-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/operator/private-demo/evidence-gallery?limit=7"* ]] || fail "evidence gallery helper used wrong route"

  cat >"$TMPDIR/private-demo-evidence-gallery.json" <<'JSON'
{
  "generatedAt": "2026-06-28T08:45:00Z",
  "overallStatus": "READY",
  "completedJobCount": 2,
  "handoffReadyCount": 1,
  "recommendedJobId": "job-gallery-best",
  "jobs": [
    {
      "jobId": "job-gallery-best",
      "videoId": "video-gallery",
      "filename": "tears-demo.mp4",
      "targetLanguage": "zh-CN",
      "demoProfileId": "tears-showcase",
      "status": "COMPLETED",
      "qualityScore": 94,
      "qualityVerdict": "EXCELLENT",
      "estimatedCostUsd": 0.40,
      "modelCallCount": 5,
      "providerCacheHitCount": 1,
      "handoffReady": true,
      "presenterPackReady": true,
      "recommended": true,
      "attentionReasons": [],
      "downloads": [
        {
          "label": "Demo run package",
          "href": "/api/jobs/job-gallery-best/demo-run-package/download",
          "description": "Complete safe demo run package."
        },
        {
          "label": "AI audit package",
          "href": "/api/jobs/job-gallery-best/ai-audit-package/download",
          "description": "Prompt and model-call audit package."
        }
      ],
      "unsafeToken": "OPENAI_API_KEY",
      "unsafePath": "/Users/example/private.mov",
      "unsafePayload": "provider payload"
    }
  ],
  "galleryDownloads": [
    {
      "label": "Demo run package",
      "href": "/api/jobs/job-gallery-best/demo-run-package/download",
      "description": "Complete safe demo run package."
    }
  ],
  "galleryNotesMarkdown": "# LinguaFrame Private Demo Evidence Gallery\nraw transcript text /Users/example/private.mov private-demo-token provider payload"
}
JSON

  print_private_demo_evidence_gallery_summary_file \
    "$TMPDIR/private-demo-evidence-gallery.json" \
    >"$TMPDIR/private-demo-evidence-gallery.out"
  local summary
  summary="$(cat "$TMPDIR/private-demo-evidence-gallery.out")"
  [[ "$summary" == *"privateDemoEvidenceGalleryOverall=READY"* ]] || fail "gallery summary missed overall"
  [[ "$summary" == *"privateDemoEvidenceGalleryRecommendedJobId=job-gallery-best"* ]] || fail "gallery summary missed recommendation"
  [[ "$summary" == *"privateDemoEvidenceGalleryJob=job-gallery-best:tears-showcase:handoff=true:quality=94"* ]] || fail "gallery summary missed job"
  [[ "$summary" == *"privateDemoEvidenceGalleryDownload=Demo run package:/api/jobs/job-gallery-best/demo-run-package/download"* ]] || fail "gallery summary missed download"
  [[ "$summary" != *"OPENAI_API_KEY"* ]] || fail "gallery summary exposed API key"
  [[ "$summary" != *"/Users/example"* ]] || fail "gallery summary exposed local path"
  [[ "$summary" != *"provider payload"* ]] || fail "gallery summary exposed provider payload"

  write_private_demo_evidence_gallery_report \
    "$TMPDIR/private-demo-evidence-gallery.json" \
    "$TMPDIR/private-demo-evidence-gallery.md"
  local output
  output="$(cat "$TMPDIR/private-demo-evidence-gallery.md")"
  [[ "$output" == *"# LinguaFrame Private Demo Evidence Gallery"* ]] || fail "gallery report missed title"
  [[ "$output" == *"- Overall: READY"* ]] || fail "gallery report missed overall"
  [[ "$output" == *"- Recommended job: job-gallery-best"* ]] || fail "gallery report missed recommendation"
  [[ "$output" == *"tears-showcase"* ]] || fail "gallery report missed profile"
  [[ "$output" == *"/api/jobs/job-gallery-best/demo-run-package/download"* ]] || fail "gallery report missed package route"
  [[ "$output" != *"private-demo-token"* ]] || fail "gallery report exposed demo token"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "gallery report exposed API key"
  [[ "$output" != *"/Users/example"* ]] || fail "gallery report exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "gallery report exposed provider payload"
  [[ "$output" != *"raw transcript text"* ]] || fail "gallery report exposed transcript"
}

test_private_demo_run_archive_helpers_are_metadata_only() {
  local fake_curl
  fake_curl="$(fake_curl_bin)"

  LINGUAFRAME_DEMO_CURL_BIN="$fake_curl" \
    download_private_demo_run_archive_json "http://example.test" "$TMPDIR/private-demo-run-archive-route.json" \
    >"$TMPDIR/private-demo-run-archive-curl.out"
  local curl_output
  curl_output="$(cat "$TMPDIR/private-demo-run-archive-curl.out")"
  [[ "$curl_output" == *"http://example.test/api/operator/private-demo/run-archive"* ]] || fail "run archive helper used wrong route"

  cat >"$TMPDIR/private-demo-run-archive.json" <<'JSON'
{
  "generatedAt": "2026-06-28T08:50:00Z",
  "overallStatus": "READY",
  "recommendedJobId": "job-gallery-best",
  "recommendedVideoId": "video-gallery",
  "recommendedProfileId": "tears-showcase",
  "recommendedReadiness": "READY",
  "operationsOverallStatus": "READY",
  "launchOverallStatus": "READY",
  "launchRecommendedNextStep": "operations-report-export",
  "galleryCompletedJobCount": 2,
  "galleryHandoffReadyCount": 1,
  "candidates": [
    {
      "jobId": "job-gallery-best",
      "videoId": "video-gallery",
      "filename": "tears-demo.mp4",
      "profileId": "tears-showcase",
      "status": "COMPLETED",
      "readiness": "READY",
      "qualityScore": 94,
      "estimatedCostUsd": 0.40,
      "modelCallCount": 5,
      "providerCacheHitCount": 1,
      "handoffReady": true,
      "roles": ["RECOMMENDED", "HANDOFF_READY"],
      "unsafeToken": "OPENAI_API_KEY",
      "unsafePath": "/Users/example/private.mov",
      "unsafePayload": "provider payload"
    }
  ],
  "archiveLinks": [
    {
      "label": "Operations readiness",
      "href": "/api/operator/private-demo/operations",
      "description": "Private demo readiness."
    },
    {
      "label": "Demo run package",
      "href": "/api/jobs/job-gallery-best/demo-run-package/download",
      "description": "Complete safe demo run package."
    }
  ],
  "archiveNotesMarkdown": "# LinguaFrame Private Demo Run Archive\n- Overall: READY\n- Recommended job: job-gallery-best\n- Demo run package: /api/jobs/job-gallery-best/demo-run-package/download\nraw transcript text /Users/example/private.mov private-demo-token provider payload"
}
JSON

  print_private_demo_run_archive_summary_file \
    "$TMPDIR/private-demo-run-archive.json" \
    >"$TMPDIR/private-demo-run-archive.out"
  local summary
  summary="$(cat "$TMPDIR/private-demo-run-archive.out")"
  [[ "$summary" == *"privateDemoRunArchiveOverall=READY"* ]] || fail "run archive summary missed overall"
  [[ "$summary" == *"privateDemoRunArchiveRecommendedJobId=job-gallery-best"* ]] || fail "run archive summary missed recommendation"
  [[ "$summary" == *"privateDemoRunArchiveCompletedJobCount=2"* ]] || fail "run archive summary missed completed count"
  [[ "$summary" == *"privateDemoRunArchiveHandoffReadyCount=1"* ]] || fail "run archive summary missed handoff count"
  [[ "$summary" == *"privateDemoRunArchiveCandidate=job-gallery-best:tears-showcase:COMPLETED:READY:quality=94:cost=0.4:modelCalls=5:providerCacheHits=1:handoffReady=true"* ]] || fail "run archive summary missed candidate"
  [[ "$summary" == *"privateDemoRunArchiveLink=Demo run package:/api/jobs/job-gallery-best/demo-run-package/download"* ]] || fail "run archive summary missed link"
  [[ "$summary" != *"OPENAI_API_KEY"* ]] || fail "run archive summary exposed API key"
  [[ "$summary" != *"/Users/example"* ]] || fail "run archive summary exposed local path"
  [[ "$summary" != *"provider payload"* ]] || fail "run archive summary exposed provider payload"

  write_private_demo_run_archive_report \
    "$TMPDIR/private-demo-run-archive.json" \
    "$TMPDIR/private-demo-run-archive.md"
  local output
  output="$(cat "$TMPDIR/private-demo-run-archive.md")"
  [[ "$output" == *"# LinguaFrame Private Demo Run Archive"* ]] || fail "run archive report missed title"
  [[ "$output" == *"- Overall: READY"* ]] || fail "run archive report missed overall"
  [[ "$output" == *"- Recommended job: job-gallery-best"* ]] || fail "run archive report missed recommendation"
  [[ "$output" == *"/api/jobs/job-gallery-best/demo-run-package/download"* ]] || fail "run archive report missed package route"
  [[ "$output" != *"private-demo-token"* ]] || fail "run archive report exposed demo token"
  [[ "$output" != *"OPENAI_API_KEY"* ]] || fail "run archive report exposed API key"
  [[ "$output" != *"/Users/example"* ]] || fail "run archive report exposed local path"
  [[ "$output" != *"provider payload"* ]] || fail "run archive report exposed provider payload"
  [[ "$output" != *"raw transcript text"* ]] || fail "run archive report exposed transcript"
}

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file
test_demo_session_owner_summary_is_metadata_only
test_local_auth_helpers_are_metadata_only
test_owner_workspace_smoke_helpers_are_metadata_only
test_owner_workspace_smoke_script_skips_when_auth_unconfigured
test_owner_quota_preflight_helpers_are_metadata_only
test_owner_quota_preflight_script_exits_on_blocked_state
test_upload_readiness_helpers_are_metadata_only
test_worker_topology_summary_is_metadata_only
test_upload_readiness_script_exits_on_blocked_state
test_demo_sample_media_catalog_helpers_are_metadata_only
test_demo_sample_media_catalog_script_exits_on_blocked_state
test_demo_run_launcher_helpers_are_metadata_only
test_demo_run_launcher_script_exits_on_blocked_state
test_upload_demo_video_includes_subtitle_polishing_mode
test_upload_demo_video_applies_tears_showcase_profile
test_upload_demo_video_explicit_env_overrides_profile
test_download_job_comparison_helpers_use_backend_routes
test_print_job_comparison_summary_is_metadata_only
test_download_demo_run_matrix_helper_uses_backend_route
test_print_demo_run_matrix_summary_is_metadata_only
test_download_demo_presenter_pack_helper_uses_backend_route
test_download_demo_replay_card_helper_uses_backend_route
test_download_demo_completion_certificate_helper_uses_backend_route
test_download_demo_acceptance_gate_helper_uses_backend_route
test_download_demo_share_sheet_helpers_use_backend_routes
test_download_demo_run_monitor_helpers_use_backend_routes
test_print_demo_run_monitor_summary_is_metadata_only
test_print_demo_share_sheet_summary_is_metadata_only
test_print_demo_presenter_pack_summary_is_metadata_only
test_print_demo_replay_card_summary_is_metadata_only
test_print_demo_completion_certificate_summary_is_metadata_only
test_print_demo_acceptance_gate_summary_is_metadata_only
test_demo_acceptance_gate_script_requires_job_id
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
test_private_demo_launch_rehearsal_helpers_are_metadata_only
test_private_demo_evidence_gallery_helpers_are_metadata_only
test_private_demo_run_archive_helpers_are_metadata_only

echo "linguaframe-demo client tests passed"
