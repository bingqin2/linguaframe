# Smoke Test Checklist

This checklist defines the end-to-end evidence LinguaFrame should eventually provide.

## Test Command Reference

Run commands from the repository root unless a command explicitly says otherwise.

### Automated Backend Tests

Use this as the default verification before committing backend changes:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected:

- Maven exits with code 0.
- Output ends with `BUILD SUCCESS`.
- Current suite reports all tests passing.

Some Spring Boot tests use random local ports. If sandboxed execution fails with `SocketException: Operation not permitted`, rerun the same command with local socket access enabled.

For a focused backend slice, run the relevant test class first:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests
```

### Docker Runtime Verification

Validate Compose configuration and rebuild the backend image:

```bash
docker compose --env-file .env.example config
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example build linguaframe-backend
docker compose --env-file .env.example build linguaframe-frontend
```

Expected:

- Compose renders MySQL, Redis, RabbitMQ, MinIO, backend, and frontend services.
- Compose renders `LINGUAFRAME_TRANSLATION_PROVIDER=demo`, `LINGUAFRAME_TTS_PROVIDER=demo`, `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=true`, all `LINGUAFRAME_COST_*` values, and empty OpenAI placeholders when using `.env.example`.
- Compose renders `LINGUAFRAME_FRONTEND_PORT=5173` and `LINGUAFRAME_API_PROXY_TARGET=http://linguaframe-backend:8080`.
- Maven builds `LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar`.
- Docker builds `linguaframe-linguaframe-backend:latest`.
- Docker builds `linguaframe-linguaframe-frontend:latest`.

### Frontend Demo Verification

Run frontend checks:

```bash
cd frontend
npm run test:run
npm run build
```

Expected:

- Vitest exits with code 0.
- Vite production build exits with code 0.

With the Docker stack running, open:

```text
http://localhost:5173
```

Expected browser behavior:

- Upload a short MP4 with a target language.
- A recent job appears in the browser-local recent jobs list.
- The selected job reaches `COMPLETED`, `FAILED`, or `CANCELLED`.
- Timeline, usage summary, and model-call panels render from `GET /api/jobs/{jobId}`.
- Transcript and subtitle preview panels render when backend preview data exists.
- Artifact download links appear when artifacts exist.
- The `Download result bundle` link appears in the `Artifacts` panel and points to `/api/jobs/{jobId}/artifacts/archive/download`.
- The `Download diagnostics` link appears in the selected job header and points to `/api/jobs/{jobId}/diagnostics/download`.
- Audio and video previews appear for `DUBBING_AUDIO` and `BURNED_VIDEO` artifacts.
- Failed jobs show a retry button.
- The `Retention cleanup` panel appears in the sidebar.
- Clicking `Preview cleanup` refreshes aggregate cleanup counts without deleting data.
- With `.env.example`, the panel reports dry-run/default-off behavior and `Run cleanup` requires browser confirmation before calling the backend.
- The `Demo readiness` panel shows budget guard state and the configured cost limit without exposing provider credentials.

### Docker E2E Demo

Start the stack:

```bash
docker compose --env-file .env.example up -d
```

Run private-demo preflight before uploading media:

```bash
LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh
```

Expected:

- Docker Compose config renders for the default stack and the split-worker profile.
- Backend health returns `UP`.
- Frontend responds on `http://localhost:5173`.
- If `LINGUAFRAME_DEMO_ACCESS_TOKEN` is configured, anonymous `/api/**` access returns `401` and the configured header succeeds.
- If `LINGUAFRAME_DEMO_SAMPLE_PATH` or `LINGUAFRAME_TEARS_SAMPLE_PATH` is configured, the path points to a readable non-empty file.

Run the successful E2E path:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected:

