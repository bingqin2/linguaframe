# Source Media Fingerprint Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect repeated source videos before upload execution, persist a safe SHA-256 fingerprint for new uploads, and show reuse guidance in the backend API, frontend upload flow, and demo scripts.

**Architecture:** Add a nullable source fingerprint to `videos`, compute the fingerprint from uploaded bytes during upload validation/preflight, and query owner-scoped prior uploads with the same fingerprint. Extend the upload execution plan rather than creating a separate workflow, so the existing browser and terminal demo path can decide whether to reuse an existing completed run or intentionally upload again.

**Tech Stack:** Java 21, Spring Boot, JdbcClient, Flyway, JUnit 5, React + Vite + TypeScript, shell demo scripts.

## Global Constraints

- Never expose object storage keys, local media paths, API keys, or raw provider credentials in fingerprint or reuse responses.
- Fingerprint matching must be owner-scoped through `DemoOwnerIdentityService`; one owner must not see another owner's duplicate candidates.
- Duplicate detection is advisory for this slice. It must not block upload creation.
- Existing videos with no fingerprint remain valid and simply do not participate in duplicate matching.
- Frontend and scripts must keep the current upload execution plan flow working when the backend returns no candidates.

---

## Task 1: Persist Source Fingerprints

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V26__add_video_source_content_sha256.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/entity/VideoRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/repository/VideoRepositoryTests.java`

**Interfaces:**
- `VideoRecord.sourceContentSha256()` returns `String` or `null`.
- `VideoRepository.findRecentByOwnerIdAndSourceContentSha256(String ownerId, String sourceContentSha256, int limit)` returns newest matching videos.

- [ ] Add a Flyway migration with `source_content_sha256 VARCHAR(64)` and an index on `(owner_id, source_content_sha256, created_at)`.
- [ ] Extend `VideoRecord` with `sourceContentSha256` while preserving convenience constructors used by older tests.
- [ ] Update insert/select mapping in `VideoRepository`.
- [ ] Add repository tests proving save/find includes the fingerprint and owner-scoped duplicate lookup excludes other owners.
- [ ] Run `mvn -pl LinguaFrame -Dtest=VideoRepositoryTests test`.

## Task 2: Fingerprint Upload Bytes Safely

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/SourceMediaFingerprintService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/Sha256SourceMediaFingerprintService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/SourceMediaFingerprintServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceTests.java`

**Interfaces:**
- `String SourceMediaFingerprintService.sha256(MultipartFile file)` reads the multipart stream and returns lowercase hex SHA-256.
- `MediaUploadServiceImpl.createUpload(...)` stores the fingerprint on the `VideoRecord`.

- [ ] Add a streaming SHA-256 implementation using `MessageDigest` and `DigestInputStream`.
- [ ] Compute the fingerprint only after validation succeeds and before object storage.
- [ ] Keep object storage using a fresh `file.getInputStream()` so upload bytes still reach storage.
- [ ] Add unit tests for stable digest output, empty-file rejection through existing validation, and upload persistence.
- [ ] Run `mvn -pl LinguaFrame -Dtest=SourceMediaFingerprintServiceTests,MediaUploadServiceTests test`.

## Task 3: Add Reuse Evidence to Execution Plan

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseCandidateVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/UploadSourceReuseService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadSourceReuseServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadExecutionPlanVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadExecutionPlanServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadExecutionPlanServiceTests.java`

**Interfaces:**
- `UploadSourceReuseVo` includes `sourceContentSha256`, `candidateCount`, `recommendedAction`, `recommendedExistingJobId`, and `candidates`.
- Candidate rows include `videoId`, `jobId`, `originalFilename`, `durationSeconds`, `jobStatus`, `demoProfileId`, `translationStyle`, `subtitleStylePreset`, `subtitlePolishingMode`, and `createdAt`.

- [ ] Build owner-scoped candidate lookup by combining matching videos with recent jobs for each video.
- [ ] Prefer a completed candidate with matching demo profile/style settings as the recommended existing job.
- [ ] Return `UPLOAD_NEW_SOURCE` when no candidate exists, `REVIEW_EXISTING_COMPLETED_RUN` for completed matches, and `WAIT_FOR_ACTIVE_RUN` for active queued/processing matches.
- [ ] Embed the reuse object in `UploadExecutionPlanVo`; invalid files should return an empty reuse object with no hash.
- [ ] Add tests for no match, completed same-owner match, active match, and cross-owner isolation.
- [ ] Run `mvn -pl LinguaFrame -Dtest=UploadExecutionPlanServiceTests test`.

## Task 4: Surface Reuse Guidance in API, UI, and Scripts

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `scripts/demo/upload-execution-plan.sh`
- Modify: `README.md`

**Interfaces:**
- `/api/media/uploads/execution-plan` JSON contains `sourceReuse`.
- `scripts/demo/upload-execution-plan.sh` prints `sourceContentSha256`, duplicate candidate count, recommended action, and recommended job id when present.

- [ ] Update controller tests to assert `sourceReuse` fields without requiring an upload to be created.
- [ ] Extend TypeScript API types and test fixtures.
- [ ] Add a compact “Source reuse” section to the upload execution plan panel with candidate links to job detail/share/evidence endpoints.
- [ ] Update the demo script output so terminal validation can prove duplicate detection without opening the browser.
- [ ] Document how to test duplicate reuse by running the execution plan twice with the same sample video.
- [ ] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests#estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test`.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification and Merge

**Files:**
- Modify: `docs/progress/execution-log.md`

- [ ] Record the feature summary and exact validation commands in `docs/progress/execution-log.md`.
- [ ] Run `git diff --check`.
- [ ] Run the focused backend/frontend/script checks from Tasks 1-4.
- [ ] Commit the implementation as `Add source media fingerprint preflight`.
- [ ] Merge the feature branch back to `main` after validation passes.
