# Private Demo Launch Rehearsal Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one launch rehearsal workspace that proves a private demo is ready to present, recover, and hand off before uploading media or spending OpenAI credits.

**Architecture:** Add a read-only backend aggregate under the operator boundary that converts existing deployment, readiness, live-check, backup, OpenAI preflight, and demo-evidence signals into a staged launch checklist. Render the same checklist in React and export it from terminal scripts so browser, CI-like local runs, and deployment docs all use one contract. Keep all outputs metadata-only and command-oriented; do not run backups, restores, uploads, OpenAI calls, or cleanup from the API.

**Tech Stack:** Spring Boot MVC, existing runtime/operator services, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend API, frontend workspace, terminal rehearsal script, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep the launch rehearsal metadata-only. Do not expose API keys, demo tokens, passwords, object keys, raw local media paths, provider payloads, raw transcript/subtitle text, uploaded media bytes, or backup directory contents.
- The backend API is read-only. It must not run Docker, create backups, restore data, upload media, call OpenAI, delete retention candidates, or mutate provider state.
- The terminal script may call existing safe preflight/report endpoints and existing dry-run scripts, but must default to no provider spend and no media upload.
- Use existing private-demo patterns before adding new abstractions.

---

## Current Context

- Private demo deployment already has `docs/deployment/private-demo.md`, Compose/Caddy overlay, owner access token gate, `private-demo-preflight.sh`, backup/restore scripts, OpenAI preflight, `Private demo operations`, and full demo evidence exports.
- The remaining gap is launch rehearsal: one operator workflow that says which steps are ready, which command should run next, which evidence files are expected, and whether the demo is blocked before the owner spends credits or invites a viewer.
- This feature should sit above `Private demo operations`: operations explains current runtime health, while launch rehearsal explains the ordered go/no-go path for a demo session.

## Task 1: Backend Launch Rehearsal API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoLaunchRehearsalStepVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoLaunchRehearsalVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/PrivateDemoLaunchRehearsalService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/PrivateDemoLaunchRehearsalServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/PrivateDemoLaunchRehearsalServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Produces: `GET /api/operator/private-demo/launch-rehearsal`.
- Produces: `PrivateDemoLaunchRehearsalVo(generatedAt, overallStatus, readyCount, attentionCount, blockedCount, recommendedNextStepId, steps, evidenceDownloads, rehearsalNotesMarkdown)`.
- Produces step rows with `id`, `title`, `status`, `detail`, `command`, `evidencePath`, `nextAction`, and `blocking`.
- Status values: `READY`, `ATTENTION`, `BLOCKED`.

- [x] Write failing service tests for ordered launch steps, overall status, recommended next step, safe evidence links, and metadata redaction.
- [x] Add VO records and `PrivateDemoLaunchRehearsalService`.
- [x] Implement launch rehearsal composition from `PrivateDemoOperationsService.operations()`, `RuntimeDependencySummaryService.getSummary()`, and static safe command templates.
- [x] Generate ordered steps for deploy preflight, stack startup, private preflight, OpenAI preflight, backup dry-run, restore dry-run, short smoke demo, full Tears demo, presenter pack export, and operations report export.
- [x] Mark deploy/runtime/access/dependency failures as blocking, and mark optional OpenAI/full-video readiness as attention unless providers are enabled and live checks are ready.
- [x] Add controller endpoint and runtime/OpenAPI route assertions.
- [x] Run `mvn -pl LinguaFrame -Dtest=PrivateDemoLaunchRehearsalServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`.

## Task 2: Browser Launch Rehearsal Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `PrivateDemoLaunchRehearsal` from `/api/operator/private-demo/launch-rehearsal`.
- Produces: a `Private demo launch rehearsal` region with go/no-go status, ordered checklist, commands, expected evidence paths, copy notes, and download notes.

- [x] Write failing API test for `getPrivateDemoLaunchRehearsal()` with stored demo token header.
- [x] Add TypeScript interfaces for launch rehearsal aggregate and steps.
- [x] Implement API helper and load the rehearsal alongside existing private-demo operations.
- [x] Write failing App test that verifies overall status, recommended next step, blocking step display, command visibility, evidence paths, copy notes, and download notes.
- [x] Render `PrivateDemoLaunchRehearsalPanel` near `PrivateDemoOperationsPanel` without blocking upload controls when unavailable.
- [x] Use dense operational styling consistent with existing panels; no hero layout or nested decorative cards.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "private demo launch rehearsal"`.

## Task 3: Terminal Launch Rehearsal Report

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/private-demo-launch-rehearsal.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces: `download_private_demo_launch_rehearsal_json BASE_URL OUTPUT_PATH`.
- Produces: `print_private_demo_launch_rehearsal_summary_file REHEARSAL_JSON_PATH`.
- Produces: `scripts/demo/private-demo-launch-rehearsal.sh` that writes JSON and Markdown notes under `/tmp/linguaframe-demo/private-demo-launch-rehearsal/`.

- [x] Write failing script tests for encoded route, status summary, recommended next step, metadata-only redaction, and non-zero exit only when status is `BLOCKED`.
- [x] Implement JSON download helper and safe Python summary printer.
- [x] Implement terminal script that sources existing env handling, fetches the launch rehearsal API, writes `launch-rehearsal.json`, writes `launch-rehearsal.md`, prints a concise summary, and exits non-zero on `BLOCKED`.
- [x] Ensure the script does not call OpenAI, upload media, create backups, restore data, or run Docker by default.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/private-demo-launch-rehearsal.sh`.

## Task 4: Documentation, Verification, Commit, Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/096-private-demo-launch-rehearsal-workspace.md`

**Interfaces:**
- Documents when to use operations readiness versus launch rehearsal, how to run the terminal report, what evidence files are produced, and which commands remain manual.

- [x] Document the browser launch rehearsal workflow and terminal `private-demo-launch-rehearsal.sh` output.
- [x] Record the decision to keep launch rehearsal read-only and command-oriented rather than auto-running deployment, backup, restore, OpenAI, or upload steps.
- [x] Update execution log with implementation and validation evidence.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on `private-demo-launch-rehearsal`, then merge back to `main` after validation.
