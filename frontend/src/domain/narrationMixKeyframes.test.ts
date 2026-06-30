import { describe, expect, test } from 'vitest';

import type { NarrationMixKeyframe } from './jobTypes';
import {
  clearNarrationMixLane,
  deleteNarrationMixKeyframe,
  toSaveNarrationMixKeyframes,
  upsertNarrationMixKeyframe,
  validateNarrationMixKeyframes
} from './narrationMixKeyframes';

const keyframes: NarrationMixKeyframe[] = [
  { lane: 'DUCKING_VOLUME', timeSeconds: 0, value: 0.6, updatedAt: null },
  { lane: 'DUCKING_VOLUME', timeSeconds: 20, value: 0.25, updatedAt: null },
  { lane: 'NARRATION_VOLUME', timeSeconds: 20, value: 1.4, updatedAt: null },
  { lane: 'FADE_DURATION_MS', timeSeconds: 20, value: 500, updatedAt: null }
];

describe('narrationMixKeyframes', () => {
  test('upserts one lane/time position and keeps deterministic save order', () => {
    const updated = upsertNarrationMixKeyframe(keyframes, {
      lane: 'DUCKING_VOLUME',
      timeSeconds: 20.0004,
      value: 0.3333
    });

    expect(toSaveNarrationMixKeyframes(updated)).toEqual([
      { lane: 'DUCKING_VOLUME', timeSeconds: 0, value: 0.6 },
      { lane: 'DUCKING_VOLUME', timeSeconds: 20, value: 0.333 },
      { lane: 'NARRATION_VOLUME', timeSeconds: 20, value: 1.4 },
      { lane: 'FADE_DURATION_MS', timeSeconds: 20, value: 500 }
    ]);
  });

  test('deletes individual keyframes and clears a full lane', () => {
    expect(deleteNarrationMixKeyframe(keyframes, 'DUCKING_VOLUME', 20.0001))
      .toHaveLength(3);
    expect(clearNarrationMixLane(keyframes, 'DUCKING_VOLUME'))
      .toEqual([
        { lane: 'NARRATION_VOLUME', timeSeconds: 20, value: 1.4, updatedAt: null },
        { lane: 'FADE_DURATION_MS', timeSeconds: 20, value: 500, updatedAt: null }
      ]);
  });

  test('validates value ranges, whole fade duration, non-negative time, and duplicate positions', () => {
    const messages = validateNarrationMixKeyframes([
      { lane: 'DUCKING_VOLUME', timeSeconds: -1, value: 1.5, updatedAt: null },
      { lane: 'NARRATION_VOLUME', timeSeconds: 1, value: 2.5, updatedAt: null },
      { lane: 'FADE_DURATION_MS', timeSeconds: 2, value: 500.5, updatedAt: null },
      { lane: 'DUCKING_VOLUME', timeSeconds: -1, value: 0.5, updatedAt: null }
    ]);

    expect(messages).toContain('Keyframe 1: time must be greater than or equal to 0.');
    expect(messages).toContain('Keyframe 1: ducking volume must be between 0.00 and 1.00.');
    expect(messages).toContain('Keyframe 2: narration volume must be between 0.00 and 2.00.');
    expect(messages).toContain('Keyframe 3: fade duration must be a whole number between 0 and 5000 ms.');
    expect(messages).toContain('Keyframe 4: duplicate lane and time.');
  });
});
