# Cache Hit Demo Evidence MVP

**Goal:** Provide a repeatable Docker demo that proves LinguaFrame reuses provider-cache results on compatible repeat jobs, with clear command-line evidence and documentation.

**Architecture:** Add a dedicated demo script that uploads the same sample twice through the existing Docker backend, waits for both jobs to complete, captures both job details and diagnostics reports, and asserts the second job exposes provider cache hits. Reuse `scripts/demo/lib/linguaframe-demo.sh` for health checks, uploads, polling, downloads, and diagnostics safety checks. Keep the feature focused on evidence and documentation; do not change cache semantics in this slice.

## Scope

- Add `scripts/demo/docker-e2e-cache-hit.sh`.
- Store script outputs under `/tmp/linguaframe-demo/cache-hit/`.
- Run two compatible jobs with the same sample path, target language, provider/model configuration, and prompt versions.
- Print first/second job IDs, model-call counts, cache summaries, provider `CACHE_HIT` timeline events, and diagnostics summaries.
- Fail the script when the second job has no provider cache hit.
- Download first and second diagnostics JSON reports and validate they remain metadata-only through the existing diagnostics helper.
- Update README, Docker demo runbook, smoke-test checklist, and execution log.

## Non-Goals

- Do not implement generic prompt-response caching.
- Do not change transcription, translation, TTS, quality evaluation, or artifact-cache key behavior.
- Do not require OpenAI credentials; the script must work with deterministic demo providers from `.env.example`.
- Do not commit local demo videos or generated `/tmp` artifacts.

## Implementation Steps

1. **Script helper support**
   - Add small reusable helper functions if needed for reading cache summary fields and filtering provider `CACHE_HIT` events from job detail JSON.
   - Keep helper output safe: no object keys, local media paths from diagnostics, secrets, provider payloads, or raw transcript/subtitle text.

2. **Cache-hit demo script**
   - Create `scripts/demo/docker-e2e-cache-hit.sh`.
   - Require `curl` and `python3`.
   - Wait for backend health, ensure or use `LINGUAFRAME_DEMO_SAMPLE_PATH`, upload job one, wait for `COMPLETED`, then upload job two and wait for `COMPLETED`.
   - Save job detail JSON and diagnostics JSON for both runs under `/tmp/linguaframe-demo/cache-hit/`.
   - Assert `cacheSummary.providerCacheHitCount >= 1` for the second job.
   - Print a concise comparison showing model-call counts and provider cache-hit events.

3. **Documentation**
   - Add a README section that explains when to run the cache-hit demo and what output proves.
   - Extend `docs/agent/docker-e2e-demo.md` with the exact command and expected evidence.
   - Update `docs/agent/smoke-test-checklist.md` so provider-cache verification points to the script instead of remaining manual-only.
   - Record the feature and verification commands in `docs/progress/execution-log.md`.

4. **Validation**
   - Run `bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-cache-hit.sh`.
   - Run `mvn -pl LinguaFrame -Dtest=LocalizationJobExecutionServiceTests,LocalizationJobControllerTests test`.
   - Run `docker compose --env-file .env.example config --quiet`.
   - If a local Docker stack is already running, run `scripts/demo/docker-e2e-cache-hit.sh` and capture its summary; otherwise document the command as the manual runtime verification path.
   - Run `git diff --check`.

## Acceptance Criteria

- A contributor can prove cache reuse with one command after starting the Docker stack.
- The second compatible job fails the script if `providerCacheHitCount` is zero.
- Output clearly distinguishes first-job model calls from second-job provider cache hits.
- Diagnostics reports are downloaded and pass the existing sensitive-string checks.
- Documentation explains that this is a demo evidence feature, not a new cache implementation.
