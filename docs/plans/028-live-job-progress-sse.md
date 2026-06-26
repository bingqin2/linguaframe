# Live Job Progress SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a live job progress stream so the React demo updates selected job detail through Server-Sent Events while preserving polling fallback.

**Architecture:** Implement a lightweight Spring MVC `SseEmitter` endpoint that emits `LocalizationJobVo` snapshots for one job until the job reaches a terminal state or a bounded stream timeout. The React app opens an `EventSource` for the selected active job, updates the same job state from SSE messages, refreshes previews/history on terminal events, and falls back to the existing polling path if SSE is unavailable.

**Tech Stack:** Java 21, Spring Boot 3.5.15, `SseEmitter`, JdbcClient-backed query services, JUnit 5, MockMvc, React, Vite, TypeScript, Vitest, React Testing Library.

## Global Constraints

- Use feature branch `live-job-progress-sse`.
- Keep this as a demo reliability slice; do not add WebSocket, Redis pub/sub, Kafka, a separate worker process, or authentication.
- SSE payloads must reuse the existing safe `LocalizationJobVo`; do not expose object storage keys, local media paths, provider payloads, OpenAI keys, or stack traces.
- Preserve existing polling as fallback for browsers/tests where `EventSource` is unavailable or errors.
- Stop streaming when status is `COMPLETED`, `FAILED`, or `CANCELLED`.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

- `GET /api/jobs/{jobId}/events` returns `text/event-stream`.
- The stream sends an initial `job` event immediately.
- While the job is active, the stream sends periodic `job` snapshots.
- When the job becomes terminal, the stream sends the terminal snapshot and completes.
- Missing jobs still return the existing concise not-found error before opening a stream.
- The frontend uses SSE for selected active jobs and keeps the current polling behavior as fallback.
- The frontend refreshes preview data and server history when an SSE event makes the selected job terminal.

## Design Choices

Recommended approach: bounded snapshot SSE backed by existing query service. This is smaller and more reliable than building a push bus now, and it is enough to make the demo visibly live.

Alternatives considered:

- WebSocket: useful later, but larger than needed for one-way job progress.
- Redis pub/sub: better for multi-process deployment, but premature while API and worker still run in one service.
- Remove polling entirely: risky for browsers, tests, and future proxy environments; fallback keeps the UI dependable.

