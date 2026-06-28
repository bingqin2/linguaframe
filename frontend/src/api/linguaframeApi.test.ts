import { afterEach, describe, expect, test, vi } from 'vitest';

import type { RetentionCleanupResult } from '../domain/jobTypes';
import {
  artifactArchiveDownloadUrl,
  artifactDownloadUrl,
  clearSubtitleDraft,
  getJob,
  getSubtitleDraft,
  getSubtitleReview,
  jobDiagnosticsDownloadUrl,
  jobEvidenceBundleDownloadUrl,
  jobEvidenceMarkdownDownloadUrl,
  jobEventsUrl,
  listJobs,
  listArtifacts,
  listPromptTemplates,
  getOperatorDashboard,
  getRetentionCleanupPreview,
  getRuntimeDependencies,
  getRuntimeLiveChecks,
  getDemoSession,
  loginDemoSession,
  logoutDemoSession,
  publishReviewedSubtitles,
  readDemoToken,
  cancelJob,
  retryJob,
  runRetentionCleanup,
  subtitleDraftExportUrl,
  updateSubtitleDraft,
  validateUpload,
  writeDemoToken,
  uploadMedia
} from './linguaframeApi';

describe('linguaframeApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
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
    expect((body as FormData).has('ttsVoice')).toBe(false);
  });

  test('uploads media with selected tts voice when provided', async () => {
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
        ttsVoice: 'verse',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(new File(['demo'], 'sample.mp4', { type: 'video/mp4' }), 'zh', ' verse ');

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('ttsVoice')).toBe('verse');
  });

  test('uploads media with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
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

    await uploadMedia(new File(['demo'], 'sample.mp4', { type: 'video/mp4' }), 'zh');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads',
      expect.objectContaining({
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      })
    );
  });

  test('reads private demo owner session status', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        accessGateEnabled: true,
        authenticated: false,
        headerName: 'X-LinguaFrame-Demo-Token',
        mode: 'OWNER_SESSION_REQUIRED'
      })
    );

    const status = await getDemoSession();

    expect(status.mode).toBe('OWNER_SESSION_REQUIRED');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/demo-session',
      expect.objectContaining({ method: 'GET' })
    );
  });

  test('creates and clears private demo owner session without storing fallback token', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          accessGateEnabled: true,
          authenticated: true,
          headerName: 'X-LinguaFrame-Demo-Token',
          mode: 'OWNER_SESSION_ACTIVE'
        })
      )
      .mockResolvedValueOnce(
        jsonResponse({
          accessGateEnabled: true,
          authenticated: false,
          headerName: 'X-LinguaFrame-Demo-Token',
          mode: 'OWNER_SESSION_REQUIRED'
        })
      );

    const loginStatus = await loginDemoSession('private-demo-token');
    const logoutStatus = await logoutDemoSession();

    expect(loginStatus.authenticated).toBe(true);
    expect(logoutStatus.authenticated).toBe(false);
    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBeNull();
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/demo-session/login',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: 'private-demo-token' })
      })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/demo-session/logout',
      expect.objectContaining({ method: 'POST' })
    );
  });

  test('validates media upload as multipart form data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        valid: true,
        code: 'READY',
        message: 'File is ready for upload.',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        maxFileSizeBytes: 104857600,
        durationSeconds: 42,
        maxDurationSeconds: 300,
        supportedContentTypes: ['video/mp4', 'video/quicktime']
      })
    );

    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });
    const result = await validateUpload(file);

    expect(result.code).toBe('READY');
    expect(result.durationSeconds).toBe(42);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/validate',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('file')).toBe(file);
  });

  test('validates media upload with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        valid: false,
        code: 'DURATION_TOO_LONG',
        message: 'The uploaded video exceeds the 300 second duration limit.',
        filename: 'long.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        maxFileSizeBytes: 104857600,
        durationSeconds: 301,
        maxDurationSeconds: 300,
        supportedContentTypes: ['video/mp4', 'video/quicktime']
      })
    );

    await validateUpload(new File(['demo'], 'long.mp4', { type: 'video/mp4' }));

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/validate',
      expect.objectContaining({
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      })
    );
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
          generatedArtifactCount: 2,
          providerCacheHitCount: 1
        },
        modelCalls: []
      })
    );

    const job = await getJob('job-1');

    expect(job.status).toBe('PROCESSING');
    expect(job.cacheSummary.cacheHitCount).toBe(1);
    expect(job.cacheSummary.providerCacheHitCount).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1', { method: 'GET' });
  });

  test('builds artifact archive download URL with encoded job id', () => {
    expect(artifactArchiveDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/artifacts/archive/download'
    );
  });

  test('builds diagnostics download URL with encoded job id', () => {
    expect(jobDiagnosticsDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/diagnostics/download'
    );
  });

  test('builds evidence markdown download URL with encoded job id', () => {
    expect(jobEvidenceMarkdownDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/evidence/markdown/download'
    );
  });

  test('builds evidence bundle download URL with encoded job id', () => {
    expect(jobEvidenceBundleDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/evidence/bundle/download'
    );
  });

  test('sends demo access token header for json api requests when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
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
          cacheHitCount: 0,
          generatedArtifactCount: 0,
          providerCacheHitCount: 0
        },
        modelCalls: []
      })
    );

    await getJob('job-1');

    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job-1', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('stores and clears demo access token', () => {
    writeDemoToken(window.localStorage, ' private-demo-token ');

    expect(readDemoToken(window.localStorage)).toBe('private-demo-token');

    writeDemoToken(window.localStorage, '');

    expect(readDemoToken(window.localStorage)).toBe('');
    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBeNull();
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

  test('fetches operator dashboard with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(operatorDashboardFixture())
    );

    const dashboard = await getOperatorDashboard();

    expect(dashboard.modelCalls.modelCallCount).toBe(2);
    expect(dashboard.cache.providerCacheHitCount).toBe(1);
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/dashboard', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches runtime dependencies with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(runtimeDependenciesFixture())
    );

    const dependencies = await getRuntimeDependencies();

    expect(dependencies.runtime.latestMigrationVersion).toBe(19);
    expect(dependencies.readiness.worker.role).toBe('COMBINED');
    expect(dependencies.readiness.providers.translation.provider).toBe('demo');
    expect(fetchMock).toHaveBeenCalledWith('/api/runtime/dependencies', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches runtime live checks with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(runtimeLiveChecksFixture())
    );

    const liveChecks = await getRuntimeLiveChecks();

    expect(liveChecks.healthy).toBe(true);
    expect(liveChecks.checks.database.status).toBe('UP');
    expect(fetchMock).toHaveBeenCalledWith('/api/runtime/live-checks', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches retention cleanup preview', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(retentionCleanupResultFixture()));

    const result = await getRetentionCleanupPreview();

    expect(result.dryRun).toBe(true);
    expect(result.candidateJobCount).toBe(2);
    expect(fetchMock).toHaveBeenCalledWith('/api/retention/cleanup/preview', {
      method: 'GET'
    });
  });

  test('runs retention cleanup with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(retentionCleanupResultFixture({ dryRun: false })));

    const result = await runRetentionCleanup();

    expect(result.dryRun).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('/api/retention/cleanup/run', {
      method: 'POST',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
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
          generatedArtifactCount: 0,
          providerCacheHitCount: 0
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
          generatedArtifactCount: 0,
          providerCacheHitCount: 0
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

  test('fetches subtitle review summary with encoded job id and language query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-1',
        targetLanguage: 'zh-CN',
        segmentCount: 2,
        missingTargetCount: 0,
        timingMismatchCount: 1,
        averageDurationMs: 1200,
        maxDurationMs: 1600,
        qualityScore: 88,
        qualityVerdict: 'NEEDS_REVIEW',
        qualityIssueCount: 1,
        qualitySuggestedFixCount: 1,
        downloadableSubtitleArtifactCount: 3,
        segments: [
          {
            index: 0,
            startMs: 0,
            endMs: 1000,
            sourceText: 'Hello.',
            targetText: '你好。',
            durationMs: 1000,
            timingDeltaMs: 0,
            status: 'ALIGNED'
          }
        ]
      })
    );

    const review = await getSubtitleReview('job with/slash', 'zh-CN');

    expect(review.segmentCount).toBe(2);
    expect(review.segments[0]?.status).toBe('ALIGNED');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%2Fslash/subtitle-review?language=zh-CN',
      { method: 'GET' }
    );
  });

  test('fetches, updates, clears, and builds export urls for subtitle drafts', async () => {
    const draftResponse = {
      jobId: 'job-1',
      targetLanguage: 'zh-CN',
      segmentCount: 2,
      editedSegmentCount: 1,
      lastUpdatedAt: '2026-06-28T10:00:00Z',
      segments: [
        {
          index: 0,
          startMs: 0,
          endMs: 1000,
          sourceText: 'Hello.',
          generatedText: '你好。',
          draftText: '你好。',
          edited: false,
          updatedAt: null
        },
        {
          index: 1,
          startMs: 1000,
          endMs: 2000,
          sourceText: 'Welcome.',
          generatedText: '欢迎。',
          draftText: '欢迎你。',
          edited: true,
          updatedAt: '2026-06-28T10:00:00Z'
        }
      ]
    };
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(draftResponse))
      .mockResolvedValueOnce(jsonResponse({ ...draftResponse, editedSegmentCount: 2 }))
      .mockResolvedValueOnce(jsonResponse({ ...draftResponse, editedSegmentCount: 0 }));

    const draft = await getSubtitleDraft('job with/slash', 'zh-CN');
    const updated = await updateSubtitleDraft('job with/slash', 'zh-CN', {
      segments: [{ index: 1, text: '欢迎你。' }]
    });
    const cleared = await clearSubtitleDraft('job with/slash', 'zh-CN');

    expect(draft.editedSegmentCount).toBe(1);
    expect(updated.editedSegmentCount).toBe(2);
    expect(cleared.editedSegmentCount).toBe(0);
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/jobs/job%20with%2Fslash/subtitle-draft?language=zh-CN',
      { method: 'GET' }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/jobs/job%20with%2Fslash/subtitle-draft?language=zh-CN',
      expect.objectContaining({
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ segments: [{ index: 1, text: '欢迎你。' }] })
      })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/jobs/job%20with%2Fslash/subtitle-draft?language=zh-CN',
      { method: 'DELETE' }
    );
    expect(subtitleDraftExportUrl('job with/slash', 'zh-CN', 'srt')).toBe(
      '/api/jobs/job%20with%2Fslash/subtitle-draft/export?language=zh-CN&format=srt'
    );
  });

  test('publishes reviewed subtitle artifacts with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job reviewed/slash',
        targetLanguage: 'zh-CN',
        burnedVideoRequested: true,
        burnedVideoCreated: false,
        artifacts: [
          {
            artifactId: 'reviewed-srt',
            jobId: 'job reviewed/slash',
            type: 'REVIEWED_SUBTITLE_SRT',
            filename: 'reviewed-subtitles.zh-CN.srt',
            contentType: 'application/x-subrip;charset=UTF-8',
            sizeBytes: 120,
            contentSha256: '1234567890abcdef',
            cacheHit: false,
            sourceArtifactId: null,
            createdAt: '2026-06-28T10:30:00Z'
          }
        ]
      })
    );

    const result = await publishReviewedSubtitles('job reviewed/slash', {
      language: 'zh-CN',
      includeBurnedVideo: true
    });

    expect(result.artifacts[0].type).toBe('REVIEWED_SUBTITLE_SRT');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20reviewed%2Fslash/subtitle-draft/publish',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        },
        body: JSON.stringify({ language: 'zh-CN', includeBurnedVideo: true })
      }
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

