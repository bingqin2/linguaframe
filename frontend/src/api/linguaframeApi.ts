import type {
  AuthLoginResponse,
  AuthSessionStatus,
  DemoAcceptanceGate,
  DemoPresentationCockpit,
  DemoSampleMediaCatalog,
  DemoSessionCommandCenter,
  DemoCompletionCertificate,
  DemoRunLauncher,
  DemoHandoffPortal,
  DemoReviewerWorkspace,
  JobArtifact,
  JobComparison,
  DemoEvidenceClosurePackage,
  DemoRunMonitor,
  DemoReplayCard,
  DemoRunSnapshot,
  DemoPresenterPack,
  DemoRunMatrix,
  DemoRunProfile,
  DemoRunVarianceReport,
  DemoShareSheet,
  DemoSessionStatus,
  DemoUploadReadiness,
  DeliveryManifest,
  LocalizationJob,
  LocalizationJobList,
  LocalizationJobStatus,
  MediaUpload,
  MediaUploadDetail,
  MediaUploadValidation,
  ModelUsageLedger,
  NarrationEvidence,
  NarrationGeneration,
  NarratedVideoGeneration,
  NarrationWorkspace,
  OpenAiReadinessEvidence,
  OpenAiSmokeProof,
  OperatorDashboard,
  OwnerQuotaPreflight,
  PrivateDemoEvidenceGallery,
  PrivateDemoLaunchRehearsal,
  PrivateDemoOperations,
  PrivateDemoRunArchive,
  PublishReviewedSubtitlesRequest,
  PromptTemplate,
  RetentionCleanupResult,
  ReviewedSubtitleWorkflow,
  ReviewedSubtitlePublish,
  RuntimeDependencySummary,
  RuntimeLiveCheckSummary,
  SaveNarrationWorkspaceRequest,
  SubtitleDraftSummary,
  SubtitleReviewEvidence,
  SubtitleReviewSummary,
  SubtitleSegment,
  TranscriptSegment,
  UpdateSubtitleDraftRequest,
  UploadCostEstimate,
  UploadExecutionPlan
} from '../domain/jobTypes';

export const DEMO_ACCESS_TOKEN_STORAGE_KEY = 'linguaframe.demoToken.v1';
export const DEMO_ACCESS_TOKEN_HEADER = 'X-LinguaFrame-Demo-Token';
export const DEMO_ACCESS_TOKEN_COOKIE = 'LinguaFrame-Demo-Token';
export const AUTH_TOKEN_STORAGE_KEY = 'linguaframe.authToken.v1';

export interface ListJobsParams {
  status?: LocalizationJobStatus | 'ALL';
  limit?: number;
  offset?: number;
}

export async function getDemoSession(): Promise<DemoSessionStatus> {
  return requestJson<DemoSessionStatus>('/api/demo-session', {
    method: 'GET'
  });
}

export async function loginDemoSession(token: string): Promise<DemoSessionStatus> {
  return requestJson<DemoSessionStatus>('/api/demo-session/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ token: token.trim() })
  });
}

export async function logoutDemoSession(): Promise<DemoSessionStatus> {
  return requestJson<DemoSessionStatus>('/api/demo-session/logout', {
    method: 'POST'
  });
}

export async function getAuthSession(): Promise<AuthSessionStatus> {
  return requestJson<AuthSessionStatus>('/api/auth/session', {
    method: 'GET'
  });
}

export async function loginAuthSession(username: string, password: string): Promise<AuthLoginResponse> {
  const response = await requestJson<AuthLoginResponse>('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      username: username.trim(),
      password: password.trim()
    })
  });
  writeAuthToken(window.localStorage, response.token);
  return response;
}

export async function logoutAuthSession(): Promise<AuthSessionStatus> {
  const status = await requestJson<AuthSessionStatus>('/api/auth/logout', {
    method: 'POST'
  });
  writeAuthToken(window.localStorage, '');
  return status;
}

export async function validateUpload(file: File): Promise<MediaUploadValidation> {
  const body = new FormData();
  body.set('file', file);

  return requestJson<MediaUploadValidation>('/api/media/uploads/validate', {
    method: 'POST',
    body
  });
}

export async function estimateUploadCost(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<UploadCostEstimate> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestJson<UploadCostEstimate>('/api/media/uploads/cost-estimate', {
    method: 'POST',
    body
  });
}

export async function estimateUploadExecutionPlan(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<UploadExecutionPlan> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestJson<UploadExecutionPlan>('/api/media/uploads/execution-plan', {
    method: 'POST',
    body
  });
}

