# Decision Log

This file records project-level decisions that affect future implementation. Feature-specific decisions should also be recorded in the relevant ExecPlan under `docs/plans/`.

## 2026-06-28

Decision: Add local owner-account JWT auth as a compatibility bridge instead of replacing the private-demo token or building public account management.

Reason: LinguaFrame needs a concrete step toward Stage 3 authentication, but registration, password reset, roles, billing, and database-backed users would be too much scope for the current demo path. The existing demo token also remains useful for Swagger, curl, scripts, downloads, previews, and SSE.

Impact: `/api/auth/session`, `/api/auth/login`, and `/api/auth/logout` expose sanitized local account auth; protected APIs accept either a valid bearer token or the existing demo token/cookie. Browser and terminal flows can verify bearer auth without exposing passwords, JWT secrets, bearer tokens, or demo tokens.

## 2026-06-28

Decision: Add the private demo run archive as a derived metadata index instead of persisting archive records or embedding package contents.

Reason: After a private demo, the owner needs one table of contents for readiness state, launch status, recommended completed run, and package routes. Persisting another archive model or copying ZIP/media contents would duplicate existing job, gallery, presenter-pack, and package evidence.

Impact: `GET /api/operator/private-demo/run-archive`, the browser panel, and `scripts/demo/private-demo-run-archive.sh` compose existing safe metadata into JSON/Markdown reports. Backups, generated media packages, per-job demo run packages, OpenAI calls, uploads, cleanup, and restore operations remain explicit separate workflows.

## 2026-06-28

Decision: Expose worker topology through read-only runtime readiness instead of changing queue routing or Docker topology.

Reason: Split-worker demos need to verify the active role, listener queue, and FFmpeg/OpenAI routes before upload, but the existing worker role and RabbitMQ configuration already control execution behavior.

Impact: `GET /api/runtime/dependencies`, the browser `Demo readiness` panel, `scripts/demo/upload-readiness.sh`, and `scripts/demo/private-demo-preflight.sh` now show metadata-only worker topology, owned stages, and safe startup commands without exposing credentials, tokens, local paths, provider payloads, transcript text, subtitle text, or media bytes.

## 2026-06-28

Decision: Add pre-upload readiness as a safe aggregate workspace instead of folding all checks into file validation or upload creation.

Reason: The owner needs one go/no-go view before a real demo upload, but runtime health, owner quota, profile selection, and provider warnings have different sources of truth. Keeping readiness read-only avoids creating jobs, writing storage objects, or spending provider credits while still explaining whether upload should proceed.

Impact: `GET /api/media/uploads/readiness`, the browser `Upload readiness` panel, and `scripts/demo/upload-readiness.sh` provide metadata-only readiness checks. File validation remains per selected file, and the upload endpoint remains the only path that persists media or creates jobs.

## 2026-06-28

Decision: Keep private demo launch rehearsal read-only and command-oriented instead of auto-running deployment, backup, restore, OpenAI, upload, or cleanup steps.

Reason: A launch rehearsal should answer whether the owner can safely present the demo and what manual step comes next. Automatically starting services, spending provider credits, exporting backups, restoring data, or deleting retention candidates would mix evidence collection with operational mutation.

Impact: `GET /api/operator/private-demo/launch-rehearsal`, the browser panel, and `scripts/demo/private-demo-launch-rehearsal.sh` produce metadata-only go/no-go notes and safe command templates. Actual Docker startup, OpenAI preflight, upload, backup, restore, and cleanup remain explicit operator commands.

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

## 2026-06-30

Decision: Add deterministic narration row editing commands before decoded waveform rendering or multitrack automation.

Reason: Operators need fast authoring operations while shaping explanatory voiceover scripts, but decoded waveform rendering, audio analysis, automation curves, and full nonlinear editing would expand the surface before the current save/generate/render flow is stable. Duplicate, split-at-playhead, merge-next, and insert-after commands give immediate editing value over the existing narration workspace contract.

