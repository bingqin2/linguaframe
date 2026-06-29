# OpenAI Smoke Proof Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a job-scoped OpenAI smoke proof workspace that verifies a completed provider-backed smoke run produced the required OpenAI calls, artifacts, quality evidence, and safe demo links.

**Architecture:** Build a read-only backend aggregate from existing job detail, artifact listing, model-call audit, quality evaluation, and evidence package routes. Expose the same proof as JSON, Markdown download, a selected-job React panel, and a terminal script, then extend the OpenAI smoke runner to save the proof after a paid run completes.

**Tech Stack:** Java 21, Spring Boot MVC, existing job/query/artifact services, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash + Python demo helpers.

## Global Constraints

- This feature is post-run evidence only: it must not upload media, create jobs, dispatch queues, retry jobs, call OpenAI, mutate object storage, edit `.env`, or download media bytes.
- Outputs must exclude API keys, bearer tokens, demo tokens, provider request/response payloads, object keys, local media paths, raw transcript text, raw subtitle text, corrected draft text, and media bytes.
- Required proof should focus on successful OpenAI `TRANSCRIPTION` and `TRANSLATION` calls plus generated transcript and target subtitle artifacts.
- Optional proof should report quality evaluation, TTS, burned video, demo run package, AI audit package, and delivery links without blocking the core smoke proof unless a configured stage failed.
- Existing OpenAI readiness, smoke upload, evidence bundle, AI audit package, demo run package, and selected-job behavior must remain backward compatible.

---

## Task 1: Backend OpenAI Smoke Proof Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/OpenAiSmokeProofVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/OpenAiSmokeProofCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/OpenAiSmokeProofCallVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/OpenAiSmokeProofArtifactVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/OpenAiSmokeProofLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/OpenAiSmokeProofService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiSmokeProofServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/OpenAiSmokeProofServiceTests.java`

**Interfaces:**
- `OpenAiSmokeProofVo getProof(String jobId)` returns `jobId`, `overallStatus`, `phase`, `recommendedNextAction`, `completedAt`, `targetLanguage`, required checks, optional checks, OpenAI model-call summaries, artifact summaries, safe links, and safety notes.
- `String renderMarkdown(String jobId)` returns a metadata-only Markdown report for the same proof.
- Status rules:
  - `READY` when the job is `COMPLETED`, has successful OpenAI `TRANSCRIPTION` and `TRANSLATION` calls, has no failed OpenAI calls, and has transcript plus target subtitle artifacts.
  - `ATTENTION` when required proof is ready but optional evidence is missing, such as quality evaluation, TTS output, burned video, or handoff packages.
  - `BLOCKED` when the job is not completed, required OpenAI calls are missing or failed, required artifacts are missing, or the job has failed model calls.

- [x] Compose proof from `LocalizationJobQueryService.getJob(jobId)` and `JobArtifactService.listArtifacts(jobId)`.
- [x] Check required OpenAI model calls by `ModelCallProvider.OPENAI`, `ModelCallStatus.SUCCEEDED`, and operations `TRANSCRIPTION` and `TRANSLATION`.
- [x] Check required artifacts by `JobArtifactType.TRANSCRIPT_JSON`, `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, and `TARGET_SUBTITLE_VTT`.
- [x] Add optional checks for `qualityEvaluation`, `DUBBING_AUDIO`, `BURNED_VIDEO`, `DUBBED_VIDEO`, `/api/jobs/{jobId}/demo-run-package/download`, and `/api/jobs/{jobId}/ai-audit-package/download`.
- [x] Render model-call proof with stage, operation, provider, model, prompt version, status, latency, usage counters, estimated cost, and safe error summary only.
- [x] Add tests for `READY`, `ATTENTION`, `BLOCKED`, missing job propagation, and unsafe marker exclusion.
- [x] Run `mvn -pl LinguaFrame test -Dtest=OpenAiSmokeProofServiceTests`.

## Task 2: Job API Endpoints

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/openai-smoke-proof` returns `OpenAiSmokeProofVo`.
- `GET /api/jobs/{jobId}/openai-smoke-proof/markdown/download` returns `linguaframe-job-{jobId}-openai-smoke-proof.md`.

- [x] Inject `OpenAiSmokeProofService` into `LocalizationJobController`.
- [x] Add JSON and Markdown endpoints beside existing selected-job evidence routes.
- [x] Ensure endpoints inherit the existing private-demo token and bearer-token protection.
- [x] Add MockMvc coverage for JSON status, Markdown attachment headers, safe content, 404 propagation, and auth protection.
- [x] Run `mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests`.

## Task 3: Frontend Selected-Job Proof Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getOpenAiSmokeProof(jobId: string): Promise<OpenAiSmokeProof>`
- `downloadOpenAiSmokeProofMarkdown(jobId: string): Promise<Blob>`

- [x] Add TypeScript types matching the backend proof records.
- [x] Add API helpers using existing owner-session token and bearer-header behavior.
- [x] Load proof whenever a selected job is loaded, refreshed, retried, or selected from history.
- [x] Add an `OpenAI smoke proof` panel in the selected-job evidence area with overall status, required checks, OpenAI call rows, artifact rows, optional evidence, next action, and download action.
- [x] Keep the rest of selected-job UI usable if proof loading fails.
- [x] Add Vitest coverage for API paths, panel rendering, failure state, Markdown download, and unsafe marker absence.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.

## Task 4: Terminal Script And OpenAI Smoke Runner Export

**Files:**
- Create: `scripts/demo/openai-smoke-proof.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/128-openai-smoke-proof-workspace.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/openai-smoke-proof.sh`
- Default output directory: `/tmp/linguaframe-demo/openai-smoke-proof/`.
- Script output keys: `openAiSmokeProofStatus`, `openAiSmokeProofPhase`, `openAiSmokeProofRequiredReadyCount`, `openAiSmokeProofRequiredBlockedCount`, `openAiSmokeProofJsonPath`, and `openAiSmokeProofMarkdownPath`.

- [x] Implement a script that downloads JSON and Markdown proof, prints a Python summary, and exits non-zero on `BLOCKED` unless `LINGUAFRAME_OPENAI_SMOKE_PROOF_REPORT_ONLY=true`.
- [x] Add helper functions for proof JSON and Markdown download in `scripts/demo/lib/linguaframe-demo.sh`.
- [x] Extend `docker-e2e-openai-smoke.sh` to save `openai-smoke-proof.json` and `openai-smoke-proof.md` in the smoke output directory after job completion.
- [x] Document when to use OpenAI readiness evidence before upload versus OpenAI smoke proof after upload.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/openai-smoke-proof.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/128-openai-smoke-proof-workspace.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=OpenAiSmokeProofServiceTests,LocalizationJobControllerTests`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/openai-smoke-proof.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run full safety checks:
  `mvn -pl LinguaFrame test`
  `npm test -- --run`
  `git diff --check`
- [x] Commit as `Add OpenAI smoke proof workspace`.
- [x] Merge `openai-smoke-proof-workspace` back to `main` after validation passes.
