# Media Upload Intake MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build the first complete Phase 1 upload slice: validate a video, store it through object storage, create durable video and localization job records, and expose read APIs for the created upload and job.

**Architecture:** Add a focused media upload intake path inside the existing Spring Boot backend. The controller stays thin, validation is isolated, storage is hidden behind an object-storage service, and durable state is written through JDBC repositories backed by Flyway migrations. This slice does not start async processing, publish RabbitMQ messages, inspect duration with FFmpeg, or call OpenAI.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring Web multipart support, Spring Validation, Spring JDBC, Flyway, MySQL driver, H2 for tests, MinIO Java client, Spring Boot Test, MockMvc, AssertJ, Maven.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `media-upload-intake-mvp`.
- Keep media code under package `com.linguaframe.media`.
- Keep job read/status code under package `com.linguaframe.job`.
- Keep storage code under package `com.linguaframe.storage`.
- Add database access only for video and localization job intake records.
- Do not add RabbitMQ publishing, worker behavior, Redis cache usage, FFmpeg duration inspection, OpenAI calls, subtitles, TTS, frontend, authentication, or retry behavior in this slice.
- Store only sanitized object keys and safe upload metadata; never store raw filesystem paths from uploaded filenames.
- Return stable API codes for validation and upload failures.
- Existing tests must keep passing from the repository root.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature should produce these working APIs:

- `POST /api/media/uploads/validate`
  - Validates a multipart `file`.
  - Returns `200` when the file is acceptable.
  - Returns `400` with a stable validation code when invalid.
- `POST /api/media/uploads`
  - Validates a multipart `file`.
  - Stores the source file through `ObjectStorageService`.
  - Inserts one `videos` row with status `UPLOADED`.
  - Inserts one `localization_jobs` row with status `QUEUED`.
  - Returns `201` with `videoId`, `jobId`, upload metadata, status, object key, and timestamps.
- `GET /api/media/uploads/{videoId}`
  - Returns durable upload metadata.
  - Returns `404` when the video id is unknown.
- `GET /api/jobs/{jobId}`
  - Returns the queued localization job created by upload intake.
  - Returns `404` when the job id is unknown.

## File Structure

- Modify: `LinguaFrame/pom.xml`
  - Add JDBC, Flyway, MySQL, H2 test, and MinIO dependencies.
- Modify: `LinguaFrame/src/main/resources/application.yaml`
  - Add datasource, Flyway, multipart, and env-backed media limits.
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
  - Align Docker datasource URL with Compose service names.
- Create: `LinguaFrame/src/test/resources/application-test.yaml`
  - Use H2 in MySQL compatibility mode for tests.
- Create: `LinguaFrame/src/main/resources/db/migration/V1__create_upload_intake_tables.sql`
  - Create `videos` and `localization_jobs`.
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadValidationCode.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadValidationVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadDetailVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/entity/VideoRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/LocalizationJobRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/storage/domain/bo/StoreObjectCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/storage/domain/bo/StoredObjectBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/storage/service/ObjectStorageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/storage/service/impl/MinioObjectStorageServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadValidationService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadValidationServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobQueryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/error/ApiErrorVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/error/ApiExceptionHandler.java`
- Modify: existing Spring Boot tests to use `@ActiveProfiles("test")` where full context loading is used.
- Add tests for validation, repositories, upload service orchestration, controllers, OpenAPI visibility, and application context.
- Modify: `.env.example`, `docker-compose.yml`, `README.md`, and `docs/progress/execution-log.md`.

## Task 1: Runtime Dependencies And Test Datasource

**Files:**
- Modify: `LinguaFrame/pom.xml`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Create: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: existing `@SpringBootTest` tests to add `@ActiveProfiles("test")`

**Interfaces:**
- Produces a datasource-backed application context that uses MySQL in local/docker profiles and H2 in tests.

- [x] **Step 1: Create feature branch**

Run:

```bash
git switch -c media-upload-intake-mvp
```

Expected: branch switches to `media-upload-intake-mvp`.

- [x] **Step 2: Run baseline verification**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: current suite passes before this feature.

- [x] **Step 3: Add failing context test for test profile**

Create or update a focused test so the application starts with `@ActiveProfiles("test")` after JDBC/Flyway dependencies are added. The expected RED before config work is that there is no upload table and no test datasource profile yet.

- [x] **Step 4: Add dependencies**

Add these dependencies to `LinguaFrame/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.17</version>
</dependency>

<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [x] **Step 5: Add datasource and multipart configuration**

Update `application.yaml` so `spring` contains:

```yaml
spring:
  application:
    name: LinguaFrame
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:linguaframe}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    username: ${MYSQL_USER:linguaframe}
    password: ${MYSQL_PASSWORD:linguaframe_dev_password}
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
  servlet:
    multipart:
      max-file-size: ${SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE:100MB}
      max-request-size: ${SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE:100MB}
```

Update `linguaframe.media` values to read from env defaults:

