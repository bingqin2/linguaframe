# Target Language Subtitle MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert persisted transcript segments into deterministic target-language subtitle segments and downloadable localized subtitle artifacts.

**Architecture:** Add a translation boundary beside the existing transcription boundary, persist translated subtitle segments by job/language, and insert a `TARGET_SUBTITLE_EXPORT` worker stage after `TRANSCRIPT_SUBTITLE_EXPORT`. The stage reads transcript segments, calls a deterministic demo translation provider, stores translated segments, and writes `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, and `TARGET_SUBTITLE_VTT` artifacts through the existing artifact service.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring JDBC, Flyway, RabbitMQ worker pipeline, MinIO-backed artifact service, JUnit 5, MockMvc, Bash, curl.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `target-language-subtitle-mvp`.
- Keep this slice focused on target-language subtitle generation and export.
- Do not add real OpenAI API calls, TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, or paid external API calls.
- Keep the translation provider boundary narrow so a later OpenAI language client can replace the deterministic demo provider without rewriting storage/export.
- Preserve transcript timing exactly in translated subtitle segments.
- Store only generated subtitle text, timing, language, and safe metadata; never store API keys, provider raw responses, local absolute media paths, or object storage credentials.
- Keep automated tests external-service-free; live Docker behavior is verified by the Docker E2E demo.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `subtitle_segments` table stores ordered translated segments per job and language.
- `LinguaFrameProperties.translation` controls deterministic demo translation.
- Worker pipeline runs `TARGET_SUBTITLE_EXPORT` after `TRANSCRIPT_SUBTITLE_EXPORT`.
- Successful Docker jobs create:
  - `TARGET_SUBTITLE_JSON` artifact named `target-subtitles.json`
  - `TARGET_SUBTITLE_SRT` artifact named `target-subtitles.srt`
  - `TARGET_SUBTITLE_VTT` artifact named `target-subtitles.vtt`
- `GET /api/jobs/{jobId}/subtitles/{language}` returns persisted target-language segments for preview.
- Docker success demo prints and downloads audio, transcript, source subtitles, target subtitles, and worker summary artifacts.

## Design Choices

Recommended approach: keep translation deterministic for now and make it a separate worker stage. This makes the demo visibly closer to localization while keeping provider secrets, usage accounting, and paid calls out of this slice.

Alternatives considered:

- Add real OpenAI translation now: more realistic, but it requires prompt templates, provider errors, cost tracking, and secrets before the storage/export path is stable.
- Reuse `transcript_segments` for translated text: faster, but it mixes source transcript and localized output and makes language-specific preview/export harder.
- Put target subtitles in artifact files only: avoids a table, but prevents API preview and makes later TTS/burn-in stages depend on object storage parsing.

## File Structure

- Create: `LinguaFrame/src/main/resources/db/migration/V6__create_subtitle_segments.sql`
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
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleExportService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleExportServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/SubtitleSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranslationSegmentBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranslationResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/SubtitleSegmentRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranslationProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TargetSubtitleExportPipelineStage.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/SubtitleSegmentRepositoryTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleExportServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`

## Task 1: Target Subtitle Schema And Repository

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V6__create_subtitle_segments.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/SubtitleSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/SubtitleSegmentRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/SubtitleSegmentRepositoryTests.java`
- Modify: cleanup blocks in job controller/execution tests to delete `subtitle_segments` before `transcript_segments`.

**Interfaces:**
- `SubtitleSegmentRecord(String id, String jobId, String language, int segmentIndex, long startMs, long endMs, String text, Instant createdAt)`
- `SubtitleSegmentRepository#saveAll(List<SubtitleSegmentRecord> records): void`
- `SubtitleSegmentRepository#findByJobIdAndLanguage(String jobId, String language): List<SubtitleSegmentRecord>`
- `SubtitleSegmentRepository#deleteByJobIdAndLanguage(String jobId, String language): void`

