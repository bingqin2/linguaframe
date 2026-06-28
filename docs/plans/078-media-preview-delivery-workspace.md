# Media Preview Delivery Workspace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make generated audio/video outputs reviewable from the browser as a complete media delivery workspace, with playback, download, hash/cache evidence, and script/documentation validation.

**Architecture:** Reuse existing `JobArtifact` metadata and artifact download endpoints; do not add new media pipeline stages. Replace the small ad hoc media preview grid with a first-class React panel that classifies `DUBBING_AUDIO`, `BURNED_VIDEO`, and `REVIEWED_BURNED_VIDEO`, then surfaces player controls, download links, content type, size, short SHA-256, and generated/reused cache state. Extend tests, demo docs, and terminal-safe summaries around existing artifact metadata.

**Tech Stack:** React + TypeScript + Vitest/jsdom frontend, Spring Boot artifact APIs already in place, Bash demo scripts, Markdown docs.

## Global Constraints

- Keep this slice focused on browser/operator media delivery; do not change TTS generation, FFmpeg burn-in, reviewed subtitle publishing, object storage, or artifact persistence.
- Do not expose object keys, local media paths, provider payloads, demo tokens, credentials, raw transcript text, raw subtitle text, corrected subtitle text, or media bytes in browser evidence or terminal summaries.
- Use existing artifact download routes: `/api/jobs/{jobId}/artifacts/{artifactId}/download`.
- Treat `REVIEWED_BURNED_VIDEO` as a separate reviewed handoff output; do not replace or hide the generated `BURNED_VIDEO`.
- Keep the feature testable without live Docker, FFmpeg, or OpenAI provider calls by using existing frontend fixtures and script metadata fixtures.

---

## Current Context

- `frontend/src/App.tsx` already renders `DUBBING_AUDIO` and `BURNED_VIDEO` players below the artifact table, but without status metrics, reviewed burned-video support, explicit downloads, or delivery evidence.
- `Result delivery` already knows about `DUBBING_AUDIO`, `BURNED_VIDEO`, and `REVIEWED_BURNED_VIDEO`; this feature should reuse the same artifact metadata rather than duplicate API calls.
- `scripts/demo/docker-e2e-success.sh` already downloads generated media artifacts when present; this feature should add terminal-safe media delivery summary lines instead of downloading new bytes.
- `docs/agent/smoke-test-checklist.md` already checks that audio/video previews exist; it needs stronger expectations for generated versus reviewed video outputs and metadata safety.

## Task 1: Frontend Media Delivery Panel

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `JobArtifact[]`, `LocalizationJob.jobId`, and `linguaFrameApi.artifactDownloadUrl(jobId, artifactId)`.
- Produces: a `MediaDeliveryPanel` React component rendered in `JobDetail` with `aria-label="Media delivery"`.

- [x] **Step 1: Write failing tests**

Add coverage to the existing `renders transcript, subtitles, artifact downloads, and media previews` test:

```tsx
const mediaDelivery = screen.getByRole('region', { name: /media delivery/i });
expect(within(mediaDelivery).getByText('Media delivery')).toBeInTheDocument();
expect(within(mediaDelivery).getByText('Dubbing audio')).toBeInTheDocument();
expect(within(mediaDelivery).getByText('Generated burned video')).toBeInTheDocument();
expect(within(mediaDelivery).getByLabelText(/dubbing audio player/i)).toHaveAttribute(
  'src',
  '/api/jobs/artifact-job/artifacts/artifact-audio/download'
);
expect(within(mediaDelivery).getByLabelText(/generated burned video player/i)).toHaveAttribute(
  'src',
  '/api/jobs/artifact-job/artifacts/artifact-video/download'
);
expect(within(mediaDelivery).getByRole('link', { name: /download dubbing audio/i })).toHaveAttribute(
  'href',
  '/api/jobs/artifact-job/artifacts/artifact-audio/download'
);
expect(within(mediaDelivery).getByText('audio/mpeg')).toBeInTheDocument();
expect(within(mediaDelivery).getByText('4.1 KB')).toBeInTheDocument();
expect(within(mediaDelivery).getByText('abcdef012345')).toBeInTheDocument();
```

Add a second test fixture with `REVIEWED_BURNED_VIDEO` and assert the panel shows both generated and reviewed video cards with distinct players and links.

- [x] **Step 2: Run tests to verify failure**

Run:

```bash
cd frontend && npm run test:run -- App -t "renders transcript, subtitles, artifact downloads, and media previews"
```

Expected: fail because `Media delivery` and reviewed-video playback are not implemented.

- [x] **Step 3: Implement `MediaDeliveryPanel`**

In `frontend/src/App.tsx`, replace the current `media-grid` block with:

```tsx
<MediaDeliveryPanel jobId={job.jobId} artifacts={artifacts} />
```

Add helpers:

```tsx
type MediaDeliveryItem = {
  key: string;
  label: string;
  playerLabel: string;
  artifact: JobArtifact | null;
  kind: 'audio' | 'video';
};

function buildMediaDeliveryItems(artifacts: JobArtifact[]): MediaDeliveryItem[] {
  return [
    {
      key: 'dubbing-audio',
      label: 'Dubbing audio',
      playerLabel: 'Dubbing audio player',
      kind: 'audio',
      artifact: findArtifact(artifacts, 'DUBBING_AUDIO')
    },
    {
      key: 'generated-burned-video',
      label: 'Generated burned video',
      playerLabel: 'Generated burned video player',
      kind: 'video',
      artifact: findArtifact(artifacts, 'BURNED_VIDEO')
    },
    {
      key: 'reviewed-burned-video',
      label: 'Reviewed burned video',
      playerLabel: 'Reviewed burned video player',
      kind: 'video',
      artifact: findArtifact(artifacts, 'REVIEWED_BURNED_VIDEO')
    }
  ];
}
```

