# Private Demo Run Archive Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one operator-facing private demo archive workspace that captures the current demo readiness, completed-run gallery, recommended handoff candidate, and safe package routes for post-demo review.

**Architecture:** Add a read-only aggregate endpoint under the existing operator namespace instead of creating new persisted archive records. Compose the archive from existing private-demo operations readiness, launch rehearsal, evidence gallery, and safe package links, then expose the same metadata through React and terminal scripts. The archive is an index and report only; it does not upload media, call OpenAI, create backups, restore data, publish subtitles, clean storage, or copy artifact bytes.

**Tech Stack:** Spring Boot service/controller records, JUnit 5 tests, React + TypeScript + Vitest, Bash/Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be a complete feature: backend archive API, browser visibility, terminal report, docs, validation, commit, and merge back to `main`.
- Keep the archive read-only and derived from existing safe metadata.
- Do not persist a new archive table or ZIP in object storage.
- Do not expose RabbitMQ passwords, demo tokens, OpenAI keys, object keys, local paths, provider payloads, raw transcript text, subtitle text, corrected subtitle text, or media bytes.
- Prefer the evidence gallery's recommended completed job as the archive handoff candidate.
- Keep per-job `demo-run-package`, `handoff-package`, `ai-audit-package`, presenter pack, evidence bundle, and diagnostics routes as links rather than embedding package contents.

---

## Current Context

- Private demo operations readiness already aggregates runtime, live dependencies, provider, cost, storage/recovery, retention, and evidence checks.
- Private demo launch rehearsal already orders deploy, startup, preflight, OpenAI, backup/restore, smoke/full demo, and evidence steps.
- Private demo evidence gallery already lists completed jobs and safe package links.
- Demo presenter pack and demo run package already summarize one selected job.
- There is no single browser/terminal artifact that captures the full private-demo state after a run for later review.

## Task 1: Backend Private Demo Archive API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoRunArchiveVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoRunArchiveCandidateVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/PrivateDemoRunArchiveLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/PrivateDemoRunArchiveService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/PrivateDemoRunArchiveServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/operator/service/PrivateDemoRunArchiveServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Add `GET /api/operator/private-demo/run-archive`.
- Return:
  - `overallStatus`: `READY`, `ATTENTION`, or `BLOCKED`.
  - `generatedAt`: ISO instant.
  - `recommendedJobId`, `recommendedVideoId`, `recommendedProfileId`, `recommendedReadiness`.
  - `operationsOverallStatus` from private demo operations.
  - `launchOverallStatus` and `launchRecommendedNextStep`.
  - `galleryCompletedJobCount`, `galleryHandoffReadyCount`.
  - `candidates`: top completed gallery jobs with job id, profile, status, readiness, quality score, estimated cost, model call count, provider cache hits, handoff readiness, and roles.
  - `archiveLinks`: safe routes for operations report, launch rehearsal, evidence gallery, recommended presenter pack, demo run package, handoff package, AI audit package, evidence bundle, diagnostics, and result bundle.
  - `archiveNotesMarkdown`: metadata-only Markdown suitable for copying.
- Add the new route to the runtime required route contract.

- [ ] Write failing service tests for a ready archive with a recommended gallery job and safe links.
- [ ] Write failing service tests that unsafe strings in source service notes are not copied into archive notes.
- [ ] Implement VO records, service, controller route, and route contract update.
- [ ] Run `mvn -pl LinguaFrame -Dtest=PrivateDemoRunArchiveServiceTests,OperatorDashboardControllerTests,RuntimeDependencyControllerTests test`.

## Task 2: Browser Private Demo Archive Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Add `PrivateDemoRunArchive` TypeScript types matching Task 1.
- Add `getPrivateDemoRunArchive()` API helper for `/api/operator/private-demo/run-archive`.
- Add a sidebar panel `Private demo run archive`.
- Show overall status, generated time, recommended job/profile/readiness, operations status, launch status, gallery counts, candidate rows, and safe archive links.
- Provide `Copy archive notes` and `Download archive notes` actions using `archiveNotesMarkdown`.
- Keep the panel usable when loading fails; show a concise unavailable message without blocking upload or job detail.

- [ ] Write failing API test proving token-aware fetch and encoded route behavior where applicable.
- [ ] Write failing App tests proving the panel renders the recommended job, archive links, notes actions, and excludes unsafe text.
- [ ] Implement frontend types, API helper, panel, copy/download actions, loading, refresh, and error state.
- [ ] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts App.test.tsx -t "private demo run archive"`.

## Task 3: Terminal Archive Report

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/private-demo-run-archive.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/private-demo-launch-rehearsal.sh`
- Modify: `scripts/demo/private-demo-evidence-gallery.sh`

**Interfaces:**
- Add helper `download_private_demo_run_archive_json(base_url, output_path)`.
- Add helper `print_private_demo_run_archive_summary_file(path)` with lines:
  - `privateDemoRunArchiveOverall=...`
  - `privateDemoRunArchiveRecommendedJobId=...`
  - `privateDemoRunArchiveCompletedJobCount=...`
  - `privateDemoRunArchiveHandoffReadyCount=...`
  - `privateDemoRunArchiveCandidate=jobId:profile:status:readiness:quality:cost:modelCalls:providerCacheHits:handoffReady`
  - `privateDemoRunArchiveLink=label:href`
- Add helper `write_private_demo_run_archive_report(json_path, markdown_path)` using only `archiveNotesMarkdown`.
- Add `scripts/demo/private-demo-run-archive.sh` writing JSON and Markdown under `/tmp/linguaframe-demo/private-demo-run-archive/`.
- Mention the archive command in launch rehearsal and evidence gallery terminal output so the operator sees the next evidence step.

- [ ] Write failing shell tests for route download, metadata-only summary, Markdown report, and unsafe fixture redaction.
- [ ] Implement helpers and the script.
- [ ] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [ ] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/private-demo-run-archive.sh scripts/demo/private-demo-launch-rehearsal.sh scripts/demo/private-demo-evidence-gallery.sh`.

## Task 4: Documentation, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/103-private-demo-run-archive-workspace.md`

**Interfaces:**
- Document when to use the private demo run archive: after one or more completed demo jobs, before sharing evidence externally, or before ending a private demo session.
- Document that the archive is a metadata-only index, not a backup, not a generated media package, and not a substitute for per-job demo run packages.
- Record validation commands and post-merge verification.

- [ ] Update docs and execution log.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `cd frontend && npm test -- --run`.
- [ ] Run `cd frontend && npm run build`.
- [ ] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit on the feature branch, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
