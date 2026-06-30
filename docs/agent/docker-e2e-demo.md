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

The OpenAPI document at `http://localhost:8080/v3/api-docs` should include the upload, job, progress event, retry/cancel, artifact, diagnostics, transcript, subtitle, auth, demo-session, runtime, prompt-template, operator, and retention cleanup APIs. It should expose both `DemoAccessToken` and `BearerAuth`. When `LINGUAFRAME_DEMO_ACCESS_TOKEN` is configured, use the React header `Owner access token` form to start a browser owner session. When `LINGUAFRAME_AUTH_ENABLED=true` with a configured password and JWT secret, use the React `Local account` form or `scripts/demo/auth-smoke.sh` to verify bearer-token access. For Swagger and curl, use Swagger UI's `Authorize` action with either `DemoAccessToken` or `BearerAuth`; Swagger and `/v3/api-docs` stay public, while `/api/**` calls require a compatible owner-session cookie, demo header, or bearer token. `/api/demo-session` and `/api/auth/session` should show sanitized owner/account metadata only.

The React demo validates selected videos through `/api/media/uploads/validate` before upload, uploads valid videos to `/api/media/uploads`, stores recent uploaded job ids in browser local storage, polls `GET /api/jobs/{jobId}`, and renders source media metadata, timeline events, usage summary, result delivery, media delivery, subtitle review, failure triage, model-call records, transcript/subtitle previews, artifacts, downloads, and failed-job retry.

Before uploading, check the browser `Demo presentation cockpit`, `Demo runbook`, `Demo readiness`, `Live checks`, `Private demo launch rehearsal`, upload form `Upload readiness`, upload form `Demo sample media`, upload form `Demo run launcher`, and upload form `Validate file` result. The cockpit calls `GET /api/operator/demo-presentation-cockpit` and is the run-day next-action surface: it summarizes launcher, upload readiness, live checks, private-demo operations, active run monitor, recommended completed run, acceptance gate, package links, and safety notes without uploading media, starting Docker, or calling providers. Terminal export is available with `scripts/demo/demo-presentation-cockpit.sh`; set `LINGUAFRAME_DEMO_JOB_ID=<job-id>` to enrich it for a selected run. The runbook shows the one-command startup path, short/cache/full E2E commands, local frontend and backend health URLs, sample-media guidance, and current runtime constraints such as upload duration, provider modes, budget guard, and subtitle burn-in state. The `Demo readiness` panel should show sanitized worker topology: active role, listener queue, RabbitMQ exchange, default route, FFmpeg route, OpenAI route, owned stage groups, and safe startup commands. This verifies the combined or split-worker operating mode before upload; it does not mutate Docker, RabbitMQ, or provider state. The launch rehearsal panel should show the ordered manual go/no-go steps, recommended next step, expected evidence routes, and metadata-only notes without running Docker, OpenAI, backup, restore, upload, or cleanup work. After jobs complete, use `Private demo evidence gallery` to select the recommended completed run, verify handoff readiness, and open safe package links without opening every job manually. The live-check panel should show database, Redis, RabbitMQ, MinIO, and FFmpeg as `UP`, `DOWN`, or `SKIPPED`. The upload form can apply a `Demo profile` such as `quick-baseline`, `tears-showcase`, or `concise-review` before manual edits; the selected profile should appear in job detail, delivery handoff, evidence, demo session reports, and terminal Markdown summaries. The upload readiness panel should show `READY`, `ATTENTION`, or `BLOCKED` based on access-gated API reachability, runtime contract, live dependencies, owner quota, selected profile, and paid-provider checks. The sample media panel should call `GET /api/operator/demo-sample-media-catalog`, show public sample sources, attribution, configured path status without full local paths, the upload duration limit, and safe terminal commands. The run launcher panel should call `GET /api/operator/demo-run-launcher`, show the recommended Tears sample/profile command, readiness gates, and expected evidence files for the full demo without starting the run. The upload validation panel should show the selected file's validation code, message, size, duration, and configured limits before any job is created.

Also check the upload form `Owner quota` panel before paid or full-video runs. It calls `GET /api/media/uploads/preflight` and shows owner id, active jobs, queued jobs, same-day estimated spend, limits, and safe blocking reasons. When blocked, upload is disabled but file validation still works.

