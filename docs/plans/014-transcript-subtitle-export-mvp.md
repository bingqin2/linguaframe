# Transcript Subtitle Export MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the extracted audio worker output into persisted transcript segments and downloadable subtitle artifacts without requiring paid external API calls.

**Architecture:** Add a subtitle domain with transcript segment persistence, deterministic demo transcription, and SRT/VTT export services. Insert a `TRANSCRIPT_SUBTITLE_EXPORT` worker stage after `AUDIO_EXTRACTION` and before `ARTIFACT_SUMMARY`; the stage reads transcript segments from a provider boundary, persists them, and creates `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, and `SUBTITLE_VTT` artifacts through the existing artifact service.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring JDBC, Flyway, RabbitMQ worker pipeline, MinIO-backed artifact service, JUnit 5, MockMvc, Bash, curl.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `transcript-subtitle-export-mvp`.
- Keep this slice focused on transcript persistence and subtitle export artifacts.
- Do not add real OpenAI API calls, translation, TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, or paid external API calls.
- Keep the transcription boundary provider-driven so a later OpenAI speech feature can replace the deterministic demo provider without rewriting subtitle storage/export.
- Store only safe generated transcript text and timing metadata; never store API keys, provider raw responses, local absolute media paths, or object storage credentials.
- Keep automated tests external-service-free; live Docker behavior is verified by the Docker E2E demo.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `transcript_segments` table stores ordered transcript segments per job.
- Worker pipeline runs `TRANSCRIPT_SUBTITLE_EXPORT` after `AUDIO_EXTRACTION`.
- Demo transcription is deterministic and enabled by default in Docker.
- Successful jobs create:
  - `TRANSCRIPT_JSON` artifact named `transcript.json`
  - `SUBTITLE_SRT` artifact named `subtitles.srt`
  - `SUBTITLE_VTT` artifact named `subtitles.vtt`
- `GET /api/jobs/{jobId}/transcript` returns persisted segments for preview.
- Docker success demo prints and downloads audio, transcript JSON, SRT, VTT, and worker summary artifacts.
- This slice does not call OpenAI. It prepares the internal contract for the next real speech-to-text slice.

## Design Choices

Recommended approach: build the subtitle persistence/export path now with a deterministic `DemoTranscriptionProvider`. This gives a runnable local demo and locks the data contracts before adding paid model calls.

Alternatives considered:

- Add real OpenAI Speech now: stronger product realism, but requires secrets, paid calls, and more provider-error/cost handling than this slice should carry.
- Generate subtitle files without storing segments: faster, but loses the preview API and makes later translation harder.
- Put transcript tables under the job module: fewer packages, but transcript/subtitle is a distinct product domain and should not bloat job orchestration.

## File Structure

- Create: `LinguaFrame/src/main/resources/db/migration/V5__create_transcript_segments.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/entity/TranscriptSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/bo/TranscriptionSegmentBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/bo/TranscriptionResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/vo/TranscriptSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/repository/TranscriptSegmentRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/TranscriptionProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/TranscriptService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/SubtitleExportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/DemoTranscriptionProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/TranscriptServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/SubtitleExportServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranscriptSubtitleExportPipelineStage.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/repository/TranscriptSegmentRepositoryTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/service/TranscriptServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/service/SubtitleExportServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`

## Task 1: Transcript Schema And Repository

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V5__create_transcript_segments.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/entity/TranscriptSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/repository/TranscriptSegmentRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/repository/TranscriptSegmentRepositoryTests.java`

**Interfaces:**
- `TranscriptSegmentRepository#saveAll(List<TranscriptSegmentRecord> records): void`
- `TranscriptSegmentRepository#findByJobId(String jobId): List<TranscriptSegmentRecord>`
- `TranscriptSegmentRepository#deleteByJobId(String jobId): void`
- `TranscriptSegmentRecord(String id, String jobId, int segmentIndex, long startMs, long endMs, String text, Instant createdAt)`

- [ ] **Step 1: Write failing repository test**

Create `TranscriptSegmentRepositoryTests` that:

- creates a video and localization job.
- saves two segments out of order by `segmentIndex`.
- asserts `findByJobId` returns them ordered by `segment_index`.
- asserts `deleteByJobId` removes only that job's segments.

Use job ids:

```java
transcript-job-1
transcript-job-2
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=TranscriptSegmentRepositoryTests
```

Expected: fail because transcript table and repository do not exist.

- [ ] **Step 2: Add Flyway migration**

Create `V5__create_transcript_segments.sql`:

```sql
CREATE TABLE transcript_segments (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    segment_index INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_transcript_segments_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_transcript_segments_job_index
        UNIQUE (job_id, segment_index)
);

CREATE INDEX idx_transcript_segments_job_index
    ON transcript_segments(job_id, segment_index);
```

- [ ] **Step 3: Add transcript record and repository**

Create `TranscriptSegmentRecord`:

```java
package com.linguaframe.subtitle.domain.entity;

import java.time.Instant;

public record TranscriptSegmentRecord(
        String id,
        String jobId,
        int segmentIndex,
        long startMs,
        long endMs,
        String text,
        Instant createdAt
) {
}
```

Implement `TranscriptSegmentRepository` with `JdbcClient`, matching existing repository style:

- `saveAll` batch-inserts all records.
- `findByJobId` orders by `segment_index`.
- `deleteByJobId` deletes rows for a single job.

- [ ] **Step 4: Verify repository tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=TranscriptSegmentRepositoryTests
```

Expected: pass.

## Task 2: Transcript Service And Deterministic Provider Boundary

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/bo/TranscriptionSegmentBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/bo/TranscriptionResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/domain/vo/TranscriptSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/TranscriptionProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/TranscriptService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/DemoTranscriptionProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/TranscriptServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/service/TranscriptServiceTests.java`

**Interfaces:**
- `LinguaFrameProperties.Transcription#isEnabled()`
- `LinguaFrameProperties.Transcription#getProvider()`
- `TranscriptionProvider#transcribe(String jobId, byte[] audioContent): TranscriptionResultBo`
- `TranscriptService#replaceTranscript(String jobId, TranscriptionResultBo result): List<TranscriptSegmentVo>`
- `TranscriptService#listTranscript(String jobId): List<TranscriptSegmentVo>`

- [ ] **Step 1: Write failing property and service tests**

Extend `LinguaFramePropertiesTests` to assert defaults:

```java
assertThat(properties.getTranscription().isEnabled()).isFalse();
assertThat(properties.getTranscription().getProvider()).isEqualTo("demo");
```

Add a bind test for:

```properties
linguaframe.transcription.enabled=true
linguaframe.transcription.provider=demo
```

Create `TranscriptServiceTests` with an in-memory repository fake. Assert:

- `replaceTranscript` deletes previous job rows.
- saves new rows with generated ids.
- returns VOs ordered by index.
- rejects invalid segment timing where `endMs <= startMs`.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,TranscriptServiceTests
```

Expected: fail because transcription properties and service types do not exist.

- [ ] **Step 2: Add transcription configuration**

Add to `LinguaFrameProperties`:

```java
@Valid
private final Transcription transcription = new Transcription();

public Transcription getTranscription() {
    return transcription;
}

public static class Transcription {
    private boolean enabled = false;

    @NotBlank
    private String provider = "demo";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}
```

Add YAML:

```yaml
linguaframe:
  transcription:
    enabled: ${LINGUAFRAME_TRANSCRIPTION_ENABLED:false}
    provider: ${LINGUAFRAME_TRANSCRIPTION_PROVIDER:demo}
```

In `application-docker.yaml`, default `enabled` to true. In `application-test.yaml`, set it false. Add matching `.env.example` and `docker-compose.yml` variables.

- [ ] **Step 3: Add transcript BO/VO/service interfaces**

Use:

```java
public record TranscriptionSegmentBo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
```

```java
public record TranscriptionResultBo(
        List<TranscriptionSegmentBo> segments
) {
}
```

```java
public record TranscriptSegmentVo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
```

`TranscriptionProvider` should accept audio bytes so later OpenAI speech implementation can use the same stage contract without depending on object-storage paths.

- [ ] **Step 4: Implement deterministic demo provider**

`DemoTranscriptionProvider` should return two segments:

```text
0: 0-1200 ms, "Welcome to LinguaFrame."
1: 1200-2400 ms, "This demo generated subtitle artifacts."
```

Ignore audio content for now, but keep the method signature.

- [ ] **Step 5: Implement transcript service**

`TranscriptServiceImpl#replaceTranscript` should:

- reject empty segment lists.
- reject blank text.
- reject `endMs <= startMs`.
- reject negative start/end.
- delete previous job rows.
- save new `TranscriptSegmentRecord` rows with UUID ids and `Instant.now(clock)`.
- return `listTranscript(jobId)`.

- [ ] **Step 6: Verify service tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,TranscriptServiceTests
```

Expected: pass.

## Task 3: Subtitle Export Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/SubtitleExportService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/subtitle/service/impl/SubtitleExportServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/subtitle/service/SubtitleExportServiceTests.java`

