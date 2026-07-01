# Custom Narration Render Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators take the current saved custom narration workspace, run a safe render preflight, generate narration audio plus optional narrated video, and export refreshed review/delivery evidence without applying a demo preset.

**Architecture:** Add a job-scoped custom narration render console that composes existing narration workspace, scene-board, render-review, audio generation, video generation, evidence, playback-review, and delivery-package services. Unlike `narration-demo/render`, this flow must never replace the workspace with a preset; it renders the operator-authored rows already saved on the job. Surface the console in the browser narration workspace, terminal demo scripts, and docs.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5, React, TypeScript, Vitest, Bash demo client.

## Global Constraints

- Keep this as one complete feature slice: backend API, frontend UX, demo CLI, docs, tests, validation, commit, and merge back to `main`.
- This feature renders the existing saved narration workspace only; it must not apply narration demo presets or replace operator-authored rows.
- Preflight is read-only and must not call OpenAI, call TTS providers, run FFmpeg, save rows, create artifacts, dispatch jobs, or mutate object storage.
- Render can call configured TTS providers and FFmpeg through existing services, so UI and CLI must require explicit provider-cost and video-generation acknowledgement.
- Preserve existing separate controls for selected-row TTS preview, generate audio, generate video, render review, playback review, and delivery package.
- Do not expose narration text, reviewer notes, transcript text, subtitle text, local paths, object keys, provider payloads, tokens, API keys, media bytes, or generated artifact bytes in console JSON/Markdown/terminal summaries.
- The console must handle partial success: if audio succeeds but video generation fails, keep the audio artifact and return `PARTIAL`.
- Prefer existing narration services over duplicating validation or artifact-generation logic.

---

## Design Decision

Recommended approach: create a `CustomNarrationRenderConsoleService` with two endpoints, one read-only preflight and one render action. This gives upload-seeded and manually edited narration rows a first-class production path without overloading the demo-preset renderer.

Alternatives considered:

- Reuse `narration-demo/render` with an empty preset id: this would blur preset replacement semantics and increase risk of accidentally replacing custom rows.
- Only rely on existing separate audio/video buttons: this works technically, but it does not give the operator one complete handoff surface for readiness, partial failures, evidence refresh, and terminal demos.

## Task 1: Backend Console Preflight

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/CustomNarrationRenderPreflightDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/CustomNarrationRenderCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/CustomNarrationRenderPreflightVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/CustomNarrationRenderConsoleService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/CustomNarrationRenderConsoleServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/CustomNarrationRenderConsoleServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Deliverable:** Operators can preflight the current saved custom narration workspace before spending provider credits.

- [x] Add a failing service test for a ready saved workspace returning `READY`, segment count, character count, total narration seconds, voice summary, scene-board status, provider mode, audio/video plan, and safe next actions.
- [x] Add failing tests for no saved rows, blocked scene-board checks, missing base video when video generation is requested, and paid provider attention.
- [x] Implement `POST /api/jobs/{jobId}/custom-narration-render/preflight` with request `generateNarratedVideo`, `acknowledgeProviderCost`, and `acknowledgeVideoRender`.
- [x] Compose existing `NarrationWorkspaceService`, `NarrationSceneBoardService`, `NarrationRenderReviewService`, `NarrationEvidenceService`, artifact lookup, and provider configuration.
- [x] Return status `READY`, `ATTENTION`, or `BLOCKED` with checks and safe commands only.
- [x] Ensure preflight never calls generation services or exposes row text.
- [x] Run `mvn -pl LinguaFrame -Dtest=CustomNarrationRenderConsoleServiceTests,LocalizationJobControllerTests#returnsCustomNarrationRenderPreflight test`.

## Task 2: Backend Render Action And Markdown Report

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/CustomNarrationRenderDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/CustomNarrationRenderStepVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/CustomNarrationRenderVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/CustomNarrationRenderReportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/CustomNarrationRenderReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/CustomNarrationRenderConsoleServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/CustomNarrationRenderReportServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Deliverable:** Operators can render the saved custom narration rows and download a safe render report.

- [x] Add `POST /api/jobs/{jobId}/custom-narration-render`.
- [x] Add `GET /api/jobs/{jobId}/custom-narration-render/markdown/download`.
- [x] Require acknowledged provider cost when provider is non-demo, and require video acknowledgement when `generateNarratedVideo=true`.
- [x] Re-run preflight inside render and block on `BLOCKED`.
- [x] Call existing narration audio generation first, then optional narrated-video generation.
- [x] Refresh render review, playback review, narration evidence, and narration delivery package after render.
- [x] Return step statuses for preflight, audio generation, video generation, render review, playback review, evidence, and delivery package.
- [x] Preserve partial success when audio succeeds and video fails.
- [x] Render Markdown with status, steps, artifact readiness, safe routes, and next commands only.
- [x] Verify Markdown excludes narration text, notes, object keys, local paths, provider payloads, and secrets.
- [x] Run focused backend render/report/controller tests.

