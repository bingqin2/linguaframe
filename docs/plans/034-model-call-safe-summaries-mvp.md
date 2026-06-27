# Model Call Safe Summaries MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe input and output summaries to every model-call record so job detail explains what each AI call processed without storing raw user text, media paths, request payloads, or secrets.

**Architecture:** Extend the existing model-call audit chain from `CreateModelCallRecordCommand` through `ModelCallRecord`, `ModelCallRepository`, `ModelCallAuditServiceImpl`, and `ModelCallVo`. Add a small summary builder used by OpenAI and demo providers to emit count-based summaries, then surface those fields in the backend JSON response and React model-call panel.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, AssertJ, React, Vite, TypeScript, Vitest.

## Global Constraints

- This feature must be a complete, user-visible feature slice: schema migration, backend domain/API, provider integration, frontend display, tests, docs, validation, commit, and merge back to `main`.
- Summaries may include counts, durations, target language, provider/model metadata, evaluation score, and verdict.
- Summaries must not include raw transcript text, translated subtitle text, TTS text, OpenAI request/response payloads, authorization headers, API keys, uploaded media bytes, or raw user media paths.
- Summary fields must be nullable for older records and capped at 512 characters before persistence.
- Existing model-call cost, latency, token, audio, character, status, and safe-error behavior must remain unchanged.

---

## Task 1: Extend Model Call Schema And Audit Domain

**Files:**

- Create: `LinguaFrame/src/main/resources/db/migration/V13__add_model_call_safe_summaries.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateModelCallRecordCommand.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/ModelCallRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ModelCallVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/ModelCallRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallAuditServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/ModelCallRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ModelCallAuditServiceTests.java`

**Interfaces:**

- `CreateModelCallRecordCommand` gains `String inputSummary` and `String outputSummary`.
- `ModelCallRecord` gains `String inputSummary` and `String outputSummary`.
- `ModelCallVo` gains `String inputSummary` and `String outputSummary`.
- `ModelCallAuditServiceImpl` truncates `inputSummary`, `outputSummary`, and `safeErrorSummary` to 512 characters.

- [x] **Step 1: Add failing backend audit tests**
  - Repository test saves a record with `inputSummary = "3 segments, 42 source chars"` and `outputSummary = "3 segments, 50 target chars"`, then verifies `findByJobId` returns both fields.
  - Audit service test records success and verifies `ModelCallVo.inputSummary()` and `ModelCallVo.outputSummary()` are populated.
  - Audit service test records 600-character summaries and verifies persisted summaries are 512 characters.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,ModelCallAuditServiceTests test
```

Expected: compilation or assertion failure because summary fields do not exist.

- [x] **Step 3: Implement schema and audit mapping**
  - Add nullable `input_summary VARCHAR(512)` and `output_summary VARCHAR(512)` columns to `model_call_records`.
  - Update insert/select SQL and row mapping in `ModelCallRepository`.
  - Update records and VO constructors.
  - Truncate summaries inside `ModelCallAuditServiceImpl.save(...)` before constructing `ModelCallRecord`.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,ModelCallAuditServiceTests test
```

Expected: selected tests pass.

---

## Task 2: Add Safe Summary Builder

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ModelCallSummaryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ModelCallSummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ModelCallSummaryServiceTests.java`

**Interfaces:**

- `translationInput(String targetLanguage, int segmentCount, int sourceCharacterCount)` returns `target=zh-CN, segments=3, sourceChars=42`.
- `translationOutput(int segmentCount, int targetCharacterCount)` returns `segments=3, targetChars=50`.
- `transcriptionInput(BigDecimal audioSeconds)` returns `audioSeconds=45.000`.
- `transcriptionOutput(int segmentCount, int textCharacterCount)` returns `segments=8, transcriptChars=320`.
- `ttsInput(int characterCount)` returns `characters=1200`.
- `ttsOutput(int audioByteCount)` returns `audioBytes=34567`.
- `evaluationInput(String targetLanguage, int sourceSegmentCount, int targetSegmentCount)` returns `target=zh-CN, sourceSegments=8, targetSegments=8`.
- `evaluationOutput(int score, String verdict)` returns `score=88, verdict=Good`.

- [x] **Step 1: Add failing service tests**
  - Verify each method returns deterministic count-only text.
  - Verify raw sample text passed through character counts never appears in summaries.
  - Verify blank verdict becomes `verdict=Unavailable`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=ModelCallSummaryServiceTests test
```

