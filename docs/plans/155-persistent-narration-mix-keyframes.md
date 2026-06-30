# Persistent Narration Mix Keyframes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent narration mix keyframes so operators can save, reuse, export, import, preview, and render time-based ducking, narration-volume, and fade automation beyond per-segment overrides.

**Architecture:** Add a normalized `narration_mix_keyframes` backend model keyed by job and lane, expose it through the existing narration workspace/script-package APIs, and fold resolved keyframe values into narrated-video mix windows. The React workbench will edit keyframes locally, validate ranges, show lane summaries on the existing automation panel, and save keyframes together with narration workspace state.

**Tech Stack:** Java 21, Spring Boot, JDBC repositories, Flyway SQL, JUnit 5, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers, Markdown docs.

## Global Constraints

- This is one complete feature slice: schema, backend persistence/API, render behavior, script package import/export, frontend editor, terminal summary, docs, validation, commit, and merge back to `main`.
- Keep automation scoped to existing mix lanes only: `DUCKING_VOLUME`, `NARRATION_VOLUME`, and `FADE_DURATION_MS`.
- Preserve existing job-level mix settings and segment overrides; keyframes override job defaults only when a saved keyframe applies at the segment/window time.
- Do not add freeform multitrack editing, waveform editing, voice cloning, uploaded reference audio, lip sync, or provider calls in keyframe editing.
- Local frontend edits must not call providers, create artifacts, write object storage, or generate media until the existing save/render actions are used.
- Value ranges match current mix settings: ducking `0.000-1.000`, narration `0.000-2.000`, fade `0-5000 ms`.

---

### Task 1: Backend Mix Keyframe Persistence

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V31__create_narration_mix_keyframes.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/NarrationMixKeyframeRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/NarrationMixLane.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/NarrationMixKeyframeRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JdbcNarrationMixKeyframeRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/NarrationMixKeyframeRepositoryTests.java`

**Interfaces:**
- Produces: `replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes)`, `findByJobId(String jobId)`, and `deleteByJobId(String jobId)`.
- Produces: lane enum values `DUCKING_VOLUME`, `NARRATION_VOLUME`, `FADE_DURATION_MS`.

- [x] Write repository tests for replace/find/delete, lane ordering, and nullable fade decimal handling.
- [x] Add Flyway table with `id`, `job_id`, `lane`, `time_seconds DECIMAL(10,3)`, `value DECIMAL(8,3)`, `created_at`, `updated_at`, index on `(job_id, lane, time_seconds)`, and foreign key to `localization_jobs(id)` with cascade delete.
- [x] Implement entity, enum, repository interface, JDBC repository, row mapper, and insert/delete methods.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationMixKeyframeRepositoryTests`.

### Task 2: Workspace API And Validation

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/SaveNarrationSegmentsRequest.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/SaveNarrationMixKeyframeDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationMixKeyframeVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationMixAutomationVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWorkspaceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Consumes: `NarrationMixKeyframeRepository`.
- Produces: workspace JSON field `mixAutomation` with `keyframes`, `keyframeCount`, lane counts, and safety notes.
- Produces: save request field `mixKeyframes`.

- [x] Write failing service/controller tests proving keyframes round-trip through `GET /api/jobs/{jobId}/narration-workspace` and `PUT /api/jobs/{jobId}/narration-workspace`.
- [x] Validate contiguous-independent keyframes: max 60 per job, non-negative `timeSeconds`, known lane, lane value range, and no duplicate `(lane,timeSeconds)` after scale normalization.
- [x] Keep `segments` behavior unchanged; saving workspace replaces both segments and keyframes atomically.
- [x] Add workspace safety notes stating keyframe edits are local until save and render.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests`.

### Task 3: Render Resolution And Evidence

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationMixAutomationResolver.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarratedVideoServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarratedVideoServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`

**Interfaces:**
- Consumes: saved segments, job-level mix settings, segment overrides, and mix keyframes.
- Produces: effective `NarrationWindowBo` values where precedence is segment override, then latest keyframe at or before segment start, then job default.

