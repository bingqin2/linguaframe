# Narration Scene Board Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dense narration scene board that turns saved and draft narration rows into an operator-friendly multi-segment editor surface for adding explanatory voiceover at exact time ranges, reviewing readiness, and choosing the next render action.

**Architecture:** Build on the existing narration workspace, timeline, timing assistant, TTS preview, voice audition, mix automation, script package, render review, playback review, and delivery package. Add a read-only backend scene-board summary for saved rows, then use the React local draft to enrich the board with unsaved text/timing changes. The board should guide editing and rendering without calling providers or mutating state until the existing save/render buttons are used.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, CLI script, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, upload APIs, retry/cancel APIs, object-storage write paths, or database mutation paths from the scene board or CLI report.
- Do not print or export raw narration text from terminal scripts or metadata packages. Browser editor panels may display operator-authored text already loaded in the workspace.
- Preserve existing save semantics: quick import, table edits, timeline edits, scene-board focus actions, mix changes, timing fixes, and voice changes stay local until `Save narration`.
- Keep the UI dense and workbench-like, closer to a desktop TTS/narration editor: segment rail, timeline board, selected-row inspector, compact status chips, and direct actions.

---

## Feature Scope

The current narration workspace has the required editing primitives, but they are spread across many panels. This slice adds a composer layer that makes multi-timepoint narration feel like one coherent editor.

Implement:

- Backend scene-board VO/service/controller endpoint for saved narration metadata.
- Frontend scene board inside `Narration workspace` that combines saved metadata with local draft rows.
- Focus actions from board rows to the selected narration row, preview playhead, TTS preview, timing assistant, render preflight, and save/render controls.
- Terminal read-only scene-board report for demo operators.
- Focused backend, frontend, script, and docs validation.

Out of scope:

- Voice cloning or uploaded reference audio.
- New provider integration.
- Generating audio/video directly from the board.
- Persisting a separate scene/chapter model.
- Replacing existing quick script import/export or script package formats.

## Backend Design

Add `NarrationSceneBoardService` and implementation under `com.linguaframe.job.service`.

Expose:

- `getSceneBoard(String jobId)`.
- `sceneBoardMarkdown(String jobId)`.

Add VO records under `com.linguaframe.job.domain.vo`:

- `NarrationSceneBoardVo`
- `NarrationSceneBoardSegmentVo`
- `NarrationSceneBoardCheckVo`
- `NarrationSceneBoardActionVo`
- `NarrationSceneBoardLinkVo`

The board should include:

- `jobId`, `generatedAt`, `status`: `READY`, `ATTENTION`, `BLOCKED`, or `EMPTY`.
- Segment count, total narration duration, total span, coverage percent, gap count, overlap flag, voice count, override count, mix keyframe count.
- Per-segment metadata: index, start/end/duration, voice state, character count, timing status, mix override status, estimated reading density, readiness.
- Checks for blank text, invalid windows, overlap, missing voice catalog entry, long dense text, missing saved rows, missing audio evidence, and missing narrated video evidence.
- Recommended next actions that point to existing browser controls or API routes.
- Safe links to workspace, evidence, script package, render review, playback review, delivery package, and artifact downloads.

Add controller endpoints to the existing job API controller:

- `GET /api/jobs/{jobId}/narration/scene-board`
- `GET /api/jobs/{jobId}/narration/scene-board/markdown/download`

Markdown must be metadata-only and exclude narration text, reviewer notes, object keys, local paths, provider payloads, tokens, secrets, and media bytes.

## Frontend Design

Extend `frontend/src/domain/jobTypes.ts` and `frontend/src/api/linguaframeApi.ts` with scene-board types and API calls:

- `getNarrationSceneBoard(jobId)`
- `downloadNarrationSceneBoardMarkdown(jobId)`

Add state and loading/error handling inside `NarrationWorkspacePanel`.

Add `NarrationSceneBoardPanel` near the top of the narration workbench, before the detailed timeline/table panels. It should show:

- Top metrics: segments, span, coverage, gaps, voice readiness, mix readiness, audio/video readiness.
- Left scene rail with compact rows like `00:15-00:28`, status chip, voice chip, and text-length/density markers.
- Center timeline board that visualizes all narration windows and highlights the selected row.
- Right selected-segment summary with text preview from the local draft, voice/mix/timing state, and direct buttons:
  - Focus row.
  - Seek preview to start.
  - Preview selected TTS.
  - Run local timing fixes from the existing assistant.
  - Save narration.
  - Run render preflight.

The scene board should clearly distinguish saved backend metadata from unsaved local draft state. If the draft is dirty, show that the board is previewing local changes and saved metadata may lag until save.

## Terminal Script Design

Add `scripts/demo/narration-scene-board.sh`.

The script should:

- Require `LINGUAFRAME_DEMO_JOB_ID`.
- Download JSON and Markdown to `/tmp/linguaframe-demo/narration-scene-board/`.
- Print status, segment count, coverage percent, gaps, overlap flag, voice count, mix keyframe count, audio/video readiness, blocked checks, and recommended next action.
- Exit non-zero only when board status is `BLOCKED`, unless `LINGUAFRAME_NARRATION_SCENE_BOARD_REPORT_ONLY=true`.
- Never print narration text, reviewer notes, object keys, local paths, provider payloads, tokens, secrets, or media bytes.

Extend `scripts/demo/lib/linguaframe-demo.sh` only if a narrow shared download/status helper is missing.

## Testing

Backend focused tests:

- Add `NarrationSceneBoardServiceTests`.
- Extend controller tests for JSON and Markdown download routes.
- Assert empty, ready, attention, and blocked statuses.
- Assert metadata-only Markdown excludes narration text.
- Assert density, coverage, gap, overlap, voice, mix override, and evidence checks are computed from existing workspace/evidence inputs.

Frontend focused tests:

- Extend `frontend/src/api/linguaframeApi.test.ts` for scene-board JSON and Markdown routes.
- Extend `frontend/src/App.test.tsx` to assert the scene board renders metrics, segment rail, selected-row state, dirty draft notice, focus/seek/TTS/preflight/save actions, and blocked checks.
- Assert board actions reuse existing local handlers and do not call save, provider preview, preflight, or render until the matching button is clicked.

Script tests:

- Run `bash -n` on the new script and changed helper script.
- Extend `scripts/demo/test-linguaframe-demo-client.sh` if helper behavior changes.

## Documentation

Update:

- `README.md` narration workspace and OpenAI TTS demo notes.
- `scripts/demo/README.md` scene-board report command.
- `docs/agent/smoke-test-checklist.md` browser and terminal checks.
- `docs/product/roadmap.md` Phase 5 narration status.
- `docs/product/target-state.md` narration editor target state.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=NarrationSceneBoardServiceTests,JobControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration scene board|narration workspace"`
- `bash -n scripts/demo/narration-scene-board.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- Backend exposes metadata-only scene-board JSON and Markdown for a job.
- Browser narration workspace shows a dense scene board that makes multi-timepoint narration editing and readiness visible in one place.
- Scene-board focus, seek, preview, timing, save, preflight, and render actions reuse existing local/provider controls with clear paid-action boundaries.
- Terminal script exports scene-board JSON/Markdown and prints a safe demo-ready summary.
- Raw narration text is not emitted in terminal output or Markdown evidence.
- Focused and full validation pass before merge back to `main`.
