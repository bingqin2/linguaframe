import type { JobArtifact } from './jobTypes';

export type NarrationPreviewSourceKind = 'narrated-video' | 'burned-video' | 'source-video';

export interface NarrationPreviewSource {
  kind: NarrationPreviewSourceKind;
  label: string;
  url: string;
  artifactId: string | null;
  available: boolean;
}

export function selectNarrationPreviewSource(input: {
  jobId: string;
  videoId: string;
  artifacts: JobArtifact[];
  artifactDownloadUrl: (jobId: string, artifactId: string) => string;
  sourceMediaDownloadUrl: (videoId: string) => string;
}): NarrationPreviewSource {
  const narratedVideo = findFirstArtifact(input.artifacts, 'NARRATED_VIDEO');
  if (narratedVideo) {
    return artifactSource('narrated-video', 'Narrated video', input.jobId, narratedVideo, input.artifactDownloadUrl);
  }

  const burnedVideo = findFirstArtifact(input.artifacts, 'BURNED_VIDEO');
  if (burnedVideo) {
    return artifactSource('burned-video', 'Burned video', input.jobId, burnedVideo, input.artifactDownloadUrl);
  }

  if (!input.videoId) {
    return {
      kind: 'source-video',
      label: 'Source video unavailable',
      url: '',
      artifactId: null,
      available: false
    };
  }

  return {
    kind: 'source-video',
    label: 'Source video',
    url: input.sourceMediaDownloadUrl(input.videoId),
    artifactId: null,
    available: true
  };
}

export function calculateNarrationPlayheadPercent(
  currentSeconds: number,
  startSeconds: number,
  endSeconds: number
): number {
  const spanSeconds = endSeconds - startSeconds;
  if (spanSeconds <= 0) {
    return 0;
  }
  return clamp(((currentSeconds - startSeconds) / spanSeconds) * 100, 0, 100);
}

function artifactSource(
  kind: NarrationPreviewSourceKind,
  label: string,
  jobId: string,
  artifact: JobArtifact,
  artifactDownloadUrl: (jobId: string, artifactId: string) => string
): NarrationPreviewSource {
  return {
    kind,
    label,
    url: artifactDownloadUrl(jobId, artifact.artifactId),
    artifactId: artifact.artifactId,
    available: true
  };
}

function findFirstArtifact(artifacts: JobArtifact[], type: JobArtifact['type']): JobArtifact | null {
  return artifacts.find((artifact) => artifact.type === type) ?? null;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
