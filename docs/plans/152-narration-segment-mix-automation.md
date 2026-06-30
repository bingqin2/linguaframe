# Narration Segment Mix Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators tune ducking, narration volume, and fade per narration segment while keeping job-level mix settings as defaults.

**Architecture:** Add nullable per-segment mix override fields to persisted narration rows, workspace/script-package DTOs, frontend draft rows, and FFmpeg mix windows. Existing job-level mix settings remain the default source; segment overrides apply only to the segment windows that define them. Evidence and terminal scripts expose metadata-only counts and ranges, not narration text.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, Flyway, existing narration workspace services, FFmpeg mix service, JUnit 5 + MockMvc, React + TypeScript + Vitest, Bash + Python demo scripts.

## Global Constraints

- This is one complete feature slice: schema, backend API, generation behavior, evidence, script package import/export, React controls, terminal preflight/docs, validation, commit, and merge back to `main`.
- Keep job-level mix settings as defaults; segment-level fields are optional overrides.
- Per-segment ranges match job-level ranges: `duckingVolume` 0.00-1.00, `narrationVolume` 0.00-2.00, `fadeDurationMs` 0-5000.
- Segment override editing is local until `Save narration`.
- Do not call OpenAI/TTS providers, generate audio/video, update evidence, or write object storage from local override edits.
- Evidence and scripts must remain metadata-only and must not print narration text, transcript/subtitle bodies, provider payloads, object keys, local paths, tokens, or API keys.
- Do not add waveform rendering, voice cloning, uploaded reference audio, lip sync, or a full nonlinear multitrack editor in this slice.

---

### Task 1: Persist Segment Mix Overrides

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V30__add_narration_segment_mix_overrides.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/NarrationSegmentRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JdbcNarrationSegmentRepository.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/repository/NarrationSegmentRepositoryTests.java`

**Interfaces:**
- Extend `NarrationSegmentRecord` with nullable `BigDecimal duckingVolume`, nullable `BigDecimal narrationVolume`, and nullable `Integer fadeDurationMs`.
- Extend repository insert/select SQL to preserve null overrides.

- [x] Add nullable DB columns for per-segment override values.
- [x] Write failing repository tests for round-tripping null/default overrides and explicit override values.
- [x] Update row mapping and insert SQL.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationSegmentRepositoryTests`.

### Task 2: Workspace Save/Read API

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/SaveNarrationSegmentsRequest.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationSegmentVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- Add optional segment request/response fields: `duckingVolume`, `narrationVolume`, `fadeDurationMs`.
- Validation rejects out-of-range overrides with safe 400 responses.

- [x] Add failing service/controller tests for reading overrides, saving overrides, null inheritance, and invalid ranges.
- [x] Preserve existing save behavior for clients that omit override fields.
- [x] Include override metadata in workspace segment responses.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests`.

### Task 3: Script Package Import/Export

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageSegmentVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ImportNarrationScriptPackageDto.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationScriptPackageServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationScriptPackageServiceTests.java`

**Interfaces:**
- Script package segment JSON includes optional per-segment mix override fields.
- Markdown export reports override counts and row-level metadata without exposing additional sensitive data beyond the existing operator-authored package text contract.

- [x] Add failing tests for package export with overrides, import with overrides, and invalid override rejection.
- [x] Preserve backward compatibility for old packages without override fields.
- [x] Document inherited defaults versus explicit overrides in package Markdown.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests`.

### Task 4: FFmpeg Mix Window Overrides

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/NarrationWindowBo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegNarratedVideoMixServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarratedVideoServiceImpl.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegNarratedVideoMixServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarratedVideoServiceTests.java`

**Interfaces:**
- `NarrationWindowBo` carries effective ducking volume, narration volume, and fade duration for each window.
- Global mix values remain fallback defaults when segment overrides are null.

- [x] Add failing FFmpeg tests proving different windows can use different ducking/fade values.
- [x] Build effective windows from saved segments plus job-level defaults.
- [x] Keep generated response top-level mix settings as job defaults while evidence reports override usage separately.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegNarratedVideoMixServiceTests,NarratedVideoServiceTests`.

### Task 5: Evidence And Terminal Metadata

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/narration-timing-assistant.sh`
- Modify: `scripts/demo/README.md`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- Add metadata-only evidence fields such as `segmentMixOverrideCount`, `segmentDuckingOverrideCount`, `segmentNarrationVolumeOverrideCount`, and `segmentFadeOverrideCount`.
- Terminal timing assistant prints override counts but no narration text.

- [x] Add failing backend tests for evidence override counts and forbidden marker absence.
- [x] Extend terminal summaries with override counts.
- [x] Keep evidence `READY` criteria unchanged.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`.
- [x] Run `bash -n scripts/demo/narration-timing-assistant.sh scripts/demo/lib/linguaframe-demo.sh`.

### Task 6: React Segment Mix Controls

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/domain/narrationQuickScriptImport.ts`
- Modify: `frontend/src/domain/narrationQuickScriptImport.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Segment rows include optional `duckingVolume`, `narrationVolume`, and `fadeDurationMs`.
- Save request includes override fields only when explicitly set.

- [x] Add failing frontend tests for inherited segment mix defaults, setting selected-row overrides, clearing overrides, saving overrides, and invalid override ranges blocking save.
- [x] Add a compact selected-segment mix override panel near the narration inspector using number inputs and clear buttons.
- [x] Show inherited job-level values beside explicit segment values.
- [x] Keep quick script import/export text format unchanged in this slice; script package JSON is the rich transport for overrides.
- [x] Run `npm test -- --run src/domain/narrationQuickScriptImport.test.ts src/App.test.tsx -t "segment mix|quick script"`.
- [x] Run `npm run build`.

### Task 7: Product Docs And Execution Evidence

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/152-narration-segment-mix-automation.md`

**Interfaces:**
- Docs describe job-level defaults plus per-segment overrides and terminal evidence.

- [x] Document the browser workflow: select row, override mix values, preview timing, save narration, generate narrated video.
- [x] Document that local override edits do not call providers or generate artifacts.
- [x] Record a decision explaining why per-segment numeric automation comes before waveform curves.
- [x] Mark completed plan tasks and record validation evidence.

### Task 8: Full Validation And Merge

**Files:**
- No new files beyond Tasks 1-7.

- [x] Run focused backend validations from Tasks 1-5.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run `bash -n scripts/demo/narration-timing-assistant.sh scripts/demo/narration-evidence.sh scripts/demo/narration-script-package.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Run `git diff --check`.
- [x] Commit with message `Add narration segment mix automation`.
- [x] Switch to `main`, merge `narration-segment-mix-automation`, and confirm `git status --short --branch` is clean.

## Self-Review

- Spec coverage: persistence, API, generation behavior, evidence, script packages, frontend controls, docs, validation, and merge are covered.
- Placeholder scan: no deferred implementation placeholders remain.
- Scope check: this slice intentionally excludes decoded waveform rendering, freeform automation curves, voice cloning, uploaded reference audio, lip sync, and full nonlinear multitrack editing.
