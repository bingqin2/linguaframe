# Demo Acceptance Gate Workspace Implementation Plan

**Goal:** Add one end-to-end demo acceptance gate that tells the owner whether a selected completed run is truly ready for presentation, with explicit pass/warn/fail criteria and safe evidence links.

**Architecture:** Add a read-only backend aggregate under the job API that composes existing job detail, source media metadata, artifacts, quality evaluation, delivery manifest, completion certificate, presenter pack, replay card, snapshot, and run matrix. Render the same gate in the selected-job browser view and expose terminal export plus full Tears demo output so the demo has one final go/no-go check after processing completes.

## Scope

- Add `GET /api/jobs/{jobId}/demo-acceptance-gate`.
- Include overall gate status, required checks, warning checks, scored evidence, selected media/output coverage, safe links, and recommended next action.
- Add a React `Demo acceptance gate` panel near the completion certificate.
- Add `scripts/demo/demo-acceptance-gate.sh`.
- Extend `scripts/demo/docker-e2e-tears-of-steel-full.sh` to export `demo-acceptance-gate.json`.
- Update README, Docker E2E guide, target-state/roadmap if needed, decisions, execution log, and this plan.

## Constraints

- This is one complete feature slice: backend API, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- The gate is read-only. It must not enqueue jobs, retry jobs, publish subtitles, mutate drafts, create artifacts, call providers, upload files, start Docker, or download media bytes.
- Do not expose API keys, demo tokens, bearer tokens, object keys, local filesystem paths, provider payloads, raw transcript text, raw subtitle text, corrected subtitle text, uploaded media bytes, or generated media bytes.
- Reuse existing safe evidence/package/download routes; do not generate a new ZIP package in this slice.
- Use `READY` only when required checks pass. Use `ATTENTION` for completed jobs with warnings and `BLOCKED` for incomplete jobs or missing required evidence.

## Acceptance Criteria

Required pass checks:

- Job status is `COMPLETED`.
- Source media metadata is available.
- At least one downloadable subtitle artifact exists.
- At least one playable or downloadable media output exists: `DUBBING_AUDIO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, or `REVIEWED_BURNED_VIDEO`.
- Quality evaluation exists and is not a failing verdict.
- Completion certificate status is `READY`.
- Demo run package, presenter pack, replay card, and snapshot links are available.

Warning checks:

- No reviewed subtitle handoff artifacts.
- No recommended same-source baseline.
- Quality score below a conservative demo threshold, for example `< 80`.
- Estimated cost is present but high relative to configured demo expectations.
- Provider cache evidence is absent for repeat-run demos.

## Implementation Tasks

### 1. Backend Acceptance Aggregate

- Create VO records for `DemoAcceptanceGateVo`, `DemoAcceptanceGateCheckVo`, `DemoAcceptanceGateEvidenceVo`, and `DemoAcceptanceGateLinkVo`.
- Add `DemoAcceptanceGateService` and `DemoAcceptanceGateServiceImpl`.
- Add `GET /api/jobs/{jobId}/demo-acceptance-gate` to `LocalizationJobController`.
- Compose from existing safe services:
  - `LocalizationJobQueryService#getJob`
  - `MediaUploadQueryService#getUpload`
  - `JobArtifactService#listArtifacts`
  - `DeliveryManifestService#buildManifest`
  - `DemoCompletionCertificateService#buildCertificate`
  - `DemoPresenterPackService#buildPresenterPack`
  - `DemoReplayCardService#buildReplayCard`
  - `DemoRunSnapshotService#buildSnapshot`
  - `DemoRunMatrixService#buildMatrix`
- Add service and controller tests covering:
  - fully ready completed job;
  - completed job with warnings;
  - incomplete job blocked;
  - missing media output blocked;
  - no secret/path/raw-text leakage;
  - OpenAPI route coverage.

### 2. Frontend Acceptance Panel

- Add TypeScript types and `linguaFrameApi.getDemoAcceptanceGate(jobId)`.
- Add selected-job state, loading, refresh, and reset behavior.
- Render overall status, required/warning checks, evidence metrics, key links, safety notes, and recommended next action.
- Add focused Vitest coverage for ready rendering, blocked rendering, warning checks, refresh, link output, and API call shape.

### 3. Terminal And Full-Demo Export

- Add shared shell helper `download_demo_acceptance_gate_json`.
- Add `print_demo_acceptance_gate_summary_file` with metadata-only key/value output.
- Add `scripts/demo/demo-acceptance-gate.sh` using `LINGUAFRAME_DEMO_JOB_ID`.
- Extend `scripts/demo/docker-e2e-tears-of-steel-full.sh` to download and summarize `demo-acceptance-gate.json`.
- Add shell tests for encoded route, summary output, missing job id, and unsafe marker redaction.

### 4. Documentation And Progress

- Document when to use the acceptance gate versus completion certificate, replay card, presenter pack, snapshot, run archive, and evidence gallery.
- Update `README.md` and `docs/agent/docker-e2e-demo.md`.
- Record the decision to keep demo acceptance generated on demand from existing evidence.
- Record validation in `docs/progress/execution-log.md`.
- Mark this plan as executed during implementation.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=DemoAcceptanceGateServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `npm --prefix frontend test -- --run App.test.tsx`
- `bash -n scripts/demo/demo-acceptance-gate.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `scripts/demo/test-linguaframe-demo-client.sh`
- `npm --prefix frontend run build`
- `git diff --check`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- Post-merge focused backend/frontend/script validation on `main`.

## Completion Criteria

- A selected job has a backend, browser, and terminal acceptance gate.
- The gate clearly says `READY`, `ATTENTION`, or `BLOCKED` with actionable reasons.
- The full Tears demo exports the gate JSON automatically.
- The feature branch is verified, committed, merged back to `main`, and post-merge validation is recorded.
