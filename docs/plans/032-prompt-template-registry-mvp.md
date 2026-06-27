# Prompt Template Registry MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register the active OpenAI prompt templates used by translation and quality evaluation, expose them through backend APIs, and show prompt version details in the React demo.

**Architecture:** Add a small prompt-template domain with immutable in-code templates for this MVP. OpenAI providers consume templates from a registry instead of hardcoded prompt strings, and model-call audit records continue to store the template version. Backend read APIs and the React demo make active prompt templates visible without exposing provider secrets or raw user media.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, AssertJ, React, Vite, TypeScript, Vitest.

## Global Constraints

- This feature must be a complete, user-visible feature slice: backend registry, provider integration, API, frontend display, tests, docs, validation, commit, and merge back to `main`.
- Do not add prompt editing, admin mutation, database persistence, A/B testing, or automatic prompt optimization in this slice.
- Do not log OpenAI API keys, request authorization headers, full user media paths, or raw uploaded media bytes.
- Prompt template text may be exposed because it is static product logic; user payload text remains part of existing model-call requests and must not be persisted as template metadata.
- Existing model-call `promptVersion` values must remain stable unless this slice intentionally defines a new version.

---

## Task 1: Add Prompt Template Domain And Registry

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/enums/PromptTemplatePurpose.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/PromptTemplateVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/PromptTemplateRegistry.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/InMemoryPromptTemplateRegistry.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/PromptTemplateRegistryTests.java`

**Interfaces:**

- `PromptTemplatePurpose` values: `SUBTITLE_TRANSLATION`, `TRANSLATION_QUALITY_EVALUATION`.
- `PromptTemplateVo` fields: `String version`, `PromptTemplatePurpose purpose`, `String provider`, `String modelFamily`, `String systemPrompt`, `String outputContract`, `boolean active`.
- `PromptTemplateRegistry.activeTemplate(PromptTemplatePurpose purpose)` returns `PromptTemplateVo`.
- `PromptTemplateRegistry.listActiveTemplates()` returns `List<PromptTemplateVo>`.

- [x] **Step 1: Add failing registry tests**
  - Assert the translation template version is `openai-subtitle-translation-v1`.
  - Assert the evaluation template version is `openai-translation-quality-evaluation-v1`.
  - Assert `listActiveTemplates()` returns exactly the active translation and evaluation templates.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=PromptTemplateRegistryTests test
```

Expected: compilation failure because prompt template registry types do not exist.

- [x] **Step 3: Implement domain and registry**
  - Create the enum, VO, interface, and `@Service` implementation.
  - Keep the current OpenAI system prompts as the template `systemPrompt`.
  - Set `outputContract` to concise static descriptions:
    - Translation: `Return JSON with segments[{index,text}] preserving order and timing.`
    - Evaluation: `Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes.`

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=PromptTemplateRegistryTests test
```

Expected: selected tests pass.

---

## Task 2: Use Registry In OpenAI Providers

**Files:**

- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTranslationProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiQualityEvaluationProvider.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiTranslationProviderTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/impl/OpenAiQualityEvaluationProviderTests.java`

**Interfaces:**

- OpenAI translation provider constructor gains `PromptTemplateRegistry promptTemplateRegistry`.
- OpenAI quality evaluation provider constructor gains `PromptTemplateRegistry promptTemplateRegistry`.
- Providers use `activeTemplate(...).systemPrompt()` in the Responses API system message.
- Providers use `activeTemplate(...).version()` in `CreateModelCallRecordCommand.promptVersion()`.

- [x] **Step 1: Add failing provider tests**
  - In translation provider test, inject a registry whose translation system prompt is `Test translation prompt.` and assert JSON path `$.input[0].content[0].text` equals that prompt.
  - Assert the recorded model call uses the injected template version.
  - In evaluation provider test, inject a registry whose evaluation system prompt is `Test evaluation prompt.` and assert the same request-body and model-call behavior.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests,OpenAiQualityEvaluationProviderTests test
```

Expected: compilation failure because constructors do not accept the registry and providers still use hardcoded prompt strings.

- [x] **Step 3: Implement provider registry integration**
  - Add `PromptTemplateRegistry` fields to both providers.
  - Keep existing production constructors and pass the registry through Spring injection.
  - In request construction, fetch the active template once per request.
  - In audit command creation, use the active template version.
  - Preserve current output JSON schemas and OpenAI endpoint shape.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests,OpenAiQualityEvaluationProviderTests test
```

