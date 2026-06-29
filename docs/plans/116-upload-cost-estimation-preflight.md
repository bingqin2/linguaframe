# Upload Cost Estimation Preflight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pre-upload cost estimation cockpit so an operator can see likely OpenAI stages, estimated cost range, budget impact, and recommended action before uploading a selected demo video.

**Architecture:** Add a metadata-only backend preflight endpoint under media uploads that consumes sanitized file facts from the existing validation result plus selected demo profile/runtime cost configuration. The React upload workspace will show the estimate beside validation/readiness/quota, and a terminal script will print the same safe summary for local demos. This feature does not upload media, enqueue jobs, call OpenAI, inspect raw media bytes beyond existing validation/probe behavior, or persist estimates.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, React + Vite + TypeScript, Vitest, Bash demo scripts.

## Global Constraints

- This is one larger complete feature slice: backend API, tests, frontend UI, terminal script, docs, validation, commit, and merge back to `main`.
- Keep the feature pre-upload and read-only. It must not create uploads, enqueue jobs, retry/cancel jobs, publish subtitles, start Docker, call providers, or persist estimates.
- Do not expose API keys, bearer tokens, demo tokens, object keys, local filesystem paths, provider payloads, raw transcript text, raw subtitle text, corrected draft text, uploaded media bytes, or generated media bytes.
- Treat costs as estimates for demo planning, not billing truth.
- Reuse existing upload validation, runtime readiness, demo profile, owner quota, and cost-rate configuration patterns.
- Keep statuses actionable with one recommended next action.

---

## Scope

- Add `POST /api/media/uploads/cost-estimate`, multipart form-data with:
  - `file`;
  - optional `demoProfileId`;
  - optional upload options already supported by upload creation: target language, TTS voice, translation style, subtitle style preset, translation glossary, subtitle polishing mode.
- Return:
  - overall status `READY`, `ATTENTION`, or `BLOCKED`;
  - selected profile/options after normalization;
  - validated safe file facts: filename, content type, size, duration, max limits;
  - provider stage estimates for transcription, translation, subtitle polishing, quality evaluation, TTS, and FFmpeg-only stages;
  - estimated cost lower/upper/current-point values in USD;
  - budget guard and owner daily budget impact;
  - cache uncertainty note when content-hash/provider cache may reduce actual spend later;
  - recommended next action and safety notes.
- Add a React `Cost estimate` panel in the upload workspace that refreshes after file/profile/options changes and never blocks file validation display.
- Add `scripts/demo/upload-cost-estimate.sh` for terminal preflight against a configured sample path.
- Update README, Docker E2E guide, target state, roadmap, decisions, execution log, and this plan.

## Acceptance Criteria

- A selected valid demo video with `tears-showcase` shows likely paid stages, cost estimates, configured budget limits, and whether uploading is safe, attention-worthy, or blocked.
- A too-large or too-long file returns `BLOCKED` with validation details and no provider estimate optimism.
- A deterministic local/demo-provider configuration returns zero or near-zero estimated cost while still showing which stages are disabled, demo-backed, or FFmpeg-only.
- The browser and terminal summaries do not include full local paths, source object keys, API keys, tokens, provider payloads, raw transcript/subtitle text, or media bytes.
- Existing upload, validation, readiness, owner quota, and run launcher behavior continues to work.

## Design Options Considered

- **Recommended: multipart pre-upload estimate endpoint.** Best fit because it reuses existing validation and can estimate against the exact selected file before upload.
- **Alternative: query-only estimate by duration and profile.** Easier to script but less trustworthy because it bypasses actual file validation and duration probing.
- **Alternative: fold estimates into existing upload readiness.** Too broad because readiness is profile/runtime-wide and does not know the selected file.

## Implementation Tasks

