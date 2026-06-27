# Architecture

## Architectural Style

LinguaFrame starts as a modular Spring Boot backend plus a React frontend. The first goal is to prove a reliable video localization pipeline, not to introduce premature microservices.

The MVP should remain one backend deployable with clear internal boundaries:

```text
React Frontend
      |
Spring Boot API
      |
Job Service
      |
RabbitMQ Worker Pipeline
      |
FFmpeg + OpenAI APIs + MinIO
      |
Generated Artifacts
```

The API and worker can later split into separate processes once the long-running media workload justifies it.

## Frontend Role

LinguaFrame uses React for upload, job status, artifact preview, and demo clarity.

Recommended first screens:

- Upload video.
- Job list.
- Job detail.
- Pipeline status timeline.
- Transcript and subtitle preview.
- TTS audio player.
- Subtitle-burned video preview.
- Cost and failure summary.

The frontend should be a working media localization tool. It should not start as a marketing landing page.

## Runtime Flow

```text
User uploads video
  -> UploadController validates metadata and receives file
  -> ObjectStorageService stores source video in MinIO
  -> VideoService creates video record
  -> JobService creates localization job
  -> JobQueuePublisher publishes job message to RabbitMQ
  -> JobWorker claims job
  -> FFmpegService extracts audio
  -> OpenAiSpeechClient creates timestamped transcript
  -> SubtitleService stores transcript segments
  -> OpenAiLanguageClient translates or polishes subtitles
  -> SubtitleExportService writes SRT and VTT artifacts
  -> OpenAiTtsClient generates dubbing audio
  -> FFmpegService burns selected subtitles into video
  -> UsageService records duration, token, call, and cost estimates
  -> ArtifactService records generated files
  -> JobService marks job completed or failed
```

## Backend Modules

Recommended package layout:

```text
com.linguaframe
  common
    config
    error
    response
    time
  auth
    controller
    service
    domain
  user
    service
    mapper
    domain
  media
    controller
    service
    mapper
    domain
  job
    controller
    service
    queue
    worker
    mapper
    domain
  storage
    service
    client
    domain
  ffmpeg
    service
    domain
  openai
    speech
    language
    evaluation
    tts
    domain
  prompt
    service
    mapper
    domain
  subtitle
    service
    export
    mapper
    domain
  artifact
    controller
    service
    mapper
    domain
  usage
    service
    mapper
    domain
  observability
```

Package-by-domain is preferred. Layers should be created inside each domain only when needed.

## Module Responsibilities

### Auth

Responsibilities:

- Support local demo user mode in early phases.
- Later provide JWT login and request authentication.
- Scope videos, jobs, and artifacts to the owning user.

The MVP may defer full authentication if it keeps local demo progress clear, but public hosted usage requires authentication before launch.

### Media

Responsibilities:

- Accept video upload metadata.
- Validate content type, file size, and duration limit.
- Create source video records.
- Connect uploaded files to localization jobs.

The media layer must not call OpenAI or run FFmpeg inline in the upload request.

### Job

Responsibilities:

- Create localization jobs.
- Manage status transitions.
- Publish work to RabbitMQ.
- Handle cancellation and retry.
- Expose job list and job detail APIs.
- Record failure stage and reason.

Status changes should go through a dedicated service instead of direct field mutation across modules.

### Worker Pipeline

Responsibilities:

- Consume RabbitMQ messages.
- Load job and source video state.
- Execute pipeline stages in order.
- Record timeline events and stage durations.
- Retry safely when configured.
- Mark jobs completed or failed.

The worker pipeline owns orchestration. Individual stages should remain testable services.

### Storage

Responsibilities:

- Store and retrieve source videos and generated artifacts.
- Hide MinIO or S3-specific client details behind an interface.
- Generate signed URLs or backend-proxied download links.
- Prevent path traversal and user-controlled object key abuse.

The browser must not receive object storage credentials.

### FFmpeg

Responsibilities:

- Extract audio from source video.
- Normalize audio format for OpenAI speech-to-text.
- Burn selected subtitle file into video.
- Optionally merge generated TTS audio in future phases.
- Capture command output, exit code, duration, and safe failure reason.

FFmpeg commands should be assembled from trusted templates, not raw user input.

