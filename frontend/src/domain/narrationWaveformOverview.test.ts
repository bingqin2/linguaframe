import { describe, expect, test } from 'vitest';

import {
  buildNarrationWaveformOverview,
  secondsFromWaveformPercent
} from './narrationWaveformOverview';
import type { NarrationWorkspace } from './jobTypes';

const segments: NarrationWorkspace['segments'] = [
  {
    index: 0,
    startSeconds: 10,
    endSeconds: 20,
    durationSeconds: 10,
    text: 'Short intro.',
    voice: 'alloy',
    characterCount: 12,
    updatedAt: null
  },
  {
    index: 1,
    startSeconds: 40,
    endSeconds: 55,
    durationSeconds: 15,
    text: 'A much denser explanatory narration segment with more words.',
    voice: 'verse',
    characterCount: 59,
    updatedAt: null
  }
];

describe('buildNarrationWaveformOverview', () => {
  test('returns silent gap buckets for an empty segment list', () => {
    const overview = buildNarrationWaveformOverview({
      segments: [],
      selectedIndex: 0,
      currentSeconds: 0,
      bucketCount: 8
    });

    expect(overview).toMatchObject({
      startSeconds: 0,
      endSeconds: 0,
      durationSeconds: 0,
      bucketCount: 8,
      selectedStartPercent: null,
      selectedEndPercent: null,
      playheadPercent: null,
      gapBucketCount: 8,
      activeBucketCount: 0
    });
    expect(overview.buckets).toHaveLength(8);
    expect(overview.buckets.every((bucket) => bucket.gap && !bucket.active && bucket.heightPercent === 8)).toBe(true);
  });

  test('marks covered buckets active and silence buckets as gaps', () => {
    const overview = buildNarrationWaveformOverview({
      segments,
      selectedIndex: 0,
      currentSeconds: 10,
      bucketCount: 9
    });

    expect(overview.startSeconds).toBe(10);
    expect(overview.endSeconds).toBe(55);
    expect(overview.durationSeconds).toBe(45);
    expect(overview.buckets.some((bucket) => bucket.active)).toBe(true);
    expect(overview.buckets.some((bucket) => bucket.gap)).toBe(true);
    expect(overview.activeBucketCount).toBeGreaterThan(0);
    expect(overview.gapBucketCount).toBeGreaterThan(0);
  });

  test('clamps selected window and playhead percentages', () => {
    const beforeTimeline = buildNarrationWaveformOverview({
      segments,
      selectedIndex: 0,
      currentSeconds: -10,
      bucketCount: 9
    });
    const afterTimeline = buildNarrationWaveformOverview({
      segments,
      selectedIndex: 1,
      currentSeconds: 100,
      bucketCount: 9
    });

    expect(beforeTimeline.selectedStartPercent).toBe(0);
    expect(beforeTimeline.selectedEndPercent).toBeCloseTo(22.2222, 4);
    expect(beforeTimeline.playheadPercent).toBe(0);
    expect(afterTimeline.selectedStartPercent).toBeCloseTo(66.6667, 4);
    expect(afterTimeline.selectedEndPercent).toBe(100);
    expect(afterTimeline.playheadPercent).toBe(100);
  });

  test('derives deterministic bounded bucket heights from coverage and text density', () => {
    const overview = buildNarrationWaveformOverview({
      segments,
      selectedIndex: 1,
      currentSeconds: 45,
      bucketCount: 9
    });

    const selectedBuckets = overview.buckets.filter((bucket) => bucket.selected);
    const activeHeights = overview.buckets.filter((bucket) => bucket.active).map((bucket) => bucket.heightPercent);

    expect(selectedBuckets.length).toBeGreaterThan(0);
    expect(activeHeights.every((height) => height >= 8 && height <= 100)).toBe(true);
    expect(Math.max(...activeHeights)).toBeGreaterThan(Math.min(...activeHeights));
    expect(overview.buckets.map((bucket) => bucket.heightPercent)).toMatchInlineSnapshot(`
      [
        75,
        75,
        8,
        8,
        8,
        8,
        100,
        100,
        100,
      ]
    `);
  });
});

describe('secondsFromWaveformPercent', () => {
  test('maps waveform percent to seconds across the timeline span', () => {
    expect(secondsFromWaveformPercent({ percent: 0, startSeconds: 10, endSeconds: 55 })).toBe(10);
    expect(secondsFromWaveformPercent({ percent: 50, startSeconds: 10, endSeconds: 55 })).toBe(32.5);
    expect(secondsFromWaveformPercent({ percent: 100, startSeconds: 10, endSeconds: 55 })).toBe(55);
    expect(secondsFromWaveformPercent({ percent: 150, startSeconds: 10, endSeconds: 55 })).toBe(55);
  });
});
