#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
FRONTEND_PORT="${LINGUAFRAME_FRONTEND_PORT:-5173}"
FRONTEND_URL="${LINGUAFRAME_FRONTEND_URL:-http://localhost:${FRONTEND_PORT}}"

usage() {
  cat <<'EOF'
Usage: scripts/demo/frontend-local-dev.sh

Starts the LinguaFrame React demo with local Node/Vite when Docker cannot build
the frontend image.

Environment overrides:
  LINGUAFRAME_FRONTEND_PORT   Default: 5173
  LINGUAFRAME_FRONTEND_URL    Default: http://localhost:<port>
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

fail() {
  echo "[fail] $1" >&2
  exit 1
}

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    fail "Missing required command: $name"
  fi
}

port_is_listening() {
  python3 - "$FRONTEND_PORT" <<'PY'
import socket
import sys

port = int(sys.argv[1])
with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
    sock.settimeout(0.3)
    raise SystemExit(0 if sock.connect_ex(("127.0.0.1", port)) == 0 else 1)
PY
}

require_command node
require_command npm
require_command python3
require_command curl

if [[ ! -f "$FRONTEND_DIR/package.json" ]]; then
  fail "Missing frontend/package.json. Run this script from the LinguaFrame repository."
fi

if curl -fsSI "$FRONTEND_URL" >/dev/null 2>&1; then
  echo "Frontend already responds at $FRONTEND_URL"
  exit 0
fi

if port_is_listening; then
  fail "Port $FRONTEND_PORT is already in use, but $FRONTEND_URL did not return HTTP. Free the port or set LINGUAFRAME_FRONTEND_PORT."
fi

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Installing frontend dependencies with npm ci..."
  (cd "$FRONTEND_DIR" && npm ci)
fi

echo "Starting LinguaFrame frontend at $FRONTEND_URL"
echo "Press Ctrl+C to stop it."
cd "$FRONTEND_DIR"
exec npm run dev -- --port "$FRONTEND_PORT"
