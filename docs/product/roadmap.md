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
- TTS artifact storage.
- Audio preview and download API.
- TTS usage and cost record.

Do not build yet:

- Perfect segment-level alignment.
- Voice cloning.
- Lip sync.

Exit criteria:

- A completed transcript or translation can produce a TTS audio file.
- The frontend can play or download the generated audio.
- TTS failures are retryable and visible.

Suggested ExecPlan:

- `docs/plans/005-tts-dubbing-audio.md`

## Phase 6: Subtitle-Burned Video

Goal: create a preview video with selected subtitles burned in.

Build:

- FFmpeg subtitle burn-in service.
- Subtitle style defaults.
- Generated video artifact record.
- Video preview and download API.
- Burn-in failure handling.

Do not build yet:

- Audio replacement with TTS.
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
- Failed-stage retry behavior. Status: implemented with bounded retry count, structured conflict responses, and visible retry evidence in job detail.
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

Status: in progress. The repository now includes a React + Vite demo workspace with upload, server-backed job history, manual job opening, status/timeline, previews, artifacts, one-click result bundle download, retry, cost/model-call visibility, one-click diagnostics report download, an operator dashboard for demo health and manual retention cleanup, and a read-only demo readiness panel with budget guard visibility.

Build:

- React + Vite + TypeScript frontend.
- Upload page.
- Job list.
- Job detail.
- Status timeline.
- Subtitle preview.
- Audio player.
- Video preview.
- Artifact downloads.
- One-click result bundle download. Status: implemented as an on-demand ZIP archive for generated job artifacts.
- One-click diagnostics report download. Status: implemented as metadata-only JSON for safe job debugging evidence.
- One-click backend evidence Markdown download. Status: implemented as an on-demand sanitized Markdown report for readable demo evidence.
- One-click demo evidence bundle download. Status: implemented as an on-demand metadata-only ZIP with manifest, Markdown evidence, and diagnostics JSON.
- Retry button.
- Cost summary.
- Operator dashboard. Status: implemented for status counts, recent failures, model-call totals, cache totals, and manual retention cleanup preview/run actions.
- Read-only demo readiness panel. Status: implemented for demo gate, media limits, worker mode, FFmpeg toggles, provider modes, budget guard settings, and feature flags.

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

Status: in progress. The backend now supports an optional owner-only demo access token for `/api/**`, configurable upload limits, Redis-backed upload rate limiting, a default-off retention cleanup policy for terminal demo jobs and artifacts, a browser operator panel for manual retention cleanup, budget guard demo evidence, a local preflight runbook for private demo readiness, browser-visible configuration readiness, and bounded live dependency checks through the React demo.

Build:

- Production Docker Compose or simple server deployment. Status: implemented as a private-demo Compose overlay that keeps the local stack unchanged.
- HTTPS reverse proxy. Status: implemented with a Caddy overlay routing browser traffic to the frontend and `/api`, actuator, and Swagger traffic to the backend.
- Persistent object storage. Status: implemented for the private-demo path through an operator backup/restore workflow covering MySQL job history, MinIO artifacts, and Caddy state.
- Environment configuration guide. Status: implemented for private demo access token, upload limits, and retention cleanup.
- Private demo preflight runbook. Status: implemented with a local script that checks `.env`, Compose rendering, backend/frontend readiness, backend runtime freshness, live MySQL/Redis/RabbitMQ/MinIO/FFmpeg reachability, optional token-gate behavior, and configured sample paths.
- Browser-visible readiness summary. Status: implemented through the existing sanitized runtime dependency endpoint without live probes or secret exposure, including app version, bundled migration contract, budget guard state, and configured per-job estimate limit.
- Browser-visible live dependency checks. Status: implemented through `GET /api/runtime/live-checks` and the React `Live checks` panel with bounded non-destructive probes and safe status messages.
- Explicit OpenAI connectivity check. Status: implemented as a disabled-by-default `openai` live-check probe that can verify configured model metadata access before provider-backed uploads.
- File retention policy. Status: implemented as default-off dry-run cleanup for terminal jobs, source videos, and generated artifacts, with curl fallback and browser operator controls.
- Conservative upload limits. Status: implemented with configurable size and 5-minute duration gates.
- Redis upload rate limiting. Status: implemented for upload and upload-validation `POST` APIs, disabled by default.
- Owner-only private URL access gate. Status: implemented with optional demo token.

Do not build yet:

- Public multi-user service.
- Billing.
- Enterprise permissions.

Exit criteria:

- A private demo URL can process a short sample video.
- Secrets stay server-side.
- Job history and artifacts survive restarts.
- A preflight command verifies demo readiness before media upload or paid provider-backed runs.

Suggested ExecPlan:

- `docs/plans/009-private-demo-deployment.md`

## Phase 10: AI Infrastructure Observability

Goal: make OpenAI usage reproducible and inspectable instead of hidden inside pipeline stages.

Status: in progress. Model-call audit records are implemented with safe input/output summaries, and active OpenAI translation/evaluation prompt templates are registered in code, used by providers, exposed through `GET /api/prompt-templates`, and shown in the React demo.

Build:

- Prompt template records for translation, subtitle polishing, and quality evaluation. Status: implemented for read-only in-code translation and evaluation templates.
- Active prompt version selection. Status: implemented for OpenAI translation and quality evaluation providers.
- Model-call audit records. Status: implemented.
- Safe input and output summaries. Status: implemented with count-only summaries for transcription, translation, quality evaluation, and TTS.
- Latency, token, audio, character, and estimated-cost fields. Status: implemented.
- Job detail API for model calls. Status: implemented.
- Frontend model-call panel. Status: implemented.
- Frontend prompt-template panel. Status: implemented.

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

Status: in progress. The MVP now persists quality evaluation records, runs an optional non-blocking stage after target subtitle export, exposes the latest result in job detail, and renders it in the React demo.

Build:

- Quality evaluation prompt template.
- OpenAI quality evaluation client.
- Structured quality score parser.
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
- Artifact-level cache hits for stable generated media artifacts. Status: implemented for extracted audio, dubbing audio, and subtitle-burned video within the same source video and target language.
- Transcription provider cache based on extracted audio hash, provider, model, and prompt version. Status: implemented.
- Cache key for translation inputs based on source text hash, target language, provider, model, and prompt version. Status: implemented.
- Quality evaluation provider cache based on source transcript hash, target subtitle hash, target language, provider, model, and prompt version. Status: implemented.
- TTS provider cache based on target subtitle text hash, language, provider, model, and voice. Status: implemented.
- Cache-hit audit events. Status: implemented for artifact reuse, transcription provider cache hits, translation provider cache hits, quality evaluation provider cache hits, and TTS provider cache hits.

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

Do not build yet:

- Kubernetes.
- Autoscaling controller.
- Distributed tracing platform migration.

Exit criteria:

- [x] The same codebase can run a combined worker locally.
- [x] The same codebase can run separate FFmpeg and OpenAI worker roles when configured.
- [x] The roadmap and README explain why the split exists and when to use it.

Suggested ExecPlan:

- `docs/plans/035-worker-role-routing-mvp.md`
