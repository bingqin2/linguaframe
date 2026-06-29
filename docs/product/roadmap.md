# Roadmap

This roadmap breaks LinguaFrame into small, testable phases. Each phase should produce working behavior, not only code structure.

Use this document with `docs/product/target-state.md`. The target-state document defines where LinguaFrame is going; this roadmap defines how to get there without scope drift.

## Phase 0: Foundation And Documentation

Goal: make the repository understandable, runnable, and ready for focused implementation.

Status: in progress.

Build:

- Product documentation.
- Backend code standard.
- Agent governance documents.
- Root `.gitignore`.
- Backend dependency baseline.
- Local Spring profile.
- Health endpoint.
- Basic backend test.
- React frontend scaffold planning.

Exit criteria:

- Product docs exist under `docs/product`.
- Planning and progress docs exist under `docs/plans` and `docs/progress`.
- Agent guides exist under `docs/agent`.
- Backend tests pass from the backend project.
- Local backend starts.
- `/actuator/health` or `/health` returns HTTP 200.

Suggested ExecPlan:

- `docs/plans/000-project-foundation.md`

## Phase 1: Upload And Storage MVP

Goal: upload a video, validate it, store it, and create a durable job.

Build:

- Video upload API.
- File type and size validation.
- MySQL schema for videos and jobs.
- MinIO object storage integration.
- Job creation API.
- Basic job list and detail API.

Do not build yet:

- OpenAI calls.
- Subtitle generation.
- TTS.
- Video burn-in.

Exit criteria:

- A short video can be uploaded.
- The source file is stored in MinIO.
- Video and job records exist in MySQL.
- The frontend or curl can read job status.

Suggested ExecPlan:

- `docs/plans/001-upload-storage-mvp.md`

## Phase 2: Async Job Worker And FFmpeg Audio Extraction

Goal: process uploaded videos asynchronously and extract audio with FFmpeg.

Build:

- RabbitMQ job queue.
- Worker consumer.
- Job status transitions.
- FFmpeg audio extraction service.
- Extracted audio artifact record.
- Failure event and retry shell.

Do not build yet:

- OpenAI transcription.
- Translation.
- TTS.
- Subtitle burn-in.

Exit criteria:

- Upload creates a queued job.
- Worker moves the job through extraction status.
- Extracted audio is stored as an artifact.
- FFmpeg failure marks the job failed with a clear reason.

Suggested ExecPlan:

- `docs/plans/002-worker-ffmpeg-audio.md`

## Phase 3: Speech-To-Text And Subtitle Export

Goal: generate timestamped transcript data and export subtitles.

Build:

- OpenAI speech-to-text client.
- Transcript segment persistence.
- SRT export.
- VTT export.
- Subtitle artifact records.
- Subtitle preview API.

Do not build yet:

- Translation.
- TTS.
- Subtitle-burned video.

Exit criteria:

- A short video produces transcript segments.
- SRT and VTT files are generated and downloadable.
- Transcript and subtitle preview works.
- OpenAI speech failures are recorded safely.

Suggested ExecPlan:

- `docs/plans/003-speech-subtitle-export.md`

## Phase 4: Bilingual Translation

Goal: generate Chinese and English subtitles while preserving timing.

Build:

- OpenAI language client.
- Structured translation prompt.
- Subtitle segment persistence by language.
- Chinese and English subtitle export.
- Translation usage records.

Do not build yet:

- TTS.
- Video burn-in.
- Advanced subtitle styling.

Exit criteria:

- Transcript segments produce Chinese and English subtitle tracks.
- Timing stays aligned with source segments.
- Translation output can be previewed and downloaded.
- Token usage and estimated cost are recorded when available.

Suggested ExecPlan:

- `docs/plans/004-bilingual-translation.md`

## Phase 5: TTS Dubbing Audio

Goal: generate a playable dubbing audio artifact.

Build:

