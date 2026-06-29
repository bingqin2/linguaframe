import { describe, expect, test } from 'vitest';

import type { NarrationWorkspace } from './jobTypes';
import {
  buildLocalNarrationTimeline,
  editNarrationTimelineSegment,
  normalizeNarrationSegmentTiming
} from './narrationTimelineEditing';

const baseSegment: NarrationWorkspace['segments'][number] = {
  index: 0,
  startSeconds: 10,
  endSeconds: 15,
  durationSeconds: 5,
  text: 'First explanation.',
  voice: 'alloy',
  characterCount: 18,
  updatedAt: '2026-06-30T00:00:00Z'
};

describe('narrationTimelineEditing', () => {
  test('moves a segment by pointer delta while preserving non-timing fields', () => {
    const edited = editNarrationTimelineSegment({
      segment: baseSegment,
      mode: 'move',
      pointerDeltaPx: 100,
      trackWidthPx: 400,
      timelineStartSeconds: 0,
      timelineEndSeconds: 20
    });

    expect(edited).toMatchObject({
      index: 0,
      startSeconds: 15,
      endSeconds: 20,
      durationSeconds: 5,
      text: 'First explanation.',
      voice: 'alloy',
      characterCount: 18,
      updatedAt: '2026-06-30T00:00:00Z'
    });
  });

  test('resizes the start and snaps to quarter seconds', () => {
    const edited = editNarrationTimelineSegment({
      segment: baseSegment,
      mode: 'resize-start',
      pointerDeltaPx: -18,
      trackWidthPx: 400,
      timelineStartSeconds: 0,
      timelineEndSeconds: 20
    });

    expect(edited.startSeconds).toBe(9);
    expect(edited.endSeconds).toBe(15);
    expect(edited.durationSeconds).toBe(6);
  });

  test('resizes the end and enforces minimum duration', () => {
    const edited = editNarrationTimelineSegment({
      segment: baseSegment,
      mode: 'resize-end',
      pointerDeltaPx: -500,
      trackWidthPx: 400,
      timelineStartSeconds: 0,
      timelineEndSeconds: 20
    });

    expect(edited.startSeconds).toBe(10);
    expect(edited.endSeconds).toBe(10.25);
    expect(edited.durationSeconds).toBe(0.25);
  });

  test('clamps moves at the timeline start without changing duration', () => {
    const edited = editNarrationTimelineSegment({
      segment: baseSegment,
      mode: 'move',
      pointerDeltaPx: -500,
      trackWidthPx: 400,
      timelineStartSeconds: 0,
      timelineEndSeconds: 20
    });

    expect(edited.startSeconds).toBe(0);
    expect(edited.endSeconds).toBe(5);
    expect(edited.durationSeconds).toBe(5);
  });

  test('normalizes timing to quarter seconds', () => {
    const edited = normalizeNarrationSegmentTiming({
      ...baseSegment,
      startSeconds: 10.13,
      endSeconds: 14.88,
      durationSeconds: 1
    });

    expect(edited.startSeconds).toBe(10.25);
    expect(edited.endSeconds).toBe(15);
    expect(edited.durationSeconds).toBe(4.75);
  });

  test('builds a local timeline with percentages, gaps, and overlap state', () => {
    const timeline = buildLocalNarrationTimeline([
      baseSegment,
      {
        ...baseSegment,
        index: 1,
        startSeconds: 18,
        endSeconds: 20,
        durationSeconds: 2,
        text: 'Second explanation.'
      }
    ]);

    expect(timeline.startSeconds).toBe(10);
    expect(timeline.endSeconds).toBe(20);
    expect(timeline.totalSpanSeconds).toBe(10);
    expect(timeline.coveredSeconds).toBe(7);
    expect(timeline.gapSeconds).toBe(3);
    expect(timeline.gapCount).toBe(1);
    expect(timeline.hasOverlap).toBe(false);
    expect(timeline.generationReady).toBe(true);
    expect(timeline.segments[0]).toMatchObject({
      index: 0,
      leftPercent: 0,
      widthPercent: 50
    });
    expect(timeline.segments[1]).toMatchObject({
      index: 1,
      leftPercent: 80,
      widthPercent: 20
    });
  });

  test('marks overlap and invalid text as not generation ready', () => {
    const timeline = buildLocalNarrationTimeline([
      baseSegment,
      {
        ...baseSegment,
        index: 1,
        startSeconds: 14,
        endSeconds: 16,
        durationSeconds: 2,
        text: ''
      }
    ]);

    expect(timeline.hasOverlap).toBe(true);
    expect(timeline.generationReady).toBe(false);
  });
});
