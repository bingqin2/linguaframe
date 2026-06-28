# Demo Handoff Checklist Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a browser-visible final demo checklist that tells a reviewer whether the selected job is ready to present or hand off, using existing readiness, job, artifact, media, reviewed-output, manifest, and evidence state.

**Architecture:** Reuse already-loaded frontend state and existing download URLs; do not add backend persistence or new endpoints. Add a `Demo handoff checklist` panel to `JobDetail` that computes pass/warn/fail checklist items from job terminal state, pipeline progress, reviewed subtitles, media delivery, evidence downloads, delivery manifest readiness, usage/cost, cache evidence, and failure triage. Add copy/download actions for a metadata-only checklist summary, plus script and documentation updates so the same readiness concept is visible in terminal demos.

**Tech Stack:** React + TypeScript + Vitest/jsdom frontend, existing Spring Boot job/artifact/manifest APIs, Bash demo scripts, Markdown docs.

## Global Constraints

- Keep this slice focused on final demo readiness/handoff; do not change pipeline execution, provider behavior, artifact persistence, subtitle draft semantics, or delivery manifest backend rules.
- The browser checklist must be derived from safe metadata already loaded in the selected job view: job status, pipeline progress, artifacts, subtitle review/draft counts, delivery manifest metadata, and existing safe links.
- Do not expose raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, provider payloads, API keys, demo tokens, credentials, or media bytes in the checklist summary.
- Treat `COMPLETED` plus reviewed JSON/SRT/VTT as the strongest handoff signal; generated media, reviewed video, quality evaluation, cache evidence, and evidence bundle links are additional readiness proof, not mandatory blockers.
- Keep tests runnable without Docker, FFmpeg, OpenAI, or live browser APIs by using existing frontend fixtures and Bash JSON fixtures.

---

## Current Context

- `JobDetail` already loads job detail, artifacts, subtitle review, subtitle draft, delivery manifest, media delivery, and demo evidence state.
- `Delivery handoff` explains reviewed artifact readiness, but it does not summarize the whole demo state.
- `Demo evidence` exports a safe Markdown report, but it is a raw report rather than a quick pass/warn/fail checklist.
- `scripts/demo/docker-e2e-success.sh` prints job, artifact, media delivery, reviewed publish, and manifest summaries, but there is no terminal summary that says what is ready for a demo reviewer.

## Task 1: Browser Checklist Model And Panel

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `LocalizationJob`, `JobArtifact[]`, `DeliveryManifest | null`, `SubtitleReviewSummary | null`, `SubtitleDraftSummary | null`, and `DemoEvidence`.
- Produces: `DemoHandoffChecklistPanel` rendered in `JobDetail` with `aria-label="Demo handoff checklist"`.

- [x] **Step 1: Write failing checklist test**

Add a test with a completed job, reviewed JSON/SRT/VTT artifacts, burned video, dubbing audio, delivery manifest `handoffReady=true`, subtitle review counts, and evidence links. Assert:

```tsx
const checklist = await screen.findByRole('region', { name: /demo handoff checklist/i });
expect(within(checklist).getByText('Demo handoff checklist')).toBeInTheDocument();
expect(within(checklist).getByText('Ready for demo handoff')).toBeInTheDocument();
expect(within(checklist).getByText('Job completed')).toBeInTheDocument();
expect(within(checklist).getByText('Reviewed subtitles ready')).toBeInTheDocument();
expect(within(checklist).getByText('Media outputs available')).toBeInTheDocument();
expect(within(checklist).getByText('Evidence downloads ready')).toBeInTheDocument();
expect(within(checklist).getByRole('button', { name: /copy checklist/i })).toBeEnabled();
expect(within(checklist).getByRole('button', { name: /download checklist json/i })).toBeEnabled();
```

Add a second test for a failed job with no manifest or reviewed artifacts. Assert the panel shows `Needs attention`, includes failure triage when available, and keeps evidence links visible when possible.

- [x] **Step 2: Run tests to verify failure**

Run:

```bash
cd frontend && npm run test:run -- App -t "demo handoff checklist"
```

Expected: fail because the checklist panel does not exist.

- [x] **Step 3: Add checklist types and builder**

In `frontend/src/App.tsx`, add:

```tsx
type ChecklistStatus = 'PASS' | 'WARN' | 'FAIL';

interface DemoHandoffChecklistItem {
  key: string;
  label: string;
  status: ChecklistStatus;
  detail: string;
}

interface DemoHandoffChecklist {
  overallStatus: 'READY' | 'ATTENTION';
  summary: string;
  items: DemoHandoffChecklistItem[];
  links: DemoEvidence['links'];
}
```

Add `buildDemoHandoffChecklist(...)` that returns items for:

- Job completed.
- Pipeline terminal.
- Reviewed subtitles ready.
- Media outputs available.
- Evidence downloads ready.
- Quality signal available.
- Cost and model-call evidence available.
- Cache evidence available.
- Failure triage available when failed.

