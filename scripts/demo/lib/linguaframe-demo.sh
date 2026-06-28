#!/usr/bin/env bash

set -euo pipefail

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

env_value() {
  local key="$1"
  local fallback="${2:-}"
  local env_file="${LINGUAFRAME_ENV_FILE:-.env}"

  if [[ ! -f "$env_file" ]]; then
    printf '%s' "$fallback"
    return 0
  fi

  local line
  line="$(grep -E "^${key}=" "$env_file" | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    printf '%s' "$fallback"
    return 0
  fi

  local value="${line#*=}"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf '%s' "$value"
}

demo_access_token() {
  printf '%s' "${LINGUAFRAME_DEMO_ACCESS_TOKEN:-$(env_value LINGUAFRAME_DEMO_ACCESS_TOKEN)}"
}

demo_access_header_name() {
  printf '%s' "${LINGUAFRAME_DEMO_ACCESS_HEADER_NAME:-$(env_value LINGUAFRAME_DEMO_ACCESS_HEADER_NAME X-LinguaFrame-Demo-Token)}"
}

demo_curl() {
  local curl_bin="${LINGUAFRAME_DEMO_CURL_BIN:-curl}"
  local token
  token="$(demo_access_token)"

  if [[ -n "$token" ]]; then
    "$curl_bin" -H "$(demo_access_header_name): $token" "$@"
    return 0
  fi

  "$curl_bin" "$@"
}

demo_base_url() {
  local backend_port
  backend_port="$(env_value LINGUAFRAME_BACKEND_PORT 8080)"
  echo "${LINGUAFRAME_DEMO_BASE_URL:-http://localhost:${backend_port}}"
}

wait_for_backend() {
  local base_url="$1"
  local attempts="${2:-60}"
  local delay_seconds="${3:-2}"

  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$base_url/actuator/health" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Backend did not become healthy at $base_url" >&2
  exit 1
}

create_demo_sample() {
  local path="$1"
  mkdir -p "$(dirname "$path")"
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "Missing ffmpeg to create demo MP4; set LINGUAFRAME_DEMO_SAMPLE_PATH to an existing short MP4." >&2
    exit 1
  fi
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "color=c=blue:s=640x360:r=25:d=2" \
    -f lavfi -i "sine=frequency=440:sample_rate=16000:duration=2" \
    -shortest \
    -c:v libx264 \
    -pix_fmt yuv420p \
    -c:a aac \
    -movflags +faststart \
    "$path"
}

ensure_demo_sample() {
  local path="$1"
  if [[ -s "$path" ]]; then
    return 0
  fi
  create_demo_sample "$path"
}

extract_json_field() {
  local field="$1"
  python3 -c '
import json
import sys

value = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    value = value[part]
print(value)
' "$field"
}

upload_demo_video() {
  local base_url="$1"
  local sample_path="$2"

  demo_curl -fsS \
    -F "file=@${sample_path};type=video/mp4" \
    -F "targetLanguage=zh-CN" \
    "$base_url/api/media/uploads"
}

wait_for_job_status() {
  local base_url="$1"
  local job_id="$2"
  local expected_status="$3"
  local attempts="${4:-60}"
  local delay_seconds="${5:-2}"
  local response
  local status

  for ((i = 1; i <= attempts; i++)); do
    response="$(demo_curl -fsS "$base_url/api/jobs/$job_id")"
    status="$(printf '%s' "$response" | extract_json_field status)"
    if [[ "$status" == "$expected_status" ]]; then
      printf '%s\n' "$response"
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Job $job_id did not reach $expected_status" >&2
  demo_curl -fsS "$base_url/api/jobs/$job_id" >&2 || true
  exit 1
}

download_job_detail() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id" -o "$output_path"
}

