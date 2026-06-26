# FFmpeg Audio Extraction MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Turn a completed queued job into a real extracted audio artifact by downloading the source video from object storage, running FFmpeg in a controlled worker stage, storing the extracted audio, and exposing it through the existing artifact APIs.

**Architecture:** Add a media-processing boundary around FFmpeg command execution and temporary work directories, then add a worker pipeline stage after `WORKER_SMOKE` and before `ARTIFACT_SUMMARY`. The stage reads the source object through `ObjectStorageService`, writes a temp input file, invokes FFmpeg with fixed arguments, stores `audio.wav` through `JobArtifactService`, and lets the existing job execution service record timeline success or failure.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring JDBC, MinIO SDK, RabbitMQ worker pipeline, Docker Compose, FFmpeg CLI, JUnit 5, MockMvc, Bash, curl.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `ffmpeg-audio-extraction-mvp`.
- Keep this slice focused on FFmpeg audio extraction and extracted audio artifacts.
- Do not add OpenAI transcription, subtitle generation, translation, TTS, frontend UI, authentication, Redis behavior, or paid external API calls.
- Build FFmpeg commands from fixed internal arguments only; never execute user-provided shell fragments.
- Use internally created temp directories and clean them after stage execution.
- Do not log secrets, local absolute source paths, object storage credentials, or raw media contents.
- Keep automated unit tests external-service-free; live FFmpeg/Docker behavior is verified by the Docker E2E demo.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- Docker backend image includes an `ffmpeg` binary.
- Runtime config exposes `linguaframe.ffmpeg.binary-path`, `linguaframe.ffmpeg.audio-enabled`, `linguaframe.ffmpeg.audio-timeout-seconds`, and `linguaframe.ffmpeg.work-dir`.
- Worker pipeline runs an `AUDIO_EXTRACTION` stage after `WORKER_SMOKE`.
- Successful extraction stores an `EXTRACTED_AUDIO` artifact named `audio.wav`.
- `GET /api/jobs/{jobId}/artifacts` shows both `EXTRACTED_AUDIO` and `WORKER_SUMMARY` after completion.
- FFmpeg failure marks the job `FAILED` at `AUDIO_EXTRACTION` with a safe error summary.
- Docker success demo verifies and downloads an audio artifact.

## Design Choices

Recommended approach: install FFmpeg in the backend Docker image and isolate all command execution behind `FfmpegAudioExtractionService`. This gives a real local demo while keeping tests deterministic through a fake command runner.

Alternatives considered:

- Host-mounted FFmpeg only: less image change, but local demos become machine-specific.
- Java media libraries: avoids shelling out, but moves away from the production target and adds unnecessary dependencies.
- Defer real FFmpeg and create fake audio: simpler, but no longer moves Milestone 2 forward.

## File Structure

- Modify: `LinguaFrame/Dockerfile`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ExtractAudioCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ExtractedAudioBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegAudioExtractionService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaWorkDirectoryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/DefaultMediaWorkDirectoryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegAudioExtractionServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/AudioExtractionPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegAudioExtractionServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`

## Task 1: FFmpeg Configuration And Docker Runtime

**Files:**
- Modify: `LinguaFrame/Dockerfile`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- `LinguaFrameProperties.Ffmpeg#getBinaryPath()`
- `LinguaFrameProperties.Ffmpeg#isAudioEnabled()`
- `LinguaFrameProperties.Ffmpeg#getAudioTimeoutSeconds()`
- `LinguaFrameProperties.Ffmpeg#getWorkDir()`

- [x] **Step 1: Write failing property tests**

Extend `LinguaFramePropertiesTests` to assert defaults:

```java
assertThat(properties.getFfmpeg().getBinaryPath()).isEqualTo("ffmpeg");
assertThat(properties.getFfmpeg().isAudioEnabled()).isFalse();
assertThat(properties.getFfmpeg().getAudioTimeoutSeconds()).isEqualTo(120);
assertThat(properties.getFfmpeg().getWorkDir()).isEqualTo("/tmp/linguaframe-media");
```

Also bind:

```properties
linguaframe.ffmpeg.binary-path=/usr/bin/ffmpeg
linguaframe.ffmpeg.audio-enabled=true
linguaframe.ffmpeg.audio-timeout-seconds=30
linguaframe.ffmpeg.work-dir=/tmp/custom-media
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: fail because `ffmpeg` properties do not exist.

- [x] **Step 2: Add config properties**

Add `Ffmpeg` nested class under `LinguaFrameProperties`:

```java
@Valid
private final Ffmpeg ffmpeg = new Ffmpeg();

