# Private Demo Operations Readiness Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one operator-facing private demo operations workspace that proves the hosted demo is ready to run, recover, and hand off before uploading media or spending provider budget.

**Architecture:** Add a backend aggregate readiness API under the existing `operator` boundary. It should compose sanitized runtime readiness, live dependency checks, retention cleanup preview, dashboard health, and documented private-demo commands into a single safe `PrivateDemoOperationsVo`. Add a React sidebar panel with actionable sections and Markdown export, plus a terminal script that fetches the same API and writes an operator handoff report.

**Tech Stack:** Spring Boot MVC, existing runtime/operator/retention services, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend API, frontend workspace, terminal report script, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep the response metadata-only. Do not expose API keys, demo tokens, passwords, object keys, raw local media paths, provider payloads, raw transcript/subtitle text, uploaded media bytes, or backup directory contents.
- Do not run deleting retention cleanup, restore data, create backups, upload media, call OpenAI, or mutate provider state from the readiness API.
- Live probes may reuse the existing bounded `RuntimeLiveCheckService`; all other checks must be configuration-derived or read-only.
- Tests must run without Docker, FFmpeg, OpenAI, network access, or real browser download APIs.

---

## Current Context

- Phase 9 already has the private-demo Compose overlay, Caddy reverse proxy, backup/restore scripts, deployment preflight, owner-session gate, upload limits, Redis rate limiting, budget guard visibility, runtime dependency readiness, live dependency checks, and operator retention cleanup controls.
- The browser currently shows these capabilities across separate panels: `Demo readiness`, `Live checks`, `Operator dashboard`, `Retention cleanup`, and runbook text.
- The remaining demo gap is operational proof: before a real private demo, the owner needs one browser and terminal artifact that answers `Can I run the demo? What is unsafe? What should I do next? What evidence can I hand off?`

## Task 1: Backend Operations Readiness API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoOperationsVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/PrivateDemoOperationsService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/PrivateDemoOperationsServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/PrivateDemoOperationsServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interface:**
- Endpoint: `GET /api/operator/private-demo/operations`
- Service: `PrivateDemoOperationsService.operations(): PrivateDemoOperationsVo`
- Top-level fields: `generatedAt`, `overallStatus`, `readyCount`, `attentionCount`, `blockedCount`, `sections`, `commands`, `documentationLinks`.
- Status values: `READY`, `ATTENTION`, `BLOCKED`.

- [x] **Step 1: Write failing backend service test**

Create service tests with stubbed runtime summary, live checks, operator dashboard, and retention cleanup preview.

Assert:

- `overallStatus` is `READY` when the demo gate, media limits, budget guard, live checks, dashboard, and dry-run retention preview are healthy.
- `overallStatus` becomes `ATTENTION` when OpenAI live check is `SKIPPED`, retention is disabled, or no jobs exist yet.
- `overallStatus` becomes `BLOCKED` when a required live dependency is `DOWN` or the backend runtime contract is stale.
- The serialized values never contain secret-like keys, raw filesystem paths, object keys, provider payloads, or token values.

Run:

```bash
mvn -pl LinguaFrame -Dtest=PrivateDemoOperationsServiceTests test
```

Expected: fail because the service does not exist.

- [x] **Step 2: Implement operation VO and service**

Implement compact records under `operator/domain/vo`:

```java
PrivateDemoOperationsVo
PrivateDemoOperationsSectionVo
PrivateDemoOperationsCheckVo
PrivateDemoOperationsCommandVo
PrivateDemoOperationsLinkVo
```

Sections should be:

- `Access gate`
- `Runtime contract`
- `Live dependencies`
- `Provider readiness`
- `Cost safety`
- `Storage and recovery`
- `Retention cleanup`
- `Demo evidence`

Each check must include a short `label`, `status`, `detail`, and `nextAction`.

- [x] **Step 3: Compose existing read-only services**

`PrivateDemoOperationsServiceImpl` should call:

- `RuntimeDependencySummaryService.getSummary()`
- `RuntimeLiveCheckService.check()`
- `OperatorDashboardService.dashboard()`
- `RetentionCleanupService.previewCleanup()`

Map results into the sections above. Include only safe command strings:

```bash
scripts/demo/private-demo-preflight.sh
scripts/demo/openai-demo-preflight.sh
scripts/demo/docker-e2e-success.sh
scripts/demo/private-demo-backup.sh --dry-run
scripts/demo/private-demo-restore.sh --dry-run --backup-dir <backup-dir>
```

Do not include actual `.env` values, host secrets, local sample paths, or backup paths from the machine.

- [x] **Step 4: Add controller endpoint and route contract**

Add `GET /api/operator/private-demo/operations` to `OperatorDashboardController` with OpenAPI metadata.