Run terminal upload readiness when you need a shell-level go/no-go summary for the selected profile:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/upload-readiness.sh
```

The script writes `/tmp/linguaframe-demo/upload-readiness.json`, prints metadata-only `uploadReadiness*` lines, and exits non-zero only for `BLOCKED` unless `LINGUAFRAME_UPLOAD_READINESS_REPORT_ONLY=true`. It does not upload media or call paid providers.

Run terminal sample catalog when you need to choose or verify demo media before upload:

```bash
scripts/demo/demo-sample-media-catalog.sh
```

The script writes `/tmp/linguaframe-demo/demo-sample-media-catalog.json`, prints metadata-only `sampleCatalog*` lines, and exits non-zero only for `BLOCKED` unless `LINGUAFRAME_DEMO_SAMPLE_CATALOG_REPORT_ONLY=true`. It does not download remote media, upload files, edit `.env`, start Docker, call OpenAI, or print full local paths.

Run the terminal demo launcher when you need the exact next full-demo command and expected evidence outputs:

```bash
scripts/demo/demo-run-launcher.sh
```

The script writes `/tmp/linguaframe-demo/demo-run-launcher.json`, prints metadata-only `demoRunLauncher*` lines, and exits non-zero only for `BLOCKED` unless `LINGUAFRAME_DEMO_RUN_LAUNCHER_REPORT_ONLY=true`. It does not upload media, start Docker, call OpenAI, edit `.env`, or print full local paths.

After opening a job, check the `Source media` panel first. It should show filename, content type, size, duration, upload status, created time, video id, job id, target language, and a `Download source video` link to `/api/media/uploads/{videoId}/source/download` without exposing object keys or local paths. Terminal runs should print `sourceMedia*` summary lines and write `source-media.json` plus `source-video.mp4`.

After opening a job, check the `Result delivery` panel before the detailed artifact table. It should list transcript JSON, source subtitles, target subtitles, dubbing audio, burned video, and worker summary as `Ready`, `Preview only`, or `Missing`. Ready rows should expose direct artifact downloads, short SHA-256 hashes, and generated/reused cache state. The panel should also keep `Download result bundle` and `Download diagnostics` visible without exposing object keys, local paths, or provider payloads.

Check the `Media delivery` panel when playable outputs exist. It should show `DUBBING_AUDIO`, generated `BURNED_VIDEO`, generated `DUBBED_VIDEO`, and reviewed `REVIEWED_BURNED_VIDEO` as separate outputs with browser players, direct download links, content type, size, short SHA-256 hashes, and generated/reused cache state. `DUBBED_VIDEO` appears only when TTS audio and a subtitle-burned video base are both available. Terminal `mediaDelivery*` summary lines must include only artifact type, filename, content type, and generated/reused state.

Check the read-only `Subtitle review` panel after transcript and target subtitles load. It should show segment count, missing target count, timing mismatch count, average and max duration, quality score/verdict, downloadable subtitle artifact count, and source/target comparison rows. Evidence exports and terminal summaries should include only subtitle-review metadata, not raw transcript or subtitle text.

Check the `Subtitle draft editor` panel when target subtitles exist. Edit one draft row, save it, verify the saved/unsaved counters update, use corrected JSON/SRT/VTT export links, publish reviewed subtitle artifacts, reset unsaved edits, and clear the saved draft. Reviewed JSON/SRT/VTT artifacts use the saved draft overlay and appear in result delivery, artifact downloads, archives, diagnostics, evidence, and terminal demo summaries. The reviewed burned-video option is explicit and creates a separate artifact only when FFmpeg burn-in is enabled; generated subtitle artifacts, TTS audio, and generated burned video stay unchanged.

Check the `Narration workspace` panel when you need explanatory voiceover. Add or edit time-coded rows, paste compact rows in `Quick script import`, choose inherited/default or explicit provider voice presets, inspect the `Timeline workbench` proportional bars, inspect the metadata-derived `Narration waveform overview`, use `Narration editing commands` to duplicate, split, merge, or insert local draft rows, use `Narration draft history` to inspect added/removed/timing/text/voice changes, undo or redo local edits, revert to the last saved workspace, select or scrub a narration window, preview against the best available media source (`NARRATED_VIDEO`, then `BURNED_VIDEO`, then source video), use `Jump to narration N`, use `Play window`, and verify the timeline and waveform playheads track preview time. Quick script rows use `START-END | VOICE | TEXT`, for example `00:15-00:28 | alloy | Explain this moment.` or `00:55-01:10 || Inherit default voice.`; the import panel previews rows/errors and can replace or append only the local draft, while `Quick script export` copies or downloads the current unsaved local draft in the same paste-ready text format. Use `Voice audition` to preview a configured voice preset with custom sample text, then apply it to the selected row or all local draft rows before saving. Use `Narration TTS preview` to synthesize the selected draft row before saving; it uses the current unsaved text and explicit/inherited voice and renders a local audio player. Voice audition and segment TTS preview can consume configured provider credits, but they must not save narration rows, create artifacts, update evidence, write object storage, or generate video. Then save narration, save mix settings, generate narration audio, generate narrated video, and refresh or download narration evidence through explicit actions. Preview playback, voice apply actions, quick script import/export, waveform scrubbing, editing commands, undo, redo, and revert are local-only and must not call providers, create artifacts, save rows, or mutate object storage. Draft history is in-memory only and resets after successful save response or workspace reload. Inserted blank rows intentionally block save until required fields are filled. The waveform overview uses narration timing/text metadata; it is not decoded audio waveform rendering. Gaps are allowed and mean intentional silence between narration windows; overlapping, invalid, malformed quick-script, or unknown-voice rows block save/generate actions until fixed. Evidence refresh and Markdown/ZIP downloads remain available so blocked states can still be diagnosed safely. Voice presets are provider identifiers, not voice cloning or uploaded reference audio.

Check the `Render review` panel after saving narration or running the narration demo render. It should show `READY`, `ATTENTION`, or `BLOCKED`, next action, segment/gap/overlap counts, audio/video/waveform readiness, mix override/keyframe counts, safe links, and `Download review Markdown`. The terminal equivalent is `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-render-review.sh`, which writes `narration-render-review.json` and `.md` under `/tmp/linguaframe-demo/narration-render-review/`. This review is read-only: it must not call OpenAI, call TTS providers, run FFmpeg, save narration rows, create artifacts, print narration text, expose object keys, or include media bytes.

Terminal segment preview is available without saving a row:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> \
LINGUAFRAME_NARRATION_PREVIEW_TEXT="Preview this explanatory line." \
scripts/demo/narration-segment-preview.sh
```

