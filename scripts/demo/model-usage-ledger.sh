#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_MODEL_USAGE_LEDGER_OUTPUT_DIR:-/tmp/linguaframe-demo/model-usage-ledger}"
LIMIT="${LINGUAFRAME_MODEL_USAGE_LEDGER_LIMIT:-20}"
JSON_OUTPUT_PATH="${LINGUAFRAME_MODEL_USAGE_LEDGER_JSON_PATH:-$OUTPUT_DIR/model-usage-ledger.json}"
MARKDOWN_OUTPUT_PATH="${LINGUAFRAME_MODEL_USAGE_LEDGER_MARKDOWN_PATH:-$OUTPUT_DIR/model-usage-ledger.md}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$MARKDOWN_OUTPUT_PATH")"

demo_curl -fsS \
  "$BASE_URL/api/operator/model-usage-ledger?limit=$LIMIT" \
  -o "$JSON_OUTPUT_PATH"

demo_curl -fsS \
  "$BASE_URL/api/operator/model-usage-ledger/markdown/download?limit=$LIMIT" \
  -o "$MARKDOWN_OUTPUT_PATH"

python3 - "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
ledger = json.loads(json_path.read_text(encoding="utf-8"))
summary = ledger.get("summary") or {}

print(f"modelUsageLedgerStatus={summary.get('ledgerStatus')}")
print(f"modelUsageLedgerJobCount={summary.get('jobCount')}")
print(f"modelUsageLedgerCallCount={summary.get('modelCallCount')}")
print(f"modelUsageLedgerFailedCallCount={summary.get('failedModelCallCount')}")
print(f"modelUsageLedgerEstimatedCostUsd={summary.get('estimatedCostUsd')}")
print(f"modelUsageLedgerJsonPath={json_path}")
print(f"modelUsageLedgerMarkdownPath={markdown_path}")
PY
