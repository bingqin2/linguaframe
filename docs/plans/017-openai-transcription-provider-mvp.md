# OpenAI Transcription Provider MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in OpenAI-backed transcription provider so LinguaFrame can generate transcript segments from extracted audio instead of always using deterministic demo transcript text.

**Architecture:** Keep `TranscriptionProvider` as the worker-facing boundary and add `OpenAiTranscriptionProvider` behind Spring conditional provider selection. The existing `TranscriptSubtitleExportPipelineStage` continues to read the extracted audio artifact, call `TranscriptionProvider`, persist transcript segments, and export transcript JSON/SRT/VTT. Default Docker demos remain deterministic; live OpenAI transcription is enabled only through local `.env` settings and a real speech sample.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring `RestClient`, multipart form upload, Jackson, JUnit 5, Spring `MockRestServiceServer`, Maven, Docker Compose.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `openai-transcription-provider-mvp`.
- Keep `.env.example` on `LINGUAFRAME_TRANSCRIPTION_PROVIDER=demo`; OpenAI transcription must be opt-in.
- Never commit real API keys, local `.env`, raw provider responses, raw media paths, object storage credentials, or Authorization headers.
- Use `OPENAI_API_KEY`, `OPENAI_TRANSCRIPTION_MODEL`, `OPENAI_BASE_URL`, and `OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS` from local environment only.
- Automated tests must not call OpenAI or any OpenAI-compatible gateway; use mocked HTTP only.
- The provider must fail with sanitized messages and must not expose raw response bodies.
- This slice does not add TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, cost tracking, model-call audit tables, or translation quality evaluation.
- Before coding the final multipart request, verify the current official OpenAI speech-to-text API shape through official OpenAI docs. If docs are unreachable, implement against the documented `/v1/audio/transcriptions` shape and record that limitation in `docs/progress/execution-log.md`.
- Record final verification evidence and any optional live run command in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `LINGUAFRAME_TRANSCRIPTION_PROVIDER=demo` keeps the existing deterministic transcript provider.
- `LINGUAFRAME_TRANSCRIPTION_PROVIDER=openai` creates one OpenAI transcription provider bean.
- OpenAI provider startup fails fast if `OPENAI_API_KEY` or `OPENAI_TRANSCRIPTION_MODEL` is missing.
- The worker still uses the existing `TranscriptionProvider` interface; no pipeline rewrite is needed.
- The OpenAI provider sends extracted audio bytes as multipart form data to `{baseUrl}/v1/audio/transcriptions`.
- The provider requests timestamped segment output and converts provider segment seconds into millisecond `TranscriptionSegmentBo` values.
- Missing segment timestamps, blank text, invalid timestamps, malformed JSON, and HTTP failures fail the current worker stage with sanitized errors.
- The demo script keeps the generated deterministic sample for default mode, but supports an existing `LINGUAFRAME_DEMO_SAMPLE_PATH` so live transcription can be validated with a real short speech video.

## Design Choices

Recommended approach: add OpenAI as a second `TranscriptionProvider` implementation and select it with `linguaframe.transcription.provider`. This mirrors the translation provider slice and makes paid, network-dependent behavior opt-in.

Alternatives considered:

- Replace the demo transcription provider directly: smaller change, but it would make every local demo require a real speech file and paid API credentials.
- Add OpenAI SDK now: convenient, but the current codebase already uses `RestClient` for OpenAI translation and mocked HTTP tests are straightforward.
- Fall back to one untimed transcript segment when timestamps are missing: easier to parse, but it weakens the subtitle product because all downstream subtitles depend on segment timing.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProviderTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/OpenAiTranscriptionContextTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Transcription Configuration And Provider Selection

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranscriptionProvider.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- `LinguaFrameProperties.Transcription#getOpenai(): OpenAi`
- `LinguaFrameProperties.Transcription.OpenAi#getApiKey(): String`
- `LinguaFrameProperties.Transcription.OpenAi#getModel(): String`
- `LinguaFrameProperties.Transcription.OpenAi#getBaseUrl(): String`
- `LinguaFrameProperties.Transcription.OpenAi#getTimeoutSeconds(): int`
- `DemoTranscriptionProvider` is active only when `linguaframe.transcription.provider=demo` or the provider property is missing.

- [ ] **Step 1: Write failing transcription property tests**