function operatorDashboardFixture() {
  return {
    statusCounts: [
      { status: 'QUEUED', count: 1 },
      { status: 'RETRYING', count: 0 },
      { status: 'PROCESSING', count: 1 },
      { status: 'COMPLETED', count: 2 },
      { status: 'FAILED', count: 1 },
      { status: 'CANCELLED', count: 0 }
    ],
    recentFailures: [
      {
        jobId: 'failed-dashboard-job',
        videoId: 'failed-dashboard-video',
        filename: 'failed.mp4',
        failureStage: 'DUBBING_AUDIO_GENERATION',
        failureReason: 'OpenAI TTS request failed with status 401',
        failedAt: '2026-06-27T06:00:00Z'
      }
    ],
    modelCalls: {
      modelCallCount: 2,
      failedModelCallCount: 1,
      totalLatencyMs: 200,
      estimatedCostUsd: 0.00015
    },
    cache: {
      artifactCacheHitCount: 1,
      generatedArtifactCount: 3,
      providerCacheHitCount: 1
    }
  };
}

function runtimeDependenciesFixture() {
  return {
    runtime: {
      appVersion: '0.0.1-SNAPSHOT',
      latestMigrationVersion: 19,
      requiredRoutes: [
        '/api/runtime/dependencies',
        '/api/media/uploads',
        '/api/jobs/{jobId}',
        '/api/jobs/{jobId}/diagnostics/download',
        '/api/jobs/{jobId}/artifacts/archive/download'
      ]
    },
    database: { type: 'mysql', host: 'localhost', port: 3306 },
    redis: { type: 'redis', host: 'localhost', port: 6379 },
    rabbitmq: { type: 'rabbitmq', host: 'localhost', port: 5672 },
    storage: { type: 'minio', endpoint: 'http://localhost:9000', bucket: 'linguaframe-artifacts' },
    readiness: {
      demoAccessGate: false,
      worker: {
        dispatchEnabled: true,
        executionEnabled: true,
        role: 'COMBINED',
        maxRetries: 2,
        dispatchBatchSize: 10,
        dispatchIntervalMs: 5000
      },
      media: { maxFileSizeMb: 100, maxDurationSeconds: 300 },
      ffmpeg: {
        audioEnabled: true,
        burnInEnabled: true,
        binaryConfigured: true,
        workspaceConfigured: true,
        audioTimeoutSeconds: 120,
        burnInTimeoutSeconds: 180
      },
      providers: {
        transcription: { enabled: true, provider: 'demo', model: '', credentialsConfigured: false },
        translation: { enabled: true, provider: 'demo', model: '', credentialsConfigured: false },
        tts: { enabled: false, provider: 'demo', model: '', credentialsConfigured: false },
        evaluation: { enabled: false, provider: 'demo', model: '', credentialsConfigured: false }
      },
      features: {
        jobStatusCache: { enabled: true },
        uploadRateLimit: { enabled: false },
        retentionCleanup: { enabled: false },
        costTracking: { enabled: true },
        budgetGuard: { enabled: false }
      }
    }
  };
}

function runtimeLiveChecksFixture() {
  return {
    healthy: true,
    checkedAt: '2026-06-28T08:00:00Z',
    checks: {
      database: { status: 'UP', latencyMs: 5, message: 'Database probe succeeded' },
      redis: { status: 'UP', latencyMs: 4, message: 'Redis ping succeeded' },
      rabbitmq: { status: 'UP', latencyMs: 6, message: 'RabbitMQ connection succeeded' },
      minio: { status: 'UP', latencyMs: 7, message: 'MinIO bucket is reachable' },
      ffmpeg: { status: 'UP', latencyMs: 8, message: 'FFmpeg executable responded' }
    }
  };
}

function retentionCleanupResultFixture(
  overrides: Partial<RetentionCleanupResult> = {}
): RetentionCleanupResult {
  return {
    dryRun: true,
    candidateJobCount: 2,
    deletedJobCount: 0,
    deletedVideoCount: 0,
    deletedObjectCount: 0,
    skippedObjectCount: 1,
    failureCount: 0,
    ...overrides
  };
}
