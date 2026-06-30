# Final Proof Bundle Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LinguaFrame's existing completion, AI audit, OpenAI proof, evidence closure, and final handoff artifacts appear as one coherent final proof bundle across browser, backend packages, and full-demo scripts.

**Architecture:** Reuse existing read-only proof services instead of inventing new readiness rules. Extend reviewer workspace, handoff portal, full Tears export, frontend panels, and docs so a reviewer can find the complete proof chain from one final handoff surface.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, ZIP streams, React + Vite + TypeScript, Vitest, Bash.

## Global Constraints

- This is one larger complete feature slice: backend, frontend, terminal scripts, docs, tests, validation, commit, and merge back to `main` stay together.
- Do not call OpenAI, TTS providers, FFmpeg, upload APIs, retry/cancel APIs, dispatch queues, object-storage write paths, or database mutation paths.
- Do not embed media bytes, raw transcript text, raw subtitle text, corrected draft text, reviewer note bodies, object keys, local paths, provider payloads, tokens, API keys, or credentials.
- Reuse existing services and routes for completion certificate, AI audit package, OpenAI smoke proof, demo evidence closure, reviewer workspace, and handoff portal.
- Keep evidence closure baseline JSON optional. The integrated final proof flow must work in actual-only mode when no pre-upload baseline is available.

---

## Feature Scope

The project already has several proof surfaces:

- Demo completion certificate.
- AI audit package.
- OpenAI smoke proof.
- Demo evidence closure package.
- Demo reviewer workspace.
- Demo handoff portal.

This slice makes them operate as one final reviewer-facing proof bundle instead of separate tools.

Implement:

- Reviewer workspace check, section, safe links, package entries, and ZIP entries for evidence closure and AI audit/OpenAI proof.
- Handoff portal check, section, safe links, package entries, `index.html` links, and ZIP entries for evidence closure and final proof artifacts.
- Full Tears demo script export of evidence closure, OpenAI proof Markdown, and AI audit package alongside reviewer workspace and portal outputs.
- Browser final handoff panels that show the proof chain consistently inside reviewer workspace and handoff portal areas.
- Focused backend, frontend, script, docs, and full validation coverage.

Out of scope:

- New provider calls.
- New baseline-generation workflow.
- New public authentication or billing.
- Embedding nested ZIP binaries inside reviewer/portal ZIP files.

## Backend Design

### Reviewer Workspace

Modify `DemoReviewerWorkspaceServiceImpl` to include final proof closure as an optional evidence block.

Add a check:

- Key: `FINAL_PROOF_BUNDLE`
- Required: `false`
- `PASS` when completion certificate and acceptance gate are ready and evidence closure can be linked.
- `WARN` when OpenAI proof is attention or closure is actual-only.
- `FAIL` only if an existing required final handoff dependency is blocked.

Add a section:

- Key: `FINAL_PROOF_BUNDLE`
- Title: `Final proof bundle`
- Facts:
  - Completion certificate status.
  - Acceptance gate status.
  - OpenAI smoke proof status.
  - AI audit package route availability.
  - Evidence closure route availability.
  - Actual-only baseline note.

Add safe links:

- `/api/jobs/{jobId}/demo-evidence-closure`
- `/api/jobs/{jobId}/demo-evidence-closure/markdown/download`
- `/api/jobs/{jobId}/demo-evidence-closure/download`
- `/api/jobs/{jobId}/openai-smoke-proof/markdown/download`
- `/api/jobs/{jobId}/ai-audit-package/download`

Add reviewer ZIP entries:

- `final-proof-bundle.json`
- `final-proof-bundle.md`

Do not include the AI audit ZIP, OpenAI proof Markdown download bytes, or evidence closure ZIP as nested binaries. The reviewer ZIP should include metadata summaries and safe links only.

### Handoff Portal

Modify `DemoHandoffPortalServiceImpl` to include final proof closure in:

- Checks.
- Sections.
- Safe links.
- Package entries.
- Static `index.html`.
- ZIP entries.

Add portal ZIP entries:

- `final-proof-bundle.json`
- `final-proof-bundle.md`
- Update `manifest.json` entries list.

The static `index.html` should link to:

- Completion certificate JSON.
- Demo evidence closure JSON/Markdown/ZIP route.
- AI audit package route.
- OpenAI smoke proof JSON/Markdown route.

