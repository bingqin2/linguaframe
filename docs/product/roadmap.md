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
- Failed-stage retry behavior.
- Redis status cache.
- Rate-limit hooks.
- Structured logs with job id.
- OpenAPI docs.

Do not build yet:

- Multi-tenant billing.
- Complex admin analytics.
- Kubernetes.

Exit criteria:

- Job detail shows cost, usage, duration, and failed stage.
- Job detail can expose safe model-call summaries.
- Failed jobs can be retried safely.
- A job timeline explains the pipeline without reading logs.
- OpenAPI docs expose the primary APIs.

Suggested ExecPlan:

- `docs/plans/007-cost-retry-observability.md`

## Phase 8: React Demo Experience

Goal: make the system demonstrable without terminal inspection.

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
- Retry button.
- Cost summary.

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

Build:

- Production Docker Compose or simple server deployment.
- HTTPS reverse proxy.
- Persistent object storage.
- Environment configuration guide.
- File retention policy.
- Conservative upload limits.

Do not build yet:

- Public multi-user service.
- Billing.
- Enterprise permissions.

Exit criteria:

- A private demo URL can process a short sample video.
- Secrets stay server-side.
- Job history and artifacts survive restarts.

Suggested ExecPlan:

- `docs/plans/009-private-demo-deployment.md`

## Phase 10: AI Infrastructure Observability

Goal: make OpenAI usage reproducible and inspectable instead of hidden inside pipeline stages.

Build:

- Prompt template records for translation, subtitle polishing, and quality evaluation.
- Active prompt version selection.
- Model-call audit records.
- Safe input and output summaries.
- Latency, token, audio, character, and estimated-cost fields.
- Job detail API for model calls.
- Frontend model-call panel.

Do not build yet:

- Multi-provider routing.
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

- `docs/plans/011-translation-quality-evaluation.md`

## Phase 12: AI Cost Budget And Cache

Goal: reduce avoidable AI spend and show cost-control awareness.

Build:

- Per-job cost budget configuration.
- Per-user daily cost budget hook.
- Budget checks before translation, evaluation, and TTS stages.
- Content hash for extracted audio.
- Cache key for translation inputs based on source text hash, target language, model, and prompt version.
- Cache-hit audit events.

Do not build yet:

- Real billing.
- Payments.
- Global distributed cache.

Exit criteria:

- Expensive stages can be skipped before execution when a budget is exceeded.
- Repeated compatible inputs can be detected by cache keys.
- Cache behavior is visible in job events or model-call records.

Suggested ExecPlan:

- `docs/plans/012-ai-cost-budget-cache.md`

## Phase 13: Worker Pool Split

Goal: prepare the pipeline for heavier workloads by separating CPU-bound and API-bound work.

Build:

- Worker role configuration.
- FFmpeg worker role for audio extraction and subtitle burn-in.
- OpenAI worker role for speech, translation, evaluation, and TTS.
- Queue names or routing keys per workload type.
- Documentation for when to run one combined worker versus split workers.

Do not build yet:

- Kubernetes.
- Autoscaling controller.
- Distributed tracing platform migration.

Exit criteria:

- The same codebase can run a combined worker locally.
- The same codebase can run separate FFmpeg and OpenAI worker roles when configured.
- The roadmap and README explain why the split exists and when to use it.

Suggested ExecPlan:

- `docs/plans/013-worker-pool-split.md`
