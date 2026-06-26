# Full Video Demo Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an attributable full-video demo path for the local Tears of Steel sample at `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`.

**Architecture:** Keep the MP4 outside git and add a repository script that treats it as a local input fixture. The script will reuse `scripts/demo/lib/linguaframe-demo.sh`, wait longer than the smoke demo, download core artifacts, and treat subtitle-burned video as optional because full-video burn-in currently exceeds the default FFmpeg timeout.

**Tech Stack:** Bash, curl, python3, Docker Compose runtime, Spring Boot backend APIs, FFmpeg-backed worker pipeline, Markdown docs.

## Global Constraints

- Use feature branch `full-video-demo-script`.
- Do not commit video files, generated artifacts, `.env`, OpenAI keys, storage credentials, or local absolute media paths except in the explicit local demo command examples.
- Keep the full-video script focused on processing an existing local MP4; do not add download automation in this slice.
- Keep burned-video output optional in the full-video script until FFmpeg burn-in performance is handled in a separate feature.
- The feature must document original video attribution and license source before presenting the media as demo material.

---

## Design Summary

Recommended approach: add a dedicated full-video demo script and a small media reference document. This keeps the existing short `docker-e2e-success.sh` as the fast smoke path while making the longer public-video demo repeatable.

Alternative 1, modify `docker-e2e-success.sh` to cover both short and full demos: fewer files, but it would mix a fast CI-like smoke path with an expensive manual demo path.

Alternative 2, commit a clipped MP4 fixture: easier for new contributors, but it creates repository bloat and increases licensing risk.

## Files To Create Or Modify

- Create `scripts/demo/docker-e2e-tears-of-steel-full.sh`: full-video upload, long polling, artifact download, optional burned-video download, and summary output.
- Create `docs/product/demo-references.md`: source, attribution, license, and local usage notes for demo media.
- Modify `docs/agent/docker-e2e-demo.md`: add full-video demo instructions and burn-in caveat.
- Modify `README.md`: link the reference document and point from demo media candidates to the full-video script.
- Modify `docs/progress/execution-log.md`: record validation commands and any runtime caveats.

## Task 1: Add Demo Media Reference Documentation

**Files:**
- Create: `docs/product/demo-references.md`
- Modify: `README.md`

**Interfaces:**
- Produces: a stable documentation target for demo media source, attribution, license, and local file handling.

- [x] Add `docs/product/demo-references.md` with this content:

```markdown
# Demo Media References

LinguaFrame demo media should be public, attributable, and kept outside git unless a future fixture is intentionally small and license-reviewed.

## Tears of Steel

- **Recommended use:** primary full-video localization demo.
- **Local demo file:** `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`.
- **Original project:** [Tears of Steel](https://studio.blender.org/films/tears-of-steel/) by Blender Studio / Blender Foundation.
- **Demo clip:** "Casting the Actors", listed in the Blender Studio Tears of Steel gallery as a 1280x720, 1:50 free video.
- **License reference:** the official [Mango / Tears of Steel About page](https://mango.blender.org/about/) states that Tears of Steel is licensed as Creative Commons Attribution 3.0.
- **Attribution text:** "Tears of Steel" / "Casting the Actors" by Blender Studio / Blender Foundation, licensed under Creative Commons Attribution 3.0.

Do not commit the MP4 into this repository. Keep downloaded source media under `~/Downloads`, `/tmp/linguaframe-demo`, or another local path ignored by git.
```

- [x] Add a `docs/product/demo-references.md` entry under `README.md` `Useful Docs`.
- [x] Update the `README.md` Tears of Steel bullet to mention `scripts/demo/docker-e2e-tears-of-steel-full.sh` as the full-video demo path.
- [x] Run:

```bash
rg -n "demo-references|docker-e2e-tears-of-steel-full|tos_casting|Creative Commons" README.md docs/product/demo-references.md
```

Expected: matches show the reference doc, script name, local file note, and license attribution.

