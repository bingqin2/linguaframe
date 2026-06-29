# Narration Timeline Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current narration row editor into a demo-ready timeline workbench that makes multi-segment explanatory voiceover easier to inspect, validate, and present before audio/video generation.

**Architecture:** Keep the existing narration segment and mix-setting persistence model. Add computed workspace metadata for timeline scale, gaps, overlaps, readiness, and selected segment diagnostics, then render it in React as a dense workbench with a visual timeline strip, segment list, inspector, and evidence/generation controls. This is a workflow/UI slice, not a waveform editor or drag/drop nonlinear editor.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing demo Bash scripts.

## Global Constraints

- This is one complete feature slice: backend computed metadata, API response shape, frontend workbench, docs, tests, validation, commit, and merge back to `main`.
- Use branch title `narration-timeline-workbench`; do not include `/` in the user-facing branch title.
- Do not change persisted narration segment schema unless a test proves the existing schema cannot support the workbench.
- Do not add waveform rendering, drag/drop timeline editing, multitrack automation curves, voice cloning, lip sync, or a generic video editor.
- Evidence and demo summaries must remain metadata-only and exclude narration script bodies, transcript text, subtitle text, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Keep the UI dense and workbench-like, inspired by `fish-tts-desktop` style cues: central work surface, right inspector, compact controls, light borders, no marketing layout.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Timeline Summary Metadata

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationTimelineSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationTimelineSummaryVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWorkspaceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`

**Interfaces:**
- `NarrationTimelineSegmentVo(int index, BigDecimal startSeconds, BigDecimal endSeconds, BigDecimal durationSeconds, BigDecimal leftPercent, BigDecimal widthPercent, String status, int characterCount, String voice)`
- `NarrationTimelineSummaryVo(BigDecimal startSeconds, BigDecimal endSeconds, BigDecimal totalSpanSeconds, BigDecimal coveredSeconds, BigDecimal gapSeconds, int gapCount, boolean hasOverlap, boolean generationReady, List<NarrationTimelineSegmentVo> segments)`
- Extend `NarrationWorkspaceVo` with `NarrationTimelineSummaryVo timeline`.

- [x] Write failing service tests asserting timeline summary for empty, gapped, and contiguous narration segments.
- [x] Verify the tests fail because `NarrationWorkspaceVo.timeline()` does not exist.
- [x] Implement timeline summary calculation from existing segment records.
- [x] Mark segment statuses as `READY`, `EMPTY_TEXT`, or `INVALID_RANGE` from computed data; keep service validation behavior unchanged for saves.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration timeline summary`.

## Task 2: API Contract And Runtime Evidence

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`

**Interfaces:**
- `GET /api/jobs/{jobId}/narration-workspace` returns `timeline`.
- `NarrationEvidenceVo` adds metadata-only fields `timelineGapCount`, `timelineGapSeconds`, and `timelineHasOverlap`.
- Demo terminal summaries print `narrationEvidenceTimelineGapCount`, `narrationEvidenceTimelineGapSeconds`, and `narrationEvidenceTimelineHasOverlap`.

- [x] Write failing controller tests for the `timeline` object in workspace JSON.
- [x] Write failing evidence tests for gap metadata in JSON, Markdown, ZIP manifest, and summary JSON.
- [x] Implement API/evidence serialization using computed segment timing only.
- [x] Extend demo summary shell output without printing segment text.
- [x] Run `mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests,NarrationEvidenceServiceTests`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Expose narration timeline evidence`.

## Task 3: React Timeline Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add frontend types `NarrationTimelineSummary` and `NarrationTimelineSegment`.
- `NarrationWorkspace.timeline` drives a visual timeline strip and readiness metrics.

- [x] Write failing Vitest coverage for visible timeline summary, gapped-window warning, selected segment inspector, and evidence metrics.
- [x] Split `NarrationWorkspacePanel` into clear subcomponents inside `App.tsx`: timeline strip, segment table, inspector, and mix controls.
- [x] Render timeline segments as proportional bars with stable dimensions and accessible labels.
- [x] Show compact metrics: span, covered time, gaps, segment count, generation readiness, mix source, audio/video readiness.
- [x] Keep row editing keyboard-friendly with number inputs and text inspector; do not add drag/drop.
- [x] Disable save/generate controls when local validation fails, while still allowing evidence refresh/downloads.
- [x] Run `npm test -- --run src/App.test.tsx`.
- [x] Run `npm run build`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration timeline workbench UI`.

## Task 4: Documentation And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/136-narration-timeline-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [x] Document the browser workflow: open narration workspace, inspect timeline bars, fix gaps/invalid rows, save narration, save mix settings, generate audio/video, verify evidence.
- [x] Document that gaps are allowed as intentional silent intervals while overlaps remain blocked.
- [x] Record the decision that this slice adds timeline inspection before waveform/drag editing.
- [x] Run focused backend validations from Tasks 1-2.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [ ] Commit with message `Document narration timeline workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests`
- `mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests,NarrationEvidenceServiceTests`
- `npm test -- --run src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
