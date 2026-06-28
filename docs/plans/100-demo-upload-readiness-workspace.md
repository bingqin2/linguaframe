# Demo Upload Readiness Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single pre-upload readiness workspace that tells the demo owner whether a selected demo run is safe to upload before storage, queue, FFmpeg, or provider spend.

**Architecture:** Build a backend aggregate endpoint from existing safe surfaces: runtime dependencies, live checks, demo session, owner quota preflight, demo profiles, and upload validation. The browser will show one compact `Upload readiness` panel beside the upload form, and terminal scripts will provide the same metadata-only go/no-go evidence.

**Tech Stack:** Spring Boot MVC, existing runtime/security/quota/media services, JUnit 5, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend aggregate API, upload readiness decisions, browser workspace, terminal evidence script, tests, docs, validation, commit, and merge back to `main`.
- Do not build public accounts, billing, JWT auth, tenant administration, or user-configurable owner IDs.
- Do not duplicate raw provider payloads, object keys, demo tokens, OpenAI keys, raw transcript/subtitle text, uploaded media bytes, generated media bytes, or local filesystem paths in readiness output.
- The effective owner remains `DemoOwnerIdentityService.currentOwnerId()`.
- File validation remains explicit and per selected file; readiness aggregation should not upload or persist media.
- The workspace must move the product closer to a repeatable private demo by reducing pre-upload uncertainty.

---

## Current Context

- Runtime readiness, live checks, demo session, owner quota preflight, upload validation, private-demo operations, launch rehearsal, and evidence gallery already exist as separate surfaces.
- The upload form currently shows file validation and owner quota separately, but there is no single go/no-go summary that combines selected profile, selected file, runtime readiness, owner session, dependency health, quota, and paid-provider risk.
- Terminal users can run several scripts, but there is no one metadata-only upload-readiness script for the exact pre-upload state.

## Task 1: Backend Upload Readiness Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/DemoUploadReadinessVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/DemoUploadReadinessCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/DemoUploadReadinessService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/DemoUploadReadinessServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/DemoUploadReadinessServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Add `GET /api/media/uploads/readiness?demoProfileId=<id>` returning:
  - `overallStatus`: `READY`, `ATTENTION`, or `BLOCKED`.
  - `ownerId`, `demoProfileId`, `generatedAt`.
  - `checks`: array of safe check rows with `id`, `label`, `status`, `detail`, `nextAction`, and `blocking`.
  - `requiredActions`: ordered safe action strings for the owner before upload.
  - `evidenceRoutes`: safe backend routes used for verification.
- Use existing services where possible: runtime dependency summary, live check summary, demo session status, owner quota preflight, and demo profile lookup.
- Treat the demo access gate as enforced before the readiness endpoint: unauthenticated private-demo requests receive `401`, while returned readiness metadata can mark owner-session access as ready. Mark `BLOCKED` for unhealthy required dependencies, owner quota blocked, or unknown demo profile id.
- Mark `ATTENTION` for OpenAI checks skipped while paid providers are enabled, disabled burn-in/TTS when selected profile expects them, or runtime readiness unavailable.

- [x] Write failing service tests for `READY`, access-gated API readiness, owner-quota `BLOCKED`, unknown-profile `BLOCKED`, and paid-provider `ATTENTION`.
- [x] Write failing controller test for `GET /api/media/uploads/readiness`.
- [x] Add OpenAPI and runtime required-route coverage for `/api/media/uploads/readiness`.
- [x] Implement VO types, service aggregation, controller route, safe checks, and route metadata.
- [x] Run `mvn -pl LinguaFrame -Dtest=DemoUploadReadinessServiceTests,MediaUploadControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.

## Task 2: Browser Upload Readiness Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `getDemoUploadReadiness(demoProfileId?: string)` to the frontend API layer.
- Add a compact `Upload readiness` panel near the upload controls.
- Refresh readiness on page load, demo profile changes, owner session changes, owner quota refresh, successful upload, and failed upload.
- Combine readiness with existing file validation in the UI:
  - Show backend go/no-go checks even before a file is selected.
  - Show file validation status once a file is selected and validated.
  - Disable upload only when backend readiness is `BLOCKED`, file validation fails, or owner quota is blocked.
- Keep validation available when readiness is blocked so local media issues can still be diagnosed.

- [x] Write failing API test for `getDemoUploadReadiness`.
- [x] Write failing App tests for `READY`, `ATTENTION`, `BLOCKED`, profile-change refresh, and upload disabled only on blocking states.
- [x] Add TypeScript types and API client function.
- [x] Render the upload readiness panel and integrate it with existing upload disabling logic.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.

## Task 3: Terminal Upload Readiness Evidence

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/upload-readiness.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/start-local-demo.sh`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Interfaces:**
- Add `download_upload_readiness_json(base_url, demo_profile_id, output_path)`.
- Add `print_upload_readiness_summary_file(path)` emitting only safe metadata:
  - `uploadReadinessOverall=...`
  - `uploadReadinessOwnerId=...`
  - `uploadReadinessDemoProfileId=...`
  - `uploadReadinessCheck=<status>:<id>:<label>`
  - `uploadReadinessRequiredAction=...`
- Create `scripts/demo/upload-readiness.sh`.
- Default output path: `/tmp/linguaframe-demo/upload-readiness.json`.
- Exit non-zero when `overallStatus=BLOCKED` unless `LINGUAFRAME_UPLOAD_READINESS_REPORT_ONLY=true`.
- Include the script in startup/preflight guidance without making it upload media or call paid providers.

- [x] Write failing script tests for route construction, summary redaction, blocked exit behavior, and report-only mode.
- [x] Implement shared shell helpers and `scripts/demo/upload-readiness.sh`.
- [x] Wire the command into startup/preflight guidance output where appropriate.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/upload-readiness.sh scripts/demo/start-local-demo.sh scripts/demo/private-demo-preflight.sh`.

## Task 4: Documentation, Validation, Commit, Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/100-demo-upload-readiness-workspace.md`

**Interfaces:**
- Document that upload readiness is a pre-upload go/no-go surface, not auth, billing, or media processing.
- Document browser and terminal usage.
- Document expected blocked/attention cases and how to proceed.
- Keep all validation commands in the execution log.

- [x] Update README, private-demo docs, Docker E2E guide, smoke checklist, roadmap, target state, decisions, and execution log.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [x] Commit on `demo-upload-readiness-workspace`, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
