# Subtitle Review Annotations And Release Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reviewer annotations and release notes to the reviewed subtitle workflow so a demo can prove what was checked, why segments changed, and what was published.

**Architecture:** Extend the existing subtitle draft overlay instead of creating a separate review-session system. Store per-segment review metadata beside draft text, aggregate it into a metadata-only review evidence endpoint/package, and surface the same information in the React draft editor, reviewed publish result, handoff evidence, and demo scripts.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC/Flyway, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This is one complete feature slice: persistence, backend APIs, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- Keep review annotations tied to a selected job and language; do not introduce multi-user review sessions, assignment, comments threads, or real approval workflow.
- Do not expose API keys, bearer tokens, demo tokens, object storage keys, local filesystem paths, provider payloads, uploaded/generated media bytes, or raw provider responses.
- Existing transcript/subtitle preview APIs may still show text in the selected-job UI; evidence exports and terminal summaries must be metadata-only and exclude raw transcript text, generated subtitle text, corrected subtitle text, and annotation free text.
- Segment annotation categories are fixed: `TERM`, `TONE`, `TIMING`, `READABILITY`, `MISSING_TEXT`, `OTHER`.
- Review decisions are fixed: `UNREVIEWED`, `ACCEPTED`, `EDITED`, `NEEDS_FOLLOWUP`.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Persist Review Annotations In Subtitle Drafts

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V27__add_subtitle_review_annotations.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/SubtitleReviewIssueCategory.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/SubtitleReviewDecision.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/SubtitleDraftSegmentRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/SubtitleDraftSegmentRepository.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/UpdateSubtitleDraftRequest.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleDraftSegmentVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleDraftSummaryVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleDraftServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleDraftServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/SubtitleDraftSegmentRepositoryTests.java`

**Interfaces:**
- `UpdateSubtitleDraftRequest.Segment(int index, String text, SubtitleReviewDecision decision, List<SubtitleReviewIssueCategory> issueCategories, String reviewerNote)`
- `SubtitleDraftSegmentVo` gains `decision`, `issueCategories`, `reviewerNote`, and `noteLength`.
- `SubtitleDraftSummaryVo` gains `reviewedSegmentCount`, `acceptedSegmentCount`, `editedDecisionCount`, `followupSegmentCount`, `annotationCount`, and `reviewerNoteCount`.

- [ ] Add Flyway columns to `subtitle_draft_segments`: `review_decision VARCHAR(32) NOT NULL DEFAULT 'EDITED'`, `issue_categories VARCHAR(256) NOT NULL DEFAULT ''`, and `reviewer_note TEXT NULL`.
- [ ] Add enum parsing/validation with safe defaults: unchanged existing rows should read as `EDITED` when a draft row exists, while generated rows without draft rows should render as `UNREVIEWED`.
- [ ] Extend repository upsert/find mapping without losing existing draft text behavior.
- [ ] Validate request segments: valid index, nonblank text, valid decision, at most 4 issue categories, note max 500 chars, and no duplicate categories.
- [ ] Preserve existing draft export: JSON/SRT/VTT still export only corrected subtitle text, not reviewer notes.
- [ ] Add service tests for accepted unchanged segment, edited segment with categories/note, follow-up segment, validation failures, clear draft removing annotations, and export excluding notes.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=SubtitleDraftServiceTests,SubtitleDraftSegmentRepositoryTests`.

## Task 2: Review Evidence Aggregate, Markdown, And ZIP

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/StoredSubtitleReviewEvidencePackageBo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleReviewEvidenceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleReviewEvidenceCategoryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleReviewEvidenceCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleReviewEvidenceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleReviewEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleReviewEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/subtitle-review-evidence`
- `GET /api/jobs/{jobId}/subtitle-review-evidence/markdown/download`
- `GET /api/jobs/{jobId}/subtitle-review-evidence/download`

- [ ] Build metadata-only evidence from job detail, subtitle review summary, subtitle draft summary, reviewed subtitle workflow, and reviewed publish artifacts.
- [ ] Return status `READY` when all generated target subtitle segments have review decisions and no follow-up remains; `ATTENTION` for partial review/follow-up; `BLOCKED` when target subtitles are missing.
- [ ] Include counts by decision and issue category, note count, reviewed artifact readiness, release-note summary, safe links, package entries, and safety notes.
- [ ] Render Markdown without raw transcript text, generated subtitle text, corrected subtitle text, or annotation note bodies.
- [ ] Build ZIP with `manifest.json`, `subtitle-review-evidence.md`, `review-summary.json`, `release-notes.md`, and `README.md`.
- [ ] Add controller endpoints with attachment filenames `linguaframe-job-{jobId}-subtitle-review-evidence.md` and `.zip`.
- [ ] Add runtime required routes.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=SubtitleReviewEvidenceServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`.

