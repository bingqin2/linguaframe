# React Demo Experience MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser-based demo frontend that uploads a video, follows the generated localization job, previews outputs, downloads artifacts, and retries failed jobs.

**Architecture:** Build a React + Vite + TypeScript single-page work surface under `frontend/` and keep the backend unchanged except for documentation/runtime wiring. The frontend consumes the existing upload, job detail, retry, transcript, subtitle, and artifact routes through a Vite `/api` proxy; recent jobs are tracked in browser storage because the backend does not yet expose a server-side job list endpoint.

**Tech Stack:** React, Vite, TypeScript, Vitest, React Testing Library, CSS, Docker Compose, Java 21, Spring Boot 3.5.15, Maven.

## Global Constraints

- Run all commands from the repository root unless a task says `cd frontend`.
- Use feature branch `react-demo-experience-mvp`.
- Do not add authentication, marketing pages, admin dashboards, backend job-list APIs, WebSocket/SSE streaming, or new AI provider behavior in this slice.
- Do not expose OpenAI keys, object storage credentials, raw backend local paths, or raw provider responses in frontend logs or UI.
- Keep the first screen as the actual demo workspace, not a landing page.
- Use Vite proxying for local and Docker frontend development so browser calls stay same-origin at `/api`.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `frontend/` contains a runnable React + Vite + TypeScript application.
- The browser can upload an MP4 with a target language and receive the created job id.
- The UI stores recent uploaded job ids in local storage and lets the user reopen a known job id manually.
- The selected job polls `GET /api/jobs/{jobId}` until it reaches a terminal state.
- Job detail shows status, stage, failed reason, timeline events, usage summary, and model-call summaries.
- Transcript and target-language subtitle previews load from the existing preview endpoints.
- Artifact downloads use the existing artifact download route.
- Audio and video preview controls appear when `DUBBING_AUDIO` or `BURNED_VIDEO` artifacts exist.
- Failed jobs show a retry button that calls `POST /api/jobs/{jobId}/retry`.
- Docker Compose can run a `linguaframe-frontend` service against the existing backend service.

## Design Choices

Recommended approach: build the frontend against the existing backend API and use local recent-job storage as the MVP "job list." This proves the full browser demo without expanding backend scope.

Alternatives considered:

- Add a backend `GET /api/jobs` endpoint first: useful later, but it changes persistence/query semantics and makes this frontend slice too broad.
- Use a static HTML page without a framework: faster initially, but harder to test and extend into the planned demo UI.
- Add WebSocket or SSE progress updates: more polished, but polling is enough for the current backend and keeps the feature smaller.

## File Structure

- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/Dockerfile`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.test.tsx`
- Create: `frontend/src/api/linguaframeApi.ts`
- Create: `frontend/src/api/linguaframeApi.test.ts`
- Create: `frontend/src/domain/jobTypes.ts`
- Create: `frontend/src/domain/recentJobs.ts`
- Create: `frontend/src/domain/recentJobs.test.ts`
- Create: `frontend/src/styles.css`
- Create: `frontend/src/test/setup.ts`
- Modify: `.gitignore`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Frontend Scaffold And API Client

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/src/api/linguaframeApi.ts`
- Create: `frontend/src/api/linguaframeApi.test.ts`
- Create: `frontend/src/domain/jobTypes.ts`
- Create: `frontend/src/test/setup.ts`
- Modify: `.gitignore`

**Interfaces:**
- `uploadMedia(file: File, targetLanguage: string): Promise<MediaUpload>`
- `getJob(jobId: string): Promise<LocalizationJob>`
- `retryJob(jobId: string): Promise<LocalizationJob>`
- `listArtifacts(jobId: string): Promise<JobArtifact[]>`
- `listTranscript(jobId: string): Promise<TranscriptSegment[]>`
- `listSubtitles(jobId: string, language: string): Promise<SubtitleSegment[]>`
- `artifactDownloadUrl(jobId: string, artifactId: string): string`

- [ ] **Step 1: Add package metadata and test tooling**

Create a Vite React TypeScript package with scripts:

```json
{
  "scripts": {
    "dev": "vite --host 0.0.0.0",
    "build": "tsc -b && vite build",
    "test": "vitest --environment jsdom",
    "test:run": "vitest run --environment jsdom"
  }
}
```

Run:

```bash
cd frontend
npm install
```

Expected: `package-lock.json` is created and dependencies install successfully.

- [ ] **Step 2: Write failing API-client tests**

Cover:

- upload sends `multipart/form-data` to `/api/media/uploads` with `file` and `targetLanguage`.
- job detail calls `/api/jobs/{jobId}`.
- retry calls `POST /api/jobs/{jobId}/retry`.
- artifact URLs are same-origin `/api/jobs/{jobId}/artifacts/{artifactId}/download`.
- non-2xx JSON errors surface a concise message without dumping raw response bodies.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: fail because the API client does not exist.

- [ ] **Step 3: Implement typed API client and domain types**

Define TypeScript types that match the current backend VO field names for uploads, jobs, timeline events, usage summaries, model calls, artifacts, transcript segments, and subtitle segments.

Implement `requestJson<T>()` with safe error parsing and the exported API functions listed above.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: pass.

Commit:

```bash
git add .gitignore frontend/package.json frontend/package-lock.json frontend/index.html frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json frontend/src/api/linguaframeApi.ts frontend/src/api/linguaframeApi.test.ts frontend/src/domain/jobTypes.ts frontend/src/test/setup.ts
git commit -m "Add React demo API client scaffold"
```

## Task 2: Recent Jobs And Demo Workspace UI

**Files:**
- Create: `frontend/src/domain/recentJobs.ts`
- Create: `frontend/src/domain/recentJobs.test.ts`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.test.tsx`
- Create: `frontend/src/styles.css`

**Interfaces:**
- `loadRecentJobs(storage: Storage): RecentJob[]`
- `saveRecentJob(storage: Storage, job: RecentJob): RecentJob[]`
- `removeRecentJob(storage: Storage, jobId: string): RecentJob[]`

- [ ] **Step 1: Write failing recent-job storage tests**

Cover deduplication by job id, newest-first ordering, max 10 records, corrupted JSON fallback to an empty list, and removal.

Run:

```bash
cd frontend
npm run test:run -- recentJobs
```

Expected: fail because the storage helper does not exist.

- [ ] **Step 2: Implement recent-job storage**

Store records under `linguaframe.recentJobs.v1` with `jobId`, `videoId`, `targetLanguage`, `filename`, and `createdAt`.

Run:

```bash
cd frontend
npm run test:run -- recentJobs
```

Expected: pass.

- [ ] **Step 3: Write failing App tests**

Use mocked API functions to cover:

- upload form calls `uploadMedia`, stores the returned job, and selects it.
- manual job id lookup selects and fetches that job.
- polling stops when the job status is `SUCCEEDED` or `FAILED`.
- timeline, usage summary, model calls, failed reason, and retry button render from job detail data.

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: fail because the UI is not implemented.

- [ ] **Step 4: Implement the demo workspace**

Build one dense work surface with:

- upload panel for file and target language.
- recent jobs list from local storage.
- manual job id opener.
- job header with status, stage, failed reason, and refresh state.
- timeline, usage summary, and model-call sections.
- retry action only for failed jobs.

Use restrained dashboard styling, stable dimensions for controls, and no marketing hero.

Run:

```bash
cd frontend
npm run test:run -- App recentJobs
```

Expected: pass.

Commit:

```bash
git add frontend/src/domain/recentJobs.ts frontend/src/domain/recentJobs.test.ts frontend/src/main.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit -m "Build React demo workspace"
```

## Task 3: Preview And Artifact Surfaces

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Uses `listArtifacts`, `listTranscript`, `listSubtitles`, and `artifactDownloadUrl` from Task 1.

