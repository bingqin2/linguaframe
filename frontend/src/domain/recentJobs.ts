const RECENT_JOBS_KEY = 'linguaframe.recentJobs.v1';
const MAX_RECENT_JOBS = 10;

export interface RecentJob {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  filename: string;
  createdAt: string;
}

export function loadRecentJobs(storage: Storage): RecentJob[] {
  const value = storage.getItem(RECENT_JOBS_KEY);
  if (!value) {
    return [];
  }

  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(isRecentJob).sort(compareNewestFirst).slice(0, MAX_RECENT_JOBS);
  } catch {
    return [];
  }
}

export function saveRecentJob(storage: Storage, job: RecentJob): RecentJob[] {
  const jobs = [job, ...loadRecentJobs(storage).filter((existing) => existing.jobId !== job.jobId)]
    .sort(compareNewestFirst)
    .slice(0, MAX_RECENT_JOBS);
  storage.setItem(RECENT_JOBS_KEY, JSON.stringify(jobs));
  return jobs;
}

export function removeRecentJob(storage: Storage, jobId: string): RecentJob[] {
  const jobs = loadRecentJobs(storage).filter((job) => job.jobId !== jobId);
  storage.setItem(RECENT_JOBS_KEY, JSON.stringify(jobs));
  return jobs;
}

function compareNewestFirst(left: RecentJob, right: RecentJob): number {
  return Date.parse(right.createdAt) - Date.parse(left.createdAt);
}

function isRecentJob(value: unknown): value is RecentJob {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Partial<Record<keyof RecentJob, unknown>>;
  return (
    typeof candidate.jobId === 'string' &&
    typeof candidate.videoId === 'string' &&
    typeof candidate.targetLanguage === 'string' &&
    typeof candidate.filename === 'string' &&
    typeof candidate.createdAt === 'string'
  );
}
