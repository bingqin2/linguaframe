# Upload Narration Script Intake Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Let operators paste optional time-coded narration rows during video upload, then create the localization job with an initialized editable narration workspace.

**Architecture:** Use the existing quick script grammar, `START-END | VOICE | TEXT`, as the upload-time intake format. The backend parses and validates the script after upload validation and before enqueueing the job, then seeds the existing narration workspace through the same persistence rules used by `saveWorkspace`; no TTS, OpenAI, FFmpeg, artifact, or object-storage narration generation runs during upload. Frontend upload, demo scripts, execution-plan metadata, and docs surface only safe counts and validation errors unless the operator explicitly opens the narration workspace or script package.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React, TypeScript, Vitest, Bash demo client.

## Global Constraints

- Keep this as one complete product feature: backend API, parser, workspace seeding, frontend upload UX, demo script support, tests, docs, validation, commit, and merge back to `main`.
- Preserve the current upload path when no narration script is supplied.
- Reject malformed quick script rows before storing media or enqueueing a job.
- Validate row indexes, timestamp order, non-overlap, text length, segment count, and configured voice presets through backend rules.
- Upload-time narration intake must not call OpenAI, call TTS providers, run FFmpeg, create narration artifacts, update evidence, or mutate object storage beyond the source video upload itself.
- General upload readiness, execution-plan, and decision-package outputs must expose metadata only: segment count, character count, voice summary, and readiness. Do not include narration script text there.
- Script text may appear only in explicit narration workspace or script-package flows.
- Existing narration demo presets remain a separate post-upload confirmed action; this feature is for operator-supplied upload-time quick script text.

---

## Approach Decision

Recommended approach: add an optional `narrationScript` multipart field to upload-related endpoints and reuse quick script syntax. This gives the user the desired "upload video plus custom explanation text" workflow without building a separate JSON authoring API or hidden render automation.

Alternatives considered:

- Structured JSON segments in upload requests: better for machines, worse for manual demos, and duplicates the existing quick script UX.
- Upload first, then immediately open a guided import wizard: lowest backend change, but it does not satisfy upload-time intake and can lose context if the browser closes.

## Task 1: Backend Quick Script Parser

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationQuickScriptParser.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationQuickScriptParserTests.java`
- Read: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`

**Deliverable:** A backend parser converts quick script text into `SaveNarrationSegmentsRequest.Segment` values that can be saved by the existing workspace service.

- [x] Add tests for `SS`, `MM:SS`, and `HH:MM:SS` ranges, explicit voice, inherited voice with empty middle field, blank-line skipping, malformed rows, overlapping rows, empty text, too many rows, and too-long text.
- [x] Implement `NarrationQuickScriptParser.parse(String script)` returning a result with `segments`, `segmentCount`, `characterCount`, `voiceSummary`, and `errors`.
- [x] Keep parser normalization aligned with workspace save rules: contiguous indexes, seconds rounded to three decimals, `null` voice when the voice field is blank.
- [x] Run `./mvnw -pl LinguaFrame -Dtest=NarrationQuickScriptParserTests test`.

## Task 2: Upload API And Workspace Seeding

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadVo.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceImplTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Deliverable:** `POST /api/media/uploads` accepts optional `narrationScript`, validates it, saves narration rows for the created job, and returns safe script metadata.

- [x] Extend upload method signatures with `String narrationScript`, keeping existing default overloads source-compatible.
- [x] Parse the script before source storage. If parsing fails, throw `IllegalArgumentException` with row-specific messages and do not store media.
- [x] Inject `NarrationWorkspaceService` or a small seeding service into `MediaUploadServiceImpl`; after `jobRepository.save(job)` and before dispatch enqueue, call `saveWorkspace(jobId, parsedRequest)` when parsed rows are present.
- [x] Add `narrationScriptSeeded`, `narrationScriptSegmentCount`, and `narrationScriptCharacterCount` to `MediaUploadVo`.
- [x] Verify upload without a script still returns zero counts and queues the job as before.
- [x] Verify upload with two rows creates workspace rows for the returned `jobId`.
- [x] Verify invalid script rejects the request and does not call object storage or dispatch outbox.
- [x] Run focused Maven tests for the changed media upload classes.

