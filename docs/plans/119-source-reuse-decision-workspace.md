# Source Reuse Decision Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn duplicate-source detection into an actionable reuse workspace that tells the operator whether to reuse an existing completed run, wait for an active run, or intentionally upload a new run, with safe evidence links in backend, frontend, and terminal demos.

**Architecture:** Build on `UploadSourceReuseService` and `UploadExecutionPlanVo.sourceReuse`. Add a read-only source-reuse decision VO that enriches duplicate candidates with curated evidence/package routes and operator actions. Surface the same decision in the upload execution-plan panel and a terminal script so the next action is clear before storage, queue dispatch, FFmpeg, or OpenAI work.

**Tech Stack:** Java 21, Spring Boot, JdbcClient, JUnit 5, React + Vite + TypeScript, shell demo scripts.

## Global Constraints

- Keep this feature read-only; it must not upload media, create jobs, dispatch queues, call providers, or mutate artifacts.
- Scope all reuse candidates to the current owner. Never expose another owner’s job, video, source object key, or artifact metadata.
- Do not expose raw transcript text, raw subtitle text, local media paths, object storage keys, provider payloads, tokens, or credentials.
- Preserve current upload behavior. Duplicate matches are advisory and must not block manual upload.
- If there are no candidates, the workspace must clearly recommend uploading a new source.

---

## Task 1: Backend Source Reuse Decision Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseDecisionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseDecisionActionVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseDecisionLinkVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseCandidateVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadSourceReuseVo.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadSourceReuseDecisionServiceTests.java`

**Interfaces:**
- `UploadSourceReuseDecisionVo` contains `status`, `headline`, `summary`, `recommendedAction`, `recommendedExistingJobId`, `candidateCount`, `actions`, `links`, `safetyNotes`, and `sourceReuse`.
- `UploadSourceReuseDecisionActionVo` contains `id`, `label`, `kind`, `enabled`, `detail`, and `href`.
- `UploadSourceReuseDecisionLinkVo` contains `kind`, `label`, and `href`.
- `UploadSourceReuseCandidateVo` gains safe per-candidate links: `jobDetailHref`, `shareSheetHref`, `evidenceHref`, `demoRunPackageHref`, and `acceptanceGateHref`.

- [ ] Add VO records with explicit field names and no optional maps.
- [ ] Keep existing `UploadSourceReuseVo.empty()` backward-compatible with empty lists and no links.
- [ ] Add tests that assert completed, active, and no-candidate decisions can be represented without null pointer risks.
- [ ] Run `mvn -pl LinguaFrame -Dtest=UploadSourceReuseDecisionServiceTests test` and expect compile failure before the service exists.

## Task 2: Backend Decision Service and Execution Plan Enrichment

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/UploadSourceReuseDecisionService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadSourceReuseDecisionServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadSourceReuseServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadExecutionPlanVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadExecutionPlanServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadSourceReuseDecisionServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadExecutionPlanServiceTests.java`

**Interfaces:**
- `UploadSourceReuseDecisionService.decide(UploadSourceReuseVo sourceReuse)` returns `UploadSourceReuseDecisionVo`.
- `UploadExecutionPlanVo` gains `sourceReuseDecision`.

- [ ] Populate candidate evidence links inside `UploadSourceReuseServiceImpl` using safe API routes only:
  - `/api/jobs/{jobId}`
  - `/api/jobs/{jobId}/demo-share-sheet`
  - `/api/jobs/{jobId}/evidence/markdown/download`
  - `/api/jobs/{jobId}/demo-run-package/download`
  - `/api/jobs/{jobId}/demo-acceptance-gate`
- [ ] Implement decision statuses: `UPLOAD_NEW_SOURCE`, `REUSE_COMPLETED_RUN`, `WAIT_FOR_ACTIVE_RUN`, and `REVIEW_DUPLICATES`.
- [ ] For a completed recommended job, add enabled actions for opening job evidence, downloading the demo run package, and refreshing the upload plan.
- [ ] For an active recommended job, add actions for opening the active job and waiting for completion.
- [ ] For no candidates, add an enabled action to continue upload and a note that no prior source match was found.
- [ ] Embed `sourceReuseDecision` in execution-plan responses.
- [ ] Run `mvn -pl LinguaFrame -Dtest=UploadSourceReuseDecisionServiceTests,UploadExecutionPlanServiceTests test`.

## Task 3: API Contract, Frontend Types, and Upload UI

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `/api/media/uploads/execution-plan` includes `sourceReuseDecision`.
- The browser upload form shows a `Source reuse decision` card inside the execution plan panel.

- [ ] Add controller JSON assertions for `sourceReuseDecision.status`, `headline`, `actions`, and safe links.
- [ ] Extend TypeScript types for the decision, action, and link records.
- [ ] Update API fixtures to include a completed-run reuse decision.
- [ ] Replace the current raw source reuse block with a decision-first UI: headline, status pill, recommended action, primary safe links, and the top three candidates.
- [ ] Keep hash and candidate count visible, but make the action the dominant information.
- [ ] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests#estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test`.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts`.
- [ ] Run `npm run build`.

## Task 4: Terminal Demo Script and Documentation

**Files:**
- Modify: `scripts/demo/upload-execution-plan.sh`
- Create: `scripts/demo/source-reuse-decision.sh`
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `scripts/demo/source-reuse-decision.sh` calls the same execution-plan endpoint and prints only `sourceReuseDecision*` lines.
- Existing `scripts/demo/upload-execution-plan.sh` also prints the decision status, headline, recommended job, and primary links.

- [ ] Add terminal output fields: `sourceReuseDecisionStatus`, `sourceReuseDecisionHeadline`, `sourceReuseDecisionRecommendedAction`, `sourceReuseDecisionRecommendedExistingJobId`, and `sourceReuseDecisionLink`.
- [ ] Add a focused script for source reuse decision checks using the same sample path and upload options environment variables.
- [ ] Document how to prove the workflow: complete one upload, rerun source reuse decision with the same sample, then open the recommended job evidence/package links.
- [ ] Record validation commands and any failures in `docs/progress/execution-log.md`.
- [ ] Run `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/source-reuse-decision.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification and Merge

**Files:**
- Modify: `docs/plans/119-source-reuse-decision-workspace.md`

- [ ] Mark this plan checklist complete after implementation.
- [ ] Run focused backend tests:
  `mvn -pl LinguaFrame -Dtest=UploadSourceReuseDecisionServiceTests,UploadExecutionPlanServiceTests,MediaUploadControllerTests#estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test`
- [ ] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts`
  `npm run build`
  `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/source-reuse-decision.sh scripts/demo/lib/linguaframe-demo.sh`
- [ ] Run `git diff --check`.
- [ ] Commit as `Add source reuse decision workspace`.
- [ ] Merge the feature branch back to `main` after validation passes.