print_job_summary() {
  python3 -c '
import json
import sys
from decimal import Decimal

job = json.load(sys.stdin, parse_float=Decimal)
print("jobId=" + job["jobId"])
print("videoId=" + job["videoId"])
print("status=" + job["status"])
print("retryCount=" + str(job.get("retryCount", 0)))
if job.get("failureStage"):
    print("failureStage=" + job["failureStage"])
if job.get("failureReason"):
    print("failureReason=" + job["failureReason"])
triage = job.get("failureTriage")
if triage:
    print("failureTriageCategory=" + triage["category"])
    print("failureTriageRetryable=" + str(triage["retryable"]).lower())
    print("failureTriageSummary=" + triage["summary"])
    print("failureTriageRecommendedAction=" + triage["recommendedAction"])
    if triage.get("runbookCommand"):
        print("failureTriageRunbookCommand=" + triage["runbookCommand"])
progress = job.get("pipelineProgress")
if progress:
    print("pipelineCurrentStage=" + str(progress.get("currentStage")))
    print("pipelineTerminal=" + str(progress.get("terminal")).lower())
    print("pipelineCompletedStageCount=" + str(progress.get("completedStageCount", 0)))
    print("pipelineFailedStageCount=" + str(progress.get("failedStageCount", 0)))
    print("pipelineTotalMeasuredDurationMs=" + str(progress.get("totalMeasuredDurationMs", 0)))
    if progress.get("slowestStage"):
        print("pipelineSlowestStage=" + progress["slowestStage"])
        print("pipelineSlowestStageDurationMs=" + str(progress.get("slowestStageDurationMs", 0)))
summary = job.get("usageSummary") or {}
print("modelCallCount=" + str(summary.get("modelCallCount", 0)))
print("failedModelCallCount=" + str(summary.get("failedModelCallCount", 0)))
print("estimatedCostUsd=" + str(summary.get("estimatedCostUsd", "0")))
for call in job.get("modelCalls", []):
    print("- MODEL_CALL " + call["operation"] + " " + call["provider"] + " " + call["model"] + " " + call["status"])
for event in job.get("timelineEvents", []):
    print("- " + event["stage"] + " " + event["status"] + ": " + event["message"])
'
}

print_job_cache_summary_file() {
  local job_detail_path="$1"

  python3 - "$job_detail_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
summary = job.get("usageSummary") or {}
cache = job.get("cacheSummary") or {}
print("jobId=" + job["jobId"])
print("status=" + job["status"])
print("modelCallCount=" + str(summary.get("modelCallCount", 0)))
print("failedModelCallCount=" + str(summary.get("failedModelCallCount", 0)))
print("cacheHitCount=" + str(cache.get("cacheHitCount", 0)))
print("generatedArtifactCount=" + str(cache.get("generatedArtifactCount", 0)))
print("providerCacheHitCount=" + str(cache.get("providerCacheHitCount", 0)))
PY
}

print_provider_cache_hit_events_file() {
  local job_detail_path="$1"

  python3 - "$job_detail_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
events = [
    event
    for event in job.get("timelineEvents", [])
    if event.get("status") == "CACHE_HIT" and "provider result" in (event.get("message") or "")
]
if not events:
    print("- PROVIDER_CACHE_HIT none")
else:
    for event in events:
        print("- PROVIDER_CACHE_HIT " + event["stage"] + ": " + event["message"])
PY
}

assert_provider_cache_hit_file() {
  local job_detail_path="$1"

  python3 - "$job_detail_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
count = (job.get("cacheSummary") or {}).get("providerCacheHitCount", 0)
if count < 1:
    raise SystemExit("Expected providerCacheHitCount >= 1, got " + str(count))
PY
}

print_cache_hit_comparison() {
  local first_job_detail_path="$1"
  local second_job_detail_path="$2"

  python3 - "$first_job_detail_path" "$second_job_detail_path" <<'PY'
import json
import sys

first = json.load(open(sys.argv[1], encoding="utf-8"))
second = json.load(open(sys.argv[2], encoding="utf-8"))

def usage(job, field):
    return (job.get("usageSummary") or {}).get(field, 0)

def cache(job, field):
    return (job.get("cacheSummary") or {}).get(field, 0)

print("firstJobId=" + first["jobId"])
print("secondJobId=" + second["jobId"])
print("firstModelCallCount=" + str(usage(first, "modelCallCount")))
print("secondModelCallCount=" + str(usage(second, "modelCallCount")))
print("firstProviderCacheHitCount=" + str(cache(first, "providerCacheHitCount")))
print("secondProviderCacheHitCount=" + str(cache(second, "providerCacheHitCount")))
print("firstArtifactCacheHitCount=" + str(cache(first, "cacheHitCount")))
print("secondArtifactCacheHitCount=" + str(cache(second, "cacheHitCount")))
PY
}

