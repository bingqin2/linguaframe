# Private Demo Owner Session MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace the browser-only private demo token entry with an owner-session flow that proves private demo access is intentional, observable, and still single-owner.

**Architecture:** Keep the existing `linguaframe.demo.access-token` gate and do not add real users or JWT. Add small `/api/demo-session` endpoints that validate the configured token, set/clear the existing same-site demo cookie, and return a sanitized session status. Keep header token compatibility for curl, Swagger, scripts, and existing tests. Update the React header to show logged-in/logged-out state, login/logout actions, and clear messages without exposing the token.

**Tech Stack:** Spring Boot MVC, existing `DemoAccessInterceptor`, JUnit 5/MockMvc, React, TypeScript, Vitest, Testing Library, Markdown.

## Global Constraints

- Do not add public registration, password database, JWT users, OAuth, billing, or multi-tenant authorization.
- Keep local development open when `linguaframe.demo.access-token` is empty.
- Preserve `X-LinguaFrame-Demo-Token` compatibility for scripts, Swagger, curl, and existing browser requests.
- Do not log or return configured token values.
- Session status responses may expose only safe facts: `accessGateEnabled`, `authenticated`, `headerName`, and cookie/session mode labels.
- The feature must include backend endpoints, frontend session UI, tests, docs, validation, this plan file, and merge tracking.

---

## Task 1: Backend Owner Session API

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoSessionController.java`
- Create if useful: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoSessionStatusVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoAccessInterceptor.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoAccessWebMvcConfiguration.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/common/security/DemoSessionControllerTests.java`

**Steps:**

- [x] Add `GET /api/demo-session` that returns sanitized session state.
- [x] Add `POST /api/demo-session/login` that accepts a token body, validates it against `linguaframe.demo.access-token`, sets `LinguaFrame-Demo-Token` as `HttpOnly`, `SameSite=Lax`, path `/`, and returns authenticated status.
- [x] Add `POST /api/demo-session/logout` that clears the cookie and returns unauthenticated status.
- [x] Exclude `/api/demo-session`, `/api/demo-session/login`, and `/api/demo-session/logout` from the access interceptor so a locked-out browser can log in.
- [x] Keep all other `/api/**` paths protected when the access gate is enabled.
- [x] Keep header and existing cookie token access accepted by the interceptor.
- [x] Add MockMvc tests for open demo mode, gated unauthenticated status, failed login, successful login cookie, logout cookie clearing, and protected API access after login.

## Task 2: Frontend API Client And Header UI

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/api/linguaframeApi.test.ts`
- Test: `frontend/src/App.test.tsx`

**Steps:**

- [x] Add `DemoSessionStatus` type with `accessGateEnabled`, `authenticated`, `headerName`, and `mode`.
- [x] Add API functions: `getDemoSession`, `loginDemoSession(token)`, and `logoutDemoSession()`.
- [x] Keep `readDemoToken`/`writeDemoToken` for backward-compatible header mode, but make the header UI prefer session login/logout.
- [x] On app startup, load session status and show `Open demo`, `Owner session active`, or `Owner session required`.
- [x] Change the header form copy from raw token storage to owner session login.
- [x] Add logout action that calls the backend and clears local fallback token storage/cookie.
- [x] Keep existing demo token header behavior for API calls if a token is stored locally.
- [x] Add frontend tests for session status rendering, login success, failed login message, logout, and fallback header compatibility.

## Task 3: OpenAPI, Preflight, And Script Compatibility

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java` if needed
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
- Modify: `scripts/demo/private-demo-preflight.sh`
- Modify: `docs/agent/smoke-test-checklist.md`

**Steps:**

- [x] Document the demo-session endpoints in OpenAPI through controller annotations.
- [x] Keep the existing `DemoAccessToken` API key scheme for Swagger/manual calls.
- [x] Extend preflight to check `GET /api/demo-session` when demo access is enabled.
- [x] Keep preflight token-gate checks using the configured header so terminal demos still work.
- [x] Update smoke checklist with session login/logout, header fallback, and protected API behavior.

## Task 4: Documentation And Product Tracking

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/070-private-demo-owner-session-mvp.md`

**Steps:**

- [x] Document that owner session is a private-demo gate, not public authentication.
- [x] Document local open mode when no token is configured.
- [x] Document browser login/logout and curl/Swagger header fallback.
- [x] Update roadmap Phase 9 and target-state Stage 2 with owner-session status.
- [x] Record the decision to keep this as a single-owner session before JWT/public users.
- [x] Update execution log with implementation and validation results.

## Task 5: Validation

**Files:**
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/070-private-demo-owner-session-mvp.md`

**Steps:**

- [x] Run `mvn -pl LinguaFrame -Dtest=DemoSessionControllerTests,DemoAccessInterceptorTests,OpenApiDocumentationTests test`.
- [x] Run `cd frontend && npm run test:run -- App linguaFrameApi`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash -n scripts/demo/private-demo-preflight.sh`.
- [x] Run `docker compose --env-file .env.example config --quiet`.
- [x] Run `git diff --check`.
- [x] Run a focused post-merge validation on `main` after merging.

## Done Criteria

- [x] Browser demo clearly shows open mode, owner session required, or owner session active.
- [x] A configured private demo token can establish and clear a same-site owner session without exposing the token.
- [x] Existing header-token flows for scripts, Swagger, and curl still work.
- [x] Protected `/api/**` routes remain gated when token access is enabled, while session login endpoints remain reachable.
- [x] Backend and frontend tests cover login, logout, failed login, open mode, protected access, and fallback token behavior.
- [x] README, Docker E2E guide, smoke checklist, roadmap, target-state, decisions, execution log, and this plan are updated.
- [x] The feature branch is committed, verified, and merged back to `main`.
