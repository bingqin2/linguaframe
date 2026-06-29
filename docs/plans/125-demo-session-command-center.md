# Demo Session Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one operator-level demo session command center that guides a private demo from readiness through active processing to final evidence export.

**Architecture:** Compose existing safe operator/job surfaces instead of adding new pipeline behavior. The backend will aggregate private-demo operations, launch rehearsal, demo launcher, presentation cockpit, evidence gallery, run archive, and model usage ledger into one read-only session view. The frontend will render it near the existing operator panels, and a terminal script will export JSON and Markdown under `/tmp/linguaframe-demo/demo-session-command-center/`.

**Tech Stack:** Java 21, Spring Boot MVC, existing operator/job services, JUnit 5, React + Vite + TypeScript, Vitest/jsdom, Bash + Python demo scripts.

## Global Constraints

- This feature is read-only and must not upload media, create jobs, retry/cancel jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- It must expose only safe metadata: statuses, job ids, video ids, selected/recommended run ids, commands, safe API routes, counts, estimated cost, latency, cache counts, and safe next actions.
- It must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, corrected subtitle text, provider payloads, API keys, bearer tokens, demo tokens, and credentials.
- It must work before any job exists, while a job is active, after a job completes, and when recent provider failures make the session blocked.
- Existing launch rehearsal, presentation cockpit, model usage ledger, run archive, evidence gallery, and job detail behavior must remain backward compatible.

---

## Task 1: Backend Session Command Center Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSessionCommandCenterVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSessionCommandCenterPhaseVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSessionCommandCenterRunVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSessionCommandCenterActionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSessionCommandCenterEvidenceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/DemoSessionCommandCenterService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/DemoSessionCommandCenterServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/DemoSessionCommandCenterServiceTests.java`

**Interfaces:**
- `DemoSessionCommandCenterVo commandCenter(String jobId)` returns the current owner-scoped demo session command center.
- `String commandCenterMarkdown(String jobId)` renders metadata-only Markdown beginning with `# LinguaFrame Demo Session Command Center`.

- [ ] Compose existing services: `PrivateDemoOperationsService`, `PrivateDemoLaunchRehearsalService`, `DemoRunLauncherService`, `DemoPresentationCockpitService`, `PrivateDemoEvidenceGalleryService`, `PrivateDemoRunArchiveService`, and `ModelUsageLedgerService`.
- [ ] Accept optional `jobId`; when present, pass it to the presentation cockpit so the command center can focus a selected job.
- [ ] Compute `overallStatus` as `BLOCKED` if any required readiness/session section is blocked, `ATTENTION` if any section needs attention, `READY` when the session can proceed, and `EMPTY` only when there are no jobs and no model-call evidence.
- [ ] Compute `phase` as one of `PRE_UPLOAD`, `ACTIVE_RUN`, `POST_RUN_REVIEW`, `READY_TO_PRESENT`, or `BLOCKED`.
- [ ] Return `recommendedNextAction`, `primaryCommand`, `focusRun`, `activeRun`, `recommendedCompletedRun`, ordered phase rows, safe evidence links, and safety notes.
- [ ] Include model usage totals from the ledger: estimated cost, model-call count, failed-call count, failure-rate percent, average latency, and provider cache hits.
- [ ] Render Markdown sections: `Summary`, `Phase Checklist`, `Focus Run`, `Model Usage`, `Evidence Links`, `Commands`, and `Safety Notes`.
- [ ] Add tests for pre-upload empty state, active-run state, completed recommended-run state, and blocked provider-failure state.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=DemoSessionCommandCenterServiceTests`.

## Task 2: Operator API Endpoints And Controller Tests

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Interfaces:**
- `GET /api/operator/demo-session-command-center?jobId=<optional>` returns `DemoSessionCommandCenterVo`.
- `GET /api/operator/demo-session-command-center/markdown/download?jobId=<optional>` returns `demo-session-command-center.md`.

- [ ] Add constructor injection for `DemoSessionCommandCenterService`.
- [ ] Add JSON endpoint inheriting the existing `/api/operator/**` demo-token and bearer-token protection.
- [ ] Add Markdown download endpoint with `Content-Disposition: attachment; filename="demo-session-command-center.md"`.
- [ ] Add controller tests for empty JSON, focused job JSON, Markdown download, demo-token protection, and absence of raw paths/secrets/provider payloads.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=OperatorDashboardControllerTests`.

## Task 3: Frontend API And Command Center Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- `getDemoSessionCommandCenter(jobId?: string): Promise<DemoSessionCommandCenter>`
- `downloadDemoSessionCommandCenterMarkdown(jobId?: string): Promise<Blob>`

- [ ] Add TypeScript types for command center summary, phase rows, run rows, actions, and evidence links.
- [ ] Add API functions with optional encoded `jobId` query parameter and demo-token header support.
- [ ] Add API tests for default request, focused job request, and Markdown download.
- [ ] Add a `Demo session command center` panel near `Demo presentation cockpit` and `Model usage ledger`.
- [ ] Render overall status, phase, next action, primary command, focus/active/recommended run ids, model usage summary, phase checklist, and safe evidence links.
- [ ] Add refresh, copy Markdown, and backend Markdown download controls.
- [ ] Refresh the panel after upload, history selection, retry, cancel, job polling terminal state, and dashboard failed-job open.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.

## Task 4: Terminal Script, README, And Progress Log

**Files:**
- Create: `scripts/demo/demo-session-command-center.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/demo-session-command-center/`.
- Optional focused job env: `LINGUAFRAME_DEMO_JOB_ID`.
- Script output keys: `demoSessionCommandCenterStatus`, `demoSessionCommandCenterPhase`, `demoSessionCommandCenterNextAction`, `demoSessionCommandCenterFocusJobId`, `demoSessionCommandCenterJsonPath`, and `demoSessionCommandCenterMarkdownPath`.

- [ ] Implement the script with `demo_curl`, JSON export, Markdown export, and Python summary printing.
- [ ] Exit non-zero when backend `overallStatus` is `BLOCKED` unless `LINGUAFRAME_DEMO_SESSION_COMMAND_CENTER_REPORT_ONLY=true`.
- [ ] Document when to use command center versus launch rehearsal, presentation cockpit, model usage ledger, run archive, and evidence closure.
- [ ] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [ ] Run `bash -n scripts/demo/demo-session-command-center.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/125-demo-session-command-center.md`

- [ ] Mark this plan checklist complete after implementation.
- [ ] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=DemoSessionCommandCenterServiceTests,OperatorDashboardControllerTests`
- [ ] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/demo-session-command-center.sh scripts/demo/lib/linguaframe-demo.sh`
- [ ] Run `git diff --check`.
- [ ] Commit as `Add demo session command center`.
- [ ] Merge the feature branch back to `main` after validation passes.
