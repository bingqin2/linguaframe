# AI Audit Evidence Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a reviewer-facing AI audit package that explains which prompt templates and model calls were used for one localization job, with browser and terminal download paths.

**Architecture:** Reuse existing job detail model-call data and the read-only `PromptTemplateRegistry`; do not persist new artifacts. Add an on-demand ZIP service under the job boundary, expose it through `LocalizationJobController`, wire one browser link near model-call/evidence panels, and extend demo scripts to download and validate the package.

**Tech Stack:** Spring Boot MVC, Jackson, Java ZIP streams, existing prompt/model-call VOs, React + TypeScript + Vite, Vitest/jsdom, Bash + Python ZIP validators, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend package, frontend link, terminal integration, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep the package metadata-only. It may include job ids, operation names, provider names, model names, prompt versions, template versions, usage counts, costs, latencies, statuses, safe summaries, and static API routes.
- Do not include raw transcript text, raw subtitle text, corrected subtitle text, provider request/response payloads, object storage keys, local media paths, API keys, demo tokens, uploaded bytes, generated media bytes, or generated artifact bytes.
- Do not add prompt editing, prompt A/B testing, provider routing, database-backed prompt history, or experiment tracking.
- Tests must run without Docker, FFmpeg, OpenAI, RabbitMQ, Redis, network access, object storage, or browser download APIs.

---

## Current Context

- `LocalizationJobVo.modelCalls()` already exposes safe model-call rows with provider, model, prompt version, latency, token/audio/character usage, estimated cost, safe summaries, and safe error summaries.
- `PromptTemplateRegistry.listActiveTemplates()` already exposes active translation and quality-evaluation templates through `GET /api/prompt-templates`.
- The React demo already has `Prompt templates` and `Model calls` panels, but a reviewer still needs multiple screens to connect prompt versions to model-call records.
- Existing package features follow a pattern: `Stored*Bo`, `*Service`, `*ServiceImpl`, controller download endpoint, OpenAPI/runtime route tests, frontend download link, shell download/validation helper, docs, execution log, commit, merge.

## Task 1: Backend AI Audit Package

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredAiAuditPackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/AiAuditPackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/AiAuditPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/AiAuditPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- `AiAuditPackageService.openAiAuditPackage(String jobId): StoredAiAuditPackageBo`
- `StoredAiAuditPackageBo(String filename, String contentType, long sizeBytes, InputStream inputStream)`
- Endpoint: `GET /api/jobs/{jobId}/ai-audit-package/download`

- [x] **Step 1: Write failing service test**

Create `AiAuditPackageServiceTests` with fake `LocalizationJobQueryService` and `PromptTemplateRegistry`.

Fixture one completed job:

- `jobId=job-ai-audit`
- `videoId=video-ai-audit`
- `targetLanguage=zh-CN`
- three model calls:
  - `TRANSCRIPTION`, provider `OPENAI`, model `gpt-4o-mini-transcribe`, prompt version `openai-audio-transcriptions-v1`
  - `TRANSLATION`, provider `OPENAI`, model `gpt-4.1-mini`, prompt version `openai-subtitle-translation-v1`
  - `EVALUATION`, provider `OPENAI`, model `gpt-4.1-mini`, prompt version `openai-translation-quality-evaluation-v1`
- active prompt templates for subtitle translation and quality evaluation
- unsafe marker strings in unused fields and safe-error fixture text: `/Users/example`, `job-artifacts/`, `OPENAI_API_KEY`, `sk-test`, `provider request payload`, `raw transcript text`, `raw subtitle text`

Assert ZIP entries include exactly:

```text
manifest.json
README.md
model-calls.json
prompt-templates.json
ai-usage-summary.json
ai-audit-report.md
```

Assert combined UTF-8 text:

- contains `job-ai-audit`
- contains `openai-subtitle-translation-v1`
- contains `openai-translation-quality-evaluation-v1`
- contains `modelCallCount`
- excludes unsafe markers

Run:

```bash
mvn -pl LinguaFrame -Dtest=AiAuditPackageServiceTests test
```