Expected: compilation failure because `ModelCallSummaryService` does not exist.

- [x] **Step 3: Implement summary service**
  - Implement the interface as a Spring `@Service`.
  - Format decimal audio seconds with scale 3.
  - Keep every output short enough to remain well under the 512-character persistence cap.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=ModelCallSummaryServiceTests test
```

Expected: selected tests pass.

---

## Task 3: Populate Summaries From Providers

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiQualityEvaluationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/RecordingModelCallAuditService.java`
- Test: provider tests under `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/`

**Interfaces:**

- Provider constructors gain `ModelCallSummaryService modelCallSummaryService` where model calls are recorded.
- Provider command builders pass input and output summaries into `CreateModelCallRecordCommand`.

- [x] **Step 1: Add failing provider tests**
  - Translation provider records `target=<language>`, source segment count, source character count, target segment count, and target character count.
  - Transcription provider records audio seconds and transcript segment/text counts.
  - TTS provider records character count and returned audio byte count.
  - Evaluation provider records source/target segment counts and score/verdict.
  - Demo providers record the same summary fields with demo data.

- [x] **Step 2: Run red provider tests**

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests,OpenAiQualityEvaluationProviderTests,DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests test
```

Expected: compilation or assertion failure because providers do not emit summaries.

- [x] **Step 3: Implement provider wiring**
  - Inject `ModelCallSummaryService` into the OpenAI and demo providers that create audit commands.
  - Compute summaries after successful provider responses whenever output counts are available.
  - For failure paths, include the same input summary and leave output summary `null`.
  - Update test helpers for the new command shape.

- [x] **Step 4: Run green provider tests**

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests,OpenAiQualityEvaluationProviderTests,DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests test
```

Expected: selected tests pass.

---

## Task 4: Expose Summaries In API And React Demo

**Files:**

- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**

- `GET /api/jobs/{jobId}` includes `modelCalls[].inputSummary` and `modelCalls[].outputSummary`.
- Frontend `ModelCall` type includes `inputSummary: string | null` and `outputSummary: string | null`.
- React model-call table displays `Input` and `Output` columns, with `-` for missing summaries.

- [x] **Step 1: Add failing API and frontend tests**
  - Controller test asserts model-call summary fields are present in job detail JSON.
  - API fixture tests include the two new nullable fields.
  - App test asserts the model-call region renders safe summaries and does not render raw subtitle text from fixtures.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: backend or frontend tests fail because summary fields are not exposed or rendered.

- [x] **Step 3: Implement API and UI display**
  - Backend JSON should be produced by the updated `ModelCallVo` record without a separate controller mapper.
  - Update TypeScript types and fixtures.
  - Add compact `Input` and `Output` columns to the existing model-call table.
  - Keep the empty-state behavior unchanged.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected backend and frontend tests pass.

---

## Task 5: Documentation, Validation, Commit, And Merge

**Files:**

- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/034-model-call-safe-summaries-mvp.md`

- [x] **Step 1: Update docs**
  - README: update model-call audit description to mention safe input/output summaries.
  - Product spec: document count-only model-call summaries as implemented observability.
  - Roadmap Phase 10: mark safe input/output summaries as complete after validation.
  - Decisions: record why summaries are count-based and capped instead of storing raw payload previews.
  - Execution log: record red/green validation commands and final merge verification.

- [x] **Step 2: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
```

Expected: backend and frontend test suites pass.

- [x] **Step 3: Commit feature branch**

```bash
git add README.md docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md docs/plans/034-model-call-safe-summaries-mvp.md LinguaFrame/src frontend/src
git commit -m "Add model call safe summaries"
```

- [x] **Step 4: Merge back to main**

```bash
git switch main
git merge --no-ff model-call-safe-summaries-mvp
```

- [x] **Step 5: Run post-merge smoke validation**

```bash
mvn -pl LinguaFrame -Dtest=ModelCallAuditServiceTests,LocalizationJobControllerTests test
cd frontend && npm run test:run -- App
```

Expected: selected post-merge checks pass on `main`.
