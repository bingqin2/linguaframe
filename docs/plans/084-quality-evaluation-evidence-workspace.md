# Quality Evaluation Evidence Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Turn the existing quality evaluation result into a reviewer-ready evidence workspace with safe backend Markdown, browser copy/download actions, and terminal summary output.

**Architecture:** Keep the existing quality evaluation pipeline, persistence, provider, cache, and job-detail field unchanged. Add a metadata-only quality evidence service and download endpoint under the job boundary, enhance the React `Quality evaluation` panel with evidence actions and safe status guidance, and extend demo shell helpers so terminal demos can print and persist quality evidence without exposing transcript text, subtitle text, provider payloads, object keys, API keys, local paths, or media bytes.

**Tech Stack:** Spring Boot MVC, existing `LocalizationJobQueryService` and `QualityEvaluationVo`, React + TypeScript + Vite, Vitest/jsdom, Bash + Python demo helpers, Markdown docs.

## Global Constraints

- This slice must be one complete user-visible feature: backend endpoint, frontend workspace, terminal helper/report integration, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Do not change quality evaluation scoring, provider prompts, provider calls, cache keys, pipeline stage ordering, budget behavior, or job status transitions.
- The new backend and terminal evidence must be metadata-only. It may include score, verdict, status, dimension scores, issue/fix counts, issue/fix strings already stored in `QualityEvaluationVo`, timestamps, safe download routes, and prompt/model-call metadata already exposed safely.
- Do not include raw transcript text, translated subtitle text, reviewed subtitle text, provider request/response payloads, object storage keys, API keys, demo tokens, raw local paths, or uploaded media bytes.
- Tests must run without Docker, FFmpeg, OpenAI, RabbitMQ, Redis, network access, or real browser clipboard/download APIs.

---

## Current Context

- Quality evaluation is already persisted in `quality_evaluations`, exposed as `LocalizationJobVo.qualityEvaluation`, rendered in the React `Quality evaluation` panel, included in diagnostics/evidence summaries, and cached for repeated compatible provider inputs.
- The existing React panel shows score, verdict, dimensions, issues, and suggested fixes, but it does not provide a reviewer-facing quality evidence report or direct download link.
- The existing Docker/OpenAI scripts print only basic score/verdict/status. They do not produce a standalone quality evidence Markdown artifact.
- A complete next feature should make quality evaluation explainable in browser and terminal demos without changing the AI pipeline.

## Task 1: Backend Quality Evidence Markdown Endpoint

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredQualityEvidenceBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/QualityEvaluationEvidenceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/QualityEvaluationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/QualityEvaluationEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interface:**
- Service: `QualityEvaluationEvidenceService.openMarkdownEvidence(String jobId): StoredQualityEvidenceBo`
- BO: `StoredQualityEvidenceBo(String filename, String contentType, long sizeBytes, InputStream inputStream)`
- Endpoint: `GET /api/jobs/{jobId}/quality-evaluation/evidence/markdown/download`

- [x] **Step 1: Write failing service test**

Add `QualityEvaluationEvidenceServiceTests` with a fake `LocalizationJobQueryService`.

Fixture a job with:

- `jobId=quality-job`
- `videoId=quality-video`
- `targetLanguage=zh-CN`
- `status=COMPLETED`
- `qualityEvaluation.score=92`
- `verdict=GOOD`
- dimension scores
- two issues and two suggested fixes
- unsafe markers elsewhere in the job fixture: `raw transcript text`, `raw subtitle text`, `provider payload`, `job-artifacts/`, `/Users/example`, `OPENAI_API_KEY`, `private-demo-token`

Assert Markdown includes:

```text
# LinguaFrame Quality Evaluation Evidence
- Job: quality-job
- Video: quality-video
- Target language: zh-CN
- Score: 92 / 100
- Verdict: GOOD
- Status: SUCCEEDED
## Dimensions
## Issues
## Suggested fixes
```

Assert Markdown excludes all unsafe markers.

Run:

```bash
mvn -pl LinguaFrame -Dtest=QualityEvaluationEvidenceServiceTests test
```

Expected: fail because the service does not exist.

- [x] **Step 2: Implement BO, service interface, and Markdown service**

`QualityEvaluationEvidenceServiceImpl` should:

- Load job detail through `LocalizationJobQueryService.getJob(jobId)` so missing-job behavior remains consistent.
- If `qualityEvaluation == null`, produce Markdown with `Status: NOT_RECORDED` and safe next-action guidance.
- If present, include score, verdict, status, language, createdAt, dimension scores, issue count/list, suggested-fix count/list, and safe related routes:

```text
/api/jobs/{jobId}
/api/jobs/{jobId}/diagnostics/download
/api/jobs/{jobId}/evidence/markdown/download
/api/jobs/{jobId}/subtitle-review?language=<targetLanguage>
```

- Return filename `linguaframe-job-{jobId}-quality-evidence.md` and content type `text/markdown`.

- [x] **Step 3: Add controller endpoint and route contract**

Inject `QualityEvaluationEvidenceService` into `LocalizationJobController`.

Add:

```java
@GetMapping("/{jobId}/quality-evaluation/evidence/markdown/download")
@Operation(summary = "Download safe quality evaluation evidence Markdown for a localization job")
```

