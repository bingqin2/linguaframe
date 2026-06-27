# Runtime Live Dependency Checks MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add safe live dependency checks so the private demo can prove MySQL, Redis, RabbitMQ, MinIO, and FFmpeg are reachable before media upload or paid provider calls.

**Architecture:** Keep `GET /api/runtime/dependencies` as the existing non-secret configuration contract, and add a new `GET /api/runtime/live-checks` endpoint for active probes. Probes run bounded, non-destructive checks: a trivial database query, Redis ping, RabbitMQ connection open/close, MinIO bucket existence, and FFmpeg binary discovery. The React demo and private preflight consume the live-check result without exposing credentials, local paths, object keys, or provider payloads.

**Tech Stack:** Java 21, Spring Boot MVC, JDBC `JdbcClient`, Spring Data Redis, Spring AMQP, MinIO Java client, JUnit 5/Mockito, React + TypeScript, Bash/Python preflight scripts.

## Global Constraints

- Do not call OpenAI or any paid provider.
- Do not upload, download, create, or delete media objects during live checks.
- Do not expose passwords, access keys, secret keys, demo tokens, raw local paths, object keys, provider payloads, or stack traces.
- Every probe must fail closed for its own dependency but not prevent the endpoint from returning other probe results.
- Use short, bounded checks and safe summaries suitable for browser display and terminal preflight.

---

### Task 1: Backend Live Check Contract And Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/enums/RuntimeProbeStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeProbeResultVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeLiveCheckSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/RuntimeLiveCheckService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeLiveCheckServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/controller/RuntimeDependencyController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeLiveCheckServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Produces: `RuntimeLiveCheckSummaryVo(boolean healthy, Instant checkedAt, Map<String, RuntimeProbeResultVo> checks)`.
- Produces: `RuntimeProbeResultVo(RuntimeProbeStatus status, long latencyMs, String message)`.
- Produces: `GET /api/runtime/live-checks`.

- [x] Define `RuntimeProbeStatus` with `UP`, `DOWN`, and `SKIPPED`.
- [x] Implement probe helpers that catch all dependency-specific exceptions and return safe messages:
  - database: `SELECT 1`;
  - Redis: `StringRedisTemplate.getConnectionFactory().getConnection().ping()`;
  - RabbitMQ: `ConnectionFactory.createConnection().close()`;
  - MinIO: `MinioClient.bucketExists(BucketExistsArgs.builder().bucket(configuredBucket).build())`;
  - FFmpeg: `ProcessBuilder(binaryPath, "-version")`, timeout before killing the process.
- [x] For disabled or unconfigured optional features, return `SKIPPED` only when the dependency is not required for the current demo mode; keep database, Redis, RabbitMQ, MinIO, and FFmpeg as active checks for Docker demo readiness.
- [x] `healthy` is true only when no check is `DOWN`; `SKIPPED` does not fail health.
- [x] Use messages such as `Database query succeeded`, `Redis ping succeeded`, `RabbitMQ connection opened`, `MinIO bucket is reachable`, `FFmpeg binary responded`, `Redis ping failed`, and never include exception class names or raw exception messages.
- [x] Add controller method:

```java
@GetMapping("/live-checks")
public RuntimeLiveCheckSummaryVo getLiveChecks() {
    return liveCheckService.check();
}
```

- [x] Controller tests assert the endpoint returns `healthy`, `checkedAt`, and named checks, and that private demo token protection still applies to `/api/runtime/live-checks`.

### Task 2: Storage Probe Boundary

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/impl/MinioObjectStorageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/storage/service/MinioObjectStorageServiceTests.java`

**Interfaces:**
- Produces: package-visible or public method `boolean bucketExistsForHealthCheck()` on `MinioObjectStorageServiceImpl`, or a small `ObjectStorageHealthProbe` bean if keeping the storage service interface clean is clearer.

- [x] Add a non-mutating bucket-exists method that reuses the configured bucket and MinIO client.
- [x] Test that it calls `bucketExists` with the configured bucket.
- [x] Test that failures are wrapped or converted by the live-check service into a safe `DOWN` result without leaking root exception text.

### Task 3: Frontend Live Check Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Test: `frontend/src/api/linguaframeApi.test.ts`

**Interfaces:**
- Produces: TypeScript types `RuntimeLiveCheckSummary`, `RuntimeProbeResult`, and `RuntimeProbeStatus`.
- Produces: API method `getRuntimeLiveChecks()`.

- [x] Fetch live checks alongside runtime dependencies on app load and refresh.
- [x] Add a compact `Live checks` section near `Demo readiness` showing overall `Ready` or `Blocked` and one row each for database, Redis, RabbitMQ, MinIO, and FFmpeg.
- [x] Render `UP`, `DOWN`, and `SKIPPED` states with existing restrained UI patterns; do not add a marketing card or explanatory feature text.
- [x] If live checks fail to load, show a short error line while leaving upload controls usable.
- [x] App tests cover all-up rendering, a down dependency rendering `Blocked`, and error fallback.

### Task 4: Preflight Integration And Documentation

**Files:**
- Modify: `scripts/demo/private-demo-preflight.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: `GET /api/runtime/live-checks`.
- Produces: terminal preflight output lines such as `database=UP`, `redis=UP`, `rabbitmq=UP`, `minio=UP`, `ffmpeg=UP`.

- [x] After runtime contract freshness passes, private preflight fetches `/api/runtime/live-checks` with the same optional demo token.
- [x] The script fails before media upload if any required check is `DOWN`, printing only the dependency name and safe message.
- [x] Document that `/api/runtime/dependencies` is configuration-derived, while `/api/runtime/live-checks` performs bounded active probes.
- [x] Update smoke checklist expected browser behavior and preflight behavior.
- [x] Mark the roadmap under private demo hardening or demo readiness as implemented.
- [x] Record initial validation and post-merge validation in `docs/progress/execution-log.md`.

### Task 5: Verification And Integration

**Files:**
- No extra source files beyond tasks above unless tests expose a missing fixture.

- [x] Run focused backend tests:

```bash
mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,MinioObjectStorageServiceTests test
```

- [x] Run full backend tests:

```bash
mvn -pl LinguaFrame test
```

- [x] Run frontend API and App checks:

```bash
cd frontend
npm run test:run -- App linguaFrameApi
npm run build
```

- [x] Validate scripts and Compose:

```bash
bash -n scripts/demo/private-demo-preflight.sh scripts/demo/start-local-demo.sh
docker compose --env-file .env.example config --quiet
git diff --check
```

- [x] Commit the feature on a branch named `codex-runtime-live-dependency-checks-mvp`, merge it back to `main` after verification, and record post-merge verification in `docs/progress/execution-log.md`.
