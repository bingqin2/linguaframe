# Model-Call Audit Cost MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist AI provider call records and expose a per-job usage/cost summary through the job detail API and Docker demo output.

**Architecture:** Add a `model_call_records` persistence boundary, then route demo and OpenAI transcription, translation, and TTS providers through `ModelCallAuditService`. `LocalizationJobQueryService` reads the audit records and attaches `usageSummary` plus `modelCalls` to `GET /api/jobs/{jobId}`.

**Tech Stack:** Java 21, Spring Boot 3.5.15, JDBC/Flyway, JUnit 5, Maven, Bash demo scripts, Docker Compose.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `model-call-audit-cost-mvp`.
- Store cost values as estimates, never as billing-source-of-truth values.
- Default all cost rates to `0` so the demo is reproducible and not tied to stale provider pricing.
- Keep provider raw response bodies, API keys, and object storage credentials out of logs and audit records.
- Do not add frontend UI, authentication, Redis behavior, budget enforcement, quality evaluation, prompt-template storage, or duplicate-work caching in this slice.
- Do not change existing artifact download routes.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- A new `model_call_records` table stores one row per transcription, translation, or TTS provider call.
- Records include job id, stage, operation, provider, model, prompt version, latency, usage units, estimated cost, status, safe error summary, and timestamp.
- Demo providers record successful zero-cost calls, making the default Docker demo visibly auditable without OpenAI keys.
- OpenAI providers record success and failure with safe summaries and the configured model name.
- Translation records capture usage tokens when the Responses API response contains `usage.input_tokens` and `usage.output_tokens`.
- TTS records capture `characterCount` from the synthesized input text.
- Transcription records capture `audioSeconds` from returned segment timestamps when available.
- `GET /api/jobs/{jobId}` returns `usageSummary` and `modelCalls`.
- `scripts/demo/docker-e2e-success.sh` prints model-call count and estimated job cost through the existing job summary output.

## Design Choices

Recommended approach: instrument provider boundaries directly. This keeps usage close to the outbound model call, avoids guessing in pipeline stages, and works for both demo and OpenAI implementations.

Alternatives considered:

- Record only in pipeline stages: simpler wiring, but loses provider/model-specific latency and failure details.
- Add a generic OpenAI gateway now: cleaner long term, but too broad for this slice because transcription, Responses, and TTS currently use different request shapes.
- Build frontend cost UI first: visible, but it would require placeholder data until the backend audit API exists.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/resources/db/migration/V7__create_model_call_records.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallOperation.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/ModelCallRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateModelCallRecordCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobUsageSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ModelCallVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/ModelCallRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ModelCallAuditService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify tests under `LinguaFrame/src/test/java/com/linguaframe/...` listed in the tasks below.

## Task 1: Audit Persistence And Cost Configuration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/resources/db/migration/V7__create_model_call_records.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallOperation.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/ModelCallRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateModelCallRecordCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobUsageSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ModelCallVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/ModelCallRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ModelCallAuditService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/ModelCallRepositoryTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/ModelCallAuditServiceTests.java`

**Interfaces:**
- `ModelCallAuditService#recordSuccess(CreateModelCallRecordCommand command): ModelCallVo`
- `ModelCallAuditService#recordFailure(CreateModelCallRecordCommand command, String safeErrorSummary): ModelCallVo`
- `ModelCallAuditService#listModelCalls(String jobId): List<ModelCallVo>`
- `ModelCallAuditService#summarizeJob(String jobId): JobUsageSummaryVo`

- [x] **Step 1: Write failing property binding tests**

Extend `LinguaFramePropertiesTests`:

```java
assertThat(properties.getCost().isEnabled()).isTrue();
assertThat(properties.getCost().getTranscriptionUsdPerMinute()).isEqualByComparingTo("0");
assertThat(properties.getCost().getTranslationInputUsdPerMillionTokens()).isEqualByComparingTo("0");
assertThat(properties.getCost().getTranslationOutputUsdPerMillionTokens()).isEqualByComparingTo("0");
assertThat(properties.getCost().getTtsUsdPerMillionCharacters()).isEqualByComparingTo("0");
```

Add a binding case:

