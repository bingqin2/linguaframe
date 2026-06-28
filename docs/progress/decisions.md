# Decision Log

This file records project-level decisions that affect future implementation. Feature-specific decisions should also be recorded in the relevant ExecPlan under `docs/plans/`.

## 2026-06-28

Decision: Derive demo presenter packs on demand from existing evidence APIs instead of storing curated presentation sessions.

Reason: The private demo needs one presenter-facing checklist for a selected job, but job detail, same-source run matrix, delivery manifest, diagnostics, evidence reports, and package routes already provide the durable facts.

Impact: `GET /api/jobs/{jobId}/demo-presenter-pack` builds readiness, recommended run IDs, role labels, safe download routes, and metadata-only presenter notes at request time. Persistent presenter sessions remain later work only if manual ordering, annotations, or multi-presenter collaboration become product requirements.

## 2026-06-28

Decision: Derive the demo run matrix on demand from existing same-source jobs instead of storing a demo-session table.

Reason: The current demo needs a presentation aid that explains recent profile attempts for one source video, while job detail, model calls, quality evaluation, cache evidence, and delivery manifests already hold the durable facts.

Impact: `GET /api/jobs/{jobId}/demo-run-matrix` groups recent jobs by the anchor job's `videoId`, marks baseline, best-quality, and lowest-cost candidates, and keeps browser and terminal outputs metadata-only. A persistent demo-session model remains later work only if multi-user curated sessions need manual ordering or annotations.

## 2026-06-28

Decision: Keep subtitle review read-only before adding subtitle editing workflows.

Reason: The current demo needs a trustworthy inspection surface that explains generated transcript/subtitle alignment, quality evaluation, and downloadable subtitle artifacts without introducing versioning, re-export, cache invalidation, or media regeneration semantics.

Impact: `GET /api/jobs/{jobId}/subtitle-review` derives its summary from persisted transcript segments, target subtitle segments, quality evaluation, and artifacts. Browser and terminal evidence export only metadata counts, while future editing will need a separate plan for draft subtitle state and regeneration.

## 2026-06-28

Decision: Derive pipeline progress and stage timing from durable timeline events instead of adding a separate progress table.

Reason: `job_timeline_events` already captures stage, status, timestamps, duration, and safe messages. Reusing it keeps progress advisory, avoids another consistency boundary, and makes diagnostics, evidence, browser UI, and terminal scripts agree with the same source of truth.

Impact: Job detail exposes `pipelineProgress`, the operator dashboard aggregates recent stage timings, and evidence exports can explain current stage and slowest stage without changing worker execution, retry, cancellation, cache, or provider semantics.

## 2026-06-28

Decision: Add a backend aggregate endpoint for demo profile comparison while keeping cache replay as a separate frontend-composed workflow.

Reason: Profile A/B demos need one stable evidence object that compares quality, cost, model calls, cache counts, handoff readiness, and changed settings across two completed jobs. Backend Markdown keeps terminal and browser demos aligned.

Impact: Operators can compare `quick-baseline`, `tears-showcase`, and other profile runs from the browser or full-video script without exposing raw transcript, subtitle, provider payload, local path, or secret data.

## 2026-06-28

Decision: Compose cache replay comparison in the React demo from existing safe job APIs instead of adding a backend aggregate endpoint.

Reason: The backend already exposes job detail, timeline, cache summary, model-call summary, and artifact cache state. A frontend-composed comparison is enough for the private demo and avoids adding a redundant read API before public multi-user needs exist.

Impact: The browser can explain cache-hit replay using two selected jobs and export safe evidence, while backend cache semantics remain centralized in existing job detail and artifact responses.

## 2026-06-28

Decision: Handle private-demo durability with operator-run backups before adding managed cloud storage or public account isolation.

Reason: The current product goal is a credible single-owner demo, not a public hosted service. A scripted backup/restore path protects job history, MinIO artifacts, and proxy state while keeping deployment simple and local-first.

Impact: `scripts/demo/private-demo-backup.sh` and `scripts/demo/private-demo-restore.sh` become the private-demo migration and recovery path. Managed storage, user-scoped retention, and multi-tenant restore semantics remain later public-service work.

## 2026-06-28

Decision: Add private-demo deployment as an overlay instead of changing the local Docker Compose stack.

Reason: The local demo is already stable and should remain easy to run. A private server demo needs a public HTTPS entry point and internal service routing, but it should not force local contributors through reverse-proxy setup.

Impact: `deploy/private-demo/docker-compose.private-demo.yml` and `deploy/private-demo/Caddyfile` define a proxy-fronted stack, `.env.private-demo.example` documents deployment values, and `scripts/demo/private-demo-deploy-preflight.sh` validates deployment shape before startup.