export async function downloadUploadExecutionPlanMarkdown(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<Blob> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestBlob('/api/media/uploads/execution-plan/markdown/download', {
    method: 'POST',
    body
  });
}

export async function downloadUploadDecisionPackageMarkdown(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<Blob> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestBlob('/api/media/uploads/decision-package/markdown/download', {
    method: 'POST',
    body
  });
}

export async function downloadUploadDecisionPackageZip(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<Blob> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestBlob('/api/media/uploads/decision-package/download', {
    method: 'POST',
    body
  });
}

export function renderUploadExecutionPlanMarkdown(plan: UploadExecutionPlan): string {
  const lines = [
    '# Upload Execution Plan',
    '',
    '## Summary',
    '',
    `- Status: ${safeValue(plan.overallStatus)}`,
    `- Recommended next action: ${safeValue(plan.recommendedNextAction)}`,
    `- Demo profile: ${safeValue(plan.demoProfileId)}`,
    `- Target language: ${safeValue(plan.targetLanguage)}`,
    '',
    '## Source Metadata',
    '',
    `- Filename: ${safeValue(plan.filename)}`,
    `- Content type: ${safeValue(plan.contentType)}`,
    `- Size bytes: ${plan.fileSizeBytes} / ${plan.maxFileSizeBytes}`,
    `- Duration seconds: ${plan.durationSeconds ?? 'unknown'} / ${plan.maxDurationSeconds}`,
    `- Source SHA-256: ${safeValue(plan.sourceReuse?.sourceContentSha256)}`,
    '',
    '## Source Reuse Decision',
    '',
    `- Status: ${safeValue(plan.sourceReuseDecision?.status)}`,
    `- Headline: ${safeValue(plan.sourceReuseDecision?.headline)}`,
    `- Summary: ${safeValue(plan.sourceReuseDecision?.summary)}`,
    `- Recommended existing job: ${safeValue(plan.sourceReuseDecision?.recommendedExistingJobId)}`,
    `- Candidate count: ${plan.sourceReuseDecision?.candidateCount ?? 0}`,
    ...((plan.sourceReuseDecision?.links ?? []).map((link) => `- ${safeValue(link.label)}: ${safeValue(link.href)}`)),
    '',
    '## Gates',
    '',
    ...(plan.gates.length > 0
      ? plan.gates.map((gate) => `- ${safeValue(gate.label)}: ${safeValue(gate.status)} - ${safeValue(gate.detail)} Next: ${safeValue(gate.nextAction)}`)
      : ['- No gates reported.']),
    '',
    '## Paid Stages',
    '',
    ...(plan.stages.filter((stage) => stage.executionType === 'PAID').length > 0
      ? plan.stages
          .filter((stage) => stage.executionType === 'PAID')
          .map((stage) => `- ${safeValue(stage.label)}: ${safeValue(stage.provider)} / ${safeValue(stage.model)} / ${formatReportCost(stage.estimatedCostUsd)} - ${safeValue(stage.detail)}`)
      : ['- No paid stages are planned.']),
    '',
    '## Commands',
    '',
    ...(plan.commands.length > 0
      ? plan.commands.map((command) => `- ${safeValue(command.label)}: \`${safeValue(command.command)}\` - ${safeValue(command.description)}`)
      : ['- No commands reported.']),
    '',
    '## Safety Notes',
    '',
    ...(plan.safetyNotes.length > 0 ? plan.safetyNotes.map((note) => `- ${safeValue(note)}`) : ['- Report is metadata-only and read-only.'])
  ];
  return `${lines.join('\n')}\n`;
}

export async function getOwnerQuotaPreflight(): Promise<OwnerQuotaPreflight> {
  return requestJson<OwnerQuotaPreflight>('/api/media/uploads/preflight', {
    method: 'GET'
  });
}

