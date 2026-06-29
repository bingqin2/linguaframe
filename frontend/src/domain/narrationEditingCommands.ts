import type { NarrationWorkspace } from './jobTypes';

type NarrationSegment = NarrationWorkspace['segments'][number];

export interface NarrationEditCommandResult {
  segments: NarrationWorkspace['segments'];
  selectedIndex: number;
  blockedReason: string | null;
}

const DEFAULT_INSERT_DURATION_SECONDS = 5;
const MIN_SPLIT_SIDE_SECONDS = 0.25;

export function duplicateNarrationSegment(
  segments: NarrationWorkspace['segments'],
  selectedIndex: number
): NarrationEditCommandResult {
  const selected = segments[selectedIndex];
  if (!selected) {
    return blocked(segments, selectedIndex, 'No selected narration row.');
  }

  const durationSeconds = roundSeconds(selected.endSeconds - selected.startSeconds);
  const copy: NarrationSegment = normalizeTiming({
    ...selected,
    startSeconds: selected.endSeconds,
    endSeconds: selected.endSeconds + durationSeconds,
    durationSeconds,
    updatedAt: null
  });
  const nextSegments = [
    ...segments.slice(0, selectedIndex + 1),
    copy,
    ...segments.slice(selectedIndex + 1)
  ];

  return {
    segments: reindexNarrationSegments(nextSegments),
    selectedIndex: selectedIndex + 1,
    blockedReason: null
  };
}

export function splitNarrationSegmentAtTime(
  segments: NarrationWorkspace['segments'],
  selectedIndex: number,
  splitSeconds: number
): NarrationEditCommandResult {
  const selected = segments[selectedIndex];
  if (!selected) {
    return blocked(segments, selectedIndex, 'No selected narration row.');
  }

  const roundedSplitSeconds = roundSeconds(splitSeconds);
  if (
    roundedSplitSeconds - selected.startSeconds < MIN_SPLIT_SIDE_SECONDS
    || selected.endSeconds - roundedSplitSeconds < MIN_SPLIT_SIDE_SECONDS
  ) {
    return blocked(segments, selectedIndex, 'Split time must leave at least 0.25 seconds on both sides.');
  }

  const [leftText, rightText] = splitTextNearMidpoint(selected.text);
  const left = normalizeTiming({
    ...selected,
    endSeconds: roundedSplitSeconds,
    durationSeconds: roundedSplitSeconds - selected.startSeconds,
    text: leftText,
    characterCount: leftText.length,
    updatedAt: null
  });
  const right = normalizeTiming({
    ...selected,
    startSeconds: roundedSplitSeconds,
    durationSeconds: selected.endSeconds - roundedSplitSeconds,
    text: rightText,
    characterCount: rightText.length,
    updatedAt: null
  });
  const nextSegments = [
    ...segments.slice(0, selectedIndex),
    left,
    right,
    ...segments.slice(selectedIndex + 1)
  ];

  return {
    segments: reindexNarrationSegments(nextSegments),
    selectedIndex: selectedIndex + 1,
    blockedReason: null
  };
}

export function mergeNarrationSegmentWithNext(
  segments: NarrationWorkspace['segments'],
  selectedIndex: number
): NarrationEditCommandResult {
  const selected = segments[selectedIndex];
  if (!selected) {
    return blocked(segments, selectedIndex, 'No selected narration row.');
  }
  const next = segments[selectedIndex + 1];
  if (!next) {
    return blocked(segments, selectedIndex, 'No following narration row to merge.');
  }

  const text = [selected.text.trim(), next.text.trim()].filter(Boolean).join('\n\n');
  const merged = normalizeTiming({
    ...selected,
    endSeconds: next.endSeconds,
    durationSeconds: next.endSeconds - selected.startSeconds,
    text,
    characterCount: text.length,
    updatedAt: null
  });
  const nextSegments = [
    ...segments.slice(0, selectedIndex),
    merged,
    ...segments.slice(selectedIndex + 2)
  ];

  return {
    segments: reindexNarrationSegments(nextSegments),
    selectedIndex,
    blockedReason: null
  };
}

export function insertNarrationSegmentAfter(
  segments: NarrationWorkspace['segments'],
  selectedIndex: number
): NarrationEditCommandResult {
  const selected = segments[selectedIndex] ?? null;
  const insertIndex = selected ? selectedIndex + 1 : segments.length;
  const startSeconds = selected ? selected.endSeconds : 0;
  const inserted = normalizeTiming({
    index: insertIndex,
    startSeconds,
    endSeconds: startSeconds + DEFAULT_INSERT_DURATION_SECONDS,
    durationSeconds: DEFAULT_INSERT_DURATION_SECONDS,
    text: '',
    voice: null,
    characterCount: 0,
    updatedAt: null
  });
  const nextSegments = [
    ...segments.slice(0, insertIndex),
    inserted,
    ...segments.slice(insertIndex)
  ];

  return {
    segments: reindexNarrationSegments(nextSegments),
    selectedIndex: insertIndex,
    blockedReason: null
  };
}

export function reindexNarrationSegments(segments: NarrationWorkspace['segments']): NarrationWorkspace['segments'] {
  return segments.map((segment, index) => normalizeTiming({
    ...segment,
    index,
    characterCount: segment.text.length
  }));
}

function blocked(
  segments: NarrationWorkspace['segments'],
  selectedIndex: number,
  blockedReason: string
): NarrationEditCommandResult {
  return {
    segments,
    selectedIndex,
    blockedReason
  };
}

function normalizeTiming(segment: NarrationSegment): NarrationSegment {
  const startSeconds = roundSeconds(segment.startSeconds);
  const endSeconds = roundSeconds(segment.endSeconds);
  return {
    ...segment,
    startSeconds,
    endSeconds,
    durationSeconds: roundSeconds(endSeconds - startSeconds),
    characterCount: segment.text.length
  };
}

function splitTextNearMidpoint(text: string): [string, string] {
  const trimmed = text.trim();
  if (!trimmed) {
    return ['', ''];
  }

  const midpoint = Math.floor(trimmed.length / 2);
  const before = trimmed.lastIndexOf(' ', midpoint);
  const after = trimmed.indexOf(' ', midpoint + 1);
  const splitIndex = chooseNearestSplitIndex(trimmed.length, midpoint, before, after);

  if (splitIndex <= 0 || splitIndex >= trimmed.length) {
    return [trimmed, ''];
  }

  return [
    trimmed.slice(0, splitIndex).trim(),
    trimmed.slice(splitIndex).trim()
  ];
}

function chooseNearestSplitIndex(length: number, midpoint: number, before: number, after: number): number {
  if (before < 0 && after < 0) {
    return length;
  }
  if (before < 0) {
    return after;
  }
  if (after < 0) {
    return before;
  }
  return midpoint - before <= after - midpoint ? before : after;
}

function roundSeconds(value: number): number {
  return Number(value.toFixed(3));
}
