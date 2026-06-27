# Browser Demo Cache Replay Evidence MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser-visible cache replay comparison so a demo viewer can compare two completed jobs and see whether the second run reused provider or artifact cache.

**Architecture:** Reuse existing job detail APIs instead of adding a backend endpoint. The frontend will let the operator pin the currently selected job as a baseline, choose a comparison job from recent browser jobs or server history, load both job details and artifacts, and render a safe comparison panel with cache-hit counts, provider cache-hit timeline stages, artifact generated/reused counts, model-call counts, estimated cost, and evidence export text. The existing Docker cache-hit script remains the terminal proof path; the browser panel becomes the demo explanation path.

**Tech Stack:** React, TypeScript, Vitest, Testing Library, Spring Boot API read-only reuse, Markdown docs.

## Global Constraints

- Do not add a new backend API unless existing job detail and artifact APIs cannot support the comparison.
- Do not expose raw transcript text, raw subtitle text, object keys, provider payloads, local media paths, demo tokens, or credentials.
- Keep comparison read-only; it must not retry, cancel, upload, delete, or mutate jobs.
- Compare only two jobs at a time to keep the demo simple.
- Treat missing or failed job details as panel-level errors that do not break the rest of the app.
- The feature must include frontend code, focused frontend tests, docs, validation, roadmap/progress updates, this plan file, and merge tracking.

---

## Task 1: Cache Replay Comparison Model

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/domain/jobTypes.ts` if a local view type is useful
- Test: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add a `CacheReplayComparison` view model in `App.tsx` or a small local helper section with:
  - `baselineJob`
  - `comparisonJob`
  - `baselineArtifacts`
  - `comparisonArtifacts`
  - `providerCacheHitStages`
  - `artifactCacheHitCount`
  - `generatedArtifactCount`
  - `modelCallDelta`
  - `estimatedCostDelta`
- [x] Derive provider cache-hit stages from `timelineEvents` where `status === 'CACHE_HIT'`.
- [x] Derive artifact generated/reused counts from `JobArtifact.cacheHit`.
- [x] Add a safe Markdown formatter for the comparison summary containing only job ids, statuses, counts, stages, and safe download routes.
- [x] Add unit coverage in `App.test.tsx` through rendered behavior rather than exporting internal helpers.

## Task 2: Browser UI Panel

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add a `Cache replay` panel near the selected job evidence/result panels.
- [x] Add a `Pin as baseline` action for the selected job.
- [x] Add comparison choices from browser-local recent jobs and server history jobs, excluding the pinned baseline job.
- [x] Load comparison job detail with `linguaFrameApi.getJob` and artifacts with `linguaFrameApi.listArtifacts`.
- [x] Render baseline versus comparison status, target language, TTS voice, model-call count, estimated cost, artifact cache hits, provider cache hits, and cache-hit stages.
- [x] Render empty states for no baseline, no comparison candidates, loading comparison, failed comparison load, and non-completed jobs.
- [x] Add `Copy replay evidence` and `Download replay evidence JSON` actions using the safe comparison summary.
- [x] Keep text compact and dashboard-like; do not add a marketing explanation section.

## Task 3: Frontend Tests

**Files:**
- Modify: `frontend/src/App.test.tsx`
- Modify if needed: `frontend/src/api/linguaframeApi.test.ts`

**Steps:**

- [x] Add a test that pins a completed job as baseline, selects another completed job, and shows provider/artifact cache deltas.
- [x] Add a test that a comparison job with `CACHE_HIT` timeline events lists the cache-hit stages.
- [x] Add a test that `Copy replay evidence` writes safe Markdown without transcript text, object keys, or provider payload fields.
- [x] Add a test that `Download replay evidence JSON` creates a JSON blob with job ids, counts, and safe routes only.
- [x] Add a test that failed comparison fetch renders an inline error and leaves the selected job usable.

## Task 4: Documentation And Demo Workflow

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/069-browser-demo-cache-replay-evidence-mvp.md`

**Steps:**

- [x] Document the intended cache replay demo flow:
  1. Run `scripts/demo/docker-e2e-cache-hit.sh`.
  2. Open the frontend.
  3. Open the first job and pin it as baseline.
  4. Select the second job as comparison.
  5. Export replay evidence.
- [x] Update the smoke checklist with expected UI evidence: provider cache-hit stages, artifact reused/generated counts, model-call/cost comparison, copy/download actions.
- [x] Update Phase 8 or Phase 12 roadmap status to show browser-visible cache replay evidence.
- [x] Record the decision that cache comparison is frontend-composed from existing safe job APIs instead of a new backend aggregate endpoint.
- [x] Update execution log with validation commands and results.

## Task 5: Validation

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/069-browser-demo-cache-replay-evidence-mvp.md`

**Steps:**

- [x] Run `cd frontend && npm run test:run -- App -t "cache replay"`.
- [x] Run `cd frontend && npm run test:run -- App`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash -n scripts/demo/docker-e2e-cache-hit.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] If no backend code changes are made, document why Maven tests are not required; otherwise run focused backend tests for touched backend code.

## Done Criteria

- [x] A demo operator can pin one job and compare it with another job in the browser.
- [x] The panel makes provider cache hits, artifact reuse, model-call count, and cost difference visible without reading terminal output.
- [x] Exported replay evidence is safe and excludes raw content, object keys, local paths, tokens, credentials, and provider payloads.
- [x] Frontend tests cover the main comparison flow, export flow, and failure state.
- [x] README, Docker E2E guide, smoke checklist, roadmap, decisions, execution log, and this plan are updated.
- [ ] The feature branch is committed, verified, and merged back to `main`.