export async function getDemoUploadReadiness(demoProfileId?: string): Promise<DemoUploadReadiness> {
  const query = new URLSearchParams();
  const normalizedDemoProfileId = demoProfileId?.trim();
  if (normalizedDemoProfileId) {
    query.set('demoProfileId', normalizedDemoProfileId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<DemoUploadReadiness>(`/api/media/uploads/readiness${suffix}`, {
    method: 'GET'
  });
}

export async function uploadMedia(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): Promise<MediaUpload> {
  const body = buildUploadOptionsFormData(
    file,
    targetLanguage,
    ttsVoice,
    translationStyle,
    subtitleStylePreset,
    translationGlossary,
    subtitlePolishingMode,
    demoProfileId
  );

  return requestJson<MediaUpload>('/api/media/uploads', {
    method: 'POST',
    body
  });
}

function buildUploadOptionsFormData(
  file: File,
  targetLanguage: string,
  ttsVoice?: string,
  translationStyle?: string,
  subtitleStylePreset?: string,
  translationGlossary?: string,
  subtitlePolishingMode?: string,
  demoProfileId?: string
): FormData {
  const body = new FormData();
  body.set('file', file);
  body.set('targetLanguage', targetLanguage);
  const normalizedVoice = ttsVoice?.trim();
  if (normalizedVoice) {
    body.set('ttsVoice', normalizedVoice);
  }
  const normalizedStyle = translationStyle?.trim().toUpperCase();
  if (normalizedStyle) {
    body.set('translationStyle', normalizedStyle);
  }
  const normalizedSubtitleStylePreset = subtitleStylePreset?.trim().toUpperCase();
  if (normalizedSubtitleStylePreset) {
    body.set('subtitleStylePreset', normalizedSubtitleStylePreset);
  }
  const normalizedTranslationGlossary = translationGlossary?.trim();
  if (normalizedTranslationGlossary) {
    body.set('translationGlossary', normalizedTranslationGlossary);
  }
  const normalizedSubtitlePolishingMode = subtitlePolishingMode?.trim().toUpperCase();
  if (normalizedSubtitlePolishingMode) {
    body.set('subtitlePolishingMode', normalizedSubtitlePolishingMode);
  }
  const normalizedDemoProfileId = demoProfileId?.trim();
  if (normalizedDemoProfileId) {
    body.set('demoProfileId', normalizedDemoProfileId);
  }
  return body;
}

export async function listDemoRunProfiles(): Promise<DemoRunProfile[]> {
  return requestJson<DemoRunProfile[]>('/api/demo-run-profiles', {
    method: 'GET'
  });
}

export async function getMediaUpload(videoId: string): Promise<MediaUploadDetail> {
  return requestJson<MediaUploadDetail>(`/api/media/uploads/${encodeURIComponent(videoId)}`, {
    method: 'GET'
  });
}

export async function getJob(jobId: string): Promise<LocalizationJob> {
  return requestJson<LocalizationJob>(`/api/jobs/${encodeURIComponent(jobId)}`, {
    method: 'GET'
  });
}

export async function getDeliveryManifest(jobId: string): Promise<DeliveryManifest> {
  return requestJson<DeliveryManifest>(`/api/jobs/${encodeURIComponent(jobId)}/delivery-manifest`, {
    method: 'GET'
  });
}

export async function getJobComparison(jobId: string, comparisonJobId: string): Promise<JobComparison> {
  return requestJson<JobComparison>(
    `/api/jobs/${encodeURIComponent(jobId)}/comparison/${encodeURIComponent(comparisonJobId)}`,
    {
      method: 'GET'
    }
  );
}

export async function getDemoRunMatrix(jobId: string, limit?: number): Promise<DemoRunMatrix> {
  const query = new URLSearchParams();
  if (limit !== undefined) {
    query.set('limit', String(limit));
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<DemoRunMatrix>(`/api/jobs/${encodeURIComponent(jobId)}/demo-run-matrix${suffix}`, {
    method: 'GET'
  });
}

export async function getDemoPresenterPack(jobId: string): Promise<DemoPresenterPack> {
  return requestJson<DemoPresenterPack>(`/api/jobs/${encodeURIComponent(jobId)}/demo-presenter-pack`, {
    method: 'GET'
  });
}

export async function getDemoRunMonitor(jobId: string): Promise<DemoRunMonitor> {
  return requestJson<DemoRunMonitor>(`/api/jobs/${encodeURIComponent(jobId)}/demo-run-monitor`, {
    method: 'GET'
  });
}

export async function getDemoReplayCard(jobId: string): Promise<DemoReplayCard> {
  return requestJson<DemoReplayCard>(`/api/jobs/${encodeURIComponent(jobId)}/demo-replay-card`, {
    method: 'GET'
  });
}

export async function getDemoCompletionCertificate(jobId: string): Promise<DemoCompletionCertificate> {
  return requestJson<DemoCompletionCertificate>(
    `/api/jobs/${encodeURIComponent(jobId)}/demo-completion-certificate`,
    {
      method: 'GET'
    }
  );
}

export async function getDemoAcceptanceGate(jobId: string): Promise<DemoAcceptanceGate> {
  return requestJson<DemoAcceptanceGate>(`/api/jobs/${encodeURIComponent(jobId)}/demo-acceptance-gate`, {
    method: 'GET'
  });
}

export async function getDemoRunVariance(
  jobId: string,
  preUploadJson?: string
): Promise<DemoRunVarianceReport> {
  return requestJson<DemoRunVarianceReport>(`/api/jobs/${encodeURIComponent(jobId)}/demo-run-variance`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ preUploadJson: preUploadJson?.trim() || null })
  });
}