print_budget_guard_failure() {
  local expected_reason="${1:-Job cost budget exceeded}"
  python3 -c '
import json
import sys
from decimal import Decimal

expected_reason = sys.argv[1]
job = json.load(sys.stdin, parse_float=Decimal)
print("jobId=" + job["jobId"])
print("status=" + job["status"])
print("failureStage=" + str(job.get("failureStage")))
print("failureReason=" + str(job.get("failureReason")))
triage = job.get("failureTriage")
if triage:
    print("failureTriageCategory=" + triage["category"])
    print("failureTriageRetryable=" + str(triage["retryable"]).lower())
    print("failureTriageSummary=" + triage["summary"])
    print("failureTriageRecommendedAction=" + triage["recommendedAction"])
summary = job.get("usageSummary") or {}
print("modelCallCount=" + str(summary.get("modelCallCount", 0)))
print("failedModelCallCount=" + str(summary.get("failedModelCallCount", 0)))
print("estimatedCostUsd=" + str(summary.get("estimatedCostUsd", "0")))
for call in job.get("modelCalls", []):
    print("- MODEL_CALL " + call["operation"] + " " + call["provider"] + " " + call["model"] + " " + call["status"])
for event in job.get("timelineEvents", []):
    print("- " + event["stage"] + " " + event["status"] + ": " + event["message"])
if job["status"] != "FAILED":
    raise SystemExit("Expected FAILED status")
reason = job.get("failureReason") or ""
if expected_reason not in reason:
    raise SystemExit("Expected budget guard failure reason containing " + expected_reason)
triage = job.get("failureTriage") or {}
if triage.get("category") != "BUDGET_GUARD":
    raise SystemExit("Expected failureTriage.category=BUDGET_GUARD")
' "$expected_reason"
}

list_job_artifacts() {
  local base_url="$1"
  local job_id="$2"

  demo_curl -fsS "$base_url/api/jobs/$job_id/artifacts"
}

get_job_transcript() {
  local base_url="$1"
  local job_id="$2"

  demo_curl -fsS "$base_url/api/jobs/$job_id/transcript"
}

get_job_subtitles() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"

  demo_curl -fsS "$base_url/api/jobs/$job_id/subtitles/$language"
}

download_first_artifact() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local artifacts_json
  local artifact_id

  artifacts_json="$(list_job_artifacts "$base_url" "$job_id")"
  artifact_id="$(printf '%s' "$artifacts_json" | python3 -c 'import json, sys; print(json.load(sys.stdin)[0]["artifactId"])')"
  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}

download_artifact_by_type() {
  local base_url="$1"
  local job_id="$2"
  local artifact_type="$3"
  local output_path="$4"
  local artifact_id

  artifact_id="$(list_job_artifacts "$base_url" "$job_id" | python3 -c '
import json
import sys

target = sys.argv[1]
for artifact in json.load(sys.stdin):
    if artifact["type"] == target:
        print(artifact["artifactId"])
        raise SystemExit(0)
raise SystemExit(1)
' "$artifact_type")"
  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}

download_optional_artifact_by_type() {
  local base_url="$1"
  local job_id="$2"
  local artifact_type="$3"
  local output_path="$4"
  local artifact_id

  if ! artifact_id="$(list_job_artifacts "$base_url" "$job_id" | python3 -c '
import json
import sys

target = sys.argv[1]
for artifact in json.load(sys.stdin):
    if artifact["type"] == target:
        print(artifact["artifactId"])
        raise SystemExit(0)
raise SystemExit(1)
' "$artifact_type")"; then
    return 1
  fi
  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}

download_artifact_archive() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/artifacts/archive/download" -o "$output_path"
}

download_job_diagnostics() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/diagnostics/download" -o "$output_path"
}

download_job_evidence_markdown() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/evidence/markdown/download" -o "$output_path"
}

download_job_evidence_bundle() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/evidence/bundle/download" -o "$output_path"
}

print_zip_entries() {
  local archive_path="$1"

  python3 - "$archive_path" <<'PY'
import sys
import zipfile

archive_path = sys.argv[1]
with zipfile.ZipFile(archive_path) as archive:
    print("archiveEntryCount=" + str(len(archive.namelist())))
    for name in archive.namelist():
        print("- ZIP_ENTRY " + name)
PY
}

