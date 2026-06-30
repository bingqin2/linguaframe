# Session Narration Command Center Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate the session narration production board into the run-day command center and session evidence package so narration production readiness affects the top-level demo workflow and exported session bundle.

**Architecture:** Reuse the existing `SessionNarrationProductionBoardService` as a read-only dependency of the command center and evidence package. Extend the command-center VO, Markdown, browser panel, terminal summary, and session ZIP manifest rather than creating a parallel top-level surface.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, CLI script helpers, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, upload APIs, recovery actions, retry/cancel APIs, object-storage write paths, or database mutation paths from this integration.
- Do not expose raw transcript text, subtitle text, narration text, reviewer notes, provider payloads, object keys, local paths, tokens, secrets, API keys, or media bytes.
- Keep the browser UI dense and run-day oriented: compact status cards, grouped phase evidence, and safe links rather than marketing-style cards.
- The integration must move the demo goal forward by making narration production readiness visible in the operator's top-level workflow and downloadable session handoff.

---

## Feature Scope

Integrate `Session narration production board` into:

- `GET /api/operator/demo-session-command-center`
- `GET /api/operator/demo-session-command-center/markdown/download`
- `GET /api/operator/demo-session-evidence-package/download`
- Browser `Demo session command center`
- Terminal `scripts/demo/demo-session-command-center.sh`
- Terminal `scripts/demo/demo-session-evidence-package.sh`
- README, demo script docs, smoke checklist, roadmap, target-state, and execution log.

Out of scope:

- New narration editing actions.
- New render or provider calls.
- Changing per-job acceptance gate rules.
- Adding a new persistent table.
- Replacing the standalone session narration production board.

## Backend Design

Extend `DemoSessionCommandCenterVo` with narration production summary fields:

- `narrationProductionStatus`
- `narrationReadyCount`
- `narrationNeedsReviewCount`
- `narrationNeedsRenderCount`
- `narrationNeedsAuthoringCount`
- `narrationBlockedCount`
- `narrationNotApplicableCount`
- `narrationRecommendedNextAction`
- `narrationPrimaryAction`
- `narrationProductionLinks`

Modify `DemoSessionCommandCenterServiceImpl`:

- Inject `SessionNarrationProductionBoardService`.
- Load `sessionNarrationProductionBoardService.board(25)` in `commandCenter`.
- Add a `narration-production` phase to the phase checklist.
- Treat `BLOCKED` narration production as command-center `BLOCKED`.
- Treat `ATTENTION` narration production as command-center `ATTENTION` unless another phase is blocked.
- Keep `EMPTY` and `READY` as non-blocking.
- Include safe links to the standalone production board and Markdown download in evidence links.
- Add a `## Narration Production` Markdown section with status, counts, next action, and links.

Modify `DemoSessionEvidencePackageServiceImpl`:

- Inject `SessionNarrationProductionBoardService`.
- Add `narration-production-board.json` and `narration-production-board.md` to the ZIP.
- Include the new entries in `manifest.json` and README.
- Keep package generation metadata-only and read-only.

## Frontend Design

Extend `frontend/src/domain/jobTypes.ts`:

- Add command-center narration production fields using existing `SessionNarrationProductionBoardStatus`, action, and link types.

Update `frontend/src/App.tsx` command-center panel:

- Add a compact `Narration production` summary section near `Recovery`.
- Show status chip plus ready/review/render/authoring/blocked/not-applicable counts.
- Show recommended next action and primary safe link.
- Add production board links in the existing evidence/link area when provided.
- Do not display narration script text or reviewer note bodies.

## Terminal Script Design

Extend `scripts/demo/lib/linguaframe-demo.sh`:

- `print_demo_session_command_center_summary_file` prints:
  - `demoSessionCommandCenterNarrationProductionStatus`
  - `demoSessionCommandCenterNarrationReadyCount`
  - `demoSessionCommandCenterNarrationNeedsReviewCount`
  - `demoSessionCommandCenterNarrationNeedsRenderCount`
  - `demoSessionCommandCenterNarrationNeedsAuthoringCount`
  - `demoSessionCommandCenterNarrationBlockedCount`
  - `demoSessionCommandCenterNarrationNextAction`
- `print_demo_session_evidence_package_summary_file` detects `narration-production-board.json` and `narration-production-board.md` ZIP entries and prints `demoSessionEvidencePackageHasNarrationProductionBoard=true`.
- Existing forbidden-string redaction checks remain active and include narration text/reviewer-note markers.

No new top-level script is required because `scripts/demo/session-narration-production-board.sh` already exists.

## Testing

Backend focused tests:

- Extend `DemoSessionCommandCenterServiceTests`.
- Cover a blocked narration production board causing command-center `BLOCKED`.
- Cover an attention narration production board causing command-center `ATTENTION`.
- Assert the new phase, counts, primary action, evidence link, and Markdown section are present.
- Extend `DemoSessionEvidencePackageServiceTests` to assert ZIP entries include `narration-production-board.json` and `.md`, manifest entries include both files, and unsafe content is excluded.
- Extend `OperatorDashboardControllerTests` only if JSON route assertions need exact new fields.

Frontend focused tests:

- Extend command-center fixture in `frontend/src/App.test.tsx`.
- Assert `Narration production` renders status, counts, next action, and safe link.
- Extend API fixture tests only if command-center shape validation asserts the new fields.

Script tests:

- Extend `scripts/demo/test-linguaframe-demo-client.sh`.
- Assert command-center summary prints narration production status/count/action lines.
- Assert evidence-package summary detects narration production board ZIP entries.
- Assert unsafe JSON/ZIP fixtures containing narration text, reviewer notes, local paths, provider payloads, and tokens fail redaction checks.

## Documentation

Update:

- `README.md` command-center and session evidence package sections.
- `scripts/demo/README.md` command-center/session package guidance.
- `docs/agent/smoke-test-checklist.md` with browser and terminal checks.
- `docs/product/roadmap.md` session narration production status.
- `docs/product/target-state.md` run-day validation and session handoff target.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoSessionCommandCenterServiceTests,DemoSessionEvidencePackageServiceTests,OperatorDashboardControllerTests test`
- `npm --prefix frontend test -- --run src/App.test.tsx src/api/linguaframeApi.test.ts --testNamePattern "demo session command center|session evidence package"`
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/demo-session-command-center.sh scripts/demo/demo-session-evidence-package.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- A demo operator can see session narration production readiness from the top-level command center without opening the standalone board first.
- Blocked narration production state can block command-center readiness, and attention state can surface as attention.
- The session evidence package includes narration production JSON and Markdown entries.
- Browser, backend, CLI, ZIP, docs, and tests all expose the same metadata-only integration.
- The feature is committed on its branch and merged back to `main` after verification.

## Implementation Status

- Backend command center and evidence package now reuse `SessionNarrationProductionBoardService` for read-only narration production status, counts, actions, links, Markdown, and ZIP entries.
- Browser command center, API fixtures, terminal summaries, README guidance, smoke checklist, roadmap, target state, and execution log are updated for the integrated narration production surface.
- Validation follows the commands listed above before merging back to `main`.
