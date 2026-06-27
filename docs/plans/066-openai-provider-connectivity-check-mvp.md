# OpenAI Provider Connectivity Check MVP Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit, safe OpenAI connectivity check so a demo operator can verify configured OpenAI provider access before uploading media or spending time on a full job.

**Architecture:** Extend the existing runtime live-check surface instead of adding a separate admin subsystem. The backend will add a disabled-by-default provider probe controlled by configuration, call a low-cost metadata endpoint such as `/v1/models/{model}` only when enabled, and return safe probe statuses. The React demo and private-demo preflight will display and validate the new check without exposing API keys, raw provider responses, provider request payloads, or local paths.

**Tech Stack:** Java 21, Spring Boot, `RestClient`, JUnit 5, React, TypeScript, Vitest, Bash, Docker Compose.

## Global Constraints

- Keep this as one complete feature slice: backend configuration, live-check service, frontend display, preflight integration, tests, docs, and merge record.
- Default behavior must not call OpenAI; the new check must be enabled explicitly.
- The check must be safe for private demos: no API keys, bearer tokens, raw provider payloads, local paths, or OpenAI response bodies in API output, logs, scripts, or docs.
- The check must not upload media, start jobs, or call transcription/translation/TTS/evaluation endpoints.
- Follow existing runtime readiness/live-check patterns.

---

## Task 1: Backend OpenAI Connectivity Probe

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/OpenAiConnectivityCheckService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/OpenAiConnectivityCheckServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeLiveCheckServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeLiveCheckServiceTests.java`

**Steps:**

- [x] Add `linguaframe.openai-connectivity.enabled`, `timeoutSeconds`, and `model` properties with defaults `false`, `5`, and blank.
- [x] Define `OpenAiConnectivityCheckService#check()` returning `RuntimeProbeResultVo`.
- [x] Implement the service so disabled config returns `SKIPPED` with a safe message.
- [x] When enabled, resolve the model from `linguaframe.openai-connectivity.model`, falling back to configured OpenAI provider models in order: transcription, translation, evaluation, TTS.
- [x] If enabled but API key, base URL, or model is missing, return `DOWN` with a safe configuration message.
- [x] If enabled and configured, call `GET {baseUrl}/v1/models/{model}` with bearer auth and timeout, returning `UP` for 2xx and `DOWN` for non-2xx or exceptions.
- [x] Add the result under `openai` in `GET /api/runtime/live-checks`.
- [x] Add tests for disabled, missing credentials/model, successful mock response, failed mock response, and secret redaction.

## Task 2: Runtime Contract, Preflight, And OpenAPI Coverage

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Steps:**

- [x] Keep `/api/runtime/live-checks` as the single runtime probe route; no new route is needed.
- [x] Update controller tests to expect the `openai` probe in live-check responses.
- [x] Update preflight parsing to accept `SKIPPED` for `openai` when the explicit connectivity check is disabled.
- [x] Make preflight fail when `openai` is `DOWN`, printing only the safe probe message.
- [x] Keep OpenAPI path expectations stable and ensure the runtime endpoint remains documented.

## Task 3: React Demo Visibility

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Steps:**

- [x] Extend `RuntimeLiveCheckName` to include `openai`.
- [x] Render the OpenAI probe in the existing `Live checks` panel with the same status styling as other probes.
- [x] Ensure `SKIPPED` renders as warning/neutral, not success or danger.
- [x] Add tests proving the panel shows `OpenAI`, `SKIPPED`, `UP`, and safe messages without exposing API keys or bearer tokens.

## Task 4: Configuration And Documentation

**Files:**
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/066-openai-provider-connectivity-check-mvp.md`

**Steps:**

- [x] Document that OpenAI connectivity checks are disabled by default.
- [x] Add `.env.example` variables for enabling the probe and choosing a probe model without adding any real secret.
- [x] Document the safe verification flow: configure `.env`, restart backend, open `Live checks`, or run `scripts/demo/private-demo-preflight.sh`.
- [x] Update roadmap and decisions to record the probe as a private-demo readiness feature.
- [x] Update this plan and execution log with validation commands and results.

## Verification

- [x] `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test`
- [x] `cd frontend && npm run test:run -- App`
- [x] `cd frontend && npm run build`
- [x] `bash -n scripts/demo/private-demo-preflight.sh`
- [x] `docker compose --env-file .env.example config --quiet`
- [x] `git diff --check`
- [x] `mvn -pl LinguaFrame test`

## Done Criteria

- [x] Default local startup and preflight do not call OpenAI.
- [x] When explicitly enabled, the backend can verify configured OpenAI access through a bounded metadata request.
- [x] Browser live checks and preflight show whether OpenAI connectivity is `UP`, `DOWN`, or `SKIPPED`.
- [x] No API output, logs, scripts, or docs expose API keys, bearer tokens, provider payloads, raw provider responses, or local paths.
- [ ] The feature branch is committed, verified, and merged back to `main`.
