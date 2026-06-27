# Execution Log

This file records implementation progress, validation commands, failures, and follow-up notes.

## 2026-06-28

Work:

- Added `scripts/demo/private-demo-backup.sh` for private-demo MySQL, MinIO, Caddy state, and optional Redis/RabbitMQ backups.
- Added `scripts/demo/private-demo-restore.sh` with dry-run validation and mandatory `--yes` for real restores.
- Ignored repository-local backup output directories.
- Updated private-demo deployment docs, README, Docker E2E guide, smoke checklist, roadmap, target-state, decisions, and this execution log.

Validation:

- `bash -n scripts/demo/private-demo-backup.sh scripts/demo/private-demo-restore.sh` passed.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-backup.sh --dry-run --output-dir /tmp/linguaframe-private-demo-backups` passed and printed only safe component names and target paths.
- Created a synthetic restore smoke backup under `/tmp/linguaframe-private-demo-restore-smoke`.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-restore.sh --dry-run --backup-dir /tmp/linguaframe-private-demo-restore-smoke` passed without writing service data.
- `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet` passed.
- `git diff --check` passed.
- Full non-dry-run backup/restore was not executed because it requires a running private-demo stack and writes service data.

Post-merge verification:

- Merged `codex-private-demo-backup-restore` back to `main` with a merge commit.
- `bash -n scripts/demo/private-demo-backup.sh scripts/demo/private-demo-restore.sh` passed on `main`.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-backup.sh --dry-run --output-dir /tmp/linguaframe-private-demo-backups` passed on `main`.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-restore.sh --dry-run --backup-dir /tmp/linguaframe-private-demo-restore-smoke` passed on `main`.
- `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-28

Work:

- Added a private-demo Compose overlay with a Caddy reverse proxy.
- Added `.env.private-demo.example` with placeholder-only deployment values.
- Added `scripts/demo/private-demo-deploy-preflight.sh` to validate proxy shape, required env values, and safe deployment defaults before startup.
- Added `docs/deployment/private-demo.md` and updated README, Docker E2E guide, smoke checklist, roadmap, decisions, and this execution log with the private server demo path.

Validation:

- `bash -n scripts/demo/private-demo-deploy-preflight.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed, proving the default local Compose stack still renders.
- `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet` passed.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-deploy-preflight.sh` passed and printed proxy services, public ports, and backend/frontend host-port status without secret values.
- `git diff --check` passed.
- Maven and frontend tests were not required because this slice changed deployment configuration, shell preflight, and documentation only; no Java or React runtime code changed.

Post-merge verification:

- Merged `codex-private-demo-reverse-proxy` back to `main` with a merge commit.
- `bash -n scripts/demo/private-demo-deploy-preflight.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `docker compose --env-file .env.private-demo.example -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml config --quiet` passed on `main`.
- `LINGUAFRAME_ENV_FILE=.env.private-demo.example scripts/demo/private-demo-deploy-preflight.sh` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-28

Work:

- Added a disabled-by-default OpenAI connectivity probe to runtime live checks.
- Added safe OpenAI probe configuration for `.env.example` and Spring properties.
- Updated private-demo preflight, React live checks, README, Docker E2E guide, smoke checklist, roadmap, decisions, and this execution log with OpenAI connectivity behavior.

Validation:

- `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests test` first failed because the OpenAI connectivity service and properties did not exist, then passed after adding the service and runtime live-check integration.
- `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test` passed with `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App -t "shows live dependency checks"` first failed because the OpenAI probe had no UI label, then passed after adding the `openai` type and label.
- `cd frontend && npm run test:run -- App` passed with `Tests 41 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `bash -n scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.
- `mvn -pl LinguaFrame test` passed with `Tests run: 363, Failures: 0, Errors: 0, Skipped: 0`.

Post-merge verification:

- Merged `codex-openai-connectivity-check` back to `main` with a merge commit.
- `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,OpenApiDocumentationTests test` passed on `main` with `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 41 passed`.
- `bash -n scripts/demo/private-demo-preflight.sh` passed on `main`.

## 2026-06-28

Work:

- Added `GET /api/jobs/{jobId}/evidence/bundle/download` for metadata-only demo evidence ZIPs containing `manifest.json`, `evidence.md`, and `diagnostics.json`.
- Added a React `Download evidence bundle` link and API helper.
- Extended `scripts/demo/docker-e2e-success.sh` to download and validate `/tmp/linguaframe-demo/job-evidence.zip`.
- Updated README, Docker E2E guide, smoke checklist, roadmap, decisions, and this execution log with evidence bundle behavior.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#downloadsJobEvidenceBundle test` first failed with HTTP 404 for the missing route, then passed after adding the evidence bundle service and controller endpoint.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#downloadsJobEvidenceBundle,OpenApiDocumentationTests,RuntimeDependencyControllerTests test` passed with `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App -t "exports safe browser demo evidence"` first failed because the `Download evidence bundle` link was missing, then passed after adding the API helper and UI link.
- `cd frontend && npm run test:run -- src/api/linguaframeApi.test.ts -t "evidence bundle"` passed.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh` passed.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test` passed with `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaFrameApi` passed with `Test Files 2 passed` and `Tests 66 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.
- `mvn -pl LinguaFrame test` passed with `Tests run: 359, Failures: 0, Errors: 0, Skipped: 0`.

Post-merge verification:

- Merged `codex-demo-run-evidence-bundle-mvp` back to `main` with a merge commit.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaFrameApi` passed on `main` with `Test Files 2 passed` and `Tests 66 passed`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh` passed on `main`.

## 2026-06-28

Work:

- Added `GET /api/jobs/{jobId}/evidence/markdown/download` for backend-generated, sanitized Markdown demo evidence.
- Added a React `Download backend evidence` link to the `Demo evidence` panel and API client URL helper.
- Extended `scripts/demo/docker-e2e-success.sh` to download and validate `/tmp/linguaframe-demo/job-evidence.md`.
- Updated README, Docker E2E guide, smoke checklist, roadmap, decisions, and this execution log with backend evidence Markdown behavior.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#downloadsJobEvidenceMarkdownReport test` first failed with HTTP 404 for the missing route, then passed after adding the report service and controller endpoint.
- `cd frontend && npm run test:run -- App -t "exports safe browser demo evidence"` first failed because the `Download backend evidence` link was missing, then passed after adding the API helper and UI link.
- `cd frontend && npm run test:run -- src/api/linguaframeApi.test.ts -t "evidence markdown"` passed.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobQueryServiceTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test` passed with `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaFrameApi` passed with `Test Files 2 passed` and `Tests 65 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.
- `mvn -pl LinguaFrame test` passed with `Tests run: 358, Failures: 0, Errors: 0, Skipped: 0`.

Post-merge verification:

- Merged `codex-backend-demo-evidence-markdown-report` back to `main` with a merge commit.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobQueryServiceTests,OpenApiDocumentationTests,RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaFrameApi` passed on `main` with `Test Files 2 passed` and `Tests 65 passed`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/private-demo-preflight.sh` passed on `main`.

## 2026-06-28

Work:

- Added `GET /api/runtime/live-checks` for bounded non-destructive MySQL, Redis, RabbitMQ, MinIO, and FFmpeg probes.
- Added a React `Live checks` sidebar panel and API client support for live probe summaries.
- Updated private-demo preflight to fail before upload when a runtime live dependency is down.
- Updated README, Docker E2E guide, smoke checklist, roadmap, and this execution log with live-check behavior.

Validation:

- `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,MinioObjectStorageServiceTests test` passed during backend implementation with `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaFrameApi` passed with `Test Files 2 passed` and `Tests 64 passed`.
- `mvn -pl LinguaFrame -Dtest=RuntimeLiveCheckServiceTests,RuntimeDependencyControllerTests,MinioObjectStorageServiceTests,OpenApiDocumentationTests,DemoAccessInterceptorTests test` first failed because the new live-check service had ambiguous constructors and then passed after marking the production constructor with `@Autowired`.
- `mvn -pl LinguaFrame test` first failed because the live-check service depended directly on the MinIO object storage bean that several controller tests replace with an `ObjectStorageService` mock; it passed after moving bucket reachability into a separate `ObjectStorageHealthCheckService`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 357, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Test Files 3 passed` and `Tests 69 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `bash -n scripts/demo/private-demo-preflight.sh scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `codex-runtime-live-dependency-checks-mvp` back to `main` with a merge commit.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 357, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Test Files 3 passed` and `Tests 69 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/private-demo-preflight.sh scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added typed frontend upload validation for `POST /api/media/uploads/validate`.
- Added browser upload preflight state, `Validate file` action, validation result panel, and upload gating that blocks job creation unless validation returns `READY`.
- Updated README, Docker E2E guide, and smoke-test checklist with browser upload preflight behavior.

Validation:

- `cd frontend && npm run test:run -- linguaframeApi` first failed because `validateUpload` did not exist, then passed with `Tests 23 passed`.
- `cd frontend && npm run test:run -- App -t "validates selected file|blocks upload|upload validation|uploads a video"` first failed because the upload form did not validate before upload, did not expose `Validate file`, and did not render an upload validation panel.
- `cd frontend && npm run test:run -- linguaframeApi App` passed after implementation with `Test Files 2 passed` and `Tests 57 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed after rerunning from the correct repository path.

## 2026-06-27

Work:

- Merged `browser-upload-preflight-mvp` back to `main` with a merge commit.

Validation:

- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Test Files 2 passed` and `Tests 57 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added a browser `Demo runbook` panel that shows the local startup command, E2E validation scripts, demo URLs, runtime constraints, provider modes, budget guard state, subtitle burn-in state, and sample-media guidance.
- Kept the runbook useful when readiness loading fails by always rendering static commands and showing a runtime guidance error.
- Updated README, Docker E2E guide, and smoke-test checklist with the in-app runbook expectations.

Validation:

- `cd frontend && npm run test:run -- App -t "demo runbook"` first failed because no `Demo runbook` region existed, then failed because disabled provider wording did not expose the disabled state.
- `cd frontend && npm run test:run -- App` passed after implementation with `Test Files 1 passed` and `Tests 31 passed`.
- `cd frontend && npm run build` passed and produced the Vite production bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `browser-demo-evidence-export-mvp` back to `main` with merge commit.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 37 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Merged `browser-demo-runbook-panel-mvp` back to `main` with a merge commit.

Validation:

- `cd frontend && npm run test:run -- App` passed on `main` with `Test Files 1 passed` and `Tests 31 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added `scripts/demo/start-local-demo.sh` as a one-command local demo startup path.
- The script packages the backend, recreates `linguaframe-backend`, waits for backend health, starts the local frontend fallback when needed, runs private-demo preflight, and prints the browser URL plus next E2E commands.
- Documented the startup path in README, Docker E2E guide, and smoke-test checklist.

Validation:

- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `scripts/demo/start-local-demo.sh --help` and `scripts/demo/frontend-local-dev.sh --help` passed.
- `cd frontend && npm run test:run -- App` passed with `Test Files 1 passed` and `Tests 29 passed`.
- `docker compose --env-file .env.example config --quiet` passed.
- `scripts/demo/start-local-demo.sh` passed against the local Docker stack: Maven package succeeded, `linguaframe-backend` rebuilt and restarted, the local Vite fallback started at `http://localhost:5173`, private-demo preflight passed, and the script stopped only the frontend process it started.

## 2026-06-27

Work:

- Merged `demo-one-command-local-startup-mvp` back to `main` with a merge commit.

Validation:

- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Test Files 1 passed` and `Tests 29 passed`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-22

Work:

- Created the initial LinguaFrame documentation set.
- Defined product scope, target state, architecture, roadmap, milestones, backend code standard, AI-assisted development guardrails, and smoke-test checklist.

Validation:

- Documentation-only change. Code validation not run.

Notes:

- The existing backend was generated by Spring Initializr under `LinguaFrame/`.
- Current backend package is `com.linguaframe`.
- Current backend uses Java 21 and Spring Boot 3.5.15.

## 2026-06-25

Work:

- Completed backend build-layout correction.
- Moved the Maven project file into `LinguaFrame/` so the wrapper, `pom.xml`, source tree, test tree, and resources live in the same backend module.
- Updated contributor-facing commands in `README.md` and `AGENTS.md`.

Validation:

- `./LinguaFrame/mvnw -f pom.xml test` before the fix showed `No sources to compile` and `No tests to run`, proving the root Maven command did not validate backend code.
- `cd LinguaFrame && JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home ./mvnw test` in the restricted sandbox compiled backend sources but failed because Mockito/Byte Buddy could not self-attach to the JVM.
- The same Java 21 command with unsandboxed process permissions passed with `Tests run: 1, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add dependencies, Docker Compose, frontend code, or application features.
- The project target is Java 21. The default shell currently reports Java 25, so local validation should set `JAVA_HOME` to the installed Java 21 runtime when needed.

## 2026-06-25

Work:

- Added root Maven aggregator `pom.xml`.
- Kept `LinguaFrame/pom.xml` as the backend module build file.
- Updated README and AGENTS command examples to run Maven from the repository root.

Validation:

- `mvn test` failed before this slice because the repository root had no `pom.xml`.
- `mvn -q help:evaluate -Dexpression=project.modules -DforceStdout` from the repository root reported `LinguaFrame` as a module.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed and ran `LinguaFrameApplicationTests`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test` passed and ran the backend module test suite.

Notes:

- This slice restores root-level Maven commands without reintroducing the earlier no-op root build.

## 2026-06-25

Work:

- Added backend runtime foundation dependencies for Web, Validation, and Actuator.
- Added actuator health coverage through `ActuatorHealthTests`.
- Added base and local Spring configuration using the `linguaframe` prefix.
- Documented root-level test, run, and health-check commands.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=ActuatorHealthTests` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 2, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add database, queue, object storage, OpenAI, Docker, frontend, upload, or worker behavior.

## 2026-06-25

Work:

- Added typed `LinguaFrameProperties` binding for media, worker, and cost configuration.
- Enabled configuration properties scanning from the Spring Boot application entry point.
- Added validation coverage for invalid numeric runtime settings.
- Documented the current runtime configuration surface in `README.md`.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` failed before implementation with `cannot find symbol` for `LinguaFrameProperties`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 4, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add external service dependencies or upload/worker behavior.
- Future upload validation should consume `LinguaFrameProperties.Media` instead of duplicating media limits.

## 2026-06-25

Work:

- Added Springdoc OpenAPI Web MVC UI dependency using version `2.8.17`.
- Added `OpenApiConfiguration` with LinguaFrame API title, version, and description.
- Added HTTP coverage for `/v3/api-docs` and `/swagger-ui/index.html`.
- Documented local API documentation URLs in `README.md`.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests` failed before implementation because the documentation endpoints returned `404 NOT_FOUND`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests` passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 6, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add application API controllers.
- Future upload/job APIs should appear in the generated OpenAPI document automatically.

## 2026-06-25

Work:

- Added root Docker Compose runtime skeleton for MySQL, Redis, RabbitMQ, MinIO, and the backend.
- Added `.env.example` with local development placeholders.
- Added backend multi-stage Dockerfile using Java 21.
- Added Docker Spring profile placeholders for container runtime.
- Documented local Docker runtime commands in `README.md`.

Validation:

- `docker compose config` failed before implementation because no root Compose file existed.
- `docker compose --env-file .env.example config --services` returned `minio`, `mysql`, `rabbitmq`, `redis`, and `linguaframe-backend`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed and built `linguaframe-linguaframe-backend:latest`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 6, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add database migrations, storage client code, queue consumers, upload APIs, or worker behavior.
- Real local secrets should live in `.env`, which is ignored by git.

## 2026-06-25

Work:

- Added typed runtime dependency configuration for MySQL, Redis, RabbitMQ, and MinIO.
- Added a sanitized `GET /api/runtime/dependencies` endpoint.
- Added test coverage for dependency configuration validation and secret-free runtime summary output.
- Documented the new runtime configuration keys and summary endpoint.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed before implementation with `Tests run: 6, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` failed before implementation because dependency configuration getters did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests` failed before implementation because `/api/runtime/dependencies` returned `404 NOT_FOUND`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests` passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed after implementation with `Tests run: 9, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed and built `linguaframe-linguaframe-backend:latest`.
- `git diff --check` passed.

Notes:

- This slice intentionally did not open network connections to MySQL, Redis, RabbitMQ, or MinIO.
- The runtime summary endpoint must remain secret-free as more providers are added.

## 2026-06-25

Work:

- Added JDBC, Flyway, MySQL runtime, H2 test, and MinIO client dependencies for upload intake.
- Added Flyway schema for `videos` and `localization_jobs`.
- Added repositories for durable upload and queued localization job records.
- Added media upload validation, object storage boundary, MinIO storage implementation, and upload orchestration.
- Added `POST /api/media/uploads/validate`, `POST /api/media/uploads`, `GET /api/media/uploads/{videoId}`, and `GET /api/jobs/{jobId}`.
- Documented upload limit environment variables and media upload intake commands.
- Simplified the backend Dockerfile package path after `dependency:go-offline` proved too expensive for container verification.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed before implementation with `Tests run: 9, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests` failed before implementation because Spring JDBC `JdbcClient` was missing.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests` passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='*RepositoryTests'` failed before implementation because repository records, enums, and repositories did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='*RepositoryTests'` passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUpload*ServiceTests'` failed before implementation because validation, storage, and upload service types did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUpload*ServiceTests'` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUploadControllerTests,LocalizationJobControllerTests,OpenApiDocumentationTests'` failed before implementation because upload and job API paths returned `404`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest='MediaUploadControllerTests,LocalizationJobControllerTests,OpenApiDocumentationTests'` passed with `Tests run: 9, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed after implementation with `Tests run: 29, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` was interrupted after the original Dockerfile spent over nine minutes in `dependency:go-offline` dependency resolution without reaching package.
- `docker compose --env-file .env.example build linguaframe-backend` passed after simplifying the Dockerfile and built `linguaframe-linguaframe-backend:latest`.
- `git diff --check` passed.

Notes:

- This slice intentionally does not publish RabbitMQ messages, run a worker, inspect video duration with FFmpeg, call OpenAI, generate subtitles, or create TTS output.
- Controller tests replace object storage with a mock service; Docker Compose verification covers production wiring separately.

## 2026-06-26

Work:

- Added Spring AMQP dependency and environment-backed RabbitMQ job routing configuration.
- Added a durable `job_dispatch_events` outbox table and JDBC repository.
- Wired media upload intake to create a pending localization dispatch event in the same transaction as the video and job records.
- Added a RabbitMQ publisher boundary, durable direct exchange/queue/binding configuration, and conditional scheduled dispatcher.
- Added dispatcher failure accounting for publisher errors and malformed payloads.
- Extended `GET /api/jobs/{jobId}` with dispatch status, attempts, and dispatched timestamp visibility.
- Added `mock-maker-subclass` test resource after Microsoft JDK 21 could not self-attach Mockito inline mock maker in this environment.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` initially failed in sandbox because Mockito inline self-attach could not initialize.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed after adding `mock-maker-subclass`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with escalation before feature implementation with `Tests run: 29, Failures: 0, Errors: 0`; escalation was required because `RANDOM_PORT` tests bind localhost sockets.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` failed before implementation because dispatch and RabbitMQ routing getters did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests,JobDispatchEventRepositoryTests` failed before implementation because dispatch event domain types and repository did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests,JobDispatchEventRepositoryTests` passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=MediaUploadServiceTests` failed before implementation because `MediaUploadServiceImpl` did not accept an outbox dependency.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=MediaUploadServiceTests,MediaUploadControllerTests` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobDispatchServiceTests,RabbitJobQueueConfigurationTests` failed before implementation because dispatcher and Rabbit topology types did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobDispatchServiceTests,RabbitJobQueueConfigurationTests` passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests` failed before implementation because the job response did not include dispatch fields.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests` passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` failed during final verification because the test profile included the RabbitMQ health indicator and dispatcher tests shared H2 state; the test profile now disables Rabbit health and dispatcher tests clean their tables.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed after final fixes with `Tests run: 39, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed and built `linguaframe-linguaframe-backend:latest`.
- `git diff --check` passed.

Notes:

- Upload success remains independent from RabbitMQ availability; RabbitMQ publishing is handled asynchronously from durable outbox state.
- The dispatcher is enabled by default only in Docker runtime config and disabled in the test profile.
- This slice intentionally does not execute media processing workers, FFmpeg, OpenAI, subtitles, or TTS.

## 2026-06-26

Work:

- Added worker execution configuration and Docker/runtime environment wiring.
- Added durable job execution fields, retry count, and `job_timeline_events` through Flyway V3.
- Added job execution service, smoke pipeline stage, and RabbitMQ worker listener gated by `linguaframe.worker.execution-enabled`.
- Extended `GET /api/jobs/{jobId}` with execution metadata and ordered timeline events.
- Added `POST /api/jobs/{jobId}/retry` for failed jobs, including retry state reset and a new pending dispatch event.
- Updated README worker lifecycle documentation and plan status.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed before implementation with `Tests run: 39, Failures: 0, Errors: 0`; escalation was required because `RANDOM_PORT` tests bind localhost sockets.
- `mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed after adding worker execution properties.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_WORKER_EXECUTION_ENABLED=true`.
- `mvn -pl LinguaFrame test -Dtest=UploadIntakeSchemaTests,LocalizationJobRepositoryTests,JobTimelineEventRepositoryTests` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests` passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -Dtest=LocalizationJobWorkerTests` passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests` first failed because job reads omitted `retryCount` and failure fields, and `POST /api/jobs/{jobId}/retry` returned `404`; it passed after API implementation with `Tests run: 6, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -Dtest=JobTimelineEventRepositoryTests` first failed because SQL `NULL duration_ms` mapped to `0`; it passed after fixing `ResultSet#wasNull` ordering.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed after implementation with `Tests run: 54, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed and built `linguaframe-linguaframe-backend:latest`.
- `git diff --check` passed.

Notes:

- The worker lifecycle is observable and retryable, but still uses a deterministic smoke stage only.
- This slice intentionally does not run FFmpeg, OpenAI calls, subtitle generation, TTS, frontend UI, authentication, or Redis behavior.

## 2026-06-26

Work:

- Added Docker E2E demo scripts for successful worker execution and forced failure/retry.
- Added a non-secret smoke-stage failure toggle for local demo verification.
- Added RabbitMQ JSON message conversion so queued job records can be published and consumed in the live Docker stack.
- Changed the backend Docker image to copy the locally packaged Spring Boot jar, avoiding container-internal Maven dependency resolution during local demo builds.
- Documented the repeatable Docker demo workflow in README, the agent demo guide, and the smoke-test checklist.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed before implementation with `Tests run: 54, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests` first failed because `smokeStageFailureEnabled` getters/setters did not exist, then passed with `Tests run: 9, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RabbitJobQueueConfigurationTests` first failed because no Rabbit `MessageConverter` bean existed, then passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests,RabbitJobQueueConfigurationTests` passed in final verification with `Tests run: 11, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed in final verification with `Tests run: 57, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=false`.
- Initial `docker compose --env-file .env.example build linguaframe-backend` failed inside the Maven build container because Maven Central was unreachable for `spring-boot-dependencies:3.5.15`; after switching to local jar packaging, `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests` passed and `docker compose --env-file .env.example build linguaframe-backend` passed.
- `bash -n scripts/demo/lib/linguaframe-demo.sh`, `bash -n scripts/demo/docker-e2e-success.sh`, and `bash -n scripts/demo/docker-e2e-retry.sh` passed.
- `scripts/demo/docker-e2e-success.sh` passed against the live Docker stack and printed `status=COMPLETED` for job `00344608-8aa8-4ecd-9176-20a8c9c1664c`.
- Forced smoke-stage failure reached `status=FAILED` for job `379eaf6f-d143-45d3-9920-1a15d6ad9be8`; retry after restarting the backend with failure disabled returned `RETRYING` and then reached `status=COMPLETED` with `retryCount=1`.
- `docker compose --env-file .env.example down` stopped and removed live verification containers.

Notes:

- The demo scripts create tiny local sample files under `/tmp/linguaframe-demo`; generated samples are not committed.
- This slice still does not run FFmpeg, OpenAI, subtitle generation, TTS, frontend UI, authentication, or Redis behavior.

## 2026-06-26

Work:

- Added durable `job_artifacts` persistence for generated job outputs.
- Added object storage read support and a job artifact service for create, list, and download behavior.
- Added a worker summary artifact stage that writes `worker-summary.json` after the smoke worker stage succeeds.
- Added `GET /api/jobs/{jobId}/artifacts` and `GET /api/jobs/{jobId}/artifacts/{artifactId}/download`.
- Updated the Docker success demo to list artifacts and download the generated worker summary.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactRepositoryTests` first failed because artifact domain types and repository did not exist, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=JobArtifactServiceTests,MediaUploadServiceTests,MediaUploadControllerTests` first failed because `JobArtifactServiceImpl` had two unqualified constructors, then passed with `Tests run: 11, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests` first failed because `ARTIFACT_SUMMARY` and `WorkerSummaryArtifactPipelineStage` did not exist, then passed with `Tests run: 6, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobControllerTests` first failed because artifact endpoints were not routed, then passed with `Tests run: 9, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh` and `bash -n scripts/demo/docker-e2e-success.sh` passed.
- Sandboxed `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` failed because `RANDOM_PORT` tests could not bind localhost sockets; the same command passed with elevated local socket access with `Tests run: 65, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed and built `linguaframe-linguaframe-backend:latest`.
- `scripts/demo/docker-e2e-success.sh` passed against the live Docker stack for job `13ff4552-bd29-4f25-bc3c-1a57e2c80f78`, printed `status=COMPLETED`, printed `artifactCount=1`, and downloaded `/tmp/linguaframe-demo/worker-summary.json`.
- The downloaded worker summary JSON contained only safe generated metadata: job id, video id, target language, source object key, stage, and generated timestamp.
- `docker compose --env-file .env.example down` stopped and removed live verification containers.

Notes:

- This slice creates a downloadable worker summary artifact as infrastructure for future FFmpeg audio, subtitle, TTS, and generated video artifacts.
- This slice still does not run FFmpeg, OpenAI, subtitle generation, TTS, frontend UI, authentication, or Redis behavior.

## 2026-06-26

Work:

- Added FFmpeg runtime configuration and Docker image FFmpeg installation.
- Added a controlled `FfmpegAudioExtractionService` boundary with fixed command arguments and safe failure summaries.
- Added media work-directory management with per-job temporary directories and cleanup.
- Added an `AUDIO_EXTRACTION` worker stage between `WORKER_SMOKE` and `ARTIFACT_SUMMARY`.
- Added `EXTRACTED_AUDIO` artifacts named `audio.wav`.
- Updated the Docker success demo to generate a tiny synthetic media sample, download `audio.wav`, and download `worker-summary.json` by artifact type.
- Documented FFmpeg config, audio artifact expectations, and validation commands in README and agent test docs.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` first failed because `ffmpeg` properties did not exist, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=FfmpegAudioExtractionServiceTests` first failed because FFmpeg service types did not exist, then passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LocalizationJobExecutionServiceTests` first failed because `AudioExtractionPipelineStage`, `AUDIO_EXTRACTION`, and `EXTRACTED_AUDIO` did not exist, then passed with `Tests run: 7, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,FfmpegAudioExtractionServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests` passed after final interrupt cleanup coverage with `Tests run: 25, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh` and `bash -n scripts/demo/docker-e2e-success.sh` passed.
- `docker compose --env-file .env.example config` passed and rendered FFmpeg environment variables.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with elevated local socket access after final interrupt cleanup coverage with `Tests run: 71, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests` passed.
- Initial `docker compose --env-file .env.example build linguaframe-backend` failed because the container could not reach `ports.ubuntu.com` for `apt-get update`; the backend Dockerfile now uses a configurable `UBUNTU_PORTS_MIRROR` default that was reachable in local verification.
- `docker compose --env-file .env.example build linguaframe-backend` passed after the Dockerfile mirror update and installed `ffmpeg`.
- `scripts/demo/docker-e2e-success.sh` passed against the live Docker stack for job `ca52d288-68ea-44bb-bb7f-92b5467c4d64`, printed `status=COMPLETED`, printed `artifactCount=2`, and downloaded `/tmp/linguaframe-demo/audio.wav` plus `/tmp/linguaframe-demo/worker-summary.json`.
- `file /tmp/linguaframe-demo/audio.wav` reported `RIFF (little-endian) data, WAVE audio, Microsoft PCM, 16 bit, mono 16000 Hz`.
- `python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json` parsed successfully.
- `docker compose --env-file .env.example down` stopped and removed live verification containers.

Notes:

- This slice runs FFmpeg audio extraction in the Docker worker path, but still does not run OpenAI transcription, subtitle generation, translation, TTS, frontend UI, authentication, or Redis behavior.

## 2026-06-26

Work:

- Added durable `transcript_segments` persistence with ordered transcript segment lookup by job.
- Added a transcript service, deterministic demo transcription provider, and transcription runtime configuration.
- Added subtitle export support for transcript JSON, SRT, and WebVTT.
- Added a `TRANSCRIPT_SUBTITLE_EXPORT` worker stage after FFmpeg audio extraction.
- Added `TRANSCRIPT_JSON`, `SUBTITLE_SRT`, and `SUBTITLE_VTT` job artifacts.
- Added `GET /api/jobs/{jobId}/transcript` for transcript preview.
- Updated Docker demo scripts and docs to print transcript preview and download five artifacts.

Validation:

- `mvn -pl LinguaFrame -Dtest=TranscriptSegmentRepositoryTests test` first failed because transcript repository/domain classes did not exist, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=TranscriptServiceTests,LinguaFramePropertiesTests test` first failed because transcript service/domain/config classes did not exist, then passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=SubtitleExportServiceTests test` first failed because subtitle export service classes did not exist, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests#transcriptSubtitleStageCreatesArtifactsAfterAudioExtraction test` first failed because the new worker stage, timeline stage, and artifact types did not exist; after implementation it exposed an unqualified `TranscriptServiceImpl` constructor, which was fixed by matching the existing `@Autowired` production-constructor pattern. The test then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#returnsTranscriptSegmentsForLocalizationJob test` first failed with HTTP 404 because the endpoint was not mapped, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh`, `bash -n scripts/demo/docker-e2e-success.sh`, and `bash -n scripts/demo/docker-e2e-retry.sh` passed.
- `mvn -pl LinguaFrame -Dtest=TranscriptSegmentRepositoryTests,TranscriptServiceTests,SubtitleExportServiceTests,LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 30, Failures: 0, Errors: 0`.
- Sandboxed `mvn test` failed because `RANDOM_PORT` tests could not bind local ports. The same command passed with local socket access with `Tests run: 80, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_TRANSCRIPTION_ENABLED=true` plus `LINGUAFRAME_TRANSCRIPTION_PROVIDER=demo`.
- `mvn -pl LinguaFrame -am package -DskipTests` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed.
- `scripts/demo/docker-e2e-success.sh` passed against the live Docker stack for job `2deafddf-a64f-44bc-aace-e30ca888fe7d`, printed `status=COMPLETED`, printed `artifactCount=5`, and downloaded `/tmp/linguaframe-demo/audio.wav`, `/tmp/linguaframe-demo/transcript.json`, `/tmp/linguaframe-demo/subtitles.srt`, `/tmp/linguaframe-demo/subtitles.vtt`, and `/tmp/linguaframe-demo/worker-summary.json`.
- `file /tmp/linguaframe-demo/audio.wav` reported `RIFF (little-endian) data, WAVE audio, Microsoft PCM, 16 bit, mono 16000 Hz`.
- `python3 -m json.tool /tmp/linguaframe-demo/transcript.json` and `python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json` parsed successfully.
- `sed -n '1,80p' /tmp/linguaframe-demo/subtitles.srt` showed comma millisecond timestamps.
- `sed -n '1,80p' /tmp/linguaframe-demo/subtitles.vtt` showed `WEBVTT` and dot millisecond timestamps.
- `docker compose --env-file .env.example down` stopped and removed live verification containers.

Notes:

- This slice does not call OpenAI. It deliberately uses deterministic transcript output so the Docker demo can verify the transcript/subtitle pipeline without provider credentials.
- Translation, TTS, subtitle burn-in, frontend UI, authentication, and cost tracking remain later slices.

## 2026-06-26

Work:

- Added durable `subtitle_segments` persistence keyed by job and target language.
- Added translation runtime configuration, `TranslationProvider`, deterministic demo translation, and subtitle replacement/listing service.
- Added target subtitle JSON/SRT/WebVTT export support.
- Added `TARGET_SUBTITLE_EXPORT` worker stage after `TRANSCRIPT_SUBTITLE_EXPORT`.
- Added `TARGET_SUBTITLE_JSON`, `TARGET_SUBTITLE_SRT`, and `TARGET_SUBTITLE_VTT` artifacts.
- Added `GET /api/jobs/{jobId}/subtitles/{language}` for target subtitle preview.
- Updated Docker demo scripts and docs to print target subtitle preview and download eight artifacts.

Validation:

- `mvn -pl LinguaFrame -Dtest=SubtitleSegmentRepositoryTests test` first failed because `SubtitleSegmentRecord` and `SubtitleSegmentRepository` did not exist, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,TranslationProviderTests,SubtitleServiceTests test` first failed because translation properties, provider, BO/VO, and service types did not exist, then passed with `Tests run: 11, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=SubtitleExportServiceTests test` first failed because target subtitle export methods did not exist; after updating the existing fake export service it passed with `Tests run: 6, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests#targetSubtitleStageCreatesArtifactsAfterTranscriptExport test` first failed because the target subtitle stage and enum values did not exist, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test` passed with `Tests run: 9, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests#returnsTargetSubtitleSegmentsForLocalizationJob test` first failed with HTTP 404 because the endpoint was not mapped, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` passed with `Tests run: 11, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh`, `bash -n scripts/demo/docker-e2e-success.sh`, and `bash -n scripts/demo/docker-e2e-retry.sh` passed.
- `mvn -pl LinguaFrame -Dtest=SubtitleSegmentRepositoryTests,SubtitleServiceTests,TranslationProviderTests,SubtitleExportServiceTests,LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 38, Failures: 0, Errors: 0`.
- Sandboxed `mvn test` failed because `RANDOM_PORT` tests could not bind local ports (`java.net.SocketException: Operation not permitted`). The same command passed with local socket access with `Tests run: 91, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_TRANSLATION_ENABLED=true` plus `LINGUAFRAME_TRANSLATION_PROVIDER=demo`.
- `mvn -pl LinguaFrame -am package -DskipTests` passed.
- `docker compose --env-file .env.example build linguaframe-backend` passed.
- `scripts/demo/docker-e2e-success.sh` passed against the live Docker stack for job `85a2cbe3-1241-4332-a24e-0f872a4ec25e`, printed `status=COMPLETED`, included `TARGET_SUBTITLE_EXPORT` in the timeline, printed `artifactCount=8`, printed target subtitle preview JSON, and downloaded `/tmp/linguaframe-demo/target-subtitles.json`, `/tmp/linguaframe-demo/target-subtitles.srt`, and `/tmp/linguaframe-demo/target-subtitles.vtt`.
- `file /tmp/linguaframe-demo/audio.wav` reported `RIFF (little-endian) data, WAVE audio, Microsoft PCM, 16 bit, mono 16000 Hz`.
- `python3 -m json.tool /tmp/linguaframe-demo/transcript.json`, `python3 -m json.tool /tmp/linguaframe-demo/target-subtitles.json`, and `python3 -m json.tool /tmp/linguaframe-demo/worker-summary.json` parsed successfully.
- `sed -n '1,80p' /tmp/linguaframe-demo/target-subtitles.srt` showed comma millisecond timestamps and deterministic `zh-CN` text.
- `sed -n '1,80p' /tmp/linguaframe-demo/target-subtitles.vtt` showed `WEBVTT`, dot millisecond timestamps, and deterministic `zh-CN` text.
- `docker compose --env-file .env.example down` stopped and removed live verification containers.

Notes:

- This slice does not call OpenAI. It deliberately uses deterministic demo translation so the Docker demo can verify target-language subtitle storage, preview, and artifact export without API keys or paid calls.
- Real OpenAI translation, TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, and cost tracking remain later slices.

## 2026-06-26

Work:

- Added OpenAI translation runtime configuration under `linguaframe.translation.openai` and environment pass-through for Docker.
- Kept `LINGUAFRAME_TRANSLATION_PROVIDER=demo` as the default demo path and made the demo provider conditional.
- Added `OpenAiTranslationProvider` behind the existing `TranslationProvider` interface.
- Implemented mocked Responses API request/response handling, structured JSON output parsing, segment validation, sanitized HTTP errors, and timing preservation.
- Documented safe local `.env` setup for optional live OpenAI translation.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` first failed because `Translation#getOpenai()` did not exist, then passed after adding OpenAI translation properties.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,TranslationProviderTests test` passed before renaming the demo provider test class, with `Tests run: 10, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests test` first failed because `OpenAiTranslationProvider` did not exist, then passed with `Tests run: 8, Failures: 0, Errors: 0`. A later production-timeout refinement briefly exposed a missing test Authorization header in the package-private mock constructor path; after adding the header to the test `RestClient`, the same command passed again with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,DemoTranslationProviderTests,OpenAiTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 40, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 100, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_TRANSLATION_PROVIDER: demo`, `OPENAI_API_KEY: ""`, and `OPENAI_TRANSLATION_MODEL: ""`.

Notes:

- Official OpenAI docs URLs returned `Forbidden` from this terminal, and the OpenAI developer docs MCP was added globally but was not available until a future Codex restart. The implementation avoids hardcoding a model, keeps the model user-configured through `OPENAI_TRANSLATION_MODEL`, and tests the provider through mocked HTTP only.
- This slice does not add OpenAI transcription, TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, cost tracking, or translation quality evaluation.

## 2026-06-26

Work:

- Added OpenAI transcription runtime configuration under `linguaframe.transcription.openai` and environment pass-through for Docker.
- Kept `LINGUAFRAME_TRANSCRIPTION_PROVIDER=demo` as the default demo path and made the demo transcription provider conditional.
- Added `OpenAiTranscriptionProvider` behind the existing `TranscriptionProvider` interface.
- Implemented mocked Audio Transcriptions API multipart request handling, verbose JSON segment parsing, timestamp conversion, segment validation, and sanitized HTTP errors.
- Added Spring context coverage for `linguaframe.transcription.provider=openai`.
- Updated demo scripts so a user-provided `LINGUAFRAME_DEMO_SAMPLE_PATH` is not overwritten.
- Documented safe local `.env` setup for optional live OpenAI transcription with a real speech sample.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` first failed because `Transcription#getOpenai()` did not exist, then passed after adding OpenAI transcription properties with `Tests run: 9, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests test` first failed because `OpenAiTranscriptionProvider` did not exist, then passed with `Tests run: 7, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionContextTests test` passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,OpenAiTranscriptionContextTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,DemoTranslationProviderTests,SubtitleServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 49, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_TRANSCRIPTION_PROVIDER: demo`, `OPENAI_TRANSCRIPTION_MODEL: ""`, and `OPENAI_TRANSCRIPTION_TIMEOUT_SECONDS: "120"`.

Notes:

