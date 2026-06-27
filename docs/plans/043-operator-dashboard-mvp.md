# Operator Dashboard MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser-visible operator dashboard that summarizes demo health, queue/job status, recent failures, model-call cost, and cache effectiveness without requiring terminal or database inspection.

**Architecture:** Add a read-only backend dashboard query boundary that aggregates existing durable tables without introducing authentication, billing, or admin mutation. Expose it through `GET /api/operator/dashboard`, then add a compact React dashboard panel that refreshes on demand and links failed or active jobs back into the existing job detail flow.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5, React + Vite + TypeScript, Vitest, Docker Compose config validation.

## Global Constraints

- This slice must be a complete feature: backend API, frontend UI, tests, docs, validation, commit, and merge back to `main`.
- Keep the endpoint read-only; do not add job mutation, retention execution, queue purge, user management, billing, or deployment automation.
- Reuse existing job/model-call/timeline/artifact tables; do not create new tables unless a test proves aggregation cannot be supported otherwise.
- Keep private-demo token behavior unchanged: `/api/operator/dashboard` is under `/api/**` and uses the existing demo access gate when configured.
- Keep the first screen as the working video localization interface, not a marketing or admin-only landing page.
- Branch name: `operator-dashboard-mvp`.

---

### Task 1: Backend Dashboard Query API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorDashboardVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorJobStatusCountVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorRecentFailureVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorModelCallSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OperatorCacheSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/OperatorDashboardService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/OperatorDashboardServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/repository/OperatorDashboardRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/repository/OperatorDashboardRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Interfaces:**
- Produces: `GET /api/operator/dashboard`
- Produces: `OperatorDashboardVo`
- Produces: status counts for all `LocalizationJobStatus` values.
- Produces: recent failures capped at 5.
- Produces: model-call totals: call count, failed call count, total latency, estimated cost.
- Produces: cache totals: artifact cache hits, provider cache hits, generated artifact count.

- [x] Write failing repository tests that seed jobs, model calls, artifacts, and cache-hit timeline events, then assert aggregate counts, costs, and recent failures.
- [x] Write failing controller tests proving `GET /api/operator/dashboard` returns the aggregate JSON shape and is protected by the existing demo gate when a token is configured.
- [x] Implement `OperatorDashboardRepository` with SQL grouped by job status, failed jobs ordered by `failed_at DESC`, model-call sums, and cache/event counts.
- [x] Implement `OperatorDashboardServiceImpl` as a thin read-only orchestration layer.
- [x] Implement `OperatorDashboardController`.
- [x] Run `mvn -pl LinguaFrame -Dtest='OperatorDashboardRepositoryTests,OperatorDashboardControllerTests,DemoAccessInterceptorTests' test`.

### Task 2: Frontend API And Dashboard Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify if needed: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /api/operator/dashboard`.
- Produces: `getOperatorDashboard(): Promise<OperatorDashboard>`.
- Produces: dashboard UI showing job status counts, recent failures, model-call cost summary, cache summary, and refresh.

- [x] Write failing API tests proving `getOperatorDashboard()` calls `/api/operator/dashboard` with the stored demo token header.
- [x] Write failing App tests proving the dashboard renders status counts, recent failures, cost, and cache metrics.
- [x] Add TypeScript types for `OperatorDashboard`, `OperatorJobStatusCount`, `OperatorRecentFailure`, `OperatorModelCallSummary`, and `OperatorCacheSummary`.
- [x] Add `getOperatorDashboard()` to `linguaFrameApi`.
- [x] Add a compact `Operator dashboard` panel above or near job history, with a manual refresh button and click-to-open for recent failed jobs.
- [x] Keep dashboard errors local to the panel so upload/job detail remains usable if the aggregate endpoint fails.
- [x] Run `cd frontend && npm run test:run -- linguaframeApi App`.

### Task 3: Docs, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/043-operator-dashboard-mvp.md`

**Interfaces:**
- Consumes: Tasks 1-2.
- Produces: verified feature branch merged back to `main`.

- [x] Document the operator dashboard as a read-only browser demo surface, not a full admin dashboard.
- [x] Mark the dashboard as implemented for demo observability while keeping public multi-user admin features out of scope.
- [x] Record the decision that dashboard metrics are computed read-only from existing durable tables instead of a new reporting schema.
- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='OperatorDashboardRepositoryTests,OperatorDashboardControllerTests,DemoAccessInterceptorTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [x] Commit as `Add operator dashboard`.
- [x] Merge `operator-dashboard-mvp` back to `main`.
- [x] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- linguaframeApi App`.
