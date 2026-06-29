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

For OpenAPI contract changes, run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -Dtest=OpenApiDocumentationTests test
```

Expected:

- `/v3/api-docs` exposes `LinguaFrame API` metadata.
- `components.securitySchemes.DemoAccessToken` documents the `X-LinguaFrame-Demo-Token` header.
- Tags include media uploads, localization jobs, runtime dependencies, prompt templates, operator dashboard, and retention cleanup.
- Paths cover upload, job detail, event stream, retry/cancel, artifact, diagnostics, transcript, subtitle, runtime dependencies, runtime live checks, prompt-template, operator, and cleanup APIs.

For worker logging changes, run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -Dtest=LinguaFrameLogContextTests,LocalizationJobExecutionServiceTests test
```

Expected:

- MDC helper tests prove `jobId`, `videoId`, `stage`, and `workerRole` are scoped and restored.
- Execution tests prove the stage sees job-scoped MDC while running.
- Execution tests prove MDC is cleared after successful and failed jobs.

### Docker Runtime Verification

Run the one-command local demo startup:

```bash
scripts/demo/start-local-demo.sh
```

Expected:

- The script packages `LinguaFrame/target/LinguaFrame-0.0.1-SNAPSHOT.jar`.
- The script recreates `linguaframe-backend` from the selected env file.
- The backend health endpoint responds on `http://localhost:8080/actuator/health`.
- If `http://localhost:5173` is unavailable, the script starts `scripts/demo/frontend-local-dev.sh` for the current session and waits for it to respond.
- `scripts/demo/private-demo-preflight.sh` passes before any media upload.
- Preflight prints live dependency check lines for database, Redis, RabbitMQ, MinIO, FFmpeg, and OpenAI. OpenAI is `SKIPPED` unless the explicit connectivity check is enabled.
- Preflight prints `Worker topology readiness is visible` after the runtime contract check.
- The script prints `http://localhost:5173`, `scripts/demo/demo-run-launcher.sh`, `scripts/demo/upload-readiness.sh`, `scripts/demo/docker-e2e-success.sh`, and `scripts/demo/docker-e2e-cache-hit.sh`.

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

Validate the private-demo deployment overlay:

```bash
docker compose --env-file .env.private-demo.example \
  -f docker-compose.yml \
  -f deploy/private-demo/docker-compose.private-demo.yml \
  config --quiet
LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-deploy-preflight.sh
```

Expected:

- The overlay renders `linguaframe-proxy`.
- The proxy publishes 80/443.
- Backend and frontend host ports are removed in the overlay.
- Caddy routes `/api`, actuator, Swagger, downloads, previews, and SSE to the backend while browser pages route to the frontend.
- Preflight requires a public domain and non-empty demo token without printing secrets.

Validate private-demo backup and restore static behavior:

```bash
bash -n scripts/demo/private-demo-backup.sh scripts/demo/private-demo-restore.sh
LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-backup.sh --dry-run --output-dir /tmp/linguaframe-private-demo-backups
```

Create a synthetic restore smoke directory and run dry-run restore:

```bash
rm -rf /tmp/linguaframe-private-demo-restore-smoke
mkdir -p /tmp/linguaframe-private-demo-restore-smoke/minio \
  /tmp/linguaframe-private-demo-restore-smoke/caddy-data \
  /tmp/linguaframe-private-demo-restore-smoke/caddy-config
printf '{"backupVersion":"1","createdAt":"2026-06-28T00:00:00Z","composeProject":"linguaframe-private-demo","components":["mysql","minio","caddy-data","caddy-config"],"services":["mysql","minio","linguaframe-proxy","linguaframe-backend","linguaframe-frontend"]}\n' \
  > /tmp/linguaframe-private-demo-restore-smoke/manifest.json
: > /tmp/linguaframe-private-demo-restore-smoke/mysql.sql
tar -cf /tmp/linguaframe-private-demo-restore-smoke/caddy-data.tar \
  -C /tmp/linguaframe-private-demo-restore-smoke/caddy-data .
tar -cf /tmp/linguaframe-private-demo-restore-smoke/caddy-config.tar \
  -C /tmp/linguaframe-private-demo-restore-smoke/caddy-config .
LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-restore.sh \
  --dry-run \
  --backup-dir /tmp/linguaframe-private-demo-restore-smoke
```

Expected:

- Backup dry-run prints safe component names and target paths only.
- Restore dry-run validates `manifest.json`, `mysql.sql`, `minio/`, and Caddy tarball shape without writing data.
- Non-dry-run restore refuses to run unless `--yes` is present.
- Full backup/restore requires a running private-demo stack and is not part of static validation.

If the frontend image cannot build because Docker cannot resolve or pull `node:26-alpine`, use the local fallback instead of blocking backend demo validation:

```bash
scripts/demo/frontend-local-dev.sh
```

Expected:

- The script serves Vite on `http://localhost:5173`.
- `LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh` reports `Frontend responds at http://localhost:5173`.

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

