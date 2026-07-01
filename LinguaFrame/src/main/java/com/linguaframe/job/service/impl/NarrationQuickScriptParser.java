package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class NarrationQuickScriptParser {

    private static final int MAX_ROWS = 20;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d+(?::\\d{1,2}){0,2}(?:\\.\\d+)?");

    public Result parse(String script) {
        if (!StringUtils.hasText(script)) {
            return Result.valid(List.of());
        }

        List<String> errors = new ArrayList<>();
        List<ParsedSegment> parsedSegments = new ArrayList<>();
        String[] rows = script.split("\\R", -1);
        int nonBlankRows = 0;

        for (int rowIndex = 0; rowIndex < rows.length; rowIndex += 1) {
            String row = rows[rowIndex];
            if (!StringUtils.hasText(row)) {
                continue;
            }
            nonBlankRows += 1;
            parseRow(row, rowIndex + 1, errors).ifPresent(parsedSegments::add);
        }

        if (nonBlankRows > MAX_ROWS) {
            errors.add("Narration quick script supports at most 20 rows.");
        }
        addOverlapErrors(parsedSegments, errors);

        if (!errors.isEmpty()) {
            return Result.invalid(errors);
        }

        List<SaveNarrationSegmentsRequest.Segment> segments = new ArrayList<>();
        for (int index = 0; index < parsedSegments.size(); index += 1) {
            ParsedSegment parsed = parsedSegments.get(index);
            segments.add(new SaveNarrationSegmentsRequest.Segment(
                    index,
                    parsed.startSeconds(),
                    parsed.endSeconds(),
                    parsed.text(),
                    parsed.voice()
            ));
        }
        return Result.valid(segments);
    }

    private java.util.Optional<ParsedSegment> parseRow(String row, int rowNumber, List<String> errors) {
        String[] parts = row.split("\\|", -1);
        if (parts.length != 3) {
            errors.add("Row " + rowNumber + ": expected START-END | VOICE | TEXT.");
            return java.util.Optional.empty();
        }

        String range = parts[0].trim();
        String voice = parts[1].trim();
        String text = parts[2].trim();
        String[] timestamps = range.split("-", -1);
        if (timestamps.length != 2) {
            errors.add("Row " + rowNumber + ": expected START-END | VOICE | TEXT.");
            return java.util.Optional.empty();
        }
        BigDecimal startSeconds = parseTimestamp(timestamps[0].trim(), rowNumber, "start time", errors);
        BigDecimal endSeconds = parseTimestamp(timestamps[1].trim(), rowNumber, "end time", errors);
        if (startSeconds == null || endSeconds == null) {
            return java.util.Optional.empty();
        }
        if (endSeconds.compareTo(startSeconds) <= 0) {
            errors.add("Row " + rowNumber + ": end time must be greater than start time.");
            return java.util.Optional.empty();
        }
        if (!StringUtils.hasText(text)) {
            errors.add("Row " + rowNumber + ": narration text must not be blank.");
            return java.util.Optional.empty();
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            errors.add("Row " + rowNumber + ": narration text must be at most 1000 characters.");
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ParsedSegment(
                rowNumber,
                startSeconds,
                endSeconds,
                text,
                StringUtils.hasText(voice) ? voice : null
        ));
    }

    private BigDecimal parseTimestamp(String value, int rowNumber, String label, List<String> errors) {
        if (!TIMESTAMP_PATTERN.matcher(value).matches()) {
            errors.add("Row " + rowNumber + ": " + label + " must be SS, MM:SS, or HH:MM:SS.");
            return null;
        }
        String[] parts = value.split(":");
        BigDecimal seconds;
        if (parts.length == 1) {
            seconds = new BigDecimal(parts[0]);
        } else if (parts.length == 2) {
            seconds = new BigDecimal(parts[0]).multiply(new BigDecimal("60")).add(new BigDecimal(parts[1]));
        } else if (parts.length == 3) {
            seconds = new BigDecimal(parts[0]).multiply(new BigDecimal("3600"))
                    .add(new BigDecimal(parts[1]).multiply(new BigDecimal("60")))
                    .add(new BigDecimal(parts[2]));
        } else {
            errors.add("Row " + rowNumber + ": " + label + " must be SS, MM:SS, or HH:MM:SS.");
            return null;
        }
        return seconds.setScale(3, RoundingMode.HALF_UP);
    }

    private void addOverlapErrors(List<ParsedSegment> parsedSegments, List<String> errors) {
        List<ParsedSegment> byTime = parsedSegments.stream()
                .sorted(java.util.Comparator.comparing(ParsedSegment::startSeconds))
                .toList();
        for (int index = 1; index < byTime.size(); index += 1) {
            ParsedSegment previous = byTime.get(index - 1);
            ParsedSegment current = byTime.get(index);
            if (current.startSeconds().compareTo(previous.endSeconds()) < 0) {
                errors.add("Rows " + previous.rowNumber() + " and " + current.rowNumber()
                        + ": narration segments must not overlap.");
            }
        }
    }

    private record ParsedSegment(
            int rowNumber,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String text,
            String voice
    ) {
    }

    public record Result(
            boolean valid,
            List<SaveNarrationSegmentsRequest.Segment> segments,
            List<String> errors
    ) {
        private static Result valid(List<SaveNarrationSegmentsRequest.Segment> segments) {
            return new Result(true, List.copyOf(segments), List.of());
        }

        private static Result invalid(List<String> errors) {
            return new Result(false, List.of(), List.copyOf(errors));
        }

        public int segmentCount() {
            return segments.size();
        }

        public int characterCount() {
            return segments.stream()
                    .mapToInt(segment -> segment.text() == null ? 0 : segment.text().length())
                    .sum();
        }

        public String voiceSummary() {
            if (segments.isEmpty()) {
                return "none";
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (SaveNarrationSegmentsRequest.Segment segment : segments) {
                String voice = StringUtils.hasText(segment.voice()) ? segment.voice() : "inherited";
                counts.merge(voice, 1, Integer::sum);
            }
            return counts.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(", "));
        }

        public SaveNarrationSegmentsRequest toSaveRequest() {
            return new SaveNarrationSegmentsRequest(segments);
        }
    }
}
