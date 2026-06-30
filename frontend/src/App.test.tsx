import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, test, vi } from 'vitest';

import { App } from './App';
import { linguaFrameApi } from './api/linguaframeApi';
import type {
  AuthLoginResponse,
  AuthSessionStatus,
  DeliveryManifest,
  DemoAcceptanceGate,
  DemoCompletionCertificate,
  DemoHandoffPortal,
  DemoPresentationCockpit,
  DemoReviewerWorkspace,
  DemoRunLauncher,
  DemoSampleMediaCatalog,
  DemoSessionCommandCenter,
  DemoRunMonitor,
  DemoReplayCard,
  DemoRunSnapshot,
  DemoPresenterPack,
  DemoRunMatrix,
  DemoShareSheet,
  JobArtifact,
  JobComparison,
  LocalizationJob,
  LocalizationJobList,
  MediaUpload,
  MediaUploadDetail,
  MediaUploadValidation,
  ModelUsageLedger,
  NarrationDemoPreset,
  NarrationDemoRenderPreflight,
  NarrationDemoRenderResult,
  NarrationEvidence,
  NarrationGeneration,
  NarrationScriptPackage,
  NarratedVideoGeneration,
  NarrationWorkspace,
  OpenAiReadinessEvidence,
  OpenAiSmokeProof,
  DemoRunProfile,
  DemoSessionStatus,
  DemoUploadReadiness,
  OperatorDashboard,
  OwnerQuotaPreflight,
  PrivateDemoEvidenceGallery,
  PrivateDemoLaunchRehearsal,
  PrivateDemoOperations,
  PrivateDemoRunArchive,
  PromptTemplate,
  RetentionCleanupResult,
  ReviewedSubtitleWorkflow,
  RuntimeDependencySummary,
  RuntimeLiveCheckSummary,
  SubtitleDraftSummary,
  SubtitleReviewEvidence,
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
    vi.spyOn(linguaFrameApi, 'getModelUsageLedger').mockResolvedValue(modelUsageLedgerFixture());
    vi.spyOn(linguaFrameApi, 'downloadModelUsageLedgerMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame Model Usage Ledger'], { type: 'text/markdown' })
    );
    vi.spyOn(linguaFrameApi, 'getPrivateDemoOperations').mockResolvedValue(
      privateDemoOperationsFixture()
    );
    vi.spyOn(linguaFrameApi, 'getPrivateDemoLaunchRehearsal').mockResolvedValue(
      privateDemoLaunchRehearsalFixture()
    );
    vi.spyOn(linguaFrameApi, 'getPrivateDemoEvidenceGallery').mockResolvedValue(
      privateDemoEvidenceGalleryFixture()
    );
    vi.spyOn(linguaFrameApi, 'getPrivateDemoRunArchive').mockResolvedValue(
      privateDemoRunArchiveFixture()
    );
    vi.spyOn(linguaFrameApi, 'getDemoSampleMediaCatalog').mockResolvedValue(
      demoSampleMediaCatalogFixture()
    );
    vi.spyOn(linguaFrameApi, 'getDemoRunLauncher').mockResolvedValue(
      demoRunLauncherFixture()
    );
    vi.spyOn(linguaFrameApi, 'getDemoPresentationCockpit').mockResolvedValue(
      demoPresentationCockpitFixture()
    );
    vi.spyOn(linguaFrameApi, 'getDemoSessionCommandCenter').mockResolvedValue(
      demoSessionCommandCenterFixture()
    );
    vi.spyOn(linguaFrameApi, 'downloadDemoSessionCommandCenterMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame Demo Session Command Center'], { type: 'text/markdown' })
    );
    vi.spyOn(linguaFrameApi, 'downloadDemoSessionEvidencePackageZip').mockResolvedValue(
      new Blob(['zip-bytes'], { type: 'application/zip' })
    );
    vi.spyOn(linguaFrameApi, 'getRuntimeDependencies').mockResolvedValue(runtimeDependenciesFixture());
    vi.spyOn(linguaFrameApi, 'getRuntimeLiveChecks').mockResolvedValue(runtimeLiveChecksFixture());
    vi.spyOn(linguaFrameApi, 'getOpenAiReadinessEvidence').mockResolvedValue(openAiReadinessEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'downloadOpenAiReadinessEvidenceMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame OpenAI Readiness Evidence'], { type: 'text/markdown' })
    );
    vi.spyOn(linguaFrameApi, 'getDemoSession').mockResolvedValue(demoSessionStatusFixture());
    vi.spyOn(linguaFrameApi, 'getAuthSession').mockResolvedValue(authSessionStatusFixture());
    vi.spyOn(linguaFrameApi, 'getOwnerQuotaPreflight').mockResolvedValue(ownerQuotaPreflightFixture());
    vi.spyOn(linguaFrameApi, 'getDemoUploadReadiness').mockResolvedValue(
      demoUploadReadinessFixture()
    );
    vi.spyOn(linguaFrameApi, 'getRetentionCleanupPreview').mockResolvedValue(
      retentionCleanupResultFixture()
    );
    vi.spyOn(linguaFrameApi, 'listPromptTemplates').mockResolvedValue(promptTemplateFixtures());
    vi.spyOn(linguaFrameApi, 'getSubtitleReview').mockResolvedValue(subtitleReviewFixture());
    vi.spyOn(linguaFrameApi, 'getReviewedSubtitleWorkflow').mockResolvedValue(
      reviewedSubtitleWorkflowFixture()
    );
    vi.spyOn(linguaFrameApi, 'getSubtitleReviewEvidence').mockResolvedValue(
      subtitleReviewEvidenceFixture()
    );
    vi.spyOn(linguaFrameApi, 'getSubtitleDraft').mockResolvedValue(subtitleDraftFixture());
    vi.spyOn(linguaFrameApi, 'getDeliveryManifest').mockResolvedValue(deliveryManifestFixture());
    vi.spyOn(linguaFrameApi, 'getDemoRunMatrix').mockResolvedValue(demoRunMatrixFixture());
    vi.spyOn(linguaFrameApi, 'getDemoRunMonitor').mockResolvedValue(demoRunMonitorFixture());
    vi.spyOn(linguaFrameApi, 'getDemoReplayCard').mockResolvedValue(demoReplayCardFixture());
    vi.spyOn(linguaFrameApi, 'getDemoCompletionCertificate').mockResolvedValue(demoCompletionCertificateFixture());
    vi.spyOn(linguaFrameApi, 'getDemoAcceptanceGate').mockResolvedValue(demoAcceptanceGateFixture());
    vi.spyOn(linguaFrameApi, 'getDemoRunSnapshot').mockResolvedValue(demoRunSnapshotFixture());
    vi.spyOn(linguaFrameApi, 'getDemoPresenterPack').mockResolvedValue(demoPresenterPackFixture());
    vi.spyOn(linguaFrameApi, 'getDemoShareSheet').mockResolvedValue(demoShareSheetFixture());
    vi.spyOn(linguaFrameApi, 'listDemoRunProfiles').mockResolvedValue(demoRunProfileFixtures());
    vi.spyOn(linguaFrameApi, 'listNarrationDemoPresets').mockResolvedValue([narrationDemoPresetFixture()]);
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
          ownerQuota: {
            enabled: true,
            maxActiveJobs: 2,
            maxQueuedJobs: 1,
            dailyBudgetGuardEnabled: true,
            maxDailyCostUsd: 0.25
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
    expect(within(readiness).getByText('Worker topology')).toBeInTheDocument();
    expect(within(readiness).getByText('linguaframe.localization.jobs')).toBeInTheDocument();
    expect(within(readiness).getByText('linguaframe.jobs')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg route')).toBeInTheDocument();
    expect(within(readiness).getAllByText('linguaframe.localization.jobs / localization.queued').length)
      .toBeGreaterThanOrEqual(1);
    expect(within(readiness).getByText('OpenAI route')).toBeInTheDocument();
    expect(within(readiness).getByText('linguaframe.localization.openai.jobs / localization.openai'))
      .toBeInTheDocument();
    expect(within(readiness).getByText('COMBINED:ALL')).toBeInTheDocument();
    expect(within(readiness).getByText(/FFMPEG:WORKER_SMOKE,AUDIO_EXTRACTION/)).toBeInTheDocument();
    expect(within(readiness).getByText(/OPENAI:TRANSCRIPT_SUBTITLE_EXPORT,TARGET_SUBTITLE_EXPORT/))
      .toBeInTheDocument();
    expect(within(readiness).getByText(/LINGUAFRAME_WORKER_ROLE=OPENAI/)).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg audio')).toBeInTheDocument();
    expect(within(readiness).getByText('FFmpeg burn-in')).toBeInTheDocument();
    expect(within(readiness).getAllByText('Budget guard')).toHaveLength(2);
    expect(within(readiness).getByText('Enabled / estimates Enabled')).toBeInTheDocument();
    expect(within(readiness).getByText('$0.00000100')).toBeInTheDocument();
    expect(within(readiness).getByText('Daily budget')).toBeInTheDocument();
    expect(within(readiness).getByText('Enabled / $0.00000300')).toBeInTheDocument();
    expect(within(readiness).getByText('demo-owner')).toBeInTheDocument();
    expect(within(readiness).getAllByText('Owner quota').length).toBeGreaterThanOrEqual(1);
    expect(within(readiness).getByText('Enabled / active 2 / queued 1')).toBeInTheDocument();
    expect(within(readiness).getByText('Owner daily budget')).toBeInTheDocument();
    expect(within(readiness).getByText('Enabled / $0.25000000')).toBeInTheDocument();
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

  test('shows OpenAI readiness evidence and safe commands in the sidebar', async () => {
    render(<App />);

    const readiness = await screen.findByRole('region', { name: /openai readiness evidence/i });
    expect(within(readiness).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(readiness).getByText('READY_FOR_OPENAI_SMOKE')).toBeInTheDocument();
    expect(within(readiness).getByText('Translation')).toBeInTheDocument();
    expect(within(readiness).getByText('OpenAI live check')).toBeInTheDocument();
    expect(within(readiness).getByText('scripts/demo/openai-demo-preflight.sh')).toBeInTheDocument();
    expect(within(readiness).getByRole('button', { name: /download readiness evidence/i })).toBeInTheDocument();
    expect(readiness).not.toHaveTextContent('sk-test-secret');
    expect(readiness).not.toHaveTextContent('Bearer');
    expect(readiness).not.toHaveTextContent('raw transcript text');
  });

  test('downloads OpenAI readiness evidence markdown from the sidebar', async () => {
    const downloadSpy = vi.spyOn(linguaFrameApi, 'downloadOpenAiReadinessEvidenceMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame OpenAI Readiness Evidence'], { type: 'text/markdown' })
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /openai readiness evidence/i });
    await userEvent.click(within(readiness).getByRole('button', { name: /download readiness evidence/i }));

    expect(downloadSpy).toHaveBeenCalled();
    expect(within(readiness).getByText('OpenAI readiness evidence downloaded.')).toBeInTheDocument();
  });

  test('keeps upload controls usable when OpenAI readiness evidence fails', async () => {
    vi.spyOn(linguaFrameApi, 'getOpenAiReadinessEvidence').mockRejectedValue(
      new Error('OpenAI readiness unavailable')
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /openai readiness evidence/i });
    expect(within(readiness).getByText('OpenAI readiness unavailable: OpenAI readiness unavailable'))
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

  test('shows model usage ledger and download action', async () => {
    render(<App />);

    const ledger = await screen.findByRole('region', { name: /model usage ledger/i });
    expect(within(ledger).getByText('READY')).toBeInTheDocument();
    expect(within(ledger).getAllByText('$0.00020000').length).toBeGreaterThan(0);
    expect(within(ledger).getByText('2 calls')).toBeInTheDocument();
    expect(within(ledger).getByText('0 failed · 0.00%')).toBeInTheDocument();
    expect(within(ledger).getAllByText('TRANSLATION').length).toBeGreaterThan(0);
    expect(within(ledger).getAllByText('job-ledger').length).toBeGreaterThan(0);
    expect(within(ledger).getByRole('button', { name: /copy ledger/i })).toBeEnabled();
    expect(within(ledger).getByRole('button', { name: /download ledger/i })).toBeEnabled();
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

  test('shows private demo launch rehearsal checklist and report actions', async () => {
    render(<App />);

    const rehearsal = await screen.findByRole('region', {
      name: /private demo launch rehearsal/i
    });
    expect(within(rehearsal).getAllByText('ATTENTION').length).toBeGreaterThan(0);
    expect(within(rehearsal).getByText('openai-preflight')).toBeInTheDocument();
    expect(within(rehearsal).getByText('Deployment preflight')).toBeInTheDocument();
    expect(within(rehearsal).getByText('OpenAI provider preflight')).toBeInTheDocument();
    expect(within(rehearsal).getByText('Full Tears of Steel demo')).toBeInTheDocument();
    expect(within(rehearsal).getByText(/scripts\/demo\/private-demo-deploy-preflight\.sh/))
      .toBeInTheDocument();
    expect(within(rehearsal).getByText('/api/jobs/{jobId}/demo-presenter-pack'))
      .toBeInTheDocument();
    expect(within(rehearsal).getByRole('button', { name: /copy launch rehearsal notes/i }))
      .toBeEnabled();
    expect(within(rehearsal).getByRole('button', { name: /download launch rehearsal notes/i }))
      .toBeEnabled();
  });

  test('keeps upload controls usable when private demo launch rehearsal fails', async () => {
    vi.spyOn(linguaFrameApi, 'getPrivateDemoLaunchRehearsal').mockRejectedValue(
      new Error('Launch rehearsal unavailable')
    );

    render(<App />);

    const rehearsal = await screen.findByRole('region', {
      name: /private demo launch rehearsal/i
    });
    expect(within(rehearsal).getAllByText('Launch rehearsal unavailable').length).toBeGreaterThan(0);
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('shows private demo evidence gallery with recommended run and export actions', async () => {
    render(<App />);

    const gallery = await screen.findByRole('region', {
      name: /private demo evidence gallery/i
    });
    expect(within(gallery).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(gallery).getAllByText('job-gallery-best').length).toBeGreaterThan(0);
    expect(within(gallery).getByText('1 handoff ready')).toBeInTheDocument();
    expect(within(gallery).getAllByText(/tears-showcase/).length).toBeGreaterThan(0);
    expect(within(gallery).getAllByText('Quality 94').length).toBeGreaterThan(0);
    expect(within(gallery).getAllByText('$0.40').length).toBeGreaterThan(0);
    expect(within(gallery).getAllByText('1 provider cache hit').length).toBeGreaterThan(0);
    expect(within(gallery).getByRole('link', { name: /demo run package/i }))
      .toHaveAttribute('href', '/api/jobs/job-gallery-best/demo-run-package/download');
    expect(within(gallery).getByRole('link', { name: /ai audit package/i }))
      .toHaveAttribute('href', '/api/jobs/job-gallery-best/ai-audit-package/download');
    expect(within(gallery).getByRole('button', { name: /copy evidence gallery notes/i }))
      .toBeEnabled();
    expect(within(gallery).getByRole('button', { name: /download evidence gallery notes/i }))
      .toBeEnabled();
  });

  test('keeps upload controls usable when private demo evidence gallery fails', async () => {
    vi.spyOn(linguaFrameApi, 'getPrivateDemoEvidenceGallery').mockRejectedValue(
      new Error('Evidence gallery unavailable')
    );

    render(<App />);

    const gallery = await screen.findByRole('region', {
      name: /private demo evidence gallery/i
    });
    expect(within(gallery).getAllByText('Evidence gallery unavailable').length).toBeGreaterThan(0);
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
  });

  test('shows private demo run archive with recommended job and export actions', async () => {
    render(<App />);

    const archive = await screen.findByRole('region', {
      name: /private demo run archive/i
    });
    expect(within(archive).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(archive).getAllByText('job-gallery-best').length).toBeGreaterThan(0);
    expect(within(archive).getByText('tears-showcase')).toBeInTheDocument();
    expect(within(archive).getByText('2 completed')).toBeInTheDocument();
    expect(within(archive).getByText('1 handoff ready')).toBeInTheDocument();
    expect(within(archive).getByText('operations-report-export')).toBeInTheDocument();
    expect(within(archive).getByText('Quality 94')).toBeInTheDocument();
    expect(within(archive).getByText('$0.40')).toBeInTheDocument();
    expect(within(archive).getByRole('link', { name: /demo run package/i }))
      .toHaveAttribute('href', '/api/jobs/job-gallery-best/demo-run-package/download');
    expect(within(archive).getByRole('link', { name: /operations readiness/i }))
      .toHaveAttribute('href', '/api/operator/private-demo/operations');
    expect(within(archive).getByRole('button', { name: /copy archive notes/i }))
      .toBeEnabled();
    expect(within(archive).getByRole('button', { name: /download archive notes/i }))
      .toBeEnabled();
    expect(archive).not.toHaveTextContent('raw transcript text');
    expect(archive).not.toHaveTextContent('private-demo-token');
  });

  test('shows demo presentation cockpit with focus run and readiness checks', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoPresentationCockpit').mockResolvedValue(
      demoPresentationCockpitFixture({
        overallStatus: 'READY',
        phase: 'READY_TO_PRESENT',
        recommendedNextAction: 'Open the presenter pack.',
        selectedRun: {
          jobId: 'job-selected',
          videoId: 'video-selected',
          profileId: 'tears-showcase',
          status: 'COMPLETED',
          readiness: 'READY',
          acceptanceStatus: 'READY',
          attentionLevel: 'NONE',
          currentStage: 'COMPLETED',
          elapsedMs: 42000,
          nextAction: 'Use the demo presenter pack.'
        }
      })
    );

    render(<App />);

    const panel = await screen.findByRole('region', {
      name: /demo presentation cockpit/i
    });
    expect(within(panel).getByText('READY_TO_PRESENT')).toBeInTheDocument();
    expect(within(panel).getByText('Open the presenter pack.')).toBeInTheDocument();
    expect(within(panel).getAllByText(/job-selected/).length).toBeGreaterThan(0);
    expect(within(panel).getByRole('link', { name: /Acceptance gate/i })).toHaveAttribute(
      'href',
      '/api/jobs/job-selected/demo-acceptance-gate'
    );
    expect(within(panel).getByRole('link', { name: /Presenter pack/i })).toHaveAttribute(
      'href',
      '/api/jobs/job-selected/demo-presenter-pack'
    );
  });

  test('shows demo session command center with phase gates and export actions', async () => {
    const downloadSpy = vi.spyOn(linguaFrameApi, 'downloadDemoSessionCommandCenterMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame Demo Session Command Center'], { type: 'text/markdown' })
    );
    const packageSpy = vi.spyOn(linguaFrameApi, 'downloadDemoSessionEvidencePackageZip').mockResolvedValue(
      new Blob(['zip-bytes'], { type: 'application/zip' })
    );

    render(<App />);

    const panel = await screen.findByRole('region', {
      name: /demo session command center/i
    });
    expect(within(panel).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(panel).getByText('READY_TO_PRESENT')).toBeInTheDocument();
    expect(within(panel).getAllByText(/job-session/).length).toBeGreaterThan(0);
    expect(within(panel).getAllByText('LINGUAFRAME_DEMO_JOB_ID=job-session scripts/demo/demo-session-command-center.sh').length)
      .toBeGreaterThan(0);
    expect(within(panel).getByRole('link', { name: /Command center Markdown/i })).toHaveAttribute(
      'href',
      '/api/operator/demo-session-command-center/markdown/download'
    );
    await userEvent.click(within(panel).getByRole('button', { name: /download command center/i }));
    expect(downloadSpy).toHaveBeenCalledWith('job-session');
    await userEvent.click(within(panel).getByRole('button', { name: /download session package/i }));
    expect(packageSpy).toHaveBeenCalledWith('job-session');
    expect(panel).not.toHaveTextContent('raw transcript text');
    expect(panel).not.toHaveTextContent('private-demo-token');
  });

  test('keeps upload controls usable when private demo run archive fails', async () => {
    vi.spyOn(linguaFrameApi, 'getPrivateDemoRunArchive').mockRejectedValue(
      new Error('Run archive unavailable')
    );

    render(<App />);

    const archive = await screen.findByRole('region', {
      name: /private demo run archive/i
    });
    expect(within(archive).getAllByText('Run archive unavailable').length).toBeGreaterThan(0);
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
        mode: 'OPEN',
        ownerId: 'demo-owner',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );

    render(<App />);

    expect(await screen.findByText('Open demo')).toBeInTheDocument();
    expect(await screen.findByText('Owner: demo-owner')).toBeInTheDocument();
    expect(screen.getByText('Scope: CONFIGURED_DEMO_OWNER')).toBeInTheDocument();
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

  test('creates and clears a local account auth session', async () => {
    const getAuthSession = vi.spyOn(linguaFrameApi, 'getAuthSession').mockResolvedValue(
      authSessionStatusFixture({
        enabled: true,
        configured: true,
        authenticated: false,
        authMode: 'LOCAL_AUTH_REQUIRED',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );
    const loginAuthSession = vi.spyOn(linguaFrameApi, 'loginAuthSession').mockResolvedValue(
      authLoginResponseFixture({
        session: authSessionStatusFixture({
          enabled: true,
          configured: true,
          authenticated: true,
          ownerId: 'owner-alpha',
          username: 'owner',
          authMode: 'LOCAL_AUTH_ACTIVE',
          ownershipScope: 'LOCAL_AUTH_OWNER'
        })
      })
    );
    vi.spyOn(linguaFrameApi, 'logoutAuthSession').mockResolvedValue(
      authSessionStatusFixture({
        enabled: true,
        configured: true,
        authenticated: false,
        authMode: 'LOCAL_AUTH_REQUIRED',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );

    render(<App />);

    expect(await screen.findByText('Local account required')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/account username/i));
    await userEvent.type(screen.getByLabelText(/account username/i), 'owner');
    await userEvent.type(screen.getByLabelText(/account password/i), 'owner-password');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(loginAuthSession).toHaveBeenCalledWith('owner', 'owner-password');
    await waitFor(() => {
      expect(screen.getAllByText('Local account active').length).toBeGreaterThan(0);
    });
    expect(screen.getByText('Account: owner')).toBeInTheDocument();
    expect(screen.getByText('Auth owner: owner-alpha')).toBeInTheDocument();
    expect(screen.getByText('Auth scope: LOCAL_AUTH_OWNER')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));

    expect(await screen.findByText('Local account signed out.')).toBeInTheDocument();
    expect(screen.getByLabelText(/account username/i)).toHaveValue('owner');
    expect(screen.getByLabelText(/account password/i)).toHaveValue('');
    expect(getAuthSession).toHaveBeenCalled();
  });

  test('refreshes owner workspace history after local account login and logout', async () => {
    vi.spyOn(linguaFrameApi, 'getAuthSession').mockResolvedValue(
      authSessionStatusFixture({
        enabled: true,
        configured: true,
        authenticated: false,
        authMode: 'LOCAL_AUTH_REQUIRED',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );
    const listJobs = vi.spyOn(linguaFrameApi, 'listJobs')
      .mockResolvedValueOnce(jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'demo-owner-job',
            videoId: 'demo-owner-video',
            filename: 'demo-owner.mp4'
          })
        ],
        total: 1
      }))
      .mockResolvedValueOnce(jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'local-owner-job',
            videoId: 'local-owner-video',
            filename: 'local-owner.mp4'
          })
        ],
        total: 1
      }))
      .mockResolvedValueOnce(jobListFixture({
        jobs: [
          jobSummaryFixture({
            jobId: 'demo-owner-job',
            videoId: 'demo-owner-video',
            filename: 'demo-owner.mp4'
          })
        ],
        total: 1
      }));
    vi.spyOn(linguaFrameApi, 'loginAuthSession').mockResolvedValue(
      authLoginResponseFixture({
        session: authSessionStatusFixture({
          enabled: true,
          configured: true,
          authenticated: true,
          ownerId: 'owner-alpha',
          username: 'owner',
          authMode: 'LOCAL_AUTH_ACTIVE',
          ownershipScope: 'LOCAL_AUTH_OWNER'
        })
      })
    );
    vi.spyOn(linguaFrameApi, 'logoutAuthSession').mockResolvedValue(
      authSessionStatusFixture({
        enabled: true,
        configured: true,
        authenticated: false,
        authMode: 'LOCAL_AUTH_REQUIRED',
        ownershipScope: 'CONFIGURED_DEMO_OWNER'
      })
    );

    render(<App />);

    expect(await screen.findByText('demo-owner.mp4')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText(/account username/i));
    await userEvent.type(screen.getByLabelText(/account username/i), 'owner');
    await userEvent.type(screen.getByLabelText(/account password/i), 'owner-password');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('local-owner.mp4')).toBeInTheDocument();
    expect(screen.queryByText('demo-owner.mp4')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /sign out/i }));

    expect(await screen.findByText('demo-owner.mp4')).toBeInTheDocument();
    expect(listJobs).toHaveBeenCalledTimes(3);
  });

  test('shows local account login failures without blocking upload controls', async () => {
    vi.spyOn(linguaFrameApi, 'getAuthSession').mockResolvedValue(
      authSessionStatusFixture({
        enabled: true,
        configured: true,
        authenticated: false,
        authMode: 'LOCAL_AUTH_REQUIRED'
      })
    );
    vi.spyOn(linguaFrameApi, 'loginAuthSession').mockRejectedValue(
      new Error('Local account credentials rejected')
    );

    render(<App />);

    await userEvent.clear(await screen.findByLabelText(/account username/i));
    await userEvent.type(screen.getByLabelText(/account username/i), 'owner');
    await userEvent.type(screen.getByLabelText(/account password/i), 'wrong-password');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Local account credentials rejected')).toBeInTheDocument();
    expect(screen.getByLabelText(/video file/i)).toBeInTheDocument();
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
    const getOwnerQuotaPreflight = vi.spyOn(linguaFrameApi, 'getOwnerQuotaPreflight').mockResolvedValue(
      ownerQuotaPreflightFixture()
    );
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
    expect(getOwnerQuotaPreflight).toHaveBeenCalledTimes(3);
  });

  test('shows owner quota preflight and blocks upload when quota is exhausted', async () => {
    vi.spyOn(linguaFrameApi, 'getOwnerQuotaPreflight').mockResolvedValue(
      ownerQuotaPreflightFixture({
        enabled: true,
        allowed: false,
        activeJobs: 2,
        queuedJobs: 1,
        dailyEstimatedCostUsd: 0.25,
        limits: [
          { name: 'activeJobs', enabled: true, limit: 2, current: 2 },
          { name: 'queuedJobs', enabled: true, limit: 1, current: 1 },
          { name: 'dailyCostUsd', enabled: true, limit: 0.25, current: 0.25 }
        ],
        blockingReasons: ['Owner active job limit reached: 2 / 2']
      })
    );
    const uploadMedia = vi.spyOn(linguaFrameApi, 'uploadMedia').mockResolvedValue(mediaUploadFixture());

    render(<App />);

    const quota = await screen.findByRole('region', { name: /owner quota/i });
    expect(within(quota).getByText('Blocked')).toBeInTheDocument();
    expect(within(quota).getByText('demo-owner')).toBeInTheDocument();
    expect(within(quota).getByText('2 / 2')).toBeInTheDocument();
    expect(within(quota).getByText('$0.25000000 / $0.25000000')).toBeInTheDocument();
    expect(within(quota).getByText('Owner active job limit reached: 2 / 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled();
    expect(uploadMedia).not.toHaveBeenCalled();
  });

  test('shows demo upload readiness before file selection', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoUploadReadiness').mockResolvedValue(
      demoUploadReadinessFixture({
        overallStatus: 'READY',
        checks: [
          {
            id: 'owner-session',
            label: 'Owner session',
            status: 'READY',
            detail: 'Demo access gate is open.',
            nextAction: 'No owner-session action required.',
            blocking: false
          }
        ],
        requiredActions: ['Upload can start after file validation passes.']
      })
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /upload readiness/i });
    expect(within(readiness).getAllByText('READY').length).toBeGreaterThanOrEqual(1);
    expect(within(readiness).getByText('demo-owner')).toBeInTheDocument();
    expect(within(readiness).getByText('Owner session')).toBeInTheDocument();
    expect(within(readiness).getByText('Upload can start after file validation passes.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /upload/i })).toBeEnabled();
  });

  test('shows demo sample media catalog without exposing local paths', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoSampleMediaCatalog').mockResolvedValue(
      demoSampleMediaCatalogFixture({
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
        ]
      })
    );

    render(<App />);

    const panel = await screen.findByRole('region', { name: /demo sample media/i });
    expect(within(panel).getByText('READY')).toBeInTheDocument();
    expect(within(panel).getByText('tears-of-steel-casting')).toBeInTheDocument();
    expect(within(panel).getAllByText(/300 seconds/i).length).toBeGreaterThanOrEqual(1);
    expect(within(panel).getByText('Tears of Steel casting clip')).toBeInTheDocument();
    expect(within(panel).getByRole('link', { name: /Blender Studio/i })).toHaveAttribute(
      'href',
      'https://studio.blender.org/films/tears-of-steel/'
    );
    expect(within(panel).getByText('LINGUAFRAME_TEARS_SAMPLE_PATH')).toBeInTheDocument();
    expect(within(panel).getByText('tos_casting-720p.mp4')).toBeInTheDocument();
    expect(within(panel).getByText('scripts/demo/docker-e2e-tears-of-steel-full.sh')).toBeInTheDocument();
    expect(panel).not.toHaveTextContent('/Users/');
    expect(panel).not.toHaveTextContent('Downloads');
  });

  test('shows demo run launcher with commands and expected evidence', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoRunLauncher').mockResolvedValue(
      demoRunLauncherFixture({
        gates: [
          {
            id: 'sample-media',
            label: 'Sample media',
            status: 'READY',
            detail: 'Recommended Tears sample is configured as tos_casting-720p.mp4.',
            nextAction: 'No sample-media action required.',
            blocking: false
          },
          {
            id: 'paid-provider-check',
            label: 'Paid provider check',
            status: 'ATTENTION',
            detail: 'OpenAI provider mode is enabled, but the live OpenAI connectivity check is skipped.',
            nextAction: 'Run the OpenAI preflight before provider-backed uploads.',
            blocking: false
          }
        ]
      })
    );

    render(<App />);

    const panel = await screen.findByRole('region', { name: /demo run launcher/i });
    expect(within(panel).getAllByText('ATTENTION').length).toBeGreaterThanOrEqual(1);
    expect(within(panel).getByText('tears-of-steel-casting')).toBeInTheDocument();
    expect(within(panel).getByText('tears-showcase')).toBeInTheDocument();
    expect(within(panel).getAllByText('scripts/demo/docker-e2e-tears-of-steel-full.sh').length).toBeGreaterThanOrEqual(1);
    expect(within(panel).getByText('scripts/demo/openai-demo-preflight.sh')).toBeInTheDocument();
    expect(within(panel).getByText('Paid provider check')).toBeInTheDocument();
    expect(within(panel).getByText('/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json')).toBeInTheDocument();
    expect(within(panel).getByText('/tmp/linguaframe-demo/full-tears/demo-run-snapshot.zip')).toBeInTheDocument();
    expect(panel).not.toHaveTextContent('/Users/');
    expect(panel).not.toHaveTextContent('sk-test');
    expect(panel).not.toHaveTextContent('provider payload');
  });

  test('keeps upload enabled when readiness needs attention but is not blocked', async () => {
    vi.spyOn(linguaFrameApi, 'getDemoUploadReadiness').mockResolvedValue(
      demoUploadReadinessFixture({
        overallStatus: 'ATTENTION',
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
        requiredActions: ['Review attention checks before paid or full-video upload.']
      })
    );

    render(<App />);

    const readiness = await screen.findByRole('region', { name: /upload readiness/i });
    expect(within(readiness).getAllByText('ATTENTION').length).toBeGreaterThanOrEqual(1);
    expect(within(readiness).getByText('Paid provider check')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /upload/i })).toBeEnabled();
  });

  test('blocks upload and refreshes readiness when demo profile changes', async () => {
    const getDemoUploadReadiness = vi.spyOn(linguaFrameApi, 'getDemoUploadReadiness')
      .mockResolvedValueOnce(demoUploadReadinessFixture())
      .mockResolvedValueOnce(
        demoUploadReadinessFixture({
          overallStatus: 'BLOCKED',
          demoProfileId: 'tears-showcase',
          checks: [
            {
              id: 'demo-profile',
              label: 'Demo profile',
              status: 'BLOCKED',
              detail: 'Unknown demo profile id: tears-showcase.',
              nextAction: 'Choose one of the built-in demo profiles before upload.',
              blocking: true
            }
          ],
          requiredActions: ['Resolve blocking upload readiness checks before uploading media.']
        })
      );

    render(<App />);

    await screen.findByRole('region', { name: /upload readiness/i });
    await userEvent.selectOptions(screen.getByLabelText(/demo profile/i), 'tears-showcase');

    await waitFor(() => expect(getDemoUploadReadiness).toHaveBeenCalledWith('tears-showcase'));
    await waitFor(() => {
      const readiness = screen.getByRole('region', { name: /upload readiness/i });
      expect(within(readiness).getAllByText('BLOCKED').length).toBeGreaterThanOrEqual(1);
    });
    const readiness = screen.getByRole('region', { name: /upload readiness/i });
    expect(within(readiness).getByText('Unknown demo profile id: tears-showcase.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled();
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
    vi.spyOn(linguaFrameApi, 'getOpenAiSmokeProof').mockResolvedValue(openAiSmokeProofFixture());
    vi.spyOn(linguaFrameApi, 'getDemoReviewerWorkspace').mockResolvedValue(demoReviewerWorkspaceFixture());
    vi.spyOn(linguaFrameApi, 'getDemoHandoffPortal').mockResolvedValue(demoHandoffPortalFixture());
    const downloadSmokeProof = vi.spyOn(linguaFrameApi, 'downloadOpenAiSmokeProofMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame OpenAI Smoke Proof'], { type: 'text/markdown' })
    );
    const downloadReviewerMarkdown = vi.spyOn(linguaFrameApi, 'downloadDemoReviewerWorkspaceMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame Demo Reviewer Workspace'], { type: 'text/markdown' })
    );
    const downloadReviewerZip = vi.spyOn(linguaFrameApi, 'downloadDemoReviewerWorkspaceZip').mockResolvedValue(
      new Blob(['zip'], { type: 'application/zip' })
    );
    const downloadPortalMarkdown = vi.spyOn(linguaFrameApi, 'downloadDemoHandoffPortalMarkdown').mockResolvedValue(
      new Blob(['# LinguaFrame Demo Handoff Portal'], { type: 'text/markdown' })
    );
    const downloadPortalZip = vi.spyOn(linguaFrameApi, 'downloadDemoHandoffPortalZip').mockResolvedValue(
      new Blob(['portal-zip'], { type: 'application/zip' })
    );

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
    const smokeProof = screen.getByRole('region', { name: /openai smoke proof/i });
    expect(within(smokeProof).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(smokeProof).getByText('OPENAI_SMOKE_PROVEN')).toBeInTheDocument();
    expect(within(smokeProof).getByText(/OpenAI transcription call/)).toBeInTheDocument();
    expect(within(smokeProof).getByText('TRANSCRIPTION')).toBeInTheDocument();
    expect(within(smokeProof).getByText('TARGET_SUBTITLE_SRT')).toBeInTheDocument();
    expect(within(smokeProof).queryByText('raw transcript text')).not.toBeInTheDocument();
    await userEvent.click(within(smokeProof).getByRole('button', { name: /download smoke proof/i }));
    await waitFor(() => expect(downloadSmokeProof).toHaveBeenCalledWith('job-1'));
    const reviewerWorkspace = screen.getByRole('region', { name: /demo reviewer workspace/i });
    expect(within(reviewerWorkspace).getByText('REVIEW_PACKAGE_READY')).toBeInTheDocument();
    expect(within(reviewerWorkspace).getByText(/Job completed/)).toBeInTheDocument();
    expect(within(reviewerWorkspace).getByText('Demo run package')).toBeInTheDocument();
    expect(within(reviewerWorkspace).queryByText('raw transcript text')).not.toBeInTheDocument();
    expect(within(reviewerWorkspace).queryByText('provider request payload')).not.toBeInTheDocument();
    await userEvent.click(within(reviewerWorkspace).getByRole('button', { name: /download reviewer markdown/i }));
    await waitFor(() => expect(downloadReviewerMarkdown).toHaveBeenCalledWith('job-1'));
    await userEvent.click(within(reviewerWorkspace).getByRole('button', { name: /download reviewer zip/i }));
    await waitFor(() => expect(downloadReviewerZip).toHaveBeenCalledWith('job-1'));
    const handoffPortal = screen.getByRole('region', { name: /demo handoff portal/i });
    expect(within(handoffPortal).getByText('HANDOFF_PORTAL_READY')).toBeInTheDocument();
    expect(within(handoffPortal).getByText(/Static handoff portal ZIP/)).toBeInTheDocument();
    expect(within(handoffPortal).getByText('index.html')).toBeInTheDocument();
    expect(within(handoffPortal).getByText('Demo reviewer workspace')).toBeInTheDocument();
    expect(within(handoffPortal).queryByText('raw transcript text')).not.toBeInTheDocument();
    expect(within(handoffPortal).queryByText('provider request payload')).not.toBeInTheDocument();
    await userEvent.click(within(handoffPortal).getByRole('button', { name: /download portal markdown/i }));
    await waitFor(() => expect(downloadPortalMarkdown).toHaveBeenCalledWith('job-1'));
    await userEvent.click(within(handoffPortal).getByRole('button', { name: /download portal zip/i }));
    await waitFor(() => expect(downloadPortalZip).toHaveBeenCalledWith('job-1'));
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
      },
      {
        artifactId: 'artifact-dubbed-video',
        jobId: 'artifact-job',
        type: 'DUBBED_VIDEO',
        filename: 'dubbed-video.mp4',
        contentType: 'video/mp4',
        sizeBytes: 84000,
        contentSha256: '789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456',
        cacheHit: false,
        sourceArtifactId: null,
        createdAt: '2026-06-26T10:00:08Z'
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

    const workflow = screen.getByRole('region', { name: /reviewed subtitle workflow/i });
    expect(within(workflow).getByText(/PUBLISH_READY/)).toBeInTheDocument();
    expect(within(workflow).getAllByText('Publish reviewed subtitles.').length).toBeGreaterThan(0);
    expect(within(workflow).getByText('Reviewed subtitle artifacts')).toBeInTheDocument();
    expect(within(workflow).getByRole('link', { name: /Publish reviewed subtitles/i })).toHaveAttribute(
      'href',
      '/api/jobs/job-1/subtitle-draft/publish'
    );
    expect(workflow).not.toHaveTextContent('First source line');
    expect(workflow).not.toHaveTextContent('修正后的第二行字幕');

    const delivery = screen.getByRole('region', { name: /result delivery/i });
    expect(within(delivery).getByText('4 generated')).toBeInTheDocument();
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
    expect(within(mediaDelivery).getByText('Dubbed video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByLabelText(/dubbing audio player/i)).toHaveAttribute(
      'src',
      '/api/jobs/artifact-job/artifacts/artifact-audio/download'
    );
    expect(within(mediaDelivery).getByLabelText(/generated burned video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/artifact-job/artifacts/artifact-video/download'
    );
    expect(within(mediaDelivery).getByLabelText(/dubbed video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/artifact-job/artifacts/artifact-dubbed-video/download'
    );
    expect(within(mediaDelivery).getByRole('link', { name: /download dubbing audio/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/artifact-audio/download'
    );
    expect(within(mediaDelivery).getByRole('link', { name: /download dubbed video/i })).toHaveAttribute(
      'href',
      '/api/jobs/artifact-job/artifacts/artifact-dubbed-video/download'
    );
    expect(within(mediaDelivery).getByText('audio/mpeg')).toBeInTheDocument();
    expect(within(mediaDelivery).getAllByText('video/mp4').length).toBeGreaterThanOrEqual(2);
    expect(within(mediaDelivery).getByText('4.10 KB')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('82.03 KB')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('abcdef012345')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('789abcdef012')).toBeInTheDocument();
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
        artifactId: 'dubbed-video',
        jobId: 'reviewed-media-job',
        type: 'DUBBED_VIDEO',
        filename: 'dubbed-video.mp4',
        contentType: 'video/mp4',
        contentSha256: '789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456',
        cacheHit: false,
        sourceArtifactId: null
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
      }),
      artifactFixture({
        artifactId: 'narrated-video',
        jobId: 'reviewed-media-job',
        type: 'NARRATED_VIDEO',
        filename: 'narrated-video.mp4',
        contentType: 'video/mp4',
        contentSha256: '456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123',
        cacheHit: false,
        sourceArtifactId: null
      })
    ]);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'reviewed-media-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const mediaDelivery = await screen.findByRole('region', { name: /media delivery/i });
    expect(within(mediaDelivery).getByText('Generated burned video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('Dubbed video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('Narrated video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('Reviewed burned video')).toBeInTheDocument();
    expect(within(mediaDelivery).getByLabelText(/generated burned video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/reviewed-media-job/artifacts/generated-video/download'
    );
    expect(within(mediaDelivery).getByLabelText(/dubbed video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/reviewed-media-job/artifacts/dubbed-video/download'
    );
    expect(within(mediaDelivery).getByLabelText(/narrated video player/i)).toHaveAttribute(
      'src',
      '/api/jobs/reviewed-media-job/artifacts/narrated-video/download'
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
    expect(within(mediaDelivery).getByText('789abcdef012')).toBeInTheDocument();
    expect(within(mediaDelivery).getByText('1234567890ab')).toBeInTheDocument();
  });

  test('shows timed narration audio bed and ducked narrated video status', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-mix-job', videoId: 'narration-mix-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    const listArtifacts = vi.spyOn(linguaFrameApi, 'listArtifacts')
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        artifactFixture({
          artifactId: 'narration-audio-artifact',
          jobId: 'narration-mix-job',
          type: 'NARRATION_AUDIO',
          filename: 'narration-audio.mp3',
          contentType: 'audio/mpeg'
        })
      ])
      .mockResolvedValueOnce([
        artifactFixture({
          artifactId: 'narration-audio-artifact',
          jobId: 'narration-mix-job',
          type: 'NARRATION_AUDIO',
          filename: 'narration-audio.mp3',
          contentType: 'audio/mpeg'
        }),
        artifactFixture({
          artifactId: 'narrated-video-artifact',
          jobId: 'narration-mix-job',
          type: 'NARRATED_VIDEO',
          filename: 'narrated-video.mp4',
          contentType: 'video/mp4'
        })
      ]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture());
    const updateNarrationMixSettings = vi.spyOn(linguaFrameApi, 'updateNarrationMixSettings')
      .mockResolvedValue(narrationWorkspaceFixture({
        mixSettings: {
          duckingVolume: 0.125,
          narrationVolume: 1.75,
          fadeDurationMs: 400,
          updatedAt: '2026-06-29T10:30:00Z'
        }
      }));
    const getNarrationEvidence = vi.spyOn(linguaFrameApi, 'getNarrationEvidence')
      .mockResolvedValueOnce(narrationEvidenceFixture({ narrationAudioReady: false, audioArtifactCount: 0 }))
      .mockResolvedValueOnce(narrationEvidenceFixture({
        duckingVolume: 0.125,
        narrationVolume: 1.75,
        fadeDurationMs: 400,
        mixSettingsSource: 'SAVED'
      }))
      .mockResolvedValueOnce(narrationEvidenceFixture())
      .mockResolvedValueOnce(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'generateNarrationAudio').mockResolvedValue(narrationGenerationFixture());
    vi.spyOn(linguaFrameApi, 'generateNarratedVideo').mockResolvedValue(narratedVideoGenerationFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-mix-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    expect(within(narrationPanel).getByText('Timeline workbench')).toBeInTheDocument();
    expect(within(narrationPanel).getByText((_content, element) => element?.textContent === '55.5 s span · 28.5 s covered')).toBeInTheDocument();
    expect(
      within(narrationPanel).getByText(
        (_content, element) => element?.tagName.toLowerCase() === 'span' && element.textContent === '1 gap · 27 s'
      )
    ).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText('Timeline segment 1: 15 s to 28 s, READY')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText('Timeline segment 2: 55 s to 70.5 s, READY')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Selected segment')).toBeInTheDocument();
    expect(within(narrationPanel).getByText((_content, element) => element?.textContent === '15 s - 28 s')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Voice presets')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Default voice: verse')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i })).toHaveValue('alloy');
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 2 voice/i })).toHaveValue('verse');
    expect(within(narrationPanel).getByText('Explicit preset: alloy')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('PRESET:alloy')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Ducked original audio')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('0.35')).toBeInTheDocument();
    expect(within(narrationPanel).getByText((_content, element) => element?.textContent === '2 windows')).toBeInTheDocument();

    await userEvent.clear(within(narrationPanel).getByLabelText(/ducking volume/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/ducking volume/i), '0.125');
    await userEvent.clear(within(narrationPanel).getByLabelText(/narration volume/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/narration volume/i), '1.75');
    await userEvent.clear(within(narrationPanel).getByLabelText(/fade duration ms/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/fade duration ms/i), '400');
    await userEvent.click(within(narrationPanel).getByRole('button', { name: /save mix settings/i }));

    expect(updateNarrationMixSettings).toHaveBeenCalledWith('narration-mix-job', {
      duckingVolume: 0.125,
      narrationVolume: 1.75,
      fadeDurationMs: 400
    });
    expect(await within(narrationPanel).findByText('Mix settings saved.')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Saved')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('1.75')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('400 ms')).toBeInTheDocument();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /generate narration audio/i }));
    expect(await within(narrationPanel).findByText('Generated narration-audio.mp3 as TIMED_AUDIO_BED.')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Timed audio bed')).toBeInTheDocument();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /generate narrated video/i }));
    expect(await within(narrationPanel).findByText('Generated narrated-video.mp4 from BURNED_VIDEO with DUCKED_ORIGINAL_AUDIO (ducking 0.35, narration 1, fade 250 ms).')).toBeInTheDocument();
    expect(listArtifacts).toHaveBeenCalledTimes(3);
    expect(getNarrationEvidence).toHaveBeenCalledTimes(4);
  });

  test('previews selected narration segment TTS without saving the workspace', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-tts-preview-job', videoId: 'narration-tts-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-tts-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment')
      .mockResolvedValue(new Blob(['mp3-preview'], { type: 'audio/mpeg' }));
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:narration-preview');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-tts-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const ttsPreviewPanel = within(narrationPanel).getByRole('region', { name: /narration tts preview/i });

    expect(within(ttsPreviewPanel).getByText('Narration TTS preview')).toBeInTheDocument();
    expect(within(ttsPreviewPanel).getByText(/may consume credits/i)).toBeInTheDocument();

    await userEvent.click(within(ttsPreviewPanel).getByRole('button', { name: /preview selected tts/i }));

    expect(previewNarrationSegment).toHaveBeenCalledWith('narration-tts-preview-job', {
      text: 'Explain the first scene.',
      voice: 'alloy'
    });
    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(await within(ttsPreviewPanel).findByText('Preview ready for narration 1.')).toBeInTheDocument();
    expect(within(ttsPreviewPanel).getByLabelText('Narration TTS preview player')).toHaveAttribute('src', 'blob:narration-preview');
    expect(within(ttsPreviewPanel).getByText((_content, element) =>
      element?.textContent === 'Narration 1 · Explicit preset: alloy'
    )).toBeInTheDocument();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /delete row/i }));

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:narration-preview');
  });

  test('previews unsaved narration text edits without saving the workspace', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-unsaved-tts-job', videoId: 'narration-unsaved-tts-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-unsaved-tts-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment')
      .mockResolvedValue(new Blob(['mp3-preview'], { type: 'audio/mpeg' }));
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:unsaved-narration-preview');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-unsaved-tts-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const ttsPreviewPanel = within(narrationPanel).getByRole('region', { name: /narration tts preview/i });
    const textArea = within(narrationPanel).getByLabelText(/segment text/i);

    await userEvent.clear(textArea);
    await userEvent.type(textArea, 'Unsaved local narration preview.');
    await userEvent.click(within(ttsPreviewPanel).getByRole('button', { name: /preview selected tts/i }));

    expect(previewNarrationSegment).toHaveBeenCalledWith('narration-unsaved-tts-job', {
      text: 'Unsaved local narration preview.',
      voice: 'alloy'
    });
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('disables narration TTS preview for blank selected text without calling the API', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-blank-tts-job', videoId: 'narration-blank-tts-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-blank-tts-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-blank-tts-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const ttsPreviewPanel = within(narrationPanel).getByRole('region', { name: /narration tts preview/i });

    await userEvent.clear(within(narrationPanel).getByLabelText(/segment text/i));

    expect(within(ttsPreviewPanel).getByText('Selected narration text is required.')).toBeInTheDocument();
    expect(within(ttsPreviewPanel).getByRole('button', { name: /preview selected tts/i })).toBeDisabled();
    expect(previewNarrationSegment).not.toHaveBeenCalled();
  });

  test('keeps narration save and generation usable when TTS preview is rejected', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-rejected-tts-job', videoId: 'narration-rejected-tts-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-rejected-tts-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    vi.spyOn(linguaFrameApi, 'previewNarrationSegment').mockRejectedValue(new Error('TTS provider rejected preview.'));
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-rejected-tts-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const ttsPreviewPanel = within(narrationPanel).getByRole('region', { name: /narration tts preview/i });

    await userEvent.click(within(ttsPreviewPanel).getByRole('button', { name: /preview selected tts/i }));

    expect(await within(ttsPreviewPanel).findByText('TTS provider rejected preview.')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('button', { name: /save narration/i })).toBeEnabled();
    expect(within(narrationPanel).getByRole('button', { name: /generate narration audio/i })).toBeEnabled();
  });

  test('renders voice audition presets with default sample text', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-voice-audition-job', videoId: 'narration-voice-audition-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-voice-audition-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-voice-audition-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const auditionPanel = within(narrationPanel).getByRole('region', { name: /voice audition/i });

    expect(within(auditionPanel).getByText('Voice audition')).toBeInTheDocument();
    expect(within(auditionPanel).getByText(/may consume credits/i)).toBeInTheDocument();
    expect(within(auditionPanel).getByRole('combobox', { name: /audition voice/i })).toHaveValue('verse');
    expect(within(auditionPanel).getByRole('option', { name: 'Alloy' })).toBeInTheDocument();
    expect(within(auditionPanel).getByRole('option', { name: 'Verse' })).toBeInTheDocument();
    expect(within(auditionPanel).getByLabelText(/audition text/i)).toHaveValue('This is a LinguaFrame narration voice preview.');
  });

  test('previews a selected voice audition without saving narration rows', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-voice-preview-job', videoId: 'narration-voice-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-voice-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment')
      .mockResolvedValue(new Blob(['voice-preview'], { type: 'audio/mpeg' }));
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:voice-audition-preview');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-voice-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const auditionPanel = within(narrationPanel).getByRole('region', { name: /voice audition/i });

    await userEvent.selectOptions(within(auditionPanel).getByRole('combobox', { name: /audition voice/i }), 'alloy');
    await userEvent.clear(within(auditionPanel).getByLabelText(/audition text/i));
    await userEvent.type(within(auditionPanel).getByLabelText(/audition text/i), 'A custom voice audition line.');
    await userEvent.click(within(auditionPanel).getByRole('button', { name: /preview voice/i }));

    expect(previewNarrationSegment).toHaveBeenCalledWith('narration-voice-preview-job', {
      text: 'A custom voice audition line.',
      voice: 'alloy'
    });
    expect(await within(auditionPanel).findByText('Voice preview ready for alloy.')).toBeInTheDocument();
    expect(within(auditionPanel).getByLabelText('Voice audition preview player')).toHaveAttribute('src', 'blob:voice-audition-preview');
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('keeps narration actions usable when voice audition preview is rejected', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-voice-preview-error-job', videoId: 'narration-voice-preview-error-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-voice-preview-error-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    vi.spyOn(linguaFrameApi, 'previewNarrationSegment').mockRejectedValue(new Error('Voice preview rejected.'));
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-voice-preview-error-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const auditionPanel = within(narrationPanel).getByRole('region', { name: /voice audition/i });

    await userEvent.click(within(auditionPanel).getByRole('button', { name: /preview voice/i }));

    expect(await within(auditionPanel).findByText('Voice preview rejected.')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('button', { name: /save narration/i })).toBeEnabled();
    expect(within(narrationPanel).getByRole('button', { name: /generate narration audio/i })).toBeEnabled();
    expect(within(narrationPanel).getByRole('button', { name: /refresh evidence/i })).toBeEnabled();
  });

  test('applies a voice audition preset to the selected local draft row only', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-voice-apply-selected-job', videoId: 'narration-voice-apply-selected-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-voice-apply-selected-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-voice-apply-selected-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const auditionPanel = within(narrationPanel).getByRole('region', { name: /voice audition/i });

    await userEvent.click(within(narrationPanel).getByRole('button', { name: '2' }));
    await userEvent.selectOptions(within(auditionPanel).getByRole('combobox', { name: /audition voice/i }), 'alloy');
    await userEvent.click(within(auditionPanel).getByRole('button', { name: /apply to selected row/i }));

    expect(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i })).toHaveValue('alloy');
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 2 voice/i })).toHaveValue('alloy');
    expect(within(auditionPanel).getByText('Applied alloy to narration 2.')).toBeInTheDocument();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('applies a voice audition preset to all local draft rows without provider or save calls', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-voice-apply-all-job', videoId: 'narration-voice-apply-all-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-voice-apply-all-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');
    const generateNarratedVideo = vi.spyOn(linguaFrameApi, 'generateNarratedVideo');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-voice-apply-all-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const auditionPanel = within(narrationPanel).getByRole('region', { name: /voice audition/i });

    await userEvent.selectOptions(within(auditionPanel).getByRole('combobox', { name: /audition voice/i }), 'alloy');
    await userEvent.click(within(auditionPanel).getByRole('button', { name: /apply to all rows/i }));

    expect(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i })).toHaveValue('alloy');
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 2 voice/i })).toHaveValue('alloy');
    expect(within(auditionPanel).getByText('Applied alloy to 2 narration rows.')).toBeInTheDocument();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(previewNarrationSegment).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
    expect(generateNarratedVideo).not.toHaveBeenCalled();
  });

  test('renders quick script import with parse preview for valid pasted rows', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-import-job', videoId: 'narration-quick-import-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-import-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-import-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const importPanel = within(narrationPanel).getByRole('region', { name: /quick script import/i });

    expect(within(importPanel).getByText('Quick script import')).toBeInTheDocument();
    expect(within(importPanel).getByPlaceholderText(/00:15-00:28 \\| alloy \\| explain this moment/i)).toBeInTheDocument();

    await userEvent.type(
      within(importPanel).getByLabelText(/quick narration script/i),
      '00:15-00:28 | alloy | Explain this moment.\n00:55-01:10 || Inherit default.'
    );

    expect(within(importPanel).getByText((_content, element) => element?.textContent === 'Rows2')).toBeInTheDocument();
    expect(within(importPanel).getByText((_content, element) => element?.textContent === 'Duration28 s')).toBeInTheDocument();
    expect(within(importPanel).getByText('1 · 15 s-28 s · alloy · Explain this moment.')).toBeInTheDocument();
    expect(within(importPanel).getByText('2 · 55 s-70 s · inherit · Inherit default.')).toBeInTheDocument();
  });

  test('replaces narration draft with quick script rows without saving', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-replace-job', videoId: 'narration-quick-replace-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-replace-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-replace-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const importPanel = within(narrationPanel).getByRole('region', { name: /quick script import/i });

    await userEvent.type(within(importPanel).getByLabelText(/quick narration script/i), '00:05-00:09 | verse | Replacement row.');
    await userEvent.click(within(importPanel).getByRole('button', { name: /replace draft/i }));

    expect(within(importPanel).getByText('Imported 1 narration row as local draft.')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(5);
    expect(within(narrationPanel).getByLabelText(/narration 1 end/i)).toHaveValue(9);
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i })).toHaveValue('verse');
    expect(within(narrationPanel).getByDisplayValue('Replacement row.')).toBeInTheDocument();
    expect(within(narrationPanel).queryByLabelText(/narration 2 start/i)).not.toBeInTheDocument();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('appends quick script rows and keeps save payload aligned', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-append-job', videoId: 'narration-quick-append-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-append-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace').mockResolvedValue(
      narrationWorkspaceFixture({ jobId: 'narration-quick-append-job' })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-append-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const importPanel = within(narrationPanel).getByRole('region', { name: /quick script import/i });

    await userEvent.click(within(importPanel).getByLabelText(/append to current draft/i));
    await userEvent.type(within(importPanel).getByLabelText(/quick narration script/i), '01:20-01:24 | alloy | Appended row.');
    await userEvent.click(within(importPanel).getByRole('button', { name: /append to draft/i }));
    await userEvent.click(within(narrationPanel).getByRole('button', { name: /save narration/i }));

    expect(saveNarrationWorkspace).toHaveBeenCalledWith('narration-quick-append-job', {
      segments: [
        { index: 0, startSeconds: 15, endSeconds: 28, text: 'Explain the first scene.', voice: 'alloy' },
        { index: 1, startSeconds: 55, endSeconds: 70.5, text: 'Explain the second scene.', voice: 'verse' },
        { index: 2, startSeconds: 80, endSeconds: 84, text: 'Appended row.', voice: 'alloy' }
      ]
    });
  });

  test('blocks invalid quick script import with row-level errors', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-invalid-job', videoId: 'narration-quick-invalid-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-invalid-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-invalid-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const importPanel = within(narrationPanel).getByRole('region', { name: /quick script import/i });

    await userEvent.type(
      within(importPanel).getByLabelText(/quick narration script/i),
      '00:04-00:02 | alloy | Backwards.\n00:10-00:12 | unknown | Bad voice.'
    );

    expect(within(importPanel).getByText('Line 1: end must be after start.')).toBeInTheDocument();
    expect(within(importPanel).getByText('Line 2: voice must be one of the configured presets.')).toBeInTheDocument();
    expect(within(importPanel).getByRole('button', { name: /replace draft/i })).toBeDisabled();
    expect(within(importPanel).getByRole('button', { name: /append to draft/i })).toBeDisabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('renders quick script export with the current draft text', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-export-job', videoId: 'narration-quick-export-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-export-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-export-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const exportPanel = within(narrationPanel).getByRole('region', { name: /quick script export/i });

    expect(within(exportPanel).getByText('Quick script export')).toBeInTheDocument();
    expect(within(exportPanel).getByLabelText(/quick script export text/i)).toHaveValue([
      '00:15-00:28 | alloy | Explain the first scene.',
      '00:55-01:10.5 | verse | Explain the second scene.'
    ].join('\n'));
  });

  test('updates quick script export from unsaved local draft edits and inherited voice rows', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-export-unsaved-job', videoId: 'narration-quick-export-unsaved-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-export-unsaved-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-export-unsaved-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    await userEvent.selectOptions(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i }), '');
    await userEvent.clear(within(narrationPanel).getByLabelText(/narration 1 start/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/narration 1 start/i), '20');
    await userEvent.clear(within(narrationPanel).getByLabelText(/narration 1 end/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/narration 1 end/i), '24');
    await userEvent.clear(within(narrationPanel).getByLabelText(/segment text/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/segment text/i), 'Unsaved exported row.');

    const exportPanel = within(narrationPanel).getByRole('region', { name: /quick script export/i });
    const exportText = within(exportPanel).getByLabelText(/quick script export text/i) as HTMLTextAreaElement;

    expect(exportText.value).toContain('00:20-00:24 || Unsaved exported row.');
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('copies quick script export without saving narration rows', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-copy-job', videoId: 'narration-quick-copy-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-copy-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-copy-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const exportPanel = within(narrationPanel).getByRole('region', { name: /quick script export/i });

    await userEvent.click(within(exportPanel).getByRole('button', { name: /copy quick script/i }));

    expect(writeText).toHaveBeenCalledWith([
      '00:15-00:28 | alloy | Explain the first scene.',
      '00:55-01:10.5 | verse | Explain the second scene.'
    ].join('\n'));
    expect(await within(exportPanel).findByText('Quick script copied.')).toBeInTheDocument();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('downloads quick script export as text without provider calls', async () => {
    const createObjectUrl = vi.fn().mockReturnValue('blob:quick-script-export');
    const revokeObjectUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectUrl,
      configurable: true
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: revokeObjectUrl,
      configurable: true
    });
    let clickedDownload = '';
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clickedDownload = this.download;
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-quick-download-job', videoId: 'narration-quick-download-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-quick-download-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const previewNarrationSegment = vi.spyOn(linguaFrameApi, 'previewNarrationSegment');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-quick-download-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const exportPanel = within(narrationPanel).getByRole('region', { name: /quick script export/i });

    await userEvent.click(within(exportPanel).getByRole('button', { name: /download quick script/i }));

    expect(createObjectUrl).toHaveBeenCalledWith(expect.any(Blob));
    expect(anchorClick).toHaveBeenCalled();
    expect(clickedDownload).toBe('narration-quick-download-job-narration-quick-script.txt');
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:quick-script-export');
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(previewNarrationSegment).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
  });

  test('blocks narration save and generation when a saved voice is not in the preset catalog', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-unknown-voice-job', videoId: 'narration-unknown-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({
      timeline: {
        ...narrationWorkspaceFixture().timeline,
        segments: narrationWorkspaceFixture().timeline.segments.map((segment, index) =>
          index === 0 ? { ...segment, voice: 'custom-clone' } : segment
        )
      },
      segments: narrationWorkspaceFixture().segments.map((segment, index) =>
        index === 0 ? { ...segment, voice: 'custom-clone' } : segment
      )
    }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-unknown-voice-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 1 voice/i })).toHaveValue('custom-clone');
    expect(within(narrationPanel).getByText('Unknown: custom-clone')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Unknown voice: custom-clone')).toBeInTheDocument();
    expect(within(narrationPanel).getByText('Row 1: voice must be one of the configured presets.')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('button', { name: /save narration/i })).toBeDisabled();
    expect(within(narrationPanel).getByRole('button', { name: /generate narration audio/i })).toBeDisabled();
    expect(within(narrationPanel).getByRole('button', { name: /refresh evidence/i })).toBeEnabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
  });

  test('edits narration timeline windows from the workbench and saves updated timing', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-timeline-job', videoId: 'narration-timeline-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace')
      .mockResolvedValueOnce(narrationWorkspaceFixture({ jobId: 'narration-timeline-job' }))
      .mockResolvedValueOnce(narrationWorkspaceFixture({
        jobId: 'narration-timeline-job',
        segments: narrationWorkspaceFixture().segments.map((segment, index) =>
          index === 0
            ? { ...segment, startSeconds: 15.25, endSeconds: 28.5, durationSeconds: 13.25 }
            : segment
        )
      }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace').mockResolvedValue(
      narrationWorkspaceFixture({
        jobId: 'narration-timeline-job',
        segments: narrationWorkspaceFixture().segments.map((segment, index) =>
          index === 0
            ? { ...segment, startSeconds: 15.25, endSeconds: 28.5, durationSeconds: 13.25 }
            : segment
        )
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-timeline-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const firstTimelineSegment = within(narrationPanel).getByLabelText('Timeline segment 1: 15 s to 28 s, READY');

    await userEvent.click(firstTimelineSegment);
    expect(within(narrationPanel).getByText((_content, element) => element?.textContent === '15 s - 28 s')).toBeInTheDocument();

    firstTimelineSegment.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(15.25);
    expect(within(narrationPanel).getByLabelText(/narration 1 end/i)).toHaveValue(28.25);

    await userEvent.keyboard('{Shift>}{ArrowRight}{/Shift}');
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(15.25);
    expect(within(narrationPanel).getByLabelText(/narration 1 end/i)).toHaveValue(28.5);

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /save narration/i }));

    expect(saveNarrationWorkspace).toHaveBeenCalledWith('narration-timeline-job', {
      segments: [
        {
          index: 0,
          startSeconds: 15.25,
          endSeconds: 28.5,
          text: 'Explain the first scene.',
          voice: 'alloy'
        },
        {
          index: 1,
          startSeconds: 55,
          endSeconds: 70.5,
          text: 'Explain the second scene.',
          voice: 'verse'
        }
      ]
    });
    expect(await within(narrationPanel).findByText('Narration saved.')).toBeInTheDocument();
  });

  test('narration timing assistant closes gaps as a local draft without saving', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-timing-gap-job', videoId: 'narration-timing-gap-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-timing-gap-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-timing-gap-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const assistantPanel = within(narrationPanel).getByRole('region', { name: /narration timing assistant/i });

    expect(within(assistantPanel).getAllByText('1 gap')).toHaveLength(2);
    expect(within(assistantPanel).getAllByText('27 s')).toHaveLength(2);
    expect(within(assistantPanel).getByText('Gap before row 2: 27 s.')).toBeInTheDocument();

    await userEvent.click(within(assistantPanel).getByRole('button', { name: /close gaps/i }));

    expect(await within(assistantPanel).findByText('Closed 1 narration timing gap.')).toBeInTheDocument();
    expect(within(assistantPanel).getAllByText('No timing issues')).toHaveLength(2);
    expect(within(narrationPanel).getByDisplayValue('28.25')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save narration/i })).toBeEnabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('narration timing assistant resolves overlaps and keeps provider actions untouched', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-timing-overlap-job', videoId: 'narration-timing-overlap-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(
      narrationWorkspaceFixture({
        jobId: 'narration-timing-overlap-job',
        generationReady: false,
        timeline: {
          ...narrationWorkspaceFixture().timeline,
          gapSeconds: 0,
          gapCount: 0,
          hasOverlap: true,
          generationReady: false
        },
        segments: [
          {
            ...narrationWorkspaceFixture().segments[0],
            startSeconds: 0,
            endSeconds: 5,
            durationSeconds: 5
          },
          {
            ...narrationWorkspaceFixture().segments[1],
            startSeconds: 4,
            endSeconds: 9,
            durationSeconds: 5
          }
        ]
      })
    );
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');
    const generateNarratedVideo = vi.spyOn(linguaFrameApi, 'generateNarratedVideo');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-timing-overlap-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const assistantPanel = within(narrationPanel).getByRole('region', { name: /narration timing assistant/i });

    expect(within(assistantPanel).getAllByText('1 overlap')).toHaveLength(2);
    expect(within(assistantPanel).getByText('Row 2 overlaps the previous row by 1 s.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save narration/i })).toBeDisabled();

    await userEvent.click(within(assistantPanel).getByRole('button', { name: /resolve overlaps/i }));

    expect(await within(assistantPanel).findByText('Resolved 1 narration timing overlap.')).toBeInTheDocument();
    expect(within(assistantPanel).getAllByText('No timing issues')).toHaveLength(2);
    expect(within(narrationPanel).getByDisplayValue('5.25')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /save narration/i })).toBeEnabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
    expect(generateNarratedVideo).not.toHaveBeenCalled();
  });

  test('blocks save and narration generation when timeline keyboard editing creates overlap', async () => {
    const overlappingWorkspace = narrationWorkspaceFixture({
      jobId: 'narration-overlap-edit-job',
      segments: narrationWorkspaceFixture().segments.map((segment, index) =>
        index === 1
          ? { ...segment, startSeconds: 28.5, endSeconds: 40, durationSeconds: 11.5 }
          : segment
      )
    });
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-overlap-edit-job', videoId: 'narration-overlap-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(overlappingWorkspace);
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-overlap-edit-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const secondTimelineSegment = within(narrationPanel).getByLabelText('Timeline segment 2: 28.5 s to 40 s, READY');

    secondTimelineSegment.focus();
    await userEvent.keyboard('{Alt>}{ArrowLeft}{ArrowLeft}{ArrowLeft}{/Alt}');

    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(27.75);
    expect(within(narrationPanel).getByText('Row 2: time range overlaps the previous row.')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('button', { name: /save narration/i })).toBeDisabled();
    expect(within(narrationPanel).getByRole('button', { name: /generate narration audio/i })).toBeDisabled();
    expect(within(narrationPanel).getByRole('button', { name: /refresh evidence/i })).toBeEnabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
  });

  test('reindexes narration rows and timeline bars after deleting a drag-edited segment', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-delete-edit-job', videoId: 'narration-delete-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-delete-edit-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-delete-edit-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const firstTimelineSegment = within(narrationPanel).getByLabelText('Timeline segment 1: 15 s to 28 s, READY');

    firstTimelineSegment.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(15.25);

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /delete row/i }));

    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(55);
    expect(within(narrationPanel).getByLabelText('Timeline segment 1: 55 s to 70.5 s, READY')).toBeInTheDocument();
    expect(within(narrationPanel).queryByLabelText(/narration 2 start/i)).not.toBeInTheDocument();
    expect(within(narrationPanel).queryByLabelText(/Timeline segment 2:/i)).not.toBeInTheDocument();
  });

  test('renders narration preview with narrated video when available', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-preview-job', videoId: 'narration-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'preview-narrated-video',
        jobId: 'narration-preview-job',
        type: 'NARRATED_VIDEO',
        filename: 'narrated-video.mp4',
        contentType: 'video/mp4'
      }),
      artifactFixture({
        artifactId: 'preview-burned-video',
        jobId: 'narration-preview-job',
        type: 'BURNED_VIDEO',
        filename: 'burned-video.mp4',
        contentType: 'video/mp4'
      })
    ]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });

    expect(within(previewPanel).getByText('Narrated video')).toBeInTheDocument();
    expect(within(previewPanel).getByLabelText('Narration preview player')).toHaveAttribute(
      'src',
      '/api/jobs/narration-preview-job/artifacts/preview-narrated-video/download'
    );
  });

  test('falls narration preview back to source video without generated video artifacts', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-source-preview-job', videoId: 'source-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([
      artifactFixture({
        artifactId: 'preview-narration-audio',
        jobId: 'narration-source-preview-job',
        type: 'NARRATION_AUDIO',
        filename: 'narration-audio.mp3',
        contentType: 'audio/mpeg'
      })
    ]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-source-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-source-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });

    expect(within(previewPanel).getByText('Source video')).toBeInTheDocument();
    expect(within(previewPanel).getByLabelText('Narration preview player')).toHaveAttribute(
      'src',
      '/api/media/uploads/source-preview-video/source/download'
    );
  });

  test('jumps narration preview to the selected segment start and updates playhead time', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-jump-preview-job', videoId: 'jump-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-jump-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-jump-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });
    const player = within(previewPanel).getByLabelText('Narration preview player') as HTMLVideoElement;

    await userEvent.click(within(previewPanel).getByRole('button', { name: /jump to narration 1/i }));

    expect(player.currentTime).toBe(15);
    expect(within(previewPanel).getByText('15 s')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText('Narration preview playhead: 15 s')).toBeInTheDocument();
  });

  test('plays only the selected narration preview window', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-window-preview-job', videoId: 'window-preview-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-window-preview-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-window-preview-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });
    const player = within(previewPanel).getByLabelText('Narration preview player') as HTMLVideoElement;
    const play = vi.spyOn(player, 'play').mockResolvedValue(undefined);

    await userEvent.click(within(previewPanel).getByRole('button', { name: /play window/i }));

    expect(player.currentTime).toBe(15);
    expect(play).toHaveBeenCalledTimes(1);
    expect(within(previewPanel).getByText((_content, element) => element?.textContent === 'Window15 s to 28 s')).toBeInTheDocument();
  });

  test('renders narration waveform overview with active and gap bucket metrics', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-waveform-job', videoId: 'narration-waveform-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-waveform-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-waveform-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const waveformPanel = within(narrationPanel).getByRole('region', { name: /narration waveform overview/i });

    expect(within(waveformPanel).getByText('Narration waveform overview')).toBeInTheDocument();
    expect(within(waveformPanel).getAllByLabelText(/^Waveform bucket/i)).toHaveLength(48);
    expect(within(waveformPanel).getByText((_content, element) => element?.textContent === 'Active buckets26')).toBeInTheDocument();
    expect(within(waveformPanel).getByText((_content, element) => element?.textContent === 'Gap buckets22')).toBeInTheDocument();
  });

  test('updates selected narration waveform window when selecting another row', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-waveform-select-job', videoId: 'narration-waveform-select-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-waveform-select-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-waveform-select-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const waveformPanel = within(narrationPanel).getByRole('region', { name: /narration waveform overview/i });

    expect(within(waveformPanel).getByLabelText('Selected waveform window: 0% to 23.4234%')).toBeInTheDocument();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: '2' }));

    expect(within(waveformPanel).getByLabelText('Selected waveform window: 72.0721% to 100%')).toBeInTheDocument();
  });

  test('scrubs narration waveform midpoint into the preview player and playheads', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-waveform-scrub-job', videoId: 'narration-waveform-scrub-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-waveform-scrub-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-waveform-scrub-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });
    const waveformPanel = within(narrationPanel).getByRole('region', { name: /narration waveform overview/i });
    const player = within(previewPanel).getByLabelText('Narration preview player') as HTMLVideoElement;

    await userEvent.click(within(waveformPanel).getByRole('button', { name: /scrub to midpoint/i }));

    expect(player.currentTime).toBe(42.75);
    expect(within(waveformPanel).getByLabelText('Narration waveform playhead: 50%')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText('Narration preview playhead: 42.75 s')).toBeInTheDocument();
  });

  test('disables narration waveform scrubbing when no preview media is available', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-waveform-unavailable-job', videoId: '', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-waveform-unavailable-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-waveform-unavailable-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const waveformPanel = within(narrationPanel).getByRole('region', { name: /narration waveform overview/i });

    expect(within(waveformPanel).getByRole('button', { name: /scrub to start/i })).toBeDisabled();
    expect(within(waveformPanel).getByRole('button', { name: /scrub to midpoint/i })).toBeDisabled();
    expect(within(waveformPanel).getByRole('button', { name: /scrub to selected/i })).toBeDisabled();
  });

  test('renders narration editing commands controls for local row operations', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-command-job', videoId: 'narration-command-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-command-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-command-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });

    expect(within(commandPanel).getByText('Narration editing commands')).toBeInTheDocument();
    expect(within(commandPanel).getByRole('button', { name: /duplicate/i })).toBeEnabled();
    expect(within(commandPanel).getByRole('button', { name: /split at playhead/i })).toBeDisabled();
    expect(within(commandPanel).getByRole('button', { name: /merge next/i })).toBeEnabled();
    expect(within(commandPanel).getByRole('button', { name: /insert after/i })).toBeEnabled();
  });

  test('narration editing commands duplicate the selected row locally and save the updated row list', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-duplicate-command-job', videoId: 'narration-duplicate-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-duplicate-command-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace').mockResolvedValue(
      narrationWorkspaceFixture({ jobId: 'narration-duplicate-command-job' })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-duplicate-command-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });

    await userEvent.click(within(commandPanel).getByRole('button', { name: /duplicate/i }));

    expect(within(commandPanel).getByText('Duplicated narration 1.')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(28);
    expect(within(narrationPanel).getByLabelText(/narration 2 end/i)).toHaveValue(41);
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 2 voice/i })).toHaveValue('alloy');

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /save narration/i }));

    expect(saveNarrationWorkspace).toHaveBeenCalledWith('narration-duplicate-command-job', {
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          text: 'Explain the first scene.',
          voice: 'alloy'
        },
        {
          index: 1,
          startSeconds: 28,
          endSeconds: 41,
          text: 'Explain the first scene.',
          voice: 'alloy'
        },
        {
          index: 2,
          startSeconds: 55,
          endSeconds: 70.5,
          text: 'Explain the second scene.',
          voice: 'verse'
        }
      ]
    });
  });

  test('narration editing commands split the selected row at the preview playhead', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-split-command-job', videoId: 'narration-split-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-split-command-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-split-command-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const previewPanel = within(narrationPanel).getByRole('region', { name: /narration preview/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });
    const player = within(previewPanel).getByLabelText('Narration preview player') as HTMLVideoElement;

    player.currentTime = 20;
    player.dispatchEvent(new Event('timeupdate', { bubbles: true }));

    await userEvent.click(within(commandPanel).getByRole('button', { name: /split at playhead/i }));

    expect(within(commandPanel).getByText('Split narration 1 at 20 s.')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(15);
    expect(within(narrationPanel).getByLabelText(/narration 1 end/i)).toHaveValue(20);
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(20);
    expect(within(narrationPanel).getByLabelText(/narration 2 end/i)).toHaveValue(28);
  });

  test('narration editing commands merge the selected row with the next row and block the final row', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-merge-command-job', videoId: 'narration-merge-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-merge-command-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-merge-command-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });

    await userEvent.click(within(narrationPanel).getByRole('button', { name: '2' }));
    expect(within(commandPanel).getByRole('button', { name: /merge next/i })).toBeDisabled();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: '1' }));
    await userEvent.click(within(commandPanel).getByRole('button', { name: /merge next/i }));

    expect(within(commandPanel).getByText('Merged narration 1 with narration 2.')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 1 start/i)).toHaveValue(15);
    expect(within(narrationPanel).getByLabelText(/narration 1 end/i)).toHaveValue(70.5);
    expect(within(narrationPanel).queryByLabelText(/narration 2 start/i)).not.toBeInTheDocument();
  });

  test('narration editing commands insert a blank row and keep save blocked until required fields are filled', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-insert-command-job', videoId: 'narration-insert-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-insert-command-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-insert-command-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });

    await userEvent.click(within(commandPanel).getByRole('button', { name: /insert after/i }));

    expect(within(commandPanel).getByText('Inserted narration 2.')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(28);
    expect(within(narrationPanel).getByLabelText(/narration 2 end/i)).toHaveValue(33);
    expect(within(narrationPanel).getByRole('combobox', { name: /narration 2 voice/i })).toHaveValue('');
    expect(within(narrationPanel).getByText('Row 2: text is required.')).toBeInTheDocument();
    expect(within(narrationPanel).getByRole('button', { name: /save narration/i })).toBeDisabled();
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
  });

  test('narration draft history starts clean with undo redo and revert disabled', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-history-job', videoId: 'narration-history-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-history-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-history-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const historyPanel = within(narrationPanel).getByRole('region', { name: /narration draft history/i });

    expect(within(historyPanel).getByText('Narration draft history')).toBeInTheDocument();
    expect(within(historyPanel).getByText('Clean draft')).toBeInTheDocument();
    expect(within(historyPanel).getByRole('button', { name: /undo/i })).toBeDisabled();
    expect(within(historyPanel).getByRole('button', { name: /redo/i })).toBeDisabled();
    expect(within(historyPanel).getByRole('button', { name: /revert to saved/i })).toBeDisabled();
    expect(within(historyPanel).getByText((_content, element) => element?.textContent === 'Added0')).toBeInTheDocument();
  });

  test('narration draft history tracks duplicate changes and keeps save aligned with current draft', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-history-duplicate-job', videoId: 'narration-history-duplicate-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-history-duplicate-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace').mockResolvedValue(
      narrationWorkspaceFixture({ jobId: 'narration-history-duplicate-job' })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-history-duplicate-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });
    const historyPanel = within(narrationPanel).getByRole('region', { name: /narration draft history/i });

    await userEvent.click(within(commandPanel).getByRole('button', { name: /duplicate/i }));

    expect(within(historyPanel).getByText('Unsaved changes')).toBeInTheDocument();
    expect(within(historyPanel).getByText((_content, element) => element?.textContent === 'Added1')).toBeInTheDocument();
    expect(within(historyPanel).getByRole('button', { name: /undo/i })).toBeEnabled();
    expect(within(historyPanel).getByRole('button', { name: /revert to saved/i })).toBeEnabled();

    await userEvent.click(within(narrationPanel).getByRole('button', { name: /save narration/i }));

    expect(saveNarrationWorkspace).toHaveBeenCalledWith('narration-history-duplicate-job', {
      segments: [
        { index: 0, startSeconds: 15, endSeconds: 28, text: 'Explain the first scene.', voice: 'alloy' },
        { index: 1, startSeconds: 28, endSeconds: 41, text: 'Explain the first scene.', voice: 'alloy' },
        { index: 2, startSeconds: 55, endSeconds: 70.5, text: 'Explain the second scene.', voice: 'verse' }
      ]
    });
  });

  test('narration draft history undo and redo restore table and timeline rows', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-history-undo-job', videoId: 'narration-history-undo-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-history-undo-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-history-undo-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });
    const historyPanel = within(narrationPanel).getByRole('region', { name: /narration draft history/i });

    await userEvent.click(within(commandPanel).getByRole('button', { name: /duplicate/i }));
    expect(within(narrationPanel).getByLabelText('Timeline segment 2: 28 s to 41 s, READY')).toBeInTheDocument();

    await userEvent.click(within(historyPanel).getByRole('button', { name: /undo/i }));

    expect(within(historyPanel).getByRole('button', { name: /redo/i })).toBeEnabled();
    expect(within(narrationPanel).queryByLabelText('Timeline segment 2: 28 s to 41 s, READY')).not.toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(55);

    await userEvent.click(within(historyPanel).getByRole('button', { name: /redo/i }));

    expect(within(narrationPanel).getByLabelText('Timeline segment 2: 28 s to 41 s, READY')).toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(28);
  });

  test('narration draft history tracks table text edits and timeline timing edits', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-history-edit-job', videoId: 'narration-history-edit-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-history-edit-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-history-edit-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const historyPanel = within(narrationPanel).getByRole('region', { name: /narration draft history/i });
    const firstTimelineSegment = within(narrationPanel).getByLabelText('Timeline segment 1: 15 s to 28 s, READY');

    await userEvent.clear(within(narrationPanel).getByLabelText(/segment text/i));
    await userEvent.type(within(narrationPanel).getByLabelText(/segment text/i), 'Edited narration text.');
    firstTimelineSegment.focus();
    await userEvent.keyboard('{ArrowRight}');

    expect(within(historyPanel).getByText((_content, element) => element?.textContent === 'Timing1')).toBeInTheDocument();
    expect(within(historyPanel).getByText((_content, element) => element?.textContent === 'Text1')).toBeInTheDocument();
    expect(within(historyPanel).getByText('Narration 1')).toBeInTheDocument();
  });

  test('narration draft history reverts blank inserted rows without saving or calling providers', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-history-revert-job', videoId: 'narration-history-revert-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-history-revert-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    const saveNarrationWorkspace = vi.spyOn(linguaFrameApi, 'saveNarrationWorkspace');
    const generateNarrationAudio = vi.spyOn(linguaFrameApi, 'generateNarrationAudio');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-history-revert-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const commandPanel = within(narrationPanel).getByRole('region', { name: /narration editing commands/i });
    const historyPanel = within(narrationPanel).getByRole('region', { name: /narration draft history/i });

    await userEvent.click(within(commandPanel).getByRole('button', { name: /insert after/i }));
    expect(within(narrationPanel).getByText('Row 2: text is required.')).toBeInTheDocument();

    await userEvent.click(within(historyPanel).getByRole('button', { name: /revert to saved/i }));

    expect(within(historyPanel).getByText('Clean draft')).toBeInTheDocument();
    expect(within(narrationPanel).queryByText('Row 2: text is required.')).not.toBeInTheDocument();
    expect(within(narrationPanel).getByLabelText(/narration 2 start/i)).toHaveValue(55);
    expect(saveNarrationWorkspace).not.toHaveBeenCalled();
    expect(generateNarrationAudio).not.toHaveBeenCalled();
  });

  test('exports and imports narration script packages from the narration workspace', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-package-job', videoId: 'narration-package-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace')
      .mockResolvedValueOnce(narrationWorkspaceFixture({ jobId: 'narration-package-job' }))
      .mockResolvedValueOnce(narrationWorkspaceFixture({
        jobId: 'narration-package-job',
        segments: [
          {
            ...narrationWorkspaceFixture().segments[0],
            text: 'Imported first explanation.'
          }
        ]
      }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture());
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture());
    vi.spyOn(linguaFrameApi, 'downloadNarrationScriptPackageMarkdown').mockResolvedValue(new Blob(['# Package']));
    vi.spyOn(linguaFrameApi, 'downloadNarrationScriptPackageZip').mockResolvedValue(new Blob(['zip']));
    const importNarrationScriptPackage = vi.spyOn(linguaFrameApi, 'importNarrationScriptPackage')
      .mockResolvedValue({
        jobId: 'narration-package-job',
        importedSegmentCount: 1,
        totalCharacterCount: 27,
        voiceSummary: 'PRESET:alloy',
        replacedExisting: true,
        warnings: [],
        workspace: narrationWorkspaceFixture({
          jobId: 'narration-package-job',
          segments: [
            {
              ...narrationWorkspaceFixture().segments[0],
              text: 'Imported first explanation.'
            }
          ]
        })
      });

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-package-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const packagePanel = within(narrationPanel).getByRole('region', { name: /script package/i });
    expect(within(packagePanel).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(packagePanel).getByText(/2 segments/)).toBeInTheDocument();
    expect(within(packagePanel).getByText('PRESET:alloy')).toBeInTheDocument();
    expect(within(packagePanel).getByText('SCRIPT_SEGMENTS: READY')).toBeInTheDocument();

    await userEvent.click(within(packagePanel).getByRole('button', { name: /download package markdown/i }));
    expect(linguaFrameApi.downloadNarrationScriptPackageMarkdown).toHaveBeenCalledWith('narration-package-job');
    await userEvent.click(within(packagePanel).getByRole('button', { name: /download package zip/i }));
    expect(linguaFrameApi.downloadNarrationScriptPackageZip).toHaveBeenCalledWith('narration-package-job');

    await userEvent.click(within(packagePanel).getByLabelText(/script package json/i));
    await userEvent.paste('{not valid json');
    expect(within(packagePanel).getByText('Package JSON is not valid.')).toBeInTheDocument();
    expect(within(packagePanel).getByRole('button', { name: /import package/i })).toBeDisabled();

    await userEvent.clear(within(packagePanel).getByLabelText(/script package json/i));
    await userEvent.click(within(packagePanel).getByLabelText(/script package json/i));
    await userEvent.paste(JSON.stringify({
      replaceExisting: true,
      mixSettings: {
        duckingVolume: 0.125,
        narrationVolume: 1.75,
        fadeDurationMs: 400
      },
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          text: 'Imported first explanation.',
          voice: 'alloy'
        }
      ]
    }));
    await userEvent.click(within(packagePanel).getByLabelText(/replace current narration workspace/i));
    await userEvent.click(within(packagePanel).getByRole('button', { name: /import package/i }));

    expect(importNarrationScriptPackage).toHaveBeenCalledWith('narration-package-job', {
      replaceExisting: true,
      mixSettings: {
        duckingVolume: 0.125,
        narrationVolume: 1.75,
        fadeDurationMs: 400
      },
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          text: 'Imported first explanation.',
          voice: 'alloy'
        }
      ]
    });
    expect(await within(narrationPanel).findByText('Imported 1 segment from package.')).toBeInTheDocument();
    expect((await within(narrationPanel).findAllByText('Imported first explanation.')).length).toBeGreaterThan(0);
  });

  test('applies narration demo preset from the narration workspace', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-preset-job', videoId: 'narration-preset-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace')
      .mockResolvedValueOnce(narrationWorkspaceFixture({ jobId: 'narration-preset-job' }))
      .mockResolvedValueOnce(narrationWorkspaceFixture({
        jobId: 'narration-preset-job',
        segmentCount: 4,
        segments: [
          {
            ...narrationWorkspaceFixture().segments[0],
            text: 'Preset first explanation.',
            voice: null
          }
        ]
      }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture({
      status: 'ATTENTION',
      narrationAudioReady: false
    }));
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture({
      jobId: 'narration-preset-job',
      segmentCount: 4,
      voiceSummary: 'DEFAULT:verse'
    }));
    vi.spyOn(linguaFrameApi, 'listNarrationDemoPresets').mockResolvedValue([narrationDemoPresetFixture()]);
    const applyNarrationDemoPreset = vi.spyOn(linguaFrameApi, 'applyNarrationDemoPreset')
      .mockResolvedValue({
        jobId: 'narration-preset-job',
        presetId: 'tears-showcase-narration',
        profileId: 'tears-showcase',
        importedSegmentCount: 4,
        totalCharacterCount: 128,
        voiceSummary: 'DEFAULT:verse',
        replacedExisting: true,
        generatedMedia: false,
        workspace: narrationWorkspaceFixture({
          jobId: 'narration-preset-job',
          segmentCount: 4,
          segments: [
            {
              ...narrationWorkspaceFixture().segments[0],
              text: 'Preset first explanation.',
              voice: null
            }
          ]
        }),
        scriptPackage: narrationScriptPackageFixture({
          jobId: 'narration-preset-job',
          segmentCount: 4,
          voiceSummary: 'DEFAULT:verse'
        }),
        narrationEvidenceStatus: 'ATTENTION'
      });

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-preset-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const presetPanel = within(narrationPanel).getByRole('region', { name: /demo narration preset/i });
    expect(within(presetPanel).getByText('Tears showcase narration')).toBeInTheDocument();
    expect(within(presetPanel).getByText('tears-showcase')).toBeInTheDocument();
    expect(within(presetPanel).getByText('tears-of-steel-casting')).toBeInTheDocument();
    expect(within(presetPanel).getByText('4 segments')).toBeInTheDocument();
    expect(within(presetPanel).getByRole('button', { name: /apply preset/i })).toBeDisabled();

    await userEvent.click(within(presetPanel).getByLabelText(/replace current narration workspace with preset/i));
    await userEvent.click(within(presetPanel).getByRole('button', { name: /apply preset/i }));

    expect(applyNarrationDemoPreset).toHaveBeenCalledWith('narration-preset-job', {
      presetId: 'tears-showcase-narration',
      replaceExisting: true
    });
    expect(await within(narrationPanel).findByText('Applied tears-showcase-narration with 4 segments.')).toBeInTheDocument();
    expect((await within(narrationPanel).findAllByText('Preset first explanation.')).length).toBeGreaterThan(0);
    expect(within(presetPanel).getByText('Generate narration audio separately after applying.')).toBeInTheDocument();
  });

  test('renders narration demo from the narration workspace with explicit acknowledgements', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-render-job', videoId: 'narration-render-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts')
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        artifactFixture({
          artifactId: 'narration-audio-artifact',
          jobId: 'narration-render-job',
          type: 'NARRATION_AUDIO',
          filename: 'narration-audio.mp3'
        }),
        artifactFixture({
          artifactId: 'narrated-video-artifact',
          jobId: 'narration-render-job',
          type: 'NARRATED_VIDEO',
          filename: 'narrated-video.mp4'
        })
      ]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace')
      .mockResolvedValueOnce(narrationWorkspaceFixture({ jobId: 'narration-render-job' }))
      .mockResolvedValueOnce(narrationWorkspaceFixture({
        jobId: 'narration-render-job',
        segmentCount: 4,
        segments: [
          {
            ...narrationWorkspaceFixture().segments[0],
            text: 'Rendered first explanation.',
            voice: null
          }
        ]
      }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence')
      .mockResolvedValueOnce(narrationEvidenceFixture({ status: 'ATTENTION' }))
      .mockResolvedValueOnce(narrationEvidenceFixture({
        jobId: 'narration-render-job',
        status: 'READY',
        narrationAudioReady: true,
        narratedVideoReady: true
      }));
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage')
      .mockResolvedValueOnce(narrationScriptPackageFixture({ jobId: 'narration-render-job' }))
      .mockResolvedValueOnce(narrationScriptPackageFixture({
        jobId: 'narration-render-job',
        segmentCount: 4
      }));
    vi.spyOn(linguaFrameApi, 'listNarrationDemoPresets').mockResolvedValue([narrationDemoPresetFixture()]);
    const preflightNarrationDemoRender = vi.spyOn(linguaFrameApi, 'preflightNarrationDemoRender')
      .mockResolvedValueOnce(narrationDemoRenderPreflightFixture({ status: 'ATTENTION' }))
      .mockResolvedValueOnce(narrationDemoRenderPreflightFixture({ status: 'READY', paidProvider: true }));
    const renderNarrationDemo = vi.spyOn(linguaFrameApi, 'renderNarrationDemo')
      .mockResolvedValue(narrationDemoRenderResultFixture({
        jobId: 'narration-render-job',
        status: 'READY'
      }));

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-render-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const renderPanel = within(narrationPanel).getByRole('region', { name: /render narration demo/i });
    expect(within(renderPanel).getAllByText('Tears showcase narration').length).toBeGreaterThan(0);
    expect(within(renderPanel).getByRole('button', { name: /render narration demo/i })).toBeDisabled();

    await userEvent.click(within(renderPanel).getByLabelText(/replace current narration workspace before rendering/i));
    expect(within(renderPanel).getByRole('button', { name: /render narration demo/i })).toBeDisabled();

    await userEvent.click(within(renderPanel).getByLabelText(/i understand this can call tts providers/i));
    await userEvent.click(within(renderPanel).getByRole('checkbox', { name: /generate narrated video/i }));
    expect(within(renderPanel).getByRole('button', { name: /render narration demo/i })).toBeDisabled();
    await userEvent.click(within(renderPanel).getByRole('button', { name: /run render preflight/i }));
    expect(preflightNarrationDemoRender).toHaveBeenCalledWith('narration-render-job', {
      presetId: 'tears-showcase-narration',
      replaceExisting: true,
      generateNarratedVideo: false
    });
    expect(await within(renderPanel).findByText('TTS_PROVIDER: WARN', { exact: false })).toBeInTheDocument();
    expect(within(renderPanel).getByText('openai')).toBeInTheDocument();
    expect(within(renderPanel).getAllByText('Audio only').length).toBeGreaterThan(0);
    await userEvent.click(within(renderPanel).getByRole('button', { name: /render narration demo/i }));

    expect(renderNarrationDemo).toHaveBeenCalledWith('narration-render-job', {
      presetId: 'tears-showcase-narration',
      replaceExisting: true,
      generateNarratedVideo: false
    });
    expect(await within(narrationPanel).findByText('Rendered narration demo tears-showcase-narration: READY.')).toBeInTheDocument();
    expect(preflightNarrationDemoRender).toHaveBeenCalledTimes(2);
    expect(within(renderPanel).getByText('PRESET_APPLY: SUCCEEDED')).toBeInTheDocument();
    expect(within(renderPanel).getByText('NARRATION_AUDIO: SUCCEEDED')).toBeInTheDocument();
    expect(within(renderPanel).getByText('NARRATED_VIDEO: SUCCEEDED')).toBeInTheDocument();
    expect((await within(narrationPanel).findAllByText('Rendered first explanation.')).length).toBeGreaterThan(0);
  });

  test('keeps narration demo render disabled when preflight is blocked', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({ jobId: 'narration-blocked-job', videoId: 'narration-blocked-video', targetLanguage: 'zh-CN' })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getNarrationWorkspace').mockResolvedValue(narrationWorkspaceFixture({ jobId: 'narration-blocked-job' }));
    vi.spyOn(linguaFrameApi, 'getNarrationEvidence').mockResolvedValue(narrationEvidenceFixture({ status: 'ATTENTION' }));
    vi.spyOn(linguaFrameApi, 'getNarrationScriptPackage').mockResolvedValue(narrationScriptPackageFixture({ jobId: 'narration-blocked-job' }));
    vi.spyOn(linguaFrameApi, 'listNarrationDemoPresets').mockResolvedValue([narrationDemoPresetFixture()]);
    const preflightNarrationDemoRender = vi.spyOn(linguaFrameApi, 'preflightNarrationDemoRender').mockResolvedValue(
      narrationDemoRenderPreflightFixture({
        jobId: 'narration-blocked-job',
        status: 'BLOCKED',
        generateNarratedVideo: false,
        requiredConfirmations: ['REPLACE_EXISTING'],
        checks: [
          {
            key: 'REPLACE_CONFIRMATION',
            label: 'Replace confirmation',
            status: 'BLOCK',
            message: 'Rendering would replace existing narration rows.'
          }
        ]
      })
    );
    const renderNarrationDemo = vi.spyOn(linguaFrameApi, 'renderNarrationDemo');

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'narration-blocked-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const narrationPanel = await screen.findByRole('region', { name: /narration workspace/i });
    const renderPanel = within(narrationPanel).getByRole('region', { name: /render narration demo/i });

    await userEvent.click(within(renderPanel).getByRole('checkbox', { name: /generate narrated video/i }));
    await userEvent.click(within(renderPanel).getByRole('button', { name: /run render preflight/i }));

    expect(preflightNarrationDemoRender).toHaveBeenCalledWith('narration-blocked-job', {
      presetId: 'tears-showcase-narration',
      replaceExisting: false,
      generateNarratedVideo: false
    });
    expect(await within(renderPanel).findByText('BLOCKED')).toBeInTheDocument();
    expect(within(renderPanel).getAllByText('Audio only').length).toBeGreaterThan(0);
    expect(within(renderPanel).getByText('REPLACE_CONFIRMATION: BLOCK', { exact: false })).toBeInTheDocument();
    expect(within(renderPanel).getByRole('button', { name: /render narration demo/i })).toBeDisabled();
    expect(renderNarrationDemo).not.toHaveBeenCalled();
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
        artifactId: 'dubbed-video',
        jobId: 'handoff-ready-job',
        type: 'DUBBED_VIDEO',
        filename: 'dubbed-video.mp4',
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
      }),
      artifactFixture({
        artifactId: 'session-dubbed-video',
        jobId: 'session-ready-job',
        type: 'DUBBED_VIDEO',
        filename: 'dubbed-video.mp4',
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
      releaseNotesLength: 13,
      reviewDecisionCounts: [{ category: 'EDITED', count: 1 }],
      issueCategoryCounts: [{ category: 'TERM', count: 1 }],
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
      segments: [
        {
          index: 1,
          text: '人工修正后的字幕',
          decision: 'EDITED',
          issueCategories: ['TERM'],
          reviewerNote: 'Use the glossary term.'
        }
      ]
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
    await userEvent.type(within(draftEditor).getByLabelText(/release notes/i), 'Ready to publish.');
    await userEvent.click(within(draftEditor).getByRole('button', { name: /publish reviewed subtitles/i }));
    expect(publishReviewed).toHaveBeenCalledWith('draft-job', {
      language: 'zh-CN',
      includeBurnedVideo: true,
      releaseNotes: 'Ready to publish.'
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
            updatedAt: '2026-06-28T10:00:00Z',
            decision: 'EDITED',
            issueCategories: ['TERM'],
            reviewerNote: 'hidden reviewer note',
            noteLength: 20
          }
        ]
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'evidence-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const evidence = await screen.findByRole('region', { name: /^demo evidence$/i });
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

    const evidence = await screen.findByRole('region', { name: /^demo evidence$/i });
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

  test('shows a demo presenter pack for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'presenter-showcase-job',
        videoId: 'presenter-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoPresenterPack').mockResolvedValue(demoPresenterPackFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'presenter-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const presenterPack = await screen.findByRole('region', { name: /demo presenter pack/i });
    expect(within(presenterPack).getByText('READY')).toBeInTheDocument();
    expect(within(presenterPack).getAllByText('Recommended baseline').length).toBeGreaterThan(0);
    expect(within(presenterPack).getAllByText('Best quality').length).toBeGreaterThan(0);
    expect(within(presenterPack).getAllByText('Lowest cost').length).toBeGreaterThan(0);
    expect(within(presenterPack).getByText('Demo run package')).toBeInTheDocument();
    expect(within(presenterPack).getByRole('link', { name: /ai audit package/i })).toHaveAttribute(
      'href',
      '/api/jobs/presenter-showcase-job/ai-audit-package/download'
    );
    expect(within(presenterPack).getByRole('button', { name: /copy presenter notes/i })).toBeEnabled();
    expect(linguaFrameApi.getDemoPresenterPack).toHaveBeenCalledWith('presenter-showcase-job');
  });

  test('shows a demo replay card for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'replay-showcase-job',
        videoId: 'replay-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoReplayCard').mockResolvedValue(demoReplayCardFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'replay-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const replayCard = await screen.findByRole('region', { name: /demo replay card/i });
    expect(within(replayCard).getByText('READY')).toBeInTheDocument();
    expect(within(replayCard).getByText('tears-showcase replay card to zh-CN')).toBeInTheDocument();
    expect(within(replayCard).getByText('Full Tears of Steel replay')).toBeInTheDocument();
    expect(within(replayCard).getByText('LINGUAFRAME_DEMO_JOB_ID=replay-showcase-job scripts/demo/demo-replay-card.sh')).toBeInTheDocument();
    expect(within(replayCard).getByRole('link', { name: /demo run package/i })).toHaveAttribute(
      'href',
      '/api/jobs/replay-showcase-job/demo-run-package/download'
    );
    await userEvent.click(within(replayCard).getAllByRole('button', { name: /copy/i })[0]);
    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalled());
    expect(linguaFrameApi.getDemoReplayCard).toHaveBeenCalledWith('replay-showcase-job');
  });

  test('shows a demo completion certificate for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'certificate-showcase-job',
        videoId: 'certificate-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoCompletionCertificate').mockResolvedValue(
      demoCompletionCertificateFixture({
        jobId: 'certificate-showcase-job',
        videoId: 'certificate-video'
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'certificate-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const certificate = await screen.findByRole('region', { name: /demo completion certificate/i });
    expect(within(certificate).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(certificate).getByText('tears-showcase completion certificate for zh-CN (READY)')).toBeInTheDocument();
    expect(within(certificate).getByText(/final demo handoff evidence/i)).toBeInTheDocument();
    expect(within(certificate).getByText('Reproducibility')).toBeInTheDocument();
    expect(within(certificate).getByText('Export this replay card: LINGUAFRAME_DEMO_JOB_ID=certificate-showcase-job scripts/demo/demo-replay-card.sh')).toBeInTheDocument();
    expect(within(certificate).getByRole('link', { name: /completion certificate json/i })).toHaveAttribute(
      'href',
      '/api/jobs/certificate-showcase-job/demo-completion-certificate'
    );
    expect(within(certificate).getByRole('link', { name: /demo run package/i })).toHaveAttribute(
      'href',
      '/api/jobs/certificate-showcase-job/demo-run-package/download'
    );
    expect(linguaFrameApi.getDemoCompletionCertificate).toHaveBeenCalledWith('certificate-showcase-job');
  });

  test('shows a demo acceptance gate for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'acceptance-showcase-job',
        videoId: 'acceptance-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoAcceptanceGate').mockResolvedValue(
      demoAcceptanceGateFixture({
        jobId: 'acceptance-showcase-job',
        videoId: 'acceptance-video'
      })
    );

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'acceptance-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const gate = await screen.findByRole('region', { name: /demo acceptance gate/i });
    expect(within(gate).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(gate).getByText('tears-showcase acceptance gate for zh-CN (READY)')).toBeInTheDocument();
    expect(within(gate).getByText(/Present this run/i)).toBeInTheDocument();
    expect(within(gate).getByText('Playable media outputs')).toBeInTheDocument();
    expect(within(gate).getByText('1 (READY)')).toBeInTheDocument();
    expect(within(gate).getByRole('link', { name: /demo acceptance gate json/i })).toHaveAttribute(
      'href',
      '/api/jobs/acceptance-showcase-job/demo-acceptance-gate'
    );
    expect(within(gate).getByRole('link', { name: /demo run package/i })).toHaveAttribute(
      'href',
      '/api/jobs/acceptance-showcase-job/demo-run-package/download'
    );
    expect(linguaFrameApi.getDemoAcceptanceGate).toHaveBeenCalledWith('acceptance-showcase-job');
  });

  test('shows a demo share sheet for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'share-showcase-job',
        videoId: 'share-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoShareSheet').mockResolvedValue(demoShareSheetFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'share-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const shareSheet = await screen.findByRole('region', { name: /demo share sheet/i });
    expect(within(shareSheet).getByText('READY')).toBeInTheDocument();
    expect(within(shareSheet).getByText('tears-showcase demo to zh-CN')).toBeInTheDocument();
    expect(within(shareSheet).getByText('Open the demo run package.')).toBeInTheDocument();
    expect(within(shareSheet).getByText('Status: COMPLETED')).toBeInTheDocument();
    expect(within(shareSheet).getByRole('link', { name: /download backend markdown/i })).toHaveAttribute(
      'href',
      '/api/jobs/share-showcase-job/demo-share-sheet/markdown/download'
    );
    await userEvent.click(within(shareSheet).getByRole('button', { name: /copy share sheet/i }));
    await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith('# tears-showcase demo to zh-CN\n'));
    expect(linguaFrameApi.getDemoShareSheet).toHaveBeenCalledWith('share-showcase-job');
  });

  test('shows a live demo run monitor for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'monitor-showcase-job',
        videoId: 'monitor-video',
        status: 'PROCESSING',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoRunMonitor').mockResolvedValue(demoRunMonitorFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'monitor-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const monitor = await screen.findByRole('region', { name: /demo run monitor/i });
    expect(within(monitor).getAllByText('RUNNING')).toHaveLength(2);
    expect(within(monitor).getAllByText('TARGET_SUBTITLE_EXPORT')).toHaveLength(2);
    expect(within(monitor).getByText('2 / 12')).toBeInTheDocument();
    expect(within(monitor).getByText(/Keep watching this monitor/i)).toBeInTheDocument();
    expect(within(monitor).getByText(/TRANSCRIPT_SUBTITLE_EXPORT/)).toBeInTheDocument();
    expect(within(monitor).getByRole('link', { name: /download backend markdown/i })).toHaveAttribute(
      'href',
      '/api/jobs/monitor-showcase-job/demo-run-monitor/markdown/download'
    );
    await userEvent.click(within(monitor).getByRole('button', { name: /refresh/i }));
    await waitFor(() => expect(linguaFrameApi.getDemoRunMonitor).toHaveBeenCalledWith('monitor-showcase-job'));
  });

  test('shows a static demo snapshot workspace for selected jobs', async () => {
    vi.spyOn(linguaFrameApi, 'getJob').mockResolvedValue(
      jobFixture({
        jobId: 'snapshot-showcase-job',
        videoId: 'snapshot-video',
        status: 'COMPLETED',
        demoProfileId: 'tears-showcase'
      })
    );
    vi.spyOn(linguaFrameApi, 'listTranscript').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listSubtitles').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'listArtifacts').mockResolvedValue([]);
    vi.spyOn(linguaFrameApi, 'getDemoRunSnapshot').mockResolvedValue(demoRunSnapshotFixture());

    render(<App />);

    await userEvent.type(screen.getByLabelText(/open job id/i), 'snapshot-showcase-job');
    await userEvent.click(screen.getByRole('button', { name: /open job/i }));

    const snapshot = await screen.findByRole('region', { name: /demo snapshot/i });
    expect(within(snapshot).getAllByText('READY').length).toBeGreaterThan(0);
    expect(within(snapshot).getByText('tears-showcase demo to zh-CN')).toBeInTheDocument();
    expect(within(snapshot).getByText('index.html')).toBeInTheDocument();
    expect(within(snapshot).getByText('demo-share-sheet.md')).toBeInTheDocument();
    expect(within(snapshot).getByText('transcript content')).toBeInTheDocument();
    expect(within(snapshot).getByRole('link', { name: /download static snapshot zip/i })).toHaveAttribute(
      'href',
      '/api/jobs/snapshot-showcase-job/demo-run-snapshot/download'
    );
    await userEvent.click(within(snapshot).getByRole('button', { name: /refresh/i }));
    await waitFor(() => expect(linguaFrameApi.getDemoRunSnapshot).toHaveBeenCalledWith('snapshot-showcase-job'));
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
    expect(within(delivery).getByText('8 missing')).toBeInTheDocument();
    expect(within(delivery).getByText('Transcript JSON')).toBeInTheDocument();
    expect(within(delivery).getAllByText('Preview only')).toHaveLength(6);
    expect(within(delivery).getByText('Dubbing audio')).toBeInTheDocument();
    expect(within(delivery).getByText('Dubbed video')).toBeInTheDocument();
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
    ownerId: 'demo-owner',
    ownershipScope: 'CONFIGURED_DEMO_OWNER',
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

function modelUsageLedgerFixture(overrides: Partial<ModelUsageLedger> = {}): ModelUsageLedger {
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
    safetyNotes: ['Raw media object keys, prompts, provider responses, and secrets are intentionally excluded.'],
    ...overrides
  };
}

function demoSessionCommandCenterFixture(
  overrides: Partial<DemoSessionCommandCenter> = {}
): DemoSessionCommandCenter {
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
      currentStage: 'COMPLETED',
      elapsedMs: 42000,
      nextAction: 'Use the demo presenter pack.'
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
      },
      {
        id: 'cockpit',
        label: 'Presentation cockpit',
        status: 'READY',
        detail: 'Cockpit is ready to present.',
        nextAction: 'Open presenter evidence.',
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
      },
      {
        label: 'Model usage ledger',
        href: '/api/operator/model-usage-ledger',
        contentType: 'application/json',
        description: 'Cost and latency evidence.'
      }
    ],
    estimatedCostUsd: '0.00020000',
    modelCallCount: 2,
    failedModelCallCount: 0,
    failureRatePercent: '0.00',
    averageLatencyMs: 120,
    providerCacheHitCount: 1,
    safetyNotes: ['Demo session command center is metadata-only and read-only.'],
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