## 2026-06-22

Decision: Build LinguaFrame as an AI video localization platform.

Reason: The project should demonstrate backend engineering depth for internship recruiting: upload handling, media processing, async jobs, object storage, OpenAI API integration, subtitle generation, retry handling, and cost observability.

Impact: The MVP focuses on a video-to-localized-artifacts pipeline instead of a generic chatbot or simple API wrapper.

## 2026-06-22

Decision: Use Java 21 and Spring Boot as the primary backend stack.

Reason: The project is intended to align with a Java backend internship profile while using a modern Java baseline.

Impact: Media and AI workflow concepts should be implemented through Java backend patterns, not Python notebooks or one-off scripts.

## 2026-06-22

Decision: Use React for the frontend.

Reason: React is widely recognized and suitable for upload, job monitoring, artifact preview, and demo workflows.

Impact: The frontend should be a work surface for video localization, not a landing page.

## 2026-06-22

Decision: Use MySQL as the durable database.

Reason: MySQL is common in Java backend roles and fits the project goal of demonstrating practical backend engineering.

Impact: Persistence plans should target MySQL with Flyway migrations. The selected Java data-access approach should be recorded once chosen.

## 2026-06-22

Decision: Use OpenAI APIs for speech-to-text, translation or subtitle polishing, and text-to-speech.

Reason: Using one provider reduces integration complexity and lets the first demo focus on the media pipeline.

Impact: The backend should keep OpenAI calls behind client interfaces, but the default implementation is OpenAI.

## 2026-06-22

Decision: Use Redis, RabbitMQ, MinIO, and FFmpeg in the core architecture.

Reason: These components make the project production-shaped: Redis for job status and rate-limit hooks, RabbitMQ for long-running jobs, MinIO for S3-compatible artifacts, and FFmpeg for real media processing.

Impact: Docker Compose should include these services early enough to validate the pipeline locally.

## 2026-06-22

Decision: Start with a modular monolith and local self-hosted deployment.

Reason: The first technical risk is proving the upload-to-artifact pipeline, not distributed deployment.

Impact: The backend starts as one Spring Boot service with clear internal modules. API/worker split and public hosted deployment remain later stages.

## 2026-06-22

Decision: Include translation subtitles, TTS dubbing audio, subtitle-burned video, cost tracking, and failure retry in the official demo goal.

Reason: These capabilities make the demo more complete and better aligned with the desired resume story.

Impact: The roadmap treats these features as planned MVP phases, but implementation should still proceed in narrow, testable slices.

## 2026-06-25

Decision: Add LLM and AI infrastructure depth as post-MVP roadmap scope.

Reason: The project should read as more than a video tool. Prompt versioning, model-call tracing, quality evaluation, cost budgets, and cache-aware duplicate-work avoidance demonstrate production-shaped LLM engineering while keeping video localization as the product scenario.

Impact: The core MVP remains upload, async processing, subtitles, translation, TTS, subtitle-burned video, cost tracking, and retry. AI infrastructure enhancements are planned as later phases so they can be implemented when there is time without destabilizing the first demo.

## 2026-06-25

Decision: Keep OpenAI as the default provider while designing narrow AI client boundaries.

Reason: The current project should not spend time integrating multiple providers, but interview-ready architecture should avoid scattering raw OpenAI calls across business services.

Impact: Future code should use interfaces for speech, language, quality evaluation, and TTS clients. OpenAI implementations can be the only concrete implementations until a real need for another provider exists.

## 2026-06-26

Decision: Add target-language subtitle generation with a deterministic demo translation provider before real OpenAI calls.

Reason: The storage, preview, artifact export, and worker-stage behavior should be stable and testable without provider credentials, cost tracking, or paid API failures.

Impact: Translation now sits behind `TranslationProvider`, and target subtitles are stored separately from source transcript segments. A later OpenAI integration can replace the demo provider without rewriting subtitle persistence or export.

## 2026-06-26

Decision: Add OpenAI translation as an opt-in provider behind `TranslationProvider` while keeping the Docker demo on deterministic translation by default.

Reason: The project can now exercise a real language-model integration when local secrets are available, but routine demos and automated tests should remain reproducible, free of paid API calls, and safe to run without credentials.

Impact: Real OpenAI translation can be exercised with local `.env` secrets, while the core demo and automated tests remain reproducible and cost-free.

## 2026-06-26

Decision: Add OpenAI transcription as an opt-in provider behind `TranscriptionProvider` while keeping deterministic transcript generation as the default demo path.

