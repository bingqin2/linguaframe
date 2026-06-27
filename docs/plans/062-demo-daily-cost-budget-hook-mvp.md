# Demo Daily Cost Budget Hook MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in daily AI cost budget hook for the private demo so OpenAI-backed runs can be stopped before the next paid provider call once the configured daily spend estimate is reached.

**Architecture:** Extend the existing per-job `CostBudgetGuardService` rather than introducing real billing. New model-call records receive a safe configured budget identity, repository queries sum estimated cost for that identity since the current UTC day start, and guarded AI stages reuse the same pre-provider check path. Runtime readiness, README guidance, and a Docker evidence script expose only safe configuration and behavior.

**Tech Stack:** Java 21, Spring Boot, JDBC/Flyway, JUnit 5, H2 test database, Bash demo scripts, React readiness panel contract.

## Global Constraints

- Keep this feature as a demo/operator budget hook; do not build payments, real billing, provider price automation, or user account management.
- Never derive the budget identity from raw demo tokens, IP addresses, uploaded media paths, or provider payloads.
- Default behavior must remain unchanged: daily budget guard disabled unless explicitly configured.
- Budget checks must happen before translation, evaluation, and TTS provider calls through the existing stage guard path.
- All documentation must avoid real OpenAI keys, demo tokens, object storage credentials, raw transcript text, and raw local media paths.

---

### Task 1: Persist Safe Budget Identity On Model Calls

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V18__add_model_call_budget_identity.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/ModelCallRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ModelCallVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/ModelCallRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/ModelCallRepositoryTests.java`
- Test: existing constructor call sites in repository/service/operator tests.

**Interfaces:**
- Produces: `LinguaFrameProperties.Cost#getBudgetIdentity()` and `ModelCallRepository#sumEstimatedCostByBudgetIdentitySince(String budgetIdentity, Instant since)`.
- Produces: `budgetIdentity` as a non-secret field on `ModelCallRecord` and `ModelCallVo`.

- [ ] Add Flyway migration:

```sql
ALTER TABLE model_call_records
    ADD COLUMN budget_identity VARCHAR(128) NOT NULL DEFAULT 'demo-owner';

CREATE INDEX idx_model_call_records_budget_identity_created
    ON model_call_records(budget_identity, created_at);
```

- [ ] Add cost properties:

```java
private boolean dailyBudgetGuardEnabled = false;

@DecimalMin("0.0")
private BigDecimal maxDailyCostUsd = BigDecimal.ZERO;

@NotBlank
private String budgetIdentity = "demo-owner";
```

- [ ] Update model-call record/VO constructors, repository insert/select/map code, and audit creation so every new record stores `properties.getCost().getBudgetIdentity()`.

- [ ] Add repository sum query:

```java
public BigDecimal sumEstimatedCostByBudgetIdentitySince(String budgetIdentity, Instant since) {
    BigDecimal value = jdbcClient.sql("""
                    SELECT COALESCE(SUM(estimated_cost_usd), 0)
                    FROM model_call_records
                    WHERE budget_identity = :budgetIdentity
                      AND created_at >= :since
                    """)
            .param("budgetIdentity", budgetIdentity)
            .param("since", Timestamp.from(since))
            .query(BigDecimal.class)
            .single();
    return value.setScale(8, RoundingMode.HALF_UP);
}
```

- [ ] Repository test: save two records for `demo-owner`, one older record before `since`, and one different identity; assert the sum includes only matching current-day rows.

### Task 2: Enforce Daily Budget Through Existing Guard

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/ModelCallAuditService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/CostBudgetGuardServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/CostBudgetGuardServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/CostBudgetedPipelineStageTests.java`

**Interfaces:**
- Consumes: `ModelCallRepository#sumEstimatedCostByBudgetIdentitySince`.
- Produces: `ModelCallAuditService#summarizeDailyBudget(String budgetIdentity, Instant since)`.

- [ ] Extend `ModelCallAuditService` with:

```java
BigDecimal summarizeDailyBudget(String budgetIdentity, Instant since);
```

- [ ] Implement it in `ModelCallAuditServiceImpl` by delegating to the repository and returning scale `8`.

- [ ] Inject `Clock` into `CostBudgetGuardServiceImpl` with a production constructor using `Clock.systemUTC()` and a test constructor accepting a fixed clock.

- [ ] In `assertWithinBudget`, keep the current per-job check unchanged, then check daily budget only when `dailyBudgetGuardEnabled=true` and `maxDailyCostUsd > 0`.

- [ ] Compute the UTC day start from the injected clock:

```java
Instant since = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
```

- [ ] Throw `CostBudgetExceededException` before the provider call when current daily estimated cost is `>= maxDailyCostUsd`. The message must include stage, current cost, limit, and UTC date, but not tokens, IPs, file paths, provider payloads, or secrets.

- [ ] Unit tests:
  - daily guard disabled allows over-budget daily cost.
  - daily zero limit allows over-budget daily cost.
  - under daily budget allows the stage.
  - exactly at daily limit throws before provider call.
  - per-job guard still throws independently when daily guard is disabled.

### Task 3: Surface Safe Runtime Readiness And Frontend Contract

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/BudgetReadinessVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces safe readiness fields: `dailyBudgetGuardEnabled`, `maxDailyCostUsd`, and `budgetIdentity`.

- [ ] Extend `BudgetReadinessVo` to include the daily guard fields.
- [ ] Populate the fields from `LinguaFrameProperties.Cost`.
- [ ] Update runtime controller tests to assert the daily guard defaults are present and safe.
- [ ] Update the React `Demo readiness` budget row to show both per-job and daily limits.
- [ ] Frontend test: render runtime readiness with daily budget enabled and assert the panel shows the daily limit without exposing any secret-like value.

### Task 4: Add Docker Evidence Script And Documentation

**Files:**
- Create: `scripts/demo/docker-e2e-daily-budget-guard.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: existing `scripts/demo/lib/linguaframe-demo.sh` helpers.
- Produces: repeatable terminal evidence under `/tmp/linguaframe-demo/daily-budget-guard/`.

- [ ] Add script that:
  - requires `curl` and `python3`;
  - prints required env settings;
  - uploads the sample once with non-zero local rates to create daily spend;
  - uploads a second compatible job;
  - waits for `FAILED`;
  - asserts `failureReason` contains `Daily cost budget exceeded`;
  - downloads diagnostics JSON for the failed job.

- [ ] Document `.env` settings:

```bash
LINGUAFRAME_COST_ENABLED=true
LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true
LINGUAFRAME_COST_MAX_JOB_COST_USD=1
LINGUAFRAME_COST_DAILY_BUDGET_GUARD_ENABLED=true
LINGUAFRAME_COST_MAX_DAILY_COST_USD=0.000001
LINGUAFRAME_COST_BUDGET_IDENTITY=demo-owner
```

- [ ] Mark the roadmap item `Per-user daily cost budget hook` as implemented for the private-demo identity hook, while keeping real per-user budgets and billing in “Do not build yet.”

### Task 5: Verification And Integration

**Files:**
- No new source files unless tests expose a missing fixture.

- [ ] Run focused backend tests:

```bash
mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,CostBudgetGuardServiceTests,RuntimeDependencyControllerTests test
```

- [ ] Run full backend tests:

```bash
mvn -pl LinguaFrame test
```

- [ ] Run frontend tests and build:

```bash
cd frontend
npm run test:run -- App
npm run build
```

- [ ] Validate scripts and Compose config:

```bash
bash -n scripts/demo/docker-e2e-budget-guard.sh scripts/demo/docker-e2e-daily-budget-guard.sh
docker compose --env-file .env.example config --quiet
git diff --check
```

- [ ] Commit the feature on a branch named `codex-demo-daily-cost-budget-hook-mvp`, merge it back to `main` after verification, and record post-merge verification in `docs/progress/execution-log.md`.
