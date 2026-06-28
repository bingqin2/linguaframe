export interface MediaUpload {
  videoId: string;
  jobId: string;
  filename: string;
  contentType: string;
  fileSizeBytes: number;
  sourceObjectKey: string;
  status: string;
  jobStatus: LocalizationJobStatus;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  createdAt: string;
}

export type MediaUploadValidationCode =
  | 'READY'
  | 'MISSING_FILE'
  | 'EMPTY_FILE'
  | 'UNSUPPORTED_CONTENT_TYPE'
  | 'FILE_TOO_LARGE'
  | 'UNREADABLE_MEDIA'
  | 'DURATION_TOO_LONG';

export interface MediaUploadValidation {
  valid: boolean;
  code: MediaUploadValidationCode;
  message: string;
  filename: string | null;
  contentType: string | null;
  fileSizeBytes: number;
  maxFileSizeBytes: number;
  durationSeconds: number | null;
  maxDurationSeconds: number;
  supportedContentTypes: string[];
}

export interface MediaUploadDetail {
  videoId: string;
  filename: string;
  contentType: string;
  fileSizeBytes: number;
  durationSeconds: number | null;
  status: string;
  createdAt: string;
}

export type DemoSessionMode = 'OPEN' | 'OWNER_SESSION_ACTIVE' | 'OWNER_SESSION_REQUIRED';

export interface DemoSessionStatus {
  accessGateEnabled: boolean;
  authenticated: boolean;
  headerName: string;
  mode: DemoSessionMode;
}

export type LocalizationJobStatus =
  | 'QUEUED'
  | 'RETRYING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface LocalizationJob {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  status: LocalizationJobStatus;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  failureStage: LocalizationJobStage | null;
  failureReason: string | null;
  retryCount: number;
  dispatchStatus: 'PENDING' | 'DISPATCHED' | 'FAILED' | null;
  dispatchAttempts: number;
  dispatchedAt: string | null;
  timelineEvents: JobTimelineEvent[];
  usageSummary: JobUsageSummary | null;
  cacheSummary: JobCacheSummary;
  modelCalls: ModelCall[];
  qualityEvaluation: QualityEvaluation | null;
  failureTriage: FailureTriage | null;
  pipelineProgress: JobPipelineProgress | null;
}

export type FailureTriageCategory =
  | 'CONFIGURATION'
  | 'OPENAI_AUTH_OR_MODEL'
  | 'OPENAI_TIMEOUT_OR_NETWORK'
  | 'BUDGET_GUARD'
  | 'MEDIA_PROCESSING'
  | 'STORAGE_OR_ARTIFACT'
  | 'WORKER_OR_QUEUE'
  | 'USER_CANCELLED'
  | 'UNKNOWN';

export interface FailureTriage {
  category: FailureTriageCategory;
  summary: string;
  recommendedAction: string;
  retryable: boolean;
  runbookCommand: string | null;
  safeDetails: string[];
}

export interface JobPipelineProgress {
  totalStageCount: number;
  completedStageCount: number;
  failedStageCount: number;
  skippedStageCount: number;
  cacheHitStageCount: number;
  currentStage: LocalizationJobStage | null;
  terminal: boolean;
  totalMeasuredDurationMs: number;
  slowestStage: LocalizationJobStage | null;
  slowestStageDurationMs: number | null;
  stages: JobStageProgress[];
}

export interface JobStageProgress {
  stage: LocalizationJobStage;
  status: JobTimelineEvent['status'] | null;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  message: string | null;
}

export interface LocalizationJobSummary {
  jobId: string;
  videoId: string;
  filename: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  status: LocalizationJobStatus;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  failureStage: LocalizationJobStage | null;
  failureReason: string | null;
  retryCount: number;
  estimatedCostUsd: number;
}

export interface LocalizationJobList {
  jobs: LocalizationJobSummary[];
  limit: number;
  offset: number;
  total: number;
}

export type LocalizationJobStage =
  | 'WORKER_RECEIVED'
  | 'WORKER_SMOKE'
  | 'AUDIO_EXTRACTION'
  | 'TRANSCRIPT_SUBTITLE_EXPORT'
  | 'TARGET_SUBTITLE_EXPORT'
  | 'TRANSLATION_QUALITY_EVALUATION'
  | 'DUBBING_AUDIO_GENERATION'
  | 'SUBTITLE_BURN_IN'
  | 'ARTIFACT_SUMMARY'
  | 'COMPLETED';

export interface JobTimelineEvent {
  id: string;
  stage: LocalizationJobStage;
  status: 'STARTED' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'CACHE_HIT';
  message: string;
  durationMs: number | null;
  errorSummary: string | null;
  occurredAt: string;
}

export interface JobUsageSummary {
  modelCallCount: number;
  failedModelCallCount: number;
  totalLatencyMs: number;
  estimatedCostUsd: number;
  inputTokens: number | null;
  outputTokens: number | null;
  audioSeconds: number | null;
  characterCount: number | null;
}

export interface JobCacheSummary {
  cacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
}