Expected output includes `narrationSegmentPreviewOutputPath=/tmp/linguaframe-demo/narration-segment-preview/narration-segment-preview.mp3` and the provider cost warning. Use `LINGUAFRAME_NARRATION_PREVIEW_TEXT_FILE` for longer text and `LINGUAFRAME_NARRATION_PREVIEW_VOICE` for an explicit preset.

Terminal voice audition is available for explicit preset comparison:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> \
LINGUAFRAME_NARRATION_AUDITION_VOICE=alloy \
LINGUAFRAME_NARRATION_AUDITION_TEXT="Preview this voice preset." \
scripts/demo/narration-voice-audition.sh
```

Expected output includes `narrationVoiceAuditionOutputPath=/tmp/linguaframe-demo/narration-voice-audition.mp3`, byte count, content type, and provider cost warning. Use `LINGUAFRAME_NARRATION_AUDITION_TEXT_FILE` for longer samples and `LINGUAFRAME_NARRATION_AUDITION_OUTPUT_PATH` for a custom local MP3 path.

Check the `Demo review guide` panel before presenting a selected job. It should show `Presentation ready` only when the pipeline is terminal, reviewed outputs are available, delivery is ready, evidence links are available, and the session report is ready. Its links should jump to `Result delivery`, `Pipeline progress`, `Subtitle review`, `Delivery handoff`, `Demo evidence`, and `Demo session report`; presenter notes should contain only safe metadata.

Check the `Delivery handoff` panel after publishing reviewed subtitles. It should show `Ready for handoff`, reviewed subtitle count, reviewed burned-video availability, generated audit artifact count, reviewed handoff artifacts, audit artifacts, safe verification links, `Download delivery manifest`, `Download handoff package`, and `Download demo run package`. The downloadable Markdown manifest should reference artifacts and evidence without embedding raw transcript text, raw subtitles, object keys, local paths, provider payloads, credentials, or media bytes. The handoff package should contain reviewed subtitle artifacts plus safe manifest/evidence files only.

Check the `Demo handoff checklist` panel before presenting the run. It should summarize job completion, terminal pipeline state, reviewed subtitle readiness, media outputs, evidence downloads, quality signal, cost/model-call evidence, cache evidence, and failure triage when applicable. `Copy checklist` and `Download checklist JSON` should export only metadata and safe download links. `Download handoff package` should point to `/api/jobs/{jobId}/handoff-package/download`, and `Download demo run package` should point to `/api/jobs/{jobId}/demo-run-package/download`.

Check the `Demo session report` panel as the final reviewer-facing summary for one run. It should show `Session ready` or `Session needs attention`, then group safe metadata into `Input and job`, `Generated outputs`, `Handoff evidence`, `Cost and cache`, and `Failure triage` when applicable. `Copy report`, `Download report Markdown`, `Download handoff package`, `Download demo run package`, and `/tmp/linguaframe-demo/demo-session-report.md` should not include raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.

Check the `Demo share sheet` panel when you need one compact note for a reviewer. It should call `GET /api/jobs/{jobId}/demo-share-sheet`, show readiness, headline, summary, outcome bullets, recommended next action, and curated safe links. `Copy share sheet`, `Download backend Markdown`, `scripts/demo/demo-share-sheet.sh`, and the full Tears script outputs `demo-share-sheet.json` plus `demo-share-sheet.md` must not include raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.

Check the `Demo run monitor` panel while a job is queued, running, or complete. It should call `GET /api/jobs/{jobId}/demo-run-monitor`, show attention level, current stage, elapsed time, completed/total stage count, slowest stage, recommended next action, and compact stage rows. `Download backend Markdown`, `scripts/demo/demo-run-monitor.sh`, `LINGUAFRAME_DEMO_RUN_MONITOR_WATCH=true scripts/demo/demo-run-monitor.sh`, and the full Tears script outputs `demo-run-monitor.json` plus `demo-run-monitor.md` must stay metadata-only and must not expose raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.

Use the demo run package when a reviewer wants one safe ZIP workspace for a single run. `GET /api/jobs/{jobId}/demo-run-package/download` should include `manifest.json`, `README.md`, `job-detail.json`, `diagnostics.json`, `evidence.md`, `quality-evidence.md`, `delivery-manifest.md`, `demo-handoff-checklist.md`, and `demo-session-report.md`. It should not include uploaded media, generated media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or API keys.

Use the `Demo reviewer workspace` panel when a reviewer needs the quickest consolidated handoff for one completed run. It calls `GET /api/jobs/{jobId}/demo-reviewer-workspace`, shows `READY`, `ATTENTION`, or `BLOCKED`, required and optional checks, safe evidence links, package entries, safety notes, and Markdown/ZIP download actions. The terminal equivalent is `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-reviewer-workspace.sh`, which writes `demo-reviewer-workspace.json`, `.md`, and `.zip` under `/tmp/linguaframe-demo/demo-reviewer-workspace/`. Use reviewer workspace for the top-level handoff, acceptance gate for final go/no-go, completion certificate for proof details, OpenAI smoke proof for provider-backed proof, demo run package for detailed per-job evidence, and evidence closure when tying the run back to pre-upload approval and variance.

Use the `Demo handoff portal` panel when a reviewer needs one offline entry point. It calls `GET /api/jobs/{jobId}/demo-handoff-portal`, shows portal status, phase, headline, required checks, optional evidence, package entries, and safe links. The terminal equivalent is `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-handoff-portal.sh`, which writes `demo-handoff-portal.json`, `.md`, and `.zip` under `/tmp/linguaframe-demo/demo-handoff-portal/`. The ZIP must include `index.html`, `manifest.json`, `handoff-portal.md`, `reviewer-workspace.json`, `README.md`, `acceptance-gate.json`, `completion-certificate.json`, `share-sheet.json`, and `run-monitor.json`, and must not include media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or API keys.

Use the demo run snapshot when a reviewer wants an offline entry point for one run. `GET /api/jobs/{jobId}/demo-run-snapshot` previews readiness, sections, entries, exclusion policy, and safe links. `GET /api/jobs/{jobId}/demo-run-snapshot/download`, the browser `Demo snapshot` panel, and `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-snapshot.sh` should produce a ZIP with `index.html`, `manifest.json`, `README.md`, share sheet, run monitor, presenter pack JSON, delivery manifest, diagnostics, and evidence. It should not include media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or API keys.

Use the demo acceptance gate as the final go/no-go check before presenting. `GET /api/jobs/{jobId}/demo-acceptance-gate`, the browser `Demo acceptance gate` panel, `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-acceptance-gate.sh`, and the full Tears script output `demo-acceptance-gate.json` should summarize required checks, warning checks, safe evidence metrics, package links, and recommended next action as `READY`, `ATTENTION`, or `BLOCKED`. It should not include media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or API keys.

Check the `Pipeline progress` panel during short, full-video, cache-hit, and OpenAI smoke runs. It is derived from `job_timeline_events` and does not change worker execution. It should show current stage, completed stage count, terminal state, total measured stage duration, slowest stage, and compact per-stage status/duration rows. Use the detailed `Timeline` panel when you need event-level evidence.

For failed jobs, check the `Failure triage` panel before retrying. It should show a safe category such as `OPENAI_AUTH_OR_MODEL`, `OPENAI_TIMEOUT_OR_NETWORK`, `BUDGET_GUARD`, `MEDIA_PROCESSING`, `STORAGE_OR_ARTIFACT`, `WORKER_OR_QUEUE`, `CONFIGURATION`, `USER_CANCELLED`, or `UNKNOWN`, plus retryability, a recommended action, and an optional static runbook command. The same triage appears in diagnostics JSON, backend Markdown evidence, browser evidence export, and terminal script summaries.

Use the `Demo evidence` panel after the job is visible in the browser. `Copy evidence` produces a browser-generated Markdown summary for notes or interview walkthroughs, `Download evidence JSON` writes a local metadata file, `Download backend evidence` downloads the API-generated Markdown report, and `Download evidence bundle` downloads a metadata-only ZIP with `manifest.json`, `evidence.md`, and `diagnostics.json`. The evidence should include job status, pipeline progress, subtitle-review counts, subtitle-draft counts, reviewed artifact counts, reviewed burned-video availability, timeline stages, usage, cache counts, artifact hashes, and safe download routes, but no raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, media bytes, or generated artifact bytes.

Use the `Quality evaluation` panel when a quality result exists. `Copy quality evidence` and `Download quality evidence` produce reviewer-facing Markdown from browser metadata, while `Download backend quality evidence` calls `/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download`. The terminal success and OpenAI smoke scripts also download `/tmp/linguaframe-demo/quality-evidence.md` and print `qualityEvaluation*` plus `qualityEvidenceMarkdown*` summary lines.

Use the `Model calls` panel when reviewing OpenAI or demo-provider usage. `Download AI audit package` calls `/api/jobs/{jobId}/ai-audit-package/download` and returns a metadata-only ZIP with `manifest.json`, `README.md`, `model-calls.json`, `prompt-templates.json`, `ai-usage-summary.json`, and `ai-audit-report.md`. The package connects model-call prompt versions to active prompt templates and excludes raw transcript text, raw subtitle text, corrected subtitle text, provider payloads, object keys, local paths, demo tokens, credentials, uploaded media bytes, and generated artifact bytes.

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

Run owner quota preflight when you need a shell-level stop before upload:

```bash
scripts/demo/owner-quota-preflight.sh
```

The script writes `/tmp/linguaframe-demo/owner-quota-preflight.json`, prints metadata-only `ownerQuota*` lines, and exits non-zero if the configured owner has exhausted active job, queued job, or owner daily budget limits. Use `LINGUAFRAME_OWNER_QUOTA_REPORT_ONLY=true` for report-only checks.

Run upload readiness when you need the combined browser-equivalent pre-upload state:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/upload-readiness.sh
```

