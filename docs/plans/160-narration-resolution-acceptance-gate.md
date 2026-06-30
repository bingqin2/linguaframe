# Narration Resolution Acceptance Gate Implementation Plan

**Goal:** Make unresolved narration playback issues affect final demo acceptance, reviewer handoff, and terminal evidence instead of living only in the narration workspace.

**Why This Matters:** Playback resolution now tells operators what still needs revision, but the final acceptance gate can still look ready without considering those narration blockers. This slice connects the review loop to the final go/no-go path so a demo cannot be presented as ready while narration rows still require text revision, rerender, or review.

## Scope

- Add narration playback resolution as a required acceptance-gate check when narration rows exist.
- Add acceptance-gate evidence and links for resolution status, unresolved count, text revision count, rerender count, and unreviewed count.
- Let existing reviewer workspace, handoff portal, cockpit, completion/closure flows inherit the gate result through their current acceptance-gate dependency.
- Update browser acceptance-gate rendering tests so unresolved narration appears as a blocking check.
- Update terminal acceptance-gate summary output and tests to include narration resolution metadata.
- Update docs and smoke checklist.

## Non-Goals

- Do not synthesize narration audio, rerender video, call OpenAI/TTS, or mutate narration rows.
- Do not include narration text, reviewer note bodies, provider payloads, object keys, local paths, API keys, tokens, or media bytes in acceptance evidence.
- Do not change playback review/resolution save semantics.

## Implementation Tasks

1. Backend gate integration:
   - Modify `DemoAcceptanceGateServiceImpl` to inject `NarrationPlaybackReviewResolutionService`.
   - Build resolution once in `buildGate`.
   - Add required check `NARRATION_PLAYBACK_RESOLVED`:
     - `PASS` when no narration rows exist, or when resolution status is `READY`.
     - `FAIL` when resolution status is `BLOCKED` or `ATTENTION`.
     - Detail includes only resolution status and safe counts.
   - Add evidence rows:
     - `NARRATION_PLAYBACK_RESOLUTION_STATUS`
     - `NARRATION_PLAYBACK_UNRESOLVED_COUNT`
     - `NARRATION_PLAYBACK_TEXT_REVISION_COUNT`
     - `NARRATION_PLAYBACK_RERENDER_COUNT`
     - `NARRATION_PLAYBACK_UNREVIEWED_COUNT`
   - Add safe links to resolution JSON and Markdown endpoints.
   - Add safety note that acceptance includes metadata-only narration resolution.

2. Backend tests:
   - Extend or add `DemoAcceptanceGateServiceTests`.
   - Cover ready resolution passing the gate.
   - Cover unresolved `NEEDS_EDIT`/`NEEDS_RERENDER` blocking the gate.
   - Cover no narration rows not blocking non-narration jobs.
   - Assert gate output excludes narration text and reviewer note bodies.
   - Extend controller test if there is an existing acceptance gate JSON contract test.

3. Frontend API/UI tests:
   - Extend `demoAcceptanceGateFixture` with narration resolution check/evidence/link fields.
   - Add an App test proving the selected-job `Demo acceptance gate` panel displays the blocking narration resolution check and evidence.
   - Keep UI changes minimal if the panel already renders generic checks/evidence.

4. Terminal summary and demo integration:
   - Extend `print_demo_acceptance_gate_summary_file` in `scripts/demo/lib/linguaframe-demo.sh` to print narration resolution evidence/check/link lines.
   - Extend `scripts/demo/test-linguaframe-demo-client.sh` with narration resolution summary assertions and forbidden-string checks.
   - Ensure full Tears output naturally includes the new gate evidence through existing `demo-acceptance-gate.json`.

5. Docs and progress:
   - Update `README.md`, `docs/agent/docker-e2e-demo.md`, `docs/agent/smoke-test-checklist.md`, `docs/product/target-state.md`, `docs/product/roadmap.md`, and `docs/progress/execution-log.md`.

## Validation

- `mvn -pl LinguaFrame -Dtest=DemoAcceptanceGateServiceTests,LocalizationJobControllerTests#returnsDemoAcceptanceGate test`
- `npm --prefix frontend test -- --run src/App.test.tsx -t "acceptance gate"`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/demo-acceptance-gate.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A job with unresolved narration playback resolution cannot show final acceptance as `READY`.
- A job with ready narration playback resolution can pass the narration check.
- Jobs without narration rows are not blocked by narration-specific acceptance checks.
- Browser and terminal acceptance evidence show safe counts and links only.
- Reviewer workspace and handoff portal inherit the stricter acceptance status without separate wiring.
