# Product Specification

## Overview

LinguaFrame is an AI video localization platform for short-form media. It turns an uploaded video into localized artifacts: timestamped subtitles, bilingual translations, TTS dubbing audio, and a subtitle-burned preview video.

The product is not a generic AI chat application. It is a media workflow system that combines file upload, object storage, FFmpeg processing, OpenAI speech and language APIs, asynchronous workers, job status tracking, retry handling, and cost observability.

LinguaFrame should also develop toward a lightweight AI infrastructure project. Video localization remains the product scenario, but the engineering core should include reusable LLM call boundaries, prompt versioning, model-call tracing, quality evaluation, cost controls, and duplicate-work avoidance.

The first implementation target is a local self-hosted demo. Hosted usage is a later target after the upload, processing, retry, storage, and cost-reporting paths are stable.

## Users

### Content Creator

A user who uploads short videos and wants captions, translations, and a dubbed audio track for another language.

### Developer Demo User

A recruiter, interviewer, or peer who watches a short demo and needs to understand the engineering workflow behind the generated media artifacts.

### System Operator

A developer who runs LinguaFrame locally, monitors job status, investigates failed media jobs, reviews OpenAI usage, and verifies generated artifacts.

## Product Positioning

LinguaFrame should be presented as:

```text
AI Video Localization Platform
```

The main product promise is:

```text
Upload a video, let LinguaFrame generate bilingual subtitles, translated captions, TTS dubbing, and a subtitle-burned preview through an auditable async pipeline.
```

## Deployment Requirements

LinguaFrame should support two deployment modes over time.

### Current Deployment Stage

The current target is local self-hosted development. The developer runs the backend, frontend, MySQL, Redis, RabbitMQ, and MinIO locally with Docker Compose or local services. OpenAI API credentials are supplied through environment variables.

Public hosted usage is deferred until the job pipeline, storage cleanup, file limits, abuse controls, and cost tracking are stable.

### Self-Hosted Local Usage

A developer can clone the repository and run their own local LinguaFrame instance.

Self-hosted usage requires:

- `docker-compose.yml` for MySQL, Redis, RabbitMQ, MinIO, backend, and frontend.
- `.env.example` documenting OpenAI API keys, database credentials, object storage credentials, and file limits.
- Backend startup instructions.
- Frontend startup instructions.
- A private-demo preflight command that verifies local readiness before media upload or paid provider-backed runs.
- FFmpeg availability inside the backend or worker container.
- A small sample video for smoke testing.

### Hosted Usage

The project owner may later deploy LinguaFrame as a hosted demo or public service.

Hosted usage requires:

- Public HTTPS frontend and backend URLs.
- Object storage backed by S3-compatible infrastructure.
- Persistent MySQL storage.
- Redis and RabbitMQ with durable configuration.
- Upload size and duration limits.
- Authentication and rate limiting.
- Cost controls before expensive OpenAI processing.
- Storage retention policies. The current MVP supports a default-off retention cleanup for terminal jobs and their stored objects.

## Functional Requirements

### Authentication

- The MVP may support a simple local user model or a single demo user.
- The current private demo gate is owner-only access control, not user authentication: when `linguaframe.demo.access-token` is non-empty, `/api/**` requires the matching demo token while readiness, OpenAPI, Swagger, and frontend assets remain public.
- The React demo stores the entered token in browser local storage for fetch requests and a same-site cookie for EventSource progress, artifact downloads, and media previews.
- The production-shaped target should support JWT authentication.
- Uploaded videos and generated artifacts must be scoped to the owning user.
- Admin or operator-only endpoints should not expose secrets.

### Retention Cleanup

- Retention cleanup is disabled by default and dry-run by default.
- Eligible jobs must be terminal: `COMPLETED`, `FAILED`, or `CANCELLED`.
- Non-terminal jobs must never be deleted by retention cleanup.
- Cleanup deletes object storage data before database rows and reports only aggregate counts.
- Source video objects and video rows are deleted only when no remaining job references that video.

### Video Upload

- The system accepts uploaded video files from the React frontend.
- The backend validates file type, file size, and duration before starting expensive work.
- Duration validation rejects files above the configured 5 minutes / 300 seconds limit before storage, queue dispatch, FFmpeg worker stages, or model calls.
- Files with supported content types but unreadable media metadata are rejected before storage and job creation.
- Accepted videos are processed in full as complete uploaded files; the system does not clip or trim accepted media to fit the duration limit.
- Uploaded source files are stored in MinIO or an S3-compatible object store.
- A durable media record is created in MySQL.
- A localization job is created asynchronously after upload.

### Job Creation And Status

