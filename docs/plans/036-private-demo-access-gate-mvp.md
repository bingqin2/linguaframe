# Private Demo Access Gate MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional owner-only access gate for private demo deployments without changing the default local demo workflow.

**Architecture:** Add a small Spring MVC `HandlerInterceptor` that protects `/api/**` only when `linguaframe.demo.access-token` is configured. The frontend reads a browser-stored demo token and sends it as `X-LinguaFrame-Demo-Token`; Docker and docs expose the environment variables needed for a private demo URL. Health, actuator, OpenAPI, Swagger UI, and frontend assets stay public for deployment checks.

**Tech Stack:** Java 21, Spring Boot MVC interceptors, JUnit 5, MockMvc, React + Vite + TypeScript, Vitest, Docker Compose.

## Global Constraints

- This feature must be a complete feature slice: backend access gate, frontend token entry, Docker/env docs, tests, validation, commit, and merge back to `main`.
- Default local development must remain open when no token is configured.
- Do not add JWT, user accounts, password login, OAuth, database user tables, billing, or public multi-user permissions in this slice.
- Do not protect `/actuator/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, or static frontend assets.
- Never log or render the configured access token value.

---

## Task 1: Backend Demo Access Gate

**Files:**

- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoAccessInterceptor.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoAccessWebMvcConfiguration.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/security/DemoAccessInterceptorTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**

- `LinguaFrameProperties.Demo` with `accessToken`, `accessHeaderName`, and `isAccessGateEnabled()`.
- Defaults: empty token disables the gate; header name is `X-LinguaFrame-Demo-Token`.
- Protected requests without the matching header return HTTP 401 JSON: `{"error":"DEMO_ACCESS_REQUIRED","message":"Demo access token is required."}`.

- [x] **Step 1: Add failing backend tests**
  - Assert default `properties.getDemo().isAccessGateEnabled()` is false.
  - Assert configured `linguaframe.demo.access-token=test-token` enables the gate.
  - Assert `/api/runtime/dependencies` is open when no token is configured.
  - Assert `/api/runtime/dependencies` returns 401 when token is configured but header is missing or wrong.
  - Assert `/api/runtime/dependencies` succeeds when `X-LinguaFrame-Demo-Token: test-token` is present.
  - Assert `/actuator/health`, `/v3/api-docs`, and `/swagger-ui/index.html` are not blocked by the interceptor.

- [x] **Step 2: Run red tests**

```bash
mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,LinguaFramePropertiesTests test
```

Expected: compilation failure because demo access configuration and interceptor types do not exist.

- [x] **Step 3: Implement backend gate**
  - Add `Demo` nested properties to `LinguaFrameProperties`.
  - Add YAML properties:
    - `linguaframe.demo.access-token: ${LINGUAFRAME_DEMO_ACCESS_TOKEN:}`
    - `linguaframe.demo.access-header-name: ${LINGUAFRAME_DEMO_ACCESS_HEADER_NAME:X-LinguaFrame-Demo-Token}`
  - Implement `DemoAccessInterceptor.preHandle(...)` for `/api/**`.
  - Write the 401 response with `application/json`, no token value, and no stack trace.
  - Register the interceptor for `/api/**` only.

- [x] **Step 4: Run green tests**

```bash
mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,LinguaFramePropertiesTests test
```

Expected: selected tests pass.

## Task 2: Frontend Token Entry And API Header

**Files:**

- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/App.css`

**Interfaces:**

- Local storage key: `linguaframe.demoToken.v1`.
- API helper adds `X-LinguaFrame-Demo-Token` to all JSON and multipart API calls when a non-blank token exists.
- UI exposes one compact token input in the header/tool area, with Save and Clear actions.
- 401 responses render `Demo access token is required.` and keep the entered token editable.

- [x] **Step 1: Add failing frontend tests**
  - Assert `uploadMedia` sends `X-LinguaFrame-Demo-Token` for multipart upload when stored.
  - Assert normal GET APIs send the header when stored.
  - Assert the App can save and clear the token from local storage.
  - Assert a 401 API response displays a clear token-required message.

- [x] **Step 2: Run red frontend tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: tests fail because the token storage, header injection, and UI do not exist.

- [ ] **Step 3: Implement frontend token support**
- [x] **Step 3: Implement frontend token support**
  - Add `readDemoToken(storage)` and `writeDemoToken(storage, token)` helpers in `linguaframeApi.ts`.
  - Add the configured header to `requestJson(...)` and multipart upload calls when token is non-blank.
  - Add a small token input/control in `App.tsx` using existing restrained dashboard styling.
  - Mirror the saved token into a same-site browser cookie so EventSource, artifact downloads, and media previews work in gated demo mode.
  - Map `DEMO_ACCESS_REQUIRED` and HTTP 401 to `Demo access token is required.`.

- [x] **Step 4: Run green frontend tests**

```bash
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: selected frontend tests pass.

## Task 3: Private Demo Runtime Docs And Configuration

**Files:**

- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/036-private-demo-access-gate-mvp.md`

**Interfaces:**

- Environment variables:
  - `LINGUAFRAME_DEMO_ACCESS_TOKEN=`
  - `LINGUAFRAME_DEMO_ACCESS_HEADER_NAME=X-LinguaFrame-Demo-Token`
- Compose passes both variables to backend and split-worker services; workers receive the config but only HTTP API requests are gated.

- [x] **Step 1: Update runtime docs**
  - README documents local open mode and private demo gated mode.
  - README gives a safe example using a placeholder token, not a real secret.
  - Roadmap Phase 9 marks controlled uploads, conservative config guide, and private URL access gate as implemented.
  - Spec records that this is an owner-only private demo gate, not user authentication.
  - Decisions records why a token gate was chosen before JWT.

- [x] **Step 2: Run Compose validation**

```bash
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
```

Expected: both commands exit 0.

- [x] **Step 3: Run selected full-slice validation**

```bash
mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,LinguaFramePropertiesTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
```

Expected: backend tests, frontend tests, and Compose config checks pass.

## Task 4: Full Validation, Commit, And Merge

**Files:**

- Modify all files touched by Tasks 1-3.

- [x] **Step 1: Run full validation before merge**

```bash
mvn -pl LinguaFrame test
cd frontend && npm run test:run
cd frontend && npm run build
docker compose --env-file .env.example config --quiet
docker compose --env-file .env.example --profile split-workers config --quiet
git diff --check
```

Expected: all commands exit 0.

- [x] **Step 2: Commit feature branch**

```bash
git add README.md .env.example docker-compose.yml docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md docs/plans/036-private-demo-access-gate-mvp.md LinguaFrame/src frontend/src
git commit -m "Add private demo access gate"
```

- [x] **Step 3: Merge back to main**

```bash
git switch main
git merge --no-ff private-demo-access-gate-mvp
```

- [x] **Step 4: Run post-merge smoke validation**

```bash
mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,RuntimeDependencyControllerTests test
cd frontend && npm run test:run -- linguaframeApi App
```

Expected: post-merge checks pass on `main`.
