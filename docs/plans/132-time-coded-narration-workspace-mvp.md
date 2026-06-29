# Time-Coded Narration Workspace MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a first complete custom narration workspace so a completed job can store time-coded explanatory text segments, synthesize them with the existing TTS provider boundary, and expose narration audio/evidence in browser and terminal demos.

**Architecture:** Treat narration as a job-level human-authored draft layer, separate from generated subtitles and reviewed subtitle artifacts. Store time-coded narration segments in a dedicated table, generate one narration audio artifact from ordered segments through the existing `TtsProvider`, and expose metadata-only evidence plus media delivery links. Defer narrated-video muxing, audio ducking, waveform editing, drag/drop timeline editing, and voice cloning to later slices.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC/Flyway, JUnit 5 + MockMvc, React + Vite + TypeScript, Vitest/jsdom, Bash demo helpers.

## Global Constraints

- This is one complete feature slice: persistence, backend APIs, TTS generation, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- Keep narration separate from subtitles: narration segments must not mutate transcript, target subtitles, subtitle drafts, reviewed subtitle artifacts, generated burned video, reviewed burned video, or handoff packages by default.
- First slice produces `NARRATION_AUDIO` and metadata evidence only; do not implement narrated-video muxing, background audio ducking, drag/drop timeline editing, waveform editing, voice cloning, or lip sync.
- Evidence exports and terminal summaries must be metadata-only and exclude raw transcript text, generated subtitle text, corrected subtitle text, narration script text bodies, provider payloads, object keys, local filesystem paths, tokens, API keys, and media bytes.
- Follow the `fish-tts-desktop` reference style for the browser workspace where appropriate: dense workbench layout, segment table, compact controls, inspector-like edit area, light borders, Lucide icons, and low-noise desktop-tool presentation.
- Every implementation task must include focused validation and update `docs/progress/execution-log.md`.

---

## Task 1: Persist Time-Coded Narration Segments

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V28__create_narration_segments.sql`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/NarrationSegmentRecord.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/dto/SaveNarrationSegmentsRequest.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationSegmentVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationWorkspaceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/repository/NarrationSegmentRepository.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationWorkspaceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationWorkspaceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationWorkspaceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/NarrationSegmentRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/narration-workspace`
- `PUT /api/jobs/{jobId}/narration-workspace`
- `DELETE /api/jobs/{jobId}/narration-workspace`
- `SaveNarrationSegmentsRequest(List<Segment> segments)`
- `Segment(int index, BigDecimal startSeconds, BigDecimal endSeconds, String text, String voice)`
- `NarrationWorkspaceVo(String jobId, String status, int segmentCount, BigDecimal totalDurationSeconds, int totalCharacterCount, boolean generationReady, List<NarrationSegmentVo> segments, List<String> safetyNotes)`

- [x] Add Flyway table `narration_segments` with columns `job_id`, `segment_index`, `start_seconds DECIMAL(10,3)`, `end_seconds DECIMAL(10,3)`, `text TEXT`, `voice VARCHAR(64) NULL`, `created_at`, `updated_at`, and unique key `(job_id, segment_index)`.
- [x] Add repository methods `replaceSegments(String jobId, List<NarrationSegmentRecord> segments)`, `findByJobId(String jobId)`, and `deleteByJobId(String jobId)`.
- [x] Validate save requests: max 20 segments, contiguous indexes starting at 0, `startSeconds >= 0`, `endSeconds > startSeconds`, max segment length 1000 chars, text nonblank, optional voice max 64 chars, no overlapping segments after sorting by start time.
- [x] Return workspace status `EMPTY`, `DRAFT_READY`, or `INVALID` from saved state and validation outcome.
- [x] Keep raw narration text only in the workspace API and editing UI; evidence APIs in later tasks must use counts and durations only.
- [x] Add tests for valid multi-segment save, overlap rejection, invalid time rejection, too-long text rejection, clear behavior, and repository replacement.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,NarrationSegmentRepositoryTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 2: Generate Narration Audio Through Existing TTS

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/JobArtifactType.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationGenerationVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationAudioService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationAudioServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationAudioServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Interfaces:**
- `POST /api/jobs/{jobId}/narration-workspace/generate-audio`
- Add `JobArtifactType.NARRATION_AUDIO`
- `NarrationGenerationVo(String jobId, String artifactId, String filename, String contentType, long sizeBytes, int segmentCount, int totalCharacterCount, BigDecimal totalTimelineDurationSeconds, String voiceSummary, String status)`