Impact: The React narration workspace now exposes local-only editing commands that update draft rows, selected row state, timeline, waveform, inspector, validation, and the existing save payload. Commands do not save rows, call providers, synthesize audio, generate videos, refresh evidence, or mutate object storage. Inserted blank rows intentionally block save until existing validation requirements pass. Decoded audio waveform rendering, multitrack automation, uploaded reference audio, and voice cloning remain future slices.

## 2026-06-30

Decision: Add local narration media preview and playhead controls before waveform rendering.

Reason: Operators need to verify where explanatory voiceover will land against the completed media before spending provider credits or generating new artifacts. Existing source and artifact download URLs are enough for this check, while waveform decoding and multitrack automation would expand scope without changing the saved narration contract.

Impact: The browser narration workspace now previews the best available media in priority order: `NARRATED_VIDEO`, `BURNED_VIDEO`, then source video. Jump and play-window controls update local preview state and timeline playhead only; they do not save narration rows, call OpenAI, synthesize audio, generate videos, or mutate object storage.

## 2026-06-30

Decision: Add a metadata-derived narration waveform overview before decoded audio waveform rendering.

Reason: Operators need a denser overview of narration coverage and silence while authoring scripts, but real waveform decoding would require generated audio, browser audio analysis, or a backend waveform route. Deriving buckets from timing and text density gives immediate editing value before TTS spend while preserving the current saved narration contract.

Impact: The React narration workspace now shows deterministic waveform-style buckets, selected-window overlay, active/gap counts, and local scrub controls. Scrubbing seeks only the browser preview player and updates local playhead state; it does not save rows, call providers, synthesize audio, generate videos, refresh evidence, or mutate object storage. Real decoded-audio waveform rendering and multitrack automation remain later work.

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

Decision: Keep private-demo evidence gallery read-only and derived from existing job/package evidence instead of adding persistent curation state.

Reason: After a private demo, the owner needs a fast way to select completed runs and safe handoff packages, but storing another curated gallery would duplicate job, manifest, presenter pack, evidence, and audit state. A derived view keeps the feature reversible and avoids accidental mutation after presentation.

Impact: `GET /api/operator/private-demo/evidence-gallery`, the browser panel, and `scripts/demo/private-demo-evidence-gallery.sh` aggregate recent completed jobs, handoff readiness, recommended run selection, and safe package routes without uploading media, calling providers, publishing subtitles, copying media bytes, or exposing secrets and raw text.

## 2026-06-28

Decision: Separate the private demo access token from the persisted demo owner identity.

Reason: The private demo needs owner-scoped media and job state before public authentication exists, but the access token is only an entry gate and should not become a user id, account model, or billing identity.

Impact: `LINGUAFRAME_DEMO_OWNER_ID` defaults to `demo-owner` and is persisted on uploaded videos and localization jobs. Owner-facing media and job reads use that configured identity, `/api/demo-session` exposes only sanitized owner metadata, and worker/operator maintenance paths keep explicit internal access where needed.

## 2026-06-28

Decision: Use configured owner quota preflight as a private-demo guard, not as real billing or account management.

Reason: LinguaFrame needs a credible way to stop runaway hosted demos before storage, queue, FFmpeg, or OpenAI work, but public authentication, tenant administration, and authoritative billing are later-stage product work. Reusing the configured demo owner keeps the guard simple and consistent with existing owner-scoped media/job APIs.

Impact: `GET /api/media/uploads/preflight` reports safe owner pressure, `POST /api/media/uploads` rejects over-quota uploads before side effects, runtime readiness exposes sanitized quota settings, the React upload form shows the current owner quota state, and `scripts/demo/owner-quota-preflight.sh` provides a terminal stop before paid or full-video demos.

## 2026-06-28

Decision: Use a separate OpenAI demo profile instead of making `.env.example` provider-backed.

Reason: The project needs a credible real-API demo path, but the default local demo must stay deterministic, cheap, and runnable without credentials. A separate no-secret template keeps paid behavior explicit and makes preflight responsible for proving credentials and model access before upload.