function privateDemoLaunchRehearsalFixture(
  overrides: Partial<PrivateDemoLaunchRehearsal> = {}
): PrivateDemoLaunchRehearsal {
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
      },
      {
        id: 'openai-preflight',
        title: 'OpenAI provider preflight',
        status: 'ATTENTION',
        detail: 'Provider-backed demo readiness needs manual confirmation.',
        command: 'LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-demo-preflight.sh',
        evidencePath: '/api/runtime/live-checks',
        nextAction: 'Run only when you intend to prove paid provider access.',
        blocking: false
      },
      {
        id: 'full-tears-demo',
        title: 'Full Tears of Steel demo',
        status: 'ATTENTION',
        detail: 'Processes the complete public demo sample.',
        command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
        evidencePath: '/tmp/linguaframe-demo/tears-of-steel-full',
        nextAction: 'Run after the short smoke path passes.',
        blocking: false
      }
    ],
    evidenceDownloads: [
      '/api/operator/private-demo/operations',
      '/api/jobs/{jobId}/demo-presenter-pack'
    ],
    rehearsalNotesMarkdown: '# LinguaFrame Private Demo Launch Rehearsal\n',
    ...overrides
  };
}

function privateDemoEvidenceGalleryFixture(
  overrides: Partial<PrivateDemoEvidenceGallery> = {}
): PrivateDemoEvidenceGallery {
  return {
    generatedAt: '2026-06-28T08:45:00Z',
    overallStatus: 'READY',
    completedJobCount: 2,
    handoffReadyCount: 1,
    recommendedJobId: 'job-gallery-best',
    jobs: [
      {
        jobId: 'job-gallery-best',
        videoId: 'video-gallery',
        filename: 'tears-demo.mp4',
        targetLanguage: 'zh-CN',
        demoProfileId: 'tears-showcase',
        status: 'COMPLETED',
        createdAt: '2026-06-28T08:00:00Z',
        completedAt: '2026-06-28T08:30:00Z',
        qualityScore: 94,
        qualityVerdict: 'EXCELLENT',
        estimatedCostUsd: '0.40',
        modelCallCount: 5,
        providerCacheHitCount: 1,
        handoffReady: true,
        presenterPackReady: true,
        recommended: true,
        attentionReasons: [],
        downloads: [
          {
            label: 'Demo run package',
            href: '/api/jobs/job-gallery-best/demo-run-package/download',
            contentType: 'application/zip',
            description: 'Complete safe demo run package.'
          },
          {
            label: 'AI audit package',
            href: '/api/jobs/job-gallery-best/ai-audit-package/download',
            contentType: 'application/zip',
            description: 'Prompt and model-call audit package.'
          }
        ]
      }
    ],
    galleryDownloads: [
      {
        label: 'Demo run package',
        href: '/api/jobs/job-gallery-best/demo-run-package/download',
        contentType: 'application/zip',
        description: 'Complete safe demo run package.'
      }
    ],
    galleryNotesMarkdown: '# LinguaFrame Private Demo Evidence Gallery\n',
    ...overrides
  };
}

