#!/usr/bin/env bash

set -euo pipefail

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

demo_base_url() {
  echo "${LINGUAFRAME_DEMO_BASE_URL:-http://localhost:8080}"
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

  curl -fsS \
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
    response="$(curl -fsS "$base_url/api/jobs/$job_id")"
    status="$(printf '%s' "$response" | extract_json_field status)"
    if [[ "$status" == "$expected_status" ]]; then
      printf '%s\n' "$response"
      return 0
    fi
    sleep "$delay_seconds"
  done

  echo "Job $job_id did not reach $expected_status" >&2
  curl -fsS "$base_url/api/jobs/$job_id" >&2 || true
  exit 1
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

list_job_artifacts() {
  local base_url="$1"
  local job_id="$2"

  curl -fsS "$base_url/api/jobs/$job_id/artifacts"
}

get_job_transcript() {
  local base_url="$1"
  local job_id="$2"

  curl -fsS "$base_url/api/jobs/$job_id/transcript"
}

get_job_subtitles() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"

  curl -fsS "$base_url/api/jobs/$job_id/subtitles/$language"
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
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
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
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
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
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}

download_artifact_archive() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/archive/download" -o "$output_path"
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