Reason: The project needs a real speech-to-text integration to move closer to the target localization pipeline, but routine demos and automated tests should remain reproducible and safe without provider credentials.

Impact: The pipeline can validate real speech-to-text locally with secrets and a real speech sample, while automated tests and default Docker demos remain reproducible and cost-free.

## 2026-06-26

Decision: Add TTS dubbing audio as a provider-backed worker stage instead of folding it into subtitle export.

Reason: Subtitle generation and audio synthesis are different pipeline concerns. Keeping TTS behind `TtsProvider` allows deterministic local demos and opt-in OpenAI audio generation without rewriting subtitle persistence or artifact download behavior.

Impact: The worker can now create a `DUBBING_AUDIO` artifact after target subtitle export. The MVP generates one continuous MP3 and intentionally defers lip sync, audio/video mixing, subtitle burn-in, and cost accounting.

## 2026-06-26

Decision: Add subtitle-burned video as an FFmpeg-backed worker stage after generated subtitles.

Reason: Burn-in is a media rendering concern and should stay separate from subtitle generation and TTS.

Impact: Docker demo can now produce a visible localized video artifact while audio replacement and advanced styling remain later work.

## 2026-06-26

Decision: Add model-call audit records and configurable cost estimates before the frontend.

Reason: Job detail needs durable usage data before React can honestly show model calls and estimated cost.

Impact: Demo jobs now expose provider/model/status/latency and estimated cost, while budget enforcement and quality evaluation remain separate future slices.

## 2026-06-26

Decision: Build the first React demo as a Vite work surface that consumes existing backend APIs through a same-origin `/api` proxy.

Reason: The backend already exposes upload, job detail, retry, transcript, subtitle, artifact, cost, and model-call data. Reusing those APIs keeps the frontend slice focused on demo usability instead of expanding backend query scope.

Impact: The browser demo tracks recent jobs in local storage until a server-side job list endpoint is added. Docker Compose now includes a `linguaframe-frontend` service that proxies `/api` to the backend container.

## 2026-06-26

Decision: Keep the local demo job history global until authentication and ownership exist.

Reason: The current self-hosted demo has no user model, so adding owner scoping now would create fake authorization semantics. A global `GET /api/jobs` list is enough for local demo discovery and keeps the API honest about current product boundaries.

Impact: The React demo now uses server-backed job history for discoverability, while browser-local recent jobs remain a fallback convenience. Hosted usage must add authentication and owner-scoped queries before exposing user media publicly.

## 2026-06-27

Decision: Make translation quality evaluation an optional, non-blocking pipeline stage with its own durable domain record.

Reason: Evaluation is useful product feedback, but it should not prevent transcript, subtitle, TTS, burn-in, and artifact outputs from completing when a provider fails or is not configured. A dedicated table keeps quality outcomes queryable without overloading model-call audit records.

Impact: The worker can run deterministic demo evaluation or opt-in OpenAI evaluation after target subtitle export. Provider failures create a failed `quality_evaluations` row and safe error summary while the localization job continues.

## 2026-06-27

Decision: Add a per-job cost budget guard based on recorded estimated cost before adding next-call cost forecasting.

Reason: LinguaFrame already persists model-call cost estimates per job. Checking that accumulated total before each AI stage gives the local OpenAI demo an understandable spending guard without pretending to know exact provider billing or future token/audio usage.

Impact: Operators can opt in with `LINGUAFRAME_COST_BUDGET_GUARD_ENABLED=true` and a positive `LINGUAFRAME_COST_MAX_JOB_COST_USD`. When the limit is reached, later AI stages fail before provider calls and the job timeline records the budget failure.

## 2026-06-27

Decision: Compute generated artifact SHA-256 fingerprints at artifact creation time.

Reason: `JobArtifactServiceImpl` receives the exact bytes that are stored in object storage, so hashing at this boundary gives a stable reproducibility signal without coupling hash logic to FFmpeg, OpenAI, transcription, translation, TTS, or evaluation providers.

Impact: Artifact APIs and the React demo can show `contentSha256` for generated outputs. This creates the foundation for future duplicate-work detection while deliberately leaving cache hits and provider-call skipping to later feature slices.

## 2026-06-27

Decision: Add artifact-level cache hits before provider-level prompt or response caching.

Reason: LinguaFrame already stores durable generated artifacts and content hashes, so reusing stable media artifacts is the smallest complete duplicate-work feature that is visible in the API, timeline, and React demo. Provider-level transcription and translation caching needs prompt-version, model, and input-key semantics that should be designed separately.

Impact: Repeat jobs for the same source video and target language can reuse extracted audio, dubbing audio, and subtitle-burned video artifacts without rewriting object storage bytes. Worker summaries remain regenerated because they include the current job id and generation timestamp.

