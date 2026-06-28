import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { linguaFrameApi, readAuthToken, readDemoToken, writeAuthToken, writeDemoToken } from './api/linguaframeApi';
import type {
  AuthSessionStatus,
  DeliveryManifest,
  DemoPresenterPack,
  DemoRunMatrix,
  DemoRunMonitor,
  DemoRunSnapshot,
  DemoShareSheet,
  FailureTriage,
  DemoUploadReadiness,
  JobArtifact,
  JobComparison,
  DemoRunProfile,
  DemoSessionStatus,
  LocalizationJob,
  LocalizationJobStatus,
  LocalizationJobSummary,
  MediaUpload,
  MediaUploadDetail,
  MediaUploadValidation,
  OperatorDashboard,
  OwnerQuotaPreflight,
  PrivateDemoEvidenceGallery,
  PrivateDemoLaunchRehearsal,
  PrivateDemoOperations,
  PrivateDemoRunArchive,
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
const TRANSLATION_STYLE_OPTIONS = [
  { value: 'NATURAL', label: 'Natural' },
  { value: 'FORMAL', label: 'Formal' },
  { value: 'CONCISE', label: 'Concise' }
];
const SUBTITLE_STYLE_PRESET_OPTIONS = [
  { value: 'STANDARD', label: 'Standard subtitles' },
  { value: 'LARGE', label: 'Large subtitles' },
  { value: 'HIGH_CONTRAST', label: 'High contrast' }
];
const SUBTITLE_POLISHING_MODE_OPTIONS = [
  { value: 'OFF', label: 'No polishing' },
  { value: 'BALANCED', label: 'Balanced polishing' },
  { value: 'STRICT', label: 'Strict cleanup' }
];
const MANUAL_DEMO_PROFILE_VALUE = '';

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
    translationStyle: string;
    subtitleStylePreset: string;
    translationGlossaryEntryCount: number;
    translationGlossaryHash: string;
    subtitlePolishingMode: string;
    demoProfileId: string | null;
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

interface DemoReviewStep {
  key: string;
  title: string;
  status: 'READY' | 'ATTENTION' | 'BLOCKED';
  detail: string;
  anchor: string;
  actionLabel: string;
}

interface CacheReplayCandidate {
  jobId: string;
  filename: string;
  status: LocalizationJobStatus;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
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
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string;
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
  { key: 'dubbed-video', label: 'Dubbed video', artifactType: 'DUBBED_VIDEO', preview: null },
  { key: 'reviewed-burned-video', label: 'Reviewed burned video', artifactType: 'REVIEWED_BURNED_VIDEO', preview: null },
  { key: 'worker-summary', label: 'Worker summary', artifactType: 'WORKER_SUMMARY', preview: null }
];

const MEDIA_ARTIFACT_TYPES: JobArtifact['type'][] = [
  'DUBBING_AUDIO',
  'BURNED_VIDEO',
  'DUBBED_VIDEO',
  'REVIEWED_BURNED_VIDEO'
];

export function App({ pollIntervalMs = POLL_INTERVAL_MS }: { pollIntervalMs?: number }) {
  const [demoProfileId, setDemoProfileId] = useState(MANUAL_DEMO_PROFILE_VALUE);
  const [demoRunProfiles, setDemoRunProfiles] = useState<DemoRunProfile[]>([]);
  const [demoRunProfileError, setDemoRunProfileError] = useState<string | null>(null);
  const [targetLanguage, setTargetLanguage] = useState('zh-CN');
  const [ttsVoice, setTtsVoice] = useState('');
  const [translationStyle, setTranslationStyle] = useState('NATURAL');
  const [subtitleStylePreset, setSubtitleStylePreset] = useState('STANDARD');
  const [subtitlePolishingMode, setSubtitlePolishingMode] = useState('OFF');
  const [translationGlossary, setTranslationGlossary] = useState('');
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
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authSessionStatus, setAuthSessionStatus] = useState<AuthSessionStatus | null>(null);
  const [authStatusMessage, setAuthStatusMessage] = useState<string | null>(null);
  const [isChangingAuthSession, setIsChangingAuthSession] = useState(false);
  const [job, setJob] = useState<LocalizationJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isValidatingUpload, setIsValidatingUpload] = useState(false);
  const [uploadValidation, setUploadValidation] = useState<MediaUploadValidation | null>(null);
  const [uploadValidationError, setUploadValidationError] = useState<string | null>(null);
  const [ownerQuotaPreflight, setOwnerQuotaPreflight] = useState<OwnerQuotaPreflight | null>(null);
  const [ownerQuotaPreflightError, setOwnerQuotaPreflightError] = useState<string | null>(null);
  const [isLoadingOwnerQuotaPreflight, setIsLoadingOwnerQuotaPreflight] = useState(false);
  const [demoUploadReadiness, setDemoUploadReadiness] = useState<DemoUploadReadiness | null>(null);
  const [demoUploadReadinessError, setDemoUploadReadinessError] = useState<string | null>(null);
  const [isLoadingDemoUploadReadiness, setIsLoadingDemoUploadReadiness] = useState(false);
  const [isLoadingJob, setIsLoadingJob] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [isSseUnavailable, setIsSseUnavailable] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<JobArtifact[]>([]);
  const [deliveryManifest, setDeliveryManifest] = useState<DeliveryManifest | null>(null);
  const [deliveryManifestError, setDeliveryManifestError] = useState<string | null>(null);
  const [sourceMedia, setSourceMedia] = useState<MediaUploadDetail | null>(null);
  const [sourceMediaError, setSourceMediaError] = useState<string | null>(null);
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
  const [privateDemoOperations, setPrivateDemoOperations] = useState<PrivateDemoOperations | null>(
    null
  );
  const [privateDemoOperationsError, setPrivateDemoOperationsError] = useState<string | null>(null);
  const [isLoadingPrivateDemoOperations, setIsLoadingPrivateDemoOperations] = useState(false);
  const [privateDemoLaunchRehearsal, setPrivateDemoLaunchRehearsal] =
    useState<PrivateDemoLaunchRehearsal | null>(null);
  const [privateDemoLaunchRehearsalError, setPrivateDemoLaunchRehearsalError] = useState<string | null>(
    null
  );
  const [isLoadingPrivateDemoLaunchRehearsal, setIsLoadingPrivateDemoLaunchRehearsal] = useState(false);
  const [privateDemoEvidenceGallery, setPrivateDemoEvidenceGallery] =
    useState<PrivateDemoEvidenceGallery | null>(null);
  const [privateDemoEvidenceGalleryError, setPrivateDemoEvidenceGalleryError] = useState<string | null>(
    null
  );
  const [isLoadingPrivateDemoEvidenceGallery, setIsLoadingPrivateDemoEvidenceGallery] = useState(false);
  const [privateDemoRunArchive, setPrivateDemoRunArchive] = useState<PrivateDemoRunArchive | null>(null);
  const [privateDemoRunArchiveError, setPrivateDemoRunArchiveError] = useState<string | null>(null);
  const [isLoadingPrivateDemoRunArchive, setIsLoadingPrivateDemoRunArchive] = useState(false);
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
  const [demoComparisonJobId, setDemoComparisonJobId] = useState('');
  const [demoComparison, setDemoComparison] = useState<JobComparison | null>(null);
  const [demoComparisonError, setDemoComparisonError] = useState<string | null>(null);
  const [isLoadingDemoComparison, setIsLoadingDemoComparison] = useState(false);
  const [demoRunMatrix, setDemoRunMatrix] = useState<DemoRunMatrix | null>(null);
  const [demoRunMatrixError, setDemoRunMatrixError] = useState<string | null>(null);
  const [isLoadingDemoRunMatrix, setIsLoadingDemoRunMatrix] = useState(false);
  const [demoPresenterPack, setDemoPresenterPack] = useState<DemoPresenterPack | null>(null);
  const [demoPresenterPackError, setDemoPresenterPackError] = useState<string | null>(null);
  const [isLoadingDemoPresenterPack, setIsLoadingDemoPresenterPack] = useState(false);
  const [demoRunMonitor, setDemoRunMonitor] = useState<DemoRunMonitor | null>(null);
  const [demoRunMonitorError, setDemoRunMonitorError] = useState<string | null>(null);
  const [isLoadingDemoRunMonitor, setIsLoadingDemoRunMonitor] = useState(false);
  const [demoRunSnapshot, setDemoRunSnapshot] = useState<DemoRunSnapshot | null>(null);
  const [demoRunSnapshotError, setDemoRunSnapshotError] = useState<string | null>(null);
  const [isLoadingDemoRunSnapshot, setIsLoadingDemoRunSnapshot] = useState(false);
  const [demoShareSheet, setDemoShareSheet] = useState<DemoShareSheet | null>(null);
  const [demoShareSheetError, setDemoShareSheetError] = useState<string | null>(null);
  const [isLoadingDemoShareSheet, setIsLoadingDemoShareSheet] = useState(false);

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
    const storedAuthToken = readAuthToken(window.localStorage);
    if (storedAuthToken) {
      writeAuthToken(window.localStorage, storedAuthToken);
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

  const loadAuthSession = useCallback(async () => {
    try {
      const status = await linguaFrameApi.getAuthSession();
      setAuthSessionStatus(status);
      setAuthUsername((current) => current || status.username);
      setAuthStatusMessage(null);
    } catch (authError) {
      setAuthSessionStatus(null);
      setAuthStatusMessage(toErrorMessage(authError));
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
        if (!options.silent) {
          setDemoComparisonJobId('');
          setDemoComparison(null);
          setDemoComparisonError(null);
          setDemoRunMatrix(null);
          setDemoRunMatrixError(null);
          setDemoPresenterPack(null);
          setDemoPresenterPackError(null);
          setDemoRunMonitor(null);
          setDemoRunMonitorError(null);
          setDemoShareSheet(null);
          setDemoShareSheetError(null);
        }
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

  const loadSourceMedia = useCallback(async (videoId: string) => {
    try {
      const upload = await linguaFrameApi.getMediaUpload(videoId);
      setSourceMedia(upload);
      setSourceMediaError(null);
    } catch (sourceMediaLoadError) {
      setSourceMedia(null);
      setSourceMediaError(toErrorMessage(sourceMediaLoadError));
    }
  }, []);

  const loadDemoRunMatrix = useCallback(async (jobId: string) => {
    setIsLoadingDemoRunMatrix(true);
    try {
      const matrix = await linguaFrameApi.getDemoRunMatrix(jobId, 8);
      setDemoRunMatrix(matrix);
      setDemoRunMatrixError(null);
    } catch (matrixLoadError) {
      setDemoRunMatrix(null);
      setDemoRunMatrixError(toErrorMessage(matrixLoadError));
    } finally {
      setIsLoadingDemoRunMatrix(false);
    }
  }, []);

  const loadDemoPresenterPack = useCallback(async (jobId: string) => {
    setIsLoadingDemoPresenterPack(true);
    try {
      const pack = await linguaFrameApi.getDemoPresenterPack(jobId);
      setDemoPresenterPack(pack);
      setDemoPresenterPackError(null);
    } catch (packLoadError) {
      setDemoPresenterPack(null);
      setDemoPresenterPackError(toErrorMessage(packLoadError));
    } finally {
      setIsLoadingDemoPresenterPack(false);
    }
  }, []);

  const loadDemoRunMonitor = useCallback(async (jobId: string) => {
    setIsLoadingDemoRunMonitor(true);
    try {
      const monitor = await linguaFrameApi.getDemoRunMonitor(jobId);
      setDemoRunMonitor(monitor);
      setDemoRunMonitorError(null);
    } catch (monitorLoadError) {
      setDemoRunMonitor(null);
      setDemoRunMonitorError(toErrorMessage(monitorLoadError));
    } finally {
      setIsLoadingDemoRunMonitor(false);
    }
  }, []);

  const loadDemoRunSnapshot = useCallback(async (jobId: string) => {
    setIsLoadingDemoRunSnapshot(true);
    try {
      const snapshot = await linguaFrameApi.getDemoRunSnapshot(jobId);
      setDemoRunSnapshot(snapshot);
      setDemoRunSnapshotError(null);
    } catch (snapshotLoadError) {
      setDemoRunSnapshot(null);
      setDemoRunSnapshotError(toErrorMessage(snapshotLoadError));
    } finally {
      setIsLoadingDemoRunSnapshot(false);
    }
  }, []);

  const loadDemoShareSheet = useCallback(async (jobId: string) => {
    setIsLoadingDemoShareSheet(true);
    try {
      const sheet = await linguaFrameApi.getDemoShareSheet(jobId);
      setDemoShareSheet(sheet);
      setDemoShareSheetError(null);
    } catch (sheetLoadError) {
      setDemoShareSheet(null);
      setDemoShareSheetError(toErrorMessage(sheetLoadError));
    } finally {
      setIsLoadingDemoShareSheet(false);
    }
  }, []);

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

  const loadPrivateDemoOperations = useCallback(async () => {
    setIsLoadingPrivateDemoOperations(true);
    try {
      const operations = await linguaFrameApi.getPrivateDemoOperations();
      setPrivateDemoOperations(operations);
      setPrivateDemoOperationsError(null);
    } catch (operationsLoadError) {
      setPrivateDemoOperations(null);
      setPrivateDemoOperationsError(toErrorMessage(operationsLoadError));
    } finally {
      setIsLoadingPrivateDemoOperations(false);
    }
  }, []);

  const loadPrivateDemoLaunchRehearsal = useCallback(async () => {
    setIsLoadingPrivateDemoLaunchRehearsal(true);
    try {
      const rehearsal = await linguaFrameApi.getPrivateDemoLaunchRehearsal();
      setPrivateDemoLaunchRehearsal(rehearsal);
      setPrivateDemoLaunchRehearsalError(null);
    } catch (rehearsalLoadError) {
      setPrivateDemoLaunchRehearsal(null);
      setPrivateDemoLaunchRehearsalError(toErrorMessage(rehearsalLoadError));
    } finally {
      setIsLoadingPrivateDemoLaunchRehearsal(false);
    }
  }, []);

  const loadPrivateDemoEvidenceGallery = useCallback(async () => {
    setIsLoadingPrivateDemoEvidenceGallery(true);
    try {
      const gallery = await linguaFrameApi.getPrivateDemoEvidenceGallery(20);
      setPrivateDemoEvidenceGallery(gallery);
      setPrivateDemoEvidenceGalleryError(null);
    } catch (galleryLoadError) {
      setPrivateDemoEvidenceGallery(null);
      setPrivateDemoEvidenceGalleryError(toErrorMessage(galleryLoadError));
    } finally {
      setIsLoadingPrivateDemoEvidenceGallery(false);
    }
  }, []);

  const loadPrivateDemoRunArchive = useCallback(async () => {
    setIsLoadingPrivateDemoRunArchive(true);
    try {
      const archive = await linguaFrameApi.getPrivateDemoRunArchive();
      setPrivateDemoRunArchive(archive);
      setPrivateDemoRunArchiveError(null);
    } catch (archiveLoadError) {
      setPrivateDemoRunArchive(null);
      setPrivateDemoRunArchiveError(toErrorMessage(archiveLoadError));
    } finally {
      setIsLoadingPrivateDemoRunArchive(false);
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

  const loadOwnerQuotaPreflight = useCallback(async () => {
    setIsLoadingOwnerQuotaPreflight(true);
    try {
      const preflight = await linguaFrameApi.getOwnerQuotaPreflight();
      setOwnerQuotaPreflight(preflight);
      setOwnerQuotaPreflightError(null);
      return preflight;
    } catch (preflightError) {
      setOwnerQuotaPreflight(null);
      setOwnerQuotaPreflightError(toErrorMessage(preflightError));
      return null;
    } finally {
      setIsLoadingOwnerQuotaPreflight(false);
    }
  }, []);

  const loadDemoUploadReadiness = useCallback(async (profileId: string = demoProfileId) => {
    setIsLoadingDemoUploadReadiness(true);
    try {
      const readiness = await linguaFrameApi.getDemoUploadReadiness(profileId);
      setDemoUploadReadiness(readiness);
      setDemoUploadReadinessError(null);
      return readiness;
    } catch (readinessError) {
      setDemoUploadReadiness(null);
      setDemoUploadReadinessError(toErrorMessage(readinessError));
      return null;
    } finally {
      setIsLoadingDemoUploadReadiness(false);
    }
  }, [demoProfileId]);

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
    void loadAuthSession();
  }, [loadAuthSession]);

  useEffect(() => {
    void loadHistory(historyStatusFilter);
  }, [historyStatusFilter, loadHistory]);

  useEffect(() => {
    void loadOperatorDashboard();
  }, [loadOperatorDashboard]);

  useEffect(() => {
    void loadPrivateDemoOperations();
  }, [loadPrivateDemoOperations]);

  useEffect(() => {
    void loadPrivateDemoLaunchRehearsal();
  }, [loadPrivateDemoLaunchRehearsal]);

  useEffect(() => {
    void loadPrivateDemoEvidenceGallery();
  }, [loadPrivateDemoEvidenceGallery]);

  useEffect(() => {
    void loadPrivateDemoRunArchive();
  }, [loadPrivateDemoRunArchive]);

  useEffect(() => {
    void loadRuntimeDependencies();
  }, [loadRuntimeDependencies]);

  useEffect(() => {
    void loadOwnerQuotaPreflight();
  }, [loadOwnerQuotaPreflight]);

  useEffect(() => {
    void loadDemoUploadReadiness(demoProfileId);
  }, [demoProfileId, loadDemoUploadReadiness]);

  useEffect(() => {
    void loadRetentionCleanupPreview();
  }, [loadRetentionCleanupPreview]);

  useEffect(() => {
    let ignore = false;

    linguaFrameApi
      .listDemoRunProfiles()
      .then((profiles) => {
        if (ignore) {
          return;
        }
        setDemoRunProfiles(profiles);
        setDemoRunProfileError(null);
      })
      .catch((profileLoadError) => {
        if (ignore) {
          return;
        }
        setDemoRunProfiles([]);
        setDemoRunProfileError(toErrorMessage(profileLoadError));
      });

    return () => {
      ignore = true;
    };
  }, []);

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
      void loadJob(job.jobId, { silent: true }).then((nextJob) => loadSourceMedia(nextJob.videoId));
    }, pollIntervalMs);

    return () => window.clearTimeout(timer);
  }, [isSseUnavailable, job, loadJob, loadSourceMedia, pollIntervalMs]);

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
        void loadSourceMedia(nextJob.videoId);
        void loadDemoRunMonitor(nextJob.jobId);
        if (TERMINAL_STATUSES.has(nextJob.status)) {
          void loadPreviewData(nextJob.jobId, nextJob.targetLanguage);
          void loadDemoRunMatrix(nextJob.jobId);
          void loadDemoPresenterPack(nextJob.jobId);
          void loadDemoRunSnapshot(nextJob.jobId);
          void loadDemoShareSheet(nextJob.jobId);
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
  }, [historyStatusFilter, isSseUnavailable, job, loadDemoPresenterPack, loadDemoRunMatrix, loadDemoRunMonitor, loadDemoRunSnapshot, loadDemoShareSheet, loadHistory, loadPreviewData, loadSourceMedia]);

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
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
    }
  }

  function applyDemoProfile(profileId: string) {
    setDemoProfileId(profileId);
    const profile = demoRunProfiles.find((candidate) => candidate.id === profileId);
    if (!profile) {
      return;
    }
    setTargetLanguage(profile.targetLanguage);
    setTtsVoice(profile.ttsVoice);
    setTranslationStyle(profile.translationStyle);
    setSubtitleStylePreset(profile.subtitleStylePreset);
    setSubtitlePolishingMode(profile.subtitlePolishingMode);
    setTranslationGlossary(profile.translationGlossary);
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
    if (ownerQuotaPreflight?.allowed === false) {
      setError(ownerQuotaPreflight.blockingReasons[0] ?? 'Owner quota preflight blocked upload.');
      return;
    }
    if (demoUploadReadiness?.overallStatus === 'BLOCKED') {
      setError(demoUploadReadiness.requiredActions[0] ?? 'Upload readiness blocked upload.');
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
      const upload = await linguaFrameApi.uploadMedia(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      const recentJob = toRecentJob(upload);
      setRecentJobs(saveRecentJob(window.localStorage, recentJob));
      setSelectedRecentJob(recentJob);
      const nextJob = await loadJob(upload.jobId);
      await loadSourceMedia(nextJob.videoId);
      await loadPreviewData(upload.jobId, recentJob.targetLanguage);
      await loadDemoRunMatrix(upload.jobId);
      await loadDemoRunMonitor(upload.jobId);
      await loadDemoPresenterPack(upload.jobId);
      await loadDemoRunSnapshot(upload.jobId);
      await loadDemoShareSheet(upload.jobId);
      await loadHistory(historyStatusFilter);
    } catch (uploadError) {
      setError(toErrorMessage(uploadError));
    } finally {
      setIsUploading(false);
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
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
    await loadSourceMedia(nextJob.videoId);
    await loadPreviewData(jobId, nextJob.targetLanguage ?? targetLanguage);
    await loadDemoRunMatrix(jobId);
    await loadDemoRunMonitor(jobId);
    await loadDemoPresenterPack(jobId);
    await loadDemoRunSnapshot(jobId);
    await loadDemoShareSheet(jobId);
  }

  async function handleRetry() {
    if (!job) {
      return;
    }

    setIsRetrying(true);
    try {
      const retriedJob = await linguaFrameApi.retryJob(job.jobId);
      setJob(retriedJob);
      await loadSourceMedia(retriedJob.videoId);
      await loadDemoRunMatrix(retriedJob.jobId);
      await loadDemoRunMonitor(retriedJob.jobId);
      await loadDemoPresenterPack(retriedJob.jobId);
      await loadDemoRunSnapshot(retriedJob.jobId);
      await loadDemoShareSheet(retriedJob.jobId);
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
      await loadSourceMedia(cancelledJob.videoId);
      await loadDemoRunMatrix(cancelledJob.jobId);
      await loadDemoRunMonitor(cancelledJob.jobId);
      await loadDemoPresenterPack(cancelledJob.jobId);
      await loadDemoRunSnapshot(cancelledJob.jobId);
      await loadDemoShareSheet(cancelledJob.jobId);
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
    setDemoProfileId(recentJob.demoProfileId ?? MANUAL_DEMO_PROFILE_VALUE);
    setTranslationStyle(recentJob.translationStyle);
    setSubtitleStylePreset(recentJob.subtitleStylePreset);
    setSubtitlePolishingMode(recentJob.subtitlePolishingMode);
    setTranslationGlossary('');
    const nextJob = await loadJob(recentJob.jobId);
    await loadSourceMedia(nextJob.videoId);
    await loadPreviewData(recentJob.jobId, recentJob.targetLanguage);
    await loadDemoRunMatrix(recentJob.jobId);
    await loadDemoRunMonitor(recentJob.jobId);
    await loadDemoPresenterPack(recentJob.jobId);
    await loadDemoRunSnapshot(recentJob.jobId);
    await loadDemoShareSheet(recentJob.jobId);
  }

  async function openHistoryJob(historyJob: LocalizationJobSummary) {
    setSelectedRecentJob(null);
    setManualJobId(historyJob.jobId);
    setTargetLanguage(historyJob.targetLanguage);
    setTtsVoice(historyJob.ttsVoice ?? '');
    setDemoProfileId(historyJob.demoProfileId ?? MANUAL_DEMO_PROFILE_VALUE);
    setTranslationStyle(historyJob.translationStyle);
    setSubtitleStylePreset(historyJob.subtitleStylePreset);
    setSubtitlePolishingMode(historyJob.subtitlePolishingMode);
    setTranslationGlossary('');
    const nextJob = await loadJob(historyJob.jobId);
    await loadSourceMedia(nextJob.videoId);
    await loadPreviewData(historyJob.jobId, nextJob.targetLanguage ?? historyJob.targetLanguage);
    await loadDemoRunMatrix(historyJob.jobId);
    await loadDemoRunMonitor(historyJob.jobId);
    await loadDemoPresenterPack(historyJob.jobId);
    await loadDemoRunSnapshot(historyJob.jobId);
    await loadDemoShareSheet(historyJob.jobId);
  }

  async function openDashboardFailure(failure: OperatorDashboard['recentFailures'][number]) {
    setSelectedRecentJob(null);
    setManualJobId(failure.jobId);
    const nextJob = await loadJob(failure.jobId);
    const language = nextJob.targetLanguage ?? targetLanguage;
    setTargetLanguage(language);
    setTtsVoice(nextJob.ttsVoice ?? '');
    setDemoProfileId(nextJob.demoProfileId ?? MANUAL_DEMO_PROFILE_VALUE);
    setTranslationStyle(nextJob.translationStyle ?? 'NATURAL');
    setSubtitleStylePreset(nextJob.subtitleStylePreset ?? 'STANDARD');
    setSubtitlePolishingMode(nextJob.subtitlePolishingMode ?? 'OFF');
    setTranslationGlossary('');
    await loadSourceMedia(nextJob.videoId);
    await loadPreviewData(failure.jobId, language);
    await loadDemoRunMatrix(failure.jobId);
    await loadDemoRunMonitor(failure.jobId);
    await loadDemoPresenterPack(failure.jobId);
    await loadDemoRunSnapshot(failure.jobId);
    await loadDemoShareSheet(failure.jobId);
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

  async function handleSelectDemoComparison(jobId: string) {
    setDemoComparisonJobId(jobId);
    setDemoComparison(null);
    setDemoComparisonError(null);
    if (!job || !jobId) {
      return;
    }

    setIsLoadingDemoComparison(true);
    try {
      const comparison = await linguaFrameApi.getJobComparison(job.jobId, jobId);
      setDemoComparison(comparison);
    } catch (comparisonError) {
      setDemoComparisonError(toErrorMessage(comparisonError));
    } finally {
      setIsLoadingDemoComparison(false);
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

  async function handleLocalAuthLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const username = authUsername.trim();
    const password = authPassword.trim();
    if (!username || !password) {
      setAuthStatusMessage('Enter the account username and password.');
      return;
    }

    setIsChangingAuthSession(true);
    try {
      const response = await linguaFrameApi.loginAuthSession(username, password);
      setAuthSessionStatus(response.session);
      setAuthPassword('');
      setAuthStatusMessage(response.session.authenticated ? 'Local account active' : 'Local account required');
      void loadHistory(historyStatusFilter);
      void loadRuntimeDependencies();
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
      void loadRetentionCleanupPreview();
    } catch (authError) {
      setAuthStatusMessage(toErrorMessage(authError));
    } finally {
      setIsChangingAuthSession(false);
    }
  }

  async function handleLocalAuthLogout() {
    setIsChangingAuthSession(true);
    try {
      const status = await linguaFrameApi.logoutAuthSession();
      setAuthSessionStatus(status);
      setAuthPassword('');
      setAuthStatusMessage('Local account signed out.');
      void loadHistory(historyStatusFilter);
      void loadRuntimeDependencies();
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
      void loadRetentionCleanupPreview();
    } catch (authError) {
      writeAuthToken(window.localStorage, '');
      setAuthPassword('');
      setAuthStatusMessage(toErrorMessage(authError));
    } finally {
      setIsChangingAuthSession(false);
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
          <div className="demo-session-summary" aria-label="Demo owner boundary">
            <span className="runtime-badge">{formatDemoSessionStatus(demoSessionStatus)}</span>
            <span className="runtime-badge">{formatAuthSessionStatus(authSessionStatus)}</span>
            {demoSessionStatus ? (
              <span className="owner-boundary">
                <span>Owner: {demoSessionStatus.ownerId}</span>
                <span>Scope: {demoSessionStatus.ownershipScope}</span>
              </span>
            ) : null}
            {authSessionStatus ? (
              <span className="owner-boundary">
                <span>Account: {authSessionStatus.username}</span>
                <span>Auth owner: {authSessionStatus.ownerId}</span>
                <span>Auth scope: {authSessionStatus.ownershipScope}</span>
              </span>
            ) : null}
          </div>
          <form className="demo-token-form auth-token-form" onSubmit={handleLocalAuthLogin}>
            <label>
              Account username
              <input
                type="text"
                value={authUsername}
                onChange={(event) => {
                  setAuthUsername(event.target.value);
                  setAuthStatusMessage(null);
                }}
                autoComplete="username"
              />
            </label>
            <label>
              Account password
              <input
                type="password"
                value={authPassword}
                onChange={(event) => {
                  setAuthPassword(event.target.value);
                  setAuthStatusMessage(null);
                }}
                autoComplete="current-password"
              />
            </label>
            <div className="demo-token-actions">
              <button type="submit" disabled={isChangingAuthSession}>
                Sign in
              </button>
              <button
                type="button"
                className="secondary-button"
                onClick={handleLocalAuthLogout}
                disabled={isChangingAuthSession}
              >
                Sign out
              </button>
            </div>
            {authStatusMessage ? <p className="token-status">{authStatusMessage}</p> : null}
          </form>
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
              Demo profile
              <select value={demoProfileId} onChange={(event) => applyDemoProfile(event.target.value)}>
                <option value={MANUAL_DEMO_PROFILE_VALUE}>Manual settings</option>
                {demoRunProfiles.map((profile) => (
                  <option key={profile.id} value={profile.id}>
                    {profile.label}
                  </option>
                ))}
              </select>
            </label>
            {demoRunProfileError ? <p className="error-text">{demoRunProfileError}</p> : null}
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
            <label>
              Translation style
              <select value={translationStyle} onChange={(event) => setTranslationStyle(event.target.value)}>
                {TRANSLATION_STYLE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Translation glossary
              <textarea
                value={translationGlossary}
                onChange={(event) => setTranslationGlossary(event.target.value)}
                placeholder={'Maya => 玛雅\nTears of Steel = 钢铁之泪'}
                rows={4}
              />
            </label>
            <label>
              Subtitle polishing
              <select
                value={subtitlePolishingMode}
                onChange={(event) => setSubtitlePolishingMode(event.target.value)}
              >
                {SUBTITLE_POLISHING_MODE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Subtitle style
              <select
                value={subtitleStylePreset}
                onChange={(event) => setSubtitleStylePreset(event.target.value)}
              >
                {SUBTITLE_STYLE_PRESET_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
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
              <button
                type="submit"
                disabled={
                  isUploading ||
                  isValidatingUpload ||
                  ownerQuotaPreflight?.allowed === false ||
                  demoUploadReadiness?.overallStatus === 'BLOCKED'
                }
              >
                {isUploading ? 'Uploading...' : 'Upload'}
              </button>
            </div>
            <DemoUploadReadinessPanel
              readiness={demoUploadReadiness}
              error={demoUploadReadinessError}
              isLoading={isLoadingDemoUploadReadiness}
              onRefresh={() => void loadDemoUploadReadiness()}
            />
            <OwnerQuotaPreflightPanel
              preflight={ownerQuotaPreflight}
              error={ownerQuotaPreflightError}
              isLoading={isLoadingOwnerQuotaPreflight}
              onRefresh={() => void loadOwnerQuotaPreflight()}
            />
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
                        {formatDemoProfileId(historyJob.demoProfileId)} ·
                        {formatTranslationStyle(historyJob.translationStyle)} ·
                        {formatSubtitleStylePreset(historyJob.subtitleStylePreset)} ·
                        {formatSubtitlePolishingMode(historyJob.subtitlePolishingMode)} ·
                        {formatGlossaryMetadata(historyJob.translationGlossaryEntryCount, historyJob.translationGlossaryHash)} ·
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

          <PrivateDemoOperationsPanel
            operations={privateDemoOperations}
            error={privateDemoOperationsError}
            isLoading={isLoadingPrivateDemoOperations}
            onRefresh={loadPrivateDemoOperations}
          />

          <PrivateDemoLaunchRehearsalPanel
            rehearsal={privateDemoLaunchRehearsal}
            error={privateDemoLaunchRehearsalError}
            isLoading={isLoadingPrivateDemoLaunchRehearsal}
            onRefresh={loadPrivateDemoLaunchRehearsal}
          />

          <PrivateDemoEvidenceGalleryPanel
            gallery={privateDemoEvidenceGallery}
            error={privateDemoEvidenceGalleryError}
            isLoading={isLoadingPrivateDemoEvidenceGallery}
            onRefresh={loadPrivateDemoEvidenceGallery}
          />

          <PrivateDemoRunArchivePanel
            archive={privateDemoRunArchive}
            error={privateDemoRunArchiveError}
            isLoading={isLoadingPrivateDemoRunArchive}
            onRefresh={loadPrivateDemoRunArchive}
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
                        {recentJob.targetLanguage} · {formatVoice(recentJob.ttsVoice)} ·
                        {formatDemoProfileId(recentJob.demoProfileId)} ·
                        {formatTranslationStyle(recentJob.translationStyle)} ·
                        {formatSubtitleStylePreset(recentJob.subtitleStylePreset)} ·
                        {formatSubtitlePolishingMode(recentJob.subtitlePolishingMode)} ·
                        {formatGlossaryMetadata(recentJob.translationGlossaryEntryCount, recentJob.translationGlossaryHash)}
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
              demoComparison={demoComparison}
              demoComparisonError={demoComparisonError}
              demoComparisonJobId={demoComparisonJobId}
              demoRunMatrix={demoRunMatrix}
              demoRunMatrixError={demoRunMatrixError}
              demoRunMonitor={demoRunMonitor}
              demoRunMonitorError={demoRunMonitorError}
              demoRunSnapshot={demoRunSnapshot}
              demoRunSnapshotError={demoRunSnapshotError}
              demoPresenterPack={demoPresenterPack}
              demoPresenterPackError={demoPresenterPackError}
              demoShareSheet={demoShareSheet}
              demoShareSheetError={demoShareSheetError}
              isLoadingCacheReplayComparison={isLoadingCacheReplayComparison}
              isLoadingDemoComparison={isLoadingDemoComparison}
              isLoadingDemoRunMatrix={isLoadingDemoRunMatrix}
              isLoadingDemoRunMonitor={isLoadingDemoRunMonitor}
              isLoadingDemoRunSnapshot={isLoadingDemoRunSnapshot}
              isLoadingDemoPresenterPack={isLoadingDemoPresenterPack}
              isLoadingDemoShareSheet={isLoadingDemoShareSheet}
              onCancel={handleCancel}
              onClearSubtitleDraft={handleClearSubtitleDraft}
              onPinCacheReplayBaseline={handlePinCacheReplayBaseline}
              onRefreshDemoRunMatrix={() => void loadDemoRunMatrix(job.jobId)}
              onRefreshDemoRunMonitor={() => void loadDemoRunMonitor(job.jobId)}
              onRefreshDemoRunSnapshot={() => void loadDemoRunSnapshot(job.jobId)}
              onRefreshDemoPresenterPack={() => void loadDemoPresenterPack(job.jobId)}
              onRefreshDemoShareSheet={() => void loadDemoShareSheet(job.jobId)}
              onSelectCacheReplayComparison={handleSelectCacheReplayComparison}
              onSelectDemoComparison={handleSelectDemoComparison}
              onRetry={handleRetry}
              onPublishReviewedSubtitles={handlePublishReviewedSubtitles}
              onSaveSubtitleDraft={handleSaveSubtitleDraft}
              previewErrors={previewErrors}
              selectedLanguage={selectedLanguage}
              sourceMedia={sourceMedia}
              sourceMediaError={sourceMediaError}
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

function SourceMediaPanel({
  job,
  sourceMedia,
  sourceMediaError
}: {
  job: LocalizationJob;
  sourceMedia: MediaUploadDetail | null;
  sourceMediaError: string | null;
}) {
  return (
    <section id="source-media" className="panel" aria-label="Source media">
      <div className="panel-heading">
        <h3>Source media</h3>
        <div className="panel-actions">
          <a className="secondary-link" href={linguaFrameApi.sourceMediaDownloadUrl(job.videoId)}>
            Download source video
          </a>
        </div>
      </div>
      {sourceMediaError ? <p className="error-text">{sourceMediaError}</p> : null}
      {sourceMedia ? (
        <dl className="status-grid compact">
          <div>
            <dt>Filename</dt>
            <dd>{sourceMedia.filename}</dd>
          </div>
          <div>
            <dt>Content type</dt>
            <dd>{sourceMedia.contentType}</dd>
          </div>
          <div>
            <dt>Size</dt>
            <dd>{formatBytes(sourceMedia.fileSizeBytes)}</dd>
          </div>
          <div>
            <dt>Duration</dt>
            <dd>{formatDurationSeconds(sourceMedia.durationSeconds)}</dd>
          </div>
          <div>
            <dt>Upload status</dt>
            <dd>{sourceMedia.status}</dd>
          </div>
          <div>
            <dt>Uploaded</dt>
            <dd>{formatIsoDateTime(sourceMedia.createdAt)}</dd>
          </div>
          <div>
            <dt>Video</dt>
            <dd>{sourceMedia.videoId}</dd>
          </div>
          <div>
            <dt>Job</dt>
            <dd>{job.jobId}</dd>
          </div>
          <div>
            <dt>Target language</dt>
            <dd>{job.targetLanguage}</dd>
          </div>
          <div>
            <dt>Demo profile</dt>
            <dd>{formatDemoProfileId(job.demoProfileId)}</dd>
          </div>
          <div>
            <dt>Translation style</dt>
            <dd>{formatTranslationStyle(job.translationStyle)}</dd>
          </div>
          <div>
            <dt>Subtitle style</dt>
            <dd>{formatSubtitleStylePreset(job.subtitleStylePreset)}</dd>
          </div>
          <div>
            <dt>Subtitle polishing</dt>
            <dd>{formatSubtitlePolishingMode(job.subtitlePolishingMode)}</dd>
          </div>
          <div>
            <dt>Translation glossary</dt>
            <dd>{formatGlossaryMetadata(job.translationGlossaryEntryCount, job.translationGlossaryHash)}</dd>
          </div>
        </dl>
      ) : sourceMediaError ? null : (
        <p className="muted">Loading source media metadata...</p>
      )}
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
            <div>
              <dt>Owner quota</dt>
              <dd>
                {formatEnabled(readiness.ownerQuota.enabled)} / active{' '}
                {formatLimitValue(readiness.ownerQuota.maxActiveJobs)} / queued{' '}
                {formatLimitValue(readiness.ownerQuota.maxQueuedJobs)}
              </dd>
            </div>
            <div>
              <dt>Owner daily budget</dt>
              <dd>
                {formatEnabled(readiness.ownerQuota.dailyBudgetGuardEnabled)} /{' '}
                {formatCost(readiness.ownerQuota.maxDailyCostUsd)}
              </dd>
            </div>
          </dl>
          <ul className="readiness-list" aria-label="Provider readiness">
            {providerEntries.map(([name, provider]) => (
              <li key={name}>{formatProviderReadiness(name, provider)}</li>
            ))}
          </ul>
          <section className="readiness-subsection" aria-label="Worker topology">
            <h3>Worker topology</h3>
            <dl className="status-grid readiness-grid">
              <div>
                <dt>Listener queue</dt>
                <dd>{readiness.worker.listenerQueue}</dd>
              </div>
              <div>
                <dt>Job exchange</dt>
                <dd>{readiness.worker.jobExchange}</dd>
              </div>
              <div>
                <dt>Default route</dt>
                <dd>
                  {readiness.worker.defaultJobQueue} / {readiness.worker.defaultRoutingKey}
                </dd>
              </div>
              <div>
                <dt>FFmpeg route</dt>
                <dd>
                  {readiness.worker.ffmpegJobQueue} / {readiness.worker.ffmpegRoutingKey}
                </dd>
              </div>
              <div>
                <dt>OpenAI route</dt>
                <dd>
                  {readiness.worker.openaiJobQueue} / {readiness.worker.openaiRoutingKey}
                </dd>
              </div>
            </dl>
            <ul className="readiness-list" aria-label="Worker owned stages">
              {readiness.worker.ownedStageGroups.map((stageGroup) => (
                <li key={stageGroup}>{stageGroup}</li>
              ))}
            </ul>
            <ul className="readiness-list" aria-label="Worker startup commands">
              {readiness.worker.recommendedCommands.map((command) => (
                <li key={command}>
                  <code>{command}</code>
                </li>
              ))}
            </ul>
          </section>
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

function OwnerQuotaPreflightPanel({
  preflight,
  error,
  isLoading,
  onRefresh
}: {
  preflight: OwnerQuotaPreflight | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  return (
    <section className="upload-validation-panel" aria-label="Owner quota">
      <div className="panel-heading">
        <h3>Owner quota</h3>
        {preflight ? (
          <span className={preflight.allowed ? 'status-pill' : 'status-pill danger'}>
            {preflight.allowed ? 'Allowed' : 'Blocked'}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !preflight ? <p className="muted">Loading owner quota...</p> : null}
      {preflight ? (
        <>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Owner</dt>
              <dd>{preflight.ownerId}</dd>
            </div>
            <div>
              <dt>Mode</dt>
              <dd>{formatEnabled(preflight.enabled)}</dd>
            </div>
            <div>
              <dt>Active jobs</dt>
              <dd>{formatQuotaLimit(preflight, 'activeJobs', preflight.activeJobs)}</dd>
            </div>
            <div>
              <dt>Queued jobs</dt>
              <dd>{formatQuotaLimit(preflight, 'queuedJobs', preflight.queuedJobs)}</dd>
            </div>
            <div>
              <dt>Daily spend</dt>
              <dd>{formatCostQuotaLimit(preflight, 'dailyCostUsd', preflight.dailyEstimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Budget date</dt>
              <dd>{preflight.dailyBudgetDate}</dd>
            </div>
          </dl>
          {preflight.blockingReasons.length > 0 ? (
            <ul className="error-list" aria-label="Owner quota blocking reasons">
              {preflight.blockingReasons.map((reason) => (
                <li key={reason}>{reason}</li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function DemoUploadReadinessPanel({
  readiness,
  error,
  isLoading,
  onRefresh
}: {
  readiness: DemoUploadReadiness | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  return (
    <section className="upload-validation-panel" aria-label="Upload readiness">
      <div className="panel-heading">
        <h3>Upload readiness</h3>
        {readiness ? (
          <span className={readinessStatusClassName(readiness.overallStatus)}>
            {readiness.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !readiness ? <p className="muted">Loading upload readiness...</p> : null}
      {readiness ? (
        <>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Owner</dt>
              <dd>{readiness.ownerId}</dd>
            </div>
            <div>
              <dt>Demo profile</dt>
              <dd>{formatDemoProfileId(readiness.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(readiness.generatedAt)}</dd>
            </div>
          </dl>
          <ul className="readiness-list upload-readiness-list" aria-label="Upload readiness checks">
            {readiness.checks.map((check) => (
              <li key={check.id}>
                <span>{check.label}</span>
                <span className={readinessStatusClassName(check.status)}>{check.status}</span>
                <span>{check.detail}</span>
              </li>
            ))}
          </ul>
          {readiness.requiredActions.length > 0 ? (
            <ul className="readiness-list" aria-label="Upload readiness actions">
              {readiness.requiredActions.map((action) => (
                <li key={action}>{action}</li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}
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

function PrivateDemoOperationsPanel({
  operations,
  error,
  isLoading,
  onRefresh
}: {
  operations: PrivateDemoOperations | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const report = operations ? formatPrivateDemoOperationsReport(operations) : '';

  const handleCopy = async () => {
    if (!operations || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(report);
    setStatus('Operations report copied.');
  };

  const handleDownload = () => {
    if (!operations) {
      return;
    }
    const blob = new Blob([report], { type: 'text/markdown;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'linguaframe-private-demo-operations.md';
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus('Operations report Markdown downloaded.');
  };

  return (
    <section className="panel private-demo-operations-panel" aria-label="Private demo operations">
      <div className="panel-heading">
        <h2>Private demo operations</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Operations readiness unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !operations ? <p className="muted">Loading operations readiness...</p> : null}
      {operations ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Overall</dt>
              <dd>
                <span className={operationsStatusClassName(operations.overallStatus)}>
                  {operations.overallStatus}
                </span>
              </dd>
            </div>
            <div>
              <dt>Ready</dt>
              <dd>{operations.readyCount} ready</dd>
            </div>
            <div>
              <dt>Attention</dt>
              <dd>{operations.attentionCount} attention</dd>
            </div>
            <div>
              <dt>Blocked</dt>
              <dd>{operations.blockedCount} blocked</dd>
            </div>
          </dl>
          <ul className="operations-section-list" aria-label="Private demo operation sections">
            {operations.sections.map((section) => (
              <li key={section.title}>
                <div className="operations-section-heading">
                  <strong>{section.title}</strong>
                  <span className={operationsStatusClassName(section.status)}>{section.status}</span>
                </div>
                <ul className="readiness-list operations-check-list">
                  {section.checks.map((check) => (
                    <li key={`${section.title}-${check.label}`}>
                      <span>
                        <strong>{check.label}</strong> · {check.detail}
                      </span>
                      <small>{check.nextAction}</small>
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
          <h3>Commands</h3>
          <ul className="command-list">
            {operations.commands.map((command) => (
              <li key={command.command}>
                <strong>{command.label}</strong>
                <code>{command.command}</code>
                <small>{command.detail}</small>
              </li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy operations report
            </button>
            <button type="button" className="secondary-button" onClick={handleDownload}>
              Download operations report
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function PrivateDemoLaunchRehearsalPanel({
  rehearsal,
  error,
  isLoading,
  onRefresh
}: {
  rehearsal: PrivateDemoLaunchRehearsal | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = rehearsal ? formatPrivateDemoLaunchRehearsalNotes(rehearsal) : '';

  const handleCopy = async () => {
    if (!rehearsal || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Launch rehearsal notes copied.');
  };

  const handleDownload = () => {
    if (!rehearsal) {
      return;
    }
    const blob = new Blob([notes], { type: 'text/markdown;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'linguaframe-private-demo-launch-rehearsal.md';
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus('Launch rehearsal notes downloaded.');
  };

  return (
    <section className="panel private-demo-launch-rehearsal-panel" aria-label="Private demo launch rehearsal">
      <div className="panel-heading">
        <h2>Private demo launch rehearsal</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Launch rehearsal unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !rehearsal ? <p className="muted">Loading launch rehearsal...</p> : null}
      {rehearsal ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Overall</dt>
              <dd>
                <span className={operationsStatusClassName(rehearsal.overallStatus)}>
                  {rehearsal.overallStatus}
                </span>
              </dd>
            </div>
            <div>
              <dt>Next</dt>
              <dd>{rehearsal.recommendedNextStepId}</dd>
            </div>
            <div>
              <dt>Ready</dt>
              <dd>{rehearsal.readyCount} ready</dd>
            </div>
            <div>
              <dt>Attention</dt>
              <dd>{rehearsal.attentionCount} attention</dd>
            </div>
            <div>
              <dt>Blocked</dt>
              <dd>{rehearsal.blockedCount} blocked</dd>
            </div>
          </dl>
          <ol className="operations-section-list launch-rehearsal-step-list" aria-label="Launch rehearsal steps">
            {rehearsal.steps.map((step) => (
              <li key={step.id}>
                <div className="operations-section-heading">
                  <strong>{step.title}</strong>
                  <span className={operationsStatusClassName(step.status)}>{step.status}</span>
                </div>
                <p>{step.detail}</p>
                <code>{step.command}</code>
                <small>Evidence: {step.evidencePath}</small>
                <small>Next: {step.nextAction}</small>
                {step.blocking ? <small className="error-text">Blocking launch until resolved.</small> : null}
              </li>
            ))}
          </ol>
          <h3>Evidence</h3>
          <ul className="link-list">
            {rehearsal.evidenceDownloads.map((download) => (
              <li key={download}>
                <code>{download}</code>
              </li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy launch rehearsal notes
            </button>
            <button type="button" className="secondary-button" onClick={handleDownload}>
              Download launch rehearsal notes
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function PrivateDemoEvidenceGalleryPanel({
  gallery,
  error,
  isLoading,
  onRefresh
}: {
  gallery: PrivateDemoEvidenceGallery | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = gallery ? formatPrivateDemoEvidenceGalleryNotes(gallery) : '';
  const recommendedJob = gallery?.jobs.find((job) => job.recommended) ?? gallery?.jobs[0] ?? null;

  const handleCopy = async () => {
    if (!gallery || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Evidence gallery notes copied.');
  };

  const handleDownload = () => {
    if (!gallery) {
      return;
    }
    const blob = new Blob([notes], { type: 'text/markdown;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'linguaframe-private-demo-evidence-gallery.md';
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus('Evidence gallery notes downloaded.');
  };

  return (
    <section className="panel private-demo-evidence-gallery-panel" aria-label="Private demo evidence gallery">
      <div className="panel-heading">
        <h2>Private demo evidence gallery</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Evidence gallery unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !gallery ? <p className="muted">Loading evidence gallery...</p> : null}
      {gallery ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Overall</dt>
              <dd>
                <span className={evidenceGalleryStatusClassName(gallery.overallStatus)}>
                  {gallery.overallStatus}
                </span>
              </dd>
            </div>
            <div>
              <dt>Recommended</dt>
              <dd>{gallery.recommendedJobId ?? 'None'}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>{gallery.completedJobCount} completed</dd>
            </div>
            <div>
              <dt>Handoff</dt>
              <dd>{gallery.handoffReadyCount} handoff ready</dd>
            </div>
          </dl>
          {recommendedJob ? (
            <div className="evidence-gallery-recommended">
              <h3>Recommended run</h3>
              <p>
                <strong>{recommendedJob.jobId}</strong> · {recommendedJob.filename} ·{' '}
                {recommendedJob.demoProfileId ?? 'manual'}
              </p>
              <ul className="inline-evidence-list">
                <li>{recommendedJob.qualityScore === null ? 'Quality unavailable' : `Quality ${recommendedJob.qualityScore}`}</li>
                <li>{formatGalleryCost(recommendedJob.estimatedCostUsd)}</li>
                <li>{recommendedJob.modelCallCount} model calls</li>
                <li>{recommendedJob.providerCacheHitCount} provider cache hit</li>
              </ul>
            </div>
          ) : (
            <p className="muted">No completed demo jobs are available yet.</p>
          )}
          {gallery.jobs.length > 0 ? (
            <ul className="operations-section-list evidence-gallery-job-list" aria-label="Completed demo runs">
              {gallery.jobs.map((job) => (
                <li key={job.jobId}>
                  <div className="operations-section-heading">
                    <strong>{job.jobId}</strong>
                    <span className={job.handoffReady ? 'status-pill' : 'status-pill warning'}>
                      {job.handoffReady ? 'HANDOFF READY' : 'ATTENTION'}
                    </span>
                  </div>
                  <p>
                    {job.filename} · {job.demoProfileId ?? 'manual'} · {job.targetLanguage}
                  </p>
                  <small>
                    {job.qualityScore === null ? 'Quality unavailable' : `Quality ${job.qualityScore}`}
                    {' · '}
                    {formatGalleryCost(job.estimatedCostUsd)}
                    {' · '}
                    {job.providerCacheHitCount} provider cache hit
                  </small>
                  {job.attentionReasons.length > 0 ? (
                    <ul className="compact-list">
                      {job.attentionReasons.map((reason) => (
                        <li key={reason}>{reason}</li>
                      ))}
                    </ul>
                  ) : null}
                  <ul className="link-list">
                    {job.downloads.map((download) => (
                      <li key={download.href}>
                        <a href={download.href}>{download.label}</a>
                        <small>{download.description}</small>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          ) : null}
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy evidence gallery notes
            </button>
            <button type="button" className="secondary-button" onClick={handleDownload}>
              Download evidence gallery notes
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function PrivateDemoRunArchivePanel({
  archive,
  error,
  isLoading,
  onRefresh
}: {
  archive: PrivateDemoRunArchive | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = archive ? formatPrivateDemoRunArchiveNotes(archive) : '';
  const recommended = archive?.candidates.find((candidate) => candidate.jobId === archive.recommendedJobId)
    ?? archive?.candidates[0]
    ?? null;

  const handleCopy = async () => {
    if (!archive || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Archive notes copied.');
  };

  const handleDownload = () => {
    if (!archive) {
      return;
    }
    const blob = new Blob([notes], { type: 'text/markdown;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'linguaframe-private-demo-run-archive.md';
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus('Archive notes downloaded.');
  };

  return (
    <section className="panel private-demo-run-archive-panel" aria-label="Private demo run archive">
      <div className="panel-heading">
        <h2>Private demo run archive</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Run archive unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !archive ? <p className="muted">Loading run archive...</p> : null}
      {archive ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Overall</dt>
              <dd>
                <span className={operationsStatusClassName(archive.overallStatus)}>
                  {archive.overallStatus}
                </span>
              </dd>
            </div>
            <div>
              <dt>Recommended</dt>
              <dd>{archive.recommendedJobId ?? 'None'}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{archive.recommendedProfileId ?? 'manual'}</dd>
            </div>
            <div>
              <dt>Operations</dt>
              <dd>{archive.operationsOverallStatus}</dd>
            </div>
            <div>
              <dt>Launch</dt>
              <dd>{archive.launchOverallStatus}</dd>
            </div>
            <div>
              <dt>Next</dt>
              <dd>{archive.launchRecommendedNextStep}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>{archive.galleryCompletedJobCount} completed</dd>
            </div>
            <div>
              <dt>Handoff</dt>
              <dd>{archive.galleryHandoffReadyCount} handoff ready</dd>
            </div>
          </dl>
          {recommended ? (
            <div className="evidence-gallery-recommended">
              <h3>Recommended archive candidate</h3>
              <p>
                <strong>{recommended.jobId}</strong> · {recommended.filename} · {recommended.profileId}
              </p>
              <ul className="inline-evidence-list">
                <li>{recommended.qualityScore === null ? 'Quality unavailable' : `Quality ${recommended.qualityScore}`}</li>
                <li>{formatGalleryCost(recommended.estimatedCostUsd)}</li>
                <li>{recommended.modelCallCount} model calls</li>
                <li>{recommended.providerCacheHitCount} provider cache hit</li>
              </ul>
            </div>
          ) : (
            <p className="muted">No completed recommended job is available yet.</p>
          )}
          {archive.candidates.length > 0 ? (
            <ul className="operations-section-list evidence-gallery-job-list" aria-label="Private demo archive candidates">
              {archive.candidates.map((candidate) => (
                <li key={candidate.jobId}>
                  <div className="operations-section-heading">
                    <strong>{candidate.jobId}</strong>
                    <span className={operationsStatusClassName(candidate.readiness)}>
                      {candidate.readiness}
                    </span>
                  </div>
                  <p>
                    {candidate.filename} · {candidate.profileId} · {candidate.status}
                  </p>
                  <small>
                    {candidate.qualityScore === null ? 'Quality unavailable' : `Quality ${candidate.qualityScore}`}
                    {' · '}
                    {formatGalleryCost(candidate.estimatedCostUsd)}
                    {' · '}
                    {candidate.providerCacheHitCount} provider cache hit
                  </small>
                  {candidate.roles.length > 0 ? <small>{candidate.roles.join(', ')}</small> : null}
                </li>
              ))}
            </ul>
          ) : null}
          <h3>Archive links</h3>
          <ul className="link-list">
            {archive.archiveLinks.map((link) => (
              <li key={link.href}>
                <a href={link.href}>{link.label}</a>
                <small>{link.description}</small>
              </li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy archive notes
            </button>
            <button type="button" className="secondary-button" onClick={handleDownload}>
              Download archive notes
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function formatPrivateDemoOperationsReport(operations: PrivateDemoOperations): string {
  const lines = [
    '# LinguaFrame Private Demo Operations Report',
    '',
    `- Overall: ${operations.overallStatus}`,
    `- Generated at: ${operations.generatedAt}`,
    `- Ready: ${operations.readyCount}`,
    `- Attention: ${operations.attentionCount}`,
    `- Blocked: ${operations.blockedCount}`,
    '',
    '## Checks'
  ];
  operations.sections.forEach((section) => {
    section.checks.forEach((check) => {
      lines.push(`- ${check.status} ${section.title} / ${check.label}: ${check.detail}`);
      lines.push(`  Next: ${check.nextAction}`);
    });
  });
  lines.push('', '## Commands');
  operations.commands.forEach((command) => {
    lines.push(`- ${command.command}`);
  });
  lines.push('', '## Documentation');
  operations.documentationLinks.forEach((link) => {
    lines.push(`- ${link.path}: ${link.detail}`);
  });
  return `${lines.join('\n')}\n`;
}

function formatPrivateDemoLaunchRehearsalNotes(rehearsal: PrivateDemoLaunchRehearsal): string {
  if (rehearsal.rehearsalNotesMarkdown.trim().length > 0) {
    return `${rehearsal.rehearsalNotesMarkdown.trim()}\n`;
  }
  const lines = [
    '# LinguaFrame Private Demo Launch Rehearsal',
    '',
    `- Overall: ${rehearsal.overallStatus}`,
    `- Generated at: ${rehearsal.generatedAt}`,
    `- Recommended next step: ${rehearsal.recommendedNextStepId}`,
    '',
    '## Steps'
  ];
  rehearsal.steps.forEach((step) => {
    lines.push(`- ${step.status} ${step.id}: ${step.title}`);
    lines.push(`  Command: ${step.command}`);
    lines.push(`  Evidence: ${step.evidencePath}`);
    lines.push(`  Next: ${step.nextAction}`);
  });
  lines.push('', '## Evidence Routes');
  rehearsal.evidenceDownloads.forEach((download) => {
    lines.push(`- ${download}`);
  });
  return `${lines.join('\n')}\n`;
}

function formatPrivateDemoEvidenceGalleryNotes(gallery: PrivateDemoEvidenceGallery): string {
  if (gallery.galleryNotesMarkdown.trim().length > 0) {
    return `${gallery.galleryNotesMarkdown.trim()}\n`;
  }
  const lines = [
    '# LinguaFrame Private Demo Evidence Gallery',
    '',
    `- Overall: ${gallery.overallStatus}`,
    `- Generated at: ${gallery.generatedAt}`,
    `- Completed jobs: ${gallery.completedJobCount}`,
    `- Handoff ready: ${gallery.handoffReadyCount}`,
    `- Recommended job: ${gallery.recommendedJobId ?? 'none'}`,
    '',
    '## Runs'
  ];
  gallery.jobs.forEach((job) => {
    lines.push(`- ${job.jobId}: ${job.filename}`);
    lines.push(`  Profile: ${job.demoProfileId ?? 'manual'}`);
    lines.push(`  Handoff ready: ${job.handoffReady}`);
    lines.push(`  Demo run package: /api/jobs/${job.jobId}/demo-run-package/download`);
  });
  return `${lines.join('\n')}\n`;
}

function formatPrivateDemoRunArchiveNotes(archive: PrivateDemoRunArchive): string {
  if (archive.archiveNotesMarkdown.trim().length > 0) {
    return `${archive.archiveNotesMarkdown.trim()}\n`;
  }
  const lines = [
    '# LinguaFrame Private Demo Run Archive',
    '',
    `- Overall: ${archive.overallStatus}`,
    `- Generated at: ${archive.generatedAt}`,
    `- Recommended job: ${archive.recommendedJobId ?? 'none'}`,
    `- Operations readiness: ${archive.operationsOverallStatus}`,
    `- Launch rehearsal: ${archive.launchOverallStatus}`,
    `- Completed jobs: ${archive.galleryCompletedJobCount}`,
    `- Handoff ready: ${archive.galleryHandoffReadyCount}`,
    '',
    '## Links'
  ];
  archive.archiveLinks.forEach((link) => {
    lines.push(`- ${link.label}: ${link.href}`);
  });
  return `${lines.join('\n')}\n`;
}

function operationsStatusClassName(status: string): string {
  if (status === 'BLOCKED') {
    return 'status-pill danger';
  }
  if (status === 'ATTENTION') {
    return 'status-pill warning';
  }
  return 'status-pill';
}

function evidenceGalleryStatusClassName(status: PrivateDemoEvidenceGallery['overallStatus']): string {
  if (status === 'ATTENTION') {
    return 'status-pill warning';
  }
  if (status === 'EMPTY') {
    return 'status-pill muted-pill';
  }
  return 'status-pill';
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
  demoComparison,
  demoComparisonError,
  demoComparisonJobId,
  demoRunMatrix,
  demoRunMatrixError,
  demoRunMonitor,
  demoRunMonitorError,
  demoRunSnapshot,
  demoRunSnapshotError,
  demoPresenterPack,
  demoPresenterPackError,
  demoShareSheet,
  demoShareSheetError,
  isLoadingCacheReplayComparison,
  isLoadingDemoComparison,
  isLoadingDemoRunMatrix,
  isLoadingDemoRunMonitor,
  isLoadingDemoRunSnapshot,
  isLoadingDemoPresenterPack,
  isLoadingDemoShareSheet,
  onCancel,
  onClearSubtitleDraft,
  onPinCacheReplayBaseline,
  onRefreshDemoRunMatrix,
  onRefreshDemoRunMonitor,
  onRefreshDemoRunSnapshot,
  onRefreshDemoPresenterPack,
  onRefreshDemoShareSheet,
  onSelectCacheReplayComparison,
  onSelectDemoComparison,
  onRetry,
  onPublishReviewedSubtitles,
  onSaveSubtitleDraft,
  previewErrors,
  selectedLanguage,
  sourceMedia,
  sourceMediaError,
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
  demoComparison: JobComparison | null;
  demoComparisonError: string | null;
  demoComparisonJobId: string;
  demoRunMatrix: DemoRunMatrix | null;
  demoRunMatrixError: string | null;
  demoRunMonitor: DemoRunMonitor | null;
  demoRunMonitorError: string | null;
  demoRunSnapshot: DemoRunSnapshot | null;
  demoRunSnapshotError: string | null;
  demoPresenterPack: DemoPresenterPack | null;
  demoPresenterPackError: string | null;
  demoShareSheet: DemoShareSheet | null;
  demoShareSheetError: string | null;
  isLoadingCacheReplayComparison: boolean;
  isLoadingDemoComparison: boolean;
  isLoadingDemoRunMatrix: boolean;
  isLoadingDemoRunMonitor: boolean;
  isLoadingDemoRunSnapshot: boolean;
  isLoadingDemoPresenterPack: boolean;
  isLoadingDemoShareSheet: boolean;
  onCancel: () => void;
  onClearSubtitleDraft: () => void;
  onPinCacheReplayBaseline: () => void;
  onRefreshDemoRunMatrix: () => void;
  onRefreshDemoRunMonitor: () => void;
  onRefreshDemoRunSnapshot: () => void;
  onRefreshDemoPresenterPack: () => void;
  onRefreshDemoShareSheet: () => void;
  onSelectCacheReplayComparison: (jobId: string) => void;
  onSelectDemoComparison: (jobId: string) => void;
  onRetry: () => void;
  onPublishReviewedSubtitles: (includeBurnedVideo: boolean) => void;
  onSaveSubtitleDraft: (segments: Array<{ index: number; text: string }>) => void;
  previewErrors: string[];
  selectedLanguage: string;
  sourceMedia: MediaUploadDetail | null;
  sourceMediaError: string | null;
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
  const demoReviewSteps = useMemo(
    () =>
      buildDemoReviewSteps(
        job,
        artifacts,
        deliveryManifest,
        subtitleReview,
        subtitleDraft,
        demoHandoffChecklist,
        demoSessionReport
      ),
    [artifacts, deliveryManifest, demoHandoffChecklist, demoSessionReport, job, subtitleDraft, subtitleReview]
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

      <SourceMediaPanel
        job={job}
        sourceMedia={sourceMedia}
        sourceMediaError={sourceMediaError}
      />

      <DemoReviewGuidePanel job={job} report={demoSessionReport} steps={demoReviewSteps} />

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

      <DemoRunMonitorPanel
        error={demoRunMonitorError}
        isLoading={isLoadingDemoRunMonitor}
        jobId={job.jobId}
        monitor={demoRunMonitor}
        onRefresh={onRefreshDemoRunMonitor}
      />

      <PipelineProgressPanel progress={job.pipelineProgress} />

      <FailureTriagePanel triage={job.failureTriage} />

      <DemoSessionReportPanel report={demoSessionReport} jobId={job.jobId} />

      <DemoShareSheetPanel
        error={demoShareSheetError}
        isLoading={isLoadingDemoShareSheet}
        jobId={job.jobId}
        onRefresh={onRefreshDemoShareSheet}
        sheet={demoShareSheet}
      />

      <DemoRunSnapshotPanel
        error={demoRunSnapshotError}
        isLoading={isLoadingDemoRunSnapshot}
        jobId={job.jobId}
        onRefresh={onRefreshDemoRunSnapshot}
        snapshot={demoRunSnapshot}
      />

      <DemoPresenterPackPanel
        error={demoPresenterPackError}
        isLoading={isLoadingDemoPresenterPack}
        pack={demoPresenterPack}
        onRefresh={onRefreshDemoPresenterPack}
      />

      <DemoEvidencePanel evidence={demoEvidence} markdown={demoEvidenceMarkdown} />

      <DemoRunMatrixPanel
        error={demoRunMatrixError}
        isLoading={isLoadingDemoRunMatrix}
        matrix={demoRunMatrix}
        onRefresh={onRefreshDemoRunMatrix}
      />

      <DemoComparisonPanel
        candidates={cacheReplayCandidates}
        comparison={demoComparison}
        comparisonJobId={demoComparisonJobId}
        error={demoComparisonError}
        isLoading={isLoadingDemoComparison}
        selectedJob={job}
        onSelectComparison={onSelectDemoComparison}
      />

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

      <QualityEvaluationPanel job={job} />

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

      <section id="timeline" className="panel" aria-label="Timeline">
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

      <section id="model-calls" className="panel" aria-label="Model calls">
        <div className="panel-heading">
          <h3>Model calls</h3>
          <div className="panel-actions">
            <a className="secondary-link" href={linguaFrameApi.aiAuditPackageDownloadUrl(job.jobId)}>
              Download AI audit package
            </a>
          </div>
        </div>
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

      <section id="artifacts" className="panel" aria-label="Artifacts">
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
    <section id="media-delivery" className="panel media-delivery-panel" aria-label="Media delivery">
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
    <section id="result-delivery" className="panel result-delivery-panel" aria-label="Result delivery">
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
      <section id="delivery-handoff" className="panel" aria-label="Delivery handoff">
        <h3>Delivery handoff</h3>
        <p className="error-text">{error}</p>
      </section>
    );
  }
  if (!manifest) {
    return (
      <section id="delivery-handoff" className="panel" aria-label="Delivery handoff">
        <h3>Delivery handoff</h3>
        <p className="muted">Delivery manifest is not loaded.</p>
      </section>
    );
  }

  return (
    <section id="delivery-handoff" className="panel delivery-handoff-panel" aria-label="Delivery handoff">
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
          <a className="secondary-link" href={linguaFrameApi.demoRunPackageDownloadUrl(jobId)}>
            Download demo run package
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
        <div>
          <dt>Demo profile</dt>
          <dd>{formatDemoProfileId(manifest.demoProfileId)}</dd>
        </div>
        <div>
          <dt>Subtitle polishing</dt>
          <dd>{formatSubtitlePolishingMode(manifest.subtitlePolishingMode)}</dd>
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
    <section id="pipeline-progress" className="panel" aria-label="Pipeline progress">
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

function DemoRunMonitorPanel({
  error,
  isLoading,
  jobId,
  monitor,
  onRefresh
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  monitor: DemoRunMonitor | null;
  onRefresh: () => void;
}) {
  return (
    <section className="panel demo-run-monitor-panel" aria-label="Demo run monitor">
      <div className="panel-heading">
        <h3>Demo run monitor</h3>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <a className="secondary-link" href={linguaFrameApi.demoRunMonitorMarkdownDownloadUrl(jobId)}>
            Download backend Markdown
          </a>
        </div>
      </div>
      {isLoading && !monitor ? <p className="muted">Loading demo run monitor...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {monitor ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Attention</dt>
              <dd>{monitor.attentionLevel}</dd>
            </div>
            <div>
              <dt>Current stage</dt>
              <dd>{monitor.currentStage ?? 'Queued'}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>
                {monitor.completedStageCount} / {monitor.totalStageCount}
              </dd>
            </div>
            <div>
              <dt>Elapsed</dt>
              <dd>{monitor.elapsedMs === null ? 'N/A' : formatDurationMs(monitor.elapsedMs)}</dd>
            </div>
            <div>
              <dt>Slowest stage</dt>
              <dd>
                {monitor.slowestStage
                  ? `${monitor.slowestStage} · ${formatDurationMs(monitor.slowestStageDurationMs ?? 0)}`
                  : 'Not measured'}
              </dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(monitor.generatedAt)}</dd>
            </div>
          </dl>
          <p>{monitor.summary}</p>
          <p>
            <strong>Next action:</strong> {monitor.recommendedNextAction}
          </p>
          {monitor.stages.length === 0 ? (
            <p className="muted">No stage timing events have been recorded yet.</p>
          ) : (
            <ul className="result-deliverable-list compact-list">
              {monitor.stages.map((stage) => (
                <li key={`${stage.stage}-${stage.status}-${stage.startedAt ?? 'pending'}`}>
                  <div className="result-deliverable-main">
                    <strong>{stage.stage}</strong>
                    <span className="status-pill">{stage.attention}</span>
                  </div>
                  <div className="result-deliverable-meta">
                    <span>{stage.status}</span>
                    <span>
                      {stage.durationMs === null
                        ? `Running ${formatDurationMs(stage.runningForMs ?? 0)}`
                        : formatDurationMs(stage.durationMs)}
                    </span>
                    {stage.message ? <span>{stage.message}</span> : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {monitor.links.length > 0 ? (
            <div className="link-list">
              {monitor.links.map((link) => (
                <a key={link.kind} href={link.url}>
                  {link.label}
                </a>
              ))}
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function FailureTriagePanel({ triage }: { triage: FailureTriage | null }) {
  if (!triage) {
    return null;
  }

  return (
    <section id="failure-triage" className="panel" aria-label="Failure triage">
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

function DemoReviewGuidePanel({
  job,
  report,
  steps
}: {
  job: LocalizationJob;
  report: DemoSessionReport;
  steps: DemoReviewStep[];
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';
  const mainSteps = steps.filter((step) => step.key !== 'failure-triage');
  const ready = mainSteps.every((step) => step.status === 'READY');
  const markdown = formatDemoReviewPresenterNotes(job, steps, report);

  const handleCopyPresenterNotes = useCallback(async () => {
    if (!canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Presenter notes copied.');
  }, [canCopy, markdown]);

  const handleDownloadPresenterNotes = useCallback(() => {
    const blob = new Blob([markdown], {
      type: 'text/markdown'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-demo-review-${sanitizeFilename(job.jobId)}.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Presenter notes Markdown downloaded.');
  }, [job.jobId, markdown]);

  return (
    <section className="panel demo-review-guide-panel" aria-label="Demo review guide">
      <div className="panel-heading">
        <div>
          <h3>Demo review guide</h3>
          <p className="muted">Walk through this job in a presentation-ready order.</p>
        </div>
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            disabled={!canCopy}
            onClick={handleCopyPresenterNotes}
          >
            Copy presenter notes
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadPresenterNotes}>
            Download presenter notes
          </button>
        </div>
      </div>
      <p className={ready ? 'status-pill success' : 'status-pill warning'}>
        {ready ? 'Presentation ready' : 'Needs attention'}
      </p>
      {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      <ol className="demo-review-steps">
        {steps.map((step) => (
          <li className="demo-review-step" key={step.key}>
            <div className="demo-review-step-main">
              <span className={demoReviewStatusClassName(step.status)}>{step.status}</span>
              <div>
                <strong>{step.title}</strong>
                <p>{step.detail}</p>
              </div>
            </div>
            <div className="demo-review-step-actions">
              <a className="secondary-link" href={step.anchor}>
                {step.actionLabel}
              </a>
            </div>
          </li>
        ))}
      </ol>
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
    <section id="demo-handoff-checklist" className="panel handoff-checklist-panel" aria-label="Demo handoff checklist">
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
        <a className="secondary-link" href={linguaFrameApi.demoRunPackageDownloadUrl(jobId)}>
          Download demo run package
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
    <section id="demo-session-report" className="panel demo-session-report-panel" aria-label="Demo session report">
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
        <a className="secondary-link" href={linguaFrameApi.demoRunPackageDownloadUrl(jobId)}>
          Download demo run package
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
    <section id="demo-evidence" className="panel demo-evidence-panel" aria-label="Demo evidence">
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

function DemoComparisonPanel({
  candidates,
  comparison,
  comparisonJobId,
  error,
  isLoading,
  selectedJob,
  onSelectComparison
}: {
  candidates: CacheReplayCandidate[];
  comparison: JobComparison | null;
  comparisonJobId: string;
  error: string | null;
  isLoading: boolean;
  selectedJob: LocalizationJob;
  onSelectComparison: (jobId: string) => void;
}) {
  const markdownHref = comparison
    ? linguaFrameApi.jobComparisonMarkdownDownloadUrl(selectedJob.jobId, comparison.comparisonJobId)
    : null;

  return (
    <section className="panel demo-comparison-panel" aria-label="Demo comparison">
      <div className="panel-heading">
        <h3>Demo comparison</h3>
        <div className="panel-actions">
          {markdownHref ? (
            <a className="secondary-link" href={markdownHref}>
              Download Markdown
            </a>
          ) : null}
        </div>
      </div>

      <label>
        Comparison job
        <select
          value={comparisonJobId}
          onChange={(event) => void onSelectComparison(event.target.value)}
        >
          <option value="">Choose a completed demo job</option>
          {candidates.map((candidate) => (
            <option key={candidate.jobId} value={candidate.jobId}>
              {candidate.filename} · {candidate.jobId} · {candidate.status}
            </option>
          ))}
        </select>
      </label>
      {candidates.length === 0 ? <p className="muted">No comparison candidates loaded yet.</p> : null}
      {isLoading ? <p className="muted">Loading comparison...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}

      {comparison ? (
        <>
          {!comparison.sameSourceVideo ? (
            <p className="muted">These jobs use different source videos; compare demo outcomes carefully.</p>
          ) : null}
          <dl className="metrics-grid demo-comparison-grid">
            <div>
              <dt>Baseline</dt>
              <dd>{comparison.baseline.jobId}</dd>
            </div>
            <div>
              <dt>Comparison</dt>
              <dd>{comparison.comparison.jobId}</dd>
            </div>
            <div>
              <dt>Source video</dt>
              <dd>{comparison.baseline.videoId}</dd>
            </div>
            <div>
              <dt>Comparison profile</dt>
              <dd>{formatDemoProfileId(comparison.comparison.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Quality delta</dt>
              <dd>{formatNullableSignedInteger(comparison.delta.qualityScore)}</dd>
            </div>
            <div>
              <dt>Cost delta</dt>
              <dd>{formatSignedCost(comparison.delta.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Model calls</dt>
              <dd>{formatSignedInteger(comparison.delta.modelCallCount)} calls</dd>
            </div>
            <div>
              <dt>Provider cache</dt>
              <dd>{formatSignedInteger(comparison.delta.providerCacheHitCount)} hits</dd>
            </div>
          </dl>
          <div className="comparison-setting-list">
            <h4>Setting differences</h4>
            {comparison.settingDiffs.length === 0 ? (
              <p className="muted">No tracked settings changed between these jobs.</p>
            ) : (
              <ul>
                {comparison.settingDiffs.map((diff) => (
                  <li key={diff.field}>
                    <strong>{diff.field}</strong>
                    <span>
                      {formatNullableSetting(diff.baselineValue)} -&gt; {formatNullableSetting(diff.comparisonValue)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoRunMatrixPanel({
  error,
  isLoading,
  matrix,
  onRefresh
}: {
  error: string | null;
  isLoading: boolean;
  matrix: DemoRunMatrix | null;
  onRefresh: () => void;
}) {
  return (
    <section className="panel demo-run-matrix-panel" aria-label="Demo run matrix">
      <div className="panel-heading">
        <h3>Demo run matrix</h3>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>
      {isLoading && !matrix ? <p className="muted">Loading demo run matrix...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {matrix ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Source video</dt>
              <dd>{matrix.videoId}</dd>
            </div>
            <div>
              <dt>Runs</dt>
              <dd>{matrix.jobs.length}</dd>
            </div>
            <div>
              <dt>Recommended baseline</dt>
              <dd>{matrix.recommendedBaselineJobId ?? 'N/A'}</dd>
            </div>
          </dl>
          <table>
            <thead>
              <tr>
                <th>Profile</th>
                <th>Status</th>
                <th>Quality</th>
                <th>Cost</th>
                <th>Calls</th>
                <th>Cache</th>
                <th>Handoff</th>
              </tr>
            </thead>
            <tbody>
              {matrix.jobs.map((job) => (
                <tr key={job.jobId}>
                  <td>
                    <strong>{formatDemoProfileId(job.demoProfileId)}</strong>
                    <span className="history-meta">{job.jobId}</span>
                    <RunMatrixBadges matrix={matrix} jobId={job.jobId} />
                  </td>
                  <td>{job.status}</td>
                  <td>{job.qualityScore === null ? 'N/A' : `${job.qualityScore} / 100`}</td>
                  <td>{formatCost(job.estimatedCostUsd)}</td>
                  <td>{job.modelCallCount} calls</td>
                  <td>{job.providerCacheHitCount} provider hits</td>
                  <td>{job.handoffReady ? 'Ready' : 'Attention'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : null}
    </section>
  );
}

function DemoPresenterPackPanel({
  error,
  isLoading,
  pack,
  onRefresh
}: {
  error: string | null;
  isLoading: boolean;
  pack: DemoPresenterPack | null;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);

  async function copyPresenterNotes() {
    if (!pack) {
      return;
    }
    await navigator.clipboard.writeText(pack.presenterNotesMarkdown);
    setStatus('Presenter notes copied.');
  }

  function downloadPresenterNotes() {
    if (!pack) {
      return;
    }
    const blob = new Blob([pack.presenterNotesMarkdown], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `linguaframe-demo-presenter-${sanitizeFilename(pack.anchorJobId)}.md`;
    link.click();
    URL.revokeObjectURL(url);
    setStatus('Presenter notes Markdown downloaded.');
  }

  return (
    <section className="panel demo-presenter-pack-panel" aria-label="Demo presenter pack">
      <div className="panel-heading">
        <h3>Demo presenter pack</h3>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button type="button" className="secondary-button" onClick={copyPresenterNotes} disabled={!pack}>
            Copy presenter notes
          </button>
          <button type="button" className="secondary-button" onClick={downloadPresenterNotes} disabled={!pack}>
            Download presenter notes
          </button>
        </div>
      </div>
      {isLoading && !pack ? <p className="muted">Loading demo presenter pack...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="success-text">{status}</p> : null}
      {pack ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Readiness</dt>
              <dd>{pack.readinessStatus}</dd>
            </div>
            <div>
              <dt>Recommended baseline</dt>
              <dd>{pack.recommendedBaselineJobId ?? 'N/A'}</dd>
            </div>
            <div>
              <dt>Best quality</dt>
              <dd>{pack.bestQualityJobId ?? 'N/A'}</dd>
            </div>
            <div>
              <dt>Lowest cost</dt>
              <dd>{pack.lowestCostJobId ?? 'N/A'}</dd>
            </div>
          </dl>
          <table>
            <thead>
              <tr>
                <th>Run</th>
                <th>Status</th>
                <th>Quality</th>
                <th>Cost</th>
                <th>Calls</th>
                <th>Cache</th>
                <th>Handoff</th>
              </tr>
            </thead>
            <tbody>
              {pack.runs.map((run) => (
                <tr key={run.jobId}>
                  <td>
                    <strong>{formatDemoProfileId(run.demoProfileId)}</strong>
                    <span className="history-meta">{run.jobId}</span>
                    <span className="badge-row">
                      {run.roles.map((role) => (
                        <span className="status-pill" key={role}>
                          {formatPresenterRole(role)}
                        </span>
                      ))}
                    </span>
                  </td>
                  <td>{run.status}</td>
                  <td>{run.qualityScore === null ? 'N/A' : `${run.qualityScore} / 100`}</td>
                  <td>{formatCost(run.estimatedCostUsd)}</td>
                  <td>{run.modelCallCount} calls</td>
                  <td>{run.providerCacheHitCount} provider hits</td>
                  <td>{run.handoffReady ? 'Ready' : 'Attention'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="link-list">
            {pack.downloads.map((download) => (
              <a key={download.kind} href={download.url}>
                {download.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoShareSheetPanel({
  error,
  isLoading,
  jobId,
  onRefresh,
  sheet
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  onRefresh: () => void;
  sheet: DemoShareSheet | null;
}) {
  const [status, setStatus] = useState<string | null>(null);

  async function copyShareSheet() {
    if (!sheet) {
      return;
    }
    await navigator.clipboard.writeText(sheet.markdown);
    setStatus('Share sheet copied.');
  }

  return (
    <section className="panel demo-share-sheet-panel" aria-label="Demo share sheet">
      <div className="panel-heading">
        <h3>Demo share sheet</h3>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button type="button" className="secondary-button" onClick={copyShareSheet} disabled={!sheet}>
            Copy share sheet
          </button>
          <a className="secondary-link" href={linguaFrameApi.demoShareSheetMarkdownDownloadUrl(jobId)}>
            Download backend Markdown
          </a>
        </div>
      </div>
      {isLoading && !sheet ? <p className="muted">Loading demo share sheet...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="success-text">{status}</p> : null}
      {sheet ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Readiness</dt>
              <dd>{sheet.readiness}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(sheet.generatedAt)}</dd>
            </div>
            <div>
              <dt>Job</dt>
              <dd>{sheet.jobId}</dd>
            </div>
            <div>
              <dt>Video</dt>
              <dd>{sheet.videoId}</dd>
            </div>
          </dl>
          <h4>{sheet.headline}</h4>
          <p>{sheet.summary}</p>
          <p>
            <strong>Next action:</strong> {sheet.recommendedNextAction}
          </p>
          <ul className="checklist compact-list">
            {sheet.outcomeBullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>
          <div className="link-list">
            {sheet.links.map((link) => (
              <a key={link.kind} href={link.url}>
                {link.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoRunSnapshotPanel({
  error,
  isLoading,
  jobId,
  onRefresh,
  snapshot
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  onRefresh: () => void;
  snapshot: DemoRunSnapshot | null;
}) {
  return (
    <section className="panel demo-run-snapshot-panel" aria-label="Demo snapshot">
      <div className="panel-heading">
        <div>
          <h3>Demo snapshot</h3>
          <p className="muted">Static reviewer workspace for offline inspection.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <a className="secondary-link" href={linguaFrameApi.demoRunSnapshotDownloadUrl(jobId)}>
            Download static snapshot ZIP
          </a>
        </div>
      </div>
      {isLoading && !snapshot ? <p className="muted">Loading demo snapshot...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {snapshot ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Readiness</dt>
              <dd>{snapshot.readiness}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(snapshot.generatedAt)}</dd>
            </div>
            <div>
              <dt>Entries</dt>
              <dd>{snapshot.packageEntries.length}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(snapshot.demoProfileId)}</dd>
            </div>
          </dl>
          <h4>{snapshot.headline}</h4>
          <p>{snapshot.summary}</p>
          <div className="snapshot-section-grid">
            <div>
              <h4>Packaged files</h4>
              <ul className="compact-list">
                {snapshot.sections.map((section) => (
                  <li key={section.kind}>
                    <strong>{section.filename}</strong>
                    <span>{section.status}</span>
                    <p>{section.summary}</p>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Excludes</h4>
              <ul className="compact-list">
                {snapshot.exclusionPolicy.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          </div>
          {snapshot.links.length > 0 ? (
            <div className="link-list">
              {snapshot.links.map((link) => (
                <a key={link.kind} href={link.url}>
                  {link.label}
                </a>
              ))}
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function formatPresenterRole(role: string): string {
  switch (role) {
    case 'ANCHOR':
      return 'Anchor';
    case 'RECOMMENDED_BASELINE':
      return 'Recommended baseline';
    case 'BEST_QUALITY':
      return 'Best quality';
    case 'LOWEST_COST':
      return 'Lowest cost';
    default:
      return role;
  }
}

function RunMatrixBadges({ matrix, jobId }: { matrix: DemoRunMatrix; jobId: string }) {
  const badges: string[] = [];
  if (jobId === matrix.anchorJobId) {
    badges.push('Anchor');
  }
  if (jobId === matrix.recommendedBaselineJobId) {
    badges.push('Baseline');
  }
  if (jobId === matrix.bestQualityJobId) {
    badges.push('Best quality');
  }
  if (jobId === matrix.lowestCostJobId) {
    badges.push('Lowest cost');
  }
  if (badges.length === 0) {
    return null;
  }
  return (
    <span className="badge-row">
      {badges.map((badge) => (
        <span className="status-pill" key={badge}>
          {badge}
        </span>
      ))}
    </span>
  );
}

function QualityEvaluationPanel({ job }: { job: LocalizationJob }) {
  const evaluation = job.qualityEvaluation;
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator.clipboard?.writeText === 'function';
  const markdown = useMemo(() => formatQualityEvaluationEvidence(job), [job]);

  const handleCopyEvidence = useCallback(async () => {
    if (!canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(markdown);
    setStatus('Quality evidence copied.');
  }, [canCopy, markdown]);

  const handleDownloadEvidence = useCallback(() => {
    const blob = new Blob([markdown], {
      type: 'text/markdown;charset=UTF-8'
    });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-job-${sanitizeFilename(job.jobId)}-quality-evidence.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Quality evidence downloaded.');
  }, [job.jobId, markdown]);

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
        <div className="panel-actions">
          <button
            type="button"
            className="secondary-button"
            onClick={handleCopyEvidence}
            disabled={!canCopy}
          >
            Copy quality evidence
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadEvidence}>
            Download quality evidence
          </button>
          <a
            className="secondary-link"
            href={linguaFrameApi.qualityEvaluationEvidenceMarkdownDownloadUrl(job.jobId)}
          >
            Download backend quality evidence
          </a>
          <span className="status-pill">{evaluation.status}</span>
        </div>
      </div>
      {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
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
      <section id="subtitle-review" className="panel" aria-label="Subtitle review">
        <h3>Subtitle review</h3>
        <p className="muted">No subtitle review summary loaded yet.</p>
      </section>
    );
  }

  return (
    <section id="subtitle-review" className="panel" aria-label="Subtitle review">
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
      <section id="subtitle-draft-editor" className="panel" aria-label="Subtitle draft editor">
        <h3>Subtitle draft editor</h3>
        {error ? <p className="muted">{error}</p> : <p className="muted">No editable subtitle draft loaded yet.</p>}
      </section>
    );
  }

  return (
    <section id="subtitle-draft-editor" className="panel" aria-label="Subtitle draft editor">
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
    translationStyle: upload.translationStyle,
    subtitleStylePreset: upload.subtitleStylePreset,
    translationGlossaryEntryCount: upload.translationGlossaryEntryCount,
    translationGlossaryHash: upload.translationGlossaryHash,
    subtitlePolishingMode: upload.subtitlePolishingMode,
    demoProfileId: upload.demoProfileId,
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
      key: 'dubbed-video',
      label: 'Dubbed video',
      playerLabel: 'Dubbed video player',
      kind: 'video',
      artifact: findFirstArtifact(artifacts, 'DUBBED_VIDEO')
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
  const mediaOutputCount = countArtifacts(artifacts, MEDIA_ARTIFACT_TYPES);
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

function buildDemoReviewSteps(
  job: LocalizationJob,
  artifacts: JobArtifact[],
  manifest: DeliveryManifest | null,
  subtitleReview: SubtitleReviewSummary | null,
  subtitleDraft: SubtitleDraftSummary | null,
  checklist: DemoHandoffChecklist,
  report: DemoSessionReport
): DemoReviewStep[] {
  const reviewedSubtitleCount = countArtifacts(artifacts, [
    'REVIEWED_SUBTITLE_JSON',
    'REVIEWED_SUBTITLE_SRT',
    'REVIEWED_SUBTITLE_VTT'
  ]);
  const mediaOutputCount = countArtifacts(artifacts, MEDIA_ARTIFACT_TYPES);
  const pipelineTerminal = job.pipelineProgress?.terminal ?? ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status);
  const hasInput = Boolean(job.jobId && job.videoId && job.targetLanguage);

  const steps: DemoReviewStep[] = [
    {
      key: 'input',
      title: 'Input',
      status: hasInput ? 'READY' : 'BLOCKED',
      detail: hasInput
        ? `Job ${job.jobId} localizes video ${job.videoId} to ${job.targetLanguage}.`
        : 'The selected job is missing required input metadata.',
      anchor: '#result-delivery',
      actionLabel: 'Open input'
    },
    {
      key: 'pipeline',
      title: 'Pipeline',
      status: pipelineTerminal ? 'READY' : 'ATTENTION',
      detail: pipelineTerminal
        ? `Pipeline is terminal with job status ${job.status}.`
        : 'Pipeline is still running or stage timing has not reached a terminal state.',
      anchor: '#pipeline-progress',
      actionLabel: 'Open pipeline'
    },
    {
      key: 'review',
      title: 'Review',
      status: subtitleReview ? (reviewedSubtitleCount >= 3 ? 'READY' : 'ATTENTION') : 'BLOCKED',
      detail: subtitleReview
        ? reviewedSubtitleCount >= 3
          ? `${reviewedSubtitleCount} reviewed subtitle artifacts are ready${subtitleDraft ? ` with ${subtitleDraft.editedSegmentCount} saved draft edits.` : '.'}`
          : `Subtitle review is loaded, but only ${reviewedSubtitleCount} reviewed subtitle artifacts are ready.`
        : 'Subtitle review metadata is not loaded yet.',
      anchor: '#subtitle-review',
      actionLabel: 'Open review'
    },
    {
      key: 'delivery',
      title: 'Delivery',
      status: manifest ? (manifest.handoffReady ? 'READY' : 'ATTENTION') : 'BLOCKED',
      detail: manifest
        ? manifest.handoffReady
          ? `${manifest.reviewedSubtitleArtifactCount} reviewed subtitle files and ${mediaOutputCount} playable media outputs are available.`
          : 'Delivery manifest is loaded, but reviewed handoff outputs still need attention.'
        : 'Delivery manifest is not loaded.',
      anchor: '#delivery-handoff',
      actionLabel: 'Open delivery'
    },
    {
      key: 'evidence',
      title: 'Evidence',
      status: 'READY',
      detail: 'Diagnostics, backend evidence, evidence bundle, and handoff package links are available for this job.',
      anchor: '#demo-evidence',
      actionLabel: 'Open evidence'
    },
    {
      key: 'handoff',
      title: 'Handoff',
      status: checklist.overallStatus === 'READY' && report.status === 'READY' ? 'READY' : 'ATTENTION',
      detail:
        checklist.overallStatus === 'READY' && report.status === 'READY'
          ? 'Checklist and session report both indicate this run is ready for handoff.'
          : 'Checklist or session report still needs attention before handoff.',
      anchor: '#demo-session-report',
      actionLabel: 'Open handoff'
    }
  ];

  if (job.failureTriage) {
    steps.push({
      key: 'failure-triage',
      title: 'Failure triage',
      status: 'ATTENTION',
      detail: `${job.failureTriage.category}: ${job.failureTriage.recommendedAction}`,
      anchor: '#failure-triage',
      actionLabel: 'Open triage'
    });
  }

  return steps;
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
  const mediaOutputCount = countArtifacts(artifacts, MEDIA_ARTIFACT_TYPES);
  const terminalState = job.pipelineProgress?.terminal ?? ['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.status);
  const sections: DemoSessionReport['sections'] = [
    {
      title: 'Input and job',
      lines: [
        `Job ${job.jobId}`,
        `Video ${job.videoId}`,
        `Target language ${job.targetLanguage}`,
        `Demo profile ${formatDemoProfileId(job.demoProfileId)}`,
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

function demoReviewStatusClassName(status: DemoReviewStep['status']): string {
  if (status === 'READY') {
    return 'checklist-status-pass';
  }
  if (status === 'ATTENTION') {
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
      translationStyle: job.translationStyle,
      subtitleStylePreset: job.subtitleStylePreset,
      translationGlossaryEntryCount: job.translationGlossaryEntryCount,
      translationGlossaryHash: job.translationGlossaryHash,
      subtitlePolishingMode: job.subtitlePolishingMode,
      demoProfileId: job.demoProfileId,
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

function formatQualityEvaluationEvidence(job: LocalizationJob): string {
  const evaluation = job.qualityEvaluation;
  const lines = [
    '# LinguaFrame Quality Evaluation Evidence',
    '',
    '## Job',
    `- Job: ${job.jobId}`,
    `- Video: ${job.videoId}`,
    `- Target language: ${job.targetLanguage}`,
    `- Demo profile: ${formatDemoProfileId(job.demoProfileId)}`,
    `- Translation style: ${formatTranslationStyle(job.translationStyle)}`,
    `- Subtitle style: ${formatSubtitleStylePreset(job.subtitleStylePreset)}`,
    `- Subtitle polishing: ${formatSubtitlePolishingMode(job.subtitlePolishingMode)}`,
    `- Translation glossary: ${formatGlossaryMetadata(job.translationGlossaryEntryCount, job.translationGlossaryHash)}`,
    `- Job status: ${job.status}`,
    `- Created at: ${job.createdAt}`,
    ''
  ];

  if (!evaluation) {
    lines.push('## Evaluation');
    lines.push('- Status: NOT_RECORDED');
    lines.push('- Quality evaluation has not been recorded for this job.');
    lines.push('');
  } else {
    lines.push('## Evaluation');
    lines.push(`- Status: ${evaluation.status}`);
    lines.push(`- Score: ${evaluation.score} / 100`);
    lines.push(`- Verdict: ${evaluation.verdict}`);
    lines.push(`- Evaluation language: ${evaluation.language}`);
    lines.push(`- Created at: ${evaluation.createdAt}`);
    if (evaluation.safeErrorSummary) {
      lines.push(`- Safe error summary: ${evaluation.safeErrorSummary}`);
    }
    lines.push('');
    lines.push('## Dimensions');
    lines.push(`- Completeness: ${evaluation.completeness} / 100`);
    lines.push(`- Readability: ${evaluation.readability} / 100`);
    lines.push(`- Timing preservation: ${evaluation.timingPreservation} / 100`);
    lines.push(`- Naturalness: ${evaluation.naturalness} / 100`);
    lines.push('');
    appendMarkdownList(lines, 'Issues', 'Issue count', evaluation.issues);
    appendMarkdownList(lines, 'Suggested Fixes', 'Suggested fix count', evaluation.suggestedFixes);
  }

  lines.push('## Related Safe Routes');
  lines.push(`- Job detail: /api/jobs/${job.jobId}`);
  lines.push(`- Diagnostics: ${linguaFrameApi.jobDiagnosticsDownloadUrl(job.jobId)}`);
  lines.push(`- Backend evidence: ${linguaFrameApi.jobEvidenceMarkdownDownloadUrl(job.jobId)}`);
  lines.push(
    `- Backend quality evidence: ${linguaFrameApi.qualityEvaluationEvidenceMarkdownDownloadUrl(job.jobId)}`
  );
  lines.push(`- Subtitle review: /api/jobs/${job.jobId}/subtitle-review?language=${job.targetLanguage}`);
  return lines.join('\n');
}

function appendMarkdownList(lines: string[], title: string, countLabel: string, values: string[]) {
  lines.push(`## ${title}`);
  lines.push(`- ${countLabel}: ${values.length}`);
  if (values.length === 0) {
    lines.push('- None recorded.');
  } else {
    values.forEach((value) => lines.push(`- ${value}`));
  }
  lines.push('');
}

function formatDemoEvidenceMarkdown(evidence: DemoEvidence): string {
  const lines = [
    '# LinguaFrame Demo Evidence',
    '',
    `- Job: ${evidence.job.jobId}`,
    `- Video: ${evidence.job.videoId}`,
    `- Target language: ${evidence.job.targetLanguage}`,
    `- Demo profile: ${formatDemoProfileId(evidence.job.demoProfileId)}`,
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

function formatDemoReviewPresenterNotes(
  job: LocalizationJob,
  steps: DemoReviewStep[],
  report: DemoSessionReport
): string {
  const ready = steps
    .filter((step) => step.key !== 'failure-triage')
    .every((step) => step.status === 'READY');
  const lines = [
    '# LinguaFrame Demo Review Notes',
    '',
    `- Job: ${job.jobId}`,
    `- Video: ${job.videoId}`,
    `- Target language: ${job.targetLanguage}`,
    `- Demo profile: ${formatDemoProfileId(job.demoProfileId)}`,
    `- Translation style: ${formatTranslationStyle(job.translationStyle)}`,
    `- Subtitle style: ${formatSubtitleStylePreset(job.subtitleStylePreset)}`,
    `- Subtitle polishing: ${formatSubtitlePolishingMode(job.subtitlePolishingMode)}`,
    `- Translation glossary: ${formatGlossaryMetadata(job.translationGlossaryEntryCount, job.translationGlossaryHash)}`,
    `- Overall: ${ready ? 'READY' : 'ATTENTION'}`,
    '',
    '## Walkthrough'
  ];
  steps.forEach((step) => {
    lines.push(`- ${step.status} ${step.title}: ${step.detail}`);
  });
  lines.push('', '## Session report');
  report.sections.forEach((section) => {
    lines.push(`- ${section.title}`);
    section.lines.forEach((line) => {
      lines.push(`  - ${line}`);
    });
  });
  return lines.join('\n');
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
        ttsVoice: job.ttsVoice,
        translationStyle: job.translationStyle,
        subtitleStylePreset: job.subtitleStylePreset,
        translationGlossaryEntryCount: job.translationGlossaryEntryCount,
        translationGlossaryHash: job.translationGlossaryHash,
        subtitlePolishingMode: job.subtitlePolishingMode,
        demoProfileId: job.demoProfileId
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
        ttsVoice: job.ttsVoice,
        translationStyle: job.translationStyle,
        subtitleStylePreset: job.subtitleStylePreset,
        translationGlossaryEntryCount: job.translationGlossaryEntryCount,
        translationGlossaryHash: job.translationGlossaryHash,
        subtitlePolishingMode: job.subtitlePolishingMode,
        demoProfileId: job.demoProfileId
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
    translationStyle: formatTranslationStyle(job.translationStyle),
    subtitleStylePreset: formatSubtitleStylePreset(job.subtitleStylePreset),
    translationGlossaryEntryCount: job.translationGlossaryEntryCount,
    translationGlossaryHash: job.translationGlossaryHash,
    subtitlePolishingMode: formatSubtitlePolishingMode(job.subtitlePolishingMode),
    demoProfileId: formatDemoProfileId(job.demoProfileId),
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
    `- Baseline subtitle style: ${evidence.baseline.subtitleStylePreset}`,
    `- Comparison subtitle style: ${evidence.comparison.subtitleStylePreset}`,
    `- Baseline demo profile: ${evidence.baseline.demoProfileId}`,
    `- Comparison demo profile: ${evidence.comparison.demoProfileId}`,
    `- Baseline subtitle polishing: ${evidence.baseline.subtitlePolishingMode}`,
    `- Comparison subtitle polishing: ${evidence.comparison.subtitlePolishingMode}`,
    `- Baseline translation glossary: ${formatGlossaryMetadata(evidence.baseline.translationGlossaryEntryCount, evidence.baseline.translationGlossaryHash)}`,
    `- Comparison translation glossary: ${formatGlossaryMetadata(evidence.comparison.translationGlossaryEntryCount, evidence.comparison.translationGlossaryHash)}`,
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

function formatLimitValue(value: number): string {
  return value > 0 ? String(value) : 'unlimited';
}

function formatQuotaLimit(
  preflight: OwnerQuotaPreflight,
  name: string,
  fallbackCurrent: number
): string {
  const limit = preflight.limits.find((candidate) => candidate.name === name);
  if (!limit || !limit.enabled) {
    return `${fallbackCurrent} / unlimited`;
  }
  return `${limit.current} / ${limit.limit}`;
}

function formatCostQuotaLimit(
  preflight: OwnerQuotaPreflight,
  name: string,
  fallbackCurrent: number
): string {
  const limit = preflight.limits.find((candidate) => candidate.name === name);
  if (!limit || !limit.enabled) {
    return `${formatCost(fallbackCurrent)} / unlimited`;
  }
  return `${formatCost(limit.current)} / ${formatCost(limit.limit)}`;
}

function formatGalleryCost(value: string): string {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return '$0.00';
  }
  return `$${parsed.toFixed(2)}`;
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

function formatNullableSignedInteger(value: number | null): string {
  return value === null ? 'N/A' : formatSignedInteger(value);
}

function formatNullableSetting(value: string | null): string {
  return value && value.trim().length > 0 ? value : 'None';
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

function formatTranslationStyle(value: string | null | undefined): string {
  const normalized = value?.trim().toUpperCase() || 'NATURAL';
  const option = TRANSLATION_STYLE_OPTIONS.find((candidate) => candidate.value === normalized);
  return option?.label ?? normalized;
}

function formatSubtitleStylePreset(value: string | null | undefined): string {
  const normalized = value?.trim().toUpperCase() || 'STANDARD';
  const option = SUBTITLE_STYLE_PRESET_OPTIONS.find((candidate) => candidate.value === normalized);
  return option?.label ?? normalized;
}

function formatSubtitlePolishingMode(value: string | null | undefined): string {
  const normalized = value?.trim().toUpperCase() || 'OFF';
  const option = SUBTITLE_POLISHING_MODE_OPTIONS.find((candidate) => candidate.value === normalized);
  return option?.label ?? normalized;
}

function formatDemoProfileId(value: string | null | undefined): string {
  return value?.trim() || 'Manual settings';
}

function formatGlossaryMetadata(entryCount: number | null | undefined, hash: string | null | undefined): string {
  const count = entryCount ?? 0;
  if (count <= 0) {
    return 'No glossary';
  }
  const normalizedHash = hash?.trim();
  return `Glossary ${count} · ${normalizedHash ? normalizedHash.slice(0, 8) : 'no hash'}`;
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

function readinessStatusClassName(status: string): string {
  if (status === 'READY') {
    return 'status-pill success';
  }
  if (status === 'BLOCKED') {
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

function formatAuthSessionStatus(status: AuthSessionStatus | null): string {
  if (!status) {
    return 'Checking local account';
  }
  if (!status.enabled || !status.configured) {
    return 'Local account disabled';
  }
  return status.authenticated ? 'Local account active' : 'Local account required';
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
    budgetGuard: 'Budget guard',
    dailyBudgetGuard: 'Daily budget guard',
    ownerQuota: 'Owner quota'
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

function formatDurationSeconds(durationSeconds: number | null): string {
  if (durationSeconds === null) {
    return 'Unknown';
  }
  return `${durationSeconds}s`;
}

function formatArtifactHash(contentSha256: string): string {
  if (!contentSha256) {
    return '-';
  }
  return contentSha256.slice(0, 12);
}
