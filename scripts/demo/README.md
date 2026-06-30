# Demo Scripts

## Narration Waveform

Inspect the read-only decoded narration waveform for a completed or prepared job:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-waveform.sh
```

The script calls `GET /api/jobs/{jobId}/narration-waveform`, writes JSON to `/tmp/linguaframe-demo/narration-waveform/narration-waveform.json`, and prints status, source type, bucket count, duration, max peak, average RMS, and fallback reason. It reads existing `NARRATION_AUDIO`, `NARRATED_VIDEO`, `BURNED_VIDEO`, or source media only; it does not save narration rows, create artifacts, call providers, print narration text, expose object keys, or include media bytes.

Useful tuning:

```bash
LINGUAFRAME_NARRATION_WAVEFORM_BUCKET_COUNT=96 \
LINGUAFRAME_DEMO_JOB_ID=<job-id> \
scripts/demo/narration-waveform.sh
```

## Narration Timing Assistant

Run the timing assistant after opening or preparing a narration workspace for a completed job:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-timing-assistant.sh
```

The script downloads the narration workspace JSON, writes a metadata-only report to `/tmp/linguaframe-demo/narration-timing-assistant/narration-timing-assistant-report.json`, and prints segment count, gap count, total gap seconds, overlap count, longest gap, readiness, and a suggested next action. It does not save narration rows, call TTS providers, generate artifacts, print narration text, or include media bytes.

Useful tuning:

```bash
LINGUAFRAME_NARRATION_TIMING_MINIMUM_REPORT_GAP_SECONDS=1 \
LINGUAFRAME_NARRATION_TIMING_TARGET_GAP_SECONDS=0.25 \
LINGUAFRAME_DEMO_JOB_ID=<job-id> \
scripts/demo/narration-timing-assistant.sh
```