Impact: `.env.openai-demo.example`, `scripts/demo/openai-demo-preflight.sh`, and `scripts/demo/docker-e2e-openai-smoke.sh` define the recommended real OpenAI proof path. Existing deterministic scripts remain the default, while terminal E2E helpers now support the private demo token header when a gate is configured.

## 2026-06-28

Decision: Treat the local account bearer token as an owner workspace boundary, not just a login indicator.

Reason: The previous bridge proved that one configured account could receive a JWT, but product safety depends on every owner-facing read resolving through the active owner identity. Adding public accounts or roles remains out of scope, but the private demo needs credible isolation for jobs, media, artifacts, diagnostics, evidence, and operator views.

Impact: Bearer auth and demo-token compatibility now scope job history/detail, media metadata/source downloads, artifact list/download/archive, diagnostics/evidence, and operator dashboard recent rows to the active owner. Browser UI shows auth ownership scope and refreshes history on sign-in/sign-out, while `scripts/demo/owner-workspace-smoke.sh` verifies the bearer owner workspace without printing secrets or media internals.

## 2026-06-28

Decision: Keep demo share sheets as generated metadata views instead of another persisted artifact or package.

Reason: Reviewers need one compact link-and-outcome note for a selected run, while the existing demo run package, handoff package, evidence bundle, and presenter pack already own detailed files and ZIP workspaces.

Impact: `/api/jobs/{jobId}/demo-share-sheet` and `/api/jobs/{jobId}/demo-share-sheet/markdown/download` derive readiness, summary, outcome bullets, next action, and curated links on demand. Browser and terminal exports stay metadata-only and exclude raw transcript text, subtitle text, object keys, local paths, provider payloads, tokens, credentials, and media bytes.

## 2026-06-28

Decision: Derive the live demo run monitor from existing job detail and pipeline progress instead of persisting another runtime state table.

Reason: The monitor is observability for demos, not an execution controller. Job rows, dispatch state, and timeline-derived pipeline progress already carry the durable truth needed to show current stage, elapsed time, slowest stage, stale-stage attention, and next action.

Impact: `/api/jobs/{jobId}/demo-run-monitor` and `/api/jobs/{jobId}/demo-run-monitor/markdown/download` provide metadata-only JSON/Markdown for browser and terminal use. The monitor does not retry, cancel, dispatch, upload, call providers, expose raw media text, or reveal object keys, local paths, tokens, credentials, or provider payloads.

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

Decision: Store TTS dubbed video as a separate generated artifact.

Reason: A localized demo needs a playable video with target subtitles and generated dubbing audio, but the existing generated `BURNED_VIDEO` and optional `REVIEWED_BURNED_VIDEO` carry different audit and handoff meanings.

Impact: The worker creates `DUBBED_VIDEO` only when `DUBBING_AUDIO` and generated `BURNED_VIDEO` are both available. Browser media delivery, terminal summaries, manifests, and demo packages count it as generated media, while reviewed artifacts remain a separate human-handoff surface.

## 2026-06-28

Decision: Treat source media metadata as safe evidence but keep storage object keys internal.

Reason: A reviewer needs to verify the input video that produced the demo outputs, but object keys and local paths are implementation details that should not leak into browser or terminal evidence.

Impact: `GET /api/media/uploads/{videoId}` returns safe source metadata without `sourceObjectKey`, while `GET /api/media/uploads/{videoId}/source/download` streams the source bytes only through an explicit route. Browser and terminal demos expose source media evidence without embedding source media bytes in metadata packages.

## 2026-06-29

Decision: Generate demo run snapshots on demand as static metadata-only reviewer workspaces.

Reason: The demo now has a browser share sheet, run monitor, presenter pack, delivery manifest, diagnostics, and evidence outputs. A reviewer needs one offline folder with an HTML entry point without creating another persisted artifact or exposing source/generated media.

