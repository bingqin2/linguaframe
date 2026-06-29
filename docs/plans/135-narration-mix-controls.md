# Narration Mix Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators configure and persist narration mix settings before generating `NARRATED_VIDEO`, while keeping the output metadata-only evidence and artifact boundaries stable.

**Architecture:** Add job-level narration mix settings as a small persisted configuration owned by the narration workspace. Use those settings when FFmpeg mixes narration with base video audio, expose them through workspace/evidence/generation responses, and add compact React controls in the existing narration inspector. Keep the implementation focused on practical audio controls, not waveform editing or a full timeline editor.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, Flyway, existing FFmpeg mix service, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This is one complete feature slice: persistence, backend APIs, FFmpeg command use, evidence, frontend controls, scripts/docs, validation, commit, and merge back to `main`.
- Use branch title `narration-mix-controls`; do not include `/` in the user-facing branch title.
- Preserve artifact boundaries: `NARRATION_AUDIO` remains the timed audio bed and `NARRATED_VIDEO` remains the final mixed video artifact.
- Do not replace `DUBBING_AUDIO`, `DUBBED_VIDEO`, `BURNED_VIDEO`, `REVIEWED_BURNED_VIDEO`, generated subtitles, reviewed subtitles, or handoff artifacts.
- Do not add waveform rendering, drag/drop timeline editing, voice cloning, lip sync, public collaboration, or a generic nonlinear editor in this slice.
- Evidence and terminal summaries must remain metadata-only and exclude raw transcript text, subtitle text, narration script bodies, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Validation ranges: original audio ducking volume `0.00` to `1.00`, narration volume `0.00` to `2.00`, fade duration `0` to `5000` ms.
- Defaults: `duckingVolume=0.35`, `narrationVolume=1.00`, `fadeDurationMs=250`.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Persist Narration Mix Settings

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V29__create_narration_mix_settings.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/NarrationMixSettingsRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/NarrationMixSettingsRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/JdbcNarrationMixSettingsRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/NarrationMixSettingsRepositoryTests.java`

**Interfaces:**
- `NarrationMixSettingsRecord(String jobId, BigDecimal duckingVolume, BigDecimal narrationVolume, int fadeDurationMs, Instant updatedAt)`
- `NarrationMixSettingsRepository.findByJobId(String jobId): Optional<NarrationMixSettingsRecord>`
- `NarrationMixSettingsRepository.upsert(NarrationMixSettingsRecord settings): NarrationMixSettingsRecord`
- `NarrationMixSettingsRepository.deleteByJobId(String jobId): void`

- [x] Add Flyway migration with one row per job and strict numeric columns for ducking, narration gain, fade duration, and `updated_at`.
- [x] Implement JDBC read/upsert/delete with defaults handled by service layer, not SQL magic.
- [x] Add repository tests for insert, update, find-missing, and delete.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationMixSettingsRepositoryTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 2: Workspace API For Mix Settings

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/UpdateNarrationMixSettingsDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationMixSettingsVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWorkspaceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `NarrationMixSettingsVo(BigDecimal duckingVolume, BigDecimal narrationVolume, int fadeDurationMs, Instant updatedAt)`
- `PUT /api/jobs/{jobId}/narration-workspace/mix-settings`

- [x] Extend workspace reads to include saved mix settings or defaults.
- [x] Add validation for numeric ranges and reject invalid settings with 400 responses.
- [x] Save mix settings independently from narration segment text.
- [x] Keep clear-workspace behavior focused on rows; do not delete mix settings unless explicitly required by the API.
- [x] Add service and controller tests for defaults, valid update, invalid ranges, and workspace response shape.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 3: Apply Settings In FFmpeg Mixing

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/bo/MixNarratedVideoCommand.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/FfmpegNarratedVideoMixServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarratedVideoGenerationVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarratedVideoServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/FfmpegNarratedVideoMixServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarratedVideoServiceTests.java`

**Interfaces:**
- Extend `MixNarratedVideoCommand` with `BigDecimal narrationVolume` and `int fadeDurationMs`.
- Extend `NarratedVideoGenerationVo` with `BigDecimal narrationVolume` and `int fadeDurationMs`.

- [x] Load saved mix settings when generating `NARRATED_VIDEO`.
- [x] Pass ducking volume, narration volume, and fade duration into the FFmpeg mix command.
- [x] Apply narration volume to the narration audio input.
- [x] Add short fade-in/fade-out around narration windows when `fadeDurationMs > 0`; keep the no-fade path deterministic when it is `0`.
- [x] Preserve no-audio fallback behavior for base videos without audio tracks.
- [x] Add tests for customized ducking, narration volume, fade duration, default fallback, and safe FFmpeg failure messages.
- [x] Run `mvn -pl LinguaFrame test -Dtest=FfmpegNarratedVideoMixServiceTests,NarratedVideoServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 4: Evidence, Handoff, And Demo Summaries

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- Add metadata-only evidence fields: `narrationVolume`, `fadeDurationMs`, and `mixSettingsSource`.

- [x] Include mix settings in narration evidence JSON, Markdown, ZIP manifest, and summary JSON.
- [x] Show whether settings are defaults or operator-saved values.
- [x] Update job evidence and handoff portal facts to include ducking, narration gain, and fade duration.
- [x] Extend terminal summary output with `narrationVolume`, `fadeDurationMs`, and `mixSettingsSource`.
- [x] Add tests for metadata presence and forbidden marker absence.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh`.
- [x] Update `docs/progress/execution-log.md`.

## Task 5: React Mix Controls

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `updateNarrationMixSettings(jobId, settings)` API helper.
- Add frontend settings type matching `NarrationMixSettingsVo`.

- [ ] Add compact controls in the narration inspector for ducking volume, narration volume, and fade duration.
- [ ] Use sliders or number inputs with visible current values and disabled states during save/generation.
- [ ] Save mix settings independently from segment save.
- [ ] Refresh narration evidence after settings save.
- [ ] Show generation status with the actual mix values used.
- [ ] Keep the layout dense and workbench-like, with no landing page or decorative redesign.
- [ ] Add Vitest coverage for API request body, validation state, successful save, visible evidence metrics, and generated video status.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.
- [ ] Update `docs/progress/execution-log.md`.

## Task 6: Documentation And Final Verification

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/135-narration-mix-controls.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Document the browser workflow: set mix controls, save settings, generate timed narration audio, generate mixed video, verify evidence.
- [ ] Document defaults and validation ranges.
- [ ] Add a decision record explaining why mix controls are numeric settings before waveform editing.
- [ ] Run focused backend validations from Tasks 1-4.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Add narration mix controls`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationMixSettingsRepositoryTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=FfmpegNarratedVideoMixServiceTests,NarratedVideoServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
