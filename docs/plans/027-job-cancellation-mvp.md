# Job Cancellation MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user-visible cancellation flow so queued, retrying, and processing localization jobs can be marked `CANCELLED` and stop progressing at safe worker stage boundaries.

**Architecture:** Implement soft cancellation through an atomic repository status transition and a dedicated `LocalizationJobCancellationService`. The worker checks for `CANCELLED` before claiming, before each pipeline stage, and after each stage so a cancelled job never starts a later expensive step. The React demo exposes a Cancel action for active jobs and refreshes history/detail after cancellation.

**Tech Stack:** Java 21, Spring Boot 3.5.15, JdbcClient, JUnit 5, MockMvc, React, Vite, TypeScript, Vitest, React Testing Library, Docker Compose.

## Global Constraints

- Use feature branch `job-cancellation-mvp`.
- Keep cancellation as soft cancellation; do not attempt to kill an already-running FFmpeg process or OpenAI HTTP request in this slice.
- Only jobs in `QUEUED`, `RETRYING`, or `PROCESSING` can be cancelled.
- `COMPLETED`, `FAILED`, and `CANCELLED` jobs must reject cancellation with HTTP 409.
- Cancelled jobs must not be retried unless a later feature explicitly changes retry policy.
- Do not delete videos, artifacts, model-call records, timeline events, dispatch events, or object-storage objects during cancellation.
- Do not expose object storage keys, local media paths, provider payloads, OpenAI keys, or stack traces in cancellation responses.
- Keep the first frontend screen as the actual demo workspace, not a landing page.
- Record final verification evidence in `docs/progress/execution-log.md`.

---

## Feature Boundary

This feature produces the following behavior:

- `POST /api/jobs/{jobId}/cancel` returns the updated `LocalizationJobVo`.
- Cancelling a `QUEUED`, `RETRYING`, or `PROCESSING` job changes status to `CANCELLED`, clears failure fields, sets `completedAt`, and records a timeline event.
- Cancelling `COMPLETED`, `FAILED`, or already `CANCELLED` jobs returns HTTP 409 with a concise `CONFLICT` response.
- A queued dispatch message for a cancelled job is skipped and never claims the job for execution.
- If a job is cancelled while processing, the worker stops before the next stage and returns `CANCELLED`.
- Frontend job detail shows a Cancel button for `QUEUED`, `RETRYING`, and `PROCESSING` jobs.
- Cancel success refreshes selected job detail, previews/history as appropriate, and stops polling because `CANCELLED` is already a terminal status.

## Design Choices

Recommended approach: soft cancellation at durable status boundaries. This is reliable for the current modular monolith, avoids unsafe thread/process termination, and still demonstrates real operational control in the demo.

Alternatives considered:

- Hard-kill FFmpeg/OpenAI work: more immediate, but it requires cooperative cancellation through every provider and process wrapper and is too risky for this slice.
- Only cancel queued jobs: smaller, but weaker for the demo because most visible cancellation attempts happen after processing starts.
- Delete queued outbox events: unnecessary because dispatch/worker status checks can skip stale messages, and deletion would obscure audit history.

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobCancellationService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobCancellationServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobCancellationServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Backend Cancellation State Transition

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`

**Interfaces:**
- Produces: `boolean markCancelled(String jobId, Instant cancelledAt)`
- Produces: `boolean isCancelled(String jobId)`

- [x] **Step 1: Write failing repository tests**

Add tests to `LocalizationJobRepositoryTests`:

```java
@Test
void marksQueuedRetryingAndProcessingJobsCancelled() {
    Instant now = Instant.parse("2026-06-27T03:00:00Z");
    createJob("video-cancel-queued", "job-cancel-queued", LocalizationJobStatus.QUEUED, now);
    createJob("video-cancel-retrying", "job-cancel-retrying", LocalizationJobStatus.RETRYING, now);
    createJob("video-cancel-processing", "job-cancel-processing", LocalizationJobStatus.PROCESSING, now);

    assertThat(jobRepository.markCancelled("job-cancel-queued", now.plusSeconds(1))).isTrue();
    assertThat(jobRepository.markCancelled("job-cancel-retrying", now.plusSeconds(2))).isTrue();
    assertThat(jobRepository.markCancelled("job-cancel-processing", now.plusSeconds(3))).isTrue();

    assertThat(jobRepository.findById("job-cancel-processing"))
            .get()
            .satisfies(job -> {
                assertThat(job.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
                assertThat(job.completedAt()).isEqualTo(now.plusSeconds(3));
                assertThat(job.failedAt()).isNull();
                assertThat(job.failureStage()).isNull();
                assertThat(job.failureReason()).isNull();
                assertThat(job.updatedAt()).isEqualTo(now.plusSeconds(3));
            });
}

@Test
void doesNotCancelTerminalJobsAndDetectsCancelledJobs() {
    Instant now = Instant.parse("2026-06-27T03:10:00Z");
    createJob("video-cancel-completed", "job-cancel-completed", LocalizationJobStatus.COMPLETED, now);
    createJob("video-cancel-failed", "job-cancel-failed", LocalizationJobStatus.FAILED, now);
    createJob("video-cancel-cancelled", "job-cancel-cancelled", LocalizationJobStatus.CANCELLED, now);

    assertThat(jobRepository.markCancelled("job-cancel-completed", now.plusSeconds(1))).isFalse();
    assertThat(jobRepository.markCancelled("job-cancel-failed", now.plusSeconds(1))).isFalse();
    assertThat(jobRepository.markCancelled("job-cancel-cancelled", now.plusSeconds(1))).isFalse();
    assertThat(jobRepository.isCancelled("job-cancel-cancelled")).isTrue();
    assertThat(jobRepository.isCancelled("missing-job")).isFalse();
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test
```

Expected: fail because `markCancelled` and `isCancelled` do not exist.

- [x] **Step 2: Implement repository methods**

Add to `LocalizationJobRepository`:

```java
public boolean markCancelled(String jobId, Instant cancelledAt) {
    int updated = jdbcClient.sql("""
                    UPDATE localization_jobs
                    SET status = :cancelledStatus,
                        completed_at = :completedAt,
                        failed_at = NULL,
                        failure_stage = NULL,
                        failure_reason = NULL,
                        updated_at = :updatedAt
                    WHERE id = :id
                      AND status IN (:queuedStatus, :retryingStatus, :processingStatus)
                    """)
            .param("cancelledStatus", LocalizationJobStatus.CANCELLED.name())
            .param("completedAt", Timestamp.from(cancelledAt))
            .param("updatedAt", Timestamp.from(cancelledAt))
            .param("id", jobId)
            .param("queuedStatus", LocalizationJobStatus.QUEUED.name())
            .param("retryingStatus", LocalizationJobStatus.RETRYING.name())
            .param("processingStatus", LocalizationJobStatus.PROCESSING.name())
            .update();
    return updated == 1;
}

public boolean isCancelled(String jobId) {
    Boolean cancelled = jdbcClient.sql("""
                    SELECT COUNT(*) > 0
                    FROM localization_jobs
                    WHERE id = :id
                      AND status = :cancelledStatus
                    """)
            .param("id", jobId)
            .param("cancelledStatus", LocalizationJobStatus.CANCELLED.name())
            .query(Boolean.class)
            .single();
    return Boolean.TRUE.equals(cancelled);
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test
```

Expected: pass.

## Task 2: Cancellation Service And HTTP API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobCancellationService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobCancellationServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobCancellationServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Produces: `LocalizationJobVo cancelJob(String jobId)`
- Produces: `POST /api/jobs/{jobId}/cancel`

- [x] **Step 1: Write failing service tests**

Create `LocalizationJobCancellationServiceTests` with coverage for:

```java
@Test
void cancelsQueuedJobAndRecordsTimeline() {
    Instant now = Instant.parse("2026-06-27T03:20:00Z");
    createJob("cancel-service-video", "cancel-service-job", LocalizationJobStatus.QUEUED, now);
    LocalizationJobCancellationService service = service(Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

    LocalizationJobVo result = service.cancelJob("cancel-service-job");

    assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
    assertThat(result.completedAt()).isEqualTo(now.plusSeconds(1));
    assertThat(timelineEventRepository.findByJobId("cancel-service-job"))
            .last()
            .satisfies(event -> {
                assertThat(event.stage()).isEqualTo(LocalizationJobStage.WORKER_RECEIVED);
                assertThat(event.status()).isEqualTo(JobTimelineEventStatus.SKIPPED);
                assertThat(event.message()).isEqualTo("Cancellation requested.");
            });
}

@Test
void rejectsTerminalJobCancellation() {
    Instant now = Instant.parse("2026-06-27T03:30:00Z");
    createJob("cancel-service-video-terminal", "cancel-service-job-terminal", LocalizationJobStatus.COMPLETED, now);
    LocalizationJobCancellationService service = service(Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.cancelJob("cancel-service-job-terminal"))
            .isInstanceOf(JobStateConflictException.class)
            .hasMessage("Only queued, retrying, or processing localization jobs can be cancelled.");
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobCancellationServiceTests test
```

Expected: fail because the service does not exist.

- [x] **Step 2: Implement cancellation service**

Create interface:

```java
package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobCancellationService {

    LocalizationJobVo cancelJob(String jobId);
}
```

Implement service:

```java
@Service
public class LocalizationJobCancellationServiceImpl implements LocalizationJobCancellationService {

    private final LocalizationJobRepository jobRepository;
    private final JobTimelineEventRepository timelineEventRepository;
    private final LocalizationJobQueryService queryService;
    private final Clock clock;

    @Override
    @Transactional
    public LocalizationJobVo cancelJob(String jobId) {
        jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Localization job not found."));
        Instant now = Instant.now(clock);
        if (!jobRepository.markCancelled(jobId, now)) {
            throw new JobStateConflictException(
                    "Only queued, retrying, or processing localization jobs can be cancelled."
            );
        }
        timelineEventRepository.save(new JobTimelineEventRecord(
                UUID.randomUUID().toString(),
                jobId,
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.SKIPPED,
                "Cancellation requested.",
                null,
                null,
                now
        ));
        return queryService.getJob(jobId);
    }
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobCancellationServiceTests test
```

Expected: pass.

- [x] **Step 3: Write failing controller tests**

Add to `LocalizationJobControllerTests`:

```java
@Test
void cancelsQueuedLocalizationJob() throws Exception {
    Instant createdAt = Instant.parse("2026-06-27T03:40:00Z");
    createJob("job-controller-cancel-video", "job-controller-cancel-job", createdAt);

    mockMvc.perform(post("/api/jobs/{jobId}/cancel", "job-controller-cancel-job"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jobId").value("job-controller-cancel-job"))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.completedAt").exists())
            .andExpect(jsonPath("$.timelineEvents[0].message").value("Cancellation requested."));
}

@Test
void rejectsCompletedLocalizationJobCancellation() throws Exception {
    Instant createdAt = Instant.parse("2026-06-27T03:50:00Z");
    createJob("job-controller-cancel-completed-video", "job-controller-cancel-completed", "done.mp4",
            LocalizationJobStatus.COMPLETED, createdAt);

    mockMvc.perform(post("/api/jobs/{jobId}/cancel", "job-controller-cancel-completed"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test
```

Expected: fail because `/cancel` is not implemented.

- [x] **Step 4: Wire controller route**

Inject `LocalizationJobCancellationService` into `LocalizationJobController` and add:

```java
@PostMapping("/{jobId}/cancel")
public LocalizationJobVo cancelJob(@PathVariable String jobId) {
    return cancellationService.cancelJob(jobId);
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobCancellationServiceTests test
```

Expected: pass.

## Task 3: Worker Cancellation Boundaries

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Consumes: `LocalizationJobRepository#isCancelled(String jobId)`
- Produces: worker returns `LocalizationJobExecutionResultVo(jobId, true, CANCELLED)` when cancellation is observed during processing.

- [x] **Step 1: Write failing execution tests**

Add tests:

```java
@Test
void skipsCancelledJobMessageWithoutExecutingStages() {
    Instant now = Instant.parse("2026-06-27T04:00:00Z");
    createJob("execution-video-cancelled", "execution-job-cancelled", LocalizationJobStatus.CANCELLED, now);
    RecordingStage stage = new RecordingStage(false);
    LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
            jobRepository,
            timelineEventRepository,
            List.of(stage),
            Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
    );

    var result = service.execute(message("execution-job-cancelled", "execution-video-cancelled", now));

    assertThat(result.executed()).isFalse();
    assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
    assertThat(stage.context).isNull();
}

@Test
void stopsBeforeNextStageWhenJobIsCancelledDuringProcessing() {
    Instant now = Instant.parse("2026-06-27T04:10:00Z");
    createJob("execution-video-cancel-mid", "execution-job-cancel-mid", LocalizationJobStatus.QUEUED, now);
    CancellingStage cancellingStage = new CancellingStage(jobRepository, now.plusSeconds(20));
    RecordingStage nextStage = new RecordingStage(false, LocalizationJobStage.ARTIFACT_SUMMARY);
    LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
            jobRepository,
            timelineEventRepository,
            List.of(cancellingStage, nextStage),
            Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
    );

    var result = service.execute(message("execution-job-cancel-mid", "execution-video-cancel-mid", now));

    assertThat(result.executed()).isTrue();
    assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
    assertThat(cancellingStage.context).isNotNull();
    assertThat(nextStage.context).isNull();
    assertThat(timelineEventRepository.findByJobId("execution-job-cancel-mid"))
            .extracting(event -> event.status())
            .contains(JobTimelineEventStatus.SKIPPED);
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test
```

Expected: the second test fails until the worker checks cancellation between stages.

- [x] **Step 2: Implement cancellation checks in the worker**

In `LocalizationJobExecutionServiceImpl`:

- Keep the existing claim behavior for already-terminal cancelled jobs.
- After claiming and validating the video id, check cancellation before each stage.
- After each successful stage, check cancellation before continuing.
- Add helper:

```java
private boolean isCancelled(String jobId) {
    return jobRepository.isCancelled(jobId);
}

private LocalizationJobExecutionResultVo cancelled(String jobId, LocalizationJobStage stage, Instant occurredAt) {
    saveTimeline(jobId, stage, JobTimelineEventStatus.SKIPPED,
            "Localization job cancelled.", null, null, occurredAt);
    return new LocalizationJobExecutionResultVo(jobId, true, LocalizationJobStatus.CANCELLED);
}
```

Run:

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test
```

Expected: pass.

## Task 4: Frontend Cancel API And UI

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces: `cancelJob(jobId: string): Promise<LocalizationJob>`
- Produces: visible Cancel button for `QUEUED`, `RETRYING`, and `PROCESSING`.

- [x] **Step 1: Write failing API test**

Add to `linguaframeApi.test.ts`:

```ts
test('cancels an active job', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    jsonResponse({
      jobId: 'job-1',
      videoId: 'video-1',
      targetLanguage: 'zh',
      status: 'CANCELLED',
      createdAt: '2026-06-26T10:00:00Z',
      startedAt: null,
      completedAt: '2026-06-26T10:01:00Z',
      failedAt: null,
      failureStage: null,
      failureReason: null,
      retryCount: 0,
      dispatchStatus: 'DISPATCHED',
      dispatchAttempts: 1,
      dispatchedAt: '2026-06-26T10:00:02Z',
      timelineEvents: [],
      usageSummary: null,
      modelCalls: [],
      qualityEvaluation: null
    })
  );

  const job = await cancelJob('job-1');

  expect(job.status).toBe('CANCELLED');
  expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1/cancel', { method: 'POST' });
});
```

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: fail because `cancelJob` does not exist.

- [x] **Step 2: Implement frontend cancel API**

Add:

```ts
export async function cancelJob(jobId: string): Promise<LocalizationJob> {
  return requestJson<LocalizationJob>(`/api/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: 'POST'
  });
}
```

Export it through `linguaFrameApi`.

Run:

```bash
cd frontend
npm run test:run -- linguaframeApi
```

Expected: pass.

- [x] **Step 3: Write failing App cancellation tests**

Add tests:

```ts
test('shows cancel action for active jobs and refreshes after cancellation', async () => {
  const cancelJob = vi
    .spyOn(linguaFrameApi, 'cancelJob')
    .mockResolvedValue(jobFixture({ status: 'CANCELLED', completedAt: '2026-06-26T10:01:00Z' }));
  const listJobs = vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(jobListFixture());
  vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'PROCESSING' }));
  vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

  render(<App />);

  await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
  await userEvent.click(screen.getByRole('button', { name: /open job/i }));
  await userEvent.click(await screen.findByRole('button', { name: /cancel/i }));

  await waitFor(() => expect(cancelJob).toHaveBeenCalledWith('job-1'));
  expect(within(screen.getByRole('region', { name: /selected job/i })).getByText('CANCELLED'))
    .toBeInTheDocument();
  expect(listJobs).toHaveBeenCalledTimes(2);
});

test('does not show cancel action for terminal jobs', async () => {
  vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'COMPLETED' }));
  vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
  vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

  render(<App />);

  await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
  await userEvent.click(screen.getByRole('button', { name: /open job/i }));

  expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /cancel/i })).not.toBeInTheDocument();
});
```

Run:

```bash
cd frontend
npm run test:run -- App
```

Expected: fail until the UI exposes cancellation.

- [x] **Step 4: Implement Cancel UI**

In `App.tsx`:

- Add `const ACTIVE_STATUSES = new Set(['QUEUED', 'RETRYING', 'PROCESSING']);`
- Add `const canCancel = job ? ACTIVE_STATUSES.has(job.status) : false;`
- Add `isCancelling` state and `handleCancel`.
- Pass `canCancel`, `isCancelling`, and `onCancel` to `JobDetail`.
- Render Cancel next to Retry:

```tsx
{canCancel ? (
  <button type="button" className="secondary-button" onClick={onCancel} disabled={isCancelling}>
    {isCancelling ? 'Cancelling...' : 'Cancel'}
  </button>
) : null}
```

After successful cancellation:

- Set selected `job` to the returned `CANCELLED` job.
- Clear the global error.
- Refresh server history.
- Keep previews visible; cancellation does not delete existing artifacts.

Run:

```bash
cd frontend
npm run test:run -- App linguaframeApi
```

Expected: pass.

## Task 5: Documentation And Execution Log

**Files:**
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Produces: documented cancellation behavior and validation evidence.

- [x] **Step 1: Update product and README docs**

Add to README job workflow section:

```markdown
Active jobs can be cancelled from the frontend or with `POST /api/jobs/{jobId}/cancel`. Cancellation is soft: LinguaFrame marks the job `CANCELLED` and the worker stops before the next pipeline stage, while already-generated artifacts remain available.
```

Add to `docs/product/spec.md` Failure Handling And Retry:

```markdown
- Queued, retrying, and processing jobs can be cancelled. Cancellation is durable and stops the worker at safe stage boundaries without deleting existing records or artifacts.
```

Update `docs/product/frontend-design.md` Job Detail responsibilities to include the active cancel action.

- [x] **Step 2: Append execution log entry**

Add:

```markdown
## 2026-06-27

Work:

- Added soft cancellation for queued, retrying, and processing localization jobs.
- Added `POST /api/jobs/{jobId}/cancel` and frontend Cancel action.
- Updated worker execution to stop at safe stage boundaries after cancellation.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests,LocalizationJobCancellationServiceTests test` passed.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed.
- `npm run test:run -- linguaframeApi App` passed.
```

- [x] **Step 3: Verify docs mention cancellation**

Run:

```bash
rg -n "cancel|CANCELLED|/cancel|soft cancellation|safe stage" README.md docs/product/spec.md docs/product/frontend-design.md docs/progress/execution-log.md docs/plans/027-job-cancellation-mvp.md
```

Expected: docs mention API, worker behavior, and frontend action.

## Task 6: Final Verification And Merge

**Files:**
- Verify all files changed in Tasks 1-5.

**Verification Commands:**

- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests,LocalizationJobCancellationServiceTests test`
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test`
- `cd frontend && npm run test:run -- linguaframeApi App`
- `cd frontend && npm run test:run`
- `cd frontend && npm run build`
- `docker compose --env-file .env.example config`
- `mvn -pl LinguaFrame test -q`
- `git diff --check`
- `git status --short`

- [x] Confirm no media files, generated artifacts, `.env`, API keys, or credentials are staged.
- [x] Commit: `Add job cancellation MVP`
- [x] Merge branch `job-cancellation-mvp` back to `main` after verification.
- [x] Add post-merge verification to `docs/progress/execution-log.md` and commit it on `main`.

## Completion Checklist

- [x] `POST /api/jobs/{jobId}/cancel` returns `CANCELLED` for queued, retrying, and processing jobs.
- [x] Cancelling terminal jobs returns HTTP 409 and does not mutate them.
- [x] Worker skips already-cancelled messages and stops before the next stage if cancellation happens mid-run.
- [x] Cancellation does not delete existing artifacts, timeline events, model calls, videos, or object-storage objects.
- [x] Frontend shows Cancel only for active jobs and refreshes job history after cancellation.
- [x] Polling stops when the selected job becomes `CANCELLED`.
- [x] Tests cover repository transition, service behavior, controller route, worker boundaries, frontend API, and frontend UI.
- [x] Validation evidence is recorded in `docs/progress/execution-log.md`.
- [x] Feature branch is merged back to `main` after verified implementation.
