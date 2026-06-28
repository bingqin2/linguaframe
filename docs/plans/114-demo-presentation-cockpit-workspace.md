# Demo Presentation Cockpit Workspace Implementation Plan

**Goal:** Add one operator-facing presentation cockpit that turns the existing demo readiness, sample, launcher, live checks, run archive, evidence gallery, run monitor, and acceptance gate surfaces into one clear run-day command center.

**Architecture:** Add a read-only backend operator aggregate that composes existing safe metadata without starting Docker, uploading media, calling OpenAI, creating artifacts, or downloading media bytes. Render the same aggregate in the React sidebar/main workspace and expose a terminal export script so local and private demos have one canonical "what should I do now?" surface before, during, and after a demo run.

## Scope

- Add `GET /api/operator/demo-presentation-cockpit`.
- Include overall cockpit status, phase, recommended next action, preflight checks, sample/launcher summary, runtime/live dependency status, private-demo operations status, active run status, recommended completed run, acceptance gate status, and safe links.
- Add a React `Demo cockpit` panel that works even when no job is selected.
- Add selected-job enrichment when a job is open: run monitor, acceptance gate, completion certificate, replay card, snapshot, presenter pack, and share sheet links.
- Add `scripts/demo/demo-presentation-cockpit.sh`.
- Extend `scripts/demo/start-local-demo.sh` or the full Tears script only if it can print the cockpit output without changing runtime behavior.
- Update README, Docker E2E guide, target-state/roadmap, decisions, execution log, and this plan.

## Constraints

- This is one complete feature slice: backend API, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- The cockpit is read-only. It must not enqueue jobs, retry/cancel jobs, publish subtitles, run cleanup, start Docker, run backups, upload files, download media bytes, or call providers.
- Do not expose API keys, bearer tokens, demo tokens, object keys, local filesystem paths, provider payloads, raw transcript text, raw subtitle text, corrected subtitle text, uploaded media bytes, or generated media bytes.
- Reuse existing safe services and links. Do not duplicate package generation or introduce a new persisted cockpit table.
- Keep the output actionable: each status should include a short next action, not only a label.

## Acceptance Criteria

- With no jobs selected, the cockpit can tell the operator whether the environment is ready to start a demo and which command/panel to use next.
- With active jobs, the cockpit highlights queued/processing runs, current stage, elapsed time, attention level, and monitor link.
- With completed jobs, the cockpit selects the recommended run from existing private-demo archive/gallery evidence and shows acceptance readiness plus key package links.
- If the recommended or selected run is not presentation-ready, the cockpit explains whether the blocker is pre-upload readiness, live dependency, quota, missing output, failed quality evaluation, missing acceptance gate evidence, or incomplete job state.
- Browser, backend JSON, and terminal summary all stay metadata-only and safe-link-only.
- The feature is verified with backend, frontend, shell, OpenAPI/runtime contract, build, and full-suite tests.

## Implementation Tasks

### 1. Backend Cockpit Aggregate

- Create VO records such as `DemoPresentationCockpitVo`, `DemoPresentationCockpitSectionVo`, `DemoPresentationCockpitCheckVo`, `DemoPresentationCockpitRunVo`, and `DemoPresentationCockpitLinkVo`.
- Add `DemoPresentationCockpitService` and implementation under the operator/service boundary.
- Compose from existing safe services:
  - runtime dependency and live-check services;
  - demo sample media catalog;
  - demo run launcher;
  - upload readiness and owner quota preflight;
  - private demo operations, launch rehearsal, evidence gallery, and run archive;
  - job query/list APIs for active run counts;
  - demo run monitor and acceptance gate for the recommended or selected run when available.
- Add `GET /api/operator/demo-presentation-cockpit` to the operator controller.
- Support an optional `jobId` query parameter for selected-job enrichment.
- Add service/controller/OpenAPI tests for:
  - ready environment with no active job;
  - active processing job;
  - completed recommended job with `READY` acceptance gate;
  - blocked environment or blocked acceptance gate;
  - no secret/path/raw-text leakage.

### 2. Frontend Cockpit Panel

- Add TypeScript types and `linguaFrameApi.getDemoPresentationCockpit(jobId?)`.
- Load the cockpit on startup, refresh, selected-job changes, and terminal SSE transitions.
- Render a high-level `Demo cockpit` panel near the existing runbook/readiness area.
- Show:
  - overall status and phase;
  - primary next action;
  - preflight/runtime/live dependency/owner quota summary;
  - active run summary;
  - recommended completed run and acceptance status;
  - safe links to launcher, upload readiness, monitor, acceptance gate, run package, snapshot, presenter pack, and archive surfaces.
- Add focused Vitest coverage for no-job, active-job, completed-ready, blocked, refresh, selected-job enrichment, link output, and API call shape.

### 3. Terminal Export

- Add shared helpers:
  - `download_demo_presentation_cockpit_json`;
  - `print_demo_presentation_cockpit_summary_file`.
- Add `scripts/demo/demo-presentation-cockpit.sh` with optional `LINGUAFRAME_DEMO_JOB_ID`.
- Print stable metadata-only lines: `demoCockpitStatus`, `demoCockpitPhase`, `demoCockpitNextAction`, `demoCockpitCheck`, `demoCockpitRun`, `demoCockpitLink`, and `demoCockpitSafetyNote`.
- Add shell tests for route encoding, optional selected job query, summary output, script execution, and unsafe marker redaction.

### 4. Documentation And Progress

- Document the cockpit as the first run-day surface: use it before upload, during processing, and after completion.
- Clarify how it differs from upload readiness, run launcher, run monitor, acceptance gate, evidence gallery, and run archive.
- Update README, `docs/agent/docker-e2e-demo.md`, `docs/product/target-state.md`, `docs/product/roadmap.md`, `docs/progress/decisions.md`, and `docs/progress/execution-log.md`.
- Mark this plan as executed during implementation.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=DemoPresentationCockpitServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `npm --prefix frontend test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash -n scripts/demo/demo-presentation-cockpit.sh scripts/demo/lib/linguaframe-demo.sh`
- `scripts/demo/test-linguaframe-demo-client.sh`
- `npm --prefix frontend run build`
- `git diff --check`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- Post-merge focused backend/frontend/script validation on `main`.

## Completion Criteria

- A run-day operator has one browser/backend/terminal cockpit to decide the next action before, during, and after a demo.
- The cockpit advances the demo goal by reducing panel-hopping and making readiness blockers explicit.
- The feature branch is verified, committed, merged back to `main`, and post-merge validation is recorded.

## Execution Status

- Backend cockpit aggregate, controller route, service tests, controller tests, and OpenAPI/runtime focused validation are implemented.
- Frontend types, API client, startup/selected-job refresh behavior, cockpit panel, and focused Vitest coverage are implemented.
- Terminal cockpit script and shared metadata-only helper tests are implemented.
- README, Docker E2E, private-demo deployment, target-state, decisions, and execution log have been updated.
- Full-suite validation passed: focused backend, focused frontend, script syntax/client tests, frontend build, `git diff --check`, full Maven tests, and full Vitest.
- Commit, merge to `main`, and post-merge validation remain before completion.