```yaml
  media:
    max-file-size-mb: ${LINGUAFRAME_MEDIA_MAX_FILE_SIZE_MB:100}
    max-duration-seconds: ${LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS:120}
```

- [x] **Step 6: Add docker datasource alignment**

Update `application-docker.yaml` so `spring` contains the same datasource block, but defaults host to `mysql`:

```yaml
spring:
  application:
    name: LinguaFrame
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:linguaframe}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
    username: ${MYSQL_USER:linguaframe}
    password: ${MYSQL_PASSWORD:linguaframe_dev_password}
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
```

- [x] **Step 7: Add test profile datasource**

Create `application-test.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:linguaframe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password:
    driver-class-name: org.h2.Driver
  flyway:
    enabled: true
```

- [x] **Step 8: Run context tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFrameApplicationTests,ActuatorHealthTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests
```

Expected: context tests pass with the test profile.

## Task 2: Durable Upload Intake Schema And Repositories

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V1__create_upload_intake_tables.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStatus.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/entity/VideoRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/LocalizationJobRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Create: repository integration tests under `LinguaFrame/src/test/java/com/linguaframe/...`

**Interfaces:**
- Produces `VideoRepository#save(VideoRecord)`, `VideoRepository#findById(String)`, `LocalizationJobRepository#save(LocalizationJobRecord)`, and `LocalizationJobRepository#findById(String)`.

- [x] **Step 1: Write failing repository tests**

Add integration tests with `@SpringBootTest` and `@ActiveProfiles("test")` that save and reload one video and one localization job.

- [x] **Step 2: Add Flyway migration**

Create `V1__create_upload_intake_tables.sql`:

```sql
CREATE TABLE videos (
    id VARCHAR(36) PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    source_object_key VARCHAR(512) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE localization_jobs (
    id VARCHAR(36) PRIMARY KEY,
    video_id VARCHAR(36) NOT NULL,
    target_language VARCHAR(32) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_localization_jobs_video_id FOREIGN KEY (video_id) REFERENCES videos(id)
);
```

- [x] **Step 3: Add records and repositories**

Use Java records for `VideoRecord` and `LocalizationJobRecord`. Implement repositories with `JdbcClient`, constructor injection, explicit SQL, and `Optional<T>` lookup methods.

- [x] **Step 4: Verify repository tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='*RepositoryTests'
```

Expected: repository tests pass against H2 using Flyway-created tables.

## Task 3: Storage Boundary And Upload Orchestration

**Files:**
- Create storage BOs and service interface.
- Create MinIO object storage service implementation.
- Create media validation service and upload service.
- Create service tests with a fake object storage service.

**Interfaces:**
- Produces `MediaUploadService#createUpload(MultipartFile file, String targetLanguage): MediaUploadVo`.
- Consumes `ObjectStorageService#store(StoreObjectCommand command): StoredObjectBo`.

- [x] **Step 1: Write failing validation and upload service tests**

Cover these cases:

- valid video returns `READY`.
- missing file returns `MISSING_FILE`.
- empty file returns `EMPTY_FILE`.
- unsupported content type returns `UNSUPPORTED_CONTENT_TYPE`.
- oversized file returns `FILE_TOO_LARGE`.
- successful upload stores the object, creates a `videos` row, creates a `localization_jobs` row, and returns `UPLOADED` plus `QUEUED`.
- storage failure returns a safe upload failure without leaking credentials or stack traces.

- [x] **Step 2: Add validation enum and VO**

Use stable codes:

```java
READY,
MISSING_FILE,
EMPTY_FILE,
UNSUPPORTED_CONTENT_TYPE,
FILE_TOO_LARGE
```

The validation VO must include:

```java
boolean valid;
MediaUploadValidationCode code;
String message;
String filename;
String contentType;
long fileSizeBytes;
long maxFileSizeBytes;
int maxDurationSeconds;
List<String> supportedContentTypes;
```

- [x] **Step 3: Add storage command and result BOs**

`StoreObjectCommand` fields:

```java
String objectKey;
String contentType;
long sizeBytes;
InputStream inputStream;
```

`StoredObjectBo` fields:

```java
String bucket;
String objectKey;
long sizeBytes;
```

- [x] **Step 4: Add MinIO storage implementation**

`MinioObjectStorageServiceImpl` should:

- Build a `MinioClient` from `LinguaFrameProperties.Storage`.
- Ensure the configured bucket exists before storing.
- Store with `PutObjectArgs`.
- Return `StoredObjectBo`.
- Convert MinIO exceptions to `IllegalStateException` with safe messages.

- [x] **Step 5: Add upload orchestration**

`MediaUploadServiceImpl#createUpload` should:

- Validate the file.
- Generate `videoId` and `jobId` UUID strings.
- Sanitize the filename to a basename.
- Build object key `source-videos/{videoId}/{filename}`.
- Store the object through `ObjectStorageService`.
- Save `VideoRecord` with status `UPLOADED`.
- Save `LocalizationJobRecord` with status `QUEUED`.
- Return `MediaUploadVo`.