- [x] **Step 1: Write failing repository test**

Create `SubtitleSegmentRepositoryTests` that:

- creates two jobs.
- saves translated segments for `zh-CN` out of order.
- saves a segment for the same job with another language.
- asserts `findByJobIdAndLanguage("subtitle-job-1", "zh-CN")` returns only the `zh-CN` rows ordered by `segment_index`.
- asserts `deleteByJobIdAndLanguage` removes only the target language for that job.

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleSegmentRepositoryTests test
```

Expected: fail because `subtitle_segments`, `SubtitleSegmentRecord`, and `SubtitleSegmentRepository` do not exist.

- [x] **Step 2: Add Flyway migration**

Create `V6__create_subtitle_segments.sql`:

```sql
CREATE TABLE subtitle_segments (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    language VARCHAR(32) NOT NULL,
    segment_index INT NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_subtitle_segments_job_id
        FOREIGN KEY (job_id) REFERENCES localization_jobs(id)
        ON DELETE CASCADE,
    CONSTRAINT uq_subtitle_segments_job_language_index
        UNIQUE (job_id, language, segment_index)
);

CREATE INDEX idx_subtitle_segments_job_language_index
    ON subtitle_segments(job_id, language, segment_index);
```

- [x] **Step 3: Add record and repository**

Implement `SubtitleSegmentRecord` under `job.domain.entity` and `SubtitleSegmentRepository` with `JdbcClient`, matching existing repository style.

Repository SQL must select and order by:

```sql
WHERE job_id = :jobId AND language = :language
ORDER BY segment_index
```

- [x] **Step 4: Verify repository tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleSegmentRepositoryTests test
```

Expected: pass.

## Task 2: Translation Configuration, Provider, And Subtitle Service

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranslationSegmentBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TranslationResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TranslationProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleServiceImpl.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleServiceTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- `LinguaFrameProperties.Translation#isEnabled()`
- `LinguaFrameProperties.Translation#getProvider()`
- `TranslationProvider#translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments): TranslationResultBo`
- `SubtitleService#replaceSubtitles(String jobId, String language, TranslationResultBo result): List<SubtitleSegmentVo>`
- `SubtitleService#listSubtitles(String jobId, String language): List<SubtitleSegmentVo>`

- [x] **Step 1: Write failing config/service/provider tests**

Extend `LinguaFramePropertiesTests` to assert:

```java
assertThat(properties.getTranslation().isEnabled()).isFalse();
assertThat(properties.getTranslation().getProvider()).isEqualTo("demo");
```

Add a bind test for:

```properties
linguaframe.translation.enabled=true
linguaframe.translation.provider=demo
```

Create `TranslationProviderTests` asserting `DemoTranslationProvider` returns deterministic `zh-CN` text for the two current demo transcript lines:

```text
Hello from LinguaFrame.
This demo transcript is deterministic.
```

Expected translated text:

```text
LinguaFrame 向你问好。
这个演示字幕是确定性的。
```

Create `SubtitleServiceTests` with an in-memory repository fake. Assert:

- replacing `zh-CN` deletes only previous `zh-CN` rows for the job.
- returned VOs preserve `index`, `startMs`, and `endMs`.
- blank text, blank language, and `endMs <= startMs` are rejected.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,TranslationProviderTests,SubtitleServiceTests test
```

Expected: fail because translation properties and service types do not exist.

- [x] **Step 2: Add translation configuration**

Add to `LinguaFrameProperties`:

```java
@Valid
private final Translation translation = new Translation();

public Translation getTranslation() {
    return translation;
}

