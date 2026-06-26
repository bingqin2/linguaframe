# TTS Dubbing Audio MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in TTS dubbing audio stage so completed localization jobs can produce a downloadable MP3 audio artifact from target-language subtitles.

**Architecture:** Add a narrow `TtsProvider` boundary with deterministic demo and OpenAI implementations, then insert a `DUBBING_AUDIO_GENERATION` pipeline stage after target subtitle export and before artifact summary. The stage reads persisted target subtitles, synthesizes one continuous audio file, and stores it through the existing job artifact service.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring `RestClient`, Jackson, JUnit 5, Spring `MockRestServiceServer`, Maven, Docker Compose, OpenAI-compatible audio speech API.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `tts-dubbing-audio-mvp`.
- Keep `.env.example` on `LINGUAFRAME_TTS_PROVIDER=demo`; OpenAI TTS must be opt-in.
- Never commit real API keys, local `.env`, raw provider responses, raw media paths, object storage credentials, or Authorization headers.
- Automated tests must not call OpenAI or any OpenAI-compatible gateway; use mocked HTTP only.
- TTS output is one continuous MP3 artifact for MVP; do not attempt lip sync, segment-level alignment, audio mixing, or video burn-in.
- If target subtitles are disabled or unavailable, TTS should skip when `linguaframe.tts.enabled=false` and fail clearly when enabled but no target subtitles exist.
- Record final verification evidence and optional live run instructions in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `LINGUAFRAME_TTS_ENABLED=false` skips dubbing audio generation.
- `LINGUAFRAME_TTS_ENABLED=true` and `LINGUAFRAME_TTS_PROVIDER=demo` creates a deterministic small MP3-like artifact suitable for local demo validation without network calls.
- `LINGUAFRAME_TTS_PROVIDER=openai` creates an OpenAI TTS provider bean and fails fast if `OPENAI_API_KEY`, `OPENAI_TTS_MODEL`, or `OPENAI_TTS_VOICE` is missing.
- The worker records timeline events for `DUBBING_AUDIO_GENERATION`.
- The generated artifact appears in `/api/jobs/{jobId}/artifacts` with type `DUBBING_AUDIO`, filename `dubbing-audio.mp3`, and content type `audio/mpeg`.
- Existing artifact download endpoint can download the MP3 without a new controller route.

## Design Choices

Recommended approach: add TTS as another provider-backed pipeline stage, mirroring transcription and translation. This keeps paid network behavior opt-in, keeps the worker modular, and advances the demo toward the product target without introducing frontend or video rendering scope.

Alternatives considered:

- Generate TTS directly in the subtitle export stage: fewer classes, but it mixes subtitle persistence, export, and audio synthesis into one stage.
- Add frontend playback now: visible, but the backend must first expose a durable audio artifact.
- Store per-segment audio: better future alignment, but too large for this MVP and not needed to prove a downloadable dubbing track.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsRequestBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TtsProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/OpenAiTtsContextTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTtsProviderTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: TTS Configuration, Stage, And Artifact Types

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
- `LinguaFrameProperties#getTts(): Tts`
- `LinguaFrameProperties.Tts#getProvider(): String`
- `LinguaFrameProperties.Tts.OpenAi#getModel(): String`
- `LocalizationJobStage.DUBBING_AUDIO_GENERATION`
- `JobArtifactType.DUBBING_AUDIO`

- [ ] **Step 1: Write failing property tests**

Add default assertions:

```java
assertThat(properties.getTts().isEnabled()).isFalse();
assertThat(properties.getTts().getProvider()).isEqualTo("demo");
assertThat(properties.getTts().getOpenai().getApiKey()).isEmpty();
assertThat(properties.getTts().getOpenai().getModel()).isEmpty();
assertThat(properties.getTts().getOpenai().getVoice()).isEmpty();
assertThat(properties.getTts().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com");
assertThat(properties.getTts().getOpenai().getTimeoutSeconds()).isEqualTo(120);
```

