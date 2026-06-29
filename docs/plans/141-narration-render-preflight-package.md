# Narration Render Preflight Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe preflight package for one-click narration demo render so operators can review preset choice, replacement impact, TTS provider cost risk, estimated narration size, video-generation readiness, and exact next commands before spending provider credits.

**Architecture:** Add a backend read-only aggregate endpoint that composes existing demo preset, narration workspace, script package, evidence, artifact, provider, and budget configuration state without mutating jobs or calling providers. Add a browser preflight panel next to `Render narration demo`, plus a terminal script and docs so the same decision can be checked before running the one-click render.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: backend preflight endpoint, frontend preflight panel, terminal script, full Tears documentation, safety validation, focused tests, full validation, commits, and merge back to `main`.
- Use branch title `narration-render-preflight-package`; do not include `/` in the user-facing branch title.
- The preflight endpoint is read-only: it must not apply presets, save narration rows, generate narration audio, generate narrated video, create artifacts, dispatch jobs, call OpenAI, or mutate object storage.
- The preflight must make replacement explicit and must warn when the current narration workspace already has segments.
- The preflight must distinguish `READY`, `ATTENTION`, and `BLOCKED`.
- The preflight must include a paid-provider warning when configured TTS provider is OpenAI or any non-demo provider.
- The preflight must not expose transcript text, subtitle text, narration script text, object keys, local paths, demo tokens, provider payloads, credentials, API keys, uploaded media bytes, or generated media bytes.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Narration Render Preflight Endpoint

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/NarrationDemoRenderPreflightRequestDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationDemoRenderPreflightCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationDemoRenderPreflightVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationDemoRenderPreflightService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationDemoRenderPreflightServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationDemoRenderPreflightServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-demo/render/preflight`
- Request: `NarrationDemoRenderPreflightRequestDto(String presetId, boolean replaceExisting, boolean generateNarratedVideo)`
- Response: `NarrationDemoRenderPreflightVo` with job id, preset id, overall status, checks, estimated segment count, estimated character count, provider mode, paid-provider flag, existing workspace segment count, generated-video requested flag, required confirmations, safe next command, and evidence routes.

- [x] Write failing service tests for ready demo-provider preflight, missing preset id, existing workspace without replace confirmation, paid OpenAI provider attention, missing source artifact for video generation, and generated-video disabled.
- [x] Implement DTO/VO records with exact status unions represented as strings: `READY`, `ATTENTION`, `BLOCKED`, and check statuses `PASS`, `WARN`, `BLOCK`.
- [x] Implement `NarrationDemoRenderPreflightServiceImpl` by composing `NarrationDemoPresetService`, `NarrationWorkspaceService`, `NarrationScriptPackageService`, `NarrationEvidenceService`, `JobArtifactService`, and `LinguaFrameProperties`.
- [x] Compute estimated character count from preset metadata only, not raw script text in the response.
- [x] Mark preflight `BLOCKED` when no preset exists, preset id is blank, replacement is false while workspace has segments, or generated-video is requested without an available base video path/artifact.
- [x] Mark preflight `ATTENTION` when TTS provider is not `demo`, when workspace replacement will discard existing segments, or when evidence is not already ready.
- [x] Add safe next commands such as `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render.sh` without local paths or secrets.
- [x] Add controller route with OpenAPI summary, 400/401/404 responses, and route-encoding MockMvc coverage.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderPreflightServiceTests,LocalizationJobControllerTests,NarrationDemoPresetServiceTests,NarrationScriptPackageServiceTests`.
- [x] Update `docs/progress/execution-log.md`.
- [x] Commit with message `Add narration render preflight endpoint`.

## Task 2: Frontend Preflight Decision Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `preflightNarrationDemoRender(jobId: string, request: NarrationDemoRenderPreflightRequest): Promise<NarrationDemoRenderPreflight>`

- [x] Add TypeScript types for preflight request, check rows, and result status unions.
- [x] Add API helper and route-encoding test for `/api/jobs/{jobId}/narration-demo/render/preflight`.
- [x] Add a `Render preflight` subsection inside the existing `Render narration demo` panel.
- [x] Let the same preset selection, replace confirmation, provider-cost acknowledgement, and `Generate narrated video` checkbox drive both preflight and render.
- [x] Keep render disabled until the latest preflight is `READY` or `ATTENTION` and both required confirmations are checked.
- [x] Display overall status, provider mode, paid-provider warning, estimated segments/characters, existing workspace segment count, generated-video plan, safe next command, and check rows.
- [x] Make `BLOCKED` checks visually distinct without hiding the existing separate narration controls.
- [x] Refresh preflight after successful one-click render so the panel reflects newly ready artifacts.
- [x] Add Vitest coverage for blocked replacement, paid-provider attention, audio-only preflight, successful render after preflight, and route refresh calls.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.
- [x] Update `docs/progress/execution-log.md`.
- [x] Commit with message `Add narration render preflight UI`.

## Task 3: Terminal Preflight Script And Full Tears Guidance

**Files:**
- Create: `scripts/demo/narration-demo-render-preflight.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/narration-demo-render.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render-preflight.sh`
- `LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED=true scripts/demo/narration-demo-render.sh`

- [x] Add shared Bash helper `preflight_narration_demo_render_json "$base_url" "$job_id" "$preset_id" "$replace_existing" "$generate_video" "$output_path"`.
- [x] Add `print_narration_demo_render_preflight_summary_file` that prints status, provider mode, paid-provider flag, estimated counts, existing segment count, check rows, safe command, and output path.
- [x] Create `scripts/demo/narration-demo-render-preflight.sh` with env vars `LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_OUTPUT_DIR`, `LINGUAFRAME_NARRATION_DEMO_PRESET_ID`, `LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO`, and `LINGUAFRAME_NARRATION_DEMO_RENDER_REPLACE_EXISTING`.
- [x] Exit non-zero on `BLOCKED` unless `LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REPORT_ONLY=true`.
- [x] Extend `scripts/demo/narration-demo-render.sh` so `LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED=true` runs preflight first and refuses blocked render.
- [x] Extend full Tears usage text to recommend preflight before `LINGUAFRAME_RENDER_NARRATION_DEMO=true`.
- [x] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration render preflight script`.

## Task 4: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/141-narration-render-preflight-package.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document browser run order: open completed job, run render preflight, inspect checks, acknowledge replacement/provider cost, run render, verify artifacts/evidence.
- [ ] Document terminal run order for existing jobs and full Tears runs.
- [ ] Add cost warning that preflight estimates are advisory and OpenAI provider billing remains external truth.
- [ ] Update smoke checklist with backend, frontend, terminal, full Tears, safety-exclusion, and blocked-result expectations.
- [ ] Add a decision record explaining why preflight is read-only and separate from render.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderPreflightServiceTests,LocalizationJobControllerTests,NarrationDemoRenderServiceTests,NarrationDemoPresetServiceTests,NarrationScriptPackageServiceTests`.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Document narration render preflight package`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderPreflightServiceTests,LocalizationJobControllerTests,NarrationDemoRenderServiceTests,NarrationDemoPresetServiceTests,NarrationScriptPackageServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
