# Demo Sample Media Catalog Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe demo sample media catalog so the owner can choose attributable public videos, verify configured local sample paths, and run the right demo command without hunting through README notes.

**Architecture:** Add a read-only backend operator aggregate that turns the existing demo references, runtime upload constraints, and configured sample-path environment into a structured catalog. Render the same catalog in React near upload readiness and export it from a terminal script, while keeping all local paths sanitized to availability/status metadata.

**Tech Stack:** Spring Boot MVC/services, JUnit 5, React + TypeScript + Vitest, Bash demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend catalog API, browser workspace, terminal script, full documentation, validation, commit, and merge back to `main`.
- Do not expose raw local filesystem paths, object keys, demo tokens, OpenAI keys, provider payloads, transcript/subtitle text, uploaded media bytes, or generated media bytes.
- The catalog is read-only. It must not download remote media, upload files, call OpenAI, mutate `.env`, start Docker, or create jobs.
- Keep attribution and license guidance explicit for each public sample source.
- Keep configured local sample paths useful without leaking them: show `CONFIGURED`, `MISSING`, or `UNCONFIGURED`, plus filename, extension, and size when available.
- Keep the upload duration limit visible so the owner can pick a complete file under the configured limit.

---

## Current Context

- `README.md` and `docs/product/demo-references.md` list Tears of Steel, Sintel, Big Buck Bunny/W3Schools, NASA, and Internet Archive sources, but the running app has no structured sample catalog.
- `scripts/demo/private-demo-preflight.sh` checks sample path presence, and `scripts/demo/docker-e2e-tears-of-steel-full.sh` defaults to `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`, but the browser does not explain which sample to use next.
- Upload readiness already reports runtime limits and paid-provider risk, so the sample catalog can be a neighboring read-only decision surface before uploads.

## Task 1: Backend Sample Catalog Contract

**Files:**
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSampleMediaCatalogVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSampleMediaItemVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSampleMediaCommandVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/vo/DemoSampleMediaConfiguredPathVo.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/service/DemoSampleMediaCatalogService.java`
- Add: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/DemoSampleMediaCatalogServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Add/modify focused backend tests under `LinguaFrame/src/test/java/com/linguaframe/operator/`

**Interfaces:**
- Add `GET /api/operator/demo-sample-media-catalog`.
- Return `generatedAt`, `overallStatus`, `uploadDurationLimitSeconds`, `recommendedSampleId`, `items`, `configuredPaths`, `commands`, `notesMarkdown`, and `documentationLinks`.
- Catalog items:
  - `tears-of-steel-casting`: local full-demo recommendation, Blender Studio attribution, complete current sample target, `scripts/demo/docker-e2e-tears-of-steel-full.sh`.
  - `big-buck-bunny-w3schools`: lightweight smoke fallback, W3Schools/Big Buck Bunny attribution, quick upload suitability.
  - `sintel`: longer context translation candidate, Blender Studio attribution, requires trimming or future longer limit if over 5 minutes.
  - `nasa-library`: technology-themed public source, requires per-asset metadata/license check.
  - `internet-archive-movies`: speech/documentary source, requires per-item license check.
- Configured paths include `LINGUAFRAME_DEMO_SAMPLE_PATH` and `LINGUAFRAME_TEARS_SAMPLE_PATH`, sanitized as basename/status/size only.
- Overall status is `READY` when a recommended local sample is configured and exists under the duration-limit guidance, `ATTENTION` when only remote references are available, and `BLOCKED` only when runtime metadata cannot be read.

- [x] Write failing service tests for ready local Tears sample metadata, unconfigured remote-only attention, missing configured path, duration-limit messaging, and local-path redaction.
- [x] Write failing controller/OpenAPI/runtime-contract tests for `GET /api/operator/demo-sample-media-catalog`.
- [x] Implement VO records, service composition from runtime properties and safe static catalog metadata, controller route, and runtime required-route entry.
- [x] Run focused backend tests for service, controller, OpenAPI, and runtime dependency coverage.

## Task 2: Browser Sample Catalog Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify/add focused frontend API tests under `frontend/src/api/`

**Interfaces:**
- Add a `Demo sample media` panel near upload readiness and demo runbook.
- Show overall status, duration limit, recommended sample, configured-path status without full paths, source/attribution links, recommended usage, and safe terminal commands.
- Refresh catalog on page load, owner-session changes, upload-readiness refresh, and manual refresh.
- Keep upload controls independent: catalog `ATTENTION` must not block upload.

- [x] Write failing API test for `getDemoSampleMediaCatalog()` with stored demo token header.
- [x] Write failing App test that renders the panel, recommended sample, attribution links, redacted path status, duration limit, and command copy.
- [x] Implement TypeScript interfaces, API client method, and React panel.
- [x] Run focused frontend tests.

## Task 3: Terminal Catalog Export Script

**Files:**
- Add: `scripts/demo/demo-sample-media-catalog.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `scripts/demo/private-demo-preflight.sh`

**Interfaces:**
- `scripts/demo/demo-sample-media-catalog.sh` downloads catalog JSON to `/tmp/linguaframe-demo/demo-sample-media-catalog.json`.
- Optional `LINGUAFRAME_DEMO_SAMPLE_CATALOG_REPORT_ONLY=true` keeps exit code 0 for `ATTENTION`; `BLOCKED` remains non-zero.
- Summary lines include `sampleCatalogOverall`, `sampleCatalogRecommended`, `sampleCatalogDurationLimitSeconds`, one line per configured path status, and one line per recommended command.
- `private-demo-preflight.sh` should mention the catalog script alongside upload readiness when sample paths are unconfigured or missing.

- [x] Add failing shell helper tests for catalog download, summary output, path redaction, command output, and blocked exit behavior.
- [x] Implement shared helper functions and the catalog script.
- [x] Integrate the catalog command into private-demo preflight guidance without changing upload or Docker behavior.
- [x] Run shell tests and syntax checks.

## Task 4: Documentation, Validation, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/product/demo-references.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/109-demo-sample-media-catalog-workspace.md`

**Validation Commands:**
- `mvn -pl LinguaFrame -Dtest=DemoSampleMediaCatalogServiceTests,OperatorDashboardControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `cd frontend && npm test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/demo-sample-media-catalog.sh scripts/demo/private-demo-preflight.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run build`
- `git diff --check`

- [x] Document when to use each sample source, how to verify local sample availability, and why the catalog does not download media automatically.
- [x] Record validation evidence in the execution log.
- [ ] Commit the completed feature branch, merge back to `main`, run post-merge focused validation, and record the merge.

## Plan Self-Review

- Scope is one complete user/operator-visible feature: a read-only sample media catalog available from backend, browser, terminal, and docs.
- The slice directly advances repeatable demos by making sample choice, attribution, local availability, duration limits, and commands visible before upload.
- The slice avoids remote downloads, provider calls, uploads, Docker mutation, public hosting behavior, and secret/path disclosure.
