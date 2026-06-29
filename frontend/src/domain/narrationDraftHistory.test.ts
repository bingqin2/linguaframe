import { describe, expect, test } from 'vitest';

import type { NarrationWorkspace } from './jobTypes';
import {
  applyNarrationDraftChange,
  createNarrationDraftHistory,
  markNarrationDraftSaved,
  redoNarrationDraftChange,
  resetNarrationDraftToSaved,
  summarizeNarrationDraftChanges,
  undoNarrationDraftChange
} from './narrationDraftHistory';

const savedSegments: NarrationWorkspace['segments'] = [
  {
    index: 0,
    startSeconds: 15,
    endSeconds: 28,
    durationSeconds: 13,
    text: 'Explain the first scene.',
    voice: 'alloy',
    characterCount: 24,
    updatedAt: '2026-06-30T00:00:00Z'
  },
  {
    index: 1,
    startSeconds: 55,
    endSeconds: 70.5,
    durationSeconds: 15.5,
    text: 'Explain the second scene.',
    voice: 'verse',
    characterCount: 25,
    updatedAt: '2026-06-30T00:01:00Z'
  }
];

describe('narrationDraftHistory', () => {
  test('creates independent saved and present snapshots from backend rows', () => {
    const history = createNarrationDraftHistory(savedSegments);

    expect(history.saved).toEqual(savedSegments);
    expect(history.present).toEqual(savedSegments);
    expect(history.saved).not.toBe(savedSegments);
    expect(history.present).not.toBe(savedSegments);
    expect(history.saved[0]).not.toBe(savedSegments[0]);
    expect(history.past).toEqual([]);
    expect(history.future).toEqual([]);
    expect(history.lastActionLabel).toBeNull();
  });

  test('applies a draft change with immutable snapshots and clears redo history', () => {
    const history = createNarrationDraftHistory(savedSegments);
    const undone = undoNarrationDraftChange(applyNarrationDraftChange(history, [
      { ...savedSegments[0], text: 'Temporary edit.', characterCount: 15 },
      savedSegments[1]
    ], 'Edited narration 1.'));
    const nextSegments = [
      { ...savedSegments[0], text: 'Final edit.', characterCount: 11 },
      savedSegments[1]
    ];

    const changed = applyNarrationDraftChange(undone, nextSegments, 'Edited narration 1 again.');

    expect(changed.present).toEqual(nextSegments);
    expect(changed.present).not.toBe(nextSegments);
    expect(changed.present[0]).not.toBe(nextSegments[0]);
    expect(changed.past).toHaveLength(1);
    expect(changed.past[0]).toEqual(savedSegments);
    expect(changed.future).toEqual([]);
    expect(changed.lastActionLabel).toBe('Edited narration 1 again.');
    expect(savedSegments[0].text).toBe('Explain the first scene.');
  });

  test('undo moves the previous snapshot into present and preserves redo state', () => {
    const history = createNarrationDraftHistory(savedSegments);
    const firstChange = [
      { ...savedSegments[0], startSeconds: 16, endSeconds: 29, durationSeconds: 13 },
      savedSegments[1]
    ];
    const changed = applyNarrationDraftChange(history, firstChange, 'Moved narration 1.');

    const undone = undoNarrationDraftChange(changed);

    expect(undone.present).toEqual(savedSegments);
    expect(undone.past).toEqual([]);
    expect(undone.future).toHaveLength(1);
    expect(undone.future[0]).toEqual(firstChange);
    expect(undone.lastActionLabel).toBe('Undo narration draft change.');
    expect(undoNarrationDraftChange(undone)).toEqual(undone);
  });

  test('redo restores a future snapshot and leaves state unchanged when redo is unavailable', () => {
    const history = createNarrationDraftHistory(savedSegments);
    const firstChange = [
      savedSegments[0],
      { ...savedSegments[1], voice: 'alloy' }
    ];
    const undone = undoNarrationDraftChange(applyNarrationDraftChange(history, firstChange, 'Changed narration 2 voice.'));

    const redone = redoNarrationDraftChange(undone);

    expect(redone.present).toEqual(firstChange);
    expect(redone.past).toHaveLength(1);
    expect(redone.past[0]).toEqual(savedSegments);
    expect(redone.future).toEqual([]);
    expect(redone.lastActionLabel).toBe('Redo narration draft change.');
    expect(redoNarrationDraftChange(redone)).toEqual(redone);
  });

  test('reverts to the saved baseline and clears history stacks', () => {
    const history = createNarrationDraftHistory(savedSegments);
    const changed = applyNarrationDraftChange(history, [
      savedSegments[0],
      savedSegments[1],
      {
        index: 2,
        startSeconds: 70.5,
        endSeconds: 75.5,
        durationSeconds: 5,
        text: '',
        voice: null,
        characterCount: 0,
        updatedAt: null
      }
    ], 'Inserted narration 3.');

    const reverted = resetNarrationDraftToSaved(changed);

    expect(reverted.present).toEqual(savedSegments);
    expect(reverted.saved).toEqual(savedSegments);
    expect(reverted.past).toEqual([]);
    expect(reverted.future).toEqual([]);
    expect(reverted.lastActionLabel).toBe('Reverted to saved narration.');
  });

  test('marks backend-returned segments as the new saved baseline', () => {
    const history = createNarrationDraftHistory(savedSegments);
    const changed = applyNarrationDraftChange(history, [
      { ...savedSegments[0], text: 'Edited and saved.', characterCount: 17 },
      savedSegments[1]
    ], 'Edited narration 1.');
    const backendSaved = [
      { ...savedSegments[0], text: 'Backend accepted edit.', characterCount: 22, updatedAt: '2026-06-30T00:05:00Z' },
      savedSegments[1]
    ];

    const marked = markNarrationDraftSaved(changed, backendSaved);

    expect(marked.saved).toEqual(backendSaved);
    expect(marked.present).toEqual(backendSaved);
    expect(marked.past).toEqual([]);
    expect(marked.future).toEqual([]);
    expect(marked.lastActionLabel).toBe('Narration saved.');
  });

  test('summarizes added, removed, timing, text, and voice draft changes', () => {
    const present: NarrationWorkspace['segments'] = [
      {
        ...savedSegments[0],
        startSeconds: 16,
        endSeconds: 29,
        durationSeconds: 13,
        text: 'Edited first scene.',
        voice: 'verse',
        characterCount: 19
      },
      {
        index: 2,
        startSeconds: 80,
        endSeconds: 85,
        durationSeconds: 5,
        text: 'New closing narration.',
        voice: 'alloy',
        characterCount: 22,
        updatedAt: null
      }
    ];

    const summary = summarizeNarrationDraftChanges(savedSegments, present);

    expect(summary).toEqual({
      dirty: true,
      addedCount: 1,
      removedCount: 1,
      timingChangedCount: 1,
      textChangedCount: 1,
      voiceChangedCount: 1,
      changedRowLabels: ['Narration 1', 'Narration 2 removed', 'Narration 3 added']
    });
  });

  test('reports a clean summary when saved and present rows match', () => {
    expect(summarizeNarrationDraftChanges(savedSegments, savedSegments)).toEqual({
      dirty: false,
      addedCount: 0,
      removedCount: 0,
      timingChangedCount: 0,
      textChangedCount: 0,
      voiceChangedCount: 0,
      changedRowLabels: []
    });
  });
});
