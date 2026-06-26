# Subtitle-Burned Video Export MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a worker stage that burns target-language subtitles into the uploaded video and stores a downloadable MP4 artifact.

**Architecture:** Keep subtitle burn-in behind a media service boundary, then add a `SUBTITLE_BURN_IN` pipeline stage after target subtitle export and TTS. The stage reads persisted target subtitles, writes a temporary SRT file, runs FFmpeg against the original uploaded video, and stores a `BURNED_VIDEO` artifact through the existing artifact service.

**Tech Stack:** Java 21, Spring Boot 3.5.15, FFmpeg, JUnit 5, Maven, Docker Compose, Bash demo scripts.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `subtitle-burned-video-export-mvp`.
- Keep application default burn-in disabled outside Docker: `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=false`.
- Enable burn-in for the Docker demo path through `.env.example` and Docker Compose defaults.
- Do not add audio replacement, lip sync, video editor controls, frontend UI, authentication, Redis behavior, OpenAI calls, model-call audit tables, or cost tracking in this slice.
- Never process arbitrary user-supplied local paths. Burn-in must use object storage input and worker-owned temporary files only.
- Never log source object credentials, local raw media paths from user input, object storage credentials, OpenAI keys, or raw provider responses.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=false` skips video burn-in without calling FFmpeg.
- `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=true` burns target subtitles into the original uploaded video.
- The worker records timeline events for `SUBTITLE_BURN_IN`.
- The generated artifact appears in `/api/jobs/{jobId}/artifacts` with type `BURNED_VIDEO`, filename `burned-video.mp4`, and content type `video/mp4`.
- The existing artifact download endpoint downloads the burned video without a new controller route.
- The default Docker E2E demo uploads a real short MP4 and downloads `/tmp/linguaframe-demo/burned-video.mp4`.

## Design Choices

Recommended approach: create a focused `FfmpegSubtitleBurnInService` and call it from a dedicated pipeline stage. This mirrors the existing audio extraction boundary, keeps FFmpeg process handling testable, and keeps subtitle export separate from media rendering.

Alternatives considered:

- Burn subtitles in `TargetSubtitleExportPipelineStage`: fewer classes, but it mixes subtitle persistence/export with video rendering.
- Read the generated target SRT artifact from object storage: avoids re-exporting SRT, but couples burn-in to artifact ordering and storage lookup.
- Add TTS audio mixing now: attractive for the final product, but too large for this slice and not required to prove subtitle-burned video export.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnInSubtitlesCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnedVideoBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegSubtitleBurnInService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegSubtitleBurnInServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleBurnInPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegSubtitleBurnInServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleBurnInPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`

## Task 1: Burn-In Configuration, Stage, And Artifact Types

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- `LinguaFrameProperties.Ffmpeg#isBurnInEnabled(): boolean`
- `LinguaFrameProperties.Ffmpeg#getBurnInTimeoutSeconds(): int`
- `LocalizationJobStage.SUBTITLE_BURN_IN`
- `JobArtifactType.BURNED_VIDEO`

- [x] **Step 1: Write failing property tests**

In `bindsDefaultRuntimeProperties()`, add:

```java
assertThat(properties.getFfmpeg().isBurnInEnabled()).isFalse();
assertThat(properties.getFfmpeg().getBurnInTimeoutSeconds()).isEqualTo(180);
```

In `bindsFfmpegRuntimeProperties()`, add properties and assertions:

```java
"linguaframe.ffmpeg.burn-in-enabled=true",
"linguaframe.ffmpeg.burn-in-timeout-seconds=45"
```

```java
assertThat(boundProperties.getFfmpeg().isBurnInEnabled()).isTrue();
assertThat(boundProperties.getFfmpeg().getBurnInTimeoutSeconds()).isEqualTo(45);
```

In `rejectsInvalidRuntimeProperties()`, add:

```java
"linguaframe.ffmpeg.burn-in-timeout-seconds=0"
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: fail because `getBurnInTimeoutSeconds()` and `isBurnInEnabled()` do not exist.

- [x] **Step 2: Add FFmpeg burn-in runtime properties**

In `LinguaFrameProperties.Ffmpeg`, add:

```java
private boolean burnInEnabled = false;

@Min(1)
@Max(3600)
private int burnInTimeoutSeconds = 180;

public boolean isBurnInEnabled() {
    return burnInEnabled;
}

public void setBurnInEnabled(boolean burnInEnabled) {
    this.burnInEnabled = burnInEnabled;
}

public int getBurnInTimeoutSeconds() {
    return burnInTimeoutSeconds;
}

public void setBurnInTimeoutSeconds(int burnInTimeoutSeconds) {
    this.burnInTimeoutSeconds = burnInTimeoutSeconds;
}
```

Wire YAML:

```yaml
linguaframe:
  ffmpeg:
    burn-in-enabled: ${LINGUAFRAME_FFMPEG_BURN_IN_ENABLED:false}
    burn-in-timeout-seconds: ${LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS:180}
```

Use Docker defaults:

```yaml
linguaframe:
  ffmpeg:
    burn-in-enabled: ${LINGUAFRAME_FFMPEG_BURN_IN_ENABLED:true}
    burn-in-timeout-seconds: ${LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS:180}
```

Update `.env.example`:

```env
LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=true
LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS=180
```

Update `docker-compose.yml` environment:

```yaml
LINGUAFRAME_FFMPEG_BURN_IN_ENABLED: ${LINGUAFRAME_FFMPEG_BURN_IN_ENABLED:-true}
LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS: ${LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS:-180}
```

- [x] **Step 3: Add enum values**

Add `SUBTITLE_BURN_IN` after `DUBBING_AUDIO_GENERATION` and before `ARTIFACT_SUMMARY`.

Add `BURNED_VIDEO` after `DUBBING_AUDIO` and before `WORKER_SUMMARY`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java LinguaFrame/src/main/resources/application.yaml LinguaFrame/src/main/resources/application-docker.yaml LinguaFrame/src/test/resources/application-test.yaml .env.example docker-compose.yml LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java
git commit -m "Add subtitle burn-in runtime configuration"
```

## Task 2: FFmpeg Subtitle Burn-In Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnInSubtitlesCommand.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnedVideoBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegSubtitleBurnInService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegSubtitleBurnInServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegSubtitleBurnInServiceTests.java`

**Interfaces:**
- `BurnInSubtitlesCommand(String jobId, Path inputVideoPath, Path subtitlePath, Path outputVideoPath)`
- `BurnedVideoBo(String filename, String contentType, byte[] content)`
- `FfmpegSubtitleBurnInService#burnInSubtitles(BurnInSubtitlesCommand command): BurnedVideoBo`

- [x] **Step 1: Write failing FFmpeg service tests**

Create `FfmpegSubtitleBurnInServiceTests` with these cases:

- `burnsSubtitlesWithFixedFfmpegArguments`
- `failsWithSafeErrorSummaryWhenFfmpegReturnsNonZero`
- `failsSafelyWhenFfmpegTimesOut`
- `destroysProcessAndRestoresInterruptWhenCommandRunnerIsInterrupted`

The successful command assertion should expect:

```java
assertThat(runner.lastCommand).containsExactly(
        "ffmpeg",
        "-y",
        "-i",
        input.toString(),
        "-vf",
        "subtitles=" + subtitle.toAbsolutePath(),
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-c:a",
        "copy",
        "-movflags",
        "+faststart",
        output.toString()
);
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=FfmpegSubtitleBurnInServiceTests test
```

Expected: fail because burn-in service types do not exist.

- [x] **Step 2: Implement BOs and interface**

Create:

```java
public record BurnInSubtitlesCommand(
        String jobId,
        Path inputVideoPath,
        Path subtitlePath,
        Path outputVideoPath
) {
}
```

```java
public record BurnedVideoBo(
        String filename,
        String contentType,
        byte[] content
) {
}
```