export interface ModelCall {
  modelCallId: string;
  jobId: string;
  stage: LocalizationJobStage;
  operation: 'TRANSCRIPTION' | 'TRANSLATION' | 'EVALUATION' | 'TTS';
  provider: 'DEMO' | 'OPENAI';
  model: string;
  promptVersion: string;
  status: 'SUCCEEDED' | 'FAILED';
  latencyMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  audioSeconds: number | null;
  characterCount: number | null;
  inputSummary: string | null;
  outputSummary: string | null;
  estimatedCostUsd: number;
  safeErrorSummary: string | null;
  createdAt: string;
}

export interface QualityEvaluation {
  evaluationId: string;
  jobId: string;
  language: string;
  score: number;
  verdict: string;
  completeness: number;
  readability: number;
  timingPreservation: number;
  naturalness: number;
  issues: string[];
  suggestedFixes: string[];
  status: 'SUCCEEDED' | 'FAILED';
  safeErrorSummary: string | null;
  createdAt: string;
}

export interface PromptTemplate {
  version: string;
  purpose: 'SUBTITLE_TRANSLATION' | 'TRANSLATION_QUALITY_EVALUATION';
  provider: string;
  modelFamily: string;
  systemPrompt: string;
  outputContract: string;
  active: boolean;
}

export interface OperatorDashboard {
  statusCounts: OperatorJobStatusCount[];
  recentFailures: OperatorRecentFailure[];
  modelCalls: OperatorModelCallSummary;
  cache: OperatorCacheSummary;
  stageTimings: OperatorStageTiming[];
}

export interface OperatorJobStatusCount {
  status: LocalizationJobStatus;
  count: number;
}

export interface OperatorRecentFailure {
  jobId: string;
  videoId: string;
  filename: string;
  failureStage: LocalizationJobStage | null;
  failureReason: string | null;
  failedAt: string;
}

export interface OperatorModelCallSummary {
  modelCallCount: number;
  failedModelCallCount: number;
  totalLatencyMs: number;
  estimatedCostUsd: number;
}

export interface OperatorCacheSummary {
  artifactCacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
}

export interface OperatorStageTiming {
  stage: LocalizationJobStage;
  completedEventCount: number;
  failedEventCount: number;
  averageDurationMs: number;
  maxDurationMs: number;
  latestDurationMs: number;
}

export interface RetentionCleanupResult {
  dryRun: boolean;
  candidateJobCount: number;
  deletedJobCount: number;
  deletedVideoCount: number;
  deletedObjectCount: number;
  skippedObjectCount: number;
  failureCount: number;
}

export interface PrivateDemoOperations {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  readyCount: number;
  attentionCount: number;
  blockedCount: number;
  sections: PrivateDemoOperationsSection[];
  commands: PrivateDemoOperationsCommand[];
  documentationLinks: PrivateDemoOperationsLink[];
}

export type PrivateDemoOperationsStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface PrivateDemoOperationsSection {
  title: string;
  status: PrivateDemoOperationsStatus;
  checks: PrivateDemoOperationsCheck[];
}

export interface PrivateDemoOperationsCheck {
  label: string;
  status: PrivateDemoOperationsStatus;
  detail: string;
  nextAction: string;
}

export interface PrivateDemoOperationsCommand {
  label: string;
  command: string;
  detail: string;
}

export interface PrivateDemoOperationsLink {
  label: string;
  path: string;
  detail: string;
}

export interface RuntimeDependencySummary {
  runtime: RuntimeContract;
  database: NetworkDependency;
  redis: NetworkDependency;
  rabbitmq: NetworkDependency;
  storage: StorageDependency;
  readiness: DemoReadiness;
}

export interface RuntimeLiveCheckSummary {
  healthy: boolean;
  checkedAt: string;
  checks: Record<RuntimeLiveCheckName, RuntimeProbeResult>;
}

export type RuntimeLiveCheckName = 'database' | 'redis' | 'rabbitmq' | 'minio' | 'ffmpeg' | 'openai';

export interface RuntimeProbeResult {
  status: RuntimeProbeStatus;
  latencyMs: number;
  message: string;
}

export type RuntimeProbeStatus = 'UP' | 'DOWN' | 'SKIPPED';

export interface RuntimeContract {
  appVersion: string;
  latestMigrationVersion: number;
  requiredRoutes: string[];
}

export interface NetworkDependency {
  type: string;
  host: string;
  port: number;
}

export interface StorageDependency {
  type: string;
  endpoint: string;
  bucket: string;
}

export interface DemoReadiness {
  demoAccessGate: boolean;
  worker: WorkerReadiness;
  media: MediaReadiness;
  ffmpeg: FfmpegReadiness;
  budget: BudgetReadiness;
  providers: Record<'transcription' | 'translation' | 'tts' | 'evaluation', ProviderReadiness>;
  features: Record<
    | 'jobStatusCache'
    | 'uploadRateLimit'
    | 'retentionCleanup'
    | 'costTracking'
    | 'budgetGuard'
    | 'dailyBudgetGuard',
    RuntimeFeatureFlag
  >;
}