Focused handoff portal checks:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=DemoHandoffPortalServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests
cd frontend
npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx
bash -n scripts/demo/demo-handoff-portal.sh scripts/demo/narration-demo-preset.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh
```

Expected:

- Browser selected-job view shows `Demo handoff portal` after `Demo reviewer workspace`.
- The panel shows `HANDOFF_PORTAL_READY`, required checks, package entries, safe links, and Markdown/ZIP download buttons.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-handoff-portal.sh` writes JSON, Markdown, and ZIP under `/tmp/linguaframe-demo/demo-handoff-portal/`.
- The ZIP includes `index.html`, `manifest.json`, `handoff-portal.md`, `reviewer-workspace.json`, `README.md`, `acceptance-gate.json`, `completion-certificate.json`, `share-sheet.json`, and `run-monitor.json`.
- Portal exports exclude media bytes, raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, and API keys.

Focused narration preset checks:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=NarrationDemoPresetServiceTests,DemoRunProfileControllerTests,NarrationDemoPresetApplyServiceTests,LocalizationJobControllerTests,NarrationScriptPackageServiceTests
cd frontend
npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx
bash -n scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh
```

Expected:

- Browser selected-job narration workspace shows `Demo narration preset` next to script package import/export.
- Browser selected-job narration workspace shows a `Narration preview` panel after the timeline workbench.
- Completed jobs with `NARRATED_VIDEO` preview that artifact; otherwise preview falls back to `BURNED_VIDEO`, then source video.
- Selecting a narration row and clicking `Jump to narration N` seeks the preview player to the selected start time and moves the timeline playhead.
- Clicking `Play window` seeks to the selected start time and stops at the selected end time without saving rows, generating artifacts, or calling providers.
- Apply remains disabled until the replace confirmation is checked.
- Applying `tears-showcase-narration` refreshes narration workspace, script package, narration evidence, and artifact metadata without generating audio or video.
- `LINGUAFRAME_NARRATION_DEMO_PRESET_REPORT_ONLY=true scripts/demo/narration-demo-preset.sh` writes preset catalog metadata without a job id or mutation.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-preset.sh` writes preset apply evidence plus refreshed script/evidence files under `/tmp/linguaframe-demo/narration-demo-preset/`.
- `LINGUAFRAME_APPLY_NARRATION_DEMO_PRESET=true scripts/demo/docker-e2e-tears-of-steel-full.sh` applies the preset before narration evidence export.
- Preset summaries exclude transcript text, subtitle text, object keys, local paths, demo tokens, provider payloads, credentials, API keys, and media bytes.

Focused narration render checks:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=NarrationDemoRenderServiceTests,LocalizationJobControllerTests,NarrationDemoPresetApplyServiceTests,NarrationScriptPackageServiceTests
cd frontend
npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx
bash -n scripts/demo/narration-demo-render-preflight.sh scripts/demo/narration-demo-render.sh scripts/demo/narration-demo-preset.sh scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh
```

Expected:

- Browser selected-job narration workspace shows `Render narration demo` near `Demo narration preset`.
- `Render preflight` shows `READY`, `ATTENTION`, or `BLOCKED`, provider mode, paid-provider flag, estimated size, existing workspace size, output plan, safe next command, evidence routes, and check rows.
- Render remains disabled until the latest matching preflight is `READY` or `ATTENTION` and both replacement and paid-provider acknowledgements are checked.
- `BLOCKED` preflight remains visible and disables render without hiding the separate narration controls.
- `Generate narrated video` defaults on and maps to `generateNarratedVideo`; disabling it produces an audio-only render request.
- A successful render refreshes narration workspace, script package, narration evidence, artifacts, and render preflight, then shows preset/audio/video step status rows.
- If audio succeeds but narrated-video generation fails, the backend returns `PARTIAL`, keeps `NARRATION_AUDIO`, and reports the video step as failed.
- `LINGUAFRAME_NARRATION_DEMO_RENDER_REPORT_ONLY=true scripts/demo/narration-demo-render.sh` lists the recommended preset without requiring a job id or calling render.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render-preflight.sh` writes read-only preflight JSON and exits non-zero for `BLOCKED` unless report-only mode is enabled.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED=true scripts/demo/narration-demo-render.sh` refuses blocked render before calling TTS or video generation.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-demo-render.sh` writes render JSON plus refreshed script/evidence files under `/tmp/linguaframe-demo/narration-demo-render/`.
- `LINGUAFRAME_RENDER_NARRATION_DEMO=true scripts/demo/docker-e2e-tears-of-steel-full.sh` runs preflight and the one-click render flow before final narration evidence export; if the apply-only flag is also true, render wins.
- Render summaries exclude transcript text, subtitle text, raw narration text markers, object keys, local paths, demo tokens, provider payloads, credentials, API keys, and media bytes.
- OpenAI-backed TTS render is treated as a paid-provider action and should be run only when `.env` and cost guard settings are intentional. Preflight estimates are advisory; provider-side OpenAI usage and billing remain the source of truth.

With the Docker stack running, open:

```text
http://localhost:5173
```

Open Swagger UI for API-contract inspection:

```text
http://localhost:8080/swagger-ui/index.html
```

Expected browser behavior:

- Swagger UI lists the primary demo API groups and the `DemoAccessToken` authorization option.
- Swagger UI lists `BearerAuth`, and `/api/auth/session`, `/api/auth/login`, and `/api/auth/logout` appear in the OpenAPI paths.
- When private demo access is enabled, the React header shows `Owner session required` before login.
- Entering the configured token in `Owner access token` and choosing `Start session` changes the header to `Owner session active`; choosing `End session` clears the owner session.
- When local account auth is configured, the React header shows `Local account required`; signing in changes it to `Local account active`, and signing out clears the stored bearer token.
- `scripts/demo/auth-smoke.sh` writes local auth JSON under `/tmp/linguaframe-demo/local-auth/`, verifies `/api/runtime/dependencies` with a bearer token when configured, and must not print passwords, JWT secrets, bearer tokens, or demo tokens.
- `scripts/demo/owner-workspace-smoke.sh` writes owner workspace JSON under `/tmp/linguaframe-demo/owner-workspace/`, verifies job history, upload readiness, and runtime dependencies with a bearer token when local auth is configured, and prints only `ownerWorkspace*` metadata. It exits 0 and reports a skip when local auth is disabled or unconfigured.
- After local account sign-in, job history and selected owner metadata refresh for the bearer owner. Cross-owner job, media, artifact, diagnostics, and evidence routes return safe 404/not-found behavior without exposing the other owner id, filename, object key, token, local path, provider payload, transcript text, subtitle text, or media bytes.
- Swagger and curl API calls to `/api/**` still succeed only after sending the configured token through `X-LinguaFrame-Demo-Token`.
- Anonymous browser requests to `/api/demo-session` remain reachable so a locked-out owner can log in, while other `/api/**` requests stay protected.
- Choosing a file and clicking `Validate file` shows an `Upload validation` panel with backend validation code, message, filename, content type, file size versus max size, and duration versus max duration.
- The upload form shows an `Upload readiness` panel before file selection with `READY`, `ATTENTION`, or `BLOCKED` status plus safe check rows for owner-session access, runtime contract, live dependencies, owner quota, demo profile, and paid-provider checks.
- Changing `Demo profile` refreshes upload readiness for the selected profile.
- `BLOCKED` upload readiness disables `Upload` while leaving `Validate file` available; `ATTENTION` does not disable upload.
- Clicking `Upload` validates the selected file before creating a job.
- Invalid upload validation responses block `POST /api/media/uploads` and leave the upload controls usable.
- Upload a short MP4 with a target language.
- A recent job appears in the browser-local recent jobs list.
- The selected job reaches `COMPLETED`, `FAILED`, or `CANCELLED`.
- The `Source media` panel shows filename, content type, size, duration, upload status, created time, video id, job id, target language, and `Download source video`.
- Source media metadata and terminal `sourceMedia*` summary lines do not expose source object keys, local paths, tokens, credentials, provider payloads, raw transcript text, or raw subtitle text.
- Timeline, usage summary, and model-call panels render from `GET /api/jobs/{jobId}`.
- The `Pipeline progress` panel renders current stage, completed stage count, terminal state, total measured duration, slowest stage, and per-stage status/duration rows from timeline-derived `pipelineProgress`.
- The `Demo run monitor` panel appears for the selected job and shows attention level, current stage, elapsed time, completed/total stage count, slowest stage, recommended next action, and compact stage rows from `GET /api/jobs/{jobId}/demo-run-monitor`.
- `Download backend Markdown`, `scripts/demo/demo-run-monitor.sh`, and `LINGUAFRAME_DEMO_RUN_MONITOR_WATCH=true scripts/demo/demo-run-monitor.sh` write metadata-only run monitor evidence without raw transcript text, raw subtitles, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.
- The operator dashboard shows stage timing rows with max, average, latest, completed count, and failed count for the slowest recent stages.
- Failed jobs render a `Failure triage` panel with category, retryability, recommended action, optional static runbook command, and safe details.
- Failed-job diagnostics JSON, backend Markdown evidence, browser evidence export, and terminal demo summaries include the same safe failure triage without secrets, object keys, local paths, provider payloads, media bytes, raw transcript text, or raw subtitle text.
- Diagnostics JSON, backend Markdown evidence, browser evidence export, and terminal demo summaries include safe pipeline progress without secrets, object keys, local paths, provider payloads, media bytes, raw transcript text, or raw subtitle text.
- The `Result delivery` panel renders expected deliverables with `Ready`, `Preview only`, and `Missing` states.
- The `Result delivery` panel shows generated/reused/missing counts, model-call count, estimated cost, short SHA-256 hashes, and generated versus reused cache state.
- The `Result delivery` panel includes `Download result bundle`, `Download diagnostics`, and direct download links for ready deliverables only.
- The `Media delivery` panel appears when playable media artifacts exist.
- The `Media delivery` panel renders `DUBBING_AUDIO`, generated `BURNED_VIDEO`, generated `DUBBED_VIDEO`, and reviewed `REVIEWED_BURNED_VIDEO` as separate outputs when present.
- Each `Media delivery` output has a browser player, direct download link, content type, size, short SHA-256 hash, and generated/reused cache state.
- The `Demo review guide` panel appears near the top of the selected job view.
- The guide shows `Presentation ready` only when the pipeline is terminal, reviewed subtitles are ready, handoff delivery is ready, evidence links are available, and the session report is ready.
- Failed or incomplete jobs show `Needs attention` and link to pipeline progress, demo evidence, and failure triage when triage exists.
- The upload form `Demo sample media` panel appears before upload and shows overall status, recommended sample id, upload duration limit, source/attribution links, configured path status, and safe terminal commands.
- `scripts/demo/demo-sample-media-catalog.sh` writes `/tmp/linguaframe-demo/demo-sample-media-catalog.json` and prints `sampleCatalog*` summary lines.
- Demo sample media catalog output must not expose full local paths, object keys, demo tokens, provider payloads, OpenAI keys, transcript text, subtitle text, uploaded media bytes, or generated media bytes.
- The upload form `Demo run launcher` panel appears before upload and shows overall status, recommended sample id, recommended profile id, next command, readiness gates, and expected post-run evidence paths.
- `scripts/demo/demo-run-launcher.sh` writes `/tmp/linguaframe-demo/demo-run-launcher.json` and prints `demoRunLauncher*` summary lines.
- Demo run launcher output must not expose full local paths, object keys, demo tokens, provider payloads, OpenAI keys, transcript text, subtitle text, uploaded media bytes, or generated media bytes.
- The `Demo share sheet` panel appears for the selected job and shows readiness, headline, summary, outcome bullets, recommended next action, and curated safe links.
- `Copy share sheet`, `Download backend Markdown`, and `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-share-sheet.sh` export safe metadata only.
- `Copy presenter notes` and `Download presenter notes` must not include raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.
- The read-only `Subtitle review` panel shows segment count, missing target count, timing mismatch count, average/max duration, quality score/verdict when available, and downloadable subtitle artifact count.
- The `Subtitle review` panel renders source/target comparison rows with timing range, delta, and `Aligned`, `Missing target`, or `Timing mismatch` status.
- Subtitle-review API failures appear as non-blocking preview errors, while job detail, transcript preview, subtitle preview, and artifacts remain usable.
- The `Demo evidence` panel renders a safe Markdown evidence preview with job status, subtitle-review metadata, timeline stages, usage, cache counts, artifact hashes, result bundle URL, diagnostics URL, backend evidence URL, and evidence bundle URL.
- `Copy evidence` copies the safe Markdown summary when the browser Clipboard API is available; otherwise the panel explains that clipboard copy is unavailable.
- `Download evidence JSON` downloads a local metadata file and must not include raw transcript text, raw subtitle text, object keys, local paths, demo tokens, provider payloads, or media bytes.
- `Download backend evidence` points to `/api/jobs/{jobId}/evidence/markdown/download`.
- `Download evidence bundle` points to `/api/jobs/{jobId}/evidence/bundle/download` and returns a metadata-only ZIP with `manifest.json`, `evidence.md`, and `diagnostics.json`.
- The `Narration workspace` panel can add rows, edit start/end/text, choose inherited/default or explicit provider voice presets from compact selects, drag or keyboard-edit `Timeline workbench` proportional bars, review selected-segment window/duration/voice/character diagnostics, save valid non-overlapping segments, tune ducking volume, narration volume, and fade duration, clear the workspace, generate a timed narration audio bed, generate a ducked narrated video, refresh evidence, and download narration evidence Markdown/ZIP.
- Selected narration timeline bars support ArrowLeft/Right move by 0.25 seconds, Shift+ArrowLeft/Right end resize, and Alt+ArrowLeft/Right start resize. Unsaved timeline edits should update the table and inspector immediately, but must not call OpenAI, create artifacts, or generate audio/video until the operator runs the explicit save/generate/render action.
- The `Script package` panel inside `Narration workspace` shows package status, segment/character counts, voice summary, checks, Markdown/ZIP downloads, JSON paste import, invalid JSON blocking, and a required replace-current-workspace acknowledgement before import.
- Unknown saved narration voices are shown for diagnosis but block save/generate until replaced by a configured preset. Evidence refresh and downloads stay available.
- Narration gaps are allowed as intentional silent intervals and should appear in timeline/evidence metrics; overlapping or invalid rows block save and generate actions while evidence refresh/download remains available.
- Generated narration audio and narrated video appear as `NARRATION_AUDIO` and `NARRATED_VIDEO` in artifacts and as playable `Narration audio` and `Narrated video` media-delivery cards. The narrated video should report `DUCKED_ORIGINAL_AUDIO` with actual ducking volume, narration volume, fade duration, and settings source. They must not replace `DUBBING_AUDIO`, `DUBBED_VIDEO`, `BURNED_VIDEO`, `REVIEWED_BURNED_VIDEO`, or reviewed subtitle artifacts.
- Narration evidence reports status, segment count, character count, total timeline duration, voice preset count, voice summary, default voice, audio readiness, `audioLayout`, `timeAligned`, narrated-video readiness, `mixMode`, `duckingVolume`, `narrationVolume`, `fadeDurationMs`, `mixSettingsSource`, safe links, and ZIP entries only. It must not include narration script bodies, transcript text, subtitle text, object keys, local paths, demo tokens, provider payloads, credentials, or media bytes.
- Narration script package exports write JSON/Markdown/ZIP through `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-script-package.sh`; they may include operator-authored narration text for reuse, but must exclude media bytes, transcript text, subtitle text, object keys, local paths, demo tokens, provider payloads, credentials, and API keys.
- When quality evaluation exists, the `Quality evaluation` panel exposes `Copy quality evidence`, `Download quality evidence`, and `Download backend quality evidence`.
- Backend quality evidence points to `/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download` and the terminal scripts validate `/tmp/linguaframe-demo/quality-evidence.md`.
- Quality evidence must include score, verdict, dimensions, issue/fix counts, and safe routes without raw transcript text, raw subtitle text, object keys, local paths, demo tokens, provider payloads, credentials, or media bytes.
- `Download handoff package` appears in `Delivery handoff`, `Demo handoff checklist`, and `Demo session report`, and points to `/api/jobs/{jobId}/handoff-package/download`.
- The handoff package ZIP contains `manifest.json`, `delivery-manifest.md`, `evidence.md`, `diagnostics.json`, reviewed subtitle artifacts, and optional reviewed burned video only.
- `Download demo run package` appears in `Delivery handoff`, `Demo handoff checklist`, and `Demo session report`, and points to `/api/jobs/{jobId}/demo-run-package/download`.
- The demo run package ZIP contains `manifest.json`, `README.md`, `job-detail.json`, `diagnostics.json`, `evidence.md`, `quality-evidence.md`, `delivery-manifest.md`, `demo-handoff-checklist.md`, and `demo-session-report.md`.
- Terminal `demoRunPackage*` summary lines must show the expected job id and entry count, and fail if the ZIP contains object keys, local paths, provider payloads, API keys, demo tokens, or raw transcript/subtitle markers.
- The `Demo reviewer workspace` panel appears near the top of the selected job view and shows overall status, phase, required checks, optional evidence, safe links, package entries, refresh, `Download reviewer Markdown`, and `Download reviewer ZIP`.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-reviewer-workspace.sh` writes `demo-reviewer-workspace.json`, `demo-reviewer-workspace.md`, and `demo-reviewer-workspace.zip` under `/tmp/linguaframe-demo/demo-reviewer-workspace/`.
- The reviewer workspace ZIP contains `manifest.json`, `reviewer-workspace.md`, and `README.md`; terminal `demoReviewerWorkspace*` summary lines must show status, phase, required ready count, required blocked count, optional attention count, link count, package entry count, and output paths.
- Reviewer workspace JSON, Markdown, ZIP, and browser panel must not include media bytes, transcript bodies, subtitle bodies, local paths, object keys, demo tokens, bearer tokens, credentials, provider request or response bodies, or raw transcript/subtitle markers.
- The `Demo snapshot` panel appears for the selected job and shows readiness, headline, summary, packaged sections, exclusion policy, refresh, safe live links, and `Download static snapshot ZIP`.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-snapshot.sh` writes `demo-run-snapshot.json` and `demo-run-snapshot.zip` under `/tmp/linguaframe-demo/demo-run-snapshot/`.
- The snapshot ZIP contains `index.html`, `manifest.json`, `README.md`, `demo-share-sheet.md`, `demo-share-sheet.json`, `demo-run-monitor.md`, `demo-run-monitor.json`, `presenter-pack.json`, `delivery-manifest.md`, `diagnostics.json`, and `evidence.md`.
- Terminal `demoRunSnapshot*` summary lines must show the expected job id and entry count, and fail if the ZIP contains object keys, local paths, provider payloads, API keys, demo tokens, media bytes, or raw transcript/subtitle markers.
- `Download AI audit package` appears in the `Model calls` panel and points to `/api/jobs/{jobId}/ai-audit-package/download`.
- The AI audit package ZIP contains `manifest.json`, `README.md`, `model-calls.json`, `prompt-templates.json`, `ai-usage-summary.json`, and `ai-audit-report.md`.
- Terminal `aiAuditPackage*` summary lines must show the expected job id, entry count, model-call count, and prompt-template count, and fail if the ZIP contains object keys, local paths, provider payloads, API keys, demo tokens, or raw transcript/subtitle markers.
- The `Demo handoff checklist` panel appears in the selected job view.
- The `Demo handoff checklist` panel shows `Ready for demo handoff` when the job is completed, reviewed subtitles are ready, and evidence links are available.
- Failed or incomplete jobs show `Needs attention`, while still exposing diagnostics and backend evidence links.
- `Copy checklist` and `Download checklist JSON` must not include raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, or media bytes.
- The `Demo session report` panel appears in the selected job view.
- The `Demo session report` panel shows `Session ready` for completed runs with handoff-ready reviewed subtitles, otherwise `Session needs attention`.
- The report groups safe metadata into `Input and job`, `Generated outputs`, `Handoff evidence`, `Cost and cache`, and `Failure triage` when applicable.
- `Copy report`, `Download report Markdown`, and terminal `/tmp/linguaframe-demo/demo-session-report.md` must not include raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, demo tokens, provider payloads, credentials, media bytes, or generated artifact bytes.
- Terminal `handoffPackage*` summary lines must show the expected job id, entry count, and reviewed artifact count, and fail if the ZIP contains object keys, local paths, provider payloads, API keys, demo tokens, or raw transcript/subtitle markers outside reviewed artifact files.
- The `Cache replay` panel can pin the selected job as a baseline, compare it with another loaded job, show provider cache-hit stages, artifact reused/generated counts, model-call delta, estimated-cost delta, and export safe replay evidence.
- `Copy replay evidence` and `Download replay evidence JSON` must not include raw transcript text, raw subtitle text, object keys, local paths, demo tokens, credentials, or provider payloads.
- Transcript and subtitle preview panels render when backend preview data exists.
- Artifact download links appear when artifacts exist.
- The `Download result bundle` link appears in the `Artifacts` panel and points to `/api/jobs/{jobId}/artifacts/archive/download`.
- The `Download diagnostics` link appears in the selected job header and points to `/api/jobs/{jobId}/diagnostics/download`.
- Audio and video playback appears in `Media delivery` for `DUBBING_AUDIO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, and `REVIEWED_BURNED_VIDEO` artifacts.
- Failed jobs show a retry button.
- The `Retention cleanup` panel appears in the sidebar.
- Clicking `Preview cleanup` refreshes aggregate cleanup counts without deleting data.
- With `.env.example`, the panel reports dry-run/default-off behavior and `Run cleanup` requires browser confirmation before calling the backend.
- The `Demo runbook` panel shows `scripts/demo/start-local-demo.sh`, `scripts/demo/docker-e2e-success.sh`, `scripts/demo/docker-e2e-cache-hit.sh`, `scripts/demo/docker-e2e-tears-of-steel-full.sh`, `http://localhost:5173`, and `http://localhost:8080/actuator/health`.
- The `Demo runbook` panel explains that uploads are complete files up to the configured duration and file-size limits, and shows provider modes, budget guard state, subtitle burn-in state, and sample-media guidance without exposing secrets or raw local media paths.
- If runtime readiness loading fails, the `Demo runbook` panel still shows static commands and reports the runtime guidance error.
- The `Demo readiness` panel shows budget guard state, the configured per-job cost limit, daily demo budget state, daily limit, and safe budget identity without exposing provider credentials.
- The `Demo readiness` panel shows `Worker topology` with active role, listener queue, job exchange, default route, FFmpeg route, OpenAI route, owned stage groups, and safe startup commands without exposing RabbitMQ credentials or demo tokens.
- Terminal upload readiness output includes `workerTopologyRole`, `workerTopologyListenerQueue`, `workerTopologyFfmpegRoute`, `workerTopologyOpenaiRoute`, and `workerTopologyCommand` lines before any upload or provider call.
- The `Private demo run archive` panel shows overall archive status, recommended job/profile/readiness, operations status, launch status, completed/handoff-ready counts, candidate rows, and safe archive links.
- `scripts/demo/private-demo-run-archive.sh` writes metadata-only `run-archive.json` and `run-archive.md`, and terminal output includes `privateDemoRunArchiveOverall`, `privateDemoRunArchiveRecommendedJobId`, candidate rows, and link rows without tokens, local paths, provider payloads, or raw transcript/subtitle text.
- The `Live checks` panel shows overall `Ready` or `Blocked`, plus database, Redis, RabbitMQ, MinIO, FFmpeg, and OpenAI statuses from `GET /api/runtime/live-checks`. OpenAI is `SKIPPED` by default and `UP` or `DOWN` only when the explicit connectivity check is enabled.
- If live checks fail to load, the `Live checks` panel shows a short unavailable message and leaves upload controls usable.

