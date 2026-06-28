# Demo Session Report Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a final demo session report that summarizes one completed LinguaFrame demo run from upload through handoff evidence, in both the browser and Docker E2E terminal output.

**Architecture:** Reuse already-loaded selected-job metadata in the React job detail view and existing JSON files in the Docker demo script. Do not add backend persistence or new API endpoints; the report is a safe derived artifact composed from job status, artifacts, delivery manifest, media delivery, demo checklist, evidence links, usage, cache, and failure triage. Provide copy/download actions in the browser and a generated Markdown file in `/tmp/linguaframe-demo/` for script-driven demos.

**Tech Stack:** React + TypeScript + Vitest/jsdom, Bash + Python helper scripts, existing Spring Boot job/artifact/manifest APIs, Markdown docs.

## Global Constraints

- Keep this slice focused on demo-session reporting; do not change upload, worker execution, provider calls, artifact persistence, subtitle draft behavior, or delivery manifest backend rules.
- The report must be metadata-only: never include raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, provider payloads, API keys, demo tokens, credentials, or media bytes.
- Treat the report as a handoff/readme artifact for one demo run, not as a replacement for diagnostics JSON, evidence bundle, or delivery manifest.
- Browser tests and script tests must run without Docker, FFmpeg, OpenAI, or live browser APIs.
- This feature must include code, tests, docs, validation, plan provenance, and merge back to `main` after confirmation and implementation.

---

## Current Context

- `JobDetail` already computes `DemoEvidence` and `DemoHandoffChecklist`.
- The selected-job page already exposes delivery manifest, media delivery, result bundle, diagnostics, backend evidence, evidence bundle, and checklist actions.
- `scripts/demo/docker-e2e-success.sh` already saves job detail JSON, delivery manifest JSON, and artifact list JSON under `/tmp/linguaframe-demo/`.
- The current gap is that a reviewer still has to inspect several panels or files to understand the complete demo session.

## Task 1: Browser Demo Session Report

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `LocalizationJob`, `JobArtifact[]`, `DeliveryManifest | null`, `DemoEvidence`, and `DemoHandoffChecklist`.
- Produces: `DemoSessionReportPanel` rendered in `JobDetail` with `aria-label="Demo session report"`.

- [x] **Step 1: Write failing browser tests**

Add a completed-job test that renders the selected job with reviewed subtitles, media artifacts, delivery manifest readiness, model-call usage, cache evidence, and evidence links. Assert the panel shows:

```tsx
const report = await screen.findByRole('region', { name: /demo session report/i });
expect(within(report).getByText('Demo session report')).toBeInTheDocument();
expect(within(report).getByText('Session ready')).toBeInTheDocument();
expect(within(report).getByText('Input and job')).toBeInTheDocument();
expect(within(report).getByText('Generated outputs')).toBeInTheDocument();
expect(within(report).getByText('Handoff evidence')).toBeInTheDocument();
expect(within(report).getByRole('button', { name: /copy report/i })).toBeEnabled();
expect(within(report).getByRole('button', { name: /download report markdown/i })).toBeEnabled();
```

Add a failed-job test that includes `failureTriage` and asserts `Session needs attention`, the triage category, and diagnostics link are visible.

- [x] **Step 2: Run tests to verify failure**

Run:

```bash
cd frontend && npm run test:run -- App -t "demo session report"
```

Expected: fail because the `Demo session report` panel does not exist.

- [x] **Step 3: Add report types and builder**

In `frontend/src/App.tsx`, add:

```tsx
interface DemoSessionReport {
  generatedAt: string;
  status: 'READY' | 'ATTENTION';
  title: string;
  sections: Array<{
    title: string;
    lines: string[];
  }>;
  links: DemoEvidence['links'];
}
```

Add `buildDemoSessionReport(job, artifacts, manifest, evidence, checklist)` with sections:

- `Input and job`: job id, video id, target language, status, retry count, terminal state.
- `Generated outputs`: artifact count, reviewed subtitle count, media output count, result bundle link.
- `Handoff evidence`: checklist overall status, delivery manifest readiness, evidence bundle link, diagnostics link.
- `Cost and cache`: model-call count, failed model-call count, estimated cost, artifact cache hits, provider cache hits.
- `Failure triage`: only when `job.failureTriage` exists, with category, retryable flag, and recommended action.

- [x] **Step 4: Render report panel**

Render `<DemoSessionReportPanel report={demoSessionReport} jobId={job.jobId} />` after `DemoHandoffChecklistPanel` and before `DemoEvidencePanel`.