public Ffmpeg getFfmpeg() {
    return ffmpeg;
}

public static class Ffmpeg {
    @NotBlank
    private String binaryPath = "ffmpeg";

    private boolean audioEnabled = false;

    @Min(1)
    @Max(3600)
    private int audioTimeoutSeconds = 120;

    @NotBlank
    private String workDir = "/tmp/linguaframe-media";

    // getters and setters
}
```

- [x] **Step 3: Wire YAML and Compose**

Add to `application.yaml`:

```yaml
linguaframe:
  ffmpeg:
    binary-path: ${LINGUAFRAME_FFMPEG_BINARY_PATH:ffmpeg}
    audio-enabled: ${LINGUAFRAME_FFMPEG_AUDIO_ENABLED:false}
    audio-timeout-seconds: ${LINGUAFRAME_FFMPEG_AUDIO_TIMEOUT_SECONDS:120}
    work-dir: ${LINGUAFRAME_FFMPEG_WORK_DIR:/tmp/linguaframe-media}
```

In `application-docker.yaml`, set default `audio-enabled` to true through env:

```yaml
linguaframe:
  ffmpeg:
    binary-path: ${LINGUAFRAME_FFMPEG_BINARY_PATH:ffmpeg}
    audio-enabled: ${LINGUAFRAME_FFMPEG_AUDIO_ENABLED:true}
    audio-timeout-seconds: ${LINGUAFRAME_FFMPEG_AUDIO_TIMEOUT_SECONDS:120}
    work-dir: ${LINGUAFRAME_FFMPEG_WORK_DIR:/tmp/linguaframe-media}
```

In `application-test.yaml`, set `audio-enabled: false`.

Add matching environment variables to `.env.example` and `docker-compose.yml`.

- [x] **Step 4: Install FFmpeg in Docker image**

Modify `LinguaFrame/Dockerfile`:

```dockerfile
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
```

Keep the existing local jar copy behavior.

- [x] **Step 5: Verify config**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
docker compose --env-file .env.example config
```

Expected: tests pass; compose renders FFmpeg env variables.

## Task 2: Controlled FFmpeg Audio Extraction Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ExtractAudioCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/ExtractedAudioBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegAudioExtractionService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaWorkDirectoryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/DefaultMediaWorkDirectoryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegAudioExtractionServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegAudioExtractionServiceTests.java`

**Interfaces:**
- `FfmpegAudioExtractionService#extractAudio(ExtractAudioCommand command): ExtractedAudioBo`
- `MediaWorkDirectoryService#createJobWorkDirectory(String jobId): Path`
- `MediaWorkDirectoryService#deleteRecursively(Path directory)`

- [x] **Step 1: Write failing service tests**

Tests should use a fake process runner or package-private command executor seam and assert:

- command args are exactly `ffmpeg -y -i <input> -vn -acodec pcm_s16le -ar 16000 -ac 1 <output>`.
- output content is returned as `ExtractedAudioBo`.
- non-zero exit code throws `IllegalStateException` containing only a safe stderr summary.
- timeout destroys the process and throws `IllegalStateException`.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=FfmpegAudioExtractionServiceTests
```

Expected: fail because service types do not exist.

- [x] **Step 2: Add BO records**

Use:

```java
public record ExtractAudioCommand(
        String jobId,
        Path inputVideoPath,
        Path outputAudioPath
) {
}
```

```java
public record ExtractedAudioBo(
        String filename,
        String contentType,
        byte[] content
) {
}
```

- [x] **Step 3: Implement work directory service**

`DefaultMediaWorkDirectoryService` uses `LinguaFrameProperties#getFfmpeg().getWorkDir()` and creates:

```text
{workDir}/jobs/{jobId}/{UUID}/
```

It must create directories with `Files.createDirectories` and delete recursively after use.

- [x] **Step 4: Implement FFmpeg service**

`FfmpegAudioExtractionServiceImpl` must:

- build a `ProcessBuilder` with fixed args only.
- use configured binary path and timeout.
- redirect stderr to capture safe summaries.
- return `audio.wav`, content type `audio/wav`, and file bytes.
- never use shell command strings.

