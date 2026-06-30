# Persistent Narration Waveform Artifacts

## Goal

Turn the current on-demand decoded narration waveform into a reusable job artifact. Operators should be able to load a narration waveform without re-running analysis on every request, see which media source produced it, export the JSON evidence from scripts, and include it in job artifact archives.

## Feature Scope

- Add a `NARRATION_WAVEFORM` artifact type for waveform JSON generated from the best available audio/video source.
- Reuse an existing matching waveform artifact when the source artifact, bucket count, and source media metadata still match.
- Keep the safe fallback behavior when no source media exists or FFmpeg analysis fails.
- Surface artifact id, source artifact id, cache status, generated time, and content hash through the API and UI.
- Update demo scripts and documentation so full-video narration demos can export waveform evidence deterministically.

## Implementation Tasks

1. Backend artifact model and service behavior
   - Extend `JobArtifactType` with `NARRATION_WAVEFORM`.
   - Add waveform artifact metadata fields to `NarrationWaveformVo`.
   - Update `NarrationWaveformServiceImpl` to look for a reusable waveform artifact before running FFmpeg.
   - Persist new waveform JSON with `application/json` content type when analysis succeeds.
   - Avoid persisting `UNAVAILABLE` or `FAILED_SAFE` fallback responses.

2. Backend API and tests
   - Preserve `GET /api/jobs/{jobId}/narration-waveform?bucketCount=...`.
   - Add tests for first-generation persistence, subsequent cache reuse, stale-source regeneration, bucket-count separation, and safe fallback non-persistence.
   - Keep source preference order: `NARRATION_AUDIO`, `NARRATED_VIDEO`, `BURNED_VIDEO`, then source media.

3. Frontend workspace integration
   - Extend `NarrationWaveform` TypeScript types.
   - Show artifact/cache metadata in the narration waveform panel without exposing storage paths.
   - Keep current decoded and metadata-fallback visuals working.
   - Add focused React/API tests for persisted waveform metadata.

4. Demo scripts and docs
   - Update `scripts/demo/narration-waveform.sh` and shared demo helpers to print artifact id, cache hit, source artifact id, content hash, and generated time.
   - Document persistent waveform evidence in README/demo references and product roadmap.
   - Record validation commands in `docs/progress/execution-log.md`.

## Validation Plan

- `mvn test`
- `npm --prefix frontend test -- --run`
- `scripts/demo/narration-waveform.sh <jobId>` against an existing completed narration job when the local stack is available

## Out of Scope

- Multitrack waveform editing.
- Uploaded reference-audio waveform storage.
- Replacing subtitle review artifacts or generated media.