### OpenAI

Responsibilities:

- Provide speech-to-text calls.
- Provide translation and subtitle polishing calls.
- Provide subtitle quality evaluation calls.
- Provide text-to-speech calls.
- Normalize provider errors.
- Return usage metadata when available.
- Avoid leaking provider-specific response details across the whole codebase.

Default provider is OpenAI. The code can still keep narrow client interfaces so tests and future provider changes are manageable.

### Prompt

Responsibilities:

- Store prompt templates and versions for translation, subtitle polishing, and quality evaluation.
- Resolve the active prompt version for each operation.
- Render prompt inputs from structured job, transcript, and subtitle data.
- Record prompt version on each model-call audit entry.

Prompt templates should not be scattered across service methods.

### Evaluation

Responsibilities:

- Run optional LLM-backed translation quality checks after subtitle translation.
- Score subtitle quality with structured criteria such as completeness, timing preservation, readability, and naturalness.
- Record detected issues and suggested fixes.
- Keep evaluation failures non-blocking by default unless a stricter profile is enabled.

### Subtitle

Responsibilities:

- Store transcript segments.
- Store translated subtitle segments.
- Preserve timing and ordering.
- Export SRT and VTT formats.
- Support subtitle preview APIs.

Subtitle generation must be deterministic after transcript and translation data are stored.

### Artifact

Responsibilities:

- Track generated files.
- Record type, object key, size, checksum when available, and generation stage.
- Expose preview or download metadata.
- Keep artifact ownership scoped to the job and user.

### Usage

Responsibilities:

- Record OpenAI call usage.
- Estimate per-job cost.
- Track processing duration.
- Expose cost summary in job detail.
- Enforce configured per-job or per-user cost budgets before expensive stages.

Provider pricing should be configuration data, not hard-coded business truth.

### Cache

Responsibilities:

- Store content hashes for source audio and translation inputs.
- Reuse safe prior transcription results when the same audio hash, speech model, and relevant options match.
- Reuse safe prior translation results when source text hash, target language, provider, model, and prompt version match.
- Reuse safe prior TTS results when target subtitle text hash, language, provider, model, and voice match.
- Avoid using stale cache entries across incompatible prompt or model versions.

The current implementation reuses generated media artifacts, translation provider results, and TTS provider results. Transcription, quality evaluation, and generic prompt-response caches remain follow-up optimizations.

## Data Model

The first durable model should include these tables.

### User

Represents a local or authenticated user.

Important fields:

- `id`
- `email`
- `displayName`
- `createdAt`

### Video

Represents an uploaded source video.

Important fields:

- `id`
- `ownerId`
- `originalFilename`
- `contentType`
- `durationSeconds`
- `fileSizeBytes`
- `sourceObjectKey`
- `status`
- `createdAt`

### LocalizationJob

Represents one processing attempt for a video.

Important fields:

- `id`
- `videoId`
- `ownerId`
- `sourceLanguage`
- `targetLanguages`
- `status`
- `currentStage`
- `retryCount`
- `maxRetries`
- `failureReason`
- `createdAt`
- `startedAt`
- `finishedAt`

### TranscriptSegment

Represents one timestamped source transcript segment.

Important fields:

- `id`
- `jobId`
- `segmentIndex`
- `startMs`
- `endMs`
- `sourceText`
- `detectedLanguage`

### SubtitleSegment

Represents translated or polished text for a segment.

Important fields:

- `id`
- `jobId`
- `transcriptSegmentId`
- `language`
- `text`
- `startMs`
- `endMs`

### Artifact

Represents generated files.

Important fields:

- `id`
- `jobId`
- `type`
- `language`
- `objectKey`
- `filename`
- `contentType`
- `sizeBytes`
- `createdAt`

Artifact types:

```text
SOURCE_VIDEO
EXTRACTED_AUDIO
SUBTITLE_SRT
SUBTITLE_VTT
TTS_AUDIO
SUBTITLE_BURNED_VIDEO
```

### UsageRecord

Represents one provider or processing usage record.

Important fields:

- `id`
- `jobId`
- `provider`
- `operation`
- `model`
- `inputTokens`
- `outputTokens`
- `audioSeconds`
- `characters`
- `estimatedCostUsd`
- `durationMs`
- `createdAt`