export async function downloadDemoRunVarianceMarkdown(
  jobId: string,
  preUploadJson?: string
): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-run-variance/markdown/download`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ preUploadJson: preUploadJson?.trim() || null })
  });
}

export async function getDemoEvidenceClosure(
  jobId: string,
  preUploadJson?: string
): Promise<DemoEvidenceClosurePackage> {
  return requestJson<DemoEvidenceClosurePackage>(`/api/jobs/${encodeURIComponent(jobId)}/demo-evidence-closure`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ preUploadJson: preUploadJson?.trim() || null })
  });
}

export async function downloadDemoEvidenceClosureMarkdown(
  jobId: string,
  preUploadJson?: string
): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-evidence-closure/markdown/download`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ preUploadJson: preUploadJson?.trim() || null })
  });
}

export async function downloadDemoEvidenceClosureZip(
  jobId: string,
  preUploadJson?: string
): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-evidence-closure/download`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ preUploadJson: preUploadJson?.trim() || null })
  });
}

export async function getDemoRunSnapshot(jobId: string): Promise<DemoRunSnapshot> {
  return requestJson<DemoRunSnapshot>(`/api/jobs/${encodeURIComponent(jobId)}/demo-run-snapshot`, {
    method: 'GET'
  });
}

export async function getDemoShareSheet(jobId: string): Promise<DemoShareSheet> {
  return requestJson<DemoShareSheet>(`/api/jobs/${encodeURIComponent(jobId)}/demo-share-sheet`, {
    method: 'GET'
  });
}

export async function listJobs(params: ListJobsParams = {}): Promise<LocalizationJobList> {
  const query = new URLSearchParams();
  if (params.status && params.status !== 'ALL') {
    query.set('status', params.status);
  }
  query.set('limit', String(params.limit ?? 20));
  query.set('offset', String(params.offset ?? 0));

  return requestJson<LocalizationJobList>(`/api/jobs?${query.toString()}`, {
    method: 'GET'
  });
}

export async function retryJob(jobId: string): Promise<LocalizationJob> {
  return requestJson<LocalizationJob>(`/api/jobs/${encodeURIComponent(jobId)}/retry`, {
    method: 'POST'
  });
}

export async function cancelJob(jobId: string): Promise<LocalizationJob> {
  return requestJson<LocalizationJob>(`/api/jobs/${encodeURIComponent(jobId)}/cancel`, {
    method: 'POST'
  });
}

export async function listArtifacts(jobId: string): Promise<JobArtifact[]> {
  return requestJson<JobArtifact[]>(`/api/jobs/${encodeURIComponent(jobId)}/artifacts`, {
    method: 'GET'
  });
}

export async function listPromptTemplates(): Promise<PromptTemplate[]> {
  return requestJson<PromptTemplate[]>('/api/prompt-templates', {
    method: 'GET'
  });
}

export async function getOperatorDashboard(): Promise<OperatorDashboard> {
  return requestJson<OperatorDashboard>('/api/operator/dashboard', {
    method: 'GET'
  });
}

export async function getModelUsageLedger(limit?: number): Promise<ModelUsageLedger> {
  const query = new URLSearchParams();
  if (limit !== undefined) {
    query.set('limit', String(limit));
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<ModelUsageLedger>(`/api/operator/model-usage-ledger${suffix}`, {
    method: 'GET'
  });
}

export async function downloadModelUsageLedgerMarkdown(limit?: number): Promise<Blob> {
  const query = new URLSearchParams();
  if (limit !== undefined) {
    query.set('limit', String(limit));
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestBlob(`/api/operator/model-usage-ledger/markdown/download${suffix}`, {
    method: 'GET'
  });
}

export async function getDemoSessionCommandCenter(jobId?: string): Promise<DemoSessionCommandCenter> {
  const query = new URLSearchParams();
  const normalizedJobId = jobId?.trim();
  if (normalizedJobId) {
    query.set('jobId', normalizedJobId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<DemoSessionCommandCenter>(`/api/operator/demo-session-command-center${suffix}`, {
    method: 'GET'
  });
}

export async function downloadDemoSessionCommandCenterMarkdown(jobId?: string): Promise<Blob> {
  const query = new URLSearchParams();
  const normalizedJobId = jobId?.trim();
  if (normalizedJobId) {
    query.set('jobId', normalizedJobId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestBlob(`/api/operator/demo-session-command-center/markdown/download${suffix}`, {
    method: 'GET'
  });
}

export async function downloadDemoSessionEvidencePackageZip(jobId?: string): Promise<Blob> {
  const query = new URLSearchParams();
  const normalizedJobId = jobId?.trim();
  if (normalizedJobId) {
    query.set('jobId', normalizedJobId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestBlob(`/api/operator/demo-session-evidence-package/download${suffix}`, {
    method: 'GET'
  });
}

export async function getPrivateDemoOperations(): Promise<PrivateDemoOperations> {
  return requestJson<PrivateDemoOperations>('/api/operator/private-demo/operations', {
    method: 'GET'
  });
}

export async function getPrivateDemoLaunchRehearsal(): Promise<PrivateDemoLaunchRehearsal> {
  return requestJson<PrivateDemoLaunchRehearsal>('/api/operator/private-demo/launch-rehearsal', {
    method: 'GET'
  });
}

export async function getPrivateDemoEvidenceGallery(limit?: number): Promise<PrivateDemoEvidenceGallery> {
  const query = new URLSearchParams();
  if (limit !== undefined) {
    query.set('limit', String(limit));
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<PrivateDemoEvidenceGallery>(`/api/operator/private-demo/evidence-gallery${suffix}`, {
    method: 'GET'
  });
}

export async function getPrivateDemoRunArchive(): Promise<PrivateDemoRunArchive> {
  return requestJson<PrivateDemoRunArchive>('/api/operator/private-demo/run-archive', {
    method: 'GET'
  });
}

export async function getDemoSampleMediaCatalog(): Promise<DemoSampleMediaCatalog> {
  return requestJson<DemoSampleMediaCatalog>('/api/operator/demo-sample-media-catalog', {
    method: 'GET'
  });
}

export async function getDemoRunLauncher(): Promise<DemoRunLauncher> {
  return requestJson<DemoRunLauncher>('/api/operator/demo-run-launcher', {
    method: 'GET'
  });
}

export async function getDemoPresentationCockpit(jobId?: string): Promise<DemoPresentationCockpit> {
  const query = new URLSearchParams();
  const normalizedJobId = jobId?.trim();
  if (normalizedJobId) {
    query.set('jobId', normalizedJobId);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return requestJson<DemoPresentationCockpit>(`/api/operator/demo-presentation-cockpit${suffix}`, {
    method: 'GET'
  });
}

export async function getRuntimeDependencies(): Promise<RuntimeDependencySummary> {
  return requestJson<RuntimeDependencySummary>('/api/runtime/dependencies', {
    method: 'GET'
  });
}

export async function getRuntimeLiveChecks(): Promise<RuntimeLiveCheckSummary> {
  return requestJson<RuntimeLiveCheckSummary>('/api/runtime/live-checks', {
    method: 'GET'
  });
}

export async function getOpenAiReadinessEvidence(): Promise<OpenAiReadinessEvidence> {
  return requestJson<OpenAiReadinessEvidence>('/api/operator/openai-readiness-evidence', {
    method: 'GET'
  });
}

export async function downloadOpenAiReadinessEvidenceMarkdown(): Promise<Blob> {
  return requestBlob('/api/operator/openai-readiness-evidence/markdown/download', {
    method: 'GET'
  });
}

export async function getOpenAiSmokeProof(jobId: string): Promise<OpenAiSmokeProof> {
  return requestJson<OpenAiSmokeProof>(`/api/jobs/${encodeURIComponent(jobId)}/openai-smoke-proof`, {
    method: 'GET'
  });
}

export async function downloadOpenAiSmokeProofMarkdown(jobId: string): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/openai-smoke-proof/markdown/download`, {
    method: 'GET'
  });
}

