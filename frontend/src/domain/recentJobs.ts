const RECENT_JOBS_KEY = 'linguaframe.recentJobs.v1';
const MAX_RECENT_JOBS = 10;

export interface RecentJob {
  jobId: string;
  videoId: string;
  targetLanguage: string;
  ttsVoice: string | null;
  translationStyle: string;
  subtitleStylePreset: string;
  translationGlossaryEntryCount: number;
  translationGlossaryHash: string;
  subtitlePolishingMode: string;
  filename: string;
  createdAt: string;
}

type RecentJobInput = Omit<RecentJob, 'ttsVoice' | 'translationStyle' | 'subtitleStylePreset' | 'translationGlossaryEntryCount' | 'translationGlossaryHash' | 'subtitlePolishingMode'> & {
  ttsVoice?: string | null;
  translationStyle?: string | null;
  subtitleStylePreset?: string | null;
  translationGlossaryEntryCount?: number | null;
  translationGlossaryHash?: string | null;
  subtitlePolishingMode?: string | null;
};

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
    return parsed
      .map(toRecentJob)
      .filter((job): job is RecentJob => job !== null)
      .sort(compareNewestFirst)
      .slice(0, MAX_RECENT_JOBS);
  } catch {
    return [];
  }
}

export function saveRecentJob(storage: Storage, job: RecentJobInput): RecentJob[] {
  const normalizedJob = {
    ...job,
    ttsVoice: job.ttsVoice ?? null,
    translationStyle: normalizeTranslationStyle(job.translationStyle),
    subtitleStylePreset: normalizeSubtitleStylePreset(job.subtitleStylePreset),
    translationGlossaryEntryCount: normalizeGlossaryEntryCount(job.translationGlossaryEntryCount),
    translationGlossaryHash: normalizeGlossaryHash(job.translationGlossaryHash),
    subtitlePolishingMode: normalizeSubtitlePolishingMode(job.subtitlePolishingMode)
  };
  const jobs = [normalizedJob, ...loadRecentJobs(storage).filter((existing) => existing.jobId !== job.jobId)]
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

function toRecentJob(value: unknown): RecentJob | null {
  if (!value || typeof value !== 'object') {
    return null;
  }

  const candidate = value as Partial<Record<keyof RecentJob, unknown>>;
  if (
    typeof candidate.jobId !== 'string' ||
    typeof candidate.videoId !== 'string' ||
    typeof candidate.targetLanguage !== 'string' ||
    typeof candidate.filename !== 'string' ||
    typeof candidate.createdAt !== 'string'
  ) {
    return null;
  }
  return {
    jobId: candidate.jobId,
    videoId: candidate.videoId,
    targetLanguage: candidate.targetLanguage,
    ttsVoice: typeof candidate.ttsVoice === 'string' ? candidate.ttsVoice : null,
    translationStyle: normalizeTranslationStyle(candidate.translationStyle),
    subtitleStylePreset: normalizeSubtitleStylePreset(candidate.subtitleStylePreset),
    translationGlossaryEntryCount: normalizeGlossaryEntryCount(candidate.translationGlossaryEntryCount),
    translationGlossaryHash: normalizeGlossaryHash(candidate.translationGlossaryHash),
    subtitlePolishingMode: normalizeSubtitlePolishingMode(candidate.subtitlePolishingMode),
    filename: candidate.filename,
    createdAt: candidate.createdAt
  };
}

function normalizeTranslationStyle(value: unknown): string {
  if (typeof value !== 'string' || value.trim() === '') {
    return 'NATURAL';
  }
  return value.trim().toUpperCase();
}

function normalizeSubtitleStylePreset(value: unknown): string {
  if (typeof value !== 'string' || value.trim() === '') {
    return 'STANDARD';
  }
  return value.trim().toUpperCase();
}

function normalizeGlossaryEntryCount(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.floor(value) : 0;
}

function normalizeGlossaryHash(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeSubtitlePolishingMode(value: unknown): string {
  if (typeof value !== 'string' || value.trim() === '') {
    return 'OFF';
  }
  return value.trim().toUpperCase();
}