Impact: `/api/jobs/{jobId}/demo-run-snapshot` previews readiness, sections, entries, links, and exclusion policy. `/api/jobs/{jobId}/demo-run-snapshot/download` returns a ZIP with `index.html`, `manifest.json`, `README.md`, share sheet, run monitor, presenter pack JSON, delivery manifest, diagnostics, and evidence. Browser and terminal demos expose the package, while scripts validate required entries and forbidden sensitive markers.

## 2026-06-29

Decision: Expose demo sample media as a read-only catalog instead of auto-downloading public videos.

Reason: The demo needs repeatable public media choices with attribution and local availability checks, but the app should not mutate local files, fetch remote media, upload samples, or reveal the owner's filesystem.

Impact: `/api/operator/demo-sample-media-catalog`, the browser `Demo sample media` panel, and `scripts/demo/demo-sample-media-catalog.sh` show sample recommendations, source/license guidance, upload duration limits, safe commands, and sanitized configured-path status. The catalog reports basenames, extensions, sizes, and status only; it does not expose full local paths or perform downloads.

## 2026-06-29

Decision: Add a read-only demo run launcher instead of auto-running the full public sample demo.

Reason: The project needs a repeatable bridge from sample selection to full-demo execution, but starting Docker, uploading media, or calling OpenAI from an operator metadata endpoint would hide cost and state changes.

Impact: `/api/operator/demo-run-launcher`, the browser `Demo run launcher` panel, and `scripts/demo/demo-run-launcher.sh` show the recommended sample/profile command, readiness gates, and expected evidence files. The launcher remains metadata-only and does not upload media, start Docker, edit `.env`, call OpenAI, or expose full local paths.

## 2026-06-29

Decision: Generate demo replay cards on demand from existing job evidence instead of storing rerun recipes.

Reason: Presenters need a reliable way to reproduce or compare a selected job, but automatically replaying uploads would hide cost, state changes, and unavailable local media paths.

Impact: `/api/jobs/{jobId}/demo-replay-card`, the browser `Demo replay card` panel, and `scripts/demo/demo-replay-card.sh` expose safe settings, recommended replay commands, baseline comparison guidance, and package links. The card is read-only, omits local source paths and secrets, and reuses job detail, run matrix, presenter pack, and evidence routes.

## 2026-06-29

Decision: Add provider voice preset selection before voice cloning or voice preview features.

Reason: Narration needs a reliable, repeatable way to choose TTS voices per segment without accepting arbitrary provider strings or expanding into custom voice cloning. Provider preset identifiers are easy to validate, cache, audit, and expose in safe evidence.

Impact: The narration workspace now returns a provider-aware voice catalog, segment saves reject unknown voice identifiers, blank voices inherit the default, React uses compact voice selects, and narration evidence reports voice preset count, voice summary, and default voice. Uploaded reference audio, voice cloning, voice preview playback, and waveform editing remain future slices.

## 2026-06-29

Decision: Add narration timeline inspection before waveform or drag editing.

Reason: Operators need to see where explanatory voiceover segments land, how much time is covered, and whether gaps or overlaps exist before spending TTS/video-generation cost. A computed timeline workbench gives demo-ready feedback without introducing a nonlinear editor surface.

Impact: The narration workspace now exposes backend-computed span, covered time, gap count/seconds, overlap state, readiness, proportional segment bars, and selected-segment diagnostics in React. Gaps are allowed as intentional silence; overlaps and invalid ranges still block save/generate actions. Waveform rendering, multitrack automation curves, and voice-cloning style controls remain future slices.

## 2026-06-29

Decision: Add explicit narration script packages before waveform or drag/drop editing.

Reason: Operators need to reuse and restore full time-coded narration scripts during demos, but a waveform editor or drag/drop timeline would broaden the product into unfinished nonlinear editing.

Impact: `GET /api/jobs/{jobId}/narration-script-package`, Markdown/ZIP downloads, `POST /api/jobs/{jobId}/narration-script-package/import`, the browser `Script package` panel, and `scripts/demo/narration-script-package.sh` export and restore operator-authored narration text, timing, voice presets, and mix settings. General narration evidence remains metadata-only and excludes script bodies; script packages may include narration text but still exclude media bytes, transcript text, subtitle text, object keys, local paths, provider payloads, credentials, and tokens.

