# Model Usage Ledger Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an operator-visible model usage ledger that summarizes recent model calls, cost, latency, cache savings, failure rate, and budget pressure across jobs for AI-infrastructure demo evidence.

**Architecture:** Build a read-only backend aggregate over existing model-call records, job summaries, quality/cache metadata, and owner quota configuration. Expose the ledger through an operator endpoint, render a browser panel near private-demo operations, and provide a terminal script that exports JSON and Markdown under `/tmp/linguaframe-demo/model-usage-ledger/`.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Spring JDBC/repositories already in the backend, React + Vite + TypeScript, shell demo scripts.

## Global Constraints

- The ledger is read-only and must not upload media, create jobs, retry/cancel jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- The ledger may include only safe metadata: job ids, video ids, operation/stage/provider/model names, prompt versions, counts, latency, estimated cost, cache-hit counts, failed-call counts, and safe error summaries.
- The ledger must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, corrected subtitle text, provider payloads, API keys, bearer tokens, demo tokens, and credentials.
- The feature must work when there are no model calls by returning an empty ledger with an explicit `EMPTY` status and next action.
- Existing job detail, diagnostics, operator dashboard, private-demo operations, and evidence-package behavior must remain backward compatible.

---

## Task 1: Backend Ledger Domain, Repository Query, And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/ModelUsageLedgerVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/ModelUsageLedgerSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/ModelUsageLedgerJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/ModelUsageLedgerOperationVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/ModelUsageLedgerCallVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/ModelUsageLedgerService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/ModelUsageLedgerServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/ModelUsageLedgerServiceTests.java`

**Interfaces:**
- `ModelUsageLedgerVo buildLedger(Integer limit)` returns an owner-scoped, metadata-only usage ledger.
- `String renderMarkdown(ModelUsageLedgerVo ledger)` returns Markdown beginning with `# Model Usage Ledger`.

- [ ] Query recent model calls and related job metadata using existing repositories or `JdbcClient`; default `limit` to 20 and clamp to `1..100`.
- [ ] Group calls by job id and by operation, preserving provider, model, prompt version, status, latency, cost, token/audio/character counts, and safe error summaries.
- [ ] Calculate summary fields: `ledgerStatus`, `jobCount`, `modelCallCount`, `failedModelCallCount`, `providerCacheHitCount`, `generatedArtifactCount`, `totalLatencyMs`, `estimatedCostUsd`, `averageLatencyMs`, `failureRatePercent`, and `recommendedNextAction`.
- [ ] Classify `ledgerStatus` as `EMPTY` when no calls exist, `READY` when calls exist and failure rate is 0, `ATTENTION` when failures exist but under 25%, and `BLOCKED` when failure rate is 25% or higher.
- [ ] Include safe links to job detail, diagnostics download, demo run package, and AI audit package for each job.
- [ ] Render Markdown sections: `Summary`, `Cost And Latency`, `Operations`, `Recent Jobs`, `Failure Notes`, `Safe Links`, and `Safety Notes`.
- [ ] Add service tests for empty ledger, ready ledger with successful calls, and blocked ledger with high failed-call rate.
- [ ] Run `mvn -pl LinguaFrame -Dtest=ModelUsageLedgerServiceTests test`.

## Task 2: Backend Operator Endpoint And Controller Tests

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorControllerTests.java`

**Interfaces:**
- `GET /api/operator/model-usage-ledger?limit=20` returns `ModelUsageLedgerVo`.
- `GET /api/operator/model-usage-ledger/markdown/download?limit=20` returns `model-usage-ledger.md`.

- [ ] Add constructor injection for `ModelUsageLedgerService`.
- [ ] Add JSON endpoint with demo-token behavior inherited from existing `/api/operator/**` endpoints.
- [ ] Add Markdown download endpoint with `text/markdown;charset=UTF-8` and attachment filename `model-usage-ledger.md`.
- [ ] Add controller tests for populated JSON, empty JSON, Markdown download, limit clamping, safe links, and absence of raw paths/tokens/provider payloads.
- [ ] Run `mvn -pl LinguaFrame -Dtest=OperatorControllerTests#returnsModelUsageLedger+returnsEmptyModelUsageLedger+downloadsModelUsageLedgerMarkdown test`.

## Task 3: Frontend API And Operator Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- `getModelUsageLedger(limit?: number): Promise<ModelUsageLedger>`.
- `modelUsageLedgerMarkdownDownloadUrl(limit?: number): string`.

- [ ] Add TypeScript types for ledger summary, job rows, operation rows, and recent call rows.
- [ ] Add API function and Markdown URL builder with encoded query params.
- [ ] Add API tests for default limit, custom limit, and Markdown URL generation.
- [ ] Add an operator `Model usage ledger` panel near private-demo operations/evidence gallery.
- [ ] Render ledger status, total cost, model-call count, failed-call count, cache-hit count, failure rate, average latency, operation breakdown, recent job rows, and safe package links.
- [ ] Add refresh and Markdown download controls.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [ ] Run `npm run build`.

## Task 4: Terminal Script, README, And Progress Log

**Files:**
- Create: `scripts/demo/model-usage-ledger.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/model-usage-ledger/`.
- Optional limit env: `LINGUAFRAME_MODEL_USAGE_LEDGER_LIMIT`.
- Script output keys: `modelUsageLedgerStatus`, `modelUsageLedgerJobCount`, `modelUsageLedgerCallCount`, `modelUsageLedgerFailedCallCount`, `modelUsageLedgerEstimatedCostUsd`, `modelUsageLedgerJsonPath`, and `modelUsageLedgerMarkdownPath`.

- [ ] Implement the script with `demo_curl`, `python3`, JSON export, Markdown export, and summary printing.
- [ ] Document when to use model usage ledger versus job diagnostics, AI audit package, operator dashboard, private-demo operations, and closure package.
- [ ] Document empty-ledger behavior for deterministic no-cost runs and populated-ledger behavior for OpenAI smoke/full demo runs.
- [ ] Record focused validation commands and outcomes in `docs/progress/execution-log.md`.
- [ ] Run `bash -n scripts/demo/model-usage-ledger.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/124-model-usage-ledger-workspace.md`

- [ ] Mark this plan checklist complete after implementation.
- [ ] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=ModelUsageLedgerServiceTests,OperatorControllerTests#returnsModelUsageLedger+returnsEmptyModelUsageLedger+downloadsModelUsageLedgerMarkdown test`
- [ ] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/model-usage-ledger.sh scripts/demo/lib/linguaframe-demo.sh`
- [ ] Run `git diff --check`.
- [ ] Commit as `Add model usage ledger workspace`.
- [ ] Merge the feature branch back to `main` after validation passes.
