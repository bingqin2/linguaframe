# TTS Voice Selection MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let each localization job carry its own TTS voice selection from upload through dubbing generation, cache lookup, job detail, and the React demo.

**Architecture:** Add a nullable `tts_voice` column to `localization_jobs` and default missing values to the configured provider voice at execution time. Extend upload APIs and frontend form data to accept an optional `ttsVoice`, expose the selected voice in job/detail/list responses, and use the per-job voice when building `TtsRequestBo` and TTS cache keys. Keep existing jobs and demo-mode behavior backward compatible.

**Tech Stack:** Java 21, Spring Boot MVC/JDBC/Flyway, JUnit 5, Mockito, React + Vite + TypeScript, Vitest, Docker Compose config validation.

## Global Constraints

- This slice must be a complete feature: persistence, API contract, worker behavior, cache compatibility, frontend controls, tests, docs, progress logs, validation, commit, and merge back to `main`.
- Do not add speaker cloning, segment-level voice assignment, lip sync, or audio/video mixing.
- Do not make OpenAI TTS mandatory; default Docker demo remains deterministic and cost-free.
- Never log or expose OpenAI API keys, raw provider payloads, uploaded media bytes, or local media paths.
- Branch name: `tts-voice-selection-mvp`.

---

### Task 1: Persist And Expose Per-Job TTS Voice

**Files:**
- Create: `LinguaFrame/src/main/resources/db/migration/V15__add_job_tts_voice.sql`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/entity/LocalizationJobRecord.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/vo/LocalizationJobSummaryVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/domain/vo/MediaUploadVo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/repository/LocalizationJobRepository.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/repository/LocalizationJobRepositoryTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/repository/UploadIntakeSchemaTests.java`

**Interfaces:**
- Produces: `LocalizationJobRecord.ttsVoice(): String`
- Produces: `LocalizationJobVo.ttsVoice(): String`
- Produces: `LocalizationJobSummaryVo.ttsVoice(): String`
- Produces: `MediaUploadVo.ttsVoice(): String`

- [x] Add Flyway migration `V15__add_job_tts_voice.sql` with `ALTER TABLE localization_jobs ADD COLUMN tts_voice VARCHAR(64);`.
- [x] Write failing schema/repository tests proving the column exists and saved jobs can round-trip `ttsVoice`.
- [x] Extend `LocalizationJobRecord` constructors so older call sites can omit `ttsVoice` and get `null`.
- [x] Update repository row mapping, insert SQL, list summaries, and job detail construction to include `tts_voice`.
- [x] Update API VO records to expose `ttsVoice`.
- [x] Run `mvn -pl LinguaFrame -Dtest='UploadIntakeSchemaTests,LocalizationJobRepositoryTests' test`.

### Task 2: Upload API And Dispatch Contract

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/controller/MediaUploadController.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/MediaUploadService.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/media/service/impl/MediaUploadServiceImpl.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/message/QueuedLocalizationJobMessage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/JobDispatchOutboxServiceImpl.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/controller/MediaUploadControllerTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/media/service/MediaUploadServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/JobDispatchOutboxServiceTests.java`

**Interfaces:**
- Consumes: `LocalizationJobRecord.ttsVoice()`.
- Produces: `POST /api/media/uploads` multipart field `ttsVoice`.
- Produces: `QueuedLocalizationJobMessage.ttsVoice(): String`.

- [x] Write failing upload controller/service tests proving `ttsVoice=verse` is accepted, trimmed, persisted, returned in upload response, and visible in job detail.
- [x] Write failing tests proving blank `ttsVoice` is normalized to `null`.
- [x] Extend `MediaUploadController#createUpload` with optional `@RequestParam("ttsVoice")`.
- [x] Extend `MediaUploadService#createUpload(MultipartFile file, String targetLanguage, String ttsVoice)`.
- [x] Normalize `ttsVoice` with `StringUtils.hasText`; store trimmed voice or `null`.
- [x] Extend queued job messages and dispatch serialization with `ttsVoice` for observability and future split-worker compatibility.
- [x] Run `mvn -pl LinguaFrame -Dtest='MediaUploadControllerTests,MediaUploadServiceTests,JobDispatchOutboxServiceTests,LocalizationJobControllerTests' test`.

