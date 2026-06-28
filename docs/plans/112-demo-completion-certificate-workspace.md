# Demo Completion Certificate Workspace Implementation Plan

**Goal:** Build a read-only demo completion certificate for a selected job so a presenter can prove one run is complete, reproducible, handoff-ready, and backed by safe evidence without opening every existing panel.

**Architecture:** Add a backend aggregate under the job API that reuses job detail, delivery manifest, demo handoff/readiness facts, replay card, share sheet, snapshot, presenter pack, run matrix, diagnostics, and package routes. Render the same aggregate in the selected-job frontend surface and expose it through a terminal script plus the full Tears demo output directory.

## Scope

- Add `GET /api/jobs/{jobId}/demo-completion-certificate`.
- Include overall certificate status, blocking checks, proof sections, selected job identity, profile/settings summary, readiness evidence, reproducibility evidence, package links, and safe next actions.
- Add a React `Demo completion certificate` panel near replay card and presenter pack.
- Add `scripts/demo/demo-completion-certificate.sh`.
- Extend `scripts/demo/docker-e2e-tears-of-steel-full.sh` to export `demo-completion-certificate.json`.
- Update README, Docker E2E guide, target-state or roadmap if needed, decisions, execution log, and this plan.

## Constraints

- This feature is one complete slice: backend API, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- The certificate is read-only. It must not enqueue jobs, retry jobs, publish subtitles, mutate drafts, create artifacts, call providers, upload files, or start Docker.
- Do not expose API keys, demo tokens, bearer tokens, object keys, local filesystem paths, provider payloads, raw transcript text, raw subtitle text, corrected subtitle text, uploaded media bytes, or generated media bytes.
- Reuse existing evidence/package/download routes; do not duplicate ZIP package generation in this slice.
- Use `READY` only when the selected job is completed and the key evidence routes are present; use `NEEDS_ATTENTION` or `BLOCKED` for incomplete or unsafe states.

## Implementation Tasks

### 1. Backend Certificate Aggregate

- Create VO records for `DemoCompletionCertificateVo`, proof sections, checks, links, and recommended actions.
- Add `DemoCompletionCertificateService` and `DemoCompletionCertificateServiceImpl`.
- Add `GET /api/jobs/{jobId}/demo-completion-certificate` to `LocalizationJobController`.
- Compose from existing safe services:
  - `LocalizationJobQueryService#getJob`
  - `DeliveryManifestService#buildManifest`
  - `DemoPresenterPackService#buildPresenterPack`
  - `DemoReplayCardService#buildReplayCard`
  - `DemoShareSheetService#buildShareSheet`
  - `DemoRunSnapshotService#buildSnapshot`
  - `DemoRunMatrixService#buildMatrix`
- Add service and controller tests covering:
  - completed ready job;
  - completed job missing handoff readiness;
  - failed/in-progress job;
  - no secret/path/raw-text leakage;
  - OpenAPI route coverage.

### 2. Frontend Certificate Panel

- Add TypeScript types and `linguaFrameApi.getDemoCompletionCertificate(jobId)`.
- Add selected-job state, loading, refresh, and reset behavior.
- Render certificate status, proof checks, proof sections, package links, recommended actions, and safety notes.
- Add command/link copy controls only where the payload contains safe text.
- Add focused Vitest coverage for ready rendering, blocked rendering, refresh, link output, and API call shape.

### 3. Terminal And Full-Demo Export

- Add shared shell helper `download_demo_completion_certificate_json`.
- Add `print_demo_completion_certificate_summary_file` with metadata-only key/value output.
- Add `scripts/demo/demo-completion-certificate.sh` using `LINGUAFRAME_DEMO_JOB_ID`.
- Extend `scripts/demo/docker-e2e-tears-of-steel-full.sh` to download and summarize `demo-completion-certificate.json`.
- Add shell tests for encoded route, summary output, missing job id, and unsafe marker redaction.

### 4. Documentation And Progress

- Document when to use the certificate versus replay card, presenter pack, snapshot, share sheet, and run archive.
- Update `README.md` and `docs/agent/docker-e2e-demo.md`.
- Record the decision to keep certificates generated on demand from existing evidence.
- Record validation in `docs/progress/execution-log.md`.
- Mark task checkboxes in this plan as complete during implementation.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=DemoCompletionCertificateServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `npm --prefix frontend test -- --run App.test.tsx`
- `bash -n scripts/demo/demo-completion-certificate.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `scripts/demo/test-linguaframe-demo-client.sh`
- `npm --prefix frontend run build`
- `git diff --check`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- Post-merge focused backend/frontend/script validation on `main`.

## Completion Criteria

- One completed selected job has a backend, browser, and terminal completion certificate.
- The certificate explains completion, handoff readiness, reproducibility, evidence packages, and next action using safe metadata only.
- Full Tears demo exports the certificate JSON automatically.
- The verified feature branch is merged back to `main`, and post-merge validation is recorded.
