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

auth_curl() {
  local bearer_token="$1"
  shift
  local curl_bin="${LINGUAFRAME_DEMO_CURL_BIN:-curl}"

  if [[ -n "$bearer_token" ]]; then
    "$curl_bin" -H "Authorization: Bearer $bearer_token" "$@"
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
  local demo_profile_id="${LINGUAFRAME_DEMO_PROFILE_ID:-quick-baseline}"
  local profile_translation_style="NATURAL"
  local profile_subtitle_style_preset="STANDARD"
  local profile_subtitle_polishing_mode="OFF"
  local profile_translation_glossary=""
  case "$demo_profile_id" in
    quick-baseline)
      ;;
    tears-showcase)
      profile_translation_style="FORMAL"
      profile_subtitle_style_preset="HIGH_CONTRAST"
      profile_subtitle_polishing_mode="BALANCED"
      profile_translation_glossary=$'Maya => 玛雅\nTears of Steel => 钢铁之泪\nThom => 汤姆'
      ;;
    concise-review)
      profile_translation_style="CONCISE"
      profile_subtitle_style_preset="LARGE"
      profile_subtitle_polishing_mode="STRICT"
      ;;
    "")
      demo_profile_id="quick-baseline"
      ;;
    *)
      echo "Unknown LINGUAFRAME_DEMO_PROFILE_ID: $demo_profile_id" >&2
      return 2
      ;;
  esac

  local translation_style="${LINGUAFRAME_DEMO_TRANSLATION_STYLE:-$profile_translation_style}"
  local subtitle_style_preset="${LINGUAFRAME_DEMO_SUBTITLE_STYLE_PRESET:-$profile_subtitle_style_preset}"
  local subtitle_polishing_mode="${LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE:-$profile_subtitle_polishing_mode}"
  local translation_glossary="${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY:-$profile_translation_glossary}"
  if [[ -z "$translation_glossary" && -n "${LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE:-}" ]]; then
    translation_glossary="$(<"$LINGUAFRAME_DEMO_TRANSLATION_GLOSSARY_FILE")"
  fi

  local form_args=(
    -F "file=@${sample_path};type=video/mp4"
    -F "targetLanguage=zh-CN"
    -F "demoProfileId=${demo_profile_id}"
    -F "translationStyle=${translation_style}"
    -F "subtitleStylePreset=${subtitle_style_preset}"
    -F "subtitlePolishingMode=${subtitle_polishing_mode}"
  )
  if [[ -n "${translation_glossary//[[:space:]]/}" ]]; then
    form_args+=(-F "translationGlossary=${translation_glossary}")
  fi

  demo_curl -fsS "${form_args[@]}" "$base_url/api/media/uploads"
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

download_reviewed_subtitle_workflow_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/reviewed-subtitle-workflow" -o "$output_path"
}

download_demo_session_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/demo-session" -o "$output_path"
}

download_local_auth_session_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/auth/session" -o "$output_path"
}

login_local_auth_json() {
  local base_url="$1"
  local username="$2"
  local password="$3"
  local output_path="$4"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS \
    -H "Content-Type: application/json" \
    -X POST \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}" \
    "$base_url/api/auth/login" \
    -o "$output_path"
}

print_demo_session_owner_summary_file() {
  local session_json_path="$1"

  python3 - "$session_json_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    session = json.load(handle)

print(f"demoSessionMode={session.get('mode', '')}")
print(f"demoSessionAuthenticated={str(bool(session.get('authenticated'))).lower()}")
print(f"demoOwnerId={session.get('ownerId', '')}")
print(f"demoOwnershipScope={session.get('ownershipScope', '')}")
PY
}

print_local_auth_summary_file() {
  local auth_json_path="$1"

  python3 - "$auth_json_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    body = json.load(handle)

session = body.get("session") if isinstance(body.get("session"), dict) else body

def text(value):
    return "" if value is None else str(value)

def bool_text(value):
    return str(bool(value)).lower()

print("localAuthEnabled=" + bool_text(session.get("enabled")))
print("localAuthConfigured=" + bool_text(session.get("configured")))
print("localAuthAuthenticated=" + bool_text(session.get("authenticated")))
print("localAuthOwnerId=" + text(session.get("ownerId")))
print("localAuthUsername=" + text(session.get("username")))
print("localAuthMode=" + text(session.get("authMode")))
print("localAuthTokenExpiresAt=" + text(body.get("expiresAt")))
PY
}

url_encode_path_segment() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

download_job_comparison_json() {
  local base_url="$1"
  local baseline_job_id="$2"
  local comparison_job_id="$3"
  local output_path="$4"
  local encoded_baseline_job_id
  local encoded_comparison_job_id
  encoded_baseline_job_id="$(url_encode_path_segment "$baseline_job_id")"
  encoded_comparison_job_id="$(url_encode_path_segment "$comparison_job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_baseline_job_id/comparison/$encoded_comparison_job_id" -o "$output_path"
}

download_job_comparison_markdown() {
  local base_url="$1"
  local baseline_job_id="$2"
  local comparison_job_id="$3"
  local output_path="$4"
  local encoded_baseline_job_id
  local encoded_comparison_job_id
  encoded_baseline_job_id="$(url_encode_path_segment "$baseline_job_id")"
  encoded_comparison_job_id="$(url_encode_path_segment "$comparison_job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_baseline_job_id/comparison/$encoded_comparison_job_id/markdown/download" -o "$output_path"
}

download_demo_run_matrix_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-run-matrix" -o "$output_path"
}

download_demo_presenter_pack_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-presenter-pack" -o "$output_path"
}

download_demo_run_monitor_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-run-monitor" -o "$output_path"
}

download_demo_run_monitor_markdown() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-run-monitor/markdown/download" -o "$output_path"
}

download_demo_replay_card_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-replay-card" -o "$output_path"
}

download_demo_completion_certificate_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-completion-certificate" -o "$output_path"
}

download_demo_acceptance_gate_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-acceptance-gate" -o "$output_path"
}

download_demo_share_sheet_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-share-sheet" -o "$output_path"
}

download_demo_share_sheet_markdown() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-share-sheet/markdown/download" -o "$output_path"
}

download_demo_run_snapshot_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-run-snapshot" -o "$output_path"
}

download_demo_run_snapshot_zip() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local encoded_job_id
  encoded_job_id="$(url_encode_path_segment "$job_id")"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$encoded_job_id/demo-run-snapshot/download" -o "$output_path"
}

download_owner_quota_preflight_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/media/uploads/preflight" -o "$output_path"
}

download_upload_readiness_json() {
  local base_url="$1"
  local demo_profile_id="$2"
  local output_path="$3"
  local query=""

  if [[ -n "${demo_profile_id//[[:space:]]/}" ]]; then
    query="?demoProfileId=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$demo_profile_id")"
  fi

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/media/uploads/readiness$query" -o "$output_path"
}

download_demo_sample_media_catalog_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/demo-sample-media-catalog" -o "$output_path"
}

download_demo_run_launcher_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/demo-run-launcher" -o "$output_path"
}

download_demo_presentation_cockpit_json() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local query=""

  if [[ -n "${job_id//[[:space:]]/}" ]]; then
    query="?jobId=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote_plus(sys.argv[1]))' "$job_id")"
  fi

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/demo-presentation-cockpit$query" -o "$output_path"
}

download_runtime_dependencies_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/runtime/dependencies" -o "$output_path"
}

download_owner_workspace_jobs_json() {
  local base_url="$1"
  local bearer_token="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  auth_curl "$bearer_token" -fsS "$base_url/api/jobs?limit=20&offset=0" -o "$output_path"
}

download_owner_workspace_upload_readiness_json() {
  local base_url="$1"
  local bearer_token="$2"
  local demo_profile_id="$3"
  local output_path="$4"
  local query=""

  if [[ -n "${demo_profile_id//[[:space:]]/}" ]]; then
    query="?demoProfileId=$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$demo_profile_id")"
  fi

  mkdir -p "$(dirname "$output_path")"
  auth_curl "$bearer_token" -fsS "$base_url/api/media/uploads/readiness$query" -o "$output_path"
}

download_owner_workspace_runtime_dependencies_json() {
  local base_url="$1"
  local bearer_token="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  auth_curl "$bearer_token" -fsS "$base_url/api/runtime/dependencies" -o "$output_path"
}

print_owner_workspace_summary_files() {
  local session_path="$1"
  local jobs_path="$2"
  local readiness_path="$3"

  python3 - "$session_path" "$jobs_path" "$readiness_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    session_body = json.load(handle)
with open(sys.argv[2], encoding="utf-8") as handle:
    jobs = json.load(handle)
with open(sys.argv[3], encoding="utf-8") as handle:
    readiness = json.load(handle)

session = session_body.get("session") if isinstance(session_body.get("session"), dict) else session_body

def text(value):
    return "" if value is None else str(value)

print("ownerWorkspaceAuthMode=" + text(session.get("authMode")))
print("ownerWorkspaceOwnerId=" + text(session.get("ownerId")))
print("ownerWorkspaceOwnershipScope=" + text(session.get("ownershipScope")))
print("ownerWorkspaceJobCount=" + text(jobs.get("total", len(jobs.get("jobs", [])))))
print("ownerWorkspaceUploadReadiness=" + text(readiness.get("overallStatus")))
PY
}

