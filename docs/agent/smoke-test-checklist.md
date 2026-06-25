# Smoke Test Checklist

This checklist defines the end-to-end evidence LinguaFrame should eventually provide.

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
- Chinese SRT or VTT.
- English SRT or VTT.
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