function privateDemoRunArchiveFixture(
  overrides: Partial<PrivateDemoRunArchive> = {}
): PrivateDemoRunArchive {
  return {
    generatedAt: '2026-06-28T08:50:00Z',
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
    candidates: [
      {
        jobId: 'job-gallery-best',
        videoId: 'video-gallery',
        filename: 'tears-demo.mp4',
        profileId: 'tears-showcase',
        status: 'COMPLETED',
        readiness: 'READY',
        qualityScore: 94,
        estimatedCostUsd: '0.40',
        modelCallCount: 5,
        providerCacheHitCount: 1,
        handoffReady: true,
        roles: ['RECOMMENDED', 'HANDOFF_READY']
      }
    ],
    archiveLinks: [
      {
        label: 'Operations readiness',
        href: '/api/operator/private-demo/operations',
        contentType: 'application/json',
        description: 'Private demo readiness.'
      },
      {
        label: 'Demo run package',
        href: '/api/jobs/job-gallery-best/demo-run-package/download',
        contentType: 'application/zip',
        description: 'Complete safe demo run package.'
      }
    ],
    archiveNotesMarkdown: '# LinguaFrame Private Demo Run Archive\nRecommended job: job-gallery-best\n',
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
        dispatchIntervalMs: 5000,
        listenerQueue: 'linguaframe.localization.jobs',
        jobExchange: 'linguaframe.jobs',
        defaultJobQueue: 'linguaframe.localization.jobs',
        defaultRoutingKey: 'localization.queued',
        ffmpegJobQueue: 'linguaframe.localization.jobs',
        ffmpegRoutingKey: 'localization.queued',
        openaiJobQueue: 'linguaframe.localization.openai.jobs',
        openaiRoutingKey: 'localization.openai',
        ownedStageGroups: [
          'COMBINED:ALL',
          'FFMPEG:WORKER_SMOKE,AUDIO_EXTRACTION,SUBTITLE_BURN_IN,DUBBED_VIDEO_DELIVERY,ARTIFACT_SUMMARY',
          'OPENAI:TRANSCRIPT_SUBTITLE_EXPORT,TARGET_SUBTITLE_EXPORT,SUBTITLE_POLISHING,QUALITY_EVALUATION,DUBBING_AUDIO_GENERATION'
        ],
        recommendedCommands: [
          'LINGUAFRAME_WORKER_ROLE=COMBINED docker compose --env-file .env up -d linguaframe-backend',
          'LINGUAFRAME_WORKER_ROLE=FFMPEG LINGUAFRAME_RABBITMQ_LISTENER_QUEUE=linguaframe.localization.jobs docker compose --env-file .env up -d linguaframe-backend',
          'LINGUAFRAME_WORKER_ROLE=OPENAI LINGUAFRAME_RABBITMQ_LISTENER_QUEUE=linguaframe.localization.openai.jobs docker compose --env-file .env up -d linguaframe-backend'
        ]
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
      ownerQuota: {
        enabled: false,
        maxActiveJobs: 0,
        maxQueuedJobs: 0,
        dailyBudgetGuardEnabled: false,
        maxDailyCostUsd: 0
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
        dailyBudgetGuard: { enabled: false },
        ownerQuota: { enabled: false }
      }
    },
    ...overrides
  };
}

