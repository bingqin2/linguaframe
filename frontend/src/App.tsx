import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { linguaFrameApi, readAuthToken, readDemoToken, writeAuthToken, writeDemoToken } from './api/linguaframeApi';
import type {
  AuthSessionStatus,
  DemoAcceptanceGate,
  DemoCompletionCertificate,
  DemoEvidenceClosurePackage,
  DemoHandoffPortal,
  DemoPresentationCockpit,
  DemoRunLauncher,
  DemoReplayCard,
  DemoSampleMediaCatalog,
  DemoSessionCommandCenter,
  DemoSessionCommandCenterStatus,
  DeliveryManifest,
  DemoPresenterPack,
  DemoRunMatrix,
  DemoRunMonitor,
  DemoReviewerWorkspace,
  DemoRunSnapshot,
  DemoRunVarianceReport,
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
  ModelUsageLedger,
  NarrationDemoPreset,
  NarrationEvidence,
  NarrationScriptPackage,
  ImportNarrationScriptPackageRequest,
  NarrationWorkspace,
  OpenAiReadinessEvidence,
  OpenAiSmokeProof,
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
  ReviewedSubtitleWorkflow,
  SubtitleDraftSummary,
  SubtitleReviewDecision,
  SubtitleReviewEvidence,
  SubtitleReviewIssueCategory,
  SubtitleReviewSummary,
  SubtitleSegment,
  TranscriptSegment,
  UploadCostEstimate,
  UploadExecutionPlan
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
const SUBTITLE_REVIEW_DECISIONS: SubtitleReviewDecision[] = ['UNREVIEWED', 'ACCEPTED', 'EDITED', 'NEEDS_FOLLOWUP'];
const SUBTITLE_REVIEW_ISSUE_CATEGORIES: SubtitleReviewIssueCategory[] = [
  'TERM',
  'TONE',
  'TIMING',
  'READABILITY',
  'MISSING_TEXT',
  'OTHER'
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
  const [narrationDemoPresets, setNarrationDemoPresets] = useState<NarrationDemoPreset[]>([]);
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
  const [uploadExecutionPlan, setUploadExecutionPlan] = useState<UploadExecutionPlan | null>(null);
  const [uploadExecutionPlanError, setUploadExecutionPlanError] = useState<string | null>(null);
  const [isEstimatingUploadExecutionPlan, setIsEstimatingUploadExecutionPlan] = useState(false);
  const [uploadExecutionPlanReportStatus, setUploadExecutionPlanReportStatus] = useState<string | null>(null);
  const [isDownloadingUploadExecutionPlanReport, setIsDownloadingUploadExecutionPlanReport] = useState(false);
  const [uploadCostEstimate, setUploadCostEstimate] = useState<UploadCostEstimate | null>(null);
  const [uploadCostEstimateError, setUploadCostEstimateError] = useState<string | null>(null);
  const [isEstimatingUploadCost, setIsEstimatingUploadCost] = useState(false);
  const [ownerQuotaPreflight, setOwnerQuotaPreflight] = useState<OwnerQuotaPreflight | null>(null);
  const [ownerQuotaPreflightError, setOwnerQuotaPreflightError] = useState<string | null>(null);
  const [isLoadingOwnerQuotaPreflight, setIsLoadingOwnerQuotaPreflight] = useState(false);
  const [demoUploadReadiness, setDemoUploadReadiness] = useState<DemoUploadReadiness | null>(null);
  const [demoUploadReadinessError, setDemoUploadReadinessError] = useState<string | null>(null);
  const [isLoadingDemoUploadReadiness, setIsLoadingDemoUploadReadiness] = useState(false);
  const [demoSampleMediaCatalog, setDemoSampleMediaCatalog] = useState<DemoSampleMediaCatalog | null>(null);
  const [demoSampleMediaCatalogError, setDemoSampleMediaCatalogError] = useState<string | null>(null);
  const [isLoadingDemoSampleMediaCatalog, setIsLoadingDemoSampleMediaCatalog] = useState(false);
  const [demoRunLauncher, setDemoRunLauncher] = useState<DemoRunLauncher | null>(null);
  const [demoRunLauncherError, setDemoRunLauncherError] = useState<string | null>(null);
  const [isLoadingDemoRunLauncher, setIsLoadingDemoRunLauncher] = useState(false);
  const [demoPresentationCockpit, setDemoPresentationCockpit] = useState<DemoPresentationCockpit | null>(null);
  const [demoPresentationCockpitError, setDemoPresentationCockpitError] = useState<string | null>(null);
  const [isLoadingDemoPresentationCockpit, setIsLoadingDemoPresentationCockpit] = useState(false);
  const [demoSessionCommandCenter, setDemoSessionCommandCenter] = useState<DemoSessionCommandCenter | null>(null);
  const [demoSessionCommandCenterError, setDemoSessionCommandCenterError] = useState<string | null>(null);
  const [isLoadingDemoSessionCommandCenter, setIsLoadingDemoSessionCommandCenter] = useState(false);
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
  const [reviewedSubtitleWorkflow, setReviewedSubtitleWorkflow] = useState<ReviewedSubtitleWorkflow | null>(null);
  const [reviewedSubtitleWorkflowError, setReviewedSubtitleWorkflowError] = useState<string | null>(null);
  const [subtitleReviewEvidence, setSubtitleReviewEvidence] = useState<SubtitleReviewEvidence | null>(null);
  const [subtitleReviewEvidenceError, setSubtitleReviewEvidenceError] = useState<string | null>(null);
  const [subtitleDraft, setSubtitleDraft] = useState<SubtitleDraftSummary | null>(null);
  const [subtitleDraftError, setSubtitleDraftError] = useState<string | null>(null);
  const [subtitleDraftStatus, setSubtitleDraftStatus] = useState<string | null>(null);
  const [isSavingSubtitleDraft, setIsSavingSubtitleDraft] = useState(false);
  const [isClearingSubtitleDraft, setIsClearingSubtitleDraft] = useState(false);
  const [isPublishingReviewedSubtitles, setIsPublishingReviewedSubtitles] = useState(false);
  const [narrationWorkspace, setNarrationWorkspace] = useState<NarrationWorkspace | null>(null);
  const [narrationEvidence, setNarrationEvidence] = useState<NarrationEvidence | null>(null);
  const [narrationScriptPackage, setNarrationScriptPackage] = useState<NarrationScriptPackage | null>(null);
  const [narrationError, setNarrationError] = useState<string | null>(null);
  const [narrationStatus, setNarrationStatus] = useState<string | null>(null);
  const [isSavingNarration, setIsSavingNarration] = useState(false);
  const [isClearingNarration, setIsClearingNarration] = useState(false);
  const [isGeneratingNarration, setIsGeneratingNarration] = useState(false);
  const [isGeneratingNarratedVideo, setIsGeneratingNarratedVideo] = useState(false);
  const [promptTemplates, setPromptTemplates] = useState<PromptTemplate[]>([]);
  const [promptTemplateError, setPromptTemplateError] = useState<string | null>(null);
  const [operatorDashboard, setOperatorDashboard] = useState<OperatorDashboard | null>(null);
  const [operatorDashboardError, setOperatorDashboardError] = useState<string | null>(null);
  const [isLoadingOperatorDashboard, setIsLoadingOperatorDashboard] = useState(false);
  const [modelUsageLedger, setModelUsageLedger] = useState<ModelUsageLedger | null>(null);
  const [modelUsageLedgerError, setModelUsageLedgerError] = useState<string | null>(null);
  const [isLoadingModelUsageLedger, setIsLoadingModelUsageLedger] = useState(false);
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
  const [openAiReadinessEvidence, setOpenAiReadinessEvidence] = useState<OpenAiReadinessEvidence | null>(null);
  const [openAiReadinessEvidenceError, setOpenAiReadinessEvidenceError] = useState<string | null>(null);
  const [openAiSmokeProof, setOpenAiSmokeProof] = useState<OpenAiSmokeProof | null>(null);
  const [openAiSmokeProofError, setOpenAiSmokeProofError] = useState<string | null>(null);
  const [isLoadingOpenAiSmokeProof, setIsLoadingOpenAiSmokeProof] = useState(false);
  const [demoReviewerWorkspace, setDemoReviewerWorkspace] = useState<DemoReviewerWorkspace | null>(null);
  const [demoReviewerWorkspaceError, setDemoReviewerWorkspaceError] = useState<string | null>(null);
  const [isLoadingDemoReviewerWorkspace, setIsLoadingDemoReviewerWorkspace] = useState(false);
  const [demoHandoffPortal, setDemoHandoffPortal] = useState<DemoHandoffPortal | null>(null);
  const [demoHandoffPortalError, setDemoHandoffPortalError] = useState<string | null>(null);
  const [isLoadingDemoHandoffPortal, setIsLoadingDemoHandoffPortal] = useState(false);
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
  const [demoReplayCard, setDemoReplayCard] = useState<DemoReplayCard | null>(null);
  const [demoReplayCardError, setDemoReplayCardError] = useState<string | null>(null);
  const [isLoadingDemoReplayCard, setIsLoadingDemoReplayCard] = useState(false);
  const [demoCompletionCertificate, setDemoCompletionCertificate] = useState<DemoCompletionCertificate | null>(null);
  const [demoCompletionCertificateError, setDemoCompletionCertificateError] = useState<string | null>(null);
  const [isLoadingDemoCompletionCertificate, setIsLoadingDemoCompletionCertificate] = useState(false);
  const [demoAcceptanceGate, setDemoAcceptanceGate] = useState<DemoAcceptanceGate | null>(null);
  const [demoAcceptanceGateError, setDemoAcceptanceGateError] = useState<string | null>(null);
  const [isLoadingDemoAcceptanceGate, setIsLoadingDemoAcceptanceGate] = useState(false);
  const [demoRunVariance, setDemoRunVariance] = useState<DemoRunVarianceReport | null>(null);
  const [demoRunVarianceError, setDemoRunVarianceError] = useState<string | null>(null);
  const [isLoadingDemoRunVariance, setIsLoadingDemoRunVariance] = useState(false);
  const [demoEvidenceClosure, setDemoEvidenceClosure] = useState<DemoEvidenceClosurePackage | null>(null);
  const [demoEvidenceClosureError, setDemoEvidenceClosureError] = useState<string | null>(null);
  const [isLoadingDemoEvidenceClosure, setIsLoadingDemoEvidenceClosure] = useState(false);
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
          setDemoReplayCard(null);
          setDemoReplayCardError(null);
          setDemoCompletionCertificate(null);
          setDemoCompletionCertificateError(null);
          setDemoAcceptanceGate(null);
          setDemoAcceptanceGateError(null);
          setDemoRunVariance(null);
          setDemoRunVarianceError(null);
          setDemoEvidenceClosure(null);
          setDemoEvidenceClosureError(null);
          setDemoShareSheet(null);
          setDemoShareSheetError(null);
          setOpenAiSmokeProof(null);
          setOpenAiSmokeProofError(null);
          setDemoReviewerWorkspace(null);
          setDemoReviewerWorkspaceError(null);
          setDemoHandoffPortal(null);
          setDemoHandoffPortalError(null);
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

  const loadOpenAiSmokeProof = useCallback(async (jobId: string) => {
    setIsLoadingOpenAiSmokeProof(true);
    try {
      const proof = await linguaFrameApi.getOpenAiSmokeProof(jobId);
      setOpenAiSmokeProof(proof);
      setOpenAiSmokeProofError(null);
    } catch (proofLoadError) {
      setOpenAiSmokeProof(null);
      setOpenAiSmokeProofError(toErrorMessage(proofLoadError));
    } finally {
      setIsLoadingOpenAiSmokeProof(false);
    }
  }, []);

  const loadDemoReviewerWorkspace = useCallback(async (jobId: string) => {
    setIsLoadingDemoReviewerWorkspace(true);
    try {
      const workspace = await linguaFrameApi.getDemoReviewerWorkspace(jobId);
      setDemoReviewerWorkspace(workspace);
      setDemoReviewerWorkspaceError(null);
    } catch (workspaceLoadError) {
      setDemoReviewerWorkspace(null);
      setDemoReviewerWorkspaceError(toErrorMessage(workspaceLoadError));
    } finally {
      setIsLoadingDemoReviewerWorkspace(false);
    }
  }, []);

  const loadDemoHandoffPortal = useCallback(async (jobId: string) => {
    setIsLoadingDemoHandoffPortal(true);
    try {
      const portal = await linguaFrameApi.getDemoHandoffPortal(jobId);
      setDemoHandoffPortal(portal);
      setDemoHandoffPortalError(null);
    } catch (portalLoadError) {
      setDemoHandoffPortal(null);
      setDemoHandoffPortalError(toErrorMessage(portalLoadError));
    } finally {
      setIsLoadingDemoHandoffPortal(false);
    }
  }, []);

  const loadDemoReplayCard = useCallback(async (jobId: string) => {
    setIsLoadingDemoReplayCard(true);
    try {
      const card = await linguaFrameApi.getDemoReplayCard(jobId);
      setDemoReplayCard(card);
      setDemoReplayCardError(null);
    } catch (cardLoadError) {
      setDemoReplayCard(null);
      setDemoReplayCardError(toErrorMessage(cardLoadError));
    } finally {
      setIsLoadingDemoReplayCard(false);
    }
  }, []);

  const loadDemoCompletionCertificate = useCallback(async (jobId: string) => {
    setIsLoadingDemoCompletionCertificate(true);
    try {
      const certificate = await linguaFrameApi.getDemoCompletionCertificate(jobId);
      setDemoCompletionCertificate(certificate);
      setDemoCompletionCertificateError(null);
    } catch (certificateLoadError) {
      setDemoCompletionCertificate(null);
      setDemoCompletionCertificateError(toErrorMessage(certificateLoadError));
    } finally {
      setIsLoadingDemoCompletionCertificate(false);
    }
  }, []);

  const loadDemoAcceptanceGate = useCallback(async (jobId: string) => {
    setIsLoadingDemoAcceptanceGate(true);
    try {
      const gate = await linguaFrameApi.getDemoAcceptanceGate(jobId);
      setDemoAcceptanceGate(gate);
      setDemoAcceptanceGateError(null);
    } catch (gateLoadError) {
      setDemoAcceptanceGate(null);
      setDemoAcceptanceGateError(toErrorMessage(gateLoadError));
    } finally {
      setIsLoadingDemoAcceptanceGate(false);
    }
  }, []);

  const loadDemoRunVariance = useCallback(async (jobId: string, preUploadJson?: string) => {
    setIsLoadingDemoRunVariance(true);
    try {
      const variance = await linguaFrameApi.getDemoRunVariance(jobId, preUploadJson);
      setDemoRunVariance(variance);
      setDemoRunVarianceError(null);
    } catch (varianceLoadError) {
      setDemoRunVariance(null);
      setDemoRunVarianceError(toErrorMessage(varianceLoadError));
    } finally {
      setIsLoadingDemoRunVariance(false);
    }
  }, []);

  const loadDemoEvidenceClosure = useCallback(async (jobId: string, preUploadJson?: string) => {
    setIsLoadingDemoEvidenceClosure(true);
    try {
      const closure = await linguaFrameApi.getDemoEvidenceClosure(jobId, preUploadJson);
      setDemoEvidenceClosure(closure);
      setDemoEvidenceClosureError(null);
    } catch (closureLoadError) {
      setDemoEvidenceClosure(null);
      setDemoEvidenceClosureError(toErrorMessage(closureLoadError));
    } finally {
      setIsLoadingDemoEvidenceClosure(false);
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

  const loadModelUsageLedger = useCallback(async () => {
    setIsLoadingModelUsageLedger(true);
    try {
      const ledger = await linguaFrameApi.getModelUsageLedger(20);
      setModelUsageLedger(ledger);
      setModelUsageLedgerError(null);
    } catch (ledgerLoadError) {
      setModelUsageLedger(null);
      setModelUsageLedgerError(toErrorMessage(ledgerLoadError));
    } finally {
      setIsLoadingModelUsageLedger(false);
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

  const loadDemoSampleMediaCatalog = useCallback(async () => {
    setIsLoadingDemoSampleMediaCatalog(true);
    try {
      const catalog = await linguaFrameApi.getDemoSampleMediaCatalog();
      setDemoSampleMediaCatalog(catalog);
      setDemoSampleMediaCatalogError(null);
      return catalog;
    } catch (catalogLoadError) {
      setDemoSampleMediaCatalog(null);
      setDemoSampleMediaCatalogError(toErrorMessage(catalogLoadError));
      return null;
    } finally {
      setIsLoadingDemoSampleMediaCatalog(false);
    }
  }, []);

  const loadDemoRunLauncher = useCallback(async () => {
    setIsLoadingDemoRunLauncher(true);
    try {
      const launcher = await linguaFrameApi.getDemoRunLauncher();
      setDemoRunLauncher(launcher);
      setDemoRunLauncherError(null);
      return launcher;
    } catch (launcherLoadError) {
      setDemoRunLauncher(null);
      setDemoRunLauncherError(toErrorMessage(launcherLoadError));
      return null;
    } finally {
      setIsLoadingDemoRunLauncher(false);
    }
  }, []);

  const loadDemoPresentationCockpit = useCallback(async (jobId?: string) => {
    setIsLoadingDemoPresentationCockpit(true);
    try {
      const cockpit = await linguaFrameApi.getDemoPresentationCockpit(jobId);
      setDemoPresentationCockpit(cockpit);
      setDemoPresentationCockpitError(null);
      return cockpit;
    } catch (cockpitLoadError) {
      setDemoPresentationCockpit(null);
      setDemoPresentationCockpitError(toErrorMessage(cockpitLoadError));
      return null;
    } finally {
      setIsLoadingDemoPresentationCockpit(false);
    }
  }, []);

  const loadDemoSessionCommandCenter = useCallback(async (jobId?: string) => {
    setIsLoadingDemoSessionCommandCenter(true);
    try {
      const commandCenter = await linguaFrameApi.getDemoSessionCommandCenter(jobId);
      setDemoSessionCommandCenter(commandCenter);
      setDemoSessionCommandCenterError(null);
      return commandCenter;
    } catch (commandCenterLoadError) {
      setDemoSessionCommandCenter(null);
      setDemoSessionCommandCenterError(toErrorMessage(commandCenterLoadError));
      return null;
    } finally {
      setIsLoadingDemoSessionCommandCenter(false);
    }
  }, []);

  const loadRuntimeDependencies = useCallback(async () => {
    setIsLoadingRuntimeDependencies(true);
    const [dependenciesResult, liveChecksResult, openAiReadinessResult] = await Promise.allSettled([
      linguaFrameApi.getRuntimeDependencies(),
      linguaFrameApi.getRuntimeLiveChecks(),
      linguaFrameApi.getOpenAiReadinessEvidence()
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

    if (openAiReadinessResult.status === 'fulfilled') {
      setOpenAiReadinessEvidence(openAiReadinessResult.value);
      setOpenAiReadinessEvidenceError(null);
    } else {
      setOpenAiReadinessEvidence(null);
      setOpenAiReadinessEvidenceError(toErrorMessage(openAiReadinessResult.reason));
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
    const [
      artifactResult,
      manifestResult,
      transcriptResult,
      subtitleResult,
      reviewResult,
      workflowResult,
      evidenceResult,
      draftResult,
      narrationWorkspaceResult,
      narrationEvidenceResult,
      narrationScriptPackageResult
    ] = await Promise.allSettled([
      linguaFrameApi.listArtifacts(jobId),
      linguaFrameApi.getDeliveryManifest(jobId),
      linguaFrameApi.listTranscript(jobId),
      linguaFrameApi.listSubtitles(jobId, language),
      linguaFrameApi.getSubtitleReview(jobId, language),
      linguaFrameApi.getReviewedSubtitleWorkflow(jobId),
      linguaFrameApi.getSubtitleReviewEvidence(jobId),
      linguaFrameApi.getSubtitleDraft(jobId, language),
      linguaFrameApi.getNarrationWorkspace(jobId),
      linguaFrameApi.getNarrationEvidence(jobId),
      linguaFrameApi.getNarrationScriptPackage(jobId)
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

    if (workflowResult.status === 'fulfilled') {
      setReviewedSubtitleWorkflow(workflowResult.value);
      setReviewedSubtitleWorkflowError(null);
    } else {
      setReviewedSubtitleWorkflow(null);
      setReviewedSubtitleWorkflowError(toErrorMessage(workflowResult.reason));
      errors.push(`Reviewed subtitle workflow: ${toErrorMessage(workflowResult.reason)}`);
    }

    if (evidenceResult.status === 'fulfilled') {
      setSubtitleReviewEvidence(evidenceResult.value);
      setSubtitleReviewEvidenceError(null);
    } else {
      setSubtitleReviewEvidence(null);
      setSubtitleReviewEvidenceError(toErrorMessage(evidenceResult.reason));
      errors.push(`Subtitle review evidence: ${toErrorMessage(evidenceResult.reason)}`);
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

    if (narrationWorkspaceResult.status === 'fulfilled') {
      setNarrationWorkspace(narrationWorkspaceResult.value);
      setNarrationError(null);
      setNarrationStatus(null);
    } else {
      setNarrationWorkspace(null);
      setNarrationError(toErrorMessage(narrationWorkspaceResult.reason));
      errors.push(`Narration workspace: ${toErrorMessage(narrationWorkspaceResult.reason)}`);
    }

    if (narrationEvidenceResult.status === 'fulfilled') {
      setNarrationEvidence(narrationEvidenceResult.value);
    } else {
      setNarrationEvidence(null);
      setNarrationError(toErrorMessage(narrationEvidenceResult.reason));
      errors.push(`Narration evidence: ${toErrorMessage(narrationEvidenceResult.reason)}`);
    }

    if (narrationScriptPackageResult.status === 'fulfilled') {
      setNarrationScriptPackage(narrationScriptPackageResult.value);
    } else {
      setNarrationScriptPackage(null);
      setNarrationError(toErrorMessage(narrationScriptPackageResult.reason));
      errors.push(`Narration script package: ${toErrorMessage(narrationScriptPackageResult.reason)}`);
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
    void loadModelUsageLedger();
  }, [loadModelUsageLedger]);

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
    void loadDemoSampleMediaCatalog();
  }, [loadDemoSampleMediaCatalog]);

  useEffect(() => {
    void loadDemoRunLauncher();
  }, [loadDemoRunLauncher]);

  useEffect(() => {
    void loadDemoPresentationCockpit();
  }, [loadDemoPresentationCockpit]);

  useEffect(() => {
    void loadDemoSessionCommandCenter();
  }, [loadDemoSessionCommandCenter]);

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

    linguaFrameApi
      .listNarrationDemoPresets()
      .then((presets) => {
        if (ignore) {
          return;
        }
        setNarrationDemoPresets(presets);
      })
      .catch(() => {
        if (ignore) {
          return;
        }
        setNarrationDemoPresets([]);
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
      void loadJob(job.jobId, { silent: true }).then((nextJob) => {
        void loadSourceMedia(nextJob.videoId);
        void loadDemoPresentationCockpit(nextJob.jobId);
        void loadDemoSessionCommandCenter(nextJob.jobId);
        void loadOpenAiSmokeProof(nextJob.jobId);
        void loadDemoReviewerWorkspace(nextJob.jobId);
        void loadDemoHandoffPortal(nextJob.jobId);
      });
    }, pollIntervalMs);

    return () => window.clearTimeout(timer);
  }, [isSseUnavailable, job, loadDemoHandoffPortal, loadDemoPresentationCockpit, loadDemoReviewerWorkspace, loadDemoSessionCommandCenter, loadJob, loadOpenAiSmokeProof, loadSourceMedia, pollIntervalMs]);

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
        void loadDemoPresentationCockpit(nextJob.jobId);
        void loadDemoSessionCommandCenter(nextJob.jobId);
        void loadOpenAiSmokeProof(nextJob.jobId);
        void loadDemoReviewerWorkspace(nextJob.jobId);
        void loadDemoHandoffPortal(nextJob.jobId);
        if (TERMINAL_STATUSES.has(nextJob.status)) {
          void loadPreviewData(nextJob.jobId, nextJob.targetLanguage);
          void loadDemoRunMatrix(nextJob.jobId);
          void loadDemoPresenterPack(nextJob.jobId);
          void loadDemoReplayCard(nextJob.jobId);
          void loadDemoCompletionCertificate(nextJob.jobId);
          void loadDemoAcceptanceGate(nextJob.jobId);
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
  }, [historyStatusFilter, isSseUnavailable, job, loadDemoAcceptanceGate, loadDemoCompletionCertificate, loadDemoHandoffPortal, loadDemoPresentationCockpit, loadDemoPresenterPack, loadDemoReplayCard, loadDemoReviewerWorkspace, loadDemoRunMatrix, loadDemoRunMonitor, loadDemoRunSnapshot, loadDemoSessionCommandCenter, loadDemoShareSheet, loadHistory, loadOpenAiSmokeProof, loadPreviewData, loadSourceMedia]);

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

  async function handleEstimateUploadCost(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadCostEstimate(null);
      setUploadCostEstimateError('Choose a video file before estimating cost.');
      return;
    }
    setIsEstimatingUploadCost(true);
    try {
      const estimate = await linguaFrameApi.estimateUploadCost(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      setUploadCostEstimate(estimate);
      setUploadCostEstimateError(null);
    } catch (estimateError) {
      setUploadCostEstimate(null);
      setUploadCostEstimateError(toErrorMessage(estimateError));
    } finally {
      setIsEstimatingUploadCost(false);
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
    }
  }

  async function handleEstimateUploadExecutionPlan(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadExecutionPlan(null);
      setUploadExecutionPlanError('Choose a video file before planning execution.');
      setUploadExecutionPlanReportStatus(null);
      return;
    }
    setIsEstimatingUploadExecutionPlan(true);
    try {
      const plan = await linguaFrameApi.estimateUploadExecutionPlan(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      setUploadExecutionPlan(plan);
      setUploadExecutionPlanError(null);
      setUploadExecutionPlanReportStatus(null);
    } catch (planError) {
      setUploadExecutionPlan(null);
      setUploadExecutionPlanError(toErrorMessage(planError));
      setUploadExecutionPlanReportStatus(null);
    } finally {
      setIsEstimatingUploadExecutionPlan(false);
      void loadOwnerQuotaPreflight();
      void loadDemoUploadReadiness();
    }
  }

  async function handleCopyUploadExecutionPlanReport(plan: UploadExecutionPlan | null) {
    if (!plan) {
      setUploadExecutionPlanReportStatus('Run execution planning before copying a report.');
      return;
    }
    if (typeof navigator.clipboard?.writeText !== 'function') {
      setUploadExecutionPlanReportStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(linguaFrameApi.renderUploadExecutionPlanMarkdown(plan));
    setUploadExecutionPlanReportStatus('Copied Markdown execution plan.');
  }

  async function handleDownloadUploadExecutionPlanReport(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadExecutionPlanReportStatus('Choose a video file before downloading a report.');
      return;
    }
    setIsDownloadingUploadExecutionPlanReport(true);
    try {
      const blob = await linguaFrameApi.downloadUploadExecutionPlanMarkdown(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = 'upload-execution-plan.md';
      link.click();
      URL.revokeObjectURL(objectUrl);
      setUploadExecutionPlanReportStatus('Downloaded Markdown execution plan.');
    } catch (reportError) {
      setUploadExecutionPlanReportStatus(toErrorMessage(reportError));
    } finally {
      setIsDownloadingUploadExecutionPlanReport(false);
    }
  }

  async function handleDownloadUploadDecisionPackageReport(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadExecutionPlanReportStatus('Choose a video file before downloading a decision report.');
      return;
    }
    setIsDownloadingUploadExecutionPlanReport(true);
    try {
      const blob = await linguaFrameApi.downloadUploadDecisionPackageMarkdown(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = 'upload-decision-package.md';
      link.click();
      URL.revokeObjectURL(objectUrl);
      setUploadExecutionPlanReportStatus('Downloaded Markdown decision package.');
    } catch (reportError) {
      setUploadExecutionPlanReportStatus(toErrorMessage(reportError));
    } finally {
      setIsDownloadingUploadExecutionPlanReport(false);
    }
  }

  async function handleDownloadUploadDecisionPackageZip(form: HTMLFormElement | null) {
    const file = form ? getSelectedUploadFile(form) : null;
    if (!file) {
      setUploadExecutionPlanReportStatus('Choose a video file before downloading a decision ZIP.');
      return;
    }
    setIsDownloadingUploadExecutionPlanReport(true);
    try {
      const blob = await linguaFrameApi.downloadUploadDecisionPackageZip(
        file,
        targetLanguage.trim(),
        ttsVoice,
        translationStyle,
        subtitleStylePreset,
        translationGlossary,
        subtitlePolishingMode,
        demoProfileId
      );
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = 'upload-decision-package.zip';
      link.click();
      URL.revokeObjectURL(objectUrl);
      setUploadExecutionPlanReportStatus('Downloaded decision package ZIP.');
    } catch (reportError) {
      setUploadExecutionPlanReportStatus(toErrorMessage(reportError));
    } finally {
      setIsDownloadingUploadExecutionPlanReport(false);
    }
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
      await loadDemoReplayCard(upload.jobId);
      await loadDemoCompletionCertificate(upload.jobId);
      await loadDemoAcceptanceGate(upload.jobId);
      await loadDemoRunSnapshot(upload.jobId);
      await loadDemoShareSheet(upload.jobId);
      await loadDemoPresentationCockpit(upload.jobId);
      await loadDemoSessionCommandCenter(upload.jobId);
      await loadOpenAiSmokeProof(upload.jobId);
      await loadDemoReviewerWorkspace(upload.jobId);
      await loadDemoHandoffPortal(upload.jobId);
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
    await loadDemoReplayCard(jobId);
    await loadDemoCompletionCertificate(jobId);
    await loadDemoAcceptanceGate(jobId);
    await loadDemoRunSnapshot(jobId);
    await loadDemoShareSheet(jobId);
    await loadDemoPresentationCockpit(jobId);
    await loadDemoSessionCommandCenter(jobId);
    await loadOpenAiSmokeProof(jobId);
    await loadDemoReviewerWorkspace(jobId);
    await loadDemoHandoffPortal(jobId);
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
      await loadDemoReplayCard(retriedJob.jobId);
      await loadDemoCompletionCertificate(retriedJob.jobId);
      await loadDemoAcceptanceGate(retriedJob.jobId);
      await loadDemoRunSnapshot(retriedJob.jobId);
      await loadDemoShareSheet(retriedJob.jobId);
      await loadDemoPresentationCockpit(retriedJob.jobId);
      await loadDemoSessionCommandCenter(retriedJob.jobId);
      await loadOpenAiSmokeProof(retriedJob.jobId);
      await loadDemoReviewerWorkspace(retriedJob.jobId);
      await loadDemoHandoffPortal(retriedJob.jobId);
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
      await loadDemoReplayCard(cancelledJob.jobId);
      await loadDemoCompletionCertificate(cancelledJob.jobId);
      await loadDemoAcceptanceGate(cancelledJob.jobId);
      await loadDemoRunSnapshot(cancelledJob.jobId);
      await loadDemoShareSheet(cancelledJob.jobId);
      await loadDemoPresentationCockpit(cancelledJob.jobId);
      await loadDemoSessionCommandCenter(cancelledJob.jobId);
      await loadOpenAiSmokeProof(cancelledJob.jobId);
      await loadDemoReviewerWorkspace(cancelledJob.jobId);
      await loadDemoHandoffPortal(cancelledJob.jobId);
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
    await loadDemoReplayCard(recentJob.jobId);
    await loadDemoCompletionCertificate(recentJob.jobId);
    await loadDemoAcceptanceGate(recentJob.jobId);
    await loadDemoRunSnapshot(recentJob.jobId);
    await loadDemoShareSheet(recentJob.jobId);
    await loadDemoPresentationCockpit(recentJob.jobId);
    await loadDemoSessionCommandCenter(recentJob.jobId);
    await loadOpenAiSmokeProof(recentJob.jobId);
    await loadDemoReviewerWorkspace(recentJob.jobId);
    await loadDemoHandoffPortal(recentJob.jobId);
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
    await loadDemoReplayCard(historyJob.jobId);
    await loadDemoCompletionCertificate(historyJob.jobId);
    await loadDemoAcceptanceGate(historyJob.jobId);
    await loadDemoRunSnapshot(historyJob.jobId);
    await loadDemoShareSheet(historyJob.jobId);
    await loadDemoPresentationCockpit(historyJob.jobId);
    await loadDemoSessionCommandCenter(historyJob.jobId);
    await loadOpenAiSmokeProof(historyJob.jobId);
    await loadDemoReviewerWorkspace(historyJob.jobId);
    await loadDemoHandoffPortal(historyJob.jobId);
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
    await loadDemoReplayCard(failure.jobId);
    await loadDemoCompletionCertificate(failure.jobId);
    await loadDemoAcceptanceGate(failure.jobId);
    await loadDemoRunSnapshot(failure.jobId);
    await loadDemoShareSheet(failure.jobId);
    await loadDemoPresentationCockpit(failure.jobId);
    await loadDemoSessionCommandCenter(failure.jobId);
    await loadOpenAiSmokeProof(failure.jobId);
    await loadDemoReviewerWorkspace(failure.jobId);
    await loadDemoHandoffPortal(failure.jobId);
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

  async function handleSaveSubtitleDraft(
    segments: Array<{
      index: number;
      text: string;
      decision: SubtitleReviewDecision;
      issueCategories: SubtitleReviewIssueCategory[];
      reviewerNote: string | null;
    }>
  ) {
    if (!job) {
      return;
    }
    setIsSavingSubtitleDraft(true);
    try {
      const updated = await linguaFrameApi.updateSubtitleDraft(job.jobId, selectedLanguage, { segments });
      const evidence = await linguaFrameApi.getSubtitleReviewEvidence(job.jobId);
      setSubtitleDraft(updated);
      setSubtitleReviewEvidence(evidence);
      setSubtitleReviewEvidenceError(null);
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
      const evidence = await linguaFrameApi.getSubtitleReviewEvidence(job.jobId);
      setSubtitleDraft(cleared);
      setSubtitleReviewEvidence(evidence);
      setSubtitleReviewEvidenceError(null);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus('Draft cleared.');
    } catch (draftError) {
      setSubtitleDraftError(toErrorMessage(draftError));
    } finally {
      setIsClearingSubtitleDraft(false);
    }
  }

  async function handlePublishReviewedSubtitles(includeBurnedVideo: boolean, releaseNotes: string) {
    if (!job) {
      return;
    }
    setIsPublishingReviewedSubtitles(true);
    try {
      const published = await linguaFrameApi.publishReviewedSubtitles(job.jobId, {
        language: selectedLanguage,
        includeBurnedVideo,
        releaseNotes: releaseNotes.trim() || null
      });
      const [refreshedArtifacts, refreshedManifest, refreshedEvidence] = await Promise.all([
        linguaFrameApi.listArtifacts(job.jobId),
        linguaFrameApi.getDeliveryManifest(job.jobId),
        linguaFrameApi.getSubtitleReviewEvidence(job.jobId)
      ]);
      setArtifacts(refreshedArtifacts);
      setDeliveryManifest(refreshedManifest);
      setSubtitleReviewEvidence(refreshedEvidence);
      setSubtitleReviewEvidenceError(null);
      setDeliveryManifestError(null);
      setSubtitleDraftError(null);
      setSubtitleDraftStatus(`Published ${published.artifacts.length} reviewed artifacts.`);
    } catch (publishError) {
      setSubtitleDraftError(toErrorMessage(publishError));
    } finally {
      setIsPublishingReviewedSubtitles(false);
    }
  }

  async function handleSaveNarrationWorkspace(segments: NarrationWorkspace['segments']) {
    if (!job) {
      return;
    }
    setIsSavingNarration(true);
    try {
      const updated = await linguaFrameApi.saveNarrationWorkspace(job.jobId, {
        segments: segments.map((segment, index) => ({
          index,
          startSeconds: Number(segment.startSeconds),
          endSeconds: Number(segment.endSeconds),
          text: segment.text,
          voice: segment.voice?.trim() || null
        }))
      });
      const evidence = await linguaFrameApi.getNarrationEvidence(job.jobId);
      setNarrationWorkspace(updated);
      setNarrationEvidence(evidence);
      setNarrationError(null);
      setNarrationStatus('Narration saved.');
    } catch (narrationSaveError) {
      setNarrationError(toErrorMessage(narrationSaveError));
    } finally {
      setIsSavingNarration(false);
    }
  }

  async function handleSaveNarrationMixSettings(settings: NarrationWorkspace['mixSettings']) {
    if (!job) {
      return;
    }
    setIsSavingNarration(true);
    try {
      const updated = await linguaFrameApi.updateNarrationMixSettings(job.jobId, {
        duckingVolume: Number(settings.duckingVolume),
        narrationVolume: Number(settings.narrationVolume),
        fadeDurationMs: Number(settings.fadeDurationMs)
      });
      const evidence = await linguaFrameApi.getNarrationEvidence(job.jobId);
      setNarrationWorkspace(updated);
      setNarrationEvidence(evidence);
      setNarrationError(null);
      setNarrationStatus('Mix settings saved.');
    } catch (mixSettingsError) {
      setNarrationError(toErrorMessage(mixSettingsError));
    } finally {
      setIsSavingNarration(false);
    }
  }

  async function handleClearNarrationWorkspace() {
    if (!job) {
      return;
    }
    setIsClearingNarration(true);
    try {
      const cleared = await linguaFrameApi.clearNarrationWorkspace(job.jobId);
      const evidence = await linguaFrameApi.getNarrationEvidence(job.jobId);
      setNarrationWorkspace(cleared);
      setNarrationEvidence(evidence);
      setNarrationError(null);
      setNarrationStatus('Narration cleared.');
    } catch (narrationClearError) {
      setNarrationError(toErrorMessage(narrationClearError));
    } finally {
      setIsClearingNarration(false);
    }
  }

  async function handleGenerateNarrationAudio() {
    if (!job) {
      return;
    }
    setIsGeneratingNarration(true);
    try {
      const generation = await linguaFrameApi.generateNarrationAudio(job.jobId);
      const [refreshedArtifacts, refreshedEvidence] = await Promise.all([
        linguaFrameApi.listArtifacts(job.jobId),
        linguaFrameApi.getNarrationEvidence(job.jobId)
      ]);
      setArtifacts(refreshedArtifacts);
      setNarrationEvidence(refreshedEvidence);
      setNarrationError(null);
      setNarrationStatus(`Generated ${generation.filename} as ${generation.audioLayout}.`);
    } catch (narrationGenerateError) {
      setNarrationError(toErrorMessage(narrationGenerateError));
    } finally {
      setIsGeneratingNarration(false);
    }
  }

  async function handleGenerateNarratedVideo() {
    if (!job) {
      return;
    }
    setIsGeneratingNarratedVideo(true);
    try {
      const generation = await linguaFrameApi.generateNarratedVideo(job.jobId);
      const [refreshedArtifacts, refreshedEvidence] = await Promise.all([
        linguaFrameApi.listArtifacts(job.jobId),
        linguaFrameApi.getNarrationEvidence(job.jobId)
      ]);
      setArtifacts(refreshedArtifacts);
      setNarrationEvidence(refreshedEvidence);
      setNarrationError(null);
      setNarrationStatus(
        `Generated ${generation.filename} from ${generation.baseVideoType} with ${generation.mixMode} (ducking ${generation.duckingVolume}, narration ${generation.narrationVolume}, fade ${generation.fadeDurationMs} ms).`
      );
    } catch (narratedVideoError) {
      setNarrationError(toErrorMessage(narratedVideoError));
    } finally {
      setIsGeneratingNarratedVideo(false);
    }
  }

  async function handleRefreshNarrationEvidence() {
    if (!job) {
      return;
    }
    try {
      const evidence = await linguaFrameApi.getNarrationEvidence(job.jobId);
      setNarrationEvidence(evidence);
      setNarrationError(null);
      setNarrationStatus('Narration evidence refreshed.');
    } catch (narrationEvidenceError) {
      setNarrationError(toErrorMessage(narrationEvidenceError));
    }
  }

  async function handleImportNarrationScriptPackage(request: ImportNarrationScriptPackageRequest) {
    if (!job) {
      return;
    }
    setIsSavingNarration(true);
    try {
      const result = await linguaFrameApi.importNarrationScriptPackage(job.jobId, request);
      const [refreshedWorkspace, refreshedEvidence, refreshedScriptPackage, refreshedArtifacts] = await Promise.all([
        linguaFrameApi.getNarrationWorkspace(job.jobId),
        linguaFrameApi.getNarrationEvidence(job.jobId),
        linguaFrameApi.getNarrationScriptPackage(job.jobId),
        linguaFrameApi.listArtifacts(job.jobId)
      ]);
      setNarrationWorkspace(refreshedWorkspace ?? result.workspace);
      setNarrationEvidence(refreshedEvidence);
      setNarrationScriptPackage(refreshedScriptPackage);
      setArtifacts(refreshedArtifacts);
      setNarrationError(null);
      setNarrationStatus(`Imported ${result.importedSegmentCount} ${result.importedSegmentCount === 1 ? 'segment' : 'segments'} from package.`);
    } catch (scriptPackageError) {
      setNarrationError(toErrorMessage(scriptPackageError));
    } finally {
      setIsSavingNarration(false);
    }
  }

  async function handleApplyNarrationDemoPreset(presetId: string) {
    if (!job) {
      return;
    }
    setIsSavingNarration(true);
    try {
      const result = await linguaFrameApi.applyNarrationDemoPreset(job.jobId, {
        presetId,
        replaceExisting: true
      });
      const [refreshedWorkspace, refreshedEvidence, refreshedScriptPackage, refreshedArtifacts] = await Promise.all([
        linguaFrameApi.getNarrationWorkspace(job.jobId),
        linguaFrameApi.getNarrationEvidence(job.jobId),
        linguaFrameApi.getNarrationScriptPackage(job.jobId),
        linguaFrameApi.listArtifacts(job.jobId)
      ]);
      setNarrationWorkspace(refreshedWorkspace ?? result.workspace);
      setNarrationEvidence(refreshedEvidence);
      setNarrationScriptPackage(refreshedScriptPackage ?? result.scriptPackage);
      setArtifacts(refreshedArtifacts);
      setNarrationError(null);
      setNarrationStatus(`Applied ${result.presetId} with ${result.importedSegmentCount} ${result.importedSegmentCount === 1 ? 'segment' : 'segments'}.`);
    } catch (presetApplyError) {
      setNarrationError(toErrorMessage(presetApplyError));
    } finally {
      setIsSavingNarration(false);
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

          <OpenAiReadinessEvidencePanel
            evidence={openAiReadinessEvidence}
            error={openAiReadinessEvidenceError}
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
                  setUploadExecutionPlan(null);
                  setUploadExecutionPlanError(null);
                  setUploadCostEstimate(null);
                  setUploadCostEstimateError(null);
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
                disabled={isUploading || isValidatingUpload || isEstimatingUploadCost || isEstimatingUploadExecutionPlan}
                onClick={(event) => void handleValidateUpload(event.currentTarget.form)}
              >
                {isValidatingUpload ? 'Validating...' : 'Validate file'}
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={isUploading || isValidatingUpload || isEstimatingUploadCost || isEstimatingUploadExecutionPlan}
                onClick={(event) => void handleEstimateUploadExecutionPlan(event.currentTarget.form)}
              >
                {isEstimatingUploadExecutionPlan ? 'Planning...' : 'Execution plan'}
              </button>
              <button
                type="button"
                className="secondary-button"
                disabled={isUploading || isValidatingUpload || isEstimatingUploadCost || isEstimatingUploadExecutionPlan}
                onClick={(event) => void handleEstimateUploadCost(event.currentTarget.form)}
              >
                {isEstimatingUploadCost ? 'Estimating...' : 'Estimate cost'}
              </button>
              <button
                type="submit"
                disabled={
                  isUploading ||
                  isValidatingUpload ||
                  isEstimatingUploadCost ||
                  isEstimatingUploadExecutionPlan ||
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
            <DemoSampleMediaCatalogPanel
              catalog={demoSampleMediaCatalog}
              error={demoSampleMediaCatalogError}
              isLoading={isLoadingDemoSampleMediaCatalog}
              onRefresh={() => void loadDemoSampleMediaCatalog()}
            />
            <DemoRunLauncherPanel
              launcher={demoRunLauncher}
              error={demoRunLauncherError}
              isLoading={isLoadingDemoRunLauncher}
              onRefresh={() => void loadDemoRunLauncher()}
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
            <UploadExecutionPlanPanel
              plan={uploadExecutionPlan}
              error={uploadExecutionPlanError}
              isLoading={isEstimatingUploadExecutionPlan}
              isDownloadingReport={isDownloadingUploadExecutionPlanReport}
              reportStatus={uploadExecutionPlanReportStatus}
              onRefresh={(form) => void handleEstimateUploadExecutionPlan(form)}
              onCopyReport={(plan) => void handleCopyUploadExecutionPlanReport(plan)}
              onDownloadReport={(form) => void handleDownloadUploadExecutionPlanReport(form)}
              onDownloadDecisionReport={(form) => void handleDownloadUploadDecisionPackageReport(form)}
              onDownloadDecisionZip={(form) => void handleDownloadUploadDecisionPackageZip(form)}
            />
            <UploadCostEstimatePanel
              estimate={uploadCostEstimate}
              error={uploadCostEstimateError}
              isLoading={isEstimatingUploadCost}
              onRefresh={(form) => void handleEstimateUploadCost(form)}
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

          <ModelUsageLedgerPanel
            ledger={modelUsageLedger}
            error={modelUsageLedgerError}
            isLoading={isLoadingModelUsageLedger}
            onRefresh={loadModelUsageLedger}
          />

          <DemoSessionCommandCenterPanel
            commandCenter={demoSessionCommandCenter}
            error={demoSessionCommandCenterError}
            isLoading={isLoadingDemoSessionCommandCenter}
            onRefresh={() => void loadDemoSessionCommandCenter(job?.jobId)}
          />

          <DemoPresentationCockpitPanel
            cockpit={demoPresentationCockpit}
            error={demoPresentationCockpitError}
            isLoading={isLoadingDemoPresentationCockpit}
            onRefresh={() => void loadDemoPresentationCockpit(job?.jobId)}
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
              isClearingNarration={isClearingNarration}
              isClearingSubtitleDraft={isClearingSubtitleDraft}
              isGeneratingNarration={isGeneratingNarration}
              isGeneratingNarratedVideo={isGeneratingNarratedVideo}
              isLoadingJob={isLoadingJob}
              isPublishingReviewedSubtitles={isPublishingReviewedSubtitles}
              isRetrying={isRetrying}
              isSavingNarration={isSavingNarration}
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
              demoReplayCard={demoReplayCard}
              demoReplayCardError={demoReplayCardError}
              demoCompletionCertificate={demoCompletionCertificate}
              demoCompletionCertificateError={demoCompletionCertificateError}
              demoAcceptanceGate={demoAcceptanceGate}
              demoAcceptanceGateError={demoAcceptanceGateError}
              demoRunVariance={demoRunVariance}
              demoRunVarianceError={demoRunVarianceError}
              demoEvidenceClosure={demoEvidenceClosure}
              demoEvidenceClosureError={demoEvidenceClosureError}
              demoRunSnapshot={demoRunSnapshot}
              demoRunSnapshotError={demoRunSnapshotError}
              demoPresenterPack={demoPresenterPack}
              demoPresenterPackError={demoPresenterPackError}
              demoShareSheet={demoShareSheet}
              demoShareSheetError={demoShareSheetError}
              openAiSmokeProof={openAiSmokeProof}
              openAiSmokeProofError={openAiSmokeProofError}
              demoReviewerWorkspace={demoReviewerWorkspace}
              demoReviewerWorkspaceError={demoReviewerWorkspaceError}
              demoHandoffPortal={demoHandoffPortal}
              demoHandoffPortalError={demoHandoffPortalError}
              isLoadingCacheReplayComparison={isLoadingCacheReplayComparison}
              isLoadingDemoComparison={isLoadingDemoComparison}
              isLoadingDemoRunMatrix={isLoadingDemoRunMatrix}
              isLoadingDemoRunMonitor={isLoadingDemoRunMonitor}
              isLoadingDemoReplayCard={isLoadingDemoReplayCard}
              isLoadingDemoCompletionCertificate={isLoadingDemoCompletionCertificate}
              isLoadingDemoAcceptanceGate={isLoadingDemoAcceptanceGate}
              isLoadingDemoRunVariance={isLoadingDemoRunVariance}
              isLoadingDemoEvidenceClosure={isLoadingDemoEvidenceClosure}
              isLoadingDemoRunSnapshot={isLoadingDemoRunSnapshot}
              isLoadingDemoPresenterPack={isLoadingDemoPresenterPack}
              isLoadingDemoShareSheet={isLoadingDemoShareSheet}
              isLoadingOpenAiSmokeProof={isLoadingOpenAiSmokeProof}
              isLoadingDemoReviewerWorkspace={isLoadingDemoReviewerWorkspace}
              isLoadingDemoHandoffPortal={isLoadingDemoHandoffPortal}
              onCancel={handleCancel}
              onClearNarrationWorkspace={handleClearNarrationWorkspace}
              onClearSubtitleDraft={handleClearSubtitleDraft}
              onGenerateNarrationAudio={handleGenerateNarrationAudio}
              onGenerateNarratedVideo={handleGenerateNarratedVideo}
              onPinCacheReplayBaseline={handlePinCacheReplayBaseline}
              onRefreshDemoRunMatrix={() => void loadDemoRunMatrix(job.jobId)}
              onRefreshDemoRunMonitor={() => void loadDemoRunMonitor(job.jobId)}
              onRefreshDemoReplayCard={() => void loadDemoReplayCard(job.jobId)}
              onRefreshDemoCompletionCertificate={() => void loadDemoCompletionCertificate(job.jobId)}
              onRefreshDemoAcceptanceGate={() => void loadDemoAcceptanceGate(job.jobId)}
              onRefreshDemoRunVariance={(preUploadJson) => void loadDemoRunVariance(job.jobId, preUploadJson)}
              onRefreshDemoEvidenceClosure={(preUploadJson) => void loadDemoEvidenceClosure(job.jobId, preUploadJson)}
              onRefreshDemoRunSnapshot={() => void loadDemoRunSnapshot(job.jobId)}
              onRefreshDemoPresenterPack={() => void loadDemoPresenterPack(job.jobId)}
              onRefreshDemoShareSheet={() => void loadDemoShareSheet(job.jobId)}
              onRefreshOpenAiSmokeProof={() => void loadOpenAiSmokeProof(job.jobId)}
              onRefreshDemoReviewerWorkspace={() => void loadDemoReviewerWorkspace(job.jobId)}
              onRefreshDemoHandoffPortal={() => void loadDemoHandoffPortal(job.jobId)}
              onRefreshNarrationEvidence={handleRefreshNarrationEvidence}
              onSelectCacheReplayComparison={handleSelectCacheReplayComparison}
              onSelectDemoComparison={handleSelectDemoComparison}
              onRetry={handleRetry}
              onPublishReviewedSubtitles={handlePublishReviewedSubtitles}
              onImportNarrationScriptPackage={handleImportNarrationScriptPackage}
              onApplyNarrationDemoPreset={handleApplyNarrationDemoPreset}
              onSaveNarrationMixSettings={handleSaveNarrationMixSettings}
              onSaveNarrationWorkspace={handleSaveNarrationWorkspace}
              onSaveSubtitleDraft={handleSaveSubtitleDraft}
              narrationError={narrationError}
              narrationEvidence={narrationEvidence}
              narrationScriptPackage={narrationScriptPackage}
              narrationDemoPresets={narrationDemoPresets}
              narrationStatus={narrationStatus}
              narrationWorkspace={narrationWorkspace}
              previewErrors={previewErrors}
              selectedLanguage={selectedLanguage}
              sourceMedia={sourceMedia}
              sourceMediaError={sourceMediaError}
              subtitleDraft={subtitleDraft}
              subtitleDraftError={subtitleDraftError}
              subtitleDraftStatus={subtitleDraftStatus}
              subtitleReview={subtitleReview}
              subtitleReviewEvidence={subtitleReviewEvidence}
              subtitleReviewEvidenceError={subtitleReviewEvidenceError}
              reviewedSubtitleWorkflow={reviewedSubtitleWorkflow}
              reviewedSubtitleWorkflowError={reviewedSubtitleWorkflowError}
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

function OpenAiReadinessEvidencePanel({
  evidence,
  error,
  isLoading,
  onRefresh
}: {
  evidence: OpenAiReadinessEvidence | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);

  const handleDownload = async () => {
    try {
      const blob = await linguaFrameApi.downloadOpenAiReadinessEvidenceMarkdown();
      downloadBlob(blob, 'openai-readiness-evidence.md');
      setStatus('OpenAI readiness evidence downloaded.');
    } catch (downloadError) {
      setStatus(toErrorMessage(downloadError));
    }
  };

  return (
    <section className="panel" aria-label="OpenAI readiness evidence">
      <div className="panel-heading">
        <h2>OpenAI readiness evidence</h2>
        {evidence ? (
          <span className={demoSessionStatusClassName(evidence.overallStatus)}>
            {evidence.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">OpenAI readiness unavailable: {error}</p> : null}
      {isLoading && !evidence ? <p className="muted">Checking OpenAI readiness...</p> : null}
      {evidence ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Phase</dt>
              <dd>{evidence.phase}</dd>
            </div>
            <div>
              <dt>Live check</dt>
              <dd>{evidence.liveCheck.status}</dd>
            </div>
            <div>
              <dt>Calls</dt>
              <dd>{evidence.modelUsage.modelCallCount} calls</dd>
            </div>
            <div>
              <dt>Failures</dt>
              <dd>
                {evidence.modelUsage.failedModelCallCount} failed · {evidence.modelUsage.failureRatePercent}%
              </dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatLedgerCost(evidence.modelUsage.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Latency</dt>
              <dd>{evidence.liveCheck.latencyMs} ms probe</dd>
            </div>
          </dl>
          <p className={evidence.overallStatus === 'BLOCKED' ? 'error-text' : 'muted'}>
            {evidence.recommendedNextAction}
          </p>
          <ul className="feature-list live-check-list" aria-label="OpenAI provider readiness">
            {evidence.providers.map((provider) => (
              <li key={provider.stage}>
                <span>{formatOpenAiStage(provider.stage)}</span>
                <span className={demoSessionStatusClassName(provider.status)}>{provider.status}</span>
                <small>
                  {provider.provider} · {provider.enabled ? 'enabled' : 'disabled'} ·{' '}
                  {provider.credentialsConfigured ? 'credentials configured' : 'credentials not configured'} ·{' '}
                  {provider.detail}
                </small>
              </li>
            ))}
          </ul>
          <h3>Readiness signals</h3>
          <ul className="operations-section-list">
            {evidence.readinessSignals.map((signal) => (
              <li key={signal.id}>
                <div className="operations-section-heading">
                  <strong>{signal.label}</strong>
                  <span className={demoSessionStatusClassName(signal.status)}>{signal.status}</span>
                </div>
                <p>{signal.detail}</p>
                <small>{signal.nextAction}</small>
              </li>
            ))}
          </ul>
          <h3>Commands</h3>
          <ul className="command-list">
            {evidence.commands.map((command) => (
              <li key={command.command}>
                <strong>{command.label}</strong>
                <code>{command.command}</code>
                <small>{command.description}</small>
              </li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" onClick={() => void handleDownload()}>
              Download readiness evidence
            </button>
          </div>
          {status ? <p className="muted">{status}</p> : null}
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

function DemoSampleMediaCatalogPanel({
  catalog,
  error,
  isLoading,
  onRefresh
}: {
  catalog: DemoSampleMediaCatalog | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const recommended = catalog?.items.find((item) => item.id === catalog.recommendedSampleId) ?? catalog?.items[0];
  return (
    <section className="upload-validation-panel" aria-label="Demo sample media">
      <div className="panel-heading">
        <h3>Demo sample media</h3>
        {catalog ? (
          <span className={readinessStatusClassName(catalog.overallStatus)}>
            {catalog.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !catalog ? <p className="muted">Loading sample media catalog...</p> : null}
      {catalog ? (
        <>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Recommended</dt>
              <dd>{catalog.recommendedSampleId}</dd>
            </div>
            <div>
              <dt>Duration limit</dt>
              <dd>{catalog.uploadDurationLimitSeconds} seconds</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(catalog.generatedAt)}</dd>
            </div>
          </dl>
          {recommended ? (
            <div className="evidence-preview">
              <h4>{recommended.title}</h4>
              <p>{recommended.recommendedUse}</p>
              <p>
                <a href={recommended.sourceUrl} target="_blank" rel="noreferrer">
                  {recommended.source}
                </a>
                {' - '}
                {recommended.attribution}
              </p>
              <p>{recommended.licenseGuidance}</p>
              <p>{recommended.durationGuidance}</p>
            </div>
          ) : null}
          <ul className="readiness-list upload-readiness-list" aria-label="Configured sample paths">
            {catalog.configuredPaths.map((path) => (
              <li key={path.envVar}>
                <span>{path.envVar}</span>
                <span className={readinessStatusClassName(path.status === 'CONFIGURED' ? 'READY' : 'ATTENTION')}>
                  {path.status}
                </span>
                <span>{path.filename || 'Not configured'}</span>
                <span>{path.sizeBytes === null ? path.message : `${formatBytes(path.sizeBytes)} - ${path.message}`}</span>
              </li>
            ))}
          </ul>
          <ul className="readiness-list" aria-label="Demo sample media commands">
            {catalog.commands.map((command) => (
              <li key={command.command}>
                <span>{command.label}</span>
                <code>{command.command}</code>
                <span>{command.description}</span>
              </li>
            ))}
          </ul>
          {catalog.items.length > 1 ? (
            <ul className="readiness-list" aria-label="Demo sample media sources">
              {catalog.items.slice(1).map((item) => (
                <li key={item.id}>
                  <span>{item.title}</span>
                  <span>{item.source}</span>
                  <span>{item.recommendedUse}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function DemoRunLauncherPanel({
  launcher,
  error,
  isLoading,
  onRefresh
}: {
  launcher: DemoRunLauncher | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  return (
    <section className="upload-validation-panel" aria-label="Demo run launcher">
      <div className="panel-heading">
        <h3>Demo run launcher</h3>
        {launcher ? (
          <span className={readinessStatusClassName(launcher.overallStatus)}>
            {launcher.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !launcher ? <p className="muted">Loading demo run launcher...</p> : null}
      {launcher ? (
        <>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Sample</dt>
              <dd>{launcher.recommendedSampleId}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(launcher.recommendedProfileId)}</dd>
            </div>
            <div>
              <dt>Next command</dt>
              <dd>
                <code>{launcher.recommendedNextCommand}</code>
              </dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(launcher.generatedAt)}</dd>
            </div>
          </dl>
          <ul className="readiness-list upload-readiness-list" aria-label="Demo run launcher gates">
            {launcher.gates.map((gate) => (
              <li key={gate.id}>
                <span>{gate.label}</span>
                <span className={readinessStatusClassName(gate.status)}>{gate.status}</span>
                <span>{gate.detail}</span>
                <span>{gate.nextAction}</span>
              </li>
            ))}
          </ul>
          <ul className="readiness-list" aria-label="Demo run launcher commands">
            {launcher.commands.map((command) => (
              <li key={command.command}>
                <span>{command.label}</span>
                <code>{command.command}</code>
                <span>{command.description}</span>
              </li>
            ))}
          </ul>
          <ul className="readiness-list" aria-label="Demo run launcher expected evidence">
            {launcher.expectedEvidence.map((evidence) => (
              <li key={evidence.path}>
                <span>{evidence.label}</span>
                <code>{evidence.path}</code>
                <span>{evidence.description}</span>
              </li>
            ))}
          </ul>
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

function UploadExecutionPlanPanel({
  plan,
  error,
  isLoading,
  isDownloadingReport,
  reportStatus,
  onRefresh,
  onCopyReport,
  onDownloadReport,
  onDownloadDecisionReport,
  onDownloadDecisionZip
}: {
  plan: UploadExecutionPlan | null;
  error: string | null;
  isLoading: boolean;
  isDownloadingReport: boolean;
  reportStatus: string | null;
  onRefresh: (form: HTMLFormElement | null) => void;
  onCopyReport: (plan: UploadExecutionPlan | null) => void;
  onDownloadReport: (form: HTMLFormElement | null) => void;
  onDownloadDecisionReport: (form: HTMLFormElement | null) => void;
  onDownloadDecisionZip: (form: HTMLFormElement | null) => void;
}) {
  if (!plan && !error && !isLoading) {
    return null;
  }

  const blockingGates = plan?.gates.filter((gate) => gate.blocking) ?? [];
  const visibleGates = blockingGates.length > 0 ? blockingGates : plan?.gates.slice(0, 5) ?? [];
  return (
    <section className="upload-validation-panel" aria-label="Upload execution plan">
      <div className="panel-heading">
        <h3>Execution plan</h3>
        {plan ? (
          <span className={readinessStatusClassName(plan.overallStatus)}>
            {plan.overallStatus}
          </span>
        ) : null}
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading}
          onClick={(event) => onRefresh(event.currentTarget.form)}
        >
          Refresh
        </button>
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading || !plan}
          onClick={() => onCopyReport(plan)}
        >
          Copy plan
        </button>
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading || isDownloadingReport || !plan}
          onClick={(event) => onDownloadReport(event.currentTarget.form)}
        >
          {isDownloadingReport ? 'Downloading...' : 'Download Markdown'}
        </button>
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading || isDownloadingReport || !plan}
          onClick={(event) => onDownloadDecisionReport(event.currentTarget.form)}
        >
          Decision report
        </button>
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading || isDownloadingReport || !plan}
          onClick={(event) => onDownloadDecisionZip(event.currentTarget.form)}
        >
          Decision ZIP
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {reportStatus ? <p className="muted validation-message">{reportStatus}</p> : null}
      {isLoading && !plan ? <p className="muted">Planning upload execution...</p> : null}
      {plan ? (
        <>
          <p className={plan.overallStatus === 'BLOCKED' ? 'error-text' : 'muted validation-message'}>
            {plan.recommendedNextAction}
          </p>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Estimated time</dt>
              <dd>
                {formatDurationSeconds(plan.estimatedDurationSecondsLower)} - {formatDurationSeconds(plan.estimatedDurationSecondsUpper)}
              </dd>
            </div>
            <div>
              <dt>Estimated cost</dt>
              <dd>{formatCost(plan.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Source duration</dt>
              <dd>{plan.durationSeconds === null ? 'Unknown' : formatDurationSeconds(plan.durationSeconds)}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(plan.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Style</dt>
              <dd>{plan.translationStyle} / {plan.subtitleStylePreset}</dd>
            </div>
            <div>
              <dt>Polishing</dt>
              <dd>{plan.subtitlePolishingMode}</dd>
            </div>
          </dl>
          {visibleGates.length > 0 ? (
            <ul className="readiness-list upload-readiness-list" aria-label="Upload execution plan gates">
              {visibleGates.map((gate) => (
                <li key={gate.id}>
                  <span>{gate.label}</span>
                  <span className={readinessStatusClassName(gate.status)}>{gate.status}</span>
                  <span>{gate.detail}</span>
                  <span>{gate.nextAction}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {plan.sourceReuseDecision ? (
            <div className="upload-validation-subsection source-reuse-decision-card" aria-label="Source reuse decision">
              <div className="panel-heading compact-panel-heading">
                <h4>Source reuse decision</h4>
                <span className={readinessStatusClassName(sourceReuseDecisionReadiness(plan.sourceReuseDecision.status))}>
                  {plan.sourceReuseDecision.status}
                </span>
              </div>
              <p className="muted validation-message">{plan.sourceReuseDecision.headline}</p>
              <p>{plan.sourceReuseDecision.summary}</p>
              <dl className="status-grid compact-status-grid upload-validation-grid">
                <div>
                  <dt>Source SHA-256</dt>
                  <dd><code>{formatShortHash(plan.sourceReuseDecision.sourceReuse.sourceContentSha256)}</code></dd>
                </div>
                <div>
                  <dt>Matches</dt>
                  <dd>{plan.sourceReuseDecision.candidateCount}</dd>
                </div>
                <div>
                  <dt>Recommended job</dt>
                  <dd>{plan.sourceReuseDecision.recommendedExistingJobId ?? 'None'}</dd>
                </div>
              </dl>
              {plan.sourceReuseDecision.links.length > 0 ? (
                <ul className="readiness-list source-reuse-link-list" aria-label="Source reuse decision links">
                  {plan.sourceReuseDecision.links.slice(0, 4).map((link) => (
                    <li key={`${link.kind}-${link.href}`}>
                      <span>{link.label}</span>
                      <a className="secondary-link" href={link.href}>
                        Open
                      </a>
                    </li>
                  ))}
                </ul>
              ) : null}
              {plan.sourceReuseDecision.actions.length > 0 ? (
                <ul className="readiness-list source-reuse-action-list" aria-label="Source reuse decision actions">
                  {plan.sourceReuseDecision.actions.slice(0, 3).map((action) => (
                    <li key={action.id}>
                      <span>{action.label}</span>
                      <span className={action.enabled ? 'status-pill success' : 'status-pill warning'}>{action.kind}</span>
                      <span>{action.detail}</span>
                    </li>
                  ))}
                </ul>
              ) : null}
              {plan.sourceReuseDecision.sourceReuse.candidates.length > 0 ? (
                <ul className="readiness-list" aria-label="Source reuse candidates">
                  {plan.sourceReuseDecision.sourceReuse.candidates.slice(0, 3).map((candidate) => (
                    <li key={candidate.jobId}>
                      <span>{candidate.originalFilename}</span>
                      <span className={readinessStatusClassName(sourceReuseJobReadiness(candidate.jobStatus))}>{candidate.jobStatus}</span>
                      <span>{formatDemoProfileId(candidate.demoProfileId)} / {candidate.translationStyle}</span>
                      <span>
                        <a className="secondary-link" href={candidate.jobDetailHref ?? `#job-${candidate.jobId}`}>
                          Open job
                        </a>
                        {' '}
                        <a className="secondary-link" href={candidate.shareSheetHref ?? linguaFrameApi.demoShareSheetMarkdownDownloadUrl(candidate.jobId)}>
                          Share sheet
                        </a>
                      </span>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ) : null}
          {plan.stages.length > 0 ? (
            <ul className="readiness-list" aria-label="Upload execution plan stages">
              {plan.stages.map((stage) => (
                <li key={stage.id}>
                  <span>{stage.label}</span>
                  <span>{stage.executionType}</span>
                  <span>
                    {formatDurationSeconds(stage.estimatedDurationSecondsLower)} - {formatDurationSeconds(stage.estimatedDurationSecondsUpper)}
                  </span>
                  <span>{stage.executionType === 'PAID' ? formatCost(stage.estimatedCostUsd) : stage.status}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {plan.commands.length > 0 ? (
            <ul className="readiness-list" aria-label="Upload execution plan commands">
              {plan.commands.map((command) => (
                <li key={command.id}>
                  <span>{command.label}</span>
                  <code>{command.command}</code>
                  <span>{command.description}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function UploadCostEstimatePanel({
  estimate,
  error,
  isLoading,
  onRefresh
}: {
  estimate: UploadCostEstimate | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: (form: HTMLFormElement | null) => void;
}) {
  if (!estimate && !error && !isLoading) {
    return null;
  }

  const paidStages = estimate?.stages.filter((stage) => stage.paidProviderCall) ?? [];
  return (
    <section className="upload-validation-panel" aria-label="Upload cost estimate">
      <div className="panel-heading">
        <h3>Cost estimate</h3>
        {estimate ? (
          <span className={readinessStatusClassName(estimate.overallStatus)}>
            {estimate.overallStatus}
          </span>
        ) : null}
        <button
          type="button"
          className="secondary-button"
          disabled={isLoading}
          onClick={(event) => onRefresh(event.currentTarget.form)}
        >
          Refresh
        </button>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {isLoading && !estimate ? <p className="muted">Estimating upload cost...</p> : null}
      {estimate ? (
        <>
          <p className={estimate.overallStatus === 'BLOCKED' ? 'error-text' : 'muted validation-message'}>
            {estimate.recommendedNextAction}
          </p>
          <dl className="status-grid compact-status-grid upload-validation-grid">
            <div>
              <dt>Estimate</dt>
              <dd>
                {formatCost(estimate.estimatedCostUsdLower)} - {formatCost(estimate.estimatedCostUsdUpper)}
              </dd>
            </div>
            <div>
              <dt>Point cost</dt>
              <dd>{formatCost(estimate.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Duration</dt>
              <dd>
                {estimate.durationSeconds === null ? 'Unknown' : `${estimate.durationSeconds} seconds`}
              </dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(estimate.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Glossary</dt>
              <dd>{estimate.translationGlossaryEntryCount} entries</dd>
            </div>
            <div>
              <dt>Polishing</dt>
              <dd>{estimate.subtitlePolishingMode}</dd>
            </div>
          </dl>
          {estimate.budgets.length > 0 ? (
            <ul className="readiness-list upload-readiness-list" aria-label="Upload cost budget checks">
              {estimate.budgets.map((budget) => (
                <li key={budget.id}>
                  <span>{budget.label}</span>
                  <span className={readinessStatusClassName(budget.status)}>{budget.status}</span>
                  <span>
                    {formatCost(budget.projectedUsd)}
                    {budget.enabled ? ` / ${formatCost(budget.limitUsd)}` : ' projected'}
                  </span>
                  <span>{budget.detail}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {paidStages.length > 0 ? (
            <ul className="readiness-list" aria-label="Upload cost paid provider stages">
              {paidStages.map((stage) => (
                <li key={stage.id}>
                  <span>{stage.label}</span>
                  <span>{stage.provider}</span>
                  <span>{formatCost(stage.estimatedCostUsd)}</span>
                  <span>{stage.basis}</span>
                </li>
              ))}
            </ul>
          ) : null}
          {estimate.cacheNotes.length > 0 ? (
            <ul className="muted-list" aria-label="Upload cost estimate notes">
              {estimate.cacheNotes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          ) : null}
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

function ModelUsageLedgerPanel({
  ledger,
  error,
  isLoading,
  onRefresh
}: {
  ledger: ModelUsageLedger | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = ledger ? formatModelUsageLedgerNotes(ledger) : '';

  const handleCopy = async () => {
    if (!ledger || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Model usage ledger copied.');
  };

  const handleDownload = async () => {
    try {
      const blob = await linguaFrameApi.downloadModelUsageLedgerMarkdown(ledger?.limit ?? 20);
      downloadBlob(blob, 'linguaframe-model-usage-ledger.md');
      setStatus('Model usage ledger Markdown downloaded.');
    } catch (downloadError) {
      setStatus(toErrorMessage(downloadError));
    }
  };

  return (
    <section className="panel model-usage-ledger-panel" aria-label="Model usage ledger">
      <div className="panel-heading">
        <h2>Model usage ledger</h2>
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Model usage ledger unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !ledger ? <p className="muted">Loading model usage ledger...</p> : null}
      {ledger ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Status</dt>
              <dd>
                <span className={modelUsageStatusClassName(ledger.summary.ledgerStatus)}>
                  {ledger.summary.ledgerStatus}
                </span>
              </dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatLedgerCost(ledger.summary.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Calls</dt>
              <dd>{ledger.summary.modelCallCount} calls</dd>
            </div>
            <div>
              <dt>Failures</dt>
              <dd>
                {ledger.summary.failedModelCallCount} failed · {ledger.summary.failureRatePercent}%
              </dd>
            </div>
            <div>
              <dt>Latency</dt>
              <dd>{formatDurationMs(ledger.summary.averageLatencyMs)} avg</dd>
            </div>
            <div>
              <dt>Cache</dt>
              <dd>{ledger.summary.providerCacheHitCount} provider hits</dd>
            </div>
          </dl>
          <p className={ledger.summary.ledgerStatus === 'BLOCKED' ? 'error-text' : 'muted'}>
            {ledger.summary.recommendedNextAction}
          </p>
          {ledger.operations.length > 0 ? (
            <>
              <h3>Operations</h3>
              <ul className="readiness-list" aria-label="Model usage operations">
                {ledger.operations.slice(0, 5).map((operation) => (
                  <li key={`${operation.operation}-${operation.provider}-${operation.model}-${operation.promptVersion}`}>
                    <span>{operation.operation}</span>
                    <span>{operation.provider} / {operation.model}</span>
                    <span>{operation.modelCallCount} calls / {operation.failedModelCallCount} failed</span>
                    <span>{formatLedgerCost(operation.estimatedCostUsd)}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
          {ledger.jobs.length > 0 ? (
            <>
              <h3>Jobs</h3>
              <ul className="recent-list" aria-label="Model usage jobs">
                {ledger.jobs.slice(0, 4).map((job) => (
                  <li key={job.jobId}>
                    <span>{job.jobId}</span>
                    <span className="history-meta">
                      {job.jobStatus} · {job.modelCallCount} calls · {formatLedgerCost(job.estimatedCostUsd)}
                    </span>
                    <small>
                      {job.targetLanguage} · cache {job.providerCacheHitCount} · generated {job.generatedArtifactCount}
                    </small>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted">No model-call evidence is available yet.</p>
          )}
          {ledger.recentCalls.length > 0 ? (
            <>
              <h3>Recent calls</h3>
              <ul className="recent-list" aria-label="Recent model calls">
                {ledger.recentCalls.slice(0, 4).map((call) => (
                  <li key={call.modelCallId}>
                    <span>{call.operation}</span>
                    <span className="history-meta">
                      {call.status} · {call.provider} / {call.model} · {formatDurationMs(call.latencyMs)}
                    </span>
                    <small>{call.safeErrorSummary ?? call.jobId}</small>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy ledger
            </button>
            <button type="button" className="secondary-button" onClick={() => void handleDownload()}>
              Download ledger
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function DemoSessionCommandCenterPanel({
  commandCenter,
  error,
  isLoading,
  onRefresh
}: {
  commandCenter: DemoSessionCommandCenter | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = commandCenter ? formatDemoSessionCommandCenterNotes(commandCenter) : '';
  const focusRun = commandCenter?.focusRun ?? commandCenter?.activeRun ?? commandCenter?.recommendedCompletedRun ?? null;
  const primaryAction = commandCenter?.actions.find((action) => action.primary) ?? commandCenter?.actions[0] ?? null;

  const handleCopyNotes = async () => {
    if (!commandCenter || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Command center copied.');
  };

  const handleCopyCommand = async () => {
    if (!primaryAction || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(primaryAction.command);
    setStatus('Primary command copied.');
  };

  const handleDownload = async () => {
    try {
      const blob = await linguaFrameApi.downloadDemoSessionCommandCenterMarkdown(focusRun?.jobId);
      downloadBlob(blob, 'linguaframe-demo-session-command-center.md');
      setStatus('Command center Markdown downloaded.');
    } catch (downloadError) {
      setStatus(toErrorMessage(downloadError));
    }
  };

  const handleDownloadPackage = async () => {
    try {
      const blob = await linguaFrameApi.downloadDemoSessionEvidencePackageZip(focusRun?.jobId);
      downloadBlob(blob, 'linguaframe-demo-session-evidence-package.zip');
      setStatus('Demo session evidence package downloaded.');
    } catch (downloadError) {
      setStatus(toErrorMessage(downloadError));
    }
  };

  return (
    <section className="panel private-demo-operations-panel" aria-label="Demo session command center">
      <div className="panel-heading">
        <h2>Demo session command center</h2>
        {commandCenter ? (
          <span className={demoSessionStatusClassName(commandCenter.overallStatus)}>
            {commandCenter.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Demo session command center unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !commandCenter ? <p className="muted">Loading demo session command center...</p> : null}
      {commandCenter ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Phase</dt>
              <dd>{commandCenter.phase}</dd>
            </div>
            <div>
              <dt>Focus run</dt>
              <dd>{focusRun?.jobId ?? 'None'}</dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatLedgerCost(commandCenter.estimatedCostUsd)}</dd>
            </div>
            <div>
              <dt>Calls</dt>
              <dd>{commandCenter.modelCallCount} calls</dd>
            </div>
            <div>
              <dt>Failures</dt>
              <dd>
                {commandCenter.failedModelCallCount} failed · {commandCenter.failureRatePercent}%
              </dd>
            </div>
            <div>
              <dt>Latency</dt>
              <dd>{formatDurationMs(commandCenter.averageLatencyMs)} avg</dd>
            </div>
          </dl>
          <p className={commandCenter.overallStatus === 'BLOCKED' ? 'error-text' : 'muted'}>
            {commandCenter.recommendedNextAction}
          </p>
          {focusRun ? (
            <div className="evidence-gallery-recommended">
              <h3>Run focus</h3>
              <p>
                <strong>{focusRun.jobId}</strong> · {focusRun.status} · {focusRun.profileId}
              </p>
              <ul className="inline-evidence-list">
                <li>{focusRun.role}</li>
                <li>{focusRun.acceptanceStatus}</li>
                <li>{focusRun.readiness}</li>
                <li>{focusRun.currentStage ?? 'No active stage'}</li>
                <li>{focusRun.elapsedMs === null ? 'No elapsed time' : formatDurationMs(focusRun.elapsedMs)}</li>
              </ul>
              <small>{focusRun.nextAction}</small>
            </div>
          ) : (
            <p className="muted">No selected, active, or completed run is available yet.</p>
          )}
          {primaryAction ? (
            <div className="command-highlight">
              <h3>Primary command</h3>
              <code>{primaryAction.command}</code>
              <small>{primaryAction.description}</small>
            </div>
          ) : null}
          <ul className="operations-section-list" aria-label="Demo session phases">
            {commandCenter.phases.map((phase) => (
              <li key={phase.id}>
                <div className="operations-section-heading">
                  <strong>{phase.label}</strong>
                  <span className={demoSessionStatusClassName(phase.status)}>{phase.status}</span>
                </div>
                <p>{phase.detail}</p>
                <small>{phase.nextAction}</small>
                {phase.blocking ? <small className="error-text">Blocking demo session readiness.</small> : null}
              </li>
            ))}
          </ul>
          {commandCenter.actions.length > 0 ? (
            <>
              <h3>Actions</h3>
              <ul className="command-list">
                {commandCenter.actions.map((action) => (
                  <li key={action.id}>
                    <strong>{action.label}</strong>
                    <code>{action.command}</code>
                    <small>{action.description}</small>
                  </li>
                ))}
              </ul>
            </>
          ) : null}
          <h3>Evidence links</h3>
          <ul className="link-list">
            {commandCenter.evidenceLinks.slice(0, 12).map((link) => (
              <li key={`${link.label}-${link.href}`}>
                <a href={link.href}>{link.label}</a>
                <small>{link.contentType} · {link.description}</small>
              </li>
            ))}
          </ul>
          <h3>Safety</h3>
          <ul className="compact-list">
            {commandCenter.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopyNotes}>
              Copy command center
            </button>
            <button type="button" className="secondary-button" disabled={!primaryAction || !canCopy} onClick={handleCopyCommand}>
              Copy command
            </button>
            <button type="button" className="secondary-button" onClick={() => void handleDownload()}>
              Download command center
            </button>
            <button type="button" className="secondary-button" onClick={() => void handleDownloadPackage()}>
              Download session package
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
        </>
      ) : null}
    </section>
  );
}

function DemoPresentationCockpitPanel({
  cockpit,
  error,
  isLoading,
  onRefresh
}: {
  cockpit: DemoPresentationCockpit | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const canCopy = typeof navigator !== 'undefined' && Boolean(navigator.clipboard?.writeText);
  const notes = cockpit ? formatDemoPresentationCockpitNotes(cockpit) : '';
  const focusRun = cockpit?.selectedRun ?? cockpit?.activeRun ?? cockpit?.recommendedRun ?? null;

  const handleCopy = async () => {
    if (!cockpit || !canCopy) {
      setStatus('Clipboard copy is unavailable in this browser.');
      return;
    }
    await navigator.clipboard.writeText(notes);
    setStatus('Presentation cockpit notes copied.');
  };

  const handleDownload = () => {
    if (!cockpit) {
      return;
    }
    const blob = new Blob([notes], { type: 'text/markdown;charset=utf-8' });
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = 'linguaframe-demo-presentation-cockpit.md';
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus('Presentation cockpit notes downloaded.');
  };

  return (
    <section className="panel private-demo-operations-panel" aria-label="Demo presentation cockpit">
      <div className="panel-heading">
        <h2>Demo presentation cockpit</h2>
        {cockpit ? (
          <span className={operationsStatusClassName(cockpit.overallStatus)}>
            {cockpit.overallStatus}
          </span>
        ) : null}
        <button type="button" className="secondary-button" disabled={isLoading} onClick={onRefresh}>
          Refresh
        </button>
      </div>
      {error ? (
        <>
          <p className="error-text">Presentation cockpit unavailable</p>
          <p className="muted">{error}</p>
        </>
      ) : null}
      {isLoading && !cockpit ? <p className="muted">Loading presentation cockpit...</p> : null}
      {cockpit ? (
        <>
          <dl className="status-grid compact-status-grid operations-summary-grid">
            <div>
              <dt>Phase</dt>
              <dd>{cockpit.phase}</dd>
            </div>
            <div>
              <dt>Next action</dt>
              <dd>{cockpit.recommendedNextAction}</dd>
            </div>
            <div>
              <dt>Focus run</dt>
              <dd>{focusRun?.jobId ?? 'None'}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatIsoDateTime(cockpit.generatedAt)}</dd>
            </div>
          </dl>
          {focusRun ? (
            <div className="evidence-gallery-recommended">
              <h3>Run focus</h3>
              <p>
                <strong>{focusRun.jobId}</strong> · {focusRun.status} · {focusRun.profileId}
              </p>
              <ul className="inline-evidence-list">
                <li>{focusRun.acceptanceStatus}</li>
                <li>{focusRun.readiness}</li>
                <li>{focusRun.attentionLevel}</li>
                <li>{focusRun.currentStage}</li>
                <li>{focusRun.elapsedMs === null ? 'No elapsed time' : formatDurationMs(focusRun.elapsedMs)}</li>
              </ul>
              <small>{focusRun.nextAction}</small>
            </div>
          ) : (
            <p className="muted">No selected, active, or completed demo run is available yet.</p>
          )}
          <ul className="operations-section-list" aria-label="Demo presentation cockpit checks">
            {cockpit.checks.map((check) => (
              <li key={check.key}>
                <div className="operations-section-heading">
                  <strong>{check.label}</strong>
                  <span className={operationsStatusClassName(check.status)}>{check.status}</span>
                </div>
                <p>{check.detail}</p>
                <small>{check.nextAction}</small>
                {check.blocking ? <small className="error-text">Blocking presentation readiness.</small> : null}
              </li>
            ))}
          </ul>
          <h3>Links</h3>
          <ul className="link-list">
            {cockpit.links.map((link) => (
              <li key={`${link.kind}-${link.url}`}>
                <a href={link.url}>{link.label}</a>
                <small>{link.kind}</small>
              </li>
            ))}
          </ul>
          <h3>Safety</h3>
          <ul className="compact-list">
            {cockpit.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="panel-actions">
            <button type="button" className="secondary-button" disabled={!canCopy} onClick={handleCopy}>
              Copy cockpit notes
            </button>
            <button type="button" className="secondary-button" onClick={handleDownload}>
              Download cockpit notes
            </button>
          </div>
          {!canCopy ? <p className="muted">Clipboard copy is unavailable in this browser.</p> : null}
          {status ? <p className="muted">{status}</p> : null}
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

function formatDemoPresentationCockpitNotes(cockpit: DemoPresentationCockpit): string {
  const focusRun = cockpit.selectedRun ?? cockpit.activeRun ?? cockpit.recommendedRun ?? null;
  const lines = [
    '# LinguaFrame Demo Presentation Cockpit',
    '',
    `- Overall: ${safeMarkdownLine(cockpit.overallStatus)}`,
    `- Phase: ${safeMarkdownLine(cockpit.phase)}`,
    `- Generated at: ${safeMarkdownLine(cockpit.generatedAt)}`,
    `- Next action: ${safeMarkdownLine(cockpit.recommendedNextAction)}`,
    `- Focus job: ${focusRun ? safeMarkdownLine(focusRun.jobId) : 'none'}`,
    '',
    '## Focus Run'
  ];
  if (focusRun) {
    lines.push(`- Job: ${safeMarkdownLine(focusRun.jobId)}`);
    lines.push(`- Video: ${safeMarkdownLine(focusRun.videoId)}`);
    lines.push(`- Status: ${safeMarkdownLine(focusRun.status)}`);
    lines.push(`- Acceptance: ${safeMarkdownLine(focusRun.acceptanceStatus)}`);
    lines.push(`- Stage: ${safeMarkdownLine(focusRun.currentStage)}`);
    lines.push(`- Next: ${safeMarkdownLine(focusRun.nextAction)}`);
  } else {
    lines.push('- No selected, active, or completed run is available.');
  }
  lines.push('', '## Checks');
  cockpit.checks.forEach((check) => {
    lines.push(`- ${safeMarkdownLine(check.status)} ${safeMarkdownLine(check.key)}: ${safeMarkdownLine(check.detail)}`);
    lines.push(`  Next: ${safeMarkdownLine(check.nextAction)}`);
  });
  lines.push('', '## Links');
  cockpit.links.forEach((link) => {
    lines.push(`- ${safeMarkdownLine(link.label)}: ${safeMarkdownLine(link.url)}`);
  });
  lines.push('', '## Safety');
  cockpit.safetyNotes.forEach((note) => {
    lines.push(`- ${safeMarkdownLine(note)}`);
  });
  return `${lines.join('\n')}\n`;
}

function formatDemoSessionCommandCenterNotes(commandCenter: DemoSessionCommandCenter): string {
  const focusRun = commandCenter.focusRun ?? commandCenter.activeRun ?? commandCenter.recommendedCompletedRun ?? null;
  const lines = [
    '# LinguaFrame Demo Session Command Center',
    '',
    `- Overall: ${safeMarkdownLine(commandCenter.overallStatus)}`,
    `- Phase: ${safeMarkdownLine(commandCenter.phase)}`,
    `- Generated at: ${safeMarkdownLine(commandCenter.generatedAt)}`,
    `- Next action: ${safeMarkdownLine(commandCenter.recommendedNextAction)}`,
    `- Primary command: ${safeMarkdownLine(commandCenter.primaryCommand)}`,
    `- Focus job: ${focusRun ? safeMarkdownLine(focusRun.jobId) : 'none'}`,
    `- Estimated cost: ${safeMarkdownLine(formatLedgerCost(commandCenter.estimatedCostUsd))}`,
    `- Calls: ${commandCenter.modelCallCount}`,
    `- Failed calls: ${commandCenter.failedModelCallCount}`,
    `- Failure rate: ${safeMarkdownLine(commandCenter.failureRatePercent)}%`,
    '',
    '## Focus Run'
  ];
  if (focusRun) {
    lines.push(`- Role: ${safeMarkdownLine(focusRun.role)}`);
    lines.push(`- Job: ${safeMarkdownLine(focusRun.jobId)}`);
    lines.push(`- Video: ${safeMarkdownLine(focusRun.videoId)}`);
    lines.push(`- Profile: ${safeMarkdownLine(focusRun.profileId)}`);
    lines.push(`- Status: ${safeMarkdownLine(focusRun.status)}`);
    lines.push(`- Acceptance: ${safeMarkdownLine(focusRun.acceptanceStatus)}`);
    lines.push(`- Next: ${safeMarkdownLine(focusRun.nextAction)}`);
  } else {
    lines.push('- No selected, active, or completed run is available.');
  }
  lines.push('', '## Phases');
  commandCenter.phases.forEach((phase) => {
    lines.push(`- ${safeMarkdownLine(phase.status)} ${safeMarkdownLine(phase.label)}: ${safeMarkdownLine(phase.detail)}`);
    lines.push(`  Next: ${safeMarkdownLine(phase.nextAction)}`);
  });
  lines.push('', '## Actions');
  commandCenter.actions.forEach((action) => {
    lines.push(`- ${safeMarkdownLine(action.label)}: ${safeMarkdownLine(action.command)}`);
  });
  lines.push('', '## Evidence');
  commandCenter.evidenceLinks.forEach((link) => {
    lines.push(`- ${safeMarkdownLine(link.label)}: ${safeMarkdownLine(link.href)}`);
  });
  lines.push('', '## Safety');
  commandCenter.safetyNotes.forEach((note) => {
    lines.push(`- ${safeMarkdownLine(note)}`);
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

function formatModelUsageLedgerNotes(ledger: ModelUsageLedger): string {
  const lines = [
    '# LinguaFrame Model Usage Ledger',
    '',
    `- Status: ${safeMarkdownLine(ledger.summary.ledgerStatus)}`,
    `- Generated at: ${safeMarkdownLine(ledger.generatedAt)}`,
    `- Owner scope: ${safeMarkdownLine(ledger.ownershipScope)}`,
    `- Jobs: ${ledger.summary.jobCount}`,
    `- Calls: ${ledger.summary.modelCallCount}`,
    `- Failed calls: ${ledger.summary.failedModelCallCount}`,
    `- Failure rate: ${safeMarkdownLine(ledger.summary.failureRatePercent)}%`,
    `- Estimated cost: ${safeMarkdownLine(formatLedgerCost(ledger.summary.estimatedCostUsd))}`,
    `- Next action: ${safeMarkdownLine(ledger.summary.recommendedNextAction)}`,
    '',
    '## Jobs'
  ];
  if (ledger.jobs.length === 0) {
    lines.push('- No model-call evidence is available yet.');
  } else {
    ledger.jobs.forEach((job) => {
      lines.push(`- ${safeMarkdownLine(job.jobId)}: ${safeMarkdownLine(job.jobStatus)}`);
      lines.push(`  Calls: ${job.modelCallCount}, failed: ${job.failedModelCallCount}, cost: ${formatLedgerCost(job.estimatedCostUsd)}`);
      job.safeLinks.forEach((link) => {
        lines.push(`  Link: ${safeMarkdownLine(link)}`);
      });
    });
  }
  lines.push('', '## Operations');
  ledger.operations.forEach((operation) => {
    lines.push(
      `- ${safeMarkdownLine(operation.operation)} ${safeMarkdownLine(operation.provider)}/${safeMarkdownLine(operation.model)}: ${operation.modelCallCount} calls, ${operation.failedModelCallCount} failed, ${formatLedgerCost(operation.estimatedCostUsd)}`
    );
  });
  lines.push('', '## Safety');
  ledger.safetyNotes.forEach((note) => {
    lines.push(`- ${safeMarkdownLine(note)}`);
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

function modelUsageStatusClassName(status: ModelUsageLedger['summary']['ledgerStatus']): string {
  if (status === 'BLOCKED') {
    return 'status-pill danger';
  }
  if (status === 'ATTENTION') {
    return 'status-pill warning';
  }
  if (status === 'EMPTY') {
    return 'status-pill muted-pill';
  }
  return 'status-pill';
}

function demoSessionStatusClassName(status: string): string {
  if (status === 'BLOCKED') {
    return 'status-pill danger';
  }
  if (status === 'ATTENTION') {
    return 'status-pill warning';
  }
  if (status === 'EMPTY' || status === 'SKIPPED') {
    return 'status-pill muted-pill';
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
  isClearingNarration,
  isClearingSubtitleDraft,
  isGeneratingNarration,
  isLoadingJob,
  isPublishingReviewedSubtitles,
  isRetrying,
  isSavingNarration,
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
  demoReplayCard,
  demoReplayCardError,
  demoCompletionCertificate,
  demoCompletionCertificateError,
  demoAcceptanceGate,
  demoAcceptanceGateError,
  demoRunVariance,
  demoRunVarianceError,
  demoEvidenceClosure,
  demoEvidenceClosureError,
  demoRunSnapshot,
  demoRunSnapshotError,
  demoPresenterPack,
  demoPresenterPackError,
  demoShareSheet,
  demoShareSheetError,
  demoReviewerWorkspace,
  demoReviewerWorkspaceError,
  demoHandoffPortal,
  demoHandoffPortalError,
  isLoadingCacheReplayComparison,
  isLoadingDemoComparison,
  isLoadingDemoRunMatrix,
  isLoadingDemoRunMonitor,
  isLoadingDemoReplayCard,
  isLoadingDemoCompletionCertificate,
  isLoadingDemoAcceptanceGate,
  isLoadingDemoRunVariance,
  isLoadingDemoEvidenceClosure,
  isLoadingDemoRunSnapshot,
  isLoadingDemoPresenterPack,
  isLoadingDemoShareSheet,
  isLoadingOpenAiSmokeProof,
  isLoadingDemoReviewerWorkspace,
  isLoadingDemoHandoffPortal,
  isGeneratingNarratedVideo,
  onCancel,
  onClearNarrationWorkspace,
  onClearSubtitleDraft,
  onGenerateNarrationAudio,
  onGenerateNarratedVideo,
  onPinCacheReplayBaseline,
  onRefreshDemoRunMatrix,
  onRefreshDemoRunMonitor,
  onRefreshDemoReplayCard,
  onRefreshDemoCompletionCertificate,
  onRefreshDemoAcceptanceGate,
  onRefreshDemoRunVariance,
  onRefreshDemoEvidenceClosure,
  onRefreshDemoRunSnapshot,
  onRefreshDemoPresenterPack,
  onRefreshDemoShareSheet,
  onRefreshOpenAiSmokeProof,
  onRefreshDemoReviewerWorkspace,
  onRefreshDemoHandoffPortal,
  onRefreshNarrationEvidence,
  onSelectCacheReplayComparison,
  onSelectDemoComparison,
  onRetry,
  onApplyNarrationDemoPreset,
  onImportNarrationScriptPackage,
  onPublishReviewedSubtitles,
  onSaveNarrationMixSettings,
  onSaveNarrationWorkspace,
  onSaveSubtitleDraft,
  narrationError,
  narrationEvidence,
  narrationDemoPresets,
  narrationScriptPackage,
  narrationStatus,
  narrationWorkspace,
  previewErrors,
  selectedLanguage,
  sourceMedia,
  sourceMediaError,
  subtitleDraft,
  subtitleDraftError,
  subtitleDraftStatus,
  subtitleReview,
  subtitleReviewEvidence,
  subtitleReviewEvidenceError,
  reviewedSubtitleWorkflow,
  reviewedSubtitleWorkflowError,
  openAiSmokeProof,
  openAiSmokeProofError,
  subtitles,
  transcript
}: {
  canCancel: boolean;
  canRetry: boolean;
  isCancelling: boolean;
  isClearingNarration: boolean;
  isClearingSubtitleDraft: boolean;
  isGeneratingNarration: boolean;
  isGeneratingNarratedVideo: boolean;
  isLoadingJob: boolean;
  isPublishingReviewedSubtitles: boolean;
  isRetrying: boolean;
  isSavingNarration: boolean;
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
  demoReplayCard: DemoReplayCard | null;
  demoReplayCardError: string | null;
  demoCompletionCertificate: DemoCompletionCertificate | null;
  demoCompletionCertificateError: string | null;
  demoAcceptanceGate: DemoAcceptanceGate | null;
  demoAcceptanceGateError: string | null;
  demoRunVariance: DemoRunVarianceReport | null;
  demoRunVarianceError: string | null;
  demoEvidenceClosure: DemoEvidenceClosurePackage | null;
  demoEvidenceClosureError: string | null;
  demoRunSnapshot: DemoRunSnapshot | null;
  demoRunSnapshotError: string | null;
  demoPresenterPack: DemoPresenterPack | null;
  demoPresenterPackError: string | null;
  demoShareSheet: DemoShareSheet | null;
  demoShareSheetError: string | null;
  openAiSmokeProof: OpenAiSmokeProof | null;
  openAiSmokeProofError: string | null;
  demoReviewerWorkspace: DemoReviewerWorkspace | null;
  demoReviewerWorkspaceError: string | null;
  demoHandoffPortal: DemoHandoffPortal | null;
  demoHandoffPortalError: string | null;
  isLoadingCacheReplayComparison: boolean;
  isLoadingDemoComparison: boolean;
  isLoadingDemoRunMatrix: boolean;
  isLoadingDemoRunMonitor: boolean;
  isLoadingDemoReplayCard: boolean;
  isLoadingDemoCompletionCertificate: boolean;
  isLoadingDemoAcceptanceGate: boolean;
  isLoadingDemoRunVariance: boolean;
  isLoadingDemoEvidenceClosure: boolean;
  isLoadingDemoRunSnapshot: boolean;
  isLoadingDemoPresenterPack: boolean;
  isLoadingDemoShareSheet: boolean;
  isLoadingOpenAiSmokeProof: boolean;
  isLoadingDemoReviewerWorkspace: boolean;
  isLoadingDemoHandoffPortal: boolean;
  onCancel: () => void;
  onClearNarrationWorkspace: () => void;
  onClearSubtitleDraft: () => void;
  onGenerateNarrationAudio: () => void;
  onGenerateNarratedVideo: () => void;
  onPinCacheReplayBaseline: () => void;
  onRefreshDemoRunMatrix: () => void;
  onRefreshDemoRunMonitor: () => void;
  onRefreshDemoReplayCard: () => void;
  onRefreshDemoCompletionCertificate: () => void;
  onRefreshDemoAcceptanceGate: () => void;
  onRefreshDemoRunVariance: (preUploadJson: string) => void;
  onRefreshDemoEvidenceClosure: (preUploadJson: string) => void;
  onRefreshDemoRunSnapshot: () => void;
  onRefreshDemoPresenterPack: () => void;
  onRefreshDemoShareSheet: () => void;
  onRefreshOpenAiSmokeProof: () => void;
  onRefreshDemoReviewerWorkspace: () => void;
  onRefreshDemoHandoffPortal: () => void;
  onRefreshNarrationEvidence: () => void;
  onSelectCacheReplayComparison: (jobId: string) => void;
  onSelectDemoComparison: (jobId: string) => void;
  onRetry: () => void;
  onApplyNarrationDemoPreset: (presetId: string) => void;
  onImportNarrationScriptPackage: (request: ImportNarrationScriptPackageRequest) => void;
  onPublishReviewedSubtitles: (includeBurnedVideo: boolean, releaseNotes: string) => void;
  onSaveNarrationMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
  onSaveNarrationWorkspace: (segments: NarrationWorkspace['segments']) => void;
  onSaveSubtitleDraft: (segments: Array<{
    index: number;
    text: string;
    decision: SubtitleReviewDecision;
    issueCategories: SubtitleReviewIssueCategory[];
    reviewerNote: string | null;
  }>) => void;
  narrationError: string | null;
  narrationEvidence: NarrationEvidence | null;
  narrationDemoPresets: NarrationDemoPreset[];
  narrationScriptPackage: NarrationScriptPackage | null;
  narrationStatus: string | null;
  narrationWorkspace: NarrationWorkspace | null;
  previewErrors: string[];
  selectedLanguage: string;
  sourceMedia: MediaUploadDetail | null;
  sourceMediaError: string | null;
  subtitleDraft: SubtitleDraftSummary | null;
  subtitleDraftError: string | null;
  subtitleDraftStatus: string | null;
  subtitleReview: SubtitleReviewSummary | null;
  subtitleReviewEvidence: SubtitleReviewEvidence | null;
  subtitleReviewEvidenceError: string | null;
  reviewedSubtitleWorkflow: ReviewedSubtitleWorkflow | null;
  reviewedSubtitleWorkflowError: string | null;
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

      <DemoReviewerWorkspacePanel
        error={demoReviewerWorkspaceError}
        isLoading={isLoadingDemoReviewerWorkspace}
        jobId={job.jobId}
        onRefresh={onRefreshDemoReviewerWorkspace}
        workspace={demoReviewerWorkspace}
      />

      <DemoHandoffPortalPanel
        error={demoHandoffPortalError}
        isLoading={isLoadingDemoHandoffPortal}
        jobId={job.jobId}
        onRefresh={onRefreshDemoHandoffPortal}
        portal={demoHandoffPortal}
      />

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

      <DemoAcceptanceGatePanel
        error={demoAcceptanceGateError}
        gate={demoAcceptanceGate}
        isLoading={isLoadingDemoAcceptanceGate}
        onRefresh={onRefreshDemoAcceptanceGate}
      />

      <DemoCompletionCertificatePanel
        certificate={demoCompletionCertificate}
        error={demoCompletionCertificateError}
        isLoading={isLoadingDemoCompletionCertificate}
        onRefresh={onRefreshDemoCompletionCertificate}
      />

      <DemoRunVariancePanel
        error={demoRunVarianceError}
        isLoading={isLoadingDemoRunVariance}
        onRefresh={onRefreshDemoRunVariance}
        report={demoRunVariance}
      />

      <DemoEvidenceClosurePanel
        closure={demoEvidenceClosure}
        error={demoEvidenceClosureError}
        isLoading={isLoadingDemoEvidenceClosure}
        onRefresh={onRefreshDemoEvidenceClosure}
      />

      <DemoReplayCardPanel
        card={demoReplayCard}
        error={demoReplayCardError}
        isLoading={isLoadingDemoReplayCard}
        onRefresh={onRefreshDemoReplayCard}
      />

      <DemoEvidencePanel evidence={demoEvidence} markdown={demoEvidenceMarkdown} />

      <OpenAiSmokeProofPanel
        error={openAiSmokeProofError}
        isLoading={isLoadingOpenAiSmokeProof}
        jobId={job.jobId}
        onRefresh={onRefreshOpenAiSmokeProof}
        proof={openAiSmokeProof}
      />

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

      <ReviewedSubtitleWorkflowPanel
        error={reviewedSubtitleWorkflowError}
        workflow={reviewedSubtitleWorkflow}
      />

      <SubtitleReviewEvidencePanel
        evidence={subtitleReviewEvidence}
        error={subtitleReviewEvidenceError}
        jobId={job.jobId}
      />

      <NarrationWorkspacePanel
        error={narrationError}
        evidence={narrationEvidence}
        isClearing={isClearingNarration}
        isGenerating={isGeneratingNarration}
        isGeneratingVideo={isGeneratingNarratedVideo}
        isSaving={isSavingNarration}
        jobId={job.jobId}
        onClear={onClearNarrationWorkspace}
        onGenerateAudio={onGenerateNarrationAudio}
        onGenerateVideo={onGenerateNarratedVideo}
        onApplyDemoPreset={onApplyNarrationDemoPreset}
        onImportScriptPackage={onImportNarrationScriptPackage}
        onRefreshEvidence={onRefreshNarrationEvidence}
        onSave={onSaveNarrationWorkspace}
        onSaveMixSettings={onSaveNarrationMixSettings}
        scriptPackage={narrationScriptPackage}
        demoPresets={narrationDemoPresets}
        status={narrationStatus}
        workspace={narrationWorkspace}
      />

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

function OpenAiSmokeProofPanel({
  error,
  isLoading,
  jobId,
  onRefresh,
  proof
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  onRefresh: () => void;
  proof: OpenAiSmokeProof | null;
}) {
  const [status, setStatus] = useState<string | null>(null);

  const handleDownload = useCallback(async () => {
    const blob = await linguaFrameApi.downloadOpenAiSmokeProofMarkdown(jobId);
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = `linguaframe-job-${sanitizeFilename(jobId)}-openai-smoke-proof.md`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
    setStatus('Smoke proof Markdown downloaded.');
  }, [jobId]);

  return (
    <section id="openai-smoke-proof" className="panel" aria-label="OpenAI smoke proof">
      <div className="panel-heading">
        <div>
          <h3>OpenAI smoke proof</h3>
          <p className="muted">
            {proof ? proof.recommendedNextAction : 'Post-run proof for provider-backed smoke jobs.'}
          </p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button type="button" className="secondary-button" onClick={handleDownload} disabled={!proof}>
            Download smoke proof
          </button>
        </div>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      {!proof ? <p className="muted">Smoke proof has not been loaded for this job.</p> : null}
      {proof ? (
        <>
          <dl className="status-grid">
            <div>
              <dt>Status</dt>
              <dd>{proof.overallStatus}</dd>
            </div>
            <div>
              <dt>Phase</dt>
              <dd>{proof.phase}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>{formatDateTime(proof.completedAt)}</dd>
            </div>
            <div>
              <dt>OpenAI calls</dt>
              <dd>{proof.modelCalls.length}</dd>
            </div>
          </dl>
          <div className="evidence-grid">
            <div>
              <h4>Required checks</h4>
              <ul>
                {proof.requiredChecks.map((check) => (
                  <li key={check.name}>
                    <strong>{check.status}</strong> {check.name}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Optional evidence</h4>
              <ul>
                {proof.optionalChecks.map((check) => (
                  <li key={check.name}>
                    <strong>{check.status}</strong> {check.name}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <table className="compact-table">
            <thead>
              <tr>
                <th>Operation</th>
                <th>Provider</th>
                <th>Status</th>
                <th>Model</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {proof.modelCalls.map((call) => (
                <tr key={`${call.operation}-${call.stage}-${call.model}`}>
                  <td>{call.operation}</td>
                  <td>{call.provider}</td>
                  <td>{call.status}</td>
                  <td>{call.model ?? 'N/A'}</td>
                  <td>{formatCost(call.estimatedCostUsd ?? 0)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="tag-list">
            {proof.artifacts.map((artifact) => (
              <span key={artifact.artifactId}>{artifact.type}</span>
            ))}
          </div>
          <div className="panel-actions">
            {proof.safeLinks.map((link) => (
              <a key={link.href} href={link.href}>
                {link.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoReviewerWorkspacePanel({
  error,
  isLoading,
  jobId,
  onRefresh,
  workspace
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  onRefresh: () => void;
  workspace: DemoReviewerWorkspace | null;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const requiredChecks = workspace?.checks.filter((check) => check.required) ?? [];
  const optionalChecks = workspace?.checks.filter((check) => !check.required) ?? [];

  const handleDownloadMarkdown = useCallback(async () => {
    const blob = await linguaFrameApi.downloadDemoReviewerWorkspaceMarkdown(jobId);
    downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-demo-reviewer-workspace.md`);
    setStatus('Reviewer Markdown downloaded.');
  }, [jobId]);

  const handleDownloadZip = useCallback(async () => {
    const blob = await linguaFrameApi.downloadDemoReviewerWorkspaceZip(jobId);
    downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-demo-reviewer-workspace.zip`);
    setStatus('Reviewer ZIP downloaded.');
  }, [jobId]);

  return (
    <section id="demo-reviewer-workspace" className="panel" aria-label="Demo reviewer workspace">
      <div className="panel-heading">
        <div>
          <h3>Demo reviewer workspace</h3>
          <p className="muted">
            {workspace ? workspace.recommendedNextAction : 'Metadata-only review package for the selected demo job.'}
          </p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadMarkdown} disabled={!workspace}>
            Download reviewer Markdown
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadZip} disabled={!workspace}>
            Download reviewer ZIP
          </button>
        </div>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      {!workspace ? <p className="muted">Reviewer workspace has not been loaded for this job.</p> : null}
      {workspace ? (
        <>
          <dl className="status-grid">
            <div>
              <dt>Status</dt>
              <dd>{workspace.overallStatus}</dd>
            </div>
            <div>
              <dt>Phase</dt>
              <dd>{workspace.phase}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{workspace.demoProfileId ?? 'N/A'}</dd>
            </div>
            <div>
              <dt>Completed</dt>
              <dd>{formatDateTime(workspace.completedAt)}</dd>
            </div>
          </dl>
          <div className="evidence-grid">
            <div>
              <h4>Required checks</h4>
              <ul>
                {requiredChecks.map((check) => (
                  <li key={check.key}>
                    <strong>{check.status}</strong> {check.label}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Optional evidence</h4>
              <ul>
                {optionalChecks.map((check) => (
                  <li key={check.key}>
                    <strong>{check.status}</strong> {check.label}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="evidence-grid">
            {workspace.sections.map((section) => (
              <div key={section.key}>
                <h4>{section.title}</h4>
                <p className={section.status === 'READY' ? 'success-text' : 'muted'}>{section.status}</p>
                <ul>
                  {section.facts.map((fact) => (
                    <li key={fact}>{fact}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="tag-list">
            {workspace.packageEntries.map((entry) => (
              <span key={entry}>{entry}</span>
            ))}
          </div>
          <div className="panel-actions">
            {workspace.safeLinks.map((link) => (
              <a key={link.href} href={link.href}>
                {link.label}
              </a>
            ))}
          </div>
          <ul>
            {workspace.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}

function DemoHandoffPortalPanel({
  error,
  isLoading,
  jobId,
  onRefresh,
  portal
}: {
  error: string | null;
  isLoading: boolean;
  jobId: string;
  onRefresh: () => void;
  portal: DemoHandoffPortal | null;
}) {
  const [status, setStatus] = useState<string | null>(null);
  const requiredChecks = portal?.checks.filter((check) => check.required) ?? [];
  const optionalChecks = portal?.checks.filter((check) => !check.required) ?? [];

  const handleDownloadMarkdown = useCallback(async () => {
    const blob = await linguaFrameApi.downloadDemoHandoffPortalMarkdown(jobId);
    downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-demo-handoff-portal.md`);
    setStatus('Handoff portal Markdown downloaded.');
  }, [jobId]);

  const handleDownloadZip = useCallback(async () => {
    const blob = await linguaFrameApi.downloadDemoHandoffPortalZip(jobId);
    downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-demo-handoff-portal.zip`);
    setStatus('Handoff portal ZIP downloaded.');
  }, [jobId]);

  return (
    <section id="demo-handoff-portal" className="panel" aria-label="Demo handoff portal">
      <div className="panel-heading">
        <div>
          <h3>Demo handoff portal</h3>
          <p className="muted">
            {portal ? portal.recommendedNextAction : 'Static offline portal package for demo reviewer handoff.'}
          </p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadMarkdown} disabled={!portal}>
            Download portal Markdown
          </button>
          <button type="button" className="secondary-button" onClick={handleDownloadZip} disabled={!portal}>
            Download portal ZIP
          </button>
        </div>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="mode-line">{status}</p> : null}
      {!portal ? <p className="muted">Handoff portal has not been loaded for this job.</p> : null}
      {portal ? (
        <>
          <dl className="status-grid">
            <div>
              <dt>Status</dt>
              <dd>{portal.overallStatus}</dd>
            </div>
            <div>
              <dt>Phase</dt>
              <dd>{portal.phase}</dd>
            </div>
            <div>
              <dt>Headline</dt>
              <dd>{portal.headline}</dd>
            </div>
            <div>
              <dt>Generated</dt>
              <dd>{formatDateTime(portal.generatedAt)}</dd>
            </div>
          </dl>
          <div className="evidence-grid">
            <div>
              <h4>Required checks</h4>
              <ul>
                {requiredChecks.map((check) => (
                  <li key={check.key}>
                    <strong>{check.status}</strong> {check.label}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Optional evidence</h4>
              <ul>
                {optionalChecks.map((check) => (
                  <li key={check.key}>
                    <strong>{check.status}</strong> {check.label}: {check.detail}
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="evidence-grid">
            {portal.sections.map((section) => (
              <div key={section.key}>
                <h4>{section.title}</h4>
                <p className={section.status === 'READY' ? 'success-text' : 'muted'}>{section.status}</p>
                <ul>
                  {section.facts.map((fact) => (
                    <li key={fact}>{fact}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="tag-list">
            {portal.packageEntries.map((entry) => (
              <span key={entry}>{entry}</span>
            ))}
          </div>
          <div className="panel-actions">
            {portal.safeLinks.map((link) => (
              <a key={link.href} href={link.href}>
                {link.label}
              </a>
            ))}
          </div>
          <ul>
            {portal.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </>
      ) : null}
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

function DemoReplayCardPanel({
  card,
  error,
  isLoading,
  onRefresh
}: {
  card: DemoReplayCard | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<string | null>(null);

  async function copyCommand(command: string) {
    await navigator.clipboard.writeText(command);
    setStatus('Replay command copied.');
  }

  return (
    <section className="panel demo-replay-card-panel" aria-label="Demo replay card">
      <div className="panel-heading">
        <div>
          <h3>Demo replay card</h3>
          <p className="muted">Metadata-only settings and commands for reproducing this run.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>
      {isLoading && !card ? <p className="muted">Loading demo replay card...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="success-text">{status}</p> : null}
      {card ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Readiness</dt>
              <dd>{card.readiness}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(card.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Recommended baseline</dt>
              <dd>{card.recommendedBaselineJobId ?? 'N/A'}</dd>
            </div>
            <div>
              <dt>Cost</dt>
              <dd>{formatCost(card.estimatedCostUsd)}</dd>
            </div>
          </dl>
          <h4>{card.headline}</h4>
          <div className="snapshot-section-grid">
            <div>
              <h4>Replay commands</h4>
              <ul className="compact-list command-list">
                {card.commands.map((command) => (
                  <li key={command.kind}>
                    <strong>{command.label}</strong>
                    <code>{command.command}</code>
                    <span>{command.note}</span>
                    <button
                      type="button"
                      className="secondary-button"
                      onClick={() => void copyCommand(command.command)}
                    >
                      Copy
                    </button>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <h4>Run settings</h4>
              <dl className="compact-definition-list">
                {card.settings.map((setting) => (
                  <div key={setting.key}>
                    <dt>{setting.label}</dt>
                    <dd>{setting.value}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </div>
          <ul className="checklist compact-list">
            {card.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="link-list">
            {card.links.slice(0, 8).map((link) => (
              <a key={`${link.kind}-${link.url}`} href={link.url}>
                {link.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoAcceptanceGatePanel({
  gate,
  error,
  isLoading,
  onRefresh
}: {
  gate: DemoAcceptanceGate | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const attentionChecks = gate?.checks.filter((check) => check.status !== 'PASS') ?? [];

  return (
    <section className="panel demo-acceptance-gate-panel" aria-label="Demo acceptance gate">
      <div className="panel-heading">
        <div>
          <h3>Demo acceptance gate</h3>
          <p className="muted">Final go/no-go checks for presenting this run.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>
      {isLoading && !gate ? <p className="muted">Loading demo acceptance gate...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {gate ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Gate</dt>
              <dd>{gate.gateStatus}</dd>
            </div>
            <div>
              <dt>Job status</dt>
              <dd>{gate.jobStatus}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(gate.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Language</dt>
              <dd>{gate.targetLanguage}</dd>
            </div>
          </dl>
          <h4>{gate.headline}</h4>
          <p>{gate.summary}</p>
          <p className={gate.gateStatus === 'READY' ? 'success-text' : gate.gateStatus === 'BLOCKED' ? 'error-text' : 'warning-text'}>
            {gate.recommendedNextAction}
          </p>
          {attentionChecks.length > 0 ? (
            <ul className="checklist compact-list">
              {attentionChecks.map((check) => (
                <li key={check.key}>
                  <strong>{check.label}: {check.status}</strong>
                  <span>{check.detail}</span>
                </li>
              ))}
            </ul>
          ) : null}
          <div className="snapshot-section-grid">
            <div>
              <h4>Acceptance evidence</h4>
              <dl className="compact-definition-list">
                {gate.evidence.slice(0, 10).map((item) => (
                  <div key={item.key}>
                    <dt>{item.label}</dt>
                    <dd>{item.value} ({item.status})</dd>
                  </div>
                ))}
              </dl>
            </div>
            <div>
              <h4>Required checks</h4>
              <ul className="compact-list">
                {gate.checks.filter((check) => check.required).map((check) => (
                  <li key={check.key}>
                    <strong>{check.label}</strong>
                    <span>{check.status}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <ul className="checklist compact-list">
            {gate.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="link-list">
            {gate.links.slice(0, 10).map((link) => (
              <a key={`${link.kind}-${link.url}`} href={link.url}>
                {link.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoCompletionCertificatePanel({
  certificate,
  error,
  isLoading,
  onRefresh
}: {
  certificate: DemoCompletionCertificate | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: () => void;
}) {
  const attentionChecks = certificate?.checks.filter((check) => check.blocking || check.status !== 'PASS') ?? [];

  return (
    <section className="panel demo-completion-certificate-panel" aria-label="Demo completion certificate">
      <div className="panel-heading">
        <div>
          <h3>Demo completion certificate</h3>
          <p className="muted">Final metadata-only proof that this run is complete and handoff-ready.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? 'Refreshing...' : 'Refresh'}
          </button>
        </div>
      </div>
      {isLoading && !certificate ? <p className="muted">Loading demo completion certificate...</p> : null}
      {error ? <p className="error-text">{error}</p> : null}
      {certificate ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Certificate</dt>
              <dd>{certificate.certificateStatus}</dd>
            </div>
            <div>
              <dt>Job status</dt>
              <dd>{certificate.jobStatus}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(certificate.demoProfileId)}</dd>
            </div>
            <div>
              <dt>Baseline</dt>
              <dd>{certificate.recommendedBaselineJobId ?? 'N/A'}</dd>
            </div>
          </dl>
          <h4>{certificate.headline}</h4>
          <p>{certificate.summary}</p>
          <p className={certificate.certificateStatus === 'READY' ? 'success-text' : 'warning-text'}>
            {certificate.recommendedNextAction}
          </p>
          {attentionChecks.length > 0 ? (
            <ul className="checklist compact-list">
              {attentionChecks.map((check) => (
                <li key={check.key}>
                  <strong>{check.label}: {check.status}</strong>
                  <span>{check.detail}</span>
                </li>
              ))}
            </ul>
          ) : null}
          <div className="snapshot-section-grid">
            {certificate.sections.map((section) => (
              <div key={section.key}>
                <h4>{section.title}</h4>
                <p className="muted">{section.status}</p>
                <ul className="compact-list">
                  {section.facts.slice(0, 8).map((fact) => (
                    <li key={fact}>{fact}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <ul className="checklist compact-list">
            {certificate.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="link-list">
            {certificate.links.slice(0, 10).map((link) => (
              <a key={`${link.kind}-${link.url}`} href={link.url}>
                {link.label}
              </a>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

function DemoRunVariancePanel({
  report,
  error,
  isLoading,
  onRefresh
}: {
  report: DemoRunVarianceReport | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: (preUploadJson: string) => void;
}) {
  const [preUploadJson, setPreUploadJson] = useState('');
  const [isDownloadingMarkdown, setIsDownloadingMarkdown] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  async function handleDownloadMarkdown() {
    if (!report?.jobId) {
      return;
    }
    setIsDownloadingMarkdown(true);
    setDownloadError(null);
    try {
      const blob = await linguaFrameApi.downloadDemoRunVarianceMarkdown(report.jobId, preUploadJson);
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = 'demo-run-variance.md';
      link.click();
      URL.revokeObjectURL(objectUrl);
    } catch (markdownError) {
      setDownloadError(toErrorMessage(markdownError));
    } finally {
      setIsDownloadingMarkdown(false);
    }
  }

  return (
    <section className="panel demo-run-variance-panel" aria-label="Demo run variance report">
      <div className="panel-heading">
        <div>
          <h3>Demo run variance</h3>
          <p className="muted">Compare a pre-upload estimate or decision package with this completed run.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={() => onRefresh(preUploadJson)} disabled={isLoading}>
            {isLoading ? 'Building...' : 'Build report'}
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => void handleDownloadMarkdown()}
            disabled={isDownloadingMarkdown || !report}
          >
            {isDownloadingMarkdown ? 'Downloading...' : 'Download Markdown'}
          </button>
        </div>
      </div>
      <label className="field-label" htmlFor="demo-run-variance-baseline">
        Pre-upload JSON baseline
      </label>
      <textarea
        id="demo-run-variance-baseline"
        className="json-textarea"
        value={preUploadJson}
        onChange={(event) => setPreUploadJson(event.target.value)}
        placeholder='Paste upload decision package JSON or execution-plan JSON. Leave blank for actual-only mode.'
        rows={6}
      />
      {error ? <p className="error-text">{error}</p> : null}
      {downloadError ? <p className="error-text">{downloadError}</p> : null}
      {report ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Status</dt>
              <dd>{report.overallStatus}</dd>
            </div>
            <div>
              <dt>Baseline</dt>
              <dd>{report.baselineMode}</dd>
            </div>
            <div>
              <dt>Job</dt>
              <dd>{report.jobStatus}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(report.demoProfileId)}</dd>
            </div>
          </dl>
          <p className={report.overallStatus === 'READY' ? 'success-text' : report.overallStatus === 'BLOCKED' ? 'error-text' : 'warning-text'}>
            {report.recommendedNextAction}
          </p>
          {report.notes.length > 0 ? (
            <ul className="compact-list">
              {report.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          ) : null}
          <div className="responsive-table">
            <table>
              <thead>
                <tr>
                  <th>Metric</th>
                  <th>Status</th>
                  <th>Estimated</th>
                  <th>Actual</th>
                </tr>
              </thead>
              <tbody>
                {report.metrics.map((metric) => (
                  <tr key={metric.id}>
                    <td>
                      <strong>{metric.label}</strong>
                      <span>{metric.detail}</span>
                    </td>
                    <td>{metric.status}</td>
                    <td>{metric.estimatedValue}</td>
                    <td>{metric.actualValue}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="link-list">
            {report.safeLinks.map((link) => (
              <a key={link} href={link}>
                {link}
              </a>
            ))}
          </div>
          <ul className="compact-list">
            {report.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
        </>
      ) : null}
    </section>
  );
}

function DemoEvidenceClosurePanel({
  closure,
  error,
  isLoading,
  onRefresh
}: {
  closure: DemoEvidenceClosurePackage | null;
  error: string | null;
  isLoading: boolean;
  onRefresh: (preUploadJson: string) => void;
}) {
  const [preUploadJson, setPreUploadJson] = useState('');
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [isDownloadingMarkdown, setIsDownloadingMarkdown] = useState(false);
  const [isDownloadingZip, setIsDownloadingZip] = useState(false);

  async function downloadClosureMarkdown() {
    if (!closure?.jobId) {
      return;
    }
    setIsDownloadingMarkdown(true);
    setDownloadError(null);
    try {
      const blob = await linguaFrameApi.downloadDemoEvidenceClosureMarkdown(closure.jobId, preUploadJson);
      downloadBlob(blob, 'demo-evidence-closure.md');
    } catch (markdownError) {
      setDownloadError(toErrorMessage(markdownError));
    } finally {
      setIsDownloadingMarkdown(false);
    }
  }

  async function downloadClosureZip() {
    if (!closure?.jobId) {
      return;
    }
    setIsDownloadingZip(true);
    setDownloadError(null);
    try {
      const blob = await linguaFrameApi.downloadDemoEvidenceClosureZip(closure.jobId, preUploadJson);
      downloadBlob(blob, `linguaframe-job-${closure.jobId}-demo-evidence-closure.zip`);
    } catch (zipError) {
      setDownloadError(toErrorMessage(zipError));
    } finally {
      setIsDownloadingZip(false);
    }
  }

  return (
    <section className="panel demo-evidence-closure-panel" aria-label="Demo evidence closure package">
      <div className="panel-heading">
        <div>
          <h3>Demo evidence closure</h3>
          <p className="muted">Build the final reviewer package that closes pre-upload decision, variance, acceptance, and completion evidence.</p>
        </div>
        <div className="panel-actions">
          <button type="button" className="secondary-button" onClick={() => onRefresh(preUploadJson)} disabled={isLoading}>
            {isLoading ? 'Building...' : 'Build closure'}
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => void downloadClosureMarkdown()}
            disabled={isDownloadingMarkdown || !closure}
          >
            {isDownloadingMarkdown ? 'Downloading...' : 'Download Markdown'}
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => void downloadClosureZip()}
            disabled={isDownloadingZip || !closure}
          >
            {isDownloadingZip ? 'Downloading...' : 'Download ZIP'}
          </button>
        </div>
      </div>
      <label className="field-label" htmlFor="demo-evidence-closure-baseline">
        Pre-upload JSON baseline
      </label>
      <textarea
        id="demo-evidence-closure-baseline"
        className="json-textarea"
        value={preUploadJson}
        onChange={(event) => setPreUploadJson(event.target.value)}
        placeholder='Paste upload decision package JSON or execution-plan JSON. Leave blank for actual-only closure.'
        rows={6}
      />
      {error ? <p className="error-text">{error}</p> : null}
      {downloadError ? <p className="error-text">{downloadError}</p> : null}
      {closure ? (
        <>
          <dl className="status-grid compact-status-grid">
            <div>
              <dt>Closure</dt>
              <dd>{closure.closureStatus}</dd>
            </div>
            <div>
              <dt>Baseline</dt>
              <dd>{closure.baselineMode}</dd>
            </div>
            <div>
              <dt>Variance</dt>
              <dd>{closure.varianceReport.overallStatus}</dd>
            </div>
            <div>
              <dt>Profile</dt>
              <dd>{formatDemoProfileId(closure.demoProfileId)}</dd>
            </div>
          </dl>
          <p className={closure.closureStatus === 'READY' ? 'success-text' : closure.closureStatus === 'BLOCKED' ? 'error-text' : 'warning-text'}>
            {closure.recommendedNextAction}
          </p>
          <div className="snapshot-section-grid">
            {closure.sections.map((section) => (
              <div key={section.key}>
                <h4>{section.title}</h4>
                <p className="muted">{section.status}</p>
                <p>{section.summary}</p>
                <ul className="compact-list">
                  {section.facts.slice(0, 6).map((fact) => (
                    <li key={fact}>{fact}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
          <div className="link-list">
            {closure.safeLinks.slice(0, 12).map((link) => (
              <a key={link} href={link}>
                {link}
              </a>
            ))}
          </div>
          <ul className="compact-list">
            {closure.safetyNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
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

function ReviewedSubtitleWorkflowPanel({
  error,
  workflow
}: {
  error: string | null;
  workflow: ReviewedSubtitleWorkflow | null;
}) {
  if (!workflow) {
    return (
      <section id="reviewed-subtitle-workflow" className="panel" aria-label="Reviewed subtitle workflow">
        <h3>Reviewed subtitle workflow</h3>
        {error ? <p className="muted">{error}</p> : <p className="muted">No reviewed subtitle workflow loaded yet.</p>}
      </section>
    );
  }

  return (
    <section id="reviewed-subtitle-workflow" className="panel" aria-label="Reviewed subtitle workflow">
      <div className="panel-heading">
        <div>
          <h3>Reviewed subtitle workflow</h3>
          <p className="muted">{workflow.recommendedNextAction}</p>
        </div>
        <span className={`status-pill ${workflow.overallStatus === 'BLOCKED' ? 'warning' : ''}`}>
          {workflow.overallStatus} · {workflow.phase}
        </span>
      </div>
      <dl className="status-grid compact-status-grid">
        <div>
          <dt>Review issues</dt>
          <dd>{workflow.missingTargetCount} missing / {workflow.timingMismatchCount} timing</dd>
        </div>
        <div>
          <dt>Draft edits</dt>
          <dd>{workflow.editedSegmentCount} / {workflow.segmentCount}</dd>
        </div>
        <div>
          <dt>Generated subtitles</dt>
          <dd>{workflow.generatedSubtitleArtifactCount} files</dd>
        </div>
        <div>
          <dt>Reviewed subtitles</dt>
          <dd>{workflow.reviewedSubtitleArtifactCount} files</dd>
        </div>
        <div>
          <dt>Reviewed video</dt>
          <dd>{workflow.reviewedBurnedVideoAvailable ? 'Available' : 'Not available'}</dd>
        </div>
        <div>
          <dt>Handoff</dt>
          <dd>{workflow.handoffReady ? 'Ready' : 'Pending'}</dd>
        </div>
      </dl>
      <ul className="checklist">
        {workflow.checks.map((check) => (
          <li key={check.key}>
            <strong>{check.label}</strong>
            <span className="muted">{check.status} · {check.detail}</span>
            <small>{check.nextAction}</small>
          </li>
        ))}
      </ul>
      <div className="panel-actions">
        {workflow.links.map((link) => (
          <a key={link.kind} className="secondary-link" href={link.url}>
            {link.label}
          </a>
        ))}
      </div>
      <ul className="compact-list">
        {workflow.safetyNotes.map((note) => (
          <li key={note}>{note}</li>
        ))}
      </ul>
    </section>
  );
}

function NarrationWorkspacePanel({
  error,
  evidence,
  isClearing,
  isGenerating,
  isGeneratingVideo,
  isSaving,
  jobId,
  demoPresets,
  onClear,
  onApplyDemoPreset,
  onGenerateAudio,
  onGenerateVideo,
  onImportScriptPackage,
  onRefreshEvidence,
  onSave,
  onSaveMixSettings,
  scriptPackage,
  status,
  workspace
}: {
  error: string | null;
  evidence: NarrationEvidence | null;
  isClearing: boolean;
  isGenerating: boolean;
  isGeneratingVideo: boolean;
  isSaving: boolean;
  jobId: string;
  demoPresets: NarrationDemoPreset[];
  onClear: () => void;
  onApplyDemoPreset: (presetId: string) => void;
  onGenerateAudio: () => void;
  onGenerateVideo: () => void;
  onImportScriptPackage: (request: ImportNarrationScriptPackageRequest) => void;
  onRefreshEvidence: () => void;
  onSave: (segments: NarrationWorkspace['segments']) => void;
  onSaveMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
  scriptPackage: NarrationScriptPackage | null;
  status: string | null;
  workspace: NarrationWorkspace | null;
}) {
  const [segments, setSegments] = useState<NarrationWorkspace['segments']>([]);
  const [mixSettings, setMixSettings] = useState<NarrationWorkspace['mixSettings'] | null>(null);
  const [selectedIndex, setSelectedIndex] = useState(0);

  useEffect(() => {
    setSegments(workspace?.segments ?? []);
    setMixSettings(workspace?.mixSettings ?? null);
    setSelectedIndex(0);
  }, [workspace]);

  const selectedSegment = segments[selectedIndex] ?? null;
  const validation = validateNarrationSegments(segments, workspace?.voiceCatalog ?? null);
  const mixValidation = validateNarrationMixSettings(mixSettings);

  function updateSegment(index: number, patch: Partial<NarrationWorkspace['segments'][number]>) {
    setSegments((current) =>
      current.map((segment, currentIndex) => (currentIndex === index ? { ...segment, ...patch } : segment))
    );
  }

  function addSegment() {
    setSegments((current) => [
      ...current,
      {
        index: current.length,
        startSeconds: current.length === 0 ? 0 : current[current.length - 1].endSeconds,
        endSeconds: current.length === 0 ? 10 : current[current.length - 1].endSeconds + 10,
        durationSeconds: 10,
        text: '',
        voice: '',
        characterCount: 0,
        updatedAt: null
      }
    ]);
    setSelectedIndex(segments.length);
  }

  function deleteSelectedSegment() {
    setSegments((current) => current.filter((_, index) => index !== selectedIndex).map((segment, index) => ({ ...segment, index })));
    setSelectedIndex(Math.max(0, selectedIndex - 1));
  }

  return (
    <section id="narration-workspace" className="panel narration-workspace-panel" aria-label="Narration workspace">
      <div className="panel-heading">
        <div>
          <h3>Narration workspace</h3>
          <p className="muted">
            {workspace ? `${workspace.segmentCount} segments · ${workspace.totalCharacterCount} chars` : 'No workspace loaded.'}
          </p>
        </div>
        <div className="panel-actions">
          <button type="button" onClick={addSegment}>Add row</button>
          <button type="button" onClick={deleteSelectedSegment} disabled={!selectedSegment}>Delete row</button>
          <button type="button" onClick={() => onSave(segments)} disabled={isSaving || validation.length > 0}>
            {isSaving ? 'Saving...' : 'Save narration'}
          </button>
          <button type="button" onClick={onGenerateAudio} disabled={isGenerating || !workspace?.generationReady || validation.length > 0}>
            {isGenerating ? 'Generating...' : 'Generate narration audio'}
          </button>
          <button
            type="button"
            onClick={onGenerateVideo}
            disabled={isGeneratingVideo || !evidence?.narrationAudioReady || validation.length > 0}
          >
            {isGeneratingVideo ? 'Generating video...' : 'Generate narrated video'}
          </button>
          <button type="button" onClick={onClear} disabled={isClearing}>Clear</button>
        </div>
      </div>
      {error ? <p className="error-text">{error}</p> : null}
      {status ? <p className="success-text">{status}</p> : null}
      {validation.length > 0 ? (
        <ul className="error-list">
          {validation.map((message) => <li key={message}>{message}</li>)}
        </ul>
      ) : null}
      <div className="narration-workbench">
        <div className="narration-table-wrap">
          {workspace?.timeline ? (
            <NarrationTimelineWorkbench timeline={workspace.timeline} />
          ) : null}
          <NarrationSegmentTable
            segments={segments}
            selectedIndex={selectedIndex}
            voiceCatalog={workspace?.voiceCatalog ?? null}
            onSelectSegment={setSelectedIndex}
            onUpdateSegment={updateSegment}
          />
        </div>
        <NarrationInspector
          evidence={evidence}
          isSaving={isSaving}
          jobId={jobId}
          mixSettings={mixSettings}
          mixValidation={mixValidation}
          selectedIndex={selectedIndex}
          selectedSegment={selectedSegment}
          voiceCatalog={workspace?.voiceCatalog ?? null}
          onRefreshEvidence={onRefreshEvidence}
          onSaveMixSettings={onSaveMixSettings}
          onUpdateMixSettings={setMixSettings}
          onUpdateSegment={updateSegment}
        />
        <NarrationScriptPackagePanel
          isImporting={isSaving}
          jobId={jobId}
          scriptPackage={scriptPackage}
          workspace={workspace}
          onImportScriptPackage={onImportScriptPackage}
        />
        <NarrationDemoPresetPanel
          isApplying={isSaving}
          presets={demoPresets}
          workspace={workspace}
          onApplyPreset={onApplyDemoPreset}
        />
      </div>
    </section>
  );
}

function NarrationSegmentTable({
  onSelectSegment,
  onUpdateSegment,
  segments,
  selectedIndex,
  voiceCatalog
}: {
  onSelectSegment: (index: number) => void;
  onUpdateSegment: (index: number, patch: Partial<NarrationWorkspace['segments'][number]>) => void;
  segments: NarrationWorkspace['segments'];
  selectedIndex: number;
  voiceCatalog: NarrationWorkspace['voiceCatalog'] | null;
}) {
  const voiceOptions = voiceCatalog?.presets ?? [];
  return (
    <>
      {voiceCatalog ? (
        <div className="voice-preset-strip" aria-label="Voice presets">
          <div>
            <h4>Voice presets</h4>
            <p className="muted">Default voice: {voiceCatalog.defaultVoice}</p>
          </div>
          <span className="status-pill ready">{voiceCatalog.provider}</span>
        </div>
      ) : null}
      <table className="narration-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Start</th>
            <th>End</th>
            <th>Voice</th>
            <th>Text</th>
          </tr>
        </thead>
        <tbody>
          {segments.length === 0 ? (
            <tr>
              <td colSpan={5}>No narration rows.</td>
            </tr>
          ) : segments.map((segment, index) => (
            <tr key={index} className={index === selectedIndex ? 'selected-row' : undefined}>
              <td>
                <button type="button" onClick={() => onSelectSegment(index)}>{index + 1}</button>
              </td>
              <td>
                <input
                  aria-label={`Narration ${index + 1} start`}
                  min="0"
                  step="0.001"
                  type="number"
                  value={segment.startSeconds}
                  onChange={(event) => onUpdateSegment(index, { startSeconds: Number(event.target.value) })}
                />
              </td>
              <td>
                <input
                  aria-label={`Narration ${index + 1} end`}
                  min="0"
                  step="0.001"
                  type="number"
                  value={segment.endSeconds}
                  onChange={(event) => onUpdateSegment(index, { endSeconds: Number(event.target.value) })}
                />
              </td>
              <td>
                <select
                  aria-label={`Narration ${index + 1} voice`}
                  value={segment.voice ?? ''}
                  onChange={(event) => onUpdateSegment(index, { voice: event.target.value || null })}
                >
                  <option value="">Inherit default ({voiceCatalog?.defaultVoice ?? 'default'})</option>
                  {voiceOptions.map((preset) => (
                    <option key={preset.voice} value={preset.voice}>{preset.label}</option>
                  ))}
                  {segment.voice && !voiceOptions.some((preset) => preset.voice === segment.voice) ? (
                    <option value={segment.voice}>Unknown: {segment.voice}</option>
                  ) : null}
                </select>
              </td>
              <td>{segment.text || '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function NarrationInspector({
  evidence,
  isSaving,
  jobId,
  mixSettings,
  mixValidation,
  onRefreshEvidence,
  onSaveMixSettings,
  onUpdateMixSettings,
  onUpdateSegment,
  selectedIndex,
  selectedSegment,
  voiceCatalog
}: {
  evidence: NarrationEvidence | null;
  isSaving: boolean;
  jobId: string;
  mixSettings: NarrationWorkspace['mixSettings'] | null;
  mixValidation: string[];
  onRefreshEvidence: () => void;
  onSaveMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
  onUpdateMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
  onUpdateSegment: (index: number, patch: Partial<NarrationWorkspace['segments'][number]>) => void;
  selectedIndex: number;
  selectedSegment: NarrationWorkspace['segments'][number] | null;
  voiceCatalog: NarrationWorkspace['voiceCatalog'] | null;
}) {
  return (
    <aside className="narration-inspector" aria-label="Narration inspector">
      {selectedSegment ? (
        <>
          <div className="selected-segment-summary">
            <h4>Selected segment</h4>
            <dl className="compact-metrics">
              <div>
                <dt>Window</dt>
                <dd>{formatSeconds(selectedSegment.startSeconds)} - {formatSeconds(selectedSegment.endSeconds)}</dd>
              </div>
              <div>
                <dt>Duration</dt>
                <dd>{formatSeconds(selectedSegment.durationSeconds)}</dd>
              </div>
              <div>
                <dt>Voice</dt>
                <dd>{formatNarrationVoiceState(selectedSegment.voice, voiceCatalog)}</dd>
              </div>
              <div>
                <dt>Characters</dt>
                <dd>{selectedSegment.text.length}</dd>
              </div>
            </dl>
          </div>
          <label>
            Segment text
            <textarea
              maxLength={1000}
              value={selectedSegment.text}
              onChange={(event) => onUpdateSegment(selectedIndex, { text: event.target.value })}
            />
          </label>
          <NarrationEvidenceMetrics evidence={evidence} />
          {mixSettings ? (
            <NarrationMixSettingsPanel
              isSaving={isSaving}
              mixSettings={mixSettings}
              mixValidation={mixValidation}
              onSaveMixSettings={onSaveMixSettings}
              onUpdateMixSettings={onUpdateMixSettings}
            />
          ) : null}
          <div className="panel-actions">
            <button type="button" onClick={onRefreshEvidence}>Refresh evidence</button>
            <button type="button" onClick={() => void downloadNarrationEvidenceFile(jobId, 'markdown')}>
              Download Markdown
            </button>
            <button type="button" onClick={() => void downloadNarrationEvidenceFile(jobId, 'zip')}>
              Download ZIP
            </button>
          </div>
        </>
      ) : (
        <p className="muted">Select a row to edit narration text.</p>
      )}
    </aside>
  );
}

function NarrationScriptPackagePanel({
  isImporting,
  jobId,
  onImportScriptPackage,
  scriptPackage,
  workspace
}: {
  isImporting: boolean;
  jobId: string;
  onImportScriptPackage: (request: ImportNarrationScriptPackageRequest) => void;
  scriptPackage: NarrationScriptPackage | null;
  workspace: NarrationWorkspace | null;
}) {
  const [packageJson, setPackageJson] = useState('');
  const [replaceCurrentWorkspace, setReplaceCurrentWorkspace] = useState(false);
  const parsedPackage = parseNarrationScriptPackageImport(packageJson);
  const jsonError = packageJson.trim() && !parsedPackage ? 'Package JSON is not valid.' : null;
  const canImport = Boolean(parsedPackage && replaceCurrentWorkspace && !isImporting);

  return (
    <section className="script-package-panel" aria-label="Script package">
      <div className="compact-panel-heading">
        <div>
          <h4>Script package</h4>
          <p className="muted">
            {scriptPackage
              ? `${scriptPackage.segmentCount} segments · ${scriptPackage.totalCharacterCount} chars`
              : 'No package loaded.'}
          </p>
        </div>
        <span className={scriptPackage?.status === 'READY' ? 'status-pill ready' : 'status-pill attention'}>
          {scriptPackage?.status ?? 'Missing'}
        </span>
      </div>
      <dl className="compact-metrics">
        <div>
          <dt>Voice</dt>
          <dd>{scriptPackage?.voiceSummary ?? 'N/A'}</dd>
        </div>
        <div>
          <dt>Gaps</dt>
          <dd>{scriptPackage ? `${scriptPackage.timelineGapCount} gaps` : 'N/A'}</dd>
        </div>
        <div>
          <dt>Duration</dt>
          <dd>{formatSeconds(scriptPackage?.durationSeconds)}</dd>
        </div>
        <div>
          <dt>Workspace</dt>
          <dd>{workspace?.status ?? 'N/A'}</dd>
        </div>
      </dl>
      {scriptPackage?.checks.length ? (
        <ul className="script-package-checks">
          {scriptPackage.checks.map((check) => (
            <li key={check.key}>{check.key}: {check.status}</li>
          ))}
        </ul>
      ) : null}
      <div className="panel-actions">
        <button type="button" onClick={() => void downloadNarrationScriptPackageFile(jobId, 'markdown')}>
          Download package Markdown
        </button>
        <button type="button" onClick={() => void downloadNarrationScriptPackageFile(jobId, 'zip')}>
          Download package ZIP
        </button>
      </div>
      <label>
        Script package JSON
        <textarea
          aria-label="Script package JSON"
          value={packageJson}
          onChange={(event) => setPackageJson(event.target.value)}
          placeholder='{"replaceExisting":true,"segments":[]}'
        />
      </label>
      {jsonError ? <p className="error-text">{jsonError}</p> : null}
      <label className="inline-checkbox">
        <input
          aria-label="Replace current narration workspace"
          checked={replaceCurrentWorkspace}
          type="checkbox"
          onChange={(event) => setReplaceCurrentWorkspace(event.target.checked)}
        />
        Replace current narration workspace
      </label>
      <button
        type="button"
        disabled={!canImport}
        onClick={() => parsedPackage ? onImportScriptPackage({ ...parsedPackage, replaceExisting: true }) : undefined}
      >
        {isImporting ? 'Importing...' : 'Import package'}
      </button>
    </section>
  );
}

function NarrationDemoPresetPanel({
  isApplying,
  onApplyPreset,
  presets,
  workspace
}: {
  isApplying: boolean;
  onApplyPreset: (presetId: string) => void;
  presets: NarrationDemoPreset[];
  workspace: NarrationWorkspace | null;
}) {
  const [selectedPresetId, setSelectedPresetId] = useState(presets[0]?.id ?? '');
  const [replaceCurrentWorkspace, setReplaceCurrentWorkspace] = useState(false);
  const selectedPreset = presets.find((preset) => preset.id === selectedPresetId) ?? presets[0] ?? null;
  const canApply = Boolean(selectedPreset && replaceCurrentWorkspace && !isApplying);

  useEffect(() => {
    if (!selectedPresetId && presets[0]) {
      setSelectedPresetId(presets[0].id);
    }
  }, [presets, selectedPresetId]);

  return (
    <section className="script-package-panel" aria-label="Demo narration preset">
      <div className="compact-panel-heading">
        <div>
          <h4>Demo narration preset</h4>
          <p className="muted">
            {selectedPreset ? selectedPreset.description : 'No narration preset available.'}
          </p>
        </div>
        <span className={selectedPreset ? 'status-pill ready' : 'status-pill attention'}>
          {selectedPreset ? 'Available' : 'Missing'}
        </span>
      </div>
      {selectedPreset ? (
        <>
          <label>
            Preset
            <select
              aria-label="Narration demo preset"
              value={selectedPreset.id}
              onChange={(event) => {
                setSelectedPresetId(event.target.value);
                setReplaceCurrentWorkspace(false);
              }}
            >
              {presets.map((preset) => (
                <option key={preset.id} value={preset.id}>{preset.label}</option>
              ))}
            </select>
          </label>
          <dl className="compact-metrics">
            <div>
              <dt>Profile</dt>
              <dd>{selectedPreset.profileId}</dd>
            </div>
            <div>
              <dt>Sample</dt>
              <dd>{selectedPreset.sampleIdHint}</dd>
            </div>
            <div>
              <dt>Segments</dt>
              <dd>{selectedPreset.segmentCount} segments</dd>
            </div>
            <div>
              <dt>Voice</dt>
              <dd>{selectedPreset.voiceSummary}</dd>
            </div>
            <div>
              <dt>Span</dt>
              <dd>{formatSeconds(selectedPreset.timeSpanSeconds)}</dd>
            </div>
            <div>
              <dt>Workspace</dt>
              <dd>{workspace?.status ?? 'N/A'}</dd>
            </div>
          </dl>
          <p className="muted">Generate narration audio separately after applying.</p>
          <label className="inline-checkbox">
            <input
              aria-label="Replace current narration workspace with preset"
              checked={replaceCurrentWorkspace}
              type="checkbox"
              onChange={(event) => setReplaceCurrentWorkspace(event.target.checked)}
            />
            Replace current narration workspace with preset
          </label>
          <button
            type="button"
            disabled={!canApply}
            onClick={() => onApplyPreset(selectedPreset.id)}
          >
            {isApplying ? 'Applying...' : 'Apply preset'}
          </button>
        </>
      ) : null}
    </section>
  );
}

function NarrationEvidenceMetrics({ evidence }: { evidence: NarrationEvidence | null }) {
  return (
    <dl className="compact-metrics">
      <div>
        <dt>Evidence</dt>
        <dd>{evidence?.status ?? 'Not loaded'}</dd>
      </div>
      <div>
        <dt>Audio</dt>
        <dd>{evidence?.narrationAudioReady ? 'Ready' : 'Missing'}</dd>
      </div>
      <div>
        <dt>Voice summary</dt>
        <dd>{evidence?.voiceSummary ?? 'N/A'}</dd>
      </div>
      <div>
        <dt>Default voice</dt>
        <dd>{evidence?.defaultVoice ?? 'N/A'}</dd>
      </div>
      <div>
        <dt>Audio layout</dt>
        <dd>{formatEvidenceToken(evidence?.audioLayout)}</dd>
      </div>
      <div>
        <dt>Time aligned</dt>
        <dd>{evidence?.timeAligned ? 'true' : 'false'}</dd>
      </div>
      <div>
        <dt>Video</dt>
        <dd>{evidence?.narratedVideoReady ? 'Ready' : 'Missing'}</dd>
      </div>
      <div>
        <dt>Video artifacts</dt>
        <dd>{evidence?.narratedVideoArtifactCount ?? 0}</dd>
      </div>
      <div>
        <dt>Mix mode</dt>
        <dd>{formatEvidenceToken(evidence?.mixMode)}</dd>
      </div>
      <div>
        <dt>Ducking volume</dt>
        <dd>{formatNullableNumber(evidence?.duckingVolume)}</dd>
      </div>
      <div>
        <dt>Narration volume</dt>
        <dd>{formatNullableNumber(evidence?.narrationVolume)}</dd>
      </div>
      <div>
        <dt>Fade duration</dt>
        <dd>{evidence?.fadeDurationMs ? `${evidence.fadeDurationMs} ms` : 'N/A'}</dd>
      </div>
      <div>
        <dt>Mix settings</dt>
        <dd>{formatEvidenceToken(evidence?.mixSettingsSource)}</dd>
      </div>
      <div>
        <dt>Narration windows</dt>
        <dd>{evidence?.segmentCount ?? 0} windows</dd>
      </div>
    </dl>
  );
}

function NarrationMixSettingsPanel({
  isSaving,
  mixSettings,
  mixValidation,
  onSaveMixSettings,
  onUpdateMixSettings
}: {
  isSaving: boolean;
  mixSettings: NarrationWorkspace['mixSettings'];
  mixValidation: string[];
  onSaveMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
  onUpdateMixSettings: (settings: NarrationWorkspace['mixSettings']) => void;
}) {
  return (
    <div className="mix-settings-panel" aria-label="Mix settings">
      <label>
        Ducking volume
        <input
          aria-label="Ducking volume"
          max="1"
          min="0"
          step="0.001"
          type="number"
          value={mixSettings.duckingVolume}
          onChange={(event) => onUpdateMixSettings({ ...mixSettings, duckingVolume: Number(event.target.value) })}
        />
      </label>
      <label>
        Narration volume
        <input
          aria-label="Narration volume"
          max="2"
          min="0"
          step="0.001"
          type="number"
          value={mixSettings.narrationVolume}
          onChange={(event) => onUpdateMixSettings({ ...mixSettings, narrationVolume: Number(event.target.value) })}
        />
      </label>
      <label>
        Fade duration ms
        <input
          aria-label="Fade duration ms"
          max="5000"
          min="0"
          step="1"
          type="number"
          value={mixSettings.fadeDurationMs}
          onChange={(event) => onUpdateMixSettings({ ...mixSettings, fadeDurationMs: Number(event.target.value) })}
        />
      </label>
      {mixValidation.length > 0 ? (
        <ul className="error-list">
          {mixValidation.map((message) => <li key={message}>{message}</li>)}
        </ul>
      ) : null}
      <button
        type="button"
        onClick={() => onSaveMixSettings(mixSettings)}
        disabled={isSaving || mixValidation.length > 0}
      >
        {isSaving ? 'Saving...' : 'Save mix settings'}
      </button>
    </div>
  );
}

function NarrationTimelineWorkbench({ timeline }: { timeline: NarrationWorkspace['timeline'] }) {
  const gapSummary = timeline.gapCount === 0
    ? 'No gaps'
    : `${timeline.gapCount} ${timeline.gapCount === 1 ? 'gap' : 'gaps'} · ${formatSeconds(timeline.gapSeconds)}`;
  return (
    <div className="narration-timeline-workbench" aria-label="Narration timeline workbench">
      <div className="compact-panel-heading">
        <div>
          <h4>Timeline workbench</h4>
          <p className="muted">
            {formatSeconds(timeline.totalSpanSeconds)} span · {formatSeconds(timeline.coveredSeconds)} covered
          </p>
        </div>
        <span className={timeline.hasOverlap ? 'status-pill blocked' : 'status-pill ready'}>
          {timeline.hasOverlap ? 'Overlap' : gapSummary}
        </span>
      </div>
      <div className="narration-timeline-track" aria-label="Narration timeline track">
        {timeline.segments.length === 0 ? (
          <span className="narration-empty-track">No narration windows</span>
        ) : timeline.segments.map((segment) => (
          <button
            aria-label={`Timeline segment ${segment.index + 1}: ${formatSeconds(segment.startSeconds)} to ${formatSeconds(segment.endSeconds)}, ${segment.status}`}
            className="narration-timeline-segment"
            key={segment.index}
            style={{
              left: `${segment.leftPercent}%`,
              width: `${Math.max(segment.widthPercent, 2)}%`
            }}
            title={`${segment.index + 1}: ${formatSeconds(segment.startSeconds)}-${formatSeconds(segment.endSeconds)}`}
            type="button"
          >
            {segment.index + 1}
          </button>
        ))}
      </div>
      <dl className="compact-metrics narration-timeline-metrics">
        <div>
          <dt>Start</dt>
          <dd>{formatSeconds(timeline.startSeconds)}</dd>
        </div>
        <div>
          <dt>End</dt>
          <dd>{formatSeconds(timeline.endSeconds)}</dd>
        </div>
        <div>
          <dt>Gaps</dt>
          <dd>{gapSummary}</dd>
        </div>
        <div>
          <dt>Ready</dt>
          <dd>{timeline.generationReady ? 'true' : 'false'}</dd>
        </div>
      </dl>
    </div>
  );
}

function formatEvidenceToken(value: string | null | undefined) {
  if (!value || value === 'MISSING') {
    return 'Missing';
  }
  return value
    .toLowerCase()
    .split('_')
    .map((part, index) => (index === 0 ? part.charAt(0).toUpperCase() + part.slice(1) : part))
    .join(' ');
}

function formatNullableNumber(value: number | null | undefined) {
  return value == null ? 'N/A' : String(value);
}

function formatSeconds(value: number | null | undefined) {
  if (value == null || Number.isNaN(value)) {
    return 'N/A';
  }
  return `${Number(value.toFixed(3))} s`;
}

function formatNarrationVoiceState(voice: string | null | undefined, catalog: NarrationWorkspace['voiceCatalog'] | null) {
  if (!voice) {
    return `Inherited default: ${catalog?.defaultVoice ?? 'default'}`;
  }
  const preset = catalog?.presets.find((candidate) => candidate.voice === voice);
  return preset ? `Explicit preset: ${preset.voice}` : `Unknown voice: ${voice}`;
}

function validateNarrationSegments(segments: NarrationWorkspace['segments'], catalog: NarrationWorkspace['voiceCatalog'] | null): string[] {
  const messages: string[] = [];
  segments.forEach((segment, index) => {
    if (!segment.text.trim()) {
      messages.push(`Row ${index + 1}: text is required.`);
    }
    if (segment.endSeconds <= segment.startSeconds) {
      messages.push(`Row ${index + 1}: end must be after start.`);
    }
    if (segment.text.length > 1000) {
      messages.push(`Row ${index + 1}: text must be 1000 characters or fewer.`);
    }
    if ((segment.voice ?? '').length > 64) {
      messages.push(`Row ${index + 1}: voice must be 64 characters or fewer.`);
    }
    if (segment.voice && catalog && !catalog.presets.some((preset) => preset.voice === segment.voice)) {
      messages.push(`Row ${index + 1}: voice must be one of the configured presets.`);
    }
  });
  for (let index = 1; index < segments.length; index += 1) {
    if (segments[index].startSeconds < segments[index - 1].endSeconds) {
      messages.push(`Row ${index + 1}: time range overlaps the previous row.`);
    }
  }
  return messages;
}

function validateNarrationMixSettings(settings: NarrationWorkspace['mixSettings'] | null): string[] {
  if (!settings) {
    return [];
  }
  const messages: string[] = [];
  if (settings.duckingVolume < 0 || settings.duckingVolume > 1) {
    messages.push('Ducking volume must be between 0.00 and 1.00.');
  }
  if (settings.narrationVolume < 0 || settings.narrationVolume > 2) {
    messages.push('Narration volume must be between 0.00 and 2.00.');
  }
  if (settings.fadeDurationMs < 0 || settings.fadeDurationMs > 5000) {
    messages.push('Fade duration must be between 0 and 5000 ms.');
  }
  return messages;
}

async function downloadNarrationEvidenceFile(jobId: string, format: 'markdown' | 'zip') {
  const blob = format === 'markdown'
    ? await linguaFrameApi.downloadNarrationEvidenceMarkdown(jobId)
    : await linguaFrameApi.downloadNarrationEvidenceZip(jobId);
  downloadBlob(blob, `narration-evidence-${jobId}.${format === 'markdown' ? 'md' : 'zip'}`);
}

async function downloadNarrationScriptPackageFile(jobId: string, format: 'markdown' | 'zip') {
  const blob = format === 'markdown'
    ? await linguaFrameApi.downloadNarrationScriptPackageMarkdown(jobId)
    : await linguaFrameApi.downloadNarrationScriptPackageZip(jobId);
  downloadBlob(blob, `narration-script-package-${jobId}.${format === 'markdown' ? 'md' : 'zip'}`);
}

function parseNarrationScriptPackageImport(value: string): ImportNarrationScriptPackageRequest | null {
  if (!value.trim()) {
    return null;
  }
  try {
    const parsed = JSON.parse(value) as Partial<NarrationScriptPackage & ImportNarrationScriptPackageRequest>;
    if (!Array.isArray(parsed.segments)) {
      return null;
    }
    return {
      replaceExisting: Boolean(parsed.replaceExisting),
      mixSettings: parsed.mixSettings
        ? {
            duckingVolume: Number(parsed.mixSettings.duckingVolume),
            narrationVolume: Number(parsed.mixSettings.narrationVolume),
            fadeDurationMs: Number(parsed.mixSettings.fadeDurationMs)
          }
        : null,
      segments: parsed.segments.map((segment, index) => ({
        index: Number(segment.index ?? index),
        startSeconds: Number(segment.startSeconds),
        endSeconds: Number(segment.endSeconds),
        text: String(segment.text ?? ''),
        voice: segment.voice ? String(segment.voice) : null
      }))
    };
  } catch {
    return null;
  }
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
  onPublish: (includeBurnedVideo: boolean, releaseNotes: string) => void;
  onSave: (segments: Array<{
    index: number;
    text: string;
    decision: SubtitleReviewDecision;
    issueCategories: SubtitleReviewIssueCategory[];
    reviewerNote: string | null;
  }>) => void;
  status: string | null;
}) {
  const [draftTextByIndex, setDraftTextByIndex] = useState<Record<number, string>>({});
  const [decisionByIndex, setDecisionByIndex] = useState<Record<number, SubtitleReviewDecision>>({});
  const [categoriesByIndex, setCategoriesByIndex] = useState<Record<number, SubtitleReviewIssueCategory[]>>({});
  const [noteByIndex, setNoteByIndex] = useState<Record<number, string>>({});
  const [includeReviewedBurnedVideo, setIncludeReviewedBurnedVideo] = useState(false);
  const [releaseNotes, setReleaseNotes] = useState('');

  useEffect(() => {
    if (!draft) {
      setDraftTextByIndex({});
      setDecisionByIndex({});
      setCategoriesByIndex({});
      setNoteByIndex({});
      return;
    }
    setDraftTextByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.draftText])));
    setDecisionByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.decision])));
    setCategoriesByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.issueCategories])));
    setNoteByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.reviewerNote ?? ''])));
  }, [draft]);

  const dirtySegments = useMemo(() => {
    if (!draft) {
      return [];
    }
    return draft.segments
      .filter((segment) => {
        const text = draftTextByIndex[segment.index] ?? segment.draftText;
        const decision = decisionByIndex[segment.index] ?? segment.decision;
        const categories = categoriesByIndex[segment.index] ?? segment.issueCategories;
        const note = noteByIndex[segment.index] ?? segment.reviewerNote ?? '';
        return text !== segment.draftText
          || decision !== segment.decision
          || categories.join('|') !== segment.issueCategories.join('|')
          || note !== (segment.reviewerNote ?? '');
      })
      .map((segment) => ({
        index: segment.index,
        text: draftTextByIndex[segment.index] ?? segment.draftText,
        decision: decisionByIndex[segment.index] ?? segment.decision,
        issueCategories: categoriesByIndex[segment.index] ?? segment.issueCategories,
        reviewerNote: (noteByIndex[segment.index] ?? '').trim() || null
      }));
  }, [categoriesByIndex, decisionByIndex, draft, draftTextByIndex, noteByIndex]);

  const handleReset = useCallback(() => {
    if (!draft) {
      return;
    }
    setDraftTextByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.draftText])));
    setDecisionByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.decision])));
    setCategoriesByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.issueCategories])));
    setNoteByIndex(Object.fromEntries(draft.segments.map((segment) => [segment.index, segment.reviewerNote ?? ''])));
  }, [draft]);

  const toggleCategory = useCallback((index: number, category: SubtitleReviewIssueCategory) => {
    setCategoriesByIndex((current) => {
      const existing = current[index] ?? [];
      const next = existing.includes(category)
        ? existing.filter((value) => value !== category)
        : [...existing, category];
      return { ...current, [index]: next };
    });
  }, []);

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
          <dt>Reviewed</dt>
          <dd>{draft.reviewedSegmentCount}</dd>
        </div>
        <div>
          <dt>Follow-up</dt>
          <dd>{draft.followupSegmentCount}</dd>
        </div>
        <div>
          <dt>Notes</dt>
          <dd>{draft.reviewerNoteCount}</dd>
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
        <label className="stacked-field compact-field">
          <span>Release notes</span>
          <textarea
            aria-label="Release notes"
            maxLength={1000}
            rows={2}
            value={releaseNotes}
            onChange={(event) => setReleaseNotes(event.target.value)}
          />
        </label>
        <button
          type="button"
          onClick={() => onPublish(includeReviewedBurnedVideo, releaseNotes)}
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
            <th>Review</th>
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
              <td>
                <label className="stacked-field compact-field">
                  <span>Decision</span>
                  <select
                    aria-label={`Review decision ${segment.index}`}
                    value={decisionByIndex[segment.index] ?? segment.decision}
                    onChange={(event) =>
                      setDecisionByIndex((current) => ({
                        ...current,
                        [segment.index]: event.target.value as SubtitleReviewDecision
                      }))
                    }
                  >
                    {SUBTITLE_REVIEW_DECISIONS.map((decision) => (
                      <option key={decision} value={decision}>{decision}</option>
                    ))}
                  </select>
                </label>
                <div className="checkbox-grid" aria-label={`Issue categories ${segment.index}`}>
                  {SUBTITLE_REVIEW_ISSUE_CATEGORIES.map((category) => (
                    <label key={category} className="inline-checkbox">
                      <input
                        type="checkbox"
                        checked={(categoriesByIndex[segment.index] ?? segment.issueCategories).includes(category)}
                        onChange={() => toggleCategory(segment.index, category)}
                      />
                      {category}
                    </label>
                  ))}
                </div>
                <textarea
                  aria-label={`Reviewer note ${segment.index}`}
                  maxLength={500}
                  value={noteByIndex[segment.index] ?? ''}
                  onChange={(event) =>
                    setNoteByIndex((current) => ({
                      ...current,
                      [segment.index]: event.target.value
                    }))
                  }
                  rows={2}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function SubtitleReviewEvidencePanel({
  evidence,
  error,
  jobId
}: {
  evidence: SubtitleReviewEvidence | null;
  error: string | null;
  jobId: string;
}) {
  const [downloadStatus, setDownloadStatus] = useState<string | null>(null);

  async function handleDownloadMarkdown() {
    setDownloadStatus(null);
    try {
      const blob = await linguaFrameApi.downloadSubtitleReviewEvidenceMarkdown(jobId);
      downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-subtitle-review-evidence.md`);
      setDownloadStatus('Markdown downloaded.');
    } catch (downloadError) {
      setDownloadStatus(toErrorMessage(downloadError));
    }
  }

  async function handleDownloadZip() {
    setDownloadStatus(null);
    try {
      const blob = await linguaFrameApi.downloadSubtitleReviewEvidenceZip(jobId);
      downloadBlob(blob, `linguaframe-job-${sanitizeFilename(jobId)}-subtitle-review-evidence.zip`);
      setDownloadStatus('ZIP downloaded.');
    } catch (downloadError) {
      setDownloadStatus(toErrorMessage(downloadError));
    }
  }

  if (!evidence) {
    return (
      <section id="subtitle-review-evidence" className="panel" aria-label="Review evidence">
        <h3>Subtitle review evidence</h3>
        {error ? <p className="muted">{error}</p> : <p className="muted">No subtitle review evidence loaded yet.</p>}
      </section>
    );
  }

  return (
    <section id="subtitle-review-evidence" className="panel" aria-label="Review evidence">
      <div className="panel-heading">
        <h3>Subtitle review evidence</h3>
        <span className={`status-pill ${evidence.status === 'READY' ? 'success' : evidence.status === 'BLOCKED' ? 'danger' : 'warning'}`}>
          {evidence.status}
        </span>
      </div>
      <dl className="status-grid compact-status-grid">
        <div>
          <dt>Reviewed</dt>
          <dd>{evidence.reviewedSegmentCount} / {evidence.segmentCount}</dd>
        </div>
        <div>
          <dt>Accepted</dt>
          <dd>{evidence.acceptedSegmentCount}</dd>
        </div>
        <div>
          <dt>Edited</dt>
          <dd>{evidence.editedDecisionCount}</dd>
        </div>
        <div>
          <dt>Follow-up</dt>
          <dd>{evidence.followupSegmentCount}</dd>
        </div>
        <div>
          <dt>Categories</dt>
          <dd>{evidence.annotationCount}</dd>
        </div>
        <div>
          <dt>Notes</dt>
          <dd>{evidence.reviewerNoteCount}</dd>
        </div>
      </dl>
      <p className="mode-line">{evidence.summary}</p>
      <div className="panel-actions">
        <button type="button" className="secondary-button" onClick={handleDownloadMarkdown}>
          Download review Markdown
        </button>
        <button type="button" className="secondary-button" onClick={handleDownloadZip}>
          Download review ZIP
        </button>
      </div>
      {downloadStatus ? <p className="mode-line">{downloadStatus}</p> : null}
      <dl className="status-grid compact-status-grid">
        {evidence.decisionCounts.map((count) => (
          <div key={count.category}>
            <dt>{count.category}</dt>
            <dd>{count.count}</dd>
          </div>
        ))}
        {evidence.issueCategoryCounts.map((count) => (
          <div key={count.category}>
            <dt>{count.category}</dt>
            <dd>{count.count}</dd>
          </div>
        ))}
      </dl>
      <ul className="compact-list">
        {evidence.checks.map((check) => (
          <li key={check.key}>
            <strong>{check.status}</strong> {check.label}: {check.detail}
          </li>
        ))}
      </ul>
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

function downloadBlob(blob: Blob, filename: string) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(objectUrl);
}

function safeMarkdownLine(value: unknown): string {
  return String(value ?? '')
    .replace(/\r?\n/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
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
      key: 'narration-audio',
      label: 'Narration audio',
      playerLabel: 'Narration audio player',
      kind: 'audio',
      artifact: findFirstArtifact(artifacts, 'NARRATION_AUDIO')
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
      key: 'narrated-video',
      label: 'Narrated video',
      playerLabel: 'Narrated video player',
      kind: 'video',
      artifact: findFirstArtifact(artifacts, 'NARRATED_VIDEO')
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

function formatLedgerCost(value: string | number): string {
  const numericValue = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(numericValue) ? formatCost(numericValue) : '$0.00000000';
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

function formatShortHash(hash: string | null | undefined): string {
  const normalized = hash?.trim();
  if (!normalized) {
    return 'none';
  }
  return normalized.length <= 16 ? normalized : `${normalized.slice(0, 12)}...${normalized.slice(-8)}`;
}

function sourceReuseJobReadiness(status: LocalizationJobStatus): DemoUploadReadiness['overallStatus'] {
  if (status === 'COMPLETED') {
    return 'READY';
  }
  if (status === 'FAILED' || status === 'CANCELLED') {
    return 'BLOCKED';
  }
  return 'ATTENTION';
}

function sourceReuseDecisionReadiness(status: string): DemoUploadReadiness['overallStatus'] {
  if (status === 'UPLOAD_NEW_SOURCE' || status === 'REUSE_COMPLETED_RUN') {
    return 'READY';
  }
  if (status === 'WAIT_FOR_ACTIVE_RUN' || status === 'REVIEW_DUPLICATES') {
    return 'ATTENTION';
  }
  return 'BLOCKED';
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

function formatOpenAiStage(stage: string): string {
  const labels: Record<string, string> = {
    transcription: 'Transcription',
    translation: 'Translation',
    evaluation: 'Quality evaluation',
    tts: 'TTS'
  };
  return labels[stage] ?? stage;
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

function formatDateTime(value: string | null): string {
  if (!value) {
    return 'N/A';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toISOString();
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