print_upload_readiness_summary_file() {
  local readiness_path="$1"

  python3 - "$readiness_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    readiness = json.load(handle)

def text(value):
    return "" if value is None else str(value)

print("uploadReadinessOverall=" + text(readiness.get("overallStatus")))
print("uploadReadinessOwnerId=" + text(readiness.get("ownerId")))
print("uploadReadinessDemoProfileId=" + text(readiness.get("demoProfileId")))
print("uploadReadinessGeneratedAt=" + text(readiness.get("generatedAt")))
for check in readiness.get("checks", []):
    print(
        "uploadReadinessCheck="
        + text(check.get("status"))
        + ":"
        + text(check.get("id"))
        + ":"
        + text(check.get("label"))
    )
for action in readiness.get("requiredActions", []):
    print("uploadReadinessRequiredAction=" + text(action))
for route in readiness.get("evidenceRoutes", []):
    print("uploadReadinessEvidenceRoute=" + text(route))
PY
}

print_demo_sample_media_catalog_summary_file() {
  local catalog_path="$1"

  python3 - "$catalog_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    catalog = json.load(handle)

def text(value):
    return "" if value is None else str(value)

def safe(value):
    return text(value).replace("\n", " ").replace("\r", " ").strip()

print("sampleCatalogOverall=" + text(catalog.get("overallStatus")))
print("sampleCatalogRecommended=" + text(catalog.get("recommendedSampleId")))
print("sampleCatalogDurationLimitSeconds=" + text(catalog.get("uploadDurationLimitSeconds")))
print("sampleCatalogGeneratedAt=" + text(catalog.get("generatedAt")))
for path in catalog.get("configuredPaths", []):
    print(
        "sampleCatalogPath="
        + safe(path.get("envVar"))
        + ":"
        + safe(path.get("status"))
        + ":"
        + safe(path.get("filename"))
        + ":"
        + text(path.get("sizeBytes"))
    )
for item in catalog.get("items", []):
    print(
        "sampleCatalogItem="
        + safe(item.get("id"))
        + ":"
        + safe(item.get("source"))
        + ":"
        + safe(item.get("title"))
    )
for command in catalog.get("commands", []):
    print("sampleCatalogCommand=" + safe(command.get("command")))
PY
}

print_demo_run_launcher_summary_file() {
  local launcher_path="$1"

  python3 - "$launcher_path" <<'PY'
import json
import sys

launcher = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return ""
    return str(value).replace("\n", " ")

print("demoRunLauncherOverall=" + text(launcher.get("overallStatus", "UNKNOWN")))
print("demoRunLauncherRecommendedSample=" + text(launcher.get("recommendedSampleId")))
print("demoRunLauncherRecommendedProfile=" + text(launcher.get("recommendedProfileId")))
print("demoRunLauncherNextCommand=" + text(launcher.get("recommendedNextCommand")))
print("demoRunLauncherGeneratedAt=" + text(launcher.get("generatedAt")))
for gate in launcher.get("gates", []):
    print(
        "demoRunLauncherGate="
        + text(gate.get("status"))
        + ":"
        + text(gate.get("id"))
        + ":"
        + text(gate.get("label"))
        + ":blocking="
        + ("true" if gate.get("blocking") else "false")
    )
for command in launcher.get("commands", []):
    value = text(command.get("command"))
    if value:
        print("demoRunLauncherCommand=" + value)
for evidence in launcher.get("expectedEvidence", []):
    label = text(evidence.get("label"))
    path = text(evidence.get("path"))
    if label or path:
        print("demoRunLauncherEvidence=" + label + ":" + path)
PY
}

print_demo_presentation_cockpit_summary_file() {
  local cockpit_path="$1"

  python3 - "$cockpit_path" <<'PY'
import json
import sys

cockpit = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return ""
    return str(value).replace("\n", " ").replace("\r", " ").strip()

def print_run(prefix, run):
    if not isinstance(run, dict):
        print(prefix + "JobId=")
        return
    print(prefix + "JobId=" + text(run.get("jobId")))
    print(prefix + "VideoId=" + text(run.get("videoId")))
    print(prefix + "ProfileId=" + text(run.get("profileId")))
    print(prefix + "Status=" + text(run.get("status")))
    print(prefix + "Readiness=" + text(run.get("readiness")))
    print(prefix + "AcceptanceStatus=" + text(run.get("acceptanceStatus")))
    print(prefix + "CurrentStage=" + text(run.get("currentStage")))
    print(prefix + "NextAction=" + text(run.get("nextAction")))

print("demoPresentationCockpitOverall=" + text(cockpit.get("overallStatus", "UNKNOWN")))
print("demoPresentationCockpitPhase=" + text(cockpit.get("phase")))
print("demoPresentationCockpitNextAction=" + text(cockpit.get("recommendedNextAction")))
print("demoPresentationCockpitGeneratedAt=" + text(cockpit.get("generatedAt")))
print_run("demoPresentationCockpitSelectedRun", cockpit.get("selectedRun"))
print_run("demoPresentationCockpitActiveRun", cockpit.get("activeRun"))
print_run("demoPresentationCockpitRecommendedRun", cockpit.get("recommendedRun"))
for check in cockpit.get("checks", []):
    print(
        "demoPresentationCockpitCheck="
        + text(check.get("status"))
        + ":"
        + text(check.get("key"))
        + ":"
        + text(check.get("label"))
        + ":blocking="
        + ("true" if check.get("blocking") else "false")
    )
for link in cockpit.get("links", []):
    print(
        "demoPresentationCockpitLink="
        + text(link.get("kind"))
        + ":"
        + text(link.get("label"))
        + ":"
        + text(link.get("url"))
    )
for note in cockpit.get("safetyNotes", []):
    print("demoPresentationCockpitSafetyNote=" + text(note))
PY
}

print_worker_topology_summary_file() {
  local runtime_dependencies_path="$1"

  python3 - "$runtime_dependencies_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    body = json.load(handle)

worker = ((body.get("readiness") or {}).get("worker") or {})

def text(value):
    return "" if value is None else str(value)

print("workerTopologyRole=" + text(worker.get("role")))
print("workerTopologyListenerQueue=" + text(worker.get("listenerQueue")))
print(
    "workerTopologyFfmpegRoute="
    + text(worker.get("ffmpegJobQueue"))
    + ":"
    + text(worker.get("ffmpegRoutingKey"))
)
print(
    "workerTopologyOpenaiRoute="
    + text(worker.get("openaiJobQueue"))
    + ":"
    + text(worker.get("openaiRoutingKey"))
)
for command in worker.get("recommendedCommands", []):
    print("workerTopologyCommand=" + text(command))
PY
}

print_owner_quota_preflight_summary_file() {
  local preflight_path="$1"

  python3 - "$preflight_path" <<'PY'
import json
import sys
from decimal import Decimal

preflight = json.load(open(sys.argv[1], encoding="utf-8"), parse_float=Decimal)

def text(value):
    if value is None:
        return ""
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)

def bool_text(value):
    return str(bool(value)).lower()

print("ownerQuotaOwnerId=" + text(preflight.get("ownerId")))
print("ownerQuotaEnabled=" + bool_text(preflight.get("enabled")))
print("ownerQuotaAllowed=" + bool_text(preflight.get("allowed")))
print("ownerQuotaActiveJobs=" + text(preflight.get("activeJobs", 0)))
print("ownerQuotaQueuedJobs=" + text(preflight.get("queuedJobs", 0)))
print("ownerQuotaDailyEstimatedCostUsd=" + text(preflight.get("dailyEstimatedCostUsd", 0)))
print("ownerQuotaDailyBudgetDate=" + text(preflight.get("dailyBudgetDate")))
for limit in preflight.get("limits", []):
    print(
        "ownerQuotaLimit="
        + text(limit.get("name"))
        + ":enabled="
        + bool_text(limit.get("enabled"))
        + ":current="
        + text(limit.get("current", 0))
        + ":limit="
        + text(limit.get("limit", 0))
    )
for reason in preflight.get("blockingReasons", []):
    print("ownerQuotaBlockingReason=" + text(reason))
PY
}

print_job_comparison_summary_file() {
  local comparison_path="$1"

  python3 - "$comparison_path" <<'PY'
import json
import sys
from decimal import Decimal

comparison = json.load(open(sys.argv[1], encoding="utf-8"), parse_float=Decimal)

def text(value):
    if value is None:
        return "N/A"
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)

def setting_value(field, value):
    if field in {"translationGlossary"}:
        return "[present]" if value and str(value).strip() else "[empty]"
    return text(value) if str(value or "").strip() else "None"

baseline = comparison["baseline"]
candidate = comparison["comparison"]
delta = comparison["delta"]

