# Job History List MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-backed job history list so the browser demo can discover recent localization jobs without relying only on local storage.

**Architecture:** Add a lightweight backend list endpoint under `GET /api/jobs` that returns paged job summaries joined with source video metadata and optional status filtering. Extend the React demo API client and sidebar so the first screen shows a refreshable server job history, while keeping upload, manual job open, polling, previews, retry, and browser-local recent jobs intact.

**Tech Stack:** Java 21, Spring Boot 3.5.15, JdbcClient, JUnit 5, MockMvc, React, Vite, TypeScript, Vitest, React Testing Library, Docker Compose.

## Global Constraints

- Use feature branch `job-history-list-mvp`.
- Do not add authentication, users, ownership scoping, admin analytics, WebSocket/SSE updates, or deletion/cancel actions in this slice.
- Do not expose object storage keys, raw local media paths, OpenAI keys, provider payloads, or backend stack traces in list responses or frontend UI.
- Keep the first screen as the actual demo workspace, not a landing page.
- Preserve manual job id opening and browser-local recent jobs as fallback conveniences.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `GET /api/jobs` returns recent jobs ordered by `createdAt DESC`, capped to a safe page size.
- Optional query parameters:
  - `status`: one `LocalizationJobStatus` value such as `QUEUED`, `PROCESSING`, `COMPLETED`, or `FAILED`.
  - `limit`: integer from `1` to `100`; invalid or missing values use a default of `20`.
  - `offset`: integer `0` or greater; invalid or missing values use `0`.
- Each list item contains `jobId`, `videoId`, `filename`, `targetLanguage`, `status`, `createdAt`, `startedAt`, `completedAt`, `failedAt`, `failureStage`, `failureReason`, `retryCount`, and an estimated cost summary.
- The frontend loads the first page on startup, can filter by status, refresh the list, and open any listed job.
- Uploading or retrying refreshes the server-backed history after the action succeeds.
- Existing detail, artifact, transcript, subtitle, audio/video preview, and retry behavior continues to work.

## Design Choices

Recommended approach: add a compact `LocalizationJobSummaryVo` and `LocalizationJobListVo` instead of returning full `LocalizationJobVo` objects from the list endpoint. Full detail includes timeline and model-call arrays, which are useful after selection but too heavy for list refreshes.

Alternatives considered:

- Use the existing `GET /api/jobs/{jobId}` shape for every list row: simple, but it would over-fetch timeline and model-call data for every refresh.
- Keep relying on `localStorage` only: no backend work, but it fails after a different browser, cleared storage, or demo handoff.
- Add user-scoped history now: correct for hosted usage, but authentication and ownership are explicitly outside this local-demo slice.

## File Structure

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobListVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobQueryService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Backend Job Summary Query

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobListVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`

**Interfaces:**
- `List<LocalizationJobSummaryVo> findSummaries(LocalizationJobStatus status, int limit, int offset)`
- `int countSummaries(LocalizationJobStatus status)`
- `record LocalizationJobSummaryVo(String jobId, String videoId, String filename, String targetLanguage, LocalizationJobStatus status, Instant createdAt, Instant startedAt, Instant completedAt, Instant failedAt, LocalizationJobStage failureStage, String failureReason, int retryCount, BigDecimal estimatedCostUsd)`
- `record LocalizationJobListVo(List<LocalizationJobSummaryVo> jobs, int limit, int offset, int total)`

- [ ] **Step 1: Write failing repository tests**

Add tests that create three videos/jobs with distinct `createdAt` values and model-call cost data, then assert:

- `findSummaries(null, 2, 0)` returns newest two jobs first with filenames.
- `findSummaries(LocalizationJobStatus.FAILED, 20, 0)` returns only failed jobs.
- `countSummaries(null)` and `countSummaries(LocalizationJobStatus.FAILED)` return correct totals.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test
```

Expected: fail because summary query methods do not exist.

- [ ] **Step 2: Implement summary records and repository queries**

Implement `LocalizationJobSummaryVo` and `LocalizationJobListVo`. In `LocalizationJobRepository`, join `localization_jobs` to `videos`, left join `model_call_records`, group by job/video fields, and compute `COALESCE(SUM(model_call_records.estimated_cost_usd), 0)`.

Ordering and bounds:

- `ORDER BY localization_jobs.created_at DESC`
- `LIMIT :limit OFFSET :offset`
- Repository methods trust already-normalized `limit` and `offset`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobSummaryVo.java LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobListVo.java LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java
git commit -m "Add job history summary query"
```

## Task 2: Backend Job List API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobQueryService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset)`
- `GET /api/jobs?status=FAILED&limit=10&offset=0`