- OpenAI TTS client.
- Job-level TTS voice selection. Status: implemented through upload API, React form, job/list/detail responses, dispatch payloads, provider requests, and TTS cache identity.
- Dubbing text preparation.
- Time-coded custom narration segments. Status: future work. The target is an operator-facing narration editor where each segment has a start/end time, text, voice, validation, TTS evidence, and optional narrated-video export.
- TTS artifact storage.
- Audio preview and download API.
- TTS usage and cost record.

Do not build yet:

- Perfect segment-level alignment.
- Voice cloning.
- Lip sync.
- Full nonlinear video editing, timeline drag/drop, automatic background-music ducking, or replacing existing subtitle review artifacts.

Exit criteria:

- A completed transcript or translation can produce a TTS audio file.
- The frontend can play or download the generated audio.
- TTS failures are retryable and visible.
- Future narration slices can add multiple time-coded explanatory voiceover segments and produce separate narration outputs without disrupting the current localization pipeline.

Suggested ExecPlan:

- `docs/plans/005-tts-dubbing-audio.md`

## Phase 6: Subtitle-Burned Video

Goal: create a preview video with selected subtitles burned in.

Build:

- FFmpeg subtitle burn-in service.
- Subtitle style defaults.
- Generated video artifact record.
- Audio replacement with TTS. Status: implemented as a separate `DUBBED_VIDEO` artifact created after TTS audio and generated `BURNED_VIDEO` are both available.
- Video preview and download API.
- Burn-in failure handling.

Do not build yet:

- Multi-track export.
- Advanced video editor controls.

Exit criteria:

- A generated subtitle file can be burned into the uploaded video.
- The resulting video can be previewed or downloaded.
- Failed burn-in records a clear failure reason.

Suggested ExecPlan:

- `docs/plans/006-subtitle-burned-video.md`

## Phase 7: Cost, Retry, And Observability Hardening

Goal: make the demo production-shaped enough for interview discussion.

Build:

- Per-job usage summary.
- Estimated cost configuration.
- Basic model-call audit records.
- Job timeline events.
- Timeline-derived pipeline progress and stage timing. Status: implemented in job detail, diagnostics, evidence exports, terminal demo summaries, React selected-job progress, and operator dashboard timing rows.
- Failed-stage retry behavior. Status: implemented with bounded retry count, structured conflict responses, visible retry evidence in job detail, and advisory failure triage for OpenAI, budget, media, storage, worker/queue, configuration, cancellation, and unknown failures.
- Redis status cache. Status: implemented for `GET /api/jobs/{jobId}` and SSE job-detail snapshots with short-lived Redis cache-aside entries.
- Rate-limit hooks. Status: implemented for upload and upload-validation `POST` APIs with Redis-backed fixed-window counters.
- Structured logs with job id. Status: implemented with worker MDC fields for job id, video id, stage, and worker role.
- OpenAPI docs. Status: implemented with demo-token security metadata, primary controller tags, path contract tests, and Swagger demo guidance.

Do not build yet:

- Multi-tenant billing.
- Complex admin analytics.
- Kubernetes.

Exit criteria:

- Job detail shows cost, usage, duration, and failed stage.
- Job detail can expose safe model-call summaries.
- Failed jobs can be retried safely within the configured retry limit.
- A job timeline explains the pipeline without reading logs.
- OpenAPI docs expose the primary APIs.

Suggested ExecPlan:

- `docs/plans/007-cost-retry-observability.md`

## Phase 8: React Demo Experience

Goal: make the system demonstrable without terminal inspection.

Status: in progress. The repository now includes a React + Vite demo workspace with upload, owner quota preflight, reusable demo run profiles, server-backed job history, manual job opening, guided demo review, source media evidence, status/timeline, pipeline progress, previews, media delivery playback, demo handoff checklist, demo session report, subtitle review, subtitle draft editing and corrected subtitle export, artifacts, one-click result bundle download, retry, cost/model-call visibility, one-click diagnostics report download, an operator dashboard for demo health, stage timing, and manual retention cleanup, and a read-only demo readiness panel with budget guard visibility.