print("comparisonBaselineJobId=" + comparison["baselineJobId"])
print("comparisonJobId=" + comparison["comparisonJobId"])
print("comparisonSameSourceVideo=" + str(comparison.get("sameSourceVideo", False)).lower())
print("comparisonBaselineProfile=" + text(baseline.get("demoProfileId")))
print("comparisonProfile=" + text(candidate.get("demoProfileId")))
print("comparisonBaselineStatus=" + text(baseline.get("status")))
print("comparisonStatus=" + text(candidate.get("status")))
print("comparisonQualityDelta=" + text(delta.get("qualityScore")))
print("comparisonModelCallDelta=" + text(delta.get("modelCallCount")))
print("comparisonEstimatedCostDeltaUsd=" + text(delta.get("estimatedCostUsd")))
print("comparisonArtifactCacheHitDelta=" + text(delta.get("artifactCacheHitCount")))
print("comparisonProviderCacheHitDelta=" + text(delta.get("providerCacheHitCount")))
print("comparisonGeneratedArtifactDelta=" + text(delta.get("generatedArtifactCount")))
for diff in comparison.get("settingDiffs", []):
    field = diff["field"]
    baseline_value = setting_value(field, diff.get("baselineValue"))
    comparison_value = setting_value(field, diff.get("comparisonValue"))
    print("comparisonSettingDiff=" + field + ":" + baseline_value + "->" + comparison_value)
PY
}

print_demo_run_matrix_summary_file() {
  local matrix_path="$1"

  python3 - "$matrix_path" <<'PY'
import json
import sys
from decimal import Decimal

matrix = json.load(open(sys.argv[1], encoding="utf-8"), parse_float=Decimal)

def text(value):
    if value is None:
        return "N/A"
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)

def flag(value):
    return str(bool(value)).lower()

jobs = matrix.get("jobs") or []

print("demoRunMatrixAnchorJobId=" + text(matrix.get("anchorJobId")))
print("demoRunMatrixVideoId=" + text(matrix.get("videoId")))
print("demoRunMatrixRunCount=" + str(len(jobs)))
print("demoRunMatrixRecommendedBaselineJobId=" + text(matrix.get("recommendedBaselineJobId")))
print("demoRunMatrixBestQualityJobId=" + text(matrix.get("bestQualityJobId")))
print("demoRunMatrixLowestCostJobId=" + text(matrix.get("lowestCostJobId")))
for job in jobs:
    print("demoRunMatrixRun=" + ":".join([
        text(job.get("jobId")),
        text(job.get("demoProfileId") or "manual"),
        text(job.get("status")),
        "quality=" + text(job.get("qualityScore")),
        "cost=" + text(job.get("estimatedCostUsd")),
        "modelCalls=" + text(job.get("modelCallCount", 0)),
        "providerCacheHits=" + text(job.get("providerCacheHitCount", 0)),
        "handoffReady=" + flag(job.get("handoffReady")),
    ]))
PY
}

print_demo_presenter_pack_summary_file() {
  local pack_path="$1"

  python3 - "$pack_path" <<'PY'
import json
import sys
from decimal import Decimal

pack = json.load(open(sys.argv[1], encoding="utf-8"), parse_float=Decimal)

def text(value):
    if value is None:
        return "N/A"
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)

def flag(value):
    return str(bool(value)).lower()

runs = pack.get("runs") or []
downloads = pack.get("downloads") or []

print("demoPresenterPackAnchorJobId=" + text(pack.get("anchorJobId")))
print("demoPresenterPackVideoId=" + text(pack.get("videoId")))
print("demoPresenterPackReadiness=" + text(pack.get("readinessStatus")))
print("demoPresenterPackRunCount=" + str(len(runs)))
print("demoPresenterPackRecommendedBaselineJobId=" + text(pack.get("recommendedBaselineJobId")))
print("demoPresenterPackBestQualityJobId=" + text(pack.get("bestQualityJobId")))
print("demoPresenterPackLowestCostJobId=" + text(pack.get("lowestCostJobId")))
for run in runs:
    roles = ",".join(run.get("roles") or [])
    print("demoPresenterPackRun=" + ":".join([
        text(run.get("jobId")),
        text(run.get("demoProfileId") or "manual"),
        text(run.get("status")),
        "roles=" + roles,
        "quality=" + text(run.get("qualityScore")),
        "cost=" + text(run.get("estimatedCostUsd")),
        "modelCalls=" + text(run.get("modelCallCount", 0)),
        "providerCacheHits=" + text(run.get("providerCacheHitCount", 0)),
        "handoffReady=" + flag(run.get("handoffReady")),
    ]))
for download in downloads:
    print("demoPresenterPackDownload=" + text(download.get("kind")) + ":" + text(download.get("url")))
PY
}

print_demo_replay_card_summary_file() {
  local card_path="$1"

  python3 - "$card_path" <<'PY'
import json
import sys
from decimal import Decimal

card = json.load(open(sys.argv[1], encoding="utf-8"), parse_float=Decimal)

def text(value):
    if value is None:
        return "N/A"
    if isinstance(value, Decimal):
        return format(value, "f")
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "/Users/", "sk-", "provider payload", "OPENAI_API_KEY")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

print("demoReplayCardJobId=" + text(card.get("jobId")))
print("demoReplayCardVideoId=" + text(card.get("videoId")))
print("demoReplayCardReadiness=" + text(card.get("readiness")))
print("demoReplayCardProfile=" + text(card.get("demoProfileId") or "manual"))
print("demoReplayCardRecommendedBaselineJobId=" + text(card.get("recommendedBaselineJobId")))
print("demoReplayCardBestQualityJobId=" + text(card.get("bestQualityJobId")))
print("demoReplayCardLowestCostJobId=" + text(card.get("lowestCostJobId")))
print("demoReplayCardQualityScore=" + text(card.get("qualityScore")))
print("demoReplayCardEstimatedCostUsd=" + text(card.get("estimatedCostUsd")))
print("demoReplayCardModelCallCount=" + text(card.get("modelCallCount", 0)))
print("demoReplayCardProviderCacheHitCount=" + text(card.get("providerCacheHitCount", 0)))
for setting in card.get("settings", []):
    print("demoReplayCardSetting=" + text(setting.get("key")) + ":" + safe(setting.get("value")))
for command in card.get("commands", []):
    print("demoReplayCardCommand=" + text(command.get("kind")) + ":" + safe(command.get("command")))
for link in card.get("links", []):
    print("demoReplayCardLink=" + text(link.get("kind")) + ":" + safe(link.get("url")))
for note in card.get("safetyNotes", []):
    print("demoReplayCardSafetyNote=" + safe(note))
PY
}

print_demo_completion_certificate_summary_file() {
  local certificate_path="$1"

  python3 - "$certificate_path" <<'PY'
import json
import sys

certificate = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return "N/A"
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "/Users/", "sk-", "provider payload", "OPENAI_API_KEY")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

print("demoCompletionCertificateJobId=" + text(certificate.get("jobId")))
print("demoCompletionCertificateVideoId=" + text(certificate.get("videoId")))
print("demoCompletionCertificateStatus=" + text(certificate.get("certificateStatus")))
print("demoCompletionCertificateJobStatus=" + text(certificate.get("jobStatus")))
print("demoCompletionCertificateProfile=" + text(certificate.get("demoProfileId") or "manual"))
print("demoCompletionCertificateRecommendedBaselineJobId=" + text(certificate.get("recommendedBaselineJobId")))
print("demoCompletionCertificateBestQualityJobId=" + text(certificate.get("bestQualityJobId")))
print("demoCompletionCertificateLowestCostJobId=" + text(certificate.get("lowestCostJobId")))
print("demoCompletionCertificateNextAction=" + safe(certificate.get("recommendedNextAction")))
for check in certificate.get("checks", []):
    print("demoCompletionCertificateCheck=" + ":".join([
        text(check.get("key")),
        text(check.get("status")),
        "blocking=" + str(bool(check.get("blocking"))).lower(),
    ]))
for section in certificate.get("sections", []):
    print("demoCompletionCertificateSection=" + text(section.get("key")) + ":" + text(section.get("status")))
for link in certificate.get("links", []):
    print("demoCompletionCertificateLink=" + text(link.get("kind")) + ":" + safe(link.get("url")))
for note in certificate.get("safetyNotes", []):
    print("demoCompletionCertificateSafetyNote=" + safe(note))
PY
}

print_demo_acceptance_gate_summary_file() {
  local gate_path="$1"

  python3 - "$gate_path" <<'PY'
import json
import sys

gate = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return "N/A"
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "/Users/", "sk-", "provider payload", "OPENAI_API_KEY")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

print("demoAcceptanceGateJobId=" + text(gate.get("jobId")))
print("demoAcceptanceGateVideoId=" + text(gate.get("videoId")))
print("demoAcceptanceGateStatus=" + text(gate.get("gateStatus")))
print("demoAcceptanceGateJobStatus=" + text(gate.get("jobStatus")))
print("demoAcceptanceGateProfile=" + text(gate.get("demoProfileId") or "manual"))
print("demoAcceptanceGateNextAction=" + safe(gate.get("recommendedNextAction")))
for check in gate.get("checks", []):
    print("demoAcceptanceGateCheck=" + ":".join([
        text(check.get("key")),
        text(check.get("status")),
        "required=" + str(bool(check.get("required"))).lower(),
    ]))