- [ ] **Step 1: Extend failing App tests for previews**

Cover:

- transcript preview renders timestamped source segments.
- subtitle preview renders timestamped target-language segments.
- artifact table renders type, filename, content type, size, and download link.
- audio player appears for `DUBBING_AUDIO`.
- video player appears for `BURNED_VIDEO`.
- preview panels show a concise empty state before artifacts exist.

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: fail because preview surfaces are incomplete.

- [ ] **Step 2: Implement previews and artifact controls**

Load transcript, subtitles, and artifacts when a job is selected. Use the current target language from the selected recent job or the language input fallback.

Use artifact download URLs for links and media preview sources. Keep backend errors concise and visible per panel.

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: pass.

Commit:

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/styles.css
git commit -m "Add demo previews and artifact controls"
```

## Task 4: Runtime Wiring And Documentation

**Files:**
- Create: `frontend/Dockerfile`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Docker Compose service `linguaframe-frontend`
- Environment variable `LINGUAFRAME_FRONTEND_PORT`

- [ ] **Step 1: Add frontend Docker service**

Add `LINGUAFRAME_FRONTEND_PORT=5173` to `.env.example`.

Add a `linguaframe-frontend` service that builds `frontend/Dockerfile`, exposes `${LINGUAFRAME_FRONTEND_PORT:-5173}:5173`, and depends on `linguaframe-backend`.

Configure Vite proxy in `frontend/vite.config.ts`:

```ts
server: {
  proxy: {
    '/api': {
      target: process.env.LINGUAFRAME_API_PROXY_TARGET ?? 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

The Docker service sets `LINGUAFRAME_API_PROXY_TARGET=http://linguaframe-backend:8080`.

- [ ] **Step 2: Update run documentation**

Document these commands:

```bash
docker compose --env-file .env up -d --build
open http://localhost:5173
```

For local frontend development:

```bash
mvn -pl LinguaFrame spring-boot:run
cd frontend
npm run dev
```

Add frontend smoke steps to the demo checklist: upload sample video, select job, wait for terminal state, inspect previews, download artifacts, retry a failed job when available.

- [ ] **Step 3: Verify build, tests, and Compose config**

Run:

```bash
cd frontend
npm run test:run
npm run build
cd ..
docker compose --env-file .env.example config
mvn -pl LinguaFrame test
```

Expected: frontend tests pass, frontend build succeeds, Compose renders a valid config, and backend tests pass.

Commit:

```bash
git add frontend/Dockerfile frontend/vite.config.ts .env.example docker-compose.yml README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/progress/decisions.md docs/progress/execution-log.md
git commit -m "Wire React demo runtime"
```

## Task 5: Browser Smoke And Merge

**Files:**
- Modify: `docs/progress/execution-log.md`

- [ ] **Step 1: Start the demo stack or local dev servers**

Preferred Docker path:

```bash
docker compose --env-file .env up -d --build
```

Open:

```text
http://localhost:5173
```

- [ ] **Step 2: Run a browser smoke test**

From the UI:

- upload a short MP4.
- confirm a recent job appears.
- wait until the selected job reaches `SUCCEEDED` or `FAILED`.
- confirm timeline and usage/model-call panels render.
- confirm transcript/subtitle preview panels render when data is available.
- confirm artifact download links appear.
- confirm audio/video previews appear when matching artifacts exist.

- [ ] **Step 3: Record evidence and final verification**

Append the exact commands, browser URL, and outcome to `docs/progress/execution-log.md`.

Run:

```bash
cd frontend
npm run test:run
npm run build
cd ..
mvn -pl LinguaFrame test
docker compose --env-file .env.example config
git status --short --branch
```

Expected: all verification commands pass and only intentional files are changed.

Commit:

```bash
git add docs/progress/execution-log.md
git commit -m "Record React demo verification"
```

Merge back to `main` after the feature is verified.