Extend `LinguaFramePropertiesTests#bindsDefaultRuntimeProperties`:

```java
assertThat(properties.getTranscription().getOpenai().getApiKey()).isEmpty();
assertThat(properties.getTranscription().getOpenai().getModel()).isEmpty();
assertThat(properties.getTranscription().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
assertThat(properties.getTranscription().getOpenai().getTimeoutSeconds()).isEqualTo(120);
```

Add:

```java
@Test
void bindsOpenAiTranscriptionRuntimeProperties() {
    contextRunner
            .withPropertyValues(
                    "linguaframe.transcription.enabled=true",
                    "linguaframe.transcription.provider=openai",
                    "linguaframe.transcription.openai.api-key=test-key",
                    "linguaframe.transcription.openai.model=whisper-1",
                    "linguaframe.transcription.openai.base-url=http://localhost:9999",
                    "linguaframe.transcription.openai.timeout-seconds=45"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                assertThat(boundProperties.getTranscription().getProvider()).isEqualTo("openai");
                assertThat(boundProperties.getTranscription().getOpenai().getApiKey()).isEqualTo("test-key");
                assertThat(boundProperties.getTranscription().getOpenai().getModel()).isEqualTo("whisper-1");
                assertThat(boundProperties.getTranscription().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                assertThat(boundProperties.getTranscription().getOpenai().getTimeoutSeconds()).isEqualTo(45);
            });
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: fail because `Transcription#getOpenai()` does not exist.

- [ ] **Step 2: Add nested OpenAI transcription properties**

Add this shape under `LinguaFrameProperties.Transcription`:

```java
@Valid
private final OpenAi openai = new OpenAi();

public OpenAi getOpenai() {
    return openai;
}

public static class OpenAi {

    private String apiKey = "";

    private String model = "";

    @NotBlank
    private String baseUrl = "https://api.openai.com";

    @Min(1)
    @Max(600)
    private int timeoutSeconds = 120;

    // getters and setters
}
```

Keep `apiKey` and `model` nullable-at-configuration-level because demo mode must boot without OpenAI secrets.

- [ ] **Step 3: Wire environment properties**

Update application YAML files:

```yaml
linguaframe:
  transcription:
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_TRANSCRIPTION_MODEL:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      timeout-seconds: ${OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS:120}
```

Update `.env.example`:

```text
OPENAI_TRANSCRIPTION_MODEL=
OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS=120
```

Keep:

```text
LINGUAFRAME_TRANSCRIPTION_PROVIDER=demo
```

Update `docker-compose.yml` backend environment to pass through `OPENAI_TRANSCRIPTION_MODEL` and `OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS`.

- [ ] **Step 4: Make demo transcription provider conditional**

Annotate `DemoTranscriptionProvider`:

```java
@Component
@ConditionalOnProperty(prefix = "linguaframe.transcription", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTranscriptionProvider implements TranscriptionProvider {
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: pass after property and conditional changes.

## Task 2: OpenAI Audio Transcription Provider

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranscriptionProviderTests.java`

**Interfaces:**
- `OpenAiTranscriptionProvider implements TranscriptionProvider`
- Constructor dependencies: `LinguaFrameProperties properties`, `RestClient.Builder restClientBuilder`, `ObjectMapper objectMapper`
- Request target: `{baseUrl}/v1/audio/transcriptions`
- Response contract: JSON with a `segments` array containing `start`, `end`, and `text` fields.

- [ ] **Step 1: Verify official API shape before implementation**

Before writing request code, verify current official OpenAI speech-to-text docs for:

- endpoint path.
- required multipart fields.
- timestamp segment support.
- `response_format` value for segment timestamps.
- supported model requirements for timestamped segments.

Expected current implementation target:

```text
POST {baseUrl}/v1/audio/transcriptions
Authorization: Bearer <OPENAI_API_KEY>
multipart/form-data:
  file=<audio bytes>
  model=<OPENAI_TRANSCRIPTION_MODEL>
  response_format=verbose_json
  timestamp_granularities[]=segment
```

If official docs are unavailable, keep this request shape and record the docs-access limitation in `docs/progress/execution-log.md`.

- [ ] **Step 2: Write failing successful-transcription test**

Create `OpenAiTranscriptionProviderTests#transcribesVerboseJsonSegments`.

The test should expect:

- `POST https://api.openai.test/v1/audio/transcriptions`
- `Authorization: Bearer test-openai-key`
- multipart body contains `name="model"` and `whisper-1`
- multipart body contains `name="response_format"` and `verbose_json`
- multipart body contains `name="timestamp_granularities[]"` and `segment`
- response:

```json
{
  "text": "Hello from LinguaFrame. This is a real transcript.",
  "segments": [
    {"id": 0, "start": 0.0, "end": 1.8, "text": "Hello from LinguaFrame."},
    {"id": 1, "start": 1.8, "end": 3.6, "text": "This is a real transcript."}
  ]
}
```

Assert:

```text
0:0:1800:Hello from LinguaFrame.
1:1800:3600:This is a real transcript.
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests test
```

Expected: fail because `OpenAiTranscriptionProvider` does not exist.

- [ ] **Step 3: Implement provider skeleton and startup validation**

Create `OpenAiTranscriptionProvider`:

```java
@Component
@ConditionalOnProperty(prefix = "linguaframe.transcription", name = "provider", havingValue = "openai")
public class OpenAiTranscriptionProvider implements TranscriptionProvider {

    private static final String MISSING_CONFIGURATION_MESSAGE =
            "OpenAI transcription provider requires OPENAI_API_KEY and OPENAI_TRANSCRIPTION_MODEL.";

    private final LinguaFrameProperties.Transcription.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiTranscriptionProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this(properties, buildRestClient(properties.getTranscription().getOpenai(), restClientBuilder), objectMapper);
    }

    OpenAiTranscriptionProvider(
            LinguaFrameProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.openai = properties.getTranscription().getOpenai();
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        requireConfigured(openai.getApiKey());
        requireConfigured(openai.getModel());
    }
}
```

Use the same constructor pattern as `OpenAiTranslationProvider` so tests can inject a mocked `RestClient`.

- [ ] **Step 4: Implement multipart request**

Implementation requirements:

- Use `MultipartBodyBuilder`.
- Add `model` part from `openai.getModel()`.
- Add `response_format` part with `verbose_json`.
- Add `timestamp_granularities[]` part with `segment`.
- Add `file` part using a `ByteArrayResource` whose `getFilename()` returns `jobId + ".wav"`.
- Send `Content-Type: multipart/form-data`.
- Call `.post().uri("/v1/audio/transcriptions")`.

HTTP failures must throw:

```text
OpenAI transcription request failed with status 401
```

Use the actual status code. Do not include the provider response body.

- [ ] **Step 5: Implement response parsing and validation**

Provider behavior:

- Parse response JSON into private records or `JsonNode`.
- Require `segments` to exist and contain at least one item.
- Convert each segment from seconds to milliseconds using `Math.round(seconds * 1000)`.
- Assign output indexes from response order: `0`, `1`, `2`, ...
- Require `start >= 0`.
- Require `end > start`.
- Require `text` to be non-blank after trim.
- Return `new TranscriptionResultBo(List<TranscriptionSegmentBo>)`.

Sanitized exception messages:

```text
OpenAI transcription response did not contain segment timestamps.
OpenAI transcription response was not valid JSON.
OpenAI transcription returned blank text for segment 1.
OpenAI transcription returned invalid timestamps for segment 1.
```

- [ ] **Step 6: Add failure-path tests**

Add tests for:

- missing API key/model fails during provider construction with `OpenAI transcription provider requires OPENAI_API_KEY and OPENAI_TRANSCRIPTION_MODEL.`
- HTTP 401 returns `OpenAI transcription request failed with status 401`
- non-JSON response returns `OpenAI transcription response was not valid JSON.`
- missing or empty `segments` returns `OpenAI transcription response did not contain segment timestamps.`
- blank segment text returns `OpenAI transcription returned blank text for segment 1.`
- `end <= start` returns `OpenAI transcription returned invalid timestamps for segment 1.`

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests test
```

Expected: pass after provider implementation.

## Task 3: Spring Context And Worker Regression Coverage

**Files:**
- Create: `LinguaFrame/src/test/java/com/linguaframe/OpenAiTranscriptionContextTests.java`
- Run existing worker/controller tests without changing production pipeline code unless provider selection breaks wiring.

**Interfaces:**
- `TranscriptSubtitleExportPipelineStage` still consumes only `TranscriptionProvider`.
- Existing worker tests continue to use fake or demo transcription providers.

- [ ] **Step 1: Add OpenAI transcription context test**

Create:

```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "linguaframe.transcription.provider=openai",
        "linguaframe.transcription.openai.api-key=test-openai-key",
        "linguaframe.transcription.openai.model=whisper-1"
})
class OpenAiTranscriptionContextTests {

