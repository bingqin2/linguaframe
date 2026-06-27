# OpenAI Demo Profile Smoke Runner MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make a real OpenAI-backed LinguaFrame demo repeatable, bounded, and easy to verify from local Docker without leaking secrets.

**Architecture:** Keep existing provider implementations unchanged. Add a safe OpenAI demo env template, script-level demo-token header support, and a dedicated smoke runner that verifies connectivity, provider configuration, job completion, model-call evidence, quality/evidence downloads, and artifact outputs. The default `.env.example` remains deterministic and cost-free.

**Tech Stack:** Bash, Docker Compose, Spring Boot configuration surface, existing demo script library, React/runtime readiness docs, Maven tests, Vitest.

## Global Constraints

- Do not commit real OpenAI keys or user `.env`.
- Do not change provider request payloads, model parsing, transcription/translation/TTS/evaluation core behavior, or billing semantics in this slice.
- Keep default `.env.example` on deterministic demo providers and no paid OpenAI calls.
- All OpenAI demo behavior must be opt-in through `.env.openai-demo.example` copied to a local ignored env file.
- Scripts must support `LINGUAFRAME_DEMO_ACCESS_TOKEN` and `LINGUAFRAME_DEMO_ACCESS_HEADER_NAME` so private demo gates do not break terminal E2E flows.
- Smoke output must prove provider-backed execution through safe job detail, model-call summaries, artifacts, and evidence files without printing secrets or raw provider payloads.

---

## Task 1: Script Client Demo-Token Compatibility

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Test: `scripts/demo/lib/linguaframe-demo.bats` if the repo already has Bats; otherwise add shell syntax and focused dry-run validation in docs.
- Modify: `docs/progress/execution-log.md`

**Steps:**

- [x] Add helper functions that read `LINGUAFRAME_DEMO_ACCESS_TOKEN` and `LINGUAFRAME_DEMO_ACCESS_HEADER_NAME`, defaulting the header to `X-LinguaFrame-Demo-Token`.
- [x] Add a `demo_curl` wrapper that calls `curl` with the configured header only when a token is present.
- [x] Replace every `/api/**` curl in `scripts/demo/lib/linguaframe-demo.sh` with `demo_curl`, including upload, job detail, artifacts, transcript, subtitles, diagnostics, evidence, and downloads.
- [x] Keep `/actuator/health` unauthenticated.
- [x] Verify private-demo script compatibility with `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-cache-hit.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`.

## Task 2: OpenAI Demo Env Profile

**Files:**
- Create: `.env.openai-demo.example`
- Modify: `.gitignore` if needed to ensure `.env.openai-demo` remains ignored.
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/decisions.md`

**Steps:**

- [x] Add `.env.openai-demo.example` as a copyable template with no secrets.
- [x] Keep infrastructure values aligned with `.env.example`.
- [x] Enable OpenAI connectivity check by default in this template.
- [x] Configure a bounded OpenAI demo profile:
  - transcription enabled with provider `openai`
  - translation enabled with provider `openai`
  - quality evaluation enabled with provider `openai`
  - TTS disabled by default to control cost and runtime
  - FFmpeg audio and burn-in enabled
  - cost tracking enabled with non-secret safe budget identity
  - optional per-job/daily budget guard values documented but conservative
- [x] Leave model fields explicit placeholders such as `OPENAI_TRANSCRIPTION_MODEL=whisper-1` and `OPENAI_TRANSLATION_MODEL=<set-current-model>`, because model availability changes.
- [x] Document how to copy it locally:

```bash
cp .env.openai-demo.example .env.openai-demo
```

- [x] Document that `.env.openai-demo` is ignored and must hold the real key locally only.

## Task 3: OpenAI Demo Preflight

**Files:**
- Create: `scripts/demo/openai-demo-preflight.sh`
- Modify: `scripts/demo/private-demo-preflight.sh` only if reusable helper extraction is necessary.
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`

**Steps:**

