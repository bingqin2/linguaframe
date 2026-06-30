# Target State

## Product Target

LinguaFrame should become a production-shaped AI media localization system that demonstrates real backend engineering, not only API wrapping. The target state is a reliable local or hosted platform that accepts videos, processes them through an observable asynchronous pipeline, and returns useful localized media artifacts.

The project should stay focused on video localization:

- Speech-to-text.
- Bilingual subtitle generation.
- Subtitle translation and polishing.
- Upload-time translation style control for natural, formal, or concise subtitle localization.
- Upload-time translation glossary control for names, product terms, and demo vocabulary.
- Upload-time subtitle polishing control for disabled, balanced, or strict subtitle cleanup.
- Reusable demo run profiles that apply a complete localization preset while preserving manual overrides.
- TTS dubbing audio.
- Time-coded custom narration segments with provider voice presets, voice audition, quick script paste import/export, editable timeline bars, local row editing commands, local draft history, and metadata-derived waveform overview for adding explanatory voiceover on top of localization output.
- Subtitle-burned preview video.
- Cost, retry, and failure observability.

It should not become a generic chatbot, a full video editor, or an unbounded collection of AI features. Custom narration should stay focused on timed explanatory voiceover segments for localization demos, not full nonlinear video editing.

The AI infrastructure target is to make model usage reproducible, observable, and cost-aware. OpenAI remains the default API provider, but the backend should treat speech, language, quality evaluation, and TTS calls as auditable pipeline operations rather than one-off SDK calls.

## Deployment Target

LinguaFrame should support staged deployment.

### Stage 1: Local Self-Hosted Development

The current implementation target is local self-hosted development.

Target local setup:

```text
git clone linguaframe
  -> configure .env
  -> docker compose up --build
  -> open React frontend
  -> inspect demo run launcher
  -> upload sample video
  -> inspect generated artifacts
  -> compare two completed demo profile runs
  -> inspect the same-source demo run matrix
  -> open the demo presenter pack
  -> verify the demo acceptance gate
```

Stage 1 goals:

- Validate backend startup.
- Validate MySQL migrations.
- Validate Redis connectivity.
- Validate RabbitMQ queue dispatch.
- Validate MinIO artifact storage.
- Validate FFmpeg availability.
- Validate OpenAI API calls with a short sample video.
- Validate real OpenAI demo runs through an explicit no-secret env template, preflight, and smoke runner instead of enabling paid providers by default.
- Validate the next full-demo command and expected evidence files through the browser `Demo run launcher` and `scripts/demo/demo-run-launcher.sh` before uploading full public sample media.
- Validate run-day next action through the browser `Demo presentation cockpit`, backend cockpit JSON, and `scripts/demo/demo-presentation-cockpit.sh` before upload, during processing, and after completion.
- Validate demo profile A/B evidence through backend comparison JSON/Markdown, the browser `Demo comparison` panel, and terminal full-video comparison downloads.
- Validate same-source demo run selection through the browser `Demo run matrix`, backend matrix JSON, and terminal full-video `demo-run-matrix.json` output.
- Validate presenter handoff readiness through the browser `Demo presenter pack`, backend presenter pack JSON, and terminal full-video `demo-presenter-pack.json` output.
- Validate demo narration setup through the browser `Narration draft history`, `Demo narration preset`, and `Render narration demo` panels, backend preset apply/preflight/render responses, terminal `scripts/demo/narration-demo-preset.sh`, `scripts/demo/narration-demo-render-preflight.sh`, and `scripts/demo/narration-demo-render.sh`, and optional full Tears narration render before narration evidence export.
- Validate final demo readiness through the browser `Demo acceptance gate`, backend gate JSON, and terminal full-video `demo-acceptance-gate.json` output.
- Validate offline reviewer handoff through the browser `Demo handoff portal`, backend portal JSON/Markdown/ZIP, and terminal full-video `demo-handoff-portal.zip` output.
- Keep secrets local.

### Stage 2: Private Demo Deployment

After the local pipeline works, LinguaFrame can be deployed to a private server for demos.

Target private demo shape:

```text
Internet
  -> HTTPS frontend
  -> HTTPS backend
  -> MySQL
  -> Redis
  -> RabbitMQ
  -> S3-compatible object storage
```

Stage 2 goals:

