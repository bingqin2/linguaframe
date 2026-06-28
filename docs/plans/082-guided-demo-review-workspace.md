# Guided Demo Review Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a browser-first guided demo review workspace that lets a viewer walk through one selected job in presentation order: input, pipeline, review, delivery, evidence, and handoff.

**Architecture:** Reuse already-loaded job, artifact, delivery manifest, evidence, checklist, and report state in the React job detail view. Add one derived `DemoReviewGuidePanel` near the top of the selected job surface with step statuses, anchor links to existing panels, next-action guidance, and metadata-only presenter notes export. Do not add backend persistence or new provider calls.

**Tech Stack:** React + TypeScript + Vite, Vitest/jsdom, existing Spring Boot APIs, Bash demo script docs, Markdown product/progress docs.

## Global Constraints

- This slice must be one complete user-visible feature: browser UI, tests, docs, validation, plan provenance, commit, and merge back to `main`.
- Keep the guide metadata-only. It must not expose raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, provider payloads, API keys, demo tokens, credentials, or media bytes.
- Do not change upload, worker execution, provider behavior, artifact persistence, subtitle draft semantics, delivery manifest semantics, or handoff package contents.
- Prefer anchor links to existing panels over duplicating large panel content.
- Tests must run without Docker, FFmpeg, OpenAI, network access, or real clipboard/download APIs.

---

## Current Context

- The selected job view already has many strong panels: `Result delivery`, `Delivery handoff`, `Demo handoff checklist`, `Demo session report`, `Demo evidence`, `Pipeline progress`, `Subtitle review`, `Subtitle draft editor`, `Media delivery`, artifacts, model calls, and timeline.
- The current gap is presentation flow. A reviewer can inspect everything, but the UI does not yet provide a single ordered guide showing what to explain first, what is ready, what needs attention, and which panel to open next.
- Phase 8 exit criteria say a viewer should understand and run the demo from the browser. This slice makes the browser job detail easier to present without terminal narration.

## Task 1: Browser Guided Demo Review Panel

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Create `DemoReviewStep` in `frontend/src/App.tsx`:

```ts
interface DemoReviewStep {
  key: string;
  title: string;
  status: 'READY' | 'ATTENTION' | 'BLOCKED';
  detail: string;
  anchor: string;
  actionLabel: string;
}
```

- Create `buildDemoReviewSteps(job, artifacts, deliveryManifest, subtitleReview, subtitleDraft, demoHandoffChecklist, demoSessionReport): DemoReviewStep[]`.
- Create `DemoReviewGuidePanel` with `aria-label="Demo review guide"`.

- [x] **Step 1: Write failing ready-job browser test**

In `frontend/src/App.test.tsx`, add a test using an existing completed/reviewed job fixture or a new fixture with:

- `status: COMPLETED`
- terminal pipeline progress
- at least one transcript/subtitle preview row
- reviewed JSON/SRT/VTT artifacts
- media output artifacts
- delivery manifest `handoffReady: true`
- usage and cache metadata

Assert:

```tsx
const guide = await screen.findByRole('region', { name: /demo review guide/i });
expect(within(guide).getByText('Demo review guide')).toBeInTheDocument();
expect(within(guide).getByText('Presentation ready')).toBeInTheDocument();
expect(within(guide).getByText('Input')).toBeInTheDocument();
expect(within(guide).getByText('Pipeline')).toBeInTheDocument();
expect(within(guide).getByText('Review')).toBeInTheDocument();
expect(within(guide).getByText('Delivery')).toBeInTheDocument();
expect(within(guide).getByText('Evidence')).toBeInTheDocument();
expect(within(guide).getByText('Handoff')).toBeInTheDocument();
expect(within(guide).getByRole('link', { name: /open delivery/i })).toHaveAttribute('href', '#delivery-handoff');
expect(within(guide).getByRole('button', { name: /copy presenter notes/i })).toBeEnabled();
```

Run:

```bash
cd frontend && npm run test:run -- App -t "demo review guide"
```

Expected: fail because the guide panel does not exist.

- [x] **Step 2: Write failing attention-state browser test**

Add a failed or incomplete job fixture with `failureTriage` and no reviewed subtitle artifacts. Assert:

```tsx
const guide = await screen.findByRole('region', { name: /demo review guide/i });
expect(within(guide).getByText('Needs attention')).toBeInTheDocument();
expect(within(guide).getByText(/failure triage/i)).toBeInTheDocument();
expect(within(guide).getByRole('link', { name: /open pipeline/i })).toHaveAttribute('href', '#pipeline-progress');
expect(within(guide).getByRole('link', { name: /open evidence/i })).toHaveAttribute('href', '#demo-evidence');
```

Run:

```bash
cd frontend && npm run test:run -- App -t "demo review guide"
```

Expected: fail for the same missing panel.

- [x] **Step 3: Add stable section anchors to existing panels**

In `frontend/src/App.tsx`, add stable `id` attributes:

```tsx
<section id="result-delivery" ... aria-label="Result delivery">
<section id="delivery-handoff" ... aria-label="Delivery handoff">
<section id="demo-handoff-checklist" ... aria-label="Demo handoff checklist">
<section id="pipeline-progress" ... aria-label="Pipeline progress">
<section id="demo-session-report" ... aria-label="Demo session report">
<section id="demo-evidence" ... aria-label="Demo evidence">
<section id="subtitle-review" ... aria-label="Subtitle review">
<section id="subtitle-draft-editor" ... aria-label="Subtitle draft editor">
<section id="media-delivery" ... aria-label="Media delivery">
<section id="artifacts" ... aria-label="Artifacts">
```

For panels that render error/loading variants, keep the same `id` on each variant so anchor links always work.

