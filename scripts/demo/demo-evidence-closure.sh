#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_DEMO_EVIDENCE_CLOSURE_DIR:-/tmp/linguaframe-demo/demo-evidence-closure}"
JSON_OUTPUT_PATH="$OUTPUT_DIR/demo-evidence-closure.json"
MARKDOWN_OUTPUT_PATH="$OUTPUT_DIR/demo-evidence-closure.md"
ZIP_OUTPUT_PATH="$OUTPUT_DIR/demo-evidence-closure.zip"
REQUEST_PATH="$OUTPUT_DIR/request.json"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID to a completed localization job id." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

write_demo_evidence_closure_request "$REQUEST_PATH"
download_demo_evidence_closure_json "$BASE_URL" "$JOB_ID" "$REQUEST_PATH" "$JSON_OUTPUT_PATH"
download_demo_evidence_closure_markdown "$BASE_URL" "$JOB_ID" "$REQUEST_PATH" "$MARKDOWN_OUTPUT_PATH"
download_demo_evidence_closure_zip "$BASE_URL" "$JOB_ID" "$REQUEST_PATH" "$ZIP_OUTPUT_PATH"

python3 - "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH" "$ZIP_OUTPUT_PATH" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
zip_path = Path(sys.argv[3])
closure = json.loads(json_path.read_text(encoding="utf-8"))

print(f"demoEvidenceClosureStatus={closure.get('closureStatus')}")
print(f"demoEvidenceClosureBaselineMode={closure.get('baselineMode')}")
print(f"demoEvidenceClosureSectionCount={len(closure.get('sections') or [])}")
print(f"demoEvidenceClosureJsonPath={json_path}")
print(f"demoEvidenceClosureMarkdownPath={markdown_path}")
print(f"demoEvidenceClosureZipPath={zip_path}")
PY
