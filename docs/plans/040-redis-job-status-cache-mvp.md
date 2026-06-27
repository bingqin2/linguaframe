# Redis Job Status Cache MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional Redis-backed cache for job detail snapshots returned by `GET /api/jobs/{jobId}` and reused by SSE progress polling.

**Architecture:** Keep MySQL as the source of truth. Add a cache-aside `LocalizationJobStatusCacheService` that stores serialized `LocalizationJobVo` snapshots in Redis after database reads, reads from Redis before rebuilding the detail view, and evicts affected job keys after retry/cancel/worker status transitions. Keep the feature default-on in Docker but fail-open: Redis cache errors must never break job queries.

**Tech Stack:** Java 21, Spring Boot, Jackson `ObjectMapper`, Spring Data Redis `StringRedisTemplate`, JUnit 5, Mockito, H2-backed Spring Boot tests.

## Global Constraints

- This slice must be a complete feature: runtime config, cache service, query integration, mutation invalidation, tests, docs, and progress logs.
- Do not store uploaded media bytes, local paths, secrets, OpenAI payloads, or raw model text in Redis beyond the already sanitized `LocalizationJobVo` API shape.
- Cache keys must be namespaced as `linguaframe:job-status:<jobId>`.
- Redis errors must fail open and fall back to database reads.
- Do not replace the durable database state model or add Redis pub/sub in this slice.

---

### Task 1: Runtime Configuration And Cache Boundary

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobStatusCacheService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/RedisLocalizationJobStatusCacheService.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/RedisLocalizationJobStatusCacheServiceTests.java`

**Interfaces:**
- Produces: `LocalizationJobStatusCacheService.get(String jobId): Optional<LocalizationJobVo>`
- Produces: `LocalizationJobStatusCacheService.put(LocalizationJobVo job): void`
- Produces: `LocalizationJobStatusCacheService.evict(String jobId): void`

- [x] Add `linguaframe.job-status-cache.enabled` default `true` and `ttl-seconds` default `30` to `LinguaFrameProperties`.
- [x] Add config values to `application*.yaml`, `.env.example`, and Compose environment.
- [x] Write failing property-binding tests for defaults, custom binding, and invalid `ttl-seconds=0`.
- [x] Implement `RedisLocalizationJobStatusCacheService` using `StringRedisTemplate`, `ObjectMapper`, and TTL writes.
- [x] Write tests proving disabled mode bypasses Redis, cache hit deserializes, cache miss returns empty, put writes with TTL, evict deletes, and Redis/Jackson failures fail open.
- [x] Run `mvn -pl LinguaFrame -Dtest='LinguaFramePropertiesTests,RedisLocalizationJobStatusCacheServiceTests' test`.

### Task 2: Query Cache-Aside Integration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobQueryServiceTests.java`
- Test: update existing `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java` only if Spring context needs a mock cache bean.

**Interfaces:**
- Consumes: `LocalizationJobStatusCacheService` from Task 1.
- Produces: `LocalizationJobQueryServiceImpl.getJob(String jobId)` reads cache first, builds from repositories on miss, then writes cache.

- [x] Write failing tests proving `getJob` returns cached `LocalizationJobVo` without repository detail calls when cache hits.
- [x] Write failing tests proving cache miss rebuilds the job detail from repositories and calls `put`.
- [x] Write failing tests proving cache service exceptions do not break `getJob`.
- [x] Modify `LocalizationJobQueryServiceImpl` to inject `LocalizationJobStatusCacheService` and use cache-aside only for `getJob`; keep `listJobs` database-backed.
- [x] Run `mvn -pl LinguaFrame -Dtest='LocalizationJobQueryServiceTests,LocalizationJobControllerTests' test`.

### Task 3: Mutation Invalidation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobRetryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobCancellationServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobRetryServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobCancellationServiceTests.java`
- Test: update `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Consumes: `LocalizationJobStatusCacheService.evict(String jobId)`.
- Produces: retry, cancel, claim, completed, failed, and cancelled execution paths evict stale job snapshots before returning or finishing.

- [x] Write failing tests proving retry and cancel evict the cache before returning fresh job details.
- [x] Write failing tests proving worker execution evicts after status transitions to `PROCESSING`, `COMPLETED`, `FAILED`, and `CANCELLED`.
- [x] Inject `LocalizationJobStatusCacheService` into retry, cancellation, and execution services.
- [x] Call `evict(jobId)` immediately after successful state-changing repository calls.
- [x] Ensure cache eviction exceptions fail open and do not roll back job mutations.
- [x] Run `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests' test`.

### Task 4: Documentation And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/040-redis-job-status-cache-mvp.md`

- [x] Document the job status cache variables, TTL, fail-open behavior, and Redis key boundary.
- [x] Mark the Redis status cache roadmap item as implemented for job-detail snapshots.
- [x] Record the architectural decision that MySQL remains source of truth and Redis is a short-lived cache.
- [x] Run focused validation: `mvn -pl LinguaFrame -Dtest='RedisLocalizationJobStatusCacheServiceTests,LocalizationJobQueryServiceTests,LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test`.
- [x] Run full validation: `mvn -pl LinguaFrame test`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [x] Commit as `Add Redis job status cache`, merge `redis-job-status-cache-mvp` back to `main`, and run a post-merge focused validation.