## 2026-06-29

Decision: Generate demo completion certificates on demand from existing safe evidence surfaces.

Reason: A completed public demo needs one final proof surface, but persisting another artifact would duplicate state already represented by delivery manifests, presenter packs, replay cards, share sheets, snapshots, run matrices, and package routes.

Impact: `/api/jobs/{jobId}/demo-completion-certificate`, the browser `Demo completion certificate` panel, and `scripts/demo/demo-completion-certificate.sh` aggregate completion status, blocking checks, handoff readiness, reproducibility, evidence links, and safe next action. The certificate is read-only, metadata-only, and does not create artifacts, call providers, expose raw text, reveal local paths, or embed media bytes.

## 2026-06-29

Decision: Generate demo acceptance gates on demand from existing safe evidence surfaces.

Reason: The demo already has proof, replay, presenter, snapshot, matrix, delivery, and evidence surfaces. Before presenting, the owner still needs one concise go/no-go answer without storing another artifact or mutating the job.

Impact: `/api/jobs/{jobId}/demo-acceptance-gate`, the browser `Demo acceptance gate` panel, `scripts/demo/demo-acceptance-gate.sh`, and the full Tears script aggregate required checks, warning checks, evidence metrics, safe links, and recommended next action into `READY`, `ATTENTION`, or `BLOCKED`. The gate is read-only, metadata-only, and does not create artifacts, call providers, expose raw text, reveal local paths, or embed media bytes.

## 2026-06-29

Decision: Add a read-only demo presentation cockpit instead of turning existing demo panels into stateful launch automation.

Reason: A run-day operator needs one next-action surface before upload, during processing, and after completion, but starting Docker, uploading media, retrying jobs, creating packages, or calling OpenAI from a cockpit would hide cost and state changes.

Impact: `GET /api/operator/demo-presentation-cockpit`, the browser `Demo presentation cockpit` panel, and `scripts/demo/demo-presentation-cockpit.sh` compose launcher, upload readiness, live checks, private-demo operations, active run monitor, recommended run archive, acceptance gate, and safe evidence links. The cockpit remains metadata-only and does not upload media, start Docker, call providers, mutate jobs, create artifacts, expose local paths, print secrets, or embed raw transcript/subtitle text.

## 2026-06-29

Decision: Add a reviewed subtitle workflow cockpit as a derived read-only aggregate instead of persisting review sessions.

Reason: Subtitle review, draft edits, reviewed artifact publishing, reviewed burned-video creation, delivery manifest readiness, and handoff package export were already implemented, but reviewers still had to inspect several panels to know the next step.

Impact: `GET /api/jobs/{jobId}/reviewed-subtitle-workflow`, the browser `Reviewed subtitle workflow` panel, and `scripts/demo/reviewed-subtitle-workflow.sh` compose existing job, subtitle review, draft, artifact, and manifest state into `READY`, `ATTENTION`, or `BLOCKED` with actionable checks and safe links. The cockpit is metadata-only and does not edit drafts, publish artifacts, create media, call providers, expose raw text, reveal local paths, or embed media bytes.

## 2026-06-29

Decision: Store subtitle review annotations in the draft overlay and export review evidence as metadata-only packages.

Reason: The demo needs to prove what a reviewer accepted, edited, or marked for follow-up without turning the MVP into a multi-user review system or leaking free-form note text into handoff packages.

Impact: Draft rows now carry review decision, issue categories, and reviewer note metadata beside corrected text. `GET /api/jobs/{jobId}/subtitle-review-evidence`, Markdown download, ZIP download, the browser review-evidence panel, and `scripts/demo/subtitle-review-evidence.sh` summarize review completion, category counts, note counts, release-note length, checks, safe links, and package entries. Evidence packages exclude raw transcript text, generated subtitle text, corrected subtitle text, reviewer note bodies, local paths, object keys, provider payloads, tokens, API keys, and media bytes.

## 2026-06-29

