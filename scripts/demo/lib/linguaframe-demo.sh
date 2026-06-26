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
  printf 'linguaframe demo sample\n' > "$path"
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

job = json.load(sys.stdin)
print("jobId=" + job["jobId"])
print("videoId=" + job["videoId"])
print("status=" + job["status"])
print("retryCount=" + str(job.get("retryCount", 0)))
for event in job.get("timelineEvents", []):
    print("- " + event["stage"] + " " + event["status"] + ": " + event["message"])
'
}