The portal must remain usable in deterministic demo mode where OpenAI proof is `ATTENTION`; that should not block portal readiness by itself.

## Frontend Design

Extend existing selected-job renderers in `frontend/src/App.tsx`.

Reviewer workspace panel:

- Show the final proof bundle check when present.
- Show package entries for `final-proof-bundle.json` and `final-proof-bundle.md`.
- Show safe links for evidence closure, OpenAI proof Markdown, and AI audit package.

Handoff portal panel:

- Show final proof as an explicit portal section.
- Show the proof links in the same compact link list as existing portal artifacts.
- Keep the UI dense and workbench-like; no landing-page or marketing treatment.

Demo evidence closure panel:

- Keep the manual baseline JSON input.
- Do not auto-build closure from reviewer/portal rendering; final proof links should point to routes and avoid hidden POST actions.

## Terminal Script Design

Modify `scripts/demo/docker-e2e-tears-of-steel-full.sh`:

- After final acceptance gate export, export:
  - `demo-completion-certificate.json`
  - `openai-smoke-proof.json`
  - `openai-smoke-proof.md`
  - `ai-audit-package.zip`
  - `demo-evidence-closure.json`
  - `demo-evidence-closure.md`
  - `demo-evidence-closure.zip`
- Use actual-only closure unless `LINGUAFRAME_PRE_UPLOAD_JSON_PATH` or `LINGUAFRAME_PRE_UPLOAD_JSON_INLINE` is provided.
- Refresh reviewer workspace and handoff portal after final proof exports so their package inventories reflect the integrated proof bundle.

Extend `scripts/demo/lib/linguaframe-demo.sh` only if existing helper coverage is missing:

- Add or reuse download helpers for OpenAI proof Markdown, AI audit package, and evidence closure.
- Add a compact `print_final_proof_bundle_summary_file` helper if needed.

## Testing

Backend focused tests:

- Extend `DemoReviewerWorkspaceServiceTests` to assert:
  - `FINAL_PROOF_BUNDLE` check exists.
  - Final proof section includes completion, acceptance, OpenAI proof, AI audit, and closure facts.
  - Safe links include evidence closure, OpenAI proof Markdown, and AI audit package.
  - ZIP includes `final-proof-bundle.json` and `final-proof-bundle.md`.
  - ZIP does not include nested proof ZIP binaries.
- Extend `DemoHandoffPortalServiceTests` to assert:
  - Portal checks and sections include final proof.
  - Static `index.html` links final proof routes.
  - ZIP includes final proof JSON/Markdown entries.
  - Manifest entry list includes final proof entries.

Frontend focused tests:

- Extend `frontend/src/App.test.tsx` selected-job coverage to assert reviewer workspace and handoff portal render:
  - `Final proof bundle`.
  - Evidence closure link.
  - AI audit package link.
  - OpenAI smoke proof Markdown link.

Script tests:

- Run `bash -n` on changed scripts.
- Extend `scripts/demo/test-linguaframe-demo-client.sh` if helper output has new required assertions.

## Documentation

Update:

- `README.md` final demo and reviewer handoff guidance.
- `docs/agent/docker-e2e-demo.md` full Tears final proof export flow.
- `docs/agent/smoke-test-checklist.md` final proof browser and terminal checks.
- `docs/product/roadmap.md` Phase 8/10/12 status language for final proof integration.
- `docs/product/target-state.md` final proof bundle expectation.
- `docs/progress/execution-log.md` with fail-first and final validation evidence.

## Validation Commands

- `mvn -pl LinguaFrame -Dtest=DemoReviewerWorkspaceServiceTests,DemoHandoffPortalServiceTests test`
- `npm --prefix frontend test -- --run src/App.test.tsx -t "demo reviewer workspace|demo handoff portal"`
- `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh scripts/demo/demo-evidence-closure.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `bash scripts/demo/test-linguaframe-demo-client.sh`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- `npm --prefix frontend run build`
- `git diff --check`

## Acceptance Criteria

- Reviewer workspace exposes final proof bundle readiness, safe links, package entries, and ZIP metadata entries.
- Handoff portal and its static ZIP/index expose final proof bundle links and metadata entries.
- Full Tears demo exports final proof artifacts before refreshed reviewer/portal outputs.
- Integrated proof flow works without a pre-upload baseline JSON.
- All new surfaces stay read-only and metadata-only, with tests proving nested binary proof packages and sensitive text are not introduced.