Expected output includes `uploadReadinessOverall=READY`, `ATTENTION`, or `BLOCKED`, one `uploadReadinessCheck=<status>:<id>:<label>` line per check, safe evidence route lines, and `workerTopologyRole`, `workerTopologyListenerQueue`, `workerTopologyFfmpegRoute`, `workerTopologyOpenaiRoute`, and `workerTopologyCommand` lines from `/api/runtime/dependencies`. The command does not print demo tokens, local paths, object keys, provider payloads, transcript text, subtitle text, or media bytes.

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

Set `LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase` when the terminal demo should use the Tears of Steel terminology, high-contrast subtitles, and balanced subtitle polishing preset. Existing env overrides such as `LINGUAFRAME_DEMO_SUBTITLE_POLISHING_MODE=STRICT` still win over the profile.

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
/tmp/linguaframe-demo/demo-reviewer-workspace.json
/tmp/linguaframe-demo/demo-reviewer-workspace.md
/tmp/linguaframe-demo/demo-reviewer-workspace.zip
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

Use the browser `Demo sample media` panel or `scripts/demo/demo-sample-media-catalog.sh` before the full run to verify the recommended Tears sample status and duration-limit guidance without exposing the full local path. Use `Demo run launcher` or `scripts/demo/demo-run-launcher.sh` immediately after that to confirm the recommended profile, next command, readiness gates, and evidence files expected from the full run.

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