```java
public interface FfmpegSubtitleBurnInService {

    BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command);
}
```

- [x] **Step 3: Implement FFmpeg service**

`FfmpegSubtitleBurnInServiceImpl` should mirror `FfmpegAudioExtractionServiceImpl` and use:

```java
List<String> ffmpegCommand = List.of(
        properties.getFfmpeg().getBinaryPath(),
        "-y",
        "-i",
        command.inputVideoPath().toString(),
        "-vf",
        "subtitles=" + command.subtitlePath().toAbsolutePath(),
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-c:a",
        "copy",
        "-movflags",
        "+faststart",
        command.outputVideoPath().toString()
);
```

Use `properties.getFfmpeg().getBurnInTimeoutSeconds()` for timeout.

Return:

```java
return new BurnedVideoBo(
        "burned-video.mp4",
        "video/mp4",
        Files.readAllBytes(command.outputVideoPath())
);
```

Use these safe messages:

```text
FFmpeg subtitle burn-in failed.
FFmpeg subtitle burn-in timed out.
FFmpeg subtitle burn-in failed: <safe stderr summary>
Failed to read burned video.
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=FfmpegSubtitleBurnInServiceTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnInSubtitlesCommand.java LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/BurnedVideoBo.java LinguaFrame/src/main/java/com/linguaframe/media/service/FfmpegSubtitleBurnInService.java LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegSubtitleBurnInServiceImpl.java LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegSubtitleBurnInServiceTests.java
git commit -m "Add FFmpeg subtitle burn-in service"
```

## Task 3: Subtitle Burn-In Pipeline Stage

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleBurnInPipelineStage.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleBurnInPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Stage: `LocalizationJobStage.SUBTITLE_BURN_IN`
- Reads: `SubtitleService#listSubtitles(jobId, targetLanguage)`
- Exports: `SubtitleExportService#exportSubtitleSrt(subtitles)`
- Runs: `FfmpegSubtitleBurnInService#burnInSubtitles(...)`
- Writes: `JobArtifactType.BURNED_VIDEO`

- [x] **Step 1: Write failing pipeline stage tests**

Create `SubtitleBurnInPipelineStageTests` with:

- disabled burn-in skips object storage, FFmpeg, and artifact creation.
- enabled burn-in with target subtitles copies the source video, writes `target-subtitles.srt`, calls FFmpeg, creates `BURNED_VIDEO`, and cleans the work directory.
- enabled burn-in with no target subtitles throws `Target subtitles not found for subtitle burn-in.`

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleBurnInPipelineStageTests test
```

Expected: fail because `SubtitleBurnInPipelineStage` does not exist.

- [x] **Step 2: Implement pipeline stage**

Create `SubtitleBurnInPipelineStage` as a Spring `@Component`.

Constructor dependencies:

```java
LinguaFrameProperties properties
ObjectStorageService objectStorageService
MediaWorkDirectoryService workDirectoryService
FfmpegSubtitleBurnInService burnInService
SubtitleService subtitleService
SubtitleExportService subtitleExportService
JobArtifactService artifactService
```

Implementation rules:

- `stage()` returns `LocalizationJobStage.SUBTITLE_BURN_IN`.
- If `properties.getFfmpeg().isBurnInEnabled()` is false, return immediately.
- Read target subtitles using `context.job().id()` and `context.job().targetLanguage()`.
- If subtitles are empty, throw `Target subtitles not found for subtitle burn-in.`
- Create a worker-owned temp directory.
- Copy `context.message().sourceObjectKey()` from object storage to `source-video.mp4`.
- Write `subtitleExportService.exportSubtitleSrt(subtitles)` to `target-subtitles.srt`.
- Run `burnInService.burnInSubtitles(new BurnInSubtitlesCommand(jobId, inputVideoPath, subtitlePath, outputVideoPath))`.
- Store `BURNED_VIDEO` using the returned filename, content type, and bytes.
- Always delete the work directory in `finally`.

- [x] **Step 3: Extend execution ordering coverage**

Add or update a `LocalizationJobExecutionServiceTests` case so the ordered pipeline includes:

```text
TARGET_SUBTITLE_EXPORT STARTED/SUCCEEDED
DUBBING_AUDIO_GENERATION STARTED/SUCCEEDED
SUBTITLE_BURN_IN STARTED/SUCCEEDED
ARTIFACT_SUMMARY STARTED/SUCCEEDED
```

The artifact type order should include:

```text
EXTRACTED_AUDIO
TRANSCRIPT_JSON
SUBTITLE_SRT
SUBTITLE_VTT
TARGET_SUBTITLE_JSON
TARGET_SUBTITLE_SRT
TARGET_SUBTITLE_VTT
DUBBING_AUDIO
BURNED_VIDEO
WORKER_SUMMARY
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleBurnInPipelineStage.java LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleBurnInPipelineStageTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java
git commit -m "Add subtitle burn-in pipeline stage"
```

## Task 4: Docker Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Demo behavior:**
- Generate a real short MP4 sample when `LINGUAFRAME_DEMO_SAMPLE_PATH` does not already exist.
- Download `BURNED_VIDEO` to `/tmp/linguaframe-demo/burned-video.mp4`.
- Default `.env.example` expected artifact count becomes `9`.
- If TTS is also enabled, expected artifact count becomes `10`.

- [x] **Step 1: Upgrade demo sample generation**

In `create_demo_sample()`, replace WAV generation with an FFmpeg-generated MP4:

```bash
if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "Missing ffmpeg to create demo MP4; set LINGUAFRAME_DEMO_SAMPLE_PATH to an existing short MP4." >&2
  exit 1
