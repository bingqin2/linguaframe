import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { linguaFrameApi } from './api/linguaframeApi';
import type { LocalizationJob, MediaUpload } from './domain/jobTypes';
import { loadRecentJobs, RecentJob, saveRecentJob } from './domain/recentJobs';

const POLL_INTERVAL_MS = 5000;
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);

export function App({ pollIntervalMs = POLL_INTERVAL_MS }: { pollIntervalMs?: number }) {
  const [targetLanguage, setTargetLanguage] = useState('zh-CN');
  const [manualJobId, setManualJobId] = useState('');
  const [selectedRecentJob, setSelectedRecentJob] = useState<RecentJob | null>(null);
  const [recentJobs, setRecentJobs] = useState<RecentJob[]>(() =>
    loadRecentJobs(window.localStorage)
  );
  const [job, setJob] = useState<LocalizationJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingJob, setIsLoadingJob] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedLanguage = selectedRecentJob?.targetLanguage ?? job?.targetLanguage ?? targetLanguage;
  const canRetry = job?.status === 'FAILED';

  const loadJob = useCallback(
    async (jobId: string, options: { silent?: boolean } = {}) => {
      if (!options.silent) {
        setIsLoadingJob(true);
      }
      try {
        const nextJob = await linguaFrameApi.getJob(jobId);
        setJob(nextJob);
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

  useEffect(() => {
    if (!job || TERMINAL_STATUSES.has(job.status)) {
      return;
    }

    const timer = window.setTimeout(() => {
      void loadJob(job.jobId, { silent: true });
    }, pollIntervalMs);

    return () => window.clearTimeout(timer);
  }, [job, loadJob, pollIntervalMs]);

  async function handleUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const input = form.elements.namedItem('videoFile') as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      setError('Choose an MP4 file before uploading.');
      return;
    }

    setIsUploading(true);
    try {
      const upload = await linguaFrameApi.uploadMedia(file, targetLanguage.trim());
      const recentJob = toRecentJob(upload);
      setRecentJobs(saveRecentJob(window.localStorage, recentJob));
      setSelectedRecentJob(recentJob);
      await loadJob(upload.jobId);
    } catch (uploadError) {
      setError(toErrorMessage(uploadError));
    } finally {
      setIsUploading(false);
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
    await loadJob(jobId);
  }

  async function handleRetry() {
    if (!job) {
      return;
    }

    setIsRetrying(true);
    try {
      const retriedJob = await linguaFrameApi.retryJob(job.jobId);
      setJob(retriedJob);
      setError(null);
    } catch (retryError) {
      setError(toErrorMessage(retryError));
    } finally {
      setIsRetrying(false);
    }
  }

  async function openRecentJob(recentJob: RecentJob) {
    setSelectedRecentJob(recentJob);
    setManualJobId(recentJob.jobId);
    setTargetLanguage(recentJob.targetLanguage);
    await loadJob(recentJob.jobId);
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <h1>LinguaFrame Demo</h1>
          <p>Upload a video, follow the localization pipeline, and inspect generated outputs.</p>
        </div>
        <span className="runtime-badge">Browser demo</span>
      </header>

      {error ? <div className="alert">{error}</div> : null}

      <section className="workspace-grid" aria-label="Demo workspace">
        <aside className="sidebar" aria-label="Job controls">
          <form className="panel" onSubmit={handleUpload}>
            <h2>Upload</h2>
            <label>
              Video file
              <input name="videoFile" type="file" accept="video/mp4,video/*" />
            </label>
            <label>
              Target language
              <input
                value={targetLanguage}
                onChange={(event) => setTargetLanguage(event.target.value)}
                placeholder="zh-CN"
              />
            </label>
            <button type="submit" disabled={isUploading}>
              {isUploading ? 'Uploading...' : 'Upload'}
            </button>
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
                      <small>{recentJob.jobId}</small>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </aside>

        <section className="job-surface" aria-label="Selected job">
          {job ? (
            <JobDetail
              canRetry={canRetry}
              isLoadingJob={isLoadingJob}
              isRetrying={isRetrying}
              job={job}
              onRetry={handleRetry}
              selectedLanguage={selectedLanguage}
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

function JobDetail({
  canRetry,
  isLoadingJob,
  isRetrying,
  job,
  onRetry,
  selectedLanguage
}: {
  canRetry: boolean;
  isLoadingJob: boolean;
  isRetrying: boolean;
  job: LocalizationJob;
  onRetry: () => void;
  selectedLanguage: string;
}) {
  const estimatedCost = formatCost(job.usageSummary?.estimatedCostUsd ?? 0);
  const modelCallLabel = `${job.usageSummary?.modelCallCount ?? job.modelCalls.length} calls`;

  const statusItems = useMemo(
    () => [
      ['Status', job.status],
      ['Stage', job.failureStage ?? job.timelineEvents.at(-1)?.stage ?? 'Queued'],
      ['Language', selectedLanguage],
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
      </section>

      <section className="panel" aria-label="Timeline">
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

      <section className="panel" aria-label="Model calls">
        <h3>Model calls</h3>
        {job.modelCalls.length === 0 ? (
          <p className="muted">No model calls recorded yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Operation</th>
                <th>Provider</th>
                <th>Status</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {job.modelCalls.map((call) => (
                <tr key={call.modelCallId}>
                  <td>{call.operation}</td>
                  <td>{call.provider}</td>
                  <td>{call.status}</td>
                  <td>{formatCost(call.estimatedCostUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

function toRecentJob(upload: MediaUpload): RecentJob {
  return {
    jobId: upload.jobId,
    videoId: upload.videoId,
    targetLanguage: upload.targetLanguage,
    filename: upload.filename,
    createdAt: upload.createdAt
  };
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unexpected frontend error.';
}

function formatCost(value: number): string {
  return `$${value.toFixed(8)}`;
}
