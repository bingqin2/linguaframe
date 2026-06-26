# Per-Job Cost Budget Guard MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## Goal

Add a conservative per-job cost budget guard that can stop later AI stages before a provider call when a job's already recorded estimated spend reaches a configured limit. This protects real OpenAI demo runs without changing the default cost-free local workflow.

## Architecture

The existing `ModelCallAuditService` already computes `JobUsageSummaryVo.estimatedCostUsd` from durable model-call records. This feature adds a small guard service that reads that summary before expensive stages run. If disabled, unset, or under budget, execution continues. If enabled and the current job cost is greater than or equal to the configured limit, the stage throws a safe domain exception. `LocalizationJobExecutionServiceImpl` already marks stage exceptions as failed job timeline events, so the failure becomes visible in job detail without creating a fake model-call record.

Guarded stages:

- `TRANSCRIPT_SUBTITLE_EXPORT` before transcription provider calls.
- `TARGET_SUBTITLE_EXPORT` before translation provider calls.
- `DUBBING_AUDIO_GENERATION` before TTS provider calls.
- `TRANSLATION_QUALITY_EVALUATION` before quality evaluation provider calls.

Non-AI media stages, artifact summary, and disabled provider stages are not guarded.

## Tech Stack

- Java 21, Spring Boot, JUnit 5, AssertJ.
- Existing Maven module: `LinguaFrame`.
- Existing cost/audit types under `com.linguaframe.job`.
- Existing docs: `README.md`, `.env.example`, `docker-compose.yml`, `application.yaml`, `docs/progress/execution-log.md`, `docs/progress/decisions.md`.

## Global Constraints

- Default behavior must remain unchanged: budget guard is off by default.
- Do not log, print, or document any real OpenAI key.
- Treat `estimatedCostUsd` as a local estimate, not billing-source-of-truth data.
- The MVP checks current recorded cost only; it does not forecast the next call cost.
- A blocked stage must not call the provider and must not create a new model-call record.

---

## Task 1: Add Budget Configuration

- [x] Extend `LinguaFrameProperties.Cost` with:
  - `private boolean budgetGuardEnabled = false;`
  - `@DecimalMin("0.0") private BigDecimal maxJobCostUsd = BigDecimal.ZERO;`
  - getters/setters.
- [x] Update `LinguaFrame/src/main/resources/application.yaml`:
  - `linguaframe.cost.budget-guard-enabled: ${LINGUAFRAME_COST_BUDGET_GUARD_ENABLED:false}`
  - `linguaframe.cost.max-job-cost-usd: ${LINGUAFRAME_COST_MAX_JOB_COST_USD:0}`
- [x] Update `.env.example` and `docker-compose.yml` with the same two env vars.
- [x] Update `LinguaFramePropertiesTests`:
  - defaults are `budgetGuardEnabled=false`, `maxJobCostUsd=0`.
  - binding test accepts `linguaframe.cost.budget-guard-enabled=true` and `linguaframe.cost.max-job-cost-usd=0.01`.

### Red-Green Verification

- [x] First add/adjust the property tests and confirm they fail before implementation.
- [x] Implement config binding and confirm the property tests pass.

---

## Task 2: Add Guard Service and Exception

- [x] Add `CostBudgetExceededException` under `LinguaFrame/src/main/java/com/linguaframe/job/domain/exception/`.
  - Message format: `Job cost budget exceeded before <stage>: current estimated cost <current> USD, limit <limit> USD.`
  - Keep only safe operational data in the message.
- [x] Add `CostBudgetGuardService` under `LinguaFrame/src/main/java/com/linguaframe/job/service/`:
  - `void assertWithinBudget(String jobId, LocalizationJobStage stage);`
- [x] Add `CostBudgetGuardServiceImpl` under `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/`.
  - Inject `LinguaFrameProperties` and `ModelCallAuditService`.
  - Return immediately when guard disabled.
  - Return when `maxJobCostUsd <= 0`.
  - Compare `modelCallAuditService.summarizeJob(jobId).estimatedCostUsd()` with `maxJobCostUsd`.
  - Throw `CostBudgetExceededException` when current cost is greater than or equal to the limit.
