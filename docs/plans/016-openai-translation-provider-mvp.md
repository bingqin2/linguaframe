# OpenAI Translation Provider MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in OpenAI-backed translation provider that can replace the deterministic demo provider for target subtitle generation without changing subtitle storage, export, or preview APIs.

**Architecture:** Keep `TranslationProvider` as the worker-facing boundary. Add provider selection through Spring conditional beans, add OpenAI-specific configuration under `linguaframe.translation.openai`, and implement `OpenAiTranslationProvider` with Spring `RestClient` against the OpenAI Responses API. The provider validates structured segment output, preserves source timing, and returns the existing `TranslationResultBo`.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring `RestClient`, Jackson, JUnit 5, Spring `MockRestServiceServer`, Maven, Docker Compose.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `openai-translation-provider-mvp`.
- Keep the default Docker/demo path on `LINGUAFRAME_TRANSLATION_PROVIDER=demo`; OpenAI must be opt-in.
- Never commit real API keys, local `.env`, raw OpenAI responses, raw media paths, object storage credentials, or Authorization headers.
- Use `OPENAI_API_KEY` and `OPENAI_TRANSLATION_MODEL` from local environment only.
- Do not add OpenAI transcription, TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, cost tracking, or quality evaluation in this slice.
- Automated tests must not call the OpenAI network; use mocked HTTP only.
- Before coding the final request body, verify the current official OpenAI Responses API shape through the OpenAI developer docs MCP or official OpenAI docs.
- Record final verification evidence and any live OpenAI run command in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `LINGUAFRAME_TRANSLATION_PROVIDER=demo` keeps the existing deterministic provider.
- `LINGUAFRAME_TRANSLATION_PROVIDER=openai` creates one OpenAI translation provider bean.
- OpenAI provider startup fails fast with a sanitized message if `OPENAI_API_KEY` or `OPENAI_TRANSLATION_MODEL` is missing.
- The worker continues to run `TARGET_SUBTITLE_EXPORT` through the existing `TranslationProvider` interface.
- OpenAI translation output is parsed as structured JSON with one translated segment per source transcript segment.
- Segment indexes and timings are preserved from source transcript segments.
- OpenAI HTTP failures, malformed model output, segment-count mismatches, and blank translated text fail the current worker stage with sanitized errors.
- README and agent docs explain how to run demo mode and how to opt into a live OpenAI translation run.

## Design Choices

Recommended approach: add OpenAI as a second `TranslationProvider` implementation and select it with `linguaframe.translation.provider`. This uses the boundary created by the previous target-subtitle slice and keeps paid API behavior isolated.

Alternatives considered:

- Replace the demo provider directly: less code, but it would make every Docker demo require a paid API call and a secret.
- Add an official OpenAI Java SDK dependency now: convenient, but unnecessary for this narrow endpoint and harder to keep stable without verifying the current artifact/version.
- Add a generic LLM gateway abstraction first: more extensible, but too much structure before transcription, TTS, and evaluation providers exist.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranslationProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: OpenAI Translation Configuration And Provider Selection

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTranslationProvider.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`

**Interfaces:**
- `LinguaFrameProperties.Translation#getOpenai(): OpenAi`
- `LinguaFrameProperties.Translation.OpenAi#getApiKey(): String`
- `LinguaFrameProperties.Translation.OpenAi#getModel(): String`
- `LinguaFrameProperties.Translation.OpenAi#getBaseUrl(): String`
- `LinguaFrameProperties.Translation.OpenAi#getTimeoutSeconds(): int`
- `DemoTranslationProvider` is active only when `linguaframe.translation.provider=demo` or the provider property is missing.

- [x] **Step 1: Write failing config and provider-selection tests**

Extend `LinguaFramePropertiesTests#bindsDefaultRuntimeProperties` with:

```java
assertThat(properties.getTranslation().getOpenai().getApiKey()).isEmpty();
assertThat(properties.getTranslation().getOpenai().getModel()).isEmpty();
assertThat(properties.getTranslation().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
assertThat(properties.getTranslation().getOpenai().getTimeoutSeconds()).isEqualTo(60);
```

Add a binding test:

```java
@Test
void bindsOpenAiTranslationRuntimeProperties() {
    contextRunner
            .withPropertyValues(
                    "linguaframe.translation.enabled=true",
                    "linguaframe.translation.provider=openai",
                    "linguaframe.translation.openai.api-key=test-key",
                    "linguaframe.translation.openai.model=test-model",
                    "linguaframe.translation.openai.base-url=http://localhost:9999",
                    "linguaframe.translation.openai.timeout-seconds=15"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                LinguaFrameProperties boundProperties = context.getBean(LinguaFrameProperties.class);
                assertThat(boundProperties.getTranslation().getProvider()).isEqualTo("openai");
                assertThat(boundProperties.getTranslation().getOpenai().getApiKey()).isEqualTo("test-key");
                assertThat(boundProperties.getTranslation().getOpenai().getModel()).isEqualTo("test-model");
                assertThat(boundProperties.getTranslation().getOpenai().getBaseUrl()).isEqualTo("http://localhost:9999");
                assertThat(boundProperties.getTranslation().getOpenai().getTimeoutSeconds()).isEqualTo(15);
            });
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: fail because OpenAI translation properties do not exist.

- [x] **Step 2: Add nested OpenAI properties**

Add this shape under `LinguaFrameProperties.Translation`:

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
    @Max(300)
    private int timeoutSeconds = 60;

    // getters and setters
}
```