function ownerQuotaPreflightFixture(
  overrides: Partial<OwnerQuotaPreflight> = {}
): OwnerQuotaPreflight {
  return {
    ownerId: 'demo-owner',
    enabled: false,
    allowed: true,
    activeJobs: 0,
    queuedJobs: 0,
    dailyEstimatedCostUsd: 0,
    dailyBudgetDate: '2026-06-28',
    limits: [
      { name: 'activeJobs', enabled: false, limit: 0, current: 0 },
      { name: 'queuedJobs', enabled: false, limit: 0, current: 0 },
      { name: 'dailyCostUsd', enabled: false, limit: 0, current: 0 }
    ],
    blockingReasons: [],
    ...overrides
  };
}

function demoUploadReadinessFixture(
  overrides: Partial<DemoUploadReadiness> = {}
): DemoUploadReadiness {
  return {
    overallStatus: 'READY',
    ownerId: 'demo-owner',
    demoProfileId: 'quick-baseline',
    generatedAt: '2026-06-28T08:00:00Z',
    checks: [
      {
        id: 'owner-session',
        label: 'Owner session',
        status: 'READY',
        detail: 'Demo access gate is open.',
        nextAction: 'No owner-session action required.',
        blocking: false
      },
      {
        id: 'owner-quota',
        label: 'Owner quota',
        status: 'READY',
        detail: 'Owner quota allows upload.',
        nextAction: 'No owner quota action required.',
        blocking: false
      }
    ],
    requiredActions: ['Upload can start after file validation passes.'],
    evidenceRoutes: ['/api/media/uploads/readiness', '/api/media/uploads/preflight'],
    ...overrides
  };
}

