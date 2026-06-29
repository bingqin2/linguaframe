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
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
  createdAt: string;
}

export interface DemoRunProfile {
  id: string;
  label: string;
  description: string;
  targetLanguage: string;
  ttsVoice: string;
  translationStyle: string;
  subtitleStylePreset: string;
  subtitlePolishingMode: string;
  translationGlossary: string;
}

export interface DemoRunVarianceMetric {
  id: string;
  label: string;
  status: string;
  estimatedValue: string;
  actualValue: string;
  detail: string;
}

export interface DemoRunVarianceReport {
  jobId: string;
  videoId: string;
  generatedAt: string;
  overallStatus: string;
  baselineMode: string;
  jobStatus: string;
  targetLanguage: string;
  demoProfileId: string | null;
  recommendedNextAction: string;
  metrics: DemoRunVarianceMetric[];
  notes: string[];
  safeLinks: string[];
  safetyNotes: string[];
}

export interface DemoEvidenceClosureSection {
  key: string;
  title: string;
  status: string;
  summary: string;
  facts: string[];
  links: string[];
}

export interface DemoEvidenceClosurePackage {
  jobId: string;
  videoId: string;
  generatedAt: string;
  closureStatus: string;
  baselineMode: string;
  jobStatus: string;
  targetLanguage: string;
  demoProfileId: string | null;
  recommendedNextAction: string;
  varianceReport: DemoRunVarianceReport;
  sections: DemoEvidenceClosureSection[];
  safeLinks: string[];
  safetyNotes: string[];
}

export type OpenAiSmokeProofStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface OpenAiSmokeProofCheck {
  name: string;
  status: OpenAiSmokeProofStatus;
  detail: string;
  nextAction: string;
}

export interface OpenAiSmokeProofCall {
  stage: string;
  operation: string;
  provider: string;
  model: string | null;
  promptVersion: string | null;
  status: string;
  latencyMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  audioSeconds: number | null;
  characterCount: number | null;
  estimatedCostUsd: number | null;
  safeErrorSummary: string | null;
}

export interface OpenAiSmokeProofArtifact {
  artifactId: string;
  type: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  contentSha256: string | null;
  cacheHit: boolean;
  createdAt: string | null;
}

