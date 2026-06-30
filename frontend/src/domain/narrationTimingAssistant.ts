import type { NarrationWorkspace } from './jobTypes';

export type NarrationTimingAssistantIssueType = 'gap' | 'overlap';

export interface NarrationTimingAssistantOptions {
  minimumReportGapSeconds?: number;
  targetGapSeconds?: number;
}

export interface NarrationTimingAssistantIssue {
  type: NarrationTimingAssistantIssueType;
  rowIndex: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  message: string;
}

export interface NarrationTimingAssistantReport {
  segmentCount: number;
  gapCount: number;
  totalGapSeconds: number;
  longestGapSeconds: number;
  overlapCount: number;
  generationReady: boolean;
  canCloseGaps: boolean;
  canResolveOverlaps: boolean;
  canNormalizeOrder: boolean;
  issues: NarrationTimingAssistantIssue[];
}

const DEFAULT_TARGET_GAP_SECONDS = 0.25;
const DEFAULT_MINIMUM_REPORT_GAP_SECONDS = 0.25;

export function buildNarrationTimingAssistantReport(
  segments: NarrationWorkspace['segments'],
  options: NarrationTimingAssistantOptions = {}
): NarrationTimingAssistantReport {
  const sortedSegments = sortAndNormalizeSegments(segments);
  const minimumReportGapSeconds = options.minimumReportGapSeconds ?? DEFAULT_MINIMUM_REPORT_GAP_SECONDS;
  const issues: NarrationTimingAssistantIssue[] = [];
  let totalGapSeconds = 0;
  let longestGapSeconds = 0;
  let overlapCount = 0;

  for (let index = 1; index < sortedSegments.length; index += 1) {
    const previous = sortedSegments[index - 1];
    const current = sortedSegments[index];
    const gapSeconds = roundSeconds(current.startSeconds - previous.endSeconds);
    if (gapSeconds >= minimumReportGapSeconds) {
      totalGapSeconds = roundSeconds(totalGapSeconds + gapSeconds);
      longestGapSeconds = Math.max(longestGapSeconds, gapSeconds);
      issues.push({
        type: 'gap',
        rowIndex: current.index,
        startSeconds: previous.endSeconds,
        endSeconds: current.startSeconds,
        durationSeconds: gapSeconds,
        message: `Gap before row ${current.index + 1}: ${formatSeconds(gapSeconds)}.`
      });
    }
    if (gapSeconds < 0) {
      const overlapSeconds = roundSeconds(Math.abs(gapSeconds));
      overlapCount += 1;
      issues.push({
        type: 'overlap',
        rowIndex: current.index,
        startSeconds: current.startSeconds,
        endSeconds: previous.endSeconds,
        durationSeconds: overlapSeconds,
        message: `Row ${current.index + 1} overlaps the previous row by ${formatSeconds(overlapSeconds)}.`
      });
    }
  }

  const gapCount = issues.filter((issue) => issue.type === 'gap').length;
  const hasInvalidRange = sortedSegments.some((segment) => segment.endSeconds <= segment.startSeconds);
  const hasBlankText = sortedSegments.some((segment) => !segment.text.trim());

  return {
    segmentCount: sortedSegments.length,
    gapCount,
    totalGapSeconds: roundSeconds(totalGapSeconds),
    longestGapSeconds: roundSeconds(longestGapSeconds),
    overlapCount,
    generationReady: sortedSegments.length > 0 && !hasInvalidRange && !hasBlankText && overlapCount === 0,
    canCloseGaps: gapCount > 0,
    canResolveOverlaps: overlapCount > 0,
    canNormalizeOrder: hasNonSequentialOrder(sortedSegments),
    issues
  };
}

export function closeNarrationDraftGaps(
  segments: NarrationWorkspace['segments'],
  options: NarrationTimingAssistantOptions = {}
): NarrationWorkspace['segments'] {
  return packSegments(segments, options, 'gaps');
}

export function resolveNarrationDraftOverlaps(
  segments: NarrationWorkspace['segments'],
  options: NarrationTimingAssistantOptions = {}
): NarrationWorkspace['segments'] {
  return packSegments(segments, options, 'overlaps');
}

export function normalizeNarrationDraftOrder(
  segments: NarrationWorkspace['segments'],
  options: NarrationTimingAssistantOptions = {}
): NarrationWorkspace['segments'] {
  return sortAndNormalizeSegments(segments, options.targetGapSeconds).map((segment, index) => ({
    ...segment,
    index
  }));
}

function packSegments(
  segments: NarrationWorkspace['segments'],
  options: NarrationTimingAssistantOptions,
  mode: 'gaps' | 'overlaps'
): NarrationWorkspace['segments'] {
  const targetGapSeconds = options.targetGapSeconds ?? DEFAULT_TARGET_GAP_SECONDS;
  const sortedSegments = sortAndNormalizeSegments(segments, targetGapSeconds);
  if (sortedSegments.length === 0) {
    return [];
  }

  const packed: NarrationWorkspace['segments'] = [];
  sortedSegments.forEach((segment, index) => {
    const durationSeconds = getDurationSeconds(segment);
    if (index === 0) {
      packed.push({ ...segment, index: 0, durationSeconds });
      return;
    }

    const previous = packed[index - 1];
    const preferredStart = mode === 'gaps'
      ? roundSeconds(previous.endSeconds + targetGapSeconds)
      : Math.max(segment.startSeconds, roundSeconds(previous.endSeconds + targetGapSeconds));
    const startSeconds = roundSeconds(preferredStart);
    const endSeconds = roundSeconds(startSeconds + durationSeconds);
    packed.push({
      ...segment,
      index,
      startSeconds,
      endSeconds,
      durationSeconds: roundSeconds(durationSeconds)
    });
  });
  return packed;
}

function sortAndNormalizeSegments(
  segments: NarrationWorkspace['segments'],
  snapSeconds = DEFAULT_TARGET_GAP_SECONDS
): NarrationWorkspace['segments'] {
  return [...segments]
    .map((segment) => {
      const startSeconds = snapToStep(Math.max(0, segment.startSeconds), snapSeconds);
      const durationSeconds = getDurationSeconds(segment);
      const endSeconds = roundSeconds(startSeconds + durationSeconds);
      return {
        ...segment,
        startSeconds,
        endSeconds,
        durationSeconds
      };
    })
    .sort((left, right) => left.startSeconds - right.startSeconds || left.index - right.index);
}

function hasNonSequentialOrder(segments: NarrationWorkspace['segments']): boolean {
  return segments.some((segment, index) => segment.index !== index);
}

function getDurationSeconds(segment: NarrationWorkspace['segments'][number]): number {
  return roundSeconds(Math.max(0.25, segment.endSeconds - segment.startSeconds));
}

function snapToStep(value: number, step: number): number {
  if (step <= 0) {
    return roundSeconds(value);
  }
  return roundSeconds(Math.round(value / step) * step);
}

function roundSeconds(value: number): number {
  return Number(value.toFixed(3));
}

function formatSeconds(value: number): string {
  return `${Number(value.toFixed(3))} s`;
}
