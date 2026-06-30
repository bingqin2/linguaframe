package com.linguaframe.job.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaveNarrationSegmentsRequest(
        List<Segment> segments,
        List<SaveNarrationMixKeyframeDto> mixKeyframes
) {
    public SaveNarrationSegmentsRequest(List<Segment> segments) {
        this(segments, List.of());
    }

    public record Segment(
            int index,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String text,
            String voice,
            BigDecimal duckingVolume,
            BigDecimal narrationVolume,
            Integer fadeDurationMs
    ) {
        public Segment(int index, BigDecimal startSeconds, BigDecimal endSeconds, String text, String voice) {
            this(index, startSeconds, endSeconds, text, voice, null, null, null);
        }
    }
}