for item in gate.get("evidence", []):
    print("demoAcceptanceGateEvidence=" + text(item.get("key")) + ":" + safe(item.get("value")) + ":" + text(item.get("status")))
for link in gate.get("links", []):
    print("demoAcceptanceGateLink=" + text(link.get("kind")) + ":" + safe(link.get("url")))
for note in gate.get("safetyNotes", []):
    print("demoAcceptanceGateSafetyNote=" + safe(note))
PY
}

print_demo_share_sheet_summary_file() {
  local sheet_path="$1"

  python3 - "$sheet_path" <<'PY'
import json
import sys

sheet = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return "N/A"
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "/Users/", "sk-", "provider payload")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

print("demoShareSheetJobId=" + text(sheet.get("jobId")))
print("demoShareSheetVideoId=" + text(sheet.get("videoId")))
print("demoShareSheetReadiness=" + text(sheet.get("readiness")))
print("demoShareSheetHeadline=" + safe(sheet.get("headline")))
print("demoShareSheetRecommendedNextAction=" + safe(sheet.get("recommendedNextAction")))
for outcome in sheet.get("outcomeBullets") or []:
    safe_outcome = safe(outcome)
    if safe_outcome != "REDACTED_UNSAFE_DETAIL":
        print("demoShareSheetOutcome=" + safe_outcome)
for link in sheet.get("links") or []:
    print("demoShareSheetLink=" + text(link.get("kind")) + ":" + text(link.get("url")))
PY
}

print_demo_run_monitor_summary_file() {
  local monitor_path="$1"

  python3 - "$monitor_path" <<'PY'
import json
import sys

monitor = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return "N/A"
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "raw subtitle text", "/Users/", "sk-", "provider payload", "objectKey")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

status = text(monitor.get("status"))
terminal = "true" if status in ("COMPLETED", "FAILED", "CANCELLED") else "false"
print("demoRunMonitorJobId=" + text(monitor.get("jobId")))
print("demoRunMonitorVideoId=" + text(monitor.get("videoId")))
print("demoRunMonitorStatus=" + status)
print("demoRunMonitorDispatchStatus=" + text(monitor.get("dispatchStatus")))
print("demoRunMonitorAttentionLevel=" + text(monitor.get("attentionLevel")))
print("demoRunMonitorCurrentStage=" + text(monitor.get("currentStage")))
print("demoRunMonitorElapsedMs=" + text(monitor.get("elapsedMs")))
print("demoRunMonitorCompletedStageCount=" + text(monitor.get("completedStageCount", 0)))
print("demoRunMonitorTotalStageCount=" + text(monitor.get("totalStageCount", 0)))
print("demoRunMonitorFailedStageCount=" + text(monitor.get("failedStageCount", 0)))
print("demoRunMonitorSlowestStage=" + text(monitor.get("slowestStage")))
print("demoRunMonitorSlowestStageDurationMs=" + text(monitor.get("slowestStageDurationMs")))
print("demoRunMonitorTerminal=" + terminal)
summary = safe(monitor.get("summary"))
if summary != "REDACTED_UNSAFE_DETAIL":
    print("demoRunMonitorSummary=" + summary)
next_action = safe(monitor.get("recommendedNextAction"))
if next_action != "REDACTED_UNSAFE_DETAIL":
    print("demoRunMonitorRecommendedNextAction=" + next_action)
for stage in monitor.get("stages") or []:
    print(":".join([
        "demoRunMonitorStage=" + text(stage.get("stage")),
        text(stage.get("status")),
        "attention=" + text(stage.get("attention")),
        "durationMs=" + text(stage.get("durationMs")),
        "runningForMs=" + text(stage.get("runningForMs")),
    ]))
for link in monitor.get("links") or []:
    print("demoRunMonitorLink=" + text(link.get("kind")) + ":" + text(link.get("url")))
PY
}

print_demo_run_snapshot_summary_file() {
  local snapshot_path="$1"

  python3 - "$snapshot_path" <<'PY'
import json
import sys

snapshot = json.load(open(sys.argv[1], encoding="utf-8"))

def text(value):
    if value is None:
        return "N/A"
    return str(value)

def safe(value):
    value = text(value)
    unsafe_markers = ("raw transcript text", "raw subtitle text", "/Users/", "sk-", "provider payload", "objectKey")
    if any(marker in value for marker in unsafe_markers):
        return "REDACTED_UNSAFE_DETAIL"
    return value

print("demoRunSnapshotJobId=" + text(snapshot.get("jobId")))
print("demoRunSnapshotVideoId=" + text(snapshot.get("videoId")))
print("demoRunSnapshotReadiness=" + text(snapshot.get("readiness")))
print("demoRunSnapshotHeadline=" + safe(snapshot.get("headline")))
print("demoRunSnapshotSectionCount=" + str(len(snapshot.get("sections") or [])))
print("demoRunSnapshotPackageEntryCount=" + str(len(snapshot.get("packageEntries") or [])))
summary = safe(snapshot.get("summary"))
if summary != "REDACTED_UNSAFE_DETAIL":
    print("demoRunSnapshotSummary=" + summary)
for section in snapshot.get("sections") or []:
    print("demoRunSnapshotSection=" + text(section.get("kind")) + ":" + text(section.get("filename")) + ":" + text(section.get("status")))
for entry in snapshot.get("packageEntries") or []:
    print("demoRunSnapshotEntry=" + text(entry))
for link in snapshot.get("links") or []:
    print("demoRunSnapshotLink=" + text(link.get("kind")) + ":" + text(link.get("url")))
PY
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

print_quality_evaluation_summary_file() {
  local job_detail_path="$1"

  python3 - "$job_detail_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
evaluation = job.get("qualityEvaluation")
print("qualityEvaluationJobId=" + job["jobId"])
if not evaluation:
    print("qualityEvaluationStatus=NOT_RECORDED")
else:
    print("qualityEvaluationStatus=" + str(evaluation.get("status")))
    print("qualityEvaluationScore=" + str(evaluation.get("score")))
    print("qualityEvaluationVerdict=" + str(evaluation.get("verdict")))
    print("qualityEvaluationLanguage=" + str(evaluation.get("language")))
    print("qualityEvaluationIssueCount=" + str(len(evaluation.get("issues") or [])))
    print("qualityEvaluationSuggestedFixCount=" + str(len(evaluation.get("suggestedFixes") or [])))
PY
}

write_quality_evaluation_evidence_markdown() {
  local job_detail_path="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$job_detail_path" "$output_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
output_path = sys.argv[2]
evaluation = job.get("qualityEvaluation")
job_id = job["jobId"]
target_language = job.get("targetLanguage") or "N/A"
demo_profile_id = job.get("demoProfileId") or "manual"
translation_style = job.get("translationStyle") or "NATURAL"
subtitle_style_preset = job.get("subtitleStylePreset") or "STANDARD"
subtitle_polishing_mode = job.get("subtitlePolishingMode") or "OFF"
glossary_count = int(job.get("translationGlossaryEntryCount") or 0)
glossary_hash = job.get("translationGlossaryHash") or "none"
lines = [
    "# LinguaFrame Quality Evaluation Evidence",
    "",
    "## Job",
    "- Job: " + job_id,
    "- Video: " + str(job.get("videoId") or "N/A"),
    "- Target language: " + target_language,
    "- Demo profile: " + demo_profile_id,
    "- Translation style: " + translation_style,
    "- Subtitle style: " + subtitle_style_preset,
    "- Subtitle polishing: " + subtitle_polishing_mode,
    f"- Translation glossary: {glossary_count} entries / {glossary_hash}",
    "- Job status: " + str(job.get("status") or "N/A"),
    "- Created at: " + str(job.get("createdAt") or "N/A"),
    "",
]
if not evaluation:
    lines.extend([
        "## Evaluation",
        "- Status: NOT_RECORDED",
        "- Quality evaluation has not been recorded for this job.",
        "",
    ])
else:
    lines.extend([
        "## Evaluation",
        "- Status: " + str(evaluation.get("status") or "N/A"),
        "- Score: " + str(evaluation.get("score")) + " / 100",
        "- Verdict: " + str(evaluation.get("verdict") or "N/A"),
        "- Evaluation language: " + str(evaluation.get("language") or "N/A"),
        "- Created at: " + str(evaluation.get("createdAt") or "N/A"),
        "",
        "## Dimensions",
        "- Completeness: " + str(evaluation.get("completeness")) + " / 100",
        "- Readability: " + str(evaluation.get("readability")) + " / 100",
        "- Timing preservation: " + str(evaluation.get("timingPreservation")) + " / 100",
        "- Naturalness: " + str(evaluation.get("naturalness")) + " / 100",
        "",
        "## Issues",
        "- Issue count: " + str(len(evaluation.get("issues") or [])),
    ])
    lines.extend("- " + issue for issue in (evaluation.get("issues") or ["None recorded."]))
    lines.extend(["", "## Suggested Fixes", "- Suggested fix count: " + str(len(evaluation.get("suggestedFixes") or []))])
    lines.extend("- " + fix for fix in (evaluation.get("suggestedFixes") or ["None recorded."]))
    lines.append("")
lines.extend([
    "## Related Safe Routes",
    "- Job detail: /api/jobs/" + job_id,
    "- Diagnostics: /api/jobs/" + job_id + "/diagnostics/download",
    "- Backend evidence: /api/jobs/" + job_id + "/evidence/markdown/download",
    "- Backend quality evidence: /api/jobs/" + job_id + "/quality-evaluation/evidence/markdown/download",
    "- Subtitle review: /api/jobs/" + job_id + "/subtitle-review?language=" + target_language,
    "",
])
open(output_path, "w", encoding="utf-8").write("\n".join(lines))
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

get_job_subtitle_review() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"

  demo_curl -fsS "$base_url/api/jobs/$job_id/subtitle-review?language=$language"
}

get_job_subtitle_draft() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"

  demo_curl -fsS "$base_url/api/jobs/$job_id/subtitle-draft?language=$language"
}