## 2026-06-27

Decision: Add a read-only in-code prompt template registry before database-backed prompt management.

Reason: The current model-call audit path already records `promptVersion`, but the active prompt text lived inside OpenAI provider classes. A small registry makes translation and evaluation prompts inspectable in API/UI without introducing admin mutation, prompt experiments, or persistence concerns before they are needed.

Impact: OpenAI translation and quality evaluation providers now consume active prompt templates from the registry, and the React demo can show which prompt versions are active. Prompt editing, A/B testing, and historical prompt storage remain future slices.

## 2026-06-27

Decision: Add translation provider caching before broader provider response caching.

Reason: Subtitle translation has a stable text input, target language, provider, model, and prompt version boundary, so it can safely skip duplicate provider calls without handling binary audio, generated media bytes, or evaluation-specific semantics. This directly builds on the prompt-template registry and artifact cache while keeping the slice testable.

Impact: Repeat compatible translation inputs now reuse stored translated subtitle segments, create fresh subtitle artifacts for the current job, and expose provider cache hits in job timeline, job cache summary, and the React demo. Transcription, TTS, quality evaluation, and generic prompt-response caches remain future work.

## 2026-06-27

Decision: Add count-only safe input and output summaries to model-call audit records.

Reason: Job detail should explain what each provider call processed without persisting raw transcript text, translated subtitle text, TTS text, OpenAI payloads, secrets, media bytes, or local media paths. Counts and short verdict metadata are enough for demo observability while preserving the existing privacy boundary.

Impact: Transcription, translation, quality evaluation, and TTS model calls now expose `inputSummary` and `outputSummary` in backend job detail and the React model-call panel. Summaries are nullable for older records and capped at 512 characters before persistence.

## 2026-06-27

Decision: Split worker roles with stage-aware RabbitMQ handoff messages before adding Kubernetes or autoscaling.

Reason: LinguaFrame needs to prove that CPU-bound FFmpeg work and API-bound OpenAI work can run in separate processes using the current codebase and local Docker stack. Stage handoff messages are enough to demonstrate the production boundary without introducing orchestration concerns too early.

Impact: The default local backend remains a `COMBINED` worker for simple demos. Optional `FFMPEG` and `OPENAI` worker roles can process contiguous stage segments through separate queues, and jobs remain `PROCESSING` across handoffs until the final segment completes.

## 2026-06-27

Decision: Add an optional owner-only demo access token before building JWT users.

Reason: LinguaFrame needs a private hosted demo gate now, but a real user model would require owner-scoped media, artifact authorization, account lifecycle, and token refresh semantics. A single configured demo token protects `/api/**` without pretending to be production authentication.

Impact: Local development remains open when `LINGUAFRAME_DEMO_ACCESS_TOKEN` is empty. Private demos can set the token in `.env`; the React UI stores the entered token for fetch APIs and writes a same-site cookie so EventSource, artifact downloads, audio previews, and video previews continue to work.

## 2026-06-27

Decision: Add retention cleanup as a default-off, dry-run-first operator workflow before public hosted usage.

Reason: LinguaFrame stores uploaded videos and generated artifacts in object storage, so a private demo needs a bounded cleanup path before it is useful outside local development. Keeping the policy disabled and dry-run by default avoids accidental deletion during normal demos.

Impact: Operators can preview and run cleanup for terminal jobs only. Cleanup removes object storage data before database rows, preserves shared source videos when another job still references them, and exposes only aggregate count results.

## 2026-06-27

Decision: Add TTS provider caching after artifact and translation provider caching.

Reason: TTS has a stable compatibility boundary of ordered target-subtitle text, language, provider, model, and voice. Caching at this boundary avoids repeat OpenAI TTS calls across compatible jobs without trying to solve transcription, quality evaluation, or generic prompt-response caching too early.

Impact: Dubbing audio generation still checks same-video artifact reuse first. On a TTS provider cache hit, the worker skips budget/provider execution, writes a fresh `DUBBING_AUDIO` artifact for the current job, and exposes the hit through existing provider cache-hit timeline and job summary fields.

## 2026-06-27

Decision: Add Redis-backed upload rate limiting before broader authentication or public multi-user controls.

Reason: LinguaFrame already runs Redis in the local and Docker stack, but Redis was not yet part of a real backend request path. Upload and upload-validation requests are the highest-risk unauthenticated demo entry points, so a small fixed-window limiter creates a concrete hosted-demo safety feature without introducing user accounts or billing.

