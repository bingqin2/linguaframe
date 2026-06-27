# Demo Readiness Panel MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser-visible private-demo readiness panel that summarizes safe runtime configuration and pipeline prerequisites before uploading media.

**Architecture:** Extend the existing sanitized `GET /api/runtime/dependencies` endpoint instead of adding a second preflight API. The backend will return non-secret dependency metadata plus worker, media, FFmpeg, provider, cache, rate-limit, retention, and demo-gate readiness fields; the React demo will show those fields in a compact read-only panel.

**Tech Stack:** Spring Boot, Java 21, JUnit 5, TestRestTemplate, React, TypeScript, Vitest, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Do not perform live network probes, object writes, OpenAI calls, FFmpeg execution, or paid-provider checks in this slice.
- Do not expose passwords, access keys, OpenAI API keys, raw local media paths, raw tokens, or uploaded media data.
- Reuse `/api/runtime/dependencies`; keep it accessible under the existing demo access gate behavior.
- Plan and discussion can be Chinese, but repository docs and UI copy stay English.

---

## Task 1: Backend Readiness Summary Shape

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeDependencySummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/DemoReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/WorkerReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/MediaReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/FfmpegReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/ProviderReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeFeatureFlagVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Consumes: existing `LinguaFrameProperties`
- Produces: `RuntimeDependencySummaryVo.readiness`

- [x] Add failing controller assertions for `readiness.demoAccessGate`, `readiness.worker`, `readiness.media`, `readiness.ffmpeg`, `readiness.providers`, and `readiness.features`.
- [x] Assert the JSON still excludes password, secret, access key, API key, token, and raw media-path values.
- [x] Add VO records with only safe scalar fields: booleans, enums, provider names, model names, limits, and non-secret endpoint metadata.
- [x] Populate worker flags: dispatch enabled, execution enabled, worker role, max retries, dispatch interval, batch size.
- [x] Populate media and FFmpeg flags: max size, max duration, audio enabled, burn-in enabled, binary configured, timeout seconds, and work-dir configured without returning the work-dir path.
- [x] Populate provider flags for transcription, translation, TTS, and evaluation: enabled, provider, configured model, configured API key boolean.
- [x] Populate feature flags for Redis job cache, upload rate limit, retention cleanup, cost tracking, and budget guard.
- [x] Run: `mvn -pl LinguaFrame -Dtest='RuntimeDependencyControllerTests,LinguaFramePropertiesTests,DemoAccessInterceptorTests' test`.

## Task 2: Frontend API And Readiness Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /api/runtime/dependencies`
- Produces: a sidebar panel labeled `Demo readiness`

- [x] Add TypeScript interfaces for `RuntimeDependencySummary` and nested readiness types.
- [x] Add `getRuntimeDependencies(): Promise<RuntimeDependencySummary>` to `linguaframeApi`.
- [x] Add an API test verifying the demo token header is sent to `/api/runtime/dependencies`.
- [x] Add App state and startup loading for runtime dependencies, with a refresh button and local error message.
- [x] Render a `Demo readiness` panel above upload controls showing: demo gate, media duration limit, worker mode, FFmpeg audio/burn-in, provider modes, and key feature flags.
- [x] Keep the panel compact and read-only; do not block upload in the browser based on these fields.
- [x] Add App tests for populated readiness data and readiness API failure while upload controls remain usable.
- [x] Run: `cd frontend && npm run test:run -- linguaframeApi App`.

## Task 3: Documentation And Preflight Alignment

**Files:**
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: clear operator guidance for browser and script-based readiness checks

- [x] Update README to explain that `/api/runtime/dependencies` is the source for the browser readiness panel and intentionally does not run probes or expose secrets.
- [x] Document how the readiness panel complements `scripts/demo/private-demo-preflight.sh`: browser panel for configuration visibility, script for local reachability checks.
- [x] Update product docs to include browser-visible readiness as part of the private demo experience.
- [x] Record the decision to keep readiness read-only and configuration-derived in this slice.
- [x] Run: `docker compose --env-file .env.example config --quiet`.
- [x] Run: `git diff --check`.

## Task 4: Full Verification And Merge

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/045-demo-readiness-panel-mvp.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='RuntimeDependencyControllerTests,LinguaFramePropertiesTests,DemoAccessInterceptorTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add demo readiness panel`.
- [ ] Merge `demo-readiness-panel-mvp` back to `main`.
- [ ] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- linguaframeApi App`.