- [x] Add a preflight script that defaults `LINGUAFRAME_ENV_FILE=.env.openai-demo`.
- [x] Fail fast when the env file is missing, `OPENAI_API_KEY` is empty, or required OpenAI model placeholders are still unset.
- [x] Run the existing private-demo preflight with the selected env file.
- [x] Fetch `GET /api/runtime/dependencies` and assert safe readiness shows OpenAI providers for the enabled stages.
- [x] Fetch `GET /api/runtime/live-checks` and assert `checks.openai.status` is `UP`.
- [x] Print only safe configuration facts: enabled stages, provider names, model names, frontend/backend URLs, and sample path.
- [x] Do not print `OPENAI_API_KEY`, authorization headers, raw provider responses, media paths beyond user-provided sample path, or object storage keys.
- [x] Validate with `bash -n scripts/demo/openai-demo-preflight.sh`.

## Task 4: OpenAI Smoke Runner

**Files:**
- Create: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh` if small reusable summary helpers are needed.
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/execution-log.md`

**Steps:**

- [x] Add a smoke runner that defaults to:

```bash
LINGUAFRAME_ENV_FILE=${LINGUAFRAME_ENV_FILE:-.env.openai-demo}
LINGUAFRAME_DEMO_SAMPLE_PATH=${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/openai-smoke.mp4}
LINGUAFRAME_DEMO_OUTPUT_DIR=${LINGUAFRAME_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo/openai-smoke}
```

- [x] Require a real readable MP4 sample path; do not auto-generate the tone-only sample for OpenAI transcription.
- [x] Run `scripts/demo/openai-demo-preflight.sh` before upload.
- [x] Upload the sample, wait for `COMPLETED`, and print job summary.
- [x] Assert model calls include `OPENAI` provider for transcription and translation.
- [x] If evaluation is enabled, assert a quality evaluation exists and print score/verdict/status.
- [x] If TTS is enabled, download `DUBBING_AUDIO`; otherwise print that TTS is disabled by profile.
- [x] Always download core artifacts, diagnostics, backend evidence Markdown, evidence bundle, and result bundle into the output directory.
- [x] Validate evidence files do not contain keys, bearer headers, object keys, local raw paths, raw provider payloads, or raw transcript/subtitle bodies.
- [x] Print next browser steps: open frontend, start owner session if gated, open job id, inspect Model calls, Quality evaluation, Result delivery, and Demo evidence.

## Task 5: Tests And Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/071-openai-demo-profile-smoke-runner-mvp.md`

**Steps:**

- [x] Document the OpenAI demo path as the recommended proof when real API credentials are available.
- [x] Explain that `scripts/demo/docker-e2e-success.sh` remains the deterministic no-cost path.
- [x] Explain that `scripts/demo/docker-e2e-openai-smoke.sh` can consume credits and should use a short speech sample.
- [x] Add smoke checklist items for OpenAI connectivity `UP`, provider readiness, model-call provider `OPENAI`, quality evaluation when enabled, artifacts, and evidence bundle.
- [x] Record the decision that the project uses an explicit OpenAI demo profile instead of turning `.env.example` into a paid default.
- [x] Update the plan checkboxes as tasks complete.

## Validation

- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/openai-demo-preflight.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-cache-hit.sh scripts/demo/private-demo-preflight.sh`.
- [x] Run `docker compose --env-file .env.example config --quiet`.
- [x] Run `docker compose --env-file .env.openai-demo.example config --quiet`.
- [x] Run `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests,RuntimeLiveCheckServiceTests,DemoSessionControllerTests test` if those test classes exist; otherwise run the closest runtime/security focused test set.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `git diff --check`.
- [x] Optional live validation, only with user-provided local credentials and sample media:

```bash
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-demo-preflight.sh
LINGUAFRAME_ENV_FILE=.env.openai-demo \
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 \
scripts/demo/docker-e2e-openai-smoke.sh
```

## Done Criteria

- [x] The deterministic local demo remains unchanged and cost-free by default.
- [x] A separate OpenAI demo env template exists and contains no secrets.
- [x] Terminal E2E scripts work with the private demo token header when configured.
- [x] OpenAI preflight proves credentials/model access before upload.
- [x] OpenAI smoke runner produces completed-job evidence from real provider-backed stages.
- [x] Documentation tells the user exactly how to run, what costs may happen, and what artifacts/evidence to inspect.
- [x] The feature branch is committed, verified, and merged back to `main`.
