import { describe, expect, test } from 'vitest';

import type { NarrationWorkspace } from './jobTypes';
import { parseNarrationQuickScript } from './narrationQuickScriptImport';

const voiceCatalog: NarrationWorkspace['voiceCatalog'] = {
  provider: 'demo',
  defaultVoice: 'verse',
  presets: [
    {
      voice: 'alloy',
      label: 'Alloy',
      provider: 'demo',
      defaultPreset: false,
      description: 'Clear voice'
    },
    {
      voice: 'verse',
      label: 'Verse',
      provider: 'demo',
      defaultPreset: true,
      description: 'Default voice'
    }
  ],
  safetyNotes: []
};

const existingSegments: NarrationWorkspace['segments'] = [
  {
    index: 0,
    startSeconds: 2,
    endSeconds: 8,
    durationSeconds: 6,
    text: 'Existing narration.',
    voice: 'verse',
    characterCount: 19,
    updatedAt: '2026-06-30T00:00:00Z'
  }
];

describe('narrationQuickScriptImport', () => {
  test('parses explicit voice and blank inherited voice rows', () => {
    const result = parseNarrationQuickScript(
      [
        '00:15-00:28 | alloy | Explain the first scene.',
        '55-70.5 || Inherit default voice.'
      ].join('\n'),
      { existingSegments: [], voiceCatalog, mode: 'replace' }
    );

    expect(result.valid).toBe(true);
    expect(result.issues).toEqual([]);
    expect(result.totalDurationSeconds).toBe(28.5);
    expect(result.segments).toEqual([
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
        text: 'Inherit default voice.',
        voice: null,
        characterCount: 22,
        updatedAt: null
      }
    ]);
  });

  test('parses timestamp variants whitespace and unicode text', () => {
    const result = parseNarrationQuickScript(
      [
        '1:02.5 - 1:05 | verse | 解释这段对白。',
        '01:02:03-01:02:05.25 | alloy | Long-form timestamp.'
      ].join('\n'),
      { existingSegments: [], voiceCatalog, mode: 'replace' }
    );

    expect(result.valid).toBe(true);
    expect(result.segments[0]).toMatchObject({
      startSeconds: 62.5,
      endSeconds: 65,
      durationSeconds: 2.5,
      text: '解释这段对白。',
      characterCount: 7
    });
    expect(result.segments[1]).toMatchObject({
      startSeconds: 3723,
      endSeconds: 3725.25,
      durationSeconds: 2.25
    });
  });

  test('reports row-level format text timing and voice errors', () => {
    const longText = 'x'.repeat(1001);
    const result = parseNarrationQuickScript(
      [
        'not a row',
        '00:04-00:02 | alloy | Backwards.',
        '00:10-00:12 | unknown | Bad voice.',
        `00:13-00:14 | ${'v'.repeat(65)} | Too long voice.`,
        `00:15-00:16 | alloy | ${longText}`,
        '00:17-00:18 | alloy |   '
      ].join('\n'),
      { existingSegments: [], voiceCatalog, mode: 'replace' }
    );

    expect(result.valid).toBe(false);
    expect(result.segments).toEqual([]);
    expect(result.issues.map((issue) => issue.message)).toEqual([
      'Line 1: expected START-END | VOICE | TEXT.',
      'Line 2: end must be after start.',
      'Line 3: voice must be one of the configured presets.',
      'Line 4: voice must be 64 characters or fewer.',
      'Line 5: text must be 1000 characters or fewer.',
      'Line 6: text is required.'
    ]);
  });

  test('rejects overlapping imported rows', () => {
    const result = parseNarrationQuickScript(
      [
        '00:10-00:20 | alloy | First.',
        '00:19-00:25 | verse | Overlap.'
      ].join('\n'),
      { existingSegments: [], voiceCatalog, mode: 'replace' }
    );

    expect(result.valid).toBe(false);
    expect(result.segments).toEqual([]);
    expect(result.issues).toEqual([
      {
        lineNumber: 2,
        message: 'Line 2: start overlaps the previous imported row.'
      }
    ]);
  });

  test('append mode preserves existing rows and reindexes imported rows without mutating callers', () => {
    const result = parseNarrationQuickScript(
      '00:15-00:20 | alloy | Appended explanation.',
      { existingSegments, voiceCatalog, mode: 'append' }
    );

    expect(result.valid).toBe(true);
    expect(result.segments).toHaveLength(2);
    expect(result.segments[0]).toEqual(existingSegments[0]);
    expect(result.segments[1]).toMatchObject({
      index: 1,
      startSeconds: 15,
      endSeconds: 20,
      text: 'Appended explanation.',
      voice: 'alloy',
      updatedAt: null
    });
    expect(existingSegments).toHaveLength(1);
    expect(existingSegments[0].index).toBe(0);
  });
});
