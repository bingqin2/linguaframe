import type { NarrationWorkspace } from './jobTypes';

export interface NarrationWaveformBucket {
  index: number;
  startSeconds: number;
  endSeconds: number;
  heightPercent: number;
  active: boolean;
  gap: boolean;
  selected: boolean;
}

export interface NarrationWaveformOverview {
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  bucketCount: number;
  buckets: NarrationWaveformBucket[];
  selectedStartPercent: number | null;
  selectedEndPercent: number | null;
  playheadPercent: number | null;
  gapBucketCount: number;
  activeBucketCount: number;
}

const DEFAULT_BUCKET_COUNT = 48;
const SILENCE_HEIGHT_PERCENT = 8;

export function buildNarrationWaveformOverview(input: {
  segments: NarrationWorkspace['segments'];
  selectedIndex: number;
  currentSeconds: number;
  bucketCount?: number;
}): NarrationWaveformOverview {
  const bucketCount = Math.max(1, Math.floor(input.bucketCount ?? DEFAULT_BUCKET_COUNT));
  if (input.segments.length === 0) {
    const buckets = Array.from({ length: bucketCount }, (_, index) => ({
      index,
      startSeconds: 0,
      endSeconds: 0,
      heightPercent: SILENCE_HEIGHT_PERCENT,
      active: false,
      gap: true,
      selected: false
    }));
    return {
      startSeconds: 0,
      endSeconds: 0,
      durationSeconds: 0,
      bucketCount,
      buckets,
      selectedStartPercent: null,
      selectedEndPercent: null,
      playheadPercent: null,
      gapBucketCount: bucketCount,
      activeBucketCount: 0
    };
  }

  const sortedSegments = [...input.segments].sort((left, right) =>
    left.startSeconds - right.startSeconds || left.index - right.index
  );
  const startSeconds = sortedSegments[0].startSeconds;
  const endSeconds = Math.max(...sortedSegments.map((segment) => segment.endSeconds));
  const durationSeconds = roundSeconds(Math.max(0, endSeconds - startSeconds));
  const spanSeconds = Math.max(durationSeconds, 0.001);
  const selectedSegment = input.segments.find((segment) => segment.index === input.selectedIndex) ?? null;
  const buckets = Array.from({ length: bucketCount }, (_, index) => {
    const bucketStartSeconds = roundSeconds(startSeconds + (spanSeconds * index) / bucketCount);
    const bucketEndSeconds = roundSeconds(startSeconds + (spanSeconds * (index + 1)) / bucketCount);
    const bucketDurationSeconds = Math.max(0.001, bucketEndSeconds - bucketStartSeconds);
    const overlappingSegments = sortedSegments.filter((segment) =>
      segment.startSeconds < bucketEndSeconds && bucketStartSeconds < segment.endSeconds
    );
    const coverageSeconds = overlappingSegments.reduce((sum, segment) =>
      sum + Math.max(0, Math.min(segment.endSeconds, bucketEndSeconds) - Math.max(segment.startSeconds, bucketStartSeconds)),
    0);
    const coverageRatio = clamp(coverageSeconds / bucketDurationSeconds, 0, 1);
    const textDensity = overlappingSegments.reduce((sum, segment) =>
      sum + Math.min(1, segment.text.trim().length / 40),
    0);
    const active = coverageRatio > 0;
    const selected = selectedSegment
      ? selectedSegment.startSeconds < bucketEndSeconds && bucketStartSeconds < selectedSegment.endSeconds
      : false;

    return {
      index,
      startSeconds: bucketStartSeconds,
      endSeconds: bucketEndSeconds,
      heightPercent: active ? clamp(Math.round(8 + coverageRatio * 56 + Math.min(1, textDensity) * 36), 8, 100) : 8,
      active,
      gap: !active,
      selected
    };
  });

  return {
    startSeconds,
    endSeconds,
    durationSeconds,
    bucketCount,
    buckets,
    selectedStartPercent: selectedSegment ? percentOfTimeline(selectedSegment.startSeconds, startSeconds, endSeconds) : null,
    selectedEndPercent: selectedSegment ? percentOfTimeline(selectedSegment.endSeconds, startSeconds, endSeconds) : null,
    playheadPercent: percentOfTimeline(input.currentSeconds, startSeconds, endSeconds),
    gapBucketCount: buckets.filter((bucket) => bucket.gap).length,
    activeBucketCount: buckets.filter((bucket) => bucket.active).length
  };
}

export function secondsFromWaveformPercent(input: {
  percent: number;
  startSeconds: number;
  endSeconds: number;
}): number {
  const spanSeconds = Math.max(0, input.endSeconds - input.startSeconds);
  if (spanSeconds === 0) {
    return roundSeconds(input.startSeconds);
  }
  return roundSeconds(input.startSeconds + spanSeconds * (clamp(input.percent, 0, 100) / 100));
}

function percentOfTimeline(seconds: number, startSeconds: number, endSeconds: number): number {
  const spanSeconds = endSeconds - startSeconds;
  if (spanSeconds <= 0) {
    return 0;
  }
  return roundPercent(clamp(((seconds - startSeconds) / spanSeconds) * 100, 0, 100));
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function roundSeconds(value: number): number {
  return Number(value.toFixed(3));
}

function roundPercent(value: number): number {
  return Number(value.toFixed(4));
}