Build:

- React + Vite + TypeScript frontend.
- Upload page.
- Owner quota preflight workspace. Status: implemented with `GET /api/media/uploads/preflight`, an upload-adjacent React panel, upload blocking when quota is exhausted, runtime readiness metadata, and terminal `scripts/demo/owner-quota-preflight.sh` evidence.
- Demo run profile selector. Status: implemented with read-only built-in profiles that populate upload fields, persist selected profile metadata, and keep manual field overrides available.
- Job list.
- Job detail.
- Status timeline.
- Guided demo review workspace. Status: implemented as a browser panel that orders one selected job into input, pipeline, review, delivery, evidence, and handoff steps with anchor links and metadata-only presenter notes.
- Source media evidence workspace. Status: implemented with safe upload metadata, a controlled source-video download route, browser source media panel, and terminal source media summary.
- Subtitle preview.
- Subtitle review workspace. Status: implemented as a read-only source/target comparison panel with missing-target and timing-mismatch counts, quality score/verdict, target subtitle artifact count, and safe evidence metadata.
- Subtitle draft editing workspace. Status: implemented as a target-subtitle draft overlay with save, reset, clear, review decisions, issue categories, reviewer notes, corrected JSON/SRT/VTT export links, metadata-only evidence counts, and explicit reviewed artifact publishing.
- Subtitle review evidence workspace. Status: implemented with `GET /api/jobs/{jobId}/subtitle-review-evidence`, Markdown/ZIP downloads, a browser review-evidence panel, terminal `scripts/demo/subtitle-review-evidence.sh`, and deterministic/OpenAI/full Tears exports. Evidence stays metadata-only and excludes raw subtitle text and reviewer note bodies.
- Reviewed subtitle delivery. Status: implemented as publishable reviewed JSON/SRT/VTT artifacts plus optional separate reviewed burned video output. It intentionally does not regenerate TTS audio, replace generated subtitle artifacts, or replace the generated burned video artifact.
- Delivery handoff manifest. Status: implemented as a selected-job browser panel plus JSON and Markdown backend endpoints that separate reviewed handoff artifacts from generated audit artifacts and link safe verification evidence.
- Media delivery workspace. Status: implemented for generated dubbing audio, generated burned video, and reviewed burned video with browser playback, direct downloads, content type, size, hash evidence, and generated/reused cache state.
- Demo handoff checklist. Status: implemented as a selected-job browser checklist plus terminal summary that combines job completion, reviewed output readiness, media output availability, evidence links, quality, cost/model-call, cache, and failure-triage metadata.
- Demo session report. Status: implemented as a selected-job browser panel plus terminal Markdown output that summarizes one run's input/job, generated outputs, handoff evidence, cost/cache, and failure triage using metadata only.
- Artifact downloads.
- One-click result bundle download. Status: implemented as an on-demand ZIP archive for generated job artifacts.
- One-click diagnostics report download. Status: implemented as metadata-only JSON for safe job debugging evidence, including advisory failure triage when a job fails.
- One-click backend evidence Markdown download. Status: implemented as an on-demand sanitized Markdown report for readable demo evidence.
- One-click demo evidence bundle download. Status: implemented as an on-demand metadata-only ZIP with manifest, Markdown evidence, and diagnostics JSON.
- One-click reviewed handoff package download. Status: implemented as an on-demand ZIP with reviewed subtitle artifacts, optional reviewed burned video, delivery manifest, diagnostics, evidence, and a package manifest, excluding internal audit artifacts and secrets.
- One-click demo run package download. Status: implemented as an on-demand metadata-only ZIP workspace with manifest, README, job detail, diagnostics, evidence, quality evidence, delivery manifest, handoff checklist, and session report.
- Browser cache replay evidence. Status: implemented as a read-only selected-job comparison panel that shows provider cache-hit stages, artifact reuse, model-call delta, estimated-cost delta, and safe replay evidence exports.
- Backend-backed demo profile comparison. Status: implemented with job comparison JSON/Markdown endpoints, a browser `Demo comparison` panel, and terminal full-video comparison downloads.
- Same-source demo run matrix. Status: implemented with `GET /api/jobs/{jobId}/demo-run-matrix`, a browser `Demo run matrix` panel, and full Tears terminal `demo-run-matrix.json` output for baseline, quality, cost, cache, and handoff inspection across recent runs.
- Demo presenter pack workspace. Status: implemented with `GET /api/jobs/{jobId}/demo-presenter-pack`, a browser `Demo presenter pack` panel, and full Tears terminal `demo-presenter-pack.json` output for readiness, recommended runs, presenter notes, and safe evidence links.
- Retry button.
- Cost summary.
- Operator dashboard. Status: implemented for status counts, recent failures, model-call totals, cache totals, and manual retention cleanup preview/run actions.
- Read-only demo readiness panel. Status: implemented for demo gate, media limits, worker mode, FFmpeg toggles, provider modes, budget guard settings, and feature flags.
- Private demo launch rehearsal. Status: implemented with `GET /api/operator/private-demo/launch-rehearsal`, a browser `Private demo launch rehearsal` panel, and terminal `private-demo-launch-rehearsal.sh` output for ordered go/no-go steps, recommended next action, safe commands, and evidence routes.
- Private demo evidence gallery. Status: implemented with `GET /api/operator/private-demo/evidence-gallery`, a browser `Private demo evidence gallery` panel, and terminal `private-demo-evidence-gallery.sh` output for completed-run selection, handoff readiness, recommended run evidence, and safe package links.

