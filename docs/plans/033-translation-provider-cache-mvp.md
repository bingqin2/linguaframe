# Translation Provider Cache MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache compatible subtitle translation provider results so repeated jobs can skip duplicate translation model calls while keeping cache behavior visible in job detail.

**Architecture:** Add a database-backed translation cache keyed by normalized source subtitle text, target language, provider, model, and prompt version. The target-subtitle pipeline checks the cache before calling `TranslationProvider`, stores successful provider results after subtitle validation, and records provider cache hits as timeline/model-call-visible behavior. The React demo extends the existing cache summary so users can distinguish generated artifacts from skipped translation provider calls.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, AssertJ, React, Vite, TypeScript, Vitest.

## Global Constraints

- This feature must be a complete, user-visible feature slice: schema, key generation, repository, service, pipeline integration, backend API output, frontend display, tests, docs, validation, commit, and merge back to `main`.
- Cache only subtitle translation provider results in this slice.
- Do not cache transcription, quality evaluation, TTS, raw OpenAI request payloads, authorization headers, uploaded media bytes, or raw user media paths.
- Cache compatibility must include target language, provider, model, prompt version, and a SHA-256 hash of ordered transcript segment input.
- A cache hit must not create a successful provider model-call record with fake tokens or cost.
- Existing artifact-level cache behavior must remain unchanged.

---

## Task 1: Add Translation Cache Schema And Repository

**Files:**

- Create: `LinguaFrame/src/main/resources/db/migration/V12__create_translation_cache_entries.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/TranslationCacheEntryRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateTranslationCacheEntryCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/TranslationCacheRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/TranslationCacheRepositoryTests.java`

**Interfaces:**

- `TranslationCacheEntryRecord` fields: `String id`, `String cacheKey`, `String sourceHash`, `String targetLanguage`, `String provider`, `String model`, `String promptVersion`, `String responseJson`, `String sourceJobId`, `Instant createdAt`.
- `CreateTranslationCacheEntryCommand` mirrors repository insert inputs except `id` and `createdAt`.
- `TranslationCacheRepository.findByCacheKey(String cacheKey)` returns `Optional<TranslationCacheEntryRecord>`.
- `TranslationCacheRepository.saveIfAbsent(CreateTranslationCacheEntryCommand command)` inserts the entry unless the key already exists, then returns the existing or inserted record.

- [x] **Step 1: Add failing repository tests**
  - Verify `saveIfAbsent` persists a cache entry and `findByCacheKey` reads it back.
  - Verify a duplicate cache key returns the original entry and keeps the original `sourceJobId`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheRepositoryTests test
```

Expected: compilation failure because translation cache repository types do not exist.

- [x] **Step 3: Implement schema and repository**
  - Create table `translation_cache_entries`.
  - Columns: `id`, `cache_key`, `source_hash`, `target_language`, `provider`, `model`, `prompt_version`, `response_json`, `source_job_id`, `created_at`.
  - Add unique index `uq_translation_cache_entries_cache_key`.
  - Add lookup index on `(target_language, provider, model, prompt_version, created_at)`.
  - Implement repository with `JdbcClient`.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheRepositoryTests test
```

Expected: selected tests pass.

---

## Task 2: Add Stable Translation Cache Key Generation

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranslationCacheLookupBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranslationCacheKeyService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranslationCacheKeyServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationCacheKeyServiceTests.java`

**Interfaces:**

- `TranslationCacheLookupBo` fields: `String cacheKey`, `String sourceHash`, `String targetLanguage`, `String provider`, `String model`, `String promptVersion`.
- `TranslationCacheKeyService.build(String targetLanguage, String provider, String model, String promptVersion, List<TranscriptSegmentVo> segments)` returns `TranslationCacheLookupBo`.

- [x] **Step 1: Add failing key-service tests**
  - Same ordered transcript input returns the same `cacheKey`.
  - Changing segment text, target language, model, or prompt version changes `cacheKey`.
  - Leading/trailing whitespace in target language and transcript text is normalized.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheKeyServiceTests test
```

Expected: compilation failure because the key service does not exist.

- [x] **Step 3: Implement key generation**
  - Build canonical source JSON from ordered transcript segments: `index`, `startMs`, `endMs`, and trimmed `text`.
  - Hash canonical source JSON with SHA-256 lowercase hex into `sourceHash`.
  - Build `cacheKey` as SHA-256 lowercase hex of `provider`, `model`, `promptVersion`, normalized target language, and `sourceHash`.
  - Reject blank target language, provider, model, prompt version, or empty segments.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheKeyServiceTests test
```

Expected: selected tests pass.

---

## Task 3: Add Translation Cache Service

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/TranslationCacheHitVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranslationCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranslationCacheServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationCacheServiceTests.java`

**Interfaces:**

- `TranslationCacheHitVo` fields: `String cacheKey`, `String sourceJobId`, `TranslationResultBo result`.
- `TranslationCacheService.findCachedTranslation(TranslationCacheLookupBo lookup)` returns `Optional<TranslationCacheHitVo>`.
- `TranslationCacheService.storeTranslation(TranslationCacheLookupBo lookup, String jobId, TranslationResultBo result)` returns `void`.

- [x] **Step 1: Add failing service tests**
  - A stored translation can be read back as a `TranslationResultBo`.
  - Malformed cached JSON is ignored as a miss instead of failing the job.
  - Duplicate stores keep the first `sourceJobId`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheServiceTests test