- [x] **Step 4: Render the panel**

Render `<DemoHandoffChecklistPanel checklist={demoHandoffChecklist} jobId={job.jobId} />` after `DeliveryHandoffPanel` and before `PipelineProgressPanel`.

The panel must show:

- Overall pill: `Ready for demo handoff` when all required items pass; otherwise `Needs attention`.
- Metrics for pass/warn/fail counts.
- Checklist rows with label, status, and detail.
- Existing safe links for result bundle, diagnostics, backend evidence, and evidence bundle.
- `Copy checklist` and `Download checklist JSON` actions.

- [x] **Step 5: Style the checklist**

In `frontend/src/styles.css`, add focused styles for `.handoff-checklist-panel`, `.checklist-summary`, `.checklist-list`, and `.checklist-status-*`. Use compact rows that scan well and remain single-column on mobile.

- [x] **Step 6: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: all App tests pass and Vite build succeeds.

## Task 2: Terminal Demo Checklist Summary

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Test: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Consumes: job detail JSON, delivery manifest JSON, and artifact list JSON.
- Produces: `print_demo_handoff_checklist_summary` with safe pass/warn/fail lines.

- [x] **Step 1: Write failing script test**

Add `test_print_demo_handoff_checklist_summary_is_metadata_only` that feeds three fixture files: completed job detail, ready delivery manifest, and artifact list containing reviewed subtitles and burned video. Expected output:

```text
demoHandoffOverall=READY
demoHandoffItem=PASS:Job completed
demoHandoffItem=PASS:Reviewed subtitles ready
demoHandoffItem=PASS:Media outputs available
demoHandoffItem=PASS:Evidence downloads ready
```

Add unsafe fixture values containing `raw transcript text`, `raw subtitle text`, `raw corrected subtitle`, `job-artifacts/`, `/Users/`, `provider payload`, and `OPENAI_API_KEY`; assert none appear in output.

- [x] **Step 2: Run test to verify failure**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because `print_demo_handoff_checklist_summary` is not defined.

- [x] **Step 3: Implement script helper**

Add a function that accepts three file paths:

```bash
print_demo_handoff_checklist_summary "$JOB_DETAIL_PATH" "$DELIVERY_MANIFEST_PATH" "$ARTIFACTS_PATH"
```

The helper should print only:

- Overall: `READY` when job status is `COMPLETED`, manifest `handoffReady=true`, and reviewed subtitle count is at least 3; otherwise `ATTENTION`.
- `demoHandoffItem=<PASS|WARN|FAIL>:<label>` for job completion, reviewed subtitles, media outputs, evidence downloads, model calls, cache evidence, and failure triage.

- [x] **Step 4: Wire docker success script**

In `scripts/demo/docker-e2e-success.sh`, save job detail, delivery manifest JSON, and artifact list JSON to `/tmp/linguaframe-demo/` temp files and print:

```bash
echo "Demo handoff checklist for job $job_id:"
print_demo_handoff_checklist_summary "$JOB_DETAIL_PATH" "$DELIVERY_MANIFEST_JSON_PATH" "$ARTIFACTS_JSON_PATH"
```

- [x] **Step 5: Verify scripts**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
```

Expected: script tests pass and syntax checks pass.

## Task 3: Documentation And Plan Provenance

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/079-demo-handoff-checklist-workspace.md`

**Interfaces:**
- Consumes: browser checklist and terminal summary behavior.
- Produces: documentation that explains how to verify final demo readiness from the browser and terminal.

- [x] **Step 1: Update product and demo docs**

Document:

- Browser `Demo handoff checklist` panel.
- Required versus optional readiness signals.
- Copy/download checklist actions.
- Terminal `demoHandoff*` output lines.
- Metadata-only safety boundary.

- [x] **Step 2: Update progress log**

Append a `2026-06-28` work section naming this feature, plan path, implementation summary, RED/GREEN notes, and validation commands.

- [x] **Step 3: Mark plan checkboxes**

When implementation and validation pass, mark every checkbox in this plan as `[x]`.

- [x] **Step 4: Verify docs**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

## Task 4: Final Validation And Merge

**Files:**
- All files changed in Tasks 1-3.

**Interfaces:**
- Produces: a verified feature branch merged back to `main`.

- [x] **Step 1: Run focused validation**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
git diff --check
```

Expected: all commands pass.

- [x] **Step 2: Commit feature branch**

Commit message:

```bash
git commit -m "Add demo handoff checklist"
```

- [x] **Step 3: Merge back to main**

After validation, merge the feature branch back to `main` with a no-ff merge commit.

- [x] **Step 4: Post-merge verification**

Run the same focused validation on `main`, append post-merge evidence to `docs/progress/execution-log.md`, and commit the verification log.