Add `bindsOpenAiTtsRuntimeProperties()` with `linguaframe.tts.enabled=true`, `provider=openai`, `api-key=test-key`, `model=gpt-4o-mini-tts`, `voice=alloy`, `base-url=http://localhost:9999`, and `timeout-seconds=45`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: fail because `getTts()` does not exist.

- [ ] **Step 2: Add TTS config and environment wiring**

Add `Tts` beside `Transcription` and `Translation` with `enabled=false`, `provider=demo`, nested `OpenAi` fields `apiKey`, `model`, `voice`, `baseUrl`, and `timeoutSeconds`.

Wire YAML:

```yaml
linguaframe:
  tts:
    enabled: ${LINGUAFRAME_TTS_ENABLED:false}
    provider: ${LINGUAFRAME_TTS_PROVIDER:demo}
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_TTS_MODEL:}
      voice: ${OPENAI_TTS_VOICE:}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      timeout-seconds: ${OPENAI_TTS_TIMEOUT_SECONDS:120}
```

Update `.env.example` and `docker-compose.yml` with these variables.

- [ ] **Step 3: Add enum values**

Add `DUBBING_AUDIO_GENERATION` between `TARGET_SUBTITLE_EXPORT` and `ARTIFACT_SUMMARY`. Add `DUBBING_AUDIO` to `JobArtifactType`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java LinguaFrame/src/main/resources/application.yaml LinguaFrame/src/main/resources/application-docker.yaml LinguaFrame/src/test/resources/application-test.yaml .env.example docker-compose.yml LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/LocalizationJobStage.java LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java
git commit -m "Add TTS runtime configuration"
```

## Task 2: TTS Provider Boundary And Implementations

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsRequestBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsResultBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/TtsProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTtsProviderTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/OpenAiTtsContextTests.java`

**Interfaces:**
- `TtsRequestBo(String jobId, String language, String text)`
- `TtsResultBo(byte[] audioContent, String filename, String contentType)`
- `TtsProvider#synthesize(TtsRequestBo request): TtsResultBo`

- [ ] **Step 1: Write failing OpenAI provider tests**

Test successful request:

- `POST https://api.openai.test/v1/audio/speech`
- `Authorization: Bearer test-openai-key`
- JSON contains `model`, `voice`, `input`, and `response_format=mp3`
- returns binary `audio/mpeg`
- result filename is `dubbing-audio.mp3`

Also test:

- missing model or voice fails fast with `OpenAI TTS provider requires OPENAI_API_KEY, OPENAI_TTS_MODEL, and OPENAI_TTS_VOICE.`
- HTTP failures produce `OpenAI TTS request failed with status 401`
- empty audio body produces `OpenAI TTS response was empty.`

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTtsProviderTests test
```

Expected: fail because provider types do not exist.

- [ ] **Step 2: Implement BOs and provider interface**

Add the two records and `TtsProvider` interface exactly as listed above.

- [ ] **Step 3: Implement demo provider**

`DemoTtsProvider` should be conditional on `linguaframe.tts.provider=demo` with `matchIfMissing=true`. Return deterministic bytes for a small MP3-like placeholder:

```java
byte[] audio = ("LINGUAFRAME_DEMO_DUBBING_AUDIO\n" + request.language() + "\n" + request.text()).getBytes(StandardCharsets.UTF_8);
return new TtsResultBo(audio, "dubbing-audio.mp3", "audio/mpeg");
```

- [ ] **Step 4: Implement OpenAI provider**

Use `RestClient` with base URL, bearer auth, timeout, and JSON body:

```json
{
  "model": "...",
  "voice": "...",
  "input": "...",
  "response_format": "mp3"
}
```

Return binary bytes as `TtsResultBo(audioBytes, "dubbing-audio.mp3", "audio/mpeg")`. Sanitize HTTP failures and never include response bodies in exception messages.

- [ ] **Step 5: Add Spring context coverage**

Create `OpenAiTtsContextTests` with properties enabling `linguaframe.tts.provider=openai`, dummy API key/model/voice, and assert exactly one `TtsProvider` bean exists.

Run:

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTtsProviderTests,OpenAiTtsContextTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsRequestBo.java LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsResultBo.java LinguaFrame/src/main/java/com/linguaframe/job/service/TtsProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTtsProviderTests.java LinguaFrame/src/test/java/com/linguaframe/OpenAiTtsContextTests.java
git commit -m "Add TTS provider implementations"
```