Impact: Operators can opt in with `LINGUAFRAME_RATE_LIMIT_ENABLED=true`. The limiter applies only to upload `POST` routes, hashes client identities before writing Redis keys, returns structured `429 RATE_LIMIT_EXCEEDED` responses, and fails open by default when Redis is unavailable.

## 2026-06-27

Decision: Add Redis as a short-lived cache-aside layer for job detail snapshots while keeping MySQL as the source of truth.

Reason: `GET /api/jobs/{jobId}` and SSE progress polling repeatedly rebuild the same sanitized `LocalizationJobVo` shape from durable tables. A small Redis cache demonstrates the planned job-status cache path without changing persistence semantics or introducing Redis pub/sub before it is needed.

Impact: Job detail reads can return cached snapshots from `linguaframe:job-status:<jobId>` for a short TTL, then fall back to database reads on misses or Redis/JSON failures. Retry, cancel, and worker status transitions evict the affected job key so stale snapshots do not survive state changes.

## 2026-06-27

Decision: Add a local private-demo preflight before running media uploads or provider-backed demo jobs.

Reason: The private demo now depends on Docker Compose services, optional access-token gating, local `.env` values, frontend/backend reachability, and optional local media files. A preflight script catches environment and readiness problems before a user spends time or OpenAI credits on a demo run.

Impact: Operators can run `scripts/demo/private-demo-preflight.sh` after starting Compose to verify required commands, Compose rendering, backend health, frontend reachability, optional token-gate behavior, and configured sample paths without uploading media or calling paid providers.

## 2026-06-27

Decision: Store TTS voice selection on each localization job and include it in TTS cache identity.

Reason: Voice is part of the user's requested output, not just a runtime knob. A job-level value makes upload responses, job detail, split-worker dispatch messages, provider requests, and cache lookups agree even when the global default voice changes later.

Impact: Jobs without a selected voice still use the configured provider default. Jobs with `ttsVoice` use that value for OpenAI TTS requests and TTS provider cache keys, so two jobs with the same subtitles but different voices do not share audio.

## 2026-06-27

Decision: Enforce failed-job retry limits in the backend service, not the browser.

Reason: Retry creates durable state transitions and dispatch events, so the backend must be the source of truth for whether another attempt is allowed. The frontend should display retry count and structured errors, but it must not predict the limit from potentially stale job detail.

Impact: `POST /api/jobs/{jobId}/retry` rejects jobs whose `retryCount` has reached `linguaframe.worker.max-retries` before mutating job state or enqueueing dispatch. The selected job remains visible in the React UI when the backend returns the conflict.

## 2026-06-27

Decision: Compute operator dashboard metrics read-only from existing durable tables.

Reason: The current demo needs browser-visible health, failures, cost, and cache signals, but a new reporting schema or mutable admin surface would add operational semantics the product does not have yet.

Impact: `GET /api/operator/dashboard` aggregates jobs, model calls, artifacts, and cache-hit timeline events on demand. It is protected by the existing demo access gate when configured and deliberately does not add queue purge, retention execution, billing, or user administration.

## 2026-06-27

Decision: Keep browser demo readiness read-only and configuration-derived.

Reason: The private demo needs visible readiness evidence before upload, but live probes, FFmpeg execution, object writes, and provider calls would make opening the page slow, stateful, or potentially paid.

Impact: `GET /api/runtime/dependencies` now returns safe readiness fields for the React panel, including demo gate state, worker mode, media limits, FFmpeg toggles, provider modes, and feature flags. It still excludes secrets, tokens, raw local media paths, and workspace paths.

## 2026-06-27

Decision: Generate job artifact ZIP bundles on demand instead of storing archive artifacts.

Reason: A result bundle is a demo export convenience over existing durable artifacts, not a new pipeline output. Persisting ZIPs would duplicate object storage, complicate retention, and create another artifact type to cache or invalidate.

Impact: `GET /api/jobs/{jobId}/artifacts/archive/download` streams a ZIP containing generated artifacts plus a safe manifest. The archive excludes source videos, secrets, tokens, raw local paths, and storage credentials, and it does not create object storage or database rows.

## 2026-06-27

Decision: Expose retention cleanup in the browser as a manual, confirmation-gated operator action.

Reason: The private demo needs a visible way to manage terminal jobs and stored artifacts without terminal-only curl commands, but cleanup can delete durable data when retention is enabled and dry-run is disabled.

Impact: The React demo can preview cleanup counts and manually run cleanup through the existing backend endpoints. The browser does not schedule cleanup, does not bypass backend retention flags, and requires a confirmation prompt before calling the run endpoint.

## 2026-06-27

Decision: Expose budget guard readiness as safe configuration and add a repeatable demo failure script.

