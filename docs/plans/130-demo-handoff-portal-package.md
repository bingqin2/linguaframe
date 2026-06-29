# Demo Handoff Portal Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add one offline, reviewer-facing demo handoff portal ZIP for a completed job that opens from `index.html` and links all safe job evidence in one place.

**Architecture:** Build a read-only backend aggregate from existing job evidence services and package it as JSON, Markdown, and a static HTML ZIP. The React selected-job view exposes a compact portal panel near the reviewer workspace. Demo scripts export the same portal files after deterministic, OpenAI smoke, and full Tears runs.

**Tech Stack:** Java 21, Spring Boot MVC, existing job evidence services, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This feature must be read-only: it must not upload media, create jobs, retry jobs, dispatch queues, call OpenAI, edit `.env`, run Docker, or download generated media bytes.
- Output must exclude API keys, bearer tokens, demo tokens, object storage keys, local filesystem paths, raw transcript text, raw subtitle text, provider request or response bodies, and media bytes.
- The portal should aggregate existing safe evidence surfaces instead of duplicating their implementation: reviewer workspace, acceptance gate, completion certificate, demo snapshot, share sheet, run monitor, delivery manifest, OpenAI smoke proof, AI audit package, handoff package, demo run package, diagnostics, and evidence Markdown.
- Missing optional evidence should produce `ATTENTION`, not `BLOCKED`; missing required terminal completion, safe package links, or reviewer workspace should produce `BLOCKED`.
- The ZIP must contain only static metadata files: `index.html`, `manifest.json`, `handoff-portal.md`, `reviewer-workspace.json`, `README.md`, and optional safe JSON snapshots. It must not embed uploaded or generated media artifacts.
- Every implementation task must include focused backend/frontend/script validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Portal Aggregate And Package

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoHandoffPortalVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoHandoffPortalSectionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoHandoffPortalCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoHandoffPortalLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredDemoHandoffPortalPackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoHandoffPortalService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- `DemoHandoffPortalVo getPortal(String jobId)`
- `String renderMarkdown(String jobId)`
- `StoredDemoHandoffPortalPackageBo openPackage(String jobId)`

- [x] Write failing service tests for `READY`, `ATTENTION`, `BLOCKED`, missing job propagation, static HTML entry presence, ZIP entries, and unsafe marker exclusion.
- [x] Implement portal VO records with `jobId`, `videoId`, `generatedAt`, `overallStatus`, `phase`, `headline`, `recommendedNextAction`, `completedAt`, `targetLanguage`, `demoProfileId`, `checks`, `sections`, `safeLinks`, `packageEntries`, and `safetyNotes`.
- [x] Compose portal data from `LocalizationJobQueryService`, `DemoReviewerWorkspaceService`, `DemoAcceptanceGateService`, `DemoCompletionCertificateService`, `DemoRunSnapshotService`, `DemoShareSheetService`, `DemoRunMonitorService`, `DeliveryManifestService`, and `OpenAiSmokeProofService`.
- [x] Derive status using required checks for completed job, reviewer workspace, safe evidence links, portal package entries, and required package routes.
- [x] Render Markdown with summary, checks, sections, safe links, package inventory, and safety notes.
- [x] Render static `index.html` with escaped text only, no inline raw transcript/subtitle bodies, and no external scripts.
- [x] Build ZIP with `index.html`, `manifest.json`, `handoff-portal.md`, `reviewer-workspace.json`, `README.md`, and safe JSON snapshots for acceptance/certificate/share-sheet/run-monitor when available.
- [x] Run `mvn -pl LinguaFrame test -Dtest=DemoHandoffPortalServiceTests`.

## Task 2: Job API Endpoints And Runtime Contract

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/demo-handoff-portal`
- `GET /api/jobs/{jobId}/demo-handoff-portal/markdown/download`
- `GET /api/jobs/{jobId}/demo-handoff-portal/download`

- [x] Add JSON, Markdown, and ZIP endpoints beside the reviewer workspace routes.
- [x] Use `Content-Disposition` filenames `linguaframe-job-{jobId}-demo-handoff-portal.md` and `.zip`.
- [x] Add routes to the runtime required-route contract.
- [x] Add MockMvc coverage for JSON status, Markdown headers/body safety, ZIP entries including `index.html`, auth behavior, runtime route contract, and unsafe marker exclusion.
- [x] Run `mvn -pl LinguaFrame test -Dtest=DemoHandoffPortalServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`.

## Task 3: React Selected-Job Portal Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getDemoHandoffPortal(jobId: string): Promise<DemoHandoffPortal>`
- `downloadDemoHandoffPortalMarkdown(jobId: string): Promise<Blob>`
- `downloadDemoHandoffPortalZip(jobId: string): Promise<Blob>`

- [x] Add TypeScript types matching the backend portal VO.
- [x] Add API helpers using the existing demo-token and bearer-token behavior.
- [x] Load the portal whenever selected job evidence loads: upload completion, manual open, retry/cancel result, recent/history/dashboard selection, polling, and SSE refresh.
- [x] Add a `Demo handoff portal` panel near `Demo reviewer workspace` with status, phase, headline, required checks, optional checks, package entries, safe links, refresh, Markdown download, and ZIP download.
- [x] Keep selected-job details usable if portal loading fails.
- [x] Add Vitest coverage for API paths, panel rendering, failure state, Markdown/ZIP downloads, and unsafe marker absence.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.

## Task 4: Terminal Export, E2E Integration, And Docs

**Files:**
- Create: `scripts/demo/demo-handoff-portal.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-handoff-portal.sh`
- Default output directory: `/tmp/linguaframe-demo/demo-handoff-portal/`

- [x] Add helper functions to download portal JSON, Markdown, and ZIP.
- [x] Add `print_demo_handoff_portal_summary_file` that validates ZIP entries, checks unsafe markers, and prints `demoHandoffPortal*` summary keys.
- [x] Implement the standalone script to write `demo-handoff-portal.json`, `.md`, and `.zip`; exit non-zero on `BLOCKED` unless `LINGUAFRAME_DEMO_HANDOFF_PORTAL_REPORT_ONLY=true`.
- [x] Extend deterministic, OpenAI smoke, and full Tears E2E scripts to export the portal after job completion.
- [x] Document when to use portal versus reviewer workspace, demo snapshot, demo run package, evidence closure, acceptance gate, completion certificate, and OpenAI smoke proof.
- [x] Update smoke checklist with browser panel expectations, script output, ZIP entries, `index.html`, and safety exclusions.
- [x] Run `bash -n scripts/demo/demo-handoff-portal.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/130-demo-handoff-portal-package.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=DemoHandoffPortalServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/demo-handoff-portal.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run full safety checks:
  `mvn -pl LinguaFrame test`
  `npm test -- --run`
  `git diff --check`
- [x] Commit as `Add demo handoff portal package`.
- [x] Merge the feature branch back to `main` after validation passes.
