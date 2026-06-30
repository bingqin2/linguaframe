#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
JOB_ID="${LINGUAFRAME_DEMO_JOB_ID:-${1:-}}"
OUTPUT_DIR="${LINGUAFRAME_NARRATION_TIMING_ASSISTANT_OUTPUT_DIR:-/tmp/linguaframe-demo/narration-timing-assistant}"
MINIMUM_REPORT_GAP_SECONDS="${LINGUAFRAME_NARRATION_TIMING_MINIMUM_REPORT_GAP_SECONDS:-0.5}"
TARGET_GAP_SECONDS="${LINGUAFRAME_NARRATION_TIMING_TARGET_GAP_SECONDS:-0.25}"

if [[ -z "$JOB_ID" ]]; then
  echo "Set LINGUAFRAME_DEMO_JOB_ID or pass a job id as the first argument." >&2
  exit 2
fi

mkdir -p "$OUTPUT_DIR"

WORKSPACE_PATH="$OUTPUT_DIR/narration-workspace.json"
REPORT_PATH="$OUTPUT_DIR/narration-timing-assistant-report.json"

download_narration_workspace_json "$BASE_URL" "$JOB_ID" "$WORKSPACE_PATH"

python3 - "$WORKSPACE_PATH" "$REPORT_PATH" "$MINIMUM_REPORT_GAP_SECONDS" "$TARGET_GAP_SECONDS" <<'PY'
import json
import sys

workspace_path, report_path = sys.argv[1], sys.argv[2]
minimum_report_gap_seconds = float(sys.argv[3])
target_gap_seconds = float(sys.argv[4])

workspace = json.load(open(workspace_path, encoding="utf-8"))
segments = sorted(
    workspace.get("segments", []),
    key=lambda item: (float(item.get("startSeconds") or 0), int(item.get("index") or 0)),
)

gap_count = 0
total_gap_seconds = 0.0
longest_gap_seconds = 0.0
overlap_count = 0
invalid_range_count = 0
blank_text_count = 0

previous_end = None
for segment in segments:
    start = float(segment.get("startSeconds") or 0)
    end = float(segment.get("endSeconds") or 0)
    text = str(segment.get("text") or "")
    if end <= start:
        invalid_range_count += 1
    if not text.strip():
        blank_text_count += 1
    if previous_end is not None:
        delta = round(start - previous_end, 3)
        if delta >= minimum_report_gap_seconds:
            gap_count += 1
            total_gap_seconds = round(total_gap_seconds + delta, 3)
            longest_gap_seconds = max(longest_gap_seconds, delta)
        elif delta < 0:
            overlap_count += 1
    previous_end = max(previous_end or end, end)

generation_ready = bool(segments) and overlap_count == 0 and invalid_range_count == 0 and blank_text_count == 0
if overlap_count > 0:
    suggested_next_action = "Resolve overlaps before saving or generating narration."
elif invalid_range_count > 0 or blank_text_count > 0:
    suggested_next_action = "Fix invalid ranges or blank narration rows before saving."
elif gap_count > 0:
    suggested_next_action = "Review intentional silence or close gaps in the browser timing assistant."
else:
    suggested_next_action = "Timing metadata is ready for save or narration generation."

report = {
    "jobId": workspace.get("jobId"),
    "segmentCount": len(segments),
    "gapCount": gap_count,
    "totalGapSeconds": round(total_gap_seconds, 3),
    "longestGapSeconds": round(longest_gap_seconds, 3),
    "overlapCount": overlap_count,
    "invalidRangeCount": invalid_range_count,
    "blankTextCount": blank_text_count,
    "generationReady": generation_ready,
    "minimumReportGapSeconds": minimum_report_gap_seconds,
    "targetGapSeconds": target_gap_seconds,
    "suggestedNextAction": suggested_next_action,
}

with open(report_path, "w", encoding="utf-8") as handle:
    json.dump(report, handle, ensure_ascii=False, indent=2)
    handle.write("\n")

print(f"narrationTimingAssistantJobId={report['jobId']}")
print(f"narrationTimingAssistantSegmentCount={report['segmentCount']}")
print(f"narrationTimingAssistantGapCount={report['gapCount']}")
print(f"narrationTimingAssistantTotalGapSeconds={report['totalGapSeconds']}")
print(f"narrationTimingAssistantLongestGapSeconds={report['longestGapSeconds']}")
print(f"narrationTimingAssistantOverlapCount={report['overlapCount']}")
print(f"narrationTimingAssistantInvalidRangeCount={report['invalidRangeCount']}")
print(f"narrationTimingAssistantBlankTextCount={report['blankTextCount']}")
print(f"narrationTimingAssistantGenerationReady={str(report['generationReady']).lower()}")
print(f"narrationTimingAssistantSuggestedNextAction={report['suggestedNextAction']}")
PY

echo "narrationTimingAssistantWorkspacePath=$WORKSPACE_PATH"
echo "narrationTimingAssistantReportPath=$REPORT_PATH"
echo "narrationTimingAssistantSafety=Report output is metadata-only; narration text and media bytes are not printed."
