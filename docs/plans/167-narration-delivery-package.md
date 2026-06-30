# Narration Delivery Package Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a single narration delivery package that lets an operator verify and download all generated narration media and safe narration handoff evidence for a completed demo job.

**Architecture:** Build a read-only job-scoped delivery surface on top of existing narration evidence, script package, render review, playback review, playback resolution, recovery handoff, and artifact metadata services. The feature adds one backend summary endpoint, one ZIP download endpoint, one browser panel, one terminal script, tests, and documentation without generating audio/video or calling providers.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Jackson, ZIP streams, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one complete feature slice; implement backend, frontend, terminal, docs, tests, validation, commit, and merge together.
- Read-only only: do not call OpenAI, TTS providers, FFmpeg, object-storage writes, narration save APIs, or render APIs.
- Package output may include generated narration media links and safe metadata, but must not include media bytes, narration script bodies outside the existing explicit script package, reviewer note bodies, raw transcript text, raw subtitle text, object keys, local paths, provider payloads, tokens, API keys, or credentials.
- Keep the detailed editing workflow in `Narration workspace`; this feature is a delivery and verification surface.
- Use existing routes and services where possible instead of duplicating narration status logic.

---

## Feature Scope

Add a `Narration delivery package` surface with:

- `GET /api/jobs/{jobId}/narration-delivery-package`
- `GET /api/jobs/{jobId}/narration-delivery-package/markdown/download`
- `GET /api/jobs/{jobId}/narration-delivery-package/download`
- Browser panel under the selected job narration area.
- Terminal script `scripts/demo/narration-delivery-package.sh`.

The summary should answer:

- Is narration delivery `READY`, `ATTENTION`, `BLOCKED`, or `EMPTY`?
- Are `NARRATION_AUDIO` and `NARRATED_VIDEO` artifacts available?
- Is render review ready?
- Is playback review/resolution ready?
- Which safe packages and download routes should be handed off?
- What is the next action before presenting?

## Backend Design

Create VO records under `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/`:

- `NarrationDeliveryPackageVo`
- `NarrationDeliveryPackageCheckVo`
- `NarrationDeliveryPackageArtifactVo`
- `NarrationDeliveryPackageLinkVo`

Create BO:

- `StoredNarrationDeliveryPackageBo(byte[] content, String filename, String contentType)`

Create service interface and implementation:

- `NarrationDeliveryPackageService`
- `NarrationDeliveryPackageServiceImpl`

The service consumes:

- `LocalizationJobQueryService`
- `JobArtifactService`
- `NarrationEvidenceService`
- `NarrationScriptPackageService`
- `NarrationRenderReviewService`
- `NarrationPlaybackReviewService`
- `NarrationPlaybackReviewResolutionService`
- `NarrationRecoveryHandoffService`

ZIP entries:

- `manifest.json`
- `README.md`
- `narration-delivery-package.json`
- `narration-delivery-package.md`
- `narration-evidence.json`
- `narration-evidence.md`
- `narration-script-package.json`
- `narration-render-review.json`
- `narration-render-review.md`
- `narration-playback-review.json`
- `narration-playback-review.md`
- `narration-playback-resolution.json`
- `narration-playback-resolution.md`
- `narration-recovery-handoff.json`
- `narration-recovery-handoff.md`

Do not embed MP3/MP4 bytes. Include safe artifact download links for `NARRATION_AUDIO` and `NARRATED_VIDEO`.

## Frontend Design

Extend API types and client methods in `frontend/src/domain/jobTypes.ts` and `frontend/src/api/linguaframeApi.ts`.

Add a compact `Narration delivery package` panel near existing narration evidence/package controls:

- Status pill and recommended next action.
- Artifact rows for narration audio/video with download links when present.
- Checks for evidence, script package, render review, playback review, playback resolution, and recovery handoff.
- Buttons to refresh, download Markdown, and download ZIP.

Do not duplicate the full narration editor or playback-review table.

## Terminal Script Design

Add `scripts/demo/narration-delivery-package.sh`.

Default output directory:

- `/tmp/linguaframe-demo/narration-delivery-package/`

Write:

- `narration-delivery-package.json`
- `narration-delivery-package.md`
- `narration-delivery-package.zip`

Print metadata-only summary lines:

- `narrationDeliveryStatus`
- `narrationDeliveryPhase`
- `narrationDeliveryNextAction`
- `narrationDeliveryAudioReady`
- `narrationDeliveryVideoReady`
- `narrationDeliveryCheck`
- `narrationDeliveryPackageEntry`
- `narrationDeliveryJsonPath`
- `narrationDeliveryMarkdownPath`
- `narrationDeliveryZipPath`

Exit non-zero when status is `BLOCKED` unless `LINGUAFRAME_NARRATION_DELIVERY_PACKAGE_REPORT_ONLY=true`.

## Testing

Backend:

- Add `NarrationDeliveryPackageServiceTests` for READY, ATTENTION/BLOCKED, safe links, Markdown, and ZIP entries.
- Extend `LocalizationJobControllerTests` for JSON, Markdown, and ZIP endpoints.

Frontend:

- Extend `linguaframeApi.test.ts` for all three new routes.
- Add `App.test.tsx` coverage proving the panel renders status, artifact links, checks, and download buttons.

Scripts:

- Add shared helper tests in `scripts/demo/test-linguaframe-demo-client.sh`.
- Run shell syntax checks for the new script and shared helper.

## Documentation

Update:

- `README.md` narration workflow and terminal command list.
- `docs/agent/docker-e2e-demo.md` full-video run guidance.
- `docs/agent/smoke-test-checklist.md` focused validation commands.
- `docs/product/roadmap.md` and `docs/product/target-state.md`.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=NarrationDeliveryPackageServiceTests,LocalizationJobControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "narration delivery package"`
- `bash -n scripts/demo/narration-delivery-package.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- The operator can open one browser panel to verify narration delivery readiness and download safe handoff materials.
- The terminal script exports JSON, Markdown, and ZIP for the same package.
- The ZIP includes safe narration evidence and review metadata but never embeds media bytes or hidden secrets.
- Missing audio/video or unresolved playback review rows produce a clear `ATTENTION` or `BLOCKED` status with next action.
- Existing narration editing, rendering, recovery, and evidence routes continue to work unchanged.
