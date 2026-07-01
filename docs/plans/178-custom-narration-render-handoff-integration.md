# Custom Narration Render Handoff Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make custom narration render evidence visible in final demo readiness, reviewer handoff, offline portal, and terminal full-demo outputs.

**Architecture:** Reuse the existing custom narration render preflight/report plus narration evidence and delivery package services as upstream inputs. Add metadata-only summary fields and safe links to final-demo aggregation services instead of embedding narration text, media bytes, provider payloads, or local paths.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5, React, TypeScript, Vitest, Bash demo client.

## Global Constraints

- This is one complete feature slice: backend aggregates, frontend panels, terminal scripts, docs, tests, validation, commit, and merge back to `main`.
- Keep custom narration render separate from demo-preset render; never replace saved operator-authored rows.
- All final handoff outputs must remain metadata-only: no narration text, reviewer note bodies, transcript text, subtitle text, local paths, object keys, provider payloads, tokens, API keys, media bytes, or generated artifact bytes.
- Read-only aggregate surfaces must not call OpenAI, TTS providers, FFmpeg, save rows, render media, upload media, mutate object storage, or start Docker.
- Use existing narration evidence, render review, playback resolution, delivery package, and custom render report services rather than duplicating render logic.

---

## Design Decision

Recommended approach: add a small `Custom narration render` evidence section to existing final-demo aggregates. This keeps custom render as a first-class handoff proof while preserving the current safe package boundaries.

Alternatives considered:

- Create a new standalone final package only for custom narration render. This would duplicate the existing reviewer workspace and handoff portal.
- Modify the custom render endpoint to write permanent report artifacts. That adds artifact lifecycle complexity before the current metadata report is proven necessary.

## Task 1: Backend Final Readiness Evidence

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoAcceptanceGateServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/DemoAcceptanceGateVo.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/DemoAcceptanceGateServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/controller/LocalizationJobControllerTests.java`

**Deliverable:** Acceptance gate includes custom narration render readiness and safe remediation routes.

- [x] Add failing tests for a job with saved narration rows, custom render preflight `READY`, audio ready, optional video ready, delivery package ready, and playback resolution ready.
- [x] Add failing tests for custom narration rows where render is missing or delivery package is blocked, producing `ATTENTION` with safe next action.
- [x] Add `customNarrationRenderStatus`, `customNarrationRenderOutputPlan`, `customNarrationRenderReportRoute`, and `customNarrationRenderNextAction` fields.
- [x] Ensure the gate still blocks only on existing required final checks unless narration playback resolution or delivery package already blocks readiness.
- [x] Run focused backend gate/controller tests.

## Task 2: Reviewer Workspace, Portal, And Evidence Closure

**Files:**
- Modify: reviewer workspace service and VO files under `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/`
- Modify: demo handoff portal service and VO files under `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/`
- Modify: demo evidence closure service and VO files under `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/`
- Modify: matching service/controller tests under `LinguaFrame/src/test/java/com/linguaframe/job/`

**Deliverable:** Final reviewer exports include custom narration render proof and safe report links.

- [x] Add failing tests that reviewer workspace includes custom render readiness, output mode, report route, evidence route, and delivery package route.
- [x] Add failing tests that handoff portal ZIP `index.html` links the custom render Markdown route without embedding report content or media bytes.
- [x] Add failing tests that evidence closure lists custom narration render as a closure section when saved narration rows exist.
- [x] Implement metadata-only sections using existing custom render preflight/report service methods.
- [x] Verify exported JSON/Markdown/ZIP excludes narration text, local paths, object keys, provider payloads, tokens, and API keys.

## Task 3: Frontend Final Handoff Visibility

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Deliverable:** Browser final-demo surfaces show the custom narration render handoff state.

- [x] Add type fields for custom narration render evidence in acceptance gate, reviewer workspace, handoff portal, and evidence closure responses.
- [x] Update `Demo acceptance gate` to show custom narration render status, output plan, report link, and next action.
- [x] Update `Demo reviewer workspace` and `Demo handoff portal` panels to surface the custom render link in final proof sections.
- [x] Add Vitest coverage for ready custom render evidence and missing-render attention state.
- [x] Run focused App tests.

## Task 4: CLI And Full Demo Outputs

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/demo-acceptance-gate.sh`
- Modify: `scripts/demo/demo-reviewer-workspace.sh`
- Modify: `scripts/demo/demo-handoff-portal.sh`
- Modify: `scripts/demo/demo-evidence-closure.sh`
- Modify: `scripts/demo/docker-e2e-tears-of-steel-full.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Deliverable:** Terminal full-demo artifacts expose custom narration render proof.

- [x] Add summary lines for custom narration render status, output plan, report route, and next action.
- [x] Extend full Tears script to export `custom-narration-render.md` when `LINGUAFRAME_RENDER_CUSTOM_NARRATION=true`.
- [x] Add shell tests for route output, ZIP entry presence, and redaction.
- [x] Run shell syntax and demo-client tests.

## Task 5: Docs, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/178-custom-narration-render-handoff-integration.md`

**Deliverable:** The feature is documented, verified, committed, and merged back to `main`.

- [x] Document browser order from custom render to final acceptance/reviewer/handoff.
- [x] Document terminal order for seeded full Tears custom narration handoff.
- [x] Add decision record for using final aggregate sections instead of persistent custom render artifacts.
- [x] Append validation evidence to execution log.
- [x] Run backend focused tests for acceptance gate, reviewer workspace, handoff portal, and evidence closure.
- [x] Run frontend focused tests for custom narration final handoff visibility.
- [x] Run demo client tests.
- [x] Run broad verification: `mvn -pl LinguaFrame test`, `npm --prefix frontend test -- --run`, `npm --prefix frontend run build`, shell syntax checks, and `git diff --check`.
- [x] Commit with subject `Integrate custom narration render handoff`.
- [x] Merge the verified branch back to `main`.

## Acceptance Criteria

- Custom narration render is visible in final readiness and handoff surfaces after rendering saved operator-authored rows.
- Missing custom render output gives an actionable safe next step without hiding existing final handoff evidence.
- Reviewer workspace, handoff portal, evidence closure, and full Tears terminal outputs link the custom render report when applicable.
- No final aggregate calls providers, runs FFmpeg, saves rows, renders media, or leaks text/secrets/media bytes.
- Existing demo-preset render and separate narration controls continue to work.
