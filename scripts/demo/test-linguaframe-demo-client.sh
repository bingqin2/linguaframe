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

test_demo_curl_adds_token_header_when_configured
test_demo_curl_omits_token_header_when_not_configured
test_demo_base_url_uses_backend_port_from_env_file

echo "linguaframe-demo client tests passed"