To include the built-in Tears narration script preset in the full run, apply it after upload and before narration evidence export:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase \
LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true \
scripts/demo/docker-e2e-tears-of-steel-full.sh
```

To render the complete built-in narration demo in the full run, use the one-click render flag instead. This applies the preset, generates narration audio, optionally generates narrated video, exports refreshed script package/evidence, and then continues the full evidence flow:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase \
LINGUAFRAME_RENDER_NARRATION_DEMO=true \
scripts/demo/docker-e2e-tears-of-steel-full.sh
```

For an existing completed job, run the read-only preflight first:

```bash
LINGUAFRAME_DEMO_JOB_ID=<completed-job-id> \
scripts/demo/narration-demo-render-preflight.sh
```

The browser order is the same: open a completed job, run `Render preflight`, inspect checks and safe next command, acknowledge replacement/provider cost, run render, then verify artifacts and narration evidence. The full narration order is upload full demo, run render preflight when render is enabled, optionally render narration demo, export narration evidence, then run acceptance, completion, reviewer, snapshot, and handoff packages. If both `LINGUAFRAME_RENDER_NARRATION_DEMO=true` and `LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true` are set, render wins and the apply-only step is skipped. Render can call OpenAI TTS when configured, so use it only after the selected `.env` and cost guard are intentional. Preflight estimates are advisory; OpenAI usage and billing remain the provider-side source of truth.

