# Worker Role Routing MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow the same LinguaFrame codebase to run either one combined worker or separate FFmpeg/OpenAI worker roles without breaking local Docker demo behavior.

**Architecture:** Introduce a `WorkerRole` configuration and workload-aware queue topology. `QueuedLocalizationJobMessage` gains a `startStage`; combined workers execute the full ordered pipeline, while split workers execute only the contiguous stages owned by their role and publish the next `startStage` message to the proper workload queue. The job is claimed only at the first stage, remains `PROCESSING` across handoffs, and is marked `COMPLETED` only after the final stage.

**Tech Stack:** Java 21, Spring Boot, RabbitMQ, Spring AMQP, Flyway-free message/domain changes, JdbcClient, JUnit 5, AssertJ, Docker Compose.

## Global Constraints

- This feature must be a complete, user-visible runtime feature slice: config, message contract, queue topology, publisher routing, worker role execution, tests, README/roadmap/spec updates, validation, commit, and merge back to `main`.
- Combined local worker behavior must remain the default and must continue to process a full job from upload to completion.
- Split roles must be `FFMPEG` and `OPENAI`; do not add Kubernetes, autoscaling, distributed tracing, or separate deploy manifests in this slice.
- A split worker must not mark a job completed until the final stage has run.
- A worker must never run a stage that is not owned by its configured role, except `COMBINED`, which owns all stages.
- Queue names and routing keys must remain configurable and backward-compatible with the existing `RABBITMQ_JOB_QUEUE` and `RABBITMQ_JOB_ROUTING_KEY` defaults.

---

## Task 1: Add Worker Role And Stage Ownership Domain

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/WorkerRole.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/WorkerStagePlanVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/WorkerStageRouter.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerStageRouterImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/WorkerStageRouterTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**

- `WorkerRole` values: `COMBINED`, `FFMPEG`, `OPENAI`.
- `WorkerStagePlanVo(List<LocalizationPipelineStage> executableStages, LocalizationJobStage nextStage, WorkerRole nextRole, boolean finalSegment)`.
- `WorkerStageRouter.plan(WorkerRole role, LocalizationJobStage startStage, List<LocalizationPipelineStage> orderedStages)` returns the contiguous stages this worker should execute.
- Stage ownership:
  - `COMBINED`: all stages from `startStage` to the end.
  - `FFMPEG`: `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `SUBTITLE_BURN_IN`, `ARTIFACT_SUMMARY`.
  - `OPENAI`: `TRANSCRIPT_SUBTITLE_EXPORT`, `TARGET_SUBTITLE_EXPORT`, `TRANSLATION_QUALITY_EVALUATION`, `DUBBING_AUDIO_GENERATION`.
- Default worker role is `COMBINED`.

- [x] **Step 1: Add failing router and config tests**
  - Assert default `linguaframe.worker.role` is `COMBINED`.
  - Assert invalid worker role binding fails.
  - Assert `COMBINED` from `WORKER_SMOKE` returns every configured stage and `finalSegment=true`.
  - Assert `FFMPEG` from `WORKER_SMOKE` returns `WORKER_SMOKE`, `AUDIO_EXTRACTION`, then `nextStage=TRANSCRIPT_SUBTITLE_EXPORT`, `nextRole=OPENAI`, `finalSegment=false`.
  - Assert `OPENAI` from `TRANSCRIPT_SUBTITLE_EXPORT` returns transcription, target subtitle, evaluation, TTS, then `nextStage=SUBTITLE_BURN_IN`, `nextRole=FFMPEG`, `finalSegment=false`.
  - Assert `FFMPEG` from `SUBTITLE_BURN_IN` returns subtitle burn-in and artifact summary with `finalSegment=true`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,LinguaFramePropertiesTests test
```

Expected: compilation failure because worker role and router types do not exist.

- [x] **Step 3: Implement role config and router**
  - Add `private WorkerRole role = WorkerRole.COMBINED;` to `LinguaFrameProperties.Worker`.
  - Add `role: ${LINGUAFRAME_WORKER_ROLE:COMBINED}` to `application.yaml`.
  - Implement `WorkerStageRouterImpl` as a Spring `@Service`.
  - Sort stages by `stage().ordinal()` before planning.
  - Throw `IllegalStateException("Worker role <role> cannot execute start stage <stage>.")` if the requested `startStage` is not owned by the role.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,LinguaFramePropertiesTests test
