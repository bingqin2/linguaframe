# Translation Quality Evaluation MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a non-blocking translation quality evaluation stage that scores generated target subtitles and shows the result in job detail.

**Architecture:** Add a new pipeline stage after `TARGET_SUBTITLE_EXPORT` that reads transcript and target subtitle segments, calls a `QualityEvaluationProvider`, stores one durable evaluation record per job/language, and exposes the latest result through `GET /api/jobs/{jobId}`. Provide deterministic demo and opt-in OpenAI providers so local Docker demos work without extra cost while live OpenAI evaluation can be enabled explicitly.

**Tech Stack:** Java 21, Spring Boot, Flyway, JdbcClient, JUnit 5, MockMvc, React, TypeScript, Vitest, Vite.

## Global Constraints

- Use feature branch `translation-quality-evaluation-mvp`.
- Keep evaluation non-blocking by default: provider failures create a failed evaluation record and timeline event but do not fail the localization job.
- Do not add user accounts, authentication, admin dashboards, budget enforcement, prompt-template CRUD, or cache behavior in this slice.
- Do not log or expose raw OpenAI prompts, API keys, object storage credentials, or local media paths.
- Keep `.env` secrets uncommitted; only `.env.example` and docs may include placeholder names.

---

## Design Summary

Recommended approach: add a first-class `quality_evaluations` table and `QualityEvaluationPipelineStage`. This keeps evaluation queryable without parsing artifacts and fits the existing repository/service/VO pattern.

Alternative 1, store evaluation only as a JSON artifact: simpler schema, but job detail cannot filter or summarize quality without downloading artifact content.

Alternative 2, fold evaluation into `ModelCallVo`: fewer tables, but model-call records describe provider calls, not domain quality outcomes. A separate evaluation record makes the product result explicit.

## Files To Create Or Modify

- Create `LinguaFrame/src/main/resources/db/migration/V8__create_quality_evaluations.sql`: durable evaluation result table.
- Create `QualityEvaluation*` BO/entity/VO/repository/service/provider classes under `com.linguaframe.job`.
- Modify `LinguaFrameProperties`, `application.yaml`, `application-local.yaml`, `.env.example`, and `docker-compose.yml`: evaluation enable/provider/OpenAI config.
- Modify `LocalizationJobStage`, `ModelCallOperation`, `ModelCallAuditServiceImpl`, and pipeline stage ordering.
- Modify `LocalizationJobVo`, `LocalizationJobQueryServiceImpl`, and `LocalizationJobControllerTests`: expose latest evaluation in job detail.
- Modify `frontend/src/domain/jobTypes.ts`, `frontend/src/App.tsx`, and tests: render quality score, verdict, and issues.
- Update `README.md`, `docs/product/roadmap.md`, `docs/progress/decisions.md`, and `docs/progress/execution-log.md`.

## Task 1: Persist And Query Quality Evaluations

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V8__create_quality_evaluations.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/QualityEvaluationRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/QualityEvaluationVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/QualityEvaluationRepository.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/repository/QualityEvaluationRepositoryTests.java`

**Interfaces:**
- Produces: `QualityEvaluationRepository#save(QualityEvaluationRecord)`, `findLatestByJobIdAndLanguage(String,String)`, and `findByJobId(String)`.

- [ ] Add a failing repository test that inserts two evaluations for the same job/language and expects the latest by `created_at`.
- [ ] Add `quality_evaluations` with fields: `id`, `job_id`, `language`, `score`, `verdict`, `completeness`, `readability`, `timing_preservation`, `naturalness`, `issues_json`, `suggested_fixes_json`, `status`, `safe_error_summary`, `created_at`.
- [ ] Implement repository row mapping with `JdbcClient`.
- [ ] Run `mvn -pl LinguaFrame -Dtest=QualityEvaluationRepositoryTests test`.
- [ ] Commit: `Add quality evaluation persistence`.

## Task 2: Add Evaluation Domain Service And Providers

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Create: `QualityEvaluationProvider.java`, `QualityEvaluationService.java`
- Create: `QualityEvaluationRequestBo.java`, `QualityEvaluationResultBo.java`
- Create: `DemoQualityEvaluationProvider.java`
- Create: `OpenAiQualityEvaluationProvider.java`
- Create tests for demo/openai providers and service.