export async function getDemoReviewerWorkspace(jobId: string): Promise<DemoReviewerWorkspace> {
  return requestJson<DemoReviewerWorkspace>(`/api/jobs/${encodeURIComponent(jobId)}/demo-reviewer-workspace`, {
    method: 'GET'
  });
}

export async function downloadDemoReviewerWorkspaceMarkdown(jobId: string): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-reviewer-workspace/markdown/download`, {
    method: 'GET'
  });
}

export async function downloadDemoReviewerWorkspaceZip(jobId: string): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-reviewer-workspace/download`, {
    method: 'GET'
  });
}

export async function getDemoHandoffPortal(jobId: string): Promise<DemoHandoffPortal> {
  return requestJson<DemoHandoffPortal>(`/api/jobs/${encodeURIComponent(jobId)}/demo-handoff-portal`, {
    method: 'GET'
  });
}

export async function downloadDemoHandoffPortalMarkdown(jobId: string): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-handoff-portal/markdown/download`, {
    method: 'GET'
  });
}

export async function downloadDemoHandoffPortalZip(jobId: string): Promise<Blob> {
  return requestBlob(`/api/jobs/${encodeURIComponent(jobId)}/demo-handoff-portal/download`, {
    method: 'GET'
  });
}

export async function getRetentionCleanupPreview(): Promise<RetentionCleanupResult> {
  return requestJson<RetentionCleanupResult>('/api/retention/cleanup/preview', {
    method: 'GET'
  });
}

export async function runRetentionCleanup(): Promise<RetentionCleanupResult> {
  return requestJson<RetentionCleanupResult>('/api/retention/cleanup/run', {
    method: 'POST'
  });
}

export async function listTranscript(jobId: string): Promise<TranscriptSegment[]> {
  return requestJson<TranscriptSegment[]>(`/api/jobs/${encodeURIComponent(jobId)}/transcript`, {
    method: 'GET'
  });
}

export async function listSubtitles(
  jobId: string,
  language: string
): Promise<SubtitleSegment[]> {
  return requestJson<SubtitleSegment[]>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitles/${encodeURIComponent(language)}`,
    {
      method: 'GET'
    }
  );
}