- [x] Write resolver tests through narrated-video service proving keyframes affect generated window values and segment overrides still win.
- [x] Implement `NarrationMixAutomationResolver` with deterministic lane lookup and default fallback.
- [x] Inject/use the keyframe repository in `NarratedVideoServiceImpl`.
- [x] Add evidence metadata: `mixKeyframeCount`, `mixKeyframeLaneSummary`, and keyframe source notes without exposing script text.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarratedVideoServiceTests,NarrationEvidenceServiceTests`.

### Task 4: Script Package Export And Import

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ImportNarrationScriptPackageDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ImportNarrationMixKeyframeDto.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationScriptPackageServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationScriptPackageServiceTests.java`

**Interfaces:**
- Consumes/produces script package JSON field `mixKeyframes`.
- Keeps the existing quick script text format unchanged.

- [x] Write failing tests proving script packages export keyframes and import replaces saved keyframes with validation.
- [x] Include keyframe counts in manifest, Markdown, and README package notes.
- [x] Ensure import with `replaceExisting=true` replaces segments, mix settings when provided, and keyframes together.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests`.

### Task 5: Frontend Keyframe Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Create: `frontend/src/domain/narrationMixKeyframes.ts`
- Test: `frontend/src/domain/narrationMixKeyframes.test.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes workspace `mixAutomation.keyframes`.
- Produces save payload `mixKeyframes`.
- Produces helper `buildNarrationMixKeyframeLanes(input)` for lane summary and effective values.

- [x] Write failing domain tests for lane grouping, range validation, duplicate detection, selected-time insertion, and effective-value resolution.
- [x] Update API/client types and tests for workspace keyframes and save payload.
- [x] Add a compact `Keyframes` section inside the existing `Mix automation` panel with lane tabs/buttons, time/value inputs, add selected-time keyframe, delete keyframe, clear lane, and validation messages.
- [x] Wire edits into existing local draft/save flow and draft history so keyframes are unsaved until the normal save action.
- [x] Keep visual style consistent with the dense white workbench/inspector pattern already used for narration controls.
- [x] Run `npm --prefix frontend test -- --run src/domain/narrationMixKeyframes.test.ts src/api/linguaframeApi.test.ts src/App.test.tsx -t "mix keyframe|narration workspace"`.

### Task 6: Demo Script And Documentation

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/narration-mix-automation.sh`
- Modify: `scripts/demo/README.md`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/155-persistent-narration-mix-keyframes.md`

**Interfaces:**
- Terminal summary prints metadata-only counts by lane and never prints narration text, media paths, tokens, or secrets.

- [x] Extend the terminal mix automation summary with `narrationMixKeyframeCount`, `narrationMixKeyframeLaneSummary`, and lane min/max values.
- [x] Document browser usage order: edit narration rows, add keyframes, save workspace, run render preflight, render narrated video, export script package.
- [x] Record a decision explaining why persistent keyframes come after segment overrides and derived curves.
- [x] Append validation evidence to `docs/progress/execution-log.md`.
- [x] Mark this plan complete after implementation.

### Task 7: Final Verification, Commit, And Merge

**Files:**
- All files touched above.

- [x] Run focused backend tests:
  `mvn -pl LinguaFrame test -Dtest=NarrationMixKeyframeRepositoryTests,NarrationWorkspaceServiceTests,LocalizationJobControllerTests,NarratedVideoServiceTests,NarrationEvidenceServiceTests,NarrationScriptPackageServiceTests`
- [x] Run full frontend tests:
  `npm --prefix frontend test -- --run`
- [x] Run frontend build:
  `npm --prefix frontend run build`
- [x] Run full backend tests:
  `mvn -pl LinguaFrame test`
- [x] Run script syntax checks:
  `bash -n scripts/demo/narration-mix-automation.sh scripts/demo/narration-waveform.sh scripts/demo/narration-timing-assistant.sh scripts/demo/lib/linguaframe-demo.sh`
- [x] Run `git diff --check`.
- [x] Commit with message `Add persistent narration mix keyframes`.
- [x] Merge branch `persistent-narration-mix-keyframes` back to `main`.

## Self-Review

- Spec coverage: persistence, workspace API, render behavior, evidence, package import/export, frontend editor, terminal summary, docs, validation, commit, and merge are covered.
- Placeholder scan: no placeholder implementation steps are left; each task names concrete files and verification commands.
- Type consistency: backend keyframes use lane/time/value; frontend mirrors them through `mixAutomation.keyframes` and save payload `mixKeyframes`.
- Scope check: this slice intentionally excludes freeform multitrack editing, waveform editing, voice cloning, uploaded reference audio, lip sync, and provider calls during keyframe editing.
