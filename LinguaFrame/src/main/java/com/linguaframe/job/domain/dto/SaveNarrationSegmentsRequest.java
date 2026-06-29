package com.linguaframe.job.domain.dto;

import java.math.BigDecimal;
import java.util.List;

public record SaveNarrationSegmentsRequest(
        List<Segment> segments
) {

    public record Segment(
            int index,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String text,
            String voice
    ) {
    }
}
