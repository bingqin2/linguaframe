# Artifact Cache Hit MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Reuse previously generated job artifacts for repeat jobs from the same source video and target language, and make cache hits visible in backend APIs, the React demo, and documentation.

**Architecture:** Add lightweight artifact cache metadata that links a reused artifact record to the original artifact and records whether it was generated or reused. Cache lookup lives behind a dedicated service used by artifact-producing pipeline stages before expensive artifact generation. Job detail exposes cache hit counts and timeline events; the frontend surfaces cache reuse without implying provider-level prompt caching.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, AssertJ, React, Vite, TypeScript, Vitest.

## Global Constraints

- This feature must be a complete, user-visible feature slice: database, backend service/API, pipeline integration, frontend display, tests, docs, validation, and merge back to `main`.
- Cache reuse is scoped to the same `videoId`, `targetLanguage`, and artifact `type`.
- Do not reuse artifacts across owners, users, or unrelated source videos.
- Do not implement transcription prompt caching, translation prompt caching, provider response caching, or semantic duplicate detection in this slice.
- Reused artifacts must not rewrite object storage bytes; they should create a new job artifact row pointing at the already stored object.
- Timeline and API responses must clearly label reuse as artifact cache hits, not model-call cache hits.
- Do not log or expose OpenAI keys, object storage credentials, or raw local media paths.

---

## Task 1: Persist Artifact Cache Metadata

**Files:**

- Create: `LinguaFrame/src/main/resources/db/migration/V11__add_job_artifact_cache_metadata.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobArtifactRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobArtifactVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobArtifactRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/JobArtifactRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**

- `JobArtifactRecord` gains `boolean cacheHit` and `String sourceArtifactId`.
- `JobArtifactVo` gains `boolean cacheHit` and `String sourceArtifactId`.
- `JobArtifactRepository` gains `Optional<JobArtifactRecord> findReusableArtifact(String videoId, String targetLanguage, JobArtifactType type)`.

- [x] **Step 1: Add failing repository/API tests**
  - In `JobArtifactRepositoryTests`, insert a generated artifact for an earlier job and assert `findReusableArtifact(videoId, targetLanguage, type)` returns it.
  - Insert a reused artifact and assert normal `findByJobId` returns `cacheHit=true` and `sourceArtifactId`.
  - In `LocalizationJobControllerTests.listsArtifactsForLocalizationJob`, assert JSON path `$[0].cacheHit` and `$[0].sourceArtifactId`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,LocalizationJobControllerTests test
```

Expected: compilation/test failure because cache metadata fields and reusable lookup do not exist.

- [x] **Step 3: Add Flyway migration**

```sql
ALTER TABLE job_artifacts
    ADD COLUMN cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE job_artifacts
    ADD COLUMN source_artifact_id VARCHAR(36);

CREATE INDEX idx_job_artifacts_reuse_lookup
    ON job_artifacts (type, cache_hit, created_at);
```

- [x] **Step 4: Update record, VO, repository SQL, and row mapper**
  - Include `cache_hit` and `source_artifact_id` in insert/select/map paths.
  - Implement `findReusableArtifact(...)` by joining `job_artifacts -> localization_jobs` and filtering on same `video_id`, same `target_language`, same artifact `type`, `cache_hit = FALSE`, and non-empty `content_sha256`.
  - Order by `job_artifacts.created_at DESC, job_artifacts.id DESC` and return the newest generated artifact.

- [x] **Step 5: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,LocalizationJobControllerTests test
```

Expected: selected tests pass.

---

## Task 2: Add Artifact Cache Reuse Service

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ArtifactCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ArtifactCacheServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ArtifactCacheServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobArtifactServiceTests.java`

**Interfaces:**

- `ArtifactCacheService.tryReuseArtifact(LocalizationJobExecutionContextBo context, JobArtifactType type)` returns `Optional<JobArtifactVo>`.
- `JobArtifactServiceImpl.createReusedArtifact(String jobId, JobArtifactRecord source)` creates a new artifact row with the current job id, the source object key/filename/content type/size/hash, `cacheHit=true`, and `sourceArtifactId=source.id()`.

- [x] **Step 1: Add failing service tests**
  - `ArtifactCacheServiceTests` should assert no reuse occurs when no prior artifact exists.
  - Assert reuse creates a new artifact for the current job when a prior generated artifact exists for the same video, target language, and type.
  - Assert reused artifacts keep `contentSha256`, `objectKey`, and `sourceArtifactId`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=ArtifactCacheServiceTests,JobArtifactServiceTests test
