# Upload Narration Launchpad Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Give operators a clear post-upload launchpad when a video is uploaded with narration rows, connecting seeded script intake to workspace editing, TTS preview, render preflight, and demo commands.

**Architecture:** Add a metadata-only backend launchpad service for a job that composes upload/job metadata, narration workspace summary, voice catalog provider/default voice, scene-board readiness, render-preflight route, and safe next actions. Surface the same launchpad in the browser after upload and in the job detail narration area, plus a terminal script for demos. The launchpad must not call TTS providers, OpenAI, FFmpeg, save narration rows, create artifacts, or expose narration text outside explicit workspace/script-package views.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React, TypeScript, Vitest, Bash demo client.

## Global Constraints

- Keep this as one complete feature slice: backend API, frontend UX, demo CLI, docs, tests, validation, commit, and merge back to `main`.
- The launchpad is read-only and metadata-only. It must not synthesize audio, render video, mutate object storage, call providers, or save narration rows.
- Do not expose narration script text, reviewer notes, local paths, object keys, provider payloads, tokens, API keys, or media bytes.
- Preserve existing upload behavior when no narration script is supplied.
- Prefer existing narration workspace, scene-board, render-preflight, and voice catalog services instead of duplicating business rules.
- Keep the operator path explicit: preview TTS and render actions remain separate confirmed actions.

---

## Design Decision

Recommended approach: build a small `UploadNarrationLaunchpadService` keyed by `jobId`, then call it from browser and CLI surfaces. This produces a reusable operational bridge without changing the upload transaction or hiding provider-cost actions.

Alternatives considered:

- Put all launchpad fields directly into `MediaUploadVo`: tempting for upload success only, but it does not work when reopening an existing job later.
- Auto-open or auto-render narration after upload: faster demo path, but it violates the explicit-cost and explicit-render requirements.

## Task 1: Backend Launchpad Model And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/UploadNarrationLaunchpadVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/UploadNarrationLaunchpadActionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/UploadNarrationLaunchpadService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/UploadNarrationLaunchpadServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/UploadNarrationLaunchpadServiceTests.java`

**Deliverable:** A read-only service returns safe launchpad status for any owner-visible job.

- [x] Add a failing test for a job with seeded narration rows returning `READY`, segment count, character count, configured voice summary, provider/default voice, selected first segment index, and actions for workspace, TTS preview, render preflight, and render review.
- [x] Add a failing test for a job without narration rows returning `NOT_APPLICABLE` and a next action to add narration rows.
- [x] Add a failing test for invalid or blocked workspace state returning `BLOCKED` with safe issue counts but no narration text.
- [x] Implement `UploadNarrationLaunchpadService.getLaunchpad(String jobId)` by composing existing job query, narration workspace, voice catalog, and scene-board metadata.
- [x] Ensure the service returns only safe links such as `/api/jobs/{jobId}/narration-workspace`, `/api/jobs/{jobId}/narration-scene-board`, `/api/jobs/{jobId}/narration-demo-render-preflight`, and `/api/jobs/{jobId}/narration-render-review`.
- [x] Run `mvn -pl LinguaFrame -Dtest=UploadNarrationLaunchpadServiceTests test`.

## Task 2: Backend API And Markdown Download

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/UploadNarrationLaunchpadReportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/UploadNarrationLaunchpadReportServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/UploadNarrationLaunchpadReportServiceTests.java`

**Deliverable:** Operators can fetch JSON and Markdown launchpad summaries for a job.

- [x] Add `GET /api/jobs/{jobId}/upload-narration-launchpad`.
- [x] Add `GET /api/jobs/{jobId}/upload-narration-launchpad/markdown/download`.
- [x] Render Markdown with status, next action, segment count, character count, voice summary, provider/default voice, readiness counts, safe links, and terminal commands only.
- [x] Verify Markdown excludes narration text and object storage details.
- [x] Run focused controller/report tests.

## Task 3: Frontend Launchpad Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Deliverable:** The browser shows a compact launchpad after upload and when reopening a job.

- [x] Add TypeScript types and API helpers for JSON and Markdown download.
- [x] Load launchpad data alongside narration workspace data in `loadJob`.
- [x] In the upload success path, when `narrationScriptSeeded` is true, show a launchpad status message and the launchpad panel without requiring manual job-id entry.
- [x] Add a `Upload narration launchpad` region near the narration workspace summary with status, next action, counts, provider/default voice, and buttons/links for workspace focus, TTS preview panel, render preflight, render review, and Markdown download.
- [x] Keep all provider-cost actions explicit; the panel may focus existing UI controls but must not call preview/render endpoints by itself.
- [x] Add Vitest coverage for seeded upload showing the launchpad, reopened job loading the panel, Markdown download, and launchpad API failure staying local to the panel.
- [x] Run `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "upload narration launchpad|narration launchpad"`.

## Task 4: Demo CLI And Documentation

**Files:**
- Create: `scripts/demo/upload-narration-launchpad.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Deliverable:** Terminal demos can inspect the same launchpad after a seeded upload.

- [x] Add `download_upload_narration_launchpad` helper that writes JSON and Markdown under `/tmp/linguaframe-demo/upload-narration-launchpad/`.
- [x] Add `scripts/demo/upload-narration-launchpad.sh` using `LINGUAFRAME_DEMO_JOB_ID`.
- [x] Extend `docker-e2e-success.sh` to fetch and print launchpad status when `narrationScriptSeeded=true`.
- [x] Add demo-client tests for JSON download, Markdown download, status printing, and no script text leakage.
- [x] Document the recommended flow: upload with `LINGUAFRAME_DEMO_NARRATION_SCRIPT_FILE`, inspect launchpad, preview selected-row TTS explicitly, run render preflight, then render.
- [x] Record the decision that upload-time narration launchpad is read-only and does not replace explicit TTS/render actions.

## Task 5: Verification, Commit, And Merge

**Files:**
- All files changed by Tasks 1-4.

**Deliverable:** The feature is verified, committed, and merged back to `main`.

- [x] Run backend focused tests:
  - `mvn -pl LinguaFrame -Dtest=UploadNarrationLaunchpadServiceTests,UploadNarrationLaunchpadReportServiceTests,LocalizationJobControllerTests test`
- [x] Run frontend focused tests:
  - `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "upload narration launchpad|narration launchpad"`
- [x] Run demo client tests:
  - `bash scripts/demo/test-linguaframe-demo-client.sh`
- [x] Run broader verification:
  - `mvn -pl LinguaFrame test`
  - `npm --prefix frontend test -- --run`
  - `npm --prefix frontend run build`
  - `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/upload-narration-launchpad.sh scripts/demo/docker-e2e-success.sh scripts/demo/test-linguaframe-demo-client.sh`
  - `git diff --check`
- [x] Commit with subject `Add upload narration launchpad`.
- [x] Merge the verified branch back to `main`.

## Acceptance Criteria

- Upload without narration keeps current behavior and has no misleading launchpad readiness.
- Upload with valid narration rows gives a visible next-step launchpad in browser and CLI.
- Reopening the same job shows the same launchpad from persisted workspace state.
- The launchpad helps the operator move to workspace edit, TTS preview, render preflight, and render review without triggering provider-cost or FFmpeg actions.
- JSON, Markdown, browser, and terminal output expose only safe metadata and links.
- Invalid or blocked narration state is reported as `BLOCKED` with actionable safe next steps.
