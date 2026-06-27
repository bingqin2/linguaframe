# Docker Runtime Freshness Guard MVP

**Goal:** Detect stale Docker backend containers before demo uploads by exposing a safe runtime contract summary and checking it from preflight.

**Architecture:** Extend the existing sanitized runtime dependency endpoint with a non-secret `runtime` block that reports the application version, expected latest Flyway migration version bundled in the running jar, and required demo API routes. Update the private demo preflight script to compare the running backend contract against the repository's local migration files and required endpoints. Surface the same runtime contract in the React demo readiness panel so stale-container symptoms are visible without reading logs.

## Scope

- Add a backend runtime contract summary to `GET /api/runtime/dependencies`.
- Compute `latestMigrationVersion` by scanning classpath `db/migration/V*__*.sql` resources inside the running jar.
- Return only safe metadata: app version, latest migration version, and required route paths.
- Update `scripts/demo/private-demo-preflight.sh` to fail early when:
  - the running backend reports a lower migration version than the local repo, or
  - a required demo route such as `/api/jobs/__preflight__/diagnostics/download` is missing at the routing layer.
- Update the React demo readiness panel to display app version and migration contract.
- Update README, Docker E2E runbook, smoke checklist, product docs, and execution log.

## Non-Goals

- Do not expose passwords, API keys, object keys, local media paths, Git remotes, branch names, commit hashes, or environment variables.
- Do not run database migration queries from the readiness endpoint; this is a running-code contract check, not a live database audit.
- Do not rebuild Docker images automatically from preflight; print the exact package/recreate command instead.
- Do not require the frontend to block uploads; the frontend panel is informational in this slice.

## Implementation Steps

1. **Backend runtime contract**
   - Add `RuntimeContractVo` with fields `appVersion`, `latestMigrationVersion`, and `requiredRoutes`.
   - Add the field to `RuntimeDependencySummaryVo`.
   - Update `RuntimeDependencySummaryServiceImpl` to scan classpath migration resources and parse the highest numeric `V<number>__` prefix.
   - Include required routes for current demo-critical APIs, including diagnostics download, artifact archive download, runtime dependencies, uploads, and job detail.
   - Add backend tests proving the fields exist, the migration version is at least `17`, and the response remains secret-free.

2. **Preflight freshness guard**
   - Add a local helper in `private-demo-preflight.sh` that computes the highest migration version from `LinguaFrame/src/main/resources/db/migration`.
   - Fetch `/api/runtime/dependencies` after backend health and parse `runtime.latestMigrationVersion`.
   - Fail with a clear stale-backend message when the running version is lower than the local version.
   - Probe required route availability using documented placeholder IDs and accept non-404 application responses where appropriate; fail when the route is absent.
   - Keep token-gated environments working by sending the configured demo token header when present.

3. **React readiness visibility**
   - Extend `RuntimeDependencySummary` TypeScript types with `runtime`.
   - Update `DemoReadinessPanel` to show app version and latest migration version.
   - Add/adjust frontend tests so the panel renders runtime contract metadata and still tolerates readiness failures.

4. **Documentation and validation**
   - Document the stale-container guard and recreate command in README and `docs/agent/docker-e2e-demo.md`.
   - Update `docs/agent/smoke-test-checklist.md`, `docs/product/spec.md`, `docs/product/roadmap.md`, and `docs/progress/execution-log.md`.
   - Run backend tests, frontend tests, preflight, Docker Compose config rendering, and `git diff --check`.

## Acceptance Criteria

- A stale backend container with an older migration contract fails preflight before media upload.
- A current backend reports `runtime.latestMigrationVersion` matching the highest local Flyway migration.
- The readiness endpoint and UI expose no secrets or machine-local paths.
- The preflight error tells the contributor to run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up -d --build linguaframe-backend
```

- The React demo readiness panel displays the runtime contract without blocking the rest of the app.
