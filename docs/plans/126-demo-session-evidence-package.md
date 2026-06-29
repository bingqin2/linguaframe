# Demo Session Evidence Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one downloadable operator-level demo session evidence ZIP that packages the whole private demo session index, readiness evidence, cost ledger, run archive, and safe next-action notes.

**Architecture:** Reuse the existing metadata-only operator services instead of reading media artifacts or object storage. A new backend package service will compose the demo session command center, private-demo operations, launch rehearsal, evidence gallery, run archive, model usage ledger, and presentation cockpit into a ZIP with JSON and Markdown entries. The frontend command center panel and a terminal script will expose the same package download.

**Tech Stack:** Java 21, Spring Boot MVC, existing operator services, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash + Python demo helpers, ZIP streams.

## Global Constraints

- This feature is read-only and must not upload media, create jobs, retry/cancel jobs, dispatch queues, run FFmpeg, call OpenAI, mutate object storage, or write database records.
- The ZIP must contain only safe metadata: statuses, job ids, video ids, profile ids, safe API routes, commands, counts, estimated costs, latency, cache counts, readiness labels, and next actions.
- The ZIP must exclude uploaded media bytes, generated media bytes, object keys, local filesystem paths, raw transcripts, raw subtitles, corrected subtitle text, provider request/response payloads, API keys, bearer tokens, demo tokens, JWT signing secrets, passwords, and credentials.
- The package must work before upload, during an active job, after completion, and when the session is blocked.
- Existing command center, cockpit, ledger, operations, launch rehearsal, gallery, archive, and per-job package endpoints must remain backward compatible.

---

## Task 1: Backend Demo Session Evidence Package Service

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/domain/bo/DemoSessionEvidencePackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/DemoSessionEvidencePackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/operator/service/impl/DemoSessionEvidencePackageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/operator/service/DemoSessionEvidencePackageServiceTests.java`

**Interfaces:**
- `DemoSessionEvidencePackageBo openPackage(String jobId)` returns `filename`, `contentType`, and ZIP bytes.
- Default filename: `linguaframe-demo-session-evidence-package.zip` or `linguaframe-demo-session-<safe-job-id>-evidence-package.zip` when `jobId` is present.
- ZIP entries:
  - `manifest.json`
  - `README.md`
  - `command-center.json`
  - `command-center.md`
  - `operations.json`
  - `operations.md`
  - `launch-rehearsal.json`
  - `launch-rehearsal.md`
  - `model-usage-ledger.json`
  - `model-usage-ledger.md`
  - `presentation-cockpit.json`
  - `presentation-cockpit.md`
  - `evidence-gallery.json`
  - `evidence-gallery.md`
  - `run-archive.json`
  - `run-archive.md`

- [x] Inject `ObjectMapper`, `DemoSessionCommandCenterService`, `PrivateDemoOperationsService`, `PrivateDemoLaunchRehearsalService`, `ModelUsageLedgerService`, `DemoPresentationCockpitService`, `PrivateDemoEvidenceGalleryService`, and `PrivateDemoRunArchiveService`.
- [x] Use `ZipOutputStream` with UTF-8 entry names and in-memory `ByteArrayOutputStream`.
- [x] Build `manifest.json` with `packageType`, `generatedAt`, `focusedJobId`, `overallStatus`, `phase`, `primaryCommand`, `entryCount`, `entries`, `safeLinks`, and `safetyNotes`.
- [x] Render `README.md` as the package table of contents, explaining what each entry is for and which per-job packages remain the detailed evidence source.
- [x] Generate Markdown for services that already expose Markdown (`commandCenterMarkdown`, `ledgerMarkdown`) by calling those services directly.
- [x] Generate Markdown locally for operations, cockpit, gallery, archive, and launch rehearsal using only fields already present in their VOs.
- [x] Sanitize ZIP filenames with `[A-Za-z0-9._-]`; replace all other characters with `-`.
- [x] Add tests proving the ZIP contains all required entries, includes focused job metadata when `jobId` is provided, and excludes unsafe markers such as `source-videos/`, `job-artifacts/`, `/Users/`, `OPENAI_API_KEY`, `private-demo-token`, `provider payload`, `raw transcript`, and `raw subtitle`.
- [x] Run `mvn -pl LinguaFrame test -Dtest=DemoSessionEvidencePackageServiceTests`.

## Task 2: Operator API Endpoint And Controller Tests

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/operator/controller/OperatorDashboardController.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/operator/controller/OperatorDashboardControllerTests.java`