fi
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i "color=c=blue:s=640x360:r=25:d=2" \
  -f lavfi -i "sine=frequency=440:sample_rate=16000:duration=2" \
  -shortest \
  -c:v libx264 \
  -pix_fmt yuv420p \
  -c:a aac \
  -movflags +faststart \
  "$path"
```

- [x] **Step 2: Download burned video in success demo**

Add:

```bash
BURNED_VIDEO_PATH="${LINGUAFRAME_DEMO_BURNED_VIDEO_PATH:-/tmp/linguaframe-demo/burned-video.mp4}"
```

After target subtitles:

```bash
download_artifact_by_type "$BASE_URL" "$job_id" BURNED_VIDEO "$BURNED_VIDEO_PATH"
echo "Downloaded burned video to $BURNED_VIDEO_PATH"
```

- [x] **Step 3: Update docs and progress records**

Document:

- `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED`
- `LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS`
- `BURNED_VIDEO burned-video.mp4`
- `/tmp/linguaframe-demo/burned-video.mp4`
- default Docker artifact count `9`
- artifact count `10` when TTS is enabled
- MVP boundary: no TTS audio mixing, no lip sync, no subtitle style editor.

Add a decision:

```text
Decision: Add subtitle-burned video as an FFmpeg-backed worker stage after generated subtitles.
Reason: Burn-in is a media rendering concern and should stay separate from subtitle generation and TTS.
Impact: Docker demo can now produce a visible localized video artifact while audio replacement and advanced styling remain later work.
```

- [x] **Step 4: Run focused verification**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,FfmpegSubtitleBurnInServiceTests,SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
docker compose --env-file .env.example config
```

Expected:

- Maven focused tests pass.
- Bash syntax checks pass.
- Compose renders `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED: "true"`.
- Compose renders `LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS: "180"`.

- [x] **Step 5: Run full backend verification**

Run:

```bash
mvn -pl LinguaFrame test
```

Expected: pass.

Commit:

```bash
git add scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/progress/decisions.md docs/progress/execution-log.md
git commit -m "Document subtitle-burned video demo path"
```

## Final Integration

After all tasks pass:

1. Verify `git status --short --branch` is clean on `subtitle-burned-video-export-mvp`.
2. Merge the completed branch back to `main`.
3. Run `mvn -pl LinguaFrame test` again on `main`.
4. Report validation evidence and note whether Docker E2E was run locally.
