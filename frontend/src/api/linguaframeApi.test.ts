import { afterEach, describe, expect, test, vi } from 'vitest';

import type { RetentionCleanupResult } from '../domain/jobTypes';
import {
  artifactArchiveDownloadUrl,
  artifactDownloadUrl,
  clearNarrationWorkspace,
  clearSubtitleDraft,
  downloadNarrationEvidenceMarkdown,
  downloadNarrationEvidenceZip,
  getJob,
  getMediaUpload,
  getNarrationEvidence,
  getNarrationWorkspace,
  getReviewedSubtitleWorkflow,
  getSubtitleReviewEvidence,
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
  getModelUsageLedger,
  downloadModelUsageLedgerMarkdown,
  getDemoSessionCommandCenter,
  downloadDemoSessionCommandCenterMarkdown,
  downloadDemoSessionEvidencePackageZip,
  getDemoSampleMediaCatalog,
  getDemoRunLauncher,
  getDemoPresentationCockpit,
  getPrivateDemoEvidenceGallery,
  getPrivateDemoRunArchive,
  getPrivateDemoLaunchRehearsal,
  getPrivateDemoOperations,
  getRetentionCleanupPreview,
  getRuntimeDependencies,
  getRuntimeLiveChecks,
  getOpenAiReadinessEvidence,
  downloadOpenAiReadinessEvidenceMarkdown,
  getOpenAiSmokeProof,
  downloadOpenAiSmokeProofMarkdown,
  getDemoReviewerWorkspace,
  downloadDemoReviewerWorkspaceMarkdown,
  downloadDemoReviewerWorkspaceZip,
  getDemoHandoffPortal,
  downloadDemoHandoffPortalMarkdown,
  downloadDemoHandoffPortalZip,
  downloadSubtitleReviewEvidenceMarkdown,
  downloadSubtitleReviewEvidenceZip,
  generateNarrationAudio,
  generateNarratedVideo,
  getDemoSession,
  getAuthSession,
  loginAuthSession,
  logoutAuthSession,
  getDeliveryManifest,
  getJobComparison,
  getDemoRunMatrix,
  getDemoRunMonitor,
  getDemoPresenterPack,
  getDemoRunVariance,
  downloadDemoRunVarianceMarkdown,
  getDemoEvidenceClosure,
  downloadDemoEvidenceClosureMarkdown,
  downloadDemoEvidenceClosureZip,
  getDemoShareSheet,
  getDemoUploadReadiness,
  getOwnerQuotaPreflight,
  estimateUploadCost,
  estimateUploadExecutionPlan,
  downloadUploadExecutionPlanMarkdown,
  downloadUploadDecisionPackageMarkdown,
  downloadUploadDecisionPackageZip,
  listDemoRunProfiles,
  loginDemoSession,
  logoutDemoSession,
  deliveryManifestMarkdownDownloadUrl,
  demoRunMonitorMarkdownDownloadUrl,
  demoShareSheetMarkdownDownloadUrl,
  jobComparisonMarkdownDownloadUrl,
  publishReviewedSubtitles,
  readDemoToken,
  readAuthToken,
  cancelJob,
  retryJob,
  runRetentionCleanup,
  saveNarrationWorkspace,
  subtitleDraftExportUrl,
  sourceMediaDownloadUrl,
  updateSubtitleDraft,
  validateUpload,
  writeDemoToken,
  writeAuthToken,
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

  test('uploads media with selected translation style when provided', async () => {
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
        translationStyle: 'FORMAL',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(new File(['demo'], 'sample.mp4', { type: 'video/mp4' }), 'zh', 'verse', ' formal ');

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('translationStyle')).toBe('FORMAL');
  });

  test('uploads media with selected subtitle style preset when provided', async () => {
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
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(new File(['demo'], 'sample.mp4', { type: 'video/mp4' }), 'zh', 'verse', 'formal', ' high_contrast ');

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
  });

  test('uploads media with translation glossary only when non blank', async () => {
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
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        translationGlossaryEntryCount: 1,
        translationGlossaryHash: 'abc123',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' }),
      'zh',
      'verse',
      'formal',
      'high_contrast',
      ' Maya => 玛雅 '
    );

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('translationGlossary')).toBe('Maya => 玛雅');

    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        videoId: 'video-2',
        jobId: 'job-2',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        sourceObjectKey: 'uploads/video-2/source.mp4',
        status: 'STORED',
        jobStatus: 'QUEUED',
        targetLanguage: 'zh',
        createdAt: '2026-06-26T10:01:00Z'
      })
    );
    fetchMock.mockClear();
    await uploadMedia(new File(['demo'], 'sample.mp4', { type: 'video/mp4' }), 'zh', undefined, undefined, undefined, '   ');
    const blankBody = fetchMock.mock.calls[0]?.[1]?.body;
    expect(blankBody).toBeInstanceOf(FormData);
    expect((blankBody as FormData).has('translationGlossary')).toBe(false);
  });

  test('uploads media with selected subtitle polishing mode when provided', async () => {
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
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        subtitlePolishingMode: 'BALANCED',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' }),
      'zh',
      'verse',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      ' balanced '
    );

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('subtitlePolishingMode')).toBe('BALANCED');
  });

  test('manages narration workspace endpoints', async () => {
    const workspaceResponse = {
        jobId: 'job-narration',
        status: 'DRAFT_READY',
        segmentCount: 1,
        totalDurationSeconds: 13,
        totalCharacterCount: 24,
        generationReady: true,
        segments: [],
        safetyNotes: []
      };
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(jsonResponse(workspaceResponse))
    );

    await getNarrationWorkspace('job-narration');
    await saveNarrationWorkspace('job-narration', {
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          text: 'Explain the first scene.',
          voice: 'alloy'
        }
      ]
    });
    await clearNarrationWorkspace('job-narration');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/jobs/job-narration/narration-workspace');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET' });
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/jobs/job-narration/narration-workspace');
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: 'PUT' });
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          text: 'Explain the first scene.',
          voice: 'alloy'
        }
      ]
    });
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/jobs/job-narration/narration-workspace');
    expect(fetchMock.mock.calls[2]?.[1]).toMatchObject({ method: 'DELETE' });
  });

  test('generates narration audio, narrated video, and downloads narration evidence', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-narration',
        artifactId: 'artifact-narration',
        filename: 'narration-audio.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 3,
        segmentCount: 1,
        totalCharacterCount: 24,
        totalTimelineDurationSeconds: 13,
        voiceSummary: 'alloy',
        status: 'READY'
      })
    );

    await generateNarrationAudio('job-narration');
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        jobId: 'job-narration',
        artifactId: 'artifact-narrated-video',
        filename: 'narrated-video.mp4',
        contentType: 'video/mp4',
        sizeBytes: 3,
        baseVideoType: 'BURNED_VIDEO',
        narrationAudioArtifactId: 'artifact-narration',
        status: 'READY'
      })
    );
    await generateNarratedVideo('job-narration');
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        jobId: 'job-narration',
        status: 'READY',
        segmentCount: 1,
        totalCharacterCount: 24,
        totalTimelineDurationSeconds: 13,
        narrationAudioReady: true,
        audioArtifactCount: 1,
        narratedVideoReady: true,
        narratedVideoArtifactCount: 1,
        checks: [],
        safeLinks: [],
        packageEntries: [],
        safetyNotes: []
      })
    );
    await getNarrationEvidence('job-narration');
    fetchMock.mockResolvedValueOnce(new Response(new Blob(['markdown'])));
    await downloadNarrationEvidenceMarkdown('job-narration');
    fetchMock.mockResolvedValueOnce(new Response(new Blob(['zip'])));
    await downloadNarrationEvidenceZip('job-narration');

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/jobs/job-narration/narration-workspace/generate-audio');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST' });
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/jobs/job-narration/narration-workspace/generate-video');
    expect(fetchMock.mock.calls[1]?.[1]).toMatchObject({ method: 'POST' });
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/jobs/job-narration/narration-evidence');
    expect(fetchMock.mock.calls[3]?.[0]).toBe('/api/jobs/job-narration/narration-evidence/markdown/download');
    expect(fetchMock.mock.calls[4]?.[0]).toBe('/api/jobs/job-narration/narration-evidence/download');
  });

  test('uploads media with selected demo profile id when provided', async () => {
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
        demoProfileId: 'tears-showcase',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    await uploadMedia(
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' }),
      'zh',
      'verse',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      'balanced',
      ' tears-showcase '
    );

    const body = fetchMock.mock.calls[0]?.[1]?.body;
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('demoProfileId')).toBe('tears-showcase');
  });

  test('lists demo run profiles', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse([
        {
          id: 'quick-baseline',
          label: 'Quick baseline',
          description: 'Fast default demo run.',
          targetLanguage: 'zh-CN',
          ttsVoice: '',
          translationStyle: 'NATURAL',
          subtitleStylePreset: 'STANDARD',
          subtitlePolishingMode: 'OFF',
          translationGlossary: ''
        }
      ])
    );

    const profiles = await listDemoRunProfiles();

    expect(profiles[0]?.id).toBe('quick-baseline');
    expect(fetchMock).toHaveBeenCalledWith('/api/demo-run-profiles', { method: 'GET' });
  });

  test('fetches uploaded source media metadata without object keys', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        videoId: 'video-1',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        durationSeconds: 42,
        status: 'UPLOADED',
        createdAt: '2026-06-26T10:00:00Z'
      })
    );

    const upload = await getMediaUpload('video-1');

    expect(upload.filename).toBe('sample.mp4');
    expect('sourceObjectKey' in upload).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('/api/media/uploads/video-1', { method: 'GET' });
    expect(sourceMediaDownloadUrl('video/1')).toBe(
      '/api/media/uploads/video%2F1/source/download'
    );
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
        mode: 'OWNER_SESSION_REQUIRED',
        ownerId: 'owner-alpha',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );

    const status = await getDemoSession();

    expect(status.mode).toBe('OWNER_SESSION_REQUIRED');
    expect(status.ownerId).toBe('owner-alpha');
    expect(status.ownershipScope).toBe('CONFIGURED_DEMO_OWNER');
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

  test('reads local account auth session status', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        enabled: true,
        configured: true,
        authenticated: false,
        ownerId: 'owner-alpha',
        username: 'owner',
        authMode: 'LOCAL_AUTH_REQUIRED'
      })
    );

    const status = await getAuthSession();

    expect(status.configured).toBe(true);
    expect(status.username).toBe('owner');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/session',
      expect.objectContaining({ method: 'GET' })
    );
  });

  test('logs in and logs out local account auth with bearer token storage', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        jsonResponse({
          token: 'jwt-token',
          tokenType: 'Bearer',
          expiresAt: '2026-06-28T13:00:00Z',
          session: {
            enabled: true,
            configured: true,
            authenticated: true,
            ownerId: 'owner-alpha',
            username: 'owner',
            authMode: 'LOCAL_AUTH_ACTIVE'
          }
        })
      )
      .mockResolvedValueOnce(
        jsonResponse({
          enabled: true,
          configured: true,
          authenticated: false,
          ownerId: 'owner-alpha',
          username: 'owner',
          authMode: 'LOCAL_AUTH_REQUIRED'
        })
      );

    const login = await loginAuthSession(' owner ', ' owner-password ');
    const logout = await logoutAuthSession();

    expect(login.token).toBe('jwt-token');
    expect(login.session.authenticated).toBe(true);
    expect(logout.authenticated).toBe(false);
    expect(readAuthToken(window.localStorage)).toBe('');
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/auth/login',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'owner', password: 'owner-password' })
      })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/auth/logout',
      expect.objectContaining({
        method: 'POST',
        headers: {
          Authorization: 'Bearer jwt-token'
        }
      })
    );
  });

  test('adds bearer token and demo token headers to protected requests when both are stored', async () => {
    writeAuthToken(window.localStorage, 'jwt-token');
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobs: [],
        total: 0,
        limit: 20,
        offset: 0
      })
    );

    await listJobs();

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs?limit=20&offset=0',
      expect.objectContaining({
        headers: {
          Authorization: 'Bearer jwt-token',
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      })
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

  test('estimates media upload cost as multipart form data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        overallStatus: 'READY',
        recommendedNextAction: 'Upload can proceed with the selected profile and options.',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        maxFileSizeBytes: 104857600,
        durationSeconds: 90,
        maxDurationSeconds: 300,
        valid: true,
        validationCode: 'READY',
        validationMessage: 'File is ready for upload.',
        targetLanguage: 'zh-CN',
        ttsVoice: null,
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        translationGlossaryEntryCount: 1,
        translationGlossaryHash: 'hash',
        subtitlePolishingMode: 'BALANCED',
        demoProfileId: 'tears-showcase',
        estimatedCostUsdLower: 0.001,
        estimatedCostUsd: 0.002,
        estimatedCostUsdUpper: 0.003,
        stages: [],
        budgets: [],
        cacheNotes: [],
        safetyNotes: []
      })
    );
    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });

    const result = await estimateUploadCost(
      file,
      ' zh-CN ',
      '',
      ' formal ',
      ' high_contrast ',
      'Maya => 玛雅',
      ' balanced ',
      'tears-showcase'
    );

    expect(result.overallStatus).toBe('READY');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/cost-estimate',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('targetLanguage')).toBe(' zh-CN ');
    expect(body.get('translationStyle')).toBe('FORMAL');
    expect(body.get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
    expect(body.get('translationGlossary')).toBe('Maya => 玛雅');
    expect(body.get('subtitlePolishingMode')).toBe('BALANCED');
    expect(body.get('demoProfileId')).toBe('tears-showcase');
  });

  test('estimates media upload execution plan as multipart form data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        overallStatus: 'READY',
        recommendedNextAction: 'Upload can proceed with the selected profile and options.',
        filename: 'sample.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 1234,
        maxFileSizeBytes: 104857600,
        durationSeconds: 90,
        maxDurationSeconds: 300,
        valid: true,
        validationCode: 'READY',
        validationMessage: 'File is ready for upload.',
        targetLanguage: 'zh-CN',
        ttsVoice: null,
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        translationGlossaryEntryCount: 1,
        translationGlossaryHash: 'hash',
        subtitlePolishingMode: 'BALANCED',
        demoProfileId: 'tears-showcase',
        estimatedCostUsdLower: 0.001,
        estimatedCostUsd: 0.002,
        estimatedCostUsdUpper: 0.003,
        estimatedDurationSecondsLower: 45,
        estimatedDurationSecondsUpper: 120,
        stages: [
          {
            id: 'translation',
            label: 'Translation',
            status: 'ESTIMATED',
            executionType: 'PAID',
            provider: 'openai',
            model: 'gpt-4.1-mini',
            runnable: true,
            estimatedCostUsd: 0.001,
            estimatedDurationSecondsLower: 3,
            estimatedDurationSecondsUpper: 9,
            detail: 'Includes style prompt.'
          }
        ],
        gates: [
          {
            id: 'uploadValidation',
            label: 'Upload validation',
            status: 'READY',
            blocking: false,
            detail: 'File is ready for upload.',
            nextAction: 'No validation action required.'
          }
        ],
        commands: [
          {
            id: 'upload',
            label: 'Run upload demo',
            command: 'LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/docker-e2e-tears-of-steel-full.sh',
            description: 'Run the selected demo upload.'
          }
        ],
        sourceReuse: {
          sourceContentSha256: '039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81',
          candidateCount: 1,
          recommendedAction: 'REVIEW_EXISTING_COMPLETED_RUN',
          recommendedExistingJobId: 'job-existing',
          candidates: [
            {
              videoId: 'video-existing',
              jobId: 'job-existing',
              originalFilename: 'sample.mp4',
              durationSeconds: 90,
              jobStatus: 'COMPLETED',
              demoProfileId: 'tears-showcase',
              translationStyle: 'FORMAL',
              subtitleStylePreset: 'HIGH_CONTRAST',
              subtitlePolishingMode: 'BALANCED',
              createdAt: '2026-06-28T12:00:00Z',
              jobDetailHref: '/api/jobs/job-existing',
              shareSheetHref: '/api/jobs/job-existing/demo-share-sheet',
              evidenceHref: '/api/jobs/job-existing/evidence/markdown/download',
              demoRunPackageHref: '/api/jobs/job-existing/demo-run-package/download',
              acceptanceGateHref: '/api/jobs/job-existing/demo-acceptance-gate'
            }
          ]
        },
        sourceReuseDecision: {
          status: 'REUSE_COMPLETED_RUN',
          headline: 'Existing completed run found for this source.',
          summary: 'Review the completed job evidence before uploading this same source again.',
          recommendedAction: 'REVIEW_EXISTING_COMPLETED_RUN',
          recommendedExistingJobId: 'job-existing',
          candidateCount: 1,
          actions: [
            {
              id: 'openJob',
              label: 'Open existing job',
              kind: 'LINK',
              enabled: true,
              detail: 'Inspect the completed same-source job.',
              href: '/api/jobs/job-existing'
            }
          ],
          links: [
            {
              kind: 'DEMO_RUN_PACKAGE',
              label: 'Demo run package',
              href: '/api/jobs/job-existing/demo-run-package/download'
            }
          ],
          safetyNotes: ['Source reuse decision is read-only and does not store media or call providers.'],
          sourceReuse: {
            sourceContentSha256: '039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81',
            candidateCount: 1,
            recommendedAction: 'REVIEW_EXISTING_COMPLETED_RUN',
            recommendedExistingJobId: 'job-existing',
            candidates: []
          }
        },
        cacheNotes: [],
        safetyNotes: []
      })
    );
    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });

    const result = await estimateUploadExecutionPlan(
      file,
      'zh-CN',
      '',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      'balanced',
      'tears-showcase'
    );

    expect(result.overallStatus).toBe('READY');
    expect(result.stages[0]?.executionType).toBe('PAID');
    expect(result.sourceReuse.recommendedExistingJobId).toBe('job-existing');
    expect(result.sourceReuseDecision.status).toBe('REUSE_COMPLETED_RUN');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/execution-plan',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('targetLanguage')).toBe('zh-CN');
    expect(body.get('translationStyle')).toBe('FORMAL');
    expect(body.get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
    expect(body.get('translationGlossary')).toBe('Maya => 玛雅');
    expect(body.get('subtitlePolishingMode')).toBe('BALANCED');
    expect(body.get('demoProfileId')).toBe('tears-showcase');
  });

  test('downloads upload execution plan markdown with matching multipart fields', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# Upload Execution Plan\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );
    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });

    const result = await downloadUploadExecutionPlanMarkdown(
      file,
      'zh-CN',
      '',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      'balanced',
      'tears-showcase'
    );

    expect(await result.text()).toBe('# Upload Execution Plan\n');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/execution-plan/markdown/download',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('targetLanguage')).toBe('zh-CN');
    expect(body.has('ttsVoice')).toBe(false);
    expect(body.get('translationStyle')).toBe('FORMAL');
    expect(body.get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
    expect(body.get('translationGlossary')).toBe('Maya => 玛雅');
    expect(body.get('subtitlePolishingMode')).toBe('BALANCED');
    expect(body.get('demoProfileId')).toBe('tears-showcase');
  });

  test('downloads upload decision package markdown with matching multipart fields', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# Upload Decision Package\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );
    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });

    const result = await downloadUploadDecisionPackageMarkdown(
      file,
      'zh-CN',
      '',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      'balanced',
      'tears-showcase'
    );

    expect(await result.text()).toBe('# Upload Decision Package\n');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/decision-package/markdown/download',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('targetLanguage')).toBe('zh-CN');
    expect(body.has('ttsVoice')).toBe(false);
    expect(body.get('translationStyle')).toBe('FORMAL');
    expect(body.get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
    expect(body.get('translationGlossary')).toBe('Maya => 玛雅');
    expect(body.get('subtitlePolishingMode')).toBe('BALANCED');
    expect(body.get('demoProfileId')).toBe('tears-showcase');
  });

  test('downloads upload decision package zip with matching multipart fields', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );
    const file = new File(['demo'], 'sample.mp4', { type: 'video/mp4' });

    const result = await downloadUploadDecisionPackageZip(
      file,
      'zh-CN',
      '',
      'formal',
      'high_contrast',
      'Maya => 玛雅',
      'balanced',
      'tears-showcase'
    );

    expect(await result.text()).toBe('zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/media/uploads/decision-package/download',
      expect.objectContaining({
        method: 'POST',
        body: expect.any(FormData)
      })
    );
    const body = fetchMock.mock.calls[0]?.[1]?.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('targetLanguage')).toBe('zh-CN');
    expect(body.has('ttsVoice')).toBe(false);
    expect(body.get('translationStyle')).toBe('FORMAL');
    expect(body.get('subtitleStylePreset')).toBe('HIGH_CONTRAST');
    expect(body.get('translationGlossary')).toBe('Maya => 玛雅');
    expect(body.get('subtitlePolishingMode')).toBe('BALANCED');
    expect(body.get('demoProfileId')).toBe('tears-showcase');
  });

  test('fetches owner quota preflight before upload', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        ownerId: 'demo-owner',
        enabled: true,
        allowed: false,
        activeJobs: 2,
        queuedJobs: 1,
        dailyEstimatedCostUsd: 0.25,
        dailyBudgetDate: '2026-06-28',
        limits: [
          { name: 'activeJobs', enabled: true, limit: 2, current: 2 },
          { name: 'dailyCostUsd', enabled: true, limit: 0.25, current: 0.25 }
        ],
        blockingReasons: [
          'Owner active job limit reached: 2 / 2',
          'Owner daily budget limit reached: $0.25000000 / $0.25000000'
        ]
      })
    );

    const result = await getOwnerQuotaPreflight();

    expect(result.allowed).toBe(false);
    expect(result.limits).toHaveLength(2);
    expect(fetchMock).toHaveBeenCalledWith('/api/media/uploads/preflight', { method: 'GET' });
  });

  test('fetches demo upload readiness for selected profile', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        overallStatus: 'ATTENTION',
        ownerId: 'demo-owner',
        demoProfileId: 'tears-showcase',
        generatedAt: '2026-06-28T08:00:00Z',
        checks: [
          {
            id: 'paid-provider-check',
            label: 'Paid provider check',
            status: 'ATTENTION',
            detail: 'OpenAI provider mode is enabled, but the live OpenAI connectivity check is skipped.',
            nextAction: 'Run the OpenAI preflight before provider-backed uploads.',
            blocking: false
          }
        ],
        requiredActions: ['Review attention checks before paid or full-video upload.'],
        evidenceRoutes: ['/api/media/uploads/readiness']
      })
    );

    const result = await getDemoUploadReadiness('tears-showcase');

    expect(result.overallStatus).toBe('ATTENTION');
    expect(result.demoProfileId).toBe('tears-showcase');
    expect(fetchMock).toHaveBeenCalledWith('/api/media/uploads/readiness?demoProfileId=tears-showcase', {
      method: 'GET'
    });
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

  test('builds delivery manifest markdown download URL with encoded job id', () => {
    expect(deliveryManifestMarkdownDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/delivery-manifest/markdown/download'
    );
  });

  test('builds demo share sheet markdown download URL with encoded job id', () => {
    expect(demoShareSheetMarkdownDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/demo-share-sheet/markdown/download'
    );
  });

  test('builds demo run monitor markdown download URL with encoded job id', () => {
    expect(demoRunMonitorMarkdownDownloadUrl('job with/slash')).toBe(
      '/api/jobs/job%20with%2Fslash/demo-run-monitor/markdown/download'
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

  test('fetches model usage ledger with limit and demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(modelUsageLedgerFixture())
    );

    const ledger = await getModelUsageLedger(20);

    expect(ledger.summary.ledgerStatus).toBe('READY');
    expect(ledger.operations[0]?.operation).toBe('TRANSLATION');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/model-usage-ledger?limit=20', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads model usage ledger markdown with limit and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame Model Usage Ledger', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadModelUsageLedgerMarkdown(10);

    expect(await result.text()).toContain('Model Usage Ledger');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/model-usage-ledger/markdown/download?limit=10', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches OpenAI smoke proof with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(openAiSmokeProofFixture())
    );

    const proof = await getOpenAiSmokeProof('job with spaces/slash');

    expect(proof.overallStatus).toBe('READY');
    expect(proof.requiredChecks[0]?.name).toBe('OpenAI transcription call');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20with%20spaces%2Fslash/openai-smoke-proof', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads OpenAI smoke proof markdown with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame OpenAI Smoke Proof', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadOpenAiSmokeProofMarkdown('job with spaces/slash');

    expect(await result.text()).toContain('OpenAI Smoke Proof');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%20spaces%2Fslash/openai-smoke-proof/markdown/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('fetches demo reviewer workspace with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(demoReviewerWorkspaceFixture())
    );

    const workspace = await getDemoReviewerWorkspace('job with spaces/slash');

    expect(workspace.overallStatus).toBe('READY');
    expect(workspace.checks[0]?.key).toBe('JOB_COMPLETED');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20with%20spaces%2Fslash/demo-reviewer-workspace', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads demo reviewer workspace markdown with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame Demo Reviewer Workspace', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadDemoReviewerWorkspaceMarkdown('job with spaces/slash');

    expect(await result.text()).toContain('Demo Reviewer Workspace');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%20spaces%2Fslash/demo-reviewer-workspace/markdown/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('downloads demo reviewer workspace zip with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );

    const result = await downloadDemoReviewerWorkspaceZip('job with spaces/slash');

    expect(await result.text()).toContain('zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%20spaces%2Fslash/demo-reviewer-workspace/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('fetches demo handoff portal with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(demoHandoffPortalFixture())
    );

    const portal = await getDemoHandoffPortal('job with spaces/slash');

    expect(portal.overallStatus).toBe('READY');
    expect(portal.phase).toBe('HANDOFF_PORTAL_READY');
    expect(portal.checks[0]?.key).toBe('JOB_COMPLETED');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20with%20spaces%2Fslash/demo-handoff-portal', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads demo handoff portal markdown with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame Demo Handoff Portal', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadDemoHandoffPortalMarkdown('job with spaces/slash');

    expect(await result.text()).toContain('Demo Handoff Portal');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%20spaces%2Fslash/demo-handoff-portal/markdown/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('downloads demo handoff portal zip with encoded job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('portal-zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );

    const result = await downloadDemoHandoffPortalZip('job with spaces/slash');

    expect(await result.text()).toContain('portal-zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20with%20spaces%2Fslash/demo-handoff-portal/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('fetches demo session command center with selected job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(demoSessionCommandCenterFixture())
    );

    const commandCenter = await getDemoSessionCommandCenter('job with spaces');

    expect(commandCenter.phase).toBe('READY_TO_PRESENT');
    expect(commandCenter.focusRun?.jobId).toBe('job-session');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-session-command-center?jobId=job+with+spaces', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads demo session command center markdown with selected job id and demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame Demo Session Command Center', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadDemoSessionCommandCenterMarkdown('job with spaces');

    expect(await result.text()).toContain('Demo Session Command Center');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/operator/demo-session-command-center/markdown/download?jobId=job+with+spaces',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('downloads demo session evidence package zip with demo token header', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );

    const result = await downloadDemoSessionEvidencePackageZip();

    expect(await result.text()).toBe('zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-session-evidence-package/download', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads focused demo session evidence package zip with encoded job id', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );

    const result = await downloadDemoSessionEvidencePackageZip(' job with spaces/slash ');

    expect(await result.text()).toBe('zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/operator/demo-session-evidence-package/download?jobId=job+with+spaces%2Fslash',
      {
        method: 'GET'
      }
    );
  });

  test('fetches private demo operations with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(privateDemoOperationsFixture())
    );

    const operations = await getPrivateDemoOperations();

    expect(operations.overallStatus).toBe('READY');
    expect(operations.sections[0]?.title).toBe('Access gate');
    expect(operations.commands[0]?.command).toBe('scripts/demo/private-demo-preflight.sh');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/private-demo/operations', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches private demo launch rehearsal with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(privateDemoLaunchRehearsalFixture())
    );

    const rehearsal = await getPrivateDemoLaunchRehearsal();

    expect(rehearsal.overallStatus).toBe('ATTENTION');
    expect(rehearsal.recommendedNextStepId).toBe('openai-preflight');
    expect(rehearsal.steps[0]?.id).toBe('deploy-preflight');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/private-demo/launch-rehearsal', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches demo sample media catalog with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-29T08:00:00Z',
        overallStatus: 'READY',
        uploadDurationLimitSeconds: 300,
        recommendedSampleId: 'tears-of-steel-casting',
        items: [
          {
            id: 'tears-of-steel-casting',
            title: 'Tears of Steel casting clip',
            source: 'Blender Studio',
            sourceUrl: 'https://studio.blender.org/films/tears-of-steel/',
            attribution: 'Credit Blender Studio.',
            licenseGuidance: 'Check the Blender Studio page.',
            recommendedUse: 'Full local demo.',
            durationGuidance: 'Under 300 seconds.',
            command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
            tags: ['recommended']
          }
        ],
        configuredPaths: [
          {
            envVar: 'LINGUAFRAME_TEARS_SAMPLE_PATH',
            status: 'CONFIGURED',
            filename: 'tos_casting-720p.mp4',
            extension: 'mp4',
            sizeBytes: 1024,
            message: 'Configured sample exists.',
            fullPathExposed: false
          }
        ],
        commands: [
          {
            label: 'Run full Tears sample',
            command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
            description: 'Run the full demo.'
          }
        ],
        notesMarkdown: '# Catalog',
        documentationLinks: []
      })
    );

    const catalog = await getDemoSampleMediaCatalog();

    expect(catalog.recommendedSampleId).toBe('tears-of-steel-casting');
    expect(catalog.configuredPaths[0]?.fullPathExposed).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-sample-media-catalog', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches demo run launcher with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-29T08:05:00Z',
        overallStatus: 'READY',
        recommendedSampleId: 'tears-of-steel-casting',
        recommendedProfileId: 'tears-showcase',
        recommendedNextCommand: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
        gates: [],
        commands: [],
        expectedEvidence: [],
        notesMarkdown: '# Launcher'
      })
    );

    const launcher = await getDemoRunLauncher();

    expect(launcher.recommendedProfileId).toBe('tears-showcase');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-run-launcher', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches demo presentation cockpit with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-29T08:10:00Z',
        overallStatus: 'READY',
        phase: 'READY_TO_PRESENT',
        recommendedNextAction: 'Open the presenter pack.',
        selectedRun: null,
        activeRun: null,
        recommendedRun: null,
        checks: [],
        links: [],
        safetyNotes: []
      })
    );

    const cockpit = await getDemoPresentationCockpit();

    expect(cockpit.phase).toBe('READY_TO_PRESENT');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-presentation-cockpit', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches demo presentation cockpit for selected job id', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-29T08:10:00Z',
        overallStatus: 'ATTENTION',
        phase: 'RUN_IN_PROGRESS',
        recommendedNextAction: 'Wait for completion.',
        selectedRun: {
          jobId: 'job with spaces',
          videoId: 'video-1',
          profileId: 'tears-showcase',
          status: 'PROCESSING',
          readiness: 'ATTENTION',
          acceptanceStatus: 'ATTENTION',
          attentionLevel: 'INFO',
          currentStage: 'TRANSLATION',
          elapsedMs: 1200,
          nextAction: 'Monitor the active run.'
        },
        activeRun: null,
        recommendedRun: null,
        checks: [],
        links: [],
        safetyNotes: []
      })
    );

    const cockpit = await getDemoPresentationCockpit('job with spaces');

    expect(cockpit.selectedRun?.jobId).toBe('job with spaces');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/demo-presentation-cockpit?jobId=job+with+spaces', {
      method: 'GET'
    });
  });

  test('fetches private demo evidence gallery with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-28T08:00:00Z',
        overallStatus: 'READY',
        completedJobCount: 1,
        handoffReadyCount: 1,
        recommendedJobId: 'job-gallery',
        jobs: [],
        galleryDownloads: [],
        galleryNotesMarkdown: '# Gallery'
      })
    );

    const gallery = await getPrivateDemoEvidenceGallery(10);

    expect(gallery.recommendedJobId).toBe('job-gallery');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/private-demo/evidence-gallery?limit=10', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('fetches private demo run archive with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        generatedAt: '2026-06-28T08:00:00Z',
        overallStatus: 'READY',
        recommendedJobId: 'job-gallery-best',
        recommendedVideoId: 'video-gallery',
        recommendedProfileId: 'tears-showcase',
        recommendedReadiness: 'READY',
        operationsOverallStatus: 'READY',
        launchOverallStatus: 'READY',
        launchRecommendedNextStep: 'operations-report-export',
        galleryCompletedJobCount: 2,
        galleryHandoffReadyCount: 1,
        candidates: [],
        archiveLinks: [],
        archiveNotesMarkdown: '# Archive'
      })
    );

    const archive = await getPrivateDemoRunArchive();

    expect(archive.recommendedJobId).toBe('job-gallery-best');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/private-demo/run-archive', {
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

  test('fetches OpenAI readiness evidence with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(openAiReadinessEvidenceFixture())
    );

    const evidence = await getOpenAiReadinessEvidence();

    expect(evidence.overallStatus).toBe('READY');
    expect(evidence.liveCheck.status).toBe('UP');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/openai-readiness-evidence', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('downloads OpenAI readiness evidence markdown with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# LinguaFrame OpenAI Readiness Evidence', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown'
        }
      })
    );

    const result = await downloadOpenAiReadinessEvidenceMarkdown();

    expect(await result.text()).toContain('OpenAI Readiness Evidence');
    expect(fetchMock).toHaveBeenCalledWith('/api/operator/openai-readiness-evidence/markdown/download', {
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
      reviewedSegmentCount: 1,
      acceptedSegmentCount: 0,
      editedDecisionCount: 1,
      followupSegmentCount: 0,
      annotationCount: 1,
      reviewerNoteCount: 1,
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
          updatedAt: null,
          decision: 'UNREVIEWED',
          issueCategories: [],
          reviewerNote: null,
          noteLength: 0
        },
        {
          index: 1,
          startMs: 1000,
          endMs: 2000,
          sourceText: 'Welcome.',
          generatedText: '欢迎。',
          draftText: '欢迎你。',
          edited: true,
          updatedAt: '2026-06-28T10:00:00Z',
          decision: 'EDITED',
          issueCategories: ['TERM'],
          reviewerNote: 'Use consistent term.',
          noteLength: 20
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
      segments: [
        {
          index: 1,
          text: '欢迎你。',
          decision: 'EDITED',
          issueCategories: ['TERM'],
          reviewerNote: 'Use consistent term.'
        }
      ]
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
        body: JSON.stringify({
          segments: [
            {
              index: 1,
              text: '欢迎你。',
              decision: 'EDITED',
              issueCategories: ['TERM'],
              reviewerNote: 'Use consistent term.'
            }
          ]
        })
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

  test('fetches reviewed subtitle workflow cockpit with encoded job id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job workflow/slash',
        videoId: 'video-workflow',
        targetLanguage: 'zh-CN',
        generatedAt: '2026-06-29T14:00:00Z',
        overallStatus: 'ATTENTION',
        phase: 'PUBLISH_READY',
        recommendedNextAction: 'Publish reviewed subtitles.',
        segmentCount: 2,
        missingTargetCount: 0,
        timingMismatchCount: 0,
        qualityScore: 91,
        qualityVerdict: 'GOOD',
        qualityIssueCount: 0,
        qualitySuggestedFixCount: 0,
        editedSegmentCount: 1,
        reviewedSegmentCount: 1,
        followupSegmentCount: 0,
        annotationCount: 1,
        reviewerNoteCount: 1,
        draftLastUpdatedAt: '2026-06-29T13:50:00Z',
        generatedSubtitleArtifactCount: 3,
        reviewedSubtitleArtifactCount: 0,
        reviewedBurnedVideoAvailable: false,
        handoffReady: false,
        checks: [],
        links: [],
        safetyNotes: []
      })
    );

    const workflow = await getReviewedSubtitleWorkflow('job workflow/slash');

    expect(workflow.phase).toBe('PUBLISH_READY');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20workflow%2Fslash/reviewed-subtitle-workflow',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
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
        releaseNotesLength: 13,
        reviewDecisionCounts: [{ category: 'EDITED', count: 1 }],
        issueCategoryCounts: [{ category: 'TERM', count: 1 }],
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
      includeBurnedVideo: true,
      releaseNotes: 'Ready to ship'
    });

    expect(result.artifacts[0].type).toBe('REVIEWED_SUBTITLE_SRT');
    expect(result.releaseNotesLength).toBe(13);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20reviewed%2Fslash/subtitle-draft/publish',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        },
        body: JSON.stringify({ language: 'zh-CN', includeBurnedVideo: true, releaseNotes: 'Ready to ship' })
      }
    );
  });

  test('fetches and downloads subtitle review evidence with encoded job id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        jobId: 'job evidence/slash',
        videoId: 'video-evidence',
        targetLanguage: 'zh-CN',
        generatedAt: '2026-06-29T14:00:00Z',
        status: 'READY',
        summary: 'All subtitle segments are reviewed.',
        segmentCount: 2,
        reviewedSegmentCount: 2,
        acceptedSegmentCount: 1,
        editedDecisionCount: 1,
        followupSegmentCount: 0,
        annotationCount: 1,
        reviewerNoteCount: 1,
        reviewedSubtitleArtifactCount: 3,
        reviewedBurnedVideoAvailable: false,
        releaseNotesLength: 0,
        decisionCounts: [{ category: 'EDITED', count: 1 }],
        issueCategoryCounts: [{ category: 'TERM', count: 1 }],
        checks: [],
        links: [],
        packageEntries: ['manifest.json'],
        safetyNotes: ['metadata only']
      }))
      .mockResolvedValueOnce(new Response('# Subtitle Review Evidence', { status: 200 }))
      .mockResolvedValueOnce(new Response('zip', { status: 200, headers: { 'Content-Type': 'application/zip' } }));

    const evidence = await getSubtitleReviewEvidence('job evidence/slash');
    const markdown = await downloadSubtitleReviewEvidenceMarkdown('job evidence/slash');
    const zip = await downloadSubtitleReviewEvidenceZip('job evidence/slash');

    expect(evidence.status).toBe('READY');
    await expect(markdown.text()).resolves.toContain('Subtitle Review Evidence');
    expect(zip.type).toBe('application/zip');
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/jobs/job%20evidence%2Fslash/subtitle-review-evidence',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/jobs/job%20evidence%2Fslash/subtitle-review-evidence/markdown/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/jobs/job%20evidence%2Fslash/subtitle-review-evidence/download',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
  });

  test('gets delivery manifest with demo access token header when stored', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job manifest/slash',
        videoId: 'video-manifest',
        targetLanguage: 'zh-CN',
        status: 'COMPLETED',
        generatedAt: '2026-06-28T11:00:00Z',
        handoffReady: true,
        reviewedSubtitleArtifactCount: 3,
        reviewedBurnedVideoAvailable: false,
        generatedArtifactCount: 1,
        reviewedArtifacts: [
          {
            artifactId: 'reviewed-srt',
            type: 'REVIEWED_SUBTITLE_SRT',
            filename: 'reviewed-subtitles.zh-CN.srt',
            contentType: 'application/x-subrip',
            sizeBytes: 128,
            shortSha256: '0123456789ab',
            cacheState: 'Generated',
            role: 'REVIEWED_HANDOFF',
            downloadUrl: '/api/jobs/job manifest/slash/artifacts/reviewed-srt/download'
          }
        ],
        auditArtifacts: [],
        links: [
          {
            label: 'Result bundle',
            kind: 'RESULT_BUNDLE',
            url: '/api/jobs/job manifest/slash/artifacts/archive/download'
          }
        ]
      })
    );

    const result = await getDeliveryManifest('job manifest/slash');

    expect(result.handoffReady).toBe(true);
    expect(result.reviewedArtifacts[0].filename).toBe('reviewed-subtitles.zh-CN.srt');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20manifest%2Fslash/delivery-manifest', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('gets job comparison and builds markdown download URL with encoded ids', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        baselineJobId: 'job base/slash',
        comparisonJobId: 'job compare/slash',
        sameVideo: true,
        generatedAt: '2026-06-28T12:00:00Z',
        baseline: {
          jobId: 'job base/slash',
          videoId: 'video',
          targetLanguage: 'zh-CN',
          demoProfileId: 'quick-baseline',
          ttsVoice: null,
          translationStyle: 'NATURAL',
          subtitleStylePreset: 'STANDARD',
          translationGlossaryEntryCount: 0,
          translationGlossaryHash: '',
          subtitlePolishingMode: 'OFF',
          status: 'COMPLETED',
          qualityScore: 82,
          qualityVerdict: 'GOOD',
          modelCallCount: 1,
          failedModelCallCount: 0,
          estimatedCostUsd: 0.000063,
          artifactCacheHitCount: 0,
          generatedArtifactCount: 3,
          providerCacheHitCount: 0,
          handoffReady: true
        },
        comparison: {
          jobId: 'job compare/slash',
          videoId: 'video',
          targetLanguage: 'zh-CN',
          demoProfileId: 'tears-showcase',
          ttsVoice: null,
          translationStyle: 'FORMAL',
          subtitleStylePreset: 'HIGH_CONTRAST',
          translationGlossaryEntryCount: 3,
          translationGlossaryHash: 'abc123',
          subtitlePolishingMode: 'BALANCED',
          status: 'COMPLETED',
          qualityScore: 91,
          qualityVerdict: 'GOOD',
          modelCallCount: 2,
          failedModelCallCount: 0,
          estimatedCostUsd: 0.000141,
          artifactCacheHitCount: 1,
          generatedArtifactCount: 4,
          providerCacheHitCount: 1,
          handoffReady: false
        },
        delta: {
          qualityScore: 9,
          modelCallCount: 1,
          estimatedCostUsd: 0.000078,
          artifactCacheHitCount: 1,
          generatedArtifactCount: 1,
          providerCacheHitCount: 1
        },
        settingDiffs: [
          {
            field: 'demoProfileId',
            baselineValue: 'quick-baseline',
            comparisonValue: 'tears-showcase'
          }
        ]
      })
    );

    const result = await getJobComparison('job base/slash', 'job compare/slash');

    expect(result.delta.qualityScore).toBe(9);
    expect(result.settingDiffs[0].field).toBe('demoProfileId');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/jobs/job%20base%2Fslash/comparison/job%20compare%2Fslash',
      {
        method: 'GET',
        headers: {
          'X-LinguaFrame-Demo-Token': 'private-demo-token'
        }
      }
    );
    expect(jobComparisonMarkdownDownloadUrl('job base/slash', 'job compare/slash')).toBe(
      '/api/jobs/job%20base%2Fslash/comparison/job%20compare%2Fslash/markdown/download'
    );
  });

  test('gets demo run matrix with encoded id and limit', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        anchorJobId: 'job matrix/slash',
        videoId: 'video-matrix',
        generatedAt: '2026-06-28T12:00:00Z',
        recommendedBaselineJobId: 'job-baseline',
        bestQualityJobId: 'job-showcase',
        lowestCostJobId: 'job-baseline',
        jobs: [
          {
            jobId: 'job-showcase',
            videoId: 'video-matrix',
            filename: 'tears.mp4',
            targetLanguage: 'zh-CN',
            demoProfileId: 'tears-showcase',
            ttsVoice: null,
            translationStyle: 'FORMAL',
            subtitleStylePreset: 'HIGH_CONTRAST',
            translationGlossaryEntryCount: 3,
            translationGlossaryHash: 'abc123',
            subtitlePolishingMode: 'BALANCED',
            status: 'COMPLETED',
            createdAt: '2026-06-28T11:00:00Z',
            completedAt: '2026-06-28T11:03:00Z',
            failureStage: null,
            failureReason: null,
            retryCount: 0,
            qualityScore: 91,
            qualityVerdict: 'GOOD',
            modelCallCount: 2,
            failedModelCallCount: 0,
            estimatedCostUsd: 0.000141,
            artifactCacheHitCount: 1,
            generatedArtifactCount: 4,
            providerCacheHitCount: 1,
            handoffReady: true
          }
        ]
      })
    );

    const result = await getDemoRunMatrix('job matrix/slash', 6);

    expect(result.jobs[0].demoProfileId).toBe('tears-showcase');
    expect(result.bestQualityJobId).toBe('job-showcase');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20matrix%2Fslash/demo-run-matrix?limit=6', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('gets demo presenter pack with encoded id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        anchorJobId: 'job presenter/slash',
        videoId: 'video-presenter',
        generatedAt: '2026-06-28T12:00:00Z',
        headline: 'tears-showcase demo to zh-CN',
        readinessStatus: 'READY',
        recommendedBaselineJobId: 'job-baseline',
        bestQualityJobId: 'job presenter/slash',
        lowestCostJobId: 'job-baseline',
        runs: [
          {
            jobId: 'job presenter/slash',
            demoProfileId: 'tears-showcase',
            status: 'COMPLETED',
            completedAt: '2026-06-28T11:03:00Z',
            qualityScore: 91,
            estimatedCostUsd: 0.000141,
            modelCallCount: 2,
            providerCacheHitCount: 1,
            handoffReady: true,
            roles: ['ANCHOR', 'BEST_QUALITY']
          }
        ],
        downloads: [
          {
            kind: 'DEMO_RUN_PACKAGE',
            label: 'Demo run package',
            url: '/api/jobs/job presenter/slash/demo-run-package/download'
          }
        ],
        presenterNotesMarkdown: '# LinguaFrame Demo Presenter Pack\n'
      })
    );

    const result = await getDemoPresenterPack('job presenter/slash');

    expect(result.readinessStatus).toBe('READY');
    expect(result.runs[0].roles).toContain('BEST_QUALITY');
    expect(result.downloads[0].kind).toBe('DEMO_RUN_PACKAGE');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20presenter%2Fslash/demo-presenter-pack', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('gets demo share sheet with encoded id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-share',
        videoId: 'video-share',
        generatedAt: '2026-06-28T12:00:00Z',
        readiness: 'READY',
        headline: 'tears-showcase demo to zh-CN',
        summary: 'Completed demo share sheet.',
        outcomeBullets: ['Status: COMPLETED'],
        recommendedNextAction: 'Open the demo run package.',
        links: [
          {
            kind: 'DEMO_RUN_PACKAGE',
            label: 'Demo run package',
            url: '/api/jobs/job-share/demo-run-package/download'
          }
        ],
        markdown: '# tears-showcase demo to zh-CN\n'
      })
    );

    const result = await getDemoShareSheet('job share/slash');

    expect(result.readiness).toBe('READY');
    expect(result.links[0]?.kind).toBe('DEMO_RUN_PACKAGE');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20share%2Fslash/demo-share-sheet', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
  });

  test('gets demo run variance with encoded id and baseline body', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-variance',
        videoId: 'video-variance',
        generatedAt: '2026-06-29T12:00:00Z',
        overallStatus: 'READY',
        baselineMode: 'EXECUTION_PLAN',
        jobStatus: 'COMPLETED',
        targetLanguage: 'zh-CN',
        demoProfileId: 'tears-showcase',
        recommendedNextAction: 'Use delivery evidence.',
        metrics: [
          {
            id: 'estimatedCostUsd',
            label: 'Estimated cost USD',
            status: 'LOWER_THAN_ESTIMATE',
            estimatedValue: '0.01000000',
            actualValue: '0.00007800',
            detail: 'Compares estimated and actual cost.'
          }
        ],
        notes: [],
        safeLinks: ['/api/jobs/job-variance/demo-run-package/download'],
        safetyNotes: ['Metadata-only report.']
      })
    );

    const result = await getDemoRunVariance('job variance/slash', '{"overallStatus":"READY"}');

    expect(result.baselineMode).toBe('EXECUTION_PLAN');
    expect(result.metrics[0]?.status).toBe('LOWER_THAN_ESTIMATE');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20variance%2Fslash/demo-run-variance', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      },
      body: JSON.stringify({ preUploadJson: '{"overallStatus":"READY"}' })
    });
  });

  test('downloads demo run variance markdown with encoded id and optional baseline', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# Demo Run Variance Report\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown;charset=UTF-8'
        }
      })
    );

    const result = await downloadDemoRunVarianceMarkdown('job variance/slash', '  ');

    expect(await result.text()).toBe('# Demo Run Variance Report\n');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20variance%2Fslash/demo-run-variance/markdown/download', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      },
      body: JSON.stringify({ preUploadJson: null })
    });
  });

  test('gets demo evidence closure with encoded id and baseline body', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-closure',
        videoId: 'video-closure',
        generatedAt: '2026-06-29T12:00:00Z',
        closureStatus: 'READY',
        baselineMode: 'EXECUTION_PLAN',
        jobStatus: 'COMPLETED',
        targetLanguage: 'zh-CN',
        demoProfileId: 'tears-showcase',
        recommendedNextAction: 'Share closure package.',
        varianceReport: {
          jobId: 'job-closure',
          videoId: 'video-closure',
          generatedAt: '2026-06-29T12:00:00Z',
          overallStatus: 'READY',
          baselineMode: 'EXECUTION_PLAN',
          jobStatus: 'COMPLETED',
          targetLanguage: 'zh-CN',
          demoProfileId: 'tears-showcase',
          recommendedNextAction: 'Use delivery evidence.',
          metrics: [],
          notes: [],
          safeLinks: [],
          safetyNotes: []
        },
        sections: [
          {
            key: 'ACCEPTANCE_GATE',
            title: 'Acceptance gate',
            status: 'READY',
            summary: 'Ready.',
            facts: ['Gate status: READY'],
            links: ['/api/jobs/job-closure/demo-acceptance-gate']
          }
        ],
        safeLinks: ['/api/jobs/job-closure/demo-evidence-closure/download'],
        safetyNotes: ['Metadata-only closure.']
      })
    );

    const result = await getDemoEvidenceClosure('job closure/slash', '{"estimatedCostUsd":"0.01000000"}');

    expect(result.closureStatus).toBe('READY');
    expect(result.sections[0]?.key).toBe('ACCEPTANCE_GATE');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20closure%2Fslash/demo-evidence-closure', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      },
      body: JSON.stringify({ preUploadJson: '{"estimatedCostUsd":"0.01000000"}' })
    });
  });

  test('downloads demo evidence closure markdown with encoded id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('# Demo Evidence Closure Package\n', {
        status: 200,
        headers: {
          'Content-Type': 'text/markdown;charset=UTF-8'
        }
      })
    );

    const result = await downloadDemoEvidenceClosureMarkdown('job closure/slash');

    expect(await result.text()).toBe('# Demo Evidence Closure Package\n');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20closure%2Fslash/demo-evidence-closure/markdown/download', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      },
      body: JSON.stringify({ preUploadJson: null })
    });
  });

  test('downloads demo evidence closure zip with encoded id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('zip-bytes', {
        status: 200,
        headers: {
          'Content-Type': 'application/zip'
        }
      })
    );

    const result = await downloadDemoEvidenceClosureZip('job closure/slash', '{"overallStatus":"READY"}');

    expect(await result.text()).toBe('zip-bytes');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20closure%2Fslash/demo-evidence-closure/download', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      },
      body: JSON.stringify({ preUploadJson: '{"overallStatus":"READY"}' })
    });
  });

  test('gets demo run monitor with encoded id', async () => {
    writeDemoToken(window.localStorage, 'private-demo-token');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse({
        jobId: 'job-monitor',
        videoId: 'video-monitor',
        status: 'PROCESSING',
        dispatchStatus: 'DISPATCHED',
        generatedAt: '2026-06-29T10:00:00Z',
        elapsedMs: 540000,
        currentStage: 'TARGET_SUBTITLE_EXPORT',
        completedStageCount: 2,
        totalStageCount: 12,
        failedStageCount: 0,
        slowestStage: 'TRANSCRIPT_SUBTITLE_EXPORT',
        slowestStageDurationMs: 90000,
        attentionLevel: 'RUNNING',
        summary: 'Localization job is running.',
        recommendedNextAction: 'Keep watching this monitor.',
        stages: [
          {
            stage: 'TARGET_SUBTITLE_EXPORT',
            status: 'STARTED',
            startedAt: '2026-06-29T09:51:00Z',
            finishedAt: null,
            durationMs: null,
            runningForMs: 540000,
            attention: 'RUNNING',
            message: 'Stage is running.'
          }
        ],
        links: [
          {
            kind: 'JOB_DETAIL',
            label: 'Job detail',
            url: '/api/jobs/job-monitor'
          }
        ],
        markdown: '# LinguaFrame Demo Run Monitor\n'
      })
    );

    const result = await getDemoRunMonitor('job monitor/slash');

    expect(result.attentionLevel).toBe('RUNNING');
    expect(result.currentStage).toBe('TARGET_SUBTITLE_EXPORT');
    expect(result.stages[0]?.attention).toBe('RUNNING');
    expect(fetchMock).toHaveBeenCalledWith('/api/jobs/job%20monitor%2Fslash/demo-run-monitor', {
      method: 'GET',
      headers: {
        'X-LinguaFrame-Demo-Token': 'private-demo-token'
      }
    });
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

function modelUsageLedgerFixture() {
  return {
    generatedAt: '2026-06-28T08:00:00Z',
    limit: 20,
    ownerId: 'demo-owner',
    ownershipScope: 'CONFIGURED_DEMO_OWNER',
    summary: {
      ledgerStatus: 'READY',
      jobCount: 1,
      modelCallCount: 2,
      failedModelCallCount: 0,
      providerCacheHitCount: 1,
      generatedArtifactCount: 1,
      totalLatencyMs: 240,
      estimatedCostUsd: '0.00020000',
      averageLatencyMs: 120,
      failureRatePercent: '0.00',
      recommendedNextAction: 'Use the ledger links as cost and latency evidence for the current demo run.'
    },
    jobs: [
      {
        jobId: 'job-ledger',
        videoId: 'video-ledger',
        jobStatus: 'COMPLETED',
        targetLanguage: 'zh-CN',
        demoProfileId: 'tears-showcase',
        modelCallCount: 2,
        failedModelCallCount: 0,
        providerCacheHitCount: 1,
        generatedArtifactCount: 1,
        totalLatencyMs: 240,
        estimatedCostUsd: '0.00020000',
        latestModelCallAt: '2026-06-28T08:01:00Z',
        safeLinks: ['/api/jobs/job-ledger/demo-run-package/download']
      }
    ],
    operations: [
      {
        operation: 'TRANSLATION',
        provider: 'OPENAI',
        model: 'gpt-test',
        promptVersion: 'openai-translation-v1',
        modelCallCount: 2,
        failedModelCallCount: 0,
        totalLatencyMs: 240,
        estimatedCostUsd: '0.00020000',
        averageLatencyMs: 120
      }
    ],
    recentCalls: [
      {
        modelCallId: 'call-ledger',
        jobId: 'job-ledger',
        videoId: 'video-ledger',
        stage: 'TARGET_SUBTITLE_EXPORT',
        operation: 'TRANSLATION',
        provider: 'OPENAI',
        model: 'gpt-test',
        promptVersion: 'openai-translation-v1',
        status: 'SUCCEEDED',
        latencyMs: 120,
        inputTokens: 100,
        outputTokens: 50,
        audioSeconds: null,
        characterCount: null,
        estimatedCostUsd: '0.00010000',
        safeErrorSummary: null,
        createdAt: '2026-06-28T08:01:00Z'
      }
    ],
    safeLinks: ['/api/operator/model-usage-ledger/markdown/download'],
    safetyNotes: ['Raw media object keys, prompts, provider responses, and secrets are intentionally excluded.']
  };
}

function demoSessionCommandCenterFixture() {
  return {
    generatedAt: '2026-06-29T08:20:00Z',
    overallStatus: 'READY',
    phase: 'READY_TO_PRESENT',
    recommendedNextAction: 'Open the presenter pack.',
    primaryCommand: 'LINGUAFRAME_DEMO_JOB_ID=job-session scripts/demo/demo-session-command-center.sh',
    focusRun: {
      role: 'SELECTED',
      jobId: 'job-session',
      videoId: 'video-session',
      profileId: 'tears-showcase',
      status: 'COMPLETED',
      readiness: 'READY',
      acceptanceStatus: 'READY',
      currentStage: 'RESULT_BUNDLE',
      elapsedMs: 120000,
      nextAction: 'Use this run for the demo.'
    },
    activeRun: null,
    recommendedCompletedRun: null,
    phases: [
      {
        id: 'model-usage',
        label: 'Model usage ledger',
        status: 'READY',
        detail: 'Calls 2, failed 0, cost 0.00020000.',
        nextAction: 'Use the ledger links as cost evidence.',
        blocking: false
      }
    ],
    actions: [
      {
        id: 'session-command-center',
        label: 'Export command center',
        command: 'LINGUAFRAME_DEMO_JOB_ID=job-session scripts/demo/demo-session-command-center.sh',
        description: 'Export focused session command center JSON and Markdown.',
        primary: true
      }
    ],
    evidenceLinks: [
      {
        label: 'Command center Markdown',
        href: '/api/operator/demo-session-command-center/markdown/download',
        contentType: 'text/markdown',
        description: 'Downloadable command center notes.'
      }
    ],
    estimatedCostUsd: '0.00020000',
    modelCallCount: 2,
    failedModelCallCount: 0,
    failureRatePercent: '0.00',
    averageLatencyMs: 120,
    providerCacheHitCount: 1,
    safetyNotes: ['Demo session command center is metadata-only and read-only.']
  };
}

function openAiReadinessEvidenceFixture() {
  return {
    generatedAt: '2026-06-29T08:30:00Z',
    overallStatus: 'READY',
    phase: 'READY_FOR_OPENAI_SMOKE',
    recommendedNextAction: 'Run scripts/demo/openai-demo-preflight.sh, then scripts/demo/docker-e2e-openai-smoke.sh.',
    providers: [
      {
        stage: 'translation',
        enabled: true,
        provider: 'openai',
        model: 'gpt-4.1-mini',
        credentialsConfigured: true,
        status: 'READY',
        detail: 'OpenAI provider is configured for this stage.',
        paidProvider: true
      }
    ],
    liveCheck: {
      status: 'UP',
      latencyMs: 33,
      message: 'OpenAI model metadata endpoint is reachable'
    },
    readinessSignals: [
      {
        id: 'OPENAI_LIVE_CHECK',
        label: 'OpenAI live check',
        status: 'READY',
        detail: 'OpenAI model metadata endpoint is reachable',
        nextAction: 'Use this evidence before running the OpenAI smoke upload.',
        blocking: false
      }
    ],
    modelUsage: {
      ledgerStatus: 'READY',
      modelCallCount: 2,
      failedModelCallCount: 0,
      failureRatePercent: '0.00',
      estimatedCostUsd: '0.00020000',
      recommendedNextAction: 'Use the ledger links as cost and latency evidence.'
    },
    commands: [
      {
        label: 'OpenAI preflight',
        command: 'scripts/demo/openai-demo-preflight.sh',
        description: 'Validate local ignored OpenAI demo env.'
      }
    ],
    safeLinks: ['/api/operator/openai-readiness-evidence/markdown/download'],
    safetyNotes: ['API keys, bearer tokens, demo tokens, provider payloads, and media bytes are intentionally excluded.']
  };
}

function privateDemoOperationsFixture() {
  return {
    generatedAt: '2026-06-28T08:00:00Z',
    overallStatus: 'READY',
    readyCount: 8,
    attentionCount: 0,
    blockedCount: 0,
    sections: [
      {
        title: 'Access gate',
        status: 'READY',
        checks: [
          {
            label: 'Owner access gate',
            status: 'READY',
            detail: 'Private demo API access requires the configured owner token.',
            nextAction: 'Use the browser owner-session login or demo token header for API calls.'
          }
        ]
      }
    ],
    commands: [
      {
        label: 'Private demo preflight',
        command: 'scripts/demo/private-demo-preflight.sh',
        detail: 'Checks local env and dependency reachability.'
      }
    ],
    documentationLinks: [
      {
        label: 'Private demo deployment',
        path: 'docs/deployment/private-demo.md',
        detail: 'Reverse proxy, env, backup, and restore runbook.'
      }
    ]
  };
}

function privateDemoLaunchRehearsalFixture() {
  return {
    generatedAt: '2026-06-28T08:30:00Z',
    overallStatus: 'ATTENTION',
    readyCount: 8,
    attentionCount: 2,
    blockedCount: 0,
    recommendedNextStepId: 'openai-preflight',
    steps: [
      {
        id: 'deploy-preflight',
        title: 'Deployment preflight',
        status: 'READY',
        detail: 'The backend runtime contract includes launch rehearsal.',
        command: 'LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh',
        evidencePath: '/api/runtime/dependencies',
        nextAction: 'Run this before starting the stack.',
        blocking: false
      }
    ],
    evidenceDownloads: [
      '/api/operator/private-demo/operations',
      '/api/jobs/{jobId}/demo-presenter-pack'
    ],
    rehearsalNotesMarkdown: '# LinguaFrame Private Demo Launch Rehearsal\n'
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

function openAiSmokeProofFixture() {
  return {
    jobId: 'job-openai-smoke',
    videoId: 'video-openai-smoke',
    targetLanguage: 'zh-CN',
    overallStatus: 'READY',
    phase: 'OPENAI_SMOKE_PROVEN',
    recommendedNextAction: 'Use this proof to present the completed OpenAI smoke run.',
    completedAt: '2026-06-29T01:03:00Z',
    requiredChecks: [
      {
        name: 'OpenAI transcription call',
        status: 'READY',
        detail: 'Successful OpenAI TRANSCRIPTION call recorded.',
        nextAction: 'No action required.'
      }
    ],
    optionalChecks: [
      {
        name: 'Quality evaluation',
        status: 'READY',
        detail: 'Quality score 91 / 100, verdict GOOD.',
        nextAction: 'No action required.'
      }
    ],
    modelCalls: [
      {
        stage: 'TRANSCRIPT_SUBTITLE_EXPORT',
        operation: 'TRANSCRIPTION',
        provider: 'OPENAI',
        model: 'gpt-4o-mini-transcribe',
        promptVersion: 'openai-audio-transcriptions-v1',
        status: 'SUCCEEDED',
        latencyMs: 250,
        inputTokens: null,
        outputTokens: null,
        audioSeconds: 30,
        characterCount: null,
        estimatedCostUsd: 0.0012,
        safeErrorSummary: null
      }
    ],
    artifacts: [
      {
        artifactId: 'target-srt',
        type: 'TARGET_SUBTITLE_SRT',
        filename: 'target-subtitles.zh-CN.srt',
        contentType: 'application/x-subrip',
        sizeBytes: 128,
        contentSha256: 'hash-target-srt',
        cacheHit: false,
        createdAt: '2026-06-29T01:03:00Z'
      }
    ],
    safeLinks: [
      {
        label: 'AI audit package',
        href: '/api/jobs/job-openai-smoke/ai-audit-package/download',
        contentType: 'application/zip',
        description: 'Prompt, model-call, usage, and cost audit package.'
      }
    ],
    safetyNotes: ['Metadata only.']
  };
}

function demoReviewerWorkspaceFixture() {
  return {
    jobId: 'job-reviewer',
    videoId: 'video-reviewer',
    generatedAt: '2026-06-29T13:00:00Z',
    overallStatus: 'READY',
    phase: 'REVIEW_PACKAGE_READY',
    recommendedNextAction: 'Download the reviewer workspace ZIP and share it with the demo evidence links.',
    completedAt: '2026-06-29T12:59:30Z',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    sections: [
      {
        key: 'RUN_SUMMARY',
        title: 'Run summary',
        status: 'COMPLETED',
        facts: ['Job status: COMPLETED', 'Model calls: 4']
      }
    ],
    checks: [
      {
        key: 'JOB_COMPLETED',
        label: 'Job completed',
        status: 'READY',
        detail: 'Job reached COMPLETED.',
        nextAction: 'Keep the completed job id with reviewer evidence.',
        required: true
      }
    ],
    safeLinks: [
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        href: '/api/jobs/job-reviewer/demo-run-package/download',
        contentType: 'application/zip',
        description: 'Detailed safe job package.'
      }
    ],
    packageEntries: ['manifest.json', 'reviewer-workspace.md', 'README.md'],
    safetyNotes: ['Metadata only.']
  };
}

function demoHandoffPortalFixture() {
  return {
    jobId: 'job-portal',
    videoId: 'video-portal',
    generatedAt: '2026-06-29T13:15:00Z',
    overallStatus: 'READY',
    phase: 'HANDOFF_PORTAL_READY',
    headline: 'Demo handoff portal is ready.',
    recommendedNextAction: 'Download the handoff portal ZIP and share index.html with reviewers.',
    completedAt: '2026-06-29T13:14:30Z',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    checks: [
      {
        key: 'JOB_COMPLETED',
        label: 'Job completed',
        status: 'READY',
        detail: 'Job reached COMPLETED.',
        nextAction: 'Keep this portal with the completed job id.',
        required: true
      }
    ],
    sections: [
      {
        key: 'OFFLINE_PORTAL',
        title: 'Offline portal',
        status: 'READY',
        facts: ['Entry point: index.html', 'Static package excludes media bytes.']
      }
    ],
    safeLinks: [
      {
        kind: 'PORTAL_ZIP',
        label: 'Demo handoff portal ZIP',
        href: '/api/jobs/job-portal/demo-handoff-portal/download',
        contentType: 'application/zip',
        description: 'Static portal package.'
      }
    ],
    packageEntries: ['index.html', 'manifest.json', 'handoff-portal.md'],
    safetyNotes: ['Metadata only.']
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