The script downloads core artifacts to `/tmp/linguaframe-demo/tears-of-steel-full/`. It also downloads `demo-run-matrix.json` for the completed source video, `demo-presenter-pack.json`, `demo-acceptance-gate.json`, `demo-run-snapshot.json`, `demo-run-snapshot.zip`, `demo-reviewer-workspace.json`, `demo-reviewer-workspace.md`, and `demo-reviewer-workspace.zip` for the selected job, then prints metadata-only summaries with profile, status, quality, estimated cost, model calls, provider cache hits, handoff readiness, recommended runs, acceptance readiness, snapshot entries, reviewer workspace status, and safe download routes. When `LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true`, it also writes preset apply evidence under `narration-demo-preset/`. When `LINGUAFRAME_RENDER_NARRATION_DEMO=true`, it writes preflight JSON, render evidence, refreshed script package, and refreshed narration evidence under `narration-demo-render/`. `BURNED_VIDEO`, `DUBBING_AUDIO`, `DUBBED_VIDEO`, and `NARRATED_VIDEO` are optional because burn-in, TTS, and narration generation can be disabled for stable local runs.

To compare two complete Tears of Steel runs, first run a baseline profile and keep the completed job id:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=quick-baseline scripts/demo/docker-e2e-tears-of-steel-full.sh
```

Then run the showcase profile against that baseline:

```bash
LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase \
LINGUAFRAME_COMPARISON_BASELINE_JOB_ID=<baseline-job-id> \
scripts/demo/docker-e2e-tears-of-steel-full.sh
```

The second run downloads `job-comparison.json` and `job-comparison.md` to `/tmp/linguaframe-demo/tears-of-steel-full/` and prints a metadata-only summary with profile, quality, model-call, cost, cache, and setting deltas. The same backend-backed comparison is available in the browser `Demo comparison` panel. To inspect all recent runs for the same source video, open the selected job in the browser and use `Demo run matrix`; it marks the recommended baseline, best quality run, and lowest cost run from the same backend matrix used by the terminal JSON output. Use `Demo presenter pack` when you need a single presenter-facing checklist with readiness, recommended run IDs, copy/download notes, and safe evidence package links. Use `Demo acceptance gate` or `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-acceptance-gate.sh` when you need one final `READY`, `ATTENTION`, or `BLOCKED` go/no-go answer. Use `Demo completion certificate` or `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-completion-certificate.sh` when you need proof that the selected run is completed, handoff-ready, reproducible, and backed by safe package routes. Use `Demo replay card` or `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-replay-card.sh` when you need the selected job's safe settings, replay commands, baseline comparison command, and package links in one JSON file. Use `Demo snapshot` or `scripts/demo/demo-run-snapshot.sh` when you need one static reviewer folder with an HTML entry point and safe metadata files. Use `scripts/demo/private-demo-evidence-gallery.sh` when you need a private-demo-wide index of completed runs and safe package links after multiple jobs have completed. Use `scripts/demo/private-demo-run-archive.sh` at the end of a private demo to write `/tmp/linguaframe-demo/private-demo-run-archive/run-archive.json` and `.md`, combining operations readiness, launch rehearsal, gallery counts, recommended job, and safe package routes without backing up data or embedding media bytes.

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
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-readiness-evidence.sh
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-demo-preflight.sh
```

