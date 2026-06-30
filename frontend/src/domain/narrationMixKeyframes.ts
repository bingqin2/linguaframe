import type { NarrationMixKeyframe, NarrationMixLane, SaveNarrationMixKeyframe } from './jobTypes';

export const NARRATION_MIX_LANES: NarrationMixLane[] = [
  'DUCKING_VOLUME',
  'NARRATION_VOLUME',
  'FADE_DURATION_MS'
];

export function upsertNarrationMixKeyframe(
  keyframes: NarrationMixKeyframe[],
  keyframe: SaveNarrationMixKeyframe
): NarrationMixKeyframe[] {
  const normalized = normalizeNarrationMixKeyframe(keyframe);
  return sortNarrationMixKeyframes([
    ...keyframes.filter((current) => !sameKeyframePosition(current, normalized)),
    { ...normalized, updatedAt: null }
  ]);
}

export function deleteNarrationMixKeyframe(
  keyframes: NarrationMixKeyframe[],
  lane: NarrationMixLane,
  timeSeconds: number
): NarrationMixKeyframe[] {
  const normalizedTime = roundSeconds(timeSeconds);
  return keyframes.filter((current) => !(current.lane === lane && roundSeconds(current.timeSeconds) === normalizedTime));
}

export function clearNarrationMixLane(
  keyframes: NarrationMixKeyframe[],
  lane: NarrationMixLane
): NarrationMixKeyframe[] {
  return keyframes.filter((keyframe) => keyframe.lane !== lane);
}

export function toSaveNarrationMixKeyframes(keyframes: NarrationMixKeyframe[]): SaveNarrationMixKeyframe[] {
  return sortNarrationMixKeyframes(keyframes).map((keyframe) => ({
    lane: keyframe.lane,
    timeSeconds: roundSeconds(keyframe.timeSeconds),
    value: normalizeValue(keyframe.lane, keyframe.value)
  }));
}

export function validateNarrationMixKeyframes(keyframes: NarrationMixKeyframe[]): string[] {
  const messages: string[] = [];
  const seen = new Set<string>();
  if (keyframes.length > 60) {
    messages.push('Mix automation supports at most 60 keyframes.');
  }
  keyframes.forEach((keyframe, index) => {
    if (!NARRATION_MIX_LANES.includes(keyframe.lane)) {
      messages.push(`Keyframe ${index + 1}: lane is required.`);
    }
    if (!Number.isFinite(keyframe.timeSeconds) || keyframe.timeSeconds < 0) {
      messages.push(`Keyframe ${index + 1}: time must be greater than or equal to 0.`);
    }
    if (!Number.isFinite(keyframe.value)) {
      messages.push(`Keyframe ${index + 1}: value is required.`);
    } else if (keyframe.lane === 'DUCKING_VOLUME' && (keyframe.value < 0 || keyframe.value > 1)) {
      messages.push(`Keyframe ${index + 1}: ducking volume must be between 0.00 and 1.00.`);
    } else if (keyframe.lane === 'NARRATION_VOLUME' && (keyframe.value < 0 || keyframe.value > 2)) {
      messages.push(`Keyframe ${index + 1}: narration volume must be between 0.00 and 2.00.`);
    } else if (keyframe.lane === 'FADE_DURATION_MS' && (keyframe.value < 0 || keyframe.value > 5000 || !Number.isInteger(keyframe.value))) {
      messages.push(`Keyframe ${index + 1}: fade duration must be a whole number between 0 and 5000 ms.`);
    }
    const key = `${keyframe.lane}:${roundSeconds(keyframe.timeSeconds).toFixed(3)}`;
    if (seen.has(key)) {
      messages.push(`Keyframe ${index + 1}: duplicate lane and time.`);
    }
    seen.add(key);
  });
  return messages;
}

export function laneLabel(lane: NarrationMixLane): string {
  switch (lane) {
    case 'DUCKING_VOLUME':
      return 'Ducking';
    case 'NARRATION_VOLUME':
      return 'Narration';
    case 'FADE_DURATION_MS':
      return 'Fade';
  }
}

function sortNarrationMixKeyframes(keyframes: NarrationMixKeyframe[]): NarrationMixKeyframe[] {
  const laneOrder = new Map<NarrationMixLane, number>(NARRATION_MIX_LANES.map((lane, index) => [lane, index]));
  return [...keyframes].sort((left, right) => (
    (laneOrder.get(left.lane) ?? 99) - (laneOrder.get(right.lane) ?? 99)
    || roundSeconds(left.timeSeconds) - roundSeconds(right.timeSeconds)
  ));
}

function normalizeNarrationMixKeyframe(keyframe: SaveNarrationMixKeyframe): SaveNarrationMixKeyframe {
  return {
    lane: keyframe.lane,
    timeSeconds: roundSeconds(keyframe.timeSeconds),
    value: normalizeValue(keyframe.lane, keyframe.value)
  };
}

function normalizeValue(lane: NarrationMixLane, value: number): number {
  if (lane === 'FADE_DURATION_MS') {
    return Math.round(value);
  }
  return Number(value.toFixed(3));
}

function sameKeyframePosition(left: NarrationMixKeyframe, right: SaveNarrationMixKeyframe): boolean {
  return left.lane === right.lane && roundSeconds(left.timeSeconds) === right.timeSeconds;
}

function roundSeconds(value: number): number {
  return Number(value.toFixed(3));
}
