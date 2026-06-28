# Demo Run Evidence Package Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add one reviewer-facing demo run package that bundles safe job evidence, quality evidence, diagnostics, delivery manifest, checklist, and session report into a single ZIP for browser and terminal demos.

**Architecture:** Keep existing artifact archives, evidence bundles, and reviewed handoff packages unchanged. Add a new metadata-only package service under the job boundary that composes existing safe services plus new backend-generated checklist/session report Markdown. Expose the package through a backend endpoint, a selected-job browser panel/action, and deterministic demo scripts.

**Tech Stack:** Spring Boot MVC, existing job query/evidence/delivery services, Java ZIP streams, Jackson, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete user-visible feature: backend package, frontend workspace, terminal integration, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Do not replace `GET /api/jobs/{jobId}/evidence/bundle/download` or `GET /api/jobs/{jobId}/handoff-package/download`; this package is a reviewer index bundle, not a media/artifact delivery bundle.
- The package must be metadata-only. It may include job ids, video ids, statuses, timestamps, route paths, usage/cache counts, quality scores, hashes already exposed safely, artifact filenames, and handoff readiness.
- Do not include uploaded source video bytes, generated media bytes, extracted audio, transcript text, generated subtitles, reviewed subtitle text, provider request/response payloads, object storage keys, API keys, demo tokens, raw local paths, or raw uploaded bytes.
- Tests must run without Docker, FFmpeg, OpenAI, RabbitMQ, Redis, network access, real object storage, or real browser clipboard/download APIs.

---

## Current Context

- `JobEvidenceBundleServiceImpl` currently creates a compact ZIP with `manifest.json`, `evidence.md`, and `diagnostics.json`.
- `JobHandoffPackageServiceImpl` currently creates a reviewed delivery ZIP with reviewed subtitle artifacts plus manifest/evidence/diagnostics.
- The browser already shows `Demo evidence`, `Demo handoff checklist`, `Demo session report`, `Delivery handoff`, and `Quality evaluation`, but reviewers still need multiple downloads to collect the whole run story.
- Terminal demo scripts now download `job-evidence.md`, `job-evidence.zip`, `delivery-manifest.md`, `handoff-package.zip`, `demo-session-report.md`, and `quality-evidence.md` separately.
- A larger next slice should make one shareable evidence package without changing media generation, reviewed delivery, or provider behavior.

## Task 1: Backend Demo Run Package

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredDemoRunPackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/DemoRunPackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoRunPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoRunPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interface:**
- Service: `DemoRunPackageService.openDemoRunPackage(String jobId): StoredDemoRunPackageBo`
- BO: `StoredDemoRunPackageBo(String filename, String contentType, long sizeBytes, InputStream inputStream)`
- Endpoint: `GET /api/jobs/{jobId}/demo-run-package/download`

- [x] **Step 1: Write failing backend package service test**

Create `DemoRunPackageServiceTests` with in-memory/fake collaborators.

Fixture a completed job with:

- `jobId=job-demo-package`
- `videoId=video-demo-package`
- `targetLanguage=zh-CN`
- `status=COMPLETED`
- `qualityEvaluation.score=92`
- safe diagnostics artifact metadata
- unsafe markers in fields not allowed in package text: `raw transcript text`, `raw subtitle text`, `provider request payload`, `job-artifacts/`, `/Users/example`, `OPENAI_API_KEY`, `private-demo-token`

Assert ZIP entries include exactly safe reviewer evidence:

```text
manifest.json
README.md
job-detail.json
diagnostics.json
evidence.md
quality-evidence.md
delivery-manifest.md
demo-handoff-checklist.md
demo-session-report.md
```

Assert combined UTF-8 text excludes all unsafe markers.

Run:

```bash
mvn -pl LinguaFrame -Dtest=DemoRunPackageServiceTests test
```

Expected: fail because service and BO do not exist.

- [x] **Step 2: Implement package BO, interface, and service**

`DemoRunPackageServiceImpl` should compose:

- `LocalizationJobQueryService.getDiagnosticsReport(jobId)` for safe job detail and diagnostics.
- `JobEvidenceReportService.buildMarkdownReport(jobId)` for backend evidence.
- `QualityEvaluationEvidenceService.openMarkdownEvidence(jobId)` for quality evidence bytes.
- `DeliveryManifestService.buildMarkdownManifest(jobId)` for delivery handoff metadata.

Generate new Markdown entries:

- `README.md`: package overview, entry list, safety statement, safe routes.
- `demo-handoff-checklist.md`: backend equivalent of the browser checklist using job status, terminal pipeline, reviewed subtitle readiness, media output availability, evidence links, quality status, usage, cache, and failure triage.
- `demo-session-report.md`: backend equivalent of the browser session report using input/job, generated outputs, handoff evidence, cost/cache, quality, and failure triage.

Return:

- filename: `linguaframe-job-{jobId}-demo-run-package.zip`
- content type: `application/zip`

