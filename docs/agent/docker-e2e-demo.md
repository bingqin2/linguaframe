# Docker E2E Demo

This guide verifies the current LinguaFrame backend demo path: upload a small MP4 sample file, create a job, dispatch through RabbitMQ, execute the smoke worker stage, extract audio with FFmpeg, generate transcript/source subtitle/target subtitle artifacts, optionally generate dubbing audio, burn target subtitles into a preview video, download artifacts, and inspect the job timeline. The default `.env.example` path uses deterministic transcription and translation with TTS disabled; OpenAI transcription, translation, and TTS are optional local `.env` modes.

## Start The Stack

For the default local browser demo, use the one-command startup from the repository root:

```bash
scripts/demo/start-local-demo.sh
```

It packages the backend jar, recreates `linguaframe-backend`, starts the local Vite frontend fallback when `http://localhost:5173` is unavailable, runs private-demo preflight, and prints the browser URL plus next E2E commands. If the script starts the local frontend itself, that frontend process is stopped when the script exits.

Use the lower-level commands below when you need to debug a specific Docker or frontend step.

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

Open Swagger UI when you need to inspect the backend API contract used by the demo:

```text
http://localhost:8080/swagger-ui/index.html
```

The OpenAPI document at `http://localhost:8080/v3/api-docs` should include the upload, job, progress event, retry/cancel, artifact, diagnostics, transcript, subtitle, demo-session, runtime, prompt-template, operator, and retention cleanup APIs. When `LINGUAFRAME_DEMO_ACCESS_TOKEN` is configured, use the React header `Owner access token` form to start a browser owner session. For Swagger and curl, use Swagger UI's `Authorize` action with the `DemoAccessToken` API key value; Swagger and `/v3/api-docs` stay public, while `/api/**` calls require the owner-session cookie or `X-LinguaFrame-Demo-Token`.

The React demo validates selected videos through `/api/media/uploads/validate` before upload, uploads valid videos to `/api/media/uploads`, stores recent uploaded job ids in browser local storage, polls `GET /api/jobs/{jobId}`, and renders timeline events, usage summary, result delivery, media delivery, subtitle review, failure triage, model-call records, transcript/subtitle previews, artifacts, downloads, and failed-job retry.

Before uploading, check the browser `Demo runbook`, `Live checks`, and upload form `Validate file` result. The runbook shows the one-command startup path, short/cache/full E2E commands, local frontend and backend health URLs, sample-media guidance, and current runtime constraints such as upload duration, provider modes, budget guard, and subtitle burn-in state. The live-check panel should show database, Redis, RabbitMQ, MinIO, and FFmpeg as `UP`, `DOWN`, or `SKIPPED`. The upload validation panel should show the selected file's validation code, message, size, duration, and configured limits before any job is created.

After opening a job, check the `Result delivery` panel before the detailed artifact table. It should list transcript JSON, source subtitles, target subtitles, dubbing audio, burned video, and worker summary as `Ready`, `Preview only`, or `Missing`. Ready rows should expose direct artifact downloads, short SHA-256 hashes, and generated/reused cache state. The panel should also keep `Download result bundle` and `Download diagnostics` visible without exposing object keys, local paths, or provider payloads.

Check the `Media delivery` panel when playable outputs exist. It should show `DUBBING_AUDIO`, generated `BURNED_VIDEO`, and reviewed `REVIEWED_BURNED_VIDEO` as separate outputs with browser players, direct download links, content type, size, short SHA-256 hashes, and generated/reused cache state. Terminal `mediaDelivery*` summary lines must include only artifact type, filename, content type, and generated/reused state.

Check the read-only `Subtitle review` panel after transcript and target subtitles load. It should show segment count, missing target count, timing mismatch count, average and max duration, quality score/verdict, downloadable subtitle artifact count, and source/target comparison rows. Evidence exports and terminal summaries should include only subtitle-review metadata, not raw transcript or subtitle text.