```

Expected: selected tests pass.

---

## Task 2: Add Stage-Aware Messages And Queue Routing

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/message/QueuedLocalizationJobMessage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/config/RabbitJobQueueConfiguration.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobQueuePublisher.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/RabbitJobQueuePublisher.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobDispatchOutboxServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobDispatchServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/config/RabbitJobQueueConfigurationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobDispatchServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobDispatchOutboxServiceTests.java`

**Interfaces:**

- `QueuedLocalizationJobMessage` gains `LocalizationJobStage startStage`.
- Canonical first message uses `startStage=WORKER_SMOKE`.
- `JobQueuePublisher.publish(QueuedLocalizationJobMessage message)` routes by `message.startStage()`.
- `LinguaFrameProperties.Rabbitmq` gains:
  - `ffmpegJobQueue`, default `${RABBITMQ_FFMPEG_JOB_QUEUE:${RABBITMQ_JOB_QUEUE:linguaframe.localization.jobs}}`
  - `openaiJobQueue`, default `${RABBITMQ_OPENAI_JOB_QUEUE:linguaframe.localization.openai.jobs}`
  - `ffmpegJobRoutingKey`, default `${RABBITMQ_FFMPEG_JOB_ROUTING_KEY:${RABBITMQ_JOB_ROUTING_KEY:localization.queued}}`
  - `openaiJobRoutingKey`, default `${RABBITMQ_OPENAI_JOB_ROUTING_KEY:localization.openai}`

- [x] **Step 1: Add failing message and queue tests**
  - Assert outbox payload includes `"startStage":"WORKER_SMOKE"`.
  - Assert dispatcher still deserializes existing payloads with missing `startStage` by normalizing them to `WORKER_SMOKE`.
  - Assert Rabbit configuration declares durable FFmpeg and OpenAI queues and bindings.
  - Assert publisher sends `WORKER_SMOKE`, `AUDIO_EXTRACTION`, `SUBTITLE_BURN_IN`, and `ARTIFACT_SUMMARY` messages to the FFmpeg routing key.
  - Assert publisher sends transcription, translation, evaluation, and TTS start-stage messages to the OpenAI routing key.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=RabbitJobQueueConfigurationTests,JobDispatchServiceTests,JobDispatchOutboxServiceTests test
```

Expected: compilation or assertion failure because messages and queue topology are not stage-aware.

- [x] **Step 3: Implement message compatibility and queue routing**
  - Add a compact constructor to `QueuedLocalizationJobMessage` that defaults null `startStage` to `WORKER_SMOKE`.
  - Keep existing JSON fields and add only the new `startStage` field.
  - Update outbox creation to pass `LocalizationJobStage.WORKER_SMOKE`.
  - Add two queue beans and two binding beans. Keep the legacy bean names for the FFmpeg/default queue so existing tests and local defaults remain meaningful.
  - Update publisher routing with a private `routingKeyFor(LocalizationJobStage startStage)` helper.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=RabbitJobQueueConfigurationTests,JobDispatchServiceTests,JobDispatchOutboxServiceTests test
```

Expected: selected tests pass.

---

## Task 3: Execute Stage Segments And Publish Handoffs

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobExecutionServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/worker/LocalizationJobWorker.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/worker/LocalizationJobWorkerTests.java`

**Interfaces:**

- `LocalizationJobExecutionServiceImpl` gains dependencies: `LinguaFrameProperties properties`, `WorkerStageRouter stageRouter`, `JobQueuePublisher publisher`.
- First segment (`startStage=WORKER_SMOKE`) claims `QUEUED`/`RETRYING` and records `WORKER_RECEIVED STARTED`.
- Handoff segments require the job to already be `PROCESSING`; stale or terminal jobs are skipped with a `SKIPPED` timeline event.
- After a non-final segment succeeds, execution publishes a new `QueuedLocalizationJobMessage` with the same job/video/source/target fields and `startStage=plan.nextStage()`.
- Final segment marks the job completed and records `COMPLETED SUCCEEDED`.
- `LocalizationJobWorker` listens to `${linguaframe.rabbitmq.listener-queue}` where `listenerQueue` defaults by role: combined/FFmpeg -> FFmpeg queue, OpenAI -> OpenAI queue.

- [x] **Step 1: Add failing execution tests**
  - `COMBINED` from `WORKER_SMOKE` still executes every stage and marks completed.
  - `FFMPEG` from `WORKER_SMOKE` executes only smoke/audio, does not mark completed, publishes a message with `startStage=TRANSCRIPT_SUBTITLE_EXPORT`, and leaves the job `PROCESSING`.
  - `OPENAI` from `TRANSCRIPT_SUBTITLE_EXPORT` executes only OpenAI stages, publishes `startStage=SUBTITLE_BURN_IN`, and leaves the job `PROCESSING`.
  - `FFMPEG` from `SUBTITLE_BURN_IN` executes burn-in/summary and marks completed.
  - A role receiving an unowned `startStage` fails the job with a safe error.
  - A handoff segment for a non-processing job is skipped.
  - Worker listener annotation uses `${linguaframe.rabbitmq.listener-queue}`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test
```

