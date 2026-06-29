#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=scripts/demo/lib/linguaframe-demo.sh
source "$REPO_ROOT/scripts/demo/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="${LINGUAFRAME_DEMO_BASE_URL:-$(demo_base_url)}"
OUTPUT_DIR="${LINGUAFRAME_OPENAI_READINESS_OUTPUT_DIR:-/tmp/linguaframe-demo/openai-readiness-evidence}"
JSON_OUTPUT_PATH="${LINGUAFRAME_OPENAI_READINESS_JSON_PATH:-$OUTPUT_DIR/openai-readiness-evidence.json}"
MARKDOWN_OUTPUT_PATH="${LINGUAFRAME_OPENAI_READINESS_MARKDOWN_PATH:-$OUTPUT_DIR/openai-readiness-evidence.md}"
REPORT_ONLY="${LINGUAFRAME_OPENAI_READINESS_REPORT_ONLY:-false}"

mkdir -p "$OUTPUT_DIR" "$(dirname "$JSON_OUTPUT_PATH")" "$(dirname "$MARKDOWN_OUTPUT_PATH")"

demo_curl -fsS \
  "$BASE_URL/api/operator/openai-readiness-evidence" \
  -o "$JSON_OUTPUT_PATH"

demo_curl -fsS \
  "$BASE_URL/api/operator/openai-readiness-evidence/markdown/download" \
  -o "$MARKDOWN_OUTPUT_PATH"

python3 - "$JSON_OUTPUT_PATH" "$MARKDOWN_OUTPUT_PATH" <<'PY'
import json
import sys
from pathlib import Path

json_path = Path(sys.argv[1])
markdown_path = Path(sys.argv[2])
evidence = json.loads(json_path.read_text(encoding="utf-8"))
live_check = evidence.get("liveCheck") or {}
model_usage = evidence.get("modelUsage") or {}
providers = evidence.get("providers") or []
openai_stages = [
    provider.get("stage")
    for provider in providers
    if provider.get("paidProvider") and provider.get("enabled")
]

print(f"openAiReadinessStatus={evidence.get('overallStatus')}")
print(f"openAiReadinessPhase={evidence.get('phase')}")
print(f"openAiReadinessLiveCheckStatus={live_check.get('status')}")
print(f"openAiReadinessLiveCheckLatencyMs={live_check.get('latencyMs')}")
print(f"openAiReadinessModelCallCount={model_usage.get('modelCallCount')}")
print(f"openAiReadinessFailedModelCallCount={model_usage.get('failedModelCallCount')}")
print(f"openAiReadinessOpenAiStages={','.join(stage for stage in openai_stages if stage)}")
print(f"openAiReadinessRecommendedNextAction={evidence.get('recommendedNextAction')}")
print(f"openAiReadinessJsonPath={json_path}")
print(f"openAiReadinessMarkdownPath={markdown_path}")
PY

STATUS="$(python3 - "$JSON_OUTPUT_PATH" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1], encoding="utf-8")).get("overallStatus"))
PY
)"

if [[ "$STATUS" == "BLOCKED" && "$REPORT_ONLY" != "true" ]]; then
  echo "OpenAI readiness evidence is BLOCKED. Set LINGUAFRAME_OPENAI_READINESS_REPORT_ONLY=true to export without failing." >&2
  exit 1
fi