Check the `Subtitle draft editor` panel when target subtitles exist. Edit one draft row, save it, verify the saved/unsaved counters update, use corrected JSON/SRT/VTT export links, publish reviewed subtitle artifacts, reset unsaved edits, and clear the saved draft. Reviewed JSON/SRT/VTT artifacts use the saved draft overlay and appear in result delivery, artifact downloads, archives, diagnostics, evidence, and terminal demo summaries. The reviewed burned-video option is explicit and creates a separate artifact only when FFmpeg burn-in is enabled; generated subtitle artifacts, TTS audio, and generated burned video stay unchanged.

Check the `Demo review guide` panel before presenting a selected job. It should show `Presentation ready` only when the pipeline is terminal, reviewed outputs are available, delivery is ready, evidence links are available, and the session report is ready. Its links should jump to `Result delivery`, `Pipeline progress`, `Subtitle review`, `Delivery handoff`, `Demo evidence`, and `Demo session report`; presenter notes should contain only safe metadata.

Check the `Delivery handoff` panel after publishing reviewed subtitles. It should show `Ready for handoff`, reviewed subtitle count, reviewed burned-video availability, generated audit artifact count, reviewed handoff artifacts, audit artifacts, safe verification links, `Download delivery manifest`, `Download handoff package`, and `Download demo run package`. The downloadable Markdown manifest should reference artifacts and evidence without embedding raw transcript text, raw subtitles, object keys, local paths, provider payloads, credentials, or media bytes. The handoff package should contain reviewed subtitle artifacts plus safe manifest/evidence files only.

Check the `Demo handoff checklist` panel before presenting the run. It should summarize job completion, terminal pipeline state, reviewed subtitle readiness, media outputs, evidence downloads, quality signal, cost/model-call evidence, cache evidence, and failure triage when applicable. `Copy checklist` and `Download checklist JSON` should export only metadata and safe download links. `Download handoff package` should point to `/api/jobs/{jobId}/handoff-package/download`, and `Download demo run package` should point to `/api/jobs/{jobId}/demo-run-package/download`.

Check the `Demo session report` panel as the final reviewer-facing summary for one run. It should show `Session ready` or `Session needs attention`, then group safe metadata into `Input and job`, `Generated outputs`, `Handoff evidence`, `Cost and cache`, and `Failure triage` when applicable. `Copy report`, `Download report Markdown`, `Download handoff package`, `Download demo run package`, and `/tmp/linguaframe-demo/demo-session-report.md` should not include raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.

Use the demo run package when a reviewer wants one safe ZIP workspace for a single run. `GET /api/jobs/{jobId}/demo-run-package/download` should include `manifest.json`, `README.md`, `job-detail.json`, `diagnostics.json`, `evidence.md`, `quality-evidence.md`, `delivery-manifest.md`, `demo-handoff-checklist.md`, and `demo-session-report.md`. It should not include uploaded media, generated media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or API keys.

Check the `Pipeline progress` panel during short, full-video, cache-hit, and OpenAI smoke runs. It is derived from `job_timeline_events` and does not change worker execution. It should show current stage, completed stage count, terminal state, total measured stage duration, slowest stage, and compact per-stage status/duration rows. Use the detailed `Timeline` panel when you need event-level evidence.

For failed jobs, check the `Failure triage` panel before retrying. It should show a safe category such as `OPENAI_AUTH_OR_MODEL`, `OPENAI_TIMEOUT_OR_NETWORK`, `BUDGET_GUARD`, `MEDIA_PROCESSING`, `STORAGE_OR_ARTIFACT`, `WORKER_OR_QUEUE`, `CONFIGURATION`, `USER_CANCELLED`, or `UNKNOWN`, plus retryability, a recommended action, and an optional static runbook command. The same triage appears in diagnostics JSON, backend Markdown evidence, browser evidence export, and terminal script summaries.

Use the `Demo evidence` panel after the job is visible in the browser. `Copy evidence` produces a browser-generated Markdown summary for notes or interview walkthroughs, `Download evidence JSON` writes a local metadata file, `Download backend evidence` downloads the API-generated Markdown report, and `Download evidence bundle` downloads a metadata-only ZIP with `manifest.json`, `evidence.md`, and `diagnostics.json`. The evidence should include job status, pipeline progress, subtitle-review counts, subtitle-draft counts, reviewed artifact counts, reviewed burned-video availability, timeline stages, usage, cache counts, artifact hashes, and safe download routes, but no raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, media bytes, or generated artifact bytes.

