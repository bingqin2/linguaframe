# Frontend Design Direction

## Purpose

LinguaFrame's frontend is a video localization work surface. Its job is to make upload, processing status, generated subtitles, dubbing audio, final video, and cost records visible in one place.

The frontend is not a marketing landing page and not a chatbot. It should demonstrate the real media processing workflow.

## Design Philosophy

The interface should feel like a practical creator and operator tool:

- Keep the first screen focused on uploading or inspecting jobs.
- Show real pipeline states instead of vague AI progress copy.
- Make generated artifacts easy to preview and download.
- Treat failures as normal operational states.
- Show cost and duration clearly without overclaiming billing accuracy.
- Keep controls explicit: upload, retry, cancel, download, preview.

## Target Screens

### Upload Screen

Responsibilities:

- Select or drag a video file.
- Show accepted formats and current file limits.
- Choose subtitle languages when options exist.
- Start localization.
- Show validation errors before upload.

### Job List

Responsibilities:

- Show recent localization jobs.
- Filter by status.
- Show source filename, created time, current stage, duration, and cost estimate.
- Open job detail.

### Job Detail

Responsibilities:

- Show source video metadata.
- Show pipeline timeline.
- Show current status and failure reason.
- Show retry for failed jobs and cancel for queued, retrying, or processing jobs.
- Show generated artifacts.

### Subtitle Preview

Responsibilities:

- Show timestamped transcript segments.
- Show Chinese and English subtitle text.
- Allow downloading SRT and VTT files.
- Make timing issues visible.

### Artifact Preview

Responsibilities:

- Play generated TTS audio.
- Preview subtitle-burned video.
- Download generated files.
- Show artifact size and generation stage.

### Cost Summary

Responsibilities:

- Show OpenAI operation breakdown.
- Show audio duration, token usage when available, call count, and estimated cost.
- Show processing duration.
- Clearly label costs as estimates.

### AI Infrastructure Detail

Responsibilities:

- Show model-call records for speech, translation, evaluation, and TTS stages.
- Show prompt version for translation and evaluation calls.
- Show latency, usage, estimated cost, and status for each model call.
- Show translation quality score and detected issues when evaluation is enabled.
- Show whether a result came from cache when duplicate-work avoidance is implemented.

## First Demo UX

The first demo should be simple:

```text
Open frontend
  -> Upload a 30-60 second video
  -> Watch status move through the pipeline
  -> Preview transcript and translated subtitles
  -> Play TTS audio
  -> Preview subtitle-burned video
  -> Inspect cost and retry information
```

## Visual Style

Use a focused operational interface:

- Dense but readable layouts.
- Compact status badges.
- Timeline or stepper for pipeline stages.
- Tables for subtitle segments.
- Native media previews for video and audio.
- Clear empty, loading, failed, and completed states.

Avoid:

- Oversized hero sections.
- Decorative AI gradients as the main experience.
- Generic chat-first layouts.
- Cards inside cards.
- Marketing copy in place of real job state.

## Non-Goals

The frontend should not:

- Become a full video editor.
- Hide pipeline details behind a chat-only interface.
- Replace artifact downloads with screenshots.
- Allow arbitrary backend command execution.
- Expose object storage credentials.

## Success Criteria

The frontend is successful when a viewer can answer these questions without terminal access:

- Which video was uploaded?
- What stage is running now?
- Did transcript and translation succeed?
- Which subtitle files were generated?
- Is TTS audio available?
- Is the subtitle-burned video available?
- What did the job cost approximately?
- Which model calls ran and which prompt versions were used?
- Did translation quality evaluation pass?
- If it failed, which stage failed and can it be retried?