- [x] **Step 5: Verify FFmpeg service tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=FfmpegAudioExtractionServiceTests
```

Expected: pass.

## Task 3: Audio Extraction Worker Stage

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/AudioExtractionPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `JobArtifactType.EXTRACTED_AUDIO`
- `LocalizationJobStage.AUDIO_EXTRACTION`
- `AudioExtractionPipelineStage` implements `LocalizationPipelineStage`

- [x] **Step 1: Write failing execution test**

Add a test that wires stages:

```java
List.of(
    new WorkerSmokePipelineStage(properties),
    new AudioExtractionPipelineStage(...fake dependencies...),
    new WorkerSummaryArtifactPipelineStage(...)
)
```

Assert:

- timeline order is `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `ARTIFACT_SUMMARY`, `COMPLETED`.
- artifact service receives one `EXTRACTED_AUDIO` command named `audio.wav`.
- source object bytes are copied into a temp input path.
- temp work directory cleanup is called.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: fail because enum/stage do not exist.

- [x] **Step 2: Add enums**

Add:

```java
EXTRACTED_AUDIO
```

to `JobArtifactType`.

Insert:

```java
AUDIO_EXTRACTION
```

between `WORKER_SMOKE` and `ARTIFACT_SUMMARY` in `LocalizationJobStage`.

- [x] **Step 3: Implement audio extraction stage**

Stage behavior:

- if `linguaframe.ffmpeg.audio-enabled=false`, return without creating artifact.
- create job work directory.
- open `context.message().sourceObjectKey()` through `ObjectStorageService#open`.
- copy to `source-video` inside the work directory.
- call `FfmpegAudioExtractionService#extractAudio`.
- create `EXTRACTED_AUDIO` artifact with returned content.
- delete work directory in `finally`.

- [x] **Step 4: Verify worker tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: pass.

## Task 4: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Demo output includes `EXTRACTED_AUDIO audio.wav`.
- Demo downloads `/tmp/linguaframe-demo/audio.wav`.

- [x] **Step 1: Update demo helpers**

Add a helper to download an artifact by type:

```bash
download_artifact_by_type() {
  local base_url="$1"
  local job_id="$2"
  local artifact_type="$3"
  local output_path="$4"
  local artifact_id

  artifact_id="$(list_job_artifacts "$base_url" "$job_id" | python3 -c '
import json, sys
target = sys.argv[1]
for artifact in json.load(sys.stdin):
    if artifact["type"] == target:
        print(artifact["artifactId"])
        raise SystemExit(0)
raise SystemExit(1)
' "$artifact_type")"
  mkdir -p "$(dirname "$output_path")"
  curl -fsS "$base_url/api/jobs/$job_id/artifacts/$artifact_id/download" -o "$output_path"
}
```

- [x] **Step 2: Update success demo**

After job completion, download:

```text
/tmp/linguaframe-demo/audio.wav
/tmp/linguaframe-demo/worker-summary.json
```

Use artifact type selection instead of “first artifact”.

- [x] **Step 3: Update docs**

Document:

- FFmpeg config variables.
- Docker success demo expected `EXTRACTED_AUDIO`.
- `audio.wav` output path.
- This still does not perform OpenAI transcription or subtitle generation.

## Task 5: Full Verification And Merge Readiness

**Files:**
- Modify only if verification exposes small documentation corrections.

- [x] **Step 1: Run focused tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,FfmpegAudioExtractionServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests
```

Expected: pass.

- [x] **Step 2: Run full tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: `Tests run: 65` or higher, `Failures: 0`, `Errors: 0`.

- [x] **Step 3: Run Docker verification**

Run:

```bash
docker compose --env-file .env.example config
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json
file /tmp/linguaframe-demo/audio.wav
docker compose --env-file .env.example down
```

Expected: job completes, artifact list includes `EXTRACTED_AUDIO` and `WORKER_SUMMARY`, `audio.wav` downloads, and Docker stack is stopped.

- [x] **Step 4: Commit and merge**

Run:

```bash
git status --short
git add LinguaFrame src docs scripts .env.example docker-compose.yml README.md
git commit -m "Add FFmpeg audio extraction MVP"
git switch main
git merge --no-ff ffmpeg-audio-extraction-mvp
git branch -d ffmpeg-audio-extraction-mvp
```

Expected: feature branch is merged back to `main`; working tree is clean.

## User-Run Handoff Commands

After implementation, run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
```

Expected downloaded files:

```text
/tmp/linguaframe-demo/audio.wav
/tmp/linguaframe-demo/worker-summary.json
```

Clean up:

```bash
docker compose --env-file .env.example down
```
