# Browser Upload Preflight MVP

**Goal:** Validate selected videos from the React demo before creating a localization job, so users see file type, size, duration, and FFprobe-related issues before any storage write, queue dispatch, worker execution, or provider-backed work.

**Architecture:** Reuse the existing backend `POST /api/media/uploads/validate` endpoint. Add a typed frontend API client, upload-form state, an explicit validation result panel, and upload gating that runs validation before `POST /api/media/uploads`. Keep validation advisory but enforce backend invalid responses by blocking the upload and showing the backend message.

## Scope

- Add a frontend `MediaUploadValidation` type matching `MediaUploadValidationVo`.
- Add `linguaFrameApi.validateUpload(file)` using multipart form data and the existing demo-token header behavior.
- Update the React upload form to support:
  - validating the selected file before upload
  - showing filename, content type, file size, duration, max size, max duration, validation code, and validation message
  - blocking upload when validation returns invalid
  - continuing upload when validation returns `READY`
  - showing validation errors without clearing job history or selected job state
- Make the normal `Upload` button run validation first, then upload only if validation passes.
- Add an optional `Validate file` button for users who want to inspect a file before uploading.
- Keep the backend unchanged unless tests reveal the existing response contract is insufficient.
- Update README, Docker E2E guide, smoke-test checklist, and execution log.

## Non-Goals

- Do not bypass backend upload validation; upload must still be validated server-side.
- Do not implement client-side video duration probing.
- Do not store selected local file paths, raw media bytes, or validation payloads in local storage.
- Do not call OpenAI, run FFmpeg worker stages, or create jobs during validation.
- Do not add drag-and-drop or resumable upload in this slice.

## Implementation Steps

1. **Frontend API contract**
   - Add `MediaUploadValidation` and `MediaUploadValidationCode` to `frontend/src/domain/jobTypes.ts`.
   - Add `validateUpload(file)` to `frontend/src/api/linguaframeApi.ts`.
   - Add API tests for multipart body, endpoint path, demo token header, and JSON result parsing.

2. **React upload preflight UI**
   - Add validation state to `App.tsx`.
   - Add a `Validate file` button next to upload controls.
   - Render a compact validation result panel inside the upload form.
   - Run `validateUpload` inside `handleUpload` before `uploadMedia`.
   - If validation is invalid or throws, show the validation message and do not call `uploadMedia`.
   - If validation is ready, proceed with the existing upload, recent-job persistence, job load, preview load, and history refresh.

3. **Frontend tests**
   - Extend `frontend/src/App.test.tsx` to assert:
     - manual validation renders `READY` metadata
     - upload calls validation before upload
     - invalid validation blocks upload and shows backend message
     - validation request failures keep upload controls usable
   - Keep existing upload and runbook tests passing.

4. **Documentation**
   - Update README upload section with browser preflight behavior.
   - Update `docs/agent/docker-e2e-demo.md` with the browser validation expectation.
   - Update `docs/agent/smoke-test-checklist.md` with preflight panel checks.
   - Record red/green validation and final verification in `docs/progress/execution-log.md`.

5. **Validation**
   - Run `cd frontend && npm run test:run -- linguaframeApi App`.
   - Run `cd frontend && npm run build`.
   - Run `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - Run `git diff --check`.

## Acceptance Criteria

- A selected file can be validated from the browser before upload.
- Upload runs backend validation first and only creates a job after a `READY` validation response.
- Invalid files show the backend validation code/message and do not trigger `POST /api/media/uploads`.
- The UI does not expose local file paths, raw media bytes, secrets, or provider credentials.
- Existing upload, job detail, runbook, readiness, and operator panels continue to work.
- The feature is verified, committed on a feature branch, and merged back to `main`.