function demoSampleMediaCatalogFixture(
  overrides: Partial<DemoSampleMediaCatalog> = {}
): DemoSampleMediaCatalog {
  return {
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
        attribution: 'Credit Blender Studio / Tears of Steel.',
        licenseGuidance: 'Check the Blender Studio page before sharing.',
        recommendedUse: 'Best full local product demo.',
        durationGuidance: 'Current local casting sample is intended to stay complete and under 300 seconds.',
        command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
        tags: ['recommended', 'dialogue']
      },
      {
        id: 'big-buck-bunny-w3schools',
        title: 'Big Buck Bunny / W3Schools sample',
        source: 'W3Schools sample video',
        sourceUrl: 'https://www.w3schools.com/html/mov_bbb.mp4',
        attribution: 'Courtesy of Big Buck Bunny.',
        licenseGuidance: 'Confirm attribution before external presentation.',
        recommendedUse: 'Fast upload and pipeline check.',
        durationGuidance: 'Short sample should fit within 300 seconds.',
        command: 'LINGUAFRAME_DEMO_SAMPLE_PATH=/path/to/mov_bbb.mp4 scripts/demo/docker-e2e-success.sh',
        tags: ['quick-smoke']
      }
    ],
    configuredPaths: [
      {
        envVar: 'LINGUAFRAME_TEARS_SAMPLE_PATH',
        status: 'UNCONFIGURED',
        filename: '',
        extension: '',
        sizeBytes: null,
        message: 'Sample path is not configured.',
        fullPathExposed: false
      }
    ],
    commands: [
      {
        label: 'Run full Tears sample',
        command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
        description: 'Process the configured complete Tears sample.'
      }
    ],
    notesMarkdown: '# LinguaFrame Demo Sample Media Catalog',
    documentationLinks: [
      {
        label: 'Demo references',
        path: 'docs/product/demo-references.md',
        detail: 'Public sample sources.'
      }
    ],
    ...overrides
  };
}

