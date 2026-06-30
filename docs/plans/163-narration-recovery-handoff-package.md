# Narration Recovery Handoff Package Implementation Plan

**Goal:** Turn narration-blocked acceptance recovery into a safe downloadable handoff package that a reviewer or operator can inspect offline.

**Why This Matters:** The browser `Acceptance recovery` panel now guides an operator through blocked narration playback resolution, but the recovery state is not yet a durable handoff artifact. This slice makes the recovery evidence exportable from backend JSON/Markdown/ZIP, visible in the frontend, available in demo scripts, and linked from final handoff surfaces without exposing narration text or private notes.

## Scope

- Add a backend metadata-only `NarrationRecoveryHandoffService`.
- Expose:
  - `GET /api/jobs/{jobId}/narration-recovery-handoff`
  - `GET /api/jobs/{jobId}/narration-recovery-handoff/markdown/download`
  - `GET /api/jobs/{jobId}/narration-recovery-handoff/download`
- Aggregate existing safe data:
  - demo acceptance gate and runbook steps
  - narration playback resolution counts and unresolved segment metadata
  - narration render review status
  - narration playback review status/counts
  - narration evidence audio/video readiness
- Generate a ZIP with `narration-recovery-handoff.json`, `narration-recovery-handoff.md`, `acceptance-gate.json`, `playback-resolution.json`, `README.md`, and `manifest.json`.
- Add a browser `Recovery handoff` section to the existing `Acceptance recovery` panel with status, package inventory, safe links, and download actions.
- Add `scripts/demo/narration-recovery-handoff.sh` plus demo-client redaction tests.
- Link the recovery handoff from `Demo handoff portal` when narration recovery applies.
- Update README, Docker demo guidance, smoke checklist, roadmap, target state, and execution log.

## Non-Goals

- Do not modify acceptance gate pass/fail semantics.
- Do not save narration rows, edit playback review decisions, or auto-focus frontend rows from backend output.
- Do not call OpenAI, TTS providers, FFmpeg, Docker, upload APIs, or artifact generation.
- Do not include narration text, reviewer note bodies, transcript/subtitle text, corrected draft text, object keys, local paths, tokens, API keys, provider payloads, credentials, or media bytes.

## Backend Shape

Create VOs:

- `NarrationRecoveryHandoffVo`
  - `jobId`, `videoId`, `generatedAt`, `status`, `phase`, `headline`, `recommendedNextAction`
  - `acceptanceGateStatus`, `playbackResolutionStatus`
  - `unresolvedSegmentCount`, `textRevisionRequiredCount`, `rerenderRequiredCount`, `unreviewedSegmentCount`
  - `audioReady`, `videoReady`
  - `checks`, `steps`, `safeLinks`, `packageEntries`, `safetyNotes`
- `NarrationRecoveryHandoffCheckVo`
  - `key`, `label`, `status`, `detail`, `nextAction`, `required`
- `NarrationRecoveryHandoffStepVo`
  - `key`, `label`, `status`, `action`, `safeCommand`, `safeLink`
- `NarrationRecoveryHandoffLinkVo`
  - `kind`, `label`, `href`, `contentType`, `description`

Service rules:

- `READY`: acceptance gate is not blocked by narration playback and playback resolution is ready or not applicable.
- `BLOCKED`: acceptance gate has a failed `NARRATION_PLAYBACK_RESOLVED` required check.
- `ATTENTION`: narration recovery data exists but non-required review evidence needs attention.
- Steps are derived from acceptance-gate runbook plus playback-resolution unresolved segment metadata.
- Output must be metadata-only. Segment rows may include index, timing, voice, issue category, resolution status, and safe action, but never text or reviewer note bodies.

## Frontend Shape

- Extend the current `Acceptance recovery` panel instead of adding another top-level card.
- Render a compact `Recovery handoff` area:
  - status and recommended next action
  - package entry count
  - safe JSON/Markdown/ZIP links
  - download buttons using existing browser link patterns
  - no raw narration text or note bodies
- Add API client methods/types only for the new backend endpoint.
- Keep all provider/media mutations behind existing explicit narration render buttons.

## Terminal Shape

- Add `scripts/demo/narration-recovery-handoff.sh`.
- The script should:
  - require `LINGUAFRAME_DEMO_JOB_ID` or a first positional job id
  - download JSON, Markdown, and ZIP under `/tmp/linguaframe-demo/narration-recovery-handoff/`
  - print safe summary lines such as `narrationRecoveryHandoffStatus`, `narrationRecoveryHandoffUnresolved`, `narrationRecoveryHandoffEntry`
  - fail when status is `BLOCKED` unless `LINGUAFRAME_NARRATION_RECOVERY_HANDOFF_REPORT_ONLY=true`
- Integrate the script into the full Tears demo after playback resolution and before final handoff portal export.

## Tests

- Backend service tests:
  - blocked narration recovery produces `BLOCKED`, ordered steps, safe links, and ZIP package entries.
  - ready/no-narration state produces low-noise `READY`.
  - unsafe markers from narration text, reviewer notes, object keys, local paths, tokens, and provider payloads are absent from JSON/Markdown/ZIP.
- Controller tests:
  - JSON, Markdown, and ZIP endpoints return expected content types and filenames.
- Frontend App test:
  - `Acceptance recovery` renders `Recovery handoff`, package entries, and safe download links.
  - unsafe fixture strings are not rendered.
- Demo client tests:
  - terminal summary prints recovery handoff lines.
  - redaction guard fails if unsafe markers appear.

## Validation

- `mvn -pl LinguaFrame -Dtest=NarrationRecoveryHandoffServiceTests,LocalizationJobControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "recovery handoff|narration recovery"`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/narration-recovery-handoff.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A narration-blocked final acceptance gate has a safe downloadable recovery handoff package.
- Browser, backend, terminal, and docs all point to the same recovery evidence.
- The package gives an operator enough metadata to resolve the blocked rows and re-run acceptance without seeing raw narration text or private reviewer notes.
- Existing acceptance gate, acceptance recovery, playback resolution, handoff portal, and full demo scripts continue to pass validation.
