import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { linguaFrameApi } from './api/linguaframeApi';
import type {
  JobArtifact,
  LocalizationJob,
  LocalizationJobStatus,
  LocalizationJobSummary,
  MediaUpload,
  SubtitleSegment,
  TranscriptSegment
} from './domain/jobTypes';
import { loadRecentJobs, RecentJob, saveRecentJob } from './domain/recentJobs';

const POLL_INTERVAL_MS = 5000;
const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED', 'CANCELLED']);
const HISTORY_LIMIT = 20;
const HISTORY_STATUSES: Array<LocalizationJobStatus | 'ALL'> = [
  'ALL',
  'QUEUED',
  'RETRYING',
  'PROCESSING',
  'COMPLETED',
  'FAILED',
  'CANCELLED'
];

export function App({ pollIntervalMs = POLL_INTERVAL_MS }: { pollIntervalMs?: number }) {
  const [targetLanguage, setTargetLanguage] = useState('zh-CN');
  const [manualJobId, setManualJobId] = useState('');
  const [selectedRecentJob, setSelectedRecentJob] = useState<RecentJob | null>(null);
  const [recentJobs, setRecentJobs] = useState<RecentJob[]>(() =>
    loadRecentJobs(window.localStorage)
  );
  const [historyStatusFilter, setHistoryStatusFilter] = useState<LocalizationJobStatus | 'ALL'>(
    'ALL'
  );
  const [history, setHistory] = useState<LocalizationJobSummary[]>([]);
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [job, setJob] = useState<LocalizationJob | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingJob, setIsLoadingJob] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [artifacts, setArtifacts] = useState<JobArtifact[]>([]);
  const [transcript, setTranscript] = useState<TranscriptSegment[]>([]);
  const [subtitles, setSubtitles] = useState<SubtitleSegment[]>([]);
  const [previewErrors, setPreviewErrors] = useState<string[]>([]);

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

  const loadHistory = useCallback(async (status: LocalizationJobStatus | 'ALL') => {
    setIsLoadingHistory(true);
    try {
      const result = await linguaFrameApi.listJobs({
        status,
        limit: HISTORY_LIMIT,
        offset: 0
      });
      setHistory(result.jobs);
      setHistoryError(null);
    } catch (historyLoadError) {
      setHistory([]);
      setHistoryError(toErrorMessage(historyLoadError));
    } finally {
      setIsLoadingHistory(false);
    }
  }, []);

  const loadPreviewData = useCallback(async (jobId: string, language: string) => {
    const errors: string[] = [];
    const [artifactResult, transcriptResult, subtitleResult] = await Promise.allSettled([
      linguaFrameApi.listArtifacts(jobId),
      linguaFrameApi.listTranscript(jobId),
      linguaFrameApi.listSubtitles(jobId, language)
    ]);

    if (artifactResult.status === 'fulfilled') {
      setArtifacts(artifactResult.value);
    } else {
      setArtifacts([]);
      errors.push(`Artifacts: ${toErrorMessage(artifactResult.reason)}`);
    }

    if (transcriptResult.status === 'fulfilled') {
      setTranscript(transcriptResult.value);
    } else {
      setTranscript([]);
      errors.push(`Transcript: ${toErrorMessage(transcriptResult.reason)}`);
    }

    if (subtitleResult.status === 'fulfilled') {
      setSubtitles(subtitleResult.value);
    } else {
      setSubtitles([]);
      errors.push(`Subtitles: ${toErrorMessage(subtitleResult.reason)}`);
    }

    setPreviewErrors(errors);
  }, []);

  useEffect(() => {
    void loadHistory(historyStatusFilter);
  }, [historyStatusFilter, loadHistory]);

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
      await loadPreviewData(upload.jobId, recentJob.targetLanguage);
      await loadHistory(historyStatusFilter);
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
    const nextJob = await loadJob(jobId);
    await loadPreviewData(jobId, nextJob.targetLanguage ?? targetLanguage);
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
      await loadHistory(historyStatusFilter);
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
    await loadPreviewData(recentJob.jobId, recentJob.targetLanguage);
  }

  async function openHistoryJob(historyJob: LocalizationJobSummary) {
    setSelectedRecentJob(null);
    setManualJobId(historyJob.jobId);
    setTargetLanguage(historyJob.targetLanguage);
    const nextJob = await loadJob(historyJob.jobId);
    await loadPreviewData(historyJob.jobId, nextJob.targetLanguage ?? historyJob.targetLanguage);
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

          <section className="panel" aria-label="Job history">
            <div className="panel-heading">
              <h2>Job history</h2>
              <button
                type="button"
                className="secondary-button"
                disabled={isLoadingHistory}
                onClick={() => void loadHistory(historyStatusFilter)}
              >
                Refresh
              </button>
            </div>
            <label>
              History status
              <select
                value={historyStatusFilter}
                onChange={(event) =>
                  setHistoryStatusFilter(event.target.value as LocalizationJobStatus | 'ALL')
                }
              >
                {HISTORY_STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
            </label>
            {historyError ? <p className="error-text">{historyError}</p> : null}
            {isLoadingHistory ? <p className="muted">Loading history...</p> : null}
            {!isLoadingHistory && history.length === 0 ? (
              <p className="muted">No server jobs match this filter.</p>
            ) : null}
            {history.length > 0 ? (
              <ul className="history-list" aria-label="Server job history">
                {history.map((historyJob) => (
                  <li key={historyJob.jobId}>
                    <button type="button" onClick={() => void openHistoryJob(historyJob)}>
                      <span className="history-title">
                        <strong>{historyJob.filename}</strong>
                        <span className="status-pill">{historyJob.status}</span>
                      </span>
                      <span className="history-meta">
                        {historyJob.targetLanguage} · {formatCost(historyJob.estimatedCostUsd)} ·
                        retry {historyJob.retryCount}
                      </span>
                      <small>{historyJob.jobId}</small>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>

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
              artifacts={artifacts}
              job={job}
              onRetry={handleRetry}
              previewErrors={previewErrors}
              selectedLanguage={selectedLanguage}
              subtitles={subtitles}
              transcript={transcript}
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
  artifacts,
  job,
  onRetry,
  previewErrors,
  selectedLanguage,
  subtitles,
  transcript
}: {
  canRetry: boolean;
  isLoadingJob: boolean;
  isRetrying: boolean;
  artifacts: JobArtifact[];
  job: LocalizationJob;
  onRetry: () => void;
  previewErrors: string[];
  selectedLanguage: string;
  subtitles: SubtitleSegment[];
  transcript: TranscriptSegment[];
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
  const dubbingAudio = artifacts.find((artifact) => artifact.type === 'DUBBING_AUDIO');
  const burnedVideo = artifacts.find((artifact) => artifact.type === 'BURNED_VIDEO');

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

      <QualityEvaluationPanel evaluation={job.qualityEvaluation} />

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

      <section className="preview-grid" aria-label="Output previews">
        <section className="panel" aria-label="Transcript preview">
          <h3>Transcript preview</h3>
          {transcript.length === 0 ? (
            <p className="muted">No transcript segments yet.</p>
          ) : (
            <SegmentList
              segments={transcript.map((segment) => ({
                key: String(segment.index),
                label: formatTimeRange(segment.startMs, segment.endMs),
                text: segment.text
              }))}
            />
          )}
        </section>

        <section className="panel" aria-label="Subtitle preview">
          <h3>Subtitle preview</h3>
          {subtitles.length === 0 ? (
            <p className="muted">No subtitle segments yet.</p>
          ) : (
            <>
              <p className="muted">{selectedLanguage}</p>
              <SegmentList
                segments={subtitles.map((segment) => ({
                  key: `${segment.language}-${segment.index}`,
                  label: formatTimeRange(segment.startMs, segment.endMs),
                  text: segment.text
                }))}
              />
            </>
          )}
        </section>
      </section>

      <section className="panel" aria-label="Artifacts">
        <h3>Artifacts</h3>
        {previewErrors.length > 0 ? (
          <ul className="error-list">
            {previewErrors.map((previewError) => (
              <li key={previewError}>{previewError}</li>
            ))}
          </ul>
        ) : null}
        {artifacts.length === 0 ? (
          <p className="muted">No artifacts yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Type</th>
                <th>Filename</th>
                <th>Content type</th>
                <th>Size</th>
                <th>Download</th>
              </tr>
            </thead>
            <tbody>
              {artifacts.map((artifact) => (
                <tr key={artifact.artifactId}>
                  <td>{artifact.type}</td>
                  <td>{artifact.filename}</td>
                  <td>{artifact.contentType}</td>
                  <td>{formatBytes(artifact.sizeBytes)}</td>
                  <td>
                    <a href={linguaFrameApi.artifactDownloadUrl(job.jobId, artifact.artifactId)}>
                      Download {artifact.filename}
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {dubbingAudio || burnedVideo ? (
        <section className="media-grid" aria-label="Media previews">
          {dubbingAudio ? (
            <section className="panel">
              <h3>Dubbing audio</h3>
              <audio
                aria-label="Dubbing audio preview"
                controls
                src={linguaFrameApi.artifactDownloadUrl(job.jobId, dubbingAudio.artifactId)}
              />
            </section>
          ) : null}
          {burnedVideo ? (
            <section className="panel">
              <h3>Burned video</h3>
              <video
                aria-label="Burned video preview"
                controls
                src={linguaFrameApi.artifactDownloadUrl(job.jobId, burnedVideo.artifactId)}
              />
            </section>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function QualityEvaluationPanel({
  evaluation
}: {
  evaluation: LocalizationJob['qualityEvaluation'];
}) {
  if (!evaluation) {
    return (
      <section className="panel" aria-label="Quality evaluation">
        <h3>Quality evaluation</h3>
        <p className="muted">No quality evaluation recorded yet.</p>
      </section>
    );
  }

  const dimensionScores = [
    ['Completeness', evaluation.completeness],
    ['Readability', evaluation.readability],
    ['Timing', evaluation.timingPreservation],
    ['Naturalness', evaluation.naturalness]
  ];

  return (
    <section className="panel" aria-label="Quality evaluation">
      <div className="panel-heading">
        <h3>Quality evaluation</h3>
        <span className="status-pill">{evaluation.status}</span>
      </div>
      {evaluation.status === 'FAILED' && evaluation.safeErrorSummary ? (
        <p className="error-text">{evaluation.safeErrorSummary}</p>
      ) : null}
      <div className="quality-overview">
        <div>
          <span>Score</span>
          <strong>{evaluation.score} / 100</strong>
        </div>
        <div>
          <span>Verdict</span>
          <strong>{evaluation.verdict}</strong>
        </div>
        <div>
          <span>Language</span>
          <strong>{evaluation.language}</strong>
        </div>
      </div>
      <dl className="quality-dimensions">
        {dimensionScores.map(([label, score]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{score}</dd>
          </div>
        ))}
      </dl>
      <div className="quality-lists">
        <div>
          <h4>Issues</h4>
          {evaluation.issues.length === 0 ? (
            <p className="muted">No issues recorded.</p>
          ) : (
            <ul>
              {evaluation.issues.map((issue) => (
                <li key={issue}>{issue}</li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <h4>Suggested fixes</h4>
          {evaluation.suggestedFixes.length === 0 ? (
            <p className="muted">No fixes suggested.</p>
          ) : (
            <ul>
              {evaluation.suggestedFixes.map((fix) => (
                <li key={fix}>{fix}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
}

function SegmentList({
  segments
}: {
  segments: Array<{ key: string; label: string; text: string }>;
}) {
  return (
    <ol className="segment-list">
      {segments.map((segment) => (
        <li key={segment.key}>
          <span>{segment.label}</span>
          <p>{segment.text}</p>
        </li>
      ))}
    </ol>
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

function formatTimeRange(startMs: number, endMs: number): string {
  return `${formatTimestamp(startMs)} - ${formatTimestamp(endMs)}`;
}

function formatTimestamp(valueMs: number): string {
  const minutes = Math.floor(valueMs / 60000);
  const seconds = Math.floor((valueMs % 60000) / 1000);
  const milliseconds = valueMs % 1000;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}.${String(
    milliseconds
  ).padStart(3, '0')}`;
}

function formatBytes(sizeBytes: number): string {
  if (sizeBytes < 1024) {
    return `${sizeBytes} B`;
  }
  if (sizeBytes < 1024 * 1024) {
    return `${(sizeBytes / 1024).toFixed(1)} KB`;
  }
  return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
}