- The script uploads a tiny MP4 sample file under `/tmp/linguaframe-demo`.
- Job status reaches `COMPLETED`.
- Output includes `modelCallCount=2` by default and `modelCallCount=3` when TTS is enabled.
- Output includes `failedModelCallCount=0`.
- Output includes `estimatedCostUsd=0E-8` with `.env.example` cost rates.
- Output includes `MODEL_CALL TRANSCRIPTION DEMO demo-transcription SUCCEEDED`.
- Output includes `MODEL_CALL TRANSLATION DEMO demo-translation SUCCEEDED`.
- Output includes `MODEL_CALL TTS DEMO demo-tts SUCCEEDED` only when TTS is enabled.
- Timeline includes `WORKER_RECEIVED`, `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `TRANSCRIPT_SUBTITLE_EXPORT`, `TARGET_SUBTITLE_EXPORT`, `DUBBING_AUDIO_GENERATION`, `SUBTITLE_BURN_IN`, `ARTIFACT_SUMMARY`, and `COMPLETED`.
- Output includes `artifactCount=9` by default and `artifactCount=10` when TTS is enabled.
- Output includes `EXTRACTED_AUDIO audio.wav`.
- Output includes `TRANSCRIPT_JSON transcript.json`.
- Output includes `SUBTITLE_SRT subtitles.srt`.
- Output includes `SUBTITLE_VTT subtitles.vtt`.
- Output includes `TARGET_SUBTITLE_JSON target-subtitles.json`.
- Output includes `TARGET_SUBTITLE_SRT target-subtitles.srt`.
- Output includes `TARGET_SUBTITLE_VTT target-subtitles.vtt`.
- Output includes `DUBBING_AUDIO dubbing-audio.mp3` only when TTS is enabled.
- Output includes `BURNED_VIDEO burned-video.mp4`.
- Output includes `WORKER_SUMMARY worker-summary.json`.
- The script downloads `/tmp/linguaframe-demo/audio.wav`.
- The script downloads `/tmp/linguaframe-demo/transcript.json`.
- The script downloads `/tmp/linguaframe-demo/subtitles.srt`.
- The script downloads `/tmp/linguaframe-demo/subtitles.vtt`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.json`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.srt`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.vtt`.
- The script downloads `/tmp/linguaframe-demo/dubbing-audio.mp3` only when TTS is enabled.
- The script downloads `/tmp/linguaframe-demo/burned-video.mp4`.
- The script downloads `/tmp/linguaframe-demo/job-diagnostics.json`.
- The script downloads `/tmp/linguaframe-demo/worker-summary.json`.

Run the budget guard failure path only after recreating the backend with a tiny positive budget and a non-zero local cost rate:

```bash
LINGUAFRAME_COST_ENABLED=true \
LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true \
LINGUAFRAME_COST_MAX_JOB_COST_USD=0.000001 \
LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE=1 \
docker compose --env-file .env up -d --force-recreate linguaframe-backend

scripts/demo/docker-e2e-budget-guard.sh
```

Expected:

- Job status reaches `FAILED`.
- Output includes `failureReason=Job cost budget exceeded`.
- Output includes `modelCallCount` and `estimatedCostUsd` evidence from the failed job detail.
- Output includes diagnostics summary lines for the failed job.
- Timeline includes the stage where the budget guard stopped execution.
- No later guarded provider stage should run after the budget failure.

Inspect the downloaded artifacts:

```bash
file /tmp/linguaframe-demo/audio.wav
python3 -m json.tool /tmp/linguaframe-demo/transcript.json
cat /tmp/linguaframe-demo/subtitles.srt
cat /tmp/linguaframe-demo/subtitles.vtt
python3 -m json.tool /tmp/linguaframe-demo/target-subtitles.json
cat /tmp/linguaframe-demo/target-subtitles.srt
cat /tmp/linguaframe-demo/target-subtitles.vtt
file /tmp/linguaframe-demo/dubbing-audio.mp3
file /tmp/linguaframe-demo/burned-video.mp4
python3 -m json.tool /tmp/linguaframe-demo/job-diagnostics.json
python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json
```

Skip `file /tmp/linguaframe-demo/dubbing-audio.mp3` when TTS is disabled.

Expected transcript fields:

- `segments`
- `index`
- `startMs`
- `endMs`
- `text`

Expected target subtitle fields:

- `language`
- `index`
- `startMs`
- `endMs`
- `text`

Expected worker summary fields:

- `jobId`
- `videoId`
- `targetLanguage`
- `sourceObjectKey`
- `stage`
- `generatedAt`

The artifact must not contain local absolute paths, passwords, access keys, secret keys, or raw provider credentials.

Expected diagnostics fields:

- `generatedAt`
- `job.jobId`
- `job.status`
- `job.timelineEvents`
- `job.modelCalls`
- `job.qualityEvaluation`
- `artifacts`
- `artifactCount`

The diagnostics report must not contain object storage keys, local absolute paths, demo access tokens, API keys, raw transcript text, raw subtitle text, provider request payloads, or uploaded media bytes.

Expected job detail fields from `GET /api/jobs/{jobId}`:

- `usageSummary.modelCallCount`
- `usageSummary.failedModelCallCount`
- `usageSummary.estimatedCostUsd`
- `cacheSummary.providerCacheHitCount`
- `modelCalls`
- `modelCalls[].operation`
- `modelCalls[].provider`
- `modelCalls[].model`
- `modelCalls[].status`
- `modelCalls[].latencyMs`

### Provider Cache Verification

Run the cache-hit demo after the Docker stack is healthy:

```bash
scripts/demo/docker-e2e-cache-hit.sh
```

The script runs two compatible jobs with the same extracted audio, provider, model, and prompt/version settings, downloads both job details and diagnostics reports, and fails if the second job does not expose a provider cache hit.

Expected:

- The second compatible transcription job timeline includes `CACHE_HIT`.
- `GET /api/jobs/{jobId}` for the second job reports `cacheSummary.providerCacheHitCount >= 1`.
- The second job still writes fresh `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, and `SUBTITLE_VTT` artifacts.
- The second job does not create another transcription provider model call.
- With quality evaluation enabled, the second compatible quality evaluation job timeline includes `CACHE_HIT`.
- The second compatible quality evaluation job writes a fresh current-job `qualityEvaluation` result in `GET /api/jobs/{jobId}`.
- The second compatible quality evaluation job does not create another evaluation provider model call.
- Evidence files are written under `/tmp/linguaframe-demo/cache-hit/`.

### Optional OpenAI Transcription Verification

Use this only when validating the paid provider path with a local `.env` file and a real short speech sample:

```bash
cp .env.example .env
```

Set:

```text
OPENAI_API_KEY=<your key>
OPENAI_BASE_URL=https://api.openai.com
OPENAI_TRANSCRIPTION_MODEL=whisper-1
OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS=120
LINGUAFRAME_TRANSCRIPTION_PROVIDER=openai
LINGUAFRAME_TRANSCRIPTION_ENABLED=true
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 scripts/demo/docker-e2e-success.sh
```

Expected:

- Job status reaches `COMPLETED`.
- Transcript preview reflects the supplied speech sample.
- `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, and `SUBTITLE_VTT` artifacts are present.
- Logs and persisted failure reasons do not expose the API key or raw OpenAI response body.
- This path may consume OpenAI credits.

### Optional OpenAI Translation Verification

Use this only when validating the paid provider path with a local `.env` file:

```bash
cp .env.example .env
```

Set:

```text
OPENAI_API_KEY=<your key>
OPENAI_TRANSLATION_MODEL=<model from current OpenAI docs>
LINGUAFRAME_TRANSLATION_PROVIDER=openai
LINGUAFRAME_TRANSLATION_ENABLED=true
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected:

- Job status reaches `COMPLETED`.
- Target subtitle preview and `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, and `TARGET_SUBTITLE_VTT` artifacts are present.
- Logs and persisted failure reasons do not expose the API key or raw OpenAI response body.
- This path may consume OpenAI credits.

### Optional OpenAI TTS Verification

Use this only when validating the paid provider path with a local `.env` file:

```bash
cp .env.example .env
```

Set:

```text
OPENAI_API_KEY=<your key>
OPENAI_TTS_MODEL=gpt-4o-mini-tts
OPENAI_TTS_VOICE=alloy
OPENAI_TTS_TIMEOUT_SECONDS=120
LINGUAFRAME_TTS_ENABLED=true
LINGUAFRAME_TTS_PROVIDER=openai
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected:

- Job status reaches `COMPLETED`.
- `DUBBING_AUDIO` artifact is present as `dubbing-audio.mp3`.
- The downloaded file has content type `audio/mpeg`.
- Logs and persisted failure reasons do not expose the API key or raw OpenAI response body.
- This path may consume OpenAI credits.

### Failure And Retry Demo

Use the forced worker failure path when retry behavior changes:

```bash
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true docker compose --env-file .env.example up -d --build
scripts/demo/docker-e2e-retry.sh
```

Expected:

- First execution reaches `FAILED`.
- Failure metadata is visible on `GET /api/jobs/{jobId}`.
- After restarting backend with failure disabled, retry reaches `COMPLETED`.
- `retryCount` increments to `1`.

### Cleanup

Stop containers after live verification:

```bash
docker compose --env-file .env.example down
```

Use `-v` only when intentionally deleting local MySQL, RabbitMQ, Redis, and MinIO volumes.

## Foundation Smoke Test

Run:

```bash
cd LinguaFrame
./mvnw test
./mvnw spring-boot:run
```

Expected:

- Tests pass.
- Backend starts.
- Health endpoint returns HTTP 200 after it is added.

## Docker Compose Smoke Test

Run after Docker Compose is added:

```bash
docker compose config
docker compose up --build
```

Expected:

- MySQL starts.
- Redis starts.
- RabbitMQ starts.
- MinIO starts.
- Backend starts and connects to dependencies.
- Frontend starts after it is added.

## Docker E2E Demo Smoke Test

Run after the Docker stack is healthy:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected:

- Docker stack starts with `docker compose --env-file .env.example up --build`.
- `scripts/demo/docker-e2e-success.sh` prints `status=COMPLETED`.
- `scripts/demo/docker-e2e-success.sh` prints `artifactCount=9`.
- `/tmp/linguaframe-demo/audio.wav` is downloaded.
- `/tmp/linguaframe-demo/transcript.json` is downloaded.
- `/tmp/linguaframe-demo/subtitles.srt` is downloaded.
- `/tmp/linguaframe-demo/subtitles.vtt` is downloaded.
- `/tmp/linguaframe-demo/target-subtitles.json` is downloaded.
- `/tmp/linguaframe-demo/target-subtitles.srt` is downloaded.
- `/tmp/linguaframe-demo/target-subtitles.vtt` is downloaded.
- `/tmp/linguaframe-demo/burned-video.mp4` is downloaded.
- `/tmp/linguaframe-demo/worker-summary.json` is downloaded.
- Forced smoke-stage failure produces `status=FAILED`.
- Retry after disabling failure produces `status=COMPLETED`.
- Job timeline includes worker receive, smoke stage, audio extraction, transcript/source subtitle export, target subtitle export, subtitle burn-in, artifact summary, and completion events.

## Upload Smoke Test

Input:

- A 30-60 second video.

Expected:

- Upload succeeds.
- Source video artifact is stored.
- Video record exists.
- Localization job is created.
- Job detail API returns status.

## Pipeline Smoke Test

Expected stages:

```text
UPLOADED
QUEUED
EXTRACTING_AUDIO
TRANSCRIBING
TRANSLATING
GENERATING_SUBTITLES
GENERATING_DUBBING
BURNING_SUBTITLES
COMPLETED
```

Expected artifacts:

- Extracted audio.
- Source transcript.
- Source SRT or VTT.
- Target-language SRT or VTT.
- TTS audio.
- Subtitle-burned video.

## Failure Smoke Test

Test with:

- Unsupported file type, or
- Missing OpenAI API key, or
- Forced FFmpeg failure.

Expected:

- Job moves to `FAILED`.
- Failed stage is recorded.
- Safe failure reason is visible.
- Retry button or retry API is available when appropriate.
- Secrets are not exposed.

## Cost Smoke Test

Expected:

- Job detail shows OpenAI call count.
- Job detail shows audio duration or token usage when available.
- Job detail shows estimated cost.
- Cost is labeled as an estimate.
