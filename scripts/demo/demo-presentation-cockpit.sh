#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

BASE_URL="$(demo_base_url)"
OUTPUT_DIR="${LINGUAFRAME_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo/demo-presentation-cockpit}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
COCKPIT_JSON="$OUTPUT_DIR/demo-presentation-cockpit.json"

wait_for_backend "$BASE_URL" 30 2
download_demo_presentation_cockpit_json "$BASE_URL" "$JOB_ID" "$COCKPIT_JSON"

echo "Downloaded demo presentation cockpit: $COCKPIT_JSON"
print_demo_presentation_cockpit_summary_file "$COCKPIT_JSON"