function demoRunLauncherFixture(
  overrides: Partial<DemoRunLauncher> = {}
): DemoRunLauncher {
  return {
    generatedAt: '2026-06-29T08:05:00Z',
    overallStatus: 'ATTENTION',
    recommendedSampleId: 'tears-of-steel-casting',
    recommendedProfileId: 'tears-showcase',
    recommendedNextCommand: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
    gates: [
      {
        id: 'sample-media',
        label: 'Sample media',
        status: 'ATTENTION',
        detail: 'Recommended Tears sample is not configured for the full demo.',
        nextAction: 'Set LINGUAFRAME_TEARS_SAMPLE_PATH before a full run.',
        blocking: false
      },
      {
        id: 'upload-readiness',
        label: 'Upload readiness',
        status: 'READY',
        detail: 'Upload readiness for profile tears-showcase is READY.',
        nextAction: 'Validate the selected media file before upload.',
        blocking: false
      }
    ],
    commands: [
      {
        label: 'Inspect launcher',
        command: 'scripts/demo/demo-run-launcher.sh',
        description: 'Download this read-only launcher contract.'
      },
      {
        label: 'Check OpenAI preflight',
        command: 'scripts/demo/openai-demo-preflight.sh',
        description: 'Verify provider-backed demo configuration.'
      },
      {
        label: 'Run full Tears demo',
        command: 'scripts/demo/docker-e2e-tears-of-steel-full.sh',
        description: 'Process the configured complete Tears sample.'
      }
    ],
    expectedEvidence: [
      {
        label: 'Job detail JSON',
        path: '/tmp/linguaframe-demo/full-tears/job-detail.json',
        description: 'Terminal job-detail snapshot.'
      },
      {
        label: 'Demo presenter pack',
        path: '/tmp/linguaframe-demo/full-tears/demo-presenter-pack.json',
        description: 'Presenter-facing metadata.'
      },
      {
        label: 'Demo run snapshot ZIP',
        path: '/tmp/linguaframe-demo/full-tears/demo-run-snapshot.zip',
        description: 'Safe reviewer package.'
      }
    ],
    notesMarkdown: '# LinguaFrame Demo Run Launcher',
    ...overrides
  };
}