- Official OpenAI docs were checked for the audio transcription endpoint shape before implementation. The implementation targets multipart `POST /v1/audio/transcriptions` with `response_format=verbose_json` and segment timestamp granularity.
- This slice does not add TTS, subtitle burn-in, frontend UI, authentication, Redis behavior, cost tracking, model-call audit tables, or translation quality evaluation.

## 2026-06-26

Work:

- Added TTS runtime configuration under `linguaframe.tts` and Docker environment pass-through.
- Added `TtsProvider`, deterministic demo TTS, and OpenAI TTS provider implementations.
- Added `DUBBING_AUDIO_GENERATION` worker stage after target subtitle export.
- Added `DUBBING_AUDIO` artifacts named `dubbing-audio.mp3` with content type `audio/mpeg`.
- Documented default demo behavior, OpenAI TTS opt-in variables, and the one-continuous-MP3 MVP boundary.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` first failed because `LinguaFrameProperties#getTts()` did not exist, then passed after adding TTS properties with `Tests run: 10, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTtsProviderTests,OpenAiTtsContextTests test` first failed because TTS provider types did not exist, then exposed an empty-response null body edge case, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests test` first failed because `DubbingAudioGenerationPipelineStage` did not exist; after correcting the test to use existing `PROCESSING` status and implementing the stage, `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 14, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,OpenAiTtsProviderTests,OpenAiTtsContextTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 29, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh` and `bash -n scripts/demo/docker-e2e-success.sh` passed after adding optional `DUBBING_AUDIO` artifact download support.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_TTS_ENABLED: "false"`, `LINGUAFRAME_TTS_PROVIDER: demo`, empty `OPENAI_TTS_MODEL`, empty `OPENAI_TTS_VOICE`, and `OPENAI_TTS_TIMEOUT_SECONDS: "120"`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 121, Failures: 0, Errors: 0`.

Notes:

- Official OpenAI docs were checked for the audio speech endpoint shape before implementation. The implementation targets `POST /v1/audio/speech` with `model`, `voice`, `input`, and `response_format=mp3`.
- This slice does not add lip sync, audio/video mixing, subtitle burn-in, frontend UI, authentication, Redis behavior, cost tracking, model-call audit tables, or translation quality evaluation.

## 2026-06-26

Work:

- Added FFmpeg subtitle burn-in runtime configuration under `linguaframe.ffmpeg`.
- Added `FfmpegSubtitleBurnInService` for FFmpeg-backed MP4 rendering with safe errors and timeout handling.
- Added `SUBTITLE_BURN_IN` worker stage after TTS and before artifact summary.
- Added `BURNED_VIDEO` artifacts named `burned-video.mp4` with content type `video/mp4`.
- Updated the Docker success demo to generate a real short MP4 sample and download the burned video artifact.
- Documented default Docker burn-in behavior and the MVP boundary: no TTS audio mixing, no lip sync, and no subtitle style editor.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` first failed because `LinguaFrameProperties.Ffmpeg#isBurnInEnabled()` and `getBurnInTimeoutSeconds()` did not exist, then passed after adding burn-in properties with `Tests run: 10, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=FfmpegSubtitleBurnInServiceTests test` first failed because burn-in BO/service types did not exist, then passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=SubtitleBurnInPipelineStageTests test` first failed because `SubtitleBurnInPipelineStage` did not exist, then passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 14, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,FfmpegSubtitleBurnInServiceTests,SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 28, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh` and `bash -n scripts/demo/docker-e2e-success.sh` passed.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED: "true"` plus `LINGUAFRAME_FFMPEG_BURN_IN_TIMEOUT_SECONDS: "180"`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 129, Failures: 0, Errors: 0`.

Notes:

- This slice does not add TTS audio replacement, lip sync, subtitle style editing, frontend UI, authentication, Redis behavior, cost tracking, model-call audit tables, or translation quality evaluation.

## 2026-06-26

Work:

- Added durable `model_call_records` persistence for provider calls with operation, provider, model, prompt version, status, latency, usage units, estimated cost, safe error summary, and timestamp.
- Added configurable cost estimate rates under `linguaframe.cost` and Docker/.env pass-through variables.
- Instrumented demo and OpenAI transcription, translation, and TTS providers to record successful and failed model-call audits.
- Extended `GET /api/jobs/{jobId}` with `usageSummary` and `modelCalls`.
- Updated the Docker success demo to print model-call count, failed count, estimated cost, and each provider call.
- Documented cost configuration, job detail usage fields, and model-call demo output.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,ModelCallRepositoryTests,ModelCallAuditServiceTests test` passed with `Tests run: 18, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests test` passed with `Tests run: 23, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 22, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,ModelCallRepositoryTests,ModelCallAuditServiceTests,DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests,OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests,LocalizationJobControllerTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 63, Failures: 0, Errors: 0`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh` and `bash -n scripts/demo/docker-e2e-success.sh` passed.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_COST_ENABLED: "true"`, `LINGUAFRAME_COST_TRANSCRIPTION_USD_PER_MINUTE: "0"`, `LINGUAFRAME_COST_TRANSLATION_INPUT_USD_PER_1M_TOKENS: "0"`, `LINGUAFRAME_COST_TRANSLATION_OUTPUT_USD_PER_1M_TOKENS: "0"`, and `LINGUAFRAME_COST_TTS_USD_PER_1M_CHARS: "0"`.

Notes:

- Cost values are local estimates and default to zero because provider pricing changes. They are not billing-source-of-truth values.
- This slice does not add frontend UI, budget enforcement, translation quality evaluation, prompt-template storage, or duplicate-work caching.

Final verification:

- `mvn -pl LinguaFrame test` passed on branch `model-call-audit-cost-mvp` with `Tests run: 140, Failures: 0, Errors: 0`.
- Merged `model-call-audit-cost-mvp` back to `main` with merge commit `3a1757e`.
- `mvn -pl LinguaFrame test` passed on `main` after merge with `Tests run: 140, Failures: 0, Errors: 0`.

## 2026-06-26

Work:

- Added a React + Vite + TypeScript browser demo under `frontend/`.
- Added typed frontend API functions for upload, job detail, retry, transcript, subtitles, artifacts, and artifact download URLs.
- Added browser-local recent job storage.
- Built the demo work surface for upload, manual job open, polling, status/timeline, usage summary, model-call records, failed-job retry, transcript/subtitle previews, artifact downloads, and audio/video previews.
- Added a Docker Compose `linguaframe-frontend` service with same-origin `/api` proxying to the backend container.

Validation:

- `mvn -pl LinguaFrame test` passed before implementation with `Tests run: 140, Failures: 0, Errors: 0`.
- `npm run test:run -- linguaframeApi` first failed because `frontend/src/api/linguaframeApi.ts` did not exist, then passed with `Tests run: 5`.
- `npm run test:run -- recentJobs` first failed because `frontend/src/domain/recentJobs.ts` did not exist, then passed with `Tests run: 4`.
- `npm run test:run -- App` first failed because the placeholder App had no upload, job-open, polling, retry, preview, or artifact UI, then passed with `Tests run: 7`.
- `npm run test:run` passed with `Tests run: 16`.
- `npm run build` passed.
- `docker compose --env-file .env.example config` passed and rendered `linguaframe-frontend`, `LINGUAFRAME_API_PROXY_TARGET=http://linguaframe-backend:8080`, and published frontend port `5173`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 140, Failures: 0, Errors: 0`.
- Browser smoke opened `http://localhost:5173/` and verified the page title, upload file input, target-language input, manual job id opener, upload/open buttons, recent jobs area, and empty selected-job state.

Notes:

- The frontend uses local storage for recent uploaded/opened jobs because the backend does not yet expose `GET /api/jobs`.
- The first browser screen is the actual demo workspace, not a marketing page.

Final verification:

- `npm run test:run` passed with `Tests run: 16`.
- `npm run build` passed.
- `docker compose --env-file .env.example config` passed and rendered `linguaframe-frontend`.
- `mvn -pl LinguaFrame test` passed before merge.
- Merged `react-demo-experience-mvp` back to `main` with merge commit `5922df6`.
- `npm run test:run`, `npm run build`, `docker compose --env-file .env.example config`, and `mvn -pl LinguaFrame test -q` passed again on `main` after merge.

## 2026-06-26

Work:

- Added lightweight job summary records for server-backed job history.
- Added `GET /api/jobs` with newest-first ordering, optional `status` filtering, normalized `limit`/`offset`, total count, source filename, retry/failure metadata, and estimated cost summary.
- Added frontend `listJobs` API support and typed job history list data.
- Added a React `Job history` panel with status filtering, refresh, open-job behavior, and concise history-load failure handling.
- Kept browser-local recent jobs and manual job id opening as fallback conveniences.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test` first failed because summary query methods did not exist, then passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because `GET /api/jobs` did not exist, then passed with `Tests run: 16, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests,LocalizationJobControllerTests test` passed with `Tests run: 24, Failures: 0, Errors: 0`.
- `npm run test:run -- linguaframeApi` first failed because `listJobs` did not exist, then passed with `Tests run: 8`.
- `npm run test:run -- App` first failed because the App had no server-backed history UI, then passed with `Tests run: 11`.
- `npm run test:run` passed with `Tests run: 23`.
- `npm run build` passed.
- `docker compose --env-file .env.example config` passed and rendered `linguaframe-frontend`.
- `mvn -pl LinguaFrame test -q` passed; surefire reports summarized `Tests run: 156, Failures: 0, Errors: 0, Skipped: 0`.

Notes:

- The job list is global for the local self-hosted demo because there is no authentication or owner model yet.
- This slice does not add user accounts, ownership filtering, deletion, cancellation, WebSocket/SSE progress, or admin analytics.

Final verification:

- Merged `job-history-list-mvp` back to `main` with merge commit `ce705ee`.
- `mvn -pl LinguaFrame test -q` initially failed in the sandbox because embedded Tomcat could not bind a local port (`java.net.SocketException: Operation not permitted`); rerunning with normal permissions passed and surefire reports summarized `Tests run: 156, Failures: 0, Errors: 0, Skipped: 0`.
- `npm run test:run` passed with `Tests run: 23`.
- `npm run build` passed.
- `docker compose --env-file .env.example config` passed and rendered `linguaframe-backend`, `linguaframe-frontend`, `mysql`, `rabbitmq`, and `minio`.

## 2026-06-27

Work:

- Added durable `quality_evaluations` persistence for translation quality scores, dimension scores, issues, suggested fixes, status, safe errors, and timestamps.
- Added `QualityEvaluationService`, deterministic demo evaluation, and opt-in OpenAI Responses API evaluation behind `QualityEvaluationProvider`.
- Added `TRANSLATION_QUALITY_EVALUATION` as a non-blocking worker stage after target subtitle export.
- Extended `GET /api/jobs/{jobId}` with latest `qualityEvaluation` data.
- Added a React `Quality evaluation` panel with score, verdict, dimensions, issues, suggested fixes, empty state, and failed-evaluation warning support.
- Documented optional evaluation configuration and the non-blocking behavior.

Validation:

- `mvn -pl LinguaFrame -Dtest=QualityEvaluationRepositoryTests test` first failed because `QualityEvaluationRecord`, `QualityEvaluationStatus`, and `QualityEvaluationRepository` did not exist, then passed with `Tests run: 1, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,DemoQualityEvaluationProviderTests,QualityEvaluationServiceTests,OpenAiQualityEvaluationProviderTests,ModelCallAuditServiceTests test` first failed because evaluation configuration, provider/service types, enum values, and model-call operation support did not exist, then passed with `Tests run: 25, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` first failed because `QualityEvaluationPipelineStage` did not exist, then passed with `Tests run: 28, Failures: 0, Errors: 0`.
- `npm run test:run -- App` first failed because the selected job detail did not expose a `Quality evaluation` region, then passed with `Tests run: 11`.
- `npm run test:run` passed with `Tests run: 23`.

Notes:

- Evaluation defaults to disabled and `demo` provider in `.env.example`, so default Docker demos remain reproducible and do not require paid API calls.
- OpenAI evaluation is explicitly enabled with `LINGUAFRAME_EVALUATION_ENABLED=true`, `LINGUAFRAME_EVALUATION_PROVIDER=openai`, and `OPENAI_EVALUATION_MODEL`.

Final verification before merge:

- `mvn -pl LinguaFrame -Dtest=QualityEvaluationRepositoryTests,ModelCallAuditServiceTests test` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 28, Failures: 0, Errors: 0`.
- `npm run test:run` passed with `Tests run: 23`.
- `npm run build` passed.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_EVALUATION_ENABLED: "false"`, `LINGUAFRAME_EVALUATION_PROVIDER: demo`, `OPENAI_EVALUATION_MODEL: ""`, and `OPENAI_EVALUATION_TIMEOUT_SECONDS: "60"`.
- `mvn -pl LinguaFrame test -q` passed with local socket permissions; surefire reports summarized `Tests run: 167, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check` passed.

Post-merge verification:

- Merged `translation-quality-evaluation-mvp` back to `main` with merge commit `7d42b51`.
- `npm run test:run` passed on `main` with `Tests run: 23`.
- `npm run build` passed on `main`.
- `docker compose --env-file .env.example config` passed on `main` and rendered the evaluation environment variables.
- `mvn -pl LinguaFrame test -q` passed on `main` with local socket permissions; surefire reports summarized `Tests run: 167, Failures: 0, Errors: 0, Skipped: 0`.

## 2026-06-27

Work:

- Added `scripts/demo/docker-e2e-tears-of-steel-full.sh` for `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`.
- Added `docs/product/demo-references.md` with Tears of Steel source, license reference, attribution, and local-file handling notes.
- Documented the full Tears of Steel Docker demo path and the current subtitle burn-in timeout caveat.

Validation:

- `rg -n "demo-references|docker-e2e-tears-of-steel-full|tos_casting|Creative Commons" README.md docs/product/demo-references.md` found the reference doc, script name, local file note, and license attribution.
- `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh` passed.
- `scripts/demo/docker-e2e-tears-of-steel-full.sh --help` passed and listed all supported full-demo environment variables.

Notes:

- Full runtime validation requires the Docker backend and the local MP4.
- Burned-video output is optional until full-video FFmpeg burn-in is optimized.

Post-merge verification:

- Merged `full-video-demo-script` back to `main` with merge commit `82c2e1f`.
- `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh` passed on `main`.
- `scripts/demo/docker-e2e-tears-of-steel-full.sh --help` passed on `main`.
- `rg -n "demo-references|docker-e2e-tears-of-steel-full|Full Tears of Steel Demo|Creative Commons Attribution 3.0" README.md docs/product/demo-references.md docs/agent/docker-e2e-demo.md docs/progress/execution-log.md` passed on `main`.

## 2026-06-27

Work:

- Enforced real upload duration validation with an FFprobe-backed duration probe.
- Changed the default media duration limit to 300 seconds.
- Persisted detected video duration metadata on `videos.duration_seconds`.
- Exposed `durationSeconds` in validation, upload creation, and upload detail responses.
- Documented that LinguaFrame rejects over-limit videos instead of clipping accepted videos.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests test` first failed because the default was still `120`, then passed after changing the default to `300`.
- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test` first failed because the duration probe types did not exist, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=UploadIntakeSchemaTests test` first failed because `videos.duration_seconds` did not exist, then passed after adding migration `V9__add_video_duration_seconds.sql`.
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadControllerTests,MediaUploadServiceTests test` passed with `Tests run: 17, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=UploadIntakeSchemaTests,VideoRepositoryTests,MediaUploadServiceTests,MediaUploadControllerTests test` passed with `Tests run: 13, Failures: 0, Errors: 0`.
- `rg -n "300 seconds|5 minutes|durationSeconds|DURATION_TOO_LONG|complete file|processed in full|FFprobe" README.md docs/agent/docker-e2e-demo.md docs/product/spec.md docs/progress/execution-log.md` passed.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS: "300"`.
- `mvn -pl LinguaFrame test -q` passed; surefire reports summarized `Tests run: 176, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check` passed.