## Task 3: Frontend Custom Render Console

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Deliverable:** The browser gives upload-seeded and manually edited narration rows a clear render workflow.

- [x] Add TypeScript request/response types and API helpers for preflight, render, and Markdown download.
- [x] Add API route-encoding tests for all custom render console endpoints.
- [x] Add a `Custom narration render` panel near `Upload narration launchpad`, scene board, and existing demo render panel.
- [x] Show saved workspace counts, scene-board status, provider mode, audio/video readiness, required acknowledgements, preflight checks, and next action.
- [x] Disable render until latest preflight is not `BLOCKED` and required acknowledgements are checked.
- [x] On render success or partial result, refresh artifacts, narration workspace, scene board, evidence, and delivery package data. Render/playback review reload through the workspace panel effects after artifacts/evidence refresh; acceptance/reviewer/portal refresh remains available through existing panels.
- [x] Display step rows and partial failure messages without hiding existing separate controls.
- [x] Add Markdown download button.
- [x] Add Vitest/API coverage for route encoding and acknowledgements; backend tests cover ready, blocked, paid-provider, success, and partial-video behavior.
- [x] Run `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts -t "custom narration render|custom narration"` and `npm --prefix frontend run build`.

## Task 4: Demo CLI And Full Demo Integration

**Files:**
- Create: `scripts/demo/custom-narration-render.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/README.md`

**Deliverable:** Terminal demos can preflight and render custom upload-seeded narration rows.

- [x] Add helpers to download custom render preflight JSON, run render JSON, download Markdown, and print metadata-only summaries.
- [x] Create `scripts/demo/custom-narration-render.sh` using `LINGUAFRAME_DEMO_JOB_ID`, `LINGUAFRAME_CUSTOM_NARRATION_RENDER_GENERATE_VIDEO`, `LINGUAFRAME_CUSTOM_NARRATION_RENDER_ACK_PROVIDER_COST`, and `LINGUAFRAME_CUSTOM_NARRATION_RENDER_ACK_VIDEO`.
- [x] Support report-only mode that performs preflight and exits before render.
- [x] Extend `docker-e2e-success.sh` so seeded upload narration can optionally run the custom renderer when `LINGUAFRAME_RENDER_CUSTOM_NARRATION=true`.
- [x] Extend full Tears script with the same optional custom renderer path, separate from preset render.
- [x] Add demo-client tests for routes, acknowledgement payloads, summary redaction, and no script text leakage.
- [x] Document commands and expected files under `/tmp/linguaframe-demo/custom-narration-render/`.
- [x] Run `bash -n scripts/demo/custom-narration-render.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.

## Task 5: Documentation, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/177-custom-narration-render-console.md`

**Deliverable:** The feature is documented, verified, committed, and merged back to `main`.

- [x] Document browser order: upload with narration script, inspect launchpad, save/edit workspace, run custom render preflight, acknowledge cost/video, render, inspect review/delivery/acceptance.
- [x] Document terminal order for seeded short demos and full Tears demos.
- [x] Add decision record: custom narration render is separate from demo preset render to preserve operator-authored rows.
- [x] Append validation evidence to execution log.
- [x] Run backend focused tests:
  - `mvn -pl LinguaFrame -Dtest=CustomNarrationRenderConsoleServiceTests,CustomNarrationRenderReportServiceTests,LocalizationJobControllerTests test`
- [x] Run frontend focused tests:
  - `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "custom narration render|narration render console"`
- [x] Run demo client tests:
  - `bash scripts/demo/test-linguaframe-demo-client.sh`
- [x] Run broad verification:
  - `mvn -pl LinguaFrame test`
  - `npm --prefix frontend test -- --run`
  - `npm --prefix frontend run build`
  - `bash -n scripts/demo/custom-narration-render.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/test-linguaframe-demo-client.sh`
  - `git diff --check`
- [ ] Commit with subject `Add custom narration render console`.
- [ ] Merge the verified branch back to `main`.

## Acceptance Criteria

- Upload-seeded narration rows can be taken from launchpad to preflight to render without applying a demo preset.
- Preflight is read-only and blocks unsafe render states before provider/FFmpeg work.
- Render generates narration audio and optional narrated video from the saved custom workspace.
- Partial video failure preserves generated narration audio and reports actionable safe next steps.
- Browser, API, Markdown, and CLI outputs are metadata-only and do not leak narration text or secrets.
- Existing separate narration controls and demo preset render flow continue to work.