- [x] **Step 6: Verify service tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUpload*ServiceTests'
```

Expected: validation and orchestration service tests pass.

## Task 4: Upload And Read APIs

**Files:**
- Create `MediaUploadController.java`
- Create `LocalizationJobController.java`
- Create API error objects and exception handler.
- Create MockMvc controller tests.
- Modify `OpenApiDocumentationTests.java`.

**Interfaces:**
- Produces `POST /api/media/uploads/validate`, `POST /api/media/uploads`, `GET /api/media/uploads/{videoId}`, and `GET /api/jobs/{jobId}`.

- [x] **Step 1: Write failing controller tests**

Use `MockMvc` with `@SpringBootTest`, `@AutoConfigureMockMvc`, and `@ActiveProfiles("test")`.

Cover:

- `POST /api/media/uploads/validate` returns `READY`.
- `POST /api/media/uploads` returns `201` and includes `videoId`, `jobId`, `status=UPLOADED`, `jobStatus=QUEUED`.
- invalid upload returns `400` with validation code.
- `GET /api/media/uploads/{videoId}` returns the saved upload.
- `GET /api/jobs/{jobId}` returns the queued job.
- unknown ids return `404` with safe `NOT_FOUND` error.

- [x] **Step 2: Add controllers and exception handler**

Controllers should depend on service interfaces only. Use explicit VO responses and `ResponseEntity` for status codes.

`ApiErrorVo` should include:

```java
String code;
String message;
```

Use codes:

```text
NOT_FOUND
UPLOAD_VALIDATION_FAILED
UPLOAD_STORAGE_FAILED
```

- [x] **Step 3: Add OpenAPI path assertions**

Update `OpenApiDocumentationTests` to assert these paths exist:

```text
/api/media/uploads/validate
/api/media/uploads
/api/media/uploads/{videoId}
/api/jobs/{jobId}
```

- [x] **Step 4: Verify API tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUpload*ControllerTests,LocalizationJobControllerTests,OpenApiDocumentationTests'
```

Expected: API tests pass and OpenAPI docs include all upload intake paths.

## Task 5: Runtime Configuration And Documentation

**Files:**
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Documents how to configure and call the upload intake APIs.

- [x] **Step 1: Update `.env.example`**

Add upload limits near backend settings:

```dotenv
SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=100MB
SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=100MB
LINGUAFRAME_MEDIA_MAX_FILE_SIZE_MB=100
LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS=120
```

- [x] **Step 2: Update `docker-compose.yml`**

Pass the same environment variables to `linguaframe-backend`.

- [x] **Step 3: Update README**

Document:

- `POST /api/media/uploads/validate`
- `POST /api/media/uploads`
- `GET /api/media/uploads/{videoId}`
- `GET /api/jobs/{jobId}`
- The upload command:

```bash
curl -F "file=@sample.mp4" -F "targetLanguage=zh-CN" http://localhost:8080/api/media/uploads
```

State that this slice stores source video and creates durable queued job metadata, but does not start processing.

- [x] **Step 4: Append execution log**

Record RED/GREEN evidence, final verification commands, and the explicit non-goals: no RabbitMQ publish, no worker, no FFmpeg, no OpenAI.

## Task 6: Final Verification, Commit, And Merge

**Files:**
- Verify all files touched by Tasks 1-5.

**Interfaces:**
- Produces one complete feature commit merged back to `main`.

- [x] **Step 1: Run full validation**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
git diff --check
```

Expected: Maven, Compose config, Docker build, and whitespace checks all pass.

- [x] **Step 2: Review changed files**

Run:

```bash
git status --short
git diff --stat
git diff -- LinguaFrame/pom.xml LinguaFrame/src/main/resources LinguaFrame/src/main/java/com/linguaframe LinguaFrame/src/test/java/com/linguaframe .env.example docker-compose.yml README.md docs/progress/execution-log.md docs/plans/008-media-upload-intake-mvp.md
```

Expected: only this feature slice's files are modified or created.

- [x] **Step 3: Commit the feature**

Run:

```bash
git add LinguaFrame/pom.xml \
  LinguaFrame/src/main/resources \
  LinguaFrame/src/test/resources \
  LinguaFrame/src/main/java/com/linguaframe \
  LinguaFrame/src/test/java/com/linguaframe \
  .env.example \
  docker-compose.yml \
  README.md \
  docs/progress/execution-log.md \
  docs/plans/008-media-upload-intake-mvp.md
git commit -m "Add media upload intake MVP"
```

Expected: one commit containing the complete feature.

- [x] **Step 4: Merge back to main**

Run:

```bash
git switch main
git merge --ff-only media-upload-intake-mvp
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
git branch -d media-upload-intake-mvp
```

Expected: `main` contains the feature commit, Maven tests pass on `main`, and the feature branch is deleted.
