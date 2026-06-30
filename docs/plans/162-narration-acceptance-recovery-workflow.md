# Narration Acceptance Recovery Workflow Implementation Plan

**Goal:** Turn a blocked narration acceptance gate into a browser-guided recovery workflow that helps the operator resolve unresolved narration rows and return the run to demo-ready status.

**Why This Matters:** The acceptance gate now exposes safe runbook steps, but the operator still needs to manually jump between acceptance gate, playback resolution, narration editing, render review, playback review, and re-checking final readiness. This slice makes that recovery path explicit and operational in the browser without auto-fixing content or calling providers unexpectedly.

## Scope

- Add a browser `Acceptance recovery` panel near `Demo acceptance gate` when runbook steps exist.
- Load and summarize the current acceptance gate, playback resolution, render review, playback review, narration evidence, and generated artifact readiness for the selected job.
- Show an ordered recovery checklist:
  - Open/focus unresolved playback-resolution rows.
  - Save narration after text/timing/voice edits.
  - Regenerate narration audio/video only through existing explicit actions.
  - Re-run render review, playback review, playback resolution, and acceptance gate.
- Add local UI actions that reuse existing frontend handlers:
  - Focus unresolved narration row.
  - Refresh recovery evidence.
  - Open safe JSON/Markdown routes.
  - Re-run acceptance gate refresh.
- Add backend-safe recovery summary JSON if the frontend needs one consolidated endpoint; otherwise compose from existing endpoints and keep backend unchanged.
- Add terminal support only if a consolidated backend endpoint is introduced.
- Update docs and smoke checklist with the recovery flow.

## Non-Goals

- Do not automatically rewrite narration text.
- Do not automatically save narration rows.
- Do not automatically call OpenAI/TTS providers, FFmpeg, Docker, upload APIs, or artifact generation.
- Do not expose narration text, reviewer note bodies, transcript/subtitle text, object keys, local paths, tokens, API keys, provider payloads, credentials, or media bytes.
- Do not change acceptance-gate pass/fail semantics.

## Preferred Architecture

- Prefer frontend composition from existing safe endpoints first:
  - `GET /api/jobs/{jobId}/demo-acceptance-gate`
  - `GET /api/jobs/{jobId}/narration-playback-review/resolution`
  - `GET /api/jobs/{jobId}/narration-render-review`
  - `GET /api/jobs/{jobId}/narration-playback-review`
  - `GET /api/jobs/{jobId}/narration-evidence`
- Add backend aggregation only if frontend composition becomes too duplicated or brittle.
- Keep all mutation behind existing explicit buttons: `Save narration`, `Generate narration audio`, `Generate narrated video`, and playback-review segment save.

## Backend Tasks

1. Decide whether a backend `NarrationAcceptanceRecoveryService` is needed after inspecting frontend duplication.
2. If needed, add metadata-only VOs:
   - `NarrationAcceptanceRecoveryVo`
   - `NarrationAcceptanceRecoveryStepVo`
   - `NarrationAcceptanceRecoveryLinkVo`
3. If needed, add `GET /api/jobs/{jobId}/narration-acceptance-recovery`.
4. Add focused service/controller tests proving:
   - unresolved narration rows produce ordered recovery steps.
   - ready narration rows produce low-noise status.
   - output excludes raw text, note bodies, local paths, tokens, provider payloads, object keys, and media bytes.

## Frontend Tasks

1. Add API/types only for a new backend endpoint if backend aggregation is chosen.
2. Add an `Acceptance recovery` panel inside the selected-job narration/acceptance area.
3. Render:
   - gate status and blocking runbook steps.
   - unresolved row counts and first unresolved row.
   - render/playback/recovery readiness badges.
   - safe next action text.
   - buttons for `Focus row`, `Refresh recovery`, `Open playback resolution`, and `Refresh acceptance gate`.
4. Reuse existing `focusPlaybackResolutionSegment`, narration save/render handlers, and acceptance gate refresh instead of creating parallel mutation paths.
5. Add App tests for:
   - blocked gate shows recovery checklist.
   - clicking focus selects the unresolved narration row.
   - unsafe text/note fixtures are not rendered.

## Terminal and Docs Tasks

- If no backend endpoint is added, keep terminal unchanged and document the browser recovery flow as the guided path.
- If a backend endpoint is added, create `scripts/demo/narration-acceptance-recovery.sh` and integrate safe summary tests.
- Update README, `docs/agent/docker-e2e-demo.md`, `docs/agent/smoke-test-checklist.md`, `docs/product/target-state.md`, `docs/product/roadmap.md`, and `docs/progress/execution-log.md`.

## Validation

- `mvn -pl LinguaFrame -Dtest=DemoAcceptanceGateServiceTests,NarrationPlaybackReviewResolutionServiceTests,LocalizationJobControllerTests test`
- `npm --prefix frontend test -- --run src/App.test.tsx -t "acceptance recovery"`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/demo-acceptance-gate.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A blocked narration acceptance gate gives the operator a browser-guided recovery checklist, not just passive links.
- The operator can focus unresolved narration rows from the recovery workflow.
- All provider/media-generating actions remain explicit and reuse existing buttons.
- Recovery evidence is metadata-only and safe.
- Existing acceptance gate, playback resolution, reviewer workspace, handoff portal, and full demo scripts continue to pass validation.
