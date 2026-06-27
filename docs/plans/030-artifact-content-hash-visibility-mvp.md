# Artifact Content Hash Visibility MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record a stable SHA-256 content hash for every generated job artifact and make it visible through backend APIs, the React demo artifact table, and project documentation.

**Architecture:** `JobArtifactServiceImpl` already receives artifact bytes before storing them, so it is the right boundary to compute `contentSha256`. A Flyway migration adds the hash to `job_artifacts`; repository/entity/VO layers carry it through `GET /api/jobs/{jobId}/artifacts`, and the frontend renders a short hash in the artifact table. This creates the cache-aware foundation without skipping provider calls or implementing result reuse yet.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, AssertJ, React, Vite, TypeScript, Vitest.

## Global Constraints

- This feature must be a complete, user-visible feature slice: database, backend service/API, frontend display, tests, docs, validation, and merge back to `main`.
- Default demo behavior must remain unchanged except for the extra artifact hash field.
- Use SHA-256 over the exact artifact bytes passed to `CreateJobArtifactCommand.content()`.
- Store hashes as lowercase hexadecimal strings.
- Do not implement cache hits, provider skipping, or cache result reuse in this slice.
- Do not log or expose raw media paths or secrets.

---

## Task 1: Persist Artifact Content Hashes

**Files:**

- Create: `LinguaFrame/src/main/resources/db/migration/V10__add_job_artifact_content_hash.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobArtifactRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobArtifactVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobArtifactRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/JobArtifactRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobArtifactServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**

- `JobArtifactRecord` gains `String contentSha256`.
- `JobArtifactVo` gains `String contentSha256`.
- `JobArtifactServiceImpl.createArtifact(...)` computes the hash from `command.content()`.

- [x] **Step 1: Add failing repository/API/service tests**
  - In `JobArtifactRepositoryTests`, construct `JobArtifactRecord` with `contentSha256="abc123"` and assert `findById` and `findByJobId` return it.
  - In `JobArtifactServiceTests`, assert creating an artifact from `{"jobId":"job-artifact-service-1"}` returns and saves a lowercase 64-character SHA-256 hash.
  - In `LocalizationJobControllerTests.listsArtifactsForLocalizationJob`, assert JSON path `$[0].contentSha256` exists and equals the test value.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,JobArtifactServiceTests,LocalizationJobControllerTests test
```

Expected: compilation/test failure because `contentSha256` is not yet part of the record/VO/schema.

- [x] **Step 3: Add Flyway migration**

```sql
ALTER TABLE job_artifacts
    ADD COLUMN content_sha256 VARCHAR(64) NOT NULL DEFAULT '';
```

- [x] **Step 4: Update artifact entity, VO, repository, and service**
  - Add `contentSha256` to record constructors and repository SQL insert/select/map code.
  - Add a private `sha256Hex(byte[] content)` helper in `JobArtifactServiceImpl`.
  - Use `MessageDigest.getInstance("SHA-256")`, format each byte as two lowercase hex chars, and throw `IllegalStateException("SHA-256 digest algorithm is unavailable.")` only if the JVM lacks SHA-256.

- [x] **Step 5: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,JobArtifactServiceTests,LocalizationJobControllerTests test
```

Expected: all selected tests pass.

---

## Task 2: Show Hashes In The React Artifact Table

**Files:**

- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**

- `JobArtifact` gains `contentSha256: string`.
- Artifact table gains a `SHA-256` column.
- UI shows a compact value such as the first 12 characters while preserving the full hash in `title`.

- [x] **Step 1: Add failing frontend tests**
  - Update `linguaframeApi.test.ts` artifact fixture to include `contentSha256`.
  - In `App.test.tsx`, assert the artifact section shows the short hash prefix, for example `0123456789ab`.

- [x] **Step 2: Run red frontend tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: App test fails because the hash column is not rendered.

- [x] **Step 3: Implement frontend type and table rendering**
  - Add `contentSha256` to `JobArtifact`.
  - Add a `SHA-256` header between `Size` and `Download`.
  - Render `artifact.contentSha256.slice(0, 12)` with `title={artifact.contentSha256}`.
  - If the value is empty for old data, render `-`.

- [x] **Step 4: Run green frontend tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected frontend tests pass.

---

## Task 3: Document Hash Semantics And Cache Boundary

**Files:**

- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/030-artifact-content-hash-visibility-mvp.md`

**Interfaces:**

- Documentation must state that artifact hashes are stable content fingerprints, not cache hits.
- Roadmap should mark content hash foundation as implemented while keeping real cache reuse as future work.

- [x] **Step 1: Update docs**
  - README: mention artifact list includes `contentSha256` and frontend shows a short SHA-256 prefix.
  - Product spec: add that generated artifacts expose SHA-256 fingerprints for reproducibility and future duplicate-work detection.
  - Roadmap Phase 12: mark content hash foundation as implemented; leave translation cache keys and cache-hit audit events as future work.
  - Decisions: record why hashes are stored at artifact creation rather than provider layers.
  - Execution log: record red/green and final validation commands.

- [x] **Step 2: Run docs sanity checks**

```bash
rg -n "contentSha256|SHA-256|cache" README.md docs/product/spec.md docs/product/roadmap.md docs/progress/decisions.md docs/progress/execution-log.md
git diff --check
```

Expected: docs contain the new behavior and no whitespace errors exist.

---

## Task 4: Full Validation And Merge

**Files:**

- All files touched by Tasks 1-3.

- [x] **Step 1: Run focused backend tests**

```bash
mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,JobArtifactServiceTests,LocalizationJobControllerTests test
```

- [x] **Step 2: Run focused frontend tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

- [x] **Step 3: Run full frontend validation**

```bash
cd frontend && npm run test:run
cd frontend && npm run build
```

- [x] **Step 4: Run full backend validation**

```bash
mvn -pl LinguaFrame test
```

- [x] **Step 5: Verify Docker config**

```bash
docker compose --env-file .env.example config
```

- [ ] **Step 6: Commit, merge, and verify on `main`**
  - Work on branch `artifact-content-hash-visibility-mvp`.
  - Commit as `Add artifact content hash visibility`.
  - Merge back to `main`.
  - Re-run focused backend/frontend tests, full backend tests, frontend build, Docker config, and `git diff --check HEAD`.
  - Record post-merge validation in `docs/progress/execution-log.md` and commit it.
  - Delete the local feature branch after successful merge.

---

## Completion Criteria

- [x] Artifact records persist a lowercase 64-character SHA-256 content hash.
- [x] `GET /api/jobs/{jobId}/artifacts` returns `contentSha256`.
- [x] The React artifact table shows a short hash prefix with the full hash available on hover.
- [x] Backend and frontend tests cover the new field.
- [x] README/product docs explain the hash and explicitly defer cache reuse.
- [x] Full validation passes.
- [ ] Feature branch is merged back to `main`.