```java
"linguaframe.cost.transcription-usd-per-minute=0.006",
"linguaframe.cost.translation-input-usd-per-million-tokens=0.15",
"linguaframe.cost.translation-output-usd-per-million-tokens=0.60",
"linguaframe.cost.tts-usd-per-million-characters=15.00"
```

Add invalid-property coverage for a negative rate:

```java
"linguaframe.cost.tts-usd-per-million-characters=-1"
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: fail because the new cost getters do not exist.

- [x] **Step 2: Add cost rate properties**

In `LinguaFrameProperties.Cost`, add `BigDecimal` properties with `@DecimalMin("0.0")`:

```java
private BigDecimal transcriptionUsdPerMinute = BigDecimal.ZERO;
private BigDecimal translationInputUsdPerMillionTokens = BigDecimal.ZERO;
private BigDecimal translationOutputUsdPerMillionTokens = BigDecimal.ZERO;
private BigDecimal ttsUsdPerMillionCharacters = BigDecimal.ZERO;
```

Wire YAML:

```yaml
linguaframe:
  cost:
    enabled: ${LINGUAFRAME_COST_ENABLED:true}
    transcription-usd-per-minute: ${LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE:0}
    translation-input-usd-per-million-tokens: ${LINGUAFRAME_COST_TRANSLATION_INPUT_USD_PER_1M_TOKENS:0}
    translation-output-usd-per-million-tokens: ${LINGUAFRAME_COST_TRANSLATION_OUTPUT_USD_PER_1M_TOKENS:0}
    tts-usd-per-million-characters: ${LINGUAFRAME_COST_TTS_USD_PER_1M_CHARS:0}
```

Add the same keys to `.env.example` and `docker-compose.yml` with zero defaults.

- [x] **Step 3: Write failing repository and service tests**

Create `ModelCallRepositoryTests` to save and load a row containing:

```java
new ModelCallRecord(
        "call-1",
        "model-call-job-1",
        LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
        ModelCallOperation.TRANSLATION,
        ModelCallProvider.OPENAI,
        "gpt-test",
        "openai-subtitle-translation-v1",
        ModelCallStatus.SUCCEEDED,
        125L,
        1000,
        500,
        null,
        null,
        new BigDecimal("0.00045000"),
        null,
        createdAt
)
```

Create `ModelCallAuditServiceTests` with:

- translation cost: input `1000`, output `500`, rates `0.15` and `0.60` per 1M tokens -> `0.00045000`.
- transcription cost: `audioSeconds=120.0`, rate `0.006` per minute -> `0.01200000`.
- TTS cost: `characterCount=2000`, rate `15.00` per 1M characters -> `0.03000000`.
- `summarizeJob` returns call count, failed count, total latency, and total estimated cost.
- `recordFailure` truncates safe errors to 512 chars and stores status `FAILED`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,ModelCallAuditServiceTests test
```

Expected: fail because persistence and audit types do not exist.

- [x] **Step 4: Implement migration, enums, repository, and service**

Create `V7__create_model_call_records.sql`:

```sql
CREATE TABLE model_call_records (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    prompt_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    audio_seconds DECIMAL(12,3) NULL,
    character_count INT NULL,
    estimated_cost_usd DECIMAL(18,8) NOT NULL,
    safe_error_summary VARCHAR(512) NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_model_call_records_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_model_call_records_job_created
    ON model_call_records(job_id, created_at);
```

Create enums:

```java
public enum ModelCallOperation {
    TRANSCRIPTION,
    TRANSLATION,
    TTS
}
```

```java
public enum ModelCallProvider {
    DEMO,
    OPENAI
}
```

```java
public enum ModelCallStatus {
    SUCCEEDED,
    FAILED
}
```

Create records:

```java
public record CreateModelCallRecordCommand(
        String jobId,
        LocalizationJobStage stage,
        ModelCallOperation operation,
        ModelCallProvider provider,
        String model,
        String promptVersion,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount
) {
}
```

```java
public record JobUsageSummaryVo(
        int modelCallCount,
        int failedModelCallCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount
) {
}
```

Implement repository methods:

```java
void save(ModelCallRecord record);
List<ModelCallRecord> findByJobId(String jobId);
```

