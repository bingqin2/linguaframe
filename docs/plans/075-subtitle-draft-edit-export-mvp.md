# Subtitle Draft Edit And Export MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a complete subtitle draft editing workflow so a demo reviewer can correct translated subtitle text, persist those corrections, and export corrected subtitle files without changing the generated media pipeline.

**Architecture:** Keep generated subtitle segments immutable as the pipeline output. Add a separate draft layer keyed by `jobId`, `language`, and segment `index`; the draft overlays edited target text on top of persisted target subtitles for review/export. The browser edits only draft text, while backend export endpoints generate corrected JSON, SRT, and VTT artifacts on demand from the draft overlay.

**Tech Stack:** Java 21, Spring Boot, Flyway, JUnit 5, MySQL/H2 tests, React, TypeScript, Vitest, existing transcript/subtitle/review/export services.

## Global Constraints

- This is one complete user-visible feature slice: backend persistence/API, frontend editing UI, export path, tests, docs, validation, commit, and merge.
- Do not mutate original generated `subtitle_segments` rows.
- Do not trigger TTS, subtitle burn-in, artifact cache invalidation, or job status transitions in this feature.
- Do not expose object storage keys, local paths, provider payloads, API keys, demo tokens, or media bytes.
- Draft evidence may include counts and timestamps, but shareable evidence exports must not include raw transcript or subtitle text.
- Keep the workflow private-demo oriented; do not add multi-user review assignments or approval roles yet.

---

## Task 1: Backend Draft Persistence And Overlay API

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V19__create_subtitle_review_drafts.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/SubtitleDraftSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleDraftSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleDraftSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/UpdateSubtitleDraftRequest.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/SubtitleDraftSegmentRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleDraftService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleDraftServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleDraftServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Steps:**

- [x] Add `subtitle_draft_segments` with `id`, `job_id`, `language`, `segment_index`, `text`, `created_at`, `updated_at`, and a unique key on `(job_id, language, segment_index)`.
- [x] Define draft DTO/VO types for one edited segment, bulk update request, and draft summary counts.
- [x] Implement `SubtitleDraftService.getDraft(jobId, language)` by overlaying draft rows on existing target subtitle segments.
- [x] Implement `SubtitleDraftService.updateDraft(jobId, language, request)` as an upsert by segment index, rejecting indexes not present in generated target subtitles.
- [x] Implement `SubtitleDraftService.clearDraft(jobId, language)` to remove draft rows for a job/language.
- [x] Add APIs:
  - `GET /api/jobs/{jobId}/subtitle-draft?language=zh-CN`
  - `PUT /api/jobs/{jobId}/subtitle-draft?language=zh-CN`
  - `DELETE /api/jobs/{jobId}/subtitle-draft?language=zh-CN`
- [x] Test overlay behavior, invalid indexes, idempotent upsert, clear behavior, and controller JSON shape.

## Task 2: Corrected Subtitle Export API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/SubtitleDraftExportFormat.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleDraftService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleDraftServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleDraftServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Steps:**

- [x] Add export formats `JSON`, `SRT`, and `VTT`.
- [x] Reuse `SubtitleExportService` to export corrected subtitles from generated segment timing plus draft text overlay.
- [x] Add `GET /api/jobs/{jobId}/subtitle-draft/export?language=zh-CN&format=srt`.
- [x] Return safe filenames such as `corrected-subtitles.zh-CN.srt`.
- [x] Return `404` or a clear client error when no generated target subtitles exist.
- [x] Test JSON/SRT/VTT export content, content type, filename header, and no mutation of stored generated subtitles.

## Task 3: Browser Draft Editing Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add TypeScript types for draft summary, draft segments, and update request.
- [x] Add frontend API methods `getSubtitleDraft`, `updateSubtitleDraft`, `clearSubtitleDraft`, and draft export URL builder.
- [x] Load draft data with selected job preview data.
- [x] Add a `Subtitle draft editor` panel near `Subtitle review`.
- [x] Render source text read-only and target text editable in stable rows.
- [x] Track dirty rows locally and show edited/unchanged counts.
- [x] Add `Save draft`, `Reset unsaved`, `Clear draft`, and download links for corrected JSON/SRT/VTT.
- [x] Handle save/clear failures as non-blocking panel errors.
- [x] Ensure browser evidence exports include draft metadata counts only, not edited subtitle text.
- [x] Test editing a row, save payload shape, clear draft, export links, failed save fallback, and evidence redaction.

## Task 4: Demo Scripts, Evidence, And Documentation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/075-subtitle-draft-edit-export-mvp.md`

**Steps:**

- [x] Add draft metadata lines to backend evidence: draft segment count, edited segment count, last updated time if present.
- [x] Add terminal helper output for draft summary metadata only.
- [x] Extend demo script tests to prove helper output excludes raw edited subtitle text.
- [x] Document browser draft editing, corrected subtitle downloads, and the fact that generated subtitles remain immutable.
- [x] Add smoke checklist items for save, clear, export, and evidence redaction.
- [x] Record the decision that draft export is separate from TTS/burn-in regeneration.
- [x] Mark this plan complete after validation.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=SubtitleDraftServiceTests,LocalizationJobControllerTests,JobEvidenceReportServiceTests,OpenApiDocumentationTests test`.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh`.
- [x] Run `git diff --check`.
- [x] Post-merge: rerun backend focused tests, frontend focused tests, and demo client tests on `main`.

## Done Criteria

- [x] A browser user can edit translated subtitle draft text for a completed job.
- [x] Draft edits persist separately from generated subtitle segments.
- [x] Corrected JSON/SRT/VTT exports use draft text with original timing.
- [x] Browser, backend, and terminal evidence expose draft metadata only.
- [x] Tests, docs, validation, commit, and merge back to `main` are completed as part of this feature slice.