export async function getSubtitleReview(
  jobId: string,
  language: string
): Promise<SubtitleReviewSummary> {
  const query = new URLSearchParams({ language });
  return requestJson<SubtitleReviewSummary>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-review?${query.toString()}`,
    {
      method: 'GET'
    }
  );
}

export async function getSubtitleDraft(
  jobId: string,
  language: string
): Promise<SubtitleDraftSummary> {
  const query = new URLSearchParams({ language });
  return requestJson<SubtitleDraftSummary>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-draft?${query.toString()}`,
    {
      method: 'GET'
    }
  );
}

export async function getReviewedSubtitleWorkflow(jobId: string): Promise<ReviewedSubtitleWorkflow> {
  return requestJson<ReviewedSubtitleWorkflow>(
    `/api/jobs/${encodeURIComponent(jobId)}/reviewed-subtitle-workflow`,
    {
      method: 'GET'
    }
  );
}

export async function getSubtitleReviewEvidence(jobId: string): Promise<SubtitleReviewEvidence> {
  return requestJson<SubtitleReviewEvidence>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-review-evidence`,
    {
      method: 'GET'
    }
  );
}

export async function getNarrationWorkspace(jobId: string): Promise<NarrationWorkspace> {
  return requestJson<NarrationWorkspace>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-workspace`,
    {
      method: 'GET'
    }
  );
}

export async function saveNarrationWorkspace(
  jobId: string,
  request: SaveNarrationWorkspaceRequest
): Promise<NarrationWorkspace> {
  return requestJson<NarrationWorkspace>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-workspace`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(request)
    }
  );
}

export async function clearNarrationWorkspace(jobId: string): Promise<NarrationWorkspace> {
  return requestJson<NarrationWorkspace>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-workspace`,
    {
      method: 'DELETE'
    }
  );
}

export async function generateNarrationAudio(jobId: string): Promise<NarrationGeneration> {
  return requestJson<NarrationGeneration>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-workspace/generate-audio`,
    {
      method: 'POST'
    }
  );
}

export async function generateNarratedVideo(jobId: string): Promise<NarratedVideoGeneration> {
  return requestJson<NarratedVideoGeneration>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-workspace/generate-video`,
    {
      method: 'POST'
    }
  );
}

export async function getNarrationEvidence(jobId: string): Promise<NarrationEvidence> {
  return requestJson<NarrationEvidence>(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-evidence`,
    {
      method: 'GET'
    }
  );
}

export async function downloadNarrationEvidenceMarkdown(jobId: string): Promise<Blob> {
  return requestBlob(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-evidence/markdown/download`,
    {
      method: 'GET'
    }
  );
}

export async function downloadNarrationEvidenceZip(jobId: string): Promise<Blob> {
  return requestBlob(
    `/api/jobs/${encodeURIComponent(jobId)}/narration-evidence/download`,
    {
      method: 'GET'
    }
  );
}

export async function downloadSubtitleReviewEvidenceMarkdown(jobId: string): Promise<Blob> {
  return requestBlob(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-review-evidence/markdown/download`,
    {
      method: 'GET'
    }
  );
}

export async function downloadSubtitleReviewEvidenceZip(jobId: string): Promise<Blob> {
  return requestBlob(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-review-evidence/download`,
    {
      method: 'GET'
    }
  );
}

export async function updateSubtitleDraft(
  jobId: string,
  language: string,
  request: UpdateSubtitleDraftRequest
): Promise<SubtitleDraftSummary> {
  const query = new URLSearchParams({ language });
  return requestJson<SubtitleDraftSummary>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-draft?${query.toString()}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(request)
    }
  );
}