Use the `Quality evaluation` panel when a quality result exists. `Copy quality evidence` and `Download quality evidence` produce reviewer-facing Markdown from browser metadata, while `Download backend quality evidence` calls `/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download`. The terminal success and OpenAI smoke scripts also download `/tmp/linguaframe-demo/quality-evidence.md` and print `qualityEvaluation*` plus `qualityEvidenceMarkdown*` summary lines.

After a job starts, inspect job-scoped backend logs when debugging worker behavior:

```bash
JOB_ID=<job id from the demo output>
docker logs linguaframe-backend 2>&1 | grep "jobId=$JOB_ID"
docker logs linguaframe-backend 2>&1 | grep "stage=TARGET_SUBTITLE_EXPORT"
docker logs linguaframe-backend 2>&1 | grep "workerRole=COMBINED"
```

Expected log context fields are `jobId`, `videoId`, `stage`, and `workerRole`. Logs must not include OpenAI keys, demo tokens, object storage credentials, source object keys, local filesystem paths, raw transcript text, raw subtitles, provider payloads, or media bytes.

## Private Demo Preflight

Run this before the short or full demo scripts:

```bash
scripts/demo/private-demo-preflight.sh
```

The preflight does not upload media and does not call OpenAI unless `LINGUAFRAME_OPENAI_CONNECTIVITY_CHECK_ENABLED=true`. It verifies required commands, `.env`, Docker Compose rendering, backend health, backend runtime freshness, live MySQL/Redis/RabbitMQ/MinIO/FFmpeg checks, OpenAI connectivity status (`SKIPPED` by default), frontend reachability, owner-session status/login/logout, optional demo-token header behavior, and any configured `LINGUAFRAME_DEMO_SAMPLE_PATH` or `LINGUAFRAME_TEARS_SAMPLE_PATH`.

If Docker cannot build the frontend image because the Node base image registry or mirror is unavailable, start the frontend locally while keeping the backend stack in Docker:

```bash
scripts/demo/frontend-local-dev.sh
```

The script starts Vite on `http://localhost:5173`. Leave it running in its terminal while running preflight or browser demos from another terminal.

If the backend container was built from older code, preflight fails before any upload with the package and recreate commands:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up -d --build linguaframe-backend
```

For a single-owner private server demo, use the reverse-proxy overlay instead of changing the local Compose file:

```bash
cp .env.private-demo.example .env.private-demo
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh
docker compose --env-file .env.private-demo \
  -f docker-compose.yml \
  -f deploy/private-demo/docker-compose.private-demo.yml \
  up -d --build
```

The overlay adds Caddy on ports 80/443 and keeps backend/frontend host ports internal. After startup, run `LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-preflight.sh`.

For private-demo persistence checks, validate backup and restore shape before touching service data:

```bash
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-backup.sh --dry-run
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-backup.sh --output-dir /tmp/linguaframe-private-demo-backups
LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-restore.sh --dry-run --backup-dir /tmp/linguaframe-private-demo-backups/<timestamp>.linguaframe-backup
```

The backup includes MySQL job history, MinIO artifacts, and Caddy state by default. Redis and RabbitMQ snapshots are optional with `--include-volatile`.

## Successful Job Demo

In another terminal, run:

```bash
scripts/demo/docker-e2e-success.sh
```

Expected output includes:

```text
status=COMPLETED
pipelineCurrentStage=COMPLETED
pipelineTerminal=true
pipelineCompletedStageCount=...
pipelineTotalMeasuredDurationMs=...
pipelineSlowestStage=...
modelCallCount=2
failedModelCallCount=0
estimatedCostUsd=0E-8
qualityEvaluationStatus=...
qualityEvaluationScore=...
qualityEvaluationVerdict=...
qualityEvidenceMarkdownJobId=...
subtitleReviewSegmentCount=...
subtitleReviewMissingTargetCount=...
subtitleReviewTimingMismatchCount=...
subtitleReviewQuality=...
subtitleReviewSubtitleArtifactCount=...
subtitleDraftSegmentCount=...
subtitleDraftEditedSegmentCount=0
subtitleDraftLastUpdated=Not saved
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
/tmp/linguaframe-demo/quality-evidence.md
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