    @Test
    void contextLoadsWithOpenAiTranscriptionProvider() {
    }
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionContextTests test
```

Expected: fail before provider-selection wiring is complete, then pass.

- [ ] **Step 2: Run focused regression tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,OpenAiTranscriptionContextTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,DemoTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test
```

Expected: pass with no real OpenAI calls.

- [ ] **Step 3: Verify Docker config remains demo by default**

Run:

```bash
docker compose --env-file .env.example config
```

Expected:

- rendered backend environment contains `LINGUAFRAME_TRANSCRIPTION_PROVIDER: demo`.
- rendered backend environment contains empty `OPENAI_TRANSCRIPTION_MODEL`.
- rendered backend environment contains `OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS: 120`.
- command does not require a real OpenAI key.

## Task 4: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Default deterministic demo command remains:

```bash
docker compose --env-file .env.example up --build
scripts/demo/docker-e2e-success.sh
```

- Optional live transcription demo requires local `.env` plus a real speech sample path.

- [ ] **Step 1: Preserve user-supplied demo sample files**

Change the demo helper so an existing non-empty `LINGUAFRAME_DEMO_SAMPLE_PATH` is not overwritten.

Implementation shape:

```bash
ensure_demo_sample() {
  local path="$1"
  if [[ -s "$path" ]]; then
    return 0
  fi
  create_demo_sample "$path"
}
```

Update `scripts/demo/docker-e2e-success.sh` to call:

```bash
ensure_demo_sample "$SAMPLE_PATH"
```

instead of always calling `create_demo_sample`.

- [ ] **Step 2: Document safe local OpenAI transcription setup**

Add README guidance:

```text
OPENAI_API_KEY=<your key>
OPENAI_BASE_URL=https://api.openai.com
OPENAI_TRANSCRIPTION_MODEL=whisper-1
OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS=120
LINGUAFRAME_TRANSCRIPTION_PROVIDER=openai
LINGUAFRAME_TRANSCRIPTION_ENABLED=true
```

State that a real short speech sample is required for live transcription validation because the generated default demo sample is a synthetic tone.

- [ ] **Step 3: Document optional full live transcription + translation run**

Document:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 scripts/demo/docker-e2e-success.sh
```

If live translation is also enabled, include:

```text
OPENAI_TRANSLATION_MODEL=<your working translation model>
LINGUAFRAME_TRANSLATION_PROVIDER=openai
LINGUAFRAME_TRANSLATION_ENABLED=true
```

State that the command can call OpenAI and may consume credits.

- [ ] **Step 4: Record decisions and verification**

Add to `docs/progress/decisions.md`:

```text
Decision: Add OpenAI transcription as an opt-in provider behind TranscriptionProvider while keeping deterministic transcript generation as the default demo path.
Impact: The pipeline can validate real speech-to-text locally with secrets and a real speech sample, while automated tests and default Docker demos remain reproducible and cost-free.
```

After implementation, add actual command results to `docs/progress/execution-log.md`.

## Final Verification

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,OpenAiTranscriptionContextTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,DemoTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test
```

Run:

```bash
mvn -pl LinguaFrame test
```

Run:

```bash
docker compose --env-file .env.example config
```

Optional live verification, only with local `.env` and a real short speech sample:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 scripts/demo/docker-e2e-success.sh
```

Expected live result:

```text
status=COMPLETED
- TRANSCRIPT_SUBTITLE_EXPORT SUCCEEDED
- TARGET_SUBTITLE_EXPORT SUCCEEDED
artifactCount=8
```

The transcript preview should contain text derived from the supplied speech sample, not `Hello from LinguaFrame.` unless that phrase is actually spoken in the sample.

## Out Of Scope

- OpenAI TTS.
- Subtitle-burned preview video.
- Frontend upload dashboard.
- Usage and cost tables.
- Prompt/version audit records.
- Retry policy changes.
- Multiple transcription providers beyond `demo` and `openai`.
- Automatic generation of a real speech fixture.
