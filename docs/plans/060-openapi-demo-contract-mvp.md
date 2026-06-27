# OpenAPI Demo Contract MVP

**Goal:** Make the demo backend API contract explicit, grouped, and test-covered so reviewers can inspect the upload-to-result workflow from Swagger or `/v3/api-docs`.

**Architecture:** Keep endpoint behavior unchanged. Extend the existing Springdoc setup with demo-token security metadata, controller tags, operation summaries, response descriptions, and a contract test that locks the primary demo paths into the generated OpenAPI document.

## Scope

- Add OpenAPI metadata for the private demo header:
  - header name: `X-LinguaFrame-Demo-Token`
  - scheme type: API key in request header
  - description: required only when `linguaframe.demo.access-token` is configured
- Add `@Tag`, `@Operation`, `@ApiResponses`, and useful `@Parameter` annotations to primary controllers:
  - `MediaUploadController`
  - `LocalizationJobController`
  - `RuntimeDependencyController`
  - `PromptTemplateController`
  - `OperatorDashboardController`
  - `RetentionCleanupController`
- Expand OpenAPI tests to verify:
  - API title/version/description
  - demo-token security scheme
  - primary tags
  - upload, job, event stream, artifact, diagnostics, transcript, subtitle, runtime, prompt-template, operator, and cleanup paths
- Update README and demo docs with OpenAPI/Swagger validation steps.
- Update roadmap and execution log after validation.

## Non-Goals

- Do not change endpoint URLs, request/response payloads, status codes, or business logic.
- Do not introduce JWT, user accounts, OAuth, generated clients, or public API versioning.
- Do not redesign DTO/VO schemas in this slice.
- Do not protect `/v3/api-docs` or Swagger UI behind the demo token.

## Implementation Steps

1. **OpenAPI security metadata**
   - Update `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java`.
   - Add a reusable API key security scheme named `DemoAccessToken`.
   - Keep `info.title = LinguaFrame API` and `info.version = 0.0.1`.

2. **Controller contract annotations**
   - Annotate each primary controller with a stable tag name:
     - `Media Uploads`
     - `Localization Jobs`
     - `Runtime Dependencies`
     - `Prompt Templates`
     - `Operator Dashboard`
     - `Retention Cleanup`
   - Add operation summaries that describe reviewer-facing actions, for example `Upload media and create a localization job`.
   - Add parameter descriptions for IDs, language, pagination, status filters, multipart file, target language, and TTS voice.
   - Add concise response descriptions for success, bad request, unauthorized demo access, not found, and conflict-style job state errors where applicable.

3. **OpenAPI contract tests**
   - Expand `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`.
   - Assert the generated document includes the demo-token scheme under `components.securitySchemes`.
   - Assert the expected tag names exist.
   - Assert these paths are present:
     - `/api/media/uploads/validate`
     - `/api/media/uploads`
     - `/api/media/uploads/{videoId}`
     - `/api/jobs`
     - `/api/jobs/{jobId}`
     - `/api/jobs/{jobId}/events`
     - `/api/jobs/{jobId}/retry`
     - `/api/jobs/{jobId}/cancel`
     - `/api/jobs/{jobId}/artifacts`
     - `/api/jobs/{jobId}/diagnostics/download`
     - `/api/jobs/{jobId}/transcript`
     - `/api/jobs/{jobId}/subtitles/{language}`
     - `/api/jobs/{jobId}/artifacts/{artifactId}/download`
     - `/api/jobs/{jobId}/artifacts/archive/download`
     - `/api/runtime/dependencies`
     - `/api/prompt-templates`
     - `/api/operator/dashboard`
     - `/api/retention/cleanup/preview`
     - `/api/retention/cleanup/run`

4. **Documentation**
   - Update README OpenAPI section with:
     - `/v3/api-docs`
     - `/swagger-ui/index.html`
     - how to use `X-LinguaFrame-Demo-Token` in Swagger when private demo access is enabled
   - Update `docs/agent/docker-e2e-demo.md` with a Swagger-based demo inspection step.
   - Update `docs/agent/smoke-test-checklist.md` with OpenAPI contract checks.
   - Mark roadmap OpenAPI docs as implemented.
   - Record red/green validation in `docs/progress/execution-log.md`.

5. **Validation**
   - Run `mvn -pl LinguaFrame -Dtest=OpenApiDocumentationTests test`.
   - Run `mvn -pl LinguaFrame test`.
   - Run `cd frontend && npm run test:run -- App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh scripts/demo/docker-e2e-success.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- `/v3/api-docs` exposes stable metadata, demo-token security documentation, primary tags, and the complete demo workflow path set.
- Swagger UI can be used to inspect the upload, job progress, artifacts, diagnostics, transcript, subtitle, runtime, prompt-template, operator, and cleanup APIs.
- Private demo token behavior is documented without changing runtime access rules.
- Existing browser demo, upload flow, job detail, result delivery, evidence export, and Docker demo scripts remain valid.
- The feature is verified, committed on a feature branch, and merged back to `main`.
