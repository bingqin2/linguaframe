# Demo Scripts

## Private Demo Delivery Receipt

Export the final metadata-only receipt after a rehearsal or private demo:

```bash
scripts/demo/private-demo-delivery-receipt.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/private-demo-delivery-receipt.sh
```

The script writes `private-demo-delivery-receipt.json`, `.md`, and `.zip` to `/tmp/linguaframe-demo/private-demo-delivery-receipt/`, prints status, recommended job, evidence link counts, package entry counts, and the primary export command, and exits non-zero only when the receipt is `BLOCKED`. Set `LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_REPORT_ONLY=true` to export a blocked receipt without failing the command.

## Narration Render Review

Export the metadata-only narration cue sheet after saving narration or running the demo render:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-render-review.sh
```

The script downloads `narration-render-review.json` and `narration-render-review.md` to `/tmp/linguaframe-demo/narration-render-review/`, prints status, next action, segment/gap/overlap counts, audio/video/waveform readiness, artifact counts, mix override/keyframe counts, and blocked or warning checks. It reads existing workspace, evidence, and artifacts only; it does not call OpenAI, call TTS providers, run FFmpeg, save narration rows, create artifacts, print narration text, expose object keys, or include media bytes.

Use `LINGUAFRAME_NARRATION_RENDER_REVIEW_REPORT_ONLY=true` when you want to export a `BLOCKED` review without failing the terminal command.

## Narration Mix Automation

Inspect effective narration mix automation values from an existing workspace:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-mix-automation.sh
```

The script downloads the narration workspace JSON, writes it to `/tmp/linguaframe-demo/narration-mix-automation/narration-workspace.json`, and prints segment count, override count, inherited count, minimum ducking volume, maximum narration volume, maximum fade duration, persistent keyframe count, lane summary, and lane value ranges. It derives values from existing job mix settings, segment overrides, and saved `mixAutomation.keyframes`; it does not save narration rows, call providers, create artifacts, print narration text, expose object keys, or include media bytes.

Browser demo order: edit narration rows, add or delete `Mix keyframes` in the inspector, click `Save narration`, run render preflight, generate the narrated video, then export the script package. Script package JSON/ZIP includes `mixKeyframes` so the same automation can be restored with the narration rows.

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