export interface WorkerReadiness {
  dispatchEnabled: boolean;
  executionEnabled: boolean;
  role: 'COMBINED' | 'FFMPEG' | 'OPENAI';
  maxRetries: number;
  dispatchBatchSize: number;
  dispatchIntervalMs: number;
}

export interface MediaReadiness {
  maxFileSizeMb: number;
  maxDurationSeconds: number;
}

export interface FfmpegReadiness {
  audioEnabled: boolean;
  burnInEnabled: boolean;
  binaryConfigured: boolean;
  workspaceConfigured: boolean;
  audioTimeoutSeconds: number;
  burnInTimeoutSeconds: number;
}

export interface BudgetReadiness {
  enabled: boolean;
  maxJobCostUsd: number;
  dailyBudgetGuardEnabled: boolean;
  maxDailyCostUsd: number;
  budgetIdentity: string;
  estimatedCostTrackingEnabled: boolean;
}

export interface ProviderReadiness {
  enabled: boolean;
  provider: string;
  model: string;
  credentialsConfigured: boolean;
}

export interface RuntimeFeatureFlag {
  enabled: boolean;
}

export interface JobArtifact {
  artifactId: string;
  jobId: string;
  type:
    | 'EXTRACTED_AUDIO'
    | 'TRANSCRIPT_JSON'
    | 'SUBTITLE_SRT'
    | 'SUBTITLE_VTT'
    | 'TARGET_SUBTITLE_JSON'
    | 'TARGET_SUBTITLE_SRT'
    | 'TARGET_SUBTITLE_VTT'
    | 'REVIEWED_SUBTITLE_JSON'
    | 'REVIEWED_SUBTITLE_SRT'
    | 'REVIEWED_SUBTITLE_VTT'
    | 'DUBBING_AUDIO'
    | 'BURNED_VIDEO'
    | 'REVIEWED_BURNED_VIDEO'
    | 'WORKER_SUMMARY';
  filename: string;
  contentType: string;
  sizeBytes: number;
  contentSha256: string;
  cacheHit: boolean;
  sourceArtifactId: string | null;
  createdAt: string;
}

export interface TranscriptSegment {
  index: number;
  startMs: number;
  endMs: number;
  text: string;
}

export interface SubtitleSegment {
  language: string;
  index: number;
  startMs: number;
  endMs: number;
  text: string;
}

export type SubtitleReviewSegmentStatus = 'ALIGNED' | 'MISSING_TARGET' | 'TIMING_MISMATCH';

export interface SubtitleReviewSummary {
  jobId: string;
  targetLanguage: string;
  segmentCount: number;
  missingTargetCount: number;
  timingMismatchCount: number;
  averageDurationMs: number;
  maxDurationMs: number;
  qualityScore: number | null;
  qualityVerdict: string | null;
  qualityIssueCount: number;
  qualitySuggestedFixCount: number;
  downloadableSubtitleArtifactCount: number;
  segments: SubtitleReviewSegment[];
}

export interface SubtitleDraftSummary {
  jobId: string;
  targetLanguage: string;
  segmentCount: number;
  editedSegmentCount: number;
  lastUpdatedAt: string | null;
  segments: SubtitleDraftSegment[];
}

export interface SubtitleDraftSegment {
  index: number;
  startMs: number;
  endMs: number;
  sourceText: string;
  generatedText: string;
  draftText: string;
  edited: boolean;
  updatedAt: string | null;
}

export interface UpdateSubtitleDraftRequest {
  segments: Array<{
    index: number;
    text: string;
  }>;
}

export interface PublishReviewedSubtitlesRequest {
  language: string;
  includeBurnedVideo: boolean;
}

export interface ReviewedSubtitlePublish {
  jobId: string;
  targetLanguage: string;
  burnedVideoRequested: boolean;
  burnedVideoCreated: boolean;
  artifacts: JobArtifact[];
}

export interface DeliveryManifestArtifact {
  artifactId: string;
  type: JobArtifact['type'];
  filename: string;
  contentType: string;
  sizeBytes: number;
  shortSha256: string;
  cacheState: string;
  role: string;
  downloadUrl: string;
}

export interface DeliveryManifestLink {
  label: string;
  kind: string;
  url: string;
}

export interface DeliveryManifest {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  status: LocalizationJobStatus;
  generatedAt: string;
  handoffReady: boolean;
  reviewedSubtitleArtifactCount: number;
  reviewedBurnedVideoAvailable: boolean;
  generatedArtifactCount: number;
  reviewedArtifacts: DeliveryManifestArtifact[];
  auditArtifacts: DeliveryManifestArtifact[];
  links: DeliveryManifestLink[];
}

export interface SubtitleReviewSegment {
  index: number;
  startMs: number;
  endMs: number;
  sourceText: string;
  targetText: string | null;
  durationMs: number;
  timingDeltaMs: number;
  status: SubtitleReviewSegmentStatus;
}
