#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

ENV_FILE="${LINGUAFRAME_ENV_FILE:-.env.private-demo}"
LOCAL_COMPOSE_FILE="docker-compose.yml"
PRIVATE_COMPOSE_FILE="deploy/private-demo/docker-compose.private-demo.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

pass() {
  echo "PASS: $*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    fail "Missing required command: $1"
  fi
}

env_value() {
  local key="$1"
  python3 - "$ENV_FILE" "$key" <<'PY'
import pathlib
import sys

env_path = pathlib.Path(sys.argv[1])
key = sys.argv[2]
for raw_line in env_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    name, value = line.split("=", 1)
    if name.strip() == key:
        value = value.strip().strip('"').strip("'")
        print(value)
        break
PY
}

require_env_value() {
  local key="$1"
  local value
  value="$(env_value "$key")"
  if [[ -z "$value" ]]; then
    fail "$key must be set in $ENV_FILE"
  fi
}

render_compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$LOCAL_COMPOSE_FILE" \
    -f "$PRIVATE_COMPOSE_FILE" \
    config \
    --format json
}

check_files() {
  [[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE. Create it with: cp .env.private-demo.example $ENV_FILE"
  [[ -f "$LOCAL_COMPOSE_FILE" ]] || fail "Missing $LOCAL_COMPOSE_FILE"
  [[ -f "$PRIVATE_COMPOSE_FILE" ]] || fail "Missing $PRIVATE_COMPOSE_FILE"
  [[ -f "deploy/private-demo/Caddyfile" ]] || fail "Missing deploy/private-demo/Caddyfile"
  pass "Deployment files are present"
}

check_required_env() {
  require_env_value "LINGUAFRAME_PUBLIC_DOMAIN"
  require_env_value "LINGUAFRAME_DEMO_ACCESS_TOKEN"
  pass "Required private-demo env values are present"
}

check_compose_shape() {
  local config_file
  config_file="$(mktemp)"
  if ! render_compose > "$config_file"; then
    rm -f "$config_file"
    fail "Private-demo Compose rendering failed"
  fi

  python3 - "$config_file" <<'PY'
import pathlib
import sys
import json

config_path = pathlib.Path(sys.argv[1])
body = json.loads(config_path.read_text(encoding="utf-8"))
services = body.get("services") or {}
required = {"linguaframe-proxy", "linguaframe-backend", "linguaframe-frontend", "mysql", "redis", "rabbitmq", "minio"}
missing = sorted(required - set(services))
if missing:
    raise SystemExit("Missing services: " + ", ".join(missing))

proxy = services["linguaframe-proxy"]
ports = proxy.get("ports") or []
published = {str(item.get("published")) for item in ports if isinstance(item, dict)}
if not {"80", "443"}.issubset(published):
    raise SystemExit("Proxy must publish HTTP 80 and HTTPS 443 by default")

for service_name in ("linguaframe-backend", "linguaframe-frontend"):
    ports = services[service_name].get("ports") or []
    if ports:
        raise SystemExit(service_name + " must not publish host ports in private-demo overlay")

volumes = set((body.get("volumes") or {}).keys())
for name in ("caddy-data", "caddy-config"):
    if name not in volumes:
        raise SystemExit("Missing Caddy volume: " + name)

print("composeServices=" + ",".join(sorted(required)))
print("proxyPublishes=80,443")
print("backendFrontendHostPorts=none")
PY
  rm -f "$config_file"
  pass "Private-demo Compose shape is valid"
}

check_proxy_routes() {
  python3 - "deploy/private-demo/Caddyfile" <<'PY'
import pathlib
import sys

text = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
required = [
    "{$LINGUAFRAME_PUBLIC_DOMAIN}",
    "path /api/* /actuator/* /swagger-ui/* /v3/api-docs* /swagger-ui.html",
    "reverse_proxy @backend linguaframe-backend:8080",
    "reverse_proxy linguaframe-frontend:5173",
]
missing = [item for item in required if item not in text]
if missing:
    raise SystemExit("Caddyfile missing required routes: " + ", ".join(missing))
PY
  pass "Caddy proxy routes are present"
}

print_next_steps() {
  cat <<'TXT'
Next commands after this preflight passes:
  JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
  docker compose --env-file .env.private-demo -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml up -d --build
  LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-preflight.sh

The deployment preflight does not print secrets and does not upload media or call provider APIs.
TXT
}

echo "LinguaFrame private demo deployment preflight"
echo "Env file: $ENV_FILE"
require_command docker
require_command python3
check_files
check_required_env
check_compose_shape
check_proxy_routes
print_next_steps
pass "Private demo deployment preflight passed"