function demoPresentationCockpitFixture(
  overrides: Partial<DemoPresentationCockpit> = {}
): DemoPresentationCockpit {
  return {
    generatedAt: '2026-06-29T08:10:00Z',
    overallStatus: 'ATTENTION',
    phase: 'READY_FOR_UPLOAD',
    recommendedNextAction: 'Run the full demo command after readiness checks.',
    selectedRun: null,
    activeRun: null,
    recommendedRun: {
      jobId: 'job-gallery-best',
      videoId: 'video-gallery',
      profileId: 'tears-showcase',
      status: 'COMPLETED',
      readiness: 'READY',
      acceptanceStatus: 'READY',
      attentionLevel: 'NONE',
      currentStage: 'COMPLETED',
      elapsedMs: 65000,
      nextAction: 'Open presenter evidence.'
    },
    checks: [
      {
        key: 'DEMO_RUN_LAUNCHER',
        label: 'Demo run launcher',
        status: 'READY',
        detail: 'Launcher has a recommended sample and profile.',
        nextAction: 'Use the recommended full demo command.',
        blocking: false
      },
      {
        key: 'ACCEPTANCE_GATE',
        label: 'Acceptance gate',
        status: 'READY',
        detail: 'Recommended run passes presentation checks.',
        nextAction: 'Use the presenter pack.',
        blocking: false
      }
    ],
    links: [
      {
        kind: 'PRESENTER_PACK',
        label: 'Presenter pack',
        url: '/api/jobs/job-selected/demo-presenter-pack'
      },
      {
        kind: 'ACCEPTANCE_GATE',
        label: 'Acceptance gate',
        url: '/api/jobs/job-selected/demo-acceptance-gate'
      }
    ],
    safetyNotes: [
      'Metadata-only cockpit: only IDs, statuses, counts, readiness labels, and safe routes are included.'
    ],
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

function openAiReadinessEvidenceFixture(
  overrides: Partial<OpenAiReadinessEvidence> = {}
): OpenAiReadinessEvidence {
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
      },
      {
        label: 'OpenAI smoke runner',
        command: 'LINGUAFRAME_ENV_FILE=.env.openai-demo LINGUAFRAME_DEMO_SAMPLE_PATH=<short-speech.mp4> scripts/demo/docker-e2e-openai-smoke.sh',
        description: 'Run the paid provider-backed smoke path.'
      }
    ],
    safeLinks: ['/api/operator/openai-readiness-evidence/markdown/download'],
    safetyNotes: ['API keys, bearer tokens, demo tokens, provider payloads, and media bytes are intentionally excluded.'],
    ...overrides
  };
}

function demoSessionStatusFixture(overrides: Partial<DemoSessionStatus> = {}): DemoSessionStatus {
  return {
    accessGateEnabled: false,
    authenticated: true,
    headerName: 'X-LinguaFrame-Demo-Token',
    mode: 'OPEN',
    ownerId: 'demo-owner',
    ownershipScope: 'CONFIGURED_DEMO_OWNER',
    ...overrides
  };
}

function authSessionStatusFixture(overrides: Partial<AuthSessionStatus> = {}): AuthSessionStatus {
  return {
    enabled: false,
    configured: false,
    authenticated: false,
    ownerId: 'demo-owner',
    username: 'owner',
    ownershipScope: 'CONFIGURED_DEMO_OWNER',
    authMode: 'LOCAL_AUTH_DISABLED',
    ...overrides
  };
}

function authLoginResponseFixture(overrides: Partial<AuthLoginResponse> = {}): AuthLoginResponse {
  return {
    token: 'jwt-token',
    tokenType: 'Bearer',
    expiresAt: '2026-06-28T13:00:00Z',
    session: authSessionStatusFixture({
      enabled: true,
      configured: true,
      authenticated: true,
      ownerId: 'owner-alpha',
      username: 'owner',
      authMode: 'LOCAL_AUTH_ACTIVE'
    }),
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

function demoPresenterPackFixture(overrides: Partial<DemoPresenterPack> = {}): DemoPresenterPack {
  return {
    anchorJobId: 'presenter-showcase-job',
    videoId: 'presenter-video',
    generatedAt: '2026-06-28T12:00:00Z',
    headline: 'tears-showcase demo to zh-CN',
    readinessStatus: 'READY',
    recommendedBaselineJobId: 'presenter-baseline-job',
    bestQualityJobId: 'presenter-showcase-job',
    lowestCostJobId: 'presenter-baseline-job',
    runs: [
      {
        jobId: 'presenter-showcase-job',
        demoProfileId: 'tears-showcase',
        status: 'COMPLETED',
        completedAt: '2026-06-28T11:03:00Z',
        qualityScore: 91,
        estimatedCostUsd: 0.000141,
        modelCallCount: 2,
        providerCacheHitCount: 1,
        handoffReady: true,
        roles: ['ANCHOR', 'BEST_QUALITY']
      },
      {
        jobId: 'presenter-baseline-job',
        demoProfileId: 'quick-baseline',
        status: 'COMPLETED',
        completedAt: '2026-06-28T10:03:00Z',
        qualityScore: 82,
        estimatedCostUsd: 0.000063,
        modelCallCount: 1,
        providerCacheHitCount: 0,
        handoffReady: true,
        roles: ['RECOMMENDED_BASELINE', 'LOWEST_COST']
      }
    ],
    downloads: [
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        url: '/api/jobs/presenter-showcase-job/demo-run-package/download'
      },
      {
        kind: 'AI_AUDIT_PACKAGE',
        label: 'AI audit package',
        url: '/api/jobs/presenter-showcase-job/ai-audit-package/download'
      }
    ],
    presenterNotesMarkdown: '# LinguaFrame Demo Presenter Pack\n- Anchor job: presenter-showcase-job\n',
    ...overrides
  };
}

function demoReplayCardFixture(overrides: Partial<DemoReplayCard> = {}): DemoReplayCard {
  return {
    jobId: 'replay-showcase-job',
    videoId: 'replay-video',
    generatedAt: '2026-06-29T10:15:30Z',
    headline: 'tears-showcase replay card to zh-CN',
    readiness: 'READY',
    status: 'COMPLETED',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    qualityScore: 91,
    qualityVerdict: 'GOOD',
    modelCallCount: 2,
    providerCacheHitCount: 1,
    artifactCacheHitCount: 0,
    estimatedCostUsd: 0.000141,
    recommendedBaselineJobId: 'replay-baseline-job',
    bestQualityJobId: 'replay-showcase-job',
    lowestCostJobId: 'replay-baseline-job',
    settings: [
      {
        key: 'targetLanguage',
        label: 'Target language',
        value: 'zh-CN'
      },
      {
        key: 'demoProfileId',
        label: 'Demo profile',
        value: 'tears-showcase'
      },
      {
        key: 'translationGlossary',
        label: 'Glossary',
        value: '3 entries / abc123'
      }
    ],
    commands: [
      {
        kind: 'TEARS_FULL_REPLAY',
        label: 'Full Tears of Steel replay',
        command: 'LINGUAFRAME_DEMO_PROFILE_ID=tears-showcase scripts/demo/docker-e2e-tears-of-steel-full.sh',
        note: 'Set LINGUAFRAME_TEARS_SAMPLE_PATH if the sample is not in the default location.'
      },
      {
        kind: 'EXPORT_REPLAY_CARD',
        label: 'Export this replay card',
        command: 'LINGUAFRAME_DEMO_JOB_ID=replay-showcase-job scripts/demo/demo-replay-card.sh',
        note: 'Writes the metadata-only replay card JSON under /tmp/linguaframe-demo/demo-replay-card.'
      }
    ],
    links: [
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        url: '/api/jobs/replay-showcase-job/demo-run-package/download'
      }
    ],
    safetyNotes: [
      'Metadata only: no API keys, object storage credentials, raw prompts, or media bytes are included.',
      'Local source paths are intentionally omitted; choose the source file again before replaying.'
    ],
    ...overrides
  };
}

function demoCompletionCertificateFixture(
  overrides: Partial<DemoCompletionCertificate> = {}
): DemoCompletionCertificate {
  const jobId = overrides.jobId ?? 'certificate-showcase-job';
  return {
    jobId,
    videoId: overrides.videoId ?? 'certificate-video',
    generatedAt: '2026-06-29T10:45:00Z',
    certificateStatus: 'READY',
    jobStatus: 'COMPLETED',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    headline: 'tears-showcase completion certificate for zh-CN (READY)',
    summary: `Job ${jobId} is COMPLETED with certificateStatus=READY.`,
    recommendedNextAction: 'Use the completion certificate, demo run package, and snapshot as final demo handoff evidence.',
    recommendedBaselineJobId: 'certificate-baseline-job',
    bestQualityJobId: jobId,
    lowestCostJobId: 'certificate-baseline-job',
    checks: [
      {
        key: 'JOB_COMPLETED',
        label: 'Job completed',
        status: 'PASS',
        detail: 'Job status is COMPLETED.',
        blocking: false
      },
      {
        key: 'HANDOFF_READY',
        label: 'Reviewed handoff ready',
        status: 'PASS',
        detail: 'Delivery manifest handoffReady=true.',
        blocking: false
      }
    ],
    sections: [
      {
        key: 'RUN_IDENTITY',
        title: 'Run identity',
        status: 'READY',
        facts: [`Job: ${jobId}`, 'Video: certificate-video', 'Target language: zh-CN']
      },
      {
        key: 'REPRODUCIBILITY',
        title: 'Reproducibility',
        status: 'READY',
        facts: [
          'Replay readiness: READY',
          `Export this replay card: LINGUAFRAME_DEMO_JOB_ID=${jobId} scripts/demo/demo-replay-card.sh`
        ]
      },
      {
        key: 'EVIDENCE',
        title: 'Evidence packages',
        status: 'READY',
        facts: ['Presenter downloads: 10', 'Snapshot entries: 11']
      }
    ],
    links: [
      {
        kind: 'CERTIFICATE_JSON',
        label: 'Completion certificate JSON',
        url: `/api/jobs/${jobId}/demo-completion-certificate`
      },
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        url: `/api/jobs/${jobId}/demo-run-package/download`
      }
    ],
    safetyNotes: [
      'Metadata-only certificate: only IDs, status, readiness, costs, counts, safe routes, and replay commands are included.',
      'The certificate is generated on demand from existing safe evidence routes and does not create new artifacts.'
    ],
    ...overrides
  };
}

function demoAcceptanceGateFixture(overrides: Partial<DemoAcceptanceGate> = {}): DemoAcceptanceGate {
  const jobId = overrides.jobId ?? 'acceptance-showcase-job';
  return {
    jobId,
    videoId: overrides.videoId ?? 'acceptance-video',
    generatedAt: '2026-06-29T11:00:00Z',
    gateStatus: 'READY',
    jobStatus: 'COMPLETED',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    headline: 'tears-showcase acceptance gate for zh-CN (READY)',
    summary: `Job ${jobId} is COMPLETED with gateStatus=READY, failedChecks=0, warningChecks=0.`,
    recommendedNextAction: 'Present this run using the completion certificate, demo run package, and snapshot.',
    checks: [
      {
        key: 'JOB_COMPLETED',
        label: 'Job completed',
        status: 'PASS',
        detail: 'Job status is COMPLETED.',
        required: true
      },
      {
        key: 'MEDIA_OUTPUT_AVAILABLE',
        label: 'Playable media output available',
        status: 'PASS',
        detail: 'Playable/downloadable media output count is 1.',
        required: true
      },
      {
        key: 'COMPLETION_CERTIFICATE_READY',
        label: 'Completion certificate ready',
        status: 'PASS',
        detail: 'Completion certificate status is READY.',
        required: true
      }
    ],
    evidence: [
      {
        key: 'MEDIA_OUTPUT_COUNT',
        label: 'Playable media outputs',
        value: '1',
        status: 'READY'
      },
      {
        key: 'QUALITY_SCORE',
        label: 'Quality score',
        value: '91',
        status: 'READY'
      },
      {
        key: 'CERTIFICATE_STATUS',
        label: 'Completion certificate',
        value: 'READY',
        status: 'READY'
      }
    ],
    links: [
      {
        kind: 'ACCEPTANCE_GATE_JSON',
        label: 'Demo acceptance gate JSON',
        url: `/api/jobs/${jobId}/demo-acceptance-gate`
      },
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        url: `/api/jobs/${jobId}/demo-run-package/download`
      }
    ],
    safetyNotes: [
      'Metadata-only gate: only IDs, status, counts, scores, costs, safe routes, and readiness labels are included.',
      'The gate is generated on demand from existing safe evidence surfaces and does not create artifacts or call providers.'
    ],
    ...overrides
  };
}

function demoShareSheetFixture(overrides: Partial<DemoShareSheet> = {}): DemoShareSheet {
  return {
    jobId: 'share-showcase-job',
    videoId: 'share-video',
    generatedAt: '2026-06-28T12:00:00Z',
    readiness: 'READY',
    headline: 'tears-showcase demo to zh-CN',
    summary: 'Completed demo share sheet.',
    outcomeBullets: [
      'Status: COMPLETED',
      'Quality score: 91 (GOOD)',
      'Model calls: 2, estimated cost: 0.00014100 USD'
    ],
    recommendedNextAction: 'Open the demo run package.',
    links: [
      {
        kind: 'DEMO_RUN_PACKAGE',
        label: 'Demo run package',
        url: '/api/jobs/share-showcase-job/demo-run-package/download'
      }
    ],
    markdown: '# tears-showcase demo to zh-CN\n',
    ...overrides
  };
}

function demoRunMonitorFixture(overrides: Partial<DemoRunMonitor> = {}): DemoRunMonitor {
  return {
    jobId: 'monitor-showcase-job',
    videoId: 'monitor-video',
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
    summary: 'Localization job is running at TARGET_SUBTITLE_EXPORT.',
    recommendedNextAction: 'Keep watching this monitor until the job reaches a terminal status.',
    stages: [
      {
        stage: 'WORKER_RECEIVED',
        status: 'SUCCEEDED',
        startedAt: '2026-06-29T09:50:00Z',
        finishedAt: '2026-06-29T09:50:01Z',
        durationMs: 1000,
        runningForMs: null,
        attention: 'OK',
        message: 'Stage completed.'
      },
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
        url: '/api/jobs/monitor-showcase-job'
      }
    ],
    markdown: '# LinguaFrame Demo Run Monitor\n',
    ...overrides
  };
}