```

Expected: compilation failure because translation cache service types do not exist.

- [x] **Step 3: Implement service**
  - Serialize `TranslationResultBo` as JSON for storage.
  - Deserialize cached JSON through `ObjectMapper`.
  - Return `Optional.empty()` on malformed cache JSON so bad cache data does not break a job.
  - Do not store blank or empty translation results.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=TranslationCacheServiceTests test
```

Expected: selected tests pass.

---

## Task 4: Integrate Cache Into Target Subtitle Pipeline

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/LocalizationJobExecutionContextBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ProviderCacheHitVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TargetSubtitleExportPipelineStage.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TargetSubtitleExportPipelineStageTests.java`

**Interfaces:**

- `ProviderCacheHitVo` fields: `ModelCallOperation operation`, `String cacheKey`, `String sourceJobId`.
- `LocalizationJobExecutionContextBo.recordProviderCacheHit(ProviderCacheHitVo hit)` records provider-level cache hits.
- `LocalizationJobExecutionContextBo.consumeProviderCacheHits()` returns and clears provider cache hits.
- `TargetSubtitleExportPipelineStage` uses provider `"OPENAI"` and active translation prompt version when the configured translation provider is `openai`; for demo provider, use provider `"DEMO"`, model `"demo-translation"`, prompt version `"demo-translation-v1"`.

- [x] **Step 1: Add failing pipeline tests**
  - When cache miss: stage calls `TranslationProvider`, stores result in cache, writes target subtitle artifacts.
  - When cache hit: stage does not call `TranslationProvider`, uses cached result, writes target subtitle artifacts, and records a provider cache hit.
  - Execution service turns provider cache hits into timeline `CACHE_HIT` events with message `Reused cached TRANSLATION provider result from job <sourceJobId>.`

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=TargetSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: compilation failure or behavioral failure because provider cache hits are not modeled or used.

- [x] **Step 3: Implement pipeline integration**
  - Add provider cache hit collection to execution context.
  - Add execution-service timeline persistence for provider cache hits after each stage.
  - In target subtitle stage, build cache lookup before `costBudgetGuardService.assertWithinBudget`.
  - On cache hit, skip budget guard and provider call, replace subtitles from cached result, and export artifacts.
  - On cache miss, run budget guard, call provider, validate/store subtitles, store translation cache, and export artifacts.
  - Keep artifact-level cache behavior unchanged.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=TargetSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: selected tests pass.

---

## Task 5: Expose Provider Cache Summary In API And React

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobCacheSummaryVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**

- `JobCacheSummaryVo` fields become `int cacheHitCount`, `int generatedArtifactCount`, `int providerCacheHitCount`.
- Frontend `JobCacheSummary` mirrors the backend shape.
- UI metrics label remains `Cache hits`, value becomes `<artifact hits> artifacts / <provider hits> provider`.

- [x] **Step 1: Add failing API/UI tests**
  - Backend job detail test asserts `cacheSummary.providerCacheHitCount` increases when timeline has a provider `CACHE_HIT` message.
  - Frontend API test parses `providerCacheHitCount`.
  - App test renders `1 artifacts / 1 provider`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: `providerCacheHitCount` does not exist.

- [x] **Step 3: Implement API and UI**
  - Count provider cache hits from timeline events with status `CACHE_HIT` and messages containing `provider result`.
  - Keep artifact `cacheHitCount` based on artifact rows.
  - Update TypeScript types, API fixtures, and metric rendering.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected backend and frontend tests pass.

---

## Task 6: Documentation, Validation, Commit, And Merge

**Files:**

- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/architecture.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/033-translation-provider-cache-mvp.md`

- [x] **Step 1: Update docs**
  - README: document translation provider cache behavior and how users see provider cache hits.
  - Product spec/roadmap: mark translation provider cache key and cache-hit visibility as implemented for translation only.
  - Architecture: document cache key compatibility and why prompt version is part of the key.
  - Decisions: record why provider cache starts with translation only.
  - Execution log: record red/green validation and post-merge validation.

- [x] **Step 2: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config
git diff --check
```

Expected: all commands pass.

- [ ] **Step 3: Commit and merge**
  - Work on branch `translation-provider-cache-mvp`.
  - Commit as `Add translation provider cache`.
  - Merge back to `main`.

- [ ] **Step 4: Verify on `main`**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config
git diff --check HEAD
```

Expected: all commands pass on `main`.

- [ ] **Step 5: Record post-merge verification and clean up**
  - Add merge commit hash and validation results to `docs/progress/execution-log.md`.
  - Commit the execution-log update.
  - Delete local branch `translation-provider-cache-mvp`.

---

## Completion Criteria

- [x] Translation provider cache entries are persisted with stable compatibility keys.
- [x] Cache keys include source text hash, target language, provider, model, and prompt version.
- [x] Target subtitle generation skips translation provider calls on cache hit.
- [x] Cache misses call the provider, validate subtitles, store cache, and export artifacts.
- [x] Provider cache hits are visible in job timeline and job cache summary.
- [x] React demo distinguishes artifact cache hits from provider cache hits.
- [x] Tests cover repository, key generation, service serialization, pipeline integration, API output, and UI rendering.
- [x] Docs explain translation-only provider cache and defer transcription/TTS/provider-response caching.
- [x] Full validation passes.
- [ ] Feature branch is merged back to `main`.
