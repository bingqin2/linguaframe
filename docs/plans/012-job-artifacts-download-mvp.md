# Job Artifacts Download MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add durable generated artifact records and download APIs so the current Docker demo can prove upload -> queued job -> worker execution -> generated artifact -> download.

**Architecture:** Introduce a `job_artifacts` table owned by the job module, keep object bytes behind `ObjectStorageService`, and add a worker pipeline stage that writes a deterministic worker summary JSON artifact after the smoke stage succeeds. Expose artifacts through thin job controller endpoints backed by a service, so future FFmpeg audio, subtitle, TTS, and burned-video outputs can reuse the same persistence and download path.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring JDBC `JdbcClient`, Flyway, MinIO SDK, MySQL, H2 test profile, JUnit 5, MockMvc, AssertJ, Docker Compose, Bash, curl.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `job-artifacts-download-mvp`.
- Keep this slice focused on artifact metadata, storage retrieval, API access, and demo verification.
- Do not add real FFmpeg, OpenAI calls, subtitle generation, TTS, frontend UI, authentication, Redis behavior, or paid external API calls.
- Do not commit generated media, downloaded artifacts, local `.env`, runtime volumes, credentials, or Docker data.
- Keep automated tests external-service-free; unit and controller tests must not require live Docker services.
- Store only safe generated metadata in the worker summary artifact; never include raw credentials, absolute local media paths, or object storage secrets.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- A completed worker job creates one durable `WORKER_SUMMARY` artifact record.
- The summary JSON bytes are stored in object storage under a stable job-scoped key.
- `GET /api/jobs/{jobId}/artifacts` lists artifacts for a job.
- `GET /api/jobs/{jobId}/artifacts/{artifactId}/download` streams artifact bytes with content type and filename headers.
- The existing Docker E2E success script prints artifact metadata and verifies the summary artifact can be downloaded.
- Tests cover repository mapping, service behavior, controller responses, storage retrieval wiring, and worker-stage integration without live MinIO/RabbitMQ/MySQL.

## File Structure

- Create: `LinguaFrame/src/main/resources/db/migration/V4__create_job_artifacts.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobArtifactRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobArtifactVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateJobArtifactCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredObjectResourceBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobArtifactRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobArtifactService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerSummaryArtifactPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/ObjectStorageService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/impl/MinioObjectStorageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/JobArtifactRepositoryTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobArtifactServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Artifact Schema And Repository

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V4__create_job_artifacts.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/JobArtifactRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JobArtifactRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/JobArtifactRepositoryTests.java`

**Interfaces:**
- `JobArtifactRepository#save(JobArtifactRecord record)`
- `JobArtifactRepository#findById(String artifactId)`
- `JobArtifactRepository#findByJobId(String jobId)`
- `JobArtifactType.WORKER_SUMMARY`

- [x] **Step 1: Write the failing repository test**

Create `JobArtifactRepositoryTests` that saves a video, job, and artifact, then asserts `findByJobId` returns the artifact ordered by `created_at` and `findById` maps every column.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactRepositoryTests
```

Expected: fail because the table and repository do not exist.

- [x] **Step 2: Add Flyway migration**

Create `V4__create_job_artifacts.sql`:

```sql
CREATE TABLE job_artifacts (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    type VARCHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_job_artifacts_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
);

CREATE INDEX idx_job_artifacts_job_id_created
    ON job_artifacts(job_id, created_at);
```

- [x] **Step 3: Add artifact domain records and repository**

Use immutable records:

```java
public enum JobArtifactType {
    WORKER_SUMMARY
}
```

```java
public record JobArtifactRecord(
        String id,
        String jobId,
        JobArtifactType type,
        String objectKey,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
}
```

Implement `JobArtifactRepository` with `JdbcClient`, matching existing repository style.

- [x] **Step 4: Verify repository behavior**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactRepositoryTests
```

Expected: pass.

## Task 2: Storage Read Contract And Artifact Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/CreateJobArtifactCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredObjectResourceBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/JobArtifactVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobArtifactService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/ObjectStorageService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/storage/service/impl/MinioObjectStorageServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobArtifactServiceTests.java`
- Modify: existing test fakes that implement `ObjectStorageService`

**Interfaces:**
- `ObjectStorageService#open(String objectKey): InputStream`
- `JobArtifactService#createArtifact(CreateJobArtifactCommand command): JobArtifactVo`
- `JobArtifactService#listArtifacts(String jobId): List<JobArtifactVo>`
- `JobArtifactService#openArtifact(String jobId, String artifactId): StoredObjectResourceBo`

- [x] **Step 1: Write failing service tests**

Cover:

- `createArtifact` stores bytes, records metadata, and returns a `JobArtifactVo`.
- `listArtifacts` maps repository rows to API VOs.
- `openArtifact` rejects artifacts that do not belong to the requested job with `NoSuchElementException`.
- `openArtifact` returns `StoredObjectResourceBo` with stream, filename, content type, and size.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactServiceTests
```

Expected: fail because service and read contract do not exist.

- [x] **Step 2: Add service domain objects**

Use:

```java
public record CreateJobArtifactCommand(
        String jobId,
        JobArtifactType type,
        String filename,
        String contentType,
        byte[] content
) {
}
```

```java
public record StoredObjectResourceBo(
        String filename,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
```

```java
public record JobArtifactVo(
        String artifactId,
        String jobId,
        JobArtifactType type,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
}
```

- [x] **Step 3: Extend object storage with reads**

Add `InputStream open(String objectKey)` to `ObjectStorageService`.

In `MinioObjectStorageServiceImpl`, implement it with `GetObjectArgs` and wrap failures as:

```java
throw new IllegalStateException("Object storage read failed.", ex);
```

Update existing fake storage test classes with a minimal `open` implementation.

- [x] **Step 4: Implement `JobArtifactServiceImpl`**

Object keys should be deterministic and job scoped:

```text
job-artifacts/{jobId}/{artifactId}/{filename}
```

Generate ids with `UUID.randomUUID().toString()`. Store bytes through `StoreObjectCommand`, save `JobArtifactRecord`, and map to `JobArtifactVo`.

- [x] **Step 5: Verify service behavior**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactServiceTests,MediaUploadServiceTests,MediaUploadControllerTests
```

Expected: pass.

## Task 3: Worker Summary Artifact Stage

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerSummaryArtifactPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `LocalizationJobStage.ARTIFACT_SUMMARY`
- `WorkerSummaryArtifactPipelineStage` implements `LocalizationPipelineStage`

- [x] **Step 1: Write failing execution test**

Add a test with stages `WorkerSmokePipelineStage` and `WorkerSummaryArtifactPipelineStage`, then assert:

- job status becomes `COMPLETED`.
- timeline includes `ARTIFACT_SUMMARY STARTED` and `ARTIFACT_SUMMARY SUCCEEDED`.
- one `WORKER_SUMMARY` artifact exists for the job.
- downloaded JSON contains job id, video id, target language, source object key, and generated-at timestamp.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: fail because the stage does not exist.

- [x] **Step 2: Add stage enum and implementation**

Add `ARTIFACT_SUMMARY` to `LocalizationJobStage`.

Implement a stage that creates `worker-summary.json` with safe fields only:

```json
{
  "jobId": "...",
  "videoId": "...",
  "targetLanguage": "zh-CN",
  "sourceObjectKey": "source-videos/.../sample.mp4",
  "stage": "ARTIFACT_SUMMARY",
  "generatedAt": "2026-06-26T00:00:00Z"
}
```

Use `JobArtifactService#createArtifact` with type `WORKER_SUMMARY` and content type `application/json`.

- [x] **Step 3: Verify worker integration**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: pass.

## Task 4: Artifact List And Download API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/artifacts`
- `GET /api/jobs/{jobId}/artifacts/{artifactId}/download`

- [x] **Step 1: Write failing controller tests**

Add MockMvc coverage:

- artifact list returns `artifactId`, `type`, `filename`, `contentType`, `sizeBytes`, and `createdAt`.
- download returns HTTP 200, `Content-Type: application/json`, `Content-Disposition: attachment; filename="worker-summary.json"`, and the stored JSON body.
- unknown artifact or artifact from another job returns 404.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

Expected: fail because endpoints do not exist.

- [x] **Step 2: Add controller endpoints**

Inject `JobArtifactService`. Keep the controller thin:

```java
@GetMapping("/{jobId}/artifacts")
public List<JobArtifactVo> listArtifacts(@PathVariable String jobId) {
    return artifactService.listArtifacts(jobId);
}
```

For download, return `ResponseEntity<InputStreamResource>` and set content length, content type, and attachment filename.

- [x] **Step 3: Verify API behavior**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

Expected: pass.

## Task 5: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Bash helper `list_job_artifacts`
- Bash helper `download_first_artifact`

- [x] **Step 1: Extend demo helpers**

Add helper functions:

```bash
list_job_artifacts() {
  local base_url="$1"
  local job_id="$2"
  curl -fsS "$base_url/api/jobs/$job_id/artifacts"
}

download_first_artifact() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"
  local artifacts_json
  local artifact_id

  artifacts_json="$(list_job_artifacts "$base_url" "$job_id")"
  artifact_id="$(printf '%s' "$artifacts_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["artifactId"])')"
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}
```

- [x] **Step 2: Update success demo**

After job completion, list artifacts and download the first artifact to:

```text
/tmp/linguaframe-demo/worker-summary.json
```

Print the artifact JSON path and a short artifact summary.

- [x] **Step 3: Update demo docs**

Document the new artifact endpoints and the local file path produced by the script. Include the manual commands a reviewer can run:

```bash
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/artifacts"
curl -fL "http://localhost:8080/api/jobs/$JOB_ID/artifacts/$ARTIFACT_ID/download" -o /tmp/linguaframe-demo/worker-summary.json
```

- [x] **Step 4: Record verification**

Append concise evidence to `docs/progress/execution-log.md` after tests and live Docker validation complete.

## Task 6: Full Verification And Merge Readiness

**Files:**
- Modify only if verification exposes small documentation corrections.

- [x] **Step 1: Run automated tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: all tests pass.

- [x] **Step 2: Run Docker config and package checks**

Run:

```bash
docker compose --env-file .env.example config >/tmp/linguaframe-compose-config.txt
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
```

Expected: compose config, package, and backend image build all pass.

- [x] **Step 3: Run live Docker demo**

Run:

```bash
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
docker compose --env-file .env.example down
```

Expected: script reports a `COMPLETED` job and downloads `/tmp/linguaframe-demo/worker-summary.json`.

- [x] **Step 4: Commit and merge**

Run:

```bash
git status --short
git add LinguaFrame/src/main docs scripts
git commit -m "Add job artifact download MVP"
git switch main
git merge --no-ff job-artifacts-download-mvp
git branch -d job-artifacts-download-mvp
```

Expected: feature branch is merged back to `main`; working tree is clean.

## User-Run Handoff Commands

After implementation, use these commands from the repository root to run the basic feature locally through Docker:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
```

The script should print the completed job, list one artifact, and download:

```text
/tmp/linguaframe-demo/worker-summary.json
```

Clean up the running stack with:

```bash
docker compose --env-file .env.example down
```