- Use a real HTTPS URL.
- Use a private-demo reverse proxy that exposes only the public web entry point while keeping app and dependency ports internal.
- Support controlled uploads from the project owner.
- Let the owner start and end a browser session with the configured private demo token, while keeping Swagger, curl, and scripts compatible with the demo access header.
- Persist a configured demo owner id on uploaded videos and localization jobs, and scope owner-facing media/job APIs to that owner before public authentication exists.
- Expose a configured local owner-account bearer bridge that resolves to the same owner workspace for job history, job detail, media, artifacts, diagnostics, evidence, upload readiness, and operator dashboard metadata.
- Enforce a private-demo owner quota preflight for active jobs, queued jobs, and same-day estimated spend before new uploads create storage objects, job rows, dispatch events, FFmpeg work, or provider calls.
- Persist job history and artifacts across restarts.
- Provide an operator backup and restore path for private-demo migration or server rebuilds.
- Provide a read-only launch rehearsal checklist that orders deploy preflight, stack startup, private preflight, OpenAI preflight, backup/restore dry-runs, smoke/full demos, and evidence export without auto-running those steps.
- Provide a read-only evidence gallery that selects completed demo runs for presentation and handoff from safe metadata and package links.
- Provide a read-only run archive that captures private-demo readiness, launch status, completed-run gallery counts, the recommended job, and safe package routes as a post-demo evidence index.
- Keep file size and duration limits conservative.
- Show repeatable demo input and output.

### Stage 3: Public Hosted Service

Public hosted usage is a later maturity target and should not drive the first implementation.

Stage 3 requires:

- Real user authentication.
- The current bridge is a configured local owner account with JWT bearer tokens; public registration and multi-user account lifecycle remain later work.
- Per-user storage isolation.
- Rate limiting and quota checks before expensive processing.
- Cost budgets.
- Abuse controls.
- Storage retention.
- Background cleanup.
- Stronger queue and worker recovery.
- Clear privacy and data-handling policy.

## User Experience Target

A user should be able to:

1. Open the LinguaFrame frontend.
2. Upload a short video.
3. Select target language or keep the default Chinese-English localization.
4. Watch job progress through a status timeline.
5. Verify source media metadata and download the uploaded source video through an explicit source route.
6. Use a guided demo review panel to walk through input, pipeline, review, delivery, evidence, and handoff in order.
7. Preview transcript segments and translated subtitles.
8. Download SRT or VTT files.
9. Correct generated target subtitles in a draft layer and export corrected JSON, SRT, or VTT without changing generated media artifacts.
10. Add per-segment review decisions, issue categories, reviewer notes, and publish release notes as review metadata separate from subtitle text.
11. Download subtitle review evidence as safe JSON, Markdown, and ZIP files that expose counts, checks, and links without raw subtitle text or reviewer note bodies.
12. Publish reviewed JSON/SRT/VTT artifacts and optionally create a separate reviewed subtitle-burned video for handoff.
13. Download a safe delivery manifest that explains handoff readiness, reviewed outputs, generated audit artifacts, evidence links, and hashes.
14. Download a reviewed handoff package that contains the delivery manifest, diagnostics, evidence report, reviewed subtitles, and optional reviewed burned video without internal audit artifacts or secrets.
15. Review a final demo handoff checklist that summarizes job completion, reviewed subtitles, media outputs, evidence links, cost/model-call evidence, cache evidence, and failure triage without exposing raw media text or secrets.
16. Download or copy a demo session report that explains the input job, generated outputs, handoff evidence, cost/cache evidence, and failure triage for one run.
17. Download one safe demo run package that combines job detail, diagnostics, evidence, quality evidence, delivery manifest, handoff checklist, and session report for reviewer handoff.
18. Download one safe AI audit package that connects model calls, prompt versions, active prompt templates, usage, latency, and cost for a selected job.
19. Inspect a same-source demo run matrix that marks the recommended baseline, best quality run, and lowest cost run across recent profile attempts.
20. Open a presenter pack that combines handoff readiness, recommended run IDs, safe package links, and copy/download presenter notes for one selected job.
21. Open a demo acceptance gate that gives one final `READY`, `ATTENTION`, or `BLOCKED` answer with required checks, warning checks, safe evidence, and next action.
22. Download a demo handoff portal ZIP with `index.html` as an offline reviewer entry point to all safe job evidence.
23. Open a private-demo evidence gallery that lists completed runs, marks the recommended handoff candidate, and exposes safe package links without opening every job manually.
24. Add time-coded narration rows manually or by quick script paste import, copy/download the current draft as reusable quick script text, audition configured voice presets, apply a voice to selected/all local draft rows, choose inherited/default or explicit provider voice presets, duplicate/split/merge/insert local draft rows, move or resize proportional timeline bars, use timing assistant actions to close gaps, resolve overlaps, or normalize row order locally, inspect metadata-derived waveform density/gaps, preview and scrub selected windows against narrated/burned/source video with visible playheads, preview selected-row TTS for unsaved text before saving, use in-memory undo/redo/revert before saving, understand gaps as intentional silence, fix overlaps or invalid windows, tune mix settings, generate narration audio/video, run read-only timing and render preflight before one-click narration demo render for completed Tears jobs, and download safe narration evidence.
25. Play the generated dubbing audio with visible file metadata and download evidence.
26. Preview or download generated and reviewed subtitle-burned videos as separate outputs.
27. Inspect cost and processing time.
28. Retry the job if a step fails.
29. Identify the current stage, slowest stage, and stage timing evidence without reading backend logs.
30. Review source and translated subtitle rows side by side with missing-target, timing-delta, quality, and downloadable subtitle artifact evidence.
31. Copy or download quality-evaluation evidence that captures score, verdict, dimensions, issue/fix counts, and safe routes without exposing raw media text or secrets.