**Interfaces:**
- `GET /api/operator/demo-session-evidence-package/download?jobId=<optional>` returns a ZIP attachment.

- [x] Inject `DemoSessionEvidencePackageService` into `OperatorDashboardController`.
- [x] Add `GET /api/operator/demo-session-evidence-package/download` with `Content-Type: application/zip` and the service-provided `Content-Disposition` filename.
- [x] Ensure the endpoint inherits existing `/api/operator/**` demo-token and bearer-token protection.
- [x] Add controller tests for focused-job ZIP download, filename header, required ZIP entries, unsafe marker absence, and demo-token protection.
- [x] Run `mvn -pl LinguaFrame test -Dtest=OperatorDashboardControllerTests`.

## Task 3: Frontend API And Command Center Package Download

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `downloadDemoSessionEvidencePackageZip(jobId?: string): Promise<Blob>`

- [x] Add API function with optional encoded `jobId` query parameter and existing demo-token/bearer header support.
- [x] Add API tests for default package download and focused-job download with spaces/slashes encoded.
- [x] Add a `Download session package` action to `DemoSessionCommandCenterPanel`.
- [x] Use the focused run id when available; otherwise download the unfocused session package.
- [x] Download filename in browser: `linguaframe-demo-session-evidence-package.zip`.
- [x] Add App tests proving the button is visible, calls `downloadDemoSessionEvidencePackageZip('job-session')`, and does not render raw text/secrets.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.

## Task 4: Terminal Script, README, Deployment Guide, And Progress Log

**Files:**
- Create: `scripts/demo/demo-session-evidence-package.sh`
- Modify: `README.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/126-demo-session-evidence-package.md`

**Interfaces:**
- Default output directory: `/tmp/linguaframe-demo/demo-session-evidence-package/`.
- Optional focused job env: `LINGUAFRAME_DEMO_JOB_ID`.
- Script output keys: `demoSessionEvidencePackageStatus`, `demoSessionEvidencePackagePhase`, `demoSessionEvidencePackageFocusJobId`, `demoSessionEvidencePackageZipPath`, and `demoSessionEvidencePackageEntries`.

- [x] Implement the script with `demo_curl`, optional job query encoding, ZIP download, Python ZIP inspection, and summary printing.
- [x] Download `command-center.json` first so the script can print package status and phase before saving the ZIP summary.
- [x] Exit non-zero when command center status is `BLOCKED` unless `LINGUAFRAME_DEMO_SESSION_EVIDENCE_PACKAGE_REPORT_ONLY=true`.
- [x] Document when to use the session evidence package versus command center Markdown, per-job demo run package, AI audit package, handoff package, and demo evidence closure.
- [x] Record validation commands and outcomes in `docs/progress/execution-log.md`.
- [x] Run `bash -n scripts/demo/demo-session-evidence-package.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 5: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/126-demo-session-evidence-package.md`

- [x] Mark this plan checklist complete after implementation.
- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=DemoSessionEvidencePackageServiceTests,OperatorDashboardControllerTests`
- [x] Run frontend and script checks:
  `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
  `npm run build`
  `bash -n scripts/demo/demo-session-evidence-package.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit as `Add demo session evidence package`.
- [x] Merge the feature branch back to `main` after validation passes.
