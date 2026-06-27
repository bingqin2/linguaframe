import { afterEach, describe, expect, test, vi } from 'vitest';

import {
  artifactDownloadUrl,
  getJob,
  jobEventsUrl,
  listJobs,
  listArtifacts,
  listPromptTemplates,
  cancelJob,
  retryJob,
  uploadMedia
} from './linguaframeApi';

describe('linguaframeApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  test('uploads media as multipart form data with target language', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        videoId: 'video-1',
        jobId: 'job-1',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        sourceObjectKey: 'uploads/video-1/source.mp4',
        status: 'STORED',
        jobStatus: 'QUEUED',
        targetLanguage: 'zh',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });
    const result = await uploadMedia(file, 'zh');

    expect(result.jobId).toBe('job-1');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('file')).toBe(file);
    expect((body as FormData).get('targetLanguage')).toBe('zh');
  });

  test('fetches job detail by id', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-1',
        videoId: 'video-1',
        targetLanguage: 'zh',
        status: 'PROCESSING',
        createdAt: '2026-06-26T10:00:00Z',
        startedAt: null,
        completedAt: null,
        failedAt: null,
        failureStage: null,
        failureReason: null,
        retryCount: 0,
        dispatchStatus: 'DISPATCHED',
        dispatchAttempts: 1,
        dispatchedAt: '2026-06-26T10:00:02Z',
        timelineEvents: [],
        usageSummary: {
          modelCallCount: 0,
          failedModelCallCount: 0,
          totalLatencyMs: 0,
          estimatedCostUsd: 0,
          inputTokens: null,
          outputTokens: null,
          audioSeconds: null,
          characterCount: null
        },
        cacheSummary: {
          cacheHitCount: 1,
          generatedArtifactCount: 2
        },
        modelCalls: []
      })
    );

    const job = await getJob('job-1');

    expect(job.status).toBe('PROCESSING');
    expect(job.cacheSummary.cacheHitCount).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1', { method: 'GET' });
  });

  test('builds same-origin job event stream urls', () => {
    expect(jobEventsUrl('job 1')).toBe('/api/jobs/job%201/events');
  });

  test('lists prompt templates', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse([
        {
          version: 'openai-subtitle-translation-v1',
          purpose: 'SUBTITLE_TRANSLATION',
          provider: 'OPENAI',
          modelFamily: 'responses',
          systemPrompt: 'Translate subtitles.',
          outputContract: 'Return JSON with segments[{index,text}] preserving order and timing.',
          active: true
        }
      ])
    );

    const templates = await listPromptTemplates();

    expect(templates[0]?.version).toBe('openai-subtitle-translation-v1');
    expect(templates[0]?.purpose).toBe('SUBTITLE_TRANSLATION');
    expect(fetchMock).toHaveBeenCalledWith('/api/prompt-templates', { method: 'GET' });
  });

  test('retries a failed job', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-1',
        videoId: 'video-1',
        targetLanguage: 'zh',
        status: 'RETRYING',
        createdAt: '2026-06-26T10:00:00Z',
        startedAt: null,
        completedAt: null,
        failedAt: null,
        failureStage: null,
        failureReason: null,
        retryCount: 1,
        dispatchStatus: 'PENDING',
        dispatchAttempts: 0,
        dispatchedAt: null,
        timelineEvents: [],
        usageSummary: null,
        cacheSummary: {
          cacheHitCount: 0,
          generatedArtifactCount: 0
        },
        modelCalls: []
      })
    );

    const job = await retryJob('job-1');

    expect(job.retryCount).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1/retry', { method: 'POST' });
  });

  test('cancels an active job', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-1',
        videoId: 'video-1',
        targetLanguage: 'zh',
        status: 'CANCELLED',
        createdAt: '2026-06-26T10:00:00Z',
        startedAt: null,
        completedAt: null,
        failedAt: null,
        failureStage: null,
        failureReason: null,
        retryCount: 0,
        dispatchStatus: 'PENDING',
        dispatchAttempts: 0,
        dispatchedAt: null,
        timelineEvents: [],
        usageSummary: null,
        cacheSummary: {
          cacheHitCount: 0,
          generatedArtifactCount: 0
        },
        modelCalls: []
      })
    );

    const job = await cancelJob('job-1');

    expect(job.status).toBe('CANCELLED');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1/cancel', { method: 'POST' });
  });

  test('lists artifacts and builds same-origin download urls', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse([
        {
          artifactId: 'artifact-1',
          jobId: 'job-1',
          type: 'SUBTITLE_VTT',
          filename: 'subtitles.vtt',
          contentType: 'text/vtt',
          sizeBytes: 42,
          contentSha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
          cacheHit: true,
          sourceArtifactId: 'source-artifact-1',
          createdAt: '2026-06-26T10:00:05Z'
        }
      ])
    );

    const artifacts = await listArtifacts('job-1');

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.contentSha256).toBe(
      '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
    );
    expect(artifacts[0]?.cacheHit).toBe(true);
    expect(artifacts[0]?.sourceArtifactId).toBe('source-artifact-1');
    expect(artifactDownloadUrl('job-1', 'artifact-1')).toBe(
      '/api/jobs/job-1/artifacts/artifact-1/download'
    );
  });

  test('lists jobs with default paging params', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobs: [],
        limit: 20,
        offset: 0,
        total: 0
      })
    );

    const result = await listJobs();

    expect(result.jobs).toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs?limit=20&offset=0', { method: 'GET' });
  });

  test('lists jobs with status filter and custom paging params', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobs: [
          {
            jobId: 'failed-job',
            videoId: 'failed-video',
            filename: 'failed.mp4',
            targetLanguage: 'zh-CN',
            status: 'FAILED',
            createdAt: '2026-06-26T10:00:00Z',
            startedAt: null,
            completedAt: null,
            failedAt: '2026-06-26T10:01:00Z',
            failureStage: 'AUDIO_EXTRACTION',
            failureReason: 'FFmpeg failed safely',
            retryCount: 1,
            estimatedCostUsd: 0
          }
        ],
        limit: 10,
        offset: 20,
        total: 1
      })
    );

    const result = await listJobs({ status: 'FAILED', limit: 10, offset: 20 });

    expect(result.jobs[0]?.status).toBe('FAILED');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs?status=FAILED&limit=10&offset=20', {
      method: 'GET'
    });
  });

  test('omits all-status filter when listing jobs', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobs: [],
        limit: 20,
        offset: 0,
        total: 0
      })
    );

    await listJobs({ status: 'ALL' });

    expect(fetchMock).toHaveBeenCalledWith('/api/jobs?limit=20&offset=0', { method: 'GET' });
  });

  test('throws concise api errors without raw response body dumps', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        {
          message: 'Upload failed',
          detail: 'This raw diagnostic text should not be included in the thrown message'
        },
        { status: 400 }
      )
    );

    await expect(getJob('missing-job')).rejects.toThrow('Upload failed');
    await expect(getJob('missing-job')).rejects.not.toThrow('raw diagnostic');
  });
});

function jsonResponse(body: unknown, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    status: init?.status ?? 200,
    headers: {
      'Content-Type': 'application/json'
    }
  });
}