### Task 3: Worker TTS Voice And Cache Compatibility

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/domain/bo/TtsRequestBo.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DubbingAudioGenerationPipelineStage.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/OpenAiTtsProvider.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/DemoTtsProvider.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/DubbingAudioGenerationPipelineStageTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/TtsCacheKeyServiceTests.java`
- Test: `LinguaFrame/src/test/java/com/linguaframe/job/service/OpenAiTtsProviderTests.java`

**Interfaces:**
- Consumes: `LocalizationJobRecord.ttsVoice()`.
- Produces: `TtsRequestBo.voice(): String`.
- Produces: TTS cache lookup voice equals job voice when present, otherwise provider default voice.

- [x] Write failing pipeline tests proving a job-level `ttsVoice` is passed to `TtsProvider` and used in `TtsCacheKeyService.build(...)`.
- [x] Write failing tests proving missing job voice falls back to `linguaframe.tts.openai.voice` for OpenAI provider and `demo-voice` for demo provider/cache identity.
- [x] Extend `TtsRequestBo` with `voice`.
- [x] Update `DubbingAudioGenerationPipelineStage` to resolve effective voice once and pass it to provider/cache lookup/cache store.
- [x] Update `OpenAiTtsProvider` to use `request.voice()` when present, falling back to configured voice.
- [x] Keep `DemoTtsProvider` deterministic and keep its model-call summaries count-only.
- [x] Run `mvn -pl LinguaFrame -Dtest='DubbingAudioGenerationPipelineStageTests,TtsCacheKeyServiceTests,OpenAiTtsProviderTests' test`.

### Task 4: React Demo Voice Control

**Files:**
- Modify: `frontend/src/api/linguaframeApi.ts`
- Modify: `frontend/src/domain/jobTypes.ts`
- Modify: `frontend/src/domain/recentJobs.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/api/linguaframeApi.test.ts`
- Test: `frontend/src/domain/recentJobs.test.ts`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: upload `ttsVoice` field and job `ttsVoice` API field.
- Produces: upload form voice selector with default blank provider voice and options `alloy`, `verse`, `aria`, `sage`, `coral`.

- [x] Write failing API test proving `uploadMedia(file, targetLanguage, ttsVoice)` sends multipart `ttsVoice` only when non-blank.
- [x] Write failing recent-job test proving `ttsVoice` persists when present and old recent-job entries without it still load.
- [x] Write failing App test proving the upload form has a TTS voice selector and uploaded jobs display the selected voice.
- [x] Extend TypeScript domain types with optional/nullable `ttsVoice`.
- [x] Extend `uploadMedia` signature and FormData creation.
- [x] Add a compact upload-form select for TTS voice; keep the blank option as provider default.
- [x] Show selected job voice near target language/status metadata when present.
- [x] Run `cd frontend && npm run test:run -- linguaframeApi recentJobs App`.

### Task 5: Docs, Validation, Commit, And Merge

**Files:**
- Modify: `README.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/spec.md`
- Modify: `docs/product/frontend-design.md`
- Modify: `docs/progress/decisions.md`
- Modify: `docs/progress/execution-log.md`
- Modify: `docs/plans/042-tts-voice-selection-mvp.md`

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: verified feature branch merged back to `main`.

- [x] Document per-job TTS voice selection, fallback behavior, and cache compatibility in README.
- [x] Mark multiple voice choices as implemented for job-level TTS selection in product docs.
- [x] Record the decision that voice selection belongs to job creation and cache identity, not a global runtime-only setting.
- [x] Run focused backend validation: `mvn -pl LinguaFrame -Dtest='UploadIntakeSchemaTests,LocalizationJobRepositoryTests,MediaUploadControllerTests,MediaUploadServiceTests,JobDispatchOutboxServiceTests,LocalizationJobControllerTests,DubbingAudioGenerationPipelineStageTests,TtsCacheKeyServiceTests,OpenAiTtsProviderTests' test`.
- [x] Run focused frontend validation: `cd frontend && npm run test:run -- linguaframeApi recentJobs App`.
- [x] Run full backend validation: `mvn -pl LinguaFrame test`.
- [x] Run full frontend validation: `cd frontend && npm run test:run`.
- [x] Run frontend build: `cd frontend && npm run build`.
- [x] Run Compose validation: `docker compose --env-file .env.example config --quiet` and `docker compose --env-file .env.example --profile split-workers config --quiet`.
- [x] Run `git diff --check`.
- [ ] Commit as `Add TTS voice selection`.
- [ ] Merge `tts-voice-selection-mvp` back to `main`.
- [ ] Run post-merge focused validation: backend focused command and `cd frontend && npm run test:run -- linguaframeApi recentJobs App`.
