import type { NarrationWorkspace } from './jobTypes';

export type NarrationTimelineEditMode = 'move' | 'resize-start' | 'resize-end';

export interface NarrationTimelineEditInput {
  segment: NarrationWorkspace['segments'][number];
  mode: NarrationTimelineEditMode;
  pointerDeltaPx: number;
  trackWidthPx: number;
  timelineStartSeconds: number;
  timelineEndSeconds: number;
  minDurationSeconds?: number;
  snapSeconds?: number;
}

const DEFAULT_MIN_DURATION_SECONDS = 0.25;
const DEFAULT_SNAP_SECONDS = 0.25;

export function editNarrationTimelineSegment(input: NarrationTimelineEditInput): NarrationWorkspace['segments'][number] {
  const minDurationSeconds = input.minDurationSeconds ?? DEFAULT_MIN_DURATION_SECONDS;
  const snapSeconds = input.snapSeconds ?? DEFAULT_SNAP_SECONDS;
  const timelineSpanSeconds = Math.max(input.timelineEndSeconds - input.timelineStartSeconds, minDurationSeconds);
  const secondsPerPixel = timelineSpanSeconds / Math.max(input.trackWidthPx, 1);
  const deltaSeconds = snapToStep(input.pointerDeltaPx * secondsPerPixel, snapSeconds);
  const durationSeconds = Math.max(input.segment.endSeconds - input.segment.startSeconds, minDurationSeconds);

  if (input.mode === 'move') {
    const unclampedStart = snapToStep(input.segment.startSeconds + deltaSeconds, snapSeconds);
    const startSeconds = clamp(unclampedStart, input.timelineStartSeconds, input.timelineEndSeconds - durationSeconds);
    return normalizeNarrationSegmentTiming({
      ...input.segment,
      startSeconds,
      endSeconds: startSeconds + durationSeconds
    }, snapSeconds, minDurationSeconds);
  }

  if (input.mode === 'resize-start') {
    return normalizeNarrationSegmentTiming({
      ...input.segment,
      startSeconds: clamp(
        input.segment.startSeconds + deltaSeconds,
        input.timelineStartSeconds,
        input.segment.endSeconds - minDurationSeconds
      )
    }, snapSeconds, minDurationSeconds);
  }

  return normalizeNarrationSegmentTiming({
    ...input.segment,
    endSeconds: clamp(
      input.segment.endSeconds + deltaSeconds,
      input.segment.startSeconds + minDurationSeconds,
      input.timelineEndSeconds
    )
  }, snapSeconds, minDurationSeconds);
}

export function normalizeNarrationSegmentTiming(
  segment: NarrationWorkspace['segments'][number],
  snapSeconds = DEFAULT_SNAP_SECONDS,
  minDurationSeconds = DEFAULT_MIN_DURATION_SECONDS
): NarrationWorkspace['segments'][number] {
  const startSeconds = snapToStep(Math.max(0, segment.startSeconds), snapSeconds);
  const snappedEndSeconds = snapToStep(Math.max(startSeconds + minDurationSeconds, segment.endSeconds), snapSeconds);
  const endSeconds = Math.max(startSeconds + minDurationSeconds, snappedEndSeconds);
  return {
    ...segment,
    startSeconds: roundSeconds(startSeconds),
    endSeconds: roundSeconds(endSeconds),
    durationSeconds: roundSeconds(endSeconds - startSeconds)
  };
}

export function buildLocalNarrationTimeline(segments: NarrationWorkspace['segments']): NarrationWorkspace['timeline'] {
  if (segments.length === 0) {
    return {
      startSeconds: 0,
      endSeconds: 0,
      totalSpanSeconds: 0,
      coveredSeconds: 0,
      gapSeconds: 0,
      gapCount: 0,
      hasOverlap: false,
      generationReady: false,
      segments: []
    };
  }

  const sortedSegments = [...segments]
    .map((segment) => normalizeNarrationSegmentTiming(segment))
    .sort((left, right) => left.startSeconds - right.startSeconds || left.index - right.index);
  const startSeconds = sortedSegments[0].startSeconds;
  const endSeconds = Math.max(...sortedSegments.map((segment) => segment.endSeconds));
  const totalSpanSeconds = roundSeconds(endSeconds - startSeconds);
  let coveredSeconds = 0;
  let gapSeconds = 0;
  let gapCount = 0;
  let hasOverlap = false;
  let previousEnd = sortedSegments[0].startSeconds;

  sortedSegments.forEach((segment) => {
    coveredSeconds += Math.max(0, segment.endSeconds - segment.startSeconds);
    if (segment.startSeconds > previousEnd) {
      gapCount += 1;
      gapSeconds += segment.startSeconds - previousEnd;
    }
    if (segment.startSeconds < previousEnd) {
      hasOverlap = true;
    }
    previousEnd = Math.max(previousEnd, segment.endSeconds);
  });

  const timelineSegments = sortedSegments.map((segment) => {
    const span = Math.max(totalSpanSeconds, DEFAULT_MIN_DURATION_SECONDS);
    return {
      index: segment.index,
      startSeconds: segment.startSeconds,
      endSeconds: segment.endSeconds,
      durationSeconds: segment.durationSeconds,
      leftPercent: roundPercent(((segment.startSeconds - startSeconds) / span) * 100),
      widthPercent: roundPercent(((segment.endSeconds - segment.startSeconds) / span) * 100),
      status: segment.endSeconds <= segment.startSeconds ? 'INVALID' : hasOverlapForSegment(segment, sortedSegments) ? 'OVERLAP' : 'READY',
      characterCount: segment.text.length,
      voice: segment.voice ?? ''
    };
  });

  return {
    startSeconds,
    endSeconds,
    totalSpanSeconds,
    coveredSeconds: roundSeconds(coveredSeconds),
    gapSeconds: roundSeconds(gapSeconds),
    gapCount,
    hasOverlap,
    generationReady: !hasOverlap && sortedSegments.every((segment) => segment.text.trim() && segment.endSeconds > segment.startSeconds),
    segments: timelineSegments
  };
}

function hasOverlapForSegment(
  segment: NarrationWorkspace['segments'][number],
  segments: NarrationWorkspace['segments']
): boolean {
  return segments.some((other) =>
    other.index !== segment.index
    && segment.startSeconds < other.endSeconds
    && other.startSeconds < segment.endSeconds
  );
}

function snapToStep(value: number, step: number): number {
  if (step <= 0) {
    return roundSeconds(value);
  }
  return roundSeconds(Math.round(value / step) * step);
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