publish_reviewed_subtitles() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"
  local include_burned_video="${4:-false}"

  demo_curl -fsS -X POST \
    -H "Content-Type: application/json" \
    -d "{\"language\":\"$language\",\"includeBurnedVideo\":$include_burned_video}" \
    "$base_url/api/jobs/$job_id/subtitle-draft/publish"
}

get_delivery_manifest() {
  local base_url="$1"
  local job_id="$2"

  demo_curl -fsS "$base_url/api/jobs/$job_id/delivery-manifest"
}

download_delivery_manifest_markdown() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/delivery-manifest/markdown/download" -o "$output_path"
}

download_handoff_package() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/handoff-package/download" -o "$output_path"
}

download_demo_run_package() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/demo-run-package/download" -o "$output_path"
}

download_ai_audit_package() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/ai-audit-package/download" -o "$output_path"
}

print_subtitle_review_summary() {
  python3 -c '
import json
import sys

review = json.load(sys.stdin)
score = review.get("qualityScore")
verdict = review.get("qualityVerdict") or "No verdict"
print("subtitleReviewJobId=" + review["jobId"])
print("subtitleReviewLanguage=" + review["targetLanguage"])
print("subtitleReviewSegmentCount=" + str(review.get("segmentCount", 0)))
print("subtitleReviewMissingTargetCount=" + str(review.get("missingTargetCount", 0)))
print("subtitleReviewTimingMismatchCount=" + str(review.get("timingMismatchCount", 0)))
print("subtitleReviewQuality=" + ("Not evaluated" if score is None else str(score) + " / 100, " + verdict))
print("subtitleReviewSubtitleArtifactCount=" + str(review.get("downloadableSubtitleArtifactCount", 0)))
'
}

print_subtitle_draft_summary() {
  python3 -c '
import json
import sys

draft = json.load(sys.stdin)
print("subtitleDraftJobId=" + draft["jobId"])
print("subtitleDraftLanguage=" + draft["targetLanguage"])
print("subtitleDraftSegmentCount=" + str(draft.get("segmentCount", 0)))
print("subtitleDraftEditedSegmentCount=" + str(draft.get("editedSegmentCount", 0)))
print("subtitleDraftLastUpdated=" + str(draft.get("lastUpdatedAt") or "Not saved"))
'
}

print_reviewed_publish_summary() {
  python3 -c '
import json
import sys

publish = json.load(sys.stdin)
artifacts = publish.get("artifacts", [])
subtitle_types = {"REVIEWED_SUBTITLE_JSON", "REVIEWED_SUBTITLE_SRT", "REVIEWED_SUBTITLE_VTT"}
subtitle_count = sum(1 for artifact in artifacts if artifact.get("type") in subtitle_types)
burned_video_count = sum(1 for artifact in artifacts if artifact.get("type") == "REVIEWED_BURNED_VIDEO")
print("reviewedPublishJobId=" + publish["jobId"])
print("reviewedPublishLanguage=" + publish["targetLanguage"])
print("reviewedPublishArtifactCount=" + str(len(artifacts)))
print("reviewedPublishBurnedVideoRequested=" + str(publish.get("burnedVideoRequested", False)).lower())
print("reviewedPublishBurnedVideoCreated=" + str(publish.get("burnedVideoCreated", False)).lower())
print("reviewedPublishSubtitleArtifactCount=" + str(subtitle_count))
print("reviewedPublishBurnedVideoArtifactCount=" + str(burned_video_count))
for artifact in artifacts:
    print("reviewedPublishArtifact=" + artifact.get("type", "UNKNOWN") + ":" + artifact.get("filename", "unnamed"))
'
}

print_delivery_manifest_summary() {
  python3 -c '
import json
import sys

manifest = json.load(sys.stdin)
print("deliveryManifestJobId=" + manifest["jobId"])
print("deliveryManifestHandoffReady=" + str(manifest.get("handoffReady", False)).lower())
print("deliveryManifestReviewedSubtitleArtifactCount=" + str(manifest.get("reviewedSubtitleArtifactCount", 0)))
print("deliveryManifestReviewedBurnedVideoAvailable=" + str(manifest.get("reviewedBurnedVideoAvailable", False)).lower())
print("deliveryManifestGeneratedArtifactCount=" + str(manifest.get("generatedArtifactCount", 0)))
print("deliveryManifestLinkCount=" + str(len(manifest.get("links", []))))
for artifact in manifest.get("reviewedArtifacts", []):
    print("deliveryManifestReviewedArtifact=" + artifact.get("type", "UNKNOWN") + ":" + artifact.get("filename", "unnamed"))
'
}

print_media_delivery_summary() {
  python3 -c '
import json
import sys

media_types = {"DUBBING_AUDIO", "BURNED_VIDEO", "DUBBED_VIDEO", "REVIEWED_BURNED_VIDEO"}
artifacts = [artifact for artifact in json.load(sys.stdin) if artifact.get("type") in media_types]
print("mediaDeliveryReadyCount=" + str(len(artifacts)))
for artifact in artifacts:
    cache_state = "Reused" if artifact.get("cacheHit") else "Generated"
    print(
        "mediaDeliveryArtifact="
        + artifact.get("type", "UNKNOWN")
        + ":"
        + artifact.get("filename", "unnamed")
        + ":"
        + artifact.get("contentType", "unknown")
        + ":"
        + cache_state
    )
'
}

print_demo_handoff_checklist_summary() {
  local job_detail_path="$1"
  local delivery_manifest_path="$2"
  local artifacts_path="$3"

  python3 - "$job_detail_path" "$delivery_manifest_path" "$artifacts_path" <<'PY'
import json
import sys

job = json.load(open(sys.argv[1], encoding="utf-8"))
manifest = json.load(open(sys.argv[2], encoding="utf-8"))
artifacts = json.load(open(sys.argv[3], encoding="utf-8"))

artifact_types = [artifact.get("type") for artifact in artifacts]
reviewed_types = {"REVIEWED_SUBTITLE_JSON", "REVIEWED_SUBTITLE_SRT", "REVIEWED_SUBTITLE_VTT"}
media_types = {"DUBBING_AUDIO", "BURNED_VIDEO", "DUBBED_VIDEO", "REVIEWED_BURNED_VIDEO"}
reviewed_count = sum(1 for artifact_type in artifact_types if artifact_type in reviewed_types)
media_count = sum(1 for artifact_type in artifact_types if artifact_type in media_types)

job_completed = job.get("status") == "COMPLETED"
handoff_ready = bool(manifest.get("handoffReady")) or reviewed_count >= 3
evidence_ready = True
overall = "READY" if job_completed and handoff_ready and evidence_ready else "ATTENTION"

def item(status, label):
    print("demoHandoffItem=" + status + ":" + label)

print("demoHandoffOverall=" + overall)
item("PASS" if job_completed else "FAIL", "Job completed")
item("PASS" if (job.get("pipelineProgress") or {}).get("terminal") or job.get("status") in {"COMPLETED", "FAILED", "CANCELLED"} else "WARN", "Pipeline terminal")
item("PASS" if handoff_ready else "FAIL", "Reviewed subtitles ready")
item("PASS" if media_count > 0 else "WARN", "Media outputs available")
item("PASS", "Evidence downloads ready")
item("PASS" if (job.get("usageSummary") or {}).get("modelCallCount", 0) > 0 else "WARN", "Cost and model-call evidence available")
cache = job.get("cacheSummary") or {}
item("PASS" if cache.get("cacheHitCount", 0) > 0 or cache.get("providerCacheHitCount", 0) > 0 else "WARN", "Cache evidence available")
if job.get("failureTriage"):
    item("PASS", "Failure triage available")
PY
}