public static class Translation {
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
  translation:
    enabled: ${LINGUAFRAME_TRANSLATION_ENABLED:false}
    provider: ${LINGUAFRAME_TRANSLATION_PROVIDER:demo}
```

In `application-docker.yaml`, default `enabled` to true. In `application-test.yaml`, set it false. Add matching `.env.example` and `docker-compose.yml` variables.

- [x] **Step 3: Add translation BO/VO/service interfaces**

Use:

```java
public record TranslationSegmentBo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
```

```java
public record TranslationResultBo(
        List<TranslationSegmentBo> segments
) {
}
```

```java
public record SubtitleSegmentVo(
        String language,
        int index,
        long startMs,
        long endMs,
        String text
) {
}
```

- [x] **Step 4: Implement deterministic demo provider**

Implement `DemoTranslationProvider` as a `@Component`.

For `targetLanguage=zh-CN`, map:

```java
"Hello from LinguaFrame." -> "LinguaFrame 向你问好。"
"This demo transcript is deterministic." -> "这个演示字幕是确定性的。"
```

For any unknown text, return:

```java
"[" + targetLanguage + "] " + sourceText
```

Preserve segment timing and indexes from the transcript input.

- [x] **Step 5: Implement subtitle service**

`SubtitleServiceImpl` should:

- validate language is not blank.
- validate result and segments are non-empty.
- validate `index >= 0`, `startMs >= 0`, `endMs > startMs`, and text not blank.
- trim text.
- delete only existing rows for the same job and language.
- save new rows with generated UUIDs and current UTC time.
- return `listSubtitles(jobId, language)`.

Follow the existing production/test constructor pattern:

```java
@Autowired
public SubtitleServiceImpl(SubtitleSegmentRepository repository) {
    this(repository, Clock.systemUTC());
}
```

- [x] **Step 6: Verify config/service/provider tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,TranslationProviderTests,SubtitleServiceTests test
```

Expected: pass.

## Task 3: Target Subtitle Export Service Support

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleExportService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleExportServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleExportServiceTests.java`

**Interfaces:**
- `SubtitleExportService#exportSubtitleJson(List<SubtitleSegmentVo> segments): byte[]`
- `SubtitleExportService#exportSubtitleSrt(List<SubtitleSegmentVo> segments): byte[]`
- `SubtitleExportService#exportSubtitleVtt(List<SubtitleSegmentVo> segments): byte[]`

- [x] **Step 1: Write failing export tests**

Extend `SubtitleExportServiceTests` to assert:

- target subtitle JSON contains `language`, `index`, `startMs`, `endMs`, `text`.
- target SRT uses translated text and comma millisecond timestamps.
- target VTT starts with `WEBVTT` and uses dot millisecond timestamps.

Use sample VOs:

```java
List.of(
        new SubtitleSegmentVo("zh-CN", 0, 0L, 1_800L, "LinguaFrame 向你问好。"),
        new SubtitleSegmentVo("zh-CN", 1, 1_800L, 3_600L, "这个演示字幕是确定性的。")
)
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleExportServiceTests test
```

Expected: fail because target subtitle export methods do not exist.

- [x] **Step 2: Implement target subtitle export methods**

Reuse the existing timestamp formatter and text-loop behavior.

JSON should use:

```java
Map.of(
        "language", segments.getFirst().language(),
        "segments", segments
)
```

If the list is empty, throw `IllegalArgumentException("Subtitle segments must not be empty.")`.

- [x] **Step 3: Verify export tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleExportServiceTests test
```

Expected: pass.

## Task 4: Worker Target Subtitle Stage

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/TargetSubtitleExportPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `LocalizationJobStage.TARGET_SUBTITLE_EXPORT`
- `JobArtifactType.TARGET_SUBTITLE_JSON`
- `JobArtifactType.TARGET_SUBTITLE_SRT`
- `JobArtifactType.TARGET_SUBTITLE_VTT`

- [x] **Step 1: Write failing worker stage test**

Add a test to `LocalizationJobExecutionServiceTests` named:

```java
targetSubtitleStageCreatesArtifactsAfterTranscriptExport
```

It should assemble stages:

1. `WorkerSmokePipelineStage`
2. `AudioExtractionPipelineStage`
3. `TranscriptSubtitleExportPipelineStage`
4. `TargetSubtitleExportPipelineStage`
5. `WorkerSummaryArtifactPipelineStage`

Use recording fakes for:

- `TranslationProvider`
- `SubtitleService`
- `SubtitleExportService`

Assert timeline order includes:

```text
TRANSCRIPT_SUBTITLE_EXPORT STARTED/SUCCEEDED
TARGET_SUBTITLE_EXPORT STARTED/SUCCEEDED
ARTIFACT_SUMMARY STARTED/SUCCEEDED
```

Assert artifact types are exactly:

```java
EXTRACTED_AUDIO,
TRANSCRIPT_JSON,
SUBTITLE_SRT,
SUBTITLE_VTT,
TARGET_SUBTITLE_JSON,
TARGET_SUBTITLE_SRT,
TARGET_SUBTITLE_VTT,
WORKER_SUMMARY
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests#targetSubtitleStageCreatesArtifactsAfterTranscriptExport test
```

Expected: fail because the stage and enum values do not exist.

- [x] **Step 2: Add enum values**

Add `TARGET_SUBTITLE_EXPORT` after `TRANSCRIPT_SUBTITLE_EXPORT` and before `ARTIFACT_SUMMARY`.

Add artifact types:

```java
TARGET_SUBTITLE_JSON,
TARGET_SUBTITLE_SRT,
TARGET_SUBTITLE_VTT
```

- [x] **Step 3: Implement worker stage**

`TargetSubtitleExportPipelineStage` should:

- return `LocalizationJobStage.TARGET_SUBTITLE_EXPORT`.
- return immediately if `properties.getTranslation().isEnabled()` is false.
- read transcript preview via `TranscriptService#listTranscript(jobId)`.
- if transcript list is empty, throw `IllegalStateException("Transcript segments not found.")`.
- call `TranslationProvider#translate(jobId, context.job().targetLanguage(), transcriptSegments)`.
- persist via `SubtitleService#replaceSubtitles(jobId, targetLanguage, result)`.
- create:
  - `target-subtitles.json` with `application/json`
  - `target-subtitles.srt` with `application/x-subrip`
  - `target-subtitles.vtt` with `text/vtt`

- [x] **Step 4: Verify worker tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test
```

Expected: pass.

## Task 5: Target Subtitle Preview API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/subtitles/{language}` returns `List<SubtitleSegmentVo>`.

- [x] **Step 1: Write failing controller test**

Add `returnsTargetSubtitleSegmentsForLocalizationJob` to `LocalizationJobControllerTests`.

Use `SubtitleService#replaceSubtitles` to seed `zh-CN` rows, then assert:

```java
mockMvc.perform(get("/api/jobs/{jobId}/subtitles/{language}", jobId, "zh-CN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].language").value("zh-CN"))
        .andExpect(jsonPath("$[0].index").value(0))
        .andExpect(jsonPath("$[0].text").value("LinguaFrame 向你问好。"));
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#returnsTargetSubtitleSegmentsForLocalizationJob test
```

Expected: fail with 404 because the endpoint does not exist.

- [x] **Step 2: Add endpoint**

Inject `SubtitleService` into `LocalizationJobController`.

Add:

```java
@GetMapping("/{jobId}/subtitles/{language}")
public List<SubtitleSegmentVo> listSubtitles(
        @PathVariable String jobId,
        @PathVariable String language
) {
    return subtitleService.listSubtitles(jobId, language);
}
```

- [x] **Step 3: Verify controller tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: pass.

## Task 6: Demo Scripts And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `get_job_subtitles "$base_url" "$job_id" "$language"` helper prints preview JSON.
- Docker demo downloads target subtitle JSON/SRT/VTT paths under `/tmp/linguaframe-demo`.

- [x] **Step 1: Update demo helper**

Add to `scripts/demo/lib/linguaframe-demo.sh`:

```bash
get_job_subtitles() {
  local base_url="$1"
  local job_id="$2"
  local language="$3"

  curl -fsS "$base_url/api/jobs/$job_id/subtitles/$language"
}
```

- [x] **Step 2: Update success demo downloads**

In `scripts/demo/docker-e2e-success.sh`, add:

```bash
TARGET_SUBTITLE_JSON_PATH="${LINGUAFRAME_DEMO_TARGET_SUBTITLE_JSON_PATH:-/tmp/linguaframe-demo/target-subtitles.json}"
TARGET_SRT_PATH="${LINGUAFRAME_DEMO_TARGET_SRT_PATH:-/tmp/linguaframe-demo/target-subtitles.srt}"
TARGET_VTT_PATH="${LINGUAFRAME_DEMO_TARGET_VTT_PATH:-/tmp/linguaframe-demo/target-subtitles.vtt}"
```

After transcript preview, print target subtitle preview:

```bash
echo "Target subtitle preview for job $job_id:"
get_job_subtitles "$BASE_URL" "$job_id" "zh-CN" | python3 -m json.tool
```

Download:

```bash
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_JSON "$TARGET_SUBTITLE_JSON_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_SRT "$TARGET_SRT_PATH"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_VTT "$TARGET_VTT_PATH"
```

- [x] **Step 3: Update docs**

Update README and agent docs to say the Docker success path now downloads eight artifacts:

```text
audio.wav
transcript.json
subtitles.srt
subtitles.vtt
target-subtitles.json
target-subtitles.srt
target-subtitles.vtt
worker-summary.json
```

Document:

```bash
curl http://localhost:8080/api/jobs/{jobId}/subtitles/zh-CN
```

Clarify this is deterministic demo translation and still not a real OpenAI language call.

- [x] **Step 4: Verify scripts**

Run:

```bash
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
bash -n scripts/demo/docker-e2e-retry.sh
```

Expected: all exit 0.

## Task 7: Full Verification And Merge Readiness

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: this plan file as checkboxes are completed.

- [x] **Step 1: Run focused tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=SubtitleSegmentRepositoryTests,SubtitleServiceTests,TranslationProviderTests,SubtitleExportServiceTests,LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test
```

Expected: pass.

- [x] **Step 2: Run full tests**

Run:

```bash
mvn test
```

Expected: `BUILD SUCCESS`, all tests pass. If sandbox blocks `RANDOM_PORT` tests, rerun with local socket access and record both outcomes.

- [x] **Step 3: Run Docker verification**

Run:

```bash
docker compose --env-file .env.example config
mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example up -d
scripts/demo/docker-e2e-success.sh
file /tmp/linguaframe-demo/audio.wav
python3 -m json.tool /tmp/linguaframe-demo/transcript.json
python3 -m json.tool /tmp/linguaframe-demo/target-subtitles.json
sed -n '1,80p' /tmp/linguaframe-demo/target-subtitles.srt
sed -n '1,80p' /tmp/linguaframe-demo/target-subtitles.vtt
python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json
docker compose --env-file .env.example down
```

Expected:

- job reaches `COMPLETED`.
- timeline includes `TARGET_SUBTITLE_EXPORT`.
- artifact count is 8.
- target subtitle JSON parses.
- target SRT/VTT contain deterministic `zh-CN` text.

- [x] **Step 4: Record evidence**

Append to `docs/progress/execution-log.md`:

- implementation summary.
- red/green test evidence.
- full test result.
- Docker E2E result.
- explicit note that this slice uses deterministic demo translation, not OpenAI.

- [x] **Step 5: Commit and merge**

After verification passes:

```bash
git status --short
git add .
git commit -m "Add target language subtitle MVP"
git checkout main
git merge --no-ff target-language-subtitle-mvp -m "Merge branch 'target-language-subtitle-mvp'"
mvn test
```

Expected: merge succeeds and full tests pass on `main`.
