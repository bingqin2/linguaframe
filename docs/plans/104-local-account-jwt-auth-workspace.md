# Local Account JWT Auth Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a minimal local account login and JWT bearer-token path that moves LinguaFrame from owner-only private demo access toward real authenticated usage while preserving existing demo-token workflows.

**Architecture:** Keep the existing demo access gate as a compatibility layer, then add a separate local account auth layer under `/api/auth/**`. Auth uses configured local owner credentials and HMAC-signed JWTs, exposes sanitized session status, and lets protected `/api/**` requests pass with either a valid bearer token or the existing demo token/cookie. Owner identity resolves to the authenticated JWT subject when present and falls back to the configured demo owner.

**Tech Stack:** Spring Boot MVC interceptors, Java HMAC/JWT utilities without adding a full user-management subsystem, JUnit 5 + MockMvc, React + TypeScript + Vitest, Bash demo-client helpers, Markdown docs.

## Global Constraints

- This slice must be one complete feature: backend auth API, request protection compatibility, owner identity integration, browser login/logout, terminal validation, OpenAPI/docs, validation, commit, and merge back to `main`.
- Do not build public registration, password reset, roles/permissions, billing, database-backed users, OAuth, or enterprise account management.
- Keep `linguaframe.demo.access-token` header/cookie compatibility so existing Swagger, curl, scripts, SSE, previews, and downloads still work.
- Never expose configured passwords, JWT signing secrets, demo tokens, bearer tokens, local paths, provider payloads, transcript text, subtitle text, object keys, or media bytes in responses, logs, scripts, reports, or tests.
- JWT auth must be disabled until both a signing secret and local owner password are configured.
- Use short-lived bearer tokens and explicit sanitized status messages; do not persist sessions server-side.

---

## Current Context

- `DemoAccessInterceptor` protects `/api/**` when `linguaframe.demo.access-token` is configured and accepts the demo header or `LinguaFrame-Demo-Token` cookie.
- `DemoSessionController` supports browser owner-session login/logout for the private-demo token.
- `DemoOwnerIdentityService` currently returns the configured `linguaframe.demo.owner-id`, and upload/job reads are scoped through that owner id.
- The frontend stores demo tokens through `frontend/src/api/linguaframeApi.ts` and already has an owner access token form.
- OpenAPI currently documents `DemoAccessToken` only.

## Task 1: Backend Local Auth Configuration And Token Service