write_demo_session_report_markdown() {
  local job_detail_path="$1"
  local delivery_manifest_path="$2"
  local artifacts_path="$3"
  local output_path="$4"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$job_detail_path" "$delivery_manifest_path" "$artifacts_path" "$output_path" <<'PY'
import json
import sys
from datetime import datetime, timezone

job = json.load(open(sys.argv[1], encoding="utf-8"))
manifest = json.load(open(sys.argv[2], encoding="utf-8"))
artifacts = json.load(open(sys.argv[3], encoding="utf-8"))
output_path = sys.argv[4]

artifact_types = [artifact.get("type") for artifact in artifacts]
reviewed_types = {"REVIEWED_SUBTITLE_JSON", "REVIEWED_SUBTITLE_SRT", "REVIEWED_SUBTITLE_VTT"}
media_types = {"DUBBING_AUDIO", "BURNED_VIDEO", "DUBBED_VIDEO", "REVIEWED_BURNED_VIDEO"}
reviewed_count = sum(1 for artifact_type in artifact_types if artifact_type in reviewed_types)
media_count = sum(1 for artifact_type in artifact_types if artifact_type in media_types)
job_id = job.get("jobId", "unknown")
video_id = job.get("videoId", "unknown")
target_language = job.get("targetLanguage", "unknown")
demo_profile_id = job.get("demoProfileId") or "manual"
translation_style = job.get("translationStyle", "NATURAL")
subtitle_style_preset = job.get("subtitleStylePreset", "STANDARD")
subtitle_polishing_mode = job.get("subtitlePolishingMode", "OFF")
glossary_count = int(job.get("translationGlossaryEntryCount") or 0)
glossary_hash = job.get("translationGlossaryHash") or "none"
status = job.get("status", "UNKNOWN")
pipeline = job.get("pipelineProgress") or {}
usage = job.get("usageSummary") or {}
cache = job.get("cacheSummary") or {}
handoff_ready = bool(manifest.get("handoffReady")) and reviewed_count >= 3
overall = "READY" if status == "COMPLETED" and handoff_ready else "ATTENTION"

lines = [
    "# LinguaFrame Demo Session Report",
    "",
    f"- Job: {job_id}",
    f"- Overall: {overall}",
    f"- Generated at: {datetime.now(timezone.utc).isoformat()}",
    "",
    "## Input and job",
    f"- Video: {video_id}",
    f"- Target language: {target_language}",
    f"- Demo profile: {demo_profile_id}",
    f"- Translation style: {translation_style}",
    f"- Subtitle style: {subtitle_style_preset}",
    f"- Subtitle polishing: {subtitle_polishing_mode}",
    f"- Translation glossary: {glossary_count} entries / {glossary_hash}",
    f"- Status: {status}",
    f"- Retries: {job.get('retryCount', 0)}",
    f"- Terminal: {'yes' if pipeline.get('terminal') or status in {'COMPLETED', 'FAILED', 'CANCELLED'} else 'no'}",
    "",
    "## Generated outputs",
    f"- Artifacts: {len(artifacts)}",
    f"- Reviewed subtitle artifacts: {reviewed_count}",
    f"- Media outputs: {media_count}",
    f"- Result bundle: /api/jobs/{job_id}/artifacts/archive/download",
    "",
    "## Handoff evidence",
    f"- Delivery manifest ready: {'yes' if manifest.get('handoffReady') else 'no'}",
    f"- Reviewed burned video: {'yes' if manifest.get('reviewedBurnedVideoAvailable') else 'no'}",
    f"- Diagnostics: /api/jobs/{job_id}/diagnostics/download",
    f"- Evidence bundle: /api/jobs/{job_id}/evidence/bundle/download",
    "",
    "## Cost and cache",
    f"- Model calls: {usage.get('modelCallCount', 0)}",
    f"- Failed model calls: {usage.get('failedModelCallCount', 0)}",
    f"- Estimated cost USD: {usage.get('estimatedCostUsd', 0)}",
    f"- Artifact cache hits: {cache.get('cacheHitCount', 0)}",
    f"- Provider cache hits: {cache.get('providerCacheHitCount', 0)}",
]

triage = job.get("failureTriage")
if triage:
    lines.extend([
        "",
        "## Failure triage",
        f"- Category: {triage.get('category', 'UNKNOWN')}",
        f"- Retryable: {'yes' if triage.get('retryable') else 'no'}",
        f"- Recommended action: {triage.get('recommendedAction', 'Inspect diagnostics before retrying.')}",
    ])

with open(output_path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
PY
}

get_private_demo_operations() {
  local base_url="$1"
  demo_curl -fsS "$base_url/api/operator/private-demo/operations"
}

print_private_demo_operations_summary() {
  python3 -c '
import json
import sys

operations = json.load(sys.stdin)
print("privateDemoOperationsOverall=" + operations.get("overallStatus", "UNKNOWN"))
print("privateDemoOperationsReadyCount=" + str(operations.get("readyCount", 0)))
print("privateDemoOperationsAttentionCount=" + str(operations.get("attentionCount", 0)))
print("privateDemoOperationsBlockedCount=" + str(operations.get("blockedCount", 0)))
for section in operations.get("sections", []):
    print("privateDemoOperationsSection=" + section.get("status", "UNKNOWN") + ":" + section.get("title", "Untitled"))
for command in operations.get("commands", []):
    print("privateDemoOperationsCommand=" + command.get("command", ""))
'
}

write_private_demo_operations_report() {
  local operations_path="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$operations_path" "$output_path" <<'PY'
import json
import sys

operations = json.load(open(sys.argv[1], encoding="utf-8"))
output_path = sys.argv[2]

lines = [
    "# LinguaFrame Private Demo Operations Report",
    "",
    f"- Overall: {operations.get('overallStatus', 'UNKNOWN')}",
    f"- Generated at: {operations.get('generatedAt', 'unknown')}",
    f"- Ready: {operations.get('readyCount', 0)}",
    f"- Attention: {operations.get('attentionCount', 0)}",
    f"- Blocked: {operations.get('blockedCount', 0)}",
    "",
    "## Checks",
]

for section in operations.get("sections", []):
    section_title = section.get("title", "Untitled")
    for check in section.get("checks", []):
        lines.append(
            f"- {check.get('status', 'UNKNOWN')} {section_title} / "
            f"{check.get('label', 'check')}: {check.get('detail', '')}"
        )
        next_action = check.get("nextAction")
        if next_action:
            lines.append(f"  Next: {next_action}")

lines.extend(["", "## Commands"])
for command in operations.get("commands", []):
    value = command.get("command")
    if value:
        lines.append(f"- {value}")

lines.extend(["", "## Documentation"])
for link in operations.get("documentationLinks", []):
    path = link.get("path")
    if path:
        detail = link.get("detail", "")
        lines.append(f"- {path}: {detail}")

with open(output_path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
PY
}

download_private_demo_launch_rehearsal_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/private-demo/launch-rehearsal" -o "$output_path"
}

print_private_demo_launch_rehearsal_summary_file() {
  local rehearsal_path="$1"

  python3 - "$rehearsal_path" <<'PY'
import json
import sys

rehearsal = json.load(open(sys.argv[1], encoding="utf-8"))
print("privateDemoLaunchOverall=" + rehearsal.get("overallStatus", "UNKNOWN"))
print("privateDemoLaunchReadyCount=" + str(rehearsal.get("readyCount", 0)))
print("privateDemoLaunchAttentionCount=" + str(rehearsal.get("attentionCount", 0)))
print("privateDemoLaunchBlockedCount=" + str(rehearsal.get("blockedCount", 0)))
print("privateDemoLaunchRecommendedNextStepId=" + rehearsal.get("recommendedNextStepId", ""))
for step in rehearsal.get("steps", []):
    print(
        "privateDemoLaunchStep="
        + step.get("status", "UNKNOWN")
        + ":"
        + step.get("id", "")
        + ":"
        + step.get("title", "")
        + ":blocking="
        + ("true" if step.get("blocking") else "false")
    )
for route in rehearsal.get("evidenceDownloads", []):
    print("privateDemoLaunchEvidence=" + route)
PY
}

write_private_demo_launch_rehearsal_report() {
  local rehearsal_path="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$rehearsal_path" "$output_path" <<'PY'
import json
import sys

rehearsal = json.load(open(sys.argv[1], encoding="utf-8"))
output_path = sys.argv[2]

lines = [
    "# LinguaFrame Private Demo Launch Rehearsal",
    "",
    f"- Overall: {rehearsal.get('overallStatus', 'UNKNOWN')}",
    f"- Generated at: {rehearsal.get('generatedAt', 'unknown')}",
    f"- Ready: {rehearsal.get('readyCount', 0)}",
    f"- Attention: {rehearsal.get('attentionCount', 0)}",
    f"- Blocked: {rehearsal.get('blockedCount', 0)}",
    f"- Recommended next step: {rehearsal.get('recommendedNextStepId', '')}",
    "",
    "## Steps",
]

for step in rehearsal.get("steps", []):
    lines.append(f"- {step.get('status', 'UNKNOWN')} {step.get('id', '')}: {step.get('title', '')}")
    command = step.get("command")
    if command:
        lines.append(f"  Command: {command}")
    evidence = step.get("evidencePath")
    if evidence:
        lines.append(f"  Evidence: {evidence}")
    next_action = step.get("nextAction")
    if next_action:
        lines.append(f"  Next: {next_action}")
    if step.get("blocking"):
        lines.append("  Blocking: yes")

lines.extend(["", "## Evidence Routes"])
for route in rehearsal.get("evidenceDownloads", []):
    lines.append(f"- {route}")

with open(output_path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
PY
}

download_private_demo_evidence_gallery_json() {
  local base_url="$1"
  local output_path="$2"
  local limit="${3:-20}"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/private-demo/evidence-gallery?limit=$limit" -o "$output_path"
}

download_private_demo_run_archive_json() {
  local base_url="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/operator/private-demo/run-archive" -o "$output_path"
}

print_private_demo_evidence_gallery_summary_file() {
  local gallery_path="$1"

  python3 - "$gallery_path" <<'PY'
import json
import sys

gallery = json.load(open(sys.argv[1], encoding="utf-8"))
print("privateDemoEvidenceGalleryOverall=" + gallery.get("overallStatus", "UNKNOWN"))
print("privateDemoEvidenceGalleryCompletedJobCount=" + str(gallery.get("completedJobCount", 0)))
print("privateDemoEvidenceGalleryHandoffReadyCount=" + str(gallery.get("handoffReadyCount", 0)))
print("privateDemoEvidenceGalleryRecommendedJobId=" + str(gallery.get("recommendedJobId") or ""))
for job in gallery.get("jobs", []):
    print(
        "privateDemoEvidenceGalleryJob="
        + str(job.get("jobId", ""))
        + ":"
        + str(job.get("demoProfileId") or "manual")
        + ":handoff="
        + ("true" if job.get("handoffReady") else "false")
        + ":quality="
        + str(job.get("qualityScore") if job.get("qualityScore") is not None else "none")
    )
    for reason in job.get("attentionReasons", []):
        print("privateDemoEvidenceGalleryAttention=" + str(job.get("jobId", "")) + ":" + str(reason))
for download in gallery.get("galleryDownloads", []):
    print(
        "privateDemoEvidenceGalleryDownload="
        + str(download.get("label", ""))
        + ":"
        + str(download.get("href", ""))
    )
PY
}

write_private_demo_evidence_gallery_report() {
  local gallery_path="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$gallery_path" "$output_path" <<'PY'
import json
import sys

gallery = json.load(open(sys.argv[1], encoding="utf-8"))
output_path = sys.argv[2]

lines = [
    "# LinguaFrame Private Demo Evidence Gallery",
    "",
    f"- Overall: {gallery.get('overallStatus', 'UNKNOWN')}",
    f"- Generated at: {gallery.get('generatedAt', 'unknown')}",
    f"- Completed jobs: {gallery.get('completedJobCount', 0)}",
    f"- Handoff ready: {gallery.get('handoffReadyCount', 0)}",
    f"- Recommended job: {gallery.get('recommendedJobId') or 'none'}",
    "",
    "## Completed Runs",
]

for job in gallery.get("jobs", []):
    job_id = job.get("jobId", "")
    lines.append(f"- {job_id}: {job.get('filename', '')}")
    lines.append(f"  Profile: {job.get('demoProfileId') or 'manual'}")
    lines.append(f"  Target language: {job.get('targetLanguage', '')}")
    lines.append(f"  Handoff ready: {str(bool(job.get('handoffReady'))).lower()}")
    quality = job.get("qualityScore")
    lines.append(f"  Quality score: {quality if quality is not None else 'not available'}")
    lines.append(f"  Estimated cost USD: {job.get('estimatedCostUsd', 0)}")
    lines.append(f"  Model calls: {job.get('modelCallCount', 0)}")
    lines.append(f"  Provider cache hits: {job.get('providerCacheHitCount', 0)}")
    for reason in job.get("attentionReasons", []):
        lines.append(f"  Attention: {reason}")
    for download in job.get("downloads", []):
        label = download.get("label")
        href = download.get("href")
        if label and href:
            lines.append(f"  {label}: {href}")

lines.extend(["", "## Recommended Downloads"])
for download in gallery.get("galleryDownloads", []):
    label = download.get("label")
    href = download.get("href")
    if label and href:
        lines.append(f"- {label}: {href}")

with open(output_path, "w", encoding="utf-8") as handle:
    handle.write("\n".join(lines) + "\n")
PY
}

print_private_demo_run_archive_summary_file() {
  local archive_path="$1"

  python3 - "$archive_path" <<'PY'
import json
import sys

archive = json.load(open(sys.argv[1], encoding="utf-8"))
print("privateDemoRunArchiveOverall=" + str(archive.get("overallStatus", "UNKNOWN")))
print("privateDemoRunArchiveRecommendedJobId=" + str(archive.get("recommendedJobId") or ""))
print("privateDemoRunArchiveCompletedJobCount=" + str(archive.get("galleryCompletedJobCount", 0)))
print("privateDemoRunArchiveHandoffReadyCount=" + str(archive.get("galleryHandoffReadyCount", 0)))
for candidate in archive.get("candidates", []):
    print(
        "privateDemoRunArchiveCandidate="
        + str(candidate.get("jobId", ""))
        + ":"
        + str(candidate.get("profileId") or "manual")
        + ":"
        + str(candidate.get("status", ""))
        + ":"
        + str(candidate.get("readiness", ""))
        + ":quality="
        + str(candidate.get("qualityScore") if candidate.get("qualityScore") is not None else "none")
        + ":cost="
        + str(candidate.get("estimatedCostUsd", 0))
        + ":modelCalls="
        + str(candidate.get("modelCallCount", 0))
        + ":providerCacheHits="
        + str(candidate.get("providerCacheHitCount", 0))
        + ":handoffReady="
        + ("true" if candidate.get("handoffReady") else "false")
    )
for link in archive.get("archiveLinks", []):
    print(
        "privateDemoRunArchiveLink="
        + str(link.get("label", ""))
        + ":"
        + str(link.get("href", ""))
    )
PY
}

write_private_demo_run_archive_report() {
  local archive_path="$1"
  local output_path="$2"

  mkdir -p "$(dirname "$output_path")"
  python3 - "$archive_path" "$output_path" <<'PY'
import json
import sys

archive = json.load(open(sys.argv[1], encoding="utf-8"))
output_path = sys.argv[2]
markdown = archive.get("archiveNotesMarkdown") or ""
if not markdown.strip():
    lines = [
        "# LinguaFrame Private Demo Run Archive",
        "",
        f"- Overall: {archive.get('overallStatus', 'UNKNOWN')}",
        f"- Generated at: {archive.get('generatedAt', 'unknown')}",
        f"- Recommended job: {archive.get('recommendedJobId') or 'none'}",
        f"- Completed jobs: {archive.get('galleryCompletedJobCount', 0)}",
        f"- Handoff-ready jobs: {archive.get('galleryHandoffReadyCount', 0)}",
        "",
        "## Archive Links",
    ]
    for link in archive.get("archiveLinks", []):
        label = link.get("label")
        href = link.get("href")
        if label and href:
            lines.append(f"- {label}: {href}")
    markdown = "\n".join(lines)

for forbidden in [
    "OPENAI_API_KEY",
    "private-demo-token",
    "/Users/",
    "provider payload",
    "raw transcript text",
    "raw subtitle text",
    "corrected subtitle text",
    "job-artifacts/",
]:
    markdown = markdown.replace(forbidden, "[redacted]")

with open(output_path, "w", encoding="utf-8") as handle:
    handle.write(markdown.rstrip() + "\n")
PY
}

fetch_source_media_metadata() {
  local base_url="$1"
  local video_id="$2"

  demo_curl -fsS "$base_url/api/media/uploads/$video_id"
}

download_source_media() {
  local base_url="$1"
  local video_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/media/uploads/$video_id/source/download" -o "$output_path"
}

print_source_media_summary() {
  local metadata_path="$1"
  local job_id="$2"

  python3 - "$metadata_path" "$job_id" <<'PY'
import json
import sys

metadata_path = sys.argv[1]
job_id = sys.argv[2]
text = open(metadata_path, encoding="utf-8").read()
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "OPENAI_API_KEY",
    "sk-",
    "provider payload",
]
metadata = json.loads(text)
safe = {
    "videoId": metadata.get("videoId", ""),
    "filename": metadata.get("filename", ""),
    "contentType": metadata.get("contentType", ""),
    "fileSizeBytes": metadata.get("fileSizeBytes", 0),
    "durationSeconds": metadata.get("durationSeconds"),
    "status": metadata.get("status", ""),
    "createdAt": metadata.get("createdAt", ""),
}
summary = "\n".join([
    "sourceMediaJobId=" + job_id,
    "sourceMediaVideoId=" + str(safe["videoId"]),
    "sourceMediaFilename=" + str(safe["filename"]),
    "sourceMediaContentType=" + str(safe["contentType"]),
    "sourceMediaSizeBytes=" + str(safe["fileSizeBytes"]),
    "sourceMediaDurationSeconds=" + ("" if safe["durationSeconds"] is None else str(safe["durationSeconds"])),
    "sourceMediaStatus=" + str(safe["status"]),
    "sourceMediaCreatedAt=" + str(safe["createdAt"]),
    "sourceMediaDownloadRoute=/api/media/uploads/" + str(safe["videoId"]) + "/source/download",
])
for value in forbidden:
    if value in summary:
        raise SystemExit("Source media summary contains forbidden sensitive string: " + value)
print(summary)
PY
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

download_quality_evaluation_evidence_markdown() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/quality-evaluation/evidence/markdown/download" -o "$output_path"
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

print_quality_evidence_markdown_summary() {
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
    "# LinguaFrame Quality Evaluation Evidence",
    "- Job: " + expected_job_id,
    "## Evaluation",
    "## Related Safe Routes",
]
text = open(evidence_path, encoding="utf-8").read()
for value in forbidden:
    if value in text:
        raise SystemExit("Quality evidence report contains forbidden sensitive string: " + value)
for value in required:
    if value not in text:
        raise SystemExit("Quality evidence report is missing required marker: " + value)
print("qualityEvidenceMarkdownJobId=" + expected_job_id)
print("qualityEvidenceMarkdownBytes=" + str(len(text.encode("utf-8"))))
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

print_handoff_package_summary() {
  local package_path="$1"
  local expected_job_id="$2"

  python3 - "$package_path" "$expected_job_id" <<'PY'
import json
import sys
import zipfile

package_path = sys.argv[1]
expected_job_id = sys.argv[2]
required_entries = {"manifest.json", "delivery-manifest.md", "evidence.md", "diagnostics.json"}
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "sk-",
    "OPENAI_API_KEY",
    "raw transcript text",
    "raw subtitle text",
    "raw generated subtitle",
    "raw corrected subtitle",
    "provider payload",
    "provider request payload",
]

with zipfile.ZipFile(package_path) as archive:
    names = archive.namelist()
    missing = sorted(required_entries - set(names))
    if missing:
        raise SystemExit("Handoff package is missing entries: " + ", ".join(missing))

    reviewed_entries = [name for name in names if name.startswith("reviewed/")]
    if not reviewed_entries:
        raise SystemExit("Handoff package has no reviewed artifact entries")

    combined = ""
    for name in names:
        data = archive.read(name)
        try:
            combined += data.decode("utf-8") + "\n"
        except UnicodeDecodeError:
            continue

    for value in forbidden:
        if value in combined:
            raise SystemExit("Handoff package contains forbidden sensitive string: " + value)

    manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    if manifest.get("jobId") != expected_job_id:
        raise SystemExit("Handoff package manifest job id mismatch: " + str(manifest.get("jobId")))

print("handoffPackageJobId=" + expected_job_id)
print("handoffPackageEntryCount=" + str(len(names)))
print("handoffPackageReviewedArtifactCount=" + str(len(reviewed_entries)))
PY
}

print_demo_run_package_summary() {
  local package_path="$1"
  local expected_job_id="$2"

  python3 - "$package_path" "$expected_job_id" <<'PY'
import json
import sys
import zipfile

package_path = sys.argv[1]
expected_job_id = sys.argv[2]
required_entries = {
    "manifest.json",
    "README.md",
    "job-detail.json",
    "diagnostics.json",
    "evidence.md",
    "quality-evidence.md",
    "delivery-manifest.md",
    "demo-handoff-checklist.md",
    "demo-session-report.md",
}
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "sk-",
    "OPENAI_API_KEY",
    "raw transcript text",
    "raw subtitle text",
    "raw generated subtitle",
    "raw corrected subtitle",
    "provider payload",
    "provider request payload",
]

with zipfile.ZipFile(package_path) as archive:
    names = set(archive.namelist())
    missing = sorted(required_entries - names)
    if missing:
        raise SystemExit("Demo run package is missing entries: " + ", ".join(missing))

    combined = ""
    for name in sorted(required_entries):
        combined += archive.read(name).decode("utf-8") + "\n"

    for value in forbidden:
        if value in combined:
            raise SystemExit("Demo run package contains forbidden sensitive string: " + value)

    manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    if manifest.get("jobId") != expected_job_id:
        raise SystemExit("Demo run package manifest job id mismatch: " + str(manifest.get("jobId")))

print("demoRunPackageJobId=" + expected_job_id)
print("demoRunPackageEntryCount=" + str(len(required_entries)))
PY
}

