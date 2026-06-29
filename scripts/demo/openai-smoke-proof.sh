#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-}"
OUTPUT_DIR="${LINGUAFRAME_OPENAI_SMOKE_PROOF_OUTPUT_DIR:-/tmp/linguaframe-demo/openai-smoke-proof}"
JSON_OUTPUT_PATH="${LINGUAFRAME_OPENAI_SMOKE_PROOF_JSON_PATH:-$OUTPUT_DIR/openai-smoke-proof.json}"
MARKDOWN_OUTPUT_PATH="${LINGUAFRAME_OPENAI_SMOKE_PROOF_MARKDOWN_PATH:-$OUTPUT_DIR/openai-smoke-proof.md}"
REPORT_ONLY="${LINGUAFRAME_OPENAI_SMOKE_PROOF_REPORT_ONLY:-false}"

if [[ -z "${JOB_ID//[[:space:]]/}" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID to a completed OpenAI smoke job id." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$MARKDOWN_OUTPUT_PATH")"

download_openai_smoke_proof_json "$BASE_URL" "$JOB_ID" "$JSON_OUTPUT_PATH"
download_openai_smoke_proof_markdown "$BASE_URL" "$JOB_ID" "$MARKDOWN_OUTPUT_PATH"

python3 - "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
proof = json.loads(json_path.read_text(encoding="utf-8"))
required = proof.get("requiredChecks") or []
optional = proof.get("optionalChecks") or []

def count(rows, status):
    return sum(1 for row in rows if row.get("status") == status)

print(f"openAiSmokeProofStatus={proof.get('overallStatus')}")
print(f"openAiSmokeProofPhase={proof.get('phase')}")
print(f"openAiSmokeProofRequiredReadyCount={count(required, 'READY')}")
print(f"openAiSmokeProofRequiredBlockedCount={count(required, 'BLOCKED')}")
print(f"openAiSmokeProofOptionalAttentionCount={count(optional, 'ATTENTION')}")
print(f"openAiSmokeProofModelCallCount={len(proof.get('modelCalls') or [])}")
print(f"openAiSmokeProofArtifactCount={len(proof.get('artifacts') or [])}")
print(f"openAiSmokeProofRecommendedNextAction={proof.get('recommendedNextAction')}")
print(f"openAiSmokeProofJsonPath={json_path}")
print(f"openAiSmokeProofMarkdownPath={markdown_path}")
PY

STATUS="$(python3 - "$JSON_OUTPUT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("overallStatus"))
PY
)"

if [[ "$STATUS" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "OpenAI smoke proof is BLOCKED. Set LINGUAFRAME_OPENAI_SMOKE_PROOF_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
