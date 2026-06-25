# Milestones

## Milestone 0: Foundation

Outcome:

- The project is documented, runnable, and ready for implementation.

Evidence:

- Product docs exist.
- Backend code standard exists.
- Basic backend test passes.
- Local backend starts.
- Health endpoint works.

## Milestone 1: Upload To Job

Outcome:

- A user can upload a short video and create a durable localization job.

Evidence:

- Upload validation exists.
- Source file is stored in MinIO.
- MySQL records exist for video and job.
- Job status can be queried.

## Milestone 2: Job To Audio

Outcome:

- The backend processes jobs asynchronously and extracts audio.

Evidence:

- RabbitMQ dispatch works.
- Worker status transitions are recorded.
- FFmpeg extracts audio.
- Extracted audio artifact is stored.
- FFmpeg failure creates a failed job record.

## Milestone 3: Audio To Subtitles

Outcome:

- A job can produce timestamped transcript data and subtitle files.

Evidence:

- OpenAI speech-to-text integration works on a sample video.
- Transcript segments are stored.
- SRT and VTT files are generated.
- Subtitle files are downloadable.

## Milestone 4: Translation And Dubbing

Outcome:

- A job can produce bilingual subtitles and TTS dubbing audio.

Evidence:

- Chinese and English subtitles are generated.
- Segment timing is preserved.
- OpenAI TTS produces an audio artifact.
- Audio can be previewed or downloaded.

## Milestone 5: Subtitle-Burned Video

Outcome:

- A job can produce a preview video with generated subtitles burned in.

Evidence:

- FFmpeg burn-in works with generated subtitle files.
- Generated video artifact is stored.
- Video can be previewed or downloaded.

## Milestone 6: Production-Shaped Demo

Outcome:

- LinguaFrame has the minimum reliability and observability expected from a serious backend project.

Evidence:

- Failed jobs can be retried.
- Usage and estimated cost are visible.
- Job timeline exists.
- Redis status cache or rate-limit hook exists.
- Docker Compose local runtime works.
- React UI can demonstrate the full upload-to-artifact flow.

## Milestone 7: AI Infrastructure Depth

Outcome:

- LinguaFrame exposes model usage as reproducible, observable, and cost-aware infrastructure.

Evidence:

- Prompt templates are versioned.
- Model-call audit records include stage, model, prompt version, latency, usage, cost, and status.
- Translation quality evaluation exists for at least one subtitle track.
- Budget checks can stop expensive stages before execution.
- Duplicate-work avoidance is planned or implemented through content-hash cache keys.
