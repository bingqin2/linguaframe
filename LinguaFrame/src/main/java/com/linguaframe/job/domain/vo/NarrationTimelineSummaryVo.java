package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationTimelineSummaryVo(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal totalSpanSeconds,
        BigDecimal coveredSeconds,
        BigDecimal gapSeconds,
        int gapCount,
        boolean hasOverlap,
        boolean generationReady,
        List<NarrationTimelineSegmentVo> segments
) {
}