export async function clearSubtitleDraft(
  jobId: string,
  language: string
): Promise<SubtitleDraftSummary> {
  const query = new URLSearchParams({ language });
  return requestJson<SubtitleDraftSummary>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-draft?${query.toString()}`,
    {
      method: 'DELETE'
    }
  );
}

export async function publishReviewedSubtitles(
  jobId: string,
  request: PublishReviewedSubtitlesRequest
): Promise<ReviewedSubtitlePublish> {
  return requestJson<ReviewedSubtitlePublish>(
    `/api/jobs/${encodeURIComponent(jobId)}/subtitle-draft/publish`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(request)
    }
  );
}

export function subtitleDraftExportUrl(
  jobId: string,
  language: string,
  format: 'json' | 'srt' | 'vtt'
): string {
  const query = new URLSearchParams({ language, format });
  return `/api/jobs/${encodeURIComponent(jobId)}/subtitle-draft/export?${query.toString()}`;
}

export function artifactDownloadUrl(jobId: string, artifactId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/artifacts/${encodeURIComponent(
    artifactId
  )}/download`;
}

export function artifactArchiveDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/artifacts/archive/download`;
}

export function jobDiagnosticsDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/diagnostics/download`;
}

export function jobEvidenceMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/evidence/markdown/download`;
}

export function jobEvidenceBundleDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/evidence/bundle/download`;
}

export function qualityEvaluationEvidenceMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(
    jobId
  )}/quality-evaluation/evidence/markdown/download`;
}

export function jobHandoffPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/handoff-package/download`;
}

export function demoRunPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/demo-run-package/download`;
}

export function aiAuditPackageDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/ai-audit-package/download`;
}

export function sourceMediaDownloadUrl(videoId: string): string {
  return `/api/media/uploads/${encodeURIComponent(videoId)}/source/download`;
}

export function deliveryManifestMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/delivery-manifest/markdown/download`;
}

export function demoShareSheetMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/demo-share-sheet/markdown/download`;
}

export function demoRunMonitorMarkdownDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/demo-run-monitor/markdown/download`;
}

export function demoRunSnapshotDownloadUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/demo-run-snapshot/download`;
}

export function jobComparisonMarkdownDownloadUrl(jobId: string, comparisonJobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/comparison/${encodeURIComponent(
    comparisonJobId
  )}/markdown/download`;
}

export function jobEventsUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/events`;
}

export const linguaFrameApi = {
  getDemoSession,
  getAuthSession,
  listDemoRunProfiles,
  loginDemoSession,
  logoutDemoSession,
  loginAuthSession,
  logoutAuthSession,
  validateUpload,
  estimateUploadCost,
  estimateUploadExecutionPlan,
  downloadUploadExecutionPlanMarkdown,
  downloadUploadDecisionPackageMarkdown,
  downloadUploadDecisionPackageZip,
  renderUploadExecutionPlanMarkdown,
  getOwnerQuotaPreflight,
  getDemoUploadReadiness,
  uploadMedia,
  getMediaUpload,
  listJobs,
  getJob,
  getDeliveryManifest,
  getDemoRunMatrix,
  getDemoRunMonitor,
  getDemoReplayCard,
  getDemoCompletionCertificate,
  getDemoAcceptanceGate,
  getDemoRunVariance,
  downloadDemoRunVarianceMarkdown,
  getDemoEvidenceClosure,
  downloadDemoEvidenceClosureMarkdown,
  downloadDemoEvidenceClosureZip,
  getDemoRunSnapshot,
  getDemoPresenterPack,
  getDemoShareSheet,
  getJobComparison,
  retryJob,
  cancelJob,
  listArtifacts,
  listPromptTemplates,
  getOperatorDashboard,
  getModelUsageLedger,
  downloadModelUsageLedgerMarkdown,
  getDemoSessionCommandCenter,
  downloadDemoSessionCommandCenterMarkdown,
  downloadDemoSessionEvidencePackageZip,
  getPrivateDemoOperations,
  getPrivateDemoLaunchRehearsal,
  getPrivateDemoEvidenceGallery,
  getPrivateDemoRunArchive,
  getDemoSampleMediaCatalog,
  getDemoRunLauncher,
  getDemoPresentationCockpit,
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
  getRetentionCleanupPreview,
  runRetentionCleanup,
  listTranscript,
  listSubtitles,
  getSubtitleReview,
  getSubtitleDraft,
  getReviewedSubtitleWorkflow,
  getSubtitleReviewEvidence,
  getNarrationWorkspace,
  saveNarrationWorkspace,
  clearNarrationWorkspace,
  generateNarrationAudio,
  generateNarratedVideo,
  getNarrationEvidence,
  downloadNarrationEvidenceMarkdown,
  downloadNarrationEvidenceZip,
  downloadSubtitleReviewEvidenceMarkdown,
  downloadSubtitleReviewEvidenceZip,
  updateSubtitleDraft,
  clearSubtitleDraft,
  publishReviewedSubtitles,
  subtitleDraftExportUrl,
  artifactDownloadUrl,
  artifactArchiveDownloadUrl,
  jobDiagnosticsDownloadUrl,
  jobEvidenceMarkdownDownloadUrl,
  jobEvidenceBundleDownloadUrl,
  qualityEvaluationEvidenceMarkdownDownloadUrl,
  jobHandoffPackageDownloadUrl,
  demoRunPackageDownloadUrl,
  aiAuditPackageDownloadUrl,
  sourceMediaDownloadUrl,
  deliveryManifestMarkdownDownloadUrl,
  demoRunMonitorMarkdownDownloadUrl,
  demoRunSnapshotDownloadUrl,
  demoShareSheetMarkdownDownloadUrl,
  jobComparisonMarkdownDownloadUrl,
  jobEventsUrl,
  readDemoToken,
  writeDemoToken
};