```

Expected: compilation failure because `ArtifactCacheService` and reused artifact creation do not exist.

- [x] **Step 3: Implement service and artifact creation path**
  - Add `ArtifactCacheService` and `ArtifactCacheServiceImpl`.
  - Add `createReusedArtifact(...)` to `JobArtifactServiceImpl`.
  - Keep existing generated artifact creation unchanged except for setting `cacheHit=false` and `sourceArtifactId=null`.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=ArtifactCacheServiceTests,JobArtifactServiceTests test
```

Expected: selected tests pass.

---

## Task 3: Integrate Cache Hits Into Artifact-Producing Stages

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/AudioExtractionPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleBurnInPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobTimelineEventStatus.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleBurnInPipelineStageTests.java`

**Interfaces:**

- `JobTimelineEventStatus` gains `CACHE_HIT`.
- `LocalizationPipelineStage.execute(...)` remains unchanged.
- Stable artifact-producing stages call `artifactCacheService.tryReuseArtifact(...)` before generating their artifact when the stage output is safe to reuse.
- `WORKER_SUMMARY` is not cacheable because it includes the current `jobId` and `generatedAt`.

- [x] **Step 1: Add failing stage/execution tests**
  - In stage tests, configure a recording cache service that returns an artifact and assert expensive provider/FFmpeg work is not called.
  - In `LocalizationJobExecutionServiceTests`, assert a stage that records cache reuse produces a timeline event with status `CACHE_HIT`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: compilation/test failure because cache service wiring and `CACHE_HIT` status do not exist.

- [x] **Step 3: Implement cache integration**
  - Add `CACHE_HIT` to `JobTimelineEventStatus`.
  - Let `LocalizationJobExecutionServiceImpl` save a `CACHE_HIT` timeline event when a stage reports a reused artifact through a lightweight context flag or timeline helper.
  - Wire `ArtifactCacheService` into audio extraction, dubbing audio, and subtitle burn-in stages.
  - Do not cache transcript/subtitle segment persistence in this slice; only artifact rows are reused.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: selected tests pass.

---

## Task 4: Expose Cache Summary In Job Detail And React UI

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobCacheSummaryVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/api/linguaframeApi.test.ts`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**

- `LocalizationJobVo` gains `JobCacheSummaryVo cacheSummary`.
- `JobCacheSummaryVo` contains `int cacheHitCount` and `int generatedArtifactCount`.
- Frontend `LocalizationJob` gains `cacheSummary`.
- Artifact table shows a cache marker for reused artifacts.

- [x] **Step 1: Add failing API/frontend tests**
  - Backend controller test asserts job detail includes `cacheSummary.cacheHitCount`.
  - Frontend API fixture includes `cacheSummary`.
  - App test asserts job detail shows cache hits and reused artifact rows show `Reused`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: tests fail because cache summary and UI rendering are missing.

- [x] **Step 3: Implement job detail and UI rendering**
  - Compute cache summary from `JobArtifactRepository.findByJobId(jobId)`.
  - Render `Cache hits` near usage/cost metadata in the job detail panel.
  - Add an artifact table column or compact label that shows `Reused` when `artifact.cacheHit` is true.
  - Keep full source artifact id available through `title`.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected backend/frontend tests pass.

---

## Task 5: Documentation, Full Validation, Commit, And Merge

**Files:**

- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/031-artifact-cache-hit-mvp.md`

- [x] **Step 1: Update docs**
  - README: describe artifact cache hit behavior and clarify it is scoped to same source video and target language.
  - Product spec: mark artifact-level duplicate-work avoidance as implemented and keep provider prompt caching as future work.
  - Roadmap Phase 12: distinguish artifact cache hits from future transcription/translation provider cache.
  - Decisions: record why reuse is artifact-level first.
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
  - Work on branch `artifact-cache-hit-mvp`.
  - Commit as `Add artifact cache hit reuse`.
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
  - Delete local branch `artifact-cache-hit-mvp`.

---

## Completion Criteria

- [x] Reused job artifact rows can point to a previous generated artifact through `sourceArtifactId`.
- [x] Cache hits are restricted to the same source video, target language, and artifact type.
- [x] Reused artifacts do not rewrite object storage bytes.
- [x] Job detail API exposes cache summary counts.
- [x] Artifact list API exposes `cacheHit` and `sourceArtifactId`.
- [x] React job detail and artifact table show cache reuse.
- [x] Timeline events clearly mark artifact cache hits.
- [x] Tests cover repository lookup, service reuse, stage skipping, API fields, and UI display.
- [x] README/product docs explain artifact cache scope and defer provider prompt caching.
- [x] Full validation passes.
- [ ] Feature branch is merged back to `main`.
