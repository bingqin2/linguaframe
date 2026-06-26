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
- Current suite reports `Tests run: 65, Failures: 0, Errors: 0`.

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
```

Expected:

- Compose renders MySQL, Redis, RabbitMQ, MinIO, and backend services.
- Maven builds `LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar`.
- Docker builds `linguaframe-linguaframe-backend:latest`.

### Docker E2E Demo

Start the stack:

```bash
docker compose --env-file .env.example up -d
```

Run the successful E2E path:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected:

- The script uploads a tiny sample file under `/tmp/linguaframe-demo`.
- Job status reaches `COMPLETED`.
- Timeline includes `WORKER_RECEIVED`, `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `ARTIFACT_SUMMARY`, and `COMPLETED`.
- Output includes `artifactCount=2`.
- Output includes `EXTRACTED_AUDIO audio.wav`.
- Output includes `WORKER_SUMMARY worker-summary.json`.
- The script downloads `/tmp/linguaframe-demo/audio.wav`.
- The script downloads `/tmp/linguaframe-demo/worker-summary.json`.

Inspect the downloaded artifacts:

```bash
file /tmp/linguaframe-demo/audio.wav
python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json
```

Expected fields:

- `jobId`
- `videoId`
- `targetLanguage`
- `sourceObjectKey`
- `stage`
- `generatedAt`

The artifact must not contain local absolute paths, passwords, access keys, secret keys, or raw provider credentials.

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
- `scripts/demo/docker-e2e-success.sh` prints `artifactCount=2`.
- `/tmp/linguaframe-demo/audio.wav` is downloaded.
- `/tmp/linguaframe-demo/worker-summary.json` is downloaded.
- Forced smoke-stage failure produces `status=FAILED`.
- Retry after disabling failure produces `status=COMPLETED`.
- Job timeline includes worker receive, smoke stage, audio extraction, artifact summary, and completion events.

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