function demoRunSnapshotFixture(overrides: Partial<DemoRunSnapshot> = {}): DemoRunSnapshot {
  return {
    jobId: 'snapshot-showcase-job',
    videoId: 'snapshot-video',
    targetLanguage: 'zh-CN',
    demoProfileId: 'tears-showcase',
    generatedAt: '2026-06-29T12:00:00Z',
    readiness: 'READY',
    headline: 'tears-showcase demo to zh-CN',
    summary: 'This offline reviewer snapshot captures metadata-only evidence for job snapshot-showcase-job while it is COMPLETED.',
    sections: [
      {
        kind: 'INDEX_HTML',
        title: 'Offline index',
        status: 'READY',
        filename: 'index.html',
        summary: 'Self-contained HTML entry point for reviewers.'
      },
      {
        kind: 'SHARE_SHEET',
        title: 'Demo share sheet',
        status: 'READY',
        filename: 'demo-share-sheet.md',
        summary: 'Reviewer-facing summary.'
      }
    ],
    packageEntries: [
      'index.html',
      'manifest.json',
      'README.md',
      'demo-share-sheet.md',
      'demo-share-sheet.json'
    ],
    links: [
      {
        kind: 'DEMO_RUN_SNAPSHOT_DOWNLOAD',
        label: 'Static demo snapshot ZIP',
        url: '/api/jobs/snapshot-showcase-job/demo-run-snapshot/download'
      }
    ],
    exclusionPolicy: ['media bytes', 'transcript content', 'subtitle content', 'provider request bodies'],
    markdown: '# LinguaFrame Demo Snapshot\n',
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

function narrationWorkspaceFixture(overrides: Partial<NarrationWorkspace> = {}): NarrationWorkspace {
  return {
    jobId: 'narration-mix-job',
    status: 'READY',
    segmentCount: 2,
    totalDurationSeconds: 28.5,
    totalCharacterCount: 49,
    generationReady: true,
    mixSettings: {
      duckingVolume: 0.35,
      narrationVolume: 1,
      fadeDurationMs: 250,
      updatedAt: null
    },
    voiceCatalog: {
      provider: 'openai',
      defaultVoice: 'verse',
      presets: [
        {
          voice: 'alloy',
          label: 'Alloy',
          provider: 'openai',
          defaultPreset: false,
          description: 'OpenAI TTS voice preset.'
        },
        {
          voice: 'verse',
          label: 'Verse',
          provider: 'openai',
          defaultPreset: true,
          description: 'OpenAI TTS voice preset.'
        }
      ],
      safetyNotes: ['Voice presets are provider identifiers, not uploaded reference audio.']
    },
    timeline: {
      startSeconds: 15,
      endSeconds: 70.5,
      totalSpanSeconds: 55.5,
      coveredSeconds: 28.5,
      gapSeconds: 27,
      gapCount: 1,
      hasOverlap: false,
      generationReady: true,
      segments: [
        {
          index: 0,
          startSeconds: 15,
          endSeconds: 28,
          durationSeconds: 13,
          leftPercent: 0,
          widthPercent: 23.42,
          status: 'READY',
          characterCount: 24,
          voice: 'alloy'
        },
        {
          index: 1,
          startSeconds: 55,
          endSeconds: 70.5,
          durationSeconds: 15.5,
          leftPercent: 72.07,
          widthPercent: 27.93,
          status: 'READY',
          characterCount: 25,
          voice: 'verse'
        }
      ]
    },
    segments: [
      {
        index: 0,
        startSeconds: 15,
        endSeconds: 28,
        durationSeconds: 13,
        text: 'Explain the first scene.',
        voice: 'alloy',
        characterCount: 24,
        updatedAt: '2026-06-29T10:00:00Z'
      },
      {
        index: 1,
        startSeconds: 55,
        endSeconds: 70.5,
        durationSeconds: 15.5,
        text: 'Explain the second scene.',
        voice: 'verse',
        characterCount: 25,
        updatedAt: '2026-06-29T10:00:00Z'
      }
    ],
    safetyNotes: ['Narration scripts stay in the workspace only.'],
    ...overrides
  };
}

function narrationScriptPackageFixture(overrides: Partial<NarrationScriptPackage> = {}): NarrationScriptPackage {
  return {
    jobId: 'narration-package-job',
    targetLanguage: 'zh-CN',
    durationSeconds: 90,
    status: 'READY',
    segmentCount: 2,
    totalCharacterCount: 49,
    totalTimelineDurationSeconds: 28.5,
    timelineGapCount: 1,
    timelineGapSeconds: 27,
    timelineHasOverlap: false,
    voiceSummary: 'PRESET:alloy',
    defaultVoice: 'verse',
    mixSettings: {
      duckingVolume: 0.35,
      narrationVolume: 1,
      fadeDurationMs: 250,
      updatedAt: null
    },
    voiceCatalog: narrationWorkspaceFixture().voiceCatalog,
    segments: [
      {
        index: 0,
        startSeconds: 15,
        endSeconds: 28,
        durationSeconds: 13,
        text: 'Explain the first scene.',
        voice: 'alloy',
        characterCount: 24,
        updatedAt: '2026-06-29T10:00:00Z'
      },
      {
        index: 1,
        startSeconds: 55,
        endSeconds: 70.5,
        durationSeconds: 15.5,
        text: 'Explain the second scene.',
        voice: 'verse',
        characterCount: 25,
        updatedAt: '2026-06-29T10:00:00Z'
      }
    ],
    checks: [
      {
        key: 'SCRIPT_SEGMENTS',
        label: 'Script segments',
        status: 'READY',
        detail: '2 narration segments saved.'
      }
    ],
    safeLinks: [
      {
        kind: 'NARRATION_SCRIPT_PACKAGE',
        label: 'Narration script package',
        href: '/api/jobs/narration-package-job/narration-script-package',
        contentType: 'application/json'
      }
    ],
    packageEntries: ['manifest.json', 'narration-script-package.json', 'narration-script-package.md', 'README.md'],
    safetyNotes: ['This explicit package includes operator-authored narration text.'],
    ...overrides
  };
}

function narrationDemoPresetFixture(overrides: Partial<NarrationDemoPreset> = {}): NarrationDemoPreset {
  return {
    id: 'tears-showcase-narration',
    label: 'Tears showcase narration',
    description: 'Reusable explanatory narration for the Tears of Steel demo run.',
    profileId: 'tears-showcase',
    sampleIdHint: 'tears-of-steel-casting',
    targetLanguage: 'zh-CN',
    voiceSummary: 'DEFAULT',
    segmentCount: 4,
    totalCharacterCount: 128,
    timeSpanSeconds: 165,
    mixSettings: {
      duckingVolume: 0.35,
      narrationVolume: 1,
      fadeDurationMs: 250
    },
    segments: [
      {
        index: 0,
        startSeconds: 15,
        endSeconds: 28,
        durationSeconds: 13,
        text: 'Preset first explanation.',
        characterCount: 25,
        voice: null
      }
    ],
    safetyNotes: ['Preset text is operator-authored narration intended for workspace restoration.'],
    ...overrides
  };
}

function narrationEvidenceFixture(overrides: Partial<NarrationEvidence> = {}): NarrationEvidence {
  const readyAudio = overrides.narrationAudioReady ?? true;
  const readyVideo = overrides.narratedVideoReady ?? true;
  return {
    jobId: 'narration-mix-job',
    status: readyAudio && readyVideo ? 'READY' : 'ATTENTION',
    segmentCount: 2,
    totalCharacterCount: 49,
    totalTimelineDurationSeconds: 28.5,
    timelineGapCount: 1,
    timelineGapSeconds: 27,
    timelineHasOverlap: false,
    voicePresetCount: 1,
    voiceSummary: 'PRESET:alloy',
    defaultVoice: 'verse',
    narrationAudioReady: readyAudio,
    audioArtifactCount: overrides.audioArtifactCount ?? (readyAudio ? 1 : 0),
    audioLayout: readyAudio ? 'TIMED_AUDIO_BED' : 'MISSING',
    timeAligned: readyAudio,
    narratedVideoReady: readyVideo,
    narratedVideoArtifactCount: overrides.narratedVideoArtifactCount ?? (readyVideo ? 1 : 0),
    mixMode: readyVideo ? 'DUCKED_ORIGINAL_AUDIO' : 'MISSING',
    duckingVolume: readyVideo ? 0.35 : null,
    narrationVolume: readyVideo ? 1 : null,
    fadeDurationMs: readyVideo ? 250 : 0,
    mixSettingsSource: readyVideo ? 'DEFAULTS' : null,
    checks: [],
    safeLinks: [],
    packageEntries: [],
    safetyNotes: [],
    ...overrides
  };
}

function narrationGenerationFixture(overrides: Partial<NarrationGeneration> = {}): NarrationGeneration {
  return {
    jobId: 'narration-mix-job',
    artifactId: 'narration-audio-artifact',
    filename: 'narration-audio.mp3',
    contentType: 'audio/mpeg',
    sizeBytes: 1024,
    segmentCount: 2,
    totalCharacterCount: 49,
    totalTimelineDurationSeconds: 28.5,
    voiceSummary: 'alloy, verse',
    audioLayout: 'TIMED_AUDIO_BED',
    timeAligned: true,
    ttsCallCount: 2,
    status: 'READY',
    ...overrides
  };
}

function narratedVideoGenerationFixture(overrides: Partial<NarratedVideoGeneration> = {}): NarratedVideoGeneration {
  return {
    jobId: 'narration-mix-job',
    artifactId: 'narrated-video-artifact',
    filename: 'narrated-video.mp4',
    contentType: 'video/mp4',
    sizeBytes: 2048,
    baseVideoType: 'BURNED_VIDEO',
    narrationAudioArtifactId: 'narration-audio-artifact',
    mixMode: 'DUCKED_ORIGINAL_AUDIO',
    duckingVolume: 0.35,
    narrationVolume: 1,
    fadeDurationMs: 250,
    narrationWindowCount: 2,
    status: 'READY',
    ...overrides
  };
}

function narrationDemoRenderResultFixture(overrides: Partial<NarrationDemoRenderResult> = {}): NarrationDemoRenderResult {
  return {
    jobId: 'narration-render-job',
    presetId: 'tears-showcase-narration',
    status: 'READY',
    steps: [
      {
        key: 'PRESET_APPLY',
        label: 'Apply narration preset',
        status: 'SUCCEEDED',
        message: 'Applied preset tears-showcase-narration.'
      },
      {
        key: 'NARRATION_AUDIO',
        label: 'Generate narration audio',
        status: 'SUCCEEDED',
        message: 'Generated narration-audio.mp3.'
      },
      {
        key: 'NARRATED_VIDEO',
        label: 'Generate narrated video',
        status: 'SUCCEEDED',
        message: 'Generated narrated-video.mp4.'
      }
    ],
    presetApply: {
      jobId: 'narration-render-job',
      presetId: 'tears-showcase-narration',
      profileId: 'tears-showcase',
      importedSegmentCount: 4,
      totalCharacterCount: 128,
      voiceSummary: 'DEFAULT:verse',
      replacedExisting: true,
      generatedMedia: false,
      workspace: narrationWorkspaceFixture({ jobId: 'narration-render-job' }),
      scriptPackage: narrationScriptPackageFixture({ jobId: 'narration-render-job' }),
      narrationEvidenceStatus: 'ATTENTION'
    },
    narrationAudio: narrationGenerationFixture({ jobId: 'narration-render-job' }),
    narratedVideo: narratedVideoGenerationFixture({ jobId: 'narration-render-job' }),
    scriptPackage: narrationScriptPackageFixture({ jobId: 'narration-render-job' }),
    narrationEvidence: narrationEvidenceFixture({
      jobId: 'narration-render-job',
      status: 'READY',
      narrationAudioReady: true,
      narratedVideoReady: true
    }),
    generatedArtifactCount: 2,
    ...overrides
  };
}

function narrationDemoRenderPreflightFixture(
  overrides: Partial<NarrationDemoRenderPreflight> = {}
): NarrationDemoRenderPreflight {
  return {
    jobId: 'narration-render-job',
    presetId: 'tears-showcase-narration',
    status: 'ATTENTION',
    checks: [
      {
        key: 'PRESET',
        label: 'Preset',
        status: 'PASS',
        message: 'Preset tears-showcase-narration is available.'
      },
      {
        key: 'REPLACE_CONFIRMATION',
        label: 'Replace confirmation',
        status: 'WARN',
        message: 'Rendering will replace 2 existing narration rows.'
      },
      {
        key: 'TTS_PROVIDER',
        label: 'TTS provider',
        status: 'WARN',
        message: 'Narration render can call the configured openai TTS provider.'
      },
      {
        key: 'NARRATED_VIDEO_INPUT',
        label: 'Narrated video input',
        status: 'PASS',
        message: 'A base video artifact is available.'
      }
    ],
    estimatedSegmentCount: 4,
    estimatedCharacterCount: 128,
    providerMode: 'openai',
    paidProvider: true,
    existingWorkspaceSegmentCount: 2,
    generateNarratedVideo: false,
    requiredConfirmations: ['REPLACE_EXISTING', 'PAID_PROVIDER'],
    safeNextCommand: 'LINGUAFRAME_DEMO_JOB_ID=narration-render-job scripts/demo/narration-demo-render.sh',
    evidenceRoutes: [
      '/api/jobs/narration-render-job/narration-demo/render',
      '/api/jobs/narration-render-job/narration-evidence',
      '/api/jobs/narration-render-job/narration-script-package'
    ],
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
        endMs: 1200,
        sourceText: 'First source line',
        generatedText: '第一行字幕',
        draftText: '第一行字幕',
        edited: false,
        updatedAt: null,
        decision: 'UNREVIEWED',
        issueCategories: [],
        reviewerNote: null,
        noteLength: 0
      },
      {
        index: 1,
        startMs: 1200,
        endMs: 2800,
        sourceText: 'Second source line',
        generatedText: '第二行字幕',
        draftText: '修正后的第二行字幕',
        edited: true,
        updatedAt: '2026-06-28T10:00:00Z',
        decision: 'EDITED',
        issueCategories: ['TERM'],
        reviewerNote: 'Use the glossary term.',
        noteLength: 22
      }
    ],
    ...overrides
  };
}

function reviewedSubtitleWorkflowFixture(
  overrides: Partial<ReviewedSubtitleWorkflow> = {}
): ReviewedSubtitleWorkflow {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    generatedAt: '2026-06-29T14:00:00Z',
    overallStatus: 'ATTENTION',
    phase: 'PUBLISH_READY',
    recommendedNextAction: 'Publish reviewed subtitles.',
    segmentCount: 2,
    missingTargetCount: 0,
    timingMismatchCount: 0,
    qualityScore: 88,
    qualityVerdict: 'NEEDS_REVIEW',
    qualityIssueCount: 1,
    qualitySuggestedFixCount: 1,
    editedSegmentCount: 1,
    draftLastUpdatedAt: '2026-06-28T10:00:00Z',
    generatedSubtitleArtifactCount: 3,
    reviewedSubtitleArtifactCount: 0,
    reviewedBurnedVideoAvailable: false,
    handoffReady: false,
    checks: [
      {
        key: 'JOB_COMPLETED',
        label: 'Job completed',
        status: 'READY',
        detail: 'The job is completed and can enter subtitle review.',
        nextAction: 'Review generated subtitles.',
        blocking: false
      },
      {
        key: 'REVIEWED_SUBTITLE_ARTIFACTS',
        label: 'Reviewed subtitle artifacts',
        status: 'ATTENTION',
        detail: 'Reviewed subtitle artifacts: 0 of 3.',
        nextAction: 'Publish reviewed subtitles.',
        blocking: false
      }
    ],
    links: [
      {
        kind: 'SUBTITLE_REVIEW',
        label: 'Subtitle review',
        url: '/api/jobs/job-1/subtitle-review?language=zh-CN'
      },
      {
        kind: 'PUBLISH_REVIEWED_SUBTITLES',
        label: 'Publish reviewed subtitles',
        url: '/api/jobs/job-1/subtitle-draft/publish'
      },
      {
        kind: 'DELIVERY_MANIFEST',
        label: 'Delivery manifest',
        url: '/api/jobs/job-1/delivery-manifest'
      }
    ],
    safetyNotes: [
      'Metadata-only workflow: IDs, counts, statuses, timestamps, and safe routes are included.'
    ],
    ...overrides
  };
}

function subtitleReviewEvidenceFixture(
  overrides: Partial<SubtitleReviewEvidence> = {}
): SubtitleReviewEvidence {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    generatedAt: '2026-06-29T14:00:00Z',
    status: 'ATTENTION',
    summary: 'Subtitle review evidence exists but still needs reviewer attention.',
    segmentCount: 2,
    reviewedSegmentCount: 1,
    acceptedSegmentCount: 0,
    editedDecisionCount: 1,
    followupSegmentCount: 0,
    annotationCount: 1,
    reviewerNoteCount: 1,
    reviewedSubtitleArtifactCount: 0,
    reviewedBurnedVideoAvailable: false,
    releaseNotesLength: 0,
    decisionCounts: [
      { category: 'UNREVIEWED', count: 1 },
      { category: 'EDITED', count: 1 }
    ],
    issueCategoryCounts: [
      { category: 'TERM', count: 1 }
    ],
    checks: [
      {
        key: 'review-coverage',
        label: 'All subtitle segments reviewed',
        status: 'WARN',
        detail: '1 of 2 subtitle segments have review decisions.'
      }
    ],
    links: [],
    packageEntries: ['manifest.json', 'subtitle-review-evidence.md'],
    safetyNotes: ['Metadata-only evidence.'],
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

function openAiSmokeProofFixture(): OpenAiSmokeProof {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    targetLanguage: 'zh-CN',
    overallStatus: 'READY',
    phase: 'OPENAI_SMOKE_PROVEN',
    recommendedNextAction: 'Use this proof to present the completed OpenAI smoke run.',
    completedAt: '2026-06-26T10:00:04Z',
    requiredChecks: [
      {
        name: 'OpenAI transcription call',
        status: 'READY',
        detail: 'Successful OpenAI TRANSCRIPTION call recorded.',
        nextAction: 'No action required.'
      },
      {
        name: 'OpenAI translation call',
        status: 'READY',
        detail: 'Successful OpenAI TRANSLATION call recorded.',
        nextAction: 'No action required.'
      }
    ],
    optionalChecks: [
      {
        name: 'Quality evaluation',
        status: 'READY',
        detail: 'Quality score 92 / 100, verdict GOOD.',
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
        createdAt: '2026-06-26T10:00:04Z'
      }
    ],
    safeLinks: [
      {
        label: 'AI audit package',
        href: '/api/jobs/job-1/ai-audit-package/download',
        contentType: 'application/zip',
        description: 'Prompt, model-call, usage, and cost audit package.'
      }
    ],
    safetyNotes: ['Metadata only; sensitive content is excluded.']
  };
}

function demoReviewerWorkspaceFixture(): DemoReviewerWorkspace {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    generatedAt: '2026-06-26T10:00:05Z',
    overallStatus: 'READY',
    phase: 'REVIEW_PACKAGE_READY',
    recommendedNextAction: 'Share this metadata-only reviewer workspace with demo reviewers.',
    completedAt: '2026-06-26T10:00:04Z',
    targetLanguage: 'zh-CN',
    demoProfileId: 'openai-smoke',
    sections: [
      {
        key: 'run',
        title: 'Run summary',
        status: 'READY',
        facts: ['Completed job job-1.', 'Target language zh-CN.']
      }
    ],
    checks: [
      {
        key: 'job-completed',
        label: 'Job completed',
        status: 'READY',
        detail: 'Job reached COMPLETED.',
        nextAction: 'No action required.',
        required: true
      },
      {
        key: 'openai-smoke-proof',
        label: 'OpenAI smoke proof',
        status: 'READY',
        detail: 'Provider-backed model calls are recorded.',
        nextAction: 'No action required.',
        required: false
      }
    ],
    safeLinks: [
      {
        kind: 'package',
        label: 'Demo run package',
        href: '/api/jobs/job-1/demo-run-package/download',
        contentType: 'application/zip',
        description: 'Metadata and links for the completed demo run.'
      }
    ],
    packageEntries: ['manifest.json', 'reviewer-workspace.md', 'README.md'],
    safetyNotes: [
      'Metadata only: no media bytes, transcript bodies, subtitle bodies, local filesystem paths, object storage keys, provider request or response bodies, credentials, bearer tokens, or demo tokens are included.'
    ]
  };
}

function demoHandoffPortalFixture(): DemoHandoffPortal {
  return {
    jobId: 'job-1',
    videoId: 'video-1',
    generatedAt: '2026-06-26T10:00:06Z',
    overallStatus: 'READY',
    phase: 'HANDOFF_PORTAL_READY',
    headline: 'Demo handoff portal is ready.',
    recommendedNextAction: 'Download the handoff portal ZIP and share index.html with reviewers.',
    completedAt: '2026-06-26T10:00:04Z',
    targetLanguage: 'zh-CN',
    demoProfileId: 'openai-smoke',
    checks: [
      {
        key: 'portal-package',
        label: 'Static handoff portal ZIP',
        status: 'READY',
        detail: 'Portal package entries and safe links are available.',
        nextAction: 'Open index.html from the ZIP for offline review.',
        required: true
      },
      {
        key: 'reviewer-workspace',
        label: 'Demo reviewer workspace',
        status: 'READY',
        detail: 'Reviewer workspace status is READY.',
        nextAction: 'Download reviewer workspace.',
        required: true
      }
    ],
    sections: [
      {
        key: 'offline-portal',
        title: 'Offline portal',
        status: 'READY',
        facts: ['Entry point: index.html', 'Static package excludes media bytes.']
      }
    ],
    safeLinks: [
      {
        kind: 'portal',
        label: 'Demo handoff portal ZIP',
        href: '/api/jobs/job-1/demo-handoff-portal/download',
        contentType: 'application/zip',
        description: 'Static portal package.'
      },
      {
        kind: 'reviewer',
        label: 'Demo reviewer workspace',
        href: '/api/jobs/job-1/demo-reviewer-workspace/download',
        contentType: 'application/zip',
        description: 'Reviewer workspace package.'
      }
    ],
    packageEntries: ['index.html', 'manifest.json', 'handoff-portal.md'],
    safetyNotes: ['Metadata only; sensitive content is excluded.']
  };
}