- [x] **Step 3: Add controller endpoint and runtime/OpenAPI contracts**

Inject `DemoRunPackageService` into `LocalizationJobController`.

Add:

```java
@GetMapping("/{jobId}/demo-run-package/download")
@Operation(summary = "Download a safe demo run evidence package for a localization job")
```

Add required route:

```text
/api/jobs/{jobId}/demo-run-package/download
```

Update OpenAPI and runtime tests.

- [x] **Step 4: Verify backend slice**

Run:

```bash
mvn -pl LinguaFrame -Dtest=DemoRunPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

## Task 2: Browser Demo Run Package Workspace

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/App.test.tsx`

**Interface:**
- Add `linguaFrameApi.demoRunPackageDownloadUrl(jobId: string): string`.
- Add browser action/link in selected job view: `Download demo run package`.
- Add package route to demo evidence/session report/checklist link groups where appropriate.

- [x] **Step 1: Write failing frontend tests**

Extend selected-job tests so a completed job shows:

- `Download demo run package`
- href `/api/jobs/{jobId}/demo-run-package/download`
- package action appears near existing demo evidence/session report/handoff actions
- missing artifacts or missing quality evaluation do not hide the package link because the backend package can record `NOT_RECORDED` / `Missing`

Run:

```bash
cd frontend && npm run test:run -- App -t "demo run package"
```

Expected: fail because helper and UI action do not exist.

- [x] **Step 2: Implement API helper and UI wiring**

Add:

```ts
export function demoRunPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/demo-run-package/download`;
}
```

Wire `Download demo run package` into the selected job evidence/handoff area using existing `secondary-link` / `panel-actions` styles. Do not create a marketing-style card or new landing screen.

- [x] **Step 3: Verify frontend slice**

Run:

```bash
cd frontend && npm run test:run -- App -t "demo run package"
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

## Task 3: Terminal Demo Package Integration

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interface:**
- Add `download_demo_run_package <base_url> <job_id> <output_path>`.
- Add `print_demo_run_package_summary <package_path> <expected_job_id>`.
- Default output path: `/tmp/linguaframe-demo/demo-run-package.zip`.

- [x] **Step 1: Write failing shell helper test**

Add fixture ZIP validation to `test-linguaframe-demo-client.sh`.

Expected helper assertions:

- required entries exist
- manifest references expected job id
- package text excludes forbidden strings
- summary prints `demoRunPackageJobId=...` and `demoRunPackageEntryCount=...`

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because helpers do not exist.

- [x] **Step 2: Implement shell helpers**

Add:

```bash
download_demo_run_package "$BASE_URL" "$job_id" "$DEMO_RUN_PACKAGE_PATH"
print_demo_run_package_summary "$DEMO_RUN_PACKAGE_PATH" "$job_id"
```

`print_demo_run_package_summary` must reject:

```text
/Users/
source-videos/
job-artifacts/
objectKey
demo-access-token
private-demo-token
OPENAI_API_KEY
sk-
raw transcript text
raw subtitle text
raw generated subtitle
raw corrected subtitle
provider payload
provider request payload
```

- [x] **Step 3: Wire success and OpenAI smoke scripts**

In `docker-e2e-success.sh` and `docker-e2e-openai-smoke.sh`:

- set `DEMO_RUN_PACKAGE_PATH`
- download the package after diagnostics/evidence/quality evidence
- print summary
- echo final output path

- [x] **Step 4: Verify terminal slice**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
```

## Task 4: Documentation, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/085-demo-run-evidence-package-workspace.md`

- [x] **Step 1: Document package behavior**

Document:

- browser `Download demo run package`
- backend route `/api/jobs/{jobId}/demo-run-package/download`
- terminal output `/tmp/linguaframe-demo/demo-run-package.zip`
- ZIP entries
- safety exclusions

- [x] **Step 2: Run full verification**

Run:

```bash
mvn -pl LinguaFrame -Dtest=DemoRunPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- App -t "demo run package"
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
git diff --check
```

- [x] **Step 3: Update execution log**

Record:

- RED test failures
- final passing commands
- package route and output path
- explicit safety exclusions

- [x] **Step 4: Commit and merge**

Commit:

```bash
git add .
git commit -m "Add demo run evidence package workspace"
git checkout main
git merge --no-ff demo-run-evidence-package-workspace -m "Merge demo run evidence package workspace"
```

Post-merge, run at least:

```bash
cd frontend && npm run test:run -- App -t "demo run package"
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
git diff --check HEAD~1..HEAD
```

## Done Criteria

- A reviewer can download one demo run ZIP from browser or terminal.
- The ZIP includes safe Markdown/JSON entries for job detail, diagnostics, evidence, quality evidence, delivery manifest, checklist, and session report.
- Existing artifact archive, evidence bundle, and reviewed handoff package behavior remains unchanged.
- No package entry exposes raw media text, object keys, local paths, provider payloads, credentials, demo tokens, or media bytes.
- The feature is committed on a feature branch and merged back to `main`.
