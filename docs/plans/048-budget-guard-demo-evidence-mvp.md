# Budget Guard Demo Evidence MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing per-job cost budget guard visible and demonstrable from the private demo workflow so OpenAI-backed runs can prove cost protection before spending on later stages.

**Architecture:** Extend the sanitized runtime readiness contract with budget configuration that is safe to expose, then render it in the React demo readiness panel. Add a Docker demo script that intentionally uses a tiny budget and non-zero local cost rates to prove the worker fails before a guarded provider stage. No billing, payment, user account, or provider-pricing automation is added.

**Tech Stack:** Spring Boot, Java 21, JUnit 5, React, TypeScript, Vitest, Bash, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Do not expose API keys, provider request bodies, uploaded media paths, storage credentials, or raw prompt/text payloads.
- Treat costs as local estimates only, not billing-source-of-truth values.
- Keep budget guard opt-in; `.env.example` must remain non-blocking by default.
- The demo script must fail only when the guard blocks a job; unexpected provider, upload, or timeout failures must exit with clear evidence.
- Plan and discussion can be Chinese, but repository docs and UI copy stay English.

---

## Task 1: Runtime Readiness Budget Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/BudgetReadinessVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/DemoReadinessVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Consumes: `LinguaFrameProperties.Cost`
- Produces: `readiness.budget.enabled`, `readiness.budget.maxJobCostUsd`, and `readiness.budget.estimatedCostTrackingEnabled`

- [x] Add `BudgetReadinessVo(boolean enabled, BigDecimal maxJobCostUsd, boolean estimatedCostTrackingEnabled)`.
- [x] Add `BudgetReadinessVo budget` to `DemoReadinessVo`.
- [x] Populate it from `properties.getCost().isBudgetGuardEnabled()`, `getMaxJobCostUsd()`, and `isEnabled()`.
- [x] Add controller assertions for default test values: budget guard disabled, max job cost `0`, estimated cost tracking enabled.
- [x] Extend the secret-leak test to ensure the readiness payload still excludes provider credentials and raw local paths.
- [x] Run: `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test`.

## Task 2: Browser Budget Readiness Display

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `RuntimeDependencySummary.readiness.budget`
- Produces: budget guard status in the `Demo readiness` panel

- [x] Add `BudgetReadiness` TypeScript interface with `enabled`, `maxJobCostUsd`, and `estimatedCostTrackingEnabled`.
- [x] Add `budget: BudgetReadiness` to `DemoReadiness`.
- [x] Render budget status in the existing readiness grid as `Budget guard` and `Cost limit`.
- [x] Use copy that clearly separates `Enabled`/`Disabled` guard state from local estimated-cost tracking.
- [x] Add an App test where budget guard is enabled and max job cost is `0.000001`.
- [x] Keep the upload form usable if runtime readiness loading fails.
- [x] Run: `cd frontend && npm run test:run -- App`.

## Task 3: Docker Budget Guard Demo Script

**Files:**
- Create: `scripts/demo/docker-e2e-budget-guard.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: local Docker backend with budget guard and non-zero cost rates
- Produces: repeatable terminal evidence that a guarded job fails before a later provider stage

- [x] Add a helper that prints a concise failure assertion from job JSON: status, failure stage, failure reason, model-call count, estimated cost, and timeline entries.
- [x] Add `docker-e2e-budget-guard.sh` that uploads the demo sample, waits for `FAILED`, and asserts `failureReason` contains `Job cost budget exceeded`.
- [x] The script must print the required environment variables: `LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true`, positive `LINGUAFRAME_COST_MAX_JOB_COST_USD`, and non-zero cost rates.
- [x] Use default sample creation through existing helper functions; allow `LINGUAFRAME_DEMO_SAMPLE_PATH` override.
- [x] Document the exact Docker command sequence for running the budget guard demo.
- [x] Add smoke checklist expectations for failure stage, reason, and absence of later provider calls.
- [x] Run: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-budget-guard.sh`.

## Task 4: Product Docs And Progress Records

**Files:**
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: product-visible evidence that budget control is demo-verifiable

- [x] Update Phase 8/9/12 docs to say budget guard is visible in readiness and has a repeatable Docker evidence script.
- [x] Keep real billing, payments, per-user budgets, and provider price automation in future scope.
- [x] Record the decision that budget readiness exposes only safe configuration, not secrets or provider prices.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.

## Task 5: Verification And Merge

**Files:**
- Modify: `docs/plans/048-budget-guard-demo-evidence-mvp.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run script syntax validation: `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-budget-guard.sh`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add budget guard demo evidence`.
- [ ] Merge `budget-guard-demo-evidence-mvp` back to `main`.
- [ ] Run post-merge focused validation: `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` and `cd frontend && npm run test:run -- App`.