`scripts/demo/openai-readiness-evidence.sh` writes `/tmp/linguaframe-demo/openai-readiness-evidence/openai-readiness-evidence.json` and `.md`. It is a metadata-only readiness report over provider modes, live-check status, upload readiness, budget posture, and recent model-call failures. It does not upload media or execute OpenAI transcription, translation, TTS, or evaluation.

Then run the smoke with a real short speech MP4:

```bash
LINGUAFRAME_ENV_FILE=.env.openai-demo \
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 \
scripts/demo/docker-e2e-openai-smoke.sh
```

Expected output includes successful `MODEL_CALL TRANSCRIPTION OPENAI ... SUCCEEDED` and `MODEL_CALL TRANSLATION OPENAI ... SUCCEEDED`, quality score output when evaluation is enabled, downloaded artifacts under `/tmp/linguaframe-demo/openai-smoke/`, diagnostics, backend evidence Markdown, evidence bundle, demo run package, AI audit package, OpenAI smoke proof JSON/Markdown, demo reviewer workspace JSON/Markdown/ZIP, and result bundle. This path can consume OpenAI credits and must never print or commit the API key.

For an already completed smoke job, export only the post-run proof:

```bash
LINGUAFRAME_DEMO_JOB_ID=<completed-job-id> scripts/demo/openai-smoke-proof.sh
```

The proof is metadata-only and checks successful OpenAI transcription and translation calls, required transcript/target-subtitle artifacts, optional quality/TTS evidence, and safe package links.

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

## Reviewed Subtitle Workflow Export

After any successful or partially reviewed job, export the metadata-only reviewed subtitle workflow cockpit:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/reviewed-subtitle-workflow.sh
```

The script downloads `/api/jobs/{jobId}/reviewed-subtitle-workflow` to `/tmp/linguaframe-demo/reviewed-subtitle-workflow-<job-id>.json` and prints status, phase, next action, subtitle artifact counts, and handoff readiness. Use it to decide whether to review subtitles, export a draft, publish reviewed JSON/SRT/VTT, request a reviewed burned video, or download the handoff package. The JSON and summary must stay metadata-only: no transcript text, subtitle text, corrected draft text, object keys, local paths, provider payloads, tokens, credentials, or media bytes.

## Subtitle Review Evidence Export

Use subtitle review evidence when you need proof of the human review pass, not the corrected subtitle text itself:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/subtitle-review-evidence.sh
```

The script writes `subtitle-review-evidence.json`, `subtitle-review-evidence.md`, and `subtitle-review-evidence.zip` under `/tmp/linguaframe-demo/subtitle-review-evidence/`. It exits non-zero when the evidence is `BLOCKED`; set `LINGUAFRAME_SUBTITLE_REVIEW_EVIDENCE_REPORT_ONLY=true` to export a blocked report for triage. The ZIP must contain `manifest.json`, `subtitle-review-evidence.md`, `review-summary.json`, `release-notes.md`, and `README.md`.

In the browser, edit subtitle text in the subtitle draft editor and separately set review decision, issue categories, reviewer note, and publish release notes. The review metadata explains acceptance, edits, follow-up, terminology, timing, tone, readability, missing text, or other issues. Evidence exports and handoff packages include counts and safe links only; they must not include raw transcript text, generated subtitle text, corrected subtitle text, reviewer note bodies, local paths, object keys, provider payloads, tokens, API keys, or media bytes. The deterministic, OpenAI smoke, and full Tears scripts all export the same subtitle review evidence after job completion.

## Narration Workspace And Evidence Export

Use the browser `Narration workspace` panel when you need explanatory voiceover that is separate from subtitle dubbing. Add time-coded rows or import a script package, select a `Timeline workbench` bar, edit timing with drag handles or table inputs, use `Narration editing commands` to duplicate, split at playhead, merge next, or insert a blank row, inspect the metadata-derived `Narration waveform overview`, scrub locally against the preview media, then save the workspace. Keyboard editing on a selected bar uses ArrowLeft/Right to move by 0.25 seconds, Shift+ArrowLeft/Right to resize the end, and Alt+ArrowLeft/Right to resize the start. Timeline edits, row editing commands, and waveform scrubs stay local until `Save narration`; they do not call OpenAI, create artifacts, generate narration audio, or generate narrated video. After save, set mix controls, generate the timed narration audio bed, generate narrated video, and verify `NARRATION_AUDIO` plus `NARRATED_VIDEO` appear in media delivery as playable cards. Use the adjacent `Script package` panel to export reusable JSON/Markdown/ZIP packages, paste package JSON, acknowledge replacement, and restore a full script workspace. Narrated-video export creates a standalone `narrated-video.mp4`; it preserves the base video's original audio, applies the saved ducking volume during narration windows, applies narration volume and fade duration, and does not replace dubbing audio, dubbed video, burned video, or reviewed handoff media. Defaults are `duckingVolume=0.35`, `narrationVolume=1.00`, and `fadeDurationMs=250`; valid ranges are `0.00-1.00`, `0.00-2.00`, and `0-5000`.

