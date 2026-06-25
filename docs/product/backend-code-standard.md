# Backend Code Standard

This document defines the backend code structure for LinguaFrame. New backend code should follow this standard first, and deviations should be recorded in `docs/progress/decisions.md` or the relevant execution plan.

## Goals

- Keep controllers thin and focused on HTTP concerns.
- Keep business state transitions inside services.
- Use explicit domain objects for API input, API output, internal business data, queries, and persistence.
- Keep OpenAI, FFmpeg, object storage, and queue operations behind provider or service boundaries.
- Keep prompt templates, model-call tracing, and quality evaluation behind explicit AI infrastructure modules.
- Make media processing stages auditable.
- Avoid demo-script style code in production paths.

## Target Module Layout

Backend code is package-by-domain first. Inside each domain, use layered packages only when needed:

```text
com.linguaframe.<module>
  controller
    XxxController.java
  service
    XxxService.java
    impl
      XxxServiceImpl.java
  mapper
    XxxMapper.java
  repository
    XxxRepository.java
  domain
    entity
      XxxEntity.java
    dto
      XxxDto.java
    vo
      XxxVo.java
    bo
      XxxBo.java
    query
      XxxQuery.java
    enums
      XxxStatus.java
  convert
    XxxConvert.java
  client
    XxxClient.java
    impl
      XxxClientImpl.java
  config
    XxxProperties.java
```

Small modules should create only the packages they need.

## Layer Responsibilities

### Controller

Controllers own HTTP concerns only:

- Route mapping.
- Request body, path variable, query parameter, header, and multipart extraction.
- Bean Validation entry points such as `@Valid`.
- Calling service interfaces.
- Returning `Vo` or response wrapper objects.

Controllers must not:

- Call mappers or repositories directly.
- Execute FFmpeg commands.
- Call OpenAI APIs.
- Read or write object storage directly.
- Publish queue messages directly unless through a job service.
- Contain business state transitions.

### Service Interface

`service/XxxService.java` defines the business contract used by controllers and other modules.

Rules:

- Controllers depend on service interfaces, not implementation classes.
- Service methods should describe business capabilities instead of raw CRUD operations.
- Cross-module behavior should be exposed through narrow service methods.

### Service Implementation

`service/impl/XxxServiceImpl.java` contains business orchestration.

Rules:

- Annotate implementations with `@Service`.
- Use constructor injection with `private final` dependencies.
- Own transactions for operations that update durable state.
- Convert `Dto` input into `Bo` or persistence objects before writes.
- Return `Vo` or command results, not entities.

### Mapper Or Repository

If MyBatis-Plus is adopted, mapper interfaces should use `BaseMapper<XxxEntity>` and be annotated with `@Mapper`.

If Spring Data JPA is adopted, repository interfaces should extend the appropriate Spring Data repository type.

Rules:

- Persistence interfaces must not be called from controllers.
- Cross-module persistence access is forbidden.
- Custom SQL should live in mapper methods or XML only when it is clearer than wrappers or derived queries.
- The chosen persistence style should be recorded in `docs/progress/decisions.md`.

### Client / Provider

Client packages contain external integration contracts and implementations.

Examples:

```text
openai/speech/OpenAiSpeechClient.java
openai/language/OpenAiLanguageClient.java
openai/evaluation/OpenAiQualityEvaluationClient.java
openai/tts/OpenAiTtsClient.java
storage/client/ObjectStorageClient.java
```

Rules:

- Services depend on client interfaces.
- Client implementations hide vendor-specific request and response details.
- Provider exceptions should be normalized before crossing into service code.
- API keys and credentials must come from configuration and never from request bodies.

### Prompt And Model-Call Infrastructure

Prompt and model-call code should be separated from media business logic.

Rules:

