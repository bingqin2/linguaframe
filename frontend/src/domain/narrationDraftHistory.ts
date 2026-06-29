import type { NarrationWorkspace } from './jobTypes';

type NarrationSegment = NarrationWorkspace['segments'][number];

export interface NarrationDraftHistoryState {
  saved: NarrationWorkspace['segments'];
  past: NarrationWorkspace['segments'][];
  present: NarrationWorkspace['segments'];
  future: NarrationWorkspace['segments'][];
  lastActionLabel: string | null;
}

export interface NarrationDraftChangeSummary {
  dirty: boolean;
  addedCount: number;
  removedCount: number;
  timingChangedCount: number;
  textChangedCount: number;
  voiceChangedCount: number;
  changedRowLabels: string[];
}

export function createNarrationDraftHistory(segments: NarrationWorkspace['segments']): NarrationDraftHistoryState {
  const snapshot = cloneSegments(segments);
  return {
    saved: cloneSegments(snapshot),
    past: [],
    present: cloneSegments(snapshot),
    future: [],
    lastActionLabel: null
  };
}

export function applyNarrationDraftChange(
  state: NarrationDraftHistoryState,
  nextSegments: NarrationWorkspace['segments'],
  actionLabel: string
): NarrationDraftHistoryState {
  return {
    saved: cloneSegments(state.saved),
    past: [...cloneSnapshotStack(state.past), cloneSegments(state.present)],
    present: cloneSegments(nextSegments),
    future: [],
    lastActionLabel: actionLabel
  };
}

export function undoNarrationDraftChange(state: NarrationDraftHistoryState): NarrationDraftHistoryState {
  if (state.past.length === 0) {
    return state;
  }

  const previous = state.past[state.past.length - 1];
  return {
    saved: cloneSegments(state.saved),
    past: cloneSnapshotStack(state.past.slice(0, -1)),
    present: cloneSegments(previous),
    future: [cloneSegments(state.present), ...cloneSnapshotStack(state.future)],
    lastActionLabel: 'Undo narration draft change.'
  };
}

export function redoNarrationDraftChange(state: NarrationDraftHistoryState): NarrationDraftHistoryState {
  if (state.future.length === 0) {
    return state;
  }

  const next = state.future[0];
  return {
    saved: cloneSegments(state.saved),
    past: [...cloneSnapshotStack(state.past), cloneSegments(state.present)],
    present: cloneSegments(next),
    future: cloneSnapshotStack(state.future.slice(1)),
    lastActionLabel: 'Redo narration draft change.'
  };
}

export function resetNarrationDraftToSaved(state: NarrationDraftHistoryState): NarrationDraftHistoryState {
  return {
    saved: cloneSegments(state.saved),
    past: [],
    present: cloneSegments(state.saved),
    future: [],
    lastActionLabel: 'Reverted to saved narration.'
  };
}

export function markNarrationDraftSaved(
  _state: NarrationDraftHistoryState,
  savedSegments: NarrationWorkspace['segments']
): NarrationDraftHistoryState {
  const snapshot = cloneSegments(savedSegments);
  return {
    saved: cloneSegments(snapshot),
    past: [],
    present: cloneSegments(snapshot),
    future: [],
    lastActionLabel: 'Narration saved.'
  };
}

export function summarizeNarrationDraftChanges(
  saved: NarrationWorkspace['segments'],
  present: NarrationWorkspace['segments']
): NarrationDraftChangeSummary {
  const savedByIndex = new Map(saved.map((segment, position) => [segment.index, { segment, position }]));
  const presentByIndex = new Map(present.map((segment, position) => [segment.index, { segment, position }]));
  const changedRowLabels: string[] = [];
  let addedCount = 0;
  let removedCount = 0;
  let timingChangedCount = 0;
  let textChangedCount = 0;
  let voiceChangedCount = 0;

  saved.forEach((savedSegment, position) => {
    const presentEntry = presentByIndex.get(savedSegment.index) ?? findMatchingPosition(present, position, savedSegment.index);
    if (!presentEntry) {
      removedCount += 1;
      changedRowLabels.push(`${formatRowLabel(savedSegment.index)} removed`);
      return;
    }

    const timingChanged = !sameTiming(savedSegment, presentEntry.segment);
    const textChanged = savedSegment.text !== presentEntry.segment.text;
    const voiceChanged = (savedSegment.voice ?? '') !== (presentEntry.segment.voice ?? '');
    if (timingChanged || textChanged || voiceChanged) {
      changedRowLabels.push(formatRowLabel(savedSegment.index));
      if (timingChanged) {
        timingChangedCount += 1;
      }
      if (textChanged) {
        textChangedCount += 1;
      }
      if (voiceChanged) {
        voiceChangedCount += 1;
      }
    }
  });

  present.forEach((presentSegment, position) => {
    const savedEntry = savedByIndex.get(presentSegment.index) ?? findMatchingPosition(saved, position, presentSegment.index);
    if (!savedEntry) {
      addedCount += 1;
      changedRowLabels.push(`${formatRowLabel(presentSegment.index)} added`);
    }
  });

  return {
    dirty: addedCount > 0 || removedCount > 0 || timingChangedCount > 0 || textChangedCount > 0 || voiceChangedCount > 0,
    addedCount,
    removedCount,
    timingChangedCount,
    textChangedCount,
    voiceChangedCount,
    changedRowLabels
  };
}

function cloneSnapshotStack(stack: NarrationWorkspace['segments'][]): NarrationWorkspace['segments'][] {
  return stack.map(cloneSegments);
}

function cloneSegments(segments: NarrationWorkspace['segments']): NarrationWorkspace['segments'] {
  return segments.map((segment) => ({ ...segment }));
}

function findMatchingPosition(
  segments: NarrationWorkspace['segments'],
  position: number,
  expectedIndex: number
): { segment: NarrationSegment; position: number } | null {
  const segment = segments[position];
  return segment && segment.index === expectedIndex ? { segment, position } : null;
}

function sameTiming(saved: NarrationSegment, present: NarrationSegment): boolean {
  return saved.startSeconds === present.startSeconds
    && saved.endSeconds === present.endSeconds
    && saved.durationSeconds === present.durationSeconds;
}

function formatRowLabel(index: number): string {
  return `Narration ${index + 1}`;
}