### PromptTemplate

Represents a versioned prompt used by an AI stage.

Important fields:

- `id`
- `taskType`
- `name`
- `version`
- `template`
- `active`
- `createdAt`

Task types:

```text
SUBTITLE_TRANSLATION
SUBTITLE_POLISHING
TRANSLATION_QUALITY_EVALUATION
```

### ModelCallRecord

Represents an audited model invocation.

Important fields:

- `id`
- `jobId`
- `stage`
- `operation`
- `provider`
- `model`
- `promptTemplateId`
- `promptVersion`
- `inputSummary`
- `outputSummary`
- `inputTokens`
- `outputTokens`
- `audioSeconds`
- `characters`
- `estimatedCostUsd`
- `latencyMs`
- `status`
- `errorSummary`
- `createdAt`

### QualityEvaluation

Represents an optional LLM-based quality check for translated subtitles.

Important fields:

- `id`
- `jobId`
- `language`
- `score`
- `completenessScore`
- `readabilityScore`
- `timingPreservationScore`
- `naturalnessScore`
- `issues`
- `suggestedFixes`
- `modelCallRecordId`
- `createdAt`

### TranslationCacheEntry

Represents a reusable translated-subtitle provider result.

Important fields:

- `id`
- `cacheKey`
- `sourceHash`
- `targetLanguage`
- `provider`
- `model`
- `promptVersion`
- `responseJson`

### TtsCacheEntry

Represents a reusable TTS provider audio result.

Important fields:

- `id`
- `cacheKey`
- `textHash`
- `language`
- `provider`
- `model`
- `voice`
- `responseJson`
- `sourceJobId`
- `createdAt`
- `sourceJobId`
- `createdAt`

### JobEvent

Represents a timeline event.

Important fields:

- `id`
- `jobId`
- `stage`
- `status`
- `message`
- `createdAt`

## Queue Model

RabbitMQ should carry lightweight job messages:

```json
{
  "jobId": "123",
  "attempt": 1,
  "requestedAt": "2026-06-22T10:00:00Z"
}
```

The database remains the source of truth for job state. Queue messages should not contain raw user files, transcripts, API keys, or large payloads.

## API Shape

Initial API endpoints:

```text
POST   /api/videos
GET    /api/videos
GET    /api/jobs
GET    /api/jobs/{jobId}
POST   /api/jobs/{jobId}/retry
POST   /api/jobs/{jobId}/cancel
GET    /api/jobs/{jobId}/transcript
GET    /api/jobs/{jobId}/subtitles
GET    /api/jobs/{jobId}/artifacts
GET    /api/jobs/{jobId}/usage
GET    /api/jobs/{jobId}/model-calls
GET    /api/jobs/{jobId}/quality-evaluations
GET    /api/artifacts/{artifactId}/download
GET    /actuator/health
```

The first implementation may use a smaller subset, but it should keep these boundaries in mind.

## Error Handling

Errors should be normalized into stable categories:

```text
VALIDATION_ERROR
UNSUPPORTED_MEDIA
STORAGE_ERROR
QUEUE_ERROR
FFMPEG_ERROR
OPENAI_SPEECH_ERROR
OPENAI_LANGUAGE_ERROR
OPENAI_TTS_ERROR
SUBTITLE_EXPORT_ERROR
ARTIFACT_ERROR
INTERNAL_ERROR
```

User-facing errors should be clear enough to retry or change the input. Internal logs can include more detail but must not include secrets.

## Testing Strategy

Minimum tests by phase:

- Upload validation tests.
- Job status transition tests.
- Subtitle SRT and VTT export tests.
- Retry decision tests.
- Cost calculation tests.
- Prompt template rendering tests.
- Model-call audit record tests.
- Translation quality evaluation parser tests.
- AI cache key compatibility tests.
- Storage key safety tests.
- FFmpeg command template tests.
- OpenAI client contract tests with mocked provider responses.
- Integration tests for MySQL, Redis, RabbitMQ, and MinIO once Testcontainers is added.

## Local Runtime

Target Docker Compose services:

```text
mysql
redis
rabbitmq
minio
linguaframe-backend
linguaframe-frontend
```

FFmpeg should be available inside the backend or worker container.
