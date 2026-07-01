# Narration Studio Guided Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a guided narration studio surface that helps an operator move from seeded/custom narration rows through preview, render, review, delivery, and final handoff without hunting across many panels.

**Architecture:** Add a metadata-only backend orchestration endpoint that composes existing narration workspace, upload launchpad, scene-board, render review, playback resolution, custom render handoff, delivery package, acceptance gate, reviewer workspace, and handoff portal state. Add a dense frontend workbench inspired by the `fish-tts-desktop` style reference: compact header, step rail, central narration status/editing context, and right-side readiness inspector. Reuse existing mutation endpoints for save/preview/render; the new orchestration endpoint stays read-only.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5, React, TypeScript, Vitest, Bash demo client.

## Global Constraints

- This is one complete feature slice: backend aggregate, browser workbench, terminal script, docs, tests, validation, commit, and merge back to `main`.
- Keep the new orchestration endpoint read-only and metadata-only.
- Do not embed narration text, reviewer note bodies, transcript text, subtitle text, local paths, object keys, provider payloads, API keys, tokens, generated media bytes, or uploaded media bytes in the studio aggregate.
- Do not call OpenAI/TTS providers, run FFmpeg, save rows, upload media, render media, mutate object storage, or start Docker from the studio aggregate endpoint.
- Reuse existing endpoints and services for workspace save, segment preview, custom render, render review, playback review/resolution, delivery package, acceptance gate, reviewer workspace, and handoff portal.
- Frontend should follow the existing LinguaFrame UI conventions while borrowing the dense workbench feel from `Sherlockouo/fish-tts-desktop`: compact controls, clear step rail, central work surface, and a right inspector.

---

## Design Decision

Recommended approach: add a new read-only `Narration Studio` aggregate and browser panel instead of rewriting the existing narration workspace. This gives operators one guided command center while preserving the stable specialized panels and existing APIs.

Alternatives considered:

- Merge all controls directly into the existing narration workspace. This would make the already large panel harder to scan and test.
- Build only a frontend-only guide from currently loaded state. This avoids a backend endpoint, but it would duplicate readiness rules and make terminal/demo evidence weaker.
- Persist a new narration session model. This is unnecessary until multi-user/manual session ordering becomes a real requirement.

## Task 1: Backend Narration Studio Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationStudioStepVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationStudioLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationStudioVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationStudioService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationStudioServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationStudioServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Deliverable:** `GET /api/jobs/{jobId}/narration-studio` returns a safe end-to-end narration workflow state.

- [x] Add tests for a ready custom narration path: saved rows exist, scene board ready, audio/video rendered, playback resolved, delivery ready, final handoff links available.
- [x] Add tests for missing custom render output: saved rows exist but audio/video not ready, studio status is `ATTENTION`, next action points to custom render.
- [x] Add tests for no saved narration rows: studio status is `EMPTY`, next action points to upload seeding or workspace authoring.
- [x] Implement `NarrationStudioVo` with fields: `jobId`, `videoId`, `generatedAt`, `overallStatus`, `phase`, `recommendedNextAction`, `segmentCount`, `characterCount`, `audioReady`, `videoReady`, `steps`, `links`, `safetyNotes`.
- [x] Build ordered steps: `AUTHOR_ROWS`, `PREVIEW_TTS`, `RENDER_CUSTOM`, `REVIEW_PLAYBACK`, `PACKAGE_DELIVERY`, `FINAL_HANDOFF`.
- [x] Add safe links for workspace, upload launchpad, segment preview, custom render report, render review, playback resolution, delivery package, acceptance gate, reviewer workspace, and handoff portal.
- [x] Verify the aggregate excludes narration text and all unsafe payload/path/secret/media fields.

## Task 2: Frontend Narration Studio Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Deliverable:** Selected jobs show a compact `Narration studio` workbench that guides the operator through the full narration workflow.

- [x] Add TypeScript types and API helper for `getNarrationStudio(jobId)`.
- [x] Load narration studio state alongside existing selected-job narration data.
- [x] Add a `NarrationStudioPanel` near the narration workspace/final handoff surfaces.
- [x] Render a compact step rail with status chips for author, preview, render, review, package, and handoff.
- [x] Render central summary cards for row count, character count, audio/video readiness, current phase, and next action.
- [x] Render a right inspector with safe links to existing specialized panels/routes.
- [x] Keep existing save, preview, custom render, delivery, and final handoff controls as the actual mutation surfaces; the studio links to them and summarizes state.
- [x] Add Vitest coverage for ready state, missing-render attention state, and empty state.

## Task 3: Terminal Narration Studio Script

**Files:**
- Create: `scripts/demo/narration-studio.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`

**Deliverable:** Terminal demos can export the same narration studio summary as JSON and Markdown-friendly lines.

- [x] Add helper `download_narration_studio_json`.
- [x] Add helper `print_narration_studio_summary_file` with status, phase, segment count, audio/video readiness, step statuses, safe links, and safety notes.
- [x] Add `scripts/demo/narration-studio.sh` writing `/tmp/linguaframe-demo/narration-studio/narration-studio.json`.
- [x] Integrate the summary download after upload-seeded narration and after full Tears custom render.
- [x] Add shell client tests for encoded job IDs, safe route output, and redaction markers.
- [x] Run shell syntax checks and demo-client tests.

## Task 4: Documentation And Product State

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/179-narration-studio-guided-workbench.md`

**Deliverable:** The guided workbench is documented as the preferred operator entry point for custom narration demos.

- [x] Document browser order: upload/seed rows, open Narration studio, save/edit in workspace, preview TTS, render custom narration, review playback, export delivery/final handoff.
- [x] Document terminal order: upload with `LINGUAFRAME_DEMO_NARRATION_SCRIPT`, run `scripts/demo/narration-studio.sh`, optionally run custom render, then refresh studio.
- [x] Add a decision record explaining why this is a read-only aggregate instead of a new persisted narration session model.
- [x] Update roadmap and target-state to identify the studio as the guided entry point, while existing specialized controls remain authoritative.
- [x] Append validation evidence to execution log.

## Task 5: Verification, Commit, And Merge

**Files:**
- All files above.

**Deliverable:** The slice is verified, committed, and merged back to `main`.

- [x] Run focused backend tests: `mvn -pl LinguaFrame -Dtest=NarrationStudioServiceTests,LocalizationJobControllerTests test`.
- [x] Run focused frontend tests: `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration studio|Narration studio"`.
- [x] Run shell checks: `bash -n scripts/demo/narration-studio.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run demo client tests: `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run broad verification: `mvn -pl LinguaFrame test`, `npm --prefix frontend test -- --run`, `npm --prefix frontend run build`, and `git diff --check`.
- [x] Commit with subject `Add guided narration studio workbench`.
- [x] Merge the verified branch back to `main`.

## Acceptance Criteria

- A selected job has one `Narration studio` surface that clearly shows where the operator is in the custom narration workflow.
- The studio can represent `EMPTY`, `ATTENTION`, `READY`, and `BLOCKED` states without hiding existing specialized panels.
- Operators can reach existing save/preview/render/review/package/final handoff routes from safe studio links.
- Terminal demos can export and print the same studio state.
- The aggregate remains read-only and metadata-only, with no provider calls, FFmpeg runs, row saves, artifact writes, raw text, secrets, paths, or media bytes.
- Existing upload narration launchpad, narration workspace, custom render, delivery package, acceptance gate, reviewer workspace, and handoff portal behavior continues to work.