Update runtime required routes and OpenAPI tests to include:

```text
/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download
```

- [x] **Step 4: Verify backend slice**

Run:

```bash
mvn -pl LinguaFrame -Dtest=QualityEvaluationEvidenceServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
```

## Task 2: Browser Quality Evidence Workspace

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Interface:**
- Add `linguaFrameApi.qualityEvaluationEvidenceMarkdownDownloadUrl(jobId: string): string`.
- Add `formatQualityEvaluationEvidence(job): string`.
- Enhance `QualityEvaluationPanel` with `Copy quality evidence`, `Download quality evidence`, and `Download backend quality evidence`.

- [x] **Step 1: Write failing frontend tests**

In `frontend/src/App.test.tsx`, add tests asserting:

- Completed job with quality evaluation shows `Copy quality evidence`, `Download quality evidence`, and `Download backend quality evidence`.
- Backend link href is `/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download`.
- Browser Markdown copy includes score/verdict/dimension/issue/fix metadata.
- Browser Markdown copy excludes raw transcript text, raw subtitle text, object keys, local paths, provider payloads, API keys, and demo tokens.
- Missing quality evaluation keeps concise empty state and does not render evidence actions.

Run:

```bash
cd frontend && npm run test:run -- App -t "quality evidence"
```

Expected: fail because actions and URL helper do not exist.

- [x] **Step 2: Implement URL helper and evidence formatter**

Add:

```ts
export function qualityEvaluationEvidenceMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/quality-evaluation/evidence/markdown/download`;
}
```

`formatQualityEvaluationEvidence(job)` should produce:

```markdown
# LinguaFrame Quality Evaluation Evidence

- Job: <jobId>
- Video: <videoId>
- Target language: <targetLanguage>
- Score: <score> / 100
- Verdict: <verdict>
- Status: <status>

## Dimensions
- Completeness: <value>
- Readability: <value>
- Timing preservation: <value>
- Naturalness: <value>

## Issues
- ...

## Suggested fixes
- ...
```

- [x] **Step 3: Update `QualityEvaluationPanel`**

Change panel props from only `evaluation` to `job`.

Render:

- status pill
- score/verdict/language overview
- dimensions
- issues/fixes
- copy/download/backend download actions

When `job.qualityEvaluation` is missing, keep `No quality evaluation recorded yet.` and do not show actions.

- [x] **Step 4: Style quality evidence actions**

Reuse existing `panel-actions`, `secondary-button`, and `secondary-link`. Keep the panel dense and operational.

- [x] **Step 5: Verify frontend slice**

Run:

```bash
cd frontend && npm run test:run -- App -t "quality evidence"
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

## Task 3: Terminal Quality Evidence Helpers And Docs

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/084-quality-evaluation-evidence-workspace.md`

**Interfaces:**
- `print_quality_evaluation_summary_file <job-detail-json>`
- `write_quality_evaluation_evidence_markdown <job-detail-json> <output-md>`
- Download backend Markdown to `/tmp/linguaframe-demo/quality-evidence.md` when available.

- [x] **Step 1: Write failing script tests**

Extend `scripts/demo/test-linguaframe-demo-client.sh` with a job fixture containing quality evaluation plus unsafe markers.

Assert:

- `print_quality_evaluation_summary_file` prints `qualityScore`, `qualityVerdict`, `qualityStatus`, dimension scores, issue count, and suggested-fix count.
- `write_quality_evaluation_evidence_markdown` writes the quality evidence title, score, verdict, dimensions, issues, and fixes.
- Output excludes unsafe markers.

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because helpers do not exist.

- [x] **Step 2: Implement script helpers**

Add Python-backed helpers to `scripts/demo/lib/linguaframe-demo.sh`:

- `print_quality_evaluation_summary_file`
- `write_quality_evaluation_evidence_markdown`

The Markdown helper must output `NOT_RECORDED` when `qualityEvaluation` is absent.

- [x] **Step 3: Integrate demo scripts**

In `scripts/demo/docker-e2e-success.sh`, after job detail is downloaded:

- print quality evaluation summary from job detail
- write `/tmp/linguaframe-demo/quality-evidence.md`
- attempt backend download from `/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download` when the backend route is present

In `scripts/demo/docker-e2e-openai-smoke.sh`, print the same summary and write a smoke quality evidence Markdown file under its existing output directory.

- [x] **Step 4: Update docs and roadmap**

Document:

- Browser quality evidence actions.
- Backend Markdown route.
- Terminal quality evidence output.
- How to verify in deterministic and OpenAI demo flows.

- [x] **Step 5: Final validation**

Run:

```bash
mvn -pl LinguaFrame -Dtest=QualityEvaluationEvidenceServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- App -t "quality evidence"
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh
git diff --check
```

## Completion Criteria

- A completed job with quality evaluation has a safe backend Markdown evidence endpoint.
- The browser `Quality evaluation` panel can copy/download quality evidence and link to the backend Markdown.
- Terminal demo scripts print quality evaluation evidence and write a standalone Markdown artifact.
- Missing quality evaluation remains a clear, non-error empty state.
- Tests prove endpoint behavior, UI actions, script output, OpenAPI/runtime route contracts, and metadata-only safety.
