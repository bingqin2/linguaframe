import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { linguaFrameApi, readDemoToken, writeDemoToken } from './api/linguaframeApi';
import type {
  DeliveryManifest,
  FailureTriage,
  JobArtifact,
  DemoSessionStatus,
  LocalizationJob,
  LocalizationJobStatus,
  LocalizationJobSummary,
  MediaUpload,
  MediaUploadValidation,
  OperatorDashboard,
  PromptTemplate,
  ProviderReadiness,
  RetentionCleanupResult,
  RuntimeDependencySummary,
  RuntimeLiveCheckName,
  RuntimeLiveCheckSummary,
  SubtitleDraftSummary,
  SubtitleReviewSummary,
  SubtitleSegment,
  TranscriptSegment
} from './domain/jobTypes';
import { loadRecentJobs, RecentJob, saveRecentJob } from './domain/recentJobs';

const POLL_INTERVAL_MS = 5000;
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);
const CANCELLABLE_STATUSES = new Set(['QUEUED', 'RETRYING', 'PROCESSING']);
const HISTORY_LIMIT = 20;
const HISTORY_STATUSES: Array<LocalizationJobStatus | 'ALL'> = [
  'ALL',
  'QUEUED',
  'RETRYING',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'CANCELLED'
];
const TTS_VOICE_OPTIONS = [
  { value: '', label: 'Default voice' },
  { value: 'alloy', label: 'Alloy' },
  { value: 'verse', label: 'Verse' },
  { value: 'aria', label: 'Aria' },
  { value: 'sage', label: 'Sage' },
  { value: 'coral', label: 'Coral' }
];

type DeliverableStatus = 'Ready' | 'Preview only' | 'Missing';

interface ResultDeliverableDefinition {
  key: string;
  label: string;
  artifactType: JobArtifact['type'];
  preview: 'transcript' | 'subtitle' | null;
}

interface ResultDeliverable {
  definition: ResultDeliverableDefinition;
  artifact: JobArtifact | null;
  status: DeliverableStatus;
}

interface MediaDeliveryItem {
  key: string;
  label: string;
  playerLabel: string;
  artifact: JobArtifact | null;
  kind: 'audio' | 'video';
}

interface DemoEvidence {
  generatedAt: string;
  job: {
    jobId: string;
    videoId: string;
    targetLanguage: string;
    status: LocalizationJobStatus;
    retryCount: number;
    failureStage: string | null;
    failureReason: string | null;
    failureTriage: FailureTriage | null;
    pipelineProgress: LocalizationJob['pipelineProgress'];
  };
  previews: {
    transcriptSegmentCount: number;
    subtitleSegmentCount: number;
    subtitleLanguage: string;
  };
  subtitleReview: {
    segmentCount: number;
    missingTargetCount: number;
    timingMismatchCount: number;
    qualityScore: number | null;
    qualityVerdict: string | null;
    downloadableSubtitleArtifactCount: number;
  } | null;
  subtitleDraft: {
    segmentCount: number;
    editedSegmentCount: number;
    lastUpdatedAt: string | null;
  } | null;
  reviewedDelivery: {
    subtitleArtifactCount: number;
    burnedVideoAvailable: boolean;
  };
  usage: {
    modelCallCount: number;
    failedModelCallCount: number;
    estimatedCostUsd: number;
    totalLatencyMs: number;
  };
  cache: {
    artifactCacheHitCount: number;
    generatedArtifactCount: number;
    providerCacheHitCount: number;
  };
  qualityEvaluation: {
    score: number;
    verdict: string;
    status: string;
  } | null;
  timeline: Array<{
    stage: string;
    status: string;
  }>;
  artifacts: Array<{
    type: JobArtifact['type'];
    filename: string;
    sizeBytes: number;
    sha256: string;
    cacheState: 'Generated' | 'Reused';
  }>;
  links: {
    resultBundle: string;
    diagnostics: string;
    evidenceMarkdown: string;
    evidenceBundle: string;
  };
}

type ChecklistStatus = 'PASS' | 'WARN' | 'FAIL';

interface DemoHandoffChecklistItem {
  key: string;
  label: string;
  status: ChecklistStatus;
  detail: string;
}

interface DemoHandoffChecklist {
  overallStatus: 'READY' | 'ATTENTION';
  summary: string;
  items: DemoHandoffChecklistItem[];
  links: DemoEvidence['links'];
}

interface DemoSessionReport {
  generatedAt: string;
  status: 'READY' | 'ATTENTION';
  title: string;
  sections: Array<{
    title: string;
    lines: string[];
  }>;
  links: DemoEvidence['links'];
}

interface CacheReplayCandidate {
  jobId: string;
  filename: string;
  status: LocalizationJobStatus;
  targetLanguage: string;
  ttsVoice: string | null;
}

interface CacheReplayBaseline {
  job: LocalizationJob;
  artifacts: JobArtifact[];
}

interface CacheReplayEvidenceJob {
  jobId: string;
  status: LocalizationJobStatus;
  targetLanguage: string;
  ttsVoice: string;
  modelCallCount: number;
  estimatedCostUsd: number;
  artifactCacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
}

interface CacheReplayEvidence {
  generatedAt: string;
  baseline: CacheReplayEvidenceJob;
  comparison: CacheReplayEvidenceJob;
  delta: {
    modelCalls: number;
    estimatedCostUsd: number;
  };
  providerCacheHitStages: string[];
  links: {
    baselineResultBundle: string;
    comparisonResultBundle: string;
    baselineDiagnostics: string;
    comparisonDiagnostics: string;
  };
}

const RESULT_DELIVERABLES: ResultDeliverableDefinition[] = [
  { key: 'transcript-json', label: 'Transcript JSON', artifactType: 'TRANSCRIPT_JSON', preview: 'transcript' },
  { key: 'source-srt', label: 'Source SRT', artifactType: 'SUBTITLE_SRT', preview: 'transcript' },
  { key: 'source-vtt', label: 'Source VTT', artifactType: 'SUBTITLE_VTT', preview: 'transcript' },
  {
    key: 'target-subtitle-json',
    label: 'Target subtitle JSON',
    artifactType: 'TARGET_SUBTITLE_JSON',
    preview: 'subtitle'
  },
  { key: 'target-srt', label: 'Target SRT', artifactType: 'TARGET_SUBTITLE_SRT', preview: 'subtitle' },
  { key: 'target-vtt', label: 'Target VTT', artifactType: 'TARGET_SUBTITLE_VTT', preview: 'subtitle' },
  { key: 'reviewed-json', label: 'Reviewed subtitle JSON', artifactType: 'REVIEWED_SUBTITLE_JSON', preview: null },
  { key: 'reviewed-srt', label: 'Reviewed SRT', artifactType: 'REVIEWED_SUBTITLE_SRT', preview: null },
  { key: 'reviewed-vtt', label: 'Reviewed VTT', artifactType: 'REVIEWED_SUBTITLE_VTT', preview: null },
  { key: 'dubbing-audio', label: 'Dubbing audio', artifactType: 'DUBBING_AUDIO', preview: null },
  { key: 'burned-video', label: 'Burned video', artifactType: 'BURNED_VIDEO', preview: null },
  { key: 'reviewed-burned-video', label: 'Reviewed burned video', artifactType: 'REVIEWED_BURNED_VIDEO', preview: null },
  { key: 'worker-summary', label: 'Worker summary', artifactType: 'WORKER_SUMMARY', preview: null }
];

