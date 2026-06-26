# Decision Log

This file records project-level decisions that affect future implementation. Feature-specific decisions should also be recorded in the relevant ExecPlan under `docs/plans/`.

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
