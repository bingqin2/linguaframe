import type {
  JobArtifact,
  LocalizationJob,
  LocalizationJobList,
  LocalizationJobStatus,
  MediaUpload,
  OperatorDashboard,
  PromptTemplate,
  RetentionCleanupResult,
  RuntimeDependencySummary,
  SubtitleSegment,
  TranscriptSegment
} from '../domain/jobTypes';

export const DEMO_ACCESS_TOKEN_STORAGE_KEY = 'linguaframe.demoToken.v1';
export const DEMO_ACCESS_TOKEN_HEADER = 'X-LinguaFrame-Demo-Token';
export const DEMO_ACCESS_TOKEN_COOKIE = 'LinguaFrame-Demo-Token';

export interface ListJobsParams {
  status?: LocalizationJobStatus | 'ALL';
  limit?: number;
  offset?: number;
}

export async function uploadMedia(
  file: File,
  targetLanguage: string,
  ttsVoice?: string
): Promise<MediaUpload> {
  const body = new FormData();
  body.set('file', file);
  body.set('targetLanguage', targetLanguage);
  const normalizedVoice = ttsVoice?.trim();
  if (normalizedVoice) {
    body.set('ttsVoice', normalizedVoice);
  }

  return requestJson<MediaUpload>('/api/media/uploads', {
    method: 'POST',
    body
  });
}

export async function getJob(jobId: string): Promise<LocalizationJob> {
  return requestJson<LocalizationJob>(`/api/jobs/${encodeURIComponent(jobId)}`, {
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

export async function getRuntimeDependencies(): Promise<RuntimeDependencySummary> {
  return requestJson<RuntimeDependencySummary>('/api/runtime/dependencies', {
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

export function jobEventsUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/events`;
}

export const linguaFrameApi = {
  uploadMedia,
  listJobs,
  getJob,
  retryJob,
  cancelJob,
  listArtifacts,
  listPromptTemplates,
  getOperatorDashboard,
  getRuntimeDependencies,
  getRetentionCleanupPreview,
  runRetentionCleanup,
  listTranscript,
  listSubtitles,
  artifactDownloadUrl,
  artifactArchiveDownloadUrl,
  jobDiagnosticsDownloadUrl,
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
