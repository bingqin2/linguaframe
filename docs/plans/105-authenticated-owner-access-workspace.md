# Authenticated Owner Access Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the new local account JWT identity a real owner workspace boundary across job history, job detail, media metadata, artifacts, browser state, and terminal smoke validation.

**Architecture:** Reuse the local owner account JWT and existing demo token compatibility, but make every owner-facing read path resolve through `DemoOwnerIdentityService.currentOwnerId()`. Existing repository owner filters remain the source of truth; this slice closes gaps in controllers/services, adds explicit unauthorized/not-found behavior, and exposes sanitized ownership mode metadata for browser and terminal confidence.

**Tech Stack:** Spring Boot MVC, JdbcClient repositories, JUnit 5 + MockMvc, React + TypeScript + Vitest, Bash demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend access boundaries, frontend owner-workspace visibility, terminal smoke coverage, OpenAPI/docs, validation, commit, and merge back to `main`.
- Do not build public registration, password reset, roles, billing, database-backed multi-user accounts, OAuth, tenant administration, or user-configurable owner ids.
- Existing private demo token header/cookie flows must keep working for Swagger, curl, scripts, SSE, artifact downloads, media previews, and demo reports.
- Bearer JWT and demo-token access must produce the same owner-facing data when they resolve to the same configured owner id.
- Cross-owner reads must return safe 404/empty responses without exposing whether another owner owns the resource.
- Never expose passwords, JWT signing secrets, demo tokens, bearer tokens, local paths, provider payloads, transcript text, subtitle text, object keys, or media bytes in auth/workspace metadata.

---

## Current Context

- `docs/plans/104-local-account-jwt-auth-workspace.md` added local account login, bearer tokens, and demo-token compatibility.
- Upload already persists `ownerId` on videos and localization jobs.
- Several repositories already include owner-scoped methods such as `findByIdAndOwnerId`, `findSummariesByOwnerId`, and `findSummariesByVideoIdAndOwnerId`.
- The next product gap is making authenticated owner scope visible and enforced across the complete owner workspace, not merely at login.

## Task 1: Backend Owner Workspace Contract

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/LocalizationJobService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthSessionStatusVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/security/LocalAuthControllerTests.java`

**Interfaces:**
- `GET /api/jobs` returns only jobs owned by the current owner identity.
- `GET /api/jobs/{jobId}` returns 404 when the job id exists for another owner.
- Job detail response keeps `ownerId` visible as metadata for the authenticated owner.
- `GET /api/media/{videoId}` and source-media metadata/download routes use the current owner id and return safe 404 across owners.
- `/api/auth/session` includes sanitized `ownershipScope` so browser and scripts can distinguish `LOCAL_AUTH_OWNER` from `DEMO_OWNER`.

- [x] Write failing MockMvc tests for bearer-auth job list filtering, bearer-auth job detail 404 across owners, demo-token compatibility, and session `ownershipScope`.
- [x] Write failing media controller tests for bearer-auth source metadata/download ownership and cross-owner safe 404.
- [x] Route job and media read services through current owner identity everywhere owner-facing reads occur.
- [x] Extend auth session status with `ownershipScope` without adding secrets or token fields.
- [x] Run `mvn -pl LinguaFrame -Dtest=AuthenticatedOwnerJobAccessTests,AuthenticatedOwnerMediaAccessTests,LocalAuthControllerTests test`.

## Task 2: Artifact, Preview, And Report Access Boundaries

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobArtifactService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobArtifactServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/job/controller/AuthenticatedOwnerJobAccessTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Interfaces:**
- Artifact listing, preview, download, diagnostics, evidence, and generated report routes first verify the selected job belongs to the current owner.
- Operator dashboard remains private-demo/operator scoped but reports the active owner identity in metadata and does not leak cross-owner job details.
- Safe 404 is used for cross-owner job/artifact/report access; no response says “belongs to another owner”.

- [x] Write failing tests proving artifact list/download/archive routes return 404 for a valid job id owned by another owner.
- [x] Write failing diagnostics/report tests proving cross-owner job evidence routes are safe 404.
- [x] Write failing operator dashboard tests proving recent job rows are scoped to current owner when owner identity is authenticated.
- [x] Add shared owner-check helper logic in services rather than duplicating controller checks.
- [x] Run `mvn -pl LinguaFrame -Dtest=AuthenticatedOwnerJobAccessTests,AuthenticatedOwnerOperatorDashboardTests,OperatorDashboardControllerTests test`.

## Task 3: Browser Owner Workspace Visibility

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Frontend `AuthSessionStatus` includes `ownershipScope`.
- Header shows the authenticated workspace owner id and ownership mode.
- Job history refreshes after login/logout and uses the bearer header automatically.
- Cross-owner 404s are rendered as a neutral “not found in this workspace” message, not as a token/secret/config hint.

- [x] Write failing API type/header tests for `ownershipScope` and bearer-auth job history refresh.
- [x] Write failing App tests for login refreshing job history and workspace mode display.
- [x] Implement owner workspace display and refresh flow without duplicating demo-token UI state.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts App.test.tsx -t "local account|owner workspace|bearer token"`.

## Task 4: Terminal Owner Workspace Smoke

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/owner-workspace-smoke.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/deployment/private-demo.md`

**Interfaces:**
- `scripts/demo/owner-workspace-smoke.sh` logs in when local auth is configured, fetches session, job list, upload readiness, and runtime dependencies with bearer auth, and prints only metadata lines:
  - `ownerWorkspaceAuthMode=...`
  - `ownerWorkspaceOwnerId=...`
  - `ownerWorkspaceOwnershipScope=...`
  - `ownerWorkspaceJobCount=...`
  - `ownerWorkspaceUploadReadiness=...`
- The script exits 0 when local auth is disabled or unconfigured and clearly reports it skipped bearer validation.
- The script never prints credentials, JWTs, demo tokens, object keys, local media paths, or transcript/subtitle text.

- [x] Write failing shell tests for owner workspace summary redaction, bearer-header request shape, disabled-auth skip behavior, and metadata-only output.
- [x] Implement shared helper functions and `owner-workspace-smoke.sh`.
- [x] Update README, smoke checklist, and private-demo docs with the owner workspace smoke flow.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/owner-workspace-smoke.sh`.

## Task 5: Full Validation, Documentation, And Merge

**Files:**
- Modify: `docs/product/spec.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/105-authenticated-owner-access-workspace.md`

**Interfaces:**
- Document this feature as an owner-workspace boundary for the local account bridge, not real multi-user SaaS.
- Record validation evidence and any red/green failures in the execution log.
- Merge the verified feature branch back to `main`.

- [x] Update product docs, decisions, execution log, and this plan.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on the feature branch, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