### Docker E2E Demo

Start the stack:

```bash
docker compose --env-file .env.example up -d
```

Run private-demo preflight before uploading media:

```bash
LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh
scripts/demo/private-demo-operations-report.sh
```

Expected:

- Docker Compose config renders for the default stack and the split-worker profile.
- Backend health returns `UP`.
- Backend runtime contract is current: `runtime.latestMigrationVersion` is at least the highest local `LinguaFrame/src/main/resources/db/migration/V*__*.sql` version.
- Required demo routes are listed in `runtime.requiredRoutes`, including diagnostics and artifact archive downloads.
- Runtime live dependency checks pass for database, Redis, RabbitMQ, MinIO, FFmpeg, and OpenAI, or preflight fails before upload with the failing dependency names. OpenAI `SKIPPED` is accepted when the connectivity check is disabled.
- The browser `Private demo operations` panel and `scripts/demo/private-demo-operations-report.sh` summarize access gate, runtime contract, live dependencies, provider readiness, cost safety, storage/recovery, retention cleanup, and demo evidence without secrets or raw media paths.
- A stale backend container fails preflight before media upload and prints the backend package/recreate commands.
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
- Output includes `subtitleReviewSegmentCount`, `subtitleReviewMissingTargetCount`, `subtitleReviewTimingMismatchCount`, `subtitleReviewQuality`, and `subtitleReviewSubtitleArtifactCount`.
- Terminal subtitle-review output excludes raw transcript text, raw subtitle text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Output includes `subtitleDraftSegmentCount`, `subtitleDraftEditedSegmentCount`, and `subtitleDraftLastUpdated`.
- Terminal subtitle-draft output excludes raw transcript text, raw generated subtitle text, raw corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Output includes `reviewedPublishArtifactCount=3`, `reviewedPublishSubtitleArtifactCount=3`, `reviewedPublishBurnedVideoRequested=false`, and `reviewedPublishBurnedVideoCreated=false`.
- Terminal reviewed-publish output excludes raw transcript text, raw generated subtitle text, raw corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Output includes `deliveryManifestHandoffReady=true`, `deliveryManifestReviewedSubtitleArtifactCount=3`, `deliveryManifestReviewedBurnedVideoAvailable=false`, `deliveryManifestGeneratedArtifactCount`, and `deliveryManifestLinkCount`.
- Terminal delivery-manifest output excludes raw transcript text, raw generated subtitle text, raw corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Terminal media-delivery output excludes raw transcript text, raw generated subtitle text, raw corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Terminal demo-handoff-checklist output excludes raw transcript text, raw generated subtitle text, raw corrected draft text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Output includes `demoHandoffOverall=READY` for completed reviewed handoff runs, otherwise `demoHandoffOverall=ATTENTION`.
- Output includes `demoHandoffItem=PASS:Job completed` when the job completes.
- Output includes `demoHandoffItem=PASS:Reviewed subtitles ready` after reviewed JSON/SRT/VTT artifacts are published.
- Output includes `demoHandoffItem=PASS:Evidence downloads ready`.
- Output includes `mediaDeliveryReadyCount`.
- Output includes `mediaDeliveryArtifact=BURNED_VIDEO:burned-video.mp4:video/mp4:Generated` or `mediaDeliveryArtifact=BURNED_VIDEO:burned-video.mp4:video/mp4:Reused`.
- Output includes `mediaDeliveryArtifact=DUBBING_AUDIO:dubbing-audio.mp3:audio/mpeg:Generated` only when TTS is enabled.
- Output includes `mediaDeliveryArtifact=DUBBED_VIDEO:dubbed-video.mp4:video/mp4:Generated` only when TTS and subtitle burn-in both produce compatible media artifacts.
- Output includes `mediaDeliveryArtifact=REVIEWED_BURNED_VIDEO:reviewed-burned-video.mp4:video/mp4:Generated` only when reviewed burn-in is requested.
- Output includes `MODEL_CALL TRANSCRIPTION DEMO demo-transcription SUCCEEDED`.
- Output includes `MODEL_CALL TRANSLATION DEMO demo-translation SUCCEEDED`.
- Output includes `MODEL_CALL TTS DEMO demo-tts SUCCEEDED` only when TTS is enabled.
- Timeline includes `WORKER_RECEIVED`, `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `TRANSCRIPT_SUBTITLE_EXPORT`, `TARGET_SUBTITLE_EXPORT`, `DUBBING_AUDIO_GENERATION`, `SUBTITLE_BURN_IN`, `DUBBED_VIDEO_DELIVERY`, `ARTIFACT_SUMMARY`, and `COMPLETED`.
- Output includes `artifactCount=12` by default, `artifactCount=13` when TTS audio is enabled without dubbed-video delivery, and `artifactCount=14` when TTS and subtitle burn-in also create `DUBBED_VIDEO`.
- Output includes `EXTRACTED_AUDIO audio.wav`.
- Output includes `TRANSCRIPT_JSON transcript.json`.
- Output includes `SUBTITLE_SRT subtitles.srt`.
- Output includes `SUBTITLE_VTT subtitles.vtt`.
- Output includes `TARGET_SUBTITLE_JSON target-subtitles.json`.
- Output includes `TARGET_SUBTITLE_SRT target-subtitles.srt`.
- Output includes `TARGET_SUBTITLE_VTT target-subtitles.vtt`.
- Output includes `REVIEWED_SUBTITLE_JSON reviewed-subtitles.zh-CN.json`.
- Output includes `REVIEWED_SUBTITLE_SRT reviewed-subtitles.zh-CN.srt`.
- Output includes `REVIEWED_SUBTITLE_VTT reviewed-subtitles.zh-CN.vtt`.
- Output includes `DUBBING_AUDIO dubbing-audio.mp3` only when TTS is enabled.
- Output includes `BURNED_VIDEO burned-video.mp4`.
- Output includes `DUBBED_VIDEO dubbed-video.mp4` only when TTS and subtitle burn-in both produce compatible media artifacts.
- Output includes `WORKER_SUMMARY worker-summary.json`.
- The script downloads `/tmp/linguaframe-demo/audio.wav`.
- The script downloads `/tmp/linguaframe-demo/transcript.json`.
- The script downloads `/tmp/linguaframe-demo/subtitles.srt`.
- The script downloads `/tmp/linguaframe-demo/subtitles.vtt`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.json`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.srt`.
- The script downloads `/tmp/linguaframe-demo/target-subtitles.vtt`.
- The script downloads `/tmp/linguaframe-demo/dubbing-audio.mp3` only when TTS is enabled.
- The script downloads `/tmp/linguaframe-demo/dubbed-video.mp4` only when TTS and subtitle burn-in both produce compatible media artifacts.
- The script downloads `/tmp/linguaframe-demo/burned-video.mp4`.
- The script downloads `/tmp/linguaframe-demo/job-diagnostics.json`.
- The script downloads `/tmp/linguaframe-demo/delivery-manifest.md`.
- The script downloads `/tmp/linguaframe-demo/worker-summary.json`.

Inspect job-scoped backend logs:

```bash
JOB_ID=<job id printed by the script>
docker logs linguaframe-backend 2>&1 | grep "jobId=$JOB_ID"
docker logs linguaframe-backend 2>&1 | grep "stage=TARGET_SUBTITLE_EXPORT"
docker logs linguaframe-backend 2>&1 | grep "workerRole=COMBINED"
```

Expected:

- Worker log lines include `jobId`, `videoId`, `stage`, and `workerRole` while processing stages.
- Log lines do not include OpenAI keys, demo tokens, object storage credentials, source object keys, local filesystem paths, raw transcript text, raw subtitles, provider payloads, or media bytes.

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

Run the daily budget guard path only after recreating the backend with a high per-job budget, a tiny daily budget, a safe budget identity, and a non-zero local cost rate:

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

Expected:

- First job reaches `COMPLETED` and creates same-day estimated cost for `demo-owner`.
- Second job reaches `FAILED`.
- Output includes `failureReason=Daily cost budget exceeded`.
- Output includes diagnostics summary lines for the failed second job.
- Evidence files are written under `/tmp/linguaframe-demo/daily-budget-guard/`.
- The budget identity is a safe configured label and must not be a raw token, IP address, media path, or provider payload.

Run the real OpenAI smoke path only with a local ignored `.env.openai-demo` and a short speech MP4:

```bash
cp .env.openai-demo.example .env.openai-demo
# Edit OPENAI_API_KEY and current model values in .env.openai-demo.
docker compose --env-file .env.openai-demo up -d --build
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-readiness-evidence.sh
LINGUAFRAME_ENV_FILE=.env.openai-demo scripts/demo/openai-demo-preflight.sh
LINGUAFRAME_ENV_FILE=.env.openai-demo \
LINGUAFRAME_DEMO_SAMPLE_PATH=/absolute/path/to/short-speech.mp4 \
scripts/demo/docker-e2e-openai-smoke.sh
```

Expected:

- Preflight prints `openai=UP` and safe provider readiness for transcription, translation, and evaluation.
- OpenAI readiness evidence prints `openAiReadinessStatus=READY` or a non-blocking `ATTENTION` with JSON/Markdown under `/tmp/linguaframe-demo/openai-readiness-evidence/`.
- The job reaches `COMPLETED`.
- Job summary includes successful `OPENAI` model calls for transcription and translation.
- Quality evaluation score, verdict, and status are printed when evaluation is enabled.
- Evidence files are written under `/tmp/linguaframe-demo/openai-smoke/`, including `openai-smoke-proof.json` and `openai-smoke-proof.md`.
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/openai-smoke-proof.sh` can re-export the same post-run proof under `/tmp/linguaframe-demo/openai-smoke-proof/`.
- Output and evidence do not include `OPENAI_API_KEY`, bearer headers, raw provider payloads, object keys, or raw local paths.

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
file /tmp/linguaframe-demo/dubbed-video.mp4
python3 -m json.tool /tmp/linguaframe-demo/job-diagnostics.json
python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json
```

Skip `file /tmp/linguaframe-demo/dubbing-audio.mp3` when TTS is disabled. Skip `file /tmp/linguaframe-demo/dubbed-video.mp4` unless both TTS and subtitle burn-in are enabled and completed.

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
- In the browser, the first completed job can be pinned as the `Cache replay` baseline and the second completed job can be selected as the comparison.
- Browser replay evidence shows provider cache-hit stages, artifact reused/generated counts, model-call delta, and estimated-cost delta without exposing raw content or storage keys.

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
- `DUBBED_VIDEO` artifact is present as `dubbed-video.mp4` when subtitle burn-in is also enabled and the generated TTS audio is muxable by FFmpeg.
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
- `/tmp/linguaframe-demo/subtitle-review-evidence.json` is downloaded.
- `/tmp/linguaframe-demo/subtitle-review-evidence.md` is downloaded.
- `/tmp/linguaframe-demo/subtitle-review-evidence.zip` is downloaded.
- Terminal output includes `subtitleReviewEvidenceStatus`, `subtitleReviewEvidenceReviewedSegmentCount`, `subtitleReviewEvidenceFollowupSegmentCount`, `subtitleReviewEvidenceAnnotationCount`, and `subtitleReviewEvidencePackageEntryCount=5`.
- `subtitle-review-evidence.zip` contains `manifest.json`, `subtitle-review-evidence.md`, `review-summary.json`, `release-notes.md`, and `README.md`.
- `/tmp/linguaframe-demo/worker-summary.json` is downloaded.
- Forced smoke-stage failure produces `status=FAILED`.
- Retry after disabling failure produces `status=COMPLETED`.
- Job timeline includes worker receive, smoke stage, audio extraction, transcript/source subtitle export, target subtitle export, subtitle burn-in, artifact summary, and completion events.

Browser subtitle review expectations:

- The selected job shows subtitle draft rows with text edit, review decision, issue category, and reviewer note controls.
- Publishing reviewed subtitles accepts optional release notes and reports review decision/category counts.
- The `Review evidence` panel shows status, counts, safe links, and Markdown/ZIP download buttons.
- Evidence surfaces exclude raw transcript text, generated subtitle text, corrected subtitle text, reviewer note bodies, local paths, object keys, provider payloads, tokens, API keys, and media bytes.

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