Implement `ModelCallAuditServiceImpl` with cost formulas:

```java
transcription = audioSeconds / 60 * transcriptionUsdPerMinute
translation = inputTokens / 1_000_000 * inputRate + outputTokens / 1_000_000 * outputRate
tts = characterCount / 1_000_000 * ttsRate
```

Use `RoundingMode.HALF_UP` and store `estimatedCostUsd` with scale `8`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,ModelCallRepositoryTests,ModelCallAuditServiceTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java LinguaFrame/src/main/resources/application.yaml LinguaFrame/src/main/resources/application-docker.yaml LinguaFrame/src/test/resources/application-test.yaml .env.example docker-compose.yml LinguaFrame/src/main/resources/db/migration/V7__create_model_call_records.sql LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallOperation.java LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallProvider.java LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/ModelCallStatus.java LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/ModelCallRecord.java LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateModelCallRecordCommand.java LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobUsageSummaryVo.java LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ModelCallVo.java LinguaFrame/src/main/java/com/linguaframe/job/repository/ModelCallRepository.java LinguaFrame/src/main/java/com/linguaframe/job/service/ModelCallAuditService.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java LinguaFrame/src/test/java/com/linguaframe/job/repository/ModelCallRepositoryTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/ModelCallAuditServiceTests.java
git commit -m "Add model call audit persistence"
```

## Task 2: Provider Audit Instrumentation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/RecordingModelCallAuditService.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTranslationProviderTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTranscriptionProviderTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTtsProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranslationProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTtsProviderTests.java`

**Interfaces:**
- Providers depend on `ModelCallAuditService`.
- Prompt versions:
  - `demo-transcription-v1`
  - `demo-translation-v1`
  - `demo-tts-v1`
  - `openai-audio-transcriptions-v1`
  - `openai-subtitle-translation-v1`
  - `openai-tts-v1`

- [x] **Step 1: Write failing provider audit tests**

Add `RecordingModelCallAuditService` test helper with `successCommands`, `failureCommands`, and `failureSummaries`.

Add/extend provider tests:

- Demo transcription records `DEMO`, `TRANSCRIPTION`, `TRANSCRIPT_SUBTITLE_EXPORT`, `demo-transcription-v1`, model `demo-transcription`.
- Demo translation records `DEMO`, `TRANSLATION`, `TARGET_SUBTITLE_EXPORT`, `demo-translation-v1`, model `demo-translation`.
- Demo TTS records `DEMO`, `TTS`, `DUBBING_AUDIO_GENERATION`, `demo-tts-v1`, model `demo-tts`, and `characterCount=request.text().length()`.
- OpenAI transcription records success with provider `OPENAI`, configured model, audio seconds from last segment end time, and prompt version `openai-audio-transcriptions-v1`.
- OpenAI translation records success with provider `OPENAI`, configured model, prompt version `openai-subtitle-translation-v1`, and token usage from:

```json
"usage": {
  "input_tokens": 1000,
  "output_tokens": 500
}
```

- OpenAI TTS records success with provider `OPENAI`, configured model, prompt version `openai-tts-v1`, and `characterCount`.
- OpenAI HTTP failures record one failed audit entry with sanitized summary such as `OpenAI translation request failed with status 401`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests test
```

Expected: fail because providers do not yet accept or call `ModelCallAuditService`.

- [x] **Step 2: Instrument demo providers**

Give each demo provider a constructor dependency:

```java
private final ModelCallAuditService auditService;