async function requestJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, withAccessHeaders(init));
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.json() as Promise<T>;
}

async function requestBlob(url: string, init: RequestInit): Promise<Blob> {
  const response = await fetch(url, withAccessHeaders(init));
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.blob();
}

function safeValue(value: string | null | undefined): string {
  const normalized = value?.replace(/\r|\n/g, ' ').trim();
  return normalized ? normalized : 'none';
}

function formatReportCost(value: number): string {
  return `$${value.toFixed(8)}`;
}

export function readAuthToken(storage: Storage = window.localStorage): string {
  return storage.getItem(AUTH_TOKEN_STORAGE_KEY)?.trim() ?? '';
}

export function writeAuthToken(storage: Storage, token: string): string {
  const normalizedToken = token.trim();
  if (normalizedToken.length === 0) {
    storage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    return '';
  }

  storage.setItem(AUTH_TOKEN_STORAGE_KEY, normalizedToken);
  return normalizedToken;
}

export function readDemoToken(storage: Storage = window.localStorage): string {
  return storage.getItem(DEMO_ACCESS_TOKEN_STORAGE_KEY)?.trim() ?? '';
}

export function writeDemoToken(storage: Storage, token: string): string {
  const normalizedToken = token.trim();
  if (normalizedToken.length === 0) {
    storage.removeItem(DEMO_ACCESS_TOKEN_STORAGE_KEY);
    clearDemoAccessCookie();
    return '';
  }

  storage.setItem(DEMO_ACCESS_TOKEN_STORAGE_KEY, normalizedToken);
  writeDemoAccessCookie(normalizedToken);
  return normalizedToken;
}

function withAccessHeaders(init: RequestInit): RequestInit {
  const demoToken = readDemoToken();
  const authToken = readAuthToken();
  if (!demoToken && !authToken) {
    return init;
  }

  const headers = headersToRecord(init.headers);
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }
  if (demoToken) {
    headers[DEMO_ACCESS_TOKEN_HEADER] = demoToken;
  }

  return {
    ...init,
    headers
  };
}

function headersToRecord(headers: HeadersInit | undefined): Record<string, string> {
  if (!headers) {
    return {};
  }
  if (headers instanceof Headers) {
    return Object.fromEntries(headers.entries());
  }
  if (Array.isArray(headers)) {
    return Object.fromEntries(headers);
  }
  return headers;
}

function writeDemoAccessCookie(token: string) {
  document.cookie = `${DEMO_ACCESS_TOKEN_COOKIE}=${encodeURIComponent(
    token
  )}; Path=/; SameSite=Lax`;
}

function clearDemoAccessCookie() {
  document.cookie = `${DEMO_ACCESS_TOKEN_COOKIE}=; Path=/; Max-Age=0; SameSite=Lax`;
}

async function readErrorMessage(response: Response): Promise<string> {
  const fallback = `Request failed with status ${response.status}`;
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('application/json')) {
    return fallback;
  }

  try {
    const body = (await response.json()) as { message?: unknown; error?: unknown };
    if (typeof body.message === 'string' && body.message.trim().length > 0) {
      return body.message.trim();
    }
    if (typeof body.error === 'string' && body.error.trim().length > 0) {
      return body.error.trim();
    }
    return fallback;
  } catch {
    return fallback;
  }
}