Expected: selected tests pass.

---

## Task 3: Expose Prompt Templates In Backend API

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/job/controller/PromptTemplateController.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/controller/PromptTemplateControllerTests.java`
- Modify if needed: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`

**Interfaces:**

- `GET /api/prompt-templates` returns active prompt templates as JSON.
- Response items expose `version`, `purpose`, `provider`, `modelFamily`, `systemPrompt`, `outputContract`, and `active`.

- [x] **Step 1: Add failing controller tests**
  - Assert `GET /api/prompt-templates` returns HTTP 200.
  - Assert the response contains one `SUBTITLE_TRANSLATION` item with version `openai-subtitle-translation-v1`.
  - Assert the response contains one `TRANSLATION_QUALITY_EVALUATION` item with version `openai-translation-quality-evaluation-v1`.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=PromptTemplateControllerTests,OpenApiDocumentationTests test
```

Expected: controller route is missing.

- [x] **Step 3: Implement controller**
  - Add `@RestController` under `/api/prompt-templates`.
  - Inject `PromptTemplateRegistry`.
  - Return `registry.listActiveTemplates()`.
  - Do not add mutation endpoints.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=PromptTemplateControllerTests,OpenApiDocumentationTests test
```

Expected: selected tests pass.

---

## Task 4: Show Prompt Templates In React Demo

**Files:**

- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**

- Frontend type `PromptTemplate` mirrors backend response fields.
- API function `listPromptTemplates(): Promise<PromptTemplate[]>`.
- React demo loads templates on startup and shows a compact `Prompt templates` panel near model-call visibility.

- [x] **Step 1: Add failing frontend tests**
  - API test mocks `/api/prompt-templates` and asserts `listPromptTemplates()` returns the template version and purpose.
  - App test asserts the demo renders `Prompt templates`, `openai-subtitle-translation-v1`, and `openai-translation-quality-evaluation-v1`.
  - App test asserts an API failure shows a non-blocking muted message, not a crash.

- [x] **Step 2: Run red tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: frontend API function and UI panel do not exist.

- [x] **Step 3: Implement frontend API and UI**
  - Add `PromptTemplate` type.
  - Add `listPromptTemplates()` with the existing `fetchJson` helper.
  - Load prompt templates in `App` alongside existing job history behavior.
  - Render a compact panel with purpose, version, provider, and output contract.
  - Keep prompt template failure non-blocking.

- [x] **Step 4: Run green tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected frontend tests pass.

---

## Task 5: Documentation, Validation, Commit, And Merge

**Files:**

- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/032-prompt-template-registry-mvp.md`

- [x] **Step 1: Update docs**
  - README: document `GET /api/prompt-templates` and the React panel.
  - Product spec: mark active prompt template registry as implemented while keeping editing and A/B testing future.
  - Roadmap Phase 10: distinguish this MVP from future prompt editing or experimentation.
  - Decisions: record why templates are in-code and read-only first.
  - Execution log: record red/green validation and post-merge validation.

- [x] **Step 2: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config
git diff --check
```

Expected: all commands pass.

- [ ] **Step 3: Commit and merge**
  - Work on branch `prompt-template-registry-mvp`.
  - Commit as `Add prompt template registry`.
  - Merge back to `main`.

- [ ] **Step 4: Verify on `main`**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config
git diff --check HEAD
```

Expected: all commands pass on `main`.

- [ ] **Step 5: Record post-merge verification and clean up**
  - Add merge commit hash and validation results to `docs/progress/execution-log.md`.
  - Commit the execution-log update.
  - Delete local branch `prompt-template-registry-mvp`.

---

## Completion Criteria

- [x] Active translation and quality-evaluation prompt templates are registered with stable versions.
- [x] OpenAI translation and quality evaluation providers consume templates from the registry.
- [x] Model-call audit prompt versions come from the active templates.
- [x] Backend exposes active templates through `GET /api/prompt-templates`.
- [x] React demo displays active prompt template versions and output contracts.
- [x] Tests cover registry, provider integration, API response, and UI rendering.
- [x] Docs explain read-only in-code templates and defer editing/A-B testing.
- [x] Full validation passes.
- [ ] Feature branch is merged back to `main`.