## Task 2: Add The Full-Video Demo Script

**Files:**
- Create: `scripts/demo/docker-e2e-tears-of-steel-full.sh`

**Interfaces:**
- Consumes: `wait_for_backend`, `upload_demo_video`, `wait_for_job_status`, `print_job_summary`, `list_job_artifacts`, `download_artifact_by_type`, and `download_optional_artifact_by_type` from `scripts/demo/lib/linguaframe-demo.sh`.
- Produces: downloaded artifacts under `/tmp/linguaframe-demo/tears-of-steel-full/`.

- [x] Create the script with this behavior:

```bash
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

usage() {
  cat <<'USAGE'
Usage: scripts/demo/docker-e2e-tears-of-steel-full.sh

Runs a full-video LinguaFrame demo using the local Tears of Steel sample.

Environment:
  LINGUAFRAME_TEARS_SAMPLE_PATH             Default: /Users/wangbingqin/Downloads/tos_casting-720p.mp4
  LINGUAFRAME_DEMO_BASE_URL                 Default: http://localhost:8080
  LINGUAFRAME_FULL_DEMO_OUTPUT_DIR          Default: /tmp/linguaframe-demo/tears-of-steel-full
  LINGUAFRAME_FULL_DEMO_WAIT_ATTEMPTS       Default: 240
  LINGUAFRAME_FULL_DEMO_WAIT_DELAY_SECONDS  Default: 5
USAGE
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_TEARS_SAMPLE_PATH:-/Users/wangbingqin/Downloads/tos_casting-720p.mp4}"
OUTPUT_DIR="${LINGUAFRAME_FULL_DEMO_OUTPUT_DIR:-/tmp/linguaframe-demo/tears-of-steel-full}"
WAIT_ATTEMPTS="${LINGUAFRAME_FULL_DEMO_WAIT_ATTEMPTS:-240}"
WAIT_DELAY_SECONDS="${LINGUAFRAME_FULL_DEMO_WAIT_DELAY_SECONDS:-5}"

if [[ ! -s "$SAMPLE_PATH" ]]; then
  echo "Missing Tears of Steel sample: $SAMPLE_PATH" >&2
  echo "Download the sample locally, then rerun with LINGUAFRAME_TEARS_SAMPLE_PATH=/absolute/path/to/video.mp4 if needed." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

echo "Demo media: Tears of Steel / Casting the Actors"
echo "Source: https://studio.blender.org/films/tears-of-steel/"
echo "License reference: https://mango.blender.org/about/"
echo "Input: $SAMPLE_PATH"
echo "Output directory: $OUTPUT_DIR"

wait_for_backend "$BASE_URL"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded full demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED "$WAIT_ATTEMPTS" "$WAIT_DELAY_SECONDS")"
printf '%s' "$job_response" | print_job_summary

echo "Artifacts for job $job_id:"
artifacts_response="$(list_job_artifacts "$BASE_URL" "$job_id")"
printf '%s' "$artifacts_response" | print_artifact_summary

download_artifact_by_type "$BASE_URL" "$job_id" EXTRACTED_AUDIO "$OUTPUT_DIR/audio.wav"
download_artifact_by_type "$BASE_URL" "$job_id" TRANSCRIPT_JSON "$OUTPUT_DIR/transcript.json"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_SRT "$OUTPUT_DIR/subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" SUBTITLE_VTT "$OUTPUT_DIR/subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_JSON "$OUTPUT_DIR/target-subtitles.json"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_SRT "$OUTPUT_DIR/target-subtitles.srt"
download_artifact_by_type "$BASE_URL" "$job_id" TARGET_SUBTITLE_VTT "$OUTPUT_DIR/target-subtitles.vtt"
download_artifact_by_type "$BASE_URL" "$job_id" WORKER_SUMMARY "$OUTPUT_DIR/worker-summary.json"

if download_optional_artifact_by_type "$BASE_URL" "$job_id" BURNED_VIDEO "$OUTPUT_DIR/burned-video.mp4"; then
  echo "Downloaded burned video to $OUTPUT_DIR/burned-video.mp4"
else
  echo "No burned video artifact found; full-video burn-in may be disabled or skipped."
fi

if download_optional_artifact_by_type "$BASE_URL" "$job_id" DUBBING_AUDIO "$OUTPUT_DIR/dubbing-audio.mp3"; then
  echo "Downloaded dubbing audio to $OUTPUT_DIR/dubbing-audio.mp3"
else
  echo "No dubbing audio artifact found; TTS may be disabled."
fi

echo "Downloaded full-video demo artifacts to $OUTPUT_DIR"
```