Update runtime dependency route contracts and OpenAPI tests so the endpoint is visible in generated docs and stale-backend checks.

- [x] **Step 5: Verify backend slice**

Run:

```bash
mvn -pl LinguaFrame -Dtest=PrivateDemoOperationsServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

## Task 2: Browser Operations Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/api/linguaframeApi.test.ts`
- Test: `frontend/src/App.test.tsx`

**Interface:**
- Add `getPrivateDemoOperations(): Promise<PrivateDemoOperations>`.
- Add `PrivateDemoOperationsPanel` with `aria-label="Private demo operations"`.

- [x] **Step 1: Write failing API and UI tests**

Assert the API helper calls `/api/operator/private-demo/operations` with the demo token header when stored.

Assert the browser panel shows:

- Overall status.
- Section counts.
- Access gate, live dependency, cost safety, storage/recovery, retention, and evidence checks.
- Safe command examples.
- `Copy operations report` and `Download operations report` actions.

Run:

```bash
cd frontend && npm run test:run -- linguaframeApi -t "private demo operations"
cd frontend && npm run test:run -- App -t "private demo operations"
```

Expected: fail because the API and panel do not exist.

- [x] **Step 2: Implement types, API helper, and sidebar loader**

Load operations readiness alongside the existing dashboard, runtime dependencies, live checks, and retention preview. Failures must not block upload controls.

When unavailable, render `Operations readiness unavailable` with the safe error message.

- [x] **Step 3: Implement operations report formatter**

Create `formatPrivateDemoOperationsReport(operations)` in `frontend/src/App.tsx`.

Markdown output:

```markdown
# LinguaFrame Private Demo Operations Report

- Overall: READY|ATTENTION|BLOCKED
- Generated at: <timestamp>

## Checks
- READY Access gate: ...
- ATTENTION OpenAI connectivity: ...

## Commands
- scripts/demo/private-demo-preflight.sh
```

Keep it metadata-only and match the existing clipboard/download patterns.

- [x] **Step 4: Style the workspace**

Use dense operational styling consistent with current panels. Avoid hero layout, decorative cards, or duplicated nested card surfaces.

- [x] **Step 5: Verify frontend slice**

Run:

```bash
cd frontend && npm run test:run -- linguaframeApi -t "private demo operations"
cd frontend && npm run test:run -- App -t "private demo operations"
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

## Task 3: Terminal Report Script And Docs

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/private-demo-operations-report.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/083-private-demo-operations-readiness-workspace.md`

- [x] **Step 1: Add demo client helpers**

Add reusable helpers:

- `get_private_demo_operations`
- `print_private_demo_operations_summary`
- `write_private_demo_operations_report`

Back them with Python JSON parsing as existing scripts do.

- [x] **Step 2: Add one-command report script**

Create:

```bash
scripts/demo/private-demo-operations-report.sh
```

Behavior:

- Fetch `/api/operator/private-demo/operations` with existing `BASE_URL` and demo-token support.
- Print concise terminal status.
- Write Markdown to `${LINGUAFRAME_PRIVATE_DEMO_OPERATIONS_REPORT_PATH:-/tmp/linguaframe-demo/private-demo-operations-report.md}`.
- Exit non-zero when `overallStatus=BLOCKED`; exit zero for `READY` and `ATTENTION`.

- [x] **Step 3: Test script behavior**

Extend `scripts/demo/test-linguaframe-demo-client.sh` with fixture JSON for `READY`, `ATTENTION`, and `BLOCKED` operations.

Assert the report is metadata-only and excludes token, API key, object key, `/Users/`, and provider payload markers.

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/private-demo-operations-report.sh scripts/demo/lib/linguaframe-demo.sh
```

- [x] **Step 4: Update docs and roadmap**

Document the browser panel and script in:

- `README.md`
- `docs/deployment/private-demo.md`
- `docs/agent/smoke-test-checklist.md`

Update roadmap/target state to mark the private-demo operations readiness workspace as implemented after validation.

- [x] **Step 5: Final validation**

Run:

```bash
mvn -pl LinguaFrame -Dtest=PrivateDemoOperationsServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- linguaframeApi -t "private demo operations"
cd frontend && npm run test:run -- App -t "private demo operations"
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/private-demo-operations-report.sh scripts/demo/lib/linguaframe-demo.sh
git diff --check
```

## Completion Criteria

- Browser users can open one `Private demo operations` panel and see whether the private demo is ready, needs attention, or is blocked.
- Terminal users can run one script to produce the same safe operations report before uploads or provider-backed runs.
- The feature uses existing readiness, live-check, dashboard, and retention services without mutating runtime state.
- Tests prove the API, UI, script, route docs, and safety filters.
- Documentation explains when to use the browser panel versus the terminal preflight and operations report.
