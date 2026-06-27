import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { App } from './App';
import { linguaFrameApi } from './api/linguaframeApi';
import type {
  LocalizationJob,
  LocalizationJobList,
  MediaUpload,
  OperatorDashboard,
  PromptTemplate,
  RetentionCleanupResult,
  RuntimeDependencySummary
} from './domain/jobTypes';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(readonly url: string) {
    FakeEventSource.instances.push(this);
  }

  emitJob(job: LocalizationJob) {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(job) }));
  }

  close() {
    this.closed = true;
  }
}

describe('App', () => {
  beforeEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
    vi.restoreAllMocks();
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(jobListFixture());
    vi.spyOn(linguaFrameApi, 'getOperatorDashboard').mockResolvedValue(operatorDashboardFixture());
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockResolvedValue(runtimeDependenciesFixture());
    vi.spyOn(linguaFrameApi, 'getRetentionCleanupPreview').mockResolvedValue(
      retentionCleanupResultFixture()
    );
    vi.spyOn(linguaFrameApi, 'listPromptTemplates').mockResolvedValue(promptTemplateFixtures());
  });

  test('shows demo readiness configuration in the sidebar', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockResolvedValue(
      runtimeDependenciesFixture({
        readiness: {
          ...runtimeDependenciesFixture().readiness,
          demoAccessGate: true,
          budget: {
            enabled: true,
            maxJobCostUsd: 0.000001,
            estimatedCostTrackingEnabled: true
          },
          providers: {
            ...runtimeDependenciesFixture().readiness.providers,
            translation: {
              enabled: true,
              provider: 'openai',
              model: 'gpt-4.1-mini',
              credentialsConfigured: true
            }
          }
        }
      })
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /demo readiness/i });
    expect(within(readiness).getByText('Protected')).toBeInTheDocument();
    expect(within(readiness).getByText('300 seconds')).toBeInTheDocument();
    expect(within(readiness).getByText('COMBINED')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg audio')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg burn-in')).toBeInTheDocument();
    expect(within(readiness).getAllByText('Budget guard')).toHaveLength(2);
    expect(within(readiness).getByText('Enabled / estimates Enabled')).toBeInTheDocument();
    expect(within(readiness).getByText('$0.00000100')).toBeInTheDocument();
    expect(within(readiness).getByText('translation: openai / gpt-4.1-mini / credentials set'))
      .toBeInTheDocument();
    expect(within(readiness).getByText('Job cache')).toBeInTheDocument();
  });

  test('keeps upload controls usable when demo readiness fails', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockRejectedValue(
      new Error('Readiness unavailable')
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /demo readiness/i });
    expect(within(readiness).getByText('Readiness unavailable')).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('shows operator dashboard metrics and opens a recent failed job', async () => {
    vi.spyOn(linguaFrameApi, 'getOperatorDashboard').mockResolvedValue(
      operatorDashboardFixture({
        recentFailures: [
          {
            jobId: 'failed-dashboard-job',
            videoId: 'failed-dashboard-video',
            filename: 'failed-dashboard.mp4',
            failureStage: 'DUBBING_AUDIO_GENERATION',
            failureReason: 'OpenAI TTS request failed with status 401',
            failedAt: '2026-06-27T06:00:00Z'
          }
        ]
      })
    );
    const getJob = vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'failed-dashboard-job',
        videoId: 'failed-dashboard-video',
        status: 'FAILED',
        failureStage: 'DUBBING_AUDIO_GENERATION',
        failureReason: 'OpenAI TTS request failed with status 401'
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    const dashboard = await screen.findByRole('region', { name: /operator dashboard/i });
    expect(within(dashboard).getByText('5 jobs')).toBeInTheDocument();
    expect(within(dashboard).getByText('1 failed')).toBeInTheDocument();
    expect(within(dashboard).getByText('$0.00015000')).toBeInTheDocument();
    expect(within(dashboard).getByText('1 / 3 artifacts')).toBeInTheDocument();
    expect(within(dashboard).getByRole('button', { name: /failed-dashboard\.mp4/i }))
      .toHaveTextContent('DUBBING_AUDIO_GENERATION');

    await userEvent.click(within(dashboard).getByRole('button', { name: /failed-dashboard\.mp4/i }));

    expect(await screen.findByRole('heading', { name: /job failed-dashboard-job/i })).toBeInTheDocument();
    expect(getJob).toHaveBeenCalledWith('failed-dashboard-job');
  });

  test('keeps upload controls usable when operator dashboard fails', async () => {
    vi.spyOn(linguaFrameApi, 'getOperatorDashboard').mockRejectedValue(
      new Error('Dashboard unavailable')
    );

    render(<App />);

    const dashboard = await screen.findByRole('region', { name: /operator dashboard/i });
    expect(within(dashboard).getByText('Dashboard unavailable')).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('shows retention cleanup preview in the operator sidebar', async () => {
    render(<App />);

    const cleanup = await screen.findByRole('region', { name: /retention cleanup/i });
    expect(within(cleanup).getByText('Dry run')).toBeInTheDocument();
    expect(within(cleanup).getByText('2 terminal jobs would be considered.')).toBeInTheDocument();
    expect(within(cleanup).getByText('2 jobs')).toBeInTheDocument();
    expect(within(cleanup).getByText('1 objects')).toBeInTheDocument();
    expect(within(cleanup).getByRole('button', { name: /preview cleanup/i })).toBeInTheDocument();
    expect(within(cleanup).getByRole('button', { name: /run cleanup/i })).toBeInTheDocument();
  });

  test('refreshes retention cleanup preview', async () => {
    const preview = vi
      .spyOn(linguaFrameApi, 'getRetentionCleanupPreview')
      .mockResolvedValueOnce(retentionCleanupResultFixture())
      .mockResolvedValueOnce(retentionCleanupResultFixture({ candidateJobCount: 4 }));

    render(<App />);

    const cleanup = await screen.findByRole('region', { name: /retention cleanup/i });
    await userEvent.click(within(cleanup).getByRole('button', { name: /preview cleanup/i }));

    expect(preview).toHaveBeenCalledTimes(2);
    expect(await within(cleanup).findByText('4 jobs')).toBeInTheDocument();
  });

  test('does not run retention cleanup when confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const runCleanup = vi.spyOn(linguaFrameApi, 'runRetentionCleanup').mockResolvedValue(
      retentionCleanupResultFixture({ dryRun: false })
    );

    render(<App />);

    const cleanup = await screen.findByRole('region', { name: /retention cleanup/i });
    await userEvent.click(within(cleanup).getByRole('button', { name: /run cleanup/i }));

    expect(runCleanup).not.toHaveBeenCalled();
  });

  test('runs retention cleanup after confirmation and shows delete-mode result', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const runCleanup = vi.spyOn(linguaFrameApi, 'runRetentionCleanup').mockResolvedValue(
      retentionCleanupResultFixture({
        dryRun: false,
        deletedJobCount: 2,
        deletedVideoCount: 1,
        deletedObjectCount: 5,
        skippedObjectCount: 0
      })
    );

    render(<App />);

    const cleanup = await screen.findByRole('region', { name: /retention cleanup/i });
    await userEvent.click(within(cleanup).getByRole('button', { name: /run cleanup/i }));

    expect(runCleanup).toHaveBeenCalledTimes(1);
    expect(await within(cleanup).findByText('Delete mode')).toBeInTheDocument();
    expect(
      within(cleanup).getByText('2 jobs, 1 videos, and 5 objects deleted.')
    ).toBeInTheDocument();
  });

  test('keeps upload controls usable when retention cleanup preview fails', async () => {
    vi.spyOn(linguaFrameApi, 'getRetentionCleanupPreview').mockRejectedValue(
      new Error('Cleanup unavailable')
    );

    render(<App />);

    const cleanup = await screen.findByRole('region', { name: /retention cleanup/i });
    expect(within(cleanup).getByText('Cleanup unavailable')).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('loads server job history on startup', async () => {
    const listJobs = vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'history-job',
            videoId: 'history-video',
            filename: 'history.mp4',
            status: 'COMPLETED',
            estimatedCostUsd: 0.00045
          })
        ],
        total: 1
      })
    );

    render(<App />);

    const history = await screen.findByRole('region', { name: /job history/i });
    const historyList = within(history).getByRole('list', { name: /server job history/i });
    expect(within(history).getByText('history.mp4')).toBeInTheDocument();
    expect(within(historyList).getByText('COMPLETED')).toBeInTheDocument();
    expect(within(historyList).getByRole('button', { name: /history\.mp4/i })).toHaveTextContent(
      '$0.00045000'
    );
    expect(listJobs).toHaveBeenCalledWith({ status: 'ALL', limit: 20, offset: 0 });
  });

  test('shows active prompt templates on startup', async () => {
    render(<App />);

    const templates = await screen.findByRole('region', { name: /prompt templates/i });
    expect(within(templates).getByText('openai-subtitle-translation-v1')).toBeInTheDocument();
    expect(
      within(templates).getByText('openai-translation-quality-evaluation-v1')
    ).toBeInTheDocument();
    expect(within(templates).getByText('SUBTITLE_TRANSLATION')).toBeInTheDocument();
    expect(
      within(templates).getByText('Return JSON with segments[{index,text}] preserving order and timing.')
    ).toBeInTheDocument();
  });

  test('shows non-blocking prompt template load failures', async () => {
    vi.spyOn(linguaFrameApi, 'listPromptTemplates').mockRejectedValue(
      new Error('Prompt templates unavailable')
    );

    render(<App />);

    const templates = await screen.findByRole('region', { name: /prompt templates/i });
    expect(within(templates).getByText('Prompt templates unavailable')).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: /job controls/i })).toBeInTheDocument();
  });

  test('saves and clears the private demo access token', async () => {
    render(<App />);

    const tokenInput = screen.getByLabelText(/demo access token/i);
    await userEvent.type(tokenInput, 'private-demo-token');
    await userEvent.click(screen.getByRole('button', { name: /save token/i }));

    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBe('private-demo-token');
    expect(screen.getByText(/token saved/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /clear token/i }));

    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBeNull();
    expect(tokenInput).toHaveValue('');
  });

  test('shows demo token required errors from protected APIs', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockRejectedValue(
      new Error('Demo access token is required.')
    );

    render(<App />);

    expect(await screen.findByText('Demo access token is required.')).toBeInTheDocument();
    expect(screen.getByLabelText(/demo access token/i)).toBeInTheDocument();
  });

  test('filters server job history by status', async () => {
    const listJobs = vi
      .spyOn(linguaFrameApi, 'listJobs')
      .mockResolvedValueOnce(jobListFixture())
      .mockResolvedValueOnce(
        jobListFixture({
          jobs: [
            jobSummaryFixture({
              jobId: 'failed-history-job',
              filename: 'failed-history.mp4',
              status: 'FAILED'
            })
          ],
          total: 1
        })
      );

    render(<App />);

    await userEvent.selectOptions(screen.getByLabelText(/history status/i), 'FAILED');

    await waitFor(() =>
      expect(listJobs).toHaveBeenLastCalledWith({ status: 'FAILED', limit: 20, offset: 0 })
    );
    const history = screen.getByRole('region', { name: /job history/i });
    expect(await within(history).findByText('failed-history.mp4')).toBeInTheDocument();
  });

  test('opens a server history job and loads its previews', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'history-open-job',
            videoId: 'history-open-video',
            filename: 'open-me.mp4',
            targetLanguage: 'ja'
          })
        ],
        total: 1
      })
    );
    const getJob = vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'history-open-job',
        videoId: 'history-open-video',
        targetLanguage: 'ja'
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    const history = await screen.findByRole('region', { name: /job history/i });
    await userEvent.click(within(history).getByRole('button', { name: /open-me.mp4/i }));

    expect(await screen.findByRole('heading', { name: /job history-open-job/i })).toBeInTheDocument();
    expect(getJob).toHaveBeenCalledWith('history-open-job');
    expect(linguaFrameApi.listSubtitles).toHaveBeenCalledWith('history-open-job', 'ja');
  });

  test('uploads a video, stores it as a recent job, and selects the created job', async () => {
    const listJobs = vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(jobListFixture());
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
    await userEvent.selectOptions(screen.getByLabelText(/tts voice/i), 'verse');
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    expect(screen.getByText('sample.mp4')).toBeInTheDocument();
    const selectedJob = screen.getByRole('region', { name: /selected job/i });
    expect(within(selectedJob).getByText('QUEUED')).toBeInTheDocument();
    expect(JSON.parse(window.localStorage.getItem('linguaframe.recentJobs.v1') ?? '[]')).toEqual([
      expect.objectContaining({
        jobId: 'job-1',
        filename: 'sample.mp4',
        targetLanguage: 'zh-CN',
        ttsVoice: 'verse'
      })
    ]);
    expect(linguaFrameApi.uploadMedia).toHaveBeenCalledWith(
      expect.any(File),
      'zh-CN',
      'verse'
    );
    expect(listJobs).toHaveBeenCalledTimes(2);
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
    await waitFor(() =>
      expect(within(screen.getByRole('region', { name: /selected job/i })).getByText('PROCESSING'))
        .toBeInTheDocument()
    );

    await waitFor(() =>
      expect(within(screen.getByRole('region', { name: /selected job/i })).getByText('COMPLETED'))
        .toBeInTheDocument()
    );

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
            inputSummary: 'target=zh-CN, segments=2, sourceChars=61',
            outputSummary: 'segments=2, targetChars=29',
            estimatedCostUsd: 0.00045,
            safeErrorSummary: null,
            createdAt: '2026-06-26T10:00:02Z'
          }
        ],
        qualityEvaluation: {
          evaluationId: 'quality-1',
          jobId: 'job-1',
          language: 'zh-CN',
          score: 92,
          verdict: 'GOOD',
          completeness: 95,
          readability: 92,
          timingPreservation: 94,
          naturalness: 88,
          issues: ['One subtitle line is slightly literal.'],
          suggestedFixes: ['Review tone and terminology before publishing.'],
          status: 'SUCCEEDED',
          safeErrorSummary: null,
          createdAt: '2026-06-26T10:00:03Z'
        }
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
    expect(within(usageSummary).getByText('Cache hits')).toBeInTheDocument();
    expect(within(usageSummary).getByText('1 artifacts / 1 provider')).toBeInTheDocument();
    expect(within(modelCalls).getByText('TRANSLATION')).toBeInTheDocument();
    expect(within(modelCalls).getByText('target=zh-CN, segments=2, sourceChars=61')).toBeInTheDocument();
    expect(within(modelCalls).getByText('segments=2, targetChars=29')).toBeInTheDocument();
    expect(within(modelCalls).queryByText('Hello from LinguaFrame.')).not.toBeInTheDocument();
    const qualityEvaluation = screen.getByRole('region', { name: /quality evaluation/i });
    expect(within(qualityEvaluation).getByText('92 / 100')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('GOOD')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('Completeness')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('One subtitle line is slightly literal.')).toBeInTheDocument();
    expect(
      within(qualityEvaluation).getByText('Review tone and terminology before publishing.')
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('region', { name: /selected job/i })).getByRole('link', {
        name: /download diagnostics/i
      })
    ).toHaveAttribute('href', '/api/jobs/job-1/diagnostics/download');

    const retryButton = screen.getByRole('button', { name: /retry/i });
    await userEvent.click(retryButton);

    await waitFor(() => expect(retryJob).toHaveBeenCalledWith('job-1'));
  });

  test('keeps selected failed job visible when retry is rejected by the backend', async () => {
    vi.spyOn(linguaFrameApi, 'retryJob').mockRejectedValue(
      new Error('Retry limit reached for this localization job.')
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        status: 'FAILED',
        failureStage: 'WORKER_SMOKE',
        failureReason: 'stage failed safely',
        retryCount: 1
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));
    await userEvent.click(await screen.findByRole('button', { name: /retry/i }));

    expect(await screen.findByText('Retry limit reached for this localization job.')).toBeInTheDocument();
    const selectedJob = screen.getByRole('region', { name: /selected job/i });
    expect(within(selectedJob).getByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    expect(within(selectedJob).getByText('FAILED')).toBeInTheDocument();
    expect(within(selectedJob).getByText('1')).toBeInTheDocument();
  });

  test('cancels active jobs and refreshes server history', async () => {
    const cancelJob = vi
      .spyOn(linguaFrameApi, 'cancelJob')
      .mockResolvedValue(jobFixture({ status: 'CANCELLED' }));
    const listJobs = vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(jobListFixture());
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'PROCESSING' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const cancelButton = await screen.findByRole('button', { name: /cancel/i });
    await userEvent.click(cancelButton);

    await waitFor(() => expect(cancelJob).toHaveBeenCalledWith('job-1'));
    expect(within(screen.getByRole('region', { name: /selected job/i })).getByText('CANCELLED'))
      .toBeInTheDocument();
    expect(listJobs).toHaveBeenCalledTimes(2);
  });

  test('updates selected active job from server-sent events', async () => {
    const originalEventSource = window.EventSource;
    window.EventSource = FakeEventSource as unknown as typeof EventSource;
    FakeEventSource.instances = [];
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'PROCESSING' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    try {
      render(<App />);
      await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
      await userEvent.click(screen.getByRole('button', { name: /open job/i }));

      await waitFor(() => expect(FakeEventSource.instances[0]?.url).toBe('/api/jobs/job-1/events'));
      FakeEventSource.instances[0].emitJob(jobFixture({ status: 'COMPLETED' }));

      expect(await screen.findByText('COMPLETED')).toBeInTheDocument();
    } finally {
      window.EventSource = originalEventSource;
    }
  });

  test('falls back to polling when server-sent events error', async () => {
    const originalEventSource = window.EventSource;
    window.EventSource = FakeEventSource as unknown as typeof EventSource;
    FakeEventSource.instances = [];
    const getJob = vi
      .spyOn(linguaFrameApi, 'getJob')
      .mockResolvedValueOnce(jobFixture({ status: 'PROCESSING' }))
      .mockResolvedValueOnce(jobFixture({ status: 'COMPLETED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    try {
      render(<App pollIntervalMs={10} />);
      await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
      await userEvent.click(screen.getByRole('button', { name: /open job/i }));

      await waitFor(() => expect(FakeEventSource.instances).toHaveLength(1));
      FakeEventSource.instances[0].onerror?.();

      await waitFor(() => expect(getJob).toHaveBeenCalledTimes(2));
      await waitFor(() =>
        expect(within(screen.getByRole('region', { name: /selected job/i })).getByText('COMPLETED'))
          .toBeInTheDocument()
      );
    } finally {
      window.EventSource = originalEventSource;
    }
  });

  test('does not show cancel action for terminal jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'COMPLETED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /cancel/i })).not.toBeInTheDocument();
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

  test('renders transcript, subtitles, artifact downloads, and media previews', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'artifact-job', videoId: 'artifact-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([
      {
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: 'First source line'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([
      {
        language: 'zh-CN',
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: '第一行字幕'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      {
        artifactId: 'artifact-vtt',
        jobId: 'artifact-job',
        type: 'SUBTITLE_VTT',
        filename: 'subtitles.vtt',
        contentType: 'text/vtt',
        sizeBytes: 42,
        contentSha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        cacheHit: true,
        sourceArtifactId: 'source-vtt-artifact',
        createdAt: '2026-06-26T10:00:05Z'
      },
      {
        artifactId: 'artifact-audio',
        jobId: 'artifact-job',
        type: 'DUBBING_AUDIO',
        filename: 'dubbing.mp3',
        contentType: 'audio/mpeg',
        sizeBytes: 4200,
        contentSha256: 'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
        cacheHit: false,
        sourceArtifactId: null,
        createdAt: '2026-06-26T10:00:06Z'
      },
      {
        artifactId: 'artifact-video',
        jobId: 'artifact-job',
        type: 'BURNED_VIDEO',
        filename: 'burned-video.mp4',
        contentType: 'video/mp4',
        sizeBytes: 42000,
        contentSha256: 'fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210',
        cacheHit: false,
        sourceArtifactId: null,
        createdAt: '2026-06-26T10:00:07Z'
      }
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'artifact-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const transcript = await screen.findByRole('region', { name: /transcript preview/i });
    expect(within(transcript).getByText('First source line')).toBeInTheDocument();
    expect(within(transcript).getByText('00:00.000 - 00:01.200')).toBeInTheDocument();

    const subtitles = screen.getByRole('region', { name: /subtitle preview/i });
    expect(within(subtitles).getByText('第一行字幕')).toBeInTheDocument();
    expect(within(subtitles).getByText('zh-CN')).toBeInTheDocument();

    const artifacts = screen.getByRole('region', { name: /artifacts/i });
    expect(within(artifacts).getByText('subtitles.vtt')).toBeInTheDocument();
    expect(within(artifacts).getByText('42 B')).toBeInTheDocument();
    expect(within(artifacts).getByText('0123456789ab')).toBeInTheDocument();
    expect(within(artifacts).getByText('Reused')).toBeInTheDocument();
    expect(within(artifacts).getByRole('link', { name: /download subtitles.vtt/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/artifact-vtt/download'
    );
    expect(within(artifacts).getByRole('link', { name: /download result bundle/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/archive/download'
    );

    expect(screen.getByLabelText(/dubbing audio preview/i)).toHaveAttribute(
      'src',
      '/api/jobs/artifact-job/artifacts/artifact-audio/download'
    );
    expect(screen.getByLabelText(/burned video preview/i)).toHaveAttribute(
      'src',
      '/api/jobs/artifact-job/artifacts/artifact-video/download'
    );
  });

  test('shows concise empty states before previews and artifacts exist', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ jobId: 'empty-job' }));
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'empty-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByText('No transcript segments yet.')).toBeInTheDocument();
    expect(screen.getByText('No subtitle segments yet.')).toBeInTheDocument();
    const artifacts = screen.getByRole('region', { name: /artifacts/i });
    expect(within(artifacts).getByText('No artifacts yet.')).toBeInTheDocument();
    expect(within(artifacts).getByRole('link', { name: /download result bundle/i })).toHaveAttribute(
      'href',
      '/api/jobs/empty-job/artifacts/archive/download'
    );
  });

  test('keeps manual job opening available when server history fails', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockRejectedValue(new Error('History unavailable'));
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ jobId: 'manual-after-history-error' }));
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);

    render(<App />);

    const history = await screen.findByRole('region', { name: /job history/i });
    expect(within(history).getByText('History unavailable')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/open job id/i), 'manual-after-history-error');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(
      await screen.findByRole('heading', { name: /job manual-after-history-error/i })
    ).toBeInTheDocument();
  });
});

