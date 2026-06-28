# Worker Topology Readiness Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current worker role, queue routing, and combined/split-worker operating mode visible and verifiable before running private demos.

**Architecture:** Extend the existing `/api/runtime/dependencies` safe readiness surface instead of adding a new privileged endpoint. Add metadata-only worker topology fields derived from `LinguaFrameProperties`, render them in the React readiness/runbook surfaces, and expose the same evidence through demo scripts and docs. This does not change queue publishing or worker execution behavior.

**Tech Stack:** Spring Boot configuration properties, runtime readiness VOs, JUnit 5/Spring Boot tests, React + TypeScript + Vitest, Bash/Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be a complete feature: backend topology metadata, browser visibility, terminal summary, docs, validation, commit, and merge back to `main`.
- Do not change queue publishing semantics, RabbitMQ exchange binding, worker execution order, or Docker service topology in this slice.
- Do not expose RabbitMQ passwords, demo tokens, OpenAI keys, object keys, local paths, provider payloads, raw transcript text, subtitle text, or media bytes.
- Keep combined local worker behavior as the default.
- Keep split-worker guidance operational and explicit: `COMBINED`, `FFMPEG`, and `OPENAI` should each show which queue and stages they own.

---

## Current Context

- Phase 13 worker pool split is implemented at the routing/execution layer, but the browser and terminal readiness surfaces do not explain the active topology.
- `WorkerReadinessVo` currently contains dispatch/execution flags, role, retry count, dispatch batch size, and dispatch interval.
- `LinguaFrameProperties.Rabbitmq` already contains job exchange, legacy/default queue/routing key, FFmpeg queue/routing key, OpenAI queue/routing key, and listener queue.
- The React `Demo readiness` panel currently shows only worker role and dispatch state, so it cannot tell whether a split-worker process is listening to the correct queue.

## Task 1: Backend Safe Worker Topology Metadata

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/WorkerReadinessVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Extend `WorkerReadinessVo` with safe fields:
  - `String listenerQueue`
  - `String jobExchange`
  - `String defaultJobQueue`
  - `String defaultRoutingKey`
  - `String ffmpegJobQueue`
  - `String ffmpegRoutingKey`
  - `String openaiJobQueue`
  - `String openaiRoutingKey`
  - `List<String> ownedStageGroups`
  - `List<String> recommendedCommands`
- `ownedStageGroups` should be concise labels, for example `FFMPEG:AUDIO_EXTRACTION,SUBTITLE_BURN_IN,DUBBED_VIDEO_DELIVERY` and `OPENAI:TRANSCRIPT_SUBTITLE_EXPORT,TARGET_SUBTITLE_EXPORT,SUBTITLE_POLISHING,QUALITY_EVALUATION,DUBBING_AUDIO_GENERATION`.
- `recommendedCommands` should be metadata-only startup hints such as `LINGUAFRAME_WORKER_ROLE=COMBINED docker compose --env-file .env up -d linguaframe-backend`.

- [x] Write failing controller test assertions that `/api/runtime/dependencies` returns worker queue/routing fields and recommended commands without credentials.
- [x] Implement the extended VO and populate it from `LinguaFrameProperties`.
- [x] Run `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test`.

## Task 2: Browser Worker Topology Readiness Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Extend the TypeScript `WorkerReadiness` shape with the backend fields from Task 1.
- Add a `Worker topology` subsection inside the existing `Demo readiness` panel.
- Show role, execution/dispatch state, listener queue, exchange, FFmpeg queue/routing key, OpenAI queue/routing key, owned stage groups, and recommended commands.
- Keep values text-only and copy-safe; do not render secrets or raw environment dumps.

- [x] Write failing `App.test.tsx` coverage proving the panel renders queue topology, owned stage groups, and recommended commands.
- [x] Implement the frontend type and UI changes.
- [x] Run `cd frontend && npm test -- --run App.test.tsx`.

## Task 3: Terminal Readiness Summary

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/upload-readiness.sh`
- Modify: `scripts/demo/private-demo-preflight.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Extend runtime/upload readiness summary output with:
  - `workerTopologyRole=...`
  - `workerTopologyListenerQueue=...`
  - `workerTopologyFfmpegRoute=queue:routingKey`
  - `workerTopologyOpenaiRoute=queue:routingKey`
  - `workerTopologyCommand=...`
- Keep output metadata-only and redaction-tested.
- `private-demo-preflight.sh` should print a pass line for worker topology visibility after the runtime contract check succeeds.

- [x] Write failing shell helper tests that include queue/routing metadata and unsafe password/token markers in fixture JSON.
- [x] Implement summary output and preflight pass line.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/upload-readiness.sh scripts/demo/private-demo-preflight.sh`.

## Task 4: Documentation And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/102-worker-topology-readiness-workspace.md`

**Interfaces:**
- Document how to read worker topology in browser and terminal before running a demo.
- Document that this is observability/readiness only and does not change RabbitMQ routing.
- Record validation commands and post-merge verification.

- [x] Update docs and execution log.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on the feature branch, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
