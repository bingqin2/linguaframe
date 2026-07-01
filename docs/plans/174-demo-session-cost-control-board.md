# Demo Session Cost Control Board Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a session-level cost control board that turns model usage, budget guard settings, owner daily spend, and recent failures into one operator-ready cost safety surface for private demos.

**Architecture:** Reuse existing model-call records, `ModelUsageLedgerService`, runtime budget readiness, and owner quota preflight data. Add a read-only operator service and VO for cost posture, then surface it through backend JSON/Markdown, the browser command center, the session evidence package, and demo scripts.

**Tech Stack:** Java 21, Spring Boot, JdbcClient, JUnit 5, React + TypeScript + Vite, Bash/Python demo helpers.

## Global Constraints

- Keep the slice metadata-only: no uploads, provider calls, FFmpeg execution, object-storage writes, budget mutation, or cleanup actions.
- Do not expose API keys, tokens, provider payloads, prompts, raw transcripts/subtitles, local media paths, object keys, or media bytes.
- The board must clearly distinguish local estimated cost from provider billing source of truth.
- A `BLOCKED` board must block the demo session command center; an `ATTENTION` board must surface as command-center review work.
- This is one complete feature slice: backend, API, browser UI, CLI, ZIP export, docs, tests, validation, commit, and merge back to `main`.

---

## Feature Scope

Implement a new `Demo session cost control board` for the private-demo operator.

It should answer:

- Is estimated cost tracking enabled?
- Are per-job and daily budget guards enabled?
- What is the current owner-scoped same-day estimated spend?
- What is the recent session estimated spend and failed model-call rate?
- Which recent jobs or operations are driving spend or risk?
- What safe next action should the operator take before running another demo?

Out of scope:

- Real provider billing reconciliation.
- Editing `.env` or budget settings from the UI.
- New pricing tables.
- Automatic cancellation, cleanup, or provider calls.

## Backend Design

Create these VOs under `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/`:

- `DemoSessionCostControlBoardVo`
- `DemoSessionCostControlSummaryVo`
- `DemoSessionCostControlBudgetVo`
- `DemoSessionCostControlJobVo`
- `DemoSessionCostControlOperationVo`
- `DemoSessionCostControlCheckVo`
- `DemoSessionCostControlActionVo`
- `DemoSessionCostControlLinkVo`

Create service interface and implementation:

- `DemoSessionCostControlBoardService`
- `DemoSessionCostControlBoardServiceImpl`

The implementation should compose:

- `ModelUsageLedgerService.ledger(limit)` for recent cost/failure data.
- `RuntimeDependencySummaryService.summary()` or equivalent existing runtime readiness source for budget guard configuration.
- `OwnerQuotaPreflightService.preflight()` for owner same-day estimated spend and quota guard status.

Status rules:

- `EMPTY`: no recent model calls and cost tracking is enabled.
- `READY`: budget posture is configured acceptably, recent failures are below attention threshold, and recent estimated spend is below configured limits.
- `ATTENTION`: cost tracking is disabled, budget guards are disabled, recent spend is non-zero but under limit, or failure rate requires review.
- `BLOCKED`: model usage ledger is blocked, owner quota preflight is blocked by daily cost, recent estimated spend is at/above a configured enabled daily limit, or per-job budget settings are invalid for paid demo mode.

Backend routes in `OperatorDashboardController`:

- `GET /api/operator/demo-session-cost-control-board`
- `GET /api/operator/demo-session-cost-control-board/markdown/download`

## Command Center & Evidence Package Integration

Extend `DemoSessionCommandCenterVo` with:

- `costControlStatus`
- `costControlRecentEstimatedCostUsd`
- `costControlDailyEstimatedCostUsd`
- `costControlFailedModelCallCount`
- `costControlRecommendedNextAction`
- `costControlPrimaryAction`
- `costControlLinks`

Update `DemoSessionCommandCenterServiceImpl`:

- Add a `cost-control` phase.
- Treat `BLOCKED` cost control as command-center `BLOCKED`.
- Treat `ATTENTION` cost control as `NEEDS_REVIEW` when no phase is blocked.
- Add cost-control links to command-center evidence links.
- Add a `## Cost Control` section to command-center Markdown.