print_artifact_summary() {
  python3 -c '
import json
import sys

artifacts = json.load(sys.stdin)
print("artifactCount=" + str(len(artifacts)))
for artifact in artifacts:
    print("- " + artifact["type"] + " " + artifact["filename"] + " " + str(artifact["sizeBytes"]) + " bytes")
'
}

print_diagnostics_summary() {
  local diagnostics_path="$1"

  python3 - "$diagnostics_path" <<'PY'
import json
import sys

diagnostics_path = sys.argv[1]
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "sk-",
    "raw transcript text",
    "raw subtitle text",
    "provider request payload",
]
text = open(diagnostics_path, encoding="utf-8").read()
for value in forbidden:
    if value in text:
        raise SystemExit("Diagnostics report contains forbidden sensitive string: " + value)

report = json.loads(text)
job = report["job"]
print("diagnosticsJobId=" + job["jobId"])
print("diagnosticsStatus=" + job["status"])
print("diagnosticsArtifactCount=" + str(report.get("artifactCount", len(report.get("artifacts", [])))))
print("diagnosticsModelCallCount=" + str(len(job.get("modelCalls", []))))
print("diagnosticsTimelineEventCount=" + str(len(job.get("timelineEvents", []))))
triage = job.get("failureTriage")
if triage:
    print("diagnosticsFailureTriageCategory=" + triage["category"])
    print("diagnosticsFailureTriageRetryable=" + str(triage["retryable"]).lower())
progress = job.get("pipelineProgress")
if progress:
    print("diagnosticsPipelineCurrentStage=" + str(progress.get("currentStage")))
    print("diagnosticsPipelineTerminal=" + str(progress.get("terminal")).lower())
    print("diagnosticsPipelineCompletedStageCount=" + str(progress.get("completedStageCount", 0)))
    print("diagnosticsPipelineTotalMeasuredDurationMs=" + str(progress.get("totalMeasuredDurationMs", 0)))
PY
}

print_evidence_markdown_summary() {
  local evidence_path="$1"
  local expected_job_id="$2"

  python3 - "$evidence_path" "$expected_job_id" <<'PY'
import sys

evidence_path = sys.argv[1]
expected_job_id = sys.argv[2]
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "sk-",
    "raw transcript text",
    "raw subtitle text",
    "provider request payload",
]
required = [
    "# LinguaFrame Demo Evidence",
    "- Job: " + expected_job_id,
    "- Status: COMPLETED",
    "- Model calls:",
    "- Estimated cost:",
    "- Result bundle:",
    "- Diagnostics:",
    "Timeline:",
    "Artifacts:",
]
text = open(evidence_path, encoding="utf-8").read()
for value in forbidden:
    if value in text:
        raise SystemExit("Evidence report contains forbidden sensitive string: " + value)
for value in required:
    if value not in text:
        raise SystemExit("Evidence report is missing required marker: " + value)
print("evidenceMarkdownJobId=" + expected_job_id)
print("evidenceMarkdownBytes=" + str(len(text.encode("utf-8"))))
PY
}

print_evidence_bundle_summary() {
  local bundle_path="$1"
  local expected_job_id="$2"

  python3 - "$bundle_path" "$expected_job_id" <<'PY'
import sys
import zipfile

bundle_path = sys.argv[1]
expected_job_id = sys.argv[2]
required_entries = {"manifest.json", "evidence.md", "diagnostics.json"}
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "sk-",
    "raw transcript text",
    "raw subtitle text",
    "provider request payload",
]

with zipfile.ZipFile(bundle_path) as archive:
    names = set(archive.namelist())
    missing = sorted(required_entries - names)
    if missing:
        raise SystemExit("Evidence bundle is missing entries: " + ", ".join(missing))
    combined = ""
    for name in sorted(required_entries):
        combined += archive.read(name).decode("utf-8") + "\n"

for value in forbidden:
    if value in combined:
        raise SystemExit("Evidence bundle contains forbidden sensitive string: " + value)
if "- Job: " + expected_job_id not in combined:
    raise SystemExit("Evidence bundle markdown does not reference expected job id")
if '"jobId":"' + expected_job_id + '"' not in combined:
    raise SystemExit("Evidence bundle JSON does not reference expected job id")

print("evidenceBundleJobId=" + expected_job_id)
print("evidenceBundleEntryCount=" + str(len(required_entries)))
PY
}
