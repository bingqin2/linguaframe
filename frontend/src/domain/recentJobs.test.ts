import { describe, expect, test } from 'vitest';

import { loadRecentJobs, removeRecentJob, saveRecentJob } from './recentJobs';

describe('recentJobs', () => {
  test('saves recent jobs newest first and deduplicates by job id', () => {
    const storage = new MemoryStorage();

    saveRecentJob(storage, {
      jobId: 'job-1',
      videoId: 'video-1',
      targetLanguage: 'zh',
      filename: 'first.mp4',
      createdAt: '2026-06-26T10:00:00Z'
    });
    const jobs = saveRecentJob(storage, {
      jobId: 'job-1',
      videoId: 'video-1',
      targetLanguage: 'ja',
      filename: 'updated.mp4',
      createdAt: '2026-06-26T10:01:00Z'
    });

    expect(jobs).toEqual([
      {
        jobId: 'job-1',
        videoId: 'video-1',
        targetLanguage: 'ja',
        ttsVoice: null,
        translationStyle: 'NATURAL',
        subtitleStylePreset: 'STANDARD',
        translationGlossaryEntryCount: 0,
        translationGlossaryHash: '',
        subtitlePolishingMode: 'OFF',
        demoProfileId: null,
        filename: 'updated.mp4',
        createdAt: '2026-06-26T10:01:00Z'
      }
    ]);
  });

  test('keeps only the ten newest records', () => {
    const storage = new MemoryStorage();

    for (let index = 0; index < 12; index += 1) {
      saveRecentJob(storage, {
        jobId: `job-${index}`,
        videoId: `video-${index}`,
        targetLanguage: 'zh',
        filename: `${index}.mp4`,
        createdAt: `2026-06-26T10:${String(index).padStart(2, '0')}:00Z`
      });
    }

    const jobs = loadRecentJobs(storage);

    expect(jobs).toHaveLength(10);
    expect(jobs[0]?.jobId).toBe('job-11');
    expect(jobs.at(-1)?.jobId).toBe('job-2');
  });

  test('returns an empty list when stored json is corrupted', () => {
    const storage = new MemoryStorage();
    storage.setItem('linguaframe.recentJobs.v1', '{');

    expect(loadRecentJobs(storage)).toEqual([]);
  });

  test('loads jobs saved before tts voice selection existed', () => {
    const storage = new MemoryStorage();
    storage.setItem(
      'linguaframe.recentJobs.v1',
      JSON.stringify([
        {
          jobId: 'job-legacy',
          videoId: 'video-legacy',
          targetLanguage: 'zh-CN',
          filename: 'legacy.mp4',
          createdAt: '2026-06-26T10:00:00Z'
        }
      ])
    );

    expect(loadRecentJobs(storage)).toEqual([
      {
        jobId: 'job-legacy',
        videoId: 'video-legacy',
        targetLanguage: 'zh-CN',
        ttsVoice: null,
        translationStyle: 'NATURAL',
        subtitleStylePreset: 'STANDARD',
        translationGlossaryEntryCount: 0,
        translationGlossaryHash: '',
        subtitlePolishingMode: 'OFF',
        demoProfileId: null,
        filename: 'legacy.mp4',
        createdAt: '2026-06-26T10:00:00Z'
      }
    ]);
  });

  test('persists glossary metadata without raw glossary text', () => {
    const storage = new MemoryStorage();

    const jobs = saveRecentJob(storage, {
      jobId: 'job-glossary',
      videoId: 'video-glossary',
      targetLanguage: 'zh-CN',
      filename: 'glossary.mp4',
      translationGlossaryEntryCount: 2,
      translationGlossaryHash: 'abc123',
      createdAt: '2026-06-26T10:00:00Z'
    });

    expect(jobs[0]).toMatchObject({
      translationGlossaryEntryCount: 2,
      translationGlossaryHash: 'abc123'
    });
    expect(storage.getItem('linguaframe.recentJobs.v1')).not.toContain('Maya');
  });

  test('persists subtitle polishing mode as safe metadata', () => {
    const storage = new MemoryStorage();

    const jobs = saveRecentJob(storage, {
      jobId: 'job-polished',
      videoId: 'video-polished',
      targetLanguage: 'zh-CN',
      filename: 'polished.mp4',
      subtitlePolishingMode: ' balanced ',
      createdAt: '2026-06-26T10:00:00Z'
    });

    expect(jobs[0]?.subtitlePolishingMode).toBe('BALANCED');
    expect(storage.getItem('linguaframe.recentJobs.v1')).toContain('"subtitlePolishingMode":"BALANCED"');
  });

  test('removes a recent job by id', () => {
    const storage = new MemoryStorage();
    saveRecentJob(storage, {
      jobId: 'job-1',
      videoId: 'video-1',
      targetLanguage: 'zh',
      filename: 'one.mp4',
      createdAt: '2026-06-26T10:00:00Z'
    });
    saveRecentJob(storage, {
      jobId: 'job-2',
      videoId: 'video-2',
      targetLanguage: 'en',
      filename: 'two.mp4',
      createdAt: '2026-06-26T10:01:00Z'
    });

    const jobs = removeRecentJob(storage, 'job-1');

    expect(jobs.map((job) => job.jobId)).toEqual(['job-2']);
    expect(loadRecentJobs(storage).map((job) => job.jobId)).toEqual(['job-2']);
  });
});

class MemoryStorage implements Storage {
  private readonly values = new Map<string, string>();

  get length(): number {
    return this.values.size;
  }

  clear(): void {
    this.values.clear();
  }

  getItem(key: string): string | null {
    return this.values.get(key) ?? null;
  }

  key(index: number): string | null {
    return Array.from(this.values.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.values.delete(key);
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value);
  }
}
