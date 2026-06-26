import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { App } from './App';
import { linguaFrameApi } from './api/linguaframeApi';
import type { LocalizationJob, MediaUpload } from './domain/jobTypes';

describe('App', () => {
  beforeEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
    vi.restoreAllMocks();
  });

  test('uploads a video, stores it as a recent job, and selects the created job', async () => {
    vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture());
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'QUEUED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.upload(
      screen.getByLabelText(/video file/i),
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' })
    );
    await userEvent.clear(screen.getByLabelText(/target language/i));
    await userEvent.type(screen.getByLabelText(/target language/i), 'zh-CN');
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    expect(screen.getByText('sample.mp4')).toBeInTheDocument();
    expect(screen.getByText('QUEUED')).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('linguaframe.recentJobs.v1') ?? '[]')).toEqual([
      expect.objectContaining({ jobId: 'job-1', filename: 'sample.mp4', targetLanguage: 'zh-CN' })
    ]);
  });

  test('opens a known job id manually', async () => {
    const getJob = vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'manual-job',
        videoId: 'manual-video',
        status: 'PROCESSING'
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'manual-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByRole('heading', { name: /job manual-job/i })).toBeInTheDocument();
    expect(getJob).toHaveBeenCalledWith('manual-job');
  });

  test('polls active jobs and stops when the job reaches a terminal state', async () => {
    const user = userEvent.setup();
    const getJob = vi
      .spyOn(linguaFrameApi, 'getJob')
      .mockResolvedValueOnce(jobFixture({ status: 'PROCESSING' }))
      .mockResolvedValueOnce(jobFixture({ status: 'COMPLETED' }))
      .mockResolvedValue(jobFixture({ status: 'COMPLETED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App pollIntervalMs={10} />);

    await user.type(screen.getByLabelText(/open job id/i), 'job-1');
    await user.click(screen.getByRole('button', { name: /open job/i }));
    expect(await screen.findByText('PROCESSING')).toBeInTheDocument();

    expect(await screen.findByText('COMPLETED')).toBeInTheDocument();

    await new Promise((resolve) => window.setTimeout(resolve, 30));
    expect(getJob).toHaveBeenCalledTimes(2);
  });

  test('renders timeline, usage summary, model calls, failed reason, and retry action', async () => {
    const retryJob = vi
      .spyOn(linguaFrameApi, 'retryJob')
      .mockResolvedValue(jobFixture({ status: 'RETRYING', retryCount: 1, failureReason: null }));
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        status: 'FAILED',
        failureReason: 'stage failed safely',
        modelCalls: [
          {
            modelCallId: 'call-1',
            jobId: 'job-1',
            stage: 'TARGET_SUBTITLE_EXPORT',
            operation: 'TRANSLATION',
            provider: 'OPENAI',
            model: 'gpt-test',
            promptVersion: 'openai-subtitle-translation-v1',
            status: 'SUCCEEDED',
            latencyMs: 125,
            inputTokens: 1000,
            outputTokens: 500,
            audioSeconds: null,
            characterCount: null,
            estimatedCostUsd: 0.00045,
            safeErrorSummary: null,
            createdAt: '2026-06-26T10:00:02Z'
          }
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByText('stage failed safely')).toBeInTheDocument();
    const timeline = screen.getByRole('region', { name: /timeline/i });
    expect(within(timeline).getByText('WORKER_RECEIVED')).toBeInTheDocument();
    const modelCalls = screen.getByRole('region', { name: /model calls/i });
    expect(within(modelCalls).getByRole('heading', { name: /model calls/i })).toBeInTheDocument();
    expect(screen.getByText('2 calls')).toBeInTheDocument();
    const usageSummary = screen.getByRole('region', { name: /usage summary/i });
    expect(within(usageSummary).getByText('$0.00045000')).toBeInTheDocument();
    expect(within(modelCalls).getByText('TRANSLATION')).toBeInTheDocument();

    const retryButton = screen.getByRole('button', { name: /retry/i });
    await userEvent.click(retryButton);

    await waitFor(() => expect(retryJob).toHaveBeenCalledWith('job-1'));
  });

  test('loads and opens recent jobs from local storage', async () => {
    window.localStorage.setItem(
      'linguaframe.recentJobs.v1',
      JSON.stringify([
        {
          jobId: 'recent-job',
          videoId: 'recent-video',
          targetLanguage: 'ja',
          filename: 'recent.mp4',
          createdAt: '2026-06-26T10:00:00Z'
        }
      ])
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'recent-job', videoId: 'recent-video', targetLanguage: 'ja' })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    const recentList = screen.getByRole('list', { name: /recent jobs/i });
    await userEvent.click(within(recentList).getByRole('button', { name: /recent.mp4/i }));

    expect(await screen.findByRole('heading', { name: /job recent-job/i })).toBeInTheDocument();
  });
});

function mediaUploadFixture(overrides: Partial<MediaUpload> = {}): MediaUpload {
  return {
    videoId: 'video-1',
    jobId: 'job-1',
    filename: 'sample.mp4',
    contentType: 'video/mp4',
    fileSizeBytes: 1234,
    sourceObjectKey: 'source-videos/video-1/sample.mp4',
    status: 'UPLOADED',
    jobStatus: 'QUEUED',
    targetLanguage: 'zh-CN',
    createdAt: '2026-06-26T10:00:00Z',
    ...overrides
  };
}

function jobFixture(overrides: Partial<LocalizationJob> = {}): LocalizationJob {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    status: 'QUEUED',
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
    timelineEvents: [
      {
        id: 'event-1',
        stage: 'WORKER_RECEIVED',
        status: 'STARTED',
        message: 'Worker received localization job.',
        durationMs: null,
        errorSummary: null,
        occurredAt: '2026-06-26T10:00:01Z'
      }
    ],
    usageSummary: {
      modelCallCount: 2,
      failedModelCallCount: 1,
      totalLatencyMs: 200,
      estimatedCostUsd: 0.00045,
      inputTokens: 1000,
      outputTokens: 500,
      audioSeconds: null,
      characterCount: null
    },
    modelCalls: [],
    ...overrides
  };
}
