# OpenAI Readiness Evidence Center Implementation Plan

**Goal:** Add one metadata-only OpenAI readiness evidence center that proves whether a configured OpenAI demo is safe to run before uploading media or spending on a full job.

**Architecture:** Reuse existing runtime dependency summaries, live checks, upload readiness, model usage ledger, and OpenAI demo scripts. Add a backend operator aggregate that turns provider configuration, live OpenAI probe state, provider warnings, budget posture, and recommended commands into one JSON/Markdown evidence surface. Expose it in the React demo and a terminal script.

**Tech Stack:** Java 21, Spring Boot MVC, existing runtime/operator services, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash + Python demo helpers.

## Global Constraints

- This feature is read-only and must not upload media, create jobs, call transcription/translation/TTS/evaluation, mutate object storage, dispatch queues, edit `.env`, or print secrets.
- The only live provider signal may come from the existing disabled-by-default OpenAI connectivity check inside `GET /api/runtime/live-checks`.
- Outputs must exclude API keys, bearer tokens, demo tokens, provider request/response payloads, local media paths, object keys, raw transcript text, subtitle text, and media bytes.
- Existing OpenAI preflight, live checks, upload readiness, private-demo operations, and model usage ledger behavior must remain backward compatible.

## Task 1: Backend OpenAI Readiness Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/OpenAiReadinessEvidenceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/OpenAiReadinessEvidenceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/OpenAiReadinessEvidenceServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/OpenAiReadinessEvidenceServiceTests.java`

**Interfaces:**
- `OpenAiReadinessEvidenceVo getEvidence()` returns `overallStatus`, `phase`, `recommendedNextAction`, provider readiness rows, OpenAI live-check status, budget/readiness warnings, recent model-call summary, safe commands, safe links, and safety notes.
- Status rules:
  - `READY` when OpenAI providers are configured for paid stages and live check is `UP`.
  - `ATTENTION` when OpenAI providers are configured but the live check is `SKIPPED`, or when recent failed model calls exist.
  - `BLOCKED` when OpenAI providers are configured but the live check is `DOWN`, required model/credentials are missing, upload readiness is blocked for provider/budget reasons, or runtime summary is missing required routes.
  - `SKIPPED` when all providers are deterministic/demo and OpenAI is not part of the current run path.

- [x] Compose evidence from `RuntimeDependencySummaryService`, `RuntimeLiveCheckService`, `DemoUploadReadinessService`, and `ModelUsageLedgerService`.
- [x] Identify transcription, translation, evaluation, and TTS provider modes without exposing credentials.
- [x] Add safe commands: `scripts/demo/openai-demo-preflight.sh`, `scripts/demo/docker-e2e-openai-smoke.sh`, `scripts/demo/upload-readiness.sh`, and `scripts/demo/model-usage-ledger.sh`.
- [x] Render Markdown with provider rows, live-check result, budget/readiness notes, model usage summary, and next action.
- [x] Add tests for `READY`, `ATTENTION`, `BLOCKED`, and `SKIPPED` outcomes plus unsafe marker exclusion.
- [x] Run `mvn -pl LinguaFrame test -Dtest=OpenAiReadinessEvidenceServiceTests`.

## Task 2: Operator API Endpoint And Security Tests

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Interfaces:**
- `GET /api/operator/openai-readiness-evidence` returns JSON.
- `GET /api/operator/openai-readiness-evidence/markdown/download` returns `openai-readiness-evidence.md`.

- [x] Inject `OpenAiReadinessEvidenceService` into `OperatorDashboardController`.
- [x] Add JSON and Markdown download endpoints under `/api/operator/**`.
- [x] Ensure endpoints inherit existing demo-token and bearer-token protection.
- [x] Add controller tests for JSON, Markdown attachment headers, safe content, and token protection.
- [x] Run `mvn -pl LinguaFrame test -Dtest=OperatorDashboardControllerTests`.

## Task 3: Frontend OpenAI Readiness Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getOpenAiReadinessEvidence(): Promise<OpenAiReadinessEvidence>`
- `downloadOpenAiReadinessEvidenceMarkdown(): Promise<Blob>`

- [x] Add TypeScript domain types matching the backend VO.
- [x] Add API helpers with existing demo-token/bearer header support.
- [x] Add an `OpenAI readiness evidence` panel near live checks and demo run launcher.
- [x] Show overall status, phase, provider rows, live-check status, model-call risk, recommended next action, and safe commands.
- [x] Add `Download readiness evidence` action using the backend Markdown endpoint.
- [x] Keep upload controls usable if the panel fails to load.
- [x] Add Vitest coverage for API calls, panel rendering, download action, and secret/raw-text absence.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.

## Task 4: Terminal Script And Documentation

**Files:**
- Create: `scripts/demo/openai-readiness-evidence.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/127-openai-readiness-evidence-center.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/openai-readiness-evidence/`.
- Script output keys: `openAiReadinessStatus`, `openAiReadinessPhase`, `openAiReadinessLiveCheckStatus`, `openAiReadinessRecommendedNextAction`, `openAiReadinessJsonPath`, and `openAiReadinessMarkdownPath`.

- [x] Implement script with `demo_curl`, JSON download, Markdown download, Python summary printing, and report-only mode.
- [x] Exit non-zero on `BLOCKED` unless `LINGUAFRAME_OPENAI_READINESS_REPORT_ONLY=true`.
- [x] Document when to use this evidence center versus `openai-demo-preflight.sh`, `docker-e2e-openai-smoke.sh`, upload readiness, live checks, and model usage ledger.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/openai-readiness-evidence.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/127-openai-readiness-evidence-center.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=OpenAiReadinessEvidenceServiceTests,OperatorDashboardControllerTests`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/openai-readiness-evidence.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit as `Add OpenAI readiness evidence center`.
- [x] Merge the feature branch back to `main` after validation passes.
