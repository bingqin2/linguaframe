# Quality Evaluation Provider Cache MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache repeated quality evaluation provider outputs by source transcript, target subtitles, target language, provider, model, and prompt version so compatible repeat jobs can skip another evaluation provider call.

**Architecture:** Add a durable `quality_evaluation_cache_entries` table plus key, repository, and service layers following the transcription, translation, and TTS cache patterns. Wire `QualityEvaluationPipelineStage` to build a cache lookup after loading source/target segments and before budget/provider execution. On a cache hit, write a fresh `quality_evaluations` row for the current job, record a provider `CACHE_HIT`, and skip the budget guard and provider call.

**Tech Stack:** Spring Boot, Java 21, Flyway, JdbcClient, Jackson, JUnit 5, AssertJ, Mockito, Docker Compose.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Cache keys must not store raw transcript text, raw subtitle text, object keys, local paths, provider payloads, API keys, or uploaded media bytes.
- Cache hits must be visible through existing job timeline and `cacheSummary.providerCacheHitCount`.
- Cache hits must create a fresh current-job quality evaluation record, not reuse the original row id or job id.
- Quality evaluation remains optional and non-blocking for provider failures.
- Keep repository docs and UI copy in English; discussion can stay Chinese.

---

## Task 1: Cache Schema And Repository

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V17__create_quality_evaluation_cache_entries.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/QualityEvaluationCacheEntryRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateQualityEvaluationCacheEntryCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/QualityEvaluationCacheRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/QualityEvaluationCacheRepositoryTests.java`

**Interfaces:**
- Produces: `QualityEvaluationCacheRepository.findByCacheKey(String cacheKey)`
- Produces: `QualityEvaluationCacheRepository.saveIfAbsent(CreateQualityEvaluationCacheEntryCommand command)`

- [x] Add Flyway table `quality_evaluation_cache_entries` with `id`, `cache_key`, `source_hash`, `target_hash`, `language`, `provider`, `model`, `prompt_version`, `response_json`, `source_job_id`, and `created_at`.
- [x] Add a unique index on `cache_key` and a lookup index on `source_hash`, `target_hash`, `language`, `provider`, `model`, and `prompt_version`.
- [x] Add record and command types matching repository columns.
- [x] Add repository tests for save/find and duplicate `saveIfAbsent` returning the existing row.
- [x] Run: `mvn -pl LinguaFrame -Dtest=QualityEvaluationCacheRepositoryTests test`.

## Task 2: Cache Key And Serialization Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/QualityEvaluationCacheLookupBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/QualityEvaluationCacheHitVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/QualityEvaluationCacheKeyService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/QualityEvaluationCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/QualityEvaluationCacheKeyServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/QualityEvaluationCacheServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/QualityEvaluationCacheKeyServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/QualityEvaluationCacheServiceTests.java`

**Interfaces:**
- Produces: `QualityEvaluationCacheKeyService.build(String language, String provider, String model, String promptVersion, List<TranscriptSegmentVo> sourceSegments, List<SubtitleSegmentVo> targetSegments): QualityEvaluationCacheLookupBo`
- Produces: `QualityEvaluationCacheService.findCachedEvaluation(QualityEvaluationCacheLookupBo lookup): Optional<QualityEvaluationCacheHitVo>`
- Produces: `QualityEvaluationCacheService.storeEvaluation(QualityEvaluationCacheLookupBo lookup, String jobId, QualityEvaluationResultBo result): void`

- [x] Hash ordered source transcript segments and ordered target subtitle segments separately with SHA-256 using index, start/end time, and text.
- [x] Build the cache key from language, provider, model, prompt version, source hash, and target hash.
- [x] Reject blank language/provider/model/prompt version and empty source or target segment lists.
- [x] Serialize `QualityEvaluationResultBo` as JSON containing score, verdict, dimension scores, issues, and suggested fixes.
- [x] Deserialize cached JSON back to `QualityEvaluationResultBo`.
- [x] Test deterministic keys, key differences across language/provider/model/version/source/target, store/find behavior, and invalid cached JSON being ignored as a cache miss.
- [x] Run: `mvn -pl LinguaFrame -Dtest='QualityEvaluationCacheKeyServiceTests,QualityEvaluationCacheServiceTests' test`.

