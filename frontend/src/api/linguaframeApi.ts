import type {
  JobArtifact,
  LocalizationJob,
  LocalizationJobList,
  LocalizationJobStatus,
  MediaUpload,
  SubtitleSegment,
  TranscriptSegment
} from '../domain/jobTypes';

export interface ListJobsParams {
  status?: LocalizationJobStatus | 'ALL';
  limit?: number;
  offset?: number;
}

export async function uploadMedia(file: File, targetLanguage: string): Promise<MediaUpload> {
  const body = new FormData();
  body.set('file', file);
  body.set('targetLanguage', targetLanguage);

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

export async function listArtifacts(jobId: string): Promise<JobArtifact[]> {
  return requestJson<JobArtifact[]>(`/api/jobs/${encodeURIComponent(jobId)}/artifacts`, {
    method: 'GET'
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

export const linguaFrameApi = {
  uploadMedia,
  listJobs,
  getJob,
  retryJob,
  listArtifacts,
  listTranscript,
  listSubtitles,
  artifactDownloadUrl
};

async function requestJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return response.json() as Promise<T>;
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
