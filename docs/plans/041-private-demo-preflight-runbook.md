# Private Demo Preflight Runbook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a repeatable private-demo preflight workflow that proves the local Docker demo is configured, reachable, and ready before running a sample video.

**Architecture:** Keep this slice operational and repository-local: add a shell preflight script under `scripts/demo/` that checks required tools, `.env` presence, Compose config rendering, backend/frontend health, optional demo token behavior, and sample-video availability without uploading media. Update demo docs so the user can run preflight first, then run the existing short or Tears of Steel demo scripts.

**Tech Stack:** POSIX-compatible Bash, Docker Compose CLI, curl, existing demo shell helpers, Markdown docs, shell syntax validation.

## Global Constraints

- This slice must be a complete feature: executable preflight script, deterministic checks, docs, progress logs, validation, commit, and merge back to `main`.
- Do not commit `.env`, OpenAI keys, generated artifacts, downloaded videos, or local media files.
- Do not require paid OpenAI calls or media upload in preflight.
- Keep the script safe to run repeatedly against an already running Docker stack.
- Branch name: `private-demo-preflight-runbook`.

---

### Task 1: Preflight Script

**Files:**
- Create: `scripts/demo/private-demo-preflight.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh` only if a reusable helper is clearly needed.

**Interfaces:**
- Consumes: `LINGUAFRAME_BASE_URL`, `LINGUAFRAME_FRONTEND_URL`, `LINGUAFRAME_DEMO_ACCESS_TOKEN`, `LINGUAFRAME_DEMO_ACCESS_HEADER_NAME`, `LINGUAFRAME_DEMO_SAMPLE_PATH`, `LINGUAFRAME_TEARS_SAMPLE_PATH`.
- Produces: exit code `0` when preflight passes; non-zero with actionable messages when required checks fail.

- [x] Create `scripts/demo/private-demo-preflight.sh` with `set -euo pipefail`, `--help`, and concise status output.
- [x] Check required commands: `docker`, `curl`, and `mvn`; warn but do not fail if `ffmpeg` is missing because existing demos can use an existing MP4.
- [x] Check `.env` exists or print the exact bootstrap command `cp .env.example .env`.
- [x] Run `docker compose --env-file .env config --quiet` and `docker compose --env-file .env --profile split-workers config --quiet`.
- [x] Check backend health at `${LINGUAFRAME_BASE_URL:-http://localhost:8080}/actuator/health`.
- [x] Check frontend entry at `${LINGUAFRAME_FRONTEND_URL:-http://localhost:5173}`.
- [x] If `LINGUAFRAME_DEMO_ACCESS_TOKEN` is set, check one protected endpoint without a token returns `401` and with the configured header returns non-`401`.
- [x] If `LINGUAFRAME_DEMO_SAMPLE_PATH` or `LINGUAFRAME_TEARS_SAMPLE_PATH` is set, verify the file exists and is readable; otherwise print the documented default demo choices.
- [x] Validate with `bash -n scripts/demo/private-demo-preflight.sh` and `scripts/demo/private-demo-preflight.sh --help`.

### Task 2: Documentation And Runbook Integration

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/041-private-demo-preflight-runbook.md`

**Interfaces:**
- Consumes: `scripts/demo/private-demo-preflight.sh` from Task 1.
- Produces: a documented operator workflow: configure `.env`, start Compose, run preflight, run short demo, optionally run full Tears of Steel demo.

- [x] Add a `Private Demo Preflight` section to `README.md` with the exact commands:

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
scripts/demo/private-demo-preflight.sh
scripts/demo/docker-e2e-success.sh
```

- [x] Update `docs/agent/docker-e2e-demo.md` to make preflight the first validation step before short or full demo scripts.
- [x] Update `docs/agent/smoke-test-checklist.md` with preflight pass criteria: Compose config renders, backend health is `UP`, frontend responds, optional demo token gate behaves correctly, sample path is readable when configured.
- [x] Mark Phase 9 private demo deployment as improved with a preflight/runbook path in `docs/product/roadmap.md` and `docs/product/spec.md`.
- [x] Record the decision that private demo readiness should be verified by local preflight before running paid/provider-backed media jobs.
- [x] Record implementation and validation results in `docs/progress/execution-log.md`.

### Task 3: Validation, Commit, And Merge

**Files:**
- Modify: `docs/plans/041-private-demo-preflight-runbook.md`

**Interfaces:**
- Consumes: Tasks 1 and 2.
- Produces: verified feature branch merged back to `main`.

- [x] Run `bash -n scripts/demo/private-demo-preflight.sh`.
- [x] Run `scripts/demo/private-demo-preflight.sh --help`.
- [x] Run `docker compose --env-file .env.example config --quiet`.
- [x] Run `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `rg -n "private-demo-preflight|Private Demo Preflight|preflight" README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add private demo preflight runbook`.
- [ ] Merge `private-demo-preflight-runbook` back to `main`.
- [ ] Run post-merge validation: `bash -n scripts/demo/private-demo-preflight.sh` and `scripts/demo/private-demo-preflight.sh --help`.
