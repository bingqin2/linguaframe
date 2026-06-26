# Upload Duration Limit MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a real 5-minute upload duration limit while guaranteeing that accepted videos are processed as complete files, not clipped samples.

**Architecture:** Add an FFprobe-backed media duration probe behind a small `MediaDurationProbeService` boundary, call it from upload validation after cheap file type and size checks, and persist the detected duration on the `videos` table. Validation rejects videos longer than `linguaframe.media.max-duration-seconds`; accepted uploads keep the original source object unchanged so the worker pipeline processes the full file.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, MockMvc, Bash demo scripts, FFmpeg/FFprobe, Docker Compose, Markdown docs.

## Global Constraints

- Use feature branch `upload-duration-limit-mvp`.
- The default maximum duration is exactly `300` seconds.
- Duration limit is an intake gate only: do not add trimming, clipping, transcoding, `ffmpeg -t`, or generated preview slices in this feature.
- Accepted files must be stored and queued using the original uploaded bytes.
- Do not log or expose local media paths, API keys, object storage credentials, database passwords, or raw FFprobe stderr beyond a safe bounded summary.
- Keep validation deterministic in unit tests by injecting fake duration probes; do not require real FFprobe for ordinary service tests.

---

## Design Summary

Recommended approach: add a `MediaDurationProbeService` with an FFprobe implementation and call it from `MediaUploadValidationServiceImpl`. This keeps upload validation as the single intake gate and gives the service one explicit reason to reject a file before storage or model calls.

Alternative 1, validate duration inside the worker after storage: easier to wire because source files already exist on disk/object storage, but it wastes storage and queue work and violates the requirement to reject before expensive processing.

Alternative 2, rely only on file size: cheaper and avoids FFprobe, but a highly compressed long video could pass and then consume OpenAI/FFmpeg runtime unexpectedly.

## Files To Create Or Modify

- Create `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MediaDurationProbeCommand.java`: probe input record with filename and temp file path.
- Create `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MediaDurationProbeResult.java`: duration result in milliseconds/seconds.
- Create `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaDurationProbeService.java`: duration probe boundary.
- Create `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfprobeMediaDurationProbeService.java`: FFprobe process implementation.
- Modify `MediaUploadValidationCode`, `MediaUploadValidationVo`, `MediaUploadValidationServiceImpl`, and tests: add duration metadata and rejection.
- Modify `LinguaFrameProperties`, `application.yaml`, `application-local.yaml`, `.env.example`, and `README.md`: default limit becomes 300 seconds.
- Create `LinguaFrame/src/main/resources/db/migration/V9__add_video_duration_seconds.sql`: nullable duration metadata for existing videos.
- Modify `VideoRecord`, `VideoRepository`, `MediaUploadVo`, `MediaUploadDetailVo`, `MediaUploadServiceImpl`, controller tests, and repository tests: persist and expose duration.
- Modify `docs/agent/docker-e2e-demo.md`, `docs/progress/execution-log.md`, and optionally `docs/product/roadmap.md`: document the five-minute gate and full-file processing guarantee.

## Task 1: Update Configuration Default To Five Minutes

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `.env.example`
- Modify: `README.md`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- Produces: `LinguaFrameProperties.Media#getMaxDurationSeconds()` defaulting to `300`.

- [x] Change `LinguaFrameProperties.Media#maxDurationSeconds` default from `120` to `300`.
- [x] Change `application.yaml` `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS` fallback from `120` to `300`.
- [x] Change `application-local.yaml` `linguaframe.media.max-duration-seconds` from `120` to `300`.
- [x] Change `.env.example` `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS=300`.
- [x] Update `README.md` runtime configuration or local demo copy to say the default upload duration limit is 300 seconds / 5 minutes.
- [x] Update `LinguaFramePropertiesTests#bindsDefaultRuntimeProperties` to expect `300`.
- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: test passes with default `maxDurationSeconds=300`.

## Task 2: Add FFprobe Duration Probe Boundary

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MediaDurationProbeCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MediaDurationProbeResult.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaDurationProbeService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfprobeMediaDurationProbeService.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfprobeMediaDurationProbeServiceTests.java`

**Interfaces:**
- Produces: `MediaDurationProbeService#probeDuration(MediaDurationProbeCommand command): MediaDurationProbeResult`.
- Produces: `MediaDurationProbeResult#durationSecondsRoundedUp(): int`.

- [x] Add a failing unit test that injects a fake `CommandRunner` returning stdout `299.001\n` and expects rounded-up duration `300`.
- [x] Add a failing unit test that injects stdout `300.001\n` and expects rounded-up duration `301`.
- [x] Add a failing unit test that injects a timeout and expects `IllegalStateException("FFprobe duration probe timed out.")`.
- [x] Add a failing unit test that injects non-zero exit and long stderr and expects message prefix `FFprobe duration probe failed:` with a bounded safe summary.
- [x] Implement records:

```java
public record MediaDurationProbeCommand(String filename, Path inputVideoPath) {
}

public record MediaDurationProbeResult(double durationSeconds) {
    public int durationSecondsRoundedUp() {
        return (int) Math.ceil(durationSeconds);
    }
}
```

- [x] Implement FFprobe command:

```text
ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 <input>
```

- [x] Use `properties.getFfmpeg().getBinaryPath()` to derive the probe binary by replacing a trailing `ffmpeg` executable name with `ffprobe`; if the configured binary does not end in `ffmpeg`, use `ffprobe`.
- [x] Use a 30-second probe timeout constant.
- [x] Read stdout/stderr after process completion and truncate stderr summaries to 240 normalized characters.
- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test
```

Expected: probe tests pass without requiring real FFprobe.

## Task 3: Enforce Duration During Upload Validation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadValidationCode.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadValidationVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadValidationServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadValidationServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Interfaces:**
- Consumes: `MediaDurationProbeService#probeDuration(...)`.
- Produces: `MediaUploadValidationVo#durationSeconds(): Integer`.
- Produces: `MediaUploadValidationCode.DURATION_TOO_LONG`.