export interface OpenAiSmokeProofLink {
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export interface OpenAiSmokeProof {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  overallStatus: OpenAiSmokeProofStatus;
  phase: string;
  recommendedNextAction: string;
  completedAt: string | null;
  requiredChecks: OpenAiSmokeProofCheck[];
  optionalChecks: OpenAiSmokeProofCheck[];
  modelCalls: OpenAiSmokeProofCall[];
  artifacts: OpenAiSmokeProofArtifact[];
  safeLinks: OpenAiSmokeProofLink[];
  safetyNotes: string[];
}

export type DemoReviewerWorkspaceStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface DemoReviewerWorkspaceSection {
  key: string;
  title: string;
  status: string;
  facts: string[];
}

export interface DemoReviewerWorkspaceCheck {
  key: string;
  label: string;
  status: DemoReviewerWorkspaceStatus;
  detail: string;
  nextAction: string;
  required: boolean;
}

export interface DemoReviewerWorkspaceLink {
  kind: string;
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export interface DemoReviewerWorkspace {
  jobId: string;
  videoId: string;
  generatedAt: string;
  overallStatus: DemoReviewerWorkspaceStatus;
  phase: string;
  recommendedNextAction: string;
  completedAt: string | null;
  targetLanguage: string;
  demoProfileId: string | null;
  sections: DemoReviewerWorkspaceSection[];
  checks: DemoReviewerWorkspaceCheck[];
  safeLinks: DemoReviewerWorkspaceLink[];
  packageEntries: string[];
  safetyNotes: string[];
}

export type DemoHandoffPortalStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface DemoHandoffPortalSection {
  key: string;
  title: string;
  status: string;
  facts: string[];
}

export interface DemoHandoffPortalCheck {
  key: string;
  label: string;
  status: DemoHandoffPortalStatus;
  detail: string;
  nextAction: string;
  required: boolean;
}

export interface DemoHandoffPortalLink {
  kind: string;
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export interface DemoHandoffPortal {
  jobId: string;
  videoId: string;
  generatedAt: string;
  overallStatus: DemoHandoffPortalStatus;
  phase: string;
  headline: string;
  recommendedNextAction: string;
  completedAt: string | null;
  targetLanguage: string;
  demoProfileId: string | null;
  checks: DemoHandoffPortalCheck[];
  sections: DemoHandoffPortalSection[];
  safeLinks: DemoHandoffPortalLink[];
  packageEntries: string[];
  safetyNotes: string[];
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

export interface OwnerQuotaLimit {
  name: string;
  enabled: boolean;
  limit: number;
  current: number;
}

export interface OwnerQuotaPreflight {
  ownerId: string;
  enabled: boolean;
  allowed: boolean;
  activeJobs: number;
  queuedJobs: number;
  dailyEstimatedCostUsd: number;
  dailyBudgetDate: string;
  limits: OwnerQuotaLimit[];
  blockingReasons: string[];
}

export interface UploadCostEstimateStage {
  id: string;
  label: string;
  status: string;
  provider: string;
  model: string;
  paidProviderCall: boolean;
  estimatedCostUsd: number;
  basis: string;
  detail: string;
}

export interface UploadCostEstimateBudget {
  id: string;
  label: string;
  enabled: boolean;
  status: DemoUploadReadinessStatus;
  currentUsd: number;
  estimateUsd: number;
  projectedUsd: number;
  limitUsd: number;
  detail: string;
}

export interface UploadCostEstimate {
  overallStatus: DemoUploadReadinessStatus;
  recommendedNextAction: string;
  filename: string | null;
  contentType: string | null;
  fileSizeBytes: number;
  maxFileSizeBytes: number;
  durationSeconds: number | null;
  maxDurationSeconds: number;
  valid: boolean;
  validationCode: MediaUploadValidationCode;
  validationMessage: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
  estimatedCostUsdLower: number;
  estimatedCostUsd: number;
  estimatedCostUsdUpper: number;
  stages: UploadCostEstimateStage[];
  budgets: UploadCostEstimateBudget[];
  cacheNotes: string[];
  safetyNotes: string[];
}

export interface UploadExecutionPlanStage {
  id: string;
  label: string;
  status: string;
  executionType: 'LOCAL' | 'PAID' | 'DISABLED' | string;
  provider: string;
  model: string;
  runnable: boolean;
  estimatedCostUsd: number;
  estimatedDurationSecondsLower: number;
  estimatedDurationSecondsUpper: number;
  detail: string;
}

export interface UploadExecutionPlanGate {
  id: string;
  label: string;
  status: DemoUploadReadinessStatus;
  blocking: boolean;
  detail: string;
  nextAction: string;
}

export interface UploadExecutionPlanCommand {
  id: string;
  label: string;
  command: string;
  description: string;
}

export interface UploadSourceReuseCandidate {
  videoId: string;
  jobId: string;
  originalFilename: string;
  durationSeconds: number | null;
  jobStatus: LocalizationJobStatus;
  demoProfileId: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  subtitlePolishingMode: string;
  createdAt: string;
  jobDetailHref: string | null;
  shareSheetHref: string | null;
  evidenceHref: string | null;
  demoRunPackageHref: string | null;
  acceptanceGateHref: string | null;
}

export interface UploadSourceReuse {
  sourceContentSha256: string | null;
  candidateCount: number;
  recommendedAction: 'UPLOAD_NEW_SOURCE' | 'REVIEW_EXISTING_COMPLETED_RUN' | 'WAIT_FOR_ACTIVE_RUN' | string;
  recommendedExistingJobId: string | null;
  candidates: UploadSourceReuseCandidate[];
}

export interface UploadSourceReuseDecisionAction {
  id: string;
  label: string;
  kind: string;
  enabled: boolean;
  detail: string;
  href: string | null;
}

export interface UploadSourceReuseDecisionLink {
  kind: string;
  label: string;
  href: string;
}

export interface UploadSourceReuseDecision {
  status: 'UPLOAD_NEW_SOURCE' | 'REUSE_COMPLETED_RUN' | 'WAIT_FOR_ACTIVE_RUN' | 'REVIEW_DUPLICATES' | string;
  headline: string;
  summary: string;
  recommendedAction: string;
  recommendedExistingJobId: string | null;
  candidateCount: number;
  actions: UploadSourceReuseDecisionAction[];
  links: UploadSourceReuseDecisionLink[];
  safetyNotes: string[];
  sourceReuse: UploadSourceReuse;
}

export interface UploadExecutionPlan {
  overallStatus: DemoUploadReadinessStatus;
  recommendedNextAction: string;
  filename: string | null;
  contentType: string | null;
  fileSizeBytes: number;
  maxFileSizeBytes: number;
  durationSeconds: number | null;
  maxDurationSeconds: number;
  valid: boolean;
  validationCode: MediaUploadValidationCode;
  validationMessage: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
  estimatedCostUsdLower: number;
  estimatedCostUsd: number;
  estimatedCostUsdUpper: number;
  estimatedDurationSecondsLower: number;
  estimatedDurationSecondsUpper: number;
  stages: UploadExecutionPlanStage[];
  gates: UploadExecutionPlanGate[];
  commands: UploadExecutionPlanCommand[];
  sourceReuse: UploadSourceReuse;
  sourceReuseDecision: UploadSourceReuseDecision;
  cacheNotes: string[];
  safetyNotes: string[];
}

export type DemoUploadReadinessStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface DemoUploadReadinessCheck {
  id: string;
  label: string;
  status: DemoUploadReadinessStatus;
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface DemoUploadReadiness {
  overallStatus: DemoUploadReadinessStatus;
  ownerId: string;
  demoProfileId: string;
  generatedAt: string;
  checks: DemoUploadReadinessCheck[];
  requiredActions: string[];
  evidenceRoutes: string[];
}

export interface DemoSampleMediaCatalog {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  uploadDurationLimitSeconds: number;
  recommendedSampleId: string;
  items: DemoSampleMediaItem[];
  configuredPaths: DemoSampleMediaConfiguredPath[];
  commands: DemoSampleMediaCommand[];
  notesMarkdown: string;
  documentationLinks: PrivateDemoOperationsLink[];
}

export interface DemoSampleMediaItem {
  id: string;
  title: string;
  source: string;
  sourceUrl: string;
  attribution: string;
  licenseGuidance: string;
  recommendedUse: string;
  durationGuidance: string;
  command: string;
  tags: string[];
}

export interface DemoSampleMediaConfiguredPath {
  envVar: string;
  status: 'CONFIGURED' | 'MISSING' | 'UNCONFIGURED';
  filename: string;
  extension: string;
  sizeBytes: number | null;
  message: string;
  fullPathExposed: boolean;
}

export interface DemoSampleMediaCommand {
  label: string;
  command: string;
  description: string;
}

export interface DemoRunLauncher {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  recommendedSampleId: string;
  recommendedProfileId: string;
  recommendedNextCommand: string;
  gates: DemoRunLauncherGate[];
  commands: DemoRunLauncherCommand[];
  expectedEvidence: DemoRunLauncherEvidence[];
  notesMarkdown: string;
}

export interface DemoRunLauncherGate {
  id: string;
  label: string;
  status: PrivateDemoOperationsStatus;
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface DemoRunLauncherCommand {
  label: string;
  command: string;
  description: string;
}

export interface DemoRunLauncherEvidence {
  label: string;
  path: string;
  description: string;
}

export interface DemoPresentationCockpit {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  phase: string;
  recommendedNextAction: string;
  selectedRun: DemoPresentationCockpitRun | null;
  activeRun: DemoPresentationCockpitRun | null;
  recommendedRun: DemoPresentationCockpitRun | null;
  checks: DemoPresentationCockpitCheck[];
  links: DemoPresentationCockpitLink[];
  safetyNotes: string[];
}

export interface DemoPresentationCockpitRun {
  jobId: string;
  videoId: string;
  profileId: string;
  status: LocalizationJobStatus;
  readiness: string;
  acceptanceStatus: string;
  attentionLevel: string;
  currentStage: string;
  elapsedMs: number | null;
  nextAction: string;
}

export interface DemoPresentationCockpitCheck {
  key: string;
  label: string;
  status: PrivateDemoOperationsStatus;
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface DemoPresentationCockpitLink {
  kind: string;
  label: string;
  url: string;
}

export type DemoSessionCommandCenterStatus = 'READY' | 'ATTENTION' | 'BLOCKED' | 'EMPTY';

export interface DemoSessionCommandCenter {
  generatedAt: string;
  overallStatus: DemoSessionCommandCenterStatus;
  phase: string;
  recommendedNextAction: string;
  primaryCommand: string;
  focusRun: DemoSessionCommandCenterRun | null;
  activeRun: DemoSessionCommandCenterRun | null;
  recommendedCompletedRun: DemoSessionCommandCenterRun | null;
  phases: DemoSessionCommandCenterPhase[];
  actions: DemoSessionCommandCenterAction[];
  evidenceLinks: DemoSessionCommandCenterEvidence[];
  estimatedCostUsd: string;
  modelCallCount: number;
  failedModelCallCount: number;
  failureRatePercent: string;
  averageLatencyMs: number;
  providerCacheHitCount: number;
  safetyNotes: string[];
}

export interface DemoSessionCommandCenterRun {
  role: string;
  jobId: string;
  videoId: string;
  profileId: string;
  status: LocalizationJobStatus;
  readiness: string;
  acceptanceStatus: string;
  currentStage: string | null;
  elapsedMs: number | null;
  nextAction: string;
}

export interface DemoSessionCommandCenterPhase {
  id: string;
  label: string;
  status: DemoSessionCommandCenterStatus;
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface DemoSessionCommandCenterAction {
  id: string;
  label: string;
  command: string;
  description: string;
  primary: boolean;
}

export interface DemoSessionCommandCenterEvidence {
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export type ModelUsageLedgerStatus = 'READY' | 'ATTENTION' | 'BLOCKED' | 'EMPTY';

export interface ModelUsageLedger {
  generatedAt: string;
  limit: number;
  ownerId: string;
  ownershipScope: string;
  summary: ModelUsageLedgerSummary;
  jobs: ModelUsageLedgerJob[];
  operations: ModelUsageLedgerOperation[];
  recentCalls: ModelUsageLedgerCall[];
  safeLinks: string[];
  safetyNotes: string[];
}

export interface ModelUsageLedgerSummary {
  ledgerStatus: ModelUsageLedgerStatus;
  jobCount: number;
  modelCallCount: number;
  failedModelCallCount: number;
  providerCacheHitCount: number;
  generatedArtifactCount: number;
  totalLatencyMs: number;
  estimatedCostUsd: string;
  averageLatencyMs: number;
  failureRatePercent: string;
  recommendedNextAction: string;
}

export interface ModelUsageLedgerJob {
  jobId: string;
  videoId: string;
  jobStatus: LocalizationJobStatus;
  targetLanguage: string;
  demoProfileId: string | null;
  modelCallCount: number;
  failedModelCallCount: number;
  providerCacheHitCount: number;
  generatedArtifactCount: number;
  totalLatencyMs: number;
  estimatedCostUsd: string;
  latestModelCallAt: string | null;
  safeLinks: string[];
}

export interface ModelUsageLedgerOperation {
  operation: string;
  provider: string;
  model: string;
  promptVersion: string;
  modelCallCount: number;
  failedModelCallCount: number;
  totalLatencyMs: number;
  estimatedCostUsd: string;
  averageLatencyMs: number;
}

export interface ModelUsageLedgerCall {
  modelCallId: string;
  jobId: string;
  videoId: string;
  stage: string;
  operation: string;
  provider: string;
  model: string;
  promptVersion: string;
  status: string;
  latencyMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  audioSeconds: string | null;
  characterCount: number | null;
  estimatedCostUsd: string;
  safeErrorSummary: string | null;
  createdAt: string;
}

export type ReviewedSubtitleWorkflowStatus = 'READY' | 'ATTENTION' | 'BLOCKED';

export interface ReviewedSubtitleWorkflow {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  generatedAt: string;
  overallStatus: ReviewedSubtitleWorkflowStatus;
  phase: string;
  recommendedNextAction: string;
  segmentCount: number;
  missingTargetCount: number;
  timingMismatchCount: number;
  qualityScore: number | null;
  qualityVerdict: string | null;
  qualityIssueCount: number;
  qualitySuggestedFixCount: number;
  editedSegmentCount: number;
  draftLastUpdatedAt: string | null;
  generatedSubtitleArtifactCount: number;
  reviewedSubtitleArtifactCount: number;
  reviewedBurnedVideoAvailable: boolean;
  handoffReady: boolean;
  checks: ReviewedSubtitleWorkflowCheck[];
  links: ReviewedSubtitleWorkflowLink[];
  safetyNotes: string[];
}

export interface ReviewedSubtitleWorkflowCheck {
  key: string;
  label: string;
  status: ReviewedSubtitleWorkflowStatus;
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface ReviewedSubtitleWorkflowLink {
  kind: string;
  label: string;
  url: string;
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
  ownerId: string;
  ownershipScope: string;
}

export type AuthSessionMode =
  | 'LOCAL_AUTH_DISABLED'
  | 'LOCAL_AUTH_REQUIRED'
  | 'LOCAL_AUTH_ACTIVE';

export interface AuthSessionStatus {
  enabled: boolean;
  configured: boolean;
  authenticated: boolean;
  ownerId: string;
  username: string;
  ownershipScope: string;
  authMode: AuthSessionMode;
}

export interface AuthLoginResponse {
  token: string;
  tokenType: 'Bearer';
  expiresAt: string;
  session: AuthSessionStatus;
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
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
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

export interface DemoRunMonitorStage {
  stage: LocalizationJobStage;
  status: JobTimelineEvent['status'];
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  runningForMs: number | null;
  attention: string;
  message: string | null;
}

export interface DemoRunMonitorLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoRunMonitor {
  jobId: string;
  videoId: string;
  status: LocalizationJobStatus;
  dispatchStatus: 'PENDING' | 'DISPATCHED' | 'FAILED' | null;
  generatedAt: string;
  elapsedMs: number | null;
  currentStage: LocalizationJobStage | null;
  completedStageCount: number;
  totalStageCount: number;
  failedStageCount: number;
  slowestStage: LocalizationJobStage | null;
  slowestStageDurationMs: number | null;
  attentionLevel: string;
  summary: string;
  recommendedNextAction: string;
  stages: DemoRunMonitorStage[];
  links: DemoRunMonitorLink[];
  markdown: string;
}

export interface DemoRunSnapshotSection {
  kind: string;
  title: string;
  status: string;
  filename: string;
  summary: string;
}

export interface DemoRunSnapshotLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoRunSnapshot {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  demoProfileId: string;
  generatedAt: string;
  readiness: string;
  headline: string;
  summary: string;
  sections: DemoRunSnapshotSection[];
  packageEntries: string[];
  links: DemoRunSnapshotLink[];
  exclusionPolicy: string[];
  markdown: string;
}

export interface LocalizationJobSummary {
  jobId: string;
  videoId: string;
  filename: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
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
  | 'SUBTITLE_POLISHING'
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
  operation: 'TRANSCRIPTION' | 'TRANSLATION' | 'SUBTITLE_POLISHING' | 'EVALUATION' | 'TTS';
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
  ownerId: string;
  ownershipScope: string;
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

export interface PrivateDemoLaunchRehearsal {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  readyCount: number;
  attentionCount: number;
  blockedCount: number;
  recommendedNextStepId: string;
  steps: PrivateDemoLaunchRehearsalStep[];
  evidenceDownloads: string[];
  rehearsalNotesMarkdown: string;
}

export interface PrivateDemoLaunchRehearsalStep {
  id: string;
  title: string;
  status: PrivateDemoOperationsStatus;
  detail: string;
  command: string;
  evidencePath: string;
  nextAction: string;
  blocking: boolean;
}

export interface PrivateDemoEvidenceGallery {
  generatedAt: string;
  overallStatus: PrivateDemoEvidenceGalleryStatus;
  completedJobCount: number;
  handoffReadyCount: number;
  recommendedJobId: string | null;
  jobs: PrivateDemoEvidenceGalleryJob[];
  galleryDownloads: PrivateDemoEvidenceGalleryDownload[];
  galleryNotesMarkdown: string;
}

export type PrivateDemoEvidenceGalleryStatus = 'READY' | 'ATTENTION' | 'EMPTY';

export interface PrivateDemoEvidenceGalleryJob {
  jobId: string;
  videoId: string;
  filename: string;
  targetLanguage: string;
  demoProfileId: string | null;
  status: LocalizationJobStatus;
  createdAt: string;
  completedAt: string;
  qualityScore: number | null;
  qualityVerdict: string | null;
  estimatedCostUsd: string;
  modelCallCount: number;
  providerCacheHitCount: number;
  handoffReady: boolean;
  presenterPackReady: boolean;
  recommended: boolean;
  attentionReasons: string[];
  downloads: PrivateDemoEvidenceGalleryDownload[];
}

export interface PrivateDemoEvidenceGalleryDownload {
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export interface PrivateDemoRunArchive {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus;
  recommendedJobId: string | null;
  recommendedVideoId: string | null;
  recommendedProfileId: string | null;
  recommendedReadiness: string;
  operationsOverallStatus: PrivateDemoOperationsStatus;
  launchOverallStatus: PrivateDemoOperationsStatus;
  launchRecommendedNextStep: string;
  galleryCompletedJobCount: number;
  galleryHandoffReadyCount: number;
  candidates: PrivateDemoRunArchiveCandidate[];
  archiveLinks: PrivateDemoRunArchiveLink[];
  archiveNotesMarkdown: string;
}

export interface PrivateDemoRunArchiveCandidate {
  jobId: string;
  videoId: string;
  filename: string;
  profileId: string;
  status: LocalizationJobStatus;
  readiness: string;
  qualityScore: number | null;
  estimatedCostUsd: string;
  modelCallCount: number;
  providerCacheHitCount: number;
  handoffReady: boolean;
  roles: string[];
}

export interface PrivateDemoRunArchiveLink {
  label: string;
  href: string;
  contentType: string;
  description: string;
}

export interface OpenAiReadinessEvidence {
  generatedAt: string;
  overallStatus: PrivateDemoOperationsStatus | 'SKIPPED';
  phase: string;
  recommendedNextAction: string;
  providers: OpenAiReadinessProvider[];
  liveCheck: OpenAiReadinessLiveCheck;
  readinessSignals: OpenAiReadinessSignal[];
  modelUsage: OpenAiReadinessModelUsage;
  commands: OpenAiReadinessCommand[];
  safeLinks: string[];
  safetyNotes: string[];
}

export interface OpenAiReadinessProvider {
  stage: string;
  enabled: boolean;
  provider: string;
  model: string | null;
  credentialsConfigured: boolean;
  status: PrivateDemoOperationsStatus | 'SKIPPED';
  detail: string;
  paidProvider: boolean;
}

export interface OpenAiReadinessLiveCheck {
  status: RuntimeProbeStatus | 'BLOCKED';
  latencyMs: number;
  message: string;
}

export interface OpenAiReadinessSignal {
  id: string;
  label: string;
  status: PrivateDemoOperationsStatus | 'SKIPPED';
  detail: string;
  nextAction: string;
  blocking: boolean;
}

export interface OpenAiReadinessModelUsage {
  ledgerStatus: string;
  modelCallCount: number;
  failedModelCallCount: number;
  failureRatePercent: string;
  estimatedCostUsd: string;
  recommendedNextAction: string;
}

export interface OpenAiReadinessCommand {
  label: string;
  command: string;
  description: string;
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
  ownerQuota: OwnerQuotaReadiness;
  providers: Record<'transcription' | 'translation' | 'tts' | 'evaluation', ProviderReadiness>;
  features: Record<
    | 'jobStatusCache'
    | 'uploadRateLimit'
    | 'retentionCleanup'
    | 'costTracking'
    | 'budgetGuard'
    | 'dailyBudgetGuard'
    | 'ownerQuota',
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
  listenerQueue: string;
  jobExchange: string;
  defaultJobQueue: string;
  defaultRoutingKey: string;
  ffmpegJobQueue: string;
  ffmpegRoutingKey: string;
  openaiJobQueue: string;
  openaiRoutingKey: string;
  ownedStageGroups: string[];
  recommendedCommands: string[];
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

export interface OwnerQuotaReadiness {
  enabled: boolean;
  maxActiveJobs: number;
  maxQueuedJobs: number;
  dailyBudgetGuardEnabled: boolean;
  maxDailyCostUsd: number;
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
    | 'NARRATION_AUDIO'
    | 'BURNED_VIDEO'
    | 'DUBBED_VIDEO'
    | 'NARRATED_VIDEO'
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

export interface NarrationSegment {
  index: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  text: string;
  voice: string | null;
  characterCount: number;
  updatedAt: string | null;
}

export interface NarrationWorkspace {
  jobId: string;
  status: string;
  segmentCount: number;
  totalDurationSeconds: number;
  totalCharacterCount: number;
  generationReady: boolean;
  mixSettings: NarrationMixSettings;
  voiceCatalog: NarrationVoiceCatalog;
  timeline: NarrationTimelineSummary;
  segments: NarrationSegment[];
  safetyNotes: string[];
}

export interface NarrationVoiceCatalog {
  provider: string;
  defaultVoice: string;
  presets: NarrationVoicePreset[];
  safetyNotes: string[];
}

export interface NarrationVoicePreset {
  voice: string;
  label: string;
  provider: string;
  defaultPreset: boolean;
  description: string;
}

export interface NarrationTimelineSummary {
  startSeconds: number;
  endSeconds: number;
  totalSpanSeconds: number;
  coveredSeconds: number;
  gapSeconds: number;
  gapCount: number;
  hasOverlap: boolean;
  generationReady: boolean;
  segments: NarrationTimelineSegment[];
}

export interface NarrationTimelineSegment {
  index: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  leftPercent: number;
  widthPercent: number;
  status: string;
  characterCount: number;
  voice: string;
}

export interface NarrationMixSettings {
  duckingVolume: number;
  narrationVolume: number;
  fadeDurationMs: number;
  updatedAt: string | null;
}

export interface SaveNarrationSegment {
  index: number;
  startSeconds: number;
  endSeconds: number;
  text: string;
  voice: string | null;
}

export interface SaveNarrationWorkspaceRequest {
  segments: SaveNarrationSegment[];
}

export interface UpdateNarrationMixSettingsRequest {
  duckingVolume: number;
  narrationVolume: number;
  fadeDurationMs: number;
}

export interface NarrationGeneration {
  jobId: string;
  artifactId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  segmentCount: number;
  totalCharacterCount: number;
  totalTimelineDurationSeconds: number;
  voiceSummary: string;
  audioLayout: string;
  timeAligned: boolean;
  ttsCallCount: number;
  status: string;
}

export interface NarratedVideoGeneration {
  jobId: string;
  artifactId: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  baseVideoType: string;
  narrationAudioArtifactId: string;
  mixMode: string;
  duckingVolume: number;
  narrationVolume: number;
  fadeDurationMs: number;
  narrationWindowCount: number;
  status: string;
}

export interface NarrationEvidenceCheck {
  key: string;
  label: string;
  status: string;
  detail: string;
}

export interface NarrationEvidenceLink {
  kind: string;
  label: string;
  href: string;
  contentType: string;
}

export interface NarrationEvidence {
  jobId: string;
  status: string;
  segmentCount: number;
  totalCharacterCount: number;
  totalTimelineDurationSeconds: number;
  timelineGapCount: number;
  timelineGapSeconds: number;
  timelineHasOverlap: boolean;
  voicePresetCount: number;
  voiceSummary: string;
  defaultVoice: string;
  narrationAudioReady: boolean;
  audioArtifactCount: number;
  audioLayout: string;
  timeAligned: boolean;
  narratedVideoReady: boolean;
  narratedVideoArtifactCount: number;
  mixMode: string;
  duckingVolume: number | null;
  narrationVolume: number | null;
  fadeDurationMs: number;
  mixSettingsSource: string | null;
  checks: NarrationEvidenceCheck[];
  safeLinks: NarrationEvidenceLink[];
  packageEntries: string[];
  safetyNotes: string[];
}

export interface NarrationScriptPackageSegment {
  index: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  text: string;
  voice: string | null;
  characterCount: number;
  updatedAt: string | null;
}

export interface NarrationScriptPackageCheck {
  key: string;
  label: string;
  status: string;
  detail: string;
}

export interface NarrationScriptPackageLink {
  kind: string;
  label: string;
  href: string;
  contentType: string;
}

export interface NarrationScriptPackage {
  jobId: string;
  targetLanguage: string;
  durationSeconds: number | null;
  status: string;
  segmentCount: number;
  totalCharacterCount: number;
  totalTimelineDurationSeconds: number;
  timelineGapCount: number;
  timelineGapSeconds: number;
  timelineHasOverlap: boolean;
  voiceSummary: string;
  defaultVoice: string;
  mixSettings: NarrationMixSettings;
  voiceCatalog: NarrationVoiceCatalog;
  segments: NarrationScriptPackageSegment[];
  checks: NarrationScriptPackageCheck[];
  safeLinks: NarrationScriptPackageLink[];
  packageEntries: string[];
  safetyNotes: string[];
}

export interface ImportNarrationScriptPackageSegmentRequest {
  index: number;
  startSeconds: number;
  endSeconds: number;
  text: string;
  voice: string | null;
}

export interface ImportNarrationScriptPackageRequest {
  replaceExisting: boolean;
  segments: ImportNarrationScriptPackageSegmentRequest[];
  mixSettings?: UpdateNarrationMixSettingsRequest | null;
}

export interface NarrationScriptPackageImportResult {
  jobId: string;
  importedSegmentCount: number;
  totalCharacterCount: number;
  voiceSummary: string;
  replacedExisting: boolean;
  warnings: string[];
  workspace: NarrationWorkspace;
}

export interface NarrationDemoPresetMixSettings {
  duckingVolume: number;
  narrationVolume: number;
  fadeDurationMs: number;
}

export interface NarrationDemoPresetSegment {
  index: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  text: string;
  characterCount: number;
  voice: string | null;
}

export interface NarrationDemoPreset {
  id: string;
  label: string;
  description: string;
  profileId: string;
  sampleIdHint: string;
  targetLanguage: string;
  voiceSummary: string;
  segmentCount: number;
  totalCharacterCount: number;
  timeSpanSeconds: number;
  mixSettings: NarrationDemoPresetMixSettings;
  segments: NarrationDemoPresetSegment[];
  safetyNotes: string[];
}

export interface ApplyNarrationDemoPresetRequest {
  presetId: string;
  replaceExisting: boolean;
}

export interface NarrationDemoPresetApplyResult {
  jobId: string;
  presetId: string;
  profileId: string;
  importedSegmentCount: number;
  totalCharacterCount: number;
  voiceSummary: string;
  replacedExisting: boolean;
  generatedMedia: boolean;
  workspace: NarrationWorkspace;
  scriptPackage: NarrationScriptPackage;
  narrationEvidenceStatus: string;
}

export type NarrationDemoRenderStatus = 'READY' | 'PARTIAL' | 'FAILED';
export type NarrationDemoRenderStepStatus = 'READY' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';

export interface RenderNarrationDemoRequest {
  presetId: string;
  replaceExisting: boolean;
  generateNarratedVideo: boolean;
}

export interface NarrationDemoRenderStep {
  key: string;
  label: string;
  status: NarrationDemoRenderStepStatus;
  message: string;
}

export interface NarrationDemoRenderResult {
  jobId: string;
  presetId: string;
  status: NarrationDemoRenderStatus;
  steps: NarrationDemoRenderStep[];
  presetApply: NarrationDemoPresetApplyResult | null;
  narrationAudio: NarrationGeneration | null;
  narratedVideo: NarratedVideoGeneration | null;
  scriptPackage: NarrationScriptPackage | null;
  narrationEvidence: NarrationEvidence | null;
  generatedArtifactCount: number;
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
  reviewedSegmentCount: number;
  acceptedSegmentCount: number;
  editedDecisionCount: number;
  followupSegmentCount: number;
  annotationCount: number;
  reviewerNoteCount: number;
  lastUpdatedAt: string | null;
  segments: SubtitleDraftSegment[];
}

export type SubtitleReviewDecision = 'UNREVIEWED' | 'ACCEPTED' | 'EDITED' | 'NEEDS_FOLLOWUP';

export type SubtitleReviewIssueCategory =
  | 'TERM'
  | 'TONE'
  | 'TIMING'
  | 'READABILITY'
  | 'MISSING_TEXT'
  | 'OTHER';

export interface SubtitleDraftSegment {
  index: number;
  startMs: number;
  endMs: number;
  sourceText: string;
  generatedText: string;
  draftText: string;
  edited: boolean;
  updatedAt: string | null;
  decision: SubtitleReviewDecision;
  issueCategories: SubtitleReviewIssueCategory[];
  reviewerNote: string | null;
  noteLength: number;
}

export interface UpdateSubtitleDraftRequest {
  segments: Array<{
    index: number;
    text: string;
    decision?: SubtitleReviewDecision;
    issueCategories?: SubtitleReviewIssueCategory[];
    reviewerNote?: string | null;
  }>;
}

export interface PublishReviewedSubtitlesRequest {
  language: string;
  includeBurnedVideo: boolean;
  releaseNotes?: string | null;
}

export interface ReviewedSubtitlePublish {
  jobId: string;
  targetLanguage: string;
  burnedVideoRequested: boolean;
  burnedVideoCreated: boolean;
  releaseNotesLength: number;
  reviewDecisionCounts: SubtitleReviewEvidenceCategory[];
  issueCategoryCounts: SubtitleReviewEvidenceCategory[];
  artifacts: JobArtifact[];
}

export interface SubtitleReviewEvidenceCategory {
  category: string;
  count: number;
}

export interface SubtitleReviewEvidenceCheck {
  key: string;
  label: string;
  status: string;
  detail: string;
}

export interface SubtitleReviewEvidence {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  generatedAt: string;
  status: string;
  summary: string;
  segmentCount: number;
  reviewedSegmentCount: number;
  acceptedSegmentCount: number;
  editedDecisionCount: number;
  followupSegmentCount: number;
  annotationCount: number;
  reviewerNoteCount: number;
  reviewedSubtitleArtifactCount: number;
  reviewedBurnedVideoAvailable: boolean;
  releaseNotesLength: number;
  decisionCounts: SubtitleReviewEvidenceCategory[];
  issueCategoryCounts: SubtitleReviewEvidenceCategory[];
  checks: SubtitleReviewEvidenceCheck[];
  links: ReviewedSubtitleWorkflowLink[];
  packageEntries: string[];
  safetyNotes: string[];
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
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  demoProfileId: string | null;
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

export interface JobComparisonSettingDiff {
  field: string;
  baselineValue: string;
  comparisonValue: string;
}

export interface JobComparisonDelta {
  qualityScore: number | null;
  modelCallCount: number;
  estimatedCostUsd: number;
  artifactCacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
}

export interface JobComparisonJob {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  demoProfileId: string | null;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  status: LocalizationJobStatus;
  qualityScore: number | null;
  qualityVerdict: string | null;
  modelCallCount: number;
  failedModelCallCount: number;
  estimatedCostUsd: number;
  artifactCacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
  handoffReady: boolean;
}

export interface JobComparison {
  baselineJobId: string;
  comparisonJobId: string;
  sameSourceVideo: boolean;
  generatedAt: string;
  baseline: JobComparisonJob;
  comparison: JobComparisonJob;
  delta: JobComparisonDelta;
  settingDiffs: JobComparisonSettingDiff[];
}

export interface DemoRunMatrixJob {
  jobId: string;
  videoId: string;
  filename: string;
  targetLanguage: string;
  demoProfileId: string | null;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  status: LocalizationJobStatus;
  createdAt: string;
  completedAt: string | null;
  failureStage: LocalizationJobStage | null;
  failureReason: string | null;
  retryCount: number;
  qualityScore: number | null;
  qualityVerdict: string | null;
  modelCallCount: number;
  failedModelCallCount: number;
  estimatedCostUsd: number;
  artifactCacheHitCount: number;
  generatedArtifactCount: number;
  providerCacheHitCount: number;
  handoffReady: boolean;
}

export interface DemoRunMatrix {
  anchorJobId: string;
  videoId: string;
  generatedAt: string;
  jobs: DemoRunMatrixJob[];
  recommendedBaselineJobId: string | null;
  bestQualityJobId: string | null;
  lowestCostJobId: string | null;
}

export interface DemoPresenterPackRun {
  jobId: string;
  demoProfileId: string | null;
  status: LocalizationJobStatus;
  completedAt: string | null;
  qualityScore: number | null;
  estimatedCostUsd: number;
  modelCallCount: number;
  providerCacheHitCount: number;
  handoffReady: boolean;
  roles: string[];
}

export interface DemoPresenterPackDownload {
  kind: string;
  label: string;
  url: string;
}

export interface DemoPresenterPack {
  anchorJobId: string;
  videoId: string;
  generatedAt: string;
  headline: string;
  readinessStatus: string;
  recommendedBaselineJobId: string | null;
  bestQualityJobId: string | null;
  lowestCostJobId: string | null;
  runs: DemoPresenterPackRun[];
  downloads: DemoPresenterPackDownload[];
  presenterNotesMarkdown: string;
}

export interface DemoReplayCardSetting {
  key: string;
  label: string;
  value: string;
}

export interface DemoReplayCardCommand {
  kind: string;
  label: string;
  command: string;
  note: string;
}

export interface DemoReplayCardLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoReplayCard {
  jobId: string;
  videoId: string;
  generatedAt: string;
  headline: string;
  readiness: string;
  status: LocalizationJobStatus;
  targetLanguage: string;
  demoProfileId: string | null;
  qualityScore: number | null;
  qualityVerdict: string | null;
  modelCallCount: number;
  providerCacheHitCount: number;
  artifactCacheHitCount: number;
  estimatedCostUsd: number;
  recommendedBaselineJobId: string | null;
  bestQualityJobId: string | null;
  lowestCostJobId: string | null;
  settings: DemoReplayCardSetting[];
  commands: DemoReplayCardCommand[];
  links: DemoReplayCardLink[];
  safetyNotes: string[];
}

export interface DemoCompletionCertificateCheck {
  key: string;
  label: string;
  status: string;
  detail: string;
  blocking: boolean;
}

export interface DemoCompletionCertificateSection {
  key: string;
  title: string;
  status: string;
  facts: string[];
}

export interface DemoCompletionCertificateLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoCompletionCertificate {
  jobId: string;
  videoId: string;
  generatedAt: string;
  certificateStatus: string;
  jobStatus: LocalizationJobStatus;
  targetLanguage: string;
  demoProfileId: string | null;
  headline: string;
  summary: string;
  recommendedNextAction: string;
  recommendedBaselineJobId: string | null;
  bestQualityJobId: string | null;
  lowestCostJobId: string | null;
  checks: DemoCompletionCertificateCheck[];
  sections: DemoCompletionCertificateSection[];
  links: DemoCompletionCertificateLink[];
  safetyNotes: string[];
}

export interface DemoAcceptanceGateCheck {
  key: string;
  label: string;
  status: string;
  detail: string;
  required: boolean;
}

export interface DemoAcceptanceGateEvidence {
  key: string;
  label: string;
  value: string;
  status: string;
}

export interface DemoAcceptanceGateLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoAcceptanceGate {
  jobId: string;
  videoId: string;
  generatedAt: string;
  gateStatus: string;
  jobStatus: LocalizationJobStatus;
  targetLanguage: string;
  demoProfileId: string | null;
  headline: string;
  summary: string;
  recommendedNextAction: string;
  checks: DemoAcceptanceGateCheck[];
  evidence: DemoAcceptanceGateEvidence[];
  links: DemoAcceptanceGateLink[];
  safetyNotes: string[];
}

export interface DemoShareSheetLink {
  kind: string;
  label: string;
  url: string;
}

export interface DemoShareSheet {
  jobId: string;
  videoId: string;
  generatedAt: string;
  readiness: string;
  headline: string;
  summary: string;
  outcomeBullets: string[];
  recommendedNextAction: string;
  links: DemoShareSheetLink[];
  markdown: string;
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