public DemoTranslationProvider(ModelCallAuditService auditService) {
    this.auditService = auditService;
}
```

Wrap provider logic:

```java
long started = System.nanoTime();
try {
    TranslationResultBo result = translateDeterministically(targetLanguage, transcriptSegments);
    auditService.recordSuccess(new CreateModelCallRecordCommand(
            jobId,
            LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
            ModelCallOperation.TRANSLATION,
            ModelCallProvider.DEMO,
            "demo-translation",
            "demo-translation-v1",
            elapsedMillis(started),
            null,
            null,
            null,
            translatedCharacterCount(result)
    ));
    return result;
} catch (RuntimeException ex) {
    auditService.recordFailure(commandWithoutUsage(...), ex.getMessage());
    throw ex;
}
```

Use the matching operation, stage, model, and prompt version for transcription and TTS.

- [x] **Step 3: Instrument OpenAI providers**

In each OpenAI provider, measure latency around the existing request/parse flow. Record success only after response validation passes. Record failure in `catch (RuntimeException ex)` and rethrow.

For translation, change response parsing to keep usage:

```java
private record OpenAiTranslationResponse(String outputText, Integer inputTokens, Integer outputTokens) {
}
```

Extract token usage only when present and numeric:

```java
JsonNode usage = response.get("usage");
Integer inputTokens = integerOrNull(usage, "input_tokens");
Integer outputTokens = integerOrNull(usage, "output_tokens");
```

For transcription, compute audio seconds from result segments:

```java
BigDecimal audioSeconds = result.segments().stream()
        .map(TranscriptionSegmentBo::endMs)
        .max(Long::compareTo)
        .map(endMs -> BigDecimal.valueOf(endMs).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP))
        .orElse(null);
```

For TTS, use:

```java
Integer characterCount = request.text() == null ? 0 : request.text().length();
```

Use only sanitized exception messages already exposed by the providers.

- [x] **Step 4: Run provider audit tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java LinguaFrame/src/test/java/com/linguaframe/job/service/RecordingModelCallAuditService.java LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTranscriptionProviderTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTranslationProviderTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/DemoTtsProviderTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProviderTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranslationProviderTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTtsProviderTests.java
git commit -m "Record provider model call audits"
```

## Task 3: Job Detail Usage Summary API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `LocalizationJobVo` adds:
  - `JobUsageSummaryVo usageSummary`
  - `List<ModelCallVo> modelCalls`

- [x] **Step 1: Write failing controller test**

Add `returnsLocalizationJobWithUsageSummaryAndModelCalls()`:

1. Create a video and job.
2. Save one successful translation record and one failed TTS record through `ModelCallAuditService`.
3. Call `GET /api/jobs/{jobId}`.

Expected JSON assertions:

```java
.andExpect(jsonPath("$.usageSummary.modelCallCount").value(2))
.andExpect(jsonPath("$.usageSummary.failedModelCallCount").value(1))
.andExpect(jsonPath("$.usageSummary.estimatedCostUsd").value(0.00045000))
.andExpect(jsonPath("$.modelCalls[0].operation").value("TRANSLATION"))
.andExpect(jsonPath("$.modelCalls[0].status").value("SUCCEEDED"))
.andExpect(jsonPath("$.modelCalls[1].operation").value("TTS"))
.andExpect(jsonPath("$.modelCalls[1].status").value("FAILED"))
.andExpect(jsonPath("$.modelCalls[1].safeErrorSummary").value("OpenAI TTS request failed with status 401"))
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: fail because `usageSummary` and `modelCalls` are not returned.

- [x] **Step 2: Attach usage to job detail**

Modify `LocalizationJobVo`:

```java
JobUsageSummaryVo usageSummary,
List<ModelCallVo> modelCalls
```

Modify `LocalizationJobQueryServiceImpl` constructor to accept `ModelCallAuditService`.

Return:

```java
modelCallAuditService.summarizeJob(jobId),
modelCallAuditService.listModelCalls(jobId)
```

Use empty summary values when a job has no model calls:

```json
{
  "modelCallCount": 0,
  "failedModelCallCount": 0,
  "totalLatencyMs": 0,
  "estimatedCostUsd": 0,
  "inputTokens": null,
  "outputTokens": null,
  "audioSeconds": null,
  "characterCount": null
}
```

- [x] **Step 3: Extend execution pipeline coverage**

Update `LocalizationJobExecutionServiceTests#dubbingAudioStageCreatesArtifactAfterTargetSubtitleExport` or the current full-pipeline test so the in-memory/fake providers use a fake `ModelCallAuditService` and the test can assert the full pipeline still completes.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobExecutionServiceTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java
git commit -m "Expose job model call usage summary"
```

## Task 4: Demo Output And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

- [ ] **Step 1: Print usage summary in demo script**

Update `print_job_summary()` to print usage values after `retryCount`:

```python
summary = job.get("usageSummary") or {}
print("modelCallCount=" + str(summary.get("modelCallCount", 0)))
print("failedModelCallCount=" + str(summary.get("failedModelCallCount", 0)))
print("estimatedCostUsd=" + str(summary.get("estimatedCostUsd", "0")))
for call in job.get("modelCalls", []):
    print("- MODEL_CALL " + call["operation"] + " " + call["provider"] + " " + call["model"] + " " + call["status"])
