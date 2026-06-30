import { describe, expect, test } from 'vitest';

import { buildNarrationMixAutomation } from './narrationMixAutomation';
import type { NarrationWorkspace } from './jobTypes';

const mixSettings: NarrationWorkspace['mixSettings'] = {
  duckingVolume: 0.35,
  narrationVolume: 1,
  fadeDurationMs: 250,
  updatedAt: null
};

const segments: NarrationWorkspace['segments'] = [
  {
    index: 0,
    startSeconds: 15,
    endSeconds: 28,
    durationSeconds: 13,
    text: 'Explain the first scene.',
    voice: 'alloy',
    characterCount: 24,
    updatedAt: null
  },
  {
    index: 1,
    startSeconds: 55,
    endSeconds: 70.5,
    durationSeconds: 15.5,
    text: 'Explain the second scene.',
    voice: 'verse',
    characterCount: 25,
    duckingVolume: 0.2,
    narrationVolume: 1.45,
    fadeDurationMs: 125,
    updatedAt: null
  }
];

describe('buildNarrationMixAutomation', () => {
  test('derives inherited effective mix values from job-level settings', () => {
    const automation = buildNarrationMixAutomation({
      segments: [segments[0]],
      mixSettings,
      selectedIndex: 0
    });

    expect(automation).toMatchObject({
      overrideCount: 0,
      inheritedCount: 1,
      minDuckingVolume: 0.35,
      maxNarrationVolume: 1,
      maxFadeDurationMs: 250
    });
    expect(automation.points).toEqual([
      {
        index: 0,
        startSeconds: 15,
        endSeconds: 28,
        duckingVolume: 0.35,
        narrationVolume: 1,
        fadeDurationMs: 250,
        hasOverride: false,
        selected: true
      }
    ]);
  });

  test('uses explicit segment overrides only for the overridden window', () => {
    const automation = buildNarrationMixAutomation({
      segments,
      mixSettings,
      selectedIndex: 1
    });

    expect(automation.points[0]).toMatchObject({
      duckingVolume: 0.35,
      narrationVolume: 1,
      fadeDurationMs: 250,
      hasOverride: false,
      selected: false
    });
    expect(automation.points[1]).toMatchObject({
      duckingVolume: 0.2,
      narrationVolume: 1.45,
      fadeDurationMs: 125,
      hasOverride: true,
      selected: true
    });
  });

  test('returns deterministic summary metrics for empty and mixed timelines', () => {
    expect(buildNarrationMixAutomation({ segments: [], mixSettings, selectedIndex: 0 })).toMatchObject({
      points: [],
      overrideCount: 0,
      inheritedCount: 0,
      minDuckingVolume: 0,
      maxNarrationVolume: 0,
      maxFadeDurationMs: 0
    });

    expect(buildNarrationMixAutomation({ segments, mixSettings, selectedIndex: 0 })).toMatchObject({
      overrideCount: 1,
      inheritedCount: 1,
      minDuckingVolume: 0.2,
      maxNarrationVolume: 1.45,
      maxFadeDurationMs: 250
    });
  });
});
