# Reviewed Subtitle Workflow Cockpit Implementation Plan

**Goal:** Add one selected-job review cockpit that guides a completed localization job from generated subtitles through human edits, publish preflight, reviewed artifacts, optional reviewed burned video, and handoff readiness.

**Architecture:** Add a read-only backend aggregate under the job API that composes existing subtitle review, draft overlay, reviewed publish readiness, delivery manifest, artifacts, media delivery, and safe evidence links. Render it near the existing subtitle review/draft panels and expose a terminal export so browser and shell runs share one canonical "what remains before handoff?" answer. Publishing reviewed subtitles remains an explicit existing action; this cockpit does not mutate drafts or artifacts.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash demo scripts.

## Global Constraints

- This is one complete feature slice: backend API, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- The cockpit is read-only. It must not update subtitle drafts, publish reviewed subtitles, create burned videos, retry or cancel jobs, upload media, run cleanup, call providers, or start Docker.
- Do not expose API keys, bearer tokens, demo tokens, object keys, local filesystem paths, provider payloads, raw transcript text, raw generated subtitle text, corrected subtitle text, uploaded media bytes, or generated media bytes.
- Reuse existing subtitle review, subtitle draft, reviewed delivery, delivery manifest, artifact, and demo evidence services. Do not add a new persisted cockpit table.
- Keep every status actionable with a short next action.

---

## Scope

- Add `GET /api/jobs/{jobId}/reviewed-subtitle-workflow`.
- Return an aggregate with:
  - overall status: `READY`, `ATTENTION`, or `BLOCKED`;
  - phase: `WAITING_FOR_JOB`, `REVIEW_NEEDED`, `DRAFT_READY`, `PUBLISH_READY`, `HANDOFF_READY`, or `BLOCKED`;
  - recommended next action;
  - generated subtitle review metrics;
  - draft edit status and export links;
  - reviewed artifact readiness;
  - optional reviewed burned video readiness;
  - delivery manifest and handoff package readiness;
  - safe links to review, draft, publish, exports, delivery manifest, handoff package, evidence, and job detail.
- Add a React `Reviewed subtitle workflow` panel for selected jobs.
- Refresh the panel when a job is opened, subtitle draft is saved/reset/cleared, reviewed subtitles are published, and SSE/polling transitions complete.
- Add `scripts/demo/reviewed-subtitle-workflow.sh` with required `LINGUAFRAME_DEMO_JOB_ID`.
- Update README, Docker E2E guide, target state, decisions, execution log, and this plan.

## Acceptance Criteria

- For a completed job with generated target subtitles and no reviewed artifacts, the cockpit shows `REVIEW_NEEDED` or `PUBLISH_READY`, the number of segments, draft edit count, export links, and a next action pointing to review or publish.
- For a completed job with reviewed JSON/SRT/VTT artifacts and optional reviewed burned video, the cockpit shows handoff readiness and links to delivery manifest and handoff package.
- For incomplete or failed jobs, the cockpit explains why review cannot proceed yet and links to job status/evidence.
- Browser, backend JSON, and terminal summary all remain metadata-only and never include transcript/subtitle text, corrected draft text, object keys, provider payloads, local paths, tokens, or media bytes.
- The workflow visibly reduces panel-hopping between subtitle review, draft editor, delivery handoff, artifacts, and handoff package.

## Implementation Tasks

### Task 1: Backend Workflow Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ReviewedSubtitleWorkflowVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ReviewedSubtitleWorkflowCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ReviewedSubtitleWorkflowLinkVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/ReviewedSubtitleWorkflowService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ReviewedSubtitleWorkflowServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ReviewedSubtitleWorkflowServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Produces: `ReviewedSubtitleWorkflowService.workflow(String jobId): ReviewedSubtitleWorkflowVo`.
- Consumes existing job detail, artifact list, subtitle review, subtitle draft, delivery manifest, and safe route conventions.

