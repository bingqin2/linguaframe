# Owner Quota And Budget Preflight Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an owner-scoped quota and budget preflight workspace so LinguaFrame can reject expensive new uploads before storage, queue dispatch, FFmpeg, or provider calls.

**Architecture:** Build on the configured demo owner boundary instead of adding real accounts. A new owner quota service will aggregate owner job counts and same-day model-call spend, expose safe readiness/preflight metadata, and block upload creation when configured owner limits are exceeded. Browser and terminal surfaces will show the current owner quota state without exposing tokens, raw media paths, object keys, provider payloads, or uploaded media bytes.

**Tech Stack:** Spring Boot MVC, JDBC repositories, JUnit 5, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend config, repository aggregation, owner preflight API, upload blocking, browser UI, terminal evidence, tests, docs, validation, commit, and merge back to `main`.
- Do not build public sign-up, JWT issuance, password login, billing accounts, payment flows, or tenant administration in this slice.
- Do not let clients choose arbitrary owner IDs through headers or request parameters. The effective owner remains `DemoOwnerIdentityService.currentOwnerId()`.
- Upload creation must reject over-quota owners before object storage writes, job inserts, dispatch events, FFmpeg stages, or OpenAI calls.
- Validation/preflight responses must be metadata-only and must not include demo tokens, OpenAI keys, object storage credentials, raw local media paths, object keys, provider payloads, transcript/subtitle text, or media bytes.
- Operator-wide dashboards may remain aggregate, but owner-facing upload and preflight checks must use the configured owner identity.

---

## Current Context

- Videos and jobs now persist `owner_id`, and owner-facing media/job reads are scoped to the configured demo owner.
- Existing safeguards include file size/duration validation, Redis upload rate limiting, per-job budget guard, daily demo budget guard, and runtime readiness metadata.
- There is no owner-facing preflight that combines active job count, queued/running pressure, and same-day estimated spend before upload creation.
- Stage 3 still requires quota checks before expensive processing; this slice creates the private-demo version without real multi-user auth.

## Task 1: Backend Owner Quota Configuration And Aggregation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `.env.example`
- Modify: `.env.private-demo.example`
- Modify: `.env.openai-demo.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/quota/OwnerQuotaPreflightService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/quota/OwnerQuotaPreflightServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/quota/OwnerQuotaExceededException.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/quota/OwnerQuotaPreflightVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/quota/OwnerQuotaLimitVo.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/quota/OwnerQuotaPreflightServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- Add `linguaframe.owner-quota.enabled`, default `false`.
- Add `linguaframe.owner-quota.max-active-jobs`, default `0` meaning unlimited.
- Add `linguaframe.owner-quota.max-queued-jobs`, default `0` meaning unlimited.
- Add `linguaframe.owner-quota.daily-budget-guard-enabled`, default `false`.
- Add `linguaframe.owner-quota.max-daily-cost-usd`, default `0.00000000` meaning unlimited.
- Add repository aggregations for an owner:
  - active jobs: `QUEUED`, `PROCESSING`, `RETRYING`.
  - queued jobs: `QUEUED`, `RETRYING`.
  - same-day estimated spend using existing model-call budget identity or owner id, documented as private-demo approximation.
- `OwnerQuotaPreflightVo` reports `ownerId`, `enabled`, `allowed`, counts, daily spend, limits, and safe blocking reasons.

- [x] Write failing tests for owner quota property defaults, configured binding, and blank-safe values.
- [x] Write failing repository tests for owner-scoped active/queued job counts across mixed owners and statuses.
- [x] Write failing service tests for allowed state, disabled state, active-job block, queued-job block, and daily-budget block.
- [x] Implement properties, env bindings, repository aggregation, service logic, and metadata-only VO types.
- [x] Run `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,LocalizationJobRepositoryTests,OwnerQuotaPreflightServiceTests test`.

## Task 2: Upload Preflight API And Upload Blocking

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/error/ApiExceptionHandler.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify as needed: runtime VO types under `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`

**Interfaces:**
- Add `GET /api/media/uploads/preflight` returning owner quota state.
- Extend `POST /api/media/uploads/validate` to return existing file validation only; browser can combine it with preflight instead of overloading file validation with owner state.
- `POST /api/media/uploads` calls `OwnerQuotaPreflightService.requireUploadAllowed()` after file validation and option parsing, but before object storage writes and database inserts.
- Quota failures return HTTP `409` with code `OWNER_QUOTA_EXCEEDED` and safe reasons.
- Runtime readiness includes sanitized owner quota settings and feature flag state.

- [x] Write failing controller tests for `GET /api/media/uploads/preflight`.
- [x] Write failing service tests proving over-quota upload does not store an object, save video/job rows, or enqueue dispatch.
- [x] Write failing API error test for `OWNER_QUOTA_EXCEEDED` response shape.
- [x] Add OpenAPI and runtime-route contract coverage for the new preflight route.
- [x] Implement the route, upload guard, error mapping, and runtime readiness metadata.
- [x] Run `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests,MediaUploadServiceTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test`.

## Task 3: Browser Owner Quota Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `getOwnerQuotaPreflight()` in the frontend API layer.
- Show an `Owner quota` panel near upload/readiness surfaces with owner id, active jobs, queued jobs, daily estimated spend, configured limits, and blocking reasons.
- Refresh quota preflight on page load, after validation, after successful upload, and after upload failure.
- Disable or clearly block upload when the backend preflight says `allowed=false`, while still allowing file validation for local media checks.
- Keep the UI compact and operational; no marketing copy or instructional panels.

- [x] Write failing API tests for `getOwnerQuotaPreflight()`.
- [x] Write failing App tests for allowed quota state, blocked quota state, refresh after upload, and safe reason rendering.
- [x] Add TypeScript types and API function.
- [x] Render the owner quota panel and upload-blocking state.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.

## Task 4: Terminal Demo Evidence And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/owner-quota-preflight.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/099-owner-quota-budget-preflight-workspace.md`

**Interfaces:**
- Terminal helper downloads `/api/media/uploads/preflight` and prints metadata-only owner quota summary.
- Demo script exits non-zero when preflight is blocked unless explicitly run in report-only mode.
- Docs explain that owner quota preflight is a private-demo abuse/cost guard, not public billing or real user auth.
- Docs include `.env` examples for enabling active job, queued job, and daily budget limits.

- [x] Write failing script tests for preflight download, summary printing, blocked-state exit behavior, and redaction.
- [x] Implement shell helpers and `scripts/demo/owner-quota-preflight.sh`.
- [x] Update README, deployment docs, Docker E2E guide, roadmap, target state, decisions, and execution log.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/owner-quota-preflight.sh`.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `git diff --check`.
- [ ] Commit on `owner-quota-budget-preflight`, merge back to `main`, and record the merge in the execution log.