Expected: fail because the service and BO do not exist.

- [x] **Step 2: Implement service and safe JSON/Markdown composition**

Implement `AiAuditPackageServiceImpl` with dependencies:

- `LocalizationJobQueryService`
- `PromptTemplateRegistry`
- `ObjectMapper`

Use `queryService.getJob(jobId)` for job/model-call detail and `promptTemplateRegistry.listActiveTemplates()` for active templates.

ZIP entries:

- `manifest.json`: job id, video id, target language, status, generatedAt, entry list, safety flags, modelCallCount, promptTemplateCount.
- `README.md`: purpose, entry descriptions, safety statement, route path.
- `model-calls.json`: safe model-call rows only.
- `prompt-templates.json`: active template metadata plus system prompts/output contracts as currently exposed by the API.
- `ai-usage-summary.json`: counts by provider, operation, status; total latency; total estimated cost; token/audio/character totals.
- `ai-audit-report.md`: readable summary tying prompt versions to model calls, missing-template warnings, failed call count, and cost/latency totals.

Safety rules:

- Reuse a local `FORBIDDEN_MARKERS` list matching demo package scripts.
- Sanitize free-text fields by replacing unsafe summaries with `omitted because it contained fields outside the AI audit safety contract`.
- Do not include timeline events, artifacts, diagnostics artifacts, transcripts, subtitles, failureReason, object keys, or local paths.

Run:

```bash
mvn -pl LinguaFrame -Dtest=AiAuditPackageServiceTests test
```

Expected: pass.

- [x] **Step 3: Add download endpoint and API contracts**

Modify `LocalizationJobController`:

- inject `AiAuditPackageService`
- add:

```java
@GetMapping("/{jobId}/ai-audit-package/download")
@Operation(summary = "Download a safe AI audit package for a localization job")
public ResponseEntity<InputStreamResource> downloadAiAuditPackage(@PathVariable String jobId)
```

Response:

- `Content-Type: application/zip`
- attachment filename: `linguaframe-job-{jobId}-ai-audit-package.zip`

Add required runtime route:

```text
/api/jobs/{jobId}/ai-audit-package/download
```

Update OpenAPI/runtime tests to require the route.

- [x] **Step 4: Add controller test**

Extend `LocalizationJobControllerTests` with `downloadsAiAuditPackageForLocalizationJob`.

Persist:

- one completed job
- at least two model-call records with prompt versions and safe summaries
- one failed model call with unsafe error text to prove package sanitization

Assert:

- HTTP 200
- `Content-Disposition` filename is `linguaframe-job-{jobId}-ai-audit-package.zip`
- required ZIP entries exist
- combined text includes prompt/model-call evidence
- combined text excludes forbidden markers

Run:

```bash
mvn -pl LinguaFrame -Dtest=AiAuditPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

Expected: pass.

## Task 2: Frontend Download Entry

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- `linguaFrameApi.aiAuditPackageDownloadUrl(jobId: string): string`

- [x] **Step 1: Write failing frontend test**

Update the existing model-call job-detail test or add a focused test named with `AI audit package`.

Assert the selected job view shows:

```text
Download AI audit package
```

with href:

```text
/api/jobs/{jobId}/ai-audit-package/download
```

Run:

```bash
cd frontend && npm run test:run -- App -t "AI audit package"
```

Expected: fail because the link/helper does not exist.

- [x] **Step 2: Implement API helper and UI link**

Add:

```ts
export function aiAuditPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/ai-audit-package/download`;
}
```

Export it from `linguaFrameApi`.

Add a `secondary-link` in the `Model calls` panel header when a job is selected:

```tsx
<a className="secondary-link" href={linguaFrameApi.aiAuditPackageDownloadUrl(job.jobId)}>
  Download AI audit package
</a>
```

Do not add a new card or landing page.

- [x] **Step 3: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App -t "AI audit package"
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: all pass.

## Task 3: Demo Script Download And ZIP Validation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Test: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- `download_ai_audit_package "$BASE_URL" "$job_id" "$output_path"`
- `print_ai_audit_package_summary "$package_path" "$expected_job_id"`