Do not build yet:

- Marketing landing page.
- Complex user management.
- Full admin dashboard.

Exit criteria:

- A viewer can understand and run the demo from the browser.
- The UI shows real job states and artifacts.
- Failures and retries are visible.

Suggested ExecPlan:

- `docs/plans/008-react-demo-experience.md`

## Phase 9: Private Demo Deployment

Goal: prepare a controlled hosted demo after the local pipeline is stable.

Status: in progress. The backend now supports an optional owner-only demo access token for `/api/**`, browser owner-session login/logout on top of that token, a local owner-account JWT bridge with owner workspace boundaries, a configured demo owner boundary persisted on videos and localization jobs, owner quota preflight before upload creation, configurable upload limits, Redis-backed upload rate limiting, a default-off retention cleanup policy for terminal demo jobs and artifacts, a browser operator panel for manual retention cleanup, budget guard demo evidence, a local preflight runbook for private demo readiness, browser-visible configuration readiness, bounded live dependency checks through the React demo, private-demo operations readiness, launch rehearsal, a completed-run evidence gallery, and a private demo run archive with browser and terminal metadata-only reports.

Build:

- Production Docker Compose or simple server deployment. Status: implemented as a private-demo Compose overlay that keeps the local stack unchanged.
- HTTPS reverse proxy. Status: implemented with a Caddy overlay routing browser traffic to the frontend and `/api`, actuator, and Swagger traffic to the backend.
- Persistent object storage. Status: implemented for the private-demo path through an operator backup/restore workflow covering MySQL job history, MinIO artifacts, and Caddy state.
- Environment configuration guide. Status: implemented for private demo access token, upload limits, and retention cleanup.
- Private demo preflight runbook. Status: implemented with a local script that checks `.env`, Compose rendering, backend/frontend readiness, backend runtime freshness, live MySQL/Redis/RabbitMQ/MinIO/FFmpeg reachability, owner-session login/logout, optional token-header behavior, and configured sample paths.
- Browser-visible readiness summary. Status: implemented through the existing sanitized runtime dependency endpoint without live probes or secret exposure, including app version, bundled migration contract, budget guard state, and configured per-job estimate limit.
- Browser-visible live dependency checks. Status: implemented through `GET /api/runtime/live-checks` and the React `Live checks` panel with bounded non-destructive probes and safe status messages.
- Private demo operations readiness workspace. Status: implemented through `GET /api/operator/private-demo/operations`, the React `Private demo operations` panel, and `scripts/demo/private-demo-operations-report.sh`.
- Private demo evidence gallery workspace. Status: implemented through `GET /api/operator/private-demo/evidence-gallery`, the React `Private demo evidence gallery` panel, and `scripts/demo/private-demo-evidence-gallery.sh`.
- Private demo run archive workspace. Status: implemented through `GET /api/operator/private-demo/run-archive`, the React `Private demo run archive` panel, and `scripts/demo/private-demo-run-archive.sh` as a metadata-only table of contents for readiness, recommended completed run, and safe package links.
- Demo run launcher workspace. Status: implemented through `GET /api/operator/demo-run-launcher`, the upload-form `Demo run launcher` panel, and `scripts/demo/demo-run-launcher.sh` for read-only recommended sample/profile commands, readiness gates, and expected evidence files before a full Tears run.
- Explicit OpenAI connectivity check. Status: implemented as a disabled-by-default `openai` live-check probe that can verify configured model metadata access before provider-backed uploads.
- OpenAI demo smoke profile. Status: implemented with a separate no-secret env template, preflight script, and provider-backed smoke runner for real credential demos.
- File retention policy. Status: implemented as default-off dry-run cleanup for terminal jobs, source videos, and generated artifacts, with curl fallback and browser operator controls.
- Conservative upload limits. Status: implemented with configurable size and 5-minute duration gates.
- Redis upload rate limiting. Status: implemented for upload and upload-validation `POST` APIs, disabled by default.
- Owner-only private URL access gate. Status: implemented with optional demo token, React owner-session login/logout, and header-token compatibility for Swagger, curl, and scripts.
- Local account JWT bridge. Status: implemented with configured owner credentials, short-lived HMAC bearer tokens, React account sign-in/sign-out, Swagger `BearerAuth`, and terminal `auth-smoke.sh` validation while preserving demo-token compatibility.
- Authenticated owner access workspace. Status: implemented with bearer/demo-token owner scoping for job history/detail, media metadata/source downloads, artifact list/download/archive, diagnostics/evidence, operator dashboard owner metadata, React workspace scope display/history refresh, and terminal `owner-workspace-smoke.sh`.
- Demo owner data boundary. Status: implemented with `LINGUAFRAME_DEMO_OWNER_ID`, persisted `owner_id` columns for videos/jobs, owner-scoped upload/job/media reads, browser-visible owner metadata, and terminal-safe owner summary output.
- Owner quota and budget preflight. Status: implemented with configurable active-job, queued-job, and owner daily-budget limits; upload rejection before storage/database/dispatch; browser owner quota panel; runtime readiness metadata; env templates; and terminal `owner-quota-preflight.sh` evidence.

