# Invalid Media Upload Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return a clear upload validation error for supported-content-type files whose media duration cannot be inspected, instead of surfacing FFprobe failures as generic 500 storage errors.

**Architecture:** Keep upload validation as the single intake gate. Add a validation-specific exception for duration probe failures, have the FFprobe adapter throw it for malformed or unreadable media, and translate it to `MediaUploadValidationCode.UNREADABLE_MEDIA` inside `MediaUploadValidationServiceImpl`. Storage and job creation remain unchanged and are never reached for unreadable media.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, MockMvc, AssertJ, FFprobe boundary tests.

## Global Constraints

- Use feature branch `invalid-media-upload-validation`.
- This feature only changes upload validation behavior for unreadable media; do not change duration limits, storage layout, worker execution, or generated artifacts.
- Keep cheap validation short-circuiting unchanged: missing, empty, unsupported type, and oversized files must not call the duration probe.
- Do not log or expose raw FFprobe stderr, local media paths, `.env`, OpenAI keys, object storage credentials, or database credentials.
- Accepted uploads must still store and process the complete original file; do not add clipping, trimming, transcoding, or preview slicing.

---

## Design Summary

Recommended approach: introduce `UnreadableMediaException` under the media domain and throw it from `FfprobeMediaDurationProbeService` when FFprobe cannot produce a valid duration. `MediaUploadValidationServiceImpl` catches that exception and returns an invalid `MediaUploadValidationVo` with `code=UNREADABLE_MEDIA` and a user-facing message. This keeps FFprobe details behind the provider boundary while preserving `/api/media/uploads/validate` and `/api/media/uploads` as normal 400 flows.

Alternative 1, catch every `IllegalStateException` from the duration probe in validation: smaller patch, but it would mix infrastructure failures with user-caused malformed media and could hide real server misconfiguration.

Alternative 2, add global exception handling for FFprobe failures: useful later, but weaker for upload validation because `/validate` should return the same structured validation VO as other intake failures.

## Files To Create Or Modify

- Create `LinguaFrame/src/main/java/com/linguaframe/media/domain/exception/UnreadableMediaException.java`: validation-domain exception carrying a safe message.
- Modify `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadValidationCode.java`: add `UNREADABLE_MEDIA`.
- Modify `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfprobeMediaDurationProbeService.java`: throw `UnreadableMediaException` for FFprobe non-zero exit, missing stdout, and invalid stdout.
- Modify `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadValidationServiceImpl.java`: catch `UnreadableMediaException` and return invalid validation response.
- Modify `LinguaFrame/src/test/java/com/linguaframe/media/service/FfprobeMediaDurationProbeServiceTests.java`: assert unreadable media exception behavior.
- Modify `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadValidationServiceTests.java`: assert validation VO for unreadable media and storage short-circuit.
- Modify `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`: assert `/validate` and `/uploads` return HTTP 400 for unreadable media.
- Modify `README.md`, `docs/product/spec.md`, and `docs/progress/execution-log.md`: document unreadable media behavior and validation evidence.

## Task 1: Add Domain Error And Validation Code

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/exception/UnreadableMediaException.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/enums/MediaUploadValidationCode.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadValidationServiceTests.java`

**Interfaces:**
- Produces: `UnreadableMediaException(String message)`
- Produces: `MediaUploadValidationCode.UNREADABLE_MEDIA`

- [x] Add a failing assertion in `MediaUploadValidationServiceTests`:

```java
@Test
void returnsUnreadableMediaWhenDurationCannotBeInspected() {
    durationProbeService.failure = new UnreadableMediaException("The uploaded video could not be inspected.");
    MultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

    MediaUploadValidationVo result = service.validate(file);

    assertThat(result.valid()).isFalse();
    assertThat(result.code()).isEqualTo(MediaUploadValidationCode.UNREADABLE_MEDIA);
    assertThat(result.message()).isEqualTo("The uploaded video could not be inspected.");
    assertThat(result.durationSeconds()).isNull();
    assertThat(durationProbeService.probeCalls).isEqualTo(1);
}
```

- [x] Update the fake probe helper in the same test class:

```java
private static class RecordingMediaDurationProbeService implements MediaDurationProbeService {

    private double durationSeconds;
    private RuntimeException failure;
    private int probeCalls;