- [x] Add focused unit tests in `LinguaFrame/src/test/java/com/linguaframe/job/service/CostBudgetGuardServiceTests.java`:
  - disabled guard allows even over-budget jobs.
  - zero limit means no limit.
  - under-budget job is allowed.
  - exactly-at-limit and over-limit jobs throw the safe exception.

### Red-Green Verification

- [x] Write `CostBudgetGuardServiceTests` first against the planned API.
- [x] Implement service and exception until these tests pass.

---

## Task 3: Guard Expensive Pipeline Stages

- [x] Inject `CostBudgetGuardService` into:
  - `TranscriptSubtitleExportPipelineStage`
  - `TargetSubtitleExportPipelineStage`
  - `DubbingAudioGenerationPipelineStage`
  - `QualityEvaluationPipelineStage`
- [x] Call `costBudgetGuardService.assertWithinBudget(jobId, stage())` after the stage-specific enabled check and after required input lookup that does not call AI, but before provider/service methods that trigger model calls.
- [x] Keep disabled stages as no-ops; a disabled transcription/translation/TTS/evaluation stage must not invoke the guard.

### Test Coverage

- [x] Update existing stage tests where present:
  - `DubbingAudioGenerationPipelineStageTests`: over-budget guard throws and `RecordingTtsProvider.request` remains `null`.
  - Add or update tests for transcript, translation, and quality evaluation stages so blocked stages do not call their providers/services.
- [x] Add an execution-level test in `LocalizationJobExecutionServiceTests` or a focused stage test proving the thrown exception is surfaced as a failed job/timeline event with the budget message.

### Red-Green Verification

- [x] Add failing tests that assert providers are not called when the guard blocks.
- [x] Wire the guard into stages and confirm tests pass.

---

## Task 4: Document Runtime Usage

- [x] Update `README.md` cost configuration section:
  - Explain `LINGUAFRAME_COST_BUDGET_GUARD_ENABLED`.
  - Explain `LINGUAFRAME_COST_MAX_JOB_COST_USD`.
  - Include a safe example for testing the guard:

```env
LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true
LINGUAFRAME_COST_MAX_JOB_COST_USD=0.000001
```

- [x] Document expected behavior:
  - The guard checks accumulated estimated cost before the next AI stage.
  - The job fails at the guarded stage instead of making the next provider call.
  - `usageSummary` and `timelineEvents` show what happened.
- [x] Update `docs/product/spec.md` or `docs/product/roadmap.md` to move per-job budget guard from planned concept to implemented MVP.
- [x] Add a decision to `docs/progress/decisions.md` explaining why the MVP uses recorded-cost threshold checks rather than next-call forecasting.
- [x] Add validation results to `docs/progress/execution-log.md`.

---

## Task 5: Validate End to End

- [x] Run focused backend tests:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,CostBudgetGuardServiceTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test
```

- [x] Run full backend tests:

```bash
mvn -pl LinguaFrame test
```

- [x] Verify Docker env rendering:

```bash
docker compose --env-file .env.example config
```

- [ ] Optional manual budget-block demo after user supplies `.env`:

```bash
docker compose --env-file .env up -d --force-recreate linguaframe-backend
scripts/demo/docker-e2e-success.sh
```

Expected manual result with a deliberately tiny positive budget and non-zero cost rates: the job reaches `FAILED` at the first AI stage after the accumulated estimate reaches the configured max, and no later provider call appears in `modelCalls`.

---

## Completion Criteria

- [x] The feature is implemented on branch `cost-budget-guard-mvp`.
- [x] Default `.env.example` demo behavior remains unchanged.
- [x] A configured tiny budget can prevent additional AI provider calls.
- [x] Backend tests pass.
- [x] Docker config includes the new env vars.
- [x] Docs explain how to enable and validate the guard.
- [ ] Branch is merged back into `main` after verification.
