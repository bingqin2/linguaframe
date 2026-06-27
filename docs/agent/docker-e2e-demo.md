# Docker E2E Demo

This guide verifies the current LinguaFrame backend demo path: upload a small MP4 sample file, create a job, dispatch through RabbitMQ, execute the smoke worker stage, extract audio with FFmpeg, generate transcript/source subtitle/target subtitle artifacts, optionally generate dubbing audio, burn target subtitles into a preview video, download artifacts, and inspect the job timeline. The default `.env.example` path uses deterministic transcription and translation with TTS disabled; OpenAI transcription, translation, and TTS are optional local `.env` modes.

## Start The Stack

Package the backend jar from the repository root:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
```

Start Docker Compose:

```bash
docker compose --env-file .env.example up --build
```

Wait until the backend health endpoint is up:

```bash
curl http://localhost:8080/actuator/health
```

Open the browser demo:

```text
http://localhost:5173
```

The React demo uploads videos to `/api/media/uploads`, stores recent uploaded job ids in browser local storage, polls `GET /api/jobs/{jobId}`, and renders timeline events, usage summary, model-call records, transcript/subtitle previews, artifacts, media previews, downloads, and failed-job retry.

## Private Demo Preflight

Run this before the short or full demo scripts:

```bash
scripts/demo/private-demo-preflight.sh
```

The preflight does not upload media and does not call OpenAI. It verifies required commands, `.env`, Docker Compose rendering, backend health, backend runtime freshness, frontend reachability, optional demo-token gate behavior, and any configured `LINGUAFRAME_DEMO_SAMPLE_PATH` or `LINGUAFRAME_TEARS_SAMPLE_PATH`.

If the backend container was built from older code, preflight fails before any upload with the package and recreate commands:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up -d --build linguaframe-backend
```

## Successful Job Demo

In another terminal, run:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected output includes:

```text
status=COMPLETED
modelCallCount=2
failedModelCallCount=0
estimatedCostUsd=0E-8
- MODEL_CALL TRANSCRIPTION DEMO demo-transcription SUCCEEDED
- MODEL_CALL TRANSLATION DEMO demo-translation SUCCEEDED
- WORKER_RECEIVED STARTED
- WORKER_SMOKE STARTED
- WORKER_SMOKE SUCCEEDED
- AUDIO_EXTRACTION STARTED
- AUDIO_EXTRACTION SUCCEEDED
- TRANSCRIPT_SUBTITLE_EXPORT STARTED
- TRANSCRIPT_SUBTITLE_EXPORT SUCCEEDED
- TARGET_SUBTITLE_EXPORT STARTED
- TARGET_SUBTITLE_EXPORT SUCCEEDED
- DUBBING_AUDIO_GENERATION STARTED
- DUBBING_AUDIO_GENERATION SUCCEEDED
- SUBTITLE_BURN_IN STARTED
- SUBTITLE_BURN_IN SUCCEEDED
- ARTIFACT_SUMMARY STARTED
- ARTIFACT_SUMMARY SUCCEEDED
- COMPLETED SUCCEEDED
artifactCount=9
- EXTRACTED_AUDIO audio.wav
- TRANSCRIPT_JSON transcript.json
- SUBTITLE_SRT subtitles.srt
- SUBTITLE_VTT subtitles.vtt
- TARGET_SUBTITLE_JSON target-subtitles.json
- TARGET_SUBTITLE_SRT target-subtitles.srt
- TARGET_SUBTITLE_VTT target-subtitles.vtt
- BURNED_VIDEO burned-video.mp4
- WORKER_SUMMARY worker-summary.json
```

With the default `.env.example`, the dubbing stage is recorded and then skipped without creating an audio artifact. When `LINGUAFRAME_TTS_ENABLED=true`, expected output also includes:

```text
modelCallCount=3
- MODEL_CALL TTS DEMO demo-tts SUCCEEDED
artifactCount=10
- DUBBING_AUDIO dubbing-audio.mp3
```

`GET /api/jobs/{jobId}` returns `usageSummary` and `modelCalls`. Default cost rates in `.env.example` are `0`, so the estimated cost is visible but remains zero unless local `LINGUAFRAME_COST_*` rates are configured.

The script downloads generated artifacts to:

```text
/tmp/linguaframe-demo/audio.wav
/tmp/linguaframe-demo/transcript.json
/tmp/linguaframe-demo/subtitles.srt
/tmp/linguaframe-demo/subtitles.vtt
/tmp/linguaframe-demo/target-subtitles.json
/tmp/linguaframe-demo/target-subtitles.srt
/tmp/linguaframe-demo/target-subtitles.vtt
/tmp/linguaframe-demo/burned-video.mp4
/tmp/linguaframe-demo/worker-summary.json
```

`dubbing-audio.mp3` is downloaded only when TTS is enabled.

You can inspect artifact APIs manually:

```bash
JOB_ID=<job id printed by the script>
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/artifacts"
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/transcript" | python3 -m json.tool
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/subtitles/zh-CN" | python3 -m json.tool
ARTIFACT_ID=<artifact id from the artifact list>
curl -fL "http://localhost:8080/api/jobs/$JOB_ID/artifacts/$ARTIFACT_ID/download" -o /tmp/linguaframe-demo/artifact.bin
```