- [ ] **Step 1: Write failing controller tests**

Add MockMvc coverage for:

- `GET /api/jobs` returns `jobs`, `limit`, `offset`, and `total`, ordered newest first.
- `GET /api/jobs?status=FAILED` filters failed jobs.
- `GET /api/jobs?status=NOT_A_STATUS` returns HTTP 400.
- `GET /api/jobs?limit=999&offset=-5` normalizes to `limit=20` and `offset=0`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: fail because `GET /api/jobs` is not implemented.

- [ ] **Step 2: Implement service normalization and controller route**

In `LocalizationJobQueryServiceImpl`, normalize:

- default `limit = 20`
- maximum `limit = 100`
- values outside `1..100` become `20`
- negative `offset` becomes `0`

In `LocalizationJobController`, add:

```java
@GetMapping
public LocalizationJobListVo listJobs(
        @RequestParam(required = false) LocalizationJobStatus status,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer offset
)
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: pass.

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobQueryService.java LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java
git commit -m "Expose job history list API"
```

## Task 3: Frontend API And History UI

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `listJobs(params?: { status?: LocalizationJobStatus | 'ALL'; limit?: number; offset?: number }): Promise<LocalizationJobList>`
- `interface LocalizationJobSummary`
- `interface LocalizationJobList`

- [ ] **Step 1: Write failing frontend API tests**

Cover:

- `listJobs()` calls `/api/jobs?limit=20&offset=0`.
- `listJobs({ status: 'FAILED', limit: 10, offset: 20 })` calls `/api/jobs?status=FAILED&limit=10&offset=20`.
- `status: 'ALL'` is omitted from the query string.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: fail because `listJobs` does not exist.

- [ ] **Step 2: Implement frontend types and API client**

Add `LocalizationJobSummary` and `LocalizationJobList` to `jobTypes.ts`. Add `listJobs` to `linguaframeApi.ts` and export it in `linguaFrameApi`.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: pass.

- [ ] **Step 3: Write failing App tests for server-backed history**

Mock `linguaFrameApi.listJobs` and cover:

- App loads server job history on startup.
- Changing status filter to `FAILED` reloads with `{ status: 'FAILED', limit: 20, offset: 0 }`.
- Clicking a history row opens that job and loads preview data.
- Upload success refreshes the server-backed job list.
- If list loading fails, the UI shows a concise history error and keeps manual job opening available.

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: fail because the App has no server-backed history UI.

- [ ] **Step 4: Implement history UI**

Add state for:

- `historyStatusFilter: LocalizationJobStatus | 'ALL'`
- `history: LocalizationJobSummary[]`
- `isLoadingHistory`
- `historyError`

Load history on startup and whenever the filter changes. Add a compact `Job history` panel above browser-local recent jobs with:

- status select containing `ALL`, `QUEUED`, `RETRYING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`
- refresh button
- rows showing filename, status, target language, created time, retry count, and estimated cost
- empty state `No server jobs match this filter.`

Run:

```bash
cd frontend
npm run test:run -- App
npm run test:run
npm run build
```

Expected: pass.

Commit:

```bash
git add frontend/src/domain/jobTypes.ts frontend/src/api/linguaframeApi.ts frontend/src/api/linguaframeApi.test.ts frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit -m "Add server-backed job history UI"
```

## Task 4: Docs And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

- [ ] **Step 1: Document the job history API and UI behavior**

Update README with:

```bash
curl "http://localhost:8080/api/jobs?limit=20&offset=0"
curl "http://localhost:8080/api/jobs?status=FAILED&limit=20&offset=0"
```

Update roadmap/frontend notes to mark the basic job list behavior as implemented for the local demo.

- [ ] **Step 2: Run full verification**

Run:

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config
```

Expected: all pass.

- [ ] **Step 3: Record validation and commit docs**

Append a dated entry to `docs/progress/execution-log.md` with the implemented behavior and exact commands run. Add a decision note that the job list is intentionally global for the local self-hosted demo until authentication exists.

Commit:

```bash
git add README.md docs/product/roadmap.md docs/progress/decisions.md docs/progress/execution-log.md
git commit -m "Document job history list MVP"
```

## Completion Criteria

- Backend `GET /api/jobs` supports paging, optional status filtering, and safe list summaries.
- React demo shows server-backed job history on first load.
- Manual job open and browser-local recent jobs still work.
- Upload and retry refresh the server history.
- Frontend and backend tests pass.
- Docker Compose config still renders backend and frontend services.
- Feature branch is merged back to `main` after verification.
