# Demo Run Launcher Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a read-only demo run launcher that tells the owner exactly which sample/profile command to run next, what readiness gates apply, and which evidence artifacts should exist after the run.

**Architecture:** Add one backend operator aggregate that composes sample media catalog metadata, upload readiness, demo profile guidance, provider warnings, and static safe command templates. Render the aggregate in the React upload workspace and export the same contract through a terminal script. The launcher is command-oriented only: it does not upload media, start Docker, call OpenAI, mutate env files, or expose full local paths.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash, Python JSON parsing in existing demo helpers.

## Global Constraints

- This slice must be one complete user/operator-visible feature with backend API, frontend workspace, terminal script, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep all launcher outputs metadata-only. Never expose OpenAI keys, demo tokens, bearer tokens, passwords, object keys, raw local media paths, provider payloads, raw transcript/subtitle text, media bytes, or generated artifact bytes.
- The launcher may recommend commands, but must not run uploads, Docker, OpenAI checks, backups, restores, or cleanup from the backend API.
- The launcher must move the local demo goal forward by connecting sample selection to a repeatable full-video run and post-run evidence collection.

---

## Task 1: Backend Launcher Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoRunLauncherVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoRunLauncherCommandVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoRunLauncherEvidenceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoRunLauncherGateVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/DemoRunLauncherService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/DemoRunLauncherServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/DemoRunLauncherServiceTests.java`
- Modify tests: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`
- Modify tests: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify tests: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- Produces: `GET /api/operator/demo-run-launcher`.
- Produces: `DemoRunLauncherVo(generatedAt, overallStatus, recommendedSampleId, recommendedProfileId, recommendedNextCommand, gates, commands, expectedEvidence, notes)`.
- Consumes: `DemoSampleMediaCatalogService.getCatalog()` and upload-readiness service metadata if available from existing application services.
- Status rules: `READY` when the recommended sample path is configured and upload readiness is not blocked; `ATTENTION` when the sample is not configured or paid-provider checks need manual confirmation; `BLOCKED` when upload readiness/runtime contract is blocked.

- [x] Write failing service tests for ready Tears sample, sample-missing attention, blocked upload-readiness gate, safe command list, expected evidence list, and local-path redaction.
- [x] Write failing controller/OpenAPI/runtime-contract tests for `GET /api/operator/demo-run-launcher`.
- [x] Implement VO records, service composition, controller route, and runtime required-route entry.
- [x] Run focused backend tests:

```bash
mvn -pl LinguaFrame -Dtest=DemoRunLauncherServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

## Task 2: Browser Launcher Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify tests: `frontend/src/api/linguaframeApi.test.ts`

**Interfaces:**
- Add `getDemoRunLauncher()` in the frontend API client.
- Add a `Demo run launcher` panel near `Demo sample media` and `Upload readiness`.
- Show overall status, recommended sample/profile, readiness gates, copyable commands, and expected post-run evidence paths/routes.
- Keep upload controls independent: `ATTENTION` must not block upload; `BLOCKED` is advisory unless upload readiness itself blocks upload.

- [x] Write failing API test that confirms the launcher route sends the stored demo token header.
- [x] Write failing App test that renders the launcher panel, command, evidence outputs, readiness gates, and no local paths/secrets.
- [x] Implement TypeScript interfaces, API method, data loading, refresh behavior, and React panel.
- [x] Run focused frontend tests:

```bash
cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts
```

## Task 3: Terminal Launcher Script

**Files:**
- Create: `scripts/demo/demo-run-launcher.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/start-local-demo.sh`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Interfaces:**
- `scripts/demo/demo-run-launcher.sh` downloads launcher JSON to `/tmp/linguaframe-demo/demo-run-launcher.json`.
- Summary lines include `demoRunLauncherOverall`, `demoRunLauncherRecommendedSample`, `demoRunLauncherRecommendedProfile`, `demoRunLauncherNextCommand`, one line per gate, command, and expected evidence item.
- `READY` and `ATTENTION` exit 0; `BLOCKED` exits non-zero unless `LINGUAFRAME_DEMO_RUN_LAUNCHER_REPORT_ONLY=true`.
- `start-local-demo.sh` and `private-demo-preflight.sh` mention the launcher command as the next operator step without running it automatically.

- [x] Add failing shell tests for helper route, summary output, metadata-only redaction, command/evidence lines, and blocked exit behavior.
- [x] Implement shared helper functions and launcher script.
- [x] Add non-invasive guidance to startup/preflight scripts.
- [x] Run shell tests and syntax checks:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/demo-run-launcher.sh scripts/demo/start-local-demo.sh scripts/demo/private-demo-preflight.sh scripts/demo/lib/linguaframe-demo.sh
```

## Task 4: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/110-demo-run-launcher-workspace.md`

**Validation Commands:**
- `mvn -pl LinguaFrame -Dtest=DemoRunLauncherServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/demo-run-launcher.sh scripts/demo/start-local-demo.sh scripts/demo/private-demo-preflight.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `git diff --check`

- [x] Document where the launcher appears, how to use it before a full Tears run, and why it remains read-only.
- [x] Record validation evidence in the execution log.
- [x] Commit the completed feature branch, merge back to `main`, run post-merge focused validation, and record the merge.

## Plan Self-Review

- Scope is one complete feature: a run launcher exposed through backend, browser, terminal, and docs.
- The slice directly advances the demo by connecting sample selection to one repeatable next run and expected evidence outputs.
- The slice avoids auto-running paid or stateful operations and keeps safety boundaries explicit.