## Daily Budget Guard Demo

Run this when you need terminal evidence that same-day demo budget protection blocks a second paid path before the next guarded provider call:

```bash
LINGUAFRAME_COST_ENABLED=true \
LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true \
LINGUAFRAME_COST_MAX_JOB_COST_USD=1 \
LINGUAFRAME_COST_DAILY_BUDGET_GUARD_ENABLED=true \
LINGUAFRAME_COST_MAX_DAILY_COST_USD=0.000001 \
LINGUAFRAME_COST_BUDGET_IDENTITY=demo-owner \
LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE=1 \
docker compose --env-file .env up -d --force-recreate linguaframe-backend

scripts/demo/docker-e2e-daily-budget-guard.sh
```

The script uploads one sample job to create same-day estimated spend, uploads a second compatible job, waits for `FAILED`, checks that `failureReason` contains `Daily cost budget exceeded`, checks `failureTriage.category=BUDGET_GUARD`, and downloads safe evidence to:

```text
/tmp/linguaframe-demo/daily-budget-guard/first-job.json
/tmp/linguaframe-demo/daily-budget-guard/second-job.json
/tmp/linguaframe-demo/daily-budget-guard/second-diagnostics.json
```

The budget identity is the configured safe label, such as `demo-owner`. Do not use raw demo tokens, IP addresses, local media paths, or provider payloads as budget identities.

## Provider Cache-Hit Demo

Run this after the successful job path when you need terminal evidence that repeat compatible jobs reuse provider results:

```bash
scripts/demo/docker-e2e-cache-hit.sh
```

The script uploads the same sample twice with the same target language and current provider/model/prompt settings. It waits for both jobs to complete, downloads job detail and diagnostics reports for both runs, validates diagnostics safety, and fails if the second job has no provider cache hit.

After the script passes, open the React frontend, open the first completed job, click `Pin as baseline` in the `Cache replay` panel, and choose the second completed job as the comparison. The panel should show provider cache-hit stages, reused/generated artifact counts, model-call delta, estimated-cost delta, and safe copy/download replay evidence actions.

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

## Recommended OpenAI Smoke Demo

Use this path when you have real OpenAI credentials and want one repeatable provider-backed proof run. The default deterministic demo stays on `.env.example`; the OpenAI smoke uses a separate ignored env file:

```bash
cp .env.openai-demo.example .env.openai-demo
```

Set the local values in `.env.openai-demo`:

```text
OPENAI_API_KEY=<your key>
OPENAI_BASE_URL=https://api.openai.com
OPENAI_TRANSCRIPTION_MODEL=whisper-1
OPENAI_TRANSLATION_MODEL=<current text model>
OPENAI_EVALUATION_MODEL=<current text model>
LINGUAFRAME_OPENAI_CONNECTIVITY_MODEL=<current text model>
```

Recreate the backend and run preflight:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.openai-demo up -d --build
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-demo-preflight.sh
```

Then run the smoke with a real short speech MP4:

```bash
LINGUAFRAME_ENV_FILE=.env.openai-demo \
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 \
scripts/demo/docker-e2e-openai-smoke.sh
```

Expected output includes successful `MODEL_CALL TRANSCRIPTION OPENAI ... SUCCEEDED` and `MODEL_CALL TRANSLATION OPENAI ... SUCCEEDED`, quality score output when evaluation is enabled, downloaded artifacts under `/tmp/linguaframe-demo/openai-smoke/`, diagnostics, backend evidence Markdown, evidence bundle, demo run package, and result bundle. This path can consume OpenAI credits and must never print or commit the API key.

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
LINGUAFRAME_OPENAI_CONNECTIVITY_CHECK_ENABLED=true
LINGUAFRAME_OPENAI_CONNECTIVITY_MODEL=whisper-1
```

After recreating the backend, `scripts/demo/private-demo-preflight.sh` should print `openai=UP` before you run the paid media path. If the probe is `DOWN`, fix the base URL, API key, or model before uploading a sample video.

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
