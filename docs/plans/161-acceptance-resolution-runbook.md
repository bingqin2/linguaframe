# Acceptance Resolution Runbook Implementation Plan

**Goal:** Turn a blocked demo acceptance gate into an actionable runbook that tells the operator exactly which safe surfaces to open next, especially for unresolved narration playback.

**Why This Matters:** The acceptance gate now blocks final readiness when narration playback resolution is unresolved, but operators still need to infer the next remediation steps from generic checks. This slice closes that operator loop: final gate output should provide safe, ordered commands and browser actions that move a blocked run back toward `READY`.

## Scope

- Add backend acceptance runbook steps derived from failed required checks and warning checks.
- Include narration-specific remediation when `NARRATION_PLAYBACK_RESOLVED` fails:
  - Open `Playback resolution`.
  - Focus unresolved narration rows in the workspace.
  - Revise/save narration or regenerate audio/video.
  - Re-run playback review and acceptance gate.
- Add safe JSON fields to `DemoAcceptanceGateVo` for runbook steps without exposing raw text or private note bodies.
- Render the runbook in the browser `Demo acceptance gate` panel.
- Print the runbook in `scripts/demo/demo-acceptance-gate.sh` summaries.
- Update reviewer/handoff docs so blocked gates have a clear recovery route.

## Non-Goals

- Do not auto-fix narration rows.
- Do not call OpenAI, TTS providers, FFmpeg, Docker, or upload APIs from the runbook.
- Do not include narration text, reviewer note bodies, object keys, local paths, tokens, API keys, provider payloads, or media bytes.
- Do not change acceptance gate pass/fail semantics from plan 160.

## Backend Shape

Create a new VO:

- `DemoAcceptanceGateRunbookStepVo`
  - `key`
  - `label`
  - `status`
  - `detail`
  - `primaryAction`
  - `safeCommand`
  - `safeLink`

Extend `DemoAcceptanceGateVo` with:

- `List<DemoAcceptanceGateRunbookStepVo> runbookSteps`

Runbook behavior:

- For every failed required check, add a blocking runbook step.
- For warning checks, add optional review steps after required steps.
- For `NARRATION_PLAYBACK_RESOLVED`, include a step with:
  - `primaryAction`: `Open playback resolution, focus unresolved narration rows, save revisions, regenerate narration media, then re-run acceptance gate.`
  - `safeCommand`: `LINGUAFRAME_DEMO_JOB_ID=<jobId> scripts/demo/narration-playback-review-resolution.sh`
  - `safeLink`: `/api/jobs/<jobId>/narration-playback-review/resolution`
- For `MEDIA_OUTPUT_AVAILABLE`, point to rerun/render output checks.
- For `QUALITY_EVALUATION_READY`, point to job diagnostics/OpenAI smoke proof when applicable.

## Frontend Behavior

- In `DemoAcceptanceGatePanel`, add a `Resolution runbook` section below attention checks.
- Show each step as compact rows with label/status/detail/action.
- Render `safeLink` as an anchor and `safeCommand` as monospace text.
- Keep it dense and operational; no marketing copy.

## Terminal Behavior

- Extend `print_demo_acceptance_gate_summary_file` to print:
  - `demoAcceptanceGateRunbook=<key>:<status>:<safe primary action>`
  - `demoAcceptanceGateRunbookCommand=<key>:<safeCommand>`
  - `demoAcceptanceGateRunbookLink=<key>:<safeLink>`
- Keep the existing redaction rules.

## Tests

- Backend service tests:
  - blocked narration resolution produces a narration runbook step.
  - ready gate still includes an empty or low-noise runbook.
  - runbook excludes narration text and reviewer note bodies.
- Controller JSON test:
  - acceptance gate response includes `runbookSteps`.
- Frontend App test:
  - blocked gate renders `Resolution runbook`, narration action, command, and link.
- Demo client test:
  - acceptance gate summary prints runbook lines and redacts unsafe markers.

## Validation

- `mvn -pl LinguaFrame -Dtest=DemoAcceptanceGateServiceTests,LocalizationJobControllerTests#returnsDemoAcceptanceGateForSelectedCompletedJob test`
- `npm --prefix frontend test -- --run src/App.test.tsx -t "acceptance gate"`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/demo-acceptance-gate.sh scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A blocked narration acceptance gate shows a clear remediation sequence in backend JSON, browser UI, and terminal output.
- Runbook output includes safe commands and links that move the operator toward resolving the blocker.
- No runbook field contains raw narration text, reviewer note bodies, provider payloads, object keys, local paths, tokens, API keys, or media bytes.
- Existing reviewer workspace and handoff portal continue to work with the extended acceptance gate response.