## Task 3: Dubbing Audio Pipeline Stage

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Stage: `LocalizationJobStage.DUBBING_AUDIO_GENERATION`
- Reads: `SubtitleService#listSubtitles(jobId, targetLanguage)`
- Writes: `JobArtifactService#createArtifact(new CreateJobArtifactCommand(jobId, DUBBING_AUDIO, "dubbing-audio.mp3", "audio/mpeg", audioBytes))`

- [ ] **Step 1: Write failing stage tests**

Create tests for:

- disabled TTS skips provider and artifact creation.
- enabled TTS with target subtitles joins subtitle text in segment order and creates `DUBBING_AUDIO`.
- enabled TTS with no target subtitles throws `Target subtitles not found for dubbing audio generation.`

Run:

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests test
```

Expected: fail because the stage does not exist.

- [ ] **Step 2: Implement stage**

Implementation rules:

- `stage()` returns `DUBBING_AUDIO_GENERATION`.
- If `properties.getTts().isEnabled()` is false, return immediately.
- Read subtitles using `context.job().id()` and `context.job().targetLanguage()`.
- Build text by sorting by `SubtitleSegmentVo#index`, trimming each text, and joining with a newline.
- Call `ttsProvider.synthesize(new TtsRequestBo(jobId, targetLanguage, joinedText))`.
- Store the returned audio as `DUBBING_AUDIO`.

- [ ] **Step 3: Extend execution ordering coverage**

Add or update a `LocalizationJobExecutionServiceTests` case to include `TargetSubtitleExportPipelineStage`, `DubbingAudioGenerationPipelineStage`, and `WorkerSummaryArtifactPipelineStage`, then assert timeline order contains:

```text
TARGET_SUBTITLE_EXPORT:STARTED
TARGET_SUBTITLE_EXPORT:SUCCEEDED
DUBBING_AUDIO_GENERATION:STARTED
DUBBING_AUDIO_GENERATION:SUCCEEDED
ARTIFACT_SUMMARY:STARTED
ARTIFACT_SUMMARY:SUCCEEDED
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java
git commit -m "Add dubbing audio pipeline stage"
```

## Task 4: Documentation, Demo Guidance, And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Documentation requirements:**
- Explain default demo TTS behavior.
- Explain OpenAI TTS opt-in variables.
- Explain that this MVP generates one continuous MP3 and does not do lip sync.
- Add artifact verification command to the Docker E2E checklist.

- [ ] **Step 1: Update docs**

Document:

```env
LINGUAFRAME_TTS_ENABLED=true
LINGUAFRAME_TTS_PROVIDER=openai
OPENAI_TTS_MODEL=gpt-4o-mini-tts
OPENAI_TTS_VOICE=alloy
OPENAI_TTS_TIMEOUT_SECONDS=120
```

Record the architectural decision that TTS is a provider-backed worker stage and not part of subtitle export.

- [ ] **Step 2: Run focused verification**

Run:

```bash
mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,OpenAiTtsProviderTests,OpenAiTtsContextTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
docker compose --env-file .env.example config
```

Expected: all pass; Compose should show `LINGUAFRAME_TTS_PROVIDER=demo`.

- [ ] **Step 3: Run full backend verification**

Run:

```bash
mvn -pl LinguaFrame test
```

Expected: pass.

- [ ] **Step 4: Commit docs**

```bash
git add README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/progress/decisions.md docs/progress/execution-log.md
git commit -m "Document TTS dubbing audio MVP"
```

## Final Integration

After all tasks pass:

1. Verify `git status --short --branch` is clean on `tts-dubbing-audio-mvp`.
2. Merge the completed branch back to `main`.
3. Run `mvn -pl LinguaFrame test` again on `main`.
4. Report validation evidence and note that live OpenAI TTS was not run unless explicitly tested with the user's API key.