print_demo_run_snapshot_package_summary() {
  local package_path="$1"
  local expected_job_id="$2"

  python3 - "$package_path" "$expected_job_id" <<'PY'
import json
import sys
import zipfile

package_path = sys.argv[1]
expected_job_id = sys.argv[2]
required_entries = {
    "index.html",
    "manifest.json",
    "README.md",
    "demo-share-sheet.md",
    "demo-share-sheet.json",
    "demo-run-monitor.md",
    "demo-run-monitor.json",
    "presenter-pack.json",
    "delivery-manifest.md",
    "diagnostics.json",
    "evidence.md",
}
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "sk-",
    "OPENAI_API_KEY",
    "raw transcript text",
    "raw subtitle text",
    "raw generated subtitle",
    "raw corrected subtitle",
    "provider payload",
    "provider request payload",
]

with zipfile.ZipFile(package_path) as archive:
    names = set(archive.namelist())
    missing = sorted(required_entries - names)
    if missing:
        raise SystemExit("Demo run snapshot package is missing entries: " + ", ".join(missing))

    combined = ""
    for name in sorted(required_entries):
        data = archive.read(name)
        try:
            combined += data.decode("utf-8") + "\n"
        except UnicodeDecodeError:
            continue

    for value in forbidden:
        if value in combined:
            raise SystemExit("Demo run snapshot package contains forbidden sensitive string: " + value)

    manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    if manifest.get("jobId") != expected_job_id:
        raise SystemExit("Demo run snapshot package manifest job id mismatch: " + str(manifest.get("jobId")))