- Prompt templates live behind a prompt service, not inside worker stage methods.
- Every LLM-backed language or evaluation call records the prompt version.
- Model-call audit records store summaries and usage metadata, not raw secrets or excessive transcript content.
- Cost calculation uses configuration, not hard-coded pricing assumptions inside clients.
- Quality evaluation output should be parsed into structured fields before persistence.

### Worker Pipeline

Worker classes organize long-running processing.

Rules:

- The worker consumes lightweight job messages.
- Job state is loaded from MySQL.
- Each stage records timeline events and status transitions.
- Failures are mapped to stable failure categories.
- The worker should not expose provider-specific responses to controllers.

### FFmpeg

FFmpeg integration must be controlled.

Rules:

- Build commands from trusted templates.
- Use internally generated file paths.
- Validate file locations before command execution.
- Capture exit code, stderr summary, and duration.
- Enforce timeouts.
- Do not execute user-provided shell fragments.

## Domain Objects

Use domain subpackages to make object intent explicit:

| Package | Suffix | Purpose |
| --- | --- | --- |
| `domain.entity` | `*Entity.java` | Database table mapping object. |
| `domain.dto` | `*Dto.java` | API request or external transfer object. |
| `domain.vo` | `*Vo.java` | API response object. |
| `domain.bo` | `*Bo.java` | Internal business object used inside service workflows. |
| `domain.query` | `*Query.java` | Query, filter, and pagination input object. |
| `domain.enums` | descriptive enum name | Business enum owned by the module. |

Avoid vague names such as `Request`, `Response`, `Model`, or `Info`.

Preferred examples:

```text
CreateLocalizationJobDto
LocalizationJobDetailVo
LocalizationJobExecutionBo
LocalizationJobQuery
LocalizationJobStatus
TranscriptSegmentEntity
GeneratedArtifactEntity
UsageRecordEntity
```

## Naming Rules

- Use `LinguaFrame` only for application-level names.
- Use domain-specific names for classes.
- Use `*Service` for business contracts.
- Use `*ServiceImpl` for concrete service classes.
- Use `*Client` for external API contracts.
- Use `*Adapter` when an implementation adapts a storage or provider API.
- Use `*Worker` for queue consumers.
- Use `*Stage` or `*Processor` for pipeline steps.
- Use `*Properties` for configuration classes.

## Configuration Rules

All LinguaFrame configuration uses the `linguaframe` prefix:

```yaml
linguaframe:
  storage:
    endpoint:
    bucket:
    access-key:
    secret-key:
  openai:
    api-key:
    speech-model:
    language-model:
    tts-model:
  media:
    max-file-size-mb: 100
    max-duration-seconds: 120
  worker:
    max-retries: 2
    stage-timeout-seconds: 600
  cost:
    enabled: true
```

Secrets must come from environment-specific configuration and must not be committed.

## Logging Rules

Logs should include:

- `jobId`
- `videoId`
- user id when available
- pipeline stage
- status transition
- provider operation
- duration
- artifact id when relevant

Logs must not include:

- Raw OpenAI API keys.
- Object storage credentials.
- Full signed URLs with secrets.
- Raw uploaded file contents.
- Excessively long transcript text.

## Testing Rules

Required test categories:

- Unit tests for validation and status transition logic.
- Unit tests for SRT and VTT export formatting.
- Unit tests for cost estimation.
- Unit tests for prompt template selection and rendering.
- Unit tests for model-call audit persistence decisions.
- Unit tests for translation quality evaluation parsing.
- Unit tests for AI cache key generation.
- Unit tests for retry decision logic.
- Tests for safe object keys and path guards.
- Mocked OpenAI client tests.
- Integration tests for MySQL, Redis, RabbitMQ, and MinIO when Testcontainers is introduced.

## API Response Rules

API responses should be stable and frontend-friendly:

- Use explicit response objects.
- Include ids and status values.
- Avoid returning persistence entities directly.
- Use predictable error codes.
- Include safe failure messages.
- Do not expose internal stack traces.