    private RecordingMediaDurationProbeService(double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    @Override
    public MediaDurationProbeResult probeDuration(MediaDurationProbeCommand command) {
        probeCalls++;
        if (failure != null) {
            throw failure;
        }
        return new MediaDurationProbeResult(durationSeconds);
    }
}
```

- [x] Run the failing test:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests#returnsUnreadableMediaWhenDurationCannotBeInspected test
```

Expected: compile or test failure because `UnreadableMediaException` and/or `UNREADABLE_MEDIA` does not exist.

- [x] Create `UnreadableMediaException`:

```java
package com.linguaframe.media.domain.exception;

public class UnreadableMediaException extends RuntimeException {

    public UnreadableMediaException(String message) {
        super(message);
    }
}
```

- [x] Add `UNREADABLE_MEDIA` to `MediaUploadValidationCode` after `FILE_TOO_LARGE` and before `DURATION_TOO_LONG`.

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests#returnsUnreadableMediaWhenDurationCannotBeInspected test
```

Expected: test still fails until validation catches the new exception.

## Task 2: Convert FFprobe Parse Failures To Unreadable Media

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfprobeMediaDurationProbeService.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfprobeMediaDurationProbeServiceTests.java`

**Interfaces:**
- Consumes: `UnreadableMediaException(String message)`
- Produces: `FfprobeMediaDurationProbeService#probeDuration(...)` throws `UnreadableMediaException` for unreadable user media.

- [x] Replace or extend the existing non-zero exit test:

```java
@Test
void failsUnreadableMediaWithoutLeakingFfprobeStderr() throws IOException {
    Path input = videoFile("bad.mp4");
    RecordingCommandRunner runner = new RecordingCommandRunner(
            "",
            "failed to inspect /Users/wangbingqin/Downloads/private.mp4 with sk-test-secret",
            1,
            false
    );
    FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

    assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("bad.mp4", input)))
            .isInstanceOf(UnreadableMediaException.class)
            .hasMessage("The uploaded video could not be inspected.");
}
```

- [x] Add missing stdout and invalid stdout tests:

```java
@Test
void failsUnreadableMediaWhenDurationOutputIsMissing() throws IOException {
    Path input = videoFile("empty-output.mp4");
    RecordingCommandRunner runner = new RecordingCommandRunner("", "", 0, false);
    FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

    assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("empty-output.mp4", input)))
            .isInstanceOf(UnreadableMediaException.class)
            .hasMessage("The uploaded video could not be inspected.");
}

@Test
void failsUnreadableMediaWhenDurationOutputIsInvalid() throws IOException {
    Path input = videoFile("invalid-output.mp4");
    RecordingCommandRunner runner = new RecordingCommandRunner("not-a-duration\n", "", 0, false);
    FfprobeMediaDurationProbeService service = new FfprobeMediaDurationProbeService(properties("ffmpeg"), runner);

    assertThatThrownBy(() -> service.probeDuration(new MediaDurationProbeCommand("invalid-output.mp4", input)))
            .isInstanceOf(UnreadableMediaException.class)
            .hasMessage("The uploaded video could not be inspected.");
}
```

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test
```

Expected: tests fail until FFprobe service throws `UnreadableMediaException`.

- [x] Import `UnreadableMediaException` in `FfprobeMediaDurationProbeService`.

- [x] Change non-zero exit handling:

```java
if (result.exitCode() != 0) {
    throw new UnreadableMediaException("The uploaded video could not be inspected.");
}
```

- [x] Change `parseDurationSeconds`:

```java
private double parseDurationSeconds(String stdout) {
    if (stdout == null || stdout.isBlank()) {
        throw new UnreadableMediaException("The uploaded video could not be inspected.");
    }
    try {
        return Double.parseDouble(stdout.trim());
    } catch (NumberFormatException ex) {
        throw new UnreadableMediaException("The uploaded video could not be inspected.");
    }
}
```

- [x] Keep timeout and process I/O failures as `IllegalStateException`, because those are runtime/infrastructure failures rather than validation results.

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test
```

Expected: tests pass.

## Task 3: Return Structured Validation Errors For Unreadable Media

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadValidationServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadValidationServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceTests.java`

**Interfaces:**
- Consumes: `UnreadableMediaException`
- Produces: invalid `MediaUploadValidationVo` with `code=UNREADABLE_MEDIA`

- [x] In `MediaUploadValidationServiceTests`, verify existing cheap failures still short-circuit:

```java
assertThat(durationProbeService.probeCalls).isZero();
```

Expected existing tests still cover missing, empty, unsupported content type, and file too large.

- [x] Add service upload short-circuit test in `MediaUploadServiceTests`:

```java
@Test
void rejectsUnreadableVideoBeforeStorage() {
    RecordingObjectStorageService storageService = new RecordingObjectStorageService(false);
    MediaUploadService service = new MediaUploadServiceImpl(
            new MediaUploadValidationServiceImpl(
                    properties,
                    command -> {
                        throw new UnreadableMediaException("The uploaded video could not be inspected.");
                    }
            ),
            storageService,
            videoRepository,
            jobRepository,
            new JobDispatchOutboxServiceImpl(dispatchEventRepository, objectMapper)
    );
    MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

    assertThatThrownBy(() -> service.createUpload(file, "zh-CN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UNREADABLE_MEDIA");
    assertThat(storageService.lastCommand).isNull();
}
```

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadServiceTests test
```

Expected: validation test fails until `MediaUploadValidationServiceImpl` catches `UnreadableMediaException`.

- [x] Import `UnreadableMediaException` in `MediaUploadValidationServiceImpl`.

- [x] Wrap duration probing:

```java
Integer durationSeconds;
try {
    durationSeconds = probeDurationSeconds(file, filename);
} catch (UnreadableMediaException ex) {
    return invalid(
            MediaUploadValidationCode.UNREADABLE_MEDIA,
            ex.getMessage(),
            filename,
            contentType,
            fileSizeBytes
    );
}
```

- [x] Leave the existing duration-too-long comparison unchanged:

```java
if (durationSeconds > properties.getMedia().getMaxDurationSeconds()) {
    ...
}
```

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadServiceTests test
```

Expected: tests pass.

## Task 4: Cover Controller 400 Behavior

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Interfaces:**
- Consumes: `UnreadableMediaException`
- Produces: HTTP 400 with `UNREADABLE_MEDIA` on `/api/media/uploads/validate`
- Produces: HTTP 400 with `UPLOAD_VALIDATION_FAILED` and `UNREADABLE_MEDIA` message on `/api/media/uploads`

- [x] Add `/validate` controller test:

```java
@Test
void returnsBadRequestForUnreadableValidationFile() throws Exception {
    when(mediaDurationProbeService.probeDuration(any()))
            .thenThrow(new UnreadableMediaException("The uploaded video could not be inspected."));
    MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

    mockMvc.perform(multipart("/api/media/uploads/validate").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.code").value("UNREADABLE_MEDIA"))
            .andExpect(jsonPath("$.durationSeconds").doesNotExist());
}
```

- [x] Add `/uploads` controller test:

```java
@Test
void returnsBadRequestForUnreadableUploadFile() throws Exception {
    when(mediaDurationProbeService.probeDuration(any()))
            .thenThrow(new UnreadableMediaException("The uploaded video could not be inspected."));
    MockMultipartFile file = new MockMultipartFile("file", "broken.mp4", "video/mp4", new byte[] {1});

    mockMvc.perform(multipart("/api/media/uploads")
                    .file(file)
                    .param("targetLanguage", "zh-CN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("UPLOAD_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value(
                    "UNREADABLE_MEDIA: The uploaded video could not be inspected."
            ));
}
```

- [x] Run:

```bash
mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests test
```

Expected: tests pass after Task 3 implementation.

## Task 5: Documentation And Execution Log

**Files:**
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: user-facing documentation of unreadable media validation behavior.

- [x] Update README Media Upload Intake section:

```markdown
Files with supported content types must also be inspectable by FFprobe. If LinguaFrame cannot inspect duration, the upload is rejected as `UNREADABLE_MEDIA` before storage or queue dispatch.
```

- [x] Update `docs/product/spec.md` Video Upload section:

```markdown
- Files with supported content types but unreadable media metadata are rejected before storage and job creation.
```

- [x] Append execution log entry:

```markdown
## 2026-06-27

Work:

- Added `UNREADABLE_MEDIA` validation for supported-content-type files whose duration cannot be inspected.
- Converted FFprobe unreadable-media failures into structured upload validation responses.
- Confirmed unreadable uploads do not reach object storage or job creation.

Validation:

- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test` passed.
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadServiceTests test` passed.
- `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests test` passed.
```

- [x] Run:

```bash
rg -n "UNREADABLE_MEDIA|unreadable media|could not be inspected|before storage" README.md docs/product/spec.md docs/progress/execution-log.md
```

Expected: docs mention the error code and before-storage contract.

## Task 6: Final Verification And Merge

**Files:**
- Verify all files changed in Tasks 1-5.

**Verification Commands:**
- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test`
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadServiceTests test`
- `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests test`
- `mvn -pl LinguaFrame test -q`
- `docker compose --env-file .env.example config`
- `git diff --check`
- `git status --short`

- [x] Confirm no media files, generated artifacts, `.env`, or credentials are staged.
- [x] Commit: `Validate unreadable media uploads`
- [x] Merge branch `invalid-media-upload-validation` back to `main` after verification.
- [x] Add post-merge verification to `docs/progress/execution-log.md` and commit it on `main`.

## Completion Checklist

- [x] `/api/media/uploads/validate` returns HTTP 400 with `code=UNREADABLE_MEDIA` for unreadable but supported-content-type files.
- [x] `/api/media/uploads` returns HTTP 400 before object storage and job creation for unreadable media.
- [x] Existing cheap validation failures still avoid duration probing.
- [x] FFprobe stderr is not exposed; secrets and local paths are not exposed.
- [x] Runtime/infrastructure FFprobe failures such as timeout or process I/O failure remain server errors.
- [x] Tests cover FFprobe unreadable output, validation VO behavior, upload service storage short-circuit, and controller HTTP responses.
- [x] Validation evidence is recorded in `docs/progress/execution-log.md`.
- [x] Feature branch is merged back to `main` after verified implementation.
