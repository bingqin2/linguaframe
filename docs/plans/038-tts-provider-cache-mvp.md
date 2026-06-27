# TTS Provider Cache MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reuse compatible TTS provider outputs across jobs so repeated target-subtitle text can skip duplicate OpenAI or demo TTS calls while keeping cache behavior visible in job detail.

**Architecture:** Add a TTS-specific cache key and persistence layer, modeled after translation provider caching but storing generated audio bytes metadata and safe cache compatibility fields. `DubbingAudioGenerationPipelineStage` will check artifact cache first, then provider cache, then budget/provider execution, so same-video artifact reuse remains the cheapest path and cross-video compatible TTS reuse avoids provider calls. Cache hits create fresh `DUBBING_AUDIO` artifacts for the current job and record `ProviderCacheHitVo(ModelCallOperation.TTS, cacheKey, sourceJobId)`.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, AssertJ, React/Vite docs-only visibility through existing cache summary.

## Global Constraints

- Keep this as one complete feature slice: backend cache key, persistence, pipeline integration, tests, docs, validation, commit, and merge to `main`.
- Do not cache raw OpenAI request payloads, API keys, local media paths, or user-supplied file paths.
- Do not add generic prompt-response caching, transcription caching, quality-evaluation caching, billing, payments, or admin dashboards.
- Preserve existing artifact cache behavior: same-video `DUBBING_AUDIO` artifact reuse runs before provider cache lookup.
- Preserve budget behavior: budget guard runs only before a real provider call, not before artifact or provider cache hits.
- Cache compatibility must include target language, provider, model, voice, and normalized ordered TTS text.

---

## Task 1: TTS Cache Schema, Repository, And Key Service

**Files:**

- Create: `LinguaFrame/src/main/resources/db/migration/V14__create_tts_cache_entries.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsCacheLookupBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateTtsCacheEntryCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/TtsCacheEntryRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/TtsCacheHitVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/TtsCacheRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TtsCacheKeyService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TtsCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TtsCacheKeyServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TtsCacheServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/TtsCacheRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TtsCacheKeyServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TtsCacheServiceTests.java`

**Interfaces:**

- `TtsCacheKeyService.build(String language, String provider, String model, String voice, String text) -> TtsCacheLookupBo`
- `TtsCacheService.findCachedTts(TtsCacheLookupBo lookup) -> Optional<TtsCacheHitVo>`
- `TtsCacheService.storeTts(TtsCacheLookupBo lookup, String jobId, TtsResultBo result) -> void`

- [x] **Step 1: Add failing tests**
  - `TtsCacheKeyServiceTests` verifies stable SHA-256 keys for equivalent trimmed text and different keys when voice/model/text differs.
  - `TtsCacheRepositoryTests` verifies `saveIfAbsent` stores and returns one row per `cacheKey`.
  - `TtsCacheServiceTests` verifies cached audio response JSON round-trips into `TtsResultBo`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=TtsCacheKeyServiceTests,TtsCacheRepositoryTests,TtsCacheServiceTests test
```

Expected: compilation failure because TTS cache types do not exist.

- [x] **Step 3: Implement schema and services**
  - Create `tts_cache_entries` with unique `cache_key`, `text_hash`, `language`, `provider`, `model`, `voice`, `response_json`, `source_job_id`, and `created_at`.
  - Store audio bytes as Base64 inside `response_json` with `filename` and `contentType`.
  - Build cache keys from provider, model, voice, language, and SHA-256 of normalized text.
  - Keep validation messages generic and free of raw text.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=TtsCacheKeyServiceTests,TtsCacheRepositoryTests,TtsCacheServiceTests test
```

Expected: selected tests pass.

## Task 2: Dubbing Audio Stage Provider Cache Integration

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java`
- Modify as needed: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**

- Consumes `TtsCacheKeyService` and `TtsCacheService` from Task 1.
- Produces provider cache hits through existing `LocalizationJobExecutionContextBo.recordProviderCacheHit`.

- [x] **Step 1: Add failing stage tests**
  - Provider cache hit creates a new `DUBBING_AUDIO` artifact without calling `TtsProvider`.
  - Provider cache hit records `ProviderCacheHitVo` with `ModelCallOperation.TTS`.
  - Artifact cache hit still short-circuits before TTS provider cache lookup.
  - Budget guard is not called on provider cache hit.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests test
```

Expected: failure because the stage does not consult TTS provider cache.

- [x] **Step 3: Implement stage wiring**
  - Add constructor dependencies for `TtsCacheKeyService` and `TtsCacheService`, keeping compatibility constructors for existing tests.
  - Build ordered TTS text exactly once from target subtitles.
  - Resolve cache provider/model/voice from `linguaframe.tts.provider` and `linguaframe.tts.openai`.
  - On cache hit, create a fresh `DUBBING_AUDIO` artifact from cached bytes and record provider cache hit.
  - On cache miss, run budget guard, call `TtsProvider`, create artifact, then store the result in cache.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: selected tests pass.

## Task 3: Job Visibility, Docs, And Validation

**Files:**

- Modify: `README.md`
- Modify: `docs/product/architecture.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/038-tts-provider-cache-mvp.md`

**Interfaces:**

- Uses existing `ProviderCacheHitVo`, timeline cache-hit handling, `JobCacheSummaryVo.providerCacheHitCount`, and React cache summary display.

- [x] **Step 1: Update docs**
  - Document that TTS provider cache is implemented and visible through provider cache-hit counts.
  - Clarify that transcription, quality evaluation, and generic prompt-response caches remain future work.
  - Record the decision to cache provider TTS outputs by language/provider/model/voice/text hash.

- [x] **Step 2: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
git diff --check
```

Expected: all commands exit 0.

- [ ] **Step 3: Commit feature branch**

```bash
git add README.md docs/product/architecture.md docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md docs/plans/038-tts-provider-cache-mvp.md LinguaFrame/src
git commit -m "Add TTS provider cache"
```

- [ ] **Step 4: Merge back to main**

```bash
git switch main
git merge --no-ff tts-provider-cache-mvp
```

- [ ] **Step 5: Run post-merge smoke validation**

```bash
mvn -pl LinguaFrame -Dtest=TtsCacheServiceTests,DubbingAudioGenerationPipelineStageTests test
docker compose --env-file .env.example config --quiet
```

Expected: post-merge checks pass on `main`.
