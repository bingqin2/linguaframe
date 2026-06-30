# Private Demo Delivery Receipt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only private demo delivery receipt that proves what the demo session delivered, which run should be shown, where evidence packages live, and which next actions are safe after a completed private demo.

**Architecture:** Compose existing operator surfaces instead of creating new readiness rules. The receipt should summarize operations readiness, launch rehearsal, evidence gallery, run archive, command center, session evidence package, final proof bundle links, reviewer workspace, handoff portal, and OpenAI readiness evidence in one operator endpoint, browser panel, and terminal export.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, ZIP streams, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, CLI script, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, upload APIs, retry/cancel APIs, dispatch queues, object-storage write paths, or database mutation paths.
- Do not embed media bytes, raw transcript text, raw subtitle text, corrected draft text, reviewer notes, object keys, local paths, provider payloads, tokens, API keys, or credentials.
- Reuse existing read-only services and routes for private demo operations, launch rehearsal, evidence gallery, run archive, command center, session evidence package, recovery board, OpenAI readiness evidence, reviewer workspace, handoff portal, and final proof artifacts.
- The receipt must work when there is no selected job by falling back to the run archive recommended job.

---

## Feature Scope

The project already has many demo proof surfaces, but an operator still has to know which ones to open at the end of a private demo. This slice adds the final session-level receipt.

Implement:

- Backend receipt VO/service/controller endpoints.
- Markdown and ZIP export for the receipt.
- Frontend receipt panel near the existing private demo archive and command center.
- Terminal script that exports JSON, Markdown, and ZIP receipt artifacts.
- Focused backend, frontend, script, and docs validation.

Out of scope:

- New media processing.
- New narration authoring.
- New provider integration.
- New authentication model.
- Changing existing proof bundle semantics.

## Backend Design

Add `PrivateDemoDeliveryReceiptService` and implementation under `com.linguaframe.operator.service`.

The service should expose:

- `receipt(String jobId)`.
- `receiptMarkdown(String jobId)`.
- `openPackage(String jobId)` returning a ZIP BO similar to the session evidence package BO.

Add VO records under `com.linguaframe.operator.domain.vo`:

- `PrivateDemoDeliveryReceiptVo`
- `PrivateDemoDeliveryReceiptCheckVo`
- `PrivateDemoDeliveryReceiptSectionVo`
- `PrivateDemoDeliveryReceiptLinkVo`
- `PrivateDemoDeliveryReceiptPackageEntryVo`
- `PrivateDemoDeliveryReceiptActionVo`

Receipt fields should include:

- `generatedAt`
- `overallStatus`: `READY`, `ATTENTION`, `BLOCKED`, or `EMPTY`
- `selectedJobId`
- `recommendedJobId`
- `recommendedVideoId`
- `recommendedReadiness`
- readiness summaries for operations, launch, gallery, archive, command center, recovery board, model usage ledger, OpenAI readiness, reviewer workspace, handoff portal, and final proof links
- safe actions and safe evidence links
- package entries for JSON, Markdown, ZIP, session evidence package, reviewer workspace, handoff portal, evidence closure, AI audit package, and OpenAI proof Markdown
- `receiptNotesMarkdown`

Add controller endpoints to `OperatorDashboardController`:

- `GET /api/operator/private-demo/delivery-receipt`
- `GET /api/operator/private-demo/delivery-receipt/markdown/download`
- `GET /api/operator/private-demo/delivery-receipt/download`

The ZIP should include metadata-only files:

- `manifest.json`
- `private-demo-delivery-receipt.json`
- `private-demo-delivery-receipt.md`
- `run-archive.json`
- `command-center.json`
- `README.md`

Do not nest existing ZIP packages inside the receipt ZIP. Link them by route.

## Frontend Design

Extend `frontend/src/domain/jobTypes.ts` and `frontend/src/api/linguaframeApi.ts` with delivery receipt types and API functions:

- `getPrivateDemoDeliveryReceipt(jobId?: string)`
- `downloadPrivateDemoDeliveryReceiptMarkdown(jobId?: string)`
- `downloadPrivateDemoDeliveryReceiptZip(jobId?: string)`

Add state/loading/error handling in `frontend/src/App.tsx`.

Add a compact `PrivateDemoDeliveryReceiptPanel` near the private demo evidence gallery, run archive, and command center. It should show:

- Overall receipt status.
- Selected or recommended job.
- Delivery checks.
- Safe next actions.
- Evidence links.
- Download buttons for Markdown and ZIP.

Keep the layout dense and workbench-like, following the existing operator dashboard style.

## Terminal Script Design

Add `scripts/demo/private-demo-delivery-receipt.sh`.

The script should:

- Accept optional `LINGUAFRAME_DEMO_JOB_ID`.
- Write JSON to `/tmp/linguaframe-demo/private-demo-delivery-receipt/private-demo-delivery-receipt.json` by default.
- Write Markdown to the same directory.
- Write ZIP to the same directory.
- Print a concise summary with status, selected job, recommended job, and output paths.
- Exit non-zero only when receipt status is `BLOCKED`, unless report-only mode is enabled.

Extend `scripts/demo/lib/linguaframe-demo.sh` with narrowly scoped helpers only if existing helpers are missing.

## Testing

Backend focused tests:

- Add `PrivateDemoDeliveryReceiptServiceTests`.
- Extend `OperatorDashboardControllerTests`.
- Assert the service falls back to the archive recommended job when no job is selected.
- Assert `ATTENTION` is used for partial evidence and `BLOCKED` for blocked critical readiness.
- Assert ZIP entries are metadata-only and do not include nested ZIP binaries.
- Assert safe links include session evidence package, reviewer workspace, handoff portal, evidence closure, OpenAI smoke proof Markdown, and AI audit package.

Frontend focused tests:

- Extend `frontend/src/api/linguaframeApi.test.ts` for JSON, Markdown, ZIP, and jobId query encoding.
- Extend `frontend/src/App.test.tsx` to assert the receipt panel renders status, recommended job, links, and download buttons.

Script tests:

- Run `bash -n` on the new script and changed helper script.
- Extend `scripts/demo/test-linguaframe-demo-client.sh` when helper behavior is added.

## Documentation

Update:

- `README.md` demo operator flow.
- `scripts/demo/README.md` receipt export command.
- `docs/agent/docker-e2e-demo.md` end-of-demo receipt evidence.
- `docs/agent/smoke-test-checklist.md` browser and terminal checks.
- `docs/deployment/private-demo.md` operator handoff flow.
- `docs/product/roadmap.md` private demo milestone status.
- `docs/product/target-state.md` demo delivery expectation.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=PrivateDemoDeliveryReceiptServiceTests,OperatorDashboardControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "private demo delivery receipt|private demo run archive|demo session command center"`
- `bash -n scripts/demo/private-demo-delivery-receipt.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- Operator API exposes JSON, Markdown, and ZIP receipt exports.
- Browser dashboard shows a private demo delivery receipt with status, recommended run, checks, actions, links, and downloads.
- CLI exports receipt JSON, Markdown, and ZIP artifacts for the selected or recommended job.
- Receipt package is metadata-only and links to existing artifacts instead of nesting binaries.
- All changed backend, frontend, script, and docs surfaces are covered by focused tests and full validation.