**Interfaces:**
- `SubtitleExportService#transcriptJson(List<TranscriptSegmentVo> segments): byte[]`
- `SubtitleExportService#srt(List<TranscriptSegmentVo> segments): byte[]`
- `SubtitleExportService#vtt(List<TranscriptSegmentVo> segments): byte[]`

- [ ] **Step 1: Write failing subtitle export tests**

Create `SubtitleExportServiceTests` asserting:

- JSON contains `segments` with `index`, `startMs`, `endMs`, and `text`.
- SRT uses `00:00:00,000 --> 00:00:01,200`.
- VTT starts with `WEBVTT`.
- VTT uses dot milliseconds: `00:00:00.000 --> 00:00:01.200`.
- text is emitted without HTML escaping in this backend artifact layer.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=SubtitleExportServiceTests
```

Expected: fail because service does not exist.

- [ ] **Step 2: Implement export service**

`SubtitleExportServiceImpl` should use Jackson for JSON and deterministic string builders for SRT/VTT.

Formatting:

```text
SRT:
1
00:00:00,000 --> 00:00:01,200
Welcome to LinguaFrame.

2
00:00:01,200 --> 00:00:02,400
This demo generated subtitle artifacts.
```

```text
VTT:
WEBVTT

00:00:00.000 --> 00:00:01.200
Welcome to LinguaFrame.

00:00:01.200 --> 00:00:02.400
This demo generated subtitle artifacts.
```

Return UTF-8 bytes.

- [ ] **Step 3: Verify export tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=SubtitleExportServiceTests
```

Expected: pass.

## Task 4: Worker Transcript Subtitle Export Stage

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TranscriptSubtitleExportPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `JobArtifactType.TRANSCRIPT_JSON`
- `JobArtifactType.SUBTITLE_SRT`
- `JobArtifactType.SUBTITLE_VTT`
- `LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT`
- `TranscriptSubtitleExportPipelineStage` implements `LocalizationPipelineStage`

- [ ] **Step 1: Write failing worker stage test**

Extend `LocalizationJobExecutionServiceTests` with a test that wires stages:

```java
List.of(
    new WorkerSmokePipelineStage(properties),
    new TranscriptSubtitleExportPipelineStage(...fake dependencies...),
    new WorkerSummaryArtifactPipelineStage(...)
)
```

Use a fake `JobArtifactService` containing one prior `EXTRACTED_AUDIO` artifact and a fake `openArtifact` that returns audio bytes.

Assert:

- timeline order includes `TRANSCRIPT_SUBTITLE_EXPORT` between `AUDIO_EXTRACTION` and `ARTIFACT_SUMMARY` when all stages are present.
- transcript provider receives audio bytes.
- transcript service receives provider result.
- artifact service receives `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, and `SUBTITLE_VTT`.
- disabled transcription returns without creating subtitle artifacts.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: fail because stage and enum values do not exist.

- [ ] **Step 2: Add enum values**

Add to `JobArtifactType`:

```java
TRANSCRIPT_JSON,
SUBTITLE_SRT,
SUBTITLE_VTT
```

Insert in `LocalizationJobStage` after `AUDIO_EXTRACTION`:

```java
TRANSCRIPT_SUBTITLE_EXPORT
```

- [ ] **Step 3: Implement worker stage**

`TranscriptSubtitleExportPipelineStage` should:

- return immediately when `linguaframe.transcription.enabled=false`.
- find an `EXTRACTED_AUDIO` artifact for the job from `artifactService.listArtifacts(jobId)`.
- open the audio artifact through `artifactService.openArtifact(jobId, artifactId)`.
- read audio bytes.
- call `TranscriptionProvider#transcribe(jobId, audioContent)`.
- call `TranscriptService#replaceTranscript`.
- call `SubtitleExportService` for JSON/SRT/VTT bytes.
- create artifacts:
  - `TRANSCRIPT_JSON`, `transcript.json`, `application/json`
  - `SUBTITLE_SRT`, `subtitles.srt`, `application/x-subrip`
  - `SUBTITLE_VTT`, `subtitles.vtt`, `text/vtt`

If no `EXTRACTED_AUDIO` artifact exists, throw:

```text
Extracted audio artifact not found.
```

- [ ] **Step 4: Verify worker tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests
```

Expected: pass.

## Task 5: Transcript Preview API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/transcript`
- Controller depends on `TranscriptService#listTranscript(String jobId)`

- [ ] **Step 1: Write failing controller tests**

Add tests:

- `GET /api/jobs/{jobId}/transcript` returns an array of segment VOs ordered by index.
- unknown job with no transcript returns an empty array for now; job ownership/auth is deferred.

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

Expected: fail because endpoint is missing.

- [ ] **Step 2: Add controller dependency and endpoint**

Inject `TranscriptService` into `LocalizationJobController` and add:

```java
@GetMapping("/{jobId}/transcript")
public List<TranscriptSegmentVo> getTranscript(@PathVariable String jobId) {
    return transcriptService.listTranscript(jobId);
}
```

- [ ] **Step 3: Verify controller tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

Expected: pass.

## Task 6: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Demo output includes `TRANSCRIPT_JSON transcript.json`, `SUBTITLE_SRT subtitles.srt`, and `SUBTITLE_VTT subtitles.vtt`.
- Demo downloads:
  - `/tmp/linguaframe-demo/audio.wav`
  - `/tmp/linguaframe-demo/transcript.json`
  - `/tmp/linguaframe-demo/subtitles.srt`
  - `/tmp/linguaframe-demo/subtitles.vtt`
  - `/tmp/linguaframe-demo/worker-summary.json`

- [ ] **Step 1: Update success demo downloads**

In `scripts/demo/docker-e2e-success.sh`, add:

```bash
TRANSCRIPT_PATH="${LINGUAFRAME_DEMO_TRANSCRIPT_PATH:-/tmp/linguaframe-demo/transcript.json}"
SRT_PATH="${LINGUAFRAME_DEMO_SRT_PATH:-/tmp/linguaframe-demo/subtitles.srt}"
VTT_PATH="${LINGUAFRAME_DEMO_VTT_PATH:-/tmp/linguaframe-demo/subtitles.vtt}"
```

Download by type:

```bash
download_artifact_by_type "$BASE_URL" "$job_id" TRANSCRIPT_JSON "$TRANSCRIPT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_SRT "$SRT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_VTT "$VTT_PATH"
```

Print each downloaded path.

- [ ] **Step 2: Add transcript helper**

Add helper:

```bash
get_job_transcript() {
  local base_url="$1"
  local job_id="$2"

  curl -fsS "$base_url/api/jobs/$job_id/transcript"
}
```

In success script, print:

```bash
echo "Transcript for job $job_id:"
get_job_transcript "$BASE_URL" "$job_id" | python3 -m json.tool
```

- [ ] **Step 3: Update docs**

Document:

- transcription config variables.
- deterministic demo transcription behavior.
- expected five artifacts in Docker success demo.
- `GET /api/jobs/{jobId}/transcript`.
- this still does not call OpenAI.

- [ ] **Step 4: Verify scripts**

Run:

```bash
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
```

Expected: both pass.

## Task 7: Full Verification And Merge Readiness

**Files:**
- Modify only if verification exposes small documentation corrections.

- [ ] **Step 1: Run focused tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,TranscriptSegmentRepositoryTests,TranscriptServiceTests,SubtitleExportServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests
```

Expected: pass.

- [ ] **Step 2: Run full tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: `Tests run: 71` or higher, `Failures: 0`, `Errors: 0`.

- [ ] **Step 3: Run Docker verification**

Run:

```bash
docker compose --env-file .env.example config
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
python3 -m json.tool /tmp/linguaframe-demo/transcript.json
cat /tmp/linguaframe-demo/subtitles.srt
cat /tmp/linguaframe-demo/subtitles.vtt
file /tmp/linguaframe-demo/audio.wav
docker compose --env-file .env.example down
```

Expected: job completes, artifact list includes `EXTRACTED_AUDIO`, `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, `SUBTITLE_VTT`, and `WORKER_SUMMARY`; transcript and subtitle files download; Docker stack is stopped.

- [ ] **Step 4: Record evidence**

Append to `docs/progress/execution-log.md`:

- red/green test evidence.
- full test count.
- Docker E2E job id.
- downloaded artifact paths.
- note that OpenAI is intentionally not called in this slice.

- [ ] **Step 5: Commit and merge**

Run:

```bash
git status --short
git add .env.example LinguaFrame README.md docker-compose.yml docs scripts
git commit -m "Add transcript subtitle export MVP"
git switch main
git merge --no-ff transcript-subtitle-export-mvp
git branch -d transcript-subtitle-export-mvp
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
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
/tmp/linguaframe-demo/transcript.json
/tmp/linguaframe-demo/subtitles.srt
/tmp/linguaframe-demo/subtitles.vtt
/tmp/linguaframe-demo/worker-summary.json
```

Clean up:

```bash
docker compose --env-file .env.example down
```