- [x] **Step 4: Implement guide step builder**

Add `buildDemoReviewSteps(...)` near the existing demo evidence/checklist/report builders.

Rules:

- `Input`: `READY` when job id, video id, and target language are present; anchor `#result-delivery`.
- `Pipeline`: `READY` when `job.pipelineProgress?.terminal === true` or job status is `COMPLETED`, `FAILED`, or `CANCELLED`; `ATTENTION` otherwise; anchor `#pipeline-progress`.
- `Review`: `READY` when `subtitleReview` exists and reviewed subtitle count is at least 3; `ATTENTION` when subtitle review exists but reviewed artifacts are missing; `BLOCKED` when preview data is unavailable; anchor `#subtitle-review`.
- `Delivery`: `READY` when `deliveryManifest?.handoffReady === true`; `ATTENTION` when manifest exists but is not ready; `BLOCKED` when missing; anchor `#delivery-handoff`.
- `Evidence`: `READY` when diagnostics, backend evidence, evidence bundle, and handoff package links are derivable from job id; anchor `#demo-evidence`.
- `Handoff`: `READY` when `demoHandoffChecklist.overallStatus === 'READY'` and `demoSessionReport.status === 'READY'`; `ATTENTION` otherwise; anchor `#demo-session-report`.
- If `job.failureTriage` exists, append a `Failure triage` step with `ATTENTION`, anchor `#failure-triage`, and detail from safe triage metadata.

- [x] **Step 5: Implement `DemoReviewGuidePanel`**

Render the panel near the top of `JobDetail`, after the usage summary and before `ResultDeliveryPanel`.

Panel behavior:

- Header text: `Demo review guide`.
- Overall pill: `Presentation ready` when all non-failure steps are `READY`; otherwise `Needs attention`.
- Step list with stable labels and status pills.
- Each step has an anchor link using `actionLabel`, for example `Open pipeline`, `Open review`, `Open delivery`, `Open evidence`.
- Actions include:

```tsx
<button type="button" onClick={handleCopyPresenterNotes}>Copy presenter notes</button>
<button type="button" onClick={handleDownloadPresenterNotes}>Download presenter notes</button>
```

Use local component state to show `Presenter notes copied.` or `Presenter notes Markdown downloaded.`. If Clipboard API is unavailable, disable copy and show the existing clipboard unavailable pattern used by evidence/report panels.

- [x] **Step 6: Add presenter notes formatter**

Add `formatDemoReviewPresenterNotes(job, steps, demoSessionReport)` that emits metadata-only Markdown:

```markdown
# LinguaFrame Demo Review Notes

- Job: <jobId>
- Video: <videoId>
- Target language: <targetLanguage>
- Overall: READY|ATTENTION

## Walkthrough
- READY Input: ...
- READY Pipeline: ...
- READY Review: ...
- READY Delivery: ...
- READY Evidence: ...
- READY Handoff: ...

## Session report
- <safe line copied from demoSessionReport.sections>
```

Use only safe metadata already present in `job`, `steps`, and `demoSessionReport`. Do not include transcript/subtitle text.

- [x] **Step 7: Style the guide**

In `frontend/src/styles.css`, add compact styles:

- `.demo-review-guide-panel`
- `.demo-review-steps`
- `.demo-review-step`
- `.demo-review-step-main`
- `.demo-review-step-actions`

Keep it dense and operational, not a hero/marketing section. Reuse existing status pill colors and panel action patterns.

- [x] **Step 8: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App -t "demo review guide"
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: focused guide tests pass, full App suite passes, and Vite build succeeds.

## Task 2: Demo Script And Documentation Alignment

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/082-guided-demo-review-workspace.md`

**Interfaces:**
- The browser guide does not add a backend endpoint or script output file.
- The deterministic Docker script remains the source for terminal artifacts; docs should explain that the browser guide consumes the same safe endpoints and downloaded artifacts already validated by the script.

- [x] **Step 1: Update README**

Document the selected job `Demo review guide`:

- It appears near the top of the selected job.
- It orders the demo into input, pipeline, review, delivery, evidence, and handoff.
- It links to existing panels by anchor.
- It exports metadata-only presenter notes.
- It does not replace diagnostics, evidence bundle, demo session report, delivery manifest, or handoff package.

- [x] **Step 2: Update smoke checklist**

Add checklist items:

- The guide shows `Presentation ready` only when completed pipeline, reviewed subtitles, handoff readiness, evidence links, and session report are ready.
- In failed/incomplete jobs, the guide shows `Needs attention` and links to pipeline/evidence/failure triage.
- Presenter notes do not contain raw transcript text, raw subtitle text, corrected draft text, object keys, local paths, provider payloads, API keys, demo tokens, credentials, or media bytes.

- [x] **Step 3: Update roadmap and target state**

Update Phase 8 status/build list to include:

```text
Guided demo review workspace. Status: implemented as a browser panel that orders one selected job into input, pipeline, review, delivery, evidence, and handoff steps with anchor links and metadata-only presenter notes.
```

Update target-state UX flow to include using the guide before inspecting detailed panels.

- [x] **Step 4: Update execution log**

Record:

- Plan path: `docs/plans/082-guided-demo-review-workspace.md`.
- Browser behavior.
- Presenter-notes safety boundary.
- Validation commands and outcomes.
- Post-merge validation after merging to `main`.

- [x] **Step 5: Mark plan complete**

After final verification, mark all checkboxes in this plan complete.

## Final Verification

Run before committing:

```bash
cd frontend && npm run test:run -- App -t "demo review guide"
cd frontend && npm run test:run -- App
cd frontend && npm run build
git diff --check
```

After commit and merge back to `main`, rerun the same validation on `main` and record the result in `docs/progress/execution-log.md`.
