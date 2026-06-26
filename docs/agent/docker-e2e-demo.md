# Docker E2E Demo

This guide verifies the current LinguaFrame backend demo path: upload a small sample file, create a job, dispatch through RabbitMQ, execute the smoke worker stage, extract audio with FFmpeg, generate transcript/source subtitle/target subtitle artifacts, download those artifacts, and inspect the job timeline. The default `.env.example` path uses deterministic transcription and translation; OpenAI transcription and translation are optional local `.env` modes.

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

## Successful Job Demo

In another terminal, run:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected output includes:

```text
status=COMPLETED
- WORKER_RECEIVED STARTED
- WORKER_SMOKE STARTED
- WORKER_SMOKE SUCCEEDED
- AUDIO_EXTRACTION STARTED
- AUDIO_EXTRACTION SUCCEEDED
- TRANSCRIPT_SUBTITLE_EXPORT STARTED
- TRANSCRIPT_SUBTITLE_EXPORT SUCCEEDED
- TARGET_SUBTITLE_EXPORT STARTED
- TARGET_SUBTITLE_EXPORT SUCCEEDED
- ARTIFACT_SUMMARY STARTED
- ARTIFACT_SUMMARY SUCCEEDED
- COMPLETED SUCCEEDED
artifactCount=8
- EXTRACTED_AUDIO audio.wav
- TRANSCRIPT_JSON transcript.json
- SUBTITLE_SRT subtitles.srt
- SUBTITLE_VTT subtitles.vtt
- TARGET_SUBTITLE_JSON target-subtitles.json
- TARGET_SUBTITLE_SRT target-subtitles.srt
- TARGET_SUBTITLE_VTT target-subtitles.vtt
- WORKER_SUMMARY worker-summary.json
```

The script downloads generated artifacts to:

```text
/tmp/linguaframe-demo/audio.wav
/tmp/linguaframe-demo/transcript.json
/tmp/linguaframe-demo/subtitles.srt
/tmp/linguaframe-demo/subtitles.vtt
/tmp/linguaframe-demo/target-subtitles.json
/tmp/linguaframe-demo/target-subtitles.srt
/tmp/linguaframe-demo/target-subtitles.vtt
/tmp/linguaframe-demo/worker-summary.json
```

You can inspect artifact APIs manually:

```bash
JOB_ID=<job id printed by the script>
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/artifacts"
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/transcript" | python3 -m json.tool
curl -fsS "http://localhost:8080/api/jobs/$JOB_ID/subtitles/zh-CN" | python3 -m json.tool
ARTIFACT_ID=<artifact id from the artifact list>
curl -fL "http://localhost:8080/api/jobs/$JOB_ID/artifacts/$ARTIFACT_ID/download" -o /tmp/linguaframe-demo/artifact.bin
```

This demo verifies FFmpeg audio extraction plus deterministic transcript, source subtitle, and target subtitle export. With `.env.example`, it does not perform OpenAI transcription, OpenAI translation, TTS, or subtitle burn-in; transcript and target subtitles use deterministic demo providers.

## Optional OpenAI Transcription Demo

Use this path only with a local `.env` file that contains real OpenAI credentials and with a real short speech sample. The generated default demo sample is a synthetic tone and should not be used to judge speech-to-text behavior.

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
```

Then run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up --build
scripts/demo/docker-e2e-success.sh
```

Expected output still includes `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, `TARGET_SUBTITLE_VTT`, and target subtitle preview JSON. This path can call OpenAI and may consume credits; never commit `.env`.

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