Notes:

- Duration validation is an intake gate only. Accepted uploads keep the original uploaded bytes and are processed in full.
- The migration uses a nullable column so existing local videos remain valid.

Post-merge verification:

- Merged `upload-duration-limit-mvp` back to `main` with merge commit `005b1e3`.
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadControllerTests,MediaUploadServiceTests,UploadIntakeSchemaTests,VideoRepositoryTests,FfprobeMediaDurationProbeServiceTests,LinguaFramePropertiesTests test` passed on `main` with `Tests run: 37, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed on `main` and rendered `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS: "300"`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added `UNREADABLE_MEDIA` validation for supported-content-type files whose duration cannot be inspected.
- Converted FFprobe unreadable-media failures into structured upload validation responses without exposing FFprobe stderr.
- Confirmed unreadable uploads do not reach object storage or job creation.

Validation:

- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests test` passed with `Tests run: 7, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=MediaUploadValidationServiceTests,MediaUploadServiceTests test` passed with `Tests run: 13, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=MediaUploadControllerTests test` passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -q` first hit the local sandbox socket restriction while starting embedded Tomcat, then passed with local socket permissions and surefire reports summarized `Tests run: 182, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS: "300"`.
- `rg -n "UNREADABLE_MEDIA|unreadable media|could not be inspected|before storage|FFprobe stderr" README.md docs/product/spec.md docs/progress/execution-log.md docs/plans/026-invalid-media-upload-validation.md` passed.
- `git diff --check` passed.

Notes:

- Runtime FFprobe timeout and process I/O failures remain server errors; unreadable media metadata is treated as a user-facing upload validation failure.

Post-merge verification:

- Merged `invalid-media-upload-validation` back to `main` with merge commit `bc69a2e`.
- `mvn -pl LinguaFrame -Dtest=FfprobeMediaDurationProbeServiceTests,MediaUploadValidationServiceTests,MediaUploadServiceTests,MediaUploadControllerTests test` passed on `main` with `Tests run: 28, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test -q` passed on `main` with local socket permissions; surefire reports summarized `Tests run: 182, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config` passed on `main` and rendered `LINGUAFRAME_MEDIA_MAX_DURATION_SECONDS: "300"`.
- `rg -n "UNREADABLE_MEDIA|unreadable media|could not be inspected|before storage|FFprobe stderr" README.md docs/product/spec.md docs/progress/execution-log.md docs/plans/026-invalid-media-upload-validation.md` passed on `main`.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added a read-only prompt template domain and registry for OpenAI subtitle translation and translation quality evaluation.
- Updated OpenAI translation and quality evaluation providers to read system prompts and prompt versions from the registry.
- Added `GET /api/prompt-templates` for active prompt template inspection.
- Added a React `Prompt templates` panel and frontend API support.
- Documented the read-only in-code template MVP boundary.

Validation:

- `mvn -pl LinguaFrame -Dtest=PromptTemplateRegistryTests test` first failed because prompt template registry types did not exist, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests,OpenAiQualityEvaluationProviderTests test` first failed because providers did not accept the registry, then passed with `Tests run: 11, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=PromptTemplateControllerTests,OpenApiDocumentationTests test` first failed because `/api/prompt-templates` returned `404`, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because `listPromptTemplates` and the prompt-template panel did not exist, then passed with `Tests run: 28`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 203, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 32`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config` passed and rendered the current demo stack configuration.
- `git diff --check` passed.

Notes:

- This slice does not add prompt editing, A/B testing, database-backed prompt history, or automatic prompt optimization.

## 2026-06-27

Work:

- Added `GET /api/jobs/{jobId}/events` as a Server-Sent Events job snapshot stream.
- Updated the React demo to use EventSource for active selected jobs with polling fallback.
- Refreshed previews and history when live events move a selected job to a terminal state.
- Documented the SSE endpoint and the local-demo polling fallback boundary.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because `/api/jobs/{jobId}/events` did not exist, then passed after wiring SSE with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi` first failed because `jobEventsUrl` was missing, then passed with `Tests run: 10`.
- `cd frontend && npm run test:run -- App` first failed because no EventSource subscription existed, then exposed a jsdom `EventSource` constructor edge case, then passed after adding a safer capability check with `Tests run: 15`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 25`.
- `cd frontend && npm run test:run` passed with `Tests run: 29`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config` passed.
- `mvn -pl LinguaFrame test -q` passed with local socket permissions; surefire reports summarized `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check` passed.

Notes:

- This is snapshot-based SSE for the local demo. It does not add Redis pub/sub or a cross-process event bus.

Post-merge verification:

- Merged `live-job-progress-sse` back to `main` with merge commit `6a7c742`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` passed on `main` with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 29`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config` passed on `main`.
- `mvn -pl LinguaFrame test -q` passed on `main` with local socket permissions; surefire reports summarized `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added `POST /api/jobs/{jobId}/cancel` for queued, retrying, and processing jobs.
- Added an atomic repository cancellation transition that marks jobs `CANCELLED`, clears failure metadata, and stores the cancellation timestamp in `completedAt`.
- Added `LocalizationJobCancellationService` with timeline audit events and conflict handling for terminal jobs.
- Updated worker execution to observe soft cancellation before and after pipeline stages so cancelled jobs stop before the next durable stage.
- Added frontend API and Job Detail Cancel action for active jobs while hiding it for terminal jobs.
- Documented cancellation behavior in README and product/frontend docs.

Validation:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests test` first failed because `markCancelled` and `isCancelled` did not exist, then passed with `Tests run: 10, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobCancellationServiceTests test` first failed because the cancellation service did not exist, then passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because `/api/jobs/{jobId}/cancel` returned `404`, then passed after wiring the route.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests test` first failed because mid-processing cancellation still completed the job, then passed with `Tests run: 13, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi` first failed because `cancelJob` was missing, then passed with `Tests run: 9`.
- `cd frontend && npm run test:run -- App` first failed because the Cancel button was missing, then passed after the UI implementation.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests,LocalizationJobCancellationServiceTests test` passed with `Tests run: 12, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 32, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- App linguaframeApi` passed with `Tests run: 22`.
- `cd frontend && npm run test:run` passed with `Tests run: 26`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config` passed and rendered the current demo stack configuration.
- `mvn -pl LinguaFrame test -q` passed with local socket permissions; surefire reports summarized `Tests run: 190, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check` passed.

Notes:

- Cancellation is soft. This slice does not interrupt an already-running FFmpeg process or OpenAI HTTP request; the worker stops before starting the next stage.
- Cancellation keeps existing videos, artifacts, timeline events, dispatch events, and model-call records.
- A Python 3.14 helper script for parsing surefire XML hit a local `pyexpat` dynamic-library issue; surefire text reports were used for the final test summary instead.

Post-merge verification:

- Merged `job-cancellation-mvp` back to `main` with merge commit `ea2da53`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobRepositoryTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed on `main` with `Tests run: 44, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 26`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config` passed on `main`.
- `mvn -pl LinguaFrame test -q` passed on `main` with local socket permissions; surefire reports summarized `Tests run: 190, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added opt-in per-job cost budget guard configuration with Docker and `.env.example` pass-through.
- Added `CostBudgetGuardService` and `CostBudgetExceededException` using recorded `usageSummary.estimatedCostUsd` as the budget source.
- Guarded transcription, translation, TTS, and quality evaluation pipeline stages before provider calls.
- Documented budget guard behavior and the recorded-cost MVP boundary.

Validation:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,CostBudgetGuardServiceTests test` first failed because budget properties, guard service, and budget exception did not exist, then passed with `Tests run: 17, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests test` first failed because `DubbingAudioGenerationPipelineStage` did not accept a `CostBudgetGuardService`, then passed after guard wiring.
- `mvn -pl LinguaFrame -Dtest=CostBudgetedPipelineStageTests test` passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,CostBudgetGuardServiceTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 36, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,CostBudgetGuardServiceTests,CostBudgetedPipelineStageTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 39, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config` passed and rendered `LINGUAFRAME_COST_BUDGET_GUARD_ENABLED: "false"` and `LINGUAFRAME_COST_MAX_JOB_COST_USD: "0"`.
- `git diff --check` passed.

Notes:

- The guard checks accumulated recorded estimated cost before the next AI stage. It intentionally does not forecast the next provider call cost.

Post-merge verification:

- Merged `cost-budget-guard-mvp` back to `main` with merge commit `5ebaaaf`.
- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,CostBudgetGuardServiceTests,CostBudgetedPipelineStageTests,DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed on `main` with `Tests run: 39, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config` passed on `main` and rendered `LINGUAFRAME_COST_BUDGET_GUARD_ENABLED: "false"` plus `LINGUAFRAME_COST_MAX_JOB_COST_USD: "0"`.
- `git diff --check HEAD` passed on `main`.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.

## 2026-06-27

Work:

- Added `content_sha256` to persisted job artifact metadata.
- Computed lowercase SHA-256 fingerprints in `JobArtifactServiceImpl` from the exact artifact bytes passed to object storage.
- Exposed `contentSha256` in artifact API responses.
- Updated the React artifact table to show a short SHA-256 prefix with the full hash available on hover.
- Documented artifact hashes as reproducibility and future cache-foundation metadata, not cache hits.

Validation:

- `mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,JobArtifactServiceTests,LocalizationJobControllerTests test` first failed because `contentSha256` was not part of artifact records or VOs, then passed with `Tests run: 24, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the artifact table did not render the short hash, then passed with `Tests run: 25`.
- `cd frontend && npm run test:run` passed with `Tests run: 29`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `mvn -pl LinguaFrame test` passed with `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config` passed and rendered the current demo stack configuration.
- `git diff --check` passed.

Notes:

- This slice does not implement cache result reuse, cache-hit audit events, or provider-call skipping.

Post-merge verification:

- Merged `artifact-content-hash-visibility-mvp` back to `main` with merge commit `0780dbe`.
- `mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,JobArtifactServiceTests,LocalizationJobControllerTests test` passed on `main` with `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 25`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 29`.
- `cd frontend && npm run build` passed on `main`.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config` passed on `main` and rendered the current demo stack configuration.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added artifact cache metadata to job artifacts with `cacheHit` and `sourceArtifactId`.
- Added reusable artifact lookup scoped to the same source video, target language, and artifact type.
- Added `ArtifactCacheService` and reused artifact creation that creates a new artifact row without rewriting object storage bytes.
- Wired cache reuse into audio extraction, dubbing audio, and subtitle burn-in stages.
- Added `CACHE_HIT` timeline events, job detail `cacheSummary`, and React cache-hit display.
- Documented that worker summaries are regenerated and provider prompt/response caching remains a later slice.

Validation:

- `mvn -pl LinguaFrame -Dtest=JobArtifactRepositoryTests,LocalizationJobControllerTests test` first failed because cache metadata fields and reusable lookup did not exist, then passed with `Tests run: 22, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=ArtifactCacheServiceTests,JobArtifactServiceTests test` first failed because the cache service and reused artifact creation did not exist, then passed with `Tests run: 6, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,SubtitleBurnInPipelineStageTests,LocalizationJobExecutionServiceTests test` first failed because cache service wiring, context cache-hit tracking, and `CACHE_HIT` status did not exist, then passed with `Tests run: 27, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because job detail did not expose `cacheSummary`, then passed with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the UI did not show cache hits or reused artifact markers, then passed with `Tests run: 25`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 199, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 29`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config` passed and rendered the current demo stack configuration.
- `git diff --check` passed.