## Task 3: Quality Evaluation Service Cache Support

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/QualityEvaluationService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/QualityEvaluationServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/QualityEvaluationServiceTests.java`

**Interfaces:**
- Consumes: `QualityEvaluationResultBo` from cache service.
- Produces: `QualityEvaluationService.storeCachedEvaluation(String jobId, String language, QualityEvaluationResultBo result): QualityEvaluationVo`

- [x] Add a failing service test that `storeCachedEvaluation` writes a fresh succeeded `QualityEvaluationRecord` for the current job using a cached `QualityEvaluationResultBo` without invoking `QualityEvaluationProvider`.
- [x] Add `storeCachedEvaluation` to `QualityEvaluationService`.
- [x] Reuse the existing succeeded-record conversion logic so cached and provider-backed evaluations produce the same public `QualityEvaluationVo` shape.
- [x] Keep provider failures in `evaluateAndStore` non-blocking and unchanged.
- [x] Run: `mvn -pl LinguaFrame -Dtest=QualityEvaluationServiceTests test`.

## Task 4: Pipeline Cache Integration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/QualityEvaluationPipelineStage.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/QualityEvaluationPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Consumes: cache services from Task 2 and `QualityEvaluationService.storeCachedEvaluation` from Task 3.
- Produces: quality evaluation provider cache-hit reuse in `TRANSLATION_QUALITY_EVALUATION`.

- [x] Add a failing stage test that a cached evaluation stores a current-job quality evaluation, records a provider `CACHE_HIT`, and does not call the budget guard or provider-backed `evaluateAndStore`.
- [x] Add a failing stage test that a cache miss calls the budget guard and `evaluateAndStore`, then stores the successful result in the evaluation cache.
- [x] Add constructor dependencies for `QualityEvaluationCacheKeyService` and `QualityEvaluationCacheService` while preserving the existing constructor for tests that do not need cache services.
- [x] Build provider metadata from `linguaframe.evaluation.provider`: `OPENAI` with `linguaframe.evaluation.openai.model` and prompt version from `PromptTemplateRegistry`, otherwise `DEMO`, `demo-quality-evaluation`, and `demo-quality-evaluation-v1`.
- [x] Keep cache lookup disabled if cache services cannot build a lookup, but do not hide provider failures after a miss.
- [x] Add execution-service coverage that an evaluation provider cache hit contributes to `cacheSummary.providerCacheHitCount`.
- [x] Run: `mvn -pl LinguaFrame -Dtest='QualityEvaluationPipelineStageTests,LocalizationJobExecutionServiceTests' test`.

## Task 5: Documentation And Demo Evidence

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: documented cache behavior and verification expectations.

- [x] Document that quality evaluation cache uses source transcript hash, target subtitle hash, target language, provider, model, and prompt version.
- [x] Document that the cache stores structured evaluation JSON, not raw provider payloads, local paths, object keys, secrets, or uploaded media bytes.
- [x] Add smoke-test expectations for `CACHE_HIT` timeline events, `providerCacheHitCount`, fresh current-job evaluation records, and no repeated evaluation provider model call on compatible repeat jobs.
- [x] Update product docs to mark provider-level quality evaluation cache as implemented and keep generic prompt-response caching as future work.
- [x] Record the decision that quality evaluation cache is provider-output reuse with fresh job evaluation persistence.
- [x] Run: `rg -n "quality evaluation cache|provider cache|CACHE_HIT" README.md docs/agent/smoke-test-checklist.md docs/product/spec.md docs/product/roadmap.md docs/progress/decisions.md docs/progress/execution-log.md`.

## Task 6: Verification And Merge

**Files:**
- Modify: `docs/plans/051-quality-evaluation-provider-cache-mvp.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`.

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='QualityEvaluationCacheRepositoryTests,QualityEvaluationCacheKeyServiceTests,QualityEvaluationCacheServiceTests,QualityEvaluationServiceTests,QualityEvaluationPipelineStageTests,LocalizationJobExecutionServiceTests' test`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run frontend validation to catch cache-summary regressions: `cd frontend && npm run test:run -- App`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add quality evaluation provider cache`.
- [ ] Merge `quality-evaluation-provider-cache-mvp` back to `main`.
- [ ] Run post-merge focused backend validation and `cd frontend && npm run test:run -- App`.