Decision: Add time-coded custom narration as a future product goal that builds on the existing TTS pipeline.

Reason: The product should eventually support explanatory voiceover segments, such as adding narration at `00:15-00:28` and `00:55-01:10`, while preserving the current localization pipeline and reviewed subtitle handoff semantics.

Impact: Future feature slices should treat narration as operator-authored, time-coded text segments synthesized through the existing TTS provider/cache/audit boundary and exported as separate narration audio or narrated-video artifacts. It should not mutate generated subtitles, reviewed subtitles, generated burned videos, or reviewed handoff artifacts by default, and it should avoid becoming a full nonlinear video editor.

## 2026-06-29

Decision: Implement the first narration slice as separate audio plus metadata-only evidence, not narrated-video muxing.

Reason: The project needs a complete, demoable narration feature that fits the existing localization architecture without turning the app into a full video editor. Generating separate narration audio proves the TTS workflow and browser editor while keeping reviewed subtitle and media delivery semantics intact.

Impact: Narration segments are stored as job-level time-coded rows, synthesized through the existing TTS provider and budget guard, and saved as `NARRATION_AUDIO`. Narration evidence JSON/Markdown/ZIP exposes counts, timing, audio readiness, and safe links only. Generated subtitles, reviewed subtitles, `DUBBING_AUDIO`, `DUBBED_VIDEO`, burned videos, and handoff artifacts are not replaced by narration.

## 2026-06-29

Decision: Generate narrated videos as standalone artifacts without replacing existing localization outputs.

Reason: The narration workflow now needs a complete demo path from time-coded narration text to playable video, but the project still should not become a full nonlinear editor or mutate existing localization outputs.

Impact: `POST /api/jobs/{jobId}/narration-workspace/generate-video` combines the preferred available base video with existing `NARRATION_AUDIO`, stores `narrated-video.mp4` as `NARRATED_VIDEO`, and leaves dubbing, burned-video, reviewed subtitle, and handoff artifacts unchanged. A later same-day decision upgraded the implementation from simple audio replacement to fixed original-audio ducking while keeping waveform editing as future work.

## 2026-06-29

Decision: Use fixed original-audio ducking for narrated video before exposing manual mix controls.

Reason: The demo needs a reliable end-to-end narration result that preserves the base video's original audio while keeping explanatory voiceover intelligible. A fixed `0.35` ducking volume is predictable, easy to verify in backend evidence, frontend status, and terminal scripts, and avoids introducing an unfinished multitrack editor surface.

Impact: `NARRATION_AUDIO` is generated as a timed audio bed from saved narration windows, and `NARRATED_VIDEO` mixes that bed with the selected base video while ducking original/base audio to `0.35` during narration windows. Evidence surfaces report `TIMED_AUDIO_BED`, `DUCKED_ORIGINAL_AUDIO`, `timeAligned`, `duckingVolume`, and narration window count. Adjustable ducking and waveform editing remain future feature slices.

## 2026-06-29

Decision: Add narration mix controls as persisted numeric settings before waveform or multitrack editing.

Reason: The next demo improvement needs operator control over intelligibility without expanding LinguaFrame into a nonlinear editor. Numeric ducking volume, narration volume, and fade duration are easy to validate, persist, replay in FFmpeg, and verify in metadata-only evidence.

Impact: `PUT /api/jobs/{jobId}/narration-workspace/mix-settings` stores job-level mix settings with defaults `0.35`, `1.00`, and `250 ms`; narrated-video generation applies those values and evidence reports whether they came from defaults or saved settings. Waveform editing and automation curves remain future work.

## 2026-06-30 - Narration Timeline Drag Editing Workbench

Decision: Add timeline move/resize editing before waveform rendering or multitrack automation.

Reason: Operators need to correct time-coded narration windows before spending TTS or narrated-video cost, and the existing table-only workflow is too slow for demo review. A compact bar editor over the existing segment state gives the product a practical narration editor without adding audio decoding, waveform rendering, provider calls, storage writes, or a new backend route.