Terminal evidence export:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-preset.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-evidence.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-playback-review.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-playback-review-resolution.sh
```

The render script writes `narration-demo-presets.json`, `narration-demo-preset.json`, `narration-demo-render.json`, refreshed script package files, and refreshed narration evidence under `/tmp/linguaframe-demo/narration-demo-render/`. Set `LINGUAFRAME_NARRATION_DEMO_RENDER_REPORT_ONLY=true` to inspect the recommended preset without changing a job, `LINGUAFRAME_NARRATION_DEMO_GENERATE_VIDEO=false` for an audio-only render, or `LINGUAFRAME_NARRATION_DEMO_PRESET_ID=<preset-id>` to override the profile recommendation. Render requires explicit replace semantics and can call paid TTS providers when they are configured.

The preset script writes `narration-demo-presets.json`, `narration-demo-preset.json`, `narration-demo-preset-apply.json`, refreshed script package files, and refreshed narration evidence under `/tmp/linguaframe-demo/narration-demo-preset/`. Set `LINGUAFRAME_NARRATION_DEMO_PRESET_REPORT_ONLY=true` to list the recommended profile preset without changing a job. Applying a preset requires a job id and always uses explicit replace mode so existing narration rows are not silently merged.

The script writes `narration-evidence.json`, `narration-evidence.md`, and `narration-evidence.zip` under `/tmp/linguaframe-demo/narration-evidence/`. Set `LINGUAFRAME_DEMO_GENERATE_NARRATED_VIDEO=true` to call the narrated-video generation endpoint when narration audio is ready and print `mixMode=DUCKED_ORIGINAL_AUDIO`, `duckingVolume`, `narrationVolume`, `fadeDurationMs`, `mixSettingsSource`, and `narrationWindowCount`. It exits non-zero when evidence is `BLOCKED`; set `LINGUAFRAME_NARRATION_EVIDENCE_REPORT_ONLY=true` to export a blocked report. The ZIP must contain `manifest.json`, `narration-evidence.md`, `narration-summary.json`, and `README.md`, and must not include narration script bodies, transcript text, subtitle text, provider payloads, object keys, local paths, tokens, API keys, or media bytes.

The playback review script writes `narration-playback-review.json` and `narration-playback-review.md` under `/tmp/linguaframe-demo/narration-playback-review/`. Use it after narration audio/video generation to record segment-level playback decisions from the browser `Playback review` panel and export safe counts. Set `LINGUAFRAME_NARRATION_PLAYBACK_REVIEW_REPORT_ONLY=true` to export a blocked report. The export must include only timing, decision, issue-category, artifact-readiness, note-present, and safe-link metadata; it must not include narration text, reviewer note bodies, provider payloads, object keys, local paths, tokens, API keys, or media bytes.

The playback resolution script writes `narration-playback-resolution.json` and `narration-playback-resolution.md` under `/tmp/linguaframe-demo/narration-playback-resolution/`. Use it after playback review to classify unresolved rows, identify whether text revision or rerender is needed, and prove whether narrated-video handoff is ready. Set `LINGUAFRAME_NARRATION_PLAYBACK_RESOLUTION_REPORT_ONLY=true` to export a blocked report. The export must stay metadata-only and must not include narration text, reviewer note bodies, provider payloads, object keys, local paths, tokens, API keys, or media bytes.

Script package export:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-script-package.sh
```

The script writes `narration-script-package.json`, `narration-script-package.md`, and `narration-script-package.zip` under `/tmp/linguaframe-demo/narration-script-package/`, then prints `narrationScriptPackage*` status, counts, voice summary, checks, and output paths. This package is explicitly for operator-authored script reuse and may include narration text; unlike narration evidence, it is not a metadata-only proof surface. It must still exclude media bytes, transcript text, subtitle text, object keys, local paths, provider payloads, tokens, API keys, and credentials.

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
