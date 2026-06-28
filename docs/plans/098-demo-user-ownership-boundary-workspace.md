# Demo User Ownership Boundary Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a demo-user ownership boundary so uploaded videos, localization jobs, owner-facing job APIs, browser evidence, and terminal demo reports are tied to a safe effective owner before LinguaFrame moves toward public hosted usage.

**Architecture:** Keep the current private demo access token as the authentication gate, then add a separate configured owner identity for data ownership. Persist that owner on videos and localization jobs, scope owner-facing media/job reads by the effective owner, and expose only sanitized owner metadata in the browser and scripts. Operator-wide readiness and retention tools remain operator views.

**Tech Stack:** Spring Boot MVC, JDBC repositories, Flyway, JUnit 5, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: migration, backend identity model, owner-scoped APIs, frontend owner visibility, terminal evidence, tests, docs, validation, commit, and merge back to `main`.
- Do not build public sign-up, passwords, JWT issuance, OAuth, billing accounts, or tenant administration in this slice.
- Do not allow clients to choose arbitrary owner IDs through an unauthenticated request header. The effective owner comes from configuration now and can later be replaced by real auth.
- Do not expose demo tokens, API keys, raw local media paths, object storage keys, provider payloads, or uploaded/generated media bytes in owner metadata.
- Internal worker and operator maintenance flows must keep unscoped access where required for processing, retention, diagnostics, and private-demo operations.

---

## Current Context

- Stage 2 already has an optional owner-only demo access token, browser owner-session login/logout, upload limits, rate limiting, budget guard evidence, private-demo operations, launch rehearsal, and evidence gallery.
- Stage 3 requires real user authentication, per-user storage isolation, quota checks, budgets, abuse controls, storage retention, cleanup, and stronger worker recovery.
- Current `VideoRecord`, `LocalizationJobRecord`, `VideoRepository`, `LocalizationJobRepository`, `MediaUploadServiceImpl`, and `LocalizationJobQueryServiceImpl` do not persist or enforce an owner boundary.
- This feature should create the data and API boundary needed for future auth without changing the local single-owner demo flow.

## Task 1: Backend Owner Identity And Persistence

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V25__add_demo_owner_boundary.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoOwnerIdentityService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/ConfiguredDemoOwnerIdentityService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/entity/VideoRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/LocalizationJobRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/repository/VideoRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Test: repository and configuration tests under `LinguaFrame/src/test/java`.

**Interfaces:**
- Add `linguaframe.demo.owner-id`, defaulting to `demo-owner`.
- Add `owner_id` to `videos` and `localization_jobs`, backfilled to `demo-owner` and non-null.
- Add repository methods such as `findByIdAndOwnerId`, `findSummariesByOwnerId`, `countSummariesByOwnerId`, and `findSummariesByVideoIdAndOwnerId`.

- [x] Write failing tests for owner-id defaulting, migration columns, saving owner IDs, and owner-filtered repository queries.
- [x] Add the Flyway migration with indexes that support owner-scoped job and media lookups.
- [x] Add the configured demo owner identity service with normalization and safe defaults.
- [x] Extend video/job records and repository mappers without breaking worker/operator internal methods.
- [x] Run focused backend tests for configuration, repositories, and migrations.

## Task 2: Owner-Scoped Media And Job APIs

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/LocalizationJobQueryServiceImpl.java`
- Modify as needed: job artifact, transcript, subtitle, review, delivery, package, diagnostics, and source-media service boundaries that are reachable from owner-facing controllers.
- Test: controller and service tests for media upload, media detail/download, job list/detail, and package/evidence routes.

**Interfaces:**
- New uploads persist the configured owner ID on both the video and initial job.
- Owner-facing `GET /api/jobs`, `GET /api/jobs/{jobId}`, video detail, source media download, and job-scoped evidence routes reject or hide records owned by another owner.
- Worker execution, dispatch, retention cleanup, and operator dashboards continue to use explicit internal repository methods.

- [x] Write failing tests proving a job/video owned by a different owner is not returned through owner-facing APIs.
- [x] Persist owner ID during upload and job creation.
- [x] Scope job list, job detail, media detail, source media download, and derived package/evidence reads through the current owner.
- [x] Avoid caching cross-owner job detail by making the status cache owner-aware or bypassing cached details when ownership cannot be proven.
- [x] Preserve unscoped internal methods for workers, retention cleanup, and operator reports.
- [x] Run focused backend tests for media, job query, artifact/evidence, and OpenAPI coverage.

## Task 3: Browser Owner Session Visibility

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Extend `/api/demo-session` with sanitized owner metadata such as `ownerId` and `ownershipScope`.
- Show the active owner boundary near the existing owner-session/private-demo readiness surfaces.
- Keep upload, job history, job detail, evidence downloads, and manual job opening behavior unchanged for the default single-owner demo.

- [x] Write failing API tests for the new demo-session owner metadata.
- [x] Add frontend types and fetch handling for sanitized owner fields.
- [x] Render a compact owner-boundary indicator in the app without a marketing or instructional panel.
- [x] Add tests proving the UI shows the owner scope and keeps existing owner-session login/logout behavior intact.
- [x] Run focused frontend API and App tests.

## Task 4: Demo Scripts, Docs, And Validation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify relevant demo scripts that print job/session evidence.
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/098-demo-user-ownership-boundary-workspace.md`

**Interfaces:**
- Terminal demo reports include sanitized owner scope when available.
- Docs explain that `linguaframe.demo.owner-id` is a local/private-demo ownership boundary, not public authentication.
- Validation covers backend, frontend, scripts, formatting, commit, and merge.

- [x] Add script tests for owner metadata parsing and redaction.
- [x] Update demo and deployment docs with `.env` examples and the owner boundary limitation.
- [x] Update roadmap/target-state to mark the demo owner boundary as implemented after validation.
- [x] Record the architectural decision to separate the private demo access token from persisted owner identity.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [ ] Commit on `demo-user-ownership-boundary`, merge back to `main`, and record the merge in the execution log.