Impact: The React `Narration workspace` now recomputes local timeline metadata from unsaved segment state, lets selected bars move or resize by pointer or keyboard, keeps table inputs and inspector details synchronized, blocks overlap/invalid saves locally, and persists only through the existing `Save narration` API. Timeline editing is local until save and does not call OpenAI, generate narration audio/video, create artifacts, or mutate object storage. Waveform rendering, automation curves, uploaded reference audio, and voice cloning remain future slices.

## 2026-06-30

Decision: Add in-memory narration draft history before persisted editor sessions or decoded waveform editing.

Reason: Operators need a safe way to experiment with time-coded explanatory voiceover before saving rows or spending TTS/video-generation cost. Undo, redo, revert-to-saved, and unsaved-change metrics solve the immediate browser editing risk while keeping the backend narration contract, evidence routes, provider calls, and artifact generation unchanged.

Impact: The React `Narration workspace` now keeps local history snapshots around the current narration draft, shows added/removed/timing/text/voice change counts, and lets operators undo, redo, or revert to the last saved workspace. History is in-memory only and resets on workspace reload or successful save response. These controls do not call OpenAI, save rows, generate audio/video, create artifacts, mutate object storage, or add persistence schema. Decoded waveform rendering, persisted editor sessions, and multitrack automation remain future slices.

## 2026-06-30

Decision: Treat narration demo presets as explicit post-processing imports instead of upload-time automation.

Reason: Demo profiles already define localization settings at upload time, while narration scripts are operator-authored timeline rows that may replace existing workspace content. Keeping preset application as a separate confirmed step makes replacement, duration validation, voice validation, script package export, narration evidence, and later audio/video generation easy to explain and audit.

Impact: `GET /api/demo-run-profiles/narration-presets`, `GET /api/demo-run-profiles/{profileId}/narration-preset`, and `POST /api/jobs/{jobId}/narration-demo-preset/apply` provide a reusable Tears showcase script without generating media automatically. The browser panel and terminal script require explicit replacement, refresh script/evidence metadata, and keep `NARRATION_AUDIO` plus `NARRATED_VIDEO` generation as separate actions.

## 2026-06-30

Decision: Implement one-click narration demo render as orchestration over existing narration services.

Reason: A demo operator needs one command/button for the complete Tears narration showcase, but preset import, narration audio synthesis, narrated-video muxing, script package export, evidence export, and artifact listing already have separate validation and persistence rules. Duplicating that logic would make partial failures and cost warnings harder to audit.

Impact: `POST /api/jobs/{jobId}/narration-demo/render`, the browser `Render narration demo` panel, `scripts/demo/narration-demo-render.sh`, and `LINGUAFRAME_RENDER_NARRATION_DEMO=true scripts/demo/docker-e2e-tears-of-steel-full.sh` run the existing services in order. The request keeps explicit replacement and optional video generation visible. If audio succeeds but narrated-video generation fails, the response is `PARTIAL` and preserves `NARRATION_AUDIO` so the operator can retry video generation without losing paid TTS output.

## 2026-06-30

Decision: Keep narration render preflight read-only and separate from render.

Reason: Operators need a safe go/no-go surface for replacement impact, provider-cost attention, estimated narration size, generated-video readiness, evidence routes, and next command before any OpenAI TTS or FFmpeg render work runs. Folding that logic into render would make `BLOCKED` and `ATTENTION` states harder to review and would risk spending provider credits before the user sees the consequences.

Impact: `POST /api/jobs/{jobId}/narration-demo/render/preflight`, the browser `Render preflight` subsection, and `scripts/demo/narration-demo-render-preflight.sh` compose existing preset, workspace, artifact, script package, evidence, and provider configuration metadata without mutating jobs, saving narration rows, creating artifacts, dispatching workers, calling OpenAI, or touching object storage. Terminal render can require preflight with `LINGUAFRAME_NARRATION_DEMO_RENDER_PREFLIGHT_REQUIRED=true`; blocked preflight refuses render, while advisory OpenAI cost estimates remain separate from provider-side billing truth.
