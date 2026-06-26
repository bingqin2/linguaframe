package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.SubtitleExportService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class SubtitleExportServiceImpl implements SubtitleExportService {

    private final ObjectMapper objectMapper;

    public SubtitleExportServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
        try {
            return objectMapper.writeValueAsBytes(Map.of("segments", segments));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to export transcript JSON.", ex);
        }
    }

    @Override
    public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            TranscriptSegmentVo segment = segments.get(i);
            builder.append(i + 1).append('\n')
                    .append(formatTimestamp(segment.startMs(), ','))
                    .append(" --> ")
                    .append(formatTimestamp(segment.endMs(), ','))
                    .append('\n')
                    .append(segment.text())
                    .append("\n\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
        StringBuilder builder = new StringBuilder("WEBVTT\n\n");
        for (TranscriptSegmentVo segment : segments) {
            builder.append(formatTimestamp(segment.startMs(), '.'))
                    .append(" --> ")
                    .append(formatTimestamp(segment.endMs(), '.'))
                    .append('\n')
                    .append(segment.text())
                    .append("\n\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String formatTimestamp(long timestampMs, char millisecondSeparator) {
        long hours = timestampMs / 3_600_000L;
        long remainingMs = timestampMs % 3_600_000L;
        long minutes = remainingMs / 60_000L;
        remainingMs = remainingMs % 60_000L;
        long seconds = remainingMs / 1_000L;
        long milliseconds = remainingMs % 1_000L;
        return "%02d:%02d:%02d%c%03d".formatted(hours, minutes, seconds, millisecondSeparator, milliseconds);
    }
}
