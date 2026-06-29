import { describe, expect, test } from 'vitest';

import type { JobArtifact } from './jobTypes';
import { calculateNarrationPlayheadPercent, selectNarrationPreviewSource } from './narrationPreview';

function artifact(type: JobArtifact['type'], artifactId = `${type.toLowerCase()}-artifact`): JobArtifact {
  return {
    artifactId,
    jobId: 'job-preview',
    type,
    filename: `${artifactId}.mp4`,
    contentType: 'video/mp4',
    sizeBytes: 1024,
    contentSha256: 'sha256',
    cacheHit: false,
    sourceArtifactId: null,
    createdAt: '2026-06-30T00:00:00Z'
  };
}

describe('narrationPreview', () => {
  test('prefers narrated video over burned video and source video', () => {
    const source = selectNarrationPreviewSource({
      jobId: 'job-preview',
      videoId: 'video-preview',
      artifacts: [
        artifact('BURNED_VIDEO', 'burned-1'),
        artifact('NARRATED_VIDEO', 'narrated-1')
      ],
      artifactDownloadUrl: (jobId, artifactId) => `/api/jobs/${jobId}/artifacts/${artifactId}/download`,
      sourceMediaDownloadUrl: (videoId) => `/api/media/uploads/${videoId}/source/download`
    });

    expect(source).toEqual({
      kind: 'narrated-video',
      label: 'Narrated video',
      url: '/api/jobs/job-preview/artifacts/narrated-1/download',
      artifactId: 'narrated-1',
      available: true
    });
  });

  test('falls back to burned video before source video', () => {
    const source = selectNarrationPreviewSource({
      jobId: 'job-preview',
      videoId: 'video-preview',
      artifacts: [artifact('BURNED_VIDEO', 'burned-1')],
      artifactDownloadUrl: (jobId, artifactId) => `/api/jobs/${jobId}/artifacts/${artifactId}/download`,
      sourceMediaDownloadUrl: (videoId) => `/api/media/uploads/${videoId}/source/download`
    });

    expect(source).toMatchObject({
      kind: 'burned-video',
      label: 'Burned video',
      url: '/api/jobs/job-preview/artifacts/burned-1/download',
      artifactId: 'burned-1',
      available: true
    });
  });

  test('falls back to source video when generated video artifacts are unavailable', () => {
    const source = selectNarrationPreviewSource({
      jobId: 'job-preview',
      videoId: 'video-preview',
      artifacts: [artifact('NARRATION_AUDIO', 'audio-1')],
      artifactDownloadUrl: (jobId, artifactId) => `/api/jobs/${jobId}/artifacts/${artifactId}/download`,
      sourceMediaDownloadUrl: (videoId) => `/api/media/uploads/${videoId}/source/download`
    });

    expect(source).toEqual({
      kind: 'source-video',
      label: 'Source video',
      url: '/api/media/uploads/video-preview/source/download',
      artifactId: null,
      available: true
    });
  });

  test('reports unavailable source when no video id is present', () => {
    const source = selectNarrationPreviewSource({
      jobId: 'job-preview',
      videoId: '',
      artifacts: [],
      artifactDownloadUrl: (jobId, artifactId) => `/api/jobs/${jobId}/artifacts/${artifactId}/download`,
      sourceMediaDownloadUrl: (videoId) => `/api/media/uploads/${videoId}/source/download`
    });

    expect(source).toEqual({
      kind: 'source-video',
      label: 'Source video unavailable',
      url: '',
      artifactId: null,
      available: false
    });
  });

  test('calculates clamped playhead percent', () => {
    expect(calculateNarrationPlayheadPercent(15, 10, 30)).toBe(25);
    expect(calculateNarrationPlayheadPercent(5, 10, 30)).toBe(0);
    expect(calculateNarrationPlayheadPercent(35, 10, 30)).toBe(100);
    expect(calculateNarrationPlayheadPercent(10, 10, 10)).toBe(0);
  });
});
