# Target State

## Product Target

LinguaFrame should become a production-shaped AI media localization system that demonstrates real backend engineering, not only API wrapping. The target state is a reliable local or hosted platform that accepts videos, processes them through an observable asynchronous pipeline, and returns useful localized media artifacts.

The project should stay focused on video localization:

- Speech-to-text.
- Bilingual subtitle generation.
- Subtitle translation and polishing.
- TTS dubbing audio.
- Subtitle-burned preview video.
- Cost, retry, and failure observability.

It should not become a generic chatbot, a full video editor, or an unbounded collection of AI features.

The AI infrastructure target is to make model usage reproducible, observable, and cost-aware. OpenAI remains the default API provider, but the backend should treat speech, language, quality evaluation, and TTS calls as auditable pipeline operations rather than one-off SDK calls.

## Deployment Target

LinguaFrame should support staged deployment.

### Stage 1: Local Self-Hosted Development

The current implementation target is local self-hosted development.

Target local setup:

```text
git clone linguaframe
  -> configure .env
  -> docker compose up --build
  -> open React frontend
  -> upload sample video
  -> inspect generated artifacts
```

Stage 1 goals:

- Validate backend startup.
- Validate MySQL migrations.
- Validate Redis connectivity.
- Validate RabbitMQ queue dispatch.
- Validate MinIO artifact storage.
- Validate FFmpeg availability.
- Validate OpenAI API calls with a short sample video.
- Validate real OpenAI demo runs through an explicit no-secret env template, preflight, and smoke runner instead of enabling paid providers by default.
- Keep secrets local.

### Stage 2: Private Demo Deployment

After the local pipeline works, LinguaFrame can be deployed to a private server for demos.

Target private demo shape:

```text
Internet
  -> HTTPS frontend
  -> HTTPS backend
  -> MySQL
  -> Redis
  -> RabbitMQ
  -> S3-compatible object storage
```

Stage 2 goals:

- Use a real HTTPS URL.
- Use a private-demo reverse proxy that exposes only the public web entry point while keeping app and dependency ports internal.
- Support controlled uploads from the project owner.
- Let the owner start and end a browser session with the configured private demo token, while keeping Swagger, curl, and scripts compatible with the demo access header.
- Persist job history and artifacts across restarts.
- Provide an operator backup and restore path for private-demo migration or server rebuilds.
- Keep file size and duration limits conservative.
- Show repeatable demo input and output.

### Stage 3: Public Hosted Service

Public hosted usage is a later maturity target and should not drive the first implementation.

Stage 3 requires:

- Real user authentication.
- Per-user storage isolation.
- Rate limiting and quota checks before expensive processing.
- Cost budgets.
- Abuse controls.
- Storage retention.
- Background cleanup.
- Stronger queue and worker recovery.
- Clear privacy and data-handling policy.

## User Experience Target

A user should be able to:

1. Open the LinguaFrame frontend.
2. Upload a short video.
3. Select target language or keep the default Chinese-English localization.
4. Watch job progress through a status timeline.
5. Preview transcript segments and translated subtitles.
6. Download SRT or VTT files.
7. Play the generated dubbing audio.
8. Preview or download the subtitle-burned video.
9. Inspect cost and processing time.
10. Retry the job if a step fails.
11. Identify the current stage, slowest stage, and stage timing evidence without reading backend logs.

The UI should make the system feel like a media workflow tool, not a chat page.

## Frontend Target

The frontend target is a React work surface for upload, job inspection, and artifact review.

Target screens:

- Upload and job creation.
- Job list.
- Job detail.
- Pipeline timeline.
- Pipeline progress and stage timing summary.
- Transcript and subtitle table.
- Artifact preview and downloads.
- Cost and usage summary.
- Failure and retry panel.

The first screen should be the usable upload and job dashboard. A marketing landing page is not needed for the project goal.

## Backend Target

The backend should provide:

- JWT authentication or a local demo-user mode.
- Video upload APIs.
- Object storage abstraction.
- MySQL-backed media, job, transcript, artifact, usage, and failure records.
- RabbitMQ-backed async job processing.
- Redis-backed job status cache for job detail snapshots, rate-limit hooks, and lightweight coordination.
- FFmpeg integration for audio extraction and subtitle burn-in.
- OpenAI speech-to-text client.
- OpenAI language client for translation and subtitle polishing.
- OpenAI TTS client.
- OpenAI-backed translation quality evaluation client.
- Prompt template versioning for translation, polishing, and evaluation prompts.
- Model-call audit records with model, prompt version, usage, latency, cost, status, and safe errors.
- Timeline-derived pipeline progress and stage timing summaries.
- Content-hash caching hooks for repeated transcription and translation work.
- Per-job and per-user budget checks before expensive stages.
- SRT and VTT export.
- Retryable failure handling.
- Cost estimation and usage capture.
- OpenAPI documentation.
- Structured logs with job and stage identifiers.

## Pipeline Target

The long-term runtime may split API and worker processes:

```text
linguaframe-api
  Uploads, auth, job APIs, artifact APIs

linguaframe-worker
  FFmpeg processing, OpenAI calls, subtitle export, retry handling

shared storage
  MySQL, Redis, RabbitMQ, object storage
```

The MVP should remain a modular monolith until the pipeline is proven. API and worker split should happen only when it improves runtime clarity, isolation, or deployment.

The future worker target can separate workloads by resource profile:

```text
ffmpeg-worker
  CPU-bound audio extraction and subtitle burn-in

openai-worker
  API-bound speech, translation, evaluation, and TTS calls

artifact-worker
  Subtitle export, object storage writes, cleanup, and download preparation
```

This split is optional. It should be implemented only after the modular monolith has stable stage boundaries and durable job state.

## Safety And Cost Target

LinguaFrame must:

- Reject unsupported file types.
- Enforce file size and duration limits before OpenAI calls.
- Never log raw API keys.
- Never expose private object storage credentials to the browser.
- Never process arbitrary file paths supplied by the user.
- Avoid overwriting artifacts without explicit versioning or idempotent keys.
- Record provider failures safely.
- Explain failed jobs with safe advisory triage that maps known OpenAI, budget, media, storage, worker/queue, configuration, cancellation, and unknown failures to recommended next actions.
- Track estimated cost per job.
- Enforce per-job and per-user budget limits before expensive OpenAI stages.
- Cache duplicate inputs by content hash when it avoids repeated model calls safely.
- Record prompt template versions for reproducibility.
- Allow expensive steps to be disabled for local testing.
- Treat estimated cost as informational, not authoritative billing.

## Engineering Target

The codebase should remain understandable to a Java backend interviewer:

- Modular Spring Boot backend.
- Package-by-domain structure.
- Thin controllers.
- Service interfaces and implementations.
- Explicit DTO, VO, BO, Query, Entity, and Enum objects.
- External provider clients behind interfaces.
- Media processing steps behind pipeline stage boundaries.
- Durable job status transitions.
- Tests for validation, status transitions, retry behavior, and subtitle export formatting.
- Local reproducible Docker Compose runtime.

## Resume Target

The final project should demonstrate:

- Java and Spring Boot backend development.
- React product UI.
- MySQL schema design.
- Redis caching and coordination.
- RabbitMQ asynchronous processing.
- S3-compatible object storage with MinIO.
- FFmpeg media processing.
- OpenAI speech-to-text, language, and TTS APIs.
- Prompt versioning and model-call tracing.
- LLM-based quality evaluation.
- Long-running job orchestration.
- Retry handling.
- Cost and usage observability.
- AI cost budget controls and cache-aware duplicate-work avoidance.
- Docker Compose deployment.