Do not build yet:

- Public multi-user service.
- Billing.
- Enterprise permissions.

Exit criteria:

- A private demo URL can process a short sample video.
- Secrets stay server-side.
- Job history and artifacts survive restarts.
- A preflight command verifies demo readiness before media upload or paid provider-backed runs.
- Owner quota preflight can stop new uploads before storage, queue, FFmpeg, or provider spend.

Suggested ExecPlan:

- `docs/plans/009-private-demo-deployment.md`

## Phase 10: AI Infrastructure Observability

Goal: make OpenAI usage reproducible and inspectable instead of hidden inside pipeline stages.

Status: in progress. Model-call audit records are implemented with safe input/output summaries, active OpenAI translation/evaluation/polishing prompt templates are registered in code, used by providers, exposed through `GET /api/prompt-templates`, shown in the React demo, and packaged into a safe per-job AI audit ZIP. A separate OpenAI smoke profile now verifies provider-backed model calls and evidence generation without making `.env.example` a paid default. Upload-time translation style control is implemented for `NATURAL`, `FORMAL`, and `CONCISE` jobs, including provider prompt payloads and cache identity. Upload-time translation glossary control is implemented with strict parsing, OpenAI request payload support, cache-key isolation, browser form/history/detail metadata, and safe evidence metadata. Upload-time subtitle polishing control is implemented for `OFF`, `BALANCED`, and `STRICT` jobs, including a separate audited stage, provider cache identity, browser/demo controls, and evidence metadata. Demo run profiles now package these upload-time controls into repeatable presets while cache identity stays based on the resolved settings.

