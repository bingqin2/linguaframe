# Subtitle Review Workspace MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a browser-visible subtitle review workspace that lets a demo viewer inspect source transcript, translated subtitles, timing alignment, quality evaluation, and downloadable subtitle artifacts from one coherent surface.

**Architecture:** Keep transcript and subtitle persistence unchanged. Add a read-only backend review summary derived from existing transcript segments, target subtitle segments, quality evaluation, artifacts, and job detail. Render that summary in React as the primary subtitle-review work surface, then propagate safe review metadata into browser evidence, backend Markdown evidence, demo scripts, and docs.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, MySQL/H2 tests, React, TypeScript, Vitest, Bash demo scripts, existing LinguaFrame services and APIs.

## Global Constraints

- This slice must be one complete user-visible feature, not a small UI polish pass.
- Do not edit transcript or subtitle text in this feature; review is read-only.
- Do not expose object storage keys, local paths, raw provider payloads, API keys, demo tokens, or media bytes.
- The review API may include transcript and subtitle text because existing preview APIs already expose it to the job viewer; evidence exports and script summaries must stay metadata-only.
- Do not change worker stage ordering, provider calls, cache semantics, retry, cancellation, or artifact generation.
- Reuse existing transcript, subtitle, quality evaluation, artifact, and job query services where possible.

---

## Task 1: Backend Subtitle Review Summary API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleReviewSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/SubtitleReviewSummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/SubtitleReviewService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/SubtitleReviewServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/SubtitleReviewServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Steps:**

- [x] Define `SubtitleReviewSegmentVo` with `index`, `startMs`, `endMs`, `sourceText`, `targetText`, `durationMs`, `timingDeltaMs`, and `status`.
- [x] Define `SubtitleReviewSummaryVo` with `jobId`, `targetLanguage`, `segmentCount`, `missingTargetCount`, `timingMismatchCount`, `averageDurationMs`, `maxDurationMs`, `qualityScore`, `qualityVerdict`, `qualityIssueCount`, `qualitySuggestedFixCount`, `downloadableSubtitleArtifactCount`, and `segments`.
- [x] Implement `SubtitleReviewService.buildReview(String jobId, String language)`.
- [x] Pair source transcript and target subtitle segments by `index`.
- [x] Mark segment status as `ALIGNED`, `MISSING_TARGET`, or `TIMING_MISMATCH`; use a mismatch threshold of `250 ms`.
- [x] Include only subtitle artifacts of type `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, or `TARGET_SUBTITLE_VTT` in `downloadableSubtitleArtifactCount`.
- [x] Add `GET /api/jobs/{jobId}/subtitle-review?language=zh-CN`.
- [x] Add service tests for aligned segments, missing target text, timing mismatch, missing quality evaluation, and artifact count.
- [x] Add controller tests for JSON shape and demo access protection compatibility.

## Task 2: React Subtitle Review Workspace

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add TypeScript types `SubtitleReviewSegment` and `SubtitleReviewSummary`.
- [x] Add `linguaFrameApi.getSubtitleReview(jobId, language)`.
- [x] Load subtitle review whenever a selected job loads preview data.
- [x] Add a `Subtitle review` panel near transcript/subtitle previews.
- [x] Show segment count, missing target count, timing mismatch count, average/max duration, quality score/verdict, and subtitle artifact count.
- [x] Render a compact comparison table with source text, target text, timing range, delta, and status.
- [x] Keep existing transcript preview, subtitle preview, quality evaluation, and artifact panels visible.
- [x] Handle review API failure as a non-blocking preview error.
- [x] Add Vitest coverage for aligned review, missing/timing mismatch badges, and API load failure fallback.

## Task 3: Evidence, Diagnostics, And Demo Script Summaries

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `frontend/src/App.tsx`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Steps:**

- [x] Add metadata-only subtitle review lines to backend Markdown evidence: `segmentCount`, `missingTargetCount`, `timingMismatchCount`, `qualityScore`, `qualityVerdict`, and `downloadableSubtitleArtifactCount`.
- [x] Add the same safe metadata to browser evidence Markdown and JSON.
- [x] Extend `print_diagnostics_summary` or add a new `print_subtitle_review_summary_file` helper for terminal demos.
- [x] Update `docker-e2e-success.sh` through shared helper usage so successful demo runs can print review summary once the backend endpoint exists.
- [x] Extend demo client tests with a fixture that proves script output includes review counts and excludes local paths, object keys, provider payloads, raw transcript text, raw subtitle text, and secrets.

## Task 4: Documentation, Progress Notes, And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/074-subtitle-review-workspace-mvp.md`

**Steps:**

- [x] Document where to inspect subtitle review in the browser.
- [x] Document that review is read-only and derived from persisted transcript/subtitle segments.
- [x] Document terminal review evidence for Docker demo runs.
- [x] Add smoke checklist items for review counts, comparison rows, quality score, and safe evidence exports.
- [x] Record the decision to keep review read-only before adding editing workflows.
- [x] Mark plan checkboxes as complete after implementation.

## Validation

- [x] Run `mvn -pl LinguaFrame -Dtest=SubtitleReviewServiceTests,LocalizationJobControllerTests,JobEvidenceReportServiceTests test`.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh`.
- [x] Run `git diff --check`.
- [x] Optional live validation:

```bash
scripts/demo/start-local-demo.sh
scripts/demo/docker-e2e-success.sh
```

## Done Criteria

- [x] Browser users can review source and translated subtitle segments side by side from one panel.
- [x] Backend exposes a safe read-only review summary without changing persisted media outputs.
- [x] Review metadata appears in backend evidence, browser evidence, and terminal demo summaries.
- [x] Existing transcript/subtitle preview, quality evaluation, result delivery, cache replay, and pipeline progress behavior remains visible.
- [x] Tests, docs, validation, commit, and merge back to `main` are completed as part of this feature slice.