- [x] Add `NARRATION_AUDIO` artifact type and include it in artifact list/archive behavior through existing generic artifact APIs.
- [x] Compose TTS input from saved segments in timeline order using safe delimiters such as `[00:15.000-00:28.000]` only inside provider input; do not store this composed text in evidence surfaces.
- [x] Use segment voice when all segments share one nonblank voice; otherwise use job/default voice and expose `voiceSummary=MIXED_OR_DEFAULT`.
- [x] Reuse existing `TtsProvider`, `CostBudgetGuardService`, `ModelCallOperation.TTS`, and TTS model-call audit behavior; do not create a new provider abstraction.
- [x] Store result as `narration-audio.mp3` with artifact type `NARRATION_AUDIO`.
- [x] Do not create or replace `DUBBING_AUDIO`, `DUBBED_VIDEO`, `BURNED_VIDEO`, or `REVIEWED_BURNED_VIDEO`.
- [x] Add tests for successful generation, empty workspace rejection, artifact type isolation, selected voice behavior, and provider failure propagation as a safe API error.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationAudioServiceTests,LocalizationJobControllerTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 3: Narration Evidence, Runtime Routes, And Handoff Links

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/NarrationEvidenceCheckVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/NarrationEvidenceService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/NarrationEvidenceServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/controller/LocalizationJobController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobEvidenceReportServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoHandoffPortalServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/NarrationEvidenceServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobEvidenceReportServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoHandoffPortalServiceTests.java`

**Interfaces:**
- `GET /api/jobs/{jobId}/narration-evidence`
- `GET /api/jobs/{jobId}/narration-evidence/markdown/download`
- `GET /api/jobs/{jobId}/narration-evidence/download`
- `NarrationEvidenceVo` includes status, segment count, character count, total timeline duration, narration audio artifact readiness, safe links, package entries, checks, and safety notes.

- [x] Return evidence status `READY` when at least one segment exists and `NARRATION_AUDIO` exists; `ATTENTION` when draft segments exist but no audio exists; `BLOCKED` when no narration segments exist.
- [x] Render Markdown without raw narration text bodies, transcript text, subtitle text, provider payloads, object keys, local paths, tokens, API keys, or media bytes.
- [x] Build ZIP with `manifest.json`, `narration-evidence.md`, `narration-summary.json`, and `README.md`.
- [x] Add safe narration links to job evidence Markdown and demo handoff portal package inventory.
- [x] Add runtime required routes for the three narration evidence endpoints and workspace/generate endpoints.
- [x] Add tests for READY/ATTENTION/BLOCKED, ZIP entries, forbidden marker absence, and runtime route coverage.
- [x] Run `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,RuntimeDependencyControllerTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`.
- [x] Update `docs/progress/execution-log.md`.

## Task 4: React Narration Workspace UI

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- `getNarrationWorkspace(jobId: string): Promise<NarrationWorkspace>`
- `saveNarrationWorkspace(jobId: string, request: SaveNarrationWorkspaceRequest): Promise<NarrationWorkspace>`
- `clearNarrationWorkspace(jobId: string): Promise<NarrationWorkspace>`
- `generateNarrationAudio(jobId: string): Promise<NarrationGeneration>`
- `getNarrationEvidence(jobId: string): Promise<NarrationEvidence>`
- `downloadNarrationEvidenceMarkdown(jobId: string): Promise<Blob>`
- `downloadNarrationEvidenceZip(jobId: string): Promise<Blob>`

- [x] Add TypeScript types for narration segments, workspace, generation result, and evidence.
- [x] Add API helper tests for workspace get/save/clear, audio generation, evidence JSON, Markdown download, and ZIP download.
- [x] In selected job detail, add a `Narration workspace` panel near media delivery/review workflow.
- [x] Follow the `fish-tts-desktop`-inspired pattern: compact segment table with start/end/text/voice/status columns, add/delete row controls, and an inspector-style edit area for selected row details.
- [x] Provide validation hints for overlap, invalid time range, empty text, and character limits before save.
- [x] Add actions: `Save narration`, `Clear narration`, `Generate narration audio`, `Refresh evidence`, `Download evidence Markdown`, and `Download evidence ZIP`.
- [x] Keep raw narration text only in the editing workspace; evidence panel must show counts, durations, status, links, and safety notes only.
- [x] Extend media delivery to show `NARRATION_AUDIO` as a playable audio card.
- [x] Add Vitest coverage for segment editing payload, validation messaging, audio generation action, evidence rendering, downloads, and `NARRATION_AUDIO` media card.
- [x] Run `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`.
- [x] Run `npm run build`.
- [x] Update `docs/progress/execution-log.md`.

## Task 5: Terminal Script, Demo Docs, And Product Docs

**Files:**
- Create: `scripts/demo/narration-evidence.sh`
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
- `LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-evidence.sh`
- Default output directory: `/tmp/linguaframe-demo/narration-evidence/`

- [x] Add Bash helpers for narration workspace JSON, narration evidence JSON/Markdown/ZIP, and `print_narration_evidence_summary_file`.
- [x] Add standalone script that exits non-zero on `BLOCKED` unless `LINGUAFRAME_NARRATION_EVIDENCE_REPORT_ONLY=true`.
- [x] Extend deterministic, OpenAI smoke, and full Tears scripts to export narration evidence when a job has narration segments; skip cleanly when evidence is `BLOCKED`.
- [x] Document narration as separate from subtitle dubbing, reviewed subtitle publishing, and dubbed-video delivery.
- [x] Document first-slice limitation: narration audio is generated, narrated-video muxing comes later.
- [x] Update smoke checklist with browser workspace expectations, terminal output, ZIP entries, `NARRATION_AUDIO` media card, and safety exclusions.
- [x] Run `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`.
- [x] Update `docs/progress/execution-log.md`.

## Task 6: Final Verification, Commit, And Merge

**Files:**
- Modify: `docs/plans/132-time-coded-narration-workspace-mvp.md`
- Modify: `docs/progress/execution-log.md`

- [x] Mark completed plan tasks.
- [x] Run focused backend validation from Tasks 1-3.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `npm test -- --run`.
- [x] Run `npm run build`.
- [x] Run script syntax validation from Task 5.
- [x] Run `git diff --check`.
- [x] Commit with message `Add time-coded narration workspace`.
- [x] Merge feature branch back to `main`.
- [x] Confirm `git status --short --branch` is clean on `main`.

## Validation Plan

- `mvn -pl LinguaFrame test -Dtest=NarrationWorkspaceServiceTests,NarrationSegmentRepositoryTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationAudioServiceTests,LocalizationJobControllerTests`
- `mvn -pl LinguaFrame test -Dtest=NarrationEvidenceServiceTests,RuntimeDependencyControllerTests,JobEvidenceReportServiceTests,DemoHandoffPortalServiceTests`
- `npm test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `npm run build`
- `bash -n scripts/demo/narration-evidence.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-openai-smoke.sh scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/lib/linguaframe-demo.sh`
- `mvn -pl LinguaFrame test`
- `npm test -- --run`
- `git diff --check`