Reason: The cost budget guard is only useful in a private OpenAI demo if an operator can see the active limit before upload and produce terminal evidence that the guard blocks later AI stages. The runtime endpoint should expose only booleans and local estimate limits, not provider prices, raw usage payloads, API keys, or billing data.

Impact: `GET /api/runtime/dependencies` now includes budget guard state, max per-job estimated cost, and estimated-cost tracking status. The React readiness panel shows those fields, and `scripts/demo/docker-e2e-budget-guard.sh` verifies the guard by expecting a controlled `FAILED` job with a budget-exceeded reason.

## 2026-06-27

Decision: Add quality evaluation provider caching as structured evaluation-result reuse with fresh current-job persistence.

Reason: Quality evaluation has a stable compatibility boundary of source transcript hash, target subtitle hash, target language, provider, model, and prompt version. Caching at this boundary skips duplicate evaluation provider calls while preserving a current-job quality evaluation row and avoiding raw provider payloads, local paths, object keys, secrets, and uploaded media bytes in cache identity.

Impact: Repeat compatible quality evaluation inputs now reuse stored structured evaluation results, create fresh `quality_evaluations` rows for the current job, and expose the hit through existing provider cache-hit timeline and job summary fields. Generic prompt-response caching remains future work.

## 2026-06-27

Decision: Add transcription provider caching as transcript-segment reuse, not raw audio artifact reuse.

Reason: Transcription has a stable compatibility boundary of extracted-audio hash, provider, model, and prompt/version. Caching at this boundary can skip duplicate speech-to-text provider calls while preserving fresh job artifacts and avoiding raw audio bytes, object keys, local paths, provider payloads, or secrets in cache identity.

Impact: Repeat compatible transcription inputs now reuse stored transcript segments, create fresh transcript/SRT/VTT artifacts for the current job, and expose the hit through existing provider cache-hit timeline and job summary fields. Raw extracted-audio artifact reuse remains the artifact cache's responsibility.

## 2026-06-27

Decision: Generate job diagnostics reports on demand as metadata-only JSON.

Reason: Demo failures and successful runs need shareable debugging evidence, but copying multiple API responses risks exposing object storage keys, local paths, raw transcript or subtitle text, provider payloads, credentials, or uploaded media bytes.

Impact: `GET /api/jobs/{jobId}/diagnostics/download` returns sanitized job detail plus artifact metadata and hashes without adding persistence. The React demo links to it from the selected job header, and demo scripts download and validate the JSON report for success, retry, and budget-guard paths.

## 2026-06-28

Decision: Generate backend demo evidence Markdown on demand from the sanitized diagnostics surface.

Reason: Demo reviewers need a readable, shareable report that can be downloaded from the API and reproduced by scripts, while browser-only evidence is tied to already-loaded frontend state and diagnostics JSON is optimized for machines.

Impact: `GET /api/jobs/{jobId}/evidence/markdown/download` returns a text/markdown attachment with job status, timeline, usage, cache, quality, artifact hash, result bundle, and diagnostics links. The report is not persisted as an artifact and excludes raw transcript text, raw subtitle text, object keys, local paths, tokens, provider payloads, and media bytes.

## 2026-06-28

Decision: Add an explicit disabled-by-default OpenAI connectivity probe to runtime live checks.

Reason: Private demos with real OpenAI credentials need a quick way to prove base URL, API key, and model access before uploading media or starting a paid provider-backed job. Running the check only when explicitly enabled keeps normal local startup deterministic and cost-free.

Impact: `GET /api/runtime/live-checks`, the React `Live checks` panel, and private-demo preflight now include an `openai` probe. It reports `SKIPPED` by default and only calls the model metadata endpoint when enabled, without exposing API keys, bearer headers, raw provider responses, or request payloads.

## 2026-06-28

Decision: Generate demo evidence bundles on demand as metadata-only ZIP archives.

Reason: A private demo needs a single shareable proof package, but the existing artifact archive is for generated media deliverables and should not mix in audit files. The evidence bundle should be reproducible from backend-safe state and script-verifiable without reading object storage bytes.

Impact: `GET /api/jobs/{jobId}/evidence/bundle/download` returns a ZIP with `manifest.json`, `evidence.md`, and `diagnostics.json`. It is not persisted as an artifact and excludes generated media bytes, uploaded media bytes, raw transcript text, raw subtitle text, object keys, local paths, tokens, credentials, and provider payloads.

## 2026-06-28

Decision: Treat translation style as part of the localization job identity.

Reason: A natural, formal, or concise translation can produce different subtitles from the same transcript. Keeping the style only in the browser would make provider prompts, cache hits, evidence, and reruns ambiguous.