This demo verifies FFmpeg audio extraction, deterministic transcript/source subtitle/target subtitle export, and FFmpeg subtitle burn-in. With `.env.example`, it does not perform OpenAI transcription, OpenAI translation, or OpenAI TTS; transcript and target subtitles use deterministic demo providers.

## Provider Cache-Hit Demo

Run this after the successful job path when you need terminal evidence that repeat compatible jobs reuse provider results:

```bash
scripts/demo/docker-e2e-cache-hit.sh
```

The script uploads the same sample twice with the same target language and current provider/model/prompt settings. It waits for both jobs to complete, downloads job detail and diagnostics reports for both runs, validates diagnostics safety, and fails if the second job has no provider cache hit.

Expected output includes:

```text
First job summary:
modelCallCount=2
providerCacheHitCount=0
Second job summary:
providerCacheHitCount=1
- PROVIDER_CACHE_HIT ...
Cache-hit comparison:
firstModelCallCount=2
secondProviderCacheHitCount=1
```

Exact model-call and cache-hit counts can be higher when TTS or quality evaluation is enabled. Evidence files are written to:

```text
/tmp/linguaframe-demo/cache-hit/first-job.json
/tmp/linguaframe-demo/cache-hit/second-job.json
/tmp/linguaframe-demo/cache-hit/first-diagnostics.json
/tmp/linguaframe-demo/cache-hit/second-diagnostics.json
```

## Full Tears of Steel Demo

Use this path for a larger public-media demo after the short smoke path is working. The local source video is not committed to git; see `docs/product/demo-references.md` for attribution and license notes.

The default upload duration limit is 5 minutes. The current `tos_casting-720p.mp4` demo sample is about 1:50, so the script uploads it as one complete file under the limit; it does not cut a shorter clip.

For the current full-video demo, prefer `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=false` in local `.env` before recreating the backend. Previous full-video attempts reached OpenAI translation/evaluation but exceeded the default 180-second subtitle burn-in timeout.

Package the backend jar and recreate the backend container from the repository root:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up -d --build linguaframe-backend
```

Run the full-video script:

```bash
scripts/demo/docker-e2e-tears-of-steel-full.sh
```

The script defaults to:

```text
/Users/wangbingqin/Downloads/tos_casting-720p.mp4
```

Override the input path when needed:

```bash
LINGUAFRAME_TEARS_SAMPLE_PATH=/absolute/path/to/video.mp4 scripts/demo/docker-e2e-tears-of-steel-full.sh
```

The script downloads core artifacts to `/tmp/linguaframe-demo/tears-of-steel-full/`. `BURNED_VIDEO` and `DUBBING_AUDIO` are optional because burn-in and TTS can be disabled for stable local runs.

## Optional OpenAI Transcription Demo

Use this path only with a local `.env` file that contains real OpenAI credentials and with a real short speech sample. The generated default demo sample is a synthetic test video with a tone and should not be used to judge speech-to-text behavior.

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
LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE=0
```

Then run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 scripts/demo/docker-e2e-success.sh
```

Expected output still includes `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, `SUBTITLE_VTT`, and transcript preview JSON. The transcript preview should reflect the supplied speech sample. This path can call OpenAI and may consume credits; never commit `.env`.

## Optional OpenAI Translation Demo

Use this path only with a local `.env` file that contains real OpenAI credentials:

```bash
cp .env.example .env
```

Set:

```text
OPENAI_API_KEY=<your key>
OPENAI_TRANSLATION_MODEL=<model from current OpenAI docs>
LINGUAFRAME_TRANSLATION_PROVIDER=openai
LINGUAFRAME_TRANSLATION_ENABLED=true
LINGUAFRAME_COST_TRANSLATION_INPUT_USD_PER_1M_TOKENS=0
LINGUAFRAME_COST_TRANSLATION_OUTPUT_USD_PER_1M_TOKENS=0
```

Then run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected output still includes `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, `TARGET_SUBTITLE_VTT`, and target subtitle preview JSON. This path can call OpenAI and may consume credits; never commit `.env`.

## Optional OpenAI TTS Demo

Use this path only with a local `.env` file that contains real OpenAI credentials:

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
LINGUAFRAME_COST_TTS_USD_PER_1M_CHARS=0
```

Then run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected output includes `DUBBING_AUDIO dubbing-audio.mp3`. This MVP produces one continuous MP3 audio artifact; it does not do lip sync or audio/video mixing. This path can call OpenAI and may consume credits; never commit `.env`.

## Failure And Retry Demo

Start the stack with forced smoke-stage failure:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true docker compose --env-file .env.example up --build
```

In another terminal, run:

```bash
scripts/demo/docker-e2e-retry.sh
```

When prompted, stop the backend, restart it with failure disabled, then press Enter in the script terminal:

```bash
docker compose --env-file .env.example up --build linguaframe-backend
```

Expected output first includes `status=FAILED`, then `status=COMPLETED`.

## Cleanup

```bash
docker compose --env-file .env.example down
```

Add `-v` only when you intentionally want to delete local MySQL, RabbitMQ, Redis, and MinIO volumes.