Build:

- Prompt template records for translation, subtitle polishing, and quality evaluation. Status: implemented for read-only in-code translation, subtitle polishing, and evaluation templates.
- Active prompt version selection. Status: implemented for OpenAI translation, subtitle polishing, and quality evaluation providers.
- Model-call audit records. Status: implemented.
- Safe input and output summaries. Status: implemented with count-only summaries for transcription, translation, quality evaluation, and TTS.
- Translation style control. Status: implemented across upload, job metadata, OpenAI translation payloads, safe summaries, provider cache keys, React demo controls, and demo scripts.
- Subtitle polishing control. Status: implemented across upload, job metadata, OpenAI polishing payloads, safe summaries, provider cache keys, React demo controls, and demo scripts.
- Demo run profiles. Status: implemented as read-only presets exposed by `GET /api/demo-run-profiles`, React upload selection, terminal `LINGUAFRAME_DEMO_PROFILE_ID`, persisted job metadata, and safe evidence/package metadata.
- Latency, token, audio, character, and estimated-cost fields. Status: implemented.
- Job detail API for model calls. Status: implemented.
- Frontend model-call panel. Status: implemented.
- Frontend prompt-template panel. Status: implemented.
- Per-job AI audit package. Status: implemented as an on-demand metadata-only ZIP with model calls, active prompt templates, usage summary, and a Markdown audit report.

Do not build yet:

- Multi-provider routing.
- Prompt editing UI.
- Automatic prompt optimization.
- Complex experiment tracking.

Exit criteria:

- Each translation or evaluation call records the prompt version.
- Each model call records model, stage, status, latency, usage, and estimated cost.
- A job detail view can explain which OpenAI calls ran without reading backend logs.

Suggested ExecPlan:

- `docs/plans/010-ai-infra-observability.md`

## Phase 11: Translation Quality Evaluation

Goal: add a lightweight LLM-based quality check for generated subtitles.

Status: in progress. The MVP now persists quality evaluation records, runs an optional non-blocking stage after target subtitle export, exposes the latest result in job detail, renders it in the React demo, and provides browser/backend/terminal quality-evidence Markdown exports.

Build:

- Quality evaluation prompt template.
- OpenAI quality evaluation client.
- Structured quality score parser.
- Quality evaluation evidence workspace. Status: implemented with browser copy/download actions, backend Markdown download, terminal `/tmp/linguaframe-demo/quality-evidence.md` output, and metadata-only safety validation.
- Quality evaluation persistence.
- Evaluation result in job detail.
- Optional non-blocking evaluation stage after translation.

Do not build yet:

- Human review workflow.
- Automated prompt rewriting loop.
- Fine-tuned evaluation model.

Exit criteria:

- A translated subtitle track can receive a quality score.
- Evaluation records completeness, readability, timing preservation, naturalness, issues, and suggested fixes.
- Evaluation failure does not break the core artifact generation path by default.

Suggested ExecPlan:

- `docs/plans/023-translation-quality-evaluation-mvp.md`

## Phase 12: AI Cost Budget And Cache

Goal: reduce avoidable AI spend and show cost-control awareness.

Build:

- Per-job cost budget configuration. Status: implemented with sanitized readiness visibility and a repeatable Docker budget-guard evidence script.
- Per-user daily cost budget hook. Status: implemented for the private-demo budget identity hook; real authenticated user budgets remain out of scope.
- Budget checks before translation, evaluation, and TTS stages. Status: implemented for guarded AI stages using accumulated recorded estimated cost before provider execution.
- Content hash foundation for generated artifacts. Status: implemented for artifact records and UI visibility.
- Artifact-level cache hits for stable generated media artifacts. Status: implemented for extracted audio, dubbing audio, and subtitle-burned video within the same source video and target language; subtitle-burned video reuse is also scoped by subtitle style preset.
- Subtitle burn-in style presets. Status: implemented as upload-time `STANDARD`, `LARGE`, and `HIGH_CONTRAST` presets persisted on the job, shown in demo evidence, applied to FFmpeg burn-in, and included in burned-video artifact cache identity.
- Transcription provider cache based on extracted audio hash, provider, model, and prompt version. Status: implemented.
- Cache key for translation inputs based on source text hash, target language, provider, model, and prompt version. Status: implemented.
- Quality evaluation provider cache based on source transcript hash, target subtitle hash, target language, provider, model, and prompt version. Status: implemented.
- TTS provider cache based on target subtitle text hash, language, provider, model, and voice. Status: implemented.
- Cache-hit audit events. Status: implemented for artifact reuse, transcription provider cache hits, translation provider cache hits, quality evaluation provider cache hits, and TTS provider cache hits.
- Browser-visible cache replay evidence. Status: implemented by composing existing safe job detail and artifact APIs in the React demo without adding a backend aggregate endpoint.
- Pre-upload demo readiness workspace. Status: implemented with `GET /api/media/uploads/readiness`, a browser `Upload readiness` panel, and `scripts/demo/upload-readiness.sh` for metadata-only terminal go/no-go checks.
- Demo run launcher workspace. Status: implemented as a metadata-only operator aggregate that connects sample selection, the `tears-showcase` profile, upload-readiness gates, the full Tears command, and expected post-run evidence outputs before paid or full-video execution.
- Reviewed subtitle workflow cockpit. Status: implemented as a metadata-only selected-job aggregate that connects subtitle review, draft edits, reviewed artifact readiness, optional reviewed burned-video availability, delivery manifest readiness, safe handoff links, browser UI, and terminal export.
- Demo handoff portal package. Status: implemented as a metadata-only selected-job aggregate that exports JSON, Markdown, and a static `index.html` ZIP linking reviewer workspace, snapshot, run packages, diagnostics, evidence, and OpenAI proof without media bytes or secrets.

Do not build yet:

- Real billing.
- Payments.
- Provider price automation.
- Real authenticated per-user daily budgets.
- Global distributed cache.
- Generic prompt-response caching.

Exit criteria:

- Expensive stages can be skipped before execution when a budget is exceeded.
- Generated artifacts expose stable content hashes.
- Repeated compatible media artifact outputs can be reused without rewriting object storage.
- Repeated compatible transcription provider inputs can be reused without another transcription provider call.
- Repeated compatible translation provider inputs can be reused without another translation provider call.
- Repeated compatible TTS provider inputs can be reused without another TTS provider call.
- Cache behavior is visible in job events or model-call records.

Suggested ExecPlan:

- `docs/plans/012-ai-cost-budget-cache.md`

## Phase 13: Worker Pool Split

Goal: prepare the pipeline for heavier workloads by separating CPU-bound and API-bound work.

Build:

- [x] Worker role configuration.
- [x] FFmpeg worker role for audio extraction and subtitle burn-in.
- [x] OpenAI worker role for speech, translation, evaluation, and TTS.
- [x] Queue names or routing keys per workload type.
- [x] Documentation for when to run one combined worker versus split workers.
- [x] Browser and terminal readiness evidence for active role, listener queue, split-worker routes, owned stages, and safe startup commands.

Do not build yet:

- Kubernetes.
- Autoscaling controller.
- Distributed tracing platform migration.

Exit criteria:

- [x] The same codebase can run a combined worker locally.
- [x] The same codebase can run separate FFmpeg and OpenAI worker roles when configured.
- [x] The roadmap and README explain why the split exists and when to use it.
- [x] Operators can verify the active combined or split-worker topology before upload without changing RabbitMQ routing.

Suggested ExecPlan:

- `docs/plans/035-worker-role-routing-mvp.md`
