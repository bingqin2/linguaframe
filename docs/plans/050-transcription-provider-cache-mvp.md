# Transcription Provider Cache MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache repeated transcription provider outputs by extracted-audio hash, provider, model, and prompt/version boundary so compatible repeat jobs can skip another transcription provider call.

**Architecture:** Add a durable `transcription_cache_entries` table plus key, repository, and service layers that mirror the existing translation and TTS cache patterns. Wire `TranscriptSubtitleExportPipelineStage` to check the cache after reading the extracted audio and before budget/provider execution, store provider results on misses, and record a `CACHE_HIT` timeline event when cached transcript segments are reused.

**Tech Stack:** Spring Boot, Java 21, Flyway, JdbcClient, JUnit 5, AssertJ, Mockito, Docker Compose, Bash.

## Global Constraints

- This is one complete feature slice and must be merged back to `main` after verification.
- Cache keys must not store raw audio bytes, raw transcript text, object keys, local paths, provider payloads, API keys, or uploaded media bytes.
- Cache hits must be visible through existing job timeline and cache summary surfaces.
- Reuse existing transcript persistence and subtitle artifact export paths.
- Keep repository docs and UI copy in English; discussion can stay Chinese.

---

## Task 1: Cache Schema And Repository

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V16__create_transcription_cache_entries.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/TranscriptionCacheEntryRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateTranscriptionCacheEntryCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/TranscriptionCacheRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/TranscriptionCacheRepositoryTests.java`

**Interfaces:**
- Produces: `TranscriptionCacheRepository.findByCacheKey(String cacheKey)`
- Produces: `TranscriptionCacheRepository.saveIfAbsent(CreateTranscriptionCacheEntryCommand command)`

- [x] Add Flyway table `transcription_cache_entries` with `id`, `cache_key`, `audio_hash`, `provider`, `model`, `prompt_version`, `response_json`, `source_job_id`, and `created_at`.
- [x] Add unique indexes on `cache_key` and a lookup index on `audio_hash`.
- [x] Add record and command types matching repository columns.
- [x] Add repository tests for save/find and duplicate `saveIfAbsent` returning the existing row.
- [x] Run: `mvn -pl LinguaFrame -Dtest=TranscriptionCacheRepositoryTests test`.

## Task 2: Cache Key And Serialization Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranscriptionCacheLookupBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/TranscriptionCacheHitVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranscriptionCacheKeyService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranscriptionCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranscriptionCacheKeyServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranscriptionCacheServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranscriptionCacheKeyServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranscriptionCacheServiceTests.java`

**Interfaces:**
- Produces: `TranscriptionCacheKeyService.build(String provider, String model, String promptVersion, byte[] audioContent): TranscriptionCacheLookupBo`
- Produces: `TranscriptionCacheService.findCachedTranscription(TranscriptionCacheLookupBo lookup): Optional<TranscriptionCacheHitVo>`
- Produces: `TranscriptionCacheService.storeTranscription(TranscriptionCacheLookupBo lookup, String jobId, TranscriptionResultBo result): void`

- [x] Hash audio bytes with SHA-256 and build cache key from provider, model, prompt version, and audio hash.
- [x] Reject blank provider/model/prompt version and empty audio.
- [x] Serialize `TranscriptionResultBo` as JSON containing segment indexes, start/end times, and text.
- [x] Deserialize cached JSON back to `TranscriptionResultBo`.
- [x] Test deterministic keys, key differences across provider/model/version/audio, store/find behavior, and invalid cached JSON being ignored as a cache miss.
- [x] Run: `mvn -pl LinguaFrame -Dtest='TranscriptionCacheKeyServiceTests,TranscriptionCacheServiceTests' test`.

## Task 3: Pipeline Cache Integration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranscriptSubtitleExportPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranscriptSubtitleExportPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Consumes: cache services from Task 2.
- Produces: cache-hit reuse in `TRANSCRIPT_SUBTITLE_EXPORT` without invoking `TranscriptionProvider`.

- [x] Add a failing stage test that a cached transcription replaces transcript segments, exports transcript/SRT/VTT artifacts, records a `CACHE_HIT` timeline event, and does not call the provider or budget guard.
- [x] Add a failing stage test that a cache miss calls budget guard and provider, then stores the transcription result.
- [x] Add constructor dependencies for `TranscriptionCacheKeyService` and `TranscriptionCacheService`, then report cache hits through `LocalizationJobExecutionContextBo`.
- [x] Keep cache lookup disabled if cache services cannot build a lookup, but do not hide provider failures after a miss.
- [x] Add execution-service coverage that provider cache-hit timeline events contribute to `cacheSummary.providerCacheHitCount`.
- [x] Run: `mvn -pl LinguaFrame -Dtest='TranscriptSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests' test`.

## Task 4: Demo Evidence And Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: documented cache behavior and verification expectations.

- [x] Document that transcription cache uses extracted-audio hash plus provider/model/prompt version and stores transcript JSON, not raw audio or provider payloads.
- [x] Add smoke-test expectations for `CACHE_HIT` timeline events and provider cache-hit counts on compatible repeat jobs.
- [x] Update product docs to mark provider-level transcription cache as implemented.
- [x] Record the decision that transcription cache is provider-output reuse, not raw audio artifact reuse.
- [x] Run: `rg -n "transcription cache|provider cache|CACHE_HIT" README.md docs/agent/smoke-test-checklist.md docs/product/spec.md docs/product/roadmap.md docs/progress/decisions.md docs/progress/execution-log.md`.

## Task 5: Verification And Merge

**Files:**
- Modify: `docs/plans/050-transcription-provider-cache-mvp.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: verified feature branch merged back to `main`.

- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='TranscriptionCacheRepositoryTests,TranscriptionCacheKeyServiceTests,TranscriptionCacheServiceTests,TranscriptSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests' test`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run frontend validation to catch cache-summary regressions: `cd frontend && npm run test:run -- App`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add transcription provider cache`.
- [ ] Merge `transcription-provider-cache-mvp` back to `main`.
- [ ] Run post-merge focused backend validation and `cd frontend && npm run test:run -- App`.
