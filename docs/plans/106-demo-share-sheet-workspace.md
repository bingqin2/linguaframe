# Demo Share Sheet Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe, reviewer-facing demo share sheet for a completed job so the browser and terminal can present one concise entry point with status, key outcomes, and curated download links.

**Architecture:** Build on the existing job detail, demo run matrix, delivery manifest, evidence, and presenter pack services. The share sheet is metadata-only: it should summarize what to inspect and where to download safe artifacts, but it must not embed media bytes, object keys, local paths, provider payloads, raw transcript text, raw subtitle text, credentials, or demo tokens.

**Tech Stack:** Spring Boot services/controllers, JUnit 5 tests, React + TypeScript + Vitest, Bash demo helpers, Markdown docs.

## Global Constraints

- This slice must be a complete feature: backend API, Markdown download, browser panel, terminal export, docs, validation, commit, and merge back to `main`.
- Keep existing demo presenter pack, handoff package, evidence bundle, and run archive behavior backward compatible.
- Require owner-scoped access for share sheet endpoints and download routes.
- Prefer curated links and concise outcome fields over large nested diagnostics payloads.

---

## Current Context

- `DemoPresenterPackServiceImpl` already combines the run matrix, delivery manifest, recommended runs, and safe download links.
- The selected job view already has detailed panels for review guide, session report, presenter pack, delivery, media, evidence, and subtitle review.
- Terminal scripts can export evidence gallery and run archive, but there is not yet a single share-sheet command for one chosen job.

## Task 1: Backend Share Sheet Contract

**Files:**
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoShareSheetVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoShareSheetLinkVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoShareSheetService.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoShareSheetServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Add/modify focused backend tests under `LinguaFrame/src/test/java/com/linguaframe/job/`

**Interfaces:**
- Add `GET /api/jobs/{jobId}/demo-share-sheet`.
- Add `GET /api/jobs/{jobId}/demo-share-sheet/markdown/download`.
- Include safe fields: job id, video id, target language, demo profile, generated time, readiness, headline, one-paragraph summary, primary outcome bullets, recommended next action, selected safe links, and evidence package links.
- Add tests proving completed jobs produce a ready sheet, incomplete jobs produce an attention sheet, and unsafe values are excluded.

- [ ] Write failing service/controller tests for JSON and Markdown output.
- [ ] Implement VO, service, controller routes, and Markdown rendering.
- [ ] Run focused backend tests for share sheet and owner access.

## Task 2: Browser Share Sheet Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Add a selected-job `Demo share sheet` panel near the existing review/presenter sections.
- Show readiness, headline, summary, outcome bullets, recommended next action, and curated links.
- Provide copy-safe Markdown and backend Markdown download actions.
- Keep UI dense and operational; do not add a marketing landing page.

- [ ] Write failing Vitest coverage for rendering, copy action, and download link.
- [ ] Implement frontend API type and panel.
- [ ] Run focused frontend tests.

## Task 3: Terminal Export Script

**Files:**
- Add: `scripts/demo/demo-share-sheet.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/demo-share-sheet.sh` downloads JSON and Markdown to `/tmp/linguaframe-demo/demo-share-sheet/`.
- The full Tears demo should export the share sheet automatically after a completed job.
- Script output must stay metadata-only and fail clearly when the job id is missing or the backend blocks access.

- [ ] Add failing shell helper tests for JSON/Markdown export and unsafe redaction fixtures.
- [ ] Implement the script and integrate it into the full demo path.
- [ ] Run shell validation.

## Task 4: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/106-demo-share-sheet-workspace.md`

**Validation Commands:**
- `mvn -pl LinguaFrame -Dtest=DemoShareSheetServiceTests,LocalizationJobControllerTests,AuthenticatedOwnerJobAccessTests test`
- `cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/demo-share-sheet.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `mvn -pl LinguaFrame test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `git diff --check`

- [ ] Document how to export a share sheet from browser and terminal.
- [ ] Record validation evidence in the execution log.
- [ ] Commit the completed feature branch, merge back to `main`, run post-merge focused validation, and record the merge.