The UI should make the system feel like a media workflow tool, not a chat page.

## Frontend Target

The frontend target is a React work surface for upload, job inspection, and artifact review.

Target screens:

- Upload and job creation.
- Job list.
- Job detail.
- Pipeline timeline.
- Pipeline progress and stage timing summary.
- Transcript and subtitle table.
- Read-only subtitle review workspace.
- Subtitle draft editor with corrected JSON/SRT/VTT export.
- Artifact preview and downloads.
- Cost and usage summary.
- Failure and retry panel.

The first screen should be the usable upload and job dashboard. A marketing landing page is not needed for the project goal.

## Backend Target

The backend should provide:

- JWT authentication or a local demo-user mode.
- A local owner-account JWT bridge that coexists with the private-demo token while public authentication is still out of scope.
- Owner workspace access boundaries for local bearer auth and demo-token access across media, jobs, artifacts, evidence, upload readiness, and operator dashboard metadata.
- Video upload APIs.
- Object storage abstraction.
- MySQL-backed media, job, transcript, artifact, usage, and failure records.
- RabbitMQ-backed async job processing.
- Redis-backed job status cache for job detail snapshots, rate-limit hooks, and lightweight coordination.
- FFmpeg integration for audio extraction and subtitle burn-in.
- Preset-based subtitle burn-in styling that is persisted per job and applied to generated preview videos.
- TTS dubbed-video delivery that combines generated `DUBBING_AUDIO` with generated `BURNED_VIDEO` into a separate `DUBBED_VIDEO` artifact when both inputs are available.
- Time-coded narration authoring that lets an operator attach multiple text segments to video time ranges, paste or export quick script rows in `START-END | VOICE | TEXT` format, audition provider-aware voice presets with custom sample text, apply a voice to selected/all local draft rows, choose provider-aware voice presets or inherit the configured default voice, duplicate/split/merge/insert local draft rows, inspect local draft history with undo/redo/revert and added/removed/timing/text/voice change counts, inspect and edit computed span/covered/gap/readiness metadata in a compact timeline workbench, run local timing assistant fixes for material gaps, overlaps, and row order, run terminal timing preflight that prints only safe metadata, inspect metadata-derived waveform density/gap buckets, preview and scrub selected windows against the best available narrated/burned/source video with jump/play-window controls and visible playheads, preview selected-row TTS for unsaved text/voice through a transient provider call, export/import explicit narration script packages for workspace reuse, synthesize saved segments through the existing TTS provider boundary, generate a timed `NARRATION_AUDIO` bed, tune numeric ducking/narration/fade settings, and generate standalone `NARRATED_VIDEO` with original/base audio ducked under narration without replacing generated or reviewed subtitle artifacts. Quick script import/export, timing assistant fixes, voice apply actions, video preview playback, waveform scrubbing, row editing commands, undo, redo, and revert are local-only until save and do not call providers or create artifacts. Voice audition and segment TTS preview may call the configured TTS provider and consume credits, but they do not save rows, create artifacts, update evidence, write object storage, or generate video. Draft history is in-memory only and resets on workspace reload or successful save response. Gaps are intentional silence; overlaps, invalid windows, blank inserted rows, malformed quick-script rows, and unknown voice presets remain blocked by validation. Voice cloning, uploaded reference audio, decoded-audio waveform rendering, and multitrack automation curves remain later targets.
- OpenAI speech-to-text client.
- OpenAI language client for translation and subtitle polishing.
- Translation style metadata that is persisted per job and included in provider prompts, safe summaries, and translation cache keys.
- Subtitle polishing metadata that is persisted per job and included in polishing prompts, safe summaries, model-call audit, and provider cache keys.
- OpenAI TTS client.
- OpenAI-backed translation quality evaluation client.
- Prompt template versioning for translation, polishing, and evaluation prompts.
- Model-call audit records with model, prompt version, usage, latency, cost, status, and safe errors.
- Timeline-derived pipeline progress and stage timing summaries.
- Read-only subtitle review summary derived from transcript segments, target subtitle segments, quality evaluation, and subtitle artifacts.
- Subtitle draft persistence and corrected JSON/SRT/VTT export as a human-review layer separate from TTS or burn-in regeneration.
- Content-hash caching hooks for repeated transcription and translation work.
- Per-job and per-user budget checks before expensive stages.
- SRT and VTT export.
- Retryable failure handling.
- Cost estimation and usage capture.
- OpenAPI documentation.
- Structured logs with job and stage identifiers.
- A private-demo operations readiness workspace that safely aggregates access gate, runtime contract, live dependency, provider, cost, storage/recovery, retention, and demo-evidence checks for browser and terminal handoff.
- A private-demo evidence gallery that safely aggregates recent completed jobs, handoff readiness, recommended run selection, and package links for browser and terminal handoff.
- A private-demo owner quota preflight workspace that safely reports configured owner pressure and blocks new uploads before expensive work.
- A pre-upload readiness workspace that combines access-gated API reachability, runtime contract, live dependencies, owner quota, selected demo profile, and paid-provider warnings before media upload.
- A demo acceptance gate that combines existing safe job evidence into one final read-only go/no-go answer after processing completes.

