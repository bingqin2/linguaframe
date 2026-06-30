# Narration Final Handoff Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the narration delivery package part of the final demo acceptance, reviewer workspace, handoff portal, and full Tears demo export flow.

**Architecture:** Keep `NarrationDeliveryPackageService` as the source of narration handoff truth, then surface its readiness, safe links, and package entries through the existing final-demo aggregation services. The feature remains read-only and metadata-only; it does not render media, synthesize speech, save narration rows, or call providers.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, ZIP streams, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, terminal scripts, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, render APIs, narration save APIs, or object-storage write paths.
- Do not embed media bytes, raw transcript text, raw subtitle text, corrected draft text, narration text outside explicit script-package entries, reviewer note bodies, object keys, local paths, provider payloads, tokens, API keys, or credentials.
- Reuse `NarrationDeliveryPackageService`; do not duplicate delivery-readiness rules in acceptance, reviewer, portal, or script code.
- Keep the delivery package as a final handoff signal, not an authoring or playback-review editor.

---

## Feature Scope

The previous slice created the standalone `Narration delivery package`. This slice wires it into the final demo path so an operator can move from acceptance to reviewer handoff without hunting for separate narration exports.

Implement:

- Demo acceptance gate delivery-package check, evidence metric, and safe link.
- Demo reviewer workspace delivery-package check, package entry, and safe link.
- Demo handoff portal delivery-package package entry, safe link, and static ZIP/index reference.
- Full Tears demo script export of the narration delivery package when narration render is enabled.
- Browser panels that show delivery-package readiness inside acceptance, reviewer workspace, and handoff portal areas.
- Focused backend, frontend, script, docs, and full validation coverage.

Out of scope:

- New narration authoring controls.
- New voice cloning or reference-audio upload.
- Changing playback-resolution rules.
- Embedding generated audio/video into ZIP packages.

## Backend Design

### Acceptance Gate

Modify `DemoAcceptanceGateServiceImpl` to call `NarrationDeliveryPackageService.getPackage(jobId)` after existing narration playback-resolution evaluation.

Add a warning check:

- Key: `NARRATION_DELIVERY_PACKAGE_READY`
- Required: `false`
- `PASS` when delivery status is `READY` or `EMPTY`.
- `WARN` when delivery status is `ATTENTION`.
- `FAIL` when delivery status is `BLOCKED`.

Add evidence:

- `NARRATION_DELIVERY_PACKAGE_STATUS`
- `NARRATION_DELIVERY_AUDIO_READY`
- `NARRATION_DELIVERY_VIDEO_READY`
- `NARRATION_DELIVERY_PACKAGE_ENTRY_COUNT`

Add safe links:

- JSON: `/api/jobs/{jobId}/narration-delivery-package`
- Markdown: `/api/jobs/{jobId}/narration-delivery-package/markdown/download`
- ZIP: `/api/jobs/{jobId}/narration-delivery-package/download`

If the package is blocked and narration rows exist, keep the gate's existing required playback-resolution blocker as the hard stop, but point the runbook toward the delivery package after resolution is ready.

### Reviewer Workspace

Modify `DemoReviewerWorkspaceServiceImpl` to include narration delivery as an optional evidence/check block:

- Add a check with key `NARRATION_DELIVERY_PACKAGE`.
- Add package entries for JSON, Markdown, and ZIP.
- Add safe links to the delivery package routes.
- Include delivery status and next action in Markdown.

The reviewer workspace ZIP should include the delivery JSON and Markdown summaries, but not the delivery ZIP as a nested binary.

### Handoff Portal

Modify `DemoHandoffPortalServiceImpl` to include narration delivery in:

- Portal package entries.
- Portal safe links.
- Static `index.html` link list.
- Portal ZIP entries: `narration-delivery-package.json` and `narration-delivery-package.md`.

Do not fail the whole portal when narration delivery is `EMPTY`; treat it as no narration rows. Use `ATTENTION` for a visible warning and `BLOCKED` only when the existing portal status is already blocked by final acceptance requirements.

## Frontend Design

Extend existing selected-job renderers in `frontend/src/App.tsx` without creating a new top-level workflow.

Acceptance gate panel:

- Show the `NARRATION_DELIVERY_PACKAGE_READY` check when present.
- Show the delivery safe links alongside existing evidence/package links.
- Keep copy short: status, next action, and download links.

Reviewer workspace panel:

- Show the delivery-package check and package entries in the existing package/evidence sections.
- Use the current dense workbench style, with compact rows and restrained status pills.

Handoff portal panel:

- Show the delivery package as one final handoff artifact in the portal package list.
- Preserve existing Markdown/ZIP download actions.

No marketing hero, no new landing page, and no decorative UI.

## Terminal Script Design

Modify `scripts/demo/docker-e2e-tears-of-steel-full.sh`:

- When `LINGUAFRAME_RENDER_NARRATION_DEMO=true`, run `scripts/demo/narration-delivery-package.sh` after playback review/resolution and before final reviewer workspace/handoff portal exports.
- Write outputs under `/tmp/linguaframe-demo/tears-of-steel-full/narration-delivery-package/`.
- Use `LINGUAFRAME_NARRATION_DELIVERY_PACKAGE_REPORT_ONLY=true` for the full demo script so a non-ready delivery report can still be collected when the run is intentionally diagnostic.
- Print the delivery package JSON, Markdown, and ZIP paths in the same style as existing full-demo evidence outputs.

Do not change standalone `scripts/demo/narration-delivery-package.sh` behavior except for small helper reuse if needed.

## Testing

Backend focused tests:

- Extend `DemoAcceptanceGateServiceTests` to assert delivery status evidence, check status mapping, and safe links.
- Extend `DemoReviewerWorkspaceServiceTests` to assert delivery package entries, Markdown mention, and ZIP entries.
- Extend `DemoHandoffPortalServiceTests` to assert delivery package links, index entry, and ZIP entries.

Frontend focused tests:

- Extend `frontend/src/App.test.tsx` selected-job coverage for acceptance gate, reviewer workspace, and handoff portal delivery links/checks.
- Extend `frontend/src/api/linguaframeApi.test.ts` only if new client methods or typed fields are needed.

Script tests:

- Extend `scripts/demo/test-linguaframe-demo-client.sh` or add a focused shell test for full Tears output wiring if existing helpers already cover it.
- Run `bash -n` on changed scripts.

## Documentation

Update:

- `README.md` normal browser order and terminal command guidance.
- `docs/agent/docker-e2e-demo.md` full Tears narration handoff flow.
- `docs/agent/smoke-test-checklist.md` acceptance/reviewer/portal checks.
- `docs/product/roadmap.md` narration and final handoff status.
- `docs/product/target-state.md` final demo readiness and offline handoff expectations.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoAcceptanceGateServiceTests,DemoReviewerWorkspaceServiceTests,DemoHandoffPortalServiceTests test`
- `npm --prefix frontend test -- --run src/App.test.tsx -t "demo acceptance gate|demo reviewer workspace|demo handoff portal"`
- `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/narration-delivery-package.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- Final acceptance gate exposes narration delivery-package readiness, evidence, and safe download links.
- Reviewer workspace includes narration delivery-package checks, entries, Markdown, and ZIP metadata.
- Handoff portal and its static ZIP/index link the narration delivery JSON and Markdown.
- Full Tears demo script exports narration delivery-package evidence as part of the final handoff flow when narration render is enabled.
- All new surfaces remain read-only and metadata-only, with tests proving forbidden sensitive content is not introduced.