Render a panel only when at least one media artifact exists. Each ready card must include player, filename, content type, size, SHA-256 short hash, cache state, and direct download link.

- [x] **Step 4: Style the panel**

In `frontend/src/styles.css`, add focused styles for `.media-delivery-panel`, `.media-delivery-grid`, `.media-card`, `.media-card-meta`, and `.media-card-actions`. Keep layout responsive, avoid nested card-in-card patterns, and preserve stable player width on mobile.

- [x] **Step 5: Verify frontend**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
```

Expected: all App tests pass and Vite build succeeds.

## Task 2: Terminal Demo Media Summary

**Files:**
- Modify: `scripts/demo/lib/linguaframe-demo.sh`
- Modify: `scripts/demo/docker-e2e-success.sh`
- Test: `scripts/demo/test-linguaframe-demo-client.sh`

**Interfaces:**
- Consumes: artifact list JSON from existing `list_artifacts`.
- Produces: `print_media_delivery_summary` with safe metadata-only lines.

- [x] **Step 1: Write failing script test**

Add `test_print_media_delivery_summary_is_metadata_only` with fixture artifacts for `DUBBING_AUDIO`, `BURNED_VIDEO`, `REVIEWED_BURNED_VIDEO`, plus unsafe fields containing object keys, local paths, raw text, and `OPENAI_API_KEY`. Expected output:

```text
mediaDeliveryReadyCount=3
mediaDeliveryArtifact=DUBBING_AUDIO:dubbing-audio.mp3:audio/mpeg:Generated
mediaDeliveryArtifact=BURNED_VIDEO:burned-video.mp4:video/mp4:Reused
mediaDeliveryArtifact=REVIEWED_BURNED_VIDEO:reviewed-burned-video.mp4:video/mp4:Generated
```

Assert output does not contain unsafe object keys, local paths, raw text, or token-like strings.

- [x] **Step 2: Run test to verify failure**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
```

Expected: fail because `print_media_delivery_summary` is not defined.

- [x] **Step 3: Implement summary helper**

In `scripts/demo/lib/linguaframe-demo.sh`, add:

```bash
print_media_delivery_summary() {
  jq -r '
    def cache_state: if .cacheHit then "Reused" else "Generated" end;
    [ .[] | select(.type == "DUBBING_AUDIO" or .type == "BURNED_VIDEO" or .type == "REVIEWED_BURNED_VIDEO") ] as $media
    | "mediaDeliveryReadyCount=\($media | length)",
      ($media[] | "mediaDeliveryArtifact=\(.type):\(.filename):\(.contentType):\(cache_state)")
  '
}
```

In `scripts/demo/docker-e2e-success.sh`, after artifact listing and reviewed publish, print:

```bash
echo "Media delivery summary for job $job_id:"
list_artifacts "$BASE_URL" "$job_id" | print_media_delivery_summary
```

- [x] **Step 4: Verify scripts**

Run:

```bash
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
```

Expected: script tests pass and syntax checks pass.

## Task 3: Documentation And Roadmap

**Files:**
- Modify: `README.md`
- Modify: `docs/agent/docker-e2e-demo.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/product/roadmap.md`
- Modify: `docs/product/target-state.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: implemented browser panel and script summary names.
- Produces: repo documentation explaining how to verify media delivery without relying on hidden terminal knowledge.

- [x] **Step 1: Update docs**

Document:

- Browser `Media delivery` panel for `DUBBING_AUDIO`, generated `BURNED_VIDEO`, and reviewed `REVIEWED_BURNED_VIDEO`.
- Expected player controls, direct download links, content type, size, short hash, and generated/reused cache state.
- Terminal summary lines from `print_media_delivery_summary`.
- Safety rule that summaries and evidence are metadata-only and do not expose paths, keys, raw text, credentials, or media bytes.

- [x] **Step 2: Update progress log**

Append a `2026-06-28` work section naming this feature, the plan path, implementation summary, and validation commands.

- [x] **Step 3: Verify documentation formatting**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.

## Task 4: Final Validation And Merge

**Files:**
- All files changed in Tasks 1-3.

**Interfaces:**
- Produces: a verified feature branch merged back to `main`.

- [x] **Step 1: Run full focused validation**

Run:

```bash
cd frontend && npm run test:run -- App
cd frontend && npm run build
bash scripts/demo/test-linguaframe-demo-client.sh
bash -n scripts/demo/lib/linguaframe-demo.sh scripts/demo/docker-e2e-success.sh
git diff --check
```

Expected: all commands pass.

- [x] **Step 2: Commit feature branch**

Commit message:

```bash
git commit -m "Add media delivery workspace"
```

- [x] **Step 3: Merge back to main**

After validation, merge the feature branch back to `main` with a no-ff merge commit.

- [x] **Step 4: Post-merge verification**

Run the same focused validation on `main`, append the post-merge evidence to `docs/progress/execution-log.md`, and commit the verification log.