## Pipeline Target

The long-term runtime may split API and worker processes:

```text
linguaframe-api
  Uploads, auth, job APIs, artifact APIs

linguaframe-worker
  FFmpeg processing, OpenAI calls, subtitle export, retry handling

shared storage
  MySQL, Redis, RabbitMQ, object storage
```

The MVP should remain a modular monolith until the pipeline is proven. API and worker split should happen only when it improves runtime clarity, isolation, or deployment.

The future worker target can separate workloads by resource profile:

```text
ffmpeg-worker
  CPU-bound audio extraction and subtitle burn-in

openai-worker
  API-bound speech, translation, evaluation, and TTS calls

artifact-worker
  Subtitle export, object storage writes, cleanup, and download preparation
```

This split is optional. It should be implemented only after the modular monolith has stable stage boundaries and durable job state.

Before a demo upload, the browser and terminal readiness surfaces should show the active worker role, listener queue, queue routes, stage ownership, and safe startup commands. This is an observability contract only; routing behavior remains controlled by worker role and RabbitMQ configuration.

## Safety And Cost Target

LinguaFrame must:

- Reject unsupported file types.
- Enforce file size and duration limits before OpenAI calls.
- Never log raw API keys.
- Never expose private object storage credentials to the browser.
- Never process arbitrary file paths supplied by the user.
- Avoid overwriting artifacts without explicit versioning or idempotent keys.
- Record provider failures safely.
- Explain failed jobs with safe advisory triage that maps known OpenAI, budget, media, storage, worker/queue, configuration, cancellation, and unknown failures to recommended next actions.
- Track estimated cost per job.
- Enforce per-job and per-user budget limits before expensive OpenAI stages.
- Reject new private-demo uploads when configured owner active-job, queued-job, or owner daily-budget limits are exhausted.
- Explain upload-blocking readiness issues before object storage writes, queue dispatch, FFmpeg work, or OpenAI provider calls.
- Cache duplicate inputs by content hash when it avoids repeated model calls safely.
- Record prompt template versions for reproducibility.
- Allow expensive steps to be disabled for local testing.
- Treat estimated cost as informational, not authoritative billing.

## Engineering Target

The codebase should remain understandable to a Java backend interviewer:

- Modular Spring Boot backend.
- Package-by-domain structure.
- Thin controllers.
- Service interfaces and implementations.
- Explicit DTO, VO, BO, Query, Entity, and Enum objects.
- External provider clients behind interfaces.
- Media processing steps behind pipeline stage boundaries.
- Durable job status transitions.
- Tests for validation, status transitions, retry behavior, and subtitle export formatting.
- Local reproducible Docker Compose runtime.
- Browser and terminal readiness evidence for combined and split-worker topology.
- Browser, backend, and terminal reviewed-subtitle workflow cockpit for deciding whether a completed job still needs subtitle review, draft publishing, reviewed burned-video generation, or handoff package export.

## Resume Target

The final project should demonstrate:

- Java and Spring Boot backend development.
- React product UI.
- MySQL schema design.
- Redis caching and coordination.
- RabbitMQ asynchronous processing.
- S3-compatible object storage with MinIO.
- FFmpeg media processing for extraction, subtitle burn-in, and dubbed-video muxing.
- OpenAI speech-to-text, language, and TTS APIs.
- Prompt versioning and model-call tracing.
- LLM-based quality evaluation.
- Long-running job orchestration.
- Retry handling.
- Cost and usage observability.
- AI cost budget controls and cache-aware duplicate-work avoidance.
- Docker Compose deployment.
