# Session Recovery Board Implementation Plan

**Goal:** Add a session-level recovery board that lets a demo operator see which jobs need attention, which recovery path applies, and which per-job action or evidence link to open next.

**Why This Matters:** LinguaFrame now has strong per-job surfaces: run monitor, failure triage, stuck-job recovery, narration recovery, acceptance gate, and completion evidence. During a real demo, the operator still has to inspect jobs one at a time. This feature turns those pieces into one metadata-only operational board so a stalled, failed, blocked, active, or completed run can be triaged from a single place.

## Scope

Build `Demo session recovery board` as an operator-visible aggregate:

- `GET /api/operator/demo-session-recovery-board`
- `GET /api/operator/demo-session-recovery-board/markdown/download`
- Browser panel inside the run-day/operator workspace.
- Terminal script `scripts/demo/demo-session-recovery-board.sh`.
- Links from command center / presentation cockpit docs where useful.

The board must reuse existing job summaries, demo run monitor, stuck-job recovery, failure triage, acceptance gate, and completion/handoff links. It must not auto-run recovery actions.

## Backend Design

Create operator VOs for:

- `DemoSessionRecoveryBoardVo`
- `DemoSessionRecoveryBoardJobVo`
- `DemoSessionRecoveryBoardActionVo`
- `DemoSessionRecoveryBoardCheckVo`
- `DemoSessionRecoveryBoardLinkVo`

Create `DemoSessionRecoveryBoardService` and implementation in the operator service layer. It should classify recent jobs into:

- `RECOVER_NOW`: stale queued/processing/retryable failed or blocked acceptance.
- `WATCH`: active job that is still within expected timing.
- `READY_TO_PRESENT`: completed job with ready acceptance/handoff evidence.
- `NEEDS_REVIEW`: completed job with review/narration/acceptance blockers.
- `NO_ACTION`: terminal cancelled or non-demo job with no operator action.

Each row should include job id, status, current stage, age/elapsed metadata when available, classification, attention level, recommended next action, and safe links to existing per-job surfaces.

## Frontend Design

Add a dense workbench-style panel labeled `Demo session recovery board`, consistent with the existing command center and the user’s preferred desktop workbench direction:

- Summary counters for recover-now, watch, ready, needs-review, and no-action.
- A compact job table grouped by classification.
- Row actions that open existing safe links: stuck-job recovery, failure triage, run monitor, acceptance gate, recovery handoff, completion certificate, and demo package.
- Refresh and Markdown download controls.
- Empty state that points to demo run launcher.

The panel must not embed raw transcript, subtitle, narration text, reviewer notes, object keys, local paths, provider payloads, tokens, or API keys.

## Terminal Script

Add `scripts/demo/demo-session-recovery-board.sh` to download JSON and Markdown under `/tmp/linguaframe-demo/demo-session-recovery-board/`.

The script should print stable summary lines:

- `demoSessionRecoveryStatus`
- `demoSessionRecoveryRecoverNowCount`
- `demoSessionRecoveryWatchCount`
- `demoSessionRecoveryReadyCount`
- `demoSessionRecoveryNeedsReviewCount`
- `demoSessionRecoveryJob=<classification>:<jobId>:<status>:<recommendedAction>`

Set `LINGUAFRAME_DEMO_SESSION_RECOVERY_REPORT_ONLY=true` to avoid non-zero exit when recover-now rows exist.

## Testing

Backend:

- Service tests for mixed recent jobs: stale queued, active processing, retryable failed, completed ready, and completed blocked by acceptance/narration.
- Controller tests for JSON and Markdown download.
- Runtime dependency/OpenAPI route coverage if the repo has route contract tests for operator endpoints.

Frontend:

- API tests for the new endpoint and Markdown download.
- App tests proving the board renders grouped rows and links.
- App tests proving endpoint failure does not break the existing command center/dashboard.

Scripts:

- Demo-client tests for JSON/Markdown helper functions, summary lines, and redaction.
- Shell syntax check for the new script and shared helper.

## Documentation

Update:

- `README.md` with when to use the recovery board versus per-job stuck recovery.
- `docs/agent/docker-e2e-demo.md` with run-day recovery flow.
- `docs/agent/smoke-test-checklist.md` with browser and terminal validation.
- `docs/product/roadmap.md` and `docs/product/target-state.md` after implementation.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoSessionRecoveryBoardServiceTests,OperatorDashboardControllerTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx -t "session recovery board|demo session"`
- `bash -n scripts/demo/demo-session-recovery-board.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A run-day operator can see all recent jobs that need recovery or review without manually opening each job id.
- Recoverable stuck/failed jobs point to the existing stuck-job recovery cockpit rather than inventing new action semantics.
- Completed but blocked jobs point to acceptance/narration recovery evidence.
- Ready completed jobs point to completion/handoff evidence.
- Browser, backend, terminal, and docs expose the same safe metadata-only board.
- No recovery action is executed automatically.
