import type { NarrationWorkspace } from './jobTypes';

export interface NarrationMixAutomationPoint {
  index: number;
  startSeconds: number;
  endSeconds: number;
  duckingVolume: number;
  narrationVolume: number;
  fadeDurationMs: number;
  hasOverride: boolean;
  selected: boolean;
}

export interface NarrationMixAutomation {
  points: NarrationMixAutomationPoint[];
  overrideCount: number;
  inheritedCount: number;
  minDuckingVolume: number;
  maxNarrationVolume: number;
  maxFadeDurationMs: number;
}

export function buildNarrationMixAutomation(input: {
  segments: NarrationWorkspace['segments'];
  mixSettings: NarrationWorkspace['mixSettings'] | null;
  selectedIndex: number;
}): NarrationMixAutomation {
  const defaults = {
    duckingVolume: input.mixSettings?.duckingVolume ?? 0.35,
    narrationVolume: input.mixSettings?.narrationVolume ?? 1,
    fadeDurationMs: input.mixSettings?.fadeDurationMs ?? 250
  };
  const points = input.segments.map((segment) => {
    const hasOverride = segment.duckingVolume != null
      || segment.narrationVolume != null
      || segment.fadeDurationMs != null;
    return {
      index: segment.index,
      startSeconds: segment.startSeconds,
      endSeconds: segment.endSeconds,
      duckingVolume: roundMixValue(segment.duckingVolume ?? defaults.duckingVolume),
      narrationVolume: roundMixValue(segment.narrationVolume ?? defaults.narrationVolume),
      fadeDurationMs: Math.round(segment.fadeDurationMs ?? defaults.fadeDurationMs),
      hasOverride,
      selected: segment.index === input.selectedIndex
    };
  });

  return {
    points,
    overrideCount: points.filter((point) => point.hasOverride).length,
    inheritedCount: points.filter((point) => !point.hasOverride).length,
    minDuckingVolume: points.length === 0 ? 0 : Math.min(...points.map((point) => point.duckingVolume)),
    maxNarrationVolume: points.length === 0 ? 0 : Math.max(...points.map((point) => point.narrationVolume)),
    maxFadeDurationMs: points.length === 0 ? 0 : Math.max(...points.map((point) => point.fadeDurationMs))
  };
}

function roundMixValue(value: number): number {
  return Number(value.toFixed(3));
}