Update `DemoSessionEvidencePackageServiceImpl`:

- Add `cost-control-board.json`.
- Add `cost-control-board.md`.
- Include both entries in the manifest and package README.

## Frontend Design

Add TypeScript interfaces in `frontend/src/domain/jobTypes.ts` for the new board and command-center fields.

Add API helpers in `frontend/src/api/linguaframeApi.ts`:

- `getDemoSessionCostControlBoard()`
- `downloadDemoSessionCostControlBoardMarkdown()`

Add a browser panel in `frontend/src/App.tsx`:

- Title: `Demo session cost control`
- Show status, tracking state, per-job guard, daily guard, owner daily spend, recent spend, failed calls, failure rate, top jobs, top operations, checks, primary action, and Markdown download.
- Match existing operator panels: dense, metadata-focused, no decorative landing layout.

Extend `DemoSessionCommandCenterPanel`:

- Add a compact `Cost control` summary region with status, recent cost, daily owner spend, failed calls, next action, and primary link.

## CLI Design

Create:

- `scripts/demo/demo-session-cost-control-board.sh`

Extend `scripts/demo/lib/linguaframe-demo.sh` with a summary printer:

- `demoSessionCostControlStatus`
- `demoSessionCostControlRecentEstimatedCostUsd`
- `demoSessionCostControlDailyEstimatedCostUsd`
- `demoSessionCostControlFailedModelCallCount`
- `demoSessionCostControlNextAction`
- `demoSessionCostControlMarkdownPath`

Extend existing command-center and evidence-package helpers:

- Print `demoSessionCommandCenterCostControl*` lines.
- Detect `cost-control-board.json` and `cost-control-board.md` in the session ZIP.

## Tests

Backend:

- Add `DemoSessionCostControlBoardServiceTests`.
- Cover empty, ready, attention, and blocked cost postures.
- Verify Markdown excludes unsafe values.
- Extend `DemoSessionCommandCenterServiceTests` for blocked and attention cost-control phases.
- Extend `DemoSessionEvidencePackageServiceTests` for cost-control ZIP entries.
- Extend `OperatorDashboardControllerTests` for new JSON/Markdown routes.
- Update any constructor fixtures affected by new command-center fields.

Frontend:

- Extend `frontend/src/api/linguaframeApi.test.ts` for new helper and fixture shape.
- Extend `frontend/src/App.test.tsx` for the new cost-control panel and command-center summary.

CLI:

- Extend `scripts/demo/test-linguaframe-demo-client.sh`.
- Assert the new script summary lines.
- Assert command-center cost-control lines.
- Assert evidence-package ZIP detection.
- Assert unsafe fixture data is rejected or omitted from generated summaries.

## Documentation

Update:

- `README.md` with cost-control board usage and how it differs from model usage ledger.
- `scripts/demo/README.md` with script examples.
- `docs/agent/smoke-test-checklist.md` with backend/frontend/CLI checks.
- `docs/product/roadmap.md` to mark `Cost summary` as implemented through the board.
- `docs/product/target-state.md` private-demo validation target.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoSessionCostControlBoardServiceTests,DemoSessionCommandCenterServiceTests,DemoSessionEvidencePackageServiceTests,OperatorDashboardControllerTests test`
- `npm --prefix frontend test -- --run src/App.test.tsx src/api/linguaframeApi.test.ts --testNamePattern "cost control|demo session command center|session evidence package"`
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/demo-session-cost-control-board.sh scripts/demo/demo-session-command-center.sh scripts/demo/demo-session-evidence-package.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A private-demo operator can see session-level cost posture without opening per-job details.
- Disabled or risky budget posture is visible before another paid run.
- Cost-control `BLOCKED` and `ATTENTION` states affect the top-level command center.
- Session evidence package includes cost-control JSON and Markdown.
- Browser, backend, CLI, ZIP, docs, and tests expose the same metadata-only feature.
- The feature branch is committed and merged back to `main` after verification.
