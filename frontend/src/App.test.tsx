import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { App } from './App';
import { linguaFrameApi } from './api/linguaframeApi';
import type {
  DeliveryManifest,
  DemoRunMatrix,
  JobArtifact,
  JobComparison,
  LocalizationJob,
  LocalizationJobList,
  MediaUpload,
  MediaUploadDetail,
  MediaUploadValidation,
  DemoRunProfile,
  DemoSessionStatus,
  OperatorDashboard,
  PrivateDemoOperations,
  PromptTemplate,
  RetentionCleanupResult,
  RuntimeDependencySummary,
  RuntimeLiveCheckSummary,
  SubtitleDraftSummary,
  SubtitleReviewSummary
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

function selectedJobStatusGrid() {
  const selectedJob = screen.getByRole('region', { name: /selected job/i });
  const statusGrid = selectedJob.querySelector('.status-grid');
  if (!(statusGrid instanceof HTMLElement)) {
    throw new Error('Selected job status grid was not rendered');
  }
  return within(statusGrid);
}

describe('App', () => {
  beforeEach(() => {
    vi.useRealTimers();
    window.localStorage.clear();
    vi.restoreAllMocks();
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined)
      }
    });
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(jobListFixture());
    vi.spyOn(linguaFrameApi, 'getOperatorDashboard').mockResolvedValue(operatorDashboardFixture());
    vi.spyOn(linguaFrameApi, 'getPrivateDemoOperations').mockResolvedValue(
      privateDemoOperationsFixture()
    );
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockResolvedValue(runtimeDependenciesFixture());
    vi.spyOn(linguaFrameApi, 'getRuntimeLiveChecks').mockResolvedValue(runtimeLiveChecksFixture());
    vi.spyOn(linguaFrameApi, 'getDemoSession').mockResolvedValue(demoSessionStatusFixture());
    vi.spyOn(linguaFrameApi, 'getRetentionCleanupPreview').mockResolvedValue(
      retentionCleanupResultFixture()
    );
    vi.spyOn(linguaFrameApi, 'listPromptTemplates').mockResolvedValue(promptTemplateFixtures());
    vi.spyOn(linguaFrameApi, 'getSubtitleReview').mockResolvedValue(subtitleReviewFixture());
    vi.spyOn(linguaFrameApi, 'getSubtitleDraft').mockResolvedValue(subtitleDraftFixture());
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(deliveryManifestFixture());
    vi.spyOn(linguaFrameApi, 'getDemoRunMatrix').mockResolvedValue(demoRunMatrixFixture());
    vi.spyOn(linguaFrameApi, 'listDemoRunProfiles').mockResolvedValue(demoRunProfileFixtures());
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
            dailyBudgetGuardEnabled: true,
            maxDailyCostUsd: 0.000003,
            budgetIdentity: 'demo-owner',
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
    expect(within(readiness).getByText('0.0.1-SNAPSHOT')).toBeInTheDocument();
    expect(within(readiness).getByText('V19')).toBeInTheDocument();
    expect(within(readiness).getByText('300 seconds')).toBeInTheDocument();
    expect(within(readiness).getByText('COMBINED')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg audio')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg burn-in')).toBeInTheDocument();
    expect(within(readiness).getAllByText('Budget guard')).toHaveLength(2);
    expect(within(readiness).getByText('Enabled / estimates Enabled')).toBeInTheDocument();
    expect(within(readiness).getByText('$0.00000100')).toBeInTheDocument();
    expect(within(readiness).getByText('Daily budget')).toBeInTheDocument();
    expect(within(readiness).getByText('Enabled / $0.00000300')).toBeInTheDocument();
    expect(within(readiness).getByText('demo-owner')).toBeInTheDocument();
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

  test('shows live dependency checks in the sidebar', async () => {
    render(<App />);

    const liveChecks = await screen.findByRole('region', { name: /live checks/i });
    expect(within(liveChecks).getByText('Ready')).toBeInTheDocument();
    expect(within(liveChecks).getByText('Database')).toBeInTheDocument();
    expect(within(liveChecks).getByText('Redis')).toBeInTheDocument();
    expect(within(liveChecks).getByText('RabbitMQ')).toBeInTheDocument();
    expect(within(liveChecks).getByText('MinIO')).toBeInTheDocument();
    expect(within(liveChecks).getByText('FFmpeg')).toBeInTheDocument();
    expect(within(liveChecks).getByText('OpenAI')).toBeInTheDocument();
    expect(within(liveChecks).getAllByText('UP')).toHaveLength(5);
    expect(within(liveChecks).getByText('SKIPPED')).toBeInTheDocument();
    expect(within(liveChecks).getByText(/database probe succeeded/i)).toBeInTheDocument();
    expect(within(liveChecks).getByText(/OpenAI connectivity check is disabled/i)).toBeInTheDocument();
    expect(liveChecks).not.toHaveTextContent('sk-test-secret');
    expect(liveChecks).not.toHaveTextContent('Bearer');
  });

  test('marks live dependency checks as blocked when a probe is down', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeLiveChecks').mockResolvedValue(
      runtimeLiveChecksFixture({
        healthy: false,
        checks: {
          ...runtimeLiveChecksFixture().checks,
          redis: {
            status: 'DOWN',
            latencyMs: 12,
            message: 'Redis ping failed'
          }
        }
      })
    );

    render(<App />);

    const liveChecks = await screen.findByRole('region', { name: /live checks/i });
    expect(within(liveChecks).getByText('Blocked')).toBeInTheDocument();
    expect(within(liveChecks).getByText('Redis')).toBeInTheDocument();
    expect(within(liveChecks).getByText('DOWN')).toBeInTheDocument();
    expect(within(liveChecks).getByText(/Redis ping failed/i)).toBeInTheDocument();
  });

  test('shows enabled OpenAI live check status safely', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeLiveChecks').mockResolvedValue(
      runtimeLiveChecksFixture({
        checks: {
          ...runtimeLiveChecksFixture().checks,
          openai: {
            status: 'UP',
            latencyMs: 33,
            message: 'OpenAI model metadata endpoint is reachable'
          }
        }
      })
    );

    render(<App />);

    const liveChecks = await screen.findByRole('region', { name: /live checks/i });
    expect(within(liveChecks).getByText('OpenAI')).toBeInTheDocument();
    expect(within(liveChecks).getAllByText('UP')).toHaveLength(6);
    expect(within(liveChecks).getByText(/OpenAI model metadata endpoint is reachable/i))
      .toBeInTheDocument();
    expect(liveChecks).not.toHaveTextContent('sk-test-secret');
    expect(liveChecks).not.toHaveTextContent('Bearer');
  });

  test('keeps upload controls usable when live dependency checks fail', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeLiveChecks').mockRejectedValue(
      new Error('Probe unavailable')
    );

    render(<App />);

    const liveChecks = await screen.findByRole('region', { name: /live checks/i });
    expect(within(liveChecks).getByText('Live checks unavailable: Probe unavailable'))
      .toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('shows browser demo runbook commands and runtime guidance', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockResolvedValue(
      runtimeDependenciesFixture({
        readiness: {
          ...runtimeDependenciesFixture().readiness,
          demoAccessGate: true,
          ffmpeg: {
            ...runtimeDependenciesFixture().readiness.ffmpeg,
            burnInEnabled: false
          },
          budget: {
            enabled: true,
            maxJobCostUsd: 0.000001,
            dailyBudgetGuardEnabled: true,
            maxDailyCostUsd: 0.000003,
            budgetIdentity: 'demo-owner',
            estimatedCostTrackingEnabled: true
          },
          providers: {
            transcription: {
              enabled: true,
              provider: 'openai',
              model: 'whisper-1',
              credentialsConfigured: true
            },
            translation: {
              enabled: true,
              provider: 'openai',
              model: 'gpt-4.1-mini',
              credentialsConfigured: true
            },
            tts: {
              enabled: true,
              provider: 'demo',
              model: 'demo-tts',
              credentialsConfigured: false
            },
            evaluation: {
              enabled: false,
              provider: 'demo',
              model: '',
              credentialsConfigured: false
            }
          }
        }
      })
    );

    render(<App />);

    const runbook = await screen.findByRole('region', { name: /demo runbook/i });
    expect(within(runbook).getByText('scripts/demo/start-local-demo.sh')).toBeInTheDocument();
    expect(within(runbook).getByText('scripts/demo/docker-e2e-success.sh')).toBeInTheDocument();
    expect(within(runbook).getByText('scripts/demo/docker-e2e-cache-hit.sh')).toBeInTheDocument();
    expect(within(runbook).getByText('scripts/demo/docker-e2e-tears-of-steel-full.sh'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('http://localhost:5173')).toBeInTheDocument();
    expect(within(runbook).getByText('http://localhost:8080/actuator/health')).toBeInTheDocument();
    expect(within(runbook).getByText('Private demo token is required for API calls.'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('Uploads must be complete files up to 300 seconds and 100 MB.'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('transcription: openai / whisper-1 / credentials set'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('translation: openai / gpt-4.1-mini / credentials set'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('tts: demo / demo-tts / credentials missing'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('evaluation: disabled / demo / credentials missing'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('Budget guard is enabled at $0.00000100 per job.'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('Daily budget guard is enabled at $0.00000300 for demo-owner.'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('Subtitle burn-in is disabled.')).toBeInTheDocument();
    expect(within(runbook).getByText('Quick sample: generated by docker-e2e-success.sh when FFmpeg is available.'))
      .toBeInTheDocument();
    expect(within(runbook).getByText('Full sample: set LINGUAFRAME_TEARS_SAMPLE_PATH before running the Tears of Steel script.'))
      .toBeInTheDocument();
  });

  test('keeps static demo runbook commands visible when readiness fails', async () => {
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockRejectedValue(
      new Error('Readiness unavailable')
    );

    render(<App />);

    const runbook = await screen.findByRole('region', { name: /demo runbook/i });
    expect(within(runbook).getByText('scripts/demo/start-local-demo.sh')).toBeInTheDocument();
    expect(within(runbook).getByText('scripts/demo/docker-e2e-success.sh')).toBeInTheDocument();
    expect(within(runbook).getByText('Runtime guidance unavailable: Readiness unavailable'))
      .toBeInTheDocument();
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
    expect(within(dashboard).getByText('AUDIO_EXTRACTION')).toBeInTheDocument();
    expect(within(dashboard).getByText('max 2.4 s · avg 1.2 s')).toBeInTheDocument();
    expect(within(dashboard).getByText('2 completed / 0 failed · latest 900 ms')).toBeInTheDocument();
    expect(within(dashboard).getByRole('button', { name: /failed-dashboard\.mp4/i }))
      .toHaveTextContent('DUBBING_AUDIO_GENERATION');

    await userEvent.click(within(dashboard).getByRole('button', { name: /failed-dashboard\.mp4/i }));

    expect(await screen.findByRole('heading', { name: /job failed-dashboard-job/i })).toBeInTheDocument();
    expect(getJob).toHaveBeenCalledWith('failed-dashboard-job');
  });

  test('shows private demo operations readiness and report actions', async () => {
    render(<App />);

    const operations = await screen.findByRole('region', { name: /private demo operations/i });
    expect(within(operations).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(operations).getByText('8 ready')).toBeInTheDocument();
    expect(within(operations).getByText('Access gate')).toBeInTheDocument();
    expect(within(operations).getByText('Live dependencies')).toBeInTheDocument();
    expect(within(operations).getByText('Cost safety')).toBeInTheDocument();
    expect(within(operations).getByText('Storage and recovery')).toBeInTheDocument();
    expect(within(operations).getByText('Retention cleanup')).toBeInTheDocument();
    expect(within(operations).getByText('Demo evidence')).toBeInTheDocument();
    expect(within(operations).getByText('scripts/demo/private-demo-preflight.sh')).toBeInTheDocument();
    expect(within(operations).getByRole('button', { name: /copy operations report/i })).toBeEnabled();
    expect(within(operations).getByRole('button', { name: /download operations report/i }))
      .toBeEnabled();
  });

  test('keeps upload controls usable when private demo operations fails', async () => {
    vi.spyOn(linguaFrameApi, 'getPrivateDemoOperations').mockRejectedValue(
      new Error('Operations unavailable')
    );

    render(<App />);

    const operations = await screen.findByRole('region', { name: /private demo operations/i });
    expect(within(operations).getByText('Operations readiness unavailable')).toBeInTheDocument();
    expect(within(operations).getByText('Operations unavailable')).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
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

  test('shows open demo owner session status on startup', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoSession').mockResolvedValue(
      demoSessionStatusFixture({
        accessGateEnabled: false,
        authenticated: true,
        mode: 'OPEN'
      })
    );

    render(<App />);

    expect(await screen.findByText('Open demo')).toBeInTheDocument();
  });

  test('creates and clears a private demo owner session', async () => {
    const getDemoSession = vi.spyOn(linguaFrameApi, 'getDemoSession').mockResolvedValue(
      demoSessionStatusFixture({
        accessGateEnabled: true,
        authenticated: false,
        mode: 'OWNER_SESSION_REQUIRED'
      })
    );
    const loginDemoSession = vi.spyOn(linguaFrameApi, 'loginDemoSession').mockResolvedValue(
      demoSessionStatusFixture({
        accessGateEnabled: true,
        authenticated: true,
        mode: 'OWNER_SESSION_ACTIVE'
      })
    );
    vi.spyOn(linguaFrameApi, 'logoutDemoSession').mockResolvedValue(
      demoSessionStatusFixture({
        accessGateEnabled: true,
        authenticated: false,
        mode: 'OWNER_SESSION_REQUIRED'
      })
    );

    render(<App />);

    expect(await screen.findByText('Owner session required')).toBeInTheDocument();
    const tokenInput = screen.getByLabelText(/owner access token/i);
    await userEvent.type(tokenInput, 'private-demo-token');
    await userEvent.click(screen.getByRole('button', { name: /start session/i }));

    expect(loginDemoSession).toHaveBeenCalledWith('private-demo-token');
    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBeNull();
    await waitFor(() => {
      expect(screen.getAllByText('Owner session active').length).toBeGreaterThan(0);
    });

    await userEvent.click(screen.getByRole('button', { name: /end session/i }));

    expect(window.localStorage.getItem('linguaframe.demoToken.v1')).toBeNull();
    expect(tokenInput).toHaveValue('');
    expect(await screen.findByText('Owner session ended.')).toBeInTheDocument();
    expect(getDemoSession).toHaveBeenCalled();
  });

  test('shows private demo owner session login failures', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoSession').mockResolvedValue(
      demoSessionStatusFixture({
        accessGateEnabled: true,
        authenticated: false,
        mode: 'OWNER_SESSION_REQUIRED'
      })
    );
    vi.spyOn(linguaFrameApi, 'loginDemoSession').mockRejectedValue(new Error('Owner token rejected'));

    render(<App />);

    const tokenInput = await screen.findByLabelText(/owner access token/i);
    await userEvent.type(tokenInput, 'wrong-token');
    await userEvent.click(screen.getByRole('button', { name: /start session/i }));

    expect(await screen.findByText('Owner token rejected')).toBeInTheDocument();
  });

  test('shows demo token required errors from protected APIs', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockRejectedValue(
      new Error('Demo access token is required.')
    );

    render(<App />);

    expect(await screen.findByText('Demo access token is required.')).toBeInTheDocument();
    expect(screen.getByLabelText(/owner access token/i)).toBeInTheDocument();
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
    const validateUpload = vi
      .spyOn(linguaFrameApi, 'validateUpload')
      .mockResolvedValue(mediaUploadValidationFixture());
    vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture({
      translationStyle: 'FORMAL',
      subtitleStylePreset: 'HIGH_CONTRAST',
      translationGlossaryEntryCount: 1,
      translationGlossaryHash: 'abc123'
    }));
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
    await userEvent.selectOptions(screen.getByLabelText(/translation style/i), 'FORMAL');
    await userEvent.type(screen.getByLabelText(/translation glossary/i), 'Maya => 玛雅');
    await userEvent.selectOptions(screen.getByLabelText(/subtitle style/i), 'HIGH_CONTRAST');
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    const selectedJob = screen.getByRole('region', { name: /selected job/i });
    expect(within(selectedJob).getByText('QUEUED')).toBeInTheDocument();
    expect(screen.getAllByText('sample.mp4').length).toBeGreaterThan(0);
    expect(JSON.parse(window.localStorage.getItem('linguaframe.recentJobs.v1') ?? '[]')).toEqual([
      expect.objectContaining({
        jobId: 'job-1',
        filename: 'sample.mp4',
        targetLanguage: 'zh-CN',
        ttsVoice: 'verse',
        translationStyle: 'FORMAL',
        subtitleStylePreset: 'HIGH_CONTRAST',
        translationGlossaryEntryCount: 1,
        translationGlossaryHash: 'abc123',
        subtitlePolishingMode: 'OFF'
      })
    ]);
    expect(linguaFrameApi.uploadMedia).toHaveBeenCalledWith(
      expect.any(File),
      'zh-CN',
      'verse',
      'FORMAL',
      'HIGH_CONTRAST',
      'Maya => 玛雅',
      'OFF',
      ''
    );
    expect(validateUpload).toHaveBeenCalledBefore(linguaFrameApi.uploadMedia as never);
    expect(listJobs).toHaveBeenCalledTimes(2);
  });

  test('applies a demo run profile to upload fields', async () => {
    vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture({
      demoProfileId: 'tears-showcase',
      translationStyle: 'FORMAL',
      subtitleStylePreset: 'HIGH_CONTRAST',
      subtitlePolishingMode: 'BALANCED'
    }));
    vi.spyOn(linguaFrameApi, 'validateUpload').mockResolvedValue(mediaUploadValidationFixture());
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'QUEUED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.selectOptions(await screen.findByLabelText(/demo profile/i), 'tears-showcase');

    expect(screen.getByLabelText(/translation style/i)).toHaveValue('FORMAL');
    expect(screen.getByLabelText(/subtitle style/i)).toHaveValue('HIGH_CONTRAST');
    expect(screen.getByLabelText(/subtitle polishing/i)).toHaveValue('BALANCED');
    expect(screen.getByLabelText(/translation glossary/i)).toHaveValue(
      'Maya => 玛雅\nTears of Steel => 钢铁之泪'
    );

    await userEvent.upload(
      screen.getByLabelText(/video file/i),
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' })
    );
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    await waitFor(() =>
      expect(linguaFrameApi.uploadMedia).toHaveBeenCalledWith(
        expect.any(File),
        'zh-CN',
        '',
        'FORMAL',
        'HIGH_CONTRAST',
        expect.stringContaining('Tears of Steel'),
        'BALANCED',
        'tears-showcase'
      )
    );
  });

  test('validates selected file before upload when requested', async () => {
    vi.spyOn(linguaFrameApi, 'validateUpload').mockResolvedValue(
      mediaUploadValidationFixture({
        filename: 'sample.mp4',
        fileSizeBytes: 1048576,
        durationSeconds: 42
      })
    );

    render(<App />);

    await userEvent.upload(
      screen.getByLabelText(/video file/i),
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' })
    );
    await userEvent.click(screen.getByRole('button', { name: /validate file/i }));

    const validation = await screen.findByRole('region', { name: /upload validation/i });
    expect(within(validation).getByText('READY')).toBeInTheDocument();
    expect(within(validation).getByText('File is ready for upload.')).toBeInTheDocument();
    expect(within(validation).getByText('sample.mp4')).toBeInTheDocument();
    expect(within(validation).getByText('video/mp4')).toBeInTheDocument();
    expect(within(validation).getByText('1.00 MB / 100.00 MB')).toBeInTheDocument();
    expect(within(validation).getByText('42 seconds / 300 seconds')).toBeInTheDocument();
  });

  test('blocks upload when validation rejects the selected file', async () => {
    const validateUpload = vi.spyOn(linguaFrameApi, 'validateUpload').mockResolvedValue(
      mediaUploadValidationFixture({
        valid: false,
        code: 'DURATION_TOO_LONG',
        message: 'The uploaded video exceeds the 300 second duration limit.',
        durationSeconds: 301
      })
    );
    const uploadMedia = vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture());

    render(<App />);

    await userEvent.upload(
      screen.getByLabelText(/video file/i),
      new File(['demo'], 'long.mp4', { type: 'video/mp4' })
    );
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    const validation = await screen.findByRole('region', { name: /upload validation/i });
    expect(validateUpload).toHaveBeenCalledTimes(1);
    expect(uploadMedia).not.toHaveBeenCalled();
    expect(within(validation).getByText('DURATION_TOO_LONG')).toBeInTheDocument();
    expect(
      within(validation).getByText('The uploaded video exceeds the 300 second duration limit.')
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('keeps upload controls usable when upload validation request fails', async () => {
    vi.spyOn(linguaFrameApi, 'validateUpload').mockRejectedValue(
      new Error('Validation unavailable')
    );
    const uploadMedia = vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture());

    render(<App />);

    await userEvent.upload(
      screen.getByLabelText(/video file/i),
      new File(['demo'], 'sample.mp4', { type: 'video/mp4' })
    );
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    expect(await screen.findByText('Validation unavailable')).toBeInTheDocument();
    expect(uploadMedia).not.toHaveBeenCalled();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
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
    await waitFor(() => expect(selectedJobStatusGrid().getByText('PROCESSING')).toBeInTheDocument());

    await waitFor(() => expect(selectedJobStatusGrid().getByText('COMPLETED')).toBeInTheDocument());

    await new Promise((resolve) => window.setTimeout(resolve, 30));
    expect(getJob).toHaveBeenCalledTimes(2);
  });

  test('renders timeline, usage summary, model calls, AI audit package, quality evidence, failed reason, and retry action', async () => {
    const retryJob = vi
      .spyOn(linguaFrameApi, 'retryJob')
      .mockResolvedValue(jobFixture({ status: 'RETRYING', retryCount: 1, failureReason: null }));
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        status: 'FAILED',
        failureReason:
          'stage failed safely provider request payload raw transcript text sk-test /Users/example/job-artifacts/raw.json',
        failureTriage: {
          category: 'OPENAI_AUTH_OR_MODEL',
          summary: 'OpenAI rejected the configured credentials or model.',
          recommendedAction: 'Run the OpenAI preflight, then fix OPENAI_API_KEY or model values before retrying.',
          retryable: false,
          runbookCommand: 'scripts/demo/openai-demo-preflight.sh',
          safeDetails: ['failureStage=TARGET_SUBTITLE_EXPORT']
        },
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

    expect((await screen.findAllByText(/stage failed safely/)).length).toBeGreaterThan(0);
    const timeline = screen.getByRole('region', { name: /timeline/i });
    expect(within(timeline).getByText('WORKER_RECEIVED')).toBeInTheDocument();
    const modelCalls = screen.getByRole('region', { name: /model calls/i });
    expect(within(modelCalls).getByRole('heading', { name: /model calls/i })).toBeInTheDocument();
    expect(within(modelCalls).getByRole('link', { name: /download ai audit package/i })).toHaveAttribute(
      'href',
      '/api/jobs/job-1/ai-audit-package/download'
    );
    const usageSummary = screen.getByRole('region', { name: /usage summary/i });
    expect(within(usageSummary).getByText('2 calls')).toBeInTheDocument();
    expect(within(usageSummary).getByText('$0.00045000')).toBeInTheDocument();
    expect(within(usageSummary).getByText('Cache hits')).toBeInTheDocument();
    expect(within(usageSummary).getByText('1 artifacts / 1 provider')).toBeInTheDocument();
    const pipelineProgress = screen.getByRole('region', { name: /pipeline progress/i });
    expect(within(pipelineProgress).getAllByText('TARGET_SUBTITLE_EXPORT').length).toBeGreaterThan(0);
    expect(within(pipelineProgress).getByText('2 / 10')).toBeInTheDocument();
    expect(within(pipelineProgress).getByText('1.7 s')).toBeInTheDocument();
    expect(within(pipelineProgress).getByText('TARGET_SUBTITLE_EXPORT · 1.5 s')).toBeInTheDocument();
    expect(within(pipelineProgress).getByText('TARGET_SUBTITLE_EXPORT failed')).toBeInTheDocument();
    expect(within(modelCalls).getByText('TRANSLATION')).toBeInTheDocument();
    expect(within(modelCalls).getByText('target=zh-CN, segments=2, sourceChars=61')).toBeInTheDocument();
    expect(within(modelCalls).getByText('segments=2, targetChars=29')).toBeInTheDocument();
    expect(within(modelCalls).queryByText('Hello from LinguaFrame.')).not.toBeInTheDocument();
    const failureTriage = screen.getByRole('region', { name: /failure triage/i });
    expect(within(failureTriage).getByText('OPENAI_AUTH_OR_MODEL')).toBeInTheDocument();
    expect(within(failureTriage).getByText('OpenAI rejected the configured credentials or model.')).toBeInTheDocument();
    expect(within(failureTriage).getByText(/Run the OpenAI preflight/)).toBeInTheDocument();
    expect(within(failureTriage).getByText('scripts/demo/openai-demo-preflight.sh')).toBeInTheDocument();
    const qualityEvaluation = screen.getByRole('region', { name: /quality evaluation/i });
    expect(within(qualityEvaluation).getByText('92 / 100')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('GOOD')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('Completeness')).toBeInTheDocument();
    expect(within(qualityEvaluation).getByText('One subtitle line is slightly literal.')).toBeInTheDocument();
    expect(
      within(qualityEvaluation).getByText('Review tone and terminology before publishing.')
    ).toBeInTheDocument();
    expect(
      within(qualityEvaluation).getByRole('link', { name: /download backend quality evidence/i })
    ).toHaveAttribute(
      'href',
      '/api/jobs/job-1/quality-evaluation/evidence/markdown/download'
    );
    await userEvent.click(
      within(qualityEvaluation).getByRole('button', { name: /copy quality evidence/i })
    );
    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalled());
    const copiedEvidence = vi.mocked(navigator.clipboard.writeText).mock.calls.at(-1)?.[0] ?? '';
    expect(copiedEvidence).toContain('# LinguaFrame Quality Evaluation Evidence');
    expect(copiedEvidence).toContain('- Job: job-1');
    expect(copiedEvidence).toContain('- Score: 92 / 100');
    expect(copiedEvidence).not.toContain('raw transcript text');
    expect(copiedEvidence).not.toContain('provider request payload');
    expect(copiedEvidence).not.toContain('/Users/');
    expect(copiedEvidence).not.toContain('sk-test');
    expect(within(qualityEvaluation).getByText('Quality evidence copied.')).toBeInTheDocument();
    expect(
      within(qualityEvaluation).getByRole('button', { name: /download quality evidence/i })
    ).toBeEnabled();
    const selectedJob = screen.getByRole('region', { name: /selected job/i });
    expect(
      within(selectedJob)
        .getAllByRole('link', { name: /download diagnostics/i })
        .every((link) => link.getAttribute('href') === '/api/jobs/job-1/diagnostics/download')
    ).toBe(true);

    const retryButton = screen.getByRole('button', { name: /retry/i });
    await userEvent.click(retryButton);

    await waitFor(() => expect(retryJob).toHaveBeenCalledWith('job-1'));
  });

  test('does not render failure triage for completed jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ status: 'COMPLETED' }));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    await waitFor(() => expect(selectedJobStatusGrid().getByText('COMPLETED')).toBeInTheDocument());
    expect(screen.queryByRole('region', { name: /failure triage/i })).not.toBeInTheDocument();
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
    expect(within(selectedJob).getByText('Retries').nextElementSibling).toHaveTextContent('1');
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
    expect(selectedJobStatusGrid().getByText('CANCELLED')).toBeInTheDocument();
    expect(listJobs).toHaveBeenCalledTimes(2);
  });

  test('renders safe source media workspace for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ videoId: 'video-1' }));
    vi.spyOn(linguaFrameApi, 'getMediaUpload').mockResolvedValue(
      mediaUploadDetailFixture({
        filename: 'source-demo.mp4',
        contentType: 'video/mp4',
        fileSizeBytes: 4096,
        durationSeconds: 45
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const sourceMedia = await screen.findByRole('region', { name: /source media/i });
    expect(within(sourceMedia).getByText('source-demo.mp4')).toBeInTheDocument();
    expect(within(sourceMedia).getByText('video/mp4')).toBeInTheDocument();
    expect(within(sourceMedia).getByText('45s')).toBeInTheDocument();
    expect(within(sourceMedia).getByRole('link', { name: /download source video/i }))
      .toHaveAttribute('href', '/api/media/uploads/video-1/source/download');
    expect(sourceMedia).not.toHaveTextContent('source-videos/');
  });

  test('keeps selected job usable when source media metadata fails to load', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ videoId: 'video-1' }));
    vi.spyOn(linguaFrameApi, 'getMediaUpload').mockRejectedValue(new Error('Source metadata unavailable'));
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'job-1');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByRole('heading', { name: /job job-1/i })).toBeInTheDocument();
    const sourceMedia = screen.getByRole('region', { name: /source media/i });
    expect(within(sourceMedia).getByText('Source metadata unavailable')).toBeInTheDocument();
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

      await waitFor(() => expect(selectedJobStatusGrid().getByText('COMPLETED')).toBeInTheDocument());
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
      await waitFor(() => expect(selectedJobStatusGrid().getByText('COMPLETED')).toBeInTheDocument());
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
        artifactId: 'artifact-transcript',
        jobId: 'artifact-job',
        type: 'TRANSCRIPT_JSON',
        filename: 'transcript.json',
        contentType: 'application/json',
        sizeBytes: 84,
        contentSha256: '1111111111111111111111111111111111111111111111111111111111111111',
        cacheHit: false,
        sourceArtifactId: null,
        createdAt: '2026-06-26T10:00:04Z'
      },
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

    const review = screen.getByRole('region', { name: /subtitle review/i });
    expect(within(review).getByText('First source line')).toBeInTheDocument();
    expect(within(review).getByText('第一行字幕')).toBeInTheDocument();
    expect(within(review).getByText('Second source line')).toBeInTheDocument();
    expect(within(review).getByText('Timing mismatch')).toBeInTheDocument();
    expect(within(review).getByText('400 ms')).toBeInTheDocument();
    expect(within(review).getByText('88 / 100 · NEEDS_REVIEW')).toBeInTheDocument();
    expect(within(review).getByText('3 files')).toBeInTheDocument();

    const draftEditor = screen.getByRole('region', { name: /subtitle draft editor/i });
    expect(within(draftEditor).getByText('Saved edits')).toBeInTheDocument();
    expect(within(draftEditor).getByText('Unsaved edits')).toBeInTheDocument();
    expect(within(draftEditor).getByDisplayValue('修正后的第二行字幕')).toBeInTheDocument();
    expect(within(draftEditor).getByRole('link', { name: /download corrected srt/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/subtitle-draft/export?language=zh-CN&format=srt'
    );

    const delivery = screen.getByRole('region', { name: /result delivery/i });
    expect(within(delivery).getByText('3 generated')).toBeInTheDocument();
    expect(within(delivery).getByText('1 reused')).toBeInTheDocument();
    expect(within(delivery).getByText('5 missing')).toBeInTheDocument();
    expect(within(delivery).getByText('2 calls')).toBeInTheDocument();
    expect(within(delivery).getByText('$0.00045000')).toBeInTheDocument();
    expect(within(delivery).getByText('Transcript JSON')).toBeInTheDocument();
    expect(within(delivery).getByText('transcript.json')).toBeInTheDocument();
    expect(within(delivery).getByText('Source VTT')).toBeInTheDocument();
    expect(within(delivery).getByText('subtitles.vtt')).toBeInTheDocument();
    expect(within(delivery).getByText('0123456789ab')).toBeInTheDocument();
    expect(within(delivery).getAllByText('Reused').length).toBeGreaterThan(0);
    expect(within(delivery).getByRole('link', { name: /download result bundle/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/archive/download'
    );
    expect(within(delivery).getByRole('link', { name: /download diagnostics/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/diagnostics/download'
    );
    expect(within(delivery).getByRole('link', { name: /download transcript json/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/artifact-transcript/download'
    );

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
    expect(within(mediaDelivery).getByText('4.10 KB')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('abcdef012345')).toBeInTheDocument();
  });

  test('renders generated and reviewed burned video as separate media delivery outputs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'reviewed-media-job', videoId: 'reviewed-media-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'generated-video',
        jobId: 'reviewed-media-job',
        type: 'BURNED_VIDEO',
        filename: 'burned-video.mp4',
        contentType: 'video/mp4',
        contentSha256: 'fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210',
        cacheHit: true,
        sourceArtifactId: 'original-generated-video'
      }),
      artifactFixture({
        artifactId: 'reviewed-video',
        jobId: 'reviewed-media-job',
        type: 'REVIEWED_BURNED_VIDEO',
        filename: 'reviewed-burned-video.mp4',
        contentType: 'video/mp4',
        contentSha256: '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
        cacheHit: false,
        sourceArtifactId: null
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'reviewed-media-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const mediaDelivery = await screen.findByRole('region', { name: /media delivery/i });
    expect(within(mediaDelivery).getByText('Generated burned video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('Reviewed burned video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByLabelText(/generated burned video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/reviewed-media-job/artifacts/generated-video/download'
    );
    expect(within(mediaDelivery).getByLabelText(/reviewed burned video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/reviewed-media-job/artifacts/reviewed-video/download'
    );
    expect(within(mediaDelivery).getByRole('link', { name: /download reviewed burned video/i })).toHaveAttribute(
      'href',
      '/api/jobs/reviewed-media-job/artifacts/reviewed-video/download'
    );
    expect(within(mediaDelivery).getByText('fedcba987654')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('1234567890ab')).toBeInTheDocument();
  });

  test('renders ready demo handoff checklist and demo run package link for completed reviewed media jobs', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'handoff-ready-job',
        videoId: 'handoff-video',
        targetLanguage: 'zh-CN',
        status: 'COMPLETED'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'handoff-ready-job',
        handoffReady: true,
        reviewedSubtitleArtifactCount: 3,
        reviewedBurnedVideoAvailable: true,
        links: [
          {
            label: 'Evidence bundle',
            kind: 'EVIDENCE_BUNDLE',
            url: '/api/jobs/handoff-ready-job/evidence/bundle/download'
          }
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'reviewed-json',
        jobId: 'handoff-ready-job',
        type: 'REVIEWED_SUBTITLE_JSON',
        filename: 'reviewed-subtitles.zh-CN.json'
      }),
      artifactFixture({
        artifactId: 'reviewed-srt',
        jobId: 'handoff-ready-job',
        type: 'REVIEWED_SUBTITLE_SRT',
        filename: 'reviewed-subtitles.zh-CN.srt'
      }),
      artifactFixture({
        artifactId: 'reviewed-vtt',
        jobId: 'handoff-ready-job',
        type: 'REVIEWED_SUBTITLE_VTT',
        filename: 'reviewed-subtitles.zh-CN.vtt'
      }),
      artifactFixture({
        artifactId: 'dubbing-audio',
        jobId: 'handoff-ready-job',
        type: 'DUBBING_AUDIO',
        filename: 'dubbing-audio.mp3',
        contentType: 'audio/mpeg'
      }),
      artifactFixture({
        artifactId: 'burned-video',
        jobId: 'handoff-ready-job',
        type: 'BURNED_VIDEO',
        filename: 'burned-video.mp4',
        contentType: 'video/mp4'
      }),
      artifactFixture({
        artifactId: 'reviewed-video',
        jobId: 'handoff-ready-job',
        type: 'REVIEWED_BURNED_VIDEO',
        filename: 'reviewed-burned-video.mp4',
        contentType: 'video/mp4'
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'handoff-ready-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const checklist = await screen.findByRole('region', { name: /demo handoff checklist/i });
    expect(within(checklist).getByText('Demo handoff checklist')).toBeInTheDocument();
    expect(within(checklist).getByText('Ready for demo handoff')).toBeInTheDocument();
    expect(within(checklist).getByText('Job completed')).toBeInTheDocument();
    expect(within(checklist).getByText('Reviewed subtitles ready')).toBeInTheDocument();
    expect(within(checklist).getByText('Media outputs available')).toBeInTheDocument();
    expect(within(checklist).getByText('Evidence downloads ready')).toBeInTheDocument();
    expect(within(checklist).getByRole('button', { name: /copy checklist/i })).toBeEnabled();
    expect(within(checklist).getByRole('button', { name: /download checklist json/i })).toBeEnabled();
    expect(within(checklist).getByRole('link', { name: /download demo run package/i })).toHaveAttribute(
      'href',
      '/api/jobs/handoff-ready-job/demo-run-package/download'
    );
  });

  test('renders attention demo handoff checklist for failed jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'handoff-failed-job',
        videoId: 'handoff-failed-video',
        status: 'FAILED',
        failureStage: 'TARGET_SUBTITLE_EXPORT',
        failureReason: 'OpenAI request failed',
        failureTriage: {
          category: 'OPENAI_AUTH_OR_MODEL',
          summary: 'OpenAI rejected the configured credentials or model.',
          recommendedAction: 'Run the OpenAI preflight before retrying.',
          retryable: false,
          runbookCommand: 'scripts/demo/openai-demo-preflight.sh',
          safeDetails: ['failureStage=TARGET_SUBTITLE_EXPORT']
        }
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'handoff-failed-job',
        handoffReady: false,
        reviewedSubtitleArtifactCount: 0,
        reviewedArtifacts: [],
        auditArtifacts: [],
        links: []
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'handoff-failed-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const checklist = await screen.findByRole('region', { name: /demo handoff checklist/i });
    expect(within(checklist).getByText('Needs attention')).toBeInTheDocument();
    expect(within(checklist).getByText('Job completed')).toBeInTheDocument();
    expect(within(checklist).getByText('Reviewed subtitles ready')).toBeInTheDocument();
    expect(within(checklist).getByText('Failure triage available')).toBeInTheDocument();
    expect(within(checklist).getByText(/OPENAI_AUTH_OR_MODEL/)).toBeInTheDocument();
    expect(within(checklist).getByRole('link', { name: /download diagnostics/i })).toHaveAttribute(
      'href',
      '/api/jobs/handoff-failed-job/diagnostics/download'
    );
  });

  test('renders ready demo session report for completed reviewed media jobs', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'session-ready-job',
        videoId: 'session-video',
        targetLanguage: 'zh-CN',
        status: 'COMPLETED'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'session-ready-job',
        handoffReady: true,
        reviewedSubtitleArtifactCount: 3,
        reviewedBurnedVideoAvailable: true
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'session-reviewed-json',
        jobId: 'session-ready-job',
        type: 'REVIEWED_SUBTITLE_JSON',
        filename: 'reviewed-subtitles.zh-CN.json'
      }),
      artifactFixture({
        artifactId: 'session-reviewed-srt',
        jobId: 'session-ready-job',
        type: 'REVIEWED_SUBTITLE_SRT',
        filename: 'reviewed-subtitles.zh-CN.srt'
      }),
      artifactFixture({
        artifactId: 'session-reviewed-vtt',
        jobId: 'session-ready-job',
        type: 'REVIEWED_SUBTITLE_VTT',
        filename: 'reviewed-subtitles.zh-CN.vtt'
      }),
      artifactFixture({
        artifactId: 'session-dubbing-audio',
        jobId: 'session-ready-job',
        type: 'DUBBING_AUDIO',
        filename: 'dubbing-audio.mp3',
        contentType: 'audio/mpeg'
      }),
      artifactFixture({
        artifactId: 'session-burned-video',
        jobId: 'session-ready-job',
        type: 'BURNED_VIDEO',
        filename: 'burned-video.mp4',
        contentType: 'video/mp4'
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'session-ready-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const report = await screen.findByRole('region', { name: /demo session report/i });
    expect(within(report).getByText('Demo session report')).toBeInTheDocument();
    expect(within(report).getByText('Session ready')).toBeInTheDocument();
    expect(within(report).getByText('Input and job')).toBeInTheDocument();
    expect(within(report).getByText('Generated outputs')).toBeInTheDocument();
    expect(within(report).getByText('Handoff evidence')).toBeInTheDocument();
    expect(within(report).getByRole('button', { name: /copy report/i })).toBeEnabled();
    expect(within(report).getByRole('button', { name: /download report markdown/i })).toBeEnabled();
  });

  test('renders ready demo review guide for presentation walkthroughs', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'guide-ready-job',
        videoId: 'guide-video',
        targetLanguage: 'zh-CN',
        status: 'COMPLETED'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([
      {
        index: 0,
        startMs: 0,
        endMs: 1000,
        text: 'source preview row'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([
      {
        language: 'zh-CN',
        index: 0,
        startMs: 0,
        endMs: 1000,
        text: 'target preview row'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'guide-ready-job',
        handoffReady: true,
        reviewedSubtitleArtifactCount: 3,
        reviewedBurnedVideoAvailable: true
      })
    );
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'guide-reviewed-json',
        jobId: 'guide-ready-job',
        type: 'REVIEWED_SUBTITLE_JSON',
        filename: 'reviewed-subtitles.zh-CN.json'
      }),
      artifactFixture({
        artifactId: 'guide-reviewed-srt',
        jobId: 'guide-ready-job',
        type: 'REVIEWED_SUBTITLE_SRT',
        filename: 'reviewed-subtitles.zh-CN.srt'
      }),
      artifactFixture({
        artifactId: 'guide-reviewed-vtt',
        jobId: 'guide-ready-job',
        type: 'REVIEWED_SUBTITLE_VTT',
        filename: 'reviewed-subtitles.zh-CN.vtt'
      }),
      artifactFixture({
        artifactId: 'guide-dubbing-audio',
        jobId: 'guide-ready-job',
        type: 'DUBBING_AUDIO',
        filename: 'dubbing-audio.mp3',
        contentType: 'audio/mpeg'
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'guide-ready-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const guide = await screen.findByRole('region', { name: /demo review guide/i });
    expect(within(guide).getByText('Demo review guide')).toBeInTheDocument();
    expect(within(guide).getByText('Presentation ready')).toBeInTheDocument();
    expect(within(guide).getByText('Input')).toBeInTheDocument();
    expect(within(guide).getByText('Pipeline')).toBeInTheDocument();
    expect(within(guide).getByText('Review')).toBeInTheDocument();
    expect(within(guide).getByText('Delivery')).toBeInTheDocument();
    expect(within(guide).getByText('Evidence')).toBeInTheDocument();
    expect(within(guide).getByText('Handoff')).toBeInTheDocument();
    expect(within(guide).getByRole('link', { name: /open delivery/i })).toHaveAttribute(
      'href',
      '#delivery-handoff'
    );
    expect(within(guide).getByRole('button', { name: /copy presenter notes/i })).toBeEnabled();
  });

  test('renders attention demo session report for failed jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'session-failed-job',
        videoId: 'session-failed-video',
        status: 'FAILED',
        failureStage: 'TARGET_SUBTITLE_EXPORT',
        failureReason: 'OpenAI request failed',
        failureTriage: {
          category: 'OPENAI_AUTH_OR_MODEL',
          summary: 'OpenAI rejected the configured credentials or model.',
          recommendedAction: 'Run the OpenAI preflight before retrying.',
          retryable: false,
          runbookCommand: 'scripts/demo/openai-demo-preflight.sh',
          safeDetails: ['failureStage=TARGET_SUBTITLE_EXPORT']
        }
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'session-failed-job',
        handoffReady: false,
        reviewedSubtitleArtifactCount: 0,
        reviewedArtifacts: [],
        auditArtifacts: [],
        links: []
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'session-failed-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const report = await screen.findByRole('region', { name: /demo session report/i });
    expect(within(report).getByText('Session needs attention')).toBeInTheDocument();
    expect(within(report).getByText('Failure triage')).toBeInTheDocument();
    expect(within(report).getByText(/OPENAI_AUTH_OR_MODEL/)).toBeInTheDocument();
    expect(within(report).getByRole('link', { name: /download diagnostics/i })).toHaveAttribute(
      'href',
      '/api/jobs/session-failed-job/diagnostics/download'
    );
  });

  test('renders attention demo review guide for failed jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'guide-failed-job',
        videoId: 'guide-failed-video',
        status: 'FAILED',
        failureStage: 'TARGET_SUBTITLE_EXPORT',
        failureReason: 'OpenAI request failed',
        failureTriage: {
          category: 'OPENAI_AUTH_OR_MODEL',
          summary: 'OpenAI rejected the configured credentials or model.',
          recommendedAction: 'Run the OpenAI preflight before retrying.',
          retryable: false,
          runbookCommand: 'scripts/demo/openai-demo-preflight.sh',
          safeDetails: ['failureStage=TARGET_SUBTITLE_EXPORT']
        }
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(
      deliveryManifestFixture({
        jobId: 'guide-failed-job',
        handoffReady: false,
        reviewedSubtitleArtifactCount: 0,
        reviewedArtifacts: [],
        auditArtifacts: [],
        links: []
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'guide-failed-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const guide = await screen.findByRole('region', { name: /demo review guide/i });
    expect(within(guide).getByText('Needs attention')).toBeInTheDocument();
    expect(within(guide).getByText(/failure triage/i)).toBeInTheDocument();
    expect(within(guide).getByRole('link', { name: /open pipeline/i })).toHaveAttribute(
      'href',
      '#pipeline-progress'
    );
    expect(within(guide).getByRole('link', { name: /open evidence/i })).toHaveAttribute(
      'href',
      '#demo-evidence'
    );
  });

  test('edits, saves, resets, and clears subtitle draft rows', async () => {
    const updateDraft = vi.spyOn(linguaFrameApi, 'updateSubtitleDraft').mockResolvedValue(
      subtitleDraftFixture({
        editedSegmentCount: 2,
        segments: [
          ...subtitleDraftFixture().segments.slice(0, 1),
          {
            ...subtitleDraftFixture().segments[1],
            draftText: '人工修正后的字幕',
            edited: true
          }
        ]
      })
    );
    const clearDraft = vi.spyOn(linguaFrameApi, 'clearSubtitleDraft').mockResolvedValue(
      subtitleDraftFixture({
        editedSegmentCount: 0,
        lastUpdatedAt: null,
        segments: subtitleDraftFixture().segments.map((segment) => ({
          ...segment,
          draftText: segment.generatedText,
          edited: false,
          updatedAt: null
        }))
      })
    );
    const reviewedArtifacts = [
      artifactFixture({
        artifactId: 'reviewed-srt',
        jobId: 'draft-job',
        type: 'REVIEWED_SUBTITLE_SRT',
        filename: 'reviewed-subtitles.zh-CN.srt',
        contentType: 'application/x-subrip;charset=UTF-8'
      })
    ];
    const publishReviewed = vi.spyOn(linguaFrameApi, 'publishReviewedSubtitles').mockResolvedValue({
      jobId: 'draft-job',
      targetLanguage: 'zh-CN',
      burnedVideoRequested: true,
      burnedVideoCreated: false,
      artifacts: reviewedArtifacts
    });
    const getDeliveryManifest = vi.spyOn(linguaFrameApi, 'getDeliveryManifest')
      .mockResolvedValueOnce(deliveryManifestFixture({
        jobId: 'draft-job',
        handoffReady: false,
        reviewedSubtitleArtifactCount: 0,
        reviewedArtifacts: [],
        auditArtifacts: [],
        links: []
      }))
      .mockResolvedValueOnce(deliveryManifestFixture({
        jobId: 'draft-job',
        handoffReady: true,
        reviewedSubtitleArtifactCount: 1,
        reviewedArtifacts: [
          {
            artifactId: 'reviewed-srt',
            type: 'REVIEWED_SUBTITLE_SRT',
            filename: 'reviewed-subtitles.zh-CN.srt',
            contentType: 'application/x-subrip;charset=UTF-8',
            sizeBytes: 120,
            shortSha256: '0123456789ab',
            cacheState: 'Generated',
            role: 'REVIEWED_HANDOFF',
            downloadUrl: '/api/jobs/draft-job/artifacts/reviewed-srt/download'
          }
        ],
        auditArtifacts: [],
        links: [
          {
            label: 'Delivery manifest Markdown',
            kind: 'DELIVERY_MANIFEST_MARKDOWN',
            url: '/api/jobs/draft-job/delivery-manifest/markdown/download'
          }
        ]
      }));
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'draft-job', videoId: 'draft-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    const listArtifacts = vi.spyOn(linguaFrameApi, 'listArtifacts')
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce(reviewedArtifacts);
    vi.spyOn(linguaFrameApi, 'getSubtitleDraft').mockResolvedValue(
      subtitleDraftFixture({ jobId: 'draft-job' })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'draft-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const draftEditor = await screen.findByRole('region', { name: /subtitle draft editor/i });
    const handoff = await screen.findByRole('region', { name: /delivery handoff/i });
    expect(within(handoff).getByText('Needs reviewed subtitle publish')).toBeInTheDocument();
    const secondDraft = within(draftEditor).getByLabelText('Draft text 1');
    await userEvent.clear(secondDraft);
    await userEvent.type(secondDraft, '人工修正后的字幕');
    expect(within(draftEditor).getByText('Unsaved edits').nextElementSibling).toHaveTextContent('1');

    await userEvent.click(within(draftEditor).getByRole('button', { name: /save draft/i }));
    expect(updateDraft).toHaveBeenCalledWith('draft-job', 'zh-CN', {
      segments: [{ index: 1, text: '人工修正后的字幕' }]
    });
    expect(await within(draftEditor).findByText('Draft saved.')).toBeInTheDocument();

    await userEvent.clear(within(draftEditor).getByLabelText('Draft text 1'));
    await userEvent.type(within(draftEditor).getByLabelText('Draft text 1'), '未保存文本');
    await userEvent.click(within(draftEditor).getByRole('button', { name: /reset unsaved/i }));
    expect(within(draftEditor).getByDisplayValue('人工修正后的字幕')).toBeInTheDocument();

    await userEvent.click(within(draftEditor).getByRole('button', { name: /clear draft/i }));
    expect(clearDraft).toHaveBeenCalledWith('draft-job', 'zh-CN');
    expect(await within(draftEditor).findByText('Draft cleared.')).toBeInTheDocument();

    await userEvent.click(within(draftEditor).getByLabelText(/include reviewed burned video/i));
    await userEvent.click(within(draftEditor).getByRole('button', { name: /publish reviewed subtitles/i }));
    expect(publishReviewed).toHaveBeenCalledWith('draft-job', {
      language: 'zh-CN',
      includeBurnedVideo: true
    });
    expect(listArtifacts).toHaveBeenCalledTimes(2);
    expect(getDeliveryManifest).toHaveBeenCalledTimes(2);
    expect(await within(draftEditor).findByText('Published 1 reviewed artifacts.')).toBeInTheDocument();
    expect(await within(handoff).findByText('Ready for handoff')).toBeInTheDocument();
    expect(within(handoff).getByText('reviewed-subtitles.zh-CN.srt')).toBeInTheDocument();
    expect(within(handoff).getByRole('link', { name: /download delivery manifest/i })).toHaveAttribute(
      'href',
      '/api/jobs/draft-job/delivery-manifest/markdown/download'
    );
    expect(within(handoff).getByRole('link', { name: /download handoff package/i })).toHaveAttribute(
      'href',
      '/api/jobs/draft-job/handoff-package/download'
    );
    const checklist = await screen.findByRole('region', { name: /demo handoff checklist/i });
    expect(within(checklist).getByRole('link', { name: /download handoff package/i })).toHaveAttribute(
      'href',
      '/api/jobs/draft-job/handoff-package/download'
    );
    const sessionReport = await screen.findByRole('region', { name: /demo session report/i });
    expect(within(sessionReport).getByRole('link', { name: /download handoff package/i })).toHaveAttribute(
      'href',
      '/api/jobs/draft-job/handoff-package/download'
    );
    const delivery = screen.getByRole('region', { name: /result delivery/i });
    expect(within(delivery).getByText('Reviewed SRT')).toBeInTheDocument();
    expect(within(delivery).getByText('reviewed-subtitles.zh-CN.srt')).toBeInTheDocument();
  });

  test('exports safe browser demo evidence for a selected job', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    const createObjectUrl = vi.fn().mockReturnValue('blob:linguaframe-evidence');
    const revokeObjectUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectUrl,
      configurable: true
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: revokeObjectUrl,
      configurable: true
    });
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'evidence-job', videoId: 'evidence-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([
      {
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: 'Sensitive source line that must not be exported'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([
      {
        language: 'zh-CN',
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: '敏感字幕不能导出'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      {
        artifactId: 'artifact-vtt',
        jobId: 'evidence-job',
        type: 'SUBTITLE_VTT',
        filename: 'subtitles.vtt',
        contentType: 'text/vtt',
        sizeBytes: 42,
        contentSha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        cacheHit: true,
        sourceArtifactId: 'source-vtt-artifact',
        createdAt: '2026-06-26T10:00:05Z'
      },
      artifactFixture({
        artifactId: 'reviewed-json',
        jobId: 'evidence-job',
        type: 'REVIEWED_SUBTITLE_JSON',
        filename: 'reviewed-subtitles.zh-CN.json'
      }),
      artifactFixture({
        artifactId: 'reviewed-srt',
        jobId: 'evidence-job',
        type: 'REVIEWED_SUBTITLE_SRT',
        filename: 'reviewed-subtitles.zh-CN.srt'
      }),
      artifactFixture({
        artifactId: 'reviewed-video',
        jobId: 'evidence-job',
        type: 'REVIEWED_BURNED_VIDEO',
        filename: 'reviewed-burned-video.mp4'
      })
    ]);
    vi.spyOn(linguaFrameApi, 'getSubtitleReview').mockResolvedValue(
      subtitleReviewFixture({
        jobId: 'evidence-job',
        segments: [
          {
            index: 0,
            startMs: 0,
            endMs: 1200,
            sourceText: 'Sensitive review source line that must not be exported',
            targetText: '敏感审核字幕不能导出',
            durationMs: 1200,
            timingDeltaMs: 0,
            status: 'ALIGNED'
          }
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getSubtitleDraft').mockResolvedValue(
      subtitleDraftFixture({
        jobId: 'evidence-job',
        editedSegmentCount: 1,
        segments: [
          {
            index: 0,
            startMs: 0,
            endMs: 1200,
            sourceText: 'Sensitive draft source line that must not be exported',
            generatedText: '敏感生成字幕不能导出',
            draftText: '敏感草稿字幕不能导出',
            edited: true,
            updatedAt: '2026-06-28T10:00:00Z'
          }
        ]
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'evidence-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const evidence = await screen.findByRole('region', { name: /demo evidence/i });
    const evidencePreview = evidence.querySelector('.evidence-preview');
    expect(evidencePreview).not.toBeNull();
    const evidenceText = evidencePreview?.textContent ?? '';
    expect(evidenceText).toContain('Job: evidence-job');
    expect(evidenceText).toContain('Status: QUEUED');
    expect(evidenceText).toContain('Artifacts: 4');
    expect(evidenceText).toContain('Transcript preview segments: 1');
    expect(evidenceText).toContain('Subtitle preview segments: 1');
    expect(evidenceText).toContain('Subtitle review segments: 2');
    expect(evidenceText).toContain('Subtitle review timing mismatches: 1');
    expect(evidenceText).toContain('Subtitle review quality: 88 / 100, NEEDS_REVIEW');
    expect(evidenceText).toContain('Subtitle draft segments: 2');
    expect(evidenceText).toContain('Subtitle draft edited segments: 1');
    expect(evidenceText).toContain('Reviewed subtitle artifacts: 2');
    expect(evidenceText).toContain('Reviewed burned video: Available');
    expect(evidenceText).toContain('Pipeline current stage: TARGET_SUBTITLE_EXPORT');
    expect(evidenceText).toContain('Pipeline completed: 2 / 10');
    expect(evidenceText).toContain('Pipeline slowest stage: TARGET_SUBTITLE_EXPORT (1.5 s)');
    expect(evidenceText).toContain('Result bundle: /api/jobs/evidence-job/artifacts/archive/download');
    expect(evidenceText).toContain('Diagnostics: /api/jobs/evidence-job/diagnostics/download');
    expect(within(evidence).getByRole('link', { name: /download backend evidence/i })).toHaveAttribute(
      'href',
      '/api/jobs/evidence-job/evidence/markdown/download'
    );
    expect(within(evidence).getByRole('link', { name: /download evidence bundle/i })).toHaveAttribute(
      'href',
      '/api/jobs/evidence-job/evidence/bundle/download'
    );
    expect(evidenceText).not.toContain('Sensitive source line');
    expect(evidenceText).not.toContain('敏感字幕');
    expect(evidenceText).not.toContain('Sensitive review source line');
    expect(evidenceText).not.toContain('敏感审核字幕');
    expect(evidenceText).not.toContain('Sensitive draft source line');
    expect(evidenceText).not.toContain('敏感生成字幕');
    expect(evidenceText).not.toContain('敏感草稿字幕');
    expect(evidenceText).not.toContain('source-vtt-artifact');

    await userEvent.click(within(evidence).getByRole('button', { name: /copy evidence/i }));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('# LinguaFrame Demo Evidence'));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('- Job: evidence-job'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('Sensitive source line'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('Sensitive review source line'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('敏感草稿字幕'));
    expect(await within(evidence).findByText('Evidence copied.')).toBeInTheDocument();

    await userEvent.click(within(evidence).getByRole('button', { name: /download evidence json/i }));
    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(anchorClick).toHaveBeenCalled();
    expect(await within(evidence).findByText('Evidence JSON downloaded.')).toBeInTheDocument();
  });

  test('keeps selected job usable when subtitle review fails to load', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'review-error-job', videoId: 'review-error-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getSubtitleReview').mockRejectedValue(new Error('Review unavailable'));

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'review-error-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    expect(await screen.findByRole('heading', { name: /job review-error-job/i })).toBeInTheDocument();
    const errors = screen.getByRole('region', { name: /artifacts/i });
    expect(within(errors).getByText('Subtitle review: Review unavailable')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: /subtitle review/i })).toHaveTextContent(
      'No subtitle review summary loaded yet.'
    );
  });

  test('shows clipboard unavailable state for browser demo evidence', async () => {
    Object.defineProperty(navigator, 'clipboard', {
      value: undefined,
      configurable: true
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(jobFixture({ jobId: 'no-clipboard-job' }));
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'no-clipboard-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const evidence = await screen.findByRole('region', { name: /demo evidence/i });
    expect(within(evidence).getByText('Clipboard copy is unavailable in this browser.')).toBeInTheDocument();
    expect(within(evidence).getByRole('button', { name: /copy evidence/i })).toBeDisabled();
  });

  test('compares selected job with another demo run using backend evidence', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'compare-baseline-job',
            videoId: 'compare-video',
            filename: 'compare.mp4',
            status: 'COMPLETED',
            demoProfileId: 'quick-baseline'
          }),
          jobSummaryFixture({
            jobId: 'compare-showcase-job',
            videoId: 'compare-video',
            filename: 'compare.mp4',
            status: 'COMPLETED',
            demoProfileId: 'tears-showcase'
          })
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'compare-baseline-job',
        videoId: 'compare-video',
        status: 'COMPLETED',
        demoProfileId: 'quick-baseline'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getJobComparison').mockResolvedValue(jobComparisonFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'compare-baseline-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));
    const comparison = await screen.findByRole('region', { name: /demo comparison/i });
    await userEvent.selectOptions(
      within(comparison).getByLabelText(/comparison job/i),
      'compare-showcase-job'
    );

    expect(await within(comparison).findByText('compare-showcase-job')).toBeInTheDocument();
    expect(within(comparison).getByText('tears-showcase')).toBeInTheDocument();
    expect(within(comparison).getByText('+9')).toBeInTheDocument();
    expect(within(comparison).getByText('+$0.00007800')).toBeInTheDocument();
    expect(within(comparison).getByText('demoProfileId')).toBeInTheDocument();
    expect(within(comparison).getByRole('link', { name: /download markdown/i })).toHaveAttribute(
      'href',
      '/api/jobs/compare-baseline-job/comparison/compare-showcase-job/markdown/download'
    );
    expect(linguaFrameApi.getJobComparison).toHaveBeenCalledWith(
      'compare-baseline-job',
      'compare-showcase-job'
    );
  });

  test('shows a same-source demo run matrix for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'matrix-showcase-job',
        videoId: 'matrix-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoRunMatrix').mockResolvedValue(demoRunMatrixFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'matrix-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const matrix = await screen.findByRole('region', { name: /demo run matrix/i });
    expect(within(matrix).getByText('quick-baseline')).toBeInTheDocument();
    expect(within(matrix).getByText('tears-showcase')).toBeInTheDocument();
    expect(within(matrix).getByText('Best quality')).toBeInTheDocument();
    expect(within(matrix).getByText('Lowest cost')).toBeInTheDocument();
    expect(within(matrix).getByText('$0.00014100')).toBeInTheDocument();
    expect(within(matrix).getByText('1 provider hits')).toBeInTheDocument();
    expect(within(matrix).getAllByText('Ready')).toHaveLength(2);
    expect(linguaFrameApi.getDemoRunMatrix).toHaveBeenCalledWith('matrix-showcase-job', 8);
  });

  test('cache replay compares a pinned baseline with a completed cache-hit job', async () => {
    const baselineJob = jobFixture({
      jobId: 'cache-baseline-job',
      videoId: 'cache-video',
      status: 'COMPLETED',
      targetLanguage: 'zh-CN',
      usageSummary: {
        ...jobFixture().usageSummary!,
        modelCallCount: 3,
        estimatedCostUsd: 0.0009
      },
      cacheSummary: {
        cacheHitCount: 0,
        generatedArtifactCount: 3,
        providerCacheHitCount: 0
      }
    });
    const replayJob = jobFixture({
      jobId: 'cache-replay-job',
      videoId: 'cache-video',
      status: 'COMPLETED',
      targetLanguage: 'zh-CN',
      usageSummary: {
        ...jobFixture().usageSummary!,
        modelCallCount: 1,
        estimatedCostUsd: 0.0001
      },
      cacheSummary: {
        cacheHitCount: 2,
        generatedArtifactCount: 1,
        providerCacheHitCount: 2
      },
      timelineEvents: [
        {
          id: 'cache-hit-translation',
          stage: 'TARGET_SUBTITLE_EXPORT',
          status: 'CACHE_HIT',
          message: 'Reused cached TRANSLATION provider result.',
          durationMs: 0,
          errorSummary: null,
          occurredAt: '2026-06-26T10:00:02Z'
        },
        {
          id: 'cache-hit-tts',
          stage: 'DUBBING_AUDIO_GENERATION',
          status: 'CACHE_HIT',
          message: 'Reused cached TTS provider result.',
          durationMs: 0,
          errorSummary: null,
          occurredAt: '2026-06-26T10:00:03Z'
        }
      ]
    });
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'cache-baseline-job',
            videoId: 'cache-video',
            filename: 'cache-demo.mp4',
            status: 'COMPLETED',
            estimatedCostUsd: 0.0009
          }),
          jobSummaryFixture({
            jobId: 'cache-replay-job',
            videoId: 'cache-video',
            filename: 'cache-demo.mp4',
            status: 'COMPLETED',
            estimatedCostUsd: 0.0001
          })
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockImplementation(async (jobId: string) => {
      if (jobId === 'cache-baseline-job') {
        return baselineJob;
      }
      if (jobId === 'cache-replay-job') {
        return replayJob;
      }
      throw new Error('job not found');
    });
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockImplementation(async (jobId: string) => {
      if (jobId === 'cache-baseline-job') {
        return [
          artifactFixture({ artifactId: 'baseline-transcript', jobId, cacheHit: false }),
          artifactFixture({
            artifactId: 'baseline-subtitle',
            jobId,
            type: 'TARGET_SUBTITLE_VTT',
            filename: 'target-subtitles.vtt',
            cacheHit: false
          }),
          artifactFixture({
            artifactId: 'baseline-video',
            jobId,
            type: 'BURNED_VIDEO',
            filename: 'burned-video.mp4',
            cacheHit: false
          })
        ];
      }
      if (jobId === 'cache-replay-job') {
        return [
          artifactFixture({ artifactId: 'replay-transcript', jobId, cacheHit: true }),
          artifactFixture({
            artifactId: 'replay-subtitle',
            jobId,
            type: 'TARGET_SUBTITLE_VTT',
            filename: 'target-subtitles.vtt',
            cacheHit: true
          }),
          artifactFixture({
            artifactId: 'replay-video',
            jobId,
            type: 'BURNED_VIDEO',
            filename: 'burned-video.mp4',
            cacheHit: false
          })
        ];
      }
      return [];
    });

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'cache-baseline-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));
    const replay = await screen.findByRole('region', { name: /cache replay/i });
    await userEvent.click(within(replay).getByRole('button', { name: /pin as baseline/i }));
    await userEvent.selectOptions(
      within(replay).getByLabelText(/comparison job/i),
      'cache-replay-job'
    );

    expect(await within(replay).findByText('cache-baseline-job')).toBeInTheDocument();
    expect(within(replay).getByText('cache-replay-job')).toBeInTheDocument();
    expect(within(replay).getByText('2 provider hits')).toBeInTheDocument();
    expect(within(replay).getByText('2 reused / 1 generated')).toBeInTheDocument();
    expect(within(replay).getByText('-2 calls')).toBeInTheDocument();
    expect(within(replay).getByText('-$0.00080000')).toBeInTheDocument();
    expect(within(replay).getByText('TARGET_SUBTITLE_EXPORT')).toBeInTheDocument();
    expect(within(replay).getByText('DUBBING_AUDIO_GENERATION')).toBeInTheDocument();
  });

  test('exports safe cache replay evidence', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    const createObjectUrl = vi.fn().mockReturnValue('blob:cache-replay-evidence');
    const revokeObjectUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectUrl,
      configurable: true
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: revokeObjectUrl,
      configurable: true
    });
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({ jobId: 'safe-baseline-job', status: 'COMPLETED' }),
          jobSummaryFixture({ jobId: 'safe-replay-job', status: 'COMPLETED' })
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockImplementation(async (jobId: string) =>
      jobFixture({
        jobId,
        status: 'COMPLETED',
        timelineEvents:
          jobId === 'safe-replay-job'
            ? [
                {
                  id: 'safe-cache-hit',
                  stage: 'TARGET_SUBTITLE_EXPORT',
                  status: 'CACHE_HIT',
                  message: 'Reused cached provider result with source-videos/private-object-key.mp4',
                  durationMs: 0,
                  errorSummary: null,
                  occurredAt: '2026-06-26T10:00:02Z'
                }
              ]
            : []
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([
      {
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: 'Sensitive transcript line'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([
      {
        language: 'zh-CN',
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: '敏感字幕'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'safe-artifact',
        sourceArtifactId: 'source-videos/private-object-key.mp4',
        cacheHit: true
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'safe-baseline-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));
    const replay = await screen.findByRole('region', { name: /cache replay/i });
    await userEvent.click(within(replay).getByRole('button', { name: /pin as baseline/i }));
    await userEvent.selectOptions(within(replay).getByLabelText(/comparison job/i), 'safe-replay-job');

    await userEvent.click(await within(replay).findByRole('button', { name: /copy replay evidence/i }));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('# LinguaFrame Cache Replay Evidence'));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('safe-baseline-job'));
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('safe-replay-job'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('Sensitive transcript line'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('敏感字幕'));
    expect(writeText).not.toHaveBeenCalledWith(expect.stringContaining('private-object-key'));

    await userEvent.click(within(replay).getByRole('button', { name: /download replay evidence json/i }));
    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(anchorClick).toHaveBeenCalled();
    expect(await within(replay).findByText('Replay evidence JSON downloaded.')).toBeInTheDocument();
  });

  test('shows cache replay comparison load errors without clearing the selected job', async () => {
    vi.spyOn(linguaFrameApi, 'listJobs').mockResolvedValue(
      jobListFixture({
        jobs: [
          jobSummaryFixture({ jobId: 'baseline-ok', status: 'COMPLETED' }),
          jobSummaryFixture({ jobId: 'comparison-fails', status: 'COMPLETED' })
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getJob').mockImplementation(async (jobId: string) => {
      if (jobId === 'baseline-ok') {
        return jobFixture({ jobId, status: 'COMPLETED' });
      }
      throw new Error('Comparison unavailable');
    });
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'baseline-ok');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));
    const replay = await screen.findByRole('region', { name: /cache replay/i });
    await userEvent.click(within(replay).getByRole('button', { name: /pin as baseline/i }));
    await userEvent.selectOptions(within(replay).getByLabelText(/comparison job/i), 'comparison-fails');

    expect(await within(replay).findByText('Comparison unavailable')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /job baseline-ok/i })).toBeInTheDocument();
  });

  test('shows preview-only and missing result delivery states', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'preview-only-job', videoId: 'preview-only-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([
      {
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: 'Preview transcript line'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([
      {
        language: 'zh-CN',
        index: 0,
        startMs: 0,
        endMs: 1200,
        text: '预览字幕'
      }
    ]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'preview-only-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const delivery = await screen.findByRole('region', { name: /result delivery/i });
    expect(within(delivery).getByText('0 generated')).toBeInTheDocument();
    expect(within(delivery).getByText('0 reused')).toBeInTheDocument();
    expect(within(delivery).getByText('7 missing')).toBeInTheDocument();
    expect(within(delivery).getByText('Transcript JSON')).toBeInTheDocument();
    expect(within(delivery).getAllByText('Preview only')).toHaveLength(6);
    expect(within(delivery).getByText('Dubbing audio')).toBeInTheDocument();
    expect(within(delivery).getAllByText('Missing').length).toBeGreaterThan(0);
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
    stageTimings: [
      {
        stage: 'AUDIO_EXTRACTION',
        completedEventCount: 2,
        failedEventCount: 0,
        averageDurationMs: 1200,
        maxDurationMs: 2400,
        latestDurationMs: 900
      }
    ],
    ...overrides
  };
}

function privateDemoOperationsFixture(
  overrides: Partial<PrivateDemoOperations> = {}
): PrivateDemoOperations {
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
      },
      {
        title: 'Live dependencies',
        status: 'READY',
        checks: [
          {
            label: 'database',
            status: 'READY',
            detail: 'Database query succeeded',
            nextAction: 'No action required before the next demo run.'
          }
        ]
      },
      {
        title: 'Cost safety',
        status: 'READY',
        checks: [
          {
            label: 'Per-job budget guard',
            status: 'READY',
            detail: 'Per-job budget guard is enabled at $0.50000000.',
            nextAction: 'Keep the limit aligned with the planned demo sample length.'
          }
        ]
      },
      {
        title: 'Storage and recovery',
        status: 'READY',
        checks: [
          {
            label: 'Backup and restore commands',
            status: 'READY',
            detail: 'Dry-run backup and restore commands are available.',
            nextAction: 'Run backup dry-run first.'
          }
        ]
      },
      {
        title: 'Retention cleanup',
        status: 'READY',
        checks: [
          {
            label: 'Retention policy',
            status: 'READY',
            detail: 'Retention cleanup is enabled and preview reports 2 candidate jobs.',
            nextAction: 'Review the browser preview before any deleting cleanup run.'
          }
        ]
      },
      {
        title: 'Demo evidence',
        status: 'READY',
        checks: [
          {
            label: 'Recorded demo jobs',
            status: 'READY',
            detail: '5 jobs are visible in the operator dashboard.',
            nextAction: 'Use a completed job for browser review.'
          }
        ]
      }
    ],
    commands: [
      {
        label: 'Private demo preflight',
        command: 'scripts/demo/private-demo-preflight.sh',
        detail: 'Checks local env and dependency reachability.'
      },
      {
        label: 'Backup dry-run',
        command: 'scripts/demo/private-demo-backup.sh --dry-run',
        detail: 'Validates backup shape without exporting service data.'
      }
    ],
    documentationLinks: [
      {
        label: 'Private demo deployment',
        path: 'docs/deployment/private-demo.md',
        detail: 'Reverse proxy, env, backup, and restore runbook.'
      }
    ],
    ...overrides
  };
}

function runtimeDependenciesFixture(
  overrides: Partial<RuntimeDependencySummary> = {}
): RuntimeDependencySummary {
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
      budget: {
        enabled: false,
        maxJobCostUsd: 0,
        dailyBudgetGuardEnabled: false,
        maxDailyCostUsd: 0,
        budgetIdentity: 'demo-owner',
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
        budgetGuard: { enabled: false },
        dailyBudgetGuard: { enabled: false }
      }
    },
    ...overrides
  };
}

function runtimeLiveChecksFixture(
  overrides: Partial<RuntimeLiveCheckSummary> = {}
): RuntimeLiveCheckSummary {
  return {
    healthy: true,
    checkedAt: '2026-06-28T08:00:00Z',
    checks: {
      database: { status: 'UP', latencyMs: 5, message: 'Database probe succeeded' },
      redis: { status: 'UP', latencyMs: 4, message: 'Redis ping succeeded' },
      rabbitmq: { status: 'UP', latencyMs: 6, message: 'RabbitMQ connection succeeded' },
      minio: { status: 'UP', latencyMs: 7, message: 'MinIO bucket is reachable' },
      ffmpeg: { status: 'UP', latencyMs: 8, message: 'FFmpeg executable responded' },
      openai: { status: 'SKIPPED', latencyMs: 1, message: 'OpenAI connectivity check is disabled' }
    },
    ...overrides
  };
}

function demoSessionStatusFixture(overrides: Partial<DemoSessionStatus> = {}): DemoSessionStatus {
  return {
    accessGateEnabled: false,
    authenticated: true,
    headerName: 'X-LinguaFrame-Demo-Token',
    mode: 'OPEN',
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

function demoRunProfileFixtures(overrides: Partial<DemoRunProfile>[] = []): DemoRunProfile[] {
  const profiles: DemoRunProfile[] = [
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
    },
    {
      id: 'tears-showcase',
      label: 'Tears showcase',
      description: 'Presentation profile for Tears of Steel.',
      targetLanguage: 'zh-CN',
      ttsVoice: '',
      translationStyle: 'FORMAL',
      subtitleStylePreset: 'HIGH_CONTRAST',
      subtitlePolishingMode: 'BALANCED',
      translationGlossary: 'Maya => 玛雅\nTears of Steel => 钢铁之泪'
    }
  ];
  return profiles.map((profile, index) => ({ ...profile, ...(overrides[index] ?? {}) }));
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
    translationStyle: 'NATURAL',
    subtitleStylePreset: 'STANDARD',
    translationGlossaryEntryCount: 0,
    translationGlossaryHash: '',
    subtitlePolishingMode: 'OFF',
    demoProfileId: null,
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
    translationStyle: 'NATURAL',
    subtitleStylePreset: 'STANDARD',
    translationGlossaryEntryCount: 0,
    translationGlossaryHash: '',
    subtitlePolishingMode: 'OFF',
    demoProfileId: null,
    createdAt: '2026-06-26T10:00:00Z',
    ...overrides
  };
}

function mediaUploadDetailFixture(overrides: Partial<MediaUploadDetail> = {}): MediaUploadDetail {
  return {
    videoId: 'video-1',
    filename: 'sample.mp4',
    contentType: 'video/mp4',
    fileSizeBytes: 1234,
    durationSeconds: 42,
    status: 'UPLOADED',
    createdAt: '2026-06-26T10:00:00Z',
    ...overrides
  };
}

function deliveryManifestFixture(overrides: Partial<DeliveryManifest> = {}): DeliveryManifest {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    subtitleStylePreset: 'STANDARD',
    translationGlossaryEntryCount: 0,
    translationGlossaryHash: '',
    subtitlePolishingMode: 'OFF',
    demoProfileId: null,
    status: 'COMPLETED',
    generatedAt: '2026-06-28T11:00:00Z',
    handoffReady: true,
    reviewedSubtitleArtifactCount: 3,
    reviewedBurnedVideoAvailable: false,
    generatedArtifactCount: 1,
    reviewedArtifacts: [],
    auditArtifacts: [],
    links: [
      {
        label: 'Result bundle',
        kind: 'RESULT_BUNDLE',
        url: '/api/jobs/job-1/artifacts/archive/download'
      }
    ],
    ...overrides
  };
}

function jobComparisonFixture(overrides: Partial<JobComparison> = {}): JobComparison {
  return {
    baselineJobId: 'compare-baseline-job',
    comparisonJobId: 'compare-showcase-job',
    sameSourceVideo: true,
    generatedAt: '2026-06-28T12:00:00Z',
    baseline: {
      jobId: 'compare-baseline-job',
      videoId: 'compare-video',
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
      jobId: 'compare-showcase-job',
      videoId: 'compare-video',
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
    ],
    ...overrides
  };
}

function demoRunMatrixFixture(overrides: Partial<DemoRunMatrix> = {}): DemoRunMatrix {
  return {
    anchorJobId: 'matrix-showcase-job',
    videoId: 'matrix-video',
    generatedAt: '2026-06-28T12:00:00Z',
    recommendedBaselineJobId: 'matrix-baseline-job',
    bestQualityJobId: 'matrix-showcase-job',
    lowestCostJobId: 'matrix-baseline-job',
    jobs: [
      {
        jobId: 'matrix-showcase-job',
        videoId: 'matrix-video',
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
      },
      {
        jobId: 'matrix-baseline-job',
        videoId: 'matrix-video',
        filename: 'tears.mp4',
        targetLanguage: 'zh-CN',
        demoProfileId: 'quick-baseline',
        ttsVoice: null,
        translationStyle: 'NATURAL',
        subtitleStylePreset: 'STANDARD',
        translationGlossaryEntryCount: 0,
        translationGlossaryHash: '',
        subtitlePolishingMode: 'OFF',
        status: 'COMPLETED',
        createdAt: '2026-06-28T10:00:00Z',
        completedAt: '2026-06-28T10:02:00Z',
        failureStage: null,
        failureReason: null,
        retryCount: 0,
        qualityScore: 82,
        qualityVerdict: 'GOOD',
        modelCallCount: 1,
        failedModelCallCount: 0,
        estimatedCostUsd: 0.000063,
        artifactCacheHitCount: 0,
        generatedArtifactCount: 3,
        providerCacheHitCount: 0,
        handoffReady: true
      }
    ],
    ...overrides
  };
}

function mediaUploadValidationFixture(
  overrides: Partial<MediaUploadValidation> = {}
): MediaUploadValidation {
  return {
    valid: true,
    code: 'READY',
    message: 'File is ready for upload.',
    filename: 'sample.mp4',
    contentType: 'video/mp4',
    fileSizeBytes: 1234,
    maxFileSizeBytes: 104857600,
    durationSeconds: 42,
    maxDurationSeconds: 300,
    supportedContentTypes: ['video/mp4', 'video/quicktime'],
    ...overrides
  };
}

function artifactFixture(overrides: Partial<JobArtifact> = {}): JobArtifact {
  return {
    artifactId: 'artifact-1',
    jobId: 'job-1',
    type: 'TRANSCRIPT_JSON',
    filename: 'transcript.json',
    contentType: 'application/json',
    sizeBytes: 84,
    contentSha256: '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
    cacheHit: false,
    sourceArtifactId: null,
    createdAt: '2026-06-26T10:00:05Z',
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

function subtitleReviewFixture(
  overrides: Partial<SubtitleReviewSummary> = {}
): SubtitleReviewSummary {
  return {
    jobId: 'job-1',
    targetLanguage: 'zh-CN',
    segmentCount: 2,
    missingTargetCount: 0,
    timingMismatchCount: 1,
    averageDurationMs: 1400,
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
        endMs: 1200,
        sourceText: 'First source line',
        targetText: '第一行字幕',
        durationMs: 1200,
        timingDeltaMs: 0,
        status: 'ALIGNED'
      },
      {
        index: 1,
        startMs: 1200,
        endMs: 2800,
        sourceText: 'Second source line',
        targetText: '第二行字幕',
        durationMs: 1600,
        timingDeltaMs: 400,
        status: 'TIMING_MISMATCH'
      }
    ],
    ...overrides
  };
}

function subtitleDraftFixture(
  overrides: Partial<SubtitleDraftSummary> = {}
): SubtitleDraftSummary {
  return {
    jobId: 'job-1',
    targetLanguage: 'zh-CN',
    segmentCount: 2,
    editedSegmentCount: 1,
    lastUpdatedAt: '2026-06-28T10:00:00Z',
    segments: [
      {
        index: 0,
        startMs: 0,
        endMs: 1200,
        sourceText: 'First source line',
        generatedText: '第一行字幕',
        draftText: '第一行字幕',
        edited: false,
        updatedAt: null
      },
      {
        index: 1,
        startMs: 1200,
        endMs: 2800,
        sourceText: 'Second source line',
        generatedText: '第二行字幕',
        draftText: '修正后的第二行字幕',
        edited: true,
        updatedAt: '2026-06-28T10:00:00Z'
      }
    ],
    ...overrides
  };
}

function jobFixture(overrides: Partial<LocalizationJob> = {}): LocalizationJob {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    ttsVoice: 'verse',
    translationStyle: 'NATURAL',
    subtitleStylePreset: 'STANDARD',
    translationGlossaryEntryCount: 0,
    translationGlossaryHash: '',
    subtitlePolishingMode: 'OFF',
    demoProfileId: null,
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
    failureTriage: null,
    pipelineProgress: pipelineProgressFixture(),
    ...overrides
  };
}

function pipelineProgressFixture(
  overrides: Partial<NonNullable<LocalizationJob['pipelineProgress']>> = {}
): NonNullable<LocalizationJob['pipelineProgress']> {
  return {
    totalStageCount: 10,
    completedStageCount: 2,
    failedStageCount: 1,
    skippedStageCount: 0,
    cacheHitStageCount: 0,
    currentStage: 'TARGET_SUBTITLE_EXPORT',
    terminal: true,
    totalMeasuredDurationMs: 1700,
    slowestStage: 'TARGET_SUBTITLE_EXPORT',
    slowestStageDurationMs: 1500,
    stages: [
      {
        stage: 'WORKER_RECEIVED',
        status: 'SUCCEEDED',
        startedAt: '2026-06-26T10:00:01Z',
        finishedAt: '2026-06-26T10:00:01Z',
        durationMs: 50,
        message: 'Worker received localization job.'
      },
      {
        stage: 'WORKER_SMOKE',
        status: 'SUCCEEDED',
        startedAt: '2026-06-26T10:00:01Z',
        finishedAt: '2026-06-26T10:00:02Z',
        durationMs: 150,
        message: 'WORKER_SMOKE succeeded'
      },
      {
        stage: 'TARGET_SUBTITLE_EXPORT',
        status: 'FAILED',
        startedAt: '2026-06-26T10:00:02Z',
        finishedAt: '2026-06-26T10:00:04Z',
        durationMs: 1500,
        message: 'TARGET_SUBTITLE_EXPORT failed'
      }
    ],
    ...overrides
  };
}