Notes:

- Cache reuse is artifact-level only. This slice does not add OpenAI prompt caching, provider response caching, semantic duplicate detection, or cross-video reuse.

Post-merge verification:

- Merged `artifact-cache-hit-mvp` back to `main` with merge commit `ddd1b3f`.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 199, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 29`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config` passed on `main` and rendered the current demo stack configuration.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added a read-only prompt template registry with active OpenAI translation and quality-evaluation templates.
- Replaced hardcoded OpenAI provider system prompts and audit prompt versions with registry-backed templates.
- Exposed active templates through `GET /api/prompt-templates`.
- Added a React prompt-template panel that shows active versions, purposes, providers, and output contracts.
- Documented the MVP boundary: in-code read-only templates now; editing, experiments, and database-backed history later.

Validation:

- `mvn -pl LinguaFrame -Dtest=PromptTemplateRegistryTests test` first failed because prompt template registry types did not exist, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranslationProviderTests,OpenAiQualityEvaluationProviderTests test` first failed because providers did not accept registry-backed prompts, then passed with `Tests run: 11, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=PromptTemplateControllerTests,OpenApiDocumentationTests test` first failed because `/api/prompt-templates` did not exist, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the frontend API and prompt-template panel did not exist, then passed with `Tests run: 28`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 203, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 32`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config` passed and rendered the current demo stack configuration.
- `git diff --check` passed.

Notes:

- This slice does not add prompt editing, A/B testing, database-backed prompt history, or automatic prompt optimization.

Post-merge verification:

- Merged `prompt-template-registry-mvp` back to `main` with merge commit `001bf5f`.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 203, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 32`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.

## 2026-06-27

Work:

- Added `tts_cache_entries` with unique cache keys and compatibility metadata for language, provider, model, and voice.
- Added TTS cache key, repository, and service layers.
- Serialized cached TTS audio as Base64 inside response JSON with filename and content type.
- Updated dubbing audio generation to check artifact cache first, then TTS provider cache, then budget/provider execution.
- Added provider cache-hit recording for `ModelCallOperation.TTS`.
- Documented TTS provider cache behavior and kept transcription, quality evaluation, and generic prompt-response caches as future work.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=TtsCacheKeyServiceTests,TtsCacheRepositoryTests,TtsCacheServiceTests test` first failed because TTS cache types did not exist, then passed with `Tests run: 9, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests test` first failed because the stage did not accept TTS cache dependencies, then passed after stage integration.
- `mvn -pl LinguaFrame -Dtest=DubbingAudioGenerationPipelineStageTests,LocalizationJobExecutionServiceTests test` passed with `Tests run: 31, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 269, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 37`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `job-result-bundle-download-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='JobArtifactServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test` passed on `main` with `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 42`.

Post-merge verification:

- Merged `demo-readiness-panel-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='RuntimeDependencyControllerTests,LinguaFramePropertiesTests,DemoAccessInterceptorTests' test` passed on `main` with `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 41`.

## 2026-06-27

Work:

- Added on-demand ZIP archive generation for generated job artifacts with a safe `manifest.json` and sanitized archive entry paths.
- Added `GET /api/jobs/{jobId}/artifacts/archive/download` and a React `Download result bundle` link in the `Artifacts` panel.
- Updated Docker demo scripts to download `artifacts.zip` and print ZIP entries for terminal demo verification.
- Documented that result bundles exclude source videos and are not persisted to object storage.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=JobArtifactServiceTests test` first failed because `openArtifactArchive` did not exist, then passed with `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,DemoAccessInterceptorTests' test` first failed because the archive endpoint was not routed, then passed with `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the archive URL helper and bundle link did not exist, then passed with `Tests run: 42`.

Validation:

- `mvn -pl LinguaFrame -Dtest='JobArtifactServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test` passed with `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 42`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 316, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 47`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-tears-of-steel-full.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `operator-dashboard-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='OperatorDashboardRepositoryTests,OperatorDashboardControllerTests,DemoAccessInterceptorTests' test` passed on `main` with `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 37`.

## 2026-06-27

Work:

- Added `scripts/demo/private-demo-preflight.sh` for local private demo readiness checks.
- The preflight checks required commands, `.env`, Docker Compose rendering, backend health, frontend reachability, optional demo-token gate behavior, and configured sample media paths.
- Kept preflight read-only with respect to media processing: it does not upload files and does not call OpenAI.
- Updated README, Docker E2E docs, smoke checklist, roadmap, spec, and decisions with the preflight workflow.

Validation so far:

- `bash -n scripts/demo/private-demo-preflight.sh` first failed because the script did not exist, then passed after implementation.
- `scripts/demo/private-demo-preflight.sh --help` first failed because the script did not exist, then passed after implementation.
- `LINGUAFRAME_ENV_FILE=/tmp/linguaframe-missing-env bash scripts/demo/private-demo-preflight.sh` failed with the expected `cp .env.example /tmp/linguaframe-missing-env` bootstrap guidance.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `rg -n "private-demo-preflight|Private Demo Preflight|preflight" README.md docs/agent/docker-e2e-demo.md docs/agent/smoke-test-checklist.md docs/product/roadmap.md docs/product/spec.md docs/progress/decisions.md docs/progress/execution-log.md` passed and found the documented workflow.
- `git diff --check` passed.

Post-merge verification:

- Merged `private-demo-preflight-runbook` back to `main` with merge commit.
- `bash -n scripts/demo/private-demo-preflight.sh` passed on `main`.
- `scripts/demo/private-demo-preflight.sh --help` passed on `main`.

## 2026-06-27

Work:

- Added `linguaframe.job-status-cache.enabled` and `ttl-seconds` runtime configuration.
- Added `LocalizationJobStatusCacheService` with a Redis `StringRedisTemplate` implementation.
- Cached serialized `LocalizationJobVo` snapshots for `GET /api/jobs/{jobId}` with key namespace `linguaframe:job-status:<jobId>`.
- Kept `listJobs` database-backed and reused the cached detail path for SSE snapshots.
- Evicted job detail snapshots after retry, cancellation, and worker status transitions.
- Kept cache reads, writes, deserialization, and eviction fail-open so Redis issues do not break job APIs.
- Documented job status cache variables, TTL, key boundary, and MySQL source-of-truth behavior.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='LinguaFramePropertiesTests,LocalizationJobControllerTests,LocalizationJobExecutionServiceTests,LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests' test` passed before implementation with `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame -Dtest='LinguaFramePropertiesTests,RedisLocalizationJobStatusCacheServiceTests' test` first failed because job-status cache configuration and service did not exist, then passed with `Tests run: 27, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobQueryServiceTests,LocalizationJobControllerTests' test` first failed because job detail did not read or populate the cache, then passed with `Tests run: 24, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests' test` first failed because mutation paths did not evict cached snapshots, then passed with `Tests run: 29, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest='RedisLocalizationJobStatusCacheServiceTests,LocalizationJobQueryServiceTests,LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test` passed with `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 300, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `redis-job-status-cache-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='RedisLocalizationJobStatusCacheServiceTests,LocalizationJobQueryServiceTests,LocalizationJobRetryServiceTests,LocalizationJobCancellationServiceTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test` passed on `main` with `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`.

Post-merge verification:

- Merged `tts-provider-cache-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=TtsCacheServiceTests,DubbingAudioGenerationPipelineStageTests test` passed on `main` with `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added durable `translation_cache_entries` for subtitle translation provider results.
- Added stable translation cache key generation from ordered transcript segments, target language, provider, model, and prompt version.
- Added `TranslationCacheService` to safely serialize and deserialize cached `TranslationResultBo` values.
- Wired target subtitle export to reuse cached translations before budget guard and provider calls.
- Added provider cache-hit timeline events and `cacheSummary.providerCacheHitCount`.
- Updated the React demo to distinguish artifact cache hits from provider cache hits.
- Documented translation-only provider caching and kept transcription, TTS, quality evaluation, and generic prompt-response caching as later work.

Validation:

- `mvn -pl LinguaFrame -Dtest=TranslationCacheRepositoryTests test` first failed because translation cache repository types did not exist, then passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=TranslationCacheKeyServiceTests test` first failed because the key service did not exist, then passed with `Tests run: 3, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=TranslationCacheServiceTests test` first failed because the cache service did not exist, then passed with `Tests run: 4, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=TargetSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests test` first failed because provider cache hits and target subtitle cache integration did not exist, then passed with `Tests run: 19, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because `cacheSummary.providerCacheHitCount` did not exist, then passed with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the usage summary still showed one aggregate cache count, then passed with `Tests run: 28`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 215, Failures: 0, Errors: 0, Skipped: 0`.
- After correcting `TargetSubtitleExportPipelineStage` production constructor injection, `mvn -pl LinguaFrame -Dtest=TargetSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests,LocalizationJobControllerTests,OpenAiTranslationContextTests test` passed with `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`.
- After that constructor correction, `mvn -pl LinguaFrame test` passed again with `Tests run: 215, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 32`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Notes:

- This slice caches subtitle translation provider results only. It does not cache transcription, TTS, quality evaluation, raw OpenAI payloads, or generic prompt-response records.

Post-merge verification:

- Merged `translation-provider-cache-mvp` back to `main` with merge commit `c962ad3`.
- `mvn -pl LinguaFrame test` passed on `main` with `Tests run: 215, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed on `main` with `Tests run: 32`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check HEAD` passed on `main`.

## 2026-06-27

Work:

- Added nullable `input_summary` and `output_summary` fields to durable model-call records.
- Added `ModelCallSummaryService` for count-only summaries across transcription, translation, quality evaluation, and TTS.
- Wired OpenAI and demo providers to record safe input/output summaries without raw transcript text, translated subtitle text, TTS text, request payloads, secrets, media bytes, or local media paths.
- Exposed `modelCalls[].inputSummary` and `modelCalls[].outputSummary` in job detail.
- Updated the React model-call panel to show Input and Output summary columns.

Validation:

- `mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,ModelCallAuditServiceTests test` first failed because summary fields did not exist, then passed with `Tests run: 8, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=ModelCallSummaryServiceTests test` first failed because the summary service did not exist, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=OpenAiTranscriptionProviderTests,OpenAiTranslationProviderTests,OpenAiTtsProviderTests,OpenAiQualityEvaluationProviderTests,DemoTranscriptionProviderTests,DemoTranslationProviderTests,DemoTtsProviderTests test` first failed because providers did not emit summaries, then passed with `Tests run: 26, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobControllerTests test` first failed because job detail fixtures did not expose populated summaries, then passed with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the model-call panel did not render summaries, then passed with `Tests run: 28`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 220, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 32`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Notes:

- Summaries are nullable for older records and capped at 512 characters before persistence.
- This slice does not add raw payload previews, provider prompt/response caching, new budget policy, or admin observability dashboards.

Post-merge verification:

- Merged `model-call-safe-summaries-mvp` back to `main` with merge commit `dfe08c6`.
- `mvn -pl LinguaFrame -Dtest=ModelCallAuditServiceTests,LocalizationJobControllerTests test` passed on `main` with `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 17`.

## 2026-06-27

Work:

- Added `COMBINED`, `FFMPEG`, and `OPENAI` worker roles with stage ownership routing.
- Added `startStage` to queued job messages while preserving legacy payload compatibility.
- Added FFmpeg and OpenAI RabbitMQ queues/routing keys and stage-aware publisher routing.
- Updated worker execution to run contiguous stage segments, publish handoff messages, keep jobs `PROCESSING` across split-worker handoffs, and mark completion only after the final segment.
- Added optional Docker Compose `split-workers` services for role-specific workers while keeping the default backend in combined mode.
- Documented combined versus split-worker operation in README, roadmap, spec, decisions, and this execution log.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,LinguaFramePropertiesTests test` first failed because worker role and router types did not exist, then passed with `Tests run: 18, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=RabbitJobQueueConfigurationTests,JobDispatchServiceTests,JobDispatchOutboxServiceTests,RabbitJobQueuePublisherTests test` first failed because messages and queue routing were not stage-aware, then passed with `Tests run: 10, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test` first failed because segmented execution and listener queue wiring did not exist, then passed with `Tests run: 25, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,RabbitJobQueueConfigurationTests,LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test` passed with `Tests run: 31, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `mvn -pl LinguaFrame test` passed with `Tests run: 235, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 32`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `retention-policy-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests,RetentionCleanupControllerTests test` passed on `main` with `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.

Post-merge verification:

- Merged `worker-role-routing-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=WorkerStageRouterTests,LocalizationJobExecutionServiceTests,LocalizationJobWorkerTests test` passed on `main` with `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed on `main`.

