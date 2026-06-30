import type { NarrationWorkspace } from './jobTypes';

export type NarrationQuickScriptImportMode = 'replace' | 'append';

export interface NarrationQuickScriptImportIssue {
  lineNumber: number;
  message: string;
}

export interface NarrationQuickScriptImportRow {
  lineNumber: number;
  startSeconds: number;
  endSeconds: number;
  durationSeconds: number;
  text: string;
  voice: string | null;
  characterCount: number;
}

export interface NarrationQuickScriptImportResult {
  valid: boolean;
  rows: NarrationQuickScriptImportRow[];
  segments: NarrationWorkspace['segments'];
  issues: NarrationQuickScriptImportIssue[];
  importedCount: number;
  totalDurationSeconds: number;
}

export function parseNarrationQuickScript(
  input: string,
  options: {
    existingSegments: NarrationWorkspace['segments'];
    voiceCatalog: NarrationWorkspace['voiceCatalog'] | null;
    mode: NarrationQuickScriptImportMode;
  }
): NarrationQuickScriptImportResult {
  const rows: NarrationQuickScriptImportRow[] = [];
  const issues: NarrationQuickScriptImportIssue[] = [];
  const lines = input.split(/\r?\n/);

  lines.forEach((rawLine, lineIndex) => {
    const lineNumber = lineIndex + 1;
    const line = rawLine.trim();
    if (!line) {
      return;
    }

    const parts = line.split('|');
    if (parts.length !== 3) {
      issues.push(issue(lineNumber, 'expected START-END | VOICE | TEXT.'));
      return;
    }

    const timing = parseTimingRange(parts[0].trim());
    if (!timing) {
      issues.push(issue(lineNumber, 'expected START-END | VOICE | TEXT.'));
      return;
    }

    const voice = normalizeOptional(parts[1]);
    const text = parts[2].trim();
    if (timing.endSeconds <= timing.startSeconds) {
      issues.push(issue(lineNumber, 'end must be after start.'));
    }
    if (!text) {
      issues.push(issue(lineNumber, 'text is required.'));
    }
    if (text.length > 1000) {
      issues.push(issue(lineNumber, 'text must be 1000 characters or fewer.'));
    }
    const voiceTooLong = (voice ?? '').length > 64;
    if (voiceTooLong) {
      issues.push(issue(lineNumber, 'voice must be 64 characters or fewer.'));
    }
    if (
      voice
      && !voiceTooLong
      && options.voiceCatalog
      && !options.voiceCatalog.presets.some((preset) => preset.voice === voice)
    ) {
      issues.push(issue(lineNumber, 'voice must be one of the configured presets.'));
    }

    rows.push({
      lineNumber,
      startSeconds: timing.startSeconds,
      endSeconds: timing.endSeconds,
      durationSeconds: roundSeconds(timing.endSeconds - timing.startSeconds),
      text,
      voice,
      characterCount: text.length
    });
  });

  rows
    .slice()
    .sort((left, right) => left.startSeconds - right.startSeconds || left.endSeconds - right.endSeconds)
    .forEach((row, index, sortedRows) => {
      if (index > 0 && row.startSeconds < sortedRows[index - 1].endSeconds) {
        issues.push(issue(row.lineNumber, 'start overlaps the previous imported row.'));
      }
    });

  if (issues.length > 0 || rows.length === 0) {
    return {
      valid: false,
      rows,
      segments: [],
      issues: issues.sort((left, right) => left.lineNumber - right.lineNumber),
      importedCount: 0,
      totalDurationSeconds: 0
    };
  }

  const importedSegments = rows.map((row, index) => ({
    index: options.mode === 'append' ? options.existingSegments.length + index : index,
    startSeconds: row.startSeconds,
    endSeconds: row.endSeconds,
    durationSeconds: row.durationSeconds,
    text: row.text,
    voice: row.voice,
    characterCount: row.characterCount,
    updatedAt: null
  }));
  const segments = options.mode === 'append'
    ? [
        ...cloneAndReindex(options.existingSegments, 0),
        ...importedSegments
      ]
    : importedSegments;

  return {
    valid: true,
    rows,
    segments,
    issues: [],
    importedCount: importedSegments.length,
    totalDurationSeconds: roundSeconds(importedSegments.reduce((total, segment) => total + segment.durationSeconds, 0))
  };
}

function parseTimingRange(value: string): { startSeconds: number; endSeconds: number } | null {
  const parts = value.split('-');
  if (parts.length !== 2) {
    return null;
  }
  const startSeconds = parseTimestampSeconds(parts[0].trim());
  const endSeconds = parseTimestampSeconds(parts[1].trim());
  if (startSeconds == null || endSeconds == null) {
    return null;
  }
  return { startSeconds, endSeconds };
}

export function parseTimestampSeconds(value: string): number | null {
  if (!value) {
    return null;
  }
  const parts = value.split(':');
  if (parts.length > 3 || parts.some((part) => part.trim() === '')) {
    return null;
  }
  const numbers = parts.map((part) => Number(part));
  if (numbers.some((part) => !Number.isFinite(part) || part < 0)) {
    return null;
  }
  if (parts.length === 1) {
    return roundSeconds(numbers[0]);
  }
  if (numbers.slice(1).some((part) => part >= 60)) {
    return null;
  }
  if (parts.length === 2) {
    return roundSeconds(numbers[0] * 60 + numbers[1]);
  }
  return roundSeconds(numbers[0] * 3600 + numbers[1] * 60 + numbers[2]);
}

function normalizeOptional(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function issue(lineNumber: number, message: string): NarrationQuickScriptImportIssue {
  return {
    lineNumber,
    message: `Line ${lineNumber}: ${message}`
  };
}

function cloneAndReindex(
  segments: NarrationWorkspace['segments'],
  startIndex: number
): NarrationWorkspace['segments'] {
  return segments.map((segment, offset) => ({
    ...segment,
    index: startIndex + offset
  }));
}

function roundSeconds(value: number): number {
  return Number(value.toFixed(3));
}