Expected: failures because execution always runs the full pipeline and the listener queue is not role-aware.

- [x] **Step 3: Implement segmented execution**
  - Route stage selection through `WorkerStageRouter`.
  - Keep existing cache-hit, provider-cache-hit, failure, cancellation, and timeline behavior inside the per-stage loop.
  - Add a private `handoff(...)` method that publishes the next message and records a `SKIPPED` or `SUCCEEDED` handoff timeline message using the current stage.
  - Do not call `markCompleted` unless `plan.finalSegment()` is true.
  - Add a backward-compatible constructor for tests that passes `COMBINED`, router, and no-op publisher where needed.
  - Add `listenerQueue` derived config in properties/application YAML.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test
```

Expected: selected tests pass.

---

## Task 4: Runtime Configuration, Docker Compose, And Documentation

**Files:**

- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/035-worker-role-routing-mvp.md`

**Interfaces:**

- Existing `linguaframe-backend` service remains combined by default.
- Add documented optional services `linguaframe-worker-ffmpeg` and `linguaframe-worker-openai` behind a Compose profile such as `split-workers`.
- Combined mode keeps `LINGUAFRAME_WORKER_ROLE=COMBINED`.
- FFmpeg worker uses `LINGUAFRAME_WORKER_ROLE=FFMPEG`.
- OpenAI worker uses `LINGUAFRAME_WORKER_ROLE=OPENAI`.

- [x] **Step 1: Add docs and Compose validation expectations**
  - README documents when to use combined worker locally and when to use split workers.
  - README documents new env vars: `LINGUAFRAME_WORKER_ROLE`, `RABBITMQ_FFMPEG_JOB_QUEUE`, `RABBITMQ_OPENAI_JOB_QUEUE`, `RABBITMQ_FFMPEG_JOB_ROUTING_KEY`, `RABBITMQ_OPENAI_JOB_ROUTING_KEY`.
  - Roadmap Phase 13 marks worker role configuration, FFmpeg/OpenAI role support, queue routing, and combined-vs-split docs as implemented.
  - Decisions records why split routing uses stage handoff messages before Kubernetes/autoscaling.
  - Execution log records red/green validation.

- [x] **Step 2: Run Docker config red/green validation**

```bash
docker compose --env-file .env.example --profile split-workers config --quiet
```

Expected after implementation: command exits 0.

- [x] **Step 3: Update docs and Compose**
  - Add the split-worker services with the same backend image and command, but role-specific env vars.
  - Keep the default Compose path unchanged for `docker compose --env-file .env.example up -d`.
  - Document that split workers require dispatcher enabled and RabbitMQ reachable.

- [x] **Step 4: Run selected validation**

```bash
mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,RabbitJobQueueConfigurationTests,LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test
docker compose --env-file .env.example --profile split-workers config --quiet
```

Expected: selected backend tests and Compose config pass.

---

## Task 5: Full Validation, Commit, And Merge

**Files:**

- Modify all files touched by Tasks 1-4.

- [x] **Step 1: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
git diff --check
```

Expected: all commands exit 0.

- [x] **Step 2: Commit feature branch**

```bash
git add README.md .env.example docker-compose.yml docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md docs/plans/035-worker-role-routing-mvp.md LinguaFrame/src
git commit -m "Add worker role routing"
```

- [x] **Step 3: Merge back to main**

```bash
git switch main
git merge --no-ff worker-role-routing-mvp
```

- [x] **Step 4: Run post-merge smoke validation**

```bash
mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test
docker compose --env-file .env.example --profile split-workers config --quiet
```

Expected: selected post-merge checks pass on `main`.