- [x] Make the script executable:

```bash
chmod +x scripts/demo/docker-e2e-tears-of-steel-full.sh
```

- [x] Run:

```bash
bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh
scripts/demo/docker-e2e-tears-of-steel-full.sh --help
```

Expected: syntax check passes and help output lists the supported environment variables.

## Task 3: Document How To Run The Full Demo

**Files:**
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: script from Task 2 and attribution doc from Task 1.
- Produces: repeatable manual runbook for full-video demos.

- [x] Add a `Full Tears of Steel Demo` section to `docs/agent/docker-e2e-demo.md` with these commands:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env up -d --build linguaframe-backend
scripts/demo/docker-e2e-tears-of-steel-full.sh
```

- [x] Include the stable run recommendation:

```text
For the current full-video demo, prefer `LINGUAFRAME_FFMPEG_BURN_IN_ENABLED=false` in local `.env` before recreating the backend. Previous full-video attempts reached OpenAI translation/evaluation but exceeded the default 180-second subtitle burn-in timeout.
```

- [x] Record validation evidence in `docs/progress/execution-log.md`:

```markdown
## Full Video Demo Script

- Added `scripts/demo/docker-e2e-tears-of-steel-full.sh` for `/Users/wangbingqin/Downloads/tos_casting-720p.mp4`.
- Validated with `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh`.
- Validated help output with `scripts/demo/docker-e2e-tears-of-steel-full.sh --help`.
- Full runtime validation requires Docker backend and the local MP4; burned-video output is optional until full-video FFmpeg burn-in is optimized.
```

- [x] Run:

```bash
rg -n "Full Tears of Steel Demo|LINGUAFRAME_FFMPEG_BURN_IN_ENABLED|docker-e2e-tears-of-steel-full" docs/agent/docker-e2e-demo.md docs/progress/execution-log.md
```

Expected: docs include the run command, burn-in recommendation, and validation evidence.

## Task 4: Final Verification And Commit

**Files:**
- Verify all files changed in Tasks 1-3.

**Verification Commands:**
- `bash -n scripts/demo/docker-e2e-tears-of-steel-full.sh`
- `scripts/demo/docker-e2e-tears-of-steel-full.sh --help`
- `rg -n "demo-references|docker-e2e-tears-of-steel-full|Full Tears of Steel Demo|Creative Commons Attribution 3.0" README.md docs/product/demo-references.md docs/agent/docker-e2e-demo.md docs/progress/execution-log.md`
- `git status --short`

- [x] Confirm no media files, generated artifacts, `.env`, or credentials are staged.
- [x] Commit: `Add full Tears of Steel demo script`.
- [x] Merge branch `full-video-demo-script` back to `main` after verification.

## Completion Checklist

- [x] Repository documents Tears of Steel source, license reference, attribution, and local-file handling.
- [x] Full-video demo script can run against an existing local MP4 without requiring host FFmpeg.
- [x] Script downloads core artifacts and treats `BURNED_VIDEO` and `DUBBING_AUDIO` as optional.
- [x] Docker E2E docs explain how to run the full-video demo and why burn-in should be disabled for the current full-video path.
- [x] Validation evidence is recorded in `docs/progress/execution-log.md`.
- [x] Feature branch is merged back to `main` after verified implementation.
