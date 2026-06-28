#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-completion-certificate.sh

Downloads the metadata-only demo completion certificate for one job.

Environment:
  LINGUAFRAME_DEMO_JOB_ID                         Required selected job id
  LINGUAFRAME_DEMO_BASE_URL                       Default: http://localhost:8080
  LINGUAFRAME_DEMO_CERTIFICATE_OUTPUT_DIR         Default: /tmp/linguaframe-demo/demo-completion-certificate
  LINGUAFRAME_DEMO_CERTIFICATE_JSON_PATH          Default: $OUTPUT_DIR/demo-completion-certificate.json
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_CERTIFICATE_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-completion-certificate}"
JSON_PATH="${LINGUAFRAME_DEMO_CERTIFICATE_JSON_PATH:-$OUTPUT_DIR/demo-completion-certificate.json}"

if [[ -z "$JOB_ID" ]]; then
  echo "Missing LINGUAFRAME_DEMO_JOB_ID." >&2
  usage >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_PATH")"

download_demo_completion_certificate_json "$BASE_URL" "$JOB_ID" "$JSON_PATH"
print_demo_completion_certificate_summary_file "$JSON_PATH"

echo "Wrote demo completion certificate JSON to $JSON_PATH"