**Interfaces:**
- Consumes: transcript segments and subtitle segments.
- Produces: `QualityEvaluationService#evaluateAndStore(jobId, language, sourceSegments, targetSegments)` and `#latestForJob(jobId)`.

- [ ] Add failing configuration tests for `linguaframe.evaluation.enabled`, provider, OpenAI base URL/model/timeout.
- [ ] Add `ModelCallOperation.EVALUATION` and cost estimation through translation token rates.
- [ ] Implement demo provider returning deterministic score `92`, verdict `GOOD`, and concise issues/fixes.
- [ ] Implement OpenAI provider using `/v1/responses` JSON schema output with fields matching `QualityEvaluationResultBo`; record success/failure model calls with prompt version `openai-translation-quality-evaluation-v1`.
- [ ] Ensure OpenAI provider fails fast when enabled with missing `OPENAI_API_KEY` or `OPENAI_EVALUATION_MODEL`.
- [ ] Run focused provider/service tests.
- [ ] Commit: `Add quality evaluation providers`.

## Task 3: Wire Evaluation Into Pipeline And Job Detail API

**Files:**
- Modify: `LocalizationJobStage.java`
- Create: `QualityEvaluationPipelineStage.java`
- Modify: `LocalizationJobVo.java`
- Modify: `LocalizationJobQueryServiceImpl.java`
- Modify: `LocalizationJobControllerTests.java`
- Modify: `LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- Consumes: `TranscriptService#listTranscript`, `SubtitleService#listSubtitles`, and `QualityEvaluationService`.
- Produces: job detail field `qualityEvaluation: QualityEvaluationVo | null`.

- [ ] Add failing execution test: when translation and evaluation are enabled, timeline includes `TRANSLATION_QUALITY_EVALUATION` after `TARGET_SUBTITLE_EXPORT` and before TTS/burn-in/summary.
- [ ] Implement stage that returns immediately when `linguaframe.evaluation.enabled=false` or no target subtitles exist.
- [ ] Catch provider failures inside the stage, store failed evaluation, and do not throw.
- [ ] Add controller test asserting `GET /api/jobs/{jobId}` includes `qualityEvaluation.score`, `verdict`, `issues`, and `suggestedFixes`.
- [ ] Run `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test`.
- [ ] Commit: `Expose translation quality evaluation`.

## Task 4: Show Quality Evaluation In React Demo

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `LocalizationJob.qualityEvaluation`.
- Produces: a `Quality evaluation` panel in selected job detail.

- [ ] Add failing App test with a job fixture containing score `92`, verdict `GOOD`, one issue, and one suggested fix.
- [ ] Extend TypeScript types with `QualityEvaluation`.
- [ ] Render score, verdict, dimension scores, issues, suggested fixes, and failed-evaluation warning when `status=FAILED`.
- [ ] Keep empty state concise: `No quality evaluation recorded yet.`
- [ ] Run `npm run test:run -- App` and `npm run test:run`.
- [ ] Commit: `Show quality evaluation in demo UI`.

## Task 5: Document And Verify The Feature Slice

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Verification Commands:**
- `mvn -pl LinguaFrame -Dtest=QualityEvaluationRepositoryTests,ModelCallAuditServiceTests test`
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test`
- `mvn -pl LinguaFrame test -q`
- `npm run test:run`
- `npm run build`
- `docker compose --env-file .env.example config`

- [ ] Document configuration keys and explain that evaluation is optional and non-blocking.
- [ ] Update roadmap Phase 11 status as started for quality evaluation MVP.
- [ ] Record the non-blocking evaluation decision in `docs/progress/decisions.md`.
- [ ] Record red/green validation evidence in `docs/progress/execution-log.md`.
- [ ] Commit: `Document quality evaluation MVP`.

## Completion Checklist

- [ ] `GET /api/jobs/{jobId}` returns latest quality evaluation when present.
- [ ] Demo jobs can run with deterministic local evaluation and without paid API calls.
- [ ] OpenAI evaluation is opt-in and records model-call usage.
- [ ] Evaluation failures are visible but do not fail otherwise completed jobs.
- [ ] Frontend shows quality score/verdict/issues in selected job detail.
- [ ] Branch is merged back to `main` after verification.