export function App({ pollIntervalMs = POLL_INTERVAL_MS }: { pollIntervalMs?: number }) {
  const [targetLanguage, setTargetLanguage] = useState('zh-CN');
  const [ttsVoice, setTtsVoice] = useState('');
  const [manualJobId, setManualJobId] = useState('');
  const [selectedRecentJob, setSelectedRecentJob] = useState<RecentJob | null>(null);
  const [recentJobs, setRecentJobs] = useState<RecentJob[]>(() =>
    loadRecentJobs(window.localStorage)
  );
  const [historyStatusFilter, setHistoryStatusFilter] = useState<LocalizationJobStatus | 'ALL'>(
    'ALL'
  );
  const [history, setHistory] = useState<LocalizationJobSummary[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [demoTokenInput, setDemoTokenInput] = useState(() => readDemoToken(window.localStorage));
  const [demoTokenStatus, setDemoTokenStatus] = useState<string | null>(null);
  const [demoSessionStatus, setDemoSessionStatus] = useState<DemoSessionStatus | null>(null);
  const [isChangingDemoSession, setIsChangingDemoSession] = useState(false);
  const [job, setJob] = useState<LocalizationJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isValidatingUpload, setIsValidatingUpload] = useState(false);
  const [uploadValidation, setUploadValidation] = useState<MediaUploadValidation | null>(null);
  const [uploadValidationError, setUploadValidationError] = useState<string | null>(null);
  const [isLoadingJob, setIsLoadingJob] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [isSseUnavailable, setIsSseUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<JobArtifact[]>([]);
  const [deliveryManifest, setDeliveryManifest] = useState<DeliveryManifest | null>(null);
  const [deliveryManifestError, setDeliveryManifestError] = useState<string | null>(null);
  const [transcript, setTranscript] = useState<TranscriptSegment[]>([]);
  const [subtitles, setSubtitles] = useState<SubtitleSegment[]>([]);
  const [subtitleReview, setSubtitleReview] = useState<SubtitleReviewSummary | null>(null);
  const [subtitleDraft, setSubtitleDraft] = useState<SubtitleDraftSummary | null>(null);
  const [subtitleDraftError, setSubtitleDraftError] = useState<string | null>(null);
  const [subtitleDraftStatus, setSubtitleDraftStatus] = useState<string | null>(null);
  const [isSavingSubtitleDraft, setIsSavingSubtitleDraft] = useState(false);
  const [isClearingSubtitleDraft, setIsClearingSubtitleDraft] = useState(false);
  const [isPublishingReviewedSubtitles, setIsPublishingReviewedSubtitles] = useState(false);
  const [promptTemplates, setPromptTemplates] = useState<PromptTemplate[]>([]);
  const [promptTemplateError, setPromptTemplateError] = useState<string | null>(null);
  const [operatorDashboard, setOperatorDashboard] = useState<OperatorDashboard | null>(null);
  const [operatorDashboardError, setOperatorDashboardError] = useState<string | null>(null);
  const [isLoadingOperatorDashboard, setIsLoadingOperatorDashboard] = useState(false);
  const [retentionCleanup, setRetentionCleanup] = useState<RetentionCleanupResult | null>(null);
  const [retentionCleanupError, setRetentionCleanupError] = useState<string | null>(null);
  const [isLoadingRetentionCleanup, setIsLoadingRetentionCleanup] = useState(false);
  const [isRunningRetentionCleanup, setIsRunningRetentionCleanup] = useState(false);
  const [runtimeDependencies, setRuntimeDependencies] = useState<RuntimeDependencySummary | null>(
    null
  );
  const [runtimeDependenciesError, setRuntimeDependenciesError] = useState<string | null>(null);
  const [runtimeLiveChecks, setRuntimeLiveChecks] = useState<RuntimeLiveCheckSummary | null>(null);
  const [runtimeLiveChecksError, setRuntimeLiveChecksError] = useState<string | null>(null);
  const [isLoadingRuntimeDependencies, setIsLoadingRuntimeDependencies] = useState(false);
  const [previewErrors, setPreviewErrors] = useState<string[]>([]);
  const [cacheReplayBaseline, setCacheReplayBaseline] = useState<CacheReplayBaseline | null>(null);
  const [cacheReplayComparisonJobId, setCacheReplayComparisonJobId] = useState('');
  const [cacheReplayComparisonJob, setCacheReplayComparisonJob] = useState<LocalizationJob | null>(null);
  const [cacheReplayComparisonArtifacts, setCacheReplayComparisonArtifacts] = useState<JobArtifact[]>([]);
  const [cacheReplayError, setCacheReplayError] = useState<string | null>(null);
  const [isLoadingCacheReplayComparison, setIsLoadingCacheReplayComparison] = useState(false);

  const selectedLanguage = selectedRecentJob?.targetLanguage ?? job?.targetLanguage ?? targetLanguage;
  const canRetry = job?.status === 'FAILED';
  const canCancel = job ? CANCELLABLE_STATUSES.has(job.status) : false;
  const cacheReplayCandidates = useMemo(
    () => buildCacheReplayCandidates(history, recentJobs, cacheReplayBaseline?.job.jobId ?? null),
    [cacheReplayBaseline?.job.jobId, history, recentJobs]
  );

  useEffect(() => {
    const storedToken = readDemoToken(window.localStorage);
    if (storedToken) {
      writeDemoToken(window.localStorage, storedToken);
    }
  }, []);

  const loadDemoSession = useCallback(async () => {
    try {
      const status = await linguaFrameApi.getDemoSession();
      setDemoSessionStatus(status);
      setDemoTokenStatus(null);
    } catch (sessionError) {
      setDemoSessionStatus(null);
      setDemoTokenStatus(toErrorMessage(sessionError));
    }
  }, []);

  const loadJob = useCallback(
    async (jobId: string, options: { silent?: boolean } = {}) => {
      if (!options.silent) {
        setIsLoadingJob(true);
      }
      try {
        const nextJob = await linguaFrameApi.getJob(jobId);
        setJob(nextJob);
        setIsSseUnavailable(false);
        setError(null);
        return nextJob;
      } catch (loadError) {
        setError(toErrorMessage(loadError));
        throw loadError;
      } finally {
        if (!options.silent) {
          setIsLoadingJob(false);
        }
      }
    },
    []
  );

  const loadHistory = useCallback(async (status: LocalizationJobStatus | 'ALL') => {
    setIsLoadingHistory(true);
    try {
      const result = await linguaFrameApi.listJobs({
        status,
        limit: HISTORY_LIMIT,
        offset: 0
      });
      setHistory(result.jobs);
      setHistoryError(null);
    } catch (historyLoadError) {
      setHistory([]);
      setHistoryError(toErrorMessage(historyLoadError));
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  const loadOperatorDashboard = useCallback(async () => {
    setIsLoadingOperatorDashboard(true);
    try {
      const dashboard = await linguaFrameApi.getOperatorDashboard();
      setOperatorDashboard(dashboard);
      setOperatorDashboardError(null);
    } catch (dashboardLoadError) {
      setOperatorDashboard(null);
      setOperatorDashboardError(toErrorMessage(dashboardLoadError));
    } finally {
      setIsLoadingOperatorDashboard(false);
    }
  }, []);

  const loadRuntimeDependencies = useCallback(async () => {
    setIsLoadingRuntimeDependencies(true);
    const [dependenciesResult, liveChecksResult] = await Promise.allSettled([
      linguaFrameApi.getRuntimeDependencies(),
      linguaFrameApi.getRuntimeLiveChecks()
    ]);

    if (dependenciesResult.status === 'fulfilled') {
      setRuntimeDependencies(dependenciesResult.value);
      setRuntimeDependenciesError(null);
    } else {
      setRuntimeDependencies(null);
      setRuntimeDependenciesError(toErrorMessage(dependenciesResult.reason));
    }

    if (liveChecksResult.status === 'fulfilled') {
      setRuntimeLiveChecks(liveChecksResult.value);
      setRuntimeLiveChecksError(null);
    } else {
      setRuntimeLiveChecks(null);
      setRuntimeLiveChecksError(toErrorMessage(liveChecksResult.reason));
    }

    setIsLoadingRuntimeDependencies(false);
  }, []);

  const loadRetentionCleanupPreview = useCallback(async () => {
    setIsLoadingRetentionCleanup(true);
    try {
      const result = await linguaFrameApi.getRetentionCleanupPreview();
      setRetentionCleanup(result);
      setRetentionCleanupError(null);
    } catch (retentionLoadError) {
      setRetentionCleanup(null);
      setRetentionCleanupError(toErrorMessage(retentionLoadError));
    } finally {
      setIsLoadingRetentionCleanup(false);
    }
  }, []);

  const loadPreviewData = useCallback(async (jobId: string, language: string) => {
    const errors: string[] = [];
    const [artifactResult, manifestResult, transcriptResult, subtitleResult, reviewResult, draftResult] = await Promise.allSettled([
      linguaFrameApi.listArtifacts(jobId),
      linguaFrameApi.getDeliveryManifest(jobId),
      linguaFrameApi.listTranscript(jobId),
      linguaFrameApi.listSubtitles(jobId, language),
      linguaFrameApi.getSubtitleReview(jobId, language),
      linguaFrameApi.getSubtitleDraft(jobId, language)
    ]);

    if (artifactResult.status === 'fulfilled') {
      setArtifacts(artifactResult.value);
    } else {
      setArtifacts([]);
      errors.push(`Artifacts: ${toErrorMessage(artifactResult.reason)}`);
    }

    if (manifestResult.status === 'fulfilled') {
      setDeliveryManifest(manifestResult.value);
      setDeliveryManifestError(null);
    } else {
      setDeliveryManifest(null);
      setDeliveryManifestError(toErrorMessage(manifestResult.reason));
      errors.push(`Delivery manifest: ${toErrorMessage(manifestResult.reason)}`);
    }

    if (transcriptResult.status === 'fulfilled') {
      setTranscript(transcriptResult.value);
    } else {
      setTranscript([]);
      errors.push(`Transcript: ${toErrorMessage(transcriptResult.reason)}`);
    }

    if (subtitleResult.status === 'fulfilled') {
      setSubtitles(subtitleResult.value);
    } else {
      setSubtitles([]);
      errors.push(`Subtitles: ${toErrorMessage(subtitleResult.reason)}`);
    }

    if (reviewResult.status === 'fulfilled') {
      setSubtitleReview(reviewResult.value);
    } else {
      setSubtitleReview(null);
      errors.push(`Subtitle review: ${toErrorMessage(reviewResult.reason)}`);
    }

    if (draftResult.status === 'fulfilled') {
      setSubtitleDraft(draftResult.value);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus(null);
    } else {
      setSubtitleDraft(null);
      setSubtitleDraftError(toErrorMessage(draftResult.reason));
      errors.push(`Subtitle draft: ${toErrorMessage(draftResult.reason)}`);
    }

    setPreviewErrors(errors);
  }, []);

  useEffect(() => {
    void loadDemoSession();
  }, [loadDemoSession]);

  useEffect(() => {
    void loadHistory(historyStatusFilter);
  }, [historyStatusFilter, loadHistory]);

  useEffect(() => {
    void loadOperatorDashboard();
  }, [loadOperatorDashboard]);

  useEffect(() => {
    void loadRuntimeDependencies();
  }, [loadRuntimeDependencies]);

  useEffect(() => {
    void loadRetentionCleanupPreview();
  }, [loadRetentionCleanupPreview]);

  useEffect(() => {
    let ignore = false;

    linguaFrameApi
      .listPromptTemplates()
      .then((templates) => {
        if (ignore) {
          return;
        }
        setPromptTemplates(templates);
        setPromptTemplateError(null);
      })
      .catch((templateLoadError) => {
        if (ignore) {
          return;
        }
        setPromptTemplates([]);
        setPromptTemplateError(toErrorMessage(templateLoadError));
      });

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (
      !job ||
      TERMINAL_STATUSES.has(job.status) ||
      (!isSseUnavailable && supportsEventSource())
    ) {
      return;
    }

    const timer = window.setTimeout(() => {
      void loadJob(job.jobId, { silent: true });
    }, pollIntervalMs);

    return () => window.clearTimeout(timer);
  }, [isSseUnavailable, job, loadJob, pollIntervalMs]);

  useEffect(() => {
    if (!job || TERMINAL_STATUSES.has(job.status) || !supportsEventSource() || isSseUnavailable) {
      return;
    }

    const eventSource = new EventSource(linguaFrameApi.jobEventsUrl(job.jobId));
    eventSource.onmessage = (event) => {
      try {
        const nextJob = JSON.parse(event.data) as LocalizationJob;
        setJob(nextJob);
        setError(null);
        if (TERMINAL_STATUSES.has(nextJob.status)) {
          void loadPreviewData(nextJob.jobId, nextJob.targetLanguage);
          void loadHistory(historyStatusFilter);
          eventSource.close();
        }
      } catch {
        setIsSseUnavailable(true);
        eventSource.close();
      }
    };
    eventSource.onerror = () => {
      setIsSseUnavailable(true);
      eventSource.close();
    };

    return () => eventSource.close();
  }, [historyStatusFilter, isSseUnavailable, job, loadHistory, loadPreviewData]);

  function getSelectedUploadFile(form: HTMLFormElement): File | null {
    const input = form.elements.namedItem('videoFile') as HTMLInputElement | null;
    return input?.files?.[0] ?? null;
  }

  async function runUploadValidation(file: File): Promise<MediaUploadValidation | null> {
    setIsValidatingUpload(true);
    try {
      const validation = await linguaFrameApi.validateUpload(file);
      setUploadValidation(validation);
      setUploadValidationError(null);
      return validation;
    } catch (validationError) {
      setUploadValidation(null);
      setUploadValidationError(toErrorMessage(validationError));
      return null;
    } finally {
      setIsValidatingUpload(false);
    }
  }

  async function handleValidateUpload(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadValidation(null);
      setUploadValidationError('Choose an MP4 file before validation.');
      return;
    }
    await runUploadValidation(file);
  }

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const file = getSelectedUploadFile(form);
    if (!file) {
      setError('Choose an MP4 file before uploading.');
      return;
    }

    const validation = await runUploadValidation(file);
    if (!validation) {
      return;
    }
    if (!validation.valid) {
      setError(validation.message);
      return;
    }

    setIsUploading(true);
    try {
      const upload = await linguaFrameApi.uploadMedia(file, targetLanguage.trim(), ttsVoice);
      const recentJob = toRecentJob(upload);
      setRecentJobs(saveRecentJob(window.localStorage, recentJob));
      setSelectedRecentJob(recentJob);
      await loadJob(upload.jobId);
      await loadPreviewData(upload.jobId, recentJob.targetLanguage);
      await loadHistory(historyStatusFilter);
    } catch (uploadError) {
      setError(toErrorMessage(uploadError));
    } finally {
      setIsUploading(false);
    }
  }

  async function handleOpenJob(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const jobId = manualJobId.trim();
    if (!jobId) {
      setError('Enter a job id to open.');
      return;
    }
    setSelectedRecentJob(recentJobs.find((recentJob) => recentJob.jobId === jobId) ?? null);
    const nextJob = await loadJob(jobId);
    await loadPreviewData(jobId, nextJob.targetLanguage ?? targetLanguage);
  }

  async function handleRetry() {
    if (!job) {
      return;
    }

    setIsRetrying(true);
    try {
      const retriedJob = await linguaFrameApi.retryJob(job.jobId);
      setJob(retriedJob);
      setError(null);
      await loadHistory(historyStatusFilter);
    } catch (retryError) {
      setError(toErrorMessage(retryError));
    } finally {
      setIsRetrying(false);
    }
  }

  async function handleCancel() {
    if (!job) {
      return;
    }

    setIsCancelling(true);
    try {
      const cancelledJob = await linguaFrameApi.cancelJob(job.jobId);
      setJob(cancelledJob);
      setError(null);
      await loadHistory(historyStatusFilter);
    } catch (cancelError) {
      setError(toErrorMessage(cancelError));
    } finally {
      setIsCancelling(false);
    }
  }

  async function openRecentJob(recentJob: RecentJob) {
    setSelectedRecentJob(recentJob);
    setManualJobId(recentJob.jobId);
    setTargetLanguage(recentJob.targetLanguage);
    setTtsVoice(recentJob.ttsVoice ?? '');
    await loadJob(recentJob.jobId);
    await loadPreviewData(recentJob.jobId, recentJob.targetLanguage);
  }

  async function openHistoryJob(historyJob: LocalizationJobSummary) {
    setSelectedRecentJob(null);
    setManualJobId(historyJob.jobId);
    setTargetLanguage(historyJob.targetLanguage);
    setTtsVoice(historyJob.ttsVoice ?? '');
    const nextJob = await loadJob(historyJob.jobId);
    await loadPreviewData(historyJob.jobId, nextJob.targetLanguage ?? historyJob.targetLanguage);
  }

  async function openDashboardFailure(failure: OperatorDashboard['recentFailures'][number]) {
    setSelectedRecentJob(null);
    setManualJobId(failure.jobId);
    const nextJob = await loadJob(failure.jobId);
    const language = nextJob.targetLanguage ?? targetLanguage;
    setTargetLanguage(language);
    setTtsVoice(nextJob.ttsVoice ?? '');
    await loadPreviewData(failure.jobId, language);
  }

  function handlePinCacheReplayBaseline() {
    if (!job) {
      return;
    }
    setCacheReplayBaseline({ job, artifacts });
    setCacheReplayComparisonJobId('');
    setCacheReplayComparisonJob(null);
    setCacheReplayComparisonArtifacts([]);
    setCacheReplayError(null);
  }

  async function handleSelectCacheReplayComparison(jobId: string) {
    setCacheReplayComparisonJobId(jobId);
    setCacheReplayComparisonJob(null);
    setCacheReplayComparisonArtifacts([]);
    setCacheReplayError(null);
    if (!jobId) {
      return;
    }

    setIsLoadingCacheReplayComparison(true);
    try {
      const [comparisonJob, comparisonArtifacts] = await Promise.all([
        linguaFrameApi.getJob(jobId),
        linguaFrameApi.listArtifacts(jobId)
      ]);
      setCacheReplayComparisonJob(comparisonJob);
      setCacheReplayComparisonArtifacts(comparisonArtifacts);
    } catch (comparisonLoadError) {
      setCacheReplayError(toErrorMessage(comparisonLoadError));
    } finally {
      setIsLoadingCacheReplayComparison(false);
    }
  }

  async function handleSaveSubtitleDraft(segments: Array<{ index: number; text: string }>) {
    if (!job) {
      return;
    }
    setIsSavingSubtitleDraft(true);
    try {
      const updated = await linguaFrameApi.updateSubtitleDraft(job.jobId, selectedLanguage, { segments });
      setSubtitleDraft(updated);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus('Draft saved.');
    } catch (draftError) {
      setSubtitleDraftError(toErrorMessage(draftError));
    } finally {
      setIsSavingSubtitleDraft(false);
    }
  }

  async function handleClearSubtitleDraft() {
    if (!job) {
      return;
    }
    setIsClearingSubtitleDraft(true);
    try {
      const cleared = await linguaFrameApi.clearSubtitleDraft(job.jobId, selectedLanguage);
      setSubtitleDraft(cleared);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus('Draft cleared.');
    } catch (draftError) {
      setSubtitleDraftError(toErrorMessage(draftError));
    } finally {
      setIsClearingSubtitleDraft(false);
    }
  }

  async function handlePublishReviewedSubtitles(includeBurnedVideo: boolean) {
    if (!job) {
      return;
    }
    setIsPublishingReviewedSubtitles(true);
    try {
      const published = await linguaFrameApi.publishReviewedSubtitles(job.jobId, {
        language: selectedLanguage,
        includeBurnedVideo
      });
      const [refreshedArtifacts, refreshedManifest] = await Promise.all([
        linguaFrameApi.listArtifacts(job.jobId),
        linguaFrameApi.getDeliveryManifest(job.jobId)
      ]);
      setArtifacts(refreshedArtifacts);
      setDeliveryManifest(refreshedManifest);
      setDeliveryManifestError(null);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus(`Published ${published.artifacts.length} reviewed artifacts.`);
    } catch (publishError) {
      setSubtitleDraftError(toErrorMessage(publishError));
    } finally {
      setIsPublishingReviewedSubtitles(false);
    }
  }

  async function handleSaveDemoToken(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = demoTokenInput.trim();
    if (!token) {
      setDemoTokenStatus('Enter the owner access token.');
      return;
    }

    setIsChangingDemoSession(true);
    try {
      const status = await linguaFrameApi.loginDemoSession(token);
      writeDemoToken(window.localStorage, '');
      setDemoSessionStatus(status);
      setDemoTokenInput('');
      setDemoTokenStatus(status.authenticated ? 'Owner session active' : 'Owner session required');
      void loadRuntimeDependencies();
      void loadRetentionCleanupPreview();
    } catch (sessionError) {
      setDemoTokenStatus(toErrorMessage(sessionError));
    } finally {
      setIsChangingDemoSession(false);
    }
  }

  async function handleClearDemoToken() {
    setIsChangingDemoSession(true);
    try {
      const status = await linguaFrameApi.logoutDemoSession();
      writeDemoToken(window.localStorage, '');
      setDemoSessionStatus(status);
      setDemoTokenInput('');
      setDemoTokenStatus('Owner session ended.');
      void loadRuntimeDependencies();
      void loadRetentionCleanupPreview();
    } catch (sessionError) {
      writeDemoToken(window.localStorage, '');
      setDemoTokenInput('');
      setDemoTokenStatus(toErrorMessage(sessionError));
    } finally {
      setIsChangingDemoSession(false);
    }
  }

  async function handleRunRetentionCleanup() {
    const confirmed = window.confirm(
      'Run retention cleanup now? The backend retention configuration decides whether this is a dry run or a deleting run.'
    );
    if (!confirmed) {
      return;
    }
    setIsRunningRetentionCleanup(true);
    try {
      const result = await linguaFrameApi.runRetentionCleanup();
      setRetentionCleanup(result);
      setRetentionCleanupError(null);
      void loadOperatorDashboard();
      void loadHistory(historyStatusFilter);
    } catch (retentionRunError) {
      setRetentionCleanupError(toErrorMessage(retentionRunError));
    } finally {
      setIsRunningRetentionCleanup(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <h1>LinguaFrame Demo</h1>
          <p>Upload a video, follow the localization pipeline, and inspect generated outputs.</p>
        </div>
        <div className="header-tools">
          <span className="runtime-badge">{formatDemoSessionStatus(demoSessionStatus)}</span>
          <form className="demo-token-form" onSubmit={handleSaveDemoToken}>
            <label>
              Owner access token
              <input
                type="password"
                value={demoTokenInput}
                onChange={(event) => {
                  setDemoTokenInput(event.target.value);
                  setDemoTokenStatus(null);
                }}
                autoComplete="off"
              />
            </label>
            <div className="demo-token-actions">
              <button type="submit" disabled={isChangingDemoSession}>
                Start session
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={handleClearDemoToken}
                disabled={isChangingDemoSession}
              >
                End session
              </button>
            </div>
            {demoTokenStatus ? <p className="token-status">{demoTokenStatus}</p> : null}
          </form>
        </div>
      </header>

      {error ? <div className="alert">{error}</div> : null}

      <section className="workspace-grid" aria-label="Demo workspace">
        <aside className="sidebar" aria-label="Job controls">
          <DemoReadinessPanel
            dependencies={runtimeDependencies}
            error={runtimeDependenciesError}
            isLoading={isLoadingRuntimeDependencies}
            onRefresh={loadRuntimeDependencies}
          />

          <RuntimeLiveChecksPanel
            liveChecks={runtimeLiveChecks}
            error={runtimeLiveChecksError}
            isLoading={isLoadingRuntimeDependencies}
            onRefresh={loadRuntimeDependencies}
          />

          <DemoRunbookPanel
            dependencies={runtimeDependencies}
            error={runtimeDependenciesError}
          />

          <form className="panel" onSubmit={handleUpload}>
            <h2>Upload</h2>
            <label>
              Video file
              <input
                name="videoFile"
                type="file"
                accept="video/mp4,video/*"
                onChange={() => {
                  setUploadValidation(null);
                  setUploadValidationError(null);
                }}
              />
            </label>
            <label>
              Target language
              <input
                value={targetLanguage}
                onChange={(event) => setTargetLanguage(event.target.value)}
                placeholder="zh-CN"
              />
            </label>
            <label>
              TTS voice
              <select value={ttsVoice} onChange={(event) => setTtsVoice(event.target.value)}>
                {TTS_VOICE_OPTIONS.map((option) => (
                  <option key={option.value || 'default'} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <div className="panel-actions upload-actions">
              <button
                type="button"
                className="secondary-button"
                disabled={isUploading || isValidatingUpload}
                onClick={(event) => void handleValidateUpload(event.currentTarget.form)}
              >
                {isValidatingUpload ? 'Validating...' : 'Validate file'}
              </button>
              <button type="submit" disabled={isUploading || isValidatingUpload}>
                {isUploading ? 'Uploading...' : 'Upload'}
              </button>
            </div>
            <UploadValidationPanel
              validation={uploadValidation}
              error={uploadValidationError}
            />
          </form>

          <form className="panel" onSubmit={handleOpenJob}>
            <h2>Open job</h2>
            <label>
              Open job id
              <input
                value={manualJobId}
                onChange={(event) => setManualJobId(event.target.value)}
                placeholder="job id"
              />
            </label>
            <button type="submit" disabled={isLoadingJob}>
              Open job
            </button>
          </form>

          <section className="panel" aria-label="Job history">
            <div className="panel-heading">
              <h2>Job history</h2>
              <button
                type="button"
                className="secondary-button"
                disabled={isLoadingHistory}
                onClick={() => void loadHistory(historyStatusFilter)}
              >
                Refresh
              </button>
            </div>
            <label>
              History status
              <select
                value={historyStatusFilter}
                onChange={(event) =>
                  setHistoryStatusFilter(event.target.value as LocalizationJobStatus | 'ALL')
                }
              >
                {HISTORY_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
            {historyError ? <p className="error-text">{historyError}</p> : null}
            {isLoadingHistory ? <p className="muted">Loading history...</p> : null}
            {!isLoadingHistory && history.length === 0 ? (
              <p className="muted">No server jobs match this filter.</p>
            ) : null}
            {history.length > 0 ? (
              <ul className="history-list" aria-label="Server job history">
                {history.map((historyJob) => (
                  <li key={historyJob.jobId}>
                    <button type="button" onClick={() => void openHistoryJob(historyJob)}>
                      <span className="history-title">
                        <strong>{historyJob.filename}</strong>
                        <span className="status-pill">{historyJob.status}</span>
                      </span>
                      <span className="history-meta">
                        {historyJob.targetLanguage} · {formatVoice(historyJob.ttsVoice)} ·
                        {formatCost(historyJob.estimatedCostUsd)} ·
                        retry {historyJob.retryCount}
                      </span>
                      <small>{historyJob.jobId}</small>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>

          <OperatorDashboardPanel
            dashboard={operatorDashboard}
            error={operatorDashboardError}
            isLoading={isLoadingOperatorDashboard}
            onRefresh={loadOperatorDashboard}
            onOpenFailure={openDashboardFailure}
          />

          <RetentionCleanupPanel
            result={retentionCleanup}
            error={retentionCleanupError}
            isLoading={isLoadingRetentionCleanup}
            isRunning={isRunningRetentionCleanup}
            onPreview={loadRetentionCleanupPreview}
            onRun={handleRunRetentionCleanup}
          />

          <section className="panel">
            <h2>Recent jobs</h2>
            {recentJobs.length === 0 ? (
              <p className="muted">No browser-local jobs yet.</p>
            ) : (
              <ul className="recent-list" aria-label="Recent jobs">
                {recentJobs.map((recentJob) => (
                  <li key={recentJob.jobId}>
                    <button type="button" onClick={() => void openRecentJob(recentJob)}>
                      <span>{recentJob.filename}</span>
                      <span className="history-meta">
                        {recentJob.targetLanguage} · {formatVoice(recentJob.ttsVoice)}
                      </span>
                      <small>{recentJob.jobId}</small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <PromptTemplatesPanel
            error={promptTemplateError}
            promptTemplates={promptTemplates}
          />
        </aside>

        <section className="job-surface" aria-label="Selected job">
          {job ? (
            <JobDetail
              canCancel={canCancel}
              canRetry={canRetry}
              isCancelling={isCancelling}
              isClearingSubtitleDraft={isClearingSubtitleDraft}
              isLoadingJob={isLoadingJob}
              isPublishingReviewedSubtitles={isPublishingReviewedSubtitles}
              isRetrying={isRetrying}
              isSavingSubtitleDraft={isSavingSubtitleDraft}
              artifacts={artifacts}
              deliveryManifest={deliveryManifest}
              deliveryManifestError={deliveryManifestError}
              job={job}
              cacheReplayBaseline={cacheReplayBaseline}
              cacheReplayCandidates={cacheReplayCandidates}
              cacheReplayComparisonArtifacts={cacheReplayComparisonArtifacts}
              cacheReplayComparisonJob={cacheReplayComparisonJob}
              cacheReplayComparisonJobId={cacheReplayComparisonJobId}
              cacheReplayError={cacheReplayError}
              isLoadingCacheReplayComparison={isLoadingCacheReplayComparison}
              onCancel={handleCancel}
              onClearSubtitleDraft={handleClearSubtitleDraft}
              onPinCacheReplayBaseline={handlePinCacheReplayBaseline}
              onSelectCacheReplayComparison={handleSelectCacheReplayComparison}
              onRetry={handleRetry}
              onPublishReviewedSubtitles={handlePublishReviewedSubtitles}
              onSaveSubtitleDraft={handleSaveSubtitleDraft}
              previewErrors={previewErrors}
              selectedLanguage={selectedLanguage}
              subtitleDraft={subtitleDraft}
              subtitleDraftError={subtitleDraftError}
              subtitleDraftStatus={subtitleDraftStatus}
              subtitleReview={subtitleReview}
              subtitles={subtitles}
              transcript={transcript}
            />
          ) : (
            <div className="empty-state">
              <h2>No job selected</h2>
              <p>Upload a video or open a known job id to inspect pipeline progress.</p>
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

function PromptTemplatesPanel({
  error,
  promptTemplates
}: {
  error: string | null;
  promptTemplates: PromptTemplate[];
}) {
  return (
    <section className="panel" aria-label="Prompt templates">
      <h2>Prompt templates</h2>
      {error ? <p className="muted">{error}</p> : null}
      {!error && promptTemplates.length === 0 ? (
        <p className="muted">No active prompt templates loaded.</p>
      ) : null}
      {promptTemplates.length > 0 ? (
        <ul className="template-list">
          {promptTemplates.map((template) => (
            <li key={template.version}>
              <strong>{template.purpose}</strong>
              <span>{template.version}</span>
              <small>
                {template.provider} · {template.modelFamily}
              </small>
              <p>{template.outputContract}</p>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function DemoReadinessPanel({
  dependencies,
  error,
  isLoading,
  onRefresh
}: {
  dependencies: RuntimeDependencySummary | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const readiness = dependencies?.readiness ?? null;
  const runtime = dependencies?.runtime ?? null;
  const providerEntries = readiness
    ? (['transcription', 'translation', 'tts', 'evaluation'] as const).map((name) => [
        name,
        readiness.providers[name]
      ] as const)
    : [];
  const featureEntries = readiness ? Object.entries(readiness.features) : [];

  return (
    <section className="panel" aria-label="Demo readiness">
      <div className="panel-heading">
        <h2>Demo readiness</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !readiness ? <p className="muted">Loading readiness...</p> : null}
      {readiness ? (
        <>
          <dl className="status-grid readiness-grid">
            <div>
              <dt>App version</dt>
              <dd>{runtime?.appVersion ?? 'Unknown'}</dd>
            </div>
            <div>
              <dt>Migration contract</dt>
              <dd>{runtime ? `V${runtime.latestMigrationVersion}` : 'Unknown'}</dd>
            </div>
            <div>
              <dt>Access</dt>
              <dd>{readiness.demoAccessGate ? 'Protected' : 'Open'}</dd>
            </div>
            <div>
              <dt>Video limit</dt>
              <dd>{readiness.media.maxDurationSeconds} seconds</dd>
            </div>
            <div>
              <dt>Worker</dt>
              <dd>{readiness.worker.role}</dd>
            </div>
            <div>
              <dt>Dispatch</dt>
              <dd>{formatEnabled(readiness.worker.dispatchEnabled)}</dd>
            </div>
            <div>
              <dt>FFmpeg audio</dt>
              <dd>
                {formatEnabled(readiness.ffmpeg.audioEnabled)} /{' '}
                {formatConfigured(readiness.ffmpeg.binaryConfigured)}
              </dd>
            </div>
            <div>
              <dt>FFmpeg burn-in</dt>
              <dd>
                {formatEnabled(readiness.ffmpeg.burnInEnabled)} /{' '}
                {formatConfigured(readiness.ffmpeg.workspaceConfigured)}
              </dd>
            </div>
            <div>
              <dt>Budget guard</dt>
              <dd>
                {formatEnabled(readiness.budget.enabled)} / estimates{' '}
                {formatEnabled(readiness.budget.estimatedCostTrackingEnabled)}
              </dd>
            </div>
            <div>
              <dt>Job cost limit</dt>
              <dd>{formatCost(readiness.budget.maxJobCostUsd)}</dd>
            </div>
            <div>
              <dt>Daily budget</dt>
              <dd>
                {formatEnabled(readiness.budget.dailyBudgetGuardEnabled)} /{' '}
                {formatCost(readiness.budget.maxDailyCostUsd)}
              </dd>
            </div>
            <div>
              <dt>Budget identity</dt>
              <dd>{readiness.budget.budgetIdentity}</dd>
            </div>
          </dl>
          <ul className="readiness-list" aria-label="Provider readiness">
            {providerEntries.map(([name, provider]) => (
              <li key={name}>{formatProviderReadiness(name, provider)}</li>
            ))}
          </ul>
          <ul className="feature-list" aria-label="Runtime feature flags">
            {featureEntries.map(([name, feature]) => (
              <li key={name}>
                <span>{formatFeatureName(name)}</span>
                <span>{formatEnabled(feature.enabled)}</span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}

function RuntimeLiveChecksPanel({
  liveChecks,
  error,
  isLoading,
  onRefresh
}: {
  liveChecks: RuntimeLiveCheckSummary | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const checkEntries = liveChecks
    ? (Object.entries(liveChecks.checks) as Array<[RuntimeLiveCheckName, RuntimeLiveCheckSummary['checks'][RuntimeLiveCheckName]]>)
    : [];

  return (
    <section className="panel" aria-label="Live checks">
      <div className="panel-heading">
        <h2>Live checks</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">Live checks unavailable: {error}</p> : null}
      {isLoading && !liveChecks ? <p className="muted">Checking runtime...</p> : null}
      {liveChecks ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Overall</dt>
              <dd>{liveChecks.healthy ? 'Ready' : 'Blocked'}</dd>
            </div>
            <div>
              <dt>Checked at</dt>
              <dd>{formatIsoDateTime(liveChecks.checkedAt)}</dd>
            </div>
          </dl>
          <ul className="feature-list live-check-list" aria-label="Runtime live dependency checks">
            {checkEntries.map(([name, result]) => (
              <li key={name}>
                <span>{formatRuntimeCheckName(name)}</span>
                <span className={runtimeProbeStatusClassName(result.status)}>
                  {result.status}
                </span>
                <small>
                  {result.latencyMs} ms · {result.message}
                </small>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}

function DemoRunbookPanel({
  dependencies,
  error
}: {
  dependencies: RuntimeDependencySummary | null;
  error: string | null;
}) {
  const readiness = dependencies?.readiness ?? null;
  const providerEntries = readiness
    ? (['transcription', 'translation', 'tts', 'evaluation'] as const).map((name) => [
        name,
        readiness.providers[name]
      ] as const)
    : [];

  return (
    <section className="panel demo-runbook-panel" aria-label="Demo runbook">
      <h2>Demo runbook</h2>
      <dl className="runbook-list">
        <div>
          <dt>Start</dt>
          <dd>
            <code>scripts/demo/start-local-demo.sh</code>
          </dd>
        </div>
        <div>
          <dt>Open</dt>
          <dd>
            <code>http://localhost:5173</code>
          </dd>
        </div>
        <div>
          <dt>Health</dt>
          <dd>
            <code>http://localhost:8080/actuator/health</code>
          </dd>
        </div>
      </dl>

      <h3>Validation</h3>
      <ul className="command-list">
        <li>
          <code>scripts/demo/docker-e2e-success.sh</code>
        </li>
        <li>
          <code>scripts/demo/docker-e2e-cache-hit.sh</code>
        </li>
        <li>
          <code>scripts/demo/docker-e2e-tears-of-steel-full.sh</code>
        </li>
      </ul>

      <h3>Runtime guidance</h3>
      {error ? <p className="error-text">Runtime guidance unavailable: {error}</p> : null}
      {readiness ? (
        <ul className="readiness-list">
          <li>
            {readiness.demoAccessGate
              ? 'Private demo token is required for API calls.'
              : 'Local API is open without a demo token.'}
          </li>
          <li>
            Uploads must be complete files up to {readiness.media.maxDurationSeconds} seconds and{' '}
            {readiness.media.maxFileSizeMb} MB.
          </li>
          <li>
            {readiness.budget.enabled
              ? `Budget guard is enabled at ${formatCost(readiness.budget.maxJobCostUsd)} per job.`
              : 'Budget guard is disabled.'}
          </li>
          <li>
            {readiness.budget.dailyBudgetGuardEnabled
              ? `Daily budget guard is enabled at ${formatCost(readiness.budget.maxDailyCostUsd)} for ${readiness.budget.budgetIdentity}.`
              : 'Daily budget guard is disabled.'}
          </li>
          <li>
            Subtitle burn-in is {readiness.ffmpeg.burnInEnabled ? 'enabled' : 'disabled'}.
          </li>
          {providerEntries.map(([name, provider]) => (
            <li key={name}>{formatProviderReadiness(name, provider)}</li>
          ))}
        </ul>
      ) : null}

      <h3>Sample media</h3>
      <ul className="readiness-list">
        <li>Quick sample: generated by docker-e2e-success.sh when FFmpeg is available.</li>
        <li>
          Full sample: set LINGUAFRAME_TEARS_SAMPLE_PATH before running the Tears of Steel script.
        </li>
      </ul>
    </section>
  );
}

function UploadValidationPanel({
  validation,
  error
}: {
  validation: MediaUploadValidation | null;
  error: string | null;
}) {
  if (!validation && !error) {
    return null;
  }

  return (
    <section className="upload-validation-panel" aria-label="Upload validation">
      <div className="panel-heading">
        <h3>Upload validation</h3>
        {validation ? (
          <span className={validation.valid ? 'status-pill' : 'status-pill danger'}>
            {validation.code}
          </span>
        ) : null}
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {validation ? (
        <>
          <p className={validation.valid ? 'muted validation-message' : 'error-text'}>
            {validation.message}
          </p>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Filename</dt>
              <dd>{validation.filename ?? 'Unknown'}</dd>
            </div>
            <div>
              <dt>Content type</dt>
              <dd>{validation.contentType ?? 'Unknown'}</dd>
            </div>
            <div>
              <dt>File size</dt>
              <dd>
                {formatBytes(validation.fileSizeBytes)} / {formatBytes(validation.maxFileSizeBytes)}
              </dd>
            </div>
            <div>
              <dt>Duration</dt>
              <dd>
                {validation.durationSeconds === null
                  ? 'Unknown'
                  : `${validation.durationSeconds} seconds`}{' '}
                / {validation.maxDurationSeconds} seconds
              </dd>
            </div>
          </dl>
        </>
      ) : null}
    </section>
  );
}

function OperatorDashboardPanel({
  dashboard,
  error,
  isLoading,
  onRefresh,
  onOpenFailure
}: {
  dashboard: OperatorDashboard | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
  onOpenFailure: (failure: OperatorDashboard['recentFailures'][number]) => void;
}) {
  const totalJobs = dashboard?.statusCounts.reduce((total, item) => total + item.count, 0) ?? 0;
  const failedJobs = dashboard?.statusCounts.find((item) => item.status === 'FAILED')?.count ?? 0;
  const processingJobs =
    dashboard?.statusCounts.find((item) => item.status === 'PROCESSING')?.count ?? 0;

  return (
    <section className="panel" aria-label="Operator dashboard">
      <div className="panel-heading">
        <h2>Operator dashboard</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !dashboard ? <p className="muted">Loading dashboard...</p> : null}
      {dashboard ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Total</dt>
              <dd>{totalJobs} jobs</dd>
            </div>
            <div>
              <dt>Processing</dt>
              <dd>{processingJobs} active</dd>
            </div>
            <div>
              <dt>Failed</dt>
              <dd>{failedJobs} failed</dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatCost(dashboard.modelCalls.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Model calls</dt>
              <dd>
                {dashboard.modelCalls.modelCallCount} calls /{' '}
                {dashboard.modelCalls.failedModelCallCount} failed
              </dd>
            </div>
            <div>
              <dt>Cache</dt>
              <dd>
                {dashboard.cache.artifactCacheHitCount} /{' '}
                {dashboard.cache.generatedArtifactCount} artifacts
              </dd>
            </div>
            <div>
              <dt>Provider cache</dt>
              <dd>{dashboard.cache.providerCacheHitCount} hits</dd>
            </div>
          </dl>
          {dashboard.recentFailures.length > 0 ? (
            <ul className="recent-list" aria-label="Recent failed jobs">
              {dashboard.recentFailures.map((failure) => (
                <li key={failure.jobId}>
                  <button type="button" onClick={() => onOpenFailure(failure)}>
                    <span>{failure.filename}</span>
                    <span className="history-meta">
                      {failure.failureStage ?? 'FAILED'} · {failure.failureReason ?? 'No reason'}
                    </span>
                    <small>{failure.jobId}</small>
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No recent failed jobs.</p>
          )}
          <h3>Stage timings</h3>
          {dashboard.stageTimings.length > 0 ? (
            <ul className="recent-list" aria-label="Stage timings">
              {dashboard.stageTimings.map((timing) => (
                <li key={timing.stage}>
                  <span>{timing.stage}</span>
                  <span className="history-meta">
                    max {formatDurationMs(timing.maxDurationMs)} · avg{' '}
                    {formatDurationMs(timing.averageDurationMs)}
                  </span>
                  <small>
                    {timing.completedEventCount} completed / {timing.failedEventCount} failed · latest{' '}
                    {formatDurationMs(timing.latestDurationMs)}
                  </small>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">No stage timing data yet.</p>
          )}
        </>
      ) : null}
    </section>
  );
}

function RetentionCleanupPanel({
  result,
  error,
  isLoading,
  isRunning,
  onPreview,
  onRun
}: {
  result: RetentionCleanupResult | null;
  error: string | null;
  isLoading: boolean;
  isRunning: boolean;
  onPreview: () => void;
  onRun: () => void;
}) {
  return (
    <section className="panel" aria-label="Retention cleanup">
      <div className="panel-heading">
        <h2>Retention cleanup</h2>
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={isLoading || isRunning}
            onClick={onPreview}
          >
            Preview cleanup
          </button>
          <button type="button" disabled={isLoading || isRunning} onClick={onRun}>
            {isRunning ? 'Running...' : 'Run cleanup'}
          </button>
        </div>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !result ? <p className="muted">Loading cleanup preview...</p> : null}
      {result ? (
        <>
          <p className="mode-line">
            <span className={result.dryRun ? 'status-pill' : 'status-pill danger'}>
              {result.dryRun ? 'Dry run' : 'Delete mode'}
            </span>
            <span>{formatRetentionOutcome(result)}</span>
          </p>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Candidates</dt>
              <dd>{result.candidateJobCount} jobs</dd>
            </div>
            <div>
              <dt>Jobs</dt>
              <dd>{result.deletedJobCount} deleted</dd>
            </div>
            <div>
              <dt>Videos</dt>
              <dd>{result.deletedVideoCount} deleted</dd>
            </div>
            <div>
              <dt>Objects</dt>
              <dd>{result.deletedObjectCount} deleted</dd>
            </div>
            <div>
              <dt>Skipped</dt>
              <dd>{result.skippedObjectCount} objects</dd>
            </div>
            <div>
              <dt>Failures</dt>
              <dd>{result.failureCount} failed</dd>
            </div>
          </dl>
        </>
      ) : null}
    </section>
  );
}

function JobDetail({
  canCancel,
  canRetry,
  isCancelling,
  isClearingSubtitleDraft,
  isLoadingJob,
  isPublishingReviewedSubtitles,
  isRetrying,
  isSavingSubtitleDraft,
  artifacts,
  deliveryManifest,
  deliveryManifestError,
  job,
  cacheReplayBaseline,
  cacheReplayCandidates,
  cacheReplayComparisonArtifacts,
  cacheReplayComparisonJob,
  cacheReplayComparisonJobId,
  cacheReplayError,
  isLoadingCacheReplayComparison,
  onCancel,
  onClearSubtitleDraft,
  onPinCacheReplayBaseline,
  onSelectCacheReplayComparison,
  onRetry,
  onPublishReviewedSubtitles,
  onSaveSubtitleDraft,
  previewErrors,
  selectedLanguage,
  subtitleDraft,
  subtitleDraftError,
  subtitleDraftStatus,
  subtitleReview,
  subtitles,
  transcript
}: {
  canCancel: boolean;
  canRetry: boolean;
  isCancelling: boolean;
  isClearingSubtitleDraft: boolean;
  isLoadingJob: boolean;
  isPublishingReviewedSubtitles: boolean;
  isRetrying: boolean;
  isSavingSubtitleDraft: boolean;
  artifacts: JobArtifact[];
  deliveryManifest: DeliveryManifest | null;
  deliveryManifestError: string | null;
  job: LocalizationJob;
  cacheReplayBaseline: CacheReplayBaseline | null;
  cacheReplayCandidates: CacheReplayCandidate[];
  cacheReplayComparisonArtifacts: JobArtifact[];
  cacheReplayComparisonJob: LocalizationJob | null;
  cacheReplayComparisonJobId: string;
  cacheReplayError: string | null;
  isLoadingCacheReplayComparison: boolean;
  onCancel: () => void;
  onClearSubtitleDraft: () => void;
  onPinCacheReplayBaseline: () => void;
  onSelectCacheReplayComparison: (jobId: string) => void;
  onRetry: () => void;
  onPublishReviewedSubtitles: (includeBurnedVideo: boolean) => void;
  onSaveSubtitleDraft: (segments: Array<{ index: number; text: string }>) => void;
  previewErrors: string[];
  selectedLanguage: string;
  subtitleDraft: SubtitleDraftSummary | null;
  subtitleDraftError: string | null;
  subtitleDraftStatus: string | null;
  subtitleReview: SubtitleReviewSummary | null;
  subtitles: SubtitleSegment[];
  transcript: TranscriptSegment[];
}) {
  const estimatedCost = formatCost(job.usageSummary?.estimatedCostUsd ?? 0);
  const modelCallLabel = `${job.usageSummary?.modelCallCount ?? job.modelCalls.length} calls`;
  const deliverables = useMemo(
    () => buildResultDeliverables(artifacts, transcript.length > 0, subtitles.length > 0),
    [artifacts, subtitles.length, transcript.length]
  );
  const demoEvidence = useMemo(
    () => buildDemoEvidence(job, artifacts, transcript.length, subtitles.length, selectedLanguage, subtitleReview, subtitleDraft),
    [artifacts, job, selectedLanguage, subtitleDraft, subtitleReview, subtitles.length, transcript.length]
  );
  const demoEvidenceMarkdown = useMemo(
    () => formatDemoEvidenceMarkdown(demoEvidence),
    [demoEvidence]
  );
  const demoHandoffChecklist = useMemo(
    () => buildDemoHandoffChecklist(job, artifacts, deliveryManifest, subtitleReview, subtitleDraft, demoEvidence),
    [artifacts, deliveryManifest, demoEvidence, job, subtitleDraft, subtitleReview]
  );
  const demoSessionReport = useMemo(
    () => buildDemoSessionReport(job, artifacts, deliveryManifest, demoEvidence, demoHandoffChecklist),
    [artifacts, deliveryManifest, demoEvidence, demoHandoffChecklist, job]
  );

  const statusItems = useMemo(
    () => [
      ['Status', job.status],
      ['Stage', job.failureStage ?? job.timelineEvents.at(-1)?.stage ?? 'Queued'],
      ['Language', selectedLanguage],
      ['TTS voice', formatVoice(job.ttsVoice)],
      ['Retries', String(job.retryCount)]
    ],
    [job, selectedLanguage]
  );
  return (
    <div className="job-detail">
      <header className="job-header">
        <div>
          <p className="eyebrow">Selected job</p>
          <h2>Job {job.jobId}</h2>
          {job.failureReason ? <p className="failure-text">{job.failureReason}</p> : null}
        </div>
        <div className="job-actions">
          {isLoadingJob ? <span className="muted">Refreshing...</span> : null}
          <a className="secondary-link" href={linguaFrameApi.jobDiagnosticsDownloadUrl(job.jobId)}>
            Download diagnostics
          </a>
          {canCancel ? (
            <button
              type="button"
              className="secondary-button"
              onClick={onCancel}
              disabled={isCancelling}
            >
              {isCancelling ? 'Cancelling...' : 'Cancel'}
            </button>
          ) : null}
          {canRetry ? (
            <button type="button" onClick={onRetry} disabled={isRetrying}>
              {isRetrying ? 'Retrying...' : 'Retry'}
            </button>
          ) : null}
        </div>
      </header>

      <dl className="status-grid">
        {statusItems.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>

      <section className="metrics-grid" aria-label="Usage summary">
        <div>
          <span>Model calls</span>
          <strong>{modelCallLabel}</strong>
        </div>
        <div>
          <span>Estimated cost</span>
          <strong>{estimatedCost}</strong>
        </div>
        <div>
          <span>Latency</span>
          <strong>{job.usageSummary?.totalLatencyMs ?? 0} ms</strong>
        </div>
        <div>
          <span>Cache hits</span>
          <strong>
            {job.cacheSummary.cacheHitCount} artifacts / {job.cacheSummary.providerCacheHitCount} provider
          </strong>
        </div>
      </section>

      <ResultDeliveryPanel
        deliverables={deliverables}
        estimatedCost={estimatedCost}
        job={job}
        modelCallLabel={modelCallLabel}
      />

      <DeliveryHandoffPanel
        error={deliveryManifestError}
        jobId={job.jobId}
        manifest={deliveryManifest}
      />

      <DemoHandoffChecklistPanel checklist={demoHandoffChecklist} jobId={job.jobId} />

      <PipelineProgressPanel progress={job.pipelineProgress} />

      <FailureTriagePanel triage={job.failureTriage} />

      <DemoSessionReportPanel report={demoSessionReport} jobId={job.jobId} />

      <DemoEvidencePanel evidence={demoEvidence} markdown={demoEvidenceMarkdown} />

      <CacheReplayPanel
        baseline={cacheReplayBaseline}
        candidates={cacheReplayCandidates}
        comparisonArtifacts={cacheReplayComparisonArtifacts}
        comparisonJob={cacheReplayComparisonJob}
        comparisonJobId={cacheReplayComparisonJobId}
        error={cacheReplayError}
        isLoadingComparison={isLoadingCacheReplayComparison}
        onPinBaseline={onPinCacheReplayBaseline}
        onSelectComparison={onSelectCacheReplayComparison}
      />

      <QualityEvaluationPanel evaluation={job.qualityEvaluation} />

      <SubtitleReviewPanel review={subtitleReview} />

      <SubtitleDraftEditorPanel
        draft={subtitleDraft}
        error={subtitleDraftError}
        isClearing={isClearingSubtitleDraft}
        isPublishing={isPublishingReviewedSubtitles}
        isSaving={isSavingSubtitleDraft}
        jobId={job.jobId}
        onClear={onClearSubtitleDraft}
        onPublish={onPublishReviewedSubtitles}
        onSave={onSaveSubtitleDraft}
        status={subtitleDraftStatus}
      />

      <section className="panel" aria-label="Timeline">
        <h3>Timeline</h3>
        {job.timelineEvents.length === 0 ? (
          <p className="muted">No timeline events yet.</p>
        ) : (
          <ol className="timeline">
            {job.timelineEvents.map((event) => (
              <li key={event.id}>
                <strong>{event.stage}</strong>
                <span>{event.status}</span>
                <p>{event.errorSummary ?? event.message}</p>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="panel" aria-label="Model calls">
        <h3>Model calls</h3>
        {job.modelCalls.length === 0 ? (
          <p className="muted">No model calls recorded yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Operation</th>
                <th>Provider</th>
                <th>Status</th>
                <th>Input</th>
                <th>Output</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {job.modelCalls.map((call) => (
                <tr key={call.modelCallId}>
                  <td>{call.operation}</td>
                  <td>{call.provider}</td>
                  <td>{call.status}</td>
                  <td>{call.inputSummary ?? '-'}</td>
                  <td>{call.outputSummary ?? '-'}</td>
                  <td>{formatCost(call.estimatedCostUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="preview-grid" aria-label="Output previews">
        <section className="panel" aria-label="Transcript preview">
          <h3>Transcript preview</h3>
          {transcript.length === 0 ? (
            <p className="muted">No transcript segments yet.</p>
          ) : (
            <SegmentList
              segments={transcript.map((segment) => ({
                key: String(segment.index),
                label: formatTimeRange(segment.startMs, segment.endMs),
                text: segment.text
              }))}
            />
          )}
        </section>

        <section className="panel" aria-label="Subtitle preview">
          <h3>Subtitle preview</h3>
          {subtitles.length === 0 ? (
            <p className="muted">No subtitle segments yet.</p>
          ) : (
            <>
              <p className="muted">{selectedLanguage}</p>
              <SegmentList
                segments={subtitles.map((segment) => ({
                  key: `${segment.language}-${segment.index}`,
                  label: formatTimeRange(segment.startMs, segment.endMs),
                  text: segment.text
                }))}
              />
            </>
          )}
        </section>
      </section>

      <section className="panel" aria-label="Artifacts">
        <div className="panel-heading artifact-panel-heading">
          <h3>Artifacts</h3>
          <a className="secondary-link" href={linguaFrameApi.artifactArchiveDownloadUrl(job.jobId)}>
            Download result bundle
          </a>
        </div>
        {previewErrors.length > 0 ? (
          <ul className="error-list">
            {previewErrors.map((previewError) => (
              <li key={previewError}>{previewError}</li>
            ))}
          </ul>
        ) : null}
        {artifacts.length === 0 ? (
          <p className="muted">No artifacts yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Filename</th>
                <th>Content type</th>
                <th>Size</th>
                <th>SHA-256</th>
                <th>Cache</th>
                <th>Download</th>
              </tr>
            </thead>
            <tbody>
              {artifacts.map((artifact) => (
                <tr key={artifact.artifactId}>
                  <td>{artifact.type}</td>
                  <td>{artifact.filename}</td>
                  <td>{artifact.contentType}</td>
                  <td>{formatBytes(artifact.sizeBytes)}</td>
                  <td title={artifact.contentSha256 || undefined}>
                    {formatArtifactHash(artifact.contentSha256)}
                  </td>
                  <td title={artifact.sourceArtifactId ?? undefined}>
                    {artifact.cacheHit ? 'Reused' : 'Generated'}
                  </td>
                  <td>
                    <a href={linguaFrameApi.artifactDownloadUrl(job.jobId, artifact.artifactId)}>
                      Download {artifact.filename}
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <MediaDeliveryPanel jobId={job.jobId} artifacts={artifacts} />
    </div>
  );
}

function MediaDeliveryPanel({ artifacts, jobId }: { artifacts: JobArtifact[]; jobId: string }) {
  const mediaItems = buildMediaDeliveryItems(artifacts).filter((item) => item.artifact);
  if (mediaItems.length === 0) {
    return null;
  }

  return (
    <section className="panel media-delivery-panel" aria-label="Media delivery">
      <div className="panel-heading">
        <h3>Media delivery</h3>
        <p className="muted">{mediaItems.length} playable outputs</p>
      </div>
      <div className="media-delivery-grid">
        {mediaItems.map((item) => {
          const artifact = item.artifact;
          if (!artifact) {
            return null;
          }
          const downloadUrl = linguaFrameApi.artifactDownloadUrl(jobId, artifact.artifactId);
          return (
            <article className="media-card" key={item.key}>
              <div className="media-card-heading">
                <h4>{item.label}</h4>
                <span title={artifact.sourceArtifactId ?? undefined}>
                  {artifact.cacheHit ? 'Reused' : 'Generated'}
                </span>
              </div>
              {item.kind === 'audio' ? (
                <audio aria-label={item.playerLabel} controls src={downloadUrl} />
              ) : (
                <video aria-label={item.playerLabel} controls src={downloadUrl} />
              )}
              <dl className="media-card-meta">
                <div>
                  <dt>Filename</dt>
                  <dd>{artifact.filename}</dd>
                </div>
                <div>
                  <dt>Type</dt>
                  <dd>{artifact.contentType}</dd>
                </div>
                <div>
                  <dt>Size</dt>
                  <dd>{formatBytes(artifact.sizeBytes)}</dd>
                </div>
                <div>
                  <dt>SHA-256</dt>
                  <dd title={artifact.contentSha256 || undefined}>
                    {formatArtifactHash(artifact.contentSha256)}
                  </dd>
                </div>
              </dl>
              <div className="media-card-actions">
                <a href={downloadUrl}>Download {item.label}</a>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function ResultDeliveryPanel({
  deliverables,
  estimatedCost,
  job,
  modelCallLabel
}: {
  deliverables: ResultDeliverable[];
  estimatedCost: string;
  job: LocalizationJob;
  modelCallLabel: string;
}) {
  const generatedCount = deliverables.filter(
    (deliverable) => deliverable.artifact && !deliverable.artifact.cacheHit
  ).length;
  const reusedCount = deliverables.filter((deliverable) => deliverable.artifact?.cacheHit).length;
  const missingCount = deliverables.filter((deliverable) => deliverable.status === 'Missing').length;

  return (
    <section className="panel result-delivery-panel" aria-label="Result delivery">
      <div className="panel-heading">
        <h3>Result delivery</h3>
        <div className="panel-actions">
          <a className="secondary-link" href={linguaFrameApi.artifactArchiveDownloadUrl(job.jobId)}>
            Download result bundle
          </a>
          <a className="secondary-link" href={linguaFrameApi.jobDiagnosticsDownloadUrl(job.jobId)}>
            Download diagnostics
          </a>
        </div>
      </div>

      <dl className="metrics-grid result-delivery-metrics">
        <div>
          <dt>Generated</dt>
          <dd>{generatedCount} generated</dd>
        </div>
        <div>
          <dt>Reused</dt>
          <dd>{reusedCount} reused</dd>
        </div>
        <div>
          <dt>Missing</dt>
          <dd>{missingCount} missing</dd>
        </div>
        <div>
          <dt>Model calls</dt>
          <dd>{modelCallLabel}</dd>
        </div>
        <div>
          <dt>Estimated cost</dt>
          <dd>{estimatedCost}</dd>
        </div>
      </dl>

      <ul className="result-deliverable-list">
        {deliverables.map((deliverable) => (
          <li key={deliverable.definition.key}>
            <div className="result-deliverable-main">
              <strong>{deliverable.definition.label}</strong>
              <span className={resultStatusClassName(deliverable.status)}>{deliverable.status}</span>
            </div>
            {deliverable.artifact ? (
              <div className="result-deliverable-meta">
                <span>{deliverable.artifact.filename}</span>
                <span>SHA-256</span>
                <span title={deliverable.artifact.contentSha256 || undefined}>
                  {formatArtifactHash(deliverable.artifact.contentSha256)}
                </span>
                <span title={deliverable.artifact.sourceArtifactId ?? undefined}>
                  {deliverable.artifact.cacheHit ? 'Reused' : 'Generated'}
                </span>
                <a href={linguaFrameApi.artifactDownloadUrl(job.jobId, deliverable.artifact.artifactId)}>
                  Download {deliverable.definition.label}
                </a>
              </div>
            ) : (
              <p className="muted">
                {deliverable.status === 'Preview only'
                  ? 'Preview data is available, but no downloadable artifact has been stored yet.'
                  : 'No preview or downloadable artifact is available yet.'}
              </p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function DeliveryHandoffPanel({
  error,
  jobId,
  manifest
}: {
  error: string | null;
  jobId: string;
  manifest: DeliveryManifest | null;
}) {
  if (error) {
    return (
      <section className="panel" aria-label="Delivery handoff">
        <h3>Delivery handoff</h3>
        <p className="error-text">{error}</p>
      </section>
    );
  }
  if (!manifest) {
    return (
      <section className="panel" aria-label="Delivery handoff">
        <h3>Delivery handoff</h3>
        <p className="muted">Delivery manifest is not loaded.</p>
      </section>
    );
  }

  return (
    <section className="panel delivery-handoff-panel" aria-label="Delivery handoff">
      <div className="panel-heading">
        <div>
          <h3>Delivery handoff</h3>
          <p className={manifest.handoffReady ? 'status-pill success' : 'status-pill warning'}>
            {manifest.handoffReady ? 'Ready for handoff' : 'Needs reviewed subtitle publish'}
          </p>
        </div>
        <div className="panel-actions">
          <a className="secondary-link" href={linguaFrameApi.jobHandoffPackageDownloadUrl(jobId)}>
            Download handoff package
          </a>
          <a className="secondary-link" href={linguaFrameApi.deliveryManifestMarkdownDownloadUrl(jobId)}>
            Download delivery manifest
          </a>
        </div>
      </div>

      <dl className="metrics-grid result-delivery-metrics">
        <div>
          <dt>Reviewed subtitles</dt>
          <dd>{manifest.reviewedSubtitleArtifactCount} files</dd>
        </div>
        <div>
          <dt>Reviewed video</dt>
          <dd>{manifest.reviewedBurnedVideoAvailable ? 'Available' : 'Not available'}</dd>
        </div>
        <div>
          <dt>Audit artifacts</dt>
          <dd>{manifest.generatedArtifactCount} files</dd>
        </div>
      </dl>

      <ManifestArtifactList title="Reviewed handoff artifacts" artifacts={manifest.reviewedArtifacts} />
      <ManifestArtifactList title="Audit artifacts" artifacts={manifest.auditArtifacts} />

      {manifest.links.length > 0 ? (
        <ul className="handoff-link-list" aria-label="Delivery verification links">
          {manifest.links.map((link) => (
            <li key={`${link.kind}-${link.url}`}>
              <a href={link.url}>{link.label}</a>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function ManifestArtifactList({
  artifacts,
  title
}: {
  artifacts: DeliveryManifest['reviewedArtifacts'];
  title: string;
}) {
  return (
    <div className="manifest-artifact-group">
      <h4>{title}</h4>
      {artifacts.length === 0 ? (
        <p className="muted">No artifacts in this group.</p>
      ) : (
        <ul className="result-deliverable-list">
          {artifacts.map((artifact) => (
            <li key={artifact.artifactId}>
              <div className="result-deliverable-main">
                <strong>{artifact.filename}</strong>
                <span className="status-pill">{artifact.role}</span>
              </div>
              <div className="result-deliverable-meta">
                <span>{artifact.type}</span>
                <span>{formatBytes(artifact.sizeBytes)}</span>
                <span title={artifact.shortSha256}>SHA-256 {artifact.shortSha256}</span>
                <span>{artifact.cacheState}</span>
                <a href={artifact.downloadUrl}>Download {artifact.filename}</a>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function PipelineProgressPanel({ progress }: { progress: LocalizationJob['pipelineProgress'] }) {
  if (!progress) {
    return null;
  }

  return (
    <section className="panel" aria-label="Pipeline progress">
      <h3>Pipeline progress</h3>
      <dl className="status-grid compact-status-grid">
        <div>
          <dt>Current stage</dt>
          <dd>{progress.currentStage ?? 'Queued'}</dd>
        </div>
        <div>
          <dt>Completed</dt>
          <dd>
            {progress.completedStageCount} / {progress.totalStageCount}
          </dd>
        </div>
        <div>
          <dt>Terminal</dt>
          <dd>{progress.terminal ? 'Yes' : 'No'}</dd>
        </div>
        <div>
          <dt>Measured time</dt>
          <dd>{formatDurationMs(progress.totalMeasuredDurationMs)}</dd>
        </div>
        <div>
          <dt>Slowest stage</dt>
          <dd>
            {progress.slowestStage
              ? `${progress.slowestStage} · ${formatDurationMs(progress.slowestStageDurationMs ?? 0)}`
              : 'Not measured'}
          </dd>
        </div>
        <div>
          <dt>Other states</dt>
          <dd>
            {progress.failedStageCount} failed / {progress.skippedStageCount} skipped /{' '}
            {progress.cacheHitStageCount} cache hits
          </dd>
        </div>
      </dl>
      {progress.stages.length > 0 ? (
        <ul className="recent-list" aria-label="Pipeline stages">
          {progress.stages.map((stage) => (
            <li key={stage.stage}>
              <span>{stage.stage}</span>
              <span className="history-meta">
                {stage.status ?? 'PENDING'} · {formatDurationMs(stage.durationMs ?? 0)}
              </span>
              {stage.message ? <small>{stage.message}</small> : null}
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">No measured stage events yet.</p>
      )}
    </section>
  );
}

function FailureTriagePanel({ triage }: { triage: FailureTriage | null }) {
  if (!triage) {
    return null;
  }

  return (
    <section className="panel" aria-label="Failure triage">
      <h3>Failure triage</h3>
      <dl className="status-grid">
        <div>
          <dt>Category</dt>
          <dd>{triage.category}</dd>
        </div>
        <div>
          <dt>Retryable</dt>
          <dd>{triage.retryable ? 'Yes' : 'No'}</dd>
        </div>
        {triage.runbookCommand ? (
          <div>
            <dt>Runbook</dt>
            <dd>{triage.runbookCommand}</dd>
          </div>
        ) : null}
      </dl>
      <p>{triage.summary}</p>
      <p className="mode-line">{triage.recommendedAction}</p>
      {triage.safeDetails.length > 0 ? (
        <ul>
          {triage.safeDetails.map((detail) => (
            <li key={detail}>{detail}</li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

function DemoHandoffChecklistPanel({
  checklist,
  jobId
}: {
  checklist: DemoHandoffChecklist;
  jobId: string;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';
  const passCount = checklist.items.filter((item) => item.status === 'PASS').length;
  const warnCount = checklist.items.filter((item) => item.status === 'WARN').length;
  const failCount = checklist.items.filter((item) => item.status === 'FAIL').length;
  const markdown = formatDemoHandoffChecklistMarkdown(checklist, jobId);

  const handleCopyChecklist = useCallback(async () => {
    if (!canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Checklist copied.');
  }, [canCopy, markdown]);

  const handleDownloadChecklist = useCallback(() => {
    const blob = new Blob([JSON.stringify({ jobId, ...checklist }, null, 2)], {
      type: 'application/json'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-demo-handoff-${sanitizeFilename(jobId)}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Checklist JSON downloaded.');
  }, [checklist, jobId]);

  return (
    <section className="panel handoff-checklist-panel" aria-label="Demo handoff checklist">
      <div className="panel-heading">
        <div>
          <h3>Demo handoff checklist</h3>
          <p className="muted">{checklist.summary}</p>
        </div>
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={!canCopy}
            onClick={handleCopyChecklist}
          >
            Copy checklist
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadChecklist}>
            Download checklist JSON
          </button>
        </div>
      </div>
      <p className={checklist.overallStatus === 'READY' ? 'status-pill success' : 'status-pill warning'}>
        {checklist.overallStatus === 'READY' ? 'Ready for demo handoff' : 'Needs attention'}
      </p>
      {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      <dl className="metrics-grid checklist-summary">
        <div>
          <dt>Passed</dt>
          <dd>{passCount}</dd>
        </div>
        <div>
          <dt>Warnings</dt>
          <dd>{warnCount}</dd>
        </div>
        <div>
          <dt>Failures</dt>
          <dd>{failCount}</dd>
        </div>
      </dl>
      <ul className="checklist-list">
        {checklist.items.map((item) => (
          <li key={item.key}>
            <span className={checklistStatusClassName(item.status)}>{item.status}</span>
            <strong>{item.label}</strong>
            <p>{item.detail}</p>
          </li>
        ))}
      </ul>
      <div className="panel-actions checklist-links">
        <a className="secondary-link" href={checklist.links.resultBundle}>
          Download result bundle
        </a>
        <a className="secondary-link" href={checklist.links.diagnostics}>
          Download diagnostics
        </a>
        <a className="secondary-link" href={checklist.links.evidenceMarkdown}>
          Download backend evidence
        </a>
        <a className="secondary-link" href={checklist.links.evidenceBundle}>
          Download evidence bundle
        </a>
        <a className="secondary-link" href={linguaFrameApi.jobHandoffPackageDownloadUrl(jobId)}>
          Download handoff package
        </a>
      </div>
    </section>
  );
}

function DemoSessionReportPanel({
  jobId,
  report
}: {
  jobId: string;
  report: DemoSessionReport;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';
  const markdown = formatDemoSessionReportMarkdown(report, jobId);

  const handleCopyReport = useCallback(async () => {
    if (!canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Report copied.');
  }, [canCopy, markdown]);

  const handleDownloadReport = useCallback(() => {
    const blob = new Blob([markdown], {
      type: 'text/markdown'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-demo-session-${sanitizeFilename(jobId)}.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Report Markdown downloaded.');
  }, [jobId, markdown]);

  return (
    <section className="panel demo-session-report-panel" aria-label="Demo session report">
      <div className="panel-heading">
        <div>
          <h3>Demo session report</h3>
          <p className="muted">{report.title}</p>
        </div>
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={!canCopy}
            onClick={handleCopyReport}
          >
            Copy report
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadReport}>
            Download report Markdown
          </button>
        </div>
      </div>
      <p className={report.status === 'READY' ? 'status-pill success' : 'status-pill warning'}>
        {report.status === 'READY' ? 'Session ready' : 'Session needs attention'}
      </p>
      {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      <div className="session-report-grid">
        {report.sections.map((section) => (
          <article className="session-report-section" key={section.title}>
            <h4>{section.title}</h4>
            <ul>
              {section.lines.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </article>
        ))}
      </div>
      <div className="panel-actions session-report-links">
        <a className="secondary-link" href={report.links.resultBundle}>
          Download result bundle
        </a>
        <a className="secondary-link" href={report.links.diagnostics}>
          Download diagnostics
        </a>
        <a className="secondary-link" href={report.links.evidenceMarkdown}>
          Download backend evidence
        </a>
        <a className="secondary-link" href={report.links.evidenceBundle}>
          Download evidence bundle
        </a>
        <a className="secondary-link" href={linguaFrameApi.jobHandoffPackageDownloadUrl(jobId)}>
          Download handoff package
        </a>
      </div>
    </section>
  );
}

function DemoEvidencePanel({
  evidence,
  markdown
}: {
  evidence: DemoEvidence;
  markdown: string;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';

  const handleCopyEvidence = useCallback(async () => {
    if (!canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Evidence copied.');
  }, [canCopy, markdown]);

  const handleDownloadEvidence = useCallback(() => {
    const blob = new Blob([JSON.stringify(evidence, null, 2)], {
      type: 'application/json'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-demo-evidence-${sanitizeFilename(evidence.job.jobId)}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Evidence JSON downloaded.');
  }, [evidence]);

  return (
    <section className="panel demo-evidence-panel" aria-label="Demo evidence">
      <div className="panel-heading">
        <h3>Demo evidence</h3>
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={handleCopyEvidence}
            disabled={!canCopy}
          >
            Copy evidence
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadEvidence}>
            Download evidence JSON
          </button>
          <a className="secondary-link" href={evidence.links.evidenceMarkdown}>
            Download backend evidence
          </a>
          <a className="secondary-link" href={evidence.links.evidenceBundle}>
            Download evidence bundle
          </a>
        </div>
      </div>
      {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      <pre className="evidence-preview">{markdown}</pre>
    </section>
  );
}

function CacheReplayPanel({
  baseline,
  candidates,
  comparisonArtifacts,
  comparisonJob,
  comparisonJobId,
  error,
  isLoadingComparison,
  onPinBaseline,
  onSelectComparison
}: {
  baseline: CacheReplayBaseline | null;
  candidates: CacheReplayCandidate[];
  comparisonArtifacts: JobArtifact[];
  comparisonJob: LocalizationJob | null;
  comparisonJobId: string;
  error: string | null;
  isLoadingComparison: boolean;
  onPinBaseline: () => void;
  onSelectComparison: (jobId: string) => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';
  const evidence = useMemo(
    () =>
      baseline && comparisonJob
        ? buildCacheReplayEvidence(baseline.job, baseline.artifacts, comparisonJob, comparisonArtifacts)
        : null,
    [baseline, comparisonArtifacts, comparisonJob]
  );
  const markdown = useMemo(
    () => (evidence ? formatCacheReplayEvidenceMarkdown(evidence) : ''),
    [evidence]
  );

  const handleCopy = useCallback(async () => {
    if (!canCopy || !markdown) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Replay evidence copied.');
  }, [canCopy, markdown]);

  const handleDownload = useCallback(() => {
    if (!evidence) {
      return;
    }
    const blob = new Blob([JSON.stringify(evidence, null, 2)], {
      type: 'application/json'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-cache-replay-${sanitizeFilename(evidence.baseline.jobId)}-${sanitizeFilename(
      evidence.comparison.jobId
    )}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Replay evidence JSON downloaded.');
  }, [evidence]);

  return (
    <section className="panel cache-replay-panel" aria-label="Cache replay">
      <div className="panel-heading">
        <h3>Cache replay</h3>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onPinBaseline}>
            Pin as baseline
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={handleCopy}
            disabled={!evidence || !canCopy}
          >
            Copy replay evidence
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={handleDownload}
            disabled={!evidence}
          >
            Download replay evidence JSON
          </button>
        </div>
      </div>

      {!baseline ? <p className="muted">Pin the selected job as a baseline before comparing runs.</p> : null}
      {baseline ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Baseline</dt>
              <dd>{baseline.job.jobId}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>{baseline.job.status}</dd>
            </div>
            <div>
              <dt>Model calls</dt>
              <dd>{modelCallCount(baseline.job)} calls</dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatCost(jobEstimatedCost(baseline.job))}</dd>
            </div>
          </dl>

          <label>
            Comparison job
            <select
              value={comparisonJobId}
              onChange={(event) => void onSelectComparison(event.target.value)}
            >
              <option value="">Choose a completed replay job</option>
              {candidates.map((candidate) => (
                <option key={candidate.jobId} value={candidate.jobId}>
                  {candidate.filename} · {candidate.jobId} · {candidate.status}
                </option>
              ))}
            </select>
          </label>
          {candidates.length === 0 ? <p className="muted">No comparison candidates loaded yet.</p> : null}
        </>
      ) : null}

      {isLoadingComparison ? <p className="muted">Loading comparison...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}

      {baseline && comparisonJob && evidence ? (
        <>
          {baseline.job.status !== 'COMPLETED' || comparisonJob.status !== 'COMPLETED' ? (
            <p className="muted">Cache replay evidence is strongest when both jobs are completed.</p>
          ) : null}
          <dl className="metrics-grid cache-replay-grid">
            <div>
              <dt>Comparison</dt>
              <dd>{comparisonJob.jobId}</dd>
            </div>
            <div>
              <dt>Provider cache</dt>
              <dd>{evidence.comparison.providerCacheHitCount} provider hits</dd>
            </div>
            <div>
              <dt>Artifacts</dt>
              <dd>
                {evidence.comparison.artifactCacheHitCount} reused /{' '}
                {evidence.comparison.generatedArtifactCount} generated
              </dd>
            </div>
            <div>
              <dt>Model calls</dt>
              <dd>{formatSignedInteger(evidence.delta.modelCalls)} calls</dd>
            </div>
            <div>
              <dt>Estimated cost</dt>
              <dd>{formatSignedCost(evidence.delta.estimatedCostUsd)}</dd>
            </div>
          </dl>
          <div className="cache-stage-list">
            <h4>Cache-hit stages</h4>
            {evidence.providerCacheHitStages.length === 0 ? (
              <p className="muted">No provider cache-hit stages recorded.</p>
            ) : (
              <ul>
                {evidence.providerCacheHitStages.map((stage) => (
                  <li key={stage}>{stage}</li>
                ))}
              </ul>
            )}
          </div>
        </>
      ) : null}
    </section>
  );
}

function QualityEvaluationPanel({
  evaluation
}: {
  evaluation: LocalizationJob['qualityEvaluation'];
}) {
  if (!evaluation) {
    return (
      <section className="panel" aria-label="Quality evaluation">
        <h3>Quality evaluation</h3>
        <p className="muted">No quality evaluation recorded yet.</p>
      </section>
    );
  }

  const dimensionScores = [
    ['Completeness', evaluation.completeness],
    ['Readability', evaluation.readability],
    ['Timing', evaluation.timingPreservation],
    ['Naturalness', evaluation.naturalness]
  ];

  return (
    <section className="panel" aria-label="Quality evaluation">
      <div className="panel-heading">
        <h3>Quality evaluation</h3>
        <span className="status-pill">{evaluation.status}</span>
      </div>
      {evaluation.status === 'FAILED' && evaluation.safeErrorSummary ? (
        <p className="error-text">{evaluation.safeErrorSummary}</p>
      ) : null}
      <div className="quality-overview">
        <div>
          <span>Score</span>
          <strong>{evaluation.score} / 100</strong>
        </div>
        <div>
          <span>Verdict</span>
          <strong>{evaluation.verdict}</strong>
        </div>
        <div>
          <span>Language</span>
          <strong>{evaluation.language}</strong>
        </div>
      </div>
      <dl className="quality-dimensions">
        {dimensionScores.map(([label, score]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{score}</dd>
          </div>
        ))}
      </dl>
      <div className="quality-lists">
        <div>
          <h4>Issues</h4>
          {evaluation.issues.length === 0 ? (
            <p className="muted">No issues recorded.</p>
          ) : (
            <ul>
              {evaluation.issues.map((issue) => (
                <li key={issue}>{issue}</li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <h4>Suggested fixes</h4>
          {evaluation.suggestedFixes.length === 0 ? (
            <p className="muted">No fixes suggested.</p>
          ) : (
            <ul>
              {evaluation.suggestedFixes.map((fix) => (
                <li key={fix}>{fix}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
}

function SubtitleReviewPanel({ review }: { review: SubtitleReviewSummary | null }) {
  if (!review) {
    return (
      <section className="panel" aria-label="Subtitle review">
        <h3>Subtitle review</h3>
        <p className="muted">No subtitle review summary loaded yet.</p>
      </section>
    );
  }

  return (
    <section className="panel" aria-label="Subtitle review">
      <div className="panel-heading">
        <h3>Subtitle review</h3>
        <span className="status-pill">{review.targetLanguage}</span>
      </div>
      <dl className="status-grid compact-status-grid">
        <div>
          <dt>Segments</dt>
          <dd>{review.segmentCount}</dd>
        </div>
        <div>
          <dt>Missing targets</dt>
          <dd>{review.missingTargetCount}</dd>
        </div>
        <div>
          <dt>Timing mismatches</dt>
          <dd>{review.timingMismatchCount}</dd>
        </div>
        <div>
          <dt>Average duration</dt>
          <dd>{formatDurationMs(review.averageDurationMs)}</dd>
        </div>
        <div>
          <dt>Max duration</dt>
          <dd>{formatDurationMs(review.maxDurationMs)}</dd>
        </div>
        <div>
          <dt>Quality</dt>
          <dd>
            {review.qualityScore === null
              ? 'Not evaluated'
              : `${review.qualityScore} / 100 · ${review.qualityVerdict ?? 'No verdict'}`}
          </dd>
        </div>
        <div>
          <dt>Quality notes</dt>
          <dd>
            {review.qualityIssueCount} issues / {review.qualitySuggestedFixCount} fixes
          </dd>
        </div>
        <div>
          <dt>Subtitle artifacts</dt>
          <dd>{review.downloadableSubtitleArtifactCount} files</dd>
        </div>
      </dl>
      {review.segments.length === 0 ? (
        <p className="muted">No transcript segments are available for review.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Source</th>
              <th>Target</th>
              <th>Delta</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {review.segments.map((segment) => (
              <tr key={segment.index}>
                <td>{formatTimeRange(segment.startMs, segment.endMs)}</td>
                <td>{segment.sourceText}</td>
                <td>{segment.targetText ?? '-'}</td>
                <td>{formatDurationMs(segment.timingDeltaMs)}</td>
                <td>{formatReviewStatus(segment.status)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function SubtitleDraftEditorPanel({
  draft,
  error,
  isClearing,
  isPublishing,
  isSaving,
  jobId,
  onClear,
  onPublish,
  onSave,
  status
}: {
  draft: SubtitleDraftSummary | null;
  error: string | null;
  isClearing: boolean;
  isPublishing: boolean;
  isSaving: boolean;
  jobId: string;
  onClear: () => void;
  onPublish: (includeBurnedVideo: boolean) => void;
  onSave: (segments: Array<{ index: number; text: string }>) => void;
  status: string | null;
}) {
  const [draftTextByIndex, setDraftTextByIndex] = useState<Record<number, string>>({});
  const [includeReviewedBurnedVideo, setIncludeReviewedBurnedVideo] = useState(false);

  useEffect(() => {
    if (!draft) {
      setDraftTextByIndex({});
      return;
    }
    setDraftTextByIndex(
      Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.draftText]))
    );
  }, [draft]);

  const dirtySegments = useMemo(() => {
    if (!draft) {
      return [];
    }
    return draft.segments
      .filter((segment) => (draftTextByIndex[segment.index] ?? segment.draftText) !== segment.draftText)
      .map((segment) => ({
        index: segment.index,
        text: draftTextByIndex[segment.index] ?? segment.draftText
      }));
  }, [draft, draftTextByIndex]);

  const handleReset = useCallback(() => {
    if (!draft) {
      return;
    }
    setDraftTextByIndex(
      Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.draftText]))
    );
  }, [draft]);

  if (!draft) {
    return (
      <section className="panel" aria-label="Subtitle draft editor">
        <h3>Subtitle draft editor</h3>
        {error ? <p className="muted">{error}</p> : <p className="muted">No editable subtitle draft loaded yet.</p>}
      </section>
    );
  }

  return (
    <section className="panel" aria-label="Subtitle draft editor">
      <div className="panel-heading">
        <h3>Subtitle draft editor</h3>
        <span className="status-pill">{draft.targetLanguage}</span>
      </div>
      <dl className="status-grid compact-status-grid">
        <div>
          <dt>Segments</dt>
          <dd>{draft.segmentCount}</dd>
        </div>
        <div>
          <dt>Saved edits</dt>
          <dd>{draft.editedSegmentCount}</dd>
        </div>
        <div>
          <dt>Unsaved edits</dt>
          <dd>{dirtySegments.length}</dd>
        </div>
        <div>
          <dt>Last saved</dt>
          <dd>{draft.lastUpdatedAt ? formatIsoDateTime(draft.lastUpdatedAt) : 'Not saved'}</dd>
        </div>
      </dl>
      {error ? <p className="failure-text">{error}</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      <div className="panel-actions">
        <button
          type="button"
          onClick={() => onSave(dirtySegments)}
          disabled={dirtySegments.length === 0 || isSaving}
        >
          {isSaving ? 'Saving...' : 'Save draft'}
        </button>
        <button
          type="button"
          className="secondary-button"
          onClick={handleReset}
          disabled={dirtySegments.length === 0 || isSaving}
        >
          Reset unsaved
        </button>
        <button
          type="button"
          className="secondary-button"
          onClick={onClear}
          disabled={draft.editedSegmentCount === 0 || isClearing}
        >
          {isClearing ? 'Clearing...' : 'Clear draft'}
        </button>
        <label className="inline-checkbox">
          <input
            type="checkbox"
            checked={includeReviewedBurnedVideo}
            onChange={(event) => setIncludeReviewedBurnedVideo(event.target.checked)}
          />
          Include reviewed burned video
        </label>
        <button
          type="button"
          onClick={() => onPublish(includeReviewedBurnedVideo)}
          disabled={isPublishing}
        >
          {isPublishing ? 'Publishing...' : 'Publish reviewed subtitles'}
        </button>
        <a className="secondary-link" href={linguaFrameApi.subtitleDraftExportUrl(jobId, draft.targetLanguage, 'json')}>
          Download corrected JSON
        </a>
        <a className="secondary-link" href={linguaFrameApi.subtitleDraftExportUrl(jobId, draft.targetLanguage, 'srt')}>
          Download corrected SRT
        </a>
        <a className="secondary-link" href={linguaFrameApi.subtitleDraftExportUrl(jobId, draft.targetLanguage, 'vtt')}>
          Download corrected VTT
        </a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Time</th>
            <th>Source</th>
            <th>Generated</th>
            <th>Draft</th>
          </tr>
        </thead>
        <tbody>
          {draft.segments.map((segment) => (
            <tr key={segment.index}>
              <td>{formatTimeRange(segment.startMs, segment.endMs)}</td>
              <td>{segment.sourceText}</td>
              <td>{segment.generatedText}</td>
              <td>
                <textarea
                  aria-label={`Draft text ${segment.index}`}
                  value={draftTextByIndex[segment.index] ?? segment.draftText}
                  onChange={(event) =>
                    setDraftTextByIndex((current) => ({
                      ...current,
                      [segment.index]: event.target.value
                    }))
                  }
                  rows={3}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function SegmentList({
  segments
}: {
  segments: Array<{ key: string; label: string; text: string }>;
}) {
  return (
    <ol className="segment-list">
      {segments.map((segment) => (
        <li key={segment.key}>
          <span>{segment.label}</span>
          <p>{segment.text}</p>
        </li>
      ))}
    </ol>
  );
}

function toRecentJob(upload: MediaUpload): RecentJob {
  return {
    jobId: upload.jobId,
    videoId: upload.videoId,
    targetLanguage: upload.targetLanguage,
    ttsVoice: upload.ttsVoice,
    filename: upload.filename,
    createdAt: upload.createdAt
  };
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unexpected frontend error.';
}

function supportsEventSource(): boolean {
  return typeof window.EventSource === 'function';
}

function buildResultDeliverables(
  artifacts: JobArtifact[],
  hasTranscriptPreview: boolean,
  hasSubtitlePreview: boolean
): ResultDeliverable[] {
  return RESULT_DELIVERABLES.map((definition) => {
    const artifact = artifacts.find((candidate) => candidate.type === definition.artifactType) ?? null;
    let status: DeliverableStatus = 'Missing';
    if (artifact) {
      status = 'Ready';
    } else if (
      (definition.preview === 'transcript' && hasTranscriptPreview) ||
      (definition.preview === 'subtitle' && hasSubtitlePreview)
    ) {
      status = 'Preview only';
    }
    return { definition, artifact, status };
  });
}

function buildMediaDeliveryItems(artifacts: JobArtifact[]): MediaDeliveryItem[] {
  return [
    {
      key: 'dubbing-audio',
      label: 'Dubbing audio',
      playerLabel: 'Dubbing audio player',
      kind: 'audio',
      artifact: findFirstArtifact(artifacts, 'DUBBING_AUDIO')
    },
    {
      key: 'generated-burned-video',
      label: 'Generated burned video',
      playerLabel: 'Generated burned video player',
      kind: 'video',
      artifact: findFirstArtifact(artifacts, 'BURNED_VIDEO')
    },
    {
      key: 'reviewed-burned-video',
      label: 'Reviewed burned video',
      playerLabel: 'Reviewed burned video player',
      kind: 'video',
      artifact: findFirstArtifact(artifacts, 'REVIEWED_BURNED_VIDEO')
    }
  ];
}

function findFirstArtifact(artifacts: JobArtifact[], type: JobArtifact['type']): JobArtifact | null {
  return artifacts.find((artifact) => artifact.type === type) ?? null;
}

function buildDemoHandoffChecklist(
  job: LocalizationJob,
  artifacts: JobArtifact[],
  manifest: DeliveryManifest | null,
  subtitleReview: SubtitleReviewSummary | null,
  subtitleDraft: SubtitleDraftSummary | null,
  evidence: DemoEvidence
): DemoHandoffChecklist {
  const reviewedSubtitleCount = countArtifacts(artifacts, [
    'REVIEWED_SUBTITLE_JSON',
    'REVIEWED_SUBTITLE_SRT',
    'REVIEWED_SUBTITLE_VTT'
  ]);
  const mediaOutputCount = countArtifacts(artifacts, [
    'DUBBING_AUDIO',
    'BURNED_VIDEO',
    'REVIEWED_BURNED_VIDEO'
  ]);
  const hasEvidenceLinks = Boolean(
    evidence.links.resultBundle &&
      evidence.links.diagnostics &&
      evidence.links.evidenceMarkdown &&
      evidence.links.evidenceBundle
  );
  const pipelineTerminal = job.pipelineProgress?.terminal ?? ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status);

  const items: DemoHandoffChecklistItem[] = [
    {
      key: 'job-completed',
      label: 'Job completed',
      status: job.status === 'COMPLETED' ? 'PASS' : 'FAIL',
      detail:
        job.status === 'COMPLETED'
          ? 'The selected job reached COMPLETED.'
          : `The selected job is ${job.status}${job.failureReason ? `: ${job.failureReason}` : '.'}`
    },
    {
      key: 'pipeline-terminal',
      label: 'Pipeline terminal',
      status: pipelineTerminal ? 'PASS' : 'WARN',
      detail: pipelineTerminal
        ? 'Pipeline progress is terminal or the job is in a terminal status.'
        : 'Pipeline progress is still active or not fully measured.'
    },
    {
      key: 'reviewed-subtitles',
      label: 'Reviewed subtitles ready',
      status: (manifest?.handoffReady ?? false) || reviewedSubtitleCount >= 3 ? 'PASS' : 'FAIL',
      detail:
        (manifest?.handoffReady ?? false) || reviewedSubtitleCount >= 3
          ? `${manifest?.reviewedSubtitleArtifactCount ?? reviewedSubtitleCount} reviewed subtitle artifacts are ready.`
          : 'Reviewed JSON/SRT/VTT artifacts are not all available yet.'
    },
    {
      key: 'media-outputs',
      label: 'Media outputs available',
      status: mediaOutputCount > 0 ? 'PASS' : 'WARN',
      detail:
        mediaOutputCount > 0
          ? `${mediaOutputCount} playable media outputs are available.`
          : 'No playable media output artifact is available yet.'
    },
    {
      key: 'evidence-downloads',
      label: 'Evidence downloads ready',
      status: hasEvidenceLinks ? 'PASS' : 'FAIL',
      detail: hasEvidenceLinks
        ? 'Result bundle, diagnostics, backend evidence, and evidence bundle links are available.'
        : 'One or more evidence download links are unavailable.'
    },
    {
      key: 'quality-signal',
      label: 'Quality signal available',
      status: subtitleReview?.qualityScore !== null && subtitleReview?.qualityScore !== undefined ? 'PASS' : 'WARN',
      detail:
        subtitleReview?.qualityScore !== null && subtitleReview?.qualityScore !== undefined
          ? `Quality score ${subtitleReview.qualityScore} / 100 · ${subtitleReview.qualityVerdict ?? 'No verdict'}.`
          : 'No quality score is available for this job.'
    },
    {
      key: 'cost-model-evidence',
      label: 'Cost and model-call evidence available',
      status: evidence.usage.modelCallCount > 0 ? 'PASS' : 'WARN',
      detail: `${evidence.usage.modelCallCount} model calls, ${evidence.usage.failedModelCallCount} failed, ${formatCost(evidence.usage.estimatedCostUsd)} estimated.`
    },
    {
      key: 'cache-evidence',
      label: 'Cache evidence available',
      status:
        evidence.cache.artifactCacheHitCount > 0 || evidence.cache.providerCacheHitCount > 0 ? 'PASS' : 'WARN',
      detail: `${evidence.cache.artifactCacheHitCount} artifact cache hits and ${evidence.cache.providerCacheHitCount} provider cache hits.`
    }
  ];

  if (subtitleDraft) {
    items.push({
      key: 'subtitle-draft',
      label: 'Subtitle draft state recorded',
      status: subtitleDraft.editedSegmentCount > 0 ? 'PASS' : 'WARN',
      detail: `${subtitleDraft.editedSegmentCount} edited draft segments out of ${subtitleDraft.segmentCount}.`
    });
  }
  if (job.failureTriage) {
    items.push({
      key: 'failure-triage',
      label: 'Failure triage available',
      status: job.status === 'FAILED' ? 'PASS' : 'WARN',
      detail: `${job.failureTriage.category}: ${job.failureTriage.summary}`
    });
  }

  const requiredItems = items.filter((item) =>
    ['job-completed', 'reviewed-subtitles', 'evidence-downloads'].includes(item.key)
  );
  const overallStatus = requiredItems.every((item) => item.status === 'PASS') ? 'READY' : 'ATTENTION';
  return {
    overallStatus,
    summary:
      overallStatus === 'READY'
        ? 'Required demo handoff signals are ready.'
        : 'One or more required demo handoff signals need attention.',
    items,
    links: evidence.links
  };
}

function countArtifacts(artifacts: JobArtifact[], types: JobArtifact['type'][]): number {
  const accepted = new Set<JobArtifact['type']>(types);
  return artifacts.filter((artifact) => accepted.has(artifact.type)).length;
}

function buildDemoSessionReport(
  job: LocalizationJob,
  artifacts: JobArtifact[],
  manifest: DeliveryManifest | null,
  evidence: DemoEvidence,
  checklist: DemoHandoffChecklist
): DemoSessionReport {
  const reviewedSubtitleCount = countArtifacts(artifacts, [
    'REVIEWED_SUBTITLE_JSON',
    'REVIEWED_SUBTITLE_SRT',
    'REVIEWED_SUBTITLE_VTT'
  ]);
  const mediaOutputCount = countArtifacts(artifacts, [
    'DUBBING_AUDIO',
    'BURNED_VIDEO',
    'REVIEWED_BURNED_VIDEO'
  ]);
  const terminalState = job.pipelineProgress?.terminal ?? ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status);
  const sections: DemoSessionReport['sections'] = [
    {
      title: 'Input and job',
      lines: [
        `Job ${job.jobId}`,
        `Video ${job.videoId}`,
        `Target language ${job.targetLanguage}`,
        `Status ${job.status}`,
        `Retries ${job.retryCount}`,
        `Terminal ${terminalState ? 'yes' : 'no'}`
      ]
    },
    {
      title: 'Generated outputs',
      lines: [
        `${artifacts.length} artifacts recorded`,
        `${reviewedSubtitleCount} reviewed subtitle artifacts`,
        `${mediaOutputCount} playable media outputs`,
        `Result bundle ${evidence.links.resultBundle}`
      ]
    },
    {
      title: 'Handoff evidence',
      lines: [
        `Checklist ${checklist.overallStatus}`,
        `Delivery manifest ${manifest?.handoffReady ? 'ready' : 'needs attention'}`,
        `Evidence bundle ${evidence.links.evidenceBundle}`,
        `Diagnostics ${evidence.links.diagnostics}`
      ]
    },
    {
      title: 'Cost and cache',
      lines: [
        `${evidence.usage.modelCallCount} model calls`,
        `${evidence.usage.failedModelCallCount} failed model calls`,
        `${formatCost(evidence.usage.estimatedCostUsd)} estimated cost`,
        `${evidence.cache.artifactCacheHitCount} artifact cache hits`,
        `${evidence.cache.providerCacheHitCount} provider cache hits`
      ]
    }
  ];

  if (job.failureTriage) {
    sections.push({
      title: 'Failure triage',
      lines: [
        `${job.failureTriage.category}: ${job.failureTriage.summary}`,
        `Retryable ${job.failureTriage.retryable ? 'yes' : 'no'}`,
        job.failureTriage.recommendedAction
      ]
    });
  }

  return {
    generatedAt: new Date().toISOString(),
    status: checklist.overallStatus,
    title:
      checklist.overallStatus === 'READY'
        ? 'This demo run has the required handoff evidence.'
        : 'This demo run still needs attention before handoff.',
    sections,
    links: evidence.links
  };
}

function resultStatusClassName(status: DeliverableStatus): string {
  if (status === 'Ready') {
    return 'status-pill success';
  }
  if (status === 'Preview only') {
    return 'status-pill warning';
  }
  return 'status-pill';
}

function checklistStatusClassName(status: ChecklistStatus): string {
  if (status === 'PASS') {
    return 'checklist-status-pass';
  }
  if (status === 'WARN') {
    return 'checklist-status-warn';
  }
  return 'checklist-status-fail';
}

function buildDemoEvidence(
  job: LocalizationJob,
  artifacts: JobArtifact[],
  transcriptSegmentCount: number,
  subtitleSegmentCount: number,
  selectedLanguage: string,
  subtitleReview: SubtitleReviewSummary | null,
  subtitleDraft: SubtitleDraftSummary | null
): DemoEvidence {
  const reviewedSubtitleArtifactCount = artifacts.filter(
    (artifact) =>
      artifact.type === 'REVIEWED_SUBTITLE_JSON' ||
      artifact.type === 'REVIEWED_SUBTITLE_SRT' ||
      artifact.type === 'REVIEWED_SUBTITLE_VTT'
  ).length;
  const reviewedBurnedVideoAvailable = artifacts.some((artifact) => artifact.type === 'REVIEWED_BURNED_VIDEO');

  return {
    generatedAt: new Date().toISOString(),
    job: {
      jobId: job.jobId,
      videoId: job.videoId,
      targetLanguage: job.targetLanguage,
      status: job.status,
      retryCount: job.retryCount,
      failureStage: job.failureStage,
      failureReason: job.failureReason,
      failureTriage: job.failureTriage,
      pipelineProgress: job.pipelineProgress
    },
    previews: {
      transcriptSegmentCount,
      subtitleSegmentCount,
      subtitleLanguage: selectedLanguage
    },
    subtitleReview: subtitleReview
      ? {
          segmentCount: subtitleReview.segmentCount,
          missingTargetCount: subtitleReview.missingTargetCount,
          timingMismatchCount: subtitleReview.timingMismatchCount,
          qualityScore: subtitleReview.qualityScore,
          qualityVerdict: subtitleReview.qualityVerdict,
          downloadableSubtitleArtifactCount: subtitleReview.downloadableSubtitleArtifactCount
      }
      : null,
    subtitleDraft: subtitleDraft
      ? {
          segmentCount: subtitleDraft.segmentCount,
          editedSegmentCount: subtitleDraft.editedSegmentCount,
          lastUpdatedAt: subtitleDraft.lastUpdatedAt
        }
      : null,
    reviewedDelivery: {
      subtitleArtifactCount: reviewedSubtitleArtifactCount,
      burnedVideoAvailable: reviewedBurnedVideoAvailable
    },
    usage: {
      modelCallCount: job.usageSummary?.modelCallCount ?? job.modelCalls.length,
      failedModelCallCount: job.usageSummary?.failedModelCallCount ?? 0,
      estimatedCostUsd: job.usageSummary?.estimatedCostUsd ?? 0,
      totalLatencyMs: job.usageSummary?.totalLatencyMs ?? 0
    },
    cache: {
      artifactCacheHitCount: job.cacheSummary.cacheHitCount,
      generatedArtifactCount: job.cacheSummary.generatedArtifactCount,
      providerCacheHitCount: job.cacheSummary.providerCacheHitCount
    },
    qualityEvaluation: job.qualityEvaluation
      ? {
          score: job.qualityEvaluation.score,
          verdict: job.qualityEvaluation.verdict,
          status: job.qualityEvaluation.status
        }
      : null,
    timeline: job.timelineEvents.map((event) => ({
      stage: event.stage,
      status: event.status
    })),
    artifacts: artifacts.map((artifact) => ({
      type: artifact.type,
      filename: artifact.filename,
      sizeBytes: artifact.sizeBytes,
      sha256: formatArtifactHash(artifact.contentSha256),
      cacheState: artifact.cacheHit ? 'Reused' : 'Generated'
    })),
    links: {
      resultBundle: linguaFrameApi.artifactArchiveDownloadUrl(job.jobId),
      diagnostics: linguaFrameApi.jobDiagnosticsDownloadUrl(job.jobId),
      evidenceMarkdown: linguaFrameApi.jobEvidenceMarkdownDownloadUrl(job.jobId),
      evidenceBundle: linguaFrameApi.jobEvidenceBundleDownloadUrl(job.jobId)
    }
  };
}

function formatDemoEvidenceMarkdown(evidence: DemoEvidence): string {
  const lines = [
    '# LinguaFrame Demo Evidence',
    '',
    `- Job: ${evidence.job.jobId}`,
    `- Video: ${evidence.job.videoId}`,
    `- Target language: ${evidence.job.targetLanguage}`,
    `- Status: ${evidence.job.status}`,
    `- Retries: ${evidence.job.retryCount}`,
    `- Model calls: ${evidence.usage.modelCallCount}`,
    `- Failed model calls: ${evidence.usage.failedModelCallCount}`,
    `- Estimated cost: ${formatCost(evidence.usage.estimatedCostUsd)}`,
    `- Cache hits: ${evidence.cache.artifactCacheHitCount} artifacts / ${evidence.cache.providerCacheHitCount} provider`,
    `- Transcript preview segments: ${evidence.previews.transcriptSegmentCount}`,
    `- Subtitle preview segments: ${evidence.previews.subtitleSegmentCount}`,
    `- Artifacts: ${evidence.artifacts.length}`,
    `- Result bundle: ${evidence.links.resultBundle}`,
    `- Diagnostics: ${evidence.links.diagnostics}`,
    `- Backend evidence: ${evidence.links.evidenceMarkdown}`,
    `- Backend evidence bundle: ${evidence.links.evidenceBundle}`
  ];

  if (evidence.job.failureStage || evidence.job.failureReason) {
    lines.push(`- Failure: ${evidence.job.failureStage ?? 'Unknown'} / ${evidence.job.failureReason ?? 'No reason'}`);
  }
  if (evidence.job.failureTriage) {
    lines.push(
      `- Failure triage: ${evidence.job.failureTriage.category}, retryable=${evidence.job.failureTriage.retryable}, ${evidence.job.failureTriage.summary}`
    );
    lines.push(`- Failure action: ${evidence.job.failureTriage.recommendedAction}`);
    if (evidence.job.failureTriage.runbookCommand) {
      lines.push(`- Failure runbook: ${evidence.job.failureTriage.runbookCommand}`);
    }
  }
  if (evidence.job.pipelineProgress) {
    lines.push(
      `- Pipeline current stage: ${evidence.job.pipelineProgress.currentStage ?? 'Queued'}`,
      `- Pipeline completed: ${evidence.job.pipelineProgress.completedStageCount} / ${evidence.job.pipelineProgress.totalStageCount}`,
      `- Pipeline measured time: ${formatDurationMs(evidence.job.pipelineProgress.totalMeasuredDurationMs)}`,
      `- Pipeline slowest stage: ${
        evidence.job.pipelineProgress.slowestStage
          ? `${evidence.job.pipelineProgress.slowestStage} (${formatDurationMs(
              evidence.job.pipelineProgress.slowestStageDurationMs ?? 0
            )})`
          : 'Not measured'
      }`
    );
  }
  if (evidence.qualityEvaluation) {
    lines.push(
      `- Quality: ${evidence.qualityEvaluation.score} / 100, ${evidence.qualityEvaluation.verdict}, ${evidence.qualityEvaluation.status}`
    );
  }
  if (evidence.subtitleReview) {
    lines.push(
      `- Subtitle review segments: ${evidence.subtitleReview.segmentCount}`,
      `- Subtitle review missing targets: ${evidence.subtitleReview.missingTargetCount}`,
      `- Subtitle review timing mismatches: ${evidence.subtitleReview.timingMismatchCount}`,
      `- Subtitle review quality: ${
        evidence.subtitleReview.qualityScore === null
          ? 'Not evaluated'
          : `${evidence.subtitleReview.qualityScore} / 100, ${evidence.subtitleReview.qualityVerdict ?? 'No verdict'}`
      }`,
      `- Subtitle review downloadable subtitle artifacts: ${evidence.subtitleReview.downloadableSubtitleArtifactCount}`
    );
  }
  if (evidence.subtitleDraft) {
    lines.push(
      `- Subtitle draft segments: ${evidence.subtitleDraft.segmentCount}`,
      `- Subtitle draft edited segments: ${evidence.subtitleDraft.editedSegmentCount}`,
      `- Subtitle draft last updated: ${evidence.subtitleDraft.lastUpdatedAt ?? 'Not saved'}`
    );
  }
  lines.push(
    `- Reviewed subtitle artifacts: ${evidence.reviewedDelivery.subtitleArtifactCount}`,
    `- Reviewed burned video: ${evidence.reviewedDelivery.burnedVideoAvailable ? 'Available' : 'Not available'}`
  );
  if (evidence.timeline.length > 0) {
    lines.push('', 'Timeline:');
    evidence.timeline.forEach((event) => {
      lines.push(`- ${event.stage}: ${event.status}`);
    });
  }
  if (evidence.artifacts.length > 0) {
    lines.push('', 'Artifacts:');
    evidence.artifacts.forEach((artifact) => {
      lines.push(
        `- ${artifact.type}: ${artifact.filename}, ${formatBytes(artifact.sizeBytes)}, ${artifact.sha256}, ${artifact.cacheState}`
      );
    });
  }
  return lines.join('\n');
}

function formatDemoHandoffChecklistMarkdown(checklist: DemoHandoffChecklist, jobId: string): string {
  const lines = [
    '# LinguaFrame Demo Handoff Checklist',
    '',
    `- Job: ${jobId}`,
    `- Overall: ${checklist.overallStatus}`,
    `- Summary: ${checklist.summary}`,
    '',
    'Checklist:'
  ];
  checklist.items.forEach((item) => {
    lines.push(`- ${item.status}: ${item.label} - ${item.detail}`);
  });
  lines.push(
    '',
    'Links:',
    `- Result bundle: ${checklist.links.resultBundle}`,
    `- Diagnostics: ${checklist.links.diagnostics}`,
    `- Backend evidence: ${checklist.links.evidenceMarkdown}`,
    `- Evidence bundle: ${checklist.links.evidenceBundle}`
  );
  return lines.join('\n');
}

function formatDemoSessionReportMarkdown(report: DemoSessionReport, jobId: string): string {
  const lines = [
    '# LinguaFrame Demo Session Report',
    '',
    `- Job: ${jobId}`,
    `- Overall: ${report.status}`,
    `- Generated at: ${report.generatedAt}`,
    ''
  ];
  report.sections.forEach((section) => {
    lines.push(`## ${section.title}`);
    section.lines.forEach((line) => {
      lines.push(`- ${line}`);
    });
    lines.push('');
  });
  lines.push(
    '## Links',
    `- Result bundle: ${report.links.resultBundle}`,
    `- Diagnostics: ${report.links.diagnostics}`,
    `- Backend evidence: ${report.links.evidenceMarkdown}`,
    `- Evidence bundle: ${report.links.evidenceBundle}`
  );
  return lines.join('\n').trimEnd();
}

function buildCacheReplayCandidates(
  history: LocalizationJobSummary[],
  recentJobs: RecentJob[],
  baselineJobId: string | null
): CacheReplayCandidate[] {
  const candidates = new Map<string, CacheReplayCandidate>();
  history.forEach((job) => {
    if (job.jobId !== baselineJobId) {
      candidates.set(job.jobId, {
        jobId: job.jobId,
        filename: job.filename,
        status: job.status,
        targetLanguage: job.targetLanguage,
        ttsVoice: job.ttsVoice
      });
    }
  });
  recentJobs.forEach((job) => {
    if (job.jobId !== baselineJobId && !candidates.has(job.jobId)) {
      candidates.set(job.jobId, {
        jobId: job.jobId,
        filename: job.filename,
        status: 'COMPLETED',
        targetLanguage: job.targetLanguage,
        ttsVoice: job.ttsVoice
      });
    }
  });
  return Array.from(candidates.values());
}

function buildCacheReplayEvidence(
  baselineJob: LocalizationJob,
  baselineArtifacts: JobArtifact[],
  comparisonJob: LocalizationJob,
  comparisonArtifacts: JobArtifact[]
): CacheReplayEvidence {
  return {
    generatedAt: new Date().toISOString(),
    baseline: cacheReplayEvidenceJob(baselineJob, baselineArtifacts),
    comparison: cacheReplayEvidenceJob(comparisonJob, comparisonArtifacts),
    delta: {
      modelCalls: modelCallCount(comparisonJob) - modelCallCount(baselineJob),
      estimatedCostUsd: jobEstimatedCost(comparisonJob) - jobEstimatedCost(baselineJob)
    },
    providerCacheHitStages: comparisonJob.timelineEvents
      .filter((event) => event.status === 'CACHE_HIT')
      .map((event) => event.stage),
    links: {
      baselineResultBundle: linguaFrameApi.artifactArchiveDownloadUrl(baselineJob.jobId),
      comparisonResultBundle: linguaFrameApi.artifactArchiveDownloadUrl(comparisonJob.jobId),
      baselineDiagnostics: linguaFrameApi.jobDiagnosticsDownloadUrl(baselineJob.jobId),
      comparisonDiagnostics: linguaFrameApi.jobDiagnosticsDownloadUrl(comparisonJob.jobId)
    }
  };
}

function cacheReplayEvidenceJob(
  job: LocalizationJob,
  artifacts: JobArtifact[]
): CacheReplayEvidenceJob {
  const artifactCacheHitCount = artifacts.filter((artifact) => artifact.cacheHit).length;
  return {
    jobId: job.jobId,
    status: job.status,
    targetLanguage: job.targetLanguage,
    ttsVoice: formatVoice(job.ttsVoice),
    modelCallCount: modelCallCount(job),
    estimatedCostUsd: jobEstimatedCost(job),
    artifactCacheHitCount,
    generatedArtifactCount: artifacts.length - artifactCacheHitCount,
    providerCacheHitCount: job.cacheSummary.providerCacheHitCount
  };
}

function formatCacheReplayEvidenceMarkdown(evidence: CacheReplayEvidence): string {
  const lines = [
    '# LinguaFrame Cache Replay Evidence',
    '',
    `- Baseline job: ${evidence.baseline.jobId}`,
    `- Comparison job: ${evidence.comparison.jobId}`,
    `- Baseline status: ${evidence.baseline.status}`,
    `- Comparison status: ${evidence.comparison.status}`,
    `- Baseline model calls: ${evidence.baseline.modelCallCount}`,
    `- Comparison model calls: ${evidence.comparison.modelCallCount}`,
    `- Model call delta: ${formatSignedInteger(evidence.delta.modelCalls)}`,
    `- Baseline estimated cost: ${formatCost(evidence.baseline.estimatedCostUsd)}`,
    `- Comparison estimated cost: ${formatCost(evidence.comparison.estimatedCostUsd)}`,
    `- Estimated cost delta: ${formatSignedCost(evidence.delta.estimatedCostUsd)}`,
    `- Comparison artifact cache: ${evidence.comparison.artifactCacheHitCount} reused / ${evidence.comparison.generatedArtifactCount} generated`,
    `- Comparison provider cache hits: ${evidence.comparison.providerCacheHitCount}`,
    `- Baseline result bundle: ${evidence.links.baselineResultBundle}`,
    `- Comparison result bundle: ${evidence.links.comparisonResultBundle}`,
    `- Baseline diagnostics: ${evidence.links.baselineDiagnostics}`,
    `- Comparison diagnostics: ${evidence.links.comparisonDiagnostics}`
  ];

  if (evidence.providerCacheHitStages.length > 0) {
    lines.push('', 'Cache-hit stages:');
    evidence.providerCacheHitStages.forEach((stage) => {
      lines.push(`- ${stage}`);
    });
  }
  return lines.join('\n');
}

function modelCallCount(job: LocalizationJob): number {
  return job.usageSummary?.modelCallCount ?? job.modelCalls.length;
}

function jobEstimatedCost(job: LocalizationJob): number {
  return job.usageSummary?.estimatedCostUsd ?? 0;
}

function sanitizeFilename(value: string): string {
  return value.replace(/[^a-zA-Z0-9._-]+/g, '-').replace(/^-+|-+$/g, '') || 'job';
}

function formatCost(value: number): string {
  return `$${value.toFixed(8)}`;
}

function formatSignedCost(value: number): string {
  const sign = value > 0 ? '+' : value < 0 ? '-' : '';
  return `${sign}${formatCost(Math.abs(value))}`;
}

function formatSignedInteger(value: number): string {
  if (value > 0) {
    return `+${value}`;
  }
  return String(value);
}

function formatDurationMs(durationMs: number): string {
  if (durationMs < 1000) {
    return `${durationMs} ms`;
  }
  return `${(durationMs / 1000).toFixed(1)} s`;
}

function formatReviewStatus(status: SubtitleReviewSummary['segments'][number]['status']): string {
  if (status === 'MISSING_TARGET') {
    return 'Missing target';
  }
  if (status === 'TIMING_MISMATCH') {
    return 'Timing mismatch';
  }
  return 'Aligned';
}

function formatVoice(value: string | null | undefined): string {
  return value?.trim() || 'Default voice';
}

function formatEnabled(value: boolean): string {
  return value ? 'Enabled' : 'Disabled';
}

function formatConfigured(value: boolean): string {
  return value ? 'Configured' : 'Missing';
}

function formatRuntimeCheckName(name: RuntimeLiveCheckName): string {
  const labels: Record<RuntimeLiveCheckName, string> = {
    database: 'Database',
    redis: 'Redis',
    rabbitmq: 'RabbitMQ',
    minio: 'MinIO',
    ffmpeg: 'FFmpeg',
    openai: 'OpenAI'
  };
  return labels[name];
}

function runtimeProbeStatusClassName(status: string): string {
  if (status === 'UP') {
    return 'status-pill success';
  }
  if (status === 'DOWN') {
    return 'status-pill danger';
  }
  return 'status-pill warning';
}

function formatProviderReadiness(name: string, provider: ProviderReadiness): string {
  const model = provider.model?.trim() || 'default';
  const credentials = provider.credentialsConfigured ? 'credentials set' : 'credentials missing';
  const status = provider.enabled ? provider.provider : `disabled / ${provider.provider}`;
  if (!provider.enabled && model === 'default') {
    return `${name}: ${status} / ${credentials}`;
  }
  return `${name}: ${status} / ${model} / ${credentials}`;
}

function formatDemoSessionStatus(status: DemoSessionStatus | null): string {
  if (!status) {
    return 'Checking owner session';
  }
  if (!status.accessGateEnabled) {
    return 'Open demo';
  }
  return status.authenticated ? 'Owner session active' : 'Owner session required';
}

function formatRetentionOutcome(result: RetentionCleanupResult): string {
  if (result.dryRun) {
    return `${result.candidateJobCount} terminal jobs would be considered.`;
  }
  return `${result.deletedJobCount} jobs, ${result.deletedVideoCount} videos, and ${result.deletedObjectCount} objects deleted.`;
}

function formatFeatureName(name: string): string {
  const labels: Record<string, string> = {
    jobStatusCache: 'Job cache',
    uploadRateLimit: 'Upload rate limit',
    retentionCleanup: 'Retention cleanup',
    costTracking: 'Cost tracking',
    budgetGuard: 'Budget guard'
  };
  return labels[name] ?? name;
}

function formatIsoDateTime(value: string): string {
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    return value;
  }
  return new Date(timestamp).toLocaleString();
}

function formatTimeRange(startMs: number, endMs: number): string {
  return `${formatTimestamp(startMs)} - ${formatTimestamp(endMs)}`;
}

function formatTimestamp(valueMs: number): string {
  const minutes = Math.floor(valueMs / 60000);
  const seconds = Math.floor((valueMs % 60000) / 1000);
  const milliseconds = valueMs % 1000;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(
    milliseconds
  ).padStart(3, '0')}`;
}

function formatBytes(sizeBytes: number): string {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(2)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatArtifactHash(contentSha256: string): string {
  if (!contentSha256) {
    return '-';
  }
  return contentSha256.slice(0, 12);
}