function jobListFixture(overrides: Partial<LocalizationJobList> = {}): LocalizationJobList {
  return {
    jobs: [],
    limit: 20,
    offset: 0,
    total: 0,
    ...overrides
  };
}

function operatorDashboardFixture(overrides: Partial<OperatorDashboard> = {}): OperatorDashboard {
  return {
    statusCounts: [
      { status: 'QUEUED', count: 1 },
      { status: 'RETRYING', count: 0 },
      { status: 'PROCESSING', count: 1 },
      { status: 'COMPLETED', count: 2 },
      { status: 'FAILED', count: 1 },
      { status: 'CANCELLED', count: 0 }
    ],
    recentFailures: [],
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
    },
    ...overrides
  };
}

function runtimeDependenciesFixture(
  overrides: Partial<RuntimeDependencySummary> = {}
): RuntimeDependencySummary {
  return {
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
      budget: {
        enabled: false,
        maxJobCostUsd: 0,
        estimatedCostTrackingEnabled: true
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
    },
    ...overrides
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

function jobSummaryFixture(
  overrides: Partial<LocalizationJobList['jobs'][number]> = {}
): LocalizationJobList['jobs'][number] {
  return {
    jobId: 'history-job-1',
    videoId: 'history-video-1',
    filename: 'history.mp4',
    targetLanguage: 'zh-CN',
    ttsVoice: null,
    status: 'QUEUED',
    createdAt: '2026-06-26T10:00:00Z',
    startedAt: null,
    completedAt: null,
    failedAt: null,
    failureStage: null,
    failureReason: null,
    retryCount: 0,
    estimatedCostUsd: 0,
    ...overrides
  };
}

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
    ttsVoice: 'verse',
    createdAt: '2026-06-26T10:00:00Z',
    ...overrides
  };
}

function promptTemplateFixtures(): PromptTemplate[] {
  return [
    {
      version: 'openai-subtitle-translation-v1',
      purpose: 'SUBTITLE_TRANSLATION',
      provider: 'OPENAI',
      modelFamily: 'responses',
      systemPrompt: 'You translate subtitle segments for video localization.',
      outputContract: 'Return JSON with segments[{index,text}] preserving order and timing.',
      active: true
    },
    {
      version: 'openai-translation-quality-evaluation-v1',
      purpose: 'TRANSLATION_QUALITY_EVALUATION',
      provider: 'OPENAI',
      modelFamily: 'responses',
      systemPrompt: 'You evaluate translated subtitle quality for video localization.',
      outputContract:
        'Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes.',
      active: true
    }
  ];
}

function jobFixture(overrides: Partial<LocalizationJob> = {}): LocalizationJob {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    ttsVoice: 'verse',
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
    cacheSummary: {
      cacheHitCount: 1,
      generatedArtifactCount: 2,
      providerCacheHitCount: 1
    },
    qualityEvaluation: null,
    ...overrides
  };
}