**Files:**
- Modify: `LinguaFrame/pom.xml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/LocalAuthTokenClaims.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/LocalAuthTokenService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/impl/HmacLocalAuthTokenService.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/security/HmacLocalAuthTokenServiceTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- Add `linguaframe.auth.enabled`, `linguaframe.auth.owner-username`, `linguaframe.auth.owner-password`, `linguaframe.auth.jwt-secret`, `linguaframe.auth.token-ttl-minutes`, and `linguaframe.auth.issuer`.
- `isLocalAuthConfigured()` returns true only when auth is enabled, owner password is nonblank, and JWT secret length is at least 32 characters.
- `LocalAuthTokenService.issueOwnerToken()` returns a compact bearer token for the configured owner username and owner id.
- `LocalAuthTokenService.parse(String token)` verifies signature, issuer, expiry, and subject, then returns `LocalAuthTokenClaims`.

- [x] Write failing token service tests for issuing/parsing a configured owner token, rejecting tampered tokens, rejecting expired tokens, and not leaking secret/password in exception messages.
- [x] Write failing property binding tests for default-disabled auth and configured auth.
- [x] Add properties and a token service using HMAC-SHA256 with Base64URL encoding and constant-time signature comparison.
- [x] Run `mvn -pl LinguaFrame -Dtest=HmacLocalAuthTokenServiceTests,LinguaFramePropertiesTests test`.

## Task 2: Backend Auth API, Request Gate, And Owner Identity

**Files:**
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthLoginRequestDto.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthSessionStatusVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthLoginResponseVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthController.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/AuthenticatedOwnerContext.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/security/LocalAuthInterceptor.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/DemoAccessWebMvcConfiguration.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/security/ConfiguredDemoOwnerIdentityService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/security/LocalAuthControllerTests.java`
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/security/LocalAuthInterceptorTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`

**Interfaces:**
- Add `GET /api/auth/session` returning enabled/configured/authenticated/ownerId/username/authMode without secrets.
- Add `POST /api/auth/login` accepting `{ "username": "...", "password": "..." }` and returning `{ token, tokenType, expiresAt, session }` only when local auth is configured and credentials match.
- Add `POST /api/auth/logout` returning unauthenticated status; it is stateless and client-driven.
- `Authorization: Bearer <token>` authenticates protected `/api/**` requests when local auth is configured.
- Existing demo token header/cookie still authenticates protected `/api/**`.
- If both demo access gate and local auth are unconfigured, local development remains open as today.
- If local auth is configured, `/api/auth/**`, `/api/demo-session/**`, `/actuator/health`, `/v3/api-docs`, and Swagger remain reachable without bearer token.

- [x] Write failing controller tests for status, successful login, wrong password, disabled/unconfigured auth, and response redaction.
- [x] Write failing interceptor tests proving bearer token access works, missing/invalid bearer token is rejected when local auth is configured, demo token compatibility still works, and auth/session endpoints are not blocked.
- [x] Add auth controller, request context, interceptor registration, and owner identity lookup from authenticated claims.
- [x] Extend OpenAPI with an `BearerAuth` HTTP security scheme and auth paths.
- [x] Run `mvn -pl LinguaFrame -Dtest=LocalAuthControllerTests,LocalAuthInterceptorTests,DemoAccessInterceptorTests,DemoSessionControllerTests,OpenApiDocumentationTests test`.

## Task 3: Frontend Account Login And Bearer Token Support

**Files:**
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/api/linguaframeApi.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Add `AuthSessionStatus` and `AuthLoginResponse` frontend types.
- Add `getAuthSession()`, `loginAuthSession(username, password)`, and `logoutAuthSession()`.
- Store bearer token under `linguaframe.authToken.v1`, send it as `Authorization: Bearer ...`, and keep the existing demo token header behavior unchanged.
- Add an account sign-in panel near the owner access token controls showing mode, username/owner id, sign-in, sign-out, and sanitized errors.
- On successful login/logout, refresh protected readiness/dashboard data without exposing the token.
- Keep upload controls usable when auth status calls fail; show a concise unavailable message.

- [x] Write failing API tests for login, logout, token storage, bearer header injection, and demo-token compatibility.
- [x] Write failing App tests for configured account sign-in, wrong password error, sign-out, and protected API retry after login.
- [x] Implement types, API helper functions, token storage/header injection, and browser account login panel.
- [x] Run `cd frontend && npm test -- --run src/api/linguaframeApi.test.ts App.test.tsx -t "auth"`.

## Task 4: Terminal Auth Smoke Script And Demo Compatibility

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `scripts/demo/auth-smoke.sh`
- Modify: `scripts/demo/test-linguaframe-demo-client.sh`
- Modify: `.env.example`
- Modify: `.env.private-demo.example`

**Interfaces:**
- Add `login_local_auth_json(base_url, username, password, output_path)` that writes the raw login response only to a caller-provided path.
- Add `print_local_auth_summary_file(path)` with metadata-only lines:
  - `localAuthEnabled=...`
  - `localAuthConfigured=...`
  - `localAuthAuthenticated=...`
  - `localAuthOwnerId=...`
  - `localAuthUsername=...`
  - `localAuthTokenExpiresAt=...`
- Add `scripts/demo/auth-smoke.sh` that:
  - reads `LINGUAFRAME_AUTH_USERNAME` and `LINGUAFRAME_AUTH_PASSWORD` from the selected env file or environment;
  - calls `/api/auth/session`;
  - logs in only when auth is configured;
  - verifies `/api/runtime/dependencies` with the returned bearer token;
  - writes JSON under `/tmp/linguaframe-demo/local-auth/`;
  - never prints the password, bearer token, signing secret, or demo token.
- Add commented env template settings for local auth, disabled by default.

- [x] Write failing shell tests for auth summary redaction, bearer-token runtime dependency call shape, and disabled-auth behavior.
- [x] Implement shell helpers, `auth-smoke.sh`, and env template comments.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/auth-smoke.sh`.

## Task 5: Documentation, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/deployment/private-demo.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/104-local-account-jwt-auth-workspace.md`

**Interfaces:**
- Document local account auth as an incremental Stage 3 bridge, not public registration or multi-user SaaS.
- Document exact env variables, minimum secret length, browser sign-in flow, bearer-token curl usage, and demo-token compatibility.
- Record that passwords/JWT secrets/tokens must remain local and uncommitted.

- [x] Update docs and execution log.
- [x] Run `mvn -pl LinguaFrame test`.
- [x] Run `cd frontend && npm test -- --run`.
- [x] Run `cd frontend && npm run build`.
- [x] Run `bash scripts/demo/test-linguaframe-demo-client.sh`.
- [x] Run `git diff --check`.
- [x] Commit on the feature branch, merge back to `main`, run post-merge focused validation, and record the merge in the execution log.
