# Demo Scripts

## Private Demo Delivery Receipt

Export the final metadata-only receipt after a rehearsal or private demo:

```bash
scripts/demo/private-demo-delivery-receipt.sh
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/private-demo-delivery-receipt.sh
```

The script writes `private-demo-delivery-receipt.json`, `.md`, and `.zip` to `/tmp/linguaframe-demo/private-demo-delivery-receipt/`, prints status, recommended job, evidence link counts, package entry counts, and the primary export command, and exits non-zero only when the receipt is `BLOCKED`. Set `LINGUAFRAME_PRIVATE_DEMO_DELIVERY_RECEIPT_REPORT_ONLY=true` to export a blocked receipt without failing the command.

## Narration Scene Board

Export the metadata-only scene-board summary for a saved narration workspace:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/narration-scene-board.sh
```

The script writes `narration-scene-board.json` and `.md` to `/tmp/linguaframe-demo/narration-scene-board/`, prints status, segment count, coverage, gaps, overlap state, voice count, mix keyframe count, audio/video readiness, blocked checks, and the recommended next action. It does not call OpenAI, call TTS providers, run FFmpeg, save rows, print narration text, expose object keys, expose local paths, or include media bytes. Set `LINGUAFRAME_NARRATION_SCENE_BOARD_REPORT_ONLY=true` to export a `BLOCKED` board without failing the command.

## Upload Narration Launchpad

Export the metadata-only handoff after uploading a video with `LINGUAFRAME_DEMO_NARRATION_SCRIPT` or `LINGUAFRAME_DEMO_NARRATION_SCRIPT_FILE`:

```bash
LINGUAFRAME_DEMO_JOB_ID=<job-id> scripts/demo/upload-narration-launchpad.sh
```

The script writes `upload-narration-launchpad.json` and `.md` to `/tmp/linguaframe-demo/upload-narration-launchpad/`, prints status, seeded row count, character count, selected row, voice summary, scene-board status, audio/video readiness, next actions, and safe links. It does not call OpenAI, call TTS providers, run FFmpeg, save rows, print narration text, expose object keys, expose local paths, or include media bytes.

## Session Narration Production Board

Export the run-day production overview across recent narration jobs:

```bash
scripts/demo/session-narration-production-board.sh
LINGUAFRAME_SESSION_NARRATION_PRODUCTION_BOARD_REPORT_ONLY=true scripts/demo/session-narration-production-board.sh
```

The script writes `session-narration-production-board.json` and `.md` to `/tmp/linguaframe-demo/session-narration-production-board/`, prints ready/review/render/authoring/blocked counts, first blocked job id, primary action, and the Markdown path. It is read-only and metadata-only: it does not call OpenAI, call TTS providers, run FFmpeg, upload media, save narration rows, or print narration text, reviewer notes, object keys, local paths, provider payloads, tokens, API keys, or media bytes. By default it exits non-zero when blocked rows exist; set report-only mode to export the board without failing.

The same production summary is also surfaced by `scripts/demo/demo-session-command-center.sh` through `demoSessionCommandCenterNarration*` lines. `scripts/demo/demo-session-evidence-package.sh` includes `narration-production-board.json` and `narration-production-board.md` in the session ZIP so the run-day handoff contains narration readiness evidence without opening the standalone board first.

## Demo Session Cost Control Board

Export the run-day cost safety overview before another paid demo:

```bash
scripts/demo/demo-session-cost-control-board.sh
LINGUAFRAME_DEMO_SESSION_COST_CONTROL_BOARD_REPORT_ONLY=true scripts/demo/demo-session-cost-control-board.sh
```

The script writes `demo-session-cost-control-board.json` and `.md` to `/tmp/linguaframe-demo/demo-session-cost-control-board/`, prints recent estimated spend, same-day estimated spend, configured daily budget, failed model-call count, first failed job id, primary action, and the Markdown path. It is read-only and metadata-only: it does not call OpenAI, call TTS providers, run FFmpeg, upload media, edit `.env`, mutate budgets, print prompts, print provider payloads, expose object keys, expose local paths, expose tokens or API keys, or include media bytes. By default it exits non-zero when the board is `BLOCKED`; set report-only mode to export the board without failing.

The same cost-control summary is surfaced by `scripts/demo/demo-session-command-center.sh` through `demoSessionCommandCenterCostControl*` lines. `scripts/demo/demo-session-evidence-package.sh` includes `cost-control-board.json` and `cost-control-board.md` in the session ZIP so the run-day handoff contains cost safety evidence without opening the standalone board first.

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