print("demoRunSnapshotPackageJobId=" + expected_job_id)
print("demoRunSnapshotPackageEntryCount=" + str(len(required_entries)))
PY
}

print_ai_audit_package_summary() {
  local package_path="$1"
  local expected_job_id="$2"

  python3 - "$package_path" "$expected_job_id" <<'PY'
import json
import sys
import zipfile

package_path = sys.argv[1]
expected_job_id = sys.argv[2]
required_entries = {
    "manifest.json",
    "README.md",
    "model-calls.json",
    "prompt-templates.json",
    "ai-usage-summary.json",
    "ai-audit-report.md",
}
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "sk-",
    "OPENAI_API_KEY",
    "raw transcript text",
    "raw subtitle text",
    "raw generated subtitle",
    "raw corrected subtitle",
    "provider payload",
    "provider request payload",
]

with zipfile.ZipFile(package_path) as archive:
    names = set(archive.namelist())
    missing = sorted(required_entries - names)
    if missing:
        raise SystemExit("AI audit package is missing entries: " + ", ".join(missing))

    combined = ""
    for name in sorted(required_entries):
        combined += archive.read(name).decode("utf-8") + "\n"

    for value in forbidden:
        if value in combined:
            raise SystemExit("AI audit package contains forbidden sensitive string: " + value)

    manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    if manifest.get("jobId") != expected_job_id:
        raise SystemExit("AI audit package manifest job id mismatch: " + str(manifest.get("jobId")))

    model_calls = json.loads(archive.read("model-calls.json").decode("utf-8"))
    prompt_templates = json.loads(archive.read("prompt-templates.json").decode("utf-8"))

print("aiAuditPackageJobId=" + expected_job_id)
print("aiAuditPackageEntryCount=" + str(len(required_entries)))
print("aiAuditPackageModelCallCount=" + str(len(model_calls)))
print("aiAuditPackagePromptTemplateCount=" + str(len(prompt_templates)))
PY
}

print_reviewed_subtitle_workflow_summary_file() {
  local workflow_json_path="$1"

  python3 - "$workflow_json_path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    workflow = json.load(handle)

combined = json.dumps(workflow, ensure_ascii=False)
forbidden = [
    "/Users/",
    "source-videos/",
    "job-artifacts/",
    "objectKey",
    "demo-access-token",
    "private-demo-token",
    "sk-",
    "OPENAI_API_KEY",
    "raw transcript text",
    "raw subtitle text",
    "raw generated subtitle",
    "raw corrected subtitle",
    "provider payload",
    "provider request payload",
]
for value in forbidden:
    if value in combined:
        raise SystemExit("Reviewed subtitle workflow contains forbidden sensitive string: " + value)

print("reviewedSubtitleWorkflowJobId=" + str(workflow.get("jobId", "")))
print("reviewedSubtitleWorkflowStatus=" + str(workflow.get("overallStatus", "")))
print("reviewedSubtitleWorkflowPhase=" + str(workflow.get("phase", "")))
print("reviewedSubtitleWorkflowNextAction=" + str(workflow.get("recommendedNextAction", "")))
print("reviewedSubtitleWorkflowGeneratedSubtitleArtifactCount=" + str(workflow.get("generatedSubtitleArtifactCount", 0)))
print("reviewedSubtitleWorkflowReviewedSubtitleArtifactCount=" + str(workflow.get("reviewedSubtitleArtifactCount", 0)))
print("reviewedSubtitleWorkflowHandoffReady=" + str(bool(workflow.get("handoffReady", False))).lower())
PY
}
