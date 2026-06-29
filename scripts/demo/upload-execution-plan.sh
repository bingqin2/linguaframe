#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_UPLOAD_PLAN_SAMPLE_PATH:-${LINGUAFRAME_TEARS_SAMPLE_PATH:-${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/sample.mp4}}}"
OUTPUT_PATH="${LINGUAFRAME_UPLOAD_EXECUTION_PLAN_PATH:-/tmp/linguaframe-demo/upload-execution-plan.json}"
DEMO_PROFILE_ID="${LINGUAFRAME_DEMO_PROFILE_ID:-tears-showcase}"
TARGET_LANGUAGE="${LINGUAFRAME_DEMO_TARGET_LANGUAGE:-zh-CN}"
TRANSLATION_STYLE="${LINGUAFRAME_DEMO_TRANSLATION_STYLE:-FORMAL}"
SUBTITLE_STYLE_PRESET="${LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET:-HIGH_CONTRAST}"
SUBTITLE_POLISHING_MODE="${LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE:-BALANCED}"
TRANSLATION_GLOSSARY="${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY:-$'Maya => 玛雅\nTears of Steel => 钢铁之泪\nThom => 汤姆'}"

if [[ -z "$TRANSLATION_GLOSSARY" && -n "${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE:-}" ]]; then
  TRANSLATION_GLOSSARY="$(<"$LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE")"
fi

if [[ ! -s "$SAMPLE_PATH" ]]; then
  echo "Sample video does not exist or is empty. Set LINGUAFRAME_UPLOAD_PLAN_SAMPLE_PATH or LINGUAFRAME_TEARS_SAMPLE_PATH." >&2
  exit 1
fi

wait_for_backend "$BASE_URL"
mkdir -p "$(dirname "$OUTPUT_PATH")"

form_args=(
  -F "file=@${SAMPLE_PATH};type=video/mp4"
  -F "targetLanguage=${TARGET_LANGUAGE}"
  -F "demoProfileId=${DEMO_PROFILE_ID}"
  -F "translationStyle=${TRANSLATION_STYLE}"
  -F "subtitleStylePreset=${SUBTITLE_STYLE_PRESET}"
  -F "subtitlePolishingMode=${SUBTITLE_POLISHING_MODE}"
)
if [[ -n "${TRANSLATION_GLOSSARY//[[:space:]]/}" ]]; then
  form_args+=(-F "translationGlossary=${TRANSLATION_GLOSSARY}")
fi

demo_curl -fsS "${form_args[@]}" "$BASE_URL/api/media/uploads/execution-plan" >"$OUTPUT_PATH"

python3 - "$OUTPUT_PATH" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as handle:
    value = json.load(handle)

print(f"uploadExecutionPlanStatus={value['overallStatus']}")
print(f"uploadExecutionPlanFile={value.get('filename') or 'unknown'}")
print(f"uploadExecutionPlanDurationSeconds={value.get('durationSeconds')}")
print(f"uploadExecutionPlanEstimatedCostUsd={value['estimatedCostUsd']}")
print(
    "uploadExecutionPlanEstimatedDurationSeconds="
    f"{value['estimatedDurationSecondsLower']}..{value['estimatedDurationSecondsUpper']}"
)
print(f"uploadExecutionPlanRecommendedNextAction={value['recommendedNextAction']}")
reuse = value.get("sourceReuse") or {}
if reuse.get("sourceContentSha256"):
    print(f"uploadExecutionPlanSourceSha256={reuse['sourceContentSha256']}")
print(f"uploadExecutionPlanDuplicateSourceMatchCount={reuse.get('candidateCount', 0)}")
print(f"uploadExecutionPlanSourceReuseRecommendedAction={reuse.get('recommendedAction', 'UPLOAD_NEW_SOURCE')}")
if reuse.get("recommendedExistingJobId"):
    print(f"uploadExecutionPlanRecommendedExistingJobId={reuse['recommendedExistingJobId']}")
decision = value.get("sourceReuseDecision") or {}
if decision:
    print(f"sourceReuseDecisionStatus={decision.get('status', 'UPLOAD_NEW_SOURCE')}")
    print(f"sourceReuseDecisionHeadline={decision.get('headline', '')}")
    print(f"sourceReuseDecisionRecommendedAction={decision.get('recommendedAction', 'UPLOAD_NEW_SOURCE')}")
    if decision.get("recommendedExistingJobId"):
        print(f"sourceReuseDecisionRecommendedExistingJobId={decision['recommendedExistingJobId']}")
    for link in decision.get("links", [])[:5]:
        print(f"sourceReuseDecisionLink={link['kind']}:{link['href']}")
for candidate in reuse.get("candidates", [])[:5]:
    print(
        "uploadExecutionPlanSourceReuseCandidate="
        f"{candidate['jobId']}:{candidate['jobStatus']}:{candidate.get('demoProfileId') or 'default'}:"
        f"{candidate.get('translationStyle') or 'unknown'}"
    )
for gate in value.get("gates", []):
    if gate.get("blocking"):
        print(
            "uploadExecutionPlanBlockingGate="
            f"{gate['id']}:{gate['status']}:{gate['nextAction']}"
        )
for stage in value.get("stages", []):
    if stage.get("executionType") == "PAID":
        print(
            "uploadExecutionPlanPaidStage="
            f"{stage['id']}:{stage['provider']}:{stage['estimatedCostUsd']}:"
            f"{stage['estimatedDurationSecondsLower']}..{stage['estimatedDurationSecondsUpper']}"
        )
for command in value.get("commands", []):
    print(f"uploadExecutionPlanCommand={command['id']}:{command['command']}")
PY

echo "Wrote upload execution plan to $OUTPUT_PATH"