## File Structure

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobProgressStreamService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobProgressStreamServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `README.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Backend SSE Endpoint

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobProgressStreamService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobProgressStreamServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Produces: `SseEmitter streamJob(String jobId)`
- Produces: `GET /api/jobs/{jobId}/events`

- [x] **Step 1: Write failing controller test for stream content type**

Add to `LocalizationJobControllerTests`:

```java
@Test
void opensJobProgressEventStream() throws Exception {
    Instant createdAt = Instant.parse("2026-06-27T08:00:00Z");
    createJob("job-controller-events-video", "job-controller-events-job", "events.mp4",
            LocalizationJobStatus.COMPLETED, createdAt);

    mockMvc.perform(get("/api/jobs/{jobId}/events", "job-controller-events-job"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/event-stream")));
}
```

Add imports:

```java
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.startsWith;
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: fail with `404` because the endpoint does not exist.

- [x] **Step 2: Add stream service interface and controller route**

Create `LocalizationJobProgressStreamService`:

```java
package com.linguaframe.job.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LocalizationJobProgressStreamService {

    SseEmitter streamJob(String jobId);
}
```

Wire it into `LocalizationJobController`:

```java
@GetMapping(value = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamJobEvents(@PathVariable String jobId) {
    return progressStreamService.streamJob(jobId);
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: fail because no `LocalizationJobProgressStreamService` bean exists.

- [x] **Step 3: Implement bounded snapshot stream**

Create `LocalizationJobProgressStreamServiceImpl`:

```java
@Service
public class LocalizationJobProgressStreamServiceImpl implements LocalizationJobProgressStreamService {

    private static final long STREAM_TIMEOUT_MS = 60_000L;
    private static final long SNAPSHOT_INTERVAL_MS = 1_000L;
    private static final int MAX_SNAPSHOTS = 60;
    private static final Set<LocalizationJobStatus> TERMINAL_STATUSES =
            Set.of(LocalizationJobStatus.COMPLETED, LocalizationJobStatus.FAILED, LocalizationJobStatus.CANCELLED);

    private final LocalizationJobQueryService queryService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public LocalizationJobProgressStreamServiceImpl(LocalizationJobQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public SseEmitter streamJob(String jobId) {
        LocalizationJobVo initialJob = queryService.getJob(jobId);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        executorService.submit(() -> streamSnapshots(jobId, initialJob, emitter));
        return emitter;
    }

    private void streamSnapshots(String jobId, LocalizationJobVo initialJob, SseEmitter emitter) {
        try {
            LocalizationJobVo currentJob = initialJob;
            for (int index = 0; index < MAX_SNAPSHOTS; index++) {
                emitter.send(SseEmitter.event().name("job").data(currentJob));
                if (TERMINAL_STATUSES.contains(currentJob.status())) {
                    emitter.complete();
                    return;
                }
                Thread.sleep(SNAPSHOT_INTERVAL_MS);
                currentJob = queryService.getJob(jobId);
            }
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.completeWithError(ex);
        } catch (RuntimeException | IOException ex) {
            emitter.completeWithError(ex);
        }
    }
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: pass.

## Task 2: Frontend SSE API Helper

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`

**Interfaces:**
- Produces: `jobEventsUrl(jobId: string): string`

- [x] **Step 1: Write failing API URL test**

Add to `linguaframeApi.test.ts` imports and tests:

```ts
import { jobEventsUrl } from './linguaframeApi';

test('builds same-origin job event stream urls', () => {
  expect(jobEventsUrl('job 1')).toBe('/api/jobs/job%201/events');
});
```

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: fail because `jobEventsUrl` does not exist.

- [x] **Step 2: Implement URL helper**

Add to `linguaframeApi.ts`:

```ts
export function jobEventsUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/events`;
}
```

Export it through `linguaFrameApi`.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: pass.

## Task 3: Frontend Live Updates With Polling Fallback

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `linguaFrameApi.jobEventsUrl(jobId)`
- Produces: selected active jobs update from SSE `message`/`job` events.

- [x] **Step 1: Write failing EventSource test**

Add a minimal fake `EventSource` to `App.test.tsx`:

```ts
class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  emitJob(job: LocalizationJob) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(job) }));
  }

  close() {
    this.closed = true;
  }
}
```

Add test:

```ts
test('updates selected active job from server-sent events', async () => {
  const originalEventSource = window.EventSource;
  window.EventSource = FakeEventSource as unknown as typeof EventSource;
  FakeEventSource.instances = [];
  vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'PROCESSING' }));
  vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

  try {
    render(<App />);
    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    await waitFor(() => expect(FakeEventSource.instances[0]?.url).toBe('/api/jobs/job-1/events'));
    FakeEventSource.instances[0].emitJob(jobFixture({ status: 'COMPLETED' }));

    expect(await screen.findByText('COMPLETED')).toBeInTheDocument();
  } finally {
    window.EventSource = originalEventSource;
  }
});
```

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: fail because `App` does not create an `EventSource`.

- [x] **Step 2: Implement SSE subscription hook inside App**

In `App.tsx`, add:

```ts
const [isSseUnavailable, setIsSseUnavailable] = useState(false);
```

Add effect:

```tsx
useEffect(() => {
  if (!job || TERMINAL_STATUSES.has(job.status) || !supportsEventSource() || isSseUnavailable) {
    return;
  }

  const eventSource = new EventSource(linguaFrameApi.jobEventsUrl(job.jobId));
  eventSource.onmessage = (event) => {
    try {
      const nextJob = JSON.parse(event.data) as LocalizationJob;
      setJob(nextJob);
      setError(null);
      if (TERMINAL_STATUSES.has(nextJob.status)) {
        void loadPreviewData(nextJob.jobId, nextJob.targetLanguage);
        void loadHistory(historyStatusFilter);
        eventSource.close();
      }
    } catch {
      setIsSseUnavailable(true);
      eventSource.close();
    }
  };
  eventSource.onerror = () => {
    setIsSseUnavailable(true);
    eventSource.close();
  };

  return () => eventSource.close();
}, [historyStatusFilter, isSseUnavailable, job, loadHistory, loadPreviewData]);
```

Update polling effect to skip only when SSE is available:

```tsx
if (!job || TERMINAL_STATUSES.has(job.status) || (!isSseUnavailable && supportsEventSource())) {
  return;
}
```

Reset fallback when a new job is selected in `loadJob` success:

```ts
setIsSseUnavailable(false);
```

Add helper:

```ts
function supportsEventSource(): boolean {
  return typeof window.EventSource === 'function';
}
```

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: pass.

- [x] **Step 3: Add fallback test**

Add test:

```ts
test('falls back to polling when server-sent events error', async () => {
  const originalEventSource = window.EventSource;
  window.EventSource = FakeEventSource as unknown as typeof EventSource;
  FakeEventSource.instances = [];
  const getJob = vi
    .spyOn(linguaFrameApi, 'getJob')
    .mockResolvedValueOnce(jobFixture({ status: 'PROCESSING' }))
    .mockResolvedValueOnce(jobFixture({ status: 'COMPLETED' }));
  vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

  try {
    render(<App pollIntervalMs={10} />);
    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
    FakeEventSource.instances[0].onerror?.();

    await waitFor(() => expect(getJob).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('COMPLETED')).toBeInTheDocument();
  } finally {
    window.EventSource = originalEventSource;
  }
});
```

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: pass.

## Task 4: Documentation And Execution Log

**Files:**
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/028-live-job-progress-sse.md`

**Interfaces:**
- Produces: documented SSE endpoint, fallback behavior, and validation evidence.

- [x] **Step 1: Update README and product docs**

Add README section near job detail APIs:

```md
Live job progress is available through Server-Sent Events:

```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/api/jobs/{jobId}/events
```

The stream emits safe job-detail snapshots and closes after `COMPLETED`, `FAILED`, or `CANCELLED`. The React demo uses this stream when available and falls back to polling if the stream errors.
```

Update `docs/product/spec.md` Future Scope by marking SSE as implemented in the local demo path.

Update `docs/product/frontend-design.md` Job Detail responsibilities:

```md
- Prefer live SSE progress updates and keep polling fallback.
```

- [x] **Step 2: Append execution log entry**

Append to `docs/progress/execution-log.md` after validation:

```md
## 2026-06-27

Work:

- Added `GET /api/jobs/{jobId}/events` as a Server-Sent Events job snapshot stream.
- Updated the React demo to use EventSource for active selected jobs with polling fallback.
- Refreshed previews and history when live events move a selected job to a terminal state.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because `/api/jobs/{jobId}/events` did not exist, then passed after wiring SSE.
- `cd frontend && npm run test:run -- linguaframeApi` first failed because `jobEventsUrl` was missing, then passed.
- `cd frontend && npm run test:run -- App` first failed because no EventSource subscription existed, then passed after implementation.

Notes:

- This is snapshot-based SSE for the local demo. It does not add Redis pub/sub or a cross-process event bus.
```

- [x] **Step 3: Verify docs mention SSE**

Run:

```bash
rg -n "Server-Sent Events|EventSource|/events|SSE|polling fallback" README.md docs/product/spec.md docs/product/frontend-design.md docs/progress/execution-log.md docs/plans/028-live-job-progress-sse.md
```

Expected: output includes README, product docs, execution log, and this plan.

## Task 5: Final Verification And Merge

**Verification Commands:**

- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test`
- `cd frontend && npm run test:run -- linguaframeApi App`
- `cd frontend && npm run test:run`
- `cd frontend && npm run build`
- `docker compose --env-file .env.example config`
- `mvn -pl LinguaFrame test -q`
- `git diff --check`
- `git status --short`

- [x] Confirm no media files, generated artifacts, `.env`, API keys, or credentials are staged.
- [x] Commit: `Add live job progress SSE`
- [x] Merge branch `live-job-progress-sse` back to `main` after verification.
- [x] Add post-merge verification to `docs/progress/execution-log.md` and commit it on `main`.

## Completion Checklist

- [x] `GET /api/jobs/{jobId}/events` returns `text/event-stream`.
- [x] Stream emits an initial job snapshot.
- [x] Stream completes after terminal statuses.
- [x] Missing jobs return the existing safe not-found response.
- [x] Frontend opens EventSource for active selected jobs.
- [x] Frontend updates selected job detail from SSE events.
- [x] Frontend falls back to polling after SSE errors or unavailable EventSource.
- [x] Terminal SSE updates refresh previews and server job history.
- [x] Validation evidence is recorded in `docs/progress/execution-log.md`.
- [x] Feature branch is merged back to `main` after verified implementation.
