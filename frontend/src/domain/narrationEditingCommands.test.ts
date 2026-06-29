import { describe, expect, test } from 'vitest';

import type { NarrationWorkspace } from './jobTypes';
import {
  duplicateNarrationSegment,
  insertNarrationSegmentAfter,
  mergeNarrationSegmentWithNext,
  reindexNarrationSegments,
  splitNarrationSegmentAtTime
} from './narrationEditingCommands';

const segments: NarrationWorkspace['segments'] = [
  {
    index: 0,
    startSeconds: 15,
    endSeconds: 28,
    durationSeconds: 13,
    text: 'Explain the robot entering the room.',
    voice: 'alloy',
    characterCount: 36,
    updatedAt: '2026-06-30T00:00:00Z'
  },
  {
    index: 1,
    startSeconds: 55,
    endSeconds: 70.5,
    durationSeconds: 15.5,
    text: 'Describe the second conversation beat.',
    voice: 'verse',
    characterCount: 38,
    updatedAt: '2026-06-30T00:01:00Z'
  }
];

describe('narrationEditingCommands', () => {
  test('duplicates the selected segment immediately after its window and selects the copy', () => {
    const result = duplicateNarrationSegment(segments, 0);

    expect(result.blockedReason).toBeNull();
    expect(result.selectedIndex).toBe(1);
    expect(result.segments).toHaveLength(3);
    expect(result.segments[1]).toMatchObject({
      index: 1,
      startSeconds: 28,
      endSeconds: 41,
      durationSeconds: 13,
      text: 'Explain the robot entering the room.',
      voice: 'alloy',
      characterCount: 36,
      updatedAt: null
    });
    expect(result.segments[2].index).toBe(2);
    expect(segments).toHaveLength(2);
  });

  test('splits a selected segment at the playhead and selects the second half', () => {
    const result = splitNarrationSegmentAtTime(segments, 0, 20.3334);

    expect(result.blockedReason).toBeNull();
    expect(result.selectedIndex).toBe(1);
    expect(result.segments).toHaveLength(3);
    expect(result.segments[0]).toMatchObject({
      index: 0,
      startSeconds: 15,
      endSeconds: 20.333,
      durationSeconds: 5.333,
      text: 'Explain the robot',
      voice: 'alloy',
      characterCount: 17
    });
    expect(result.segments[1]).toMatchObject({
      index: 1,
      startSeconds: 20.333,
      endSeconds: 28,
      durationSeconds: 7.667,
      text: 'entering the room.',
      voice: 'alloy',
      characterCount: 18,
      updatedAt: null
    });
  });

  test('blocks split points that would leave less than a quarter second on either side', () => {
    const result = splitNarrationSegmentAtTime(segments, 0, 15.1);

    expect(result.blockedReason).toBe('Split time must leave at least 0.25 seconds on both sides.');
    expect(result.selectedIndex).toBe(0);
    expect(result.segments).toEqual(segments);
  });

  test('merges the selected segment with the next segment and keeps selection on the merged row', () => {
    const result = mergeNarrationSegmentWithNext(segments, 0);

    expect(result.blockedReason).toBeNull();
    expect(result.selectedIndex).toBe(0);
    expect(result.segments).toHaveLength(1);
    expect(result.segments[0]).toMatchObject({
      index: 0,
      startSeconds: 15,
      endSeconds: 70.5,
      durationSeconds: 55.5,
      text: 'Explain the robot entering the room.\n\nDescribe the second conversation beat.',
      voice: 'alloy',
      characterCount: 76,
      updatedAt: null
    });
  });

  test('inserts a blank local row after the selected segment', () => {
    const result = insertNarrationSegmentAfter(segments, 0);

    expect(result.blockedReason).toBeNull();
    expect(result.selectedIndex).toBe(1);
    expect(result.segments).toHaveLength(3);
    expect(result.segments[1]).toMatchObject({
      index: 1,
      startSeconds: 28,
      endSeconds: 33,
      durationSeconds: 5,
      text: '',
      voice: null,
      characterCount: 0,
      updatedAt: null
    });
    expect(result.segments[2].index).toBe(2);
  });

  test('reindexes copied segments without mutating caller-owned objects', () => {
    const original = { ...segments[1], index: 9 };
    const reindexed = reindexNarrationSegments([original]);

    expect(reindexed[0]).toMatchObject({ index: 0, startSeconds: 55, endSeconds: 70.5 });
    expect(original.index).toBe(9);
  });

  test('returns blocked reasons for impossible commands without throwing', () => {
    expect(duplicateNarrationSegment(segments, -1)).toMatchObject({
      blockedReason: 'No selected narration row.',
      selectedIndex: -1,
      segments
    });
    expect(mergeNarrationSegmentWithNext(segments, 1)).toMatchObject({
      blockedReason: 'No following narration row to merge.',
      selectedIndex: 1,
      segments
    });
  });
});
