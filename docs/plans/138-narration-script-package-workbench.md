# Narration Script Package Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let operators export, import, validate, and reuse a complete time-coded narration script package for a selected job so full-video demos can iterate on narration without manually retyping segments.

**Architecture:** Keep existing `narration_segments` persistence and workspace APIs as the source of truth. Add a metadata-only package service that serializes narration timing, text, voice preset choices, mix settings, catalog metadata, validation checks, and safe job references to JSON/Markdown/ZIP; add an import endpoint that validates the package against the current job duration and voice catalog before replacing the workspace. The React workbench gets import/export controls and a package preview without adding waveform editing, drag/drop editing, voice cloning, or media bytes.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, existing demo Bash scripts.

## Global Constraints

- This is one complete feature slice: backend package export/import, frontend workbench controls, demo script integration, docs, tests, validation, commits, and merge back to `main`.
- Use branch title `narration-script-package-workbench`; do not include `/` in the user-facing branch title.
- Keep package exports metadata-only: no generated audio/video bytes, transcript text, subtitle text, object keys, local filesystem paths, provider payloads, tokens, API keys, or credentials.
- Imported packages may contain narration text because that is the operator-authored script being restored; do not copy it into unrelated evidence reports or safe demo summaries.
- Preserve existing narration segment schema unless a test proves package import cannot be implemented with the current rows.
- Do not implement voice cloning, uploaded reference audio, voice preview playback, waveform editing, drag/drop timeline editing, or multitrack automation curves.
- Validate imported time ranges, overlaps, duration bounds, text length, and voice presets before replacing the current workspace.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Backend Script Package Export Contract

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationScriptPackageService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationScriptPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationScriptPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/narration-script-package`
- `GET /api/jobs/{jobId}/narration-script-package/markdown/download`
- `GET /api/jobs/{jobId}/narration-script-package/download`
- `NarrationScriptPackageVo` includes `jobId`, `targetLanguage`, `durationSeconds`, `segmentCount`, `totalCharacterCount`, `voiceSummary`, `defaultVoice`, `mixSettings`, `segments`, `checks`, `safeLinks`, `packageEntries`, and `safetyNotes`.

- [x] Write failing service tests for package JSON generated from an empty workspace, a valid two-segment workspace, and a workspace with intentional gaps.
- [x] Write failing controller tests for JSON, Markdown, and ZIP routes, including safe route names and package entries.
- [x] Implement package export from existing repositories/services, reusing narration workspace validation and voice catalog semantics.
- [x] Render Markdown with segment index, time range, duration, voice state, and character count; include script text only in this explicit script package, not in general evidence.
- [x] Build ZIP entries `manifest.json`, `narration-script-package.json`, `narration-script-package.md`, and `README.md`.
- [x] Verify no object keys, local paths, provider payloads, tokens, or media bytes are included.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration script package export`.

## Task 2: Backend Script Package Import And Validation

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ImportNarrationScriptPackageDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/ImportNarrationScriptPackageSegmentDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationScriptPackageImportVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationScriptPackageService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationScriptPackageServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationScriptPackageServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-script-package/import`
- Request accepts package metadata plus `segments`, `mixSettings`, and `replaceExisting=true`.
- Response returns `NarrationScriptPackageImportVo(jobId, importedSegmentCount, totalCharacterCount, voiceSummary, replacedExisting, warnings, workspace)`.

- [ ] Write failing tests for successful import replacing an existing workspace, preserving mix settings, and returning the refreshed workspace.
- [ ] Write failing tests for rejected imports: missing `replaceExisting=true`, overlapping ranges, end before start, ranges beyond source duration when duration is known, empty text, text over existing limits, and unknown voice presets.
- [ ] Implement import validation before mutation; if any row is invalid, keep the existing workspace unchanged.
- [ ] Reuse current narration workspace save path so timeline summary, mix settings, and voice catalog remain consistent.
- [ ] Return clear validation messages that the frontend can display without stack traces.
- [ ] Run `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests,LocalizationJobControllerTests,NarrationWorkspaceServiceTests`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Import narration script packages`.

## Task 3: React Import/Export Workbench

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `getNarrationScriptPackage`, `downloadNarrationScriptPackageMarkdown`, `downloadNarrationScriptPackageZip`, and `importNarrationScriptPackage`.
- Add `NarrationScriptPackage`, `NarrationScriptPackageSegment`, `NarrationScriptPackageImportResult`, and request DTO types.

- [ ] Write failing API tests for encoded route URLs, JSON fetch, Markdown download, ZIP download, and import POST payload.
- [ ] Write failing App tests for package preview, Markdown/ZIP export buttons, JSON paste/import flow, import validation errors, and refreshed workspace after successful import.
- [ ] Add a compact `Script package` panel inside `Narration workspace` with export summary, package checks, safe download actions, and an import textarea/file-like paste area.
- [ ] On import success, refresh workspace, narration evidence, and artifacts without reloading the page.
- [ ] Keep controls disabled when package JSON is invalid, `replaceExisting` is not acknowledged, or validation errors are returned.
- [ ] Keep the UI dense and workbench-like, following the existing segment table plus inspector pattern.
- [ ] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [ ] Run `npm run build`.
- [ ] Update `docs/progress/execution-log.md`.
- [ ] Commit with message `Add narration script package UI`.

## Task 4: Demo Script, Docs, And Final Verification

**Files:**
- Create: `scripts/demo/narration-script-package.sh`
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/plans/138-narration-script-package-workbench.md`
- Modify: `docs/progress/execution-log.md`

- [ ] Add terminal export command `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-script-package.sh`.
- [ ] Script downloads package JSON, Markdown, and ZIP into `/tmp/linguaframe-demo/`, prints metadata-only counts, voice summary, importability checks, and ZIP entries.
- [ ] Document that script packages are explicit operator-authored script artifacts and may include narration text, unlike general evidence packages.
- [ ] Update browser smoke checklist with export/import, validation, and refreshed workspace checks.
- [ ] Record decision to add script package reuse before waveform editing or drag/drop timeline controls.
- [ ] Run focused backend validations from Tasks 1-2.
- [ ] Run `mvn -pl LinguaFrame test`.
- [ ] Run `npm test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `bash -n scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [ ] Run `git diff --check`.
- [ ] Commit with message `Document narration script package workbench`.
- [ ] Merge feature branch back to `main`.
- [ ] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationScriptPackageServiceTests,LocalizationJobControllerTests,NarrationWorkspaceServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-script-package.sh scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
