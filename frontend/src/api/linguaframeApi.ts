import type {
  JobArtifact,
  JobComparison,
  DemoRunMatrix,
  DemoRunProfile,
  DemoSessionStatus,
  DeliveryManifest,
  LocalizationJob,
  LocalizationJobList,
  LocalizationJobStatus,
  MediaUpload,
  MediaUploadDetail,
  MediaUploadValidation,
  OperatorDashboard,
  PrivateDemoOperations,
  PublishReviewedSubtitlesRequest,
  PromptTemplate,
  RetentionCleanupResult,
  ReviewedSubtitlePublish,
  RuntimeDependencySummary,
  RuntimeLiveCheckSummary,
  SubtitleDraftSummary,
  SubtitleReviewSummary,
  SubtitleSegment,
  TranscriptSegment,
  UpdateSubtitleDraftRequest
} from '../domain/jobTypes';

export const DEMO_ACCESS_TOKEN_STORAGE_KEY = 'linguaframe.demoToken.v1';
export const DEMO_ACCESS_TOKEN_HEADER = 'X-LinguaFrame-Demo-Token';
export const DEMO_ACCESS_TOKEN_COOKIE = 'LinguaFrame-Demo-Token';

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

export async function validateUpload(file: File): Promise<MediaUploadValidation> {
  const body = new FormData();
  body.set('file', file);

  return requestJson<MediaUploadValidation>('/api/media/uploads/validate', {
    method: 'POST',
    body
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

  return requestJson<MediaUpload>('/api/media/uploads', {
    method: 'POST',
    body
  });
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

export async function getPrivateDemoOperations(): Promise<PrivateDemoOperations> {
  return requestJson<PrivateDemoOperations>('/api/operator/private-demo/operations', {
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
  listDemoRunProfiles,
  loginDemoSession,
  logoutDemoSession,
  validateUpload,
  uploadMedia,
  getMediaUpload,
  listJobs,
  getJob,
  getDeliveryManifest,
  getDemoRunMatrix,
  getJobComparison,
  retryJob,
  cancelJob,
  listArtifacts,
  listPromptTemplates,
  getOperatorDashboard,
  getPrivateDemoOperations,
  getRuntimeDependencies,
  getRuntimeLiveChecks,
  getRetentionCleanupPreview,
  runRetentionCleanup,
  listTranscript,
  listSubtitles,
  getSubtitleReview,
  getSubtitleDraft,
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
  jobComparisonMarkdownDownloadUrl,
  jobEventsUrl,
  readDemoToken,
  writeDemoToken
};

async function requestJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, withDemoAccessHeader(init));
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.json() as Promise<T>;
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

function withDemoAccessHeader(init: RequestInit): RequestInit {
  const token = readDemoToken();
  if (!token) {
    return init;
  }

  return {
    ...init,
    headers: {
      ...headersToRecord(init.headers),
      [DEMO_ACCESS_TOKEN_HEADER]: token
    }
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
