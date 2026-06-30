# Session Recovery Command Center Integration Implementation Plan

**Goal:** Integrate the demo session recovery board into the run-day command center and session evidence package so recovery state is visible in the top-level operator flow and exported session bundle.

**Why This Matters:** The recovery board now works as a standalone operator surface, but a real demo operator should not need to remember a second entry point. This slice makes recovery state part of the command center, evidence package, terminal command-center export, and browser run-day workflow.

## Scope

Build one complete operator-visible integration feature:

- Extend `GET /api/operator/demo-session-command-center` with recovery-board status, counters, primary recovery action, and safe evidence links.
- Add recovery-board JSON/Markdown into `GET /api/operator/demo-session-evidence-package/download`.
- Update the browser `Demo session command center` panel to show a compact recovery summary and link to the full recovery board.
- Update terminal command-center and evidence-package scripts so the recovery board appears in session-level exported evidence.
- Keep `Demo session recovery board` as the detailed drill-down surface.

This feature must not add new recovery action semantics. It only surfaces recovery state and links to existing recovery/evidence routes.

## Backend Design

Modify `DemoSessionCommandCenterServiceImpl` to depend on `DemoSessionRecoveryBoardService`.

Add command-center fields:

- `recoveryStatus`
- `recoverNowCount`
- `watchCount`
- `needsReviewCount`
- `readyCount`
- `recoveryRecommendedNextAction`
- `recoveryPrimaryAction`
- `recoveryLinks`

Command-center `overallStatus` should become `BLOCKED` when the recovery board reports recover-now rows, even if other session phases are ready. Add a new phase named `recovery-board` with detail text and next action derived from the board.

Modify `DemoSessionEvidencePackageServiceImpl` to include:

- `recovery-board.json`
- `recovery-board.md`

Update manifest/package README to mention recovery-board evidence and preserve existing safe-content exclusions.

## Frontend Design

Extend `DemoSessionCommandCenter` TypeScript types and fixtures.

Update the browser `Demo session command center` panel:

- Add a compact `Recovery` block near the phase/status summary.
- Show recovery status, recover-now/watch/review/ready counts, and recommended next action.
- Link to `/api/operator/demo-session-recovery-board` and `/api/operator/demo-session-recovery-board/markdown/download`.
- Keep existing command center actions and session package download behavior.

Do not duplicate the full job table from the recovery board in the command center.

## Terminal Script Design

Update `scripts/demo/demo-session-command-center.sh` summary output with:

- `demoSessionCommandCenterRecoveryStatus`
- `demoSessionCommandCenterRecoverNowCount`
- `demoSessionCommandCenterRecoveryNextAction`

Update `scripts/demo/demo-session-evidence-package.sh` validation or summary output to confirm the ZIP contains recovery-board files.

Extend shared script tests to verify:

- Recovery fields print from command-center JSON.
- Session evidence package ZIP includes recovery-board entries.
- Summary output does not expose filenames, local paths, tokens, object keys, provider payloads, transcripts, subtitles, reviewer notes, narration text, or media bytes.

## Testing

Backend:

- Add/extend `DemoSessionCommandCenterServiceTests` for recovery-board integration and overall `BLOCKED` propagation.
- Extend `DemoSessionEvidencePackageServiceTests` for recovery-board ZIP entries and manifest references.
- Extend controller tests only if response fields or package contents need route-level proof.
- Keep runtime/OpenAPI tests unchanged unless routes change.

Frontend:

- Extend API type fixtures for new command-center recovery fields.
- Add App test proving the command center renders recovery status/counters/links and still downloads command center and session package.
- Keep the standalone recovery-board test unchanged.

Scripts:

- Extend `scripts/demo/test-linguaframe-demo-client.sh`.
- Run shell syntax checks for command-center, evidence-package, recovery-board, and shared helper scripts.

## Documentation

Update:

- `README.md` command-center and session evidence package sections.
- `docs/agent/docker-e2e-demo.md` run-day recovery flow.
- `docs/agent/smoke-test-checklist.md` focused validation commands.
- `docs/product/roadmap.md` and `docs/product/target-state.md` after implementation.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoSessionCommandCenterServiceTests,DemoSessionEvidencePackageServiceTests,OperatorDashboardControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "demo session command center|session recovery board"`
- `bash -n scripts/demo/demo-session-command-center.sh scripts/demo/demo-session-evidence-package.sh scripts/demo/demo-session-recovery-board.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- The command center shows whether the session has recover-now rows without opening the standalone recovery board.
- A recover-now board makes the command center overall status `BLOCKED`.
- The session evidence ZIP includes recovery-board JSON and Markdown.
- Browser, backend, terminal, and docs describe the same recovery integration.
- No automatic recovery actions are executed.
- Safe-output rules remain enforced for local paths, object keys, tokens, provider payloads, raw transcript/subtitle text, narration text, reviewer notes, and media bytes.
