# Upload Execution Plan Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a pre-upload execution-plan workspace that tells the operator whether the selected file/profile can run now, what stages will execute, what it may cost, how long it may take, what blocks upload, and which demo command to run next.

**Architecture:** Add a backend read-only service and `POST /api/media/uploads/execution-plan` endpoint that composes upload validation, existing cost estimation, upload readiness, owner quota, runtime dependency metadata, and selected demo profile into one safe plan. Add a React upload-adjacent panel and a terminal script that expose the same plan before object storage writes, queue dispatch, FFmpeg work, or OpenAI calls.

**Tech Stack:** Spring Boot, Java 21 records/services/controllers, MockMvc and JUnit 5, React + TypeScript + Vite, Vitest, Bash + curl + python3.

## Global Constraints

- Keep this feature read-only: no upload rows, object storage writes, dispatch events, FFmpeg work, or provider calls.
- Do not expose local filesystem paths, object keys, API keys, demo tokens, provider payloads, transcript text, subtitle text, or media bytes.
- Keep the slice complete: backend endpoint, tests, frontend panel, terminal script, README/progress docs, and validation.
- Preserve existing upload, validation, readiness, owner quota, and cost-estimate behavior.
- Merge the verified feature branch back to `main` after implementation.

---

## Scope

This feature turns the current separate pre-upload surfaces into one actionable plan:

- File safety: supported content type, size, duration, and validation message.
- Profile/options: resolved target language, translation style, subtitle style, glossary entry count/hash, polishing mode, TTS voice, and demo profile.
- Execution stages: ordered stage list from local media preparation through transcription, translation, polishing, evaluation, TTS, subtitle burn-in, and dubbed/reviewed outputs where applicable.
- Cost: reuse `UploadCostEstimateService` output for point/range estimates, budget rows, cache notes, and paid provider stage costs.
- Readiness: include upload readiness checks, owner quota checks, live dependency blockers, and whether upload should be disabled.
- Time estimate: conservative duration range based on video length and enabled stages, clearly marked as an estimate.
- Next action: a single `READY`, `ATTENTION`, or `BLOCKED` status plus recommended next command.

Out of scope:

- No automatic upload after plan generation.
- No billing ledger or exact OpenAI pricing sync.
- No new provider calls for more accurate duration/cost.
- No queue or worker routing changes.

## Backend Design

Create `UploadExecutionPlanService` under `com.linguaframe.media.service`. Its implementation composes existing services instead of duplicating behavior:

- `UploadCostEstimateService.estimate(file, options)` for validation, resolved options, stages, budgets, and cost.
- `DemoUploadReadinessService.getReadiness(demoProfileId)` for readiness checks and required actions.
- `OwnerQuotaPreflightService.getPreflight()` for owner quota metadata.
- `RuntimeDependencyService` or existing runtime readiness service, if directly available, for dependency names/status; if not cleanly injectable, rely on readiness check details already surfaced by `DemoUploadReadinessService`.

Create VOs:

- `UploadExecutionPlanVo`
- `UploadExecutionPlanStageVo`
- `UploadExecutionPlanGateVo`
- `UploadExecutionPlanCommandVo`

Endpoint:

```http
POST /api/media/uploads/execution-plan
Content-Type: multipart/form-data
```

Form fields mirror upload and cost estimate:

- `file`
- `targetLanguage`
- `ttsVoice`
- `translationStyle`
- `subtitleStylePreset`
- `translationGlossary`
- `subtitlePolishingMode`
- `demoProfileId`

The response must include:

- `overallStatus`
- `recommendedNextAction`
- safe file facts and validation status
- resolved options/profile
- `estimatedCostUsdLower`, `estimatedCostUsd`, `estimatedCostUsdUpper`
- `estimatedDurationSecondsLower`, `estimatedDurationSecondsUpper`
- ordered `stages`
- `gates`
- `commands`
- `safetyNotes`

## Frontend Design

Add an `Execution plan` panel in the upload form, near `Cost estimate`, because it is the higher-level decision surface. The panel should:

- Use the selected file and current form options.
- Show status, next action, estimated time/cost, file duration, profile, and key gates.
- Show stage rows with `LOCAL`, `PAID`, `DISABLED`, or `BLOCKED` indicators.
- Include a refresh button and non-blocking error handling.
- Clear stale plan data when the file changes.
- Not block upload independently; upload remains blocked by existing readiness/quota logic.

Add TypeScript API/types:

- `estimateUploadExecutionPlan(...)`
- `UploadExecutionPlan`, `UploadExecutionPlanStage`, `UploadExecutionPlanGate`, `UploadExecutionPlanCommand`

## Terminal Script

Add `scripts/demo/upload-execution-plan.sh`.

Defaults:

- sample path: `LINGUAFRAME_UPLOAD_PLAN_SAMPLE_PATH`, then `LINGUAFRAME_TEARS_SAMPLE_PATH`, then `LINGUAFRAME_DEMO_SAMPLE_PATH`, then `/tmp/linguaframe-demo/sample.mp4`
- profile: `LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase`
- output: `/tmp/linguaframe-demo/upload-execution-plan.json`

The script should:

- wait for backend health
- fail if sample path is missing
- call `/api/media/uploads/execution-plan`
- print metadata-only summary lines:
  - `uploadExecutionPlanStatus`
  - `uploadExecutionPlanFile`
  - `uploadExecutionPlanDurationSeconds`
  - `uploadExecutionPlanEstimatedCostUsd`
  - `uploadExecutionPlanEstimatedDurationSeconds`
  - `uploadExecutionPlanRecommendedNextAction`
  - one line per blocking gate
  - one line per paid stage

## Testing Plan

Backend:

- `UploadExecutionPlanServiceTests`
  - valid upload returns `READY`, stages, cost, time range, gates, and commands.
  - invalid validation returns `BLOCKED` without provider stages marked runnable.
  - blocked readiness or owner quota appears as a blocking gate and changes overall status.
- `MediaUploadControllerTests`
  - execution-plan endpoint returns JSON for valid multipart input.
  - invalid file returns HTTP 200 with `overallStatus=BLOCKED`.

Frontend:

- `linguaframeApi.test.ts`
  - multipart request includes file and all option fields.
  - response is parsed into execution plan type.
- `App.test.tsx` if existing upload panel tests are stable enough:
  - clicking `Execution plan` calls the API and renders next action.

Scripts/docs:

- `bash -n scripts/demo/upload-execution-plan.sh scripts/demo/lib/linguaframe-demo.sh`
- README documents browser and terminal usage.
- `docs/progress/execution-log.md` records implementation and validation results.

Full verification target:

```bash
mvn -pl LinguaFrame -Dtest=UploadExecutionPlanServiceTests,MediaUploadControllerTests#estimatesUploadExecutionPlanBeforeCreatingUpload+returnsBlockedExecutionPlanForInvalidFile test
npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts
npm --prefix frontend run build
bash -n scripts/demo/upload-execution-plan.sh scripts/demo/lib/linguaframe-demo.sh
git diff --check
```

Run `mvn -pl LinguaFrame test` if local dependencies are healthy; if Redis/dependency health is down, record the environment-specific failures instead of hiding them.

## Implementation Tasks

### Task 1: Backend Execution Plan Service

- [ ] Add failing service tests for valid, invalid, and blocked readiness/quota cases.
- [ ] Add VO records and `UploadExecutionPlanService`.
- [ ] Implement service by composing cost estimate, readiness, and owner quota.
- [ ] Add deterministic time-estimate calculation based on upload duration and enabled paid/local stages.
- [ ] Run focused service tests.

### Task 2: Backend Controller Endpoint

- [ ] Add failing MockMvc tests for `/api/media/uploads/execution-plan`.
- [ ] Add controller dependency and endpoint with same multipart fields as upload/cost-estimate.
- [ ] Ensure invalid file returns HTTP 200 with `BLOCKED`, not a thrown upload error.
- [ ] Run focused controller tests.

### Task 3: Frontend API And Panel

- [ ] Add TypeScript execution plan types.
- [ ] Add `estimateUploadExecutionPlan` API function with shared multipart builder.
- [ ] Add API unit test.
- [ ] Add App state, handler, and upload-adjacent panel.
- [ ] Clear stale plan state on file change.
- [ ] Run frontend API tests and build.

### Task 4: Script And Docs

- [ ] Add `scripts/demo/upload-execution-plan.sh`.
- [ ] Update README demo/pre-upload sections.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Run shell syntax validation and diff check.

### Task 5: Commit, Merge, Verify

- [ ] Commit implementation on `upload-execution-plan-preflight`.
- [ ] Merge back to `main`.
- [ ] Re-run focused backend/frontend/script validation on `main`.
- [ ] Record any full-suite environment failures honestly.