Impact: `translation_style` is stored on `localization_jobs`, defaults to `NATURAL`, flows through upload responses, job list/detail, dispatch messages, OpenAI translation payloads, safe model-call summaries, browser demo state, and demo scripts. Translation provider cache keys include the style so jobs with different style choices cannot reuse each other's translated subtitle provider results.

## 2026-06-28

Decision: Keep subtitle burn-in customization as preset-based job input.

Reason: The demo needs visible control over generated burned videos, but a free-form subtitle style editor would expand the UX, validation, and FFmpeg escaping surface before the product needs it. Presets give predictable output and make artifact cache identity explicit.

Impact: Uploads can choose `STANDARD`, `LARGE`, or `HIGH_CONTRAST`. The selected preset is persisted on the job, carried through dispatch and worker execution, applied to FFmpeg `force_style`, shown in safe browser/terminal evidence, and included in `BURNED_VIDEO` artifact cache reuse.

## 2026-06-28

Decision: Treat upload glossaries as bounded job input metadata, not a full terminology-management subsystem.

Reason: Demo operators need predictable handling for names, product terms, and sci-fi vocabulary, but LinguaFrame does not need reusable account-level glossaries yet. Keeping the glossary on the job makes the OpenAI payload, cache identity, evidence, and replay behavior explicit without adding ownership or lifecycle complexity.

Impact: Uploads accept up to 20 `source => target` or `source = target` mappings. The backend stores normalized JSON plus hash/count, sends entries to translation providers, includes the hash in translation cache keys, and exposes only safe metadata in browser, backend evidence, delivery, handoff, AI audit, worker summary, and demo run packages.

## 2026-06-28

Decision: Add a private demo owner-session API before building public authentication.

Reason: The browser demo needs a clear login/logout affordance for the project owner, but LinguaFrame still has no user model, owner-scoped media, account lifecycle, JWT refresh, or billing semantics. Reusing the configured demo access token keeps the private demo honest without pretending to be a public hosted service.

Impact: `/api/demo-session` exposes sanitized gate state, `/api/demo-session/login` sets the same-site `LinguaFrame-Demo-Token` cookie after validating the configured token, and `/api/demo-session/logout` clears it. Other `/api/**` routes remain protected when the demo gate is enabled, while Swagger, curl, and scripts can keep using `X-LinguaFrame-Demo-Token`.

## 2026-06-28

Decision: Treat demo run profiles as read-only presets instead of cache identity or editable experiments.

Reason: Profiles should make repeated demos faster and easier to compare, but they should not fork provider behavior when the resolved upload settings are equivalent. Keeping the catalog fixed also avoids turning the demo into a prompt/profile management product.

Impact: `GET /api/demo-run-profiles`, the React upload selector, and `LINGUAFRAME_DEMO_PROFILE_ID` expose the same built-in presets. Uploads persist `demoProfileId` for job detail, evidence, delivery manifests, handoff packages, demo run packages, worker summaries, and session reports, while provider/artifact cache keys continue to use target language, style, glossary, subtitle style, and polishing mode.

## 2026-06-28

Decision: Use a separate OpenAI demo profile instead of making `.env.example` provider-backed.

Reason: The project needs a credible real-API demo path, but the default local demo must stay deterministic, cheap, and runnable without credentials. A separate no-secret template keeps paid behavior explicit and makes preflight responsible for proving credentials and model access before upload.

Impact: `.env.openai-demo.example`, `scripts/demo/openai-demo-preflight.sh`, and `scripts/demo/docker-e2e-openai-smoke.sh` define the recommended real OpenAI proof path. Existing deterministic scripts remain the default, while terminal E2E helpers now support the private demo token header when a gate is configured.

## 2026-06-28

Decision: Keep failed-job triage advisory instead of changing retry semantics.

Reason: Retry transitions already have backend ownership, bounded retry counts, cache eviction, and dispatch side effects. The next demo gap is explaining likely causes and next actions safely across browser, diagnostics, evidence, and terminal scripts, not adding another automatic recovery path.

Impact: Failed or cancelled job detail can include `failureTriage` with category, retryability, summary, recommended action, static runbook command, and safe details. The same structure flows through diagnostics JSON, backend Markdown evidence, browser evidence export, and script summaries without exposing secrets, object keys, local paths, provider payloads, raw transcript/subtitle text, or media bytes.

## 2026-06-28

Decision: Treat subtitle polishing as an explicit upload-time pipeline stage instead of hiding it inside translation.

Reason: Demo runs need to prove when subtitles were only translated versus when an extra model call refined readability. Keeping polishing separate also prevents surprise paid calls when the default mode is `OFF`.