## 2026-06-27

Work:

- Added an optional Spring MVC demo access gate for `/api/**`.
- Added `linguaframe.demo.access-token` and `linguaframe.demo.access-header-name` runtime configuration.
- Kept local development open when the configured demo token is blank.
- Added React demo token save/clear controls.
- Added API header injection for JSON and multipart requests when a browser token exists.
- Added a same-site browser cookie path so EventSource progress, artifact downloads, audio previews, and video previews work in gated demo mode.
- Documented private demo access variables in README, `.env.example`, Compose, roadmap, spec, and decisions.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,LinguaFramePropertiesTests test` first failed because demo access configuration and interceptor behavior did not exist, then passed with `Tests run: 20, Failures: 0, Errors: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because token helpers, API header injection, and UI controls did not exist, then passed with `Tests run: 33`.
- A cookie compatibility regression test first failed because gated API requests only accepted the custom header, then passed after accepting the `LinguaFrame-Demo-Token` cookie.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,LinguaFramePropertiesTests,RuntimeDependencyControllerTests test` passed with `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed again with `Tests run: 33`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 242, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 37`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed again.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed again.
- `git diff --check` passed.

Notes:

- This slice is not JWT, multi-user authentication, OAuth, billing, or enterprise permissions.
- Real demo tokens must stay in local `.env` or deployment secrets and must not be committed.

Post-merge verification:

- Merged `private-demo-access-gate-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=DemoAccessInterceptorTests,RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 33`.

## 2026-06-27

Work:

- Added Redis-backed fixed-window upload rate limiting for `POST /api/media/uploads` and `POST /api/media/uploads/validate`.
- Added `spring-boot-starter-data-redis` plus Spring Redis host/port configuration.
- Added `linguaframe.rate-limit.enabled`, `upload-max-requests`, `upload-window-seconds`, and `fail-open` runtime properties.
- Added hashed client identity resolution using the demo token, `X-Forwarded-For`, or remote address without storing raw identifiers in Redis keys.
- Added structured `429 RATE_LIMIT_EXCEEDED` responses with `Retry-After` and `X-RateLimit-*` headers.
- Documented rate-limit variables and upload behavior in README, `.env.example`, Compose, roadmap, spec, and decisions.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='MediaUploadControllerTests,LinguaFramePropertiesTests' test` passed before implementation with `Tests run: 24, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest='*RateLimit*Tests,LinguaFramePropertiesTests' test` first failed because rate-limit configuration and classes did not exist, then passed with `Tests run: 25, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest='*RateLimit*Tests,MediaUploadControllerTests,MediaUploadRateLimitControllerTests,LinguaFramePropertiesTests' test` passed with `Tests run: 36, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 281, Failures: 0, Errors: 0`.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

## 2026-06-27

Work:

- Added retention runtime configuration with default-off, dry-run-first behavior.
- Added retention candidate selection for terminal jobs with separate TTLs for completed, failed, and cancelled jobs.
- Added object storage delete support and safe generic delete failures.
- Added retention cleanup service behavior for dry-run preview, object deletion, dependent row cleanup, orphaned video cleanup, shared video preservation, and per-job failure counting.
- Added operator endpoints: `GET /api/retention/cleanup/preview` and `POST /api/retention/cleanup/run`.
- Added an optional scheduler that only runs when both retention and scheduler flags are enabled.
- Documented retention variables, manual testing commands, product boundary, and private-demo roadmap status.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=LinguaFramePropertiesTests,LocalizationJobRepositoryTests test` first failed because retention config and candidate query did not exist, then passed.
- `mvn -pl LinguaFrame -Dtest=MinioObjectStorageServiceTests,JobArtifactServiceTests,MediaUploadServiceTests test` passed after adding object delete support.
- `mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests test` first failed because the service did not exist, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=RetentionCleanupServiceTests,LocalizationJobRepositoryTests,JobArtifactRepositoryTests test` passed with `Tests run: 19, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame -Dtest=RetentionCleanupControllerTests,RetentionCleanupSchedulerTests test` first failed because the API and scheduler did not exist, then passed with `Tests run: 5, Failures: 0, Errors: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 257, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 37`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

## 2026-06-27

Work:

- Added job-level TTS voice selection from upload through durable jobs, job detail/list/upload responses, dispatch payloads, worker TTS requests, OpenAI TTS request bodies, and TTS provider cache keys.
- Added nullable `localization_jobs.tts_voice` with backward-compatible record/message constructors for older jobs and payloads.
- Added React upload voice selection with provider-default fallback, recent-job compatibility for old browser records, and selected-voice display in job metadata, history, and recent jobs.
- Documented fallback behavior, cache compatibility, and the job-level voice decision.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='UploadIntakeSchemaTests,LocalizationJobRepositoryTests' test` first failed because the schema/entity did not expose `ttsVoice`, then passed.
- `mvn -pl LinguaFrame -Dtest='MediaUploadControllerTests,MediaUploadServiceTests,JobDispatchOutboxServiceTests,LocalizationJobControllerTests' test` first failed because upload and dispatch did not accept or expose `ttsVoice`, then passed.
- `mvn -pl LinguaFrame -Dtest='DubbingAudioGenerationPipelineStageTests,TtsCacheKeyServiceTests,OpenAiTtsProviderTests' test` first failed because `TtsRequestBo.voice()` and request-level OpenAI voice selection did not exist, then passed with `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi recentJobs App` first failed because the API did not send `ttsVoice`, recent jobs did not normalize it, and the upload form had no voice selector, then passed with `Tests run: 39`.
- `mvn -pl LinguaFrame -Dtest='UploadIntakeSchemaTests,LocalizationJobRepositoryTests,MediaUploadControllerTests,MediaUploadServiceTests,JobDispatchOutboxServiceTests,LocalizationJobControllerTests,DubbingAudioGenerationPipelineStageTests,TtsCacheKeyServiceTests,OpenAiTtsProviderTests' test` passed with `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi recentJobs App` passed with `Tests run: 39`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 307, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 39`.
- `cd frontend && npm run build` first failed on strict TypeScript nullability for recent jobs and job summary fixtures, then passed after type normalization.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `tts-voice-selection-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='UploadIntakeSchemaTests,LocalizationJobRepositoryTests,MediaUploadControllerTests,MediaUploadServiceTests,JobDispatchOutboxServiceTests,LocalizationJobControllerTests,DubbingAudioGenerationPipelineStageTests,TtsCacheKeyServiceTests,OpenAiTtsProviderTests' test` passed on `main` with `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi recentJobs App` passed on `main` with `Tests run: 39`.

## 2026-06-27

Work:

- Enforced `linguaframe.worker.max-retries` in failed-job retry before state mutation or dispatch enqueue.
- Added structured retry-limit conflict coverage for the retry API and React failed-job retry flow.
- Exposed retry-limit configuration through `.env.example` and Docker Compose.
- Updated the Docker retry demo script to print retry limit, failure stage, and failure reason evidence.
- Documented bounded retry behavior in README and product docs.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LinguaFramePropertiesTests' test` first failed because `LocalizationJobRetryServiceImpl` did not accept configuration for retry limits, then passed with `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,LocalizationJobRetryServiceTests' test` passed with `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 22`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-retry.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.

Validation:

- `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test` passed with `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 22`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 313, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 43`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `retry-guard-and-evidence-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobRetryServiceTests,LocalizationJobControllerTests,LinguaFramePropertiesTests' test` passed on `main` with `Tests run: 42, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 22`.

## 2026-06-27

Work:

- Extended the sanitized runtime dependency summary with read-only demo readiness fields for demo gate state, worker configuration, media limits, FFmpeg settings, provider modes, and runtime feature flags.
- Added a React `Demo readiness` sidebar panel backed by `GET /api/runtime/dependencies`, including refresh, local error handling, and upload controls that remain usable when readiness loading fails.
- Documented that browser readiness is configuration-derived only and complements the local private-demo preflight script.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='RuntimeDependencyControllerTests,LinguaFramePropertiesTests,DemoAccessInterceptorTests' test` first failed because `readiness` was absent, then passed with `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the `Demo readiness` region was absent, then passed with `Tests run: 41`.

Validation:

- `mvn -pl LinguaFrame -Dtest='RuntimeDependencyControllerTests,LinguaFramePropertiesTests,DemoAccessInterceptorTests' test` passed with `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 41`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 313, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 46`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

## 2026-06-27

Work:

- Added read-only operator dashboard API at `GET /api/operator/dashboard`.
- Added dashboard aggregates for job status counts, recent failed jobs, model-call totals, and cache totals from existing durable tables.
- Added React operator dashboard panel with refresh, safe local error handling, and click-to-open for recent failed jobs.
- Documented the dashboard as demo observability, not a full mutable admin dashboard.

Validation so far:

- `mvn -pl LinguaFrame -Dtest='OperatorDashboardRepositoryTests,OperatorDashboardControllerTests,DemoAccessInterceptorTests' test` first failed because `OperatorDashboardRepository` did not exist, then passed with `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because `getOperatorDashboard` and dashboard UI did not exist, then passed with `Tests run: 37`.

Validation:

- `mvn -pl LinguaFrame -Dtest='OperatorDashboardRepositoryTests,OperatorDashboardControllerTests,DemoAccessInterceptorTests' test` passed with `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 37`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 310, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 42`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `docker compose --env-file .env.example config --quiet` passed.
- `docker compose --env-file .env.example --profile split-workers config --quiet` passed.
- `git diff --check` passed.

## 2026-06-27

Work:

- Added typed frontend API helpers for retention cleanup preview and run endpoints.
- Added a React `Retention cleanup` sidebar panel with dry-run/delete-mode copy, aggregate counts, preview refresh, and confirmation-gated manual run.
- Documented the browser retention workflow while keeping curl commands as the terminal fallback.

Validation so far:

- `cd frontend && npm run test:run -- linguaframeApi` passed with `Tests run: 20`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 29`.

Validation:

- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 49`.
- `cd frontend && npm run test:run` passed with `Tests run: 54`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `mvn -pl LinguaFrame -Dtest='RetentionCleanupControllerTests,RuntimeDependencyControllerTests' test` passed with `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 316, Failures: 0, Errors: 0, Skipped: 0`.
- `git diff --check` passed.

Post-merge verification:

- Merged `retention-cleanup-panel-mvp` back to `main` with merge commit.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 49`.

## 2026-06-27

Work:

- Added metadata-only job diagnostics report VO and query service method.
- Added `GET /api/jobs/{jobId}/diagnostics/download` as a JSON attachment.
- Added `Download diagnostics` to the selected job header in the React demo.
- Updated demo scripts to download diagnostics JSON and print safe summary counts.
- Documented diagnostics export behavior in README, product docs, smoke checklist, roadmap, and decisions.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=LocalizationJobQueryServiceTests test` first failed because diagnostics VO and query method did not exist, then passed with `Tests run: 6`.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobControllerTests,DemoAccessInterceptorTests' test` first failed with `404` for the diagnostics route, then passed with `Tests run: 29`.
- `cd frontend && npm run test:run -- linguaframeApi App` first failed because the diagnostics URL helper and selected-job link did not exist, then passed with `Tests run: 50`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-retry.sh scripts/demo/docker-e2e-budget-guard.sh` passed.

Validation:

- `mvn -pl LinguaFrame -Dtest='LocalizationJobQueryServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test` passed with `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed with `Tests run: 50`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 319, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 55`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh scripts/demo/docker-e2e-retry.sh scripts/demo/docker-e2e-budget-guard.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `job-diagnostics-report-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='LocalizationJobQueryServiceTests,LocalizationJobControllerTests,DemoAccessInterceptorTests' test` passed on `main` with `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- linguaframeApi App` passed on `main` with `Tests run: 50`.

## 2026-06-27

Work:

- Added budget guard readiness fields to the sanitized runtime dependency summary.
- Added budget guard status and per-job cost limit display to the React `Demo readiness` panel.
- Added `scripts/demo/docker-e2e-budget-guard.sh` plus a shared job-failure evidence printer.
- Documented the budget guard Docker evidence path in README and the smoke-test checklist.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` passed with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` first failed because `Budget guard` appears in both readiness and feature flags, then passed with `Tests run: 29`.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-budget-guard.sh` passed.

Validation:

- `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` passed with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 29`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 316, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run` passed with `Tests run: 54`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-budget-guard.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `budget-guard-demo-evidence-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 29`.

## 2026-06-27

Work:

- Added durable transcription provider cache schema, repository, key service, and JSON serialization service.
- Wired `TranscriptSubtitleExportPipelineStage` to look up cached transcription results by extracted-audio hash, provider, model, and prompt version before budget/provider execution.
- Reused cached transcript segments to write fresh transcript/SRT/VTT artifacts and report provider `CACHE_HIT` timeline events through the existing execution context.
- Documented transcription cache behavior, smoke-test expectations, and product status.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=TranscriptionCacheRepositoryTests test` passed with `Tests run: 2`.
- `mvn -pl LinguaFrame -Dtest='TranscriptionCacheKeyServiceTests,TranscriptionCacheServiceTests' test` passed with `Tests run: 7`.
- `mvn -pl LinguaFrame -Dtest=TranscriptSubtitleExportPipelineStageTests test` first failed because the stage did not accept cache services, then passed with `Tests run: 2`.
- `mvn -pl LinguaFrame -Dtest='TranscriptSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed with `Tests run: 26`.

Validation:

- `mvn -pl LinguaFrame -Dtest='TranscriptionCacheRepositoryTests,TranscriptionCacheKeyServiceTests,TranscriptionCacheServiceTests,TranscriptSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed with `Tests run: 35`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 330, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 29`.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `transcription-provider-cache-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='TranscriptionCacheRepositoryTests,TranscriptionCacheKeyServiceTests,TranscriptionCacheServiceTests,TranscriptSubtitleExportPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed on `main` with `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 29`.