- A video upload creates a durable processing job.
- Job creation returns quickly and must not run FFmpeg or OpenAI calls inline with the upload request.
- A job records source video id, owner, target language, requested output types, status, retry count, timestamps, failure reason, and usage summary.
- The frontend can query job list and job detail.
- Status transitions must be explicit and auditable.

Recommended status values:

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
FAILED
RETRYING
CANCELLED
```

### Media Processing Pipeline

The first demo pipeline should run:

```text
Upload Video
  -> Create Processing Job
  -> Extract Audio with FFmpeg
  -> Transcribe Audio with OpenAI speech-to-text
  -> Translate or polish transcript with OpenAI language model
  -> Generate SRT and VTT subtitles
  -> Generate TTS dubbing audio with OpenAI text-to-speech
  -> Burn subtitles into video with FFmpeg
  -> Record cost and artifacts
  -> Mark job completed or failed
```

### Speech-To-Text

- The system sends extracted audio to OpenAI speech-to-text.
- The system stores timestamped transcript segments.
- The transcript should preserve enough timing data to export SRT and VTT files.
- The system records audio duration, provider request metadata, and error details when safe.

### Subtitle Translation

- The system generates Chinese and English subtitle text.
- If the source language is English, the system should produce Chinese translation and preserve English original text.
- If the source language is Chinese, the system should produce English translation and preserve Chinese original text.
- If the source language is another language, the MVP may translate to Chinese and English through OpenAI.
- Translation output must preserve segment order and timing.
- Translation prompts should prefer structured output over free-form text.

### Subtitle Export

- The system exports SRT and VTT files.
- Subtitle artifacts are stored in object storage and recorded in MySQL.
- The frontend supports previewing and downloading generated subtitles.

### TTS Dubbing

- The system generates a TTS dubbing audio file from translated subtitle text.
- Each job may choose a TTS voice at upload time; jobs without a selection use the configured provider default voice.
- The MVP may produce one continuous audio track rather than perfect segment-level lip sync.
- The TTS artifact is stored in object storage.
- The frontend supports playback and download.

### Subtitle-Burned Video

- The system uses FFmpeg to burn one selected subtitle track into the original video.
- The MVP should support one generated preview video with burned subtitles.
- Replacing the original voice track with perfectly aligned dubbing audio is a future enhancement.
- The generated video artifact is stored in object storage and can be previewed or downloaded.
- Generated artifacts expose lowercase SHA-256 content fingerprints for reproducibility and future duplicate-work detection.

### Cost Tracking

- The system records per-job OpenAI usage and estimated cost.
- Cost tracking should include audio duration, token usage when available, TTS character count or audio length, call count, and processing duration.
- The cost model must be configurable because provider pricing can change.
- The UI should show estimated cost, not claim it is a billing source of truth.
- A per-job budget guard can stop later AI stages before provider calls when accumulated estimated cost reaches a configured limit.

### AI Infrastructure And LLM Operations

The post-MVP system should expose the AI workflow as infrastructure, not as scattered OpenAI calls.

Requirements:

- The backend defines provider client interfaces for speech-to-text, language translation or polishing, and text-to-speech.
- OpenAI is the default implementation, but domain services should depend on narrow interfaces rather than raw SDK calls.
- Active prompt templates are versioned and inspectable for translation and quality evaluation.
- Prompt editing, prompt experiments, and database-backed prompt history are future enhancements.
- Each model call records job id, stage, operation type, model, prompt version, latency, usage, estimated cost, status, safe input/output summaries, and safe error summary.
- Translation quality evaluation can run as a separate LLM-backed stage after subtitle translation.
- Evaluation records should capture score, detected issues, and suggested fixes without blocking the whole pipeline by default.
- Per-job cost budgets can stop expensive stages before provider calls once recorded estimated spend reaches the configured limit.
- Artifact content hashes provide stable fingerprints for duplicate-work detection.
- Artifact-level cache hits can reuse stable generated artifacts for repeat jobs from the same source video, target language, and artifact type.
- Translation provider cache hits can reuse prior translated subtitle segments when source text hash, target language, provider, model, and prompt version match.
- TTS provider cache hits can reuse prior generated audio when target subtitle text hash, language, provider, model, and voice match.
- Provider-level transcription, quality evaluation, and generic prompt-response caching can avoid duplicate model work for repeated compatible inputs in later slices.
- AI infrastructure features should be observable in job detail and admin-facing views.

These capabilities are follow-up engineering depth after the core upload-to-artifact workflow is working.

### Failure Handling And Retry

- Any pipeline step can fail and move the job to `FAILED`.
- Failure records must include the failed stage, safe error summary, retry count, and timestamp.
- The user or operator can retry a failed job.
- Retry should resume from the appropriate durable stage when possible, or restart the pipeline safely when simpler.
- The user or operator can cancel queued, retrying, or processing jobs; cancellation is soft and stops the worker before the next durable pipeline stage.
- The system must not duplicate artifacts silently after retries.
- Failed OpenAI or FFmpeg calls should not corrupt previously generated artifacts.

### Observability

- Logs should include `jobId`, `videoId`, stage, duration, and provider call id when available.
- Timeline events should make the job lifecycle understandable from the UI.
- Model or provider call records include count-only safe summaries and usage metadata without raw user text, model payloads, secrets, media bytes, or local media paths.
- Secrets and raw API keys must never be logged.

## Non-Goals

The MVP does not:

- Provide perfect lip-sync dubbing.
- Clone a speaker's voice.
- Separate multiple speakers automatically.
- Support live streaming subtitles.
- Support long-form video at production scale.
- Replace a professional video editing suite.
- Provide multi-tenant billing.
- Require Kubernetes.
- Split into many microservices before the async pipeline works.

## MVP Scope

The first resume-ready MVP supports:

- React upload and job detail UI.
- Spring Boot backend.
- MySQL-backed video, job, transcript, artifact, and usage records.
- Redis-backed job status cache and rate-limit hooks. Job detail and SSE snapshots use short-lived cache-aside Redis entries, and upload rate limiting is implemented for upload and upload-validation `POST` APIs with Redis-backed fixed-window counters.
- RabbitMQ-backed async processing.
- MinIO-backed file storage.
- FFmpeg audio extraction and subtitle burn-in.
- OpenAI speech-to-text.
- OpenAI translation or subtitle polishing.
- OpenAI text-to-speech.
- SRT and VTT export.
- Cost summary.
- Failed-job retry.
- Docker Compose local runtime.

## Frontend Requirements

The frontend is a product demo and operations surface for the media pipeline.

MVP frontend scope:

- Upload video page.
- Job list page.
- Job detail page.
- Pipeline status timeline.
- Transcript and subtitle preview.
- Subtitle download links.
- TTS audio player.
- Subtitle-burned video preview.
- Cost and usage summary.
- Failed-job retry button.
- Server-Sent Events for live selected-job progress with polling fallback.
- Read-only operator dashboard for job status counts, recent failures, model-call totals, and cache totals.

The frontend should not start as a marketing landing page. The first screen should be the working video localization experience.

## Future Scope

Planned follow-up capabilities:

- User accounts beyond a local demo user.
- WebSocket or cross-process event bus for hosted live job progress.
- Segment-level TTS alignment.
- Optional audio replacement in exported video.
- Segment-level or speaker-specific voice assignment.
- Speaker diarization.
- Source language auto-detection display.
- Batch jobs.
- Hosted demo deployment.
- S3 or cloud object storage.
- Queue worker split from API process.
- Cost budget limits per user.
- Full admin dashboard for queue control, retention actions, failures, and usage.
- LLM gateway interfaces for speech, translation, quality evaluation, and TTS.
- Prompt template versioning for reproducible AI outputs.
- Model-call audit records with latency, token usage, cost, prompt version, safe summaries, and failure details.
- LLM-based translation quality evaluation.
- Content-hash caching for duplicate audio transcription and subtitle translation.
- Worker-pool split between CPU-bound FFmpeg stages and API-bound OpenAI stages: implemented locally through `COMBINED`, `FFMPEG`, and `OPENAI` worker roles, stage-aware RabbitMQ messages, and separate FFmpeg/OpenAI routing keys.
- Per-user AI cost budgets that can stop expensive stages before execution.

## Success Criteria

LinguaFrame MVP is successful when:

- A user can upload a 30-60 second video.
- The backend creates a durable job and processes it asynchronously.
- FFmpeg extracts audio.
- OpenAI generates timestamped transcript data.
- The system generates Chinese and English subtitles.
- The system exports SRT or VTT files.
- OpenAI generates a TTS dubbing audio artifact.
- FFmpeg creates a subtitle-burned video artifact.
- The frontend shows job status, artifacts, retry actions, and cost summary.
- A failed pipeline stage records a clear failure reason and can be retried.

## Resume Target

The project should support this resume-level description:

```text
Built LinguaFrame, an AI video localization platform that processes uploaded videos through an asynchronous Spring Boot pipeline. The system extracts audio with FFmpeg, generates timestamped subtitles with OpenAI speech-to-text, translates captions with OpenAI language models, produces TTS dubbing audio, burns subtitles into videos, stores artifacts in S3-compatible object storage, and records retryable failures plus per-job model-call traces, prompt versions, quality checks, and cost metrics.
```