## Task 3: Upload Planning Metadata

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/UploadCostEstimateOptionsBo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadExecutionPlanVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadExecutionPlanServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadDecisionPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadExecutionPlanReportServiceImpl.java`
- Test: upload execution-plan and decision-package service tests.

**Deliverable:** Upload planning endpoints report narration intake readiness and counts without exposing script text.

- [x] Add optional `narrationScript` to upload option objects and controller helper methods for cost estimate, execution plan, and decision package endpoints.
- [x] Parse the script for execution planning and decision packages; expose `READY`, `ATTENTION`, or `BLOCKED` metadata based on parse validity.
- [x] Add Markdown lines for narration script segment count, character count, and voice summary only.
- [x] Ensure cost estimate does not add TTS cost for upload-time script intake; provider cost remains in later narration render/preview actions.
- [x] Run focused upload planning tests.

## Task 4: Frontend Upload Experience

**Files:**
- Create: `frontend/src/domain/narrationQuickScript.ts`
- Create: `frontend/src/domain/narrationQuickScript.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/domain/jobTypes.ts`
- Test: `frontend/src/api/linguaframeApi.test.ts`
- Test: `frontend/src/App.test.tsx`

**Deliverable:** The upload panel includes a compact narration script intake section with preview, validation, and API submission.

- [x] Extract existing quick script parsing and formatting helpers out of `App.tsx` into `frontend/src/domain/narrationQuickScript.ts`.
- [x] Reuse that helper in the existing narration workspace quick import/export to avoid divergence.
- [x] Add upload form state for `uploadNarrationScript`, local preview counts, and validation errors.
- [x] Show a compact workbench-style section near upload options with example rows:
  - `00:15-00:28 | alloy | Explain this moment.`
  - `00:55-01:10 || Inherit the default voice.`
- [x] Disable upload when local script validation has blocking errors.
- [x] Pass `narrationScript` through `uploadMedia`, execution-plan, decision-package, and Markdown/ZIP download calls.
- [x] After upload succeeds with seeded rows, load the returned job and narration workspace as usual so the user can edit or render from the initialized rows.
- [x] Add Vitest coverage for API form fields, blocked invalid upload, seeded upload success messaging, and existing workspace quick import/export behavior after helper extraction.

## Task 5: Demo Client And Docs

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `README.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Deliverable:** Terminal demos can seed upload-time narration scripts and documentation explains how to test the flow.

- [x] Add `LINGUAFRAME_DEMO_NARRATION_SCRIPT` and `LINGUAFRAME_DEMO_NARRATION_SCRIPT_FILE` support to `upload_demo_video`.
- [x] Print safe upload response metadata: `narrationScriptSeeded`, segment count, and character count.
- [x] Add demo-client tests confirming the multipart field is present when env/file input is set and absent when empty.
- [x] Document a sample command for Tears of Steel full-video upload with a narration script file.
- [x] Record the product decision: upload-time quick script seeds workspace only; render remains explicit.
- [x] Append validation evidence to `docs/progress/execution-log.md`.

## Task 6: Full Verification, Commit, And Merge

**Files:**
- All files changed by Tasks 1-5.

**Deliverable:** The feature is verified, committed on a feature branch, and merged back to `main`.

- [x] Run backend focused tests:
  - `./mvnw -pl LinguaFrame -Dtest=NarrationQuickScriptParserTests,MediaUploadServiceImplTests,MediaUploadControllerTests,UploadExecutionPlanServiceTests,UploadDecisionPackageServiceTests test`
- [x] Run frontend focused tests:
  - `npm --prefix frontend test -- --run src/domain/narrationQuickScript.test.ts src/api/linguaframeApi.test.ts src/App.test.tsx`
- [x] Run demo client tests:
  - `scripts/demo/test-linguaframe-demo-client.sh`
- [x] Run broader verification used by recent slices:
  - `./mvnw test`
  - `npm --prefix frontend run build`
  - `git diff --check`
- [x] Commit with an imperative subject such as `Add upload narration script intake`.
- [x] Merge the verified branch back to `main`.

## Acceptance Criteria

- Upload without narration behaves exactly as before.
- Upload with valid quick script rows creates a job whose narration workspace already contains those rows.
- Invalid upload-time script rows block upload before source storage and dispatch.
- Upload planning surfaces safe narration readiness metadata and never prints script bodies.
- Frontend users can paste, preview, validate, upload, and continue editing seeded narration rows.
- Demo scripts can seed narration from an environment string or file for repeatable demos.
- No provider call, TTS preview, audio generation, video generation, or FFmpeg operation runs as part of upload-time script intake.