- [x] Extend `MediaUploadValidationCode` with `DURATION_TOO_LONG`.
- [x] Add nullable `Integer durationSeconds` to `MediaUploadValidationVo` before `maxDurationSeconds` so validation responses show detected duration when available.
- [x] Add tests proving cheap failures do not call the duration probe:

```java
assertThat(durationProbe.probeCalls).isZero();
```

for missing file, empty file, unsupported content type, and file too large.

- [x] Add a test with configured max `300` and fake probe result `300.0`, expecting `READY` and `durationSeconds=300`.
- [x] Add a test with fake probe result `301.0`, expecting `valid=false`, `code=DURATION_TOO_LONG`, message `The uploaded video exceeds the 300 second duration limit.`, and no upload storage in `MediaUploadServiceTests`.
- [x] Add controller assertions for `/api/media/uploads/validate`: successful video includes `durationSeconds`, and too-long video returns HTTP 400 with `DURATION_TOO_LONG`.
- [x] In `MediaUploadValidationServiceImpl`, copy the multipart file to a temporary file for probing, call `MediaDurationProbeService`, delete the temp file in `finally`, and never mutate or replace the original multipart stream used later for storage.
- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadControllerTests,MediaUploadServiceTests test
```

Expected: validation, controller, and service tests pass.

## Task 4: Persist And Expose Video Duration Metadata

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V9__add_video_duration_seconds.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/entity/VideoRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadDetailVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: repository/controller/service tests.

**Interfaces:**
- Consumes: `MediaUploadValidationVo#durationSeconds()`.
- Produces: `VideoRecord#durationSeconds(): Integer`.
- Produces: `MediaUploadVo#durationSeconds(): Integer`.
- Produces: `MediaUploadDetailVo#durationSeconds(): Integer`.

- [x] Add migration:

```sql
ALTER TABLE videos
    ADD COLUMN duration_seconds INT NULL;
```

- [x] Update `UploadIntakeSchemaTests` to assert the `videos.duration_seconds` column exists.
- [x] Update `VideoRecord`, `VideoRepository#save`, and `VideoRepository#findById` to handle nullable duration seconds.
- [x] Update `VideoRepositoryTests#savesAndFindsVideoRecord` to include `300`.
- [x] Update `MediaUploadServiceImpl` to persist `validation.durationSeconds()` and return it in upload/detail VOs.
- [x] Update `MediaUploadControllerTests#createUploadAndQueuedJob` to expect `$.durationSeconds`.
- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=UploadIntakeSchemaTests,VideoRepositoryTests,MediaUploadServiceTests,MediaUploadControllerTests test
```

Expected: schema, repository, service, and controller tests pass.

## Task 5: Document The Complete-File Processing Contract

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: operator-facing documentation that the five-minute limit rejects oversized videos and does not trim accepted videos.

- [x] Update `README.md` to state: default upload duration limit is 5 minutes and accepted files are processed in full.
- [x] Update `docs/product/spec.md` upload requirement to say duration validation rejects files above the configured limit before expensive work.
- [x] Update `docs/agent/docker-e2e-demo.md` full-video section to say the Tears of Steel 1:50 sample is under the 5-minute limit and is uploaded as a complete file.
- [x] Append `docs/progress/execution-log.md` evidence:

```markdown
## 2026-06-27

Work:

- Enforced real upload duration validation with FFprobe.
- Changed the default media duration limit to 300 seconds.
- Persisted detected video duration metadata.
- Documented that LinguaFrame rejects over-limit videos instead of clipping accepted videos.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` passed.
- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test` passed.
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadControllerTests,MediaUploadServiceTests test` passed.
- `mvn -pl LinguaFrame -Dtest=UploadIntakeSchemaTests,VideoRepositoryTests,MediaUploadServiceTests,MediaUploadControllerTests test` passed.
```

- [x] Run:

```bash
rg -n "300 seconds|5 minutes|durationSeconds|DURATION_TOO_LONG|complete file|processed in full|FFprobe" README.md docs/agent/docker-e2e-demo.md docs/product/spec.md docs/progress/execution-log.md
```

Expected: docs describe both the limit and the no-clipping contract.

## Task 6: Final Verification And Merge

**Files:**
- Verify all files changed in Tasks 1-5.

**Verification Commands:**
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test`
- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test`
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadControllerTests,MediaUploadServiceTests test`
- `mvn -pl LinguaFrame -Dtest=UploadIntakeSchemaTests,VideoRepositoryTests,MediaUploadServiceTests,MediaUploadControllerTests test`
- `docker compose --env-file .env.example config`
- `git diff --check`
- `git status --short`

- [x] Confirm no media files, generated artifacts, `.env`, or credentials are staged.
- [x] Commit: `Enforce upload duration limit`.
- [x] Merge branch `upload-duration-limit-mvp` back to `main` after verification.

## Completion Checklist

- [x] Default media duration limit is 300 seconds in code, YAML, `.env.example`, tests, and docs.
- [x] Upload validation rejects videos longer than the configured limit before storage and queue dispatch.
- [x] Accepted uploads store the original file bytes and process the complete video; no clipping or trimming is introduced.
- [x] Detected duration is returned by validation/upload/detail APIs and persisted on `videos.duration_seconds`.
- [x] Unit/controller/repository tests cover accepted duration, rejected duration, cheap-validation short-circuiting, and duration persistence.
- [x] Validation evidence is recorded in `docs/progress/execution-log.md`.
- [x] Feature branch is merged back to `main` after verified implementation.
