# Demo Handoff Package Download MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a one-click reviewed demo handoff ZIP package that contains the safe reviewer-facing outputs and evidence for a completed LinguaFrame job.

**Architecture:** Add a backend handoff package service and download endpoint that composes existing reviewed artifacts, delivery manifest Markdown, diagnostics JSON, backend evidence Markdown, and a generated package manifest into a ZIP. Add frontend download links to the selected-job delivery/report surfaces and Docker demo script download/verification helpers. Keep the package separate from the existing result bundle, which remains the full generated artifact archive.

**Tech Stack:** Spring Boot MVC, Java ZIP streams, existing object storage abstraction, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo scripts, Markdown docs.

## Global Constraints

- This slice must produce one complete user/operator-visible feature: backend endpoint, frontend affordance, script download/validation, tests, docs, plan provenance, validation, and merge back to `main`.
- The handoff package is for reviewer handoff, not full internal audit. Include reviewed JSON/SRT/VTT, optional reviewed burned video, delivery manifest Markdown, diagnostics JSON, backend evidence Markdown, and a package manifest.
- Do not include uploaded source video, extracted audio, generated transcript JSON, generated target subtitles, generated burned video, worker summary, object keys, local paths, provider payloads, API keys, demo tokens, raw transcript text, raw generated subtitle text, raw corrected subtitle text outside reviewed artifact files, or unrelated audit artifacts.
- Reviewed subtitle artifact files are allowed because they are explicit handoff outputs published by the user workflow.
- If reviewed subtitles are not ready, the endpoint should still return a package with manifest/evidence and `handoffReady=false`, but it must not fake reviewed outputs.
- Reuse existing services and route style; do not add database tables or new persistence.
- Tests must run without Docker, FFmpeg, OpenAI, or network access.

---

## Current Context

- `GET /api/jobs/{jobId}/artifacts/archive/download` downloads all generated artifacts for internal result inspection.
- `GET /api/jobs/{jobId}/evidence/bundle/download` downloads metadata-only evidence.
- `GET /api/jobs/{jobId}/delivery-manifest/markdown/download` downloads reviewed handoff metadata.
- The browser has `Delivery handoff`, `Demo handoff checklist`, and `Demo session report`, but no single reviewed handoff package.
- `scripts/demo/docker-e2e-success.sh` already saves job detail, artifact list, delivery manifest, diagnostics, evidence, and report files under `/tmp/linguaframe-demo/`.

## Task 1: Backend Handoff Package Service And Endpoint

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/JobHandoffPackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobHandoffPackageServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredHandoffPackageBo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobHandoffPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- `JobHandoffPackageService.openHandoffPackage(String jobId): StoredHandoffPackageBo`
- `StoredHandoffPackageBo(String filename, String contentType, long sizeBytes, InputStream inputStream)`
- Endpoint: `GET /api/jobs/{jobId}/handoff-package/download`

- [x] **Step 1: Write failing service test**

Add `JobHandoffPackageServiceTests` with a fake `JobArtifactRepository`, fake `ObjectStorageService`, fake `DeliveryManifestService`, fake `JobEvidenceReportService`, and fake `LocalizationJobQueryService`.

Fixture artifacts:

- `REVIEWED_SUBTITLE_JSON` with bytes `{ "segments": [] }`.
- `REVIEWED_SUBTITLE_SRT` with bytes `1\n00:00:00,000 --> 00:00:01,000\nReviewed line\n`.
- `REVIEWED_SUBTITLE_VTT` with bytes `WEBVTT\n\n00:00.000 --> 00:01.000\nReviewed line\n`.
- `REVIEWED_BURNED_VIDEO` with bytes `reviewed-video-bytes`.
- `TARGET_SUBTITLE_JSON`, `TRANSCRIPT_JSON`, `BURNED_VIDEO`, `WORKER_SUMMARY`, and `EXTRACTED_AUDIO` with unsafe marker bytes that must not appear.

Assert ZIP entries include:

```text
manifest.json
delivery-manifest.md
evidence.md
diagnostics.json
reviewed/REVIEWED_SUBTITLE_JSON/<artifactId>-reviewed-subtitles.zh-CN.json
reviewed/REVIEWED_SUBTITLE_SRT/<artifactId>-reviewed-subtitles.zh-CN.srt
reviewed/REVIEWED_SUBTITLE_VTT/<artifactId>-reviewed-subtitles.zh-CN.vtt
reviewed/REVIEWED_BURNED_VIDEO/<artifactId>-reviewed-burned-video.mp4
```

Assert ZIP bytes do not include `raw transcript text`, `raw generated subtitle`, `job-artifacts/`, `/Users/`, `provider payload`, `OPENAI_API_KEY`, or `private-demo-token`.

Run:

```bash
mvn -pl LinguaFrame -Dtest=JobHandoffPackageServiceTests test
```

Expected: fail because the service does not exist.

- [x] **Step 2: Implement package BO and service interface**

Create:

```java
public record StoredHandoffPackageBo(
        String filename,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
```

Create:

```java
public interface JobHandoffPackageService {
    StoredHandoffPackageBo openHandoffPackage(String jobId);
}
```

- [x] **Step 3: Implement service**

`JobHandoffPackageServiceImpl` should:

- Load job details through `LocalizationJobQueryService.getDiagnosticsReport(jobId)` so missing jobs still use existing 404 behavior.
- Load all artifacts through `JobArtifactRepository.findByJobId(jobId)`.
- Include only `REVIEWED_SUBTITLE_JSON`, `REVIEWED_SUBTITLE_SRT`, `REVIEWED_SUBTITLE_VTT`, and `REVIEWED_BURNED_VIDEO` artifact bytes.
- Add `manifest.json` containing job id, video id, target language, status, generatedAt, handoffReady, reviewed artifact count, package entries, and safety booleans.
- Add `delivery-manifest.md` from `DeliveryManifestService.buildMarkdownManifest(jobId)`.
- Add `evidence.md` from `JobEvidenceReportService.buildMarkdownReport(jobId)`.
- Add `diagnostics.json` from `LocalizationJobQueryService.getDiagnosticsReport(jobId)`.
- Use sanitized ZIP entry names.
- Return filename `linguaframe-job-{jobId}-handoff-package.zip` and content type `application/zip`.

- [x] **Step 4: Pass service test**

Run:

```bash
mvn -pl LinguaFrame -Dtest=JobHandoffPackageServiceTests test
```

Expected: pass.

- [x] **Step 5: Add controller endpoint test**

In `LocalizationJobControllerTests`, add a test for:

```text
GET /api/jobs/job-controller-job-handoff-package/handoff-package/download
```

Assert:

- HTTP 200.
- `Content-Type: application/zip`.
- `Content-Disposition` filename contains `linguaframe-job-job-controller-job-handoff-package-handoff-package.zip`.
- ZIP contains `manifest.json`, `delivery-manifest.md`, `evidence.md`, `diagnostics.json`, and reviewed artifact entries.
- ZIP does not include unsafe raw text, object keys, local paths, provider payloads, API keys, or demo tokens.

Expected before implementation: fail because the endpoint is missing.

- [x] **Step 6: Wire controller and route contracts**

Inject `JobHandoffPackageService` into `LocalizationJobController` and add:

```java
@GetMapping("/{jobId}/handoff-package/download")
@Operation(summary = "Download a safe reviewed handoff package for a localization job")
public ResponseEntity<InputStreamResource> downloadHandoffPackage(@PathVariable String jobId) { ... }
```

Update runtime dependency route list and OpenAPI/runtime tests to include:

```text
/api/jobs/{jobId}/handoff-package/download
```

- [x] **Step 7: Verify backend**

Run:

```bash
mvn -pl LinguaFrame -Dtest=JobHandoffPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

Expected: pass.

## Task 2: Frontend Download Entry Points

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Add `linguaFrameApi.jobHandoffPackageDownloadUrl(jobId: string): string`.
- Add handoff package links in `DeliveryHandoffPanel`, `DemoHandoffChecklistPanel`, and `DemoSessionReportPanel`.

- [x] **Step 1: Write failing frontend tests**

Add tests asserting:

- The `Delivery handoff` panel contains `Download handoff package` with href `/api/jobs/{jobId}/handoff-package/download`.
- The `Demo handoff checklist` panel contains the same link.
- The `Demo session report` panel contains the same link.

Run:

```bash
cd frontend && npm run test:run -- App -t "handoff package"
```

Expected: fail because the link helper and links do not exist.

- [x] **Step 2: Implement API helper and links**

Add to `frontend/src/api/linguaframeApi.ts`:

```ts
export function jobHandoffPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/handoff-package/download`;
}
```

Render `Download handoff package` as a `secondary-link` in:

- `DeliveryHandoffPanel`
- `DemoHandoffChecklistPanel`
- `DemoSessionReportPanel`

- [x] **Step 3: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: pass.

## Task 3: Docker Demo Script Download And Verification

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Test: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Add `download_handoff_package "$BASE_URL" "$job_id" "$HANDOFF_PACKAGE_PATH"`.
- Add `print_handoff_package_summary "$HANDOFF_PACKAGE_PATH" "$job_id"`.

- [x] **Step 1: Write failing script test**

Add a test that builds a fixture ZIP with:

- `manifest.json`
- `delivery-manifest.md`
- `evidence.md`
- `diagnostics.json`
- reviewed subtitle entries

Assert `print_handoff_package_summary` prints:

```text
handoffPackageJobId=job-handoff-package
handoffPackageEntryCount=...
handoffPackageReviewedArtifactCount=3
```

Assert it fails if unsafe strings appear inside the ZIP.

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because `print_handoff_package_summary` is not defined.

- [x] **Step 2: Implement script helpers**

Add:

```bash
download_handoff_package() {
  demo_curl -fsS "$base_url/api/jobs/$job_id/handoff-package/download" -o "$output_path"
}
```

Add Python-backed `print_handoff_package_summary` that:

- Reads ZIP entries.
- Requires `manifest.json`, `delivery-manifest.md`, `evidence.md`, and `diagnostics.json`.
- Counts entries under `reviewed/`.
- Scans ZIP bytes for forbidden unsafe markers.
- Prints safe summary lines.

- [x] **Step 3: Wire Docker E2E success script**

Add:

```bash
HANDOFF_PACKAGE_PATH="${LINGUAFRAME_DEMO_HANDOFF_PACKAGE_PATH:-/tmp/linguaframe-demo/handoff-package.zip}"
download_handoff_package "$BASE_URL" "$job_id" "$HANDOFF_PACKAGE_PATH"
print_handoff_package_summary "$HANDOFF_PACKAGE_PATH" "$job_id"
echo "Downloaded handoff package to $HANDOFF_PACKAGE_PATH"
```

- [x] **Step 4: Verify scripts**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
```

Expected: pass.

## Task 4: Documentation And Provenance

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/081-demo-handoff-package-download-mvp.md`

- [x] **Step 1: Update docs**

Document:

- Difference between result bundle, evidence bundle, delivery manifest, demo session report, and handoff package.
- Browser `Download handoff package` links.
- Docker `/tmp/linguaframe-demo/handoff-package.zip` output.
- Metadata and safety boundary.

- [x] **Step 2: Update execution log**

Record the plan path, backend endpoint behavior, frontend links, script download behavior, and validation commands.

- [x] **Step 3: Mark plan complete**

After verification, mark all checkboxes in this plan complete.

## Final Verification

Run before committing:

```bash
mvn -pl LinguaFrame -Dtest=JobHandoffPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
git diff --check
```

After commit and merge back to `main`, rerun the same validation on `main` and record the result in `docs/progress/execution-log.md`.