Do not annotate `apiKey` or `model` with `@NotBlank` here because demo mode must boot without OpenAI secrets. The OpenAI provider constructor validates them only when the provider is selected.

- [x] **Step 3: Wire environment properties**

Update all runtime YAML files with:

```yaml
linguaframe:
  translation:
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_TRANSLATION_MODEL:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      timeout-seconds: ${OPENAI_TRANSLATION_TIMEOUT_SECONDS:60}
```

Update `.env.example` with empty, non-secret placeholders:

```text
OPENAI_API_KEY=
OPENAI_TRANSLATION_MODEL=
OPENAI_BASE_URL=https://api.openai.com
OPENAI_TRANSLATION_TIMEOUT_SECONDS=60
```

Keep:

```text
LINGUAFRAME_TRANSLATION_PROVIDER=demo
```

Update `docker-compose.yml` backend environment to pass through the same four OpenAI values.

- [x] **Step 4: Make demo provider conditional**

Annotate `DemoTranslationProvider`:

```java
@Component
@ConditionalOnProperty(prefix = "linguaframe.translation", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTranslationProvider implements TranslationProvider {
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,TranslationProviderTests test
```

Expected: pass after property and conditional changes.

## Task 2: OpenAI Responses Translation Provider

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranslationProviderTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`

**Interfaces:**
- `OpenAiTranslationProvider implements TranslationProvider`
- Constructor dependencies: `LinguaFrameProperties properties`, `RestClient.Builder restClientBuilder`, `ObjectMapper objectMapper`
- Request target: `{baseUrl}/v1/responses`
- Output contract: JSON object with `segments`, each item containing `index` and `text`.

- [x] **Step 1: Verify official API schema before implementation**

Before writing the provider request DTOs, verify the current OpenAI Responses API request and response shape through the OpenAI developer docs MCP if it is available after restarting Codex, or through official OpenAI docs if browser access works.

The implementation must still satisfy these repo-level constraints even if the official schema has small naming differences:

- send `Authorization: Bearer <OPENAI_API_KEY>`.
- send the configured model string from `OPENAI_TRANSLATION_MODEL`.
- request structured JSON output when supported.
- parse translated text from `output_text` or the documented message output text field.
- keep all tests mocked and deterministic.

- [x] **Step 2: Write failing successful-translation test**

Create `OpenAiTranslationProviderTests#translatesWithResponsesApiAndPreservesTiming`.

The test should:

- build a `RestClient.Builder`.
- bind `MockRestServiceServer` to that builder.
- create `LinguaFrameProperties` with provider `openai`, key `test-openai-key`, model `test-translation-model`, base URL from the mock server, and timeout `5`.
- expect `POST /v1/responses`.
- assert the `Authorization` header is `Bearer test-openai-key`.
- assert request JSON contains `model: test-translation-model`.
- return a response whose output text is:

```json
{"segments":[{"index":0,"text":"LinguaFrame 向你问好。"},{"index":1,"text":"这个演示字幕来自 OpenAI。"}]}
```

Assert the provider returns:

```text
0:0:1800:LinguaFrame 向你问好。
1:1800:3600:这个演示字幕来自 OpenAI。
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests test
```

Expected: fail because `OpenAiTranslationProvider` does not exist.

- [x] **Step 3: Implement provider skeleton and startup validation**

Create `OpenAiTranslationProvider`:

```java
@Component
@ConditionalOnProperty(prefix = "linguaframe.translation", name = "provider", havingValue = "openai")
public class OpenAiTranslationProvider implements TranslationProvider {

    private final LinguaFrameProperties.Translation.OpenAi openai;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiTranslationProvider(
            LinguaFrameProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.openai = properties.getTranslation().getOpenai();
        this.objectMapper = objectMapper;
        requireConfigured(openai.getApiKey(), "OPENAI_API_KEY");
        requireConfigured(openai.getModel(), "OPENAI_TRANSLATION_MODEL");
        this.restClient = restClientBuilder
                .baseUrl(openai.getBaseUrl())
                .requestFactory(clientHttpRequestFactory(openai.getTimeoutSeconds()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openai.getApiKey())
                .build();
    }
}
```

`requireConfigured` must throw `IllegalStateException` with this sanitized message:

```text
OpenAI translation provider requires OPENAI_API_KEY and OPENAI_TRANSLATION_MODEL.
```

- [x] **Step 4: Implement request generation**

Build a Responses API request with:

- `model`: configured model.
- system instruction: translate subtitle text only, preserve meaning and line order, return JSON only.
- user payload: `jobId`, `targetLanguage`, and an ordered `segments` array with `index` and `text`.
- structured output schema for:

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["segments"],
  "properties": {
    "segments": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["index", "text"],
        "properties": {
          "index": {"type": "integer"},
          "text": {"type": "string"}
        }
      }
    }
  }
}
```

Keep DTOs as private records in `OpenAiTranslationProvider` unless they become too large.

- [x] **Step 5: Implement response parsing and validation**

Provider behavior:

- Extract response text from top-level `output_text` if present.
- Otherwise traverse the documented response output message content text fields.
- Parse the extracted text into `TranslatedSegmentsResponse`.
- Require exactly one output segment per input segment.
- Require each output `index` to match a source segment index.
- Require translated text to be non-blank after trim.
- Return `TranslationResultBo` using source `index`, `startMs`, and `endMs`.

Sanitized exception messages:

```text
OpenAI translation response did not contain text output.
OpenAI translation response was not valid JSON.
OpenAI translation returned 1 segments for 2 source segments.
OpenAI translation returned blank text for segment 1.
OpenAI translation returned an unknown segment index: 9.
```

Do not include raw response bodies in thrown messages.

- [x] **Step 6: Add failure-path tests**

Add tests for:

- missing API key/model fails during provider construction with the sanitized configuration message.
- HTTP 401 returns `OpenAI translation request failed with status 401`.
- non-JSON output returns `OpenAI translation response was not valid JSON`.
- missing output text returns `OpenAI translation response did not contain text output`.
- mismatched segment count returns the exact count mismatch message.
- blank translated text returns the blank segment message.

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests test
```

Expected: pass after provider implementation.

## Task 3: Worker Integration Regression Tests

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/TranslationProviderTests.java`
- Run existing worker and controller tests without code changes unless provider selection breaks wiring.

**Interfaces:**
- `TargetSubtitleExportPipelineStage` continues to consume only `TranslationProvider`.
- Existing fake `RecordingTranslationProvider` tests stay valid.

- [x] **Step 1: Keep demo provider tests explicit**

Rename the current `TranslationProviderTests` class or test methods to make clear they cover the demo provider, for example:

```java
class DemoTranslationProviderTests {
```

If the filename changes, update the Maven test command accordingly.

- [x] **Step 2: Run focused regression tests**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,DemoTranslationProviderTests,OpenAiTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test
```

Expected: pass with no network calls.

- [x] **Step 3: Verify Docker config remains demo by default**

Run:

```bash
docker compose --env-file .env.example config
```

Expected:

- rendered backend environment contains `LINGUAFRAME_TRANSLATION_PROVIDER: demo`.
- rendered backend environment contains empty `OPENAI_API_KEY`.
- command does not require a real OpenAI key.

## Task 4: Documentation And Run Commands

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Demo mode command remains:

```bash
docker compose --env-file .env.example up --build
```

- Live OpenAI mode command uses a local `.env` file that is ignored by git.

- [x] **Step 1: Document safe local OpenAI setup**

Add README guidance:

```bash
cp .env.example .env
# edit .env locally; do not commit it
OPENAI_API_KEY=<your key>
OPENAI_TRANSLATION_MODEL=<model from current OpenAI docs>
LINGUAFRAME_TRANSLATION_PROVIDER=openai
LINGUAFRAME_TRANSLATION_ENABLED=true
```

State that `OPENAI_TRANSLATION_MODEL` is intentionally user-configured because model availability changes.

- [x] **Step 2: Document live run commands**

Add the opt-in live translation run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

State that this command can call the OpenAI API and may consume credits.

- [x] **Step 3: Update agent docs and checklist**

Update `docs/agent/docker-e2e-demo.md` and `docs/agent/smoke-test-checklist.md` to distinguish:

- default deterministic demo translation.
- optional OpenAI translation validation.
- expected output remains target subtitle JSON/SRT/VTT artifacts and subtitle preview.

- [x] **Step 4: Record decisions and verification**

Add to `docs/progress/decisions.md`:

```text
Decision: Add OpenAI translation as an opt-in provider behind TranslationProvider while keeping the Docker demo on deterministic translation by default.
Impact: Real OpenAI translation can be exercised with local secrets, but the core demo and automated tests remain reproducible and cost-free.
```

After implementation, add actual command results to `docs/progress/execution-log.md`.

## Final Verification

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,DemoTranslationProviderTests,OpenAiTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test
```

Run:

```bash
mvn -pl LinguaFrame test
```

Run:

```bash
docker compose --env-file .env.example config
```

Optional live OpenAI verification, only with a local `.env` containing real credentials:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected live result:

- job completes.
- `GET /api/jobs/{jobId}/subtitles/zh-CN` returns translated target subtitle segments.
- artifact list includes `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, and `TARGET_SUBTITLE_VTT`.
- logs and persisted failure reasons do not expose the API key or raw OpenAI response body.
