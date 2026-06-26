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
  createdAt: string;
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
  modelCalls: ModelCall[];
  qualityEvaluation: QualityEvaluation | null;
}

export interface LocalizationJobSummary {
  jobId: string;
  videoId: string;
  filename: string;
  targetLanguage: string;
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
  | 'TRANSLATION_QUALITY_EVALUATION'
  | 'DUBBING_AUDIO_GENERATION'
  | 'SUBTITLE_BURN_IN'
  | 'ARTIFACT_SUMMARY'
  | 'COMPLETED';

export interface JobTimelineEvent {
  id: string;
  stage: LocalizationJobStage;
  status: 'STARTED' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
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

export interface ModelCall {
  modelCallId: string;
  jobId: string;
  stage: LocalizationJobStage;
  operation: 'TRANSCRIPTION' | 'TRANSLATION' | 'EVALUATION' | 'TTS';
  provider: 'DEMO' | 'OPENAI';
  model: string;
  promptVersion: string;
  status: 'SUCCEEDED' | 'FAILED';
  latencyMs: number;
  inputTokens: number | null;
  outputTokens: number | null;
  audioSeconds: number | null;
  characterCount: number | null;
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
    | 'DUBBING_AUDIO'
    | 'BURNED_VIDEO'
    | 'WORKER_SUMMARY';
  filename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
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