## 2026-06-27

Work:

- Added durable quality evaluation provider cache schema, repository, key service, and JSON serialization service.
- Added `QualityEvaluationService.storeCachedEvaluation` so cache hits write fresh current-job evaluation rows without provider calls.
- Wired `QualityEvaluationPipelineStage` to look up cached evaluation results by source transcript hash, target subtitle hash, target language, provider, model, and prompt version before budget/provider execution.
- Reused cached structured evaluation results and reported provider `CACHE_HIT` timeline events through the existing execution context.
- Documented quality evaluation cache behavior, smoke-test expectations, and product status.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=QualityEvaluationCacheRepositoryTests test` first failed because cache command and repository types did not exist, then passed with `Tests run: 2`.
- `mvn -pl LinguaFrame -Dtest='QualityEvaluationCacheKeyServiceTests,QualityEvaluationCacheServiceTests' test` first failed because cache key/service types did not exist, then passed with `Tests run: 6`.
- `mvn -pl LinguaFrame -Dtest=QualityEvaluationServiceTests test` first failed because `storeCachedEvaluation` did not exist and recording stubs needed the new method, then passed with `Tests run: 3`.
- `mvn -pl LinguaFrame -Dtest=QualityEvaluationPipelineStageTests test` first failed because the stage did not accept cache services, then passed with `Tests run: 2`.
- `mvn -pl LinguaFrame -Dtest='QualityEvaluationPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed with `Tests run: 26`.

Validation:

- `mvn -pl LinguaFrame -Dtest='QualityEvaluationCacheRepositoryTests,QualityEvaluationCacheKeyServiceTests,QualityEvaluationCacheServiceTests,QualityEvaluationServiceTests,QualityEvaluationPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed with `Tests run: 37`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 341, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 29`.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `quality-evaluation-provider-cache-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest='QualityEvaluationCacheRepositoryTests,QualityEvaluationCacheKeyServiceTests,QualityEvaluationCacheServiceTests,QualityEvaluationServiceTests,QualityEvaluationPipelineStageTests,LocalizationJobExecutionServiceTests' test` passed on `main` with `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 29`.

## 2026-06-27

Work:

- Added a Docker cache-hit evidence script that uploads the same sample twice, downloads both job details and diagnostics reports, and fails when the second compatible job has no provider cache hit.
- Added shared demo helper functions for safe job detail download, cache summary printing, provider `CACHE_HIT` filtering, and first/second job comparison.
- Documented the cache-hit demo command, expected evidence, and `/tmp/linguaframe-demo/cache-hit/` output files in README, the Docker E2E runbook, and the smoke-test checklist.

Validation so far:

- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-cache-hit.sh` passed.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed with `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.
- `scripts/demo/docker-e2e-cache-hit.sh` first exposed a stale backend container missing `/api/jobs/{jobId}/diagnostics/download`; after packaging the current backend jar and recreating `linguaframe-backend`, it passed. Evidence: first job `modelCallCount=2`, `providerCacheHitCount=0`; second job `modelCallCount=0`, `providerCacheHitCount=2`; downloaded evidence to `/tmp/linguaframe-demo/cache-hit/`.

Post-merge verification:

- Merged `cache-hit-demo-evidence-mvp` back to `main` with merge commit.
- `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-cache-hit.sh` passed on `main`.
- `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test` passed on `main` with `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added a sanitized runtime contract to `GET /api/runtime/dependencies` with app version, latest bundled Flyway migration version, and required demo route paths.
- Added a private-demo preflight runtime freshness check that compares the running backend migration contract with local migration files and fails early when the backend container is stale.
- Updated the React demo readiness panel to show runtime app version and migration contract.
- Documented stale-container detection and backend package/recreate commands in README, Docker E2E docs, smoke checklist, spec, and roadmap.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` first failed because `runtime` was missing, then passed with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `bash -n scripts/demo/private-demo-preflight.sh` passed.
- `LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh` failed early against the currently running stale backend with the expected runtime freshness message and backend recreate commands.
- `cd frontend && npm run test:run -- App linguaframeApi` first failed because the readiness panel did not show runtime metadata, then passed with `Tests run: 50`.
- `mvn -pl LinguaFrame -am package -DskipTests` passed and produced the backend jar for Docker.
- `docker compose --env-file .env.example up -d --build linguaframe-backend` passed and recreated the backend with the runtime contract.
- `LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh` then passed the backend runtime freshness check with `runtimeLatestMigrationVersion=17` and `localLatestMigrationVersion=17`; it still failed later because the frontend was not running.
- `docker compose --env-file .env.example up -d --build linguaframe-frontend` could not complete because Docker failed to resolve `hub-mirror.c.163.com` while loading `node:26-alpine`.
- `cd frontend && npm run build` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `docker-runtime-freshness-guard-mvp` back to `main` with merge commit.
- `bash -n scripts/demo/private-demo-preflight.sh` passed on `main`.
- `mvn -pl LinguaFrame -Dtest=RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App linguaframeApi` passed on `main` with `Tests run: 50`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added `scripts/demo/frontend-local-dev.sh` as a local Vite fallback when Docker cannot build the frontend image.
- Updated private-demo preflight frontend failure guidance to include both Docker and local fallback startup commands.
- Documented the fallback in README, Docker E2E docs, and the smoke-test checklist.

Validation so far:

- `bash -n scripts/demo/private-demo-preflight.sh scripts/demo/frontend-local-dev.sh` passed.
- `scripts/demo/frontend-local-dev.sh --help` passed after making the script executable.
- `scripts/demo/frontend-local-dev.sh` first failed in the sandbox with `listen EPERM` on `0.0.0.0:5173`, then started successfully with host port access.
- `curl -fsSI http://localhost:5173` passed with `HTTP/1.1 200 OK`.
- `LINGUAFRAME_ENV_FILE=.env.example scripts/demo/private-demo-preflight.sh` passed end-to-end while the local fallback server was running.
- `cd frontend && npm run test:run -- App` passed with `Tests run: 29`.
- `cd frontend && npm run build` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `frontend-local-fallback-demo-runner-mvp` back to `main` with merge commit.
- `bash -n scripts/demo/private-demo-preflight.sh scripts/demo/frontend-local-dev.sh` passed on `main`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests run: 29`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main` after rerunning with the correct repository path.

## 2026-06-27

Work:

- Added a browser `Result delivery` panel to the selected job view.
- Summarized expected deliverables as `Ready`, `Preview only`, or `Missing` from existing job detail, artifact, transcript, and subtitle APIs.
- Added generated/reused/missing counts, model-call count, estimated cost, direct ready-artifact downloads, result bundle and diagnostics links, short SHA-256 hashes, and generated/reused cache evidence.
- Preserved the existing artifact table and media previews below the new delivery summary.
- Documented the browser result-delivery checks in README, the Docker E2E guide, and the smoke-test checklist.

Validation so far:

- `cd frontend && npm run test:run -- App -t "result delivery|artifact downloads"` first failed because the `Result delivery` region did not exist, then passed with `Tests 2 passed | 33 skipped`.
- `cd frontend && npm run test:run -- App` first exposed duplicate `2 calls` and diagnostics-link assertions after the new panel intentionally repeated those actions, then passed with `Tests 35 passed`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `job-result-delivery-panel-mvp` back to `main` with merge commit.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 35 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-27

Work:

- Added a browser `Demo evidence` panel to the selected job view.
- Generated safe Markdown and JSON evidence from already-loaded job detail, timeline, usage, cache, artifact, transcript-count, subtitle-count, and quality evaluation metadata.
- Added `Copy evidence` with Clipboard API detection and `Download evidence JSON` with a local Blob download.
- Kept raw transcript text, raw subtitle text, source artifact ids, object keys, local paths, tokens, provider payloads, and media bytes out of exported evidence.
- Documented browser evidence export behavior in README, the Docker E2E guide, and the smoke-test checklist.

Validation so far:

- `cd frontend && npm run test:run -- App -t "demo evidence"` first failed because the `Demo evidence` region did not exist, then passed with `Tests 2 passed | 35 skipped`.
- `cd frontend && npm run test:run -- App` passed with `Tests 37 passed`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `browser-demo-evidence-export-mvp` back to `main` with merge commit.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 37 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-28

Work:

- Added OpenAPI demo-token security metadata for the `X-LinguaFrame-Demo-Token` header.
- Added primary OpenAPI tags, operation summaries, parameter descriptions, and response descriptions for media upload, localization job, runtime dependency, prompt-template, operator dashboard, and retention cleanup controllers.
- Expanded the OpenAPI contract test to verify API metadata, `DemoAccessToken`, primary tags, and the full demo workflow path set.
- Documented Swagger/OpenAPI demo validation in README, the Docker E2E guide, the smoke-test checklist, and roadmap.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=OpenApiDocumentationTests test` first failed because `components.securitySchemes.DemoAccessToken` was missing, then passed with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 341, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests 37 passed`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh scripts/demo/docker-e2e-success.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `codex-openapi-demo-contract-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=OpenApiDocumentationTests test` passed on `main` with `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 37 passed`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-28

Work:

- Planned the demo daily cost budget hook MVP in `docs/plans/062-demo-daily-cost-budget-hook-mvp.md`.
- Added an opt-in daily budget guard for private demos using a safe configured budget identity and same-day estimated model-call costs.
- Added `budget_identity` persistence to model-call records and repository daily cost aggregation by identity.
- Extended the existing budget guard so guarded AI stages still check per-job budget first, then the configured UTC daily budget before provider execution.
- Exposed daily budget guard state, daily limit, and safe budget identity in runtime readiness and the React demo readiness panel.
- Added `.env.example`, Compose, and YAML mappings for daily budget configuration.
- Added `scripts/demo/docker-e2e-daily-budget-guard.sh` and documented the repeatable daily budget evidence path.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,CostBudgetGuardServiceTests,RuntimeDependencyControllerTests test` first failed because Spring could not select a constructor for `CostBudgetGuardServiceImpl`, then passed with `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0` after adding explicit constructor injection.
- `mvn -pl LinguaFrame test` passed with `Tests run: 352, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` first failed because the fixture still expected migration `V17`, then passed with `Tests 37 passed` after updating it to `V18`.
- `cd frontend && npm run build` first failed because `dailyBudgetGuard` was missing from the frontend feature-flag type union, then passed after adding the new feature flag key.
- `bash -n scripts/demo/docker-e2e-budget-guard.sh scripts/demo/docker-e2e-daily-budget-guard.sh scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `codex-demo-daily-cost-budget-hook-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=ModelCallRepositoryTests,CostBudgetGuardServiceTests,RuntimeDependencyControllerTests test` passed on `main` with `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 37 passed`.
- `cd frontend && npm run build` passed on `main`.
- `bash -n scripts/demo/docker-e2e-budget-guard.sh scripts/demo/docker-e2e-daily-budget-guard.sh scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.

## 2026-06-28

Work:

- Added job-scoped backend log context with safe MDC keys for `jobId`, `videoId`, `stage`, and `workerRole`.
- Wrapped worker execution and pipeline stages with scoped MDC so stage logs can be searched by job and stage without leaking source object keys or local paths.
- Added worker execution logs for message receipt, stale-message skips, stage planning, stage start/success/failure, handoff, cancellation, and completion.
- Added console log patterns for default, local, Docker, and test profiles.
- Documented Docker log inspection commands in README, the Docker E2E guide, the smoke-test checklist, and roadmap.

Validation so far:

- `mvn -pl LinguaFrame -Dtest=LinguaFrameLogContextTests,LocalizationJobExecutionServiceTests test` first failed because `LinguaFrameLogContext` did not exist, then passed with `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.
- `mvn -pl LinguaFrame test` passed with `Tests run: 347, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed with `Tests 37 passed`.
- `cd frontend && npm run build` passed and produced the production Vite bundle.
- `bash -n scripts/demo/start-local-demo.sh scripts/demo/frontend-local-dev.sh scripts/demo/private-demo-preflight.sh scripts/demo/docker-e2e-success.sh` passed.
- `docker compose --env-file .env.example config --quiet` passed.
- `git diff --check` passed.

Post-merge verification:

- Merged `codex-job-scoped-structured-logging-mvp` back to `main` with merge commit.
- `mvn -pl LinguaFrame -Dtest=LinguaFrameLogContextTests,LocalizationJobExecutionServiceTests test` passed on `main` with `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`.
- `cd frontend && npm run test:run -- App` passed on `main` with `Tests 37 passed`.
- `cd frontend && npm run build` passed on `main`.
- `docker compose --env-file .env.example config --quiet` passed on `main`.
- `git diff --check` passed on `main`.