### Task 1: Backend Cost Estimate Aggregate

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadCostEstimateVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadCostEstimateStageVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/UploadCostEstimateBudgetVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/UploadCostEstimateService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/UploadCostEstimateServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/UploadCostEstimateServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`

**Interfaces:**
- Produces: `UploadCostEstimateService.estimate(MultipartFile file, UploadOptions options): UploadCostEstimateVo`.
- Consumes existing validation behavior, demo profile defaults, provider/runtime cost configuration, and owner quota daily budget summary.

- [ ] Write failing service tests for:
  - valid short file with OpenAI transcription/translation/TTS/evaluation enabled returns `READY` or `ATTENTION` with non-negative estimate fields and stage rows;
  - duration-limit failure returns `BLOCKED` and does not mark paid stages as runnable;
  - demo-provider or disabled providers produce zero-cost rows with explicit `DEMO`, `DISABLED`, or `FFMPEG_ONLY` labels;
  - budget guard or daily budget pressure changes overall status and recommended action;
  - serialized output excludes unsafe marker strings.
- [ ] Implement VO records and service interface.
- [ ] Implement deterministic estimate math using configured cost rates, duration seconds, conservative token/character proxies, and selected profile/options.
- [ ] Add controller endpoint with OpenAPI annotations.
- [ ] Add controller tests for route shape, multipart inputs, validation failure, and private-demo access compatibility.
- [ ] Run focused backend validation:
  `mvn -pl LinguaFrame -Dtest=UploadCostEstimateServiceTests,MediaUploadControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`

### Task 2: Frontend Upload Cost Estimate Panel

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `POST /api/media/uploads/cost-estimate`.
- Produces: `estimateUploadCost(file, options): Promise<UploadCostEstimate>`.

- [ ] Add TypeScript types for estimate, stage rows, and budget summary.
- [ ] Add API helper and tests for multipart fields plus demo-token/auth headers.
- [ ] Add upload-workspace state and loader that runs after file/profile/options changes with debounce or explicit reuse of validation trigger.
- [ ] Render `Cost estimate` near upload validation/readiness/owner quota with status, cost range, budget impact, provider stages, and next action.
- [ ] Keep upload controls usable when estimate fails; show an inline error rather than blocking validation.
- [ ] Add Vitest coverage for ready, attention, blocked, disabled-provider, and unsafe text absence.
- [ ] Run focused frontend validation:
  `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`

### Task 3: Terminal Preflight Script

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/upload-cost-estimate.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Produces helper `download_upload_cost_estimate_json "$base_url" "$sample_path" "$output_path"`.
- Produces helper `print_upload_cost_estimate_summary_file "$json_path"`.

- [ ] Add helper that posts the selected sample with profile/options through `demo_curl`.
- [ ] Add summary printer with stable `uploadCostEstimate*` lines and forbidden-marker checks.
- [ ] Add script requiring `LINGUAFRAME_DEMO_SAMPLE_PATH` or using existing sample defaults when available.
- [ ] Add shell tests for route, form fields, summary output, blocked state exit, and unsafe marker validation.
- [ ] Run:
  `bash -n scripts/demo/upload-cost-estimate.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- [ ] Run:
  `scripts/demo/test-linguaframe-demo-client.sh`

### Task 4: Documentation And Validation

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/116-upload-cost-estimation-preflight.md`

- [ ] Document when to use cost estimate versus upload validation, upload readiness, owner quota, OpenAI preflight, and demo run launcher.
- [ ] Record the decision that this estimate is a non-persistent planning surface, not billing.
- [ ] Record validation commands and results.
- [ ] Run:
  `npm --prefix frontend run build`
- [ ] Run:
  `git diff --check`
- [ ] Run:
  `mvn -pl LinguaFrame test`
- [ ] Run:
  `npm --prefix frontend test -- --run`
- [ ] Commit feature branch, merge back to `main`, run post-merge focused validation, and record it.

## Validation Plan

- `mvn -pl LinguaFrame -Dtest=UploadCostEstimateServiceTests,MediaUploadControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test`
- `npm --prefix frontend test -- --run src/api/linguaframeApi.test.ts src/App.test.tsx`
- `bash -n scripts/demo/upload-cost-estimate.sh scripts/demo/lib/linguaframe-demo.sh scripts/demo/test-linguaframe-demo-client.sh`
- `scripts/demo/test-linguaframe-demo-client.sh`
- `npm --prefix frontend run build`
- `git diff --check`
- `mvn -pl LinguaFrame test`
- `npm --prefix frontend test -- --run`
- Post-merge focused backend/frontend/script validation on `main`.

## Completion Criteria

- Before spending credits, a demo operator can inspect selected-file cost risk in browser, backend JSON, and terminal.
- The feature advances the product goal by making OpenAI-backed demo runs more predictable, safer, and easier to explain.
- The feature branch is verified, committed, merged back to `main`, and post-merge validation is recorded.