Impact: Jobs store `subtitlePolishingMode`, the worker runs `SUBTITLE_POLISHING` after target subtitle export only for enabled modes, OpenAI/demo providers record separate `SUBTITLE_POLISHING` model-call audits, and provider cache keys include the selected mode before quality evaluation, TTS, burn-in, review, and handoff evidence consume the final subtitles.

## 2026-06-28

Decision: Store subtitle corrections as a draft overlay separate from generated subtitle artifacts.

Reason: Human review needs editable corrected subtitles, but saving a text correction should not imply that LinguaFrame has regenerated TTS audio, burned video, artifact cache entries, or provider outputs.

Impact: `/api/jobs/{jobId}/subtitle-draft` persists per-segment draft text and corrected JSON/SRT/VTT exports read from the overlay. Existing generated target subtitle artifacts remain unchanged, and evidence exports report draft counts and timestamps without raw corrected text.

## 2026-06-28

Decision: Publish reviewed subtitles as explicit handoff artifacts instead of mutating generated artifacts.

Reason: A demo reviewer needs downloadable corrected outputs after editing subtitles, but generated provider outputs, cache evidence, and the original burned video should remain auditable and unchanged.

Impact: `POST /api/jobs/{jobId}/subtitle-draft/publish` creates reviewed JSON/SRT/VTT artifacts from the current draft overlay and can optionally create a separate reviewed burned video. Artifact lists, archives, browser result delivery, diagnostics, evidence, and terminal scripts expose reviewed artifact metadata without raw corrected subtitle text.

## 2026-06-28

Decision: Derive delivery handoff manifests on demand from durable job and artifact state.

Reason: A demo handoff needs one readable checklist, but persisting another artifact would duplicate state already represented by jobs, reviewed artifacts, result bundles, diagnostics, and evidence bundles.

Impact: `/api/jobs/{jobId}/delivery-manifest` and `/api/jobs/{jobId}/delivery-manifest/markdown/download` report handoff readiness, reviewed artifacts, audit artifacts, hashes, and safe verification links without storing new media, embedding raw subtitles, or exposing object keys and local paths.

## 2026-06-28

Decision: Generate reviewed handoff packages on demand from existing safe demo surfaces.

Reason: The final demo handoff needs one downloadable package for reviewed deliverables, but it should not persist another artifact row or mix internal generated audit outputs with reviewer-facing files.

Impact: `/api/jobs/{jobId}/handoff-package/download` returns a ZIP with a package manifest, delivery manifest, diagnostics, evidence report, reviewed subtitle artifacts, and optional reviewed burned video. It excludes source uploads, generated transcript/target subtitle artifacts, generated burned video, worker summaries, object keys, local paths, provider payloads, demo tokens, and credentials.

## 2026-06-28

Decision: Generate a complete demo run package on demand from existing safe evidence surfaces.

Reason: The demo now has multiple proof artifacts, including diagnostics, evidence Markdown, quality evidence, delivery manifests, handoff checklists, and session reports. Reviewers need one ZIP workspace for a single run without mixing in media bytes or internal storage identifiers.

Impact: `/api/jobs/{jobId}/demo-run-package/download` returns a metadata-only ZIP with `manifest.json`, `README.md`, `job-detail.json`, `diagnostics.json`, `evidence.md`, `quality-evidence.md`, `delivery-manifest.md`, `demo-handoff-checklist.md`, and `demo-session-report.md`. Browser and terminal demos expose the download, and scripts validate fixed entries plus forbidden sensitive markers.

## 2026-06-28

Decision: Generate a focused AI audit package on demand from safe job model-call records and active prompt templates.

Reason: The full demo run package explains the whole run, but AI infrastructure review needs a narrower package that ties prompt versions, active templates, model calls, usage, latency, and estimated cost together without requiring a reviewer to cross-check multiple panels.

Impact: `/api/jobs/{jobId}/ai-audit-package/download` returns a metadata-only ZIP with `manifest.json`, `README.md`, `model-calls.json`, `prompt-templates.json`, `ai-usage-summary.json`, and `ai-audit-report.md`. Browser and terminal demos expose the download, and scripts validate fixed entries plus forbidden sensitive markers.

## 2026-06-28

Decision: Treat source media metadata as safe evidence but keep storage object keys internal.

Reason: A reviewer needs to verify the input video that produced the demo outputs, but object keys and local paths are implementation details that should not leak into browser or terminal evidence.

Impact: `GET /api/media/uploads/{videoId}` returns safe source metadata without `sourceObjectKey`, while `GET /api/media/uploads/{videoId}/source/download` streams the source bytes only through an explicit route. Browser and terminal demos expose source media evidence without embedding source media bytes in metadata packages.