```

Run:

```bash
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
```

Expected: pass.

- [ ] **Step 2: Update user-facing docs**

Document:

- `LINGUAFRAME_COST_ENABLED`
- `LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE`
- `LINGUAFRAME_COST_TRANSLATION_INPUT_USD_PER_1M_TOKENS`
- `LINGUAFRAME_COST_TRANSLATION_OUTPUT_USD_PER_1M_TOKENS`
- `LINGUAFRAME_COST_TTS_USD_PER_1M_CHARS`
- `GET /api/jobs/{jobId}` includes `usageSummary` and `modelCalls`.
- Default `.env.example` cost rates are zero because provider pricing changes.
- Docker demo should print `modelCallCount=2` by default when transcription and translation are enabled, and `modelCallCount=3` when TTS is enabled.

Add decision:

```text
Decision: Add model-call audit records and configurable cost estimates before the frontend.
Reason: Job detail needs durable usage data before React can honestly show model calls and estimated cost.
Impact: Demo jobs now expose provider/model/status/latency and estimated cost, while budget enforcement and quality evaluation remain separate future slices.
```

- [ ] **Step 3: Run focused verification**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,ModelCallRepositoryTests,ModelCallAuditServiceTests,DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests,LocalizationJobControllerTests,LocalizationJobExecutionServiceTests test
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
docker compose --env-file .env.example config
```

Expected:

- Focused Maven tests pass.
- Bash syntax checks pass.
- Compose renders all `LINGUAFRAME_COST_*` values.

- [ ] **Step 4: Update progress records**

Append validation evidence to `docs/progress/execution-log.md`, including test counts and compose-rendered cost variables.

Commit:

```bash
git add scripts/demo/lib/linguaframe-demo.sh README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/progress/decisions.md docs/progress/execution-log.md
git commit -m "Document model call audit demo output"
```

## Task 5: Full Verification And Merge

**Files:**
- Modify: `docs/plans/020-model-call-audit-cost-mvp.md`
- Modify: `docs/progress/execution-log.md`

- [ ] **Step 1: Run full backend verification**

Run:

```bash
mvn -pl LinguaFrame test
```

Expected: all tests pass with `Failures: 0, Errors: 0`.

- [ ] **Step 2: Mark this plan complete**

Replace all task checkboxes in this plan with `- [x]` only after the corresponding task and verification have passed.

- [ ] **Step 3: Commit verification records**

Commit plan checkbox updates and final execution-log evidence:

```bash
git add docs/plans/020-model-call-audit-cost-mvp.md docs/progress/execution-log.md
git commit -m "Record model call audit verification"
```

- [ ] **Step 4: Merge back to main**

Verify clean feature branch:

```bash
git status --short --branch
```

Merge:

```bash
git checkout main
git merge --no-ff model-call-audit-cost-mvp -m "Merge branch 'model-call-audit-cost-mvp'"
```

Run final verification on `main`:

```bash
mvn -pl LinguaFrame test
git status --short --branch
```

Expected:

- Full tests pass on `main`.
- Working tree is clean.
- `main` contains merge commit for `model-call-audit-cost-mvp`.

## Self-Review

- Spec coverage: persistence, provider instrumentation, job API output, demo output, docs, and verification are covered.
- Scope control: frontend UI, budget enforcement, prompt-template storage, Redis, quality evaluation, and caching are explicitly excluded.
- Type consistency: `ModelCallOperation`, `ModelCallProvider`, `ModelCallStatus`, `ModelCallVo`, and `JobUsageSummaryVo` names are consistent across tasks.
- Pricing risk: defaults are zero and all estimates are configurable, avoiding stale provider price assumptions.