- [ ] Write failing service tests for:
  - incomplete job is `BLOCKED` with next action to wait for completion;
  - completed job with review data but no reviewed artifacts is `PUBLISH_READY` or `REVIEW_NEEDED`;
  - reviewed JSON/SRT/VTT artifacts plus delivery manifest ready is `HANDOFF_READY`;
  - unsafe marker strings are absent from serialized output.
- [ ] Implement VO records and service interface.
- [ ] Implement service composition without mutating drafts/artifacts.
- [ ] Add controller endpoint and optional OpenAPI annotations.
- [ ] Add controller tests for route shape and owner/demo access compatibility.
- [ ] Run focused backend validation:
  `mvn -pl LinguaFrame -Dtest=ReviewedSubtitleWorkflowServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`

### Task 2: Frontend Review Cockpit

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `GET /api/jobs/{jobId}/reviewed-subtitle-workflow`.
- Produces: `getReviewedSubtitleWorkflow(jobId: string): Promise<ReviewedSubtitleWorkflow>`.

- [ ] Add TypeScript types for workflow, checks, and links.
- [ ] Add API helper and route tests, including demo-token header behavior.
- [ ] Add selected-job React state and loader.
- [ ] Render `Reviewed subtitle workflow` near subtitle review/draft/handoff panels with phase, next action, checks, links, and safety notes.
- [ ] Refresh after selected job load, draft save/reset/clear, publish reviewed subtitles, SSE terminal transition, retry/cancel result, and manual refresh.
- [ ] Add Vitest coverage for review-needed, handoff-ready, blocked, selected-job refresh, and safe links.
- [ ] Run focused frontend validation:
  `npm --prefix frontend test -- --run App.test.tsx src/api/linguaframeApi.test.ts`

### Task 3: Terminal Export

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/reviewed-subtitle-workflow.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces helper `download_reviewed_subtitle_workflow_json "$base_url" "$job_id" "$output_path"`.
- Produces helper `print_reviewed_subtitle_workflow_summary_file "$json_path"`.

- [ ] Add download and summary helpers that print stable `reviewedSubtitleWorkflow*` lines.
- [ ] Add script requiring `LINGUAFRAME_DEMO_JOB_ID`.
- [ ] Add shell tests for missing job id, route encoding, summary output, and unsafe marker redaction.
- [ ] Run:
  `bash -n scripts/demo/reviewed-subtitle-workflow.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- [ ] Run:
  `scripts/demo/test-linguaframe-demo-client.sh`

### Task 4: Documentation And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/115-reviewed-subtitle-workflow-cockpit.md`

- [ ] Document when to use the workflow cockpit versus subtitle review, draft editor, publish action, delivery manifest, and handoff package.
- [ ] Record the decision that this is a derived read-only review cockpit, not a new persisted review session.
- [ ] Record validation commands and results.
- [ ] Run:
  `npm --prefix frontend run build`
- [ ] Run:
  `git diff --check`
- [ ] Run:
  `mvn -pl LinguaFrame test`
- [ ] Run:
  `npm --prefix frontend test -- --run`
- [ ] Commit feature branch, merge back to `main`, run post-merge focused validation, and record it.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=ReviewedSubtitleWorkflowServiceTests,LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `npm --prefix frontend test -- --run App.test.tsx src/api/linguaframeApi.test.ts`
- `bash -n scripts/demo/reviewed-subtitle-workflow.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `scripts/demo/test-linguaframe-demo-client.sh`
- `npm --prefix frontend run build`
- `git diff --check`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- Post-merge focused backend/frontend/script validation on `main`.

## Completion Criteria

- A selected-job reviewer has one browser/backend/terminal cockpit to decide whether to edit, export, publish, burn reviewed subtitles, or hand off.
- The feature advances product usability by connecting existing subtitle review and handoff surfaces into one actionable workflow.
- The feature branch is verified, committed, merged back to `main`, and post-merge validation is recorded.
