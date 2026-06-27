# Retention Cleanup Panel MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser-visible operator panel for previewing and manually running retention cleanup so a private demo can manage stored jobs and artifacts without terminal-only curl commands.

**Architecture:** Reuse the existing backend endpoints `GET /api/retention/cleanup/preview` and `POST /api/retention/cleanup/run`. The frontend will add typed API helpers, a compact operator card, explicit confirmation before run, and tests around the safe preview-first workflow. Backend behavior remains the source of truth for disabled, dry-run, and delete semantics.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, Spring Boot, Java 21, JUnit 5.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Do not add automatic cleanup execution from the browser; only manual preview and manual run.
- Do not expose object keys, raw media paths, credentials, demo tokens, or OpenAI keys.
- Treat `dryRun: true` as preview/simulation copy and `dryRun: false` as a destructive run result.
- Keep repository docs and UI copy in English; discussion can stay Chinese.

---

## Task 1: Frontend Retention API Contract

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`

**Interfaces:**
- Consumes: `RetentionCleanupResultVo(dryRun, candidateJobCount, deletedJobCount, deletedVideoCount, deletedObjectCount, skippedObjectCount, failureCount)`
- Produces: `RetentionCleanupResult`, `getRetentionCleanupPreview()`, and `runRetentionCleanup()`

- [x] Add a `RetentionCleanupResult` TypeScript interface matching the backend record fields exactly.
- [x] Add `getRetentionCleanupPreview(): Promise<RetentionCleanupResult>` using `GET /api/retention/cleanup/preview`.
- [x] Add `runRetentionCleanup(): Promise<RetentionCleanupResult>` using `POST /api/retention/cleanup/run`.
- [x] Export both helpers through `linguaFrameApi`.
- [x] Add API tests that assert method, path, JSON parsing, and demo token header propagation.
- [x] Run: `cd frontend && npm run test:run -- linguaframeApi`.

## Task 2: Operator Cleanup Panel UI

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `getRetentionCleanupPreview()` and `runRetentionCleanup()`
- Produces: a visible `Retention cleanup` panel in the operator/dashboard area

- [x] Load cleanup preview with the existing dashboard/runtime readiness refresh path.
- [x] Render counts for candidates, deleted jobs, deleted videos, deleted objects, skipped objects, and failures.
- [x] Show mode copy from `dryRun`: `Dry run` for safe previews, `Delete mode` only when the backend reports non-dry-run results.
- [x] Add a `Preview cleanup` button that refreshes only the cleanup preview.
- [x] Add a `Run cleanup` button that opens `window.confirm` before calling `POST /api/retention/cleanup/run`.
- [x] Disable run while a request is pending and show a clear error message if preview or run fails.
- [x] Add tests for initial preview rendering, preview refresh, confirmation-cancel behavior, confirmed run behavior, and API failure copy.
- [x] Run: `cd frontend && npm run test:run -- App`.

## Task 3: Docs And Demo Guidance

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: documented operator workflow and verification evidence

- [x] Document that retention cleanup can be previewed and manually run from the browser operator panel.
- [x] Keep curl commands in README as the terminal fallback.
- [x] Add smoke-test steps for opening the panel, previewing cleanup, and confirming that default `.env.example` behavior remains dry-run/default-off.
- [x] Update product docs so retention actions are no longer described as absent from the operator dashboard.
- [x] Record the decision that browser cleanup remains manual and confirmation-gated.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.

## Task 4: Verification And Merge

**Files:**
- Modify: `docs/plans/047-retention-cleanup-panel-mvp.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi App`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='RetentionCleanupControllerTests,RuntimeDependencyControllerTests' test`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add retention cleanup operator panel`.
- [ ] Merge `retention-cleanup-panel-mvp` back to `main`.
- [ ] Run post-merge focused validation: `cd frontend && npm run test:run -- linguaframeApi App`.