The panel must include:

- Overall pill: `Session ready` when checklist status is `READY`; otherwise `Session needs attention`.
- Four normal sections for complete jobs and a fifth failure section for failed jobs.
- Safe links for result bundle, diagnostics, backend evidence, and evidence bundle.
- `Copy report` and `Download report Markdown` actions.

- [x] **Step 5: Add Markdown formatter**

Add `formatDemoSessionReportMarkdown(report, jobId)` that emits:

```markdown
# LinguaFrame Demo Session Report

- Job: <jobId>
- Status: READY|ATTENTION
- Generated at: <ISO timestamp>

## Input and job
- ...

## Generated outputs
- ...

## Handoff evidence
- ...

## Cost and cache
- ...
```

The formatter must use only values from the safe report object.

- [x] **Step 6: Style the report**

Add compact styles for `.demo-session-report-panel`, `.session-report-grid`, `.session-report-section`, and `.session-report-links`. Keep it readable beside the existing demo checklist and evidence panels.

- [x] **Step 7: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: all App tests pass and Vite build succeeds.

## Task 2: Terminal Demo Session Report

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Test: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Consumes: job detail JSON, delivery manifest JSON, artifact list JSON, and an output Markdown path.
- Produces: `write_demo_session_report_markdown "$JOB_DETAIL_PATH" "$DELIVERY_MANIFEST_JSON_PATH" "$ARTIFACTS_JSON_PATH" "$DEMO_SESSION_REPORT_PATH"`.

- [x] **Step 1: Write failing script test**

Add `test_write_demo_session_report_markdown_is_metadata_only` with fixture JSON containing safe metadata plus unsafe values such as raw transcript text, raw subtitle text, raw corrected subtitle, `job-artifacts/`, `/Users/`, `provider payload`, `OPENAI_API_KEY`, and demo token values.

Assert the generated Markdown contains:

```text
# LinguaFrame Demo Session Report
- Overall: READY
## Input and job
## Generated outputs
## Handoff evidence
## Cost and cache
```

Assert none of the unsafe fixture values appear in the report.

- [x] **Step 2: Run test to verify failure**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because `write_demo_session_report_markdown` is not defined.

- [x] **Step 3: Implement script helper**

Add `write_demo_session_report_markdown` using Python in `scripts/demo/lib/linguaframe-demo.sh`.

Rules:

- Overall is `READY` only when job status is `COMPLETED`, delivery manifest `handoffReady=true`, and reviewed subtitle count is at least 3.
- Include counts for total artifacts, reviewed subtitles, media outputs, model calls, failed model calls, estimated cost, artifact cache hits, and provider cache hits.
- Include failure triage category and retryable flag only when present.
- Write the Markdown to the provided output path.

- [x] **Step 4: Wire Docker E2E script**

In `scripts/demo/docker-e2e-success.sh`, add:

```bash
DEMO_SESSION_REPORT_PATH="${LINGUAFRAME_DEMO_SESSION_REPORT_PATH:-/tmp/linguaframe-demo/demo-session-report.md}"
```

After printing the demo handoff checklist, call:

```bash
write_demo_session_report_markdown \
  "$JOB_DETAIL_PATH" \
  "$DELIVERY_MANIFEST_JSON_PATH" \
  "$ARTIFACTS_JSON_PATH" \
  "$DEMO_SESSION_REPORT_PATH"
echo "Wrote demo session report to $DEMO_SESSION_REPORT_PATH"
```

- [x] **Step 5: Verify scripts**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
```

Expected: script tests pass and syntax checks pass.

## Task 3: Documentation And Provenance

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/080-demo-session-report-workspace.md`

- [x] **Step 1: Update user-facing docs**

Document:

- Browser `Demo session report` panel.
- Terminal `/tmp/linguaframe-demo/demo-session-report.md` output.
- Difference between report, delivery manifest, diagnostics, and evidence bundle.
- Metadata-only safety boundary.

- [x] **Step 2: Update product docs**

Update Phase 8 roadmap and target-state UX goals to include the demo session report as the final reviewer-facing summary.

- [x] **Step 3: Update execution log**

Record:

- Plan path.
- Browser behavior.
- Terminal behavior.
- Validation commands and outcomes.
- Post-merge verification after merging to `main`.

- [x] **Step 4: Mark plan complete**

After verification, mark all checkboxes in this plan complete.

## Final Verification

Run before committing:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
git diff --check
```

After commit and merge back to `main`, rerun the same validation on `main` and record the result in `docs/progress/execution-log.md`.
