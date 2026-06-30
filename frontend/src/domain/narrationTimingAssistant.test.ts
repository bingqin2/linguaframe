import { describe, expect, test } from 'vitest';
import type { NarrationWorkspace } from './jobTypes';
import {
  buildNarrationTimingAssistantReport,
  closeNarrationDraftGaps,
  normalizeNarrationDraftOrder,
  resolveNarrationDraftOverlaps
} from './narrationTimingAssistant';

function segment(
  index: number,
  startSeconds: number,
  endSeconds: number,
  overrides: Partial<NarrationWorkspace['segments'][number]> = {}
): NarrationWorkspace['segments'][number] {
  return {
    index,
    startSeconds,
    endSeconds,
    durationSeconds: Number((endSeconds - startSeconds).toFixed(3)),
    text: `Narration ${index}`,
    voice: index % 2 === 0 ? 'verse' : 'alloy',
    characterCount: `Narration ${index}`.length,
    updatedAt: null,
    ...overrides
  };
}

describe('narrationTimingAssistant', () => {
  test('reports material gaps and overlaps without exposing narration text', () => {
    const report = buildNarrationTimingAssistantReport([
      segment(0, 0, 5),
      segment(1, 8, 10),
      segment(2, 9.5, 12)
    ], { minimumReportGapSeconds: 0.5, targetGapSeconds: 0.25 });

    expect(report.segmentCount).toBe(3);
    expect(report.gapCount).toBe(1);
    expect(report.totalGapSeconds).toBe(3);
    expect(report.longestGapSeconds).toBe(3);
    expect(report.overlapCount).toBe(1);
    expect(report.generationReady).toBe(false);
    expect(report.issues).toEqual([
      {
        type: 'gap',
        rowIndex: 1,
        startSeconds: 5,
        endSeconds: 8,
        durationSeconds: 3,
        message: 'Gap before row 2: 3 s.'
      },
      {
        type: 'overlap',
        rowIndex: 2,
        startSeconds: 9.5,
        endSeconds: 10,
        durationSeconds: 0.5,
        message: 'Row 3 overlaps the previous row by 0.5 s.'
      }
    ]);
    expect(JSON.stringify(report)).not.toContain('Narration');
  });

  test('closes gaps while preserving segment durations, text, and voice', () => {
    const input = [
      segment(0, 10, 14, { text: 'Keep this wording', voice: 'alloy' }),
      segment(1, 25, 31, { text: 'Keep second wording', voice: 'verse' })
    ];

    const packed = closeNarrationDraftGaps(input, { targetGapSeconds: 0.5 });

    expect(packed).toEqual([
      expect.objectContaining({
        index: 0,
        startSeconds: 10,
        endSeconds: 14,
        durationSeconds: 4,
        text: 'Keep this wording',
        voice: 'alloy'
      }),
      expect.objectContaining({
        index: 1,
        startSeconds: 14.5,
        endSeconds: 20.5,
        durationSeconds: 6,
        text: 'Keep second wording',
        voice: 'verse'
      })
    ]);
    expect(input[1].startSeconds).toBe(25);
  });

  test('resolves overlaps by shifting later rows and preserving durations', () => {
    const input = [
      segment(0, 0, 5),
      segment(1, 4, 8),
      segment(2, 7.5, 10)
    ];

    const resolved = resolveNarrationDraftOverlaps(input, { targetGapSeconds: 0.25 });

    expect(resolved.map((candidate) => ({
      index: candidate.index,
      startSeconds: candidate.startSeconds,
      endSeconds: candidate.endSeconds,
      durationSeconds: candidate.durationSeconds
    }))).toEqual([
      { index: 0, startSeconds: 0, endSeconds: 5, durationSeconds: 5 },
      { index: 1, startSeconds: 5.25, endSeconds: 9.25, durationSeconds: 4 },
      { index: 2, startSeconds: 9.5, endSeconds: 12, durationSeconds: 2.5 }
    ]);
    expect(input[1].startSeconds).toBe(4);
  });

  test('normalizes draft order by start time and reindexes rows', () => {
    const normalized = normalizeNarrationDraftOrder([
      segment(5, 20, 22),
      segment(2, 3, 8),
      segment(4, 12, 14)
    ]);

    expect(normalized.map((candidate) => ({
      index: candidate.index,
      startSeconds: candidate.startSeconds,
      endSeconds: candidate.endSeconds
    }))).toEqual([
      { index: 0, startSeconds: 3, endSeconds: 8 },
      { index: 1, startSeconds: 12, endSeconds: 14 },
      { index: 2, startSeconds: 20, endSeconds: 22 }
    ]);
  });
});
