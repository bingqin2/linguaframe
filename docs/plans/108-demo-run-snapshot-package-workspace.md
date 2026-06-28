# Demo Run Snapshot Package Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a metadata-only static demo snapshot package so a selected run can be downloaded as an offline reviewer workspace with one HTML entry point, Markdown notes, JSON manifests, and safe links back to the live demo.

**Architecture:** Build on the existing job detail, diagnostics, evidence, delivery manifest, share sheet, presenter pack, and live run monitor services. The snapshot package is generated on demand, is not persisted as an artifact, and excludes media bytes, raw transcript/subtitle text, corrected draft text, object keys, local paths, provider payloads, credentials, demo tokens, and secrets.

**Tech Stack:** Spring Boot services/controllers, JUnit 5 tests, React + TypeScript + Vitest, Bash demo helpers, ZIP/HTML/Markdown exports, Markdown docs.

## Global Constraints

- This slice must be a complete feature: backend preview/download API, static HTML snapshot ZIP, browser panel, terminal export script, full-video demo integration, docs, validation, commit, and merge back to `main`.
- Keep existing demo run package, share sheet, presenter pack, monitor, evidence, diagnostics, and delivery package behavior backward compatible.
- Require the same owner-scoped access boundary as other selected-job evidence endpoints.
- Treat the snapshot as a reviewer workspace only. It must not retry, cancel, dispatch, upload, mutate subtitle drafts, write artifacts, or call providers.
- Snapshot contents must be deterministic enough for tests except generated timestamps, and all generated text must be metadata-only.

---

## Current Context

- `GET /api/jobs/{jobId}/demo-run-package/download` already returns a metadata-only ZIP with safe JSON/Markdown evidence, but it does not include a static HTML landing page or a browser/terminal workflow focused on offline review.
- The browser selected-job view now exposes share sheet and live monitor panels, so a reviewer snapshot can curate those existing surfaces instead of inventing another source of truth.
- Terminal full-video demos already export many individual files under `/tmp/linguaframe-demo/tears-of-steel-full/`; a snapshot script can make the final evidence folder easier to inspect and archive.

## Task 1: Backend Snapshot Preview And ZIP Contract

**Files:**
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredDemoRunSnapshotPackageBo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunSnapshotVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunSnapshotSectionVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoRunSnapshotLinkVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoRunSnapshotService.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunSnapshotServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Add/modify focused backend tests under `LinguaFrame/src/test/java/com/linguaframe/job/`

**Interfaces:**
- Add `GET /api/jobs/{jobId}/demo-run-snapshot`.
- Add `GET /api/jobs/{jobId}/demo-run-snapshot/download`.
- JSON preview returns job id, video id, target language, demo profile, generated time, readiness, headline, summary, section list, package entry list, safe live links, and exclusion policy.
- ZIP entries: `index.html`, `manifest.json`, `README.md`, `demo-share-sheet.md`, `demo-share-sheet.json`, `demo-run-monitor.md`, `demo-run-monitor.json`, `presenter-pack.json`, `delivery-manifest.md`, `diagnostics.json`, and `evidence.md`.
- `index.html` must be self-contained, readable without backend access, and contain only escaped metadata plus relative links to packaged files.

- [x] Write failing service tests for completed, failed, and in-progress jobs proving snapshot readiness, ZIP entries, HTML escaping, and unsafe marker exclusion.
- [x] Write failing controller/owner-access tests for JSON preview and ZIP download routes.
- [x] Implement BO/VO records, service composition, Markdown/HTML rendering, ZIP generation, and controller routes.
- [x] Run focused backend tests for snapshot service, controller contract, OpenAPI route coverage, and runtime route coverage.

## Task 2: Browser Snapshot Workspace Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify/add focused frontend API tests under `frontend/src/api/`

**Interfaces:**
- Add a selected-job `Demo snapshot` panel near share sheet, presenter pack, and run monitor.
- Show readiness, headline, summary, included package sections, exclusion policy, and live links.
- Provide explicit refresh and `Download static snapshot ZIP` actions.
- Refresh snapshot data when a job is opened, uploaded, retried, cancelled, manually refreshed, and when SSE updates the selected job.

- [x] Write failing Vitest coverage for snapshot rendering, included entries, exclusion copy, refresh behavior, and download link.
- [x] Implement frontend API types/client methods and selected-job panel.
- [x] Run focused frontend tests.

## Task 3: Terminal Snapshot Export Script

**Files:**
- Add: `scripts/demo/demo-run-snapshot.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-run-snapshot.sh` downloads preview JSON and ZIP to `/tmp/linguaframe-demo/demo-run-snapshot/`.
- The script prints a metadata-only summary: readiness, section count, ZIP path, and key entry names.
- The full Tears demo automatically exports `demo-run-snapshot.json` and `demo-run-snapshot.zip` after a completed job.
- The script fails clearly when job id is missing, backend access is denied, the ZIP is empty, or expected entries are missing.

- [x] Add failing shell helper tests for JSON download, ZIP download, summary printing, missing job id, and entry validation.
- [x] Implement shared helper functions and the snapshot script.
- [x] Integrate final snapshot export into the full Tears demo path without changing quick demo behavior.

## Task 4: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/108-demo-run-snapshot-package-workspace.md`

**Validation Commands:**
- `mvn -pl LinguaFrame -Dtest=DemoRunSnapshotServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,AuthenticatedOwnerJobAccessTests test`
- `cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/demo-run-snapshot.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `git diff --check`

- [x] Document when to use the snapshot package versus the existing demo run package, share sheet, evidence bundle, and reviewed handoff package.
- [x] Record validation evidence in the execution log.
- [ ] Commit the completed feature branch, merge back to `main`, run post-merge focused validation, and record the merge.

## Plan Self-Review

- Scope is one complete user-visible/operator-visible feature: a static reviewer snapshot package available from backend, browser, terminal, and full-video demo.
- The slice reuses existing safe evidence services and does not introduce provider calls, persistence, billing, public sharing, or multi-user behavior.
- No placeholder tasks remain; every task names concrete files, endpoints, expected package entries, and validation commands.