- [x] **Step 1: Write failing shell test**

Add `test_print_ai_audit_package_summary_validates_zip_and_secrets`.

Create a safe ZIP with required entries and an unsafe ZIP containing `provider request payload sk-test /Users/example/job-artifacts/raw.json`.

Assert safe output includes:

```text
aiAuditPackageJobId=job-ai-audit-package
aiAuditPackageEntryCount=6
aiAuditPackageModelCallCount=3
```

Assert unsafe ZIP fails with `forbidden sensitive string`.

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because `print_ai_audit_package_summary` does not exist.

- [x] **Step 2: Implement shell helpers**

In `scripts/demo/lib/linguaframe-demo.sh`, add:

```bash
download_ai_audit_package() {
  local base_url="$1"
  local job_id="$2"
  local output_path="$3"

  mkdir -p "$(dirname "$output_path")"
  demo_curl -fsS "$base_url/api/jobs/$job_id/ai-audit-package/download" -o "$output_path"
}
```

Add Python ZIP validator requiring the six entries, checking forbidden markers, verifying manifest job id, and printing entry/model-call/template counts.

- [x] **Step 3: Wire deterministic and OpenAI smoke scripts**

In `scripts/demo/docker-e2e-success.sh`:

- default output path: `/tmp/linguaframe-demo/ai-audit-package.zip`
- download after `job-evidence.zip`
- print `print_ai_audit_package_summary`
- echo final output path

In `scripts/demo/docker-e2e-openai-smoke.sh`:

- download to `$OUTPUT_DIR/ai-audit-package.zip`
- print summary after evidence bundle summary

- [x] **Step 4: Verify scripts**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
```

Expected: pass.

## Task 4: Documentation, Full Verification, Commit, Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/086-ai-audit-evidence-workspace.md`

- [x] **Step 1: Document behavior**

Document:

- Browser `Download AI audit package`
- backend route `/api/jobs/{jobId}/ai-audit-package/download`
- terminal output `/tmp/linguaframe-demo/ai-audit-package.zip`
- package entries
- safety exclusions
- distinction from demo run package: AI audit package is prompt/model-call focused; demo run package is the full reviewer workspace.

- [x] **Step 2: Run full verification**

Run:

```bash
mvn -pl LinguaFrame -Dtest=AiAuditPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- App -t "AI audit package"
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
git diff --check
```

Expected: all pass.

- [x] **Step 3: Update execution log and plan checklist**

Add a new `docs/progress/execution-log.md` entry with:

- plan path
- backend endpoint
- frontend link
- script output
- validation commands

Mark all plan checkboxes complete after the implementation and validation are actually complete.

- [x] **Step 4: Commit and merge**

Commit:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredAiAuditPackageBo.java \
  LinguaFrame/src/main/java/com/linguaframe/job/service/AiAuditPackageService.java \
  LinguaFrame/src/main/java/com/linguaframe/job/service/impl/AiAuditPackageServiceImpl.java \
  LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java \
  LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java \
  LinguaFrame/src/test/java/com/linguaframe/job/service/AiAuditPackageServiceTests.java \
  LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java \
  LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java \
  LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java \
  frontend/src/api/linguaframeApi.ts frontend/src/App.tsx frontend/src/App.test.tsx \
  scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh \
  scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/test-linguaframe-demo-client.sh \
  README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md \
  docs/product/roadmap.md docs/product/target-state.md docs/progress/decisions.md \
  docs/progress/execution-log.md docs/plans/086-ai-audit-evidence-workspace.md
git commit -m "Add AI audit evidence workspace"
git checkout main
git merge --no-ff ai-audit-evidence-workspace -m "Merge AI audit evidence workspace"
```

Post-merge verification:

```bash
mvn -pl LinguaFrame -Dtest=AiAuditPackageServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- App -t "AI audit package"
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
git status --short --branch
```

Expected:

- branch is `main`
- working tree is clean
- `main` contains the merge commit
