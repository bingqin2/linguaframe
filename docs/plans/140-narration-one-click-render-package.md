# Narration One-Click Render Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-click narration demo render flow that applies the profile narration preset, generates narration audio, generates the narrated video, and exports refreshed evidence for a completed job.

**Architecture:** Keep existing narration workspace, preset apply, audio generation, video generation, script package, and evidence services as the source of truth. Add a thin orchestration service and endpoint that executes those existing steps in order and returns a step-by-step result object for browser and terminal use. The feature must not add a new TTS provider, clone voices, edit source media, or hide the existing separate controls.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing Bash demo helpers.

## Global Constraints

- This is one complete feature slice: backend orchestration endpoint, frontend render panel, terminal script, full Tears script integration, docs, focused tests, full validation, commits, and merge back to `main`.
- Use branch title `narration-one-click-render-package`; do not include `/` in the user-facing branch title.
- The render endpoint may call existing preset apply, narration audio generation, and narrated-video generation services, but it must not duplicate their validation or persistence logic.
- Applying the preset still requires explicit replace semantics; the render request must make replacement visible in the request and response.
- Audio and video generation can call paid OpenAI TTS when configured, so docs and UI must warn that render can consume provider credits.
- Render outputs must not expose transcript text, subtitle text, object keys, local paths, demo tokens, provider payloads, credentials, API keys, uploaded media bytes, or generated media bytes inside JSON/Markdown summaries.
- If audio generation succeeds but video generation fails, the response must report partial progress and leave the generated `NARRATION_AUDIO` artifact intact.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Render Orchestration Endpoint

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/RenderNarrationDemoDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationDemoRenderStepVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationDemoRenderVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationDemoRenderService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationDemoRenderServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationDemoRenderServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-demo/render`
- Request: `RenderNarrationDemoDto(String presetId, boolean replaceExisting, boolean generateNarratedVideo)`
- Response: `NarrationDemoRenderVo` with job id, preset id, overall status, step rows, preset apply result, narration audio result, optional narrated video result, refreshed script package, refreshed evidence, and generated artifact count.

- [x] Write service tests for full render success, missing `replaceExisting=true`, audio failure before video, video failure after audio success, and `generateNarratedVideo=false`.
- [x] Implement `RenderNarrationDemoDto`, `NarrationDemoRenderStepVo`, and `NarrationDemoRenderVo` as Java records using existing VO types for nested results.
- [x] Implement `NarrationDemoRenderServiceImpl` to call `NarrationDemoPresetApplyService.applyPreset`, `NarrationAudioService.generateAudio`, optional `NarratedVideoService.generateVideo`, `NarrationScriptPackageService.getPackage`, `NarrationEvidenceService.getEvidence`, and `JobArtifactService.listArtifacts`.
- [x] Represent each step as `READY`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `SKIPPED` with a short safe message and no raw script text.
- [x] Preserve partial success: catch the narrated-video exception after successful audio generation, refresh evidence, return overall `PARTIAL`, and do not delete artifacts.
- [x] Add controller route with OpenAPI summary, 400/401/404 responses, and route-encoding MockMvc coverage.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderServiceTests,LocalizationJobControllerTests,NarrationDemoPresetApplyServiceTests,NarrationScriptPackageServiceTests`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration demo render endpoint`.

## Task 2: Frontend One-Click Render Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `renderNarrationDemo(jobId: string, request: RenderNarrationDemoRequest): Promise<NarrationDemoRenderResult>`

- [x] Add TypeScript types for render request, step result, and render result with exact status unions.
- [x] Add API helper and route-encoding tests for `/api/jobs/{jobId}/narration-demo/render`.
- [x] Add a `Render narration demo` panel inside the selected-job narration workspace near `Demo narration preset`.
- [x] Require explicit replace confirmation and a separate paid-provider acknowledgement before enabling render.
- [x] Include a `Generate narrated video` checkbox that defaults on and maps to `generateNarratedVideo`.
- [x] On success or partial result, refresh narration workspace, script package, narration evidence, and artifacts.
- [x] Display step rows for preset apply, audio generation, video generation, script package refresh, and evidence refresh.
- [x] Preserve existing separate buttons for apply preset, generate audio, generate video, script package import/export, and evidence export.
- [x] Add Vitest coverage for disabled render states, successful full render, partial video failure message, no-video mode, and refresh calls.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration demo render UI`.

## Task 3: Terminal Render Script And Full Tears Integration

**Files:**
- Create: `scripts/demo/narration-demo-render.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `docs/plans/140-narration-one-click-render-package.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render.sh`
- `LINGUAFRAME_RENDER_NARRATION_DEMO=true scripts/demo/docker-e2e-tears-of-steel-full.sh`

- [x] Add shared Bash helper `render_narration_demo_json "$base_url" "$job_id" "$preset_id" "$generate_video" "$output_path"`.
- [x] Add `print_narration_demo_render_summary_file` that prints overall status, step statuses, preset id, audio filename, narrated-video filename when present, evidence status, artifact counts, and output paths.
- [x] Create `scripts/demo/narration-demo-render.sh` with env vars `LINGUAFRAME_NARRATION_DEMO_RENDER_OUTPUT_DIR`, `LINGUAFRAME_NARRATION_DEMO_PRESET_ID`, `LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO`, and `LINGUAFRAME_NARRATION_DEMO_RENDER_REPORT_ONLY`.
- [x] In report-only mode, list the recommended preset and exit without calling the render endpoint.
- [x] In normal mode, call render, download refreshed narration script package JSON/Markdown/ZIP, download refreshed narration evidence JSON/Markdown/ZIP, and validate summaries.
- [x] Extend full Tears script so `LINGUAFRAME_RENDER_NARRATION_DEMO=true` runs the render script before narration evidence export.
- [x] Keep existing `LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true` behavior for apply-only flows; if both are true, render wins and apply-only is skipped.
- [x] Run `bash -n scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration demo render script`.

## Task 4: Documentation, Final Verification, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/140-narration-one-click-render-package.md`
- Modify: `docs/progress/execution-log.md`

- [x] Document browser run order: completed job, render narration demo, inspect step rows, verify `NARRATION_AUDIO`, verify optional `NARRATED_VIDEO`, export evidence.
- [x] Document terminal run order for existing jobs and full Tears runs.
- [x] Add cost warning that OpenAI TTS render can consume credits when configured.
- [x] Update smoke checklist with backend, frontend, terminal, full Tears, safety-exclusion, and partial-result expectations.
- [x] Add a decision record explaining why render orchestration reuses existing services and preserves partial audio artifacts.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderServiceTests,LocalizationJobControllerTests,NarrationDemoPresetApplyServiceTests,NarrationScriptPackageServiceTests`.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run `bash -n scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Commit with message `Document narration demo render package`.
- [x] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderServiceTests,LocalizationJobControllerTests,NarrationDemoPresetApplyServiceTests,NarrationScriptPackageServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