## Task 3: Publish Release Notes And Handoff Integration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/PublishReviewedSubtitlesRequest.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/ReviewedSubtitlePublishVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ReviewedSubtitleDeliveryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/ReviewedSubtitleWorkflowServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobHandoffPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/ReviewedSubtitleDeliveryServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobHandoffPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- `PublishReviewedSubtitlesRequest(String language, boolean includeBurnedVideo, String releaseNotes)`
- `ReviewedSubtitlePublishVo` gains `releaseNotesLength`, `reviewDecisionCounts`, and `issueCategoryCounts`.

- [ ] Accept optional release notes up to 1000 chars during publish; do not persist note bodies in artifacts unless represented as metadata counts and safe release summary.
- [ ] Include review decision/category counts in publish response.
- [ ] Update reviewed subtitle workflow checks with review completion counts and follow-up warnings.
- [ ] Add subtitle review evidence links to job evidence Markdown, handoff package manifest/README, and demo handoff portal safe links/package inventory.
- [ ] Ensure all added handoff/package surfaces remain metadata-only and exclude free-form note text.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=ReviewedSubtitleDeliveryServiceTests,JobHandoffPackageServiceTests,DemoHandoffPortalServiceTests`.

## Task 4: React Review Annotation UI

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getSubtitleReviewEvidence(jobId: string): Promise<SubtitleReviewEvidence>`
- `downloadSubtitleReviewEvidenceMarkdown(jobId: string): Promise<Blob>`
- `downloadSubtitleReviewEvidenceZip(jobId: string): Promise<Blob>`

- [ ] Add TS types for review decisions, issue categories, draft annotation fields, evidence summary, and publish response counts.
- [ ] Extend subtitle draft editor rows with decision select, category checkboxes, reviewer note textarea, and compact counts.
- [ ] Keep free-form notes visible only in the editing UI; metadata/evidence panels show note counts and categories.
- [ ] Add release notes textarea to publish controls and send it with `publishReviewedSubtitles`.
- [ ] Add a `Subtitle review evidence` panel near reviewed workflow with status, decision counts, issue category counts, note count, release-note length, safe links, Markdown/ZIP download actions, and refresh.
- [ ] Refresh evidence after draft save/clear and reviewed publish.
- [ ] Add Vitest coverage for API paths, annotation editing payload, publish release notes payload, evidence panel rendering, downloads, and unsafe marker absence.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.

## Task 5: Terminal Export, Demo Scripts, And Docs

**Files:**
- Create: `scripts/demo/subtitle-review-evidence.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Modify: `scripts/demo/docker-e2e-openai-smoke.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/subtitle-review-evidence.sh`
- Default output directory: `/tmp/linguaframe-demo/subtitle-review-evidence/`

- [ ] Add helpers for evidence JSON, Markdown, ZIP download, and `print_subtitle_review_evidence_summary_file`.
- [ ] Add standalone script that exits non-zero on `BLOCKED` unless `LINGUAFRAME_SUBTITLE_REVIEW_EVIDENCE_REPORT_ONLY=true`.
- [ ] Extend deterministic, OpenAI smoke, and full Tears scripts to export subtitle review evidence after job completion.
- [ ] Document how review annotations differ from subtitle text edits, reviewed subtitle workflow, handoff package, and demo handoff portal.
- [ ] Update smoke checklist with browser annotation expectations, terminal output, ZIP entries, and safety exclusions.
- [ ] Run `bash -n scripts/demo/subtitle-review-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.

## Task 6: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/131-subtitle-review-annotations-release-notes.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Mark completed plan tasks.
- [ ] Run focused backend validation from Tasks 1-3.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run script syntax validation from Task 5.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Add subtitle review annotations`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=SubtitleDraftServiceTests,SubtitleDraftSegmentRepositoryTests`
- `mvn -pl LinguaFrame test -Dtest=SubtitleReviewEvidenceServiceTests,LocalizationJobControllerTests,RuntimeDependencyControllerTests`
- `mvn -pl LinguaFrame test -Dtest=ReviewedSubtitleDeliveryServiceTests,JobHandoffPackageServiceTests,DemoHandoffPortalServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/subtitle-review-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
